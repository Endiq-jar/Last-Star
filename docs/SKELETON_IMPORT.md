# Weighted character asset pipeline

The runtime loads the versioned `LSSKIN 1` text interchange format with
`SkinnedAssetLoader`. It contains a parent-first bone hierarchy, inverse-bind
matrices, indexed triangle geometry, four joint/weight pairs per vertex, and
sampled local-matrix animation frames. The runtime normalizes weights, validates
bone/index ranges, samples clips, composes hierarchical skin matrices, and feeds
the weighted skinner used by the character renderer.

## Compile an exported JSON asset

`tools/import_skinned.py source.json output.lsskin` validates and compiles a
DCC-neutral JSON export. The root schema is:

- `format`: `last-star-skinned`; `version`: `1`
- `bones`: parent-first entries with `name`, `parent` (`-1` for root), and
  column-major 16-number `inverse_bind` matrix
- `vertices`: entries with `position[3]`, `normal[3]`, optional `color[4]`, and
  one to four `[jointIndex, weight]` pairs in `influences`
- `indices`: triangle indices
- optional `clips`: `name`, positive `duration`, and increasing `frames`; each
  frame has a `time` and one column-major 4x4 `local_matrices` entry per bone

The importer checks parent order, finite values, weights, triangle indices,
keyframe ordering, and normalizes/pads skin weights to four slots. No runtime
network access or third-party package is involved. A DCC-specific exporter can
produce this JSON from glTF/FBX data; the checked-in converter is the final
validation/compilation stage rather than a claim that arbitrary FBX files are
already parsed by the Android app.
