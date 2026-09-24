"""Pannello di controllo web locale.

Avvia un piccolo server su http://127.0.0.1:<porta> (solo questo computer)
e apre il browser su una pagina da cui:

- avviare una scansione, una prova senza Telegram o l'avvio continuo;
- modificare i parametri di config.json con un modulo;
- vedere i risultati (tabella e mappa) e il registro;
- aggiornare il programma da GitHub.

Usa solo la libreria standard di Python.

Uso:
    python pannello.py                 # apre il browser
    python pannello.py --porta 8800 --no-browser
"""

from __future__ import annotations

import argparse
import json
import logging
import secrets
import shutil
import tempfile
import threading
import webbrowser
import zipfile
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

import httpx

import geo
import main as bot
import storage
import viewer
from config import carica_config, valida_config
from notifier import TelegramNotifier

log = logging.getLogger("pannello")

CARTELLA = Path(__file__).resolve().parent
PAGINA = CARTELLA / "web" / "pannello.html"
ZIP_URL = ("https://github.com/donatocannatello-cloud/BTOrder/archive/refs/heads/"
           "claude/cacciatore-affari-bot-mh8x85.zip")
# file che l'aggiornamento non deve mai sovrascrivere
PROTETTI = {"config.json", ".venv", "data", "logs", "report"}
# il .bat è in esecuzione mentre il pannello è aperto: la nuova versione viene
# salvata a parte e il .bat la installa da solo al prossimo avvio
BAT = "AFFARI_MIEI.bat"


def _ora() -> str:
    return datetime.now().isoformat(timespec="seconds")


class Controllore:
    """Esegue le scansioni in background e ne tiene lo stato."""

    def __init__(self, config_path: Path, ciclo=None):
        self.config_path = Path(config_path)
        self._ciclo = ciclo or bot.ciclo
        self._lock = threading.Lock()
        self._stop_loop = threading.Event()
        self._loop_thread: threading.Thread | None = None
        self.stato = {
            "in_corso": None,       # {"tipo", "iniziato"} durante una scansione
            "ultimo": None,         # esito dell'ultima scansione
            "loop": {"attivo": False, "ore": None, "prossimo": None},
        }

    def cfg(self) -> dict:
        return carica_config(self.config_path)

    # --- scansioni --------------------------------------------------------------
    def _esegui(self, dry_run: bool, tipo: str) -> None:
        with self._lock:
            self.stato["in_corso"] = {"tipo": tipo, "iniziato": _ora()}
            try:
                stats = self._ciclo(self.cfg(), dry_run=dry_run)
                self.stato["ultimo"] = {"tipo": tipo, "fine": _ora(), "esito": "ok", "stats": stats}
            except Exception as e:  # l'errore va mostrato nel pannello, non deve fermare il server
                log.exception("Scansione fallita")
                self.stato["ultimo"] = {"tipo": tipo, "fine": _ora(), "esito": "errore", "errore": str(e)}
            finally:
                self.stato["in_corso"] = None

    def avvia_scansione(self, dry_run: bool) -> bool:
        if self._lock.locked():
            return False
        tipo = "prova" if dry_run else "scansione"
        threading.Thread(target=self._esegui, args=(dry_run, tipo), daemon=True).start()
        return True

    def avvia_loop(self, ore: float) -> bool:
        if self.stato["loop"]["attivo"]:
            return False
        self._stop_loop.clear()
        self.stato["loop"] = {"attivo": True, "ore": ore, "prossimo": None}

        def corpo():
            while not self._stop_loop.is_set():
                self._esegui(False, "continuo")
                prossimo = datetime.now() + timedelta(hours=ore)
                self.stato["loop"]["prossimo"] = prossimo.isoformat(timespec="minutes")
                if self._stop_loop.wait(ore * 3600):
                    break
            self.stato["loop"] = {"attivo": False, "ore": None, "prossimo": None}

        self._loop_thread = threading.Thread(target=corpo, daemon=True)
        self._loop_thread.start()
        return True

    def ferma_loop(self) -> None:
        # la scansione eventualmente in corso termina normalmente
        self._stop_loop.set()
        self.stato["loop"]["prossimo"] = None


# --- funzioni di servizio -------------------------------------------------------

def coda_log(path: str | Path, righe: int = 200) -> list[str]:
    p = Path(path)
    if not p.exists():
        return []
    with p.open("rb") as f:
        f.seek(0, 2)
        f.seek(max(0, f.tell() - 256_000))
        testo = f.read().decode("utf-8", errors="replace")
    return testo.splitlines()[-righe:]


