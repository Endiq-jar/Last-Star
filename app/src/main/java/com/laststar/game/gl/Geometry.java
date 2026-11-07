package com.laststar.game.gl;

import com.laststar.game.core.Noise;
import com.laststar.game.core.Terrain;
import com.laststar.game.core.Vec3;

/**
 * Procedural geometry generators. Everything the game draws is produced here at
 * startup, which keeps the APK small and avoids asset pipelines.
 *
 * <p>No Android imports: these run in unit tests too.</p>
 */
public final class Geometry {

    private Geometry() {
    }

    // ---------------------------------------------------------------- terrain

    /** Irregular, glowing underside of the Hollow ceiling, lit from below. */
    public static MeshData hollowCeiling(float halfExtent, float height, int segments) {
        MeshData.Builder b = new MeshData.Builder();
        int n = segments + 1;
        for (int z = 0; z <= segments; z++) {
            for (int x = 0; x <= segments; x++) {
                float u = x / (float) segments;
                float v = z / (float) segments;
                float wx = (u * 2f - 1f) * halfExtent;
                float wz = (v * 2f - 1f) * halfExtent;
                float ridge = (float) (Math.sin(wx * 0.075f + Math.cos(wz * 0.041f))
                        * Math.cos(wz * 0.062f - wx * 0.027f));
                float y = height + ridge * 2.6f;
                float vein = Noise.smoothstep(0.91f, 0.985f,
                        0.5f + 0.5f * fastNoise(wx * 0.11f, wz * 0.11f));
                float r = 0.075f + vein * 0.24f;
                float g = 0.055f + vein * 0.10f;
                float bl = 0.17f + vein * 0.36f;
                int i = b.addVertex(wx, y, wz, 0f, -1f, 0f, r, g, bl, 1f, 0f);
                if (x < segments && z < segments) {
                    int a = i;
                    int c = i + 1;
                    int d = i + n;
                    int e = d + 1;
                    // Winding is double-sided on the GLES material; normals
                    // explicitly face the player below the ceiling.
                    b.addQuad(a, d, e, c);
                }
            }
        }
        return b.build();
    }


