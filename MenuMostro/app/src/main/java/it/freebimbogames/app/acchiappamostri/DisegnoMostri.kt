package it.freebimbogames.app.acchiappamostri

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.sin

fun Punto.offset() = Offset(x, y)

private val pennelloEmoji = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
}

/** Disegna un'emoji centrata in [c], grande circa [diametro]. */
private fun DrawScope.emoji(testo: String, c: Offset, diametro: Float, alpha: Float = 1f) {
    pennelloEmoji.textSize = diametro
    pennelloEmoji.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
    // Paint.drawText si allinea alla base del testo: si sposta di un po' per centrarlo verticalmente
    drawContext.canvas.nativeCanvas.drawText(testo, c.x, c.y + diametro * 0.35f, pennelloEmoji)
}

/**
 * Una tana: più grande di un mostro e circondata da un alone colorato ben visibile, per non
 * essere confusa con un mostro da acchiappare. Dentro c'è un'anteprima sbiadita del mostro che
 * ci va infilato (più visibile nei primi livelli, come aiuto).
 */
fun DrawScope.disegnaSlot(slot: Slot, aiutoColore: Boolean, dp: Float) {
    val c = slot.centro.offset()
    val r = slot.raggio * 1.3f
    val colore = Color(slot.tipo.colore)
    // Alone grande e colorato: è questo, più che le dimensioni, a far riconoscere "è una tana"
    val raggioAlone = r * 1.6f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colore.copy(alpha = if (aiutoColore) 0.55f else 0.35f), colore.copy(alpha = 0f)),
            center = c,
            radius = raggioAlone,
        ),
        radius = raggioAlone,
        center = c,
    )
    drawCircle(Color.Black.copy(alpha = 0.5f), r, c)
    if (aiutoColore) drawCircle(colore.copy(alpha = 0.22f), r, c)
    emoji(slot.tipo.emoji, c, r * 1.1f, alpha = if (aiutoColore) 0.6f else 0.35f)
    val tratteggio = Stroke(
        width = 3.5f * dp,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * dp, 7f * dp)),
    )
    val bordo = if (aiutoColore) lerp(colore, Color.White, 0.4f) else Color.White.copy(alpha = 0.9f)
    drawCircle(bordo, r, c, style = tratteggio)
}

/**
 * Un mostro "vivo": la sua emoji su un alone del suo colore, con ombra quando è sollevato.
 * @param tenuto il mostro è in mano: viene ingrandito e proietta un'ombra
 * @param evidenziato la mano aperta ci passa sopra: si può prendere
 */
fun DrawScope.disegnaPezzo(pezzo: Pezzo, tenuto: Boolean, evidenziato: Boolean, dp: Float) {
    val rimbalzo = sin(pezzo.rimbalzo * PI).toFloat() * 0.18f
    val scala = 1f + (if (tenuto) 0.12f else 0f) + rimbalzo
    val r = pezzo.raggio * scala
    val c = pezzo.posizione.offset()
    val base = Color(pezzo.tipo.colore)

    if (tenuto) {
        translate(4f * dp, 8f * dp) { drawCircle(Color.Black.copy(alpha = 0.35f), r * 1.15f, c) }
    }
    if (evidenziato) {
        // Anello bianco ben marcato: è il segnale "ci sei sopra, pizzica per prenderlo"
        drawCircle(Color.White.copy(alpha = 0.85f), r * 1.3f + 10f * dp, c, style = Stroke(10f * dp))
    }
    // Alone colorato dietro all'emoji: si riconosce il tipo di mostro anche da lontano
    drawCircle(base.copy(alpha = 0.3f), r * 1.2f, c)
    emoji(pezzo.tipo.emoji, c, r * 2f)
    drawCircle(Color.White, r * 1.15f, c, style = Stroke((if (tenuto) 3.5f else 2.5f) * dp))

    // Conto alla rovescia della trasformazione: l'arco si svuota, e diventa rosso alla fine
    if (pezzo.periodoCambio > 0f && !pezzo.incastrato) {
        val resto = (pezzo.tempoAlCambio / pezzo.periodoCambio).coerceIn(0f, 1f)
        val urgente = pezzo.tempoAlCambio < 1f
        val raggioArco = r * 1.35f + 4f * dp
        drawArc(
            color = if (urgente) Color(0xFFFF5252) else Color.White.copy(alpha = 0.85f),
            startAngle = -90f,
            sweepAngle = 360f * resto,
            useCenter = false,
            topLeft = c - Offset(raggioArco, raggioArco),
            size = Size(raggioArco * 2f, raggioArco * 2f),
            style = Stroke((if (urgente) 3.5f else 2.5f) * dp, cap = StrokeCap.Round),
        )
    }
}
