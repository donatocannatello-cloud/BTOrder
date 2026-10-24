package it.freebimbogames.app.invaders3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Three-dimensional Space Invaders simulation in plain Java (no Android imports), so
 * the whole thing runs and is tested on a desktop JVM.
 *
 * <p>Axes: X is right, Y is up, Z points away from the camera into the screen. The
 * ship flies on all three (strafe, altitude, depth), the alien formation sways in X
 * and creeps towards the player along Z, and every hit test is a real 3D distance.
 */
public final class Game3D {

    // Sound events drained by the view.
    public static final int EV_SHOOT = 1;
    public static final int EV_ALIEN_HIT = 2;
    public static final int EV_PLAYER_HIT = 3;
    public static final int EV_UFO_HIT = 4;
    public static final int EV_LEVEL_UP = 5;
    public static final int EV_POWERUP = 6;
    public static final int EV_SHIELD_ABSORB = 7;
    public static final int EV_BOSS_HIT = 8;
    public static final int EV_BOSS_DOWN = 9;

    public enum State { READY, PLAYING, RESPAWN, LEVEL_CLEAR, GAME_OVER }

    // ---- flight envelope (world units) ----
    public static final float FIELD_X = 112f;
    public static final float FIELD_Y_TOP = 88f;
    public static final float FIELD_Y_BOTTOM = -62f;
    public static final float FIELD_Z = 62f;
    public static final float START_Y = -22f;
    public static final float PLAYER_R = 19f;

    // ---- alien formation ----
    public static final int COLS = 7;
    public static final int ROWS = 4;
    public static final int LAYERS = 2;
    public static final float SPACING_X = 36f;
    public static final float SPACING_Y = 40f;
    public static final float SPACING_Z = 78f;
    public static final float ALIEN_R = 14f;
    public static final float FORMATION_LIMIT = 46f;

    // ---- destructible bunkers ----
    public static final int SHIELD_COUNT = 3;
    public static final int SHIELD_COLS = 5;
    public static final int SHIELD_ROWS = 4;
    public static final float SHIELD_BLOCK = 11f;
    public static final float SHIELD_Z = 145f;
    public static final float SHIELD_TOP = 2f;

    // ---- power-ups ----
    public static final int PU_TRIPLE = 0;
    public static final int PU_SHIELD = 1;
    public static final int PU_PIERCE = 2;
    public static final float PU_DURATION = 10f;
    public static final float PU_DROP_CHANCE = 0.12f;
    public static final float PU_CATCH_R = 34f;

    // ---- boss ----
    public static final int BOSS_EVERY = 5;
    public static final float BOSS_Z = 250f;
    public static final float BOSS_HALF_X = 66f;
    public static final float BOSS_HALF_Y = 26f;
    public static final float BOSS_HALF_Z = 30f;

    public static final class Bullet {
        public float x, y, z, vx, vy, vz;
        public boolean fromPlayer;
        public int pierce;
        public float r;
    }

    public static final class Particle {
        public float x, y, z, vx, vy, vz, life, maxLife;
        public int color;
    }

    public static final class Shield {
        public float x;
        public final boolean[][] cells = new boolean[SHIELD_ROWS][SHIELD_COLS];
    }

    public static final class PowerUp {
        public int type;
        public float x, y, z;
    }

    // ---- public state read by renderer and HUD ----
    public volatile State state = State.READY;
    public volatile boolean paused;
    public volatile int score;
    public volatile int highScore;
    public volatile int lives = 3;
    public volatile int level = 1;

    public volatile float playerX;
    public volatile float playerY = START_Y;
    public volatile float playerZ;
    public volatile boolean playerAlive = true;
    public volatile boolean playerVisible = true;
    public volatile float invuln;

    private volatile float targetX;
    private volatile float targetY = START_Y;
    private volatile float targetZ;

    public final boolean[][][] alienAlive = new boolean[LAYERS][ROWS][COLS];
    public volatile int aliensAlive;
    public volatile float formationX;
    public volatile float formationY = 88f;
    public volatile float formationZ = 235f;
    public float formationDir = 1f;
    public volatile int animFrame;

    public volatile boolean ufoActive;
    public volatile float ufoX;
    public volatile float ufoY = 96f;
    public int ufoDir = 1;

