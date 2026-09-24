"""Gestione delle perizie (PDF).

Il download è già funzionante; l'analisi del contenuto è solo uno stub da
completare in seguito con un modello locale via Ollama.
"""

from __future__ import annotations

import logging
import re
from pathlib import Path

import httpx

from http_client import PoliteClient, RobotsDisallowed

log = logging.getLogger(__name__)


def nome_file_perizia(annuncio_id: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "_", annuncio_id) + ".pdf"


def scarica_perizia(client: PoliteClient, url: str, annuncio_id: str, cartella: str | Path) -> Path | None:
    """Scarica il PDF della perizia in ``cartella``; se già presente non lo riscarica."""
    dest = Path(cartella) / nome_file_perizia(annuncio_id)
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    try:
        resp = client.get(url)
    except (httpx.HTTPError, RobotsDisallowed) as e:
        log.warning("Perizia %s non scaricata: %s", url, e)
        return None
    if b"%PDF" not in resp.content[:1024]:
        log.warning("Perizia %s: il contenuto non sembra un PDF, ignorato", url)
        return None
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".pdf.tmp")
    tmp.write_bytes(resp.content)
    tmp.replace(dest)
    log.info("Perizia salvata in %s", dest)
    return dest


def analizza_perizia(pdf_path: str | Path) -> dict:
    """Estrae dalla perizia le informazioni utili alla valutazione.

    TODO: implementare con Ollama (estrazione testo dal PDF + prompt a un
    modello locale). Il dizionario restituito dovrà contenere ad esempio:
    ``superficie_mq``, ``stato_occupazione``, ``abusi_edilizi``,
    ``vincoli``, ``stato_conservativo``, ``valore_stima``, ``note``.
    """
    return {"analizzata": False, "pdf": str(pdf_path)}
