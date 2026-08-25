package it.example.frattalogic.engine

import it.example.frattalogic.ui.FractalSpec

/** Un'opzione mostrata nel gioco: o un numero, o una figura frattale. */
sealed class PuzzleOption {
    data class Numero(val valore: Int) : PuzzleOption()
    data class Frattale(val spec: FractalSpec) : PuzzleOption()
}

enum class PuzzleKind {
    SEQUENZA_NUMERICA,
    SEQUENZA_PROFONDITA_FRATTALE,
    SEQUENZA_ROTAZIONE_FRATTALE,
    INTRUSO
}

/**
 * Un singolo enigma: [sequenzaData] è la serie di elementi "dati" da osservare
 * (vuota per l'INTRUSO, dove la griglia stessa è la domanda), [opzioni] è la
 * griglia tappabile tra cui scegliere, [indiceCorretto] l'indice giusto in essa.
 */
data class Puzzle(
    val id: Long,
    val kind: PuzzleKind,
    val istruzioni: String,
    val sequenzaData: List<PuzzleOption>,
    val opzioni: List<PuzzleOption>,
    val indiceCorretto: Int
)
