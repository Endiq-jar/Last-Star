package com.laststar.game.world;

import com.laststar.game.world.lore.SpellCorpus;

/**
 * The Wander, as a working spell-book.
 *
 * <p>This replaces the four-page placeholder with the full corpus: a hundred
 * and one spells, each with its own mastery track, and only four awake at the
 * start. The design document is explicit that a new player gets two to four
 * pages and has to work for the rest, so {@link #awake()} is the gate the whole
 * system hangs from.</p>
 *
 * <p>Per-spell state lives here rather than on the corpus definition, because a
 * {@link SpellCorpus.Def} is shared data and must never be mutated.</p>
 */
public final class SpellBook {

    /** Mastery ceiling, from the document. */
    public static final int MAX_LEVEL = 30;

    /** Light at full, before capacity upgrades. */
    public static final float BASE_LIGHT = 100f;
    /** Light regained per second. */
    public static final float LIGHT_REGEN = 4.5f;
    /** Fraction of capacity restored by one pickup. */
    public static final float PICKUP_RESTORE = 0.35f;
    /** Passive mastery, so a page used as a tool still creeps up over a run. */
    public static final float PASSIVE_XP = 0.4f;

    /** One page the player owns, and how far it has been developed. */
    public static final class Page {
        public final SpellCorpus.Def def;
        public int level = 1;
        public float xp;
        public float cooldownLeft;
        public boolean awake;

        Page(SpellCorpus.Def def, boolean awake) {
            this.def = def;
            this.awake = awake;
        }

        /** Damage at this mastery. */
        public float damage() {
            return def.baseDamage * def.damageScaleAt(level);
        }

        /** Experience needed to reach the next level. */
        public float xpToNext() {
            return 20f + level * 12f;
        }

        /** Adds experience and levels the page as it crosses thresholds. */
        public void awardXp(float amount) {
            if (!awake || level >= MAX_LEVEL) {
                return;
            }
            xp += amount;
            while (level < MAX_LEVEL && xp >= xpToNext()) {
                xp -= xpToNext();
                level++;
            }
            if (level >= MAX_LEVEL) {
                xp = 0f;
            }
        }

        /** 0 to 1 through the current level, for the HUD rings. */
        public float progress() {
            if (level >= MAX_LEVEL) {
                return 1f;
            }
            float need = xpToNext();
            return need <= 0f ? 0f : Math.min(1f, xp / need);
        }

        /** Whether a trait has been unlocked at this mastery. */
        public boolean has(SpellCorpus.Trait trait) {
            return def.has(trait, level);
        }

        /** The most recent mastery stage, for the spell's description. */
        public SpellCorpus.Stage stage() {
            return def.stageAt(level);
        }

        /** Castable right now: awake, off cooldown, and affordable. */
        public boolean ready(SpellBook book) {
            return awake && cooldownLeft <= 0f && book.light >= def.lightCost;
        }
    }

    /** One page per spell in the corpus, indexed the same way. */
    public final Page[] pages = new Page[SpellCorpus.count()];

    /** Current Light. */
    public float light = BASE_LIGHT;
    /** Maximum Light, raised by quests and progression. */
    public float lightCapacity = BASE_LIGHT;
    /** Set when a cast was refused for lack of Light, for the HUD to flash. */
    public float lightStarved;
    /** Index of the page cast this frame, or -1. */
    public int lastCast = -1;

    /** Indices of the pages on the action bar, in slot order. */
    public final int[] equipped = new int[4];

