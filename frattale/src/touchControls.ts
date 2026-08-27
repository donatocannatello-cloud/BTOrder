// Controlli touch: doppio "joystick" invisibile, come nella maggior parte
// dei giochi mobile di volo/esplorazione. Nessun elemento fisso a schermo:
// gli indicatori compaiono solo sotto il dito e svaniscono al rilascio,
// così restiamo coerenti con "niente HUD permanente".
//
// Metà sinistra dello schermo = movimento (avanti/indietro + laterale).
// Metà destra = guarda intorno (yaw/pitch), stesso gesto del drag-look
// desktop. Non c'è un gesto dedicato per su/giù: come in un aereo/astro-
// nave, si sale o si scende inclinando lo sguardo e andando avanti — la
// combinazione di pitch + avanzamento copre comunque tutto lo spazio 3D,
// e tiene i controlli touch a due soli "stick" invece di tre-quattro.

export interface TouchFrameInput {
  forward: number; // -1..1, analogico
  right: number; // -1..1, analogico
  yawDelta: number;
  pitchDelta: number;
}

type Role = "move" | "look";

interface ActiveTouch {
  id: number;
  role: Role;
  startX: number;
  startY: number;
  lastX: number;
  lastY: number;
  base?: HTMLDivElement;
  knob?: HTMLDivElement;
}

const MOVE_RADIUS = 56; // px, corsa massima del joystick di movimento
const LOOK_SENSITIVITY = 0.0034;

export class TouchControls {
  private touches = new Map<number, ActiveTouch>();
  private yawAccum = 0;
  private pitchAccum = 0;
  private moveX = 0;
  private moveY = 0;

  constructor(
    private el: HTMLElement,
    private indicatorLayer: HTMLElement
  ) {
    el.addEventListener("pointerdown", this.onDown);
    el.addEventListener("pointermove", this.onMove);
    el.addEventListener("pointerup", this.onUp);
    el.addEventListener("pointercancel", this.onUp);
  }

  private onDown = (e: PointerEvent) => {
    if (e.pointerType !== "touch") return;
    const role: Role = e.clientX < window.innerWidth / 2 ? "move" : "look";
    for (const t of this.touches.values()) if (t.role === role) return; // un dito per ruolo

    const touch: ActiveTouch = {
      id: e.pointerId,
      role,
      startX: e.clientX,
      startY: e.clientY,
      lastX: e.clientX,
      lastY: e.clientY,
    };

    if (role === "move") {
      const base = document.createElement("div");
      base.className = "joy-base";
      base.style.left = `${e.clientX - MOVE_RADIUS}px`;
      base.style.top = `${e.clientY - MOVE_RADIUS}px`;
      base.style.width = base.style.height = `${MOVE_RADIUS * 2}px`;
      const knob = document.createElement("div");
      knob.className = "joy-knob";
      base.appendChild(knob);
      this.indicatorLayer.appendChild(base);
      touch.base = base;
      touch.knob = knob;
    }

    this.touches.set(e.pointerId, touch);
    this.el.setPointerCapture(e.pointerId);
  };

  private onMove = (e: PointerEvent) => {
    const t = this.touches.get(e.pointerId);
    if (!t) return;

    if (t.role === "move") {
      const dx = e.clientX - t.startX;
      const dy = e.clientY - t.startY;
      const len = Math.hypot(dx, dy);
      const clamped = Math.min(len, MOVE_RADIUS);
      const nx = len > 0 ? dx / len : 0;
      const ny = len > 0 ? dy / len : 0;
      this.moveX = nx * (clamped / MOVE_RADIUS);
      this.moveY = ny * (clamped / MOVE_RADIUS);
      if (t.knob) t.knob.style.transform = `translate(${nx * clamped}px, ${ny * clamped}px)`;
    } else {
      this.yawAccum += (e.clientX - t.lastX) * LOOK_SENSITIVITY;
      this.pitchAccum += (e.clientY - t.lastY) * LOOK_SENSITIVITY;
      t.lastX = e.clientX;
      t.lastY = e.clientY;
    }
  };

  private onUp = (e: PointerEvent) => {
    const t = this.touches.get(e.pointerId);
    if (!t) return;
    if (t.role === "move") {
      this.moveX = 0;
      this.moveY = 0;
      if (t.base) {
        t.base.classList.add("fade");
        setTimeout(() => t.base?.remove(), 220);
      }
    }
    this.touches.delete(e.pointerId);
  };

  consumeFrame(): TouchFrameInput {
    const out: TouchFrameInput = {
      forward: -this.moveY,
      right: this.moveX,
      yawDelta: this.yawAccum,
      pitchDelta: this.pitchAccum,
    };
    this.yawAccum = 0;
    this.pitchAccum = 0;
    return out;
  }
}
