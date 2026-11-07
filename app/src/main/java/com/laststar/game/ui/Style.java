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
    public static final float[] IVORY = {0.945f, 0.953f, 1.000f};
    /** Cool, bright copy for the blue-violet glass panels. */
    public static final float[] CREAM = {0.805f, 0.843f, 0.949f};
    /** Muted periwinkle for secondary text. */
    public static final float[] CREAM_DIM = {0.541f, 0.612f, 0.780f};
    /** Warm gold for rewards and primary actions. */
    public static final float[] GOLD = {0.914f, 0.678f, 0.294f};
    /** Bright champagne-gold highlight. */
    public static final float[] GOLD_LIGHT = {1.000f, 0.827f, 0.482f};
    /** Pale gold for thin inner lines. */
    public static final float[] GOLD_PALE = {1.000f, 0.906f, 0.690f};
    /** Deep blue-violet glass panel. */
    public static final float[] SLATE = {0.035f, 0.047f, 0.125f};
    /** Lifted indigo panel for selected rows. */
    public static final float[] SLATE_HI = {0.110f, 0.137f, 0.282f};
    /** Frosted indigo surface used for cards and dropdowns. */
    public static final float[] PARCHMENT = {0.118f, 0.145f, 0.290f};
    /** Lower blue-violet edge of frosted surfaces. */
    public static final float[] PARCHMENT_DK = {0.071f, 0.090f, 0.208f};
    /** Cool white lettering on the blue glass cards. */
    public static final float[] INK = {0.902f, 0.929f, 1.000f};
    /** Cyan for active states, magic and navigation focus. */
    public static final float[] TEAL = {0.400f, 0.835f, 1.000f};
    /** Violet accent for magic and companion affinity. */
    public static final float[] VIOLET = {0.651f, 0.471f, 1.000f};

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
