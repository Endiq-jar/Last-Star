package com.laststar.game.core;

/**
 * Small mutable-free 3D vector helper.
 *
 * <p>All methods return new instances. This keeps the game loop allocation free
 * in practice (escape analysis removes the temporaries) while staying readable,
 * and avoids the aliasing bugs that mutable math classes tend to collect.</p>
 */
public final class Vec3 {

    public static final Vec3 ZERO = new Vec3(0f, 0f, 0f);
    public static final Vec3 UP = new Vec3(0f, 1f, 0f);

    public final float x;
    public final float y;
    public final float z;

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vec3 of(float x, float y, float z) {
        return new Vec3(x, y, z);
    }

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 sub(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 mul(float s) {
        return new Vec3(x * s, y * s, z * s);
    }

    public Vec3 mul(Vec3 o) {
        return new Vec3(x * o.x, y * o.y, z * o.z);
    }

    public Vec3 addScaled(Vec3 dir, float s) {
        return new Vec3(x + dir.x * s, y + dir.y * s, z + dir.z * s);
    }

    public float dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3 cross(Vec3 o) {
        return new Vec3(
                y * o.z - z * o.y,
                z * o.x - x * o.z,
                x * o.y - y * o.x);
    }

    public float lengthSq() {
        return x * x + y * y + z * z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSq());
    }

    public float distanceTo(Vec3 o) {
        return sub(o).length();
    }

    public float distanceSqTo(Vec3 o) {
        return sub(o).lengthSq();
    }

    public Vec3 normalized() {
        float len = length();
        if (len < 1e-6f) {
            return ZERO;
        }
        float inv = 1f / len;
        return new Vec3(x * inv, y * inv, z * inv);
    }

    /** Horizontal (XZ plane) normalisation, used for "walk direction" logic. */
    public Vec3 normalizedXZ() {
        float len = (float) Math.sqrt(x * x + z * z);
        if (len < 1e-6f) {
            return ZERO;
        }
        float inv = 1f / len;
        return new Vec3(x * inv, 0f, z * inv);
    }

    /** Keeps the vector inside a horizontal square of half-extent {@code r}. */
    public Vec3 clampXZ(float r) {
        float nx = x < -r ? -r : (x > r ? r : x);
        float nz = z < -r ? -r : (z > r ? r : z);
        return new Vec3(nx, y, nz);
    }

    public Vec3 withY(float newY) {
        return new Vec3(x, newY, z);
    }

    public float horizontalLength() {
        return (float) Math.sqrt(x * x + z * z);
    }

    /** Signed angle in degrees between the XZ projection of this vector and +Z. */
    public float headingDegrees() {
        if (x * x + z * z < 1e-8f) {
            return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(x, z));
    }

    public Vec3 lerp(Vec3 o, float t) {
        return new Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
    }

    @Override
    public String toString() {
        return "(" + fmt(x) + ", " + fmt(y) + ", " + fmt(z) + ")";
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
