package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.Backdrop;
import com.laststar.game.ui.GenshinKit;
import com.laststar.game.ui.Style;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;

/**
 * Title screen: painted citadel backdrop, ornate title plate, the hero
 * medallion, a parchment name selector and the big START lozenge.
 */
public final class HomeScreen implements Screen {

    private final TabBar tabs;
    private float time;
    private float pressStart;
    private float pressName;
    private float pressTab;

    /** Characters available as the player's name, matching the sketch's arrows. */
    public static final String[] NAMES = {
            "NIGHTWALKER", "STARWARDEN", "SIXTH SHARD", "LUMEN", "DUSKRUNNER"
    };
    private int nameIndex;
    private final float[] nameFlash = new float[2];

    public HomeScreen(TabBar tabs) {
        this.tabs = tabs;
    }

    @Override
    public void update(float dt) {
        time += dt;
        if (pressStart > 0f) {
            pressStart = Math.max(0f, pressStart - dt * 3.2f);
        }
        if (pressName > 0f) {
            pressName = Math.max(0f, pressName - dt * 3.2f);
        }
        if (pressTab > 0f) {
            pressTab = Math.max(0f, pressTab - dt * 3.2f);
        }
        for (int i = 0; i < nameFlash.length; i++) {
            if (nameFlash[i] > 0f) {
                nameFlash[i] = Math.max(0f, nameFlash[i] - dt * 2.5f);
            }
        }
    }

