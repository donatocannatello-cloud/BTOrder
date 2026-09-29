package it.freebimbogames.app.acchiappamostri

import kotlin.math.exp

/** Una mano che partecipa al gioco (vera o simulata dal dito sullo schermo), in pixel dello schermo. */
data class ManoGioco(
    val id: Int,
    val posizione: Punto,
    val presente: Boolean,
    val afferra: Boolean,
)

/**
 * Tiene traccia delle due mani da un fotogramma all'altro. MediaPipe non garantisce che la
 * stessa mano abbia lo stesso indice in fotogrammi diversi, quindi ogni mano riconosciuta viene
 * assegnata al "posto" (0 o 1) la cui ultima posizione è più vicina: così un mostro resta in
 * mano a chi l'ha preso.
 *
 * Inoltre:
 * - la posizione viene levigata per togliere il tremolio del riconoscimento;
 * - una mano persa per pochi fotogrammi resta "presente" (con il mostro in mano) per [TOLLERANZA_S];
 * - la presa si rilascia solo dopo [FOTOGRAMMI_RILASCIO] fotogrammi consecutivi di mano aperta.
 */
class InseguitoreMani(numero: Int = 2) {

    class Posto(val id: Int) {
        var posizione = Punto.ZERO
        var presente = false
        var afferra = false
        /** Punti della mano sullo schermo, per disegnarla. */
        var punti: List<Punto> = emptyList()
        var assenteDa = 0f
        var fotogrammiAperta = 0
        /** Distanza pollice-indice in rapporto alla mano: serve a mostrare quanto manca al pizzico. */
        var pizzico = 1f

        fun comeMano() = ManoGioco(id, posizione, presente, afferra && presente)
    }

    val posti = List(numero) { Posto(it) }

    /**
     * Quante mani partecipano al gioco. Con una sola (l'altra tiene il telefono) si segue solo
     * la mano già in gioco o, all'inizio, quella più vicina alla fotocamera; le altre si ignorano.
     */
    var maniAttive = numero
        set(valore) {
            field = valore.coerceIn(1, posti.size)
            for (p in posti.drop(field)) {
                p.presente = false
                p.afferra = false
                p.punti = emptyList()
            }
        }

    fun mani(): List<ManoGioco> = posti.map { it.comeMano() }

    /**
     * @param rilevate mani del fotogramma, ciascuna con 21 punti in pixel dello schermo
     * @param dt secondi trascorsi dal fotogramma precedente
     */
    fun aggiorna(rilevate: List<List<Punto>>, dt: Float) {
        val valide = rilevate.filter { it.size >= Gesti.PUNTI_MANO }
        val mani = if (valide.size <= maniAttive) valide else {
            val primo = posti[0]
            if (primo.presente) valide.sortedBy { Gesti.puntoPresa(it).distanza(primo.posizione) }.take(maniAttive)
            else valide.sortedByDescending { Gesti.misura(it) }.take(maniAttive)
        }
        val centri = mani.map { Gesti.puntoPresa(it) }
        val assegnazione = assegna(centri)
        val alfa = 1f - exp(-dt / LEVIGATURA_S)

        for (posto in posti.take(maniAttive)) {
            val i = assegnazione[posto.id]
            if (i == null) {
                posto.assenteDa += dt
                if (posto.assenteDa > TOLLERANZA_S) {
                    posto.presente = false
                    posto.afferra = false
                    posto.punti = emptyList()
                }
                continue
            }
            val mano = mani[i]
            val appenaArrivata = !posto.presente
            posto.posizione = if (appenaArrivata) centri[i] else posto.posizione.verso(centri[i], alfa)
            posto.presente = true
            posto.assenteDa = 0f
            posto.punti = mano
            posto.pizzico = Gesti.pizzico(mano)

            val chiusa = Gesti.afferra(mano, posto.afferra)
            when {
                chiusa -> {
                    posto.afferra = true
                    posto.fotogrammiAperta = 0
                }
                posto.afferra -> {
                    posto.fotogrammiAperta++
                    if (posto.fotogrammiAperta >= FOTOGRAMMI_RILASCIO) posto.afferra = false
                }
            }
        }
    }

    /** Per ogni posto, l'indice della mano rilevata che gli spetta (o null). */
    private fun assegna(centri: List<Punto>): Map<Int, Int?> {
        fun costo(posto: Posto, c: Punto) =
            if (posto.presente) posto.posizione.distanza(c) else COSTO_POSTO_LIBERO
        val risultato = HashMap<Int, Int?>()
        posti.forEach { risultato[it.id] = null }
        val attivi = posti.take(maniAttive)
        when (centri.size) {
            0 -> Unit
            1 -> risultato[attivi.minBy { costo(it, centri[0]) }.id] = 0
            else -> {
                // Con due mani e due posti si prova l'abbinamento diretto e quello incrociato
                val a = posti[0]
                val b = posti[1]
                val diretto = costo(a, centri[0]) + costo(b, centri[1])
                val incrociato = costo(a, centri[1]) + costo(b, centri[0])
                if (diretto <= incrociato) {
                    risultato[a.id] = 0; risultato[b.id] = 1
                } else {
                    risultato[a.id] = 1; risultato[b.id] = 0
                }
            }
        }
        return risultato
    }

    companion object {
        const val LEVIGATURA_S = 0.045f
        const val TOLLERANZA_S = 0.35f
        const val FOTOGRAMMI_RILASCIO = 3
        /** Costo per dare una mano a un posto vuoto: più alto di qualunque distanza sullo schermo. */
        private const val COSTO_POSTO_LIBERO = 1e6f
    }
}
