package android.opengl;

/**
 * A recording stand-in for android.opengl.GLES20, used by the desktop harness
 * to run the real GL start-up path without a GPU.
 *
 * <p>The real class is a thin native binding: every method is a no-op stub that
 * throws UnsatisfiedLinkError off-device, so no amount of desktop testing
 * exercises the renderer. This class implements the same public API, records the
 * calls, and -- crucially -- validates shader sources the way a driver would,
 * so a bad shader fails here instead of on a phone.</p>
 */
public final class GLES20 {
    public static final int GL_DEPTH_BUFFER_BIT = 0x100;
    public static final int GL_COLOR_BUFFER_BIT = 0x4000;
    public static final int GL_TRIANGLES = 0x0004;
    public static final int GL_DEPTH_TEST = 0x0B71;
    public static final int GL_BLEND = 0x0BE2;
    public static final int GL_SRC_ALPHA = 0x0302;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    public static final int GL_ONE = 1;
    public static final int GL_LEQUAL = 0x0203;
    public static final int GL_FLOAT = 0x1406;
    public static final int GL_UNSIGNED_SHORT = 0x1403;
    public static final int GL_UNSIGNED_BYTE = 0x1401;
    public static final int GL_TEXTURE_2D = 0x0DE1;
    public static final int GL_TEXTURE0 = 0x84C0;
    public static final int GL_RGBA = 0x1908;
    public static final int GL_LINEAR = 0x2601;
    public static final int GL_NEAREST = 0x2600;
    public static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    public static final int GL_TEXTURE_WRAP_S = 0x2802;
    public static final int GL_TEXTURE_WRAP_T = 0x2803;
    public static final int GL_CLAMP_TO_EDGE = 0x812F;
    public static final int GL_VERTEX_SHADER = 0x8B31;
    public static final int GL_FRAGMENT_SHADER = 0x8B30;
    public static final int GL_COMPILE_STATUS = 0x8B81;
    public static final int GL_LINK_STATUS = 0x8B82;
    public static final int GL_ARRAY_BUFFER = 0x8892;
    public static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    public static final int GL_STATIC_DRAW = 0x88E4;
    public static final int GL_STREAM_DRAW = 0x88E0;
    public static final int GL_RENDERER = 0x1F01;
    public static final int GL_VERSION = 0x1F02;

    /** Set by the harness: reject fwidth unless the extension was requested. */
    public static boolean strictDerivatives = true;

    public static final java.util.List<String> compiledShaders = new java.util.ArrayList<>();
    public static int draws;
    public static int bufferUploads;
    public static int streamUploads;
    public static int bufferOffsetDraws;
    public static int clientPointerCalls;
    public static boolean buffersAvailable = true;
    public static String versionString = "OpenGL ES 2.0 (fake)";

    private static final java.util.Map<Integer, String> shaderSource = new java.util.HashMap<>();
    private static final java.util.Map<Integer, Boolean> shaderOk = new java.util.HashMap<>();
    private static int nextId = 1;

    public static void reset() {
        compiledShaders.clear();
        shaderSource.clear();
        shaderOk.clear();
        nextId = 1;
        draws = 0;
        bufferUploads = 0;
        streamUploads = 0;
        bufferOffsetDraws = 0;
        clientPointerCalls = 0;
        buffersAvailable = true;
        versionString = "OpenGL ES 2.0 (fake)";
    }

    public static String glGetString(int name) {
        return name == GL_VERSION ? versionString : "FakeGPU";
    }

    public static int glCreateShader(int type) {
        int id = nextId++;
        shaderOk.put(id, true);
        return id;
    }

    public static void glShaderSource(int shader, String src) {
        shaderSource.put(shader, src);
        compiledShaders.add(src);
    }

