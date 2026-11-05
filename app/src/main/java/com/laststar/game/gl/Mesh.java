package com.laststar.game.gl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * An indexed mesh held in direct buffers.
 *
 * <p>Geometry is uploaded as client-side vertex arrays which is simple, robust
 * across drivers/emulators, and fast enough here: the whole world is only a
 * handful of draw calls per frame.</p>
 */
public final class Mesh {

    public final FloatBuffer positions;
    public final FloatBuffer normals;
    public final FloatBuffer colors;
    public final FloatBuffer waves;
    public final ShortBuffer indices;
    public final int vertexCount;
    public final int indexCount;
    public final int stride = 0;

    private Mesh(FloatBuffer positions, FloatBuffer normals, FloatBuffer colors, FloatBuffer waves,
                 ShortBuffer indices, int vertexCount, int indexCount) {
        this.positions = positions;
        this.normals = normals;
        this.colors = colors;
        this.waves = waves;
        this.indices = indices;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
    }

    public static Mesh create(MeshData data) {
        int vertexCount = data.vertexCount();
        FloatBuffer pb = floats(data.positions);
        FloatBuffer nb = data.normals == null ? null : floats(data.normals);
        FloatBuffer cb = data.colors == null ? null : floats(data.colors);
        FloatBuffer wb = data.waves == null ? null : floats(data.waves);
        ShortBuffer ib = shorts(data.indices);
        return new Mesh(pb, nb, cb, wb, ib, vertexCount, data.indices.length);
    }

    public static Mesh create(float[] pos, float[] nrm, float[] col, float[] waves, short[] idx) {
        MeshData d = new MeshData();
        d.positions = pos;
        d.normals = nrm;
        d.colors = col;
        d.waves = waves;
        d.indices = idx;
        return create(d);
    }

    private static FloatBuffer floats(float[] src) {
        FloatBuffer fb = ByteBuffer.allocateDirect(src.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(src).position(0);
        return fb;
    }

    private static ShortBuffer shorts(short[] src) {
        ShortBuffer sb = ByteBuffer.allocateDirect(src.length * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        sb.put(src).position(0);
        return sb;
    }

    public void draw() {
        indices.position(0);
        android.opengl.GLES20.glDrawElements(android.opengl.GLES20.GL_TRIANGLES,
                indexCount, android.opengl.GLES20.GL_UNSIGNED_SHORT, indices);
    }
}
