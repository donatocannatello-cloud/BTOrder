// Audio generativo in tempo reale (Web Audio API, nessun file precampionato).
// Tutto passa dallo stesso bus (filtro condiviso -> dry/wet -> riverbero ->
// master): il drone ambientale e gli impulsi ritmici non sono due suoni
// separati sovrapposti, sono voci della stessa composizione, cosi' si
// combinano davvero in un'unica musica invece di leggersi come effetti
// sonori indipendenti.
//
// Due soli segnali pilotano tutto, entrambi gia' disponibili dalla camera:
//  - raggio orbitale (quanto si e' vicini/dentro il centro) -> registro e
//    apertura del filtro: da lontano suono aperto e chiaro, immergendosi
//    nella nube il suono si scurisce e si fa piu' risonante/avvolgente
//  - intensita' di movimento (orbita + zoom combinati) -> densita' degli
//    impulsi ritmici: fermi non c'e' quasi percussione, muovendosi la
//    trama si infittisce. L'impressione voluta e' che il giocatore stia
//    "componendo" muovendosi, non ascoltando una traccia di sottofondo.

function makeImpulseResponse(ctx: BaseAudioContext, duration: number, decay: number): AudioBuffer {
  const rate = ctx.sampleRate;
  const length = Math.floor(rate * duration);
  const impulse = ctx.createBuffer(2, length, rate);
  for (let ch = 0; ch < 2; ch++) {
    const data = impulse.getChannelData(ch);
    for (let i = 0; i < length; i++) {
      data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, decay);
    }
  }
  return impulse;
}

const DRONE_ROOT = 55; // A1
const DRONE_RATIOS = [1, 1.5, 2, 2.996, 4.008]; // radice, quinta, ottava, +quinta/ottave leggermente stonate per battimenti
const PLUCK_NOTES = [220, 261.6, 329.6, 392, 440, 523.3]; // pentatonica su A, per gli impulsi ritmici

export class AudioEngine {
  private ctx: AudioContext;
  private master: GainNode;
  private filter: BiquadFilterNode;
  private dry: GainNode;
  private wet: GainNode;
  private started = false;
  private lastPulse = 0;
  private droneGains: GainNode[] = [];
  private droneOscillators: OscillatorNode[] = [];

  constructor() {
    const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    this.ctx = new Ctx();

    this.master = this.ctx.createGain();
    this.master.gain.value = 0.5;
    this.master.connect(this.ctx.destination);

    // Bus condiviso: tutto (drone + impulsi) passa dallo stesso filtro
    // prima di dividersi in dry/wet, cosi' resta un'unica voce coerente.
    this.filter = this.ctx.createBiquadFilter();
    this.filter.type = "lowpass";
    this.filter.frequency.value = 900;
    this.filter.Q.value = 0.6;

    this.dry = this.ctx.createGain();
    this.dry.gain.value = 0.6;
    this.wet = this.ctx.createGain();
    this.wet.gain.value = 0.55;
    this.filter.connect(this.dry);
    this.filter.connect(this.wet);
    this.dry.connect(this.master);

    const convolver = this.ctx.createConvolver();
    convolver.buffer = makeImpulseResponse(this.ctx, 3.4, 2.3);
    this.wet.connect(convolver);
    convolver.connect(this.master);

    for (const ratio of DRONE_RATIOS) {
      const osc = this.ctx.createOscillator();
      osc.type = "sine";
      osc.frequency.value = DRONE_ROOT * ratio;
      const gain = this.ctx.createGain();
      gain.gain.value = 0.045;
      osc.connect(gain);
      gain.connect(this.filter);
      osc.start();
      this.droneGains.push(gain);
      this.droneOscillators.push(osc);
    }
  }

  /** Da chiamare dentro un gesto utente (tap/click/tasto), per rispettare
   * le policy di autoplay dei browser. */
  resume() {
    if (this.started) return;
    this.started = true;
    void this.ctx.resume();
  }

  /**
   * @param radiusT 0 (al centro, immersi nella nube) .. 1 (lontano)
   * @param motionIntensity 0 (fermi) .. 1 (movimento massimo)
   */
  update(nowMs: number, radiusT: number, motionIntensity: number) {
    if (!this.started) return;
    const t = this.ctx.currentTime;

    const targetFreq = 260 + radiusT * 2200;
    this.filter.frequency.setTargetAtTime(targetFreq, t, 0.35);

    const targetDroneLevel = 0.03 + (1 - radiusT) * 0.035; // più risonante/presente da vicino
    for (const gain of this.droneGains) {
      gain.gain.setTargetAtTime(targetDroneLevel, t, 0.6);
    }

    // Densità ritmica: più ci si muove, più impulsi, con un tetto minimo di
    // spaziatura perché non diventi un ronzio indistinto.
    const minGapMs = 650 - motionIntensity * 500;
    if (nowMs - this.lastPulse > minGapMs && Math.random() < 0.15 + motionIntensity * 0.5) {
      this.lastPulse = nowMs;
      this.pluck(radiusT);
    }
  }

  /**
   * Accento sonoro al passaggio di livello: la radice del drone scende di
   * un semitono per livello (avvolta ogni 7 livelli, cosi' non esce mai
   * dal registro udibile anche scendendo all'infinito) e un breve
   * arpeggio discendente segna il momento della soglia attraversata.
   */
  layerTransition(depthLayer: number) {
    if (!this.started) return;
    const t = this.ctx.currentTime;
    const semitones = depthLayer % 7;
    const rootMultiplier = Math.pow(2, -semitones / 12);
    this.droneOscillators.forEach((osc, i) => {
      osc.frequency.setTargetAtTime(DRONE_ROOT * DRONE_RATIOS[i] * rootMultiplier, t, 0.8);
    });

    const notes = [523.3, 440, 349.2];
    notes.forEach((note, i) => {
      const at = t + i * 0.09;
      const osc = this.ctx.createOscillator();
      osc.type = "triangle";
      osc.frequency.value = note;
      const gain = this.ctx.createGain();
      gain.gain.setValueAtTime(0, at);
      gain.gain.linearRampToValueAtTime(0.11, at + 0.015);
      gain.gain.exponentialRampToValueAtTime(0.001, at + 0.7);
      osc.connect(gain);
      gain.connect(this.filter);
      osc.start(at);
      osc.stop(at + 0.75);
    });
  }

  private pluck(radiusT: number) {
    const t = this.ctx.currentTime;
    const note = PLUCK_NOTES[Math.floor(Math.random() * PLUCK_NOTES.length)];
    const osc = this.ctx.createOscillator();
    osc.type = "triangle";
    osc.frequency.value = note * (0.5 + radiusT * 0.5); // più cupo quando si è immersi

    const gain = this.ctx.createGain();
    gain.gain.setValueAtTime(0, t);
    gain.gain.linearRampToValueAtTime(0.1, t + 0.012);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.55);

    osc.connect(gain);
    gain.connect(this.filter);
    osc.start(t);
    osc.stop(t + 0.6);
  }
}
