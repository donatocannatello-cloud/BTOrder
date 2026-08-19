package it.example.ripassofoto.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.example.ripassofoto.data.PaginaStudio
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataHome(
    pagine: List<PaginaStudio>,
    onNuovaFoto: () -> Unit,
    onAperturaPagina: (PaginaStudio) -> Unit,
    onImpostazioni: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RipassoFoto") },
                actions = {
                    IconButton(onClick = onImpostazioni) {
                        Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNuovaFoto) {
                Icon(Icons.Default.Add, contentDescription = "Fotografa una nuova pagina")
            }
        }
    ) { padding ->
        if (pagine.isEmpty()) {
            SchermataHomeVuota(padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, 96.dp)
            ) {
                item {
                    Text(
                        "Le pagine che hai fotografato e studiato finora.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                items(pagine, key = { it.id }) { pagina ->
                    CardPagina(pagina = pagina, onClick = { onAperturaPagina(pagina) })
                }
            }
        }
    }
}

@Composable
private fun SchermataHomeVuota(padding: PaddingValues) {
    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.School,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text("Nessuna pagina ancora", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tocca + per fotografare la prima pagina da studiare.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CardPagina(pagina: PaginaStudio, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(pagina.titolo, style = MaterialTheme.typography.titleMedium)
            Text(
                formattaData(pagina.creataIl),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    anteprimaTesto(pagina.testoEstratto),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                if (pagina.ultimoPunteggio != null && pagina.ultimoTotale != null) {
                    Text(
                        "${pagina.ultimoPunteggio}/${pagina.ultimoTotale}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

private fun anteprimaTesto(testo: String): String =
    testo.trim().take(90).let { if (testo.length > 90) "$it…" else it }

private fun formattaData(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.ITALIAN).format(millis)
