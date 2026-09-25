package it.example.theremin.learn

/** Una nota di una melodia: altezza MIDI e durata in battiti. */
data class Nota(val midi: Int, val battiti: Float)

/** Melodia di un brano classico (tutti di pubblico dominio), trascritta nell'estensione del theremin. */
class Brano(val titolo: String, val autore: String, val bpm: Int, partitura: String) {

    val note: List<Nota> = leggiPartitura(partitura)

    val midiMin: Int = note.minOf { it.midi }
    val midiMax: Int = note.maxOf { it.midi }

    /**
     * Estensione usata per mappare la mano sulle note in modalità Impara: la melodia più un
     * semitono di margine per parte, così anche le note estreme hanno una fascia intera.
     */
    val estensioneMin: Float get() = midiMin - 1f
    val estensioneMax: Float get() = midiMax + 1f

    /** Durata di una nota in millisecondi al tempo del brano. */
    fun durataMs(nota: Nota): Long = (nota.battiti * 60_000f / bpm).toLong()

    companion object {
        private val CLASSI = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

        /**
         * Legge una partitura in notazione anglosassone, una nota per token: `NOME[#|b]OTTAVA:BATTITI`,
         * ad esempio `"E4:1 F#4:0.5 Bb3:2"` (Do centrale = C4 = MIDI 60).
         */
        fun leggiPartitura(testo: String): List<Nota> =
            testo.trim().split(Regex("\\s+")).map { token ->
                val (nome, durata) = token.split(':').also {
                    require(it.size == 2) { "Nota non valida: $token" }
                }
                var classe = CLASSI[nome[0]] ?: error("Nota non valida: $token")
                var i = 1
                when (nome.getOrNull(1)) {
                    '#' -> { classe++; i++ }
                    'b' -> { classe--; i++ }
                }
                val ottava = nome.substring(i).toInt()
                Nota((ottava + 1) * 12 + classe, durata.toFloat())
            }
    }
}

