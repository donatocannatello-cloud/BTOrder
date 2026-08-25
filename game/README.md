# FrattaLogic

Passatempo/quiz logico per Android (Kotlin + Jetpack Compose). Un flusso
infinito di piccoli enigmi logici generati proceduralmente, con grafica
interamente vettoriale/frattale (nessuna immagine bitmap) e un accompagnamento
sonoro sintetizzato in tempo reale che si mescola in base a cosa succede in
partita.

- **Package**: `it.example.frattalogic`
- **minSdk**: 26 · **targetSdk / compileSdk**: 34

## Il gioco

Ad ogni turno viene proposto uno tra quattro tipi di enigma, scelto a caso:

1. **Sequenza numerica** — completa la sequenza (progressioni aritmetiche,
   geometriche, a passo alternato o di tipo Fibonacci).
2. **Sequenza di profondità frattale** — una figura (albero ricorsivo o
   triangolo di Sierpinski) cresce di un livello di ricorsione ad ogni passo:
   quale prosegue la sequenza?
3. **Sequenza di rotazione** — la figura ruota sempre dello stesso angolo:
   quale rotazione viene dopo?
4. **Intruso** — in una griglia di figure identiche, una ha una tonalità di
   colore leggermente diversa: individuala.

Ogni risposta corretta aumenta punteggio e serie (streak); la serie alza
gradualmente la difficoltà (più opzioni, distrattori più simili, rotazioni più
strette, profondità maggiori). Ogni 5 risposte corrette di fila scatta un
piccolo traguardo sonoro/visivo.

## Grafica vettoriale/frattale

Tutte le figure (`ui/FractalShapes.kt`) sono disegnate ricorsivamente con
`Canvas` di Compose — albero frattale, triangolo di Sierpinski, fiocco di
Koch — parametrizzate da profondità di ricorsione, rotazione e tonalità.
Anche lo sfondo animato del gioco è un fiocco di Koch in lenta rotazione. Non
c'è alcuna risorsa immagine: tutto è vettoriale e ridisegnato ad ogni frame.

## Audio procedurale

`audio/SoundEngine.kt` non riproduce alcun file audio: genera in tempo reale,
via `AudioTrack` in modalità streaming, un mix per somma additiva di:

- un **pad ambientale** continuo la cui frequenza segue la difficoltà
  corrente e la cui intensità cresce con la serie di risposte corrette;
- brevi **"blip" con inviluppo** che scattano sugli eventi di gioco (risposta
  corretta, sbagliata, traguardo di serie raggiunto).

Il risultato è un tappeto sonoro che cambia continuamente in base a quello
che succede in partita, generato interamente via codice.

## Come compilare in locale

```bash
./gradlew :game:assembleRelease
```

Richiede il keystore persistente in `game/keystore/frattalogic-release.keystore`
(vedi sotto): se manca, generalo con lo stesso comando `keytool` usato dalla CI
(si trova nello step "Assicura il keystore di firma persistente" di
`frattalogic-ci.yml`) prima di compilare in locale.

## Build e release automatiche (GitHub Actions)

- **`.github/workflows/frattalogic-ci.yml`** compila un APK di release ad
  ogni push/PR che tocca il modulo `game` e lo carica come artifact della
  run, per provarlo subito senza dover taggare una release.
- **`.github/workflows/frattalogic-release.yml`** compila lo stesso APK di
  release e pubblica una GitHub Release con l'APK allegato ogni volta che
  viene pushato un tag `frattalogic-vX.Y.Z`, ad esempio:

  ```bash
  git tag frattalogic-v0.1.0
  git push origin frattalogic-v0.1.0
  ```

  Ogni nuovo tag produce una nuova release con l'APK aggiornato: per
  aggiornare il gioco sul telefono basta scaricare l'APK dell'ultima release
  e installarlo sopra al precedente (stesso `applicationId`, stessa firma —
  vedi sotto — quindi Android lo installa come aggiornamento in-place, senza
  perdere i dati dell'app).

### Firma persistente (il motivo per cui gli aggiornamenti funzionano)

L'APK è firmato con un **keystore fisso e committato nel repo**
(`game/keystore/frattalogic-release.keystore`), non con il keystore di debug
di Android Gradle Plugin: quest'ultimo viene rigenerato automaticamente ad
ogni macchina/runner privo di un `~/.android/debug.keystore` preesistente, e
in CI ogni run parte da un runner nuovo — quindi ogni build avrebbe una firma
diversa, e Android rifiuterebbe di installarla come aggiornamento della
precedente (richiederebbe una disinstallazione, con perdita dei dati
locali). Il primo workflow `frattalogic-ci.yml` genera questo keystore una
tantum (se non lo trova già nel repo) e lo committa automaticamente; da quel
momento in poi ogni build — sia i push di verifica sia le release taggate —
riusa sempre la stessa identità di firma. La password non è pensata per
essere segreta (è la stessa filosofia del keystore di debug standard
"android"/"androiddebugkey"): serve solo coerenza tra le build, non
sicurezza. Non è quindi adatto alla pubblicazione su Play Store, che
richiede una firma di release dedicata e gestita a parte.

### Nota sulla verifica automatica della build in questo ambiente

Come per l'altra app del repository, in questo ambiente di sviluppo sandbox
l'accesso a `dl.google.com`/`maven.google.com` è bloccato dalla policy di
rete (403 dal proxy egress), quindi `./gradlew :game:assembleDebug` non è
stato eseguito con successo qui: Gradle non riesce a risolvere il plugin
Android Gradle Plugin né le dipendenze AndroidX/Compose senza quel
repository. Il codice Kotlin è stato scritto e riletto con attenzione, ma la
build va verificata con accesso reale al Google Maven repository — cosa che
avviene automaticamente nei workflow di GitHub Actions sopra descritti, che
girano su runner con rete non ristretta.

## Struttura del codice

```
game/src/main/java/it/example/frattalogic/
├── MainActivity.kt              punto di ingresso, avvia/ferma l'audio col ciclo di vita
├── audio/SoundEngine.kt         sintesi audio in tempo reale (AudioTrack)
├── engine/
│   ├── PuzzleModels.kt          modelli dati di un enigma
│   ├── PuzzleEngine.kt          generatori procedurali dei 4 tipi di enigma
│   └── GameViewModel.kt         stato di gioco (punteggio, serie, difficoltà)
└── ui/
    ├── FractalShapes.kt         disegno ricorsivo delle figure frattali
    ├── GameScreen.kt            schermata di gioco Compose
    └── theme/                   palette colori e tipografia
```
