#!/usr/bin/env bash
#
# Runs the real GL start-up path (SceneRenderer + UiRenderer) on the desktop
# against a recording stand-in for GLES20, then draws one frame.
#
# This is the test that was missing when a shader bug shipped: the app compiled,
# every headless check passed, and the phone showed a blank screen because the
# fragment shader failed to compile inside onSurfaceCreated().
#
#   tools/render_path_test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [[ -z "${JAVA_BIN:-}" || ! -x "$JAVA_BIN" ]]; then
  JAVA_BIN="$(command -v java)"
fi
TOOLS="${BUILD_TOOLS:-$ROOT/tools/.toolchain}"
ANDROID_JAR="${ANDROID_JAR:-$TOOLS/android.jar}"
ECJ_JAR="${ECJ_JAR:-$(ls "$TOOLS"/ecj-*.jar 2>/dev/null | head -1)}"

[[ -n "${ECJ_JAR:-}" && -e "$ECJ_JAR" ]] || { echo "error: ecj not found" >&2; exit 1; }
[[ -e "$ANDROID_JAR" ]] || { echo "error: android.jar not found" >&2; exit 1; }

FAKE="$ROOT/desktop/fakegl"
OUT="$ROOT/build/renderpath-classes"
rm -rf "$OUT" && mkdir -p "$OUT"

echo "==> compiling the fake GL driver and the render-path harness"
# The fake GLES20/Log are compiled first so the renderer links against them
# instead of the throwing android.jar stubs.
"$JAVA_BIN" -jar "$ECJ_JAR" -source 1.8 -target 1.8 -nowarn \
  -classpath "$ANDROID_JAR" -d "$OUT" \
  "$FAKE/android/opengl/GLES20.java" \
  "$FAKE/android/util/Log.java"
"$JAVA_BIN" -jar "$ECJ_JAR" -source 1.8 -target 1.8 -nowarn \
  -classpath "$OUT:$ANDROID_JAR" -d "$OUT" \
  desktop/RenderPathTest.java \
  app/src/main/java/com/laststar/game/gl/*.java \
  app/src/main/java/com/laststar/game/core/*.java \
  app/src/main/java/com/laststar/game/world/*.java \
  app/src/main/java/com/laststar/game/art/*.java \
  app/src/main/java/com/laststar/game/ui/UiRenderer.java \
  app/src/main/java/com/laststar/game/ui/UiBatch.java \
  app/src/main/java/com/laststar/game/ui/AtlasFont.java \
  app/src/main/java/com/laststar/game/ui/VectorFont.java \
  app/src/main/java/com/laststar/game/ui/Font.java

echo "==> running the GL start-up verification"
# Put our stand-ins first on the classpath so they shadow android.jar's stubs.
"$JAVA_BIN" -cp "$OUT:$ANDROID_JAR" RenderPathTest
