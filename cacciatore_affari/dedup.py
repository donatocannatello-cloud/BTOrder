"""Riconoscimento dei duplicati.

Due annunci sono considerati lo stesso bene se:

1. hanno lo stesso numero di procedura (RGE) e lo stesso tribunale
   (e, se entrambi lo indicano, lo stesso lotto: una procedura può vendere
   più lotti distinti); oppure
2. si trovano nello stesso comune, hanno superficie simile (±3% di default)
   e prezzo base simile (±5% di default).
"""

from __future__ import annotations

import re
import unicodedata

DEFAULT_TOLL_SUPERFICIE = 0.03
DEFAULT_TOLL_PREZZO = 0.05


def _slug(s: str | None) -> str:
    if not s:
        return ""
    s = unicodedata.normalize("NFKD", str(s)).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def normalizza_tribunale(t: str | None) -> str:
    s = _slug(t)
    s = re.sub(r"^(tribunale( ordinario)?( di)?|trib)\s+", "", s)
    return s.strip()


def normalizza_rge(rge: str | None) -> str:
    """Riduce "R.G.E. n. 123/2021" o "123 / 2021" a "123/2021"."""
    if not rge:
        return ""
    m = re.search(r"(\d+)\s*/\s*(\d{2,4})", str(rge))
    if m:
        num, anno = int(m.group(1)), m.group(2)
        if len(anno) == 2:
            anno = "20" + anno
        return f"{num}/{anno}"
    return _slug(rge).replace(" ", "")


def _simile(a: float | None, b: float | None, toll: float) -> bool:
    if a is None or b is None or a <= 0 or b <= 0:
        return False
    return abs(a - b) / max(a, b) <= toll


def stesso_rge(a: dict, b: dict) -> bool:
    ra, rb = normalizza_rge(a.get("rge")), normalizza_rge(b.get("rge"))
    ta, tb = normalizza_tribunale(a.get("tribunale")), normalizza_tribunale(b.get("tribunale"))
    if not (ra and rb and ta and tb) or ra != rb or ta != tb:
        return False
    la, lb = _slug(a.get("lotto")), _slug(b.get("lotto"))
    return not (la and lb and la != lb)


def stessa_localita_superficie_prezzo(
    a: dict,
    b: dict,
    toll_superficie: float = DEFAULT_TOLL_SUPERFICIE,
    toll_prezzo: float = DEFAULT_TOLL_PREZZO,
) -> bool:
    ca, cb = _slug(a.get("comune")), _slug(b.get("comune"))
    if not ca or ca != cb:
        return False
    return (_simile(a.get("superficie_mq"), b.get("superficie_mq"), toll_superficie)
            and _simile(a.get("prezzo_base"), b.get("prezzo_base"), toll_prezzo))


def sono_duplicati(
    a: dict,
    b: dict,
    toll_superficie: float = DEFAULT_TOLL_SUPERFICIE,
    toll_prezzo: float = DEFAULT_TOLL_PREZZO,
) -> bool:
    if a.get("id") and a.get("id") == b.get("id"):
        return True
    if a.get("categoria") and b.get("categoria") and a["categoria"] != b["categoria"]:
        return False
    return stesso_rge(a, b) or stessa_localita_superficie_prezzo(a, b, toll_superficie, toll_prezzo)


def trova_duplicato(
    annuncio: dict,
    archivio: list[dict],
    toll_superficie: float = DEFAULT_TOLL_SUPERFICIE,
    toll_prezzo: float = DEFAULT_TOLL_PREZZO,
) -> dict | None:
    """Restituisce l'annuncio già archiviato che corrisponde a ``annuncio``.

    La corrispondenza per ID (anche tramite alias) ha priorità, poi RGE+tribunale,
    infine comune+superficie+prezzo.
    """
    aid = annuncio.get("id")
    for rec in archivio:
        if aid and (rec.get("id") == aid or aid in (rec.get("id_alias") or [])):
            return rec
    for rec in archivio:
        if (annuncio.get("categoria") == rec.get("categoria") or not rec.get("categoria")) and stesso_rge(annuncio, rec):
            return rec
    for rec in archivio:
        if (annuncio.get("categoria") == rec.get("categoria") or not rec.get("categoria")) and \
                stessa_localita_superficie_prezzo(annuncio, rec, toll_superficie, toll_prezzo):
            return rec
    return None


def deduplica_lista(annunci: list[dict], **toll) -> list[dict]:
    """Rimuove i duplicati all'interno di una stessa lista (mantiene il primo)."""
    out: list[dict] = []
    for a in annunci:
        if trova_duplicato(a, out, **toll) is None:
            out.append(a)
    return out
