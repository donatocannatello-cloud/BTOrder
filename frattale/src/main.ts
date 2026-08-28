import { MapCamera, type MapInput } from "./camera";
import { Renderer } from "./renderer";
import { TouchControls } from "./touchControls";
import { QualityManager } from "./quality";
import { AudioEngine } from "./audio";

const canvas = document.getElementById("gl") as HTMLCanvasElement;
const indicatorLayer = document.getElementById("touch-layer") as HTMLElement;
const renderer = new Renderer(canvas);
const camera = new MapCamera();
const touchControls = new TouchControls(canvas, indicatorLayer);
const quality = new QualityManager();
const audio = new AudioEngine();

// ---- schermata iniziale + schermo intero + uscita -------------------------
// L'app gira sempre a schermo intero: la schermata iniziale serve sia da
// gesto utente per sbloccare l'audio (autoplay policy dei browser) sia da
// punto di ingresso esplicito nel fullscreen; il pulsante X riporta qui e
// mette in pausa l'audio, dato che non c'e' un tasto Indietro di sistema
// collegato.
const entry = document.getElementById("entry") as HTMLElement;
const exitBtn = document.getElementById("exit-btn") as HTMLElement;
const buildIdEl = document.getElementById("build-id") as HTMLElement;
buildIdEl.textContent = `build ${__BUILD_ID__}`;
let entered = false;

function enter() {
  if (entered) return;
  entered = true;
  entry.classList.add("hidden");
  exitBtn.classList.add("show");
  indicatorLayer.classList.add("show");
  audio.resume();
  const root = document.documentElement as HTMLElement & { webkitRequestFullscreen?: () => Promise<void> };
  const req = root.requestFullscreen || root.webkitRequestFullscreen;
  if (req) req.call(root).catch(() => {});
}

function exit() {
  if (!entered) return;
  entered = false;
  entry.classList.remove("hidden");
  exitBtn.classList.remove("show");
  indicatorLayer.classList.remove("show");
  audio.suspend();
  const doc = document as Document & { webkitExitFullscreen?: () => Promise<void>; webkitFullscreenElement?: Element };
  const exitFs = document.exitFullscreen || doc.webkitExitFullscreen;
  if (exitFs && (document.fullscreenElement || doc.webkitFullscreenElement)) {
    exitFs.call(document).catch(() => {});
  }
}

entry.addEventListener("pointerdown", enter);
window.addEventListener("keydown", (e) => {
  if (e.key === "Escape") exit();
  else enter();
});
exitBtn.addEventListener("click", exit);

function clamp(v: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, v));
}

// La mappa "respira" lentamente nel tempo, anche senza input: il mondo e'
// vivo di suo, non solo quando ci si muove. E' una piccola oscillazione
// che nello shader fa derivare appena il parametro di *ogni* livello, non
// un valore assoluto.
const BREATH_AMPLITUDE = 1.0;
const BREATH_FREQ = 0.05; // rad/s, periodo ~125s

const keys = new Set<string>();

// Il drag col mouse scorre la mappa direttamente (manipolazione diretta,
// nessuna inerzia: e' cosi' che ci si aspetta funzioni un trascinamento, ed
// e' anche il gesto naturale su una mappa), mentre stick/tastiera pilotano
// una velocita' di scorrimento smussata dall'inerzia della camera stessa.
let dragXAccum = 0;
let dragYAccum = 0;
let dragging = false;
let lastX = 0;
let lastY = 0;
const DRAG_SENSITIVITY = 0.0035; // unita' di schermo per pixel trascinato
let wheelZoom = 0;

window.addEventListener("keydown", (e) => keys.add(e.code));
window.addEventListener("keyup", (e) => keys.delete(e.code));
window.addEventListener("blur", () => keys.clear());

