package com.laststar.game.ui;

import com.laststar.game.Prefs;
import com.laststar.game.ui.screens.AboutScreen;
import com.laststar.game.ui.screens.HomeScreen;
import com.laststar.game.ui.screens.QuestsScreen;
import com.laststar.game.ui.screens.Screen;
import com.laststar.game.ui.screens.SettingsScreen;
import com.laststar.game.ui.screens.ShopScreen;
import com.laststar.game.ui.screens.ForgeScreen;
import com.laststar.game.ui.screens.CompanionsScreen;
import com.laststar.game.ui.screens.TravelScreen;
import com.laststar.game.ui.screens.SpellScreen;
import com.laststar.game.ui.screens.WardrobeScreen;

/**
 * The out-of-game interface: which page is showing, tap routing and the
 * animations shared by every page.
 *
 * <p>Kept free of Android types (it talks to {@link Prefs}, which does need a
 * Context, but only through its plain getters/setters) so the whole menu can be
 * rendered and asserted on in the headless harness.</p>
 */
public final class Menu {

    /** Which page the menu is showing. */
    public enum Page {
        HOME, ABOUT, SHOP, QUESTS, WARDROBE, SETTINGS,
        /**
         * The Wander, opened from the journal.
         *
         * <p>Reached from a screen rather than a tab: six tabs is already the
         * width of a phone, and the book belongs next to the quests that fill
         * it.</p>
         */
        SPELLS,
        /** Spell creation, reached from the Wander. */
        FORGE,
        /** The map of Elaris, reached from the journal. */
        TRAVEL,
        /** The company: companions, bonds and conversations. */
        COMPANY
    }

    private final TabBar tabs = new TabBar();
    private final Backdrop backdrop = new Backdrop();

    public final HomeScreen home = new HomeScreen(tabs);
    public final AboutScreen about = new AboutScreen();
    public final ShopScreen shop = new ShopScreen();
    public final QuestsScreen quests = new QuestsScreen();
    public final SpellScreen spellScreen = new SpellScreen();
    public final ForgeScreen forge = new ForgeScreen();
    public final TravelScreen travel = new TravelScreen();
    public final CompanionsScreen company = new CompanionsScreen();
    public final WardrobeScreen wardrobe = new WardrobeScreen();
    public final SettingsScreen settings = new SettingsScreen();

    public Page page = Page.HOME;
    /** The world the menu is reading, or null before start-up. */
    private com.laststar.game.world.World bound;
    private Screen shown;
    private float fade = 1f;
    private float time;
    /** Set when the player pressed START; consumed by the surface. */
    public boolean startRequested;

    /**
     * Points every page at the running game.
     *
     * <p>Called once at start-up and again whenever a page is opened, so a page
     * that shows live data -- the journal, the coin purse -- is never drawing a
     * stale world.</p>
     */
    public void bind(com.laststar.game.world.World world) {
        bound = world;
        quests.bind(world);
        shop.bind(world);
        spellScreen.bind(world);
        forge.bind(world);
        travel.bind(world);
        company.bind(world);
    }

    public Menu() {
        shown = home;
        tabs.setActive(TabBar.Tab.HOME);
    }

    /** Loads persisted preferences into the settings page. */
    public void load(Prefs prefs) {
        settings.audio = !prefs.isMuted();
        settings.masterVolume = prefs.volume();
        settings.haptics = prefs.haptics();
        settings.cameraShake = prefs.cameraShake();
        settings.difficulty = prefs.difficulty();
        wardrobe.selectedSwatch = prefs.skinTone();
        wardrobe.selectedBody = prefs.body();
    }

    /** Writes the settings page back to storage. */
    public void save(Prefs prefs) {
        prefs.setMuted(!settings.audio);
        prefs.setVolume(settings.masterVolume);
        prefs.setHaptics(settings.haptics);
        prefs.setCameraShake(settings.cameraShake);
        prefs.setDifficulty(settings.difficulty);
        prefs.setSkinTone(wardrobe.selectedSwatch);
        prefs.setBody(wardrobe.selectedBody);
    }

    public void update(float dt) {
        time += dt;
        backdrop.update(dt);
        tabs.update(dt);
        shown.update(dt);
        if (fade < 1f) {
            fade = Math.min(1f, fade + dt * 4f);
        }
    }

    private void show(Page p) {
        if (p == page) {
            return;
        }
        page = p;
        fade = 0f;
        if (bound != null) {
            bind(bound);
        }
        switch (p) {
            case HOME:
                shown = home;
                tabs.setActive(TabBar.Tab.HOME);
                break;
            case ABOUT:
                shown = about;
                tabs.setActive(TabBar.Tab.ABOUT);
                break;
            case SHOP:
                shown = shop;
                tabs.setActive(TabBar.Tab.SHOP);
                break;
            case QUESTS:
                shown = quests;
                tabs.setActive(TabBar.Tab.QUESTS);
                break;
            case WARDROBE:
                shown = wardrobe;
                tabs.setActive(TabBar.Tab.WARDROBE);
                break;
            case FORGE:
                shown = forge;
                tabs.setActive(TabBar.Tab.QUESTS);
                break;
            case TRAVEL:
                shown = travel;
                tabs.setActive(TabBar.Tab.QUESTS);
                break;
            case COMPANY:
                shown = company;
                tabs.setActive(TabBar.Tab.QUESTS);
                break;
            case SPELLS:
                shown = spellScreen;
                // The book has no tab of its own; the journal's stays lit so
                // the player can see where they came from.
                tabs.setActive(TabBar.Tab.QUESTS);
                break;
            case SETTINGS:
            default:
                shown = settings;
                tabs.setActive(TabBar.Tab.SETTINGS);
                break;
        }
    }

