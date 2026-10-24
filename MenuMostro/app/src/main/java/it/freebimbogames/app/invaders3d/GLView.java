package it.freebimbogames.app.invaders3d;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Real OpenGL ES 2.0 view. The simulation and all geometry work happen on the CPU
 * (see {@link Scene}); this class owns the EGL surface, one shader program, two
 * vertex buffers and the per-frame upload/draw.
 */
public final class GLView extends GLSurfaceView implements HudView.Clock {

    private static final String TAG = "InvadersGL";

    private static final String PREFS = "invaders3d";
    private static final String PREF_HIGH = "high_score";

    public final Game3D game;
    private final Sfx sfx;
    private final SharedPreferences prefs;
    private final Scene scene = new Scene(20261023L);
    private final Renderer renderer = new Renderer();

    private long lastFrameNanos;
    private long gameOverAt;

    public GLView(Context context) {
        super(context);
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        game = new Game3D(prefs.getInt(PREF_HIGH, 0));
        sfx = new Sfx(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
        setKeepScreenOn(true);
    }

    public void onResumeView() {
        onResume();
        lastFrameNanos = 0L;
        if (game.state != Game3D.State.READY && game.state != Game3D.State.GAME_OVER) {
            game.paused = true; // require an explicit tap after coming back
        }
    }

    public void onPauseView() {
        game.paused = true;
        onPause();
        saveHighScore();
    }

    public void release() {
        sfx.release();
    }

    public void saveHighScore() {
        int best = Math.max(game.highScore, game.score);
        if (best > prefs.getInt(PREF_HIGH, 0)) {
            prefs.edit().putInt(PREF_HIGH, best).apply();
        }
    }

    @Override
    public long gameOverAt() {
        return gameOverAt;
    }

    private void tick() {
        long now = SystemClock.elapsedRealtime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
        }
        float dt = (now - lastFrameNanos) / 1000f;
        lastFrameNanos = now;
        if (dt > 0.1f) {
            dt = 0.016f; // returning from a stall should not teleport anything
        }
        game.update(dt);

        for (int i = 0; i < game.events.size(); i++) {
            sfx.play(game.events.get(i));
        }
        game.events.clear();

        if (game.state == Game3D.State.GAME_OVER) {
            if (gameOverAt == 0L) {
                gameOverAt = now;
                saveHighScore();
            }
        } else {
            gameOverAt = 0L;
        }
    }

    private final class Renderer implements GLSurfaceView.Renderer {

        private int program;
        private int uMvp;
        private int uEye;
        private int uUnlit;
        private int aPos;
        private int aNormal;
        private int aColor;
        private int solidVbo;
        private int glowVbo;
        private FloatBuffer upload;
        private float aspect = 9f / 16f;
        private boolean ready;
        private final float[] eye = new float[3];

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            GLES20.glClearColor(0.010f, 0.014f, 0.032f, 1f);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthFunc(GLES20.GL_LEQUAL);

            int vs = compile(GLES20.GL_VERTEX_SHADER, Shaders.VERTEX);
            int fs = compile(GLES20.GL_FRAGMENT_SHADER, Shaders.FRAGMENT);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, vs);
            GLES20.glAttachShader(program, fs);
            GLES20.glLinkProgram(program);
            int[] status = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
            if (status[0] == 0) {
                Log.e(TAG, "link failed: " + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            GLES20.glDeleteShader(vs);
            GLES20.glDeleteShader(fs);

            uMvp = GLES20.glGetUniformLocation(program, "uMvp");
            uEye = GLES20.glGetUniformLocation(program, "uEye");
            uUnlit = GLES20.glGetUniformLocation(program, "uUnlit");
            aPos = GLES20.glGetAttribLocation(program, "aPos");
            aNormal = GLES20.glGetAttribLocation(program, "aNormal");
            aColor = GLES20.glGetAttribLocation(program, "aColor");

            int[] buffers = new int[2];
            GLES20.glGenBuffers(2, buffers, 0);
            solidVbo = buffers[0];
            glowVbo = buffers[1];
            upload = ByteBuffer.allocateDirect(1024 * 1024 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            ready = program != 0;
            lastFrameNanos = 0L;
        }

        private int compile(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] ok = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                Log.e(TAG, "shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
            }
            return shader;
        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            aspect = height == 0 ? 9f / 16f : width / (float) height;
            lastFrameNanos = 0L;
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            tick();

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            if (!ready) {
                return;
            }
            scene.build(game, aspect);

            GLES20.glUseProgram(program);
            GLES20.glUniformMatrix4fv(uMvp, 1, false, scene.mvpMatrix(), 0);
            Mat4.eyeFromView(scene.viewMatrix(), eye);
            GLES20.glUniform3f(uEye, eye[0], eye[1], eye[2]);

            drawPass(solidVbo, scene.mesh.solidData(), scene.mesh.solidFloats(), false);
            drawPass(glowVbo, scene.mesh.glowData(), scene.mesh.glowFloats(), true);
        }

        private void drawPass(int vbo, float[] data, int floats, boolean glow) {
            int verts = floats / Mesh.STRIDE;
            if (verts == 0) {
                return;
            }
            if (upload.capacity() < floats) {
                upload = ByteBuffer.allocateDirect(floats * 4)
                        .order(ByteOrder.nativeOrder()).asFloatBuffer();
            }
            upload.clear();
            upload.put(data, 0, floats);
            upload.position(0);

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, floats * 4, upload,
                    GLES20.GL_STREAM_DRAW);

            int stride = Mesh.STRIDE * 4;
            GLES20.glEnableVertexAttribArray(aPos);
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0);
            GLES20.glEnableVertexAttribArray(aNormal);
            GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, stride, 3 * 4);
            GLES20.glEnableVertexAttribArray(aColor);
            GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, stride, 6 * 4);

            if (glow) {
                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE);
                GLES20.glDepthMask(false);
                GLES20.glUniform1f(uUnlit, 1f);
            } else {
                GLES20.glUniform1f(uUnlit, 0f);
            }

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);

            if (glow) {
                GLES20.glDepthMask(true);
                GLES20.glDisable(GLES20.GL_BLEND);
            }
            GLES20.glDisableVertexAttribArray(aPos);
            GLES20.glDisableVertexAttribArray(aNormal);
            GLES20.glDisableVertexAttribArray(aColor);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }
}
