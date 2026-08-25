# Abisso Frattale

Esplorazione fluida per Android (Kotlin + Jetpack Compose): si naviga con
comandi a schermo in un mare frattale generato dinamicamente, scoprendo
mondi nuovi mano a mano che ci si allontana. Grafica interamente vettoriale
(nessuna immagine bitmap) e una colonna sonora sintetizzata in tempo reale
che si arricchisce di strumenti mondo dopo mondo. Il breve enigma "trova la
dissonanza" delle versioni precedenti resta, come evento bonus ad ogni
cambio di mondo.

- **Package**: `it.example.frattalogic`
- **minSdk**: 26 · **targetSdk / compileSdk**: 34

## Il gioco

- **Navigazione**: il vascello avanza da solo; il joystick a schermo (in
  basso a destra) ne imposta rotta (scostamento orizzontale) e velocità
  (scostamento verticale) in modo continuo — si guida come una barca alla
  deriva, non con input discreti a turni.
- **Mondo procedurale**: il paesaggio frattale attorno al vascello
  (`engine/MondoGenerator.kt`) è generato al volo da un hash deterministico
  delle coordinate: nessuna mappa precaricata, ma tornando in un punto già
  visitato si ritrova lo stesso paesaggio.
- **Nuovi mondi**: superata una certa distanza si entra in un nuovo "mondo"
  (nome, tipo di frattale dominante e tonalità diversi — una nuova regione
  da scoprire) e scatta l'**evento bonus**: il nucleo del mondo "vacilla",
  bisogna toccare tra un anello di figure frattali l'unica dissonante
  (tonalità, rotazione o profondità di ricorsione alterata) per stabilizzare
  l'ingresso. Risolverlo dà punti; sbagliare non blocca la partita, si
  riprende subito a navigare.

## Grafica vettoriale/frattale

Tutte le figure (`ui/FractalShapes.kt`) sono disegnate ricorsivamente con
`Canvas` di Compose — albero frattale, triangolo di Sierpinski, fiocco di
Koch — parametrizzate da profondità di ricorsione, rotazione e tonalità. Il
paesaggio che si vede navigando è composto da istanze di queste stesse
figure, posizionate proceduralmente nel mondo. Non c'è alcuna risorsa
immagine: tutto è vettoriale e ridisegnato ad ogni frame.

## Audio procedurale multi-strumento

`audio/SoundEngine.kt` non riproduce alcun file audio: sintetizza in tempo
reale, via `AudioTrack` in modalità streaming, quattro voci che si mescolano
per somma additiva e si aggiungono una alla volta esplorando mondi nuovi:

1. **basso** — drone continuo un'ottava sotto la nota fondamentale (sempre
   attivo);
2. **arpeggio** — onda triangolare che percorre una scala pentatonica minore;
3. **pad armonico** — accordo sostenuto di tre seni;
4. **percussione** — un breve impulso di rumore filtrato ad ogni nota
   dell'arpeggio.

La nota fondamentale scende lentamente mondo dopo mondo. Risolvere l'evento
bonus fa suonare un breve accordo consonante; sbagliare fa suonare un
cluster dissonante. Tutto generato via codice, nessun file audio incluso.

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
│   ├── ExplorationModels.kt     modelli dati della navigazione (vascello, mondo, elementi)
│   ├── MondoGenerator.kt        genera il paesaggio frattale in modo procedurale/deterministico
│   ├── ExplorationViewModel.kt  loop di gioco, sterzo, transizioni di mondo, evento bonus
│   ├── DiveModels.kt            modelli dell'evento bonus (nucleo + anello di nodi)
│   └── DiveEngine.kt            genera ogni evento bonus e dosa la dissonanza
└── ui/
    ├── FractalShapes.kt         disegno ricorsivo delle figure frattali
    ├── ExplorationScreen.kt     canvas di navigazione, joystick, overlay dell'evento bonus
    └── theme/                   palette colori e tipografia
```
