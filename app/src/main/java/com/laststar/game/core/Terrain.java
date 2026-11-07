package com.laststar.game.core;

import com.laststar.game.world.lore.Elaris;

/**
 * Terrain height field, generated once per world and then queried in O(1) by
 * rendering, physics and gameplay code.
 *
 * <p>The world is a square of {@link #size} metres divided into
 * {@link #segments} x {@link #segments} quads. Heights are stored per grid
 * vertex and bilinearly interpolated, which matches exactly how the mesh is
 * rendered, so the player never floats above or sinks into the ground.</p>
 */
public final class Terrain {

    /** World size in metres (the world spans [-size/2, +size/2] on X and Z). */
    public final float size;
    /** Grid subdivisions per axis. */
    public final int segments;
    /** Water plane height. */
    public final float waterLevel;
    /** Per-vertex heights, row major, {@code (segments+1)^2} entries. */
    public final float[] heights;
    /** Per-vertex 0..1 grass/dirt blend used by the renderer. */
    public final byte[] blend;
    /** Baked flat lookup so gameplay loops do not need to re-derive it. */
    private final float cellSize;
    /** Global lower-left sample coordinate; chunk tiles share exact edges. */
    public final float originX;
    public final float originZ;
    /** Radius used for region-scale shaping, shared by every streamed tile. */
    public final float worldSpanSize;
    private final Noise baseNoise;
    private final Noise detailNoise;
    private final Noise ridgeNoise;

    public Terrain(float size, int segments, long seed) {
        this(size, segments, seed, null, -size * 0.5f, -size * 0.5f, size);
    }

    /**
     * Builds a region's terrain.
     *
     * <p>The fourteen settlements are shaped differently on purpose: Frostmere
     * is jagged and white, The Deep is a walled cavern with no water and no
     * sky, Suncrest is a dune field. Those differences are generation
     * parameters rather than a texture swap, so the ground the player walks on
     * is genuinely a different surface.</p>
     */
    public static Terrain forRegion(Elaris.Region region, float size, int segments,
                                    long seed) {
        return new Terrain(size, segments, seed, region,
                -size * 0.5f, -size * 0.5f, size);
    }

    /** Builds one globally aligned tile for the endless regional terrain stream. */
    public static Terrain chunk(Elaris.Region region, float size, int segments,
                                long seed, int chunkX, int chunkZ, float worldSpanSize) {
        return new Terrain(size, segments, seed, region,
                chunkX * size - size * 0.5f,
                chunkZ * size - size * 0.5f,
                worldSpanSize);
    }

    private Terrain(float size, int segments, long seed, Elaris.Region region,
                    float originX, float originZ, float worldSpanSize) {
        this.size = size;
        this.segments = segments;
        this.cellSize = size / segments;
        this.originX = originX;
        this.originZ = originZ;
        this.worldSpanSize = Math.max(size, worldSpanSize);
        this.region = region;
        this.waterLevel = region == null ? -0.55f : region.waterLevel;
        this.heights = new float[(segments + 1) * (segments + 1)];
        this.blend = new byte[(segments + 1) * (segments + 1)];
        this.baseNoise = new Noise(seed);
        this.detailNoise = new Noise(seed ^ 0x9E3779B97F4A7C15L);
        this.ridgeNoise = new Noise(seed + 7717L);
        generate();
    }

    /** The region this terrain was built for, or null for the default. */
    public final Elaris.Region region;

    private void generate() {
        int n = segments + 1;
        for (int gz = 0; gz < n; gz++) {
            for (int gx = 0; gx < n; gx++) {
                float wx = worldX(gx);
                float wz = worldZ(gz);
                heights[gz * n + gx] = heightWorld(wx, wz);
                float bl = Noise.clamp01(0.35f + 0.8f * detailNoise.fbm(
                        wx * 0.06f, wz * 0.06f, 3, 2f, 0.5f));
                blend[gz * n + gx] = (byte) (bl * 127f);
            }
        }
    }

    /** Global procedural height function shared at every chunk boundary. */
    public float heightWorld(float wx, float wz) {
        return height(wx, wz, baseNoise, detailNoise, ridgeNoise);
    }

    public float worldX(int gx) {
        return originX + gx * cellSize;
    }

    public float worldZ(int gz) {
        return originZ + gz * cellSize;
    }

