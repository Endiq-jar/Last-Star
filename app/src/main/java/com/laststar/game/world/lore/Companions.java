package com.laststar.game.world.lore;

/**
 * The companions, as characters rather than followers.
 *
 * <p>The document is specific about what a companion has to be: their own Gift,
 * personal quests, a unique combat ability, dialogue choices, relationship
 * progression, a personal history, and interactions with the other companions.
 * It also names the reason they matter mechanically -- certain combinations
 * unlock special abilities -- so companionship is not decoration.</p>
 *
 * <p>Six companions, one for each direction the story goes: the Star Guard who
 * remembers the Seals, the Hollow-touched who hears the First Silence, the
 * Tidefall navigator, the Frostmere warden, the archivist of Aetheris, and the
 * thief who is not what she says she is.</p>
 */
public final class Companions {

    /** A companion's unique combat ability, on a cooldown. */
    public static final class Ability {
        public final String name;
        /** What it does, in one line, for the UI. */
        public final String effect;
        /** Seconds between uses. */
        public final float cooldown;
        /** How much Light it costs. */
        public final float light;

        public Ability(String name, String effect, float cooldown, float light) {
            this.name = name;
            this.effect = effect;
            this.cooldown = cooldown;
            this.light = light;
        }
    }

    /** One companion. */
    public static final class Def {
        public final String name;
        public final String title;
        /** Where they are found. */
        public final int region;
        /** Their Gift: what they can do that nobody else can. */
        public final String gift;
        /** One line of personal history, for the codex. */
        public final String history;
        public final Ability ability;
        /** The spell combination they can teach, as a forge hint. */
        public final String teaches;
        /** Index into {@link QuestCorpus}, or -1. Their personal quest. */
        public final int personalQuest;

        Def(String name, String title, int region, String gift, String history,
            Ability ability, String teaches, int personalQuest) {
            this.name = name;
            this.title = title;
            this.region = region;
            this.gift = gift;
            this.history = history;
            this.ability = ability;
            this.teaches = teaches;
            this.personalQuest = personalQuest;
        }
    }

    public static final Def[] ALL = build();

    private static Def[] build() {
        java.util.List<Def> l = new java.util.ArrayList<Def>();

        l.add(new Def("SERA VALE", "STAR GUARD, DISGRACED", 0,
                "SHE REMEMBERS EVERY SEAL SHE HAS EVER TOUCHED.",
                "SHE WAS THERE WHEN THE FIRST SEAL BROKE. SHE HAS NEVER SAID SO.",
                new Ability("SEAL WARD", "A WALL OF SEAL-LIGHT THAT HOLDS FOR SIX SECONDS.",
                        28f, 30f),
                "LUMEN + WARD",
                3));

        l.add(new Def("KESH", "HOLLOW-TOUCHED", 2,
                "HE HEARS WHAT THE SILENCE IS SAYING.",
                "HE WALKED INTO A HOLLOW AND CAME OUT WITH SOMETHING ELSE LISTENING.",
                new Ability("HOLLOW ECHO", "MARKS EVERY ENEMY IN RANGE; EACH TAKES +30% DAMAGE.",
                        22f, 18f),
                "SHADE + TRAP",
                7));

        l.add(new Def("NADI TORR", "TIDEFALL NAVIGATOR", 3,
                "SHE CAN SAIL ANYWHERE THERE WAS EVER WATER.",
                "HER SHIP WAS THE LAST THING OUT OF TIDEFALL BEFORE IT DROWNED.",
                new Ability("TIDE STEP", "TELEPORTS THE PLAYER THREE METRES TOWARD HER.",
                        16f, 12f),
                "GALE + DASH",
                11));

        l.add(new Def("BRANN", "FROSTMERE WARDEN", 5,
                "NOTHING HE STANDS ON CAN MOVE HIM.",
                "HE HELD A GATE FOR ELEVEN DAYS AND DOES NOT THINK IT WAS BRAVE.",
                new Ability("PERMAFROST", "RINGS THE PLAYER IN ICE THAT SLOWS EVERYTHING NEARBY.",
                        32f, 26f),
                "FROST + FIELD",
                15));

        l.add(new Def("MIRA SOLENNE", "ARCHIVIST OF AETHERIS", 13,
                "SHE READS A SPELL THE WAY OTHER PEOPLE READ A FACE.",
                "SHE HAS READ THE FIRST SILENCE. SHE IS STILL HOLDING THE PAGE.",
                new Ability("RECALL", "RESETS THE COOLDOWN ON THE LAST SPELL CAST.",
                        24f, 20f),
                "STORM + BEAM",
                19));

        l.add(new Def("WREN", "SHE IS NOT A THIEF", 9,
                "SHE KNOWS WHAT YOU ARE GOING TO DO BEFORE YOU DO.",
                "NOBODY HAS EVER ASKED HER REAL NAME. SHE IS NOT SURE SHE HAS ONE.",
                new Ability("QUICKSTEP", "THE NEXT THREE DODGES COST NOTHING AND GO TWICE AS FAR.",
                        20f, 14f),
                "SHADE + DASH",
                23));

        return l.toArray(new Def[0]);
    }

