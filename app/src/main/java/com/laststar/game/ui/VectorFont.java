package com.laststar.game.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A stroke-built Roman (Trajan-flavoured) display serif, baked at start-up into a
 * signed distance-field atlas so the menus get engraved-looking capitals that
 * stay crisp at any scale.
 *
 * <p>Glyphs are described as a handful of strokes in a 600x700 cap box. Strokes
 * are scan-converted with exact horizontal span coverage and <em>combined with a
 * union</em> (max), so overlapping strokes merge cleanly instead of cancelling
 * the way an even-odd fill would. The merged coverage is then turned into a
 * signed distance field with a chamfer sweep, which is what makes 8-pixel
 * lettering render smoothly with bilinear filtering.</p>
 *
 * <p>Everything is generated in source: no TTF, no {@code Typeface}, no assets.</p>
 */
public final class VectorFont extends AtlasFont {

    /** Design-space cap height: one glyph at scale 1 in 720x405 virtual space. */
    public static final int CAP_PX = 8;
    /** Design-space advance (cap + side bearing). */
    public static final int ADVANCE_PX = 9;

    private static final int EM_W = 600;
    /** Cap height in glyph units. */
    private static final int CAP = 700;
    /** Total vertical glyph space mapped into the atlas (cap plus descender). */
    private static final int DESC_END = 770;

    /** Rasterised glyph box inside the atlas. */
    private static final int DF_W = 34;
    private static final int DF_H = 40;
    /** Coarse grid the outlines are scan-converted on before upsampling. */
    private static final int LOW_W = 17;
    private static final int LOW_H = 20;
    private static final int PAD = 3;
    private static final int COLS = 8;
    /** Distance range encoded by the atlas, in atlas pixels. */
    private static final float SPREAD = 5f;

    private static final String CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,:;!?/+-'()%<>&";

    private final int rows;

    public VectorFont() {
        rows = (CHARS.length() + COLS - 1) / COLS;
        atlasWidth = COLS * (DF_W + PAD * 2);
        atlasHeight = rows * (DF_H + PAD * 2);
        byte[] raw = new byte[atlasWidth * atlasHeight];
        int cellW = DF_W + PAD * 2;
        int cellH = DF_H + PAD * 2;
        for (int g = 0; g < CHARS.length(); g++) {
            List<float[]> polys = glyph(CHARS.charAt(g));
            int cellX = (g % COLS) * cellW + PAD;
            int cellY = (g / COLS) * cellH + PAD;
            float[] cov = coverage(polys);
            float[] dist = distanceField(cov);
            for (int py = 0; py < DF_H; py++) {
                for (int px = 0; px < DF_W; px++) {
                    float d = dist[py * DF_W + px];
                    int v = Math.round((0.5f + d / (2f * SPREAD)) * 255f);
                    raw[(cellY + py) * atlasWidth + (cellX + px)] = (byte) clamp(v, 0, 255);
                }
            }
        }
        atlas = dilate(raw, atlasWidth);
    }

    // ------------------------------------------------------------- raster

