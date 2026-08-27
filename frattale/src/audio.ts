// Audio generativo in tempo reale (Web Audio API, nessun file precampionato).
// Tutto passa dallo stesso bus (filtro condiviso -> master): il drone
// ambientale e gli impulsi ritmici non sono due suoni separati sovrapposti,
// sono voci della stessa composizione, cosi' si combinano davvero in
// un'unica musica invece di leggersi come effetti sonori indipendenti.
//
// Deliberatamente senza spazializzazione 3D (niente PannerNode/
// AudioListener) e senza riverbero a convoluzione: un ConvolverNode e' il
// nodo piu' costoso in CPU di questa catena, e su un telefono reale, in
// concorrenza con il raymarching del frattale, la sua elaborazione era la
// causa piu' probabile sia dei "disturbi" intermittenti (buffer audio che
// non fanno in tempo) sia della sensazione di eco/ovattamento -- la coda
// del riverbero si accumulava sopra pizzicati e accordi sempre piu'
// frequenti scendendo. Rimosso del tutto: piu' leggero e piu' pulito.
//
// Due soli segnali pilotano tutto, entrambi gia' disponibili dalla camera:
//  - raggio orbitale (quanto si e' vicini/dentro il centro) -> quanto il
//    drone e' presente/risonante: da lontano piu' discreto, immergendosi
//    nella nube piu' pieno -- il timbro pero' resta sempre chiaro, non si
//    scurisce con la profondita'
//  - intensita' di movimento (orbita + zoom combinati) -> densita' degli
//    impulsi ritmici: fermi non c'e' quasi percussione, muovendosi la
//    trama si infittisce. L'impressione voluta e' che il giocatore stia
//    "componendo" muovendosi, non ascoltando una traccia di sottofondo.

const DRONE_ROOT = 55; // A1
const DRONE_RATIOS = [1, 1.5, 2, 2.996, 4.008]; // radice, quinta, ottava, +quinta/ottave leggermente stonate per battimenti
const PLUCK_NOTES = [220, 261.6, 329.6, 392, 440, 523.3]; // pentatonica su A, per gli impulsi ritmici

// I parametri (filtro, livelli drone) non hanno bisogno di risoluzione a
// 60fps: il timeConstant delle rampe e' gia' molto piu' lento (0.35-0.6s).
// Aggiornarli ogni frame significa solo mandare 6 volte piu' comandi di
// automazione del necessario dal thread principale a quello audio -- uno
// dei sospetti principali per i micro-disturbi su un telefono sotto carico
// mentre il raymarching gia' occupa la CPU.
const UPDATE_INTERVAL_MS = 100;

export class AudioEngine {
  private ctx: AudioContext;
  private master: GainNode;
  private filter: BiquadFilterNode;
  private started = false;
  private lastPulse = 0;
  private lastUpdate = 0;
  private droneGains: GainNode[] = [];
  private droneOscillators: OscillatorNode[] = [];

  constructor() {
    const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    this.ctx = new Ctx();

    this.master = this.ctx.createGain();
    this.master.gain.value = 0.85;

    // Compressore sul bus finale: permette di alzare i livelli individuali
    // senza rischiare distorsione quando drone e impulsi si sovrappongono.
    const compressor = this.ctx.createDynamicsCompressor();
    compressor.threshold.value = -18;
    compressor.knee.value = 12;
    compressor.ratio.value = 3.5;
    compressor.attack.value = 0.01;
    compressor.release.value = 0.25;
    this.master.connect(compressor);
    compressor.connect(this.ctx.destination);

    // Bus condiviso: tutto (drone + impulsi) passa dallo stesso filtro
    // prima del master, cosi' resta un'unica voce coerente. Frequenza
    // fissa e chiara -- non si scurisce piu' scendendo.
    this.filter = this.ctx.createBiquadFilter();
    this.filter.type = "lowpass";
    this.filter.frequency.value = 2400;
    this.filter.Q.value = 0.6;
    this.filter.connect(this.master);

    for (const ratio of DRONE_RATIOS) {
      const osc = this.ctx.createOscillator();
      osc.type = "sine";
      osc.frequency.value = DRONE_ROOT * ratio;
      const gain = this.ctx.createGain();
      gain.gain.value = 0.09;
      osc.connect(gain);
      gain.connect(this.filter);
      osc.start();
      this.droneGains.push(gain);
      this.droneOscillators.push(osc);
    }
  }

  /** Da chiamare dentro un gesto utente (tap/click/tasto), per rispettare
   * le policy di autoplay dei browser -- e anche per riprendere dopo un
   * suspend() (uscita/rientro dal frattale), quindi va sempre eseguito
   * anche se non e' il primo avvio. */
  resume() {
    this.started = true;
    void this.ctx.resume();
  }

  /** Da chiamare quando si esce dal frattale (pulsante X), per non tenere
   * l'audio a suonare mentre si e' tornati alla schermata iniziale. */
  suspend() {
    void this.ctx.suspend();
  }

  /**
   * @param radiusT 0 (al centro, immersi nella nube) .. 1 (lontano)
   * @param motionIntensity 0 (fermi) .. 1 (movimento massimo)
   */
  update(nowMs: number, radiusT: number, motionIntensity: number) {
    if (!this.started) return;

    // Il pizzicato puo' comunque scattare ad ogni chiamata (throttlato per
    // conto suo da lastPulse/minGapMs): solo i parametri continui (filtro,
    // livelli drone) sono limitati alla cadenza piu' bassa qui sotto.
    const minGapMs = 650 - motionIntensity * 500;
    if (nowMs - this.lastPulse > minGapMs && Math.random() < 0.15 + motionIntensity * 0.5) {
      this.lastPulse = nowMs;
      this.pluck(radiusT);
    }

    if (nowMs - this.lastUpdate < UPDATE_INTERVAL_MS) return;
    this.lastUpdate = nowMs;

    const t = this.ctx.currentTime;
    const targetDroneLevel = 0.065 + (1 - radiusT) * 0.06; // più risonante/presente da vicino
    for (const gain of this.droneGains) {
      gain.gain.setTargetAtTime(targetDroneLevel, t, 0.6);
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
      gain.gain.linearRampToValueAtTime(0.2, at + 0.015);
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
    gain.gain.linearRampToValueAtTime(0.18, t + 0.012);
    gain.gain.exponentialRampToValueAtTime(0.001, t + 0.55);

    osc.connect(gain);
    gain.connect(this.filter);
    osc.start(t);
    osc.stop(t + 0.6);
  }
}
