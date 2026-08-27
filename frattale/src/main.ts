import { FlightCamera, type CameraInput } from "./camera";
import { Renderer } from "./renderer";
import { TouchControls } from "./touchControls";

const canvas = document.getElementById("gl") as HTMLCanvasElement;
const indicatorLayer = document.getElementById("touch-layer") as HTMLElement;
const renderer = new Renderer(canvas);
const camera = new FlightCamera();
const touchControls = new TouchControls(canvas, indicatorLayer);

function clamp(v: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, v));
}

// Mandelbulb power fixed for level 1 (static fractal, no time evolution yet).
const FRACTAL_POWER = 8.0;

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

function resize() {
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  renderer.resize(Math.floor(window.innerWidth * dpr), Math.floor(window.innerHeight * dpr));
}
window.addEventListener("resize", resize);
resize();

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
  const dt = Math.min((now - last) / 1000, 1 / 20);
  last = now;

  const { cam, boost } = readInput();
  camera.update(dt, cam, boost);

  renderer.render({
    camPos: camera.position,
    camRight: camera.rightAxis,
    camUp: camera.upAxis,
    camForward: camera.forwardAxis,
    fov: camera.fov,
    time: now / 1000,
    power: FRACTAL_POWER,
  });

  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