def riepilogo_annunci(path: str | Path) -> dict:
    annunci = storage.load_annunci(path)
    return {
        "totale": len(annunci),
        "sopra_60": sum(1 for a in annunci if (a.get("punteggio") or 0) >= 60),
        "da_notificare": sum(1 for a in annunci if not a.get("notificato")),
    }


def aggiorna_programma(destinazione: Path = CARTELLA, url: str = ZIP_URL, client: httpx.Client | None = None) -> list[str]:
    """Scarica l'ultima versione da GitHub e sovrascrive i file del programma.

    Non tocca config.json, i dati, i log e la .venv. Il .bat, se cambiato,
    viene salvato come AFFARI_MIEI.bat.nuovo. Restituisce i file aggiornati.
    """
    client = client or httpx.Client(timeout=60, follow_redirects=True)
    resp = client.get(url)
    resp.raise_for_status()
    aggiornati: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        zpath = Path(tmp) / "programma.zip"
        zpath.write_bytes(resp.content)
        with zipfile.ZipFile(zpath) as z:
            z.extractall(tmp)
        radici = [p for p in Path(tmp).rglob("cacciatore_affari") if (p / "main.py").exists()]
        if not radici:
            raise RuntimeError("cartella cacciatore_affari non trovata nello ZIP")
        radice = radici[0]
        for src in radice.rglob("*"):
            rel = src.relative_to(radice)
            if rel.parts[0] in PROTETTI or src.is_dir():
                continue
            dest = destinazione / rel
            if rel.as_posix() == BAT:
                attuale = destinazione / BAT
                if attuale.exists() and attuale.read_bytes() == src.read_bytes():
                    continue
                dest = destinazione / (BAT + ".nuovo")
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(src, dest)
            aggiornati.append(rel.as_posix())
    # al prossimo avvio del .bat le dipendenze vengono reinstallate
    (destinazione / ".venv" / "installato.ok").unlink(missing_ok=True)
    return aggiornati


# --- server HTTP -----------------------------------------------------------------

class Pannello(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, indirizzo, controllore: Controllore):
        super().__init__(indirizzo, Gestore)
        self.controllore = controllore
        # chiave segreta inserita nella pagina: le richieste che modificano
        # qualcosa devono presentarla, così un altro sito aperto nel browser
        # non può comandare il pannello.
        self.chiave = secrets.token_urlsafe(24)


