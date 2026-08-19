package it.freebimbogames.app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.freebimbogames.app.ui.theme.SfondoChiaro
import kotlinx.coroutines.delay
import kotlin.random.Random

// ---------------------------------------------------------------------------------
// Ritmo Mostruoso: un "Simon Says" con 4 tasti mostruosi. A differenza degli altri
// giochi della suite non ha livelli fissi: la sequenza da ripetere si allunga di un
// passo ogni volta che il giocatore la ripete tutta giusta, come nel gioco originale,
// finché non sbaglia. Il "punteggio" è la lunghezza massima raggiunta.
// ---------------------------------------------------------------------------------

private enum class SchermataRitmo { HOME, GIOCO, FINE }

@Composable
fun AppRitmo(onTornaAiGiochi: () -> Unit) {
    var schermata by remember { mutableStateOf(SchermataRitmo.HOME) }
    var recordSessione by remember { mutableStateOf(0) }
    var ultimaLunghezza by remember { mutableStateOf(0) }

    when (schermata) {
        SchermataRitmo.HOME -> SchermataHomeRitmo(
            record = recordSessione,
            onGioca = { schermata = SchermataRitmo.GIOCO },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataRitmo.GIOCO -> SchermataGiocoRitmo(
            onErrore = { lunghezzaRaggiunta ->
                ultimaLunghezza = lunghezzaRaggiunta
                recordSessione = maxOf(recordSessione, lunghezzaRaggiunta)
                schermata = SchermataRitmo.FINE
            }
        )

        SchermataRitmo.FINE -> SchermataFineRitmo(
            lunghezza = ultimaLunghezza,
            record = recordSessione,
            onRiprova = { schermata = SchermataRitmo.GIOCO }
        )
    }
}

@Composable
fun SchermataHomeRitmo(record: Int, onGioca: () -> Unit, onTornaAiGiochi: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0x33000000))
                .clickable(onClick = onTornaAiGiochi),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "⬅️", fontSize = 22.sp)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🎵🐙", fontSize = 72.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Ritmo Mostruoso".maiuscolo(),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Guarda la sequenza di mostri che si accendono e ripetila toccandoli nello stesso ordine!".maiuscolo(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            if (record > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Record: $record".maiuscolo(), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onGioca,
                shape = RoundedCornerShape(50),
                modifier = Modifier.size(width = 220.dp, height = 64.dp)
            ) {
                Text(text = "🎮 Gioca!".maiuscolo(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SchermataGiocoRitmo(onErrore: (Int) -> Unit) {
    var sequenza by remember { mutableStateOf(listOf(Random.nextInt(elencoTastiRitmo.size))) }
    var inMostra by remember { mutableStateOf(true) }
    var indiceEvidenziato by remember { mutableStateOf(-1) }
    var inputGiocatore by remember { mutableStateOf<List<Int>>(emptyList()) }

    // Ad ogni nuova sequenza (più lunga di un passo) la si rimostra tutta da capo,
    // lampeggiando un tasto alla volta, prima di lasciare il turno al giocatore.
    LaunchedEffect(sequenza) {
        inMostra = true
        inputGiocatore = emptyList()
        delay(500)
        for (id in sequenza) {
            indiceEvidenziato = id
            delay(500)
            indiceEvidenziato = -1
            delay(250)
        }
        inMostra = false
    }

    fun onTocca(id: Int) {
        if (inMostra) return
        val posizione = inputGiocatore.size
        if (sequenza[posizione] != id) {
            onErrore(sequenza.size - 1)
            return
        }
        val nuovoInput = inputGiocatore + id
        inputGiocatore = nuovoInput
        if (nuovoInput.size == sequenza.size) {
            sequenza = sequenza + Random.nextInt(elencoTastiRitmo.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Sequenza: ${sequenza.size}".maiuscolo(), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = (if (inMostra) "Guarda bene..." else "Tocca nello stesso ordine!").maiuscolo(),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))

        elencoTastiRitmo.chunked(2).forEach { riga ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                riga.forEach { tasto ->
                    TastoRitmoVista(
                        tasto = tasto,
                        acceso = indiceEvidenziato == tasto.id,
                        abilitato = !inMostra,
                        modifier = Modifier.weight(1f),
                        onClick = { onTocca(tasto.id) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TastoRitmoVista(
    tasto: TastoRitmo,
    acceso: Boolean,
    abilitato: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val coloreBase = Color(tasto.colore)
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(enabled = abilitato, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (acceso) coloreBase else coloreBase.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = tasto.emoji, fontSize = 48.sp)
        }
    }
}

@Composable
fun SchermataFineRitmo(lunghezza: Int, record: Int, onRiprova: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = if (lunghezza >= record && lunghezza > 0) "🏆" else "😅", fontSize = 96.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Sei arrivato a $lunghezza!".maiuscolo(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Record: $record".maiuscolo(), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRiprova,
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(width = 240.dp, height = 64.dp)
        ) {
            Text(text = "🔁 Riprova".maiuscolo(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
