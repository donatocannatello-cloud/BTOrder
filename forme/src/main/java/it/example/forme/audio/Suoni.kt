package it.example.forme.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Effetti sonori del gioco, sintetizzati all'avvio (niente file audio nel pacchetto):
 * vengono scritti come piccoli WAV nella cache e suonati con SoundPool, che permette
 * di sovrapporli senza ritardi.
 */
class Suoni(context: Context) {

    enum class Effetto { PRESA, INCASTRO, SBAGLIATO, PESANTE, LIVELLO, CAMBIO }

    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val id = HashMap<Effetto, Int>()

    init {
        val cartella = File(context.cacheDir, "suoni").apply { mkdirs() }
        for (e in Effetto.entries) {
            val file = File(cartella, "${e.name.lowercase()}.wav")
            file.writeBytes(wav(campioni(e)))
            id[e] = pool.load(file.absolutePath, 1)
        }
    }

    fun suona(e: Effetto) {
        id[e]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    fun rilascia() = pool.release()

    private fun campioni(e: Effetto): FloatArray = when (e) {
        // "Pop" breve che sale
        Effetto.PRESA -> glissato(520.0, 880.0, 0.07)
        // "Magia": la forma in mano è diventata un'altra
        Effetto.CAMBIO -> concatena(glissato(1200.0, 700.0, 0.06), glissato(700.0, 1400.0, 0.08))
        // Due note luminose (Do6 - Sol6)
        Effetto.INCASTRO -> concatena(nota(1046.5, 0.09, 20.0), nota(1568.0, 0.28, 9.0))
        // Ronzio grave
        Effetto.SBAGLIATO -> concatena(nota(196.0, 0.12, 6.0, quadra = true), nota(165.0, 0.18, 6.0, quadra = true))
        // Due colpi sordi: "è pesante"
        Effetto.PESANTE -> concatena(nota(130.8, 0.1, 25.0), silenzio(0.05), nota(130.8, 0.12, 20.0))
        // Arpeggio di Do maggiore
        Effetto.LIVELLO -> concatena(
            nota(523.3, 0.1, 12.0), nota(659.3, 0.1, 12.0), nota(784.0, 0.1, 12.0), nota(1046.5, 0.45, 5.0)
        )
    }

    private fun nota(freq: Double, durata: Double, smorzamento: Double, quadra: Boolean = false): FloatArray {
        val n = (durata * FREQ).toInt()
        return FloatArray(n) { i ->
            val t = i / FREQ.toDouble()
            val onda = sin(2 * PI * freq * t).let { if (quadra) if (it >= 0) 0.6 else -0.6 else it }
            // Attacco di 4 ms per evitare il "clic", poi decadimento esponenziale
            val inviluppo = (t / 0.004).coerceAtMost(1.0) * exp(-t * smorzamento)
            (onda * inviluppo * 0.8).toFloat()
        }
    }

    private fun glissato(da: Double, a: Double, durata: Double): FloatArray {
        val n = (durata * FREQ).toInt()
        var fase = 0.0
        return FloatArray(n) { i ->
            val t = i / FREQ.toDouble()
            val f = da + (a - da) * (i / n.toDouble())
            fase += 2 * PI * f / FREQ
            (sin(fase) * (t / 0.004).coerceAtMost(1.0) * exp(-t * 25) * 0.8).toFloat()
        }
    }

    private fun silenzio(durata: Double) = FloatArray((durata * FREQ).toInt())

    private fun concatena(vararg parti: FloatArray): FloatArray {
        val r = FloatArray(parti.sumOf { it.size })
        var k = 0
        for (p in parti) {
            p.copyInto(r, k)
            k += p.size
        }
        return r
    }

    /** WAV mono 16 bit. */
    private fun wav(dati: FloatArray): ByteArray {
        val byteDati = dati.size * 2
        val b = ByteBuffer.allocate(44 + byteDati).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + byteDati).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(FREQ).putInt(FREQ * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(byteDati)
        for (v in dati) b.putShort((v.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        return b.array()
    }

    private companion object {
        const val FREQ = 44_100
    }
}
