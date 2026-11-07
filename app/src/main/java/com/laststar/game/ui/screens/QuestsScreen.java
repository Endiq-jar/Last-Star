package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.GenshinKit;
import com.laststar.game.ui.Style;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;
import com.laststar.game.world.QuestLog;
import com.laststar.game.world.World;
import com.laststar.game.world.lore.Elaris;

/**
 * Quest journal: parchment tabs (Completed / Side / Main / Serve Quests), a
 * ribbon list of quests on the left and an illuminated parchment card on the
 * right showing title, description, rewards and shard progress.
 */
public final class QuestsScreen implements Screen {

    /** The journal's four views. */
    private static final String[] TABS = {"ACTIVE", "MAIN", "SIDE", "DONE"};

    /** How many rows the list shows at once. */
    private static final int ROWS = 5;

    public int activeTab = 1;
    public int selectedQuest;
    /** First row shown, for scrolling past five quests. */
    public int scroll;
    private final float[] tabPress = new float[TABS.length];
    private final float[] rowPress = new float[ROWS];
    private float trackPress;
    private float time;
    /** The live journal, or null before the game has started. */
    private World world;
    private int cachedTab = -1;
    private int cachedVersion = -1;
    private final java.util.List<QuestLog.Entry> rows =
            new java.util.ArrayList<QuestLog.Entry>();

    @Override
    public void bind(World world) {
        if (this.world != world) {
            this.world = world;
            cachedTab = -1;
        }
    }

