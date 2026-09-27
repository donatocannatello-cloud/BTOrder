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

    private val voce0 = InseguitoreVoce()
    private val voce1 = InseguitoreVoce()
    private var ultimoMs = 0L

    /**
     * false: una mano fa tutto, oppure destra = nota e sinistra = volume (theremin classico).
     * true: "Due voci", ogni mano suona la propria nota; la seconda è in [secondaVoce].
     */
    @Volatile var dueVoci = false

    /** Ultime mani riconosciute, per disegnarle nell'anteprima. */
    var ultimeMani: List<List<PuntoMano2D>> = emptyList()
        private set

    /** Posizione della seconda voce (solo in modalità Due voci), altrimenti null. */
    var secondaVoce: PosizioneMano? = null
        private set

    /**
     * Analizza un fotogramma già raddrizzato e specchiato come l'anteprima.
     * Restituisce la voce principale (in Due voci: la mano più a sinistra).
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

        if (!dueVoci) {
            secondaVoce = null
            voce1.aggiorna(null, dt)
            return voce0.aggiorna(SceltaMani.comandi(mani), dt)
        }
        val punte = SceltaMani.voci(mani, floatArrayOf(voce0.x, voce1.x))
        val principale = voce0.aggiorna(punte[0]?.let { it.x to it.y }, dt)
        secondaVoce = voce1.aggiorna(punte[1]?.let { it.x to it.y }, dt)
        return principale
    }

    override fun close() = landmarker.close()

    companion object {
        /** Scaricato automaticamente negli asset durante la compilazione (vedi build.gradle.kts). */
        const val MODELLO = "hand_landmarker.task"
    }
}
