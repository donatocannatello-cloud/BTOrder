package it.example.theremin.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/** Stato della base musicale, mostrato dalla UI. */
data class StatoBase(
    val titolo: String? = null,
    val inRiproduzione: Boolean = false,
    val caricamento: Boolean = false,
    val errore: String? = null,
)

/**
 * Base musicale di sottofondo su cui suonare il theremin: un file audio scelto dal telefono
 * (es. la propria copia di "Romeo and Juliet") oppure una radio / uno stream via internet.
 *
 * Usa un [MediaPlayer] separato dal synth, con un volume indipendente; l'ultima base caricata
 * viene ricordata e ricaricata (in pausa) all'avvio successivo.
 */
class BaseMusicale(private val context: Context, private val suStato: (StatoBase) -> Unit) {

    private val prefs = context.getSharedPreferences("base_musicale", Context.MODE_PRIVATE)
    private var player: MediaPlayer? = null
    private var pronto = false
    private var volume = 0.6f
    private var ripeti = true
    private var riprendiAlRitorno = false
    private var stato = StatoBase()
        set(value) {
            field = value
            suStato(value)
        }

    /** Ricarica l'ultima base usata, senza farla partire. */
    fun ripristina() {
        val uri = prefs.getString("uri", null) ?: return
        carica(Uri.parse(uri), prefs.getString("titolo", null) ?: uri, avvia = false)
    }

    fun carica(uri: Uri, titolo: String, avvia: Boolean = true) {
        rilascia()
        prefs.edit().putString("uri", uri.toString()).putString("titolo", titolo).apply()
        stato = StatoBase(titolo = titolo, caricamento = true)
        val streaming = uri.scheme == "http" || uri.scheme == "https"
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(context, uri)
            mp.setOnPreparedListener {
                if (player !== mp) return@setOnPreparedListener
                pronto = true
                applicaVolume()
                it.isLooping = ripeti && !streaming
                if (avvia) it.start()
                stato = stato.copy(caricamento = false, inRiproduzione = avvia)
            }
            mp.setOnCompletionListener {
                if (player === mp) stato = stato.copy(inRiproduzione = false)
            }
            mp.setOnErrorListener { _, cosa, extra ->
                if (player === mp) {
                    pronto = false
                    stato = stato.copy(caricamento = false, inRiproduzione = false, errore = "Impossibile riprodurre ($cosa/$extra)")
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            stato = stato.copy(caricamento = false, errore = "Impossibile aprire: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun alterna() {
        val mp = player ?: return
        if (!pronto) return
        if (mp.isPlaying) mp.pause() else mp.start()
        stato = stato.copy(inRiproduzione = mp.isPlaying)
    }

    /** Volume 0..1 della sola base (curva quadratica, più naturale all'orecchio). */
    fun impostaVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        applicaVolume()
    }

    fun impostaRipeti(r: Boolean) {
        ripeti = r
        if (pronto) player?.isLooping = r
    }

    /** Da chiamare in onStop: mette in pausa ricordando se stava suonando. */
    fun sospendi() {
        val mp = player ?: return
        riprendiAlRitorno = pronto && mp.isPlaying
        if (riprendiAlRitorno) {
            mp.pause()
            stato = stato.copy(inRiproduzione = false)
        }
    }

    /** Da chiamare in onStart: riparte se era stata sospesa mentre suonava. */
    fun riprendi() {
        if (riprendiAlRitorno && pronto) {
            player?.start()
            stato = stato.copy(inRiproduzione = true)
        }
        riprendiAlRitorno = false
    }

    fun rilascia() {
        player?.release()
        player = null
        pronto = false
    }

    private fun applicaVolume() {
        if (!pronto) return
        val v = volume * volume
        player?.setVolume(v, v)
    }
}
