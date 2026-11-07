package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.GenshinKit;
import com.laststar.game.ui.Style;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;
import com.laststar.game.world.SpellBook;
import com.laststar.game.world.World;
import com.laststar.game.world.lore.SpellCorpus;

/**
 * The Wander, open.
 *
 * <p>Every page the player owns, with its mastery, its Light cost and what it
 * has learned so far. The screen exists because the book is the progression
 * system: a hundred and one pages each levelling to thirty is only meaningful
 * if the player can see where each one stands, what the next stage will give
 * them, and which four are on the bar.</p>
 *
 * <p>Pages still asleep are listed too, greyed, with the settlement that holds
 * them. Hunting for a page is easier if you know the page exists.</p>
 */
public final class SpellScreen implements Screen {

    /** Rows visible at once. The book has a hundred and one pages. */
    private static final int ROWS = 5;

    /** Which pages the list is showing. */
    private static final String[] TABS = {"AWAKE", "ALL", "SCHOOL", "SLEEPING"};

    public int activeTab;
    public int selected;
    public int scroll;
    private final float[] tabPress = new float[TABS.length];
    private final float[] rowPress = new float[ROWS];
    private float equipPress;
    private float time;

    private World world;
    private int cachedTab = -1;
    private int cachedAwake = -1;
    private final java.util.List<Integer> rows = new java.util.ArrayList<Integer>();

    @Override
    public void bind(World world) {
        this.world = world;
        cachedTab = -1;
    }

    @Override
    public void update(float dt) {
        time += dt;
        for (int i = 0; i < tabPress.length; i++) {
            if (tabPress[i] > 0f) {
                tabPress[i] = Math.max(0f, tabPress[i] - dt * 3.5f);
            }
        }
        for (int i = 0; i < rowPress.length; i++) {
            if (rowPress[i] > 0f) {
                rowPress[i] = Math.max(0f, rowPress[i] - dt * 3.5f);
            }
        }
        if (equipPress > 0f) {
            equipPress = Math.max(0f, equipPress - dt * 3f);
        }
        syncRows();
    }

