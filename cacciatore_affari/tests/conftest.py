import pytest


def annuncio(**kw):
    """Annuncio finto già normalizzato, con valori di default sensati."""
    base = {
        "id": "pvp:1",
        "fonte": "pvp",
        "categoria": "capannoni",
        "titolo": "Capannone industriale",
        "prezzo_base": 300000.0,
        "offerta_minima": 225000.0,
        "superficie_mq": 1000.0,
        "comune": "Bergamo",
        "provincia": "BG",
        "numero_tentativo": 1,
        "rge": "123/2022",
        "tribunale": "Tribunale di Bergamo",
    }
    base.update(kw)
    return base


@pytest.fixture
def crea_annuncio():
    return annuncio
