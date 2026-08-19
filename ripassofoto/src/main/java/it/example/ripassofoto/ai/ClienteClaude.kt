package it.example.ripassofoto.ai

import it.example.ripassofoto.quiz.Domanda
import it.example.ripassofoto.quiz.DomandaScelta
import it.example.ripassofoto.quiz.DomandaVeroFalso
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Errore restituito da [ClienteClaude], con un messaggio già pronto per l'utente in italiano. */
class ErroreClaude(messaggio: String) : Exception(messaggio)

/**
 * Genera domande di verifica di alto livello chiamando la Claude API (Anthropic) con
 * output strutturato: il modello analizza il testo della pagina e restituisce
 * direttamente un elenco di domande in un formato JSON garantito, invece delle
 * euristiche testuali locali di [it.example.ripassofoto.quiz.GeneratoreDomande].
 *
 * Richiede una chiave API personale dell'utente (vedi [ChiaveApiStore]): la richiesta
 * parte direttamente dal telefono verso api.anthropic.com, senza alcun server intermedio.
 */
object ClienteClaude {

    private const val MODELLO = "claude-opus-5"
    private const val URL_MESSAGES = "https://api.anthropic.com/v1/messages"
    private const val VERSIONE_API = "2023-06-01"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val MEDIA_TYPE_JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun generaDomande(
        chiaveApi: String,
        testo: String,
        numeroDomande: Int = 6
    ): List<Domanda> = withContext(Dispatchers.IO) {
        val corpo = costruisciCorpoRichiesta(testo, numeroDomande)
        val richiesta = Request.Builder()
            .url(URL_MESSAGES)
            .addHeader("content-type", "application/json")
            .addHeader("x-api-key", chiaveApi)
            .addHeader("anthropic-version", VERSIONE_API)
            .post(corpo.toString().toRequestBody(MEDIA_TYPE_JSON))
            .build()

        val corpoRisposta = try {
            client.newCall(richiesta).execute().use { risposta ->
                val testoRisposta = risposta.body?.string().orEmpty()
                if (!risposta.isSuccessful) {
                    throw ErroreClaude(messaggioErrore(risposta.code, testoRisposta))
                }
                testoRisposta
            }
        } catch (e: ErroreClaude) {
            throw e
        } catch (e: IOException) {
            throw ErroreClaude("Impossibile contattare Claude: controlla la connessione a Internet.")
        }

        analizzaRisposta(corpoRisposta)
    }

    private fun costruisciCorpoRichiesta(testo: String, numeroDomande: Int): JSONObject {
        val sistema = """
            Sei un assistente didattico che aiuta uno studente delle scuole superiori italiane
            (liceo) a ripassare un capitolo di un libro di testo. Ricevi il testo di una pagina,
            estratto tramite riconoscimento ottico dei caratteri (OCR) da una foto: potrebbe
            contenere piccoli errori di trascrizione, che devi correggere implicitamente quando
            il significato è chiaro dal contesto.

            Genera domande di verifica impegnative e di alto livello, che verifichino la reale
            comprensione del testo — non il semplice richiamo mnemonico di singole parole o
            numeri isolati. Preferisci domande che richiedano di collegare concetti, individuare
            cause ed effetti, fare inferenze, valutare la plausibilità di un'affermazione o
            applicare quanto letto a un caso nuovo. Ogni risposta deve però restare verificabile
            esclusivamente dal testo fornito, senza richiedere conoscenze esterne all'argomento.

            Alterna domande a scelta multipla (4 opzioni plausibili e ben distinte, una sola
            corretta) e domande vero/falso (con affermazioni sottili, non banalmente vere o
            false a colpo d'occhio). Per ogni domanda fornisci anche una breve spiegazione della
            risposta corretta, con un riferimento al punto del testo che la giustifica.
        """.trimIndent()

        val utente = "Genera $numeroDomande domande di verifica su questo testo:\n\n$testo"

        val schemaDomanda = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("tipo", enumSchema("scelta_multipla", "vero_falso"))
                    .put("livello", enumSchema("comprensione", "applicazione", "analisi"))
                    .put("testo", JSONObject().put("type", "string"))
                    .put(
                        "opzioni",
                        JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))
                    )
                    .put("indice_corretto", JSONObject().put("type", "integer"))
                    .put("corretta", JSONObject().put("type", "boolean"))
                    .put("spiegazione", JSONObject().put("type", "string"))
            )
            .put(
                "required",
                JSONArray(listOf("tipo", "livello", "testo", "opzioni", "indice_corretto", "corretta", "spiegazione"))
            )
            .put("additionalProperties", false)

        val schema = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().put("domande", JSONObject().put("type", "array").put("items", schemaDomanda)))
            .put("required", JSONArray(listOf("domande")))
            .put("additionalProperties", false)

        return JSONObject()
            .put("model", MODELLO)
            .put("max_tokens", 8000)
            .put("system", sistema)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", utente))
            )
            .put(
                "output_config",
                JSONObject().put("format", JSONObject().put("type", "json_schema").put("schema", schema))
            )
    }

    private fun enumSchema(vararg valori: String): JSONObject =
        JSONObject().put("type", "string").put("enum", JSONArray(valori))

    private fun analizzaRisposta(corpo: String): List<Domanda> {
        val json = JSONObject(corpo)
        val bloccoTesto = json.getJSONArray("content")
            .let { blocchi -> (0 until blocchi.length()).map { blocchi.getJSONObject(it) } }
            .firstOrNull { it.optString("type") == "text" }
            ?: throw ErroreClaude("Claude non ha restituito un risultato utilizzabile.")

        val output = JSONObject(bloccoTesto.getString("text"))
        val domandeJson = output.getJSONArray("domande")

        return (0 until domandeJson.length()).mapNotNull { indice ->
            val d = domandeJson.getJSONObject(indice)
            val fraseOrigine = d.optString("testo")
            val spiegazione = d.optString("spiegazione").takeIf { it.isNotBlank() }

            when (d.optString("tipo")) {
                "vero_falso" -> DomandaVeroFalso(
                    id = indice,
                    affermazione = d.optString("testo"),
                    corretta = d.optBoolean("corretta"),
                    fraseOrigine = fraseOrigine,
                    spiegazione = spiegazione
                )
                else -> {
                    val opzioni = d.optJSONArray("opzioni")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                    val indiceCorretto = d.optInt("indice_corretto", -1)
                    if (opzioni.size < 2 || indiceCorretto !in opzioni.indices) {
                        null
                    } else {
                        DomandaScelta(
                            id = indice,
                            testo = d.optString("testo"),
                            opzioni = opzioni,
                            indiceCorretto = indiceCorretto,
                            fraseOrigine = fraseOrigine,
                            spiegazione = spiegazione
                        )
                    }
                }
            }
        }
    }

    private fun messaggioErrore(codice: Int, corpo: String): String {
        val messaggioApi = runCatching {
            JSONObject(corpo).getJSONObject("error").getString("message")
        }.getOrNull()

        return when (codice) {
            401 -> "La chiave API di Claude non è valida. Controllala nelle impostazioni."
            429 -> "Limite di richieste a Claude raggiunto: riprova tra qualche istante."
            in 500..599 -> "Il servizio di Claude è temporaneamente non disponibile."
            else -> messaggioApi ?: "Errore nella richiesta a Claude (codice $codice)."
        }
    }
}
