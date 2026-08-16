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
    Piatto("Bruschetta al Pomodoro", "🍅", schifezza = false, colore = "rosso"),
    Piatto("Insalata Verde Croccante", "🥗", schifezza = false, colore = "verde", categorie = setOf("insalata")),
    Piatto("Prosciutto e Melone", "🍈", schifezza = false, colore = "arancione", categorie = setOf("carne", "frutta")),
    Piatto("Tagliere di Formaggi", "🧀", schifezza = false, colore = "giallo", categorie = setOf("formaggio")),
    Piatto("Bastoncini di Pesce Fritti", "🐟", schifezza = false, colore = "giallo", categorie = setOf("pesce")),
    Piatto("Vermi Rossi in Salamoia", "🪱", schifezza = true, colore = "rosso"),
    Piatto("Occhi di Rospo in Gelatina Verde", "👁️", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Ragnetti Fritti Piccanti", "🕷️", schifezza = true, colore = "marrone", categorie = setOf("piccante"))
)

/** Menù del primo: mix di piatti normali e piatti folli. */
val menuPrimi: List<Piatto> = listOf(
    Piatto("Spaghetti al Pomodoro", "🍝", schifezza = false, colore = "rosso"),
    Piatto("Insalata di Riso Fredda", "🍚", schifezza = false, colore = "verde", categorie = setOf("insalata")),
    Piatto("Pollo alla Griglia", "🍗", schifezza = false, colore = "giallo", categorie = setOf("carne")),
    Piatto("Filetto di Salmone al Limone", "🐟", schifezza = false, colore = "arancione", categorie = setOf("pesce")),
    Piatto("Spaghetti coi Vermi Veri", "🪱", schifezza = true, colore = "marrone"),
    Piatto("Zuppa di Fango Fumante", "🍲", schifezza = true, colore = "marrone"),
    Piatto("Purè di Melma Verde", "🥣", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Riso al Veleno Fluorescente", "🍚", schifezza = true, colore = "giallo", categorie = setOf("piccante"))
)

/** Menù del dolce: mix di piatti normali e piatti folli. */
val menuDolci: List<Piatto> = listOf(
    Piatto("Macedonia di Frutta Fresca", "🍇", schifezza = false, colore = "viola", categorie = setOf("frutta")),
    Piatto("Gelato alla Fragola", "🍨", schifezza = false, colore = "rosso"),
    Piatto("Torta al Cioccolato", "🍫", schifezza = false, colore = "marrone"),
    Piatto("Formaggio con Miele", "🧀", schifezza = false, colore = "giallo", categorie = setOf("formaggio")),
    Piatto("Budino di Melma Verde", "🍮", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Gelato ai Vermi Gommosi", "🍦", schifezza = true, colore = "viola"),
    Piatto("Occhi di Gelatina Gialla", "👁️", schifezza = true, colore = "giallo"),
    Piatto("Gelato al Prosciutto Piccante", "🍦", schifezza = true, colore = "rosa", categorie = setOf("carne", "piccante"))
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
    RichiestaMostro("Oggi voglio solo CIBI ROSSI! 🔴", "🔴") { piatti -> frazionePunti(piatti) { it.colore == "rosso" } },
    RichiestaMostro("Sono VEGETARIANO, niente carne o pesce! 🥦", "🥦") { piatti -> frazionePunti(piatti) { it.vegetariano } },
    RichiestaMostro("Sono CARNIVORO, voglio tanta carne! 🍖", "🍖") { piatti -> frazionePunti(piatti) { "carne" in it.categorie } },
    RichiestaMostro("Mi piacciono le INSALATE fresche! 🥗", "🥗") { piatti -> frazionePunti(piatti) { "insalata" in it.categorie } },
    RichiestaMostro("Voglio solo cose VERDI! 🟢", "🟢") { piatti -> frazionePunti(piatti) { it.colore == "verde" } },
    RichiestaMostro("Adoro il FORMAGGIO, mettetene ovunque! 🧀", "🧀") { piatti -> frazionePunti(piatti) { "formaggio" in it.categorie } },
    RichiestaMostro("Datemi qualcosa di PICCANTE che scotta! 🌶️", "🌶️") { piatti -> frazionePunti(piatti) { "piccante" in it.categorie } },
    RichiestaMostro("Adoro la FRUTTA fresca! 🍇", "🍇") { piatti -> frazionePunti(piatti) { "frutta" in it.categorie } },
    RichiestaMostro("Voglio cose GIALLE, il mio colore preferito! 🟡", "🟡") { piatti -> frazionePunti(piatti) { it.colore == "giallo" } },
    RichiestaMostro("Adoro tutto ciò che è VISCIDO e BAVOSO! 🐌", "🐌") { piatti -> frazionePunti(piatti) { "viscido" in it.categorie } }
)
