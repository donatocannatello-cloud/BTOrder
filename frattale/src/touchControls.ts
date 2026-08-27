// Controlli touch: doppio joystick, sempre visibile e ancorato agli angoli
// (basso-sinistra = movimento, basso-destra = sguardo), così è chiaro a
// colpo d'occhio dove toccare. Il tocco iniziale viene comunque accettato
// in tutta la metà schermo corrispondente (non serve centrare il dito sul
// cerchietto), ma la base resta ferma nell'angolo e la manopola si sposta
// verso il dito, invece di "nascere" dove tocchi.
//
// Metà sinistra = movimento (avanti/indietro + laterale). Metà destra =
// guarda intorno (yaw/pitch), stesso gesto del drag-look desktop. Non c'è
// un gesto dedicato per su/giù: come in un aereo/astronave, si sale o si
// scende inclinando lo sguardo e andando avanti — la combinazione di pitch
// + avanzamento copre comunque tutto lo spazio 3D, e tiene i controlli
// touch a due soli stick invece di tre-quattro.

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
  lastX: number;
  lastY: number;
}

const RADIUS = 52; // px, corsa massima della manopola
const LOOK_SENSITIVITY = 0.0034;

function createJoystick(role: Role): { base: HTMLDivElement; knob: HTMLDivElement } {
  const base = document.createElement("div");
  base.className = `joy-base joy-${role}`;
  const knob = document.createElement("div");
  knob.className = "joy-knob";
  base.appendChild(knob);
  return { base, knob };
}

export class TouchControls {
  private touches = new Map<number, ActiveTouch>();
  private yawAccum = 0;
  private pitchAccum = 0;
  private moveX = 0;
  private moveY = 0;
  private moveUI = createJoystick("move");
  private lookUI = createJoystick("look");

  constructor(
    private el: HTMLElement,
    indicatorLayer: HTMLElement
  ) {
    indicatorLayer.appendChild(this.moveUI.base);
    indicatorLayer.appendChild(this.lookUI.base);

    el.addEventListener("pointerdown", this.onDown);
    el.addEventListener("pointermove", this.onMove);
    el.addEventListener("pointerup", this.onUp);
    el.addEventListener("pointercancel", this.onUp);
  }

  private anchorFor(role: Role): { x: number; y: number } {
    const rect = (role === "move" ? this.moveUI.base : this.lookUI.base).getBoundingClientRect();
    return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
  }

  private onDown = (e: PointerEvent) => {
    if (e.pointerType !== "touch") return;
    const role: Role = e.clientX < window.innerWidth / 2 ? "move" : "look";
    for (const t of this.touches.values()) if (t.role === role) return; // un dito per ruolo

    this.touches.set(e.pointerId, { id: e.pointerId, role, lastX: e.clientX, lastY: e.clientY });
    (role === "move" ? this.moveUI.base : this.lookUI.base).classList.add("active");
    this.el.setPointerCapture(e.pointerId);
    this.applyMove(role, e.clientX, e.clientY);
  };

  private applyMove(role: Role, clientX: number, clientY: number) {
    if (role !== "move") return;
    const anchor = this.anchorFor("move");
    const dx = clientX - anchor.x;
    const dy = clientY - anchor.y;
    const len = Math.hypot(dx, dy);
    const clamped = Math.min(len, RADIUS);
    const nx = len > 0 ? dx / len : 0;
    const ny = len > 0 ? dy / len : 0;
    this.moveX = nx * (clamped / RADIUS);
    this.moveY = ny * (clamped / RADIUS);
    this.moveUI.knob.style.transform = `translate(${nx * clamped}px, ${ny * clamped}px)`;
  }

  private onMove = (e: PointerEvent) => {
    const t = this.touches.get(e.pointerId);
    if (!t) return;

    if (t.role === "move") {
      this.applyMove("move", e.clientX, e.clientY);
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
      this.moveUI.knob.style.transform = "translate(0, 0)";
      this.moveUI.base.classList.remove("active");
    } else {
      this.lookUI.base.classList.remove("active");
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
