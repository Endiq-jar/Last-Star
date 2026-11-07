package com.laststar.game.gl;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * The game's imported props: eighty-odd hand-picked CC0 models, baked at build
 * time by {@code tools/bake_models.py} into a single little-endian buffer.
 *
 * <p>Everything the renderer needs is in the buffer: triangles, their per-vertex
 * colour (already art-directed to the game's anime palette), a role tag so the
 * scatterer can ask for "a tree" without knowing names, and the size the prop
 * should be scaled to in metres.</p>
 *
 * <p>Reading is one straight pass with no per-model allocation beyond its three
 * buffers, done on the GL thread during {@code onSurfaceCreated}: no background
 * loader, no JSON parser, no texture upload.</p>
 */
public final class ModelLibrary {

    /** Magic written by the baker: "LDMOD". */
    private static final int MAGIC = 0x4D4F444C;
    private static final int VERSION = 2;

    /** Roles, mirroring the ids in {@code tools/model_manifest.py}. */
    public static final int TREE = 0;
    public static final int ROCK = 1;
    public static final int PLANT = 2;
    public static final int GRASS = 3;
    public static final int FLOWER = 4;
    public static final int MUSHROOM = 5;
    public static final int LOG = 6;
    public static final int STUMP = 7;
    public static final int CLIFF = 8;
    public static final int PATH = 9;
    public static final int WALL = 10;
    public static final int TOWER = 11;
    public static final int ROOF = 12;
    public static final int GATE = 13;
    public static final int FLAG = 14;
    public static final int TENT = 15;
    public static final int BARREL = 16;
    public static final int CRATE = 17;
    public static final int FIRE = 18;
    public static final int BRIDGE = 19;
    public static final int HILL = 20;
    public static final int DOOR = 21;
    public static final int FENCE = 22;

    /**
     * The Wander: a single lore object, never part of a scatter pool.
     *
     * <p>It used to share {@link #PLANT}, which meant the ground-cover
     * scatterer treated it as a shrub and put ninety copies of the world's
     * most important book across the meadow. A role of its own keeps it out of
     * every pool by construction.</p>
     */
    public static final int WANDER = 23;

    public static final int ROLE_COUNT = 24;

    /** One imported prop. */
    public static final class Model {
        /** Index of this model inside its set, i.e. what {@link #pickIndex} returns. */
        public final int index;
        public final String name;
        public final int role;
        /** Largest-axis size in metres this prop should be scaled to. */
        public final float targetSize;
        /** Bounds relative to the origin, which sits on the ground, centred. */
        public final float minX, minY, minZ, maxX, maxY, maxZ;
        public final Mesh mesh;

        Model(int index, String name, int role, float targetSize,
              float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
              Mesh mesh) {
            this.index = index;
            this.name = name;
            this.role = role;
            this.targetSize = targetSize;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.mesh = mesh;
        }

        public float width() {
            return maxX - minX;
        }

        public float height() {
            return maxY - minY;
        }

        public float depth() {
            return maxZ - minZ;
        }

        /**
         * Largest extent of the model -- the dimension placements are authored
         * against. Using the largest rather than the height keeps flat props
         * honest: a 4 cm pebble path and a 10 m tower both land at a sensible
         * size instead of the flat one exploding to forty metres.
         */
        public float extent() {
            return Math.max(width(), Math.max(height(), depth()));
        }

        /** Uniform scale bringing the model's largest extent to {@code metres}. */
        public float scaleTo(float metres) {
            float e = extent();
            return e <= 0.0001f ? 1f : metres / e;
        }

        // --- Offscreen rendering helpers ---------------------------------------
        // The software rasteriser behind the docs screenshots consumes plain
        // arrays. The device never calls these, so the copies are lazy.

        private float[] posArray, nrmArray, colArray, wavArray;
        private short[] idxArray;

        public float[] positionArray() {
            if (posArray == null) {
                posArray = floats(mesh.positions, mesh.vertexCount * 3);
            }
            return posArray;
        }

        public float[] normalArray() {
            if (nrmArray == null) {
                nrmArray = floats(mesh.normals, mesh.vertexCount * 3);
            }
            return nrmArray;
        }

        public float[] colorArray() {
            if (colArray == null) {
                colArray = floats(mesh.colors, mesh.vertexCount * 4);
            }
            return colArray;
        }

        public float[] waveArray() {
            if (mesh.waves == null) {
                return null;
            }
            if (wavArray == null) {
                wavArray = new float[mesh.vertexCount];
                for (int i = 0; i < wavArray.length; i++) {
                    wavArray[i] = mesh.waves.get(i);
                }
            }
            return wavArray;
        }

        public short[] indexArray() {
            if (idxArray == null) {
                idxArray = new short[mesh.indexCount];
                for (int i = 0; i < idxArray.length; i++) {
                    idxArray[i] = mesh.indices.get(i);
                }
            }
            return idxArray;
        }

        private static float[] floats(FloatBuffer buf, int count) {
            float[] out = new float[count];
            for (int i = 0; i < count; i++) {
                out[i] = buf.get(i);
            }
            return out;
        }
    }

    private final Model[] models;
    private final int[][] byRole;

    private ModelLibrary(Model[] models) {
        this.models = models;
        int[] counts = new int[ROLE_COUNT];
        for (Model m : models) {
            if (m.role >= 0 && m.role < ROLE_COUNT) {
                counts[m.role]++;
            }
        }
        byRole = new int[ROLE_COUNT][];
        for (int r = 0; r < ROLE_COUNT; r++) {
            byRole[r] = new int[counts[r]];
        }
        int[] fill = new int[ROLE_COUNT];
        for (int i = 0; i < models.length; i++) {
            int r = models[i].role;
            if (r >= 0 && r < ROLE_COUNT) {
                byRole[r][fill[r]++] = i;
            }
        }
    }

    public int size() {
        return models.length;
    }

    public Model get(int i) {
        return models[i];
    }

    public int count(int role) {
        return (role >= 0 && role < ROLE_COUNT) ? byRole[role].length : 0;
    }

    /**
     * Deterministic index pick from a role, so the same seed always dresses the
     * same world. Returns -1 when the set has no model for that role.
     */
    public int pickIndex(int role, int seed) {
        if (role < 0 || role >= ROLE_COUNT) {
            return -1;
        }
        int[] pool = byRole[role];
        if (pool.length == 0) {
            return -1;
        }
        int h = seed * 1103515245 + 12345;
        h ^= (h >>> 16);
        return pool[(h & 0x7FFFFFFF) % pool.length];
    }

    /** As {@link #pickIndex}, returning the model or null. */
    public Model pick(int role, int seed) {
        int i = pickIndex(role, seed);
        return i < 0 ? null : models[i];
    }

    /** Total triangles, for the diagnostics overlay and the tests. */
    public int triangleCount() {
        int n = 0;
        for (Model m : models) {
            n += m.mesh.indexCount / 3;
        }
        return n;
    }

    /**
     * Parses the baked set.
     *
     * @throws IOException when the asset is missing or the header does not match
     */
    public static ModelLibrary load(InputStream in) throws IOException {
        if (in == null) {
            throw new IOException("model set asset missing");
        }
        ByteBuffer buf = readFully(in);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int magic = buf.getInt();
        int version = buf.getInt();
        int count = buf.getInt();
        buf.getInt(); // reserved
        if (magic != MAGIC) {
            throw new IOException("bad model magic: 0x" + Integer.toHexString(magic));
        }
        if (version != VERSION) {
            throw new IOException("model set version " + version + ", expected " + VERSION);
        }
        if (count < 0 || count > 4096) {
            throw new IOException("implausible model count: " + count);
        }

        Model[] models = new Model[count];
        byte[] nameBytes = new byte[256];
        for (int i = 0; i < count; i++) {
            int nameLen = buf.getInt();
            if (nameLen < 0 || nameLen > nameBytes.length) {
                throw new IOException("bad model name length: " + nameLen);
            }
            buf.get(nameBytes, 0, nameLen);
            int padded = (nameLen + 3) & ~3;
            if (padded > nameLen) {
                buf.position(buf.position() + (padded - nameLen));
            }
            String name = new String(nameBytes, 0, nameLen);

            int vertexCount = buf.getInt();
            int indexCount = buf.getInt();
            int role = buf.getInt();
            buf.getInt(); // reserved
            float targetSize = buf.getFloat();
            float minX = buf.getFloat();
            float minY = buf.getFloat();
            float minZ = buf.getFloat();
            float maxX = buf.getFloat();
            float maxY = buf.getFloat();
            float maxZ = buf.getFloat();

            // The bake interleaves nine floats per vertex: position, normal,
            // then an RGB colour. Read them in one pass, then derive the wind
            // phase from the heights that pass just measured -- the root of a
            // blade or a trunk stays planted while the tip bends.
            FloatBuffer positions = direct(vertexCount * 12).asFloatBuffer();
            FloatBuffer normals = direct(vertexCount * 12).asFloatBuffer();
            FloatBuffer colors = direct(vertexCount * 16).asFloatBuffer();
            for (int v = 0; v < vertexCount; v++) {
                positions.put(buf.getFloat()).put(buf.getFloat()).put(buf.getFloat());
                normals.put(buf.getFloat()).put(buf.getFloat()).put(buf.getFloat());
                colors.put(buf.getFloat()).put(buf.getFloat()).put(buf.getFloat()).put(1f);
            }
            positions.position(0);
            normals.position(0);
            colors.position(0);

            FloatBuffer waves = direct(vertexCount * 4).asFloatBuffer();
            float span = Math.max(0.0001f, maxY - minY);
            float topWeight = role == GRASS || role == FLOWER || role == PLANT
                    || role == MUSHROOM ? 1f : (role == TREE ? 0.22f : 0f);
            for (int v = 0; v < vertexCount; v++) {
                float h = (positions.get(v * 3 + 1) - minY) / span;
                // Only the upper part of the mesh moves, and it moves together:
                // one phase per model keeps the tuft coherent.
                waves.put(topWeight * h * h);
            }
            waves.position(0);

            ShortBuffer indices = direct(indexCount * 2).asShortBuffer();
            for (int v = 0; v < indexCount; v++) {
                indices.put(buf.getShort());
            }
            indices.position(0);

            Mesh mesh = Mesh.fromBuffers(positions, normals, colors, waves,
                    indices, vertexCount, indexCount);
            models[i] = new Model(i, name, role, targetSize,
                    minX, minY, minZ, maxX, maxY, maxZ, mesh);
        }
        return new ModelLibrary(models);
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
    }

    private static ByteBuffer readFully(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(1200000);
        byte[] chunk = new byte[16384];
        int n;
        try {
            while ((n = in.read(chunk)) > 0) {
                out.write(chunk, 0, n);
            }
        } catch (EOFException e) {
            // Fall through with what we have; the header check will complain.
        } finally {
            in.close();
        }
        byte[] bytes = out.toByteArray();
        ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length);
        direct.put(bytes);
        direct.position(0);
        return direct;
    }
}
