#!/usr/bin/env bash
# fetch_tools_extra.sh — dociąga NARZĘDZIA Androida potrzebne przez build_android.sh:
#   * aapt2 (oficjalny kompilator zasobów/manifestu)  — z PyPI: pakiet `aapt2` (wheel z binarką)
#   * apktool 2.9.3 (w środku: smali + dexlib2 3.x)   — z npm: pakiet `@postar/apktool-node`
#
# Dlaczego właśnie te: dają DOKŁADNIE taki APK jak Gradle (resources.arsc, AXML, DEX 037),
# bez instalowania Android SDK i bez Gradle.
#
# Wymaga: python3 + pip, node/npm (tylko do rozpakowania paczki), unzip/tar.
set -euo pipefail
CACHE="${LC_CACHE:-$HOME/.cache/lc}"
mkdir -p "$CACHE"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "== aapt2 =="
if [ -x "$CACHE/aapt2" ]; then
  echo "  już jest: $CACHE/aapt2"
else
  python3 -m pip download aapt2 -d "$TMP" --no-deps -q
  python3 - "$TMP" "$CACHE/aapt2" <<'PY'
import sys, glob, zipfile, os, stat
whl = glob.glob(os.path.join(sys.argv[1], "aapt2-*.whl"))[0]
dst = sys.argv[2]
with zipfile.ZipFile(whl) as z:
    data = z.read("aapt2/bin/Linux/aapt2")
open(dst, "wb").write(data)
os.chmod(dst, os.stat(dst).st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
print("  zapisano", dst, len(data), "B")
PY
fi
"$CACHE/aapt2" version || { echo "BŁĄD: aapt2 nie działa" >&2; exit 1; }

echo "== apktool =="
if [ -f "$CACHE/apktool.jar" ]; then
  echo "  już jest: $CACHE/apktool.jar"
else
  ( cd "$TMP" && npm pack @postar/apktool-node >/dev/null 2>&1 )
  tar xzf "$TMP"/postar-apktool-node-*.tgz -C "$TMP"
  cp "$TMP/package/lib/apktool.jar" "$CACHE/apktool.jar"
  echo "  zapisano $CACHE/apktool.jar"
fi
export JAVA_HOME="${JAVA_HOME:-$(python3 -c 'import jdk4py;print(jdk4py.JAVA_HOME)' 2>/dev/null || true)}"
"$JAVA_HOME/bin/java" -jar "$CACHE/apktool.jar" --version 2>&1 | head -2 || true
echo "OK: narzędzia w $CACHE"

echo "== uber-apk-signer (prawdziwy apksig Google: v1+v2+v3 + zipalign) =="
if [ -f "$CACHE/uber-apk-signer.jar" ]; then
  echo "  już jest: $CACHE/uber-apk-signer.jar"
else
  python3 -m pip download simple-apk-signer -d "$TMP" --no-deps -q
  python3 - "$TMP" "$CACHE/uber-apk-signer.jar" <<'PY'
import sys, glob, zipfile, os
whl = glob.glob(os.path.join(sys.argv[1], "simple_apk_signer-*.whl"))[0]
with zipfile.ZipFile(whl) as z:
    name = [n for n in z.namelist() if n.endswith("uber-apk-signer-1.3.0.jar")][0]
    data = z.read(name)
open(sys.argv[2], "wb").write(data)
print("  zapisano", sys.argv[2], len(data), "B")
PY
fi
"$JAVA_HOME/bin/java" -jar "$CACHE/uber-apk-signer.jar" --version 2>&1 | head -1
