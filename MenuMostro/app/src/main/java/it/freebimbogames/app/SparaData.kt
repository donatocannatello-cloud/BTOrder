package it.freebimbogames.app

import kotlin.random.Random

/**
 * Spara ai Mostri: un mini sparatutto da luna park, non violento — un razzo in basso spara
 * stelline verso l'alto per far scoppiare "palloncini mostro" che scendono dal cielo, come un
 * tiro a segno. Le coordinate (x, y) di ogni entità sono in pixel dello schermo di gioco,
 * origine in alto a sinistra: le usa direttamente la UI per posizionare le emoji.
 */

/** Il tipo di un mostro-palloncino: da colpire, da evitare, oppure il potenziamento moltiplicatore. */
enum class TipoMostro { NORMALE, EVITA, MOLTIPLICATORE }

/** Un mostro-palloncino che scende dall'alto verso il razzo. */
data class MostroVolante(
    val id: Int,
    val emoji: String,
    val tipo: TipoMostro,
    val x: Float,
    val y: Float,
    val velocitaY: Float
)

/** Una stellina sparata dal razzo verso l'alto. */
data class Proiettile(val id: Int, val x: Float, val y: Float)

/** Un piccolo effetto mostrato per un attimo quando un mostro viene colpito. */
data class EffettoPop(val id: Int, val x: Float, val y: Float, val emoji: String, val scadenzaNanos: Long)

/** Amici travestiti da mostro: non vanno colpiti, altrimenti si perde una vita invece di fare punti. */
private val elencoAmiciSpara = listOf("😇", "👼", "🐰", "🦋", "🐣")

private const val RAGGIO_COLPO = 60f
private const val MASSIMO_MOSTRI_A_SCHERMO = 15

/** Da che punteggio in poi compaiono i mostri-amico da evitare, e con quale probabilità massima. */
private const val SOGLIA_EVITA = 8
private const val PROBABILITA_MASSIMA_EVITA = 0.28f

/** Da che punteggio in poi può comparire il mostro moltiplicatore, e con quale probabilità. */
private const val SOGLIA_MOLTIPLICATORE = 16
private const val PROBABILITA_MOLTIPLICATORE = 0.07f

/** Da che punteggio in poi la difficoltà sale ulteriormente (mostri più frequenti e veloci). */
private const val SOGLIA_LIVELLO_4 = 30

/** Da 1 a 4: quanti elementi di sfida sono già entrati in gioco, mostrato nell'interfaccia. */
fun livelloSpara(punteggio: Int): Int = when {
    punteggio < SOGLIA_EVITA -> 1
    punteggio < SOGLIA_MOLTIPLICATORE -> 2
    punteggio < SOGLIA_LIVELLO_4 -> 3
    else -> 4
}

/** Vero se una stellina in (px, py) è abbastanza vicina a un mostro in (mx, my) da farlo scoppiare. */
fun colpisce(px: Float, py: Float, mx: Float, my: Float): Boolean {
    val dx = px - mx
    val dy = py - my
    return dx * dx + dy * dy < RAGGIO_COLPO * RAGGIO_COLPO
}

/** Sceglie il tipo del prossimo mostro in base al punteggio: gli elementi nuovi si sbloccano uno alla volta. */
private fun tipoCasuale(punteggio: Int, esisteMoltiplicatoreInVolo: Boolean): TipoMostro {
    if (!esisteMoltiplicatoreInVolo &&
        punteggio >= SOGLIA_MOLTIPLICATORE &&
        Random.nextFloat() < PROBABILITA_MOLTIPLICATORE
    ) {
        return TipoMostro.MOLTIPLICATORE
    }
    if (punteggio >= SOGLIA_EVITA) {
        val probabilitaEvita = minOf((punteggio - SOGLIA_EVITA) * 0.012f, PROBABILITA_MASSIMA_EVITA)
        if (Random.nextFloat() < probabilitaEvita) return TipoMostro.EVITA
    }
    return TipoMostro.NORMALE
}

/**
 * Genera un nuovo mostro in cima allo schermo, in una posizione orizzontale casuale. La velocità
 * di caduta cresce (fino a un limite) man mano che il punteggio sale, per aumentare la sfida.
 * Restituisce null se ci sono già troppi mostri a schermo, per non sovraccaricare il gioco.
 */
fun generaMostro(
    id: Int,
    larghezzaPx: Float,
    punteggio: Int,
    mostriAttuali: Int,
    esisteMoltiplicatoreInVolo: Boolean
): MostroVolante? {
    if (mostriAttuali >= MASSIMO_MOSTRI_A_SCHERMO) return null
    val margine = 40f
    val x = Random.nextFloat() * (larghezzaPx - 2 * margine) + margine
    val velocitaBase = 70f + minOf(punteggio * 3f, 170f)
    val velocita = velocitaBase + Random.nextFloat() * 40f
    val tipo = tipoCasuale(punteggio, esisteMoltiplicatoreInVolo)
    val emoji = when (tipo) {
        TipoMostro.NORMALE -> elencoSimboliMemory.random()
        TipoMostro.EVITA -> elencoAmiciSpara.random()
        TipoMostro.MOLTIPLICATORE -> "🌟"
    }
    return MostroVolante(id = id, emoji = emoji, tipo = tipo, x = x, y = -60f, velocitaY = velocita)
}

/** L'intervallo tra un mostro e il successivo si accorcia (fino a un minimo) man mano che il punteggio sale. */
fun intervalloSpawnMillis(punteggio: Int): Long = maxOf(1400L - punteggio * 25L, 420L)
