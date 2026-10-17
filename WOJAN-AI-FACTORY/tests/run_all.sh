#!/usr/bin/env bash
# WOJAN AI FACTORY — wszystkie testy automatyczne
set -e
cd "$(dirname "$0")/.."
echo "== Silnik (node:test) =="
(cd engine && node --test "test/*.test.js")
echo "== Fabryka E2E =="
node tests/test_factory_e2e.js
echo "== Wszystkie testy automatyczne: PASS =="
