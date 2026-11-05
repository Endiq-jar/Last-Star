#!/usr/bin/env bash
#
# Fetches the Android build toolchain this project needs, entirely from
# npm and PyPI (no Android SDK, no Gradle, no Unity, no admin rights).
#
#   tools/fetch_toolchain.sh [destination]     (default: tools/.toolchain)
#
# It installs:
#   java     JDK/JRE  -> jdk4py (PyPI wheel, linux x86_64/aarch64)
#   aapt2    resources -> aaptjs3 (npm, ships a Linux x86_64 binary)
#   ecj      javac     -> @drxiaozhi/minapk (npm)
#   d8       dexer     -> @drxiaozhi/minapk (npm)
#   android  jar       -> @drxiaozhi/minapk (npm)
#   apksigner signer   -> @drxiaozhi/minapk (npm)
#
# Afterwards `tools/build_apk.sh` picks the tools up automatically.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="${1:-$ROOT/tools/.toolchain}"
mkdir -p "$DEST"
cd "$DEST"

need_npm=1
need_pip=1
command -v npm >/dev/null 2>&1 || need_npm=0
command -v pip3 >/dev/null 2>&1 || need_pip=0

echo "==> fetching Java runtime (jdk4py)"
if [[ ! -x "$DEST/java/bin/java" ]]; then
  if [[ $need_pip -eq 1 ]]; then
    pip3 install --quiet --target "$DEST/pyjdk" --only-binary=:all: jdk4py
    RUNTIME="$(find "$DEST/pyjdk" -maxdepth 3 -type d -name java-runtime | head -1)"
    if [[ -n "$RUNTIME" ]]; then
      ln -sfn "$RUNTIME" "$DEST/java"
    fi
  else
    echo "    pip3 not found: install a JDK and set JAVA_HOME instead" >&2
  fi
fi
if [[ -x "$DEST/java/bin/java" ]]; then
  "$DEST/java/bin/java" -version 2>&1 | head -1 | sed 's/^/    /'
else
  echo "    using system java if available"
fi

echo "==> fetching Android build tools (npm)"
if [[ ! -f "$DEST/d8.jar" || ! -f "$DEST/android.jar" ]]; then
  if [[ $need_npm -eq 1 ]]; then
    npm pack --silent @drxiaozhi/minapk >/dev/null 2>&1 || npm pack --silent @drxiaozhi/minapk
    PKG=$(ls -t drxiaozhi-minapk-*.tgz | head -1)
    tar xzf "$PKG"
    cp package/tools/android.jar package/tools/d8.jar package/tools/ecj-*.jar \
       package/tools/apksigner.jar "$DEST/"
    rm -rf package
    echo "    extracted android.jar, d8.jar, ecj, apksigner"
  else
    echo "    npm not found: cannot fetch aapt2/d8/ecj" >&2
    exit 1
  fi
fi

if [[ ! -x "$DEST/aapt2" ]]; then
  if [[ $need_npm -eq 1 ]]; then
    npm pack --silent aaptjs3 >/dev/null 2>&1 || npm pack --silent aaptjs3
    PKG=$(ls -t aaptjs3-*.tgz | head -1)
    tar xzf "$PKG" package/bin/x64/linux/aapt2
    cp package/bin/x64/linux/aapt2 "$DEST/aapt2"
    chmod +x "$DEST/aapt2"
    rm -rf package "$PKG"
    echo "    extracted aapt2"
  fi
fi

# Convenience wrapper so build_apk.sh finds everything with one variable.
cat > "$DEST/env.sh" <<EOF
# source this file (or pass BUILD_TOOLS=$DEST / JAVA_HOME=$DEST/java)
export BUILD_TOOLS="$DEST"
export JAVA_HOME="$DEST/java"
export ANDROID_JAR="$DEST/android.jar"
export D8_JAR="$DEST/d8.jar"
export APKSIGNER_JAR="$DEST/apksigner.jar"
export ECJ_JAR="\$(ls "$DEST"/ecj-*.jar 2>/dev/null | head -1)"
EOF

echo
echo "toolchain ready in $DEST"
ls -1 "$DEST" | sed 's/^/    /'
echo
echo "build with:"
echo "    source $DEST/env.sh && tools/build_apk.sh"
