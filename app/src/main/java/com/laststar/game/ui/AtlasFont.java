package com.laststar.game.ui;

/**
 * A font baked into a single-channel atlas.
 *
 * <p>Two implementations exist: {@link Font} (the original 5x7 pixel font used by
 * the in-game HUD) and {@link VectorFont} (a stroke-built Roman serif used by the
 * menus). Both expose the same contract so {@link UiRenderer} can upload either
 * one and {@link UiBatch} can lay text out against it.</p>
 *
 * <p>Metrics split in two: {@link #glyphWidth()} / {@link #glyphHeight()} /
 * {@link #advance()} are <em>design</em> pixels (the size one glyph occupies at
 * scale 1 in the 720x405 virtual space), while the UV rectangle returned by
 * {@link #uv(char)} addresses the glyph inside the atlas, whose resolution is
 * independent and usually higher.</p>
 */
public abstract class AtlasFont {

    /** Coverage values, one byte per pixel, row major. */
    public byte[] atlas;
    public int atlasWidth;
    public int atlasHeight;

    /** Design-space glyph width at scale 1. */
    public abstract int glyphWidth();

    /** Design-space glyph height (cap height) at scale 1. */
    public abstract int glyphHeight();

    /** Design-space distance between glyph origins at scale 1. */
    public abstract int advance();

    /** Atlas UV rect for a glyph: {u0, v0, du, dv}. */
    public abstract float[] uv(char c);

    /** Width of {@code s} drawn at {@code scale}. */
    public int textWidth(String s, int scale) {
        return s.length() * advance() * scale;
    }

    /** Height of one line at {@code scale}. */
    public int lineHeight(int scale) {
        return glyphHeight() * scale;
    }

    /** Cap height at {@code scale} — what vertical centring should use. */
    public int capHeight(int scale) {
        return glyphHeight() * scale;
    }

    /** True when the atlas should be sampled with bilinear filtering. */
    public boolean smooth() {
        return false;
    }

    /** True while the atlas values are just binary coverage. */
    public boolean coverageAtlas() {
        return true;
    }
}
