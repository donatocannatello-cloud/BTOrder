package it.example.frattalogic.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Tutte le forme del gioco sono frattali generati ricorsivamente al volo (nessuna
 * immagine bitmap): un albero ricorsivo, il triangolo di Sierpinski e il fiocco di
 * Koch. Ogni [FractalSpec] descrive la "regola" con cui una figura è stata generata
 * (profondità di ricorsione, rotazione, tonalità) — è proprio questa regola che i
 * puzzle logici chiedono di indovinare.
 */
enum class FractalKind { TREE, SIERPINSKI, KOCH }

data class FractalSpec(
    val kind: FractalKind,
    val depth: Int,
    val rotationDeg: Float = 0f,
    val hue: Float = 180f
)

fun DrawScope.drawFractal(spec: FractalSpec, sizePx: Float, center: Offset) {
    rotate(degrees = spec.rotationDeg, pivot = center) {
        when (spec.kind) {
            FractalKind.TREE -> drawFractalTree(
                start = Offset(center.x, center.y + sizePx * 0.42f),
                length = sizePx * 0.34f,
                angle = -90f,
                depth = spec.depth.coerceIn(1, 8),
                hue = spec.hue
            )

            FractalKind.SIERPINSKI -> drawSierpinski(
                p1 = Offset(center.x, center.y - sizePx * 0.42f),
                p2 = Offset(center.x - sizePx * 0.42f, center.y + sizePx * 0.32f),
                p3 = Offset(center.x + sizePx * 0.42f, center.y + sizePx * 0.32f),
                depth = spec.depth.coerceIn(0, 6),
                hue = spec.hue
            )

            FractalKind.KOCH -> drawKochSnowflake(
                center = center,
                radius = sizePx * 0.4f,
                depth = spec.depth.coerceIn(0, 4),
                hue = spec.hue
            )
        }
    }
}

private fun DrawScope.drawFractalTree(start: Offset, length: Float, angle: Float, depth: Int, hue: Float) {
    if (depth == 0 || length < 3f) return
    val rad = angle * PI.toFloat() / 180f
    val end = Offset(start.x + length * cos(rad), start.y + length * sin(rad))
    drawLine(
        color = hueColor(hue + depth * 6f),
        start = start,
        end = end,
        strokeWidth = (depth.toFloat() * 0.9f).coerceAtLeast(1.5f)
    )
    val nextLength = length * 0.74f
    drawFractalTree(end, nextLength, angle - 26f, depth - 1, hue)
    drawFractalTree(end, nextLength, angle + 26f, depth - 1, hue)
    if (depth % 3 == 0) {
        drawFractalTree(end, nextLength * 0.8f, angle, depth - 1, hue)
    }
}

private fun DrawScope.drawSierpinski(p1: Offset, p2: Offset, p3: Offset, depth: Int, hue: Float) {
    if (depth == 0) {
        val path = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            close()
        }
        drawPath(path, color = hueColor(hue + (p1.x % 40f) - 20f))
        return
    }
    val m12 = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    val m23 = Offset((p2.x + p3.x) / 2f, (p2.y + p3.y) / 2f)
    val m31 = Offset((p3.x + p1.x) / 2f, (p3.y + p1.y) / 2f)
    drawSierpinski(p1, m12, m31, depth - 1, hue)
    drawSierpinski(m12, p2, m23, depth - 1, hue)
    drawSierpinski(m31, m23, p3, depth - 1, hue)
}

private fun DrawScope.drawKochSnowflake(center: Offset, radius: Float, depth: Int, hue: Float) {
    val points = (0 until 3).map { i ->
        val angle = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / 3f
        Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
    }
    val segments = listOf(points[0] to points[1], points[1] to points[2], points[2] to points[0])
    val path = Path()
    segments.forEachIndexed { index, (a, b) ->
        val kochPoints = kochSegment(a, b, depth)
        if (index == 0) path.moveTo(kochPoints.first().x, kochPoints.first().y)
        kochPoints.drop(1).forEach { path.lineTo(it.x, it.y) }
    }
    path.close()
    drawPath(path, color = hueColor(hue, lightness = 0.22f))
    drawPath(path, color = hueColor(hue), style = Stroke(width = 2.5f))
}

private fun kochSegment(a: Offset, b: Offset, depth: Int): List<Offset> {
    if (depth == 0) return listOf(a, b)
    val dx = (b.x - a.x) / 3f
    val dy = (b.y - a.y) / 3f
    val p1 = Offset(a.x + dx, a.y + dy)
    val p3 = Offset(a.x + 2 * dx, a.y + 2 * dy)
    val segLen = hypot(dx, dy)
    val angle = atan2(dy, dx) - PI.toFloat() / 3f
    val p2 = Offset(p1.x + segLen * cos(angle), p1.y + segLen * sin(angle))
    val left = kochSegment(a, p1, depth - 1)
    val mid1 = kochSegment(p1, p2, depth - 1)
    val mid2 = kochSegment(p2, p3, depth - 1)
    val right = kochSegment(p3, b, depth - 1)
    return left + mid1.drop(1) + mid2.drop(1) + right.drop(1)
}

private fun hueColor(hue: Float, saturation: Float = 0.62f, lightness: Float = 0.6f): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = lightness - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r1 + m, g1 + m, b1 + m)
}
