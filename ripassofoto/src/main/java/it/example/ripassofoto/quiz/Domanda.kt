package it.example.ripassofoto.quiz

/** Una domanda di verifica generata a partire dal testo studiato. */
sealed interface Domanda {
    val id: Int
    val fraseOrigine: String

    /**
     * Breve spiegazione della risposta corretta, mostrata allo studente dopo che ha
     * risposto. Presente solo per le domande generate con l'IA; `null` per quelle
     * generate localmente con le euristiche testuali.
     */
    val spiegazione: String?
}

/**
 * Domanda a scelta multipla. [testo] può essere una frase con uno spazio da
 * completare (es. "La capitale ____ è Roma.") oppure una domanda diretta.
 */
data class DomandaScelta(
    override val id: Int,
    val testo: String,
    val opzioni: List<String>,
    val indiceCorretto: Int,
    override val fraseOrigine: String,
    override val spiegazione: String? = null
) : Domanda

/** Domanda vero/falso su un'affermazione tratta (o leggermente alterata) dal testo. */
data class DomandaVeroFalso(
    override val id: Int,
    val affermazione: String,
    val corretta: Boolean,
    override val fraseOrigine: String,
    override val spiegazione: String? = null
) : Domanda
