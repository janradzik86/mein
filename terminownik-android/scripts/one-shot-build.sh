#!/usr/bin/env bash
# ============================================================================
#  TERMINOWNIK – build podpisanej wersji release BEZ żadnych przygotowań
# ============================================================================
# Ten skrypt SAM instaluje wszystko, czego potrzeba do zbudowania APK:
#
#   1. JDK 21 (Temurin / Corretto)   – jeśli brak w systemie
#   2. Android SDK: cmdline-tools, platform-tools, platform 36, build-tools
#   3. trwały klucz podpisywania     – jeśli jeszcze nie istnieje (nigdy nie nadpisuje)
#   4. kompiluje, podpisuje i weryfikuje APK
#
# Nie wymaga Android Studio, nie wymaga roota, nie wymaga konta Google,
# nie wymaga GitHuba ani CI. Wszystko ląduje w katalogach użytkownika.
#
# UŻYCIE (Linux / macOS / WSL / Git Bash):
#
#   cd mein/terminownik-android
#   bash scripts/one-shot-build.sh
#
# Flagi:
#   --skip-tests   pomiń testy jednostkowe i lint (najszybciej do pliku APK)
#   --clean        wyczyść build przed kompilacją
#   --tools-dir X  inny katalog na narzędzia (domyślnie ~/.terminownik-tools)
#
# WYNIK:
#   dist/terminownik-release.apk    ← plik instalacyjny
#   dist/SHA256SUMS.txt             ← suma kontrolna
#   dist/VERIFY_REPORT.md           ← podpis, rozmiar, wersja, uprawnienia, testy
#   dist/BUILD_FAILURE_LOG.txt      ← tylko gdy coś się nie skompiluje (do wklejenia w zgłoszeniu)
#
# Pobrane narzędzia i zależności Gradle (~5–8 GB) są buforowane, więc kolejne
# uruchomienia trwają minuty, nie dziesiątki minut.
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TOOLS_DIR="${TERMINOWNIK_TOOLS_DIR:-$HOME/.terminownik-tools}"
SDK_DIR=""
JDK_DIR=""
SKIP_TESTS=0
CLEAN=0
PASSTHROUGH=("--assemble-first")

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-tests) SKIP_TESTS=1; PASSTHROUGH+=("--skip-tests") ;;
    --clean) CLEAN=1 ;;
    --tools-dir) TOOLS_DIR="$2"; shift ;;
    *) echo "Nieznana flaga: $1" >&2; exit 2 ;;
  esac
  shift
done

step() { printf '\n\033[1m[%s]\033[0m %s\n' "$1" "$2"; }
info() { printf '    %s\n' "$*"; }
die()  { printf '\n\033[31mBŁĄD:\033[0m %s\n' "$*" >&2; exit 1; }

START_TS=$(date +%s)

# ---------------------------------------------------------------------------
step "0/6" "Rozpoznaję system"
# ---------------------------------------------------------------------------
OS="$(uname -s)"
ARCH="$(uname -m)"
case "$OS" in
  Linux*)  SYS=linux ;;
  Darwin*) SYS=mac ;;
  MINGW*|MSYS*|CYGWIN*) SYS=linux; info "Wykryto Git Bash – używam pakietów linuksowych (WSL/Git Bash)." ;;
  *) die "nieobsługiwany system: $OS (na Windows użyj scripts/one-shot-build.ps1)" ;;
esac
case "$ARCH" in
  x86_64|amd64) JARCH=x64; CORRETTO_ARCH=x64 ;;
  arm64|aarch64) JARCH=aarch64; CORRETTO_ARCH=aarch64 ;;
  *) die "nieobsługiwana architektura: $ARCH" ;;
esac
info "system: $OS ($ARCH) → pakiety: $SYS/$JARCH"
[[ "$SYS" == mac ]] && ADOPT_OS=mac || ADOPT_OS=linux

command -v curl >/dev/null 2>&1 || command -v wget >/dev/null 2>&1 \
  || die "brak curl i wget – zainstaluj curl (na Debianie: sudo apt install curl)."

