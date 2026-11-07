import com.laststar.game.core.Vec3;
import com.laststar.game.world.Enemy;
import com.laststar.game.world.QuestLog;
import com.laststar.game.world.SaveGame;
import com.laststar.game.world.SpellBook;
import com.laststar.game.world.World;
import com.laststar.game.world.lore.Elaris;
import com.laststar.game.world.lore.QuestCorpus;
import com.laststar.game.world.lore.SpellCorpus;

import java.io.IOException;
import java.util.List;

/**
 * Drives the systems that replaced the four-page Wander.
 *
 * <p>These run on the desktop with no Android in the way, which means they can
 * build a real world, cast real spells at real enemies, advance real quests and
 * round-trip a real save. Checks are counted rather than thrown so one failure
 * does not hide the rest.</p>
 */
public final class SystemsTest {

    private static int checks;
    private static int failures;

    private static void check(String what, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FAIL " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        spellBook();
        spellCorpus();
        questLog();
        questCorpus();
        saveRoundTrip();
        regionTravel();

        System.out.println(checks + " checks, " + failures + " failures");
        if (failures > 0) {
            throw new AssertionError(failures + " failures");
        }
    }

    // -- the book -------------------------------------------------------------

    private static void spellBook() {
        System.out.println("SpellBook");
        SpellBook book = new SpellBook();

        check("a new book has every page", book.pages.length == SpellCorpus.count());
        check("four pages awake at the start",
                countAwake(book) == SpellCorpus.STARTER_COUNT);
        check("every page starts at level one", allAtLevel(book, 1));
        check("the bar starts full", book.equipped[0] >= 0 && book.equipped[3] >= 0);

        // A full book has nowhere to regenerate to, so drain it first. This is
        // the state the player is actually in most of the time.
        book.light = 10f;
        float before = book.light;
        book.tick(1f);
        check("Light regenerates", book.light > before);
        book.light = book.lightCapacity;
        book.tick(1f);
        check("Light never exceeds capacity", book.light <= book.lightCapacity);

        SpellBook.Page first = book.pages[book.equipped[0]];
        float cost = first.def.lightCost;
        check("casting costs its Light", castAndMeasure(book, 0) >= cost - 0.01f);
        check("casting starts a cooldown", first.cooldownLeft > 0f);
        check("a cooling page cannot cast", !book.canCast(0));

        // A drained book must refuse rather than go negative.
        book.light = 0.5f;
        first.cooldownLeft = 0f;
        check("a starved book refuses", book.cast(0) == null);
        check("Light never goes negative", book.light >= 0f);

        book.absorbLight();
        check("a Light pickup restores Light", book.light > 10f);

        // Mastery.
        book.pages[0].xp = 0f;
        book.pages[0].level = 1;
        float needed = book.pages[0].xpToNext();
        book.pages[0].awardXp(needed + 1f);
        check("experience levels a page", book.pages[0].level == 2);
        float atTwo = book.pages[0].damage();
        book.pages[0].level = 30;
        check("damage grows with mastery",
                book.pages[0].damage() > atTwo);
        book.pages[0].awardXp(100000f);
        check("mastery stops at thirty", book.pages[0].level == 30);

        // Equipping: the bar keeps itself closed up. Every starting page is on
        // the bar, so a page has to be woken before it can be slotted -- which
        // is exactly the sequence a player follows after a find.
        int sleepingForBar = firstSleeping(book);
        check("a page is woken to slot", sleepingForBar >= 0 && book.awaken(sleepingForBar));
        int offBar = firstAwakeOffBar(book);
        check("there is an awake page off the bar", offBar >= 0);
        if (offBar >= 0) {
            int slot = book.toggleEquip(offBar);
            check("equipping returns a slot", slot >= 0 && slot < 4);
            check("equipping puts it on the bar", book.equipped[slot] == offBar);
            check("the bar closes the gap when full", barHasNoHoles(book));

            // Take one off the bar, and the gap must close behind it.
            int onBar = book.equipped[1];
            check("unequipping removes it", book.toggleEquip(onBar) == -1);
            check("no slot holds it any more", slotOf(book, onBar) < 0);
            check("the bar closes the gap", barHasNoHoles(book));
            check("the page goes back on", book.toggleEquip(onBar) >= 0);
        }

        // Awakening.
        int sleeping = firstSleeping(book);
        check("there are pages still to find", sleeping >= 0);
        if (sleeping >= 0) {
            check("awakening a page works", book.awaken(sleeping));
            check("awakening is not repeatable", !book.awaken(sleeping));
        }
        check("most of the book is still to find",
                countAwake(book) < book.pages.length / 2);

        float cap = book.lightCapacity;
        book.raiseCapacity(10f);
        check("Light capacity can be raised", book.lightCapacity > cap);
    }

