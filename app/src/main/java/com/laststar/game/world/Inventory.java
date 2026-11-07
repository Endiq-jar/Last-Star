package com.laststar.game.world;

import com.laststar.game.world.lore.Gear;

/**
 * What the player owns, and what they are wearing.
 *
 * <p>Three worn slots, one of each kind, so a choice is always a trade. The
 * modifiers are asked for by whatever cares -- the blade asks for its multiplier
 * when a swing lands, the book asks for its Light ceiling when it regenerates --
 * rather than being baked into the player's fields at equip time. Baking them in
 * means every later change to health or capacity has to remember to unbake them,
 * which is how equipment systems acquire bugs.</p>
 */
public final class Inventory {

    /** Whether each piece has been acquired. */
    public final boolean[] owned = new boolean[Gear.count()];
    /** Worn piece per slot, or -1. */
    public final int[] worn = {-1, -1, -1};

    /** Whether a piece is owned. */
    public boolean has(int id) {
        return id >= 0 && id < owned.length && owned[id];
    }

    /** Whether a piece is being worn. */
    public boolean isWorn(int id) {
        for (int i = 0; i < worn.length; i++) {
            if (worn[i] == id) {
                return true;
            }
        }
        return false;
    }

    /** Adds a piece to the inventory. */
    public boolean acquire(int id) {
        if (id < 0 || id >= owned.length) {
            return false;
        }
        if (owned[id]) {
            return false;
        }
        owned[id] = true;
        return true;
    }

    /**
     * Wears a piece, or takes it off if it is already worn.
     *
     * <p>Taking it off is the same tap, because a player comparing two blades
     * wants to see the difference immediately rather than hunt for an unequip
     * control.</p>
     */
    public boolean toggle(int id) {
        if (!has(id)) {
            return false;
        }
        Gear.Def def = Gear.get(id);
        int slot = def.slot.ordinal();
        if (worn[slot] == id) {
            worn[slot] = -1;
            return false;
        }
        worn[slot] = id;
        return true;
    }

    /** The piece worn in a slot, or null. */
    public Gear.Def worn(Gear.Slot slot) {
        int id = worn[slot.ordinal()];
        return id < 0 ? null : Gear.get(id);
    }

    // --- the modifiers, combined ---------------------------------------------

    /** Combined maximum-health addition. */
    public float healthBonus() {
        float v = 0f;
        for (int i = 0; i < worn.length; i++) {
            if (worn[i] >= 0) {
                v += Gear.get(worn[i]).mods.health;
            }
        }
        return v;
    }

    /** Combined melee multiplier. */
    public float meleeMultiplier() {
        float v = 1f;
        for (int i = 0; i < worn.length; i++) {
            if (worn[i] >= 0) {
                v *= Gear.get(worn[i]).mods.melee;
            }
        }
        return v;
    }

    /** Combined spell multiplier. */
    public float spellMultiplier() {
        float v = 1f;
        for (int i = 0; i < worn.length; i++) {
            if (worn[i] >= 0) {
                v *= Gear.get(worn[i]).mods.spell;
            }
        }
        return v;
    }

    /** Combined Light-capacity addition. */
    public float lightBonus() {
        float v = 0f;
        for (int i = 0; i < worn.length; i++) {
            if (worn[i] >= 0) {
                v += Gear.get(worn[i]).mods.light;
            }
        }
        return v;
    }

    /** Combined Light-regeneration multiplier. */
    public float regenMultiplier() {
        float v = 1f;
        for (int i = 0; i < worn.length; i++) {
            if (worn[i] >= 0) {
                v *= Gear.get(worn[i]).mods.regen;
            }
        }
        return v;
    }

    /** Combined cooldown multiplier for dodge and parry; under 1 is faster. */
    public float agilityMultiplier() {
        float v = 1f;
        for (int i = 0; i < worn.length; i++) {
            if (worn[i] >= 0) {
                v *= Gear.get(worn[i]).mods.agility;
            }
        }
        return v;
    }

    /** How many pieces are worn. */
    public int wornCount() {
        int n = 0;
        for (int i = 0; i < worn.length; i++) {
            if (worn[i] >= 0) {
                n++;
            }
        }
        return n;
    }

    /** How many pieces are owned. */
    public int ownedCount() {
        int n = 0;
        for (int i = 0; i < owned.length; i++) {
            if (owned[i]) {
                n++;
            }
        }
        return n;
    }

    /** A one-line summary of everything worn, for the HUD and the shop. */
    public String summary() {
        if (wornCount() == 0) {
            return "NOTHING WORN";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < worn.length; i++) {
            if (worn[i] >= 0) {
                if (sb.length() > 0) {
                    sb.append("  ");
                }
                sb.append(Gear.get(worn[i]).name);
            }
        }
        return sb.toString();
    }
}
