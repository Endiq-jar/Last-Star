package com.laststar.game.world;

import com.laststar.game.world.lore.QuestCorpus;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's quest journal, and the thing that actually advances it.
 *
 * <p>Quests were previously a screen of hardcoded strings. This is the record
 * behind that screen: which quests are open, how far each one has got, which
 * have been finished, and which consequence flags the world has earned.</p>
 *
 * <p>Progress is reported by the world as things happen -- a kill, a shard, a
 * region reached -- and the log decides whether that satisfies an open quest.
 * Keeping the decision here rather than in {@code World} means the rule is one
 * function that the tests can drive directly.</p>
 */
public final class QuestLog {

    public static final int OPEN = 0;
    public static final int DONE = 1;
    public static final int LOCKED = 2;

    /** Per-quest runtime state, parallel to {@link QuestCorpus#QUESTS}. */
    public static final class Entry {
        public final QuestCorpus.Def def;
        public int progress;
        public int state = LOCKED;

        Entry(QuestCorpus.Def def) {
            this.def = def;
        }

        public boolean satisfied() {
            return progress >= def.target;
        }

        /** 0 to 1, for the journal's progress bar. */
        public float fraction() {
            if (def.target <= 0) {
                return 1f;
            }
            return Math.min(1f, (float) progress / def.target);
        }
    }

    public final Entry[] entries = new Entry[QuestCorpus.count()];

    /** Consequence flags earned so far, indexed by {@link QuestCorpus.Consequence}. */
    private final boolean[] flags =
            new boolean[QuestCorpus.Consequence.values().length];

    /** Quests finished since the log was created, for the reward toasts. */
    public final List<QuestCorpus.Def> justCompleted =
            new ArrayList<QuestCorpus.Def>();

    public QuestLog() {
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new Entry(QuestCorpus.QUESTS[i]);
        }
    }

    /**
     * Opens every quest whose prerequisite is met.
     *
     * <p>Called after each completion rather than every frame, because it walks
     * the whole corpus.</p>
     */
    public void refreshAvailability() {
        for (int i = 0; i < entries.length; i++) {
            Entry e = entries[i];
            if (e.state != LOCKED) {
                continue;
            }
            int req = e.def.requires;
            if (req < 0 || (req < entries.length && entries[req].state == DONE)) {
                e.state = OPEN;
            }
        }
    }

    /** Whether a consequence flag has been earned. */
    public boolean has(QuestCorpus.Consequence flag) {
        return flags[flag.ordinal()];
    }

    /**
     * Reports that the player did something.
     *
     * <p>Every open quest of the matching kind and subject advances. Returns the
     * quests that finished as a result, so the caller can pay out.</p>
     */
    public List<QuestCorpus.Def> report(QuestCorpus.Kind kind, String subject, int amount) {
        justCompleted.clear();
        for (int i = 0; i < entries.length; i++) {
            Entry e = entries[i];
            if (e.state != OPEN || e.def.kind != kind) {
                continue;
            }
            // A named hunt only counts its own creature; an unnamed one counts
            // anything of that kind.
            if (e.def.subject != null && subject != null
                    && requiresSubject(kind) && !e.def.subject.equals(subject)) {
                continue;
            }
            e.progress = Math.min(e.def.target, e.progress + amount);
            if (e.satisfied()) {
                complete(e);
            }
        }
        return justCompleted;
    }

    /** Reports one unit of a kind with no subject match. */
    public List<QuestCorpus.Def> report(QuestCorpus.Kind kind) {
        return report(kind, null, 1);
    }

    /** Subjects only narrow a hunt for the kinds where that makes sense. */
    private static boolean requiresSubject(QuestCorpus.Kind kind) {
        switch (kind) {
            case SLAY:
            case GATHER:
            case BOSS:
            case SPEAK:
            case TRAVEL:
            case CLEAR_HOLLOW:
                return true;
            default:
                return false;
        }
    }

    private void complete(Entry e) {
        if (e.state == DONE) {
            return;
        }
        e.state = DONE;
        if (e.def.consequence != QuestCorpus.Consequence.NONE) {
            flags[e.def.consequence.ordinal()] = true;
        }
        justCompleted.add(e.def);
        refreshAvailability();
    }

    /** Opens a quest directly, for story beats that are not gated on a turn-in. */
    public boolean open(int questId) {
        if (questId < 0 || questId >= entries.length) {
            return false;
        }
        if (entries[questId].state != LOCKED) {
            return false;
        }
        entries[questId].state = OPEN;
        return true;
    }

    /** Every open quest, newest first -- what the journal lists. */
    public List<Entry> openQuests() {
        List<Entry> out = new ArrayList<Entry>();
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].state == OPEN) {
                out.add(entries[i]);
            }
        }
        return out;
    }

    /** Every finished quest. */
    public List<Entry> completedQuests() {
        List<Entry> out = new ArrayList<Entry>();
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].state == DONE) {
                out.add(entries[i]);
            }
        }
        return out;
    }

    /** The main quest currently in play, or null. */
    public Entry currentMainQuest() {
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].def.main && entries[i].state == OPEN) {
                return entries[i];
            }
        }
        return null;
    }

    public int completedCount() {
        int n = 0;
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].state == DONE) {
                n++;
            }
        }
        return n;
    }

    public int openCount() {
        int n = 0;
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].state == OPEN) {
                n++;
            }
        }
        return n;
    }
}
