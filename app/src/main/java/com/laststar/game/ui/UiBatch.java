package com.laststar.game.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates 2D UI geometry (solid quads, circles and text) for one frame,
 * in a virtual coordinate space, then hands it to the renderer as a single
 * batched draw.
 *
 * <p>This class holds no GL state so it can be unit tested.</p>
 */
public final class UiBatch {

    /** A single triangle, packaging everything the GL layer needs. */
    public static final class Vertex {
        public float x, y, u, v, r, g, b, a;
        public boolean textured;

        void set(float x, float y, float u, float v, float r, float g, float b, float a, boolean textured) {
            this.x = x;
            this.y = y;
            this.u = u;
            this.v = v;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
            this.textured = textured;
        }
    }

    private final List<Vertex> vertices = new ArrayList<>();
    public int virtualWidth = 720;
    public int virtualHeight = 405;

    // Reusable vertex objects to keep the per-frame allocation count down.
    private final List<Vertex> pool = new ArrayList<>();
    private int poolIndex;

    public void begin(int vw, int vh) {
        vertices.clear();
        poolIndex = 0;
        virtualWidth = vw;
        virtualHeight = vh;
    }

    private Vertex next() {
        if (poolIndex < pool.size()) {
            return pool.get(poolIndex++);
        }
        Vertex v = new Vertex();
        pool.add(v);
        poolIndex++;
        return v;
    }

    public int vertexCount() {
        return vertices.size();
    }

    public List<Vertex> vertices() {
        return vertices;
    }

    /** Adds an axis-aligned quad with per-corner alpha (for gradient panels). */
    public void quad(float x, float y, float w, float h,
                     float r, float g, float b, float a) {
        quadGradient(x, y, w, h, r, g, b, a, r, g, b, a * 0.55f);
    }

    public void quadGradient(float x, float y, float w, float h,
                             float r0, float g0, float b0, float a0,
                             float r1, float g1, float b1, float a1) {
        Vertex v0 = next();
        Vertex v1 = next();
        Vertex v2 = next();
        Vertex v3 = next();
        v0.set(x, y, 0f, 0f, r0, g0, b0, a0, false);
        v1.set(x + w, y, 0f, 0f, r0, g0, b0, a0, false);
        v2.set(x + w, y + h, 0f, 0f, r1, g1, b1, a1, false);
        v3.set(x, y + h, 0f, 0f, r1, g1, b1, a1, false);
        addQuad(v0, v1, v2, v3);
    }

    private void addQuad(Vertex v0, Vertex v1, Vertex v2, Vertex v3) {
        vertices.add(v0);
        vertices.add(v1);
        vertices.add(v2);
        vertices.add(v0);
        vertices.add(v2);
        vertices.add(v3);
    }

    /** Filled circle centred on (cx, cy). */
    public void circle(float cx, float cy, float radius, int segments,
                       float r, float g, float b, float a) {
        addCircle(cx, cy, radius, 0f, segments, r, g, b, a, a);
    }

