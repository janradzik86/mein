#!/usr/bin/env bash
# Buduje, podpisuje i weryfikuje wydanie Terminownika na Twojej maszynie.
#
# Wymagania:
#   * JDK 17–21 (np. Temurin/Zulu) – `java -version`
#   * Android SDK: platform 36 + build-tools (Android Studio instaluje je domyślnie)
#   * zmienne środowiskowe ANDROID_HOME lub ANDROID_SDK_ROOT (albo plik local.properties)
#   * ~10 GB miejsca i sieć do pierwszego pobrania zależności Gradle
#
# Użycie:
#   scripts/build-release.sh            # pełny build: lint + testy + podpisane APK
#   scripts/build-release.sh --skip-tests
#
# Wynik:
#   dist/terminownik-release.apk
#   dist/SHA256SUMS.txt
#   dist/VERIFY_REPORT.md   (apksigner, aapt2, kontrola manifestu, wyniki testów)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SKIP_TESTS=0
[[ "${1:-}" == "--skip-tests" ]] && SKIP_TESTS=1

log() { printf '\n=== %s\n' "$*"; }
die() { echo "BŁĄD: $*" >&2; exit 1; }

# --- 1. Java ----------------------------------------------------------------------------------
log "Sprawdzam JDK"
command -v java >/dev/null 2>&1 || die "brak java w PATH. Zainstaluj JDK 17–21."
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
echo "java: $(java -version 2>&1 | head -1) (major=$JAVA_MAJOR)"
if (( JAVA_MAJOR < 17 || JAVA_MAJOR > 25 )); then
  echo "OSTRZEŻENIE: oczekiwano JDK 17–21; build może się nie udać z JDK $JAVA_MAJOR."
fi

# --- 2. Android SDK ---------------------------------------------------------------------------
log "Lokalizuję Android SDK"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" && -f local.properties ]]; then
  SDK="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
fi
if [[ -z "$SDK" ]]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" "/usr/lib/android-sdk" "/opt/android-sdk"; do
    [[ -d "$candidate" ]] && SDK="$candidate" && break
  done
fi
[[ -n "$SDK" && -d "$SDK" ]] || die "nie znaleziono Android SDK. Ustaw ANDROID_HOME albo wpisz sdk.dir=... w local.properties."
echo "SDK: $SDK"
[[ -d "$SDK/platforms/android-36" ]] || echo "OSTRZEŻENIE: brak platforms/android-36 – Gradle spróbuje go pobrać (wymaga licencji)."
grep -q "sdk.dir" local.properties 2>/dev/null || echo "sdk.dir=$SDK" > local.properties

# --- 3. Klucz podpisywania ---------------------------------------------------------------------
log "Przygotowuję podpisywanie"
if [[ -n "${TERMINOWNIK_STORE_FILE:-}" && -n "${TERMINOWNIK_STORE_PASSWORD:-}" &&
      -n "${TERMINOWNIK_KEY_ALIAS:-}" && -n "${TERMINOWNIK_KEY_PASSWORD:-}" ]]; then
  echo "źródło: zmienne środowiskowe właściciela"
elif [[ -f keystore.properties ]]; then
  echo "źródło: istniejący keystore.properties (nie nadpisuję)"
else
  KEY_DIR="${TERMINOWNIK_KEY_DIR:-$HOME/keys/terminownik}"
  if [[ -f "$KEY_DIR/keystore.properties" ]]; then
    cp "$KEY_DIR/keystore.properties" keystore.properties
    chmod 600 keystore.properties
    echo "źródło: $KEY_DIR/keystore.properties"
  else
    echo "Brak klucza – tworzę nowy, trwały klucz w $KEY_DIR"
    bash scripts/make-keystore.sh "$KEY_DIR"
    cp "$KEY_DIR/keystore.properties" keystore.properties
    chmod 600 keystore.properties
    echo "WAŻNE: zrób kopię zapasową OFFLINE katalogu $KEY_DIR (bez niego nie zaktualizujesz aplikacji)."
  fi
fi

# --- 4. Build -----------------------------------------------------------------------------------
log "Buduję (lint + testy + release APK)"
GRADLE_TASKS=(":app:lintRelease" ":app:assembleRelease")
if (( SKIP_TESTS == 0 )); then
  GRADLE_TASKS=(":app:lintRelease" ":app:testDebugUnitTest" ":app:assembleRelease")
fi
./gradlew --no-daemon --stacktrace "${GRADLE_TASKS[@]}"

