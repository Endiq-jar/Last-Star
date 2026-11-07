#!/usr/bin/env bash
#
# Runs the headless verification suite and regenerates the screenshots.
#
#   tools/run_tests.sh            # tests + screenshots
#   tools/run_tests.sh --tests    # tests only
#
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Source the fetched toolchain so this works standalone -- CI, a fresh clone,
# or a rebuilt sandbox -- rather than only inside an interactive shell with a
# hand-exported JAVA_HOME. It also removes the hardcoded /tmp paths that used
# to break every time the sandbox was recreated.
if [[ -z "${JAVA_HOME:-}" && -f "$ROOT/tools/.toolchain/env.sh" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/tools/.toolchain/env.sh"
fi
if [[ -z "${JAVA_HOME:-}" && -x "$ROOT/tools/.toolchain/java/bin/java" ]]; then
  export JAVA_HOME="$ROOT/tools/.toolchain/java"
fi

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [[ -z "${JAVA_BIN:-}" || ! -x "$JAVA_BIN" ]]; then
  JAVA_BIN="$(command -v java || true)"
fi
[[ -n "${JAVA_BIN:-}" ]] || { echo "error: no java runtime found" >&2; exit 1; }

TOOLS="${BUILD_TOOLS:-$ROOT/tools/.toolchain}"
ANDROID_JAR="${ANDROID_JAR:-$TOOLS/android.jar}"
ECJ_JAR="${ECJ_JAR:-$(ls "$TOOLS"/ecj-*.jar 2>/dev/null | head -1)}"
[[ -e "$ANDROID_JAR" ]] || { echo "error: android.jar not found ($ANDROID_JAR)" >&2; exit 1; }
[[ -e "$ECJ_JAR" ]] || { echo "error: ecj not found ($ECJ_JAR)" >&2; exit 1; }

OUT="$ROOT/build/test-classes"
rm -rf "$OUT" && mkdir -p "$OUT"

# Everything except the classes that need a live GL context / Android runtime.
find app/src/main/java -name '*.java' \
  ! -path '*/gl/SceneRenderer.java' \
  ! -path '*/ui/UiRenderer.java' \
  ! -path '*/audio/*' \
  ! -path '*MainActivity*' \
  ! -path '*GameSurface*' | sort > "$ROOT/build/test-sources.txt"

echo "==> compiling simulation + harness"
"$JAVA_BIN" -jar "$ECJ_JAR" -source 1.8 -target 1.8 -nowarn \
  -classpath "$ANDROID_JAR" -d "$OUT" \
  @"$ROOT/build/test-sources.txt" \
  desktop/HeadlessTest.java desktop/PngWriter.java desktop/Raster.java \
  desktop/ScreenshotTool.java desktop/MenuPreview.java \
  desktop/ModelLoaderFixture.java desktop/WanderTest.java

echo "==> checking GLSL ES 1.00 rules that a syntax parser cannot see"
python3 tools/extract_shaders.py
python3 tools/check_glsl.py

echo "==> verifying the real GL start-up path (shaders, renderer, one frame)"
"$ROOT/tools/render_path_test.sh"

echo "==> verifying the FBX import path"
python3 "$ROOT/tools/tests/test_fbx_import.py"
python3 "$ROOT/tools/tests/test_mesh_text.py"

echo "==> verifying the menu input path"
"$ROOT/tools/input_path_test.sh"

echo "==> verifying the Wander and its spells"
"$JAVA_BIN" -cp "$OUT" WanderTest

echo "==> running headless verification"
"$JAVA_BIN" -cp "$OUT" HeadlessTest

if [[ "${1:-}" != "--tests" ]]; then
  echo
  echo "==> rendering screenshots from the real game code"
  "$JAVA_BIN" -cp "$OUT" ScreenshotTool docs/screenshots 960 540
  echo
  echo "==> rendering the menu screens"
  "$JAVA_BIN" -cp "$OUT" MenuPreview docs/ui/screens 1280 720
fi
