#!/usr/bin/env bash
# build_android.sh — buduje podpisany Release APK bez Android SDK/Gradle.
#
# Łańcuch (wszystko oficjalne narzędzia tam, gdzie to możliwe):
#   ECJ (.java → .class) → j2smali (.class → .smali)
#   → apktool/aapt2 (manifest + res/ + resources.arsc + smali → classes.dex → APK)
#   → kontrole: dexcheck, dexlink, wersja DEX, obecność resources.arsc
#   → apksigner (v1+v2+v3) → weryfikacja
#
# Dlaczego apktool, a nie własne AXML: APK 1.0.0/1.0.1 miały manifest składany
# ręcznie (tools/axmlbuild.py) i ZERO zasobów (brak res/ i resources.arsc).
# apktool 2.9 używa aapt2 do manifestu/zasobów i nowoczesnego smali (dexlib2 3.x)
# do DEX-a — czyli dokładnie tego, co robi Gradle, tylko bez SDK.
set -euo pipefail
cd "$(dirname "$0")/.."

ROOT="$PWD"
OUT="build/android"
CACHE="${LC_CACHE:-$HOME/.cache/lc}"
ECJ="$CACHE/ecj.jar"
ANDROID_JAR="$CACHE/android34.jar"
APKTOOL="$CACHE/apktool.jar"
AAPT2="$CACHE/aapt2"
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

for f in "$ECJ" "$ANDROID_JAR" "$APKTOOL" "$APKSIGNER"; do
  [ -f "$f" ] || { echo "BŁĄD: brak narzędzia $f (uruchom tools/fetch_tools_extra.sh)" >&2; exit 1; }
done
[ -x "$AAPT2" ] || { echo "BŁĄD: brak aapt2 ($AAPT2) — uruchom tools/fetch_tools_extra.sh" >&2; exit 1; }

VERSION_NAME="${VERSION_NAME:-1.0.5}"
VERSION_CODE="${VERSION_CODE:-6}"
PKG="pl.wojan.lasercraft"

rm -rf "$OUT/classes" "$OUT/smali" "$OUT/classes.dex" "$OUT/base.apk" "$OUT/unsigned.apk" "$OUT/proj"
mkdir -p "$OUT/classes" "$OUT/smali"

echo "== 1/6 ECJ (Java 1.8 → klasy JVM) =="
find android/app/src -name '*.java' > "$OUT/sources.txt"
"$JAVA" -jar "$ECJ" -source 1.8 -target 1.8 -bootclasspath "$ANDROID_JAR" \
  -proc:none -nowarn -encoding UTF-8 -d "$OUT/classes" @"$OUT/sources.txt"

echo "== 2/6 j2smali (.class → .smali) =="
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
python3 tools/j2smali/j2smali.py $(cat "$OUT/classes.txt") > "$OUT/j2smali.log" 2>&1 || {
  echo "j2smali FAILED — ostatnie linie:"; tail -20 "$OUT/j2smali.log"; exit 1; }
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

echo "== 3/6 zasoby Androida (ikony + values) i manifest źródłowy =="
python3 tools/make_res.py --res android/res --manifest "$OUT/AndroidManifest.src.xml" \
  --package "$PKG" --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --min-sdk 24 --target-sdk 36

echo "== 4/6 apktool + aapt2: resources.arsc, manifest AXML, classes.dex, assets =="
PROJ="$OUT/proj"
mkdir -p "$PROJ"
cp -r "$OUT/smali" "$PROJ/smali"
cp -r android/res "$PROJ/res"
mkdir -p "$PROJ/assets" && cp -r android/app/assets/. "$PROJ/assets/"
cp "$OUT/AndroidManifest.src.xml" "$PROJ/AndroidManifest.xml"
cat > "$PROJ/apktool.yml" <<YML
!!brut.androlib.meta.MetaInfo
apkFileName: base.apk
isFrameworkApk: false
packageInfo:
  forcedPackageId: '127'
  renameManifestPackage: null
sdkInfo:
  minSdkVersion: '24'
  targetSdkVersion: '36'
usesFramework:
  ids:
  - 1
  tag: null
versionInfo:
  versionCode: '$VERSION_CODE'
  versionName: $VERSION_NAME
resourcesAreCompressed: false
YML
PATH="$(dirname "$AAPT2"):$PATH" "$JAVA" -jar "$APKTOOL" b "$PROJ" -o "$OUT/unsigned.apk" --use-aapt2 2>&1 | tail -6

