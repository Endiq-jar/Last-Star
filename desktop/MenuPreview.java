import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.Menu;
import com.laststar.game.ui.VectorFont;
import com.laststar.game.ui.UiBatch;

import java.io.File;
import java.util.List;

/**
 * Renders the menu screens with the software rasteriser so the interface can be
 * reviewed on a machine with no device.
 *
 * <p>It exercises the same {@link UiBatch} geometry, the same {@link VectorFont}
 * atlas and the same screen classes that the APK ships, so the output is a
 * faithful preview of the phone build.</p>
 *
 * <p>Usage: {@code java MenuPreview <outputDir> [width] [height]}</p>
 */
public final class MenuPreview {

    private static final AtlasFont FONT = new VectorFont();

    public static void main(String[] args) throws Exception {
        String outDir = args.length > 0 ? args[0] : "docs/ui/screens";
        int w = args.length > 1 ? Integer.parseInt(args[1]) : 1280;
        int h = args.length > 2 ? Integer.parseInt(args[2]) : 720;
        new File(outDir).mkdirs();

        shoot(outDir, w, h, "home", Menu.Page.HOME, null);
        shoot(outDir, w, h, "about", Menu.Page.ABOUT, null);
        shoot(outDir, w, h, "shop", Menu.Page.SHOP, null);
        shoot(outDir, w, h, "quests", Menu.Page.QUESTS, null);
        shoot(outDir, w, h, "wardrobe", Menu.Page.WARDROBE, null);
        shoot(outDir, w, h, "settings", Menu.Page.SETTINGS, null);
        // Interaction proof: an opened dropdown and a different quest tab.
        shoot(outDir, w, h, "settings-dropdown", Menu.Page.SETTINGS, "dropdown");
        shoot(outDir, w, h, "quests-side", Menu.Page.QUESTS, "side");
        shoot(outDir, w, h, "font-sheet", Menu.Page.HOME, "font");

        System.out.println("menu previews written to " + outDir);
    }

    private static void shoot(String dir, int w, int h, String name, Menu.Page page,
                              String variant) throws Exception {
        Raster raster = new Raster(w, h, new com.laststar.game.core.Mat4(),
                new com.laststar.game.core.Mat4());
        UiBatch ui = new UiBatch();
        Menu menu = new Menu();
        // Bind a real world: the journal and the shop read live state now, so
        // an unbound preview would photograph the empty state rather than the
        // screen a player actually sees.
        com.laststar.game.world.World previewWorld =
                new com.laststar.game.world.World(20261105L);
        previewWorld.quests.refreshAvailability();
        previewWorld.gold = 1840;
        // Advance a little progress so the bars are not all empty.
        java.util.List<com.laststar.game.world.QuestLog.Entry> open =
                previewWorld.quests.openQuests();
        if (!open.isEmpty()) {
            com.laststar.game.world.QuestLog.Entry q = open.get(0);
            previewWorld.quests.report(q.def.kind, q.def.subject, q.def.target / 2);
        }
        if (!open.isEmpty()) {
            com.laststar.game.world.QuestLog.Entry q2 =
                    previewWorld.quests.openQuests().get(0);
            previewWorld.quests.report(q2.def.kind, q2.def.subject, q2.def.target);
        }
        menu.bind(previewWorld);
        // Jump straight to the page, then let everything settle.
        menu.page = page;
        menu.shownForTest(page);
        for (int i = 0; i < 90; i++) {
            menu.update(1f / 60f);
        }
        if ("dropdown".equals(variant)) {
            // Tap the difficulty dropdown where SettingsScreen lays it out.
            float dx = 720 * 0.135f + 720 * 0.700f - 26f - 96f - 104f + 52f;
            float panelY = 405 * 0.245f;
            float panelH = 405 * 0.400f;
            float row3 = panelY + 30f + 3f * (panelH - 52f) / 3.4f;
            menu.tap(dx, row3, 720, 405);
            for (int i = 0; i < 10; i++) {
                menu.update(1f / 60f);
            }
        } else if ("side".equals(variant)) {
            menu.quests.activeTab = 2;
            menu.quests.selectedQuest = 2;
            menu.update(1f / 60f);
        }
        if ("font".equals(variant)) {
            ui.begin(720, 405);
            menu.draw(ui, FONT);
            drawFontSheet(ui, FONT);
            blitUi(raster, ui, w, h);
            String path = dir + "/" + name + ".png";
            PngWriter.write(path, w, h, raster.argb);
            System.out.println("wrote " + path);
            return;
        }

        ui.begin(720, 405);
        menu.draw(ui, FONT);
        blitUi(raster, ui, w, h);

        String path = dir + "/" + name + ".png";
        PngWriter.write(path, w, h, raster.argb);
        System.out.println("wrote " + path);
    }

    /** Charset sampler, to check every glyph bakes correctly. */
    private static void drawFontSheet(UiBatch ui, AtlasFont font) {
        ui.overlay(0.04f, 0.05f, 0.08f, 0.88f);
        String rows = "ABCDEFGHIJKLMNOPQRSTUVWXYZ 0123456789 .,:;!?/+-'()%<>&";
        int perRow = 26;
        for (int i = 0; i < rows.length(); i++) {
            char c = rows.charAt(i);
            if (c == ' ') {
                continue;
            }
            int col = i % perRow;
            int row = i / perRow;
            ui.textSpaced(String.valueOf(c), 20f + col * 26f, 60f + row * 40f, 3, 1.0f,
                    0.95f, 0.90f, 0.78f, 1f, font);
        }
        ui.textSpacedCentred("TRAJAN SERIF 0123 - THE LAST STAR", 360f, 340f, 2, 2f,
                0.95f, 0.84f, 0.55f, 1f, font);
    }

    private static void blitUi(Raster raster, UiBatch batch, int w, int h) {
        float sx = w / (float) batch.virtualWidth;
        float sy = h / (float) batch.virtualHeight;
        List<UiBatch.Vertex> verts = batch.vertices();
        for (int i = 0; i + 2 < verts.size(); i += 3) {
            UiBatch.Vertex a = verts.get(i);
            UiBatch.Vertex b = verts.get(i + 1);
            UiBatch.Vertex c = verts.get(i + 2);
            if (!a.textured) {
                raster.fillTriangle2D(
                        a.x * sx, a.y * sy, b.x * sx, b.y * sy, c.x * sx, c.y * sy,
                        new float[]{a.r, a.g, a.b, a.a},
                        new float[]{b.r, b.g, b.b, b.a},
                        new float[]{c.r, c.g, c.b, c.a});
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
            int col = Raster.pack(a.r, a.g, a.b, 1f);
            raster.fillTexturedQuad2D(minX, minY, maxX, maxY, u0, v0, u1, v1,
                    FONT.atlas, FONT.atlasWidth, FONT.atlasHeight, col, a.a,
                    !FONT.coverageAtlas());
        }
    }
}
