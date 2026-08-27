// Camera orbitale: il mondo ha un centro (l'origine, dove vive il
// frattale) e la camera lo guarda sempre. Due modi di muoversi: orbitare
// attorno al centro (azimut orizzontale + elevazione verticale -- le "4
// direzioni cardinali") e avvicinarsi/allontanarsi dal centro (raggio).
// Nessun volo libero, nessun roll: la posizione e' interamente definita
// da coordinate sferiche attorno all'origine.
//
// Il raggio si muove in scala logaritmica (moltiplicativa), non lineare:
// per un affondamento continuo su molti ordini di grandezza la velocita'
// deve essere relativa alla scala attuale, altrimenti vicino al centro il
// movimento sembrerebbe schizzare via (o, al contrario, essere fermo).
// E' lo stesso motivo per cui uno zoom di mappa/fotocamera e' sempre
// moltiplicativo, mai un semplice +/- costante.

export type Vec3 = [number, number, number];

function vNormalize(a: Vec3): Vec3 {
  const len = Math.hypot(a[0], a[1], a[2]) || 1;
  return [a[0] / len, a[1] / len, a[2] / len];
}
function vCross(a: Vec3, b: Vec3): Vec3 {
  return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
}
function clamp(v: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, v));
}

export interface OrbitInput {
  azimuth: number; // -1..1, orbita sinistra/destra (stick/tastiera, con inerzia)
  elevation: number; // -1..1, orbita su/giù (stick/tastiera, con inerzia)
  zoom: number; // -1..1, positivo = avvicina al centro (con inerzia)
  dragAzimuth: number; // radianti in questo frame, istantaneo (drag mouse)
  dragElevation: number; // radianti in questo frame, istantaneo (drag mouse)
}

// Il raggio minimo e' bassissimo (non uno "zero" vero, ma abbastanza
// vicino da dare comunque moltissimi ordini di grandezza di discesa
// continua prima che la precisione in virgola mobile del renderer diventi
// un problema pratico) -- l'affondamento e' continuo, senza un reset a
// scatti: e' lo shader, non la camera, a occuparsi di far apparire nuovi
// frattali via via che si scende (vedi shaders/raymarch.ts, SCALE/NUM_LAYERS).
export const RADIUS_MIN = 1e-5;
export const RADIUS_MAX = 9.0;
const LOG_RADIUS_SPEED = 0.8; // "e-fold" al secondo a levetta tutta spinta: velocita' relativa, non assoluta
const ROTATE_SPEED = 1.0; // rad/s a stick tutto spinto
const BOOST_MULT = 2.4;
const ELEVATION_LIMIT = 1.45; // rad (~83°): resta sotto i poli, niente flip
const EASE = 4.0; // costante di smoothing dell'inerzia
const BASE_FOV = (58 * Math.PI) / 180;
const MAX_FOV = (70 * Math.PI) / 180;

const WORLD_UP: Vec3 = [0, 1, 0];

export class OrbitCamera {
  radius = 5.2;
  azimuth = 0;
  elevation = 0;
  fov = BASE_FOV;
  /** 0..1: quanto ci si sta muovendo (orbita + zoom combinati), per l'audio. */
  motionIntensity = 0;

  private velAzimuth = 0;
  private velElevation = 0;
  private velLogRadius = 0;

  get position(): Vec3 {
    const ce = Math.cos(this.elevation);
    const se = Math.sin(this.elevation);
    const sa = Math.sin(this.azimuth);
    const ca = Math.cos(this.azimuth);
    return [this.radius * ce * sa, this.radius * se, this.radius * ce * ca];
  }

  get forwardAxis(): Vec3 {
    const p = this.position;
    return vNormalize([-p[0], -p[1], -p[2]]); // guarda sempre verso l'origine
  }

  get rightAxis(): Vec3 {
    return vNormalize(vCross(this.forwardAxis, WORLD_UP));
  }

  get upAxis(): Vec3 {
    return vCross(this.rightAxis, this.forwardAxis);
  }

  update(dt: number, input: OrbitInput, boost: boolean) {
    const mult = boost ? BOOST_MULT : 1;
    const targetVelAzimuth = input.azimuth * ROTATE_SPEED * mult;
    const targetVelElevation = input.elevation * ROTATE_SPEED * mult;
    const targetVelLogRadius = -input.zoom * LOG_RADIUS_SPEED * mult;

    const ease = 1 - Math.exp(-EASE * dt);
    this.velAzimuth += (targetVelAzimuth - this.velAzimuth) * ease;
    this.velElevation += (targetVelElevation - this.velElevation) * ease;
    this.velLogRadius += (targetVelLogRadius - this.velLogRadius) * ease;

    // Il drag del mouse e' manipolazione diretta (nessuna inerzia propria,
    // e' cosi' che ci si aspetta si comporti un trascinamento); stick e
    // tastiera invece pilotano una velocita' smussata dall'inerzia sopra.
    this.azimuth += this.velAzimuth * dt + input.dragAzimuth;
    this.elevation = clamp(
      this.elevation + this.velElevation * dt + input.dragElevation,
      -ELEVATION_LIMIT,
      ELEVATION_LIMIT
    );
    this.radius = clamp(this.radius * Math.exp(this.velLogRadius * dt), RADIUS_MIN, RADIUS_MAX);

    const rotSpeed = Math.hypot(this.velAzimuth, this.velElevation) / ROTATE_SPEED;
    const zoomSpeed = Math.abs(this.velLogRadius) / LOG_RADIUS_SPEED;
    this.motionIntensity = clamp(rotSpeed + zoomSpeed, 0, 1);
    const targetFov = BASE_FOV + (MAX_FOV - BASE_FOV) * this.motionIntensity;
    this.fov += (targetFov - this.fov) * (1 - Math.exp(-2.5 * dt));
  }
}