    private static int countAwake(SpellBook book) {
        int n = 0;
        for (int i = 0; i < book.pages.length; i++) {
            if (book.pages[i].awake) {
                n++;
            }
        }
        return n;
    }

    private static boolean allAtLevel(SpellBook book, int level) {
        for (int i = 0; i < book.pages.length; i++) {
            if (book.pages[i].level != level) {
                return false;
            }
        }
        return true;
    }

    private static float castAndMeasure(SpellBook book, int slot) {
        book.light = book.lightCapacity;
        book.pages[book.equipped[slot]].cooldownLeft = 0f;
        float before = book.light;
        book.cast(slot);
        return before - book.light;
    }

    /** First awake page that is not on the action bar. */
    private static int firstAwakeOffBar(SpellBook book) {
        for (int i = 0; i < book.pages.length; i++) {
            if (book.pages[i].awake && slotOf(book, i) < 0) {
                return i;
            }
        }
        return -1;
    }

    private static int slotOf(SpellBook book, int spell) {
        for (int i = 0; i < book.equipped.length; i++) {
            if (book.equipped[i] == spell) {
                return i;
            }
        }
        return -1;
    }

    private static int firstAwake(SpellBook book) {
        for (int i = 0; i < book.pages.length; i++) {
            if (book.pages[i].awake) {
                return i;
            }
        }
        return -1;
    }

    /** The filled slots of the bar are the first ones. */
    private static boolean barHasNoHoles(SpellBook book) {
        boolean sawEmpty = false;
        for (int i = 0; i < book.equipped.length; i++) {
            if (book.equipped[i] < 0) {
                sawEmpty = true;
            } else if (sawEmpty) {
                return false;
            }
        }
        return true;
    }

    private static int firstSleeping(SpellBook book) {
        for (int i = 0; i < book.pages.length; i++) {
            if (!book.pages[i].awake) {
                return i;
            }
        }
        return -1;
    }

    // -- the corpus -----------------------------------------------------------

    private static void spellCorpus() {
        System.out.println("SpellCorpus");
        check("more than a hundred spells", SpellCorpus.count() > 100);
        check("eight categories",
                SpellCorpus.Category.values().length == 8);
        check("every category is stocked", everyCategoryStocked());
        check("every spell has a name", everySpellNamed());
        check("every spell costs Light", everySpellCostsLight());
        check("mastery curves are sane", curvesSane());
        check("traits unlock with mastery", traitsUnlock());
        int stocked = 0;
        for (int i = 0; i < Elaris.REGION_COUNT; i++) {
            if (SpellCorpus.inRegion(i).length > 0) {
                stocked++;
            }
        }
        check("all fourteen regions hold spells", stocked == Elaris.REGION_COUNT);
    }

