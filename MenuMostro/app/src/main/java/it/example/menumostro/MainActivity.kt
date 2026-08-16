package it.example.menumostro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.example.menumostro.ui.theme.MenuMostroTheme
import it.example.menumostro.ui.theme.SfondoChiaro
import it.example.menumostro.ui.theme.palettePiatti

private const val NUMERO_CLIENTI = 6
private const val STELLE_PER_CLIENTE = 3
private val ETICHETTE_PORTATE = listOf("Antipasto", "Primo", "Dolce")

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

private enum class Schermata { HOME, GIOCO, FINE }

@Composable
fun AppMenuMostro() {
    var schermata by remember { mutableStateOf(Schermata.HOME) }
    var numeroCliente by remember { mutableStateOf(1) }
    var stelleTotali by remember { mutableStateOf(0) }
    var richiestaCorrente by remember { mutableStateOf(elencoRichieste.random()) }

    fun iniziaPartita() {
        numeroCliente = 1
        stelleTotali = 0
        richiestaCorrente = elencoRichieste.random()
        schermata = Schermata.GIOCO
    }

    when (schermata) {
        Schermata.HOME -> SchermataHome(onGioca = { iniziaPartita() })

        Schermata.GIOCO -> SchermataGioco(
            numeroCliente = numeroCliente,
            stelleTotali = stelleTotali,
            richiesta = richiestaCorrente,
            onClienteServito = { stelleGuadagnate ->
                stelleTotali += stelleGuadagnate
                if (numeroCliente >= NUMERO_CLIENTI) {
                    schermata = Schermata.FINE
                } else {
                    richiestaCorrente = prossimaRichiesta(richiestaCorrente)
                    numeroCliente += 1
                }
            }
        )

        Schermata.FINE -> SchermataFine(
            stelleTotali = stelleTotali,
            stelleMassime = NUMERO_CLIENTI * STELLE_PER_CLIENTE,
            onGiocaAncora = { iniziaPartita() }
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
        Text(text = "👹", fontSize = 96.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Il Menù del Mostro",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Prepara piatti buffi (e un po' pazzi) per accontentare Papà Mostro, " +
                "che ogni volta ha voglia di qualcosa di diverso!",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGioca,
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(width = 220.dp, height = 64.dp)
        ) {
            Text(text = "🎮 Gioca!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SchermataGioco(
    numeroCliente: Int,
    stelleTotali: Int,
    richiesta: RichiestaMostro,
    onClienteServito: (Int) -> Unit
) {
    // Chiave sul numero di cliente: ad ogni nuovo cliente il piatto e il
    // risultato precedente vengono azzerati automaticamente.
    var piattiScelti by remember(numeroCliente) { mutableStateOf<List<Piatto?>>(listOf(null, null, null)) }
    var risultato by remember(numeroCliente) { mutableStateOf<Int?>(null) }

    fun aggiungiPiatto(piatto: Piatto) {
        val indiceLibero = piattiScelti.indexOfFirst { it == null }
        if (indiceLibero != -1) {
            piattiScelti = piattiScelti.toMutableList().apply { set(indiceLibero, piatto) }
        }
    }

    fun rimuoviPiatto(indice: Int) {
        piattiScelti = piattiScelti.toMutableList().apply { set(indice, null) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Cliente $numeroCliente/$NUMERO_CLIENTI", style = MaterialTheme.typography.titleLarge)
                Text(text = "⭐ $stelleTotali", style = MaterialTheme.typography.titleLarge)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "👹", fontSize = 48.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = richiesta.frase,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                piattiScelti.forEachIndexed { indice, piatto ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(if (piatto != null) Color.White else Color(0xFFE0D9F5))
                                .clickable(enabled = piatto != null) { rimuoviPiatto(indice) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = piatto?.emoji ?: "❓", fontSize = 36.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = ETICHETTE_PORTATE[indice], style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tocca un piatto per aggiungerlo al menù, tocca un cerchio pieno per toglierlo.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(elencoPiatti) { indice, piatto ->
                    val colore = palettePiatti[indice % palettePiatti.size]
                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { aggiungiPiatto(piatto) },
                        colors = CardDefaults.cardColors(containerColor = colore),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = piatto.emoji, fontSize = 30.sp)
                            Text(
                                text = piatto.nome,
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val scelti = piattiScelti.filterNotNull()
                    risultato = calcolaStelle(scelti, richiesta)
                },
                enabled = piattiScelti.all { it != null },
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(text = "🍽️ Servi al Mostro!", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        risultato?.let { stelle ->
            RisultatoOverlay(
                stelle = stelle,
                ultimoCliente = numeroCliente >= NUMERO_CLIENTI,
                onContinua = { onClienteServito(stelle) }
            )
        }
    }
}

@Composable
fun RisultatoOverlay(stelle: Int, ultimoCliente: Boolean, onContinua: () -> Unit) {
    val (emoji, messaggio) = when (stelle) {
        3 -> "🤩" to "Delizioso! Il Mostro è al settimo cielo!"
        2 -> "😋" to "Mmm, buonissimo! Il Mostro è soddisfatto."
        else -> "😅" to "Che sapore strano... ma il Mostro lo mangia lo stesso, con una smorfia buffa!"
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
                Text(text = emoji, fontSize = 72.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    repeat(STELLE_PER_CLIENTE) { indice ->
                        Text(text = if (indice < stelle) "⭐" else "☆", fontSize = 32.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = messaggio,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onContinua, shape = RoundedCornerShape(50)) {
                    Text(
                        text = if (ultimoCliente) "Vedi il risultato finale 🏁" else "Prossimo cliente ➡️",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SchermataFine(stelleTotali: Int, stelleMassime: Int, onGiocaAncora: () -> Unit) {
    val percentuale = stelleTotali.toFloat() / stelleMassime
    val (emoji, messaggio) = when {
        percentuale >= 0.85f -> "🏆" to "Sei un vero Chef dei Mostri!"
        percentuale >= 0.6f -> "🎉" to "Ottimo lavoro, il Mostro è tornato ogni volta contento!"
        else -> "👍" to "Bel tentativo! Il Mostro ha adorato la tua fantasia."
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
            text = "Hai servito $NUMERO_CLIENTI clienti oggi!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Stelle raccolte: $stelleTotali su $stelleMassime ⭐",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = messaggio, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGiocaAncora,
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(width = 220.dp, height = 64.dp)
        ) {
            Text(text = "🔁 Gioca ancora", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
