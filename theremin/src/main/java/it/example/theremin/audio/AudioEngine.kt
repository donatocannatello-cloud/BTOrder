package it.example.theremin.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process

/**
 * Riproduce in streaming i campioni di [ThereminSynth] su un [AudioTrack] a bassa latenza,
 * da un thread dedicato ad alta priorità audio.
 *
 * Gli ultimi campioni generati sono copiati in un buffer circolare che la UI legge per
 * disegnare l'oscilloscopio al centro delle onde cromatiche.
 */
class AudioEngine {

    val sampleRate: Int = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
    val synth = ThereminSynth(sampleRate)

    private val storico = FloatArray(DIM_STORICO)
    @Volatile private var posStorico = 0

    @Volatile private var inEsecuzione = false
    private var thread: Thread? = null

    val attivo: Boolean get() = inEsecuzione

    fun avvia() {
        if (inEsecuzione) return
        inEsecuzione = true
        thread = Thread(::cicloAudio, "theremin-audio").also { it.start() }
    }

    fun ferma() {
        inEsecuzione = false
        thread?.join(500)
        thread = null
    }

    /** Copia in [dest] gli ultimi `dest.size` campioni (il più recente in fondo). */
    fun copiaStorico(dest: FloatArray) {
        val fine = posStorico
        val n = dest.size.coerceAtMost(DIM_STORICO)
        for (i in 0 until n) {
            dest[i] = storico[(fine - n + i + DIM_STORICO) % DIM_STORICO]
        }
    }

    private fun cicloAudio() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

        val formato = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minimo = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(formato)
            .setBufferSizeInBytes(minimo * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        val blocco = FloatArray(BLOCCO)
        track.play()
        try {
            while (inEsecuzione) {
                synth.render(blocco)
                track.write(blocco, 0, blocco.size, AudioTrack.WRITE_BLOCKING)
                var p = posStorico
                for (c in blocco) {
                    storico[p] = c
                    p = (p + 1) % DIM_STORICO
                }
                posStorico = p
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
        }
    }

    private companion object {
        const val BLOCCO = 256
        const val DIM_STORICO = 2048
    }
}
