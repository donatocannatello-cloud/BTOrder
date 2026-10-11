package it.freebimbogames.app.acchiappamostri

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Ciò che succede durante un aggiornamento della partita: serve a suoni, effetti e messaggi. */
sealed interface Evento {
    data class Presa(val pezzo: Pezzo) : Evento
    data class Incastro(val pezzo: Pezzo, val punti: Int) : Evento
    data class Sbagliato(val pezzo: Pezzo) : Evento
    data class LivelloCompletato(val livello: Int, val bonus: Int) : Evento
    data class NuovoLivello(val livello: Int) : Evento
    /** Il mostro in mano si è trasformato in un altro (livelli con le trasformazioni). */
    data class Cambio(val pezzo: Pezzo, val tenuto: Boolean) : Evento
}

enum class StatoPartita { IN_CORSO, LIVELLO_COMPLETATO }

/**
 * Regole del gioco, indipendenti da Android (tutte le misure sono in pixel dello schermo).
 *
 * Su metà schermo ci sono le tane, sull'altra metà i mostri. Si afferra un mostro pizzicandolo
 * fra pollice e indice (o chiudendo la mano), lo si porta nella sua tana e si apre la mano: se la
 * tana è quella giusta il mostro ci si infila, se è sbagliata torna al suo posto. Acchiappati
 * tutti i mostri si passa al livello successivo, con più mostri. Dal livello 2 i mostri si
 * muovono da soli, al 3 si trasformano ogni pochi secondi in un altro mostro (se non si arriva
 * in tempo alla tana bisogna cambiare tana), dal 4 si muovono e si trasformano.
 *
 * @param margineAlto frazione dell'altezza lasciata libera in alto per punteggio e pulsanti
 * @param margineLaterale frazione della larghezza lasciata libera ai due lati: una mano sola,
 * con l'altra che tiene il telefono, non arriva comoda fino al bordo dello schermo.
 */
