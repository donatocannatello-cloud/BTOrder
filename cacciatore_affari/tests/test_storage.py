import json
import os

import pytest

import models
import storage


def test_load_default_se_file_mancante(tmp_path):
    assert storage.load(tmp_path / "nope.json", default=[]) == []


def test_save_e_load(tmp_path):
    p = tmp_path / "sub" / "dati.json"
    storage.save(p, [{"id": "a", "città": "Brescia"}])
    assert storage.load(p) == [{"id": "a", "città": "Brescia"}]
    # nessun file temporaneo lasciato in giro
    assert os.listdir(p.parent) == ["dati.json"]


def test_save_atomico_non_corrompe_in_caso_di_errore(tmp_path):
    p = tmp_path / "dati.json"
    storage.save(p, {"ok": 1})
    with pytest.raises(TypeError):
        storage.save(p, {"non_serializzabile": object()})
    assert json.loads(p.read_text()) == {"ok": 1}
    assert os.listdir(tmp_path) == ["dati.json"]


def test_save_usa_os_replace(tmp_path, monkeypatch):
    chiamate = []
    originale = os.replace
    monkeypatch.setattr(storage.os, "replace", lambda a, b: (chiamate.append((a, b)), originale(a, b)))
    storage.save(tmp_path / "x.json", [])
    assert len(chiamate) == 1 and str(chiamate[0][1]).endswith("x.json")


def test_upsert_inserisce_e_aggiorna(tmp_path):
    p = tmp_path / "annunci.json"
    rec, nuovo = storage.upsert(p, {"id": "1", "prezzo": 10})
    assert nuovo and rec["prezzo"] == 10
    rec, nuovo = storage.upsert(p, {"id": "1", "prezzo": 8})
    assert not nuovo and rec["prezzo"] == 8
    storage.upsert(p, {"id": "2", "prezzo": 5})
    assert [r["id"] for r in storage.load(p)] == ["1", "2"]


def test_upsert_many_con_merge_annuncio(tmp_path, crea_annuncio):
    p = tmp_path / "annunci.json"
    a1 = models.normalizza(crea_annuncio(), now="2026-01-01T00:00:00+00:00")
    storage.upsert(p, a1, merge=models.merge_annuncio)
    storage.load(p)[0]["notificato"] = True
    a1["notificato"] = True
    storage.save(p, [a1])

    ribasso = models.normalizza(crea_annuncio(prezzo_base=225000, numero_tentativo=2),
                                now="2026-03-01T00:00:00+00:00")
    [(rec, nuovo)] = storage.upsert_many(p, [ribasso], merge=models.merge_annuncio)
    assert not nuovo
    assert rec["first_seen"] == "2026-01-01T00:00:00+00:00"
    assert [s["prezzo_base"] for s in rec["storico_prezzi"]] == [300000.0, 225000.0]
    assert rec["notificato"] is False  # il ribasso va notificato di nuovo
    assert rec["eur_mq"] == 225.0


def test_load_annunci_rifiuta_formato_errato(tmp_path):
    p = tmp_path / "annunci.json"
    storage.save(p, {"non": "lista"})
    with pytest.raises(ValueError):
        storage.load_annunci(p)
