package it.freebimbogames.app

/** Una carta del memory: due carte con lo stesso [emoji] formano una coppia da trovare. */
data class CartaMemory(val id: Int, val emoji: String, val abbinata: Boolean = false)

/** Il pool di simboli mostruosi tra cui pescare le coppie di ogni livello. */
val elencoSimboliMemory: List<String> = listOf(
    "👹", "👻", "🧌", "👽", "🎃", "🐙", "👾", "🦄",
    "🐲", "🦇", "🕷️", "🐉", "🧟", "🧛", "🦖", "🐍"
)

/** Un livello di Memory dei Mostri: quante coppie compongono la griglia e in quante colonne. */
data class LivelloMemory(val numero: Int, val coppie: Int, val colonne: Int)

/** 6 livelli sequenziali: la griglia cresce da 4 a 15 coppie. */
val elencoLivelliMemory: List<LivelloMemory> = listOf(
    LivelloMemory(numero = 1, coppie = 4, colonne = 2),
    LivelloMemory(numero = 2, coppie = 6, colonne = 3),
    LivelloMemory(numero = 3, coppie = 8, colonne = 4),
    LivelloMemory(numero = 4, coppie = 10, colonne = 4),
    LivelloMemory(numero = 5, coppie = 12, colonne = 4),
    LivelloMemory(numero = 6, coppie = 15, colonne = 5)
)

/** Genera un mazzo mischiato di [coppie] coppie di carte, pescando simboli a caso dal pool. */
fun mazzoMemory(coppie: Int): List<CartaMemory> {
    val simboli = elencoSimboliMemory.shuffled().take(coppie)
    return (simboli + simboli).shuffled().mapIndexed { indice, emoji -> CartaMemory(id = indice, emoji = emoji) }
}
