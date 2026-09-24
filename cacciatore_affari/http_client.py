"""Client HTTP "educato": User-Agent realistico, pausa tra richieste,
retry con backoff esponenziale e rispetto di robots.txt."""

from __future__ import annotations

import logging
import random
import time
from urllib.parse import urlsplit
from urllib.robotparser import RobotFileParser

import httpx

log = logging.getLogger(__name__)

DEFAULT_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
)
RETRY_STATUS = {429, 500, 502, 503, 504}


class RobotsDisallowed(Exception):
    """L'URL richiesto è vietato da robots.txt."""


class PoliteClient:
    def __init__(
        self,
        user_agent: str = DEFAULT_UA,
        min_delay: float = 3.0,
        max_delay: float = 5.0,
        max_retries: int = 4,
        backoff_base: float = 5.0,
        timeout: float = 30.0,
        rispetta_robots: bool = True,
        transport: httpx.BaseTransport | None = None,
        sleep=time.sleep,
    ):
        self.user_agent = user_agent
        self.min_delay = min_delay
        self.max_delay = max(max_delay, min_delay)
        self.max_retries = max_retries
        self.backoff_base = backoff_base
        self.rispetta_robots = rispetta_robots
        self._sleep = sleep
        self._last_request: dict[str, float] = {}
        self._robots: dict[str, RobotFileParser | None] = {}
        self.client = httpx.Client(
            headers={
                "User-Agent": user_agent,
                "Accept-Language": "it-IT,it;q=0.9,en;q=0.6",
            },
            timeout=timeout,
            follow_redirects=True,
            transport=transport,
        )

    # --- context manager ---------------------------------------------------
    def __enter__(self):
        return self

    def __exit__(self, *exc):
        self.close()

    def close(self) -> None:
        self.client.close()

    # --- robots.txt --------------------------------------------------------
    def _robots_for(self, url: str) -> RobotFileParser | None:
        parts = urlsplit(url)
        origin = f"{parts.scheme}://{parts.netloc}"
        if origin in self._robots:
            return self._robots[origin]
        rp = RobotFileParser()
        robots_url = origin + "/robots.txt"
        self._throttle(origin)
        try:
            resp = self.client.get(robots_url)
        except httpx.HTTPError as e:
            # Non potendo verificare, per prudenza non si procede su questo host.
            log.warning("robots.txt non raggiungibile (%s): host %s escluso in questo ciclo", e, origin)
            rp.disallow_all = True
            self._robots[origin] = rp
            return rp
        if resp.status_code in (401, 403):
            rp.disallow_all = True
        elif resp.status_code >= 500:
            log.warning("robots.txt di %s risponde %s: host escluso in questo ciclo", origin, resp.status_code)
            rp.disallow_all = True
        elif resp.status_code >= 400:
            rp.allow_all = True
        else:
            rp.parse(resp.text.splitlines())
        self._robots[origin] = rp
        return rp

    def consentito(self, url: str) -> bool:
        if not self.rispetta_robots:
            return True
        rp = self._robots_for(url)
        return rp is None or rp.can_fetch(self.user_agent, url)

    # --- rate limiting ------------------------------------------------------
    @staticmethod
    def _origin(url: str) -> str:
        p = urlsplit(url)
        return f"{p.scheme}://{p.netloc}"

    def _throttle(self, origin: str) -> None:
        last = self._last_request.get(origin)
        delay = random.uniform(self.min_delay, self.max_delay)
        rp = self._robots.get(origin)
        if rp is not None:
            try:
                cd = rp.crawl_delay(self.user_agent)
            except AttributeError:
                cd = None
            if cd:
                delay = max(delay, float(cd))
        if last is not None:
            wait = last + delay - time.monotonic()
            if wait > 0:
                self._sleep(wait)
        self._last_request[origin] = time.monotonic()

    # --- richieste ---------------------------------------------------------
    def request(self, method: str, url: str, **kwargs) -> httpx.Response:
        if not self.consentito(url):
            raise RobotsDisallowed(url)
        origin = self._origin(url)
        attempt = 0
        while True:
            self._throttle(origin)
            try:
                resp = self.client.request(method, url, **kwargs)
                if resp.status_code not in RETRY_STATUS:
                    resp.raise_for_status()
                    return resp
                err: Exception = httpx.HTTPStatusError(
                    f"HTTP {resp.status_code}", request=resp.request, response=resp)
                retry_after = resp.headers.get("Retry-After")
            except (httpx.TransportError, httpx.TimeoutException) as e:
                err, retry_after = e, None
            attempt += 1
            if attempt > self.max_retries:
                raise err
            wait = self.backoff_base * (2 ** (attempt - 1)) + random.uniform(0, 1)
            if retry_after and retry_after.isdigit():
                wait = max(wait, float(retry_after))
            log.warning("%s %s fallita (%s): nuovo tentativo %d/%d tra %.0fs",
                        method, url, err, attempt, self.max_retries, wait)
            self._sleep(wait)

    def get(self, url: str, **kwargs) -> httpx.Response:
        return self.request("GET", url, **kwargs)

    def post(self, url: str, **kwargs) -> httpx.Response:
        return self.request("POST", url, **kwargs)
