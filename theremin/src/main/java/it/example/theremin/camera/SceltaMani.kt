package it.example.theremin.camera

/** Un punto della mano riconosciuto da MediaPipe, in coordinate schermo normalizzate (0..1). */
data class PuntoMano2D(val x: Float, val y: Float)

/**
 * Da una o due mani riconosciute (21 punti ciascuna, numerazione MediaPipe) ricava i due
 * comandi del theremin: la x che decide la nota e la y che decide il volume.
 *
 * - **una mano**: la punta dell'indice fa tutto, come prima (x = nota, altezza = volume);
 * - **due mani**, come in un theremin vero: la mano più a destra suona la nota con la punta
 *   dell'indice, quella più a sinistra regola il volume con l'altezza del palmo.
 */
object SceltaMani {
    const val POLSO = 0
    const val PUNTA_INDICE = 8
    private val PALMO = intArrayOf(0, 5, 9, 13, 17)

    /** Restituisce (x della nota, y del volume), oppure null se non c'è nessuna mano. */
    fun comandi(mani: List<List<PuntoMano2D>>): Pair<Float, Float>? {
        val valide = mani.filter { it.size > PUNTA_INDICE }
        if (valide.isEmpty()) return null
        if (valide.size == 1) {
            val punta = valide[0][PUNTA_INDICE]
            return punta.x to punta.y
        }
        val perPosizione = valide.sortedBy { centroPalmo(it).x }
        val manoVolume = perPosizione.first()
        val manoNota = perPosizione.last()
        return manoNota[PUNTA_INDICE].x to centroPalmo(manoVolume).y
    }

    /**
     * Modalità "Due voci": ogni mano suona la propria nota con la punta dell'indice.
     * Restituisce la punta assegnata a ciascuna delle due voci (null se quella voce non ha mano):
     * con due mani la più a sinistra va alla voce 0 e l'altra alla voce 1; con una sola mano,
     * alla voce la cui ultima posizione è più vicina, così le voci non si scambiano di colpo.
     */
    fun voci(mani: List<List<PuntoMano2D>>, xPrecedenti: FloatArray): Array<PuntoMano2D?> {
        val punte = mani.filter { it.size > PUNTA_INDICE }.map { it[PUNTA_INDICE] }.sortedBy { it.x }
        val risultato = arrayOfNulls<PuntoMano2D>(2)
        when {
            punte.size >= 2 -> {
                risultato[0] = punte.first()
                risultato[1] = punte.last()
            }
            punte.size == 1 -> {
                val p = punte[0]
                val voce = if (kotlin.math.abs(p.x - xPrecedenti[0]) <= kotlin.math.abs(p.x - xPrecedenti[1])) 0 else 1
                risultato[voce] = p
            }
        }
        return risultato
    }

    fun centroPalmo(mano: List<PuntoMano2D>): PuntoMano2D {
        val punti = PALMO.filter { it < mano.size }.map { mano[it] }
        return PuntoMano2D(punti.map { it.x }.average().toFloat(), punti.map { it.y }.average().toFloat())
    }
}
