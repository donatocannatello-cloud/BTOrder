package it.example.menumostro

/** Un piatto che il giocatore può usare per comporre il menù del Mostro. */
data class Piatto(
    val nome: String,
    val emoji: String,
    val categorie: Set<String>
)

/**
 * Piatti disponibili nel banco degli ingredienti: metà "normali" e metà
 * "folli", pensati per essere buffi ma mai spaventosi per bambini di 6/7 anni.
 */
val elencoPiatti: List<Piatto> = listOf(
    // Piatti normali
    Piatto("Pizza Margherita", "🍕", setOf("salato", "caldo", "normale")),
    Piatto("Gelato al Cioccolato", "🍦", setOf("dolce", "freddo", "normale")),
    Piatto("Mela Rossa", "🍎", setOf("dolce", "croccante", "normale")),
    Piatto("Pollo Arrosto", "🍗", setOf("salato", "caldo", "croccante", "normale")),
    Piatto("Insalata Verde", "🥗", setOf("verde", "freddo", "normale")),
    Piatto("Popcorn Salati", "🍿", setOf("salato", "croccante", "normale")),
    Piatto("Torta di Compleanno", "🎂", setOf("dolce", "normale")),
    Piatto("Banana", "🍌", setOf("dolce", "normale")),

    // Piatti folli del Mostro
    Piatto("Vermi Gommosi al Cioccolato", "🪱", setOf("dolce", "pazzo")),
    Piatto("Occhio di Ciclope Gelatinoso", "👁️", setOf("freddo", "pazzo", "verde")),
    Piatto("Melma Verde Frizzante", "🫙", setOf("verde", "freddo", "pazzo")),
    Piatto("Ragnetti Croccanti allo Zucchero", "🕷️", setOf("croccante", "dolce", "pazzo")),
    Piatto("Zampa di Drago Piccantissima", "🐉", setOf("piccante", "caldo", "pazzo")),
    Piatto("Bava di Lumaca Viola", "🐌", setOf("viola", "freddo", "pazzo")),
    Piatto("Alga Puzzolente del Pantano", "🌿", setOf("verde", "pazzo", "salato")),
    Piatto("Sorpresa Misteriosa del Mostro", "🎲", setOf("pazzo"))
)

/** Una richiesta del Mostro: cosa desidera mangiare in quel turno. */
data class RichiestaMostro(
    val frase: String,
    val categorieDesiderate: Set<String>
)

/** Le richieste possibili, scelte a caso ad ogni cliente per variare i gusti del Mostro. */
val elencoRichieste: List<RichiestaMostro> = listOf(
    RichiestaMostro("Oggi ho una fame... DOLCE e un po' PAZZA! 🍬", setOf("dolce", "pazzo")),
    RichiestaMostro("Voglio qualcosa di CROCCANTE e SALATO! 😋", setOf("croccante", "salato")),
    RichiestaMostro("Ho voglia di cibo VERDE e FREDDO! 🥶", setOf("verde", "freddo")),
    RichiestaMostro("Datemi qualcosa di PICCANTE, sono un mostro coraggioso! 🌶️", setOf("piccante")),
    RichiestaMostro("Oggi voglio solo cose NORMALI, sono stanco di stranezze! 😌", setOf("normale")),
    RichiestaMostro("Sorprendetemi con qualcosa di STRANISSIMO! 🤪", setOf("pazzo")),
    RichiestaMostro("Mi piace tutto ciò che è VIOLA e FREDDO! 💜", setOf("viola", "freddo")),
    RichiestaMostro("Ho voglia di qualcosa di CALDO e SALATO! 🔥", setOf("caldo", "salato"))
)
