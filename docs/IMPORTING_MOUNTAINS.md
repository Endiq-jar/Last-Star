# Importing the Mountain pack

The Holotna **Mountain – Stylized Fantasy Environment** pack ships from the
Unity Asset Store, which is not reachable from this build environment. The
importer below is ready and tested: the moment the files exist on disk, one
command turns them into game models.

## Which download to send

| File | Usable? | Why |
|---|---|---|
| **Mountain (FBX).zip** | **Yes** | `.fbx` meshes — `tools/import_mountain.py` reads these directly. |
| Mountain (Textures).zip | Partly | No geometry on its own. Its average colour is sampled as the rock tint; the game bakes colour into vertices rather than sampling textures per pixel. |
| Mountain (Blender 4.2.2).zip | Not directly | `.blend` is Blender's internal format. It needs Blender installed to extract a mesh, which this environment does not have. Open it in Blender and `File > Export > FBX`, then send that. |

So: **send the FBX zip.** That is the one that works end to end.

## What to do with it

```bash
# 1. Put the archive (or a folder of .fbx files) somewhere in the repo.
#    assets_src/ is git-ignored for large art, so it stays out of history.
mkdir -p assets_src/mountain
cp "Mountain (FBX).zip" assets_src/mountain/

# 2. Bake it into the game's model set.
tools/import_mountain.py assets_src/mountain/"Mountain (FBX).zip" \
    --role cliff --out build/mountain_set --size 90

# 3. Look at what came out before committing anything.
#    (a single oversized mountain will dominate the skyline; the importer
#    normalises the largest axis to --size metres and grounds it at y = 0)
ls -la build/mountain_set/world.bin
```

Then either replace `app/src/main/assets/models/world.bin` with the result, or
merge the new models into the existing set with `tools/bake_models.py`.

## What the importer handles

* **Binary FBX 7.x** — header check, node tree, compressed typed arrays.
* **ASCII FBX** — the token-based fallback parser.
* **Nested transforms** — both `Model > Properties70 > P "Lcl Translation"` and
  the flatter `Model > Lcl Translation` spelling, plus rotation (XYZ order) and
  scale, composed into one matrix and applied to every vertex.
* **Polygon faces** — FBX negates the last index of each face (`~i`); faces are
  fanned into triangles on the way out.
* **Normalisation** — scaled to a target size and re-centred so it sits on the
  ground at the origin.
* **Welding** — duplicate vertices merged, so a ~2 000-triangle mesh does not
  ship as ~6 000 vertices. The bake format indexes triangles.

## Tests

`tools/tests/test_fbx_import.py` writes a binary FBX that mimics an exporter's
output — compressed array property, negated face terminators, nested
`Properties70` transform — then reads it back and asserts the geometry,
winding, translation, rotation and scale all survive. It runs in
`tools/run_tests.sh` and in CI.

That test caught four real bugs while the importer was being written, which is
the point: FBX has no single spelling for any of this, and a parser that is
only ever run against one hand-written file will silently produce wrong
geometry rather than fail.
