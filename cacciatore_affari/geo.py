"""Tabelle geografiche per i filtri per regione/provincia.

Per ora è censita solo la Lombardia: per estendere la ricerca ad altre
regioni basta aggiungere la voce corrispondente in ``REGIONI``.
"""

from __future__ import annotations

REGIONI: dict[str, dict[str, str]] = {
    "lombardia": {
        "BG": "Bergamo",
        "BS": "Brescia",
        "CO": "Como",
        "CR": "Cremona",
        "LC": "Lecco",
        "LO": "Lodi",
        "MN": "Mantova",
        "MI": "Milano",
        "MB": "Monza e della Brianza",
        "PV": "Pavia",
        "SO": "Sondrio",
        "VA": "Varese",
    },
}

_NOME_A_SIGLA = {nome.lower(): sigla for prov in REGIONI.values() for sigla, nome in prov.items()}
_NOME_A_SIGLA.update({"monza": "MB", "monza brianza": "MB", "monza-brianza": "MB"})


def sigla_provincia(valore: str | None) -> str | None:
    """"MI", "mi", "Milano", "(MI)" → "MI". Restituisce None se sconosciuta."""
    if not valore:
        return None
    v = str(valore).strip().strip("()").strip()
    if len(v) == 2 and v.isalpha():
        return v.upper()
    return _NOME_A_SIGLA.get(v.lower())


def sigle_ammesse(regioni: list[str] | None, province: list[str] | None) -> set[str]:
    """Insieme delle sigle di provincia ammesse dai filtri.

    Se ``province`` è valorizzato prevale; altrimenti si usano tutte le
    province delle ``regioni`` indicate. Insieme vuoto = nessun filtro.
    """
    if province:
        return {s for p in province if (s := sigla_provincia(p))}
    out: set[str] = set()
    for r in regioni or []:
        out.update(REGIONI.get(r.strip().lower(), {}).keys())
    return out
