#!/usr/bin/env bash
# Buduje bibliotekę core (C++17) do build/libwojan.a
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p build
CXX="${CXX:-g++}"
FLAGS="-std=c++17 -O2 -Wall -Wextra -Wno-unused-parameter -Icore"
OBJS=()
for f in core/*.cpp; do
  o="build/$(basename "${f%.cpp}").o"
  $CXX $FLAGS -c "$f" -o "$o"
  OBJS+=("$o")
done
rm -f build/libwojan.a
ar rcs build/libwojan.a "${OBJS[@]}"
echo "OK build/libwojan.a $(stat -c%s build/libwojan.a) B"