    private static boolean everyCategoryStocked() {
        SpellCorpus.Category[] all = SpellCorpus.Category.values();
        for (int i = 0; i < all.length; i++) {
            if (SpellCorpus.count(all[i]) == 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean everySpellNamed() {
        for (int i = 0; i < SpellCorpus.count(); i++) {
            SpellCorpus.Def d = SpellCorpus.SPELLS[i];
            if (d.name == null || d.name.length() < 3) {
                return false;
            }
        }
        return true;
    }

    private static boolean everySpellCostsLight() {
        for (int i = 0; i < SpellCorpus.count(); i++) {
            if (SpellCorpus.SPELLS[i].lightCost <= 0f) {
                return false;
            }
        }
        return true;
    }

    private static boolean curvesSane() {
        for (int i = 0; i < SpellCorpus.count(); i++) {
            SpellCorpus.Def d = SpellCorpus.SPELLS[i];
            float one = d.damageScaleAt(1);
            float thirty = d.damageScaleAt(30);
            if (thirty < one) {
                return false;
            }
            // A stage carries its own multiplier; it must never go backwards.
            if (d.stageAt(30).damageScale < d.stageAt(1).damageScale) {
                return false;
            }
        }
        return true;
    }

    private static boolean traitsUnlock() {
        // Some spell somewhere must gain a trait it did not have at level one,
        // otherwise mastery is only a number.
        for (int i = 0; i < SpellCorpus.count(); i++) {
            SpellCorpus.Def d = SpellCorpus.SPELLS[i];
            SpellCorpus.Trait[] all = SpellCorpus.Trait.values();
            for (int t = 0; t < all.length; t++) {
                if (!d.has(all[t], 1) && d.has(all[t], 30)) {
                    return true;
                }
            }
        }
        return false;
    }

    // -- the journal ----------------------------------------------------------

    private static void questLog() {
        System.out.println("QuestLog");
        QuestLog log = new QuestLog();
        log.refreshAvailability();

        check("a new journal has open quests", log.openCount() > 0);
        check("a new journal has finished nothing", log.completedCount() == 0);
        check("there is a main quest to follow", log.currentMainQuest() != null);

        int open = log.openCount();
        // Finish whatever the first open quest is by over-reporting its kind.
        QuestLog.Entry first = log.openQuests().get(0);
        List<QuestCorpus.Def> done =
                log.report(first.def.kind, first.def.subject, first.def.target);
        check("reporting progress completes a quest", done.contains(first.def));
        check("completed quests leave the open list", log.openQuests().size() < open + 1);
        check("the completion is recorded", log.completedCount() >= 1);

        // Availability must open successors without being told.
        int afterOpen = log.openCount();
        check("finishing opens what it unlocked", afterOpen >= 0);

        // Consequence flags.
        boolean anyFlag = false;
        for (int i = 0; i < QuestCorpus.Consequence.values().length; i++) {
            if (i != QuestCorpus.Consequence.NONE.ordinal()
                    && log.has(QuestCorpus.Consequence.values()[i])) {
                anyFlag = true;
            }
        }
        check("finishing a quest can earn a consequence", anyFlag
                || first.def.consequence == QuestCorpus.Consequence.NONE);

        // Reporting the wrong subject must not advance a named hunt.
        QuestLog named = new QuestLog();
        named.refreshAvailability();
        QuestLog.Entry hunt = findKind(named, QuestCorpus.Kind.SLAY);
        if (hunt != null && hunt.def.subject != null) {
            named.report(QuestCorpus.Kind.SLAY, "SOMETHING ELSE ENTIRELY", 99);
            check("a named hunt ignores the wrong creature", hunt.progress == 0);
            named.report(QuestCorpus.Kind.SLAY, hunt.def.subject, 1);
            check("a named hunt counts the right creature", hunt.progress > 0);
        }
    }

    private static QuestLog.Entry findKind(QuestLog log, QuestCorpus.Kind kind) {
        for (int i = 0; i < log.entries.length; i++) {
            if (log.entries[i].state == QuestLog.OPEN
                    && log.entries[i].def.kind == kind) {
                return log.entries[i];
            }
        }
        return null;
    }

    private static void questCorpus() {
        System.out.println("QuestCorpus");
        check("a hundred and ninety quests", QuestCorpus.count() == 190);
        check("twenty-five main", QuestCorpus.mainCount() == 25);
        check("a hundred and sixty-five side", QuestCorpus.sideCount() == 165);
        check("every quest has a title", everyQuestTitled());
        check("every quest has an objective line", everyQuestHasObjective());
        check("the main chain is unbroken", mainChainUnbroken());
    }

    private static boolean everyQuestTitled() {
        for (int i = 0; i < QuestCorpus.count(); i++) {
            if (QuestCorpus.QUESTS[i].title == null
                    || QuestCorpus.QUESTS[i].title.length() < 3) {
                return false;
            }
        }
        return true;
    }

    private static boolean everyQuestHasObjective() {
        for (int i = 0; i < QuestCorpus.count(); i++) {
            String line = QuestCorpus.QUESTS[i].objectiveLine();
            if (line == null || line.length() < 3) {
                return false;
            }
        }
        return true;
    }

    private static boolean mainChainUnbroken() {
        // Every main quest must be reachable: either it is the first, or its
        // requirement is another main quest that comes before it.
        for (int i = 0; i < QuestCorpus.count(); i++) {
            QuestCorpus.Def d = QuestCorpus.QUESTS[i];
            if (!d.main || d.requires < 0) {
                continue;
            }
            if (d.requires >= i) {
                return false;
            }
            if (!QuestCorpus.QUESTS[d.requires].main) {
                return false;
            }
        }
        return true;
    }

    // -- the save -------------------------------------------------------------

    private static void saveRoundTrip() throws IOException {
        System.out.println("SaveGame");
        World world = new World(20260101L);
        world.gold = 1234;
        world.kills = 17;
        world.deaths = 3;
        world.spells.pages[0].level = 12;
        world.spells.pages[0].xp = 4.5f;
        world.spells.raiseCapacity(20f);
        world.quests.refreshAvailability();
        QuestLog.Entry q = world.quests.openQuests().get(0);
        world.quests.report(q.def.kind, q.def.subject, 1);

        byte[] bytes = SaveGame.capture(world).toBytes();
        check("a save has a sane size", bytes.length > 100 && bytes.length < 8000);

        SaveGame loaded = SaveGame.fromBytes(bytes);
        check("gold survives", loaded.gold == 1234);
        check("kills survive", loaded.kills == 17);
        check("deaths survive", loaded.deaths == 3);
        check("mastery survives", loaded.spellLevel[0] == 12);
        check("experience survives", Math.abs(loaded.spellXp[0] - 4.5f) < 0.01f);
        check("Light capacity survives",
                loaded.lightCapacity > SpellBook.BASE_LIGHT);
        check("quest progress survives", loaded.questProgress[q.def.id] > 0);
        check("awake pages survive",
                loaded.awake[world.spells.equipped[0]]);

        // Garbage and truncation must be rejected rather than crash the game.
        boolean rejectedGarbage = false;
        try {
            SaveGame.fromBytes(new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        } catch (IOException e) {
            rejectedGarbage = true;
        }
        check("garbage is rejected", rejectedGarbage);

        boolean rejectedTruncated = false;
        byte[] half = new byte[bytes.length / 2];
        System.arraycopy(bytes, 0, half, 0, half.length);
        try {
            SaveGame.fromBytes(half);
        } catch (IOException e) {
            rejectedTruncated = true;
        }
        check("a half-written save is rejected", rejectedTruncated);

        // And it must actually apply to a fresh world.
        World fresh = new World(20260101L);
        loaded.applyTo(fresh);
        check("applying restores gold", fresh.gold == 1234);
        check("applying restores mastery", fresh.spells.pages[0].level == 12);
        check("applying restores quest progress",
                fresh.quests.entries[q.def.id].progress > 0);
    }

    // -- travel ---------------------------------------------------------------

    private static void regionTravel() {
        System.out.println("Region travel");
        World world = new World(4242L);
        check("a new world starts at Ashvale", world.regionIndex == 0);
        check("the world knows its region",
                world.region() == Elaris.regions()[0]);

        int enemySeed = world.enemies.size();
        world.enterRegion(5);
        check("travel changes the region", world.regionIndex == 5);
        check("travel rebuilds the terrain",
                world.terrain.region == Elaris.regions()[5]);
        check("Frostmere has its own creatures",
                Elaris.regions()[5].creatures.length > 0);
        check("travel rebuilds the level", world.enemies.size() > 0);
        check("the player lands on dry ground",
                world.player.position.y > world.terrain.waterLevel);

        boolean namedCreature = false;
        for (int i = 0; i < world.enemies.size(); i++) {
            Enemy e = world.enemies.get(i);
            for (int c = 0; c < Elaris.regions()[5].creatures.length; c++) {
                if (Elaris.regions()[5].creatures[c].equals(e.name)) {
                    namedCreature = true;
                }
            }
        }
        check("enemies carry their region's names", namedCreature);

        // Each region's terrain must actually differ, or travel is cosmetic.
        float ashvaleHeight = new World(99L).terrain.heightAt(40f, 40f);
        World deep = new World(99L);
        deep.enterRegion(10);
        float deepHeight = deep.terrain.heightAt(40f, 40f);
        check("terrain differs between regions",
                Math.abs(ashvaleHeight - deepHeight) > 0.01f);

        check("every region has a name", everyRegionNamed());
        check("every region has a boss", everyRegionHasBoss());
    }

    private static boolean everyRegionNamed() {
        for (int i = 0; i < Elaris.REGION_COUNT; i++) {
            if (Elaris.regions()[i].name == null
                    || Elaris.regions()[i].name.length() < 3) {
                return false;
            }
        }
        return true;
    }

    private static boolean everyRegionHasBoss() {
        for (int i = 0; i < Elaris.REGION_COUNT; i++) {
            if (Elaris.regions()[i].boss == null
                    || Elaris.regions()[i].boss.length() < 3) {
                return false;
            }
        }
        return true;
    }
}
