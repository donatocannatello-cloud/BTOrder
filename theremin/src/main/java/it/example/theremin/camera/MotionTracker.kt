package it.example.theremin.camera

import kotlin.math.PI
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
 * Ogni fotogramma (solo luminanza) viene ridotto a una griglia di [COLONNE]×[RIGHE] celle.
 * Dopo un breve assestamento (la fotocamera regola l'esposizione) un modello di sfondo impara
 * la scena statica; poi, per ogni fotogramma:
 *
 * 1. **compensazione dell'esposizione**: la differenza mediana tra fotogramma e sfondo è lo
 *    scostamento globale di luminosità (tipico quando la mano entra su uno sfondo bianco e la
 *    fotocamera si riadatta) e viene sottratta a tutte le celle;
 * 2. **soglia adattiva**: il rumore del sensore viene stimato (mediana delle differenze) e la
 *    soglia si alza o si abbassa di conseguenza, con un minimo regolato da [sensibilita];
 * 3. anche il **movimento** rispetto al fotogramma precedente conta, così una mano che si muove
 *    su uno sfondo di colore simile viene comunque vista;
 * 4. le celle isolate (rumore) vengono scartate, tenendo solo quelle con vicini in primo piano;
 * 5. il punto scelto ([punto]) passa per un **filtro One Euro**: fermo quando la mano è ferma,
 *    reattivo quando si muove.
 */
class MotionTracker {

    @Volatile var punto: PuntoMano = PuntoMano.PUNTA

    /** 0 = poco sensibile (sfondi rumorosi), 1 = molto sensibile (mano poco contrastata sullo sfondo). */
    @Volatile var sensibilita: Float = 0.5f

    /** true quando lo sfondo è stato imparato: da qui la fotocamera può bloccare l'esposizione. */
    @Volatile var calibrato: Boolean = false
        private set

    private val n = COLONNE * RIGHE
    private val sfondo = FloatArray(n)
    private val corrente = FloatArray(n)
    private val precedente = FloatArray(n)
    private val punteggio = FloatArray(n)
    private val primoPiano = BooleanArray(n)
    private val appoggio = FloatArray(n)
    private var fotogrammi = 0

    // Accumulatori per riga in coordinate schermo (al massimo max(COLONNE, RIGHE) righe)
    private val maxRighe = maxOf(COLONNE, RIGHE)
    private val rigaCelle = IntArray(maxRighe)
    private val rigaPeso = FloatArray(maxRighe)
    private val rigaSx = FloatArray(maxRighe)
    private val rigaSy = FloatArray(maxRighe)

    private val filtroX = FiltroOneEuro()
    private val filtroY = FiltroOneEuro()
    private var xUscita = 0.5f
    private var yUscita = 0.5f
    private var presenzaLiscia = 0f
    private var ultimoTimestampNs = 0L

    /** Dimentica lo sfondo: i prossimi fotogrammi vengono usati per reimpararlo (tenere la mano fuori campo). */
    @Synchronized
    fun ricalibra() {
        fotogrammi = 0
        calibrato = false
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
     * @param timestampNs istante del fotogramma (0 = ignoto, si assumono 30 fotogrammi al secondo)
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
        timestampNs: Long = 0L,
    ): PosizioneMano {
        riduci(luma, larghezza, altezza, rowStride, pixelStride)
        val dt = if (timestampNs > 0L && ultimoTimestampNs > 0L) {
            ((timestampNs - ultimoTimestampNs) / 1e9f).coerceIn(0.005f, 0.2f)
        } else 1f / 30f
        ultimoTimestampNs = timestampNs

        // Assestamento dell'esposizione, poi apprendimento dello sfondo
        if (fotogrammi < FOTOGRAMMI_ASSESTAMENTO + FOTOGRAMMI_CALIBRAZIONE) {
            val k = fotogrammi - FOTOGRAMMI_ASSESTAMENTO
            when {
                k < 0 -> {}
                k == 0 -> corrente.copyInto(sfondo)
                else -> for (i in 0 until n) sfondo[i] += (corrente[i] - sfondo[i]) / (k + 1)
            }
            fotogrammi++
            if (fotogrammi == FOTOGRAMMI_ASSESTAMENTO + FOTOGRAMMI_CALIBRAZIONE) calibrato = true
            corrente.copyInto(precedente)
            presenzaLiscia = 0f
            return PosizioneMano(xUscita, yUscita, 0f)
        }

        // 1. scostamento globale di esposizione
        for (i in 0 until n) appoggio[i] = corrente[i] - sfondo[i]
        val scostamento = mediana(appoggio)
        // 2. rumore: mediana delle differenze assolute residue
        for (i in 0 until n) appoggio[i] = abs(corrente[i] - sfondo[i] - scostamento)
        val rumore = mediana(appoggio)
        val sogliaMinima = SOGLIA_MIN_POCO_SENSIBILE +
            (SOGLIA_MIN_MOLTO_SENSIBILE - SOGLIA_MIN_POCO_SENSIBILE) * sensibilita.coerceIn(0f, 1f)
        val soglia = maxOf(sogliaMinima, rumore * 4f)

        // 3. punteggio per cella: differenza dallo sfondo o movimento rispetto al fotogramma prima
        for (i in 0 until n) {
            val dSfondo = abs(corrente[i] - sfondo[i] - scostamento)
            val dMoto = abs(corrente[i] - precedente[i]) * 0.8f
            punteggio[i] = maxOf(dSfondo, dMoto)
            primoPiano[i] = punteggio[i] > soglia
        }

        // 4. via le celle isolate: servono almeno due vicini in primo piano
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
                val attiva = primoPiano[i] && vicini(r, c) >= 2
                if (attiva) {
                    val peso = punteggio[i] - soglia
                    var (x, y) = ruota((c + 0.5f) / COLONNE, (r + 0.5f) / RIGHE, rotazione)
                    if (specchia) x = 1f - x
                    val riga = (y * righeSchermo).toInt().coerceIn(0, righeSchermo - 1)
                    rigaCelle[riga]++
                    rigaPeso[riga] += peso
                    rigaSx[riga] += peso * x
                    rigaSy[riga] += peso * y
                    celleAttive++
                    sfondo[i] += (corrente[i] - scostamento - sfondo[i]) * ALFA_PRIMO_PIANO
                } else {
                    // Lo sfondo segue lentamente la scena, esposizione compresa
                    sfondo[i] += (corrente[i] - sfondo[i]) * ALFA_SFONDO
                }
            }
        }
        corrente.copyInto(precedente)

        val frazione = celleAttive.toFloat() / n
        var presente = frazione > FRAZIONE_MINIMA
        if (presente) {
            var prima = 0
            var ultima = righeSchermo - 1
            if (punto == PuntoMano.PUNTA) {
                prima = (0 until righeSchermo).firstOrNull { rigaCelle[it] >= MIN_CELLE_RIGA }
                    ?: (0 until righeSchermo).first { rigaCelle[it] > 0 }
                ultima = (prima + righeSchermo * FASCIA_PUNTA).toInt().coerceIn(prima, righeSchermo - 1)
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
                if (presenzaLiscia < 0.3f) {
                    // La mano è appena entrata: si parte da dove si trova, senza scivolare dal punto vecchio
                    filtroX.reimposta(x)
                    filtroY.reimposta(y)
                }
                xUscita = filtroX.filtra(x, dt)
                yUscita = filtroY.filtra(y, dt)
            }
        }
        presenzaLiscia += ((if (presente) 1f else 0f) - presenzaLiscia) * 0.3f
        return PosizioneMano(xUscita, yUscita, presenzaLiscia)
    }

    private fun vicini(r: Int, c: Int): Int {
        var k = 0
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val rr = r + dr
            val cc = c + dc
            if (rr in 0 until RIGHE && cc in 0 until COLONNE && primoPiano[rr * COLONNE + cc]) k++
        }
        return k
    }

    /** Mediana approssimata con un istogramma su 0..255 (i valori sono livelli di luminanza). */
    private val istogramma = IntArray(512)
    private fun mediana(v: FloatArray): Float {
        istogramma.fill(0)
        for (x in v) istogramma[(x + 256f).toInt().coerceIn(0, 511)]++
        var cumulata = 0
        for (b in istogramma.indices) {
            cumulata += istogramma[b]
            if (cumulata * 2 >= v.size) return b - 256f + 0.5f
        }
        return 0f
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
                var cnt = 0
                var py = r * cellaH
                val fineY = py + cellaH
                while (py < fineY) {
                    var px = c * cellaW
                    val fineX = px + cellaW
                    val base = py * rowStride
                    while (px < fineX) {
                        tot += luma[base + px * pixelStride].toInt() and 0xFF
                        cnt++
                        px += passoX
                    }
                    py += passoY
                }
                corrente[r * COLONNE + c] = if (cnt > 0) tot.toFloat() / cnt else 0f
            }
        }
    }

    companion object {
        const val COLONNE = 64
        const val RIGHE = 48
        private const val CAMPIONI_PER_LATO = 3
        private const val FOTOGRAMMI_ASSESTAMENTO = 8
        private const val FOTOGRAMMI_CALIBRAZIONE = 12
        private const val SOGLIA_MIN_POCO_SENSIBILE = 22f
        private const val SOGLIA_MIN_MOLTO_SENSIBILE = 7f
        private const val FRAZIONE_MINIMA = 0.008f
        private const val ALFA_SFONDO = 0.03f
        private const val ALFA_PRIMO_PIANO = 0.003f
        private const val MIN_CELLE_RIGA = 2
        /** Altezza della fascia "punta", in frazione dell'altezza dell'immagine. */
        private const val FASCIA_PUNTA = 0.08f

        /** Ruota in senso orario di [gradi] un punto normalizzato (u, v) del fotogramma grezzo. */
        fun ruota(u: Float, v: Float, gradi: Int): Pair<Float, Float> = when (((gradi % 360) + 360) % 360) {
            90 -> Pair(1f - v, u)
            180 -> Pair(1f - u, 1f - v)
            270 -> Pair(v, 1f - u)
            else -> Pair(u, v)
        }
    }
}

/**
 * Filtro One Euro (Casiez et al., 2012): passa-basso la cui frequenza di taglio cresce con la
 * velocità. Elimina il tremolio a mano ferma senza introdurre ritardo nei movimenti rapidi.
 */
class FiltroOneEuro(
    private val tagliominimoHz: Float = 1.5f,
    private val beta: Float = 3f,
    private val taglioDerivataHz: Float = 1f,
) {
    private var x = Float.NaN
    private var dx = 0f

    fun reimposta(valore: Float) {
        x = valore
        dx = 0f
    }

    fun filtra(valore: Float, dt: Float): Float {
        if (x.isNaN()) {
            reimposta(valore)
            return valore
        }
        val derivata = (valore - x) / dt
        dx += (derivata - dx) * alfa(taglioDerivataHz, dt)
        val taglio = tagliominimoHz + beta * abs(dx)
        x += (valore - x) * alfa(taglio, dt)
        return x
    }

    private fun alfa(taglioHz: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * taglioHz)
        return 1f / (1f + tau / dt)
    }
}
