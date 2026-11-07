package com.laststar.game.world.lore;

/**
 * The conversations, as data.
 *
 * <p>Every companion has a conversation at each bond, and the choices inside it
 * matter: one answer earns their trust, the other costs it. The document asks
 * for dialogue choices and relationship progression as two separate things, so
 * the choice is what moves the number rather than merely flavour over it.</p>
 *
 * <p>Consequences from the journal also surface here: a companion who has seen
 * the player save a settlement says so, and one who has seen them fail does not
 * pretend otherwise. That is what makes the consequence flags earn their keep.</p>
 */
public final class Dialogue {

    /** One thing the player can say, and what it does. */
    public static final class Choice {
        public final String text;
        /** Trust gained; negative for a cold answer. */
        public final int trust;
        /** What the companion says back. */
        public final String reply;

        public Choice(String text, int trust, String reply) {
            this.text = text;
            this.trust = trust;
            this.reply = reply;
        }
    }

    /** One exchange: what the companion says, and what the player can answer. */
    public static final class Node {
        public final String speaker;
        public final String line;
        public final Choice[] choices;

        public Node(String speaker, String line, Choice[] choices) {
            this.speaker = speaker;
            this.line = line;
            this.choices = choices;
        }
    }

    /**
     * The conversation a companion has at a given bond.
     *
     * <p>Indexed by companion, with one node per bond level. A companion at
     * CONFIDANT has a different conversation from the one they had at
     * ACQUAINTANCE, and the earlier one is not repeated.</p>
     */
    public static Node nodeFor(int companion, Companions.Bond bond) {
        Companions.Def c = Companions.get(companion);
        if (c == null) {
            return null;
        }
        switch (bond) {
            case STRANGER:
                return new Node(c.name,
                        "YOU ARE THE ONE THE SKY FOLLOWS. I HAVE NOT DECIDED WHAT THAT MEANS.",
                        new Choice[]{
                                new Choice("I have not decided either.", 2,
                                        "THEN WE ARE BOTH WAITING. GOOD."),
                                new Choice("It means I am useful. Remember it.", -1,
                                        "USEFUL THINGS GET SPENT.")
                        });
            case ACQUAINTANCE:
                return new Node(c.name,
                        "I KNEW SOMEONE WHO CARRIED THE LIGHT LIKE THAT. IT DID NOT END WELL.",
                        new Choice[]{
                                new Choice("Tell me about them.", 2,
                                        "NOT YET. WHEN I TRUST YOU."),
                                new Choice("I am not them.", -1,
                                        "NO. YOU ARE NOT. THAT IS NOT ALWAYS A COMFORT.")
                        });
            case ALLY:
                return new Node(c.name,
                        "I WILL STAND WHERE YOU POINT. BUT I WILL NOT THROW MY LIFE AWAY FOR A SHARD.",
                        new Choice[]{
                                new Choice("I would not ask you to.", 2,
                                        "GOOD. I WILL REMEMBER YOU SAID IT."),
                                new Choice("Then stand closer and I will not lose you.", 2,
                                        "...THAT IS THE FIRST THING YOU HAVE SAID THAT SOUNDED LIKE A PLAN.")
                        });
            case FRIEND:
                // This is where the personal history comes out.
                return new Node(c.name, c.history,
                        new Choice[]{
                                new Choice("That was not your fault.", 2,
                                        "I KNOW. I HAVE HAD A LONG TIME TO KNOW IT."),
                                new Choice("Why did you never tell anyone?", 1,
                                        "BECAUSE NOBODY ASKED, AND YOU JUST DID.")
                        });
            case CONFIDANT:
            default:
                return new Node(c.name,
                        "THERE IS SOMETHING I HAVE NOT TOLD ANYONE. AND I AM TELLING YOU.",
                        new Choice[]{
                                new Choice("I am listening.", 2,
                                        "I KNOW. THAT IS WHY IT IS YOU."),
                                new Choice("Keep it. Some things should stay kept.", 1,
                                        "...THANK YOU. I DID NOT EXPECT THAT.")
                        });
        }
    }

    /**
     * An extra line a companion has when a consequence flag is set.
     *
     * <p>The journal's flags are what make the world remember. A companion who
     * has seen the player save a settlement mentions it, and one who saw them
     * open a Hollow does not let it go.</p>
     */
    public static String asideFor(int companion, QuestCorpus.Consequence consequence) {
        Companions.Def c = Companions.get(companion);
        if (c == null) {
            return null;
        }
        switch (consequence) {
            case SETTLEMENT_SAVED:
                return "THEY WILL TELL THAT STORY FOR A GENERATION. YOU SHOULD KNOW THAT.";
            case HOLLOW_OPENED:
                return "I FELT THAT. EVERY ONE OF US FELT THAT.";
            case COMPANION_TRUSTS_YOU:
                return "YOU DID NOT HAVE TO DO THAT. I NOTICED THAT YOU DID.";
            case MEMORY_RETURNED:
                return "YOU GAVE IT BACK. MOST PEOPLE WOULD HAVE KEPT IT.";
            case ASHVALE_OWES_YOU:
                return "THEY OWE YOU. DO NOT LET THEM FORGET IT.";
            case STAR_GUARD_SUSPICIOUS:
                return "THE GUARD IS WATCHING YOU NOW. I WOULD WATCH THEM BACK.";
            case NONE:
            default:
                return null;
        }
    }
}
