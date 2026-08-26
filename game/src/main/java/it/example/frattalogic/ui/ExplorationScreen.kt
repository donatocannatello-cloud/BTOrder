package it.example.frattalogic.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import it.example.frattalogic.engine.Camera
import it.example.frattalogic.engine.Esito
import it.example.frattalogic.engine.ExplorationViewModel
import it.example.frattalogic.engine.Fase
import it.example.frattalogic.engine.ImmersioneState
import it.example.frattalogic.ui.theme.AccentoTeal
import it.example.frattalogic.ui.theme.Corretto
import it.example.frattalogic.ui.theme.Sbagliato
import it.example.frattalogic.ui.theme.SfondoPannello
import it.example.frattalogic.ui.theme.SfondoProfondo
import it.example.frattalogic.ui.theme.TestoAttenuato
import it.example.frattalogic.ui.theme.TestoChiaro
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ExplorationScreen(viewModel: ExplorationViewModel) {
    val stato by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoProfondo)
            .onSizeChanged { dimensione ->
                viewModel.impostaRisoluzione(dimensione.width, dimensione.height)
            }
    ) {
        ImmersioneCanvas(stato)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            IntestazioneImmersione(stato)
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                JoystickControllo(onCambia = { _, y -> viewModel.impostaDiscesa(y) })
                JoystickControllo(onCambia = viewModel::impostaPan)
            }
        }

        val cameraBonus = stato.cameraBonus
        if (stato.fase == Fase.EVENTO_BONUS && cameraBonus != null) {
            EventoBonusOverlay(
                camera = cameraBonus,
                indiceSelezionato = stato.indiceSelezionatoBonus,
                esito = stato.esitoBonus,
                onTocca = viewModel::toccaBonus
            )
        }
    }
}

@Composable
private fun ImmersioneCanvas(stato: ImmersioneState) {
    val pixelArray = stato.pixel
    if (pixelArray == null || stato.larghezzaPixel <= 0 || stato.altezzaPixel <= 0) return

    val immagine = Bitmap
        .createBitmap(pixelArray, stato.larghezzaPixel, stato.altezzaPixel, Bitmap.Config.ARGB_8888)
        .asImageBitmap()

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawImage(
            image = immagine,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        )
    }
}

@Composable
private fun IntestazioneImmersione(stato: ImmersioneState) {
    Column {
        Text(
            text = "Immersione",
            style = MaterialTheme.typography.headlineMedium,
            color = AccentoTeal
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "Punteggio: ${stato.punteggio}",
                style = MaterialTheme.typography.labelLarge,
                color = TestoAttenuato
            )
            Text(
                text = "Profondità: ${stato.livelloZoom.toInt()}",
                style = MaterialTheme.typography.labelLarge,
                color = TestoAttenuato
            )
        }
    }
}

@Composable
private fun JoystickControllo(onCambia: (x: Float, y: Float) -> Unit) {
    val raggioBaseDp = 64.dp
    var offset by remember { mutableStateOf(Offset.Zero) }
    val raggioPx = with(LocalDensity.current) { raggioBaseDp.toPx() }

    Box(
        modifier = Modifier
            .size(raggioBaseDp * 2)
            .clip(CircleShape)
            .background(SfondoPannello.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val proposto = offset + dragAmount
                        val distanza = hypot(proposto.x, proposto.y)
                        offset = if (distanza > raggioPx) proposto * (raggioPx / distanza) else proposto
                        onCambia(offset.x / raggioPx, offset.y / raggioPx)
                    },
                    onDragEnd = {
                        offset = Offset.Zero
                        onCambia(0f, 0f)
                    },
                    onDragCancel = {
                        offset = Offset.Zero
                        onCambia(0f, 0f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(42.dp)
                .clip(CircleShape)
                .background(AccentoTeal.copy(alpha = 0.85f))
        )
    }
}

@Composable
private fun EventoBonusOverlay(
    camera: Camera,
    indiceSelezionato: Int?,
    esito: Esito,
    onTocca: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoProfondo.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "La corrente si increspa",
                style = MaterialTheme.typography.titleMedium,
                color = TestoChiaro
            )
            Text(
                text = "Trova l'elemento dissonante per stabilizzare la discesa",
                style = MaterialTheme.typography.bodyLarge,
                color = TestoAttenuato
            )
            Spacer(modifier = Modifier.height(24.dp))
            AnelloNodiBonus(
                camera = camera,
                indiceSelezionato = indiceSelezionato,
                esito = esito,
                abilitato = esito == Esito.NESSUNO,
                onTocca = onTocca,
                modifier = Modifier.size(320.dp)
            )
        }
    }
}

@Composable
private fun AnelloNodiBonus(
    camera: Camera,
    indiceSelezionato: Int?,
    esito: Esito,
    abilitato: Boolean,
    onTocca: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scalaTransizione = remember { Animatable(1f) }
    LaunchedEffect(camera, esito) {
        when (esito) {
            Esito.RISOLTO -> {
                scalaTransizione.animateTo(1.3f, tween(280))
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
            (minOf(maxWidth, maxHeight) / 2 - 40.dp).toPx().coerceAtLeast(0f)
        }

        Canvas(modifier = Modifier.size(88.dp)) {
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
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(SfondoPannello)
                    .then(if (bordo != null) Modifier.border(3.dp, bordo, CircleShape) else Modifier)
                    .clickable(enabled = abilitato) { onTocca(nodo.indice) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp)
                ) {
                    drawFractal(nodo.spec, size.minDimension, Offset(size.width / 2f, size.height / 2f))
                }
            }
        }
    }
}
