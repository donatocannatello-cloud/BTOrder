import pytest

import models
import scoring


def _ann(crea_annuncio, **kw):
    return models.normalizza(crea_annuncio(**kw), now="2026-01-01T00:00:00+00:00")


def _archivio(crea_annuncio):
    # mediana BG = 300 €/m²
    return [
        _ann(crea_annuncio, id=f"pvp:{i}", rge=f"{i}/2020", prezzo_base=p, superficie_mq=1000)
        for i, p in enumerate([200000, 300000, 400000], start=1)
    ]


def test_mediane_per_provincia(crea_annuncio):
    med = scoring.mediane_eur_mq(_archivio(crea_annuncio), campioni_minimi=3)
    assert med[("capannoni", "BG")] == 300
    assert med[("capannoni", None)] == 300


def test_provincia_con_pochi_dati_usa_mediana_categoria(crea_annuncio):
    archivio = _archivio(crea_annuncio) + [
        _ann(crea_annuncio, id="pvp:9", rge="9/2020", provincia="SO", prezzo_base=100000)]
    med = scoring.mediane_eur_mq(archivio, campioni_minimi=3)
    assert ("capannoni", "SO") not in med
    assert scoring.mediana_riferimento(archivio[-1], med) == med[("capannoni", None)]


def test_componente_eur_mq(crea_annuncio):
    med = {("capannoni", "BG"): 300.0}
    alla_mediana = _ann(crea_annuncio, prezzo_base=300000)
    meta = _ann(crea_annuncio, prezzo_base=150000)
    caro = _ann(crea_annuncio, prezzo_base=600000)
    assert scoring.componenti(alla_mediana, med)["eur_mq"] == pytest.approx(0.5)
    assert scoring.componenti(meta, med)["eur_mq"] == 1.0
    assert scoring.componenti(caro, med)["eur_mq"] == 0.0


def test_ribasso_da_storico(crea_annuncio):
    a = _ann(crea_annuncio, prezzo_base=400000)
    b = models.merge_annuncio(a, _ann(crea_annuncio, prezzo_base=200000, numero_tentativo=3))
    comp = scoring.componenti(b, {}, {"ribasso_massimo": 0.5})
    assert comp["ribasso"] == pytest.approx(1.0)       # -50% su 50% massimo
    assert comp["tentativi"] == pytest.approx(2 / 4)   # 2 aste deserte


def test_ribasso_stimato_dai_tentativi(crea_annuncio):
    # visto per la prima volta al 3° tentativo: base originaria stimata = p / 0.75²
    a = _ann(crea_annuncio, prezzo_base=225000, numero_tentativo=3)
    primo = scoring.prima_base_asta(a, 0.25)
    assert primo == pytest.approx(400000)
    comp = scoring.componenti(a, {}, {"ribasso_massimo": 0.6})
    assert comp["ribasso"] == pytest.approx((1 - 225000 / 400000) / 0.6)


def test_punteggio_pesato_e_intervallo(crea_annuncio):
    archivio = _archivio(crea_annuncio)
    cfg = {"pesi": {"eur_mq": 1, "ribasso": 0, "tentativi": 0}}
    scoring.assegna_punteggi(archivio, cfg)
    punteggi = [a["punteggio"] for a in archivio]
    assert punteggi == sorted(punteggi, reverse=True)  # più economico = punteggio più alto
    assert all(0 <= p <= 100 for p in punteggi)
    assert archivio[1]["punteggio"] == 50.0


def test_pesi_ridistribuiti_se_manca_superficie(crea_annuncio):
    a = _ann(crea_annuncio, superficie_mq=None, numero_tentativo=5)
    score, dett = scoring.calcola_punteggio(a, {("capannoni", "BG"): 300.0},
                                            {"pesi": {"eur_mq": 0.5, "ribasso": 0.0, "tentativi": 0.5}})
    assert "eur_mq" not in dett["componenti"]
    assert score == 100.0


def test_nessun_dato_nessun_punteggio(crea_annuncio):
    a = _ann(crea_annuncio, superficie_mq=None, prezzo_base=None, numero_tentativo=None)
    assert scoring.calcola_punteggio(a, {})[0] is None
