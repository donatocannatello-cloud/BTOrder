# Discesa Frattale

Esploratore solitario dentro un universo frattale generato via shader,
senza HUD né testo: tutto passa da immagine, suono e movimento. Vedi la
richiesta originale nell'issue/prompt per il concept completo.

Questo sotto-progetto vive nella cartella `frattale/` di questa repo, che
contiene anche l'app Android indipendente `ChiamateBT` (root della repo):
i due progetti non condividono nulla.

## Piano tecnico

**Rendering**: WebGL2 "raw" (nessun Three.js) — un solo triangolo
full-screen (`gl_VertexID`, nessun vertex buffer) e tutto il lavoro nel
fragment shader (`src/shaders/fractalMap.ts`). Niente Three.js perché qui
non serve uno scene graph: c'è una sola "geometria" (il frattale, definito
analiticamente nello shader) e un solo draw call; saltare Three.js tiene il
bundle minuscolo (~7 KB JS gzip) e riduce l'overhead nella WebView Android.

**Mappe piane sovrapposte, non mondi sferici concentrici.** L'impianto è
quello di una carta topografica navigata come Google Maps: il piano si
guarda sempre dall'alto, ci si sposta scorrendo (pan) e si scende/sale di
scala (zoom). Ogni livello è una mappa frattale piatta — un insieme di
Julia disegnato per curve di livello sul suo campo escape-time "smooth"
(conteggio di fuga continuo, non a gradini, che è ciò che permette isolinee
pulite). Il parametro di Julia, la tinta, la rotazione e lo scostamento di
ciascun livello derivano da una hash del suo indice assoluto: ogni mappa è
visibilmente diversa dalle altre, ma il livello 0 è fissato così il punto
di partenza non cambia mai.

I livelli condividono un unico sistema di coordinate 2D ma sono
campionati a scale diverse:

```
p_k = uCenter + offset(L) + uv * SCALE^(k - uFrac)
```

dove `k` va da `K_MIN` (−3) a `K_MIN + NUM_LAYERS − 1` (+1),
`L = uLayerBase + k` è l'indice assoluto e `uFrac` la parte frazionaria
dello zoom. Il livello a `−3` è quello che si sta già superando,
enormemente ingrandito e in dissolvenza; `−2`, `−1` e `0` sono quelli a
piena intensità; `+1` è la mappa fine che si intravede appena dal fondo.
Una dissolvenza incrociata (`layerWeight()`) porta a zero l'uscente e
l'entrante esattamente sui bordi della transizione, quindi lo scambio di
indice non si vede mai. Solo `NUM_LAYERS` livelli vengono valutati per
pixel: il costo resta piatto a qualunque profondità.

> **Perché la finestra è spostata tutta verso il vicino.** Non basta
> tenere acceso più a lungo il livello che si sta superando: conta
> **quali livelli portano il peso**. Un primo tentativo spostò la
> finestra da `[0,+2]` a `[−1,+2]`, ma i due livelli a piena intensità
> restavano `0` e `1`, la cui scala oscilla fra 0,45 e 2,2 — il frattale
> dominante non diventava mai grande, e quello a `−1`, pur arrivando a
> 4,8×, era in dissolvenza e contribuiva poco: l'effetto era quasi
> indistinguibile da prima. Con `[−3,+1]` i livelli a piena intensità
> diventano `−2`, `−1` e `0`, e l'uscente arriva a `SCALE⁴` = **23×**
> prima di spegnersi.
>
> Il costo è rientrato **abbassando la densità del tratto** (`hatch`) da 5
> ottave a 3 — le due più fini erano quasi sempre sotto la soglia di
> risoluzione, dove `contour()` le sfuma via da sola, quindi rendevano
> poco ma si pagavano su ogni livello — e il tetto delle iterazioni in
> `quality.ts` da 220 a 130. Ciò che conta è il prodotto livelli ×
> iterazioni: 5×130 costa **332 ms** contro i **360 ms** di 3×220, quindi
> l'ingrandimento è dieci volte maggiore *e* il rendering più leggero di
> prima.
>
> Le misure vanno prese con `readPixels` a forzare la sincronizzazione con
> la GPU: una misura basata su `requestAnimationFrame` è quantizzata dal
> vsync (33,3 e 50,0 ms sono esattamente 30 e 20 fps) e faceva sembrare
> +12% un +50%.

