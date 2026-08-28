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
// Il livello k=0 e' quello "a fuoco", k=1 e k=2 sono le mappe piu' fini che
// si stanno gia' intravedendo sotto. Scendendo, uFrac cresce: il livello 0
// si ingrandisce fino a sparire, il livello 1 prende il suo posto e un
// livello 3 nuovo entra dal fondo. Poiche' SCALE^(1-1) == SCALE^0, il
// fattore di scala del livello entrante coincide esattamente con quello del
// livello uscente nel momento dello scambio: l'indice puo' avanzare (o
// arretrare) all'infinito senza nessuno scatto e senza ri-ancorare il
// centro. Solo 3 livelli vengono valutati per pixel: il costo resta piatto
// a qualunque profondita'.
const float SCALE = 2.2;
const int NUM_LAYERS = 3;

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
  if (k == 0) return 1.0 - smoothstep(0.5, 1.0, frac);
  if (k == 1) return 1.0;
  return smoothstep(0.0, 0.5, frac);
}

vec3 shadeLayer(vec2 p, float L, int k) {
  bool inside;
  float field = layerField(p, L, inside);

  float hue = layerHue(L);
  vec3 tint = 0.5 + 0.5 * cos(uTime * 0.02 + hue + vec3(0.0, 2.1, 4.2));
  vec3 lineColor = mix(vec3(0.62, 0.76, 1.0), vec3(0.86, 0.58, 1.0), tint.x);
  lineColor = mix(lineColor, lineColor * tint * 1.25, 0.35);

  // Interno dell'insieme: campitura appena percettibile, come la terraferma
  // su una carta. Volutamente bassissima -- la correzione gamma finale
  // amplifica molto anche valori lineari piccoli.
  if (inside) return lineColor * 0.03;

  // Curve di livello multi-ottava: la trama topografica della mappa.
  float lines = 0.0;
  float freq = 0.9;
  for (int o = 0; o < 5; o++) {
    lines = max(lines, contour(field, freq) * (1.0 - float(o) * 0.13));
    freq *= 2.0;
  }

  // Costa: dove la fuga e' lenta si e' vicini al bordo dell'insieme. E'
  // li' che vive tutto il dettaglio frattale, quindi e' il tratto piu'
  // marcato della carta -- e le curve di livello si infittiscono verso
  // di esso invece di restare uniformi anche nelle zone piatte al largo.
  float nearSet = smoothstep(0.06, 0.55, field / float(uMaxIter));
  float shore = smoothstep(0.55, 0.95, field / float(uMaxIter));

  vec3 color = lineColor * (lines * (0.45 + nearSet * 0.95) + shore * 0.7);

  // I livelli piu' profondi (ancora "sotto") leggono un po' piu' tenui,
  // cosi' la pila si percepisce come sovrapposizione e non come un unico
  // disegno appiattito.
  return color * (1.0 - float(k) * 0.16);
}

void main() {
  vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution) / uResolution.y * 2.0;

  vec3 color = vec3(0.0);
  for (int k = 0; k < NUM_LAYERS; k++) {
    float w = layerWeight(k, uFrac);
    if (w <= 0.002) continue;
    float L = uLayerBase + float(k);
    vec2 p = uCenter + layerOffset(L) + uv * pow(SCALE, float(k) - uFrac);
    color += shadeLayer(p, L, k) * w;
  }

  // Vignettatura, coerente con quella della schermata iniziale.
  float vig = smoothstep(1.5, 0.25, length(uv));
  color *= mix(0.72, 1.0, vig);

  color = color / (1.0 + color);
  color = pow(color, vec3(1.0 / 2.2));

  fragColor = vec4(color, 1.0);
}
`;
