package com.laststar.game.core;

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

    public Terrain(float size, int segments, long seed) {
        this.size = size;
        this.segments = segments;
        this.cellSize = size / segments;
        this.waterLevel = -0.55f;
        this.heights = new float[(segments + 1) * (segments + 1)];
        this.blend = new byte[(segments + 1) * (segments + 1)];
        generate(seed);
    }

    private void generate(long seed) {
        Noise base = new Noise(seed);
        Noise detail = new Noise(seed ^ 0x9E3779B97F4A7C15L);
        Noise ridge = new Noise(seed + 7717L);
        int n = segments + 1;
        for (int gz = 0; gz < n; gz++) {
            for (int gx = 0; gx < n; gx++) {
                float wx = (gx * cellSize) - size * 0.5f;
                float wz = (gz * cellSize) - size * 0.5f;
                float h = height(wx, wz, base, detail, ridge);
                heights[gz * n + gx] = h;
                float bl = Noise.clamp01(0.35f + 0.8f * detail.fbm(wx * 0.06f, wz * 0.06f, 3, 2f, 0.5f));
                blend[gz * n + gx] = (byte) (bl * 127f);
            }
        }
    }

    private float height(float wx, float wz, Noise base, Noise detail, Noise ridge) {
        float nx = wx * 0.0125f;
        float nz = wz * 0.0125f;

        // Rolling base terrain.
        float h = base.fbm(nx, nz, 4, 2.02f, 0.5f) * 5.5f;

        // Foothills towards the rim. Deliberately moderate: shards spawn up to
        // 86 m out and the player has to walk to them, so the playable ground
        // can never turn into a wall. The tall, dramatic peaks are a separate
        // decorative range outside the map (see Geometry.mountainRange) -- they
        // need no collision, so they can be as huge as the view wants.
        float radial = (float) Math.sqrt(wx * wx + wz * wz) / (size * 0.5f);
        float rim = Noise.smoothstep(0.30f, 0.92f, radial);
        h += ridge.ridged(nx * 0.62f + 13.1f, nz * 0.62f - 7.7f, 4) * 21.0f * rim;

        // Fine bumps for footstep variation.
        h += detail.fbm(wx * 0.05f, wz * 0.05f, 3, 2.11f, 0.45f) * 1.1f;

        // Flatten the spawn plateau (radius ~9 m at the origin).
        float d = (float) Math.sqrt(wx * wx + wz * wz);
        float plateau = 1f - Noise.smoothstep(7f, 16f, d);
        h = h * (1f - plateau) + plateau * 0.35f;

        // Carve a lake basin in the north-east quadrant, then clamp above it.
        float lake = base.lakeMask(nx * 1.15f - 2.4f, nz * 1.15f + 2.1f);
        h -= lake * 3.2f;

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
        float fx = (wx + size * 0.5f) / cellSize;
        float fz = (wz + size * 0.5f) / cellSize;
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
        float hl = heightAt(wx - d, wz);
        float hr = heightAt(wx + d, wz);
        float hd = heightAt(wx, wz - d);
        float hu = heightAt(wx, wz + d);
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
        float fx = Noise.clamp((wx + size * 0.5f) / cellSize, 0f, segments - 1e-4f);
        float fz = Noise.clamp((wz + size * 0.5f) / cellSize, 0f, segments - 1e-4f);
        int x = (int) fx;
        int z = (int) fz;
        float v = (float) (blend[z * n + x] & 0xFF) / 127f;
        return Noise.clamp01(v);
    }
}