    public volatile boolean bossActive;
    public volatile float bossX;
    public volatile float bossY = 62f;
    public volatile float bossZ = BOSS_Z;
    public float bossDir = 1f;
    public volatile int bossHp;
    public volatile int bossMaxHp;
    public volatile float bossHitFlash;
    public volatile float bossIntro;
    public volatile boolean lastWaveWasBoss;

    public volatile float tripleTimer;
    public volatile float shieldTimer;
    public volatile float pierceTimer;

    public final List<Shield> shields = new ArrayList<>();
    public final List<Bullet> bullets = new ArrayList<>();
    public final List<PowerUp> powerUps = new ArrayList<>();
    public final List<Particle> particles = new ArrayList<>();
    public final List<Integer> events = new ArrayList<>();

    public volatile float stateTimer;
    public volatile float shake;
    public volatile float time;

    private float fireCooldown;
    private float alienFireTimer;
    private float ufoTimer;
    private float animTimer;
    private float bossFireTimer;
    private float bossAimTimer;
    private final Random rnd = new Random(20261023L);

    public Game3D(int highScore) {
        this.highScore = highScore;
        playerX = 0f;
        targetX = 0f;
        fillFormation();
        buildShields();
    }

    // -------------------------------------------------------------- lifecycle

    public synchronized void startGame() {
        score = 0;
        lives = 3;
        level = 1;
        paused = false;
        shake = 0f;
        particles.clear();
        bullets.clear();
        powerUps.clear();
        tripleTimer = 0f;
        shieldTimer = 0f;
        pierceTimer = 0f;
        bossActive = false;
        bossIntro = 0f;
        bossHitFlash = 0f;
        bossHp = 0;
        bossMaxHp = 0;
        lastWaveWasBoss = false;
        buildShields();
        newWave();
        state = State.PLAYING;
    }

    public synchronized void togglePause() {
        paused = !paused;
    }

    // The simulation lists are not thread-safe: the UI thread only queues requests and
    // the render thread applies them at the top of its own update.
    private volatile boolean startRequested;
    private volatile boolean pauseRequested;

    public void requestStart() {
        startRequested = true;
    }

    public void requestPauseToggle() {
        pauseRequested = true;
    }

    /** Touch input: absolute target inside the flight envelope. */
    public void setTarget(float x, float y, float z) {
        targetX = clamp(x, -FIELD_X, FIELD_X);
        targetY = clamp(y, FIELD_Y_BOTTOM, FIELD_Y_TOP);
        targetZ = clamp(z, -FIELD_Z, FIELD_Z);
    }

    public void nudgeTargetX(float dx) {
        targetX = clamp(targetX + dx, -FIELD_X, FIELD_X);
    }

    public void nudgeTargetY(float dy) {
        targetY = clamp(targetY + dy, FIELD_Y_BOTTOM, FIELD_Y_TOP);
    }

    public void nudgeTargetZ(float dz) {
        targetZ = clamp(targetZ + dz, -FIELD_Z, FIELD_Z);
    }

    void newWave() {
        bullets.clear();
        powerUps.clear();
        ufoActive = false;
        ufoTimer = 9f + rnd.nextFloat() * 7f;
        alienFireTimer = 2.2f;
        fireCooldown = 0.2f;
        animTimer = 0f;
        animFrame = 0;
        bossActive = false;
        bossHitFlash = 0f;
        bossIntro = 0f;

        if (level % BOSS_EVERY == 0) {
            for (int l = 0; l < LAYERS; l++) {
                for (int r = 0; r < ROWS; r++) {
                    for (int c = 0; c < COLS; c++) {
                        alienAlive[l][r][c] = false;
                    }
                }
            }
            aliensAlive = 0;
            bossActive = true;
            bossMaxHp = 30 + 12 * bossTier();
            bossHp = bossMaxHp;
            bossX = 0f;
            bossY = 62f;
            bossZ = BOSS_Z;
            bossDir = rnd.nextBoolean() ? 1f : -1f;
            bossIntro = 1.8f;
            bossFireTimer = 0.5f;
            bossAimTimer = 2.2f;
        } else {
            fillFormation();
            formationX = 0f;
            formationY = 88f;
            formationZ = 235f + Math.min(level - 1, 4) * 6f;
            formationDir = 1f;
        }

        for (Shield s : shields) {
            for (int r = 0; r < SHIELD_ROWS; r++) {
                for (int c = 0; c < SHIELD_COLS; c++) {
                    s.cells[r][c] = shieldMask(r, c);
                }
            }
        }
        resetPlayer();
    }

