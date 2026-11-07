package com.laststar.game.world;

import com.laststar.game.world.lore.Companions;

/**
 * Who is travelling with the player, how far they trust them, and what the
 * pair of them unlocks together.
 *
 * <p>Two companions travel at once, out of six. The limit is the design's
 * rather than a technical one: the document wants synergy to be a discovery,
 * and a party of six has no choice in it.</p>
 */
public final class Party {

    /** How many companions can travel together. */
    public static final int MAX_ACTIVE = 2;

    /** Trust points per companion. */
    public final int[] trust = new int[Companions.count()];
    /** Whether a companion has joined, in the order they did. */
    public final boolean[] recruited = new boolean[Companions.count()];
    /** The active pair, as companion indices, or -1. */
    public final int[] active = {-1, -1};

    /** Remaining cooldown per companion's ability. */
    public final float[] abilityCooldown = new float[Companions.count()];

    /** How many conversation choices each companion has answered. */
    public final int[] conversations = new int[Companions.count()];

    /** Recruits a companion, filling an active slot if one is free. */
    public boolean recruit(int companion) {
        if (companion < 0 || companion >= recruited.length || recruited[companion]) {
            return false;
        }
        recruited[companion] = true;
        for (int i = 0; i < active.length; i++) {
            if (active[i] < 0) {
                active[i] = companion;
                break;
            }
        }
        return true;
    }

    /** Puts a recruited companion in a slot, or -1 to clear it. */
    public boolean setActive(int slot, int companion) {
        if (slot < 0 || slot >= active.length) {
            return false;
        }
        if (companion >= 0 && (companion >= recruited.length
                || !recruited[companion])) {
            return false;
        }
        active[slot] = companion;
        return true;
    }

    /** Adds trust, and reports whether the bond deepened. */
    public boolean addTrust(int companion, int amount) {
        if (companion < 0 || companion >= trust.length || !recruited[companion]) {
            return false;
        }
        Companions.Bond before = bond(companion);
        trust[companion] = Math.max(0, trust[companion] + amount);
        return bond(companion) != before;
    }

    /** A companion's current bond. */
    public Companions.Bond bond(int companion) {
        if (companion < 0 || companion >= trust.length) {
            return Companions.Bond.STRANGER;
        }
        return Companions.Bond.at(trust[companion]);
    }

    /** How many companions have been recruited. */
    public int recruitedCount() {
        int n = 0;
        for (int i = 0; i < recruited.length; i++) {
            if (recruited[i]) {
                n++;
            }
        }
        return n;
    }

    /** The synergy the current pair unlocks, or null. */
    public Companions.Synergy activeSynergy() {
        if (active[0] < 0 || active[1] < 0) {
            return null;
        }
        return Companions.synergyBetween(active[0], active[1]);
    }

    /**
     * Whether a companion's ability can be used now.
     *
     * <p>Bond gates the strongest uses: an ALLY will act for you, a STRANGER
     * will not. That is what makes trust mechanical rather than a number on a
     * codex page.</p>
     */
    public boolean abilityReady(int companion) {
        if (companion < 0 || companion >= recruited.length) {
            return false;
        }
        if (!recruited[companion] || abilityCooldown[companion] > 0f) {
            return false;
        }
        return bond(companion).threshold >= Companions.Bond.ALLY.threshold;
    }

    /** Marks a companion's ability used. */
    public void useAbility(int companion) {
        if (companion < 0 || companion >= abilityCooldown.length) {
            return;
        }
        abilityCooldown[companion] = Companions.get(companion).ability.cooldown;
        // Using an ability together is how a bond deepens between fights.
        addTrust(companion, 1);
    }

    /** Advances cooldowns. */
    public void tick(float dt) {
        for (int i = 0; i < abilityCooldown.length; i++) {
            if (abilityCooldown[i] > 0f) {
                abilityCooldown[i] = Math.max(0f, abilityCooldown[i] - dt);
            }
        }
    }

    /** Records a conversation choice, which is the main route to trust. */
    public void converse(int companion, int choice) {
        if (companion < 0 || companion >= conversations.length) {
            return;
        }
        conversations[companion]++;
        // Not every choice earns trust: one that pushes a companion away costs
        // some, which is what makes the choice matter.
        addRecruitedTrust(companion, choice > 0 ? 2 : -1);
    }

    private void addRecruitedTrust(int companion, int amount) {
        if (recruited[companion]) {
            trust[companion] = Math.max(0, trust[companion] + amount);
        }
    }
}
