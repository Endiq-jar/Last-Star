package com.laststar.game.core;

import java.util.Random;

/**
 * Deterministic 2D value-noise + fractal helper used by the terrain and
 * texture generators.
 *
 * <p>No Android dependencies, so the same code produces identical worlds on the
 * desktop test harness and on the phone.</p>
 */
public final class Noise {

    private final int[] perm = new int[512];
    private final float[] gradX = new float[256];
    private final float[] gradY = new float[256];

    public Noise(long seed) {
        Random rnd = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 255; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
        for (int i = 0; i < 256; i++) {
            double a = rnd.nextDouble() * Math.PI * 2.0;
            gradX[i] = (float) Math.cos(a);
            gradY[i] = (float) Math.sin(a);
        }
    }

    private static int floor(float v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    /** Quintic smoothstep, C2 continuous. */
    private static float fade(float t) {
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private float corner(int ix, int iy, float fx, float fy) {
        int h = perm[(perm[ix & 255] + (iy & 255)) & 511];
        return gradX[h] * fx + gradY[h] * fy;
    }

    /** Gradient noise in roughly [-1, 1]. */
    public float noise(float x, float y) {
        int ix = floor(x);
        int iy = floor(y);
        float fx = x - ix;
        float fy = y - iy;
        float u = fade(fx);
        float v = fade(fy);

        float n00 = corner(ix, iy, fx, fy);
        float n10 = corner(ix + 1, iy, fx - 1f, fy);
        float n01 = corner(ix, iy + 1, fx, fy - 1f);
        float n11 = corner(ix + 1, iy + 1, fx - 1f, fy - 1f);

        float nx0 = n00 + u * (n10 - n00);
        float nx1 = n01 + u * (n11 - n01);
        return (nx0 + v * (nx1 - nx0)) * 1.4f;
    }

    /** Fractal Brownian motion; {@code octaves} adds detail at each scale. */
    public float fbm(float x, float y, int octaves, float lacunarity, float gain) {
        float sum = 0f;
        float amp = 0.5f;
        float norm = 0f;
        float fx = x;
        float fy = y;
        for (int o = 0; o < octaves; o++) {
            sum += noise(fx, fy) * amp;
            norm += amp;
            amp *= gain;
            fx *= lacunarity;
            fy *= lacunarity;
        }
        return norm > 0f ? sum / norm : 0f;
    }

    /** Ridged variant that makes mountain ridges instead of rolling hills. */
    public float ridged(float x, float y, int octaves) {
        float sum = 0f;
        float amp = 0.5f;
        float norm = 0f;
        float fx = x;
        float fy = y;
        for (int o = 0; o < octaves; o++) {
            float n = 1f - Math.abs(noise(fx, fy));
            sum += n * n * amp;
            norm += amp;
            amp *= 0.5f;
            fx *= 2.03f;
            fy *= 2.03f;
        }
        return norm > 0f ? sum / norm : 0f;
    }

    /** Smooth 0..1 mask useful for lakes. */
    public float lakeMask(float x, float y) {
        float r = 0.62f + 0.34f * fbm(x * 0.35f + 91.7f, y * 0.35f - 12.3f, 3, 2.1f, 0.55f);
        float d = (float) Math.sqrt(x * x + y * y);
        return clamp01(r - d);
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0 + 1e-8f));
        return t * t * (3f - 2f * t);
    }
}
