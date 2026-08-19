package it.freebimbogames.app

/** Un tasto colorato del Simon: [colore] è un ARGB (0xAARRGGBB) usato direttamente come Color. */
data class TastoRitmo(val id: Int, val emoji: String, val colore: Long)

/** I 4 tasti del gioco, sempre nello stesso ordine. */
val elencoTastiRitmo: List<TastoRitmo> = listOf(
    TastoRitmo(id = 0, emoji = "👹", colore = 0xFFEF5350),
    TastoRitmo(id = 1, emoji = "👻", colore = 0xFF42A5F5),
    TastoRitmo(id = 2, emoji = "🧌", colore = 0xFF66BB6A),
    TastoRitmo(id = 3, emoji = "👽", colore = 0xFFFFCA28)
)
