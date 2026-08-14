# BTOrder

Questo repository contiene due app Android indipendenti (Kotlin + Jetpack
Compose), entrambe pensate per semplificare l'uso del telefono in auto via
Bluetooth:

- **[`app`](app) — ChiamateBT**: instrada automaticamente l'audio delle
  chiamate verso il dispositivo Bluetooth preferito.
- **[`btorder`](btorder) — BTOrder**: gestisce le periferiche Bluetooth
  accoppiate e applica automazioni ("modalità auto") quando ci si connette a
  un dispositivo di fiducia.

Sono due moduli Gradle separati (`:app` e `:btorder`) nello stesso progetto,
ognuno con il proprio `applicationId` e la propria icona: si possono
installare ed eseguire contemporaneamente sullo stesso telefono.

## ChiamateBT (modulo `app`)

App che permette di definire un ordine di
priorità personale tra i dispositivi audio disponibili — cuffie/auto
Bluetooth, auricolare integrato e vivavoce integrato — e lo applica
automaticamente a ogni chiamata telefonica.

- **Package**: `it.example.chiamatebt`
- **minSdk**: 31 (Android 12) — richiesto da `AudioManager.setCommunicationDevice`
  e da `TelephonyCallback`
- **targetSdk / compileSdk**: 34

### Come funziona

1. **`DispositiviAudio.kt`** enumera i dispositivi Bluetooth accoppiati con
   profilo audio (`BluetoothClass.Device.Major.AUDIO_VIDEO`) e li combina con
   due voci fisse: `Audio Telefono` (id `PHONE_EARPIECE`) e `Vivavoce
   Telefono` (id `PHONE_SPEAKER`).
2. Nella schermata principale (**`MainActivity.kt`**) l'utente riordina la
   lista con un **drag&drop nativo Compose** (nessuna libreria esterna):
   tenendo premuto un elemento e trascinandolo, `LazyColumn` +
   `Modifier.pointerInput` + `detectDragGesturesAfterLongPress` ricalcolano la
   posizione in tempo reale.
3. L'ordine (una lista di ID: MAC address per il Bluetooth, `PHONE_EARPIECE`
   / `PHONE_SPEAKER` per le voci fisse) viene salvato con **DataStore
   Preferences** (`DevicePriorityStore.kt`) e resta valido tra un riavvio e
   l'altro.
4. **`CallRoutingService.kt`** è un Service in foreground
   (`foregroundServiceType="phoneCall"`) che registra un
   `TelephonyCallback.CallStateListener` (API 31+). Quando la chiamata passa
   allo stato `OFFHOOK`:
   - legge l'ordine salvato;
   - lo scorre finché non trova il primo ID presente anche tra
     `AudioManager.availableCommunicationDevices` (i dispositivi
     *effettivamente* disponibili in quel momento);
   - lo applica con `audioManager.setCommunicationDevice(device)`.
5. Il pulsante **"Aggiorna elenco dispositivi"** ri-scansiona i dispositivi
   Bluetooth accoppiati senza perdere l'ordine già impostato: i dispositivi
   già noti mantengono la loro posizione, quelli nuovi vengono aggiunti in
   coda (`DispositiviAudio.costruisciListaOrdinata`).

Il servizio va avviato/fermato manualmente dal pulsante "Avvia/Ferma
monitoraggio chiamate" nella schermata principale.

### Permessi

Richiesti a runtime da `MainActivity` con
`ActivityResultContracts.RequestMultiplePermissions`:

