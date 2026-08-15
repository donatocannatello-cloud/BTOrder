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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import it.example.btorder.ui.theme.BTOrderTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Permessi richiesti a runtime dall'app, dipendenti dalla versione di Android in esecuzione. */
private fun permessiRichiesti(): Array<String> = buildList {
    add(android.Manifest.permission.BLUETOOTH_CONNECT)
    add(android.Manifest.permission.READ_PHONE_STATE)
    add(android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
    add(android.Manifest.permission.FOREGROUND_SERVICE)
    if (Build.VERSION.SDK_INT >= 34) {
        add(android.Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL)
    }
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

/**
 * Schermata radice: richiede una volta sola tutti i permessi necessari a entrambe le
 * funzionalità dell'app e le presenta come due schede ("Chiamate" e "Auto e dispositivi").
 */
@Composable
fun SchermataPrincipale() {
    val launcherPermessi = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    LaunchedEffect(Unit) {
        launcherPermessi.launch(permessiRichiesti())
    }

    var schedaSelezionata by remember { mutableStateOf(0) }
    val titoliSchede = listOf("Chiamate", "Auto e dispositivi")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = schedaSelezionata) {
            titoliSchede.forEachIndexed { indice, titolo ->
                Tab(
                    selected = schedaSelezionata == indice,
                    onClick = { schedaSelezionata = indice },
                    text = { Text(titolo) }
                )
            }
        }
        when (schedaSelezionata) {
            0 -> SchermataChiamate()
            else -> SchermataAutoDispositivi()
        }
    }
}

// ============================================================================================
// Scheda "Chiamate": priorità dei dispositivi audio per l'instradamento automatico in chiamata
// ============================================================================================

@Composable
fun SchermataChiamate() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dispositivi by remember { mutableStateOf<List<VoceDispositivoAudio>>(emptyList()) }
    var servizioAttivo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val ordineSalvato = DevicePriorityStore.leggiOrdineUnaVolta(context)
        dispositivi = DispositiviAudio.costruisciListaOrdinata(context, ordineSalvato)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Tieni premuto e trascina un dispositivo per impostare l'ordine di priorità " +
                "usato automaticamente durante le chiamate.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
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

// ============================================================================================
// Scheda "Auto e dispositivi": gestione periferiche Bluetooth e automazioni di prossimità
// ============================================================================================

@Composable
fun SchermataAutoDispositivi() {
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

    LaunchedEffect(Unit) { ricaricaDispositivi() }

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
                Text(
                    text = "Segna l'auto (o un'altra periferica) come dispositivo di fiducia per " +
                        "applicare automazioni automatiche quando ti connetti in Bluetooth.",
                    style = MaterialTheme.typography.bodyMedium
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
