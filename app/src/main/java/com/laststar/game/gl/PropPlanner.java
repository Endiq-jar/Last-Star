package com.laststar.game.gl;

import com.laststar.game.core.Terrain;
import com.laststar.game.world.VillageLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decides where the imported props stand.
 *
 * <p>This is the set-dressing pass the world was missing: instead of a ring of
 * procedural monoliths, the terrain is dressed in layers -- ground cover, then
 * plants, then rocks and fallen timber, then the forest itself -- around four
 * hand-placed landmarks (a castle gate, a walled courtyard, a traveller's camp
 * and a watchtower) that give the map silhouettes to navigate by.</p>
 *
 * <p>Everything is deterministic from the world seed and free of Android types,
 * so the headless tests can assert on the plan and the off-screen screenshot
 * renderer draws exactly what the phone draws.</p>
 */
public final class PropPlanner {

    /** One instance of one imported model. */
    public static final class Instance {
        public final int model;
        public final float x, y, z;
        public final float yaw;
        public final float scale;
        /** Tint blend: 0 keeps the baked colour, 1 replaces it with the tint. */
        public final float tintR, tintG, tintB, tintAmount;

        public Instance(int model, float x, float y, float z, float yaw, float scale,
                        float tintR, float tintG, float tintB, float tintAmount) {
            this.model = model;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.scale = scale;
            this.tintR = tintR;
            this.tintG = tintG;
            this.tintB = tintB;
            this.tintAmount = tintAmount;
        }

        /** True when this instance sits within {@code radius} of (px, pz). */
        public boolean near(float px, float pz, float radius) {
            float dx = x - px;
            float dz = z - pz;
            return dx * dx + dz * dz < radius * radius;
        }
    }

    /** Uniform-grid buckets so the renderer can cull by distance cheaply. */
    public static final class Grid {
        public final float cellSize;
        public final float originX, originZ;
        private final List<Instance>[] cells;
        private final int cols, rows;

        @SuppressWarnings("unchecked")
        Grid(float cellSize, float originX, float originZ, int cols, int rows) {
            this.cellSize = cellSize;
            this.originX = originX;
            this.originZ = originZ;
            this.cols = cols;
            this.rows = rows;
            // Flat array of buckets, indexed cz * cols + cx.
            this.cells = (List<Instance>[]) new List[cols * rows];
        }

        public int cols() {
            return cols;
        }

        public int rows() {
            return rows;
        }

        public int at(int cx, int cz) {
            List<Instance> l = cells[cz * cols + cx];
            return l == null ? 0 : l.size();
        }

        /** Buckets are never null: an empty cell returns an empty list. */
        public List<Instance> cell(int cx, int cz) {
            List<Instance> l = cells[cz * cols + cx];
            return l == null ? EMPTY : l;
        }

        void add(Instance in) {
            int cx = (int) ((in.x - originX) / cellSize);
            int cz = (int) ((in.z - originZ) / cellSize);
            if (cx < 0 || cz < 0 || cx >= cols || cz >= rows) {
                return;
            }
            int i = cz * cols + cx;
            if (cells[i] == null) {
                cells[i] = new ArrayList<Instance>();
            }
            cells[i].add(in);
        }

