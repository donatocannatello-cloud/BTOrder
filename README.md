# BTOrder

App Android singola (Kotlin + Jetpack Compose) per semplificare l'uso del
telefono in auto via Bluetooth. Un'unica schermata mostra **una sola lista**
di dispositivi — periferiche Bluetooth accoppiate più le due voci fisse del
telefono (auricolare/vivavoce integrati) — che serve per due cose insieme:

- **Priorità in chiamata**: trascinando un dispositivo dalle lineette (☰) lo
  si sposta nell'ordine usato automaticamente per instradare l'audio a ogni
  chiamata telefonica.
- **Automazioni di prossimità**: toccando un dispositivo Bluetooth (non le
  due voci fisse) la riga si apre a tendina e mostra i suoi settaggi: se
  marcarlo come **dispositivo di fiducia** — tipicamente l'unità Bluetooth
  dell'auto — e quali automazioni applicare alla connessione (schermo sempre
  acceso, timeout schermo esteso, scorciatoia per aprire un'app),
  ripristinate alla disconnessione.

- **Package**: `it.example.btorder`
- **minSdk**: 31 (Android 12) — richiesto da `AudioManager.setCommunicationDevice`
  e da `TelephonyCallback` per l'instradamento delle chiamate
- **targetSdk / compileSdk**: 34

## Nota importante sullo "sblocco sicuro"

Su Android moderno **nessuna app di terze parti può bypassare davvero il
PIN/pattern del lucchetto** in base alla prossimità Bluetooth: l'API di
sistema che permetteva questo comportamento (`TrustAgentService`, alla base
della vecchia funzione "Smart Lock: dispositivi affidabili") richiede dal
2017 circa un permesso di firma riservato al sistema operativo, non
concedibile a un'app normale. Non esiste un modo legittimo per aggirare
questa restrizione senza root o senza essere Device Owner via MDM.

Per questo BTOrder **non promette uno sblocco reale del lucchetto**: nella
schermata mostra un promemoria e un pulsante che apre le impostazioni di
sicurezza native del telefono (`Settings.ACTION_SECURITY_SETTINGS`), dove —
se il produttore la offre ancora — si può configurare "Smart Lock" in modo
nativo e supportato. Le automazioni che l'app applica davvero (schermo,
timeout, scorciatoia app, priorità audio in chiamata) sono invece azioni
concrete e realizzabili con le API pubbliche di Android.

## Come funziona la lista unica

1. **`DispositiviBluetooth.kt`** enumera tutti i dispositivi Bluetooth
   accoppiati (non solo quelli audio) e osserva in tempo reale le
   connessioni/disconnessioni tramite i broadcast di sistema
   `BluetoothDevice.ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED`.
2. **`VociDispositivi.kt`** unisce questi dispositivi alle due voci fisse del
   telefono (`Audio Telefono` id `PHONE_EARPIECE`, `Vivavoce Telefono` id
   `PHONE_SPEAKER`) rispettando l'ordine di priorità già salvato: le voci con
   un id già noto mantengono la posizione scelta dall'utente, quelle nuove
   vengono aggiunte in coda.
3. Nella schermata (**`MainActivity.kt`**, `SchermataPrincipale` +
   `RigaDispositivo`) ogni riga ha una maniglia dedicata (☰): tenerla e
   trascinarla riordina la lista con un **drag&drop nativo Compose** (nessuna
   libreria esterna) — `LazyColumn` + `Modifier.pointerInput` +
   `detectDragGestures` ricalcolano la posizione in tempo reale. Toccare il
   resto della riga (solo per i dispositivi Bluetooth reali, non per le due
   voci fisse) apre a tendina i suoi settaggi: interruttore "dispositivo di
   fiducia" e, se attivo, le relative automazioni.
4. L'ordine (una lista di ID: MAC address per il Bluetooth, `PHONE_EARPIECE`
   / `PHONE_SPEAKER` per le voci fisse) viene salvato con **DataStore
   Preferences** (`DevicePriorityStore.kt`) e resta valido tra un riavvio e
   l'altro. Il pulsante **"Aggiorna elenco dispositivi"** ri-scansiona i
   dispositivi Bluetooth accoppiati senza perdere l'ordine già impostato.
