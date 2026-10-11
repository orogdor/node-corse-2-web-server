#!/usr/bin/env bash
# Build a SIDE-BY-SIDE Joolin2 APK from the user's own original APK.
# Required: original APK, Apktool v2.12.1 jar, Android SDK 35 + build tools 35.
set -euo pipefail
SOURCE_APK="${1:?Usage: bash joolin2/build.sh /path/to/Joolink-original-base.apk}"
APKTOOL_JAR="${APKTOOL_JAR:?APKTOOL_JAR required}"
ANDROID_HOME="${ANDROID_HOME:?ANDROID_HOME required}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${RUNNER_TEMP:-/tmp}/joolin2-build"
OUT="${GITHUB_WORKSPACE:-$ROOT}/Joolin2-Decoder-Test.apk"
BUILDTOOLS="$ANDROID_HOME/build-tools/35.0.0"
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
test -s "$SOURCE_APK" && test -s "$APKTOOL_JAR" && test -s "$ANDROID_JAR"
for executable in d8 zipalign apksigner;do test -x "$BUILDTOOLS/$executable";done
rm -rf "$WORK"
mkdir -p "$WORK" "$WORK/bin" "$WORK/compiled" "$WORK/helperdex"
echo '[1/8] Decode APK bytecode, preserving resources if possible'
java -Xmx4g -jar "$APKTOOL_JAR" d -f -r --no-debug-info "$SOURCE_APK" -o "$WORK/decoded"
if ! head -c 7 "$WORK/decoded/AndroidManifest.xml" | grep -qa '<?xml\|<mani';then
    echo 'Binary manifest still present: decode full Android resource table'
    rm -rf "$WORK/decoded"
    java -Xmx4g -jar "$APKTOOL_JAR" d -f --no-debug-info "$SOURCE_APK" -o "$WORK/decoded"
fi
echo '[2/8] Distinct Joolin2 ID + video menu hooks'
python3 "$ROOT/patch.py" --decoded "$WORK/decoded"
echo '[3/8] Repackage modified APK'
java -Xmx4g -jar "$APKTOOL_JAR" b "$WORK/decoded" -o "$WORK/unsigned.apk"
echo '[4/8] Build VideoSettings Java helper'
javac --release 8 -cp "$ANDROID_JAR" -d "$WORK/compiled" $(find "$ROOT/src" -name '*.java' -print)
"$BUILDTOOLS/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$WORK/helperdex" $(find "$WORK/compiled" -name '*.class')
echo '[5/8] Insert helper as new DEX module'
python3 - "$WORK/unsigned.apk" "$WORK/helperdex/classes.dex" <<'PY'
import sys,zipfile
apk,component=sys.argv[1:]
with zipfile.ZipFile(apk,'a') as z:
    exists=set(z.namelist())
    i=2
    while f'classes{i}.dex' in exists:i+=1
    z.write(component,f'classes{i}.dex',compress_type=zipfile.ZIP_DEFLATED)
    print('Extra DEX: classes%d.dex'%i)
PY
echo '[6/8] Zipalign'
"$BUILDTOOLS/zipalign" -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
echo '[7/8] Test APK signing; this is NOT the original vendor signature'
# The key is a test signing key. Optionally provide a secure persistent keystore
# through the Android CI environment for installable upgrades.
if [[ -n "${JOOLIN2_KEYSTORE_B64:-}" ]];then
    echo "$JOOLIN2_KEYSTORE_B64" | base64 -d > "$WORK/key.jks"
    KEY_ALIAS="${JOOLIN2_KEY_ALIAS:?KEY_ALIAS required}"
    KEY_PASS="${JOOLIN2_KEY_PASSWORD:?KEY_PASSWORD required}"
else
    KEY_ALIAS="joolin2test";KEY_PASS="android"
    keytool -genkeypair -noprompt -storetype PKCS12 \
        -keystore "$WORK/key.jks" -storepass "$KEY_PASS" -keypass "$KEY_PASS" \
        -alias "$KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 3650 \
        -dname 'CN=Joolin2 experimental, O=Private Camera Test, C=EE' >/dev/null 2>&1
fi
"$BUILDTOOLS/apksigner" sign --ks "$WORK/key.jks" \
    --ks-key-alias "$KEY_ALIAS" --ks-pass "pass:$KEY_PASS" \
    --key-pass "pass:$KEY_PASS" --out "$OUT" "$WORK/aligned.apk"
echo '[8/8] Validate signing and APK properties'
"$BUILDTOOLS/apksigner" verify --verbose "$OUT"
"$BUILDTOOLS/aapt" dump badging "$OUT" | grep -E "package:|application-label:"
"$BUILDTOOLS/aapt" dump permissions "$OUT" > "$WORK/permissions.txt"
if grep -E 'com[.]jooan[.]qiaoanzhilian[.]gp[.](permission[.]|AGOO|ACCS)' "$WORK/permissions.txt";then
    echo "ERROR: duplicate vendor permission names found, refusing to publish Joolin2"
    exit 1
fi
grep 'ee.joolin2.decoder' "$WORK/permissions.txt" | head -n 14 || true
ls -lh "$OUT"
echo "Joolin2 APK: $OUT"
