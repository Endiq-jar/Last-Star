package com.laststar.game.art;

import com.laststar.game.core.Mat4;
import com.laststar.game.gl.Mesh;
import com.laststar.game.gl.MeshData;

/**
 * Animated weighted mesh with immutable bind geometry and a reusable dynamic
 * GPU mesh. The CPU skinner is the cross-driver reference path; static vertex
 * and index data stay in VBOs while only deformed positions/normals change.
 */
public final class SkinnedMesh {

    public final Mesh mesh;
    public final MeshData bindPose;
    public final int[] joints;
    public final float[] weights;
    private final float[] deformedPositions;
    private final float[] deformedNormals;
    private final float[] colors;
    private final float[] baseColors;
    private final int[] dominantJoint;
    private int tintedJoint = -1;
    private float tintR, tintG, tintB, tintAmount;

    public SkinnedMesh(MeshData bindPose, int[] joints, float[] weights) {
        if (bindPose == null || bindPose.normals == null || bindPose.colors == null) {
            throw new IllegalArgumentException("skinned mesh needs positions, normals and colors");
        }
        int vertexCount = bindPose.vertexCount();
        if (joints.length != vertexCount * 4 || weights.length != vertexCount * 4) {
            throw new IllegalArgumentException("expected four skin influences per vertex");
        }
        this.bindPose = bindPose;
        this.joints = joints.clone();
        this.weights = weights.clone();
        this.deformedPositions = new float[bindPose.positions.length];
        this.deformedNormals = new float[bindPose.normals.length];
        this.colors = bindPose.colors.clone();
        this.baseColors = bindPose.colors.clone();
        this.dominantJoint = new int[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            int offset = v * 4;
            int best = 0;
            for (int k = 1; k < 4; k++) {
                if (this.weights[offset + k] > this.weights[offset + best]) best = k;
            }
            dominantJoint[v] = this.joints[offset + best];
        }
        mesh = Mesh.create(bindPose);
    }

    /** Creates a runtime skinned mesh from the imported asset representation. */
    public static SkinnedMesh fromAsset(SkinnedAsset asset) {
        if (asset == null) throw new IllegalArgumentException("asset is null");
        return new SkinnedMesh(asset.mesh, asset.joints, asset.weights);
    }

    /** Samples an imported clip and applies its hierarchical skin matrices. */
    public void update(SkinnedAsset asset, SkinnedAsset.Clip clip, float time) {
        if (asset == null || clip == null) throw new IllegalArgumentException("asset/clip is null");
        update(clip.sample(time, asset.parents, asset.inverseBind));
    }

    /** Builds a rigidly weighted skeleton mesh from the procedural rig's bone pieces. */
    public static SkinnedMesh fromRigidParts(MeshData[] parts) {
        MeshData combined = MeshData.merge(parts);
        int vertices = combined.vertexCount();
        int[] joints = new int[vertices * 4];
        float[] weights = new float[vertices * 4];
        int base = 0;
        for (int bone = 0; bone < parts.length; bone++) {
            int count = parts[bone].vertexCount();
            for (int v = 0; v < count; v++) {
                int o = (base + v) * 4;
                joints[o] = bone;
                weights[o] = 1f;
            }
            base += count;
        }
        return new SkinnedMesh(combined, joints, weights);
    }

    /** Optional per-joint color treatment, e.g. the player's selected skin tone. */
    public void setJointTint(int joint, float r, float g, float b, float amount) {
        amount = amount < 0f ? 0f : (amount > 1f ? 1f : amount);
        if (joint == tintedJoint && r == tintR && g == tintG && b == tintB
                && amount == tintAmount) return;
        tintedJoint = joint;
        tintR = r; tintG = g; tintB = b; tintAmount = amount;
        for (int v = 0; v < dominantJoint.length; v++) {
            int c = v * 4;
            float blend = dominantJoint[v] == joint ? amount : 0f;
            colors[c] = baseColors[c] + (r - baseColors[c]) * blend;
            colors[c + 1] = baseColors[c + 1] + (g - baseColors[c + 1]) * blend;
            colors[c + 2] = baseColors[c + 2] + (b - baseColors[c + 2]) * blend;
            colors[c + 3] = baseColors[c + 3];
        }
        mesh.updateDynamic(null, null, colors);
    }

    /** Applies one final matrix per bone and updates the GPU vertex buffers. */
    public void update(Mat4[] jointMatrices) {
        Skinning.skin(bindPose.positions, bindPose.normals, joints, weights,
                jointMatrices, deformedPositions, deformedNormals);
        mesh.updateDynamic(deformedPositions, deformedNormals, null);
    }
}
