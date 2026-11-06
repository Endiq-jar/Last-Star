package com.laststar.game.gl;

/** GLSL ES 1.00 shader sources used by the renderer. */
public final class Shaders {

    private Shaders() {
    }

    /**
     * Vertex stage for world geometry.
     *
     * <p>Does nothing but transform and pass the pieces the fragment stage needs
     * to do its work: world position, world normal, vertex colour and the
     * screen-space depth rate, which is the cheap stand-in for the depth true
     * cel shading wants (see {@link #SCENE_FRAG}).</p>
     */
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

    /**
     * Toon shading, anime style.
     *
     * <p>Three things make a frame read as "Genshin" rather than as low-poly
     * flat shading, and all three live here:</p>
     *
     * <ol>
     *   <li><b>Band the light.</b> The diffuse term is quantised to hard steps
     *       instead of falling off smoothly, so lit faces are one flat value
     *       and shadowed faces are another, with a crisp line between them.
     *       The steps are deliberately uneven: a wide lit band, a narrow
     *       "terminator" band, then shadow.</li>
     *   <li><b>Tint the shadow, don't dim it.</b> Shadows go toward a cool
     *       blue-violet rather than black, which is what keeps anime foliage
     *       vivid instead of muddy. The ambient term has its own sky/near-ground
     *       gradient for the same reason.</li>
     *   <li><b>A rim light.</b> A bright edge on silhouettes facing away from
     *       the sun, plus a soft fill from the camera side, which separates
     *       characters from the background and gives hard surfaces that
     *       illustrated sheen.</li>
     * </ol>
     *
     * <p>Deliberately free of derivatives (no {@code fwidth}, {@code dFdx},
     * {@code dFdy}) and therefore free of extension requirements: this exact
     * shader ships to every device, and the app once failed to start on Mali
     * and Adreno hardware because a sibling shader used one of them without the
     * extension. See {@code tools/check_glsl.py}.</p>
     */
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
                    + "\n"
                    + "// Hard-edged step. The edge is 1.5% of the range wide: enough to\n"
                    + "// survive 8-bit banding on the shadow boundary, far too narrow to\n"
                    + "// read as a gradient.\n"
                    + "float band(float x, float edge) {\n"
                    + "  return smoothstep(edge - 0.015, edge + 0.015, x);\n"
                    + "}\n"
                    + "\n"
                    + "void main() {\n"
                    + "  vec3 n = normalize(vNormal);\n"
                    + "  vec3 viewDir = normalize(uCamPos - vWorld);\n"
                    + "  float dist = length(uCamPos - vWorld);\n"
                    + "  vec3 base = mix(vColor.rgb, uTint, uTintAmount);\n"
                    + "\n"
                    + "  // Grazing faces are lit slightly less: gives the ground its soft\n"
                    + "  // roll and stops wide grass planes reading as one flat sheet.\n"
                    + "  float nDotL = dot(n, uSunDir);\n"
                    + "  float facing = clamp(nDotL * 0.5 + 0.5, 0.0, 1.0);\n"
                    + "\n"
                    + "  // Three-tone ramp: shadow / terminator / lit.\n"
                    + "  float lit = band(facing, 0.56);\n"
                    + "  float mid = band(facing, 0.47) * (1.0 - lit);\n"
                    + "\n"
                    + "  // Shadow colour: cool and saturated, never black.\n"
                    + "  vec3 shadowTint = vec3(0.52, 0.60, 0.92);\n"
                    + "  vec3 amb = uAmbient * mix(shadowTint, vec3(1.0), 0.35 + 0.35 * n.y);\n"
                    + "  vec3 shade = base * amb * 0.78;\n"
                    + "  shade = mix(shade, base * amb * 1.10, mid);\n"
                    + "  // Sunlight is capped rather than added straight on: with a\n"
                    + "  // bright ambient term an uncapped sum clips every lit face to\n"
                    + "  // white and the palette disappears.\n"
                    + "  vec3 sun = uSunColor * (0.62 * lit + 0.22 * mid);\n"
                    + "  shade = min(shade + base * sun, vec3(1.06));\n"
                    + "\n"
                    + "  // Rim: strongest where the surface turns away from the camera.\n"
                    + "  float rim = pow(1.0 - clamp(dot(n, viewDir), 0.0, 1.0), 3.0);\n"
                    + "  shade += base * rim * 0.34;\n"
                    + "\n"
                    + "  // A cool sheen from the sky on upward faces, so tops of foliage\n"
                    + "  // and rocks catch light the way painted surfaces do.\n"
                    + "  float skyFill = clamp(n.y * 0.5 + 0.5, 0.0, 1.0);\n"
                    + "  shade += base * vec3(0.16, 0.22, 0.34) * skyFill * 0.42;\n"
                    + "\n"
                    + "  shade += base * uEmissive;\n"
                    + "\n"
                    + "  // Aerial perspective. Squared so near geometry keeps its colour\n"
                    + "  // while the range on the horizon dissolves into the sky.\n"
                    + "  float fog = 1.0 - exp(-dist * uFogDensity);\n"
                    + "  fog = fog * fog;\n"
                    + "  vec3 c = mix(shade, uFogColor, clamp(fog, 0.0, 1.0));\n"
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

