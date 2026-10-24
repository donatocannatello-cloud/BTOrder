package it.freebimbogames.app.invaders3d;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/**
 * Transparent 2D overlay on top of the GL surface: HUD, menus and touch controls.
 *
 * <p>Drawn as a topographic survey sheet: nested contour isolines with index contours
 * and quota labels, hachured stations for the touch pads, hollow ink glyphs instead of
 * filled chips, and a survey profile for the boss health. Left side of the screen drags
 * the ship in the screen plane (X and Y), the right side controls depth (Z); firing is
 * automatic. The layout is authored for an 800-unit-tall screen and scaled to the
 * device, so it fills any aspect ratio.
 */
public final class HudView extends View {

    /** Something that can report when the last run ended (used for the restart delay). */
    public interface Clock {
        long gameOverAt();
    }

    private static final int MODE_FLIGHT = 0;
    private static final int MODE_DEPTH = 1;

    // Cartographic ink palette: paper linework on a dark survey sheet.
    private static final int PAPER = 0xFFEDE4CE;
    private static final int PAPER_MID = 0xFFBDB9A6;
    private static final int PAPER_DIM = 0xFF7E877E;
    private static final int OCHRE = 0xFFD8B36A;
    private static final int MOSS = 0xFF9CC2A6;
    private static final int RUST = 0xFFD0715A;
    private static final int INK = 0xFF070C0E;

    private static final int RING_SEGMENTS = 64;

