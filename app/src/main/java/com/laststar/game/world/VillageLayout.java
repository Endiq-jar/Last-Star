package com.laststar.game.world;

import com.laststar.game.core.Terrain;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Shared deterministic placement for the starter fantasy hamlet and its colliders. */
public final class VillageLayout {
    public static final float HOUSE_SIZE = 5.2f;
    public static final float WALL_THICKNESS = 0.38f;
    public static final float DOOR_WIDTH = 1.75f;

    public static final class House {
        public final float x, z, yaw;
        public House(float x, float z, float yaw) { this.x = x; this.z = z; this.yaw = yaw; }
    }

    private VillageLayout() { }

    public static House[] houses(Terrain terrain, long seed) {
        if (terrain == null || (terrain.region != null && terrain.region.index == 14)) {
            return new House[0];
        }
        float[] hub = findHub(terrain, seed);
        Random random = new Random(seed ^ 0x6A09E667F3BCC909L);
        float rotation = random.nextFloat() * 0.22f - 0.11f;
        House[] result = new House[4];
        int at = 0;
        for (int iz = -1; iz <= 1; iz += 2) {
            for (int ix = -1; ix <= 1; ix += 2) {
                float lx = ix * 7.1f;
                float lz = iz * 7.1f;
                float c = (float) Math.cos(rotation);
                float s = (float) Math.sin(rotation);
                float x = hub[0] + c * lx + s * lz;
                float z = hub[1] - s * lx + c * lz;
                float yaw = (float) Math.toDegrees(Math.atan2(hub[0] - x, hub[1] - z));
                result[at++] = new House(x, z, yaw);
            }
        }
        return result;
    }

    /** Adds oriented wall boxes, leaving a walk-through opening on each house front. */
    public static List<BuildingCollider> colliders(Terrain terrain, long seed) {
        List<BuildingCollider> out = new ArrayList<BuildingCollider>();
        House[] houses = houses(terrain, seed);
        for (House h : houses) {
            float half = HOUSE_SIZE * 0.5f;
            float doorHalf = DOOR_WIDTH * 0.5f;
            float sideLength = half - doorHalf;
            addWall(out, h, -doorHalf - sideLength * 0.5f, half,
                    sideLength * 0.5f, WALL_THICKNESS * 0.5f);
            addWall(out, h, doorHalf + sideLength * 0.5f, half,
                    sideLength * 0.5f, WALL_THICKNESS * 0.5f);
            addWall(out, h, -doorHalf - sideLength * 0.5f, -half,
                    sideLength * 0.5f, WALL_THICKNESS * 0.5f);
            addWall(out, h, doorHalf + sideLength * 0.5f, -half,
                    sideLength * 0.5f, WALL_THICKNESS * 0.5f);
            addWall(out, h, -half, -doorHalf - sideLength * 0.5f,
                    WALL_THICKNESS * 0.5f, sideLength * 0.5f);
            addWall(out, h, -half, doorHalf + sideLength * 0.5f,
                    WALL_THICKNESS * 0.5f, sideLength * 0.5f);
            addWall(out, h, half, -doorHalf - sideLength * 0.5f,
                    WALL_THICKNESS * 0.5f, sideLength * 0.5f);
            addWall(out, h, half, doorHalf + sideLength * 0.5f,
                    WALL_THICKNESS * 0.5f, sideLength * 0.5f);
        }
        return out;
    }

    private static void addWall(List<BuildingCollider> out, House house,
                                float localX, float localZ, float halfX, float halfZ) {
        float a = (float) Math.toRadians(house.yaw);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float x = house.x + c * localX + s * localZ;
        float z = house.z - s * localX + c * localZ;
        out.add(new BuildingCollider(x, z, halfX, halfZ, house.yaw));
    }

    /** Finds a reasonably level hamlet centre using only deterministic terrain queries. */
    private static float[] findHub(Terrain terrain, long seed) {
        Random random = new Random(seed ^ 0xBB67AE8584CAA73BL);
        float bestScore = Float.MAX_VALUE;
        float[] best = {27f, 0f};
        for (int i = 0; i < 240; i++) {
            float angle = random.nextFloat() * (float) (Math.PI * 2.0);
            float radius = 24f + random.nextFloat() * 6f;
            float x = (float) Math.cos(angle) * radius;
            float z = (float) Math.sin(angle) * radius;
            float score = terrain.slopeAt(x, z) * 7f;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    float px = x + dx * 7.1f;
                    float pz = z + dz * 7.1f;
                    float h = terrain.heightAt(px, pz);
                    float hubHeight = terrain.heightAt(x, z);
                    score += terrain.slopeAt(px, pz) * 3f + Math.abs(h - hubHeight) * 0.06f;
                }
            }
            if (score < bestScore) { bestScore = score; best[0] = x; best[1] = z; }
        }
        return best;
    }
}
