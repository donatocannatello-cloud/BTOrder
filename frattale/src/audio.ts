// Audio generativo in tempo reale (Web Audio API, nessun file precampionato).
// Tutto passa dallo stesso bus (filtro condiviso -> master): il drone
// ambientale e gli impulsi ritmici non sono due suoni separati sovrapposti,
// sono voci della stessa composizione, cosi' si combinano davvero in
// un'unica musica invece di leggersi come effetti sonori indipendenti.
//
// Deliberatamente senza spazializzazione 3D (niente PannerNode/
// AudioListener) e senza riverbero a convoluzione: un ConvolverNode e' il
// nodo piu' costoso in CPU di questa catena, e su un telefono reale, in
// concorrenza col rendering del frattale, la sua elaborazione era la
// causa piu' probabile sia dei "disturbi" intermittenti (buffer audio che
// non fanno in tempo) sia della sensazione di eco/ovattamento -- la coda
// del riverbero si accumulava sopra pizzicati e accordi sempre piu'
// frequenti scendendo. Rimosso del tutto: piu' leggero e piu' pulito.
//
// Due soli segnali continui pilotano tutto, entrambi gia' disponibili
// dalla navigazione:
//  - avanzamento dentro il livello corrente -> quanto il drone e'
//    presente/risonante: si apre man mano che si scende dentro una mappa e
//    riparte al livello successivo -- il timbro pero' resta sempre chiaro,
//    non si scurisce con la profondita'
//  - intensita' di movimento (pan + zoom combinati) -> densita' degli
//    impulsi ritmici: fermi non c'e' quasi percussione, muovendosi la
//    trama si infittisce. L'impressione voluta e' che il giocatore stia
//    "componendo" muovendosi, non ascoltando una traccia di sottofondo.
//
// A questi si aggiunge un evento discreto: ad ogni passaggio di livello
// tutto si trasposta di un'ottava, in su o in giu' a caso (vedi
// layerTransition).

const DRONE_ROOT = 55; // A1
const DRONE_RATIOS = [1, 1.5, 2, 2.996, 4.008]; // radice, quinta, ottava, +quinta/ottave leggermente stonate per battimenti

// Peso di ciascun parziale: le armoniche alte vanno via via molto piu'
// piano, come in qualunque timbro naturale. Prima ricevevano tutte
// esattamente lo stesso guadagno -- una quarta armonica forte quanto la
// fondamentale e' precisamente cio' che fa "uuuuu" da organo invece di un
// bordone che sta sotto senza farsi notare.
const DRONE_WEIGHTS = [1.0, 0.42, 0.26, 0.13, 0.07];

// Il bordone e' un letto, non una voce: livello basso e sostanzialmente
// fermo. Prima cresceva del 92% lungo ogni livello per poi scattare
// indietro alla soglia successiva: un crescendo lento ripetuto all'
// infinito, che e' la ricetta esatta per un suono che "aumenta fino a
// diventare fastidioso". Ora l'unica modulazione e' un filo di presenza
// in piu' quando ci si muove, che non si accumula mai.
const DRONE_BASE = 0.052;
const DRONE_MOTION = 0.018;
const PLUCK_NOTES = [220, 261.6, 329.6, 392, 440, 523.3]; // pentatonica su A, per gli impulsi ritmici

// I parametri (filtro, livelli drone) non hanno bisogno di risoluzione a
// 60fps: il timeConstant delle rampe e' gia' molto piu' lento (0.35-0.6s).
// Aggiornarli ogni frame significa solo mandare 6 volte piu' comandi di
// automazione del necessario dal thread principale a quello audio -- uno
// dei sospetti principali per i micro-disturbi su un telefono sotto carico
// mentre il raymarching gia' occupa la CPU.
const UPDATE_INTERVAL_MS = 100;

// Ad ogni nuovo livello la musica si sposta di un'ottava, in su o in giu'
// a caso: stessi suoni e stesse note, solo un registro diverso, cosi' il
// passaggio si sente come un cambio di scena e non come un brano nuovo.
// E' una passeggiata casuale limitata, non un salto libero: senza limiti
// bastano pochi livelli per finire nel subsonico o oltre il taglio del
// filtro, e la musica sparirebbe. Arrivati a un estremo si rimbalza
// nell'altra direzione, quindi il registro resta sempre udibile.
const OCTAVE_MIN = -1;
const OCTAVE_MAX = 2;

