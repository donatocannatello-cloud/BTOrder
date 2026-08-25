package it.example.frattalogic.engine

import it.example.frattalogic.ui.FractalKind
import it.example.frattalogic.ui.FractalSpec
import kotlin.random.Random

/**
 * Genera ogni "camera" della discesa: un nucleo frattale centrale più un
 * anello di nodi che condividono la sua stessa regola generativa — tranne
 * uno, il nodo dissonante, a cui viene alterata una sola proprietà (tonalità,
 * rotazione o profondità di ricorsione). Più si scende, più l'alterazione è
 * sottile e i nodi sono numerosi: è più difficile individuare la dissonanza.
 */
object DiveEngine {

    fun generaCamera(profondita: Int, random: Random): Camera {
        val kind = FractalKind.entries[random.nextInt(FractalKind.entries.size)]
        val massimoProfonditaFrattale = massimoProfondita(kind)
        val minimoBase = (2 + profondita / 6).coerceIn(1, massimoProfonditaFrattale - 1)
        val depthBase = random.nextInt(minimoBase, massimoProfonditaFrattale + 1)
        val hueBase = random.nextInt(0, 360).toFloat()
        val rotazioneBase = random.nextInt(0, 360).toFloat()
        val nucleo = FractalSpec(kind, depthBase, rotazioneBase, hueBase)

        val numeroNodi = (6 + profondita / 3).coerceIn(6, 10)
        val indiceDissonante = random.nextInt(numeroNodi)
        val specDissonante = perturba(nucleo, profondita, random, massimoProfonditaFrattale)

        val nodi = (0 until numeroNodi).map { i ->
            NodoFrattale(
                indice = i,
                angoloDeg = 360f * i / numeroNodi,
                spec = if (i == indiceDissonante) specDissonante else nucleo,
                dissonante = i == indiceDissonante
            )
        }

        return Camera(profondita, nucleo, nodi, indiceDissonante)
    }

    private fun massimoProfondita(kind: FractalKind): Int = when (kind) {
        FractalKind.TREE -> 8
        FractalKind.SIERPINSKI -> 6
        FractalKind.KOCH -> 4
    }

    private fun perturba(base: FractalSpec, profondita: Int, random: Random, massimoProfondita: Int): FractalSpec {
        val ampiezza = (55 - profondita * 2).coerceAtLeast(9).toFloat()
        return when (random.nextInt(3)) {
            0 -> base.copy(hue = base.hue + segno(random) * ampiezza)
            1 -> base.copy(rotationDeg = base.rotationDeg + segno(random) * (ampiezza * 1.2f))
            else -> {
                val delta = when {
                    base.depth <= 1 -> 1
                    base.depth >= massimoProfondita -> -1
                    else -> if (random.nextBoolean()) 1 else -1
                }
                base.copy(depth = base.depth + delta)
            }
        }
    }

    private fun segno(random: Random): Float = if (random.nextBoolean()) 1f else -1f
}
