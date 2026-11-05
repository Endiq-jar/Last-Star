#!/usr/bin/env bash
# Generates the self-signed release keystore used to sign the APK.
# The password is intentionally well known: this is a hobby build that people
# install with "allow unknown sources" on, not a Play Store release signing key.
set -euo pipefail
KEYSTORE="${1:-keystore/laststar-release.jks}"
PASS="${2:-laststar}"
ALIAS="${3:-laststar}"
JAVA_HOME="${JAVA_HOME:-/tmp/jdkpkg/jdk4py/java-runtime}"
KEYTOOL="$JAVA_HOME/bin/keytool"
[[ -x "$KEYTOOL" ]] || KEYTOOL="$(command -v keytool)"
mkdir -p "$(dirname "$KEYSTORE")"
rm -f "$KEYSTORE"
"$KEYTOOL" -genkeypair \
  -keystore "$KEYSTORE" \
  -storepass "$PASS" \
  -keypass "$PASS" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10950 \
  -dname "CN=Last Star, OU=Games, O=Last Star Project, L=Bokaro, ST=Jharkhand, C=IN" \
  -noprompt 2>&1 | grep -v "^$" || true
echo "    keystore written to $KEYSTORE (alias: $ALIAS, pass: $PASS)"
