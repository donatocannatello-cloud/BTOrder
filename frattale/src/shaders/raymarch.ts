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

// Infinite descent: instead of one fractal, the scene is a small, fixed
// *window* of NUM_LAYERS nested copies -- the current one the camera sits
// in, plus the next ones already visible growing inside it -- rendered as
// translucent wireframe shells (see shadeSurface/alpha below) so each is
// seen through the one before it, rather than occluding it outright.
// Each copy k lives SCALE^k times smaller (standard SDF domain-scaling:
// scale the sample point, divide the resulting distance by the same
// factor), so as the camera's radius keeps shrinking, copy k=1 gradually
// takes over as the nearest surface -- continuously, no cut or reset.
// Layers already crossed, and anything beyond this window, are never
// evaluated at all: cost stays flat regardless of how deep you go. One
// extra copy, one level *up* (absLayer = uDepthLayerBase - 1), is always
// rendered too: the fractal that currently contains the camera, shaded
// two-sided as a backdrop behind the foreground window.
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

// Distance estimate for a single nested copy at absolute depth 'absLayer',
// independent of the others -- the shared building block for both the
// foreground window (sceneDE below) and the standalone container layer.
float deLayerAt(vec3 p, float absLayer, out vec4 trap) {
  float distToCam = length(p - uCamPos);
  float proximity = 1.0 - smoothstep(0.0, PROX_RADIUS, distToCam);

  // The fractal slowly rotates in place over time (the world "breathes"
  // even without input), independently per layer, and the exponent gets a
  // small extra wobble near wherever the camera currently is, as a gentle
  // "presence" reaction.
  float power = layerPower(absLayer) + uBreath + proximity * 0.45 * sin(uTime * 1.3 + dot(p, p) * 2.0);
  float rs = layerRotSpeed(absLayer);
  float rsign = layerRotSign(absLayer);

  // s deve partire ancorato alla profondita' assoluta 'absLayer', non da
  // 1.0: altrimenti la copia resta sempre alla dimensione originale mentre
  // la camera (il cui raggio si riduce con la stessa legge) le sfila via
  // sotto, e oltre una certa profondita' non trova piu' nulla.
  float s = pow(SCALE, absLayer);
  vec3 lp = p * s;
  vec3 rp = rotY(uTime * 0.015 * rs * rsign) * rotX(uTime * 0.006 * rs) * lp;
  vec4 tr;
  float d = deMandelbulb(rp, power, tr) / s;
  trap = tr;
  return d;
}

float deLayer(vec3 p, float absLayer) {
  vec4 tr;
  return deLayerAt(p, absLayer, tr);
}

vec3 normalAtLayer(vec3 p, float absLayer) {
  float e = SURF_EPS * 3.0;
  vec2 h = vec2(e, 0.0);
  return normalize(vec3(
    deLayer(p + h.xyy, absLayer) - deLayer(p - h.xyy, absLayer),
    deLayer(p + h.yxy, absLayer) - deLayer(p - h.yxy, absLayer),
    deLayer(p + h.yyx, absLayer) - deLayer(p - h.yyx, absLayer)
  ));
}

