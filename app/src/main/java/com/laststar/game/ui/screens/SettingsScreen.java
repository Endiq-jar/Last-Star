package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.GenshinKit;
import com.laststar.game.ui.Style;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;

/**
 * Settings page matching the chosen design: five diamond set-tabs, a vertical
 * diamond volume slider on the left and a framed panel of ornate toggles with a
 * parchment dropdown on the right.
 */
public final class SettingsScreen implements Screen {

    public static final String[] SETS = {"I", "II", "III", "IV", "V"};
    private static final String[] DIFFICULTIES = {"EASY", "NORMAL", "HARD"};

    public int activeSet;
    public float masterVolume = 0.70f;
    public boolean audio = true;
    public boolean haptics = false;
    public boolean cameraShake = true;
    public int difficulty = 1;

    public boolean dropdownOpen;
    private int dropdownHover = -1;

    // Animated values.
    private final float[] setPress = new float[5];
    private float audioAnim = 1f;
    private float hapticsAnim;
    private float shakeAnim = 1f;
    private float sliderDrag;
    private boolean draggingSlider;
    private float resetPress;
    private float dropdownPress;

    @Override
    public void update(float dt) {
        for (int i = 0; i < setPress.length; i++) {
            if (setPress[i] > 0f) {
                setPress[i] = Math.max(0f, setPress[i] - dt * 3.5f);
            }
        }
        audioAnim = approach(audioAnim, audio ? 1f : 0f, dt * 7f);
        hapticsAnim = approach(hapticsAnim, haptics ? 1f : 0f, dt * 7f);
        shakeAnim = approach(shakeAnim, cameraShake ? 1f : 0f, dt * 7f);
        if (resetPress > 0f) {
            resetPress = Math.max(0f, resetPress - dt * 3f);
        }
        if (dropdownPress > 0f) {
            dropdownPress = Math.max(0f, dropdownPress - dt * 3f);
        }
        if (!draggingSlider && sliderDrag > 0f) {
            sliderDrag = Math.max(0f, sliderDrag - dt * 3f);
        }
    }

    private static float approach(float v, float target, float step) {
        if (v < target) {
            return Math.min(target, v + step);
        }
        return Math.max(target, v - step);
    }

    // ------------------------------------------------------------ geometry

    private float sliderTop(float h) {
        return panelY(h) + 34f;
    }

    private float sliderBottom(float h) {
        return panelY(h) + panelH(h) - 30f;
    }

    private float panelX(float w) {
        return w * 0.135f;
    }

    private float panelW(float w) {
        return w * 0.700f;
    }

    /** Slider column, inside the panel's left edge. */
    private float sliderX(float w) {
        return panelX(w) + 54f;
    }

    /** Where the labelled rows begin, to the right of the slider column. */
    private float rowsX(float w) {
        return panelX(w) + 132f;
    }

    private float rowsW(float w) {
        return panelW(w) - 132f - 26f;
    }

    private float panelY(float h) {
        return h * 0.245f;
    }

    private float panelH(float h) {
        return h * 0.400f;
    }

    private float rowY(float h, int row) {
        float y = panelY(h) + 30f;
        return y + row * (panelH(h) - 52f) / 3.4f;
    }

    private float dropdownY(float h) {
        return rowY(h, 3);
    }

    @Override
    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        // ---- set tabs: roman numerals in diamonds ----
        float tabY = h * 0.172f;
        for (int i = 0; i < SETS.length; i++) {
            float cx = w * 0.315f + i * w * 0.105f;
            boolean active = i == activeSet;
            float scale = active ? 1.06f : 0.92f;
            GenshinKit.diamondTab(b, cx, tabY, 23f * scale, active, setPress[i], alpha);
            b.textSpacedCentred(SETS[i], cx, tabY - font.capHeight(1) * 0.5f, 1, 1.2f,
                    active ? Style.INK[0] : Style.CREAM[0],
                    active ? Style.INK[1] : Style.CREAM[1],
                    active ? Style.INK[2] : Style.CREAM[2], alpha, font);
        }

