# RipassoFoto

App Android (Kotlin + Jetpack Compose) pensata per uno studente di liceo: si
fotografa la pagina del libro da studiare, l'app ne estrae il testo tramite
OCR **on-device** e genera una serie di domande di verifica (scelta multipla
e vero/falso) per ripassare. Le domande possono essere generate in due modi:
localmente sul telefono con euristiche testuali (sempre disponibile, anche
offline), oppure — se lo studente configura una propria chiave API — con
**Claude Opus 5** (Anthropic), per un'analisi più approfondita del testo e
domande di livello più alto. Vedi [Generazione delle domande con l'IA](#generazione-delle-domande-con-lia-claude).

- **Package**: `it.example.ripassofoto`
- **minSdk**: 26 (Android 8) — compatibilità ampia per CameraX e ML Kit
- **targetSdk / compileSdk**: 34

## Come funziona

1. Dalla schermata iniziale (**`SchermataHome.kt`**) si tocca il pulsante "+"
   per fotografare una nuova pagina. **`SchermataFotocamera.kt`** usa
   **CameraX** (`Preview` + `ImageCapture`) per mostrare l'anteprima e
   salvare lo scatto in `filesDir/pagine/`.
2. **`RiconoscimentoTesto.kt`** passa la foto a **ML Kit Text Recognition**
   (`com.google.mlkit:text-recognition`), che gira interamente sul
   dispositivo: nessuna immagine o testo lascia il telefono. Il modello
   viene scaricato una tantum da Google Play Services al primo utilizzo;
   dopodiché funziona anche offline.
3. **`SchermataRevisioneTesto.kt`** mostra il testo riconosciuto in un campo
   modificabile, così lo studente può correggere eventuali errori di OCR
   prima di generare le domande (utile con foto poco nitide o font
   particolari).
4. **`StudioViewModel.generaNuoveDomande`** genera le domande. Se è
   configurata una chiave API di Claude (vedi sotto) usa **`ClienteClaude.kt`**;
   altrimenti, o se la chiamata fallisce per qualunque motivo (rete assente,
   chiave non valida, errore del servizio...), ricade su
   **`GeneratoreDomande.kt`**, che genera le domande con euristiche testuali
   locali, senza alcun servizio esterno o chiave API:
   - il testo viene diviso in frasi "utili" (né troppo brevi né troppo
     lunghe);
   - per ogni frase viene individuata una parola chiave (nome proprio,
     numero o parola lunga e non comune);
   - si generano domande **"completa la frase"** a scelta multipla (la
     parola chiave viene nascosta e sostituita con parole chiave prese da
     altre frasi come alternative) e domande **vero/falso** (l'affermazione
     originale, oppure una versione alterata scambiando la parola chiave con
     un'altra pertinente presa dal resto del testo).
5. **`SchermataQuiz.kt`** presenta le domande una alla volta con una barra di
   avanzamento, evidenzia risposta corretta/sbagliata dopo la selezione (e,
   se generata con l'IA, mostra anche la spiegazione della risposta) e
   calcola il punteggio finale, mostrato in **`SchermataRisultato.kt`** con
   la possibilità di rifare subito il quiz (le domande vengono rigenerate,
   quindi la combinazione può variare leggermente a ogni tentativo).
6. Ogni pagina fotografata (titolo, testo estratto, percorso della foto,
   data e ultimo punteggio) viene salvata in locale con **Room**
   (`AppDatabase.kt`, `PaginaStudioDao.kt`) tramite `StudioRepository.kt`, e
   compare nell'elenco della home per essere ripassata di nuovo in qualsiasi
   momento (**`SchermataDettaglioPagina.kt`**).

## Generazione delle domande con l'IA (Claude)

Dalla home, l'icona ingranaggio apre **`SchermataImpostazioni.kt`**, dove si
può incollare una propria chiave API di [Anthropic](https://console.anthropic.com/)
(quella che inizia con `sk-ant-...`). Da quel momento, ogni "Genera domande"
o "Rifai il quiz":

1. chiama direttamente `https://api.anthropic.com/v1/messages` dal telefono
   (**`ClienteClaude.kt`**, via OkHttp — non un SDK server-side: su Android
   evita i problemi noti di R8/Jackson e riduce le dipendenze rispetto
   all'SDK Java ufficiale di Anthropic, pensato per uso server-side), con il
   modello **`claude-opus-5`** e **output strutturato**
   (`output_config.format` con schema JSON) per ottenere sempre un elenco di
   domande in un formato valido, invece di dover analizzare testo libero;
2. chiede a Claude di generare domande di comprensione, collegamento tra
   concetti, inferenza e applicazione — non semplice richiamo mnemonico —
   più una breve spiegazione della risposta corretta per ciascuna domanda;
3. in caso di errore (chiave non valida, rete assente, servizio non
   disponibile, risposta non valida) mostra un avviso e ricade
   automaticamente sul generatore locale, così l'app resta sempre
   utilizzabile.

**Note importanti:**

- La chiave è conservata **cifrata sul dispositivo** (`ChiaveApiStore.kt`,
  Android Keystore + `EncryptedSharedPreferences`) e non viene mai inviata
  altrove se non nell'header `x-api-key` delle richieste dirette ad
  Anthropic.
- Quando questa modalità è attiva, **il testo della pagina fotografata viene
  inviato ad Anthropic** per generare le domande. Senza chiave configurata
  non viene inviato nulla a nessun servizio esterno (l'OCR resta comunque
  on-device in entrambi i casi).
- L'utilizzo è **a carico dell'account Anthropic dello studente** (non c'è
  alcuna chiave o server condiviso nell'app); ogni generazione è una
  richiesta a `claude-opus-5`.

## Permessi

- `CAMERA` — richiesto a runtime da `SchermataFotocamera` per fotografare la
  pagina da studiare.
- `INTERNET` — usato solo per le chiamate a `api.anthropic.com` quando è
  configurata una chiave API (vedi sopra); nessun altro traffico di rete.

## Come compilare

1. Apri la cartella **`ripassofoto/`** (o la root del repository) con
   **Android Studio** (Koala o successivo consigliato, richiede AGP 8.4+).
2. Lascia che Android Studio scarichi le dipendenze (Android Gradle Plugin,
   AndroidX, Compose, CameraX, ML Kit, Room) e sincronizzi il progetto.
3. Da terminale, in alternativa (dalla root del repository):
   ```bash
   ./gradlew :ripassofoto:assembleDebug
   ```
   L'APK di debug viene generato in
   `ripassofoto/build/outputs/apk/debug/`.

## Build automatica su GitHub Actions

Il workflow **[`ripassofoto-apk.yml`](../.github/workflows/ripassofoto-apk.yml)**
compila automaticamente l'APK di debug a ogni push che tocca il modulo
`ripassofoto/` (o su richiesta manuale, `workflow_dispatch`), usando i
runner GitHub-hosted (che, a differenza di alcuni ambienti di sviluppo
sandboxed, hanno accesso senza restrizioni a `dl.google.com` /
`maven.google.com`). Ogni esecuzione:

1. compila `./gradlew :ripassofoto:assembleDebug`;
2. carica l'APK come **artifact del workflow** (visibile nella pagina della
   run su GitHub Actions, utile per debug e per le pull request);
3. pubblica/aggiorna la **release con tag fisso `ripassofoto-latest`**,
   sostituendo l'APK precedente con quello appena compilato — questo dà un
   link stabile che punta sempre all'ultima build, aggiornato a ogni
   modifica:
   `https://github.com/donatocannatello-cloud/BTOrder/releases/tag/ripassofoto-latest`

### Nota sulla verifica automatica della build in questo ambiente

Nell'ambiente sandboxed usato per scrivere questo codice, `./gradlew
assembleDebug` **non è stata verificata end-to-end** localmente: la policy
di rete del sandbox blocca `dl.google.com` / `maven.google.com` (risposta
403 dal proxy egress), il repository Maven da cui si scarica l'Android
Gradle Plugin e le librerie AndroidX/Compose/CameraX/ML Kit/Room. Il codice
Kotlin è stato scritto e riletto con attenzione (tipi, firme delle API,
import), ma la verifica end-to-end reale avviene tramite il workflow GitHub
Actions descritto sopra, che gira su runner con accesso di rete completo.

## Limiti noti

- Il generatore locale (`GeneratoreDomande.kt`, usato senza chiave API o come
  fallback) è euristico: fa bene su pagine con frasi complete e ricche di
  nomi/numeri (storia, geografia, scienze, letteratura), meno bene su testi
  molto brevi, elenchi puntati o formule, da cui potrebbe non riuscire a
  generare domande sensate. Con una chiave API configurata questo limite non
  c'è più, perché è Claude ad analizzare il testo.
- La generazione con l'IA richiede una connessione a Internet e una chiave
  API Anthropic con credito disponibile; l'utilizzo non è gratuito (è
  addebitato sull'account Anthropic dello studente) e non è pensata per
  funzionare offline — per quello resta il generatore locale.
- La qualità delle domande dipende dalla qualità dell'OCR: foto sfocate,
  poco illuminate o con la pagina non ben inquadrata producono testo pieno
  di errori. Per questo la schermata di revisione permette sempre di
  correggere il testo a mano prima di generare il quiz.
- Le domande non vengono salvate su disco: solo il testo della pagina e
  l'ultimo punteggio ottenuto. Ogni "Rifai il quiz" rigenera le domande da
  zero a partire dal testo salvato, quindi la combinazione esatta di
  domande può cambiare tra un tentativo e l'altro.
