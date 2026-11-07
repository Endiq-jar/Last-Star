# Credits

## Imported 3D models

The world is dressed with low-poly props from **Kenney** (kenney.nl), imported
from the CC0 mirror [`shorepine/kenney`](https://github.com/shorepine/kenney) on
GitHub:

| Pack | Used for |
|---|---|
| [Nature Kit](https://kenney.nl/assets/nature-kit) | conifers, oaks, pines, rocks, cliffs, bushes, grass, flowers, mushrooms, logs, stumps, ground tiles |
| [Fantasy Town Kit](https://kenney.nl/assets/fantasy-town-kit) | the curtain wall, towers, roofs, gate, well, fences |
| [Castle Kit](https://kenney.nl/assets/castle-kit) | gatehouse, walls, towers, the corner ruin |
| [Survival Kit](https://kenney.nl/assets/survival-kit) | tents, campfire, barrels, crates, chests, buckets, the bedroll |

84 models, 28,515 vertices, 11,940 triangles. They are converted to flat-shaded,
vertex-coloured triangles by [`tools/bake_models.py`](tools/bake_models.py) and
packed into a single 1.1 MB asset, `app/src/main/assets/models/world.bin`, which
`ModelLibrary` reads at start-up. `docs/ui/models.png` is a contact sheet of the
whole set.

**Licence: CC0 1.0 Universal (public domain).** Kenney releases these kits under
CC0; see [kenney.nl](https://kenney.nl). No attribution is required, but the
artist deserves it — this file exists for that reason.

Two deliberate conversions were made while baking, so the imported art matches
the game instead of fighting it:

* **Colour.** The source atlases are flat-shaded and teal-leaning; every vertex
  is re-tinted through the game's palette (grass `#5FBF6A`, leaf `#49B36B`,
  wood `#C07244`, stone `#C2CBDA`, water `#8FE0F0`) so imported props and the
  procedurally generated ground share one light.
* **Scale.** Each model is normalised by its largest horizontal extent to a
  metre size declared in [`tools/model_manifest.py`](tools/model_manifest.py),
  never by height — a flat rock and a tall tree then sit at the right relative
  size on the terrain.

## Mountains

**The Holotna "Mountain – Stylized Fantasy Environment" pack could not be
imported.** `assetstore.unity.com` and `holotna.com` are both unreachable from
the build environment (no DNS route), GitHub hosts no repository by that name,
and the projects that mention it in their docs contain text references only —
no meshes. It is also a paid, account-gated Asset Store licence, so even a
reachable copy would not be redistributable into this repository.

What ships instead is built from assets already licensed here:

* **14 rock spires inside the map**, assembled from the imported Kenney cliff
  and rock models — a broad skirt at the base, then progressively smaller,
  taller blocks stacked on top, with a pale "snow" tint creeping down from the
  summit. These are real props placed by `PropPlanner.buildPeaks`, walkable-up
  and visible from anywhere on the map.
* **A 56-peak range on the horizon**, generated in `Geometry.mountainRange`.


The distant snow-capped range on the horizon is **not** an imported asset — no
CC0 mountain kit was available from a reachable source (Kenney publishes no
mountain kit, and the linked Unity Asset Store / Holotna packs are both licence
gated and unreachable from the build environment). It is generated in
`Geometry.mountainRange`: 56 low-poly peaks with jittered rims, an asymmetric
apex and a painted snow band, a separate ring that follows the camera so the
skyline reads from anywhere on the map. The foothills inside the map are part of
the procedural terrain.

## Everything else

The engine (`app/src/main/java`), the headless test harness and software
rasteriser (`desktop/`), the build pipeline, the UI and its SDF typeface, and all
procedural character, terrain and audio generation are original work released
under the repository's EPL-2.0 licence.

The three Unity projects under `upstream/` keep their own licences — see
[`upstream/README.md`](upstream/README.md).

The separate `godot/` migration prototype is based on
[ProWille/Godot-Adventure-Game](https://github.com/ProWille/Godot-Adventure-Game),
upstream commit `774112bc3d689c2690f8b40a3955f321dfc51542`, under its MIT
licence. Its original code, placeholder assets, screenshots, and license remain
in that directory; it is not compiled into the native Android APK. See
[`docs/GODOT_MIGRATION.md`](docs/GODOT_MIGRATION.md) before redistributing the
prototype.

## Rendering

The world is drawn with a hand-written toon shader, not with an imported
material:

* **Three-tone banding** — the diffuse term is quantised so lit faces are one
  flat value and shadowed faces are another, with a narrow terminator band
  between them. This is what makes the frame read as illustration.
* **Cool shadows** — the shadow term shifts toward blue-violet instead of
  going black. Anime foliage keeps its colour in shadow; dimming it toward
  black is what makes stylised terrain look muddy.
* **Rim light** — a bright edge on silhouettes that turn away from the camera,
  which separates characters from the background.
* **Painted sky** — banded vertical gradient, sun disc with bloom, procedural
  cloud streaks and stars that only appear once the sky has darkened.
* **Wind** — grass, flowers and foliage carry a per-vertex phase derived from
  height above each model's base, so tips bend and roots stay planted. The
  amplitude is per-role: ground cover sways, rocks and timber do not.
* **Aerial perspective** — squared distance fog, so near geometry keeps its
  colour while the horizon dissolves into the sky.

No derivatives, no extensions: every shader here runs on GLSL ES 1.00 as
shipped, and `tools/check_glsl.py` enforces the rules that once kept the app
from starting on Mali and Adreno hardware.