- `BLUETOOTH_CONNECT` — per leggere nome/indirizzo dei dispositivi accoppiati
- `READ_PHONE_STATE` — per il `TelephonyCallback`
- `MODIFY_AUDIO_SETTINGS` — per `setCommunicationDevice`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL` — permessi "normali",
  concessi automaticamente ma comunque richiesti esplicitamente
- `POST_NOTIFICATIONS` (solo API 33+) — per la notifica persistente del
  servizio

### Limiti noti

- `AudioManager.setCommunicationDevice` è disponibile solo da **API 31**
  (Android 12); l'app non è installabile su versioni precedenti (`minSdk`
  è impostato di conseguenza).
- Alcune skin dei produttori (es. Samsung, Xiaomi) o servizi come **Android
  Auto**/Bluetooth SCO di terze parti possono reimporre il proprio routing
  audio dopo che l'app ha applicato il proprio, specialmente se intervengono
  dopo l'evento `OFFHOOK` con un piccolo ritardo.
- La lista dei dispositivi Bluetooth mostra i dispositivi **accoppiati**
  (bonded), non necessariamente quelli connessi in questo momento: questo è
  intenzionale, per permettere di definirne la priorità anche quando non
  sono nel raggio d'azione; solo al momento della chiamata si verifica quali
  siano *davvero* disponibili.
- Il servizio va avviato manualmente dall'app; non c'è (ancora) un
  `BroadcastReceiver` per l'avvio automatico al boot.

## BTOrder (modulo `btorder`)

App che gestisce le periferiche Bluetooth accoppiate e permette di marcarne
una (o più) come **dispositivo di fiducia** — tipicamente il vivavoce/l'unità
Bluetooth dell'auto — per applicare in automatico, alla connessione, alcune
comodità pensate per la guida: schermo sempre acceso, timeout schermo
allungato, scorciatoia rapida per aprire un'app (Maps, un lettore musicale,
...). Alla disconnessione, tutto viene ripristinato com'era prima.

- **Package**: `it.example.btorder`
- **minSdk**: 26 (Android 8.0)
- **targetSdk / compileSdk**: 34

### Nota importante sullo "sblocco sicuro"

Su Android moderno **nessuna app di terze parti può bypassare davvero il
PIN/pattern del lucchetto** in base alla prossimità Bluetooth: l'API di
sistema che permetteva questo comportamento (`TrustAgentService`, alla base
della vecchia funzione "Smart Lock: dispositivi affidabili") richiede dal
2017 circa un permesso di firma riservato al sistema operativo, non
concedibile a un'app normale. Non esiste un modo legittimo per aggirare
questa restrizione senza root o senza essere Device Owner via MDM.

Per questo BTOrder **non promette uno sblocco reale del lucchetto**: nella
schermata principale mostra un promemoria e un pulsante che apre le
impostazioni di sicurezza native del telefono (`Settings.ACTION_SECURITY_SETTINGS`),
dove — se il produttore la offre ancora — si può configurare "Smart Lock" in
modo nativo e supportato. Le automazioni che l'app applica davvero
(schermo, timeout, scorciatoia app) sono invece azioni concrete e
realizzabili con le API pubbliche di Android.

### Come funziona

1. **`DispositiviBluetooth.kt`** enumera tutti i dispositivi Bluetooth
   accoppiati (non solo quelli audio) e osserva in tempo reale le
   connessioni/disconnessioni tramite i broadcast di sistema
   `BluetoothDevice.ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED`.
2. Nella schermata principale (**`MainActivity.kt`**) l'utente marca uno o
   più dispositivi come "di fiducia" con uno switch, e per ciascuno può
   attivare le automazioni desiderate.
3. **`TrustedDeviceStore.kt`** salva con **DataStore Preferences** l'elenco
   dei dispositivi di fiducia e le relative automazioni, oltre alla
   preferenza di avvio automatico al boot.
4. **`ProximityAutomationService.kt`** è un Service in foreground
   (`foregroundServiceType="connectedDevice"`) che registra un
   `BroadcastReceiver` per gli eventi ACL. Alla connessione di un
   dispositivo di fiducia:
   - se richiesto, acquisisce un wake lock che mantiene lo schermo acceso;
   - se richiesto, legge e salva il timeout schermo corrente e lo estende al
     valore scelto (5/10/30 minuti) tramite `Settings.System` (richiede il
     permesso speciale `WRITE_SETTINGS`, concesso dall'utente dall'app);
   - se è stata scelta un'app, mostra una notifica con azione rapida per
     aprirla (l'avvio automatico dell'Activity in background non è permesso
     da Android: serve il tocco dell'utente sulla notifica).

   Alla disconnessione, rilascia il wake lock e ripristina il timeout
   schermo originale.
5. **`BootReceiver.kt`** riavvia il servizio dopo l'accensione del telefono
   se l'utente ha attivato "Avvia automaticamente all'accensione".

Il servizio va avviato/fermato manualmente dal pulsante "Avvia/Ferma
monitoraggio automazioni" nella schermata principale, oppure automaticamente
al boot se l'opzione è attiva.

### Permessi

- `BLUETOOTH_CONNECT` — per leggere nome/indirizzo dei dispositivi accoppiati
- `POST_NOTIFICATIONS` (solo API 33+) — per le notifiche del servizio
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE` — permessi
  "normali" per il Service in foreground
- `RECEIVE_BOOT_COMPLETED` — per il riavvio automatico al boot (opzionale)
- `WAKE_LOCK` — per mantenere lo schermo acceso su richiesta
- `WRITE_SETTINGS` — permesso speciale, concesso dall'utente tramite un
  pulsante in-app che apre `Settings.ACTION_MANAGE_WRITE_SETTINGS`, usato
  solo per estendere/ripristinare il timeout schermo

### Limiti noti

- Nessun vero bypass del lucchetto di sistema: vedi la nota dedicata sopra.
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

## Come compilare

Entrambi i moduli si compilano allo stesso modo:

1. Apri la cartella del progetto con **Android Studio** (Koala o successivo
   consigliato, richiede AGP 8.4+).
2. Lascia che Android Studio scarichi le dipendenze (Android Gradle Plugin,
   AndroidX, Compose) e sincronizzi il progetto.
3. Da terminale, in alternativa:
   ```bash
   ./gradlew :app:assembleDebug
   ./gradlew :btorder:assembleDebug
   ```
   Gli APK di debug vengono generati in `app/build/outputs/apk/debug/` e
   `btorder/build/outputs/apk/debug/`.

### Nota sulla verifica automatica della build in questo ambiente

In questo ambiente di sviluppo la build (`./gradlew assembleDebug`) **non è
stata verificata end-to-end**: la policy di rete del sandbox blocca
`dl.google.com` / `maven.google.com` (risposta 403 dal proxy egress), che è
il repository Maven da cui si scarica l'Android Gradle Plugin e le librerie
AndroidX/Compose. Senza accesso a quel repository Gradle non riesce nemmeno a
risolvere il plugin `com.android.application`, indipendentemente dalla
correttezza del codice sorgente. Il codice Kotlin è stato scritto e
riletto con attenzione, ma va comunque verificato con una build reale in un
ambiente con accesso al Google Maven repository (Android Studio su una
macchina normale, o una CI con rete non ristretta).