    /**
     * Covers every polygon into one cell buffer, taking the maximum so
     * overlapping strokes stay solid. Rows are sampled {@link #SUB} times for
     * vertical antialiasing, spans get fractional horizontal coverage.
     */
    private static float[] coverage(List<float[]> polys) {
        final int sub = 4;
        float[] low = new float[LOW_W * LOW_H];
        float sx = (float) LOW_W / EM_W;
        float sy = (float) LOW_H / DESC_END;
        for (float[] poly : polys) {
            int n = poly.length / 2;
            for (int py = 0; py < LOW_H; py++) {
                for (int s = 0; s < sub; s++) {
                    float gy = (py + (s + 0.5f) / sub) / sy;
                    float xa = Float.NaN;
                    float xb = Float.NaN;
                    for (int i = 0, j = n - 1; i < n; j = i++) {
                        float x0 = poly[j * 2];
                        float y0 = poly[j * 2 + 1];
                        float x1 = poly[i * 2];
                        float y1 = poly[i * 2 + 1];
                        if ((y0 > gy) == (y1 > gy)) {
                            continue;
                        }
                        float x = x0 + (gy - y0) * (x1 - x0) / (y1 - y0);
                        if (Float.isNaN(xa)) {
                            xa = x;
                        } else if (Float.isNaN(xb)) {
                            xb = x;
                        } else {
                            xa = Math.min(xa, x);
                            xb = Math.max(xb, x);
                        }
                    }
                    if (Float.isNaN(xb)) {
                        continue;
                    }
                    if (xa > xb) {
                        float t = xa;
                        xa = xb;
                        xb = t;
                    }
                    xa *= sx;
                    xb *= sx;
                    int x0i = Math.max(0, (int) Math.floor(xa));
                    int x1i = Math.min(LOW_W - 1, (int) Math.ceil(xb));
                    for (int x = x0i; x <= x1i; x++) {
                        float cover = Math.min(x + 1f, xb) - Math.max(x, xa);
                        if (cover > 0f) {
                            int idx = py * LOW_W + x;
                            float v = low[idx] + cover / sub;
                            low[idx] = v > 1f ? 1f : v;
                        }
                    }
                }
            }
        }
        // Upsample to the atlas grid; the chamfer pass turns this into the
        // smooth outline, so a coarse scan does not cost any visible quality.
        float[] out = new float[DF_W * DF_H];
        for (int py = 0; py < DF_H; py++) {
            float fy = (py + 0.5f) / DF_H * LOW_H - 0.5f;
            int y0 = (int) Math.floor(fy);
            float ty = fy - y0;
            int y1 = Math.min(LOW_H - 1, y0 + 1);
            y0 = Math.max(0, y0);
            for (int px = 0; px < DF_W; px++) {
                float fx = (px + 0.5f) / DF_W * LOW_W - 0.5f;
                int x0 = (int) Math.floor(fx);
                float tx = fx - x0;
                int x1 = Math.min(LOW_W - 1, x0 + 1);
                x0 = Math.max(0, x0);
                float a = low[y0 * LOW_W + x0];
                float b = low[y0 * LOW_W + x1];
                float c = low[y1 * LOW_W + x0];
                float d = low[y1 * LOW_W + x1];
                out[py * DF_W + px] = (a * (1f - tx) + b * tx) * (1f - ty)
                        + (c * (1f - tx) + d * tx) * ty;
            }
        }
        return out;
    }

    /**
     * Signed distance in atlas pixels (positive inside) from antialiased
     * coverage, using a two-pass chamfer sweep. Ample for 8px lettering.
     */
    private static float[] distanceField(float[] cov) {
        float big = 1e6f;
        float[] outside = new float[cov.length];
        float[] inside = new float[cov.length];
        for (int i = 0; i < cov.length; i++) {
            boolean on = cov[i] > 0.5f;
            outside[i] = on ? 0f : big;
            inside[i] = on ? big : 0f;
        }
        sweep(outside);
        sweep(inside);
        float[] out = new float[cov.length];
        for (int i = 0; i < cov.length; i++) {
            // Use the (continuous) antialiasing value to soften the 0.5 step.
            float edge = (cov[i] - 0.5f) * 2f;
            out[i] = (inside[i] - outside[i]) + edge * 0.25f;
        }
        return out;
    }

    private static void sweep(float[] d) {
        float diag = 1.4142135f;
        for (int y = 0; y < DF_H; y++) {
            for (int x = 0; x < DF_W; x++) {
                int i = y * DF_W + x;
                float best = d[i];
                if (x > 0) {
                    best = Math.min(best, d[i - 1] + 1f);
                }
                if (y > 0) {
                    best = Math.min(best, d[i - DF_W] + 1f);
                    if (x > 0) {
                        best = Math.min(best, d[i - DF_W - 1] + diag);
                    }
                    if (x < DF_W - 1) {
                        best = Math.min(best, d[i - DF_W + 1] + diag);
                    }
                }
                d[i] = best;
            }
        }
        for (int y = DF_H - 1; y >= 0; y--) {
            for (int x = DF_W - 1; x >= 0; x--) {
                int i = y * DF_W + x;
                float best = d[i];
                if (x < DF_W - 1) {
                    best = Math.min(best, d[i + 1] + 1f);
                }
                if (y < DF_H - 1) {
                    best = Math.min(best, d[i + DF_W] + 1f);
                    if (x < DF_W - 1) {
                        best = Math.min(best, d[i + DF_W + 1] + diag);
                    }
                    if (x > 0) {
                        best = Math.min(best, d[i + DF_W - 1] + diag);
                    }
                }
                d[i] = best;
            }
        }
    }

