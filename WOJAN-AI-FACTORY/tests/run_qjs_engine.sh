#!/usr/bin/env bash
# WOJAN AI FACTORY — test silnika w QuickJS-NG (build/qjs), nie w Node.
set -e
cd "$(dirname "$0")/.."
QJS_SRC="${WOJAN_TOOLCACHE:-$HOME/.cache/wojan-tools}/src/quickjs-master"
QJS="$QJS_SRC/build/qjs"
if [ ! -x "$QJS" ]; then
  echo "== buduję qjs (QuickJS-NG) =="
  (cd "$QJS_SRC" && ${WOJAN_VENV:-$HOME/.cache/wojan-venv}/bin/pip install -q cmake 2>/dev/null || true)
  (cd "$QJS_SRC" && PATH="${WOJAN_VENV:-$HOME/.cache/wojan-venv}/bin:$PATH" make -j4 >/dev/null 2>&1)
fi
echo "== QuickJS engine smoke =="
"$QJS" --std --module engine/test/qjs_smoke.js