    private final Game3D game;
    private final Clock clock;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private float s = 1f;   // pixels per layout unit
    private int vw;         // view size, kept for the drawing pass
    private int vh;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean ticking;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            invalidate();
            if (ticking) {
                handler.postDelayed(this, 40L);
            }
        }
    };

    private final int[] ptrId = {-1, -1, -1, -1};
    private final int[] ptrMode = new int[4];
    private final float[] ptrX = new float[4];
    private final float[] ptrY = new float[4];
    private float padX;
    private float padY;

    public HudView(Context context, Game3D game, Clock clock) {
        super(context);
        this.game = game;
        this.clock = clock;
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        text.setTypeface(Typeface.MONOSPACE);
        setKeepScreenOn(true);
        setWillNotDraw(false);
    }

    public void startTicking() {
        if (!ticking) {
            ticking = true;
            handler.post(ticker);
        }
    }

    public void stopTicking() {
        ticking = false;
        handler.removeCallbacks(ticker);
    }

    // ---------------------------------------------------------------- drawing

    /** Caches the view size; touch handling needs it before the first frame too. */
    private void updateMetrics() {
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            vw = w;
            vh = h;
            s = h / 800f;
            padX = 92f * s;
            padY = vh - 132f * s;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        updateMetrics();
        if (vw == 0 || vh == 0) {
            return;
        }

        if (game.state != Game3D.State.READY && game.state != Game3D.State.GAME_OVER) {
            drawStatus(c);
            drawControls(c);
            drawPowerTimers(c);
        }
        drawOverlays(c);
    }

    // ------------------------------------------------------------- survey ink

    private static int ink(int color, int alpha) {
        int a = alpha < 0 ? 0 : (alpha > 255 ? 255 : alpha);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private void rule(Canvas c, float x1, float y1, float x2, float y2, int color, float width) {
        stroke.setColor(color);
        stroke.setStrokeWidth(width);
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    /** One irregular closed isoline: a circle bent by three harmonics so it reads as
     *  terrain, never as a geometric ring. */
    private void loopPath(float cx, float cy, float rx, float ry, float phase, float amp) {
        path.reset();
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            float a = (float) (i * 2.0 * Math.PI / RING_SEGMENTS);
            float wobble = 1f + amp * (0.55f * (float) Math.sin(3f * a + phase)
                    + 0.30f * (float) Math.sin(5f * a - phase * 1.7f)
                    + 0.15f * (float) Math.sin(9f * a + phase * 0.4f));
            float x = cx + (float) Math.cos(a) * rx * wobble;
            float y = cy + (float) Math.sin(a) * ry * wobble;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
    }

    /**
     * A nested set of isolines. Every third ring is an index contour: heavier and in
     * ochre, tagged with its quota, exactly like a hiking map.
     */
    private void isolines(Canvas c, float cx, float cy, float r0, float step, int rings,
                          float amp, float phase, int fineAlpha, int indexAlpha,
                          boolean quotas) {
        for (int i = 0; i < rings; i++) {
            float r = r0 + i * step;
            boolean index = i % 3 == 0;
            loopPath(cx, cy, r, r * 0.93f, phase + i * 0.62f, amp);
            stroke.setColor(ink(index ? OCHRE : PAPER, index ? indexAlpha : fineAlpha));
            stroke.setStrokeWidth((index ? 1.35f : 0.8f) * s);
            c.drawPath(path, stroke);

            if (!index) {
                continue;
            }
            // hachures: short inward strokes that read as a slope
            float ha = phase * 0.5f + i * 1.1f;
            for (int k = 0; k < 2; k++) {
                float a = ha + k * 2.2f;
                float ca = (float) Math.cos(a);
                float sa = (float) Math.sin(a);
                float x1 = cx + ca * r * 0.97f;
                float y1 = cy + sa * r * 0.90f;
                float x2 = cx + ca * r * 0.86f;
                float y2 = cy + sa * r * 0.80f;
                rule(c, x1, y1, x2, y2, ink(OCHRE, indexAlpha / 2), 0.8f * s);
            }
            if (quotas && i >= 3) {
                // Quotas are only set on the flanks of a ring and kept clear of the
                // sheet margins, so they never collide with the title block.
                float a = phase * 0.5f + i * 0.8f;
                float dirX = (float) Math.cos(a);
                float qx = cx + dirX * r;
                float qy = cy + (float) Math.sin(a) * r * 0.93f - 3f * s;
                if (Math.abs(dirX) > 0.45f && qx > 26f * s && qx < vw - 26f * s
                        && qy > 44f * s && qy < vh - 44f * s) {
                    spaced(c, Integer.toString(120 + i * 20), qx, qy, 7f * s,
                            ink(PAPER_MID, 0x66), Paint.Align.LEFT, 0.4f * s);
                }
            }
        }
    }

    /** The contour sheet behind the menus. */
    private void sheet(Canvas c) {
        float t = game.time;
        isolines(c, vw * 0.5f, vh * 0.40f, 40f * s, 50f * s, 13, 0.17f,
                t * 0.05f, 0x24, 0x3C, true);
        // a second, offset summit in the lower left corner
        isolines(c, vw * 0.12f, vh * 0.93f, 10f * s, 26f * s, 5, 0.22f,
                -t * 0.04f + 1.7f, 0x1C, 0x30, false);
    }

    /** L-shaped survey corners around a block of text. */
    private void cornerTicks(Canvas c, float l, float t, float r, float b, float len, int color) {
        rule(c, l, t, l + len, t, color, 1f * s);
        rule(c, l, t, l, t + len, color, 1f * s);
        rule(c, r - len, t, r, t, color, 1f * s);
        rule(c, r, t, r, t + len, color, 1f * s);
        rule(c, l, b - len, l, b, color, 1f * s);
        rule(c, l, b, l + len, b, color, 1f * s);
        rule(c, r - len, b, r, b, color, 1f * s);
        rule(c, r, b - len, r, b, color, 1f * s);
    }

    /** Hollow delta glyph, used for the remaining lives. */
    private void shipGlyph(Canvas c, float x, float y, float w, int color) {
        path.reset();
        path.moveTo(x, y - w * 0.85f);
        path.lineTo(x + w, y + w * 0.6f);
        path.lineTo(x, y + w * 0.14f);
        path.lineTo(x - w, y + w * 0.6f);
        path.close();
        stroke.setColor(color);
        stroke.setStrokeWidth(1.25f * s);
        c.drawPath(path, stroke);

        path.reset();
        path.moveTo(x, y - w * 0.45f);
        path.lineTo(x + w * 0.58f, y + w * 0.34f);
        path.lineTo(x - w * 0.58f, y + w * 0.34f);
        path.close();
        stroke.setColor(ink(color, 0x50));
        stroke.setStrokeWidth(0.8f * s);
        c.drawPath(path, stroke);
    }

    private void chevron(Canvas c, float x, float y, float w, boolean up, int color) {
        path.reset();
        if (up) {
            path.moveTo(x, y - w * 0.55f);
            path.lineTo(x + w * 0.7f, y + w * 0.35f);
            path.lineTo(x - w * 0.7f, y + w * 0.35f);
        } else {
            path.moveTo(x, y + w * 0.55f);
            path.lineTo(x + w * 0.7f, y - w * 0.35f);
            path.lineTo(x - w * 0.7f, y - w * 0.35f);
        }
        path.close();
        stroke.setColor(color);
        stroke.setStrokeWidth(1.15f * s);
        c.drawPath(path, stroke);
    }

    /**
     * Boss health as a survey profile: the whole silhouette is the full bar, the inked
     * part is what is left. Terrain height is deterministic, so damage reads clearly.
     */
    private void profile(Canvas c, float x, float y, float w, float h, float frac) {
        final int n = 44;
        path.reset();
        path.moveTo(x, y + h);
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            path.lineTo(x + w * u, y + h - h * profileHeight(u));
        }
        path.lineTo(x + w, y + h);
        path.close();

        stroke.setColor(ink(PAPER, 0x30));
        stroke.setStrokeWidth(0.9f * s);
        c.drawPath(path, stroke);

        if (frac > 0.001f) {
            c.save();
            c.clipRect(x, y - 2f * s, x + w * frac, y + h + 2f * s);
            fill.setColor(ink(RUST, 0x88));
            c.drawPath(path, fill);
            stroke.setColor(ink(OCHRE, 0xC0));
            stroke.setStrokeWidth(1.1f * s);
            c.drawPath(path, stroke);
            c.restore();
        }

        rule(c, x, y + h, x + w, y + h, ink(OCHRE, 0x90), 1f * s);
        float mx = x + w * frac;
        float my = y + h - h * profileHeight(frac);
        stroke.setColor(ink(PAPER, 0xD0));
        stroke.setStrokeWidth(1f * s);
        c.drawCircle(mx, my, 3.4f * s, stroke);
        fill.setColor(ink(PAPER, 0xE0));
        c.drawCircle(mx, my, 1.2f * s, fill);
    }

    private static float profileHeight(float u) {
        float v = 0.5f + 0.30f * (float) Math.sin(u * 11.4f + 1.2f)
                + 0.18f * (float) Math.sin(u * 25.7f - 0.7f);
        return Math.max(0.18f, Math.min(1f, v));
    }

    // ------------------------------------------------------------------ status

    private void drawStatus(Canvas c) {
        float m = 20f * s;
        float ml = 84f * s;

        cornerTicks(c, ml - 9f * s, 15f * s, ml + 106f * s, 68f * s, 9f * s, ink(OCHRE, 0x60));

        spaced(c, "PUNTI", ml, 30f * s, 9.5f * s, PAPER_DIM, Paint.Align.LEFT, 1.5f * s);
        text(c, pad(game.score), ml, 61f * s, 25f * s, PAPER, Paint.Align.LEFT);

        spaced(c, "RECORD", vw - m, 30f * s, 9.5f * s, PAPER_DIM, Paint.Align.RIGHT, 1.5f * s);
        text(c, pad(game.highScore), vw - m, 56f * s, 16f * s, OCHRE, Paint.Align.RIGHT);

        spaced(c, "LIVELLO " + two(game.level), vw / 2f, 33f * s, 10.5f * s, PAPER_MID,
                Paint.Align.CENTER, 1.4f * s);

        rule(c, ml, 76f * s, vw - m, 76f * s, ink(OCHRE, 0x38), 0.9f * s);

        if (game.bossActive && game.bossMaxHp > 0) {
            float barW = 250f * s;
            float barH = 20f * s;
            float bx = (vw - barW) / 2f;
            float by = 100f * s;
            float frac = Math.max(0f, Math.min(1f, game.bossHp / (float) game.bossMaxHp));
            spaced(c, "BOSS", vw / 2f, by - 6f * s, 8f * s, RUST, Paint.Align.CENTER, 2.2f * s);
            profile(c, bx, by, barW, barH, frac);
        }

        if (game.state == Game3D.State.PLAYING || game.state == Game3D.State.RESPAWN) {
            float px = vw - 50f * s;
            float py = 78f * s;
            float side = 30f * s;
            // pause: a hachured map station, same hit box as before
            stroke.setColor(ink(PAPER, 0x50));
            stroke.setStrokeWidth(0.9f * s);
            rect.set(px + 1f * s, py + 1f * s, px + side - 1f * s, py + side - 1f * s);
            c.drawRect(rect, stroke);
            stroke.setColor(ink(OCHRE, 0x66));
            rect.set(px + 5f * s, py + 5f * s, px + side - 5f * s, py + side - 5f * s);
            c.drawRect(rect, stroke);
            fill.setColor(ink(PAPER, 0xC0));
            c.drawRect(px + 10f * s, py + 10f * s, px + 13f * s, py + 21f * s, fill);
            c.drawRect(px + 17f * s, py + 10f * s, px + 20f * s, py + 21f * s, fill);
        }

        // remaining lives as tiny contour summits
        for (int i = 0; i < Math.max(0, game.lives); i++) {
            float lx = (30f + i * 30f) * s;
            float ly = vh - 29f * s;
            shipGlyph(c, lx, ly, 10f * s, ink(OCHRE, 0xE6));
        }
        spaced(c, "VITE", 14f * s, vh - 47f * s, 8.5f * s, PAPER_DIM, Paint.Align.LEFT, 1.4f * s);

        drawDepthGauge(c);
    }

    /** Vertical Z section line: graduations, index marks and a survey knob. */
    private void drawDepthGauge(Canvas c) {
        float x = vw - 26f * s;
        float top = 210f * s;
        float bottom = vh - 210f * s;
        float mid = (top + bottom) / 2f;
        float half = (bottom - top) / 2f;

        rule(c, x, top, x, bottom, ink(PAPER, 0x38), 0.9f * s);
        for (int i = 0; i <= 8; i++) {
            float ty = top + (bottom - top) * i / 8f;
            boolean index = i % 4 == 0;
            float len = (index ? 9f : 5f) * s;
            rule(c, x - len, ty, x, ty, ink(index ? OCHRE : PAPER, index ? 0x99 : 0x44),
                    (index ? 1.3f : 0.8f) * s);
        }

        float z = Math.max(-1f, Math.min(1f, game.playerZ / Game3D.FIELD_Z));
        float ky = mid - z * half;
        fill.setColor(ink(INK, 0xF0));
        c.drawCircle(x, ky, 6f * s, fill);
        stroke.setColor(ink(OCHRE, 0xE6));
        stroke.setStrokeWidth(1.3f * s);
        c.drawCircle(x, ky, 6.5f * s, stroke);
        stroke.setColor(ink(PAPER, 0x80));
        stroke.setStrokeWidth(0.8f * s);
        c.drawCircle(x, ky, 3.2f * s, stroke);
        fill.setColor(ink(PAPER, 0xE8));
        c.drawCircle(x, ky, 1.4f * s, fill);

        spaced(c, "AV.", x - 4f * s, top - 12f * s, 8f * s, PAPER_DIM, Paint.Align.CENTER, 1f * s);
        spaced(c, "DIE.", x - 4f * s, bottom + 20f * s, 8f * s, PAPER_DIM, Paint.Align.CENTER, 1f * s);
    }

    private void drawPowerTimers(Canvas c) {
        float y = vh - 62f * s;
        float right = vw - 148f * s;
        int[] types = {Game3D.PU_PIERCE, Game3D.PU_SHIELD, Game3D.PU_TRIPLE};
        float[] timers = {game.pierceTimer, game.shieldTimer, game.tripleTimer};
        for (int i = 0; i < types.length; i++) {
            if (timers[i] <= 0f) {
                continue;
            }
            float w = 62f * s;
            right -= w;
            int color = Game3D.powerUpColor(types[i]);
            stroke.setColor(ink(color, 0x70));
            stroke.setStrokeWidth(0.9f * s);
            rect.set(right, y, right + w, y + 22f * s);
            c.drawRect(rect, stroke);
            cornerTicks(c, right - 2f * s, y - 2f * s, right + w + 2f * s, y + 24f * s,
                    4f * s, ink(color, 0x90));
            spaced(c, powerLabel(types[i]), right + w / 2f, y + 10.5f * s, 7.5f * s, color,
                    Paint.Align.CENTER, 0.9f * s);
            // remaining time as a graduated strip
            float frac = Math.min(1f, timers[i] / Game3D.PU_DURATION);
            int ticks = 11;
            for (int k = 0; k < ticks; k++) {
                float tx = right + 5f * s + (w - 10f * s) * k / (float) (ticks - 1);
                boolean lit = k / (float) (ticks - 1) <= frac + 1e-4f;
                rule(c, tx, y + 15f * s, tx, y + (lit ? 20f : 17f) * s,
                        ink(color, lit ? 0xB0 : 0x30), 1f * s);
            }
            right -= 7f * s;
        }
    }

    private static String powerLabel(int type) {
        switch (type) {
            case Game3D.PU_TRIPLE:
                return "TRIPLO";
            case Game3D.PU_SHIELD:
                return "SCUDO";
            default:
                return "PERFO";
        }
    }

    private void drawControls(Canvas c) {
        // flight station: three isolines, a crosshair and a summit dot
        isolines(c, padX, padY, 17f * s, 13f * s, 3, 0.12f, game.time * 0.35f, 0x3E, 0x6A, false);
        rule(c, padX - 11f * s, padY, padX + 11f * s, padY, ink(PAPER, 0x48), 0.9f * s);
        rule(c, padX, padY - 11f * s, padX, padY + 11f * s, ink(PAPER, 0x48), 0.9f * s);
        fill.setColor(ink(OCHRE, 0xB0));
        c.drawCircle(padX, padY, 1.9f * s, fill);
        spaced(c, "VOLO", padX, padY + 64f * s, 8.5f * s, PAPER_DIM, Paint.Align.CENTER, 1.3f * s);

        float dx = vw - 92f * s;
        float dy = vh - 132f * s;
        isolines(c, dx, dy, 13f * s, 14f * s, 3, 0.14f, -game.time * 0.3f, 0x36, 0x5E, false);
        chevron(c, dx, dy - 13f * s, 10f * s, true, ink(PAPER, 0x90));
        chevron(c, dx, dy + 13f * s, 10f * s, false, ink(PAPER, 0x50));
        spaced(c, "PROFONDITA'", dx, dy + 62f * s, 8f * s, PAPER_DIM, Paint.Align.CENTER, 1.1f * s);
    }

    // ---------------------------------------------------------------- overlays

    private void drawOverlays(Canvas c) {
        float cx = vw / 2f;
        float cy = vh / 2f;

        if (game.state == Game3D.State.PLAYING && game.bossIntro > 0f) {
            spaced(c, "ONDATA BOSS", cx, cy - 34f * s, 24f * s, RUST, Paint.Align.CENTER, 3f * s);
            rule(c, cx - 100f * s, cy - 20f * s, cx + 100f * s, cy - 20f * s, ink(RUST, 0x70), 1f * s);
            if (((int) (game.time * 3f) % 2) == 0) {
                spaced(c, "ATTENZIONE", cx, cy + 12f * s, 11f * s, OCHRE, Paint.Align.CENTER, 2.4f * s);
            }
        }
        if (game.paused) {
            dim(c, 0xE0);
            sheet(c);
            spaced(c, "PAUSA", cx, cy - 6f * s, 40f * s, PAPER, Paint.Align.CENTER, 6f * s);
            rule(c, cx - 96f * s, cy + 12f * s, cx + 96f * s, cy + 12f * s, ink(OCHRE, 0x60), 1f * s);
            spaced(c, "TOCCA PER CONTINUARE", cx, cy + 38f * s, 10.5f * s, PAPER_MID,
                    Paint.Align.CENTER, 1.8f * s);
            return;
        }
        switch (game.state) {
            case READY:
                dim(c, 0xEA);
                sheet(c);
                spaced(c, "MONSTER", cx, cy - 178f * s, 34f * s, PAPER, Paint.Align.CENTER, 5f * s);
                spaced(c, "INVADERS", cx, cy - 136f * s, 30f * s, PAPER, Paint.Align.CENTER, 4f * s);
                spaced(c, "3D", cx, cy - 92f * s, 22f * s, OCHRE, Paint.Align.CENTER, 6f * s);
                rule(c, cx - 118f * s, cy - 74f * s, cx + 118f * s, cy - 74f * s, ink(OCHRE, 0x70), 1f * s);
                rule(c, cx - 118f * s, cy - 70f * s, cx + 118f * s, cy - 70f * s, ink(OCHRE, 0x30), 1f * s);
                spaced(c, "SINISTRA: TRASCINA PER VOLARE", cx, cy + 4f * s, 9.5f * s, PAPER_MID,
                        Paint.Align.CENTER, 1.1f * s);
                spaced(c, "DESTRA: SU/GIU' PER LA PROFONDITA'", cx, cy + 24f * s, 9.5f * s, PAPER_MID,
                        Paint.Align.CENTER, 1.1f * s);
                spaced(c, "FUOCO AUTOMATICO", cx, cy + 44f * s, 9.5f * s, PAPER_DIM,
                        Paint.Align.CENTER, 1.1f * s);
                if (((int) (game.time * 2f) % 2) == 0) {
                    spaced(c, "TOCCA PER INIZIARE", cx, cy + 112f * s, 14f * s, OCHRE,
                            Paint.Align.CENTER, 3f * s);
                }
                spaced(c, "RECORD  " + pad(game.highScore), cx, cy + 178f * s, 10f * s, PAPER_DIM,
                        Paint.Align.CENTER, 1.4f * s);
                break;
            case GAME_OVER:
                dim(c, 0xEA);
                sheet(c);
                spaced(c, "GAME OVER", cx, cy - 76f * s, 32f * s, RUST, Paint.Align.CENTER, 5f * s);
                rule(c, cx - 118f * s, cy - 58f * s, cx + 118f * s, cy - 58f * s, ink(RUST, 0x70), 1f * s);
                spaced(c, "PUNTEGGIO", cx, cy - 22f * s, 9.5f * s, PAPER_DIM, Paint.Align.CENTER, 1.8f * s);
                text(c, pad(game.score), cx, cy + 18f * s, 32f * s, PAPER, Paint.Align.CENTER);
                spaced(c, "LIVELLO RAGGIUNTO " + two(game.level), cx, cy + 52f * s, 9.5f * s,
                        PAPER_MID, Paint.Align.CENTER, 1.2f * s);
                if (((int) (game.time * 2f) % 2) == 0) {
                    spaced(c, "TOCCA PER RIGIOCARE", cx, cy + 118f * s, 13f * s, OCHRE,
                            Paint.Align.CENTER, 2.6f * s);
                }
                break;
            case LEVEL_CLEAR:
                dim(c, 0xD8);
                sheet(c);
                if (game.lastWaveWasBoss) {
                    spaced(c, "BOSS SCONFITTO", cx, cy - 8f * s, 25f * s, MOSS, Paint.Align.CENTER, 3f * s);
                    spaced(c, "LIVELLO " + two(game.level + 1), cx, cy + 30f * s, 11f * s, PAPER_MID,
                            Paint.Align.CENTER, 2f * s);
                } else {
                    spaced(c, "LIVELLO " + two(game.level + 1), cx, cy - 8f * s, 30f * s, MOSS,
                            Paint.Align.CENTER, 4f * s);
                    spaced(c, "PREPARATI", cx, cy + 30f * s, 11f * s, PAPER_MID, Paint.Align.CENTER, 2f * s);
                }
                rule(c, cx - 92f * s, cy + 8f * s, cx + 92f * s, cy + 8f * s, ink(MOSS, 0x50), 1f * s);
                break;
            case RESPAWN:
                if (game.lives <= 0) {
                    dim(c, 0xD0);
                }
                break;
            default:
                break;
        }
    }

    private void dim(Canvas c, int alpha) {
        fill.setColor((alpha << 24) | (INK & 0x00FFFFFF));
        c.drawRect(0f, 0f, vw, vh, fill);
    }

    private void text(Canvas c, String s2, float x, float y, float size, int color, Paint.Align align) {
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(align);
        c.drawText(s2, x, y, text);
    }

    /** Letterspaced label: mail surveys and maps never set type tight. */
    private void spaced(Canvas c, String s2, float x, float y, float size, int color,
                        Paint.Align align, float track) {
        text.setTextSize(size);
        text.setColor(color);
        text.setTextAlign(Paint.Align.LEFT);
        float width = 0f;
        for (int i = 0; i < s2.length(); i++) {
            width += text.measureText(s2, i, i + 1) + track;
        }
        width -= track;
        float cursor = align == Paint.Align.LEFT ? x
                : (align == Paint.Align.RIGHT ? x - width : x - width / 2f);
        for (int i = 0; i < s2.length(); i++) {
            c.drawText(s2, i, i + 1, cursor, y, text);
            cursor += text.measureText(s2, i, i + 1) + track;
        }
        text.setTextAlign(Paint.Align.LEFT);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private static String pad(int value) {
        String s2 = Integer.toString(Math.max(0, value));
        while (s2.length() < 6) {
            s2 = "0" + s2;
        }
        return s2;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        updateMetrics();
        int action = e.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int index = e.getActionIndex();
            float px = e.getX(index);
            float py = e.getY(index);

            if (action == MotionEvent.ACTION_DOWN && isPauseHit(px, py)
                    && (game.state == Game3D.State.PLAYING || game.state == Game3D.State.RESPAWN)) {
                game.requestPauseToggle();
                return true;
            }
            if (game.paused) {
                game.requestPauseToggle();
                return true;
            }
            if (game.state == Game3D.State.READY) {
                game.requestStart();
                return true;
            }
            if (game.state == Game3D.State.GAME_OVER) {
                long at = clock.gameOverAt();
                if (at == 0L || SystemClock.elapsedRealtime() - at > 600L) {
                    game.requestStart();
                }
                return true;
            }
            int slot = freeSlot();
            if (slot >= 0) {
                ptrId[slot] = e.getPointerId(index);
                ptrX[slot] = px;
                ptrY[slot] = py;
                ptrMode[slot] = px < vw * 0.56f ? MODE_FLIGHT : MODE_DEPTH;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                int slot = slotOf(e.getPointerId(i));
                if (slot < 0) {
                    continue;
                }
                float px = e.getX(i);
                float py = e.getY(i);
                float dx = px - ptrX[slot];
                float dy = py - ptrY[slot];
                ptrX[slot] = px;
                ptrY[slot] = py;
                if (ptrMode[slot] == MODE_FLIGHT) {
                    // The camera looks along +Z with +Y up, which puts world +X on the
                    // LEFT of the screen (see Mat4.lookAt). Screen-space drag is therefore
                    // mapped with a flipped X so the ship follows the thumb.
                    game.nudgeTargetX(-dx / s * 1.05f);
                    game.nudgeTargetY(-dy / s * 1.05f);
                } else {
                    game.nudgeTargetZ(-dy / s * 0.75f);
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                || action == MotionEvent.ACTION_CANCEL) {
            int slot = slotOf(e.getPointerId(e.getActionIndex()));
            if (slot >= 0) {
                ptrId[slot] = -1;
            }
            return true;
        }
        return true;
    }

    private boolean isPauseHit(float x, float y) {
        return x >= vw - 50f * s && x <= vw - 20f * s && y >= 78f * s && y <= 108f * s;
    }

    private int freeSlot() {
        for (int i = 0; i < ptrId.length; i++) {
            if (ptrId[i] < 0) {
                return i;
            }
        }
        return -1;
    }

    private int slotOf(int id) {
        for (int i = 0; i < ptrId.length; i++) {
            if (ptrId[i] == id) {
                return i;
            }
        }
        return -1;
    }
}
