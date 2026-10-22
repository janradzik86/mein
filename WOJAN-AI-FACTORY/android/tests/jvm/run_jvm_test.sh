#!/bin/bash
# Testy uruchomieniowe logiki aplikacji na JVM (atrapy Androida).
# Wymaga: ~/.cache/wojan-venv (jdk4py) + ~/.cache/wojan-tools/ecj.jar (fetch-toolchain.sh).
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
APP="$DIR/../../app/src"
VENV="${WOJAN_VENV:-$HOME/.cache/wojan-venv}"
JAVA="$VENV/lib/python3.11/site-packages/jdk4py/java-runtime/bin/java"
ECJ="$HOME/.cache/wojan-tools/ecj.jar"
OUT="$DIR/out"

if [ ! -x "$JAVA" ]; then echo "BRAK JDK (jdk4py) — uruchom scripts/fetch-toolchain.sh"; exit 2; fi
if [ ! -f "$ECJ" ]; then echo "BRAK ecj.jar — uruchom scripts/fetch-toolchain.sh"; exit 2; fi

rm -rf "$OUT"; mkdir -p "$OUT"
echo "== Kompilacja (ECJ): atrapy + kod aplikacji + TestMain =="
"$JAVA" -jar "$ECJ" -source 1.8 -target 1.8 -nowarn -d "$OUT" \
    $(find "$DIR/stubs" -name '*.java') \
    $(find "$APP" -name '*.java') \
    "$DIR/TestMain.java"
echo "== Uruchomienie testów =="
"$JAVA" -cp "$OUT" TestMain
