package it.example.frattalogic.ui

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.example.frattalogic.engine.Esito
import it.example.frattalogic.engine.GameViewModel
import it.example.frattalogic.engine.PuzzleOption
import it.example.frattalogic.ui.theme.AccentoAmbra
import it.example.frattalogic.ui.theme.AccentoTeal
import it.example.frattalogic.ui.theme.Corretto
import it.example.frattalogic.ui.theme.Sbagliato
import it.example.frattalogic.ui.theme.SfondoPannello

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val stato by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        SfondoFrattaleAnimato(difficolta = stato.difficolta)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BarraStato(punteggio = stato.punteggio, streak = stato.streak, record = stato.recordStreak)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stato.puzzle.istruzioni,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (stato.puzzle.sequenzaData.isNotEmpty()) {
                RigaSequenza(stato.puzzle.sequenzaData)
                Spacer(modifier = Modifier.height(24.dp))
            }

            GrigliaOpzioni(
                opzioni = stato.puzzle.opzioni,
                indiceSelezionato = stato.indiceSelezionato,
                indiceCorretto = stato.puzzle.indiceCorretto,
                esito = stato.esito,
                abilitato = stato.puoRispondere,
                onScegli = viewModel::rispondi,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BarraStato(punteggio: Int, streak: Int, record: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatoTesto("Punteggio", punteggio.toString())
        StatoTesto("Serie", streak.toString())
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
private fun RigaSequenza(sequenza: List<PuzzleOption>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        sequenza.forEach { opzione ->
            OpzioneCard(
                opzione = opzione,
                modifier = Modifier.weight(1f),
                sfondo = SfondoPannello,
                bordo = null
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(SfondoPannello.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text("?", style = MaterialTheme.typography.headlineMedium, color = AccentoAmbra)
        }
    }
}

@Composable
private fun GrigliaOpzioni(
    opzioni: List<PuzzleOption>,
    indiceSelezionato: Int?,
    indiceCorretto: Int,
    esito: Esito,
    abilitato: Boolean,
    onScegli: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colonne = if (opzioni.size > 6) 3 else 2
    LazyVerticalGrid(
        columns = GridCells.Fixed(colonne),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(opzioni.size) { indice ->
            val opzione = opzioni[indice]
            val bordo = when {
                esito != Esito.NESSUNO && indice == indiceCorretto -> Corretto
                esito == Esito.SBAGLIATO && indice == indiceSelezionato -> Sbagliato
                else -> null
            }
            OpzioneCard(
                opzione = opzione,
                modifier = Modifier.clickable(enabled = abilitato) { onScegli(indice) },
                sfondo = SfondoPannello,
                bordo = bordo
            )
        }
    }
}

@Composable
private fun OpzioneCard(
    opzione: PuzzleOption,
    modifier: Modifier = Modifier,
    sfondo: Color,
    bordo: Color?
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(sfondo)
            .then(if (bordo != null) Modifier.border(3.dp, bordo, RoundedCornerShape(12.dp)) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when (opzione) {
            is PuzzleOption.Numero -> Text(
                text = opzione.valore.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            is PuzzleOption.Frattale -> Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {
                drawFractal(opzione.spec, size.minDimension, Offset(size.width / 2f, size.height / 2f))
            }
        }
    }
}

@Composable
private fun SfondoFrattaleAnimato(difficolta: Int) {
    val transizione = rememberInfiniteTransition(label = "sfondo")
    val rotazione by transizione.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 40000, easing = LinearEasing)
        ),
        label = "rotazioneSfondo"
    )
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.15f)
    ) {
        val spec = FractalSpec(
            kind = FractalKind.KOCH,
            depth = 3,
            rotationDeg = rotazione,
            hue = 190f + difficolta * 4f
        )
        drawFractal(spec, size.minDimension * 1.3f, Offset(size.width / 2f, size.height / 2f))
    }
}