canvas.addEventListener("pointerdown", (e) => {
  if (e.pointerType === "touch") return; // gestito da TouchControls
  dragging = true;
  lastX = e.clientX;
  lastY = e.clientY;
  canvas.setPointerCapture(e.pointerId);
});
canvas.addEventListener("pointerup", (e) => {
  if (e.pointerType === "touch") return;
  dragging = false;
  canvas.releasePointerCapture(e.pointerId);
});
canvas.addEventListener("pointermove", (e) => {
  if (e.pointerType === "touch" || !dragging) return;
  // Il trascinamento resta "afferra e tira": la mappa segue il cursore,
  // quindi il punto di vista va nel verso opposto al gesto -- l'inverso
  // dello stick, che invece guida il punto di vista. Sono due gesti
  // diversi e ci si aspetta versi diversi: trascinare sposta il foglio,
  // spingere uno stick sposta chi guarda.
  dragXAccum += -(e.clientX - lastX) * DRAG_SENSITIVITY;
  dragYAccum += (e.clientY - lastY) * DRAG_SENSITIVITY;
  lastX = e.clientX;
  lastY = e.clientY;
});
canvas.addEventListener(
  "wheel",
  (e) => {
    e.preventDefault();
    wheelZoom = clamp(wheelZoom - e.deltaY * 0.0015, -1, 1);
  },
  { passive: false }
);

// Il DPR e' gia' limitato in partenza (un telefono a 3x non deve
// renderizzare a 3x: il costo scala con il numero di pixel), e il
// QualityManager applica un ulteriore renderScale sopra questo, misurato
// sul frame time reale del dispositivo.
const DPR_CAP = 1.5;
const INITIAL_RENDER_SCALE = 0.9;
let appliedRenderScale = -1;

function resize(renderScale: number) {
  appliedRenderScale = renderScale;
  const dpr = Math.min(window.devicePixelRatio || 1, DPR_CAP) * renderScale;
  renderer.resize(Math.max(1, Math.floor(window.innerWidth * dpr)), Math.max(1, Math.floor(window.innerHeight * dpr)));
}
window.addEventListener("resize", () =>
  resize(appliedRenderScale > 0 ? appliedRenderScale : INITIAL_RENDER_SCALE)
);
resize(INITIAL_RENDER_SCALE);

// Niente calcoli sprecati quando l'app e' in background (tab non attiva /
// telefono con lo schermo su un'altra app): meno carico, meno batteria.
let running = true;
document.addEventListener("visibilitychange", () => {
  running = !document.hidden;
  if (running) {
    last = performance.now();
    requestAnimationFrame(frame);
  }
});

function readInput(): { cam: MapInput; boost: boolean } {
  // WASD = scorrimento nelle 4 direzioni, Space/Shift = scendi/sali di scala.
  const kPanX = (keys.has("KeyD") ? 1 : 0) - (keys.has("KeyA") ? 1 : 0);
  const kPanY = (keys.has("KeyW") ? 1 : 0) - (keys.has("KeyS") ? 1 : 0);
  const kZoom = (keys.has("Space") ? 1 : 0) - (keys.has("ShiftLeft") || keys.has("ShiftRight") ? 1 : 0);

  const touch = touchControls.consumeFrame();

  const cam: MapInput = {
    panX: clamp(kPanX + touch.panX, -1, 1),
    panY: clamp(kPanY + touch.panY, -1, 1),
    zoom: clamp(kZoom + touch.zoom + wheelZoom, -1, 1),
    dragX: dragXAccum,
    dragY: dragYAccum,
  };
  dragXAccum = 0;
  dragYAccum = 0;
  wheelZoom *= 0.85;

  return { cam, boost: keys.has("ControlLeft") || keys.has("ControlRight") };
}

let last = performance.now();
let lastLayerBase = 0;
function frame(now: number) {
  if (!running) return;

  const dtMs = Math.min(now - last, 1000 / 20);
  last = now;
  quality.recordFrame(dtMs);
  const q = quality.update(now);
  if (q.renderScale !== appliedRenderScale) resize(q.renderScale);

  const dt = dtMs / 1000;
  const { cam, boost } = readInput();
  camera.update(dt, cam, boost);

  const layerBase = camera.layerBase;
  const frac = camera.layerFrac;
  if (layerBase !== lastLayerBase) {
    lastLayerBase = layerBase;
    audio.layerTransition();
  }

  // Per l'audio: 1 all'inizio di un livello, 0 quando lo si sta lasciando --
  // il drone si apre man mano che si scende dentro ogni mappa e riparte al
  // livello successivo.
  audio.update(now, 1 - frac, camera.motionIntensity);

  const time = now / 1000;
  renderer.render({
    centerX: camera.centerX,
    centerY: camera.centerY,
    frac,
    layerBase,
    maxIter: q.maxIter,
    time,
    breath: Math.sin(time * BREATH_FREQ) * BREATH_AMPLITUDE,
  });

  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
