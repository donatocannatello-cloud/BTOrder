package it.example.menumostro

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.example.menumostro.ui.theme.SfondoChiaro
import it.example.menumostro.ui.theme.palettePiatti

private const val LARGHEZZA_GRIGLIA = 320

private enum class SchermataParcheggio { HOME, GIOCO, FINE }

@Composable
fun AppMonsterParking(onTornaAiGiochi: () -> Unit) {
    var schermata by remember { mutableStateOf(SchermataParcheggio.HOME) }
    var numeroLivello by remember { mutableStateOf(1) }
    var mosseUltimoLivello by remember { mutableStateOf(0) }

    fun nuovaPartita() {
        numeroLivello = 1
        schermata = SchermataParcheggio.GIOCO
    }

    when (schermata) {
        SchermataParcheggio.HOME -> SchermataHomeParcheggio(
            onGioca = { nuovaPartita() },
            onTornaAiGiochi = onTornaAiGiochi
        )

        SchermataParcheggio.GIOCO -> SchermataGiocoParcheggio(
            livello = elencoLivelliParcheggio[numeroLivello - 1],
            onRisolto = { mosse ->
                mosseUltimoLivello = mosse
                schermata = SchermataParcheggio.FINE
            }
        )

        SchermataParcheggio.FINE -> SchermataFineParcheggio(
            numeroLivello = numeroLivello,
            mosse = mosseUltimoLivello,
            ultimoLivello = numeroLivello >= elencoLivelliParcheggio.size,
            onProssimoLivello = {
                numeroLivello += 1
                schermata = SchermataParcheggio.GIOCO
            },
            onNuovaPartita = { nuovaPartita() }
        )
    }
}

@Composable
fun SchermataHomeParcheggio(onGioca: () -> Unit, onTornaAiGiochi: () -> Unit) {
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
            Text(text = "🅿️🚗👹", fontSize = 72.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Monster Parking".maiuscolo(),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Un mostro ha parcheggiato tutto male! Libera la macchina rossa e falla uscire!".maiuscolo(),
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
fun SchermataGiocoParcheggio(livello: LivelloParcheggio, onRisolto: (Int) -> Unit) {
    var auto by remember(livello) { mutableStateOf(livello.autoIniziali) }
    var selezionataId by remember(livello) { mutableStateOf<Int?>(null) }
    var mosse by remember(livello) { mutableStateOf(0) }

    val cellSize = (LARGHEZZA_GRIGLIA / livello.dimensione).dp

    fun muovi(delta: Int) {
        val id = selezionataId ?: return
        val nuove = provaSpostamento(auto, id, delta, livello.dimensione) ?: return
        auto = nuove
        mosse += 1
        if (livello.risolto(nuove)) {
            onRisolto(mosse)
        }
    }

    val selezionata = auto.firstOrNull { it.id == selezionataId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SfondoChiaro)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Livello ${livello.numero}".maiuscolo(), style = MaterialTheme.typography.titleLarge)
            Text(text = "🔧 $mosse".maiuscolo(), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Libera la macchina rossa!".maiuscolo(),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        Column {
            for (riga in 0 until livello.dimensione) {
                Row {
                    for (colonna in 0 until livello.dimensione) {
                        val occupante = auto.firstOrNull { (riga to colonna) in it.celle() }
                        val isUscita = riga == livello.rigaUscita && colonna == livello.dimensione - 1
                        CellaParcheggio(
                            size = cellSize,
                            auto = occupante,
                            selezionata = occupante != null && occupante.id == selezionataId,
                            colore = occupante?.let { coloreAuto(livello.autoIniziali, it) },
                            isUscita = isUscita,
                            onClick = { selezionataId = occupante?.id }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selezionata != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (selezionata.orientamento == Orientamento.ORIZZONTALE) {
                    Button(onClick = { muovi(-1) }) { Text(text = "◀️", fontSize = 24.sp) }
                    Button(onClick = { muovi(1) }) { Text(text = "▶️", fontSize = 24.sp) }
                } else {
                    Button(onClick = { muovi(-1) }) { Text(text = "🔼", fontSize = 24.sp) }
                    Button(onClick = { muovi(1) }) { Text(text = "🔽", fontSize = 24.sp) }
                }
            }
        } else {
            Text(text = "Tocca un'auto per muoverla".maiuscolo(), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Colore stabile per ogni auto: rosso fisso per l'auto da liberare, palette a rotazione per le altre. */
private fun coloreAuto(tutteLeAuto: List<Auto>, auto: Auto): Color {
    if (auto.rossa) return Color(0xFFE53935)
    val indice = tutteLeAuto.filter { !it.rossa }.indexOfFirst { it.id == auto.id }
    return palettePiatti[indice % palettePiatti.size]
}

@Composable
private fun CellaParcheggio(
    size: Dp,
    auto: Auto?,
    selezionata: Boolean,
    colore: Color?,
    isUscita: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .padding(1.dp)
            .background(colore ?: Color(0xFFE0E0E0), RoundedCornerShape(4.dp))
            .then(
                if (selezionata) {
                    Modifier.border(3.dp, Color.Black, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when {
            auto != null -> Text(text = if (auto.rossa) "🚗" else "🚙", fontSize = 18.sp)
            isUscita -> Text(text = "🏁", fontSize = 16.sp)
        }
    }
}

@Composable
fun SchermataFineParcheggio(
    numeroLivello: Int,
    mosse: Int,
    ultimoLivello: Boolean,
    onProssimoLivello: () -> Unit,
    onNuovaPartita: () -> Unit
) {
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
                    "Hai liberato tutte le auto! Sei un campione!"
                } else {
                    "Livello $numeroLivello superato!"
                }
                ).maiuscolo(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Mosse usate: $mosse".maiuscolo(),
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
}
