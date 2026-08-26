package it.freebimbogames.app

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.freebimbogames.app.ui.theme.SfondoChiaro
import it.freebimbogames.app.ui.theme.palettePiatti
import kotlinx.coroutines.delay

private enum class SchermataMemory { HOME, GIOCO, FINE }

@Composable
fun AppMemory(onTornaAiGiochi: () -> Unit) {
    var schermata by remember { mutableStateOf(SchermataMemory.HOME) }
    var numeroLivello by remember { mutableStateOf(1) }
    var tentativiUltimoLivello by remember { mutableStateOf(0) }

    fun nuovaPartita() {
        numeroLivello = 1
        schermata = SchermataMemory.GIOCO
    }

    when (schermata) {
        SchermataMemory.HOME -> SchermataHomeMemory(
            onGioca = { nuovaPartita() },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataMemory.GIOCO -> SchermataGiocoMemory(
            livello = elencoLivelliMemory[numeroLivello - 1],
            onCompletato = { tentativi ->
                tentativiUltimoLivello = tentativi
                schermata = SchermataMemory.FINE
            },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataMemory.FINE -> SchermataFineMemory(
            numeroLivello = numeroLivello,
            tentativi = tentativiUltimoLivello,
            ultimoLivello = numeroLivello >= elencoLivelliMemory.size,
            onProssimoLivello = {
                numeroLivello += 1
                schermata = SchermataMemory.GIOCO
            },
            onNuovaPartita = { nuovaPartita() },
            onTornaAiGiochi = onTornaAiGiochi
        )
    }
}

@Composable
fun SchermataHomeMemory(onGioca: () -> Unit, onTornaAiGiochi: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.sfondo_memory_mostri),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Sfumatura scura solo nella metà inferiore: l'illustrazione resta ben
        // visibile in alto, il testo resta leggibile in basso.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1.0f to Color(0xE6000000)
                    )
                )
        )
        BottoneTornaAiGiochi(
            onClick = onTornaAiGiochi,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            sfondo = Color(0x99000000)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = "Memory dei Mostri".maiuscolo(),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Trova tutte le coppie di mostri uguali nel minor numero di tentativi!".maiuscolo(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
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
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SchermataGiocoMemory(livello: LivelloMemory, onCompletato: (Int) -> Unit, onTornaAiGiochi: () -> Unit) {
    var mazzo by remember(livello) { mutableStateOf(mazzoMemory(livello.coppie)) }
    var girate by remember(livello) { mutableStateOf<List<Int>>(emptyList()) }
    var tentativi by remember(livello) { mutableStateOf(0) }
    var bloccaInput by remember(livello) { mutableStateOf(false) }

    // Quando sono girate 2 carte: se sono uguali restano scoperte per sempre,
    // altrimenti dopo una breve pausa si girano di nuovo a faccia in giù.
    LaunchedEffect(girate) {
        if (girate.size == 2) {
            val (primoId, secondoId) = girate
            val prima = mazzo.first { it.id == primoId }
            val seconda = mazzo.first { it.id == secondoId }
            if (prima.emoji == seconda.emoji) {
                SuoniGioco.successo()
                mazzo = mazzo.map { if (it.id == primoId || it.id == secondoId) it.copy(abbinata = true) else it }
                girate = emptyList()
            } else {
                SuoniGioco.errore()
                bloccaInput = true
                delay(900)
                girate = emptyList()
                bloccaInput = false
            }
        }
    }

    val completato = mazzo.all { it.abbinata }
    LaunchedEffect(completato) {
        if (completato) onCompletato(tentativi)
    }

    fun onTocca(id: Int) {
        if (bloccaInput || completato) return
        val carta = mazzo.first { it.id == id }
        if (carta.abbinata || id in girate || girate.size >= 2) return
        SuoniGioco.tocco()
        girate = girate + id
        if (girate.size == 2) tentativi += 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
            .verticalScroll(rememberScrollState())
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
            Text(text = "🔁 $tentativi".maiuscolo(), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Trova tutte le coppie!".maiuscolo(), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))

        mazzo.chunked(livello.colonne).forEach { riga ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                riga.forEach { carta ->
                    CartaMemoryVista(
                        carta = carta,
                        girata = carta.abbinata || carta.id in girate,
                        modifier = Modifier.weight(1f),
                        onClick = { onTocca(carta.id) }
                    )
                }
                repeat(livello.colonne - riga.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CartaMemoryVista(
    carta: CartaMemory,
    girata: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (carta.abbinata) Modifier.border(3.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(enabled = !carta.abbinata, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (girata) palettePiatti[carta.id % palettePiatti.size] else Color(0xFFBDBDBD)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = if (girata) carta.emoji else "❓", fontSize = 30.sp)
        }
    }
}

@Composable
fun SchermataFineMemory(
    numeroLivello: Int,
    tentativi: Int,
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
                        "Hai trovato tutte le coppie di ogni livello! Che memoria!"
                    } else {
                        "Livello $numeroLivello superato!"
                    }
                    ).maiuscolo(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tentativi usati: $tentativi".maiuscolo(),
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
