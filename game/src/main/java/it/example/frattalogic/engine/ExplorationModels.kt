package it.example.frattalogic.engine

import it.example.frattalogic.ui.FractalKind
import it.example.frattalogic.ui.FractalSpec

enum class Fase { ESPLORAZIONE, EVENTO_BONUS }

/** Posizione e assetto del vascello nello spazio infinito del mondo. */
data class Vascello(
    val x: Float = 0f,
    val y: Float = 0f,
    val rotta: Float = -90f,
    val velocita: Float = 90f
)

/** Un elemento frattale del paesaggio, posizionato in coordinate di mondo. */
data class ElementoMondo(
    val id: Long,
    val x: Float,
    val y: Float,
    val spec: FractalSpec,
    val scala: Float
)

/** Una regione del mondo attraversata: cambia palette/tipo frattale dominante. */
data class Mondo(
    val indice: Int,
    val nome: String,
    val kindDominante: FractalKind,
    val hueBase: Float
)

data class ExplorationState(
    val vascello: Vascello = Vascello(),
    val mondo: Mondo,
    val elementiVisibili: List<ElementoMondo> = emptyList(),
    val distanzaPercorsa: Float = 0f,
    val punteggio: Int = 0,
    val fase: Fase = Fase.ESPLORAZIONE,
    val cameraBonus: Camera? = null,
    val indiceSelezionatoBonus: Int? = null,
    val esitoBonus: Esito = Esito.NESSUNO
)
