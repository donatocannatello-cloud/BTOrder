package it.example.ripassofoto.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.example.ripassofoto.data.PaginaStudio
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SchermataDettaglioPagina(
    pagina: PaginaStudio,
    onRifaiQuiz: () -> Unit,
    onElimina: () -> Unit,
    onIndietro: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(pagina.titolo, style = MaterialTheme.typography.headlineSmall)
        Text(
            SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.ITALIAN).format(pagina.creataIl),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
        if (pagina.ultimoPunteggio != null && pagina.ultimoTotale != null) {
            Text(
                "Ultimo risultato: ${pagina.ultimoPunteggio}/${pagina.ultimoTotale}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Text(
            pagina.testoEstratto,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        )

        Button(onClick = onRifaiQuiz, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            Text("Rifai il quiz")
        }
        OutlinedButton(onClick = onElimina, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Elimina pagina")
        }
        TextButton(onClick = onIndietro, modifier = Modifier.fillMaxWidth()) {
            Text("Indietro")
        }
    }
}
