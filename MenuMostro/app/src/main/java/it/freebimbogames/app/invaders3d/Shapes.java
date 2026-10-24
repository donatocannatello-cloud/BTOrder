package it.freebimbogames.app.invaders3d;

/**
 * Ships, aliens and props as small baked triangle meshes in local space.
 *
 * <p>Each shape stores position and colour per vertex (6 floats); normals are
 * recomputed whenever an instance is placed in the world, so the same mesh can be
 * rotated and scaled freely. Everything is procedural: no model files, no textures.
 */
public final class Shapes {

    public static final float[] SHIP = buildShip();
    public static final float[] ALIEN_A = buildAlienA();
    public static final float[] ALIEN_B = buildAlienB();
    public static final float[] ALIEN_C = buildAlienC();
    public static final float[] BOSS = buildBoss();
    public static final float[] UFO = buildUfo();
    public static final float[] CAPSULE = buildCapsule();

    private Shapes() {
    }

    private static float[] finish(Mesh m) {
        return m.freeze(m.solidFloats(), false);
    }

    /** Player interceptor: long fuselage, swept wings, cockpit and engine block. */
    private static float[] buildShip() {
        Mesh m = new Mesh();
        m.octa(0f, 0f, 2f, 7f, 5f, 18f, 0.40f, 0.86f, 1f, false);
        m.box(0f, 4.4f, 3f, 4f, 2f, 7f, 0.78f, 0.97f, 1f, false);
        m.box(0f, 0f, -19f, 5f, 4f, 5f, 0.26f, 0.55f, 0.88f, false);
        // wings: thin prisms, mirrored
        wing(m, 1f);
        wing(m, -1f);
        // tail fin
        m.box(0f, 7f, -15f, 1.6f, 5f, 5f, 0.30f, 0.62f, 0.92f, false);
        return finish(m);
    }

    private static void wing(Mesh m, float side) {
        float a = 6f * side;
        float b = 30f * side;
        float rt = 0.28f, gt = 0.64f, bt = 1f;
        float refX = 18f * side;
        float inY = 2.5f;    // root, carried by the fuselage
        float outY = 7.5f;   // wing tip, raised: dihedral makes the shape read in 3D
        float thick = 1.8f;
        // top and bottom surfaces
        m.quad(a, inY, 10f, a, inY, -8f, b, outY, -14f, b, outY, 2f, rt, gt, bt, refX, 0f, 0f, false);
        m.quad(a, inY - thick, 9f, b, outY - thick, 1f, b, outY - thick, -13f, a, inY - thick, -7f,
                rt, gt, bt, refX, 0f, 0f, false);
        // leading edge
        m.quad(a, inY, 10f, b, outY, 2f, b, outY - thick, 1f, a, inY - thick, 9f,
                rt, gt, bt, refX, 0f, 0f, false);
        // trailing edge
        m.quad(b, outY, -14f, a, inY, -8f, a, inY - thick, -7f, b, outY - thick, -13f,
                rt, gt, bt, refX, 0f, 0f, false);
        // tip
        m.quad(b, outY, 2f, b, outY, -14f, b, outY - thick, -13f, b, outY - thick, 1f,
                rt, gt, bt, refX, 0f, 0f, false);
    }

    /** Front-line crab: rounded core with two side cannons. */
    private static float[] buildAlienA() {
        Mesh m = new Mesh();
        m.octa(0f, 0f, 0f, 9f, 7f, 9f, 0.88f, 0.92f, 0.98f, false);
        m.box(11f, 2f, 0f, 4f, 5f, 5f, 0.62f, 0.70f, 0.82f, false);
        m.box(-11f, 2f, 0f, 4f, 5f, 5f, 0.62f, 0.70f, 0.82f, false);
        m.box(4f, 3.5f, 7f, 2.4f, 2.4f, 2.4f, 1f, 0.95f, 0.55f, false);
        m.box(-4f, 3.5f, 7f, 2.4f, 2.4f, 2.4f, 1f, 0.95f, 0.55f, false);
        return finish(m);
    }

