package it.freebimbogames.app.invaders3d;

/**
 * Turns the simulation state into GPU-ready geometry.
 *
 * <p>The whole frame is baked on the CPU into two float arrays owned by {@link Mesh}:
 * world-space triangles with per-vertex colour and normals, one pass for opaque
 * geometry and one for additive glow. The Android renderer just uploads them; the
 * desktop harness rasterises the same arrays, so the visual tests exercise the real
 * scene data.
 */
public final class Scene {

    public static final float FOVY = 58f;
    public static final float NEAR = 4f;
    public static final float FAR = 2400f;

    private static final int STAR_COUNT = 320;
    private static final float STAR_SPAN = 1500f;

    // Terrain contours on the floor: a height field sampled once, then turned into
    // isolines by marching squares. The segments drift toward the player afterwards.
    private static final float FIELD_Z0 = -220f;
    private static final float FIELD_Z1 = 2100f;
    private static final float FLOOR_Y = -150f;

    /** Segment list: x1, z1, x2, z2 per entry. */
    private float[] contour = new float[4 * 512];
    private byte[] contourStyle = new byte[512];
    private int contourCount;
    private float contourSpan = FIELD_Z1 - FIELD_Z0;

    private final float[] view = new float[16];
    private final float[] proj = new float[16];
    private final float[] mvp = new float[16];
    private final float[] right = new float[3];
    private final float[] up = new float[3];
    private final float[] tmp = new float[4];

    public final Mesh mesh = new Mesh();

    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starZ = new float[STAR_COUNT];
    private final float[] starSpeed = new float[STAR_COUNT];
    private final float[] starSize = new float[STAR_COUNT];
    private final float[] starR = new float[STAR_COUNT];
    private final float[] starG = new float[STAR_COUNT];
    private final float[] starB = new float[STAR_COUNT];

