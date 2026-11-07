package com.laststar.game.world;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Durable, offline save storage. A flushed temporary file is renamed into place
 * and the previous good file is retained as a recovery copy. The former
 * SharedPreferences/Base64 slot is still read and migrated for existing users.
 */
public final class SaveFile {

    private static final String LEGACY_KEY = "save_v1";
    private static final String FILE_NAME = "laststar.save";
    private static final String TEMP_NAME = "laststar.save.tmp";
    private static final String BACKUP_NAME = "laststar.save.bak";
    private static final int MAX_SAVE_BYTES = 1024 * 1024;

    private final SharedPreferences prefs;
    private final File file;
    private final File temp;
    private final File backup;

    public SaveFile(Context context) {
        this.prefs = context.getSharedPreferences("laststar", Context.MODE_PRIVATE);
        File directory = context.getFilesDir();
        this.file = new File(directory, FILE_NAME);
        this.temp = new File(directory, TEMP_NAME);
        this.backup = new File(directory, BACKUP_NAME);
    }

    /** Writes a checksummed snapshot. Returns false without replacing a good save on failure. */
    public synchronized boolean save(World world) {
        byte[] bytes = SaveGame.capture(world).toBytes();
        if (!writeAtomically(bytes)) {
            return false;
        }
        prefs.edit().remove(LEGACY_KEY).commit();
        return true;
    }

    /** Whether a primary, recovery or pre-migration save is present. */
    public boolean exists() {
        return file.isFile() || backup.isFile() || prefs.contains(LEGACY_KEY);
    }

    /** Loads the newest valid save, falling back to recovery and legacy storage. */
    public synchronized boolean load(World world) {
        byte[] primary = readQuietly(file);
        if (apply(primary, world)) {
            return true;
        }

        byte[] recovery = readQuietly(backup);
        if (apply(recovery, world)) {
            writeAtomically(recovery);
            android.util.Log.w("LastStar", "recovered progress from backup save");
            return true;
        }

        String encoded = prefs.getString(LEGACY_KEY, null);
        if (encoded != null) {
            try {
                byte[] legacy = Base64.decode(encoded, Base64.NO_WRAP);
                SaveGame save = SaveGame.fromBytes(legacy);
                save.applyTo(world);
                // Migration is best effort: loading the old save must not fail
                // if the device temporarily refuses the new file write.
                writeAtomically(save.toBytes());
                return true;
            } catch (IOException | RuntimeException e) {
                android.util.Log.w("LastStar", "save rejected: " + e.getMessage());
            }
        }
        return false;
    }

    private static boolean apply(byte[] bytes, World world) {
        if (bytes == null) return false;
        try {
            SaveGame.fromBytes(bytes).applyTo(world);
            return true;
        } catch (IOException | RuntimeException e) {
            android.util.Log.w("LastStar", "save rejected: " + e.getMessage());
            return false;
        }
    }

    private byte[] readQuietly(File source) {
        if (!source.isFile() || source.length() <= 0 || source.length() > MAX_SAVE_BYTES) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(source);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
                if (out.size() > MAX_SAVE_BYTES) return null;
            }
            return out.toByteArray();
        } catch (IOException e) {
            android.util.Log.w("LastStar", "could not read save: " + e.getMessage());
            return null;
        }
    }

    private synchronized boolean writeAtomically(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_SAVE_BYTES) return false;
        File directory = file.getParentFile();
        if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) return false;

        try (FileOutputStream out = new FileOutputStream(temp, false)) {
            out.write(bytes);
            out.flush();
            out.getFD().sync();
        } catch (IOException e) {
            temp.delete();
            android.util.Log.e("LastStar", "could not write temporary save", e);
            return false;
        }

        boolean hadPrimary = file.isFile();
        if (backup.exists() && !backup.delete()) {
            temp.delete();
            return false;
        }
        if (hadPrimary && !file.renameTo(backup)) {
            temp.delete();
            android.util.Log.e("LastStar", "could not preserve previous save");
            return false;
        }
        if (!temp.renameTo(file)) {
            if (hadPrimary && backup.isFile()) backup.renameTo(file);
            temp.delete();
            android.util.Log.e("LastStar", "could not install new save");
            return false;
        }
        // Keep the backup until the new primary is in place. It is retained
        // after success as a one-generation recovery point.
        return true;
    }

    /** Forgets all save generations and the pre-migration entry. */
    public synchronized void clear() {
        temp.delete();
        file.delete();
        backup.delete();
        prefs.edit().remove(LEGACY_KEY).commit();
    }
}
