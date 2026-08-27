# Abisso Frattale

Immersione per Android (Kotlin + Jetpack Compose): si avanza in un tunnel
frattale generato proceduralmente e disegnato **interamente in vettoriale,
in tempo reale** — nessuna immagine raster, nessun bitmap: ogni fotogramma
ridisegna da zero le figure frattali (alberi ricorsivi, triangoli di
Sierpinski, fiocchi di Koch) che compongono la scena. Due joystick
indipendenti: uno regola solo la velocità di avanzamento, l'altro sposta il
punto di fuga in ogni direzione. **A riposo (nessun joystick azionato) la
camera non si muove affatto.** La colonna sonora, multi-strumento, cambia di
continuo seguendo la profondità raggiunta. Ogni tanto la corrente "si
increspa": un breve enigma — trovare l'unico elemento dissonante in un
anello di figure frattali — punteggia l'avanzamento senza interromperne il
flusso.

- **Package**: `it.example.frattalogic`
- **minSdk**: 26 · **targetSdk / compileSdk**: 34

## Il gioco

- **Due joystick, movimento solo su comando**: quello a sinistra regola
  soltanto la **velocità di avanzamento** (su = si avanza più in fretta,
  giù = si retrocede; al centro, fermo); quello a destra sposta **il punto
  di fuga** in ogni direzione (sinistra/su/destra/giù). Se non si tocca
  alcun joystick, la scena resta immobile: nessun movimento automatico.
- **Tunnel vettoriale dinamico**: la profondità raggiunta determina la fase
  ciclica di un gruppo di figure frattali — le più vicine (grandi) superano
  lo schermo e uscendo di scena, le più lontane (piccole) emergono al
  centro — dando la sensazione di avanzare attraverso una galleria di forme
  che si rinnovano di continuo. Tipo di frattale, tonalità e rotazione di
  ogni figura variano con la profondità, così la scena non si ripete mai
  davvero. Non c'è un obiettivo da "vincere": è un'immersione senza fine.
- **Evento bonus**: ogni tanti livelli di avanzamento si apre un breve
  enigma — un nucleo frattale centrale e un anello di figure attorno, tutte
  identiche tranne una (tonalità, rotazione o profondità di ricorsione
  alterata): toccarla dà punti; sbagliare non interrompe l'avanzamento.

## Grafica: puro vettoriale, nessun bitmap

Tutte le figure (`ui/FractalShapes.kt`) sono disegnate ricorsivamente con
`Canvas` di Compose — albero frattale, triangolo di Sierpinski, fiocco di
Koch — parametrizzate da profondità di ricorsione, rotazione e tonalità.
Il tunnel (`ui/ExplorationScreen.kt`, `ImmersioneCanvas`) le compone in più
livelli, ciascuno con una propria fase/scala/rotazione/tonalità calcolate
dalla profondità corrente, e le ridisegna da zero ad ogni fotogramma: non
c'è alcun buffer di pixel, alcuna bitmap, alcuna immagine precaricata — solo
disegno di percorsi vettoriali in tempo reale, ad ogni framerate del
dispositivo.

## Audio procedurale multi-strumento, continuo con la profondità

`audio/SoundEngine.kt` non riproduce alcun file audio: sintetizza in tempo
reale, via `AudioTrack` in modalità streaming, quattro voci che si mescolano
per somma additiva e si aggiungono una alla volta avanzando:

1. **basso** — drone continuo un'ottava sotto la nota fondamentale (sempre
   attivo);
2. **arpeggio** — onda triangolare che percorre una scala pentatonica minore
   (dalla profondità 2 in su);
3. **pad armonico** — accordo sostenuto di tre seni (dalla profondità 4);
4. **percussione** — un breve impulso di rumore filtrato ad ogni nota
   dell'arpeggio (dalla profondità 6).

La nota fondamentale e il tempo dell'arpeggio seguono la profondità **ad
ogni fotogramma**, non solo a soglie discrete. Risolvere l'evento bonus fa
suonare un breve accordo consonante; sbagliare fa suonare un cluster
dissonante.

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
rete (403 dal proxy egress), quindi `./gradlew :game:assembleRelease` non è
stato eseguito con successo qui: Gradle non riesce a risolvere il plugin
Android Gradle Plugin né le dipendenze AndroidX/Compose senza quel
repository. Il codice Kotlin è stato scritto e riletto con attenzione, ma la
build va verificata con accesso reale al Google Maven repository — cosa che
avviene automaticamente nei workflow di GitHub Actions sopra descritti, che
girano su runner con rete non ristretta.

## Struttura del codice

```
game/src/main/java/it/example/frattalogic/
├── MainActivity.kt              punto di ingresso, avvia/ferma audio e loop col ciclo di vita
├── audio/SoundEngine.kt         sintesi multi-strumento in tempo reale (AudioTrack)
├── engine/
│   ├── ImmersioneModels.kt      stato dell'avanzamento (profondità, punto di fuga, fase)
│   ├── ExplorationViewModel.kt  loop di avanzamento, sterzo, soglie, evento bonus
│   ├── DiveModels.kt            modelli dell'evento bonus (nucleo + anello di nodi)
│   └── DiveEngine.kt            genera ogni evento bonus e dosa la dissonanza
└── ui/
    ├── FractalShapes.kt         disegno ricorsivo delle figure frattali (vettoriale)
    ├── ExplorationScreen.kt     tunnel vettoriale, joystick, overlay dell'evento bonus
    └── theme/                   palette colori e tipografia
```
