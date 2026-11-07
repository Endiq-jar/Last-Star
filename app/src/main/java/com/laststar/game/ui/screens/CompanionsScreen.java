package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.GenshinKit;
import com.laststar.game.ui.Style;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;
import com.laststar.game.world.World;
import com.laststar.game.world.lore.Companions;
import com.laststar.game.world.lore.Dialogue;
import com.laststar.game.world.lore.Elaris;
import com.laststar.game.world.lore.QuestCorpus;

/**
 * The company: who travels with you, what they can do, and what they will say.
 *
 * <p>Two views in one screen. The roster shows each companion's Gift, their
 * bond, whether they are in the party and what the current pair unlocks. Tap one
 * and the screen becomes their conversation: what they say at this bond, the
 * choices, and the replies. Choosing is what moves trust, so the dialogue is the
 * progression rather than a decoration on it.</p>
 */
public final class CompanionsScreen implements Screen {

    private static final int ROWS = 6;

    public int selected;
    /** True while a conversation is open. */
    public boolean talking;
    /** The reply shown after a choice, or null. */
    public String reply;
    private float replyFade;

    private final float[] rowPress = new float[ROWS];
    private final float[] choicePress = new float[2];
    /** Where each choice box sits this frame, shared by draw and hit. */
    private final float[] choiceX = new float[2];
    private final float[] choiceW = new float[2];
    private float partyPress;
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
        for (int i = 0; i < choicePress.length; i++) {
            if (choicePress[i] > 0f) {
                choicePress[i] = Math.max(0f, choicePress[i] - dt * 3.5f);
            }
        }
        if (partyPress > 0f) {
            partyPress = Math.max(0f, partyPress - dt * 3f);
        }
        if (replyFade > 0f) {
            replyFade = Math.max(0f, replyFade - dt * 0.35f);
        }
    }

    // ------------------------------------------------------------ geometry

    private static float listX() {
        return 18f;
    }

    private static float listY(float h) {
        return h * 0.195f;
    }

    private static float rowStep() {
        return 40f;
    }

    private static float panelX(float w) {
        return w * 0.315f;
    }

    private static float panelY(float h) {
        return h * 0.195f;
    }

    private static float panelW(float w) {
        return w * 0.645f;
    }

    private static float panelH(float h) {
        return h * 0.60f;
    }

    @Override
    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        if (talking) {
            drawConversation(b, font, alpha, w, h);
        } else {
            drawRoster(b, font, alpha, w, h);
            drawDetail(b, font, alpha, w, h);
        }

        GenshinKit.roundButton(b, 30f, 26f, 15f, 0f, alpha);
        GenshinKit.chevron(b, 30f, 26f, 9f, -1, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
        GenshinKit.roundButton(b, w - 30f, 26f, 15f, 0f, alpha);
        b.line(w - 35f, 21f, w - 25f, 31f, 2.2f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
        b.line(w - 25f, 21f, w - 35f, 31f, 2.2f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
    }

    private void drawRoster(UiBatch b, AtlasFont font, float alpha, float w, float h) {
        float rx = listX();
        float ry = listY(h);
        float lw = w * 0.27f;
        for (int i = 0; i < Companions.count() && i < ROWS; i++) {
            Companions.Def c = Companions.get(i);
            float y = ry + i * rowStep();
            boolean on = i == selected;
            boolean here = world != null && world.party.recruited[i];
            boolean active = world != null
                    && (world.party.active[0] == i || world.party.active[1] == i);

            b.quad(rx, y, lw, 34f, on ? Style.SLATE_HI[0] : 0.05f,
                    on ? Style.SLATE_HI[1] : 0.06f, on ? Style.SLATE_HI[2] : 0.09f,
                    (0.80f + rowPress[i] * 0.12f) * alpha);
            if (on) {
                b.quad(rx, y, 2.6f, 34f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                        Style.GOLD_LIGHT[2], alpha);
            }
            b.quad(rx, y + 33.4f, lw, 0.8f, Style.GOLD[0], Style.GOLD[1],
                    Style.GOLD[2], alpha * 0.4f);

            if (!here) {
                // Met but not joined: greyed, so the list still shows them.
                b.textSpaced(fit(c.name, font, lw - 60f, 1, 0.4f), rx + 10f, y + 4f, 1, 0.4f,
                        Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2],
                        alpha * 0.55f, font);
                b.textSpaced(fit(Elaris.regions()[c.region].name, font, lw - 20f, 1, 0.3f),
                        rx + 10f, y + 18f, 1, 0.3f,
                        Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2],
                        alpha * 0.45f, font);
                GenshinKit.padlock(b, rx + lw - 16f, y + 17f, 8f, alpha * 0.45f);
                continue;
            }

            b.textSpaced(fit(c.name, font, lw - 60f, 1, 0.4f), rx + 10f, y + 4f, 1, 0.4f,
                    on ? Style.IVORY[0] : Style.CREAM[0],
                    on ? Style.IVORY[1] : Style.CREAM[1],
                    on ? Style.IVORY[2] : Style.CREAM[2], alpha, font);
            String bond = world.party.bond(i).name();
            b.textSpaced(bond, rx + 10f, y + 18f, 1, 0.3f,
                    Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.85f, font);
            if (active) {
                // A lit pip for whoever is actually travelling with you.
                b.circle(rx + lw - 14f, y + 17f, 4f, 12,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        alpha);
            }
        }
    }

    private void drawDetail(UiBatch b, AtlasFont font, float alpha, float w, float h) {
        float px = panelX(w);
        float py = panelY(h);
        float pw = panelW(w);
        float ph = panelH(h);

        b.quadGradient(px, py, pw, ph,
                Style.PARCHMENT[0], Style.PARCHMENT[1], Style.PARCHMENT[2], 0.96f * alpha,
                Style.PARCHMENT_DK[0], Style.PARCHMENT_DK[1], Style.PARCHMENT_DK[2],
                0.99f * alpha);
        b.quad(px, py, pw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px, py + ph - 3f, pw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px, py, 3f, ph, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px + pw - 3f, py, 3f, ph, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);

        Companions.Def c = Companions.get(selected);
        if (c == null) {
            return;
        }
        float cx = px + 20f;
        float cy = py + 16f;
        boolean here = world != null && world.party.recruited[selected];

        b.textSpaced(fit(c.name, font, pw - 40f, 2, 1.6f), cx, cy, 2, 1.6f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
        b.textSpaced(fit(c.title, font, pw - 40f, 1, 0.7f), cx, cy + 22f, 1, 0.7f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.9f, font);

        // The Gift is the character's mechanical identity, so it leads.
        float gy = cy + 40f;
        b.textSpaced("GIFT", cx, gy, 1, 0.6f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.55f, font);
        String[] gift = UiBatch.wrap(c.gift, font, 1, 0.35f, pw - 40f);
        float ty = gy + 11f;
        for (int i = 0; i < gift.length && i < 2; i++) {
            b.textSpaced(gift[i], cx, ty, 1, 0.35f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.92f, font);
            ty += 11f;
        }

        // Ability, with its cooldown and Light cost. Shown whether or not they
        // have joined, because it is the reason to go and find them.
        float ay = ty + 12f;
        b.textSpaced("ABILITY", cx, ay, 1, 0.6f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.55f, font);
        b.textSpaced(fit(c.ability.name, font, pw - 40f, 1, 0.8f), cx, ay + 11f, 1, 0.8f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
        String[] eff = UiBatch.wrap(c.ability.effect, font, 1, 0.3f, pw - 40f);
        float ey = ay + 23f;
        for (int i = 0; i < eff.length && i < 2; i++) {
            b.textSpaced(eff[i], cx, ey, 1, 0.3f,
                    Style.INK[0] * 0.88f, Style.INK[1] * 0.88f, Style.INK[2] * 0.88f,
                    alpha * 0.92f, font);
            ey += 11f;
        }

        if (!here) {
            String where = "NOT YET MET \u00b7 FOUND IN " + Elaris.regions()[c.region].name;
            b.textSpaced(fit(where, font, pw - 40f, 1, 0.55f), cx, ey + 6f, 1, 0.55f,
                    Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.9f, font);
            String[] how = UiBatch.wrap(whereYouMeet(selected), font, 1, 0.3f, pw - 40f);
            float wy = ey + 20f;
            for (int i = 0; i < how.length && i < 3; i++) {
                b.textSpaced(how[i], cx, wy, 1, 0.3f,
                        Style.INK[0] * 0.85f, Style.INK[1] * 0.85f, Style.INK[2] * 0.85f,
                        alpha * 0.9f, font);
                wy += 11f;
            }
            // The bond they will start at, so the track is legible before it fills.
            b.textSpaced("BOND", cx, py + ph - 40f, 1, 0.6f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.55f, font);
            b.textSpaced("STRANGER", cx + 44f, py + ph - 40f, 1, 0.5f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.55f, font);
            for (int i = 0; i < Companions.Bond.values().length; i++) {
                b.circle(cx + 44f + i * 12f, py + ph - 25f, 3.5f, 10,
                        0.55f, 0.52f, 0.42f, alpha * 0.35f);
            }
            return;
        }

        // The bond, as a row of pips, because it is the thing that gates use.
        String bondLine = world.party.bond(selected).name();
        b.textSpaced("BOND", cx, ey + 8f, 1, 0.6f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.55f, font);
        b.textSpaced(bondLine, cx + 44f, ey + 8f, 1, 0.5f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
        for (int i = 0; i < Companions.Bond.values().length; i++) {
            boolean on = i <= world.party.bond(selected).ordinal();
            b.circle(cx + 44f + i * 12f, ey + 23f, 3.5f, 10,
                    on ? 0.86f : 0.55f, on ? 0.66f : 0.52f, on ? 0.28f : 0.42f,
                    alpha * (on ? 0.95f : 0.5f));
        }

        // What they say, which is the route to trust.
        String line = world.party.bond(selected) == Companions.Bond.STRANGER
                ? Companions.lineFor(selected, Companions.Bond.STRANGER)
                : Companions.lineFor(selected, world.party.bond(selected));
        String[] said = UiBatch.wrap(line, font, 1, 0.3f, pw - 40f);
        float sy = ey + 36f;
        for (int i = 0; i < said.length && i < 2; i++) {
            b.textSpaced(said[i], cx, sy, 1, 0.3f,
                    Style.INK[0] * 0.80f, Style.INK[1] * 0.80f, Style.INK[2] * 0.80f,
                    alpha * 0.85f, font);
            sy += 11f;
        }

        // Who they work with, if the current pairing unlocks something.
        Companions.Synergy syn = world.party.activeSynergy();
        if (syn != null && (world.party.active[0] == selected
                || world.party.active[1] == selected)) {
            b.textSpaced("SYNERGY: " + syn.name, cx, cy + ph - 52f, 1, 0.5f,
                    Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha, font);
        }

        // The two controls: talk, and join or leave the party.
        float by = py + ph + 22f;
        GenshinKit.lozenge(b, px + pw * 0.28f, by, 110f, 27f, true, choicePress[0], alpha);
        b.textSpacedCentred("TALK", px + pw * 0.28f, by - font.capHeight(1) * 0.5f,
                1, 2.4f, Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);

        boolean activeNow = world.party.active[0] == selected
                || world.party.active[1] == selected;
        GenshinKit.lozenge(b, px + pw * 0.70f, by, 130f, 27f, !activeNow || true,
                partyPress, alpha);
        String pLabel = activeNow ? "STAND DOWN" : "TRAVEL WITH";
        b.textSpacedCentred(fit(pLabel, font, 116f, 1, 2f), px + pw * 0.70f,
                by - font.capHeight(1) * 0.5f, 1, 2f,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);
    }

    /**
     * A drawn emblem for each companion: the team has no portraits, so each
     * person gets a mark built from their own idea. SERA a seal, KESH an echo,
     * NADI a tide, BRANN a frost, MIRA a memory, WREN a step.
     */
    private void drawSigil(UiBatch b, int index, float cx, float cy, float r, float alpha) {
        Elaris.Region home = Elaris.regions()[Companions.get(index).region];
        // The region's sky tints the disc, so each emblem feels like somewhere.
        b.circle(cx, cy, r, 40, home.skyR * 0.55f + 0.04f,
                home.skyG * 0.55f + 0.05f, home.skyB * 0.55f + 0.07f, 0.92f * alpha);
        b.circle(cx, cy, r * 0.86f, 40, 0.05f, 0.06f, 0.09f, 0.85f * alpha);
        float gr = Style.GOLD_LIGHT[0], gg = Style.GOLD_LIGHT[1], gb = Style.GOLD_LIGHT[2];
        float k = r * 0.62f;
        switch (index) {
            case 0: // SERA: a ward seal, an open hexagon around a diamond.
                for (int i = 0; i < 6; i++) {
                    double a0 = i * Math.PI / 3.0 + Math.PI / 6.0;
                    double a1 = a0 + Math.PI / 3.0;
                    b.quad((float) (cx + Math.cos(a0) * k), (float) (cy + Math.sin(a0) * k),
                            2.2f, 2.2f, gr, gg, gb, alpha * 0.9f);
                    float x0 = (float) (cx + Math.cos(a0) * k);
                    float y0 = (float) (cy + Math.sin(a0) * k);
                    float x1 = (float) (cx + Math.cos(a1) * k);
                    float y1 = (float) (cy + Math.sin(a1) * k);
                    line(b, x0, y0, x1, y1, 1.6f, gr, gg, gb, alpha * 0.65f);
                }
                GenshinKit.diamond(b, cx, cy, k * 0.46f, gr, gg, gb, alpha);
                break;
            case 1: // KESH: an echo, three rings drifting off a point.
                for (int i = 0; i < 3; i++) {
                    float rr = k * (0.34f + i * 0.30f);
                    b.ring(cx, cy, rr, rr - 1.8f, 28, gr, gg, gb,
                            alpha * (0.95f - i * 0.24f));
                }
                b.circle(cx, cy, k * 0.16f, 14, gr, gg, gb, alpha);
                break;
            case 2: // NADI: the tide, three stacked waves.
                for (int i = 0; i < 3; i++) {
                    float yy = cy - k * 0.42f + i * k * 0.42f;
                    for (int j = 0; j < 12; j++) {
                        float t0 = j / 12f;
                        float t1 = (j + 1) / 12f;
                        float x0 = cx - k + t0 * k * 2f;
                        float y0 = yy + (float) Math.sin(t0 * Math.PI * 2 + i) * k * 0.13f;
                        float x1 = cx - k + t1 * k * 2f;
                        float y1 = yy + (float) Math.sin(t1 * Math.PI * 2 + i) * k * 0.13f;
                        line(b, x0, y0, x1, y1, 2.0f, gr, gg, gb,
                                alpha * (0.95f - i * 0.20f));
                    }
                }
                break;
            case 3: // BRANN: frost, six spokes with barbs.
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3.0;
                    float ex = (float) (cx + Math.cos(a) * k);
                    float ey = (float) (cy + Math.sin(a) * k);
                    line(b, cx, cy, ex, ey, 1.8f, gr, gg, gb, alpha * 0.9f);
                    float bx = (float) (cx + Math.cos(a) * k * 0.62f);
                    float by = (float) (cy + Math.sin(a) * k * 0.62f);
                    for (int s = -1; s <= 1; s += 2) {
                        double ba = a + s * 0.9;
                        line(b, bx, by, (float) (bx + Math.cos(ba) * k * 0.22f),
                                (float) (by + Math.sin(ba) * k * 0.22f),
                                1.4f, gr, gg, gb, alpha * 0.75f);
                    }
                }
                break;
            case 4: // MIRA: a memory, a ring with an orbiting point.
                b.ring(cx, cy, k, k - 2.2f, 34, gr, gg, gb, alpha * 0.9f);
                b.ring(cx, cy, k * 0.55f, k * 0.55f - 1.6f, 26, gr, gg, gb, alpha * 0.55f);
                float oa = time * 0.7f;
                b.circle(cx + (float) Math.cos(oa) * k, cy + (float) Math.sin(oa) * k,
                        3.2f, 12, 1f, 0.92f, 0.70f, alpha);
                b.circle(cx, cy, k * 0.13f, 12, gr, gg, gb, alpha);
                break;
            default: // WREN: a step, two chevrons moving.
                for (int i = 0; i < 2; i++) {
                    float ox = cx - k * 0.45f + i * k * 0.62f;
                    line(b, ox - k * 0.30f, cy - k * 0.50f, ox + k * 0.28f, cy,
                            2.0f, gr, gg, gb, alpha * (1f - i * 0.28f));
                    line(b, ox + k * 0.28f, cy, ox - k * 0.30f, cy + k * 0.50f,
                            2.0f, gr, gg, gb, alpha * (1f - i * 0.28f));
                }
                break;
        }
    }

    /** A thin line between two points, using a rotated quad. */
    private static void line(UiBatch b, float x0, float y0, float x1, float y1, float width,
                             float r, float g, float bl, float a) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            return;
        }
        float nx = -dy / len * width * 0.5f;
        float ny = dx / len * width * 0.5f;
        b.polygon(new float[] {
                x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny
        }, r, g, bl, a);
    }

    /** The width a choice box needs, capped so the pair always fits the screen. */
    private static float choiceWidth(String text, AtlasFont font, float w) {
        float widest = 0f;
        String[] parts = text.split(" ");
        StringBuilder line = new StringBuilder();
        java.util.List<String> rows = new java.util.ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            String probe = line.length() == 0 ? parts[i] : line + " " + parts[i];
            if (UiBatch.textSpacedWidth(probe, font, 1, 0.3f) > w * 0.43f
                    && line.length() > 0) {
                rows.add(line.toString());
                line = new StringBuilder(parts[i]);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) {
            rows.add(line.toString());
        }
        for (int i = 0; i < rows.size(); i++) {
            widest = Math.max(widest,
                    UiBatch.textSpacedWidth(rows.get(i), font, 1, 0.3f));
        }
        return Math.max(w * 0.26f, Math.min(widest + 26f, w * 0.45f));
    }

    /** One line telling the player how this companion joins, for locked cards. */
    private static String whereYouMeet(int index) {
        Companions.Def c = Companions.get(index);
        return "TRAVEL TO " + Elaris.regions()[c.region].name
                + " AND THE ROAD WILL INTRODUCE YOU.";
    }

    private void drawConversation(UiBatch b, AtlasFont font, float alpha,
                                  float w, float h) {
        Companions.Def c = Companions.get(selected);
        if (c == null || world == null) {
            return;
        }
        Companions.Bond bond = world.party.bond(selected);
        Dialogue.Node node = Dialogue.nodeFor(selected, bond);
        if (node == null) {
            return;
        }

        // The speaker, drawn large on the left, with their name beneath. The
        // portrait makes the conversation feel like it is happening to someone.
        float prx = w * 0.175f;
        float pry = h * 0.40f;
        float prr = Math.min(w * 0.115f, h * 0.185f);
        b.glow(prx, pry, prr * 2.4f, 24, 1f, 0.86f, 0.58f, (0.10f + 0.02f * (float) Math.sin(time * 1.1)) * alpha);
        b.ring(prx, pry, prr, prr - 2.0f, 47, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2],
                alpha * 0.85f);
        b.ring(prx, pry, prr - 7f, prr - 8.2f, 47, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha * 0.45f);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI * 0.5 + Math.PI * 0.25 + time * 0.12;
            GenshinKit.star4(b, prx + (float) Math.cos(a) * prr,
                    pry + (float) Math.sin(a) * prr, 4f, 1.3f,
                    Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha * 0.9f);
        }
        b.glow(prx, pry, prr * 0.9f, 16, 1f, 0.90f, 0.66f, 0.30f * alpha);
        drawSigil(b, selected, prx, pry, prr * 0.86f, alpha);

        b.textSpacedCentred(fit(node.speaker, font, w * 0.34f, 1, 2.2f), prx,
                pry + prr + 12f, 1, 2.2f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha, font);
        b.textSpacedCentred(bond.name(), prx, pry + prr + 27f, 1, 1.5f,
                Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2], alpha * 0.85f, font);

        // A rule between the speaker and the words, so the two halves read as
        // one exchange rather than a caption under a picture.
        b.quad(w * 0.355f, h * 0.20f, 1f, h * 0.46f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.30f);

        // What they say, wrapped across the middle. The line is the largest
        // thing on the screen because it is the whole point of being here.
        float tx = w * 0.685f;
        String[] lines = UiBatch.wrap(node.line, font, 1, 0.55f, w * 0.56f);
        float ly = h * 0.245f;
        for (int i = 0; i < lines.length && i < 5; i++) {
            b.textSpacedCentred(lines[i], tx, ly, 1, 0.55f,
                    Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);
            ly += 16f;
        }

        // An aside earned by something the player did, if there is one.
        String aside = asideLine(selected);
        if (aside != null) {
            String[] al = UiBatch.wrap(aside, font, 1, 0.4f, w * 0.54f);
            float ay2 = ly + 12f;
            for (int i = 0; i < al.length && i < 2; i++) {
                b.textSpacedCentred(al[i], tx, ay2, 1, 0.4f,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        alpha * 0.85f, font);
                ay2 += 13f;
            }
        }

        // Fit the two choices together in the conversation column.
        float columnW = w * 0.575f;
        float gap = w * 0.028f;
        float cw0 = node.choices.length > 0 ? choiceWidth(node.choices[0].text, font, w) : 0f;
        float cw1 = node.choices.length > 1 ? choiceWidth(node.choices[1].text, font, w) : 0f;
        if (cw0 + cw1 + gap > columnW) {
            float shrink = (columnW - gap) / (cw0 + cw1);
            cw0 *= shrink;
            cw1 *= shrink;
        }
        float columnX = w * 0.352f + columnW * 0.5f;
        if (node.choices.length > 0) {
            choiceW[0] = cw0;
            choiceX[0] = columnX - gap * 0.5f - cw0 * 0.5f;
        }
        if (node.choices.length > 1) {
            choiceW[1] = cw1;
            choiceX[1] = columnX + gap * 0.5f + cw1 * 0.5f;
        }
        float cy = h * 0.700f;
        if (reply != null && replyFade > 0f) {
            String[] rlines = UiBatch.wrap(reply, font, 1, 0.5f, w * 0.56f);
            float ry = cy - 30f;
            for (int i = 0; i < rlines.length && i < 3; i++) {
                b.textSpacedCentred(rlines[i], tx, ry, 1, 0.5f,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        alpha * Math.min(1f, replyFade), font);
                ry += 14f;
            }
        } else {
            b.textSpacedCentred("CHOOSE", columnX, cy - 26f, 1, 1.8f,
                    Style.CREAM_DIM[0], Style.CREAM_DIM[1], Style.CREAM_DIM[2],
                    alpha * 0.55f, font);
            for (int i = 0; i < node.choices.length && i < 2; i++) {
                Dialogue.Choice ch = node.choices[i];
                float cx = choiceX[i];
                float lw = choiceW[i];
                boolean warm = ch.trust >= 0;
                b.quad(cx - lw * 0.5f, cy - 19f, lw, 38f,
                        warm ? 0.16f : 0.10f, warm ? 0.14f : 0.10f, warm ? 0.09f : 0.11f,
                        (0.80f + choicePress[i] * 0.20f) * alpha);
                b.quad(cx - lw * 0.5f, cy - 19f, lw, 1.4f,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        alpha * 0.75f);
                b.quad(cx - lw * 0.5f, cy + 17.6f, lw, 1.4f,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                        alpha * 0.45f);
                String[] cl = UiBatch.wrap(ch.text, font, 1, 0.3f, lw - 18f);
                float cly = cy - (cl.length > 2 ? 11f : cl.length > 1 ? 5f : 0f);
                for (int k = 0; k < cl.length && k < 3; k++) {
                    b.textSpacedCentred(cl[k], cx, cly, 1, 0.3f,
                            Style.CREAM[0], Style.CREAM[1], Style.CREAM[2], alpha, font);
                    cly += 11f;
                }
            }
        }
    }

    /** The aside a companion has earned from what the player has done. */
    private String asideLine(int companion) {
        if (world == null) {
            return null;
        }
        QuestCorpus.Consequence[] all = QuestCorpus.Consequence.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i] != QuestCorpus.Consequence.NONE && world.quests.has(all[i])) {
                String line = Dialogue.asideFor(companion, all[i]);
                if (line != null) {
                    return line;
                }
            }
        }
        return null;
    }

    @Override
    public void drawTitle(UiBatch b, AtlasFont font, float alpha) {
        GenshinKit.title(b, "THE COMPANY", b.virtualWidth * 0.5f, 8f, font, alpha);
    }

    @Override
    public Action hit(float x, float y, float w, float h, TabBar tabBar) {
        if (world == null) {
            return Action.NONE;
        }
        // Back out of a conversation first.
        if (talking) {
            float cy = h * 0.66f;
            if (reply != null && replyFade > 0f) {
                reply = null;
                replyFade = 0f;
                return Action.NONE;
            }
            for (int i = 0; i < 2; i++) {
                if (Math.abs(x - choiceX[i]) < choiceW[i] * 0.5f
                        && Math.abs(y - cy) < 17f) {
                    choose(i);
                    return Action.NONE;
                }
            }
            if (y > h * 0.86f) {
                talking = false;
                reply = null;
                replyFade = 0f;
            }
            return Action.NONE;
        }

        // Roster rows.
        float rx = listX();
        float ry = listY(h);
        for (int i = 0; i < Companions.count() && i < ROWS; i++) {
            float top = ry + i * rowStep();
            if (x >= rx && x <= rx + w * 0.27f && y >= top && y <= top + 34f) {
                selected = i;
                rowPress[i] = 1f;
                return Action.NONE;
            }
        }

        float px = panelX(w);
        float py = panelY(h);
        float pw = panelW(w);
        float ph = panelH(h);
        boolean here = world.party.recruited[selected];
        if (!here) {
            return Action.NONE;
        }
        float by = py + ph + 22f;
        if (Math.abs(x - (px + pw * 0.28f)) < 60f && Math.abs(y - by) < 18f) {
            talking = true;
            reply = null;
            replyFade = 0f;
            choicePress[0] = 1f;
            return Action.NONE;
        }
        if (Math.abs(x - (px + pw * 0.70f)) < 70f && Math.abs(y - by) < 18f) {
            partyPress = 1f;
            toggleParty();
            return Action.NONE;
        }
        if (Math.abs(x - 30f) < 20f && Math.abs(y - 26f) < 20f) {
            return Action.BACK;
        }
        if (Math.abs(x - (w - 30f)) < 20f && Math.abs(y - 26f) < 20f) {
            return Action.BACK;
        }
        return Action.NONE;
    }

    /** Applies a choice, moves trust, and shows the reply. */
    private void choose(int index) {
        Companions.Bond bond = world.party.bond(selected);
        Dialogue.Node node = Dialogue.nodeFor(selected, bond);
        if (node == null || index >= node.choices.length) {
            return;
        }
        Dialogue.Choice ch = node.choices[index];
        // The conversation counts as the choice: a warm answer is worth trust,
        // a cold one costs it, and the reply says which.
        world.party.converse(selected, ch.trust);
        reply = ch.reply;
        replyFade = 3f;
        choicePress[index] = 1f;
    }

    /** Puts a companion in or out of the travelling pair. */
    private void toggleParty() {
        int a = world.party.active[0];
        int b = world.party.active[1];
        if (a == selected) {
            world.party.setActive(0, b);
            world.party.setActive(1, -1);
            return;
        }
        if (b == selected) {
            world.party.setActive(1, -1);
            return;
        }
        if (a < 0) {
            world.party.setActive(0, selected);
        } else if (b < 0) {
            world.party.setActive(1, selected);
        } else {
            // A full pair: the older companion makes way.
            world.party.setActive(0, b);
            world.party.setActive(1, selected);
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
}
