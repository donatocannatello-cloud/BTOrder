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
uniform int uMaxIter;      // budget di iterazioni, regolato dal quality manager
uniform float uTime;
uniform float uBreath;     // lenta oscillazione: la mappa "respira" anche da ferma

out vec4 fragColor;

// Mappe piane sovrapposte, non mondi sferici concentrici. Ogni livello e'
// una mappa frattale piatta (insieme di Julia, campo escape-time) disegnata
// a curve di livello, come una carta topografica. I livelli condividono un
// unico sistema di coordinate 2D ma sono campionati a scale diverse:
//
//   p_k = uCenter + offset(L) + uv * SCALE^(k - uFrac)
//
// La finestra va da k = K_MIN (-1) a k = K_MIN + NUM_LAYERS - 1 (+2):
// il livello a -1 e' quello che si sta gia' superando, ingrandito e in
// dissolvenza; 0 e 1 sono quelli a fuoco; +2 e' la mappa fine che si
// intravede appena dal fondo. Scendendo, uFrac cresce e ogni livello
// scala di un gradino. Poiche' SCALE^(k-1) valutato a frac=1 coincide
// esattamente con SCALE^(k-1) valutato a frac=0 dopo lo scambio, l'indice
// puo' avanzare (o arretrare) all'infinito senza nessuno scatto e senza
// ri-ancorare il centro. Solo NUM_LAYERS livelli vengono valutati per
// pixel: il costo resta piatto a qualunque profondita'.
const float SCALE = 2.2;
const int NUM_LAYERS = 5;

// La finestra e' spostata tutta verso il *vicino*: k va da -3 a +1.
//
// Non basta tenere acceso piu' a lungo il livello che si sta superando:
// conta quali livelli portano il peso. Con la finestra a [-1,+2] i due a
// piena intensita' restavano 0 e 1, la cui scala oscilla fra 0.45 e 2.2 --
// il frattale dominante non diventava mai grande, e quello a -1, pur
// arrivando a 4.8x, era in dissolvenza e contribuiva poco. Con [-3,+1] i
// livelli a piena intensita' sono -2, -1 e 0, e l'uscente arriva a
// SCALE^4 = 23x prima di spegnersi: quasi cinque volte l'ingrandimento
// di prima, a parita' di costo grazie all'hatch piu' rado.
const int K_MIN = -3;

// Resa del tratto. Il disegno e' interamente auto-illuminato: non c'e'
// nessuna luce nella scena, il colore *e'* l'emissione. Quindi "quanto
// brilla" non e' un termine di illuminazione da aggiungere, e'
// semplicemente l'esposizione applicata prima del tonemap.
const float EXPOSURE = 1.15;
const float LINE_GAIN = 2.6;    // quanto marcato e' il tratto
const float SATURATION = 1.5;   // applicata DOPO il tonemap, vedi main()
const float WASH = 0.05;        // alone di costa: solo un accenno, vedi shadeLayer
const float FILL = 0.03;        // campitura dell'interno dell'insieme

// Il mondo non ha bordi: oltre il riquadro fondamentale [-H, H] la mappa
// prosegue *riflessa*, all'infinito, in tutte le direzioni. Fuori dal suo
// raggio di interesse un insieme di Julia degenera in vuoto uniforme,
// quindi scorrere davvero via darebbe deserto; un wrap col modulo darebbe
// invece una cucitura netta ad ogni giro. Il ripiegamento a specchio e' la
// terza via: e' continuo (nessun salto di valore sul bordo), quindi il
// disegno prosegue senza strappi, come in una sala degli specchi.
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

// Parametro dell'insieme di Julia del livello: preso su una corona di
// raggio ~0.6-0.8, dove gli insiemi sono ancora connessi ma gia' molto
// frastagliati -- ogni livello e' quindi una mappa visibilmente diversa.
// Il livello 0 e' fissato, cosi' il punto di partenza non cambia mai.
vec2 layerJuliaC(float L) {
  if (abs(L) < 0.5) return vec2(-0.7269, 0.1889);
  float a = hash11(L * 12.9898 + 3.1) * 6.2832;
  float r = 0.60 + hash11(L * 7.31 + 9.7) * 0.19;
  return vec2(cos(a), sin(a)) * r;
}

float layerHue(float L) {
  return abs(L) < 0.5 ? 0.0 : hash11(L * 5.13 + 1.7) * 6.2832;
}

