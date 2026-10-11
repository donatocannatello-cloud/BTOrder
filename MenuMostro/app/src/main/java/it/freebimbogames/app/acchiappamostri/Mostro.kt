package it.freebimbogames.app.acchiappamostri

/** I mostriciattoli del gioco, ciascuno con la sua emoji e il suo colore (ARGB). */
enum class TipoMostro(val emoji: String, val nome: String, val colore: Long) {
    MOSTRO("👹", "Mostro", 0xFFEF5350),
    FANTASMA("👻", "Fantasma", 0xFF90A4AE),
    ORCO("🧌", "Orco", 0xFF66BB6A),
    ALIENO("👽", "Alieno", 0xFF26C6DA),
    ZUCCA("🎃", "Zucca", 0xFFFFA726),
    PIOVRA("🐙", "Piovra", 0xFFAB47BC),
    UNICORNO("🦄", "Unicorno", 0xFFEC407A),
    DRAGHETTO("🐲", "Draghetto", 0xFF42A5F5),
    PIPISTRELLO("🦇", "Pipistrello", 0xFF757575),
}

/** Una tana in cui va acchiappato il mostro dello stesso tipo. */
class Slot(val tipo: TipoMostro, val centro: Punto, val raggio: Float) {
    var occupato = false
}

/** Un mostro da prendere con la mano. */
class Pezzo(
    val id: Int,
    /** Può cambiare durante il livello, nei livelli in cui i mostri si trasformano. */
    var tipo: TipoMostro,
    /** Posizione di partenza, dove torna se viene messo nella tana sbagliata. */
    val casa: Punto,
    val raggio: Float,
) {
    var posizione = casa
    var incastrato = false
    var tornaACasa = false
    /** 0..1: animazione di comparsa a inizio livello, di "scatto" nella tana e di trasformazione. */
    var rimbalzo = 0f
    /** Velocità (pixel al secondo) nei livelli in cui i mostri si muovono da soli. */
    var velocita = Punto.ZERO
    /** Ogni quanti secondi il mostro si trasforma (0 = mai) e quanto manca alla prossima volta. */
    var periodoCambio = 0f
    var tempoAlCambio = 0f
}