> **Perché lo zoom è davvero infinito.** Poiché `SCALE^(1-1) == SCALE^0`,
> il fattore di scala del livello entrante coincide *esattamente* con
> quello del livello uscente nel momento dello scambio: l'indice può
> avanzare (o arretrare) all'infinito senza scatti e **senza dover
> ri-ancorare il centro**. Il passo di pan è proporzionale alla sola scala
> frazionaria (`SCALE^-frac`, quindi tra `1/SCALE` e `1`), mai a quella
> assoluta, e il centro è ripiegato a specchio: le coordinate non
> crescono mai, e la precisione in virgola mobile non si degrada per quanto
> a fondo si scenda. È il difetto che affliggeva l'impianto raymarching
> precedente, dove le coordinate si rimpicciolivano fino a sfaldare
> l'immagine; qui una discesa continua di ~14 livelli (oltre 3·10⁶× di
> zoom) resta perfettamente nitida.

**Qualità adattiva** (`src/quality.ts`): il DPR è limitato a 1.5 in
partenza (un telefono a 3x non deve renderizzare a 3x: il costo scala con
il numero di pixel), poi un `QualityManager` misura il tempo-frame reale
ogni ~900ms e regola `renderScale` (risoluzione interna del canvas,
upscalata via CSS) e `maxIter` (budget di iterazioni escape-time per
livello): se il framerate scende sotto 30fps degrada prima le iterazioni
poi la risoluzione (il taglio più visibile su un disegno a linee), se sta
comodo sopra 55fps risale. Cambi piccoli e non troppo frequenti per evitare
"pompaggi" visibili. Il render loop si ferma del tutto quando la pagina è
in background (`visibilitychange`). Le mappe piane costano molto meno del
raymarching 3D precedente — una sola valutazione per livello per pixel,
nessun passo lungo un raggio — quindi si parte da una qualità
sensibilmente più alta a parità di dispositivo.

**Camera** (`src/camera.ts`): non c'è più nessuna camera 3D. `MapCamera`
tiene un centro 2D e un `zoomLevel` continuo, entrambi **illimitati**: la
parte intera dello zoom è l'indice del livello, la frazionaria la
transizione verso il successivo. Stick/tastiera pilotano una velocità di
scorrimento smussata dall'inerzia; il trascinamento col mouse scorre per
manipolazione diretta, senza inerzia propria (ci si aspetta che un drag
risponda 1:1).

Stick e trascinamento hanno **versi opposti, di proposito**: lo stick guida
il *punto di vista* (spingi a destra → ti sposti a destra e il disegno
scorre a sinistra, come guidare su una mappa), il trascinamento invece
*afferra il foglio* (trascini a destra → la mappa segue il cursore). Sono
due gesti diversi e ci si aspetta due comportamenti diversi.

**Mondo senza bordi, per riflessione**: non c'è nessun limite allo
scorrimento, in nessuna direzione. Il mondo non è però infinito "per
davvero": fuori dal suo raggio di interesse un insieme di Julia degenera in
vuoto uniforme, quindi lasciar scorrere via darebbe deserto, e un wrap col
modulo darebbe una cucitura netta e visibile ad ogni giro. La terza via è
un **ripiegamento a specchio** (`mirrorFold` nello shader): un'onda
triangolare che è l'identità sul riquadro fondamentale `[-H, H]` e poi
riflette ad ogni bordo, con periodo `4H`. Essendo continua (nessun salto di
valore sul bordo) il disegno prosegue senza strappi, come in una sala degli
specchi.

> Il ripiegamento è anche ciò che tiene lo scorrimento *illimitato* senza
> perdere precisione: `MapCamera` riporta il centro dentro un periodo ad
> ogni frame, trasformazione esattamente invisibile dato che lo shader
> ripiega con lo stesso periodo. Verificato numericamente: identità su
> `[-H, H]`, continuità senza salti su 40 unità, wrap invisibile a meno di
> 1e-14, e valori sempre dentro `[-1.5, 1.5]` anche campionando a ±10⁶.

