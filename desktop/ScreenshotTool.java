import com.laststar.game.art.HumanoidRig;
import com.laststar.game.core.Camera;
import com.laststar.game.core.Mat4;
import com.laststar.game.core.Noise;
import com.laststar.game.core.Vec3;
import com.laststar.game.gl.Geometry;
import com.laststar.game.gl.MeshData;
import com.laststar.game.gl.ModelLibrary;
import com.laststar.game.gl.PropPlanner;
import com.laststar.game.ui.Font;
import com.laststar.game.ui.Hud;
import com.laststar.game.ui.TouchControls;
import com.laststar.game.ui.UiBatch;
import com.laststar.game.world.Enemy;
import com.laststar.game.world.InputState;
import com.laststar.game.world.Pickup;
import com.laststar.game.world.Player;
import com.laststar.game.world.Projectile;
import com.laststar.game.world.Shard;
import com.laststar.game.world.World;

import java.io.File;
import java.util.List;

/**
 * Renders real frames of the game with the software rasteriser and saves them as
 * PNGs.
 *
 * <p>This is the project's visual test: it uses the exact terrain generator,
 * character rig, camera, world state and HUD code that ship in the APK, so the
 * images are representative of what runs on the phone.</p>
 *
 * <p>Usage: {@code java ScreenshotTool <outputDir> [width] [height]}</p>
 */
public final class ScreenshotTool {

    private static final Font FONT = new Font();
    /** The same baked prop set the APK ships, or null when it is not built yet. */
    private static ModelLibrary MODELS;

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "docs/screenshots";
        int w = args.length > 1 ? Integer.parseInt(args[1]) : 960;
        int h = args.length > 2 ? Integer.parseInt(args[2]) : 540;
        new File(outDir).mkdirs();
        MODELS = loadModels();

        shoot(outDir, w, h, "01-intro", Scenario.INTRO);
        shoot(outDir, w, h, "02-gameplay", Scenario.GAMEPLAY);
        shoot(outDir, w, h, "03-combat", Scenario.COMBAT);
        shoot(outDir, w, h, "04-shard", Scenario.SHARD);
        shoot(outDir, w, h, "05-night", Scenario.NIGHT);
        shoot(outDir, w, h, "06-victory", Scenario.VICTORY);
        shoot(outDir, w, h, "07-wander", Scenario.WANDER);

