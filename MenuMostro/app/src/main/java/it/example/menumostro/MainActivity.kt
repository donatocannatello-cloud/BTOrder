package it.example.menumostro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import it.example.menumostro.ui.theme.MenuMostroTheme
import it.example.menumostro.ui.theme.SfondoChiaro
import it.example.menumostro.ui.theme.palettePiatti
import java.util.Locale

private const val NUMERO_MANCHE = 4
private const val PUNTEGGIO_MASSIMO_MANCHA = 100

/** Tutto il testo del gioco va in MAIUSCOLO: più facile da leggere per i bambini che iniziano a leggere. */
private fun String.maiuscolo(): String = uppercase(Locale.ITALIAN)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MenuMostroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppMenuMostro()
                }
            }
        }
    }
}

private enum class Schermata { HOME, LIVELLI, GIOCO, FINE }

@Composable
fun AppMenuMostro() {
    var schermata by remember { mutableStateOf(Schermata.HOME) }
    var livelloScelto by remember { mutableStateOf(elencoLivelli.first()) }
    var numeroManche by remember { mutableStateOf(1) }
    var punteggioTotale by remember { mutableStateOf(0) }
    var ordineCommensali by remember { mutableStateOf(estraiCommensali(NUMERO_MANCHE)) }
    var ordineRichieste by remember { mutableStateOf(estraiRichieste(NUMERO_MANCHE)) }

    fun iniziaLivello(livello: Livello) {
        livelloScelto = livello
        numeroManche = 1
        punteggioTotale = 0
        ordineCommensali = estraiCommensali(NUMERO_MANCHE)
        ordineRichieste = estraiRichieste(NUMERO_MANCHE)
        schermata = Schermata.GIOCO
    }

    when (schermata) {
        Schermata.HOME -> SchermataHome(onGioca = { schermata = Schermata.LIVELLI })

        Schermata.LIVELLI -> SchermataLivelli(onScegli = { livello -> iniziaLivello(livello) })

        Schermata.GIOCO -> SchermataGioco(
            livello = livelloScelto,
            numeroManche = numeroManche,
            punteggioTotale = punteggioTotale,
            commensale = ordineCommensali[numeroManche - 1],
            richiesta = ordineRichieste[numeroManche - 1],
            onManchaServita = { punteggio ->
                punteggioTotale += punteggio
                if (numeroManche >= NUMERO_MANCHE) {
                    schermata = Schermata.FINE
                } else {
                    numeroManche += 1
                }
            }
        )

        Schermata.FINE -> SchermataFine(
            punteggioTotale = punteggioTotale,
            puntiMassimi = NUMERO_MANCHE * PUNTEGGIO_MASSIMO_MANCHA,
            onGiocaAncora = { iniziaLivello(livelloScelto) },
            onCambiaLivello = { schermata = Schermata.LIVELLI }
        )
    }
}