        // ---- panel ----
        float px = panelX(w);
        float py = panelY(h);
        float pw = panelW(w);
        float ph = panelH(h);
        GenshinKit.panel(b, px, py, pw, ph, alpha);

        // ---- volume slider: left column inside the panel ----
        float sx = sliderX(w);
        float top = sliderTop(h);
        float bottom = sliderBottom(h);
        b.textSpacedCentred("VOLUME", sx, py + 12f, 1, 0.6f,
                Style.CREAM[0], Style.CREAM[1], Style.CREAM[2], alpha, font);
        GenshinKit.vslider(b, sx, top, bottom, masterVolume, alpha);
        b.textSpacedCentred((int) (masterVolume * 100f) + "%", sx, bottom + 8f, 1, 0.8f,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);
        b.quad(rowsX(w) - 16f, py + 18f, 1f, ph - 36f,
                Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha * 0.35f);

        // ---- toggles ----
        String[] labels = {"AUDIO", "HAPTICS", "CAMERA SHAKE", "DIFFICULTY"};
        float labelX = rowsX(w);
        float toggleW = 50f;
        float toggleH = 20f;
        float tx = px + pw - toggleW - 26f;
        for (int i = 0; i < 4; i++) {
            float ry = rowY(h, i);
            b.textSpaced(labels[i], labelX, ry - font.capHeight(1) * 0.5f, 1, 1.6f,
                    Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);
            if (i < 3) {
                GenshinKit.divider(b, labelX - 2f, px + pw - 22f, ry + 15f, alpha * 0.8f);
            }
        }
        GenshinKit.toggle(b, tx, rowY(h, 0) - toggleH * 0.5f, toggleW, toggleH, audio,
                audioAnim, alpha);
        GenshinKit.toggle(b, tx, rowY(h, 1) - toggleH * 0.5f, toggleW, toggleH, haptics,
                hapticsAnim, alpha);
        GenshinKit.toggle(b, tx, rowY(h, 2) - toggleH * 0.5f, toggleW, toggleH, cameraShake,
                shakeAnim, alpha);

        // ---- difficulty dropdown + reset on the last row ----
        float row3 = rowY(h, 3);
        float dw = 104f;
        float dh = 24f;
        float dx = px + pw - 26f - 96f - dw;
        float dy = row3 - dh * 0.5f;
        GenshinKit.parchment(b, dx, dy, dw, dh, (0.95f + dropdownPress * 0.05f) * alpha);
        b.textSpacedCentred(DIFFICULTIES[difficulty], dx + dw * 0.5f - 6f,
                dy + dh * 0.5f - font.capHeight(1) * 0.5f, 1, 1.0f,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha, font);
        GenshinKit.chevron(b, dx + dw - 13f, dy + dh * 0.5f, 7f, 2,
                Style.INK[0], Style.INK[1], Style.INK[2], alpha * 0.9f);

        float rw = 96f;
        float rh = 26f;
        float rcx = px + pw - 26f - rw * 0.5f;
        GenshinKit.lozenge(b, rcx, row3, rw, rh, false, resetPress, alpha);
        b.textSpacedCentred("RESET", rcx, row3 - font.capHeight(1) * 0.5f, 1, 2.0f,
                Style.IVORY[0], Style.IVORY[1], Style.IVORY[2], alpha, font);

