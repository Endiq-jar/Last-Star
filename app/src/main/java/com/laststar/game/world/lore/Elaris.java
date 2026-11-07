package com.laststar.game.world.lore;

/**
 * The world of Elaris: its fourteen settlements, and the terrain each one is
 * built on.
 *
 * <p>Every settlement is a place you can travel to and walk around, not an
 * entry in a menu. That means each one needs its own terrain, its own palette
 * and its own population of Hollow creatures -- so a region here is a bundle of
 * generation parameters rather than a name and a line of flavour text.</p>
 *
 * <p>Per the design document: regions connect through exploration and story
 * rather than feeling like disconnected levels, which is why the rift network
 * is a single graph the player opens up rather than a level select.</p>
 */
public final class Elaris {

    /**
     * One of the fourteen settlements.
     *
     * <p>The terrain fields are deliberately coarse. A region is recognised by
     * its silhouette and its colour long before the player reads its name, so
     * shape and palette are what carry its identity.</p>
     */
    public static final class Region {
        public final int index;
        public final String name;
        /** One line the world map shows under the name. */
        public final String identity;

        // --- terrain shaping ---
        /** Amplitude of the ridge noise, in metres. Terrain roughness. */
        public final float ridgeAmp;
        /** Amplitude of the rolling base noise, in metres. */
        public final float baseAmp;
        /** How deep the lake basin is carved, in metres. 0 is no lake. */
        public final float lakeDepth;
        /** Height of the flattened plateau at the origin. */
        public final float plateauHeight;
        /** Water plane height. */
        public final float waterLevel;
        /** How far out from the centre the rim starts to rise (0..1). */
        public final float rimStart;

        // --- palette ---
        public final float skyR, skyG, skyB;
        /** Grass colour at the low end of the blend. */
        public final float grassLowR, grassLowG, grassLowB;
        /** Grass colour at the high end. */
        public final float grassHighR, grassHighG, grassHighB;
        /** Rock and peak colour. */
        public final float rockR, rockG, rockB;
        /** How much of the region is snow, 0..1. */
        public final float snow;

        // --- population ---
        /** Names of the Hollow creatures that live here. */
        public final String[] creatures;
        /** The region's boss. */
        public final String boss;
        /** Level band the region's content is balanced for. */
        public final int minLevel, maxLevel;
        /** Rift gate this region is reached through, or -1 for the start. */
        public final int gateFrom;

        Region(int index, String name, String identity,
               float ridgeAmp, float baseAmp, float lakeDepth, float plateauHeight,
               float waterLevel, float rimStart,
               float skyR, float skyG, float skyB,
               float grassLowR, float grassLowG, float grassLowB,
               float grassHighR, float grassHighG, float grassHighB,
               float rockR, float rockG, float rockB, float snow,
               String[] creatures, String boss, int minLevel, int maxLevel,
               int gateFrom) {
            this.index = index;
            this.name = name;
            this.identity = identity;
            this.ridgeAmp = ridgeAmp;
            this.baseAmp = baseAmp;
            this.lakeDepth = lakeDepth;
            this.plateauHeight = plateauHeight;
            this.waterLevel = waterLevel;
            this.rimStart = rimStart;
            this.skyR = skyR;
            this.skyG = skyG;
            this.skyB = skyB;
            this.grassLowR = grassLowR;
            this.grassLowG = grassLowG;
            this.grassLowB = grassLowB;
            this.grassHighR = grassHighR;
            this.grassHighG = grassHighG;
            this.grassHighB = grassHighB;
            this.rockR = rockR;
            this.rockG = rockG;
            this.rockB = rockB;
            this.snow = snow;
            this.creatures = creatures;
            this.boss = boss;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.gateFrom = gateFrom;
        }
    }

