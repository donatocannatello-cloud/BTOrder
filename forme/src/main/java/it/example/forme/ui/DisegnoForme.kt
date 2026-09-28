package it.example.forme.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import it.example.forme.gioco.Pezzo
import it.example.forme.gioco.Punto
import it.example.forme.gioco.Slot
import it.example.forme.gioco.TipoForma
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun Punto.offset() = Offset(x, y)

/** Contorno della forma [tipo] centrata in [c], grande circa quanto un cerchio di raggio [r]. */
fun percorsoForma(tipo: TipoForma, c: Offset, r: Float): Path = when (tipo) {
    TipoForma.CERCHIO -> Path().apply { addOval(Rect(c, r)) }
    TipoForma.QUADRATO -> Path().apply {
        val l = r * 0.85f
        addRoundRect(RoundRect(Rect(c.x - l, c.y - l, c.x + l, c.y + l), CornerRadius(r * 0.15f)))
    }
    TipoForma.TRIANGOLO -> poligono(c + Offset(0f, r * 0.15f), r * 1.15f, 3)
    TipoForma.CUBO -> poligono(c, r * 1.05f, 6)
    TipoForma.STELLA -> Path().apply {
        val esterno = r * 1.12f
        val interno = esterno * 0.45f
        for (k in 0 until 10) {
            val a = (-90.0 + k * 36.0) * PI / 180.0
            val raggio = if (k % 2 == 0) esterno else interno
            val p = Offset(c.x + (cos(a) * raggio).toFloat(), c.y + (sin(a) * raggio).toFloat())
            if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    TipoForma.CUORE -> Path().apply {
        moveTo(c.x, c.y + r * 0.9f)
        cubicTo(c.x - r * 1.4f, c.y, c.x - r * 0.8f, c.y - r * 1.2f, c.x, c.y - r * 0.45f)
        cubicTo(c.x + r * 0.8f, c.y - r * 1.2f, c.x + r * 1.4f, c.y, c.x, c.y + r * 0.9f)
        close()
    }
    TipoForma.ROMBO -> Path().apply {
        moveTo(c.x, c.y - r * 1.1f)
        lineTo(c.x + r * 0.75f, c.y)
        lineTo(c.x, c.y + r * 1.1f)
        lineTo(c.x - r * 0.75f, c.y)
        close()
    }
    TipoForma.LUNA -> Path().apply {
        val pieno = Path().apply { addOval(Rect(c, r)) }
        val buco = Path().apply { addOval(Rect(c + Offset(r * 0.42f, -r * 0.18f), r * 0.82f)) }
        op(pieno, buco, PathOperation.Difference)
    }
    TipoForma.CROCE -> Path().apply {
        val a = r * 0.33f
        val l = r * 0.95f
        val punti = listOf(
            -a to -l, a to -l, a to -a, l to -a, l to a, a to a,
            a to l, -a to l, -a to a, -l to a, -l to -a, -a to -a,
        )
        punti.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(c.x + x, c.y + y) else lineTo(c.x + x, c.y + y) }
        close()
    }
}

/** Poligono regolare con un vertice in alto. */
private fun poligono(c: Offset, r: Float, lati: Int): Path = Path().apply {
    for (k in 0 until lati) {
        val p = vertice(c, r, lati, k)
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

private fun vertice(c: Offset, r: Float, lati: Int, k: Int): Offset {
    val a = -PI / 2 + k * 2 * PI / lati
    return Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
}

private fun facce(p: List<Offset>) = Path().apply {
    moveTo(p[0].x, p[0].y)
    for (i in 1 until p.size) lineTo(p[i].x, p[i].y)
    close()
}

/** Le tre facce visibili del cubo: sopra, destra, sinistra. */
private fun facceCubo(c: Offset, r: Float): List<Path> {
    val v = (0 until 6).map { vertice(c, r * 1.05f, 6, it) }
    return listOf(
        facce(listOf(v[0], v[1], c, v[5])),
        facce(listOf(c, v[1], v[2], v[3])),
        facce(listOf(c, v[3], v[4], v[5])),
    )
}

private val pennelloTesto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
}

/** Etichetta "due mani" sulle forme pesanti e sui loro incavi. */
private fun DrawScope.etichettaPesante(c: Offset, r: Float, colore: Color) {
    pennelloTesto.textSize = r * 0.42f
    pennelloTesto.color = colore.toArgb()
    drawContext.canvas.nativeCanvas.drawText("✋✋", c.x, c.y + r * 0.15f, pennelloTesto)
}

/** Un incavo: sagoma scura con il bordo tratteggiato (colorato nei primi livelli, come aiuto). */
fun DrawScope.disegnaSlot(slot: Slot, aiutoColore: Boolean, dp: Float) {
    val c = slot.centro.offset()
    val r = slot.raggio * 1.08f
    val sagoma = percorsoForma(slot.tipo, c, r)
    val colore = Color(slot.tipo.colore)
    drawPath(sagoma, Color.Black.copy(alpha = 0.5f))
    if (aiutoColore) drawPath(sagoma, colore.copy(alpha = 0.22f))
    val tratteggio = Stroke(
        width = 2.5f * dp,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * dp, 6f * dp)),
    )
    val bordo = if (aiutoColore) lerp(colore, Color.White, 0.4f) else Color.White.copy(alpha = 0.9f)
    if (slot.tipo == TipoForma.CUBO) {
        // Gli spigoli interni distinguono il cubo da un esagono
        for (f in facceCubo(c, r)) drawPath(f, bordo.copy(alpha = 0.45f), style = Stroke(1.5f * dp))
    }
    drawPath(sagoma, bordo, style = tratteggio)
    if (slot.pesante) etichettaPesante(c, r, Color.White.copy(alpha = 0.6f))
}

