package it.example.frattalogic.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sintetizzatore audio minimale, senza alcun file sonoro incluso: tutto è
 * generato in tempo reale mescolando per somma additiva un pad ambientale
 * continuo — la cui frequenza e intensità seguono lo stato di gioco (streak,
 * difficoltà) — con brevi "blip" a inviluppo che scattano sugli eventi
 * (risposta corretta, sbagliata, traguardo raggiunto). Il mix è quindi
 * sempre diverso, in funzione di ciò che succede in partita.
 */
class SoundEngine {

    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile private var ambientFreq = 90f
    @Volatile private var ambientAmount = 0.10f
    private var ambientPhase = 0.0

    private val eventi = CopyOnWriteArrayList<Evento>()

    private class Evento(
        val freq: Float,
        var t: Int,
        val durataCampioni: Int,
        val ampiezza: Float
    ) {
        var fase = 0.0
    }

    fun start() {
        if (running) return
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBuf * 2).coerceAtLeast(4096)
        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        running = true
        audioTrack?.play()
        thread = thread(name = "frattalogic-audio", isDaemon = true) { generaAudio() }
    }

    fun stop() {
        running = false
        thread?.join(200)
        thread = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        eventi.clear()
    }

    /** Il pad ambientale si scurisce/schiarisce seguendo la difficoltà corrente. */
    fun onDifficolta(livello: Int) {
        ambientFreq = 90f + livello * 6f
    }

    fun onRisposta(corretta: Boolean, streak: Int) {
        if (corretta) {
            val base = 440f + streak.coerceAtMost(12) * 24f
            aggiungiBlip(base, 90)
            aggiungiBlip(base * 1.5f, 90, ritardoMs = 60)
            ambientAmount = (0.08f + streak * 0.01f).coerceAtMost(0.22f)
        } else {
            aggiungiBlip(160f, 220, ampiezza = 0.5f)
            aggiungiBlip(151f, 220, ampiezza = 0.4f)
            ambientAmount = 0.08f
        }
    }

    fun onTraguardo() {
        aggiungiBlip(660f, 130)
        aggiungiBlip(880f, 130, ritardoMs = 90)
        aggiungiBlip(1320f, 160, ritardoMs = 180)
    }

    private fun aggiungiBlip(freq: Float, durataMs: Int, ampiezza: Float = 0.35f, ritardoMs: Int = 0) {
        val durataCampioni = durataMs * sampleRate / 1000
        eventi.add(Evento(freq, -(ritardoMs * sampleRate / 1000), durataCampioni, ampiezza))
    }

    private fun generaAudio() {
        val bufferFrames = 1024
        val buffer = ShortArray(bufferFrames)
        while (running) {
            for (i in 0 until bufferFrames) {
                var mix = 0.0

                ambientPhase += 2.0 * PI * ambientFreq / sampleRate
                if (ambientPhase > 2.0 * PI) ambientPhase -= 2.0 * PI
                mix += sin(ambientPhase) * ambientAmount

                for (ev in eventi) {
                    ev.t++
                    if (ev.t < 0) continue
                    if (ev.t >= ev.durataCampioni) {
                        eventi.remove(ev)
                        continue
                    }
                    ev.fase += 2.0 * PI * ev.freq / sampleRate
                    mix += sin(ev.fase) * ev.ampiezza * inviluppo(ev.t, ev.durataCampioni)
                }

                val clamped = mix.coerceIn(-1.0, 1.0)
                buffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
            }
            audioTrack?.write(buffer, 0, bufferFrames)
        }
    }

    private fun inviluppo(t: Int, durata: Int): Double {
        val attacco = (durata * 0.08).coerceAtLeast(1.0)
        return if (t < attacco) {
            t / attacco
        } else {
            exp(-3.0 * (t - attacco) / durata)
        }
    }
}
