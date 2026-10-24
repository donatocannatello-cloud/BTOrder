package it.freebimbogames.app.invaders3d;

/**
 * CPU-side triangle soup with per-vertex colours and world-space normals.
 *
 * <p>Every frame the scene is transformed on the CPU into two flat arrays (opaque
 * geometry and additive glow geometry). The Android renderer uploads them to one VBO
 * each and draws one call per pass; the desktop harness feeds the exact same arrays to
 * a software rasteriser, so the test renders the data the GPU would receive.
 *
 * <p>Vertex layout: px, py, pz, nx, ny, nz, r, g, b (9 floats). Shapes are stored the
 * same way minus the normals: px, py, pz, r, g, b (6 floats).
 */
public final class Mesh {

    public static final int STRIDE = 9;
    public static final int SHAPE_STRIDE = 6;

    private float[] solid = new float[STRIDE * 3 * 256];
    private float[] glow = new float[STRIDE * 3 * 128];
    private int solidLen;
    private int glowLen;
    private final float[][] scratch = new float[3][9];

    public void reset() {
        solidLen = 0;
        glowLen = 0;
    }

    public int solidFloats() {
        return solidLen;
    }

    public int glowFloats() {
        return glowLen;
    }

    public float[] solidData() {
        return solid;
    }

    public float[] glowData() {
        return glow;
    }

    /** Four-component transform folded into a shape (yaw about Y, then pitch about X). */
    public void shape(float[] local, float px, float py, float pz,
                      float sx, float sy, float sz, float yaw, float pitch,
                      float tr, float tg, float tb, float flash, boolean glowPass) {
        int tris = local.length / (SHAPE_STRIDE * 3);
        float cosY = (float) Math.cos(yaw), sinY = (float) Math.sin(yaw);
        float cosX = (float) Math.cos(pitch), sinX = (float) Math.sin(pitch);
        for (int t = 0; t < tris; t++) {
            int base = t * SHAPE_STRIDE * 3;
            float[] a = scratch[0];
            float[] b = scratch[1];
            float[] c = scratch[2];
            for (int v = 0; v < 3; v++) {
                int o = base + v * SHAPE_STRIDE;
                float lx = local[o] * sx;
                float ly = local[o + 1] * sy;
                float lz = local[o + 2] * sz;
                // yaw
                float x1 = lx * cosY + lz * sinY;
                float z1 = -lx * sinY + lz * cosY;
                // pitch
                float y2 = ly * cosX - z1 * sinX;
                float z2 = ly * sinX + z1 * cosX;
                float[] dst = v == 0 ? a : (v == 1 ? b : c);
                dst[0] = px + x1;
                dst[1] = py + y2;
                dst[2] = pz + z2;
                dst[6] = mix(local[o + 3], 1f, flash) * tr;
                dst[7] = mix(local[o + 4], 1f, flash) * tg;
                dst[8] = mix(local[o + 5], 1f, flash) * tb;
            }
            triangle(a, b, c, px, py, pz, glowPass);        }
    }