/**
 * Una forma "solida", con sfumatura, bordo bianco e ombra quando è sollevata.
 * @param tenuto la forma è in mano: viene ingrandita e proiettata un'ombra
 * @param evidenziato una mano aperta ci passa sopra: si può prendere
 */
fun DrawScope.disegnaPezzo(pezzo: Pezzo, tenuto: Boolean, evidenziato: Boolean, dp: Float) {
    val rimbalzo = sin(pezzo.rimbalzo * PI).toFloat() * 0.18f
    val scala = 1f + (if (tenuto) 0.12f else 0f) + rimbalzo
    val r = pezzo.raggio * scala
    val tremolio = if (pezzo.scossa > 0f) sin(pezzo.scossa * 90f) * pezzo.scossa * 40f * dp / 2f else 0f
    val c = pezzo.posizione.offset() + Offset(tremolio, 0f)
    val base = Color(pezzo.tipo.colore)
    val chiaro = lerp(base, Color.White, 0.5f)
    val scuro = lerp(base, Color.Black, 0.35f)
    val sagoma = percorsoForma(pezzo.tipo, c, r)

    if (tenuto) {
        translate(4f * dp, 8f * dp) { drawPath(sagoma, Color.Black.copy(alpha = 0.35f)) }
    }
    if (evidenziato) {
        drawPath(sagoma, Color.White.copy(alpha = 0.55f), style = Stroke(8f * dp))
    }
    if (pezzo.tipo == TipoForma.CUBO) {
        val (sopra, destra, sinistra) = facceCubo(c, r)
        drawPath(sopra, chiaro)
        drawPath(sinistra, base)
        drawPath(destra, scuro)
        for (f in listOf(sopra, destra, sinistra)) drawPath(f, Color.White.copy(alpha = 0.5f), style = Stroke(1.5f * dp))
    } else {
        drawPath(
            sagoma,
            Brush.radialGradient(
                colors = listOf(chiaro, base, scuro),
                center = c - Offset(r * 0.35f, r * 0.45f),
                radius = r * 1.9f,
            ),
        )
        // Riflesso in alto a sinistra
        withTransform({ clipPath(sagoma) }) {
            drawCircle(Color.White.copy(alpha = 0.25f), r * 0.45f, c - Offset(r * 0.45f, r * 0.55f))
        }
    }
    val bordo = if (pezzo.pesante) Color(0xFF37474F) else Color.White
    drawPath(sagoma, bordo, style = Stroke((if (tenuto || pezzo.pesante) 3.5f else 2.5f) * dp))
    if (pezzo.pesante) etichettaPesante(c, r, Color.White)

    // Conto alla rovescia della trasformazione: l'arco si svuota, e diventa rosso alla fine
    if (pezzo.periodoCambio > 0f && !pezzo.incastrato) {
        val resto = (pezzo.tempoAlCambio / pezzo.periodoCambio).coerceIn(0f, 1f)
        val urgente = pezzo.tempoAlCambio < 1f
        val raggioArco = r * 1.3f + 4f * dp
        drawArc(
            color = if (urgente) Color(0xFFFF5252) else Color.White.copy(alpha = 0.85f),
            startAngle = -90f,
            sweepAngle = 360f * resto,
            useCenter = false,
            topLeft = c - Offset(raggioArco, raggioArco),
            size = androidx.compose.ui.geometry.Size(raggioArco * 2f, raggioArco * 2f),
            style = Stroke((if (urgente) 3.5f else 2.5f) * dp, cap = StrokeCap.Round),
        )
    }
}
