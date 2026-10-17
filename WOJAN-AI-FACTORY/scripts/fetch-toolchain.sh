#!/usr/bin/env bash
# fetch-toolchain.sh — pobiera narzędzia budowy WOJAN bez Google SDK.
# Źródła (zgodne z polityką sieciową budowniczego): GitHub Contents API, npm, PyPI.
# Cache: ~/.cache/wojan-tools (poza repozytorium).
set -euo pipefail

CACHE="${WOJAN_TOOLCACHE:-$HOME/.cache/wojan-tools}"
mkdir -p "$CACHE"
cd "$CACHE"

echo "== narzędzia Java/Android (GitHub Contents API, realne blobi) =="
fetch_blob() { # repo sha out
  local repo="$1" sha="$2" out="$3"
  if [ -s "$out" ] && [ "$(stat -c%s "$out")" -gt 10000 ]; then
    echo "  jest: $out"; return 0
  fi
  echo "  pobieram $out"
  curl -sf "https://api.github.com/repos/$repo/git/blobs/$sha" \
    | jq -r '.content' | tr -d '\n' | base64 -d > "$out"
}

# ECJ 3.37.0 (kompilator Java) — blob w repo Ocean-Moist/dots
fetch_blob "Ocean-Moist/dots" "5526d047d85f5e1beefae9b7f5c31508a0393c45" "ecj.jar"
# smali 1.3.3 + baksmali (asembler/disassembler dex) — burnsra/android_tools
fetch_blob "burnsra/android_tools" "PLACEHOLDER_SMALI" "smali.jar" || true

# smali/baksmali/apksigner — pobieranie przez contents API (ścieżka)
fetch_file() { # repo path out
  local repo="$1" path="$2" out="$3"
  if [ -s "$out" ] && [ "$(stat -c%s "$out")" -gt 10000 ]; then
    echo "  jest: $out"; return 0
  fi
  echo "  pobieram $out"
  curl -sf -H "Accept: application/vnd.github.raw" \
    "https://api.github.com/repos/$repo/contents/$path" -o "$out"
}
fetch_file "burnsra/android_tools" "bin/smali-1.3.3.jar" "smali.jar"
fetch_file "burnsra/android_tools" "bin/baksmali-1.3.3.jar" "baksmali.jar"
fetch_file "patrickfav/uber-apk-signer" "src/main/resources/lib/apksigner_33_0_2.jar" "apksigner.jar"

# android.jar (klasy API Androida do kompilacji) — git blob w Sable/android-platforms
if [ ! -s android34.jar ] || [ "$(stat -c%s android34.jar)" -lt 1000000 ]; then
  echo "  pobieram android34.jar (26 MB)"
  curl -sf "https://api.github.com/repos/Sable/android-platforms/git/blobs/923bafccf73aef996495c559e919ad8be3cabd58" \
    | jq -r '.content' | tr -d '\n' | base64 -d > android34.jar
fi

echo "== środowisko Python (PyPI) =="
python3 -m venv "${WOJAN_VENV:-$HOME/.cache/wojan-venv}" 2>/dev/null || true
"${WOJAN_VENV:-$HOME/.cache/wojan-venv}/bin/pip" install -q --disable-pip-version-check \
  pyaxml cryptography jdk4py ziglang androguard 2>&1 | tail -1

echo "== źródła C/C++ (codeload: imgui + quickjs-ng) =="
mkdir -p src
fetch_src() { # repo ref outdir
  local repo="$1" ref="$2" outdir="$3"
  if [ -d "$outdir" ] && [ -n "$(ls -A "$outdir" 2>/dev/null)" ]; then
    echo "  jest: $outdir"; return 0
  fi
  echo "  pobieram $repo@$ref -> $outdir"
  curl -sfL "https://codeload.github.com/$repo/tar.gz/$ref" -o src/tmp.tgz
  mkdir -p src/tmpx
  tar -xzf src/tmp.tgz -C src/tmpx
  local top
  top="$(ls src/tmpx | head -1)"
  rm -rf "$outdir"
  mv "src/tmpx/$top" "$outdir"
  rm -rf src/tmpx src/tmp.tgz
}
fetch_src "ocornut/imgui" "master" "src/imgui-master"
fetch_src "quickjs-ng/quickjs" "master" "src/quickjs-master"

echo "== gotowe =="
ls -la "$CACHE"