    /**
     * Bleeds distances one texel outwards so bilinear filtering of small glyphs
     * never mixes the zeroed gutter into an edge.
     */
    private static byte[] dilate(byte[] src, int width) {
        byte[] out = src.clone();
        int rowsCount = src.length / width;
        int atlasWidth = width;
        for (int y = 0; y < rowsCount; y++) {
            for (int x = 0; x < atlasWidth; x++) {
                int idx = y * atlasWidth + x;
                if (src[idx] != 0) {
                    continue;
                }
                int sum = 0;
                int count = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= atlasWidth || ny >= rowsCount) {
                            continue;
                        }
                        int nidx = ny * atlasWidth + nx;
                        if (src[nidx] != 0) {
                            sum += src[nidx] & 0xFF;
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    out[idx] = (byte) (sum / count);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ metrics

    @Override
    public int glyphWidth() {
        return CAP_PX;
    }

    @Override
    public int glyphHeight() {
        return CAP_PX;
    }

    @Override
    public int advance() {
        return ADVANCE_PX;
    }

    @Override
    public boolean smooth() {
        return true;
    }

    @Override
    public boolean coverageAtlas() {
        return false;
    }

    @Override
    public float[] uv(char c) {
        int g = indexOf(c);
        int cellW = DF_W + PAD * 2;
        int cellH = DF_H + PAD * 2;
        int cx = (g % COLS) * cellW + PAD;
        int cy = (g / COLS) * cellH + PAD;
        return new float[]{
                (float) cx / atlasWidth,
                (float) cy / atlasHeight,
                (float) DF_W / atlasWidth,
                (float) DF_H / atlasHeight
        };
    }

    public int indexOf(char c) {
        char up = Character.toUpperCase(c);
        int i = CHARS.indexOf(up);
        return i < 0 ? CHARS.indexOf(' ') : i;
    }

    public String charset() {
        return CHARS;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ----------------------------------------------------------- geometry

    /** Accumulates polygons for one glyph. Glyph space is 600 wide, 700 tall, y down. */
    private static final class Pen {
        final List<float[]> polys = new ArrayList<>();

        /** Tapered quad between two points. */
        void stroke(float x0, float y0, float x1, float y1, float w0, float w1) {
            float dx = x1 - x0;
            float dy = y1 - y0;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-4f) {
                return;
            }
            float nx = -dy / len;
            float ny = dx / len;
            float ax = x0 + nx * w0 * 0.5f;
            float ay = y0 + ny * w0 * 0.5f;
            float bx = x1 + nx * w1 * 0.5f;
            float by = y1 + ny * w1 * 0.5f;
            float cx = x1 - nx * w1 * 0.5f;
            float cy = y1 - ny * w1 * 0.5f;
            float dx2 = x0 - nx * w0 * 0.5f;
            float dy2 = y0 - ny * w0 * 0.5f;
            polys.add(new float[]{ax, ay, bx, by, cx, cy, dx2, dy2});
        }

        void stroke(float x0, float y0, float x1, float y1, float w) {
            stroke(x0, y0, x1, y1, w, w);
        }

        /** Polyline with uniform width; endpoints are rounded by extra discs. */
        void path(float w, float... xy) {
            for (int i = 0; i + 3 < xy.length; i += 2) {
                stroke(xy[i], xy[i + 1], xy[i + 2], xy[i + 3], w);
            }
            for (int i = 0; i + 1 < xy.length; i += 2) {
                knot(xy[i], xy[i + 1], w * 0.5f);
            }
        }

        /** Small regular polygon used to round joints and terminals. */
        void knot(float cx, float cy, float r) {
            int n = 10;
            float[] p = new float[n * 2];
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2.0 / n;
                p[i * 2] = cx + (float) Math.cos(a) * r;
                p[i * 2 + 1] = cy + (float) Math.sin(a) * r;
            }
            polys.add(p);
        }

        /** Elliptical arc, angles in radians (0 = right, +y is down). */
        void arc(float cx, float cy, float rx, float ry, float a0, float a1, float w) {
            int steps = Math.max(10, (int) (Math.abs(a1 - a0) * 14));
            float px = 0f;
            float py = 0f;
            for (int i = 0; i <= steps; i++) {
                float t = a0 + (a1 - a0) * i / steps;
                float x = cx + (float) Math.cos(t) * rx;
                float y = cy + (float) Math.sin(t) * ry;
                if (i > 0) {
                    stroke(px, py, x, y, w);
                }
                px = x;
                py = y;
            }
            knot(cx + (float) Math.cos(a0) * rx, cy + (float) Math.sin(a0) * ry, w * 0.5f);
            knot(px, py, w * 0.5f);
        }

        /** Cubic bezier sampled into short strokes. */
        void curve(float x0, float y0, float x1, float y1, float x2, float y2,
                   float x3, float y3, float w0, float w1) {
            int steps = 14;
            float px = x0;
            float py = y0;
            for (int i = 1; i <= steps; i++) {
                float t = i / (float) steps;
                float u = 1f - t;
                float x = u * u * u * x0 + 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t * x3;
                float y = u * u * u * y0 + 3f * u * u * t * y1 + 3f * u * t * t * y2 + t * t * t * y3;
                float wa = w0 + (w1 - w0) * (i - 1) / steps;
                float wb = w0 + (w1 - w0) * i / steps;
                stroke(px, py, x, y, wa, wb);
                px = x;
                py = y;
            }
        }

        /** Flared horizontal wedge — the serif on a vertical terminal. */
        void serif(float x, float y) {
            polys.add(new float[]{x - 52, y + 22, x + 52, y + 22, x + 30, y - 22, x - 30, y - 22});
        }

        /** Serif with the flare pointing the other way (top terminal). */
        void serifUp(float x, float y) {
            polys.add(new float[]{x - 52, y - 22, x + 52, y - 22, x + 30, y + 22, x - 30, y + 22});
        }
    }

    /** Glyph outlines in a 0..600 wide, 0..700 tall box (y down). */
    private static List<float[]> glyph(char ch) {
        Pen p = new Pen();
        float W = 92f;
        float T = 78f;
        switch (ch) {
            case 'A':
                p.path(W * 0.9f, 40, 700, 300, 0, 560, 700);
                p.stroke(150, 452, 450, 452, T);
                p.serif(40, 700);
                p.serif(560, 700);
                break;
            case 'B':
                p.stroke(96, 0, 96, 700, W);
                p.serifUp(96, 0);
                p.serif(96, 700);
                p.arc(210, 186, 214, 186, -1.5708f, 1.5708f, T);
                p.stroke(96, 372, 210, 372, T);
                p.arc(210, 536, 250, 164, -1.5708f, 1.5708f, T);
                break;
            case 'C':
                p.arc(320, 350, 250, 350, -1.15f, -5.13f, W * 0.95f);
                break;
            case 'D':
                p.stroke(96, 0, 96, 700, W);
                p.serifUp(96, 0);
                p.serif(96, 700);
                p.arc(120, 350, 400, 350, -1.5708f, 1.5708f, W * 0.9f);
                break;
            case 'E':
                p.stroke(96, 0, 96, 700, W);
                p.serifUp(96, 0);
                p.serif(96, 700);
                p.stroke(96, 44, 470, 44, T);
                p.stroke(96, 350, 408, 350, T);
                p.stroke(96, 656, 470, 656, T);
                break;
            case 'F':
                p.stroke(96, 0, 96, 700, W);
                p.serifUp(96, 0);
                p.serif(96, 700);
                p.stroke(96, 44, 470, 44, T);
                p.stroke(96, 350, 396, 350, T);
                break;
            case 'G':
                p.arc(320, 350, 250, 350, -1.15f, -4.60f, W * 0.95f);
                p.stroke(320, 378, 540, 378, T);
                p.stroke(540, 378, 540, 610, W * 0.85f);
                break;
            case 'H':
                p.stroke(96, 0, 96, 700, W);
                p.stroke(504, 0, 504, 700, W);
                p.stroke(96, 350, 504, 350, T);
                p.serifUp(96, 0);
                p.serifUp(504, 0);
                p.serif(96, 700);
                p.serif(504, 700);
                break;
            case 'I':
                p.stroke(300, 0, 300, 700, W);
                p.serifUp(300, 0);
                p.serif(300, 700);
                break;
            case 'J':
                p.stroke(430, 0, 430, 500, W);
                p.arc(300, 500, 130, 200, 0f, 1.5708f, W * 0.95f);
                p.serifUp(430, 0);
                break;
            case 'K':
                p.stroke(96, 0, 96, 700, W);
                p.serifUp(96, 0);
                p.serif(96, 700);
                p.stroke(510, 20, 128, 372, T);
                p.stroke(232, 300, 528, 700, W * 0.85f);
                p.serif(528, 700);
                break;
            case 'L':
                p.stroke(96, 0, 96, 700, W);
                p.serifUp(96, 0);
                p.serif(96, 700);
                p.stroke(96, 656, 470, 656, T);
                break;
            case 'M':
                p.stroke(52, 700, 84, 0, W * 0.9f, T);
                p.stroke(548, 700, 516, 0, W * 0.9f, T);
                p.stroke(84, 0, 300, 470, T, W * 0.75f);
                p.stroke(516, 0, 300, 470, T, W * 0.75f);
                p.serif(52, 700);
                p.serif(548, 700);
                break;
            case 'N':
                p.stroke(70, 700, 96, 0, W);
                p.stroke(530, 700, 504, 0, W);
                p.stroke(96, 0, 504, 700, T, T);
                p.serif(70, 700);
                p.serif(530, 700);
                break;
            case 'O':
                p.arc(300, 350, 244, 350, -1.5708f, 4.7124f, W * 0.95f);
                break;
            case 'P':
                p.stroke(96, 0, 96, 700, W);
                p.serifUp(96, 0);
                p.serif(96, 700);
                p.arc(214, 200, 230, 200, -1.5708f, 1.5708f, T);
                p.stroke(96, 400, 214, 400, T);
                break;
            case 'Q':
                p.arc(300, 350, 244, 350, -1.5708f, 4.7124f, W * 0.95f);
                p.stroke(340, 470, 520, 720, 58f, 44f);
                p.serif(520, 720);
                break;
            case 'R':
                p.stroke(96, 0, 96, 700, W);
                p.serifUp(96, 0);
                p.serif(96, 700);
                p.arc(214, 200, 230, 200, -1.5708f, 1.5708f, T);
                p.stroke(96, 400, 214, 400, T);
                p.stroke(216, 380, 512, 700, W * 0.8f);
                p.serif(512, 700);
                break;
            case 'S':
                p.curve(470, 96, 452, 26, 336, -18, 250, 22, 74f, 78f);
                p.curve(250, 22, 150, 72, 168, 236, 300, 312, 78f, 76f);
                p.curve(300, 312, 434, 388, 486, 468, 468, 566, 76f, 74f);
                p.curve(468, 566, 440, 690, 250, 736, 122, 622, 74f, 70f);
                p.serif(472, 96);
                p.serif(122, 622);
                break;
            case 'T':
                p.stroke(66, 44, 534, 44, T);
                p.stroke(300, 34, 300, 700, W);
                p.serif(300, 700);
                break;
            case 'U':
                p.stroke(84, 0, 84, 452, W);
                p.arc(300, 452, 216, 216, 0f, 3.1416f, W * 0.95f);
                p.stroke(516, 452, 516, 0, W);
                p.serifUp(84, 0);
                p.serifUp(516, 0);
                break;
            case 'V':
                p.stroke(46, 0, 300, 700, W * 0.9f, T);
                p.stroke(554, 0, 300, 700, W * 0.9f, T);
                p.serifUp(46, 0);
                p.serifUp(554, 0);
                break;
            case 'W':
                p.stroke(28, 0, 148, 700, W * 0.85f, T);
                p.stroke(148, 700, 300, 220, T, T);
                p.stroke(300, 220, 452, 700, T, T);
                p.stroke(452, 700, 572, 0, T, W * 0.85f);
                p.serifUp(28, 0);
                p.serifUp(572, 0);
                break;
            case 'X':
                p.stroke(56, 0, 544, 700, W * 0.9f, T);
                p.stroke(544, 0, 56, 700, W * 0.9f, T);
                p.serifUp(56, 0);
                p.serifUp(544, 0);
                break;
            case 'Y':
                p.stroke(56, 0, 300, 372, W * 0.9f, T);
                p.stroke(544, 0, 300, 372, W * 0.9f, T);
                p.stroke(300, 356, 300, 700, W);
                p.serifUp(56, 0);
                p.serifUp(544, 0);
                p.serif(300, 700);
                break;
            case 'Z':
                p.stroke(88, 44, 512, 44, T);
                p.stroke(512, 28, 106, 656, T);
                p.stroke(92, 656, 512, 656, T);
                p.serifUp(512, 44);
                break;

            // ------------------------------------------------------- digits
            case '0':
                p.arc(300, 350, 216, 350, -1.5708f, 4.7124f, W * 0.95f);
                break;
            case '1':
                p.stroke(196, 118, 296, 34, W);
                p.stroke(296, 34, 296, 700, W);
                p.serif(296, 700);
                break;
            case '2':
                p.arc(300, 216, 214, 216, -2.85f, 0.32f, W * 0.95f);
                p.stroke(496, 300, 124, 668, T);
                p.stroke(110, 668, 512, 668, T);
                break;
            case '3':
                p.arc(300, 190, 196, 190, -2.70f, 1.58f, W * 0.9f);
                p.arc(300, 508, 222, 192, -1.58f, 1.66f, W * 0.9f);
                break;
            case '4':
                p.stroke(410, 22, 116, 480, T);
                p.stroke(96, 480, 524, 480, T);
                p.stroke(410, 250, 410, 700, W * 0.95f);
                p.serif(410, 700);
                break;
            case '5':
                p.stroke(478, 44, 152, 44, T);
                p.stroke(152, 36, 130, 320, T);
                p.arc(300, 486, 220, 214, -1.86f, 1.66f, W * 0.9f);
                break;
            case '6':
                p.curve(452, 34, 300, 92, 96, 250, 96, 470, 74f, 76f);
                p.arc(300, 500, 214, 200, 3.1416f, 9.4248f, W * 0.95f);
                break;
            case '7':
                p.stroke(84, 44, 516, 44, T);
                p.stroke(506, 28, 234, 700, W * 0.9f, T);
                p.serifUp(84, 44);
                break;
            case '8':
                p.arc(300, 176, 196, 176, -1.5708f, 4.7124f, W * 0.9f);
                p.arc(300, 522, 224, 178, -1.5708f, 4.7124f, W * 0.9f);
                break;
            case '9':
                p.arc(300, 200, 214, 200, 0f, 6.2832f, W * 0.95f);
                p.curve(514, 200, 514, 420, 470, 620, 300, 700, 76f, 70f);
                break;

            // ------------------------------------------------- punctuation
            case '.':
                p.knot(160, 644, 52f);
                break;
            case ',':
                p.knot(160, 636, 50f);
                p.stroke(160, 640, 118, 726, 40f, 22f);
                break;
            case ':':
                p.knot(160, 300, 50f);
                p.knot(160, 644, 50f);
                break;
            case ';':
                p.knot(160, 300, 50f);
                p.knot(160, 636, 46f);
                p.stroke(160, 640, 118, 726, 40f, 22f);
                break;
            case '!':
                p.stroke(160, 34, 160, 470, 62f, 52f);
                p.knot(160, 644, 48f);
                break;
            case '?':
                p.arc(240, 170, 170, 170, -2.7f, 0.9f, W * 0.9f);
                p.stroke(400, 300, 230, 470, T);
                p.knot(230, 644, 48f);
                break;
            case '/':
                p.stroke(96, 726, 316, -26, 60f);
                break;
            case '+':
                p.stroke(100, 380, 440, 380, 60f);
                p.stroke(270, 210, 270, 550, 60f);
                break;
            case '-':
                p.stroke(100, 380, 440, 380, 60f);
                break;
            case '\'':
                p.stroke(170, 30, 140, 160, 62f, 46f);
                break;
            case '(':
                p.curve(420, 26, 130, 240, 130, 470, 420, 690, 58f, 58f);
                break;
            case ')':
                p.curve(120, 26, 410, 240, 410, 470, 120, 690, 58f, 58f);
                break;
            case '%':
                p.arc(140, 170, 92, 92, 0f, 6.2832f, 56f);
                p.arc(400, 540, 92, 92, 0f, 6.2832f, 56f);
                p.stroke(96, 726, 456, -26, 56f);
                break;
            case '<':
                p.path(62f, 70, 130, 440, 400, 60, 690);
                break;
            case '>':
                p.path(62f, 130, 70, 440, 400, 690, 690);
                break;
            case '&':
                p.curve(452, 96, 452, 26, 320, 0, 258, 54, 70f, 72f);
                p.curve(258, 54, 180, 120, 246, 262, 330, 366, 72f, 74f);
                p.curve(330, 366, 420, 470, 476, 520, 430, 604, 74f, 70f);
                p.curve(430, 604, 372, 700, 210, 660, 152, 560, 70f, 68f);
                p.curve(152, 560, 96, 460, 210, 380, 300, 330, 68f, 70f);
                p.stroke(196, 350, 500, 350, 66f);
                break;
            case ' ':
            default:
                break;
        }
        return p.polys;
    }
}
