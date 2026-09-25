package it.example.theremin.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Collega CameraX al [MotionTracker]: estrae il piano di luminanza (Y) di ogni fotogramma
 * YUV_420_888 e notifica la posizione della mano a [suPosizione] (sul thread di analisi).
 */
class HandAnalyzer(
    private val tracker: MotionTracker,
    private val suPosizione: (PosizioneMano) -> Unit,
) : ImageAnalysis.Analyzer {

    private var buffer = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        image.use {
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
            )
            suPosizione(posizione)
        }
    }
}
