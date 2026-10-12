#!/usr/bin/env bash
# Lokalna weryfikacja logiki biznesowej BEZ Gradle, Android SDK i bez sieci.
#
# Używa kompilatora Kotlin (npm: kotlin-compiler) i dowolnego JDK.
# Kompiluje wyłącznie kod domenowy (bez zależności od android.*) oraz wspólne scenariusze
# testowe, które w CI są uruchamiane przez JUnit w `./gradlew test`.
#
# Zmienne (opcjonalne):
#   KOTLINC   - ścieżka do kotlinc           (domyślnie: node_modules/kotlin-compiler/bin/kotlinc)
#   JAVA_HOME - katalog JDK                   (wymagany)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLINC="${KOTLINC:-$ROOT/../.kotlinc/node_modules/kotlin-compiler/bin/kotlinc}"
OUT="${OUT:-/tmp/terminownik-domain-verify}"

if [[ ! -x "$KOTLINC" && ! -f "$KOTLINC" ]]; then
  echo "Nie znaleziono kotlinc: $KOTLINC" >&2
  echo "Zainstaluj: npm install --no-save kotlin-compiler@2.4.21" >&2
  exit 2
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "Ustaw JAVA_HOME (np. JDK z pip: jdk4py)" >&2
  exit 2
fi

rm -rf "$OUT"
mkdir -p "$OUT"
echo "Kompilacja logiki domenowej i scenariuszy testowych..."
bash "$KOTLINC" \
  "$ROOT/app/src/main/kotlin/pl/terminownik/app/domain" \
  "$ROOT/app/src/main/kotlin/pl/terminownik/app/data/backup/BackupContract.kt" \
  "$ROOT/app/src/main/kotlin/pl/terminownik/app/data/backup/BackupValidator.kt" \
  "$ROOT/app/src/test/kotlin/pl/terminownik/app/domain/DomainScenarios.kt" \
  "$ROOT/app/src/test/kotlin/pl/terminownik/app/domain/LocalScenarioRunner.kt" \
  -d "$OUT" 2>&1 | grep -v '^warning: ' || true

echo "Uruchamianie scenariuszy..."
"$JAVA_HOME/bin/java" -cp "$OUT:$(dirname "$KOTLINC")/../lib/kotlin-stdlib.jar" \
  pl.terminownik.app.domain.LocalScenarioRunner