    /**
     * Builds the terrain mesh with baked vertex colours: grass on gentle slopes,
     * rock on steep ones, sand near the water line and snow on peaks.
     */
    public static MeshData terrain(Terrain t) {
        int n = t.segments + 1;

        MeshData.Builder b = new MeshData.Builder();
        for (int gz = 0; gz < n; gz++) {
            for (int gx = 0; gx < n; gx++) {
                float x = t.worldX(gx);
                float z = t.worldZ(gz);
                float y = t.getHeight(gx, gz);
                Vec3 normal = t.normalAt(x, z);

                float slope = 1f - Noise.clamp01(normal.y);
                float blend = (float) (t.blend[gz * n + gx] & 0xFF) / 127f;

                // Grass, rock, sand and snow blended by slope/height with a
                // little noise so the ground never reads as one flat colour.
                // Three octaves of patch noise: broad meadow variation, mid
                // scale clumps, then a fine speckle that stands in for grass
                // blade texture. Without the high frequencies a wide terrain
                // triangle lands on one raster step and the field turns into a
                // single flat plate of green.
                float p1 = Noise.clamp01(0.5f + 0.5f * fastNoise(x * 0.032f, z * 0.032f));
                float p2 = Noise.clamp01(0.5f + 0.5f * fastNoise(x * 0.14f, z * 0.14f));
                float p3 = Noise.clamp01(0.5f + 0.5f * fastNoise(x * 0.62f, z * 0.62f));
                float patch = p1;

                // Base meadow: a warm yellow-green in the sun, deepening to a
                // saturated teal-green in the hollows.
                float gr = Noise.lerp(0.30f, 0.50f, p1) + 0.035f * blend;
                float gg = Noise.lerp(0.56f, 0.78f, p1) + 0.035f * blend;
                float gb = Noise.lerp(0.26f, 0.38f, p1) + 0.030f * blend;

                // Mid-scale clumps: this is what stops the field looking like
                // a lawn and starts it looking painted.
                gr += (p2 - 0.5f) * 0.085f;
                gg += (p2 - 0.5f) * 0.105f;
                gb += (p2 - 0.5f) * 0.070f;

                // Fine speckle, kept subtle: it survives as texture at close
                // range and averages out into a clean field in the distance.
                float speck = (p3 - 0.5f) * 0.055f;
                gr += speck;
                gg += speck * 1.15f;
                gb += speck * 0.75f;

                // Dry earth: warm sand patches showing through the grass.
                float dirtT = Noise.smoothstep(0.66f, 0.94f, p2) * 0.80f;
                gr = lerp(gr, 0.76f, dirtT);
                gg = lerp(gg, 0.62f, dirtT);
                gb = lerp(gb, 0.42f, dirtT);

                // Rock on any real gradient. The threshold is deliberately low:
                // a 1-in-3 mountain face is only slope 0.5 here, and the whole
                // point of the ring is that it reads as rock, not as a green
                // hill. Grey-violet, so it sits between the grass and the sky.
                float rockT = Noise.smoothstep(0.10f, 0.34f, slope);
                gr = lerp(gr, 0.56f + 0.06f * p3, rockT);
                gg = lerp(gg, 0.58f + 0.06f * p3, rockT);
                gb = lerp(gb, 0.66f + 0.05f * p3, rockT);

                // High ground rock is warmer, like weathered granite.
                float altT = Noise.smoothstep(18f, 38f, y);
                gr = lerp(gr, 0.62f, altT * 0.55f);
                gg = lerp(gg, 0.62f, altT * 0.55f);
                gb = lerp(gb, 0.68f, altT * 0.55f);

                // Sand around the waterline.
                float sandT = Noise.smoothstep(t.waterLevel + 1.1f, t.waterLevel + 0.2f, y);
                gr = lerp(gr, 0.86f, sandT);
                gg = lerp(gg, 0.79f, sandT);
                gb = lerp(gb, 0.60f, sandT);

                // Snow on the summits only. The band is broken up by noise so
                // the snowline is ragged rather than a painted contour, and it
                // hugs the peaks: the arena floor stays green.
                float line = 40f + p1 * 9f;
                float snowT = Noise.smoothstep(line, line + 11f, y);
                gr = lerp(gr, 0.96f, snowT);
                gg = lerp(gg, 0.97f, snowT);
                gb = lerp(gb, 1.00f, snowT);

                // Gentle ambient occlusion in the valleys -- kept light so the
                // shadow side stays blue rather than going black.
                float ao = Noise.clamp01(0.74f + 0.30f * Noise.smoothstep(-2f, 30f, y));
                gr *= ao;
                gg *= ao;
                gb *= ao;

                if (t.region != null && t.region.index == 14) {
                    // Black-violet basalt with scattered luminous mineral seams.
                    float vein = Noise.smoothstep(0.955f, 0.995f, p3);
                    gr = 0.075f + vein * 0.19f;
                    gg = 0.055f + vein * 0.08f;
                    gb = 0.14f + vein * 0.33f;
                }
                b.addVertex(x, y, z, normal.x, normal.y, normal.z, gr, gg, gb, 1f, 0f);
            }
        }

        for (int gz = 0; gz < t.segments; gz++) {
            for (int gx = 0; gx < t.segments; gx++) {
                int i0 = gz * n + gx;
                int i1 = i0 + 1;
                int i2 = i0 + n;
                int i3 = i2 + 1;
                b.addTriangle(i0, i2, i1);
                b.addTriangle(i1, i2, i3);
            }
        }
        return b.build();
    }

    // ------------------------------------------------------------- mountains

    /**
     * A distant mountain range built as a ring of individual peaks.
     *
     * <p>This is the backdrop the player sees over the rim of the map. The
     * terrain itself climbs to ~68 m at the edges, but from inside the bowl that
     * reads as one hazy wall; a range of separate, sharp peaks behind it gives
     * the horizon the layered silhouette that makes the view look like a place
     * instead of a bowl. It follows the camera in X/Z (see the renderer), so a
     * single ring is all that is ever needed: it is far enough away (400 m+) to
     * have no parallax worth modelling.</p>
     *
     * <p>Each peak is a low-poly cone with a noisy radius profile, a displaced
     * rim and a snow band painted into the vertex colours. Peaks inside the ring
     * fade towards the fog colour so the range reads as several hazy layers.</p>
     */
    /**
     * Layered ranges: three concentric bands of peaks at different distances,
     * heights and haze levels.
     *
     * <p>One ring of peaks reads as a row of cones pasted on the horizon. Real
     * ranges are seen as overlapping ridgelines: near silhouettes are dark and
     * tall, far ones pale and low, and the eye reads the depth from the number
     * of those bands. This builds the three bands into one mesh with a shared
     * seed, so the whole backdrop is still a single draw call.</p>
     */
    public static MeshData layeredRange(float radius, float baseHeight, int count,
                                        long seed, float radiusSpread) {
        MeshData near = mountainRange(radius, baseHeight, count, seed, radiusSpread);
        MeshData mid = mountainRange(radius * 1.32f, baseHeight * 0.82f,
                (count * 2) / 3, seed ^ 0x9E37L, radiusSpread * 1.4f);
        MeshData far = mountainRange(radius * 1.75f, baseHeight * 0.62f,
                count / 2, seed ^ 0x51EDL, radiusSpread * 1.9f);
        return MeshData.merge(near, mid, far);
    }

