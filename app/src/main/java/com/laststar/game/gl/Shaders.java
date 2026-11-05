package com.laststar.game.gl;

/** GLSL ES 1.00 shader sources used by the renderer. */
public final class Shaders {

    private Shaders() {
    }

    /** Lit geometry with fog, rim light and an optional emissive term. */
    public static final String SCENE_VERT =
            "uniform mat4 uMvp;\n"
                    + "uniform mat4 uModel;\n"
                    + "attribute vec3 aPosition;\n"
                    + "attribute vec3 aNormal;\n"
                    + "attribute vec4 aColor;\n"
                    + "attribute float aWave;\n"
                    + "uniform float uTime;\n"
                    + "uniform float uWaveAmp;\n"
                    + "varying vec3 vNormal;\n"
                    + "varying vec4 vColor;\n"
                    + "varying vec3 vWorld;\n"
                    + "void main() {\n"
                    + "  vec3 pos = aPosition;\n"
                    + "  if (uWaveAmp > 0.0) {\n"
                    + "    pos.y += sin(uTime * 1.35 + aWave) * uWaveAmp\n"
                    + "           + sin(uTime * 0.71 + aWave * 1.7) * uWaveAmp * 0.55;\n"
                    + "    pos.x += cos(uTime * 0.9 + aWave * 0.8) * uWaveAmp * 0.35;\n"
                    + "  }\n"
                    + "  vec4 world = uModel * vec4(pos, 1.0);\n"
                    + "  vWorld = world.xyz;\n"
                    + "  vNormal = (uModel * vec4(aNormal, 0.0)).xyz;\n"
                    + "  vColor = aColor;\n"
                    + "  gl_Position = uMvp * vec4(pos, 1.0);\n"
                    + "}\n";

    public static final String SCENE_FRAG =
            "precision mediump float;\n"
                    + "varying vec3 vNormal;\n"
                    + "varying vec4 vColor;\n"
                    + "varying vec3 vWorld;\n"
                    + "uniform vec3 uSunDir;\n"
                    + "uniform vec3 uSunColor;\n"
                    + "uniform vec3 uAmbient;\n"
                    + "uniform vec3 uCamPos;\n"
                    + "uniform float uFogDensity;\n"
                    + "uniform vec3 uFogColor;\n"
                    + "uniform float uEmissive;\n"
                    + "uniform float uAlpha;\n"
                    + "uniform vec3 uTint;\n"
                    + "uniform float uTintAmount;\n"
                    + "void main() {\n"
                    + "  vec3 n = normalize(vNormal);\n"
                    + "  float diff = max(dot(n, uSunDir), 0.0);\n"
                    + "  float sky = 0.5 + 0.5 * n.y;\n"
                    + "  vec3 viewDir = normalize(uCamPos - vWorld);\n"
                    + "  float rim = pow(1.0 - max(dot(n, viewDir), 0.0), 2.6);\n"
                    + "  vec3 base = mix(vColor.rgb, uTint, uTintAmount);\n"
                    + "  vec3 lit = base * (uAmbient * (0.55 + 0.45 * sky) + uSunColor * diff);\n"
                    + "  lit += base * rim * 0.42;\n"
                    + "  lit += base * uEmissive;\n"
                    + "  float dist = length(uCamPos - vWorld);\n"
                    + "  float fog = 1.0 - exp(-dist * uFogDensity);\n"
                    + "  vec3 c = mix(lit, uFogColor, clamp(fog, 0.0, 1.0));\n"
                    + "  gl_FragColor = vec4(c, vColor.a * uAlpha);\n"
                    + "}\n";

    /** Unlit additive geometry for particles and glows. */
    public static final String GLOW_VERT =
            "uniform mat4 uMvp;\n"
                    + "attribute vec3 aPosition;\n"
                    + "attribute vec4 aColor;\n"
                    + "varying vec4 vColor;\n"
                    + "varying vec2 vUv;\n"
                    + "attribute vec2 aUv;\n"
                    + "void main() {\n"
                    + "  vColor = aColor;\n"
                    + "  vUv = aUv;\n"
                    + "  gl_Position = uMvp * vec4(aPosition, 1.0);\n"
                    + "}\n";

    public static final String GLOW_FRAG =
            "precision mediump float;\n"
                    + "varying vec4 vColor;\n"
                    + "varying vec2 vUv;\n"
                    + "void main() {\n"
                    + "  float d = length(vUv - vec2(0.5));\n"
                    + "  float a = smoothstep(0.5, 0.02, d);\n"
                    + "  gl_FragColor = vec4(vColor.rgb, vColor.a * a);\n"
                    + "}\n";

    /** Full-screen sky: vertical gradient with a sun disc and star speckles. */
    public static final String SKY_VERT =
            "attribute vec2 aPos;\n"
                    + "varying vec2 vUv;\n"
                    + "void main() {\n"
                    + "  vUv = aPos * 0.5 + 0.5;\n"
                    + "  gl_Position = vec4(aPos, 0.999, 1.0);\n"
                    + "}\n";

    public static final String SKY_FRAG =
            "precision mediump float;\n"
                    + "varying vec2 vUv;\n"
                    + "uniform vec2 uSunScreen;\n"
                    + "uniform float uSunVisible;\n"
                    + "uniform vec3 uHorizon;\n"
                    + "uniform vec3 uZenith;\n"
                    + "uniform vec3 uSunColor;\n"
                    + "uniform float uTime;\n"
                    + "void main() {\n"
                    + "  float t = clamp(vUv.y, 0.0, 1.0);\n"
                    + "  vec3 c = mix(uHorizon, uZenith, pow(t, 0.75));\n"
                    + "  // Sun / moon disc.\n"
                    + "  float d = length((vUv - uSunScreen) * vec2(1.0, 1.05));\n"
                    + "  float disc = smoothstep(0.085, 0.055, d) * uSunVisible;\n"
                    + "  float halo = smoothstep(0.42, 0.0, d) * 0.55 * uSunVisible;\n"
                    + "  c += uSunColor * (disc * 1.5 + halo);\n"
                    + "  // Deterministic speckle stars in the upper sky.\n"
                    + "  vec2 g = floor(vUv * vec2(160.0, 90.0));\n"
                    + "  float h = fract(sin(dot(g, vec2(12.9898, 78.233))) * 43758.5453);\n"
                    + "  float s = step(0.9965, h) * smoothstep(0.34, 0.62, t);\n"
                    + "  c += vec3(s) * (0.65 + 0.35 * sin(uTime * 2.0 + h * 40.0));\n"
                    + "  gl_FragColor = vec4(c, 1.0);\n"
                    + "}\n";

    /** Ground haze band drawn between terrain and sky. */
    public static final String HAZE_FRAG =
            "precision mediump float;\n"
                    + "varying vec2 vUv;\n"
                    + "uniform vec3 uColor;\n"
                    + "void main() {\n"
                    + "  float a = smoothstep(0.0, 0.55, vUv.y) * (1.0 - smoothstep(0.55, 1.0, vUv.y));\n"
                    + "  gl_FragColor = vec4(uColor, a * 0.85);\n"
                    + "}\n";
}
