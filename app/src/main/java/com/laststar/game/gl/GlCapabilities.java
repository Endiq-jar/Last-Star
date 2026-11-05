package com.laststar.game.gl;

/**
 * Runtime feature probe. Some Android emulators and very old drivers mis-report
 * VBO support, so we keep a single switch here and fall back to client-side
 * vertex arrays when compiling/testing the mesh code outside a GL context.
 */
public final class GlCapabilities {

    private static Boolean vbo;

    private GlCapabilities() {
    }

    public static boolean supportsVbo() {
        if (vbo == null) {
            // VBOs are core in GLES2 (which is our minimum); only disable for
            // the headless desktop harness.
            vbo = !"1".equals(System.getProperty("laststar.noVbo"));
        }
        return vbo;
    }

    public static void forceClientArraysForTests() {
        vbo = false;
    }
}
