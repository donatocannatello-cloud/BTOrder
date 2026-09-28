package it.example.forme.gioco

/** Le figure del gioco, ciascuna con il suo colore (ARGB). */
enum class TipoForma(val nome: String, val colore: Long) {
    STELLA("Stella", 0xFFFFD54F),
    CUBO("Cubo", 0xFF42A5F5),
    CERCHIO("Cerchio", 0xFFEF5350),
    TRIANGOLO("Triangolo", 0xFF66BB6A),
    QUADRATO("Quadrato", 0xFFFFA726),
    CUORE("Cuore", 0xFFEC407A),
    ROMBO("Rombo", 0xFFAB47BC),
    LUNA("Luna", 0xFF26C6DA),
    CROCE("Croce", 0xFFD4E157),
}

/** Un incavo in cui va infilata la forma dello stesso tipo. */
class Slot(val tipo: TipoForma, val centro: Punto, val raggio: Float, val pesante: Boolean = false) {
    var occupato = false
}

/** Una forma da prendere con le mani. */
class Pezzo(
    val id: Int,
    val tipo: TipoForma,
    /** Posizione di partenza, dove torna se viene messa nell'incavo sbagliato. */
    val casa: Punto,
    val raggio: Float,
    /** Le forme pesanti si sollevano solo afferrandole con tutte e due le mani. */
    val pesante: Boolean,
) {
    var posizione = casa
    var incastrato = false
    var tornaACasa = false
    /** Secondi di "tremolio" rimasti: la forma pesante vibra se la si prova a sollevare con una mano. */
    var scossa = 0f
    /** 0..1: animazione di comparsa a inizio livello e di "scatto" nell'incavo. */
    var rimbalzo = 0f
}
