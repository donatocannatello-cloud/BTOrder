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

    fun centroPalmo(mano: List<PuntoMano2D>): PuntoMano2D {
        val punti = PALMO.filter { it < mano.size }.map { mano[it] }
        return PuntoMano2D(punti.map { it.x }.average().toFloat(), punti.map { it.y }.average().toFloat())
    }
}