    public static MeshData mountainRange(float radius, float baseHeight, int count,
                                         long seed, float radiusSpread) {
        MeshData.Builder b = new MeshData.Builder();
        java.util.Random r = new java.util.Random(seed);
        float[] fog = {0.62f, 0.72f, 0.86f};

        for (int k = 0; k < count; k++) {
            // Spread unevenly round the ring so no two peaks line up.
            double ang = (k * Math.PI * 2.0 / count) + (r.nextDouble() - 0.5) * 0.42;
            float dist = radius + (r.nextFloat() - 0.5f) * radiusSpread;
            float cx = (float) Math.cos(ang) * dist;
            float cz = (float) Math.sin(ang) * dist;
            float h = baseHeight * (0.70f + r.nextFloat() * 0.60f);
            // Base width against height. Too narrow and the range reads as
            // teeth; too wide and it flattens into a mound. Roughly 2:1 works.
            float w = h * (0.40f + r.nextFloat() * 0.26f);

            // How far back this peak sits: 0 = nearest ring, 1 = furthest.
            float back = Noise.clamp01((dist - radius + radiusSpread * 0.5f)
                    / Math.max(radiusSpread, 0.001f));
            // Nearer peaks are darker and sharper; far ones dissolve into haze.
            float haze = 0.10f + 0.42f * back;

            // More sides: a 5-sided cone reads as a paper party hat. Eight to
            // twelve facets with a strong per-facet radial jitter gives the
            // asymmetric, chiselled profile of painted mountain ranges.
            int sides = 8 + r.nextInt(5);
            float[] jitter = new float[sides];
            // Two jitter scales: one per facet for the chisel, and one shared
            // pair per peak so the whole mountain is pushed off-centre in a
            // direction rather than being a symmetric cone with noisy edges.
            float shear = (r.nextFloat() - 0.5f) * 0.34f;
            float lean = (r.nextFloat() - 0.5f) * 0.26f;
            for (int i = 0; i < sides; i++) {
                float wobble = (i % 2 == 0) ? 1.0f : 0.86f;
                jitter[i] = (0.72f + r.nextFloat() * 0.52f) * wobble;
            }
            // A slightly off-centre apex gives the classic asymmetric peak.
            float apexX = cx + shear * w * 1.9f + (r.nextFloat() - 0.5f) * w * 0.22f;
            float apexZ = cz + lean * w * 1.9f + (r.nextFloat() - 0.5f) * w * 0.22f;
            float snowline = h * (0.64f + r.nextFloat() * 0.14f);

            int base = b.vertexCount();
            for (int i = 0; i < sides; i++) {
                double a = i * Math.PI * 2.0 / sides;
                float rx = (float) Math.cos(a) * w * jitter[i];
                float rz = (float) Math.sin(a) * w * jitter[i];
                // Lean the whole peak: the rim drifts as it climbs, so the
                // ridge lines converge off-axis.
                float x = cx + rx + shear * w;
                float z = cz + rz + lean * w;

                // Rock colour: deep cool slate, hazed towards the sky by
                // distance. The near range stays dark enough to silhouette
                // against the sky; only the back layer goes properly pale.
                float rockR = Noise.lerp(0.13f, 0.40f, back);
                float rockG = Noise.lerp(0.15f, 0.46f, back);
                float rockB = Noise.lerp(0.23f, 0.60f, back);
                float cr = Noise.lerp(rockR, fog[0], haze);
                float cg = Noise.lerp(rockG, fog[1], haze);
                float cb = Noise.lerp(rockB, fog[2], haze);
                Vec3 n = new Vec3(rx, w * 0.55f, rz).normalized();
                b.addVertex(x, -2f, z, n.x, n.y, n.z, cr, cg, cb, 1f, 0f);

                // Snow cap ring: same rim, lifted to the snowline.
                float capR = (rx + shear * w) * 0.42f + shear * w * 0.5f;
                float capZ = (rz + lean * w) * 0.42f + lean * w * 0.5f;
                float sc = 0.84f + r.nextFloat() * 0.08f;
                b.addVertex(cx + capR, snowline, cz + capZ,
                        n.x, n.y, n.z, sc, sc, Noise.clamp01(sc + 0.06f), 1f, 0f);
            }
            // Apex, white.
            b.addVertex(apexX, h, apexZ, 0f, 1f, 0f, 0.97f, 0.98f, 1f, 1f, 0f);

            int apexIndex = base + sides * 2;
            for (int i = 0; i < sides; i++) {
                int nxt = (i + 1) % sides;
                int b0 = base + i * 2;
                int b1 = base + nxt * 2;
                int c0 = b0 + 1;
                int c1 = b1 + 1;
                // Lower rock face.
                b.addTriangle(b0, c0, b1);
                b.addTriangle(b1, c0, c1);
                // Snow pyramid on top.
                b.addTriangle(c0, apexIndex, c1);
            }
        }
        return b.build();
    }

