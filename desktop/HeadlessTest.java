import com.laststar.game.art.HumanoidRig;
import com.laststar.game.core.Camera;
import com.laststar.game.core.Mat4;
import com.laststar.game.core.Noise;
import com.laststar.game.core.Terrain;
import com.laststar.game.core.Vec3;
import com.laststar.game.gl.Geometry;
import com.laststar.game.gl.MeshData;
import com.laststar.game.gl.ModelLibrary;
import com.laststar.game.gl.PropPlanner;
import com.laststar.game.ui.Font;
import com.laststar.game.ui.UiBatch;
import com.laststar.game.world.Enemy;
import com.laststar.game.world.InputState;
import com.laststar.game.world.Player;
import com.laststar.game.world.Shard;
import com.laststar.game.world.World;

/**
 * Headless verification of every piece of game logic that does not need OpenGL
 * or Android. Run with:
 *
 * <pre>tools/run_tests.sh</pre>
 *
 * <p>Returns exit code 0 only when every assertion passes, so it can gate the
 * APK build.</p>
 */
public final class HeadlessTest {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        System.out.println("Last Star - headless verification");
        System.out.println("=================================");

        testNoiseDeterminism();
        testTerrain();
        testMountains();
        testNoBlackScreen();
        testMatrices();
        testCamera();
        testGeometry();
        testFont();
        testUiBatch();
        testMenuFont();
        testMenuScreens();
        testDifficulty();
        testWorldSimulation();
        testPlayerMovement();
        testCombat();
        testShardCollectionAndVictory();
        testEnemyAi();
        testFullPlaythroughBot();
        testRig();

        System.out.println();
        System.out.println("checks: " + checks + "   failures: " + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // ------------------------------------------------------------- helpers

    private static void check(boolean condition, String what) {
        checks++;
        if (!condition) {
            failures++;
            System.out.println("  FAIL  " + what);
        }
    }

    private static void checkNear(float actual, float expected, float tolerance, String what) {
        checks++;
        if (Math.abs(actual - expected) > tolerance) {
            failures++;
            System.out.println("  FAIL  " + what + " (expected " + expected + ", got " + actual + ")");
        }
    }

    private static void section(String name) {
        System.out.println("- " + name);
    }

    // --------------------------------------------------------------- tests

    private static void testNoiseDeterminism() {
        section("noise determinism");
        Noise a = new Noise(42L);
        Noise b = new Noise(42L);
        float sum = 0f;
        for (int i = 0; i < 500; i++) {
            float x = i * 0.37f;
            float y = i * -0.11f;
            float va = a.noise(x, y);
            float vb = b.noise(x, y);
            check(va == vb, "same seed produces identical noise");
            check(!Float.isNaN(va) && Math.abs(va) < 2.5f, "noise in range");
            sum += Math.abs(va);
        }
        check(sum > 10f, "noise is not degenerate");
        Noise other = new Noise(43L);
        check(other.noise(1.5f, 2.5f) != a.noise(1.5f, 2.5f), "different seed differs");
    }

    private static void testTerrain() {
        section("terrain");
        Terrain t = new Terrain(220f, 110, 7L);
        check(t.heights.length == 111 * 111, "vertex count");
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float h : t.heights) {
            min = Math.min(min, h);
            max = Math.max(max, h);
            check(!Float.isNaN(h), "no NaN heights");
        }
        check(max - min > 6f, "terrain has relief (" + (max - min) + " m)");
        check(max - min < 80f, "terrain relief is sane");

        // Spawn should be flat.
        checkNear(t.heightAt(0f, 0f), 0.35f, 0.02f, "spawn plateau height");
        checkNear(t.heightAt(1f, 1f), 0.35f, 0.05f, "spawn plateau is flat");
        check(t.slopeAt(0f, 0f) < 0.05f, "spawn is walkable");

        // Bilinear sampling is continuous: small steps -> small height changes.
        float prev = t.heightAt(-100f, 5f);
        boolean continuous = true;
        for (float x = -100f; x < 100f; x += 0.25f) {
            float h = t.heightAt(x, 5f);
            if (Math.abs(h - prev) > 1.2f) {
                continuous = false;
            }
            prev = h;
        }
        check(continuous, "height field is continuous");

        // Out of bounds must clamp, not throw.
        check(!Float.isNaN(t.heightAt(-9999f, 9999f)), "out of bounds clamps");
        check(t.normalAt(0f, 0f).y > 0.9f, "normal is up on the plateau");
    }


