package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.GenshinKit;
import com.laststar.game.ui.Style;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;

/**
 * Wardrobe page matching the chosen design: a category rail on the left, the
 * character on a lit dais, an item tray below and a framed swatch grid on the
 * right with hair/lips/face chips and an EQUIP lozenge.
 */
public final class WardrobeScreen implements Screen {

    public static final String[] CATEGORIES = {"BODIES", "COSMETICS", "HAIR", "LIPS", "FACE"};

    /** 4x4 grid: skin tones then eye colours, as in the design. */
    private static final float[][] SWATCHES = {
            {0.949f, 0.898f, 0.831f}, {0.953f, 0.851f, 0.776f}, {0.937f, 0.784f, 0.667f},
            {0.910f, 0.729f, 0.573f},
            {0.749f, 0.545f, 0.373f}, {0.667f, 0.443f, 0.290f}, {0.596f, 0.365f, 0.235f},
            {0.541f, 0.318f, 0.180f},
            {0.478f, 0.239f, 0.157f}, {0.400f, 0.196f, 0.145f}, {0.298f, 0.169f, 0.129f},
            {0.227f, 0.137f, 0.106f},
            {0.235f, 0.180f, 0.145f}, {0.435f, 0.835f, 0.784f}, {0.180f, 0.639f, 0.667f},
            {0.353f, 0.545f, 0.851f}
    };

    public int activeCategory;
    public int selectedSwatch = 3;
    public int selectedBody;
    private final float[] catPress = new float[CATEGORIES.length];
    private final float[] bodyPress = new float[4];
    private final float[] chipPress = new float[5];
    private float equipPress;
    /** Which chip is selected; cosmetic only, the swatch panel is shared. */
    private int activeChip;
    private float time;

    @Override
    public void update(float dt) {
        time += dt;
        decay(catPress, dt);
        decay(bodyPress, dt);
        decay(chipPress, dt);
        if (equipPress > 0f) {
            equipPress = Math.max(0f, equipPress - dt * 3f);
        }
    }

