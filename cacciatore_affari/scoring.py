"""Calcolo del punteggio "affare" (0-100).

Componenti (ognuna normalizzata tra 0 e 1):

- ``eur_mq``: €/m² rispetto alla mediana della stessa provincia e categoria,
  calcolata sui dati raccolti. Rapporto <= ``rapporto_ottimo`` → 1,
  rapporto >= ``rapporto_pessimo`` → 0, lineare nel mezzo.
- ``ribasso``: percentuale di ribasso rispetto alla prima base d'asta nota.
  Se lo storico non la contiene la si stima dal numero di tentativi
  (ogni asta deserta riduce la base di ``ribasso_stimato_per_tentativo``).
  ``ribasso >= ribasso_massimo`` → 1.
- ``tentativi``: numero di aste andate deserte (tentativo - 1);
  ``>= tentativi_massimi`` → 1.

Il punteggio è la media pesata (pesi da config.json) delle sole componenti
calcolabili: se ad esempio manca la superficie, il peso di ``eur_mq`` viene
ridistribuito sulle altre. Senza alcuna componente il punteggio è ``None``.
"""

from __future__ import annotations

import statistics
from collections import defaultdict

DEFAULT_SCORING = {
    "pesi": {"eur_mq": 0.5, "ribasso": 0.3, "tentativi": 0.2},
    "rapporto_ottimo": 0.5,
    "rapporto_pessimo": 1.5,
    "ribasso_massimo": 0.6,
    "tentativi_massimi": 4,
    "ribasso_stimato_per_tentativo": 0.25,
    "campioni_minimi_mediana": 3,
}


def _clamp(x: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, x))


def _cfg(cfg: dict | None) -> dict:
    out = dict(DEFAULT_SCORING)
    out["pesi"] = dict(DEFAULT_SCORING["pesi"])
    if cfg:
        for k, v in cfg.items():
            if k == "pesi":
                out["pesi"].update(v)
            else:
                out[k] = v
    return out


def mediane_eur_mq(annunci: list[dict], campioni_minimi: int = 3) -> dict:
    """Mediane di €/m² per (categoria, provincia) e per categoria (chiave provincia None).

    Una provincia con meno di ``campioni_minimi`` annunci non ha mediana propria:
    lo scoring userà la mediana complessiva della categoria.
    """
    per_prov: dict[tuple, list[float]] = defaultdict(list)
    per_cat: dict[str, list[float]] = defaultdict(list)
    for a in annunci:
        v = a.get("eur_mq")
        if v is None or v <= 0:
            continue
        cat = a.get("categoria")
        per_prov[(cat, a.get("provincia"))].append(v)
        per_cat[cat].append(v)
    out: dict = {}
    for key, vals in per_prov.items():
        if key[1] and len(vals) >= campioni_minimi:
            out[key] = statistics.median(vals)
    for cat, vals in per_cat.items():
        if len(vals) >= campioni_minimi:
            out[(cat, None)] = statistics.median(vals)
    return out


def mediana_riferimento(annuncio: dict, mediane: dict) -> float | None:
    cat = annuncio.get("categoria")
    return mediane.get((cat, annuncio.get("provincia"))) or mediane.get((cat, None))


def prima_base_asta(annuncio: dict, ribasso_per_tentativo: float) -> float | None:
    storico = annuncio.get("storico_prezzi") or []
    prezzi = [s["prezzo_base"] for s in storico if s.get("prezzo_base")]
    primo = prezzi[0] if prezzi else annuncio.get("prezzo_base")
    if primo is None:
        return None
    # Se il primo prezzo visto non era al primo tentativo, stimiamo la base originaria
    tent = None
    if storico and storico[0].get("numero_tentativo"):
        tent = storico[0]["numero_tentativo"]
    elif not storico:
        tent = annuncio.get("numero_tentativo")
    if tent and tent > 1 and 0 < ribasso_per_tentativo < 1:
        primo = primo / ((1 - ribasso_per_tentativo) ** (tent - 1))
    return primo


def componenti(annuncio: dict, mediane: dict, cfg: dict | None = None) -> dict:
    c = _cfg(cfg)
    out: dict[str, float] = {}

    eur_mq = annuncio.get("eur_mq")
    med = mediana_riferimento(annuncio, mediane)
    if eur_mq and med:
        rapporto = eur_mq / med
        span = c["rapporto_pessimo"] - c["rapporto_ottimo"]
        out["eur_mq"] = _clamp((c["rapporto_pessimo"] - rapporto) / span) if span > 0 else 0.0

    prezzo = annuncio.get("prezzo_base")
    primo = prima_base_asta(annuncio, c["ribasso_stimato_per_tentativo"])
    if prezzo and primo:
        ribasso = _clamp(1 - prezzo / primo)
        out["ribasso"] = _clamp(ribasso / c["ribasso_massimo"]) if c["ribasso_massimo"] > 0 else 0.0

    tent = annuncio.get("numero_tentativo")
    if tent:
        deserte = max(0, tent - 1)
        out["tentativi"] = _clamp(deserte / c["tentativi_massimi"]) if c["tentativi_massimi"] > 0 else 0.0
    return out


def calcola_punteggio(annuncio: dict, mediane: dict, cfg: dict | None = None) -> tuple[float | None, dict]:
    c = _cfg(cfg)
    comp = componenti(annuncio, mediane, c)
    pesi = {k: float(c["pesi"].get(k, 0)) for k in comp}
    tot = sum(pesi.values())
    if not comp or tot <= 0:
        return None, {"componenti": comp}
    score = sum(comp[k] * pesi[k] for k in comp) / tot * 100
    dettaglio = {
        "componenti": {k: round(v, 3) for k, v in comp.items()},
        "mediana_eur_mq": mediana_riferimento(annuncio, mediane),
    }
    return round(score, 1), dettaglio


def assegna_punteggi(annunci: list[dict], cfg: dict | None = None) -> list[dict]:
    """Calcola le mediane su tutti gli annunci e aggiorna ``punteggio`` in place."""
    c = _cfg(cfg)
    mediane = mediane_eur_mq(annunci, c["campioni_minimi_mediana"])
    for a in annunci:
        a["punteggio"], a["dettaglio_punteggio"] = calcola_punteggio(a, mediane, c)
    return annunci
