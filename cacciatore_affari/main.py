"""Cacciatore di affari: ciclo completo fetch → normalizza → dedup → salva → score → notifica.

Uso:
    python main.py                 # un solo ciclo
    python main.py --loop 6        # un ciclo ogni 6 ore
    python main.py --dry-run       # nessun invio Telegram (messaggi solo nel log)
"""

from __future__ import annotations

import argparse
import logging
import sys
import time
from logging.handlers import RotatingFileHandler
from pathlib import Path

import dedup
import models
import scoring
import storage
from adapters import REGISTRO
from config import carica_config, filtri_categoria
from http_client import PoliteClient
from notifier import TelegramNotifier, da_notificare
from perizie import scarica_perizia
from viewer import scrivi_report

log = logging.getLogger("cacciatore_affari")

# chiavi di config.json che riguardano il ciclo e non il singolo adattatore
_CHIAVI_CICLO = {"abilitata", "scarica_dettagli", "scarica_perizie"}


def setup_logging(log_file: str, verbose: bool = False) -> None:
    Path(log_file).parent.mkdir(parents=True, exist_ok=True)
    fmt = logging.Formatter("%(asctime)s %(levelname)-7s %(name)s: %(message)s")
    root = logging.getLogger()
    root.setLevel(logging.DEBUG if verbose else logging.INFO)
    root.handlers.clear()
    fh = RotatingFileHandler(log_file, maxBytes=5_000_000, backupCount=5, encoding="utf-8")
    fh.setFormatter(fmt)
    sh = logging.StreamHandler(sys.stdout)
    sh.setFormatter(fmt)
    root.addHandler(fh)
    root.addHandler(sh)
    logging.getLogger("httpx").setLevel(logging.WARNING)


def _integra(archivio: list[dict], ann: dict, toll: dict) -> str:
    """Inserisce ``ann`` nell'archivio o lo fonde col duplicato. Restituisce l'esito."""
    dup = dedup.trova_duplicato(ann, archivio, **toll)
    if dup is None:
        archivio.append(ann)
        return "nuovo"
    idx = next(i for i, r in enumerate(archivio) if r is dup)
    archivio[idx] = models.merge_annuncio(dup, ann)
    return "ribassato" if models.prezzo_ribassato(archivio[idx]) and \
        len(archivio[idx]["storico_prezzi"]) > len(dup.get("storico_prezzi") or []) else "aggiornato"


def ciclo(cfg: dict, dry_run: bool = False) -> dict:
    path_annunci = cfg["percorsi"]["annunci"]
    archivio = storage.load_annunci(path_annunci)
    toll = {
        "toll_superficie": cfg.get("dedup", {}).get("tolleranza_superficie", dedup.DEFAULT_TOLL_SUPERFICIE),
        "toll_prezzo": cfg.get("dedup", {}).get("tolleranza_prezzo", dedup.DEFAULT_TOLL_PREZZO),
    }
    stats = {"letti": 0, "nuovi": 0, "aggiornati": 0, "ribassati": 0, "notificati": 0}
    ora = models.now_iso()
    log.info("=== Inizio ciclo (%d annunci in archivio) ===", len(archivio))

    with PoliteClient(**cfg.get("http", {})) as client:
        for nome_fonte, fcfg in (cfg.get("fonti") or {}).items():
            if not fcfg.get("abilitata", True):
                continue
            cls = REGISTRO.get(nome_fonte)
            if cls is None:
                log.warning("Fonte '%s' non implementata, ignorata", nome_fonte)
                continue
            adapter = cls(client, {k: v for k, v in fcfg.items() if k not in _CHIAVI_CICLO})

            for categoria, ccfg in (cfg.get("categorie") or {}).items():
                if not ccfg.get("abilitata", True) or not adapter.supporta(categoria):
                    continue
                log.info("Fonte %s, categoria %s", nome_fonte, categoria)
                try:
                    grezzi = adapter.fetch_listings(filtri_categoria(cfg, categoria))
                except Exception:  # un sito che cambia non deve fermare l'intero ciclo
                    log.exception("Errore durante la lettura di %s/%s", nome_fonte, categoria)
                    continue

                for raw in grezzi:
                    stats["letti"] += 1
                    ann = models.normalizza(raw, now=ora)
                    nuovo = dedup.trova_duplicato(ann, archivio, **toll) is None
                    if nuovo and fcfg.get("scarica_dettagli", True):
                        dettagli = adapter.fetch_detail(ann)
                        if dettagli:
                            ann = models.normalizza({**ann, **dettagli}, now=ora)
                    esito = _integra(archivio, ann, toll)
                    stats["nuovi" if esito == "nuovo" else "aggiornati"] += 1
                    stats["ribassati"] += esito == "ribassato"

                if fcfg.get("scarica_perizie"):
                    for a in archivio:
                        if a.get("fonte") == nome_fonte and a.get("url_perizia") and not a.get("perizia_locale"):
                            p = scarica_perizia(client, a["url_perizia"], a["id"], cfg["percorsi"]["perizie"])
                            if p:
                                a["perizia_locale"] = str(p)

                storage.save_annunci(path_annunci, archivio)  # checkpoint per categoria

    scoring.assegna_punteggi(archivio, cfg.get("scoring"))
    storage.save_annunci(path_annunci, archivio)

    soglia = cfg.get("soglia_punteggio", 60)
    soglie_cat = {c: v["soglia_punteggio"] for c, v in (cfg.get("categorie") or {}).items()
                  if "soglia_punteggio" in v}
    candidati = da_notificare(archivio, soglia, soglie_cat)
    notifier = TelegramNotifier(cfg["telegram"].get("token"), cfg["telegram"].get("chat_id"), dry_run=dry_run)
    if notifier.dry_run and not dry_run:
        log.warning("Token/chat_id Telegram non configurati: notifiche solo nel log")
    stats["notificati"] = notifier.notifica(candidati)
    storage.save_annunci(path_annunci, archivio)

    if cfg["percorsi"].get("report"):
        try:
            log.info("Report HTML aggiornato: %s", scrivi_report(path_annunci, cfg["percorsi"]["report"]))
        except OSError as e:
            log.warning("Report HTML non generato: %s", e)

    log.info("=== Fine ciclo: %s (da notificare: %d, archivio: %d) ===",
             stats, len(candidati), len(archivio))
    return stats


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Bot che cerca affari nelle aste giudiziarie")
    p.add_argument("--config", default=str(Path(__file__).with_name("config.json")))
    p.add_argument("--loop", type=float, metavar="ORE", help="ripete il ciclo ogni ORE ore")
    p.add_argument("--dry-run", action="store_true", help="non invia messaggi Telegram")
    p.add_argument("-v", "--verbose", action="store_true")
    args = p.parse_args(argv)

    cfg = carica_config(args.config)
    setup_logging(cfg["percorsi"]["log"], args.verbose)

    if not args.loop:
        ciclo(cfg, dry_run=args.dry_run)
        return 0
    if args.loop <= 0:
        p.error("--loop richiede un numero di ore positivo")
    try:
        while True:
            try:
                ciclo(cfg, dry_run=args.dry_run)
            except Exception:
                log.exception("Ciclo fallito")
            log.info("Prossimo ciclo tra %.1f ore", args.loop)
            time.sleep(args.loop * 3600)
    except KeyboardInterrupt:
        log.info("Interrotto dall'utente")
    return 0


if __name__ == "__main__":
    sys.exit(main())
