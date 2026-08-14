package it.example.btorder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import it.example.btorder.ui.theme.BTOrderTheme
import kotlinx.coroutines.launch

/** Permessi richiesti a runtime dall'app, dipendenti dalla versione di Android in esecuzione. */
private fun permessiRichiesti(): Array<String> = buildList {
    add(android.Manifest.permission.BLUETOOTH_CONNECT)
    if (Build.VERSION.SDK_INT >= 33) {
        add(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BTOrderTheme {
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

    var dispositiviAccoppiati by remember { mutableStateOf<List<DispositivoBluetooth>>(emptyList()) }
    var indirizziConnessi by remember { mutableStateOf<Set<String>>(emptySet()) }
    var servizioAttivo by remember { mutableStateOf(false) }
    var indirizzoInScelta by remember { mutableStateOf<String?>(null) }

    val dispositiviFiducia by TrustedDeviceStore.osservaDispositivi(context)
        .collectAsState(initial = emptyList())
    val avvioAutomatico by TrustedDeviceStore.osservaAvvioAutomatico(context)
        .collectAsState(initial = false)

    fun ricaricaDispositivi() {
        dispositiviAccoppiati = DispositiviBluetooth.elencaDispositiviAccoppiati(context, indirizziConnessi)
    }

    val launcherPermessi = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { ricaricaDispositivi() }

    LaunchedEffect(Unit) {
        launcherPermessi.launch(permessiRichiesti())
    }

    // Ricevitore locale solo per aggiornare in tempo reale il badge "Connesso" in questa
    // schermata; le automazioni vere e proprie sono gestite dal Service, indipendentemente
    // dal fatto che questa Activity sia visibile o meno.
    DisposableEffect(Unit) {
        val ricevitore = DispositiviBluetooth.creaRicevitoreConnessioni { indirizzo, connesso ->
            indirizziConnessi = if (connesso) indirizziConnessi + indirizzo else indirizziConnessi - indirizzo
            ricaricaDispositivi()
        }
        ContextCompat.registerReceiver(
            context,
            ricevitore,
            DispositiviBluetooth.filtroEventiConnessione(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(ricevitore) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(text = "BTOrder", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "Segna l'auto (o un'altra periferica) come dispositivo di fiducia per " +
                        "applicare automazioni automatiche quando ti connetti in Bluetooth.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item { AvvisoLimiteSblocco(context) }

            item {
                PannelloControlli(
                    servizioAttivo = servizioAttivo,
                    avvioAutomatico = avvioAutomatico,
                    puoScrivereImpostazioni = Settings.System.canWrite(context),
                    onAggiornaElenco = { ricaricaDispositivi() },
                    onToggleServizio = {
                        val intent = Intent(context, ProximityAutomationService::class.java)
                        if (servizioAttivo) {
                            context.stopService(intent)
                        } else {
                            ContextCompat.startForegroundService(context, intent)
                        }
                        servizioAttivo = !servizioAttivo
                    },
                    onToggleAvvioAutomatico = { attivo ->
                        scope.launch { TrustedDeviceStore.impostaAvvioAutomatico(context, attivo) }
                    },
                    onConsentiScritturaImpostazioni = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                )
            }

            item {
                Text(
                    text = "Dispositivi accoppiati",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (dispositiviAccoppiati.isEmpty()) {
                item {
                    Text(
                        text = "Nessun dispositivo Bluetooth accoppiato trovato.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            items(dispositiviAccoppiati, key = { it.indirizzo }) { dispositivo ->
                val fiducia = dispositiviFiducia.firstOrNull { it.indirizzo == dispositivo.indirizzo }
                SchedaDispositivo(
                    dispositivo = dispositivo,
                    fiducia = fiducia,
                    onCambiaFiducia = { attivo ->
                        scope.launch {
                            if (attivo) {
                                TrustedDeviceStore.salvaDispositivo(
                                    context,
                                    DispositivoFiducia(indirizzo = dispositivo.indirizzo, nome = dispositivo.nome)
                                )
                            } else {
                                TrustedDeviceStore.rimuoviDispositivo(context, dispositivo.indirizzo)
                            }
                        }
                    },
                    onCambiaSchermoSempreAcceso = { attivo ->
                        val f = fiducia ?: return@SchedaDispositivo
                        scope.launch {
                            TrustedDeviceStore.salvaDispositivo(context, f.copy(schermoSempreAcceso = attivo))
                        }
                    },
                    onCambiaEstendiTimeout = { attivo ->
                        val f = fiducia ?: return@SchedaDispositivo
                        scope.launch {
                            TrustedDeviceStore.salvaDispositivo(context, f.copy(estendiTimeoutSchermo = attivo))
                        }
                    },
                    onScegliTimeout = { secondi ->
                        val f = fiducia ?: return@SchedaDispositivo
                        scope.launch {
                            TrustedDeviceStore.salvaDispositivo(context, f.copy(timeoutEstesoSecondi = secondi))
                        }
                    },
                    onScegliApp = { indirizzoInScelta = dispositivo.indirizzo },
                    onRimuoviApp = {
                        val f = fiducia ?: return@SchedaDispositivo
                        scope.launch {
                            TrustedDeviceStore.salvaDispositivo(
                                context,
                                f.copy(appDaAvviarePackage = null, appDaAvviareNome = null)
                            )
                        }
                    }
                )
            }
        }
    }

    if (indirizzoInScelta != null) {
        SelettoreAppDialog(
            onAppScelta = { app ->
                val indirizzo = indirizzoInScelta ?: return@SelettoreAppDialog
                val f = dispositiviFiducia.firstOrNull { it.indirizzo == indirizzo }
                if (f != null) {
                    scope.launch {
                        TrustedDeviceStore.salvaDispositivo(
                            context,
                            f.copy(appDaAvviarePackage = app.packageName, appDaAvviareNome = app.nomeVisualizzato)
                        )
                    }
                }
                indirizzoInScelta = null
            },
            onAnnulla = { indirizzoInScelta = null }
        )
    }
}

@Composable
private fun AvvisoLimiteSblocco(context: Context) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Nota sullo sblocco lucchetto",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Android non permette alle app di terze parti di bypassare davvero il " +
                    "PIN o il pattern (serve un permesso di sistema riservato). Per lo sblocco " +
                    "automatico vero e proprio in prossimità dell'auto usa \"Smart Lock > " +
                    "Dispositivi affidabili\" nelle impostazioni native, se il tuo telefono la " +
                    "supporta ancora. BTOrder si occupa invece delle automazioni concrete qui sotto.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            }) {
                Text("Apri impostazioni di sicurezza")
            }
        }
    }
}

@Composable
private fun PannelloControlli(
    servizioAttivo: Boolean,
    avvioAutomatico: Boolean,
    puoScrivereImpostazioni: Boolean,
    onAggiornaElenco: () -> Unit,
    onToggleServizio: () -> Unit,
    onToggleAvvioAutomatico: (Boolean) -> Unit,
    onConsentiScritturaImpostazioni: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAggiornaElenco, modifier = Modifier.fillMaxWidth()) {
            Text("Aggiorna elenco dispositivi")
        }
        Button(onClick = onToggleServizio, modifier = Modifier.fillMaxWidth()) {
            Text(if (servizioAttivo) "Ferma monitoraggio automazioni" else "Avvia monitoraggio automazioni")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Avvia automaticamente all'accensione", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = avvioAutomatico, onCheckedChange = onToggleAvvioAutomatico)
        }
        if (!puoScrivereImpostazioni) {
            OutlinedButton(onClick = onConsentiScritturaImpostazioni, modifier = Modifier.fillMaxWidth()) {
                Text("Consenti a BTOrder di estendere il timeout schermo")
            }
        }
    }
}

@Composable
private fun SchedaDispositivo(
    dispositivo: DispositivoBluetooth,
    fiducia: DispositivoFiducia?,
    onCambiaFiducia: (Boolean) -> Unit,
    onCambiaSchermoSempreAcceso: (Boolean) -> Unit,
    onCambiaEstendiTimeout: (Boolean) -> Unit,
    onScegliTimeout: (Int) -> Unit,
    onScegliApp: () -> Unit,
    onRimuoviApp: () -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = dispositivo.nome, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (dispositivo.connesso) "Connesso ora" else "Non connesso",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = fiducia != null, onCheckedChange = onCambiaFiducia)
            }

            if (fiducia != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(text = "Automazioni alla connessione", style = MaterialTheme.typography.labelLarge)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Mantieni lo schermo acceso", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = fiducia.schermoSempreAcceso, onCheckedChange = onCambiaSchermoSempreAcceso)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Estendi il timeout schermo", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = fiducia.estendiTimeoutSchermo, onCheckedChange = onCambiaEstendiTimeout)
                }

                if (fiducia.estendiTimeoutSchermo) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5 * 60 to "5 min", 10 * 60 to "10 min", 30 * 60 to "30 min").forEach { (secondi, etichetta) ->
                            FilterChip(
                                selected = fiducia.timeoutEstesoSecondi == secondi,
                                onClick = { onScegliTimeout(secondi) },
                                label = { Text(etichetta) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fiducia.appDaAvviareNome?.let { "Apri automaticamente: $it" }
                            ?: "Nessuna app da aprire",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (fiducia.appDaAvviareNome != null) {
                        TextButton(onClick = onRimuoviApp) { Text("Rimuovi") }
                    } else {
                        TextButton(onClick = onScegliApp) { Text("Scegli") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelettoreAppDialog(
    onAppScelta: (AppLanciabile) -> Unit,
    onAnnulla: () -> Unit
) {
    val context = LocalContext.current
    val app = remember { AppLanciabili.elenca(context) }

    AlertDialog(
        onDismissRequest = onAnnulla,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } },
        title = { Text("Scegli l'app da aprire") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(app, key = { it.packageName }) { voce ->
                    TextButton(
                        onClick = { onAppScelta(voce) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(voce.nomeVisualizzato, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    )
}
