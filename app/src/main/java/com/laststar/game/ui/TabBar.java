package com.laststar.game.ui;

/**
 * Bottom navigation bar in the ornate style: a translucent slate strip with a
 * hairline gold top border, five delicate gold line icons over small-cap labels,
 * and a gold diamond marking the active tab.
 */
public final class TabBar {

    public enum Tab {
        HOME("Home"),
        ABOUT("About"),
        SHOP("Shop"),
        QUESTS("Quests"),
        WARDROBE("Wardrobe"),
        SETTINGS("Settings");

        public final String label;

        Tab(String label) {
            this.label = label;
        }

        /** The menu page this tab opens. Mirrors {@code Menu}'s routing. */
        public com.laststar.game.ui.Menu.Page pageForTest() {
            switch (this) {
                case ABOUT:
                    return com.laststar.game.ui.Menu.Page.ABOUT;
                case SHOP:
                    return com.laststar.game.ui.Menu.Page.SHOP;
                case QUESTS:
                    return com.laststar.game.ui.Menu.Page.QUESTS;
                case WARDROBE:
                    return com.laststar.game.ui.Menu.Page.WARDROBE;
                case SETTINGS:
                    return com.laststar.game.ui.Menu.Page.SETTINGS;
                case HOME:
                default:
                    return com.laststar.game.ui.Menu.Page.HOME;
            }
        }
    }

    public static final float HEIGHT = 46f;

    /** Currently selected tab. */
    public Tab active = Tab.HOME;
    /** Per-tab press animation. */
    private final float[] press = new float[Tab.values().length];

    public void setActive(Tab tab) {
        active = tab;
    }

    public float height() {
        return HEIGHT;
    }

    public float tabX(int index, float width) {
        float usable = width - 48f;
        return 24f + usable * (index + 0.5f) / Tab.values().length;
    }

    public int hitTest(float x, float y, float width, float screenHeight) {
        float top = screenHeight - HEIGHT;
        if (y < top) {
            return -1;
        }
        for (int i = 0; i < Tab.values().length; i++) {
            float cx = tabX(i, width);
            if (Math.abs(x - cx) < 50f) {
                return i;
            }
        }
        return -1;
    }

    /** Tab at an index, or HOME when the index is out of range. */
    public Tab tabAt(int index) {
        Tab[] all = Tab.values();
        if (index < 0 || index >= all.length) {
            return Tab.HOME;
        }
        return all[index];
    }

    public void press(int index) {
        if (index >= 0 && index < press.length) {
            press[index] = 1f;
        }
    }

    public void update(float dt) {
        for (int i = 0; i < press.length; i++) {
            if (press[i] > 0f) {
                press[i] = Math.max(0f, press[i] - dt * 3.5f);
            }
        }
    }

    public void draw(UiBatch b, AtlasFont font, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;
        float top = h - HEIGHT;

        // Panel.
        b.quadGradient(0f, top, w, HEIGHT,
                0.043f, 0.055f, 0.086f, 0.92f * alpha,
                0.024f, 0.031f, 0.051f, 0.96f * alpha);
        // Gold hairline on top plus a bright inner line.
        b.quad(0f, top, w, 1.6f, Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], alpha);
        b.quad(0f, top + 1.6f, w, 1f, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                Style.GOLD_LIGHT[2], 0.55f * alpha);

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            float cx = tabX(i, w);
            boolean isActive = tabs[i] == active;
            float p = press[i];
            float iconY = top + 14f;
            float labelY = top + 26f;