    /** Mirrors the GLES2 rules that actually break on Android drivers. */
    public static void glCompileShader(int shader) {
        String src = shaderSource.get(shader);
        if (src == null) {
            shaderOk.put(shader, false);
            return;
        }
        boolean ok = true;
        boolean vertex = src.contains("gl_Position");
        if (src.contains("fwidth(") || src.contains("dFdx(") || src.contains("dFdy(")) {
            if (strictDerivatives
                    && !src.contains("#extension GL_OES_standard_derivatives")) {
                ok = false;
            }
        }
        // #extension must come before any other statement.
        int ext = src.indexOf("#extension");
        if (ext > 0) {
            String before = src.substring(0, ext);
            for (String line : before.split("\n")) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("//")) {
                    ok = false;
                }
            }
        }
        if (!vertex && !src.contains("precision ")) {
            ok = false;
        }
        if (!src.contains("void main")) {
            ok = false;
        }
        if (src.split("\\{", -1).length != src.split("\\}", -1).length) {
            ok = false;
        }
        shaderOk.put(shader, ok);
    }

    public static void glGetShaderiv(int shader, int pname, int[] out, int off) {
        out[off] = Boolean.TRUE.equals(shaderOk.get(shader)) ? 1 : 0;
    }

    public static String glGetShaderInfoLog(int shader) {
        return "0:1: P0000 fake driver rejected the shader";
    }

    public static int glCreateProgram() {
        return nextId++;
    }

    public static void glAttachShader(int program, int shader) {
    }

    public static void glLinkProgram(int program) {
    }

    public static void glGetProgramiv(int program, int pname, int[] out, int off) {
        out[off] = 1;
    }

    public static String glGetProgramInfoLog(int program) {
        return "";
    }

    public static void glDeleteShader(int shader) {
    }

    public static void glDeleteProgram(int program) {
    }

    public static void glUseProgram(int program) {
    }

    public static int glGetUniformLocation(int program, String name) {
        return 1;
    }

    public static int glGetAttribLocation(int program, String name) {
        return 1;
    }

    public static void glUniform1f(int loc, float v) {
    }

    public static void glUniform2f(int loc, float a, float b) {
    }

    public static void glUniform3f(int loc, float a, float b, float c) {
    }

    public static void glUniform4f(int loc, float a, float b, float c, float d) {
    }

    public static void glUniform1i(int loc, int v) {
    }

    public static void glUniformMatrix4fv(int loc, int count, boolean transpose,
                                          float[] v, int off) {
    }

    public static void glGenBuffers(int n, int[] out, int off) {
        for (int i = 0; i < n; i++) {
            out[off + i] = buffersAvailable ? nextId++ : 0;
        }
    }

    public static void glBindBuffer(int target, int buffer) {
    }

    public static void glBufferData(int target, int size, java.nio.Buffer data, int usage) {
        bufferUploads++;
        if (usage == GL_STREAM_DRAW) streamUploads++;
    }

    public static void glDeleteBuffers(int n, int[] b, int off) {
    }

    public static void glGenTextures(int n, int[] out, int off) {
        for (int i = 0; i < n; i++) {
            out[off + i] = nextId++;
        }
    }

    public static void glBindTexture(int target, int texture) {
    }

    public static void glTexParameteri(int target, int pname, int value) {
    }

    public static void glTexImage2D(int target, int level, int internal, int w, int h,
                                    int border, int format, int type, java.nio.Buffer p) {
    }

    public static void glDeleteTextures(int n, int[] t, int off) {
    }

    public static void glActiveTexture(int unit) {
    }

    public static void glEnableVertexAttribArray(int index) {
    }

    public static void glDisableVertexAttribArray(int index) {
    }

    public static void glVertexAttribPointer(int index, int size, int type,
                                             boolean normalized, int stride,
                                             java.nio.Buffer ptr) {
        clientPointerCalls++;
    }

    /** The int offset overload used when a VBO is bound. */
    public static void glVertexAttribPointer(int index, int size, int type,
                                             boolean normalized, int stride,
                                             int offset) {
    }

    public static void glDrawArrays(int mode, int first, int count) {
        draws++;
    }

    public static void glDrawElements(int mode, int count, int type, java.nio.Buffer idx) {
        draws++;
    }

    public static void glDrawElements(int mode, int count, int type, int offset) {
        draws++;
        bufferOffsetDraws++;
    }

    public static void glEnable(int cap) {
    }

    public static void glDisable(int cap) {
    }

    public static void glDepthFunc(int func) {
    }

    public static void glDepthMask(boolean flag) {
    }

    public static void glBlendFunc(int src, int dst) {
    }

    public static void glClear(int mask) {
    }

    public static void glClearColor(float r, float g, float b, float a) {
    }

    public static void glViewport(int x, int y, int w, int h) {
    }

    public static int glGetError() {
        return 0;
    }
}
