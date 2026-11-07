package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.GenshinKit;
import com.laststar.game.ui.Style;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;
import com.laststar.game.world.SpellBook;
import com.laststar.game.world.World;
import com.laststar.game.world.lore.Companions;
import com.laststar.game.world.lore.Elaris;

/**
 * The map: fourteen settlements, and the choice of which one to walk to.
 *
 * <p>Without this, the fourteen regions and the companions who live in them are
 * only reachable from code. Each entry shows what the place is, what level it
 * expects, how many pages are still hidden there, whether a companion is
 * waiting, and whether the player has already been.</p>
 *
 * <p>Travelling is free. The document ties progress to curiosity rather than to
 * a currency sink, and charging for the map would make the fourteen regions a
 * budget rather than an invitation.</p>
 */
public final class TravelScreen implements Screen {

    private static final int ROWS = 5;

    public int scroll;
    public int selected;
    private final float[] rowPress = new float[ROWS];
    private float goPress;
    private float time;

    private World world;

    @Override
    public void bind(World world) {
        this.world = world;
    }

    @Override
    public void update(float dt) {
        time += dt;
        for (int i = 0; i < rowPress.length; i++) {
            if (rowPress[i] > 0f) {
                rowPress[i] = Math.max(0f, rowPress[i] - dt * 3.5f);
            }
        }
        if (goPress > 0f) {
            goPress = Math.max(0f, goPress - dt * 3f);
        }
    }

    /** Pages still lying in a region, or -1 when there is no world. */
    private int pagesWaiting(int region) {
        if (world == null) {
            return -1;
        }
        int n = 0;
        int[] found = com.laststar.game.world.lore.SpellCorpus.inRegion(region);
        for (int i = 0; i < found.length; i++) {
            if (found[i] < world.spells.pages.length && !world.spells.pages[found[i]].awake) {
                n++;
            }
        }
        return n;
    }