    public static Def get(int index) {
        if (index < 0 || index >= ALL.length) {
            return null;
        }
        return ALL[index];
    }

    public static int count() {
        return ALL.length;
    }

    // --- synergy -------------------------------------------------------------

    /** A combination of two companions that unlocks something neither has alone. */
    public static final class Synergy {
        public final int a;
        public final int b;
        public final String name;
        public final String effect;

        Synergy(int a, int b, String name, String effect) {
            this.a = a;
            this.b = b;
            this.name = name;
            this.effect = effect;
        }
    }

    /**
     * The pairs that matter.
     *
     * <p>Deliberately not every pair: a synergy is a discovery, and a system
     * where all fifteen combinations do something has no discovery in it. These
     * are the ones the characters have a reason to have.</p>
     */
    public static final Synergy[] SYNERGIES = {
            new Synergy(0, 1, "SEAL AND ECHO",
                    "SERA'S WARD ALSO SILENCES HOLLOWS INSIDE IT."),
            new Synergy(0, 4, "THE UNREAD PAGE",
                    "SERA'S WARD LASTS TWICE AS LONG WHILE MIRA HOLDS IT OPEN."),
            new Synergy(1, 5, "WHAT THE SILENCE KNOWS",
                    "KESH'S MARK LETS WREN STRIKE FROM ANY ANGLE."),
            new Synergy(2, 3, "HOLD THE LINE",
                    "NADI AND BRANN TOGETHER MAKE A STEP THAT CANNOT BE INTERRUPTED."),
            new Synergy(3, 5, "THE GATE AND THE GHOST",
                    "BRANN'S ICE HIDES WREN COMPLETELY."),
            new Synergy(1, 4, "TWO KINDS OF READING",
                    "KESH HEARS WHAT MIRA IS READING AND FINDS THE SPELL IN IT.")
    };

    /** The synergy two companions unlock together, or null. */
    public static Synergy synergyBetween(int a, int b) {
        for (int i = 0; i < SYNERGIES.length; i++) {
            Synergy s = SYNERGIES[i];
            if ((s.a == a && s.b == b) || (s.a == b && s.b == a)) {
                return s;
            }
        }
        return null;
    }

    // --- relationship -------------------------------------------------------

    /**
     * How far a companion trusts the player.
     *
     * <p>Five steps, because the document wants relationship progression to be
     * a thing that happens in the story rather than a bar that fills from
     * proximity. A companion at CONFIDANT has told the player something they
     * have not told anyone.</p>
     */
    public enum Bond {
        STRANGER(0),
        ACQUAINTANCE(1),
        ALLY(2),
        FRIEND(3),
        CONFIDANT(4);

        /** How many conversation choices it takes to reach this. */
        public final int threshold;

        Bond(int threshold) {
            this.threshold = threshold;
        }

        public static Bond at(int points) {
            Bond best = STRANGER;
            for (Bond b : values()) {
                if (points >= b.threshold * 2) {
                    best = b;
                }
            }
            return best;
        }
    }

    /** What a companion says at each bond, in the codex. */
    public static String lineFor(int companion, Bond bond) {
        switch (bond) {
            case STRANGER:
                return "WE HAVE NOT SPOKEN.";
            case ACQUAINTANCE:
                return "I KNOW YOUR NAME. THAT IS ALL, FOR NOW.";
            case ALLY:
                return "I WILL STAND WHERE YOU POINT. DO NOT WASTE IT.";
            case FRIEND:
                return "I HAVE SEEN WHAT YOU DO WHEN NOBODY IS WATCHING.";
            case CONFIDANT:
            default:
                return "THERE IS SOMETHING I HAVE NOT TOLD ANYONE. LISTEN.";
        }
    }
}
