#!/usr/bin/env bash
# test_engine.sh — testy silnika na JVM (ten sam kod, który trafia do APK).
# Podstawiane są wyłącznie klasy android.graphics (ścieżki/macierze/bitmapy) i org.json.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -z "${JAVA_HOME:-}" ] && [ -n "$(command -v python3)" ]; then
  JAVA_HOME="$(python3 -c 'import jdk4py;print(jdk4py.JAVA_HOME)' 2>/dev/null || true)"
fi
JAVA="${JAVA_HOME:?brak JAVA_HOME}/bin/java"
CACHE="${LC_CACHE:-$HOME/.cache/lc}"
OUT="build/jvm"
rm -rf "$OUT"
mkdir -p "$OUT"

"$JAVA" -jar "$CACHE/ecj.jar" -source 1.8 -target 1.8 -proc:none -nowarn -encoding UTF-8 \
  -d "$OUT" $(find tests/jvm/stubs -name '*.java') \
  android/app/src/pl/wojan/lasercraft/{Core,Qr,Gen,Draw,Pdf,Img,Store,PathProvider}.java \
  tests/jvm/src/*.java

"$JAVA" -Dfile.encoding=UTF-8 -cp "$OUT" test.Harness | tee build/jvm/wynik.txt