    /** Draws the whole menu: backdrop, page, heading and the bottom bar. */
    public void draw(UiBatch b, AtlasFont font) {
        float w = b.virtualWidth;
        float h = b.virtualHeight;

        backdrop.draw(b, backdropFor(page), 1f);
        float a = 0.35f + 0.65f * fade;
        shown.draw(b, font, a);
        shown.drawTitle(b, font, a);
        tabs.draw(b, font, 1f);
    }

    private static Backdrop.Id backdropFor(Page p) {
        switch (p) {
            case SETTINGS:
                return Backdrop.Id.SETTINGS;
            case QUESTS:
                return Backdrop.Id.QUESTS;
            case WARDROBE:
            case SPELLS:
            case FORGE:
            case TRAVEL:
            case COMPANY:
                return Backdrop.Id.WARDROBE;
            case SHOP:
                return Backdrop.Id.SHOP;
            case ABOUT:
            case HOME:
            default:
                return Backdrop.Id.HOME;
        }
    }

    /**
     * Routes a tap in virtual coordinates.
     *
     * @return true when the menu consumed the event
     */
    public boolean tap(float x, float y, float w, float h) {
        int tabIndex = tabs.hitTest(x, y, w, h);
        if (tabIndex >= 0) {
            tabs.press(tabIndex);
            show(pageFor(tabs.tabAt(tabIndex)));
            return true;
        }
        Screen.Action action = shown.hit(x, y, w, h, tabs);
        if (action == Screen.Action.OPEN_FORGE) {
            show(Page.FORGE);
            return true;
        }
        if (action == Screen.Action.OPEN_TRAVEL) {
            show(Page.TRAVEL);
            return true;
        }
        if (action == Screen.Action.OPEN_COMPANY) {
            show(Page.COMPANY);
            return true;
        }
        if (action == Screen.Action.OPEN_SPELLS) {
            show(Page.SPELLS);
            // The book has no tab of its own, so the journal's tab stays lit:
            // the player came from there and back returns there.
            return true;
        }
        if (action == Screen.Action.START_GAME) {
            startRequested = true;
            home.pressTab();
            return true;
        }
        if (action == Screen.Action.BACK) {
            // Back out of the book to the journal it was opened from.
            if (page == Page.COMPANY) {
                show(Page.TRAVEL);
            } else if (page == Page.TRAVEL) {
                show(Page.QUESTS);
            } else if (page == Page.FORGE) {
                show(Page.SPELLS);
            } else if (page == Page.SPELLS) {
                show(Page.QUESTS);
            } else {
                show(Page.HOME);
            }
            return true;
        }
        return true;
    }

    /** Called on pointer up, so sliders know the drag finished. */
    public void release() {
        settings.release();
    }

    private static Page pageFor(TabBar.Tab tab) {
        switch (tab) {
            case ABOUT:
                return Page.ABOUT;
            case SHOP:
                return Page.SHOP;
            case QUESTS:
                return Page.QUESTS;
            case WARDROBE:
                return Page.WARDROBE;
            case SETTINGS:
                return Page.SETTINGS;
            case HOME:
            default:
                return Page.HOME;
        }
    }

    /** Test/preview hook: makes {@code page} the visible page without animating. */
    public void shownForTest(Page page) {
        this.page = page;
        fade = 1f;
        switch (page) {
            case ABOUT:
                shown = about;
                tabs.setActive(TabBar.Tab.ABOUT);
                break;
            case SHOP:
                shown = shop;
                tabs.setActive(TabBar.Tab.SHOP);
                break;
            case QUESTS:
                shown = quests;
                tabs.setActive(TabBar.Tab.QUESTS);
                break;
            case WARDROBE:
                shown = wardrobe;
                tabs.setActive(TabBar.Tab.WARDROBE);
                break;
            case FORGE:
                shown = forge;
                tabs.setActive(TabBar.Tab.QUESTS);
                break;
            case TRAVEL:
                shown = travel;
                tabs.setActive(TabBar.Tab.QUESTS);
                break;
            case COMPANY:
                shown = company;
                tabs.setActive(TabBar.Tab.QUESTS);
                break;
            case SPELLS:
                shown = spellScreen;
                tabs.setActive(TabBar.Tab.QUESTS);
                break;
            case SETTINGS:
                shown = settings;
                tabs.setActive(TabBar.Tab.SETTINGS);
                break;
            case HOME:
            default:
                shown = home;
                tabs.setActive(TabBar.Tab.HOME);
                break;
        }
    }

    public TabBar tabs() {
        return tabs;
    }

    public float time() {
        return time;
    }
}
