package it.freebimbogames.app

/** Le categorie di accessori di un vestito, nell'ordine in cui si sbloccano salendo di livello. */
enum class TipoAccessorio(val etichetta: String, val emoji: String) {
    CAPPELLO("Cappello", "🎩"),
    OCCHI("Occhiali", "🕶️"),
    VESTITO("Vestito", "👕"),
    SCARPE("Scarpe", "👟"),
    OGGETTO("Oggetto Magico", "🪄")
}

/** Banco cappelli: riusa Piatto (nome/emoji/colore/categorie) fuori dal contesto cibo. */
val elencoCappelli: List<Piatto> = listOf(
    Piatto("Cappello a Cilindro", "🎩", schifezza = false, colore = "nero"),
    Piatto("Corona Dorata", "👑", schifezza = false, colore = "giallo", categorie = setOf("regale")),
    Piatto("Bandana Pirata", "🏴", schifezza = false, colore = "nero", categorie = setOf("pirata")),
    Piatto("Cappello da Strega", "🧙", schifezza = true, colore = "viola", categorie = setOf("magico")),
    Piatto("Casco Spaziale", "🪖", schifezza = false, colore = "grigio", categorie = setOf("spazio")),
    Piatto("Ciuffo di Piume", "🪶", schifezza = true, colore = "rosso")
)

val elencoOcchiali: List<Piatto> = listOf(
    Piatto("Occhiali da Sole", "🕶️", schifezza = false, colore = "nero"),
    Piatto("Maschera da Supereroe", "🦸", schifezza = false, colore = "rosso", categorie = setOf("supereroe")),
    Piatto("Benda da Pirata", "🩹", schifezza = false, colore = "nero", categorie = setOf("pirata")),
    Piatto("Occhio Ciclopico", "👁️", schifezza = true, colore = "verde", categorie = setOf("magico")),
    Piatto("Occhiali a Stelline", "🤩", schifezza = false, colore = "giallo"),
    Piatto("Casco da Astronauta", "👨‍🚀", schifezza = false, colore = "grigio", categorie = setOf("spazio"))
)

val elencoVestiti: List<Piatto> = listOf(
    Piatto("Mantello Rosso", "🦸", schifezza = false, colore = "rosso", categorie = setOf("supereroe")),
    Piatto("Vestito da Re", "👑", schifezza = false, colore = "giallo", categorie = setOf("regale")),
    Piatto("Maglia a Righe", "🎽", schifezza = false, colore = "bianco", categorie = setOf("pirata")),
    Piatto("Tuta Spaziale", "🧑‍🚀", schifezza = false, colore = "grigio", categorie = setOf("spazio")),
    Piatto("Vestaglia da Mago", "🪄", schifezza = true, colore = "viola", categorie = setOf("magico")),
    Piatto("Squame Appiccicose", "🦎", schifezza = true, colore = "verde")
)

val elencoScarpe: List<Piatto> = listOf(
    Piatto("Stivali Neri", "🥾", schifezza = false, colore = "nero"),
    Piatto("Scarpe Dorate", "👞", schifezza = false, colore = "giallo", categorie = setOf("regale")),
    Piatto("Stivali da Pirata", "👢", schifezza = false, colore = "nero", categorie = setOf("pirata")),
    Piatto("Scarpe a Razzo", "🚀", schifezza = false, colore = "grigio", categorie = setOf("spazio")),
    Piatto("Zampe Pelose", "🐾", schifezza = true, colore = "marrone")
)

val elencoOggetti: List<Piatto> = listOf(
    Piatto("Bacchetta Magica", "🪄", schifezza = false, colore = "viola", categorie = setOf("magico")),
    Piatto("Spada da Pirata", "🗡️", schifezza = false, colore = "grigio", categorie = setOf("pirata")),
    Piatto("Scudo da Supereroe", "🛡️", schifezza = false, colore = "rosso", categorie = setOf("supereroe")),
    Piatto("Bandierina Spaziale", "🚩", schifezza = false, colore = "grigio", categorie = setOf("spazio")),
    Piatto("Ragno Peloso", "🕷️", schifezza = true, colore = "nero")
)

/** Il banco di accessori giusto per ogni categoria. */
fun banchePer(tipo: TipoAccessorio): List<Piatto> = when (tipo) {
    TipoAccessorio.CAPPELLO -> elencoCappelli
    TipoAccessorio.OCCHI -> elencoOcchiali
    TipoAccessorio.VESTITO -> elencoVestiti
    TipoAccessorio.SCARPE -> elencoScarpe
    TipoAccessorio.OGGETTO -> elencoOggetti
}

/**
 * Le richieste possibili per Vesti il Mostro: colori e "temi da costume" (pirata, supereroe,
 * mago, astronauta, re) invece delle categorie di cibo di Monster Restaurant/Panino. Riusa
 * [RichiestaMostro] e [frazionePunti]. Il flag "schifezza" di [Piatto] qui diventa "stravagante".
 */
val elencoRichiesteVestiti: List<RichiestaMostro> = listOf(
    RichiestaMostro("Voglio essere tutto rosso! 🔴", "🔴") { scelte -> frazionePunti(scelte) { it.colore == "rosso" } },
    RichiestaMostro("Voglio essere tutto nero! ⚫", "⚫") { scelte -> frazionePunti(scelte) { it.colore == "nero" } },
    RichiestaMostro("Voglio essere tutto dorato! 🟡", "🟡") { scelte -> frazionePunti(scelte) { it.colore == "giallo" } },
    RichiestaMostro("Voglio qualcosa di viola! 🟣", "🟣") { scelte -> frazionePunti(scelte) { it.colore == "viola" } },
    RichiestaMostro("Vestimi da pirata! 🏴", "🏴") { scelte -> frazionePunti(scelte) { "pirata" in it.categorie } },
    RichiestaMostro("Vestimi da supereroe! 🦸", "🦸") { scelte -> frazionePunti(scelte) { "supereroe" in it.categorie } },
    RichiestaMostro("Vestimi da mago! 🪄", "🪄") { scelte -> frazionePunti(scelte) { "magico" in it.categorie } },
    RichiestaMostro("Vestimi da astronauta! 🚀", "🚀") { scelte -> frazionePunti(scelte) { "spazio" in it.categorie } },
    RichiestaMostro("Vestimi da re! 👑", "👑") { scelte -> frazionePunti(scelte) { "regale" in it.categorie } },
    RichiestaMostro("Voglio essere stravagante! 🤪", "🤪") { scelte -> frazionePunti(scelte) { it.schifezza } }
)

/** Un livello di Vesti il Mostro: quante categorie di accessori sono attive, nell'ordine di [TipoAccessorio]. */
data class LivelloVestiti(val numero: Int, val tipiAttivi: List<TipoAccessorio>)

/** Livello 1 = solo il cappello, via via se ne aggiunge una categoria fino a tutte e 5. */
val elencoLivelliVestiti: List<LivelloVestiti> = TipoAccessorio.entries.indices.map { indice ->
    LivelloVestiti(numero = indice + 1, tipiAttivi = TipoAccessorio.entries.take(indice + 1))
}