// Union of the NUM_LAYERS nested copies currently in the descent window.
// 'wonLayer' reports which one (0..NUM_LAYERS-1, relative to uDepthLayerBase)
// produced the winning (closest) distance, for shading.
float sceneDE(vec3 p, out vec4 trap, out float wonLayer) {
  float best = MAX_DIST;
  vec4 bestTrap = vec4(0.0);
  float bestK = 0.0;

  for (int k = 0; k < NUM_LAYERS; k++) {
    vec4 tr;
    float d = deLayerAt(p, uDepthLayerBase + float(k), tr);
    if (d < best) {
      best = d;
      bestTrap = tr;
      bestK = float(k);
    }
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

struct ShadeResult {
  vec3 color;
  float alpha;
};

// Shared shading for one hit on one layer: wireframe contour lines over a
// faint colored fill, so the fractal reads as glass etched with lines
// rather than a solid -- 'alpha' says how opaque this particular sample
// is, letting the caller blend through to whatever sits behind it.
// 'twoSided' drops the usual front-face falloff so the far (inner) wall of
// a shell is shaded too, for the always-visible container layer.
ShadeResult shadeSurface(vec3 p, vec3 n, vec3 rd, float t, vec4 trap, float absLayer, float ao, bool twoSided) {
  // Slow overall hue drift so the mood shifts over long timescales even
  // without the player doing anything, offset per descent-layer so each
  // fractal has a visibly different palette.
  float hue = layerHue(absLayer);
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
  // enough to actually see them.
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

  // Hard-edged silhouette line. twoSided uses abs() instead of clamping
  // negative dot products to zero, so the inside of a shell (normal facing
  // roughly the same way as the view ray) still rims instead of going dark.
  float ndotv = twoSided ? abs(dot(n, -rd)) : max(dot(n, -rd), 0.0);
  float rimLine = smoothstep(0.5, 0.85, pow(1.0 - ndotv, 2.2));
  // Kept deliberately faint: gamma correction at the end of the pipeline
  // (color^(1/2.2)) inflates even a small flat value a lot (0.05 becomes
  // ~0.24), so anything much brighter here washes the whole shell into a
  // flat tint instead of reading as line-art.
  vec3 dimFill = lineColor * 0.02 * (0.5 + 0.5 * ao);

  // Presence glow: surfaces close to the camera light up a little, echoing
  // the local exponent wobble from deLayerAt(). Kept small for the same
  // gamma reason -- and because when the camera is deep inside a shell
  // (t small almost everywhere on screen), a strong flat glow here would
  // wash out the whole surface rather than just accenting it.
  float proximity = 1.0 - smoothstep(0.0, PROX_RADIUS, t);
  vec3 glow = lineColor * (lines * 1.4 + rimLine * 0.8);
  glow += proximity * lineColor * 0.15;

  vec3 color = dimFill + glow;

  // Affondamento: quando l'hit e' fortissimamente ravvicinato (si e'
  // immersi dentro la nube del frattale, circondati da geometria, non
  // solo vicini a una parete) il colore vira verso un violaceo profondo
  // e piu' scuro, cosi' "essere dentro" si sente visivamente diverso da
  // "essere vicino a una superficie".
  float enclosure = 1.0 - smoothstep(0.0, 0.5, t);
  color = mix(color, color * vec3(0.45, 0.35, 0.65) * 0.45, enclosure * 0.8);

  ShadeResult r;
  r.color = color;
  // Mostly-transparent fill -- this is what makes the surface read as a
  // thin wireframe shell instead of a solid -- with the lines and the rim
  // pushing individual samples close to fully opaque.
  r.alpha = clamp(0.02 + lines * 1.5 + rimLine * 1.0, 0.0, 1.0);
  return r;
}

void main() {
  vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution) / uResolution.y;

  float focal = 1.0 / tan(uFov * 0.5);
  vec3 rd = normalize(uv.x * uCamRight + uv.y * uCamUp + focal * uCamForward);
  vec3 ro = uCamPos;

  // Deep-space background: soft vertical gradient, no stars (keep it calm / no HUD-like clutter).
  vec3 bgGradient = mix(vec3(0.01, 0.012, 0.02), vec3(0.05, 0.04, 0.07), 0.5 + 0.5 * uv.y);

  // The fractal that currently *contains* the camera -- one level up from
  // the foreground window below -- rendered two-sided as an always-visible
  // backdrop, so its far wall reads as the "sky" of the space we're inside.
  // Before the first descent (uDepthLayerBase == 0) this simply doesn't
  // hit anything and the plain background shows through, which is correct:
  // there is nothing "outside" the home fractal yet.
  vec3 backdrop = bgGradient;
  float containerT = MAX_DIST;
  {
    int cSteps = max(uRaySteps / 2, 16);
    float t = 0.0;
    bool hit = false;
    vec4 trap = vec4(0.0);
    float steps = 0.0;
    for (int i = 0; i < cSteps; i++) {
      vec3 p = ro + rd * t;
      vec4 tr;
      float d = deLayerAt(p, uDepthLayerBase - 1.0, tr);
      steps += 1.0;
      float ad = abs(d);
      if (ad < SURF_EPS * max(1.0, t)) {
        hit = true;
        trap = tr;
        break;
      }
      t += ad * 0.85;
      if (t > MAX_DIST) break;
    }
    if (hit) {
      vec3 p = ro + rd * t;
      vec3 n = normalAtLayer(p, uDepthLayerBase - 1.0);
      float ao = 1.0 - steps / float(cSteps);
      ShadeResult sr = shadeSurface(p, n, rd, t, trap, uDepthLayerBase - 1.0, ao, true);
      // Dimmed and kept fairly translucent so it clearly recedes behind the
      // foreground window instead of competing with it for attention.
      backdrop = mix(bgGradient, sr.color * 0.55, clamp(sr.alpha * 0.9, 0.0, 1.0));
      containerT = t;
    }
  }

  // Foreground: the fractal we're navigating through right now, plus the
  // 1-2 already visible nested inside it, composited front-to-back as
  // translucent wireframe shells (shadeSurface's alpha) -- so instead of
  // the nearest surface occluding everything behind it, each is seen
  // through the one before it, all the way down to the container backdrop.
  vec3 outColor = vec3(0.0);
  float outAlpha = 0.0;
  float firstHitT = -1.0;
  float totalSteps = 0.0;
  float t = 0.0;

  for (int pass = 0; pass < NUM_LAYERS; pass++) {
    bool hit = false;
    vec4 trap = vec4(0.0);
    float wk = 0.0;
    for (int i = 0; i < uRaySteps; i++) {
      if (totalSteps >= float(uRaySteps)) break;
      vec3 p = ro + rd * t;
      vec4 tr;
      float wkk;
      float d = sceneDE(p, tr, wkk);
      totalSteps += 1.0;
      if (d < SURF_EPS * max(1.0, t)) {
        hit = true;
        trap = tr;
        wk = wkk;
        break;
      }
      t += d * 0.85;
      if (t > MAX_DIST) break;
    }
    if (!hit) break;

    vec3 p = ro + rd * t;
    vec3 n = estimateNormal(p);
    float ao = 1.0 - totalSteps / float(uRaySteps);
    float absLayer = uDepthLayerBase + wk;
    // Two-sided like the container layer: pushing past a hit can land on
    // the far (inward-facing) wall of that same shell just as easily as on
    // the next nested layer, and that inner wall must read as wireframe
    // too, not go dark for facing "the wrong way".
    ShadeResult sr = shadeSurface(p, n, rd, t, trap, absLayer, ao, true);
    // Each successive shell seen through the ones in front of it reads a
    // little dimmer, purely by compositing order (not distance) -- so the
    // fractal(s) glimpsed inside the current one clearly recede rather than
    // looking equally "in focus".
    sr.color *= 1.0 - float(pass) * 0.32;

    outColor += (1.0 - outAlpha) * sr.alpha * sr.color;
    outAlpha += (1.0 - outAlpha) * sr.alpha;
    if (firstHitT < 0.0) firstHitT = t;
    if (outAlpha > 0.97) break;

    // Push past the surface we just hit so the next pass can find whatever
    // sits behind it (the far side of this same shell, or the next nested
    // layer), instead of registering the same hit again immediately.
    t += SURF_EPS * max(1.0, t) * 12.0;
  }

  vec3 color = outColor + (1.0 - outAlpha) * backdrop;

  // Distance fog + desaturation for a sense of infinite scale, keyed off
  // whichever surface is nearest the camera (foreground first, container
  // otherwise).
  float primaryT = firstHitT >= 0.0 ? firstHitT : containerT;
  float fogT = clamp(primaryT / MAX_DIST, 0.0, 1.0);
  float fog = 1.0 - exp(-3.2 * fogT);
  float luma = dot(color, vec3(0.299, 0.587, 0.114));
  color = mix(color, vec3(luma), fog * 0.65);
  color = mix(color, bgGradient, fog);

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
