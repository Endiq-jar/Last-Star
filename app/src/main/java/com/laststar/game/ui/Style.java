package com.laststar.game.ui;

/**
 * The menu design system: a high-fantasy "engraved gold on midnight" language
 * with painterly backdrops, ivory lettering and filigree framing.
 *
 * <p>Every colour, spacing and alpha the menus use lives here so the whole
 * interface can be retuned in one place.</p>
 */
public final class Style {

    private Style() {
    }

    // ------------------------------------------------------------- palette

    /** Ivory lettering for headings and primary labels. */
    public static final float[] IVORY = {0.949f, 0.906f, 0.816f};
    /** Slightly dimmed cream for body copy. */
    public static final float[] CREAM = {0.855f, 0.804f, 0.690f};
    /** Muted cream for secondary text. */
    public static final float[] CREAM_DIM = {0.639f, 0.600f, 0.518f};
    /** Deep warm gold. */
    public static final float[] GOLD = {0.788f, 0.635f, 0.153f};
    /** Bright gold highlight for edges and glows. */
    public static final float[] GOLD_LIGHT = {0.910f, 0.784f, 0.478f};
    /** Pale gold for thin inner lines. */
    public static final float[] GOLD_PALE = {0.965f, 0.878f, 0.659f};
    /** Midnight panel fill. */
    public static final float[] SLATE = {0.106f, 0.122f, 0.165f};
    /** Slightly lifted panel. */
    public static final float[] SLATE_HI = {0.149f, 0.169f, 0.220f};
    /** Parchment for cards and dropdowns. */
    public static final float[] PARCHMENT = {0.910f, 0.851f, 0.710f};
    /** Darker parchment shading. */
    public static final float[] PARCHMENT_DK = {0.804f, 0.729f, 0.573f};
    /** Ink used on parchment. */
    public static final float[] INK = {0.165f, 0.129f, 0.094f};
    /** Teal accent for "on"/active states. */
    public static final float[] TEAL = {0.435f, 0.835f, 0.784f};
    /** Violet used sparingly in shadows. */
    public static final float[] VIOLET = {0.486f, 0.416f, 0.682f};

    // ------------------------------------------------------------- metrics

    /** Standard panel corner ornament size. */
    public static final float ORNAMENT = 22f;
    /** Hairline width for gold rules. */
    public static final float HAIRLINE = 1.4f;
    /** Frame line width. */
    public static final float FRAME = 2.2f;

    // --------------------------------------------------------- text scales

    /** Display size for screen titles ("SETTINGS"). */
    public static final int TITLE = 3;
    /** Section heading. */
    public static final int HEADING = 2;
    /** Normal label. */
    public static final int LABEL = 1;
    /** Small caps / captions. */
    public static final int CAPTION = 1;

    public static float a(float[] c) {
        return c == null ? 1f : 1f;
    }
}