    /** Ring between {@code inner} and {@code outer}. */
    public void ring(float cx, float cy, float outer, float inner, int segments,
                     float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segments);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            Vertex v0 = next();
            Vertex v1 = next();
            Vertex v2 = next();
            Vertex v3 = next();
            v0.set(cx + c0 * inner, cy + s0 * inner, 0f, 0f, r, g, b, a, false);
            v1.set(cx + c1 * inner, cy + s1 * inner, 0f, 0f, r, g, b, a, false);
            v2.set(cx + c1 * outer, cy + s1 * outer, 0f, 0f, r, g, b, a, false);
            v3.set(cx + c0 * outer, cy + s0 * outer, 0f, 0f, r, g, b, a, false);
            addQuad(v0, v1, v2, v3);
        }
    }

    /** Soft radial glow: bright in the middle, transparent at the rim. */
    public void glow(float cx, float cy, float radius, int segments,
                     float r, float g, float b, float strength) {
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segments);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            Vertex centre = next();
            Vertex v1 = next();
            Vertex v2 = next();
            centre.set(cx, cy, 0f, 0f, r, g, b, strength, false);
            v1.set(cx + (float) Math.cos(a0) * radius, cy + (float) Math.sin(a0) * radius,
                    0f, 0f, r, g, b, 0f, false);
            v2.set(cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius,
                    0f, 0f, r, g, b, 0f, false);
            vertices.add(centre);
            vertices.add(v1);
            vertices.add(v2);
        }
    }

    private void addCircle(float cx, float cy, float radius, float innerRadius, int segments,
                           float r, float g, float b, float aInner, float aOuter) {
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segments);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            Vertex v0 = next();
            Vertex v1 = next();
            Vertex v2 = next();
            v0.set(cx, cy, 0f, 0f, r, g, b, aInner, false);
            v1.set(cx + (float) Math.cos(a0) * radius, cy + (float) Math.sin(a0) * radius,
                    0f, 0f, r, g, b, aOuter, false);
            v2.set(cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius,
                    0f, 0f, r, g, b, aOuter, false);
            vertices.add(v0);
            vertices.add(v1);
            vertices.add(v2);
        }
    }

    /**
     * Draws text with the built-in font, top-left anchored.
     *
     * @param scale integer pixel scale of one font pixel
     */
    public void text(String s, float x, float y, int scale, float r, float g, float b, float a, Font font) {
        float cursor = x;
        float w = Font.GLYPH_W * scale;
        float h = Font.GLYPH_H * scale;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                cursor += Font.ADVANCE * scale;
                continue;
            }
            float[] uv = font.uv(c);
            Vertex v0 = next();
            Vertex v1 = next();
            Vertex v2 = next();
            Vertex v3 = next();
            v0.set(cursor, y, uv[0], uv[1], r, g, b, a, true);
            v1.set(cursor + w, y, uv[0] + uv[2], uv[1], r, g, b, a, true);
            v2.set(cursor + w, y + h, uv[0] + uv[2], uv[1] + uv[3], r, g, b, a, true);
            v3.set(cursor, y + h, uv[0], uv[1] + uv[3], r, g, b, a, true);
            addQuad(v0, v1, v2, v3);
            cursor += Font.ADVANCE * scale;
        }
    }

    public void textCentred(String s, float cx, float y, int scale,
                            float r, float g, float b, float a, Font font) {
        float width = Font.textWidth(s, scale);
        text(s, cx - width * 0.5f, y, scale, r, g, b, a, font);
    }

    public void textRight(String s, float rightX, float y, int scale,
                          float r, float g, float b, float a, Font font) {
        float width = Font.textWidth(s, scale);
        text(s, rightX - width, y, scale, r, g, b, a, font);
    }

    /** Full-screen tint, useful for damage flashes. */
    public void overlay(float r, float g, float b, float a) {
        quad(0f, 0f, virtualWidth, virtualHeight, r, g, b, a);
    }

    /** Vertical gradient overlay fading from {@code topAlpha} to 0. */
    public void topFade(float height, float r, float g, float b, float topAlpha) {
        // Build as one gradient quad per band so the fade is smooth.
        int bands = 8;
        float bandH = height / bands;
        for (int i = 0; i < bands; i++) {
            float t0 = 1f - (float) i / bands;
            float t1 = 1f - (float) (i + 1) / bands;
            quadGradient(0f, i * bandH, virtualWidth, bandH + 0.5f,
                    r, g, b, topAlpha * t0 * t0, r, g, b, topAlpha * t1 * t1);
        }
    }

    /** Darkens the screen edges for a cinematic look. */
    public void vignette(float strength) {
        float band = 26f;
        for (int i = 0; i < 5; i++) {
            float a = strength * (1f - i / 5f) * 0.5f;
            float inset = i * band;
            quadGradient(0f, inset, virtualWidth, band, 0f, 0f, 0f, a, 0f, 0f, 0f, 0f);
            quadGradient(0f, virtualHeight - inset - band, virtualWidth, band,
                    0f, 0f, 0f, 0f, 0f, 0f, 0f, a);
            quadGradient(inset, 0f, band, virtualHeight, 0f, 0f, 0f, a, 0f, 0f, 0f, 0f);
            quadGradient(virtualWidth - inset - band, 0f, band, virtualHeight,
                    0f, 0f, 0f, a * 0.6f, 0f, 0f, 0f, 0f);
        }
    }
}