# Android 11+ (targetSdk >= 30) NIE zainstaluje APK, w którym resources.arsc jest
# spakowany albo nie jest wyrównany do 4 B:
#   INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED
#   „Targeting R+ … requires the resources.arsc of installed APKs to be stored
#    uncompressed and aligned on a 4-byte boundary" (ParsingPackageUtils)
# apktool pakuje arsc deflate, więc przepakowujemy: arsc i dex bez kompresji, wyrównane.
python3 - "$OUT/unsigned.apk" "$OUT/classes.dex" <<'PYDEX'
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
open(sys.argv[2], "wb").write(z.read("classes.dex"))
print("classes.dex z apktool:", len(open(sys.argv[2], "rb").read()), "B")
PYDEX
python3 tools/pack_apk.py --base "$OUT/unsigned.apk" --dex "$OUT/classes.dex" \
  --out "$OUT/unsigned-aligned.apk"
mv "$OUT/unsigned-aligned.apk" "$OUT/unsigned.apk"

echo "== 5/6 kontrole DEX i zawartości APK =="
python3 - "$OUT/unsigned.apk" "$OUT/classes.dex" <<'PYEOF'
import sys, zipfile
apk, dex_out = sys.argv[1], sys.argv[2]
z = zipfile.ZipFile(apk)
names = z.namelist()
need = ["AndroidManifest.xml", "classes.dex", "resources.arsc"]
missing = [n for n in need if n not in names]
if missing:
    sys.exit(f"BŁĄD: w APK brakuje {missing}")
if not any(n.startswith("res/") for n in names):
    sys.exit("BŁĄD: w APK brak katalogu res/ (ikona, style)")
info = z.getinfo("resources.arsc")
arsc_off = info.header_offset + 30 + len(info.filename.encode()) + len(info.extra)
if info.compress_type != 0:
    sys.exit("BŁĄD: resources.arsc jest spakowany (deflate) — Android 11+ odrzuci instalację")
if arsc_off % 4 != 0:
    sys.exit(f"BŁĄD: resources.arsc pod adresem {arsc_off} nie jest wyrównany do 4 B")
print(f"resources.arsc: STORED, offset {arsc_off} (4B OK)")
data = z.read("classes.dex")
open(dex_out, "wb").write(data)
di = z.getinfo("classes.dex")
dex_off = di.header_offset + 30 + len(di.filename.encode()) + len(di.extra)
if di.compress_type == 0 and dex_off % 4 != 0:
    sys.exit(f"BŁĄD: classes.dex niepakowany pod adresem {dex_off} nie jest wyrównany do 4 B")
print(f"classes.dex: {'STORED' if di.compress_type == 0 else 'deflate'}, offset {dex_off}")
magic = data[:8]
ok = magic[4:7] in (b"035", b"037", b"038", b"039", b"040", b"041")
print(f"APK: {len(names)} wpisów, classes.dex magic={magic!r} -> {'OK' if ok else 'BŁĄD (ART odrzuci)'}")
if not ok:
    sys.exit(1)
PYEOF
python3 tools/dexcheck.py "$OUT/classes.dex"
python3 tools/dexlink.py "$OUT/classes.dex" "$ANDROID_JAR"
python3 tools/dexverify.py "$OUT/classes.dex" "$ANDROID_JAR"

echo "== 6/6 podpis (v1+v2+v3) i weryfikacja =="
KS="keystore/wojan-lasercraft.p12"
[ -f "$KS" ] || { echo "BŁĄD: brak keystora $KS" >&2; exit 1; }
"$JAVA" -jar "$APKSIGNER" sign --ks "$KS" \
  --ks-pass "pass:${WOJAN_KEYSTORE_PASS:?ustaw WOJAN_KEYSTORE_PASS}" \
  --key-pass "pass:${WOJAN_KEYSTORE_PASS}" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" "$OUT/unsigned.apk"
"$JAVA" -jar "$APKSIGNER" verify --min-sdk-version 21 --verbose --print-certs \
  "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" 2>&1 | grep -v "WARNING" | head -14 || true

echo "== 6b/6 kontrola instalacyjna i audyt API (na GOTOWYM, podpisanym APK) =="
# apk_installcheck: warunki instalacji Androida 11–17 (arsc STORED+4B, targetSdk, podpis v2/v3,
# DEX-version, activity startowa, package/versionCode). apk_api_audit: żadna klasa platformy
# nie jest nowsza niż minSdk. Oba narzędzia pracują na pliku, który użytkownik dostaje.
"$JAVA" -version >/dev/null 2>&1
mkdir -p RELEASE
{
  echo "KONTROLA INSTALACYJNA APK — generowane przez tools/build_android.sh"
  echo "Data: $(date '+%Y-%m-%d %H:%M:%S %Z')   Wersja: $VERSION_NAME ($VERSION_CODE)"
  echo
  JAVA_HOME="$JAVA_HOME" python3 tools/apk_installcheck.py "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" || exit 1
  echo
  python3 tools/apk_api_audit.py "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" --min-sdk 24 || exit 1
  echo
  echo "SHA-256: $(sha256sum "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" | cut -d' ' -f1)"
} 2>&1 | tee RELEASE/KONTROLA-INSTALACJI.txt

ls -l "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk"
echo "OK APK: $OUT/WOJAN-LaserCraft-AI-Android-Release.apk"
