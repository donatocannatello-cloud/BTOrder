// Controlli touch, sempre visibili. A sinistra uno stick circolare per
// orbitare intorno al centro del frattale: asse orizzontale = azimut,
// asse verticale = elevazione -- le "4 direzioni cardinali" (su/giù/
// sinistra/destra sullo stick). A destra una levetta verticale, con un
// design deliberatamente diverso (un binario, non un disco) per segnalare
// che governa un solo asse: avvicinarsi/allontanarsi dal centro.
//
// Entrambi sono "a molla": tornano a riposo al rilascio e pilotano una
// velocità (rate), non una posizione assoluta -- la camera stessa guarda
// sempre il centro del mondo, quindi non serve un controllo di "sguardo"
// libero separato.

export interface TouchFrameInput {
  azimuth: number; // -1..1
  elevation: number; // -1..1
  zoom: number; // -1..1, positivo = avvicina
}

const STICK_RADIUS = 50; // px, corsa massima dello stick di orbita
const LEVER_HALF_HEIGHT = 55; // px, corsa massima della levetta sopra/sotto il centro

function clamp(v: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, v));
}

export class TouchControls {
  private stickTouchId: number | null = null;
  private leverTouchId: number | null = null;
  private azimuthValue = 0;
  private elevationValue = 0;
  private zoomValue = 0;

  private stickBase: HTMLDivElement;
  private stickKnob: HTMLDivElement;
  private leverTrack: HTMLDivElement;
  private leverHandle: HTMLDivElement;

  constructor(
    private el: HTMLElement,
    indicatorLayer: HTMLElement
  ) {
    this.stickBase = document.createElement("div");
    this.stickBase.className = "stick-base";
    this.stickKnob = document.createElement("div");
    this.stickKnob.className = "stick-knob";
    this.stickBase.appendChild(this.stickKnob);

    this.leverTrack = document.createElement("div");
    this.leverTrack.className = "lever-track";
    this.leverHandle = document.createElement("div");
    this.leverHandle.className = "lever-handle";
    this.leverTrack.appendChild(this.leverHandle);

    indicatorLayer.appendChild(this.stickBase);
    indicatorLayer.appendChild(this.leverTrack);

    el.addEventListener("pointerdown", this.onDown);
    el.addEventListener("pointermove", this.onMove);
    el.addEventListener("pointerup", this.onUp);
    el.addEventListener("pointercancel", this.onUp);
  }

  private onDown = (e: PointerEvent) => {
    if (e.pointerType !== "touch") return;
    const isLeft = e.clientX < window.innerWidth / 2;
    if (isLeft && this.stickTouchId === null) {
      this.stickTouchId = e.pointerId;
      this.stickBase.classList.add("active");
      this.el.setPointerCapture(e.pointerId);
      this.applyStick(e.clientX, e.clientY);
    } else if (!isLeft && this.leverTouchId === null) {
      this.leverTouchId = e.pointerId;
      this.leverTrack.classList.add("active");
      this.el.setPointerCapture(e.pointerId);
      this.applyLever(e.clientY);
    }
  };

  private applyStick(clientX: number, clientY: number) {
    const rect = this.stickBase.getBoundingClientRect();
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    const dx = clientX - cx;
    const dy = clientY - cy;
    const len = Math.hypot(dx, dy);
    const clamped = Math.min(len, STICK_RADIUS);
    const nx = len > 0 ? dx / len : 0;
    const ny = len > 0 ? dy / len : 0;
    // Confermato dal test con l'utente: il verticale (su/giu') e' corretto
    // con la mappatura originale (spingere su fa salire visivamente il
    // frattale nell'inquadratura), mentre l'orizzontale va lasciato nella
    // sua mappatura originale (non invertita) -- il tentativo precedente di
    // invertirlo lo rendeva sbagliato nel verso opposto. Il pallino segue
    // comunque il dito normalmente (nx/ny non invertiti nel transform).
    this.azimuthValue = nx * (clamped / STICK_RADIUS);
    this.elevationValue = -ny * (clamped / STICK_RADIUS);
    this.stickKnob.style.transform = `translate(${nx * clamped}px, ${ny * clamped}px)`;
  }

  private applyLever(clientY: number) {
    const rect = this.leverTrack.getBoundingClientRect();
    const cy = rect.top + rect.height / 2;
    const dy = clamp(clientY - cy, -LEVER_HALF_HEIGHT, LEVER_HALF_HEIGHT);
    this.zoomValue = -dy / LEVER_HALF_HEIGHT;
    this.leverHandle.style.transform = `translateY(${dy}px)`;
  }

  private onMove = (e: PointerEvent) => {
    if (e.pointerId === this.stickTouchId) this.applyStick(e.clientX, e.clientY);
    else if (e.pointerId === this.leverTouchId) this.applyLever(e.clientY);
  };

  private onUp = (e: PointerEvent) => {
    if (e.pointerId === this.stickTouchId) {
      this.stickTouchId = null;
      this.azimuthValue = 0;
      this.elevationValue = 0;
      this.stickKnob.style.transform = "translate(0, 0)";
      this.stickBase.classList.remove("active");
    } else if (e.pointerId === this.leverTouchId) {
      this.leverTouchId = null;
      this.zoomValue = 0;
      this.leverHandle.style.transform = "translateY(0)";
      this.leverTrack.classList.remove("active");
    }
  };

  consumeFrame(): TouchFrameInput {
    return { azimuth: this.azimuthValue, elevation: this.elevationValue, zoom: this.zoomValue };
  }
}
