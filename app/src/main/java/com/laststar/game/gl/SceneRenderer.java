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
 * <p>Terrain, water, characters and effects are generated procedurally at
 * start-up. The set dressing -- forest, cliffs, the castle, the camp -- is a set
 * of imported CC0 models loaded by {@link ModelLibrary} and placed by
 * {@link PropPlanner}. They are the only assets in the APK besides the fonts.</p>
 */
public final class SceneRenderer {

    /** Time-of-day driven palette, blended over the run. */
    public static final class Sky {
        public float horizonR = 0.96f, horizonG = 0.86f, horizonB = 0.72f;
        public float zenithR = 0.20f, zenithG = 0.44f, zenithB = 0.78f;
        public float sunR = 1.00f, sunG = 0.95f, sunB = 0.76f;
        public float ambientR = 0.46f, ambientG = 0.52f, ambientB = 0.68f;
        public float fogDensity = 0.0007f;
    }

    private final Sky sky = new Sky();
    private float sunYaw = 42f;
    private float sunPitch = 34f;

    private GlProgram sceneProgram;
    private GlProgram skyProgram;

    private Mesh terrainMesh;
    private Mesh waterMesh;
    /** Ground fan beyond the playable map, under the mountain ring. */
    private Mesh skirtMesh;
    /** Index of the spell-book model in the set, or -1 when it is absent. */
    private int wanderModelIndex = -1;
    private Mesh sphereMesh;
    private Mesh shardMesh;
    private Mesh particleMesh;
    private Mesh ringMesh;
    private Mesh cubeMesh;
    /**
     * The distant range. Built as a full ring, centred on the camera every
     * frame, so the horizon is never empty no matter which way you look.
     */
    private Mesh mountainMesh;

    /** Imported props (trees, rocks, the castle...) and where they stand. */
    private ModelLibrary models;
    private PropPlanner props;
    private Mesh[] propMeshes = new Mesh[0];

    private Mesh[] playerParts;
    private Mesh[] wardenParts;
    private Mesh[] pickups;

    private final Mat4[] poseMatrices = new Mat4[HumanoidRig.PART_COUNT];
    private final Mat4 modelMatrix = new Mat4();
    private final Mat4 mvp = new Mat4();
    private final Mat4 tmp = new Mat4();
    private final Mat4 propScratch = new Mat4();
    private HumanoidRig playerRig;
    private HumanoidRig wardenRig;

    private int skyVbo;
    private int skyVertexCount;

    private float time;

    /** Non-null when start-up degraded; surfaced on screen by the surface. */
    public String lastError;

    public void init(World world, android.content.res.AssetManager assets) {
        sceneProgram = GlProgram.create(Shaders.SCENE_VERT, Shaders.SCENE_FRAG);
        skyProgram = GlProgram.create(Shaders.SKY_VERT, Shaders.SKY_FRAG);

        terrainMesh = toMesh(Geometry.terrain(world.terrain));
        waterMesh = toMesh(Geometry.waterPlane(world.terrain.size * 1.4f, 44, world.terrain.waterLevel));
        // Starts inside the terrain so there is no crack at the seam, and runs
        // out past the near mountain ring so its feet land on ground.
        skirtMesh = toMesh(Geometry.distantGround(
                world.terrain.size * 0.48f, 620f, 64,
                world.terrain.heightAt(world.terrain.size * 0.48f, 0f) - 0.6f,
                26f, 0.30f, 0.40f, 0.32f));


        sphereMesh = toMesh(Geometry.sphere(1f, 10, 14, 1f, 1f, 1f, 1f));
        shardMesh = toMesh(Geometry.starShard(0.42f, 1f, 0.92f, 0.55f));
        particleMesh = toMesh(Geometry.quad(1f, 1f, 1f, 1f, 1f));
        ringMesh = toMesh(Geometry.groundRing(1f, 0.12f, 28, 1f, 1f, 1f, 1f));
        cubeMesh = toMesh(Geometry.box(1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f));

        mountainMesh = toMesh(Geometry.layeredRange(520f, 96f, 56, world.worldSeed ^ 0x5A5A5A5AL, 230f));

        loadModels(world, assets);

        // The characters are important but not worth failing start-up over: if
        // the rig cannot be built the world still renders without actors.
        try {
            playerRig = HumanoidRig.player();
            wardenRig = HumanoidRig.warden();
            playerParts = rigMeshes(playerRig);
            wardenParts = rigMeshes(wardenRig);
        } catch (RuntimeException e) {
            android.util.Log.e("LastStar", "character rig unavailable", e);
            lastError = "rig: " + e.getMessage();
            playerParts = new Mesh[0];
            wardenParts = new Mesh[0];
        }
        pickups = new Mesh[]{
                toMesh(Geometry.octahedron(0.28f, 0.35f, 0.95f, 0.62f)),
                toMesh(Geometry.octahedron(0.26f, 0.98f, 0.78f, 0.30f))
        };

        for (int i = 0; i < poseMatrices.length; i++) {
            poseMatrices[i] = new Mat4();
        }

        buildSkyQuad();
    }

