package it.example.forme.gioco

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

/** Ciò che succede durante un aggiornamento della partita: serve a suoni, effetti e messaggi. */
sealed interface Evento {
    data class Presa(val pezzo: Pezzo) : Evento
    data class Incastro(val pezzo: Pezzo, val punti: Int) : Evento
    data class Sbagliato(val pezzo: Pezzo) : Evento
    data class ServonoDueMani(val pezzo: Pezzo) : Evento
    data class LivelloCompletato(val livello: Int, val bonus: Int) : Evento
    data class NuovoLivello(val livello: Int) : Evento
}

enum class StatoPartita { IN_CORSO, LIVELLO_COMPLETATO }

/**
 * Regole del gioco, indipendenti da Android (tutte le misure sono in pixel dello schermo).
 *
 * Su metà schermo ci sono gli incavi, sull'altra metà le forme. Si afferra una forma pizzicandola
 * fra pollice e indice (o chiudendo la mano), la si porta nel suo incavo e si apre la mano: se l'incavo è quello
 * giusto la forma si incastra, se è sbagliato torna al suo posto. Le forme pesanti si sollevano
 * solo con tutte e due le mani. Completati tutti gli incastri si passa al livello successivo,
 * con più forme.
 *
 * @param margineAlto frazione dell'altezza lasciata libera in alto per punteggio e pulsanti
 */