class Partita(
    val larghezza: Float,
    val altezza: Float,
    seme: Long = System.nanoTime(),
    private val margineAlto: Float = 0.12f,
    private val margineBasso: Float = 0.08f,
    private val margineLaterale: Float = 0.08f,
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
    /** I mostri del livello si muovono da soli. */
    var movimento = false
        private set
    /** I mostri del livello si trasformano ogni pochi secondi. */
    var trasformazione = false
        private set
    private val lato get() = min(larghezza, altezza)
    /** Zona in cui si muovono i mostri: tutto lo schermo tranne i margini (fascia superiore,
     * inferiore e laterali) riservati a punteggio, pulsanti e comodità di una mano sola. */
    private val campo
        get() = Rettangolo(
            larghezza * margineLaterale,
            altezza * margineAlto,
            larghezza * (1f - margineLaterale),
            altezza * (1f - margineBasso),
        )

    /** Mostri sotto una mano aperta, da evidenziare perché si possono prendere. */
    var evidenziati: Set<Int> = emptySet()
        private set

    private class Presa(val pezzoId: Int, var scarto: Punto)

    /** Per ogni mano (id), il mostro che sta tenendo. */
    private val prese = HashMap<Int, Presa>()
    private val afferravaPrima = HashMap<Int, Boolean>()
    /** Da quanti secondi ogni mano è chiusa senza tenere nulla (finestra per una presa "in ritardo"). */
    private val chiusaDa = HashMap<Int, Float>()
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

        val quante = min(2 + n, TipoMostro.entries.size)
        val tipi = TipoMostro.entries.shuffled(casuale).take(quante)

        movimento = n == 2 || n >= 4
        trasformazione = n >= 3
        var raggio = lato * when {
            quante <= 3 -> 0.06f
            quante <= 5 -> 0.0525f
            quante <= 7 -> 0.0475f
            else -> 0.0425f
        }
        val meta = altezza / 2f
        val distacco = altezza * 0.02f
        val sinistra = larghezza * margineLaterale
        val destra = larghezza * (1f - margineLaterale)
        val alta = Rettangolo(sinistra, altezza * margineAlto, destra, meta - distacco)
        val bassa = Rettangolo(sinistra, meta + distacco, destra, altezza * (1f - margineBasso))
        // Se i mostri non ci stanno, si rimpiccioliscono finché entrambe le metà li contengono
        while (min(alta.capienza(raggio), bassa.capienza(raggio)) < quante) {
            raggio *= 0.92f
        }
        // Le tane cambiano metà a ogni livello, così si lavora in tutte le direzioni
        val (zonaSlot, zonaPezzi) = if (n % 2 == 1) alta to bassa else bassa to alta
        val posizioniSlot = zonaSlot.disponi(quante, raggio, casuale)
        val posizioniPezzi = zonaPezzi.disponi(quante, raggio, casuale)

        slot = tipi.mapIndexed { i, tipo -> Slot(tipo, posizioniSlot[i], raggio) }
        pezzi = tipi.shuffled(casuale).mapIndexed { i, tipo ->
            Pezzo(id = i, tipo = tipo, casa = posizioniPezzi[i], raggio = raggio).also { it.rimbalzo = 1f }
        }
        if (movimento) {
            // Più veloci a ogni livello, fino a un massimo
            val v = lato * (0.07f + 0.012f * (n - 2)).coerceAtMost(0.16f)
            for (pz in pezzi) {
                val a = casuale.nextDouble(0.0, 2 * PI)
                pz.velocita = Punto((cos(a) * v).toFloat(), (sin(a) * v).toFloat())
            }
        }
        if (trasformazione) {
            // Trasformazioni più frequenti a ogni livello; ogni mostro ha i suoi tempi
            val periodo = (4.5f - 0.3f * (n - 3)).coerceAtLeast(2.5f)
            for (pz in pezzi) {
                pz.periodoCambio = periodo * (0.85f + casuale.nextFloat() * 0.3f)
                pz.tempoAlCambio = pz.periodoCambio * (0.5f + casuale.nextFloat() * 0.5f)
            }
        }
    }

    /**
     * Fa avanzare il gioco di [dt] secondi con lo stato attuale della mano.
     * Un mostro si prende nel momento in cui la mano passa da aperta a chiusa sopra di esso.
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

        // 1. Mani che si sono aperte o sono uscite dall'inquadratura: lasciano il mostro
        for (id in prese.keys.toList()) {
            val m = perId[id]
            if (m == null || !m.presente || !m.afferra) rilascia(id, eventi)
        }

        // 2. Mani che si sono appena chiuse prendono il mostro che hanno sotto. Spesso le dita si
        //    chiudono un attimo prima di arrivare sul mostro: per [FINESTRA_PRESA_S] secondi dopo
        //    la chiusura la mano prende ancora il primo mostro su cui passa.
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

        // 3. I mostri tenuti seguono la mano
        for (pezzo in pezzi) {
            if (pezzo.incastrato) continue
            val idMano = tenutoDa(pezzo).firstOrNull() ?: continue
            val mano = perId[idMano] ?: continue
            val presa = prese.getValue(mano.id)
            // Il mostro scivola piano verso il centro della mano, senza scatti
            presa.scarto = presa.scarto * exp(-dt * RIENTRO)
            pezzo.posizione = limita(mano.posizione + presa.scarto, pezzo.raggio)
            // Calamita: portato quasi al centro della sua tana, il mostro ci entra da solo
            val giusto = slotDi(pezzo)
            if (!giusto.occupato && pezzo.posizione.distanza(giusto.centro) < giusto.raggio * CALAMITA) {
                incastra(pezzo, giusto, eventi)
            }
        }

        muovi(dt, perId)
        trasforma(dt, eventi)
        anima(dt)
        evidenziati = mani.filter { it.presente && !it.afferra }
            .mapNotNull { mostroSotto(it.posizione)?.id }
            .toSet()
        return eventi
    }

    /** Il mostro libero più vicino a [p], se è abbastanza vicino da essere preso. */
    fun mostroSotto(p: Punto): Pezzo? = pezzi
        .filter { !it.incastrato && it.posizione.distanza(p) < raggioPresa(it) }
        .minByOrNull { it.posizione.distanza(p) }

    /** I mostri liberi vagano per il campo e rimbalzano sui bordi; quelli in mano stanno fermi. */
    private fun muovi(dt: Float, perId: Map<Int, ManoGioco>) {
        if (!movimento) return
        val c = campo
        for (p in pezzi) {
            if (p.incastrato || p.tornaACasa || tenutoDa(p).any { perId[it]?.presente == true }) continue
            var x = p.posizione.x + p.velocita.x * dt
            var y = p.posizione.y + p.velocita.y * dt
            var vx = p.velocita.x
            var vy = p.velocita.y
            val r = p.raggio
            if (x < c.sinistra + r) { x = c.sinistra + r; vx = kotlin.math.abs(vx) }
            if (x > c.destra - r) { x = c.destra - r; vx = -kotlin.math.abs(vx) }
            if (y < c.alto + r) { y = c.alto + r; vy = kotlin.math.abs(vy) }
            if (y > c.basso - r) { y = c.basso - r; vy = -kotlin.math.abs(vy) }
            p.posizione = Punto(x, y)
            p.velocita = Punto(vx, vy)
        }
    }

    /**
     * Allo scadere del suo tempo ogni mostro libero diventa un altro, scelto fra quelli che hanno
     * ancora la tana libera: così il livello resta sempre risolvibile. Si trasforma anche mentre
     * è in mano, ed è proprio questa la sfida.
     */
    private fun trasforma(dt: Float, eventi: MutableList<Evento>) {
        if (!trasformazione) return
        for (p in pezzi) {
            if (p.incastrato || p.periodoCambio <= 0f) continue
            p.tempoAlCambio -= dt
            if (p.tempoAlCambio > 0f) continue
            p.tempoAlCambio += p.periodoCambio
            cambiaTipo(p, eventi)
        }
    }

    private fun cambiaTipo(p: Pezzo, eventi: MutableList<Evento>) {
        val possibili = slot.filter { !it.occupato }.map { it.tipo }.distinct()
        val altri = possibili.filter { it != p.tipo }
        if (altri.isEmpty()) return
        p.tipo = altri.random(casuale)
        p.rimbalzo = 1f
        eventi += Evento.Cambio(p, tenutoDa(p).isNotEmpty())
    }

    /** Distanza entro cui una mano prende il mostro: generosa, anche per mani imprecise. */
    fun raggioPresa(p: Pezzo) = maxOf(p.raggio * RAGGIO_PRESA, lato * PRESA_MINIMA)

    private fun ricordaMani(mani: List<ManoGioco>) {
        for (m in mani) afferravaPrima[m.id] = m.presente && m.afferra
    }

    private fun prendi(mano: ManoGioco, eventi: MutableList<Evento>): Boolean {
        val candidati = pezzi
            .filter { !it.incastrato && tenutoDa(it).isEmpty() && it.posizione.distanza(mano.posizione) < raggioPresa(it) }
            .sortedBy { it.posizione.distanza(mano.posizione) }
        val pezzo = candidati.firstOrNull() ?: return false
        prese[mano.id] = Presa(pezzo.id, pezzo.posizione - mano.posizione)
        pezzo.tornaACasa = false
        eventi += Evento.Presa(pezzo)
        return true
    }

    private fun rilascia(idMano: Int, eventi: MutableList<Evento>) {
        val presa = prese.remove(idMano) ?: return
        val pezzo = pezzi.first { it.id == presa.pezzoId }
        if (pezzo.incastrato) return
        verificaPosizione(pezzo, eventi)
    }

    /** Un mostro appena lasciato: si infila nella tana, torna indietro (tana sbagliata) o resta lì. */
    private fun verificaPosizione(pezzo: Pezzo, eventi: MutableList<Evento>) {
        val giusto = slotDi(pezzo)
        if (!giusto.occupato && pezzo.posizione.distanza(giusto.centro) < maxOf(giusto.raggio * TOLLERANZA_RILASCIO, lato * RILASCIO_MINIMO)) {
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
        punteggio += PUNTI_INCASTRO
        eventi += Evento.Incastro(pezzo, PUNTI_INCASTRO)
        // Un altro mostro libero trasformato nello stesso tipo non ha più una tana: cambia subito
        for (altra in pezzi) {
            if (!altra.incastrato && altra.tipo == pezzo.tipo) cambiaTipo(altra, eventi)
        }

        if (pezzi.all { it.incastrato }) {
            val bonus = ((TEMPO_PER_MOSTRO_S * pezzi.size - tempoLivello).coerceAtLeast(0f) * PUNTI_PER_SECONDO).toInt()
            punteggio += bonus
            stato = StatoPartita.LIVELLO_COMPLETATO
            attesa = ATTESA_LIVELLO_S
            eventi += Evento.LivelloCompletato(livello, bonus)
        }
    }

    private fun anima(dt: Float) {
        for (p in pezzi) {
            p.rimbalzo = (p.rimbalzo - dt * 2.5f).coerceAtLeast(0f)
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

    /** Zona dello schermo in cui disporre mostri o tane su una griglia, senza sovrapposizioni. */
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
        /** Lato della cella della griglia, in raggi: lascia spazio fra un mostro e l'altro. */
        const val PASSO = 2.3f
        /** Una mano prende un mostro se il punto fra pollice e indice è entro questo multiplo del
         * raggio: generoso apposta, perché una mano sola e poco precisa deve riuscire ad agganciare
         * il mostro senza doverci stare esattamente sopra. */
        const val RAGGIO_PRESA = 1.9f
        const val FINESTRA_PRESA_S = 0.6f
        /** Minimi (in frazione del lato corto dello schermo) per presa e incastro dei mostri piccoli. */
        const val PRESA_MINIMA = 0.11f
        const val RILASCIO_MINIMO = 0.06f
        const val CALAMITA = 0.3f
        const val TOLLERANZA_RILASCIO = 0.8f
        const val TOLLERANZA_SBAGLIATO = 0.75f
        const val RIENTRO = 3f
        const val PUNTI_INCASTRO = 100
        const val TEMPO_PER_MOSTRO_S = 8f
        const val PUNTI_PER_SECONDO = 10
        const val ATTESA_LIVELLO_S = 2.8f
    }
}
