package com.laststar.game.gl;

import android.opengl.GLES20;
import android.util.Log;

/** Thin wrapper around a compiled/linked GLSL program. */
public final class GlProgram {

    private static final String TAG = "LastStar/GlProgram";

    public final int id;
    private final int uMvp;
    private final int uModel;
    private final int uColor;
    private final int uEmissive;
    private final int uAlpha;
    private final int uFogDensity;
    private final int uFogColor;
    private final int uSunDir;
    private final int uSunColor;
    private final int uAmbient;
    private final int uTexScale;
    private final int uTime;
    private final int uTint;
    private final int uTintAmount;
    private final int aPosition;
    private final int aNormal;
    private final int aColor;
    private final int aWave;

    private GlProgram(int id, String vertSrc, String fragSrc) {
        this.id = id;
        uMvp = GLES20.glGetUniformLocation(id, "uMvp");
        uModel = GLES20.glGetUniformLocation(id, "uModel");
        uColor = GLES20.glGetUniformLocation(id, "uColor");
        uEmissive = GLES20.glGetUniformLocation(id, "uEmissive");
        uAlpha = GLES20.glGetUniformLocation(id, "uAlpha");
        uFogDensity = GLES20.glGetUniformLocation(id, "uFogDensity");
        uFogColor = GLES20.glGetUniformLocation(id, "uFogColor");
        uSunDir = GLES20.glGetUniformLocation(id, "uSunDir");
        uSunColor = GLES20.glGetUniformLocation(id, "uSunColor");
        uAmbient = GLES20.glGetUniformLocation(id, "uAmbient");
        uTexScale = GLES20.glGetUniformLocation(id, "uTexScale");
        uTime = GLES20.glGetUniformLocation(id, "uTime");
        uTint = GLES20.glGetUniformLocation(id, "uTint");
        uTintAmount = GLES20.glGetUniformLocation(id, "uTintAmount");
        aPosition = GLES20.glGetAttribLocation(id, "aPosition");
        aNormal = GLES20.glGetAttribLocation(id, "aNormal");
        aColor = GLES20.glGetAttribLocation(id, "aColor");
        aWave = GLES20.glGetAttribLocation(id, "aWave");
    }

    public static GlProgram create(String vertSrc, String fragSrc) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vertSrc);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragSrc);
        int prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vs);
        GLES20.glAttachShader(prog, fs);
        GLES20.glLinkProgram(prog);
        int[] status = new int[1];
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(prog);
            Log.e(TAG, "link failed: " + log);
            throw new IllegalStateException("Shader link failed: " + log);
        }
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        return new GlProgram(prog, vertSrc, fragSrc);
    }

    private static int compile(int type, String src) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, src);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            Log.e(TAG, "compile failed (" + type + "): " + log);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Shader compile failed: " + log);
        }
        return shader;
    }

    public void use() {
        GLES20.glUseProgram(id);
    }

    public void bindMesh(Mesh mesh) {
        boolean vbo = mesh.usesVbos();
        if (aPosition >= 0) {
            GLES20.glEnableVertexAttribArray(aPosition);
            if (vbo) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.positionVbo);
                GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, mesh.stride, 0);
            } else {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, mesh.stride, mesh.positions);
            }
        }
        if (aNormal >= 0 && mesh.normals != null) {
            GLES20.glEnableVertexAttribArray(aNormal);
            if (vbo) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.normalVbo);
                GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, mesh.stride, 0);
            } else {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, mesh.stride, mesh.normals);
            }
        }
        if (aColor >= 0 && mesh.colors != null) {
            GLES20.glEnableVertexAttribArray(aColor);
            if (vbo) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.colorVbo);
                GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, mesh.stride, 0);
            } else {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, mesh.stride, mesh.colors);
            }
        }
        if (aWave >= 0 && mesh.waves != null) {
            GLES20.glEnableVertexAttribArray(aWave);
            if (vbo) {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.waveVbo);
                GLES20.glVertexAttribPointer(aWave, 1, GLES20.GL_FLOAT, false, mesh.stride, 0);
            } else {
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                GLES20.glVertexAttribPointer(aWave, 1, GLES20.GL_FLOAT, false, mesh.stride, mesh.waves);
            }
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void unbindMesh(Mesh mesh) {
        if (aPosition >= 0) {
            GLES20.glDisableVertexAttribArray(aPosition);
        }
        if (aNormal >= 0 && mesh.normals != null) {
            GLES20.glDisableVertexAttribArray(aNormal);
        }
        if (aColor >= 0 && mesh.colors != null) {
            GLES20.glDisableVertexAttribArray(aColor);
        }
        if (aWave >= 0 && mesh.waves != null) {
            GLES20.glDisableVertexAttribArray(aWave);
        }
    }

    public void setMvp(float[] m) {
        if (uMvp >= 0) {
            GLES20.glUniformMatrix4fv(uMvp, 1, false, m, 0);
        }
    }

    public void setModel(float[] m) {
        if (uModel >= 0) {
            GLES20.glUniformMatrix4fv(uModel, 1, false, m, 0);
        }
    }

    public void setColor(float r, float g, float b, float a) {
        if (uColor >= 0) {
            GLES20.glUniform4f(uColor, r, g, b, a);
        }
    }

    public void setEmissive(float v) {
        if (uEmissive >= 0) {
            GLES20.glUniform1f(uEmissive, v);
        }
    }

    public void setAlpha(float v) {
        if (uAlpha >= 0) {
            GLES20.glUniform1f(uAlpha, v);
        }
    }

    public void setFog(float density, float r, float g, float b) {
        if (uFogDensity >= 0) {
            GLES20.glUniform1f(uFogDensity, density);
        }
        if (uFogColor >= 0) {
            GLES20.glUniform3f(uFogColor, r, g, b);
        }
    }

    public void setSun(float dx, float dy, float dz, float r, float g, float b) {
        if (uSunDir >= 0) {
            GLES20.glUniform3f(uSunDir, dx, dy, dz);
        }
        if (uSunColor >= 0) {
            GLES20.glUniform3f(uSunColor, r, g, b);
        }
    }

    public void setAmbient(float r, float g, float b) {
        if (uAmbient >= 0) {
            GLES20.glUniform3f(uAmbient, r, g, b);
        }
    }

    public void setTexScale(float s) {
        if (uTexScale >= 0) {
            GLES20.glUniform1f(uTexScale, s);
        }
    }

    public void setTime(float t) {
        if (uTime >= 0) {
            GLES20.glUniform1f(uTime, t);
        }
    }

    public void setTint(float r, float g, float b, float a) {
        if (uTint >= 0) {
            GLES20.glUniform3f(uTint, r, g, b);
        }
        if (uTintAmount >= 0) {
            GLES20.glUniform1f(uTintAmount, a);
        }
    }

    /** Convenience: sets tint colour with the blend amount as alpha. */
    public void setTintAmount(float amount) {
        if (uTintAmount >= 0) {
            GLES20.glUniform1f(uTintAmount, amount);
        }
    }

    public void setFloat(String name, float v) {
        int loc = GLES20.glGetUniformLocation(id, name);
        if (loc >= 0) {
            GLES20.glUniform1f(loc, v);
        }
    }

    public void setVec3(String name, float x, float y, float z) {
        int loc = GLES20.glGetUniformLocation(id, name);
        if (loc >= 0) {
            GLES20.glUniform3f(loc, x, y, z);
        }
    }

    public void setMat4(String name, float[] m) {
        int loc = GLES20.glGetUniformLocation(id, name);
        if (loc >= 0) {
            GLES20.glUniformMatrix4fv(loc, 1, false, m, 0);
        }
    }
}