**Input**: `src/touchControls.ts`. Il target è Android, quindi il touch è
lo schema *primario*: due controlli **sempre visibili**, ancorati agli
angoli, con un highlight quando in uso (la prima versione, invisibile
finché non tocchi, risultava confusa senza un riferimento fisso a
schermo). A **sinistra** uno stick circolare per spostarsi sulla mappa
nelle 4 direzioni: guida il punto di vista, quindi spingendo in alto ci si
sposta verso l'alto e il disegno scorre verso il basso. A **destra** una
**levetta verticale**, con un
design deliberatamente diverso da uno stick (un binario allungato, non un
disco, con un segno vuoto in alto e uno pieno in basso) per segnalare che
governa un solo asse: scendere/salire di scala. Entrambi sono "a molla":
tornano a riposo al rilascio. Il tocco iniziale è accettato in tutta la
metà schermo corrispondente, non serve precisione sul controllo. Su
desktop: `WASD` = scorrimento, `Space`/`Shift` = scendi/sali di scala,
trascinamento col mouse o rotellina, `Ctrl` per accelerare — utili solo per
un test rapido da laptop durante lo sviluppo.

**Dettaglio dinamico**: le curve di livello sono multi-ottava, ciascuna
antialiasata in spazio schermo con `fwidth()` e sfumata via automaticamente
quando il suo passo diventerebbe sub-pixel. Le linee più fini si
materializzano quindi solo quando la scala è abbastanza grande da poterle
davvero risolvere — è il comportamento "il dettaglio aumenta scendendo"
applicato al tratto, oltre che alla comparsa di mappe sempre nuove. Le
isolinee si infittiscono verso il bordo dell'insieme, dove vive tutto il
dettaglio frattale, invece di restare uniformi anche nelle zone piatte al
largo.

**Livello 2 — evoluzione temporale** (in `shaders/fractalMap.ts`): il
parametro di Julia di *ogni* livello deriva lentamente nel tempo
(oscillazione sinusoidale calcolata in `main.ts`, periodo ~125s, sommata
allo scostamento per-livello, non un valore assoluto): la mappa è viva
anche stando fermi, senza mai stravolgersi. La palette ha una deriva
cromatica lenta (cosine palette). La rotazione per-livello è invece
**fissa nel tempo**, non animata: una mappa che ruota da sola disorienta.

