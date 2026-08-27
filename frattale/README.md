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
      Nessuna evoluzione temporale, nessuna reazione alla camera, nessun
      HUD/audio/nucleo: solo la base di navigazione.
- [ ] Livello 2 — evoluzione temporale del frattale + reazione alla camera
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
