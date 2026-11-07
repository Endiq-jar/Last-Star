package com.laststar.game.ui.screens;

import com.laststar.game.ui.AtlasFont;
import com.laststar.game.ui.TabBar;
import com.laststar.game.ui.UiBatch;

/** One menu page. */
public interface Screen {

    /** Logical outcome of a tap. */
    enum Action {
        NONE,
        START_GAME,
        NAME_CHANGED,
        SWITCH_TAB,
        /** Asks the menu to open the spell book. */
        OPEN_SPELLS,
        BACK
    }

    /** Gives the page the live game state, for pages that show real data. */
    default void bind(com.laststar.game.world.World world) {
    }

    void update(float dt);

    /** Draws the page content (everything except backdrop, title and tab bar). */
    void draw(UiBatch b, AtlasFont font, float alpha);

    /** Draws the page's heading row. */
    void drawTitle(UiBatch b, AtlasFont font, float alpha);

    /** Handles a tap in virtual coordinates. */
    Action hit(float x, float y, float w, float h, TabBar tabBar);
}