    // ------------------------------------------------------------ grass tufts

    /**
     * Wind-pushed grass and flower tufts, generated as a handful of crossed
     * blades.
     *
     * <p>Anime fields read as movement as much as colour: the sway is what
     * stops a painted meadow looking like green carpet. Each blade is two
     * triangles with the wave weight baked into the vertex attribute, so the
     * vertex shader can bend the tip while the root stays planted -- no bones,
     * no skinning, no per-frame CPU work.</p>
     *
     * @param blades  number of crossed blades per tuft
     * @param height  blade height in metres
     * @param spread  horizontal radius of the tuft
     * @param r,g,b   base colour at the root
     * @param tipR,tipG,tipB colour at the tip (anime grass always lightens)
     */
    public static MeshData tuft(int blades, float height, float spread,
                                float r, float g, float b,
                                float tipR, float tipG, float tipB, long seed) {
        MeshData.Builder bb = new MeshData.Builder();
        java.util.Random rnd = new java.util.Random(seed);
        for (int i = 0; i < blades; i++) {
            double ang = (i / (double) blades) * Math.PI + rnd.nextDouble() * 0.5;
            float cx = (float) Math.cos(ang);
            float cz = (float) Math.sin(ang);
            float px = -cz;
            float pz = cx;
            float w = spread * (0.16f + rnd.nextFloat() * 0.10f);
            float h = height * (0.72f + rnd.nextFloat() * 0.55f);
            float ox = (rnd.nextFloat() - 0.5f) * spread * 0.45f;
            float oz = (rnd.nextFloat() - 0.5f) * spread * 0.45f;
            // A gentle lean, so the tuft is not perfectly vertical.
            float leanX = (rnd.nextFloat() - 0.5f) * height * 0.28f;
            float leanZ = (rnd.nextFloat() - 0.5f) * height * 0.28f;
            float phase = rnd.nextFloat() * 6.283f;
            // Slight outward bow, which reads as volume from any angle.
            float bow = spread * 0.30f;

            int base = bb.vertexCount();
            bb.addVertex(ox - px * w, 0f, oz - pz * w, px, 0.35f, pz, r, g, b, 1f, 0f);
            bb.addVertex(ox + px * w, 0f, oz + pz * w, -px, 0.35f, -pz, r, g, b, 1f, 0f);
            bb.addVertex(ox + cx * bow + leanX, h, oz + cz * bow + leanZ,
                    cx, 0.8f, cz, tipR, tipG, tipB, 1f, phase);
            bb.addTriangle(base, base + 1, base + 2);
        }
        return bb.build();
    }

    // ----------------------------------------------------------------- water

