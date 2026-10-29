#!/usr/bin/env bash
# build_android_smali.sh — buduje podpisany Release APK BEZ kompilatora Javy.
#
# Dlaczego tak: w środowisku budowania nie ma działającego kompilatora Javy
# (JDK z PyPI to samo JRE bez javac, ECJ jest nieosiągalny — Maven/Google zablokowane).
# Dlatego kod aplikacji jest asemblowany z SMALI (bajtkod DEX odpowiadający 1:1
# źródłom w android/app/src, wyciągnięty z poprzedniego wydania przez baksmali),
# a zasoby i manifest kompiluje oficjalny aapt2 przez apktool.
#
# Kroki:
#   1. baksmali wydanego APK (albo użycie gotowego drzewa smali)
#   2. podbicie wersji (smali + apktool.yml + manifest)
#   3. apktool/aapt2 → unsigned.apk
#   4. przepakowanie: resources.arsc i classes.dex STORED + wyrównanie 4 B
#   5. kontrole DEX (dexcheck, dexlink, dexverify)
#   6. podpis v1 (JAR) + v2 (własna implementacja tools/apk_sign.py)
#   7. weryfikacja: nasz weryfikator + androguard + apk_installcheck + audyt API
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION_NAME="${VERSION_NAME:-1.0.6}"
VERSION_CODE="${VERSION_CODE:-7}"
BASE_APK="${BASE_APK:-RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk}"
OUT="build/android"
CACHE="${LC_CACHE:-$HOME/.cache/lc}"
JAVA_HOME="${JAVA_HOME:-$(python3 -c 'import jdk4py;print(jdk4py.JAVA_HOME)')}"
JAVA="$JAVA_HOME/bin/java"
[ -f "$CACHE/apktool.jar" ] || { echo "BŁĄD: brak $CACHE/apktool.jar (tools/fetch_tools_extra.sh)" >&2; exit 1; }
[ -x "$CACHE/aapt2" ] || { echo "BŁĄD: brak aapt2" >&2; exit 1; }
set -a; . keystore/keystore.env; set +a

echo "== 1/7 baksmali wydanego APK =="
rm -rf "$OUT/dec"
PATH="$CACHE:$PATH" "$JAVA" -jar "$CACHE/apktool.jar" d "$BASE_APK" -o "$OUT/dec" -f >/dev/null
SMALI_COUNT=$(find "$OUT/dec/smali" -name '*.smali' | wc -l)
echo "   klas smali: $SMALI_COUNT"

echo "== 2/7 wersja $VERSION_NAME ($VERSION_CODE) =="
python3 - "$OUT/dec" "$VERSION_NAME" "$VERSION_CODE" <<'PY'
import os, re, sys
dec, name, code = sys.argv[1], sys.argv[2], sys.argv[3]
# apktool.yml
p = os.path.join(dec, "apktool.yml")
s = open(p, encoding="utf-8").read()
s = re.sub(r"versionCode: \d+", f"versionCode: {code}", s)
s = re.sub(r"versionName: .*", f"versionName: {name}", s)
if "resourcesAreCompressed" not in s:
    s = s.replace("sharedLibrary:", "resourcesAreCompressed: false\nsharedLibrary:")
s = s.replace("resourcesAreCompressed: true", "resourcesAreCompressed: false")
open(p, "w", encoding="utf-8").write(s)
# manifest źródłowy
p = os.path.join(dec, "AndroidManifest.xml")
s = open(p, encoding="utf-8").read()
s = re.sub(r'android:versionCode="\d+"', f'android:versionCode="{code}"', s)
s = re.sub(r'android:versionName="[^"]*"', f'android:versionName="{name}"', s)
open(p, "w", encoding="utf-8").write(s)
# stała VERSION w kodzie (smali)
changed = 0
for root, _d, files in os.walk(os.path.join(dec, "smali")):
    for f in files:
        if not f.endswith(".smali"):
            continue
        q = os.path.join(root, f)
        t = open(q, encoding="utf-8").read()
        if '1.0.5' in t or '1.0.4' in t:
            t2 = t.replace('1.0.5', name).replace('1.0.4', name)
            open(q, "w", encoding="utf-8").write(t2)
            changed += 1