    private static void decay(float[] arr, float dt) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] > 0f) {
                arr[i] = Math.max(0f, arr[i] - dt * 3.5f);
            }
        }
    }

    @Override
    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        // ---- category rail ----
        float railX = 18f;
        float railY = h * 0.30f;
        for (int i = 0; i < CATEGORIES.length; i++) {
            float y = railY + i * 26f;
            boolean active = i == activeCategory;
            float p = catPress[i];
            if (active) {
                // Translucent gold band with a bright left edge.
                b.quadGradient(railX - 8f, y - 9f, w * 0.22f, 22f,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], 0.16f * alpha,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], 0.0f);
                b.quad(railX - 8f, y - 9f, 2.4f, 22f,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
            }
            GenshinKit.diamond(b, railX + 2f, y + 2f, 4.2f,
                    Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                    active ? alpha : alpha * 0.6f);
            b.textSpaced(CATEGORIES[i], railX + 14f, y - font.capHeight(2) * 0.5f + 2f, 2, 1.6f,
                    active ? Style.IVORY[0] : Style.CREAM_DIM[0],
                    active ? Style.IVORY[1] : Style.CREAM_DIM[1],
                    active ? Style.IVORY[2] : Style.CREAM_DIM[2],
                    (active ? alpha : alpha * 0.85f) + p * 0.1f, font);
        }

        // ---- character silhouette on the dais ----
        float cx = w * 0.395f;
        float feetY = h * 0.645f;
        float bodyH = h * 0.40f;

        // Dais first, then the character standing on it.
        drawDais(b, cx, feetY, alpha);
        drawHero(b, cx, feetY, bodyH, alpha);

        // ---- item tray ----
        float trayY = h - 96f;
        float tile = 44f;
        float gap = 8f;
        float startX = 22f;
        for (int i = 0; i < 4; i++) {
            float x = startX + i * (tile + gap);
            boolean selected = i == selectedBody;
            float p = bodyPress[i];
            if (selected) {
                b.glow(x + tile * 0.5f, trayY + tile * 0.5f, tile * 0.95f, 18,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        0.22f * alpha);
            }
            // Parchment tile with a silhouette.
            GenshinKit.parchment(b, x, trayY, tile, tile, (0.85f + p * 0.1f) * alpha);
            float[] ink = Style.PARCHMENT_DK;
            float s = tile * 0.30f;
            float bx = x + tile * 0.5f;
            float by = trayY + tile * 0.42f;
            b.circle(bx, by - s * 0.9f, s * 0.40f, 12, ink[0], ink[1], ink[2], alpha * 0.85f);
            b.polygon(new float[]{
                    bx - s * 0.62f, by + s * 1.1f,
                    bx + s * 0.62f, by + s * 1.1f,
                    bx + s * 0.40f, by - s * 0.42f,
                    bx - s * 0.40f, by - s * 0.42f
            }, ink[0], ink[1], ink[2], alpha * 0.85f);
            // Star pips.
            for (int k = 0; k < 2; k++) {
                GenshinKit.star4(b, x + tile - 8f - k * 8f, trayY + 7f, 3.4f, 1.2f,
                        Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
            }
            if (selected) {
                float[] g = Style.GOLD_LIGHT;
                b.quad(x, trayY, tile, 2.4f, g[0], g[1], g[2], alpha);
                b.quad(x, trayY + tile - 2.4f, tile, 2.4f, g[0], g[1], g[2], alpha);
                b.quad(x, trayY, 2.4f, tile, g[0], g[1], g[2], alpha);
                b.quad(x + tile - 2.4f, trayY, 2.4f, tile, g[0], g[1], g[2], alpha);
                // Check badge.
                b.circle(x + tile - 6f, trayY + tile - 6f, 7.2f, 12, g[0], g[1], g[2], alpha);
                GenshinKit.check(b, x + tile - 6f, trayY + tile - 6f, 8f,
                        Style.INK[0], Style.INK[1], Style.INK[2], alpha);
            }
        }

        // ---- swatch panel ----
        float px = w * PANEL_X;
        float py = h * PANEL_Y;
        float pw = w * PANEL_W;
        float ph = h * PANEL_H;
        GenshinKit.panel(b, px, py, pw, ph, alpha);

        float gridPad = GRID_PAD;
        float cellW = (pw - gridPad * 2f) / 4f;
        float gridTop = py + GRID_TOP;
        for (int i = 0; i < SWATCHES.length; i++) {
            int col = i % 4;
            int row = i / 4;
            float scx = px + gridPad + cellW * (col + 0.5f);
            float scy = gridTop + cellW * (row + 0.5f);
            float r = cellW * 0.38f;
            boolean sel = i == selectedSwatch;
            if (sel) {
                b.glow(scx, scy, r * 2.0f, 16, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                        Style.GOLD_LIGHT[2], 0.25f * alpha);
            }
            b.circle(scx, scy, r, 18, SWATCHES[i][0], SWATCHES[i][1], SWATCHES[i][2], alpha);
            if (sel) {
                b.ring(scx, scy, r + 2.5f, r + 0.8f, 20, Style.GOLD_LIGHT[0],
                        Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
                GenshinKit.check(b, scx, scy, r * 1.1f,
                        Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.85f);
            } else {
                b.ring(scx, scy, r, r - 1.4f, 18, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2],
                        alpha * 0.55f);
            }
        }

        // Header chips. These used to be lozenges, but a four-letter word in a
        // pointed lozenge loses its first and last glyph at any width that fits
        // four chips across the panel. Text on a hairline gold rule reads better
        // and needs no padding: the marker and rule are drawn first, then the
        // label sits on top of them.
        String[] chips = {"HAIR", "LIPS", "FACE"};
        for (int i = 0; i < chips.length; i++) {
            drawChip(b, font, chips[i], px, pw, py, i, alpha);
        }
        drawLockChip(b, px, pw, py, alpha);

        // ---- equip ----
        float ex = px + pw * 0.5f;
        float ey = py + ph + 22f;
        GenshinKit.lozenge(b, ex, ey, 112f, 27f, true, equipPress, alpha);
        b.textSpacedCentred("EQUIP", ex, ey - font.capHeight(1) * 0.5f, 1, 2.6f,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);
    }

    /** Chip geometry, shared by drawing and hit-testing so taps land on the label. */
    private static float chipCentreX(float px, float pw, int index) {
        return px + chipRowLeft(pw) + index * CHIP_PITCH + CHIP_W * 0.5f;
    }

    /** Left edge of the chip row, relative to the panel's left edge. */
    private static float chipRowLeft(float pw) {
        return (pw - CHIP_ROW_W) * 0.5f;
    }

    // Panel geometry. Draw and hit-test both read these, so a tap target can
    // never drift away from the thing it looks like it belongs to.
    private static final float PANEL_X = 0.545f;
    private static final float PANEL_Y = 0.180f;
    private static final float PANEL_W = 0.285f;
    private static final float PANEL_H = 0.565f;
    private static final float GRID_PAD = 12f;
    /** Below the chip row, which owns the top strip of the panel. */
    private static final float GRID_TOP = 50f;
    /** Label top and rule line, measured down from the panel's top edge. */
    private static final float CHIP_LABEL_Y = 14f;
    private static final float CHIP_RULE_Y = 34f;
    private static final float CHIP_HIT_Y = 24f;
    private static final float CHIP_HIT_HALF = 18f;

    private static final float CHIP_W = 38f;
    private static final float CHIP_PITCH = 47f;
    private static final float CHIP_ROW_W = CHIP_PITCH * 4f;
    private static final float PADLOCK_W = 22f;

    /**
     * One chip: a hairline gold rule with a diamond marker at its centre and the
     * label sitting just above the rule. The selected chip's label is ivory and
     * its marker is filled; the others are dimmed cream.
     */
    private void drawChip(UiBatch b, AtlasFont font, String label, float px, float pw,
                          float py, int index, float alpha) {
        boolean sel = index == activeChip;
        float cx = chipCentreX(px, pw, index);
        float ruleY = py + CHIP_RULE_Y;
        float press = chipPress[index];
        float glow = sel ? 1f : 0.35f + press * 0.4f;

        b.quad(cx - CHIP_W * 0.5f, ruleY, CHIP_W, 0.8f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * (0.35f + 0.5f * glow));
        if (sel) {
            // A short brighter underline under the active chip.
            b.quad(cx - CHIP_W * 0.34f, ruleY - 0.9f, CHIP_W * 0.68f, 2.0f,
                    Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
            b.glow(cx, ruleY + 2f, CHIP_W * 0.42f, 14,
                    Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], 0.16f * alpha);
        }

        // Diamond marker sitting on the rule, filled for the active chip.
        float d = sel ? 3.8f : 2.7f;
        b.polygon(new float[]{
                cx, ruleY - d,
                cx + d, ruleY,
                cx, ruleY + d,
                cx - d, ruleY
        }, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha * glow);

        float[] ink = sel ? Style.IVORY : Style.CREAM_DIM;
        b.textSpacedCentred(label, cx, py + CHIP_LABEL_Y, 1, 0f,
                ink[0], ink[1], ink[2], alpha * (sel ? 1f : 0.85f), font);
    }

    /** The locked fourth slot: a hatched padlock chip with no label. */
    private void drawLockChip(UiBatch b, float px, float pw, float py, float alpha) {
        float cx = chipCentreX(px, pw, 3);
        float ruleY = py + CHIP_RULE_Y;
        b.quad(cx - PADLOCK_W * 0.5f, ruleY, PADLOCK_W, 0.8f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.3f);
        GenshinKit.padlock(b, cx, py + CHIP_LABEL_Y + 10f, 7.5f, alpha * 0.5f);
    }

    /** The lit ring the character stands on. */
    private void drawDais(UiBatch b, float cx, float feetY, float alpha) {
        float rx = 56f;
        float ry = 14f;
        float cy = feetY + 2f;
        b.glow(cx, cy, rx * 1.7f, 26, 1f, 0.86f, 0.55f, 0.14f * alpha);
        b.ellipse(cx, cy, rx, ry, 30, 0.09f, 0.10f, 0.13f, alpha * 0.9f);
        b.ellipse(cx, cy, rx * 0.92f, ry * 0.92f, 30, 0.05f, 0.06f, 0.08f, alpha * 0.5f);
        b.ringEllipse(cx, cy, rx, ry, 0.93f, 30,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.85f);
        b.ringEllipse(cx, cy, rx * 0.82f, ry * 0.82f, 0.96f, 30,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha * 0.40f);
        for (int i = 0; i < 8; i++) {
            float ang = (float) (i * Math.PI / 4.0 + time * 0.12f);
            GenshinKit.star4(b, cx + (float) Math.cos(ang) * rx * 0.87f,
                    cy + (float) Math.sin(ang) * ry * 0.87f,
                    3.0f, 1.0f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                    Style.GOLD_LIGHT[2], alpha * 0.8f);
        }
    }

    /** Stylised hero: head, coat, cape, boots — no external art. */
    private void drawHero(UiBatch b, float cx, float feetY, float hgt, float alpha) {
        float headR = hgt * 0.072f;
        float headY = feetY - hgt + headR;
        // Cape behind.
        b.polygon(new float[]{
                cx - hgt * 0.10f, headY + hgt * 0.10f,
                cx - hgt * 0.26f, feetY - hgt * 0.18f,
                cx - hgt * 0.20f, feetY - hgt * 0.02f,
                cx + hgt * 0.02f, feetY - hgt * 0.06f
        }, 0.16f, 0.45f, 0.47f, alpha * 0.95f);
        b.polygon(new float[]{
                cx + hgt * 0.08f, headY + hgt * 0.10f,
                cx + hgt * 0.24f, feetY - hgt * 0.16f,
                cx + hgt * 0.18f, feetY - hgt * 0.01f,
                cx + hgt * 0.00f, feetY - hgt * 0.06f
        }, 0.12f, 0.38f, 0.42f, alpha * 0.95f);

        // Legs.
        b.polygon(new float[]{
                cx - hgt * 0.075f, feetY - hgt * 0.42f,
                cx - hgt * 0.010f, feetY - hgt * 0.42f,
                cx - hgt * 0.014f, feetY,
                cx - hgt * 0.072f, feetY
        }, 0.13f, 0.13f, 0.16f, alpha);
        b.polygon(new float[]{
                cx + hgt * 0.010f, feetY - hgt * 0.42f,
                cx + hgt * 0.075f, feetY - hgt * 0.42f,
                cx + hgt * 0.072f, feetY,
                cx + hgt * 0.014f, feetY
        }, 0.11f, 0.11f, 0.14f, alpha);
        // Boots.
        b.quad(cx - hgt * 0.086f, feetY - hgt * 0.10f, hgt * 0.075f, hgt * 0.10f,
                0.28f, 0.22f, 0.14f, alpha);
        b.quad(cx + hgt * 0.011f, feetY - hgt * 0.10f, hgt * 0.075f, hgt * 0.10f,
                0.28f, 0.22f, 0.14f, alpha);
        // Coat.
        b.polygon(new float[]{
                cx - hgt * 0.105f, headY + hgt * 0.13f,
                cx + hgt * 0.105f, headY + hgt * 0.13f,
                cx + hgt * 0.115f, feetY - hgt * 0.36f,
                cx, feetY - hgt * 0.28f,
                cx - hgt * 0.115f, feetY - hgt * 0.36f
        }, 0.16f, 0.44f, 0.47f, alpha);
        // Gold trim down the centre and at the collar.
        b.quad(cx - hgt * 0.006f, headY + hgt * 0.14f, hgt * 0.012f, hgt * 0.42f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha * 0.85f);
        b.line(cx - hgt * 0.095f, headY + hgt * 0.155f, cx + hgt * 0.095f,
                headY + hgt * 0.155f, 2.2f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha * 0.8f);
        // Arms.
        b.quad(cx - hgt * 0.145f, headY + hgt * 0.15f, hgt * 0.042f, hgt * 0.28f,
                0.13f, 0.37f, 0.40f, alpha);
        b.quad(cx + hgt * 0.103f, headY + hgt * 0.15f, hgt * 0.042f, hgt * 0.28f,
                0.13f, 0.37f, 0.40f, alpha);
        // Head + hair.
        b.circle(cx, headY, headR, 18, SWATCHES[selectedSwatch][0] * 0.92f,
                SWATCHES[selectedSwatch][1] * 0.92f, SWATCHES[selectedSwatch][2] * 0.92f, alpha);
        b.polygon(new float[]{
                cx - headR * 1.05f, headY - headR * 0.15f,
                cx - headR * 0.65f, headY - headR * 1.15f,
                cx + headR * 0.75f, headY - headR * 1.10f,
                cx + headR * 1.05f, headY - headR * 0.10f,
                cx + headR * 0.30f, headY - headR * 0.55f,
                cx - headR * 0.40f, headY - headR * 0.50f
        }, 0.14f, 0.16f, 0.19f, alpha);
        // Rim light.
        b.line(cx - hgt * 0.118f, headY + hgt * 0.16f, cx - hgt * 0.118f,
                feetY - hgt * 0.36f, 1.6f, 0.75f, 0.88f, 0.92f, alpha * 0.35f);
    }

    @Override
    public void drawTitle(UiBatch b, AtlasFont font, float alpha) {
        GenshinKit.title(b, "WARDROBE", b.virtualWidth * 0.40f, 8f, font, alpha);
    }

    @Override
    public Action hit(float x, float y, float w, float h, TabBar tabBar) {
        // Categories.
        float railY = h * 0.30f;
        for (int i = 0; i < CATEGORIES.length; i++) {
            float cy = railY + i * 26f + 2f;
            if (y > cy - 13f && y < cy + 13f && x < w * 0.30f) {
                activeCategory = i;
                catPress[i] = 1f;
                return Action.NONE;
            }
        }
        // Body tiles.
        float trayY = h - 96f;
        float tile = 44f;
        float gap = 8f;
        for (int i = 0; i < 4; i++) {
            float tx = 22f + i * (tile + gap);
            if (x >= tx && x <= tx + tile && y >= trayY && y <= trayY + tile) {
                selectedBody = i;
                bodyPress[i] = 1f;
                return Action.NONE;
            }
        }
        // Swatches.
        float px = w * PANEL_X;
        float py = h * PANEL_Y;
        float pw = w * PANEL_W;
        float ph = h * PANEL_H;
        float gridPad = GRID_PAD;
        float cellW = (pw - gridPad * 2f) / 4f;
        float gridTop = py + GRID_TOP;
        for (int i = 0; i < SWATCHES.length; i++) {
            int col = i % 4;
            int row = i / 4;
            float scx = px + gridPad + cellW * (col + 0.5f);
            float scy = gridTop + cellW * (row + 0.5f);
            if (Math.abs(x - scx) < cellW * 0.5f && Math.abs(y - scy) < cellW * 0.5f) {
                selectedSwatch = i;
                return Action.NONE;
            }
        }
        // Chips: same geometry as drawChip, so the tap target is the label.
        for (int i = 0; i < 3; i++) {
            float chx = chipCentreX(px, pw, i);
            if (Math.abs(x - chx) < CHIP_PITCH * 0.5f
                    && Math.abs(y - (py + CHIP_HIT_Y)) < CHIP_HIT_HALF) {
                chipPress[i] = 1f;
                activeChip = i;
                return Action.NONE;
            }
        }
        // Equip.
        float ex = px + pw * 0.5f;
        float ey = py + ph + 22f;
        if (Math.abs(x - ex) < 58f && Math.abs(y - ey) < 18f) {
            equipPress = 1f;
            return Action.NONE;
        }
        return Action.NONE;
    }
}
