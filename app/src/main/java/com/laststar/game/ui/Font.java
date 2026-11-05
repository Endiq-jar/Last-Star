package com.laststar.game.ui;

/**
 * A tiny built-in 5x7 bitmap font rendered into a GL texture at start-up.
 *
 * <p>Authoring the glyphs in source (instead of shipping a TTF) keeps the APK
 * dependency free and means text is always crisp: it is drawn as quads sampled
 * with nearest-neighbour filtering at integer scales.</p>
 */
public final class Font extends AtlasFont {

    public static final int GLYPH_W = 5;
    public static final int GLYPH_H = 7;
    /** Horizontal advance including the 1px gap. */
    public static final int ADVANCE = 6;

    private static final String CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,:!?/+-'()%<>";

    /**
     * Row bitmaps, 7 rows of 5 bits per character, in {@link #CHARS} order.
     * Each string is a comma separated list of rows; '1' is ink.
     */
    private static final String[] GLYPHS = {
            // A
            "01110,10001,10001,11111,10001,10001,10001",
            // B
            "11110,10001,10001,11110,10001,10001,11110",
            // C
            "01110,10001,10000,10000,10000,10001,01110",
            // D
            "11110,10001,10001,10001,10001,10001,11110",
            // E
            "11111,10000,10000,11110,10000,10000,11111",
            // F
            "11111,10000,10000,11110,10000,10000,10000",
            // G
            "01110,10001,10000,10111,10001,10001,01110",
            // H
            "10001,10001,10001,11111,10001,10001,10001",
            // I
            "11111,00100,00100,00100,00100,00100,11111",
            // J
            "00111,00010,00010,00010,00010,10010,01100",
            // K
            "10001,10010,10100,11000,10100,10010,10001",
            // L
            "10000,10000,10000,10000,10000,10000,11111",
            // M
            "10001,11011,10101,10101,10001,10001,10001",
            // N
            "10001,11001,10101,10011,10001,10001,10001",
            // O
            "01110,10001,10001,10001,10001,10001,01110",
            // P
            "11110,10001,10001,11110,10000,10000,10000",
            // Q
            "01110,10001,10001,10001,10101,10010,01101",
            // R
            "11110,10001,10001,11110,10100,10010,10001",
            // S
            "01111,10000,10000,01110,00001,00001,11110",
            // T
            "11111,00100,00100,00100,00100,00100,00100",
            // U
            "10001,10001,10001,10001,10001,10001,01110",
            // V
            "10001,10001,10001,10001,10001,01010,00100",
            // W
            "10001,10001,10001,10101,10101,11011,10001",
            // X
            "10001,10001,01010,00100,01010,10001,10001",
            // Y
            "10001,10001,01010,00100,00100,00100,00100",
            // Z
            "11111,00001,00010,00100,01000,10000,11111",
            // 0
            "01110,10001,10011,10101,11001,10001,01110",
            // 1
            "00100,01100,00100,00100,00100,00100,01110",
            // 2
            "01110,10001,00001,00010,00100,01000,11111",
            // 3
            "11111,00010,00100,00010,00001,10001,01110",
            // 4
            "00010,00110,01010,10010,11111,00010,00010",
            // 5
            "11111,10000,11110,00001,00001,10001,01110",
            // 6
            "00110,01000,10000,11110,10001,10001,01110",
            // 7
            "11111,00001,00010,00100,01000,01000,01000",
            // 8
            "01110,10001,10001,01110,10001,10001,01110",
            // 9
            "01110,10001,10001,01111,00001,00010,01100",
            // space
            "00000,00000,00000,00000,00000,00000,00000",
            // .
            "00000,00000,00000,00000,00000,01100,01100",
            // ,
            "00000,00000,00000,00000,01100,01100,00100",
            // :
            "00000,01100,01100,00000,01100,01100,00000",
            // !
            "00100,00100,00100,00100,00100,00000,00100",
            // ?
            "01110,10001,00001,00010,00100,00000,00100",
            // /
            "00001,00010,00010,00100,01000,01000,10000",
            // +
            "00000,00100,00100,11111,00100,00100,00000",
            // -
            "00000,00000,00000,11111,00000,00000,00000",
            // '
            "00100,00100,00000,00000,00000,00000,00000",
            // (
            "00010,00100,01000,01000,01000,00100,00010",
            // )
            "01000,00100,00010,00010,00010,00100,01000",
            // %
            "11001,11010,00010,00100,01000,01011,10011",
            // <
            "00010,00100,01000,10000,01000,00100,00010",
            // >
            "01000,00100,00010,00001,00010,00100,01000",
    };

    private final int cols = 8;
    private final int rows;

    public Font() {
        rows = (GLYPHS.length + cols - 1) / cols;
        atlasWidth = cols * (GLYPH_W + 1);
        atlasHeight = rows * (GLYPH_H + 1);
        atlas = new byte[atlasWidth * atlasHeight];
        for (int g = 0; g < GLYPHS.length; g++) {
            int cx = (g % cols) * (GLYPH_W + 1);
            int cy = (g / cols) * (GLYPH_H + 1);
            String[] rowStrings = GLYPHS[g].split(",");
            for (int y = 0; y < GLYPH_H && y < rowStrings.length; y++) {
                String row = rowStrings[y];
                for (int x = 0; x < GLYPH_W && x < row.length(); x++) {
                    if (row.charAt(x) == '1') {
                        atlas[(cy + y) * atlasWidth + (cx + x)] = (byte) 255;
                    }
                }
            }
        }
    }

    public int indexOf(char c) {
        char up = Character.toUpperCase(c);
        int idx = CHARS.indexOf(up);
        if (idx < 0) {
            idx = CHARS.indexOf(' ');
        }
        return idx;
    }

    @Override
    public int glyphWidth() {
        return GLYPH_W;
    }

    @Override
    public int glyphHeight() {
        return GLYPH_H;
    }

    @Override
    public int advance() {
        return ADVANCE;
    }

    /** Atlas UV rect for a glyph: {u0, v0, du, dv}. */
    @Override
    public float[] uv(char c) {
        int g = indexOf(c);
        float u0 = (float) ((g % cols) * (GLYPH_W + 1)) / atlasWidth;
        float v0 = (float) ((g / cols) * (GLYPH_H + 1)) / atlasHeight;
        return new float[]{u0, v0, (float) GLYPH_W / atlasWidth, (float) GLYPH_H / atlasHeight};
    }

    /** Static convenience used by the HUD; the instance form lives on AtlasFont. */
    public static int glyphWidth(String s, int scale) {
        return s.length() * ADVANCE * scale;
    }

    public static int textHeight(int scale) {
        return GLYPH_H * scale;
    }
}
