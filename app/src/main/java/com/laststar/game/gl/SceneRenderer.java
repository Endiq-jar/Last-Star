package com.laststar.game.gl;

import android.opengl.GLES20;

import com.laststar.game.art.HumanoidRig;
import com.laststar.game.core.Camera;
import com.laststar.game.core.Mat4;
import com.laststar.game.core.Noise;
import com.laststar.game.core.Vec3;
import com.laststar.game.world.Enemy;
import com.laststar.game.world.Pickup;
import com.laststar.game.world.Projectile;
import com.laststar.game.world.Shard;
import com.laststar.game.world.World;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Draws the 3D world: sky, terrain, water, props, characters, particles.
 *
 * <p>All geometry is generated procedurally at start-up; the renderer therefore
 * owns no assets and only a handful of GL objects.</p>
 */
public final class SceneRenderer {

    /** Time-of-day driven palette, blended over the run. */
    public static final class Sky {
        public float horizonR = 0.42f, horizonG = 0.51f, horizonB = 0.62f;
        public float zenithR = 0.09f, zenithG = 0.16f, zenithB = 0.34f;
        public float sunR = 1.00f, sunG = 0.86f, sunB = 0.62f;
        public float ambientR = 0.35f, ambientG = 0.40f, ambientB = 0.48f;
        public float fogDensity = 0.0030f;
    }

    private final Sky sky = new Sky();
    private float sunYaw = 42f;
    private float sunPitch = 34f;

    private GlProgram sceneProgram;
    private GlProgram skyProgram;

    private Mesh terrainMesh;
    private Mesh waterMesh;
    private Mesh sphereMesh;
    private Mesh shardMesh;
    private Mesh particleMesh;
    private Mesh ringMesh;
    private Mesh cubeMesh;
    private Mesh pillarMesh;

    private Mesh[] playerParts;
    private Mesh[] wardenParts;
    private Mesh[] pickups;

    private final Mat4[] poseMatrices = new Mat4[HumanoidRig.PART_COUNT];
    private final Mat4 modelMatrix = new Mat4();
    private final Mat4 mvp = new Mat4();
    private final Mat4 tmp = new Mat4();
    private HumanoidRig playerRig;
    private HumanoidRig wardenRig;

    private int skyVbo;
    private int skyVertexCount;

    private float time;

    public void init(World world) {
        sceneProgram = GlProgram.create(Shaders.SCENE_VERT, Shaders.SCENE_FRAG);
        skyProgram = GlProgram.create(Shaders.SKY_VERT, Shaders.SKY_FRAG);

        terrainMesh = toMesh(Geometry.terrain(world.terrain));
        waterMesh = toMesh(Geometry.waterPlane(world.terrain.size * 1.4f, 44, world.terrain.waterLevel));


        sphereMesh = toMesh(Geometry.sphere(1f, 10, 14, 1f, 1f, 1f, 1f));
        shardMesh = toMesh(Geometry.starShard(0.42f, 1f, 0.92f, 0.55f));
        particleMesh = toMesh(Geometry.quad(1f, 1f, 1f, 1f, 1f));
        ringMesh = toMesh(Geometry.groundRing(1f, 0.12f, 28, 1f, 1f, 1f, 1f));
        cubeMesh = toMesh(Geometry.box(1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f));
        pillarMesh = toMesh(Geometry.cylinder(0.55f, 0.42f, 5.5f, 10, 0.55f, 0.57f, 0.62f, 1f));

        playerRig = HumanoidRig.player();
        wardenRig = HumanoidRig.warden();
        playerParts = rigMeshes(playerRig);
        wardenParts = rigMeshes(wardenRig);
        pickups = new Mesh[]{
                toMesh(Geometry.octahedron(0.28f, 0.35f, 0.95f, 0.62f)),
                toMesh(Geometry.octahedron(0.26f, 0.98f, 0.78f, 0.30f))
        };

        for (int i = 0; i < poseMatrices.length; i++) {
            poseMatrices[i] = new Mat4();
        }

        buildSkyQuad();
    }

    private Mesh[] rigMeshes(HumanoidRig rig) {
        return new Mesh[]{
                toMesh(rig.torso.mesh), toMesh(rig.hips.mesh), toMesh(rig.head.mesh),
                toMesh(rig.leftArm.mesh), toMesh(rig.rightArm.mesh),
                toMesh(rig.leftLeg.mesh), toMesh(rig.rightLeg.mesh),
                toMesh(rig.weapon.mesh), toMesh(rig.cape.mesh)
        };
    }

