# Abisso Frattale

Passatempo esplorativo per Android (Kotlin + Jetpack Compose): scendi sempre
più in profondità dentro un frattale, cercando ad ogni livello l'unico
elemento dissonante che rompe l'autosimilarità. Grafica interamente
vettoriale (nessuna immagine bitmap) e una colonna sonora sintetizzata in
tempo reale che si arricchisce di nuovi strumenti mano a mano che si scende.

- **Package**: `it.example.frattalogic`
- **minSdk**: 26 · **targetSdk / compileSdk**: 34

## Il gioco

Ogni schermata ("camera") mostra un nucleo frattale al centro e un anello di
nodi attorno: tutti i nodi condividono la stessa regola generativa (stesso
tipo di frattale, stessa profondità di ricorsione, stessa rotazione, stessa
tonalità) tranne **uno**, a cui è stata alterata una sola proprietà — è la
nota dissonante da individuare a orecchio... anzi, a occhio.

- **Toccare il nodo dissonante** fa scendere di un livello: si genera una
  nuova camera più difficile (più nodi, differenza più sottile), il punteggio
  cresce e nel mix sonoro può entrare un nuovo strumento (vedi sotto).
- **Toccare un nodo normale** fa risalire di un livello e scatena un breve
  cluster dissonante nella musica — un passo indietro, non un game over: la
  discesa continua.

Il generatore procedurale (`engine/DiveEngine.kt`) sceglie ad ogni camera
quale delle tre proprietà alterare (tonalità, rotazione o profondità di
ricorsione) e di quanto: l'ampiezza della perturbazione si restringe con la
profondità, rendendo la dissonanza sempre più sottile da scovare.

## Grafica vettoriale/frattale

Tutte le figure (`ui/FractalShapes.kt`) sono disegnate ricorsivamente con
`Canvas` di Compose — albero frattale, triangolo di Sierpinski, fiocco di
Koch — parametrizzate da profondità di ricorsione, rotazione e tonalità.
Anche lo sfondo animato è lo stesso nucleo della camera corrente, in lenta
rotazione. Non c'è alcuna risorsa immagine: tutto è vettoriale e ridisegnato
ad ogni frame.

## Audio procedurale multi-strumento

`audio/SoundEngine.kt` non riproduce alcun file audio: sintetizza in tempo
reale, via `AudioTrack` in modalità streaming, quattro voci che si mescolano
per somma additiva e si aggiungono una alla volta scendendo in profondità:

1. **basso** — drone continuo un'ottava sotto la nota fondamentale (sempre
   attivo);
2. **arpeggio** — onda triangolare che percorre una scala pentatonica minore,
   dalla profondità 2, sempre più veloce scendendo;
3. **pad armonico** — accordo sostenuto di tre seni (fondamentale, terza
   minore, quinta), dalla profondità 4;
4. **percussione** — un breve impulso di rumore filtrato ad ogni nota
   dell'arpeggio, dalla profondità 6.

La nota fondamentale scende lentamente con la profondità (sensazione di
sprofondare sempre più in basso). Trovare la dissonanza fa suonare un breve
accordo consonante (ottave e quinta); toccare un nodo sbagliato fa suonare un
cluster dissonante (seconda minore + tritono). Tutto generato via codice,
nessun file audio incluso.

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
├── MainActivity.kt              punto di ingresso, avvia/ferma l'audio col ciclo di vita
├── audio/SoundEngine.kt         sintesi multi-strumento in tempo reale (AudioTrack)
├── engine/
│   ├── DiveModels.kt            modelli dati di una camera (nucleo + anello di nodi)
│   ├── DiveEngine.kt            genera ogni camera e sceglie/dosa la dissonanza
│   └── DiveViewModel.kt         stato della discesa (punteggio, profondità, esito)
└── ui/
    ├── FractalShapes.kt         disegno ricorsivo delle figure frattali
    ├── DiveScreen.kt            schermata di gioco Compose (nucleo + anello tappabile)
    └── theme/                   palette colori e tipografia
```
