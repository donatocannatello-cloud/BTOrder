package it.freebimbogames.app.invaders3d;

/** Minimal column-major 4x4 matrix helpers (same layout OpenGL expects). */
public final class Mat4 {

    private Mat4() {
    }

    public static float[] identity() {
        float[] m = new float[16];
        identity(m);
        return m;
    }

    public static void identity(float[] m) {
        for (int i = 0; i < 16; i++) {
            m[i] = 0f;
        }
        m[0] = 1f;
        m[5] = 1f;
        m[10] = 1f;
        m[15] = 1f;
    }

    /** out = a * b (out must not alias a or b). */
    public static void multiply(float[] out, float[] a, float[] b) {
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[col * 4 + k];
                }
                out[col * 4 + row] = sum;
            }
        }
    }

    public static void perspective(float[] out, float fovyDeg, float aspect, float near, float far) {
        float f = 1f / (float) Math.tan(Math.toRadians(fovyDeg) * 0.5);
        for (int i = 0; i < 16; i++) {
            out[i] = 0f;
        }
        out[0] = f / aspect;
        out[5] = f;
        out[10] = (far + near) / (near - far);
        out[11] = -1f;
        out[14] = (2f * far * near) / (near - far);
    }

    public static void lookAt(float[] out, float ex, float ey, float ez,
                              float cx, float cy, float cz,
                              float ux, float uy, float uz) {
        float fx = cx - ex, fy = cy - ey, fz = cz - ez;
        float flen = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (flen < 1e-5f) {
            flen = 1f;
        }
        fx /= flen;
        fy /= flen;
        fz /= flen;

        float sx = fy * uz - fz * uy;
        float sy = fz * ux - fx * uz;
        float sz = fx * uy - fy * ux;
        float slen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (slen < 1e-5f) {
            slen = 1f;
            sx = 1f;
        }
        sx /= slen;
        sy /= slen;
        sz /= slen;

        float upx = sy * fz - sz * fy;
        float upy = sz * fx - sx * fz;
        float upz = sx * fy - sy * fx;

        out[0] = sx;
        out[1] = upx;
        out[2] = -fx;
        out[3] = 0f;

        out[4] = sy;
        out[5] = upy;
        out[6] = -fy;
        out[7] = 0f;

        out[8] = sz;
        out[9] = upz;
        out[10] = -fz;
        out[11] = 0f;

        out[12] = -(sx * ex + sy * ey + sz * ez);
        out[13] = -(upx * ex + upy * ey + upz * ez);
        out[14] = fx * ex + fy * ey + fz * ez;
        out[15] = 1f;
    }

    /** Transforms a point, writing xyz into out[0..2]. */
    public static void transformPoint(float[] m, float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
        out[3] = m[3] * x + m[7] * y + m[11] * z + m[15];
    }

    /** Camera position from a rigid view matrix: eye = -R^T * t. */
    public static void eyeFromView(float[] view, float[] out) {
        out[0] = -(view[0] * view[12] + view[4] * view[13] + view[8] * view[14]);
        out[1] = -(view[1] * view[12] + view[5] * view[13] + view[9] * view[14]);
        out[2] = -(view[2] * view[12] + view[6] * view[13] + view[10] * view[14]);
    }

    /** Right and up axes of the camera, derived from a view matrix. */
    public static void cameraBasis(float[] view, float[] right, float[] up) {
        right[0] = view[0];
        right[1] = view[4];
        right[2] = view[8];
        up[0] = view[1];
        up[1] = view[5];
        up[2] = view[9];
    }
}