// Rotazione fissa (non nel tempo: una mappa che ruota disorienta) e
// scostamento del centro, cosi' i livelli non risultano tutti allineati.
float layerRot(float L) {
  return abs(L) < 0.5 ? 0.0 : hash11(L * 3.77 + 5.9) * 6.2832;
}
vec2 layerOffset(float L) {
  if (abs(L) < 0.5) return vec2(0.0);
  return (vec2(hash11(L * 9.41 + 2.3), hash11(L * 6.17 + 8.5)) - 0.5) * 0.5;
}

// Campo escape-time dell'insieme di Julia del livello: conteggio di fuga
// "smooth" (continuo, non a gradini), che e' cio' che rende possibile
// tracciarci sopra curve di livello pulite. 'inside' segnala i punti che
// non fuggono mai (l'interno dell'insieme).
float layerField(vec2 p, float L, out bool inside) {
  vec2 jc = layerJuliaC(L);
  // Respiro lento: il parametro deriva appena, cosi' la mappa e' viva
  // anche stando fermi, senza mai stravolgersi.
  jc += vec2(cos(uTime * 0.05 + L), sin(uTime * 0.041 + L)) * (0.005 + uBreath * 0.003);

  float a = layerRot(L);
  float ca = cos(a), sa = sin(a);
  vec2 z = vec2(p.x * ca - p.y * sa, p.x * sa + p.y * ca);

  float n = 0.0;
  for (int i = 0; i < uMaxIter; i++) {
    z = vec2(z.x * z.x - z.y * z.y, 2.0 * z.x * z.y) + jc;
    float r2 = dot(z, z);
    if (r2 > 256.0) {
      inside = false;
      return n - log2(max(0.5 * log2(r2), 1e-6));
    }
    n += 1.0;
  }
  inside = true;
  return n;
}

// Una curva di livello del campo, antialiasata in spazio schermo con
// fwidth() e sfumata via automaticamente quando il suo passo diventa
// sub-pixel: le linee piu' fini si materializzano solo quando la scala e'
// abbastanza grande da poterle davvero risolvere, che e' esattamente il
// comportamento "il dettaglio aumenta scendendo" applicato al tratto.
float contour(float field, float freq) {
  float v = field * freq;
  float w = fwidth(v) + 1e-5;
  float fade = clamp(1.0 - w * 1.1, 0.0, 1.0);
  float g = abs(fract(v - 0.5) - 0.5) / w;
  return (1.0 - clamp(g, 0.0, 1.0)) * fade;
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

vec3 shadeLayer(vec2 p, float L, int depth) {
  bool inside;
  float field = layerField(p, L, inside);

  float hue = layerHue(L);
  vec3 tint = 0.5 + 0.5 * cos(uTime * 0.02 + hue + vec3(0.0, 2.1, 4.2));
  vec3 lineColor = mix(vec3(0.40, 0.66, 1.0), vec3(0.82, 0.40, 1.0), tint.x);
  lineColor = mix(lineColor, lineColor * tint * 1.25, 0.35);

  // Interno dell'insieme: campitura appena percettibile, come la terraferma
  // su una carta. Volutamente bassissima -- la correzione gamma finale
  // amplifica molto anche valori lineari piccoli.
  if (inside) return lineColor * FILL;

  // Curve di livello multi-ottava: la trama topografica della mappa.
  float lines = 0.0;
  float freq = 0.9;
  for (int o = 0; o < 3; o++) {
    lines = max(lines, contour(field, freq) * (1.0 - float(o) * 0.10));
    freq *= 2.0;
  }

  // Costa: dove la fuga e' lenta si e' vicini al bordo dell'insieme. E'
  // li' che vive tutto il dettaglio frattale, quindi il tratto si
  // infittisce verso di esso invece di restare uniforme anche nelle zone
  // piatte al largo.
  float nearSet = smoothstep(0.06, 0.55, field / float(uMaxIter));
  float shore = smoothstep(0.55, 0.95, field / float(uMaxIter));

  // Il tratto e' l'unica cosa che deve saltare all'occhio, quindi prende
  // tutto il guadagno...
  vec3 color = lineColor * lines * (0.7 + nearSet * 1.3) * LINE_GAIN;

  // ...mentre l'alone di costa resta un accenno. E' la parte piatta del
  // disegno: alzarla non rende il wireframe piu' visibile, lo rende meno,
  // perche' schiarisce il fondo *fra* le linee e ne divora il contrasto.
  // La correzione gamma finale amplifica molto i valori bassi (0.05
  // lineare diventa gia' ~0.25 a schermo), quindi qui basta pochissimo.
  color += lineColor * shore * WASH;

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