    /**
     * Rebuilds the visible list when the book has changed.
     *
     * <p>The book only changes when a page wakes or levels, so the awake count
     * plus the level of the first page is enough to know whether to rebuild.</p>
     */
    private void syncRows() {
        if (world == null) {
            return;
        }
        int awake = countAwake();
        int stamp = awake * 131 + (world.spells.pages[0].level << 3)
                + (int) (world.spells.pages[0].xp);
        if (activeTab == cachedTab && stamp == cachedAwake) {
            return;
        }
        cachedTab = activeTab;
        cachedAwake = stamp;
        rows.clear();
        for (int i = 0; i < SpellCorpus.count(); i++) {
            if (!matches(i)) {
                continue;
            }
            rows.add(Integer.valueOf(i));
        }
        selected = Math.max(0, Math.min(selected, rows.size() - 1));
        if (scroll > selected) {
            scroll = selected;
        }
        if (selected >= scroll + ROWS) {
            scroll = selected - ROWS + 1;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - ROWS)));
    }

    private boolean matches(int spell) {
        if (world == null) {
            return false;
        }
        SpellBook.Page p = world.spells.pages[spell];
        switch (activeTab) {
            case 0:
                return p.awake;
            case 1:
                return true;
            case 2:
                // The current school of the selected page, so taps walk the
                // eight categories without a second control.
                return p.def.category == schoolOfSelected();
            case 3:
                return !p.awake;
            default:
                return true;
        }
    }

    private SpellCorpus.Category schoolOfSelected() {
        Integer idx = selectedPage();
        if (idx == null) {
            return SpellCorpus.Category.ELEMENTAL;
        }
        return world.spells.pages[idx.intValue()].def.category;
    }

    private Integer selectedPage() {
        if (selected < 0 || selected >= rows.size()) {
            return null;
        }
        return rows.get(selected);
    }

    private int countAwake() {
        if (world == null) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < world.spells.pages.length; i++) {
            if (world.spells.pages[i].awake) {
                n++;
            }
        }
        return n;
    }

    /** Whether the selected page is on the bar, and in which slot. */
    private int equippedSlot(int spell) {
        if (world == null) {
            return -1;
        }
        for (int i = 0; i < world.spells.equipped.length; i++) {
            if (world.spells.equipped[i] == spell) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------ geometry

    private static float tabY(float h) {
        return h * 0.175f;
    }

    private static float tabX(float w, int i) {
        // Six tabs' worth of pitch across four: the longest label is "BY
        // SCHOOL", and a tighter pitch runs it into its neighbour.
        return w * 0.165f + i * w * 0.155f;
    }

    private static float listX() {
        return 18f;
    }

    private static float listY(float h) {
        return h * 0.255f;
    }

    private static float rowH() {
        return 34f;
    }

    private static float cardX(float w) {
        return w * 0.305f;
    }

    private static float cardY(float h) {
        return h * 0.255f;
    }

    private static float cardW(float w) {
        return w * 0.535f;
    }

    private static float cardH(float h) {
        return h * 0.495f;
    }

    @Override
    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        // ---- school tabs ----
        for (int i = 0; i < TABS.length; i++) {
            float cx = tabX(w, i);
            boolean active = i == activeTab;
            float tw = Math.min(w * 0.145f, Math.max(w * 0.105f,
                    UiBatch.textSpacedWidth(TABS[i], font, 1, 0.5f) + 20f));
            float th = 22f;
            float y = tabY(h);
            float[] shape = {
                    cx - tw * 0.5f, y - th * 0.5f,
                    cx + tw * 0.5f, y - th * 0.5f,
                    cx + tw * 0.5f, y + th * 0.5f,
                    cx - tw * 0.5f, y + th * 0.5f
            };
            if (active) {
                b.glow(cx, y, tw * 0.7f, 18, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                        Style.GOLD_LIGHT[2], 0.18f * alpha);
                b.polygon(shape, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                        Style.GOLD_LIGHT[2], alpha);
            } else {
                b.polygon(shape, 0.06f, 0.07f, 0.10f, (0.80f + tabPress[i] * 0.12f) * alpha);
            }
            float[] tc = active ? Style.INK : Style.CREAM;
            b.textSpacedCentred(fit(TABS[i], font, tw - 14f, 1, 0.5f), cx,
                    y - font.capHeight(1) * 0.5f, 1, 0.5f,
                    tc[0], tc[1], tc[2], alpha, font);
        }

        // ---- page list ----
        float rx = listX();
        float ry = listY(h);
        float rh = rowH();
        for (int r = 0; r < ROWS; r++) {
            int i = scroll + r;
            float y = ry + r * (rh + 5f);
            boolean sel = i == selected;
            float lw = w * 0.255f;
            if (sel) {
                b.quadGradient(rx, y, lw, rh,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        0.20f * alpha,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        0.03f * alpha);
                b.quad(rx, y, 2.6f, rh, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                        Style.GOLD_LIGHT[2], alpha);
            } else {
                b.quad(rx, y, lw, rh, 0.05f, 0.06f, 0.09f,
                        (0.66f + rowPress[r] * 0.14f) * alpha);
            }
            b.quad(rx, y, lw, 1f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.45f);
            b.quad(rx, y + rh - 1f, lw, 1f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2],
                    alpha * 0.45f);

            if (i >= rows.size() || world == null) {
                continue;
            }
            int spell = rows.get(i).intValue();
            SpellBook.Page p = world.spells.pages[spell];
            float[] c = schoolColour(p.def.category);
            GenshinKit.diamond(b, rx + 12f, y + 12f, 3.4f, c[0], c[1], c[2],
                    alpha * (p.awake ? 1f : 0.45f));

            String title = fit(p.def.name, font, lw - 46f, 1, 0.6f);
            b.textSpaced(title, rx + 22f, y + 6f, 1, 0.6f,
                    sel ? Style.IVORY[0] : Style.CREAM[0] * (p.awake ? 1f : 0.55f),
                    sel ? Style.IVORY[1] : Style.CREAM[1] * (p.awake ? 1f : 0.55f),
                    sel ? Style.IVORY[2] : Style.CREAM[2] * (p.awake ? 1f : 0.55f),
                    alpha, font);

            String sub = p.awake
                    ? "LV " + p.level + "  /  " + (int) p.def.lightCost + " LIGHT"
                    : "SLEEPING";
            b.textSpaced(sub, rx + 22f, y + 19f, 1, 0.3f,
                    Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2],
                    alpha * 0.9f, font);

            // A mastery bar along the bottom of the row: the one thing a player
            // needs at a glance across the whole book.
            if (p.awake) {
                float barW = lw - 46f;
                float frac = p.level >= SpellBook.MAX_LEVEL ? 1f : p.xp / p.xpToNext();
                b.quad(rx + 22f, y + rh - 5f, barW, 2f, 0.14f, 0.16f, 0.20f, alpha);
                b.quad(rx + 22f, y + rh - 5f, barW * Math.min(1f, frac), 2f,
                        c[0], c[1], c[2], alpha);
            }
        }

        // ---- page card ----
        drawCard(b, font, alpha, w, h);

        // ---- back / close ----
        GenshinKit.roundButton(b, 30f, 26f, 15f, 0f, alpha);
        GenshinKit.chevron(b, 30f, 26f, 9f, -1, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
        GenshinKit.roundButton(b, w - 30f, 26f, 15f, 0f, alpha);
        b.line(w - 35f, 21f, w - 25f, 31f, 2.2f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
        b.line(w - 25f, 21f, w - 35f, 31f, 2.2f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
    }

    private void drawCard(UiBatch b, AtlasFont font, float alpha, float w, float h) {
        float cx = cardX(w);
        float cy = cardY(h);
        float cw = cardW(w);
        float ch = cardH(h);

        b.quadGradient(cx, cy, cw, ch,
                Style.PARCHMENT[0], Style.PARCHMENT[1], Style.PARCHMENT[2], 0.97f * alpha,
                Style.PARCHMENT_DK[0], Style.PARCHMENT_DK[1], Style.PARCHMENT_DK[2],
                0.99f * alpha);
        b.quad(cx, cy, cw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(cx, cy + ch - 3f, cw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(cx, cy, 3f, ch, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(cx + cw - 3f, cy, 3f, ch, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);

        float mx = cx + cw * 0.5f;
        float my = cy + 32f;

        Integer idx = selectedPage();
        if (idx == null || world == null) {
            b.textSpacedCentred("THE WANDER IS EMPTY", mx, my + 20f, 1, 1.2f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
            return;
        }
        int spell = idx.intValue();
        SpellBook.Page p = world.spells.pages[spell];
        float[] c = schoolColour(p.def.category);

        b.glow(mx, my, 54f, 20, c[0], c[1], c[2], 0.22f * alpha);
        b.circle(mx, my, 17f, 22, c[0], c[1], c[2], alpha * 0.9f);
        b.circle(mx, my, 13.5f, 20, 0.93f, 0.87f, 0.72f, alpha);
        GenshinKit.star4(b, mx, my, 10f, 3.4f, Style.GOLD[0], Style.GOLD[1],
                Style.GOLD[2], alpha);

        b.textSpacedCentred(p.def.name, mx, my + 24f, 2, 1.8f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);

        String school = p.def.category.name() + "  /  " + p.def.shape.name();
        b.textSpacedCentred(school, mx, my + 43f, 1, 0.9f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.9f, font);

        // Mastery: level, bar and where the next stage lands.
        float barX = cx + 20f;
        float barW = cw - 40f;
        float barY = my + 58f;
        float frac = p.awake
                ? (p.level >= SpellBook.MAX_LEVEL ? 1f : p.xp / p.xpToNext())
                : 0f;
        b.quad(barX, barY, barW, 6f, 0.22f, 0.18f, 0.14f, alpha * 0.7f);
        b.quad(barX, barY, barW * Math.min(1f, frac), 6f, c[0], c[1], c[2], alpha);
        String lvl = p.awake ? "MASTERY " + p.level + " / " + SpellBook.MAX_LEVEL
                : "NOT YET FOUND";
        b.textSpacedCentred(lvl, mx, barY + 10f, 1, 0.7f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.85f, font);

        // What the next stage gives, or what the mastery already has.
        SpellCorpus.Stage next = nextStage(p);
        String line = next == null
                ? "NOTHING FURTHER TO LEARN."
                : "AT " + next.level + ": " + next.note;
        String[] wrapped = UiBatch.wrap(line, font, 1, 0.35f, barW);
        float ty = barY + 22f;
        for (int i = 0; i < wrapped.length && i < 2; i++) {
            b.textSpaced(wrapped[i], barX, ty, 1, 0.35f,
                    Style.INK[0] * 0.9f, Style.INK[1] * 0.9f, Style.INK[2] * 0.9f,
                    alpha * 0.92f, font);
            ty += 12.5f;
        }

        // Facts that decide whether a page earns a slot.
        String[] chips = {"DAMAGE", "LIGHT", "COOLDOWN"};
        String[] values = {
                String.valueOf(Math.round(p.damage())),
                String.valueOf(Math.round(p.def.lightCost)),
                String.format(java.util.Locale.US, "%.1fS", p.def.cooldown)
        };
        float chipY = cy + ch - 66f;
        float chipW = (cw - 78f) / 3f;
        for (int i = 0; i < 3; i++) {
            float chx = cx + 24f + i * (chipW + 15f);
            GenshinKit.parchment(b, chx, chipY, chipW, 26f, alpha * 0.95f);
            b.textSpacedCentred(chips[i], chx + chipW * 0.5f, chipY + 4f, 1, 0.55f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.62f, font);
            b.textSpacedCentred(values[i], chx + chipW * 0.5f, chipY + 15f, 1, 0.5f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
        }

        // Equip: which slot the page is in, or the next free one.
        float bx = cx + cw * 0.5f;
        float by = cy + ch + 26f;
        int slot = equippedSlot(spell);
        boolean canEquip = p.awake;
        String label = slot >= 0 ? "SLOT " + (slot + 1) + "  UNEQUIP"
                : canEquip ? "EQUIP" : "SLEEPING";
        // The lozenge is sized to the label rather than the label crammed into
        // a fixed width: "SLOT 1  UNEQUIP" is the longest state it can show.
        float labelW = UiBatch.textSpacedWidth(label, font, 1, 2.2f);
        float lozengeW = Math.max(120f, labelW + 44f);
        GenshinKit.lozenge(b, bx, by, lozengeW, 27f, canEquip, equipPress, alpha);
        b.textSpacedCentred(label, bx, by - font.capHeight(1) * 0.5f, 1, 2.2f,
                canEquip ? Style.IVORY[0] : Style.CREAM_DIM[0],
                canEquip ? Style.IVORY[1] : Style.CREAM_DIM[1],
                canEquip ? Style.IVORY[2] : Style.CREAM_DIM[2], alpha, font);
    }

    /** The next mastery stage above the page's level, or null at the top. */
    private static SpellCorpus.Stage nextStage(SpellBook.Page p) {
        SpellCorpus.Stage best = null;
        for (int i = 0; i < p.def.stages.length; i++) {
            SpellCorpus.Stage s = p.def.stages[i];
            if (s.level > p.level && (best == null || s.level < best.level)) {
                best = s;
            }
        }
        return best;
    }

    private static float[] schoolColour(SpellCorpus.Category category) {
        switch (category) {
            case ELEMENTAL:
                return new float[]{0.98f, 0.52f, 0.24f};
            case DEFENSIVE:
                return new float[]{0.42f, 0.82f, 0.98f};
            case ARCANE:
                return new float[]{0.72f, 0.52f, 0.98f};
            case SPATIAL:
                return new float[]{0.55f, 0.95f, 0.72f};
            case ENHANCEMENT:
                return new float[]{0.98f, 0.84f, 0.42f};
            case SUMMONING:
                return new float[]{0.62f, 0.90f, 0.88f};
            case ILLUSION:
                return new float[]{0.86f, 0.58f, 0.98f};
            case FORBIDDEN:
                return new float[]{0.95f, 0.32f, 0.42f};
            default:
                return new float[]{0.90f, 0.90f, 0.94f};
        }
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

    @Override
    public void drawTitle(UiBatch b, AtlasFont font, float alpha) {
        GenshinKit.title(b, "THE WANDER", b.virtualWidth * 0.5f, 8f, font, alpha);
        // A hint that the chevron leads somewhere useful, since it is not a
        // plain back arrow any more.
        b.textSpaced("FORGE", 54f, 22f, 1, 0.4f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                0.85f * alpha, font);
    }

    @Override
    public Action hit(float x, float y, float w, float h, TabBar tabBar) {
        for (int i = 0; i < TABS.length; i++) {
            if (Math.abs(x - tabX(w, i)) < w * 0.075f && Math.abs(y - tabY(h)) < 20f) {
                activeTab = i;
                selected = 0;
                scroll = 0;
                tabPress[i] = 1f;
                cachedTab = -1;
                return Action.NONE;
            }
        }
        float rx = listX();
        float ry = listY(h);
        float rh = rowH();
        for (int r = 0; r < ROWS; r++) {
            float rowTop = ry + r * (rh + 5f);
            int i = scroll + r;
            if (x >= rx && x <= rx + w * 0.255f && y >= rowTop && y <= rowTop + rh) {
                if (i < rows.size()) {
                    selected = i;
                    rowPress[r] = 1f;
                }
                return Action.NONE;
            }
        }
        if (x >= rx && x <= rx + w * 0.255f) {
            if (y < ry && scroll > 0) {
                scroll--;
                return Action.NONE;
            }
            if (y > ry + ROWS * (rh + 5f) && scroll + ROWS < rows.size()) {
                scroll++;
                return Action.NONE;
            }
        }
        // Equip / unequip.
        float bx = cardX(w) + cardW(w) * 0.5f;
        float by = cardY(h) + cardH(h) + 26f;
        if (Math.abs(x - bx) < 96f && Math.abs(y - by) < 20f) {
            equipPress = 1f;
            toggleEquip();
            return Action.NONE;
        }
        if (Math.abs(x - 30f) < 20f && Math.abs(y - 26f) < 20f) {
            // The back chevron goes to the forge instead of out: making a page
            // is the thing a player wants next after reading the book.
            return Action.OPEN_FORGE;
        }
        if (Math.abs(x - (w - 30f)) < 20f && Math.abs(y - 26f) < 20f) {
            return Action.BACK;
        }
        return Action.NONE;
    }

    /** Puts the selected page on the bar, or takes it off. */
    private void toggleEquip() {
        Integer idx = selectedPage();
        if (idx == null || world == null) {
            return;
        }
        int spell = idx.intValue();
        if (!world.spells.pages[spell].awake) {
            return;
        }
        world.spells.toggleEquip(spell);
    }
}
