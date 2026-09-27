package it.example.theremin.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Ricerca di radio online nel catalogo pubblico e gratuito Radio Browser (radio-browser.info).
 *
 * L'indirizzo dello stream non è scritto nell'app ma chiesto al catalogo ogni volta, così resta
 * valido anche se la radio cambia server. Si provano più server del catalogo, nel caso uno non risponda.
 */
object RadioBrowser {

    private val SERVER = listOf("de1", "nl1", "at1", "fi1", "de2").map { "https://$it.api.radio-browser.info" }

    suspend fun cerca(nome: String): List<StazioneRadio> = withContext(Dispatchers.IO) {
        val query = "name=" + URLEncoder.encode(nome.trim(), "UTF-8") +
            "&hidebroken=true&order=clickcount&reverse=true&limit=60"
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
                    homepage = o.optString("homepage"),
                )
            }
        } finally {
            conn.disconnect()
        }
    }
}
