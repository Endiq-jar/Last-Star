package com.laststar.game.gl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** Indexed mesh with an ES2 client-array fallback and GPU VBO storage. */
public final class Mesh {

    public final FloatBuffer positions;
    public final FloatBuffer normals;
    public final FloatBuffer colors;
    public final FloatBuffer waves;
    public final ShortBuffer indices;
    public final int vertexCount;
    public final int indexCount;
    public final int stride = 0;

    public final int positionVbo;
    public final int normalVbo;
    public final int colorVbo;
    public final int waveVbo;
    public final int indexVbo;

    private Mesh(FloatBuffer positions, FloatBuffer normals, FloatBuffer colors, FloatBuffer waves,
                 ShortBuffer indices, int vertexCount, int indexCount,
                 int positionVbo, int normalVbo, int colorVbo, int waveVbo, int indexVbo) {
        this.positions = positions;
        this.normals = normals;
        this.colors = colors;
        this.waves = waves;
        this.indices = indices;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.positionVbo = positionVbo;
        this.normalVbo = normalVbo;
        this.colorVbo = colorVbo;
        this.waveVbo = waveVbo;
        this.indexVbo = indexVbo;
    }

    /** Wraps buffers for deterministic CPU-only and legacy callers. */
    public static Mesh fromBuffers(FloatBuffer positions, FloatBuffer normals, FloatBuffer colors,
                                   FloatBuffer waves, ShortBuffer indices,
                                   int vertexCount, int indexCount) {
        return new Mesh(positions, normals, colors, waves, indices, vertexCount, indexCount,
                0, 0, 0, 0, 0);
    }

    /** Creates a mesh while a GL context is current, uploading immutable geometry to GPU buffers. */
    public static Mesh create(MeshData data) {
        int vertexCount = data.vertexCount();
        FloatBuffer pb = floats(data.positions);
        FloatBuffer nb = data.normals == null ? null : floats(data.normals);
        FloatBuffer cb = data.colors == null ? null : floats(data.colors);
        FloatBuffer wb = data.waves == null ? null : floats(data.waves);
        ShortBuffer ib = shorts(data.indices);

        int p = upload(GLES20.GL_ARRAY_BUFFER, pb);
        int n = nb == null ? 0 : upload(GLES20.GL_ARRAY_BUFFER, nb);
        int c = cb == null ? 0 : upload(GLES20.GL_ARRAY_BUFFER, cb);
        int w = wb == null ? 0 : upload(GLES20.GL_ARRAY_BUFFER, wb);
        int ix = upload(GLES20.GL_ELEMENT_ARRAY_BUFFER, ib);
        if (p == 0 || ix == 0 || (nb != null && n == 0)
                || (cb != null && c == 0) || (wb != null && w == 0)) {
            int[] ids = {p, n, c, w, ix};
            GLES20.glDeleteBuffers(ids.length, ids, 0);
            if (GlCapabilities.detectCurrentContext().atLeast(GlCapabilities.Api.GLES3_0)) {
                throw new IllegalStateException("GLES3 requires VBO-backed mesh buffers");
            }
            return new Mesh(pb, nb, cb, wb, ib, vertexCount, data.indices.length,
                    0, 0, 0, 0, 0);
        }
        return new Mesh(pb, nb, cb, wb, ib, vertexCount, data.indices.length, p, n, c, w, ix);
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

    public boolean usesVbos() {
        return positionVbo != 0 && indexVbo != 0;
    }

    private static int upload(int target, java.nio.Buffer data) {
        int[] names = new int[1];
        GLES20.glGenBuffers(1, names, 0);
        if (names[0] == 0) return 0;
        int bytes = data.remaining() * (data instanceof FloatBuffer ? 4 : 2);
        GLES20.glBindBuffer(target, names[0]);
        GLES20.glBufferData(target, bytes, data, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(target, 0);
        return names[0];
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
        if (usesVbos()) {
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexVbo);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount,
                    GLES20.GL_UNSIGNED_SHORT, 0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        } else {
            indices.position(0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount,
                    GLES20.GL_UNSIGNED_SHORT, indices);
        }
    }
}
