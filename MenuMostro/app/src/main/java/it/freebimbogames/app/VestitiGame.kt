package it.freebimbogames.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private const val NUMERO_MANCHE_VESTITI = 4
private const val COLONNE_VESTITI = 3

// ---------------------------------------------------------------------------------
// Vesti il Mostro: stesso concept di Monster Restaurant (richiesta del mostro da
// soddisfare per fare punti), applicato a un vestito invece che a un pasto. Riusa
// Piatto, Commensale, RichiestaMostro, CartaRichiesta, CartaPiattoMenu e
// RisultatoOverlay già definiti per Monster Restaurant.
// ---------------------------------------------------------------------------------

private enum class SchermataVestiti { HOME, GIOCO, FINE }

@Composable
fun AppVestiti(onTornaAiGiochi: () -> Unit) {
    var schermata by remember { mutableStateOf(SchermataVestiti.HOME) }
    var livelloCorrente by remember { mutableStateOf(elencoLivelliVestiti.first()) }
    var numeroManche by remember { mutableStateOf(1) }
    var punteggioTotale by remember { mutableStateOf(0) }
    var ordineCommensali by remember { mutableStateOf(estraiCommensali(NUMERO_MANCHE_VESTITI)) }
    var ordineRichieste by remember { mutableStateOf(elencoRichiesteVestiti.shuffled().take(NUMERO_MANCHE_VESTITI)) }

    fun iniziaManche() {
        numeroManche = 1
        ordineCommensali = estraiCommensali(NUMERO_MANCHE_VESTITI)
        ordineRichieste = elencoRichiesteVestiti.shuffled().take(NUMERO_MANCHE_VESTITI)
        schermata = SchermataVestiti.GIOCO
    }

    fun nuovaPartita() {
        livelloCorrente = elencoLivelliVestiti.first()
        punteggioTotale = 0
        iniziaManche()
    }

    when (schermata) {
        SchermataVestiti.HOME -> SchermataHomeVestiti(onGioca = { nuovaPartita() }, onTornaAiGiochi = onTornaAiGiochi)

        SchermataVestiti.GIOCO -> SchermataGiocoVestiti(
            livello = livelloCorrente,
            numeroManche = numeroManche,
            punteggioTotale = punteggioTotale,
            commensale = ordineCommensali[numeroManche - 1],
            richiesta = ordineRichieste[numeroManche - 1],
            onManchaServita = { punteggio ->
                punteggioTotale += punteggio
                if (numeroManche >= NUMERO_MANCHE_VESTITI) {
                    schermata = SchermataVestiti.FINE
                } else {
                    numeroManche += 1
                }
            },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataVestiti.FINE -> SchermataFineVestiti(
            livelloCompletato = livelloCorrente,
            punteggioTotale = punteggioTotale,
            onProssimoLivello = {
                livelloCorrente = elencoLivelliVestiti[livelloCorrente.numero]
                iniziaManche()
            },
            onNuovaPartita = { nuovaPartita() },
            onTornaAiGiochi = onTornaAiGiochi
        )
    }
}

@Composable
fun SchermataHomeVestiti(onGioca: () -> Unit, onTornaAiGiochi: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.sfondo_vestiti_mostri),
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
                text = "Vesti il Mostro".maiuscolo(),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scegli cappello, occhiali, vestito, scarpe e oggetto giusti per ogni mostro!".maiuscolo(),
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
fun SchermataGiocoVestiti(
    livello: LivelloVestiti,
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
        mutableStateOf(livello.tipiAttivi.associateWith { null as Piatto? })
    }
    var risultato by remember(numeroManche, livello) { mutableStateOf<Int?>(null) }

    fun selezionaAccessorio(tipo: TipoAccessorio, accessorio: Piatto) {
        scelte = scelte.toMutableMap().apply {
            this[tipo] = if (this[tipo] == accessorio) null else accessorio
        }
    }

    val vestitoCompleto = livello.tipiAttivi.all { scelte[it] != null }

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
                    text = "Mostro $numeroManche di $NUMERO_MANCHE_VESTITI".maiuscolo(),
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
            Spacer(modifier = Modifier.height(8.dp))

            // Anteprima: il mostro con addosso gli accessori scelti finora, nell'ordine delle categorie.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🧌", fontSize = 32.sp)
                livello.tipiAttivi.forEach { tipo ->
                    scelte[tipo]?.let { accessorio ->
                        Text(text = accessorio.emoji, fontSize = 32.sp, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            livello.tipiAttivi.forEach { tipo ->
                SezioneAccessorio(
                    tipo = tipo,
                    accessori = banchePer(tipo),
                    selezionato = scelte[tipo],
                    onSeleziona = { accessorio -> selezionaAccessorio(tipo, accessorio) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val accessoriScelti = livello.tipiAttivi.mapNotNull { scelte[it] }
                    risultato = richiesta.valuta(accessoriScelti)
                },
                enabled = vestitoCompleto,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = "🎨 Vesti il mostro!".maiuscolo(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        risultato?.let { punteggio ->
            RisultatoOverlay(
                punteggio = punteggio,
                ultimaMancha = numeroManche >= NUMERO_MANCHE_VESTITI,
                onContinua = { onManchaServita(punteggio) }
            )
        }
    }
}

@Composable
fun SezioneAccessorio(
    tipo: TipoAccessorio,
    accessori: List<Piatto>,
    selezionato: Piatto?,
    onSeleziona: (Piatto) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = tipo.emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = tipo.etichetta.maiuscolo(), style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = (selezionato?.let { "${it.emoji} scelto" } ?: "tocca qui").maiuscolo(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        accessori.withIndex().chunked(COLONNE_VESTITI).forEach { riga ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                riga.forEach { (indice, accessorio) ->
                    CartaPiattoMenu(
                        piatto = accessorio,
                        selezionato = accessorio == selezionato,
                        colore = palettePiatti[indice % palettePiatti.size],
                        modifier = Modifier.weight(1f),
                        onClick = { onSeleziona(accessorio) }
                    )
                }
                repeat(COLONNE_VESTITI - riga.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun SchermataFineVestiti(
    livelloCompletato: LivelloVestiti,
    punteggioTotale: Int,
    onProssimoLivello: () -> Unit,
    onNuovaPartita: () -> Unit,
    onTornaAiGiochi: () -> Unit
) {
    val ultimoLivello = livelloCompletato.numero >= elencoLivelliVestiti.size
    val puntiMassimiFinora = NUMERO_MANCHE_VESTITI * 100 * livelloCompletato.numero

    val (emoji, messaggio) = if (ultimoLivello) {
        "🏆" to "Hai vestito tutti i mostri! Sei uno stilista pazzesco!"
    } else {
        "🎉" to "Livello ${livelloCompletato.numero} superato!"
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
                text = "Punti: $punteggioTotale su $puntiMassimiFinora".maiuscolo(),
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
