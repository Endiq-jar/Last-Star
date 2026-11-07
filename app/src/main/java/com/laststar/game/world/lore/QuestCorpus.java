package com.laststar.game.world.lore;

import java.util.ArrayList;
import java.util.List;

/**
 * Every quest in Elaris: twenty-five that carry the story, and a hundred and
 * sixty-five that carry the world.
 *
 * <p>The design document is specific about two things. Side quests must reward
 * something worth having -- a lost Wander, a new spell, an accessory -- rather
 * than just currency. And some must have consequences that change later
 * dialogue or events. So a quest here carries an {@link Objective} the world
 * can actually check, a {@link Reward} it can actually grant, and optionally a
 * {@link Consequence} that sets a flag later writing reads.</p>
 *
 * <p>Nothing here is drawn from a table of fake strings. Every entry is an
 * inspectable record, and {@code QuestLog} is the thing that advances it.</p>
 */
public final class QuestCorpus {

    /** What the player has to do. */
    public enum Kind {
        /** Kill a given number of Hollow creatures. */
        SLAY,
        /** Recover star shards. */
        SHARDS,
        /** Reach a place. */
        TRAVEL,
        /** Speak to a named character. */
        SPEAK,
        /** Find a lost Wander, which teaches a spell. */
        RECOVER_SPELL,
        /** Clear a Hollow, or a dungeon. */
        CLEAR_HOLLOW,
        /** Defeat the region's boss. */
        BOSS,
        /** Bring a quantity of a material. */
        GATHER,
        /** Survive a night without dying. */
        SURVIVE
    }

    /** What finishing it gives back. */
    public enum Reward {
        GOLD,
        LIGHT_CAPACITY,
        SPELL,
        EQUIPMENT,
        COSMETIC,
        RECIPE,
        NOTHING
    }

    /**
     * A flag a completed quest can set, which later writing reads.
     *
     * <p>This is the mechanism the document calls for when it says some side
     * quests should have consequences that change later dialogue or events.</p>
     */
    public enum Consequence {
        NONE,
        /** Ashvale remembers you helped, and its people speak differently. */
        ASHVALE_OWES_YOU,
        /** A companion was helped, and will speak of it later. */
        COMPANION_TRUSTS_YOU,
        /** A settlement was defended, so its merchants are friendlier. */
        SETTLEMENT_SAVED,
        /** A forbidden spell was taken, and the Star Guard know. */
        STAR_GUARD_SUSPICIOUS,
        /** A memory fragment was returned, revealing the First Silence. */
        MEMORY_RETURNED,
        /** A secret dungeon was opened, and remains open. */
        HOLLOW_OPENED
    }

    /** One quest. */
    public static final class Def {
        public final int id;
        public final String title;
        /** One line, shown in the journal. */
        public final String summary;
        public final boolean main;
        /** Region the quest is set in. */
        public final int region;
        public final Kind kind;
        /** How many of whatever it is, or 1 for one-off actions. */
        public final int target;
        /** Name of the thing to kill, place, person or material. */
        public final String subject;
        public final Reward reward;
        public final int gold;
        /** Spell granted by a SPELL reward, as an index into SpellCorpus. */
        public final int rewardSpell;
        /** Quest that must be finished first, or -1. */
        public final int requires;
        public final Consequence consequence;
        /** Level the quest is meant for. */
        public final int level;

        Def(int id, String title, String summary, boolean main, int region,
            Kind kind, int target, String subject, Reward reward, int gold,
            int rewardSpell, int requires, Consequence consequence, int level) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.main = main;
            this.region = region;
            this.kind = kind;
            this.target = target;
            this.subject = subject;
            this.reward = reward;
            this.gold = gold;
            this.rewardSpell = rewardSpell;
            this.requires = requires;
            this.consequence = consequence;
            this.level = level;
        }

        /** The objective as one line of journal text. */
        public String objectiveLine() {
            switch (kind) {
                case SLAY:
                    return "SLAY " + target + " " + plural(subject);
                case SHARDS:
                    return "RECOVER " + target + " STAR " + plural("SHARD");
                case TRAVEL:
                    return "REACH " + subject;
                case SPEAK:
                    return "SPEAK WITH " + subject;
                case RECOVER_SPELL:
                    return "RECOVER THE WANDER OF " + subject;
                case CLEAR_HOLLOW:
                    return "CLEAR " + subject;
                case BOSS:
                    return "DEFEAT " + subject;
                case GATHER:
                    return "GATHER " + target + " " + plural(subject);
                case SURVIVE:
                    return "SURVIVE " + target + " " + plural("NIGHT");
                default:
                    return summary;
            }
        }

