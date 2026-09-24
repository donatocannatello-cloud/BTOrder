"""Adattatore per il Portale delle Vendite Pubbliche (pvp.giustizia.it).

Struttura del sito
------------------
Il portale è un'applicazione JavaScript: la lista annunci
(``/pvp/it/lista_annunci.page``) non contiene i dati nell'HTML, ma li carica
con chiamate XHR JSON verso microservizi. Per questo l'adattatore usa
direttamente l'API JSON invece dello scraping HTML:

1. ``GET {base_url}/bo-ms/fe-config/area-annunci`` → JSON pubblico di
   configurazione del frontend, contenente gli URL dei microservizi
   (il prefisso ``ric-<hash>`` cambia a ogni rilascio).
2. ``POST {base_url}/ric-<hash>/ric-ms/ricerca/vendite?page=N&size=M&sort=dataVendita,asc``
   con corpo JSON, ad esempio ``{"filtroAnnunci": 1, "tipoLotto": "IMMOBILI"}``,
   → pagina di risultati in stile Spring (``content``, ``totalPages``, ...).
3. Il dettaglio è la pagina ``/pvp/it/detail_annuncio.page?idAnnuncio=<id>``
   dalla quale si ricava il link alla perizia (PDF).

Poiché il formato non è documentato ufficialmente, i nomi dei campi sono
cercati tra più alternative (``CAMPI``) e possono essere corretti da
config.json (``fonti.pvp.mappa_campi``) senza toccare il codice. Con
``fonti.pvp.salva_risposte_grezze`` la prima pagina di ogni ricerca viene
salvata in ``data/raw/`` per verificare i nomi reali dei campi.

Il filtro per categoria ("capannoni") e per regione viene applicato anche
lato client, sulle parole chiave e sulla provincia/regione del lotto.
"""

from __future__ import annotations

import logging
import re
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import urljoin

import httpx

import geo
from adapters.base import Adapter
from http_client import RobotsDisallowed
from models import make_hash_id, make_id, parse_numero

log = logging.getLogger(__name__)

DEFAULTS: dict[str, Any] = {
    "base_url": "https://pvp.giustizia.it",
    "config_path": "/bo-ms/fe-config/area-annunci",
    # usato se il JSON di configurazione non è raggiungibile o non contiene l'URL
    "ricerca_path": "/ric-496b258c-986a1b71/ric-ms/ricerca/vendite",
    "page_size": 50,
    "max_pagine": 40,
    "sort": "dataVendita,asc",
    "corpo_ricerca": {"filtroAnnunci": 1},
    "filtri_extra": {},
    "url_annuncio": "https://pvp.giustizia.it/pvp/it/detail_annuncio.page?idAnnuncio={id}",
    "url_dettaglio_api": None,
    "salva_risposte_grezze": None,
    "mappa_campi": {},
}

#: per ogni campo dello schema, i percorsi (anche annidati con ".") da provare nel JSON
CAMPI: dict[str, list[str]] = {
    "id_fonte": ["idLotto", "id", "idAnnuncio", "idInserzione", "lotto.id"],
    "id_annuncio": ["idAnnuncio", "annuncio.id", "idInserzione", "id"],
    "titolo": ["titolo", "descrizioneBreve", "lotto.descrizioneBreve", "tipologia", "descrizione"],
    "descrizione": ["descrizione", "descrizioneLotto", "lotto.descrizione", "beni.0.descrizione"],
    "prezzo_base": ["prezzoBase", "prezzoBaseAsta", "prezzoValoreBase", "prezzo", "lotto.prezzoBase"],
    "offerta_minima": ["offertaMinima", "prezzoOffertaMinima", "offMinima", "lotto.offertaMinima"],
    "superficie_mq": ["superficie", "superficieMq", "mq", "beni.0.superficie", "bene.superficie"],
    "comune": ["comune", "citta", "indirizzo.comune", "ubicazione.comune", "beni.0.comune", "localita"],
    "provincia": ["siglaProvincia", "provincia", "indirizzo.provincia", "ubicazione.provincia", "beni.0.provincia"],
    "regione": ["regione", "indirizzo.regione", "ubicazione.regione", "beni.0.regione"],
    "indirizzo": ["indirizzo", "indirizzoCompleto", "ubicazione.indirizzo", "beni.0.indirizzo"],
    "lat": ["latitudine", "lat", "coordinate.lat", "geo.lat", "beni.0.latitudine"],
    "lon": ["longitudine", "lon", "lng", "coordinate.lon", "coordinate.lng", "geo.lon", "beni.0.longitudine"],
    "tipo_vendita": ["tipoVendita", "modalitaVendita", "tipologiaVendita", "descTipoVendita"],
    "data_asta": ["dataVendita", "dataOraVendita", "dataAsta", "dataEsperimento"],
    "numero_tentativo": ["numeroTentativo", "tentativo", "numeroEsperimento", "esperimento", "numeroAsta"],
    "rge": ["rge", "numeroRge", "rgeProcedura", "procedura.rge", "numeroProcedura"],
    "anno_rge": ["annoProcedura", "annoRge", "procedura.anno"],
    "tribunale": ["tribunale", "descrizioneTribunale", "ufficioGiudiziario", "procedura.tribunale", "tribunale.descrizione"],
    "lotto": ["codiceLotto", "numeroLotto", "lotto.codice", "lotto"],
    "categoria_fonte": ["categoria", "tipologia", "tipologiaBene", "categoriaBene", "beni.0.tipologia"],
    "url_perizia": ["urlPerizia", "perizia.url"],
}