    /** Mid-line squid: rotating cube body with a domed head and four legs. */
    private static float[] buildAlienB() {
        Mesh m = new Mesh();
        m.box(0f, 0f, 0f, 8f, 6f, 8f, 0.88f, 0.92f, 0.98f, false);
        m.octa(0f, 7f, 0f, 5f, 4f, 5f, 0.70f, 0.78f, 0.90f, false);
        m.box(6f, -8f, 4f, 2f, 4f, 2f, 0.62f, 0.70f, 0.82f, false);
        m.box(-6f, -8f, 4f, 2f, 4f, 2f, 0.62f, 0.70f, 0.82f, false);
        m.box(6f, -8f, -4f, 2f, 4f, 2f, 0.62f, 0.70f, 0.82f, false);
        m.box(-6f, -8f, -4f, 2f, 4f, 2f, 0.62f, 0.70f, 0.82f, false);
        return finish(m);
    }

    /** Back-line beetle: wide flat disc with mandibles. */
    private static float[] buildAlienC() {
        Mesh m = new Mesh();
        m.octa(0f, 0f, 0f, 13f, 4.5f, 10f, 0.90f, 0.94f, 1f, false);
        m.box(0f, 4f, 0f, 4f, 3f, 4f, 0.68f, 0.76f, 0.88f, false);
        m.box(8f, -3f, 6f, 2.2f, 3.4f, 5.5f, 0.62f, 0.70f, 0.82f, false);
        m.box(-8f, -3f, 6f, 2.2f, 3.4f, 5.5f, 0.62f, 0.70f, 0.82f, false);
        return finish(m);
    }

    /** Five-wave boss: wide hull, bridge tower, gun pods and glowing eyes. */
    private static float[] buildBoss() {
        Mesh m = new Mesh();
        m.box(0f, 0f, 0f, 56f, 15f, 24f, 0.72f, 0.30f, 0.48f, false);
        m.octa(0f, 15f, 0f, 26f, 13f, 16f, 0.82f, 0.36f, 0.54f, false);
        m.box(62f, 0f, 0f, 16f, 8f, 16f, 0.60f, 0.24f, 0.40f, false);
        m.box(-62f, 0f, 0f, 16f, 8f, 16f, 0.60f, 0.24f, 0.40f, false);
        m.box(46f, -16f, 0f, 4f, 9f, 6f, 0.50f, 0.20f, 0.34f, false);
        m.box(-46f, -16f, 0f, 4f, 9f, 6f, 0.50f, 0.20f, 0.34f, false);
        m.box(26f, 4f, 22f, 8f, 4f, 3f, 1f, 0.92f, 0.32f, false);
        m.box(-26f, 4f, 22f, 8f, 4f, 3f, 1f, 0.92f, 0.32f, false);
        m.box(0f, -15f, 18f, 12f, 5f, 6f, 0.95f, 0.50f, 0.70f, false);
        return finish(m);
    }

    private static float[] buildUfo() {
        Mesh m = new Mesh();
        m.octa(0f, 0f, 0f, 26f, 6f, 26f, 0.95f, 0.82f, 0.25f, false);
        m.octa(0f, 7f, 0f, 11f, 8f, 11f, 0.85f, 0.95f, 1f, false);
        m.box(18f, -4f, 0f, 3f, 2f, 3f, 1f, 1f, 0.6f, false);
        m.box(-18f, -4f, 0f, 3f, 2f, 3f, 1f, 1f, 0.6f, false);
        return finish(m);
    }

    private static float[] buildCapsule() {
        Mesh m = new Mesh();
        m.octa(0f, 0f, 0f, 10f, 10f, 10f, 1f, 1f, 1f, false);
        m.box(0f, 0f, 0f, 4f, 4f, 4f, 0.05f, 0.05f, 0.10f, false);
        return finish(m);
    }
}