print(f"   wersja w apktool.yml + manifeście; plików smali z numerem wersji: {changed}")
PY

echo "== 3/7 aapt2 + apktool: zasoby i manifest =="
PATH="$CACHE:$PATH" "$JAVA" -jar "$CACHE/apktool.jar" b "$OUT/dec" -o "$OUT/unsigned-raw.apk" --use-aapt2 2>&1 | tail -3

echo "== 4/7 przepakowanie (arsc/dex STORED + 4B) =="
python3 - "$OUT/unsigned-raw.apk" "$OUT/classes.dex" <<'PY'
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1])
open(sys.argv[2], "wb").write(z.read("classes.dex"))
print("   classes.dex:", len(open(sys.argv[2], "rb").read()), "B")
PY
python3 tools/pack_apk.py --base "$OUT/unsigned-raw.apk" --dex "$OUT/classes.dex" --out "$OUT/unsigned.apk"
python3 - "$OUT/classes.dex" "$OUT/unsigned.apk" <<'PY'
import sys, zipfile
dex, apk = sys.argv[1], sys.argv[2]
z = zipfile.ZipFile(apk)
open(dex, "wb").write(z.read("classes.dex"))
for n in ("resources.arsc", "classes.dex"):
    i = z.getinfo(n)
    off = i.header_offset + 30 + len(i.filename.encode()) + len(i.extra)
    assert i.compress_type == 0, f"BŁĄD: {n} jest spakowany"
    assert off % 4 == 0, f"BŁĄD: {n} bez wyrównania 4 B (offset {off})"
    print(f"   {n}: STORED, offset {off} (4B OK)")
PY

echo "== 5/7 kontrole DEX =="
python3 tools/dexcheck.py "$OUT/classes.dex"
python3 tools/dexlink.py "$OUT/classes.dex" "$CACHE/android34.jar"
python3 tools/dexverify.py "$OUT/classes.dex" "$CACHE/android34.jar"

echo "== 6/7 podpis v1 (JAR) + v2 =="
python3 tools/apk_sign.py --in "$OUT/unsigned.apk" \
  --out "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" \
  --ks keystore/wojan-lasercraft.p12 --pass-env WOJAN_KEYSTORE_PASS

echo "== 7/7 weryfikacja niezależna =="
python3 tools/apk_sign.py --verify "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk"
python3 - "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" <<'PY'
import sys
from loguru import logger
logger.remove()
from androguard.core.apk import APK
a = APK(sys.argv[1])
print("   androguard: package", a.get_package(), "| wersja", a.get_androidversion_name(),
      a.get_androidversion_code(), "| minSdk", a.get_min_sdk_version(),
      "| targetSdk", a.get_target_sdk_version())
print(f"   androguard: podpis v1={a.is_signed_v1()} v2={a.is_signed_v2()} v3={a.is_signed_v3()}")
assert a.is_signed_v1() and a.is_signed_v2(), "BŁĄD: brak podpisu v1/v2 wg androguard"
PY
mkdir -p RELEASE
{
  echo "KONTROLA INSTALACYJNA APK — tools/build_android_smali.sh"
  echo "Data: $(date '+%Y-%m-%d %H:%M:%S %Z')   Wersja: $VERSION_NAME ($VERSION_CODE)"
  echo
  JAVA_HOME="$JAVA_HOME" python3 tools/apk_installcheck.py "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk"
  echo
  python3 tools/apk_api_audit.py "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" --min-sdk 24
  echo
  echo "SHA-256: $(sha256sum "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk" | cut -d' ' -f1)"
} 2>&1 | tee RELEASE/KONTROLA-INSTALACJI.txt
echo "OK APK: $OUT/WOJAN-LaserCraft-AI-Android-Release.apk ($(stat -c%s "$OUT/WOJAN-LaserCraft-AI-Android-Release.apk") B)"
