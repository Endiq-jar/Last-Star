package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.GenshinKit;
import com.laststar.game.ui.Style;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;

/**
 * Shop page: the merchant's alcove, a purse, and a grid of illuminated trinket
 * cards with gold prices. Everything is cosmetic in this build, so tapping a
 * card simply inspects it.
 */
public final class ShopScreen implements Screen {

    private static final String[] NAMES = {
            "EMBER", "LANTERN", "SHARD", "GLASS",
            "CLOAK", "SEAL", "COMPASS", "HORN"
    };
    private static final String[] KINDS = {"BLADE", "LIGHT", "RELIC", "TOKEN",
            "CLOAK", "SEAL", "TOOL", "HORN"};
    private static final int[] PRICES = {1200, 650, 2400, 180, 900, 340, 480, 260};
    private static final String[] DESCRIPTIONS = {
            "A BLADE THAT REMEMBERS THE SUN.",
            "BURNS LOW, BUT NEVER GOES OUT.",
            "A FRAGMENT THAT STILL HUMS.",
            "ROUGH GLASS FULL OF GREY LIGHT.",
            "STITCHED FROM STAR-SPACE.",
            "PRESSED WAX FROM THE LAST DOOR.",
            "POINTS AT WHAT YOU HAVE LOST.",
            "SOUNDS ONCE PER NIGHT. ONCE ONLY."
    };

    public int selected;
    private final float[] press = new float[NAMES.length];
    private float buyPress;
    private float time;

    @Override
    public void update(float dt) {
        time += dt;
        for (int i = 0; i < press.length; i++) {
            if (press[i] > 0f) {
                press[i] = Math.max(0f, press[i] - dt * 3.5f);
            }
        }
        if (buyPress > 0f) {
            buyPress = Math.max(0f, buyPress - dt * 3f);
        }
    }

    private float gridX(float w) {
        return w * 0.245f;
    }

    private float gridY(float h) {
        return h * 0.235f;
    }

    private float cardW(float w) {
        return (w * 0.585f - 3f * 8f) / 4f;
    }

    private float cardH(float h) {
        return h * 0.185f;
    }

    @Override
    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        // Purse plate.
        float purseX = w * 0.118f;
        float purseY = h * 0.255f;
        GenshinKit.panel(b, purseX - 66f, purseY - 16f, 132f, 64f, alpha);
        GenshinKit.coin(b, purseX - 36f, purseY + 14f, 12f, alpha);
        b.textSpacedRight("1840", purseX + 50f, purseY + 7f, 2, 1.2f,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);
        b.textSpacedCentred("PURSE", purseX, purseY + 32f, 1, 1.2f,
                Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha, font);

        // Merchant's motto, wrapped to the left column.
        b.textSpacedCentred("EVERYTHING", purseX, h * 0.405f, 1, 0.6f,
                Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha * 0.85f, font);
        b.textSpacedCentred("HAS A PRICE", purseX, h * 0.405f + 11f, 1, 0.6f,
                Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha * 0.85f, font);

        // Cards.
        float cw = cardW(w);
        float ch = cardH(h);
        for (int i = 0; i < NAMES.length; i++) {
            int col = i % 4;
            int row = i / 4;
            float x = gridX(w) + col * (cw + 8f);
            float y = gridY(h) + row * (ch + 10f);
            boolean sel = i == selected;
            float[] fill = sel ? Style.SLATE_HI : Style.SLATE;
            b.quadGradient(x, y, cw, ch, fill[0], fill[1], fill[2], 0.92f * alpha,
                    fill[0] * 0.8f, fill[1] * 0.8f, fill[2] * 0.85f, 0.95f * alpha);
            if (sel) {
                b.glow(x + cw * 0.5f, y + ch * 0.5f, cw * 0.7f, 18,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        0.16f * alpha);
            }
            GenshinKit.frame(b, x, y, cw, ch,
                    (sel ? 1f : 0.45f) * alpha * (1f + press[i] * 0.2f));
            for (int c = 0; c < 4; c++) {
                float ox = (c == 1 || c == 3) ? x + cw - 4f : x + 4f;
                float oy = (c >= 2) ? y + ch - 4f : y + 4f;
                GenshinKit.corner(b, ox, oy, 13f, c, alpha * (sel ? 0.95f : 0.55f));
            }

            // Item glyph above the name, both centred in the card.
            float cx2 = x + cw * 0.5f;
            itemGlyph(b, i, cx2, y + 22f, 12f, alpha);
            b.textSpacedCentred(NAMES[i], cx2, y + ch - 30f, 1, 0.8f,
                    Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);
            String price = String.valueOf(PRICES[i]);
            float priceW = UiBatch.textSpacedWidth(price, font, 1, 0.4f);
            float rowW = priceW + 14f;
            float priceX = cx2 - rowW * 0.5f + 14f;
            GenshinKit.coin(b, cx2 - rowW * 0.5f + 4f, y + ch - 13f, 5f, alpha);
            b.textSpaced(price, priceX, y + ch - 17f, 1, 0.4f,
                    Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha, font);
            if (i >= 6) {
                // Locked: veil the card and stamp a lock in the middle.
                b.quad(x + 2f, y + 2f, cw - 4f, ch - 4f, 0.02f, 0.03f, 0.05f, 0.52f * alpha);
                GenshinKit.padlock(b, cx2, y + 22f, 12f, alpha);
            }
        }