**Stile visivo — carta topografica, non superficie illuminata**: lo shading
non è Lambertiano/fotorealistico (era stato segnalato come "troppo
simulato"), ma un disegno a linee. Le isolinee sono tracciate direttamente
sul campo escape-time — un campo scalare liscio e continuo, che è ciò che
permette curve pulite invece dell'effetto "rumore/statico" — multi-ottava e
antialiasate via `fwidth()`. L'interno dell'insieme è una campitura appena
percettibile (`FILL`, 3% del colore linea), come la terraferma su una
carta; volutamente bassissima, perché la correzione gamma finale amplifica
molto anche valori lineari piccoli (0.05 lineare diventa ~0.24 a schermo, e
appiattisce tutto il disegno in una tinta unita).

Quattro costanti in cima allo shader governano la resa: `EXPOSURE`,
`LINE_GAIN`, `SATURATION`, `WASH`. Due scelte non ovvie:

- **La saturazione si applica *dopo* il tonemap, non prima.** Il tonemap di
  Reinhard (`c/(1+c)`) comprime ogni canale verso 1: più si alza
  l'esposizione, più i tre canali si avvicinano fra loro e il colore
  sbianca. Saturare a monte verrebbe quindi in gran parte annullato proprio
  dove il tratto è più luminoso, cioè dove il colore conta. In spazio
  display la tinta si recupera senza rinunciare alla luminosità.
- **Alzare l'alone di costa (`WASH`) rende il wireframe *meno* visibile,
  non più.** È la parte piatta del disegno: schiarisce il fondo *fra* le
  linee e ne divora il contrasto. Un primo tentativo che alzava
  l'esposizione globale ha prodotto esattamente questo, un lavaggio
  magenta uniforme. Il guadagno va tutto sul tratto (`LINE_GAIN`), e
  l'alone tenuto a un accenno.

> Misurato su ritagli confrontabili, separando il fondo (mediana) dal
> tratto (95° percentile): **tratto 1,68× più luminoso**, **contrasto
> tratto/fondo 1,22×**, **saturazione 1,50×**.

**Identità e schermata iniziale**: il gioco si chiama **Discesa
Frattale**. L'ingresso mostra il titolo e un pulsante di avvio sopra la
mappa già in movimento — niente istruzioni scritte e niente controlli di
gioco, che comparirebbero sovrapposti al titolo senza avere ancora nulla
da comandare. Stick e levetta entrano in dissolvenza insieme al pulsante
di uscita, quando si entra davvero. Il respiro lento stava sul titolo,
quando era il titolo stesso a fare da invito a toccare; ora che c'è un
comando esplicito è il pulsante ad averlo, e il titolo resta fermo.

Si entra **solo dal pulsante**: né un tocco sul resto della schermata né
un tasto qualsiasi avviano il gioco. L'overlay `#entry` copre comunque lo
schermo e intercetta i tocchi, quindi finché si è lì non si può muovere
nulla per sbaglio sotto — semplicemente non avvia più. `Escape` resta
l'unico tasto collegato, e serve a uscire.

L'icona e lo splash nativo sono **generati dal frattale stesso**
(`scratchpad` non versionato, sorgente WebGL): è l'insieme di Julia del
livello 0 — lo stesso `c = -0.7269 + 0.1889i` che si vede aprendo l'app —
ruotato di 45° per riempire il quadrato, campito pieno e con poche curve
di livello larghe. Le isolinee fitte del gioco sono belle a schermo
intero ma a 48 px diventerebbero poltiglia grigia, quindi l'icona tiene
la silhouette e getta via il dettaglio fine. Tre inquadrature diverse
dalla stessa sorgente: più stretta per l'icona quadrata legacy, media per
quella tonda (il cerchio taglia le punte diagonali), più larga per il
*foreground* adattivo, che deve stare nei 72dp su 108 che Android
garantisce visibili sotto qualunque maschera. Lo splash nativo era quello
bianco di default di Capacitor, che faceva lampeggiare bianco prima di
un'app tutta nera: ora è il marchio su fondo `#0a0a12`.

Lo schermo intero è **nativo**, non web (`android/.../MainActivity.java`):
la `requestFullscreen()` che il codice web chiama entrando agisce su un
elemento del documento, non sulla finestra dell'activity, quindi dentro
una WebView di Capacitor barra di stato e barra di navigazione restavano
comunque visibili. L'activity le nasconde con
`WindowInsetsControllerCompat`, in modo permanente (anche sulla schermata
iniziale: non c'è nessun momento in cui serva la UI di sistema) e
ri-applicato in `onWindowFocusChanged`, altrimenti l'immersivo durerebbe
solo fino al primo swipe dal bordo o al primo rientro da un'altra app. Il
comportamento scelto è `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`: uno swipe
le mostra in overlay **senza ridimensionare la WebView** — un resize del
canvas a metà navigazione costringerebbe a riallocare il framebuffer WebGL
e a ricalcolare la scala di rendering, con uno scatto visibile. Sui
telefoni con notch la finestra disegna fino ai bordi corti
(`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`), mentre il layout web tiene i
comandi al sicuro dall'intaglio con `env(safe-area-inset-*)`, che la
WebView popola grazie a `viewport-fit=cover`.

**Livello 3 — audio generativo** (`src/audio.ts`): Web Audio API pura,
nessun file precampionato. Tutto passa dallo stesso bus (un filtro
condiviso, poi il master) — drone e impulsi ritmici non sono due suoni
separati sovrapposti, sono voci della stessa composizione, così si
combinano davvero in un'unica musica. Due segnali pilotano tutto, già
disponibili dalla navigazione: la posizione dentro il livello corrente
(`1 - frac`: il drone si apre man mano che si scende dentro ogni mappa e
riparte al livello successivo) e l'intensità di movimento
(`camera.motionIntensity`, pan + zoom combinati), che governa la densità
degli impulsi ritmici (pizzicati su scala pentatonica) — fermi quasi
silenzio, muovendosi la trama si infittisce. L'`AudioContext` parte al
primo gesto utente (tap/click/tasto), non prima, per rispettare le policy
di autoplay dei browser, e va in `suspend()` uscendo col pulsante X.

A questi due segnali continui si aggiunge un **evento discreto**: ad ogni
passaggio di livello tutta la musica si sposta di un'ottava, in su o in giù
a caso — stessi suoni e stesse note, solo un registro diverso, così la
soglia si sente come un cambio di scena e non come un brano nuovo. È una
passeggiata casuale *limitata* (`[-1, +2]` ottave), non un salto libero:
senza limiti bastano pochi livelli per finire nel subsonico o sopra il
taglio del filtro, e la musica sparirebbe. Arrivati a un estremo si rimbalza
nell'altra direzione, così ogni passaggio si sente comunque muovere.

**Il bordone è un letto, non una voce.** Tre difetti lo rendevano un
"uuuuu" che cresceva fino a stancare, e vanno tenuti distinti:

1. *Tutti e cinque i parziali avevano esattamente lo stesso guadagno* — una
   quarta armonica forte quanto la fondamentale, cosa che nessun timbro
   naturale fa: è precisamente ciò che produce il timbro da organo. Ora c'è
   un rolloff (`DRONE_WEIGHTS`, dal 100% al 7%).
2. *Il livello cresceva del 92% lungo ogni livello e poi scattava indietro*
   alla soglia successiva — un crescendo lento ripetuto all'infinito, la
   ricetta esatta per un suono che "aumenta fino a diventare fastidioso".
   Ora l'unica modulazione è un filo di presenza in più quando ci si muove,
   che non si accumula mai.
3. *Lo spostamento d'ottava lo trascinava in alto*: a `+2` i suoi parziali
   finivano fra 220 e 880 Hz, in piena zona di massima sensibilità
   dell'orecchio. A parità di manopola quello lo rendeva **3,6× più forte
   all'orecchio** del registro base. Ora il bordone segue lo spostamento
   solo verso il *basso*; pizzicati e arpeggio prendono l'escursione
   completa, e siccome sono transitori lì un registro alto è brillante
   invece che affaticante — il cambio di livello resta udibile.

> Misurato rendendo l'audio offline con `OfflineAudioContext` e pesando le
> ampiezze in curva A, non solo sommandole: **−63% di RMS** al picco,
> **−91% di livello percepito**, e **−97%** rispetto al caso peggiore
> di prima (fine livello a `+2` ottave).

> Corretto dopo due giri di test. Primo: volume troppo basso e un fruscio
> intermittente — i livelli individuali sono stati alzati con un
> `DynamicsCompressorNode` sul bus finale a tenerli sotto controllo.
> Secondo: eco, ovattamento crescente scendendo e disturbi saltuari. Non
> c'era (né c'è mai stata) alcuna spazializzazione 3D — nessun
> `PannerNode`/`AudioListener`, il bus è mono e condiviso — quindi il
> problema era altrove: il **riverbero a convoluzione** è il nodo più
> costoso della catena, e su un telefono reale, in concorrenza col
> rendering, era il sospetto principale sia per i disturbi (buffer audio
> che non arrivano in tempo) sia per l'eco che si accumulava sopra
> pizzicati sempre più frequenti. Rimosso del tutto. Il filtro è ora fisso
> (prima il suo floor a 260Hz veniva raggiunto quasi sempre, dato che la
> scala scende esponenzialmente: il suono restava ovattato al massimo per
> gran parte della discesa) e l'automazione dei parametri continui è
> limitata a 10Hz invece che a ogni frame, per ridurre il traffico verso
> il thread audio.

**Scala infinita in entrambe le direzioni**: scendendo non si "tocca il
fondo" e salendo non si esce — `zoomLevel` è illimitato, e con esso
l'indice dei livelli (anche negativo: la hash somma +4096 così anche gli
indici negativi cadono nel ramo positivo). Ogni transizione fa comparire
una mappa nuova dal fondo e ne congeda una in cima, sempre 3 in vista. Il
meccanismo e il motivo per cui non degrada mai sono descritti sopra, in
**Mappe piane sovrapposte**.

