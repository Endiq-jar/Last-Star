package com.laststar.game.world.lore;

/**
 * Spell creation: the experiment, not the recipe book.
 *
 * <p>The design document is blunt about this -- "don't make spell creation just
 * another crafting menu" -- and then lists what the player should actually be
 * choosing between: elements, shapes, projectile behaviour, range and area,
 * Light consumption, secondary effects. So this is a set of dials rather than a
 * list of results. Nothing here is discoverable from a menu of finished spells;
 * the player picks an element and a shape and a behaviour, pays for the ambition,
 * and finds out what they made.</p>
 *
 * <p>The interesting part is that the dials are not independent:</p>
 * <ul>
 *   <li><b>Element x shape</b> is the real invention. Wind + Bolt is a fast
 *       bolt; Wind + Beam is a cutting ray; Wind + Field is a standing gale.
 *       The name and the flavour come from the pairing, which is why the same
 *       element can produce several different spells.</li>
 *   <li><b>Behaviour</b> is the secondary effect -- chain, pierce, explode,
 *       slow, blind, pull -- and it costs Light the same way a larger area
 *       does. Ambition is budgeted, not gated.</li>
 *   <li><b>Range and area</b> trade against each other and against Light: a
 *       wide field at long range is expensive and slow to cast.</li>
 * </ul>
 *
 * <p>Anything the forger makes is a real spell: it joins the book with its own
 * mastery track and its own curve, and levels to thirty like the authored ones.</p>
 */
public final class SpellForge {

    /** What a spell is made of. */
    public enum Element {
        EMBER("EMBER", SpellCorpus.Category.ELEMENTAL, 0.98f, 0.52f, 0.24f),
        GALE("GALE", SpellCorpus.Category.ELEMENTAL, 0.62f, 0.92f, 0.78f),
        FROST("FROST", SpellCorpus.Category.ELEMENTAL, 0.62f, 0.86f, 1f),
        STORM("STORM", SpellCorpus.Category.ELEMENTAL, 0.86f, 0.84f, 1f),
        STONE("STONE", SpellCorpus.Category.ELEMENTAL, 0.72f, 0.66f, 0.50f),
        LUMEN("LUMEN", SpellCorpus.Category.ENHANCEMENT, 1f, 0.94f, 0.72f),
        SHADE("SHADE", SpellCorpus.Category.ILLUSION, 0.62f, 0.46f, 0.86f),
        VOID("VOID", SpellCorpus.Category.FORBIDDEN, 0.34f, 0.18f, 0.42f);

        public final String label;
        public final SpellCorpus.Category category;
        public final float r, g, b;

