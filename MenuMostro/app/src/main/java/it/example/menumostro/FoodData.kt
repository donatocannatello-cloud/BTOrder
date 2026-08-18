package it.example.menumostro

/** Le portate del gioco, nell'ordine in cui vengono sbloccate salendo di livello. */
enum class Portata(val etichetta: String, val emoji: String) {
    PRIMO("Primo", "🍝"),
    SECONDO("Secondo", "🍗"),
    BIBITA("Bibita", "🥤"),
    POZIONE("Pozione Magica", "🧪"),
    DOLCE("Dolce", "🍰"),
    CAFFE("Caffè", "☕")
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

/** Menù del secondo: mix di piatti normali e piatti folli. */
val menuSecondi: List<Piatto> = listOf(
    Piatto("Pollo Arrosto", "🍗", schifezza = false, colore = "giallo", categorie = setOf("carne")),
    Piatto("Pesce al Forno", "🐟", schifezza = false, colore = "arancione", categorie = setOf("pesce")),
    Piatto("Bistecca alla Griglia", "🥩", schifezza = false, colore = "rosso", categorie = setOf("carne")),
    Piatto("Frittata di Formaggio", "🍳", schifezza = false, colore = "giallo", categorie = setOf("formaggio")),
    Piatto("Insalata di Verdure", "🥗", schifezza = false, colore = "verde", categorie = setOf("insalata")),
    Piatto("Zampa di Drago", "🐉", schifezza = true, colore = "rosso", categorie = setOf("carne", "piccante")),
    Piatto("Tentacolo di Piovra", "🐙", schifezza = true, colore = "viola", categorie = setOf("pesce")),
    Piatto("Bistecca di Alieno", "👽", schifezza = true, colore = "verde", categorie = setOf("carne"))
)

/**
 * Menù delle bibite: solo bevande analcoliche adatte ai bambini (acqua, succhi,
 * bibite gassate) più le versioni folli del Mostro.
 */
val menuBibite: List<Piatto> = listOf(
    Piatto("Acqua Fresca", "💧", schifezza = false, colore = "azzurro"),
    Piatto("Succo di Frutta", "🧃", schifezza = false, colore = "arancione", categorie = setOf("frutta")),
    Piatto("Aranciata", "🍊", schifezza = false, colore = "arancione", categorie = setOf("frutta")),
    Piatto("Cola Frizzante", "🥤", schifezza = false, colore = "marrone"),
    Piatto("Melma da Bere", "🥤", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Pozione Fumante", "🧪", schifezza = true, colore = "viola"),
    Piatto("Succo di Vermi", "🪱", schifezza = true, colore = "rosso"),
    Piatto("Bava di Lumaca Frizzante", "🐌", schifezza = true, colore = "verde", categorie = setOf("viscido"))
)

/**
 * Menù delle pozioni magiche: al posto degli amari (alcolici, non adatti a un gioco
 * per bambini) i mostri bevono pozioni fantastiche dopo il pasto.
 */
val menuPozioni: List<Piatto> = listOf(
    Piatto("Pozione della Forza", "💪", schifezza = false, colore = "rosso"),
    Piatto("Pozione Arcobaleno", "🌈", schifezza = false, colore = "viola"),
    Piatto("Elisir di Luna", "🌙", schifezza = false, colore = "giallo"),
    Piatto("Pozione Scintillante", "✨", schifezza = false, colore = "giallo"),
    Piatto("Pozione Puzzolente", "🤢", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Bava di Mostro in Bottiglia", "🧟", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Pozione di Fango", "🟤", schifezza = true, colore = "marrone"),
    Piatto("Succo di Ragnatela", "🕸️", schifezza = true, colore = "grigio")
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

/** Menù del caffè: solo caffè in tutte le sue varianti, nessun alcolico. */
val menuCaffe: List<Piatto> = listOf(
    Piatto("Caffè Liscio", "☕", schifezza = false, colore = "marrone"),
    Piatto("Caffè Macchiato", "☕", schifezza = false, colore = "marrone"),
    Piatto("Cappuccino", "☕", schifezza = false, colore = "bianco"),
    Piatto("Caffè Decaffeinato", "☕", schifezza = false, colore = "marrone"),
    Piatto("Caffè di Vermi", "☕", schifezza = true, colore = "marrone"),
    Piatto("Caffè al Fango", "☕", schifezza = true, colore = "marrone"),
    Piatto("Caffè Puzzolente", "☕", schifezza = true, colore = "verde"),
    Piatto("Caffè Occhio Ghiacciato", "☕", schifezza = true, colore = "giallo")
)

fun menuPer(portata: Portata): List<Piatto> = when (portata) {
    Portata.PRIMO -> menuPrimi
    Portata.SECONDO -> menuSecondi
    Portata.BIBITA -> menuBibite
    Portata.POZIONE -> menuPozioni
    Portata.DOLCE -> menuDolci
    Portata.CAFFE -> menuCaffe
}

/** Ordine in cui le portate si sbloccano salendo di livello. */
val ordinePortate: List<Portata> = Portata.entries.toList()

/** Un livello di difficoltà: quante e quali portate bisogna comporre per ogni commensale. */
data class Livello(val numero: Int, val portate: List<Portata>)

/** Livello 1 = solo il primo, livello 2 = primo + secondo, ... fino a tutte le portate. */
val elencoLivelli: List<Livello> = ordinePortate.indices.map { indice ->
    Livello(numero = indice + 1, portate = ordinePortate.take(indice + 1))
}

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

/**
 * Le richieste possibili: ad ogni manche se ne estraggono 4 diverse, una per commensale.
 * Sono generiche (valutano colore/categorie di una lista di [Piatto]) e vengono usate sia
 * da Monster Restaurant sia da Monster Panino.
 */
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

/**
 * Ingredienti di Monster Panino: un unico pool (niente portate separate) da cui si
 * scelgono via via più ingredienti salendo di livello, mescolando ingredienti normali
 * e ingredienti folli del Mostro. Riusa [Piatto] e le stesse [elencoRichieste] di
 * Monster Restaurant.
 */
val elencoIngredientiPanino: List<Piatto> = listOf(
    Piatto("Pane Morbido", "🍞", schifezza = false, colore = "giallo"),
    Piatto("Pomodoro", "🍅", schifezza = false, colore = "rosso"),
    Piatto("Formaggio", "🧀", schifezza = false, colore = "giallo", categorie = setOf("formaggio")),
    Piatto("Prosciutto", "🍖", schifezza = false, colore = "rosa", categorie = setOf("carne")),
    Piatto("Lattuga", "🥬", schifezza = false, colore = "verde", categorie = setOf("insalata")),
    Piatto("Tonno", "🐟", schifezza = false, colore = "grigio", categorie = setOf("pesce")),
    Piatto("Uovo Sodo", "🥚", schifezza = false, colore = "giallo"),
    Piatto("Salame Piccante", "🌭", schifezza = false, colore = "rosso", categorie = setOf("carne", "piccante")),
    Piatto("Uva", "🍇", schifezza = false, colore = "viola", categorie = setOf("frutta")),
    Piatto("Vermi Croccanti", "🪱", schifezza = true, colore = "marrone"),
    Piatto("Occhio di Ciclope", "👁️", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Melma Verde", "🫙", schifezza = true, colore = "verde", categorie = setOf("viscido")),
    Piatto("Ragnetti Piccanti", "🕷️", schifezza = true, colore = "marrone", categorie = setOf("piccante")),
    Piatto("Formaggio Puzzolente", "🧀", schifezza = true, colore = "verde", categorie = setOf("formaggio")),
    Piatto("Bava di Lumaca", "🐌", schifezza = true, colore = "marrone", categorie = setOf("viscido")),
    Piatto("Fango Croccante", "🟤", schifezza = true, colore = "marrone"),
    Piatto("Formica Gigante", "🐜", schifezza = true, colore = "marrone")
)

/** Un livello di difficoltà per Monster Panino: quanti ingredienti bisogna scegliere. */
data class LivelloPanino(val numero: Int, val numeroIngredienti: Int)

/** Livello 1 = panino con 2 ingredienti, via via più farcito fino a 6 ingredienti. */
val elencoLivelliPanino: List<LivelloPanino> = listOf(2, 3, 4, 5, 6).mapIndexed { indice, numeroIngredienti ->
    LivelloPanino(numero = indice + 1, numeroIngredienti = numeroIngredienti)
}
