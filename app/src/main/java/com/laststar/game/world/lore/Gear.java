package com.laststar.game.world.lore;

/**
 * Every piece of equipment in the game.
 *
 * <p>The shop had eight names and eight descriptions and no effect whatsoever:
 * buying anything took the coin and changed nothing. This is what those names
 * actually are, and what wearing one does.</p>
 *
 * <p>Equipment here is a set of modifiers rather than a level number, so a
 * choice is a choice. The Ember blade trades health for damage; the Cloak
 * trades damage for health; the Compass gives up everything for Light. None of
 * them is strictly better than another, which is the only way a shop with eight
 * things in it is interesting on the second visit.</p>
 */
public final class Gear {

    /** Where a piece goes. One of each may be worn. */
    public enum Slot {
        BLADE,
        CLOAK,
        RELIC
    }

    /** How a piece changes the player. All are multipliers or flat additions. */
    public static final class Mods {
        /** Added to maximum health. */
        public final float health;
        /** Multiplies melee damage. */
        public final float melee;
        /** Multiplies spell damage. */
        public final float spell;
        /** Added to Light capacity. */
        public final float light;
        /** Multiplies Light regeneration. */
        public final float regen;
        /** Multiplies the dodge and parry cooldowns, so < 1 means faster. */
        public final float agility;

        public Mods(float health, float melee, float spell, float light,
                    float regen, float agility) {
            this.health = health;
            this.melee = melee;
            this.spell = spell;
            this.light = light;
            this.regen = regen;
            this.agility = agility;
        }

        public static final Mods NONE = new Mods(0f, 1f, 1f, 0f, 1f, 1f);
    }

    /** One piece of equipment. */
    public static final class Def {
        public final int id;
        public final String name;
        public final Slot slot;
        /** Where it come from: a settlement index, or -1 for the shop. */
        public final int from;
        public final String description;
        public final Mods mods;
        public final int price;

        Def(int id, String name, Slot slot, int from, String description,
            Mods mods, int price) {
            this.id = id;
            this.name = name;
            this.slot = slot;
            this.from = from;
            this.description = description;
            this.mods = mods;
            this.price = price;
        }

        /** One line naming what it does, for the shop and the wardrobe. */
        public String effectLine() {
            StringBuilder sb = new StringBuilder();
            append(sb, "HP", mods.health);
            if (mods.melee != 1f) {
                append(sb, "BLADE", (mods.melee - 1f) * 100f, true);
            }
            if (mods.spell != 1f) {
                append(sb, "SPELL", (mods.spell - 1f) * 100f, true);
            }
            append(sb, "LIGHT", mods.light);
            if (mods.regen != 1f) {
                append(sb, "REGEN", (mods.regen - 1f) * 100f, true);
            }
            if (mods.agility != 1f) {
                // Faster is a negative cooldown, so invert the sign to read right.
                append(sb, "SPEED", (1f - mods.agility) * 100f, true);
            }
            return sb.length() == 0 ? "NO EFFECT" : sb.toString().trim();
        }

        private static void append(StringBuilder sb, String label, float v) {
            if (v == 0f) {
                return;
            }
            sb.append(v > 0 ? "+" : "").append(Math.round(v)).append(' ')
                    .append(label).append("  ");
        }

        private static void append(StringBuilder sb, String label, float v, boolean pct) {
            sb.append(v >= 0 ? "+" : "").append(Math.round(v)).append("% ")
                    .append(label).append("  ");
        }
    }

    public static final Def[] ALL = build();

    private static Def[] build() {
        java.util.List<Def> l = new java.util.ArrayList<Def>();
        int id = 0;

        // Blades: all damage, each trading something different.
        l.add(new Def(id++, "EMBER", Slot.BLADE, 0,
                "A BLADE THAT REMEMBERS THE SUN.",
                new Mods(-10f, 1.30f, 1f, 0f, 1f, 1f), 1200));
        l.add(new Def(id++, "FROSTBITE", Slot.BLADE, 5,
                "IT CUTS SLOWLY AND THE CUT STAYS.",
                new Mods(0f, 1.18f, 1.05f, 0f, 1f, 0.94f), 900));
        l.add(new Def(id++, "GLASS EDGE", Slot.BLADE, 4,
                "SHARP ENOUGH TO CUT WHAT IS NOT THERE.",
                new Mods(-25f, 1.55f, 1.15f, 0f, 1f, 1f), 2400));

        // Cloaks: survival, at the cost of reach.
        l.add(new Def(id++, "CLOAK", Slot.CLOAK, 1,
                "STITCHED FROM STAR-SPACE.",
                new Mods(40f, 0.92f, 1f, 0f, 1f, 1f), 900));
        l.add(new Def(id++, "TIDE WRAP", Slot.CLOAK, 3,
                "STILL WET. IT NEVER DRIES.",
                new Mods(20f, 1f, 1f, 15f, 1.25f, 1f), 700));
        l.add(new Def(id++, "WARDEN PLATE", Slot.CLOAK, 5,
                "HELD A GATE FOR ELEVEN DAYS.",
                new Mods(75f, 0.85f, 0.95f, 0f, 1f, 0.90f), 1600));

        // Relics: the odd ones, each good at exactly one thing.
        l.add(new Def(id++, "LANTERN", Slot.RELIC, 0,
                "BURNS LOW, BUT NEVER GOES OUT.",
                new Mods(0f, 1f, 1f, 30f, 1.45f, 1f), 650));
        l.add(new Def(id++, "SHARD", Slot.RELIC, 2,
                "A FRAGMENT THAT STILL HUMS.",
                new Mods(0f, 1f, 1.28f, 20f, 1f, 1f), 480));
        l.add(new Def(id++, "SEAL", Slot.RELIC, 8,
                "PRESSED WAX FROM THE LAST DOOR.",
                new Mods(15f, 1f, 1f, 25f, 1.1f, 0.88f), 340));
        l.add(new Def(id++, "COMPASS", Slot.RELIC, 7,
                "POINTS AT WHAT YOU HAVE LOST.",
                new Mods(-15f, 1f, 1.12f, 45f, 1.3f, 1f), 320));
        l.add(new Def(id++, "HORN", Slot.RELIC, 10,
                "SOUNDS ONCE PER NIGHT. ONCE ONLY.",
                new Mods(30f, 1.15f, 1f, 0f, 0.9f, 1.05f), 480));
        l.add(new Def(id++, "GLASS", Slot.RELIC, 4,
                "ROUGH GLASS FULL OF GREY LIGHT.",
                new Mods(0f, 1f, 1.35f, -10f, 0.85f, 1f), 260));

        return l.toArray(new Def[0]);
    }

    public static Def get(int id) {
        if (id < 0 || id >= ALL.length) {
            return null;
        }
        return ALL[id];
    }

    public static int count() {
        return ALL.length;
    }

    /** The pieces sold in the shop, in order. */
    public static Def[] shopStock() {
        java.util.List<Def> l = new java.util.ArrayList<Def>();
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].from < 0 || ALL[i].price > 0) {
                l.add(ALL[i]);
            }
        }
        return l.toArray(new Def[0]);
    }
}
