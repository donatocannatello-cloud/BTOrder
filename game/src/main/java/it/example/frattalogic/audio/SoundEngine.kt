package it.example.frattalogic.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sintetizzatore multi-strumento in tempo reale: nessun file audio incluso,
 * tutto è generato via [AudioTrack] in modalità streaming mescolando per
 * somma additiva quattro voci continue — che si aggiungono una alla volta
 * mano a mano che si scende più in profondità — più brevi "stinger"
 * transitori sugli eventi di gioco:
 *
 * - **basso**: drone all'ottava bassa della nota fondamentale, sempre attivo;
 * - **arpeggio**: onda triangolare che percorre una scala pentatonica minore,
 *   dalla profondità 2 in su, sempre più veloce scendendo;
 * - **pad armonico**: accordo di tre seni (fondamentale, terza min., quinta),
 *   dalla profondità 4 in su;
 * - **percussione**: un breve impulso di rumore filtrato ad ogni nota
 *   dell'arpeggio, dalla profondità 6 in su.
 *
 * La nota fondamentale scende lentamente con la profondità, per dare la
 * sensazione di sprofondare sempre più in basso.
 */
class SoundEngine {

    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null
    private var thread: Thread? = null
    private val rumore = Random(System.nanoTime())

    @Volatile private var running = false
    @Volatile private var profondita = 0
    @Volatile private var radiceFreq = calcolaRadiceFreq(0)
    @Volatile private var campioniPerNota = calcolaCampioniPerNota(0)

    private var faseBasso = 0.0
    private var faseArpeggio = 0.0
    private val fasiPad = DoubleArray(3)
    private var contatoreCampioni = 0L

    private val scalaMinorePentatonica = intArrayOf(0, 3, 5, 7, 10)
    private val intervalliPad = intArrayOf(0, 3, 7)

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

    /** Aggiorna la nota fondamentale e il tempo dell'arpeggio in base alla profondità raggiunta. */
    fun aggiornaProfondita(nuovaProfondita: Int) {
        profondita = nuovaProfondita
        radiceFreq = calcolaRadiceFreq(nuovaProfondita)
        campioniPerNota = calcolaCampioniPerNota(nuovaProfondita)
    }

    /** Accordo consonante (ottave e quinta) quando si trova la dissonanza e si scende. */
    fun onRisolto(nuovaProfondita: Int) {
        aggiornaProfondita(nuovaProfondita)
        val r = radiceFreq.toFloat()
        aggiungiBlip(r * 2f, 140)
        aggiungiBlip(r * 3f, 140, ritardoMs = 40)
        aggiungiBlip(r * 4f, 170, ritardoMs = 90)
    }

    /** Cluster dissonante (seconda minore + tritono) quando si tocca il nodo sbagliato. */
    fun onRottura() {
        val r = radiceFreq.toFloat()
        aggiungiBlip(r * 2f, 260, ampiezza = 0.40f)
        aggiungiBlip(r * 2f * semitoniARatio(1).toFloat(), 260, ampiezza = 0.35f)
        aggiungiBlip(r * 2f * semitoniARatio(6).toFloat(), 220, ampiezza = 0.30f)
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
                contatoreCampioni++
                val profonditaLocale = profondita
                val radice = radiceFreq
                val durataNota = campioniPerNota
                var mix = 0.0

                // Basso: drone continuo un'ottava sotto la fondamentale.
                faseBasso += 2.0 * PI * (radice / 2.0) / sampleRate
                if (faseBasso > 2.0 * PI) faseBasso -= 2.0 * PI
                mix += sin(faseBasso) * 0.16

                // Arpeggio: onda triangolare che percorre la scala, un'ottava sopra.
                if (profonditaLocale >= 2) {
                    val posizioneNota = (contatoreCampioni % durataNota).toInt()
                    val indiceNota = ((contatoreCampioni / durataNota) % scalaMinorePentatonica.size).toInt()
                    val freqArpeggio = radice * 2.0 * semitoniARatio(scalaMinorePentatonica[indiceNota])
                    faseArpeggio += 2.0 * PI * freqArpeggio / sampleRate
                    if (faseArpeggio > 2.0 * PI) faseArpeggio -= 2.0 * PI
                    mix += triangolo(faseArpeggio) * 0.11 * inviluppoNota(posizioneNota, durataNota)
                }

                // Pad armonico: accordo minore sostenuto, un registro più in alto.
                if (profonditaLocale >= 4) {
                    for (v in intervalliPad.indices) {
                        val freqPad = radice * 4.0 * semitoniARatio(intervalliPad[v])
                        fasiPad[v] += 2.0 * PI * freqPad / sampleRate
                        if (fasiPad[v] > 2.0 * PI) fasiPad[v] -= 2.0 * PI
                        mix += sin(fasiPad[v]) * 0.045
                    }
                }

                // Percussione: un impulso di rumore ad ogni nota dell'arpeggio.
                if (profonditaLocale >= 6) {
                    val posizioneNota = (contatoreCampioni % durataNota).toInt()
                    val durataImpulso = (sampleRate * 0.04).toInt().coerceAtLeast(1)
                    if (posizioneNota < durataImpulso) {
                        val decadimento = exp(-6.0 * posizioneNota / durataImpulso)
                        mix += (rumore.nextDouble(-1.0, 1.0)) * 0.10 * decadimento
                    }
                }

                // Eventi transitori: accordi di risoluzione o cluster di rottura.
                for (ev in eventi) {
                    ev.t++
                    if (ev.t < 0) continue
                    if (ev.t >= ev.durataCampioni) {
                        eventi.remove(ev)
                        continue
                    }
                    ev.fase += 2.0 * PI * ev.freq / sampleRate
                    mix += sin(ev.fase) * ev.ampiezza * inviluppoBlip(ev.t, ev.durataCampioni)
                }

                val clamped = mix.coerceIn(-1.0, 1.0)
                buffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
            }
            audioTrack?.write(buffer, 0, bufferFrames)
        }
    }

    private fun inviluppoBlip(t: Int, durata: Int): Double {
        val attacco = (durata * 0.08).coerceAtLeast(1.0)
        return if (t < attacco) t / attacco else exp(-3.0 * (t - attacco) / durata)
    }

    private fun inviluppoNota(posizione: Int, durata: Long): Double {
        val attacco = (durata * 0.06).coerceAtLeast(1.0)
        return when {
            posizione < attacco -> posizione / attacco
            else -> (1.0 - (posizione - attacco) / (durata - attacco)).coerceIn(0.0, 1.0)
        }
    }

    private fun triangolo(fase: Double): Double = (2.0 / PI) * asin(sin(fase))

    private fun semitoniARatio(semitoni: Int): Double = 2.0.pow(semitoni / 12.0)

    private fun calcolaRadiceFreq(profondita: Int): Double =
        (220.0 * 0.974.pow(profondita.toDouble())).coerceAtLeast(85.0)

    private fun calcolaCampioniPerNota(profondita: Int): Long {
        val durataMs = (420 - profondita * 6).coerceAtLeast(150)
        return (durataMs.toLong() * sampleRate / 1000).coerceAtLeast(1L)
    }
}