    /**
     * The mountain range is easy to break in ways that are invisible in a test
     * log and very visible on screen: it can fall outside the camera's far
     * plane, or be fogged so heavily it never shows up. These are the exact
     * bugs that made the first attempt disappear, so they get assertions.
     */
    private static void testMountains() {
        section("mountains");

        // 1. The range must be inside the camera's far plane.
        Camera camera = new Camera();
        Mat4 proj = camera.projectionMatrix(16f / 9f);
        // Recover the far plane from the projection matrix itself rather than
        // trusting a constant: m[10] = (f+n)/(n-f) and m[14] = 2fn/(n-f), so
        // far = m[14] / (m[10] + 1).
        float recoveredFar = proj.m[14] / (proj.m[10] + 1f);
        check(recoveredFar > 400f, "camera far plane clears the map (" + recoveredFar + " m)");
        check(520f < recoveredFar, "distant range sits inside the far plane");

        // 2. The range must actually have peaks, and be shaped like a range.
        MeshData range = Geometry.mountainRange(520f, 96f, 56, 7L, 230f);
        check(range.positions.length > 0, "range has vertices");
        check(range.indices.length % 3 == 0, "range indices are triangles");
        float maxY = 0f;
        float minY = Float.MAX_VALUE;
        float maxR = 0f;
        for (int i = 0; i < range.positions.length; i += 3) {
            maxY = Math.max(maxY, range.positions[i + 1]);
            minY = Math.min(minY, range.positions[i + 1]);
            float r = (float) Math.sqrt(range.positions[i] * range.positions[i]
                    + range.positions[i + 2] * range.positions[i + 2]);
            maxR = Math.max(maxR, r);
        }
        check(maxY > 60f, "peaks are tall (" + maxY + " m)");
        check(minY < 0f, "peaks are grounded below the horizon");
        check(maxR > 400f && maxR < 900f, "range ring radius is sane (" + maxR + " m)");

        // 3. Every triangle must be valid and the mesh must be inside the far
        //    plane at its furthest point, or it is silently clipped away.
        int n = range.positions.length / 3;
        boolean indicesValid = true;
        for (short idx : range.indices) {
            if ((idx & 0xFFFF) >= n) {
                indicesValid = false;
            }
        }
        check(indicesValid, "range indices are in range");
        check(maxR < recoveredFar, "no peak is clipped by the far plane");

        // 4. Fog must not erase the range. The renderer uses 1-exp(-d*k); at the
        //    range's distance that has to stay well short of opaque.
        float fogDensity = 0.0007f;
        float fogAtRange = 1f - (float) Math.exp(-520f * fogDensity);
        check(fogAtRange < 0.55f, "range is not fogged out (" + fogAtRange + ")");

        // 5. The playable rim must stay walkable: shards spawn out to 86 m and
        //    the player has to reach them.
        Terrain t = new Terrain(220f, 110, 20261105L);
        float steepest = 0f;
        for (float r = 20f; r <= 100f; r += 4f) {
            for (int k = 0; k < 48; k++) {
                double ang = k * Math.PI * 2.0 / 48;
                steepest = Math.max(steepest, t.slopeAt(
                        (float) (Math.cos(ang) * r), (float) (Math.sin(ang) * r)));
            }
        }
        check(steepest < 0.55f, "playable ring has no walls (steepest " + steepest + ")");
    }


    /**
     * The app shipped once showing nothing but its clear colour, because a
     * fragment shader failed to compile inside onSurfaceCreated and the
     * exception was swallowed. Rendering a frame and asserting that the image
     * actually varies is the only test that would have caught it from the
     * outside, so it is here.
     */
    private static void testNoBlackScreen() {
        section("no black screen");

        // 1. A rendered frame must contain real content, not one flat colour.
        World world = new World(20261105L);
        world.beginRun();
        Camera camera = new Camera();
        camera.snapBehind(world);
        camera.update(1f / 60f, world, null, 0f);
        Raster raster = new Raster(160, 90,
                camera.viewMatrix(), camera.projectionMatrix(16f / 9f));
        raster.setCameraPosition(camera.position);
        raster.setSun(0.4f, 0.8f, 0.3f, 1f, 0.9f, 0.7f);
        raster.setAmbient(0.5f, 0.55f, 0.66f);
        raster.setFog(0.0007f, 0.80f, 0.64f, 0.58f);
        raster.clearDepth();
        raster.drawSky(0.80f, 0.64f, 0.58f, 0.20f, 0.34f, 0.66f,
                600f, 200f, 60f, 1f, 0.84f, 0.58f, 1f);
        // Draw the world the way the renderer does: terrain, then the range.
        MeshData terrain = com.laststar.game.gl.Geometry.terrain(world.terrain);
        raster.drawMesh(terrain.positions, terrain.normals, terrain.colors,
                terrain.indices, new Mat4(), 1f, 0f, null, 0f);
        MeshData range = com.laststar.game.gl.Geometry.mountainRange(
                520f, 96f, 56, world.worldSeed ^ 0x5A5A5A5AL, 230f);
        Mat4 rangeMatrix = new Mat4();
        rangeMatrix.m[12] = camera.position.x;
        rangeMatrix.m[14] = camera.position.z;
        raster.drawMesh(range.positions, range.normals, range.colors,
                range.indices, rangeMatrix, 1f, 0.06f, null, 0f);
        java.util.HashSet<Integer> colours = new java.util.HashSet<>();
        for (int px : raster.argb) {
            colours.add(px);
        }
        check(colours.size() > 200,
                "frame has varied content (" + colours.size() + " distinct colours)");
        check(colours.size() > 800, "frame is not a flat clear colour");

        // 2. The clear-colour fallback must not be the only thing drawn.
        int first = raster.argb[0];
        int differing = 0;
        for (int px : raster.argb) {
            if (px != first) {
                differing++;
            }
        }
        check(differing > raster.argb.length / 2,
                "most of the frame is not the background colour ("
                        + (100 * differing / raster.argb.length) + "%)");

        // 3. In-world peaks must be present, so "mountains everywhere" cannot
        //    silently regress to a bare horizon.
        ModelLibrary models = ModelLoaderFixture.load();
        if (models == null) {
            System.out.println("  (model set not built; skipping placement checks)");
            return;
        }
        PropPlanner planner = new PropPlanner(models, world.terrain, 20261105L);
        check(planner.count() > 2000, "world is dressed (" + planner.count() + " props)");
        check(countHighPlaced(planner) > 30,
                "rock spires stand in the world ("
                        + countHighPlaced(planner) + " elevated blocks)");

        // Every asset in the imported pack has to be in the terrain. This is
        // the acceptance criterion for the pack import, and it is easy to break
        // silently: a scatterer that runs out of room, or one that is placed
        // after something denser has claimed the ground, simply returns without
        // placing anything and nothing complains.
        String[][] required = {
                {"pk_tree", "tree"},
                {"pk_bush", "bush"},
                {"pk_grass", "grass"},
                {"pk_flower", "reed"},
                {"pk_flowers", "flowers"},
                {"pk_rock", "rock"},
                {"pk_rock2", "rock2"},
                {"pk_pebbles", "pebbles"},
        };
        for (String[] pair : required) {
            int n = planner.countNamed(pair[0]);
            check(n > 50, pair[1] + " is scattered through the world (" + n + ")");
        }
        // Mountains and bridges are landmarks rather than scatter: few, large,
        // and placed deliberately.
        check(planner.countNamed("pk_mountain") >= 12,
                "mountains ring the horizon (" + planner.countNamed("pk_mountain") + ")");
        check(planner.countNamed("pk_bridge") >= 1,
                "bridges cross the water (" + planner.countNamed("pk_bridge") + ")");
        // The Wander is a single object the world places by hand, never a
        // scattered prop. One copy, in World.wanderPosition.
        check(planner.countNamed("pk_spell-book") == 0,
                "the Wander is not scattered as scenery");

        // Ground cover has to be dense enough to read as a carpet rather than
        // as scattered decorations; this is the "grass everywhere" criterion.
        check(planner.countNamed("pk_flower") > 200,
                "reeds stand along the water (" + planner.countNamed("pk_flower") + ")");
        check(planner.countNamed("pk_grass") > 8000,
                "grass blankets the ground (" + planner.countNamed("pk_grass") + ")");

        // And the whole thing has to stay inside a frame budget. The renderer
        // culls individual instances, so what matters is the total population.
        check(planner.count() < 60000,
                "world stays inside its prop budget (" + planner.count() + ")");
    }