        System.out.println("screenshots written to " + outDir);
    }

    private enum Scenario {
        INTRO, GAMEPLAY, COMBAT, SHARD, NIGHT, VICTORY, WANDER
    }

    private static void shoot(String dir, int w, int h, String name, Scenario scenario) throws Exception {
        World world = new World(20261105L);
        Camera camera = new Camera();
        InputState input = new InputState();

        // Put the scenario where we want it in its timeline.
        switch (scenario) {
            case INTRO:
                world.phase = World.Phase.INTRO;
                world.phaseTimer = 1.6f;
                break;
            case GAMEPLAY:
                world.phase = World.Phase.PLAYING;
                world.worldTime = 40f;
                step(world, input, 30);
                break;
            case COMBAT: {
                world.phase = World.Phase.PLAYING;
                Enemy target = world.enemies.get(0);
                world.player.position = target.position.add(new Vec3(0f, 0f, 2.1f)).withY(target.position.y);
                world.player.yaw = target.position.sub(world.player.position).headingDegrees();
                world.player.attackAnim = Player.ATTACK_DURATION * 0.45f;
                world.player.attackCooldown = 0.1f;
                world.shakeAmount = 0.5f;
                for (int i = 0; i < 18; i++) {
                    for (Projectile p : world.projectiles) {
                        p.update(1f / 60f);
                    }
                }
                step(world, input, 20);
                break;
            }
            case SHARD: {
                world.phase = World.Phase.PLAYING;
                Shard s = world.shards.get(0);
                world.player.position = s.position.add(new Vec3(2.4f, -1.2f, 3.0f))
                        .withY(world.terrain.heightAt(s.position.x + 2.4f, s.position.z + 3.0f));
                world.player.yaw = s.position.sub(world.player.position).headingDegrees();
                step(world, input, 30);
                break;
            }
            case NIGHT: {
                world.phase = World.Phase.PLAYING;
                for (int i = 0; i < 4; i++) {
                    world.shards.get(i).collected = true;
                }
                world.shardsCollected = 4;
                step(world, input, 30);
                break;
            }
            case WANDER: {
                // Standing back from the book, so the shot shows the pickup,
                // its halo and enough of the meadow to read the setting.
                world.phase = World.Phase.PLAYING;
                Vec3 book = world.wanderPosition;
                world.player.position = book.add(new Vec3(2.6f, 0f, 3.4f))
                        .withY(world.terrain.heightAt(book.x + 2.6f, book.z + 3.4f));
                world.player.yaw = book.sub(world.player.position).headingDegrees();
                input.cameraYaw = world.player.yaw;
                step(world, input, 40);
                break;
            }
            case VICTORY:
                world.phase = World.Phase.VICTORY;
                world.phaseTimer = 1.4f;
                world.shardsCollected = World.SHARD_COUNT;
                world.kills = 17;
                world.deaths = 2;
                world.victoryFlash = 0.85f;
                for (Shard s : world.shards) {
                    s.collected = true;
                }
                break;
            default:
                break;
        }

        camera.snapBehind(world);
        camera.update(1f / 60f, world, input, 0f);
        for (int i = 0; i < 40; i++) {
            camera.update(1f / 60f, world, null, 0f);
        }

        float night = scenario == Scenario.NIGHT ? 0.75f : (scenario == Scenario.VICTORY ? 0f : 0.18f);
        float aspect = (float) w / (float) h;

        Raster raster = new Raster(w, h, camera.viewMatrix(), camera.projectionMatrix(aspect));
        raster.setCameraPosition(camera.position);
        drawWorld(raster, world, camera, aspect, night, 0f);

        // HUD pass in the same virtual space the game uses.
        TouchControls controls = new TouchControls();
        UiBatch ui = new UiBatch();
        ui.begin(720, 405);
        Hud hud = new Hud(FONT);
        hud.update(1f / 60f);
        if (scenario == Scenario.COMBAT) {
            world.player.health = 74f;
            controls.attack.pressAmount = 0.8f;
            ui.begin(720, 405);
            hud.draw(ui, world, controls, scenario == Scenario.COMBAT);
        } else {
            hud.draw(ui, world, controls, false);
        }
        blitUi(raster, ui, w, h);

        String path = dir + "/" + name + ".png";
        PngWriter.write(path, w, h, raster.argb);
        System.out.println("wrote " + path);
    }

    private static ModelLibrary loadModels() {
        try {
            ModelLibrary lib = ModelLibrary.load(new java.io.FileInputStream(
                    "app/src/main/assets/models/world.bin"));
            System.out.println("models: " + lib.size() + " imported props, "
                    + lib.triangleCount() + " triangles");
            return lib;
        } catch (Exception e) {
            System.out.println("models: unavailable (" + e.getMessage() + ")");
            return null;
        }
    }

    private static void step(World world, InputState in, int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f, in);
        }
    }

    // -------------------------------------------------------------- 3D pass

    private static void drawWorld(Raster raster, World world, Camera cam, float aspect,
                                  float night, float extraTime) {
        // Golden-hour anime palette: a warm cream horizon under a saturated
        // cerulean zenith, lit by a pale-gold sun. This is the same palette
        // SceneRenderer.updateSky produces for the APK.
        float horizonR = Noise.lerp(0.96f, 0.11f, night);
        float horizonG = Noise.lerp(0.86f, 0.12f, night);
        float horizonB = Noise.lerp(0.72f, 0.26f, night);
        float zenithR = Noise.lerp(0.20f, 0.03f, night);
        float zenithG = Noise.lerp(0.44f, 0.06f, night);
        float zenithB = Noise.lerp(0.78f, 0.16f, night);
        float sunR = Noise.lerp(1.00f, 0.44f, night);
        float sunG = Noise.lerp(0.95f, 0.52f, night);
        float sunB = Noise.lerp(0.76f, 0.86f, night);
        float ambR = Noise.lerp(0.46f, 0.09f, night);
        float ambG = Noise.lerp(0.52f, 0.12f, night);
        float ambB = Noise.lerp(0.68f, 0.24f, night);
        float fogD = Noise.lerp(0.0007f, 0.0016f, night);

        // Stars only appear once the sky has actually darkened.
        float starVisibility = Noise.clamp01((night - 0.45f) / 0.45f);

        float sunYaw = (float) Math.toRadians(42f + world.worldTime * 0.35f);
        float sunPitch = (float) Math.toRadians(Noise.lerp(34f, 12f, night));
        Vec3 sunDir = new Vec3(
                (float) Math.sin(sunYaw) * (float) Math.cos(sunPitch),
                (float) Math.sin(sunPitch),
                (float) Math.cos(sunYaw) * (float) Math.cos(sunPitch)).normalized();

        float camYawRad = (float) Math.toRadians(cam.yaw);
        float rel = sunYaw - camYawRad;
        float sunScreenX = raster.width * (0.5f + 0.42f * (float) Math.sin(rel));
        float sunScreenY = raster.height * (0.5f - 0.38f * (float) Math.sin(sunPitch));
        float visible = Math.cos(rel) > 0f ? 1f : 0f;

        // Configure the sky before drawing it: stars and cloud drift are read
        // inside drawSky.
        raster.setStars(starVisibility);
        raster.setSkyTime(night * 40f + extraTime);
        raster.drawSky(horizonR, horizonG, horizonB, zenithR, zenithG, zenithB,
                sunScreenX, sunScreenY, raster.height * 0.115f,
                sunR, sunG, sunB, visible);
        raster.setSun(sunDir.x, sunDir.y, sunDir.z, sunR, sunG, sunB);
        raster.setAmbient(ambR, ambG, ambB);
        raster.setFog(fogD, horizonR, horizonG, horizonB);
        raster.clearDepth();

        Mat4 identity = new Mat4();

        // Terrain.
        MeshData terrain = Geometry.terrain(world.terrain);
        raster.drawMesh(terrain.positions, terrain.normals, terrain.colors, terrain.indices,
                identity, 1f, 0f, null, 0f);

        // Water.
        MeshData water = Geometry.waterPlane(world.terrain.size * 1.4f, 44, world.terrain.waterLevel);
        float[] tint = {0.06f, 0.44f, 0.66f};
        raster.drawMesh(water.positions, water.normals, water.colors, water.indices,
                identity, 0.72f, 0.10f, tint, 0.62f);

        // Distant range: the same ring the renderer builds, centred on the
        // camera, so the horizon matches the APK.
        MeshData range = Geometry.layeredRange(520f, 96f, 56,
                world.worldSeed ^ 0x5A5A5A5AL, 230f);
        Mat4 rangMat = new Mat4();
        rangMat.m[12] = cam.position.x;
        rangMat.m[14] = cam.position.z;
        raster.drawMesh(range.positions, range.normals, range.colors, range.indices,
                rangMat, 1f, 0.06f, null, 0f);

        // Wind, matching the amplitudes SceneRenderer.swayFor uses.
        raster.setWind(world.worldTime + extraTime, 0.055f);

        // Set dressing: exactly the plan the renderer uses.
        if (MODELS != null) {
            PropPlanner props = new PropPlanner(MODELS, world.terrain, 20261105L);
            for (int i = 0; i < props.landmarks.size(); i++) {
                drawProp(raster, MODELS, props.landmarks.get(i));
            }
            PropPlanner.Grid grid = props.grid;
            for (int cz = 0; cz < grid.rows(); cz++) {
                for (int cx = 0; cx < grid.cols(); cx++) {
                    java.util.List<PropPlanner.Instance> cell = grid.cell(cx, cz);
                    for (int i = 0; i < cell.size(); i++) {
                        drawProp(raster, MODELS, cell.get(i));
                    }
                }
            }
        }

        // Shards.
        MeshData shard = Geometry.starShard(0.42f, 1f, 0.92f, 0.55f);
        MeshData ring = Geometry.groundRing(1f, 0.12f, 28, 1f, 1f, 1f, 1f);
        float[] shardTint = {1f, 0.93f, 0.52f};
        float[] wanderTint = {0.70f, 0.62f, 1f};
        float[] wanderHaloTint = {0.62f, 0.68f, 1f};
        float[] ringTint = {1f, 0.86f, 0.40f};
        for (Shard s : world.shards) {
            if (s.collected) {
                continue;
            }
            float y = s.position.y + s.bobOffset();
            Mat4 m = new Mat4().setMultiply(Mat4.translation(s.position.x, y, s.position.z),
                    Mat4.rotationY(s.spin));
            raster.drawMesh(shard.positions, shard.normals, shard.colors, shard.indices,
                    m, 1f, 0.85f, shardTint, 1f);
            float g = world.terrain.heightAt(s.position.x, s.position.z) + 0.04f;
            Mat4 rm = new Mat4().setMultiply(Mat4.translation(s.position.x, g, s.position.z),
                    Mat4.scale(1.2f, 1f, 1.2f));
            raster.drawMesh(ring.positions, ring.normals, ring.colors, ring.indices,
                    rm, 0.55f, 0.6f, ringTint, 1f);
        }

        // The Wander, before it is picked up: the same float, halo and tint the
        // renderer uses, so this shot is a real check on it.
        if (MODELS != null && !world.hasWander && world.wanderPosition != null) {
            int idx = -1;
            for (int i = 0; i < MODELS.size(); i++) {
                if (MODELS.get(i).name.equals("pk_spell-book")) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                Vec3 b = world.wanderPosition;
                float bob = (float) Math.sin(world.worldTime * 1.6f) * 0.13f;
                float y = b.y + 0.55f + bob;
                // Halos are additive in the renderer; the software path has no
                // blending, so it draws the ring flat, which is close enough to
                // confirm placement and size.
                Mat4 hm = new Mat4().setMultiply(
                        Mat4.translation(b.x, y, b.z), Mat4.scale(1.5f, 1.5f, 1.5f));
                raster.drawMesh(ring.positions, ring.normals, ring.colors,
                        ring.indices, hm, 0.40f, 0.5f, wanderHaloTint, 1f);
                Mat4 bm = new Mat4().setMultiply(
                        Mat4.translation(b.x, y, b.z), Mat4.rotationY(world.wanderSpin));
                ModelLibrary.Model book = MODELS.get(idx);
                raster.setWaveAttribute(book.waveArray());
                raster.drawMesh(book.positionArray(), book.normalArray(),
                        book.colorArray(), book.indexArray(),
                        bm, 0.42f, 0.55f, wanderTint, 0.55f);
            }
        }

        // Pickups.
        MeshData octa = Geometry.octahedron(0.28f, 0.35f, 0.95f, 0.62f);
        float[] healthTint = {0.40f, 1f, 0.55f};
        for (Pickup p : world.pickups) {
            Mat4 m = new Mat4().setMultiply(
                    Mat4.translation(p.position.x, p.position.y + p.bobOffset(), p.position.z),
                    Mat4.rotationY(p.spin));
            raster.drawMesh(octa.positions, octa.normals, octa.colors, octa.indices,
                    m, 1f, 0.7f, healthTint, 1f);
        }

        // Characters.
        HumanoidRig playerRig = HumanoidRig.player();
        HumanoidRig wardenRig = HumanoidRig.warden();
        Mat4[] pose = new Mat4[HumanoidRig.PART_COUNT];
        for (int i = 0; i < pose.length; i++) {
            pose[i] = new Mat4();
        }

        for (Enemy e : world.enemies) {
            if (!e.alive) {
                continue;
            }
            float move = e.state == Enemy.State.CHASE ? 1f : 0.2f;
            float attack = e.state == Enemy.State.ATTACK ? e.windupFraction() : 0f;
            Mat4 root = new Mat4().setMultiply(Mat4.translation(e.position.x, e.position.y, e.position.z),
                    Mat4.rotationY(e.yaw));
            wardenRig.pose(pose, root, e.animPhase, move, attack, !e.onGround, e.hitFlash);
            drawRig(raster, wardenRig, pose, 0f);
        }

        Player p = world.player;
        float moveAmount = Noise.clamp01(p.velocity.horizontalLength() / 5.5f);
        Mat4 root = new Mat4().setMultiply(Mat4.translation(p.position.x, p.position.y, p.position.z),
                Mat4.rotationY(p.yaw));
        playerRig.pose(pose, root, p.animPhase, moveAmount, p.attackFraction(), !p.onGround, p.hitFlash);
        drawRig(raster, playerRig, pose, 0f);

        // Particles as camera-facing quads.
        MeshData quad = Geometry.quad(1f, 1f, 1f, 1f, 1f);
        Mat4 view = cam.viewMatrix();
        Vec3 right = new Vec3(view.m[0], view.m[4], view.m[8]);
        Vec3 up = new Vec3(view.m[1], view.m[5], view.m[9]);
        for (Projectile pr : world.projectiles) {
            float s = pr.size * (0.6f + pr.lifeFraction() * 1.4f);
            float a = pr.lifeFraction() * 0.9f;
            float r = ((pr.colour >> 16) & 0xFF) / 255f;
            float g = ((pr.colour >> 8) & 0xFF) / 255f;
            float b = (pr.colour & 0xFF) / 255f;
            Mat4 billboard = new Mat4();
            billboard.m[0] = right.x * s;
            billboard.m[1] = right.y * s;
            billboard.m[2] = right.z * s;
            billboard.m[4] = up.x * s;
            billboard.m[5] = up.y * s;
            billboard.m[6] = up.z * s;
            billboard.m[12] = pr.position.x;
            billboard.m[13] = pr.position.y;
            billboard.m[14] = pr.position.z;
            billboard.m[15] = 1f;
            raster.drawMesh(quad.positions, quad.normals, quad.colors, quad.indices,
                    billboard, a, 0.9f, new float[]{r, g, b}, 1f);
        }
    }

    private static void drawProp(Raster raster, ModelLibrary models, PropPlanner.Instance in) {
        ModelLibrary.Model m = models.get(in.model);
        Mat4 model = new Mat4().setMultiply(Mat4.translation(in.x, in.y, in.z),
                Mat4.rotationY(in.yaw));
        if (in.scale != 1f) {
            model = new Mat4().setMultiply(model, Mat4.scale(in.scale, in.scale, in.scale));
        }
        float[] tint = in.tintAmount > 0f
                ? new float[]{in.tintR, in.tintG, in.tintB} : null;
        // Feed the wind phase so foliage bends exactly as the vertex shader
        // bends it on the device.
        raster.setWaveAttribute(m.waveArray());
        raster.drawMesh(m.positionArray(), m.normalArray(), m.colorArray(), m.indexArray(),
                model, 1f, 0f, tint, in.tintAmount);
    }

    private static void drawRig(Raster raster, HumanoidRig rig, Mat4[] pose, float flash) {
        MeshData[] parts = {
                rig.torso.mesh, rig.hips.mesh, rig.head.mesh, rig.leftArm.mesh, rig.rightArm.mesh,
                rig.leftLeg.mesh, rig.rightLeg.mesh, rig.weapon.mesh, rig.cape.mesh
        };
        for (int i = 0; i < parts.length; i++) {
            MeshData m = parts[i];
            raster.drawMesh(m.positions, m.normals, m.colors, m.indices, pose[i],
                    1f, i == 7 ? 0.8f : 0f, null, 0f);
        }
    }

    private static float hash(int i) {
        float x = (float) Math.sin(i * 127.1f) * 43758.5453f;
        return x - (float) Math.floor(x);
    }

    // ---------------------------------------------------------------- 2D pass

    /** Draws the accumulated UI batch on top of the rasterised frame. */
    private static void blitUi(Raster raster, UiBatch batch, int w, int h) {
        float sx = w / (float) batch.virtualWidth;
        float sy = h / (float) batch.virtualHeight;
        List<UiBatch.Vertex> verts = batch.vertices();
        for (int i = 0; i + 2 < verts.size(); i += 3) {
            UiBatch.Vertex a = verts.get(i);
            UiBatch.Vertex b = verts.get(i + 1);
            UiBatch.Vertex c = verts.get(i + 2);
            int col = Raster.pack(a.r, a.g, a.b, 1f);
            float alpha = a.a;
            if (!a.textured) {
                raster.fillTriangle2D(
                        a.x * sx, a.y * sy, b.x * sx, b.y * sy, c.x * sx, c.y * sy,
                        new float[]{a.r, a.g, a.b, a.a},
                        new float[]{b.r, b.g, b.b, b.a},
                        new float[]{c.r, c.g, c.b, c.a});
                continue;
            }
            if (a.textured) {
                // Text glyph: sample the atlas through the quad's UV rectangle.
                float minX = Math.min(a.x, Math.min(b.x, c.x)) * sx;
                float maxX = Math.max(a.x, Math.max(b.x, c.x)) * sx;
                float minY = Math.min(a.y, Math.min(b.y, c.y)) * sy;
                float maxY = Math.max(a.y, Math.max(b.y, c.y)) * sy;
                float u0 = Math.min(a.u, Math.min(b.u, c.u));
                float u1 = Math.max(a.u, Math.max(b.u, c.u));
                float v0 = Math.min(a.v, Math.min(b.v, c.v));
                float v1 = Math.max(a.v, Math.max(b.v, c.v));
                raster.fillTexturedQuad2D(minX, minY, maxX, maxY, u0, v0, u1, v1,
                        FONT.atlas, FONT.atlasWidth, FONT.atlasHeight, col, alpha);
            }
        }
    }
}
