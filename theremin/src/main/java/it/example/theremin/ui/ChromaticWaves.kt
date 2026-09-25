package it.example.theremin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import it.example.theremin.audio.AudioEngine
import it.example.theremin.audio.Note
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

private const val NUMERO_ONDE = 9
private const val CAMPIONI_OSCILLOSCOPIO = 1024

/**
 * Flusso di onde cromatiche sincronizzato con il suono del theremin.
 *
 * - la **tinta** di base segue la nota (Do = rosso, … ogni semitono ruota di 30°), e le onde
 *   si distribuiscono a ventaglio attorno a essa sulla ruota dei colori;
 * - l'**ampiezza** delle onde e la luminosità seguono il volume effettivo del synth;
 * - la **densità** (numero di creste) e la **velocità** di scorrimento crescono con l'altezza;
 * - al centro scorre l'oscilloscopio con la forma d'onda reale appena suonata.
 */
@Composable
fun ChromaticWaves(engine: AudioEngine, modifier: Modifier = Modifier) {
    var tempo by remember { mutableFloatStateOf(0f) }
    val stato = remember { StatoOnde() }

    LaunchedEffect(Unit) {
        var ultimo = 0L
        while (true) {
            withFrameNanos { ora ->
                val dt = if (ultimo == 0L) 0f else ((ora - ultimo) / 1e9f).coerceAtMost(0.1f)
                ultimo = ora
                stato.aggiorna(engine, dt)
                tempo += dt
            }
        }
    }

    Canvas(modifier) {
        // Leggere `tempo` qui invalida solo la fase di disegno a ogni fotogramma, senza ricomporre
        val t = tempo
        disegnaSfondo(stato)
        disegnaOnde(stato, t)
        disegnaOscilloscopio(engine, stato)
    }
}

/** Valori audio smussati per il disegno, così la grafica "respira" invece di scattare. */
private class StatoOnde {
    var midi = 60f
    var volume = 0f
    var fase = 0f
    var tinta = 0f
    val campioni = FloatArray(CAMPIONI_OSCILLOSCOPIO)

    fun aggiorna(engine: AudioEngine, dt: Float) {
        val synth = engine.synth
        val midiReale = Note.hzToMidi(synth.frequenzaCorrente.coerceAtLeast(20f))
        midi += (midiReale - midi) * (dt * 10f).coerceAtMost(1f)
        volume += (synth.volumeCorrente - volume) * (dt * 8f).coerceAtMost(1f)

        // La tinta segue la nota lungo l'arco più breve della ruota dei colori
        var delta = Note.tinta(midi) - tinta
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        tinta = (tinta + delta * (dt * 6f).coerceAtMost(1f) + 360f) % 360f

        // Le onde scorrono più veloci sulle note acute e anche a volume zero si muovono piano
        val altezza = ((midi - Note.MIDI_MIN) / (Note.MIDI_MAX - Note.MIDI_MIN)).coerceIn(0f, 1f)
        fase += dt * (0.6f + altezza * 3.5f) * (0.35f + volume)

        engine.copiaStorico(campioni)
    }

    val altezzaNormalizzata: Float
        get() = ((midi - Note.MIDI_MIN) / (Note.MIDI_MAX - Note.MIDI_MIN)).coerceIn(0f, 1f)
}

private fun colore(tinta: Float, saturazione: Float, luminosita: Float, alpha: Float): Color =
    Color.hsv(((tinta % 360f) + 360f) % 360f, saturazione.coerceIn(0f, 1f), luminosita.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))

private fun DrawScope.disegnaSfondo(s: StatoOnde) {
    drawRect(Color.Black)
    drawRect(
        Brush.radialGradient(
            colors = listOf(
                colore(s.tinta, 0.9f, 0.55f, 0.10f + 0.45f * s.volume),
                colore(s.tinta + 60f, 0.9f, 0.25f, 0.05f + 0.2f * s.volume),
                Color.Transparent,
            ),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = max(size.width, size.height) * (0.55f + 0.25f * s.volume),
        )
    )
}

private fun DrawScope.disegnaOnde(s: StatoOnde, t: Float) {
    val w = size.width
    val h = size.height
    val passo = 8f
    val creste = 1.2f + s.altezzaNormalizzata * 4.5f
    val ampiezzaBase = h * (0.025f + 0.11f * s.volume)

    for (i in 0 until NUMERO_ONDE) {
        val rel = i.toFloat() / (NUMERO_ONDE - 1) // 0..1 dall'alto al basso
        val centroY = h * (0.12f + 0.76f * rel)
        val sfasamento = i * 0.9f
        val k = 2f * PI.toFloat() * creste * (1f + 0.15f * sin(i * 1.7f)) / w
        val ampiezza = ampiezzaBase * (0.6f + 0.4f * sin(t * 0.7f + i))
        val tintaOnda = s.tinta + (rel - 0.5f) * 150f + 25f * sin(t * 0.3f + i)

        val path = Path()
        var x = 0f
        while (x <= w + passo) {
            // Somma di due sinusoidi a velocità diverse: le creste si inseguono e si intrecciano
            val y = centroY +
                ampiezza * sin(k * x - s.fase * 2.2f + sfasamento) +
                ampiezza * 0.45f * sin(k * 2.3f * x + s.fase * 1.3f + sfasamento * 2f)
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += passo
        }

        val pennello = Brush.horizontalGradient(
            listOf(
                colore(tintaOnda - 40f, 1f, 1f, 1f),
                colore(tintaOnda, 1f, 1f, 1f),
                colore(tintaOnda + 40f, 1f, 1f, 1f),
            )
        )
        val intensita = 0.25f + 0.75f * s.volume
        // Alone largo e tenue + filo sottile brillante, sommati in modo additivo
        drawPath(path, pennello, alpha = 0.18f * intensita, style = Stroke(width = 18f + 30f * s.volume, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
        drawPath(path, pennello, alpha = 0.85f * intensita, style = Stroke(width = 3f + 3f * s.volume, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }
}

/** Disegna la forma d'onda reale, allineata al primo passaggio per lo zero per restare stabile. */
private fun DrawScope.disegnaOscilloscopio(engine: AudioEngine, s: StatoOnde) {
    if (s.volume < 0.01f) return
    val c = s.campioni
    val periodo = (engine.sampleRate / engine.synth.frequenzaCorrente.coerceAtLeast(20f)).toInt()
    val finestra = (periodo * 3).coerceIn(64, CAMPIONI_OSCILLOSCOPIO / 2)

    var inizio = 0
    for (i in 1 until CAMPIONI_OSCILLOSCOPIO - finestra) {
        if (c[i - 1] < 0f && c[i] >= 0f) { inizio = i; break }
    }

    val w = size.width
    val cy = size.height / 2f
    val scala = size.height * 0.22f
    val path = Path()
    for (j in 0 until finestra) {
        val x = w * j / (finestra - 1)
        val y = cy - c[inizio + j] * scala
        if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val alone = colore(s.tinta + 180f, 0.35f, 1f, 1f)
    drawPath(path, alone, alpha = 0.25f * s.volume, style = Stroke(width = 22f), blendMode = BlendMode.Plus)
    drawPath(path, Color.White, alpha = 0.9f * s.volume, style = Stroke(width = 3f), blendMode = BlendMode.Plus)
}
