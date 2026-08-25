package it.example.btorder

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

/** Altezza fissa della parte "collassata" di ogni riga: usata anche per il calcolo del
 *  trascinamento, quindi resta invariata anche quando la riga sopra o sotto è espansa. */
private val ALTEZZA_ELEMENTO = 72.dp

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
 * Unica schermata dell'app: un elenco di dispositivi (periferiche Bluetooth accoppiate più le
 * due voci fisse del telefono) che serve sia per l'ordine di priorità delle chiamate (si
 * trascina dalle lineette) sia per le automazioni di prossimità (si tocca una riga per aprirla
 * a tendina e accedere ai suoi settaggi).
 */
@Composable
fun SchermataPrincipale() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcherPermessi = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    LaunchedEffect(Unit) { launcherPermessi.launch(permessiRichiesti()) }

    var dispositiviAccoppiati by remember { mutableStateOf<List<DispositivoBluetooth>>(emptyList()) }
    var indirizziConnessi by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cuffieUsbConnesse by remember { mutableStateOf(false) }
    var indirizzoInScelta by remember { mutableStateOf<String?>(null) }
    var indirizzoDaEliminare by remember { mutableStateOf<String?>(null) }
    var idEspanso by remember { mutableStateOf<String?>(null) }
    var indiceTrascinato by remember { mutableStateOf<Int?>(null) }
    var offsetTrascinamento by remember { mutableStateOf(0f) }

    val dispositiviFiducia by TrustedDeviceStore.osservaDispositivi(context)
        .collectAsState(initial = emptyList())
    val avvioAutomatico by TrustedDeviceStore.osservaAvvioAutomatico(context)
        .collectAsState(initial = false)
    val servizioAutomazioniAttivo by TrustedDeviceStore.osservaServizioAttivo(context)
        .collectAsState(initial = false)
    val servizioChiamateAttivo by DevicePriorityStore.osservaServizioAttivo(context)
        .collectAsState(initial = false)
    val ordineSalvato by DevicePriorityStore.osservaOrdine(context)
        .collectAsState(initial = emptyList())
    val ultimeConnessioni by TrustedDeviceStore.osservaUltimeConnessioni(context)
        .collectAsState(initial = emptyMap())

    fun ricaricaDispositivi() {
        dispositiviAccoppiati = DispositiviBluetooth.elencaDispositiviAccoppiati(context, indirizziConnessi)
        cuffieUsbConnesse = DispositiviAudio.cuffieUsbConnesse(context)
    }
    LaunchedEffect(Unit) { ricaricaDispositivi() }

    // Ricevitore locale solo per aggiornare in tempo reale il badge "Connesso" in questa
    // schermata; le automazioni vere e proprie sono gestite dai Service, indipendentemente
    // dal fatto che questa Activity sia visibile o meno.
    DisposableEffect(Unit) {
        val ricevitore = DispositiviBluetooth.creaRicevitoreConnessioni { indirizzo, connesso ->
            indirizziConnessi = if (connesso) indirizziConnessi + indirizzo else indirizziConnessi - indirizzo
            ricaricaDispositivi()
            if (connesso) {
                scope.launch { TrustedDeviceStore.registraConnessione(context, indirizzo, System.currentTimeMillis()) }
            }
        }
        ContextCompat.registerReceiver(
            context,
            ricevitore,
            DispositiviBluetooth.filtroEventiConnessione(),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Le cuffie USB non hanno un broadcast dedicato come il Bluetooth: si osservano i
        // dispositivi audio del sistema per accorgersi subito quando vengono collegate/scollegate.
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ascoltatoreAudio = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = ricaricaDispositivi()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = ricaricaDispositivi()
        }
        audioManager.registerAudioDeviceCallback(ascoltatoreAudio, null)

        onDispose {
            context.unregisterReceiver(ricevitore)
            audioManager.unregisterAudioDeviceCallback(ascoltatoreAudio)
        }
    }

    val voci = remember(dispositiviAccoppiati, dispositiviFiducia, ordineSalvato, ultimeConnessioni, cuffieUsbConnesse) {
        VociDispositivi.costruisci(dispositiviAccoppiati, dispositiviFiducia, ordineSalvato, ultimeConnessioni, cuffieUsbConnesse)
    }
    var elementi by remember(voci) { mutableStateOf(voci) }

    val densita = LocalDensity.current
    val altezzaElementoPx = with(densita) { ALTEZZA_ELEMENTO.toPx() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = "BTOrder", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Trascina un dispositivo dalle lineette per impostare l'ordine di " +
                    "priorità usato in chiamata. Tocca un dispositivo Bluetooth per segnarlo " +
                    "come dispositivo di fiducia (es. l'auto) e scegliere le sue automazioni.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item { AvvisoLimiteSblocco(context) }

        item {
            PannelloControlli(
                servizioChiamateAttivo = servizioChiamateAttivo,
                servizioAutomazioniAttivo = servizioAutomazioniAttivo,
                avvioAutomatico = avvioAutomatico,
                puoScrivereImpostazioni = Settings.System.canWrite(context),
                onAggiornaElenco = { ricaricaDispositivi() },
                onToggleServizioChiamate = {
                    val intent = Intent(context, CallRoutingService::class.java)
                    if (servizioChiamateAttivo) context.stopService(intent) else ContextCompat.startForegroundService(context, intent)
                    scope.launch { DevicePriorityStore.impostaServizioAttivo(context, !servizioChiamateAttivo) }
                },
                onToggleServizioAutomazioni = {
                    val intent = Intent(context, ProximityAutomationService::class.java)
                    if (servizioAutomazioniAttivo) context.stopService(intent) else ContextCompat.startForegroundService(context, intent)
                    scope.launch { TrustedDeviceStore.impostaServizioAttivo(context, !servizioAutomazioniAttivo) }
                },
                onToggleAvvioAutomatico = { attivo ->
                    scope.launch { TrustedDeviceStore.impostaAvvioAutomatico(context, attivo) }
                },
                onConsentiScritturaImpostazioni = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                }
            )
        }

        item {
            Text(text = "Dispositivi", style = MaterialTheme.typography.titleMedium)
        }

        itemsIndexed(elementi, key = { _, elemento -> elemento.id }) { indice, elemento ->
            RigaDispositivo(
                indice = indice,
                elemento = elemento,
                espanso = idEspanso == elemento.id,
                inTrascinamento = indice == indiceTrascinato,
                offsetTrascinamento = offsetTrascinamento,
                onEspandiToggle = {
                    idEspanso = if (idEspanso == elemento.id) null else elemento.id
                },
                onDragStart = {
                    idEspanso = null
                    indiceTrascinato = indice
                    offsetTrascinamento = 0f
                },
                onDrag = { deltaY ->
                    val indiceCorrente = indiceTrascinato
                    if (indiceCorrente != null) {
                        offsetTrascinamento += deltaY
                        val spostamento = (offsetTrascinamento / altezzaElementoPx).roundToInt()
                        val nuovoIndice = (indiceCorrente + spostamento).coerceIn(0, elementi.lastIndex)
                        if (nuovoIndice != indiceCorrente) {
                            elementi = elementi.toMutableList().apply {
                                add(nuovoIndice, removeAt(indiceCorrente))
                            }
                            offsetTrascinamento -= (nuovoIndice - indiceCorrente) * altezzaElementoPx
                            indiceTrascinato = nuovoIndice
                        }
                    }
                },
                onDragEnd = {
                    indiceTrascinato = null
                    offsetTrascinamento = 0f
                    scope.launch { DevicePriorityStore.salvaOrdine(context, elementi.map { it.id }) }
                },
                onDragCancel = {
                    indiceTrascinato = null
                    offsetTrascinamento = 0f
                },
                onCambiaFiducia = { attivo ->
                    scope.launch {
                        if (attivo) {
                            TrustedDeviceStore.salvaDispositivo(
                                context,
                                DispositivoFiducia(indirizzo = elemento.id, nome = elemento.nome)
                            )
                        } else {
                            TrustedDeviceStore.rimuoviDispositivo(context, elemento.id)
                        }
                    }
                },
                onCambiaSchermoSempreAcceso = { attivo ->
                    val f = elemento.fiducia ?: return@RigaDispositivo
                    scope.launch { TrustedDeviceStore.salvaDispositivo(context, f.copy(schermoSempreAcceso = attivo)) }
                },
                onCambiaEstendiTimeout = { attivo ->
                    val f = elemento.fiducia ?: return@RigaDispositivo
                    scope.launch { TrustedDeviceStore.salvaDispositivo(context, f.copy(estendiTimeoutSchermo = attivo)) }
                },
                onScegliTimeout = { secondi ->
                    val f = elemento.fiducia ?: return@RigaDispositivo
                    scope.launch { TrustedDeviceStore.salvaDispositivo(context, f.copy(timeoutEstesoSecondi = secondi)) }
                },
                onScegliApp = { indirizzoInScelta = elemento.id },
                onRimuoviApp = {
                    val f = elemento.fiducia ?: return@RigaDispositivo
                    scope.launch {
                        TrustedDeviceStore.salvaDispositivo(context, f.copy(appDaAvviarePackage = null, appDaAvviareNome = null))
                    }
                },
                onEliminaRichiesta = { indirizzoDaEliminare = elemento.id }
            )
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

    val indirizzo = indirizzoDaEliminare
    if (indirizzo != null) {
        val nome = elementi.firstOrNull { it.id == indirizzo }?.nome ?: indirizzo
        AlertDialog(
            onDismissRequest = { indirizzoDaEliminare = null },
            title = { Text("Eliminare \"$nome\"?") },
            text = {
                Text(
                    "Il dispositivo verrà disaccoppiato dal telefono, insieme alle sue automazioni " +
                        "salvate in BTOrder. Potrai accoppiarlo di nuovo in qualsiasi momento dalle " +
                        "impostazioni Bluetooth."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val riuscito = DispositiviBluetooth.dimenticaDispositivo(context, indirizzo)
                    scope.launch {
                        TrustedDeviceStore.rimuoviDispositivo(context, indirizzo)
                        TrustedDeviceStore.rimuoviUltimaConnessione(context, indirizzo)
                    }
                    if (riuscito) {
                        ricaricaDispositivi()
                    } else {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    indirizzoDaEliminare = null
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { indirizzoDaEliminare = null }) { Text("Annulla") }
            }
        )
    }
}

@Composable
private fun AvvisoLimiteSblocco(context: Context) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Nota sullo sblocco lucchetto", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Android non permette alle app di terze parti di bypassare davvero il " +
                    "PIN o il pattern (serve un permesso di sistema riservato). Per lo sblocco " +
                    "automatico vero e proprio in prossimità dell'auto usa \"Smart Lock > " +
                    "Dispositivi affidabili\" nelle impostazioni native, se il tuo telefono la " +
                    "supporta ancora. BTOrder si occupa invece delle automazioni concrete qui sotto.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }) {
                Text("Apri impostazioni di sicurezza")
            }
        }
    }
}

