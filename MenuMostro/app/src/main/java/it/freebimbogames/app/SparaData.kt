package it.freebimbogames.app

import kotlin.random.Random

/**
 * Spara ai Mostri: un mini sparatutto da luna park, non violento — un razzo in basso spara
 * stelline verso l'alto per far scoppiare "palloncini mostro" che scendono dal cielo, come un
 * tiro a segno. Le coordinate (x, y) di ogni entità sono in pixel dello schermo di gioco,
 * origine in alto a sinistra: le usa direttamente la UI per posizionare le emoji.
 */

/** Un mostro-palloncino che scende dall'alto verso il razzo. */
data class MostroVolante(val id: Int, val emoji: String, val x: Float, val y: Float, val velocitaY: Float)

/** Una stellina sparata dal razzo verso l'alto. */
data class Proiettile(val id: Int, val x: Float, val y: Float)

/** Un piccolo scoppio ✨ mostrato per un attimo quando un mostro viene colpito. */
data class EffettoPop(val id: Int, val x: Float, val y: Float, val scadenzaNanos: Long)

private const val RAGGIO_COLPO = 60f
private const val MASSIMO_MOSTRI_A_SCHERMO = 15

/** Vero se una stellina in (px, py) è abbastanza vicina a un mostro in (mx, my) da farlo scoppiare. */
fun colpisce(px: Float, py: Float, mx: Float, my: Float): Boolean {
    val dx = px - mx
    val dy = py - my
    return dx * dx + dy * dy < RAGGIO_COLPO * RAGGIO_COLPO
}

/**
 * Genera un nuovo mostro in cima allo schermo, in una posizione orizzontale casuale. La velocità
 * di caduta cresce (fino a un limite) man mano che il punteggio sale, per aumentare la sfida.
 * Restituisce null se ci sono già troppi mostri a schermo, per non sovraccaricare il gioco.
 */
fun generaMostro(id: Int, larghezzaPx: Float, punteggio: Int, mostriAttuali: Int): MostroVolante? {
    if (mostriAttuali >= MASSIMO_MOSTRI_A_SCHERMO) return null
    val margine = 40f
    val x = Random.nextFloat() * (larghezzaPx - 2 * margine) + margine
    val velocitaBase = 70f + minOf(punteggio * 3f, 140f)
    val velocita = velocitaBase + Random.nextFloat() * 40f
    return MostroVolante(id = id, emoji = elencoSimboliMemory.random(), x = x, y = -60f, velocitaY = velocita)
}

/** L'intervallo tra un mostro e il successivo si accorcia (fino a un minimo) man mano che il punteggio sale. */
fun intervalloSpawnMillis(punteggio: Int): Long = maxOf(1400L - punteggio * 25L, 550L)
