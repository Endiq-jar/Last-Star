import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.UiBatch;
import com.laststar.game.ui.VectorFont;
import java.util.List;

public final class GlyphSheet {
    private static final AtlasFont FONT = new VectorFont();
    public static void main(String[] a) throws Exception {
        String text = a.length > 0 ? a[0] : "ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 .,:!?/+-'()%<>&";
        int scale = a.length > 1 ? Integer.parseInt(a[1]) : 5;
        float track = a.length > 2 ? Float.parseFloat(a[2]) : 1.5f;
        int cols = 10;
        int cellW = 62, cellH = 58;
        int rows = (text.length() + cols - 1) / cols;
        int w = cols * cellW, h = rows * cellH;
        Raster r = new Raster(w, h, new com.laststar.game.core.Mat4(), new com.laststar.game.core.Mat4());
        // background
        UiBatch ui = new UiBatch();
        ui.begin(w, h);
        ui.quad(0, 0, w, h, 0.06f, 0.07f, 0.10f, 1f);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') continue;
            int cx = (i % cols) * cellW + cellW / 2;
            int cy = (i / cols) * cellH + cellH / 2;
            String s = String.valueOf(c);
            float tw = ui.textSpacedWidth(s, FONT, scale, track);
            ui.textSpaced(s, cx - tw * 0.5f, cy - FONT.lineHeight(scale) * 0.5f, scale, track,
                    0.95f, 0.90f, 0.76f, 1f, FONT);
        }
        blit(r, ui, w, h);
        PngWriter.write("docs/ui/preview/font.png", w, h, r.argb);
        System.out.println("wrote docs/ui/preview/font.png " + w + "x" + h);
    }
    private static void blit(Raster raster, UiBatch batch, int w, int h) {
        float sx = w / (float) batch.virtualWidth;
        float sy = h / (float) batch.virtualHeight;
        List<UiBatch.Vertex> verts = batch.vertices();
        for (int i = 0; i + 2 < verts.size(); i += 3) {
            UiBatch.Vertex a = verts.get(i), b = verts.get(i+1), c = verts.get(i+2);
            if (!a.textured) {
                raster.fillTriangle2D(a.x*sx,a.y*sy,b.x*sx,b.y*sy,c.x*sx,c.y*sy,
                    new float[]{a.r,a.g,a.b,a.a}, new float[]{b.r,b.g,b.b,b.a}, new float[]{c.r,c.g,c.b,c.a});
                continue;
            }
            float minX = Math.min(a.x, Math.min(b.x, c.x)) * sx;
            float maxX = Math.max(a.x, Math.max(b.x, c.x)) * sx;
            float minY = Math.min(a.y, Math.min(b.y, c.y)) * sy;
            float maxY = Math.max(a.y, Math.max(b.y, c.y)) * sy;
            float u0 = Math.min(a.u, Math.min(b.u, c.u));
            float u1 = Math.max(a.u, Math.max(b.u, c.u));
            float v0 = Math.min(a.v, Math.min(b.v, c.v));
            float v1 = Math.max(a.v, Math.max(b.v, c.v));
            raster.fillTexturedQuad2D(minX, minY, maxX, maxY, u0, v0, u1, v1,
                FONT.atlas, FONT.atlasWidth, FONT.atlasHeight, Raster.pack(a.r,a.g,a.b,1f), a.a, true);
        }
    }
}
