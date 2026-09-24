"""Persistenza su file JSON locali.

Tutto l'accesso ai dati del progetto passa da qui. Le scritture sono atomiche:
il contenuto viene scritto in un file temporaneo nella stessa cartella e poi
sostituito al file di destinazione con ``os.replace``, così un'interruzione
a metà scrittura non lascia mai un file JSON troncato.
"""

from __future__ import annotations

import json
import logging
import os
import tempfile
from pathlib import Path
from typing import Any, Callable, Iterable

log = logging.getLogger(__name__)

MergeFn = Callable[[dict, dict], dict]


def load(path: str | os.PathLike, default: Any = None) -> Any:
    """Legge un file JSON. Se il file non esiste restituisce ``default``."""
    p = Path(path)
    if not p.exists():
        return default
    with p.open("r", encoding="utf-8") as f:
        return json.load(f)


def save(path: str | os.PathLike, data: Any) -> None:
    """Salva ``data`` come JSON in modo atomico (file temporaneo + os.replace)."""
    save_text(path, json.dumps(data, ensure_ascii=False, indent=2))


def save_text(path: str | os.PathLike, text: str) -> None:
    """Scrive un file di testo in modo atomico (file temporaneo + os.replace)."""
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=f".{p.name}.", suffix=".tmp", dir=p.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(text)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp_name, p)
    except BaseException:
        try:
            os.unlink(tmp_name)
        except FileNotFoundError:
            pass
        raise


def _default_merge(old: dict, new: dict) -> dict:
    merged = dict(old)
    merged.update(new)
    return merged


def upsert_records(
    records: list[dict],
    new: dict,
    key: str = "id",
    merge: MergeFn | None = None,
) -> tuple[dict, bool]:
    """Inserisce o aggiorna ``new`` in una lista in memoria.

    Restituisce ``(record_risultante, is_new)``.
    """
    merge = merge or _default_merge
    for i, rec in enumerate(records):
        if rec.get(key) == new.get(key):
            records[i] = merge(rec, new)
            return records[i], False
    records.append(new)
    return new, True


def upsert(
    path: str | os.PathLike,
    record: dict,
    key: str = "id",
    merge: MergeFn | None = None,
) -> tuple[dict, bool]:
    """Inserisce o aggiorna un singolo record in un file JSON contenente una lista."""
    records = load(path, default=[])
    result, is_new = upsert_records(records, record, key=key, merge=merge)
    save(path, records)
    return result, is_new


def upsert_many(
    path: str | os.PathLike,
    items: Iterable[dict],
    key: str = "id",
    merge: MergeFn | None = None,
) -> list[tuple[dict, bool]]:
    """Come :func:`upsert` ma con una sola lettura e una sola scrittura su disco."""
    records = load(path, default=[])
    results = [upsert_records(records, it, key=key, merge=merge) for it in items]
    save(path, records)
    return results


# --- Scorciatoie per l'archivio annunci -------------------------------------

def load_annunci(path: str | os.PathLike) -> list[dict]:
    data = load(path, default=[])
    if not isinstance(data, list):
        raise ValueError(f"{path}: atteso un array JSON di annunci")
    return data


def save_annunci(path: str | os.PathLike, annunci: list[dict]) -> None:
    save(path, annunci)
    log.debug("Salvati %d annunci in %s", len(annunci), path)