APK="app/build/outputs/apk/release/app-release.apk"
[[ -f "$APK" ]] || die "nie znaleziono APK: $APK"

# --- 5. Weryfikacja ------------------------------------------------------------------------------
log "Weryfikuję artefakt"
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
APKSIGNER="$BUILD_TOOLS/apksigner"
AAPT2="$BUILD_TOOLS/aapt2"
echo "build-tools: $BUILD_TOOLS"

mkdir -p dist
cp "$APK" dist/terminownik-release.apk

SHA256="$(sha256sum dist/terminownik-release.apk | cut -d' ' -f1)"
SIZE_BYTES="$(stat -c %s dist/terminownik-release.apk 2>/dev/null || stat -f %z dist/terminownik-release.apk)"
VERSION_NAME="$("$AAPT2" dump badging "$APK" 2>/dev/null | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"
VERSION_CODE="$("$AAPT2" dump badging "$APK" 2>/dev/null | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
echo "$SHA256  terminownik-release.apk" > dist/SHA256SUMS.txt

{
  echo "# Raport weryfikacji wydania"
  echo
  echo "* data: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
  echo "* commit: $(git rev-parse HEAD 2>/dev/null || echo 'nieznany')"
  echo "* wersja: ${VERSION_NAME:-?} (kod ${VERSION_CODE:-?})"
  echo "* rozmiar: $SIZE_BYTES bajtów"
  echo "* SHA-256: \`$SHA256\`"
  echo
  echo "## apksigner verify --verbose --print-certs"
  echo '```'
  "$APKSIGNER" verify --verbose --print-certs "$APK" 2>&1
  echo '```'
  echo
  echo "## aapt2 dump badging (wybrane)"
  echo '```'
  "$AAPT2" dump badging "$APK" 2>/dev/null | grep -E "package:|sdkVersion|targetSdkVersion|application-label|native-code" || true
  echo '```'
  echo
  echo "## Uprawnienia"
  echo '```'
  "$AAPT2" dump permissions "$APK" 2>/dev/null || true
  echo '```'
} > dist/VERIFY_REPORT.md

# Kontrola: brak INTERNET i brak debuggable w wydaniu.
PERMS="$("$AAPT2" dump permissions "$APK" 2>/dev/null || true)"
if echo "$PERMS" | grep -q "android.permission.INTERNET"; then
  die "w uprawnieniach APK jest INTERNET – aplikacja ma działać w pełni offline."
fi
if [[ -x "$SDK/cmdline-tools/latest/bin/apkanalyzer" ]]; then
  "$SDK/cmdline-tools/latest/bin/apkanalyzer" manifest print "$APK" > /tmp/manifest.txt 2>/dev/null || true
  if grep -q 'android:debuggable="true"' /tmp/manifest.txt 2>/dev/null; then
    die "APK release jest debuggowalny."
  fi
  {
    echo
    echo "## Manifest (apkanalyzer) – kontrola debuggable"
    echo '```'
    grep -E 'android:debuggable|uses-permission' /tmp/manifest.txt || echo 'brak debuggable=true (oczekiwane)'
    echo '```'
  } >> dist/VERIFY_REPORT.md
fi

# --- 6. Podsumowanie testów ------------------------------------------------------------------------
if (( SKIP_TESTS == 0 )); then
  {
    echo
    echo "## Testy jednostkowe"
    echo '```'
    python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
total = failures = errors = skipped = 0
for path in sorted(glob.glob('app/build/test-results/*/TEST-*.xml')):
    root = ET.parse(path).getroot()
    total += int(root.get('tests', 0))
    failures += int(root.get('failures', 0))
    errors += int(root.get('errors', 0))
    skipped += int(root.get('skipped', 0))
print(f"testy: {total}, nieudane: {failures}, bledy: {errors}, pominięte: {skipped}")
print("WYNIK:", "PASS" if total > 0 and failures == 0 and errors == 0 else "FAIL")
PY
    echo '```'
  } >> dist/VERIFY_REPORT.md
fi

log "Gotowe"
cat dist/VERIFY_REPORT.md
echo
echo "Plik instalacyjny: $ROOT/dist/terminownik-release.apk"
echo "Sumy kontrolne:    $ROOT/dist/SHA256SUMS.txt"
echo "Raport:            $ROOT/dist/VERIFY_REPORT.md"
echo
echo "Instalacja na telefonie:"
echo "  adb install -r dist/terminownik-release.apk"
echo "  (albo skopiuj plik na telefon i otwórz go – zezwól na instalację z tego źródła)"