    /**
     * Loads and places the imported props. A missing or corrupt asset must not
     * take the game down, so a failure leaves {@link #models} null and the
     * world simply renders without set dressing.
     */
    private void loadModels(World world, android.content.res.AssetManager assets) {
        try {
            // Assets live in the APK, not on the Java classpath, so they are
            // opened through the AssetManager rather than a class loader.
            models = ModelLibrary.load(assets.open("models/world.bin"));
            // The Wander is drawn separately from the prop grid: it is one
            // object that floats and glows, not a scattered instance.
            for (int i = 0; i < models.size(); i++) {
                if (models.get(i).name.equals("pk_spell-book")) {
                    wanderModelIndex = i;
                    break;
                }
            }
            props = new PropPlanner(models, world.terrain, 20261105L);
            propMeshes = new Mesh[models.size()];
            for (int i = 0; i < models.size(); i++) {
                propMeshes[i] = models.get(i).mesh;
            }
        } catch (Exception e) {
            android.util.Log.e("LastStar", "model set unavailable: " + e.getMessage());
            models = null;
            props = null;
        }
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

        drawSkirt(camera);
        drawMountains(camera);
        drawProps(world, camera);
        drawShards(world);
        drawWander(world);
        drawPickups(world);
        drawEnemies(world);
        drawPlayer(world);

        // Water last among opaque geometry, additively blended on top.
        drawWater(world, camera);

        drawParticles(world, camera);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    /**
     * Draws the ground beyond the map.
     *
     * <p>Like the range it follows the camera in X/Z, except it is pinned to
     * the map centre: it has to stay welded to the terrain edge, and the
     * terrain does not move. It is a single flat fan, so the cost is one draw.</p>
     */
    private void drawSkirt(Camera camera) {
        modelMatrix.identity();
        tmp.setMultiply(frameViewProj, modelMatrix);
        sceneProgram.setMvp(tmp.m);
        sceneProgram.setModel(modelMatrix.m);
        sceneProgram.setTintAmount(0f);
        sceneProgram.setEmissive(0f);
        sceneProgram.setFloat("uWaveAmp", 0f);
        drawMesh(skirtMesh);
    }

    /**
     * Draws the distant range as a ring centred on the camera.
     *
     * <p>It is far outside the playable area, so rather than modelling where it
     * really is, it is translated to follow the player in X/Z and sits at a fixed
     * height. At 430 m out with the fog the game already applies, that is
     * indistinguishable from real parallax, and it means one mesh covers the
     * entire horizon from any position on the map.</p>
     */
    private void drawMountains(Camera camera) {
        modelMatrix.identity();
        modelMatrix.m[12] = camera.position.x;
        modelMatrix.m[13] = 0f;
        modelMatrix.m[14] = camera.position.z;
        tmp.setMultiply(frameViewProj, modelMatrix);
        sceneProgram.setMvp(tmp.m);
        sceneProgram.setModel(modelMatrix.m);
        // The range carries its own haze in the vertex colours, so it only needs
        // a touch of extra fog and no tint.
        sceneProgram.setTintAmount(0f);
        sceneProgram.setEmissive(0.06f);
        drawMesh(mountainMesh);
    }

    /**
     * Draws the imported set dressing.
     *
     * <p>Landmarks are always drawn; everything else is culled by a distance
     * test against the camera, which keeps the draw count in the low thousands
     * while the map is dressed with more props than that. Each model is a single
     * indexed draw, so the cull is what keeps vertex-shader work proportional to
     * what is actually on screen.</p>
     */
    private void drawProps(World world, Camera camera) {
        if (props == null) {
            return;
        }
        sceneProgram.setTintAmount(0f);
        sceneProgram.setTime(time);
        sceneProgram.setFloat("uWaveAmp", 0f);

        float camX = camera.position.x;
        float camZ = camera.position.z;

        // Landmarks first: towers and walls are the silhouettes you navigate by,
        // so they get the longest draw distance.
        // Landmarks keep their own distance because they are not all the same
        // kind of thing: a tent is 300 m of interesting, a mountain ring at
        // 430 m is culled by that and pops out of the horizon as you walk.
        for (int i = 0; i < props.landmarks.size(); i++) {
            PropPlanner.Instance in = props.landmarks.get(i);
            drawProp(in, Math.max(300f, drawDistance(in.model)), camX, camZ);
        }

        PropPlanner.Grid grid = props.grid;
        for (int cz = 0; cz < grid.rows(); cz++) {
            for (int cx = 0; cx < grid.cols(); cx++) {
                java.util.List<PropPlanner.Instance> cell = grid.cell(cx, cz);
                for (int i = 0; i < cell.size(); i++) {
                    PropPlanner.Instance in = cell.get(i);
                    drawProp(in, drawDistance(in.model), camX, camZ);
                }
            }
        }
        sceneProgram.setTintAmount(0f);
        sceneProgram.setFloat("uWaveAmp", 0f);
    }

    /**
     * Wind amplitude per model, in metres. Written to {@code uWaveAmp}, which
     * bends the mesh tip in the vertex shader; anything without grass or leaves
     * gets zero so rocks and buildings stay rigid.
     */
    private float swayFor(int modelIndex) {
        // A fraction of the model's own height. The imported pack is authored
        // at wildly different scales (a 2.5 m grass patch, an 8.5 m tree), so a
        // fixed metre figure either slides the wide props across the ground or
        // is invisible on the tall ones.
        ModelLibrary.Model m = models.get(modelIndex);
        float h = m.height();
        switch (m.role) {
            case ModelLibrary.GRASS:
                return h * 0.10f;
            case ModelLibrary.FLOWER:
                return h * 0.09f;
            case ModelLibrary.PLANT:
                return h * 0.045f;
            case ModelLibrary.MUSHROOM:
                return h * 0.03f;
            case ModelLibrary.TREE:
                return h * 0.010f;
            default:
                return 0f;
        }
    }

    /** Per-role draw distance in metres, relative to the camera. */
    private float drawDistance(int modelIndex) {
        switch (models.get(modelIndex).role) {
            case ModelLibrary.GRASS:
                // Past about fourteen metres a grass clump is a couple of
                // pixels, and the terrain's own meadow colour is already under
                // it. Culling here is what keeps the draw count in the low
                // thousands while the ground is still carpeted where it counts.
                return 14f;
            case ModelLibrary.FLOWER:
                return 20f;
            case ModelLibrary.MUSHROOM:
                return 30f;
            case ModelLibrary.PLANT:
                return 55f;
            case ModelLibrary.ROCK:
                return 90f;
            case ModelLibrary.TREE:
            case ModelLibrary.LOG:
            case ModelLibrary.STUMP:
                return 135f;
            case ModelLibrary.HILL:
                // The mountain rings sit out past 400 m; the far plane is at
                // 1400 m, so this is what keeps the whole horizon drawn.
                return 900f;
            case ModelLibrary.CLIFF:
                return 200f;
            default:
                return 160f;
        }
    }

    private void drawProp(PropPlanner.Instance in, float distance, float camX, float camZ) {
        float dx = in.x - camX;
        float dz = in.z - camZ;
        if (dx * dx + dz * dz > distance * distance) {
            return;
        }
        modelMatrix.setMultiply(
                Mat4.translation(in.x, in.y, in.z),
                Mat4.rotationY(in.yaw));
        if (in.scale != 1f) {
            propScratch.setMultiply(modelMatrix, Mat4.scale(in.scale, in.scale, in.scale));
            modelMatrix.copyFrom(propScratch);
        }
        tmp.setMultiply(frameViewProj, modelMatrix);
        sceneProgram.setMvp(tmp.m);
        sceneProgram.setModel(modelMatrix.m);
        if (in.tintAmount > 0f) {
            sceneProgram.setTint(in.tintR, in.tintG, in.tintB, in.tintAmount);
        } else {
            sceneProgram.setTintAmount(0f);
        }
        // Ground cover sways; timber and stone do not. This is the whole
        // difference between a painted meadow and a flat green carpet.
        sceneProgram.setFloat("uWaveAmp", swayFor(in.model));
        drawMesh(propMeshes[in.model]);
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

    /**
     * Draws the Wander where it lies, before it is picked up.
     *
     * <p>It floats, turns and carries its own light: the object is the only
     * route to the spell system, so it has to be findable from a distance
     * without a marker pinned on it. The bob is offset from the shards' phase
     * so the two kinds of pickup never pulse in step.</p>
     */
    private void drawWander(World world) {
        if (wanderModelIndex < 0) {
            return;
        }
        // Every lost page in the region, drawn as a smaller floating book with
        // the same halo, so a player can spot one across a meadow.
        for (int i = 0; i < world.lostPages.size(); i++) {
            drawFoundPage(world, world.lostPages.get(i));
        }
    }

    /** One lost page, bobbing and turning. */
    private void drawFoundPage(World world, World.FoundPage page) {
        Vec3 p = page.position;
        float bob = (float) Math.sin(world.worldTime * 1.6f + page.spin * 0.01f) * 0.13f;
        float y = p.y + 0.55f + bob;

        // Halo first, additively, so it reads as a glow rather than a disc.
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE);
        GLES20.glDepthMask(false);
        modelMatrix.setMultiply(Mat4.translation(p.x, y, p.z),
                Mat4.scale(1.5f, 1.5f, 1.5f));
        tmp.setMultiply(frameViewProj, modelMatrix);
        sceneProgram.setMvp(tmp.m);
        sceneProgram.setModel(modelMatrix.m);
        sceneProgram.setTint(0.55f, 0.62f, 1f, 0.40f);
        sceneProgram.setTintAmount(1f);
        sceneProgram.setEmissive(0.5f);
        drawMesh(ringMesh);
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);

        modelMatrix.setMultiply(
                Mat4.translation(p.x, y, p.z),
                Mat4.rotationY(page.spin));
        tmp.setMultiply(frameViewProj, modelMatrix);
        sceneProgram.setMvp(tmp.m);
        sceneProgram.setModel(modelMatrix.m);
        sceneProgram.setTint(0.62f, 0.55f, 1f, 1f);
        sceneProgram.setTintAmount(0.55f);
        sceneProgram.setEmissive(0.42f);
        sceneProgram.setFloat("uWaveAmp", 0f);
        sceneProgram.setTime(world.worldTime);
        drawMesh(propMeshes[wanderModelIndex]);
        sceneProgram.setEmissive(0f);
        sceneProgram.setTintAmount(0f);
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
        drawRig(playerParts, 1f, flashBlend, p.skinColour(), 0.85f);
    }

