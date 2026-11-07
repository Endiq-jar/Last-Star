package com.laststar.game.ui;

import com.laststar.game.core.Noise;

/**
 * Procedurally painted menu backdrops — the painterly night vistas behind the
 * menus. Each screen gets its own mood (citadel, close-up bokeh, library hall,
 * wardrobe hall, merchant alcove) built from layered gradient bands, ridgelines,
 * light shafts, window silhouettes and drifting motes.
 */
public final class Backdrop {

    public enum Id {
        HOME, SETTINGS, QUESTS, WARDROBE, SHOP
    }

    private final Noise noise = new Noise(20261105L);
    private float drift;

    public void update(float dt) {
        drift += dt;
    }

    public void draw(UiBatch b, Id id, float alpha) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;
        switch (id) {
            case SETTINGS:
                drawBokeh(b, w, h, alpha);
                break;
            case QUESTS:
                drawHall(b, w, h, alpha, true);
                break;
            case WARDROBE:
                drawHall(b, w, h, alpha, false);
                break;
            case SHOP:
                drawAlcove(b, w, h, alpha);
                break;
            case HOME:
            default:
                drawCitadel(b, w, h, alpha);
                break;
        }
        drawMotes(b, w, h, alpha);
        drawVignette(b, w, h, alpha);
    }

    // ------------------------------------------------------------- citadel

    private void drawCitadel(UiBatch b, float w, float h, float a) {
        // Sky: deep navy at the top, teal towards the horizon, warm amber low left.
        b.quadGradient(0f, 0f, w, h * 0.62f,
                0.035f, 0.047f, 0.145f, a,
                0.105f, 0.125f, 0.335f, a);
        b.quadGradient(0f, h * 0.62f, w, h * 0.38f,
                0.105f, 0.125f, 0.335f, a,
                0.185f, 0.180f, 0.365f, a);

        // Warm horizon bloom on the left.
        b.glow(w * 0.18f, h * 0.66f, w * 0.45f, 30,
                1.0f, 0.72f, 0.36f, 0.20f * a);
        b.glow(w * 0.20f, h * 0.70f, w * 0.20f, 20,
                1.0f, 0.85f, 0.55f, 0.16f * a);

        // Stars in the upper sky.
        for (int i = 0; i < 90; i++) {
            float x = hash(i * 3 + 1) * w;
            float y = hash(i * 7 + 5) * h * 0.55f;
            float s = 0.6f + hash(i * 11 + 3) * 1.3f;
            float tw = 0.55f + 0.45f * (float) Math.sin(drift * 1.3f + i);
            b.circle(x, y, s, 5, 0.92f, 0.94f, 1f, a * tw * 0.75f);
        }

        // Far citadel silhouette.
        citadel(b, w * 0.30f, h * 0.70f, w * 0.30f, h * 0.26f, 0.10f, 0.13f, 0.18f, a * 0.85f, 3);
        citadel(b, w * 0.72f, h * 0.72f, w * 0.22f, h * 0.20f, 0.09f, 0.11f, 0.16f, a * 0.8f, 7);

        // Warm lit windows on the towers.
        for (int i = 0; i < 26; i++) {
            float x = w * 0.22f + hash(i * 5 + 2) * w * 0.62f;
            float y = h * 0.56f + hash(i * 9 + 4) * h * 0.14f;
            float flick = 0.55f + 0.45f * (float) Math.sin(drift * 2.1f + i * 1.7f);
            b.quad(x, y, 1.6f, 2.4f, 1f, 0.82f, 0.45f, a * 0.6f * flick);
        }

        // Mist bands.
        for (int i = 0; i < 3; i++) {
            float y = h * (0.60f + i * 0.045f);
            b.quadGradient(0f, y, w, h * 0.055f,
                    0.55f, 0.62f, 0.72f, 0.0f,
                    0.55f, 0.62f, 0.72f, (0.10f - i * 0.025f) * a);
        }

        // Cliff / foreground ground plane.
        b.quadGradient(0f, h * 0.74f, w, h * 0.26f,
                0.055f, 0.075f, 0.098f, a,
                0.031f, 0.043f, 0.063f, a);
        ridge(b, w, h, h * 0.74f, 9f, 0.055f, 0.075f, 0.098f, a);
    }

    private void citadel(UiBatch b, float cx, float baseY, float wspan, float hspan,
                         float r, float g, float bl, float alpha, int seed) {
        // A cluster of towers with conical roofs.
        int towers = 5;
        for (int i = 0; i < towers; i++) {
            float t = (float) i / (towers - 1);
            float x = cx - wspan * 0.5f + t * wspan;
            float tw = wspan * (0.06f + hash(seed + i * 3) * 0.06f);
            float th = hspan * (0.45f + hash(seed + i * 7) * 0.95f);
            b.quad(x - tw * 0.5f, baseY - th, tw, th, r, g, bl, alpha);
            // Roof.
            b.triangle(x - tw * 0.72f, baseY - th,
                    x + tw * 0.72f, baseY - th,
                    x, baseY - th - th * 0.42f, r * 0.85f, g * 0.85f, bl * 0.9f, alpha);
        }
        // Curtain wall.
        b.quad(cx - wspan * 0.6f, baseY - hspan * 0.30f, wspan * 1.2f, hspan * 0.30f,
                r, g, bl, alpha);
    }

    // -------------------------------------------------------------- bokeh

    private void drawBokeh(UiBatch b, float w, float h, float a) {
        b.quadGradient(0f, 0f, w, h,
                0.031f, 0.043f, 0.078f, a,
                0.075f, 0.094f, 0.137f, a);
        // Large soft amber glow low-left.
        b.glow(w * 0.10f, h * 0.86f, w * 0.42f, 30, 1f, 0.68f, 0.30f, 0.30f * a);
        b.glow(w * 0.16f, h * 0.94f, w * 0.22f, 22, 1f, 0.84f, 0.52f, 0.22f * a);
        // Cool glow upper-right.
        b.glow(w * 0.82f, h * 0.18f, w * 0.35f, 26, 0.35f, 0.55f, 0.85f, 0.13f * a);
        // Out-of-focus citadel mass (soft stacked rectangles).
        for (int i = 0; i < 16; i++) {
            float x = w * (0.45f + hash(i * 3 + 2) * 0.55f);
            float bw = 14f + hash(i * 5 + 7) * 34f;
            float bh = 40f + hash(i * 11 + 1) * 150f;
            b.quad(x, h * 0.72f - bh, bw, bh, 0.13f, 0.16f, 0.22f, a * 0.65f);
            b.glow(x + bw * 0.5f, h * 0.72f - bh * 0.4f, bw * 2.2f, 12,
                    1f, 0.80f, 0.45f, 0.05f * a);
        }
        // Bokeh discs.
        for (int i = 0; i < 40; i++) {
            float x = hash(i * 13 + 3) * w;
            float y = h * 0.45f + hash(i * 17 + 9) * h * 0.6f;
            float r = 3f + hash(i * 23 + 5) * 13f;
            float bri = 0.05f + hash(i * 29 + 11) * 0.10f;
            float warm = hash(i * 31 + 13);
            b.glow(x, y, r, 10, 1f, 0.72f + warm * 0.22f, 0.42f + warm * 0.30f, bri * a);
        }
    }

    // --------------------------------------------------------------- halls

    private void drawHall(UiBatch b, float w, float h, float a, boolean library) {
        b.quadGradient(0f, 0f, w, h,
                0.047f, 0.055f, 0.090f, a,
                0.078f, 0.075f, 0.118f, a);

        // Stone columns.
        for (int i = 0; i < 6; i++) {
            float x = w * (0.06f + i * 0.175f);
            float cw = w * 0.055f;
            b.quadGradient(x, h * 0.10f, cw, h * 0.80f,
                    0.11f, 0.115f, 0.16f, a * 0.9f,
                    0.07f, 0.07f, 0.10f, a * 0.9f);
            // Capital + base blocks.
            b.quad(x - cw * 0.16f, h * 0.10f, cw * 1.32f, h * 0.035f,
                    0.15f, 0.15f, 0.20f, a * 0.9f);
            b.quad(x - cw * 0.16f, h * 0.86f, cw * 1.32f, h * 0.035f,
                    0.13f, 0.13f, 0.18f, a * 0.9f);
        }

        if (library) {
            // Tall arched windows with warm light.
            for (int i = 0; i < 3; i++) {
                float cx = w * (0.28f + i * 0.24f);
                float ww = w * 0.10f;
                float top = h * 0.12f;
                float bot = h * 0.62f;
                b.quad(cx - ww * 0.5f, top, ww, bot - top,
                        0.95f, 0.78f, 0.48f, a * 0.16f);
                b.ring(cx, top + ww * 0.5f, ww * 0.5f, ww * 0.5f - 3f, 16,
                        0.85f, 0.70f, 0.42f, a * 0.30f);
                // Light shaft.
                b.polygon(new float[]{
                        cx - ww * 0.45f, top,
                        cx + ww * 0.45f, top,
                        cx + ww * 1.9f, bot + h * 0.30f,
                        cx - ww * 1.0f, bot + h * 0.30f
                }, 1f, 0.88f, 0.62f, a * 0.045f);
            }
        } else {
            // Hanging banners.
            for (int i = 0; i < 4; i++) {
                float x = w * (0.14f + i * 0.24f);
                float bw = w * 0.045f;
                b.polygon(new float[]{
                        x - bw * 0.5f, h * 0.08f,
                        x + bw * 0.5f, h * 0.08f,
                        x + bw * 0.5f, h * 0.42f,
                        x, h * 0.47f,
                        x - bw * 0.5f, h * 0.42f
                }, 0.16f, 0.30f, 0.32f, a * 0.55f);
                b.line(x - bw * 0.5f, h * 0.08f, x + bw * 0.5f, h * 0.08f, 2f,
                        0.79f, 0.64f, 0.15f, a * 0.55f);
            }
            // Warm shafts from above.
            for (int i = 0; i < 4; i++) {
                float cx = w * (0.20f + i * 0.22f);
                b.polygon(new float[]{
                        cx - w * 0.02f, 0f,
                        cx + w * 0.02f, 0f,
                        cx + w * 0.09f, h,
                        cx - w * 0.04f, h
                }, 1f, 0.88f, 0.60f, a * 0.030f);
            }
        }

        // Floor.
        b.quadGradient(0f, h * 0.80f, w, h * 0.20f,
                0.08f, 0.08f, 0.11f, a,
                0.04f, 0.04f, 0.06f, a);
        // Dais ellipse.
        b.glow(w * 0.42f, h * 0.84f, w * 0.22f, 22, 1f, 0.86f, 0.52f, 0.14f * a);
        b.ring(w * 0.42f, h * 0.84f, w * 0.115f, w * 0.105f, 30,
                0.85f, 0.70f, 0.36f, a * 0.45f);
    }

    // -------------------------------------------------------------- alcove

    private void drawAlcove(UiBatch b, float w, float h, float a) {
        b.quadGradient(0f, 0f, w, h,
                0.063f, 0.051f, 0.059f, a,
                0.094f, 0.075f, 0.063f, a);
        // Warm lantern pools.
        for (int i = 0; i < 5; i++) {
            float x = w * (0.08f + i * 0.21f);
            float y = h * (0.14f + hash(i * 7 + 3) * 0.12f);
            b.glow(x, y, 60f, 22, 1f, 0.74f, 0.40f, 0.20f * a);
            b.circle(x, y, 4f, 10, 1f, 0.90f, 0.66f, a * 0.75f);
            b.line(x, 0f, x, y - 4f, 1.4f, 0.79f, 0.64f, 0.15f, a * 0.4f);
        }
        // Shelves with bottles.
        for (int row = 0; row < 3; row++) {
            float y = h * (0.34f + row * 0.20f);
            b.quad(0f, y, w, 3f, 0.30f, 0.22f, 0.15f, a * 0.75f);
            for (int i = 0; i < 14; i++) {
                float x = hash(row * 31 + i * 7) * w;
                float bh = 10f + hash(row * 17 + i * 3) * 16f;
                float bw = 5f + hash(row * 11 + i * 5) * 6f;
                float warm = hash(i * 13 + row);
                b.quad(x, y - bh, bw, bh, 0.28f + warm * 0.30f, 0.32f + warm * 0.22f,
                        0.34f, a * 0.6f);
            }
        }
        b.quadGradient(0f, h * 0.86f, w, h * 0.14f,
                0.07f, 0.06f, 0.05f, a, 0.03f, 0.03f, 0.03f, a);
    }

    // ------------------------------------------------------------- helpers

    private void ridge(UiBatch b, float w, float h, float baseY, float amp,
                       float r, float g, float bl, float a) {
        int steps = 48;
        float prevX = 0f;
        float prevY = baseY;
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float x = t * w;
            float n = noise.fbm(t * 3.2f, 11.3f, 3, 2.1f, 0.55f);
            float y = baseY - n * amp;
            b.triangle(prevX, prevY, x, y, x, h, r, g, bl, a);
            b.triangle(prevX, prevY, x, h, prevX, h, r, g, bl, a);
            prevX = x;
            prevY = y;
        }
    }

    private void drawMotes(UiBatch b, float w, float h, float a) {
        for (int i = 0; i < 46; i++) {
            float baseX = hash(i * 19 + 7) * w;
            float speed = 6f + hash(i * 23 + 11) * 14f;
            float x = baseX + (float) Math.sin(drift * 0.35f + i) * 18f;
            float y = (hash(i * 29 + 13) * h - drift * speed) % h;
            if (y < 0f) {
                y += h;
            }
            float r = 0.8f + hash(i * 37 + 17) * 1.8f;
            float tw = 0.4f + 0.6f * (float) Math.abs(Math.sin(drift * 1.7f + i * 2.1f));
            b.circle(x, y, r, 5, 1f, 0.93f, 0.76f, a * 0.30f * tw);
        }
    }

    private void drawVignette(UiBatch b, float w, float h, float a) {
        b.vignette(0.85f * a);
        // Slight top and bottom darkening for legibility of text and tabs.
        b.topFade(h * 0.22f, 0.02f, 0.03f, 0.05f, 0.55f * a);
        b.quadGradient(0f, h - h * 0.20f, w, h * 0.20f,
                0f, 0f, 0f, 0f, 0.01f, 0.02f, 0.04f, 0.72f * a);
    }

    private float hash(int i) {
        float v = (float) Math.sin(i * 127.1f) * 43758.5453f;
        return v - (float) Math.floor(v);
    }
}
