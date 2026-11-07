package com.laststar.game.art;

import com.laststar.game.core.Mat4;
import com.laststar.game.gl.MeshData;

import java.io.IOException;
import java.io.Reader;
import java.util.Scanner;

/**
 * Reader for the versioned, offline-compiled LSSKIN interchange format.
 * It contains a parent-indexed skeleton, inverse bind matrices, four normalized
 * influences per vertex and sampled local-matrix animation keys. Importers can
 * convert glTF/FBX exports to this compact runtime format without Android or
 * graphics dependencies.
 */
public final class SkinnedAssetLoader {

    private SkinnedAssetLoader() { }

    public static SkinnedAsset load(Reader source) throws IOException {
        Scanner in = new Scanner(source);
        in.useLocale(java.util.Locale.US);
        try {
            expect(in, "LSSKIN");
            int version = integer(in);
            if (version != 1) throw new IOException("unsupported LSSKIN version " + version);
            expect(in, "bones");
            int boneCount = bounded(integer(in), 1, 128, "bone count");
            String[] names = new String[boneCount];
            int[] parents = new int[boneCount];
            Mat4[] inverseBind = new Mat4[boneCount];
            for (int i = 0; i < boneCount; i++) {
                expect(in, "bone");
                names[i] = token(in, "bone name");
                parents[i] = integer(in);
                if (parents[i] < -1 || parents[i] >= i) {
                    throw new IOException("bone parent must precede its child");
                }
                inverseBind[i] = matrix(in);
            }

            expect(in, "vertices");
            int vertexCount = bounded(integer(in), 1, 65535, "vertex count");
            MeshData mesh = new MeshData();
            mesh.positions = new float[vertexCount * 3];
            mesh.normals = new float[vertexCount * 3];
            mesh.colors = new float[vertexCount * 4];
            mesh.waves = null;
            int[] joints = new int[vertexCount * 4];
            float[] weights = new float[vertexCount * 4];
            for (int v = 0; v < vertexCount; v++) {
                for (int i = 0; i < 3; i++) mesh.positions[v * 3 + i] = decimal(in);
                for (int i = 0; i < 3; i++) mesh.normals[v * 3 + i] = decimal(in);
                for (int i = 0; i < 4; i++) mesh.colors[v * 4 + i] = decimal(in);
                float sum = 0f;
                for (int i = 0; i < 4; i++) {
                    int at = v * 4 + i;
                    joints[at] = integer(in);
                    weights[at] = decimal(in);
                    if (joints[at] < 0 || joints[at] >= boneCount || weights[at] < 0f) {
                        throw new IOException("invalid skin influence at vertex " + v);
                    }
                    sum += weights[at];
                }
                if (!(sum > 1e-7f) || Float.isNaN(sum) || Float.isInfinite(sum)) {
                    throw new IOException("vertex has no valid skin weights: " + v);
                }
                for (int i = 0; i < 4; i++) weights[v * 4 + i] /= sum;
            }

            expect(in, "indices");
            int indexCount = bounded(integer(in), 3, 3 * 65535, "index count");
            if (indexCount % 3 != 0) throw new IOException("indices must form triangles");
            mesh.indices = new short[indexCount];
            for (int i = 0; i < indexCount; i++) {
                int index = integer(in);
                if (index < 0 || index >= vertexCount) throw new IOException("index out of range");
                mesh.indices[i] = (short) index;
            }

            expect(in, "clips");
            int clipCount = bounded(integer(in), 0, 256, "clip count");
            SkinnedAsset.Clip[] clips = new SkinnedAsset.Clip[clipCount];
            for (int c = 0; c < clipCount; c++) {
                expect(in, "clip");
                String name = token(in, "clip name");
                float duration = decimal(in);
                if (duration <= 0f) throw new IOException("clip duration must be positive");
                int frames = bounded(integer(in), 1, 10000, "frame count");
                float[] times = new float[frames];
                Mat4[][] local = new Mat4[frames][boneCount];
                for (int f = 0; f < frames; f++) {
                    expect(in, "frame");
                    times[f] = decimal(in);
                    if (times[f] < 0f || times[f] > duration
                            || (f > 0 && times[f] <= times[f - 1])) {
                        throw new IOException("animation frame times must increase within clip duration");
                    }
                    for (int b = 0; b < boneCount; b++) local[f][b] = matrix(in);
                }
                clips[c] = new SkinnedAsset.Clip(name, duration, times, local);
            }
            if (in.hasNext()) throw new IOException("unexpected trailing LSSKIN data");
            return new SkinnedAsset(names, parents, inverseBind, mesh, joints, weights, clips);
        } catch (java.util.NoSuchElementException e) {
            throw new IOException("malformed or truncated LSSKIN data", e);
        } finally {
            in.close();
        }
    }

    private static Mat4 matrix(Scanner in) throws IOException {
        Mat4 result = new Mat4();
        for (int i = 0; i < 16; i++) result.m[i] = decimal(in);
        return result;
    }

    private static void expect(Scanner in, String expected) throws IOException {
        String actual = token(in, expected);
        if (!expected.equals(actual)) throw new IOException("expected " + expected + ", got " + actual);
    }

    private static String token(Scanner in, String what) throws IOException {
        if (!in.hasNext()) throw new IOException("missing " + what);
        return in.next();
    }

    private static int integer(Scanner in) throws IOException {
        if (!in.hasNextInt()) throw new IOException("expected integer");
        return in.nextInt();
    }

    private static float decimal(Scanner in) throws IOException {
        if (!in.hasNextFloat()) throw new IOException("expected float");
        float value = in.nextFloat();
        if (Float.isNaN(value) || Float.isInfinite(value)) throw new IOException("non-finite float");
        return value;
    }

    private static int bounded(int value, int min, int max, String name) throws IOException {
        if (value < min || value > max) throw new IOException("invalid " + name + ": " + value);
        return value;
    }
}