> Verificato con Playwright: ~25s di discesa continua (≈14 livelli, oltre
> 3·10⁶× di zoom) restano perfettamente nitidi, e ~35s di risalita
> attraversano lo zero fino a indici negativi generando altrettante mappe
> nuove — senza errori e senza degrado.

Lo zoom si muove in **scala logaritmica** (moltiplicativa, non +/-
costante): è l'unico modo per cui la discesa resti uniforme attraverso
molti ordini di grandezza — uno zoom lineare, a fondo scala, sembrerebbe
schizzare via o restare fermo. È lo stesso motivo per cui lo zoom di una
mappa o di una fotocamera è sempre moltiplicativo.

**Nuclei e persistenza** (livello 4, non ancora implementato): punti
generati deterministicamente (seed fisso + hash), verificati contro la
distance function per restare vicino/dentro la superficie frattale nella
loro configurazione "di riferimento". Stato risolto/non risolto salvato in
`localStorage` con schema versionato (`{version, solvedIds: string[]}`),
così sopravvive tra sessioni e — impacchettato in Capacitor — resta legato
all'installazione Android.

**Build Android**: **Capacitor** (non Cordova) come wrapper WebView attorno
alla build statica di Vite — scelto perché è la soluzione più sottile e
attualmente mantenuta per portare un sito/app WebGL su Android con attrito
minimo: nessun motore di gioco nativo da imparare, il progetto Android
generato è un normale progetto Gradle che una GitHub Actions può
compilare, e le performance di un fragment shader in una WebView Android
moderna (Chromium/System WebView, WebGL2 supportato) sono adeguate per
questo tipo di scena a triangolo singolo. Un motore nativo (Unity, GLES
puro) darebbe più controllo/performance ma con costo di sviluppo molto più
alto, ingiustificato visto che l'unico target è Android e l'iterazione
rapida in browser è prioritaria in questa fase.

