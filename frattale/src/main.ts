import { OrbitCamera, RADIUS_MIN, RADIUS_MAX, type OrbitInput } from "./camera";
import { Renderer } from "./renderer";
import { TouchControls } from "./touchControls";
import { QualityManager } from "./quality";
import { AudioEngine } from "./audio";

const canvas = document.getElementById("gl") as HTMLCanvasElement;
const indicatorLayer = document.getElementById("touch-layer") as HTMLElement;
const renderer = new Renderer(canvas);
const camera = new OrbitCamera();
const touchControls = new TouchControls(canvas, indicatorLayer);
const quality = new QualityManager();
const audio = new AudioEngine();

// L'audio puo' partire solo dentro un gesto utente (autoplay policy dei
// browser): lo sblocchiamo al primo tocco/click/tasto, senza bisogno di un
// overlay/tap-to-enter dedicato.
function unlockAudio() {
  audio.resume();
  window.removeEventListener("pointerdown", unlockAudio);
  window.removeEventListener("keydown", unlockAudio);
}
window.addEventListener("pointerdown", unlockAudio);
window.addEventListener("keydown", unlockAudio);

function clamp(v: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, v));
}

// Il potere del Mandelbulb "respira" lentamente nel tempo, anche senza
// input: il mondo e' vivo di suo, non solo quando ci si muove.
const POWER_BASE = 8.0;
const POWER_AMPLITUDE = 1.2;
const POWER_FREQ = 0.05; // rad/s, periodo ~125s

// Dettaglio (numero di iterazioni del frattale) in funzione della distanza
// dal centro: da lontano il frattale resta una forma semplice/liscia
// (economica), avvicinandosi emergono via via le increspature piu' fini,
// cosi' la scena non e' statica quando ci si avvicina. La camera orbitale
// ha gia' il raggio come distanza esatta dal centro, non serve ricalcolarla.
const LOD_MIN_ITER = 5;
const LOD_MAX_ITER = 10;
const LOD_NEAR = 1.6;
const LOD_FAR = 7.0;

function fractalDetail(radius: number): number {
  const t = clamp((LOD_FAR - radius) / (LOD_FAR - LOD_NEAR), 0, 1);
  return Math.round(LOD_MIN_ITER + t * (LOD_MAX_ITER - LOD_MIN_ITER));
}

const keys = new Set<string>();

// Il drag col mouse ruota la camera direttamente (manipolazione diretta,
// nessuna inerzia: e' cosi' che ci si aspetta funzioni un trascinamento),
// mentre stick/tastiera pilotano una velocita' di orbita smussata
// dall'inerzia della camera stessa.
let dragAzimuthAccum = 0;
let dragElevationAccum = 0;
let dragging = false;
let lastX = 0;
let lastY = 0;
const DRAG_SENSITIVITY = 0.0028;
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
  dragAzimuthAccum += (e.clientX - lastX) * DRAG_SENSITIVITY;
  dragElevationAccum += -(e.clientY - lastY) * DRAG_SENSITIVITY;
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

// Il DPR e' gia' limitato in partenza (un telefono a 3x non deve renderizzare
// a 3x: il costo del raymarching scala con il numero di pixel), e il
// QualityManager applica un ulteriore renderScale sopra questo, misurato
// sul frame time reale del dispositivo.
const DPR_CAP = 1.5;
let appliedRenderScale = -1;

function resize(renderScale: number) {
  appliedRenderScale = renderScale;
  const dpr = Math.min(window.devicePixelRatio || 1, DPR_CAP) * renderScale;
  renderer.resize(Math.max(1, Math.floor(window.innerWidth * dpr)), Math.max(1, Math.floor(window.innerHeight * dpr)));
}
window.addEventListener("resize", () => resize(appliedRenderScale > 0 ? appliedRenderScale : 0.82));
resize(0.82);

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

function readInput(): { cam: OrbitInput; boost: boolean } {
  // WASD = orbita nelle 4 direzioni cardinali, Space/Shift = vicino/lontano.
  const kAzimuth = (keys.has("KeyD") ? 1 : 0) - (keys.has("KeyA") ? 1 : 0);
  const kElevation = (keys.has("KeyW") ? 1 : 0) - (keys.has("KeyS") ? 1 : 0);
  const kZoom = (keys.has("Space") ? 1 : 0) - (keys.has("ShiftLeft") || keys.has("ShiftRight") ? 1 : 0);

  const touch = touchControls.consumeFrame();

  const cam: OrbitInput = {
    azimuth: clamp(kAzimuth + touch.azimuth, -1, 1),
    elevation: clamp(kElevation + touch.elevation, -1, 1),
    zoom: clamp(kZoom + touch.zoom + wheelZoom, -1, 1),
    dragAzimuth: dragAzimuthAccum,
    dragElevation: dragElevationAccum,
  };
  dragAzimuthAccum = 0;
  dragElevationAccum = 0;
  wheelZoom *= 0.85;

  return { cam, boost: keys.has("ControlLeft") || keys.has("ControlRight") };
}

let last = performance.now();
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

  const radiusT = clamp((camera.radius - RADIUS_MIN) / (RADIUS_MAX - RADIUS_MIN), 0, 1);
  audio.update(now, radiusT, camera.motionIntensity);

  const time = now / 1000;
  renderer.render({
    camPos: camera.position,
    camRight: camera.rightAxis,
    camUp: camera.upAxis,
    camForward: camera.forwardAxis,
    fov: camera.fov,
    time,
    power: POWER_BASE + Math.sin(time * POWER_FREQ) * POWER_AMPLITUDE,
    maxIter: fractalDetail(camera.radius),
    raySteps: q.raySteps,
  });

  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
