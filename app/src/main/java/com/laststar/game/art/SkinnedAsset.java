package com.laststar.game.art;

import com.laststar.game.core.Mat4;
import com.laststar.game.gl.MeshData;

/** Imported weighted mesh, bind skeleton and matrix-keyed animation clips. */
public final class SkinnedAsset {

    public static final class Clip {
        public final String name;
        public final float duration;
        public final float[] times;
        /** Local bone matrices, frame-major. */
        public final Mat4[][] localFrames;

        Clip(String name, float duration, float[] times, Mat4[][] localFrames) {
            this.name = name;
            this.duration = duration;
            this.times = times;
            this.localFrames = localFrames;
        }

        /** Samples hierarchical skin matrices, blending between neighboring keys. */
        public Mat4[] sample(float time, int[] parents, Mat4[] inverseBind) {
            int n = inverseBind.length;
            float t = duration <= 0f ? 0f : time % duration;
            if (t < 0f) t += duration;
            int lo = 0;
            while (lo + 1 < times.length && times[lo + 1] < t) lo++;
            int hi = Math.min(lo + 1, times.length - 1);
            float span = times[hi] - times[lo];
            float blend = span <= 1e-6f ? 0f : (t - times[lo]) / span;
            Mat4[] global = new Mat4[n];
            Mat4[] skin = new Mat4[n];
            for (int bone = 0; bone < n; bone++) {
                Mat4 local = new Mat4();
                float[] a = localFrames[lo][bone].m;
                float[] b = localFrames[hi][bone].m;
                for (int k = 0; k < 16; k++) local.m[k] = a[k] + (b[k] - a[k]) * blend;
                int parent = parents[bone];
                global[bone] = parent < 0 ? local : new Mat4().setMultiply(global[parent], local);
                skin[bone] = new Mat4().setMultiply(global[bone], inverseBind[bone]);
            }
            return skin;
        }
    }

    public final String[] boneNames;
    public final int[] parents;
    public final Mat4[] inverseBind;
    public final MeshData mesh;
    /** Four joint indices and weights per vertex. */
    public final int[] joints;
    public final float[] weights;
    public final Clip[] clips;

    SkinnedAsset(String[] boneNames, int[] parents, Mat4[] inverseBind,
                 MeshData mesh, int[] joints, float[] weights, Clip[] clips) {
        this.boneNames = boneNames;
        this.parents = parents;
        this.inverseBind = inverseBind;
        this.mesh = mesh;
        this.joints = joints;
        this.weights = weights;
        this.clips = clips;
    }

    public Clip clip(String name) {
        for (Clip clip : clips) if (clip.name.equals(name)) return clip;
        return null;
    }
}
