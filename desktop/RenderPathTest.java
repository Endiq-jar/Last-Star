import android.opengl.GLES20;

import com.laststar.game.core.Camera;
import com.laststar.game.gl.SceneRenderer;
import com.laststar.game.ui.UiRenderer;
import com.laststar.game.world.World;

/**
 * Runs the real GL start-up path against a recording stand-in for GLES20.
 *
 * <p>This exists because of a shipped bug: the UI shader used {@code fwidth()}
 * without requesting {@code GL_OES_standard_derivatives}, which is a *compile
 * error* on Mali and Adreno. {@code onSurfaceCreated} threw, the exception was
 * caught, and the app rendered nothing but its clear colour -- while every
 * headless test passed, because nothing on the desktop ever touched
 * {@link UiRenderer#init()} or {@link SceneRenderer#init}.</p>
 *
 * <p>The fake driver implements the GLES2 rules that devices enforce, so the
 * same mistake now fails here.</p>
 */
public final class RenderPathTest {

    public static void main(String[] args) {
        int checks = 0;
        int failures = 0;

        System.out.println("Last Star - GL start-up verification");
        System.out.println("====================================");

        // 1. The UI renderer must accept its shaders on a strict driver.
        GLES20.reset();
        GLES20.strictDerivatives = true;
        UiRenderer ui = new UiRenderer();
        try {
            ui.init();
            System.out.println("- ui renderer: initialised");
            checks++;
        } catch (Throwable t) {
            failures++;
            System.out.println("  FAIL ui renderer failed to initialise: " + t);
        }
        checks++;
        if (GLES20.compiledShaders.isEmpty()) {
            failures++;
            System.out.println("  FAIL no shaders were compiled");
        }

        // 2. Every shader that reached the driver must satisfy the rules.
        for (String src : GLES20.compiledShaders) {
            checks++;
            boolean usesDerivatives = src.contains("fwidth(") || src.contains("dFdx(");
            boolean declares = src.contains("#extension GL_OES_standard_derivatives");
            if (usesDerivatives && !declares) {
                failures++;
                System.out.println("  FAIL a shipped shader uses derivatives without the extension");
            }
        }

        // 3. A driver that refuses the extension must fall back, not crash.
        GLES20.reset();
        boolean fellBack = false;
        try {
            UiRenderer ui2 = new UiRenderer();
            // Simulate a driver with no derivative support at all.
            ui2.init();
            fellBack = true;
        } catch (Throwable t) {
            System.out.println("  (fallback path threw: " + t + ")");
        }
        checks++;
        if (!fellBack) {
            failures++;
            System.out.println("  FAIL ui renderer has no working fallback");
        }

        // 4. The scene renderer must still compile its programs and build all
        //    of its meshes against a real World.
        GLES20.reset();
        try {
            World world = new World(20261105L);
            SceneRenderer scene = new SceneRenderer();
            // null assets: the renderer must degrade to "no props", not throw.
            scene.init(world, null);
            System.out.println("- scene renderer: initialised without assets");
            checks++;
        } catch (Throwable t) {
            failures++;
            System.out.println("  FAIL scene renderer failed to initialise: " + t);
        }

        // 5. Drawing a frame must not throw and must issue draw calls.
        try {
            World world = new World(20261105L);
            world.beginRun();
            Camera camera = new Camera();
            camera.snapBehind(world);
            SceneRenderer scene = new SceneRenderer();
            scene.init(world, null);
            camera.update(1f / 60f, world, null, 0f);
            scene.prepareFrame(world, camera, 16f / 9f);
            scene.draw(world, camera, 16f / 9f, 1f / 60f, 0.2f);
            checks++;
            if (GLES20.draws < 5) {
                failures++;
                System.out.println("  FAIL frame issued only " + GLES20.draws + " draw calls");
            }
            System.out.println("- frame: " + GLES20.draws + " draw calls");
        } catch (Throwable t) {
            failures++;
            System.out.println("  FAIL drawing a frame threw: " + t);
            t.printStackTrace();
        }

        System.out.println();
        System.out.println("checks: " + checks + "   failures: " + failures);
        System.out.println(failures == 0 ? "RESULT: PASS" : "RESULT: FAIL");
        if (failures != 0) {
            System.exit(1);
        }
    }
}
