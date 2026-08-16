package it.example.menumostro

/**
 * Calcola le stelle (1-3) ottenute servendo [scelti] alla [richiesta] del Mostro:
 * conta quanti dei 3 piatti condividono almeno una categoria con quelle desiderate.
 * Il minimo è sempre 1 stella, così anche il tentativo più stravagante viene
 * premiato e il gioco resta divertente e mai frustrante per i più piccoli.
 */
fun calcolaStelle(scelti: List<Piatto>, richiesta: RichiestaMostro): Int {
    val corrispondenze = scelti.count { piatto -> piatto.categorie.any { it in richiesta.categorieDesiderate } }
    return when (corrispondenze) {
        3 -> 3
        2 -> 2
        else -> 1
    }
}

/** Sceglie una nuova richiesta casuale, diversa dalla [precedente] quando possibile. */
fun prossimaRichiesta(precedente: RichiestaMostro?): RichiestaMostro {
    if (elencoRichieste.size <= 1) return elencoRichieste.first()
    var nuova: RichiestaMostro
    do {
        nuova = elencoRichieste.random()
    } while (nuova == precedente)
    return nuova
}
