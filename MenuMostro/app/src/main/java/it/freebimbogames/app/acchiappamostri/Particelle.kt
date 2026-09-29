package it.freebimbogames.app.acchiappamostri

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Coriandoli che esplodono quando un mostro si infila nella sua tana. */
class Particelle {
    private class Particella(var x: Float, var y: Float, var vx: Float, var vy: Float, var vita: Float, val colore: Color, val r: Float)

    private val lista = ArrayList<Particella>()

    fun esplodi(centro: Offset, colore: Color, quante: Int, velocita: Float, raggio: Float) {
        repeat(quante) {
            val a = Random.nextDouble(0.0, 2 * PI)
            val v = velocita * (0.4f + Random.nextFloat() * 0.8f)
            val tinta = if (Random.nextFloat() < 0.35f) Color.White else colore
            lista += Particella(
                centro.x, centro.y,
                (cos(a) * v).toFloat(), (sin(a) * v).toFloat(),
                0.7f + Random.nextFloat() * 0.5f, tinta, raggio * (0.5f + Random.nextFloat()),
            )
        }
    }

    fun aggiorna(dt: Float, gravita: Float) {
        val it = lista.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vita -= dt
            if (p.vita <= 0f) {
                it.remove()
                continue
            }
            p.vy += gravita * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.98f
        }
    }

    fun DrawScope.disegna() {
        for (p in lista) {
            drawCircle(p.colore.copy(alpha = p.vita.coerceIn(0f, 1f)), p.r, Offset(p.x, p.y))
        }
    }

    val vuote get() = lista.isEmpty()
}
