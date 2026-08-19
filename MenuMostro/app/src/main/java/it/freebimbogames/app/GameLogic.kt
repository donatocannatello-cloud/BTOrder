package it.freebimbogames.app

import kotlin.math.roundToInt

/**
 * Calcola la percentuale (0-100) di [piatti] che soddisfa [condizione], arrotondata.
 * Con 3 piatti (uno per portata) i valori possibili sono sempre 0, 33, 67 o 100.
 */
fun frazionePunti(piatti: List<Piatto>, condizione: (Piatto) -> Boolean): Int {
    if (piatti.isEmpty()) return 0
    val corrispondenti = piatti.count(condizione)
    return ((corrispondenti * 100.0) / piatti.size).roundToInt()
}

/** Estrae [quante] richieste distinte a caso, in ordine casuale: una per manche, mai ripetute nella partita. */
fun estraiRichieste(quante: Int): List<RichiestaMostro> = elencoRichieste.shuffled().take(quante)

/** Estrae [quanti] commensali distinti a caso, in ordine casuale. */
fun estraiCommensali(quanti: Int): List<Commensale> = elencoCommensali.shuffled().take(quanti)
