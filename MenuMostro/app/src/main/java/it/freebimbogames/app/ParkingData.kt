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
 * Quante celle (anche frazionarie) l'auto [id] può scorrere indietro (valore negativo) o avanti
 * (valore positivo) lungo il proprio orientamento prima di toccare un'altra auto o il bordo della
 * griglia. Serve a trascinare un'auto con il dito: durante il trascinamento lo spostamento viene
 * tenuto dentro questo intervallo, così non serve ricontrollare le collisioni ad ogni fotogramma e
 * l'auto non può mai "saltare" sopra un'altra.
 */
fun limiteTrascinamento(auto: List<Auto>, id: Int, dimensione: Int): ClosedFloatingPointRange<Float> {
    val corrente = auto.first { it.id == id }
    val altreCelle = auto.filter { it.id != id }.flatMap { it.celle() }.toSet()
    return if (corrente.orientamento == Orientamento.ORIZZONTALE) {
        val riga = corrente.riga
        val minColonna = (corrente.colonna - 1 downTo 0).firstOrNull { (riga to it) in altreCelle }
        val fineLibera = (corrente.colonna + corrente.lunghezza until dimensione)
            .firstOrNull { (riga to it) in altreCelle } ?: dimensione
        val indietro = -(corrente.colonna - (minColonna ?: -1) - 1).toFloat()
        val avanti = (fineLibera - (corrente.colonna + corrente.lunghezza)).toFloat()
        indietro..avanti
    } else {
        val colonna = corrente.colonna
        val minRiga = (corrente.riga - 1 downTo 0).firstOrNull { (it to colonna) in altreCelle }
        val fineLibera = (corrente.riga + corrente.lunghezza until dimensione)
            .firstOrNull { (it to colonna) in altreCelle } ?: dimensione
        val indietro = -(corrente.riga - (minRiga ?: -1) - 1).toFloat()
        val avanti = (fineLibera - (corrente.riga + corrente.lunghezza)).toFloat()
        indietro..avanti
    }
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
 * I 20 livelli di Monster Parking. I primi 3 sono fissi, pensati e verificati a mano, con
 * griglia via via piu' grande (6x6, 7x7, 8x8). Dal livello 4 in poi la griglia smette quasi
 * del tutto di crescere e cresce invece il numero di auto (vedi sotto, variantiLivelliParcheggio).
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

/** Quanti livelli ha Monster Parking in tutto (i primi 3 fissi piu' i generati). */
const val NUMERO_LIVELLI_PARCHEGGIO = 20

/**
 * Piu' varianti diverse generate per ciascuno dei livelli 4-20: a ogni partita se ne sceglie
 * una a caso (vedi [livelloCasuale]), cosi' il parcheggio non e' mai identico a quello della
 * volta prima. Generate con lo stesso metodo di prima (auto rossa gia' "risolta", mescolata
 * con mosse singole valide, quindi sempre risolvibile per costruzione), ma stavolta con in piu'
 * una vera verifica di difficolta': una ricerca in ampiezza (le mosse sono "un'auto scorre di
 * una qualunque distanza libera", esattamente come un trascinamento col dito) scarta ogni
 * variante risolvibile in meno mosse del minimo richiesto per quel livello — cosa che prima
 * poteva capitare (un livello "mescolato tanto" ma risolvibile per caso in 1-2 mosse).
 */
private val variantiLivelliParcheggio: List<LivelloParcheggio> = listOf(
    // livello 4, variante 0
    LivelloParcheggio(
        numero = 4,
        dimensione = 7,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 3),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1)
        )
    ),
    // livello 4, variante 1
    LivelloParcheggio(
        numero = 4,
        dimensione = 7,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 3),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0)
        )
    ),
    // livello 4, variante 2
    LivelloParcheggio(
        numero = 4,
        dimensione = 7,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 0),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 1)
        )
    ),
    // livello 4, variante 3
    LivelloParcheggio(
        numero = 4,
        dimensione = 7,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 6)
        )
    ),
    // livello 5, variante 0
    LivelloParcheggio(
        numero = 5,
        dimensione = 7,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 4)
        )
    ),
    // livello 5, variante 1
    LivelloParcheggio(
        numero = 5,
        dimensione = 7,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 3),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 6)
        )
    ),
    // livello 5, variante 2
    LivelloParcheggio(
        numero = 5,
        dimensione = 7,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 0),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 3),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 6)
        )
    ),
    // livello 5, variante 3
    LivelloParcheggio(
        numero = 5,
        dimensione = 7,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 0)
        )
    ),
    // livello 6, variante 0
    LivelloParcheggio(
        numero = 6,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 0),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1)
        )
    ),
    // livello 6, variante 1
    LivelloParcheggio(
        numero = 6,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 4)
        )
    ),
    // livello 6, variante 2
    LivelloParcheggio(
        numero = 6,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 7),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 0)
        )
    ),
    // livello 6, variante 3
    LivelloParcheggio(
        numero = 6,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 0),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 7)
        )
    ),
    // livello 7, variante 0
    LivelloParcheggio(
        numero = 7,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 3),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1)
        )
    ),
    // livello 7, variante 1
    LivelloParcheggio(
        numero = 7,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6)
        )
    ),
    // livello 7, variante 2
    LivelloParcheggio(
        numero = 7,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 7),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 1),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 0),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 5)
        )
    ),
    // livello 7, variante 3
    LivelloParcheggio(
        numero = 7,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 6),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 7)
        )
    ),
    // livello 8, variante 0
    LivelloParcheggio(
        numero = 8,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 0),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 5),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 6),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 4)
        )
    ),
    // livello 8, variante 1
    LivelloParcheggio(
        numero = 8,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 0),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 1),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 2),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 3)
        )
    ),
    // livello 8, variante 2
    LivelloParcheggio(
        numero = 8,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 3),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0)
        )
    ),
    // livello 8, variante 3
    LivelloParcheggio(
        numero = 8,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 3),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 7),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 5),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 6)
        )
    ),
    // livello 9, variante 0
    LivelloParcheggio(
        numero = 9,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 1),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 6),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 6)
        )
    ),
    // livello 9, variante 1
    LivelloParcheggio(
        numero = 9,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 7),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 3),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4)
        )
    ),
    // livello 9, variante 2
    LivelloParcheggio(
        numero = 9,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 4),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 7),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 6),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 3),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 2)
        )
    ),
    // livello 9, variante 3
    LivelloParcheggio(
        numero = 9,
        dimensione = 8,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 5),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 3),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 5)
        )
    ),
    // livello 10, variante 0
    LivelloParcheggio(
        numero = 10,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 4),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 6),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 7),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 4),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 5),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 0)
        )
    ),
    // livello 10, variante 1
    LivelloParcheggio(
        numero = 10,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 7),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 0),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 5),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 5),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 3)
        )
    ),
    // livello 10, variante 2
    LivelloParcheggio(
        numero = 10,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 8),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 8),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 5),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 4),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 1)
        )
    ),
    // livello 10, variante 3
    LivelloParcheggio(
        numero = 10,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 5),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 6),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 5)
        )
    ),
    // livello 11, variante 0
    LivelloParcheggio(
        numero = 11,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 3),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 6),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 8),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 4),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 8),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 7),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0)
        )
    ),
    // livello 11, variante 1
    LivelloParcheggio(
        numero = 11,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 7),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 6),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 3),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 2),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 3)
        )
    ),
    // livello 11, variante 2
    LivelloParcheggio(
        numero = 11,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 7),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 3),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 1),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 0),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 2),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 7),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 6)
        )
    ),
    // livello 11, variante 3
    LivelloParcheggio(
        numero = 11,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 0),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 8),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 7),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 3),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 7),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 5),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 7),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 6)
        )
    ),
    // livello 12, variante 0
    LivelloParcheggio(
        numero = 12,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 3),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 7),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 2),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 0),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 8),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 8),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 8),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6)
        )
    ),
    // livello 12, variante 1
    LivelloParcheggio(
        numero = 12,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 2),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 6),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 6),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 8),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4)
        )
    ),
    // livello 12, variante 2
    LivelloParcheggio(
        numero = 12,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 7),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 2),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 8),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 6),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 4),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 1),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 4),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 7),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 8)
        )
    ),
    // livello 12, variante 3
    LivelloParcheggio(
        numero = 12,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 1),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 3),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 7)
        )
    ),
    // livello 13, variante 0
    LivelloParcheggio(
        numero = 13,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 3),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 7),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 8),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 5),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 1),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 7),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 2)
        )
    ),
    // livello 13, variante 1
    LivelloParcheggio(
        numero = 13,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 8),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 3),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 1),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 5),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 2),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 8),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 7),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 0)
        )
    ),
    // livello 13, variante 2
    LivelloParcheggio(
        numero = 13,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 3),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 4),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 6),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 0)
        )
    ),
    // livello 13, variante 3
    LivelloParcheggio(
        numero = 13,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 7),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 7),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 7),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 1),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0)
        )
    ),
    // livello 14, variante 0
    LivelloParcheggio(
        numero = 14,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 5),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 6),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 2),
            Auto(id = 24, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 25, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 7)
        )
    ),
    // livello 14, variante 1
    LivelloParcheggio(
        numero = 14,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 3),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 3),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 4),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 5),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 7),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 4),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 24, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 25, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 0)
        )
    ),
    // livello 14, variante 2
    LivelloParcheggio(
        numero = 14,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 3),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 1),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 3),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 5),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 5),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 7)
        )
    ),
    // livello 14, variante 3
    LivelloParcheggio(
        numero = 14,
        dimensione = 9,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 8),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 4),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 4),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 24, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 6)
        )
    ),
    // livello 15, variante 0
    LivelloParcheggio(
        numero = 15,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 9),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 7),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 4),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 6),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 7),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 3)
        )
    ),
    // livello 15, variante 1
    LivelloParcheggio(
        numero = 15,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 9),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 8),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 5),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 3),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 6),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 3),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 7)
        )
    ),
    // livello 15, variante 2
    LivelloParcheggio(
        numero = 15,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 2),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 4),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 7),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 8),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 8),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 3),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 9),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 8),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 3)
        )
    ),
    // livello 15, variante 3
    LivelloParcheggio(
        numero = 15,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 7),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 0),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 4),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 8),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 8),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1)
        )
    ),
    // livello 16, variante 0
    LivelloParcheggio(
        numero = 16,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 9),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 5),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 9),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 4),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 3),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 7),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 8),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1)
        )
    ),
    // livello 16, variante 1
    LivelloParcheggio(
        numero = 16,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 9),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 8),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 4),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 5),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 4),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 9),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 9),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 7)
        )
    ),
    // livello 16, variante 2
    LivelloParcheggio(
        numero = 16,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 9),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 3),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 1),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 6),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 2),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 5),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 8),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 9),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 3),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 24, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 7)
        )
    ),
    // livello 16, variante 3
    LivelloParcheggio(
        numero = 16,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 7),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 7),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 8),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 7),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 9),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 3),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 5),
            Auto(id = 24, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 25, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 1)
        )
    ),
    // livello 17, variante 0
    LivelloParcheggio(
        numero = 17,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 7),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 8),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 9),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 9),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 5),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 6, colonna = 3),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 2),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 8),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 1),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 8),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 3),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 26, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 9),
            Auto(id = 27, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 6)
        )
    ),
    // livello 17, variante 1
    LivelloParcheggio(
        numero = 17,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 7),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 6),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 5),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 7),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 8),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 3),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 6),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 7),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 3),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 9),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 3),
            Auto(id = 26, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 27, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 0)
        )
    ),
    // livello 17, variante 2
    LivelloParcheggio(
        numero = 17,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 7),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 0),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 9),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 8),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 5),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 9),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 2),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 4),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 5),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 24, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 4),
            Auto(id = 26, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 27, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 6)
        )
    ),
    // livello 17, variante 3
    LivelloParcheggio(
        numero = 17,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 2),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 9),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 3),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 6),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 3),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 8),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 1),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 0),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 24, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 25, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 26, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 27, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 5)
        )
    ),
    // livello 18, variante 0
    LivelloParcheggio(
        numero = 18,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 7),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 3, colonna = 1),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 8),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 0),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 2),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 0),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 5),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 7, colonna = 9),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 0, colonna = 9),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 26, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 4),
            Auto(id = 27, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 8),
            Auto(id = 28, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 29, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 6)
        )
    ),
    // livello 18, variante 1
    LivelloParcheggio(
        numero = 18,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 7),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 1),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 9),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 7),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 0),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 4),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 2),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 7),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 8),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 7),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 7),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 5),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 9),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 3),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 4),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 7),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 26, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 27, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 28, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 29, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 8)
        )
    ),
    // livello 18, variante 2
    LivelloParcheggio(
        numero = 18,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 3),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 0),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 7),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 8),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 6),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 9),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 8),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 9),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 25, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 26, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 27, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 8),
            Auto(id = 28, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 2),
            Auto(id = 29, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1)
        )
    ),
    // livello 18, variante 3
    LivelloParcheggio(
        numero = 18,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 2, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 7),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 9),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 6),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 8),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 4),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 6),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 9),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 24, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 26, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 27, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 6),
            Auto(id = 28, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 29, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 8)
        )
    ),
    // livello 19, variante 0
    LivelloParcheggio(
        numero = 19,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 9),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 6),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 4),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 2),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 8),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 6),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 5),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 1),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 7),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 0),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 9),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 26, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 27, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 28, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 7),
            Auto(id = 29, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 1),
            Auto(id = 30, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 31, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 1)
        )
    ),
    // livello 19, variante 1
    LivelloParcheggio(
        numero = 19,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 8),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 9),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 8),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 2),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 4),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 7),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 9),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 2),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 3),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 1),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 0),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 5),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 24, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 3),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 26, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 27, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 0),
            Auto(id = 28, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 29, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 30, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 31, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3)
        )
    ),
    // livello 19, variante 2
    LivelloParcheggio(
        numero = 19,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 1),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 5),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 9),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 2),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 9),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 10, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 4),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 9),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 3),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 4),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 1),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 0),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 1),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 6),
            Auto(id = 26, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 27, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 28, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 29, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 9),
            Auto(id = 30, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 31, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 2)
        )
    ),
    // livello 19, variante 3
    LivelloParcheggio(
        numero = 19,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 3),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 8),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 1),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 2),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 9),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 0),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 7),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 7),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 9),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 9),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 8),
            Auto(id = 22, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 3),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 1),
            Auto(id = 26, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 27, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 28, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 29, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 3),
            Auto(id = 30, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 3),
            Auto(id = 31, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 5)
        )
    ),
    // livello 20, variante 0
    LivelloParcheggio(
        numero = 20,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 8, colonna = 6),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 5),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 4),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 8),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 5),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 4),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 7),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 3),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 1),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 5, colonna = 0),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 6),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 0),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 23, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 7),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 2),
            Auto(id = 25, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 9),
            Auto(id = 26, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 6, colonna = 1),
            Auto(id = 27, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 28, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 7, colonna = 7),
            Auto(id = 29, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 1),
            Auto(id = 30, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 31, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 4),
            Auto(id = 32, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 33, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 7)
        )
    ),
    // livello 20, variante 1
    LivelloParcheggio(
        numero = 20,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 4, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 0),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 7),
            Auto(id = 3, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 7),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 4),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 3),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 3),
            Auto(id = 7, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 9),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 6),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 1),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 13, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 14, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 0),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 9),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 6),
            Auto(id = 21, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 6),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 1, colonna = 9),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 25, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 8),
            Auto(id = 26, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 27, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 1),
            Auto(id = 28, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 1),
            Auto(id = 29, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 30, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 31, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 5),
            Auto(id = 32, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 4),
            Auto(id = 33, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 5)
        )
    ),
    // livello 20, variante 2
    LivelloParcheggio(
        numero = 20,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 3),
            Auto(id = 2, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 6),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 4, colonna = 7),
            Auto(id = 4, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 5, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 6),
            Auto(id = 6, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 5),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 8, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 4),
            Auto(id = 9, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 5),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 4, colonna = 2),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 7),
            Auto(id = 12, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 1),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 0),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 6),
            Auto(id = 15, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 9),
            Auto(id = 16, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 17, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 0),
            Auto(id = 18, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 19, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 3),
            Auto(id = 20, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 7),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 8, colonna = 0),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 4),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 0),
            Auto(id = 24, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 1, colonna = 4),
            Auto(id = 26, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 6),
            Auto(id = 27, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 9),
            Auto(id = 28, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 0),
            Auto(id = 29, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 2, colonna = 8),
            Auto(id = 30, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 2),
            Auto(id = 31, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 9),
            Auto(id = 32, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 33, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 5)
        )
    ),
    // livello 20, variante 3
    LivelloParcheggio(
        numero = 20,
        dimensione = 10,
        autoIniziali = listOf(
            Auto(id = 0, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 6, rossa = true),
            Auto(id = 1, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 2),
            Auto(id = 2, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 2, colonna = 0),
            Auto(id = 3, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 1),
            Auto(id = 4, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 5, colonna = 1),
            Auto(id = 5, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 4),
            Auto(id = 6, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 7, colonna = 4),
            Auto(id = 7, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 4),
            Auto(id = 8, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 6),
            Auto(id = 9, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 2, colonna = 7),
            Auto(id = 10, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 8),
            Auto(id = 11, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 9, colonna = 5),
            Auto(id = 12, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 8),
            Auto(id = 13, orientamento = Orientamento.VERTICALE, lunghezza = 3, riga = 4, colonna = 6),
            Auto(id = 14, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 2, colonna = 1),
            Auto(id = 15, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 5, colonna = 2),
            Auto(id = 16, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 0, colonna = 3),
            Auto(id = 17, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 2),
            Auto(id = 18, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 7),
            Auto(id = 19, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 3, colonna = 2),
            Auto(id = 20, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 6, colonna = 9),
            Auto(id = 21, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 0, colonna = 8),
            Auto(id = 22, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 3, colonna = 1),
            Auto(id = 23, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 6, colonna = 2),
            Auto(id = 24, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 7, colonna = 0),
            Auto(id = 25, orientamento = Orientamento.ORIZZONTALE, lunghezza = 3, riga = 0, colonna = 5),
            Auto(id = 26, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 5, colonna = 7),
            Auto(id = 27, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 8, colonna = 4),
            Auto(id = 28, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 3, colonna = 5),
            Auto(id = 29, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 4),
            Auto(id = 30, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 5),
            Auto(id = 31, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 9, colonna = 1),
            Auto(id = 32, orientamento = Orientamento.ORIZZONTALE, lunghezza = 2, riga = 1, colonna = 4),
            Auto(id = 33, orientamento = Orientamento.VERTICALE, lunghezza = 2, riga = 4, colonna = 8)
        )
    )
)

private val variantiPerNumero: Map<Int, List<LivelloParcheggio>> by lazy {
    variantiLivelliParcheggio.groupBy { it.numero }
}

/** Il livello da giocare per [numero]: fisso per i primi 3, scelto a caso fra le varianti dal 4 in poi. */
fun livelloCasuale(numero: Int): LivelloParcheggio =
    if (numero <= elencoLivelliParcheggio.size) elencoLivelliParcheggio[numero - 1]
    else variantiPerNumero.getValue(numero).random()
