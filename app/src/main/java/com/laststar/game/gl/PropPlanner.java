package com.laststar.game.gl;

import com.laststar.game.core.Terrain;

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

    public PropPlanner(ModelLibrary models, Terrain terrain, long seed) {
        int cols = 16;
        grid = new Grid(terrain.size / cols, -terrain.size * 0.5f, -terrain.size * 0.5f,
                cols, cols);

        // Attempt counts are high on purpose: the scatterer rejects anything
        // that lands in water, on a cliff, too close to another prop or inside a
        // landmark's clearing, so roughly a third of the attempts survive.
        scatter(models, terrain, seed, ModelLibrary.TREE, 380, 6.5f, 15f, true);
        scatter(models, terrain, seed + 11, ModelLibrary.ROCK, 210, 3.0f, 7f, true);
        scatter(models, terrain, seed + 23, ModelLibrary.PLANT, 760, 2.0f, 8f, true);
        scatter(models, terrain, seed + 37, ModelLibrary.GRASS, 2200, 1.35f, 3f, true);
        scatter(models, terrain, seed + 41, ModelLibrary.FLOWER, 900, 1.5f, 4f, true);
        scatter(models, terrain, seed + 53, ModelLibrary.MUSHROOM, 260, 1.6f, 6f, true);
        scatter(models, terrain, seed + 67, ModelLibrary.LOG, 110, 4.0f, 10f, true);
        scatter(models, terrain, seed + 71, ModelLibrary.STUMP, 55, 3.2f, 8f, true);
        scatter(models, terrain, seed + 83, ModelLibrary.CLIFF, 150, 7.0f, 22f, false);
        scatter(models, terrain, seed + 97, ModelLibrary.HILL, 24, 15f, 36f, false);

        buildLandmarks(models, terrain, seed);
        buildRidges(models, terrain, seed);
        buildPeaks(models, terrain, seed);
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
            grid.add(new Instance(index, x, h - 0.04f, z, r.nextFloat() * 360f, scale,
                    0f, 0f, 0f, 0f));
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
        // The camp sits near the spawn, wherever the ground is flattest in that
        // ring, so it is the first thing the player sees.
        float[] camp = flattestSpot(terrain, 15f, 4f, seed + 613);
        if (camp != null && !tooCloseToLandmarks(camp[0], camp[1], 12f)) {
            camp(models, terrain, camp[0], camp[1]);
        }
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

    /** Total instances in the plan, landmarks included. */
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
