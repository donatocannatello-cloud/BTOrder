package it.example.frattalogic.engine

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Calcola l'insieme di Mandelbrot (classico algoritmo a tempo di fuga, con
 * colorazione continua sul numero di iterazioni frazionario) in una piccola
 * griglia di pixel: nessuna immagine precaricata, il mare frattale denso di
 * colori è ricalcolato al volo ad ogni fotogramma mentre ci si immerge.
 */
object FractalField {

    /**
     * [pixel] è il fotogramma calcolato, [frazioneEscape] la quota di pixel
     * che sono "fuggiti" (colorati) invece di restare nell'interno solido
     * dell'insieme (nero): un valore vicino a 0 significa che la vista è
     * finita in una zona monocromatica priva di dettaglio — un segnale che
     * chi guida la discesa può usare per correggere la rotta.
     */
    data class RisultatoRender(val pixel: IntArray, val frazioneEscape: Float)

    fun renderizza(
        centroX: Double,
        centroY: Double,
        semiAmpiezza: Double,
        larghezza: Int,
        altezza: Int,
        maxIterazioni: Int,
        faseColore: Float
    ): RisultatoRender {
        val pixel = IntArray(larghezza * altezza)
        val rapportoAltezza = altezza.toDouble() / larghezza.toDouble()
        var interni = 0
        var indice = 0
        for (py in 0 until altezza) {
            val im0 = centroY + (py.toDouble() / altezza - 0.5) * 2.0 * semiAmpiezza * rapportoAltezza
            for (px in 0 until larghezza) {
                val re0 = centroX + (px.toDouble() / larghezza - 0.5) * 2.0 * semiAmpiezza
                val colore = coloreDelPunto(re0, im0, maxIterazioni, faseColore)
                pixel[indice] = colore
                if (colore == NERO_INTERNO) interni++
                indice++
            }
        }
        val frazioneEscape = 1f - interni.toFloat() / pixel.size.toFloat()
        return RisultatoRender(pixel, frazioneEscape)
    }

    private fun coloreDelPunto(re0: Double, im0: Double, maxIterazioni: Int, faseColore: Float): Int {
        var zr = 0.0
        var zi = 0.0
        var zr2 = 0.0
        var zi2 = 0.0
        var iter = 0
        while (zr2 + zi2 <= 4.0 && iter < maxIterazioni) {
            zi = 2.0 * zr * zi + im0
            zr = zr2 - zi2 + re0
            zr2 = zr * zr
            zi2 = zi * zi
            iter++
        }
        if (iter >= maxIterazioni) return NERO_INTERNO

        val modulo = sqrt(zr2 + zi2)
        val iterazioneLiscia = iter.toDouble() + 1.0 - ln(ln(modulo)) / ln(2.0)
        val tinta = (((iterazioneLiscia * 9.0 + faseColore) % 360.0) + 360.0) % 360.0
        return colorDaHsv(tinta.toFloat(), 0.65f, 1f)
    }

    private const val NERO_INTERNO = 0xFF05060C.toInt()

    private fun colorDaHsv(tinta: Float, saturazione: Float, valore: Float): Int {
        val c = valore * saturazione
        val x = c * (1f - abs((tinta / 60f) % 2f - 1f))
        val m = valore - c
        val (r, g, b) = when {
            tinta < 60f -> Triple(c, x, 0f)
            tinta < 120f -> Triple(x, c, 0f)
            tinta < 180f -> Triple(0f, c, x)
            tinta < 240f -> Triple(0f, x, c)
            tinta < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val ri = ((r + m) * 255f).toInt().coerceIn(0, 255)
        val gi = ((g + m) * 255f).toInt().coerceIn(0, 255)
        val bi = ((b + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}
