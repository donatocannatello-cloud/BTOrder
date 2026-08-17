package it.example.ripassofoto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Mostra il testo estratto via OCR dalla foto appena scattata, permettendo allo
 * studente di correggere eventuali errori di riconoscimento prima di generare il quiz.
 */
@Composable
fun SchermataRevisioneTesto(
    file: File,
    riconosciTesto: suspend (File) -> String,
    onGeneraQuiz: (titolo: String, testo: String) -> Unit,
    onAnnulla: () -> Unit
) {
    var stato by remember { mutableStateOf<StatoRiconoscimento>(StatoRiconoscimento.InCorso) }
    var titolo by remember { mutableStateOf("") }
    var testo by remember { mutableStateOf("") }

    LaunchedEffect(file) {
        stato = try {
            val riconosciuto = riconosciTesto(file)
            testo = riconosciuto
            if (riconosciuto.isBlank()) StatoRiconoscimento.Vuoto else StatoRiconoscimento.Pronto
        } catch (e: Exception) {
            StatoRiconoscimento.Errore
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Testo riconosciuto", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Controlla e correggi il testo se l'OCR ha sbagliato qualche parola, poi genera le domande.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        when (stato) {
            StatoRiconoscimento.InCorso -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            "Riconoscimento del testo in corso…",
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
            StatoRiconoscimento.Errore -> {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Non è stato possibile leggere il testo dalla foto. Riprova con una foto più nitida.")
                }
            }
            StatoRiconoscimento.Vuoto, StatoRiconoscimento.Pronto -> {
                OutlinedTextField(
                    value = titolo,
                    onValueChange = { titolo = it },
                    label = { Text("Titolo (es. \"Storia, cap. 3\")") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    label = { Text("Testo della pagina") },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                if (stato == StatoRiconoscimento.Vuoto) {
                    Text(
                        "Non è stato riconosciuto testo: scrivilo o incollalo qui sotto.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onAnnulla) { Text("Annulla") }
            Button(
                enabled = testo.isNotBlank(),
                onClick = { onGeneraQuiz(titolo, testo) }
            ) {
                Text("Genera domande")
            }
        }
    }
}

private enum class StatoRiconoscimento { InCorso, Pronto, Vuoto, Errore }
