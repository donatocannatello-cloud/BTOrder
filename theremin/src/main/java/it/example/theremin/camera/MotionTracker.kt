package it.example.theremin.camera

import kotlin.math.abs

/** Posizione della mano in coordinate schermo normalizzate (0..1, origine in alto a sinistra). */
data class PosizioneMano(val x: Float, val y: Float, val presenza: Float)

/** Quale punto della sagoma in movimento viene seguito. */
enum class PuntoMano(val etichetta: String) {
    /**
     * La parte più alta della sagoma (la punta delle dita, dato che il braccio entra dal basso).
     * Non viene trascinata verso il centro dal braccio né "tagliata" dal bordo dell'inquadratura,
     * quindi raggiunge anche le note più periferiche.
     */
    PUNTA("Punta delle dita"),

    /** Il baricentro di tutto ciò che si muove: più stabile, ma fatica a raggiungere i bordi. */
    CENTRO("Centro della mano"),
}

/**
 * Individua la mano davanti alla fotocamera per sottrazione dello sfondo, indipendente da Android.
 *
 * Ogni fotogramma (solo luminanza) viene ridotto a una griglia di [COLONNE]×[RIGHE] celle; un
 * modello di sfondo a media mobile impara la scena statica e le celle che se ne discostano
 * sono considerate "mano". Le celle in primo piano vengono riportate in coordinate schermo
 * (raddrizzate e specchiate) e da lì si ricava il punto da seguire, secondo [punto].
 * Lo sfondo si aggiorna lentamente anche sotto la mano, così una mano immobile per molti
 * secondi viene gradualmente assorbita, mentre un movimento viene seguito subito.
 */
class MotionTracker {

    @Volatile var punto: PuntoMano = PuntoMano.PUNTA

    private val sfondo = FloatArray(COLONNE * RIGHE)
    private val corrente = FloatArray(COLONNE * RIGHE)
    private var fotogrammiAppresi = 0

    // Accumulatori per riga in coordinate schermo (al massimo max(COLONNE, RIGHE) righe)
    private val rigaCelle = IntArray(maxOf(COLONNE, RIGHE))
    private val rigaPeso = FloatArray(maxOf(COLONNE, RIGHE))
    private val rigaSx = FloatArray(maxOf(COLONNE, RIGHE))
    private val rigaSy = FloatArray(maxOf(COLONNE, RIGHE))

    private var xLiscia = 0.5f
    private var yLiscia = 0.5f
    private var presenzaLiscia = 0f

    /** Dimentica lo sfondo: i prossimi fotogrammi vengono usati per reimpararlo (tenere la mano fuori campo). */
    @Synchronized
    fun ricalibra() {
        fotogrammiAppresi = 0
        presenzaLiscia = 0f
    }

