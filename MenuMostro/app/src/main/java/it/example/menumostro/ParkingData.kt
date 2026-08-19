package it.example.menumostro

/** Le due direzioni in cui un'auto può muoversi: orizzontale (sinistra/destra) o verticale (su/giù). */
enum class Orientamento { ORIZZONTALE, VERTICALE }

/**
 * Un'auto parcheggiata: occupa [lunghezza] celle consecutive a partire da (riga, colonna) e si
 * può spostare solo avanti e indietro lungo il proprio orientamento, mai di lato — come in Rush
 * Hour. L'auto rossa è quella da liberare.
 */
data class Auto(
    val id: Int,
    val orientamento: Orientamento,
    val lunghezza: Int,
    val riga: Int,
    val colonna: Int,
    val rossa: Boolean = false
)

/** Le celle (riga, colonna) occupate da un'auto nella sua posizione attuale. */
fun Auto.celle(): List<Pair<Int, Int>> = if (orientamento == Orientamento.ORIZZONTALE) {
    (colonna until colonna + lunghezza).map { riga to it }
} else {
    (riga until riga + lunghezza).map { it to colonna }
}

/**
 * Prova a spostare l'auto [id] di [delta] celle (di solito ±1) lungo il proprio orientamento.
 * Restituisce la nuova lista di auto se la mossa è valida (resta dentro la griglia e non si
 * sovrappone a un'altra auto), altrimenti null.
 */
fun provaSpostamento(auto: List<Auto>, id: Int, delta: Int, dimensione: Int): List<Auto>? {
    val corrente = auto.first { it.id == id }
    val spostata = if (corrente.orientamento == Orientamento.ORIZZONTALE) {
        corrente.copy(colonna = corrente.colonna + delta)
    } else {
        corrente.copy(riga = corrente.riga + delta)
    }

    val celleSpostata = spostata.celle()
    val dentroGriglia = celleSpostata.all { (r, c) -> r in 0 until dimensione && c in 0 until dimensione }
    if (!dentroGriglia) return null

    val celleAltre = auto.filter { it.id != id }.flatMap { it.celle() }
    if (celleSpostata.any { it in celleAltre }) return null

    return auto.map { if (it.id == id) spostata else it }
}

/**
 * Un livello di Monster Parking: una griglia [dimensione] x [dimensione] con un parcheggio
 * selvaggio da sbloccare. L'auto rossa è sempre orizzontale e deve uscire dal lato destro della
 * sua riga.
 */
data class LivelloParcheggio(val numero: Int, val dimensione: Int, val autoIniziali: List<Auto>)

/** La riga da cui l'auto rossa deve uscire (il bordo destro di quella riga è il traguardo). */
val LivelloParcheggio.rigaUscita: Int get() = autoIniziali.first { it.rossa }.riga

/** Vero se l'auto rossa ha raggiunto il bordo destro della griglia: livello risolto. */
fun LivelloParcheggio.risolto(auto: List<Auto>): Boolean {
    val rossa = auto.first { it.rossa }
    return rossa.colonna + rossa.lunghezza - 1 == dimensione - 1
}

/**
 * I livelli di Monster Parking, con griglia via via più grande (6x6, 7x7, 8x8) e più auto da
 * spostare. Ogni parcheggio è costruito a mano assicurandosi che ogni auto che blocca la strada
 * abbia sempre una via di fuga libera (verso l'alto o verso il basso): sono quindi tutti
 * risolvibili con semplici spostamenti, uno alla volta.
 */
val elencoLivelliParcheggio: List<LivelloParcheggio> = listOf(
    LivelloParcheggio(
        numero = 1,
        dimensione = 6,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 1, rossa = true),
            Auto(id = 1, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 0)
        )
    ),
    LivelloParcheggio(
        numero = 2,
        dimensione = 7,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 1, rossa = true),
            Auto(id = 1, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2)
        )
    ),
    LivelloParcheggio(
        numero = 3,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1, rossa = true),
            Auto(id = 1, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 0),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 7),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 0)
        )
    )
)