@Composable
private fun PannelloControlli(
    servizioChiamateAttivo: Boolean,
    servizioAutomazioniAttivo: Boolean,
    avvioAutomatico: Boolean,
    puoScrivereImpostazioni: Boolean,
    onAggiornaElenco: () -> Unit,
    onToggleServizioChiamate: () -> Unit,
    onToggleServizioAutomazioni: () -> Unit,
    onToggleAvvioAutomatico: (Boolean) -> Unit,
    onConsentiScritturaImpostazioni: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAggiornaElenco, modifier = Modifier.fillMaxWidth()) {
            Text("Aggiorna elenco dispositivi")
        }
        Button(onClick = onToggleServizioChiamate, modifier = Modifier.fillMaxWidth()) {
            Text(if (servizioChiamateAttivo) "Ferma instradamento chiamate" else "Avvia instradamento chiamate")
        }
        Button(onClick = onToggleServizioAutomazioni, modifier = Modifier.fillMaxWidth()) {
            Text(if (servizioAutomazioniAttivo) "Ferma automazioni auto" else "Avvia automazioni auto")
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

/**
 * Una riga della lista unica: la parte superiore (sempre visibile) mostra posizione, nome e
 * stato, con una maniglia dedicata per il trascinamento; toccando il resto della riga (solo per
 * i dispositivi Bluetooth reali) si apre a tendina la parte con le impostazioni di fiducia e le
 * automazioni.
 */
@Composable
private fun RigaDispositivo(
    indice: Int,
    elemento: VoceDispositivo,
    espanso: Boolean,
    inTrascinamento: Boolean,
    offsetTrascinamento: Float,
    onEspandiToggle: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onCambiaFiducia: (Boolean) -> Unit,
    onCambiaSchermoSempreAcceso: (Boolean) -> Unit,
    onCambiaEstendiTimeout: (Boolean) -> Unit,
    onScegliTimeout: (Int) -> Unit,
    onScegliApp: () -> Unit,
    onRimuoviApp: () -> Unit,
    onEliminaRichiesta: () -> Unit
) {
    val context = LocalContext.current
    val espandibile = elemento.tipo == TipoVoceDispositivo.BLUETOOTH
    val elevazione by animateDpAsState(
        targetValue = if (inTrascinamento) 8.dp else 1.dp,
        label = "elevazioneElemento"
    )

    // La maniglia di trascinamento resta in esecuzione per tutta la vita della riga (la chiave
    // è l'id, stabile): senza rememberUpdatedState continuerebbe a richiamare le lambda della
    // primissima composizione, con "indice" ed "elementi" ormai disallineati non appena la
    // lista viene riordinata da un ALTRO trascinamento.
    val onDragStartAggiornato by rememberUpdatedState(onDragStart)
    val onDragAggiornato by rememberUpdatedState(onDrag)
    val onDragEndAggiornato by rememberUpdatedState(onDragEnd)
    val onDragCancelAggiornato by rememberUpdatedState(onDragCancel)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer { translationY = if (inTrascinamento) offsetTrascinamento else 0f }
            .zIndex(if (inTrascinamento) 1f else 0f),
        elevation = CardDefaults.cardElevation(defaultElevation = elevazione)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTEZZA_ELEMENTO)
                .then(if (espandibile) Modifier.clickable(onClick = onEspandiToggle) else Modifier)
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
                Text(text = sottotitolo(elemento), style = MaterialTheme.typography.bodySmall)
            }
            if (espandibile) {
                Text(
                    text = if (espanso) "⌄" else "›",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(elemento.id) {
                        detectDragGestures(
                            onDragStart = { onDragStartAggiornato() },
                            onDragEnd = { onDragEndAggiornato() },
                            onDragCancel = { onDragCancelAggiornato() },
                            onDrag = { change, trascinamento ->
                                change.consume()
                                onDragAggiornato(trascinamento.y)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "☰", style = MaterialTheme.typography.titleLarge)
            }
        }

        if (espanso && espandibile) {
            HorizontalDivider()
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Dispositivo di fiducia (es. l'auto)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = elemento.fiducia != null, onCheckedChange = onCambiaFiducia)
                }

                if (elemento.ultimaConnessione != null) {
                    Text(
                        text = "Ultima connessione: " + DateUtils.getRelativeDateTimeString(
                            context,
                            elemento.ultimaConnessione,
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.WEEK_IN_MILLIS,
                            0
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                val fiducia = elemento.fiducia
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
                            text = fiducia.appDaAvviareNome?.let { "Apri automaticamente: $it" } ?: "Nessuna app da aprire",
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                TextButton(
                    onClick = onEliminaRichiesta,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Elimina periferica")
                }
            }
        }
    }
}

private fun sottotitolo(elemento: VoceDispositivo): String = when (elemento.tipo) {
    TipoVoceDispositivo.AURICOLARE_TELEFONO -> "Auricolare integrato"
    TipoVoceDispositivo.VIVAVOCE_TELEFONO -> "Altoparlante integrato"
    TipoVoceDispositivo.CUFFIE_USB -> if (elemento.connesso) "Collegate ora" else "Non collegate"
    TipoVoceDispositivo.BLUETOOTH -> {
        val stato = if (elemento.connesso) "Connesso ora" else "Non connesso"
        if (elemento.fiducia != null) "$stato · Di fiducia" else stato
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
