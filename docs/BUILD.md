# Building Last Star from source

This project produces a signed, installable APK **without Unity, Gradle or the
Android SDK**. Everything it needs comes from npm and PyPI.

## 1. Get a toolchain

```bash
tools/fetch_toolchain.sh          # downloads into tools/.toolchain
source tools/.toolchain/env.sh
```

That script fetches:

| Piece | What it is | Where it comes from |
|---|---|---|
| `java` | JDK/JRE (Temurin 25 packaged for pip) | PyPI `jdk4py` |
| `aapt2` | Android resource compiler/linker | npm `aaptjs3` |
| `ecj-*.jar` | Eclipse batch Java compiler | npm `@drxiaozhi/minapk` |
| `d8.jar` | Dex compiler (Java bytecode → DEX) | npm `@drxiaozhi/minapk` |
| `android.jar` | API 34 platform stubs | npm `@drxiaozhi/minapk` |
| `apksigner.jar` | APK signer (v1/v2/v3) | npm `@drxiaozhi/minapk` |

If you already have a JDK and an Android SDK, skip the script and point the build
at your own tools:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_JAR=$ANDROID_HOME/platforms/android-34/android.jar
export BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0   # needs aapt2, d8.jar, apksigner.jar
export ECJ_JAR=/path/to/ecj.jar                       # or use javac, see below
tools/build_apk.sh
```

## 2. Build

```bash
tools/build_apk.sh
# ==> 1/7 compiling resources
# ...
# BUILD OK
#   apk      : apk/last-star-1.0.0.apk
#   size     : 249 KiB
#   minSdk   : 21
```

Useful flags:

```bash
tools/build_apk.sh --version-name 1.1.0 --version-code 2
```

## 3. Test

```bash
tools/run_tests.sh           # headless suite + regenerate screenshots
tools/run_tests.sh --tests   # headless suite only
```

The suite runs the real simulation: terrain, camera matrices, movement, combat,
enemy AI, pickup/victory/defeat state machine, mesh index validity, the HUD font
and a bot that plays a full 6-shard run.

## 4. Install

```bash
adb install -r apk/last-star-1.0.0.apk
```

Or copy the APK to the phone and open it, allowing installs from that source.

## What each build stage does

```
app/src/main/res ──► aapt2 compile ──► compiled.zip
                                  │
AndroidManifest.xml ──────────────┴──► aapt2 link ──► base.apk (+ R.java)
                                                        │
app/src/main/java ──► ecj ──► *.class ──► d8 ──► classes.dex
                                                        │
                                  base.apk + classes.dex ┴──► unsigned.apk
                                                        │
                              zipalign.py (4 B / 16 KiB page) ──► aligned.apk
                                                        │
                                    apksigner (v1+v2+v3) ──► last-star.apk
```

`tools/zipalign.py` is a reimplementation of the SDK tool: uncompressed entries
are 4-byte aligned (`resources.arsc` *must* be STORED and aligned for Android
11+) and native libraries get 16 KiB page alignment.

## Signing

`tools/gen_keystore.sh` creates `keystore/laststar-release.jks` on the first
build:

* alias `laststar`, password `laststar` (store and key)
* RSA 2048, valid 30 years, `CN=Last Star, O=Last Star Project, C=IN`

Because the same key is reused across builds, upgrading installs keep the app's
saved preferences. Replace it with your own key if you plan to publish:

```bash
KEYSTORE=~/my-release.jks KEYSTORE_PASS=secret KEY_ALIAS=me tools/build_apk.sh
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `could not find aapt2` | run `tools/fetch_toolchain.sh` and `source tools/.toolchain/env.sh` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | an older build with a different signature is installed: `adb uninstall com.laststar.game` |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | impossible here — the APK contains no native code, so every ABI works |
| Black screen on an emulator | the emulator must support GLES 2.0 (all modern ones do; enable GPU host rendering) |
| `apksigner` prints a JDK native-access warning | harmless; add `--enable-native-access=ALL-UNNAMED` to silence it |
| App is not fullscreen on a notched phone | by design: it draws behind the cutout; the HUD keeps its margins |

## Using Java's own javac instead of ecj

`ecj` is used because it runs from a JRE-only installation. With a full JDK:

```bash
javac -source 1.8 -target 1.8 -bootclasspath "$ANDROID_JAR" -d build/classes @build/sources.txt
```

Everything else in `build_apk.sh` stays the same.