    public SpellBook() {
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new Page(SpellCorpus.SPELLS[i], SpellCorpus.SPELLS[i].foundIn < 0);
        }
        // The four starting pages, in action-bar order.
        int slot = 0;
        for (int i = 0; i < pages.length && slot < equipped.length; i++) {
            if (pages[i].awake) {
                equipped[slot++] = i;
            }
        }
        while (slot < equipped.length) {
            equipped[slot++] = -1;
        }
    }

    /** Advances regeneration, cooldowns and passive mastery. */
    public void tick(float dt) {
        light = Math.min(lightCapacity, light + LIGHT_REGEN * dt);
        if (lightStarved > 0f) {
            lightStarved = Math.max(0f, lightStarved - dt);
        }
        lastCast = -1;
        for (int i = 0; i < pages.length; i++) {
            Page p = pages[i];
            if (p.cooldownLeft > 0f) {
                p.cooldownLeft = Math.max(0f, p.cooldownLeft - dt);
            }
            p.awardXp(dt * PASSIVE_XP);
        }
    }

    /** Whether the page in a given action-bar slot can be cast now. */
    public boolean canCast(int slot) {
        int index = indexAt(slot);
        return index >= 0 && pages[index].ready(this);
    }

    /** The page in an action-bar slot, or null for an empty slot. */
    public Page pageAt(int slot) {
        int index = indexAt(slot);
        return index < 0 ? null : pages[index];
    }

    private int indexAt(int slot) {
        if (slot < 0 || slot >= equipped.length) {
            return -1;
        }
        return equipped[slot];
    }

    /**
     * Spends the Light and starts the cooldown.
     *
     * @return the page that was cast, or null when the cast was refused
     */
    public Page cast(int slot) {
        int index = indexAt(slot);
        if (index < 0) {
            return null;
        }
        Page p = pages[index];
        if (!p.ready(this)) {
            if (p.awake && light < p.def.lightCost) {
                lightStarved = 0.6f;
            }
            return null;
        }
        light -= p.def.lightCost;
        p.cooldownLeft = p.def.cooldown;
        lastCast = index;
        return p;
    }

    /** Light as a fraction of capacity, for the HUD bar. */
    public float lightFraction() {
        return lightCapacity <= 0f ? 0f : light / lightCapacity;
    }

    /**
     * Wakes a page the player has found.
     *
     * @return true when this call actually taught something new
     */
    public boolean awaken(int index) {
        if (index < 0 || index >= pages.length || pages[index].awake) {
            return false;
        }
        pages[index].awake = true;
        // A newly found page takes the first empty slot, or replaces nothing if
        // the bar is full -- the player chooses what to carry.
        for (int i = 0; i < equipped.length; i++) {
            if (equipped[i] < 0) {
                equipped[i] = index;
                break;
            }
        }
        return true;
    }

    /** How many pages are awake. */
    public int awakeCount() {
        int n = 0;
        for (int i = 0; i < pages.length; i++) {
            if (pages[i].awake) {
                n++;
            }
        }
        return n;
    }

    /** How many pages in a category are awake. */
    public int awakeCount(SpellCorpus.Category category) {
        int n = 0;
        for (int i = 0; i < pages.length; i++) {
            if (pages[i].awake && pages[i].def.category == category) {
                n++;
            }
        }
        return n;
    }

    /** Puts a page on the action bar, into a given slot. */
    public void equip(int slot, int pageIndex) {
        if (slot < 0 || slot >= equipped.length) {
            return;
        }
        if (pageIndex >= 0 && (pageIndex >= pages.length || !pages[pageIndex].awake)) {
            return;
        }
        equipped[slot] = pageIndex;
    }

    /**
     * Puts a page on the next free slot, or takes it off if it is already on
     * the bar.
     *
     * <p>The bar keeps its slots closed up: removing the second of four shifts
     * the rest down rather than leaving a hole the player has to tap past.</p>
     *
     * @return the slot it ended up in, or -1 if it was removed or could not be
     *         placed
     */
    public int toggleEquip(int spell) {
        if (spell < 0 || spell >= pages.length || !pages[spell].awake) {
            return -1;
        }
        for (int i = 0; i < equipped.length; i++) {
            if (equipped[i] == spell) {
                for (int j = i; j < equipped.length - 1; j++) {
                    equipped[j] = equipped[j + 1];
                }
                equipped[equipped.length - 1] = -1;
                return -1;
            }
        }
        for (int i = 0; i < equipped.length; i++) {
            if (equipped[i] < 0) {
                equipped[i] = spell;
                return i;
            }
        }
        // A full bar takes the page in the last slot.
        equipped[equipped.length - 1] = spell;
        return equipped.length - 1;
    }

    /** Raises the Light ceiling, from a quest reward. */
    public void raiseCapacity(float amount) {
        lightCapacity = Math.min(400f, lightCapacity + amount);
        light = Math.min(lightCapacity, light + amount);
    }

    /** Restores Light from a pickup and teaches every page something. */
    public void absorbLight() {
        light = Math.min(lightCapacity, light + lightCapacity * PICKUP_RESTORE);
        for (int i = 0; i < pages.length; i++) {
            pages[i].awardXp(4f);
        }
    }

    /** Highest mastery reached, for the pause screen. */
    public int highestLevel() {
        int best = 0;
        for (int i = 0; i < pages.length; i++) {
            if (pages[i].awake) {
                best = Math.max(best, pages[i].level);
            }
        }
        return best;
    }
}
