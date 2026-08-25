package it.example.frattalogic.engine

import it.example.frattalogic.ui.FractalKind
import it.example.frattalogic.ui.FractalSpec
import kotlin.math.abs
import kotlin.random.Random

/**
 * Genera enigmi logici in modo procedurale (nessun contenuto precaricato):
 * sequenze numeriche, sequenze di figure frattali che crescono in profondità
 * di ricorsione o ruotano di un angolo costante, e un intruso da individuare
 * in una griglia. La [difficulty] (derivata dalla serie di risposte corrette
 * di fila) scala gradualmente la complessità di ciascun tipo.
 */
object PuzzleEngine {

    private var nextId = 0L

    fun generaPuzzle(difficulty: Int, random: Random = Random.Default): Puzzle {
        return when (PuzzleKind.entries[random.nextInt(PuzzleKind.entries.size)]) {
            PuzzleKind.SEQUENZA_NUMERICA -> generaSequenzaNumerica(difficulty, random)
            PuzzleKind.SEQUENZA_PROFONDITA_FRATTALE -> generaSequenzaProfondita(difficulty, random)
            PuzzleKind.SEQUENZA_ROTAZIONE_FRATTALE -> generaSequenzaRotazione(difficulty, random)
            PuzzleKind.INTRUSO -> generaIntruso(difficulty, random)
        }
    }

    private fun numeroOpzioni(difficulty: Int) = (4 + difficulty / 3).coerceIn(4, 6)

    private fun generaSequenzaNumerica(difficulty: Int, random: Random): Puzzle {
        val start = random.nextInt(1, 10 + difficulty * 2)
        val sequenza: List<Int>
        val successivo: Int
        when (random.nextInt(4)) {
            0 -> { // progressione aritmetica
                val passo = random.nextInt(2, 5 + difficulty)
                sequenza = (0 until 4).map { start + it * passo }
                successivo = start + 4 * passo
            }
            1 -> { // progressione geometrica
                val ratio = random.nextInt(2, 3 + difficulty / 4)
                sequenza = (0 until 4).map { start * ratio.toDoublePow(it) }
                successivo = start * ratio.toDoublePow(4)
            }
            2 -> { // passo alternato (+a, +b, +a, +b, ...)
                val a = random.nextInt(2, 6 + difficulty)
                val b = random.nextInt(2, 6 + difficulty)
                var v = start
                val seq = mutableListOf(v)
                listOf(a, b, a, b).forEach { v += it; seq.add(v) }
                sequenza = seq.dropLast(1)
                successivo = seq.last()
            }
            else -> { // tipo Fibonacci: ogni termine è la somma dei due precedenti
                var a = start
                var b = start + random.nextInt(1, 4 + difficulty)
                val seq = mutableListOf(a, b)
                repeat(2) {
                    val c = a + b
                    seq.add(c)
                    a = b
                    b = c
                }
                sequenza = seq.take(4)
                successivo = a + b
            }
        }
        val (opzioni, indiceCorretto) = generaOpzioniNumeriche(successivo, numeroOpzioni(difficulty), random)
        return Puzzle(
            id = nextId++,
            kind = PuzzleKind.SEQUENZA_NUMERICA,
            istruzioni = "Quale numero continua la sequenza?",
            sequenzaData = sequenza.map { PuzzleOption.Numero(it) },
            opzioni = opzioni,
            indiceCorretto = indiceCorretto
        )
    }

    private fun Int.toDoublePow(esponente: Int): Int {
        var risultato = 1
        repeat(esponente) { risultato *= this }
        return risultato
    }

    private fun generaOpzioniNumeriche(corretto: Int, count: Int, random: Random): Pair<List<PuzzleOption>, Int> {
        val distanza = (1 + abs(corretto) / 10).coerceAtLeast(1)
        val valori = linkedSetOf(corretto)
        while (valori.size < count) {
            val delta = (random.nextInt(-3, 4)) * distanza
            if (delta != 0) valori.add(corretto + delta)
        }
        val lista = valori.shuffled(random)
        return lista.map { PuzzleOption.Numero(it) } to lista.indexOf(corretto)
    }