download() {  # download URL PLIK_WYNIKOWY
  local url="$1" out="$2" attempt
  for attempt in 1 2 3; do
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL --retry 2 --connect-timeout 30 -o "$out" "$url" && return 0
    else
      wget -q --tries=2 --timeout=60 -O "$out" "$url" && return 0
    fi
    info "próba $attempt/3 nieudana: $url"
    sleep 3
  done
  return 1
}

extract_zip() {  # extract_zip ARCHIWUM KATALOG
  local zip="$1" dest="$2"
  mkdir -p "$dest"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$zip" -d "$dest" && return 0
  fi
  if command -v bsdtar >/dev/null 2>&1; then
    bsdtar -xf "$zip" -C "$dest" && return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 -c "import sys,zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$zip" "$dest" && return 0
  fi
  die "brak narzędzia do rozpakowania ZIP (unzip / bsdtar / python3). Zainstaluj unzip."
}

mkdir -p "$TOOLS_DIR"

# ---------------------------------------------------------------------------
step "1/6" "JDK 17–21"
# ---------------------------------------------------------------------------
jdk_major() { "$1" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/'; }

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then
  MAJOR="$(jdk_major "$JAVA_HOME/bin/javac")"
  if (( MAJOR >= 17 && MAJOR <= 25 )); then JDK_DIR="$JAVA_HOME"; info "używam JAVA_HOME: $JDK_DIR (JDK $MAJOR)"; fi
fi
if [[ -z "$JDK_DIR" ]] && command -v javac >/dev/null 2>&1; then
  MAJOR="$(jdk_major javac)"
  if (( MAJOR >= 17 && MAJOR <= 25 )); then
    JDK_DIR="$(dirname "$(dirname "$(command -v javac)")")"
    info "używam JDK z PATH: $JDK_DIR (JDK $MAJOR)"
  fi
fi
if [[ -z "$JDK_DIR" ]]; then
  for existing in "$TOOLS_DIR"/jdk*/ "$TOOLS_DIR"/*jdk*/; do
    if [[ -x "${existing}bin/javac" ]]; then
      JDK_DIR="${existing%/}"; break
    fi
    FOUND_JAVAC="$(find "$existing" -maxdepth 3 -name javac -type f 2>/dev/null | head -1 || true)"
    if [[ -n "$FOUND_JAVAC" ]]; then
      JDK_DIR="$(dirname "$(dirname "$FOUND_JAVAC")")"; break
    fi
  done
fi

if [[ -z "$JDK_DIR" ]]; then
  info "brak JDK – pobieram (ok. 190 MB, jednorazowo)"
  JDK_TMP="$TOOLS_DIR/jdk21-download"
  rm -rf "$JDK_TMP"; mkdir -p "$JDK_TMP"
  JDK_URLS=(
    "https://api.adoptium.net/v3/binary/latest/21/ga/${ADOPT_OS}/${JARCH}/jdk/hotspot/normal/eclipse"
    "https://corretto.aws/downloads/latest/amazon-corretto-21-${CORRETTO_ARCH}-${SYS}-jdk.tar.gz"
    "https://corretto.aws/downloads/latest/amazon-corretto-17-${CORRETTO_ARCH}-${SYS}-jdk.tar.gz"
  )
  OK=0
  for url in "${JDK_URLS[@]}"; do
    info "próbuję: $url"
    if download "$url" "$JDK_TMP/jdk.archive"; then OK=1; break; fi
  done
  (( OK == 1 )) || die "nie udało się pobrać JDK. Pobierz ręcznie Temurin 21 (https://adoptium.net/) i uruchom skrypt z JAVA_HOME=/ścieżka/do/jdk."
  if file "$JDK_TMP/jdk.archive" 2>/dev/null | grep -qi zip || [[ "$url" == *.zip ]]; then
    extract_zip "$JDK_TMP/jdk.archive" "$JDK_TMP/x"
  else
    mkdir -p "$JDK_TMP/x"; tar -xzf "$JDK_TMP/jdk.archive" -C "$JDK_TMP/x"
  fi
  JAVAC_PATH="$(find "$JDK_TMP/x" -name javac -type f | head -1)"
  [[ -n "$JAVAC_PATH" ]] || die "pobrane archiwum JDK nie zawiera javac."
  JDK_DIR="$(dirname "$(dirname "$JAVAC_PATH")")"
  mv "$JDK_DIR" "$TOOLS_DIR/jdk21"
  JDK_DIR="$TOOLS_DIR/jdk21"
  rm -rf "$JDK_TMP"
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
info "JAVA_HOME=$JAVA_HOME"
info "$(java -version 2>&1 | head -1)"

# ---------------------------------------------------------------------------
step "2/6" "Android SDK"
# ---------------------------------------------------------------------------
for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk" \
                 "/usr/lib/android-sdk" "/opt/android-sdk" "$TOOLS_DIR/android-sdk"; do
  [[ -n "$candidate" && -d "$candidate" ]] && SDK_DIR="$candidate" && break
done
if [[ -n "$SDK_DIR" ]]; then
  info "znaleziono istniejący SDK: $SDK_DIR"
else
  SDK_DIR="$TOOLS_DIR/android-sdk"
  mkdir -p "$SDK_DIR"
fi

if [[ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
  info "instaluję cmdline-tools do $SDK_DIR"
  CT_TMP="$TOOLS_DIR/cmdline-tools-download"; rm -rf "$CT_TMP"; mkdir -p "$CT_TMP"
  CT_BUILDS=(13114758 12700392 11076708 10406996 9477386)
  OK=0
  for build in "${CT_BUILDS[@]}"; do
    url="https://dl.google.com/android/repository/commandlinetools-${SYS}-${build}_latest.zip"
    info "próbuję: $url"
    if download "$url" "$CT_TMP/cmdline-tools.zip"; then OK=1; break; fi
  done
  (( OK == 1 )) || die "nie udało się pobrać cmdline-tools. Sprawdź sieć albo pobierz ręcznie: https://developer.android.com/studio#command-line-tools-only i rozpakuj do $SDK_DIR/cmdline-tools/latest."
  extract_zip "$CT_TMP/cmdline-tools.zip" "$CT_TMP/x"
  SRC="$CT_TMP/x/cmdline-tools"; [[ -d "$SRC" ]] || SRC="$(find "$CT_TMP/x" -maxdepth 2 -name bin -type d | head -1 | xargs dirname)"
  mkdir -p "$SDK_DIR/cmdline-tools"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$SRC" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$CT_TMP"
fi

SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
[[ -x "$SDKMANAGER" ]] || die "sdkmanager nie jest wykonywalny: $SDKMANAGER"
export ANDROID_HOME="$SDK_DIR"
export ANDROID_SDK_ROOT="$SDK_DIR"
info "SDK: $SDK_DIR"

info "akceptuję licencje SDK"
yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null 2>&1 || true

PLATFORM="platforms;android-36"
BUILD_TOOLS_PKG="build-tools;36.0.0"
if ! "$SDKMANAGER" --sdk_root="$SDK_DIR" "$PLATFORM" >/dev/null 2>&1; then
  info "platform 36 niedostępna – szukam najnowszej zainstalowanej/dostępnej"
  PLATFORM="$("$SDKMANAGER" --sdk_root="$SDK_DIR" --list 2>/dev/null | sed -n 's/^[[:space:]]*\(platforms;android-3[0-9]\)[[:space:]].*/\1/p' | sort -V | tail -1 || true)"
  [[ -n "$PLATFORM" ]] || PLATFORM="platforms;android-36"
fi
if ! "$SDKMANAGER" --sdk_root="$SDK_DIR" "$BUILD_TOOLS_PKG" >/dev/null 2>&1; then
  BUILD_TOOLS_PKG="$("$SDKMANAGER" --sdk_root="$SDK_DIR" --list 2>/dev/null | sed -n 's/^[[:space:]]*\(build-tools;3[0-9]\.[0-9.]*\)[[:space:]].*/\1/p' | sort -V | tail -1 || true)"
  [[ -n "$BUILD_TOOLS_PKG" ]] || BUILD_TOOLS_PKG="build-tools;36.0.0"
fi
info "instaluję: platform-tools, $PLATFORM, $BUILD_TOOLS_PKG (ok. 300 MB, jednorazowo)"
"$SDKMANAGER" --sdk_root="$SDK_DIR" "platform-tools" "$PLATFORM" "$BUILD_TOOLS_PKG" >/dev/null

# local.properties dla Gradle
echo "sdk.dir=$SDK_DIR" > local.properties
info "zapisano local.properties → sdk.dir=$SDK_DIR"

# ---------------------------------------------------------------------------
step "3/6" "Klucz podpisywania"
# ---------------------------------------------------------------------------
KEY_DIR="${TERMINOWNIK_KEY_DIR:-$HOME/keys/terminownik}"
if [[ -n "${TERMINOWNIK_STORE_FILE:-}" && -n "${TERMINOWNIK_STORE_PASSWORD:-}" &&
      -n "${TERMINOWNIK_KEY_ALIAS:-}" && -n "${TERMINOWNIK_KEY_PASSWORD:-}" ]]; then
  info "źródło klucza: zmienne środowiskowe właściciela"
elif [[ -f keystore.properties ]]; then
  info "źródło klucza: istniejący keystore.properties (nie nadpisuję)"
elif [[ -f "$KEY_DIR/keystore.properties" ]]; then
  info "źródło klucza: $KEY_DIR"
else
  info "tworzę trwały klucz w $KEY_DIR (hasła losowe, nie są drukowane)"
  bash scripts/make-keystore.sh "$KEY_DIR"
fi
info "WAŻNE: katalog $KEY_DIR trzeba zbackupować OFFLINE – bez niego nie zaktualizujesz aplikacji."

# ---------------------------------------------------------------------------
step "4/6" "Kompilacja, podpisanie i weryfikacja"
# ---------------------------------------------------------------------------
if (( CLEAN == 1 )); then
  info "czyszczę poprzedni build"
  ./gradlew --no-daemon clean >/dev/null 2>&1 || true
fi

set +e
bash scripts/build-release.sh "${PASSTHROUGH[@]}"
BUILD_EXIT=$?
set -e
info "kod wyjściowy buildu: $BUILD_EXIT"

# ---------------------------------------------------------------------------
step "5/6" "Podsumowanie"
# ---------------------------------------------------------------------------
DURATION=$(( $(date +%s) - START_TS ))
info "czas: $((DURATION / 60)) min $((DURATION % 60)) s"
if [[ -f dist/terminownik-release.apk ]]; then
  SIZE=$(stat -c %s dist/terminownik-release.apk 2>/dev/null || stat -f %z dist/terminownik-release.apk)
  info "APK:      $ROOT/dist/terminownik-release.apk"
  info "rozmiar:  $SIZE bajtów ($((SIZE / 1048576)) MB)"
  info "SHA-256:  $(cut -d' ' -f1 dist/SHA256SUMS.txt)"
  info "raport:   $ROOT/dist/VERIFY_REPORT.md"
  info
  info "Instalacja na telefonie:"
  info "  $SDK_DIR/platform-tools/adb install -r dist/terminownik-release.apk"
  info "  (albo skopiuj plik na telefon i otwórz go – zezwól na instalację z nieznanego źródła)"
else
  die "APK nie powstało. Treść dist/BUILD_FAILURE_LOG.txt wklej w odpowiedzi – poprawię kod."
fi

if (( BUILD_EXIT != 0 )); then
  info
  info "UWAGA: APK istnieje, ale lint/testy zgłosiły problemy (kod $BUILD_EXIT)."
  info "Szczegóły: dist/VERIFY_REPORT.md → sekcja „Jakość kodu”."
fi

step "6/6" "Gotowe"
