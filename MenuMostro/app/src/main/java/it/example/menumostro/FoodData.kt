package it.example.menumostro

/** Le tre portate che compongono il pasto da servire ad ogni commensale. */
enum class Portata(val etichetta: String, val emoji: String) {
    ANTIPASTO("Antipasto", "🥗"),
    PRIMO("Primo", "🍝"),
    DOLCE("Dolce", "🍰")
}

/** Un piatto selezionabile in una portata: può essere un piatto normale oppure una "schifezza" mostruosa. */
data class Piatto(
    val nome: String,
    val emoji: String,
    val schifezza: Boolean,
    val colore: String,
    val categorie: Set<String> = emptySet()
)

/** Vero se il piatto non contiene né carne né pesce (usato dalla richiesta "sono vegetariano"). */
val Piatto.vegetariano: Boolean get() = "carne" !in categorie && "pesce" !in categorie

/** Menù dell'antipasto: mix di piatti normali e piatti folli. */
val menuAntipasti: List<Piatto> = listOf(
    Piatto("Pane e Pomodoro", "🍅", schifezza = false, colore = "rosso"),
    Piatto("Insalata Verde", "🥗", schifezza = false, colore = "verde", categorie = setOf("insalata")),
    Piatto("Prosciutto e Melone", "🍈", schifezza = false, colore = "arancione", categorie = setOf("carne", "frutta")),
    Piatto("Formaggio", "🧀", schifezza = false, colore = "giallo", categorie = setOf("formaggio")),
    Piatto("Bastoncini di Pesce", "🐟", schifezza = false, colore = "giallo", categorie = setOf("pesce")),
    Piatto("Vermi Rossi", "🪱", schifezza = true, colore = "rosso"),
    Piatto("Occhi di Rospo", "👁️", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Ragni Piccanti", "🕷️", schifezza = true, colore = "marrone", categorie = setOf("piccante"))
)

/** Menù del primo: mix di piatti normali e piatti folli. */
val menuPrimi: List<Piatto> = listOf(
    Piatto("Spaghetti al Pomodoro", "🍝", schifezza = false, colore = "rosso"),
    Piatto("Insalata di Riso", "🍚", schifezza = false, colore = "verde", categorie = setOf("insalata")),
    Piatto("Pollo Grigliato", "🍗", schifezza = false, colore = "giallo", categorie = setOf("carne")),
    Piatto("Salmone al Limone", "🐟", schifezza = false, colore = "arancione", categorie = setOf("pesce")),
    Piatto("Spaghetti di Vermi", "🪱", schifezza = true, colore = "marrone"),
    Piatto("Zuppa di Fango", "🍲", schifezza = true, colore = "marrone"),
    Piatto("Purè di Melma", "🥣", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Riso Velenoso", "🍚", schifezza = true, colore = "giallo", categorie = setOf("piccante"))
)

/** Menù del dolce: mix di piatti normali e piatti folli. */
val menuDolci: List<Piatto> = listOf(
    Piatto("Macedonia di Frutta", "🍇", schifezza = false, colore = "viola", categorie = setOf("frutta")),
    Piatto("Gelato alla Fragola", "🍨", schifezza = false, colore = "rosso"),
    Piatto("Torta al Cioccolato", "🍫", schifezza = false, colore = "marrone"),
    Piatto("Formaggio e Miele", "🧀", schifezza = false, colore = "giallo", categorie = setOf("formaggio")),
    Piatto("Budino di Melma", "🍮", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Gelato di Vermi", "🍦", schifezza = true, colore = "viola"),
    Piatto("Occhi Gialli", "👁️", schifezza = true, colore = "giallo"),
    Piatto("Gelato e Prosciutto", "🍦", schifezza = true, colore = "rosa", categorie = setOf("carne", "piccante"))
)

/** Un commensale mostruoso da servire: ogni manche ne porta uno diverso, con la sua richiesta. */
data class Commensale(val nome: String, val emoji: String)

val elencoCommensali: List<Commensale> = listOf(
    Commensale("Mostro", "👹"),
    Commensale("Mostra", "👺"),
    Commensale("Mostrina", "👻"),
    Commensale("Mostretta", "🧌")
)

/** Una richiesta di un commensale: descrive cosa vuole e come si valuta il pasto servito (0-100). */
data class RichiestaMostro(
    val frase: String,
    val emoji: String,
    val valuta: (List<Piatto>) -> Int
)

/** Le richieste possibili: ad ogni manche se ne estraggono 4 diverse, una per commensale. */
val elencoRichieste: List<RichiestaMostro> = listOf(
    RichiestaMostro("Voglio solo cibi rossi! 🔴", "🔴") { piatti -> frazionePunti(piatti) { it.colore == "rosso" } },
    RichiestaMostro("Niente carne e niente pesce! 🥦", "🥦") { piatti -> frazionePunti(piatti) { it.vegetariano } },
    RichiestaMostro("Voglio tanta carne! 🍖", "🍖") { piatti -> frazionePunti(piatti) { "carne" in it.categorie } },
    RichiestaMostro("Mi piace l'insalata! 🥗", "🥗") { piatti -> frazionePunti(piatti) { "insalata" in it.categorie } },
    RichiestaMostro("Voglio solo cose verdi! 🟢", "🟢") { piatti -> frazionePunti(piatti) { it.colore == "verde" } },
    RichiestaMostro("Voglio tanto formaggio! 🧀", "🧀") { piatti -> frazionePunti(piatti) { "formaggio" in it.categorie } },
    RichiestaMostro("Voglio cibo piccante! 🌶️", "🌶️") { piatti -> frazionePunti(piatti) { "piccante" in it.categorie } },
    RichiestaMostro("Voglio tanta frutta! 🍇", "🍇") { piatti -> frazionePunti(piatti) { "frutta" in it.categorie } },
    RichiestaMostro("Voglio solo cose gialle! 🟡", "🟡") { piatti -> frazionePunti(piatti) { it.colore == "giallo" } },
    RichiestaMostro("Voglio cose bavose! 🐌", "🐌") { piatti -> frazionePunti(piatti) { "viscido" in it.categorie } }
)