    private fun generaSequenzaProfondita(difficulty: Int, random: Random): Puzzle {
        val kind = if (random.nextBoolean()) FractalKind.TREE else FractalKind.SIERPINSKI
        val maxDepth = if (kind == FractalKind.TREE) 8 else 6
        val minBase = (1 + difficulty / 4).coerceIn(1, maxDepth - 4)
        val maxBase = (maxDepth - 3).coerceAtLeast(minBase + 1)
        val depthBase = if (minBase >= maxBase) minBase else random.nextInt(minBase, maxBase)
        val hue = random.nextInt(0, 360).toFloat()
        val depths = listOf(depthBase, depthBase + 1, depthBase + 2)
        val corretto = depthBase + 3

        val pool = (0..maxDepth).filter { it != corretto }.shuffled(random)
        val distrattori = pool.take(3)
        val tutteDepth = (distrattori + corretto).shuffled(random)
        val indiceCorretto = tutteDepth.indexOf(corretto)

        return Puzzle(
            id = nextId++,
            kind = PuzzleKind.SEQUENZA_PROFONDITA_FRATTALE,
            istruzioni = "Quale figura continua la sequenza (osserva come cresce la ricorsione)?",
            sequenzaData = depths.map { PuzzleOption.Frattale(FractalSpec(kind, it, 0f, hue)) },
            opzioni = tutteDepth.map { PuzzleOption.Frattale(FractalSpec(kind, it, 0f, hue)) },
            indiceCorretto = indiceCorretto
        )
    }

    private fun generaSequenzaRotazione(difficulty: Int, random: Random): Puzzle {
        val kind = FractalKind.entries[random.nextInt(FractalKind.entries.size)]
        val depth = when (kind) {
            FractalKind.TREE -> 5
            FractalKind.SIERPINSKI -> 4
            FractalKind.KOCH -> 3
        }
        val hue = random.nextInt(0, 360).toFloat()
        val ampiezza = (30 + difficulty * 5).toFloat()
        val incremento = if (random.nextBoolean()) ampiezza else -ampiezza
        val rotazioneIniziale = random.nextInt(0, 360).toFloat()
        val rotazioni = (0 until 3).map { rotazioneIniziale + it * incremento }
        val corretta = rotazioneIniziale + 3 * incremento

        val distrattori = mutableListOf<Float>()
        val fattori = listOf(-2f, -1f, 1f, 2f, 0.5f, -0.5f).shuffled(random)
        for (fattore in fattori) {
            if (distrattori.size == 3) break
            val candidato = corretta + incremento * fattore
            if (abs(candidato - corretta) > 5f) distrattori.add(candidato)
        }
        val tutte = (distrattori + corretta).shuffled(random)
        val indiceCorretto = tutte.indexOf(corretta)

        return Puzzle(
            id = nextId++,
            kind = PuzzleKind.SEQUENZA_ROTAZIONE_FRATTALE,
            istruzioni = "La figura ruota sempre dello stesso angolo: quale completa la sequenza?",
            sequenzaData = rotazioni.map { PuzzleOption.Frattale(FractalSpec(kind, depth, it, hue)) },
            opzioni = tutte.map { PuzzleOption.Frattale(FractalSpec(kind, depth, it, hue)) },
            indiceCorretto = indiceCorretto
        )
    }

    private fun generaIntruso(difficulty: Int, random: Random): Puzzle {
        val kind = FractalKind.entries[random.nextInt(FractalKind.entries.size)]
        val depth = when (kind) {
            FractalKind.TREE -> random.nextInt(3, 6)
            FractalKind.SIERPINSKI -> random.nextInt(2, 5)
            FractalKind.KOCH -> random.nextInt(1, 4)
        }
        val hueBase = random.nextInt(0, 360).toFloat()
        val gridSize = (4 + difficulty / 5).coerceIn(4, 9)
        val differenzaHue = (70 - difficulty * 4).coerceAtLeast(18).toFloat()
        val indiceIntruso = random.nextInt(gridSize)
        val opzioni = (0 until gridSize).map { i ->
            val hue = if (i == indiceIntruso) hueBase + differenzaHue else hueBase
            PuzzleOption.Frattale(FractalSpec(kind, depth, 0f, hue))
        }
        return Puzzle(
            id = nextId++,
            kind = PuzzleKind.INTRUSO,
            istruzioni = "Una figura ha una tonalità diversa dalle altre: individuala.",
            sequenzaData = emptyList(),
            opzioni = opzioni,
            indiceCorretto = indiceIntruso
        )
    }
}