    /** Flat plane subdivided so the vertex shader can ripple it. */
    public static MeshData waterPlane(float size, int segments, float level) {
        MeshData.Builder b = new MeshData.Builder();
        float half = size * 0.5f;
        float cell = size / segments;
        int n = segments + 1;
        for (int gz = 0; gz < n; gz++) {
            for (int gx = 0; gx < n; gx++) {
                float x = gx * cell - half;
                float z = gz * cell - half;
                float distToCentre = (float) Math.sqrt(x * x + z * z);
                float wave = distToCentre * 0.35f;
                b.addVertex(x, level, z, 0f, 1f, 0f, 0.22f, 0.52f, 0.72f, 0.74f, wave);
            }
        }
        for (int gz = 0; gz < segments; gz++) {
            for (int gx = 0; gx < segments; gx++) {
                int i0 = gz * n + gx;
                int i1 = i0 + 1;
                int i2 = i0 + n;
                int i3 = i2 + 1;
                b.addQuad(i0, i1, i3, i2);
            }
        }
        return b.build();
    }

    // ---------------------------------------------------------------- shapes

    /** Axis-aligned box centred at the origin (optionally offset). */
    public static MeshData box(float sx, float sy, float sz,
                               float r, float g, float b, float a,
                               float ox, float oy, float oz) {
        MeshData.Builder mb = new MeshData.Builder();
        float hx = sx * 0.5f, hy = sy * 0.5f, hz = sz * 0.5f;
        addBoxFace(mb, new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), hx, hy, hz, r, g, b, a, ox, oy, oz);
        addBoxFace(mb, new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, -1), hx, hy, hz, r, g, b, a, ox, oy, oz);
        addBoxFace(mb, new Vec3(0, 1, 0), new Vec3(0, 0, 1), new Vec3(1, 0, 0), hx, hy, hz, r, g, b, a, ox, oy, oz);
        addBoxFace(mb, new Vec3(0, -1, 0), new Vec3(0, 0, -1), new Vec3(1, 0, 0), hx, hy, hz, r, g, b, a, ox, oy, oz);
        addBoxFace(mb, new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(-1, 0, 0), hx, hy, hz, r, g, b, a, ox, oy, oz);
        addBoxFace(mb, new Vec3(0, 0, -1), new Vec3(0, 1, 0), new Vec3(1, 0, 0), hx, hy, hz, r, g, b, a, ox, oy, oz);
        return mb.build();
    }

    private static void addBoxFace(MeshData.Builder b, Vec3 n, Vec3 up, Vec3 right,
                                   float hx, float hy, float hz,
                                   float r, float g, float bl, float a,
                                   float ox, float oy, float oz) {
        float ex = Math.abs(right.x) * hx + Math.abs(up.x) * hy + Math.abs(n.x) * hz;
        float ey = Math.abs(right.y) * hx + Math.abs(up.y) * hy + Math.abs(n.y) * hz;
        float ez = Math.abs(right.z) * hx + Math.abs(up.z) * hy + Math.abs(n.z) * hz;
        Vec3 c = new Vec3(n.x * ex + ox, n.y * ey + oy, n.z * ez + oz);
        Vec3 ru = right.mul(Math.abs(right.x) > 0.5f ? hx : (Math.abs(right.y) > 0.5f ? hy : hz));
        Vec3 uu = up.mul(Math.abs(up.x) > 0.5f ? hx : (Math.abs(up.y) > 0.5f ? hy : hz));
        Vec3 p0 = c.sub(ru).sub(uu);
        Vec3 p1 = c.add(ru).sub(uu);
        Vec3 p2 = c.add(ru).add(uu);
        Vec3 p3 = c.sub(ru).add(uu);
        int i0 = b.addVertex(p0, n, r, g, bl, a, 0f);
        int i1 = b.addVertex(p1, n, r * 0.94f, g * 0.94f, bl * 0.94f, a, 0f);
        int i2 = b.addVertex(p2, n, r, g, bl, a, 0f);
        int i3 = b.addVertex(p3, n, r * 0.9f, g * 0.9f, bl * 0.9f, a, 0f);
        b.addQuad(i0, i1, i2, i3);
    }

    /** Vertical cylinder / tapered frustum with caps, base at y = 0. */
    public static MeshData cylinder(float bottomRadius, float topRadius, float height,
                                    int segments, float r, float g, float b, float a) {
        MeshData.Builder mb = new MeshData.Builder();
        for (int s = 0; s < segments; s++) {
            float t0 = (float) (s * 2.0 * Math.PI / segments);
            float t1 = (float) ((s + 1) * 2.0 * Math.PI / segments);
            Vec3 d0 = new Vec3((float) Math.cos(t0), 0f, (float) Math.sin(t0));
            Vec3 d1 = new Vec3((float) Math.cos(t1), 0f, (float) Math.sin(t1));
            Vec3 p00 = d0.mul(bottomRadius);
            Vec3 p01 = d1.mul(bottomRadius);
            Vec3 p10 = d0.mul(topRadius).withY(height);
            Vec3 p11 = d1.mul(topRadius).withY(height);
            Vec3 n0 = new Vec3(d0.x, 0f, d0.z);
            Vec3 n1 = new Vec3(d1.x, 0f, d1.z);
            int i0 = mb.addVertex(p00, n0, r * 0.85f, g * 0.85f, b * 0.85f, a, 0f);
            int i1 = mb.addVertex(p01, n1, r * 0.85f, g * 0.85f, b * 0.85f, a, 0f);
            int i2 = mb.addVertex(p11, n1, r, g, b, a, 0f);
            int i3 = mb.addVertex(p10, n0, r, g, b, a, 0f);
            mb.addQuad(i0, i1, i2, i3);
        }
        // Top cap.
        if (topRadius > 0.001f) {
            int centre = mb.addVertex(new Vec3(0f, height, 0f), Vec3.UP, r, g, b, a, 0f);
            int first = mb.vertexCount();
            for (int s = 0; s < segments; s++) {
                float t = (float) (s * 2.0 * Math.PI / segments);
                Vec3 p = new Vec3((float) Math.cos(t) * topRadius, height, (float) Math.sin(t) * topRadius);
                mb.addVertex(p, Vec3.UP, r, g, b, a, 0f);
            }
            for (int s = 0; s < segments; s++) {
                mb.addTriangle(centre, first + s, first + (s + 1) % segments);
            }
        }
        return mb.build();
    }

    /** Lat/long UV sphere. */
    public static MeshData sphere(float radius, int lat, int lon,
                                  float r, float g, float b, float a) {
        MeshData.Builder mb = new MeshData.Builder();
        for (int iy = 0; iy <= lat; iy++) {
            float v = (float) iy / lat;
            float phi = v * (float) Math.PI;
            float y = (float) Math.cos(phi);
            float sinPhi = (float) Math.sin(phi);
            for (int ix = 0; ix <= lon; ix++) {
                float u = (float) ix / lon;
                float theta = u * (float) (Math.PI * 2.0);
                Vec3 n = new Vec3(sinPhi * (float) Math.cos(theta), y, sinPhi * (float) Math.sin(theta));
                Vec3 p = n.mul(radius);
                mb.addVertex(p, n, r, g, b, a, 0f);
            }
        }
        int row = lon + 1;
        for (int iy = 0; iy < lat; iy++) {
            for (int ix = 0; ix < lon; ix++) {
                int a0 = iy * row + ix;
                int a1 = a0 + 1;
                int b0 = a0 + row;
                int b1 = b0 + 1;
                mb.addTriangle(a0, b0, a1);
                mb.addTriangle(a1, b0, b1);
            }
        }
        return mb.build();
    }

    /** Low-poly octahedron used for the star shards. */
    public static MeshData octahedron(float radius, float r0, float g0, float b0) {
        MeshData.Builder mb = new MeshData.Builder();
        Vec3[] dirs = {
                new Vec3(0, 1, 0), new Vec3(0, -1, 0),
                new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                new Vec3(0, 0, 1), new Vec3(0, 0, -1)
        };
        int[][] tris = {
                {0, 2, 4}, {0, 4, 3}, {0, 3, 5}, {0, 5, 2},
                {1, 4, 2}, {1, 3, 4}, {1, 5, 3}, {1, 2, 5}
        };
        for (int[] t : tris) {
            Vec3 a = dirs[t[0]].mul(radius);
            Vec3 bb = dirs[t[1]].mul(radius);
            Vec3 c = dirs[t[2]].mul(radius);
            Vec3 n = bb.sub(a).cross(c.sub(a)).normalized();
            int i0 = mb.addVertex(a, n, r0, g0, b0, 1f, 0f);
            int i1 = mb.addVertex(bb, n, r0, g0, b0, 1f, 0f);
            int i2 = mb.addVertex(c, n, r0, g0, b0, 1f, 0f);
            mb.addTriangle(i0, i1, i2);
        }
        return mb.build();
    }

    /** A 6-pointed spike star, the game's logo shape, used for icons and HUD. */
    public static MeshData starShard(float radius, float r, float g, float b) {
        MeshData.Builder mb = new MeshData.Builder();
        int points = 5;
        Vec3 top = new Vec3(0f, radius * 1.35f, 0f);
        Vec3 bottom = new Vec3(0f, -radius * 1.35f, 0f);
        for (int i = 0; i < points * 2; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / (points * 2));
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / (points * 2));
            float rad0 = (i % 2 == 0) ? radius : radius * 0.42f;
            float rad1 = (i % 2 == 0) ? radius * 0.42f : radius;
            Vec3 p0 = new Vec3((float) Math.cos(a0) * rad0, 0f, (float) Math.sin(a0) * rad0);
            Vec3 p1 = new Vec3((float) Math.cos(a1) * rad1, 0f, (float) Math.sin(a1) * rad1);
            Vec3 n0 = new Vec3(p0.x, 0.6f, p0.z).normalized();
            int i0 = mb.addVertex(top, Vec3.UP, r, g, b, 1f, 0f);
            int i1 = mb.addVertex(p0, n0, r, g, b, 1f, 0f);
            int i2 = mb.addVertex(p1, n0, r, g, b, 1f, 0f);
            mb.addTriangle(i0, i1, i2);
            int j0 = mb.addVertex(bottom, new Vec3(0f, -1f, 0f), r * 0.8f, g * 0.8f, b * 0.8f, 1f, 0f);
            int j1 = mb.addVertex(p1, n0, r * 0.8f, g * 0.8f, b * 0.8f, 1f, 0f);
            int j2 = mb.addVertex(p0, n0, r * 0.8f, g * 0.8f, b * 0.8f, 1f, 0f);
            mb.addTriangle(j0, j1, j2);
        }
        return mb.build();
    }

    /** Camera-facing quad for particles and billboards, centred on the origin. */
    public static MeshData quad(float size, float r, float g, float b, float a) {
        MeshData.Builder mb = new MeshData.Builder();
        float h = size * 0.5f;
        Vec3 n = new Vec3(0f, 0f, 1f);
        int i0 = mb.addVertex(new Vec3(-h, -h, 0f), n, r, g, b, a, 0f);
        int i1 = mb.addVertex(new Vec3(h, -h, 0f), n, r, g, b, a, 0f);
        int i2 = mb.addVertex(new Vec3(h, h, 0f), n, r, g, b, a, 0f);
        int i3 = mb.addVertex(new Vec3(-h, h, 0f), n, r, g, b, a, 0f);
        mb.addQuad(i0, i1, i2, i3);
        return mb.build();
    }

    /** Ground ring used as the melee impact decal and shard highlight. */
    public static MeshData groundRing(float radius, float thickness, int segments,
                                      float r, float g, float b, float a) {
        MeshData.Builder mb = new MeshData.Builder();
        for (int s = 0; s < segments; s++) {
            float t0 = (float) (s * 2.0 * Math.PI / segments);
            float t1 = (float) ((s + 1) * 2.0 * Math.PI / segments);
            Vec3 d0 = new Vec3((float) Math.cos(t0), 0f, (float) Math.sin(t0));
            Vec3 d1 = new Vec3((float) Math.cos(t1), 0f, (float) Math.sin(t1));
            int i0 = mb.addVertex(d0.mul(radius - thickness), Vec3.UP, r, g, b, a, 0f);
            int i1 = mb.addVertex(d1.mul(radius - thickness), Vec3.UP, r, g, b, a, 0f);
            int i2 = mb.addVertex(d1.mul(radius), Vec3.UP, r, g, b, a, 0f);
            int i3 = mb.addVertex(d0.mul(radius), Vec3.UP, r, g, b, a, 0f);
            mb.addQuad(i0, i1, i2, i3);
        }
        return mb.build();
    }

    /**
     * Ground beyond the playable map.
     *
     * <p>The terrain is a finite 220 m square. Without this, a camera looking
     * out from the middle of the map sees the terrain end and the distant
     * mountain ring hanging in the sky beyond it. The skirt is a fan that
     * starts at the terrain edge, stays flat for the near band so the two
     * meet, then sinks away so its far rim is hidden behind the peaks.</p>
     *
     * @param inner      radius of the playable area, where it must match
     * @param outer      radius of the far rim
     * @param innerY     ground height at the boundary
     * @param drop       how far the rim sinks below {@code innerY}
     */
    public static MeshData distantGround(float inner, float outer, int segments,
                                         float innerY, float drop,
                                         float r, float g, float b) {
        MeshData.Builder mb = new MeshData.Builder();
        float mid = inner + (outer - inner) * 0.35f;
        float midY = innerY - drop * 0.10f;
        float rimY = innerY - drop;
        for (int s = 0; s < segments; s++) {
            float t0 = (float) (s * 2.0 * Math.PI / segments);
            float t1 = (float) ((s + 1) * 2.0 * Math.PI / segments);
            float c0 = (float) Math.cos(t0), s0 = (float) Math.sin(t0);
            float c1 = (float) Math.cos(t1), s1 = (float) Math.sin(t1);
            // Two bands: the near one is very slightly dark, so the seam reads
            // as a change in ground cover rather than a hard edge.
            int a = mb.addVertex(c0 * inner, innerY, s0 * inner, 0f, 1f, 0f,
                    r * 0.97f, g * 0.98f, b * 0.99f, 1f, 0f);
            int bb = mb.addVertex(c1 * inner, innerY, s1 * inner, 0f, 1f, 0f,
                    r * 0.97f, g * 0.98f, b * 0.99f, 1f, 0f);
            int c = mb.addVertex(c1 * mid, midY, s1 * mid, 0f, 1f, 0f, r, g, b, 1f, 0f);
            int d = mb.addVertex(c0 * mid, midY, s0 * mid, 0f, 1f, 0f, r, g, b, 1f, 0f);
            mb.addQuad(a, bb, c, d);

            int e = mb.addVertex(c0 * mid, midY, s0 * mid, 0f, 1f, 0f, r, g, b, 1f, 0f);
            int f = mb.addVertex(c1 * mid, midY, s1 * mid, 0f, 1f, 0f, r, g, b, 1f, 0f);
            int h = mb.addVertex(c1 * outer, rimY, s1 * outer, 0f, 1f, 0f,
                    r * 0.88f, g * 0.90f, b * 0.96f, 1f, 0f);
            int i = mb.addVertex(c0 * outer, rimY, s0 * outer, 0f, 1f, 0f,
                    r * 0.88f, g * 0.90f, b * 0.96f, 1f, 0f);
            mb.addQuad(e, f, h, i);
        }
        return mb.build();
    }

    /** Cone (base at y = 0). */
    public static MeshData cone(float radius, float height, int segments,
                                float r, float g, float b, float a) {
        MeshData.Builder mb = new MeshData.Builder();
        Vec3 apex = new Vec3(0f, height, 0f);
        for (int s = 0; s < segments; s++) {
            float t0 = (float) (s * 2.0 * Math.PI / segments);
            float t1 = (float) ((s + 1) * 2.0 * Math.PI / segments);
            Vec3 p0 = new Vec3((float) Math.cos(t0) * radius, 0f, (float) Math.sin(t0) * radius);
            Vec3 p1 = new Vec3((float) Math.cos(t1) * radius, 0f, (float) Math.sin(t1) * radius);
            Vec3 n = p1.sub(p0).cross(apex.sub(p0)).normalized();
            int i0 = mb.addVertex(apex, n, r, g, b, a, 0f);
            int i1 = mb.addVertex(p0, n, r * 0.8f, g * 0.8f, b * 0.8f, a, 0f);
            int i2 = mb.addVertex(p1, n, r * 0.8f, g * 0.8f, b * 0.8f, a, 0f);
            mb.addTriangle(i0, i1, i2);
        }
        return mb.build();
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Cheap deterministic hash-noise used to break up flat surfaces. */
    private static float fastNoise(float x, float y) {
        float v = (float) Math.sin(x * 127.1f + y * 311.7f) * 43758.5453f;
        v = v - (float) Math.floor(v);
        float w = (float) Math.sin(x * 269.5f - y * 183.3f) * 24634.6345f;
        w = w - (float) Math.floor(w);
        return v * 0.6f + w * 0.4f;
    }
}
