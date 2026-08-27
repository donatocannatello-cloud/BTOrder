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
uniform vec3 uCamPos;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform vec3 uCamForward;
uniform float uFov;        // vertical fov, radians
uniform float uTime;
uniform float uBreath;     // small "world breathes" power oscillation, added to every layer
uniform int uMaxIter;      // iteration budget: fewer far away, more up close (LOD)
uniform int uRaySteps;     // sphere-tracing step budget, tuned live by the quality manager
uniform float uDepthLayerBase; // floor(log(RADIUS_MAX/radius)/log(SCALE)): how deep the camera currently is
uniform float uFlash;      // 0..1, subtle decaying pulse right when a new layer takes over

out vec4 fragColor;

const float MAX_DIST = 45.0;
const float SURF_EPS = 0.0009;
const float PROX_RADIUS = 2.2; // world units: how far the "presence" reaction reaches

// Infinite descent: instead of one fractal, the scene is the union of a
// small, fixed *window* of NUM_LAYERS nested copies -- the current one the
// camera sits in, plus the next ones already visible growing inside it.
// Each copy k lives SCALE^k times smaller (standard SDF domain-scaling:
// scale the sample point, divide the resulting distance by the same
// factor), so as the camera's radius keeps shrinking, copy k=1 gradually
// takes over as the nearest surface -- continuously, no cut or reset.
// Layers already crossed, and anything beyond this window, are never
// evaluated at all: cost stays flat regardless of how deep you go.
const float SCALE = 2.2;
const int NUM_LAYERS = 3;

mat3 rotY(float a) {
  float s = sin(a), c = cos(a);
  return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
}
mat3 rotX(float a) {
  float s = sin(a), c = cos(a);
  return mat3(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c);
}

float hash11(float p) {
  p = fract(p * 0.1031);
  p *= p + 33.33;
  p *= p + p;
  return fract(p);
}

// Layer 0 (the "home" fractal, before the first descent) keeps its
// original look exactly; variety only kicks in from layer 1 onward, so the
// starting point never changes just because the descent system exists.
float layerPower(float absLayer) {
  return absLayer < 0.5 ? 8.0 : 6.0 + hash11(absLayer * 12.9898 + 3.1) * 5.0;
}
float layerHue(float absLayer) {
  return absLayer < 0.5 ? 0.0 : hash11(absLayer * 7.31 + 9.7) * 6.2832;
}
float layerRotSpeed(float absLayer) {
  return absLayer < 0.5 ? 1.0 : 0.5 + hash11(absLayer * 5.13 + 1.7) * 1.5;
}
float layerRotSign(float absLayer) {
  return absLayer < 0.5 ? 1.0 : (hash11(absLayer * 5.13 + 8.8) > 0.5 ? 1.0 : -1.0);
}

// Mandelbulb distance estimator.
// Returns distance estimate; also writes orbit trap info into 'trap' for coloring.
float deMandelbulb(vec3 pos, float power, out vec4 trap) {
  vec3 z = pos;
  float dr = 1.0;
  float r = 0.0;
  trap = vec4(abs(z), dot(z, z));

  for (int i = 0; i < uMaxIter; i++) {
    r = length(z);
    if (r > 2.5) break;

    // polar decomposition
    float theta = acos(clamp(z.z / max(r, 1e-6), -1.0, 1.0));
    float phi = atan(z.y, z.x);
    dr = pow(r, power - 1.0) * power * dr + 1.0;

    float zr = pow(r, power);
    theta *= power;
    phi *= power;

    z = zr * vec3(sin(theta) * cos(phi), sin(theta) * sin(phi), cos(theta));
    z += pos;

    trap = min(trap, vec4(abs(z), dot(z, z)));
  }
  return 0.5 * log(max(r, 1e-6)) * r / dr;
}