    private static Mesh toMesh(MeshData d) {
        return Mesh.create(d);
    }

    private void buildSkyQuad() {
        float[] verts = {-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f};
        FloatBuffer fb = ByteBuffer.allocateDirect(verts.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(verts).position(0);
        int[] buf = new int[1];
        GLES20.glGenBuffers(1, buf, 0);
        skyVbo = buf[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.length * 4, fb, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        skyVertexCount = 6;
    }

    // ------------------------------------------------------------------ frame

    public void draw(World world, Camera camera, float aspect, float dt, float nightAmount) {
        time += dt;
        updateSky(world, camera, nightAmount);

        GLES20.glClearColor(sky.horizonR * 0.8f, sky.horizonG * 0.8f, sky.horizonB * 0.85f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        Mat4 view = camera.viewMatrix();
        Mat4 proj = camera.projectionMatrix(aspect);

        drawSky(camera);

        /*************************************************************/
        /* 3D pass */
        /*************************************************************/
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_BLEND);

        sceneProgram.use();
        sceneProgram.setSun(sunDir().x, sunDir().y, sunDir().z, sky.sunR, sky.sunG, sky.sunB);
        sceneProgram.setAmbient(sky.ambientR, sky.ambientG, sky.ambientB);
        sceneProgram.setFog(sky.fogDensity, sky.horizonR, sky.horizonG, sky.horizonB);
        sceneProgram.setVec3("uCamPos", camera.position.x, camera.position.y, camera.position.z);

        // Terrain.
        modelMatrix.identity();
        mvp.setMultiply(proj, view);
        sceneProgram.setMvp(mvp.m);
        sceneProgram.setModel(modelMatrix.m);
        sceneProgram.setAlpha(1f);
        sceneProgram.setEmissive(0f);
        sceneProgram.setTintAmount(0f);
        sceneProgram.setTime(time);
        sceneProgram.setFloat("uWaveAmp", 0f);
        drawMesh(terrainMesh);

        drawProps(world, camera);
        drawShards(world);
        drawPickups(world);
        drawEnemies(world);
        drawPlayer(world);

        // Water last among opaque geometry, additively blended on top.
        drawWater(world, camera);

        drawParticles(world, camera);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    private void drawProps(World world, Camera camera) {
        // Ancient monoliths placed on a deterministic ring, plus spawn beacon.
        sceneProgram.setTintAmount(0f);
        for (int i = 0; i < 14; i++) {
            float ang = (float) (i * Math.PI * 2.0 / 14.0) + 0.31f;
            float dist = 30f + (i % 4) * 11f;
            float x = (float) Math.cos(ang) * dist;
            float z = (float) Math.sin(ang) * dist;
            float h = world.terrain.heightAt(x, z);
            if (h < world.terrain.waterLevel + 0.4f) {
                continue;
            }
            float scale = 0.8f + (i % 3) * 0.35f;
            modelMatrix.setMultiply(
                    Mat4.translation(x, h - 0.4f, z),
                    Mat4.scale(scale, scale, scale));
            tmp.setMultiply(frameViewProj, modelMatrix);
            sceneProgram.setMvp(tmp.m);
            sceneProgram.setModel(modelMatrix.m);
            sceneProgram.setTint(0.52f, 0.54f, 0.60f, 0.85f);
            drawMesh(pillarMesh);
        }

        // Trees / rocks scattered on the terrain to give the eye scale cues.
        sceneProgram.setTintAmount(1f);
        int trees = 150;
        for (int i = 0; i < trees; i++) {
            float rx = (hash(i * 3 + 1) - 0.5f) * world.terrain.size * 0.92f;
            float rz = (hash(i * 7 + 5) - 0.5f) * world.terrain.size * 0.92f;
            float h = world.terrain.heightAt(rx, rz);
            if (h < world.terrain.waterLevel + 0.6f) {
                continue;
            }
            if (world.terrain.slopeAt(rx, rz) > 0.55f) {
                continue;
            }
            float dist2 = rx * rx + rz * rz;
            if (dist2 < 64f) {
                continue;
            }
            float s = 0.7f + hash(i * 13 + 3) * 1.1f;
            if (world.terrain.heightAt(rx, rz) > 9f) {
                continue;
            }
            // Trunk
            modelMatrix.setMultiply(Mat4.translation(rx, h, rz), Mat4.scale(0.16f * s, 1.5f * s, 0.16f * s));
            tmp.setMultiply(frameViewProj, modelMatrix);
            sceneProgram.setMvp(tmp.m);
            sceneProgram.setModel(modelMatrix.m);
            sceneProgram.setTint(0.20f, 0.15f, 0.11f, 0.9f);
            drawMesh(cubeMesh);
            // Canopy
            float leaf = hash(i * 17 + 9);
            modelMatrix.setMultiply(
                    Mat4.translation(rx, h + 1.6f * s, rz),
                    Mat4.scale(1.15f * s, 1.25f * s, 1.15f * s));
            tmp.setMultiply(frameViewProj, modelMatrix);
            sceneProgram.setMvp(tmp.m);
            sceneProgram.setModel(modelMatrix.m);
            sceneProgram.setTint(0.13f + leaf * 0.09f, 0.30f + leaf * 0.20f, 0.14f, 0.95f);
            drawMesh(sphereMesh);
        }
    }

    private float lastAspect = 1.7f;

    private void drawShards(World world) {
        sceneProgram.setTintAmount(1f);
        for (Shard s : world.shards) {
            if (s.collected) {
                continue;
            }
            float y = s.position.y + s.bobOffset();
            modelMatrix.setMultiply(
                    Mat4.translation(s.position.x, y, s.position.z),
                    Mat4.rotationY(s.spin));
            tmp.setMultiply(frameViewProj, modelMatrix);
            sceneProgram.setMvp(tmp.m);
            sceneProgram.setModel(modelMatrix.m);
            sceneProgram.setTint(1f, 0.93f, 0.52f, 1f);
            sceneProgram.setEmissive(0.85f);
            drawMesh(shardMesh);
            sceneProgram.setEmissive(0f);

            // Ground marker so shards are findable from a distance.
            modelMatrix.setMultiply(
                    Mat4.translation(s.position.x, world.terrain.heightAt(s.position.x, s.position.z) + 0.04f, s.position.z),
                    Mat4.scale(1.2f, 1f, 1.2f));
            tmp.setMultiply(frameViewProj, modelMatrix);
            sceneProgram.setMvp(tmp.m);
            sceneProgram.setModel(modelMatrix.m);
            sceneProgram.setTint(1f, 0.86f, 0.40f, 0.55f);
            sceneProgram.setEmissive(0.6f);
            drawMesh(ringMesh);
            sceneProgram.setEmissive(0f);
        }
    }

    private void drawPickups(World world) {
        sceneProgram.setTintAmount(1f);
        for (Pickup p : world.pickups) {
            float y = p.position.y + p.bobOffset();
            modelMatrix.setMultiply(Mat4.translation(p.position.x, y, p.position.z), Mat4.rotationY(p.spin));
            tmp.setMultiply(frameViewProj, modelMatrix);
            sceneProgram.setMvp(tmp.m);
            sceneProgram.setModel(modelMatrix.m);
            if (p.type == Pickup.Type.HEALTH) {
                sceneProgram.setTint(0.40f, 1f, 0.55f, 1f);
            } else {
                sceneProgram.setTint(1f, 0.82f, 0.30f, 1f);
            }
            sceneProgram.setEmissive(0.7f);
            drawMesh(pickups[0]);
            sceneProgram.setEmissive(0f);
        }
    }

    private void drawEnemies(World world) {
        sceneProgram.setTintAmount(0f);
        for (Enemy e : world.enemies) {
            if (!e.alive) {
                continue;
            }
            float moveAmount = e.state == Enemy.State.CHASE ? 1f : (e.state == Enemy.State.ATTACK ? 0.35f : 0.1f);
            float attack = 0f;
            if (e.state == Enemy.State.ATTACK) {
                attack = e.windupFraction();
            }
            modelAt(e.position, e.yaw);
            wardenRig.pose(poseMatrices, modelMatrix, e.animPhase, moveAmount, attack,
                    !e.onGround, e.hitFlash);
            drawRig(wardenParts, 1f, 0.0f);
        }
    }

    private void drawPlayer(World world) {
        sceneProgram.setTintAmount(0f);
        com.laststar.game.world.Player p = world.player;
        float speed = p.velocity.horizontalLength();
        float moveAmount = Noise.clamp01(speed / 5.5f);
        modelAt(p.position, p.yaw);
        playerRig.pose(poseMatrices, modelMatrix, p.animPhase, moveAmount, p.attackFraction(),
                !p.onGround, p.hitFlash);
        float flashBlend = p.invulnerability > 0f ? 0.35f + 0.25f * (float) Math.sin(time * 26f) : 0f;
        drawRig(playerParts, 1f, flashBlend);
    }

    /** Camera-projection cache refreshed once per frame for the prop passes. */
    private Mat4 cameraProjCache = new Mat4();
    private Mat4 cameraViewCache = new Mat4();
    /** Projection * view for the current frame. */
    private Mat4 frameViewProj = new Mat4();
    private Mat4 rigScratch = new Mat4();

    private void modelAt(Vec3 pos, float yawDeg) {
        modelMatrix.setMultiply(Mat4.translation(pos.x, pos.y, pos.z), Mat4.rotationY(yawDeg));
    }

    private void drawRig(Mesh[] parts, float alpha, float hitBlend) {
        sceneProgram.setAlpha(alpha);
        sceneProgram.setTintAmount(1f);
        for (int i = 0; i < parts.length; i++) {
            Mat4 part = poseMatrices[i];
            rigScratch.setMultiply(frameViewProj, part);
            sceneProgram.setMvp(rigScratch.m);
            sceneProgram.setModel(part.m);
            if (hitBlend > 0f) {
                sceneProgram.setTint(1f, 0.35f, 0.30f, hitBlend);
            } else {
                sceneProgram.setTint(0f, 0f, 0f, 0f);
            }
            drawMesh(parts[i]);
        }
        sceneProgram.setAlpha(1f);
        sceneProgram.setTintAmount(0f);
    }

    private void drawWater(World world, Camera camera) {
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glDepthMask(false);

        float yaw = time * 0.02f;
        modelMatrix.setMultiply(
                Mat4.translation(camera.position.x * 0.35f, 0f, camera.position.z * 0.35f),
                Mat4.rotationY(yaw));
        sceneProgram.use();
        rigScratch.setMultiply(frameViewProj, modelMatrix);
        sceneProgram.setMvp(rigScratch.m);
        sceneProgram.setModel(modelMatrix.m);
        sceneProgram.setFloat("uWaveAmp", 0.10f);
        sceneProgram.setTime(time);
        sceneProgram.setTint(0.30f, 0.62f, 0.78f, 0.72f);
        sceneProgram.setTintAmount(0.55f);
        sceneProgram.setEmissive(0.06f);
        drawMesh(waterMesh);

        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
        sceneProgram.setTintAmount(0f);
        sceneProgram.setEmissive(0f);
        sceneProgram.setFloat("uWaveAmp", 0f);
    }

    private void drawMesh(Mesh mesh) {
        if (mesh == null) {
            return;
        }
        sceneProgram.bindMesh(mesh);
        mesh.draw();
        sceneProgram.unbindMesh(mesh);
    }

    private void drawParticles(World world, Camera camera) {
        if (world.projectiles.isEmpty()) {
            return;
        }
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glDepthMask(false);
        sceneProgram.use();

        // Billboard: build a view-facing basis from the camera right/up vectors.
        Vec3 right = new Vec3(cameraViewCache.m[0], cameraViewCache.m[4], cameraViewCache.m[8]);
        Vec3 up = new Vec3(cameraViewCache.m[1], cameraViewCache.m[5], cameraViewCache.m[9]);

        for (Projectile p : world.projectiles) {
            float s = p.size * (0.6f + p.lifeFraction() * 1.4f);
            float r = ((p.colour >> 16) & 0xFF) / 255f;
            float g = ((p.colour >> 8) & 0xFF) / 255f;
            float b = (p.colour & 0xFF) / 255f;
            float a = p.lifeFraction() * 0.9f;

            Mat4 billboard = new Mat4();
            billboard.m[0] = right.x; billboard.m[1] = right.y; billboard.m[2] = right.z;
            billboard.m[4] = up.x; billboard.m[5] = up.y; billboard.m[6] = up.z;
            billboard.m[12] = p.position.x; billboard.m[13] = p.position.y; billboard.m[14] = p.position.z;
            billboard.m[15] = 1f;
            billboard.m[0] *= s; billboard.m[1] *= s; billboard.m[2] *= s;
            billboard.m[4] *= s; billboard.m[5] *= s; billboard.m[6] *= s;

            rigScratch.setMultiply(frameViewProj, billboard);
            sceneProgram.setMvp(rigScratch.m);
            sceneProgram.setModel(billboard.m);
            sceneProgram.setTint(r, g, b, 1f);
            sceneProgram.setTintAmount(1f);
            sceneProgram.setEmissive(0.9f);
            sceneProgram.setAlpha(a);
            // Reuse the scene program with the particle quad.
            drawMesh(particleMesh);
            sceneProgram.setAlpha(1f);
        }
        sceneProgram.setTintAmount(0f);
        sceneProgram.setEmissive(0f);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    // -------------------------------------------------------------------- sky

    private Vec3 sunDir() {
        float yawRad = (float) Math.toRadians(sunYaw);
        float pitchRad = (float) Math.toRadians(sunPitch);
        return new Vec3(
                (float) Math.sin(yawRad) * (float) Math.cos(pitchRad),
                (float) Math.sin(pitchRad),
                (float) Math.cos(yawRad) * (float) Math.cos(pitchRad)).normalized();
    }

    private void updateSky(World world, Camera camera, float night) {
        // Dusk palette lerped toward night as the run progresses.
        sky.horizonR = Noise.lerp(0.80f, 0.13f, night);
        sky.horizonG = Noise.lerp(0.64f, 0.14f, night);
        sky.horizonB = Noise.lerp(0.58f, 0.28f, night);
        sky.zenithR = Noise.lerp(0.20f, 0.03f, night);
        sky.zenithG = Noise.lerp(0.34f, 0.05f, night);
        sky.zenithB = Noise.lerp(0.66f, 0.15f, night);
        sky.sunR = Noise.lerp(1.00f, 0.62f, night);
        sky.sunG = Noise.lerp(0.84f, 0.72f, night);
        sky.sunB = Noise.lerp(0.58f, 0.95f, night);
        sky.ambientR = Noise.lerp(0.55f, 0.26f, night);
        sky.ambientG = Noise.lerp(0.58f, 0.30f, night);
        sky.ambientB = Noise.lerp(0.66f, 0.44f, night);
        sky.fogDensity = Noise.lerp(0.0030f, 0.0058f, night);
        sunPitch = Noise.lerp(34f, 12f, night);
        sunYaw = 42f + world.worldTime * 0.35f;
    }

    private void drawSky(Camera camera) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        skyProgram.use();

        Vec3 sd = sunDir();
        // Project the sun direction into screen space roughly.
        float yawRad = (float) Math.toRadians(camera.yaw);
        float rel = (float) Math.toRadians(sunYaw) - yawRad;
        float sx = 0.5f + 0.42f * (float) Math.sin(rel);
        float sy = 0.5f + 0.40f * (float) (Math.sin(Math.toRadians(sunPitch)));
        float visible = (float) Math.cos(rel) > 0f ? 1f : 0f;

        skyProgram.setVec3("uHorizon", sky.horizonR, sky.horizonG, sky.horizonB);
        skyProgram.setVec3("uZenith", sky.zenithR, sky.zenithG, sky.zenithB);
        skyProgram.setVec3("uSunColor", sky.sunR, sky.sunG, sky.sunB);
        skyProgram.setFloat("uTime", time);
        skyProgram.setFloat("uSunVisible", visible);
        int loc = GLES20.glGetUniformLocation(skyProgram.id, "uSunScreen");
        if (loc >= 0) {
            GLES20.glUniform2f(loc, sx, sy);
        }

        int aPos = GLES20.glGetAttribLocation(skyProgram.id, "aPos");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skyVbo);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, skyVertexCount);
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glDepthMask(true);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    // -------------------------------------------------------------- utilities

    private float hash(int i) {
        float x = (float) Math.sin(i * 127.1f) * 43758.5453f;
        return x - (float) Math.floor(x);
    }

    /** Called once per frame from the surface before {@link #draw}. */
    public void prepareFrame(World world, Camera camera, float aspect) {
        lastAspect = aspect;
        cameraProjCache = camera.projectionMatrix(aspect);
        cameraViewCache = camera.viewMatrix();
        frameViewProj.setMultiply(cameraProjCache, cameraViewCache);
    }

    public void release() {
        int[] buf = {skyVbo};
        GLES20.glDeleteBuffers(1, buf, 0);
    }
}
