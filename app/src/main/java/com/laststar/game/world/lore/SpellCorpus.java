package com.laststar.game.world.lore;

import java.util.ArrayList;
import java.util.List;

/**
 * Every spell in the game: their categories, their shape, and what mastery
 * actually does to them.
 *
 * <p>The design document asks for more than a hundred spells across eight
 * categories with individual mastery to level 30, and is explicit that spells
 * should not simply collect damage as they level. So a spell here is a
 * <em>shape</em> plus a <em>payload</em> plus a <em>curve</em>: a fire bolt and a
 * void lance share a projectile shape but differ in what they leave behind,
 * while Ember Shot's own curve turns it from a single bolt into an explosive,
 * then a piercing, then a burning, then a charged, then an evolved spell.</p>
 *
 * <p>This is data, not behaviour. {@code SpellBook} reads it; the rendering and
 * the world resolution read {@code SpellBook}'s compiled form. Keeping the
 * corpus separate means the whole spell list can be reviewed in one place.</p>
 */
public final class SpellCorpus {

    /** The eight categories from the document, with their intended rarity. */
    public enum Category {
        ELEMENTAL("ELEMENTAL", 30),
        SPATIAL("SPATIAL", 10),
        DEFENSIVE("DEFENSIVE", 40),
        ENHANCEMENT("ENHANCEMENT", 20),
        SUMMONING("SUMMONING", 10),
        ILLUSION("ILLUSION", 5),
        ARCANE("ARCANE", 3),
        FORBIDDEN("FORBIDDEN", 2);

        public final String label;
        /** Weight from the document's table. */
        public final int chance;

        Category(String label, int chance) {
            this.label = label;
            this.chance = chance;
        }
    }

    /** What the spell physically is when it resolves. */
    public enum Shape {
        /** A single travelling bolt at the nearest target. */
        BOLT,
        /** A cut centred on the caster. */
        SWEEP,
        /** A ward on the caster. */
        WARD,
        /** A heavy strike from above. */
        STARFALL,
        /** A field left on the ground that keeps working. */
        FIELD,
        /** A dash that damages what it passes through. */
        DASH,
        /** A summoned ally that fights for a while. */
        SUMMON,
        /** A beam that holds on one target. */
        BEAM,
        /** A thrown trap that arms where it lands. */
        TRAP
    }

    /** Secondary behaviour that mastery can switch on. */
    public enum Trait {
        NONE,
        EXPLODES_ON_IMPACT,
        PIERCES,
        LEAVES_BURNING_GROUND,
        CHAINS_TO_NEARBY,
        HEALS_CASTER,
        SHIELDS_CASTER,
        SLOWS_TARGET,
        STAGGERS_TARGET,
        CONVERTS_TO_LIGHT,
        SPLITS_INTO_THREE,
        REFLECTS,
        PULLS_TARGET,
        BLINDS,
        EXECUTES_WOUNDED
    }

    /**
     * One stage of a spell's development.
     *
     * <p>The design document's Ember Shot example lists six distinct upgrades
     * across its thirty levels, and says plainly that not every spell needs the
     * same ones. A stage is that: a level at which something specific changes.</p>
     */
    public static final class Stage {
        public final int level;
        public final String note;
        /** Damage multiplier applied from this stage onwards. */
        public final float damageScale;
        /** Trait this stage switches on, or NONE. */
        public final Trait trait;

        Stage(int level, String note, float damageScale, Trait trait) {
            this.level = level;
            this.note = note;
            this.damageScale = damageScale;
            this.trait = trait;
        }
    }

    /** One spell. */
    public static final class Def {
        public final String name;
        public final Category category;
        public final Shape shape;
        public final float lightCost;
        public final float baseDamage;
        public final float cooldown;
        public final float range;
        /** How this spell is found: a region index, or -1 for a starting page. */
        public final int foundIn;
        /** The mastery curve. Always starts at level 1. */
        public final Stage[] stages;

        Def(String name, Category category, Shape shape, float lightCost,
            float baseDamage, float cooldown, float range, int foundIn,
            Stage[] stages) {
            this.name = name;
            this.category = category;
            this.shape = shape;
            this.lightCost = lightCost;
            this.baseDamage = baseDamage;
            this.cooldown = cooldown;
            this.range = range;
            this.foundIn = foundIn;
            this.stages = stages;
        }

        /** Total damage multiplier at a given mastery level. */
        public float damageScaleAt(int level) {
            float best = 1f;
            for (int i = 0; i < stages.length; i++) {
                if (level >= stages[i].level) {
                    best = stages[i].damageScale;
                }
            }
            return best;
        }

        /** The most recent stage reached, for the spell's description. */
        public Stage stageAt(int level) {
            Stage best = stages[0];
            for (int i = 0; i < stages.length; i++) {
                if (level >= stages[i].level) {
                    best = stages[i];
                }
            }
            return best;
        }

