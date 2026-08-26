package it.example.frattalogic.engine

enum class Fase { IMMERSIONE, EVENTO_BONUS }

/**
 * Stato della discesa: [centroX]/[centroY]/[semiAmpiezza] sono la finestra
 * corrente sul piano complesso, [pixel] l'ultimo fotogramma calcolato da
 * [FractalField] (dimensioni [larghezzaPixel]×[altezzaPixel]). Il punto di
 * partenza è una nota "valle dei cavallucci marini" del bordo dell'insieme
 * di Mandelbrot, ricca di dettaglio a qualunque livello di zoom.
 */
data class ImmersioneState(
    val centroX: Double = -0.743643887037151,
    val centroY: Double = 0.131825904205330,
    val semiAmpiezza: Double = 1.4,
    val livelloZoom: Float = 0f,
    val pixel: IntArray? = null,
    val larghezzaPixel: Int = 0,
    val altezzaPixel: Int = 0,
    val punteggio: Int = 0,
    val fase: Fase = Fase.IMMERSIONE,
    val cameraBonus: Camera? = null,
    val indiceSelezionatoBonus: Int? = null,
    val esitoBonus: Esito = Esito.NESSUNO
)
