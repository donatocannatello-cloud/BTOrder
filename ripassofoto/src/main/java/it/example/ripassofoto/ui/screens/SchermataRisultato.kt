package it.example.ripassofoto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SchermataRisultato(
    punteggio: Int,
    totale: Int,
    onRifaiQuiz: () -> Unit,
    onTornaAllaHome: () -> Unit
) {
    val percentuale = if (totale > 0) (punteggio * 100) / totale else 0
    val messaggio = when {
        percentuale >= 80 -> "Ottimo lavoro!"
        percentuale >= 50 -> "Bene, ma si può migliorare."
        else -> "Ripassa ancora un po' questa pagina."
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "$punteggio / $totale",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            messaggio,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        Button(onClick = onRifaiQuiz, modifier = Modifier.fillMaxWidth()) {
            Text("Rifai il quiz")
        }
        OutlinedButton(
            onClick = onTornaAllaHome,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Torna alla home")
        }
    }
}
