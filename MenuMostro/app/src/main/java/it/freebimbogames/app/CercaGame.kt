package it.freebimbogames.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.freebimbogames.app.ui.theme.SfondoChiaro
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------
// Il Mostro Cerca: la griglia si riempie di icone tutte uguali tranne una, il
// "bersaglio" da trovare prima che scada il tempo. Come Memory e Monster Parking ha
// livelli fissi: la griglia cresce di livello in livello e ad ogni livello bisogna
// trovare il bersaglio più volte di seguito.
// ---------------------------------------------------------------------------------

private const val NUMERO_RICERCHE = 4
private const val LARGHEZZA_GRIGLIA_CERCA = 320

private enum class SchermataCerca { HOME, GIOCO, FINE }

@Composable
fun AppCerca(onTornaAiGiochi: () -> Unit) {
    var schermata by remember { mutableStateOf(SchermataCerca.HOME) }
    var numeroLivello by remember { mutableStateOf(1) }
    var tempoUltimoLivelloMillis by remember { mutableStateOf(0L) }

    fun nuovaPartita() {
        numeroLivello = 1
        schermata = SchermataCerca.GIOCO
    }

    when (schermata) {
        SchermataCerca.HOME -> SchermataHomeCerca(
            onGioca = { nuovaPartita() },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataCerca.GIOCO -> SchermataGiocoCerca(
            livello = elencoLivelliCerca[numeroLivello - 1],
            onLivelloCompletato = { tempoTotale ->
                tempoUltimoLivelloMillis = tempoTotale
                schermata = SchermataCerca.FINE
            },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataCerca.FINE -> SchermataFineCerca(
            numeroLivello = numeroLivello,
            tempoTotaleMillis = tempoUltimoLivelloMillis,
            ultimoLivello = numeroLivello >= elencoLivelliCerca.size,
            onProssimoLivello = {
                numeroLivello += 1
                schermata = SchermataCerca.GIOCO
            },
            onNuovaPartita = { nuovaPartita() },
            onTornaAiGiochi = onTornaAiGiochi
        )
    }
}

@Composable
fun SchermataHomeCerca(onGioca: () -> Unit, onTornaAiGiochi: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
    ) {
        BottoneTornaAiGiochi(
            onClick = onTornaAiGiochi,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "🔍👹", fontSize = 72.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Il Mostro Cerca".maiuscolo(),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Trova l'icona giusta tra tutte le altre prima che scada il tempo! La griglia cresce ad ogni livello.".maiuscolo(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
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
fun SchermataGiocoCerca(livello: LivelloCerca, onLivelloCompletato: (Long) -> Unit, onTornaAiGiochi: () -> Unit) {
    var numeroRicerca by remember(livello) { mutableStateOf(1) }
    var tempoTotaleMillis by remember(livello) { mutableStateOf(0L) }
    val limiteMillis = remember(livello) { limiteTempoMillis(livello.dimensione) }
    val griglia = remember(livello, numeroRicerca) { generaGrigliaCerca(livello.dimensione) }
    var tempoRimanenteMillis by remember(livello, numeroRicerca) { mutableStateOf(limiteMillis) }
    var esito by remember(livello, numeroRicerca) { mutableStateOf<Boolean?>(null) }

    // Conto alla rovescia: se il tempo scade prima che il bambino tocchi il bersaglio, esito = false.
    LaunchedEffect(livello, numeroRicerca) {
        while (tempoRimanenteMillis > 0 && esito == null) {
            delay(100)
            tempoRimanenteMillis = (tempoRimanenteMillis - 100).coerceAtLeast(0)
        }
        if (esito == null) {
            esito = false
            SuoniGioco.errore()
        }
    }

    // Dopo una breve pausa (per mostrare l'esito), si passa alla ricerca successiva o si finisce il livello.
    LaunchedEffect(esito) {
        val trovato = esito ?: return@LaunchedEffect
        delay(900)
        val tempoImpiegato = if (trovato) limiteMillis - tempoRimanenteMillis else limiteMillis
        val nuovoTotale = tempoTotaleMillis + tempoImpiegato
        if (numeroRicerca >= NUMERO_RICERCHE) {
            onLivelloCompletato(nuovoTotale)
        } else {
            tempoTotaleMillis = nuovoTotale
            numeroRicerca += 1
        }
    }

    fun onToccaCella(indice: Int) {
        if (esito != null) return
        if (indice == griglia.indiceBersaglio) {
            esito = true
            SuoniGioco.successo()
        } else {
            SuoniGioco.errore()
        }
    }

    val cellSize = (LARGHEZZA_GRIGLIA_CERCA / livello.dimensione).dp
    val secondiRimanenti = (tempoRimanenteMillis + 999) / 1000

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            BottoneTornaAiGiochi(onClick = onTornaAiGiochi)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Livello ${livello.numero}".maiuscolo(), style = MaterialTheme.typography.titleLarge)
            Text(text = "🔎 $numeroRicerca/$NUMERO_RICERCHE".maiuscolo(), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Trova: ${griglia.bersaglio}".maiuscolo(), style = MaterialTheme.typography.headlineSmall)
            Text(text = "⏱️ ${secondiRimanenti}s".maiuscolo(), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column {
            for (riga in 0 until livello.dimensione) {
                Row {
                    for (colonna in 0 until livello.dimensione) {
                        val indice = riga * livello.dimensione + colonna
                        val evidenziata = indice == griglia.indiceBersaglio && esito != null
                        CellaCerca(
                            size = cellSize,
                            emoji = griglia.icone[indice],
                            evidenziata = evidenziata,
                            onClick = { onToccaCella(indice) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        when (esito) {
            true -> Text(text = "Trovato! 🎉".maiuscolo(), style = MaterialTheme.typography.titleLarge)
            false -> Text(text = "Tempo scaduto! Era qui 👆".maiuscolo(), style = MaterialTheme.typography.titleLarge)
            null -> Text(text = "Tocca l'icona giusta!".maiuscolo(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CellaCerca(
    size: Dp,
    emoji: String,
    evidenziata: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(size)
            .padding(1.dp)
            .then(if (evidenziata) Modifier.border(3.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (evidenziata) Color(0xFFFFD54F) else Color(0xFFE0E0E0)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = emoji, fontSize = (size.value * 0.5f).sp)
        }
    }
}

@Composable
fun SchermataFineCerca(
    numeroLivello: Int,
    tempoTotaleMillis: Long,
    ultimoLivello: Boolean,
    onProssimoLivello: () -> Unit,
    onNuovaPartita: () -> Unit,
    onTornaAiGiochi: () -> Unit
) {
    LaunchedEffect(Unit) { SuoniGioco.vittoria() }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SfondoChiaro)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = if (ultimoLivello) "🏆" else "🎉", fontSize = 96.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = (
                    if (ultimoLivello) {
                        "Hai superato tutti i livelli! Che occhio di falco!"
                    } else {
                        "Livello $numeroLivello superato!"
                    }
                    ).maiuscolo(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tempo impiegato: ${tempoTotaleMillis / 1000} s".maiuscolo(),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            if (ultimoLivello) {
                Button(
                    onClick = onNuovaPartita,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(width = 240.dp, height = 64.dp)
                ) {
                    Text(text = "🔁 Nuova partita".maiuscolo(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onProssimoLivello,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(width = 240.dp, height = 64.dp)
                ) {
                    Text(text = "➡️ Prossimo livello".maiuscolo(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        BottoneTornaAiGiochi(
            onClick = onTornaAiGiochi,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
    }
}
