package it.example.theremin.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Una radio online trovata nel catalogo. */
data class StazioneRadio(
    val nome: String,
    val url: String,
    val paese: String,
    val codec: String,
    val bitrate: Int,
)

/**
 * Ricerca di radio online nel catalogo pubblico e gratuito Radio Browser (radio-browser.info).
 *
 * L'indirizzo dello stream non è scritto nell'app ma chiesto al catalogo ogni volta, così resta
 * valido anche se la radio cambia server. Si provano più server del catalogo, nel caso uno non risponda.
 */
object RadioBrowser {

    /** La radio preferita, proposta con un pulsante dedicato. */
    const val ROMEO_AND_JULIET = "Romeo and Juliet"

    private val SERVER = listOf("de1", "nl1", "at1", "fi1", "de2").map { "https://$it.api.radio-browser.info" }

    suspend fun cerca(nome: String): List<StazioneRadio> = withContext(Dispatchers.IO) {
        val query = "name=" + URLEncoder.encode(nome.trim(), "UTF-8") +
            "&hidebroken=true&order=clickcount&reverse=true&limit=30"
        var ultimoErrore: Exception? = null
        for (server in SERVER) {
            try {
                return@withContext leggi(URL("$server/json/stations/search?$query"))
            } catch (e: Exception) {
                ultimoErrore = e
            }
        }
        throw ultimoErrore ?: IllegalStateException("Catalogo radio non raggiungibile")
    }

    /**
     * Sceglie tra i risultati la stazione che corrisponde meglio al nome cercato:
     * prima un nome identico, poi uno che contiene tutte le parole, poi la più ascoltata.
     */
    fun migliore(risultati: List<StazioneRadio>, nome: String): StazioneRadio? {
        val cercato = normalizza(nome)
        val parole = cercato.split(' ').filter { it.isNotBlank() }
        return risultati.firstOrNull { normalizza(it.nome) == cercato || normalizza(it.nome) == "radio $cercato" }
            ?: risultati.firstOrNull { s -> parole.all { normalizza(s.nome).contains(it) } }
            ?: risultati.firstOrNull()
    }

    private fun normalizza(s: String) =
        s.lowercase().replace("&", "and").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun leggi(url: URL): List<StazioneRadio> {
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            // Il catalogo chiede un User-Agent che identifichi l'app
            conn.setRequestProperty("User-Agent", "ThereminCromatico/1.0 (Android)")
            val testo = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(testo)
            return (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val stream = o.optString("url_resolved").ifBlank { o.optString("url") }
                if (stream.isBlank()) null
                else StazioneRadio(
                    nome = o.optString("name").trim(),
                    url = stream,
                    paese = o.optString("countrycode"),
                    codec = o.optString("codec"),
                    bitrate = o.optInt("bitrate"),
                )
            }
        } finally {
            conn.disconnect()
        }
    }
}