        /** Radius test against a cell, used to keep props from overlapping. */
        boolean occupied(int cx, int cz, float x, float z, float radius) {
            if (cx < 0 || cz < 0 || cx >= cols || cz >= rows) {
                return false;
            }
            List<Instance> l = cells[cz * cols + cx];
            if (l == null) {
                return false;
            }
            float r2 = radius * radius;
            for (int i = 0; i < l.size(); i++) {
                Instance o = l.get(i);
                float dx = o.x - x;
                float dz = o.z - z;
                if (dx * dx + dz * dz < r2) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final List<Instance> EMPTY = new ArrayList<Instance>(0);

    /** Non-landmark props, bucketed for culling. */
    public final Grid grid;
    /** Landmarks are few and large; they are always drawn. */
    public final List<Instance> landmarks = new ArrayList<Instance>();

    private final Terrain terrain;

    public PropPlanner(ModelLibrary models, Terrain terrain, long seed) {
        this.terrain = terrain;
        this.library = models;
        int cols = 16;
        grid = new Grid(terrain.size / cols, -terrain.size * 0.5f, -terrain.size * 0.5f,
                cols, cols);

        // The Depths are not another outdoor biome: no skyline peaks, trees or
        // flower cards. Their ceiling is built separately; these cave props
        // give the traversable floor sparse rock, fungus and broken columns.
        if (terrain.region != null && terrain.region.index == 14) {
            scatter(models, terrain, seed + 7, ModelLibrary.ROCK, 300, 4.2f, 8f, true);
            scatter(models, terrain, seed + 13, ModelLibrary.MUSHROOM, 240, 3.0f, 7f, true);
            scatter(models, terrain, seed + 19, ModelLibrary.PLANT, 150, 3.8f, 9f, true);
            scatter(models, terrain, seed + 29, ModelLibrary.CLIFF, 70, 12f, 22f, false);
            return;
        }

        // Order is load-bearing, not cosmetic: the grid doubles as the overlap
        // test, so whatever goes in first wins its ground. Landmarks and the
        // big silhouettes are placed before the ground cover, and the ground
        // cover is ordered largest-first, otherwise a bush scattered after
        // seventeen thousand grass clumps finds every candidate blocked and
        // quietly never appears. Attempt counts are high for the same reason:
        // water, slope, spawn clearance and overlaps reject most candidates.
        buildLandmarks(models, terrain, seed);
        buildRidges(models, terrain, seed);
        buildPeaks(models, terrain, seed);
        buildPackMountains(models, terrain, seed);
        buildPackBridges(models, terrain, seed);

        scatter(models, terrain, seed, ModelLibrary.TREE, 380, 6.5f, 15f, true);
        scatter(models, terrain, seed + 11, ModelLibrary.ROCK, 210, 3.0f, 7f, true);
        scatter(models, terrain, seed + 23, ModelLibrary.PLANT, 760, 2.0f, 8f, true);
        scatter(models, terrain, seed + 53, ModelLibrary.MUSHROOM, 260, 1.6f, 6f, true);
        scatter(models, terrain, seed + 67, ModelLibrary.LOG, 110, 4.0f, 10f, true);
        scatter(models, terrain, seed + 71, ModelLibrary.STUMP, 55, 3.2f, 8f, true);
        scatter(models, terrain, seed + 83, ModelLibrary.CLIFF, 150, 7.0f, 22f, false);
        scatter(models, terrain, seed + 97, ModelLibrary.HILL, 24, 15f, 36f, false);

        // The imported pack, woven in with the procedural kit.
        buildPackTrees(models, terrain, seed);
        buildPackGroundCover(models, terrain, seed);

        // The built-in grass and flower cards go on last of all. They are
        // small -- 0.16 to 0.48 m -- and being single opaque quads they read as
        // coloured cards at close range, so they are kept to a supporting role
        // and the imported geometry leads. Dropping them entirely was worse:
        // the meadow lost its small-scale detail and the ground went back to
        // reading as bare.
        scatter(models, terrain, seed + 37, ModelLibrary.GRASS, 3000, 0.70f, 3f, true);
        scatter(models, terrain, seed + 41, ModelLibrary.FLOWER, 2600, 0.85f, 4f, true);
    }


    // ------------------------------------------------------ imported pack

    /** Finds the model index for a name, or -1 when the set has no such model. */
    private static int indexOf(ModelLibrary models, String name) {
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Raises the imported mountain across the whole horizon.
     *
     * <p>The procedural range behind this is a backdrop that follows the
     * camera, which reads correctly at the skyline but cannot be walked
     * towards. These are real meshes standing on the real terrain, in two
     * rings, so the mountains have parallax, catch the light and get properly
     * hazed by distance as the player crosses the map.</p>
     */
    private void buildPackMountains(ModelLibrary models, Terrain terrain, long seed) {
        int index = indexOf(models, "pk_mountain");
        if (index < 0) {
            return;
        }
        ModelLibrary.Model m = models.get(index);
        // The model is 483 m wide for every 260 m of height: about 1.86:1. So
        // height and footprint are the same decision. Sizing by height alone
        // put the foot of a 260 m peak 24 m from the centre of the map, which
        // drapes a mountainside over the whole playable area. Each ring is
        // sized so the visible foot lands beyond the map edge and the peak
        // still fills a good part of the sky.
        Random r = new Random(seed + 9091);
        // {centre distance, spread, peaks, min height, max height}
        float[][] rings = {
                {300f, 40f, 12, 112f, 152f},
                {430f, 60f, 10, 190f, 250f}
        };
        for (int ringIndex = 0; ringIndex < rings.length; ringIndex++) {
            float[] ring = rings[ringIndex];
            float centre = ring[0], spread = ring[1];
            int count = (int) ring[2];
            float minH = ring[3], maxH = ring[4];
            for (int k = 0; k < count; k++) {
                // Offset each ring half a step so the second ring's peaks fall
                // in the gaps of the first instead of directly behind them.
                float ang = (float) ((k + ringIndex * 0.5f) * Math.PI * 2.0 / count)
                        + (r.nextFloat() - 0.5f) * 0.26f;
                float dist = centre + (r.nextFloat() - 0.5f) * spread;
                float x = (float) Math.cos(ang) * dist;
                float z = (float) Math.sin(ang) * dist;
                float metres = minH + r.nextFloat() * (maxH - minH);
                // Bed the base into the ground skirt. The foot is hidden by the
                // skirt's own slope, so the seam where the model ends is never
                // on screen.
                float base = -(metres * 0.10f) - 6f;
                landmarks.add(new Instance(index, x, base, z,
                        r.nextFloat() * 360f, m.scaleTo(metres),
                        // Snow and haze creeping down from the summit.
                        0.88f + ringIndex * 0.04f, 0.92f + ringIndex * 0.03f,
                        1.05f, 0.28f + ringIndex * 0.18f + r.nextFloat() * 0.14f));
            }
        }
    }

    /**
     * Spans bridges across the water wherever a dry shore exists on both sides.
     *
     * <p>The world is a lake basin, so a crossing needs the water to be
     * genuinely interrupted by land rather than just shallow: the samples at
     * each end are required to be well clear of the water line, or the bridge
     * ends in mid-air over the shallows.</p>
     */
    private void buildPackBridges(ModelLibrary models, Terrain terrain, long seed) {
        int index = indexOf(models, "pk_bridge");
        if (index < 0) {
            return;
        }
        ModelLibrary.Model m = models.get(index);
        // Which axis of the bridge is its length is not obvious from its
        // bounding box, so the span is driven by the water it has to cross
        // rather than by the model's height.
        float halfLength = 7f;

        // The terrain holds one lake, 27 m to 66 m across depending on where
        // you cut it. A fixed-size bridge cannot span that, so crossings are
        // found by cutting the lake with many lines and keeping the narrowest
        // wet run each line passes through.
        Random r = new Random(seed + 7717);
        int placed = 0;
        float[][] found = new float[12][];
        for (int attempt = 0; attempt < 1400 && placed < 4; attempt++) {
            float px = -60f + r.nextFloat() * 80f;
            float pz = -40f + r.nextFloat() * 80f;
            float ang = (float) (r.nextDouble() * Math.PI);
            float dx = (float) Math.cos(ang);
            float dz = (float) Math.sin(ang);

            // Walk the line and track the longest contiguous wet stretch.
            float bestLen = 0f, bestMid = 0f;
            float runStart = 0f, runLen = 0f;
            boolean inWater = false;
            for (float d = -55f; d <= 55f; d += 0.8f) {
                float h = terrain.heightAt(px + dx * d, pz + dz * d);
                if (h < terrain.waterLevel) {
                    if (!inWater) {
                        inWater = true;
                        runStart = d;
                        runLen = 0f;
                    }
                    runLen = d - runStart;
                } else if (inWater) {
                    inWater = false;
                    if (runLen > bestLen) {
                        bestLen = runLen;
                        bestMid = runStart + runLen * 0.5f;
                    }
                }
            }
            // Too short is a puddle; too long cannot be spanned by any fair
            // stretch of the model. The window also has to end on dry ground
            // at both ends, or the bridge ends in the shallows.
            if (bestLen < 9f || bestLen > 34f) {
                continue;
            }
            float cx = px + dx * bestMid;
            float cz = pz + dz * bestMid;
            boolean dryEnds = true;
            for (int side = -1; side <= 1 && dryEnds; side += 2) {
                float ex = cx + dx * (bestLen * 0.5f + 3.2f) * side;
                float ez = cz + dz * (bestLen * 0.5f + 3.2f) * side;
                if (terrain.heightAt(ex, ez) < terrain.waterLevel + 0.30f
                        || terrain.slopeAt(ex, ez) > 0.55f) {
                    dryEnds = false;
                }
            }
            if (!dryEnds || blocked(cx, cz, 12f)) {
                continue;
            }
            // Reject a crossing that lands on top of one already placed.
            boolean clash = false;
            for (int i = 0; i < placed; i++) {
                float ddx = found[i][0] - cx;
                float ddz = found[i][1] - cz;
                if (ddx * ddx + ddz * ddz < 26f * 26f) {
                    clash = true;
                }
            }
            if (clash) {
                continue;
            }
            found[placed] = new float[]{cx, cz};
            placed++;

            // Span the water plus two metres of footing on each bank, and let
            // the deck rise gently out of the water rather than being scaled
            // from the model's height.
            float span = bestLen + 4.5f;
            float scale = span / (halfLength * 2f);
            // Keep the arch from turning into a cathedral: past about 2.2x the
            // rise reads as a wall rather than a bridge.
            scale = Math.min(scale, 2.2f);
            float deck = terrain.waterLevel + 0.10f;
            // The model's deck runs along its local Z. rotationY sends local +Z
            // to (sin yaw, cos yaw), so the yaw that aligns the deck with this
            // crossing is atan2(dx, dz), i.e. 90 degrees minus the line angle.
            // Getting this wrong leaves the bridge side-on to the water it is
            // supposed to cross.
            float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
            landmarks.add(new Instance(index, cx, deck, cz, yaw, scale,
                    0f, 0f, 0f, 0f));
            blockedPad.add(new float[]{cx, cz, Math.max(10f, span * 0.6f)});
        }
    }

    /** Shore approaches kept clear for the bridge ramps. */
    private final List<float[]> blockedPad = new ArrayList<float[]>();

    /**
     * Scatters the imported trees, giving the map a denser canopy than the
     * built-in set alone. These are the tallest walkable objects, so they are
     * what makes the world feel old-growth rather than a golf course.
     */
    private void buildPackTrees(ModelLibrary models, Terrain terrain, long seed) {
        int index = indexOf(models, "pk_tree");
        if (index < 0) {
            return;
        }
        ModelLibrary.Model m = models.get(index);
        Random r = new Random(seed + 3323);
        float half = terrain.size * 0.47f;
        int placed = 0;
        // Attempts are generous because the overlap test rejects anything near
        // an existing tree, and 580 trees is what makes the basin read as
        // woodland rather than a lawn with features on it.
        for (int attempt = 0; attempt < 26000 && placed < 580; attempt++) {
            float x = (r.nextFloat() * 2f - 1f) * half;
            float z = (r.nextFloat() * 2f - 1f) * half;
            float h = terrain.heightAt(x, z);
            if (h < terrain.waterLevel + 0.8f || x * x + z * z < 64f) {
                continue;
            }
            if (terrain.slopeAt(x, z) > 0.46f || blocked(x, z, 4.6f)) {
                continue;
            }
            float metres = m.targetSize * (0.72f + r.nextFloat() * 0.66f);
            grid.add(new Instance(index, x, h - 0.10f, z, r.nextFloat() * 360f,
                    m.scaleTo(metres), 0f, 0f, 0f, 0f));
            placed++;
        }
    }

    /**
     * Blanks the ground with the imported grass, flowers and small stones.
     *
     * <p>The built-in grass is a clump card that reads as a wash of green from
     * a distance. The imported clumps are denser and more varied, and mixing
     * the two is what stops the meadow looking like one texture repeated. This
     * is the call that satisfies "grass everywhere": the gap is small enough
     * that stepping anywhere on dry land puts the player in grass.</p>
     */
    private void buildPackGroundCover(ModelLibrary models, Terrain terrain, long seed) {
        // Root the shore reeds before the meadow beds; otherwise dense blooms
        // can claim every shallow-water spot and erase the asset from the scene.
        scatterReeds(models, terrain, seed + 5519, "pk_flower", 50000);
        // Largest first. The grid is the overlap test, so a bush scattered
        // after 16k grass clumps finds almost every candidate blocked and
        // silently disappears; scattering the tall things first and letting
        // grass fill in around them is what keeps every asset present.
        scatterPack(models, terrain, seed + 5537, "pk_rock2", 5000, 3.4f, 8f);
        scatterPack(models, terrain, seed + 5521, "pk_bush", 5200, 2.4f, 6f);
        scatterPack(models, terrain, seed + 5533, "pk_rock", 2200, 3.0f, 7f);
        scatterPack(models, terrain, seed + 5507, "pk_flowers", 22000, 0.48f, 3.2f);
        // A second pass tinted towards the pinks and whites of a wildflower
        // meadow, so the flowers are not one uniform harvest yellow.
        scatterPackTinted(models, terrain, seed + 5509, "pk_flowers", 9000,
                0.48f, 3.2f, 0.96f, 0.70f, 0.92f, 0.55f);
        // Flower01 is an extruded twelve-sided prism: a solid tapered drum.
        // In the meadow that reads as a coloured sheet lying on the grass, but
        // standing in the shallows it is exactly the shape of a reed clump. It
        // goes only where the water line is, which is both where it looks right
        // and a place nothing else was dressing.
        scatterPack(models, terrain, seed + 5531, "pk_pebbles", 3200, 0.80f, 2.2f);
        // pk_spell-book is deliberately absent: the Wander is a single lore
        // object placed by World, not a shrub to be scattered 88 times.
        // Grass last and by far the densest. The imported clump is a wide, low
        // ground patch: used sparsely each one reads as a green plate lying on
        // the ground. What makes it grass is overlap -- small patches packed
        // until they merge into a carpet, with the terrain's own meadow colour
        // showing through the gaps.
        scatterPack(models, terrain, seed + 5501, "pk_grass", 30000, 0.20f, 1.5f);
    }

    /**
     * Places tapered stems along the water's edge.
     *
     * <p>Reeds want the band where the ground is just under the water line:
     * deep enough to stand in, shallow enough to still be rooted. Nothing else
     * is placed there, so this both dresses a bare strip and keeps every asset
     * in the pack in the world.</p>
     */
    private void scatterReeds(ModelLibrary models, Terrain terrain, long seed,
                              String name, int attempts) {
        int index = indexOf(models, name);
        if (index < 0) {
            return;
        }
        ModelLibrary.Model m = models.get(index);
        Random r = new Random(seed);
        float half = terrain.size * 0.47f;
        for (int i = 0; i < attempts; i++) {
            float x = (r.nextFloat() * 2f - 1f) * half;
            float z = (r.nextFloat() * 2f - 1f) * half;
            float h = terrain.heightAt(x, z);
            // The shallow band only.
            if (h > terrain.waterLevel + 0.22f || h < terrain.waterLevel - 1.10f) {
                continue;
            }
            if (blocked(x, z, 0.42f) || onBridgeApproach(x, z)) {
                continue;
            }
            float scale = m.scaleTo(m.targetSize * (0.72f + r.nextFloat() * 0.55f));
            grid.add(new Instance(index, x, h - 0.04f, z, r.nextFloat() * 360f,
                    scale, 0f, 0f, 0f, 0f));
        }
    }

    /**
     * As {@link #scatterPack}, with a fixed tint.
     *
     * <p>Used to give one model two colourways: the flowers look like a meadow
     * of several species rather than one, from a single asset.</p>
     */
    private void scatterPackTinted(ModelLibrary models, Terrain terrain, long seed,
                                   String name, int attempts, float minGap,
                                   float minFromSpawn, float tr, float tg, float tb,
                                   float amount) {
        int index = indexOf(models, name);
        if (index < 0) {
            return;
        }
        ModelLibrary.Model m = models.get(index);
        Random r = new Random(seed);
        float half = terrain.size * 0.47f;
        for (int i = 0; i < attempts; i++) {
            float x = (r.nextFloat() * 2f - 1f) * half;
            float z = (r.nextFloat() * 2f - 1f) * half;
            if ("pk_flowers".equals(name)) {
                // The reference meadow has broad color drifts, not an even
                // sprinkling. Eight beds follow the playable slopes around the
                // clear spawn/camp, with soft edges from summed random offsets.
                int bed = i % 8;
                float angle = (float) (bed * Math.PI * 0.25) + 0.23f;
                float radius = 22f + (bed % 3) * 6f;
                float jx = (r.nextFloat() + r.nextFloat() + r.nextFloat()
                        + r.nextFloat() - 2f) * 7f;
                float jz = (r.nextFloat() + r.nextFloat() + r.nextFloat()
                        + r.nextFloat() - 2f) * 7f;
                x = (float) Math.cos(angle) * radius + jx;
                z = (float) Math.sin(angle) * radius + jz;
            }
            float h = terrain.heightAt(x, z);
            if (h < terrain.waterLevel + 0.35f) {
                continue;
            }
            if (x * x + z * z < minFromSpawn * minFromSpawn) {
                continue;
            }
            if (terrain.slopeAt(x, z) > 0.52f) {
                continue;
            }
            if (blocked(x, z, minGap) || onBridgeApproach(x, z)) {
                continue;
            }
            float scale = m.scaleTo(m.targetSize * (0.78f + r.nextFloat() * 0.5f));
            grid.add(new Instance(index, x, h - 0.03f, z, r.nextFloat() * 360f,
                    scale, tr, tg, tb, amount));
        }
    }

    /** One named pack model scattered over dry open ground. */
    private void scatterPack(ModelLibrary models, Terrain terrain, long seed,
                             String name, int attempts, float minGap, float minFromSpawn) {
        int index = indexOf(models, name);
        if (index < 0) {
            return;
        }
        ModelLibrary.Model m = models.get(index);
        Random r = new Random(seed);
        float half = terrain.size * 0.47f;
        for (int i = 0; i < attempts; i++) {
            float x = (r.nextFloat() * 2f - 1f) * half;
            float z = (r.nextFloat() * 2f - 1f) * half;
            if ("pk_flowers".equals(name)) {
                // The reference meadow has broad color drifts, not an even
                // sprinkling. Eight beds follow the playable slopes around the
                // clear spawn/camp, with soft edges from summed random offsets.
                int bed = i % 8;
                float angle = (float) (bed * Math.PI * 0.25) + 0.23f;
                float radius = 22f + (bed % 3) * 6f;
                float jx = (r.nextFloat() + r.nextFloat() + r.nextFloat()
                        + r.nextFloat() - 2f) * 7f;
                float jz = (r.nextFloat() + r.nextFloat() + r.nextFloat()
                        + r.nextFloat() - 2f) * 7f;
                x = (float) Math.cos(angle) * radius + jx;
                z = (float) Math.sin(angle) * radius + jz;
            }
            float h = terrain.heightAt(x, z);
            if (h < terrain.waterLevel + 0.35f) {
                continue;
            }
            if (x * x + z * z < minFromSpawn * minFromSpawn) {
                continue;
            }
            if (terrain.slopeAt(x, z) > 0.52f) {
                continue;
            }
            if (blocked(x, z, minGap) || onBridgeApproach(x, z)) {
                continue;
            }
            float sizeFactor = "pk_grass".equals(name)
                    ? 0.24f + r.nextFloat() * 0.18f
                    : 0.78f + r.nextFloat() * 0.5f;
            float scale = m.scaleTo(m.targetSize * sizeFactor);
            // The tint multiplies the vertex colour, so it can only darken a
            // channel or leave it alone; anything above 1 washes towards white.
            // A gentle drift towards straw or towards deep green is enough to
            // stop twenty thousand identical clumps reading as one sheet.
            float shade = 0.86f + r.nextFloat() * 0.14f;
            float warm = r.nextFloat();
            float tr = warm > 0.55f ? shade * 1.06f : shade * 0.92f;
            float tg = shade;
            float tb = warm > 0.55f ? shade * 0.86f : shade * 1.00f;
            grid.add(new Instance(index, x, h - 0.03f, z, r.nextFloat() * 360f,
                    scale, tr, tg, tb, 0.18f + r.nextFloat() * 0.20f));
        }
    }

    private boolean onBridgeApproach(float x, float z) {
        for (int i = 0; i < blockedPad.size(); i++) {
            float[] p = blockedPad.get(i);
            float dx = p[0] - x;
            float dz = p[1] - z;
            if (dx * dx + dz * dz < p[2] * p[2]) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------- scattering

    private void scatter(ModelLibrary models, Terrain terrain, long seed, int role,
                         int attempts, float minGap, float minFromSpawn, boolean openGround) {
        if (models.count(role) == 0) {
            return;
        }
        float half = terrain.size * 0.46f;
        Random r = new Random(seed);
        for (int i = 0; i < attempts; i++) {
            float x = (r.nextFloat() * 2f - 1f) * half;
            float z = (r.nextFloat() * 2f - 1f) * half;
            // Meadow flowers grow in drifts rather than evenly spaced confetti.
            // Eight deterministic beds surround the starting clearing; a soft
            // Gaussian-like scatter lets each edge dissolve into plain grass.
            if (role == ModelLibrary.FLOWER) {
                int bed = i % 8;
                float angle = (float) (bed * Math.PI * 0.25) + 0.23f;
                float radius = 30f + (bed % 3) * 8f;
                float jx = (r.nextFloat() + r.nextFloat() + r.nextFloat()
                        + r.nextFloat() - 2f) * 5.2f;
                float jz = (r.nextFloat() + r.nextFloat() + r.nextFloat()
                        + r.nextFloat() - 2f) * 5.2f;
                x = (float) Math.cos(angle) * radius + jx;
                z = (float) Math.sin(angle) * radius + jz;
            }
            float h = terrain.heightAt(x, z);
            if (h < terrain.waterLevel + 0.55f) {
                continue;
            }
            if (x * x + z * z < minFromSpawn * minFromSpawn) {
                continue;
            }
            float slope = terrain.slopeAt(x, z);
            if (openGround && slope > 0.5f) {
                continue;
            }
            // Cliffs and hills want the steep ground everything else avoids.
            // The threshold is low because the terrain is gentle by design: the
            // foothills peak around slope 0.24, so anything higher would leave
            // these roles with nowhere to stand and they would silently vanish.
            if (!openGround && slope < 0.13f) {
                continue;
            }
            int index = models.pickIndex(role, i * 31 + 7);
            if (index < 0) {
                return;
            }
            ModelLibrary.Model m = models.get(index);
            float scale = m.scaleTo(m.targetSize * (0.82f + r.nextFloat() * 0.42f));
            if (blocked(x, z, minGap)) {
                continue;
            }
            boolean hollow = terrain.region != null && terrain.region.index == 14;
            grid.add(new Instance(index, x, h - 0.04f, z, r.nextFloat() * 360f, scale,
                    hollow ? 0.72f : 0f, hollow ? 0.54f : 0f, hollow ? 1f : 0f,
                    hollow ? 0.32f : 0f));
        }
    }

    /** Cheap spatial rejection against everything already placed. */
    private boolean blocked(float x, float z, float gap) {
        int cx = (int) ((x - grid.originX) / grid.cellSize);
        int cz = (int) ((z - grid.originZ) / grid.cellSize);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (grid.occupied(cx + dx, cz + dz, x, z, gap)) {
                    return true;
                }
            }
        }
        // Landmarks keep their ground clear too.
        for (int i = 0; i < landmarks.size(); i++) {
            Instance o = landmarks.get(i);
            if (o.near(x, z, gap + 2.5f)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------- landmarks

    private void buildVillage(ModelLibrary models, Terrain terrain, long seed) {
        VillageLayout.House[] houses = VillageLayout.houses(terrain, seed);
        float[][] roofTints = {
                {0.78f, 0.30f, 0.27f}, {0.22f, 0.55f, 0.55f},
                {0.43f, 0.35f, 0.74f}, {0.79f, 0.59f, 0.27f}
        };
        for (int i = 0; i < houses.length; i++) {
            VillageLayout.House h = houses[i];
            float yaw = h.yaw;
            addBuildingPart(models, terrain, h, "path_stoneCircle", 0f, 0f,
                    5.8f, yaw, -0.01f, 0.77f, 0.73f, 0.64f, 0.18f);
            addBuildingPart(models, terrain, h, "wall-wood-door", 0f, 2.6f,
                    4.8f, yaw, 0f, 0f, 0f, 0f, 0f);
            addBuildingPart(models, terrain, h, "wall-doorway-square", 0f, -2.6f,
                    4.8f, yaw + 180f, 0f, 0f, 0f, 0f, 0f);
            addBuildingPart(models, terrain, h, "wall-doorway-square", -2.6f, 0f,
                    4.8f, yaw - 90f, 0f, 0f, 0f, 0f, 0f);
            addBuildingPart(models, terrain, h, "wall-doorway-square", 2.6f, 0f,
                    4.8f, yaw + 90f, 0f, 0f, 0f, 0f, 0f);
            addBuildingPart(models, terrain, h, "roof-point", 0f, 0f,
                    5.9f, yaw, 2.25f, roofTints[i][0], roofTints[i][1], roofTints[i][2], 0.24f);
            // Small roof lantern/finial silhouette gives the cottages a storybook skyline.
            addBuildingPart(models, terrain, h, "flag-banner-long", 0f, 0f,
                    1.3f, yaw, 4.2f, 0.70f, 0.77f, 0.94f, 0.38f);
            addBuildingPart(models, terrain, h, "barrel", 3.6f, 2.6f,
                    0.9f, yaw, 0f, 0f, 0f, 0f, 0f);
        }
    }

    private void addBuildingPart(ModelLibrary models, Terrain terrain,
                                 VillageLayout.House house, String name,
                                 float localX, float localZ, float metres, float yaw,
                                 float yOffset, float tr, float tg, float tb, float tint) {
        float a = (float) Math.toRadians(house.yaw);
        float x = house.x + (float) Math.cos(a) * localX + (float) Math.sin(a) * localZ;
        float z = house.z - (float) Math.sin(a) * localX + (float) Math.cos(a) * localZ;
        float y = terrain.heightAt(x, z) + yOffset;
        for (int i = 0; i < models.size(); i++) {
            ModelLibrary.Model model = models.get(i);
            if (model.name.equals(name)) {
                landmarks.add(new Instance(i, x, y, z, yaw, model.scaleTo(metres),
                        tr, tg, tb, tint));
                return;
            }
        }
    }

    private void buildLandmarks(ModelLibrary models, Terrain terrain, long seed) {
        // Flattest patches, so walls and towers do not float or sink.
        float[] gate = flattestSpot(terrain, 40f, 8f, seed);
        if (gate != null) {
            castleGate(models, terrain, gate[0], gate[1]);
        }
        float[] yard = flattestSpot(terrain, 58f, 10f, seed + 129);
        if (yard != null) {
            courtyard(models, terrain, yard[0], yard[1]);
        }
        // A compact timber hamlet around a clear spawn plaza. Layout data is
        // shared with World so every visible wall has a matching collider.
        buildVillage(models, terrain, seed);
        float[] peak = highestSpot(terrain, 30f, 80f, seed + 977);
        if (peak != null && !tooCloseToLandmarks(peak[0], peak[1], 20f)) {
            watchtower(models, terrain, peak[0], peak[1]);
        }
    }

    private boolean tooCloseToLandmarks(float x, float z, float radius) {
        for (int i = 0; i < landmarks.size(); i++) {
            if (landmarks.get(i).near(x, z, radius)) {
                return true;
            }
        }
        return false;
    }

    /** Ring search for the flattest patch; {x, z} or null. */
    private static float[] flattestSpot(Terrain terrain, float radius, float span, long seed) {
        Random r = new Random(seed);
        float[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int i = 0; i < 120; i++) {
            float ang = (float) (r.nextDouble() * Math.PI * 2.0);
            float dist = radius * (0.72f + r.nextFloat() * 0.55f);
            float x = (float) Math.cos(ang) * dist;
            float z = (float) Math.sin(ang) * dist;
            float h = terrain.heightAt(x, z);
            if (h < terrain.waterLevel + 0.8f) {
                continue;
            }
            float score = Math.abs(terrain.heightAt(x + span, z) - h)
                    + Math.abs(terrain.heightAt(x - span, z) - h)
                    + Math.abs(terrain.heightAt(x, z + span) - h)
                    + Math.abs(terrain.heightAt(x, z - span) - h)
                    + terrain.slopeAt(x, z) * 3f;
            if (score < bestScore) {
                bestScore = score;
                best = new float[]{x, z};
            }
        }
        return best;
    }

    private static float[] highestSpot(Terrain terrain, float minRadius, float maxRadius,
                                       long seed) {
        Random r = new Random(seed);
        float[] best = null;
        float bestH = -Float.MAX_VALUE;
        for (int i = 0; i < 200; i++) {
            float ang = (float) (r.nextDouble() * Math.PI * 2.0);
            float dist = minRadius + r.nextFloat() * (maxRadius - minRadius);
            float x = (float) Math.cos(ang) * dist;
            float z = (float) Math.sin(ang) * dist;
            float h = terrain.heightAt(x, z);
            if (h < terrain.waterLevel + 1.5f || terrain.slopeAt(x, z) > 0.45f) {
                continue;
            }
            if (h > bestH) {
                bestH = h;
                best = new float[]{x, z};
            }
        }
        return best;
    }

    /** Places the named model at a uniform scale that gives it {@code metres}. */
    private void place(ModelLibrary models, String name, float x, float z,
                       float metres, float yaw, float groundOffset) {
        for (int i = 0; i < models.size(); i++) {
            ModelLibrary.Model m = models.get(i);
            if (m.name.equals(name)) {
                landmarks.add(new Instance(i, x, terrainHeight + groundOffset, z, yaw,
                        m.scaleTo(metres), 0f, 0f, 0f, 0f));
                return;
            }
        }
    }

    /** Scratch height for {@link #place}; set once per landmark. */
    private float terrainHeight;

    /**
     * Two square towers flanking a gate, a curtain wall either side, banners on
     * top. Every piece is individually grounded so the whole thing follows the
     * terrain instead of floating.
     */
    private void castleGate(ModelLibrary models, Terrain terrain, float bx, float bz) {
        float yaw = (float) Math.toDegrees(Math.atan2(bx, bz));
        float rad = (float) Math.toRadians(yaw);
        float ax = (float) Math.cos(rad);
        float az = (float) Math.sin(rad);
        float sx = -az;
        float sz = ax;
        float span = 5.4f;

        for (int s = -1; s <= 1; s += 2) {
            float x = bx + sx * span * s;
            float z = bz + sz * span * s;
            terrainHeight = terrain.heightAt(x, z) - 0.25f;
            place(models, "tower-square", x, z, 7.0f, yaw, 0f);
            terrainHeight = terrain.heightAt(x, z) + 4.9f;
            place(models, "tower-square-roof", x, z, 8.6f, yaw, 0f);
            terrainHeight = terrain.heightAt(x, z) + 6.2f;
            place(models, "flag", x + sx * s * 0.8f, z + sz * s * 0.8f, 4.2f, yaw + 90f, 0f);
        }
        terrainHeight = terrain.heightAt(bx, bz) - 0.15f;
        place(models, "gate", bx, bz, 5.4f, yaw + 90f, 0f);
        place(models, "wall", bx + ax * 1.5f, bz + az * 1.5f, 4.2f, yaw + 90f, 0f);
        place(models, "wall", bx - ax * 1.5f, bz - az * 1.5f, 4.2f, yaw + 90f, 0f);

        // Curtain wall running away from both towers.
        for (int s = -1; s <= 1; s += 2) {
            for (int i = 1; i <= 3; i++) {
                float d = span + i * 3.6f;
                float x = bx + sx * d * s;
                float z = bz + sz * d * s;
                terrainHeight = terrain.heightAt(x, z) - 0.2f;
                place(models, i == 3 ? "wall-pillar" : "wall", x, z, 4.2f, yaw, 0f);
            }
        }
    }

    /** A low courtyard: a ring of wall sections with a tower at each corner. */
    private void courtyard(ModelLibrary models, Terrain terrain, float bx, float bz) {
        float radius = 12.5f;
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            float ang = (float) (i * Math.PI * 2.0 / segments);
            float x = bx + (float) Math.cos(ang) * radius;
            float z = bz + (float) Math.sin(ang) * radius;
            float yaw = (float) Math.toDegrees(ang) + 90f;
            boolean corner = i % 3 == 0;
            if (corner) {
                terrainHeight = terrain.heightAt(x, z) - 0.25f;
                place(models, "tower-hexagon-base", x, z, 7.0f, yaw, 0f);
                terrainHeight = terrain.heightAt(x, z) + 4.8f;
                place(models, "tower-hexagon-roof", x, z, 6.0f, yaw, 0f);
            } else {
                terrainHeight = terrain.heightAt(x, z) - 0.2f;
                place(models, i % 2 == 0 ? "wall-half" : "wall", x, z, 3.6f, yaw, 0f);
                if (i % 4 == 1) {
                    terrainHeight = terrain.heightAt(x, z) + 3.0f;
                    place(models, "flag-banner-long", x, z, 4.4f, yaw, 0f);
                }
            }
        }
        terrainHeight = terrain.heightAt(bx, bz) + 0.01f;
        place(models, "path_stoneCircle", bx, bz, 6.0f, 0f, 0f);
        terrainHeight = terrain.heightAt(bx + 3.0f, bz + 2.2f);
        place(models, "tent", bx + 3.0f, bz + 2.2f, 2.8f, 20f, 0f);
        terrainHeight = terrain.heightAt(bx - 3.2f, bz - 2.0f);
        place(models, "campfire-pit", bx - 3.2f, bz - 2.0f, 1.6f, 0f, 0f);
        terrainHeight = terrain.heightAt(bx - 1.0f, bz + 4.4f);
        place(models, "barrel", bx - 1.0f, bz + 4.4f, 1.2f, 15f, 0f);
        terrainHeight = terrain.heightAt(bx + 0.5f, bz + 4.9f);
        place(models, "barrel-open", bx + 0.5f, bz + 4.9f, 1.2f, -30f, 0f);
        terrainHeight = terrain.heightAt(bx + 5.0f, bz - 3.6f);
        place(models, "chest", bx + 5.0f, bz - 3.6f, 1.1f, 60f, 0f);
    }

    private void camp(ModelLibrary models, Terrain terrain, float bx, float bz) {
        terrainHeight = terrain.heightAt(bx + 2.4f, bz - 1.2f);
        place(models, "tent", bx + 2.4f, bz - 1.2f, 2.8f, 35f, 0f);
        terrainHeight = terrain.heightAt(bx - 2.6f, bz - 2.0f);
        place(models, "tent-canvas", bx - 2.6f, bz - 2.0f, 2.6f, -25f, 0f);
        terrainHeight = terrain.heightAt(bx, bz);
        place(models, "campfire-pit", bx, bz, 1.6f, 0f, 0f);
        terrainHeight = terrain.heightAt(bx, bz) - 0.02f;
        place(models, "campfire_stones", bx, bz, 2.0f, 10f, 0f);
        terrainHeight = terrain.heightAt(bx + 1.4f, bz + 2.1f);
        place(models, "log", bx + 1.4f, bz + 2.1f, 1.4f, 70f, 0f);
        terrainHeight = terrain.heightAt(bx - 1.5f, bz + 2.3f);
        place(models, "log", bx - 1.5f, bz + 2.3f, 1.4f, 105f, 0f);
        terrainHeight = terrain.heightAt(bx + 3.9f, bz + 1.6f);
        place(models, "barrel", bx + 3.9f, bz + 1.6f, 1.2f, 0f, 0f);
        terrainHeight = terrain.heightAt(bx + 4.4f, bz + 2.4f);
        place(models, "box", bx + 4.4f, bz + 2.4f, 0.9f, 25f, 0f);
        terrainHeight = terrain.heightAt(bx - 4.1f, bz + 1.4f);
        place(models, "chest", bx - 4.1f, bz + 1.4f, 1.1f, -40f, 0f);
        terrainHeight = terrain.heightAt(bx + 1.1f, bz + 3.1f);
        place(models, "bucket", bx + 1.1f, bz + 3.1f, 0.6f, 0f, 0f);
        terrainHeight = terrain.heightAt(bx + 5.4f, bz - 3.0f);
        place(models, "fence-gate", bx + 5.4f, bz - 3.0f, 2.0f, 0f, 0f);
    }

    private void watchtower(ModelLibrary models, Terrain terrain, float bx, float bz) {
        terrainHeight = terrain.heightAt(bx, bz) - 0.4f;
        place(models, "tower-square-mid", bx, bz, 9.6f, 18f, 0f);
        terrainHeight = terrain.heightAt(bx, bz) + 5.6f;
        place(models, "tower-square-top-roof", bx, bz, 9.0f, 18f, 0f);
        terrainHeight = terrain.heightAt(bx, bz) + 4.2f;
        place(models, "flag-banner-long", bx + 1.2f, bz + 1.2f, 6.6f, 18f, 0f);
        terrainHeight = terrain.heightAt(bx + 3.4f, bz + 3.2f) - 0.3f;
        place(models, "wall-doorway", bx + 3.4f, bz + 3.2f, 4.4f, 30f, 0f);
        terrainHeight = terrain.heightAt(bx + 2.0f, bz + 2.0f) + 0.02f;
        place(models, "path_stoneCircle", bx + 2.0f, bz + 2.0f, 5.0f, 0f, 0f);
        // Ruined outbuildings around the base.
        terrainHeight = terrain.heightAt(bx - 4.2f, bz + 2.6f) - 0.4f;
        place(models, "wall-corner", bx - 4.2f, bz + 2.6f, 3.8f, 60f, 0f);
        terrainHeight = terrain.heightAt(bx - 5.4f, bz - 1.2f) - 0.5f;
        place(models, "wall-half", bx - 5.4f, bz - 1.2f, 3.2f, 40f, 0f);
    }

    /**
     * Mountain ridges: lines of cliff blocks along the high ground so the map
     * has a horizon silhouette instead of a smooth green dome. This is where
     * the "mountains everywhere" brief lands -- rock faces on every skyline.
     */
    private void buildRidges(ModelLibrary models, Terrain terrain, long seed) {
        if (models.count(ModelLibrary.CLIFF) == 0) {
            return;
        }
        Random r = new Random(seed + 8111);
        int ridges = 7;
        for (int k = 0; k < ridges; k++) {
            float ang = (float) (k * Math.PI * 2.0 / ridges) + r.nextFloat() * 0.5f;
            float dist = 58f + r.nextFloat() * 30f;
            float cx = (float) Math.cos(ang) * dist;
            float cz = (float) Math.sin(ang) * dist;
            if (terrain.heightAt(cx, cz) < terrain.waterLevel + 1f) {
                continue;
            }
            // Tangent direction, so the ridge runs across the view from spawn.
            float tx = -(float) Math.sin(ang);
            float tz = (float) Math.cos(ang);
            int blocks = 5 + r.nextInt(5);
            for (int i = 0; i < blocks; i++) {
                float t = (i - blocks * 0.5f) * 4.2f;
                float x = cx + tx * t;
                float z = cz + tz * t;
                float h = terrain.heightAt(x, z);
                if (h < terrain.waterLevel + 0.6f) {
                    continue;
                }
                int index = models.pickIndex(ModelLibrary.CLIFF, i * 17 + k * 31);
                if (index < 0) {
                    continue;
                }
                ModelLibrary.Model m = models.get(index);
                float metres = 7f + r.nextFloat() * 9f;
                // Sink the base so a ridge on a slope does not look bolted on.
                grid.add(new Instance(index, x, h - metres * 0.28f, z,
                        r.nextFloat() * 360f, m.scaleTo(metres),
                        0f, 0f, 0f, 0f));
            }
        }
    }

    /**
     * Rock spires: mountains built out of the imported rock and cliff models,
     * standing on the foothills so they read from the whole map.
     *
     * <p>The distant range on the horizon is generated geometry, but these sit
     * inside the playable world: stacked, rotated and scaled stone blocks that
     * form real silhouettes you can walk up to. Each peak is a small
     * teardrop -- a few wide blocks at the base, a few tall ones on top --
     * because a single scaled rock reads as a boulder, not a mountain.</p>
     */
    private void buildPeaks(ModelLibrary models, Terrain terrain, long seed) {
        java.util.Random r = new java.util.Random(seed + 4242);
        int peaks = 14;
        float placed = 0f;
        for (int k = 0; k < peaks * 6 && placed < peaks; k++) {
            double ang = r.nextDouble() * Math.PI * 2.0;
            float dist = 62f + r.nextFloat() * 40f;
            float x = (float) Math.cos(ang) * dist;
            float z = (float) Math.sin(ang) * dist;
            float h = terrain.heightAt(x, z);
            // Peaks need ground above the water and clear of the spawn arena.
            if (h < terrain.waterLevel + 2.5f) {
                continue;
            }
            placed++;

            int role = models.count(ModelLibrary.CLIFF) > 0
                    ? ModelLibrary.CLIFF : ModelLibrary.ROCK;
            float scale = 5.5f + r.nextFloat() * 4.5f;
            int base = 3 + r.nextInt(3);
            // Skirt: broad blocks around the foot, sunk into the hillside.
            for (int i = 0; i < base; i++) {
                double a = (i / (double) base) * Math.PI * 2.0 + r.nextDouble() * 0.7;
                float rad = 1.6f + r.nextFloat() * 1.8f;
                int index = models.pickIndex(role, i * 13 + k * 29);
                if (index < 0) {
                    continue;
                }
                ModelLibrary.Model m = models.get(index);
                grid.add(new Instance(index,
                        x + (float) Math.cos(a) * rad,
                        h - 1.2f,
                        z + (float) Math.sin(a) * rad,
                        r.nextFloat() * 360f,
                        m.scaleTo(scale * (0.85f + r.nextFloat() * 0.4f)),
                        0.92f, 0.95f, 1.04f, 0.35f));
            }
            // Body and spire: progressively smaller and taller blocks.
            int stack = 3 + r.nextInt(3);
            for (int i = 0; i < stack; i++) {
                int index = models.pickIndex(role, i * 7 + k * 17 + 3);
                if (index < 0) {
                    continue;
                }
                ModelLibrary.Model m = models.get(index);
                float t = i / (float) stack;
                float shrink = 0.75f - t * 0.34f;
                grid.add(new Instance(index,
                        x + (r.nextFloat() - 0.5f) * 1.2f * shrink,
                        h + i * scale * 0.42f,
                        z + (r.nextFloat() - 0.5f) * 1.2f * shrink,
                        r.nextFloat() * 360f,
                        m.scaleTo(scale * (0.95f - t * 0.55f)),
                        // Snow creeping down: the higher the block, the paler.
                        0.86f + t * 0.12f, 0.90f + t * 0.09f, 1.0f, 0.25f + t * 0.55f));
            }
        }
    }

    /** The terrain the plan was built against. */
    public Terrain terrain() {
        return terrain;
    }

    /** Total instances in the plan, landmarks included. */
    /**
     * How many instances of the named model are in the world.
     *
     * <p>Used by the placement tests to assert that every imported asset is
     * actually in the terrain. A scatterer that finds no room returns quietly,
     * so counting is the only way to notice.</p>
     */
    public int countNamed(String modelName) {
        int index = indexOfNamed(modelName);
        if (index < 0) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < landmarks.size(); i++) {
            if (landmarks.get(i).model == index) {
                n++;
            }
        }
        for (int cz = 0; cz < grid.rows(); cz++) {
            for (int cx = 0; cx < grid.cols(); cx++) {
                java.util.List<Instance> cell = grid.cell(cx, cz);
                for (int i = 0; i < cell.size(); i++) {
                    if (cell.get(i).model == index) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** Model index for a name, or -1. Needs the library, so it is cached. */
    private java.util.Map<String, Integer> nameToIndex;
    private ModelLibrary library;

    private int indexOfNamed(String modelName) {
        if (nameToIndex == null) {
            nameToIndex = new java.util.HashMap<String, Integer>();
            for (int i = 0; i < library.size(); i++) {
                nameToIndex.put(library.get(i).name, Integer.valueOf(i));
            }
        }
        Integer found = nameToIndex.get(modelName);
        return found == null ? -1 : found.intValue();
    }

    /**
     * Number of prop draws the camera would submit from this position. Each
     * visible prop is one indexed mesh draw; this mirrors SceneRenderer's
     * distance cull and lets the content tests enforce a per-frame budget.
     */
    public int visibleDrawCount(float x, float z) {
        int n = 0;
        for (int i = 0; i < landmarks.size(); i++) {
            Instance in = landmarks.get(i);
            float radius = Math.max(300f, visibleRadius(in.model));
            if (in.near(x, z, radius)) {
                n++;
            }
        }
        for (int cz = 0; cz < grid.rows(); cz++) {
            for (int cx = 0; cx < grid.cols(); cx++) {
                List<Instance> cell = grid.cell(cx, cz);
                for (int i = 0; i < cell.size(); i++) {
                    Instance in = cell.get(i);
                    if (in.near(x, z, visibleRadius(in.model))) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    public int visibleDrawCountForRole(float x, float z, int role) {
        int n = 0;
        for (int cz = 0; cz < grid.rows(); cz++) {
            for (int cx = 0; cx < grid.cols(); cx++) {
                List<Instance> cell = grid.cell(cx, cz);
                for (int i = 0; i < cell.size(); i++) {
                    Instance in = cell.get(i);
                    if (library.get(in.model).role == role
                            && in.near(x, z, visibleRadius(in.model))) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    private float visibleRadius(int model) {
        switch (library.get(model).role) {
            case ModelLibrary.GRASS: return 4f;
            case ModelLibrary.FLOWER: return 4.5f;
            case ModelLibrary.MUSHROOM: return 22f;
            case ModelLibrary.PLANT: return 18f;
            case ModelLibrary.ROCK: return 28f;
            case ModelLibrary.TREE: return 70f;
            case ModelLibrary.LOG: return 60f;
            case ModelLibrary.STUMP: return 45f;
            case ModelLibrary.HILL: return 850f;
            case ModelLibrary.CLIFF: return 65f;
            default: return 120f;
        }
    }

    public int count() {
        int n = landmarks.size();
        for (int cz = 0; cz < grid.rows(); cz++) {
            for (int cx = 0; cx < grid.cols(); cx++) {
                n += grid.at(cx, cz);
            }
        }
        return n;
    }
}