        if (dropdownOpen) {
            float mw = dw;
            float mh = DIFFICULTIES.length * 18f + 6f;
            float mx = dx;
            float my = dy + dh + 3f;
            GenshinKit.parchment(b, mx, my, mw, mh, alpha);
            for (int i = 0; i < DIFFICULTIES.length; i++) {
                float rowTop = my + 3f + i * 18f;
                if (i < DIFFICULTIES.length - 1) {
                    b.quad(mx + 6f, rowTop + 18f - 0.5f, mw - 12f, 1f,
                            Style.PARCHMENT_DK[0], Style.PARCHMENT_DK[1], Style.PARCHMENT_DK[2],
                            alpha * 0.7f);
                }
                if (i == dropdownHover) {
                    b.quad(mx + 2f, rowTop, mw - 4f, 18f, 1f, 1f, 1f, alpha * 0.16f);
                }
                boolean selected = i == difficulty;
                float[] c = selected ? Style.TEAL : Style.INK;
                b.textSpaced(DIFFICULTIES[i], mx + 10f, rowTop + 4f, 1, 1.0f,
                        c[0], c[1], c[2], alpha, font);
                if (selected) {
                    GenshinKit.check(b, mx + mw - 16f, rowTop + 9f, 9f,
                            Style.INK[0], Style.INK[1], Style.INK[2], alpha);
                }
            }
        }
    }

    @Override
    public void drawTitle(UiBatch b, AtlasFont font, float alpha) {
        GenshinKit.title(b, "SETTINGS", b.virtualWidth * 0.5f, 12f, font, alpha);
    }

    @Override
    public Action hit(float x, float y, float w, float h, TabBar tabBar) {
        float px = panelX(w);
        float pw = panelW(w);
        float py = panelY(h);

        // Dropdown menu first (it floats above the panel).
        if (dropdownOpen) {
            float dw = 104f;
            float dh = 24f;
            float dx = px + pw - 26f - 96f - dw;
            float dy = rowY(h, 3) - dh * 0.5f;
            float my = dy + dh + 3f;
            if (x >= dx && x <= dx + dw && y >= my && y < my + DIFFICULTIES.length * 18f + 6f) {
                int index = (int) ((y - my - 3f) / 18f);
                if (index >= 0 && index < DIFFICULTIES.length) {
                    difficulty = index;
                    dropdownOpen = false;
                    return Action.NONE;
                }
            }
            dropdownOpen = false;
        }

        // Set tabs.
        float tabY = h * 0.172f;
        for (int i = 0; i < SETS.length; i++) {
            float cx = w * 0.315f + i * w * 0.105f;
            if (Math.abs(x - cx) < 26f && Math.abs(y - tabY) < 26f) {
                activeSet = i;
                setPress[i] = 1f;
                return Action.NONE;
            }
        }

        // Volume slider: grab anywhere along the column.
        float sx = sliderX(w);
        if (Math.abs(x - sx) < 34f && y > sliderTop(h) - 30f && y < sliderBottom(h) + 30f) {
            draggingSlider = true;
            sliderDrag = 1f;
            masterVolume = clamp01((sliderBottom(h) - y) / (sliderBottom(h) - sliderTop(h)));
            return Action.NONE;
        }

        // Toggles.
        float toggleW = 50f;
        float tx = px + pw - toggleW - 26f;
        for (int i = 0; i < 3; i++) {
            float ry = rowY(h, i);
            if (x >= tx - 8f && x <= tx + toggleW + 8f && Math.abs(y - ry) < 16f) {
                if (i == 0) {
                    audio = !audio;
                } else if (i == 1) {
                    haptics = !haptics;
                } else {
                    cameraShake = !cameraShake;
                }
                return Action.NONE;
            }
        }

        // Difficulty dropdown.
        float row3 = rowY(h, 3);
        float dw = 104f;
        float dh = 24f;
        float dx = px + pw - 26f - 96f - dw;
        float dy = row3 - dh * 0.5f;
        if (x >= dx && x <= dx + dw && y >= dy && y <= dy + dh) {
            dropdownOpen = true;
            dropdownPress = 1f;
            return Action.NONE;
        }

        // Reset.
        float rcx = px + pw - 26f - 48f;
        if (Math.abs(x - rcx) < 50f && Math.abs(y - row3) < 16f) {
            audio = true;
            haptics = false;
            cameraShake = true;
            difficulty = 1;
            masterVolume = 0.70f;
            resetPress = 1f;
            return Action.NONE;
        }
        if (py > h) {
            return Action.NONE;
        }
        return Action.NONE;
    }

    /** Called on release so slider dragging stops. */
    public void release() {
        draggingSlider = false;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
