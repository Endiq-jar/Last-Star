package com.laststar.game.ui;

/**
 * Drawing primitives for the ornate menu style: gold frames with corner
 * scrollwork, diamond tabs, lozenge buttons, parchment dropdowns, ornate
 * toggles and vertical diamond sliders.
 *
 * <p>Everything is built from {@link UiBatch} triangles and quads, so the whole
 * interface still renders in a single draw call.</p>
 */
public final class GenshinKit {

    private GenshinKit() {
    }

    // ---------------------------------------------------------- ornaments

    /** A small four-pointed sparkle used as an accent. */
    public static void diamond(UiBatch b, float cx, float cy, float r,
                               float rr, float gg, float bb, float aa) {
        b.triangle(cx, cy - r, cx + r, cy, cx, cy + r, rr, gg, bb, aa);
        b.triangle(cx, cy - r, cx, cy + r, cx - r, cy, rr, gg, bb, aa);
    }

    /** A thin four-pointed star (concave), the recurring gold motif. */
    public static void star4(UiBatch b, float cx, float cy, float r, float waist,
                             float rr, float gg, float bb, float aa) {
        b.polygon(new float[]{
                cx, cy - r,
                cx + waist, cy - waist,
                cx + r, cy,
                cx + waist, cy + waist,
                cx, cy + r,
                cx - waist, cy + waist,
                cx - r, cy,
                cx - waist, cy - waist
        }, rr, gg, bb, aa);
    }

    /** Scrollwork squiggle, mirrored as needed, for title flourishes. */
    public static void flourish(UiBatch b, float x, float y, float w, float h, boolean flip,
                                float alpha) {
        float dir = flip ? -1f : 1f;
        float x0 = x;
        float[] g = Style.GOLD_LIGHT;
        b.line(x0, y, x0 + w * 0.62f * dir, y, 1.6f, g[0], g[1], g[2], alpha);
        float cx = x0 + w * 0.62f * dir;
        b.line(cx, y, cx + w * 0.10f * dir, y - h * 0.55f, 1.6f, g[0], g[1], g[2], alpha);
        b.line(cx + w * 0.10f * dir, y - h * 0.55f, cx + w * 0.20f * dir, y - h * 0.62f, 1.6f,
                g[0], g[1], g[2], alpha);
        star4(b, x0 + w * 1.02f * dir, y, 4.2f, 1.3f, g[0], g[1], g[2], alpha);
    }

    /** Corner scroll: an L of thin lines with a diamond and a curl. */
    public static void corner(UiBatch b, float x, float y, float size, int quadrant,
                              float alpha) {
        float sx = (quadrant == 1 || quadrant == 2) ? -1f : 1f;
        float sy = (quadrant >= 2) ? -1f : 1f;
        float[] g = Style.GOLD_LIGHT;
        b.line(x, y, x + size * sx, y, 1.5f, g[0], g[1], g[2], alpha);
        b.line(x, y, x, y + size * sy, 1.5f, g[0], g[1], g[2], alpha);
        b.line(x + size * 0.28f * sx, y + size * 0.28f * sy,
                x + size * 0.62f * sx, y + size * 0.28f * sy, 1.2f, g[0], g[1], g[2], alpha * 0.7f);
        diamond(b, x + size * 0.22f * sx, y + size * 0.22f * sy, 3.4f,
                g[0], g[1], g[2], alpha);
    }

    /** Ornate double-ruled panel with corner scrollwork. */
    public static void panel(UiBatch b, float x, float y, float w, float h, float alpha) {
        // Body.
        float[] fill = Style.SLATE;
        b.quadGradient(x, y, w, h, fill[0], fill[1], fill[2], 0.90f * alpha,
                fill[0] * 0.82f, fill[1] * 0.82f, fill[2] * 0.86f, 0.94f * alpha);
        // Outer gold frame.
        frame(b, x, y, w, h, 0.85f * alpha);
        // Inner hairline, inset.
        innerFrame(b, x + 6f, y + 6f, w - 12f, h - 12f, 0.45f * alpha);
        // Corners.
        corner(b, x + 3f, y + 3f, Style.ORNAMENT, 0, 0.95f * alpha);
        corner(b, x + w - 3f, y + 3f, Style.ORNAMENT, 1, 0.95f * alpha);
        corner(b, x + 3f, y + h - 3f, Style.ORNAMENT, 2, 0.95f * alpha);
        corner(b, x + w - 3f, y + h - 3f, Style.ORNAMENT, 3, 0.95f * alpha);
    }

