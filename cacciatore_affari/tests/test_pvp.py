import json

import httpx

import main
import storage
from adapters.pvp import PVPAdapter, estrai_lista, estrai_superficie
from http_client import PoliteClient

LOTTI = [
    {"idLotto": 101, "idAnnuncio": 5001, "descrizioneBreve": "Capannone industriale con uffici",
     "prezzoBase": 250000, "offertaMinima": 187500, "comune": "Dalmine", "siglaProvincia": "BG",
     "regione": "Lombardia", "dataVendita": "2026-11-10T10:00:00", "numeroProcedura": 45,
     "annoProcedura": 2023, "tribunale": {"descrizione": "Tribunale di Bergamo"},
     "descrizione": "Opificio di mq 1.250 con piazzale", "tipoVendita": "Asincrona telematica"},
    {"idLotto": 102, "idAnnuncio": 5002, "descrizioneBreve": "Appartamento trilocale",
     "prezzoBase": 90000, "comune": "Milano", "siglaProvincia": "MI"},
    {"idLotto": 103, "idAnnuncio": 5003, "descrizioneBreve": "Capannone artigianale",
     "prezzoBase": 120000, "comune": "Torino", "siglaProvincia": "TO"},
]

DETTAGLIO_HTML = """<html><body><h1>Lotto</h1>
<a href="/allegati/avviso.pdf">Avviso di vendita</a>
<a href="/allegati/doc123.pdf">Perizia di stima</a></body></html>"""


def _server(richieste):
    def handler(request: httpx.Request) -> httpx.Response:
        richieste.append(request)
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nDisallow: /privato/\n")
        if request.url.path == "/bo-ms/fe-config/area-annunci":
            return httpx.Response(200, json={"services": {"ricerca": "/ric-abc/ric-ms/"}})
        if request.url.path == "/ric-abc/ric-ms/ricerca/vendite":
            return httpx.Response(200, json={"body": {"content": LOTTI, "totalPages": 1}})
        if request.url.path == "/pvp/it/detail_annuncio.page":
            return httpx.Response(200, text=DETTAGLIO_HTML)
        return httpx.Response(404)
    return httpx.MockTransport(handler)


def _client(richieste):
    return PoliteClient(min_delay=0, max_delay=0, transport=_server(richieste), sleep=lambda s: None)


def test_estrai_lista_e_superficie():
    assert estrai_lista({"body": {"content": [{"a": 1}], "totalPages": 3}}) == ([{"a": 1}], 3)
    assert estrai_superficie("capannone di mq 1.250 e uffici 80 mq") == 1250


def test_fetch_listings_filtra_categoria_e_regione():
    richieste = []
    adapter = PVPAdapter(_client(richieste), {"base_url": "https://pvp.test"})
    risultati = adapter.fetch_listings({
        "categoria": "capannoni", "tipo_lotto": "IMMOBILI", "regioni": ["Lombardia"],
        "parole_chiave": ["capannone", "opificio"],
    })
    assert [r["id"] for r in risultati] == ["pvp:101"]
    r = risultati[0]
    assert r["rge"] == "45/2023" and r["tribunale"] == "Tribunale di Bergamo"
    assert r["superficie_mq"] == 1250 and r["provincia"] == "BG"
    assert r["url"].endswith("idAnnuncio=5001")
    post = [q for q in richieste if q.method == "POST"][0]
    assert json.loads(post.content)["tipoLotto"] == "IMMOBILI"
    assert post.url.params["page"] == "0"


def test_fetch_detail_trova_perizia():
    adapter = PVPAdapter(_client([]), {"base_url": "https://pvp.test",
                                       "url_annuncio": "https://pvp.test/pvp/it/detail_annuncio.page?idAnnuncio={id}"})
    agg = adapter.fetch_detail({"id": "pvp:101", "categoria": "capannoni",
                                "url": "https://pvp.test/pvp/it/detail_annuncio.page?idAnnuncio=5001"})
    assert agg["url_perizia"] == "https://pvp.test/allegati/doc123.pdf"


def test_robots_rispettato():
    c = _client([])
    assert c.consentito("https://pvp.test/pubblico")
    assert not c.consentito("https://pvp.test/privato/x")


def test_ciclo_completo(tmp_path, monkeypatch):
    richieste = []
    monkeypatch.setattr(main, "PoliteClient", lambda **kw: _client(richieste))
    cfg = {
        "regioni": ["Lombardia"],
        "categorie": {"capannoni": {"parole_chiave": ["capannone"], "soglia_punteggio": 0}},
        "fonti": {"pvp": {"base_url": "https://pvp.test",
                          "url_annuncio": "https://pvp.test/pvp/it/detail_annuncio.page?idAnnuncio={id}"}},
        "telegram": {},
        "percorsi": {"annunci": str(tmp_path / "annunci.json"), "perizie": str(tmp_path / "perizie")},
    }
    stats = main.ciclo(cfg, dry_run=True)
    assert stats["nuovi"] == 1
    [a] = storage.load_annunci(tmp_path / "annunci.json")
    assert a["url_perizia"].endswith("doc123.pdf")
    assert a["punteggio"] is not None
    assert a["notificato"] is False  # dry-run non marca

    # secondo ciclo con prezzo ribassato: stesso annuncio, storico aggiornato
    LOTTI[0]["prezzoBase"] = 187500
    try:
        stats = main.ciclo(cfg, dry_run=True)
    finally:
        LOTTI[0]["prezzoBase"] = 250000
    assert stats == {**stats, "nuovi": 0, "ribassati": 1}
    [a] = storage.load_annunci(tmp_path / "annunci.json")
    assert [s["prezzo_base"] for s in a["storico_prezzi"]] == [250000, 187500]