class Gestore(BaseHTTPRequestHandler):
    server: Pannello

    def log_message(self, fmt, *args):  # niente righe di accesso nel terminale
        log.debug("%s - %s", self.address_string(), fmt % args)

    # --- risposte ---------------------------------------------------------------
    def _invia(self, codice: int, corpo: bytes, tipo: str) -> None:
        self.send_response(codice)
        self.send_header("Content-Type", tipo)
        self.send_header("Content-Length", str(len(corpo)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(corpo)

    def _json(self, dati, codice: int = 200) -> None:
        self._invia(codice, json.dumps(dati, ensure_ascii=False).encode(), "application/json; charset=utf-8")

    def _errore(self, messaggio: str, codice: int = 400, **extra) -> None:
        self._json({"errore": messaggio, **extra}, codice)

    def _host_valido(self) -> bool:
        host = (self.headers.get("Host") or "").split(":")[0]
        return host in ("127.0.0.1", "localhost")

    def _corpo(self) -> dict:
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(n) or b"{}") if n else {}

    @property
    def ctl(self) -> Controllore:
        return self.server.controllore

    # --- GET ----------------------------------------------------------------------
    def do_GET(self):
        if not self._host_valido():
            return self._errore("host non consentito", 403)
        url = urlsplit(self.path)
        try:
            if url.path == "/":
                html = PAGINA.read_text(encoding="utf-8").replace("__CHIAVE__", self.server.chiave)
                return self._invia(200, html.encode(), "text/html; charset=utf-8")
            if url.path == "/risultati":
                cfg = self.ctl.cfg()
                html = viewer.genera_html(storage.load_annunci(cfg["percorsi"]["annunci"]))
                return self._invia(200, html.encode(), "text/html; charset=utf-8")
            if url.path == "/api/stato":
                cfg = self.ctl.cfg()
                return self._json({**self.ctl.stato, "annunci": riepilogo_annunci(cfg["percorsi"]["annunci"]),
                                   "telegram_configurato": bool(cfg["telegram"]["token"] and cfg["telegram"]["chat_id"])})
            if url.path == "/api/config":
                return self._json({
                    "config": storage.load(self.ctl.config_path),
                    "regioni": {nome.title(): prov for nome, prov in geo.REGIONI.items()},
                })
            if url.path == "/api/log":
                righe = int(parse_qs(url.query).get("righe", ["200"])[0])
                return self._json({"righe": coda_log(self.ctl.cfg()["percorsi"]["log"], min(righe, 2000))})
        except Exception as e:
            log.exception("Errore pannello su %s", url.path)
            return self._errore(str(e), 500)
        self._errore("pagina non trovata", 404)

    # --- POST ---------------------------------------------------------------------
    def do_POST(self):
        if not self._host_valido() or self.headers.get("X-Chiave") != self.server.chiave:
            return self._errore("richiesta non autorizzata", 403)
        url = urlsplit(self.path)
        try:
            corpo = self._corpo()
        except ValueError:
            return self._errore("JSON non valido")
        try:
            if url.path == "/api/config":
                cfg = corpo.get("config")
                if not isinstance(cfg, dict):
                    return self._errore("configurazione mancante")
                errori = valida_config(cfg)
                if errori:
                    return self._errore("Controlla i valori inseriti", 422, dettagli=errori)
                storage.save(self.ctl.config_path, cfg)
                log.info("Configurazione salvata dal pannello")
                return self._json({"ok": True})

            if url.path == "/api/azione":
                azione = corpo.get("azione")
                if azione in ("scansiona", "prova"):
                    if not self.ctl.avvia_scansione(dry_run=azione == "prova"):
                        return self._errore("C'è già una scansione in corso", 409)
                    return self._json({"ok": True})
                if azione == "avvia_loop":
                    ore = corpo.get("ore")
                    if isinstance(ore, bool) or not isinstance(ore, (int, float)) or not 0.25 <= ore <= 168:
                        return self._errore("Intervallo non valido (da 0,25 a 168 ore)")
                    if not self.ctl.avvia_loop(float(ore)):
                        return self._errore("L'avvio continuo è già attivo", 409)
                    return self._json({"ok": True})
                if azione == "ferma_loop":
                    self.ctl.ferma_loop()
                    return self._json({"ok": True})
                if azione == "test_telegram":
                    tg = self.ctl.cfg()["telegram"]
                    if not (tg["token"] and tg["chat_id"]):
                        return self._errore("Inserisci e salva token e chat_id prima della prova")
                    ok = TelegramNotifier(tg["token"], tg["chat_id"]).invia(
                        "✅ Cacciatore Affari: collegamento Telegram funzionante.")
                    return self._json({"ok": True}) if ok else \
                        self._errore("Invio non riuscito: controlla token e chat_id (dettagli nel registro)", 502)
                if azione == "aggiorna":
                    if self.ctl._lock.locked():
                        return self._errore("Attendi la fine della scansione in corso", 409)
                    file = aggiorna_programma()
                    log.info("Programma aggiornato (%d file)", len(file))
                    return self._json({"ok": True, "file": len(file)})
                if azione == "chiudi":
                    self._json({"ok": True})
                    threading.Thread(target=self.server.shutdown, daemon=True).start()
                    return None
                return self._errore(f"azione sconosciuta: {azione}")
        except Exception as e:
            log.exception("Errore pannello su %s", url.path)
            return self._errore(str(e), 500)
        self._errore("pagina non trovata", 404)


def crea_server(controllore: Controllore, porta: int) -> Pannello:
    """Crea il server; se la porta è occupata prova le successive."""
    for p in range(porta, porta + 20):
        try:
            return Pannello(("127.0.0.1", p), controllore)
        except OSError:
            continue
    return Pannello(("127.0.0.1", 0), controllore)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Pannello di controllo web del Cacciatore Affari")
    ap.add_argument("--config", default=str(CARTELLA / "config.json"))
    ap.add_argument("--porta", type=int, default=None)
    ap.add_argument("--no-browser", action="store_true")
    args = ap.parse_args(argv)

    cfg = carica_config(args.config)
    bot.setup_logging(cfg["percorsi"]["log"])
    porta = args.porta or (cfg.get("pannello") or {}).get("porta", 8765)
    server = crea_server(Controllore(Path(args.config)), porta)
    indirizzo = f"http://127.0.0.1:{server.server_address[1]}/"
    log.info("Pannello attivo su %s (chiudi questa finestra o usa 'Chiudi pannello' per uscire)", indirizzo)
    if not args.no_browser:
        threading.Timer(0.5, webbrowser.open, args=(indirizzo,)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    log.info("Pannello chiuso")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
