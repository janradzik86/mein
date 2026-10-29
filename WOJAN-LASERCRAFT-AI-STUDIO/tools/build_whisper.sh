#!/usr/bin/env bash
# build_whisper.sh — kompiluje whisper.cpp (silnik STT) do biblioteki statycznej.
#   bash tools/build_whisper.sh linux    → build/whisper/libwhisper.a  (testy tutaj)
#   bash tools/build_whisper.sh windows  → build/win/libwhisper.a      (do instalatora)
# Nie wymaga CMake: kompiluje bezpośrednio listę plików źródłowych ggml + src.
set -euo pipefail
cd "$(dirname "$0")/.."
TARGET="${1:-linux}"
SRC=third_party/whisper.cpp
[ -d "$SRC" ] || { echo "BŁĄD: brak $SRC" >&2; exit 1; }

EXCL="/(tests|ggml-cuda|ggml-metal|ggml-vulkan|ggml-sycl|ggml-opencl|ggml-blas|ggml-cann|ggml-amx|ggml-webgpu|ggml-zdnn|ggml-rpc|ggml-openvino|ggml-musa|ggml-hexagon|ggml-kompute|openvino|amx)/"
FILES=$(cd "$SRC" && find ggml/src src -name '*.c' -o -name '*.cpp' | grep -vE "$EXCL" | sort)

if [ "$TARGET" = windows ]; then
  PY="${PY:-python3}"
  # uwaga: to NIE jest wywołanie w podpowłoce — budujemy polecenie jako tekst
  CC="$PY -m ziglang cc -target x86_64-windows-gnu"
  CXX="$PY -m ziglang c++ -target x86_64-windows-gnu"
  OUT=build/win/whisper
  EXTRA="-D_WIN32_WINNT=0x0A00 -DGGML_MULTIPLATFORM -D_USE_MATH_DEFINES -DM_PI=3.14159265358979323846"
else
  CC="gcc"
  CXX="g++"
  OUT=build/whisper
  EXTRA="-D_GNU_SOURCE -mavx -mavx2 -mfma -mf16c -msse4.2"
fi
rm -rf "$OUT" && mkdir -p "$OUT"
n=0
while IFS= read -r f; do
  o="$OUT/$(echo "$f" | tr '/' '_').o"
  case "$f" in
    *.c)   $CC  -O3 -std=gnu11 $EXTRA -pthread -fPIC -I"$SRC" -I"$SRC/ggml/include" \
                 -I"$SRC/include" -I"$SRC/src" -I"$SRC/ggml/src" -I"$SRC/ggml/src/ggml-cpu" \
                 -c "$SRC/$f" -o "$o" ;;
    *.cpp) $CXX -O3 -std=c++17 $EXTRA -pthread -fPIC -I"$SRC" -I"$SRC/ggml/include" \
                 -I"$SRC/include" -I"$SRC/src" -I"$SRC/ggml/src" -I"$SRC/ggml/src/ggml-cpu" \
                 -c "$SRC/$f" -o "$o" ;;
  esac
  n=$((n+1))
done <<< "$FILES"
rm -f "$OUT/libwhisper.a"
ar rcs "$OUT/libwhisper.a" "$OUT"/*.o
echo "OK $OUT/libwhisper.a ($n plików, $(stat -c%s "$OUT/libwhisper.a") B)"
