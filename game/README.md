# Abisso Frattale

Immersione per Android (Kotlin + Jetpack Compose): ci si inabissa senza
sosta in un vero insieme di Mandelbrot, calcolato in tempo reale — denso di
colore e di dettaglio, mai un'immagine precaricata. La navigazione è
principalmente verticale: si scende sempre più a fondo nello zoom, con un
joystick a schermo che regola la velocità di discesa e una lieve deriva
laterale per scegliere dove infilarsi. La colonna sonora, multi-strumento,
cambia di continuo seguendo la profondità raggiunta. Ogni tanto, scendendo,
la corrente "si increspa": un breve enigma — trovare l'unico elemento
dissonante in un anello di figure frattali — punteggia la discesa senza
interromperne il flusso.

- **Package**: `it.example.frattalogic`
- **minSdk**: 26 · **targetSdk / compileSdk**: 34

## Il gioco

- **Discesa continua**: il livello di zoom nell'insieme di Mandelbrot
  scende da solo; il joystick a schermo (in basso a destra) ne regola
  soprattutto la **velocità verticale** (spingere in su per scendere più in
  fretta) e in misura minore la **deriva laterale** (per curiosare in una
  direzione piuttosto che un'altra). Non c'è un obiettivo da "vincere": è
  un'immersione senza fine, sempre più in profondità, sempre diversa.
- **Un mondo denso e mutevole**: ogni fotogramma è un vero calcolo
  dell'insieme di Mandelbrot (algoritmo a tempo di fuga, colorazione
  continua sul numero di iterazioni), a partire da un punto della "valle
  dei cavallucci marini" sul bordo dell'insieme — una zona nota per la sua
  ricchezza di dettaglio a qualunque livello di zoom. Il colore stesso
  ruota lentamente con la profondità, così il paesaggio non si ripete mai
  davvero.
- **Evento bonus**: ogni tanti livelli di zoom la discesa apre un breve
  enigma — un nucleo frattale centrale e un anello di figure attorno, tutte
  identiche tranne una (tonalità, rotazione o profondità di ricorsione
  alterata): toccarla dà punti; sbagliare non interrompe la discesa.

## Grafica: un vero frattale, non un'illustrazione

A differenza delle prime versioni (figure vettoriali disegnate a mano —
alberi ricorsivi, Sierpinski, Koch, ancora usate per l'evento bonus in
`ui/FractalShapes.kt`), il paesaggio principale è **l'insieme di Mandelbrot
vero e proprio**: `engine/FractalField.kt` calcola punto per punto, ad ogni
fotogramma, se ciascun pixel appartiene all'insieme (tempo di fuga, fino a
qualche centinaia di iterazioni man mano che si scende) e lo colora con una
formula di colorazione continua sul numero di iterazioni frazionario, per
evitare le fasce nette e ottenere invece gradazioni morbide. Il calcolo
avviene su una griglia ridotta (per restare fluido su telefono) e viene
scalato per riempire lo schermo — nessuna immagine bitmap inclusa nell'app,
tutto è calcolato via codice.

## Audio procedurale multi-strumento, continuo con la profondità

`audio/SoundEngine.kt` non riproduce alcun file audio: sintetizza in tempo
reale, via `AudioTrack` in modalità streaming, quattro voci che si mescolano
per somma additiva e si aggiungono una alla volta scendendo:

1. **basso** — drone continuo un'ottava sotto la nota fondamentale (sempre
   attivo);
2. **arpeggio** — onda triangolare che percorre una scala pentatonica minore
   (dalla profondità 2 in su);
3. **pad armonico** — accordo sostenuto di tre seni (dalla profondità 4);
4. **percussione** — un breve impulso di rumore filtrato ad ogni nota
   dell'arpeggio (dalla profondità 6).

La nota fondamentale e il tempo dell'arpeggio seguono il livello di zoom
**ad ogni fotogramma**, non solo a soglie discrete: la musica cambia
davvero mentre si naviga. Risolvere l'evento bonus fa suonare un breve
accordo consonante; sbagliare fa suonare un cluster dissonante.

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

### Nota sulla precisione numerica dello zoom

Lo zoom usa numeri in doppia precisione (`Double`, ~15-17 cifre
significative): è il limite pratico di qualunque semplice "zoomer" di
Mandelbrot senza aritmetica a precisione arbitraria. Dopo una discesa molto
lunga (ordine dei minuti a velocità sostenuta) il dettaglio comincia a
sgranarsi per esaurimento di precisione: è un limite noto e accettato, non
un bug — implementare l'aritmetica a precisione arbitraria (`BigDecimal` o
simili) per il calcolo per-pixel in tempo reale non sarebbe praticabile su
telefono.

## Struttura del codice

```
game/src/main/java/it/example/frattalogic/
├── MainActivity.kt              punto di ingresso, avvia/ferma audio e loop col ciclo di vita
├── audio/SoundEngine.kt         sintesi multi-strumento in tempo reale (AudioTrack)
├── engine/
│   ├── FractalField.kt          calcola l'insieme di Mandelbrot (tempo di fuga + colorazione continua)
│   ├── ImmersioneModels.kt      stato della discesa (finestra sul piano complesso, fotogramma, fase)
│   ├── ExplorationViewModel.kt  loop di discesa, sterzo, soglie di profondità, evento bonus
│   ├── DiveModels.kt            modelli dell'evento bonus (nucleo + anello di nodi)
│   └── DiveEngine.kt            genera ogni evento bonus e dosa la dissonanza
└── ui/
    ├── FractalShapes.kt         disegno ricorsivo delle figure frattali (usato dall'evento bonus)
    ├── ExplorationScreen.kt     canvas della discesa, joystick, overlay dell'evento bonus
    └── theme/                   palette colori e tipografia
```
