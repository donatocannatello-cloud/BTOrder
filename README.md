# ChiamateBT

App Android (Kotlin + Jetpack Compose) che permette di definire un ordine di
priorità personale tra i dispositivi audio disponibili — cuffie/auto
Bluetooth, auricolare integrato e vivavoce integrato — e lo applica
automaticamente a ogni chiamata telefonica.

- **Package**: `it.example.chiamatebt`
- **minSdk**: 31 (Android 12) — richiesto da `AudioManager.setCommunicationDevice`
  e da `TelephonyCallback`
- **targetSdk / compileSdk**: 34

## Come funziona

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

## Permessi

Richiesti a runtime da `MainActivity` con
`ActivityResultContracts.RequestMultiplePermissions`:

- `BLUETOOTH_CONNECT` — per leggere nome/indirizzo dei dispositivi accoppiati
- `READ_PHONE_STATE` — per il `TelephonyCallback`
- `MODIFY_AUDIO_SETTINGS` — per `setCommunicationDevice`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL` — permessi "normali",
  concessi automaticamente ma comunque richiesti esplicitamente
- `POST_NOTIFICATIONS` (solo API 33+) — per la notifica persistente del
  servizio

## Come compilare

1. Apri la cartella del progetto con **Android Studio** (Koala o successivo
   consigliato, richiede AGP 8.4+).
2. Lascia che Android Studio scarichi le dipendenze (Android Gradle Plugin,
   AndroxX, Compose) e sincronizzi il progetto.
3. Da terminale, in alternativa:
   ```bash
   ./gradlew assembleDebug
   ```
   L'APK di debug viene generato in `app/build/outputs/apk/debug/`.

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

## Limiti noti

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

---

# Theremin Cromatico (modulo `:theremin`)

Seconda app del progetto (Kotlin + Jetpack Compose + CameraX): un **theremin
suonato con la mano davanti alla fotocamera anteriore**, mentre sul display
scorre un **flusso di onde cromatiche** sincronizzato con il suono.

- **Package**: `it.example.theremin` — **minSdk** 26, **targetSdk** 34
- **Permessi**: solo `CAMERA` (le immagini sono analizzate in locale, mai
  salvate né inviate)
- **Build**: `./gradlew :theremin:assembleDebug` → APK in
  `theremin/build/outputs/apk/debug/`

## Come si suona

Tieni il telefono in verticale, con la fotocamera anteriore rivolta verso di
te (o appoggiato sul tavolo, rivolto verso il soffitto) e muovi una mano
nell'inquadratura:

| Movimento della mano | Effetto |
| --- | --- |
| sinistra → destra | altezza da **Do3 a Do6** (tre ottave) |
| basso → alto | volume da silenzio a pieno |
| fuori campo | il suono sfuma nel silenzio |

- **Scala**: `Continua` (glissando libero, come un vero theremin),
  `Cromatica`, `Maggiore`, `Pentatonica` (le note si agganciano ai gradi della
  scala, con portamento morbido tra una e l'altra).
- **Ricalibra**: fa reimparare lo sfondo — tieni la mano fuori campo per un
  attimo. Utile se cambia la luce o sposti il telefono.
- **Muto**: silenzia il suono lasciando attive le onde.

## Regolazioni del suono e della lettura (🎛 Suono)

Il pulsante **🎛 Suono** in alto apre un pannello; le scelte vengono salvate.

| Regolazione | Effetto |
| --- | --- |
| Timbro | Theremin, Sinusoide pura, Flauto, Clarinetto, Violino, Organo, Voce, 8-bit (sintesi additiva di armoniche) |
| Ottava | trasporta il suono da −2 a +2 ottave senza spostare le note sull'inquadratura (vale anche in Impara) |
| Tonalità | tonica (Do…Si) su cui sono costruite le scale Cromatica/Maggiore/Pentatonica |
| Estensione | ottave coperte dalla larghezza dell'inquadratura in Suona (1–4): meno ottave = note più larghe |
| Vibrato / Velocità vibrato | profondità (fino a ±3%) e frequenza (2–9 Hz) |
| Portamento | tempo di glissando tra le note (5–400 ms) |
| Eco | ripetizioni a ~0,3 s con retroazione |
| Calore | saturazione, da pulito a "valvolare" |
| Punto seguito | **Punta delle dita** (predefinito) o **Centro della mano** |
| Margine ai bordi | fascia laterale (0–30%) esclusa dalla tastiera |

### Note agli estremi dell'inquadratura

Il baricentro della sagoma veniva trascinato verso il centro dal braccio e
"tagliato" dal bordo dell'immagine, per cui le note estreme erano
irraggiungibili. Ora:

- di default si segue la **punta delle dita** (la fascia più alta della sagoma
  in movimento), che arriva fino al bordo anche con il braccio in campo;
- la tastiera esclude un **margine** su ciascun lato (12% di default, fasce
  scure nell'anteprima): la nota più grave e la più acuta si suonano prima del
  bordo, dove la mano è ancora interamente visibile.

## Modalità Impara

In alto si passa da **Suona** (theremin libero) a **Impara**, che guida
l'esecuzione di un brano classico scelto con **♫ Brano** tra 11 melodie di
pubblico dominio:

Inno alla gioia (Beethoven) · Per Elisa (Beethoven) · Fra Martino ·
Ah! vous dirai-je, maman (Mozart) · Eine kleine Nachtmusik (Mozart) ·
Greensleeves · Ninna nanna (Brahms) · Canone in Re (Pachelbel) ·
Il mattino (Grieg) · Minuetto in Sol (Petzold/Bach) · Largo "Dal Nuovo Mondo" (Dvořák)

- Nell'anteprima della fotocamera compare un **secondo cursore**: un cerchio
  colorato che indica dove portare la mano (il punto bianco) per suonare la
  nota successiva, collegato alla mano da una linea; un cerchio più tenue
  anticipa la nota dopo. Le linee verticali colorate segnano la posizione di
  tutte le note del brano.
- La lezione **aspetta il giocatore**: una nota vale quando è tenuta intonata
  per ~0,2 s (l'arco bianco attorno al cerchio si riempie e il cerchio diventa
  verde); poi la guida passa alla successiva.
- Per rendere le note raggiungibili, in Impara la larghezza dell'inquadratura
  copre solo l'estensione del brano (± un semitono) e le note si agganciano ai
  semitoni.
- **▶ Ascolta** fa suonare il brano all'app, con il cursore guida che si sposta
  a tempo; **↺ Da capo** ricomincia la lezione.

I brani sono in `learn/Brani.kt` (notazione compatta tipo `E4:1 F#4:0.5`),
la logica di avanzamento in `learn/Lezione.kt`.