    /**
     * The fourteen settlements, in the order the document lists them.
     *
     * <p>The first is where a new player wakes up; the rest open up as the
     * quest line reaches them. Each is shaped to be recognisable from its
     * silhouette alone: Ashvale is a soft green bowl, Frostmere is white and
     * jagged, The Deep has no sky at all and is walled in by rock.</p>
     */
    public static final Region[] REGIONS = {
            new Region(0, "ASHVALE",
                    "Starting city and first safe hub",
                    21f, 5.5f, 3.2f, 0.35f, -0.55f, 0.30f,
                    0.42f, 0.62f, 0.88f,
                    0.30f, 0.56f, 0.26f, 0.50f, 0.78f, 0.38f,
                    0.50f, 0.54f, 0.62f, 0.05f,
                    new String[]{"HOLLOW CRAWLER", "ASH WISP", "SHARD HOUND"},
                    "THE ASHEN WARDEN", 1, 5, -1),

            new Region(1, "ELARIS CAPITAL",
                    "Political and military centre",
                    17f, 4.2f, 0f, 1.10f, -0.55f, 0.34f,
                    0.48f, 0.60f, 0.80f,
                    0.34f, 0.54f, 0.28f, 0.58f, 0.76f, 0.36f,
                    0.55f, 0.56f, 0.60f, 0f,
                    new String[]{"IMPERIAL SENTINEL", "BANNER WRAITH", "GATE GOLEM"},
                    "THE FIRST MARSHAL", 5, 10, 0),

            new Region(2, "LUNARETH",
                    "City of scholars and magic",
                    14f, 3.4f, 2.4f, 0.70f, -0.55f, 0.36f,
                    0.36f, 0.44f, 0.74f,
                    0.28f, 0.44f, 0.40f, 0.44f, 0.62f, 0.52f,
                    0.46f, 0.48f, 0.66f, 0.10f,
                    new String[]{"MARGIN WISP", "INK EATER", "ARCHIVIST SHADE"},
                    "THE UNWRITTEN", 8, 14, 0),

            new Region(3, "TIDEFALL",
                    "Coastal kingdom",
                    11f, 3.0f, 9.5f, 0.20f, -0.55f, 0.22f,
                    0.40f, 0.70f, 0.86f,
                    0.34f, 0.58f, 0.36f, 0.56f, 0.72f, 0.44f,
                    0.44f, 0.56f, 0.62f, 0f,
                    new String[]{"TIDE REAVER", "SALT HOUND", "DROWNED PILGRIM"},
                    "THE TIDE MOTHER", 10, 16, 1),

            new Region(4, "GLASSHEAVEN",
                    "Crystal city",
                    38f, 2.2f, 0f, 0.90f, -0.55f, 0.28f,
                    0.62f, 0.74f, 0.94f,
                    0.52f, 0.62f, 0.70f, 0.72f, 0.80f, 0.86f,
                    0.70f, 0.76f, 0.88f, 0.35f,
                    new String[]{"GLASS SENTINEL", "PRISM MOTH", "SHARD CHOIR"},
                    "THE FACETED ONE", 14, 20, 2),

            new Region(5, "FROSTMERE",
                    "Frozen mountain settlement",
                    44f, 6.5f, 2.0f, 0.60f, -0.55f, 0.26f,
                    0.70f, 0.80f, 0.94f,
                    0.48f, 0.58f, 0.56f, 0.70f, 0.78f, 0.78f,
                    0.62f, 0.68f, 0.78f, 0.72f,
                    new String[]{"RIME STALKER", "FROST BOUND", "HOAR WOLF"},
                    "THE WINTER KEEPER", 16, 22, 3),

            new Region(6, "SUNCREST",
                    "Desert trading city",
                    19f, 8.5f, 0f, 0.45f, -0.55f, 0.30f,
                    0.90f, 0.74f, 0.48f,
                    0.72f, 0.62f, 0.36f, 0.88f, 0.76f, 0.44f,
0.72f, 0.60f, 0.44f, 0f,
                    new String[]{"DUNE REAVER", "SCARAB SWARM", "MIRAGE DANCER"},
                    "THE SUNLESS TYRANT", 18, 24, 4),

            new Region(7, "WHISPERING ORCHARD",
                    "Ancient forest settlement",
                    15f, 4.0f, 1.6f, 0.30f, -0.55f, 0.32f,
                    0.44f, 0.56f, 0.44f,
                    0.22f, 0.40f, 0.20f, 0.38f, 0.60f, 0.28f,
                    0.40f, 0.44f, 0.40f, 0f,
                    new String[]{"BARK HUSK", "ORCHARD WRAITH", "GREEN MAW"},
                    "THE ROOT THAT REMEMBERS", 20, 26, 5),

            new Region(8, "RAVENHOLD",
                    "Fortress city",
                    28f, 3.6f, 0f, 1.40f, -0.55f, 0.30f,
                    0.34f, 0.36f, 0.48f,
                    0.28f, 0.34f, 0.26f, 0.40f, 0.44f, 0.34f,
                    0.36f, 0.36f, 0.42f, 0.12f,
                    new String[]{"RAVEN KNIGHT", "SIEGE HUSK", "CROW SWARM"},
                    "THE BLACK BANNER", 22, 28, 6),

            new Region(9, "DUSKHAVEN",
                    "City surrounded by perpetual twilight",
                    24f, 4.6f, 3.0f, 0.50f, -0.55f, 0.28f,
                    0.30f, 0.30f, 0.52f,
                    0.26f, 0.30f, 0.42f, 0.42f, 0.40f, 0.56f,
                    0.34f, 0.32f, 0.46f, 0f,
                    new String[]{"DUSK REAVER", "VEIL HOUND", "TWILIGHT CHORUS"},
                    "THE HOLLOW LANTERN", 24, 30, 7),

            new Region(10, "THE DEEP",
                    "Underground civilization",
                    52f, 2.4f, 0f, 0f, -900f, 0.16f,
                    0.14f, 0.14f, 0.18f,
                    0.20f, 0.26f, 0.28f, 0.34f, 0.36f, 0.34f,
                    0.28f, 0.26f, 0.30f, 0f,
                    new String[]{"DEEP BURROWER", "STONE CHOIR", "GLOW WORM"},
                    "THE UNDERCROWN", 26, 32, 8),

            new Region(11, "STARFALL HAVEN",
                    "Settlement built around a fallen star",
                    26f, 5.0f, 2.2f, 0.80f, -0.55f, 0.30f,
                    0.44f, 0.40f, 0.72f,
                    0.34f, 0.36f, 0.52f, 0.54f, 0.52f, 0.70f,
                    0.50f, 0.46f, 0.66f, 0.08f,
                    new String[]{"STAR TOUCHED", "FALLEN CHORUS", "CRATER HOUND"},
                    "THE FALLEN STAR", 28, 34, 9),

            new Region(12, "EVERGLOOM",
                    "Abandoned settlement near dangerous Hollows",
                    33f, 6.0f, 4.0f, 0.10f, -0.55f, 0.24f,
                    0.24f, 0.22f, 0.30f,
                    0.20f, 0.24f, 0.20f, 0.32f, 0.30f, 0.28f,
                    0.30f, 0.26f, 0.30f, 0.06f,
                    new String[]{"HOLLOW SPAWN", "GLOOM WALKER", "EMPTY CHOIR"},
                    "THE FIRST SILENCE", 30, 38, 10),

            new Region(13, "AETHERIS",
                    "Ancient city connected to the Seven Seals",
                    40f, 3.0f, 1.4f, 1.20f, -0.55f, 0.32f,
                    0.56f, 0.52f, 0.86f,
                    0.40f, 0.44f, 0.50f, 0.66f, 0.66f, 0.78f,
                    0.64f, 0.62f, 0.80f, 0.30f,
                    new String[]{"SEAL WARDEN", "AETHER CHOIR", "LAW GIVEN"},
                    "THE KEEPER", 34, 40, 11),
    };

