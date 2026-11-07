package com.laststar.game.world;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.IOException;

/**
 * Where a save lives on the device.
 *
 * <p>The bytes are {@link SaveGame}'s business; this class only decides where
 * they go. They go in the same preferences file as the settings, base64
 * encoded, because a save is under eight kilobytes and a second file for that
 * would be more moving parts than it is worth.</p>
 */
public final class SaveFile {

    private static final String KEY = "save_v1";
    private final SharedPreferences prefs;

    public SaveFile(Context context) {
        this.prefs = context.getSharedPreferences("laststar", Context.MODE_PRIVATE);
    }

    /** Writes a snapshot of {@code world}. Returns false if storage refused. */
    public boolean save(World world) {
        String encoded = Base64.encodeToString(SaveGame.capture(world).toBytes(),
                Base64.NO_WRAP);
        return prefs.edit().putString(KEY, encoded).commit();
    }

    /** Whether there is a save to load. */
    public boolean exists() {
        return prefs.contains(KEY);
    }

    /**
     * Loads the save into {@code world}.
     *
     * <p>A missing or corrupt save is reported rather than thrown: a player who
     * launched the game should get a fresh run, not a crash.</p>
     */
    public boolean load(World world) {
        String encoded = prefs.getString(KEY, null);
        if (encoded == null) {
            return false;
        }
        try {
            SaveGame.fromBytes(Base64.decode(encoded, Base64.NO_WRAP)).applyTo(world);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            android.util.Log.w("LastStar", "save rejected: " + e.getMessage());
            return false;
        }
    }

    /** Forgets the save, for a new run. */
    public void clear() {
        prefs.edit().remove(KEY).commit();
    }
}
