import dedup


def test_normalizzazioni():
    assert dedup.normalizza_rge("R.G.E. n. 123 / 2022") == "123/2022"
    assert dedup.normalizza_rge("0123/22") == "123/2022"
    assert dedup.normalizza_tribunale("TRIBUNALE DI BERGAMO") == "bergamo"
    assert dedup.normalizza_tribunale("Bergamo") == "bergamo"


def test_duplicato_per_rge_e_tribunale(crea_annuncio):
    a = crea_annuncio(id="pvp:1")
    b = crea_annuncio(id="altro:9", rge="RGE 123/2022", tribunale="BERGAMO",
                      comune="Seriate", superficie_mq=None, prezzo_base=150000)
    assert dedup.sono_duplicati(a, b)


def test_rge_uguale_ma_tribunale_diverso(crea_annuncio):
    a = crea_annuncio(id="pvp:1", comune="Bergamo")
    b = crea_annuncio(id="pvp:2", tribunale="Tribunale di Brescia", comune="Brescia")
    assert not dedup.sono_duplicati(a, b)


def test_stessa_procedura_lotti_diversi_non_sono_duplicati(crea_annuncio):
    a = crea_annuncio(id="pvp:1", lotto="1", superficie_mq=500)
    b = crea_annuncio(id="pvp:2", lotto="2", superficie_mq=2000, prezzo_base=900000)
    assert not dedup.sono_duplicati(a, b)


def test_duplicato_per_comune_superficie_prezzo(crea_annuncio):
    a = crea_annuncio(id="pvp:1", rge=None, tribunale=None)
    b = crea_annuncio(id="x:2", rge=None, tribunale=None, comune="BERGAMO",
                      superficie_mq=1025, prezzo_base=305000)  # +2,5% e +1,6%
    assert dedup.sono_duplicati(a, b)


def test_superficie_fuori_tolleranza(crea_annuncio):
    a = crea_annuncio(id="pvp:1", rge=None)
    b = crea_annuncio(id="x:2", rge=None, superficie_mq=1040)  # +4%
    assert not dedup.sono_duplicati(a, b)


def test_prezzo_troppo_diverso(crea_annuncio):
    a = crea_annuncio(id="pvp:1", rge=None)
    b = crea_annuncio(id="x:2", rge=None, prezzo_base=200000)
    assert not dedup.sono_duplicati(a, b)


def test_categorie_diverse_non_duplicate(crea_annuncio):
    a = crea_annuncio(id="pvp:1")
    b = crea_annuncio(id="pvp:2", categoria="auto")
    assert not dedup.sono_duplicati(a, b)


def test_trova_duplicato_per_id_alias(crea_annuncio):
    archivio = [crea_annuncio(id="pvp:1", id_alias=["altro:7"], rge=None)]
    assert dedup.trova_duplicato(crea_annuncio(id="altro:7", rge=None, comune="Lecco"), archivio) is archivio[0]


def test_deduplica_lista(crea_annuncio):
    lista = [
        crea_annuncio(id="pvp:1"),
        crea_annuncio(id="pvp:2"),  # stesso RGE
        crea_annuncio(id="pvp:3", rge="5/2023", comune="Lodi", provincia="LO"),
    ]
    assert [a["id"] for a in dedup.deduplica_lista(lista)] == ["pvp:1", "pvp:3"]