    /** Camera-projection cache refreshed once per frame for the prop passes. */
    private Mat4 cameraProjCache = new Mat4();
    private Mat4 cameraViewCache = new Mat4();
    /** Projection * view for the current frame. */
    private Mat4 frameViewProj = new Mat4();
    private Mat4 rigScratch = new Mat4();
    /** Index of the head part in {@link #rigMeshes} (torso, hips, head...). */
    private static final int HEAD_PART = 2;

    private void modelAt(Vec3 pos, float yawDeg) {
        modelMatrix.setMultiply(Mat4.translation(pos.x, pos.y, pos.z), Mat4.rotationY(yawDeg));
    }

    private void drawRig(Mesh[] parts, float alpha, float hitBlend) {
        drawRig(parts, alpha, hitBlend, null, 0f);
    }

    /**
     * Draws a rig, optionally blending one part (the head) towards the skin
     * colour the player picked in the wardrobe.
     */
    private void drawRig(Mesh[] parts, float alpha, float hitBlend,
                         float[] skin, float skinBlend) {
        sceneProgram.setAlpha(alpha);
        sceneProgram.setTintAmount(1f);
        for (int i = 0; i < parts.length; i++) {
            Mat4 part = poseMatrices[i];
            rigScratch.setMultiply(frameViewProj, part);
            sceneProgram.setMvp(rigScratch.m);
            sceneProgram.setModel(part.m);
            if (hitBlend > 0f) {
                sceneProgram.setTint(1f, 0.35f, 0.30f, hitBlend);
            } else if (skin != null && skinBlend > 0f && i == HEAD_PART) {
                sceneProgram.setTint(skin[0], skin[1], skin[2], skinBlend);
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
        // Lake water: teal in the shallows, deep blue away from the ground.
        sceneProgram.setTint(0.06f, 0.44f, 0.66f, 0.80f);
        sceneProgram.setTintAmount(0.62f);
        sceneProgram.setEmissive(0.10f);
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

    /**
     * Time-of-day palette.
     *
     * <p>Daylight is golden hour: a warm cream horizon under a saturated
     * cerulean zenith, with a pale-gold sun. Night is moonlit blue, dark
     * enough that emissive effects (shards, runes, the sword arc) read as
     * light sources rather than as slightly-brighter paint.</p>
     *
     * <p>Kept in step with {@code ScreenshotTool.drawWorld} so the repository
     * screenshots show what the APK actually renders.</p>
     */
    private void updateSky(World world, Camera camera, float night) {
        sky.horizonR = Noise.lerp(0.96f, 0.11f, night);
        sky.horizonG = Noise.lerp(0.86f, 0.12f, night);
        sky.horizonB = Noise.lerp(0.72f, 0.26f, night);
        sky.zenithR = Noise.lerp(0.20f, 0.03f, night);
        sky.zenithG = Noise.lerp(0.44f, 0.06f, night);
        sky.zenithB = Noise.lerp(0.78f, 0.16f, night);
        sky.sunR = Noise.lerp(1.00f, 0.44f, night);
        sky.sunG = Noise.lerp(0.95f, 0.52f, night);
        sky.sunB = Noise.lerp(0.76f, 0.86f, night);
        sky.ambientR = Noise.lerp(0.46f, 0.09f, night);
        sky.ambientG = Noise.lerp(0.52f, 0.12f, night);
        sky.ambientB = Noise.lerp(0.68f, 0.24f, night);
        sky.fogDensity = Noise.lerp(0.0007f, 0.0016f, night);
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