## Come funziona

1. **`camera/HandAnalyzer.kt`** riceve da CameraX (`ImageAnalysis`,
   ~640×480) il piano di luminanza di ogni fotogramma.
2. **`camera/MotionTracker.kt`** riduce il fotogramma a una griglia 40×30,
   mantiene un modello di sfondo a media mobile e considera "mano" le celle che
   se ne discostano; la posizione è il baricentro pesato di quelle celle,
   raddrizzata secondo `rotationDegrees` e specchiata come un selfie. Lo sfondo
   si aggiorna lentamente anche sotto la mano, quindi una mano immobile per
   molti secondi viene gradualmente "assorbita".
3. **`MainActivity.kt`** traduce la posizione in frequenza (`audio/Scale.kt`,
   con eventuale quantizzazione) e volume, e li passa al synth.
4. **`audio/ThereminSynth.kt`** genera il suono: sinusoide con armoniche
   decrescenti, leggera saturazione e vibrato a 5,5 Hz; portamento sulla
   frequenza e inviluppo sul volume interpolano campione per campione i
   bersagli che arrivano dalla fotocamera a ~30 Hz.
   **`audio/AudioEngine.kt`** lo riproduce su un `AudioTrack` float a bassa
   latenza da un thread audio dedicato, conservando gli ultimi campioni.
5. **`ui/ChromaticWaves.kt`** disegna a ogni fotogramma (Compose `Canvas`,
   fusione additiva `BlendMode.Plus`):
   - nove onde con alone luminoso, la cui **tinta** segue la nota (il cerchio
     delle 12 note è mappato sulla ruota dei colori: ogni semitono = 30°);
   - **ampiezza** e luminosità proporzionali al volume reale del synth;
   - numero di creste e velocità di scorrimento crescenti con l'altezza;
   - al centro, l'**oscilloscopio** della forma d'onda effettivamente suonata,
     allineato sul passaggio per lo zero per restare fermo.

## Test

La logica senza dipendenze Android (scale/note, synth, tracker) ha test JUnit
in `theremin/src/test`: `./gradlew :theremin:testDebugUnitTest`.
In questo ambiente i test sono stati eseguiti compilandoli con `kotlinc`
direttamente sulla JVM (tutti superati); la build Android completa non è stata
verificata per lo stesso motivo descritto sopra (SDK/Google Maven non
raggiungibili dal sandbox).

## Limiti noti

- Il rilevamento è basato sul movimento/contrasto rispetto allo sfondo, non
  riconosce la forma della mano: anche il viso o altri oggetti in movimento
  nell'inquadratura spostano il punto rilevato. Funziona meglio con uno
  sfondo fermo e ben illuminato (es. telefono appoggiato rivolto al soffitto).
- Latenza complessiva tipica 60–120 ms (fotocamera + buffer audio), dipende dal
  dispositivo.
