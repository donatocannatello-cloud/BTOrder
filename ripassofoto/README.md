# RipassoFoto

App Android (Kotlin + Jetpack Compose) pensata per uno studente di liceo: si
fotografa la pagina del libro da studiare, l'app ne estrae il testo tramite
OCR **on-device** e genera automaticamente una serie di domande di verifica
(scelta multipla e vero/falso) per ripassare, il tutto senza inviare nulla a
server esterni.

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
4. **`GeneratoreDomande.kt`** genera le domande di verifica con euristiche
   testuali, senza alcun servizio esterno o chiave API:
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
   avanzamento, evidenzia risposta corretta/sbagliata dopo la selezione e
   calcola il punteggio finale, mostrato in **`SchermataRisultato.kt`** con
   la possibilità di rifare subito il quiz (le domande vengono rigenerate,
   quindi la combinazione può variare leggermente a ogni tentativo).
6. Ogni pagina fotografata (titolo, testo estratto, percorso della foto,
   data e ultimo punteggio) viene salvata in locale con **Room**
   (`AppDatabase.kt`, `PaginaStudioDao.kt`) tramite `StudioRepository.kt`, e
   compare nell'elenco della home per essere ripassata di nuovo in qualsiasi
   momento (**`SchermataDettaglioPagina.kt`**).

## Permessi

- `CAMERA` — richiesto a runtime da `SchermataFotocamera` per fotografare la
  pagina da studiare.

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

- Il generatore di domande (`GeneratoreDomande.kt`) è euristico e locale: fa
  bene su pagine con frasi complete e ricche di nomi/numeri (storia,
  geografia, scienze, letteratura), meno bene su testi molto brevi, elenchi
  puntati o formule, da cui potrebbe non riuscire a generare domande
  sensate. L'interfaccia `Domanda`/`StudioViewModel.generaNuoveDomande` è
  pensata per poter essere sostituita in futuro da un generatore basato su
  un servizio di IA (es. via API), a costo di introdurre una dipendenza di
  rete e la gestione di una chiave API.
- La qualità delle domande dipende dalla qualità dell'OCR: foto sfocate,
  poco illuminate o con la pagina non ben inquadrata producono testo pieno
  di errori. Per questo la schermata di revisione permette sempre di
  correggere il testo a mano prima di generare il quiz.
- Le domande non vengono salvate su disco: solo il testo della pagina e
  l'ultimo punteggio ottenuto. Ogni "Rifai il quiz" rigenera le domande da
  zero a partire dal testo salvato, quindi la combinazione esatta di
  domande può cambiare tra un tentativo e l'altro.
