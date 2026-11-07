import com.laststar.game.core.Mat4;
import com.laststar.game.core.Vec3;

/**
 * A small, correct software rasteriser.
 *
 * <p>It exists so the game's real geometry, poses and shading can be rendered to
 * PNGs on a machine with no GPU and no Android device: proof that the meshes,
 * transforms and camera code produce sensible images, and a way to generate the
 * screenshots in the repository.</p>
 *
 * <p>Pipeline: model -> world -> view, near-plane clip, projection, screen
 * mapping, z-buffered flat shading with fog and alpha blending.</p>
 */
public final class Raster {

    public final int width;
    public final int height;
    public final int[] argb;
    public final float[] depth;

    private final Mat4 view;
    private final Mat4 proj;
    private final float[] sun = new float[3];
    private final float[] ambient = new float[3];
    private final float[] fogColour = new float[3];
    private float fogDensity = 0.007f;
    private float near = 0.08f;
    private Vec3 camPos = Vec3.ZERO;

    /** Camera position in world space, used for distance fog. */
    public void setCameraPosition(Vec3 p) {
        camPos = p;
    }

    public Raster(int width, int height, Mat4 view, Mat4 proj) {
        this.width = width;
        this.height = height;
        this.argb = new int[width * height];
        this.depth = new float[width * height];
        java.util.Arrays.fill(depth, Float.MAX_VALUE);
        this.view = view;
        this.proj = proj;
    }

    public void setSun(float x, float y, float z, float r, float g, float b) {
        sun[0] = x;
        sun[1] = y;
        sun[2] = z;
        sunR = r;
        sunG = g;
        sunB = b;
    }

    public void setAmbient(float r, float g, float b) {
        ambient[0] = r;
        ambient[1] = g;
        ambient[2] = b;
    }

    public void setFog(float density, float r, float g, float b) {
        fogDensity = density;
        fogColour[0] = r;
        fogColour[1] = g;
        fogColour[2] = b;
    }

    public void setNear(float n) {
        near = n;
    }

    private float sunR = 1f;
    private float waveTime = 0f;
    private float waveAmp = 0f;

    /** Enables the wind bend used by grass, flowers and foliage. */
    public void setWind(float time, float amp) {
        waveTime = time;
        waveAmp = amp;
    }
    private float sunG = 0.9f;
    private float sunB = 0.75f;

    // ------------------------------------------------------------------ clear

    private float skyTime = 0f;
    private float starVisibility = 1f;

    /** 0 hides stars entirely (daylight); 1 shows them all. */
    public void setStars(float v) {
        starVisibility = clamp01(v);
    }

    /** Advances the cloud drift in the painted sky. */
    public void setSkyTime(float t) {
        skyTime = t;
    }