    private void fillFormation() {
        aliensAlive = 0;
        for (int l = 0; l < LAYERS; l++) {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    alienAlive[l][r][c] = true;
                    aliensAlive++;
                }
            }
        }
    }

    private void resetPlayer() {
        playerX = 0f;
        playerY = START_Y;
        playerZ = 0f;
        targetX = 0f;
        targetY = START_Y;
        targetZ = 0f;
        playerAlive = true;
        playerVisible = true;
        invuln = 1.8f;
    }

    // -------------------------------------------------------------- geometry

    public static float alienX(float formationX, int layer, int col) {
        return formationX + (col - (COLS - 1) / 2f) * SPACING_X + layer * SPACING_X * 0.5f;
    }

    public static float alienY(float formationY, int row) {
        return formationY - row * SPACING_Y;
    }

    public static float alienZ(float formationZ, int layer) {
        return formationZ + layer * SPACING_Z;
    }

    public static int alienType(int row) {
        if (row <= 1) {
            return 0;
        }
        return row == 2 ? 1 : 2;
    }

    public static int rowScore(int row) {
        return row <= 1 ? 30 : (row == 2 ? 20 : 10);
    }

    public static int powerUpColor(int type) {
        switch (type) {
            case PU_TRIPLE:
                return 0xFFFFD93D;
            case PU_SHIELD:
                return 0xFF6EE7FF;
            default:
                return 0xFFFF4FD8;
        }
    }

    private void buildShields() {
        shields.clear();
        for (int i = 0; i < SHIELD_COUNT; i++) {
            Shield s = new Shield();
            s.x = (i - 1) * 92f - SHIELD_COLS * SHIELD_BLOCK / 2f;
            for (int r = 0; r < SHIELD_ROWS; r++) {
                for (int c = 0; c < SHIELD_COLS; c++) {
                    s.cells[r][c] = shieldMask(r, c);
                }
            }
            shields.add(s);
        }
    }

    static boolean shieldMask(int row, int col) {
        if (row == 0) {
            return col == 2;
        }
        if (row == 1) {
            return col >= 1 && col <= 3;
        }
        if (row == 3) {
            return col == 0 || col == SHIELD_COLS - 1;
        }
        return true;
    }

    // ---------------------------------------------------------------- update

    public void update(float rawDt) {
        if (startRequested) {
            startRequested = false;
            startGame();
        }
        if (pauseRequested) {
            pauseRequested = false;
            paused = !paused;
        }
        if (rawDt <= 0f) {
            return;
        }
        float dt = Math.min(rawDt, 0.05f);
        if (paused) {
            return;
        }
        time += dt;
        if (shake > 0f) {
            shake = Math.max(0f, shake - dt * 3.5f);
        }
        updateParticles(dt);

        switch (state) {
            case PLAYING:
                updatePlaying(dt);
                break;
            case RESPAWN:
                stateTimer -= dt;
                if (stateTimer <= 0f) {
                    if (lives > 0) {
                        resetPlayer();
                        state = State.PLAYING;
                    } else {
                        state = State.GAME_OVER;
                        playerAlive = false;
                        saveHighScore();
                    }
                }
                break;
            case LEVEL_CLEAR:
                stateTimer -= dt;
                if (stateTimer <= 0f) {
                    level++;
                    newWave();
                    state = State.PLAYING;
                }
                break;
            default:
                break;
        }
    }

    private void updatePlaying(float dt) {
        if (invuln > 0f) {
            invuln = Math.max(0f, invuln - dt);
        }
        playerVisible = playerAlive && (invuln <= 0f || ((int) (time * 12f) % 2 == 0));
        tickPowerUps(dt);
        updatePlayer(dt);
        updateFormation(dt);
        updateBoss(dt);
        updateBullets(dt);
        if (state != State.PLAYING) {
            return; // the player was hit this frame
        }
        updatePowerUps(dt);
        updateUfo(dt);

        animTimer += dt;
        if (animTimer >= alienAnimationInterval()) {
            animTimer = 0f;
            animFrame ^= 1;
        }

        if (aliensAlive > 0) {
            alienFireTimer -= dt;
            if (alienFireTimer <= 0f) {
                alienFire();
                float base = Math.max(0.30f, 1.10f - level * 0.055f);
                alienFireTimer = base * (0.55f + rnd.nextFloat() * 0.9f);
            }
        }

        if (bossActive) {
            if (bossHp <= 0) {
                bossDefeated();
            }
            return;
        }

        if (aliensAlive == 0) {
            lastWaveWasBoss = false;
            state = State.LEVEL_CLEAR;
            stateTimer = 2.0f;
            bullets.clear();
            ufoActive = false;
            events.add(EV_LEVEL_UP);
            return;
        }

        if (nearestAlienZ() <= playerZ + 100f) {
            invasion();
        }
    }

    private void updatePlayer(float dt) {
        if (!playerAlive) {
            return;
        }
        playerX = follow(playerX, targetX, 560f, 16f, dt);
        playerY = follow(playerY, targetY, 480f, 16f, dt);
        playerZ = follow(playerZ, targetZ, 420f, 14f, dt);

        fireCooldown -= dt;
        if (fireCooldown <= 0f) {
            fire();
            fireCooldown = Math.max(0.2f, 0.36f - level * 0.012f);
        }
    }

    private static float follow(float value, float target, float maxSpeed, float stiffness, float dt) {
        float diff = target - value;
        float step = diff * Math.min(1f, dt * stiffness);
        float limit = maxSpeed * dt;
        if (step > limit) {
            step = limit;
        } else if (step < -limit) {
            step = -limit;
        }
        return value + step;
    }

    private void fire() {
        float z = playerZ + 22f;
        if (tripleTimer > 0f) {
            addPlayerBullet(playerX, playerY, z, 0f);
            addPlayerBullet(playerX - 9f, playerY + 1f, z - 3f, -150f);
            addPlayerBullet(playerX + 9f, playerY - 1f, z - 3f, 150f);
        } else {
            addPlayerBullet(playerX, playerY, z, 0f);
        }
        events.add(EV_SHOOT);
    }

    private void addPlayerBullet(float x, float y, float z, float vx) {
        Bullet b = new Bullet();
        b.x = x;
        b.y = y;
        b.z = z;
        b.vx = vx;
        b.vz = 820f;
        b.fromPlayer = true;
        b.pierce = pierceTimer > 0f ? 2 : 0;
        b.r = 5f;
        bullets.add(b);
    }

    private float alienAnimationInterval() {
        float frac = aliensAlive / (float) (ROWS * COLS * LAYERS);
        return 0.12f + frac * 0.32f;
    }

    private void updateFormation(float dt) {
        if (aliensAlive == 0) {
            return;
        }
        float frac = aliensAlive / (float) (ROWS * COLS * LAYERS);
        float speed = (12f + level * 3.2f) * (1f + 2.4f * (1f - frac));
        speed = Math.min(speed, 150f);

        formationX += formationDir * speed * dt;
        formationZ -= (2.5f + level * 1.3f) * dt;

        if (formationDir > 0f && formationX >= FORMATION_LIMIT) {
            formationX = FORMATION_LIMIT;
            formationDir = -1f;
            formationZ -= 11f;
        } else if (formationDir < 0f && formationX <= -FORMATION_LIMIT) {
            formationX = -FORMATION_LIMIT;
            formationDir = 1f;
            formationZ -= 11f;
        }
    }

    private void updateBullets(float dt) {
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.z += b.vz * dt;

            if (b.z > 760f || b.z < -260f || b.y > 420f || b.y < -260f
                    || Math.abs(b.x) > 420f) {
                bullets.remove(i);
                continue;
            }
            if (b.fromPlayer) {
                if (hitAliens(b) || hitUfo(b) || hitBoss(b)) {
                    continue;
                }
                continue; // friendly shots pass through the bunkers
            }
            if (shieldTimer > 0f && dist2(b.x, b.y, b.z, playerX, playerY, playerZ) < 46f * 46f) {
                bullets.remove(i);
                spawnParticles(b.x, b.y, b.z, 0xFF6EE7FF, 8, 130f);
                events.add(EV_SHIELD_ABSORB);
                continue;
            }
            if (hitShields(b)) {
                continue;
            }
            if (playerAlive && invuln <= 0f
                    && dist2(b.x, b.y, b.z, playerX, playerY, playerZ)
                    < (PLAYER_R + b.r) * (PLAYER_R + b.r)) {
                hitPlayer();
                return; // every bullet is cleared on impact
            }
        }
    }

    private boolean hitAliens(Bullet b) {
        for (int l = 0; l < LAYERS; l++) {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (!alienAlive[l][r][c]) {
                        continue;
                    }
                    float ax = alienX(formationX, l, c);
                    float ay = alienY(formationY, r);
                    float az = alienZ(formationZ, l);
                    float reach = ALIEN_R + b.r;
                    if (dist2(b.x, b.y, b.z, ax, ay, az) > reach * reach) {
                        continue;
                    }
                    alienAlive[l][r][c] = false;
                    aliensAlive--;
                    score += rowScore(r) + Math.min(level - 1, 5) * 2;
                    saveHighScore();
                    spawnParticles(ax, ay, az, alienColor(r), 14, 150f);
                    events.add(EV_ALIEN_HIT);
                    maybeDropPowerUp(ax, ay, az);
                    if (b.pierce > 0) {
                        b.pierce--;
                    } else {
                        bullets.remove(b);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hitUfo(Bullet b) {
        if (!ufoActive) {
            return false;
        }
        if (dist2(b.x, b.y, b.z, ufoX, ufoY, 285f) > 30f * 30f) {
            return false;
        }
        ufoActive = false;
        score += 150;
        saveHighScore();
        spawnParticles(ufoX, ufoY, 285f, 0xFFFFD93D, 24, 210f);
        events.add(EV_UFO_HIT);
        dropPowerUp(ufoX, ufoY, 285f, rnd.nextInt(3));
        bullets.remove(b);
        return true;
    }

    private boolean hitBoss(Bullet b) {
        if (!bossActive || bossHp <= 0) {
            return false;
        }
        float dx = (b.x - bossX) / (BOSS_HALF_X + b.r);
        float dy = (b.y - bossY) / (BOSS_HALF_Y + b.r);
        float dz = (b.z - bossZ) / (BOSS_HALF_Z + b.r);
        if (dx * dx + dy * dy + dz * dz > 1f) {
            return false;
        }
        bossHp = Math.max(0, bossHp - (b.pierce > 0 ? 2 : 1));
        bossHitFlash = 1f;
        score += 10;
        saveHighScore();
        spawnParticles(b.x, b.y, b.z, 0xFFFF5C8A, 8, 150f);
        events.add(EV_BOSS_HIT);
        bullets.remove(b);
        return true;
    }

    private boolean hitShields(Bullet b) {
        for (Shield s : shields) {
            if (Math.abs(b.z - SHIELD_Z) > 10f + b.r) {
                continue;
            }
            int col = (int) Math.floor((b.x - s.x) / SHIELD_BLOCK);
            int row = (int) Math.floor((SHIELD_TOP - b.y) / SHIELD_BLOCK);
            if (col < 0 || col >= SHIELD_COLS || row < 0 || row >= SHIELD_ROWS) {
                continue;
            }
            boolean damaged = false;
            for (int r = Math.max(0, row - 1); r <= Math.min(SHIELD_ROWS - 1, row + 1); r++) {
                for (int c = Math.max(0, col - 1); c <= Math.min(SHIELD_COLS - 1, col + 1); c++) {
                    if (s.cells[r][c]) {
                        s.cells[r][c] = false;
                        damaged = true;
                    }
                }
            }
            if (damaged) {
                spawnParticles(b.x, b.y, SHIELD_Z, 0xFF4FE3A1, 7, 100f);
                bullets.remove(b);
                return true;
            }
        }
        return false;
    }

    private void hitPlayer() {
        lives--;
        playerAlive = false;
        spawnParticles(playerX, playerY, playerZ, 0xFF6EE7FF, 30, 230f);
        events.add(EV_PLAYER_HIT);
        shake = 1f;
        bullets.clear();
        ufoActive = false;
        state = State.RESPAWN;
        stateTimer = 1.6f;
        if (lives <= 0) {
            saveHighScore();
        }
    }

    private void invasion() {
        lives = 0;
        playerAlive = false;
        spawnParticles(playerX, playerY, playerZ, 0xFFFF5C5C, 34, 240f);
        events.add(EV_PLAYER_HIT);
        shake = 1f;
        state = State.GAME_OVER;
        stateTimer = 0f;
        saveHighScore();
    }

    private void saveHighScore() {
        if (score > highScore) {
            highScore = score;
        }
    }

    // -------------------------------------------------------------- power-ups

    private void tickPowerUps(float dt) {
        tripleTimer = Math.max(0f, tripleTimer - dt);
        shieldTimer = Math.max(0f, shieldTimer - dt);
        pierceTimer = Math.max(0f, pierceTimer - dt);
    }

    private void updatePowerUps(float dt) {
        for (int i = powerUps.size() - 1; i >= 0; i--) {
            PowerUp p = powerUps.get(i);
            p.z -= 95f * dt;
            p.y -= 8f * dt;
            if (p.z < playerZ - 160f) {
                powerUps.remove(i);
                continue;
            }
            if (playerAlive && dist2(p.x, p.y, p.z, playerX, playerY, playerZ)
                    < (PU_CATCH_R + PLAYER_R) * (PU_CATCH_R + PLAYER_R)) {
                activatePowerUp(p.type);
                powerUps.remove(i);
            }
        }
    }

    private void activatePowerUp(int type) {
        switch (type) {
            case PU_TRIPLE:
                tripleTimer = PU_DURATION;
                break;
            case PU_SHIELD:
                shieldTimer = PU_DURATION;
                break;
            default:
                pierceTimer = PU_DURATION;
                break;
        }
        score += 25;
        saveHighScore();
        spawnParticles(playerX, playerY, playerZ, powerUpColor(type), 18, 170f);
        events.add(EV_POWERUP);
    }

    private void maybeDropPowerUp(float x, float y, float z) {
        if (rnd.nextFloat() < PU_DROP_CHANCE) {
            dropPowerUp(x, y, z, rnd.nextInt(3));
        }
    }

    public void dropPowerUp(float x, float y, float z, int type) {
        PowerUp p = new PowerUp();
        p.x = x;
        p.y = y;
        p.z = Math.max(z, playerZ - 100f);
        p.type = type;
        powerUps.add(p);
    }

    // ------------------------------------------------------------------- boss

    public int bossTier() {
        return Math.max(1, level / BOSS_EVERY);
    }

    private void updateBoss(float dt) {
        if (!bossActive) {
            return;
        }
        if (bossIntro > 0f) {
            bossIntro = Math.max(0f, bossIntro - dt);
        }
        if (bossHitFlash > 0f) {
            bossHitFlash = Math.max(0f, bossHitFlash - dt * 8f);
        }

        float tier = bossTier();
        bossX += bossDir * (44f + 10f * tier) * dt;
        if (bossX < -72f) {
            bossX = -72f;
            bossDir = 1f;
        } else if (bossX > 72f) {
            bossX = 72f;
            bossDir = -1f;
        }
        bossY = 66f + (float) Math.sin(time * 1.3f) * 16f;
        bossZ = BOSS_Z + (float) Math.sin(time * 0.7f) * 20f;

        if (bossIntro > 0f || bossHp <= 0) {
            return;
        }

        boolean enraged = bossHp <= bossMaxHp * 0.4f;
        bossFireTimer -= dt;
        if (bossFireTimer <= 0f) {
            bossSpreadShot(tier);
            bossFireTimer = enraged ? 1.05f : 1.6f;
        }
        bossAimTimer -= dt;
        if (bossAimTimer <= 0f) {
            bossAimShot(tier);
            bossAimTimer = enraged ? 1.8f : 2.8f;
        }
    }

    private void bossSpreadShot(float tier) {
        float speed = 250f + 14f * tier;
        for (int i = -1; i <= 1; i++) {
            Bullet b = new Bullet();
            b.x = bossX;
            b.y = bossY - 8f;
            b.z = bossZ - 10f;
            b.vx = i * (105f + 10f * tier);
            b.vz = -speed;
            b.fromPlayer = false;
            b.r = 7f;
            bullets.add(b);
        }
    }

    private void bossAimShot(float tier) {
        float dx = playerX - bossX;
        float dy = playerY - bossY;
        float dz = playerZ - bossZ;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1f) {
            len = 1f;
        }
        float speed = 270f + 14f * tier;
        Bullet b = new Bullet();
        b.x = bossX;
        b.y = bossY;
        b.z = bossZ;
        b.vx = dx / len * speed;
        b.vy = dy / len * speed;
        b.vz = dz / len * speed;
        b.fromPlayer = false;
        b.r = 7f;
        bullets.add(b);
    }

    private void bossDefeated() {
        bossActive = false;
        bossIntro = 0f;
        lastWaveWasBoss = true;
        score += 400 + 250 * bossTier();
        saveHighScore();
        spawnParticles(bossX, bossY, bossZ, 0xFFFFD93D, 70, 280f);
        spawnParticles(bossX, bossY, bossZ, 0xFFFF5C8A, 50, 210f);
        shake = 1f;
        events.add(EV_BOSS_DOWN);
        bullets.clear();
        ufoActive = false;
        state = State.LEVEL_CLEAR;
        stateTimer = 2.6f;
    }

    // -------------------------------------------------------------------- UFO

    private void updateUfo(float dt) {
        if (ufoActive) {
            ufoX += ufoDir * 105f * dt;
            if (ufoX < -230f || ufoX > 230f) {
                ufoActive = false;
            }
            return;
        }
        ufoTimer -= dt;
        if (ufoTimer <= 0f && aliensAlive > 6) {
            ufoActive = true;
            ufoDir = rnd.nextBoolean() ? 1 : -1;
            ufoX = ufoDir > 0 ? -220f : 220f;
            ufoTimer = 17f + rnd.nextFloat() * 13f;
        }
    }

    // -------------------------------------------------------------- particles

    private void spawnParticles(float x, float y, float z, int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            if (particles.size() >= 260) {
                return;
            }
            Particle p = new Particle();
            double theta = rnd.nextDouble() * Math.PI * 2.0;
            double phi = Math.acos(2.0 * rnd.nextDouble() - 1.0);
            float s = speed * (0.25f + rnd.nextFloat() * 0.75f);
            p.x = x;
            p.y = y;
            p.z = z;
            p.vx = (float) (Math.sin(phi) * Math.cos(theta) * s);
            p.vy = (float) (Math.cos(phi) * s);
            p.vz = (float) (Math.sin(phi) * Math.sin(theta) * s);
            p.maxLife = 0.35f + rnd.nextFloat() * 0.45f;
            p.life = p.maxLife;
            p.color = color;
            particles.add(p);
        }
    }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0f) {
                particles.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.vx *= 0.93f;
            p.vy *= 0.93f;
            p.vz *= 0.93f;
        }
    }

    // ------------------------------------------------------------------ shots

    private void alienFire() {
        int[] candidates = new int[LAYERS * ROWS * COLS];
        int n = 0;
        for (int l = 0; l < LAYERS; l++) {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (alienAlive[l][r][c]) {
                        candidates[n++] = (l * ROWS + r) * COLS + c;
                    }
                }
            }
        }
        if (n == 0) {
            return;
        }
        int code = candidates[rnd.nextInt(n)];
        int c = code % COLS;
        int r = (code / COLS) % ROWS;
        int l = code / (COLS * ROWS);

        float sx = alienX(formationX, l, c);
        float sy = alienY(formationY, r) - 8f;
        float sz = alienZ(formationZ, l);
        // Aim with a spread: perfect tracking would make standing still instant death.
        float jitter = 16f + level * 1.5f;
        float dx = playerX - sx + (rnd.nextFloat() - 0.5f) * jitter * 2f;
        float dy = playerY - sy + (rnd.nextFloat() - 0.5f) * jitter;
        float dz = playerZ - sz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1f) {
            len = 1f;
        }
        float speed = 220f + level * 12f;
        Bullet b = new Bullet();
        b.x = sx;
        b.y = sy;
        b.z = sz;
        b.vx = dx / len * speed;
        b.vy = dy / len * speed;
        b.vz = dz / len * speed;
        b.fromPlayer = false;
        b.r = 6f;
        bullets.add(b);
    }

    // ---------------------------------------------------------------- helpers

    public int nearestAlienZ() {
        float best = Float.MAX_VALUE;
        for (int l = 0; l < LAYERS; l++) {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (alienAlive[l][r][c]) {
                        float z = alienZ(formationZ, l);
                        if (z < best) {
                            best = z;
                        }
                    }
                }
            }
        }
        return best == Float.MAX_VALUE ? -9999 : (int) best;
    }

    public int columnCount(int col) {
        int n = 0;
        for (int l = 0; l < LAYERS; l++) {
            for (int r = 0; r < ROWS; r++) {
                if (alienAlive[l][r][col]) {
                    n++;
                }
            }
        }
        return n;
    }

    public static int alienColor(int row) {
        switch (row) {
            case 0:
                return 0xFFB58CFF;
            case 1:
                return 0xFF4FE3A1;
            case 2:
                return 0xFFFFD93D;
            default:
                return 0xFFFF8A5C;
        }
    }

    private static float dist2(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = ax - bx;
        float dy = ay - by;
        float dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