            if (isActive) {
                // Soft gold halo behind the active item.
                b.glow(cx, iconY, 34f, 16, Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1],
                        Style.GOLD_LIGHT[2], 0.20f * alpha);
                GenshinKit.star4(b, cx, top + 3.4f, 4.4f, 1.5f,
                        Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha);
            }

            float[] col = isActive ? Style.IVORY : Style.CREAM_DIM;
            float iconAlpha = isActive ? alpha : alpha * 0.78f;
            icon(b, tabs[i], cx, iconY + p * 1.2f, 8.6f, col, iconAlpha);

            // Labels are sized to the slot so six tabs never collide.
            String label = tabs[i].label.toUpperCase();
            float slot = (w - 48f) / Tab.values().length - 6f;
            int scale = 1;
            float track = 0.2f;
            while (scale > 0) {
                float width = UiBatch.textSpacedWidth(label, font, scale, track);
                if (width <= slot || scale == 1) {
                    break;
                }
                scale--;
            }
            if (UiBatch.textSpacedWidth(label, font, scale, track) > slot) {
                track = 0f;
            }
            b.textSpacedCentred(label, cx, labelY, scale, track,
                    col[0], col[1], col[2], isActive ? alpha : alpha * 0.85f, font);

            // Thin separators between items.
            if (i < tabs.length - 1) {
                float sx = (cx + tabX(i + 1, w)) * 0.5f;
                b.quad(sx, top + 8f, 1f, HEIGHT - 18f,
                        Style.GOLD[0], Style.GOLD[1], Style.GOLD[2], 0.22f * alpha);
            }
        }
        // Centre diamond under the bar.
        GenshinKit.diamond(b, w * 0.5f, top + HEIGHT - 3.5f, 3.2f,
                Style.GOLD_LIGHT[0], Style.GOLD_LIGHT[1], Style.GOLD_LIGHT[2], alpha * 0.8f);
    }

    private void icon(UiBatch b, Tab tab, float cx, float cy, float s,
                      float[] c, float a) {
        float t = 1.8f;
        switch (tab) {
            case HOME:
                b.line(cx, cy - s * 0.70f, cx - s * 0.34f, cy + s * 0.42f, t, c[0], c[1], c[2], a);
                b.line(cx, cy - s * 0.70f, cx + s * 0.34f, cy + s * 0.42f, t, c[0], c[1], c[2], a);
                b.line(cx - s * 0.34f, cy + s * 0.42f, cx + s * 0.34f, cy + s * 0.42f, t,
                        c[0], c[1], c[2], a);
                b.line(cx, cy - s * 0.70f, cx, cy + s * 0.06f, t, c[0], c[1], c[2], a);
                b.line(cx - s * 0.34f, cy + s * 0.42f, cx, cy + s * 0.10f, 1.3f,
                        c[0], c[1], c[2], a * 0.8f);
                b.line(cx + s * 0.34f, cy + s * 0.42f, cx, cy + s * 0.10f, 1.3f,
                        c[0], c[1], c[2], a * 0.8f);
                break;
            case ABOUT:
                b.ring(cx, cy, s * 0.85f, s * 0.85f - t, 16, c[0], c[1], c[2], a);
                b.quad(cx - 0.9f, cy - s * 0.42f, 1.8f, s * 0.22f, c[0], c[1], c[2], a);
                b.circle(cx, cy + s * 0.36f, 1.1f, 6, c[0], c[1], c[2], a);
                break;
            case SHOP:
                b.line(cx - s * 0.62f, cy + s * 0.72f, cx + s * 0.62f, cy + s * 0.72f, t,
                        c[0], c[1], c[2], a);
                b.line(cx - s * 0.55f, cy + s * 0.72f, cx - s * 0.40f, cy - s * 0.10f, t,
                        c[0], c[1], c[2], a);
                b.line(cx + s * 0.55f, cy + s * 0.72f, cx + s * 0.40f, cy - s * 0.10f, t,
                        c[0], c[1], c[2], a);
                b.ring(cx, cy - s * 0.34f, s * 0.30f, s * 0.30f - t, 12,
                        c[0], c[1], c[2], a);
                break;
            case QUESTS:
                b.quad(cx - s * 0.58f, cy - s * 0.76f, s * 1.16f, s * 1.52f,
                        c[0], c[1], c[2], a * 0.22f);
                b.line(cx - s * 0.58f, cy - s * 0.76f, cx + s * 0.58f, cy - s * 0.76f, t,
                        c[0], c[1], c[2], a);
                b.line(cx - s * 0.58f, cy + s * 0.76f, cx + s * 0.58f, cy + s * 0.76f, t,
                        c[0], c[1], c[2], a);
                b.line(cx - s * 0.58f, cy - s * 0.76f, cx - s * 0.58f, cy + s * 0.76f, t,
                        c[0], c[1], c[2], a);
                b.line(cx + s * 0.58f, cy - s * 0.76f, cx + s * 0.58f, cy + s * 0.76f, t,
                        c[0], c[1], c[2], a);
                for (int i = 0; i < 3; i++) {
                    b.line(cx - s * 0.30f, cy - s * 0.34f + i * s * 0.34f,
                            cx + s * 0.34f, cy - s * 0.34f + i * s * 0.34f, 1.4f,
                            c[0], c[1], c[2], a * 0.85f);
                }
                break;
            case WARDROBE:
                // Robe on a hanger.
                b.line(cx - s * 0.62f, cy - s * 0.30f, cx, cy - s * 0.62f, t, c[0], c[1], c[2], a);
                b.line(cx + s * 0.62f, cy - s * 0.30f, cx, cy - s * 0.62f, t, c[0], c[1], c[2], a);
                b.ring(cx, cy - s * 0.72f, s * 0.12f, s * 0.12f - 1.3f, 10,
                        c[0], c[1], c[2], a);
                b.polygon(new float[]{
                        cx - s * 0.62f, cy - s * 0.30f,
                        cx + s * 0.62f, cy - s * 0.30f,
                        cx + s * 0.42f, cy + s * 0.72f,
                        cx - s * 0.42f, cy + s * 0.72f
                }, c[0], c[1], c[2], a * 0.26f);
                b.line(cx - s * 0.62f, cy - s * 0.30f, cx - s * 0.42f, cy + s * 0.72f, t,
                        c[0], c[1], c[2], a);
                b.line(cx + s * 0.62f, cy - s * 0.30f, cx + s * 0.42f, cy + s * 0.72f, t,
                        c[0], c[1], c[2], a);
                b.line(cx - s * 0.42f, cy + s * 0.72f, cx + s * 0.42f, cy + s * 0.72f, t,
                        c[0], c[1], c[2], a);
                break;
            case SETTINGS:
            default:
                b.ring(cx, cy, s * 0.46f, s * 0.46f - t, 12, c[0], c[1], c[2], a);
                for (int i = 0; i < 8; i++) {
                    float ang = (float) (i * Math.PI / 4.0);
                    float x0 = cx + (float) Math.cos(ang) * s * 0.52f;
                    float y0 = cy + (float) Math.sin(ang) * s * 0.52f;
                    float x1 = cx + (float) Math.cos(ang) * s * 0.80f;
                    float y1 = cy + (float) Math.sin(ang) * s * 0.80f;
                    b.line(x0, y0, x1, y1, 2.2f, c[0], c[1], c[2], a);
                }
                break;
        }
    }
}
