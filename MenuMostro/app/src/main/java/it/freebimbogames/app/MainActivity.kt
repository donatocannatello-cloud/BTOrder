package it.freebimbogames.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.freebimbogames.app.ui.theme.MenuMostroTheme
import it.freebimbogames.app.ui.theme.SfondoChiaro
import it.freebimbogames.app.ui.theme.palettePiatti
import java.util.Locale

private const val NUMERO_MANCHE = 4
private const val PUNTEGGIO_MASSIMO_MANCHA = 100

/** Tutto il testo del gioco va in MAIUSCOLO: più facile da leggere per i bambini che iniziano a leggere. */
fun String.maiuscolo(): String = uppercase(Locale.ITALIAN)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MenuMostroTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppSuite()
                }
            }
        }
    }
}

/** I giochi della suite: tutti e sette sono giocabili. */
enum class Gioco { MOSTRO, PANINO, PARCHEGGIO, MEMORY, VESTITI, RITMO, SPARA }

private data class VoceGioco(val nome: String, val emoji: String, val gioco: Gioco?)

private val elencoGiochi = listOf(
    VoceGioco("Monster Restaurant", "🍽️👹", Gioco.MOSTRO),
    VoceGioco("Monster Panino", "🥪👹", Gioco.PANINO),
    VoceGioco("Monster Parking", "🅿️🚗", Gioco.PARCHEGGIO),
    VoceGioco("Memory dei Mostri", "🧠👻", Gioco.MEMORY),
    VoceGioco("Vesti il Mostro", "🎨🧌", Gioco.VESTITI),
    VoceGioco("Ritmo Mostruoso", "🎵🐙", Gioco.RITMO),
    VoceGioco("Spara ai Mostri", "🚀👹", Gioco.SPARA)
)

/** Schermata iniziale della suite: da qui si sceglie a quale gioco giocare. */
@Composable
fun AppSuite() {
    var giocoAttivo by remember { mutableStateOf<Gioco?>(null) }

    when (giocoAttivo) {
        null -> SchermataHub(onSeleziona = { gioco -> giocoAttivo = gioco })
        Gioco.MOSTRO -> AppMenuMostro(onTornaAiGiochi = { giocoAttivo = null })
        Gioco.PANINO -> AppMonsterPanino(onTornaAiGiochi = { giocoAttivo = null })
        Gioco.PARCHEGGIO -> AppMonsterParking(onTornaAiGiochi = { giocoAttivo = null })
        Gioco.MEMORY -> AppMemory(onTornaAiGiochi = { giocoAttivo = null })
        Gioco.VESTITI -> AppVestiti(onTornaAiGiochi = { giocoAttivo = null })
        Gioco.RITMO -> AppRitmo(onTornaAiGiochi = { giocoAttivo = null })
        Gioco.SPARA -> AppSpara(onTornaAiGiochi = { giocoAttivo = null })
    }
}

