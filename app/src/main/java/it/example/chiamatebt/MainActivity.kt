package it.example.chiamatebt

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import it.example.chiamatebt.ui.theme.ChiamateBTTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Permessi richiesti a runtime dall'app, dipendenti dalla versione di Android in esecuzione. */
private fun permessiRichiesti(): Array<String> = buildList {
    add(Manifest.permission.BLUETOOTH_CONNECT)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.MODIFY_AUDIO_SETTINGS)
    add(Manifest.permission.FOREGROUND_SERVICE)
    if (Build.VERSION.SDK_INT >= 34) {
        add(Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL)
    }
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChiamateBTTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SchermataPrincipale()
                }
            }
        }
    }
}

@Composable
fun SchermataPrincipale() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dispositivi by remember { mutableStateOf<List<VoceDispositivoAudio>>(emptyList()) }
    var servizioAttivo by remember { mutableStateOf(false) }

    val launcherPermessi = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Indipendentemente dall'esito, ricostruiamo la lista: se BLUETOOTH_CONNECT
        // non è stato concesso compariranno comunque le due voci fisse del telefono.
        scope.launch {
            val ordineSalvato = DevicePriorityStore.leggiOrdineUnaVolta(context)
            dispositivi = DispositiviAudio.costruisciListaOrdinata(context, ordineSalvato)
        }
    }

    LaunchedEffect(Unit) {
        launcherPermessi.launch(permessiRichiesti())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = "ChiamateBT", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Tieni premuto e trascina un dispositivo per impostare l'ordine di priorità " +
                "usato automaticamente durante le chiamate.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        ListaDispositiviOrdinabile(
            dispositivi = dispositivi,
            modifier = Modifier.weight(1f),
            onOrdineCambiato = { nuovoOrdine ->
                dispositivi = nuovoOrdine
                scope.launch {
                    DevicePriorityStore.salvaOrdine(context, nuovoOrdine.map { it.id })
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                scope.launch {
                    val ordineCorrente = dispositivi.map { it.id }
                    val nuovaLista = DispositiviAudio.costruisciListaOrdinata(context, ordineCorrente)
                    dispositivi = nuovaLista
                    DevicePriorityStore.salvaOrdine(context, nuovaLista.map { it.id })
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aggiorna elenco dispositivi")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val intent = Intent(context, CallRoutingService::class.java)
                if (servizioAttivo) {
                    context.stopService(intent)
                } else {
                    ContextCompat.startForegroundService(context, intent)
                }
                servizioAttivo = !servizioAttivo
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (servizioAttivo) "Ferma monitoraggio chiamate" else "Avvia monitoraggio chiamate")
        }
    }
}

private val ALTEZZA_ELEMENTO: Dp = 72.dp

/**
 * Lista riordinabile con drag&drop nativo Compose: nessuna libreria esterna,
 * solo LazyColumn + Modifier.pointerInput. L'utente tiene premuto un elemento
 * e lo trascina verticalmente; la posizione viene ricalcolata in base allo
 * spostamento accumulato rispetto all'altezza di una riga.
 */
@Composable
fun ListaDispositiviOrdinabile(
    dispositivi: List<VoceDispositivoAudio>,
    onOrdineCambiato: (List<VoceDispositivoAudio>) -> Unit,
    modifier: Modifier = Modifier
) {
    var elementi by remember(dispositivi) { mutableStateOf(dispositivi) }
    var indiceTrascinato by remember { mutableStateOf<Int?>(null) }
    var offsetTrascinamento by remember { mutableStateOf(0f) }

    val densita = LocalDensity.current
    val altezzaElementoPx = with(densita) { ALTEZZA_ELEMENTO.toPx() }

    LazyColumn(modifier = modifier) {
        itemsIndexed(elementi, key = { _, elemento -> elemento.id }) { indice, elemento ->
            val inTrascinamento = indice == indiceTrascinato
            val elevazione by animateDpAsState(
                targetValue = if (inTrascinamento) 8.dp else 1.dp,
                label = "elevazioneElemento"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ALTEZZA_ELEMENTO)
                    .padding(vertical = 4.dp)
                    .graphicsLayer {
                        translationY = if (inTrascinato(indice, indiceTrascinato)) offsetTrascinamento else 0f
                    }
                    .zIndex(if (inTrascinamento) 1f else 0f)
                    // Chiave stabile sull'id, NON sulla lista: quest'ultima viene mutata
                    // durante lo stesso trascinamento e riavviare pointerInput lo interromperebbe.
                    .pointerInput(elemento.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                indiceTrascinato = indice
                                offsetTrascinamento = 0f
                            },
                            onDragEnd = {
                                indiceTrascinato = null
                                offsetTrascinamento = 0f
                                onOrdineCambiato(elementi)
                            },
                            onDragCancel = {
                                indiceTrascinato = null
                                offsetTrascinamento = 0f
                            },
                            onDrag = { change, trascinamento ->
                                change.consume()
                                val indiceCorrente = indiceTrascinato
                                if (indiceCorrente != null) {
                                    offsetTrascinamento += trascinamento.y
                                    val spostamento = (offsetTrascinamento / altezzaElementoPx).roundToInt()
                                    val nuovoIndice = (indiceCorrente + spostamento)
                                        .coerceIn(0, elementi.lastIndex)

                                    if (nuovoIndice != indiceCorrente) {
                                        elementi = elementi.toMutableList().apply {
                                            add(nuovoIndice, removeAt(indiceCorrente))
                                        }
                                        offsetTrascinamento -= (nuovoIndice - indiceCorrente) * altezzaElementoPx
                                        indiceTrascinato = nuovoIndice
                                    }
                                }
                            }
                        )
                    },
                elevation = CardDefaults.cardElevation(defaultElevation = elevazione)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${indice + 1}.",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = elemento.nome, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = descrizioneTipo(elemento.tipo),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(text = "☰", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

private fun inTrascinato(indice: Int, indiceTrascinato: Int?): Boolean = indice == indiceTrascinato

private fun descrizioneTipo(tipo: TipoVoceDispositivo): String = when (tipo) {
    TipoVoceDispositivo.BLUETOOTH -> "Dispositivo Bluetooth"
    TipoVoceDispositivo.AURICOLARE_TELEFONO -> "Auricolare integrato"
    TipoVoceDispositivo.VIVAVOCE_TELEFONO -> "Altoparlante integrato"
}
