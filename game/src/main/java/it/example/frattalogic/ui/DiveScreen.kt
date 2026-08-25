package it.example.frattalogic.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import it.example.frattalogic.engine.Camera
import it.example.frattalogic.engine.DiveViewModel
import it.example.frattalogic.engine.Esito
import it.example.frattalogic.ui.theme.AccentoTeal
import it.example.frattalogic.ui.theme.Corretto
import it.example.frattalogic.ui.theme.Sbagliato
import it.example.frattalogic.ui.theme.SfondoPannello
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun DiveScreen(viewModel: DiveViewModel) {
    val stato by viewModel.state.collectAsState()
    val camera = stato.camera

    Box(modifier = Modifier.fillMaxSize()) {
        SfondoFrattaleAnimato(camera.nucleo)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BarraStato(punteggio = stato.punteggio, profondita = camera.profondita, record = stato.profonditaMassima)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Trova l'elemento dissonante per scendere più in profondità",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            AnelloNodi(
                camera = camera,
                indiceSelezionato = stato.indiceSelezionato,
                esito = stato.esito,
                abilitato = stato.puoToccare,
                onTocca = viewModel::tocca,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BarraStato(punteggio: Int, profondita: Int, record: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatoTesto("Punteggio", punteggio.toString())
        StatoTesto("Profondità", profondita.toString())
        StatoTesto("Record", record.toString())
    }
}

@Composable
private fun StatoTesto(etichetta: String, valore: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = valore, style = MaterialTheme.typography.headlineMedium, color = AccentoTeal)
        Text(
            text = etichetta,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun AnelloNodi(
    camera: Camera,
    indiceSelezionato: Int?,
    esito: Esito,
    abilitato: Boolean,
    onTocca: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scalaTransizione = remember { Animatable(1f) }
    LaunchedEffect(camera.profondita, esito) {
        when (esito) {
            Esito.RISOLTO -> {
                scalaTransizione.animateTo(1.35f, tween(280))
                scalaTransizione.snapTo(1f)
            }
            Esito.ROTTURA -> {
                scalaTransizione.animateTo(0.9f, tween(110))
                scalaTransizione.animateTo(1f, tween(160))
            }
            Esito.NESSUNO -> Unit
        }
    }

    BoxWithConstraints(
        modifier = modifier.graphicsLayer(
            scaleX = scalaTransizione.value,
            scaleY = scalaTransizione.value
        ),
        contentAlignment = Alignment.Center
    ) {
        val raggioPx = with(LocalDensity.current) {
            (minOf(maxWidth, maxHeight) / 2 - 44.dp).toPx().coerceAtLeast(0f)
        }

        Canvas(modifier = Modifier.size(96.dp)) {
            drawFractal(camera.nucleo, size.minDimension, Offset(size.width / 2f, size.height / 2f))
        }

        camera.nodi.forEach { nodo ->
            val angoloRad = Math.toRadians(nodo.angoloDeg.toDouble() - 90.0)
            val offsetX = (raggioPx * cos(angoloRad)).toFloat()
            val offsetY = (raggioPx * sin(angoloRad)).toFloat()

            val bordo = when {
                esito != Esito.NESSUNO && nodo.indice == camera.indiceDissonante -> Corretto
                esito == Esito.ROTTURA && nodo.indice == indiceSelezionato -> Sbagliato
                else -> null
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(SfondoPannello)
                    .then(if (bordo != null) Modifier.border(3.dp, bordo, CircleShape) else Modifier)
                    .clickable(enabled = abilitato) { onTocca(nodo.indice) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    drawFractal(nodo.spec, size.minDimension, Offset(size.width / 2f, size.height / 2f))
                }
            }
        }
    }
}

@Composable
private fun SfondoFrattaleAnimato(nucleo: FractalSpec) {
    val transizione = rememberInfiniteTransition(label = "sfondo")
    val rotazione by transizione.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 50000, easing = LinearEasing)
        ),
        label = "rotazioneSfondo"
    )
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.15f)
    ) {
        val spec = nucleo.copy(rotationDeg = nucleo.rotationDeg + rotazione)
        drawFractal(spec, size.minDimension * 1.3f, Offset(size.width / 2f, size.height / 2f))
    }
}
