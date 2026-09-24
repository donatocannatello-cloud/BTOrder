"""Caricamento di config.json.

I percorsi relativi sono risolti rispetto alla cartella del file di
configurazione. Token e chat_id Telegram possono essere forniti anche con le
variabili d'ambiente ``TELEGRAM_TOKEN`` e ``TELEGRAM_CHAT_ID``.
"""

from __future__ import annotations

import os
from pathlib import Path

import storage


def carica_config(path: str | Path) -> dict:
    path = Path(path).resolve()
    cfg = storage.load(path)
    if cfg is None:
        raise FileNotFoundError(
            f"{path} non trovato: copia config.example.json in config.json e personalizzalo")
    base = path.parent
    cfg["_base_dir"] = str(base)

    percorsi = cfg.setdefault("percorsi", {})
    percorsi.setdefault("annunci", "data/annunci.json")
    percorsi.setdefault("perizie", "data/perizie")
    percorsi.setdefault("log", "logs/cacciatore_affari.log")
    percorsi.setdefault("report", "report/annunci.html")
    for k, v in percorsi.items():
        percorsi[k] = str((base / v).resolve())
    for fonte in (cfg.get("fonti") or {}).values():
        if fonte.get("salva_risposte_grezze"):
            fonte["salva_risposte_grezze"] = str((base / fonte["salva_risposte_grezze"]).resolve())

    tg = cfg.setdefault("telegram", {})
    tg["token"] = os.environ.get("TELEGRAM_TOKEN") or tg.get("token") or ""
    tg["chat_id"] = os.environ.get("TELEGRAM_CHAT_ID") or tg.get("chat_id") or ""
    return cfg


def filtri_categoria(cfg: dict, categoria: str) -> dict:
    """Filtri da passare a ``Adapter.fetch_listings`` per una categoria."""
    cat = dict(cfg["categorie"][categoria])
    cat.pop("abilitata", None)
    cat.pop("soglia_punteggio", None)
    cat["categoria"] = categoria
    cat.setdefault("regioni", cfg.get("regioni") or [])
    cat.setdefault("province", cfg.get("province") or [])
    return cat


def _numero(v, nome: str, errori: list[str], minimo: float | None = None,
            massimo: float | None = None, nullo: bool = False) -> None:
    if v is None and nullo:
        return
    if isinstance(v, bool) or not isinstance(v, (int, float)):
        errori.append(f"{nome}: deve essere un numero")
    elif minimo is not None and v < minimo:
        errori.append(f"{nome}: deve essere almeno {minimo:g}")
    elif massimo is not None and v > massimo:
        errori.append(f"{nome}: non può superare {massimo:g}")


def valida_config(cfg: dict) -> list[str]:
    """Controlla i valori modificabili dal pannello. Restituisce gli errori trovati."""
    errori: list[str] = []
    for chiave in ("regioni", "province"):
        v = cfg.get(chiave, [])
        if not isinstance(v, list) or not all(isinstance(x, str) for x in v):
            errori.append(f"{chiave}: deve essere un elenco di nomi")
    _numero(cfg.get("soglia_punteggio", 60), "Soglia punteggio", errori, 0, 100)

    categorie = cfg.get("categorie")
    if not isinstance(categorie, dict) or not categorie:
        errori.append("categorie: serve almeno una categoria")
    else:
        for nome, c in categorie.items():
            for campo in ("prezzo_max", "prezzo_min", "superficie_min"):
                _numero(c.get(campo), f"{nome} · {campo}", errori, 0, nullo=True)
            _numero(c.get("soglia_punteggio"), f"{nome} · soglia punteggio", errori, 0, 100, nullo=True)
            for campo in ("parole_chiave", "parole_escluse"):
                v = c.get(campo, [])
                if not isinstance(v, list) or not all(isinstance(x, str) for x in v):
                    errori.append(f"{nome} · {campo}: deve essere un elenco di parole")

    pesi = (cfg.get("scoring") or {}).get("pesi", {})
    for k, v in pesi.items():
        _numero(v, f"Peso {k}", errori, 0)
    if pesi and not errori and sum(pesi.values()) <= 0:
        errori.append("Pesi del punteggio: almeno uno deve essere maggiore di zero")

    http = cfg.get("http") or {}
    _numero(http.get("min_delay", 3), "Pausa minima tra richieste", errori, 3)
    _numero(http.get("max_delay", 5), "Pausa massima tra richieste", errori, 3)
    if not errori and http.get("max_delay", 5) < http.get("min_delay", 3):
        errori.append("La pausa massima deve essere maggiore o uguale alla minima")

    pvp = (cfg.get("fonti") or {}).get("pvp") or {}
    _numero(pvp.get("page_size", 50), "Annunci per pagina", errori, 1, 200)
    _numero(pvp.get("max_pagine", 40), "Pagine massime", errori, 1, 1000)

    pannello = cfg.get("pannello") or {}
    _numero(pannello.get("ore_loop", 6), "Intervallo avvio continuo (ore)", errori, 0.25, 168)
    return errori
