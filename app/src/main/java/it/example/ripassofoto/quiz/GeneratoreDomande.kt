package it.example.ripassofoto.quiz

import kotlin.random.Random

/**
 * Genera domande di verifica a partire dal testo di una pagina, senza alcun
 * servizio esterno: tutto avviene sul dispositivo con euristiche testuali.
 *
 * Idea di base:
 * 1. Il testo viene diviso in frasi "utili" (né troppo corte né troppo lunghe).
 * 2. Per ogni frase si individua una parola chiave (nome proprio, numero o
 *    parola lunga e non comune) su cui costruire la domanda.
 * 3. Le domande si alternano fra "completa la frase" (scelta multipla, con
 *    parole chiave prese da altre frasi come distrattori) e "vero o falso"
 *    (l'affermazione originale, oppure una versione alterata sostituendo la
 *    parola chiave con un'altra presa dal resto del testo).
 */
object GeneratoreDomande {

    private val PAROLE_VUOTE = setOf(
        "il", "lo", "la", "i", "gli", "le", "un", "uno", "una",
        "di", "a", "da", "in", "con", "su", "per", "tra", "fra",
        "e", "ed", "o", "che", "chi", "cui", "come", "dove", "quando",
        "perché", "poiché", "quindi", "però", "ma", "anche", "non", "più",
        "meno", "molto", "poco", "questo", "questa", "questi", "queste",
        "quello", "quella", "quelli", "quelle", "suo", "sua", "suoi", "sue",
        "loro", "nostro", "nostra", "vostro", "vostra", "ogni", "ogni",
        "qualche", "alcuni", "alcune", "tutto", "tutti", "tutta", "tutte",
        "essere", "sono", "è", "era", "erano", "sarà", "saranno", "hanno",
        "aveva", "avevano", "del", "dello", "della", "dei", "degli", "delle",
        "dal", "dallo", "dalla", "dai", "dagli", "dalle", "al", "allo",
        "alla", "ai", "agli", "alle", "nel", "nello", "nella", "nei",
        "negli", "nelle", "sul", "sullo", "sulla", "sui", "sugli", "sulle"
    )

    private const val LUNGHEZZA_MIN_FRASE = 25
    private const val LUNGHEZZA_MAX_FRASE = 220
    private const val PAROLE_MINIME_PER_FRASE = 5

    fun genera(testo: String, numeroDomandeMax: Int = 8, seed: Long = System.currentTimeMillis()): List<Domanda> {
        val frasi = estraiFrasi(testo)
        if (frasi.isEmpty()) return emptyList()

        val random = Random(seed)
        val paroleChiavePerFrase = frasi.indices.associateWith { i -> paroleChiaveDi(frasi[i]) }
        val indiciUtilizzabili = frasi.indices.filter { paroleChiavePerFrase[it] != null }
        if (indiciUtilizzabili.isEmpty()) return emptyList()

        val tutteLeParoleChiave = paroleChiavePerFrase.values.filterNotNull().distinct()
        val indiciScelti = indiciUtilizzabili.shuffled(random).take(numeroDomandeMax)

        return indiciScelti.mapIndexedNotNull { posizione, indiceFrase ->
            val frase = frasi[indiceFrase]
            val paroleChiave = paroleChiavePerFrase[indiceFrase]!!
            val vuoleVeroFalso = posizione % 2 == 1
            if (vuoleVeroFalso) {
                generaVeroFalso(posizione, frase, paroleChiave, tutteLeParoleChiave, random)
            } else {
                generaScelta(posizione, frase, paroleChiave, tutteLeParoleChiave, random)
                    ?: generaVeroFalso(posizione, frase, paroleChiave, tutteLeParoleChiave, random)
            }
        }
    }

    private fun estraiFrasi(testo: String): List<String> {
        val pulito = testo
            .replace(Regex("-\\s*\\n\\s*"), "") // riunisce parole spezzate a fine riga
            .replace(Regex("\\s*\\n\\s*"), " ")
            .replace(Regex(" {2,}"), " ")
            .trim()

        return pulito.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim().trim('"', '«', '»') }
            .filter { frase ->
                frase.length in LUNGHEZZA_MIN_FRASE..LUNGHEZZA_MAX_FRASE &&
                    frase.count { it == ' ' } + 1 >= PAROLE_MINIME_PER_FRASE
            }
    }

    /** Sceglie la parola più "informativa" della frase: la restituisce così com'è nel testo. */
    private fun paroleChiaveDi(frase: String): String? {
        val parole = Regex("[\\p{L}0-9'àèéìòù]+").findAll(frase).map { it.value }.toList()
        if (parole.isEmpty()) return null

        val candidate = parole.filterIndexed { indice, parola ->
            val minuscola = parola.lowercase()
            when {
                parola.all { it.isDigit() } -> parola.length in 2..4
                minuscola in PAROLE_VUOTE -> false
                parola.length >= 6 -> true
                indice > 0 && parola.first().isUpperCase() && parola.length >= 4 -> true
                else -> false
            }
        }
        return candidate.maxByOrNull { it.length }
    }

    private fun sostituisciParola(frase: String, parola: String, sostituto: String): String {
        val pattern = Regex("\\b${Regex.escape(parola)}\\b")
        return pattern.replaceFirst(frase, sostituto)
    }

    private fun generaScelta(
        id: Int,
        frase: String,
        paroleChiave: String,
        tutteLeParoleChiave: List<String>,
        random: Random
    ): DomandaScelta? {
        val stessoTipo = paroleChiave.all { it.isDigit() }
        val distrattori = tutteLeParoleChiave
            .filter { it != paroleChiave && it.all { c -> c.isDigit() } == stessoTipo }
            .shuffled(random)
            .take(3)

        if (distrattori.size < 2) return null

        val opzioni = (distrattori + paroleChiave).shuffled(random)
        val testoConSpazio = sostituisciParola(frase, paroleChiave, "_____")

        return DomandaScelta(
            id = id,
            testo = "Completa: $testoConSpazio",
            opzioni = opzioni,
            indiceCorretto = opzioni.indexOf(paroleChiave),
            fraseOrigine = frase
        )
    }

    private fun generaVeroFalso(
        id: Int,
        frase: String,
        paroleChiave: String,
        tutteLeParoleChiave: List<String>,
        random: Random
    ): DomandaVeroFalso {
        val vuoleAffermazioneVera = random.nextBoolean()
        if (vuoleAffermazioneVera) {
            return DomandaVeroFalso(id, frase, corretta = true, fraseOrigine = frase)
        }

        val stessoTipo = paroleChiave.all { it.isDigit() }
        val sostituto = tutteLeParoleChiave
            .filter { it != paroleChiave && it.all { c -> c.isDigit() } == stessoTipo }
            .randomOrNull(random)

        return if (sostituto != null) {
            DomandaVeroFalso(
                id = id,
                affermazione = sostituisciParola(frase, paroleChiave, sostituto),
                corretta = false,
                fraseOrigine = frase
            )
        } else {
            DomandaVeroFalso(id, frase, corretta = true, fraseOrigine = frase)
        }
    }
}