        Element(String label, SpellCorpus.Category category, float r, float g, float b) {
            this.label = label;
            this.category = category;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    /** How the spell behaves when it lands, beyond its shape. */
    public enum Behaviour {
        NONE("PLAIN", SpellCorpus.Trait.NONE, 1.0f),
        CHAIN("CHAIN", SpellCorpus.Trait.CHAINS_TO_NEARBY, 1.25f),
        PIERCE("PIERCE", SpellCorpus.Trait.PIERCES, 1.20f),
        EXPLODE("BURST", SpellCorpus.Trait.EXPLODES_ON_IMPACT, 1.45f),
        SLOW("SLOW", SpellCorpus.Trait.SLOWS_TARGET, 1.30f),
        BLIND("BLIND", SpellCorpus.Trait.BLINDS, 1.35f),
        PULL("PULL", SpellCorpus.Trait.PULLS_TARGET, 1.40f),
        HEAL("MEND", SpellCorpus.Trait.HEALS_CASTER, 1.55f),
        SHIELD("WARD", SpellCorpus.Trait.SHIELDS_CASTER, 1.50f);

        public final String label;
        public final SpellCorpus.Trait trait;
        /** Multiplies the Light cost and the damage of the made spell. */
        public final float ambition;

        Behaviour(String label, SpellCorpus.Trait trait, float ambition) {
            this.label = label;
            this.trait = trait;
            this.ambition = ambition;
        }
    }

    /** The dials, held together so a preview can be drawn before committing. */
    public static final class Recipe {
        public Element element = Element.EMBER;
        public SpellCorpus.Shape shape = SpellCorpus.Shape.BOLT;
        public Behaviour behaviour = Behaviour.NONE;
        /** 0..1: a short reach or a long one. */
        public float range = 0.5f;
        /** 0..1: a pinpoint or a wide area. */
        public float area = 0.5f;
        /** 0..1: cheap to cast, or costly and strong. */
        public float power = 0.5f;

        public Recipe() {
        }

        public Recipe(Element element, SpellCorpus.Shape shape, Behaviour behaviour) {
            this.element = element;
            this.shape = shape;
            this.behaviour = behaviour;
        }

        /**
         * The name this combination earns.
         *
         * <p>Element first, then a word chosen by the shape, then the behaviour
         * if it has one. "GALE LANCE" and "GALE LANCE, CHAINED" are different
         * spells to a player, so the behaviour has to show.</p>
         */
        public String name() {
            String base = element.label + " " + shapeWord(shape);
            return behaviour == Behaviour.NONE ? base
                    : base + " " + behaviour.label;
        }
    }

    /** The noun a shape contributes to a name. */
    public static String shapeWord(SpellCorpus.Shape shape) {
        switch (shape) {
            case BOLT:
                return "LANCE";
            case SWEEP:
                return "ARC";
            case WARD:
                return "WARD";
            case STARFALL:
                return "FALL";
            case FIELD:
                return "FIELD";
            case DASH:
                return "STEP";
            case BEAM:
                return "RAY";
            case TRAP:
                return "SNARE";
            case SUMMON:
                return "CALL";
            default:
                return "SPELL";
        }
    }

    // --- the cost of ambition -------------------------------------------------

    /** Base Light cost before the dials. */
    public static final float BASE_COST = 9f;
    public static final float BASE_DAMAGE = 12f;
    /** How much a full power dial adds to damage, as a multiplier. */
    public static final float POWER_RANGE = 1.6f;

    /** Light the recipe will cost to cast. */
    public static float lightCost(Recipe r) {
        float cost = BASE_COST * r.behaviour.ambition;
        // Power is the most expensive dial: a spell that hits hard should be a
        // spell the player has to budget for.
        cost *= 0.75f + r.power * 0.95f;
        cost *= 0.85f + r.area * 0.5f;
        cost *= 0.90f + r.range * 0.30f;
        return Math.round(cost * 10f) / 10f;
    }

    /** Damage before mastery. */
    public static float baseDamage(Recipe r) {
        return BASE_DAMAGE * (0.55f + r.power * POWER_RANGE)
                * (0.85f + r.area * 0.4f);
    }

    /** Cooldown in seconds: a wide, strong spell is also a slow one. */
    public static float cooldown(Recipe r) {
        return 0.45f + r.power * 0.9f + r.area * 0.55f + r.range * 0.25f;
    }

    /** Reach in metres. */
    public static float range(Recipe r) {
        return 6f + r.range * 18f;
    }

    /**
     * Makes a spell from a recipe.
     *
     * <p>The mastery curve is generated rather than authored: every made spell
     * gains the same shape of progression -- more damage at five and fifteen, a
     * stage at ten, its behaviour's trait sharpened at twenty, and a final stage
     * at thirty -- so a created spell is never a second-class citizen of the
     * book.</p>
     */
    public static SpellCorpus.Def forge(Recipe r) {
        // The behaviour is present from the first cast: it is the thing the
        // player chose. Mastery sharpens the spell around it rather than
        // withholding it, which is what makes creation feel like authorship.
        SpellCorpus.Stage[] stages = new SpellCorpus.Stage[]{
                new SpellCorpus.Stage(1, "AS MADE", 1f, r.behaviour.trait),
                new SpellCorpus.Stage(5, "TRUE FLIGHT", 1.25f, r.behaviour.trait),
                new SpellCorpus.Stage(10, "THE SHAPE HOLDS", 1.5f, r.behaviour.trait),
                new SpellCorpus.Stage(20, "SECOND NATURE", 2.1f, r.behaviour.trait),
                new SpellCorpus.Stage(30, "AS THE WANDER INTENDED", 2.9f,
                        r.behaviour.trait)
        };
        return SpellCorpus.make(r.name(), r.element.category, r.shape,
                lightCost(r), baseDamage(r), cooldown(r), range(r),
                -1, stages);
    }

    /**
     * Whether two recipes produce the same spell.
     *
     * <p>Used to refuse a duplicate: the book should not fill with six copies
     * of the same Gale Lance because a player tapped forge six times.</p>
     */
    public static boolean same(SpellCorpus.Def a, SpellCorpus.Def b) {
        return a.name.equals(b.name) && a.shape == b.shape
                && Math.abs(a.lightCost - b.lightCost) < 0.05f;
    }
}
