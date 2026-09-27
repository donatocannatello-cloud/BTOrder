package it.example.theremin.audio

/** Una radio online trovata nel catalogo. */
data class StazioneRadio(
    val nome: String,
    val url: String,
    val paese: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val homepage: String = "",
)

/** Criteri per scegliere la stazione giusta tra i risultati del catalogo (indipendente da Android). */
object SceltaRadio {

    /** Parola da cercare nel catalogo per trovare Radio Romeo and Juliet (anche se scritta "Romeo & Juliet"). */
    const val RICERCA_ROMEO_AND_JULIET = "romeo"

    /**
     * Radio Romeo and Juliet, web radio di Verona (radioromeoandjuliet.com).
     * Da non confondere con "RTL 102.5 Romeo & Juliet", un'altra web radio ormai chiusa: le stazioni
     * RTL vengono escluse. Preferita quella con il sito ufficiale, poi quella con il nome giusto.
     */
    fun romeoAndJuliet(risultati: List<StazioneRadio>): StazioneRadio? {
        val candidate = risultati.filterNot { normalizza(it.nome).contains("rtl") || it.homepage.contains("rtl", ignoreCase = true) }
        return candidate.firstOrNull { it.homepage.contains("radioromeoandjuliet", ignoreCase = true) }
            ?: candidate.firstOrNull { normalizza(it.nome) == "radio romeo and juliet" }
            ?: candidate.firstOrNull { normalizza(it.nome).let { n -> n.contains("romeo") && n.contains("juliet") } }
    }

    /**
     * Per una ricerca libera: prima un nome identico, poi uno che contiene tutte le parole,
     * poi il risultato più ascoltato.
     */
    fun migliore(risultati: List<StazioneRadio>, nome: String): StazioneRadio? {
        val cercato = normalizza(nome)
        val parole = cercato.split(' ').filter { it.isNotBlank() }
        return risultati.firstOrNull { normalizza(it.nome) == cercato || normalizza(it.nome) == "radio $cercato" }
            ?: risultati.firstOrNull { s -> parole.all { normalizza(s.nome).contains(it) } }
            ?: risultati.firstOrNull()
    }

    fun normalizza(s: String) =
        s.lowercase().replace("&", " and ").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}
