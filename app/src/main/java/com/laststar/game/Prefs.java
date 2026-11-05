package com.laststar.game;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tiny persistence layer: player preferences plus best-run records.
 *
 * <p>Deliberately flat and dependency free so it can be exercised and reasoned
 * about easily. All writes are asynchronous commits, which is fine for a handful
 * of scalars.</p>
 */
public final class Prefs {

    private static final String FILE = "laststar";
    private static final String KEY_MUTED = "muted";
    private static final String KEY_LEFT_HANDED = "leftHanded";
    private static final String KEY_BEST_SHARDS = "bestShards";
    private static final String KEY_BEST_KILLS = "bestKills";
    private static final String KEY_RUNS = "runsCompleted";
    private static final String KEY_SEEN_HELP = "seenHelp";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isMuted() {
        return sp.getBoolean(KEY_MUTED, false);
    }

    public void setMuted(boolean muted) {
        sp.edit().putBoolean(KEY_MUTED, muted).apply();
    }

    public boolean isLeftHanded() {
        return sp.getBoolean(KEY_LEFT_HANDED, false);
    }

    public void setLeftHanded(boolean leftHanded) {
        sp.edit().putBoolean(KEY_LEFT_HANDED, leftHanded).apply();
    }

    public int bestShards() {
        return sp.getInt(KEY_BEST_SHARDS, 0);
    }

    public int bestKills() {
        return sp.getInt(KEY_BEST_KILLS, 0);
    }

    public int runsCompleted() {
        return sp.getInt(KEY_RUNS, 0);
    }

    public boolean hasSeenHelp() {
        return sp.getBoolean(KEY_SEEN_HELP, false);
    }

    public void markHelpSeen() {
        sp.edit().putBoolean(KEY_SEEN_HELP, true).apply();
    }

    /** Records the outcome of a finished run, keeping the best values. */
    public void recordRun(int shards, int kills, boolean victory) {
        SharedPreferences.Editor e = sp.edit();
        if (shards > bestShards()) {
            e.putInt(KEY_BEST_SHARDS, shards);
        }
        if (kills > bestKills()) {
            e.putInt(KEY_BEST_KILLS, kills);
        }
        if (victory) {
            e.putInt(KEY_RUNS, runsCompleted() + 1);
            e.putBoolean(KEY_SEEN_HELP, true);
        }
        e.apply();
    }
}
