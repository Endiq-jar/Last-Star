package com.laststar.game.gl;

import android.opengl.GLES20;

/**
 * Immutable snapshot of the GL implementation actually bound to this thread.
 * Capability checks are deliberately based on the current context's version and
 * extension string, never on a device-model guess. The game still requests its
 * stable GLES2 compatibility path until its mesh and shader backends are
 * migrated; this probe makes future GLES3 paths safe to select explicitly.
 */
public final class GlCapabilities {

    public enum Api {
        UNKNOWN(0), GLES2(20), GLES3_0(30), GLES3_1(31), GLES3_2(32);

        public final int level;

        Api(int level) {
            this.level = level;
        }
    }

    public final Api api;
    public final String version;
    public final String vendor;
    public final String renderer;
    public final String extensions;

    private static volatile boolean forceClientArrays;

    private GlCapabilities(String version, String vendor, String renderer, String extensions) {
        this.version = safe(version);
        this.vendor = safe(vendor);
        this.renderer = safe(renderer);
        this.extensions = safe(extensions);
        this.api = parseApi(this.version);
    }

    /** Probe GL strings only while a context is current; safe on failed init. */
    public static GlCapabilities detectCurrentContext() {
        try {
            return fromStrings(
                    GLES20.glGetString(GLES20.GL_VERSION),
                    GLES20.glGetString(0x1F00), // GL_VENDOR; available on GLES20 implementations
                    GLES20.glGetString(GLES20.GL_RENDERER),
                    GLES20.glGetString(0x1F03)); // GL_EXTENSIONS
        } catch (RuntimeException e) {
            return fromStrings(null, null, null, null);
        }
    }

    /** Pure constructor used by tests and offline renderer diagnostics. */
    public static GlCapabilities fromStrings(String version, String vendor,
                                             String renderer, String extensions) {
        return new GlCapabilities(version, vendor, renderer, extensions);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Api parseApi(String version) {
        if (version == null) {
            return Api.UNKNOWN;
        }
        int start = version.indexOf("OpenGL ES");
        if (start < 0) {
            return Api.UNKNOWN;
        }
        int i = start + "OpenGL ES".length();
        while (i < version.length() && !Character.isDigit(version.charAt(i))) {
            i++;
        }
        if (i >= version.length()) {
            return Api.UNKNOWN;
        }
        int major = 0;
        while (i < version.length() && Character.isDigit(version.charAt(i))) {
            major = major * 10 + (version.charAt(i++) - '0');
        }
        if (i >= version.length() || version.charAt(i) != '.') {
            return Api.UNKNOWN;
        }
        i++;
        int minor = 0;
        if (i >= version.length() || !Character.isDigit(version.charAt(i))) {
            return Api.UNKNOWN;
        }
        while (i < version.length() && Character.isDigit(version.charAt(i))) {
            minor = minor * 10 + (version.charAt(i++) - '0');
        }
        if (major >= 3) {
            if (minor >= 2) return Api.GLES3_2;
            if (minor == 1) return Api.GLES3_1;
            return Api.GLES3_0;
        }
        return major == 2 ? Api.GLES2 : Api.UNKNOWN;
    }

    public boolean atLeast(Api minimum) {
        return api.level >= minimum.level && api != Api.UNKNOWN;
    }

    public boolean hasVbo() {
        return atLeast(Api.GLES2);
    }

    public boolean supportsVertexArrays() {
        return atLeast(Api.GLES3_0) || has("GL_OES_vertex_array_object");
    }

    public boolean supportsInstancing() {
        return atLeast(Api.GLES3_0) || has("GL_EXT_instanced_arrays");
    }

    public boolean supportsDepthTextures() {
        return atLeast(Api.GLES3_0) || has("GL_OES_depth_texture");
    }

    public boolean supportsDerivatives() {
        return atLeast(Api.GLES3_0) || has("GL_OES_standard_derivatives");
    }

    public boolean supportsHalfFloatTargets() {
        return atLeast(Api.GLES3_0) || has("GL_EXT_color_buffer_half_float");
    }

    private boolean has(String extension) {
        return extensions.indexOf(extension) >= 0;
    }

    /** Human-readable GPU summary suitable for the startup log and bug reports. */
    public String summary() {
        return "api=" + api + ", vendor=" + vendor + ", renderer=" + renderer
                + ", version=" + version;
    }

    /** Compatibility helper for the original GLES2 mesh path. */
    public static boolean supportsVbo() {
        return !forceClientArrays
                && !"1".equals(System.getProperty("laststar.noVbo"))
                && detectCurrentContext().hasVbo();
    }

    public static boolean supportsVboOnCurrentContext() {
        return supportsVbo();
    }

    /** Retained for deterministic headless tests that cannot create a GL context. */
    public static void forceClientArraysForTests() {
        forceClientArrays = true;
    }
}