5. **`TrustedDeviceStore.kt`** salva con **DataStore Preferences** l'elenco
   dei dispositivi di fiducia e le relative automazioni, oltre alla
   preferenza di avvio automatico al boot.

### Instradamento delle chiamate

**`CallRoutingService.kt`** è un Service in foreground
(`foregroundServiceType="phoneCall"`) che registra un
`TelephonyCallback.CallStateListener` (API 31+). Quando la chiamata passa
allo stato `OFFHOOK`, legge l'ordine salvato e lo scorre finché non trova il
primo ID presente anche tra `AudioManager.availableCommunicationDevices` (i
dispositivi *effettivamente* disponibili in quel momento), applicandolo con
`audioManager.setCommunicationDevice(device)`. Il dispositivo Bluetooth in
cima alla classifica spesso non è ancora disponibile esattamente
all'OFFHOOK (il sistema impiega qualche istante a stabilire il canale
SCO/A2DP): il servizio riprova alcune volte nei secondi successivi e reagisce
anche se un nuovo dispositivo compare più tardi nella stessa chiamata.

Va avviato/fermato manualmente dal pulsante "Avvia/Ferma instradamento
chiamate".

### Automazioni di prossimità

**`ProximityAutomationService.kt`** è un Service in foreground
(`foregroundServiceType="connectedDevice"`) che registra un
`BroadcastReceiver` per gli eventi ACL. Alla connessione di un dispositivo
di fiducia:

- se richiesto, acquisisce un wake lock che mantiene lo schermo acceso;
- se richiesto, legge e salva il timeout schermo corrente e lo estende al
  valore scelto (5/10/30 minuti) tramite `Settings.System` (richiede il
  permesso speciale `WRITE_SETTINGS`, concesso dall'utente dall'app);
