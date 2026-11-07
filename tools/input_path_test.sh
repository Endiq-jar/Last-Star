#!/usr/bin/env bash
#
# Drives the real menu through the same pixel -> virtual conversion the touch
# handler uses, on several device sizes.
#
# This is the test that was missing when a build shipped where the home screen
# rendered and no tap did anything: every existing check called the menu in
# virtual coordinates and skipped the conversion entirely.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Source the fetched toolchain so the script works standalone (CI, a fresh
# clone, or a sandbox rebuild) rather than only inside an interactive shell.
if [[ -z "${JAVA_HOME:-}" && -f "$ROOT/tools/.toolchain/env.sh" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/tools/.toolchain/env.sh"
fi
# Fall back to the JDK the fetch script unpacks, if still unset.
if [[ -z "${JAVA_HOME:-}" && -x "$ROOT/tools/.toolchain/java/bin/java" ]]; then
  export JAVA_HOME="$ROOT/tools/.toolchain/java"
fi

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [[ -z "${JAVA_BIN:-}" || ! -x "$JAVA_BIN" ]]; then
  JAVA_BIN="$(command -v java)"
fi
TOOLS="${BUILD_TOOLS:-$ROOT/tools/.toolchain}"
ANDROID_JAR="${ANDROID_JAR:-$TOOLS/android.jar}"
ECJ_JAR="${ECJ_JAR:-$(ls "$TOOLS"/ecj-*.jar 2>/dev/null | head -1)}"

OUT="$ROOT/build/inputtest"
rm -rf "$OUT" && mkdir -p "$OUT"

echo "==> compiling the input-path harness"
"$JAVA_BIN" -jar "$ECJ_JAR" -source 1.8 -target 1.8 -nowarn \
  -classpath "$ANDROID_JAR" -d "$OUT" \
  desktop/InputPathTest.java \
  app/src/main/java/com/laststar/game/ui/Menu.java \
  app/src/main/java/com/laststar/game/ui/TouchControls.java \
  app/src/main/java/com/laststar/game/ui/TabBar.java \
  app/src/main/java/com/laststar/game/ui/GenshinKit.java \
  app/src/main/java/com/laststar/game/ui/Style.java \
  app/src/main/java/com/laststar/game/ui/Font.java \
  app/src/main/java/com/laststar/game/ui/VectorFont.java \
  app/src/main/java/com/laststar/game/ui/Backdrop.java \
  app/src/main/java/com/laststar/game/ui/AtlasFont.java \
  app/src/main/java/com/laststar/game/ui/UiBatch.java \
  app/src/main/java/com/laststar/game/ui/screens/*.java \
  app/src/main/java/com/laststar/game/core/*.java \
  app/src/main/java/com/laststar/game/world/*.java \
  app/src/main/java/com/laststar/game/world/lore/*.java \
  app/src/main/java/com/laststar/game/Prefs.java

echo "==> running the input-path verification"
"$JAVA_BIN" -cp "$OUT:$ANDROID_JAR" InputPathTest