class Partita(
    val larghezza: Float,
    val altezza: Float,
    seme: Long = System.nanoTime(),
    private val margineAlto: Float = 0.12f,
    private val margineBasso: Float = 0.05f,
) {
    private val casuale = Random(seme)

    var livello = 1
        private set
    var punteggio = 0
        private set
    var errori = 0
        private set
    var tempoLivello = 0f
        private set
    var stato = StatoPartita.IN_CORSO
        private set
    var pezzi: List<Pezzo> = emptyList()
        private set
    var slot: List<Slot> = emptyList()
        private set
    /** Forme sotto una mano aperta, da evidenziare perché si possono prendere. */
    var evidenziati: Set<Int> = emptySet()
        private set

    private class Presa(val pezzoId: Int, var scarto: Punto)

    /** Per ogni mano (id), la forma che sta tenendo. */
    private val prese = HashMap<Int, Presa>()
    private val afferravaPrima = HashMap<Int, Boolean>()
    /** Da quanti secondi ogni mano è chiusa senza tenere nulla (finestra per una presa "in ritardo"). */
    private val chiusaDa = HashMap<Int, Float>()
    /** Forme pesanti sollevate con due mani: scarto fra il loro centro e il punto medio delle mani. */
    private val scartoDueMani = HashMap<Int, Punto>()
    private val avvisatoDueMani = HashSet<Int>()
    private var attesa = 0f

    init {
        iniziaLivello(1)
    }

    fun ricomincia() {
        punteggio = 0
        errori = 0
        iniziaLivello(1)
    }

    fun tenutoDa(pezzo: Pezzo): List<Int> = prese.filterValues { it.pezzoId == pezzo.id }.keys.toList()

    fun slotDi(pezzo: Pezzo): Slot = slot.first { it.tipo == pezzo.tipo }

    fun iniziaLivello(n: Int) {
        livello = n
        stato = StatoPartita.IN_CORSO
        tempoLivello = 0f
        prese.clear()
        scartoDueMani.clear()
        avvisatoDueMani.clear()

        val quante = min(2 + n, TipoForma.entries.size)
        val tipi = TipoForma.entries.shuffled(casuale).take(quante)
        val numeroPesanti = when {
            n < 3 -> 0
            n < 5 -> 1
            n < 8 -> 2
            else -> 3
        }
        val pesanti = tipi.shuffled(casuale).take(numeroPesanti).toSet()

        val lato = min(larghezza, altezza)
        var raggio = lato * when {
            quante <= 3 -> 0.12f
            quante <= 5 -> 0.105f
            quante <= 7 -> 0.095f
            else -> 0.085f
        }
        val fattoreCella = if (pesanti.isEmpty()) 1f else FATTORE_PESANTE
        val meta = altezza / 2f
        val distacco = altezza * 0.02f
        val alta = Rettangolo(0f, altezza * margineAlto, larghezza, meta - distacco)
        val bassa = Rettangolo(0f, meta + distacco, larghezza, altezza * (1f - margineBasso))
        // Se le forme non ci stanno, si rimpiccioliscono finché entrambe le metà le contengono
        while (min(alta.capienza(raggio * fattoreCella), bassa.capienza(raggio * fattoreCella)) < quante) {
            raggio *= 0.92f
        }
        // Gli incavi cambiano metà a ogni livello, così si lavora in tutte le direzioni
        val (zonaSlot, zonaPezzi) = if (n % 2 == 1) alta to bassa else bassa to alta
        val posizioniSlot = zonaSlot.disponi(quante, raggio * fattoreCella, casuale)
        val posizioniPezzi = zonaPezzi.disponi(quante, raggio * fattoreCella, casuale)

        slot = tipi.mapIndexed { i, tipo ->
            Slot(tipo, posizioniSlot[i], if (tipo in pesanti) raggio * FATTORE_PESANTE else raggio, tipo in pesanti)
        }
        pezzi = tipi.shuffled(casuale).mapIndexed { i, tipo ->
            Pezzo(
                id = i,
                tipo = tipo,
                casa = posizioniPezzi[i],
                raggio = if (tipo in pesanti) raggio * FATTORE_PESANTE else raggio,
                pesante = tipo in pesanti,
            ).also { it.rimbalzo = 1f }
        }
    }

    /**
     * Fa avanzare il gioco di [dt] secondi con lo stato attuale delle mani.
     * Una forma si prende nel momento in cui una mano passa da aperta a chiusa sopra di essa.
     */
    fun aggiorna(dt: Float, mani: List<ManoGioco>): List<Evento> {
        val eventi = mutableListOf<Evento>()
        val perId = mani.associateBy { it.id }

        if (stato == StatoPartita.LIVELLO_COMPLETATO) {
            attesa -= dt
            anima(dt)
            ricordaMani(mani)
            evidenziati = emptySet()
            if (attesa <= 0f) {
                iniziaLivello(livello + 1)
                eventi += Evento.NuovoLivello(livello)
            }
            return eventi
        }
        tempoLivello += dt

        // 1. Mani che si sono aperte o sono uscite dall'inquadratura: lasciano la forma
        for (id in prese.keys.toList()) {
            val m = perId[id]
            if (m == null || !m.presente || !m.afferra) rilascia(id, eventi)
        }

        // 2. Mani che si sono appena chiuse prendono la forma che hanno sotto. Spesso le dita si
        //    chiudono un attimo prima di arrivare sulla forma: per [FINESTRA_PRESA_S] secondi dopo
        //    la chiusura la mano prende ancora la prima forma su cui passa.
        for (m in mani) {
            val ora = m.presente && m.afferra
            if (!ora) {
                chiusaDa.remove(m.id)
                continue
            }
            val appenaChiusa = afferravaPrima[m.id] != true
            val da = if (appenaChiusa) 0f else (chiusaDa[m.id] ?: FINESTRA_PRESA_S) + dt
            chiusaDa[m.id] = da
            if (m.id !in prese && da <= FINESTRA_PRESA_S && prendi(m, eventi)) {
                // Una presa per chiusura: dopo aver lasciato o incastrato va riaperta la mano
                chiusaDa[m.id] = Float.MAX_VALUE
            }
        }
        ricordaMani(mani)

        // 3. Le forme tenute seguono le mani
        for (pezzo in pezzi) {
            if (pezzo.incastrato) continue
            val mani1 = tenutoDa(pezzo).mapNotNull { perId[it] }
            if (mani1.isEmpty()) continue
            if (!pezzo.pesante) {
                val mano = mani1[0]
                val presa = prese.getValue(mano.id)
                // La forma scivola piano verso il centro della mano, senza scatti
                presa.scarto = presa.scarto * exp(-dt * RIENTRO)
                pezzo.posizione = limita(mano.posizione + presa.scarto, pezzo.raggio)
            } else if (mani1.size >= 2) {
                val centro = Punto.media(mani1.map { it.posizione })
                val scarto = (scartoDueMani[pezzo.id] ?: (pezzo.posizione - centro)) * exp(-dt * RIENTRO)
                scartoDueMani[pezzo.id] = scarto
                pezzo.posizione = limita(centro + scarto, pezzo.raggio)
            } else {
                pezzo.scossa = 0.25f
                if (avvisatoDueMani.add(pezzo.id)) eventi += Evento.ServonoDueMani(pezzo)
                continue
            }
            // Calamita: portata quasi al centro del suo incavo, la forma ci entra da sola
            val giusto = slotDi(pezzo)
            if (!giusto.occupato && pezzo.posizione.distanza(giusto.centro) < giusto.raggio * CALAMITA) {
                incastra(pezzo, giusto, eventi)
            }
        }

        anima(dt)
        evidenziati = mani.filter { it.presente && !it.afferra }
            .mapNotNull { formaSotto(it.posizione)?.id }
            .toSet()
        return eventi
    }

    /** La forma libera più vicina a [p], se è abbastanza vicina da essere presa. */
    fun formaSotto(p: Punto): Pezzo? = pezzi
        .filter { !it.incastrato && it.posizione.distanza(p) < it.raggio * RAGGIO_PRESA }
        .minByOrNull { it.posizione.distanza(p) }

    private fun ricordaMani(mani: List<ManoGioco>) {
        for (m in mani) afferravaPrima[m.id] = m.presente && m.afferra
    }

    private fun prendi(mano: ManoGioco, eventi: MutableList<Evento>): Boolean {
        val candidati = pezzi
            .filter { !it.incastrato && it.posizione.distanza(mano.posizione) < it.raggio * RAGGIO_PRESA }
            .sortedBy { it.posizione.distanza(mano.posizione) }
        for (pezzo in candidati) {
            val altre = tenutoDa(pezzo)
            // Una forma normale si tiene con una mano sola; una pesante con due
            val libera = altre.isEmpty() || (pezzo.pesante && altre.size == 1)
            if (!libera) continue
            prese[mano.id] = Presa(pezzo.id, pezzo.posizione - mano.posizione)
            pezzo.tornaACasa = false
            eventi += Evento.Presa(pezzo)
            return true
        }
        return false
    }

    private fun rilascia(idMano: Int, eventi: MutableList<Evento>) {
        val presa = prese.remove(idMano) ?: return
        val pezzo = pezzi.first { it.id == presa.pezzoId }
        if (pezzo.incastrato) return
        val rimaste = tenutoDa(pezzo).size
        if (pezzo.pesante) {
            val eraSollevata = rimaste + 1 >= 2
            scartoDueMani.remove(pezzo.id)
            if (rimaste == 0) avvisatoDueMani.remove(pezzo.id)
            if (eraSollevata) verificaPosizione(pezzo, eventi)
        } else {
            verificaPosizione(pezzo, eventi)
        }
    }

    /** Una forma appena lasciata: si incastra, torna indietro (incavo sbagliato) o resta lì. */
    private fun verificaPosizione(pezzo: Pezzo, eventi: MutableList<Evento>) {
        val giusto = slotDi(pezzo)
        if (!giusto.occupato && pezzo.posizione.distanza(giusto.centro) < giusto.raggio * TOLLERANZA_RILASCIO) {
            incastra(pezzo, giusto, eventi)
            return
        }
        val sbagliato = slot.firstOrNull {
            it !== giusto && pezzo.posizione.distanza(it.centro) < it.raggio * TOLLERANZA_SBAGLIATO
        }
        if (sbagliato != null) {
            errori++
            pezzo.tornaACasa = true
            eventi += Evento.Sbagliato(pezzo)
        }
    }

    private fun incastra(pezzo: Pezzo, s: Slot, eventi: MutableList<Evento>) {
        pezzo.incastrato = true
        pezzo.tornaACasa = false
        pezzo.rimbalzo = 1f
        s.occupato = true
        prese.values.removeAll { it.pezzoId == pezzo.id }
        scartoDueMani.remove(pezzo.id)
        val punti = if (pezzo.pesante) PUNTI_PESANTE else PUNTI_INCASTRO
        punteggio += punti
        eventi += Evento.Incastro(pezzo, punti)

        if (pezzi.all { it.incastrato }) {
            val bonus = ((TEMPO_PER_FORMA_S * pezzi.size - tempoLivello).coerceAtLeast(0f) * PUNTI_PER_SECONDO).toInt()
            punteggio += bonus
            stato = StatoPartita.LIVELLO_COMPLETATO
            attesa = ATTESA_LIVELLO_S
            eventi += Evento.LivelloCompletato(livello, bonus)
        }
    }

    private fun anima(dt: Float) {
        for (p in pezzi) {
            p.rimbalzo = (p.rimbalzo - dt * 2.5f).coerceAtLeast(0f)
            p.scossa = (p.scossa - dt).coerceAtLeast(0f)
            when {
                p.incastrato -> p.posizione = p.posizione.verso(slotDi(p).centro, 1f - exp(-dt * 18f))
                p.tornaACasa -> {
                    p.posizione = p.posizione.verso(p.casa, 1f - exp(-dt * 7f))
                    if (p.posizione.distanza(p.casa) < 2f) {
                        p.posizione = p.casa
                        p.tornaACasa = false
                    }
                }
            }
        }
    }

    private fun limita(p: Punto, r: Float) = Punto(
        p.x.coerceIn(r, (larghezza - r).coerceAtLeast(r)),
        p.y.coerceIn(r, (altezza - r).coerceAtLeast(r)),
    )

    /** Zona dello schermo in cui disporre forme o incavi su una griglia, senza sovrapposizioni. */
    private class Rettangolo(val sinistra: Float, val alto: Float, val destra: Float, val basso: Float) {
        val larghezza get() = destra - sinistra
        val altezza get() = basso - alto

        private fun colonne(r: Float) = floor(larghezza / (r * PASSO)).toInt().coerceAtLeast(1)
        private fun righe(r: Float) = floor(altezza / (r * PASSO)).toInt().coerceAtLeast(1)
        fun capienza(r: Float) = colonne(r) * righe(r)

        fun disponi(n: Int, r: Float, casuale: Random): List<Punto> {
            val colonne = colonne(r)
            val righe = righe(r)
            val lc = larghezza / colonne
            val hc = altezza / righe
            // Quanto ci si può spostare dal centro della cella restando dentro di essa
            val giocoX = (lc / 2f - r * 1.05f).coerceAtLeast(0f)
            val giocoY = (hc / 2f - r * 1.05f).coerceAtLeast(0f)
            return (0 until colonne * righe).shuffled(casuale).take(n).map { cella ->
                val cx = sinistra + (cella % colonne + 0.5f) * lc
                val cy = alto + (cella / colonne + 0.5f) * hc
                Punto(
                    cx + (casuale.nextFloat() * 2f - 1f) * giocoX,
                    cy + (casuale.nextFloat() * 2f - 1f) * giocoY,
                )
            }
        }
    }

    companion object {
        const val FATTORE_PESANTE = 1.2f
        /** Lato della cella della griglia, in raggi: lascia spazio fra una forma e l'altra. */
        const val PASSO = 2.3f
        /** Una mano prende una forma se il punto fra pollice e indice è entro questo multiplo del raggio. */
        const val RAGGIO_PRESA = 1.5f
        const val FINESTRA_PRESA_S = 0.6f
        const val CALAMITA = 0.3f
        const val TOLLERANZA_RILASCIO = 0.8f
        const val TOLLERANZA_SBAGLIATO = 0.75f
        const val RIENTRO = 3f
        const val PUNTI_INCASTRO = 100
        const val PUNTI_PESANTE = 250
        const val TEMPO_PER_FORMA_S = 8f
        const val PUNTI_PER_SECONDO = 10
        const val ATTESA_LIVELLO_S = 2.8f
    }
}