    public void box(float cx, float cy, float cz, float hx, float hy, float hz,
                    float r, float g, float b, boolean glowPass) {
        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, cx, cy, cz, glowPass);
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, r, g, b, cx, cy, cz, glowPass);
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, r, g, b, cx, cy, cz, glowPass);
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, cx, cy, cz, glowPass);
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, cx, cy, cz, glowPass);
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, cx, cy, cz, glowPass);
    }

    /**
     * Thin ribbon lying in the XZ plane: a line with width, used for the terrain
     * contours on the floor. Drawn in the additive pass so the lines glow softly.
     */
    public void ribbon(float ax, float ay, float az, float bx, float by, float bz,
                       float width, float r, float g, float b, boolean glowPass) {
        float dx = bx - ax;
        float dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f) {
            return;
        }
        float half = width * 0.5f;
        float px = -dz / len * half;
        float pz = dx / len * half;
        quad(ax - px, ay, az - pz, ax + px, ay, az + pz,
                bx + px, by, bz + pz, bx - px, by, bz - pz,
                r, g, b, (ax + bx) * 0.5f, ay - 4f, (az + bz) * 0.5f, glowPass);
    }

    /** Octahedron: cheap, faceted body that reads well with flat lighting. */
    public void octa(float cx, float cy, float cz, float hx, float hy, float hz,
                     float r, float g, float b, boolean glowPass) {
        float ax = cx + hx, bx = cx - hx;
        float ay = cy + hy, by = cy - hy;
        float az = cz + hz, bz = cz - hz;
        tri(ax, cy, cz, cx, ay, cz, cx, cy, az, r, g, b, cx, cy, cz, glowPass);
        tri(cx, ay, cz, bx, cy, cz, cx, cy, az, r, g, b, cx, cy, cz, glowPass);
        tri(bx, cy, cz, cx, by, cz, cx, cy, az, r, g, b, cx, cy, cz, glowPass);
        tri(cx, by, cz, ax, cy, cz, cx, cy, az, r, g, b, cx, cy, cz, glowPass);
        tri(cx, ay, cz, ax, cy, cz, cx, cy, bz, r, g, b, cx, cy, cz, glowPass);
        tri(bx, cy, cz, cx, ay, cz, cx, cy, bz, r, g, b, cx, cy, cz, glowPass);
        tri(cx, by, cz, bx, cy, cz, cx, cy, bz, r, g, b, cx, cy, cz, glowPass);
        tri(ax, cy, cz, cx, by, cz, cx, cy, bz, r, g, b, cx, cy, cz, glowPass);
    }

    /** Camera-facing quad (hard-edged). */
    public void billboard(float cx, float cy, float cz, float size,
                          float[] right, float[] up, float r, float g, float b,
                          boolean glowPass) {
        float rx = right[0] * size, ry = right[1] * size, rz = right[2] * size;
        float ux = up[0] * size, uy = up[1] * size, uz = up[2] * size;
        float ax = cx - rx - ux, ay = cy - ry - uy, az = cz - rz - uz;
        float bx = cx + rx - ux, by = cy + ry - uy, bz = cz + rz - uz;
        float dx = cx + rx + ux, dy = cy + ry + uy, dz = cz + rz + uz;
        float ex = cx - rx + ux, ey = cy - ry + uy, ez = cz - rz + uz;
        tri(ax, ay, az, bx, by, bz, dx, dy, dz, r, g, b, cx, cy, cz, glowPass);
        tri(ax, ay, az, dx, dy, dz, ex, ey, ez, r, g, b, cx, cy, cz, glowPass);
    }

    /**
     * Camera-facing glow dot: bright centre falling off to the corners. Additive
     * blending turns the four triangles into a soft round light instead of a square.
     */
    public void sprite(float cx, float cy, float cz, float size,
                       float[] right, float[] up, float r, float g, float b) {
        float rx = right[0] * size, ry = right[1] * size, rz = right[2] * size;
        float ux = up[0] * size, uy = up[1] * size, uz = up[2] * size;
        float e = 0.04f;
        float ax = cx - rx - ux, ay = cy - ry - uy, az = cz - rz - uz;
        float bx = cx + rx - ux, by = cy + ry - uy, bz = cz + rz - uz;
        float dx = cx + rx + ux, dy = cy + ry + uy, dz = cz + rz + uz;
        float ex = cx - rx + ux, ey = cy - ry + uy, ez = cz - rz + uz;
        triColored(cx, cy, cz, ax, ay, az, bx, by, bz,
                r, g, b, r * e, g * e, b * e, r * e, g * e, b * e, cx, cy, cz + 1f, true);
        triColored(cx, cy, cz, bx, by, bz, dx, dy, dz,
                r, g, b, r * e, g * e, b * e, r * e, g * e, b * e, cx, cy, cz + 1f, true);
        triColored(cx, cy, cz, dx, dy, dz, ex, ey, ez,
                r, g, b, r * e, g * e, b * e, r * e, g * e, b * e, cx, cy, cz + 1f, true);
        triColored(cx, cy, cz, ex, ey, ez, ax, ay, az,
                r, g, b, r * e, g * e, b * e, r * e, g * e, b * e, cx, cy, cz + 1f, true);
    }

    public void quad(float ax, float ay, float az, float bx, float by, float bz,
                     float cx, float cy, float cz, float dx, float dy, float dz,
                     float r, float g, float b, float refX, float refY, float refZ,
                     boolean glowPass) {
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, r, g, b, refX, refY, refZ, glowPass);
        tri(ax, ay, az, cx, cy, cz, dx, dy, dz, r, g, b, refX, refY, refZ, glowPass);
    }

    public void tri(float ax, float ay, float az, float bx, float by, float bz,
                    float cx, float cy, float cz, float r, float g, float b,
                    float refX, float refY, float refZ, boolean glowPass) {
        triColored(ax, ay, az, bx, by, bz, cx, cy, cz,
                r, g, b, r, g, b, r, g, b, refX, refY, refZ, glowPass);
    }

    public void triColored(float ax, float ay, float az, float bx, float by, float bz,
                           float cx, float cy, float cz,
                           float ar, float ag, float ab,
                           float br, float bg, float bb,
                           float cr, float cg, float cb,
                           float refX, float refY, float refZ, boolean glowPass) {
        float e1x = bx - ax, e1y = by - ay, e1z = bz - az;
        float e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) {
            return; // degenerate sliver
        }
        nx /= len;
        ny /= len;
        nz /= len;
        // Point the normal away from the object's centre: keeps lighting correct even
        // when a primitive is wound the other way round.
        float mx = (ax + bx + cx) / 3f - refX;
        float my = (ay + by + cy) / 3f - refY;
        float mz = (az + bz + cz) / 3f - refZ;
        if (mx * nx + my * ny + mz * nz < 0f) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }
        float[] buf;
        int idx;
        if (glowPass) {
            buf = glow;
            idx = glowLen;
        } else {
            buf = solid;
            idx = solidLen;
        }
        if (idx + STRIDE * 3 > buf.length) {
            buf = grow(buf);
            if (glowPass) {
                glow = buf;
            } else {
                solid = buf;
            }
        }
        idx = put(buf, idx, ax, ay, az, nx, ny, nz, ar, ag, ab);
        idx = put(buf, idx, bx, by, bz, nx, ny, nz, br, bg, bb);
        idx = put(buf, idx, cx, cy, cz, nx, ny, nz, cr, cg, cb);
        if (glowPass) {
            glowLen = idx;
        } else {
            solidLen = idx;
        }
    }

    private void triangle(float[] a, float[] b, float[] c,
                          float refX, float refY, float refZ, boolean glowPass) {
        triColored(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2],
                a[6], a[7], a[8], b[6], b[7], b[8], c[6], c[7], c[8],
                refX, refY, refZ, glowPass);
    }

    private static float[] grow(float[] src) {
        float[] out = new float[src.length * 2];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }

    private static int put(float[] buf, int idx,
                           float x, float y, float z,
                           float nx, float ny, float nz,
                           float r, float g, float b) {
        buf[idx] = x;
        buf[idx + 1] = y;
        buf[idx + 2] = z;
        buf[idx + 3] = nx;
        buf[idx + 4] = ny;
        buf[idx + 5] = nz;
        buf[idx + 6] = r;
        buf[idx + 7] = g;
        buf[idx + 8] = b;
        return idx + STRIDE;
    }

    private static float mix(float a, float b, float t) {
        if (t <= 0f) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        return a + (b - a) * t;
    }

    /** Copies the accumulated solid geometry into a standalone shape array. */
    public float[] freeze(int floats, boolean glowPass) {
        float[] src = glowPass ? glow : solid;
        float[] out = new float[floats];
        // Strip normals: shape layout is pos + colour only.
        int srcIdx = 0;
        int dstIdx = 0;
        while (srcIdx < floats) {
            out[dstIdx] = src[srcIdx];
            out[dstIdx + 1] = src[srcIdx + 1];
            out[dstIdx + 2] = src[srcIdx + 2];
            out[dstIdx + 3] = src[srcIdx + 6];
            out[dstIdx + 4] = src[srcIdx + 7];
            out[dstIdx + 5] = src[srcIdx + 8];
            srcIdx += STRIDE;
            dstIdx += SHAPE_STRIDE;
        }
        return out;
    }

    // ------------------------------------------------------------ colour helpers

    public static int rgb(float r, float g, float b) {
        int ri = clamp255(r);
        int gi = clamp255(g);
        int bi = clamp255(b);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    public static float red(int color) {
        return ((color >> 16) & 0xFF) / 255f;
    }

    public static float green(int color) {
        return ((color >> 8) & 0xFF) / 255f;
    }

    public static float blue(int color) {
        return (color & 0xFF) / 255f;
    }

    private static int clamp255(float v) {
        if (v <= 0f) {
            return 0;
        }
        if (v >= 1f) {
            return 255;
        }
        return (int) (v * 255f + 0.5f);
    }
}
