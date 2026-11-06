#!/usr/bin/env bash
#
# Builds Last Star into a signed, installable Android APK.
#
# The build is self-contained: it uses aapt2 (resources), ecj (Java compiler),
# d8 (dexer) and apksigner (v1/v2/v3 signing) straight from a local toolchain
# directory, so neither Gradle nor a full Android SDK install is required.
#
# Usage:
#   tools/build_apk.sh [--version-name 1.0.0] [--version-code 1]
#
# Environment:
#   JAVA_HOME     JDK/JRE to use (defaults to a jdk4py runtime if present)
#   ANDROID_JAR   path to android.jar
#   BUILD_TOOLS   directory containing aapt2, d8.jar, ecj-*.jar, apksigner.jar
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERSION_NAME="1.0.0"
VERSION_CODE="1"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version-name) VERSION_NAME="$2"; shift 2;;
    --version-code) VERSION_CODE="$2"; shift 2;;
    *) echo "unknown argument: $1" >&2; exit 2;;
  esac
done

BUILD_DIR="$ROOT/build"
GEN_DIR="$BUILD_DIR/gen"
CLASS_DIR="$BUILD_DIR/classes"
DEX_DIR="$BUILD_DIR/dex"
OUT_DIR="$ROOT/apk"
mkdir -p "$GEN_DIR" "$CLASS_DIR" "$DEX_DIR" "$OUT_DIR"

# ---------------------------------------------------------------- toolchain
find_tool() {
  local name="$1"
  shift
  for candidate in "$@"; do
    if [[ -x "$candidate" || -f "$candidate" ]]; then
      echo "$candidate"
      return 0
    fi
  done
  echo "error: could not find $name. Set BUILD_TOOLS/ANDROID_JAR/JAVA_HOME." >&2
  return 1
}

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
if [[ -z "${JAVA_BIN:-}" ]]; then
  JAVA_BIN="$(find_tool java /tmp/jdkpkg/jdk4py/java-runtime/bin/java "$(command -v java || true)")"
fi

TOOLS="${BUILD_TOOLS:-/tmp/tc/tools}"
AAPT2="${AAPT2:-$TOOLS/aapt2}"
ANDROID_JAR="${ANDROID_JAR:-$TOOLS/android.jar}"
D8_JAR="${D8_JAR:-$TOOLS/d8.jar}"
ECJ_JAR="${ECJ_JAR:-$(ls "$TOOLS"/ecj-*.jar 2>/dev/null | head -1)}"
APKSIGNER_JAR="${APKSIGNER_JAR:-$TOOLS/apksigner.jar}"

for f in "$AAPT2" "$ANDROID_JAR" "$D8_JAR" "$ECJ_JAR" "$APKSIGNER_JAR"; do
  [[ -e "$f" ]] || { echo "error: missing build tool: $f" >&2; exit 1; }
done

MIN_SDK=21
TARGET_SDK=33

echo "==> 1/7 compiling resources"
rm -rf "$BUILD_DIR/res" && mkdir -p "$BUILD_DIR/res"
"$AAPT2" compile --dir app/src/main/res -o "$BUILD_DIR/res/compiled.zip"

echo "==> 2/7 linking resources and manifest"
rm -f "$BUILD_DIR/base.apk"
"$AAPT2" link \
  -o "$BUILD_DIR/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest app/src/main/AndroidManifest.xml \
  --java "$GEN_DIR" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  -A app/src/main/assets \
  "$BUILD_DIR/res/compiled.zip"

echo "==> 3/7 compiling Java sources"
rm -rf "$CLASS_DIR" && mkdir -p "$CLASS_DIR"
find app/src/main/java "$GEN_DIR" -name '*.java' | sort > "$BUILD_DIR/sources.txt"
"$JAVA_BIN" -jar "$ECJ_JAR" \
  -source 1.8 -target 1.8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$ANDROID_JAR" \
  -nowarn \
  -d "$CLASS_DIR" \
  "@$BUILD_DIR/sources.txt"

echo "==> 4/7 dexing"
rm -rf "$DEX_DIR" && mkdir -p "$DEX_DIR"
find "$CLASS_DIR" -name '*.class' > "$BUILD_DIR/classes.txt"
"$JAVA_BIN" -cp "$D8_JAR" com.android.tools.r8.D8 \
  --release \
  --min-api "$MIN_SDK" \
  --lib "$ANDROID_JAR" \
  --output "$DEX_DIR" \
  $(cat "$BUILD_DIR/classes.txt")

echo "==> 5/7 packaging"
UNSIGNED="$BUILD_DIR/unsigned.apk"
ALIGNED="$BUILD_DIR/aligned.apk"
cp "$BUILD_DIR/base.apk" "$UNSIGNED"
(cd "$DEX_DIR" && zip -q -X "$UNSIGNED" classes.dex)

# zipalign: 4-byte alignment for uncompressed entries, page alignment (16 KiB
# on modern ARM64 devices) for native libraries. Implemented in Python so the
# build does not need the SDK's zipalign binary.
python3 tools/zipalign.py "$UNSIGNED" "$ALIGNED"

echo "==> 6/7 signing"
KEYSTORE="${KEYSTORE:-$ROOT/keystore/laststar-release.jks}"
KEY_ALIAS="${KEY_ALIAS:-laststar}"
KEYSTORE_PASS="${KEYSTORE_PASS:-laststar}"
if [[ ! -f "$KEYSTORE" ]]; then
  echo "    generating keystore $KEYSTORE"
  mkdir -p "$(dirname "$KEYSTORE")"
  "$ROOT/tools/gen_keystore.sh" "$KEYSTORE" "$KEYSTORE_PASS" "$KEY_ALIAS"
fi

OUT_APK="$OUT_DIR/last-star-$VERSION_NAME.apk"
rm -f "$OUT_APK"
"$JAVA_BIN" -jar "$APKSIGNER_JAR" sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:$KEYSTORE_PASS" \
  --key-pass "pass:$KEYSTORE_PASS" \
  --ks-key-alias "$KEY_ALIAS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --min-sdk-version "$MIN_SDK" \
  --out "$OUT_APK" \
  "$ALIGNED"

echo "==> 7/7 verifying"
"$JAVA_BIN" -jar "$APKSIGNER_JAR" verify --verbose --print-certs "$OUT_APK" 2>/dev/null \
  | grep -E "Verified using|Signer #1 certificate DN" || true
"$AAPT2" dump badging "$OUT_APK" | head -6

SIZE=$(stat -c%s "$OUT_APK")
echo
echo "BUILD OK"
echo "  apk      : $OUT_APK"
echo "  size     : $((SIZE / 1024)) KiB"
echo "  version  : $VERSION_NAME ($VERSION_CODE)"
echo "  minSdk   : $MIN_SDK"
echo "  targetSdk: $TARGET_SDK"
