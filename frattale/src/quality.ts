// Qualita' adattiva: la leva reale per alleggerire un frattale calcolato
// per-pixel non e' il formato di output (non esiste un equivalente
// "vettoriale"), ma quanti pixel e quante iterazioni si calcolano per
// frame. Si misura il tempo-frame reale e si riducono risoluzione interna
// e budget di iterazioni quando il dispositivo fatica (tipicamente un
// telefono Android di fascia media), per poi risalire se il framerate lo
// permette. Cambi piccoli e poco frequenti, per evitare che la qualita'
// "pompi" avanti e indietro in modo visibile.
//
// Le mappe piane escape-time costano molto meno del raymarching 3D che
// c'era prima (nessun passo lungo un raggio: una sola valutazione per
// livello per pixel), quindi si parte da una qualita' sensibilmente piu'
// alta a parita' di dispositivo.

export interface QualityState {
  renderScale: number; // moltiplica la risoluzione del canvas (oltre al DPR)
  maxIter: number; // budget di iterazioni escape-time per livello, per pixel
}

const RENDER_SCALE_MIN = 0.6;
const RENDER_SCALE_MAX = 1.0;
const RENDER_SCALE_STEP = 0.08;

// Cio' che conta per il costo e' il prodotto livelli x iterazioni. La
// finestra e' passata da 3 a 5 livelli per ottenere molto piu'
// ingrandimento, quindi il tetto delle iterazioni e' sceso da 220 a 130:
// 5x130 (650) resta vicino ai 3x220 (660) di partenza. Senza questo
// aggiustamento il quality manager avrebbe ripagato i livelli in piu'
// tagliando la risoluzione, cioe' proprio la nitidezza del tratto.
const MAX_ITER_MIN = 60;
const MAX_ITER_MAX = 130;
const MAX_ITER_STEP = 20;

const TARGET_FPS_LOW = 30; // sotto: degrada
const TARGET_FPS_HIGH = 55; // sopra (con margine): puo' risalire

const ADJUST_INTERVAL_MS = 900;
const WINDOW_SIZE = 30;

export class QualityManager {
  private state: QualityState = { renderScale: 0.9, maxIter: 110 };
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
      // Prima si tagliano le iterazioni (le curve di livello piu' lontane
      // dal bordo dell'insieme restano comunque leggibili), poi la
      // risoluzione, che e' il taglio piu' visibile su un disegno a linee.
      if (this.state.maxIter > MAX_ITER_MIN) {
        this.state.maxIter = Math.max(MAX_ITER_MIN, this.state.maxIter - MAX_ITER_STEP);
      } else if (this.state.renderScale > RENDER_SCALE_MIN) {
        this.state.renderScale = Math.max(RENDER_SCALE_MIN, this.state.renderScale - RENDER_SCALE_STEP);
      }
    } else if (fps > TARGET_FPS_HIGH) {
      if (this.state.renderScale < RENDER_SCALE_MAX) {
        this.state.renderScale = Math.min(RENDER_SCALE_MAX, this.state.renderScale + RENDER_SCALE_STEP);
      } else if (this.state.maxIter < MAX_ITER_MAX) {
        this.state.maxIter = Math.min(MAX_ITER_MAX, this.state.maxIter + MAX_ITER_STEP);
      }
    }

    return this.state;
  }
}
