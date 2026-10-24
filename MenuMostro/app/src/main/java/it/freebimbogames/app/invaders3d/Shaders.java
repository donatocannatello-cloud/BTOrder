package it.freebimbogames.app.invaders3d;

/**
 * GLSL ES 1.00 shaders used by the renderer.
 *
 * <p>Kept in a plain Java class (no Android imports) so the build can dump and
 * validate the exact source strings with glslangValidator. Lighting and fog here must
 * match the reference shading in tools/Sim3D.java, which rasterises the same vertices.
 */
public final class Shaders {

    public static final String VERTEX =
            "uniform mat4 uMvp;\n"
            + "uniform vec3 uEye;\n"
            + "uniform float uUnlit;\n"
            + "attribute vec3 aPos;\n"
            + "attribute vec3 aNormal;\n"
            + "attribute vec3 aColor;\n"
            + "varying vec3 vColor;\n"
            + "void main() {\n"
            + "  vec3 n = normalize(aNormal);\n"
            + "  vec3 l = normalize(vec3(0.25, 0.78, -0.55));\n"
            + "  float diff = max(dot(n, l), 0.0);\n"
            + "  vec3 lit = aColor * (0.44 + 0.78 * diff);\n"
            + "  float dist = length(aPos - uEye);\n"
            + "  float fog = clamp((dist - 420.0) / 1500.0, 0.0, 0.92);\n"
            + "  vec3 col = mix(lit, vec3(0.020, 0.028, 0.062), fog);\n"
            + "  vColor = mix(col, aColor, uUnlit);\n"
            + "  gl_Position = uMvp * vec4(aPos, 1.0);\n"
            + "}\n";

    public static final String FRAGMENT =
            "precision mediump float;\n"
            + "varying vec3 vColor;\n"
            + "void main() {\n"
            + "  gl_FragColor = vec4(vColor, 1.0);\n"
            + "}\n";

    private Shaders() {
    }
}
