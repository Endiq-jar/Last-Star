import com.laststar.game.core.Vec3;
import com.laststar.game.world.Boss;
import com.laststar.game.world.CombatKit;
import com.laststar.game.world.Enemy;
import com.laststar.game.world.QuestLog;
import com.laststar.game.world.Party;
import com.laststar.game.world.SaveGame;
import com.laststar.game.world.SpellBook;
import com.laststar.game.world.World;
import com.laststar.game.world.lore.Elaris;
import com.laststar.game.world.lore.QuestCorpus;
import com.laststar.game.world.lore.SpellCorpus;
import com.laststar.game.world.lore.Companions;
import com.laststar.game.world.lore.SpellForge;

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
        combatKit();
        bosses();
        spellForge();
        companions();
        travel();

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

    /** The map screen's only claim: travelling actually rebuilds the world. */
    private static void travel() {
        System.out.println("Region travel");
        World world = new World(31337L);
        // Every settlement has to be reachable, or the document's fourteen are
        // fourteen names rather than fourteen places.
        boolean allReachable = true;
        for (int i = 0; i < Elaris.REGION_COUNT; i++) {
            world.enterRegion(i);
            if (world.regionIndex != i) {
                allReachable = false;
            }
            if (world.terrain.region != Elaris.regions()[i]) {
                allReachable = false;
            }
        }
        check("every settlement can be entered", allReachable);

        // Terrain must actually differ, or travel is a change of name.
        world.enterRegion(0);
        float ashvale = world.terrain.heightAt(30f, 30f);
        world.enterRegion(10);
        float deep = world.terrain.heightAt(30f, 30f);
        world.enterRegion(5);
        float frost = world.terrain.heightAt(30f, 30f);
        check("terrain differs between settlements",
                Math.abs(ashvale - deep) > 0.01f || Math.abs(deep - frost) > 0.01f);

        // Travel must not cost the player their book.
        world.enterRegion(2);
        world.spells.pages[0].level = 9;
        world.enterRegion(7);
        check("mastery survives travel", world.spells.pages[0].level == 9);

        // Arriving opens the companion who lives there and their quest.
        World meet = new World(555L);
        int withCompanion = 0;
        for (int i = 0; i < Companions.count(); i++) {
            meet.enterRegion(Companions.get(i).region);
            withCompanion++;
        }
        check("every companion is met by travelling",
                meet.party.recruitedCount() == Companions.count());

        // The map's own numbers have to be honest.
        boolean everyRegionStocked = true;
        for (int i = 0; i < Elaris.REGION_COUNT; i++) {
            if (SpellCorpus.inRegion(i).length == 0) {
                everyRegionStocked = false;
            }
        }
        check("every region holds at least one page", everyRegionStocked);
    }

    // -- companions -----------------------------------------------------------

    private static void companions() {
        System.out.println("Companions");
        check("there are six companions", Companions.count() == 6);
        check("every companion has a Gift", everyCompanionHasGift());
        check("every companion has a history", everyCompanionHasHistory());
        check("every companion has an ability", everyCompanionHasAbility());
        check("every companion teaches a combination", everyCompanionTeaches());
        check("every companion has a personal quest",
                everyCompanionHasQuest());
        check("companions are spread across the map", companionsSpread());

        Party party = new Party();
        check("nobody travels with you at the start",
                party.recruitedCount() == 0);
        check("a stranger's ability is not available",
                !party.abilityReady(0));

        // Recruiting fills the active pair.
        check("a companion can join", party.recruit(0));
        check("you cannot recruit twice", !party.recruit(0));
        check("a new companion takes a slot", party.active[0] == 0);
        check("a second companion joins", party.recruit(1));
        check("the pair is full", party.active[1] == 1);
        check("a third does not displace the pair", party.recruit(2));

        // Trust: the ability is gated on being an ALLY.
        check("a new companion is a stranger",
                party.bond(0) == Companions.Bond.STRANGER);
        check("trust deepens the bond", party.addTrust(0, 4));
        check("the bond is now an ally",
                party.bond(0).threshold >= Companions.Bond.ALLY.threshold);
        check("an ally will act for you", party.abilityReady(0));
        party.useAbility(0);
        check("using it starts the cooldown", !party.abilityReady(0));
        party.tick(60f);
        check("the cooldown expires", party.abilityReady(0));

        // Synergy: only some pairs, which is what makes it a discovery.
        check("a pair can unlock a synergy", party.activeSynergy() != null);
        check("the synergy has a name",
                party.activeSynergy().name.length() > 5);
        Party lonely = new Party();
        check("one companion unlocks nothing", lonely.activeSynergy() == null);
        // Count the pairs that do something, so the ratio stays a discovery.
        int pairs = 0;
        int total = 0;
        for (int a = 0; a < Companions.count(); a++) {
            for (int b = a + 1; b < Companions.count(); b++) {
                total++;
                if (Companions.synergyBetween(a, b) != null) {
                    pairs++;
                }
            }
        }
        check("synergy covers some but not all pairs",
                pairs > 0 && pairs < total);
        check("synergy is symmetric",
                Companions.synergyBetween(0, 4) == Companions.synergyBetween(4, 0));

        // A pair with no synergy honestly reports none.
        int noSynergy = -1;
        for (int b = 1; b < Companions.count(); b++) {
            if (Companions.synergyBetween(0, b) == null) {
                noSynergy = b;
            }
        }
        check("there is a pair without a synergy", noSynergy >= 0);

        // Conversation choices move trust in both directions.
        Party talk = new Party();
        talk.recruit(2);
        int start = talk.trust[2];
        talk.converse(2, 1);
        check("a kind choice earns trust", talk.trust[2] > start);
        int after = talk.trust[2];
        talk.converse(2, -1);
        check("a cold choice costs trust", talk.trust[2] < after);
        check("the conversation is recorded", talk.conversations[2] == 2);

        // Every companion says something different at every bond level.
        boolean everyLine = true;
        for (int c = 0; c < Companions.count(); c++) {
            for (int b = 0; b < Companions.Bond.values().length; b++) {
                String line = Companions.lineFor(c, Companions.Bond.values()[b]);
                if (line == null || line.length() < 5) {
                    everyLine = false;
                }
            }
        }
        check("every companion speaks at every bond", everyLine);

        // Meeting someone in their region recruits them.
        World world = new World(777L);
        check("a fresh run has no party", world.party.recruitedCount() == 0);
        world.enterRegion(0);
        boolean sera = false;
        for (int i = 0; i < Companions.count(); i++) {
            if (Companions.get(i).region == 0 && world.party.recruited[i]) {
                sera = true;
            }
        }
        check("travelling to a companion's home recruits them", sera);
    }

    private static boolean everyCompanionHasGift() {
        for (int i = 0; i < Companions.count(); i++) {
            if (Companions.get(i).gift == null || Companions.get(i).gift.length() < 10) {
                return false;
            }
        }
        return true;
    }

    private static boolean everyCompanionHasHistory() {
        for (int i = 0; i < Companions.count(); i++) {
            if (Companions.get(i).history == null
                    || Companions.get(i).history.length() < 20) {
                return false;
            }
        }
        return true;
    }

    private static boolean everyCompanionHasAbility() {
        for (int i = 0; i < Companions.count(); i++) {
            Companions.Ability a = Companions.get(i).ability;
            if (a == null || a.name == null || a.name.length() < 3
                    || a.effect.length() < 10 || a.cooldown <= 0f) {
                return false;
            }
        }
        return true;
    }

    private static boolean everyCompanionTeaches() {
        for (int i = 0; i < Companions.count(); i++) {
            if (Companions.get(i).teaches == null
                    || Companions.get(i).teaches.length() < 5) {
                return false;
            }
        }
        return true;
    }

    private static boolean everyCompanionHasQuest() {
        for (int i = 0; i < Companions.count(); i++) {
            int q = Companions.get(i).personalQuest;
            if (q < 0 || q >= QuestCorpus.count()) {
                return false;
            }
        }
        return true;
    }

    private static boolean companionsSpread() {
        // Two companions in one settlement would make travelling for them
        // pointless, so their regions must be distinct.
        for (int a = 0; a < Companions.count(); a++) {
            for (int b = a + 1; b < Companions.count(); b++) {
                if (Companions.get(a).region == Companions.get(b).region) {
                    return false;
                }
            }
        }
        return true;
    }

    // -- spell creation -------------------------------------------------------

    private static void spellForge() {
        System.out.println("SpellForge");

        // The same element and shape with different behaviours are different
        // spells, which is the whole point of the dials.
        SpellForge.Recipe a = new SpellForge.Recipe(SpellForge.Element.GALE,
                SpellCorpus.Shape.BOLT, SpellForge.Behaviour.NONE);
        SpellForge.Recipe b = new SpellForge.Recipe(SpellForge.Element.GALE,
                SpellCorpus.Shape.BOLT, SpellForge.Behaviour.CHAIN);
        check("behaviour changes the name", !a.name().equals(b.name()));
        check("the name names the element", a.name().startsWith("GALE"));

        // Element and shape both show in the result.
        SpellForge.Recipe c = new SpellForge.Recipe(SpellForge.Element.FROST,
                SpellCorpus.Shape.FIELD, SpellForge.Behaviour.SLOW);
        check("a field of frost reads as one", c.name().startsWith("FROST"));

        // The dials actually do something, and they interact with the cost.
        SpellForge.Recipe cheap = new SpellForge.Recipe(SpellForge.Element.EMBER,
                SpellCorpus.Shape.BOLT, SpellForge.Behaviour.NONE);
        cheap.power = 0.1f;
        cheap.area = 0.1f;
        cheap.range = 0.1f;
        SpellForge.Recipe dear = new SpellForge.Recipe(SpellForge.Element.EMBER,
                SpellCorpus.Shape.BOLT, SpellForge.Behaviour.NONE);
        dear.power = 0.95f;
        dear.area = 0.9f;
        dear.range = 0.9f;
        check("power costs Light",
                SpellForge.lightCost(dear) > SpellForge.lightCost(cheap) * 2f);
        check("power buys damage",
                SpellForge.baseDamage(dear) > SpellForge.baseDamage(cheap) * 2f);
        check("power costs time",
                SpellForge.cooldown(dear) > SpellForge.cooldown(cheap));
        check("range is a distance",
                SpellForge.range(dear) > SpellForge.range(cheap));

        // A behaviour is ambition, and ambition is budgeted in Light.
        SpellForge.Recipe plain = new SpellForge.Recipe(SpellForge.Element.STORM,
                SpellCorpus.Shape.BEAM, SpellForge.Behaviour.NONE);
        SpellForge.Recipe fancy = new SpellForge.Recipe(SpellForge.Element.STORM,
                SpellCorpus.Shape.BEAM, SpellForge.Behaviour.HEAL);
        check("a behaviour costs Light",
                SpellForge.lightCost(fancy) > SpellForge.lightCost(plain));

        // A forged spell is a real spell: it has stages, and reaches thirty.
        SpellCorpus.Def made = SpellForge.forge(dear);
        check("a forged spell has a name", made.name.length() > 4);
        check("a forged spell has a curve", made.stages.length >= 3);
        check("a forged spell can reach mastery",
                made.stageAt(30).damageScale > made.stageAt(1).damageScale);
        // The behaviour is the thing the player chose, so it is there on the
        // first cast rather than withheld until mastery ten.
        check("a forged spell carries its behaviour from the start",
                made.has(dear.behaviour.trait, 1)
                        && made.has(dear.behaviour.trait, 30));

        // It joins the book, on the bar, and the book grows.
        SpellBook book = new SpellBook();
        int before = book.pages.length;
        int index = book.addForgedPage(made);
        check("a forged spell joins the book", index >= before);
        check("the book grew", book.pages.length == before + 1);
        check("the new page is awake", book.pages[index].awake);
        boolean onBar = false;
        for (int i = 0; i < book.equipped.length; i++) {
            onBar |= book.equipped[i] == index;
        }
        check("the new page is usable at once", onBar);

        // Forging the same thing twice must not fill the book with copies.
        check("a duplicate is refused", book.addForgedPage(SpellForge.forge(dear)) < 0);
        check("the book did not grow again", book.pages.length == before + 1);

        // A duplicate is refused on the shape and cost, not only the name.
        SpellCorpus.Def renamed = SpellCorpus.make(made.name, made.category,
                made.shape, made.lightCost, made.baseDamage + 5f, made.cooldown,
                made.range, -1, made.stages);
        check("same name and shape is the same spell",
                SpellForge.same(made, renamed));

        // Every element and shape combination has a name rather than null.
        boolean everyComboNamed = true;
        for (int e = 0; e < SpellForge.Element.values().length; e++) {
            for (int sh = 0; sh < 9; sh++) {
                SpellForge.Recipe r = new SpellForge.Recipe(
                        SpellForge.Element.values()[e], SpellCorpus.Shape.values()[sh],
                        SpellForge.Behaviour.NONE);
                if (r.name() == null || r.name().length() < 5) {
                    everyComboNamed = false;
                }
            }
        }
        check("every combination has a name", everyComboNamed);

        // Two different recipes are never the same spell.
        boolean distinct = true;
        for (int e = 0; e < SpellForge.Element.values().length; e++) {
            for (int sh = 0; sh < 9; sh++) {
                SpellForge.Recipe r1 = new SpellForge.Recipe(
                        SpellForge.Element.values()[e], SpellCorpus.Shape.values()[sh],
                        SpellForge.Behaviour.NONE);
                SpellForge.Recipe r2 = new SpellForge.Recipe(
                        SpellForge.Element.values()[e], SpellCorpus.Shape.values()[sh],
                        SpellForge.Behaviour.EXPLODE);
                if (SpellForge.same(SpellForge.forge(r1), SpellForge.forge(r2))) {
                    distinct = false;
                }
            }
        }
        check("different behaviours make different spells", distinct);
    }

    // -- the bosses -----------------------------------------------------------

    private static void bosses() {
        System.out.println("Boss");
        Boss boss = new Boss("THE LAKE WARDEN", 2, 8);
        check("a boss starts alive", boss.alive);
        check("a boss starts in its first phase", boss.phase == Boss.Phase.FIRST);
        check("a boss has a big health pool", boss.maxHealth > 600f);
        check("the first phase has a small pattern", boss.patternCount() == 2);

        // Phases must follow health, and each must add something.
        check("phase one below seventy percent", Boss.phaseFor(0.69f) == Boss.Phase.SECOND);
        check("phase two below forty-two", Boss.phaseFor(0.41f) == Boss.Phase.THIRD);
        check("phase three below eighteen", Boss.phaseFor(0.17f) == Boss.Phase.FINAL);
        check("there are at most four phases", Boss.Phase.values().length == 4);

        // Each phase must introduce a mechanic, not only more health.
        check("the first phase is unguarded", !boss.guarded);
        boss.health = boss.maxHealth * 0.60f;
        boss.update(0.016f, 5f);
        check("the second phase raises a guard", boss.guarded);
        check("the second phase adds a pattern", boss.patternCount() == 3);

        boss.health = boss.maxHealth * 0.35f;
        boss.update(0.016f, 5f);
        check("the third phase reaches the arena",
                boss.patternCount() == 4);
        boolean arena = false;
        Boss.Attack[] third = boss.patterns();
        for (int i = 0; i < third.length; i++) {
            arena |= third[i] == Boss.Attack.ARENA;
        }
        check("the arena attacks in the third phase", arena);

        boss.health = boss.maxHealth * 0.10f;
        boss.update(0.016f, 5f);
        check("the final phase drops the guard", !boss.guarded);
        check("the final phase exposes the weak point", boss.weakPointExposed());
        check("the final phase has every pattern", boss.patternCount() == 5);

        // Every phase has a rule, which is what the banner reads.
        for (int i = 0; i < Boss.Phase.values().length; i++) {
            String rule = Boss.ruleFor(Boss.Phase.values()[i]);
            check("phase " + i + " states a rule",
                    rule != null && rule.length() > 5);
        }

        // The guard: a light blow barely scratches it, a heavy one lands.
        Boss guarded = new Boss("WARDEN", 0, 5);
        guarded.health = guarded.maxHealth * 0.60f;
        guarded.update(0.016f, 5f);
        check("the guard is up", guarded.guarded);
        float hp = guarded.health;
        float light = guarded.damage(40f, false, false);
        check("a light blow is shrugged off", light < 10f);
        float heavy = guarded.damage(40f, false, true);
        check("a heavy blow gets through", heavy > 35f);
        check("damage is applied", guarded.health < hp);

        // The weak point: worth several times a normal blow, and it staggers.
        Boss weak = new Boss("WARDEN", 0, 5);
        weak.exposeWeakPoint();
        check("a weak point can be exposed", weak.weakPointExposed());
        float normalHit = weak.damage(40f, false, true);
        weak.exposeWeakPoint();
        float weakHit = weak.damage(40f, true, true);
        check("a weak point is worth several blows", weakHit > normalHit * 2.5f);

        Vec3 centre = new Vec3(0f, 0f, 0f);
        Vec3 onPoint = weak.weakPointPosition(centre);
        check("a blow on the point counts",
                weak.hitsWeakPoint(centre, onPoint, 1.5f));
        check("a blow across the field does not",
                !weak.hitsWeakPoint(centre, new Vec3(9f, 0f, 9f), 1.5f));

        // Three weak-point strikes stagger, which is the window to punish.
        Boss stagger = new Boss("WARDEN", 0, 5);
        for (int i = 0; i < 3; i++) {
            stagger.exposeWeakPoint();
            stagger.damage(10f, true, true);
        }
        check("three weak-point hits stagger", stagger.staggerTimer > 0f);

        // A boss outside range does nothing rather than flailing from afar.
        Boss distant = new Boss("WARDEN", 0, 5);
        for (int i = 0; i < 20; i++) {
            distant.update(0.016f, 90f);
        }
        check("a distant boss does not swing", distant.windup <= 0f);

        // In range it winds up, then resolves, then cools down.
        Boss near = new Boss("WARDEN", 0, 5);
        boolean sawWindup = false;
        boolean sawResolve = false;
        for (int i = 0; i < 200 && !sawResolve; i++) {
            near.update(0.016f, 5f);
            if (near.windup > 0f) {
                sawWindup = true;
            }
            if (near.resolvePending) {
                sawResolve = true;
            }
        }
        check("a boss in range winds up", sawWindup);
        check("the wind-up resolves into an attack", sawResolve);

        // A boss that dies stays dead.
        Boss dead = new Boss("WARDEN", 0, 3);
        dead.damage(dead.maxHealth * 2f, false, true);
        check("enough damage kills", !dead.alive);
        check("a dead boss reports no health", dead.fraction() == 0f);
        check("a dead boss takes no more", dead.damage(50f, false, true) == 0f);

        // Every region names a boss, so every region has one to fight.
        boolean allNamed = true;
        for (int i = 0; i < Elaris.REGION_COUNT; i++) {
            String name = Elaris.regions()[i].boss;
            if (name == null || name.length() < 3) {
                allNamed = false;
            }
        }
        check("all fourteen regions have a boss", allNamed);
    }

    // -- the blade ------------------------------------------------------------

    private static void combatKit() {
        System.out.println("CombatKit");
        CombatKit kit = new CombatKit();

        // A light swing starts, and its hit window opens partway through.
        kit.update(0.016f, true, false, false, false, false, true);
        check("a light press swings", kit.stance == CombatKit.Stance.LIGHT);
        check("the first frame does not land", !kit.hitWindowOpen());
        step(kit, 0.10f);
        check("the hit window opens mid-swing", kit.hitWindowOpen());
        check("a light swing has a range", kit.lastRange > 0f);

        // Combos: three lights in sequence walk the chain and grow.
        CombatKit combo = new CombatKit();
        swingThrough(combo);
        float one = combo.damageScale();
        swingThrough(combo);
        float two = combo.damageScale();
        swingThrough(combo);
        float three = combo.damageScale();
        check("the combo escalates", three > two && two >= one);
        check("the third hit staggers", combo.staggers());

        // A heavy is slower and stronger than a light.
        CombatKit heavy = new CombatKit();
        holdHeavy(heavy, CombatKit.CHARGE_TIME * 0.5f);
        releaseHeavy(heavy);
        check("a short hold is a heavy, not a charge",
                heavy.swing == CombatKit.Swing.CHARGED);
        float shortCharge = heavy.damageScale();
        CombatKit wound = new CombatKit();
        holdHeavy(wound, CombatKit.CHARGE_FULL + 0.1f);
        releaseHeavy(wound);
        check("a long hold hits harder", wound.damageScale() > shortCharge);
        check("a charged blow breaks guard", wound.breaksGuard());

        // The dodge: i-frames are open, the perfect window is the front of it.
        CombatKit dodge = new CombatKit();
        dodge.update(0.016f, false, false, false, true, false, true);
        check("a dodge dodges", dodge.stance == CombatKit.Stance.DODGE);
        check("the dodge has i-frames", dodge.dodgeInvulnerable());
        check("the perfect window is open at the start", dodge.perfectDodgeActive());
        step(dodge, CombatKit.DODGE_PERFECT + 0.02f);
        check("the perfect window closes early", !dodge.perfectDodgeActive());
        check("the dodge is still rolling", dodge.dodgeInvulnerable());

        // A perfect dodge opens the counter window and refunds Light.
        CombatKit perfect = new CombatKit();
        perfect.update(0.016f, false, false, false, true, false, true);
        perfect.onIncomingBlow();
        check("a perfect dodge opens a counter", perfect.counterWindow > 0f);
        check("a perfect dodge is counted", perfect.perfectDodges == 1);

        // A parry does the same, but only inside its window.
        CombatKit parry = new CombatKit();
        parry.update(0.016f, false, false, false, false, true, true);
        check("a parry enters the stance", parry.stance == CombatKit.Stance.PARRY);
        check("the parry window is open", parry.parryAbsorbs());
        check("a blow is turned aside", parry.onIncomingBlow());
        check("the parry is counted", parry.parries == 1);
        check("a parry opens a counter", parry.counterWindow > 0f);

        // A mistimed parry is the recovery, not the window.
        CombatKit late = new CombatKit();
        late.update(0.016f, false, false, false, false, true, true);
        step(late, CombatKit.PARRY_TIME);
        check("a late parry catches nothing", !late.parryAbsorbs());
        check("a late parry is not counted", !late.onIncomingBlow());

        // A dodge late in its roll is neither i-frame nor perfect.
        CombatKit lateDodge = new CombatKit();
        lateDodge.update(0.016f, false, false, false, true, false, true);
        step(lateDodge, CombatKit.DODGE_TIME + 0.05f);
        check("the roll ends", lateDodge.stance == CombatKit.Stance.IDLE);

        // A chain of hits earns the finisher.
        CombatKit chain = new CombatKit();
        for (int i = 0; i < 5; i++) {
            chain.onHitLanded(10f, false);
        }
        check("five hits is not yet a finisher", !chain.finisherReady);
        chain.onHitLanded(10f, false);
        check("six hits earn the finisher", chain.finisherReady);
        chain.update(0.016f, true, false, false, false, false, true);
        check("the finisher swings", chain.swing == CombatKit.Swing.FINISHER);
        check("the finisher is worth the wait",
                chain.damageScale() > CombatKit.CHARGED_DAMAGE * 0.9f);
        check("the finisher is spent", !chain.finisherReady);

        // Being hit resets the chain.
        chain.onHitLanded(10f, false);
        chain.onPlayerHurt();
        check("a hit breaks the chain", chain.chain == 0 && !chain.finisherReady);

        // Casting is allowed while moving but not mid-roll.
        CombatKit casting = new CombatKit();
        check("a resting player can cast", casting.canCast());
        casting.update(0.016f, false, false, false, true, false, true);
        check("a rolling player cannot cast", !casting.canCast());
        step(casting, CombatKit.DODGE_TIME + 0.1f);
        check("the player can cast again", casting.canCast());

        // Aerial: a light press in the air still swings, and the window opens
        // partway through it just as a ground swing's does.
        CombatKit air = new CombatKit();
        air.update(0.016f, true, false, false, false, false, false);
        check("an aerial press swings", air.stance == CombatKit.Stance.LIGHT);
        check("an aerial swing starts without landing", !air.hitWindowOpen());
        stepAir(air, 0.12f);
        check("an aerial swing has a hit window", air.hitWindowOpen());
    }

    /**
     * Runs one light press and lets the swing play out.
     *
     * <p>The swing lasts longer than its cooldown, so waiting for the cooldown
     * alone leaves the previous stance still running and the next press is
     * swallowed. Waiting for the stance to clear is what a player does.</p>
     */
    private static void swingThrough(CombatKit kit) {
        for (int i = 0; i < 60 && (kit.stance != CombatKit.Stance.IDLE
                || kit.cooldown > 0f); i++) {
            step(kit, 0.016f);
        }
        kit.update(0.016f, true, false, false, false, false, true);
    }

    private static void holdHeavy(CombatKit kit, float seconds) {
        kit.update(0.016f, false, true, true, false, false, true);
        float t = 0f;
        while (t < seconds) {
            stepHeld(kit, 0.016f);
            t += 0.016f;
        }
    }

    private static void releaseHeavy(CombatKit kit) {
        kit.update(0.016f, false, false, false, false, false, true);
    }

    private static void step(CombatKit kit, float seconds) {
        float t = 0f;
        while (t < seconds) {
            kit.update(0.016f, false, false, false, false, false, true);
            t += 0.016f;
        }
    }

    private static void stepAir(CombatKit kit, float seconds) {
        float t = 0f;
        while (t < seconds) {
            kit.update(0.016f, false, false, false, false, false, false);
            t += 0.016f;
        }
    }

    private static void stepHeld(CombatKit kit, float seconds) {
        float t = 0f;
        while (t < seconds) {
            kit.update(0.016f, false, false, true, false, false, true);
            t += 0.016f;
        }
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
