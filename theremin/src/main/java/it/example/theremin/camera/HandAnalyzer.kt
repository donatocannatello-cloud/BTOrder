package it.example.theremin.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Collega CameraX ai due rilevatori della mano. I fotogrammi arrivano in RGBA:
 * - con **MediaPipe** vengono raddrizzati e specchiati come l'anteprima e passati al modello;
 * - con il **rilevamento per movimento** si usa direttamente il canale verde (il più vicino alla
 *   luminanza) del buffer, senza copie.
 *
 * [mediaPipe] restituisce il rilevatore IA se è scelto e disponibile, altrimenti null.
 */
class HandAnalyzer(
    private val tracker: MotionTracker,
    private val mediaPipe: () -> TrackerMediaPipe?,
    /** Posizione principale, mani riconosciute e (solo in Due voci) posizione della seconda voce. */
    private val suPosizione: (PosizioneMano, List<List<PuntoMano2D>>, PosizioneMano?) -> Unit,
) : ImageAnalysis.Analyzer {

    private var buffer = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        image.use {
            val mp = mediaPipe()
            if (mp != null) {
                val raddrizzata = raddrizza(it.toBitmap(), it.imageInfo.rotationDegrees)
                val posizione = mp.elabora(raddrizzata, it.imageInfo.timestamp / 1_000_000)
                suPosizione(posizione, mp.ultimeMani, mp.secondaVoce)
                return
            }

            val piano = it.planes[0]
            val dati = piano.buffer
            dati.rewind()
            if (buffer.size < dati.remaining()) buffer = ByteArray(dati.remaining())
            dati.get(buffer, 0, dati.remaining())
            val posizione = tracker.elabora(
                luma = buffer,
                larghezza = it.width,
                altezza = it.height,
                rowStride = piano.rowStride,
                pixelStride = piano.pixelStride,
                rotazione = it.imageInfo.rotationDegrees,
                specchia = true,
                timestampNs = it.imageInfo.timestamp,
                // Con 4 byte per pixel (RGBA) si legge il verde; con la sola luminanza, il byte stesso
                offsetCanale = if (piano.pixelStride >= 3) 1 else 0,
            )
            suPosizione(posizione, emptyList(), null)
        }
    }

    /** Ruota il fotogramma in verticale e lo specchia, così i punti coincidono con l'anteprima. */
    private fun raddrizza(sorgente: Bitmap, rotazione: Int): Bitmap {
        val m = Matrix().apply {
            postRotate(rotazione.toFloat())
            postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(sorgente, 0, 0, sorgente.width, sorgente.height, m, false)
    }
}
