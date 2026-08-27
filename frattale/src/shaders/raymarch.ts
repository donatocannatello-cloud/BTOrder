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
uniform float uFov;      // vertical fov, radians
uniform float uTime;
uniform float uPower;    // Mandelbulb exponent

out vec4 fragColor;

const int MAX_STEPS = 110;
const float MAX_DIST = 45.0;
const float SURF_EPS = 0.0009;
const int BULB_ITER = 8;

// Mandelbulb distance estimator.
// Returns distance estimate; also writes orbit trap info into 'trap' for coloring.
float deMandelbulb(vec3 pos, out vec4 trap) {
  vec3 z = pos;
  float dr = 1.0;
  float r = 0.0;
  trap = vec4(abs(z), dot(z, z));

  for (int i = 0; i < BULB_ITER; i++) {
    r = length(z);
    if (r > 2.5) break;

    // polar decomposition
    float theta = acos(clamp(z.z / max(r, 1e-6), -1.0, 1.0));
    float phi = atan(z.y, z.x);
    dr = pow(r, uPower - 1.0) * uPower * dr + 1.0;

    float zr = pow(r, uPower);
    theta *= uPower;
    phi *= uPower;

    z = zr * vec3(sin(theta) * cos(phi), sin(theta) * sin(phi), cos(theta));
    z += pos;

    trap = min(trap, vec4(abs(z), dot(z, z)));
  }
  return 0.5 * log(max(r, 1e-6)) * r / dr;
}

float sceneDE(vec3 p, out vec4 trap) {
  return deMandelbulb(p, trap);
}

vec3 estimateNormal(vec3 p) {
  vec4 t;
  float e = SURF_EPS * 3.0;
  vec2 h = vec2(e, 0.0);
  return normalize(vec3(
    sceneDE(p + h.xyy, t) - sceneDE(p - h.xyy, t),
    sceneDE(p + h.yxy, t) - sceneDE(p - h.yxy, t),
    sceneDE(p + h.yyx, t) - sceneDE(p - h.yyx, t)
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
  bool hit = false;

  for (int i = 0; i < MAX_STEPS; i++) {
    vec3 p = ro + rd * t;
    vec4 tr;
    float d = sceneDE(p, tr);
    steps += 1.0;
    if (d < SURF_EPS * max(1.0, t)) {
      hit = true;
      trap = tr;
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

    vec3 lightDir = normalize(vec3(0.5, 0.8, 0.3));
    float diff = max(dot(n, lightDir), 0.0);
    float ao = 1.0 - steps / float(MAX_STEPS);
    float rim = pow(1.0 - max(dot(n, -rd), 0.0), 2.5);

    // Orbit-trap based palette: cool blues/violets shifting into warm rim light.
    vec3 baseCol = mix(vec3(0.15, 0.25, 0.55), vec3(0.7, 0.35, 0.85), clamp(trap.w, 0.0, 1.0));
    vec3 lit = baseCol * (0.18 + 0.82 * diff) * (0.55 + 0.45 * ao);
    lit += rim * vec3(0.4, 0.55, 0.9) * 0.6;

    color = lit;
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

  color = color / (1.0 + color);
  color = pow(color, vec3(1.0 / 2.2));

  fragColor = vec4(color, 1.0);
}
`;
