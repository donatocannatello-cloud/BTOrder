package it.example.theremin.learn

import kotlin.math.abs

/**
 * Avanzamento dell'allievo lungo un [Brano], indipendente da Android.
 *
 * La lezione aspetta il giocatore: si passa alla nota successiva solo dopo aver tenuto quella
 * richiesta, intonata, per [TENUTA_MS] millisecondi. Anche le note ripetute vanno quindi
 * "ritenute" una seconda volta.
 */
class Lezione(val brano: Brano) {

    @Volatile var indice = 0
        private set

    private var tenutoMs = 0f

    val completata: Boolean get() = indice >= brano.note.size
    val notaCorrente: Nota? get() = brano.note.getOrNull(indice)

    /** Quanto manca a convalidare la nota corrente (0..1), utile per l'anello di avanzamento. */
    val progressoNota: Float get() = (tenutoMs / TENUTA_MS).coerceIn(0f, 1f)

    fun prossime(n: Int): List<Nota> = brano.note.drop(indice + 1).take(n)

    /**
     * Da chiamare a ogni fotogramma analizzato.
     * @param midiSuonato nota prodotta in quel momento, `null` se la mano non è in campo o il volume è nullo
     * @return true se la nota corrente è stata appena convalidata
     */
    @Synchronized
    fun aggiorna(midiSuonato: Float?, dtMs: Float): Boolean {
        val bersaglio = notaCorrente ?: return false
        if (midiSuonato != null && intonata(midiSuonato, bersaglio)) {
            tenutoMs += dtMs
            if (tenutoMs >= TENUTA_MS) {
                indice++
                tenutoMs = 0f
                return true
            }
        } else {
            // Una breve stonatura non azzera tutto: il tempo tenuto cala più lentamente di come cresce
            tenutoMs = (tenutoMs - dtMs * 0.5f).coerceAtLeast(0f)
        }
        return false
    }

    @Synchronized
    fun ricomincia() {
        indice = 0
        tenutoMs = 0f
    }

    companion object {
        const val TENUTA_MS = 180f
        private const val TOLLERANZA_SEMITONI = 0.5f

        fun intonata(midiSuonato: Float, nota: Nota): Boolean =
            abs(midiSuonato - nota.midi) < TOLLERANZA_SEMITONI
    }
}
