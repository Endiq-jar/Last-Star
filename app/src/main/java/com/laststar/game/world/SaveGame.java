package com.laststar.game.world;

import com.laststar.game.world.lore.QuestCorpus;
import com.laststar.game.world.lore.Gear;
import com.laststar.game.world.lore.SpellCorpus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;

/**
 * The whole run, as bytes.
 *
 * <p>The design document lists saving and loading as a core feature, and with a
 * hundred and one spells each carrying mastery the alternative is losing hours
 * of progress to a phone call. So everything that represents progress is here:
 * which pages are awake and how far each has grown, which quests are open and
 * how far each has got, the consequence flags, gold, Light capacity, and where
 * in Elaris the player is standing.</p>
 *
 * <p>Deliberately free of Android types. {@code SaveFile} is the thin Android
 * wrapper that decides where the bytes live; this class only knows how to turn
 * the simulation into a byte array and back, which is what lets the desktop
 * tests round-trip a real save.</p>
 */
public final class SaveGame {

    /** Current layout; older v2/v3 snapshots are migrated on read. */
    public static final int VERSION = 4;
    private static final int MAGIC = 0x4C535356;   // "LSSV"

    // --- what is saved -------------------------------------------------------
    public int region;
    public boolean inHollow;
    public int gold;
    public int level;
    public int kills;
    public int deaths;
    public int shardsCollected;
    public int runsCompleted;
    public float lightCapacity;
    public float playerX, playerY, playerZ, playerYaw;

    /** Awake flags and mastery, per spell. */
    public final boolean[] awake = new boolean[SpellCorpus.count()];
    public final int[] spellLevel = new int[SpellCorpus.count()];
    public final float[] spellXp = new float[SpellCorpus.count()];
    /** Action-bar slots, as spell indices, or -1. */
    public final int[] equipped = new int[4];

    /** Quest states and progress, per quest. */
    public final int[] questState = new int[QuestCorpus.count()];
    public final int[] questProgress = new int[QuestCorpus.count()];
    /** Consequence flags. */
    public final boolean[] flags = new boolean[QuestCorpus.Consequence.values().length];
    /** Owned equipment, and which slot each worn piece occupies. */
    public final boolean[] owned = new boolean[Gear.count()];
    public final int[] worn = {-1, -1, -1};
    /** Light capacity from progress alone, before equipment. */
    public float baseCapacity;

    private SaveGame() {
    }

    /** Captures a world's progress. */
    public static SaveGame capture(World world) {
        SaveGame s = new SaveGame();
        s.region = world.regionIndex;
        s.inHollow = world.inHollow;
        s.gold = world.gold;
        s.level = world.level();
        s.kills = world.kills;
        s.deaths = world.deaths;
        s.shardsCollected = world.shardsCollected;
        s.runsCompleted = world.runsCompleted;
        s.lightCapacity = world.spells.lightCapacity;
        s.baseCapacity = world.spells.baseLightCapacity();
        s.playerX = world.player.position.x;
        s.playerY = world.player.position.y;
        s.playerZ = world.player.position.z;
        s.playerYaw = world.player.yaw;

        for (int i = 0; i < s.awake.length; i++) {
            SpellBook.Page p = world.spells.pages[i];
            s.awake[i] = p.awake;
            s.spellLevel[i] = p.level;
            s.spellXp[i] = p.xp;
        }
        for (int i = 0; i < s.equipped.length; i++) {
            s.equipped[i] = world.spells.equipped[i];
        }
        for (int i = 0; i < s.questState.length; i++) {
            s.questState[i] = world.quests.entries[i].state;
            s.questProgress[i] = world.quests.entries[i].progress;
        }
        for (int i = 0; i < s.flags.length; i++) {
            s.flags[i] = world.quests.has(QuestCorpus.Consequence.values()[i]);
        }
        for (int i = 0; i < s.owned.length; i++) {
            s.owned[i] = world.inventory.owned[i];
        }
        for (int i = 0; i < s.worn.length; i++) {
            s.worn[i] = world.inventory.worn[i];
        }
        return s;
    }

    /**
     * Writes the save.
     *
     * <p>Quest state is written as one byte per quest rather than an int: with
     * a hundred and ninety quests that is the difference between a save that
     * fits comfortably in a preferences string and one that does not.</p>
     */
    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(region);
            out.writeInt(gold);
            out.writeInt(level);
            out.writeInt(kills);
            out.writeInt(deaths);
            out.writeInt(shardsCollected);
            out.writeInt(runsCompleted);
            out.writeBoolean(inHollow);
            out.writeFloat(lightCapacity);
            out.writeFloat(playerX);
            out.writeFloat(playerY);
            out.writeFloat(playerZ);
            out.writeFloat(playerYaw);

