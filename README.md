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

## Base musicale e mixer (📻)

Si può suonare il theremin sopra un brano di sottofondo (per esempio la propria
copia di *Romeo and Juliet*). L'app non include brani registrati: la base si
sceglie dal telefono o da internet.

- **📻 Radio Romeo and Juliet**: pulsante dedicato alla web radio di Verona
  (electronic chill, radioromeoandjuliet.com); la vecchia "RTL 102.5 Romeo &
  Juliet", chiusa, è esclusa (`audio/SceltaRadio.kt`). L'indirizzo dello stream non è scritto nell'app: viene
  chiesto ogni volta al catalogo pubblico [Radio Browser](https://www.radio-browser.info)
  (`audio/RadioBrowser.kt`), così resta valido anche se la radio cambia server.
  Dallo stesso pannello si può **cercare qualunque altra radio** per nome.
- **📻** apre il pannello della base: **Scegli un file audio dal telefono**
  (selettore di sistema, qualunque formato supportato da Android: mp3, m4a,
  ogg, flac…) oppure **Radio / stream**: si incolla l'URL di una radio o di un
  file in rete. L'ultima base viene ricordata e ricaricata, in pausa, al
  riavvio.
- **Mixer** sempre visibile in basso: due cursori indipendenti **Theremin** e
  **Base**, più ▶/⏸ della base. I volumi vengono salvati.
- La base va in pausa quando l'app passa in secondo piano e riparte al
  ritorno, come il theremin. Opzione **Ripeti** per i file (non per gli stream).

## Regolazioni del suono e della lettura (🎛 Suono)

Il pulsante **🎛 Suono** in alto apre un pannello; le scelte vengono salvate.

| Regolazione | Effetto |
| --- | --- |
| Timbro | Theremin, Sinusoide pura, Flauto, Clarinetto, Violino, Organo, Voce, 8-bit, Basso, Violoncello (sintesi additiva di armoniche) |
| Ottava | trasporta il suono da −3 a +2 ottave senza spostare le note sull'inquadratura (vale anche in Impara) |
| Rinforzo bassi | sulle note sotto ~250 Hz aggiunge armoniche: l'altoparlante del telefono non riproduce le fondamentali gravi, ma l'orecchio le "ricostruisce" dalle armoniche |
| Tonalità | tonica (Do…Si) su cui sono costruite le scale Cromatica/Maggiore/Pentatonica |
| Estensione | ottave coperte dalla larghezza dell'inquadratura in Suona (1–4): meno ottave = note più larghe |
| Vibrato / Velocità vibrato | profondità (fino a ±3%) e frequenza (2–9 Hz) |
| Portamento | tempo di glissando tra le note (5–400 ms) |
| Eco | ripetizioni a ~0,3 s con retroazione |
| Calore | saturazione, da pulito a "valvolare" |
| Punto seguito | **Punta delle dita** (predefinito) o **Centro della mano** |
| Sensibilità | soglia minima del rilevamento: più alta se la mano si confonde con lo sfondo |
| Margine ai bordi | fascia laterale (0–30%) esclusa dalla tastiera |

### Riconoscimento della mano con l'IA (predefinito)

Il rilevatore predefinito è **MediaPipe Hand Landmarker** di Google
(`camera/TrackerMediaPipe.kt`), eseguito interamente sul telefono: riconosce
la forma della mano e ne dà 21 punti, disegnati nell'anteprima (la punta
dell'indice in giallo). Non dipende da sfondo o luce, non scambia viso o corpo
per una mano e non serve ricalibrare.

- **una mano**: la punta dell'indice decide nota (orizzontale) e volume (altezza);
- **due mani**, a scelta nel pannello 🎛 (*Con due mani*):
  - *Destra nota, sinistra volume* (predefinito), come un theremin vero: la
    destra suona la nota con l'indice, l'altezza del palmo sinistro regola il
    volume;
  - *Due voci*: ogni mano suona la propria nota con l'indice e ne regola il
    volume con l'altezza, su una seconda voce del synth (`synth2`); le mani sono
    assegnate alle voci da sinistra a destra, senza scambi quando una esce
    (`camera/SceltaMani.kt`, `camera/InseguitoreVoce.kt`). Nell'anteprima la
    seconda voce ha il cursore azzurro. In Impara si segue una sola nota.

Il modello (`hand_landmarker.task`, 7,8 MB) non è nel repository: il task
Gradle `scaricaModelloMano` lo scarica dal server ufficiale di Google negli
asset alla prima compilazione. Se il riconoscimento non è disponibile sul
telefono, l'app usa automaticamente il rilevamento per movimento, che resta
selezionabile nel pannello 🎛 (“Movimento sullo sfondo”).

### Sensore di prossimità come interruttore

Opzione nel pannello 🎛: coprendo il sensore di prossimità (in alto, vicino
all'altoparlante delle chiamate) il suono si zittisce all'istante, per
staccare le note. Il sensore non serve per la posizione: sulla quasi totalità
dei telefoni distingue solo "vicino" (≈5 cm) e "lontano".

### Rilevamento per movimento: precisione (`camera/MotionTracker.kt`)

- griglia di analisi 64×48 (prima 40×30);
- **esposizione bloccata** (AE/AWB lock via Camera2 interop) appena lo sfondo è
  imparato, e sbloccata a ogni Ricalibra: su uno sfondo bianco, quando entrava
  la mano la fotocamera ricalibrava l'esposizione e la mano "spariva";
- **compensazione dell'esposizione** comunque applicata in software (si
  sottrae lo scostamento mediano tra fotogramma e sfondo), per i dispositivi
  che non supportano il blocco;
- **soglia adattiva** al rumore del sensore, con minimo regolabile
  (Sensibilità), più rilevamento del **movimento** tra fotogrammi;
- scarto delle celle isolate e **filtro One Euro** sulla posizione: niente
  tremolio a mano ferma, nessun ritardo nei movimenti rapidi.
- si segue solo la **sagoma connessa più grande**: piccoli movimenti altrove
  (riflessi, viso) non spostano il punto e da soli non fanno suonare;
- **silenzio immediato quando la mano esce**: lo sfondo non "assorbe" mai la
  mano (niente sagome fantasma dove è rimasta a lungo), la presenza va a zero
  dopo 2 fotogrammi senza mano (~70 ms) e il suono si chiude in ~30 ms.

I test in `MotionTrackerTest` coprono sfondo bianco con mano poco
contrastata, cambio di esposizione all'ingresso della mano, cambio di luce
senza mano e rumore del sensore.

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

# Incastra le Forme (modulo `:forme`)

Gioco nato dal Theremin Cromatico: usa la stessa fotocamera anteriore e lo stesso
riconoscimento delle mani con MediaPipe (due mani insieme), ma l'immagine della
fotocamera occupa **tutto lo schermo** e sopra compaiono forme colorate (stella,
cubo, cerchio, triangolo, quadrato, cuore, rombo, luna, croce) e i loro incavi
tratteggiati.

- **Package**: `it.donatocannatello.incastraforme` — fisso, non va più cambiato
- **APK dell'ultima versione**: release `forme-latest` del repository
  (`incastra-le-forme.apk`), compilato in modalità release
- **Aggiornamenti**: ogni build ha un `versionCode` più alto ed è firmata sempre con la
  stessa chiave (`forme/firma.keystore`), quindi il nuovo APK si installa sopra il
  precedente mantenendo il record

## Come si gioca

1. Ci si mette davanti al telefono (in verticale) e si mostrano le mani alla fotocamera:
   sullo schermo compaiono lo scheletro di ogni mano e un cerchio fra pollice e indice:
   è il punto di presa.
2. **Afferrare**: si porta il punto di presa sopra una forma e si uniscono pollice e indice
   (funziona anche chiudendo la mano a pugno).
3. **Spostare**: la forma segue il punto fra pollice e indice; con due mani si spostano due
   forme insieme.
4. **Lasciare**: si aprono le dita. Se la forma è sopra il suo incavo si incastra
   (+100 punti, coriandoli), se è sopra un incavo sbagliato torna al suo posto;
   altrove resta dove è stata lasciata. Vicino al centro del suo incavo entra da sola.
5. Le forme con **✋✋** sono pesanti: si sollevano solo afferrandole con **tutte e due
   le mani** e seguono il punto medio fra le mani (+250 punti).
6. Completati tutti gli incastri si passa al livello successivo, con una forma in più
   (da 3 fino a 9) e più forme pesanti; gli incavi si alternano fra metà alta e bassa.
   Il bonus di velocità premia i livelli finiti in fretta; il record viene salvato.

Il dito sullo schermo funziona come una terza mano (utile per provare il gioco, o se il
riconoscimento IA non è disponibile sul telefono).

## Come funziona

- `camera/TrackerMani.kt`, `camera/AnalizzatoreMani.kt`: CameraX (16:9, come lo schermo
  del telefono) → fotogramma raddrizzato e specchiato → MediaPipe Hand Landmarker (2 mani).
- `gioco/Geometria.kt` (`MappaturaSchermo`): riporta i punti dell'immagine sullo schermo con
  la stessa regola dell'anteprima a tutto schermo (`FILL_CENTER`, che taglia i lati).
- `gioco/Gesti.kt`: pugno e pizzico, con soglie diverse per presa e rilascio (isteresi).
- `gioco/InseguitoreMani.kt`: assegna ogni mano al suo "posto" anche se MediaPipe le scambia
  fra un fotogramma e l'altro, leviga la posizione e tollera brevi perdite della mano.
- `gioco/Partita.kt`: regole, livelli, disposizione su griglia senza sovrapposizioni, punteggio.
  Non dipende da Android ed è coperta dai test in `forme/src/test`.
- `ui/DisegnoForme.kt`, `ui/Particelle.kt`, `audio/Suoni.kt`: grafica delle forme e degli
  incavi, coriandoli, effetti sonori sintetizzati all'avvio.

## Test

```bash
./gradlew :forme:testDebugUnitTest
```
