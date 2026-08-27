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

**Camera**: `src/camera.ts` + `src/quat.ts`. Camera a volo libero 6DOF vera
(non FPS-style): orientamento come quaternione, aggiornato ogni frame con
rotazioni incrementali attorno agli assi *locali* correnti (yaw dal mouse
attorno all'asse "up" locale, pitch attorno al "right" locale, roll con
Q/E attorno al "forward" locale) — così non c'è gimbal lock e il roll è
possibile, oltre a beccheggio/imbardata. Il movimento (WASD + Space/Shift)
è anch'esso relativo agli assi locali, non al mondo, per coerenza con
"nessun pavimento/gravità". La velocità non insegue mai istantaneamente
l'input: c'è uno smoothing esponenziale (inerzia). Il FOV cresce
leggermente con la velocità, anch'esso smussato.

> Nota: lo spec parlava di "rotazione libera" generica; ho aggiunto il
> roll (Q/E) perché è coerente con "6 gradi di libertà" in senso stretto.
> Se preferisci una camera stile FPS (senza roll, up/down assoluti nel
> mondo) è una modifica piccola e isolata in `camera.ts`.

**Input**: `src/touchControls.ts`. Il target è Android, quindi il touch è
lo schema *primario*, non un ripiego: doppio joystick, **sempre visibile**
e ancorato agli angoli (basso-sinistra = movimento, basso-destra = guarda),
con un highlight quando in uso — la prima versione (invisibile finché non
tocchi) risultava confusa senza un riferimento fisso a schermo. Il tocco
iniziale è comunque accettato in tutta la metà schermo corrispondente, non
serve centrare il dito sul cerchietto; la base resta ferma nell'angolo e la
manopola si sposta verso il dito. Metà sinistra = movimento
(avanti/indietro + laterale, analogico); metà destra = guarda intorno
(yaw/pitch), stesso gesto del drag desktop. Non c'è un gesto dedicato per
su/giù: come in un volo/aereo, ci si alza o abbassa inclinando lo sguardo e
andando avanti — tiene i controlli a due soli stick invece di tre-quattro
zone sullo schermo. Tastiera (WASD/Space/Shift/Q/E/Ctrl) e trascinamento
col mouse restano attivi in parallelo, utili solo per un test rapido da
laptop durante lo sviluppo.

**Dettaglio dinamico (LOD)**: il numero di iterazioni del Mandelbulb
(`uMaxIter`, uniform invece di costante) cresce da 5 a 10 mano a mano che
la camera si avvicina all'origine del frattale (`fractalDetail()` in
`main.ts`, in base a `length(camPos)`). Da lontano la forma resta liscia e
semplice (economica), avvicinandosi emergono via via le increspature più
fini — altrimenti il frattale sembra una texture statica indipendentemente
da quanto ci si avvicina.

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

**Audio** (livello 3, non ancora implementato): Web Audio API pura,
nessun file precampionato. Un `AudioEngine` leggerà ogni frame: profondità
media raymarching nel campo visivo, velocità della camera, distanza dal
nucleo attivo più vicino, e pilotherà oscillatori + filtri + riverbero
(convoluzione con impulse response generata proceduralmente, o feedback
delay network) per un drone continuo che cambia timbro/densità/armonia.

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

CI: workflow GitHub Actions che builda il sito (`npm run build`),
`npx cap sync android`, poi `./gradlew assembleDebug` (o release con
keystore di debug fisso) con `applicationId` e `versionName` fissi nel
`build.gradle` e `versionCode` derivato da `github.run_number` (sempre
crescente, richiesto da Android per accettare l'update). L'APK viene
rinominato a un nome file fisso (es. `frattale.apk`) e pubblicato su una
release GitHub con tag mobile fisso `latest` (aggiornata in-place ad ogni
build), così il link di download resta sempre lo stesso.

## Stato di avanzamento

- [x] **Livello 1** — rendering frattale statico navigabile, camera libera
      6DOF con inerzia, FOV dinamico, fog/desaturazione in lontananza.
- [x] **Livello 2** — potenza/rotazione/palette che evolvono lentamente nel
      tempo, superfici vicine alla camera che si illuminano/deformano
      leggermente, dettaglio (iterazioni) crescente avvicinandosi.
- [ ] Livello 3 — audio generativo reattivo
- [ ] Livello 4 — nuclei, meccanica di risoluzione, persistenza
- [ ] Livello 5 — repo/CI Android, APK con nome/package fissi, release
      con link diretto stabile

## Sviluppo locale

```bash
cd frattale
npm install
npm run dev       # dev server con hot reload
npm run build     # build statica in dist/
npm run preview   # serve la build di produzione
```

Controlli: mouse (dopo un click sul canvas, pointer lock) per guardare
intorno, `WASD` per muoversi, `Space`/`Shift` su/giù, `Q`/`E` per il roll,
`Ctrl` per un boost di velocità.
