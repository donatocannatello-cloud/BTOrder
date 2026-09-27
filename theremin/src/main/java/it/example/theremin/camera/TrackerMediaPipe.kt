package it.example.theremin.camera

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.Closeable

/**
 * Riconoscimento vero della mano con MediaPipe Hand Landmarker (modello di Google, eseguito sul
 * telefono): per ogni mano restituisce 21 punti, dalla base del polso alla punta delle dita.
 *
 * A differenza del rilevamento per movimento non dipende dallo sfondo né dalla luce e non scambia
 * viso o corpo per una mano. Va chiamato sempre dallo stesso thread (quello di analisi della fotocamera).
 */
class TrackerMediaPipe(context: Context) : Closeable {

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

    private val filtroX = FiltroOneEuro()
    private val filtroY = FiltroOneEuro()
    private var presenza = 0f
    private var fotogrammiSenzaMano = 0
    private var ultimoMs = 0L
    private var x = 0.5f
    private var y = 0.5f

    /** Ultime mani riconosciute, per disegnarle nell'anteprima. */
    var ultimeMani: List<List<PuntoMano2D>> = emptyList()
        private set

    /**
     * Analizza un fotogramma già raddrizzato e specchiato come l'anteprima.
     * @param timestampMs istante del fotogramma, in millisecondi
     */
    fun elabora(bitmap: Bitmap, timestampMs: Long): PosizioneMano {
        // MediaPipe richiede istanti strettamente crescenti
        val ts = if (timestampMs <= ultimoMs) ultimoMs + 1 else timestampMs
        val dt = if (ultimoMs == 0L) 1f / 30f else ((ts - ultimoMs) / 1000f).coerceIn(0.005f, 0.2f)
        ultimoMs = ts

        val risultato = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), ts)
        val mani = risultato.landmarks().map { mano -> mano.map { PuntoMano2D(it.x(), it.y()) } }
        ultimeMani = mani

        val comandi = SceltaMani.comandi(mani)
        if (comandi != null) {
            val (cx, cy) = comandi
            if (presenza < 0.3f) {
                // Mano appena comparsa: si parte da dove si trova
                filtroX.reimposta(cx)
                filtroY.reimposta(cy)
            }
            x = filtroX.filtra(cx.coerceIn(0f, 1f), dt)
            y = filtroY.filtra(cy.coerceIn(0f, 1f), dt)
            fotogrammiSenzaMano = 0
            presenza = minOf(1f, presenza + 0.5f)
        } else {
            fotogrammiSenzaMano++
            if (fotogrammiSenzaMano >= 2) presenza = 0f
        }
        return PosizioneMano(x, y, presenza)
    }

    override fun close() = landmarker.close()

    companion object {
        /** Scaricato automaticamente negli asset durante la compilazione (vedi build.gradle.kts). */
        const val MODELLO = "hand_landmarker.task"
    }
}
