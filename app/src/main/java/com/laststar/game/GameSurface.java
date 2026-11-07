package com.laststar.game;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

import com.laststar.game.audio.SoundFx;
import com.laststar.game.core.Camera;
import com.laststar.game.core.DayNightCycle;
import com.laststar.game.gl.GlCapabilities;
import com.laststar.game.gl.SceneRenderer;
import com.laststar.game.ui.Hud;
import com.laststar.game.ui.Menu;
import com.laststar.game.ui.TouchControls;
import com.laststar.game.ui.UiBatch;
import com.laststar.game.ui.UiRenderer;
import com.laststar.game.world.InputState;
import com.laststar.game.world.Player;
import com.laststar.game.world.WeatherSystem;
import com.laststar.game.world.World;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
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
    /** Where the run is kept between sessions. */
    private final com.laststar.game.world.SaveFile save;
    private final Menu menu = new Menu();
    /** True while the out-of-game menus are showing. */
    private boolean inMenu = true;

    private UiRenderer uiRenderer;
    private boolean ready;
    private boolean worldReady;
    private boolean uiReady;
    /** Shown on screen when start-up degraded, so a bug is never a blank page. */
    private String fatalError;
    private boolean paused;
    private float accumulator;
    private float lastFrameNanos;
    private int screenW = 1;
    private int screenH = 1;
    private boolean debugOverlay;
    private float nightAmount;

    private static final class FallbackEglConfigChooser implements GLSurfaceView.EGLConfigChooser {
        private static final int EGL_RENDERABLE_TYPE = 0x3040;
        private static final int EGL_SURFACE_TYPE = 0x3033;
        private static final int EGL_WINDOW_BIT = 0x0004;
        private static final int EGL_OPENGL_ES2_BIT = 0x0004;
        private static final int EGL_OPENGL_ES3_BIT_KHR = 0x0040;
        private static final int EGL_RED_SIZE = 0x3024;
        private static final int EGL_GREEN_SIZE = 0x3023;
        private static final int EGL_BLUE_SIZE = 0x3022;
        private static final int EGL_ALPHA_SIZE = 0x3021;
        private static final int EGL_DEPTH_SIZE = 0x3025;
        private static final int EGL_NONE = 0x3038;

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int both = EGL_OPENGL_ES2_BIT | EGL_OPENGL_ES3_BIT_KHR;
            EGLConfig config = choose(egl, display, both);
            if (config != null) return config;
            config = choose(egl, display, EGL_OPENGL_ES2_BIT);
            if (config != null) return config;
            throw new IllegalArgumentException("No EGL config supports GLES2 or GLES3 rendering");
        }

        private EGLConfig choose(EGL10 egl, EGLDisplay display, int renderable) {
            int[] spec = {
                    EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
                    EGL_ALPHA_SIZE, 0, EGL_DEPTH_SIZE, 16,
                    EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                    EGL_RENDERABLE_TYPE, renderable,
                    EGL_NONE
            };
            int[] count = new int[1];
            if (!egl.eglChooseConfig(display, spec, null, 0, count) || count[0] <= 0) return null;
            EGLConfig[] configs = new EGLConfig[count[0]];
            if (!egl.eglChooseConfig(display, spec, configs, configs.length, count)
                    || count[0] <= 0) return null;
            return configs[0];
        }
    }

    private static final class FallbackEglContextFactory implements GLSurfaceView.EGLContextFactory {
        private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private static final int EGL_CONTEXT_MINOR_VERSION_KHR = 0x30FB;
        private static final int EGL_NONE = 0x3038;

        @Override
        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig config) {
            String extensions = egl.eglQueryString(display, EGL10.EGL_EXTENSIONS);
            boolean khrCreateContext = extensions != null
                    && extensions.contains("EGL_KHR_create_context");
            if (khrCreateContext) {
                EGLContext context = attempt(egl, display, config, new int[]{
                        EGL_CONTEXT_CLIENT_VERSION, 3, EGL_CONTEXT_MINOR_VERSION_KHR, 2, EGL_NONE});
                if (context != null) return context;
                context = attempt(egl, display, config, new int[]{
                        EGL_CONTEXT_CLIENT_VERSION, 3, EGL_CONTEXT_MINOR_VERSION_KHR, 1, EGL_NONE});
                if (context != null) return context;
            }

            EGLContext context = attempt(egl, display, config,
                    new int[]{EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE});
            if (context != null) return context;
            context = attempt(egl, display, config,
                    new int[]{EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE});
            if (context != null) return context;
            throw new IllegalStateException("EGL could not create GLES 3.2, 3.1, 3.0 or 2.0 context");
        }

        private EGLContext attempt(EGL10 egl, EGLDisplay display, EGLConfig config, int[] attrs) {
            EGLContext context = egl.eglCreateContext(display, config, EGL10.EGL_NO_CONTEXT, attrs);
            if (context == null || context == EGL10.EGL_NO_CONTEXT) return null;
            return context;
        }

        @Override
        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            egl.eglDestroyContext(display, context);
        }
    }

    public GameSurface(Context context) {
        super(context);
        // Prefer the highest ES profile the EGL driver can actually create,
        // falling back through ES3.1/3.0 to the proven ES2 compatibility path.
        setEGLConfigChooser(new FallbackEglConfigChooser());
        setEGLContextFactory(new FallbackEglContextFactory());
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        // A View with only onTouchEvent overridden does not reliably receive
        // touch events: the framework will not dispatch to it unless it is
        // clickable and focusable, and with setContentView(surface) there is no
        // parent to route them either. Without these two lines the whole game
        // renders perfectly and no input ever arrives -- the exact symptom of
        // "the home screen shows but nothing works".
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();

        // Belt and braces: route through a listener as well. If onTouchEvent
        // is somehow bypassed by a device's input stack, this still fires.
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return handleTouch(event);
            }
        });

        prefs = new Prefs(context);
        save = new com.laststar.game.world.SaveFile(context);
        world = new World(20261105L);
        // Put the player back where they left off. A rejected or missing save
        // simply means a fresh run, which is the right answer in both cases.
        if (save.load(world)) {
            world.restoreProgressNote();
        }
        hud = new Hud(new com.laststar.game.ui.Font());
        hud.soundOn = !prefs.isMuted();
        controls.setLeftHanded(prefs.isLeftHanded());
        menu.load(prefs);
        menu.bind(world);
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
                // A shard is the longest stretch between safe points, so it is
                // also the natural place to bank.
                save.save(world);
            }

            @Override
            public void onDefeat() {
                sound.play(SoundFx.Id.HURT, 1.4f);
            }

            @Override
            public void onVictory() {
                sound.play(SoundFx.Id.VICTORY);
                save.save(world);
            }
        };
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Log an actual-context capability snapshot for device diagnostics. Do
        // not select an ES3 renderer solely because the driver advertises it:
        // this scene still uses the GLES2 client-array compatibility backend.
        GlCapabilities glCaps = GlCapabilities.detectCurrentContext();
        android.util.Log.i("LastStar", "GL capabilities: " + glCaps.summary());

        // Initialise in two halves. The world renderer failing is fatal, but the
        // UI renderer failing must not be: if only the UI is broken the player
        // still gets a world and an on-screen error, instead of a black surface.
        try {
            renderer.init(world, getContext().getAssets());
            worldReady = true;
        } catch (RuntimeException e) {
            worldReady = false;
            fatalError = "world renderer: " + e.getMessage();
            android.util.Log.e("LastStar", "world init failed", e);
        }
        try {
            uiRenderer = new UiRenderer();
            uiRenderer.init();
            uiReady = true;
        } catch (RuntimeException e) {
            uiReady = false;
            if (fatalError == null) {
                fatalError = "ui renderer: " + e.getMessage();
            }
            android.util.Log.e("LastStar", "ui init failed", e);
        }
        try {
            sound.init();
        } catch (RuntimeException e) {
            // Audio is a nicety; a device without a usable track still plays.
            android.util.Log.w("LastStar", "audio unavailable: " + e.getMessage());
        }
        ready = worldReady;
        if (ready) {
            camera.snapBehind(world);
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
            // The pause button opens the menus on the settings page.
            menu.load(prefs);
            menu.page = Menu.Page.SETTINGS;
            inMenu = true;
            prefs.recordRun(world.shardsCollected, world.kills,
                    world.phase == World.Phase.COMPLETE);
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
            drawStartupFailure();
            return;
        }

        nightAmount = DayNightCycle.nightAmount(world.worldTime);
        float aspect = (float) screenW / (float) screenH;

        camera.update(dt, world, null, world.shakeAmount);
        renderer.prepareFrame(world, camera, aspect);
        renderer.draw(world, camera, aspect, dt, nightAmount);

        float uiScale = TouchControls.scaleFor(screenW, screenH);
        float vw = screenW * uiScale;
        float vh = screenH * uiScale;
        UiBatch batch = uiBatch;
        batch.begin(Math.round(vw), Math.round(vh));

        if (!uiReady) {
            // World is fine but the interface is not: still draw the world, and
            // say so on screen rather than leaving a silent, untappable view.
            drawErrorText(batch);
            return;
        }

        if (inMenu) {
            menu.update(dt);
            menu.draw(batch, uiRenderer.font());
            if (menu.startRequested) {
                menu.startRequested = false;
                inMenu = false;
                world.difficulty = menu.settings.difficulty;
                world.player.skinTone = menu.wardrobe.selectedSwatch;
                world.beginRun();
                world.player.skinTone = menu.wardrobe.selectedSwatch;
                camera.snapBehind(world);
                nightAmount = 0f;
                sound.play(SoundFx.Id.UI_TAP);
            }
            uiRenderer.draw(batch, screenW, screenH);
            return;
        }

        drawWeather(batch, vw, vh);
        hud.update(dt);
        hud.draw(batch, world, controls, debugOverlay);
        uiRenderer.draw(batch, screenW, screenH);
    }

    /**
     * The screen shown when the world renderer could not start. It must never be
     * a flat colour: a blank screen is indistinguishable from a frozen app, and
     * that is exactly how a shader bug shipped unnoticed.
     */
    private void drawWeather(UiBatch batch, float width, float height) {
        WeatherSystem.Sample weather = WeatherSystem.sample(world.regionIndex,
                world.region().snow, world.worldTime);
        if (weather.type == WeatherSystem.Type.CLEAR || weather.intensity <= 0f) return;

        if (weather.type == WeatherSystem.Type.MIST) {
            batch.quad(0f, 0f, width, height, 0.56f, 0.66f, 0.82f,
                    weather.intensity * 0.10f);
            return;
        }

        float t = world.worldTime;
        int interval = (int) (t / 120f);
        if (weather.type == WeatherSystem.Type.RAIN) {
            for (int i = 0; i < 72; i++) {
                float seed = i * 17.37f + interval * 31.1f;
                float x = fract(seed * 0.173f + t * 0.39f) * width;
                float y = fract(seed * 0.317f + t * 1.65f) * height;
                batch.line(x, y, x - 7f, y + 15f, 1.1f,
                        0.62f, 0.78f, 1.0f, weather.intensity * 0.38f);
            }
        } else {
            for (int i = 0; i < 42; i++) {
                float seed = i * 29.71f + interval * 13.7f;
                float x = fract(seed * 0.137f + t * 0.035f) * width;
                float y = fract(seed * 0.293f + t * 0.20f) * height;
                float radius = 1.1f + fract(seed * 0.071f) * 1.8f;
                batch.circle(x, y, radius, 7, 0.91f, 0.95f, 1.0f,
                        weather.intensity * 0.60f);
            }
        }
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private void drawStartupFailure() {
        android.opengl.GLES20.glClearColor(0.05f, 0.06f, 0.09f, 1f);
        android.opengl.GLES20.glClear(android.opengl.GLES20.GL_COLOR_BUFFER_BIT);
        if (uiRenderer == null || !uiReady) {
            return;
        }
        UiBatch batch = uiBatch;
        float uiScale = TouchControls.scaleFor(screenW, screenH);
        batch.begin(Math.round(screenW * uiScale), Math.round(screenH * uiScale));
        drawErrorText(batch);
    }

    /** Draws a readable failure card instead of an empty screen. */
    private void drawErrorText(UiBatch batch) {
        float vw = batch.virtualWidth;
        float vh = batch.virtualHeight;
        com.laststar.game.ui.GenshinKit.panel(batch, vw * 0.10f, vh * 0.30f,
                vw * 0.80f, vh * 0.34f, 1f);
        com.laststar.game.ui.GenshinKit.title(batch, "START-UP FAILED",
                vw * 0.5f, vh * 0.30f + 26f, uiRenderer.font(), 1f);
        String msg = fatalError == null ? "unknown error" : fatalError;
        if (msg.length() > 78) {
            msg = msg.substring(0, 78);
        }
        batch.textSpacedCentred(msg, vw * 0.5f, vh * 0.30f + 58f, 1, 0.5f,
                com.laststar.game.ui.Style.CREAM[0], com.laststar.game.ui.Style.CREAM[1],
                com.laststar.game.ui.Style.CREAM[2], 1f, uiRenderer.font());
        batch.textSpacedCentred("full details in logcat under tag LastStar",
                vw * 0.5f, vh * 0.30f + 74f, 1, 0.5f,
                com.laststar.game.ui.Style.CREAM_DIM[0], com.laststar.game.ui.Style.CREAM_DIM[1],
                com.laststar.game.ui.Style.CREAM_DIM[2], 1f, uiRenderer.font());
        uiRenderer.draw(batch, screenW, screenH);
    }

    private void stepSimulation(float dt) {
        Player p = world.player;

        // Camera-relative movement uses the camera's yaw.
        input.cameraYaw = camera.movementYaw();
        float savedWorldClock = world.worldTime;
        world.update(dt, input);
        // The title/menu view can keep its background simulation moving, but
        // browsing menus must not consume in-world daylight.
        if (inMenu) world.worldTime = savedWorldClock;
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
    private int touchLogCount;
    private boolean attackSoundPlayed;

    // ----------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return handleTouch(event);
    }

    /**
     * Single entry point for touch, called from both the listener and the
     * override so no device-specific routing quirk can swallow input.
     *
     * <p>Logs the first few events: if a build ever renders but ignores taps
     * again, logcat says immediately whether events arrive at all or arrive
     * and are mishandled, which are very different bugs.</p>
     */
    private boolean handleTouch(MotionEvent event) {
        if (touchLogCount < 8) {
            touchLogCount++;
            android.util.Log.i("LastStar", "touch #" + touchLogCount
                    + " action=" + event.getActionMasked()
                    + " at " + (int) event.getX() + "," + (int) event.getY()
                    + " surface=" + screenW + "x" + screenH
                    + " menu=" + inMenu);
        }
        float scale = TouchControls.scaleFor(screenW, screenH);
        if (scale <= 0f) {
            // Guards the first frames before the surface size is known: a zero
            // scale makes every virtual coordinate infinite and every hit test
            // miss, which looks exactly like a dead screen.
            return true;
        }
        if (inMenu) {
            float vx = event.getX() / scale;
            float vy = event.getY() / scale;
            float vw = screenW / scale;
            float vh = screenH / scale;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                menu.tap(vx, vy, vw, vh);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                menu.release();
                prefs.setMuted(!menu.settings.audio);
                prefs.setVolume(menu.settings.masterVolume);
                prefs.setHaptics(menu.settings.haptics);
                prefs.setCameraShake(menu.settings.cameraShake);
                prefs.setDifficulty(menu.settings.difficulty);
                prefs.setSkinTone(menu.wardrobe.selectedSwatch);
                prefs.setBody(menu.wardrobe.selectedBody);
                prefs.setPlayerName(menu.home.playerName());
                world.player.skinTone = menu.wardrobe.selectedSwatch;
                sound.setEnabled(menu.settings.audio);
                hud.soundOn = menu.settings.audio;
                sound.setVolume(menu.settings.masterVolume);
            }
            return true;
        }
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
        // Banking on pause is what makes "I closed the app" cost nothing.
        save.save(world);
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
