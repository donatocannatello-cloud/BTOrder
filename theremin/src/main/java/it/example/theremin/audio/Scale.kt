package it.example.theremin.audio

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Modalità di intonazione: glissando libero (come un vero theremin) o note agganciate a una scala. */
enum class Scala(val etichetta: String, private val gradi: IntArray?) {
    CONTINUA("Continua", null),
    CROMATICA("Cromatica", intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)),
    MAGGIORE("Maggiore", intArrayOf(0, 2, 4, 5, 7, 9, 11)),
    PENTATONICA("Pentatonica", intArrayOf(0, 2, 4, 7, 9));

    /** Aggancia una nota MIDI (anche frazionaria) al grado più vicino della scala (tonica Do). */
    fun quantizza(midi: Float): Float {
        val gradi = gradi ?: return midi
        val ottava = floor(midi / 12f)
        val dentroOttava = midi - ottava * 12f
        var migliore = 0f
        var distanzaMinima = Float.MAX_VALUE
        // Si considera anche il Do dell'ottava successiva (12) per arrotondare correttamente verso l'alto
        for (g in gradi.map { it.toFloat() } + 12f) {
            val d = kotlin.math.abs(dentroOttava - g)
            if (d < distanzaMinima) {
                distanzaMinima = d
                migliore = g
            }
        }
        return ottava * 12f + migliore
    }

    fun successiva(): Scala = entries[(ordinal + 1) % entries.size]
}

object Note {
    /** Estensione del theremin: da Do3 (MIDI 48) a Do6 (MIDI 84), tre ottave. */
    const val MIDI_MIN = 48f
    const val MIDI_MAX = 84f

    private val NOMI = arrayOf("Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")

    fun midiToHz(midi: Float): Float = 440f * 2f.pow((midi - 69f) / 12f)

    fun hzToMidi(hz: Float): Float = 69f + 12f * (ln(hz / 440f) / ln(2f))

    /**
     * Posizione normalizzata (0 = grave, 1 = acuto) → nota MIDI, eventualmente quantizzata.
     * L'estensione [min]..[max] è di default quella intera del theremin; in modalità Impara
     * viene ristretta al brano, così ogni nota occupa una fascia più larga dell'inquadratura.
     */
    fun posizioneToMidi(posizione: Float, scala: Scala, min: Float = MIDI_MIN, max: Float = MIDI_MAX): Float {
        val p = posizione.coerceIn(0f, 1f)
        return scala.quantizza(min + p * (max - min))
    }

    /** Inversa di [posizioneToMidi]: dove mettere la mano (0..1) per suonare [midi]. */
    fun midiToPosizione(midi: Float, min: Float = MIDI_MIN, max: Float = MIDI_MAX): Float =
        ((midi - min) / (max - min)).coerceIn(0f, 1f)

    /** Nome italiano della nota più vicina, con numero d'ottava (es. "La4"). */
    fun nome(midi: Float): String {
        val n = midi.roundToInt()
        val classe = ((n % 12) + 12) % 12
        val ottava = n / 12 - 1
        return NOMI[classe] + ottava
    }

    /** Nome italiano della nota senza ottava (es. "La"). */
    fun nomeBreve(midi: Float): String {
        val n = midi.roundToInt()
        return NOMI[((n % 12) + 12) % 12]
    }

    /** Tinta (0..360) associata all'altezza: il cerchio cromatico delle 12 note mappato sulla ruota dei colori. */
    fun tinta(midi: Float): Float {
        val classe = ((midi % 12f) + 12f) % 12f
        return classe * 30f
    }
}
