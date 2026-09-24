import io
import socket
import json
import threading
import time
import zipfile

import httpx
import pytest

import pannello
import storage


@pytest.fixture
def server(tmp_path):
    cfg = json.load(open("config.example.json", encoding="utf-8"))
    cfg["percorsi"] = {k: str(tmp_path / v) for k, v in cfg["percorsi"].items()}
    path = tmp_path / "config.json"
    storage.save(path, cfg)
    storage.save_annunci(tmp_path / "data" / "annunci.json", [{"id": "pvp:1", "punteggio": 70}])

    chiamate = []

    def finto_ciclo(c, dry_run=False):
        chiamate.append(dry_run)
        time.sleep(0.2)
        return {"letti": 1, "nuovi": 1, "aggiornati": 0, "ribassati": 0, "notificati": 0}

    ctl = pannello.Controllore(path, ciclo=finto_ciclo)
    srv = pannello.crea_server(ctl, 0)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    base = f"http://127.0.0.1:{srv.server_address[1]}"
    with httpx.Client(base_url=base, timeout=5) as client:
        yield client, srv, path, chiamate
    srv.shutdown()
    srv.server_close()


def _post(client, srv, url, dati):
    return client.post(url, json=dati, headers={"X-Chiave": srv.chiave})


def _attendi(condizione, secondi=3.0):
    fine = time.monotonic() + secondi
    while time.monotonic() < fine:
        if condizione():
            return True
        time.sleep(0.05)
    return False


def test_pagina_e_stato(server):
    client, srv, _, _ = server
    assert srv.chiave in client.get("/").text
    stato = client.get("/api/stato").json()
    assert stato["annunci"]["totale"] == 1 and stato["annunci"]["sopra_60"] == 1
    assert "pvp:1" in client.get("/risultati").text


def test_comandi_senza_chiave_rifiutati(server):
    client, _, _, chiamate = server
    assert client.post("/api/azione", json={"azione": "scansiona"}).status_code == 403
    assert client.post("/api/azione", json={"azione": "scansiona"},
                       headers={"X-Chiave": "sbagliata"}).status_code == 403
    assert chiamate == []


def test_host_estraneo_rifiutato(server):
    client, _, _, _ = server
    assert client.get("/api/config", headers={"Host": "sito-malevolo.example"}).status_code == 403


def test_salva_config_valida_e_non_valida(server):
    client, srv, path, _ = server
    cfg = client.get("/api/config").json()["config"]
    cfg["province"] = ["BG"]
    assert _post(client, srv, "/api/config", {"config": cfg}).status_code == 200
    assert storage.load(path)["province"] == ["BG"]

    cfg["http"]["min_delay"] = 1
    r = _post(client, srv, "/api/config", {"config": cfg})
    assert r.status_code == 422 and any("almeno 3" in d for d in r.json()["dettagli"])
    assert storage.load(path)["http"]["min_delay"] == 3  # file invariato


def test_scansione_in_background(server):
    client, srv, _, chiamate = server
    assert _post(client, srv, "/api/azione", {"azione": "prova"}).status_code == 200
    assert _post(client, srv, "/api/azione", {"azione": "scansiona"}).status_code == 409  # già in corso
    assert _attendi(lambda: client.get("/api/stato").json()["ultimo"])
    ultimo = client.get("/api/stato").json()["ultimo"]
    assert ultimo["esito"] == "ok" and ultimo["stats"]["nuovi"] == 1
    assert chiamate == [True]


def test_avvio_continuo(server):
    client, srv, _, chiamate = server
    assert _post(client, srv, "/api/azione", {"azione": "avvia_loop", "ore": 0}).status_code == 400
    assert _post(client, srv, "/api/azione", {"azione": "avvia_loop", "ore": 1}).status_code == 200
    assert _attendi(lambda: client.get("/api/stato").json()["loop"]["prossimo"])
    assert _post(client, srv, "/api/azione", {"azione": "ferma_loop"}).status_code == 200
    assert _attendi(lambda: not client.get("/api/stato").json()["loop"]["attivo"])
    assert chiamate == [False]


def test_aggiorna_programma_non_tocca_config(tmp_path):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("BTOrder-x/cacciatore_affari/main.py", "nuovo")
        z.writestr("BTOrder-x/cacciatore_affari/config.json", "{}")
        z.writestr("BTOrder-x/cacciatore_affari/AFFARI_MIEI.bat", "x")
        z.writestr("BTOrder-x/cacciatore_affari/web/pannello.html", "<html>")
    (tmp_path / "config.json").write_text('{"mio": 1}')
    (tmp_path / ".venv").mkdir()
    (tmp_path / ".venv" / "installato.ok").write_text("ok")
    trasporto = httpx.MockTransport(lambda r: httpx.Response(200, content=buf.getvalue()))
    file = pannello.aggiorna_programma(tmp_path, "https://x/y.zip", httpx.Client(transport=trasporto))
    assert sorted(file) == ["AFFARI_MIEI.bat", "main.py", "web/pannello.html"]
    assert (tmp_path / "config.json").read_text() == '{"mio": 1}'
    assert not (tmp_path / "AFFARI_MIEI.bat").exists()
    assert (tmp_path / "AFFARI_MIEI.bat.nuovo").read_text() == "x"  # installato dal .bat al riavvio
    assert not (tmp_path / ".venv" / "installato.ok").exists()


def test_porta_occupata_viene_saltata(tmp_path):
    altro = socket.socket()
    altro.bind(("127.0.0.1", 0))
    altro.listen()
    occupata = altro.getsockname()[1]
    try:
        assert pannello.porta_occupata(occupata)
        srv = pannello.crea_server(pannello.Controllore(tmp_path / "config.json"), occupata)
        try:
            assert srv.server_address[1] != occupata
        finally:
            srv.server_close()
    finally:
        altro.close()