    /** A distinct, explorable subterranean network reached through Evergloom. */
    public static final Region HOLLOW_DEPTHS = new Region(14, "THE HOLLOW DEPTHS",
            "An explorable rift network beneath Evergloom",
            7f, 2.8f, 0f, 0f, -100f, 0.14f,
            0.12f, 0.10f, 0.28f,
            0.12f, 0.16f, 0.22f, 0.26f, 0.22f, 0.40f,
            0.32f, 0.30f, 0.46f, 0f,
            new String[]{"HOLLOW SPAWN", "GLOOM WALKER", "EMPTY CHOIR"},
            "THE FIRST SILENCE", 30, 38, 12);

    /** Total settlements, asserted by the tests. */
    public static final int REGION_COUNT = 14;

    /** Special locations, which are not settlements. */
    public static final String[][] SPECIAL_LOCATIONS = {
            {"THE ARCHIVE", "MEMORY VAULT DUNGEON"},
            {"THE FIRST SILENCE", "ANCIENT CATASTROPHE SITE"},
            {"THE SEVEN SEALS", "SEVEN MAJOR STRUCTURES"},
            {"THE HOLLOW DEPTHS", "DANGEROUS DUNGEON NETWORK"},
            {"THE FORGOTTEN OBSERVATORY", "HIDDEN MAGICAL RESEARCH FACILITY"}
    };

    private Elaris() {
    }

    /** The region array itself, for callers that index it directly. */
    public static Region[] regions() {
        return REGIONS;
    }

    public static Region region(int index) {
        if (index < 0 || index >= REGIONS.length) {
            return REGIONS[0];
        }
        return REGIONS[index];
    }
}
