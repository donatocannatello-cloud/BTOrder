package it.example.frattalogic.engine

import it.example.frattalogic.ui.FractalSpec

/**
 * Un nodo tappabile disposto in cerchio attorno al nucleo frattale al centro
 * della schermata. Tutti i nodi di una [Camera] condividono la stessa regola
 * generativa (stesso tipo, stessa profondità di ricorsione, stessa rotazione,
 * stessa tonalità) tranne [dissonante]: quello ha *una sola* di queste
 * proprietà alterata — è la nota fuori posto da individuare per scendere più
 * in profondità.
 */
data class NodoFrattale(
    val indice: Int,
    val angoloDeg: Float,
    val spec: FractalSpec,
    val dissonante: Boolean
)

/** Una "camera" della discesa: il nucleo centrale più l'anello di nodi. */
data class Camera(
    val profondita: Int,
    val nucleo: FractalSpec,
    val nodi: List<NodoFrattale>,
    val indiceDissonante: Int
)