/** Repertorio della modalità Impara. */
object Repertorio {
    val brani: List<Brano> = listOf(
        Brano(
            "Inno alla gioia", "L. van Beethoven", 120,
            "E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 E4:1.5 D4:0.5 D4:2 " +
                "E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 D4:1.5 C4:0.5 C4:2 " +
                "D4:1 D4:1 E4:1 C4:1 D4:1 E4:0.5 F4:0.5 E4:1 C4:1 D4:1 E4:0.5 F4:0.5 E4:1 D4:1 C4:1 D4:1 G3:2 " +
                "E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 D4:1.5 C4:0.5 C4:2"
        ),
        Brano(
            "Per Elisa", "L. van Beethoven", 110,
            "E5:0.5 D#5:0.5 E5:0.5 D#5:0.5 E5:0.5 B4:0.5 D5:0.5 C5:0.5 A4:1.5 " +
                "C4:0.5 E4:0.5 A4:0.5 B4:1.5 E4:0.5 G#4:0.5 B4:0.5 C5:1.5 " +
                "E4:0.5 E5:0.5 D#5:0.5 E5:0.5 D#5:0.5 E5:0.5 B4:0.5 D5:0.5 C5:0.5 A4:1.5 " +
                "C4:0.5 E4:0.5 A4:0.5 B4:1.5 E4:0.5 C5:0.5 B4:0.5 A4:2"
        ),
        Brano(
            "Fra Martino", "Tradizionale", 110,
            "C4:1 D4:1 E4:1 C4:1 C4:1 D4:1 E4:1 C4:1 E4:1 F4:1 G4:2 E4:1 F4:1 G4:2 " +
                "G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4:1 C4:1 G4:0.5 A4:0.5 G4:0.5 F4:0.5 E4:1 C4:1 " +
                "C4:1 G3:1 C4:2 C4:1 G3:1 C4:2"
        ),
        Brano(
            "Ah! vous dirai-je, maman", "W. A. Mozart", 100,
            "C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2 F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2 " +
                "G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2 G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2 " +
                "C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2 F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2"
        ),
        Brano(
            "Eine kleine Nachtmusik", "W. A. Mozart", 130,
            "G4:1.5 D4:0.5 G4:1.5 D4:0.5 G4:0.5 D4:0.5 G4:0.5 B4:0.5 D5:2 " +
                "C5:1.5 A4:0.5 C5:1.5 A4:0.5 C5:0.5 A4:0.5 F#4:0.5 A4:0.5 D4:2"
        ),
        Brano(
            "Greensleeves", "Tradizionale inglese", 170,
            "A4:1 C5:2 D5:1 E5:1.5 F5:0.5 E5:1 D5:2 B4:1 G4:1.5 A4:0.5 B4:1 C5:2 A4:1 " +
                "A4:1.5 G#4:0.5 A4:1 B4:2 G#4:1 E4:2 A4:1 C5:2 D5:1 E5:1.5 F5:0.5 E5:1 D5:2 B4:1 " +
                "G4:1.5 A4:0.5 B4:1 C5:1.5 B4:0.5 A4:1 G#4:1.5 F#4:0.5 G#4:1 A4:3"
        ),
        Brano(
            "Ninna nanna", "J. Brahms", 100,
            "E4:0.5 E4:0.5 G4:2 E4:0.5 E4:0.5 G4:2 E4:0.5 G4:0.5 C5:1 B4:1.5 A4:0.5 A4:1 G4:1 " +
                "D4:0.5 E4:0.5 F4:1 D4:1 D4:0.5 E4:0.5 F4:2 D4:0.5 F4:0.5 B4:0.5 A4:0.5 G4:1 B4:1 C5:2"
        ),
        Brano(
            "Canone in Re", "J. Pachelbel", 80,
            "F#5:2 E5:2 D5:2 C#5:2 B4:2 A4:2 B4:2 C#5:2 D5:2 C#5:2 B4:2 A4:2 G4:2 F#4:2 G4:2 E4:2 " +
                "D4:1 F#4:1 A4:1 G4:1 F#4:1 D4:1 F#4:1 E4:1 D4:1 B3:1 D4:1 A4:1 G4:1 B4:1 A4:1 G4:1"
        ),
        Brano(
            "Il mattino (Peer Gynt)", "E. Grieg", 110,
            "B4:1 G#4:1 F#4:1 E4:1 F#4:1 G#4:1 B4:1 G#4:1 F#4:1 E4:1 F#4:0.5 G#4:0.5 F#4:0.5 G#4:0.5 " +
                "B4:1 G#4:1 B4:1 C#5:1 G#4:1 C#5:1 B4:1 G#4:1 F#4:1 E4:3"
        ),
        Brano(
            "Minuetto in Sol", "C. Petzold (attr. J. S. Bach)", 120,
            "D5:1 G4:0.5 A4:0.5 B4:0.5 C5:0.5 D5:1 G4:1 G4:1 E5:1 C5:0.5 D5:0.5 E5:0.5 F#5:0.5 G5:1 G4:1 G4:1 " +
                "C5:1 D5:0.5 C5:0.5 B4:0.5 A4:0.5 B4:1 C5:0.5 B4:0.5 A4:0.5 G4:0.5 " +
                "F#4:1 G4:0.5 A4:0.5 B4:0.5 G4:0.5 A4:3"
        ),
        Brano(
            "Largo (Dal Nuovo Mondo)", "A. Dvořák", 60,
            "E4:1 G4:0.5 G4:2.5 E4:1 D4:0.5 C4:2.5 D4:1 E4:0.5 G4:1 E4:0.5 D4:3 " +
                "E4:1 G4:0.5 G4:2.5 E4:1 D4:0.5 C4:2.5 D4:1 E4:0.5 D4:1 C4:0.5 C4:3"
        ),
    )
}
