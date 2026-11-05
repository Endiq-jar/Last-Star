package com.laststar.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.MotionEvent;

import com.laststar.game.audio.SoundFx;
import com.laststar.game.core.Camera;
import com.laststar.game.core.Noise;
import com.laststar.game.gl.SceneRenderer;
import com.laststar.game.ui.Hud;
import com.laststar.game.ui.TouchControls;
import com.laststar.game.ui.UiBatch;
import com.laststar.game.ui.UiRenderer;
import com.laststar.game.world.InputState;
import com.laststar.game.world.Player;
import com.laststar.game.world.World;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The GL surface: owns the world, renderer, HUD and input plumbing, and runs the
 * fixed-step simulation from the render callback.
 */
public final class GameSurface extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final float FIXED_STEP = 1f / 60f;

    private final World world;
    private final Camera camera = new Camera();
    private final InputState input = new InputState();
    private final TouchControls controls = new TouchControls();
    private final SoundFx sound = new SoundFx();
    private final UiBatch uiBatch = new UiBatch();
    private final SceneRenderer renderer = new SceneRenderer();
    private final Hud hud;
    private final Prefs prefs;

    private UiRenderer uiRenderer;
    private boolean ready;
    private boolean paused;
    private float accumulator;
    private float lastFrameNanos;
    private int screenW = 1;
    private int screenH = 1;
    private boolean debugOverlay;
    private float nightAmount;
    private float runTime;

    public GameSurface(Context context) {
        super(context);
        // GLES2 keeps the APK compatible with every device from Android 5 up.
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        prefs = new Prefs(context);
        world = new World(20261105L);
        hud = new Hud(new com.laststar.game.ui.Font());
        hud.soundOn = !prefs.isMuted();
        controls.setLeftHanded(prefs.isLeftHanded());
        wireEvents();
    }

    private void wireEvents() {
        world.events = new World.EventListener() {
            @Override
            public void onMessage(String text, float seconds) {
            }

            @Override
            public void onShard(int collected, int total) {
                sound.play(SoundFx.Id.SHARD);
            }

            @Override
            public void onDefeat() {
                sound.play(SoundFx.Id.HURT, 1.4f);
            }

            @Override
            public void onVictory() {
                sound.play(SoundFx.Id.VICTORY);
            }
        };
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        try {
            renderer.init(world);
            uiRenderer = new UiRenderer();
            uiRenderer.init();
            sound.init();
            ready = true;
            camera.snapBehind(world);
        } catch (RuntimeException e) {
            ready = false;
            android.util.Log.e("LastStar", "Initialisation failed", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        screenW = Math.max(1, width);
        screenH = Math.max(1, height);
        android.opengl.GLES20.glViewport(0, 0, screenW, screenH);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? FIXED_STEP : (now - lastFrameNanos) / 1e9f;
        lastFrameNanos = now;
        // Clamp so a stall (app switch, GC pause) cannot teleport the player.
        dt = Math.min(dt, 0.1f);
        if (paused) {
            dt = 0f;
        }

        if (controls.pauseRequested) {
            controls.pauseRequested = false;
            if (world.phase == World.Phase.VICTORY) {
                world.phase = World.Phase.COMPLETE;
            }
            sound.play(SoundFx.Id.UI_TAP);
        }

        if (controls.soundToggleRequested) {
            controls.soundToggleRequested = false;
            boolean nowOn = !hud.soundOn;
            hud.soundOn = nowOn;
            prefs.setMuted(!nowOn);
            sound.setEnabled(nowOn);
            if (nowOn) {
                sound.play(SoundFx.Id.UI_TAP);
            }
        }

        accumulator += dt;
        int steps = 0;
        while (accumulator >= FIXED_STEP && steps < 5) {
            stepSimulation(FIXED_STEP);
            accumulator -= FIXED_STEP;
            steps++;
        }
        if (steps == 5) {
            accumulator = 0f;
        }

        if (!ready) {
            android.opengl.GLES20.glClearColor(0.05f, 0.06f, 0.09f, 1f);
            android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT);
            return;
        }

        runTime += dt;
        nightAmount = Noise.clamp01(world.shardsCollected / (float) World.SHARD_COUNT);
        float aspect = (float) screenW / (float) screenH;

        camera.update(dt, world, null, world.shakeAmount);
        renderer.prepareFrame(world, camera, aspect);
        renderer.draw(world, camera, aspect, dt, nightAmount);

        hud.update(dt);
        float uiScale = TouchControls.scaleFor(screenW, screenH);
        float vw = screenW * uiScale;
        float vh = screenH * uiScale;
        uiBatch.begin(Math.round(vw), Math.round(vh));
        hud.draw(uiBatch, world, controls, debugOverlay);
        uiRenderer.draw(uiBatch, screenW, screenH);
    }

    private void stepSimulation(float dt) {
        Player p = world.player;

        // Camera-relative movement uses the camera's yaw.
        input.cameraYaw = camera.movementYaw();
        world.update(dt, input);
        camera.applyLook(input.lookX * 0.85f, input.lookY * 0.85f);

        // Audio cues derived from state changes.
        if (p.attackCooldown > Player.ATTACK_COOLDOWN - dt * 1.5f && p.attackAnim > 0f
                && !attackSoundPlayed) {
            sound.play(SoundFx.Id.SWING);
            attackSoundPlayed = true;
        }
        if (p.attackAnim <= 0f) {
            attackSoundPlayed = false;
        }
        if (p.hitFlash > 0.75f) {
            sound.play(SoundFx.Id.HIT, 0.7f);
            p.hitFlash = 0.7f;
        }
        if (!wasOnGround && p.onGround) {
            sound.play(SoundFx.Id.LAND, 0.5f);
        }
        if (wasOnGround && !p.onGround && p.verticalSpeed > 2f) {
            sound.play(SoundFx.Id.JUMP, 0.55f);
        }
        wasOnGround = p.onGround;

        controls.update(dt);
        input.clearFrameEdges();
    }

    private boolean wasOnGround = true;
    private boolean attackSoundPlayed;

    // ----------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float scale = TouchControls.scaleFor(screenW, screenH);
        if (world.phase == World.Phase.COMPLETE) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                prefs.recordRun(world.shardsCollected, world.kills, true);
                world.restart();
                camera.snapBehind(world);
                nightAmount = 0f;
            }
            return true;
        }
        if (world.phase == World.Phase.INTRO) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                world.phase = World.Phase.PLAYING;
                world.phaseTimer = 0f;
                world.showMessage("Collect the 6 star shards", 3f, 1f);
            }
            return true;
        }
        controls.handleTouch(event, input, scale, scale);
        return true;
    }

    // ------------------------------------------------------------- callbacks

    public void onResumeGame() {
        paused = false;
        sound.setEnabled(hud.soundOn);
        onResume();
        lastFrameNanos = 0L;
    }

    public void onPauseGame() {
        paused = true;
        onPause();
        sound.setEnabled(false);
        // Bank the run so progress survives a kill or an app switch.
        prefs.recordRun(world.shardsCollected, world.kills, world.phase == World.Phase.COMPLETE);
    }

    public boolean toggleDebug() {
        debugOverlay = !debugOverlay;
        return debugOverlay;
    }

    public World world() {
        return world;
    }

    public Prefs prefs() {
        return prefs;
    }

    public Hud hud() {
        return hud;
    }
}
