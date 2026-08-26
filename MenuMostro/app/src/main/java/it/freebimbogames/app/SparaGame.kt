package it.freebimbogames.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.freebimbogames.app.ui.theme.SfondoChiaro
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------
// Spara ai Mostri: un mini sparatutto da luna park, non violento. Un razzo in basso si
// muove dove si tocca lo schermo e spara una stellina; i mostri-palloncino scendono
// dall'alto e "scoppiano" quando vengono colpiti. Come Ritmo Mostruoso, non ha una
// lista di livelli fissi: è una partita continua che finisce quando si perdono tutte
// le vite, con difficoltà che cresce col punteggio.
// ---------------------------------------------------------------------------------

private const val VITE_INIZIALI = 3
private const val MOLTIPLICATORE_DURATA_NANOS = 6_000_000_000L

private enum class SchermataSpara { HOME, GIOCO, FINE }

@Composable
fun AppSpara(onTornaAiGiochi: () -> Unit) {
    var schermata by remember { mutableStateOf(SchermataSpara.HOME) }
    var recordSessione by remember { mutableStateOf(0) }
    var ultimoPunteggio by remember { mutableStateOf(0) }

    when (schermata) {
        SchermataSpara.HOME -> SchermataHomeSpara(
            record = recordSessione,
            onGioca = { schermata = SchermataSpara.GIOCO },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataSpara.GIOCO -> SchermataGiocoSpara(
            onGameOver = { punteggio ->
                ultimoPunteggio = punteggio
                recordSessione = maxOf(recordSessione, punteggio)
                schermata = SchermataSpara.FINE
            },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataSpara.FINE -> SchermataFineSpara(
            punteggio = ultimoPunteggio,
            record = recordSessione,
            onRiprova = { schermata = SchermataSpara.GIOCO },
            onTornaAiGiochi = onTornaAiGiochi
        )
    }
}

@Composable
fun SchermataHomeSpara(record: Int, onGioca: () -> Unit, onTornaAiGiochi: () -> Unit) {
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
            Text(text = "🚀👹", fontSize = 72.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Spara ai Mostri".maiuscolo(),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tocca lo schermo per muovere il razzo e sparare stelline! Fai scoppiare i mostri prima che scendano troppo, o perdi una vita.".maiuscolo(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Attenzione: alcuni sono amici travestiti (😇🐰🦋)! Non colpirli, o perdi una vita. Il mostro speciale 🌟 raddoppia i punti per qualche secondo!".maiuscolo(),
                style = MaterialTheme.typography.bodyMedium,
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
fun SchermataGiocoSpara(onGameOver: (Int) -> Unit, onTornaAiGiochi: () -> Unit) {
    var mostri by remember { mutableStateOf(listOf<MostroVolante>()) }
    var proiettili by remember { mutableStateOf(listOf<Proiettile>()) }
    var effetti by remember { mutableStateOf(listOf<EffettoPop>()) }
    var punteggio by remember { mutableStateOf(0) }
    var vite by remember { mutableStateOf(VITE_INIZIALI) }
    var prossimoId by remember { mutableStateOf(0) }
    var moltiplicatoreScadenzaNanos by remember { mutableStateOf(0L) }
    var moltiplicatoreSecondi by remember { mutableStateOf(0) }

    fun nuovoId(): Int {
        prossimoId += 1
        return prossimoId
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottoneTornaAiGiochi(onClick = onTornaAiGiochi)
            Text(text = "Livello ${livelloSpara(punteggio)}".maiuscolo(), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "⭐ $punteggio".maiuscolo(), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "❤️".repeat(vite))
                if (moltiplicatoreSecondi > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "🌟x2".maiuscolo(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val larghezzaPx = constraints.maxWidth.toFloat()
            val altezzaPx = constraints.maxHeight.toFloat()
            var cannoneX by remember { mutableStateOf(larghezzaPx / 2f) }

            LaunchedEffect(larghezzaPx, altezzaPx) {
                var ultimoSpawnMillis = 0L
                var ultimoTempoNanos = withFrameNanos { it }
                while (vite > 0) {
                    val tempoNanos = withFrameNanos { it }
                    val deltaSecondi = (tempoNanos - ultimoTempoNanos) / 1_000_000_000f
                    ultimoTempoNanos = tempoNanos
                    val tempoMillis = tempoNanos / 1_000_000L

                    moltiplicatoreSecondi = if (moltiplicatoreScadenzaNanos > tempoNanos) {
                        ((moltiplicatoreScadenzaNanos - tempoNanos) / 1_000_000_000L + 1).toInt()
                    } else {
                        0
                    }

                    if (tempoMillis - ultimoSpawnMillis > intervalloSpawnMillis(punteggio)) {
                        ultimoSpawnMillis = tempoMillis
                        val esisteMoltiplicatoreInVolo = mostri.any { it.tipo == TipoMostro.MOLTIPLICATORE }
                        generaMostro(nuovoId(), larghezzaPx, punteggio, mostri.size, esisteMoltiplicatoreInVolo)?.let { nuovo ->
                            mostri = mostri + nuovo
                        }
                    }

                    proiettili = proiettili
                        .map { it.copy(y = it.y - 700f * deltaSecondi) }
                        .filter { it.y > -40f }
                    mostri = mostri.map { it.copy(y = it.y + it.velocitaY * deltaSecondi) }

                    val idColpiti = mutableSetOf<Int>()
                    val proiettiliUsati = mutableSetOf<Int>()
                    for (p in proiettili) {
                        for (m in mostri) {
                            if (m.id !in idColpiti && p.id !in proiettiliUsati && colpisce(p.x, p.y, m.x, m.y)) {
                                idColpiti += m.id
                                proiettiliUsati += p.id
                            }
                        }
                    }
                    if (idColpiti.isNotEmpty()) {
                        val colpiti = mostri.filter { it.id in idColpiti }
                        val normaliColpiti = colpiti.count { it.tipo == TipoMostro.NORMALE }
                        val evitaColpiti = colpiti.count { it.tipo == TipoMostro.EVITA }
                        val moltiplicatoreColpito = colpiti.any { it.tipo == TipoMostro.MOLTIPLICATORE }
                        val moltiplicatoreAttivo = moltiplicatoreScadenzaNanos > tempoNanos

                        if (normaliColpiti > 0) {
                            SuoniGioco.successo()
                            punteggio += normaliColpiti * (if (moltiplicatoreAttivo) 2 else 1)
                        }
                        if (evitaColpiti > 0) {
                            SuoniGioco.errore()
                            vite = (vite - evitaColpiti).coerceAtLeast(0)
                        }
                        if (moltiplicatoreColpito) {
                            SuoniGioco.vittoria()
                            moltiplicatoreScadenzaNanos = tempoNanos + MOLTIPLICATORE_DURATA_NANOS
                        }

                        effetti = effetti + colpiti.map { colpito ->
                            val emojiEffetto = if (colpito.tipo == TipoMostro.EVITA) "💥" else "✨"
                            EffettoPop(
                                id = nuovoId(),
                                x = colpito.x,
                                y = colpito.y,
                                emoji = emojiEffetto,
                                scadenzaNanos = tempoNanos + 300_000_000L
                            )
                        }
                        mostri = mostri.filter { it.id !in idColpiti }
                        proiettili = proiettili.filter { it.id !in proiettiliUsati }
                    }

                    val fuoriSchermo = mostri.filter { it.y > altezzaPx }
                    if (fuoriSchermo.isNotEmpty()) {
                        val daPenalizzare = fuoriSchermo.count { it.tipo != TipoMostro.EVITA }
                        if (daPenalizzare > 0) {
                            SuoniGioco.errore()
                            vite = (vite - daPenalizzare).coerceAtLeast(0)
                        }
                        mostri = mostri.filter { it.y <= altezzaPx }
                    }

                    effetti = effetti.filter { it.scadenzaNanos > tempoNanos }
                }
                onGameOver(punteggio)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(larghezzaPx, altezzaPx) {
                        detectTapGestures { offset ->
                            cannoneX = offset.x.coerceIn(30f, larghezzaPx - 30f)
                            proiettili = proiettili + Proiettile(id = nuovoId(), x = cannoneX, y = altezzaPx - 70f)
                            SuoniGioco.tocco()
                        }
                    }
            ) {
                mostri.forEach { mostro ->
                    Text(
                        text = mostro.emoji,
                        fontSize = 32.sp,
                        modifier = Modifier.offset { IntOffset((mostro.x - 20f).roundToInt(), mostro.y.roundToInt()) }
                    )
                }
                proiettili.forEach { p ->
                    Text(
                        text = "⭐",
                        fontSize = 20.sp,
                        modifier = Modifier.offset { IntOffset((p.x - 10f).roundToInt(), p.y.roundToInt()) }
                    )
                }
                effetti.forEach { e ->
                    Text(
                        text = e.emoji,
                        fontSize = 28.sp,
                        modifier = Modifier.offset { IntOffset((e.x - 14f).roundToInt(), e.y.roundToInt()) }
                    )
                }
                Text(
                    text = "🚀",
                    fontSize = 40.sp,
                    modifier = Modifier.offset { IntOffset((cannoneX - 20f).roundToInt(), (altezzaPx - 60f).roundToInt()) }
                )
            }
        }
    }
}

@Composable
fun SchermataFineSpara(punteggio: Int, record: Int, onRiprova: () -> Unit, onTornaAiGiochi: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SfondoChiaro)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = if (punteggio >= record && punteggio > 0) "🏆" else "👾", fontSize = 96.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Hai fatto $punteggio punti!".maiuscolo(),
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
        BottoneTornaAiGiochi(
            onClick = onTornaAiGiochi,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
    }
}
