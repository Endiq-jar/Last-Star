package com.laststar.game.world;

/**
 * The Wander: the spell-book, and the magic system it carries.
 *
 * <p>Per {@code Resources.txt} the Wander is the source of every spell the
 * player will ever have. It holds {@link #MAX_LEVEL} tiers of mastery per spell,
 * and spells grow with use rather than being bought, so the book is a thing you
 * carry and cultivate rather than a shop.</p>
 *
 * <p>Only four spells are awake at the start. The design contract is that a new
 * player gets a small, readable kit -- one strike, one close cut, one defence,
 * one heavy -- and that everything else is found. Four is also the most that
 * fits on the action bar before the buttons become the game.</p>
 *
 * <p>Light is the fuel. It recovers slowly on its own and quickly from pickups,
 * so casting is a rhythm of spending and recovering rather than a resource you
 * simply run out of.</p>
 *
 * <p>Deliberately free of Android types: the desktop harness drives the whole
 * spell system directly.</p>
 */
public final class Wander {

    /** Mastery ceiling. Spells stop growing here, not when they stop being used. */
    public static final int MAX_LEVEL = 30;

    /** Light at full. */
    public static final float MAX_LIGHT = 100f;
    /** Light regained per second with nothing cast. */
    public static final float LIGHT_REGEN = 4.5f;
    /** Fraction of the maximum restored by one Light pickup. */
    public static final float PICKUP_RESTORE = 0.35f;
    /** Passive mastery, so a spell used as a tool still creeps up over a run. */
    public static final float PASSIVE_XP = 0.4f;

    /** Effect a spell produces on cast, resolved by {@link World}. */
    public enum Effect {
        /** A fast bolt at the first thing in front. */
        BOLT,
        /** A wide cut centred on the player. */
        SWEEP,
        /** A ward on the player, not on anything else. */
        WARD,
        /** A heavy strike that staggers what it hits. */
        STARFALL
    }

    /** One page of the Wander. */
    public static final class Spell {
        public final String name;
        public final String school;
        public final float lightCost;
        public final float baseDamage;
        public final float cooldown;
        public final float range;
        public final Effect effect;

        public int level = 1;
        public float xp;
        public float cooldownLeft;
        public boolean unlocked;

        Spell(String name, String school, float lightCost, float baseDamage,
              float cooldown, float range, Effect effect, boolean unlocked) {
            this.name = name;
            this.school = school;
            this.lightCost = lightCost;
            this.baseDamage = baseDamage;
            this.cooldown = cooldown;
            this.range = range;
            this.effect = effect;
            this.unlocked = unlocked;
        }

        /** Whether the spell has reached a mastery stage that changes it. */
        public boolean has(int stageLevel) {
            return unlocked && level >= stageLevel;
        }

        /**
         * Damage at the spell's current mastery.
         *
         * <p>Linear rather than exponential: at level 30 this is a little over
         * 3.4x the starting damage, which keeps early enemies relevant while
         * still making mastery worth pursuing.</p>
         */
        public float damage() {
            return baseDamage * (1f + (level - 1) * 0.085f);
        }

        /** Experience needed to reach the next level. */
        public float xpToNext() {
            return 20f + level * 12f;
        }

        /** Adds experience and levels the spell when it crosses a threshold. */
        public void awardXp(float amount) {
            if (!unlocked || level >= MAX_LEVEL) {
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

        /** Castable right now: awake, off cooldown, and affordable. */
        public boolean ready(Wander book) {
            return unlocked && cooldownLeft <= 0f && book.light >= lightCost;
        }
    }

    /** The four starting pages, in action-bar order. */
    public final Spell[] spells = new Spell[]{
            new Spell("Ember Shot", "Elemental", 8f, 14f, 0.55f, 26f,
                    Effect.BOLT, true),
            new Spell("Gale Cut", "Elemental", 6f, 10f, 0.40f, 6f,
                    Effect.SWEEP, true),
            new Spell("Ward", "Defensive", 12f, 0f, 6f, 3f,
                    Effect.WARD, true),
            new Spell("Starfall", "Arcane", 22f, 34f, 8f, 18f,
                    Effect.STARFALL, true)
    };

    /** Current Light, the fuel every spell draws from. */
    public float light = MAX_LIGHT;
    /** Set when a cast was refused for lack of Light, for the HUD to flash. */
    public float lightStarved;
    /** Index of the spell cast this frame, or -1. Read by the renderer. */
    public int lastCast = -1;

    /** Advances regeneration, cooldowns and passive mastery. */
    public void tick(float dt) {
        light = Math.min(MAX_LIGHT, light + LIGHT_REGEN * dt);
        if (lightStarved > 0f) {
            lightStarved = Math.max(0f, lightStarved - dt);
        }
        lastCast = -1;
        for (int i = 0; i < spells.length; i++) {
            Spell s = spells[i];
            if (s.cooldownLeft > 0f) {
                s.cooldownLeft = Math.max(0f, s.cooldownLeft - dt);
            }
            // Mastery drifts up while the book is open, so a page used mostly
            // as a tool still improves over a long run.
            s.awardXp(dt * PASSIVE_XP);
        }
    }

    /** Whether the spell can be cast this instant. */
    public boolean canCast(int slot) {
        if (slot < 0 || slot >= spells.length) {
            return false;
        }
        return spells[slot].ready(this);
    }

    /**
     * Spends the Light and starts the cooldown.
     *
     * @return the spell that was cast, or null when the cast was refused
     */
    public Spell cast(int slot) {
        if (!canCast(slot)) {
            if (slot >= 0 && slot < spells.length && spells[slot].unlocked
                    && light < spells[slot].lightCost) {
                lightStarved = 0.6f;
            }
            return null;
        }
        Spell s = spells[slot];
        light -= s.lightCost;
        s.cooldownLeft = s.cooldown;
        lastCast = slot;
        return s;
    }

    /** Light as a fraction of full, for the HUD bar. */
    public float lightFraction() {
        return light / MAX_LIGHT;
    }

    public int unlockedCount() {
        int n = 0;
        for (int i = 0; i < spells.length; i++) {
            if (spells[i].unlocked) {
                n++;
            }
        }
        return n;
    }

    /** Restores Light from a pickup and teaches the book something. */
    public void absorbLight() {
        light = Math.min(MAX_LIGHT, light + MAX_LIGHT * PICKUP_RESTORE);
        for (int i = 0; i < spells.length; i++) {
            spells[i].awardXp(4f);
        }
    }

    /** Highest mastery reached, shown on the pause screen. */
    public int highestLevel() {
        int best = 0;
        for (int i = 0; i < spells.length; i++) {
            best = Math.max(best, spells[i].level);
        }
        return best;
    }
}
