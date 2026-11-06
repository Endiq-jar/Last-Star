#!/usr/bin/env bash
#
# Downloads the CC0 model set from the Kenney mirror on GitHub and bakes it into
# the flat buffer the app reads at start-up.
#
#   tools/fetch_models.sh          # download + bake
#   tools/fetch_models.sh --bake   # bake only (cache already populated)
#
# Kenney (kenney.nl) releases all of these packs as CC0 1.0, so they can be
# redistributed inside this repository and inside the APK. The mirror's GitHub
# licence field reads NOASSERTION, which is why the attribution in CREDITS.md
# points at Kenney rather than the mirror.
#
# The download is idempotent: existing files are kept, so re-running only fetches
# what is missing. The cache is gitignored; only the baked .bin is committed, so
# a clean checkout builds without network access.
#
# Set MODEL_CACHE to download somewhere other than build/models-src.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${MODEL_CACHE:-$ROOT/build/models-src}"
OUT="$ROOT/app/src/main/assets/models"
REPO="shorepine/kenney"

PACKS=(nature castle survival fantasy-town)

# The colormap atlases the textured kits sample. Baked into vertex colours, so
# the PNGs themselves never ship.
ATLASES=(castle survival fantasy-town)

fetch() {
  local pack="$1" name="$2" dest="$3"
  [[ -s "$dest" ]] && return 0
  mkdir -p "$(dirname "$dest")"
  local sha
  sha="$(curl -s --max-time 30 \
      "https://api.github.com/repos/$REPO/contents/3d/$pack/$name" \
      -H 'Accept: application/vnd.github+json' \
      | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("sha",""))')"
  if [[ -z "$sha" ]]; then
    echo "  !! $pack/$name not found in the mirror" >&2
    return 1
  fi
  curl -sL --max-time 60 "https://api.github.com/repos/$REPO/git/blobs/$sha" \
    -H 'Accept: application/vnd.github.raw' -o "$dest"
  printf '  %-40s %7s bytes\n' "$pack/$name" "$(stat -c%s "$dest")"
}

if [[ "${1:-}" != "--bake" ]]; then
  echo "==> downloading CC0 models from $REPO"
  for pack in "${PACKS[@]}"; do
    mapfile -t names < <(cd "$ROOT" && python3 - "$pack" <<'PY'
import sys
sys.path.insert(0, 'tools')
import model_manifest as m
for name, p, role, size in m.MODELS:
    if p == sys.argv[1]:
        print(name)
PY
)
    for n in "${names[@]}"; do
      fetch "$pack" "$n.glb" "$CACHE/$pack/$n.glb"
    done
  done
  for pack in "${ATLASES[@]}"; do
    fetch "$pack" "Textures/colormap.png" "$CACHE/Textures/$pack.png"
  done
fi

echo "==> baking"
python3 "$ROOT/tools/bake_models.py" --manifest "$CACHE" "$OUT" world