    private float height(float wx, float wz, Noise base, Noise detail, Noise ridge) {
        float nx = wx * 0.0125f;
        float nz = wz * 0.0125f;

        // Per-region shape. The default is the original world, so a Terrain
        // built without a region behaves exactly as it always did.
        float baseAmp = region == null ? 5.5f : region.baseAmp;
        float ridgeAmp = region == null ? 21.0f : region.ridgeAmp;
        float lakeAmount = region == null ? 3.2f : region.lakeDepth;
        float plateauY = region == null ? 0.35f : region.plateauHeight;
        float rimStart = region == null ? 0.30f : region.rimStart;

        // Rolling base terrain.
        float h = base.fbm(nx, nz, 4, 2.02f, 0.5f) * baseAmp;

        // Foothills towards the rim. Deliberately moderate: shards spawn up to
        // 86 m out and the player has to walk to them, so the playable ground
        // can never turn into a wall. The tall, dramatic peaks are a separate
        // decorative range outside the map (see Geometry.mountainRange) -- they
        // need no collision, so they can be as huge as the view wants.
        float radial = (float) Math.sqrt(wx * wx + wz * wz) / (worldSpanSize * 0.5f);
        float rim = Noise.smoothstep(rimStart, 0.92f, radial);
        h += ridge.ridged(nx * 0.62f + 13.1f, nz * 0.62f - 7.7f, 4) * ridgeAmp * rim;

        // Fine bumps for footstep variation.
        h += detail.fbm(wx * 0.05f, wz * 0.05f, 3, 2.11f, 0.45f) * 1.1f;

        // Flatten the spawn plateau (radius ~9 m at the origin).
        float d = (float) Math.sqrt(wx * wx + wz * wz);
        float plateau = 1f - Noise.smoothstep(7f, 16f, d);
        h = h * (1f - plateau) + plateau * plateauY;

        // Carve a lake basin in the north-east quadrant, then clamp above it.
        // A region with no lake skips it, which is what gives The Deep its
        // walled floor and Suncrest its dry dunes.
        if (lakeAmount > 0.01f) {
            float lake = base.lakeMask(nx * 1.15f - 2.4f, nz * 1.15f + 2.1f);
            h -= lake * lakeAmount;
        }

        return h;
    }

    public float getHeight(int gx, int gz) {
        return heights[gz * (segments + 1) + gx];
    }

    /**
     * Bilinear height sample at a world position. Matches the rendered mesh
     * triangulation closely enough for character grounding.
     */
    public float heightAt(float wx, float wz) {
        int n = segments + 1;
        float fx = (wx - originX) / cellSize;
        float fz = (wz - originZ) / cellSize;
        if (fx < 0f) {
            fx = 0f;
        }
        if (fz < 0f) {
            fz = 0f;
        }
        if (fx > segments - 1e-4f) {
            fx = segments - 1e-4f;
        }
        if (fz > segments - 1e-4f) {
            fz = segments - 1e-4f;
        }
        int x0 = (int) fx;
        int z0 = (int) fz;
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        float tx = fx - x0;
        float tz = fz - z0;
        float h00 = heights[z0 * n + x0];
        float h10 = heights[z0 * n + x1];
        float h01 = heights[z1 * n + x0];
        float h11 = heights[z1 * n + x1];
        float a = h00 + (h10 - h00) * tx;
        float b = h01 + (h11 - h01) * tx;
        return a + (b - a) * tz;
    }

    /** Approximate surface normal via central differences (not normalised). */
    public Vec3 normalAt(float wx, float wz) {
        float d = cellSize;
        float hl = heightWorld(wx - d, wz);
        float hr = heightWorld(wx + d, wz);
        float hd = heightWorld(wx, wz - d);
        float hu = heightWorld(wx, wz + d);
        return new Vec3(hl - hr, 2f * d, hd - hu).normalized();
    }

    public float slopeAt(float wx, float wz) {
        Vec3 n = normalAt(wx, wz);
        return 1f - Noise.clamp01(n.y);
    }

    public boolean isUnderwater(float wx, float wz) {
        return heightAt(wx, wz) < waterLevel;
    }

    public float blendAt(float wx, float wz) {
        int n = segments + 1;
        float fx = Noise.clamp((wx - originX) / cellSize, 0f, segments - 1e-4f);
        float fz = Noise.clamp((wz - originZ) / cellSize, 0f, segments - 1e-4f);
        int x = (int) fx;
        int z = (int) fz;
        float v = (float) (blend[z * n + x] & 0xFF) / 127f;
        return Noise.clamp01(v);
    }
}
