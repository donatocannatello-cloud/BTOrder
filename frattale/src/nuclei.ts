// I nuclei: la meccanica di gioco. Ogni livello frattale ne nasconde uno.
//
// Non si vedono, si *sentono*. Avvicinandosi -- sia scorrendo sul piano
// sia con la scala, che e' il terzo asse della ricerca -- l'audio fa
// battere due sinusoidi vicine: il battimento rallenta man mano che ci si
// centra e si ferma quando si e' in accordo, esattamente come accordare
// una corda contro una nota di riferimento. Solo a quel punto il nucleo si
// mostra. Restare fermi in accordo per un istante lo risolve, e resta
// risolto per sempre (localStorage).
//
// Non c'e' modo di sbagliare: nessun tempo, nessun fallimento, nessun
// comando in piu' da imparare. L'unico verbo del gioco resta "essere in un
// posto a una certa scala".
//
// La posizione di un nucleo e' definita nello spazio di *navigazione*
// (lo stesso di centerX/centerY), non nelle coordinate ripiegate che lo
// shader campiona. E' deliberato: replicare in JS la hash GLSL darebbe
// risultati diversi, perche' quella gira in float32 e questa in float64, e
// una catena di fract() amplifica qualunque differenza di precisione.
// Definendo il nucleo nello spazio che la camera controlla, il problema
// non si pone: la sua posizione sullo schermo si ricava da soli
// centro/zoom, e lo shader la riceve gia' pronta.

import { SCALE, MIRROR_HALF } from "./camera";

const MIRROR_PERIOD = 4 * MIRROR_HALF;

/** Tolleranze di "accordo": quanto si puo' essere lontani e sentire ancora
 * qualcosa. Sulla scala e' piu' stretta della posizione, cosi' trovare il
 * livello giusto conta quanto trovare il punto. */
const TOL_POS = 0.85; // in unita' di schermo (uv, y va da -1 a 1)
const TOL_SCALE = 0.55; // in livelli
const SOLVE_CLOSENESS = 0.9;
const SOLVE_HOLD_S = 1.2;

const STORAGE_KEY = "discesa-frattale/nuclei";
const STORAGE_VERSION = 1;

function clamp(v: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, v));
}

/** Riporta v in [-period/2, period/2). Lo spazio di navigazione e'
 * periodico (vedi camera.ts), quindi la distanza fra due punti va presa
 * per la via piu' corta, non in linea retta. */
function wrapSigned(v: number, period: number) {
  return v - period * Math.floor(v / period + 0.5);
}

/** Hash deterministica: lo stesso livello ha sempre lo stesso nucleo, su
 * qualunque dispositivo e in qualunque sessione, senza salvare nulla. */
function hash(n: number, salt: number) {
  const x = Math.sin(n * 127.1 + salt * 311.7) * 43758.5453;
  return x - Math.floor(x);
}

/** Posizione del nucleo del livello, nello spazio di navigazione. Tenuta
 * entro il riquadro fondamentale: e' li' che il frattale ha struttura, e
 * oltre il mondo e' comunque solo il suo riflesso. */
function nucleusAt(level: number): { x: number; y: number } {
  const r = MIRROR_HALF * 0.78;
  return {
    x: (hash(level, 1) * 2 - 1) * r,
    y: (hash(level, 2) * 2 - 1) * r,
  };
}

export interface NucleusView {
  /** Livello a cui appartiene il nucleo attivo. */
  level: number;
  /** Posizione sullo schermo in coordinate uv (y da -1 a 1), come le usa
   * lo shader. Puo' cadere ampiamente fuori schermo se si e' lontani. */
  uvX: number;
  uvY: number;
  /** 0 = nessun segnale, 1 = in perfetto accordo. */
  closeness: number;
  /** Gia' risolto in una sessione precedente (o in questa). */
  solved: boolean;
  /** 0..1, animazione di fioritura appena risolto. */
  bloom: number;
}

export class Nuclei {
  private solved = new Set<number>();
  private holdS = 0;
  private bloomS = 0;
  private bloomLevel: number | null = null;
  /** Impostata quando un nucleo viene risolto in questo frame, cosi' il
   * chiamante puo' suonare l'accento senza fare da tramite lui stesso. */
  justResolved = false;

  constructor() {
    this.load();
  }

  get solvedCount() {
    return this.solved.size;
  }

  private load() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const data = JSON.parse(raw) as { v?: number; solved?: number[] };
      // Schema versionato: se un giorno cambia la generazione dei nuclei,
      // uno stato vecchio va ignorato invece di indicare punti sbagliati.
      if (data.v !== STORAGE_VERSION || !Array.isArray(data.solved)) return;
      for (const l of data.solved) if (Number.isFinite(l)) this.solved.add(l);
    } catch {
      // localStorage puo' essere negato (finestra privata, impostazioni):
      // il gioco resta giocabile, semplicemente non ricorda.
    }
  }

  private save() {
    try {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ v: STORAGE_VERSION, solved: [...this.solved] })
      );
    } catch {
      /* vedi load() */
    }
  }

  /**
   * @param dt secondi dall'ultimo frame
   * @param centerX,centerY centro della mappa (spazio di navigazione)
   * @param zoomLevel zoom continuo: la sua parte intera e' il livello
   */
  update(dt: number, centerX: number, centerY: number, zoomLevel: number): NucleusView {
    this.justResolved = false;

    // Il nucleo "attivo" e' quello del livello piu' vicino in scala:
    // e' l'unico che si puo' accordare da qui.
    const level = Math.round(zoomLevel);
    const n = nucleusAt(level);

    // Scala a cui questo livello e' disegnato adesso. Il ciclo dello shader
    // usa SCALE^(k - frac) con k = level - layerBase, e
    // k - frac == level - zoomLevel: quindi basta lo zoom continuo.
    const s = Math.pow(SCALE, level - zoomLevel);
    const uvX = wrapSigned(n.x - centerX, MIRROR_PERIOD) / s;
    const uvY = wrapSigned(n.y - centerY, MIRROR_PERIOD) / s;

    const posCloseness = clamp(1 - Math.hypot(uvX, uvY) / TOL_POS, 0, 1);
    const scaleCloseness = clamp(1 - Math.abs(zoomLevel - level) / TOL_SCALE, 0, 1);
    // Prodotto e non media: bisogna azzeccare *entrambe* le cose, e stare
    // nel punto giusto alla scala sbagliata non deve dare mezzo segnale.
    const closeness = posCloseness * scaleCloseness;

    const already = this.solved.has(level);
    if (!already && closeness >= SOLVE_CLOSENESS) {
      this.holdS += dt;
      if (this.holdS >= SOLVE_HOLD_S) {
        this.solved.add(level);
        this.save();
        this.justResolved = true;
        this.bloomS = 0;
        this.bloomLevel = level;
        this.holdS = 0;
      }
    } else {
      this.holdS = 0;
    }

    if (this.bloomLevel !== null) {
      this.bloomS += dt;
      if (this.bloomS > 2.5) this.bloomLevel = null;
    }

    const bloom =
      this.bloomLevel === level ? clamp(1 - this.bloomS / 2.5, 0, 1) : 0;

    return { level, uvX, uvY, closeness, solved: this.solved.has(level), bloom };
  }
}
