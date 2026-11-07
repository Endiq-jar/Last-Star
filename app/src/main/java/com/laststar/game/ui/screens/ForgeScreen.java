package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.GenshinKit;
import com.laststar.game.ui.Style;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;
import com.laststar.game.world.World;
import com.laststar.game.world.lore.SpellCorpus;
import com.laststar.game.world.lore.SpellForge;

/**
 * The forge: spell creation as an experiment rather than a menu.
 *
 * <p>The design document's instruction is that crafting must not be a list of
 * results to buy. So this page offers no spells. It offers <em>dials</em> --
 * element, shape, behaviour, range, area, power -- and shows the consequences
 * of the current setting live: what the spell will be called, what it will cost
 * in Light, how hard it will hit and how often. Nothing is granted until the
 * player commits, and the same three taps can produce a fast cheap lance or a
 * wide expensive field.</p>
 *
 * <p>The dials are laid out so their interaction is visible: raising power
 * visibly lengthens the cooldown bar and the Light cost, which is the trade the
 * player is actually making.</p>
 */
public final class ForgeScreen implements Screen {

    /** The three rows of element, shape and behaviour choices. */
    private static final SpellForge.Element[] ELEMENTS = SpellForge.Element.values();
    private static final SpellCorpus.Shape[] SHAPES = {
            SpellCorpus.Shape.BOLT, SpellCorpus.Shape.SWEEP, SpellCorpus.Shape.WARD,
            SpellCorpus.Shape.STARFALL, SpellCorpus.Shape.FIELD, SpellCorpus.Shape.DASH,
            SpellCorpus.Shape.BEAM, SpellCorpus.Shape.TRAP, SpellCorpus.Shape.SUMMON
    };
    private static final SpellForge.Behaviour[] BEHAVIOURS = SpellForge.Behaviour.values();

    /** Which dial row a tap is adjusting. */
    private static final int ROW_ELEMENT = 0;
    private static final int ROW_SHAPE = 1;
    private static final int ROW_BEHAVIOUR = 2;
    private static final int ROW_RANGE = 3;
    private static final int ROW_AREA = 4;
    private static final int ROW_POWER = 5;
    private static final int ROWS = 6;

    private final SpellForge.Recipe recipe = new SpellForge.Recipe();
    private final float[] rowPress = new float[ROWS];
    private float forgePress;
    private float time;

