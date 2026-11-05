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
    private static final String KEY_VOLUME = "volume";
    private static final String KEY_HAPTICS = "haptics";
    private static final String KEY_SHAKE = "cameraShake";
    private static final String KEY_DIFFICULTY = "difficulty";
    private static final String KEY_SKIN = "skinTone";
    private static final String KEY_BODY = "body";
    private static final String KEY_NAME = "playerName";
    private static final String KEY_LAST_PAGE = "lastPage";

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

    public float volume() {
        return sp.getFloat(KEY_VOLUME, 0.70f);
    }

    public void setVolume(float volume) {
        float v = volume < 0f ? 0f : (volume > 1f ? 1f : volume);
        sp.edit().putFloat(KEY_VOLUME, v).apply();
    }

    public boolean haptics() {
        return sp.getBoolean(KEY_HAPTICS, false);
    }

    public void setHaptics(boolean haptics) {
        sp.edit().putBoolean(KEY_HAPTICS, haptics).apply();
    }

    public boolean cameraShake() {
        return sp.getBoolean(KEY_SHAKE, true);
    }

    public void setCameraShake(boolean shake) {
        sp.edit().putBoolean(KEY_SHAKE, shake).apply();
    }

    /** 0 = easy, 1 = normal, 2 = hard. */
    public int difficulty() {
        return sp.getInt(KEY_DIFFICULTY, 1);
    }

    public void setDifficulty(int difficulty) {
        int d = difficulty < 0 ? 0 : (difficulty > 2 ? 2 : difficulty);
        sp.edit().putInt(KEY_DIFFICULTY, d).apply();
    }

    public int skinTone() {
        return sp.getInt(KEY_SKIN, 3);
    }

    public void setSkinTone(int index) {
        sp.edit().putInt(KEY_SKIN, index).apply();
    }

    public int body() {
        return sp.getInt(KEY_BODY, 0);
    }

    public void setBody(int index) {
        sp.edit().putInt(KEY_BODY, index).apply();
    }

    public String playerName() {
        return sp.getString(KEY_NAME, "");
    }

    public void setPlayerName(String name) {
        sp.edit().putString(KEY_NAME, name).apply();
    }

    /** Remembers which menu page to reopen on the next launch. */
    public int lastPage() {
        return sp.getInt(KEY_LAST_PAGE, 0);
    }

    public void setLastPage(int page) {
        sp.edit().putInt(KEY_LAST_PAGE, page).apply();
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
