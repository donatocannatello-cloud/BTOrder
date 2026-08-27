# Frattale

Esploratore solitario 3D dentro un universo frattale generato via shader
(raymarching), senza HUD né testo: tutto passa da immagine, suono e
movimento. Vedi la richiesta originale nell'issue/prompt per il concept
completo.

Questo sotto-progetto vive nella cartella `frattale/` di questa repo, che
contiene anche l'app Android indipendente `ChiamateBT` (root della repo):
i due progetti non condividono nulla.

## Piano tecnico

**Rendering**: WebGL2 "raw" (nessun Three.js) — un solo triangolo
full-screen (`gl_VertexID`, nessun vertex buffer) e tutto il lavoro nel
fragment shader (`src/shaders/raymarch.ts`), che fa raymarching di un
Mandelbulb (sphere tracing + distance estimator classico, potenza
variabile). Niente Three.js perché qui non serve uno scene graph: c'è una
sola "geometria" (il frattale, definito analiticamente nello shader) e un
solo draw call; saltare Three.js tiene il bundle minuscolo (~9 KB JS) e
riduce l'overhead nella WebView Android.

> Perché non "vettoriale": un frattale 3D via raymarching non ha un
> equivalente vettoriale (SVG-like) — non sono forme/path, è una superficie
> procedurale risolta pixel per pixel lungo ogni raggio (per questo ogni
> renderer di questo tipo, da Shadertoy a Mandelbulber, è raster per
> natura). La leva reale per alleggerirlo è risoluzione/step di calcolo per
> pixel, vedi **Qualità adattiva** più sotto.

**Qualità adattiva** (`src/quality.ts`): il DPR è limitato a 1.5 in
partenza (un telefono a 3x non deve renderizzare a 3x: il costo del
raymarching scala con il numero di pixel), poi un `QualityManager` misura
il tempo-frame reale ogni ~900ms e regola `renderScale` (risoluzione
interna del canvas, upscalata via CSS) e `raySteps` (budget di step di
sphere-tracing, ora un uniform `uRaySteps` invece della costante fissa
`MAX_STEPS`): se il framerate scende sotto 30fps degrada prima la
risoluzione poi gli step, se sta comodo sopra 55fps risale. Cambi piccoli e
non troppo frequenti per evitare "pompaggi" visibili. Il render loop si
ferma del tutto quando la pagina è in background (`visibilitychange`).