    /**
     * Sky: a vertically graded dome with a painted sun, a wide bloom around it
     * and thin streaked cloud bands.
     *
     * <p>Anime skies are built from a small number of flat, distinct colour
     * zones rather than a smooth gradient -- a warm horizon, a clean mid blue,
     * a deep zenith -- so the gradient here is quantised into soft bands. The
     * cloud streaks are procedural and static: they cost nothing per frame and
     * stop the upper sky from reading as empty.</p>
     */
    public static final String SKY_FRAG =
            "precision mediump float;\n"
                    + "varying vec2 vUv;\n"
                    + "uniform vec2 uSunScreen;\n"
                    + "uniform float uSunVisible;\n"
                    + "uniform vec3 uHorizon;\n"
                    + "uniform vec3 uZenith;\n"
                    + "uniform vec3 uSunColor;\n"
                    + "uniform float uTime;\n"
                    + "\n"
                    + "float hash(vec2 p) {\n"
                    + "  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);\n"
                    + "}\n"
                    + "\n"
                    + "// Smooth value noise, used only for cloud streaks.\n"
                    + "float vnoise(vec2 p) {\n"
                    + "  vec2 i = floor(p);\n"
                    + "  vec2 f = fract(p);\n"
                    + "  vec2 u = f * f * (3.0 - 2.0 * f);\n"
                    + "  float a = hash(i);\n"
                    + "  float b = hash(i + vec2(1.0, 0.0));\n"
                    + "  float c = hash(i + vec2(0.0, 1.0));\n"
                    + "  float d = hash(i + vec2(1.0, 1.0));\n"
                    + "  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);\n"
                    + "}\n"
                    + "\n"
                    + "void main() {\n"
                    + "  float t = clamp(vUv.y, 0.0, 1.0);\n"
                    + "  vec3 c = mix(uHorizon, uZenith, pow(t, 0.80));\n"
                    + "\n"
                    + "  // Soft banding: nudge the sky into flat zones, then blur the\n"
                    + "  // boundaries so it reads as painted rather than as a ramp.\n"
                    + "  vec3 banded = mix(uHorizon, uZenith, floor(pow(t, 0.80) * 7.0) / 7.0 + 0.05);\n"
                    + "  c = mix(c, banded, 0.45);\n"
                    + "\n"
                    + "  // Sun or moon disc, with a wide bloom and a tighter inner core.\n"
                    + "  float d = length((vUv - uSunScreen) * vec2(1.0, 1.05));\n"
                    + "  float disc = smoothstep(0.070, 0.052, d) * uSunVisible;\n"
                    + "  float bloom = pow(smoothstep(0.62, 0.0, d), 3.0) * 0.55 * uSunVisible;\n"
                    + "  float halo = pow(smoothstep(0.30, 0.0, d), 1.6) * 0.30 * uSunVisible;\n"
                    + "  c += uSunColor * (disc * 1.35 + halo) + uSunColor * vec3(1.0, 0.86, 0.72) * bloom;\n"
                    + "\n"
                    + "  // Cloud streaks. Stretched horizontally and lifted toward the\n"
                    + "  // horizon, which is where painted skies put their banding.\n"
                    + "  vec2 cp = vec2(vUv.x * 6.0 + uTime * 0.004, vUv.y * 13.0);\n"
                    + "  float cn = vnoise(cp) * 0.62 + vnoise(cp * 2.3) * 0.38;\n"
                    + "  float band = smoothstep(0.18, 0.62, t) * (1.0 - smoothstep(0.62, 0.96, t));\n"
                    + "  float cloud = smoothstep(0.58, 0.82, cn) * band * 0.55;\n"
                    + "  vec3 cloudCol = mix(uSunColor, vec3(1.0, 0.97, 0.94), 0.62);\n"
                    + "  c = mix(c, cloudCol, cloud);\n"
                    + "\n"
                    + "  // Stars, fading in as the sky darkens.\n"
                    + "  vec2 g = floor(vUv * vec2(180.0, 100.0));\n"
                    + "  float h = hash(g);\n"
                    + "  float s = step(0.9968, h) * smoothstep(0.30, 0.70, t);\n"
                    + "  float twinkle = 0.55 + 0.45 * sin(uTime * 2.2 + h * 60.0);\n"
                    + "  c += vec3(s * twinkle * 0.85);\n"
                    + "\n"
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
