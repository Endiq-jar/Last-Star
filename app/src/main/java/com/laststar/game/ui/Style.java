package com.laststar.game.ui;

/**
 * Central visual tokens for Last Star. The indigo-and-orchid palette comes
 * from the project's RPG UI direction; warm gold is reserved for reward/value
 * cues and all colors are shared by HUD and menu screens.
 */
public final class Style {

    private Style() {
    }

    // Exact brand palette from the UI brief, stored as linear 0..1 RGB values.
    /** #1E1E76 — primary deep indigo. */
    public static final float[] SLATE = {30f / 255f, 30f / 255f, 118f / 255f};
    /** #4B4BC3 — lifted indigo panels and selected states. */
    public static final float[] SLATE_HI = {75f / 255f, 75f / 255f, 195f / 255f};
    /** #707FF5 — active controls and focus. */
    public static final float[] TEAL = {112f / 255f, 127f / 255f, 245f / 255f};
    /** #A195F9 — secondary magic accent. */
    public static final float[] VIOLET = {161f / 255f, 149f / 255f, 249f / 255f};
    /** #F2A1F2 — rare magic, bond and notification accent. */
    public static final float[] PINK = {242f / 255f, 161f / 255f, 242f / 255f};
    /** #F7B769 — rewards and legendary/value cues. */
    public static final float[] GOLD = {247f / 255f, 183f / 255f, 105f / 255f};
    /** Warm pale gold highlight for fine trim. */
    public static final float[] GOLD_LIGHT = {1.000f, 0.855f, 0.690f};
    /** Inner rim highlight. */
    public static final float[] GOLD_PALE = {1.000f, 0.925f, 0.824f};
    /** #F5F0FF — primary text. */
    public static final float[] IVORY = {245f / 255f, 240f / 255f, 1.000f};
    /** Secondary text, retaining contrast over indigo glass. */
    public static final float[] CREAM = {0.820f, 0.812f, 0.941f};
    /** #A195F9 — subdued secondary labels. */
    public static final float[] CREAM_DIM = {161f / 255f, 149f / 255f, 249f / 255f};
    /** Frosted indigo card surface. */
    public static final float[] PARCHMENT = {0.145f, 0.145f, 0.380f};
    /** Lower edge of card gradients. */
    public static final float[] PARCHMENT_DK = {0.090f, 0.090f, 0.275f};
    /** Text on card surfaces. */
    public static final float[] INK = IVORY;

    // ------------------------------------------------------------- metrics

    public static final float ORNAMENT = 22f;
    public static final float HAIRLINE = 1.4f;
    public static final float FRAME = 2.2f;
    public static final int TITLE = 3;
    public static final int HEADING = 2;
    public static final int LABEL = 1;
    public static final int CAPTION = 1;

    public static float a(float[] c) {
        return c == null ? 1f : 1f;
    }
}
