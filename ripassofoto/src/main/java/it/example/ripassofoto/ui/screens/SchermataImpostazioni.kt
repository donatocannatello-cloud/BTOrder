package it.example.ripassofoto.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataImpostazioni(
    chiaveIniziale: String,
    onSalva: (String) -> Unit,
    onElimina: () -> Unit,
    onIndietro: () -> Unit
) {
    var chiave by remember { mutableStateOf(chiaveIniziale) }
    var chiaveVisibile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onIndietro) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            Text("Generazione delle domande con l'IA", style = MaterialTheme.typography.titleMedium)
            Text(
                "Se inserisci una tua chiave API di Anthropic (Claude), le domande di verifica " +
                    "vengono generate da Claude Opus 5 con un'analisi più approfondita del testo " +
                    "(comprensione, collegamenti, inferenze), invece delle domande più semplici " +
                    "generate localmente sul telefono. Senza chiave, o se la richiesta a Claude " +
                    "fallisce, l'app continua a funzionare con il generatore locale.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Text(
                "La chiave viene conservata cifrata solo su questo dispositivo e usata unicamente " +
                    "per chiamare api.anthropic.com direttamente dal telefono. Il testo delle pagine " +
                    "fotografate viene inviato ad Anthropic solo quando generi domande con questa " +
                    "modalità attiva, e l'utilizzo è addebitato sul tuo account Anthropic.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            OutlinedTextField(
                value = chiave,
                onValueChange = { chiave = it },
                label = { Text("Chiave API Anthropic") },
                placeholder = { Text("sk-ant-...") },
                singleLine = true,
                visualTransformation = if (chiaveVisibile) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { chiaveVisibile = !chiaveVisibile }) {
                        Icon(
                            if (chiaveVisibile) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (chiaveVisibile) "Nascondi chiave" else "Mostra chiave"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onSalva(chiave) },
                enabled = chiave.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("Salva chiave")
            }
            OutlinedButton(
                onClick = {
                    chiave = ""
                    onElimina()
                },
                enabled = chiaveIniziale.isNotBlank() || chiave.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Rimuovi chiave e torna al generatore locale")
            }
        }
    }
}