_NUM_MQ = r"(\d{1,3}(?:\.\d{3})+(?:,\d+)?|\d+(?:,\d+)?)"
_UNITA_MQ = r"(?:mq\.?|m2|m²|metri\s*quadr\w*)"
# "1.250 mq" oppure "mq 1.250"
_RE_MQ = re.compile(rf"{_NUM_MQ}\s*{_UNITA_MQ}|\b{_UNITA_MQ}\s*(?:di\s*)?{_NUM_MQ}", re.I)
_RE_TENTATIVO = re.compile(r"(\d+)\s*[°ºa]?\s*(?:esperimento|tentativo)", re.I)
_RE_HREF = re.compile(r"""<a\b[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", re.I | re.S)


def _get_path(obj: Any, path: str) -> Any:
    cur = obj
    for part in path.split("."):
        if isinstance(cur, dict):
            cur = cur.get(part)
        elif isinstance(cur, list) and part.isdigit():
            idx = int(part)
            cur = cur[idx] if idx < len(cur) else None
        else:
            return None
        if cur is None:
            return None
    return cur


def _primo(obj: dict, paths: list[str]) -> Any:
    for p in paths:
        v = _get_path(obj, p)
        if v not in (None, "", [], {}):
            return v
    return None


def _testo(obj: Any) -> str:
    """Concatena tutte le stringhe contenute nell'oggetto (per ricerche per parola chiave)."""
    if isinstance(obj, str):
        return obj
    if isinstance(obj, dict):
        return " ".join(_testo(v) for v in obj.values())
    if isinstance(obj, list):
        return " ".join(_testo(v) for v in obj)
    return ""


def _scalare(v: Any) -> Any:
    """Se il valore è un oggetto, prova a estrarne la descrizione leggibile."""
    if isinstance(v, dict):
        for k in ("descrizione", "nome", "denominazione", "sigla", "valore", "codice"):
            if v.get(k):
                return v[k]
        return None
    return v


def estrai_lista(payload: Any) -> tuple[list[dict], int | None]:
    """Trova la lista di risultati e il numero totale di pagine in una risposta JSON."""
    if isinstance(payload, list):
        return [x for x in payload if isinstance(x, dict)], None
    if not isinstance(payload, dict):
        return [], None
    for key in ("content", "risultati", "results", "items", "lotti", "vendite", "data", "body"):
        v = payload.get(key)
        if isinstance(v, list):
            pagine = payload.get("totalPages") or _get_path(payload, "page.totalPages")
            return [x for x in v if isinstance(x, dict)], pagine
        if isinstance(v, dict):
            items, pagine = estrai_lista(v)
            if items:
                return items, pagine or payload.get("totalPages")
    return [], None


def estrai_superficie(testo: str) -> float | None:
    valori = [parse_numero(m.group(1) or m.group(2)) for m in _RE_MQ.finditer(testo or "")]
    valori = [v for v in valori if v and v > 0]
    # in una descrizione compaiono spesso più superfici (uffici, piazzale...): si prende la maggiore
    return max(valori) if valori else None


def _data_iso(v: Any) -> str | None:
    if v is None:
        return None
    if isinstance(v, (int, float)):  # epoch in millisecondi
        return datetime.fromtimestamp(v / 1000 if v > 1e11 else v).isoformat()
    return str(v)


class PVPAdapter(Adapter):
    nome = "pvp"
    # Aggiungere "auto"/"moto" quando saranno configurate le relative categorie
    # (tipo_lotto "MOBILI" e parole chiave dedicate in config.json).
    categorie_supportate = ("capannoni",)

    def __init__(self, client, config: dict | None = None, data_dir: str | Path = "data"):
        cfg = dict(DEFAULTS)
        cfg.update(config or {})
        super().__init__(client, cfg)
        self.data_dir = Path(data_dir)
        self.campi = {k: list(v) for k, v in CAMPI.items()}
        for campo, percorsi in (cfg.get("mappa_campi") or {}).items():
            self.campi[campo] = list(percorsi) + [p for p in self.campi.get(campo, []) if p not in percorsi]
        self._url_ricerca: str | None = None

    # --- endpoint -----------------------------------------------------------
    def url_ricerca(self) -> str:
        if self._url_ricerca:
            return self._url_ricerca
        base = self.config["base_url"].rstrip("/")
        url = base + self.config["ricerca_path"]
        try:
            resp = self.client.get(base + self.config["config_path"], headers={"Accept": "application/json"})
            trovato = self._cerca_url_ric_ms(resp.json())
            if trovato:
                url = urljoin(base + "/", trovato.rstrip("/"))
                if not url.endswith("/ricerca/vendite"):
                    url = url.rstrip("/") + "/ricerca/vendite"
                log.info("PVP: endpoint di ricerca da configurazione: %s", url)
        except (httpx.HTTPError, ValueError, RobotsDisallowed) as e:
            log.warning("PVP: configurazione frontend non disponibile (%s), uso %s", e, url)
        self._url_ricerca = url
        return url

    @staticmethod
    def _cerca_url_ric_ms(obj: Any) -> str | None:
        if isinstance(obj, str):
            return obj if "ric-ms" in obj else None
        if isinstance(obj, dict):
            obj = list(obj.values())
        if isinstance(obj, list):
            for v in obj:
                r = PVPAdapter._cerca_url_ric_ms(v)
                if r:
                    return r
        return None

    # --- lista annunci --------------------------------------------------------
    def fetch_listings(self, filters: dict) -> list[dict]:
        categoria = filters["categoria"]
        corpo = dict(self.config["corpo_ricerca"])
        corpo["tipoLotto"] = filters.get("tipo_lotto", "IMMOBILI")
        corpo.update(self.config.get("filtri_extra") or {})
        corpo.update(filters.get("filtri_extra_pvp") or {})
        url = self.url_ricerca()
        size = int(self.config["page_size"])
        risultati: list[dict] = []
        visti = 0

        for page in range(int(self.config["max_pagine"])):
            params = {"page": page, "size": size, "sort": self.config["sort"]}
            try:
                resp = self.client.post(url, params=params, json=corpo, headers={
                    "Accept": "application/json",
                    "Content-Type": "application/json",
                    "Origin": self.config["base_url"],
                    "Referer": self.config["base_url"] + "/pvp/it/lista_annunci.page",
                })
                payload = resp.json()
            except RobotsDisallowed:
                log.error("PVP: %s vietato o non verificabile via robots.txt, ricerca interrotta", url)
                break
            except (httpx.HTTPError, ValueError) as e:
                log.error("PVP: errore alla pagina %d: %s", page, e)
                break
            if page == 0:
                self._salva_grezzo(categoria, payload)
            items, tot_pagine = estrai_lista(payload)
            visti += len(items)
            for it in items:
                ann = self.mappa(it, categoria)
                if self.passa_filtri(ann, it, filters):
                    risultati.append(ann)
            log.info("PVP[%s]: pagina %d/%s, %d lotti letti, %d pertinenti finora",
                     categoria, page + 1, tot_pagine or "?", visti, len(risultati))
            if not items or (tot_pagine is not None and page + 1 >= tot_pagine) or \
                    (tot_pagine is None and len(items) < size):
                break
        return risultati

    def _salva_grezzo(self, categoria: str, payload: Any) -> None:
        dest = self.config.get("salva_risposte_grezze")
        if not dest:
            return
        import storage  # import locale per evitare cicli
        path = Path(dest) / f"pvp_{categoria}_{datetime.now():%Y%m%d_%H%M%S}.json"
        storage.save(path, payload)
        log.info("PVP: risposta grezza salvata in %s", path)

    # --- mappatura ------------------------------------------------------------
    def _campo(self, item: dict, nome: str) -> Any:
        return _scalare(_primo(item, self.campi.get(nome, [])))

    def mappa(self, item: dict, categoria: str) -> dict:
        c = lambda n: self._campo(item, n)  # noqa: E731
        id_fonte = c("id_fonte")
        testo = _testo(item)

        rge = c("rge")
        anno = c("anno_rge")
        if rge is not None and anno and "/" not in str(rge):
            rge = f"{rge}/{anno}"

        superficie = parse_numero(c("superficie_mq")) or estrai_superficie(testo)
        tentativo = c("numero_tentativo")
        if tentativo is None:
            m = _RE_TENTATIVO.search(testo)
            tentativo = int(m.group(1)) if m else None

        comune, provincia = c("comune"), c("provincia")
        id_annuncio = c("id_annuncio") or id_fonte
        ann = {
            "id": make_id(self.nome, id_fonte) if id_fonte is not None
            else make_hash_id(self.nome, rge, c("tribunale"), c("lotto"), comune, c("prezzo_base")),
            "id_fonte": str(id_fonte) if id_fonte is not None else None,
            "fonte": self.nome,
            "categoria": categoria,
            "titolo": str(c("titolo") or "").strip()[:300],
            "descrizione": (str(c("descrizione")) if c("descrizione") else None),
            "prezzo_base": c("prezzo_base"),
            "offerta_minima": c("offerta_minima"),
            "superficie_mq": superficie,
            "comune": comune,
            "provincia": geo.sigla_provincia(provincia) or provincia,
            "regione": c("regione"),
            "indirizzo": c("indirizzo"),
            "lat": c("lat"),
            "lon": c("lon"),
            "tipo_vendita": c("tipo_vendita"),
            "data_asta": _data_iso(c("data_asta")),
            "numero_tentativo": tentativo,
            "rge": str(rge) if rge is not None else None,
            "tribunale": c("tribunale"),
            "lotto": str(c("lotto")) if c("lotto") is not None else None,
            "url": self.config["url_annuncio"].format(id=id_annuncio) if id_annuncio is not None else None,
            "url_perizia": c("url_perizia"),
            "extra": {"categoria_fonte": c("categoria_fonte")},
        }
        return ann

    # --- filtri lato client ----------------------------------------------------
    @staticmethod
    def passa_filtri(ann: dict, item: dict, filters: dict) -> bool:
        testo = (_testo(item) + " " + (ann.get("titolo") or "")).lower()
        parole = [p.lower() for p in filters.get("parole_chiave") or []]
        if parole and not any(p in testo for p in parole):
            return False
        escluse = [p.lower() for p in filters.get("parole_escluse") or []]
        if any(p in testo for p in escluse):
            return False

        regioni = [r.lower() for r in filters.get("regioni") or []]
        sigle = geo.sigle_ammesse(filters.get("regioni"), filters.get("province"))
        if sigle or regioni:
            prov_ok = bool(ann.get("provincia")) and ann["provincia"] in sigle
            reg_ok = not filters.get("province") and bool(ann.get("regione")) and \
                str(ann["regione"]).lower() in regioni
            if not (prov_ok or reg_ok):
                return False

        prezzo = parse_numero(ann.get("prezzo_base"))
        if filters.get("prezzo_max") and prezzo and prezzo > filters["prezzo_max"]:
            return False
        if filters.get("prezzo_min") and prezzo and prezzo < filters["prezzo_min"]:
            return False
        sup = ann.get("superficie_mq")
        if filters.get("superficie_min") and sup and sup < filters["superficie_min"]:
            return False
        return True

    # --- dettaglio --------------------------------------------------------------
    def fetch_detail(self, listing: dict) -> dict:
        agg: dict = {}
        api = self.config.get("url_dettaglio_api")
        try:
            if api and listing.get("id_fonte"):
                resp = self.client.get(api.format(id=listing["id_fonte"]), headers={"Accept": "application/json"})
                item = resp.json()
                dettaglio = self.mappa(item, listing["categoria"])
                agg = {k: v for k, v in dettaglio.items()
                       if v not in (None, "", {}) and k not in ("id", "fonte", "categoria", "extra")}
                html = _testo(item)
            elif listing.get("url"):
                html = self.client.get(listing["url"]).text
            else:
                return {}
        except RobotsDisallowed:
            log.info("PVP: dettaglio %s vietato da robots.txt", listing.get("url"))
            return {}
        except (httpx.HTTPError, ValueError) as e:
            log.warning("PVP: dettaglio %s non disponibile: %s", listing.get("id"), e)
            return {}

        if not listing.get("url_perizia") and not agg.get("url_perizia"):
            perizia = self.trova_link_perizia(html, listing.get("url") or self.config["base_url"])
            if perizia:
                agg["url_perizia"] = perizia
        if not listing.get("superficie_mq") and not agg.get("superficie_mq"):
            sup = estrai_superficie(re.sub(r"<[^>]+>", " ", html))
            if sup:
                agg["superficie_mq"] = sup
        if not listing.get("numero_tentativo") and not agg.get("numero_tentativo"):
            m = _RE_TENTATIVO.search(re.sub(r"<[^>]+>", " ", html))
            if m:
                agg["numero_tentativo"] = int(m.group(1))
        return agg

    @staticmethod
    def trova_link_perizia(html: str, base_url: str) -> str | None:
        pdf: list[str] = []
        for href, testo in _RE_HREF.findall(html or ""):
            etichetta = (href + " " + re.sub(r"<[^>]+>", " ", testo)).lower()
            if "perizia" in etichetta or "stima" in etichetta:
                return urljoin(base_url, href)
            if href.lower().split("?")[0].endswith(".pdf"):
                pdf.append(urljoin(base_url, href))
        return pdf[0] if pdf else None

