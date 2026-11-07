package com.laststar.game.world;

/** Static horizontal oriented-box collider used by building walls. */
public final class BuildingCollider {
    public final float x, z, halfX, halfZ, yaw;
    public BuildingCollider(float x, float z, float halfX, float halfZ, float yaw) {
        this.x = x; this.z = z; this.halfX = halfX; this.halfZ = halfZ; this.yaw = yaw;
    }

    /** Outward horizontal normal near the closest face; used to slide along walls. */
    public void normalAt(float px, float pz, float[] out) {
        float a = (float) Math.toRadians(yaw);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float dx = px - x, dz = pz - z;
        float localX = c * dx - s * dz;
        float localZ = s * dx + c * dz;
        float nearestX = Math.max(-halfX, Math.min(halfX, localX));
        float nearestZ = Math.max(-halfZ, Math.min(halfZ, localZ));
        float nx = localX - nearestX, nz = localZ - nearestZ;
        float length = (float) Math.sqrt(nx * nx + nz * nz);
        if (length < 1e-5f) {
            float gapX = halfX - Math.abs(localX);
            float gapZ = halfZ - Math.abs(localZ);
            if (gapX < gapZ) nx = localX < 0f ? -1f : 1f;
            else nz = localZ < 0f ? -1f : 1f;
            length = (float) Math.sqrt(nx * nx + nz * nz);
        }
        nx /= length; nz /= length;
        out[0] = c * nx + s * nz;
        out[1] = -s * nx + c * nz;
    }

    /** Circle-vs-oriented-rectangle test for a character's horizontal footprint. */
    public boolean blocks(float px, float pz, float radius) {
        float a = (float) Math.toRadians(yaw);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float dx = px - x, dz = pz - z;
        float localX = c * dx - s * dz;
        float localZ = s * dx + c * dz;
        float nearestX = Math.max(-halfX, Math.min(halfX, localX));
        float nearestZ = Math.max(-halfZ, Math.min(halfZ, localZ));
        float ox = localX - nearestX, oz = localZ - nearestZ;
        return ox * ox + oz * oz < radius * radius;
    }
}
