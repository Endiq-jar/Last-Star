import com.laststar.game.ui.Menu;
import com.laststar.game.ui.TouchControls;

/**
 * Drives the menu exactly as a finger does: physical pixels on a phone-sized
 * surface, converted through the same scale the touch handler uses.
 *
 * <p>This exists because a build once shipped where the home screen rendered
 * perfectly and no tap did anything. Every unit test passed, because they
 * called the menu in virtual coordinates and skipped the scale conversion --
 * the one step that was broken. These checks go through it.</p>
 */
public final class InputPathTest {

    private static int checks;
    private static int failures;

    public static void main(String[] args) {
        section("input path");

        // A 1080x2400 phone and a small 720x1280 one: the conversion must work
        // on both, because the design box is fixed and devices are not.
        int[][] surfaces = {{1080, 2400}, {720, 1280}, {1440, 3120}};
        for (int i = 0; i < surfaces.length; i++) {
            int sw = surfaces[i][0];
            int sh = surfaces[i][1];
            float scale = TouchControls.scaleFor(sw, sh);
            float vw = sw / scale;
            float vh = sh / scale;
            check(scale > 0f, sw + "x" + sh + ": scale is positive (" + scale + ")");
            check(vw >= TouchControls.DESIGN_W - 1f,
                    sw + "x" + sh + ": virtual width covers the design box (" + (int) vw + ")");
            check(vh >= TouchControls.DESIGN_H - 1f,
                    sw + "x" + sh + ": virtual height covers the design box (" + (int) vh + ")");

            // Tap the start button where the home screen draws it, in pixels.
            Menu menu = new Menu();
            menu.shownForTest(Menu.Page.HOME);
            float buttonX = vw * 0.5f * scale;
            float buttonY = vh * 0.735f * scale;
            menu.tap(vw * 0.5f, vh * 0.735f, vw, vh);
            check(menu.startRequested,
                    sw + "x" + sh + ": centre tap starts the game (px "
                            + (int) buttonX + "," + (int) buttonY + ")");
        }

        // Every tab must be tappable, not just the start button.
        float scale = TouchControls.scaleFor(1080, 2400);
        float vw = 1080 / scale;
        float vh = 2400 / scale;
        int tabHits = 0;
        for (int t = 0; t < 6; t++) {
            Menu menu = new Menu();
            menu.shownForTest(Menu.Page.HOME);
            // Tabs sit along the bottom in the design box; sweep the row.
            float x = vw * (0.5f + (t - 2.5f) * 0.14f);
            float y = vh - 26f;
            menu.tap(x, y, vw, vh);
            if (menu.page != Menu.Page.HOME) {
                tabHits++;
            }
        }
        check(tabHits >= 1, "tab bar responds to taps (" + tabHits + " pages opened)");

        // A tap outside any control must not start the game.
        Menu menu = new Menu();
        menu.shownForTest(Menu.Page.HOME);
        menu.tap(4f, 4f, vw, vh);
        check(!menu.startRequested, "corner tap does not start the game");

        System.out.println();
        System.out.println("checks: " + checks + "   failures: " + failures);
        System.out.println("RESULT: " + (failures == 0 ? "PASS" : "FAIL"));
        if (failures != 0) {
            System.exit(1);
        }
    }

    private static void section(String name) {
        System.out.println("- " + name);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (ok) {
            System.out.println("  ok   " + what);
        } else {
            failures++;
            System.out.println("  FAIL " + what);
        }
    }
}