    /**
     * Elabora un fotogramma di luminanza.
     *
     * @param luma piano Y del fotogramma
     * @param rowStride byte per riga nel buffer
     * @param pixelStride distanza in byte tra due pixel consecutivi
     * @param rotazione gradi (orari) per raddrizzare l'immagine, come `ImageInfo.rotationDegrees`
     * @param specchia true per la fotocamera anteriore, così la mano si muove come in uno specchio
     */
    @Synchronized
    fun elabora(
        luma: ByteArray,
        larghezza: Int,
        altezza: Int,
        rowStride: Int,
        pixelStride: Int,
        rotazione: Int,
        specchia: Boolean,
    ): PosizioneMano {
        riduci(luma, larghezza, altezza, rowStride, pixelStride)

        if (fotogrammiAppresi < FOTOGRAMMI_CALIBRAZIONE) {
            if (fotogrammiAppresi == 0) corrente.copyInto(sfondo)
            else for (i in sfondo.indices) sfondo[i] += (corrente[i] - sfondo[i]) * 0.3f
            fotogrammiAppresi++
            presenzaLiscia = 0f
            return PosizioneMano(xLiscia, yLiscia, 0f)
        }

        // Righe dell'immagine raddrizzata: con rotazione di 90°/270° le colonne del sensore diventano righe
        val ruotata = ((rotazione % 180) + 180) % 180 == 90
        val righeSchermo = if (ruotata) COLONNE else RIGHE
        rigaCelle.fill(0)
        rigaPeso.fill(0f)
        rigaSx.fill(0f)
        rigaSy.fill(0f)

        var celleAttive = 0
        for (r in 0 until RIGHE) {
            for (c in 0 until COLONNE) {
                val i = r * COLONNE + c
                val diff = abs(corrente[i] - sfondo[i])
                if (diff > SOGLIA) {
                    val peso = diff - SOGLIA
                    var (x, y) = ruota((c + 0.5f) / COLONNE, (r + 0.5f) / RIGHE, rotazione)
                    if (specchia) x = 1f - x
                    val riga = (y * righeSchermo).toInt().coerceIn(0, righeSchermo - 1)
                    rigaCelle[riga]++
                    rigaPeso[riga] += peso
                    rigaSx[riga] += peso * x
                    rigaSy[riga] += peso * y
                    celleAttive++
                    sfondo[i] += (corrente[i] - sfondo[i]) * ALFA_PRIMO_PIANO
                } else {
                    sfondo[i] += (corrente[i] - sfondo[i]) * ALFA_SFONDO
                }
            }
        }

        val frazione = celleAttive.toFloat() / (COLONNE * RIGHE)
        var presente = frazione > FRAZIONE_MINIMA
        if (presente) {
            // Righe da considerare: tutte (baricentro) o solo una fascia in cima alla sagoma (punta)
            var prima = 0
            var ultima = righeSchermo - 1
            if (punto == PuntoMano.PUNTA) {
                // Prima riga con abbastanza celle, per non farsi ingannare da un pixel di rumore isolato
                prima = (0 until righeSchermo).firstOrNull { rigaCelle[it] >= MIN_CELLE_RIGA }
                    ?: (0 until righeSchermo).first { rigaCelle[it] > 0 }
                ultima = (prima + RIGHE_PUNTA - 1).coerceAtMost(righeSchermo - 1)
            }
            var somma = 0f
            var sx = 0f
            var sy = 0f
            for (r in prima..ultima) {
                somma += rigaPeso[r]
                sx += rigaSx[r]
                sy += rigaSy[r]
            }
            presente = somma > 0f
            if (presente) {
                val x = sx / somma
                val y = sy / somma
                // Più la mano copre l'inquadratura, più la posizione è affidabile: si segue più rapidamente
                val k = if (frazione > 0.05f) 0.55f else 0.35f
                xLiscia += (x - xLiscia) * k
                yLiscia += (y - yLiscia) * k
            }
        }
        presenzaLiscia += ((if (presente) 1f else 0f) - presenzaLiscia) * 0.25f
        return PosizioneMano(xLiscia, yLiscia, presenzaLiscia)
    }

    /** Media della luminanza per cella, campionando una sottogriglia di pixel per contenere il costo. */
    private fun riduci(luma: ByteArray, larghezza: Int, altezza: Int, rowStride: Int, pixelStride: Int) {
        val cellaW = larghezza / COLONNE
        val cellaH = altezza / RIGHE
        val passoX = (cellaW / CAMPIONI_PER_LATO).coerceAtLeast(1)
        val passoY = (cellaH / CAMPIONI_PER_LATO).coerceAtLeast(1)
        for (r in 0 until RIGHE) {
            for (c in 0 until COLONNE) {
                var tot = 0
                var n = 0
                var py = r * cellaH
                val fineY = py + cellaH
                while (py < fineY) {
                    var px = c * cellaW
                    val fineX = px + cellaW
                    val base = py * rowStride
                    while (px < fineX) {
                        tot += luma[base + px * pixelStride].toInt() and 0xFF
                        n++
                        px += passoX
                    }
                    py += passoY
                }
                corrente[r * COLONNE + c] = if (n > 0) tot.toFloat() / n else 0f
            }
        }
    }

    companion object {
        const val COLONNE = 40
        const val RIGHE = 30
        private const val CAMPIONI_PER_LATO = 4
        private const val FOTOGRAMMI_CALIBRAZIONE = 12
        private const val SOGLIA = 18f
        private const val FRAZIONE_MINIMA = 0.012f
        private const val ALFA_SFONDO = 0.04f
        private const val ALFA_PRIMO_PIANO = 0.004f
        private const val MIN_CELLE_RIGA = 2
        private const val RIGHE_PUNTA = 4

        /** Ruota in senso orario di [gradi] un punto normalizzato (u, v) del fotogramma grezzo. */
        fun ruota(u: Float, v: Float, gradi: Int): Pair<Float, Float> = when (((gradi % 360) + 360) % 360) {
            90 -> Pair(1f - v, u)
            180 -> Pair(1f - u, 1f - v)
            270 -> Pair(v, 1f - u)
            else -> Pair(u, v)
        }
    }
}
