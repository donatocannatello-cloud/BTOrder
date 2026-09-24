"""Notifiche Telegram per gli annunci interessanti."""

from __future__ import annotations

import html
import logging
import time

import httpx

from models import now_iso, prezzo_ribassato

log = logging.getLogger(__name__)

TELEGRAM_API = "https://api.telegram.org/bot{token}/sendMessage"


def da_notificare(annunci: list[dict], soglia: float, soglie_categoria: dict | None = None) -> list[dict]:
    """Annunci non ancora notificati (nuovi o ribassati) con punteggio >= soglia."""
    out = []
    for a in annunci:
        s = (soglie_categoria or {}).get(a.get("categoria"), soglia)
        if not a.get("notificato") and a.get("punteggio") is not None and a["punteggio"] >= s:
            out.append(a)
    return sorted(out, key=lambda a: a["punteggio"], reverse=True)


def _euro(v: float | None) -> str:
    if v is None:
        return "n.d."
    return f"€ {v:,.0f}".replace(",", ".")


def formatta_messaggio(a: dict) -> str:
    e = lambda v: html.escape(str(v)) if v not in (None, "") else "n.d."  # noqa: E731
    if prezzo_ribassato(a):
        st = a["storico_prezzi"]
        intestazione = f"📉 <b>RIBASSO</b> {_euro(st[-2]['prezzo_base'])} → {_euro(st[-1]['prezzo_base'])}"
    else:
        intestazione = "🆕 <b>NUOVO</b>"
    righe = [
        f"{intestazione} · punteggio <b>{a.get('punteggio')}</b>/100",
        f"<b>{e(a.get('titolo'))[:200]}</b>",
        f"📍 {e(a.get('comune'))} ({e(a.get('provincia'))}) {e(a.get('indirizzo')) if a.get('indirizzo') else ''}".rstrip(),
        f"💶 Base {_euro(a.get('prezzo_base'))} · min. {_euro(a.get('offerta_minima'))}",
    ]
    if a.get("superficie_mq"):
        righe.append(f"📐 {a['superficie_mq']:.0f} m² · {_euro(a.get('eur_mq'))}/m²")
    med = (a.get("dettaglio_punteggio") or {}).get("mediana_eur_mq")
    if med:
        righe.append(f"📊 Mediana provincia {_euro(med)}/m²")
    righe.append(f"🔨 Asta {e(a.get('data_asta'))} · tentativo {e(a.get('numero_tentativo'))} · {e(a.get('tipo_vendita'))}")
    if a.get("rge") or a.get("tribunale"):
        righe.append(f"⚖️ RGE {e(a.get('rge'))} · Trib. {e(a.get('tribunale'))}")
    if a.get("url"):
        righe.append(f'<a href="{html.escape(a["url"], quote=True)}">Apri annuncio</a>')
    if a.get("url_perizia"):
        righe.append(f'<a href="{html.escape(a["url_perizia"], quote=True)}">Perizia</a>')
    return "\n".join(righe)


class TelegramNotifier:
    def __init__(self, token: str | None, chat_id: str | int | None, dry_run: bool = False,
                 pausa: float = 1.0, client: httpx.Client | None = None):
        self.token = token
        self.chat_id = chat_id
        self.dry_run = dry_run or not (token and chat_id)
        self.pausa = pausa
        self.client = client or httpx.Client(timeout=20)

    def invia(self, testo: str) -> bool:
        if self.dry_run:
            log.info("[dry-run] Telegram:\n%s", testo)
            return True
        for tentativo in range(3):
            try:
                r = self.client.post(TELEGRAM_API.format(token=self.token), json={
                    "chat_id": self.chat_id,
                    "text": testo,
                    "parse_mode": "HTML",
                    "disable_web_page_preview": True,
                })
                if r.status_code == 429:
                    attesa = r.json().get("parameters", {}).get("retry_after", 5)
                    time.sleep(attesa)
                    continue
                r.raise_for_status()
                return True
            except httpx.HTTPError as e:
                log.warning("Invio Telegram fallito (tentativo %d): %s", tentativo + 1, e)
                time.sleep(2 ** tentativo)
        return False

    def notifica(self, annunci: list[dict]) -> int:
        """Invia gli annunci e li marca come notificati. Restituisce quanti ne ha inviati.

        In dry-run i messaggi vanno solo nel log e gli annunci non vengono marcati.
        """
        inviati = 0
        for a in annunci:
            if self.invia(formatta_messaggio(a)) and not self.dry_run:
                a["notificato"] = True
                a["notificato_il"] = now_iso()
                inviati += 1
                time.sleep(self.pausa)
        return inviati