// --- Accordatura dei nuclei -------------------------------------------
// Due sinusoidi vicinissime battono a |differenza| Hz. Il pilota si
// scosta dalla nota di riferimento in proporzione a *quanto si e'
// lontani* dal nucleo: lontani, un battito veloce; avvicinandosi
// rallenta; in accordo le due frequenze coincidono e il battito sparisce,
// lasciando un tono puro. E' l'esperienza fisica di accordare una corda,
// e non richiede nessun numero a schermo per essere letta.
const TUNE_ROOT = 220; // A3: registro chiaro, sopra il bordone
const TUNE_MAX_DETUNE = 0.02; // ~4.4 Hz di battito alla massima distanza
// Sopra il bordone (che sta a ~0.098 di gain totale sommando le 5
// parziali) invece che sotto: prima, a parita' di volume, il battito si
// perdeva quasi sempre nella trama del bordone e non si sentiva affatto.
const TUNE_LEVEL = 0.11;
// Sotto questa soglia il nucleo e' muto: un segnale sempre presente
// sarebbe un assillo, non un indizio. Abbassata rispetto a prima: doveva
// arrivare troppo vicini perche' iniziasse a sentirsi qualcosa.
const TUNE_FADE_IN = 0.05;

// Ogni nucleo risolto lascia una voce in piu' nel bordone: la musica
// cresce con la collezione. Limitate, altrimenti dopo qualche decina di
// nuclei il pezzo diventa un muro.
const RESOLVED_RATIOS = [1.5, 2, 2.5, 3, 4];
const RESOLVED_LEVEL = 0.026;

export class AudioEngine {
  private ctx: AudioContext;
  private master: GainNode;
  private filter: BiquadFilterNode;
  private started = false;
  private lastPulse = 0;
  private lastUpdate = 0;
  private lastTune = 0;
  private octaveShift = 0;
  private droneGains: GainNode[] = [];
  private droneOscillators: OscillatorNode[] = [];
  private tuneRef!: OscillatorNode;
  private tunePilot!: OscillatorNode;
  private tuneGain!: GainNode;
  private resolvedGains: GainNode[] = [];
  private resolvedOscillators: OscillatorNode[] = [];

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

    for (const [i, ratio] of DRONE_RATIOS.entries()) {
      const osc = this.ctx.createOscillator();
      osc.type = "sine";
      osc.frequency.value = DRONE_ROOT * ratio;
      const gain = this.ctx.createGain();
      gain.gain.value = DRONE_BASE * DRONE_WEIGHTS[i];
      osc.connect(gain);
      gain.connect(this.filter);
      osc.start();
      this.droneGains.push(gain);
      this.droneOscillators.push(osc);
    }

