#!/usr/bin/env bash
# restore_toolchain.sh — odtwarza CAŁY łańcuch narzędzi do TRWAŁEGO katalogu
# /home/user/toolchain (poza repozytorium, więc nie zajmuje gita) i linkuje go
# do ~/.cache/lc, gdzie szukają go skrypty budowania.
#
# Dlaczego: środowisko budowania jest okresowo czyszczone (ginie ~/.cache i pakiety
# pip). Po wyczyszczeniu uruchom ten jeden skrypt — 2–3 minuty i wszystko wraca.
set -euo pipefail
TC="${TC:-/home/user/toolchain}"
mkdir -p "$TC/bin" "$TC/models" "$TC/src"
echo "== JDK + Zig (PyPI: jdk4py, ziglang) =="
python3 -m pip install --break-system-packages -q jdk4py ziglang cryptography 2>&1 | tail -1 || true
JDK="$(python3 -c 'import jdk4py;print(jdk4py.JAVA_HOME)')"
[ -d "$TC/jdk" ] || cp -r "$JDK" "$TC/jdk"
echo "   JDK: $TC/jdk"

echo "== aapt2 (PyPI wheel) =="
if [ ! -x "$TC/bin/aapt2" ]; then
  python3 -m pip download aapt2 -d /tmp/tc_aapt2 --no-deps -q
  python3 - "$TC/bin/aapt2" <<'PY'
import sys, glob, zipfile, os
whl = glob.glob('/tmp/tc_aapt2/aapt2-*.whl')[0]
with zipfile.ZipFile(whl) as z:
    data = z.read("aapt2/bin/Linux/aapt2")
open(sys.argv[1], "wb").write(data)
os.chmod(sys.argv[1], 0o755)
PY
fi
echo "   $($TC/bin/aapt2 version)"

echo "== apktool (npm) =="
if [ ! -f "$TC/bin/apktool.jar" ]; then
  mkdir -p /tmp/tc_apt && cd /tmp/tc_apt && npm pack @postar/apktool-node >/dev/null 2>&1 && tar xzf postar-apktool-node-*.tgz && cp package/lib/apktool.jar "$TC/bin/apktool.jar" && cd - >/dev/null
fi

echo "== uber-apk-signer 1.3.0 (zawiera oficjalny apksig Google) =="
if [ ! -f "$TC/bin/uber-apk-signer.jar" ]; then
  python3 -m pip download simple-apk-signer -d /tmp/tc_sas --no-deps -q
  python3 - "$TC/bin/uber-apk-signer.jar" <<'PY'
import sys, glob, zipfile
whl = glob.glob('/tmp/tc_sas/simple_apk_signer-*.whl')[0]
with zipfile.ZipFile(whl) as z:
    n = [x for x in z.namelist() if x.endswith("uber-apk-signer-1.3.0.jar")][0]
    open(sys.argv[1], "wb").write(z.read(n))
PY
fi

echo "== android.jar API 34 (GitHub blob, Sable/android-platforms) =="
if [ ! -f "$TC/bin/android34.jar" ]; then
  sha=$(gh api "repos/Sable/android-platforms/contents/android-34/android.jar" --jq .sha)
  gh api "repos/Sable/android-platforms/git/blobs/$sha" --jq .content | base64 -d > "$TC/bin/android34.jar"
fi

echo "== ImGui 1.91.5 (codeload) =="
if [ ! -d "$TC/src/imgui-1.91.5" ]; then
  ( cd "$TC/src" && curl -sSL -o imgui.tar.gz "https://codeload.github.com/ocornut/imgui/tar.gz/refs/tags/v1.91.5" && tar xzf imgui.tar.gz && rm imgui.tar.gz )
fi

echo "== dowiązanie do ~/.cache/lc (tu szukają skrypty) =="
mkdir -p "$HOME/.cache/lc"
ln -sfn "$TC/bin/aapt2" "$HOME/.cache/lc/aapt2"
ln -sfn "$TC/bin/apktool.jar" "$HOME/.cache/lc/apktool.jar"
ln -sfn "$TC/bin/uber-apk-signer.jar" "$HOME/.cache/lc/uber-apk-signer.jar"
ln -sfn "$TC/bin/android34.jar" "$HOME/.cache/lc/android34.jar"
ln -sfn "$TC/src" "$HOME/.cache/lc/src" 2>/dev/null || true
echo "OK — toolchain gotowy w $TC"
