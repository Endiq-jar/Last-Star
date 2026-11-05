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

    /** Fills an arbitrary triangle with one flat colour. */
    public void triangle(float x0, float y0, float x1, float y1, float x2, float y2,
                         float r, float g, float b, float a) {
        Vertex v0 = next();
        Vertex v1 = next();
        Vertex v2 = next();
        v0.set(x0, y0, 0f, 0f, r, g, b, a, false);
        v1.set(x1, y1, 0f, 0f, r, g, b, a, false);
        v2.set(x2, y2, 0f, 0f, r, g, b, a, false);
        vertices.add(v0);
        vertices.add(v1);
        vertices.add(v2);
    }

    /** Fills a convex polygon given as {x0, y0, x1, y1, ...} (fan triangulated). */
    public void polygon(float[] xy, float r, float g, float b, float a) {
        int n = xy.length / 2;
        if (n < 3) {
            return;
        }
        for (int i = 1; i + 1 < n; i++) {
            triangle(xy[0], xy[1], xy[i * 2], xy[i * 2 + 1],
                    xy[(i + 1) * 2], xy[(i + 1) * 2 + 1], r, g, b, a);
        }
    }

    /** Thick-ish line as a quad, with square ends. */
    public void line(float x0, float y0, float x1, float y1, float thickness,
                     float r, float g, float b, float a) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-5f) {
            return;
        }
        float nx = -dy / len * thickness * 0.5f;
        float ny = dx / len * thickness * 0.5f;
        Vertex v0 = next();
        Vertex v1 = next();
        Vertex v2 = next();
        Vertex v3 = next();
        v0.set(x0 + nx, y0 + ny, 0f, 0f, r, g, b, a, false);
        v1.set(x1 + nx, y1 + ny, 0f, 0f, r, g, b, a, false);
        v2.set(x1 - nx, y1 - ny, 0f, 0f, r, g, b, a, false);
        v3.set(x0 - nx, y0 - ny, 0f, 0f, r, g, b, a, false);
        addQuad(v0, v1, v2, v3);
    }

    /** Polyline through arbitrary points, drawn as a chain of thick lines. */
    public void curveApprox(float x0, float y0, float x1, float y1,
                            float x2, float y2, float x3, float y3, float thickness,
                            float r, float g, float b, float a) {
        int steps = 16;
        float px = x0;
        float py = y0;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float u = 1f - t;
            float x = u * u * u * x0 + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x3;
            float y = u * u * u * y0 + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y3;
            line(px, py, x, y, thickness, r, g, b, a);
            px = x;
            py = y;
        }
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

    /** Filled axis-aligned ellipse (a squashed circle, for ground planes). */
    public void ellipse(float cx, float cy, float rx, float ry, int segments,
                        float r, float g, float b, float a) {
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segments);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            triangle(cx, cy,
                    cx + (float) Math.cos(a0) * rx, cy + (float) Math.sin(a0) * ry,
                    cx + (float) Math.cos(a1) * rx, cy + (float) Math.sin(a1) * ry,
                    r, g, b, a);
        }
    }

    /** Elliptical outline between {@code inner} and {@code outer} scale. */
    public void ringEllipse(float cx, float cy, float rx, float ry,
                            float innerScale, int segments,
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
            v0.set(cx + c0 * rx * innerScale, cy + s0 * ry * innerScale, 0f, 0f, r, g, b, a, false);
            v1.set(cx + c1 * rx * innerScale, cy + s1 * ry * innerScale, 0f, 0f, r, g, b, a, false);
            v2.set(cx + c1 * rx, cy + s1 * ry, 0f, 0f, r, g, b, a, false);
            v3.set(cx + c0 * rx, cy + s0 * ry, 0f, 0f, r, g, b, a, false);
            addQuad(v0, v1, v2, v3);
        }
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
    public void text(String s, float x, float y, int scale, float r, float g, float b, float a,
                     AtlasFont font) {
        float cursor = x;
        float w = font.glyphWidth() * scale;
        float h = font.glyphHeight() * scale;
        float adv = font.advance() * scale;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                cursor += adv;
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
            cursor += adv;
        }
    }

    /**
     * Letter-spaced text, the small-caps treatment the menus use for labels.
     * {@code tracking} is the extra space in virtual pixels between glyphs.
     */
    public void textSpaced(String s, float x, float y, int scale, float tracking,
                           float r, float g, float b, float a, AtlasFont font) {
        float cursor = x;
        float w = font.glyphWidth() * scale;
        float h = font.glyphHeight() * scale;
        // Advance from the drawn cell, not the design advance: at scale 2 the
        // glyph is twice as wide, so a fixed design advance would overlap.
        float adv = w + TRACK_PX * scale + tracking;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                cursor += adv;
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
            cursor += adv;
        }
    }

    /** Width of letter-spaced text, matching {@link #textSpaced}. */
    /** Default gap between letter-spaced glyphs, in design pixels at scale 1. */
    public static final float TRACK_PX = 2f;

    public static float textSpacedWidth(String s, AtlasFont font, int scale, float tracking) {
        if (s.length() == 0) {
            return 0f;
        }
        float cell = font.glyphWidth() * scale + TRACK_PX * scale + tracking;
        return s.length() * cell - TRACK_PX * scale - tracking;
    }

    /**
     * Greedy word wrap measured against the real glyph advance, so wrapped lines
     * never overflow the box they were sized for.
     */
    public static String[] wrap(String text, AtlasFont font, int scale, float tracking,
                                float maxWidth) {
        String[] words = text.split(" ");
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (textSpacedWidth(candidate, font, scale, tracking) > maxWidth
                    && line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines.toArray(new String[lines.size()]);
    }

    /**
     * Horizontal room an uppercase word needs when drawn on a box that is
     * {@code boxWidth} wide and {@code boxHeight} tall.
     *
     * <p>{@link #textSpaced} draws the whole atlas cell (cap height plus the
     * descender strip), so on a tight button the optical size has to come from
     * the cell size rather than from {@code font.glyphHeight()}. </p>
     */
    public static void fitToBox(String s, AtlasFont font, float boxWidth, float boxHeight,
                                float tracking, int maxScale, int[] outScale) {
        int best = 1;
        for (int scale = 1; scale <= maxScale; scale++) {
            float cellW = font.glyphWidth() * scale;
            float cellH = font.glyphHeight() * scale;
            float w = s.length() * (cellW + TRACK_PX * scale + tracking)
                    - TRACK_PX * scale - tracking;
            if (w > boxWidth || cellH > boxHeight) {
                break;
            }
            best = scale;
        }
        outScale[0] = best;
    }

    public void textSpacedCentred(String s, float cx, float y, int scale, float tracking,
                                  float r, float g, float b, float a, AtlasFont font) {
        float width = textSpacedWidth(s, font, scale, tracking);
        textSpaced(s, cx - width * 0.5f, y, scale, tracking, r, g, b, a, font);
    }

    public void textSpacedRight(String s, float rightX, float y, int scale, float tracking,
                                float r, float g, float b, float a, AtlasFont font) {
        float width = textSpacedWidth(s, font, scale, tracking);
        textSpaced(s, rightX - width, y, scale, tracking, r, g, b, a, font);
    }

    public void textCentred(String s, float cx, float y, int scale,
                            float r, float g, float b, float a, AtlasFont font) {
        float width = font.textWidth(s, scale);
        text(s, cx - width * 0.5f, y, scale, r, g, b, a, font);
    }

    public void textRight(String s, float rightX, float y, int scale,
                          float r, float g, float b, float a, AtlasFont font) {
        float width = font.textWidth(s, scale);
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
