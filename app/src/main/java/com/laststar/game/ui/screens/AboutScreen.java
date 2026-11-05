package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.GenshinKit;
import com.laststar.game.ui.Style;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;

/**
 * About page: an illuminated parchment scroll describing the world, the goal
 * and the controls, framed with gold rules and a wax seal.
 */
public final class AboutScreen implements Screen {

    /** Design scale for the body copy: the atlas needs room to stay legible. */
    private static final int BODY = 1;

    private static final String[] PARAGRAPHS = {
            "SIX SHARDS FELL AND THE SKY WENT OUT. YOU WAKE AT THE LAST "
                    + "LANTERN WITH A BLADE AND A FRAGMENT OF LIGHT.",
            "RECOVER ALL SIX AND CARRY THEM TO THE CITADEL. EACH ONE DARKENS "
                    + "THE WORLD AND QUICKENS THE NIGHT.",
            "LEFT THUMB WALKS, RIGHT THUMB LOOKS, THE BOLT STRIKES. THE GEAR "
                    + "OPENS THIS MENU, THE HORN MUTES THE AUDIO."
    };

    private static final String[] HEADINGS = {"THE STORY", "THE TASK", "THE CONTROLS"};

    private float time;

    @Override
    public void update(float dt) {
        time += dt;
    }

    @Override
    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        // Scroll body.
        float px = w * 0.16f;
        float py = h * 0.225f;
        float pw = w * 0.68f;
        float ph = h * 0.47f;
        b.glow(px + pw * 0.5f, py + ph * 0.5f, pw * 0.62f, 26,
                1f, 0.88f, 0.58f, 0.10f * alpha);
        b.quadGradient(px, py, pw, ph,
                Style.PARCHMENT[0], Style.PARCHMENT[1], Style.PARCHMENT[2], 0.965f * alpha,
                Style.PARCHMENT_DK[0], Style.PARCHMENT_DK[1], Style.PARCHMENT_DK[2],
                0.985f * alpha);
        // Gold rails top and bottom, plus a thin inner rule.
        b.quad(px, py, pw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px, py + ph - 3f, pw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px, py, 3f, ph, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px + pw - 3f, py, 3f, ph, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px + 8f, py + 8f, pw - 16f, 1.2f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha * 0.65f);
        b.quad(px + 8f, py + ph - 9.2f, pw - 16f, 1.2f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha * 0.65f);
        for (int i = 0; i < 4; i++) {
            float ox = (i == 1 || i == 3) ? px + pw - 12f : px + 12f;
            float oy = (i >= 2) ? py + ph - 12f : py + 12f;
            GenshinKit.corner(b, ox, oy, 18f, i, alpha * 0.9f);
        }

        // Three columns: heading + rule + body.
        float colW = (pw - 44f) / 3f;
        for (int c = 0; c < 3; c++) {
            float cx = px + 14f + c * (colW + 8f);
            b.textSpaced(HEADINGS[c], cx, py + 18f, 1, 1.4f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
            b.quad(cx, py + 34f, colW - 10f, 1.2f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2],
                    alpha * 0.8f);
            GenshinKit.diamond(b, cx + 3f, py + 34.6f, 3f,
                    Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
            String[] lines = UiBatch.wrap(PARAGRAPHS[c], font, BODY, 0.2f, colW - 12f);
            float ty = py + 44f;
            for (int i = 0; i < lines.length && i < 8; i++) {
                b.textSpaced(lines[i], cx, ty, BODY, 0.2f,
                        Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.92f, font);
                ty += 13f;
            }
        }

        // Vertical rules between columns.
        for (int c = 1; c < 3; c++) {
            float cx = px + 10f + c * (colW + 8f) - 4f;
            b.quad(cx, py + 44f, 1f, ph - 78f,
                    Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.35f);
        }

        // Wax seal + build line.
        float sealX = w * 0.5f;
        float sealY = py + ph + 26f;
        b.glow(sealX, sealY, 30f, 18, 1f, 0.55f, 0.42f, 0.16f * alpha);
        b.circle(sealX, sealY, 15f, 22, 0.36f, 0.14f, 0.18f, alpha);
        b.ring(sealX, sealY, 15f, 13.5f, 22, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2],
                alpha * 0.8f);
        GenshinKit.star4(b, sealX, sealY, 8f, 2.6f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
        b.textSpacedCentred("LAST STAR - VERSION 1.0 - BUILT FOR ANDROID",
                w * 0.5f, sealY + 24f, 1, 0.8f,
                Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha * 0.9f, font);
        b.textSpacedCentred("GAME CODE AND ART ARE ORIGINAL - UPSTREAM SOURCES IN /UPSTREAM",
                w * 0.5f, sealY + 38f, 1, 0.2f,
                Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha * 0.55f, font);
    }

    @Override
    public void drawTitle(UiBatch b, AtlasFont font, float alpha) {
        GenshinKit.title(b, "ABOUT", b.virtualWidth * 0.5f, 12f, font, alpha);
    }

    @Override
    public Action hit(float x, float y, float w, float h, TabBar tabBar) {
        return Action.NONE;
    }
}
