// Navigazione a mappa piana, non piu' orbitale: il mondo non e' una serie
// di sfere concentriche viste da fuori, ma una pila di mappe frattali
// piatte sovrapposte, attraversate come si naviga in Google Maps --
// si scorre (pan) e si scende/sale di scala (zoom), sempre guardando il
// piano dall'alto. Non c'e' piu' nessuna camera 3D, nessun raggio,
// nessun raymarching: lo shader campiona direttamente il piano.
//
// Lo zoom e' un numero continuo *illimitato* in entrambe le direzioni:
// la sua parte intera e' l'indice del livello frattale attuale, la parte
// frazionaria dice quanto si e' dentro la transizione verso il livello
// successivo. Scendendo (o salendo) l'indice cambia all'infinito, e con
// esso i frattali che compaiono -- vedi shaders/fractalMap.ts.
//
// Il centro invece resta sempre limitato, e questa e' la proprieta' che
// rende lo zoom davvero infinito: il passo di pan e' proporzionale alla
// scala *frazionaria* (SCALE^-frac, quindi compreso fra 1/SCALE e 1), mai
// alla scala assoluta. Quando la parte intera avanza di uno, il fattore di
// scala del nuovo livello di base coincide esattamente con quello che
// aveva il livello successivo un istante prima (SCALE^(1-1) == SCALE^0),
// quindi la transizione e' continua senza dover ri-ancorare il centro. Le
// coordinate non crescono mai, e la precisione in virgola mobile non si
// degrada per quanto a fondo si scenda.

function clamp(v: number, lo: number, hi: number) {
  return Math.max(lo, Math.min(hi, v));
}

export interface MapInput {
  panX: number; // -1..1, scorrimento orizzontale (stick/tastiera, con inerzia)
  panY: number; // -1..1, scorrimento verticale (stick/tastiera, con inerzia)
  zoom: number; // -1..1, positivo = scende di scala (con inerzia)
  dragX: number; // unita' di schermo in questo frame, istantaneo (drag mouse)
  dragY: number;
}

/** Deve combaciare con SCALE nello shader. */
export const SCALE = 2.2;

// Il mondo non ha piu' bordi: oltre il riquadro fondamentale la mappa si
// *specchia*, all'infinito, in tutte le direzioni. E' l'unico modo per
// avere uno scorrimento illimitato senza inventare struttura dove il
// frattale non ne ha: fuori dal suo raggio di interesse un insieme di
// Julia degenera in vuoto uniforme, quindi lasciar scorrere davvero via
// darebbe deserto, e un semplice wrap (modulo) darebbe una cucitura netta
// e visibile ad ogni giro. Il ripiegamento a specchio invece e' continuo:
// il disegno prosegue riflesso, come una sala degli specchi.
//
// Il ripiegamento vero avviene nello shader (mirrorFold); qui serve solo
// il periodo, per poter riportare il centro dentro un giro e non farlo
// crescere mai. MIRROR_HALF deve combaciare con lo shader.
export const MIRROR_HALF = 1.5;
/** Il triangolo di riflessione si ripete ogni 4 semilati. */
const MIRROR_PERIOD = 4 * MIRROR_HALF;

/** Riporta v in [-period/2, period/2): il ripiegamento e' periodico, quindi
 * e' una trasformazione invisibile che tiene le coordinate limitate per
 * sempre, per quanto a lungo si scorra. */
function wrapSigned(v: number, period: number) {
  return v - period * Math.floor(v / period + 0.5);
}

const ZOOM_SPEED = 0.55; // livelli al secondo a levetta tutta spinta
const PAN_SPEED = 0.85; // unita' di schermo al secondo a stick tutto spinto
const BOOST_MULT = 2.4;
const EASE = 4.0; // costante di smoothing dell'inerzia

export class MapCamera {
  centerX = 0;
  centerY = 0;
  /** Continuo e illimitato: floor() = indice del livello, frac = transizione. */
  zoomLevel = 0;
  /** 0..1: quanto ci si sta muovendo (pan + zoom combinati), per l'audio. */
  motionIntensity = 0;

  private velPanX = 0;
  private velPanY = 0;
  private velZoom = 0;

  /** Indice (intero, anche negativo) del livello frattale di base. */
  get layerBase(): number {
    return Math.floor(this.zoomLevel);
  }

  /** 0..1: quanto si e' dentro la transizione verso il livello successivo. */
  get layerFrac(): number {
    return this.zoomLevel - this.layerBase;
  }

  update(dt: number, input: MapInput, boost: boolean) {
    const mult = boost ? BOOST_MULT : 1;

    const ease = 1 - Math.exp(-EASE * dt);
    this.velPanX += (input.panX * PAN_SPEED * mult - this.velPanX) * ease;
    this.velPanY += (input.panY * PAN_SPEED * mult - this.velPanY) * ease;
    this.velZoom += (input.zoom * ZOOM_SPEED * mult - this.velZoom) * ease;

    this.zoomLevel += this.velZoom * dt;

    // Il pan e' in unita' di schermo: va convertito nelle coordinate del
    // livello di base, che sono piu' fitte man mano che ci si addentra
    // nella transizione (SCALE^-frac). Cosi' la velocita' di scorrimento
    // percepita resta costante a qualunque profondita', come su una mappa.
    const screenToLayer = Math.pow(SCALE, -this.layerFrac);
    const dx = (this.velPanX * dt + input.dragX) * screenToLayer;
    const dy = (this.velPanY * dt + input.dragY) * screenToLayer;

    // Lo stick muove il *punto di vista*, non il contenuto: spingendo a
    // destra ci si sposta verso destra sulla mappa e il disegno scorre
    // verso sinistra, come quando ci si muove su una mappa invece di
    // trascinarla.
    this.centerX += dx;
    this.centerY += dy;

    // Nessun bordo: si scorre a oltranza in qualsiasi direzione. Il centro
    // viene solo riportato dentro un periodo del ripiegamento a specchio --
    // trasformazione invisibile, dato che lo shader ripiega con lo stesso
    // periodo, ma che impedisce alle coordinate di crescere e alla
    // precisione in virgola mobile di degradarsi anche dopo ore di
    // scorrimento nella stessa direzione.
    this.centerX = wrapSigned(this.centerX, MIRROR_PERIOD);
    this.centerY = wrapSigned(this.centerY, MIRROR_PERIOD);

    const panSpeed = Math.hypot(this.velPanX, this.velPanY) / PAN_SPEED;
    const zoomSpeed = Math.abs(this.velZoom) / ZOOM_SPEED;
    this.motionIntensity = clamp(panSpeed + zoomSpeed, 0, 1);
  }
}