    private World world;
    /** The last thing forged, shown in the result line. */
    private String resultLine = "";
    private float resultFade;

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
        if (forgePress > 0f) {
            forgePress = Math.max(0f, forgePress - dt * 3f);
        }
        if (resultFade > 0f) {
            resultFade = Math.max(0f, resultFade - dt * 0.5f);
        }
    }

    // ------------------------------------------------------------ geometry

    private static float rowY(float h, int row) {
        return h * 0.215f + row * 30f;
    }

    private static float optionX(float w, int i, int count) {
        // Options are spread across the left two-thirds; the preview panel
        // takes the rest.
        float left = w * 0.045f;
        float span = w * 0.585f;
        float cell = span / count;
        return left + cell * (i + 0.5f);
    }

    private static float optionW(float w, int count) {
        return (w * 0.585f) / count - 5f;
    }

    private static float sliderX(float w) {
        return w * 0.045f;
    }

    private static float sliderW(float w) {
        return w * 0.585f;
    }

    /** Which of the six rows a y coordinate falls in, or -1. */
    private static int rowAt(float h, float y) {
        for (int r = 0; r < ROWS; r++) {
            float ry = rowY(h, r);
            if (y >= ry - 12f && y <= ry + 12f) {
                return r;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------ drawing

    @Override
    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        drawOptionRow(b, font, alpha, w, h, ROW_ELEMENT, "ELEMENT", elementLabels(),
                recipe.element.ordinal(), ELEMENTS.length);
        drawOptionRow(b, font, alpha, w, h, ROW_SHAPE, "SHAPE", shapeLabels(),
                shapeIndex(), SHAPES.length);
        drawOptionRow(b, font, alpha, w, h, ROW_BEHAVIOUR, "EFFECT",
                behaviourLabels(), recipe.behaviour.ordinal(), BEHAVIOURS.length);

        drawSlider(b, font, alpha, w, h, ROW_RANGE, "RANGE", recipe.range);
        drawSlider(b, font, alpha, w, h, ROW_AREA, "AREA", recipe.area);
        drawSlider(b, font, alpha, w, h, ROW_POWER, "POWER", recipe.power);

        drawPreview(b, font, alpha, w, h);

        // Back / close.
        GenshinKit.roundButton(b, 30f, 26f, 15f, 0f, alpha);
        GenshinKit.chevron(b, 30f, 26f, 9f, -1, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
        GenshinKit.roundButton(b, w - 30f, 26f, 15f, 0f, alpha);
        b.line(w - 35f, 21f, w - 25f, 31f, 2.2f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
        b.line(w - 25f, 21f, w - 35f, 31f, 2.2f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], alpha);
    }

    /** Labels for the element row, shortened to fit. */
    private static String[] elementLabels() {
        String[] out = new String[ELEMENTS.length];
        for (int i = 0; i < ELEMENTS.length; i++) {
            out[i] = ELEMENTS[i].label;
        }
        return out;
    }

    private static String[] shapeLabels() {
        String[] out = new String[SHAPES.length];
        for (int i = 0; i < SHAPES.length; i++) {
            out[i] = SHAPES[i].name();
        }
        return out;
    }

    private static String[] behaviourLabels() {
        String[] out = new String[BEHAVIOURS.length];
        for (int i = 0; i < BEHAVIOURS.length; i++) {
            out[i] = BEHAVIOURS[i].label;
        }
        return out;
    }

    private int shapeIndex() {
        for (int i = 0; i < SHAPES.length; i++) {
            if (SHAPES[i] == recipe.shape) {
                return i;
            }
        }
        return 0;
    }

    /**
     * A single choice, shown in full, with a stepper either side.
     *
     * <p>Nine elements laid out side by side leaves four characters per label,
     * which is a row of abbreviations rather than a choice. One value at full
     * size, stepped through with arrows, is slower to scan and faster to read,
     * and it is the right trade for a screen the player visits a handful of
     * times rather than a menu they live in.</p>
     */
    private void drawOptionRow(UiBatch b, AtlasFont font, float alpha, float w, float h,
                               int row, String label, String[] options,
                               int selected, int count) {
        float y = rowY(h, row);
        b.textSpaced(label, sliderX(w), y - font.capHeight(1) * 0.5f, 1, 1.6f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                alpha * 0.85f, font);

        float sx = sliderX(w) + 104f;
        float sw = sliderW(w) - 104f;
        b.quad(sx, y - 11f, sw, 22f, 0.06f, 0.07f, 0.10f, alpha * 0.8f);
        b.quad(sx, y - 11f, sw, 1f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2],
                alpha * 0.45f);
        b.quad(sx, y + 10f, sw, 1f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2],
                alpha * 0.45f);

        // Arrows at each end.
        float arrowInset = 14f;
        GenshinKit.chevron(b, sx + arrowInset, y, 6f, -1,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                alpha * 0.9f);
        GenshinKit.chevron(b, sx + sw - arrowInset, y, 6f, 1,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                alpha * 0.9f);

        String text = options[Math.max(0, Math.min(count - 1, selected))];
        b.textSpacedCentred(fit(text, font, sw - 56f, 1, 1.4f), sx + sw * 0.5f,
                y - font.capHeight(1) * 0.5f, 1, 1.4f,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);

        // A row of pips underneath, so the player knows how far through the
        // list they are without having to count.
        float pipW = 4f;
        float gap = 4f;
        float total = count * (pipW + gap) - gap;
        float pipX = sx + sw * 0.5f - total * 0.5f;
        for (int i = 0; i < count; i++) {
            boolean on = i == selected;
            b.quad(pipX + i * (pipW + gap), y + 14f, pipW, 2f,
                    Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                    alpha * (on ? 0.95f : 0.30f));
        }
    }

    private void drawSlider(UiBatch b, AtlasFont font, float alpha, float w, float h,
                            int row, String label, float value) {
        float y = rowY(h, row);
        b.textSpaced(label, sliderX(w), y - 9f, 1, 1.6f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                alpha * 0.85f, font);
        float sx = sliderX(w) + 78f;
        float sw = sliderW(w) - 78f;
        b.quad(sx, y - 3f, sw, 6f, 0.06f, 0.07f, 0.10f, alpha * 0.8f);
        b.quad(sx, y - 3f, sw * value, 6f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2],
                alpha * 0.9f);
        b.circle(sx + sw * value, y, 6.5f, 18,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
        b.circle(sx + sw * value, y, 4f, 14, 0.98f, 0.94f, 0.84f, alpha);
    }

    /** The right-hand panel: what this recipe actually is. */
    private void drawPreview(UiBatch b, AtlasFont font, float alpha, float w, float h) {
        float px = w * 0.645f;
        float py = h * 0.215f;
        float pw = w * 0.325f;
        float ph = h * 0.545f;

        b.quadGradient(px, py, pw, ph,
                Style.PARCHMENT[0], Style.PARCHMENT[1], Style.PARCHMENT[2], 0.97f * alpha,
                Style.PARCHMENT_DK[0], Style.PARCHMENT_DK[1], Style.PARCHMENT_DK[2],
                0.99f * alpha);
        b.quad(px, py, pw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px, py + ph - 3f, pw, 3f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px, py, 3f, ph, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(px + pw - 3f, py, 3f, ph, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);

        float mx = px + pw * 0.5f;
        float my = py + 30f;

        // The element's colour, so the recipe reads at a glance.
        SpellForge.Element e = recipe.element;
        b.glow(mx, my, 44f, 18, e.r, e.g, e.b, 0.30f * alpha);
        b.circle(mx, my, 15f, 20, e.r, e.g, e.b, alpha);
        b.circle(mx, my, 11.5f, 18, 0.95f, 0.90f, 0.78f, alpha);

        String name = recipe.name();
        String[] nameLines = UiBatch.wrap(name, font, 1, 0.9f, pw - 30f);
        float ny = my + 22f;
        for (int i = 0; i < nameLines.length && i < 2; i++) {
            b.textSpacedCentred(nameLines[i], mx, ny, 1, 0.9f,
                    Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
            ny += 12f;
        }

        // The numbers that decide whether it is worth casting.
        float ly = ny + 12f;
        drawStat(b, font, alpha, px + 18f, ly, "LIGHT", fmt(SpellForge.lightCost(recipe)));
        drawStat(b, font, alpha, px + pw * 0.5f, ly, "DAMAGE",
                String.valueOf(Math.round(SpellForge.baseDamage(recipe))));
        ly += 26f;
        drawStat(b, font, alpha, px + 18f, ly, "RANGE",
                Math.round(SpellForge.range(recipe)) + "M");
        drawStat(b, font, alpha, px + pw * 0.5f, ly, "COOLDOWN",
                fmt(SpellForge.cooldown(recipe)) + "S");

        // What the behaviour will do at mastery ten, which is the reason to
        // pick one at all.
        if (recipe.behaviour != SpellForge.Behaviour.NONE) {
            String note = "AT 10: " + recipe.behaviour.label;
            b.textSpacedCentred(fit(note, font, pw - 24f, 1, 0.4f), mx, ly + 26f, 1, 0.4f,
                    Style.INK[0] * 0.85f, Style.INK[1] * 0.85f, Style.INK[2] * 0.85f,
                    alpha, font);
        }

        // The forge button.
        float bx = mx;
        float by = py + ph + 22f;
        GenshinKit.lozenge(b, bx, by, 140f, 28f, true, forgePress, alpha);
        b.textSpacedCentred("FORGE", bx, by - font.capHeight(1) * 0.5f, 1, 4f,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);

        if (resultFade > 0f && !resultLine.isEmpty()) {
            b.textSpacedCentred(fit(resultLine, font, w * 0.9f, 1, 1.5f),
                    w * 0.5f, h * 0.905f, 1, 1.5f,
                    1f, 0.92f, 0.70f, alpha * Math.min(1f, resultFade), font);
        }
    }

    private static void drawStat(UiBatch b, AtlasFont font, float alpha,
                                 float x, float y, String label, String value) {
        b.textSpaced(label, x, y, 1, 0.8f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.55f, font);
        b.textSpaced(value, x, y + 11f, 1, 0.8f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    // ------------------------------------------------------------ input

    @Override
    public void drawTitle(UiBatch b, AtlasFont font, float alpha) {
        GenshinKit.title(b, "THE FORGE", b.virtualWidth * 0.5f, 8f, font, alpha);
    }

    @Override
    public Action hit(float x, float y, float w, float h, TabBar tabBar) {
        int row = rowAt(h, y);
        if (row >= 0 && x >= sliderX(w) && x <= sliderX(w) + sliderW(w)) {
            boolean back = x < sliderX(w) + 92f + (sliderW(w) - 92f) * 0.5f;
            switch (row) {
                case ROW_ELEMENT:
                    recipe.element = step(ELEMENTS, recipe.element, back);
                    return Action.NONE;
                case ROW_SHAPE:
                    recipe.shape = step(SHAPES, recipe.shape, back);
                    return Action.NONE;
                case ROW_BEHAVIOUR:
                    recipe.behaviour = step(BEHAVIOURS, recipe.behaviour, back);
                    return Action.NONE;
                case ROW_RANGE:
                    recipe.range = sliderValue(x, w);
                    rowPress[row] = 1f;
                    return Action.NONE;
                case ROW_AREA:
                    recipe.area = sliderValue(x, w);
                    rowPress[row] = 1f;
                    return Action.NONE;
                case ROW_POWER:
                    recipe.power = sliderValue(x, w);
                    rowPress[row] = 1f;
                    return Action.NONE;
                default:
                    break;
            }
        }

        // The forge button.
        float bx = w * 0.645f + w * 0.325f * 0.5f;
        float by = h * 0.215f + h * 0.545f + 22f;
        if (Math.abs(x - bx) < 78f && Math.abs(y - by) < 20f) {
            forgePress = 1f;
            commit();
            return Action.NONE;
        }

        if (Math.abs(x - 30f) < 20f && Math.abs(y - 26f) < 20f) {
            return Action.OPEN_SPELLS;
        }
        if (Math.abs(x - (w - 30f)) < 20f && Math.abs(y - 26f) < 20f) {
            return Action.BACK;
        }
        return Action.NONE;
    }

    /** Steps a value one place through its list, wrapping at both ends. */
    private static <T> T step(T[] values, T current, boolean backwards) {
        int i = 0;
        for (int n = 0; n < values.length; n++) {
            if (values[n] == current) {
                i = n;
                break;
            }
        }
        i = backwards ? (i + values.length - 1) % values.length
                : (i + 1) % values.length;
        return values[i];
    }

    private static float sliderValue(float x, float w) {
        float sx = w * 0.045f + 78f;
        float sw = w * 0.585f - 78f;
        return Math.max(0f, Math.min(1f, (x - sx) / sw));
    }

    /** Commits the recipe to the book. */
    private void commit() {
        if (world == null) {
            resultLine = "NOTHING HAPPENS.";
            resultFade = 2f;
            return;
        }
        SpellCorpus.Def made = SpellForge.forge(recipe);
        int index = world.spells.addForgedPage(made);
        if (index < 0) {
            resultLine = "THE WANDER ALREADY REMEMBERS THAT ONE.";
        } else {
            resultLine = made.name + " JOINED THE WANDER.";
            world.spells.pages[index].awake = true;
        }
        resultFade = 4f;
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
