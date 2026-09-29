package it.freebimbogames.app.acchiappamostri

import kotlin.math.hypot
import kotlin.math.max

/** Un punto sullo schermo (in pixel) o nell'immagine della fotocamera (normalizzato 0..1). */
data class Punto(val x: Float, val y: Float) {
    operator fun plus(o: Punto) = Punto(x + o.x, y + o.y)
    operator fun minus(o: Punto) = Punto(x - o.x, y - o.y)
    operator fun times(k: Float) = Punto(x * k, y * k)
    fun distanza(o: Punto) = hypot(x - o.x, y - o.y)

    /** Punto a metà strada verso [o] per t = 0.5 (t = 0: questo, t = 1: [o]). */
    fun verso(o: Punto, t: Float) = Punto(x + (o.x - x) * t, y + (o.y - y) * t)

    companion object {
        val ZERO = Punto(0f, 0f)
        fun media(punti: List<Punto>) =
            Punto(punti.map { it.x }.average().toFloat(), punti.map { it.y }.average().toFloat())
    }
}

/**
 * Converte le coordinate normalizzate dell'immagine della fotocamera in pixel dello schermo,
 * con la stessa regola dell'anteprima a tutto schermo (`FILL_CENTER`): l'immagine viene
 * ingrandita finché copre tutto lo schermo e la parte che avanza viene tagliata ai lati.
 *
 * @param aspettoImmagine larghezza / altezza del fotogramma già raddrizzato
 */
class MappaturaSchermo(val larghezza: Float, val altezza: Float, aspettoImmagine: Float) {
    private val larghezzaImmagine = max(larghezza, altezza * aspettoImmagine)
    private val altezzaImmagine = larghezzaImmagine / aspettoImmagine
    private val dx = (larghezza - larghezzaImmagine) / 2f
    private val dy = (altezza - altezzaImmagine) / 2f

    fun inSchermo(p: Punto) = Punto(dx + p.x * larghezzaImmagine, dy + p.y * altezzaImmagine)
}