    /**
     * Rebuilds the visible rows when the tab or the journal has changed.
     *
     * <p>Doing this every frame would allocate a list per frame for nothing: the
     * journal only changes when a quest does, and the open/finished counts are
     * a cheap proxy for that.</p>
     */
    private void syncRows() {
        int version = world == null ? 0
                : world.quests.completedCount() * 31 + world.quests.openCount();
        if (activeTab == cachedTab && version == cachedVersion) {
            return;
        }
        cachedTab = activeTab;
        cachedVersion = version;
        rows.clear();
        if (world == null) {
            return;
        }
        QuestLog log = world.quests;
        for (int i = 0; i < log.entries.length; i++) {
            QuestLog.Entry e = log.entries[i];
            if (!matchesTab(e)) {
                continue;
            }
            // The list is the settlement the player is standing in, so a
            // journal reads as a place rather than as the whole world at once.
            if (e.def.region != world.regionIndex && e.state != QuestLog.DONE) {
                continue;
            }
            rows.add(e);
        }
        selectedQuest = Math.max(0, Math.min(selectedQuest, rows.size() - 1));
        if (scroll > selectedQuest) {
            scroll = selectedQuest;
        }
        if (selectedQuest >= scroll + ROWS) {
            scroll = selectedQuest - ROWS + 1;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - ROWS)));
    }

    private boolean matchesTab(QuestLog.Entry e) {
        switch (activeTab) {
            case 0:
                return e.state == QuestLog.OPEN;
            case 1:
                return e.state == QuestLog.OPEN && e.def.main;
            case 2:
                return e.state == QuestLog.OPEN && !e.def.main;
            case 3:
                return e.state == QuestLog.DONE;
            default:
                return false;
        }
    }

    /** The row the card is describing, or null. */
    private QuestLog.Entry selected() {
        if (selectedQuest < 0 || selectedQuest >= rows.size()) {
            return null;
        }
        return rows.get(selectedQuest);
    }

    @Override
    public void update(float dt) {
        time += dt;
        syncRows();
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
        if (trackPress > 0f) {
            trackPress = Math.max(0f, trackPress - dt * 3f);
        }
    }

    // ------------------------------------------------------------ geometry

    private float tabY(float h) {
        return h * 0.175f;
    }

    private float tabX(float w, int i) {
        return w * 0.185f + i * w * 0.155f;
    }

    private float listX() {
        return 18f;
    }

    private float listY(float h) {
        return h * 0.255f;
    }

    private float rowH() {
        return 34f;
    }

    private float cardX(float w) {
        return w * 0.305f;
    }

    private float cardY(float h) {
        return h * 0.255f;
    }

    private float cardW(float w) {
        return w * 0.535f;
    }

    private float cardH(float h) {
        return h * 0.495f;
    }

    @Override
    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        // ---- parchment tabs ----
        for (int i = 0; i < TABS.length; i++) {
            float cx = tabX(w, i);
            float p = tabPress[i];
            boolean active = i == activeTab;
            float tw = Math.max(w * 0.125f,
                    UiBatch.textSpacedWidth(TABS[i], font, 1, 0.8f) + 26f);
            float th = 24f;
            float y = tabY(h);
            float nose = 9f;
            float[] shape = {
                    cx - tw * 0.5f, y - th * 0.5f,
                    cx + tw * 0.5f, y - th * 0.5f,
                    cx + tw * 0.5f, y + th * 0.28f,
                    cx, y + th * 0.5f + nose * 0.6f,
                    cx - tw * 0.5f, y + th * 0.28f
            };
            if (active) {
                b.glow(cx, y, tw * 0.75f, 20, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                        Style.GOLD_LIGHT[2], 0.20f * alpha);
                b.polygon(shape, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        alpha);
            } else {
                b.polygon(shape, 0.06f, 0.07f, 0.10f, (0.82f + p * 0.12f) * alpha);
            }
            stroke(b, shape, 1.8f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                    alpha);
            float[] tc = active ? Style.INK : Style.CREAM;
            b.textSpacedCentred(TABS[i], cx, y - font.capHeight(1) * 0.5f - 1f, 1, 0.8f,
                    tc[0], tc[1], tc[2], alpha, font);
            if (active) {
                GenshinKit.diamond(b, cx, y - th * 0.5f - 5f, 3.2f,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
            }
        }

        // ---- quest ribbon list ----
        float rx = listX();
        float ry = listY(h);
        float rh = rowH();
        for (int r = 0; r < ROWS; r++) {
            int i = scroll + r;
            float y = ry + r * (rh + 5f);
            boolean selected = i == selectedQuest;
            float p = rowPress[r];
            float lw = w * 0.255f;
            if (selected) {
                b.quadGradient(rx, y, lw, rh,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], 0.20f * alpha,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], 0.03f * alpha);
                b.quad(rx, y, 2.6f, rh, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                        Style.GOLD_LIGHT[2], alpha);
            } else {
                b.quad(rx, y, lw, rh, 0.05f, 0.06f, 0.09f,
                        (0.66f + p * 0.14f) * alpha);
            }
            b.quad(rx, y, lw, 1f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.45f);
            b.quad(rx, y + rh - 1f, lw, 1f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2],
                    alpha * 0.45f);

            GenshinKit.diamond(b, rx + 12f, y + 12f, 3.4f,
                    Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
            QuestLog.Entry entry = i < rows.size() ? rows.get(i) : null;
            String title = entry == null ? "--"
                    : fit(entry.def.title, font, w * 0.255f - 30f, 1, 0.6f);
            b.textSpaced(title, rx + 22f, y + 6f, 1, 0.6f,
                    selected ? Style.IVORY[0] : Style.CREAM[0],
                    selected ? Style.IVORY[1] : Style.CREAM[1],
                    selected ? Style.IVORY[2] : Style.CREAM[2], alpha, font);
            String objective = entry == null ? ""
                    : fit(entry.def.objectiveLine(), font, w * 0.255f - 30f, 1, 0.3f);
            b.textSpaced(objective, rx + 22f, y + 19f, 1, 0.3f,
                    Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha * 0.9f,
                    font);
        }

        // ---- quest card ----
        float cx = cardX(w);
        float cy = cardY(h);
        float cw = cardW(w);
        float ch = cardH(h);

        // Parchment body.
        b.quadGradient(cx, cy, cw, ch,
                Style.PARCHMENT[0], Style.PARCHMENT[1], Style.PARCHMENT[2], 0.97f * alpha,
                Style.PARCHMENT_DK[0], Style.PARCHMENT_DK[1], Style.PARCHMENT_DK[2], 0.99f * alpha);
        // Gold double border with corner scrollwork.
        b.quad(cx, cy, cw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(cx, cy + ch - 3f, cw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(cx, cy, 3f, ch, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(cx + cw - 3f, cy, 3f, ch, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(cx + 6f, cy + 6f, cw - 12f, 1.4f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha * 0.7f);
        b.quad(cx + 6f, cy + ch - 7.4f, cw - 12f, 1.4f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha * 0.7f);
        for (int i = 0; i < 4; i++) {
            float ox = (i == 1 || i == 3) ? cx + cw - 10f : cx + 10f;
            float oy = (i >= 2) ? cy + ch - 10f : cy + 10f;
            GenshinKit.corner(b, ox, oy, 16f, i, alpha * 0.9f);
        }

        // Illuminated medallion at the top.
        float mx = cx + cw * 0.5f;
        float my = cy + 34f;
        b.glow(mx, my, 56f, 22, 1f, 0.88f, 0.55f, 0.20f * alpha);
        b.circle(mx, my, 18f, 22, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
        b.circle(mx, my, 14.5f, 20, 0.92f, 0.85f, 0.68f, alpha);
        GenshinKit.star4(b, mx, my, 11f, 3.6f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2],
                alpha);
        for (int i = 0; i < 2; i++) {
            float dir = i == 0 ? -1f : 1f;
            b.line(mx + dir * 22f, my, mx + dir * 44f, my, 1.2f,
                    Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.7f);
            GenshinKit.diamond(b, mx + dir * 48f, my, 2.6f, Style.GOLD[0], Style.GOLD[1],
                    Style.GOLD[2], alpha * 0.8f);
        }

        // Title.
        QuestLog.Entry entry = selected();
        String title = entry == null ? "NOTHING HERE" : entry.def.title;
        b.textSpacedCentred(title, mx, my + 26f, 2, 2.2f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
        float ulW = title.length() * (font.advance() * 2 + 2.2f);
        b.line(mx - ulW * 0.5f, my + 46f, mx + ulW * 0.5f, my + 46f, 1.4f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.8f);
        GenshinKit.diamond(b, mx, my + 46f, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2],
                alpha);

        // Description, wrapped to the card's inner width.
        String desc = entry == null
                ? "NO QUESTS OF THIS KIND HERE YET. WALK FURTHER, AND LOOK CLOSER."
                : entry.def.summary;
        float maxWidth = cw - 52f;
        String[] lines = UiBatch.wrap(desc, font, 1, 0.35f, maxWidth);
        float ty = my + 56f;
        for (int i = 0; i < lines.length && i < 3; i++) {
            b.textSpaced(lines[i], cx + 26f, ty, 1, 0.35f,
                    Style.INK[0] * 0.9f, Style.INK[1] * 0.9f, Style.INK[2] * 0.9f,
                    alpha * 0.92f, font);
            ty += 12.5f;
        }

        // Where it is, so an entry reads as a place.
        if (entry != null) {
            String where = Elaris.regions()[entry.def.region].name
                    + "  /  LEVEL " + entry.def.level;
            b.textSpacedCentred(where, mx, ty + 4f, 1, 0.45f,
                    Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.85f, font);
        }

        // Stat chips.
        float chipY = cy + ch - 68f;
        String[] chips = {"REWARD", "STATUS", "PROGRESS"};
        String[] values = entry == null
                ? new String[]{"-", "-", "-"}
                : new String[]{entry.def.rewardLine(),
                        entry.state == QuestLog.DONE ? "COMPLETE"
                                : entry.def.main ? "MAIN" : "SIDE",
                        entry.progress + "/" + entry.def.target};
        float chipW = (cw - 78f) / 3f;
        for (int i = 0; i < 3; i++) {
            float chx = cx + 24f + i * (chipW + 15f);
            GenshinKit.parchment(b, chx, chipY, chipW, 26f, alpha * 0.95f);
            b.textSpacedCentred(chips[i], chx + chipW * 0.5f, chipY + 4f, 1, 0.6f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.62f, font);
            b.textSpacedCentred(values[i], chx + chipW * 0.5f, chipY + 15f, 1, 0.5f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
        }

        // Shard pips.
        float pipY = cy + ch - 26f;
        int pips = entry == null ? 6 : Math.min(6, Math.max(1, entry.def.target));
        int collected = entry == null ? 0 : (int) Math.floor(entry.fraction() * pips);
        for (int i = 0; i < 6; i++) {
            float px = mx - 47f + i * 19f;
            boolean filled = i < pips && i < collected;
            if (filled) {
                b.glow(px, pipY, 14f, 10, 1f, 0.86f, 0.45f, 0.35f * alpha);
                GenshinKit.star4(b, px, pipY, 8f, 2.8f, 1f, 0.88f, 0.52f, alpha);
            } else {
                GenshinKit.star4(b, px, pipY, 8f, 2.8f, Style.INK[0], Style.INK[1],
                        Style.INK[2], alpha * 0.35f);
            }
        }

        // ---- track button ----
        float bx = cx + cw * 0.78f;
        float by = cy + ch + 26f;
        GenshinKit.lozenge(b, bx, by, 100f, 27f, true, trackPress, alpha);
        b.textSpacedCentred("WANDER", bx, by - font.capHeight(1) * 0.5f, 1, 3f,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);

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

    private static void stroke(UiBatch b, float[] poly, float t,
                               float r, float g, float bl, float a) {
        int n = poly.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            b.line(poly[i * 2], poly[i * 2 + 1], poly[j * 2], poly[j * 2 + 1], t, r, g, bl, a);
        }
    }

    @Override
    public void drawTitle(UiBatch b, AtlasFont font, float alpha) {
        GenshinKit.title(b, "QUESTS", b.virtualWidth * 0.5f, 8f, font, alpha);
    }

    @Override
    public Action hit(float x, float y, float w, float h, TabBar tabBar) {
        // Tabs.
        for (int i = 0; i < TABS.length; i++) {
            float cx = tabX(w, i);
            if (Math.abs(x - cx) < w * 0.085f && Math.abs(y - tabY(h)) < 20f) {
                activeTab = i;
                selectedQuest = 0;
                scroll = 0;
                tabPress[i] = 1f;
                return Action.NONE;
            }
        }
        // List rows.
        float rx = listX();
        float ry = listY(h);
        float rh = rowH();
        for (int r = 0; r < ROWS; r++) {
            float rowTop = ry + r * (rh + 5f);
            int i = scroll + r;
            if (x >= rx && x <= rx + w * 0.255f && y >= rowTop && y <= rowTop + rh) {
                if (i < rows.size()) {
                    selectedQuest = i;
                    rowPress[r] = 1f;
                }
                return Action.NONE;
            }
        }
        // Tapping just above or below the list moves it by a row, so a player
        // with more than five quests can reach them on a phone.
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
        // The book, which is where a tracked quest's reward usually lives.
        float bx = cardX(w) + cardW(w) * 0.78f;
        float by = cardY(h) + cardH(h) + 26f;
        if (Math.abs(x - bx) < 54f && Math.abs(y - by) < 18f) {
            trackPress = 1f;
            return Action.OPEN_SPELLS;
        }
        // Back / close.
        if (Math.abs(x - 30f) < 20f && Math.abs(y - 26f) < 20f) {
            return Action.BACK;
        }
        if (Math.abs(x - (w - 30f)) < 20f && Math.abs(y - 26f) < 20f) {
            return Action.BACK;
        }
        return Action.NONE;
    }
}
