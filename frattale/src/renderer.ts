import { FRAG_SRC, VERT_SRC } from "./shaders/fractalMap";

function compileShader(gl: WebGL2RenderingContext, type: number, src: string): WebGLShader {
  const shader = gl.createShader(type)!;
  gl.shaderSource(shader, src);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const log = gl.getShaderInfoLog(shader);
    gl.deleteShader(shader);
    throw new Error(`Shader compile error: ${log}`);
  }
  return shader;
}

export class Renderer {
  private gl: WebGL2RenderingContext;
  private program: WebGLProgram;
  private uniforms: Record<string, WebGLUniformLocation | null>;

  constructor(private canvas: HTMLCanvasElement) {
    const gl = canvas.getContext("webgl2", { antialias: false, powerPreference: "high-performance" });
    if (!gl) throw new Error("WebGL2 non disponibile su questo dispositivo.");
    this.gl = gl;

    const vert = compileShader(gl, gl.VERTEX_SHADER, VERT_SRC);
    const frag = compileShader(gl, gl.FRAGMENT_SHADER, FRAG_SRC);
    const program = gl.createProgram()!;
    gl.attachShader(program, vert);
    gl.attachShader(program, frag);
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      throw new Error(`Program link error: ${gl.getProgramInfoLog(program)}`);
    }
    this.program = program;

    const names = [
      "uResolution", "uCenter", "uFrac", "uLayerBase", "uMaxIter", "uTime", "uBreath",
      "uNucleusUv", "uNucleusGlow", "uNucleusSolved", "uNucleusBloom",
    ];
    this.uniforms = {};
    for (const name of names) {
      this.uniforms[name] = gl.getUniformLocation(program, name);
    }

    // Empty VAO: vertices are generated in the vertex shader from gl_VertexID.
    const vao = gl.createVertexArray();
    gl.bindVertexArray(vao);
  }

  resize(width: number, height: number) {
    this.canvas.width = width;
    this.canvas.height = height;
    this.gl.viewport(0, 0, width, height);
  }

  render(opts: {
    centerX: number;
    centerY: number;
    frac: number;
    layerBase: number;
    maxIter: number;
    time: number;
    breath: number;
    nucleusUvX: number;
    nucleusUvY: number;
    nucleusGlow: number;
    nucleusSolved: number;
    nucleusBloom: number;
  }) {
    const gl = this.gl;
    gl.useProgram(this.program);
    gl.uniform2f(this.uniforms.uResolution, this.canvas.width, this.canvas.height);
    gl.uniform2f(this.uniforms.uCenter, opts.centerX, opts.centerY);
    gl.uniform1f(this.uniforms.uFrac, opts.frac);
    gl.uniform1f(this.uniforms.uLayerBase, opts.layerBase);
    gl.uniform1i(this.uniforms.uMaxIter, opts.maxIter);
    gl.uniform1f(this.uniforms.uTime, opts.time);
    gl.uniform1f(this.uniforms.uBreath, opts.breath);
    gl.uniform2f(this.uniforms.uNucleusUv, opts.nucleusUvX, opts.nucleusUvY);
    gl.uniform1f(this.uniforms.uNucleusGlow, opts.nucleusGlow);
    gl.uniform1f(this.uniforms.uNucleusSolved, opts.nucleusSolved);
    gl.uniform1f(this.uniforms.uNucleusBloom, opts.nucleusBloom);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
  }
}