        /** Whether a trait is active at this mastery level. */
        public boolean has(Trait trait, int level) {
            for (int i = 0; i < stages.length; i++) {
                if (stages[i].trait == trait && level >= stages[i].level) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Stage s(int level, String note, float scale, Trait trait) {
        return new Stage(level, note, scale, trait);
    }

    // --- compact constructors for the corpus ---------------------------------

    private static Def def(String name, Category cat, Shape shape, float cost,
                           float dmg, float cd, float range, int foundIn,
                           Stage... stages) {
        return new Def(name, cat, shape, cost, dmg, cd, range, foundIn, stages);
    }

    /**
     * The corpus.
     *
     * <p>The first four are the starting pages the design document allows --
     * "unlock only 2-4 spells at first, they need to grind to unlock". The rest
     * are spread across the fourteen regions, so discovering a settlement and
     * discovering its magic are the same act.</p>
     */
    public static final Def[] SPELLS = build();

    private static Def[] build() {
        List<Def> l = new ArrayList<Def>();

        // ---- Starting pages (foundIn = -1) ---------------------------------
        l.add(def("EMBER SHOT", Category.ELEMENTAL, Shape.BOLT, 8f, 14f, 0.55f, 26f, -1,
                s(1, "A SMALL FIRE BOLT", 1f, Trait.NONE),
                s(5, "STRIKES FASTER", 1.2f, Trait.NONE),
                s(10, "EXPLODES ON IMPACT", 1.5f, Trait.EXPLODES_ON_IMPACT),
                s(15, "PIERCES WEAK FOES", 1.9f, Trait.PIERCES),
                s(20, "LEAVES BURNING GROUND", 2.4f, Trait.LEAVES_BURNING_GROUND),
                s(25, "A CHARGED VERSION AWAKENS", 3.0f, Trait.CHAINS_TO_NEARBY),
                s(30, "EVOLVES: EMBER TEMPEST", 3.8f, Trait.EXPLODES_ON_IMPACT)));

        l.add(def("GALE CUT", Category.ELEMENTAL, Shape.SWEEP, 6f, 10f, 0.40f, 6f, -1,
                s(1, "A WIDE CUT AROUND YOU", 1f, Trait.NONE),
                s(6, "REACHES FURTHER", 1.25f, Trait.NONE),
                s(14, "PULLS DISTANT FOES IN", 1.7f, Trait.PULLS_TARGET),
                s(22, "CUTS TWICE", 2.3f, Trait.SPLITS_INTO_THREE)));

        l.add(def("WARD", Category.DEFENSIVE, Shape.WARD, 12f, 0f, 6f, 3f, -1,
                s(1, "TWO SECONDS UNTOUCHABLE", 1f, Trait.SHIELDS_CASTER),
                s(8, "REFLECTS THE FIRST BLOW", 1f, Trait.REFLECTS),
                s(18, "HEALS AS IT HOLDS", 1f, Trait.HEALS_CASTER)));

        l.add(def("STARFALL", Category.ARCANE, Shape.STARFALL, 22f, 34f, 8f, 18f, -1,
                s(1, "A HEAVY STRIKE THAT STAGGERS", 1f, Trait.STAGGERS_TARGET),
                s(10, "STRIKES THREE TARGETS", 1.4f, Trait.NONE),
                s(18, "BLINDS WHAT SURVIVES", 1.9f, Trait.BLINDS),
                s(26, "EXECUTES THE WOUNDED", 2.6f, Trait.EXECUTES_WOUNDED)));

        // ---- Elemental (found across the regions) --------------------------
        l.add(def("FROST LANCE", Category.ELEMENTAL, Shape.BOLT, 10f, 18f, 0.9f, 32f, 5,
                s(1, "A SPIKE OF RIME", 1f, Trait.SLOWS_TARGET),
                s(9, "SLOWS LONGER", 1.35f, Trait.SLOWS_TARGET),
                s(17, "SHATTERS THE FROZEN", 1.9f, Trait.EXPLODES_ON_IMPACT),
                s(25, "PIERCES THROUGH", 2.5f, Trait.PIERCES)));

        l.add(def("STONE MAUL", Category.ELEMENTAL, Shape.SWEEP, 14f, 26f, 1.6f, 8f, 0,
                s(1, "A HEAVY BLOW AROUND YOU", 1f, Trait.STAGGERS_TARGET),
                s(12, "STAGGERS LONGER", 1.6f, Trait.STAGGERS_TARGET),
                s(24, "ARMS A TRAP WHERE YOU STAND", 2.4f, Trait.NONE)));

        l.add(def("TIDE CALL", Category.ELEMENTAL, Shape.FIELD, 18f, 8f, 3.2f, 12f, 3,
                s(1, "WATER THAT KEEPS DRAGGING", 1f, Trait.SLOWS_TARGET),
                s(11, "SLOWS HARDER", 1.5f, Trait.SLOWS_TARGET),
                s(21, "CONVERTS SPENT LIGHT TO YOU", 2.1f, Trait.CONVERTS_TO_LIGHT)));

        l.add(def("SUNSPEAR", Category.ELEMENTAL, Shape.BEAM, 16f, 12f, 2.0f, 40f, 6,
                s(1, "A HELD BEAM OF DESERT LIGHT", 1f, Trait.NONE),
                s(10, "BURNS AS IT TOUCHES", 1.5f, Trait.LEAVES_BURNING_GROUND),
                s(20, "SPLITS TO THREE TARGETS", 2.2f, Trait.SPLITS_INTO_THREE)));

        l.add(def("STORM CHAIN", Category.ELEMENTAL, Shape.BOLT, 20f, 22f, 2.4f, 30f, 2,
                s(1, "LIGHTNING THAT LEAPS", 1f, Trait.CHAINS_TO_NEARBY),
                s(8, "LEAPS TWICE", 1.4f, Trait.CHAINS_TO_NEARBY),
                s(18, "STAGGERS WHAT IT CROSSES", 2.0f, Trait.STAGGERS_TARGET),
                s(27, "GLASS HAS NO MERCY", 2.9f, Trait.EXPLODES_ON_IMPACT)));

        // ---- Spatial -------------------------------------------------------
        l.add(def("RIFT STEP", Category.SPATIAL, Shape.DASH, 12f, 16f, 3.0f, 14f, 2,
                s(1, "A CUT THROUGH SPACE", 1f, Trait.NONE),
                s(10, "DAMAGES WHAT YOU PASS", 1.6f, Trait.NONE),
                s(20, "LEAVES A SLOWING TEAR", 2.2f, Trait.SLOWS_TARGET)));

        l.add(def("PORTAL SNARE", Category.SPATIAL, Shape.TRAP, 15f, 20f, 5.0f, 16f, 8,
                s(1, "A TRAP THAT PULLS THEM IN", 1f, Trait.PULLS_TARGET),
                s(12, "HOLDS THEM LONGER", 1.5f, Trait.SLOWS_TARGET),
                s(21, "TWO SNAKES, TWO SNARES", 2.2f, Trait.NONE)));

        l.add(def("HOLLOW LANCE", Category.SPATIAL, Shape.BOLT, 24f, 40f, 3.0f, 44f, 12,
                s(1, "SPACE ITSELF, SHARPENED", 1f, Trait.PIERCES),
                s(14, "PIERCES EVERYTHING", 1.7f, Trait.PIERCES),
                s(26, "TEARS OPEN WHERE IT LANDS", 2.6f, Trait.EXPLODES_ON_IMPACT)));

        // ---- Defensive -----------------------------------------------------
        l.add(def("BULWARK", Category.DEFENSIVE, Shape.WARD, 16f, 0f, 9f, 3f, 1,
                s(1, "A WALL OF HELD LIGHT", 1f, Trait.SHIELDS_CASTER),
                s(11, "REFLECTS A BLOW", 1f, Trait.REFLECTS),
                s(23, "HEALS AS IT STANDS", 1f, Trait.HEALS_CASTER)));

        l.add(def("COUNTERWEAVE", Category.DEFENSIVE, Shape.WARD, 14f, 24f, 7f, 4f, 8,
                s(1, "TURNS A BLOW BACK", 1f, Trait.REFLECTS),
                s(13, "STAGGERS WHAT STRUCK", 1.6f, Trait.STAGGERS_TARGET),
                s(25, "DRAWS LIGHT FROM THE BLOW", 2.3f, Trait.CONVERTS_TO_LIGHT)));

        l.add(def("STONE SKIN", Category.DEFENSIVE, Shape.WARD, 20f, 0f, 12f, 3f, 10,
                s(1, "HARD AS THE DEEP", 1f, Trait.SHIELDS_CASTER),
                s(15, "LONGER THAN A BREATH", 1f, Trait.NONE),
                s(28, "HEALS AS IT HOLDS", 1f, Trait.HEALS_CASTER)));

        // ---- Enhancement ---------------------------------------------------
        l.add(def("WIND AT YOUR BACK", Category.ENHANCEMENT, Shape.WARD, 10f, 0f, 8f, 3f, 7,
                s(1, "SPEED FOR A FEW SECONDS", 1f, Trait.NONE),
                s(10, "FASTER STILL", 1f, Trait.NONE),
                s(22, "AND LIGHT RETURNS QUICKER", 1f, Trait.CONVERTS_TO_LIGHT)));

        l.add(def("EDGE OF DAWN", Category.ENHANCEMENT, Shape.WARD, 12f, 0f, 9f, 3f, 11,
                s(1, "YOUR BLADE CUTS DEEPER", 1f, Trait.NONE),
                s(12, "DEEPER STILL", 1f, Trait.NONE),
                s(24, "AND EACH CUT LEAVES BURNING", 1f, Trait.LEAVES_BURNING_GROUND)));

        l.add(def("STEADY HAND", Category.ENHANCEMENT, Shape.WARD, 9f, 0f, 10f, 3f, 4,
                s(1, "A STEADIER GUARD", 1f, Trait.SHIELDS_CASTER),
                s(14, "HEALS AS YOU HOLD", 1f, Trait.HEALS_CASTER)));

        // ---- Summoning -----------------------------------------------------
        l.add(def("LANTERN WISP", Category.SUMMONING, Shape.SUMMON, 22f, 12f, 14f, 10f, 0,
                s(1, "A WISP THAT FIGHTS BESIDE YOU", 1f, Trait.NONE),
                s(13, "IT STRIKES HARDER", 1.6f, Trait.NONE),
                s(25, "AND RETURNS LIGHT TO YOU", 2.2f, Trait.CONVERTS_TO_LIGHT)));

        l.add(def("ASH HOUND", Category.SUMMONING, Shape.SUMMON, 26f, 20f, 16f, 12f, 12,
                s(1, "A HOUND OF ASH AND HUNGER", 1f, Trait.NONE),
                s(15, "IT POUNCES AND STAGGERS", 1.8f, Trait.STAGGERS_TARGET),
                s(27, "TWO HOUNDS ANSWER", 2.6f, Trait.NONE)));

        l.add(def("CRYSTAL SENTRY", Category.SUMMONING, Shape.SUMMON, 30f, 26f, 18f, 12f, 4,
                s(1, "GLASS THAT STANDS AND FIGHTS", 1f, Trait.SHIELDS_CASTER),
                s(16, "IT SHIELDS YOU AS IT STRIKES", 1.9f, Trait.SHIELDS_CASTER)));

        // ---- Illusion ------------------------------------------------------
        l.add(def("MIRROR STEP", Category.ILLUSION, Shape.DASH, 14f, 8f, 4.0f, 12f, 7,
                s(1, "LEAVE A FALSE SELF BEHIND", 1f, Trait.BLINDS),
                s(12, "THE FALSE SELF STRIKES", 1.7f, Trait.NONE),
                s(24, "AND BLINDS WHAT IT TOUCHES", 2.3f, Trait.BLINDS)));

        l.add(def("TWILIGHT VEIL", Category.ILLUSION, Shape.WARD, 18f, 0f, 11f, 3f, 9,
                s(1, "THE DARK TAKES YOU IN", 1f, Trait.SHIELDS_CASTER),
                s(16, "FOES LOSE TRACK OF YOU", 1f, Trait.BLINDS),
                s(28, "AND STRIKE EACH OTHER", 1f, Trait.REFLECTS)));

        // ---- Arcane --------------------------------------------------------
        l.add(def("UNMAKING", Category.ARCANE, Shape.BEAM, 26f, 30f, 4.0f, 36f, 11,
                s(1, "A BEAM THAT UNRAVELS MAGIC", 1f, Trait.NONE),
                s(14, "STRIPS A FOE OF ITS WARDS", 1.8f, Trait.STAGGERS_TARGET),
                s(27, "AND CONVERTS THAT POWER TO YOU", 2.5f, Trait.CONVERTS_TO_LIGHT)));

        l.add(def("STAR WRIT", Category.ARCANE, Shape.FIELD, 24f, 16f, 5.0f, 14f, 13,
                s(1, "WRITTEN LIGHT THAT KEEPS BURNING", 1f, Trait.LEAVES_BURNING_GROUND),
                s(15, "BURNS LONGER", 1.7f, Trait.NONE),
                s(28, "AND EXECUTES THE WOUNDED", 2.4f, Trait.EXECUTES_WOUNDED)));

        // ---- Forbidden -----------------------------------------------------
        l.add(def("SLOW HOUR", Category.FORBIDDEN, Shape.FIELD, 32f, 10f, 9.0f, 16f, 12,
                s(1, "TIME DRAGS IN A RING", 1f, Trait.SLOWS_TARGET),
                s(16, "DRAGS HARDER", 1.8f, Trait.SLOWS_TARGET),
                s(29, "AND THE WOUNDED ARE EXECUTED", 2.7f, Trait.EXECUTES_WOUNDED)));

        l.add(def("SOUL TITHE", Category.FORBIDDEN, Shape.BOLT, 30f, 44f, 7.0f, 28f, 12,
                s(1, "TAKE LIFE TO PAY FOR MAGIC", 1f, Trait.CONVERTS_TO_LIGHT),
                s(18, "TAKES MORE", 2.0f, Trait.CONVERTS_TO_LIGHT),
                s(30, "AND HEALS YOU IN THE TAKING", 2.9f, Trait.HEALS_CASTER)));

        l.add(def("EMPTY CROWN", Category.FORBIDDEN, Shape.STARFALL, 36f, 52f, 10.0f, 22f, 12,
                s(1, "THE POWER OF THE FIRST SILENCE", 1f, Trait.STAGGERS_TARGET),
                s(20, "UNMAKES WHAT STANDS", 2.1f, Trait.BLINDS),
                s(30, "AND THE WOUNDED FALL", 3.2f, Trait.EXECUTES_WOUNDED)));


        // ---- Elemental, the long tail ---------------------------------------
        // The document weights Elemental at 30% of all magic, so it gets the
        // most pages. Each keeps its own shape and its own mastery curve.
        l.add(def("CINDER RAIN", Category.ELEMENTAL, Shape.FIELD, 20f, 12f, 4.0f, 14f, 0,
                s(1, "BURNING ASH FALLS AROUND YOU", 1f, Trait.LEAVES_BURNING_GROUND),
                s(10, "FALLS LONGER", 1.5f, Trait.NONE),
                s(21, "AND CHAINS BETWEEN PATCHES", 2.2f, Trait.CHAINS_TO_NEARBY)));
        l.add(def("GLACIER WALL", Category.ELEMENTAL, Shape.WARD, 18f, 8f, 7.0f, 4f, 5,
                s(1, "ICE RISES IN FRONT OF YOU", 1f, Trait.SHIELDS_CASTER),
                s(12, "FOES THAT TOUCH IT FREEZE", 1.3f, Trait.SLOWS_TARGET),
                s(24, "AND IT SHATTERS INTO SHARDS", 1.9f, Trait.EXPLODES_ON_IMPACT)));
        l.add(def("SAND VEIL", Category.ELEMENTAL, Shape.SWEEP, 12f, 8f, 2.0f, 9f, 6,
                s(1, "A WHIPPING CLOUD OF SAND", 1f, Trait.BLINDS),
                s(11, "BLINDS FOR LONGER", 1.4f, Trait.BLINDS),
                s(23, "AND GRINDS WHAT IT CATCHES", 2.0f, Trait.SLOWS_TARGET)));
        l.add(def("THUNDERCLAP", Category.ELEMENTAL, Shape.SWEEP, 15f, 20f, 2.6f, 7f, 8,
                s(1, "SOUND ENOUGH TO STAGGER", 1f, Trait.STAGGERS_TARGET),
                s(13, "AND LEAP TO A SECOND FOE", 1.6f, Trait.CHAINS_TO_NEARBY),
                s(25, "AND A THIRD", 2.3f, Trait.CHAINS_TO_NEARBY)));
        l.add(def("EMBER WARD", Category.ELEMENTAL, Shape.WARD, 14f, 10f, 8.0f, 3f, 1,
                s(1, "FIRE THAT BURNS WHAT TOUCHES YOU", 1f, Trait.SHIELDS_CASTER),
                s(14, "AND REFLECTS THE BLOW", 1.5f, Trait.REFLECTS),
                s(26, "AND HEALS YOU IN THE HEAT", 2.1f, Trait.HEALS_CASTER)));
        l.add(def("RIME STEP", Category.ELEMENTAL, Shape.DASH, 13f, 14f, 3.4f, 13f, 5,
                s(1, "A DASH THAT FREEZES THE GROUND", 1f, Trait.SLOWS_TARGET),
                s(12, "FREEZES LONGER", 1.5f, Trait.SLOWS_TARGET),
                s(24, "AND LEAVES A RIME FIELD", 2.2f, Trait.LEAVES_BURNING_GROUND)));
        l.add(def("STONE SKIN RITE", Category.ELEMENTAL, Shape.WARD, 17f, 0f, 9.0f, 3f, 10,
                s(1, "THE MOUNTAIN HOLDS YOU", 1f, Trait.SHIELDS_CASTER),
                s(15, "REFLECTS THE FIRST CUT", 1f, Trait.REFLECTS),
                s(27, "AND HEALS AS IT STANDS", 1f, Trait.HEALS_CASTER)));
        l.add(def("SALTMARK", Category.ELEMENTAL, Shape.TRAP, 16f, 22f, 4.5f, 12f, 3,
                s(1, "A MARK THAT ERUPTS WHEN TROD", 1f, Trait.EXPLODES_ON_IMPACT),
                s(13, "ERUPTS HARDER", 1.7f, Trait.NONE),
                s(25, "AND ARMS A SECOND", 2.4f, Trait.NONE)));
        l.add(def("WIND SHEAR", Category.ELEMENTAL, Shape.BEAM, 11f, 9f, 1.4f, 34f, 7,
                s(1, "A THIN LINE OF CUTTING AIR", 1f, Trait.PIERCES),
                s(10, "CUTS DEEPER", 1.4f, Trait.PIERCES),
                s(20, "AND SPLITS TO THREE", 2.0f, Trait.SPLITS_INTO_THREE)));
        l.add(def("FROST BLOOM", Category.ELEMENTAL, Shape.FIELD, 19f, 10f, 4.2f, 13f, 5,
                s(1, "ICE FLOWERS WHERE YOU STAND", 1f, Trait.SLOWS_TARGET),
                s(14, "THEY BLOOM WIDER", 1.6f, Trait.NONE),
                s(26, "AND SHATTER WHEN TROD ON", 2.3f, Trait.EXPLODES_ON_IMPACT)));

        // ---- Spatial, the long tail ------------------------------------------
        l.add(def("THRESHOLD", Category.SPATIAL, Shape.DASH, 15f, 12f, 3.6f, 15f, 2,
                s(1, "A DOOR, AND A CUT BEHIND IT", 1f, Trait.NONE),
                s(12, "THE CUT LEAVES A TEAR", 1.5f, Trait.SLOWS_TARGET),
                s(24, "AND A SECOND DOOR", 2.1f, Trait.NONE)));
        l.add(def("GRAVITY WELL", Category.SPATIAL, Shape.FIELD, 23f, 14f, 6.0f, 15f, 11,
                s(1, "THE GROUND REMEMBERS WEIGHT", 1f, Trait.PULLS_TARGET),
                s(15, "IT PULLS HARDER", 1.7f, Trait.PULLS_TARGET),
                s(27, "AND CRUSHES WHAT IT HOLDS", 2.5f, Trait.STAGGERS_TARGET)));
        l.add(def("SEAM WALK", Category.SPATIAL, Shape.DASH, 18f, 18f, 4.4f, 18f, 8,
                s(1, "WALK THE SEAM BETWEEN PLACES", 1f, Trait.NONE),
                s(16, "CUTS ALL YOU PASS", 1.8f, Trait.NONE),
                s(28, "AND LEAVES THEM SLOWED", 2.6f, Trait.SLOWS_TARGET)));
        l.add(def("UNMOORED", Category.SPATIAL, Shape.TRAP, 21f, 18f, 6.0f, 14f, 9,
                s(1, "A PLACE THAT FORGETS WHERE IT IS", 1f, Trait.STAGGERS_TARGET),
                s(17, "HOLDS THEM LONGER", 1.8f, Trait.SLOWS_TARGET)));
        l.add(def("FOLD", Category.SPATIAL, Shape.BOLT, 26f, 34f, 4.0f, 40f, 13,
                s(1, "SPACE FOLDED INTO A POINT", 1f, Trait.PIERCES),
                s(18, "FOLDS TWICE", 2.0f, Trait.CHAINS_TO_NEARBY),
                s(29, "AND UNFOLDS VIOLENTLY", 2.8f, Trait.EXPLODES_ON_IMPACT)));

        // ---- Defensive, weighted at 40% and the largest tail ------------------
        l.add(def("AEGIS", Category.DEFENSIVE, Shape.WARD, 15f, 0f, 8.0f, 3f, 1,
                s(1, "A HELD SHIELD OF LIGHT", 1f, Trait.SHIELDS_CASTER),
                s(12, "LONGER THAN A BREATH", 1f, Trait.NONE),
                s(24, "AND IT REFLECTS A BLOW", 1f, Trait.REFLECTS)));
        l.add(def("CIRCLE OF STILLNESS", Category.DEFENSIVE, Shape.FIELD, 20f, 4f, 9.0f, 10f, 2,
                s(1, "A RING WHERE BLOWS LOSE FORCE", 1f, Trait.SLOWS_TARGET),
                s(14, "THE RING WIDENS", 1.4f, Trait.NONE),
                s(26, "AND HEALS YOU WHILE YOU STAND IN IT", 1.9f, Trait.HEALS_CASTER)));
        l.add(def("RIPOSTE", Category.DEFENSIVE, Shape.SWEEP, 13f, 18f, 5.0f, 5f, 8,
                s(1, "STRIKE BACK AS THEY SWING", 1f, Trait.STAGGERS_TARGET),
                s(15, "STRIKE HARDER", 1.7f, Trait.NONE),
                s(27, "AND TAKE LIGHT FROM THE BLOW", 2.4f, Trait.CONVERTS_TO_LIGHT)));
        l.add(def("BASTION", Category.DEFENSIVE, Shape.WARD, 24f, 0f, 14.0f, 3f, 8,
                s(1, "NOTHING MOVES YOU", 1f, Trait.SHIELDS_CASTER),
                s(18, "AND NOTHING MOVES THEM EITHER", 1f, Trait.STAGGERS_TARGET),
                s(29, "A WALL THAT HEALS AND REFLECTS", 1f, Trait.HEALS_CASTER)));
        l.add(def("WARDSIGHT", Category.DEFENSIVE, Shape.WARD, 9f, 0f, 10.0f, 3f, 4,
                s(1, "YOU SEE THE BLOW COMING", 1f, Trait.SHIELDS_CASTER),
                s(13, "AND THE FIRST ONE BREAKS ON YOU", 1f, Trait.REFLECTS)));
        l.add(def("SHARD GUARD", Category.DEFENSIVE, Shape.WARD, 17f, 14f, 8.0f, 4f, 4,
                s(1, "GLASS PLATES CATCH THE BLOW", 1f, Trait.SHIELDS_CASTER),
                s(14, "AND FLY BACK OUT", 1.5f, Trait.REFLECTS),
                s(26, "CUTTING WHATEVER IS CLOSE", 2.1f, Trait.NONE)));
        l.add(def("DEADWEIGHT WARD", Category.DEFENSIVE, Shape.FIELD, 22f, 6f, 11.0f, 9f, 10,
                s(1, "THE DEEP'S OWN STONE HOLDS THEM", 1f, Trait.SLOWS_TARGET),
                s(16, "HOLDS THEM STILL", 1.5f, Trait.STAGGERS_TARGET)));
        l.add(def("LAST LANTERN", Category.DEFENSIVE, Shape.WARD, 26f, 0f, 16.0f, 3f, 9,
                s(1, "ONE LIGHT THAT WILL NOT GO OUT", 1f, Trait.SHIELDS_CASTER),
                s(20, "AND HEALS YOU WHILE IT BURNS", 1f, Trait.HEALS_CASTER),
                s(30, "AND RETURNS WHAT WAS SPENT", 1f, Trait.CONVERTS_TO_LIGHT)));
        l.add(def("THORN HIDE", Category.DEFENSIVE, Shape.WARD, 16f, 12f, 7.0f, 3f, 7,
                s(1, "THE FOREST BITES BACK", 1f, Trait.REFLECTS),
                s(15, "IT BITES HARDER", 1.7f, Trait.REFLECTS),
                s(27, "AND HOLDS THEM FAST", 2.4f, Trait.SLOWS_TARGET)));
        l.add(def("GLASSWARD", Category.DEFENSIVE, Shape.WARD, 19f, 10f, 9.0f, 3f, 4,
                s(1, "A PANE OF CRYSTAL LIGHT", 1f, Trait.SHIELDS_CASTER),
                s(17, "IT SHATTERS AND CUTS", 1.6f, Trait.EXPLODES_ON_IMPACT)));

        // ---- Enhancement -----------------------------------------------------
        l.add(def("QUICKEN", Category.ENHANCEMENT, Shape.WARD, 8f, 0f, 7.0f, 3f, 0,
                s(1, "YOUR ARMS REMEMBER SPEED", 1f, Trait.NONE),
                s(11, "FASTER STILL", 1f, Trait.NONE),
                s(23, "AND LIGHT RETURNS QUICKER", 1f, Trait.CONVERTS_TO_LIGHT)));
        l.add(def("IRON WILL", Category.ENHANCEMENT, Shape.WARD, 11f, 0f, 12.0f, 3f, 8,
                s(1, "NOTHING BREAKS YOUR GUARD", 1f, Trait.SHIELDS_CASTER),
                s(15, "AND NOTHING STAGGERS YOU", 1f, Trait.NONE),
                s(28, "AND WOUNDS CLOSE FASTER", 1f, Trait.HEALS_CASTER)));
        l.add(def("HUNTERS MARK", Category.ENHANCEMENT, Shape.TRAP, 12f, 6f, 8.0f, 20f, 6,
                s(1, "MARK THEM, AND THEY BLEED LONGER", 1f, Trait.SLOWS_TARGET),
                s(14, "THE MARK SPREADS TO TWO", 1.5f, Trait.CHAINS_TO_NEARBY),
                s(26, "AND EXECUTES THE WOUNDED", 2.2f, Trait.EXECUTES_WOUNDED)));
        l.add(def("STAR CHORUS", Category.ENHANCEMENT, Shape.WARD, 15f, 0f, 11.0f, 3f, 11,
                s(1, "THE STARS ANSWER YOUR CALL", 1f, Trait.NONE),
                s(16, "EVERY BLOW LANDS HARDER", 1f, Trait.NONE),
                s(28, "AND EVERY KILL RETURNS LIGHT", 1f, Trait.CONVERTS_TO_LIGHT)));
        l.add(def("SWIFTSHADOW", Category.ENHANCEMENT, Shape.DASH, 10f, 6f, 3.2f, 12f, 9,
                s(1, "MOVE BEFORE THE BLOW LANDS", 1f, Trait.NONE),
                s(13, "LEAVE THEM BLINDED", 1.4f, Trait.BLINDS),
                s(25, "AND SLOWED IN YOUR WAKE", 1.9f, Trait.SLOWS_TARGET)));
        l.add(def("GRAVEWARD", Category.ENHANCEMENT, Shape.WARD, 14f, 0f, 10.0f, 3f, 12,
                s(1, "DEATH IS A DOOR, AND IT IS SHUT", 1f, Trait.SHIELDS_CASTER),
                s(18, "IT STAYS SHUT LONGER", 1f, Trait.NONE),
                s(30, "AND OPENS ONLY FOR THEM", 1f, Trait.REFLECTS)));

        // ---- Summoning -------------------------------------------------------
        l.add(def("DEEP WARDEN", Category.SUMMONING, Shape.SUMMON, 34f, 30f, 20.0f, 12f, 10,
                s(1, "STONE RISES AND WALKS", 1f, Trait.STAGGERS_TARGET),
                s(18, "IT STANDS IN FRONT OF YOU", 1.9f, Trait.SHIELDS_CASTER)));
        l.add(def("HOLLOW FAMILIAR", Category.SUMMONING, Shape.SUMMON, 24f, 16f, 15.0f, 12f, 12,
                s(1, "SOMETHING SMALL AND WRONG OBEYS", 1f, Trait.NONE),
                s(15, "IT BITES DEEPER", 1.8f, Trait.NONE),
                s(27, "AND RETURNS LIGHT WITH EACH BITE", 2.5f, Trait.CONVERTS_TO_LIGHT)));
        l.add(def("RAVEN SWARM", Category.SUMMONING, Shape.SUMMON, 22f, 14f, 13.0f, 12f, 8,
                s(1, "THE FLOCK ANSWERS", 1f, Trait.BLINDS),
                s(14, "THE FLOCK GROWS", 1.6f, Trait.BLINDS)));
        l.add(def("ORCHARD WARDEN", Category.SUMMONING, Shape.SUMMON, 28f, 22f, 17.0f, 12f, 7,
                s(1, "THE OLD ROOTS RISE FOR YOU", 1f, Trait.SLOWS_TARGET),
                s(16, "THEY HOLD THEM STILL", 1.8f, Trait.STAGGERS_TARGET),
                s(28, "AND HEAL YOU AS THEY HOLD", 2.5f, Trait.HEALS_CASTER)));

        // ---- Illusion --------------------------------------------------------
        l.add(def("MANY FACES", Category.ILLUSION, Shape.DASH, 16f, 10f, 5.0f, 14f, 7,
                s(1, "WHICH OF YOU IS REAL", 1f, Trait.BLINDS),
                s(15, "THE COPIES STRIKE TOO", 1.7f, Trait.SPLITS_INTO_THREE),
                s(27, "AND THEY BLIND WHAT THEY PASS", 2.4f, Trait.BLINDS)));
        l.add(def("HOLLOW FACE", Category.ILLUSION, Shape.TRAP, 19f, 12f, 7.0f, 13f, 12,
                s(1, "THEY SEE WHAT THEY FEAR", 1f, Trait.BLINDS),
                s(17, "AND FLEE FROM IT", 1.8f, Trait.SLOWS_TARGET)));
        l.add(def("EMPTY STEP", Category.ILLUSION, Shape.DASH, 12f, 8f, 3.8f, 16f, 9,
                s(1, "YOU ARE NOT WHERE THEY STRUCK", 1f, Trait.SHIELDS_CASTER),
                s(14, "AND THEY STRIKE EACH OTHER", 1.4f, Trait.REFLECTS),
                s(26, "AND YOU ARE BEHIND THEM", 2.0f, Trait.STAGGERS_TARGET)));

        // ---- Arcane ----------------------------------------------------------
        l.add(def("NULL FIELD", Category.ARCANE, Shape.FIELD, 28f, 18f, 7.0f, 13f, 2,
                s(1, "MAGIC FORGETS ITSELF HERE", 1f, Trait.SLOWS_TARGET),
                s(16, "NOTHING CAST WITHIN IT LANDS", 1.9f, Trait.NONE),
                s(29, "AND WHAT STANDS IN IT BURNS", 2.7f, Trait.LEAVES_BURNING_GROUND)));
        l.add(def("SEAL BREAK", Category.ARCANE, Shape.BEAM, 30f, 36f, 6.0f, 34f, 13,
                s(1, "THE SEALS ARE NOT THE ONLY LAW", 1f, Trait.PIERCES),
                s(19, "IT UNRAVELS EVERYTHING IT TOUCHES", 2.1f, Trait.PIERCES),
                s(30, "AND LEAVES THEM OPEN TO YOU", 3.0f, Trait.EXECUTES_WOUNDED)));
        l.add(def("STARLIGHT LATTICE", Category.ARCANE, Shape.FIELD, 25f, 14f, 6.0f, 15f, 11,
                s(1, "A PATTERN THAT KEEPS CUTTING", 1f, Trait.LEAVES_BURNING_GROUND),
                s(17, "THE PATTERN WIDENS", 1.8f, Trait.NONE),
                s(28, "AND PULLS ITS VICTIMS IN", 2.6f, Trait.PULLS_TARGET)));
        l.add(def("AETHER LANCE", Category.ARCANE, Shape.BOLT, 32f, 48f, 5.5f, 42f, 13,
                s(1, "LIGHT SHARPENED TO A POINT", 1f, Trait.PIERCES),
                s(20, "IT PIERCES EVERYTHING", 2.2f, Trait.PIERCES),
                s(30, "AND EXPLODES AT THE END", 3.1f, Trait.EXPLODES_ON_IMPACT)));
    

        // ---- The remaining corpus --------------------------------------------
        // The document asks for more than a hundred. These complete the count,
        // and each still has its own shape, cost and curve rather than being a
        // renamed copy of another page.
        l.add(def("PYRE", Category.ELEMENTAL, Shape.FIELD, 24f, 16f, 5.0f, 12f, 0,
                s(1, "A FUNERAL FIRE THAT STAYS", 1f, Trait.LEAVES_BURNING_GROUND),
                s(12, "IT SPREADS WHERE THEY STAND", 1.6f, Trait.CHAINS_TO_NEARBY),
                s(25, "AND TAKES THE WOUNDED", 2.4f, Trait.EXECUTES_WOUNDED)));
        l.add(def("HAILSTORM", Category.ELEMENTAL, Shape.FIELD, 21f, 14f, 4.6f, 15f, 5,
                s(1, "ICE FALLS ON EVERYTHING NEAR", 1f, Trait.SLOWS_TARGET),
                s(13, "IT FALLS HARDER", 1.7f, Trait.STAGGERS_TARGET)));
        l.add(def("MIRAGE", Category.ILLUSION, Shape.WARD, 13f, 0f, 9.0f, 3f, 6,
                s(1, "THE DESERT HIDES YOU", 1f, Trait.BLINDS),
                s(15, "AND THE HEAT ANSWERS FOR YOU", 1.3f, Trait.REFLECTS)));
        l.add(def("SIREN CALL", Category.ILLUSION, Shape.FIELD, 17f, 5f, 8.0f, 16f, 3,
                s(1, "THEY WALK TOWARDS THE SOUND", 1f, Trait.PULLS_TARGET),
                s(16, "AND CANNOT LOOK AWAY", 1.5f, Trait.BLINDS),
                s(28, "AND DROWN IN IT", 2.3f, Trait.EXECUTES_WOUNDED)));
        l.add(def("NIGHTMARE", Category.ILLUSION, Shape.BOLT, 20f, 24f, 6.0f, 30f, 12,
                s(1, "THEIR OWN FEAR, ARMED", 1f, Trait.BLINDS),
                s(18, "IT DOES NOT STOP", 2.0f, Trait.STAGGERS_TARGET)));
        l.add(def("STILL WATER", Category.DEFENSIVE, Shape.FIELD, 18f, 4f, 10.0f, 11f, 3,
                s(1, "NOTHING RIPPLES HERE", 1f, Trait.SLOWS_TARGET),
                s(17, "AND WOUNDS CLOSE", 1.6f, Trait.HEALS_CASTER)));
        l.add(def("PILGRIMS SHELL", Category.DEFENSIVE, Shape.WARD, 21f, 0f, 13.0f, 3f, 3,
                s(1, "THE SHELL OF A DROWNED SAINT", 1f, Trait.SHIELDS_CASTER),
                s(19, "REFLECTS WHAT BREAKS ON IT", 1f, Trait.REFLECTS),
                s(29, "AND HEALS THE ONE INSIDE", 1f, Trait.HEALS_CASTER)));
        l.add(def("BULWARK RITE", Category.DEFENSIVE, Shape.WARD, 22f, 0f, 12.0f, 3f, 1,
                s(1, "THE CAPITAL DOES NOT FALL", 1f, Trait.SHIELDS_CASTER),
                s(17, "NOR DOES ITS GUARD", 1f, Trait.REFLECTS)));
        l.add(def("WATCHERS EYE", Category.ENHANCEMENT, Shape.WARD, 10f, 0f, 11.0f, 3f, 4,
                s(1, "YOU SEE EVERY BLOW", 1f, Trait.SHIELDS_CASTER),
                s(16, "AND EVERY OPENING", 1f, Trait.NONE),
                s(27, "AND EVERY WOUND CLOSES", 1f, Trait.HEALS_CASTER)));
        l.add(def("FROST GRIP", Category.ENHANCEMENT, Shape.TRAP, 15f, 10f, 7.0f, 14f, 5,
                s(1, "THE COLD TAKES THEIR LEGS", 1f, Trait.SLOWS_TARGET),
                s(18, "AND THEN THEIR ARMS", 1.8f, Trait.STAGGERS_TARGET)));
        l.add(def("SANDSTEP", Category.ENHANCEMENT, Shape.DASH, 9f, 4f, 3.0f, 15f, 6,
                s(1, "YOU DO NOT STAY WHERE THEY SWING", 1f, Trait.NONE),
                s(14, "AND THE SAND FOLLOWS YOU", 1.3f, Trait.BLINDS),
                s(26, "AND THEY CANNOT KEEP UP", 1.9f, Trait.SLOWS_TARGET)));
        l.add(def("RIVERWALK", Category.ENHANCEMENT, Shape.DASH, 11f, 6f, 3.4f, 16f, 3,
                s(1, "WATER CARRIES YOU", 1f, Trait.NONE),
                s(15, "IT PULLS THEM WITH YOU", 1.4f, Trait.PULLS_TARGET),
                s(27, "AND DROWNS THE WOUNDED", 2.0f, Trait.EXECUTES_WOUNDED)));
        l.add(def("GLASS SHARDS", Category.ELEMENTAL, Shape.BOLT, 17f, 20f, 2.2f, 28f, 4,
                s(1, "A SHOT OF SPLINTERED CRYSTAL", 1f, Trait.PIERCES),
                s(12, "IT SPLITS IN THREE", 1.5f, Trait.SPLITS_INTO_THREE),
                s(24, "AND EACH SHARD CUTS AGAIN", 2.2f, Trait.CHAINS_TO_NEARBY)));
        l.add(def("PRISM BEAM", Category.ELEMENTAL, Shape.BEAM, 23f, 16f, 3.6f, 38f, 4,
                s(1, "LIGHT THROUGH CUT GLASS", 1f, Trait.NONE),
                s(15, "IT BRANCHES TO THREE", 1.7f, Trait.SPLITS_INTO_THREE),
                s(27, "AND BURNS WHERE IT FALLS", 2.5f, Trait.LEAVES_BURNING_GROUND)));
        l.add(def("CAVERN ECHO", Category.ELEMENTAL, Shape.SWEEP, 14f, 16f, 2.2f, 8f, 10,
                s(1, "THE STONE REPEATS YOUR BLOW", 1f, Trait.STAGGERS_TARGET),
                s(14, "IT REPEATS TWICE", 1.5f, Trait.SPLITS_INTO_THREE)));
        l.add(def("GLOW WORM", Category.ELEMENTAL, Shape.FIELD, 13f, 7f, 3.8f, 11f, 10,
                s(1, "SMALL LIGHTS THAT BURN", 1f, Trait.LEAVES_BURNING_GROUND),
                s(17, "THEY SWARM WHERE THEY LAND", 1.5f, Trait.CHAINS_TO_NEARBY)));
        l.add(def("STAR TOUCH", Category.ARCANE, Shape.STARFALL, 27f, 40f, 7.5f, 20f, 11,
                s(1, "A FRAGMENT OF THE FALLEN STAR", 1f, Trait.STAGGERS_TARGET),
                s(19, "IT LANDS HARDER", 2.1f, Trait.EXPLODES_ON_IMPACT),
                s(29, "AND TAKES ROOT WHERE IT LANDS", 2.9f, Trait.LEAVES_BURNING_GROUND)));
        l.add(def("CRATER", Category.ARCANE, Shape.FIELD, 29f, 22f, 6.5f, 13f, 11,
                s(1, "THE GROUND REMEMBERS IMPACT", 1f, Trait.PULLS_TARGET),
                s(18, "IT PULLS EVERYTHING IN", 1.9f, Trait.PULLS_TARGET),
                s(28, "AND THEN RELEASES IT", 2.7f, Trait.EXPLODES_ON_IMPACT)));
        l.add(def("VEIL OF ASH", Category.ILLUSION, Shape.WARD, 15f, 0f, 10.0f, 3f, 0,
                s(1, "ASH HIDES YOU FROM THEM", 1f, Trait.BLINDS),
                s(16, "AND CHOKES THEM", 1.2f, Trait.SLOWS_TARGET)));
        l.add(def("DUSKBOUND", Category.ILLUSION, Shape.WARD, 18f, 0f, 11.0f, 3f, 9,
                s(1, "THE TWILIGHT KEEPS YOU", 1f, Trait.SHIELDS_CASTER),
                s(18, "AND MISLEADS THEM", 1f, Trait.BLINDS),
                s(29, "AND RETURNS WHAT IT SPENT", 1f, Trait.CONVERTS_TO_LIGHT)));
        l.add(def("HOLLOW GATE", Category.SPATIAL, Shape.TRAP, 25f, 26f, 8.0f, 15f, 12,
                s(1, "A DOOR TO A PLACE THEY SHOULD NOT GO", 1f, Trait.PULLS_TARGET),
                s(19, "IT CLOSES ON THEM", 2.0f, Trait.STAGGERS_TARGET),
                s(29, "AND TAKES WHAT IT CLOSES ON", 2.9f, Trait.EXECUTES_WOUNDED)));
        l.add(def("BETWEEN", Category.SPATIAL, Shape.DASH, 20f, 20f, 4.8f, 20f, 13,
                s(1, "A STEP THAT MISSES THE WORLD", 1f, Trait.NONE),
                s(17, "CUTS EACH PLACE YOU MISS", 1.9f, Trait.NONE),
                s(29, "AND LEAVES THEM BETWEEN", 2.7f, Trait.SLOWS_TARGET)));
        l.add(def("STONE TITHE", Category.FORBIDDEN, Shape.FIELD, 34f, 24f, 9.0f, 14f, 12,
                s(1, "THE DEEP TAKES ITS DUE", 1f, Trait.SLOWS_TARGET),
                s(20, "IT TAKES MORE", 2.1f, Trait.CONVERTS_TO_LIGHT),
                s(30, "AND GIVES IT TO YOU", 3.0f, Trait.HEALS_CASTER)));
        l.add(def("NAME UNMADE", Category.FORBIDDEN, Shape.BEAM, 38f, 46f, 11.0f, 32f, 12,
                s(1, "THE KEEPER'S OWN WEAPON", 1f, Trait.PIERCES),
                s(22, "NOTHING IT TOUCHES IS REMEMBERED", 2.3f, Trait.BLINDS),
                s(30, "AND NOTHING IT TOUCHES RETURNS", 3.3f, Trait.EXECUTES_WOUNDED)));
        l.add(def("SEAL OF SILENCE", Category.FORBIDDEN, Shape.WARD, 33f, 0f, 18.0f, 3f, 12,
                s(1, "THE FIRST SILENCE, HELD", 1f, Trait.SHIELDS_CASTER),
                s(21, "NOTHING CAST NEAR YOU LANDS", 1f, Trait.REFLECTS),
                s(30, "AND NOTHING THAT STRUCK YOU REMAINS", 1f, Trait.HEALS_CASTER)));
        l.add(def("RAGGED SAINT", Category.SUMMONING, Shape.SUMMON, 30f, 24f, 19.0f, 12f, 9,
                s(1, "A SAINT OF A DEAD CITY", 1f, Trait.NONE),
                s(18, "IT HEALS YOU AS IT FIGHTS", 1.9f, Trait.HEALS_CASTER),
                s(29, "AND IT DOES NOT FALL", 2.7f, Trait.SHIELDS_CASTER)));
        l.add(def("SEAL WARDEN", Category.SUMMONING, Shape.SUMMON, 36f, 34f, 22.0f, 12f, 13,
                s(1, "AN ANCIENT GUARDIAN ANSWERS", 1f, Trait.STAGGERS_TARGET),
                s(21, "IT SPEAKS THE SEALS", 2.2f, Trait.SHIELDS_CASTER),
                s(30, "AND NOTHING PASSES IT", 3.1f, Trait.REFLECTS)));
        l.add(def("LANTERN MOTH", Category.SUMMONING, Shape.SUMMON, 19f, 10f, 12.0f, 12f, 4,
                s(1, "A MOTH OF HELD LIGHT", 1f, Trait.NONE),
                s(14, "IT BLINDS AS IT FLIES", 1.6f, Trait.BLINDS),
                s(26, "AND RETURNS REACHING LIGHT", 2.3f, Trait.CONVERTS_TO_LIGHT)));
    

        l.add(def("FROSTFALL", Category.ELEMENTAL, Shape.STARFALL, 25f, 32f, 7.0f, 19f, 5,
                s(1, "WINTER COMES DOWN AT ONCE", 1f, Trait.SLOWS_TARGET),
                s(17, "AND DOES NOT STOP FALLING", 2.0f, Trait.LEAVES_BURNING_GROUND),
                s(28, "AND THE FROZEN SHATTER", 2.8f, Trait.EXPLODES_ON_IMPACT)));
        l.add(def("SEVENFOLD", Category.ARCANE, Shape.BOLT, 35f, 28f, 8.0f, 36f, 13,
                s(1, "ONE SPELL, SEVEN SEALS", 1f, Trait.CHAINS_TO_NEARBY),
                s(21, "SEVEN STRIKES, SEVEN TARGETS", 2.2f, Trait.SPLITS_INTO_THREE),
                s(30, "AND THE LAST ONE IS YOURS", 3.1f, Trait.EXECUTES_WOUNDED)));
        l.add(def("FIRST LIGHT", Category.ENHANCEMENT, Shape.WARD, 18f, 0f, 14.0f, 3f, 11,
                s(1, "THE LIGHT THAT WOKE THE WORLD", 1f, Trait.SHIELDS_CASTER),
                s(22, "IT HEALS AS IT HOLDS", 1f, Trait.HEALS_CASTER),
                s(30, "AND IT RETURNS WHAT YOU SPEND", 1f, Trait.CONVERTS_TO_LIGHT)));
    
        return l.toArray(new Def[l.size()]);
    }

    /** How many spells the corpus holds. */
    public static int count() {
        return SPELLS.length;
    }

    /** How many spells in a category. */
    public static int count(Category category) {
        int n = 0;
        for (int i = 0; i < SPELLS.length; i++) {
            if (SPELLS[i].category == category) {
                n++;
            }
        }
        return n;
    }

    /** Every spell that can be found in a given region. */
    public static int[] inRegion(int region) {
        List<Integer> found = new ArrayList<Integer>();
        for (int i = 0; i < SPELLS.length; i++) {
            if (SPELLS[i].foundIn == region) {
                found.add(Integer.valueOf(i));
            }
        }
        int[] out = new int[found.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = found.get(i).intValue();
        }
        return out;
    }

    /** The spells a new player starts with. */
    public static final int STARTER_COUNT = 4;

    private SpellCorpus() {
    }
}
