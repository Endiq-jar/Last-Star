package com.laststar.game.ui;

import android.opengl.GLES20;

import com.laststar.game.gl.GlProgram;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * Uploads a {@link UiBatch} to the GPU and draws it in one pass.
 *
 * <p>Text is sampled from a generated 1-channel atlas; untextured primitives are
 * packed into the same strip of the atlas (a fully white texel) so a single draw
 * call covers the whole interface.</p>
 */
public final class UiRenderer {

    private static final String VERT =
            "attribute vec2 aPos;\n"
                    + "attribute vec2 aUv;\n"
                    + "attribute vec4 aColor;\n"
                    + "varying vec2 vUv;\n"
                    + "varying vec4 vColor;\n"
                    + "uniform vec2 uInvSize;\n"
                    + "void main() {\n"
                    + "  vec2 p = aPos * uInvSize;\n"
                    + "  gl_Position = vec4(p.x * 2.0 - 1.0, 1.0 - p.y * 2.0, 0.0, 1.0);\n"
                    + "  vUv = aUv;\n"
                    + "  vColor = aColor;\n"
                    + "}\n";

    private static final String FRAG =
            "precision mediump float;\n"
                    + "varying vec2 vUv;\n"
                    + "varying vec4 vColor;\n"
                    + "uniform sampler2D uTex;\n"
                    + "void main() {\n"
                    + "  float coverage = texture2D(uTex, vUv).r;\n"
                    + "  if (vColor.a < 0.004) discard;\n"
                    + "  gl_FragColor = vec4(vColor.rgb, vColor.a * coverage);\n"
                    + "}\n";

    private GlProgram program;
    private int texture;
    private FloatBuffer buffer;
    private float[] scratch = new float[0];
    private final int[] vbo = new int[1];
    private boolean useVbo = true;

    private final Font font = new Font();
    private int aPos = -1;
    private int aUv = -1;
    private int aColor = -1;
    private int uInvSize = -1;
    private int uTex = -1;

    public void init() {
        program = GlProgram.create(VERT, FRAG);
        aPos = GLES20.glGetAttribLocation(program.id, "aPos");
        aUv = GLES20.glGetAttribLocation(program.id, "aUv");
        aColor = GLES20.glGetAttribLocation(program.id, "aColor");
        uInvSize = GLES20.glGetUniformLocation(program.id, "uInvSize");
        uTex = GLES20.glGetUniformLocation(program.id, "uTex");

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        texture = tex[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // Expand the font atlas into an RGBA texture and reserve a solid white
        // 2x2 block in the last row for untextured geometry.
        int w = font.atlasWidth;
        int h = font.atlasHeight + 2;
        ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        float whiteU;
        float whiteV;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float coverage = 0f;
                if (y < font.atlasHeight) {
                    coverage = (font.atlas[y * w + x] & 0xFF) / 255f;
                } else {
                    coverage = 1f;
                }
                pixels.put((byte) 255);
                pixels.put((byte) 255);
                pixels.put((byte) 255);
                pixels.put((byte) (int) (coverage * 255f));
            }
        }
        pixels.position(0);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);

        // Texture coordinates for the reserved white block.
        whiteU = 0.5f / w;
        whiteV = 1f - (0.5f / h);
        this.whiteUv = new float[]{whiteU, whiteV};

        GLES20.glGenBuffers(1, vbo, 0);
        useVbo = true;
    }

    private float[] whiteUv = {0f, 0f};

    /** Draws the accumulated batch. */
    public void draw(UiBatch batch, int screenWidth, int screenHeight) {
        List<UiBatch.Vertex> verts = batch.vertices();
        if (verts.isEmpty()) {
            return;
        }
        int floatsPerVertex = 8;
        int needed = verts.size() * floatsPerVertex;
        if (scratch.length < needed) {
            scratch = new float[Math.max(needed, scratch.length * 2 + 1024)];
        }
        int i = 0;
        for (UiBatch.Vertex v : verts) {
            scratch[i++] = v.x;
            scratch[i++] = v.y;
            float u = v.u;
            float vv = v.v;
            if (!v.textured) {
                u = whiteUv[0];
                vv = whiteUv[1];
            }
            scratch[i++] = u;
            scratch[i++] = vv;
            scratch[i++] = v.r;
            scratch[i++] = v.g;
            scratch[i++] = v.b;
            scratch[i++] = v.a;
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        program.use();
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glUniform2f(uInvSize, 1f / batch.virtualWidth, 1f / batch.virtualHeight);

        ByteBuffer bb = ByteBuffer.allocateDirect(needed * 4).order(ByteOrder.nativeOrder());
        bb.asFloatBuffer().put(scratch, 0, needed);
        FloatBuffer fb = bb.asFloatBuffer();
        fb.position(0);

        int stride = floatsPerVertex * 4;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        fb.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, stride, fb);
        fb.position(2);
        GLES20.glEnableVertexAttribArray(aUv);
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, stride, fb);
        fb.position(4);
        GLES20.glEnableVertexAttribArray(aColor);
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, fb);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts.size());

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUv);
        GLES20.glDisableVertexAttribArray(aColor);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    public Font font() {
        return font;
    }

    public void release() {
        if (texture != 0) {
            int[] t = {texture};
            GLES20.glDeleteTextures(1, t, 0);
            texture = 0;
        }
        if (vbo[0] != 0) {
            GLES20.glDeleteBuffers(1, vbo, 0);
            vbo[0] = 0;
        }
    }
}
