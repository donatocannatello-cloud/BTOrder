// Qualita' adattiva: la leva reale per alleggerire un raymarcher non e' il
// formato di output (e' comunque un calcolo per-pixel, non esiste un
// equivalente "vettoriale"), ma quanti pixel e quanti step di sphere-
// tracing si calcolano per frame. Si misura il tempo-frame reale e si
// riduce risoluzione interna e budget di step quando il dispositivo fatica
// (tipicamente un telefono Android di fascia media), per poi risalire se
// il framerate lo permette. Cambi piccoli e poco frequenti, per evitare
// che la qualita' "pompi" avanti e indietro in modo visibile.

export interface QualityState {
  renderScale: number; // moltiplica la risoluzione del canvas (oltre al DPR)
  raySteps: number; // budget di step di sphere-tracing per pixel
}

const RENDER_SCALE_MIN = 0.5;
const RENDER_SCALE_MAX = 1.0;
const RENDER_SCALE_STEP = 0.08;

const RAY_STEPS_MIN = 55;
const RAY_STEPS_MAX = 95;
const RAY_STEPS_STEP = 10;

const TARGET_FPS_LOW = 30; // sotto: degrada
const TARGET_FPS_HIGH = 55; // sopra (con margine): puo' risalire

const ADJUST_INTERVAL_MS = 900;
const WINDOW_SIZE = 30;

export class QualityManager {
  private state: QualityState = { renderScale: 0.82, raySteps: 80 };
  private frameTimes: number[] = [];
  private lastAdjust = 0;

  recordFrame(dtMs: number) {
    this.frameTimes.push(dtMs);
    if (this.frameTimes.length > WINDOW_SIZE) this.frameTimes.shift();
  }

  /** Da chiamare una volta per frame; ritorna lo stato corrente (invariato
   * finche' non e' il momento di ricalcolare). */
  update(nowMs: number): QualityState {
    if (nowMs - this.lastAdjust < ADJUST_INTERVAL_MS || this.frameTimes.length < 10) {
      return this.state;
    }
    this.lastAdjust = nowMs;

    const avgMs = this.frameTimes.reduce((a, b) => a + b, 0) / this.frameTimes.length;
    const fps = 1000 / avgMs;

    if (fps < TARGET_FPS_LOW) {
      // Prima si abbassa la risoluzione (il taglio piu' economico e meno
      // visibile su un frattale gia' pieno di dettaglio fine), poi gli step.
      if (this.state.renderScale > RENDER_SCALE_MIN) {
        this.state.renderScale = Math.max(RENDER_SCALE_MIN, this.state.renderScale - RENDER_SCALE_STEP);
      } else if (this.state.raySteps > RAY_STEPS_MIN) {
        this.state.raySteps = Math.max(RAY_STEPS_MIN, this.state.raySteps - RAY_STEPS_STEP);
      }
    } else if (fps > TARGET_FPS_HIGH) {
      if (this.state.raySteps < RAY_STEPS_MAX) {
        this.state.raySteps = Math.min(RAY_STEPS_MAX, this.state.raySteps + RAY_STEPS_STEP);
      } else if (this.state.renderScale < RENDER_SCALE_MAX) {
        this.state.renderScale = Math.min(RENDER_SCALE_MAX, this.state.renderScale + RENDER_SCALE_STEP);
      }
    }

    return this.state;
  }
}