    /** Just the gold outline. */
    public static void frame(UiBatch b, float x, float y, float w, float h, float alpha) {
        float[] g = Style.GOLD;
        float t = Style.FRAME;
        b.quad(x, y, w, t, g[0], g[1], g[2], alpha);
        b.quad(x, y + h - t, w, t, g[0], g[1], g[2], alpha);
        b.quad(x, y, t, h, g[0], g[1], g[2], alpha);
        b.quad(x + w - t, y, t, h, g[0], g[1], g[2], alpha);
        // Bright inner highlight on the top edge catches the eye like engraving.
        b.quad(x + t, y + t, w - t * 2f, 1f, Style.GOLD_PALE[0], Style.GOLD_PALE[1],
                Style.GOLD_PALE[2], alpha * 0.55f);
    }

    public static void innerFrame(UiBatch b, float x, float y, float w, float h, float alpha) {
        float[] g = Style.GOLD_LIGHT;
        float t = Style.HAIRLINE;
        b.quad(x, y, w, t, g[0], g[1], g[2], alpha);
        b.quad(x, y + h - t, w, t, g[0], g[1], g[2], alpha);
        b.quad(x, y, t, h, g[0], g[1], g[2], alpha);
        b.quad(x + w - t, y, t, h, g[0], g[1], g[2], alpha);
    }

    /** Parchment plate with a gold edge, used for dropdowns and notes. */
    public static void parchment(UiBatch b, float x, float y, float w, float h, float alpha) {
        float[] p = Style.PARCHMENT;
        b.quadGradient(x, y, w, h, p[0], p[1], p[2], alpha,
                Style.PARCHMENT_DK[0], Style.PARCHMENT_DK[1], Style.PARCHMENT_DK[2], alpha);
        float[] g = Style.GOLD;
        float t = 2f;
        b.quad(x, y, w, t, g[0], g[1], g[2], alpha);
        b.quad(x, y + h - t, w, t, g[0], g[1], g[2], alpha);
        b.quad(x, y, t, h, g[0], g[1], g[2], alpha);
        b.quad(x + w - t, y, t, h, g[0], g[1], g[2], alpha);
    }

    // -------------------------------------------------------------- buttons

    /**
     * Lozenge button: elongated hexagon with a double gold border and a dark
     * translucent fill that brightens when pressed.
     */
    public static void lozenge(UiBatch b, float cx, float cy, float w, float h,
                               boolean primary, float press, float alpha) {
        float nose = h * 0.5f;
        float[] shape = {
                cx - w * 0.5f + nose, cy - h * 0.5f,
                cx + w * 0.5f - nose, cy - h * 0.5f,
                cx + w * 0.5f, cy,
                cx + w * 0.5f - nose, cy + h * 0.5f,
                cx - w * 0.5f + nose, cy + h * 0.5f,
                cx - w * 0.5f, cy
        };
        if (primary) {
            float[] g = Style.GOLD;
            b.polygon(shape, g[0] * 0.30f, g[1] * 0.26f, g[2] * 0.10f, 0.92f * alpha);
            // Gold glow behind.
            b.glow(cx, cy, w * 0.62f, 24, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                    Style.GOLD_LIGHT[2], (0.12f + press * 0.16f) * alpha);
        } else {
            b.polygon(shape, 0.05f, 0.06f, 0.09f, (0.72f + press * 0.18f) * alpha);
        }
        // Double border.
        float[] g = Style.GOLD_LIGHT;
        strokePolygon(b, shape, 2.2f, g[0], g[1], g[2], alpha);
        float[] inner = inset(shape, cx, cy, 4.5f);
        strokePolygon(b, inner, 1.2f, g[0], g[1], g[2], alpha * 0.75f);
        // End diamonds.
        diamond(b, cx - w * 0.5f + nose * 0.5f, cy, 3.6f, g[0], g[1], g[2], alpha);
        diamond(b, cx + w * 0.5f - nose * 0.5f, cy, 3.6f, g[0], g[1], g[2], alpha);
    }

