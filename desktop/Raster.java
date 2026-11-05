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
    private float sunG = 0.9f;
    private float sunB = 0.75f;

    // ------------------------------------------------------------------ clear

    /** Vertical sky gradient plus sun disc and speckle stars. */
    public void drawSky(float horizonR, float horizonG, float horizonB,
                        float zenithR, float zenithG, float zenithB,
                        float sunScreenX, float sunScreenY, float sunRadius,
                        float sunR, float sunG, float sunB, float sunVisible) {
        for (int y = 0; y < height; y++) {
            float t = 1f - (float) y / height;
            float r = horizonR + (zenithR - horizonR) * (float) Math.pow(t, 0.75f);
            float g = horizonG + (zenithG - horizonG) * (float) Math.pow(t, 0.75f);
            float b = horizonB + (zenithB - horizonB) * (float) Math.pow(t, 0.75f);
            for (int x = 0; x < width; x++) {
                float rr = r;
                float gg = g;
                float bb = b;
                if (sunVisible > 0.5f) {
                    float dx = (x - sunScreenX) / sunRadius;
                    float dy = (y - sunScreenY) / (sunRadius * 1.05f);
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    float disc = clamp01((1f - d) * 9f);
                    float halo = clamp01(1f - d * 0.55f);
                    rr += sunR * (disc * 1.3f + halo * halo * halo * 0.22f);
                    gg += sunG * (disc * 1.3f + halo * halo * halo * 0.19f);
                    bb += sunB * (disc * 1.3f + halo * halo * halo * 0.13f);
                }
                // Stars in the upper half.
                if (y < height * 0.62f) {
                    float hsh = (float) (Math.sin(x * 12.9898 + y * 78.233) * 43758.5453);
                    hsh -= (float) Math.floor(hsh);
                    if (hsh > 0.99955f) {
                        float k = 0.35f + 0.5f * (1f - (float) y / (height * 0.7f));
                        rr += k;
                        gg += k;
                        bb += k;
                    }
                }
                argb[y * width + x] = pack(rr, gg, bb, 1f);
            }
        }
    }

    public void clearDepth() {
        java.util.Arrays.fill(depth, Float.MAX_VALUE);
    }

    // -------------------------------------------------------------- geometry

    /** A mesh instance with a model matrix. */
    public void drawMesh(float[] positions, float[] normals, float[] colors, short[] indices,
                         Mat4 model, float alpha, float emissive, float[] tint, float tintAmount) {
        int tris = indices.length / 3;
        float[] w = new float[3];
        for (int t = 0; t < tris; t++) {
            int i0 = indices[t * 3] & 0xFFFF;
            int i1 = indices[t * 3 + 1] & 0xFFFF;
            int i2 = indices[t * 3 + 2] & 0xFFFF;
            Vec3 p0 = transform(model, positions, i0);
            Vec3 p1 = transform(model, positions, i1);
            Vec3 p2 = transform(model, positions, i2);

            // Face normal in world space for flat shading.
            Vec3 e1 = p1.sub(p0);
            Vec3 e2 = p2.sub(p0);
            Vec3 n = e1.cross(e2).normalized();
            float ndl = Math.max(0f, n.x * sun[0] + n.y * sun[1] + n.z * sun[2]);
            float sky = 0.55f + 0.45f * Math.max(0f, n.y);
            float lightR = ambient[0] * sky + sunR * ndl;
            float lightG = ambient[1] * sky + sunG * ndl;
            float lightB = ambient[2] * sky + sunB * ndl;

            float cr = (colors[i0 * 4] + colors[i1 * 4] + colors[i2 * 4]) / 3f;
            float cg = (colors[i0 * 4 + 1] + colors[i1 * 4 + 1] + colors[i2 * 4 + 1]) / 3f;
            float cb = (colors[i0 * 4 + 2] + colors[i1 * 4 + 2] + colors[i2 * 4 + 2]) / 3f;
            if (tint != null && tintAmount > 0f) {
                cr = cr + (tint[0] - cr) * tintAmount;
                cg = cg + (tint[1] - cg) * tintAmount;
                cb = cb + (tint[2] - cb) * tintAmount;
            }
            float r = cr * lightR + cr * emissive;
            float g = cg * lightG + cg * emissive;
            float b = cb * lightB + cb * emissive;

            // Distance fog, measured from the camera to the triangle centroid.
            Vec3 centroid = p0.add(p1).add(p2).mul(1f / 3f);
            float dist = (float) Math.sqrt(centroid.lengthSq() - centroid.dot(camPos) * 2f
                    + camPos.lengthSq());
            float fog = 1f - (float) Math.exp(-dist * fogDensity);
            r = r + (fogColour[0] - r) * fog;
            g = g + (fogColour[1] - g) * fog;
            b = b + (fogColour[2] - b) * fog;

            drawTriangle(p0, p1, p2, r, g, b, alpha);
        }
    }

    private Vec3 transform(Mat4 m, float[] pos, int i) {
        float x = pos[i * 3];
        float y = pos[i * 3 + 1];
        float z = pos[i * 3 + 2];
        return new Vec3(
                m.m[0] * x + m.m[4] * y + m.m[8] * z + m.m[12],
                m.m[1] * x + m.m[5] * y + m.m[9] * z + m.m[13],
                m.m[2] * x + m.m[6] * y + m.m[10] * z + m.m[14]);
    }

    /** Near-plane clipping then scanline rasterisation with a z-buffer. */
    private void drawTriangle(Vec3 a, Vec3 b, Vec3 c, float r, float g, float bl, float alpha) {
        Vec3[] poly = {toView(a), toView(b), toView(c)};
        Vec3[] clipped = clipNear(poly);
        if (clipped == null) {
            return;
        }
        for (int i = 1; i + 1 < clipped.length; i++) {
            fillProjected(clipped[0], clipped[i], clipped[i + 1], r, g, bl, alpha);
        }
    }

    private Vec3 toView(Vec3 world) {
        return new Vec3(
                view.m[0] * world.x + view.m[4] * world.y + view.m[8] * world.z + view.m[12],
                view.m[1] * world.x + view.m[5] * world.y + view.m[9] * world.z + view.m[13],
                view.m[2] * world.x + view.m[6] * world.y + view.m[10] * world.z + view.m[14]);
    }

    /** Sutherland-Hodgman against the single near plane z = -near. */
    private Vec3[] clipNear(Vec3[] poly) {
        float limit = -near;
        Vec3[] out = new Vec3[poly.length + 1];
        int count = 0;
        for (int i = 0; i < poly.length; i++) {
            Vec3 cur = poly[i];
            Vec3 nxt = poly[(i + 1) % poly.length];
            boolean curIn = cur.z < limit;
            boolean nxtIn = nxt.z < limit;
            if (curIn) {
                out[count++] = cur;
            }
            if (curIn != nxtIn) {
                float t = (limit - cur.z) / (nxt.z - cur.z);
                out[count++] = cur.lerp(nxt, t);
            }
        }
        if (count < 3) {
            return null;
        }
        Vec3[] result = new Vec3[count];
        System.arraycopy(out, 0, result, 0, count);
        return result;
    }

    private float[] sx = new float[8];
    private float[] sy = new float[8];
    private float[] sz = new float[8];

    private void fillProjected(Vec3 a, Vec3 b, Vec3 c, float r, float g, float bl, float alpha) {
        project(a, 0);
        project(b, 1);
        project(c, 2);

        float x0 = sx[0], y0 = sy[0], z0 = sz[0];
        float x1 = sx[1], y1 = sy[1], z1 = sz[1];
        float x2 = sx[2], y2 = sy[2], z2 = sz[2];

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

        int col = pack(r, g, bl, alpha);
        boolean opaque = alpha >= 0.999f;

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
                // Perspective-correct depth interpolation.
                float invZ = w0 / z0 + w1 / z1 + w2 / z2;
                if (invZ <= 0f) {
                    continue;
                }
                float z = 1f / invZ;
                int idx = y * width + x;
                if (z >= depth[idx]) {
                    continue;
                }
                if (opaque) {
                    depth[idx] = z;
                    argb[idx] = col;
                } else {
                    blend(idx, col, alpha);
                }
            }
        }
    }

    private void project(Vec3 v, int slot) {
        float x = proj.m[0] * v.x + proj.m[8] * v.z;
        float y = proj.m[5] * v.y + proj.m[9] * v.z;
        float z = proj.m[10] * v.z + proj.m[14];
        float w = -v.z;
        float invW = 1f / w;
        float ndcX = x * invW;
        float ndcY = y * invW;
        float ndcZ = z * invW;
        sx[slot] = (ndcX * 0.5f + 0.5f) * width;
        sy[slot] = (1f - (ndcY * 0.5f + 0.5f)) * height;
        sz[slot] = ndcZ;
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
        int minX = (int) Math.max(0, Math.floor(Math.min(x0, x1)));
        int maxX = (int) Math.min(width - 1, Math.ceil(Math.max(x0, x1)));
        int minY = (int) Math.max(0, Math.floor(Math.min(y0, y1)));
        int maxY = (int) Math.min(height - 1, Math.ceil(Math.max(y0, y1)));
        for (int y = minY; y <= maxY; y++) {
            float ty = (y + 0.5f - y0) / (y1 - y0 + 1e-6f);
            for (int x = minX; x <= maxX; x++) {
                float tx = (x + 0.5f - x0) / (x1 - x0 + 1e-6f);
                int ax = (int) ((u0 + (u1 - u0) * tx) * atlasW);
                int ay = (int) ((v0 + (v1 - v0) * ty) * atlasH);
                if (ax < 0 || ay < 0 || ax >= atlasW || ay >= atlasH) {
                    continue;
                }
                int coverage = atlas[ay * atlasW + ax] & 0xFF;
                if (coverage == 0) {
                    continue;
                }
                blend(y * width + x, col, alpha * coverage / 255f);
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

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
