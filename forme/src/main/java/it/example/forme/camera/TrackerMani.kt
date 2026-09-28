package it.example.forme.camera

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import it.example.forme.gioco.Punto
import java.io.Closeable

/** Le mani trovate in un fotogramma, con i 21 punti in coordinate normalizzate (0..1) dell'immagine. */
class FotogrammaMani(
    val numero: Long,
    val timestampMs: Long,
    /** Larghezza / altezza del fotogramma raddrizzato: serve a riportare i punti sullo schermo. */
    val aspetto: Float,
    val mani: List<List<Punto>>,
)

/**
 * Riconoscimento delle due mani con MediaPipe Hand Landmarker, eseguito interamente sul telefono.
 * Va chiamato sempre dallo stesso thread (quello di analisi della fotocamera).
 */
class TrackerMani(context: Context) : Closeable {

    private val landmarker: HandLandmarker = HandLandmarker.createFromOptions(
        context,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODELLO).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
    )

    private var ultimoMs = 0L
    private var numero = 0L

    /** Analizza un fotogramma già raddrizzato e specchiato come l'anteprima. */
    fun elabora(bitmap: Bitmap, timestampMs: Long): FotogrammaMani {
        // MediaPipe richiede istanti strettamente crescenti
        val ts = if (timestampMs <= ultimoMs) ultimoMs + 1 else timestampMs
        ultimoMs = ts
        val risultato = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), ts)
        val mani = risultato.landmarks().map { mano -> mano.map { Punto(it.x(), it.y()) } }
        return FotogrammaMani(++numero, ts, bitmap.width.toFloat() / bitmap.height, mani)
    }

    override fun close() = landmarker.close()

    companion object {
        /** Scaricato automaticamente negli asset durante la compilazione (vedi build.gradle.kts). */
        const val MODELLO = "hand_landmarker.task"
    }
}