    // Coppia di accordatura: sempre in funzione, ma a volume zero finche'
    // non ci si avvicina a un nucleo. Tenerle accese evita il click e la
    // latenza di creare oscillatori al volo ogni volta.
    this.tuneGain = this.ctx.createGain();
    this.tuneGain.gain.value = 0;
    this.tuneGain.connect(this.filter);
    this.tuneRef = this.ctx.createOscillator();
    this.tuneRef.type = "sine";
    this.tuneRef.frequency.value = TUNE_ROOT;
    this.tuneRef.connect(this.tuneGain);
    this.tuneRef.start();
    this.tunePilot = this.ctx.createOscillator();
    this.tunePilot.type = "sine";
    this.tunePilot.frequency.value = TUNE_ROOT * (1 + TUNE_MAX_DETUNE);
    this.tunePilot.connect(this.tuneGain);
    this.tunePilot.start();
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
    const level = DRONE_BASE + motionIntensity * DRONE_MOTION;
    this.droneGains.forEach((gain, i) => {
      gain.gain.setTargetAtTime(level * DRONE_WEIGHTS[i], t, 0.6);
    });
  }

  /** Fattore di trasposizione corrente: sempre una potenza di due, quindi
   * le note restano esattamente le stesse, solo di ottava diversa. */
  private get octaveMultiplier() {
    return Math.pow(2, this.octaveShift);
  }

  /** Il bordone segue lo spostamento d'ottava solo verso il *basso*: e' un
   * letto armonico e deve restare sotto. Salendo di due ottave i suoi
   * parziali finirebbero fra 220 e 880 Hz, in piena zona di massima
   * sensibilita' dell'orecchio, e da bordone diventerebbe la voce piu'
   * forte del pezzo. Pizzicati e arpeggio prendono invece l'escursione
   * completa: sono transitori, e li' un registro alto e' brillante
   * invece che affaticante -- il cambio di livello resta udibile. */
  private get droneOctaveMultiplier() {
    return Math.pow(2, Math.min(this.octaveShift, 0));
  }

  /**
   * Vicinanza a un nucleo, 0..1. Governa lo scostamento fra le due
   * sinusoidi di accordatura: lontani battono veloce, in accordo
   * coincidono e restano un tono solo.
   */
  setTuning(closeness: number) {
    if (!this.started) return;
    // Limitata come gli altri parametri continui: chiamata ad ogni frame
    // manderebbe 60 comandi di automazione al secondo al thread audio,
    // che e' esattamente il traffico gia' identificato come causa di
    // micro-disturbi su un telefono sotto carico. Le rampe qui sotto
    // hanno costanti di tempo da 0.08-0.25s: 10Hz e' piu' che sufficiente.
    const nowMs = performance.now();
    if (nowMs - this.lastTune < UPDATE_INTERVAL_MS) return;
    this.lastTune = nowMs;

    const t = this.ctx.currentTime;
    const c = Math.max(0, Math.min(1, closeness));
    const f0 = TUNE_ROOT * this.droneOctaveMultiplier;
    this.tuneRef.frequency.setTargetAtTime(f0, t, 0.08);
    this.tunePilot.frequency.setTargetAtTime(f0 * (1 + (1 - c) * TUNE_MAX_DETUNE), t, 0.08);
    // Entra solo da vicino, e si spegne del tutto una volta risolto il
    // nucleo (il chiamante passa 0): il premio e' il silenzio del
    // battito, non un tono che resta li' a ronzare.
    const level = c < TUNE_FADE_IN ? 0 : TUNE_LEVEL * Math.min(1, (c - TUNE_FADE_IN) / 0.35);
    this.tuneGain.gain.setTargetAtTime(level, t, 0.25);
  }

  /**
   * Nucleo risolto: un accordo breve, e una voce in piu' che resta nel
   * bordone per sempre -- la musica cresce con la collezione.
   */
  resolveNucleus(index: number) {
    if (!this.started) return;
    const t = this.ctx.currentTime;
    const octave = this.droneOctaveMultiplier;

    if (this.resolvedGains.length < RESOLVED_RATIOS.length) {
      const ratio = RESOLVED_RATIOS[this.resolvedGains.length];
      const osc = this.ctx.createOscillator();
      osc.type = "sine";
      osc.frequency.value = DRONE_ROOT * ratio * octave;
      const gain = this.ctx.createGain();
      gain.gain.setValueAtTime(0, t);
      gain.gain.setTargetAtTime(RESOLVED_LEVEL, t, 1.2); // entra piano, senza annunciarsi
      osc.connect(gain);
      gain.connect(this.filter);
      osc.start();
      this.resolvedGains.push(gain);
      this.resolvedOscillators.push(osc);
    }

    // Accordo ascendente: il contrario dell'arpeggio discendente che
    // segna il passaggio di livello, cosi' i due eventi non si confondono.
    const notes = [329.6, 440, 659.3];
    notes.forEach((note, i) => {
      const at = t + i * 0.11;
      const osc = this.ctx.createOscillator();
      osc.type = "triangle";
      osc.frequency.value = note * octave;
      const gain = this.ctx.createGain();
      gain.gain.setValueAtTime(0, at);
      gain.gain.linearRampToValueAtTime(0.16, at + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.001, at + 1.1);
      osc.connect(gain);
      gain.connect(this.filter);
      osc.start(at);
      osc.stop(at + 1.15);
    });
    void index;
  }

  /**
   * Accento sonoro al passaggio di livello: tutta la musica si sposta di
   * un'ottava, in su o in giu' a caso, e un breve arpeggio discendente
   * segna la soglia attraversata.
   */
  layerTransition() {
    if (!this.started) return;

    const step = Math.random() < 0.5 ? -1 : 1;
    const next = this.octaveShift + step;
    // Fuori dai limiti si rimbalza invece di restare fermi: cosi' ogni
    // passaggio di livello si sente comunque muovere, anche agli estremi.
    this.octaveShift = next < OCTAVE_MIN || next > OCTAVE_MAX ? this.octaveShift - step : next;
    const octave = this.octaveMultiplier;

    const t = this.ctx.currentTime;
    const droneOctave = this.droneOctaveMultiplier;
    this.droneOscillators.forEach((osc, i) => {
      osc.frequency.setTargetAtTime(DRONE_ROOT * DRONE_RATIOS[i] * droneOctave, t, 0.8);
    });
    // Le voci guadagnate coi nuclei seguono lo stesso spostamento, altrimenti
    // dopo un paio di livelli suonerebbero contro il resto.
    this.resolvedOscillators.forEach((osc, i) => {
      osc.frequency.setTargetAtTime(DRONE_ROOT * RESOLVED_RATIOS[i] * droneOctave, t, 0.8);
    });

    const notes = [523.3, 440, 349.2];
    notes.forEach((note, i) => {
      const at = t + i * 0.09;
      const osc = this.ctx.createOscillator();
      osc.type = "triangle";
      osc.frequency.value = note * octave;
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
    // Più cupo scendendo dentro il livello, e trasposto come tutto il resto.
    osc.frequency.value = note * (0.5 + radiusT * 0.5) * this.octaveMultiplier;

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