        // Detail strip.
        float dx = gridX(w);
        float dy = gridY(h) + 2f * (ch + 10f) + 8f;
        float dw = w * 0.585f;
        b.quadGradient(dx, dy, dw, 44f, Style.SLATE[0], Style.SLATE[1], Style.SLATE[2],
                0.86f * alpha, Style.SLATE[0] * 0.7f, Style.SLATE[1] * 0.7f,
                Style.SLATE[2] * 0.7f, 0.9f * alpha);
        GenshinKit.innerFrame(b, dx, dy, dw, 44f, alpha * 0.6f);
        String heading = NAMES[selected] + "  -  " + KINDS[selected];
        b.textSpaced(heading, dx + 14f, dy + 8f, 1, 1.0f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha, font);
        String desc = DESCRIPTIONS[selected];
        float room = dw - 168f;
        int fit = (int) (room / 5.6f);
        if (desc.length() > fit && fit > 4) {
            desc = desc.substring(0, fit - 2) + "..";
        }
        b.textSpaced(desc, dx + 14f, dy + 26f, 1, 0.3f,
                Style.CREAM[0], Style.CREAM[1], Style.CREAM[2], alpha * 0.95f, font);

        // Buy button.
        float bx = dx + dw - 66f;
        float by = dy + 22f;
        GenshinKit.lozenge(b, bx, by, 106f, 28f, true, buyPress, alpha);
        b.textSpacedCentred("PURCHASE", bx, by - font.capHeight(1) * 0.5f, 1, 1.4f,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);