**Camera** (`src/camera.ts`): orbitale, non più a volo libero. Il mondo ha
un centro (l'origine, dove vive il frattale) e la camera lo guarda sempre —
la posizione è interamente definita da coordinate sferiche attorno
all'origine (`radius`, `azimuth`, `elevation`), niente quaternioni/roll.
Due modi di muoversi: **orbitare** attorno al centro (azimut orizzontale +
elevazione verticale, con l'elevazione bloccata a ~83° per non sorvolare i
poli) e **avvicinarsi/allontanarsi** dal centro (`radius`, clampato tra 1.5
e 9.0 unità). Stick/tastiera pilotano una velocità di orbita smussata
dall'inerzia della camera; il trascinamento col mouse invece ruota per
manipolazione diretta, senza inerzia propria (ci si aspetta che un drag
risponda 1:1). Il FOV cresce leggermente con la velocità di orbita/zoom,
anch'esso smussato.

**Input**: `src/touchControls.ts`. Il target è Android, quindi il touch è
lo schema *primario*: due controlli **sempre visibili**, ancorati agli
angoli, con un highlight quando in uso (la prima versione, invisibile
finché non tocchi, risultava confusa senza un riferimento fisso a
schermo). A **sinistra** uno stick circolare per orbitare: asse
orizzontale = azimut, asse verticale = elevazione — le "4 direzioni
cardinali". A **destra** una **levetta verticale**, con un design
deliberatamente diverso da uno stick (un binario allungato, non un disco,
con un segno vuoto in alto e uno pieno in basso) per segnalare che governa
un solo asse: avvicina/allontana dal centro. Entrambi sono "a molla":
tornano a riposo al rilascio. Il tocco iniziale è accettato in tutta la
metà schermo corrispondente, non serve precisione sul controllo. Su
desktop: `WASD` = orbita (le stesse 4 direzioni cardinali), `Space`/`Shift`
= vicino/lontano, trascinamento col mouse o rotellina per orbitare/zoomare,
`Ctrl` per accelerare — utili solo per un test rapido da laptop durante lo
sviluppo.

**Dettaglio dinamico (LOD)**: il numero di iterazioni del Mandelbulb
(`uMaxIter`, uniform invece di costante) cresce da 5 a 10 mano a mano che
`camera.radius` si avvicina al centro (`fractalDetail()` in `main.ts` — con
la camera orbitale il raggio *è* già la distanza esatta dal centro, non
serve ricalcolarla). Da lontano la forma resta liscia e semplice
(economica), avvicinandosi emergono via via le increspature più fini —
altrimenti il frattale sembra una texture statica indipendentemente da
quanto ci si avvicina.

**Livello 2 — evoluzione temporale + reazione alla camera** (in
`shaders/raymarch.ts`): la potenza del Mandelbulb "respira" lentamente nel
tempo (oscillazione sinusoidale calcolata in `main.ts`, periodo ~125s); il
dominio del frattale ruota lentamente su sé stesso nel tempo via `uTime`,
indipendentemente dall'input; la palette ha una deriva cromatica lenta
(cosine palette); le superfici vicine alla posizione della camera si
illuminano leggermente e l'esponente locale riceve una piccola oscillazione
in più ("presence" reaction) — calcolata nel frame camera/mondo *non*
ruotato, così la "zona che reagisce" segue davvero la posizione reale della
camera invece di scivolare via mentre il frattale ruota.

**Stile visivo — wireframe, non superficie illuminata**: lo shading non è
più Lambertiano/fotorealistico (era stato segnalato come "troppo
simulato"), ma un disegno a linee: una griglia di contorno triplanare
(basata sulla posizione nello spazio, non sull'orbit trap — che oscilla in
modo troppo caotico sulle bozze fini e dava un effetto "rumore/statico"
invece di linee pulite) scolpita sulla superficie, multi-ottava, con
antialiasing via `fwidth()`. Ogni ottava di frequenza più fine sfuma e
scompare quando la sua spaziatura scenderebbe sotto il pixel (aliasing), e
torna visibile quando ci si avvicina abbastanza da poterla risolvere: le
linee stesse si infittiscono avvicinandosi, non solo la geometria
sottostante (LOD). Un riempimento molto tenue (5% del colore linea) più un
bordo di silhouette netto danno comunque un minimo di lettura del volume,
senza tornare a un rendering "pieno".

**Livello 3 — audio generativo** (`src/audio.ts`): Web Audio API pura,
nessun file precampionato. Tutto passa dallo stesso bus (un filtro
condiviso, poi dry/wet verso un riverbero a convoluzione con impulse
response generata proceduralmente) — drone e impulsi ritmici non sono due
suoni separati sovrapposti, sono voci della stessa composizione, così si
combinano davvero in un'unica musica. Due segnali pilotano tutto, già
disponibili dalla camera: il raggio orbitale (`radiusT`, 0 = immersi nella
nube, 1 = lontano) apre/chiude il filtro e cambia il registro del drone —
da lontano suono aperto e chiaro, immergendosi si scurisce e si fa più
risonante; l'intensità di movimento (`camera.motionIntensity`, orbita +
zoom combinati) governa la densità degli impulsi ritmici (pizzicati su
scala pentatonica) — fermi quasi silenzio, muovendosi la trama si
infittisce. L'`AudioContext` parte al primo gesto utente (tap/click/tasto),
non prima, per rispettare le policy di autoplay dei browser.

> Corretto dopo un primo giro di test: volume troppo basso e un fruscio
> intermittente. Il fruscio veniva dall'impulse response del riverbero,
> generata come rumore bianco puro (tutte le frequenze a pari energia) —
> ogni pizzicato/accordo che l'attraversava si sentiva "sibilante". Ora il
> rumore passa da un leaky integrator prima di essere scritto nel buffer
> (lo scurisce verso un rosa/marrone, riverbero diffuso invece che
> sibilante) e c'è un lowpass dedicato sul ritorno del riverbero. I livelli
> individuali sono più alti (drone, pizzicati, accordi) e un
> `DynamicsCompressorNode` sul bus finale li tiene sotto controllo senza
> rischiare distorsione quando più suoni si sovrappongono.

**Affondamento infinito e continuo** (`shaders/raymarch.ts`, `main.ts`,
`camera.ts`): scendendo verso il centro non si "tocca il fondo" — la scena
è sempre l'**unione di 3 frattali annidati** (`NUM_LAYERS`): quello in cui
si è attualmente, e i 2 successivi, già visibili crescere al suo interno
mentre ci si avvicina, con potenza/tinta/rotazione proprie (derivate
deterministicamente dall'indice di profondità via hash, nessuna tabella da
mantenere — il livello 0, il punto di partenza, resta però sempre identico
a se stesso). Il raggio della camera si muove in **scala logaritmica**
(moltiplicativa, non +/- costante): è l'unico modo per cui l'affondamento
resti "continuo" e uniforme attraverso molti ordini di grandezza — uno
zoom lineare, vicino al centro, sembrerebbe schizzare via o restare fermo.
Nessuno scatto, nessun reset: `depthLayerBase` (quanti fattori di scala il
raggio ha già attraversato) si ricalcola ogni frame dal raggio corrente,
non è uno stato a parte. Livelli già superati o troppo lontani non vengono
mai calcolati — il costo resta piatto (sempre esattamente 3 copie) a
qualunque profondità, non si accumula nulla.

> Bug corretto durante lo sviluppo: la copia "livello corrente" (k=0) va
> ri-ancorata alla profondità assoluta (`s = pow(SCALE, uDepthLayerBase)`),
> non può ripartire da scala 1 a ogni frame — altrimenti resta sempre alla
> dimensione originale mentre il raggio (che si riduce con la stessa legge)
> le sfila via sotto, e oltre una certa profondità la telecamera non trova
> più nulla (schermo vuoto). Verificato con una discesa continua di 16s+
> senza interruzioni.

Il costo di 3 copie simultanee (~3× le iterazioni per step rispetto a
prima) è compensato abbassando i tetti di dettaglio (`LOD_MAX_ITER`
10→7) e il budget di step adattivo (`quality.ts`, `RAY_STEPS_MAX` 95→70) —
il `QualityManager` (vedi sopra) resta comunque il meccanismo che tiene
tutto entro le risorse disponibili in tempo reale.

Resta anche l'effetto "enclosure": quando l'hit è fortissimamente
ravvicinato su un pixel (circondati da geometria, non solo vicini a una
parete) il colore vira verso un violaceo più scuro e denso, così "essere
dentro" si legge diverso da "essere vicino a una superficie".

**Nuclei e persistenza** (livello 4, non ancora implementato): punti
generati deterministicamente (seed fisso + hash), verificati contro la
distance function per restare vicino/dentro la superficie frattale nella
loro configurazione "di riferimento". Stato risolto/non risolto salvato in
`localStorage` con schema versionato (`{version, solvedIds: string[]}`),
così sopravvive tra sessioni e — impacchettato in Capacitor — resta legato
all'installazione Android.

**Build Android** (livello 5, non ancora implementato): **Capacitor**
(non Cordova) come wrapper WebView attorno alla build statica di Vite —
scelto perché è la soluzione più sottile e attualmente mantenuta per
portare un sito/app WebGL su Android con attrito minimo: nessun motore di
gioco nativo da imparare, il progetto Android generato è un normale
progetto Gradle che una GitHub Actions può compilare, e le performance di
un raymarching shader in una WebView Android moderna (Chromium/System
WebView, WebGL2 supportato) sono adeguate per questo tipo di scena a
triangolo singolo. Un motore nativo (Unity, GLES puro) darebbe più
controllo/performance ma con costo di sviluppo molto più alto, ingiustifi-
cato visto che l'unico target è Android e l'iterazione rapida in browser è
prioritaria in questa fase.

**CI/Build Android** (`.github/workflows/frattale-android.yml`): builda il
sito (`npm run build`), `npx cap sync android`, poi `./gradlew
assembleDebug` — build di debug (firma con un keystore di debug generato
automaticamente da Gradle, non serve gestire segreti di firma per queste
build interne). `applicationId` (`com.donatocannatello.frattale`) e
`versionName` restano fissi in `android/app/build.gradle`; `versionCode`
è `${{ github.run_number }}` (sempre crescente), passato via variabile
d'ambiente `ANDROID_VERSION_CODE` letta da Gradle — così ogni build
sovrascrive l'installazione precedente sul telefono invece di affiancarsi
come app separata. L'APK viene rinominato a un nome fisso (`frattale.apk`)
e pubblicato su una release GitHub con tag fisso `latest`
(`allowUpdates`/`replacesArtifacts`, aggiornata in-place ad ogni build):
il link di download resta sempre lo stesso,
`.../releases/download/latest/frattale.apk`. Si attiva su ogni push che
tocca `frattale/**`, o a mano da Actions (`workflow_dispatch`).

> Nota: questo è un **build di debug** (non firmato per il Play Store),
> pensato per installare rapidamente le iterazioni sul telefono durante lo
> sviluppo — bisogna abilitare "Installa app sconosciute" per la sorgente
> da cui la scarichi. Una firma di release vera (keystore dedicato) è un
> passo successivo, quando servirà pubblicare sul Play Store.

## Stato di avanzamento

- [x] **Livello 1** — rendering frattale statico navigabile, camera libera
      6DOF con inerzia, FOV dinamico, fog/desaturazione in lontananza.
- [x] **Livello 2** — potenza/rotazione/palette che evolvono lentamente nel
      tempo, superfici vicine alla camera che si illuminano/deformano
      leggermente, dettaglio (iterazioni) crescente avvicinandosi.
- [x] **Livello 3** — audio generativo reattivo (drone + impulsi ritmici
      su un bus condiviso, pilotati da raggio/velocità della camera).
- [ ] Livello 4 — nuclei, meccanica di risoluzione, persistenza
- [x] **Livello 5** — Capacitor + CI Android, APK con nome/package fissi,
      release `latest` con link diretto stabile

## Scarica l'APK

Link diretto, sempre aggiornato all'ultima build (si aggiorna da solo a
ogni push su `frattale/`):

**https://github.com/donatocannatello-cloud/BTOrder/releases/download/latest/frattale.apk**

È un build di debug: sul telefono va abilitata l'installazione da sorgenti
sconosciute per il browser/app con cui la scarichi. Il `versionCode`
cresce a ogni build, quindi reinstallarla sovrascrive la versione
precedente invece di affiancarsi come app separata.

## Sviluppo locale

```bash
cd frattale
npm install
npm run dev       # dev server con hot reload
npm run build     # build statica in dist/
npm run preview   # serve la build di produzione
```

Controlli: stick sinistro/`WASD` per orbitare intorno al centro, levetta
destra/`Space`+`Shift` per avvicinarsi o allontanarsi, trascinamento del
mouse o rotellina su desktop.

### Build Android locale

```bash
cd frattale
npm run build
npx cap sync android
cd android
./gradlew assembleDebug   # APK in app/build/outputs/apk/debug/
```
