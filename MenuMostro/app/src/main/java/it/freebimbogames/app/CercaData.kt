package it.freebimbogames.app

import kotlin.random.Random

/** Un livello de Il Mostro Cerca: la griglia è [dimensione] x [dimensione]. */
data class LivelloCerca(val numero: Int, val dimensione: Int)

/** 6 livelli sequenziali: la griglia cresce da 3x3 a 8x8. */
val elencoLivelliCerca: List<LivelloCerca> = listOf(3, 4, 5, 6, 7, 8).mapIndexed { indice, dimensione ->
    LivelloCerca(numero = indice + 1, dimensione = dimensione)
}

/** Tempo a disposizione per trovare l'icona, in base alla dimensione della griglia: più celle, più tempo. */
fun limiteTempoMillis(dimensione: Int): Long = 8_000L + dimensione * 2_000L

/** La griglia di icone di una ricerca: [icone] è la lista di tutte le celle, [indiceBersaglio] quella da trovare. */
data class GrigliaCerca(val icone: List<String>, val indiceBersaglio: Int) {
    val bersaglio: String get() = icone[indiceBersaglio]
}

/** Genera una griglia [dimensione] x [dimensione] con un solo bersaglio nascosto tra gli altri simboli. */
fun generaGrigliaCerca(dimensione: Int): GrigliaCerca {
    val totaleCelle = dimensione * dimensione
    val bersaglio = elencoSimboliMemory.random()
    val distrattori = elencoSimboliMemory - bersaglio
    val icone = (0 until totaleCelle).map { distrattori.random() }.toMutableList()
    val indiceBersaglio = Random.nextInt(totaleCelle)
    icone[indiceBersaglio] = bersaglio
    return GrigliaCerca(icone = icone, indiceBersaglio = indiceBersaglio)
}