        // Merchant figure on the right.
        merchant(b, w * 0.905f, h * 0.60f, h * 0.30f, alpha);
    }

    private void merchant(UiBatch b, float cx, float feetY, float hgt, float alpha) {
        b.glow(cx, feetY + 2f, hgt * 0.55f, 20, 1f, 0.80f, 0.46f, 0.16f * alpha);
        b.glow(cx + hgt * 0.34f, feetY - hgt * 0.52f, hgt * 0.30f, 16,
                1f, 0.84f, 0.48f, 0.22f * alpha);
        // Robe.
        b.polygon(new float[]{
                cx - hgt * 0.31f, feetY,
                cx + hgt * 0.31f, feetY,
                cx + hgt * 0.18f, feetY - hgt * 0.74f,
                cx - hgt * 0.18f, feetY - hgt * 0.74f
        }, 0.24f, 0.19f, 0.16f, alpha);
        // Sleeve reaching for the lantern.
        b.polygon(new float[]{
                cx + hgt * 0.10f, feetY - hgt * 0.66f,
                cx + hgt * 0.24f, feetY - hgt * 0.70f,
                cx + hgt * 0.34f, feetY - hgt * 0.56f,
                cx + hgt * 0.22f, feetY - hgt * 0.50f
        }, 0.20f, 0.16f, 0.15f, alpha);
        // Belt of coins.
        b.quad(cx - hgt * 0.14f, feetY - hgt * 0.44f, hgt * 0.28f, hgt * 0.05f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.75f);
        b.polygon(new float[]{
                cx - hgt * 0.18f, feetY - hgt * 0.70f,
                cx + hgt * 0.18f, feetY - hgt * 0.70f,
                cx + hgt * 0.22f, feetY - hgt * 0.52f,
                cx - hgt * 0.22f, feetY - hgt * 0.52f
        }, 0.42f, 0.32f, 0.16f, alpha);
        // Hood + face shadow.
        b.polygon(new float[]{
                cx - hgt * 0.19f, feetY - hgt * 0.72f,
                cx - hgt * 0.12f, feetY - hgt * 0.94f,
                cx + hgt * 0.12f, feetY - hgt * 0.94f,
                cx + hgt * 0.19f, feetY - hgt * 0.72f
        }, 0.20f, 0.16f, 0.14f, alpha);
        b.circle(cx, feetY - hgt * 0.80f, hgt * 0.075f, 14,
                0.55f, 0.44f, 0.34f, alpha);
        // Lantern held out.
        float lx = cx + hgt * 0.30f;
        float ly = feetY - hgt * 0.56f;
        b.line(cx + hgt * 0.16f, feetY - hgt * 0.62f, lx, ly, 2f,
                0.30f, 0.24f, 0.18f, alpha);
        b.glow(lx, ly, hgt * 0.22f, 18, 1f, 0.82f, 0.45f, 0.30f * alpha);
        b.quad(lx - hgt * 0.045f, ly - hgt * 0.05f, hgt * 0.09f, hgt * 0.10f,
                1f, 0.90f, 0.62f, alpha);
    }

    private void itemGlyph(UiBatch b, int i, float cx, float cy, float s, float alpha) {
        float[] g = Style.GOLD_LIGHT;
        b.circle(cx, cy, s * 1.15f, 16, 0.10f, 0.11f, 0.15f, alpha * 0.9f);
        b.ring(cx, cy, s * 1.15f, s * 1.05f, 16, g[0], g[1], g[2], alpha * 0.7f);
        switch (i % 8) {
            case 0: // blade
                b.line(cx, cy - s * 0.72f, cx, cy + s * 0.42f, 3.2f, 0.88f, 0.90f, 0.95f, alpha);
                b.line(cx - s * 0.34f, cy + s * 0.34f, cx + s * 0.34f, cy + s * 0.34f, 2.6f,
                        g[0], g[1], g[2], alpha);
                b.line(cx, cy + s * 0.40f, cx, cy + s * 0.72f, 3f,
                        0.35f, 0.26f, 0.18f, alpha);
                break;
            case 1: // lantern
                b.quad(cx - s * 0.34f, cy - s * 0.40f, s * 0.68f, s * 0.80f,
                        1f, 0.86f, 0.48f, alpha);
                b.line(cx - s * 0.40f, cy - s * 0.46f, cx + s * 0.40f, cy - s * 0.46f, 2.4f,
                        g[0], g[1], g[2], alpha);
                b.ring(cx, cy - s * 0.56f, s * 0.16f, s * 0.10f, 10, g[0], g[1], g[2], alpha);
                break;
            case 2: // shard
                GenshinKit.star4(b, cx, cy, s * 0.80f, s * 0.26f, 1f, 0.92f, 0.58f, alpha);
                break;
            case 3: // glass
                b.polygon(new float[]{
                        cx - s * 0.28f, cy - s * 0.50f,
                        cx + s * 0.28f, cy - s * 0.50f,
                        cx, cy + s * 0.52f
                }, 0.55f, 0.68f, 0.72f, alpha * 0.9f);
                break;
            case 4: // cloak
                b.polygon(new float[]{
                        cx, cy - s * 0.56f,
                        cx + s * 0.52f, cy + s * 0.52f,
                        cx, cy + s * 0.24f,
                        cx - s * 0.52f, cy + s * 0.52f
                }, 0.16f, 0.44f, 0.46f, alpha);
                break;
            case 5: // seal
                b.circle(cx, cy, s * 0.44f, 14, 0.42f, 0.16f, 0.20f, alpha);
                GenshinKit.star4(b, cx, cy, s * 0.30f, s * 0.10f, 1f, 0.90f, 0.68f, alpha);
                break;
            case 6: // compass
                b.ring(cx, cy, s * 0.52f, s * 0.44f, 16, g[0], g[1], g[2], alpha);
                b.line(cx, cy - s * 0.36f, cx, cy + s * 0.36f, 1.8f, g[0], g[1], g[2], alpha);
                b.line(cx - s * 0.36f, cy, cx + s * 0.36f, cy, 1.8f, g[0], g[1], g[2], alpha);
                b.polygon(new float[]{
                        cx, cy - s * 0.34f, cx + s * 0.14f, cy, cx - s * 0.14f, cy
                }, 1f, 0.55f, 0.35f, alpha);
                break;
            case 7: // horn
            default:
                b.curveApprox(cx - s * 0.52f, cy + s * 0.40f, cx + s * 0.10f, cy + s * 0.44f,
                        cx + s * 0.52f, cy - s * 0.16f, cx + s * 0.40f, cy - s * 0.56f,
                        2.6f, g[0], g[1], g[2], alpha);
                break;
        }
    }

    @Override
    public void drawTitle(UiBatch b, AtlasFont font, float alpha) {
        GenshinKit.title(b, "SHOP", b.virtualWidth * 0.5f, 12f, font, alpha);
    }

    @Override
    public Action hit(float x, float y, float w, float h, TabBar tabBar) {
        float cw = cardW(w);
        float ch = cardH(h);
        for (int i = 0; i < NAMES.length; i++) {
            int col = i % 4;
            int row = i / 4;
            float cx = gridX(w) + col * (cw + 8f);
            float cy = gridY(h) + row * (ch + 10f);
            if (x >= cx && x <= cx + cw && y >= cy && y <= cy + ch) {
                selected = i;
                press[i] = 1f;
                return Action.NONE;
            }
        }
        float dx = gridX(w);
        float dy = gridY(h) + 2f * (ch + 10f) + 8f;
        float dw = w * 0.585f;
        if (Math.abs(x - (dx + dw - 66f)) < 55f && Math.abs(y - (dy + 22f)) < 18f) {
            buyPress = 1f;
            return Action.NONE;
        }
        return Action.NONE;
    }
}