    @Override
    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        // ---- hero medallion (right side, mirrored from the sketch's layout) ----
        float mx = w * 0.775f;
        float my = h * 0.42f;
        float mr = 46f;
        b.glow(mx, my, mr * 2.6f, 28, 1f, 0.85f, 0.52f, 0.16f * alpha);
        b.circle(mx, my, mr, 30, 0.055f, 0.065f, 0.095f, 0.90f * alpha);
        // Inner portrait: a simple silhouette bust lit from the side.
        b.circle(mx, my - 4f, mr * 0.46f, 22, 0.16f, 0.20f, 0.26f, alpha);
        b.polygon(new float[]{
                mx - mr * 0.62f, my + mr * 0.86f,
                mx + mr * 0.62f, my + mr * 0.86f,
                mx + mr * 0.42f, my + mr * 0.18f,
                mx - mr * 0.42f, my + mr * 0.18f
        }, 0.12f, 0.34f, 0.40f, alpha);
        b.ring(mx, my, mr, mr - 3f, 34, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.ring(mx, my, mr - 6f, mr - 7.2f, 34, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], 0.6f * alpha);
        for (int i = 0; i < 4; i++) {
            float ang = (float) (i * Math.PI * 0.5 + Math.PI * 0.25);
            GenshinKit.star4(b, mx + (float) Math.cos(ang) * mr, my + (float) Math.sin(ang) * mr,
                    6f, 2.1f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
        }

        // ---- name selector under the medallion ----
        float nw = 168f;
        float nh = 22f;
        float ny = my + mr + 22f;
        GenshinKit.parchment(b, mx - nw * 0.5f, ny, nw, nh, alpha * 0.96f);
        String name = NAMES[nameIndex];
        b.textSpacedCentred(name, mx, ny + nh * 0.5f - font.capHeight(1) * 0.5f, 1, 0.9f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
        float chevA = 0.75f + nameFlash[0] * 0.25f;
        GenshinKit.chevron(b, mx - nw * 0.5f + 12f, ny + nh * 0.5f, 7f, -1,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * chevA);
        GenshinKit.chevron(b, mx + nw * 0.5f - 12f, ny + nh * 0.5f, 7f, 1,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * chevA);

        // ---- small stat plate ----
        float sy = ny + nh + 10f;
        String stats = "SHARDS 6/6     BEST 12";
        b.textSpacedCentred(stats, mx, sy, 1, 0.6f,
                Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha * 0.9f, font);

        // ---- START button ----
        float bw = 190f;
        float bh = 40f;
        float by = h * 0.735f;
        GenshinKit.lozenge(b, w * 0.5f, by, bw, bh, true, pressStart, alpha);
        b.textSpacedCentred("START", w * 0.5f, by - font.capHeight(2) * 0.5f, 2, 4f,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);

        // Tagline under the button.
        b.textSpacedCentred("A FRAGMENTED WORLD - SIX SHARDS - ONE LIGHT",
                w * 0.5f, by + bh * 0.5f + 12f, 1, 0.5f,
                Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha * 0.75f, font);
    }

    @Override
    public void drawTitle(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float cx = w * 0.5f;
        float y = 18f;

        // Title plate: a slender ivory banner with gold flourishes.
        float plateW = 336f;
        float plateH = 44f;
        float[] shape = {
                cx - plateW * 0.5f + 14f, y,
                cx + plateW * 0.5f - 14f, y,
                cx + plateW * 0.5f, y + plateH * 0.5f,
                cx + plateW * 0.5f - 14f, y + plateH,
                cx - plateW * 0.5f + 14f, y + plateH,
                cx - plateW * 0.5f, y + plateH * 0.5f
        };
        b.glow(cx, y + plateH * 0.5f, plateW * 0.62f, 26,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], 0.13f * alpha);
        b.polygon(shape, 0.055f, 0.065f, 0.095f, 0.82f * alpha);
        b.line(cx - plateW * 0.5f + 14f, y, cx + plateW * 0.5f - 14f, y, 2f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.line(cx - plateW * 0.5f + 14f, y + plateH, cx + plateW * 0.5f - 14f, y + plateH, 2f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.line(cx - plateW * 0.5f, y + plateH * 0.5f, cx - plateW * 0.5f + 14f, y, 2f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.line(cx - plateW * 0.5f, y + plateH * 0.5f, cx - plateW * 0.5f + 14f, y + plateH, 2f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.line(cx + plateW * 0.5f, y + plateH * 0.5f, cx + plateW * 0.5f - 14f, y, 2f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.line(cx + plateW * 0.5f, y + plateH * 0.5f, cx + plateW * 0.5f - 14f, y + plateH, 2f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);

        // Fit the wordmark to the plate using the same metrics the draw call uses.
        int titleScale = 3;
        float titleTrack = 3f;
        while (titleScale > 1
                && UiBatch.textSpacedWidth("LAST STAR", font, titleScale, titleTrack)
                > plateW - 54f) {
            titleScale--;
        }
        b.textSpacedCentred("LAST STAR", cx, y + plateH * 0.5f - font.capHeight(titleScale) * 0.5f,
                titleScale, titleTrack, Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha,
                font);

        // Star emblem above the plate.
        GenshinKit.star4(b, cx, y - 7f, 8f, 2.6f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
    }

    /** Returns an action when the tap hits something. */
    public Action hit(float x, float y, float w, float h, TabBar tabBar) {
        float mx = w * 0.775f;
        float my = h * 0.42f;
        float mr = 46f;
        float ny = my + mr + 22f;
        // Name selector chevrons.
        if (y > ny - 8f && y < ny + 30f && Math.abs(x - mx) < 92f) {
            if (x < mx) {
                nameIndex = (nameIndex + NAMES.length - 1) % NAMES.length;
                nameFlash[0] = 1f;
                return Action.NAME_CHANGED;
            }
            nameIndex = (nameIndex + 1) % NAMES.length;
            nameFlash[1] = 1f;
            return Action.NAME_CHANGED;
        }
        // START.
        float by = h * 0.735f;
        if (Math.abs(x - w * 0.5f) < 100f && Math.abs(y - by) < 26f) {
            pressStart = 1f;
            return Action.START_GAME;
        }
        return Action.NONE;
    }

    public String playerName() {
        return NAMES[nameIndex];
    }

    public void pressTab() {
        pressTab = 1f;
    }
}
