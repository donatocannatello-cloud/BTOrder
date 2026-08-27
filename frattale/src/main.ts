import { FlightCamera, type CameraInput } from "./camera";
import { Renderer } from "./renderer";
import { TouchControls } from "./touchControls";
import { QualityManager } from "./quality";

const canvas = document.getElementById("gl") as HTMLCanvasElement;
const indicatorLayer = document.getElementById("touch-layer") as HTMLElement;
const renderer = new Renderer(canvas);
const camera = new FlightCamera();
const touchControls = new TouchControls(canvas, indicatorLayer);
const quality = new QualityManager();

function clamp(v: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, v));
}

// Il potere del Mandelbulb "respira" lentamente nel tempo, anche senza
// input: il mondo e' vivo di suo, non solo quando ci si muove.
const POWER_BASE = 8.0;
const POWER_AMPLITUDE = 1.2;
const POWER_FREQ = 0.05; // rad/s, periodo ~125s

// Dettaglio (numero di iterazioni del frattale) in funzione della distanza
// dalla camera all'origine: da lontano il frattale resta una forma
// semplice/liscia (economica), avvicinandosi emergono via via le
// increspature piu' fini, cosi' la scena non e' statica quando ci si
// avvicina.
const LOD_MIN_ITER = 5;
const LOD_MAX_ITER = 10;
const LOD_NEAR = 1.35; // gia' a ridosso della superficie
const LOD_FAR = 5.5; // punto di spawn iniziale della camera

function fractalDetail(camPos: readonly [number, number, number]): number {
  const dist = Math.hypot(camPos[0], camPos[1], camPos[2]);
  const t = clamp((LOD_FAR - dist) / (LOD_FAR - LOD_NEAR), 0, 1);
  return Math.round(LOD_MIN_ITER + t * (LOD_MAX_ITER - LOD_MIN_ITER));
}

const keys = new Set<string>();
let yawAccum = 0;
let pitchAccum = 0;
const MOUSE_SENSITIVITY = 0.0022;

window.addEventListener("keydown", (e) => keys.add(e.code));
window.addEventListener("keyup", (e) => keys.delete(e.code));
window.addEventListener("blur", () => keys.clear());

canvas.addEventListener("click", () => {
  if (document.pointerLockElement !== canvas) {
    canvas.requestPointerLock();
  }
});

window.addEventListener("mousemove", (e) => {
  if (document.pointerLockElement !== canvas) return;
  yawAccum += e.movementX * MOUSE_SENSITIVITY;
  pitchAccum += e.movementY * MOUSE_SENSITIVITY;
});

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

function readInput(): { cam: CameraInput; boost: boolean } {
  const kForward = (keys.has("KeyW") ? 1 : 0) - (keys.has("KeyS") ? 1 : 0);
  const kRight = (keys.has("KeyD") ? 1 : 0) - (keys.has("KeyA") ? 1 : 0);
  const up = (keys.has("Space") ? 1 : 0) - (keys.has("ShiftLeft") || keys.has("ShiftRight") ? 1 : 0);
  const roll = (keys.has("KeyE") ? 1 : 0) - (keys.has("KeyQ") ? 1 : 0);

  const touch = touchControls.consumeFrame();

  const cam: CameraInput = {
    forward: clamp(kForward + touch.forward, -1, 1),
    right: clamp(kRight + touch.right, -1, 1),
    up,
    roll,
    yawDelta: yawAccum + touch.yawDelta,
    pitchDelta: pitchAccum + touch.pitchDelta,
  };
  yawAccum = 0;
  pitchAccum = 0;

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

  const time = now / 1000;
  renderer.render({
    camPos: camera.position,
    camRight: camera.rightAxis,
    camUp: camera.upAxis,
    camForward: camera.forwardAxis,
    fov: camera.fov,
    time,
    power: POWER_BASE + Math.sin(time * POWER_FREQ) * POWER_AMPLITUDE,
    maxIter: fractalDetail(camera.position),
    raySteps: q.raySteps,
  });

  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
