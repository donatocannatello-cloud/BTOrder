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
 * 3. anche il **movimento** rispetto al fotogramma precedente conta (dove l'immagine differisce
 *    almeno un po' dallo sfondo), così una mano che si muove su uno sfondo simile viene comunque vista;
 * 4. le celle isolate (rumore) vengono scartate, tenendo solo quelle con vicini in primo piano,
 *    e si segue soltanto la **sagoma più grande** (la mano), ignorando piccoli movimenti altrove;
 * 5. il punto scelto ([punto]) passa per un **filtro One Euro**: fermo quando la mano è ferma,
 *    reattivo quando si muove.
 *
 * Lo sfondo non "assorbe" mai la mano: così, quando la mano esce, non resta una sagoma fantasma
 * e la presenza cade a zero in pochi centesimi di secondo. Solo un oggetto rimasto
 * perfettamente immobile per [FOTOGRAMMI_OGGETTO_FERMO] fotogrammi viene inglobato nello sfondo.
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
    private val attiva = BooleanArray(n)
    private val etichetta = IntArray(n)
    private val coda = IntArray(n)
    private val fermoDa = IntArray(n)
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
    private var fotogrammiSenzaMano = 0
    private var ultimoTimestampNs = 0L

    /** Dimentica lo sfondo: i prossimi fotogrammi vengono usati per reimpararlo (tenere la mano fuori campo). */
    @Synchronized
    fun ricalibra() {
        fotogrammi = 0
        fermoDa.fill(0)
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
     * @param offsetCanale byte da saltare in ogni pixel (es. 1 per leggere il verde da RGBA)
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
        offsetCanale: Int = 0,
    ): PosizioneMano {
        riduci(luma, larghezza, altezza, rowStride, pixelStride, offsetCanale)
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
            // Il movimento aiuta solo dove l'immagine differisce anche dallo sfondo: altrimenti il
            // punto appena lasciato libero dalla mano che esce verrebbe scambiato per la mano
            primoPiano[i] = dSfondo > soglia || (dMoto > soglia && dSfondo > soglia * 0.5f)
        }

        // 4. via le celle isolate: servono almeno due vicini in primo piano
        for (r in 0 until RIGHE) for (c in 0 until COLONNE) {
            val i = r * COLONNE + c
            attiva[i] = primoPiano[i] && vicini(r, c) >= 2
        }
        // ...e si tiene solo la sagoma connessa più grande (la mano con il braccio)
        val sagoma = sagomaPiuGrande()

        // Aggiornamento dello sfondo: segue la scena (esposizione compresa) ma non la mano
        for (i in 0 until n) {
            if (attiva[i]) {
                val immobile = abs(corrente[i] - precedente[i]) < SOGLIA_IMMOBILE
                fermoDa[i] = if (immobile) fermoDa[i] + 1 else 0
                if (fermoDa[i] > FOTOGRAMMI_OGGETTO_FERMO) {
                    // Un oggetto posato e rimasto immobile a lungo diventa parte dello sfondo
                    sfondo[i] = corrente[i] - scostamento
                    fermoDa[i] = 0
                }
            } else {
                fermoDa[i] = 0
                sfondo[i] += (corrente[i] - sfondo[i]) * ALFA_SFONDO
            }
        }

        val ruotata = ((rotazione % 180) + 180) % 180 == 90
        val righeSchermo = if (ruotata) COLONNE else RIGHE
        rigaCelle.fill(0)
        rigaPeso.fill(0f)
        rigaSx.fill(0f)
        rigaSy.fill(0f)
        var celleAttive = 0
        if (sagoma > 0) {
            for (r in 0 until RIGHE) for (c in 0 until COLONNE) {
                val i = r * COLONNE + c
                if (etichetta[i] != sagoma) continue
                val peso = punteggio[i] - soglia
                var (x, y) = ruota((c + 0.5f) / COLONNE, (r + 0.5f) / RIGHE, rotazione)
                if (specchia) x = 1f - x
                val riga = (y * righeSchermo).toInt().coerceIn(0, righeSchermo - 1)
                rigaCelle[riga]++
                rigaPeso[riga] += peso
                rigaSx[riga] += peso * x
                rigaSy[riga] += peso * y
                celleAttive++
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
        // Presenza: sale in fretta e, quando la mano esce, va a zero subito
        // (un solo fotogramma "buco" è tollerato, per non interrompere il suono per un disturbo)
        if (presente) {
            fotogrammiSenzaMano = 0
            presenzaLiscia = minOf(1f, presenzaLiscia + 0.5f)
        } else {
            fotogrammiSenzaMano++
            if (fotogrammiSenzaMano >= FOTOGRAMMI_USCITA) presenzaLiscia = 0f
        }
        return PosizioneMano(xUscita, yUscita, presenzaLiscia)
    }

    /**
     * Etichetta le sagome connesse (celle [attiva] adiacenti, anche in diagonale) e restituisce
     * l'etichetta di quella con più "energia", o 0 se nessuna è abbastanza grande da essere una mano.
     */
    private fun sagomaPiuGrande(): Int {
        etichetta.fill(0)
        var prossima = 1
        var migliore = 0
        var energiaMigliore = 0f
        for (inizio in 0 until n) {
            if (!attiva[inizio] || etichetta[inizio] != 0) continue
            val e = prossima++
            var testa = 0
            var fine = 0
            coda[fine++] = inizio
            etichetta[inizio] = e
            var celle = 0
            var energia = 0f
            while (testa < fine) {
                val i = coda[testa++]
                celle++
                energia += punteggio[i]
                val r = i / COLONNE
                val c = i % COLONNE
                for (dr in -1..1) for (dc in -1..1) {
                    val rr = r + dr
                    val cc = c + dc
                    if (rr !in 0 until RIGHE || cc !in 0 until COLONNE) continue
                    val j = rr * COLONNE + cc
                    if (attiva[j] && etichetta[j] == 0) {
                        etichetta[j] = e
                        coda[fine++] = j
                    }
                }
            }
            if (celle >= MIN_CELLE_SAGOMA && energia > energiaMigliore) {
                energiaMigliore = energia
                migliore = e
            }
        }
        return migliore
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
    private fun riduci(luma: ByteArray, larghezza: Int, altezza: Int, rowStride: Int, pixelStride: Int, offset: Int) {
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
                    val base = py * rowStride + offset
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
        private const val FRAZIONE_MINIMA = 0.006f
        private const val ALFA_SFONDO = 0.03f
        /** Celle minime perché una sagoma sia considerata una mano (~0,8% dell'inquadratura). */
        private const val MIN_CELLE_SAGOMA = 25
        private const val SOGLIA_IMMOBILE = 2.5f
        private const val FOTOGRAMMI_OGGETTO_FERMO = 240
        private const val FOTOGRAMMI_USCITA = 2
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
