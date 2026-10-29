#!/usr/bin/env bash
# build_android.sh — buduje podpisany Release APK (bez Android SDK / Gradle).
# Łańcuch: ECJ → j2smali → smali → dexcheck → AXML → ZIP → apksigner (v1+v2+v3)
set -euo pipefail
cd "$(dirname "$0")/.."

ROOT="$PWD"
OUT="build/android"
CACHE="${LC_CACHE:-$HOME/.cache/lc}"
ECJ="$CACHE/ecj.jar"
ANDROID_JAR="$CACHE/android34.jar"
SMALI="$CACHE/smali.jar"
APKSIGNER="$CACHE/apksigner.jar"

# --- Java ---
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -x /tmp/v1/bin/python ]; then
    JAVA_HOME="$(/tmp/v1/bin/python -c 'import jdk4py;print(jdk4py.JAVA_HOME)' 2>/dev/null || true)"
  fi
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "BŁĄD: brak JAVA_HOME (zainstaluj JDK lub: pip install jdk4py)" >&2
  exit 1
fi
JAVA="$JAVA_HOME/bin/java"
"$JAVA" -version >/dev/null 2>&1 || { echo "BŁĄD: java nie działa ($JAVA)" >&2; exit 1; }

for f in "$ECJ" "$ANDROID_JAR" "$SMALI" "$APKSIGNER"; do
  [ -f "$f" ] || { echo "BŁĄD: brak narzędzia $f (uruchom tools/fetch_toolchain.sh)" >&2; exit 1; }
done

VERSION_NAME="${VERSION_NAME:-1.0.0}"
VERSION_CODE="${VERSION_CODE:-1}"

rm -rf "$OUT/classes" "$OUT/smali" "$OUT/classes.dex" "$OUT/base.apk" "$OUT/unsigned.apk"
mkdir -p "$OUT/classes" "$OUT/smali"

echo "== 1/6 ECJ (Java 1.8 → klasy JVM) =="
find android/app/src -name '*.java' > "$OUT/sources.txt"
"$JAVA" -jar "$ECJ" -source 1.8 -target 1.8 -bootclasspath "$ANDROID_JAR" \
  -proc:none -nowarn -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt"

echo "== 2/6 j2smali (.class → .smali) =="
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
python3 tools/j2smali/j2smali.py $(cat "$OUT/classes.txt") > "$OUT/j2smali.log" 2>&1 || {
  echo "j2smali FAILED — ostatnie linie:"; tail -20 "$OUT/j2smali.log"; exit 1; }
grep -c '\.smali' "$OUT/j2smali.log" || true

echo "== 3/6 struktura smali → classes.dex =="
python3 - "$OUT" <<'PYEOF'
import os, shutil, sys
out = sys.argv[1]
src, dst = os.path.join(out, "classes"), os.path.join(out, "smali")
n = 0
for root, _d, files in os.walk(src):
    rel = os.path.relpath(root, src)
    for f in files:
        if not f.endswith(".smali"):
            continue
        d = os.path.join(dst, rel)
        os.makedirs(d, exist_ok=True)
        shutil.copyfile(os.path.join(root, f), os.path.join(d, f))
        n += 1
print(f"pliki smali: {n}")
PYEOF
"$JAVA" -jar "$SMALI" -a 24 -o "$OUT/classes.dex" "$OUT/smali" 2>&1 | tail -5
python3 tools/dexcheck.py "$OUT/classes.dex"

echo "== 4/6 AndroidManifest.xml (AXML) =="
/tmp/v1/bin/python tools/axmlbuild.py --out "$OUT/AndroidManifest.xml" \
  --package pl.wojan.lasercraft --label "WOJAN LaserCraft AI" \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --min-sdk 24 --target-sdk 36 \
  --permission android.permission.INTERNET \
  --permission android.permission.RECORD_AUDIO \
  --activity pl.wojan.lasercraft.MainActivity

echo "== 5/6 pakowanie APK =="
python3 tools/pack_apk.py --out "$OUT/unsigned.apk" \
  --manifest "$OUT/AndroidManifest.xml" --dex "$OUT/classes.dex" \
  --assets android/app/assets

echo "== 6/6 podpis (v1+v2+v3) i weryfikacja =="
KS="keystore/wojan-lasercraft.p12"
[ -f "$KS" ] || { echo "BŁĄD: brak keystora $KS" >&2; exit 1; }
"$JAVA" -jar "$APKSIGNER" sign --ks "$KS" \
  --ks-pass "pass:${WOJAN_KEYSTORE_PASS:?ustaw WOJAN_KEYSTORE_PASS}" \
  --key-pass "pass:${WOJAN_KEYSTORE_PASS}" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" "$OUT/unsigned.apk"
"$JAVA" -jar "$APKSIGNER" verify --verbose --print-certs \
  "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" 2>&1 | head -20 || true

ls -l "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk"
echo "OK APK: $OUT/WOJAN-LaserCraft-AI-Android-Release.apk"