    private static void strokePolygon(UiBatch b, float[] poly, float t,
                                      float r, float g, float bl, float a) {
        int n = poly.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            b.line(poly[i * 2], poly[i * 2 + 1], poly[j * 2], poly[j * 2 + 1], t, r, g, bl, a);
        }
    }

    /** Moves each vertex toward the centre — used for the inner border. */
    private static float[] inset(float[] poly, float cx, float cy, float amount) {
        float[] out = new float[poly.length];
        for (int i = 0; i < poly.length; i += 2) {
            float dx = cx - poly[i];
            float dy = cy - poly[i + 1];
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-4f) {
                out[i] = poly[i];
                out[i + 1] = poly[i + 1];
                continue;
            }
            out[i] = poly[i] + dx / len * amount;
            out[i + 1] = poly[i + 1] + dy / len * amount;
        }
        return out;
    }

    /** Diamond tab, as used for SET 1..5. */
    public static void diamondTab(UiBatch b, float cx, float cy, float r,
                                  boolean active, float press, float alpha) {
        float[] shape = {cx, cy - r, cx + r, cy, cx, cy + r, cx - r, cy};
        if (active) {
            b.glow(cx, cy, r * 1.5f, 18, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                    Style.GOLD_LIGHT[2], 0.22f * alpha);
            b.polygon(shape, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                    0.95f * alpha);
            float[] inner = {cx, cy - r * 0.74f, cx + r * 0.74f, cy, cx, cy + r * 0.74f,
                    cx - r * 0.74f, cy};
            b.polygon(inner, Style.GOLD[0] * 0.9f, Style.GOLD[1] * 0.8f, Style.GOLD[2] * 0.5f,
                    0.35f * alpha);
        } else {
            b.polygon(shape, 0.06f, 0.07f, 0.10f, (0.75f + press * 0.15f) * alpha);
        }
        float[] g = active ? Style.GOLD : Style.GOLD_LIGHT;
        strokePolygon(b, shape, 1.8f, g[0], g[1], g[2], alpha);
        // Small filigree at the cardinal points of inactive tabs.
        if (!active) {
            diamond(b, cx, cy - r, 2.2f, g[0], g[1], g[2], alpha * 0.7f);
            diamond(b, cx, cy + r, 2.2f, g[0], g[1], g[2], alpha * 0.7f);
        }
    }

    /** Gold-ringed circular icon button (back / close). */
    public static void roundButton(UiBatch b, float cx, float cy, float r,
                                   float press, float alpha) {
        b.glow(cx, cy, r * 1.5f, 18, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], 0.12f * alpha);
        b.circle(cx, cy, r, 26, 0.05f, 0.06f, 0.09f, (0.80f + press * 0.15f) * alpha);
        b.ring(cx, cy, r, r - 2.0f, 26, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.ring(cx, cy, r - 4.5f, r - 5.5f, 26, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha * 0.6f);
        for (int i = 0; i < 4; i++) {
            float ang = (float) (i * Math.PI * 0.5 + Math.PI * 0.25);
            star4(b, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r,
                    3.0f, 1.0f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                    alpha * 0.9f);
        }
    }

    /** Chevron arrow. dir: -1 left, +1 right, 0 up, 2 down. */
    public static void chevron(UiBatch b, float cx, float cy, float size, int dir,
                               float r, float g, float bl, float a) {
        if (dir == -1) {
            b.line(cx + size * 0.4f, cy - size * 0.6f, cx - size * 0.4f, cy, 2.2f, r, g, bl, a);
            b.line(cx - size * 0.4f, cy, cx + size * 0.4f, cy + size * 0.6f, 2.2f, r, g, bl, a);
        } else if (dir == 1) {
            b.line(cx - size * 0.4f, cy - size * 0.6f, cx + size * 0.4f, cy, 2.2f, r, g, bl, a);
            b.line(cx + size * 0.4f, cy, cx - size * 0.4f, cy + size * 0.6f, 2.2f, r, g, bl, a);
        } else if (dir == 2) {
            b.line(cx - size * 0.6f, cy - size * 0.3f, cx, cy + size * 0.35f, 2.2f, r, g, bl, a);
            b.line(cx, cy + size * 0.35f, cx + size * 0.6f, cy - size * 0.3f, 2.2f, r, g, bl, a);
        } else {
            b.line(cx - size * 0.6f, cy + size * 0.3f, cx, cy - size * 0.35f, 2.2f, r, g, bl, a);
            b.line(cx, cy - size * 0.35f, cx + size * 0.6f, cy + size * 0.3f, 2.2f, r, g, bl, a);
        }
    }

    // ------------------------------------------------------------- controls

    /** Ornate switch: a gold lozenge track with a jewelled thumb. */
    public static void toggle(UiBatch b, float x, float y, float w, float h,
                              boolean on, float anim, float alpha) {
        float r = h * 0.5f;
        float[] track = {
                x + r, y, x + w - r, y, x + w, y + r,
                x + w - r, y + h, x + r, y + h, x, y + r
        };
        float t = anim;
        float fr = 0.06f + (Style.TEAL[0] - 0.06f) * t;
        float fg = 0.07f + (Style.TEAL[1] - 0.07f) * t;
        float fb = 0.10f + (Style.TEAL[2] - 0.10f) * t;
        b.polygon(track, fr, fg, fb, 0.92f * alpha);
        if (on) {
            b.glow(x + w * 0.72f, y + r, h * 1.6f, 16, Style.TEAL[0], Style.TEAL[1],
                    Style.TEAL[2], 0.22f * alpha * t);
        }
        strokePolygon(b, track, 1.8f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
        // Thumb.
        float thumbX = x + r + (w - h) * t;
        b.circle(thumbX, y + r, r - 1.5f, 18, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
        b.circle(thumbX, y + r, r - 4f, 16, 0.85f, 0.72f, 0.38f, alpha * 0.9f);
        star4(b, thumbX, y + r, r * 0.52f, r * 0.18f, 1f, 0.97f, 0.86f, alpha * 0.95f);
    }

    /** Vertical square slider with a diamond thumb. */
    public static void vslider(UiBatch b, float cx, float top, float bottom,
                               float value, float alpha) {
        float h = bottom - top;
        float[] g = Style.GOLD;
        // Outer rails.
        b.quad(cx - 13f, top, 2.2f, h, g[0], g[1], g[2], alpha * 0.95f);
        b.quad(cx + 10.8f, top, 2.2f, h, g[0], g[1], g[2], alpha * 0.95f);
        // End caps.
        diamond(b, cx - 11.9f, top, 5.0f, g[0], g[1], g[2], alpha);
        diamond(b, cx + 11.9f, top, 5.0f, g[0], g[1], g[2], alpha);
        diamond(b, cx - 11.9f, bottom, 5.0f, g[0], g[1], g[2], alpha);
        diamond(b, cx + 11.9f, bottom, 5.0f, g[0], g[1], g[2], alpha);
        // Track channel.
        b.quad(cx - 5.5f, top, 11f, h, 0.04f, 0.05f, 0.08f, alpha * 0.85f);
        // Filled portion + glow.
        float thumbY = bottom - h * value;
        b.quad(cx - 5.5f, thumbY, 11f, bottom - thumbY,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha * 0.85f);
        b.glow(cx, thumbY, 46f, 20, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], 0.28f * alpha);
        // Diamond thumb.
        float r = 17f;
        b.polygon(new float[]{cx, thumbY - r, cx + r, thumbY, cx, thumbY + r, cx - r, thumbY},
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
        b.polygon(new float[]{cx, thumbY - r * 0.66f, cx + r * 0.66f, thumbY,
                        cx, thumbY + r * 0.66f, cx - r * 0.66f, thumbY},
                0.72f, 0.58f, 0.22f, alpha * 0.85f);
        star4(b, cx, thumbY, 7.5f, 2.6f, 1f, 0.96f, 0.84f, alpha);
    }

    // ---------------------------------------------------------------- misc

    /** Thin gold separator with a centred diamond. */
    public static void divider(UiBatch b, float x0, float x1, float y, float alpha) {
        b.line(x0, y, x1, y, 1.2f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.65f);
        diamond(b, (x0 + x1) * 0.5f, y, 3.0f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
    }

    /** Section title: gold diamond + letterspaced small caps + rule. */
    public static void sectionHeader(UiBatch b, String text, float x, float y,
                                     AtlasFont font, float alpha) {
        diamond(b, x + 3f, y + font.lineHeight(1) * 0.5f, 3.6f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
        b.textSpaced(text, x + 14f, y, 1, 1.4f, Style.IVORY[0], Style.IVORY[1], Style.IVORY[2],
                alpha, font);
    }

    /** Big centred screen title with mirrored flourishes. */
    public static void title(UiBatch b, String text, float cx, float y,
                             AtlasFont font, float alpha) {
        int scale = Style.TITLE;
        float tracking = 4f;
        // Shrink the heading rather than letting it run off the screen, then
        // rule and flourishes are sized from the final width.
        while (scale > 1 && textSpacedTextWidth(text, font, scale, tracking) > b.virtualWidth * 0.62f) {
            scale--;
        }
        float w = textSpacedTextWidth(text, font, scale, tracking);
        // Glow goes down first so it haloes the letters instead of veiling them.
        b.glow(cx, y + font.lineHeight(scale) * 0.5f, w * 0.56f, 26,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], 0.13f * alpha);
        float fy = y + font.lineHeight(scale) * 0.62f;
        b.quad(cx - w * 0.5f, fy, w, 1.2f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.75f);
        diamond(b, cx, fy + 0.6f, 3.2f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
        flourish(b, cx - w * 0.5f - 58f, fy + 0.6f, 48f, 9f, true, alpha * 0.9f);
        flourish(b, cx + w * 0.5f + 10f, fy + 0.6f, 48f, 9f, true, alpha * 0.9f);
        b.textSpacedCentred(text, cx, y, scale, tracking,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);
    }

    /** Width of text drawn by {@code UiBatch.textSpaced}, for layout maths. */
    public static float textSpacedTextWidth(String text, AtlasFont font, int scale,
                                            float tracking) {
        return UiBatch.textSpacedWidth(text, font, scale, tracking);
    }

    /** Gold coin glyph. */
    public static void coin(UiBatch b, float cx, float cy, float r, float alpha) {
        b.circle(cx, cy, r, 14, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                alpha);
        b.circle(cx, cy, r * 0.72f, 12, 0.72f, 0.56f, 0.20f, alpha);
        star4(b, cx, cy, r * 0.45f, r * 0.16f, 1f, 0.96f, 0.82f, alpha);
    }

    /** Padlock glyph for locked items. */
    public static void padlock(UiBatch b, float cx, float cy, float s, float alpha) {
        b.ring(cx, cy - s * 0.28f, s * 0.44f, s * 0.30f, 12,
                Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha);
        b.quad(cx - s * 0.52f, cy - s * 0.10f, s * 1.04f, s * 0.78f,
                Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha);
    }

    /** Check mark. */
    public static void check(UiBatch b, float cx, float cy, float s,
                             float r, float g, float bl, float a) {
        b.line(cx - s * 0.42f, cy + s * 0.02f, cx - s * 0.08f, cy + s * 0.36f, 3.0f, r, g, bl, a);
        b.line(cx - s * 0.08f, cy + s * 0.36f, cx + s * 0.46f, cy - s * 0.38f, 3.0f, r, g, bl, a);
    }
}