**CI/Build Android** (`.github/workflows/frattale-android.yml`): builda il
sito (`npm run build`), `npx cap sync android`, poi `./gradlew
assembleDebug` — build di debug, firmata con un keystore di debug fisso
committato nel repo (`android/app/debug.keystore`, credenziali standard
Android `androiddebugkey`/`android`, la stessa identità pubblica che
Android Studio genera in locale — non una chiave di release, nessun
segreto da gestire). Necessario perché altrimenti ogni run della CI
userebbe il keystore generato al volo dal runner (diverso a ogni run,
dato che i runner sono effimeri): l'APK risulterebbe firmato con una
chiave diversa dalla build precedente, e Android rifiuta di aggiornare
un'app se la firma non coincide con quella già installata ("l'app non è
stata installata perché è in conflitto con un pacchetto esistente").
`applicationId` (`com.donatocannatello.frattale`) e
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

- [x] **Livello 1** — rendering frattale navigabile a mappa piana (pan +
      zoom), con inerzia e vignettatura.
- [x] **Livello 2** — parametri/palette che evolvono lentamente nel tempo,
      dettaglio (isolinee e mappe nuove) crescente scendendo.
- [x] **Livello 3** — audio generativo reattivo (drone + impulsi ritmici
      su un bus condiviso, pilotati da profondità/velocità di navigazione).
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

> Se avevi già installato una build **prima** che la firma venisse fissata
> (commit `dcbe155`), quella build ha una firma diversa da tutte quelle
> successive: la prossima installazione fallirà con "app non installata,
> in conflitto con un pacchetto esistente". Basta disinstallare quella
> vecchia una volta sola — da lì in poi ogni nuova build si aggiornerà
> sopra la precedente senza bisogno di disinstallare di nuovo.

## Sviluppo locale

```bash
cd frattale
npm install
npm run dev       # dev server con hot reload
npm run build     # build statica in dist/
npm run preview   # serve la build di produzione
```

Controlli: stick sinistro/`WASD` per scorrere la mappa, levetta
destra/`Space`+`Shift` per scendere o salire di scala, trascinamento del
mouse o rotellina su desktop.

### Build Android locale

```bash
cd frattale
npm run build
npx cap sync android
cd android
./gradlew assembleDebug   # APK in app/build/outputs/apk/debug/
```
