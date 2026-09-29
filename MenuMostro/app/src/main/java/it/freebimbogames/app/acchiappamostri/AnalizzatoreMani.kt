package it.freebimbogames.app.acchiappamostri

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Collega CameraX a MediaPipe: ogni fotogramma (RGBA) viene raddrizzato e specchiato come
 * l'anteprima della fotocamera anteriore, così i punti delle mani coincidono con l'immagine
 * mostrata a tutto schermo.
 */
class AnalizzatoreMani(
    private val tracker: () -> TrackerMani?,
    private val suFotogramma: (FotogrammaMani) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        image.use {
            val t = tracker() ?: return
            val raddrizzata = raddrizza(it.toBitmap(), it.imageInfo.rotationDegrees)
            suFotogramma(t.elabora(raddrizzata, it.imageInfo.timestamp / 1_000_000))
        }
    }

    private fun raddrizza(sorgente: Bitmap, rotazione: Int): Bitmap {
        val m = Matrix().apply {
            postRotate(rotazione.toFloat())
            postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(sorgente, 0, 0, sorgente.width, sorgente.height, m, false)
    }
}
