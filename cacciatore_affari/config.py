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
