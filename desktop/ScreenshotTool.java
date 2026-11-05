import com.laststar.game.art.HumanoidRig;
import com.laststar.game.core.Camera;
import com.laststar.game.core.Mat4;
import com.laststar.game.core.Noise;
import com.laststar.game.core.Vec3;
import com.laststar.game.gl.Geometry;
import com.laststar.game.gl.MeshData;
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

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "docs/screenshots";
        int w = args.length > 1 ? Integer.parseInt(args[1]) : 960;
        int h = args.length > 2 ? Integer.parseInt(args[2]) : 540;
        new File(outDir).mkdirs();

        shoot(outDir, w, h, "01-intro", Scenario.INTRO);
        shoot(outDir, w, h, "02-gameplay", Scenario.GAMEPLAY);
        shoot(outDir, w, h, "03-combat", Scenario.COMBAT);
        shoot(outDir, w, h, "04-shard", Scenario.SHARD);
        shoot(outDir, w, h, "05-night", Scenario.NIGHT);
        shoot(outDir, w, h, "06-victory", Scenario.VICTORY);

        System.out.println("screenshots written to " + outDir);
    }

    private enum Scenario {
        INTRO, GAMEPLAY, COMBAT, SHARD, NIGHT, VICTORY
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

    private static void step(World world, InputState in, int frames) {
        for (int i = 0; i < frames; i++) {
            world.update(1f / 60f, in);
        }
    }

    // -------------------------------------------------------------- 3D pass

    private static void drawWorld(Raster raster, World world, Camera cam, float aspect,
                                  float night, float extraTime) {
        float horizonR = Noise.lerp(0.62f, 0.10f, night);
        float horizonG = Noise.lerp(0.58f, 0.12f, night);
        float horizonB = Noise.lerp(0.62f, 0.26f, night);
        float zenithR = Noise.lerp(0.16f, 0.03f, night);
        float zenithG = Noise.lerp(0.26f, 0.05f, night);
        float zenithB = Noise.lerp(0.52f, 0.13f, night);
        float sunR = Noise.lerp(1.00f, 0.62f, night);
        float sunG = Noise.lerp(0.84f, 0.72f, night);
        float sunB = Noise.lerp(0.58f, 0.95f, night);
        float ambR = Noise.lerp(0.42f, 0.20f, night);
        float ambG = Noise.lerp(0.44f, 0.23f, night);
        float ambB = Noise.lerp(0.50f, 0.34f, night);
        float fogD = Noise.lerp(0.0062f, 0.0105f, night);

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
        float[] tint = {0.30f, 0.62f, 0.78f};
        raster.drawMesh(water.positions, water.normals, water.colors, water.indices,
                identity, 0.72f, 0.06f, tint, 0.55f);

        // Props: monoliths and trees, same placement as the renderer.
        MeshData pillar = Geometry.cylinder(0.55f, 0.42f, 5.5f, 10, 0.55f, 0.57f, 0.62f, 1f);
        float[] pillarTint = {0.52f, 0.54f, 0.60f};
        for (int i = 0; i < 14; i++) {
            float ang = (float) (i * Math.PI * 2.0 / 14.0) + 0.31f;
            float dist = 30f + (i % 4) * 11f;
            float x = (float) Math.cos(ang) * dist;
            float z = (float) Math.sin(ang) * dist;
            float hgt = world.terrain.heightAt(x, z);
            if (hgt < world.terrain.waterLevel + 0.4f) {
                continue;
            }
            float scale = 0.8f + (i % 3) * 0.35f;
            Mat4 m = new Mat4().setMultiply(Mat4.translation(x, hgt - 0.4f, z),
                    Mat4.scale(scale, scale, scale));
            raster.drawMesh(pillar.positions, pillar.normals, pillar.colors, pillar.indices,
                    m, 1f, 0f, pillarTint, 0.85f);
        }

        MeshData cube = Geometry.box(1, 1, 1, 1, 1, 1, 1, 0, 0, 0);
        MeshData ball = Geometry.sphere(1f, 6, 8, 1f, 1f, 1f, 1f);
        float[] trunkTint = {0.20f, 0.15f, 0.11f};
        for (int i = 0; i < 150; i++) {
            float rx = (hash(i * 3 + 1) - 0.5f) * world.terrain.size * 0.92f;
            float rz = (hash(i * 7 + 5) - 0.5f) * world.terrain.size * 0.92f;
            float hgt = world.terrain.heightAt(rx, rz);
            if (hgt < world.terrain.waterLevel + 0.6f || hgt > 9f) {
                continue;
            }
            if (world.terrain.slopeAt(rx, rz) > 0.55f) {
                continue;
            }
            if (rx * rx + rz * rz < 64f) {
                continue;
            }
            float s = 0.7f + hash(i * 13 + 3) * 1.1f;
            Mat4 trunk = new Mat4().setMultiply(Mat4.translation(rx, hgt, rz),
                    Mat4.scale(0.16f * s, 1.5f * s, 0.16f * s));
            raster.drawMesh(cube.positions, cube.normals, cube.colors, cube.indices,
                    trunk, 1f, 0f, trunkTint, 0.9f);
            float leaf = hash(i * 17 + 9);
            float[] canopyTint = {0.13f + leaf * 0.09f, 0.30f + leaf * 0.20f, 0.14f};
            Mat4 canopy = new Mat4().setMultiply(Mat4.translation(rx, hgt + 1.6f * s, rz),
                    Mat4.scale(1.15f * s, 1.25f * s, 1.15f * s));
            raster.drawMesh(ball.positions, ball.normals, ball.colors, ball.indices,
                    canopy, 1f, 0f, canopyTint, 0.95f);
        }

        // Shards.
        MeshData shard = Geometry.starShard(0.42f, 1f, 0.92f, 0.55f);
        MeshData ring = Geometry.groundRing(1f, 0.12f, 28, 1f, 1f, 1f, 1f);
        float[] shardTint = {1f, 0.93f, 0.52f};
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