@Composable
fun SchermataHub(onSeleziona: (Gioco) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🎮🎲🎨", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Free Bimbo Games".maiuscolo(), style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "Scegli un gioco!".maiuscolo(), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(20.dp))

        elencoGiochi.forEachIndexed { indice, voce ->
            val disponibile = voce.gioco != null
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .then(
                        if (disponibile) {
                            Modifier.clickable { onSeleziona(voce.gioco!!) }
                        } else {
                            Modifier
                        }
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (disponibile) {
                        palettePiatti[indice % palettePiatti.size]
                    } else {
                        Color(0xFFBDBDBD)
                    }
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = voce.emoji, fontSize = 32.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = voce.nome.maiuscolo(),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (!disponibile) {
                            Text(
                                text = "Presto disponibile".maiuscolo(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                    if (!disponibile) {
                        Text(text = "🔒", fontSize = 22.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Il tasto "torna ai giochi" (freccia in alto a sinistra) condiviso da tutte le schermate
 * di tutti i giochi: non solo la home di ogni gioco, ma anche le schermate di partita in
 * corso e di fine partita, così si può sempre uscire senza dover prima finire la manche o
 * il livello. Su sfondo scuro/illustrato serve un cerchio più opaco per restare leggibile.
 */
@Composable
fun BottoneTornaAiGiochi(onClick: () -> Unit, modifier: Modifier = Modifier, sfondo: Color = Color(0x33000000)) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(sfondo)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "⬅️", fontSize = 22.sp)
    }
}

private enum class Schermata { HOME, GIOCO, FINE }

@Composable
fun AppMenuMostro(onTornaAiGiochi: () -> Unit) {
    var schermata by remember { mutableStateOf(Schermata.HOME) }
    var livelloCorrente by remember { mutableStateOf(elencoLivelli.first()) }
    var numeroManche by remember { mutableStateOf(1) }
    var punteggioTotale by remember { mutableStateOf(0) }
    var ordineCommensali by remember { mutableStateOf(estraiCommensali(NUMERO_MANCHE)) }
    var ordineRichieste by remember { mutableStateOf(estraiRichieste(NUMERO_MANCHE)) }

    // Prepara la prossima manche (stesso livello, 4 nuovi commensali) e torna al gioco.
    fun iniziaManche() {
        numeroManche = 1
        ordineCommensali = estraiCommensali(NUMERO_MANCHE)
        ordineRichieste = estraiRichieste(NUMERO_MANCHE)
        schermata = Schermata.GIOCO
    }

    // Il gioco è sequenziale: si parte sempre dal livello 1 e si sale un livello alla volta.
    fun nuovaPartita() {
        livelloCorrente = elencoLivelli.first()
        punteggioTotale = 0
        iniziaManche()
    }

    when (schermata) {
        Schermata.HOME -> SchermataHome(onGioca = { nuovaPartita() }, onTornaAiGiochi = onTornaAiGiochi)

        Schermata.GIOCO -> SchermataGioco(
            livello = livelloCorrente,
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
            },
            onTornaAiGiochi = onTornaAiGiochi
        )

        Schermata.FINE -> SchermataFine(
            livelloCompletato = livelloCorrente,
            punteggioTotale = punteggioTotale,
            onProssimoLivello = {
                livelloCorrente = elencoLivelli[livelloCorrente.numero]
                iniziaManche()
            },
            onNuovaPartita = { nuovaPartita() },
            onTornaAiGiochi = onTornaAiGiochi
        )
    }
}

@Composable
fun SchermataHome(onGioca: () -> Unit, onTornaAiGiochi: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.sfondo_taverna_mostri),
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
                text = "Il Menù del Mostro".maiuscolo(),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scegli i cibi giusti per ogni mostro. Livello dopo livello, i piatti da fare aumentano!".maiuscolo(),
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
fun SchermataGioco(
    livello: Livello,
    numeroManche: Int,
    punteggioTotale: Int,
    commensale: Commensale,
    richiesta: RichiestaMostro,
    onManchaServita: (Int) -> Unit,
    onTornaAiGiochi: () -> Unit
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
            BottoneTornaAiGiochi(onClick = onTornaAiGiochi)
            Spacer(modifier = Modifier.height(4.dp))
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
                Text(text = piatto.emoji, fontSize = 34.sp)
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
    livelloCompletato: Livello,
    punteggioTotale: Int,
    onProssimoLivello: () -> Unit,
    onNuovaPartita: () -> Unit,
    onTornaAiGiochi: () -> Unit
) {
    val ultimoLivello = livelloCompletato.numero >= elencoLivelli.size
    val puntiMassimiFinora = NUMERO_MANCHE * PUNTEGGIO_MASSIMO_MANCHA * livelloCompletato.numero
    val percentuale = punteggioTotale.toFloat() / puntiMassimiFinora

    val (emoji, messaggio) = if (ultimoLivello) {
        "🏆" to "Hai finito tutti i livelli! Sei un campione!"
    } else {
        when {
            percentuale >= 0.85f -> "🎉" to "Livello ${livelloCompletato.numero} finito benissimo!"
            percentuale >= 0.6f -> "😋" to "Livello ${livelloCompletato.numero} finito! I mostri sono contenti!"
            percentuale >= 0.35f -> "👍" to "Livello ${livelloCompletato.numero} finito! Continua così!"
            else -> "😄" to "Livello ${livelloCompletato.numero} finito! Puoi fare meglio!"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                text = messaggio.maiuscolo(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Punti totali: $punteggioTotale su $puntiMassimiFinora".maiuscolo(),
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

// ---------------------------------------------------------------------------------
// Monster Panino: stesso concept di Monster Restaurant (richiesta del mostro da
// soddisfare per fare punti) applicato a un unico panino invece che a più portate.
// Riusa Piatto, Commensale, RichiestaMostro, CartaRichiesta, CartaPiattoMenu e
// RisultatoOverlay già definiti sopra per Monster Restaurant.
// ---------------------------------------------------------------------------------

private enum class SchermataPanino { HOME, GIOCO, FINE }

@Composable
fun AppMonsterPanino(onTornaAiGiochi: () -> Unit) {
    var schermata by remember { mutableStateOf(SchermataPanino.HOME) }
    var livelloCorrente by remember { mutableStateOf(elencoLivelliPanino.first()) }
    var numeroManche by remember { mutableStateOf(1) }
    var punteggioTotale by remember { mutableStateOf(0) }
    var ordineCommensali by remember { mutableStateOf(estraiCommensali(NUMERO_MANCHE)) }
    var ordineRichieste by remember { mutableStateOf(estraiRichieste(NUMERO_MANCHE)) }

    fun iniziaManche() {
        numeroManche = 1
        ordineCommensali = estraiCommensali(NUMERO_MANCHE)
        ordineRichieste = estraiRichieste(NUMERO_MANCHE)
        schermata = SchermataPanino.GIOCO
    }

    fun nuovaPartita() {
        livelloCorrente = elencoLivelliPanino.first()
        punteggioTotale = 0
        iniziaManche()
    }

    when (schermata) {
        SchermataPanino.HOME -> SchermataHomePanino(onGioca = { nuovaPartita() }, onTornaAiGiochi = onTornaAiGiochi)

        SchermataPanino.GIOCO -> SchermataGiocoPanino(
            livello = livelloCorrente,
            numeroManche = numeroManche,
            punteggioTotale = punteggioTotale,
            commensale = ordineCommensali[numeroManche - 1],
            richiesta = ordineRichieste[numeroManche - 1],
            onManchaServita = { punteggio ->
                punteggioTotale += punteggio
                if (numeroManche >= NUMERO_MANCHE) {
                    schermata = SchermataPanino.FINE
                } else {
                    numeroManche += 1
                }
            },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataPanino.FINE -> SchermataFinePanino(
            livelloCompletato = livelloCorrente,
            punteggioTotale = punteggioTotale,
            onProssimoLivello = {
                livelloCorrente = elencoLivelliPanino[livelloCorrente.numero]
                iniziaManche()
            },
            onNuovaPartita = { nuovaPartita() },
            onTornaAiGiochi = onTornaAiGiochi
        )
    }
}

@Composable
fun SchermataHomePanino(onGioca: () -> Unit, onTornaAiGiochi: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.sfondo_panino_mostri),
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
                text = "Monster Panino".maiuscolo(),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scegli gli ingredienti giusti per il panino del mostro!".maiuscolo(),
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
fun SchermataGiocoPanino(
    livello: LivelloPanino,
    numeroManche: Int,
    punteggioTotale: Int,
    commensale: Commensale,
    richiesta: RichiestaMostro,
    onManchaServita: (Int) -> Unit,
    onTornaAiGiochi: () -> Unit
) {
    // Chiave sul numero di manche: ad ogni nuovo commensale il panino e il
    // risultato precedente vengono azzerati automaticamente.
    var selezione by remember(numeroManche, livello) { mutableStateOf<List<Piatto>>(emptyList()) }
    var risultato by remember(numeroManche, livello) { mutableStateOf<Int?>(null) }

    fun toggleIngrediente(ingrediente: Piatto) {
        selezione = if (ingrediente in selezione) {
            selezione - ingrediente
        } else if (selezione.size < livello.numeroIngredienti) {
            selezione + ingrediente
        } else {
            selezione
        }
    }

    val pronto = selezione.size == livello.numeroIngredienti

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
            BottoneTornaAiGiochi(onClick = onTornaAiGiochi)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Panino $numeroManche di $NUMERO_MANCHE".maiuscolo(),
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
            Spacer(modifier = Modifier.height(12.dp))

            // Schermata divisa in due colonne: a sinistra il banco ingredienti da
            // toccare, a destra il panino che si costruisce dal basso verso l'alto
            // (le due fette di pane sono sempre presenti e non contano come scelte).
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    GrigliaIngredientiPanino(
                        ingredienti = elencoIngredientiPanino,
                        selezionati = selezione,
                        massimo = livello.numeroIngredienti,
                        colonne = 2,
                        onToggle = { ingrediente -> toggleIngrediente(ingrediente) }
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Il tuo panino: ${selezione.size} su ${livello.numeroIngredienti}".maiuscolo(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "🍞", fontSize = 40.sp)
                    selezione.asReversed().forEach { ingrediente ->
                        Text(text = ingrediente.emoji, fontSize = 32.sp)
                    }
                    Text(text = "🍞", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { risultato = richiesta.valuta(selezione) },
                        enabled = pronto,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(text = "🥪 Fai il panino!".maiuscolo(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
fun GrigliaIngredientiPanino(
    ingredienti: List<Piatto>,
    selezionati: List<Piatto>,
    massimo: Int,
    colonne: Int = COLONNE_MENU,
    onToggle: (Piatto) -> Unit
) {
    Column {
        Text(text = "Ingredienti".maiuscolo(), style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(6.dp))

        ingredienti.withIndex().chunked(colonne).forEach { riga ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                riga.forEach { (indice, ingrediente) ->
                    val selezionato = ingrediente in selezionati
                    val bloccato = !selezionato && selezionati.size >= massimo
                    CartaPiattoMenu(
                        piatto = ingrediente,
                        selezionato = selezionato,
                        colore = if (bloccato) Color(0xFFBDBDBD) else palettePiatti[indice % palettePiatti.size],
                        modifier = Modifier.weight(1f),
                        onClick = { if (!bloccato) onToggle(ingrediente) }
                    )
                }
                repeat(colonne - riga.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun SchermataFinePanino(
    livelloCompletato: LivelloPanino,
    punteggioTotale: Int,
    onProssimoLivello: () -> Unit,
    onNuovaPartita: () -> Unit,
    onTornaAiGiochi: () -> Unit
) {
    val ultimoLivello = livelloCompletato.numero >= elencoLivelliPanino.size
    val puntiMassimiFinora = NUMERO_MANCHE * PUNTEGGIO_MASSIMO_MANCHA * livelloCompletato.numero
    val percentuale = punteggioTotale.toFloat() / puntiMassimiFinora

    val (emoji, messaggio) = if (ultimoLivello) {
        "🏆" to "Hai finito tutti i livelli! Sei il re dei panini!"
    } else {
        when {
            percentuale >= 0.85f -> "🎉" to "Livello ${livelloCompletato.numero} finito benissimo!"
            percentuale >= 0.6f -> "😋" to "Livello ${livelloCompletato.numero} finito! I mostri sono contenti!"
            percentuale >= 0.35f -> "👍" to "Livello ${livelloCompletato.numero} finito! Continua così!"
            else -> "😄" to "Livello ${livelloCompletato.numero} finito! Puoi fare meglio!"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                text = messaggio.maiuscolo(),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Punti totali: $punteggioTotale su $puntiMassimiFinora".maiuscolo(),
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