// Union of the NUM_LAYERS nested copies currently in the descent window.
// 'wonLayer' reports which one (0..NUM_LAYERS-1, relative to uDepthLayerBase)
// produced the winning (closest) distance, for shading.
float sceneDE(vec3 p, out vec4 trap, out float wonLayer) {
  float distToCam = length(p - uCamPos);
  float proximity = 1.0 - smoothstep(0.0, PROX_RADIUS, distToCam);

  float best = MAX_DIST;
  vec4 bestTrap = vec4(0.0);
  float bestK = 0.0;
  // s deve partire ancorato alla profondita' assoluta gia' raggiunta, non
  // da 1.0: la copia k=0 ("livello corrente") deve gia' essere rimpicciolita
  // di SCALE^uDepthLayerBase, altrimenti resta sempre alla dimensione
  // originale mentre la camera (il cui raggio si riduce con la stessa legge)
  // le sfila via sotto, e oltre una certa profondita' non trova piu' nulla.
  float s = pow(SCALE, uDepthLayerBase);

  for (int k = 0; k < NUM_LAYERS; k++) {
    float absLayer = uDepthLayerBase + float(k);
    // The fractal slowly rotates in place over time (the world "breathes"
    // even without input), independently per layer, and the exponent gets
    // a small extra wobble near wherever the camera currently is, as a
    // gentle "presence" reaction.
    float power = layerPower(absLayer) + uBreath + proximity * 0.45 * sin(uTime * 1.3 + dot(p, p) * 2.0);
    float rs = layerRotSpeed(absLayer);
    float rsign = layerRotSign(absLayer);

    vec3 lp = p * s;
    vec3 rp = rotY(uTime * 0.015 * rs * rsign) * rotX(uTime * 0.006 * rs) * lp;
    vec4 tr;
    float d = deMandelbulb(rp, power, tr) / s;
    if (d < best) {
      best = d;
      bestTrap = tr;
      bestK = float(k);
    }
    s *= SCALE;
  }

  trap = bestTrap;
  wonLayer = bestK;
  return best;
}

float sceneDist(vec3 p) {
  vec4 t;
  float w;
  return sceneDE(p, t, w);
}

vec3 estimateNormal(vec3 p) {
  float e = SURF_EPS * 3.0;
  vec2 h = vec2(e, 0.0);
  return normalize(vec3(
    sceneDist(p + h.xyy) - sceneDist(p - h.xyy),
    sceneDist(p + h.yxy) - sceneDist(p - h.yxy),
    sceneDist(p + h.yyx) - sceneDist(p - h.yyx)
  ));
}

