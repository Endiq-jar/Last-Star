package com.laststar.game.gl;

import com.laststar.game.core.Mat4;
import com.laststar.game.core.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * CPU-side geometry: plain arrays plus a tiny builder. Deliberately free of
 * Android types so all geometry generation can be unit tested on the JVM.
 */
public final class MeshData {

    public float[] positions;
    public float[] normals;
    public float[] colors;
    public float[] waves;
    public short[] indices;

    public int vertexCount() {
        return positions.length / 3;
    }

    public int triangleCount() {
        return indices.length / 3;
    }

    /** Accumulates vertices/indices and can apply a matrix while doing so. */
    public static final class Builder {
        private final List<float[]> pos = new ArrayList<>();
        private final List<float[]> nrm = new ArrayList<>();
        private final List<float[]> col = new ArrayList<>();
        private final List<Float> wav = new ArrayList<>();
        private final List<Short> idx = new ArrayList<>();
        private boolean anyNormals;
        private boolean anyWaves;

        public int vertexCount() {
            return pos.size();
        }

        public int addVertex(float x, float y, float z, float nx, float ny, float nz,
                             float r, float g, float b, float a, float wave) {
            float[] p = {x, y, z};
            float[] n = {nx, ny, nz};
            float[] c = {r, g, b, a};
            pos.add(p);
            nrm.add(n);
            col.add(c);
            wav.add(wave);
            anyNormals = true;
            if (wave != 0f) {
                anyWaves = true;
            }
            return pos.size() - 1;
        }

        public int addVertex(Vec3 p, Vec3 n, float r, float g, float b, float a, float wave) {
            return addVertex(p.x, p.y, p.z, n.x, n.y, n.z, r, g, b, a, wave);
        }

        public void addTriangle(int a, int b, int c) {
            idx.add((short) a);
            idx.add((short) b);
            idx.add((short) c);
        }

        public void addQuad(int a, int b, int c, int d) {
            addTriangle(a, b, c);
            addTriangle(a, c, d);
        }

        public MeshData build() {
            int n = pos.size();
            MeshData m = new MeshData();
            m.positions = new float[n * 3];
            m.normals = anyNormals ? new float[n * 3] : null;
            m.colors = new float[n * 4];
            m.waves = anyWaves ? new float[n] : null;
            for (int i = 0; i < n; i++) {
                float[] p = pos.get(i);
                m.positions[i * 3] = p[0];
                m.positions[i * 3 + 1] = p[1];
                m.positions[i * 3 + 2] = p[2];
                if (m.normals != null) {
                    float[] nn = nrm.get(i);
                    m.normals[i * 3] = nn[0];
                    m.normals[i * 3 + 1] = nn[1];
                    m.normals[i * 3 + 2] = nn[2];
                }
                float[] c = col.get(i);
                m.colors[i * 4] = c[0];
                m.colors[i * 4 + 1] = c[1];
                m.colors[i * 4 + 2] = c[2];
                m.colors[i * 4 + 3] = c[3];
                if (m.waves != null) {
                    m.waves[i] = wav.get(i);
                }
            }
            m.indices = new short[idx.size()];
            for (int i = 0; i < idx.size(); i++) {
                m.indices[i] = idx.get(i);
            }
            return m;
        }
    }

    /**
     * Returns a copy with every vertex transformed by {@code m} (and normals by
     * its rotation part, which for our rigid+uniform-scale matrices is the same
     * basis).
     */
    public MeshData transformed(Mat4 m) {
        MeshData out = new MeshData();
        out.positions = new float[positions.length];
        out.normals = normals == null ? null : new float[normals.length];
        out.colors = colors.clone();
        out.waves = waves == null ? null : waves.clone();
        out.indices = indices.clone();
        for (int i = 0; i < positions.length; i += 3) {
            Vec3 p = new Vec3(positions[i], positions[i + 1], positions[i + 2]);
            Vec3 t = m.transformPoint(p);
            out.positions[i] = t.x;
            out.positions[i + 1] = t.y;
            out.positions[i + 2] = t.z;
            if (out.normals != null) {
                float nx = normals[i], ny = normals[i + 1], nz = normals[i + 2];
                Vec3 n = new Vec3(
                        m.m[0] * nx + m.m[4] * ny + m.m[8] * nz,
                        m.m[1] * nx + m.m[5] * ny + m.m[9] * nz,
                        m.m[2] * nx + m.m[6] * ny + m.m[10] * nz).normalized();
                out.normals[i] = n.x;
                out.normals[i + 1] = n.y;
                out.normals[i + 2] = n.z;
            }
        }
        return out;
    }

    /** Concatenates several meshes, re-basing indices. */
    public static MeshData merge(MeshData... parts) {
        Builder b = new Builder();
        for (MeshData p : parts) {
            if (p == null) {
                continue;
            }
            int base = b.vertexCount();
            for (int i = 0; i < p.positions.length; i += 3) {
                int v = i / 3;
                float nx = p.normals != null ? p.normals[i] : 0f;
                float ny = p.normals != null ? p.normals[i + 1] : 1f;
                float nz = p.normals != null ? p.normals[i + 2] : 0f;
                b.addVertex(p.positions[i], p.positions[i + 1], p.positions[i + 2],
                        nx, ny, nz,
                        p.colors[v * 4], p.colors[v * 4 + 1], p.colors[v * 4 + 2], p.colors[v * 4 + 3],
                        p.waves != null ? p.waves[v] : 0f);
            }
            for (short s : p.indices) {
                b.idx.add((short) (s + base));
            }
        }
        return b.build();
    }

    public MeshData copy() {
        MeshData m = new MeshData();
        m.positions = positions.clone();
        m.normals = normals == null ? null : normals.clone();
        m.colors = colors.clone();
        m.waves = waves == null ? null : waves.clone();
        m.indices = indices.clone();
        return m;
    }

    /** Tints every vertex, multiplied with the existing colour. */
    public MeshData tinted(float r, float g, float b) {
        MeshData m = copy();
        for (int i = 0; i < m.colors.length; i += 4) {
            m.colors[i] *= r;
            m.colors[i + 1] *= g;
            m.colors[i + 2] *= b;
        }
        return m;
    }
}
