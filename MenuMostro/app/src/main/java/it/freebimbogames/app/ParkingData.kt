package it.freebimbogames.app

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
 * I 20 livelli di Monster Parking, con griglia via via più grande (da 6x6 a 10x10) e sempre più
 * auto da spostare. I livelli 1-3 sono i primi tre, pensati e verificati a mano. Dal livello 4 in
 * poi sono generati partendo dall'auto rossa già "risolta" (attaccata al bordo destro) e
 * mescolando la griglia con una sequenza di mosse singole valide: risolvere il livello equivale
 * quindi a ripercorrere quella sequenza al contrario, il che garantisce per costruzione che ogni
 * livello sia risolvibile (nessun parcheggio impossibile), senza bisogno di un solver a runtime.
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
    ),
    LivelloParcheggio(
        numero = 4,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 4),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 3),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 2)
        )
    ),
    LivelloParcheggio(
        numero = 5,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 3),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 5)
        )
    ),
    LivelloParcheggio(
        numero = 6,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 1, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 8),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 0)
        )
    ),
    LivelloParcheggio(
        numero = 7,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 4),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 3)
        )
    ),
    LivelloParcheggio(
        numero = 8,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 3, rossa = true),
            Auto(id = 1, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 8),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 5),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 8),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 7),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 2)
        )
    ),
    LivelloParcheggio(
        numero = 9,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 3),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 8),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 7),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 0),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 1)
        )
    ),
    LivelloParcheggio(
        numero = 10,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 4),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 3),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 4),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 5)
        )
    ),
    LivelloParcheggio(
        numero = 11,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 7),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 0),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 2),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 8),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 1),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3)
        )
    ),
    LivelloParcheggio(
        numero = 12,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 4),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 8),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 7),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 8)
        )
    ),
    LivelloParcheggio(
        numero = 13,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5, rossa = true),
            Auto(id = 1, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 9),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 7),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 6),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 0)
        )
    ),
    LivelloParcheggio(
        numero = 14,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 9),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 4),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 2),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 7),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 8),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 5)
        )
    ),
    LivelloParcheggio(
        numero = 15,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 5, rossa = true),
            Auto(id = 1, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 8),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 9),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 6),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 5)
        )
    ),
    LivelloParcheggio(
        numero = 16,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 9),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 2),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 3),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 5),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 7),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 7),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 7)
        )
    ),
    LivelloParcheggio(
        numero = 17,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 3, rossa = true),
            Auto(id = 1, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 7),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 8),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 5),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 7),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 3),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 0),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 5),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0)
        )
    ),
    LivelloParcheggio(
        numero = 18,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 7),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 2),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 9),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4)
        )
    ),
    LivelloParcheggio(
        numero = 19,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 0),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 9),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 7),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 1),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 3),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 7),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 8),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 1),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 5),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 0)
        )
    ),
    LivelloParcheggio(
        numero = 20,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 3),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 9),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 8),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 2),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 2),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 1),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 9),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 0),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 9),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 0)
        )
    )
)
