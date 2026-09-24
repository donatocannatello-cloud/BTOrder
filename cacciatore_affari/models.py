"""Schema normalizzato degli annunci.

Ogni adattatore produce dizionari con questi campi; :func:`normalizza`
completa i valori mancanti, converte i tipi e calcola i campi derivati.
Il formato è pensato per essere generico: la categoria ("capannoni", in
futuro "auto", "moto") determina quali campi hanno senso (es. la superficie).
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import asdict, dataclass, field, fields
from datetime import datetime, timezone
from typing import Any


def now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()


@dataclass
class Annuncio:
    id: str
    fonte: str
    categoria: str
    titolo: str = ""
    prezzo_base: float | None = None
    offerta_minima: float | None = None
    superficie_mq: float | None = None
    eur_mq: float | None = None
    comune: str | None = None
    provincia: str | None = None
    indirizzo: str | None = None
    lat: float | None = None
    lon: float | None = None
    tipo_vendita: str | None = None
    data_asta: str | None = None
    numero_tentativo: int | None = None
    rge: str | None = None
    tribunale: str | None = None
    url: str | None = None
    url_perizia: str | None = None
    first_seen: str | None = None
    last_seen: str | None = None
    storico_prezzi: list[dict] = field(default_factory=list)
    notificato: bool = False
    # Campi accessori (non richiesti dallo schema minimo ma utili)
    lotto: str | None = None
    regione: str | None = None
    descrizione: str | None = None
    id_fonte: str | None = None
    id_alias: list[str] = field(default_factory=list)
    punteggio: float | None = None
    dettaglio_punteggio: dict = field(default_factory=dict)
    perizia_locale: str | None = None
    notificato_il: str | None = None
    extra: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return asdict(self)


CAMPI = {f.name for f in fields(Annuncio)}


def make_id(fonte: str, id_fonte: Any) -> str:
    """ID stabile a partire dalla fonte e dall'identificativo nella fonte."""
    return f"{fonte}:{id_fonte}"


def make_hash_id(fonte: str, *parts: Any) -> str:
    """ID di ripiego quando la fonte non fornisce un identificativo."""
    raw = "|".join(str(p or "").strip().lower() for p in parts)
    return f"{fonte}:h{hashlib.sha1(raw.encode()).hexdigest()[:16]}"


_NUM_RE = re.compile(r"-?\d[\d.,]*")


def parse_numero(value: Any) -> float | None:
    """Converte numeri in formato italiano ("1.234,56 €") o standard in float."""
    if value is None or value == "":
        return None
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    m = _NUM_RE.search(str(value))
    if not m:
        return None
    s = m.group(0)
    if "," in s and "." in s:
        # il separatore che compare per ultimo è quello dei decimali
        if s.rfind(",") > s.rfind("."):
            s = s.replace(".", "").replace(",", ".")
        else:
            s = s.replace(",", "")
    elif "," in s:
        s = s.replace(".", "").replace(",", ".")
    elif s.count(".") > 1 or re.fullmatch(r"-?\d{1,3}\.\d{3}", s):
        # "1.234.567" oppure "12.500": puntini come separatori delle migliaia
        s = s.replace(".", "")
    try:
        return float(s)
    except ValueError:
        return None


def _parse_int(value: Any) -> int | None:
    n = parse_numero(value)
    return int(n) if n is not None else None


def normalizza(raw: dict, now: str | None = None) -> dict:
    """Restituisce un dizionario conforme allo schema :class:`Annuncio`.

    I campi sconosciuti finiscono in ``extra``; i numeri vengono convertiti,
    ``eur_mq`` viene ricalcolato e ``first_seen``/``last_seen`` impostati.
    """
    now = now or now_iso()
    data = {k: v for k, v in raw.items() if k in CAMPI}
    extra = dict(raw.get("extra") or {})
    extra.update({k: v for k, v in raw.items() if k not in CAMPI})
    data["extra"] = extra

    for k in ("prezzo_base", "offerta_minima", "superficie_mq", "lat", "lon"):
        data[k] = parse_numero(data.get(k))
    data["numero_tentativo"] = _parse_int(data.get("numero_tentativo"))
    if data.get("provincia"):
        data["provincia"] = str(data["provincia"]).strip().upper()
    if data.get("comune"):
        data["comune"] = str(data["comune"]).strip().title()

    ann = Annuncio(**data)
    ann.eur_mq = calcola_eur_mq(ann.prezzo_base, ann.superficie_mq)
    ann.first_seen = ann.first_seen or now
    ann.last_seen = now
    if not ann.storico_prezzi and ann.prezzo_base is not None:
        ann.storico_prezzi = [voce_storico(ann.prezzo_base, ann.data_asta, ann.numero_tentativo, now)]
    return ann.to_dict()


def calcola_eur_mq(prezzo: float | None, superficie: float | None) -> float | None:
    if prezzo is None or not superficie or superficie <= 0:
        return None
    return round(prezzo / superficie, 2)


def voce_storico(prezzo: float, data_asta: str | None, tentativo: int | None, visto_il: str) -> dict:
    return {"prezzo_base": prezzo, "data_asta": data_asta, "numero_tentativo": tentativo, "visto_il": visto_il}


def merge_annuncio(old: dict, new: dict) -> dict:
    """Aggiorna un annuncio già archiviato con i dati appena raccolti.

    - conserva ``id``, ``first_seen`` e lo storico prezzi (aggiungendo una voce
      se il prezzo base è cambiato);
    - se il prezzo è sceso rimette ``notificato = False`` così che il ribasso
      venga segnalato;
    - i campi nuovi vuoti non sovrascrivono quelli già noti.
    """
    merged = dict(old)
    for k, v in new.items():
        if k in ("id", "first_seen", "storico_prezzi", "notificato", "notificato_il",
                 "id_alias", "punteggio", "dettaglio_punteggio", "perizia_locale"):
            continue
        if v is None or v == "" or v == [] or v == {}:
            continue
        if k == "extra":
            merged["extra"] = {**(old.get("extra") or {}), **v}
            continue
        merged[k] = v

    if new.get("id") and new["id"] != old.get("id"):
        alias = list(old.get("id_alias") or [])
        if new["id"] not in alias:
            alias.append(new["id"])
        merged["id_alias"] = alias

    storico = list(old.get("storico_prezzi") or [])
    nuovo_prezzo = new.get("prezzo_base")
    ultimo = storico[-1]["prezzo_base"] if storico else old.get("prezzo_base")
    if nuovo_prezzo is not None and nuovo_prezzo != ultimo:
        storico.append(voce_storico(nuovo_prezzo, new.get("data_asta"), new.get("numero_tentativo"),
                                    new.get("last_seen") or now_iso()))
        if ultimo is not None and nuovo_prezzo < ultimo:
            merged["notificato"] = False
            merged["notificato_il"] = None
    merged["storico_prezzi"] = storico
    merged["eur_mq"] = calcola_eur_mq(merged.get("prezzo_base"), merged.get("superficie_mq"))
    return merged


def prezzo_ribassato(annuncio: dict) -> bool:
    """True se l'ultimo prezzo dello storico è inferiore al precedente."""
    st = annuncio.get("storico_prezzi") or []
    return len(st) >= 2 and st[-1]["prezzo_base"] < st[-2]["prezzo_base"]