    /**
     * Painted sky: banded vertical gradient, sun with bloom, cloud streaks and
     * stars. Kept in step with {@code Shaders.SKY_FRAG} so screenshots show
     * what a phone renders.
     */
    public void drawSky(float horizonR, float horizonG, float horizonB,
                        float zenithR, float zenithG, float zenithB,
                        float sunScreenX, float sunScreenY, float sunRadius,
                        float sR, float sG, float sB, float sunVisible) {
        for (int y = 0; y < height; y++) {
            float t = 1f - (float) y / height;
            float powT = (float) Math.pow(t, 0.80);
            float r = lerp(horizonR, zenithR, powT);
            float g = lerp(horizonG, zenithG, powT);
            float b = lerp(horizonB, zenithB, powT);
            // Soft banding: flat zones with blurred boundaries.
            float bt = (float) (Math.floor(powT * 7.0) / 7.0 + 0.05);
            r = lerp(r, lerp(horizonR, zenithR, bt), 0.45f);
            g = lerp(g, lerp(horizonG, zenithG, bt), 0.45f);
            b = lerp(b, lerp(horizonB, zenithB, bt), 0.45f);

            for (int x = 0; x < width; x++) {
                float rr = r;
                float gg = g;
                float bb = b;
                if (sunVisible > 0.5f) {
                    float dx = (x - sunScreenX) / sunRadius;
                    float dy = (y - sunScreenY) / (sunRadius * 1.05f);
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    float disc = smoothstep(0.070f * (sunRadius / 40f), 0.052f * (sunRadius / 40f), d * 0.22f);
                    float bloom = (float) Math.pow(smoothstep(0.62f, 0f, d * 0.22f), 3.0) * 0.55f;
                    float halo = (float) Math.pow(smoothstep(0.30f, 0f, d * 0.22f), 1.6) * 0.30f;
                    rr += sR * (disc * 1.35f + halo) + sR * 0.86f * bloom;
                    gg += sG * (disc * 1.35f + halo) + sG * 0.86f * bloom * 0.94f;
                    bb += sB * (disc * 1.35f + halo) + sB * 0.72f * bloom * 0.90f;
                }

                // Cloud streaks: stretched noise in the mid sky, drifting.
                float band = smoothstep(0.18f, 0.62f, t) * (1f - smoothstep(0.62f, 0.96f, t));
                if (band > 0.002f) {
                    float nx = x * 6.0f / width + skyTime * 0.004f;
                    float ny = y * 13.0f / height;
                    float cn = vnoise(nx, ny) * 0.62f + vnoise(nx * 2.3f, ny * 2.3f) * 0.38f;
                    float cloud = smoothstep(0.58f, 0.82f, cn) * band * 0.55f;
                    if (cloud > 0.002f) {
                        float cr = lerp(sR, 1f, 0.62f);
                        float cg = lerp(sG, 0.97f, 0.62f);
                        float cbn = lerp(sB, 0.94f, 0.62f);
                        rr = lerp(rr, cr, cloud);
                        gg = lerp(gg, cg, cloud);
                        bb = lerp(bb, cbn, cloud);
                    }
                }

                // Stars in the upper sky.
                if (starVisibility > 0.01f && y < height * 0.62f) {
                    float hsh = (float) (Math.sin(x * 12.9898 + y * 78.233) * 43758.5453);
                    hsh -= (float) Math.floor(hsh);
                    if (hsh > 0.9968f) {
                        float k = 0.55f + 0.45f * (float) Math.sin(skyTime * 2.2 + hsh * 60f);
                        float fade = smoothstep(0.30f, 0.70f, t);
                        float sv = k * 0.85f * fade * starVisibility;
                        rr += sv;
                        gg += sv;
                        bb += sv;
                    }
                }
                argb[y * width + x] = pack(rr, gg, bb, 1f);
            }
        }
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0 + 1e-9f));
        return t * t * (3f - 2f * t);
    }

    /** Deterministic value noise, used for cloud streaks. */
    private static float vnoise(float x, float y) {
        float fx = (float) Math.floor(x);
        float fy = (float) Math.floor(y);
        float ax = x - fx;
        float ay = y - fy;
        float ux = ax * ax * (3f - 2f * ax);
        float uy = ay * ay * (3f - 2f * ay);
        float a = hash2(fx, fy);
        float b = hash2(fx + 1f, fy);
        float c = hash2(fx, fy + 1f);
        float d = hash2(fx + 1f, fy + 1f);
        return lerp(lerp(a, b, ux), lerp(c, d, ux), uy);
    }

    private static float hash2(float x, float y) {
        float v = (float) Math.sin(x * 127.1f + y * 311.7f) * 43758.5453f;
        return v - (float) Math.floor(v);
    }

    public void clearDepth() {
        java.util.Arrays.fill(depth, Float.MAX_VALUE);
    }

    // -------------------------------------------------------------- geometry

    /**
     * A vertex on its way through the pipeline: world position, normal and
     * colour. Carrying attributes through the clipper is what lets the fill
     * shade per pixel instead of per triangle.
     */
    private static final class V {
        float x;
        float y;
        float z;
        float nx;
        float ny;
        float nz;
        float r;
        float g;
        float b;
        float a;

        V copy() {
            V v = new V();
            v.x = x;
            v.y = y;
            v.z = z;
            v.nx = nx;
            v.ny = ny;
            v.nz = nz;
            v.r = r;
            v.g = g;
            v.b = b;
            v.a = a;
            return v;
        }

        static V lerp(V p, V q, float t) {
            V v = new V();
            v.x = p.x + (q.x - p.x) * t;
            v.y = p.y + (q.y - p.y) * t;
            v.z = p.z + (q.z - p.z) * t;
            v.nx = p.nx + (q.nx - p.nx) * t;
            v.ny = p.ny + (q.ny - p.ny) * t;
            v.nz = p.nz + (q.nz - p.nz) * t;
            v.r = p.r + (q.r - p.r) * t;
            v.g = p.g + (q.g - p.g) * t;
            v.b = p.b + (q.b - p.b) * t;
            v.a = p.a + (q.a - p.a) * t;
            return v;
        }
    }

    /** A mesh instance with a model matrix. */
    public void drawMesh(float[] positions, float[] normals, float[] colors, short[] indices,
                         Mat4 model, float alpha, float emissive, float[] tint, float tintAmount) {
        drawMesh(positions, normals, colors, indices, model, alpha, emissive, tint, tintAmount,
                false);
    }

    /**
     * Draws a mesh with per-pixel shading: attributes are interpolated across
     * the triangle exactly as the rasteriser in the driver does, so the toon
     * ramp lands on the same pixels here as it does on a phone.
     *
     * <p>When {@code unlit} is true the interpolated vertex colour is used
     * directly, which is what the GPU does for sky, water highlights and
     * emissive effect geometry.</p>
     */
    public void drawMesh(float[] positions, float[] normals, float[] colors, short[] indices,
                         Mat4 model, float alpha, float emissive, float[] tint, float tintAmount,
                         boolean unlit) {
        int tris = indices.length / 3;
        V[] tri = new V[3];
        for (int t = 0; t < tris; t++) {
            int i0 = indices[t * 3] & 0xFFFF;
            int i1 = indices[t * 3 + 1] & 0xFFFF;
            int i2 = indices[t * 3 + 2] & 0xFFFF;
            tri[0] = vertex(model, positions, normals, colors, i0, alpha, emissive, tint, tintAmount);
            tri[1] = vertex(model, positions, normals, colors, i1, alpha, emissive, tint, tintAmount);
            tri[2] = vertex(model, positions, normals, colors, i2, alpha, emissive, tint, tintAmount);
            shadeTriangle(tri[0], tri[1], tri[2], unlit);
        }
    }

    /** Per-vertex wave phase, set alongside the mesh buffers. */
    private float[] wv = new float[0];

    /** Supplies the per-vertex wave phase used by {@link #setWind}. */
    public void setWaveAttribute(float[] wave) {
        wv = wave == null ? new float[0] : wave;
    }

    private V vertex(Mat4 m, float[] pos, float[] nrm, float[] col, int i,
                     float alpha, float emissive, float[] tint, float tintAmount) {
        V v = new V();
        float x = pos[i * 3];
        float y = pos[i * 3 + 1];
        float z = pos[i * 3 + 2];
        if (waveAmp > 0f) {
            // Mirrors the vertex shader's wave term. The attribute is the
            // phase, carried per vertex by Geometry.tuft and the model baker.
            float phase = wv[i < wv.length ? i : 0];
            if (phase != 0f) {
                y += (float) (Math.sin(waveTime * 1.35f + phase)
                        + Math.sin(waveTime * 0.71f + phase * 1.7f) * 0.55f) * waveAmp;
                x += (float) Math.cos(waveTime * 0.9f + phase * 0.8f) * waveAmp * 0.35f;
            }
        }
        v.x = m.m[0] * x + m.m[4] * y + m.m[8] * z + m.m[12];
        v.y = m.m[1] * x + m.m[5] * y + m.m[9] * z + m.m[13];
        v.z = m.m[2] * x + m.m[6] * y + m.m[10] * z + m.m[14];
        float nx = nrm[i * 3];
        float ny = nrm[i * 3 + 1];
        float nz = nrm[i * 3 + 2];
        v.nx = m.m[0] * nx + m.m[4] * ny + m.m[8] * nz;
        v.ny = m.m[1] * nx + m.m[5] * ny + m.m[9] * nz;
        v.nz = m.m[2] * nx + m.m[6] * ny + m.m[10] * nz;
        float r = col[i * 4];
        float g = col[i * 4 + 1];
        float b = col[i * 4 + 2];
        if (tint != null && tintAmount > 0f) {
            r = r + (tint[0] - r) * tintAmount;
            g = g + (tint[1] - g) * tintAmount;
            b = b + (tint[2] - b) * tintAmount;
        }
        // Fold the emissive term into the colour: it is a flat add, so it rides
        // along with the interpolated attribute for free.
        v.r = r * (1f + emissive);
        v.g = g * (1f + emissive);
        v.b = b * (1f + emissive);
        v.a = col[i * 4 + 3] * alpha;
        return v;
    }

    /** Near-plane clip, then fan the resulting polygon through the filler. */
    private void shadeTriangle(V a, V b, V c, boolean unlit) {
        V[] poly = {toView(a), toView(b), toView(c)};
        V[] clipped = clipNear(poly);
        if (clipped == null) {
            return;
        }
        for (int i = 1; i + 1 < clipped.length; i++) {
            fillGouraud(clipped[0], clipped[i], clipped[i + 1], unlit);
        }
    }

    private V toView(V world) {
        V v = world.copy();
        v.x = view.m[0] * world.x + view.m[4] * world.y + view.m[8] * world.z + view.m[12];
        v.y = view.m[1] * world.x + view.m[5] * world.y + view.m[9] * world.z + view.m[13];
        v.z = view.m[2] * world.x + view.m[6] * world.y + view.m[10] * world.z + view.m[14];
        // The normal is already in world space; the view matrix is a rigid
        // transform, so rotate it the same way for the rim term.
        float nx = world.nx;
        float ny = world.ny;
        float nz = world.nz;
        v.nx = view.m[0] * nx + view.m[4] * ny + view.m[8] * nz;
        v.ny = view.m[1] * nx + view.m[5] * ny + view.m[9] * nz;
        v.nz = view.m[2] * nx + view.m[6] * ny + view.m[10] * nz;
        return v;
    }

    /** Sutherland-Hodgman against the single near plane z = -near. */
    private V[] clipNear(V[] poly) {
        float limit = -near;
        V[] out = new V[poly.length + 1];
        int count = 0;
        for (int i = 0; i < poly.length; i++) {
            V cur = poly[i];
            V nxt = poly[(i + 1) % poly.length];
            boolean curIn = cur.z < limit;
            boolean nxtIn = nxt.z < limit;
            if (curIn) {
                out[count++] = cur;
            }
            if (curIn != nxtIn) {
                float t = (limit - cur.z) / (nxt.z - cur.z);
                out[count++] = V.lerp(cur, nxt, t);
            }
        }
        if (count < 3) {
            return null;
        }
        V[] result = new V[count];
        System.arraycopy(out, 0, result, 0, count);
        return result;
    }

    /**
     * Perspective-correct Gouraud fill. Barycentric weights pick the pixel;
     * 1/w weighting interpolates the attributes; then the same toon shading
     * the fragment shader runs is evaluated here, per pixel.
     */
    private void fillGouraud(V a, V b, V c, boolean unlit) {
        float ax = a.x;
        float ay = a.y;
        float az = a.z;
        float bx = b.x;
        float by = b.y;
        float bz = b.z;
        float cx = c.x;
        float cy = c.y;
        float cz = c.z;

        float[] p0s = new float[3];
        float[] p1s = new float[3];
        float[] p2s = new float[3];
        projectPoint(ax, ay, az, p0s);
        projectPoint(bx, by, bz, p1s);
        projectPoint(cx, cy, cz, p2s);

        float x0 = p0s[0], y0 = p0s[1], iw0 = p0s[2];
        float x1 = p1s[0], y1 = p1s[1], iw1 = p1s[2];
        float x2 = p2s[0], y2 = p2s[1], iw2 = p2s[2];

        int minX = (int) Math.max(0, Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = (int) Math.min(width - 1, Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = (int) Math.max(0, Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = (int) Math.min(height - 1, Math.ceil(Math.max(y0, Math.max(y1, y2))));
        if (minX > maxX || minY > maxY) {
            return;
        }
        float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (Math.abs(area) < 1e-9f) {
            return;
        }
        float invArea = 1f / area;
        boolean opaque = a.a >= 0.999f && b.a >= 0.999f && c.a >= 0.999f;

        for (int y = minY; y <= maxY; y++) {
            float py = y + 0.5f;
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) * invArea;
                float w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) * invArea;
                float w2 = 1f - w0 - w1;
                if (w0 < 0f || w1 < 0f || w2 < 0f) {
                    continue;
                }
                float invW = w0 * iw0 + w1 * iw1 + w2 * iw2;
                if (invW <= 0f) {
                    continue;
                }
                int idx = y * width + x;
                float z = 1f / invW;
                if (z >= depth[idx]) {
                    continue;
                }
                float pa = w0 * iw0 / invW;
                float pb = w1 * iw1 / invW;
                float pc = 1f - pa - pb;

                float r = a.r * pa + b.r * pb + c.r * pc;
                float g = a.g * pa + b.g * pb + c.g * pc;
                float bl = a.b * pa + b.b * pb + c.b * pc;
                float al = a.a * pa + b.a * pb + c.a * pc;

                float nx = a.nx * pa + b.nx * pb + c.nx * pc;
                float ny = a.ny * pa + b.ny * pb + c.ny * pc;
                float nz = a.nz * pa + b.nz * pb + c.nz * pc;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 1e-6f) {
                    nx /= len;
                    ny /= len;
                    nz /= len;
                } else {
                    nx = 0f;
                    ny = 1f;
                    nz = 0f;
                }

                float wx = pa * a.x + pb * b.x + pc * c.x;
                float wy = pa * a.y + pb * b.y + pc * c.y;
                float wz = pa * a.z + pb * b.z + pc * c.z;

                if (!unlit) {
                    float[] lit = shadeToon(r, g, bl, nx, ny, nz, wx, wy, wz);
                    r = lit[0];
                    g = lit[1];
                    bl = lit[2];
                }
                if (opaque) {
                    depth[idx] = z;
                    argb[idx] = pack(r, g, bl, Math.min(1f, al));
                } else {
                    blend(idx, pack(r, g, bl, 1f), Math.min(1f, al));
                }
            }
        }
    }

    private final float[] shadeScratch = new float[3];

    /**
     * The toon model, mirroring {@code Shaders.SCENE_FRAG} term for term:
     * three-tone ramp, cool shadow, rim light, sky fill, squared fog.
     */
    private float[] shadeToon(float cr, float cg, float cb,
                              float nx, float ny, float nz,
                              float wx, float wy, float wz) {
        float ndl = Math.max(0f, nx * sun[0] + ny * sun[1] + nz * sun[2]);
        float mid = band(ndl, 0.24f);
        float lit = band(ndl, 0.58f);

        float shadowR = cr * 0.43f * (0.86f + 0.14f * clamp01(ambient[0]));
        float shadowG = cg * 0.53f * (0.86f + 0.14f * clamp01(ambient[1]));
        float shadowB = cb * 0.67f * (0.86f + 0.14f * clamp01(ambient[2]));
        float midR = cr * (0.62f + sunR * 0.06f);
        float midG = cg * (0.69f + sunG * 0.06f);
        float midB = cb * (0.82f + sunB * 0.06f);
        float fullR = cr * (0.83f + sunR * 0.05f);
        float fullG = cg * (0.89f + sunG * 0.05f);
        float fullB = cb * (0.97f + sunB * 0.05f);
        float r = lerp(shadowR, midR, mid);
        float g = lerp(shadowG, midG, mid);
        float b = lerp(shadowB, midB, mid);
        r = lerp(r, fullR, lit);
        g = lerp(g, fullG, lit);
        b = lerp(b, fullB, lit);

        float vx = camPos.x - wx;
        float vy = camPos.y - wy;
        float vz = camPos.z - wz;
        float vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6f;
        float ndv = clamp01((nx * vx + ny * vy + nz * vz) / vl);
        float rim = (float) Math.pow(1f - ndv, 3.0) * 0.09f;
        float skyFill = clamp01(ny) * 0.12f;

        r += cr * rim + cr * 0.08f * skyFill;
        g += cg * rim + cg * 0.13f * skyFill;
        b += cb * rim + cb * 0.24f * skyFill;

        float dist = vl;
        float fog = 1f - (float) Math.exp(-dist * fogDensity);
        fog = fog * fog;
        r = r + (fogColour[0] - r) * fog;
        g = g + (fogColour[1] - g) * fog;
        b = b + (fogColour[2] - b) * fog;

        shadeScratch[0] = r;
        shadeScratch[1] = g;
        shadeScratch[2] = b;
        return shadeScratch;
    }

    /** View-space point to screen coordinates, plus 1/w for correction. */
    private void projectPoint(float x, float y, float z, float[] out) {
        float px = proj.m[0] * x + proj.m[8] * z;
        float py = proj.m[5] * y + proj.m[9] * z;
        float w = -z;
        float invW = 1f / w;
        out[0] = (px * invW * 0.5f + 0.5f) * width;
        out[1] = (1f - (py * invW * 0.5f + 0.5f)) * height;
        out[2] = invW;
    }

    private void blend(int idx, int col, float alpha) {
        int dst = argb[idx];
        int dr = (dst >> 16) & 0xFF;
        int dg = (dst >> 8) & 0xFF;
        int db = dst & 0xFF;
        int sr = (col >> 16) & 0xFF;
        int sg = (col >> 8) & 0xFF;
        int sb = col & 0xFF;
        int r = (int) (sr * alpha + dr * (1f - alpha));
        int g = (int) (sg * alpha + dg * (1f - alpha));
        int b = (int) (sb * alpha + db * (1f - alpha));
        argb[idx] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // -------------------------------------------------------------------- ui

    /**
     * Draws a filled 2D triangle with per-vertex colour/alpha interpolation,
     * matching what the GPU does with the UiBatch's vertex colours.
     */
    public void fillTriangle2D(float ax, float ay, float bx, float by, float cx, float cy,
                               float[] ar, float[] br, float[] cr) {
        int minX = (int) Math.max(0, Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = (int) Math.min(width - 1, Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = (int) Math.max(0, Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = (int) Math.min(height - 1, Math.ceil(Math.max(ay, Math.max(by, cy))));
        float area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay);
        if (Math.abs(area) < 1e-9f) {
            return;
        }
        float invArea = 1f / area;
        for (int y = minY; y <= maxY; y++) {
            float py = y + 0.5f;
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f;
                float w0 = ((bx - px) * (cy - py) - (cx - px) * (by - py)) * invArea;
                float w1 = ((cx - px) * (ay - py) - (ax - px) * (cy - py)) * invArea;
                float w2 = 1f - w0 - w1;
                if (w0 < -0.0001f || w1 < -0.0001f || w2 < -0.0001f) {
                    continue;
                }
                float r = ar[0] * w0 + br[0] * w1 + cr[0] * w2;
                float g = ar[1] * w0 + br[1] * w1 + cr[1] * w2;
                float b = ar[2] * w0 + br[2] * w1 + cr[2] * w2;
                float a = ar[3] * w0 + br[3] * w1 + cr[3] * w2;
                if (a <= 0.004f) {
                    continue;
                }
                blend(y * width + x, pack(r, g, b, 1f), Math.min(1f, a));
            }
        }
    }

    /** Draws a text/textured quad by sampling the font atlas coverage. */
    public void fillTexturedQuad2D(float x0, float y0, float x1, float y1, float u0, float v0,
                                   float u1, float v1, byte[] atlas, int atlasW, int atlasH,
                                   int col, float alpha) {
        fillTexturedQuad2D(x0, y0, x1, y1, u0, v0, u1, v1, atlas, atlasW, atlasH,
                col, alpha, false);
    }

    /**
     * Textured blit. When {@code distanceField} is true the atlas holds signed
     * distances (128 = edge) and the quad is supersampled so the emulated
     * hardware filtering produces a smooth outline instead of a hard staircase.
     */
    public void fillTexturedQuad2D(float x0, float y0, float x1, float y1, float u0, float v0,
                                   float u1, float v1, byte[] atlas, int atlasW, int atlasH,
                                   int col, float alpha, boolean distanceField) {
        int minX = (int) Math.max(0, Math.floor(Math.min(x0, x1)));
        int maxX = (int) Math.min(width - 1, Math.ceil(Math.max(x0, x1)));
        int minY = (int) Math.max(0, Math.floor(Math.min(y0, y1)));
        int maxY = (int) Math.min(height - 1, Math.ceil(Math.max(y0, y1)));
        float w = Math.abs(x1 - x0);
        float h = Math.abs(y1 - y0);
        int ss = distanceField ? 4 : 1;
        float step = 1f / ss;
        float norm = 1f / (ss * ss);
        // Equivalent of GL's fwidth-based smoothstep: half a texel of blur.
        float texel = h / Math.max(1f, 34f);
        float halfWidth = distanceField ? Math.max(0.012f, texel * 0.5f) : 0f;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float acc = 0f;
                for (int syi = 0; syi < ss; syi++) {
                    for (int sxi = 0; sxi < ss; sxi++) {
                        float ty = (y + (syi + 0.5f) * step - y0) / (y1 - y0 + 1e-6f);
                        float tx = (x + (sxi + 0.5f) * step - x0) / (x1 - x0 + 1e-6f);
                        if (tx < 0f || tx > 1f || ty < 0f || ty > 1f) {
                            continue;
                        }
                        int ax = (int) ((u0 + (u1 - u0) * tx) * atlasW);
                        int ay = (int) ((v0 + (v1 - v0) * ty) * atlasH);
                        if (ax < 0 || ay < 0 || ax >= atlasW || ay >= atlasH) {
                            continue;
                        }
                        float sample = (atlas[ay * atlasW + ax] & 0xFF) / 255f;
                        if (distanceField) {
                            float t = (sample - 0.5f + halfWidth) / (2f * halfWidth);
                            acc += t <= 0f ? 0f : (t >= 1f ? 1f : t * t * (3f - 2f * t));
                        } else {
                            acc += sample > 0.5f ? 1f : sample * 2f;
                        }
                    }
                }
                acc *= norm;
                if (acc <= 0.004f) {
                    continue;
                }
                blend(y * width + x, col, alpha * Math.min(1f, acc));
            }
        }
    }

    public void fillRect(int rx, int ry, int rw, int rh, int col, float alpha) {
        int x1 = Math.min(width, rx + rw);
        int y1 = Math.min(height, ry + rh);
        for (int y = Math.max(0, ry); y < y1; y++) {
            for (int x = Math.max(0, rx); x < x1; x++) {
                blend(y * width + x, col, alpha);
            }
        }
    }

    // ------------------------------------------------------------- utilities

    public static int pack(float r, float g, float b, float a) {
        int ri = (int) (clamp01(r) * 255f);
        int gi = (int) (clamp01(g) * 255f);
        int bi = (int) (clamp01(b) * 255f);
        int ai = (int) (clamp01(a) * 255f);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    /**
     * Hard step with a 1.5% edge, matching {@code band()} in the scene shader:
     * flat zones with a crisp terminator between them.
     */
    private static float band(float x, float edge) {
        float t = (x - (edge - 0.025f)) / 0.05f;
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
