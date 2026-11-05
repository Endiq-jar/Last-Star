package com.laststar.game.core;

/**
 * Column-major 4x4 matrix, the layout OpenGL ES expects.
 *
 * <p>Elements are stored as {@code m[column * 4 + row]} which means the array
 * can be uploaded straight to {@code glUniformMatrix4fv} without transposing.</p>
 */
public final class Mat4 {

    public final float[] m = new float[16];

    public Mat4() {
        identity();
    }

    public Mat4 identity() {
        for (int i = 0; i < 16; i++) {
            m[i] = 0f;
        }
        m[0] = 1f;
        m[5] = 1f;
        m[10] = 1f;
        m[15] = 1f;
        return this;
    }

    public Mat4 copyFrom(Mat4 o) {
        System.arraycopy(o.m, 0, m, 0, 16);
        return this;
    }

    public static Mat4 perspective(float fovYDegrees, float aspect, float near, float far) {
        Mat4 r = new Mat4();
        float f = 1f / (float) Math.tan(Math.toRadians(fovYDegrees) * 0.5);
        float nf = 1f / (near - far);
        for (int i = 0; i < 16; i++) {
            r.m[i] = 0f;
        }
        r.m[0] = f / aspect;
        r.m[5] = f;
        r.m[10] = (far + near) * nf;
        r.m[11] = -1f;
        r.m[14] = 2f * far * near * nf;
        return r;
    }

    public static Mat4 lookAt(Vec3 eye, Vec3 target, Vec3 up) {
        Vec3 f = target.sub(eye).normalized();      // forward
        Vec3 s = f.cross(up).normalized();          // right
        Vec3 u = s.cross(f);                        // corrected up
        Mat4 r = new Mat4();
        r.m[0] = s.x;
        r.m[4] = s.y;
        r.m[8] = s.z;
        r.m[1] = u.x;
        r.m[5] = u.y;
        r.m[9] = u.z;
        r.m[2] = -f.x;
        r.m[6] = -f.y;
        r.m[10] = -f.z;
        r.m[12] = -s.dot(eye);
        r.m[13] = -u.dot(eye);
        r.m[14] = f.dot(eye);
        r.m[15] = 1f;
        return r;
    }

    public static Mat4 translation(float x, float y, float z) {
        Mat4 r = new Mat4();
        r.m[12] = x;
        r.m[13] = y;
        r.m[14] = z;
        return r;
    }

    public static Mat4 scale(float sx, float sy, float sz) {
        Mat4 r = new Mat4();
        r.m[0] = sx;
        r.m[5] = sy;
        r.m[10] = sz;
        return r;
    }

    public static Mat4 rotationY(float degrees) {
        float a = (float) Math.toRadians(degrees);
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        Mat4 r = new Mat4();
        r.m[0] = c;
        r.m[2] = -s;
        r.m[8] = s;
        r.m[10] = c;
        return r;
    }

    public static Mat4 rotationX(float degrees) {
        float a = (float) Math.toRadians(degrees);
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        Mat4 r = new Mat4();
        r.m[5] = c;
        r.m[6] = s;
        r.m[9] = -s;
        r.m[10] = c;
        return r;
    }

    /**
     * Composes {@code this = a * b} (apply b first, then a) without allocating.
     */
    public Mat4 setMultiply(Mat4 a, Mat4 b) {
        float[] out = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a.m[k * 4 + row] * b.m[c * 4 + k];
                }
                out[c * 4 + row] = sum;
            }
        }
        System.arraycopy(out, 0, m, 0, 16);
        return this;
    }

    /** Transforms a point (w = 1) and applies the perspective divide. */
    public Vec3 transformPoint(Vec3 p) {
        float x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12];
        float y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13];
        float z = m[2] * p.x + m[6] * p.y + m[10] * p.z + m[14];
        float w = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15];
        if (Math.abs(w) < 1e-6f) {
            return new Vec3(x, y, z);
        }
        return new Vec3(x / w, y / w, z / w);
    }

    public float[] data() {
        return m;
    }
}
