# Prompt for Claude — importing the Mountain pack

Copy everything between the lines into Claude, with **Mountain (FBX).zip**
attached. There are two paths: use **Path A** if Claude can reach the
repository, otherwise **Path B**, which sends the geometry back to me as text I
*can* accept.

---

## The prompt

I have a Unity Asset Store art pack — **Mountain – Stylized Fantasy Environment
by Holotna** — and I need its geometry imported into a game called **Last Star**.
I cannot upload a `.zip` to the other assistant I work with (attachments don't
accept archives), so I need you to do the extraction, or hand me back something
I can paste.

### The project

- Repository: **https://github.com/Endiq-jar/Last-Star** (public)
- Branch to work on: **`arena/b1ab2e08-last-star`**
- Android game, Java, OpenGL ES 2.0, no engine. Runs on API 21+.
- Art direction: **anime / Genshin-Impact style** — bright, saturated,
  cel-shaded. Not realism.

### What's already built

- `tools/fbx_import.py` — a working FBX reader. Handles binary FBX 7.x and
  ASCII, compressed typed arrays, `PolygonVertexIndex` with negated face
  terminators, nested `Lcl Translation/Rotation/Scaling` under both
  `Properties70 > P` and flat spellings. Returns flat position/normal lists.
- `tools/import_mountain.py` — takes a `.zip` or a folder of `.fbx`, normalises
  each mesh to a target size, grounds it at `y = 0`, welds duplicate vertices,
  and writes `world.bin`.
- `tools/tests/test_fbx_import.py` — 9 checks that write a synthetic binary FBX
  and read it back. Currently passing.
- `app/src/main/java/com/laststar/game/gl/ModelLibrary.java` — loads the baked
  `world.bin` at start-up.
- `tools/bake_models.py` — the bake format spec at the top of the file.

### What I want

**Path A — if you can clone the repo:**

1. Clone `https://github.com/Endiq-jar/Last-Star`, check out
   `arena/b1ab2e08-last-star`.
2. Extract `Mountain (FBX).zip` into `assets_src/mountain/` (already
   git-ignored).
3. Run:
   ```
   tools/import_mountain.py assets_src/mountain/"Mountain (FBX).zip" \
       --role cliff --out build/mountain_set --size 90
   ```
4. Check the result actually looks like a mountain: load
   `build/mountain_set/world.bin` and report, per model, the triangle count,
   vertex count and bounding box. **Tell me if the meshes are wildly
   off-centre, inside-out, or scaled wrong** — don't just report success.
5. If the geometry is good, merge it into the existing set in
   `app/src/main/assets/models/world.bin` (84 models already there — read
   `tools/bake_models.py` for the format) rather than replacing it, unless
   merging is impractical, in which case say why.
6. Then place them in the world: `app/src/main/java/com/laststar/game/gl/PropPlanner.java`
   has `buildPeaks()` and `buildRidges()`. The mountains should appear
   **everywhere across the map**, not in one showcase spot. `CLIFF` is role 8.
7. Run `tools/run_tests.sh --tests` — everything must stay green
   (currently 13,578 headless checks, 7 GL start-up checks, 9 FBX checks).
8. Rebuild the APK with `tools/build_apk.sh`, update the SHA-256 in `README.md`,
   commit and push to the same branch.

**Path B — if you can't reach the repo (or just extract and hand back):**

1. Extract the `.fbx` files from the zip.
2. For each one, read the vertices, normals and polygons, and write me back a
   **single text block per mesh** in exactly this format:

   ```
   ### MESH <name>
   VERTS <vertexCount>
   X Y Z NX NY NZ        <- one line per vertex, 6 floats, 4 decimal places
   ...
   TRIS <triangleCount>
   A B C                 <- one line per triangle, 3 vertex indices
   ...
   ```

3. Keep it compact: trim to 4 decimal places, don't pretty-print, and if a mesh
   exceeds ~20,000 triangles, decimate it to roughly that and say so.
4. If the pack has a heightmap or a LOD that's cheaper, prefer that and tell me
   which you used.

I will paste that text back to my other assistant. That format is already
parsed by `tools/import_mesh_text.py` in the repo (18 tests passing), which
writes the same `world.bin` the FBX path produces -- so either route lands in
the game the same way.

### Rules

- **Do not invent geometry or claim a mesh was imported when it wasn't.** If the
  zip has no usable meshes, say so plainly.
- If the only meshes are `.blend`, tell me — that needs Blender to read, and
  I'll re-export as FBX.
- Keep the anime look: these become the horizon and the cliffs, so silhouette
  and scale matter more than texture detail.
- Licence note: this is a paid Asset Store pack. **Do not commit the raw art to
  a public repository** unless the licence allows redistribution. `assets_src/`
  is git-ignored for exactly this reason — the baked derivative can ship, the
  source zip should not.

---

## Why Path B's format

The bake format in `tools/bake_models.py` stores positions, normals and RGB
per vertex plus 16-bit indices, interleaved as 9 floats per vertex. The text
format above is a direct 1:1 mapping of that, so it needs no interpretation —
I parse it, weld it, normalise it and it drops straight into the same pipeline
`tools/import_mountain.py` writes. Triangle indices rather than raw triangle
soup keeps the transfer small: a 20,000-triangle mesh is roughly 1.5 MB of text
instead of ~4 MB.