void main() {
  vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution) / uResolution.y;

  float focal = 1.0 / tan(uFov * 0.5);
  vec3 rd = normalize(uv.x * uCamRight + uv.y * uCamUp + focal * uCamForward);
  vec3 ro = uCamPos;

  float t = 0.0;
  float steps = 0.0;
  vec4 trap = vec4(0.0);
  float hitLayerK = 0.0;
  bool hit = false;

  for (int i = 0; i < uRaySteps; i++) {
    vec3 p = ro + rd * t;
    vec4 tr;
    float wk;
    float d = sceneDE(p, tr, wk);
    steps += 1.0;
    if (d < SURF_EPS * max(1.0, t)) {
      hit = true;
      trap = tr;
      hitLayerK = wk;
      break;
    }
    t += d * 0.85;
    if (t > MAX_DIST) break;
  }

  // Deep-space background: soft vertical gradient, no stars (keep it calm / no HUD-like clutter).
  vec3 bg = mix(vec3(0.01, 0.012, 0.02), vec3(0.05, 0.04, 0.07), 0.5 + 0.5 * uv.y);

  vec3 color;
  if (hit) {
    vec3 p = ro + rd * t;
    vec3 n = estimateNormal(p);
    float ao = 1.0 - steps / float(uRaySteps);

    // Slow overall hue drift so the mood shifts over long timescales even
    // without the player doing anything, offset per descent-layer so each
    // newly-entered fractal has a visibly different palette.
    float hue = layerHue(uDepthLayerBase + hitLayerK);
    vec3 mood = 0.5 + 0.5 * cos(uTime * 0.025 + hue + vec3(0.0, 2.0, 4.0));
    vec3 lineColor = mix(vec3(0.5, 0.75, 1.0), vec3(0.85, 0.55, 1.0), clamp(trap.w, 0.0, 1.0));
    lineColor = mix(lineColor, lineColor * mood * 1.3, 0.3);

    // Multi-octave triplanar contour lines carved directly into world
    // position: smooth and continuous (unlike the orbit trap, which
    // oscillates chaotically across the fractal's fine bumps and reads as
    // noise), so it engraves clean, topographic-map-like rings onto the
    // bumpy surface. Each octave uses fwidth()-based screen-space
    // antialiasing that fades it out once its spacing would alias
    // (sub-pixel) -- so finer lines only resolve once the camera is close
    // enough to actually see them, which is the "detail grows as you
    // approach" behaviour applied to the linework itself, not just to the
    // underlying geometry's iteration budget.
    vec3 aw = abs(n) / (abs(n.x) + abs(n.y) + abs(n.z) + 1e-5);
    float lines = 0.0;
    float freq = 2.2;
    for (int o = 0; o < 4; o++) {
      vec3 v = p * freq;
      vec3 w = fwidth(v) + 1e-4;
      vec3 octaveFade = clamp(1.0 - w * 1.4, 0.0, 1.0);
      vec3 g = abs(fract(v - 0.5) - 0.5) / w;
      vec3 axisLine = (1.0 - clamp(g, 0.0, 1.0)) * octaveFade;
      float lx = max(axisLine.y, axisLine.z);
      float ly = max(axisLine.x, axisLine.z);
      float lz = max(axisLine.x, axisLine.y);
      float l = lx * aw.x + ly * aw.y + lz * aw.z;
      lines = max(lines, l * (1.0 - float(o) * 0.12));
      freq *= 2.3;
    }

    // Hard-edged silhouette line instead of a soft rim, plus a very dim
    // ambient fill so the shape still reads as solid, not just scattered
    // lines floating in space.
    float ndotv = max(dot(n, -rd), 0.0);
    float rimLine = smoothstep(0.5, 0.85, pow(1.0 - ndotv, 2.2));
    vec3 dimFill = lineColor * 0.05 * (0.5 + 0.5 * ao);

    // Presence glow: surfaces close to the camera light up a little, echoing
    // the local exponent wobble from sceneDE().
    float proximity = 1.0 - smoothstep(0.0, PROX_RADIUS, t);
    vec3 glow = lineColor * (lines * 1.4 + rimLine * 0.8);
    glow += proximity * lineColor * 0.5;

    color = dimFill + glow;

    // Affondamento: quando l'hit e' fortissimamente ravvicinato (si e'
    // immersi dentro la nube del frattale, circondati da geometria, non
    // solo vicini a una parete) il colore vira verso un violaceo profondo
    // e piu' scuro, cosi' "essere dentro" si sente visivamente diverso da
    // "essere vicino a una superficie".
    float enclosure = 1.0 - smoothstep(0.0, 0.5, t);
    color = mix(color, color * vec3(0.45, 0.35, 0.65) * 0.45, enclosure * 0.8);
  } else {
    color = bg;
  }

  // Distance fog + desaturation for a sense of infinite scale.
  float fogT = clamp(t / MAX_DIST, 0.0, 1.0);
  float fog = 1.0 - exp(-3.2 * fogT);
  float luma = dot(color, vec3(0.299, 0.587, 0.114));
  color = mix(color, vec3(luma), fog * 0.65);
  color = mix(color, bg, fog);

  // Gentle vignette, filmic-ish tonemap.
  vec2 vc = uv * (1.0 / max(uFov, 0.5));
  float vig = smoothstep(1.4, 0.2, length(vc));
  color *= mix(0.75, 1.0, vig);

  // Impulso al passaggio di livello: un breve chiarore.
  color += uFlash * vec3(0.85, 0.8, 1.0) * 0.6;

  color = color / (1.0 + color);
  color = pow(color, vec3(1.0 / 2.2));

  fragColor = vec4(color, 1.0);
}
`;