@Composable
fun SchermataHome(onGioca: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "👹🍽️👻", fontSize = 72.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Il Menù del Mostro".maiuscolo(),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Scegli i cibi giusti per ogni mostro. Più sali di livello, più piatti devi fare!".maiuscolo(),
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

@Composable
fun SchermataLivelli(onScegli: (Livello) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🎚️", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Scegli il livello".maiuscolo(), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Più è alto, più piatti devi preparare!".maiuscolo(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))

        elencoLivelli.forEach { livello ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onScegli(livello) },
                colors = CardDefaults.cardColors(
                    containerColor = palettePiatti[(livello.numero - 1) % palettePiatti.size]
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Livello ${livello.numero}".maiuscolo(),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = livello.portate.joinToString(" + ") { it.etichetta }.maiuscolo(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                    Text(text = livello.portate.joinToString("") { it.emoji }, fontSize = 26.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun SchermataGioco(
    livello: Livello,
    numeroManche: Int,
    punteggioTotale: Int,
    commensale: Commensale,
    richiesta: RichiestaMostro,
    onManchaServita: (Int) -> Unit
) {
    // Chiave sul numero di manche: ad ogni nuovo commensale le scelte e il
    // risultato precedente vengono azzerati automaticamente.
    var scelte by remember(numeroManche, livello) {
        mutableStateOf(livello.portate.associateWith { null as Piatto? })
    }
    var risultato by remember(numeroManche, livello) { mutableStateOf<Int?>(null) }

    fun selezionaPiatto(portata: Portata, piatto: Piatto) {
        scelte = scelte.toMutableMap().apply {
            this[portata] = if (this[portata] == piatto) null else piatto
        }
    }

    val menuCompleto = livello.portate.all { scelte[it] != null }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mostro $numeroManche di $NUMERO_MANCHE".maiuscolo(),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(text = "🏅 $punteggioTotale".maiuscolo(), style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = "Livello ${livello.numero}".maiuscolo(),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))
            CartaRichiesta(commensale = commensale, richiesta = richiesta)
            Spacer(modifier = Modifier.height(4.dp))

            livello.portate.forEach { portata ->
                SezioneMenu(
                    portata = portata,
                    piatti = menuPer(portata),
                    selezionato = scelte[portata],
                    onSeleziona = { piatto -> selezionaPiatto(portata, piatto) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val piatti = livello.portate.mapNotNull { scelte[it] }
                    risultato = richiesta.valuta(piatti)
                },
                enabled = menuCompleto,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = "🍽️ Dai da mangiare!".maiuscolo(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        risultato?.let { punteggio ->
            RisultatoOverlay(
                punteggio = punteggio,
                ultimaMancha = numeroManche >= NUMERO_MANCHE,
                onContinua = { onManchaServita(punteggio) }
            )
        }
    }
}

@Composable
fun CartaRichiesta(commensale: Commensale, richiesta: RichiestaMostro) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(text = commensale.emoji, fontSize = 36.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = commensale.nome.maiuscolo(), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = richiesta.frase.maiuscolo(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private const val COLONNE_MENU = 3

@Composable
fun SezioneMenu(
    portata: Portata,
    piatti: List<Piatto>,
    selezionato: Piatto?,
    onSeleziona: (Piatto) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = portata.emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = portata.etichetta.maiuscolo(), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = (selezionato?.let { "${it.emoji} scelto" } ?: "tocca qui").maiuscolo(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        piatti.withIndex().chunked(COLONNE_MENU).forEach { riga ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                riga.forEach { (indice, piatto) ->
                    CartaPiattoMenu(
                        piatto = piatto,
                        selezionato = piatto == selezionato,
                        colore = palettePiatti[indice % palettePiatti.size],
                        modifier = Modifier.weight(1f),
                        onClick = { onSeleziona(piatto) }
                    )
                }
                repeat(COLONNE_MENU - riga.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun CartaPiattoMenu(
    piatto: Piatto,
    selezionato: Boolean,
    colore: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (selezionato) {
                    Modifier.border(3.dp, Color.White, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colore),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = piatto.emoji, fontSize = 26.sp)
                Text(
                    text = piatto.nome.maiuscolo(),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            if (piatto.schifezza) {
                Text(
                    text = "🤪",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                )
            }
            if (selezionato) {
                Text(
                    text = "✔️",
                    fontSize = 16.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                )
            }
        }
    }
}

@Composable
fun RisultatoOverlay(punteggio: Int, ultimaMancha: Boolean, onContinua: () -> Unit) {
    val (emoji, messaggio) = when (punteggio) {
        100 -> "🤩" to "Perfetto! Il mostro è felice!"
        67 -> "😋" to "Bravo! Al mostro piace!"
        33 -> "😅" to "Mmm... così così."
        else -> "😬" to "Oh no! Non gli piace. Riprova!"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(32.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = emoji, fontSize = 64.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$punteggio su 100 punti".maiuscolo(),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = messaggio.maiuscolo(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onContinua, shape = RoundedCornerShape(50)) {
                    Text(
                        text = (if (ultimaMancha) "Guarda i punti finali 🏁" else "Prossimo mostro ➡️").maiuscolo(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SchermataFine(
    punteggioTotale: Int,
    puntiMassimi: Int,
    onGiocaAncora: () -> Unit,
    onCambiaLivello: () -> Unit
) {
    val percentuale = punteggioTotale.toFloat() / puntiMassimi
    val (emoji, messaggio) = when {
        percentuale >= 0.85f -> "🏆" to "Sei il miglior cuoco!"
        percentuale >= 0.6f -> "🎉" to "Bravo! I mostri sono contenti!"
        percentuale >= 0.35f -> "👍" to "Bravo! Continua così!"
        else -> "😄" to "Riprova! Puoi fare meglio!"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = emoji, fontSize = 96.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Hai dato da mangiare a $NUMERO_MANCHE mostri!".maiuscolo(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Punti totali: $punteggioTotale su $puntiMassimi".maiuscolo(),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = messaggio.maiuscolo(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGiocaAncora,
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(width = 220.dp, height = 64.dp)
        ) {
            Text(text = "🔁 Gioca ancora".maiuscolo(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onCambiaLivello,
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(width = 220.dp, height = 56.dp)
        ) {
            Text(text = "🎚️ Cambia livello".maiuscolo(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
