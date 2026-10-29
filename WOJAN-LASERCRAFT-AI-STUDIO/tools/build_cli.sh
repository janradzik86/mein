#!/usr/bin/env bash
# Buduje wojan-cli (silnik + testy + serwer API)
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
./tools/build_core.sh
CXX="${CXX:-g++}"
mkdir -p build
$CXX -std=c++17 -O2 -pthread -Icore -o build/wojan-cli \
  cli/main.cpp cli/selftest.cpp cli/server.cpp \
  -Lbuild -lwojan -static-libstdc++ -static-libgcc
echo "OK build/wojan-cli ($(stat -c%s build/wojan-cli) B)"
