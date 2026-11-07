package com.laststar.game.core;

import com.laststar.game.world.lore.Elaris;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Sliding asynchronous 3x3 terrain window. Neighboring tiles sample one global
 * noise field, so shared edges have identical heights and normals. CPU terrain
 * generation runs on two daemon workers; GL mesh creation remains on the GL
 * thread. The current and overlapping tiles stay resident while leading tiles
 * load, avoiding a transition screen or a world-edge teleport.
 */
public final class TerrainChunkStreamer {

    public static final float CHUNK_SIZE = 220f;
    public static final int CHUNK_SEGMENTS = 110;
    /** The original first region's relief continues as a global field outside the start tile. */
    public static final float WORLD_SPAN = 220f;
    public static final int RADIUS = 1;
    private static final int WINDOW_SIZE = RADIUS * 2 + 1;

    private Elaris.Region region;
    private long seed;
    private static final java.util.concurrent.atomic.AtomicInteger WORKER_ID =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "laststar-terrain-" + WORKER_ID.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private final Map<Long, Terrain> loaded = new HashMap<>();
    private final Map<Long, Future<Terrain>> pending = new HashMap<>();
    private int centerX = Integer.MIN_VALUE;
    private int centerZ = Integer.MIN_VALUE;

    public TerrainChunkStreamer(Elaris.Region region, long seed) {
        reset(region, seed);
    }

    public synchronized void reset(Elaris.Region region, long seed) {
        for (Future<Terrain> f : pending.values()) f.cancel(true);
        pending.clear();
        loaded.clear();
        this.region = region;
        this.seed = seed;
        centerX = 0;
        centerZ = 0;
        // Synchronous center tile guarantees collision and a visible ground on
        // the first frame; the surrounding eight tiles stream in behind it.
        loaded.put(key(0, 0), generate(0, 0));
        scheduleWindow();
    }

    /** Updates the desired 3x3 window; generation is asynchronous. */
    public synchronized boolean update(float worldX, float worldZ) {
        collectCompleted();
        int cx = chunkCoord(worldX);
        int cz = chunkCoord(worldZ);
        if (cx == centerX && cz == centerZ) return false;
        centerX = cx;
        centerZ = cz;
        scheduleWindow();
        return true;
    }

    private void scheduleWindow() {
        Set<Long> desired = desiredKeys();
        for (long key : desired) {
            if (loaded.containsKey(key) || pending.containsKey(key)) continue;
            int x = (int) (key >> 32);
            int z = (int) key;
            final Elaris.Region targetRegion = region;
            final long targetSeed = seed;
            final int targetX = x;
            final int targetZ = z;
            pending.put(key, WORKERS.submit(new java.util.concurrent.Callable<Terrain>() {
                @Override public Terrain call() {
                    return Terrain.chunk(targetRegion, CHUNK_SIZE, CHUNK_SEGMENTS,
                            targetSeed, targetX, targetZ, WORLD_SPAN);
                }
            }));
        }
        Iterator<Map.Entry<Long, Future<Terrain>>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Future<Terrain>> entry = it.next();
            if (!desired.contains(entry.getKey())) {
                it.remove();
                entry.getValue().cancel(true);
            }
        }
        pruneOutside(desired);
    }

    private Set<Long> desiredKeys() {
        Set<Long> desired = new HashSet<>();
        for (int z = centerZ - RADIUS; z <= centerZ + RADIUS; z++) {
            for (int x = centerX - RADIUS; x <= centerX + RADIUS; x++) {
                desired.add(key(x, z));
            }
        }
        return desired;
    }

    private void collectCompleted() {
        Iterator<Map.Entry<Long, Future<Terrain>>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Future<Terrain>> entry = it.next();
            Future<Terrain> future = entry.getValue();
            if (!future.isDone()) continue;
            try {
                loaded.put(entry.getKey(), future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | java.util.concurrent.CancellationException e) {
                // A failed tile is retried the next time the window is scheduled.
            }
            it.remove();
        }
        if (pending.isEmpty()) pruneOutside(desiredKeys());
    }

    private void pruneOutside(Set<Long> desired) {
        if (!pending.isEmpty()) return; // Keep overlap until the new rim is ready.
        for (Long key : new ArrayList<>(loaded.keySet())) {
            if (!desired.contains(key)) loaded.remove(key);
        }
    }

    private Terrain generate(int x, int z) {
        return Terrain.chunk(region, CHUNK_SIZE, CHUNK_SEGMENTS, seed, x, z, WORLD_SPAN);
    }

    public synchronized float heightAt(float worldX, float worldZ) {
        return chunkAt(worldX, worldZ).heightAt(worldX, worldZ);
    }

    public synchronized float slopeAt(float worldX, float worldZ) {
        return chunkAt(worldX, worldZ).slopeAt(worldX, worldZ);
    }

    public synchronized Terrain chunkAt(float worldX, float worldZ) {
        collectCompleted();
        int x = chunkCoord(worldX);
        int z = chunkCoord(worldZ);
        long key = key(x, z);
        Terrain terrain = loaded.get(key);
        if (terrain != null) return terrain;
        Future<Terrain> future = pending.get(key);
        if (future != null) {
            try {
                terrain = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | java.util.concurrent.CancellationException e) {
                // Fall through to a direct build, preserving ground collision.
            }
        }
        if (terrain == null) terrain = generate(x, z);
        loaded.put(key, terrain);
        pending.remove(key);
        return terrain;
    }

    public synchronized Terrain center() {
        collectCompleted();
        Terrain terrain = loaded.get(key(centerX, centerZ));
        return terrain != null ? terrain : chunkAt(centerX * CHUNK_SIZE, centerZ * CHUNK_SIZE);
    }

    /** Snapshot of currently completed tiles in the desired 3x3 window. */
    public synchronized List<Terrain> loadedChunks() {
        collectCompleted();
        Set<Long> desired = desiredKeys();
        List<Terrain> result = new ArrayList<>(WINDOW_SIZE * WINDOW_SIZE);
        for (Long key : desired) {
            Terrain t = loaded.get(key);
            if (t != null) result.add(t);
        }
        return result;
    }

    /** Wait helper for deterministic tests and offline preview tools only. */
    public boolean awaitWindow(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            synchronized (this) {
                collectCompleted();
                if (loadedChunksReady()) return true;
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        synchronized (this) {
            collectCompleted();
            return loadedChunksReady();
        }
    }

    private boolean loadedChunksReady() {
        for (Long key : desiredKeys()) if (!loaded.containsKey(key)) return false;
        return true;
    }

    public synchronized int centerChunkX() { return centerX; }
    public synchronized int centerChunkZ() { return centerZ; }

    private static int chunkCoord(float world) {
        return (int) Math.floor((world + CHUNK_SIZE * 0.5f) / CHUNK_SIZE);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }
}