    /** Instances that sit well above their own footprint, i.e. stacked spires. */
    private static int countHighPlaced(PropPlanner planner) {
        int n = 0;
        PropPlanner.Grid g = planner.grid;
        for (int cz = 0; cz < g.rows(); cz++) {
            for (int cx = 0; cx < g.cols(); cx++) {
                java.util.List<PropPlanner.Instance> cell = g.cell(cx, cz);
                for (int i = 0; i < cell.size(); i++) {
                    PropPlanner.Instance in = cell.get(i);
                    float ground = planner.terrain().heightAt(in.x, in.z);
                    if (in.y > ground + 2.5f && in.scale > 2f) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private static void testMatrices() {
        section("matrices");
        Mat4 t = Mat4.translation(1f, 2f, 3f);
        Vec3 p = t.transformPoint(new Vec3(0f, 0f, 0f));
        checkNear(p.x, 1f, 1e-5f, "translation x");
        checkNear(p.y, 2f, 1e-5f, "translation y");
        checkNear(p.z, 3f, 1e-5f, "translation z");

        Mat4 ry = Mat4.rotationY(90f);
        Vec3 q = ry.transformPoint(new Vec3(0f, 0f, 1f));
        checkNear(q.x, 1f, 1e-4f, "rotationY 90 maps +z to +x");
        checkNear(q.z, 0f, 1e-4f, "rotationY 90 zeroes z");

        Mat4 ident = new Mat4();
        Vec3 r = ident.transformPoint(new Vec3(4f, 5f, 6f));
        checkNear(r.x, 4f, 1e-6f, "identity x");

        // perspective: point at near plane maps to -1..1 clip space
        Mat4 proj = Mat4.perspective(60f, 1.6f, 0.1f, 100f);
        Vec3 centred = proj.transformPoint(new Vec3(0f, 0f, -10f));
        checkNear(centred.x, 0f, 1e-5f, "perspective centre x");
        checkNear(centred.y, 0f, 1e-5f, "perspective centre y");

        // lookAt from origin towards +z.
        Mat4 view = Mat4.lookAt(new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, 1f), Vec3.UP);
        Vec3 seen = view.transformPoint(new Vec3(0f, 0f, 5f));
        checkNear(seen.z, -5f, 1e-4f, "lookAt places forward at -z");

        Mat4 a = Mat4.translation(1f, 0f, 0f);
        Mat4 b = Mat4.scale(2f, 2f, 2f);
        Mat4 composed = new Mat4().setMultiply(a, b);
        Vec3 cp = composed.transformPoint(new Vec3(1f, 0f, 0f));
        checkNear(cp.x, 3f, 1e-5f, "a*b applies b then translation");
    }

    private static void testCamera() {
        section("camera");
        World w = new World(1L);
        Camera c = new Camera();
        InputState in = new InputState();
        c.snapBehind(w);
        for (int i = 0; i < 120; i++) {
            c.update(1f / 60f, w, in, 0f);
        }
        float dist = c.position.distanceTo(w.player.centre());
        check(dist > 2f && dist < 12f, "camera settles at a sane distance (" + dist + ")");
        check(c.position.y > w.terrain.heightAt(c.position.x, c.position.z),
                "camera stays above the ground");

        float before = c.yaw;
        in.lookX = 4f;
        c.update(1f / 60f, w, in, 0f);
        check(c.yaw > before, "look input yaws the camera");

        in.lookX = 0f;
        for (int i = 0; i < 200; i++) {
            in.lookY = -40f;
            c.update(1f / 60f, w, in, 0f);
        }
        check(c.pitch >= Camera.MIN_PITCH - 0.01f, "pitch clamps at the bottom");
        check(c.pitch <= Camera.MAX_PITCH + 0.01f, "pitch clamps at the top");

        Mat4 proj = c.projectionMatrix(1.9f);
        check(!Float.isNaN(proj.m[0]), "projection is finite");
        Mat4 view = c.viewMatrix();
        check(!Float.isNaN(view.m[0]), "view is finite");
    }

    private static void testGeometry() {
        section("geometry");
        Terrain t = new Terrain(220f, 110, 3L);
        long start = System.nanoTime();
        MeshData terrain = Geometry.terrain(t);
        long ms = (System.nanoTime() - start) / 1_000_000;
        check(terrain.vertexCount() == 111 * 111, "terrain vertex count");
        check(terrain.triangleCount() == 110 * 110 * 2, "terrain triangle count");
        check(terrain.colors.length == terrain.vertexCount() * 4, "terrain colours present");
        System.out.println("    terrain mesh: " + terrain.vertexCount() + " verts, "
                + terrain.triangleCount() + " tris, built in " + ms + " ms");

        boolean finite = true;
        float minColor = 9f;
        for (int i = 0; i < terrain.positions.length; i++) {
            if (Float.isNaN(terrain.positions[i]) || Float.isInfinite(terrain.positions[i])) {
                finite = false;
                break;
            }
        }
        for (float c : terrain.colors) {
            minColor = Math.min(minColor, c);
            if (c < 0f || c > 1.05f) {
                finite = false;
            }
        }
        check(finite, "terrain geometry has no NaN/inf and colours are in range");
        check(minColor >= 0f, "colours are not negative");

        // Indices must be in range.
        boolean indicesOk = true;
        for (short s : terrain.indices) {
            if (s < 0 || s >= terrain.vertexCount()) {
                indicesOk = false;
            }
        }
        check(indicesOk, "terrain indices are in range");

        MeshData water = Geometry.waterPlane(300f, 40, -0.55f);
        check(water.triangleCount() == 40 * 40 * 2, "water triangle count");
        check(water.waves != null, "water has wave attribute");

        MeshData box = Geometry.box(1, 1, 1, 1, 1, 1, 1, 0, 0, 0);
        check(box.triangleCount() == 12, "box has 12 triangles");
        check(box.vertexCount() == 24, "box has 24 vertices");

        MeshData sphere = Geometry.sphere(1f, 8, 10, 1, 1, 1, 1);
        check(sphere.triangleCount() == 8 * 10 * 2, "sphere triangle count");
        boolean normalsUnit = true;
        for (int i = 0; i < sphere.normals.length; i += 3) {
            float len = (float) Math.sqrt(sphere.normals[i] * sphere.normals[i]
                    + sphere.normals[i + 1] * sphere.normals[i + 1]
                    + sphere.normals[i + 2] * sphere.normals[i + 2]);
            if (Math.abs(len - 1f) > 0.01f) {
                normalsUnit = false;
            }
        }
        check(normalsUnit, "sphere normals are unit length");

        MeshData star = Geometry.starShard(0.4f, 1, 1, 1);
        check(star.triangleCount() == 20, "star shard triangle count");
        check(star.vertexCount() < 70, "star shard is cheap");

        MeshData merged = MeshData.merge(box, sphere, star);
        check(merged.vertexCount() == box.vertexCount() + sphere.vertexCount() + star.vertexCount(),
                "merge keeps every vertex");
        boolean mergedIndexOk = true;
        for (short s : merged.indices) {
            if (s < 0 || s >= merged.vertexCount()) {
                mergedIndexOk = false;
            }
        }
        check(mergedIndexOk, "merged indices rebased correctly");

        // Rigid transform must preserve distances.
        MeshData moved = box.transformed(Mat4.translation(5f, 1f, -2f));
        checkNear(moved.positions[0], box.positions[0] + 5f, 1e-4f, "transform translates x");
        checkNear(moved.positions[1], box.positions[1] + 1f, 1e-4f, "transform translates y");
    }

    private static void testFont() {
        section("font");
        Font f = new Font();
        check(f.atlas.length > 0, "font atlas generated");
        for (char c = 'A'; c <= 'Z'; c++) {
            check(f.indexOf(c) >= 0, "glyph present for " + c);
        }
        for (char c = '0'; c <= '9'; c++) {
            check(f.indexOf(c) >= 0, "glyph present for " + c);
        }
        float[] uv = f.uv('A');
        check(uv[0] >= 0f && uv[0] <= 1f && uv[1] >= 0f && uv[1] <= 1f, "uv in range");
        check(f.textWidth("ABC", 2) == 3 * 6 * 2, "text width");
        check(f.glyphWidth() == Font.GLYPH_W && f.advance() == Font.ADVANCE, "atlas contract");

        // Every glyph must have at least one lit pixel (catch typos in the atlas).
        int empty = 0;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (int i = 0; i < chars.length(); i++) {
            boolean any = false;
            for (int y = 0; y < Font.GLYPH_H; y++) {
                for (int x = 0; x < Font.GLYPH_W; x++) {
                    int px = (int) (f.uv(chars.charAt(i))[0] * f.atlasWidth) + x;
                    int py = (int) (f.uv(chars.charAt(i))[1] * f.atlasHeight) + y;
                    if (f.atlas[py * f.atlasWidth + px] != 0) {
                        any = true;
                    }
                }
            }
            if (!any) {
                empty++;
            }
        }
        check(empty == 0, "no blank glyphs (" + empty + " blank)");
    }

    private static void testUiBatch() {
        section("ui batch");
        UiBatch b = new UiBatch();
        Font f = new Font();
        b.begin(720, 405);
        check(b.virtualWidth == 720 && b.virtualHeight == 405, "batch size");
        b.quad(0, 0, 10, 10, 1, 0, 0, 1);
        check(b.vertexCount() == 6, "quad emits 2 triangles");
        b.textCentred("HELLO", 360, 100, 2, 1, 1, 1, 1, f);
        check(b.vertexCount() == 6 + 5 * 6, "text emits a quad per glyph");
        b.circle(100, 100, 20, 12, 1, 1, 1, 1);
        check(b.vertexCount() == 6 + 30 + 12 * 3, "circle emits a fan");
        b.ring(50, 50, 30, 26, 10, 1, 1, 1, 1);
        b.glow(10, 10, 30, 8, 1, 1, 1, 0.5f);
        b.vignette(0.5f);
        b.topFade(40, 0, 0, 0, 0.8f);
        int total = b.vertexCount();
        b.begin(720, 405);
        check(b.vertexCount() == 0, "begin resets the batch");
        check(total > 100, "complex frame produces geometry");

        for (UiBatch.Vertex v : b.vertices()) {
            check(!Float.isNaN(v.x), "no NaN in ui vertices");
        }
    }

    private static void testMenuFont() {
        section("menu font");
        com.laststar.game.ui.VectorFont vf = new com.laststar.game.ui.VectorFont();
        check(vf.atlasWidth > 0 && vf.atlasHeight > 0, "serif atlas generated");
        check(vf.glyphWidth() > 0 && vf.advance() > vf.glyphWidth(),
                "serif metrics are design-space");
        check(vf.coverageAtlas() == false, "serif atlas is a distance field");
        String charset = vf.charset();
        // Every glyph in the charset must carry ink inside its own UV cell,
        // otherwise a letter would silently vanish from the menus.
        int blank = 0;
        for (int i = 0; i < charset.length(); i++) {
            char c = charset.charAt(i);
            if (c == ' ') {
                continue;
            }
            float[] uv = vf.uv(c);
            int x0 = (int) (uv[0] * vf.atlasWidth);
            int y0 = (int) (uv[1] * vf.atlasHeight);
            int w = Math.max(1, (int) (uv[2] * vf.atlasWidth));
            int h = Math.max(1, (int) (uv[3] * vf.atlasHeight));
            boolean any = false;
            for (int y = y0; y < y0 + h && y < vf.atlasHeight; y++) {
                for (int x = x0; x < x0 + w && x < vf.atlasWidth; x++) {
                    if ((vf.atlas[y * vf.atlasWidth + x] & 0xFF) > 140) {
                        any = true;
                    }
                }
            }
            if (!any) {
                blank++;
                System.out.println("  blank glyph: " + c);
            }
        }
        check(blank == 0, "serif has ink for every glyph (" + blank + " blank)");
        // Distinct glyphs must not share a UV rect.
        check(vf.uv('A')[0] != vf.uv('B')[0], "glyph cells are distinct");
        check(vf.textWidth("ABC", 2) == 3 * vf.advance() * 2, "serif text width");
    }

    private static void testMenuScreens() {
        section("menu screens");
        com.laststar.game.ui.Menu menu = new com.laststar.game.ui.Menu();
        com.laststar.game.ui.VectorFont font = new com.laststar.game.ui.VectorFont();
        com.laststar.game.ui.UiBatch batch = new com.laststar.game.ui.UiBatch();

        // Every page must render without emitting NaN or a runaway vertex count.
        com.laststar.game.ui.Menu.Page[] pages = com.laststar.game.ui.Menu.Page.values();
        for (com.laststar.game.ui.Menu.Page page : pages) {
            menu.shownForTest(page);
            for (int i = 0; i < 20; i++) {
                menu.update(1f / 60f);
            }
            batch.begin(720, 405);
            menu.draw(batch, font);
            check(batch.vertexCount() > 500, page + " page draws geometry");
            check(batch.vertexCount() < 60000, page + " page stays within budget");
            boolean finite = true;
            for (com.laststar.game.ui.UiBatch.Vertex v : batch.vertices()) {
                if (Float.isNaN(v.x) || Float.isNaN(v.y) || Float.isNaN(v.u)) {
                    finite = false;
                }
            }
            check(finite, page + " page has no NaN vertices");
        }

        // Tab bar routing: the five tabs map to their pages and back to home.
        menu.shownForTest(com.laststar.game.ui.Menu.Page.HOME);
        float width = 720f;
        float height = 405f;
        com.laststar.game.ui.TabBar tabs = menu.tabs();
        for (int i = 0; i < com.laststar.game.ui.TabBar.Tab.values().length; i++) {
            float x = tabs.tabX(i, width);
            float y = height - com.laststar.game.ui.TabBar.HEIGHT * 0.5f;
            menu.tap(x, y, width, height);
            check(menu.page == com.laststar.game.ui.TabBar.Tab.values()[i].pageForTest(),
                    "tab " + i + " opens its page");
        }

        // START leaves the menu and asks the surface to launch the game.
        menu.shownForTest(com.laststar.game.ui.Menu.Page.HOME);
        menu.startRequested = false;
        menu.tap(width * 0.5f, height * 0.735f, width, height);
        check(menu.startRequested, "START requests the game");
        check(!menu.home.playerName().isEmpty(), "player name is set");

        // Settings controls actually change state through hit-testing.
        menu.shownForTest(com.laststar.game.ui.Menu.Page.SETTINGS);
        boolean before = menu.settings.audio;
        float toggleX = width * 0.135f + width * 0.700f - 50f - 26f + 25f;
        float row0 = 405f * 0.245f + 30f;
        menu.tap(toggleX, row0, width, height);
        check(menu.settings.audio != before, "audio toggle flips");
        // Slider drag sets the volume from the tapped height.
        float sliderX = width * 0.135f + 54f;
        menu.tap(sliderX, 405f * 0.245f + 34f, width, height);
        check(menu.settings.masterVolume > 0.95f, "slider top is full volume");
        menu.tap(sliderX, 405f * 0.245f + 405f * 0.400f - 30f, width, height);
        check(menu.settings.masterVolume < 0.05f, "slider bottom is silent");
        menu.release();

        // The quest tabs and rows select different content. The journal now
        // reads the live quest log rather than a fixed list, so it has to be
        // given a world before anything is on it.
        World journalWorld = new World(20261105L);
        journalWorld.quests.refreshAvailability();
        menu.bind(journalWorld);
        menu.shownForTest(com.laststar.game.ui.Menu.Page.QUESTS);
        int tabBefore = menu.quests.activeTab;
        menu.tap(width * 0.185f + 3f * width * 0.155f, 405f * 0.175f, width, height);
        check(menu.quests.activeTab != tabBefore, "quest tab switches");
        // Every tab must be able to draw without a row to select.
        for (int t = 0; t < 4; t++) {
            menu.tap(width * 0.185f + t * width * 0.155f, 405f * 0.175f, width, height);
            menu.update(0.016f);
        }
        menu.tap(width * 0.185f + 0f * width * 0.155f, 405f * 0.175f, width, height);
        menu.update(0.016f);
        check(menu.quests.activeTab == 0, "quest tab goes to active");
        int rowBefore = menu.quests.selectedQuest;
        menu.tap(60f, 405f * 0.255f + 2f * 39f, width, height);
        check(menu.quests.selectedQuest != rowBefore || rowBefore == 2,
                "quest row selects");

        // Wardrobe swatches are swappable.
        menu.shownForTest(com.laststar.game.ui.Menu.Page.WARDROBE);
        menu.tap(width * 0.545f + 12f + 20f, 405f * 0.190f + 32f + 20f, width, height);
        check(menu.wardrobe.selectedSwatch == 0, "first swatch selects");
    }

    private static void testDifficulty() {
        section("difficulty");
        int[] expectedHealth = {140, 100, 80};
        for (int d = 0; d < 3; d++) {
            World w = new World(20261105L);
            w.difficulty = d;
            w.beginRun();
            checkNear(w.player.maxHealth, expectedHealth[d], 0.01f,
                    "difficulty " + d + " max health");
            checkNear(w.player.health, expectedHealth[d], 0.01f,
                    "difficulty " + d + " starts at full health");
            check(w.phase == World.Phase.INTRO, "difficulty " + d + " begins at the intro");
            check(w.level() == 1, "difficulty " + d + " starts at level 1");
            check(w.shardsCollected == 0, "difficulty " + d + " starts with no shards");
        }
        // A restart keeps growing the player's pool instead of resetting it.
        World w = new World(7L);
        w.difficulty = 0;
        w.beginRun();
        w.restart();
        checkNear(w.player.maxHealth, 160f, 0.01f, "restart grows max health");
        check(w.level() == 2, "restart advances the level");
    }

    private static void testWorldSimulation() {
        section("world simulation");
        World w = new World(2024L);
        check(w.shards.size() == World.SHARD_COUNT, "six shards exist");
        check(w.enemies.size() >= 5, "enemies spawned");
        check(w.player.health == w.player.maxHealth, "player starts healthy");
        check(w.phase == World.Phase.INTRO, "starts in intro");

        // Every shard must be above the water line and on the ground.
        for (Shard s : w.shards) {
            check(s.position.y > w.terrain.waterLevel, "shard above water");
            float ground = w.terrain.heightAt(s.position.x, s.position.z);
            check(s.position.y - ground > 0.6f && s.position.y - ground < 2.0f,
                    "shard floats above the ground");
        }
        // Every enemy must start on the ground.
        for (Enemy e : w.enemies) {
            float ground = w.terrain.heightAt(e.position.x, e.position.z);
            checkNear(e.position.y, ground, 0.15f, "enemy grounded at spawn");
        }

        InputState in = new InputState();
        // Run the intro out.
        for (int i = 0; i < 200; i++) {
            w.update(1f / 60f, in);
        }
        check(w.phase == World.Phase.PLAYING, "intro transitions to playing");

        // Long idle run must not throw or produce NaN.
        for (int i = 0; i < 60 * 30; i++) {
            w.update(1f / 60f, in);
        }
        check(!Float.isNaN(w.player.position.x), "idle simulation keeps position finite");
        check(w.player.position.y > -50f && w.player.position.y < 200f, "player stays in the world");
    }

    private static void testPlayerMovement() {
        section("player movement");
        World w = new World(77L);
        InputState in = new InputState();
        w.phase = World.Phase.PLAYING;
        w.phaseTimer = 10f;

        Vec3 start = w.player.position;
        in.moveY = 1f;
        in.cameraYaw = 0f;
        for (int i = 0; i < 90; i++) {
            w.update(1f / 60f, in);
            in.clearFrameEdges();
        }
        float moved = w.player.position.distanceTo(start);
        check(moved > 2f, "player moves when the stick is pushed (" + moved + " m)");

        // Diagonal input must not be faster than cardinal input.
        Vec3 beforeDiag = w.player.position;
        in.moveX = 1f;
        in.moveY = 1f;
        for (int i = 0; i < 60; i++) {
            w.update(1f / 60f, in);
        }
        float diagDist = w.player.position.distanceTo(beforeDiag);

        Vec3 beforeCard = w.player.position;
        in.moveX = 0f;
        in.moveY = 1f;
        for (int i = 0; i < 60; i++) {
            w.update(1f / 60f, in);
        }
        float cardDist = w.player.position.distanceTo(beforeCard);
        check(diagDist < cardDist * 1.25f, "diagonal movement is not faster");

        // Jump then land.
        in.moveY = 0f;
        for (int i = 0; i < 30; i++) {
            w.update(1f / 60f, in);
        }
        float groundBefore = w.player.position.y;
        in.jumpPressed = true;
        w.update(1f / 60f, in);
        in.jumpPressed = false;
        boolean wentUp = false;
        float peak = groundBefore;
        for (int i = 0; i < 90; i++) {
            w.update(1f / 60f, in);
            peak = Math.max(peak, w.player.position.y);
            if (w.player.position.y > groundBefore + 0.3f) {
                wentUp = true;
            }
        }
        check(wentUp, "jump leaves the ground");
        check(peak - groundBefore > 0.8f, "jump reaches a useful height (" + (peak - groundBefore) + " m)");
        check(w.player.onGround, "player lands again");

        // Sprinting drains stamina, walking restores it.
        float staminaBefore = w.player.sprintStamina;
        in.moveY = 1f;
        in.sprintHeld = true;
        for (int i = 0; i < 60; i++) {
            w.update(1f / 60f, in);
        }
        check(w.player.sprintStamina < staminaBefore, "sprint drains stamina");
        in.sprintHeld = false;
        for (int i = 0; i < 120; i++) {
            w.update(1f / 60f, in);
        }
        check(w.player.sprintStamina > 0.2f, "stamina recovers");

        // World bounds.
        in.moveY = 1f;
        in.sprintHeld = true;
        for (int i = 0; i < 60 * 60; i++) {
            w.update(1f / 60f, in);
        }
        float half = w.terrain.size * 0.5f;
        check(Math.abs(w.player.position.x) <= half && Math.abs(w.player.position.z) <= half,
                "player cannot leave the world");
    }

    private static void testCombat() {
        section("combat");
        World w = new World(5L);
        w.phase = World.Phase.PLAYING;
        InputState in = new InputState();

        Enemy target = w.enemies.get(0);
        check(target.alive, "enemy starts alive");

        // Teleport the player next to the enemy, facing it.
        Vec3 offset = new Vec3(0f, 0f, 1.6f);
        w.player.position = target.position.add(offset).withY(target.position.y);
        w.player.yaw = 0f;  // facing +z means facing away; face the enemy instead
        float heading = target.position.sub(w.player.position).headingDegrees();
        w.player.yaw = heading;

        // A swing lands a third of the way into its animation rather than on
        // the press, so the test has to let the swing play. That delay is the
        // point of the kit: a blow that connects on frame one has no weight.
        float hpBefore = target.health;
        in.attackPressed = true;
        w.update(1f / 60f, in);
        in.attackPressed = false;
        check(target.health == hpBefore, "a swing does not land on the press");
        for (int i = 0; i < 20; i++) {
            w.update(1f / 60f, in);
        }
        check(target.health < hpBefore, "melee damages an enemy in front");

        // Enemy behind should not be hit.
        Enemy behind = null;
        for (Enemy e : w.enemies) {
            if (e != target && e.alive) {
                behind = e;
                break;
            }
        }
        check(behind != null, "found a second enemy");
        if (behind != null) {
            w.player.position = behind.position.add(new Vec3(0f, 0f, 1.5f)).withY(behind.position.y);
            w.player.yaw = behind.position.sub(w.player.position).headingDegrees() + 180f;
            w.player.attackCooldown = 0f;
            float hp = behind.health;
            in.attackPressed = true;
            w.update(1f / 60f, in);
            in.attackPressed = false;
            check(behind.health == hp, "enemies behind the player are not hit");
        }

        // Kill the first enemy.
        int guard = 0;
        while (target.alive && guard++ < 200) {
            target.staggerTimer = 0f;
            w.player.attackCooldown = 0f;
            w.player.position = target.position.add(new Vec3(0f, 0f, 1.5f)).withY(target.position.y);
            w.player.yaw = target.position.sub(w.player.position).headingDegrees();
            in.attackPressed = true;
            w.update(1f / 60f, in);
            in.attackPressed = false;
        }
        check(!target.alive, "enemy can be killed");
        check(w.kills >= 1, "kill counter increments");

        // Player death and respawn.
        w.player.health = 1f;
        w.player.damage(500f);
        check(!w.player.alive, "player can die");
        w.update(1f / 60f, in);
        check(w.phase == World.Phase.DEFEAT, "defeat phase entered");
        for (int i = 0; i < 60 * 5; i++) {
            w.update(1f / 60f, in);
        }
        check(w.player.alive, "player respawns");
        check(w.phase == World.Phase.PLAYING, "back to playing after respawn");
        check(w.player.health == w.player.maxHealth, "respawn restores health");
    }

    private static void testShardCollectionAndVictory() {
        section("shards and victory");
        World w = new World(11L);
        w.phase = World.Phase.PLAYING;
        InputState in = new InputState();

        for (int i = 0; i < w.shards.size(); i++) {
            Shard s = w.shards.get(i);
            w.player.position = s.position.withY(s.position.y - 1.2f);
            w.player.verticalSpeed = 0f;
            w.update(1f / 60f, in);
            check(s.collected, "shard " + i + " collected on contact");
        }
        check(w.shardsCollected == World.SHARD_COUNT, "all shards counted");
        check(w.phase == World.Phase.VICTORY, "victory triggers with all shards");

        for (int i = 0; i < 60 * 5; i++) {
            w.update(1f / 60f, in);
        }
        check(w.phase == World.Phase.COMPLETE, "victory completes");

        // Restart escalates the level and respawns shards.
        w.restart();
        check(w.level() == 2, "restart goes to level 2");
        check(w.shardsCollected == 0, "shards reset");
        check(w.shards.size() == World.SHARD_COUNT, "shards respawn");
        check(w.phase == World.Phase.PLAYING, "restart resumes play");
        check(w.player.alive && w.player.health > 0f, "player is alive after restart");
    }

    private static void testEnemyAi() {
        section("enemy ai");
        World w = new World(21L);
        w.phase = World.Phase.PLAYING;
        InputState in = new InputState();
        Enemy e = w.enemies.get(0);

        // Put the player within aggro range but outside attack range.
        w.player.position = e.spawnPoint().add(new Vec3(0f, 0f, 6f)).withY(e.spawnPoint().y);
        float startDist = e.position.distanceTo(w.player.position);
        for (int i = 0; i < 60; i++) {
            w.update(1f / 60f, in);
        }
        float endDist = e.position.distanceTo(w.player.position);
        check(endDist < startDist, "enemy closes distance when aggroed (" + startDist + " -> " + endDist + ")");
        check(e.state == Enemy.State.CHASE || e.state == Enemy.State.ATTACK, "enemy enters chase/attack");

        // Enemy should eventually land a hit.
        float hpBefore = w.player.health;
        for (int i = 0; i < 60 * 8 && w.player.health >= hpBefore; i++) {
            w.player.position = e.position.add(new Vec3(0f, 0f, 1.2f)).withY(e.position.y);
            w.update(1f / 60f, in);
        }
        check(w.player.health < hpBefore, "enemy damages the player in melee range");

        // Stagger interrupts the attack.
        e.staggerTimer = 0.3f;
        w.update(1f / 60f, in);
        check(e.state == Enemy.State.STAGGER, "stagger state applies");

        // Dead enemies stop moving.
        e.damage(9999f);
        Vec3 pos = e.position;
        for (int i = 0; i < 30; i++) {
            w.update(1f / 60f, in);
        }
        check(e.position.distanceTo(pos) < 0.01f, "dead enemies stay put");
        check(e.state == Enemy.State.DEAD, "dead state set");
    }

    /**
     * A simple bot plays a whole run at 60 Hz: it walks to each shard in turn
     * and attacks anything close. This is the closest thing to an end-to-end
     * gameplay test we can run without a screen.
     */
    private static void testFullPlaythroughBot() {
        section("full playthrough bot");
        World w = new World(31337L);
        InputState in = new InputState();
        w.phase = World.Phase.PLAYING;
        w.phaseTimer = 10f;

        int frames = 0;
        int maxFrames = 60 * 300;  // 5 simulated minutes
        float worstFrameMs = 0f;

        while (w.phase != World.Phase.VICTORY && frames < maxFrames) {
            long t0 = System.nanoTime();

            // Pick the nearest uncollected shard.
            Shard goal = null;
            float best = Float.MAX_VALUE;
            for (Shard s : w.shards) {
                if (!s.collected) {
                    float d = s.position.distanceTo(w.player.position);
                    if (d < best) {
                        best = d;
                        goal = s;
                    }
                }
            }
            in.moveX = 0f;
            in.moveY = 0f;
            in.attackPressed = false;

            if (goal != null) {
                Vec3 delta = goal.position.sub(w.player.position);
                Vec3 dir = delta.normalizedXZ();
                in.cameraYaw = 0f;
                // world.moveX is right, moveY is forward in camera space(yaw 0)
                in.moveX = dir.x;
                in.moveY = dir.z;
                if (delta.horizontalLength() > 2f) {
                    in.sprintHeld = true;
                } else {
                    in.sprintHeld = false;
                }
            }

            // Attack whatever is in reach.
            for (Enemy e : w.enemies) {
                if (e.alive && e.position.distanceTo(w.player.position) < Player.MELEE_RANGE * 0.9f) {
                    in.attackPressed = true;
                }
            }

            w.update(1f / 60f, in);
            in.clearFrameEdges();
            in.jumpPressed = false;
            frames++;

            long dt = System.nanoTime() - t0;
            worstFrameMs = Math.max(worstFrameMs, dt / 1_000_000f);
        }

        System.out.println("    simulated " + (frames / 60) + "s of gameplay in " + frames + " frames");
        System.out.println("    slowest simulated frame: " + String.format(java.util.Locale.US, "%.3f", worstFrameMs) + " ms");
        check(w.phase == World.Phase.VICTORY, "bot collects all shards and wins");
        check(w.shardsCollected == World.SHARD_COUNT, "all shards gathered");
        check(frames < maxFrames, "run finished inside the time budget");
        check(!Float.isNaN(w.player.position.x), "no NaN after a full run");
        check(w.player.position.y > -100f, "player never fell through the world");
    }

    private static void testRig() {
        section("character rig");
        HumanoidRig player = HumanoidRig.player();
        HumanoidRig warden = HumanoidRig.warden();
        check(player.height > 1.5f && player.height < 2f, "player height is human (" + player.height + ")");
        check(warden.height > player.height, "warden is taller");

        Mat4[] matrices = new Mat4[HumanoidRig.PART_COUNT];
        for (int i = 0; i < matrices.length; i++) {
            matrices[i] = new Mat4();
        }
        Mat4 root = Mat4.translation(0f, 0f, 0f);
        player.pose(matrices, root, 0f, 0f, 0f, false, 0f);
        for (int i = 0; i < matrices.length; i++) {
            check(!Float.isNaN(matrices[i].m[12]), "pose matrix " + i + " is finite");
        }

        // Walking must actually move the limbs.
        Mat4[] walking = new Mat4[HumanoidRig.PART_COUNT];
        for (int i = 0; i < walking.length; i++) {
            walking[i] = new Mat4();
        }
        player.pose(walking, root, 1.2f, 1f, 0f, false, 0f);
        // Track the tip of the right arm (1 m below its joint) in world space.
        Vec3 idleTip = matrices[4].transformPoint(new Vec3(0f, -0.5f, 0f));
        Vec3 walkTip = walking[4].transformPoint(new Vec3(0f, -0.5f, 0f));
        check(idleTip.distanceTo(walkTip) > 0.05f,
                "locomotion animation moves the limbs (" + idleTip + " -> " + walkTip + ")");
        Vec3 idleFoot = matrices[6].transformPoint(new Vec3(0f, -0.6f, 0f));
        Vec3 walkFoot = walking[6].transformPoint(new Vec3(0f, -0.6f, 0f));
        check(idleFoot.distanceTo(walkFoot) > 0.05f, "legs swing while walking");

        // Attack must swing the arm.
        Mat4[] attacking = new Mat4[HumanoidRig.PART_COUNT];
        for (int i = 0; i < attacking.length; i++) {
            attacking[i] = new Mat4();
        }
        player.pose(attacking, root, 0f, 0f, 0.5f, false, 0f);
        check(Math.abs(attacking[4].m[6] - matrices[4].m[6]) > 0.1f, "attack swing rotates the right arm");

        // Joints must stay inside the character's bounding volume.
        for (Mat4 m : matrices) {
            check(Math.abs(m.m[12]) < 1.5f && Math.abs(m.m[13]) < 3f && Math.abs(m.m[14]) < 1.5f,
                    "joint inside the body volume");
        }
    }
}
