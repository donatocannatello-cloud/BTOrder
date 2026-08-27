import { FRAG_SRC, VERT_SRC } from "./shaders/raymarch";
import type { Vec3 } from "./quat";

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

    const names = ["uResolution", "uCamPos", "uCamRight", "uCamUp", "uCamForward", "uFov", "uTime", "uPower", "uMaxIter"];
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
    camPos: Vec3;
    camRight: Vec3;
    camUp: Vec3;
    camForward: Vec3;
    fov: number;
    time: number;
    power: number;
    maxIter: number;
  }) {
    const gl = this.gl;
    gl.useProgram(this.program);
    gl.uniform2f(this.uniforms.uResolution, this.canvas.width, this.canvas.height);
    gl.uniform3f(this.uniforms.uCamPos, ...opts.camPos);
    gl.uniform3f(this.uniforms.uCamRight, ...opts.camRight);
    gl.uniform3f(this.uniforms.uCamUp, ...opts.camUp);
    gl.uniform3f(this.uniforms.uCamForward, ...opts.camForward);
    gl.uniform1f(this.uniforms.uFov, opts.fov);
    gl.uniform1f(this.uniforms.uTime, opts.time);
    gl.uniform1f(this.uniforms.uPower, opts.power);
    gl.uniform1i(this.uniforms.uMaxIter, opts.maxIter);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
  }
}
