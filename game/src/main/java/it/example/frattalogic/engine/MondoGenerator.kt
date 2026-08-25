package it.example.frattalogic.engine

import it.example.frattalogic.ui.FractalKind
import it.example.frattalogic.ui.FractalSpec
import kotlin.math.floor
import kotlin.random.Random

/**
 * Genera il paesaggio frattale in modo procedurale e deterministico: il
 * mondo è diviso in una griglia di celle, e per ogni cella un hash delle sue
 * coordinate (più l'indice del mondo corrente) decide se e come posizionare
 * un elemento — così tornando nello stesso punto si ritrova lo stesso
 * paesaggio, senza dover tenere in memoria nulla.
 */
object MondoGenerator {

    private const val DIMENSIONE_CELLA = 260f
    private const val DISTANZA_PER_MONDO = 4200f
    private const val MOLTIPLICATORE_HASH = 6364136223846793005L

    private val nomiMondo = listOf(
        "Correnti di Rame", "Barriera di Vetro", "Golfo Sommerso",
        "Arcipelago Cavo", "Faglia Silenziosa", "Bassofondo Ambrato",
        "Spirale Boreale", "Abisso Chiaro"
    )

    fun mondoPer(distanzaPercorsa: Float): Mondo {
        val indice = (distanzaPercorsa / DISTANZA_PER_MONDO).toInt()
        val kind = FractalKind.entries[indice % FractalKind.entries.size]
        val hueBase = (indice * 47f) % 360f
        val nome = nomiMondo[indice % nomiMondo.size]
        return Mondo(indice, nome, kind, hueBase)
    }

    fun elementiVicini(centroX: Float, centroY: Float, raggio: Float, mondo: Mondo): List<ElementoMondo> {
        val celleMinX = floor((centroX - raggio) / DIMENSIONE_CELLA).toInt()
        val celleMaxX = floor((centroX + raggio) / DIMENSIONE_CELLA).toInt()
        val celleMinY = floor((centroY - raggio) / DIMENSIONE_CELLA).toInt()
        val celleMaxY = floor((centroY + raggio) / DIMENSIONE_CELLA).toInt()

        val risultato = mutableListOf<ElementoMondo>()
        for (cx in celleMinX..celleMaxX) {
            for (cy in celleMinY..celleMaxY) {
                val seme = semeCella(cx, cy, mondo.indice)
                val random = Random(seme)
                if (random.nextFloat() > 0.55f) continue

                val x = cx * DIMENSIONE_CELLA + random.nextFloat() * DIMENSIONE_CELLA
                val y = cy * DIMENSIONE_CELLA + random.nextFloat() * DIMENSIONE_CELLA
                val dx = x - centroX
                val dy = y - centroY
                if (dx * dx + dy * dy > raggio * raggio) continue

                val kind = if (random.nextFloat() > 0.7f) {
                    FractalKind.entries[random.nextInt(FractalKind.entries.size)]
                } else {
                    mondo.kindDominante
                }
                val profonditaMassima = when (kind) {
                    FractalKind.TREE -> 7
                    FractalKind.SIERPINSKI -> 5
                    FractalKind.KOCH -> 4
                }
                val spec = FractalSpec(
                    kind = kind,
                    depth = random.nextInt(2, profonditaMassima + 1),
                    rotationDeg = random.nextFloat() * 360f,
                    hue = mondo.hueBase + random.nextFloat() * 60f - 30f
                )

                risultato.add(
                    ElementoMondo(
                        id = seme,
                        x = x,
                        y = y,
                        spec = spec,
                        scala = 0.6f + random.nextFloat() * 1.2f
                    )
                )
            }
        }
        return risultato
    }

    private fun semeCella(cx: Int, cy: Int, mondoIndice: Int): Long {
        var seme = cx.toLong()
        seme = seme * MOLTIPLICATORE_HASH + cy.toLong()
        seme = seme * MOLTIPLICATORE_HASH + mondoIndice.toLong()
        seme = seme xor (seme ushr 33)
        seme *= MOLTIPLICATORE_HASH
        seme = seme xor (seme ushr 29)
        return seme
    }
}