    /** A companion who lives here and has not joined, or -1. */
    private int companionWaiting(int region) {
        if (world == null) {
            return -1;
        }
        for (int i = 0; i < Companions.count(); i++) {
            Companions.Def c = Companions.get(i);
            if (c.region == region && !world.party.recruited[i]) {
                return i;
            }
        }
        return -1;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, Elaris.REGION_COUNT - ROWS)));
        selected = Math.max(0, Math.min(selected, Elaris.REGION_COUNT - 1));
    }

    // ------------------------------------------------------------ geometry

    private static float listX() {
        return 18f;
    }

    private static float listY(float h) {
        return h * 0.235f;
    }

    private static float rowStep() {
        return 34f;
    }

    @Override
    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;
        clampScroll();

        // The list.
        float rx = listX();
        float ry = listY(h);
        float lw = w * 0.325f;
        for (int r = 0; r < ROWS; r++) {
            int i = scroll + r;
            if (i >= Elaris.REGION_COUNT) {
                break;
            }
            Elaris.Region region = Elaris.regions()[i];
            float y = ry + r * rowStep();
            boolean on = i == selected;
            boolean here = world != null && world.regionIndex == i;

            if (on) {
                b.quadGradient(rx, y, lw, 28f,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        0.18f * alpha,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        0.02f * alpha);
                b.quad(rx, y, 2.6f, 28f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                        Style.GOLD_LIGHT[2], alpha);
            } else {
                b.quad(rx, y, lw, 28f, 0.05f, 0.06f, 0.09f,
                        (0.62f + rowPress[r] * 0.14f) * alpha);
            }
            b.quad(rx, y + 27.5f, lw, 0.8f, Style.GOLD[0], Style.GOLD[1],
                    Style.GOLD[2], alpha * 0.4f);

            String band = "LV " + region.minLevel + "-" + region.maxLevel;
            float bandW = UiBatch.textSpacedWidth(band, font, 1, 0.3f);
            b.textSpaced(fit(region.name, font, lw - 22f - bandW, 1, 0.45f),
                    rx + 10f, y + 3f, 1, 0.5f,
                    on ? Style.IVORY[0] : Style.CREAM[0],
                    on ? Style.IVORY[1] : Style.CREAM[1],
                    on ? Style.IVORY[2] : Style.CREAM[2], alpha, font);

            // Level band, right-aligned in its own gutter.
            b.textSpacedRight(band, rx + lw - 8f, y + 3f, 1, 0.3f,
                    Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2],
                    alpha * 0.85f, font);

            // What is there: pages, a companion, or the word for being here.
            int pages = pagesWaiting(i);
            int friend = companionWaiting(i);
            String note;
            if (here) {
                note = "YOU ARE HERE";
            } else if (friend >= 0) {
                note = Companions.get(friend).name + " IS HERE";
            } else if (pages > 0) {
                note = pages + (pages == 1 ? " PAGE" : " PAGES") + " HIDDEN";
            } else {
                note = region.identity;
            }
            b.textSpaced(fit(note, font, lw - 16f, 1, 0.3f), rx + 10f, y + 15f, 1, 0.3f,
                    here ? Style.GOLD_LIGHT[0] : Style.CREAM_DIM[0],
                    here ? Style.GOLD_LIGHT[1] : Style.CREAM_DIM[1],
                    here ? Style.GOLD_LIGHT[2] : Style.CREAM_DIM[2],
                    alpha * 0.9f, font);
        }

        drawDetail(b, font, alpha, w, h);

        // A pair of figures on the far control, since it now opens the company.
        b.circle(w - 33f, 23f, 3.2f, 10, 1f, 0.92f, 0.70f, alpha);
        b.circle(w - 27f, 23f, 3.2f, 10, 1f, 0.92f, 0.70f, alpha * 0.8f);
        b.quad(w - 37f, 27f, 14f, 3f, 1f, 0.92f, 0.70f, alpha * 0.85f);
        GenshinKit.roundButton(b, w - 30f, 26f, 15f, 0f, alpha);
        b.line(w - 35f, 21f, w - 25f, 31f, 2.2f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
        b.line(w - 25f, 21f, w - 35f, 31f, 2.2f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
    }

    private void drawDetail(UiBatch b, AtlasFont font, float alpha, float w, float h) {
        float px = w * 0.365f;
        float py = h * 0.235f;
        float pw = w * 0.59f;
        float ph = h * 0.50f;

        b.quadGradient(px, py, pw, ph,
                Style.PARCHMENT[0], Style.PARCHMENT[1], Style.PARCHMENT[2], 0.96f * alpha,
                Style.PARCHMENT_DK[0], Style.PARCHMENT_DK[1], Style.PARCHMENT_DK[2],
                0.99f * alpha);
        b.quad(px, py, pw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px, py + ph - 3f, pw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px, py, 3f, ph, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px + pw - 3f, py, 3f, ph, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);

        if (selected < 0 || selected >= Elaris.REGION_COUNT) {
            return;
        }
        Elaris.Region region = Elaris.regions()[selected];
        float cx = px + 20f;
        float cy = py + 16f;

        b.textSpaced(fit(region.name, font, pw - 40f, 2, 2f), cx, cy, 2, 2f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);

        String[] lines = UiBatch.wrap(region.identity, font, 1, 0.4f, pw - 40f);
        float ty = cy + 24f;
        for (int i = 0; i < lines.length && i < 2; i++) {
            b.textSpaced(lines[i], cx, ty, 1, 0.4f,
                    Style.INK[0] * 0.9f, Style.INK[1] * 0.9f, Style.INK[2] * 0.9f,
                    alpha * 0.92f, font);
            ty += 12f;
        }

        // The facts a traveller needs.
        float fy = ty + 6f;
        drawFact(b, font, alpha, cx, fy, "LEVEL BAND",
                region.minLevel + " TO " + region.maxLevel);
        drawFactWide(b, font, alpha, cx + (pw - 40f) * 0.46f, fy, "BOSS",
                region.boss, (pw - 40f) * 0.54f);
        fy += 24f;
        int pages = pagesWaiting(selected);
        drawFact(b, font, alpha, cx, fy, "PAGES HIDDEN",
                pages < 0 ? "-" : String.valueOf(pages));
        drawFact(b, font, alpha, cx + (pw - 40f) * 0.5f, fy, "HOLLOWS",
                region.creatures.length > 0 ? region.creatures[0] : "-");

        // What the player will actually meet, listed rather than summarised.
        float cy2 = fy + 34f;
        b.textSpaced("WILL BE FOUND", cx, cy2, 1, 0.6f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.55f, font);
        String creatures = "";
        for (int i = 0; i < region.creatures.length; i++) {
            creatures += (i == 0 ? "" : ", ") + region.creatures[i];
        }
        String[] clines = UiBatch.wrap(creatures, font, 1, 0.4f, pw - 40f);
        float cly = cy2 + 12f;
        for (int i = 0; i < clines.length && i < 3; i++) {
            b.textSpaced(clines[i], cx, cly, 1, 0.4f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.9f, font);
            cly += 12f;
        }

        // Whether the way here is open, which is the only gate the design has.
        if (region.gateFrom >= 0) {
            String gate = "THROUGH " + Elaris.regions()[region.gateFrom].name;
            b.textSpaced("ROUTE", cx, cly + 8f, 1, 0.6f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.55f, font);
            b.textSpaced(fit(gate, font, pw - 40f, 1, 0.4f), cx, cly + 20f, 1, 0.4f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.9f, font);
        }

        // The go button.
        boolean here = world != null && world.regionIndex == selected;
        float bx = px + pw * 0.5f;
        float by = py + ph + 24f;
        String goLabel = here ? "YOU ARE HERE" : "TRAVEL";
        float goW = Math.max(150f,
                UiBatch.textSpacedWidth(goLabel, font, 1, 3f) + 56f);
        GenshinKit.lozenge(b, bx, by, goW, 28f, !here, goPress, alpha);
        b.textSpacedCentred(goLabel,
                bx, by - font.capHeight(1) * 0.5f, 1, 3f,
                here ? Style.CREAM_DIM[0] : Style.IVORY[0],
                here ? Style.CREAM_DIM[1] : Style.IVORY[1],
                here ? Style.CREAM_DIM[2] : Style.IVORY[2], alpha, font);
    }

    /** As drawFact, but for a value that needs more of the card's width. */
    private static void drawFactWide(UiBatch b, AtlasFont font, float alpha,
                                     float x, float y, String label, String value,
                                     float width) {
        b.textSpaced(label, x, y, 1, 0.6f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.55f, font);
        b.textSpaced(fit(value, font, width, 1, 0.25f), x, y + 11f, 1, 0.25f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
    }

    private static void drawFact(UiBatch b, AtlasFont font, float alpha,
                                 float x, float y, String label, String value) {
        b.textSpaced(label, x, y, 1, 0.6f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.55f, font);
        b.textSpaced(fit(value, font, 150f, 1, 0.4f), x, y + 11f, 1, 0.4f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
    }

    @Override
    public void drawTitle(UiBatch b, AtlasFont font, float alpha) {
        GenshinKit.title(b, "ELARIS", b.virtualWidth * 0.5f, 8f, font, alpha);
    }

    @Override
    public Action hit(float x, float y, float w, float h, TabBar tabBar) {
        float rx = listX();
        float ry = listY(h);
        for (int r = 0; r < ROWS; r++) {
            float top = ry + r * rowStep();
            int i = scroll + r;
            if (x >= rx && x <= rx + w * 0.325f && y >= top && y <= top + 28f) {
                if (i < Elaris.REGION_COUNT) {
                    selected = i;
                    rowPress[r] = 1f;
                }
                return Action.NONE;
            }
        }
        // Wheel-less scrolling: the strip above and below the list.
        if (x >= rx && x <= rx + w * 0.325f) {
            if (y < ry) {
                scroll = Math.max(0, scroll - 1);
                return Action.NONE;
            }
            if (y > ry + ROWS * rowStep()) {
                scroll = Math.min(Math.max(0, Elaris.REGION_COUNT - ROWS), scroll + 1);
                return Action.NONE;
            }
        }
        // Travel.
        float bx = w * 0.365f + w * 0.59f * 0.5f;
        float by = h * 0.235f + h * 0.50f + 24f;
        if (Math.abs(x - bx) < 84f && Math.abs(y - by) < 20f) {
            goPress = 1f;
            if (world != null && world.regionIndex != selected) {
                world.enterRegion(selected);
            }
            return Action.NONE;
        }
        if (Math.abs(x - (w - 30f)) < 20f && Math.abs(y - 26f) < 20f) {
            // The map's far control opens the company: who is with you and
            // where you are going are the same decision.
            return Action.OPEN_COMPANY;
        }
        return Action.NONE;
    }

    /** Trims {@code text} with ellipsis until it fits {@code maxWidth}. */
    private static String fit(String text, AtlasFont font, float maxWidth,
                              int scale, float tracking) {
        if (UiBatch.textSpacedWidth(text, font, scale, tracking) <= maxWidth) {
            return text;
        }
        String trimmed = text;
        while (trimmed.length() > 2
                && UiBatch.textSpacedWidth(trimmed + "..", font, scale, tracking) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }
}
