package com.laststar.game.art;

import com.laststar.game.core.Mat4;

/** CPU reference skinning for up to four weighted joints per vertex. */
public final class Skinning {
    private Skinning() { }

    /**
     * Applies column-major joint matrices to positions and normals. Each vertex
     * has four integer joint indices and four non-negative weights. The weights
     * are normalized per vertex, making imported data robust to quantization.
     */
    public static void skin(float[] positions, float[] normals, int[] joints, float[] weights,
                            Mat4[] jointMatrices, float[] outPositions, float[] outNormals) {
        int vertices = positions.length / 3;
        if (normals.length != positions.length || joints.length != vertices * 4
                || weights.length != vertices * 4 || outPositions.length != positions.length
                || outNormals.length != normals.length) {
            throw new IllegalArgumentException("inconsistent skinned vertex arrays");
        }
        for (int v = 0; v < vertices; v++) {
            int p = v * 3;
            int influence = v * 4;
            float sum = 0f;
            for (int k = 0; k < 4; k++) {
                float w = weights[influence + k];
                int j = joints[influence + k];
                if (w < 0f || Float.isNaN(w) || Float.isInfinite(w)
                        || j < 0 || j >= jointMatrices.length || jointMatrices[j] == null) {
                    throw new IllegalArgumentException("invalid skin influence at vertex " + v);
                }
                sum += w;
            }
            if (sum <= 1e-8f) {
                throw new IllegalArgumentException("vertex has no active skin weights: " + v);
            }
            float x = positions[p], y = positions[p + 1], z = positions[p + 2];
            float nx = normals[p], ny = normals[p + 1], nz = normals[p + 2];
            float ox = 0f, oy = 0f, oz = 0f;
            float onx = 0f, ony = 0f, onz = 0f;
            for (int k = 0; k < 4; k++) {
                float w = weights[influence + k] / sum;
                if (w == 0f) continue;
                float[] m = jointMatrices[joints[influence + k]].m;
                ox += w * (m[0] * x + m[4] * y + m[8] * z + m[12]);
                oy += w * (m[1] * x + m[5] * y + m[9] * z + m[13]);
                oz += w * (m[2] * x + m[6] * y + m[10] * z + m[14]);
                onx += w * (m[0] * nx + m[4] * ny + m[8] * nz);
                ony += w * (m[1] * nx + m[5] * ny + m[9] * nz);
                onz += w * (m[2] * nx + m[6] * ny + m[10] * nz);
            }
            float n = (float) Math.sqrt(onx * onx + ony * ony + onz * onz);
            if (n > 1e-8f) { onx /= n; ony /= n; onz /= n; }
            outPositions[p] = ox; outPositions[p + 1] = oy; outPositions[p + 2] = oz;
            outNormals[p] = onx; outNormals[p + 1] = ony; outNormals[p + 2] = onz;
        }
    }
}