- se è stata scelta un'app, mostra una notifica con azione rapida per
  aprirla (l'avvio automatico dell'Activity in background non è permesso da
  Android: serve il tocco dell'utente sulla notifica).

Alla disconnessione, rilascia il wake lock e ripristina il timeout schermo
originale. **`BootReceiver.kt`** riavvia il servizio dopo l'accensione del
telefono se l'utente ha attivato "Avvia automaticamente all'accensione".

Va avviato/fermato manualmente dal pulsante "Avvia/Ferma automazioni auto",
oppure automaticamente al boot se l'opzione è attiva.

## Permessi

Richiesti a runtime da `MainActivity` con
`ActivityResultContracts.RequestMultiplePermissions` (una sola volta
all'avvio, valgono per l'intera schermata):

- `BLUETOOTH_CONNECT` — per leggere nome/indirizzo dei dispositivi accoppiati
- `READ_PHONE_STATE` — per il `TelephonyCallback` (instradamento chiamate)
- `MODIFY_AUDIO_SETTINGS` — per `setCommunicationDevice` (instradamento chiamate)
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`,
  `FOREGROUND_SERVICE_CONNECTED_DEVICE` — permessi "normali" per i due
  Service in foreground
- `POST_NOTIFICATIONS` (solo API 33+) — per le notifiche dei servizi
- `RECEIVE_BOOT_COMPLETED` — per il riavvio automatico al boot delle
  automazioni (opzionale)
- `WAKE_LOCK` — per mantenere lo schermo acceso su richiesta
- `WRITE_SETTINGS` — permesso speciale, concesso dall'utente tramite un
  pulsante in-app che apre `Settings.ACTION_MANAGE_WRITE_SETTINGS`, usato
  solo per estendere/ripristinare il timeout schermo

## Limiti noti

- Nessun vero bypass del lucchetto di sistema: vedi la nota dedicata sopra.
- `AudioManager.setCommunicationDevice` è disponibile solo da **API 31**
  (Android 12); l'app non è installabile su versioni precedenti (`minSdk`
  è impostato di conseguenza per l'intera app, anche se le sole automazioni
  di prossimità richiederebbero solo API 26).
- Alcune skin dei produttori (es. Samsung, Xiaomi) o servizi come **Android
  Auto**/Bluetooth SCO di terze parti possono reimporre il proprio routing
  audio dopo che l'app ha applicato il proprio, specialmente se intervengono
  dopo l'evento `OFFHOOK` con un ritardo maggiore dei tentativi automatici.
- La lista mostra i dispositivi Bluetooth **accoppiati** (bonded), non
  necessariamente quelli connessi in questo momento: questo è intenzionale,
  per permettere di definirne la priorità anche quando non sono nel raggio
  d'azione; solo al momento della chiamata si verifica quali siano
  *davvero* disponibili.
- L'avvio automatico di un'app alla connessione richiede comunque un tocco
  dell'utente sulla notifica, per rispettare le restrizioni Android
  sull'avvio di Activity dal background.
- Se l'utente non concede il permesso `WRITE_SETTINGS`, l'automazione
  "estendi timeout schermo" viene semplicemente ignorata (le altre restano
  disponibili).
- Il wake lock per "schermo sempre acceso" usa l'API deprecata
  `PowerManager.SCREEN_DIM_WAKE_LOCK`: è una scelta deliberata, perché senza
  root/MDM è l'unico modo per un Service in background di tenere lo schermo
  acceso in risposta a un evento esterno come una connessione Bluetooth.
- I due servizi in foreground (chiamate e automazioni auto) vanno avviati
  manualmente dai rispettivi pulsanti; solo il monitoraggio delle
  automazioni auto può ripartire da solo al boot, se abilitato.

## Come compilare

1. Apri la cartella del progetto con **Android Studio** (Koala o successivo
   consigliato, richiede AGP 8.4+).
2. Lascia che Android Studio scarichi le dipendenze (Android Gradle Plugin,
   AndroidX, Compose) e sincronizzi il progetto.
3. Da terminale, in alternativa:
   ```bash
   ./gradlew assembleDebug
   ```
   L'APK di debug viene generato in `app/build/outputs/apk/debug/`.

### Firma delle build di debug

Il progetto include una keystore di debug fissa e committata
(`app/debug.keystore`, password/alias standard `android`/`androiddebugkey`/
`android` — non è un segreto, è la prassi comune per le build di debug) e la
usa esplicitamente in `app/build.gradle.kts`. Senza una keystore fissa, ogni
build su un runner CI "pulito" ne genererebbe una diversa, firmando ogni APK
con una chiave differente: Android rifiuta poi di installare un APK
"aggiornato" la cui firma non coincide con quella già installata
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), costringendo a disinstallare prima di
ogni aggiornamento. Con la chiave fissa, gli APK di debug successivi si
installano normalmente sopra la versione precedente.

### Download dell'ultimo APK compilato

Ogni push su questo branch aggiorna automaticamente la Release
`debug-latest` del repository con l'ultimo APK compilato, tramite
`.github/workflows/build-apk.yml`. Il link diretto (niente zip, niente
navigazione nella pagina Actions) resta sempre lo stesso:

```
https://github.com/donatocannatello-cloud/BTOrder/releases/download/debug-latest/BTOrder-debug.apk
```

### Nota sulla verifica automatica della build in questo ambiente

In questo ambiente di sviluppo la build (`./gradlew assembleDebug`) **non è
stata verificata end-to-end**: la policy di rete del sandbox blocca
`dl.google.com` / `maven.google.com` (risposta 403 dal proxy egress), che è
il repository Maven da cui si scarica l'Android Gradle Plugin e le librerie
AndroidX/Compose. Senza accesso a quel repository Gradle non riesce nemmeno a
risolvere il plugin `com.android.application`, indipendentemente dalla
correttezza del codice sorgente. La build viene invece verificata
automaticamente su GitHub Actions (`.github/workflows/build-apk.yml`), che
gira su runner con accesso di rete completo.