    public Scene(long seed) {
        buildContours();
        java.util.Random rnd = new java.util.Random(seed);
        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = (rnd.nextFloat() - 0.5f) * 900f;
            starY[i] = (rnd.nextFloat() - 0.5f) * 520f;
            starZ[i] = rnd.nextFloat() * STAR_SPAN;
            starSpeed[i] = 40f + rnd.nextFloat() * 150f;
            starSize[i] = 1.4f + rnd.nextFloat() * 2.2f;
            float warm = rnd.nextFloat();
            starR[i] = 0.65f + warm * 0.35f;
            starG[i] = 0.72f + warm * 0.25f;
            starB[i] = 0.95f + warm * 0.05f;
        }
    }

    public float[] viewMatrix() {
        return view;
    }

    public float[] mvpMatrix() {
        return mvp;
    }

    private void camera(Game3D g, float aspect) {
        float sx = 0f, sy = 0f;
        if (g.shake > 0.01f) {
            float amp = g.shake * 9f;
            sx = (float) Math.sin(g.time * 61f) * amp;
            sy = (float) Math.cos(g.time * 47f) * amp;
        }
        float ex = g.playerX * 0.32f + sx;
        float ey = 106f + g.playerY * 0.30f + sy;
        float ez = -420f + g.playerZ * 0.25f;
        float cx = g.playerX * 0.40f;
        float cy = -6f + g.playerY * 0.20f;
        float cz = 300f;
        Mat4.lookAt(view, ex, ey, ez, cx, cy, cz, 0f, 1f, 0f);
        Mat4.perspective(proj, FOVY, aspect, NEAR, FAR);
        Mat4.multiply(mvp, proj, view);
        Mat4.cameraBasis(view, right, up);
    }

    public void build(Game3D g, float aspect) {
        camera(g, aspect);
        mesh.reset();

        stars(g);
        contours(g);
        bunkers(g);
        aliens(g);
        boss(g);
        ufo(g);
        powerUps(g);
        bullets(g);
        ship(g);
        particles(g);
    }

    // ------------------------------------------------------------------ world

    /** Distant gas clouds: a few huge faint sprites that keep the sky from going flat. */
    private void nebula(Game3D g) {
        float breathe = 0.85f + 0.15f * (float) Math.sin(g.time * 0.4f);
        mesh.sprite(-620f, 520f, 1500f, 620f, right, up, 0.055f * breathe, 0.020f, 0.090f);
        mesh.sprite(700f, 640f, 1650f, 700f, right, up, 0.020f, 0.055f * breathe, 0.085f);
        mesh.sprite(160f, 760f, 1900f, 520f, right, up, 0.040f, 0.028f, 0.075f);
    }

    private void stars(Game3D g) {
        nebula(g);
        float t = g.time;
        for (int i = 0; i < STAR_COUNT; i++) {
            float z = starZ[i] - t * starSpeed[i];
            z = z % STAR_SPAN;
            if (z < 0f) {
                z += STAR_SPAN;
            }
            float wz = z + 60f;
            float fade = 0.35f + 0.65f * Math.min(1f, wz / 600f);
            float twinkle = 0.7f + 0.3f * (float) Math.sin(t * 3.1f + i * 1.7f);
            float a = fade * twinkle * 0.75f;
            mesh.sprite(starX[i], starY[i], wz, starSize[i], right, up,
                    starR[i] * a, starG[i] * a, starB[i] * a);
        }
    }

    /** Analytic height of the surveyed valley under the playfield. */
    private static float terrain(float x, float z) {
        return 40f * (float) Math.sin(x / 95f) * (float) Math.cos(z / 140f)
                + 24f * (float) Math.sin((x + z) / 155f + 1.7f)
                + 14f * (float) Math.cos(x / 58f - z / 190f);
    }

    /**
     * Traces the isolines once with marching squares: nested closed loops around the
     * summits, open lines across the valley, saddles between them. Baking them keeps
     * the per-frame cost to a plain transform of a few hundred segments.
     */
    private void buildContours() {
        final int nx = 36;
        final int nz = 36;
        final float x0 = -900f;
        final float x1 = 900f;
        final float dx = (x1 - x0) / nx;
        final float dz = contourSpan / nz;
        final float[] px = new float[4];
        final float[] pz = new float[4];

        contourCount = 0;
        for (int level = 0; level < 10; level++) {
            float h = -58f + level * 14f;
            boolean index = level % 3 == 1;
            for (int j = 0; j < nz; j++) {
                for (int i = 0; i < nx; i++) {
                    float ax = x0 + i * dx;
                    float az = FIELD_Z0 + j * dz;
                    float h00 = terrain(ax, az);
                    float h10 = terrain(ax + dx, az);
                    float h11 = terrain(ax + dx, az + dz);
                    float h01 = terrain(ax, az + dz);
                    int mask = 0;
                    if (h00 > h) {
                        mask |= 1;
                    }
                    if (h10 > h) {
                        mask |= 2;
                    }
                    if (h11 > h) {
                        mask |= 4;
                    }
                    if (h01 > h) {
                        mask |= 8;
                    }
                    if (mask == 0 || mask == 15) {
                        continue;
                    }
                    int n = 0;
                    if (((mask & 1) > 0) != ((mask & 2) > 0)) {          // bottom edge
                        float t = (h - h00) / (h10 - h00);
                        px[n] = ax + t * dx;
                        pz[n] = az;
                        n++;
                    }
                    if (((mask & 2) > 0) != ((mask & 4) > 0)) {          // right edge
                        float t = (h - h10) / (h11 - h10);
                        px[n] = ax + dx;
                        pz[n] = az + t * dz;
                        n++;
                    }
                    if (((mask & 4) > 0) != ((mask & 8) > 0)) {          // top edge
                        float t = (h - h11) / (h01 - h11);
                        px[n] = ax + dx - t * dx;
                        pz[n] = az + dz;
                        n++;
                    }
                    if (((mask & 8) > 0) != ((mask & 1) > 0)) {          // left edge
                        float t = (h - h01) / (h00 - h01);
                        px[n] = ax;
                        pz[n] = az + dz - t * dz;
                        n++;
                    }
                    if (n == 2) {
                        addSegment(px[0], pz[0], px[1], pz[1], index);
                    } else if (n == 4) {
                        // Saddle: the centre decides which pair of corners the lines wrap.
                        boolean centreHigh = (h00 + h10 + h11 + h01) * 0.25f > h;
                        if (centreHigh) {
                            addSegment(px[3], pz[3], px[0], pz[0], index);
                            addSegment(px[1], pz[1], px[2], pz[2], index);
                        } else {
                            addSegment(px[0], pz[0], px[1], pz[1], index);
                            addSegment(px[2], pz[2], px[3], pz[3], index);
                        }
                    }
                }
            }
        }
    }

    private void addSegment(float xa, float za, float xb, float zb, boolean index) {
        if (contourCount * 4 + 4 > contour.length) {
            float[] grown = new float[contour.length * 2];
            System.arraycopy(contour, 0, grown, 0, contour.length);
            contour = grown;
            byte[] grownStyle = new byte[contourStyle.length * 2];
            System.arraycopy(contourStyle, 0, grownStyle, 0, contourStyle.length);
            contourStyle = grownStyle;
        }
        int o = contourCount * 4;
        contour[o] = xa;
        contour[o + 1] = za;
        contour[o + 2] = xb;
        contour[o + 3] = zb;
        contourStyle[contourCount] = (byte) (index ? 1 : 0);
        contourCount++;
    }

    /**
     * Draws the surveyed floor: baked isolines drifting slowly toward the player.
     * Index contours are heavier and warmer, like the HUD's own index lines.
     */
    private void contours(Game3D g) {
        float drift = (g.time * 22f) % contourSpan;
        for (int k = 0; k < contourCount; k++) {
            int o = k * 4;
            float xa = contour[o];
            float za = contour[o + 1];
            float xb = contour[o + 2];
            float zb = contour[o + 3] + drift;
            za += drift;
            float mz = (za + zb) * 0.5f;
            if (mz > FIELD_Z1) {
                za -= contourSpan;
                zb -= contourSpan;
                mz -= contourSpan;
            }
            boolean index = contourStyle[k] == 1;
            // Lines thin out toward the horizon instead of stacking up into a glow.
            float a = 1f - Math.min(1f, Math.max(0f, (mz - 160f) / 1900f));
            if (a < 0.05f) {
                continue;
            }
            if (index) {
                mesh.ribbon(xa, FLOOR_Y, za, xb, FLOOR_Y, zb, 3.4f,
                        0.24f * a, 0.185f * a, 0.100f * a, true);
            } else {
                mesh.ribbon(xa, FLOOR_Y, za, xb, FLOOR_Y, zb, 1.9f,
                        0.105f * a, 0.098f * a, 0.078f * a, true);
            }
        }
    }

    private void bunkers(Game3D g) {
        for (Game3D.Shield s : g.shields) {
            for (int r = 0; r < Game3D.SHIELD_ROWS; r++) {
                for (int c = 0; c < Game3D.SHIELD_COLS; c++) {
                    if (!s.cells[r][c]) {
                        continue;
                    }
                    float x = s.x + c * Game3D.SHIELD_BLOCK + Game3D.SHIELD_BLOCK / 2f;
                    float y = Game3D.SHIELD_TOP - r * Game3D.SHIELD_BLOCK - Game3D.SHIELD_BLOCK / 2f;
                    float h = Game3D.SHIELD_BLOCK / 2f;
                    // Energy barrier: additive blocks read as translucent shields and
                    // stay visually separate from the fleet behind them.
                    mesh.box(x, y, Game3D.SHIELD_Z, h * 0.9f, h * 0.9f, 0.6f,
                            0.030f, 0.230f, 0.150f, true);
                }
            }
        }
    }

    private void aliens(Game3D g) {
        for (int l = 0; l < Game3D.LAYERS; l++) {
            for (int r = 0; r < Game3D.ROWS; r++) {
                int color = Game3D.alienColor(r);
                float tr = Mesh.red(color), tg = Mesh.green(color), tb = Mesh.blue(color);
                float[] shape = alienShape(Game3D.alienType(r));
                for (int c = 0; c < Game3D.COLS; c++) {
                    if (!g.alienAlive[l][r][c]) {
                        continue;
                    }
                    float x = Game3D.alienX(g.formationX, l, c);
                    float y = Game3D.alienY(g.formationY, r)
                            + (float) Math.sin(g.time * 2.6f + r * 0.7f + l) * 2.4f;
                    float z = Game3D.alienZ(g.formationZ, l);
                    float yaw = (g.animFrame == 0 ? 0.22f : -0.22f) + l * 0.3f;
                    mesh.shape(shape, x, y, z, 1f, 1f, 1f, yaw, 0f,
                            tr * 1.25f, tg * 1.25f, tb * 1.25f, 0f, false);
                }
            }
        }
    }

    private static float[] alienShape(int type) {
        switch (type) {
            case 0:
                return Shapes.ALIEN_A;
            case 1:
                return Shapes.ALIEN_B;
            default:
                return Shapes.ALIEN_C;
        }
    }

    private void boss(Game3D g) {
        if (!g.bossActive) {
            return;
        }
        float flash = g.bossHitFlash;
        float scale = 1f + g.bossIntro * 0.15f;
        mesh.shape(Shapes.BOSS, g.bossX, g.bossY, g.bossZ, scale, scale, scale,
                (float) Math.sin(g.time * 0.9f) * 0.16f, 0f,
                1f, 1f, 1f, flash * 0.9f, false);

        // Engine glow under the hull.
        float pulse = 0.6f + 0.4f * (float) Math.sin(g.time * 6f);
        mesh.sprite(g.bossX, g.bossY - 18f, g.bossZ - 18f, 26f, right, up,
                0.42f * pulse, 0.14f * pulse, 0.26f * pulse);
        if (g.bossIntro > 0f) {
            float ring = 1f - Math.min(1f, g.bossIntro / 1.8f);
            float a = 0.55f * (1f - ring);
            mesh.sprite(g.bossX, g.bossY, g.bossZ, 40f + ring * 120f, right, up,
                    a, a * 0.35f, a * 0.55f);
        }
    }

    private void ufo(Game3D g) {
        if (!g.ufoActive) {
            return;
        }
        mesh.shape(Shapes.UFO, g.ufoX, g.ufoY, 285f, 1f, 1f, 1f,
                g.time * 1.2f, 0f, 1f, 1f, 1f, 0f, false);
        float pulse = 0.7f + 0.3f * (float) Math.sin(g.time * 9f);
        mesh.sprite(g.ufoX, g.ufoY - 8f, 278f, 34f, right, up,
                0.5f * pulse, 0.45f * pulse, 0.1f * pulse);
    }

    private void powerUps(Game3D g) {
        for (Game3D.PowerUp p : g.powerUps) {
            int color = Game3D.powerUpColor(p.type);
            float r = Mesh.red(color), gr = Mesh.green(color), b = Mesh.blue(color);
            float spin = g.time * 2.2f;
            mesh.shape(Shapes.CAPSULE, p.x, p.y, p.z, 1.15f, 1.15f, 1.15f, spin, 0.3f,
                    r, gr, b, 0f, false);
            float pulse = 0.55f + 0.45f * (float) Math.sin(g.time * 7f + p.z * 0.1f);
            mesh.sprite(p.x, p.y, p.z, 30f, right, up,
                    r * pulse * 0.7f, gr * pulse * 0.7f, b * pulse * 0.7f);
        }
    }

    private void bullets(Game3D g) {
        for (Game3D.Bullet b : g.bullets) {
            if (b.fromPlayer) {
                mesh.box(b.x, b.y, b.z, 1.5f, 1.5f, 8f, 0.55f, 0.75f, 0.35f, true);
                mesh.box(b.x, b.y, b.z, 0.6f, 0.6f, 5f, 1f, 0.98f, 0.75f, true);
            } else {
                mesh.box(b.x, b.y, b.z, 1.9f, 1.9f, 7f, 0.75f, 0.18f, 0.35f, true);
                mesh.box(b.x, b.y, b.z, 0.7f, 0.7f, 4.5f, 1f, 0.5f, 0.7f, true);
            }
        }
    }

    private void ship(Game3D g) {
        if (!g.playerAlive || !g.playerVisible) {
            return;
        }
        float bob = (float) Math.sin(g.time * 2f) * 1.2f;
        mesh.shape(Shapes.SHIP, g.playerX, g.playerY + bob, g.playerZ, 1.12f, 1.1f, 1.12f,
                0f, -g.playerX * 0.0016f, 1f, 1f, 1f, 0f, false);

        // engine flare
        float flare = 3.2f + 1.8f * (float) Math.abs(Math.sin(g.time * 17f));
        mesh.sprite(g.playerX, g.playerY - 2f, g.playerZ - 23f, flare, right, up,
                0.36f, 0.66f, 1f);

        if (g.shieldTimer > 0f) {
            float pulse = 0.45f + 0.35f * (float) Math.sin(g.time * 7f);
            mesh.octa(g.playerX, g.playerY, g.playerZ, 25f, 21f, 25f,
                    0.09f * pulse, 0.30f * pulse, 0.46f * pulse, true);
        }
    }

    private void particles(Game3D g) {
        for (Game3D.Particle p : g.particles) {
            float a = Math.max(0f, p.life / p.maxLife);
            float size = 2.4f + a * 5f;
            float r = Mesh.red(p.color) * a;
            float gr = Mesh.green(p.color) * a;
            float b = Mesh.blue(p.color) * a;
            mesh.sprite(p.x, p.y, p.z, size, right, up, r, gr, b);
        }
    }
}
