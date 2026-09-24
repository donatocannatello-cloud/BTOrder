export const VERT_SRC = /* glsl */ `#version 300 es
// Full-screen triangle, no vertex buffer needed.
const vec2 POSITIONS[3] = vec2[3](
  vec2(-1.0, -1.0),
  vec2( 3.0, -1.0),
  vec2(-1.0,  3.0)
);

void main() {
  gl_Position = vec4(POSITIONS[gl_VertexID], 0.0, 1.0);
}
`;

export const FRAG_SRC = /* glsl */ `#version 300 es
precision highp float;

uniform vec2 uResolution;
uniform vec2 uCenter;      // centro della mappa, coordinate locali del livello di base (limitato)
uniform float uFrac;       // 0..1: quanto si e' dentro la transizione verso il livello successivo
uniform float uLayerBase;  // indice assoluto (intero, anche negativo) del livello di base
uniform int uMaxIter;      // budget di dettaglio, regolato dal quality manager
uniform float uTime;
uniform float uBreath;     // lenta oscillazione: la scheda "respira" anche da ferma
uniform vec2 uNucleusUv;   // posizione del nucleo attivo, in coordinate schermo
uniform float uNucleusGlow;   // 0..1, vicinanza: quanto si sta per accordarlo
uniform float uNucleusSolved; // 1 se gia' risolto
uniform float uNucleusBloom;  // 1..0, fioritura al momento della risoluzione

out vec4 fragColor;

// Mappe piane sovrapposte, non mondi sferici concentrici -- non piu' pero'
// insiemi di Julia: ogni livello e' ora un die di silicio visto al
// microscopio, come una vera foto di un chip decapsulato. Il piano e'
// diviso in blocchi (un floorplan), ognuno con la propria trama -- reticolo
// dorato, array di via, bande argento di un banco di memoria, pad di
// bonding -- scelta da una hash deterministica del blocco (vedi dieCell).
// Piu' ottave della stessa griglia, a passi via via piu' fini (vedi
// shadeLayer), danno alla scheda il dettaglio infinito che un frattale ha
// di suo: e' un die dentro un die dentro un die. La logica di impilamento
// fra livelli -- finestra K_MIN..K_MIN+NUM_LAYERS-1, dissolvenza incrociata,
// ripiegamento a specchio, nucleo -- e' la stessa di prima: cambia solo
// cosa viene disegnato per ogni livello, non come i livelli si susseguono
// scendendo. I livelli condividono un unico sistema di coordinate 2D ma
// sono campionati a scale diverse:
//
//   p_k = uCenter + offset(L) + uv * SCALE^(k - uFrac)
//
// La finestra va da k = K_MIN (-3) a k = K_MIN + NUM_LAYERS - 1 (+1): il
// livello a -3 e' quello che si sta gia' superando, ingrandito e in
// dissolvenza; -2, -1 e 0 sono a piena intensita'; +1 e' la scheda fine che
// si intravede appena dal fondo. Scendendo, uFrac cresce e ogni livello
// scala di un gradino. Poiche' SCALE^(k-1) valutato a frac=1 coincide
// esattamente con SCALE^(k-1) valutato a frac=0 dopo lo scambio, l'indice
// puo' avanzare (o arretrare) all'infinito senza nessuno scatto e senza
// ri-ancorare il centro. Solo NUM_LAYERS livelli vengono valutati per
// pixel: il costo resta piatto a qualunque profondita'.
const float SCALE = 2.2;
const int NUM_LAYERS = 5;
const int K_MIN = -3;

// Resa del tratto. Il disegno e' interamente auto-illuminato: non c'e'
// nessuna luce nella scena, il colore *e'* l'emissione.
const float EXPOSURE = 1.15;
const float LINE_GAIN = 2.1;    // quanto marcato e' il tratto
const float SATURATION = 1.35;  // applicata DOPO il tonemap, vedi main()

// Il mondo non ha bordi: oltre il riquadro fondamentale [-H, H] la scheda
// prosegue *riflessa*, all'infinito, in tutte le direzioni. Fuori dal suo
// raggio di interesse una griglia di tracciati degenera in ripetizione
// vuota, quindi scorrere davvero via darebbe deserto; un wrap col modulo
// darebbe invece una cucitura netta ad ogni giro. Il ripiegamento a
// specchio e' la terza via: e' continuo (nessun salto di valore sul
// bordo), quindi il disegno prosegue senza strappi, come in una sala degli
// specchi.
const float MIRROR_HALF = 1.5;

// Onda triangolare: identita' su [-H, H], poi riflette ad ogni bordo.
// Periodo 4H -- una riflessione a destra e una a sinistra per tornare in
// fase. Deve combaciare con MIRROR_HALF/MIRROR_PERIOD in camera.ts, che
// riporta il centro dentro un periodo per non far crescere mai le
// coordinate.
float mirrorFold(float x) {
  float h = MIRROR_HALF;
  return h - abs(2.0 * h - mod(x + h, 4.0 * h));
}
vec2 mirrorFold(vec2 p) {
  return vec2(mirrorFold(p.x), mirrorFold(p.y));
}

float hash11(float p) {
  // +4096 cosi' anche gli indici negativi (si puo' salire all'infinito,
  // non solo scendere) cadono nel ramo positivo della hash.
  p = fract((p + 4096.0) * 0.1031);
  p *= p + 33.33;
  p *= p + p;
  return fract(p);
}

// Seme, rotazione e scostamento della scheda di ogni livello -- stessa
// idea di prima (un frattale diverso per livello), solo che ora
// parametrizzano una griglia di tracciati invece della costante di un
// insieme di Julia. Il livello 0 e' fissato, cosi' il punto di partenza
// non cambia mai.
float layerSeed(float L) {
  return abs(L) < 0.5 ? 0.0 : hash11(L * 12.9898 + 3.1) * 1000.0;
}
float layerHue(float L) {
  return abs(L) < 0.5 ? 0.0 : hash11(L * 5.13 + 1.7) * 6.2832;
}
// Rotazione fissa (non nel tempo: una scheda che ruota disorienta) e
// scostamento del centro, cosi' i livelli non risultano tutti allineati.
float layerRot(float L) {
  return abs(L) < 0.5 ? 0.0 : hash11(L * 3.77 + 5.9) * 6.2832;
}
vec2 layerOffset(float L) {
  if (abs(L) < 0.5) return vec2(0.0);
  return (vec2(hash11(L * 9.41 + 2.3), hash11(L * 6.17 + 8.5)) - 0.5) * 0.5;
}
// Passo di base della griglia (ottava piu' grossa, vedi shadeLayer): varia
// appena per livello, cosi' ogni scheda ha una densita' leggermente
// diversa dalle altre invece di essere tutte identiche.
float layerPitch(float L) {
  float j = abs(L) < 0.5 ? 0.5 : hash11(L * 8.21 + 4.4);
  return 0.30 * (0.8 + 0.4 * j);
}

// Tratto sottile antialiasato attorno a una distanza con segno: la
// larghezza e' in spazio schermo tramite fwidth(), quindi la linea non
// sparisce mai sotto il pixel. L'antialiasing e' pero' limitato a non piu'
// di ~1.4 mezze-larghezze: senza questo tetto, appena un'ottava della
// griglia di tracciati si avvicina alla soglia in cui sparisce (vedi
// shadeLayer), fwidth() cresce piu' in fretta della larghezza del tratto e
// la linea si scioglie in una sfumatura lattiginosa invece di restare
// netta finche' non e' il momento di sparire del tutto.
float stroke(float d, float halfWidth) {
  float aa = min(fwidth(d), halfWidth * 1.4) + 1e-5;
  return clamp(1.0 - (abs(d) - halfWidth) / aa, 0.0, 1.0);
}
// Rotazione di un punto attorno all'origine -- usata dal mirino del
// nucleo, non dalle schede (quelle usano layerRot, fissa nel tempo).
vec2 rotate(vec2 p, float a) {
  float c = cos(a), s = sin(a);
  return vec2(p.x * c - p.y * s, p.x * s + p.y * c);
}
// Distanza (Chebyshev) dal perimetro di un rombo di "raggio" r: zero sul
// bordo, negativa dentro. Un quadrato ruotato di 45 gradi -- serve a
// marcare il nucleo con una geometria netta invece di un alone morbido.
float sdDiamond(vec2 p, float r) {
  return abs(p.x) + abs(p.y) - r;
}

// Numero di celle per lato di un "blocco" del floorplan: piu' celle
// condividono lo stesso tipo di trama, cosi' il disegno si legge come un
// die di silicio -- zone rettangolari di macro-blocchi diversi (reticolo,
// via, bande di memoria, pad di bonding) -- e non come rumore cella per
// cella.
const float BLOCK_CELLS = 5.0;

// Una cella del floorplan: la sua trama dipende dal *blocco* a cui
// appartiene (un gruppo di BLOCK_CELLS x BLOCK_CELLS celle, con lo stesso
// tipo), la variazione minuta (quale cella e' vuota, dove cade un pad)
// dalla cella stessa. Ritorna (copertura 0..1 gia' antialiasata, indice di
// colore: 0 oro, 1 argento).
vec2 dieCell(vec2 lp, vec2 id, float seed) {
  vec2 blockId = floor(id / BLOCK_CELLS);
  float rt = hash11(blockId.x * 41.9 + blockId.y * 19.7 + seed);
  float cellR = hash11(id.x * 57.13 + id.y * 131.71 + seed + 91.0);

  if (rt < 0.24) {
    // Reticolo: un fondo dorato diffuso diviso da un fitto disegno di
    // celle -- la trama piu' presente della foto di riferimento.
    float borderDist = min(abs(abs(lp.x) - 0.5), abs(abs(lp.y) - 0.5));
    return vec2(stroke(borderDist, 0.05) * 0.75 + 0.22, 0.0);
  } else if (rt < 0.46) {
    // Via: pad pieni e fitti, come l'array di piccoli quadrati dorati.
    float cov = cellR < 0.78 ? stroke(length(lp), 0.16) : 0.0;
    return vec2(cov, 0.0);
  } else if (rt < 0.64) {
    // Bande verticali: la trama a righe di un array di memoria, in
    // argento anziche' oro per staccare dal resto.
    float cov = cellR < 0.88 ? stroke(lp.x, 0.11) : 0.0;
    return vec2(cov, 1.0);
  } else if (rt < 0.82) {
    float cov = cellR < 0.88 ? stroke(lp.y, 0.11) : 0.0;
    return vec2(cov, 1.0);
  } else if (rt < 0.93) {
    // Quiete: quasi vuota, un accenno ogni tanto -- senza un minimo di
    // respiro tutta la scheda si legge come rumore, non come circuito.
    float cov = cellR < 0.10 ? stroke(length(lp), 0.10) : 0.0;
    return vec2(cov, 0.0);
  } else {
    // Pad di bonding: anelli radi e piu' grandi, come i punti di
    // saldatura in fila sul bordo della foto.
    float cov = cellR < 0.32 ? stroke(length(lp) - 0.30, 0.05) : 0.0;
    return vec2(cov, 1.0);
  }
}

// Peso di ciascun livello nella dissolvenza incrociata. Il livello uscente
// e quello entrante arrivano a zero esattamente sui bordi della
// transizione, quindi lo scambio di indice non si vede mai.
float layerWeight(int k, float frac) {
  // Uscente: ormai grandissimo, si spegne solo lungo l'ultimo gradino.
  if (k == K_MIN) return 1.0 - smoothstep(0.0, 1.0, frac);
  // Entrante: arriva dal fondo, ancora minuscolo.
  if (k == K_MIN + NUM_LAYERS - 1) return smoothstep(0.0, 1.0, frac);
  // I due centrali restano sempre a piena intensita'.
  return 1.0;
}

// Una singola griglia di tracciati a passo fisso, per quanto fitta, non ha
// il dettaglio infinito di un frattale: zoomando dentro un solo livello si
// finirebbe presto a vedere solo il bordo enorme e sfocato di una cella
// sola. La soluzione e' la stessa gia' usata per le curve di livello --
// piu' ottave della stessa griglia a passi via via piu' fini, ciascuna
// sfumata via con fwidth() quando il suo passo diventa sub-pixel -- cosi'
// c'e' sempre un'ottava a fuoco a qualunque profondita' di zoom, non solo
// al cambio di livello. E' questo, non il pattern in se', a rendere la
// "discesa" ancora infinita.
const int OCTAVES = 4;
const float OCTAVE_RATIO = 3.1; // non una potenza di 2: evita l'allineamento (moire) fra ottave

vec3 shadeLayer(vec2 p, float L, int depth) {
  float seed = layerSeed(L);
  // Deriva lentissima della rotazione: la scheda e' viva anche da ferma,
  // ma di un soffio -- non deve mai leggersi come un disorientamento.
  float a = layerRot(L) + uBreath * 0.01;
  float ca = cos(a), sa = sin(a);
  vec2 rp = vec2(p.x * ca - p.y * sa, p.x * sa + p.y * ca);

  // Palette da die di silicio al microscopio: oro/rame caldo per reticolo
  // e via, argento freddo per le bande di memoria. Un lieve scarto per
  // livello (via layerHue) li tiene comunque distinguibili fra loro, senza
  // uscire mai dalla famiglia oro/argento.
  float warmth = layerHue(L) / 6.2832;
  vec3 gold = mix(vec3(1.0, 0.72, 0.30), vec3(1.0, 0.56, 0.22), warmth * 0.5);
  vec3 silver = mix(vec3(0.80, 0.83, 0.88), vec3(0.70, 0.78, 0.82), warmth);

  // Sotto un certo budget di dettaglio (dispositivo sotto carico) si
  // rinuncia alle ottave piu' fini prima: sono anche le piu' costose,
  // dato che coprono piu' celle per pixel.
  int octaves = uMaxIter > 105 ? OCTAVES : uMaxIter > 80 ? 3 : 2;

  float pitch = layerPitch(L);
  vec3 color = vec3(0.0);
  for (int o = 0; o < OCTAVES; o++) {
    if (o >= octaves) break;
    // Scostamento diverso per ogni ottava, cosi' non condividono la stessa
    // fase e non si sovrappongono sempre negli stessi punti.
    vec2 gp = rp + vec2(0.61, 0.27) * float(o) * 0.41;
    vec2 q = gp / pitch;

    // Quando una cella copre meno di un pixel la griglia non e' piu'
    // risolvibile: sparisce dolcemente invece di aliasare a scatti.
    float cellsPerPixel = fwidth(q.x) + fwidth(q.y);
    float fadeOut = clamp(1.0 - cellsPerPixel * 0.9, 0.0, 1.0);
    // ...ma un'ottava riempita (reticolo, via, pad) sparisce anche
    // nell'altro senso: zoomando *dentro* una sola sua cella, senza che
    // nessun bordo sia piu' in vista, altrimenti resterebbe una singola
    // campitura piena a schermo intero -- che il tonemap scioglie in una
    // macchia chiara. Il testimone passa all'ottava piu' fine, che a
    // quella profondita' mostra ancora molte celle.
    float fadeIn = smoothstep(0.0, 0.05, cellsPerPixel);
    float fade = fadeOut * fadeIn;
    if (fade > 0.004) {
      vec2 id = floor(q);
      vec2 lp = q - id - 0.5;
      vec2 res = dieCell(lp, id, seed + float(o) * 733.1);

      // Leggero sfarfallio per cella, non un impulso che viaggia: i pad e
      // le vie di un die non "scorrono" come un segnale, respirano appena.
      float phase = hash11(id.x * 13.1 + id.y * 7.7 + seed + float(o) * 733.1 + 53.0) * 6.2832;
      float pulse = 0.85 + 0.15 * sin(uTime * (0.5 + float(o) * 0.08) + phase);

      vec3 baseColor = res.y < 0.5 ? gold : silver;
      // Le ottave piu' fini pesano meno: sono la trama di dettaglio, non
      // il segno principale.
      float octaveFall = 1.0 / (1.0 + float(o) * 0.6);
      color += baseColor * res.x * fade * pulse * LINE_GAIN * octaveFall;
    }

    pitch /= OCTAVE_RATIO;
  }

  // I livelli piu' profondi (ancora "sotto") leggono un po' piu' tenui,
  // cosi' la pila si percepisce come sovrapposizione e non come un unico
  // disegno appiattito. 'depth' e' la posizione nella finestra, 0 = il
  // piu' vicino.
  return color * (1.0 - float(depth) * 0.11);
}

void main() {
  vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution) / uResolution.y * 2.0;

  vec3 color = vec3(0.0);
  for (int i = 0; i < NUM_LAYERS; i++) {
    int k = K_MIN + i;
    float w = layerWeight(k, uFrac);
    if (w <= 0.002) continue;
    float L = uLayerBase + float(k);
    vec2 p = mirrorFold(uCenter + layerOffset(L) + uv * pow(SCALE, float(k) - uFrac));
    // A shadeLayer serve la posizione NELLA finestra (0 = il piu' vicino),
    // non k, che ora puo' essere negativo.
    color += shadeLayer(p, L, i) * w;
  }

  // --- Nucleo ----------------------------------------------------------
  // Si disegna solo quando si e' gia' vicini: la ricerca la guida
  // l'orecchio, l'occhio arriva solo a confermare. Un mirino a tratto
  // sottile, nello stesso linguaggio geometrico dei tracciati del resto
  // della scheda -- non un alone morbido, che si perdeva nel fondo e non
  // leggeva ne' come "vicino" ne' come "qualcosa". Ciano elettrico, non
  // ambra: sul die di silicio, tutto oro e argento caldi, e' l'unico modo
  // per non confondersi con la trama circostante.
  if (uNucleusGlow > 0.002 || uNucleusSolved > 0.5) {
    vec2 rel = uv - uNucleusUv;
    float r = length(rel);
    vec3 tone = vec3(0.25, 0.85, 1.0);

    // Rombo che ruota lentamente e si stringe avvicinandosi, da meta'
    // schermo a un punto: la lettura visiva dello stesso avvicinamento che
    // il battito dice a orecchio. Il tratto e' netto (stroke, non exp),
    // quindi resta leggibile anche in movimento invece di sfumare via.
    float ringR = mix(0.55, 0.07, uNucleusGlow);
    vec2 rp = rotate(rel, uTime * 0.35);
    float ring = stroke(sdDiamond(rp, ringR), 0.006);
    color += tone * ring * uNucleusGlow * 1.8;

    // Mirino: due tacche per asse invece di una croce piena -- il vuoto al
    // centro e' cio' che lo fa leggere come reticolo geometrico e non come
    // un semplice "+"decorativo.
    float reach = ringR * 0.95;
    float gap = reach * 0.4;
    float onH = stroke(rel.y, 0.0035) * step(abs(rel.x), reach) * (1.0 - step(abs(rel.x), gap));
    float onV = stroke(rel.x, 0.0035) * step(abs(rel.y), reach) * (1.0 - step(abs(rel.y), gap));
    color += tone * (onH + onV) * uNucleusGlow * 1.4;

    // Centro: un punto piccolo e netto quando si e' vicinissimi, fisso una
    // volta risolto -- mai una macchia, solo un segno.
    float core = smoothstep(0.05, 0.0, r);
    color += tone * core * (uNucleusGlow * uNucleusGlow * 1.1 + uNucleusSolved * 1.6);

    // Fioritura alla risoluzione: lo stesso rombo che si allarga una sola
    // volta e svanisce, invece dell'anello morbido di prima.
    float bloomR = 0.05 + (1.0 - uNucleusBloom) * 0.85;
    float bloom = stroke(sdDiamond(rp, bloomR), 0.008);
    color += tone * bloom * uNucleusBloom * 2.2;
  }

  color *= EXPOSURE;

  // Vignettatura, coerente con quella della schermata iniziale.
  float vig = smoothstep(1.5, 0.25, length(uv));
  color *= mix(0.72, 1.0, vig);

  color = color / (1.0 + color);
  color = pow(color, vec3(1.0 / 2.2));

  // La saturazione va applicata QUI, dopo il tonemap, non prima. Il
  // tonemap di Reinhard comprime ogni canale verso 1: piu' si alza
  // l'esposizione, piu' i tre canali si avvicinano fra loro e il colore
  // sbianca. Saturare a monte verrebbe quindi in gran parte annullato
  // proprio dove il tratto e' piu' luminoso, cioe' dove il colore conta.
  // In spazio display invece la tinta si recupera senza rinunciare alla
  // luminosita'.
  float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
  color = clamp(mix(vec3(luma), color, SATURATION), 0.0, 1.0);

  fragColor = vec4(color, 1.0);
}
`;