            out.writeInt(awake.length);
            for (int i = 0; i < awake.length; i++) {
                out.writeBoolean(awake[i]);
                out.writeInt(spellLevel[i]);
                out.writeFloat(spellXp[i]);
            }
            for (int i = 0; i < equipped.length; i++) {
                out.writeInt(equipped[i]);
            }

            out.writeInt(questState.length);
            for (int i = 0; i < questState.length; i++) {
                out.writeByte(questState[i]);
                out.writeShort(questProgress[i]);
            }
            out.writeInt(flags.length);
            for (int i = 0; i < flags.length; i++) {
                out.writeBoolean(flags[i]);
            }
            out.writeInt(owned.length);
            for (int i = 0; i < owned.length; i++) {
                out.writeBoolean(owned[i]);
            }
            for (int i = 0; i < worn.length; i++) {
                out.writeInt(worn[i]);
            }
            out.writeFloat(baseCapacity);
            out.flush();
        } catch (IOException e) {
            // A ByteArrayOutputStream cannot fail; this is unreachable but the
            // compiler needs the catch.
            throw new IllegalStateException("save failed", e);
        }
        byte[] payload = bytes.toByteArray();
        CRC32 checksum = new CRC32();
        checksum.update(payload);
        try {
            DataOutputStream trailer = new DataOutputStream(bytes);
            trailer.writeInt((int) checksum.getValue());
            trailer.flush();
        } catch (IOException e) {
            throw new IllegalStateException("save checksum failed", e);
        }
        return bytes.toByteArray();
    }

    /**
     * Reads a save back.
     *
     * @throws IOException when the bytes are not a save of this version, or are
     *                     truncated -- which is what a half-written file looks
     *                     like and must not crash the game
     */
    public static SaveGame fromBytes(byte[] data) throws IOException {
        if (data == null || data.length < 8) {
            throw new IOException("save is truncated");
        }
        DataInputStream header = new DataInputStream(new ByteArrayInputStream(data));
        if (header.readInt() != MAGIC) {
            throw new IOException("not a Last Star save");
        }
        int version = header.readInt();
        if (version != 2 && version != 3 && version != VERSION) {
            throw new IOException("save version " + version + ", expected 2, 3 or " + VERSION);
        }
        int payloadLength = data.length;
        if (version == VERSION) {
            if (data.length < 12) {
                throw new IOException("save checksum is missing");
            }
            CRC32 checksum = new CRC32();
            checksum.update(data, 0, data.length - 4);
            int savedChecksum = ((data[data.length - 4] & 0xff) << 24)
                    | ((data[data.length - 3] & 0xff) << 16)
                    | ((data[data.length - 2] & 0xff) << 8)
                    | (data[data.length - 1] & 0xff);
            if ((int) checksum.getValue() != savedChecksum) {
                throw new IOException("save checksum mismatch");
            }
            payloadLength -= 4;
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, payloadLength));
        in.readInt(); // magic already checked
        in.readInt(); // version already checked
        SaveGame s = new SaveGame();
        s.region = in.readInt();
        s.gold = in.readInt();
        s.level = in.readInt();
        s.kills = in.readInt();
        s.deaths = in.readInt();
        s.shardsCollected = in.readInt();
        s.runsCompleted = in.readInt();
        s.inHollow = version >= 3 && in.readBoolean();
        s.lightCapacity = in.readFloat();
        s.playerX = in.readFloat();
        s.playerY = in.readFloat();
        s.playerZ = in.readFloat();
        s.playerYaw = in.readFloat();

        int spellCount = in.readInt();
        if (spellCount != s.awake.length) {
            throw new IOException("save has " + spellCount + " spells, expected "
                    + s.awake.length);
        }
        for (int i = 0; i < spellCount; i++) {
            s.awake[i] = in.readBoolean();
            s.spellLevel[i] = in.readInt();
            s.spellXp[i] = in.readFloat();
        }
        for (int i = 0; i < s.equipped.length; i++) {
            s.equipped[i] = in.readInt();
        }

        int questCount = in.readInt();
        if (questCount != s.questState.length) {
            throw new IOException("save has " + questCount + " quests, expected "
                    + s.questState.length);
        }
        for (int i = 0; i < questCount; i++) {
            s.questState[i] = in.readByte();
            s.questProgress[i] = in.readShort();
        }
        int flagCount = in.readInt();
        if (flagCount != s.flags.length) {
            throw new IOException("save has " + flagCount + " flags, expected "
                    + s.flags.length);
        }
        for (int i = 0; i < flagCount; i++) {
            s.flags[i] = in.readBoolean();
        }
        int gearCount = in.readInt();
        if (gearCount != s.owned.length) {
            throw new IOException("save has " + gearCount + " items, expected "
                    + s.owned.length);
        }
        for (int i = 0; i < gearCount; i++) {
            s.owned[i] = in.readBoolean();
        }
        for (int i = 0; i < s.worn.length; i++) {
            s.worn[i] = in.readInt();
        }
        s.baseCapacity = in.readFloat();
        if (in.available() != 0) {
            throw new IOException("unexpected trailing save data");
        }
        s.validate();
        return s;
    }

    private void validate() throws IOException {
        if (region < 0 || region >= com.laststar.game.world.lore.Elaris.REGION_COUNT
                || level < 1 || gold < 0 || kills < 0 || deaths < 0
                || shardsCollected < 0 || runsCompleted < 0) {
            throw new IOException("save contains invalid progress values");
        }
        if (!finite(lightCapacity) || lightCapacity < 0f
                || !finite(baseCapacity) || baseCapacity < 0f
                || !finite(playerX) || !finite(playerY) || !finite(playerZ)
                || !finite(playerYaw)) {
            throw new IOException("save contains invalid player values");
        }
        for (int i = 0; i < spellLevel.length; i++) {
            if (spellLevel[i] < 1 || !finite(spellXp[i]) || spellXp[i] < 0f) {
                throw new IOException("save contains invalid spell progress");
            }
        }
        for (int i = 0; i < equipped.length; i++) {
            if (equipped[i] < -1 || equipped[i] >= awake.length) {
                throw new IOException("save contains invalid equipped spell");
            }
        }
        for (int i = 0; i < questState.length; i++) {
            if (questState[i] < QuestLog.OPEN || questState[i] > QuestLog.LOCKED
                    || questProgress[i] < 0) {
                throw new IOException("save contains invalid quest progress");
            }
        }
        for (int i = 0; i < worn.length; i++) {
            if (worn[i] < -1 || worn[i] >= owned.length) {
                throw new IOException("save contains invalid equipment slot");
            }
        }
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    /**
     * Pours a save back into a world built for the same seed.
     *
     * <p>The world is constructed fresh and then overwritten rather than
     * rebuilt, so terrain generation and prop placement cost what they always
     * cost and nothing has to be serialised for them.</p>
     */
    public void applyTo(World world) {
        if (region != world.regionIndex) {
            world.enterRegion(region);
        }
        if (inHollow && !world.inHollow) {
            world.enterHollow();
        }
        world.gold = gold;
        world.kills = kills;
        world.deaths = deaths;
        world.shardsCollected = shardsCollected;
        world.runsCompleted = runsCompleted;
        world.restoreLevel(level);
        world.spells.lightCapacity = lightCapacity;
        world.spells.light = lightCapacity;

        for (int i = 0; i < awake.length && i < world.spells.pages.length; i++) {
            SpellBook.Page p = world.spells.pages[i];
            p.awake = awake[i];
            p.level = spellLevel[i];
            p.xp = spellXp[i];
            p.cooldownLeft = 0f;
        }
        for (int i = 0; i < equipped.length; i++) {
            world.spells.equipped[i] = equipped[i];
        }
        for (int i = 0; i < questState.length && i < world.quests.entries.length; i++) {
            world.quests.entries[i].state = questState[i];
            world.quests.entries[i].progress = questProgress[i];
        }
        for (int i = 0; i < flags.length; i++) {
            world.quests.setFlag(QuestCorpus.Consequence.values()[i], flags[i]);
        }
        for (int i = 0; i < owned.length && i < world.inventory.owned.length; i++) {
            world.inventory.owned[i] = owned[i];
        }
        for (int i = 0; i < worn.length && i < world.inventory.worn.length; i++) {
            world.inventory.worn[i] = worn[i];
        }
        world.spells.restoreBaseCapacity(baseCapacity);
        // The ceilings follow what is worn, so this has to run last.
        world.refreshEquipment();
        world.player.position = new com.laststar.game.core.Vec3(playerX, playerY, playerZ);
        world.player.yaw = playerYaw;
        // Open anything the restored state has made available, so a loaded
        // save is playable immediately rather than after the next completion.
        world.quests.refreshAvailability();
    }
}