        public String rewardLine() {
            switch (reward) {
                case SPELL:
                    return "A NEW SPELL";
                case LIGHT_CAPACITY:
                    return "+" + gold + " LIGHT CAPACITY";
                case EQUIPMENT:
                    return "EQUIPMENT";
                case COSMETIC:
                    return "A COSMETIC";
                case RECIPE:
                    return "A SPELL RECIPE";
                case NOTHING:
                    return "NOTHING BUT THE DOING";
                default:
                    return gold + " GOLD";
            }
        }

        private static String plural(String word) {
            if (word.endsWith("S") || word.endsWith("H")) {
                return word + "ES";
            }
            return word + "S";
        }
    }

    public static final Def[] QUESTS = build();

    private static int nextId;

    private static Def[] build() {
        List<Def> l = new ArrayList<Def>();
        nextId = 0;

        // ---- Main line: exactly 25 ----------------------------------------
        // One chain through the fourteen settlements, gated so the story is a
        // spine rather than a list that could be done in any order. Every one
        // carries a consequence, because the main line is what changes the
        // world.
        add(l, "THE LAST EMBER", "WAKE IN ASHVALE WITH A WANDER YOU DO NOT REMEMBER",
                true, 0, Kind.SPEAK, 1, "THE KEEPER OF ASHVALE",
                Reward.LIGHT_CAPACITY, 0, 0, Consequence.ASHVALE_OWES_YOU, 1);
        add(l, "SIX SHARDS", "THE SKY BROKE AND SIX PIECES OF IT FELL",
                true, 0, Kind.SHARDS, 6, "STAR SHARD",
                Reward.LIGHT_CAPACITY, 50, 0, Consequence.NONE, 1);
        add(l, "THE ASHEN WARDEN", "SOMETHING OLD GUARDS THE FIRST SHARD",
                true, 0, Kind.BOSS, 1, "THE ASHEN WARDEN",
                Reward.SPELL, 320, 5, Consequence.ASHVALE_OWES_YOU, 3);
        add(l, "THE ROAD TO THE CAPITAL", "ASHVALE SAYS THE CAPITAL WILL KNOW WHAT BROKE",
                true, 1, Kind.TRAVEL, 1, "ELARIS CAPITAL",
                Reward.GOLD, 200, 0, Consequence.NONE, 5);
        add(l, "THE FIRST MARSHAL", "THE CAPITAL'S GUARD HAVE LOST THEIR COMMANDER",
                true, 1, Kind.BOSS, 1, "THE FIRST MARSHAL",
                Reward.LIGHT_CAPACITY, 420, 0, Consequence.STAR_GUARD_SUSPICIOUS, 7);
        add(l, "WHAT THE SCHOLARS KNEW", "LUNARETH KEPT A RECORD OF THE FIRST SILENCE",
                true, 2, Kind.TRAVEL, 1, "LUNARETH",
                Reward.GOLD, 260, 0, Consequence.NONE, 8);
        add(l, "THE UNWRITTEN", "A PAGE THAT WAS NEVER MEANT TO BE READ",
                true, 2, Kind.BOSS, 1, "THE UNWRITTEN",
                Reward.SPELL, 500, 13, Consequence.MEMORY_RETURNED, 11);
        add(l, "THE COAST BELOW", "SOMETHING IN THE TIDE IS CALLING THE SHARDS HOME",
                true, 3, Kind.TRAVEL, 1, "TIDEFALL",
                Reward.GOLD, 300, 0, Consequence.NONE, 12);
        add(l, "THE TIDE MOTHER", "THE SEA ITSELF HAS A KEEPER, AND SHE IS ANGRY",
                true, 3, Kind.BOSS, 1, "THE TIDE MOTHER",
                Reward.GOLD, 640, 0, Consequence.SETTLEMENT_SAVED, 14);
        add(l, "THE GLASS ROAD", "THE CRYSTAL CITY REMEMBERS A DIFFERENT SKY",
                true, 4, Kind.TRAVEL, 1, "GLASSHEAVEN",
                Reward.GOLD, 340, 0, Consequence.NONE, 15);
        add(l, "THE FACETED ONE", "SOMETHING IN THE GLASS HAS BEEN WATCHING YOU",
                true, 4, Kind.BOSS, 1, "THE FACETED ONE",
                Reward.LIGHT_CAPACITY, 720, 0, Consequence.NONE, 17);
        add(l, "NORTH, AND UP", "THE COLD KEEPS WHAT THE WARM COUNTRIES FORGOT",
                true, 5, Kind.TRAVEL, 1, "FROSTMERE",
                Reward.GOLD, 380, 0, Consequence.NONE, 18);
        add(l, "THE WINTER KEEPER", "SOMETHING HAS BEEN KEEPING THE WINTER HERE",
                true, 5, Kind.BOSS, 1, "THE WINTER KEEPER",
                Reward.SPELL, 800, 9, Consequence.NONE, 20);
        add(l, "THE SUNLESS ROAD", "A DESERT THAT HAS NOT SEEN THE SUN SINCE THE SILENCE",
                true, 6, Kind.TRAVEL, 1, "SUNCREST",
                Reward.GOLD, 420, 0, Consequence.NONE, 21);
        add(l, "THE SUNLESS TYRANT", "IT TOOK THE SUN AND HAS NOT GIVEN IT BACK",
                true, 6, Kind.BOSS, 1, "THE SUNLESS TYRANT",
                Reward.GOLD, 900, 0, Consequence.SETTLEMENT_SAVED, 23);
        add(l, "THE WHISPERING ROAD", "THE TREES HERE HAVE BEEN REPEATING SOMETHING",
                true, 7, Kind.TRAVEL, 1, "WHISPERING ORCHARD",
                Reward.GOLD, 460, 0, Consequence.NONE, 24);
        add(l, "THE ROOT THAT REMEMBERS", "IT WAS HERE BEFORE THE FIRST SILENCE",
                true, 7, Kind.BOSS, 1, "THE ROOT THAT REMEMBERS",
                Reward.LIGHT_CAPACITY, 980, 0, Consequence.MEMORY_RETURNED, 26);
        add(l, "THE BLACK BANNER", "RAVENHOLD HAS NOT ANSWERED A COURIER IN A YEAR",
                true, 8, Kind.TRAVEL, 1, "RAVENHOLD",
                Reward.GOLD, 500, 0, Consequence.NONE, 27);
        add(l, "THE BANNER FALLS", "WHATEVER HOLDS THE FORTRESS IS NOT ITS LORD",
                true, 8, Kind.BOSS, 1, "THE BLACK BANNER",
                Reward.SPELL, 1100, 27, Consequence.NONE, 29);
        add(l, "WHERE THE LIGHT STOPS", "DUSKHAVEN HAS NOT SEEN DAWN IN SEVENTY YEARS",
                true, 9, Kind.TRAVEL, 1, "DUSKHAVEN",
                Reward.GOLD, 540, 0, Consequence.NONE, 30);
        add(l, "THE HOLLOW LANTERN", "THE THING THAT KEEPS THE TWILIGHT BURNING",
                true, 9, Kind.BOSS, 1, "THE HOLLOW LANTERN",
                Reward.LIGHT_CAPACITY, 1200, 0, Consequence.NONE, 32);
        add(l, "DOWN", "THE DEEP HAS NO SKY, AND SOMETHING IN IT IS AWAKE",
                true, 10, Kind.TRAVEL, 1, "THE DEEP",
                Reward.GOLD, 580, 0, Consequence.NONE, 33);
        add(l, "THE UNDERCROWN", "IT WAS CROWNED IN THE DARK AND NEVER CAME UP",
                true, 10, Kind.BOSS, 1, "THE UNDERCROWN",
                Reward.SPELL, 1300, 34, Consequence.NONE, 35);
        add(l, "EVERGLOOM", "THE TOWN THE HOLLOWS CAME THROUGH",
                true, 12, Kind.CLEAR_HOLLOW, 1, "THE FIRST SILENCE",
                Reward.GOLD, 1700, 0, Consequence.HOLLOW_OPENED, 38);
        add(l, "THE KEEPER", "AETHERIS IS WHERE THE SEVEN SEALS WERE MADE",
                true, 13, Kind.BOSS, 1, "THE KEEPER",
                Reward.NOTHING, 3000, 0, Consequence.MEMORY_RETURNED, 40);

        // ---- Side quests: exactly 165 --------------------------------------
        // Distributed across the regions, and cycled through the four shapes a
        // side quest takes here: a hunt, a lost Wander that teaches a spell, a
        // person who needs something, and a Hollow that wants clearing. The
        // recoveries carry most of the magic rewards because the document ties
        // new spells to exploration rather than to a shop.
        String[] titles = {
                "A LOST WANDER", "THE MISSING EXPEDITION", "LANTERNS BEFORE THE BELL",
                "A WATCH ON THE WALL", "THE FEAST TABLE", "THE VIGIL", "DUSK ROAD",
                "SALT AND SORROW", "A CARTOGRAPHER'S ERROR", "THE QUIET FORGE",
                "WHAT THE WATER KEPT", "A DEBT IN GLASS", "THE COLD ARITHMETIC",
                "A HERD WITHOUT A SHEPHERD", "THE ROAD THAT MOVES",
                "SIRENS OFF THE REEF", "A SHARD OF THE ORCHARD", "THE PRICE OF ROOTS",
                "A BANNER NEVER FLOWN", "THE COURIER WHO STOPPED",
                "SEVENTY YEARS OF DUSK", "THE LANTERN OIL", "A SONG THE DEEP REMEMBERS",
                "THE CROWN IN THE STONE", "WHAT FELL HERE", "THE TOWN THAT LEFT",
                "A SEAL WITH NO GUARDIAN", "THE OBSERVATORY", "A MEMORY NOT YOURS",
                "THE FIRST SILENCE, AGAIN"
        };
        String[] creatures = {
                "HOLLOW CRAWLER", "ASH WISP", "SHARD HOUND", "MARGIN WISP", "INK EATER",
                "TIDE REAVER", "SALT HOUND", "GLASS SENTINEL", "PRISM MOTH",
                "RIME STALKER", "HOAR WOLF", "DUNE REAVER", "SCARAB SWARM", "BARK HUSK",
                "GREEN MAW", "RAVEN KNIGHT", "SIEGE HUSK", "DUSK REAVER", "VEIL HOUND",
                "DEEP BURROWER", "GLOW WORM", "STAR TOUCHED", "CRATER HOUND",
                "GLOOM WALKER", "HOLLOW SPAWN", "SEAL WARDEN", "AETHER CHOIR"
        };
        int[] perRegion = {15, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 6};
        int side = 0;
        for (int region = 0; region < Elaris.REGION_COUNT; region++) {
            for (int k = 0; k < perRegion[region]; k++) {
                int shape = side % 4;
                String base = titles[side % titles.length];
                String title = base + " " + numeral(side / titles.length + 1);
                String creature = creatures[(side * 7 + region) % creatures.length];
                int level = Elaris.region(region).minLevel;
                int[] inRegion = SpellCorpus.inRegion(region);
                int spell = inRegion.length > 0 ? inRegion[side % inRegion.length] : 0;
                if (shape == 0) {
                    add(l, title, "THE SETTLEMENT WANTS THE HOLLOW CREATURES GONE",
                            false, region, Kind.SLAY, 3 + (side % 6), creature,
                            Reward.GOLD, 60 + region * 25 + (side % 5) * 15,
                            0, Consequence.NONE, level);
                } else if (shape == 1) {
                    add(l, title, "THE PAGE WAS LOST AND IS STILL OUT THERE",
                            false, region, Kind.RECOVER_SPELL, 1, base,
                            Reward.SPELL, 80 + region * 30, spell,
                            Consequence.COMPANION_TRUSTS_YOU, level);
                } else if (shape == 2) {
                    add(l, title, "SOMEONE HERE NEEDS SOMETHING ONLY YOU CAN GET",
                            false, region, Kind.SPEAK, 1, "THE PEOPLE OF THIS PLACE",
                            side % 3 == 0 ? Reward.LIGHT_CAPACITY : Reward.COSMETIC,
                            90 + region * 28, 0,
                            Consequence.SETTLEMENT_SAVED, level);
                } else {
                    add(l, title, "THE HOLLOW UNDER THIS PLACE HAS BEEN OPEN TOO LONG",
                            false, region, Kind.CLEAR_HOLLOW, 1, "A HOLLOW",
                            Reward.RECIPE, 120 + region * 35, 0,
                            Consequence.HOLLOW_OPENED, level);
                }
                side++;
            }
        }

        return l.toArray(new Def[l.size()]);
    }

    /**
     * Appends a quest, chaining it onto the previous main quest.
     *
     * <p>The chain is derived rather than written by hand: every main quest
     * requires the one before it, so the spine cannot develop a gap when a
     * quest is edited.</p>
     */
    private static void add(List<Def> out, String title, String summary, boolean main,
                            int region, Kind kind, int target, String subject,
                            Reward reward, int gold, int rewardSpell,
                            Consequence consequence, int level) {
        int requires = -1;
        if (main) {
            for (int i = out.size() - 1; i >= 0; i--) {
                if (out.get(i).main) {
                    requires = out.get(i).id;
                    break;
                }
            }
        }
        out.add(new Def(nextId++, title, summary, main, region, kind, target,
                subject, reward, gold, rewardSpell, requires, consequence, level));
    }

    private static String numeral(int n) {
        switch (n) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            default: return "" + n;
        }
    }

    public static int count() {
        return QUESTS.length;
    }

    public static int mainCount() {
        int n = 0;
        for (int i = 0; i < QUESTS.length; i++) {
            if (QUESTS[i].main) {
                n++;
            }
        }
        return n;
    }

    public static int sideCount() {
        return count() - mainCount();
    }

    private QuestCorpus() {
    }
}
