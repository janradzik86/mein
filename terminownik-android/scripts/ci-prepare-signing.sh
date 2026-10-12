#!/usr/bin/env bash
# Przygotowanie podpisywania wydania w CI.
#
# Trzy ścieżki, w kolejności priorytetu:
#   1. SEKRETY WŁAŚCICIELA (TERMINOWNIK_KEYSTORE_BASE64 + hasła + alias) – zalecane produkcyjnie.
#   2. ISTNIEJĄCY klucz przechowany jako prywatny asset Release (tag: signing-key-storage).
#   3. PIERWSZE URUCHOMIENIE: skrypt tworzy trwały klucz i zapisuje go w prywatnym Release,
#      żeby kolejne buildy podpisywały tym samym kluczem (inaczej aktualizacja APK jest niemożliwa).
#
# Zasady bezpieczeństwa:
#   * hasła nigdy nie trafiają do logów ani do plików w repozytorium (tylko $GITHUB_ENV / pliki poza VCS),
#   * istniejący klucz nigdy nie jest nadpisywany – gdy Release istnieje, a pobranie się nie uda,
#     skrypt kończy się błędem zamiast tworzyć nowy klucz,
#   * repozytorium jest prywatne, więc asset z kluczem widzą wyłącznie współpracownicy.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${WORK_DIR:-$ROOT/.signing}"
KEY_TAG="${KEY_TAG:-signing-key-storage}"
KEY_ASSET="terminownik-signing-key.zip"
REPO="${GITHUB_REPOSITORY:-}"

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
chmod 700 "$WORK_DIR"

log() { echo "[signing] $*"; }

fail() {
  echo "::error::[signing] $*" >&2
  exit 1
}

export_env() {
  local store_file="$1" props="$2" source_label="$3"
  [[ -f "$store_file" ]] || fail "brak pliku keystore: $store_file"
  [[ -f "$props" ]] || fail "brak pliku keystore.properties: $props"

  # shellcheck disable=SC1090
  local store_password key_alias key_password
  store_password="$(grep '^storePassword=' "$props" | cut -d= -f2-)"
  key_alias="$(grep '^keyAlias=' "$props" | cut -d= -f2-)"
  key_password="$(grep '^keyPassword=' "$props" | cut -d= -f2-)"
  [[ -n "$store_password" && -n "$key_alias" && -n "$key_password" ]] || fail "niekompletny keystore.properties"

  # Plik properties dla Gradle (katalog jest w .gitignore).
  cp "$props" "$ROOT/keystore.properties"
  chmod 600 "$ROOT/keystore.properties"

  if [[ -n "${GITHUB_ENV:-}" ]]; then
    {
      echo "TERMINOWNIK_STORE_FILE=$store_file"
      echo "TERMINOWNIK_STORE_PASSWORD=$store_password"
      echo "TERMINOWNIK_KEY_ALIAS=$key_alias"
      echo "TERMINOWNIK_KEY_PASSWORD=$key_password"
      echo "TERMINOWNIK_SIGNING_SOURCE=$source_label"
    } >> "$GITHUB_ENV"
  fi
  log "źródło klucza: $source_label"
  log "alias: $key_alias"
  log "SHA-256 keystore: $(sha256sum "$store_file" | cut -d' ' -f1)"
}

# --- 1. sekrety właściciela -------------------------------------------------------------
if [[ -n "${TERMINOWNIK_KEYSTORE_BASE64:-}" && -n "${TERMINOWNIK_STORE_PASSWORD:-}" &&
      -n "${TERMINOWNIK_KEY_PASSWORD:-}" && -n "${TERMINOWNIK_KEY_ALIAS:-}" ]]; then
  log "używam sekretów właściciela"
  echo "$TERMINOWNIK_KEYSTORE_BASE64" | base64 -d > "$WORK_DIR/terminownik-release.jks"
  cat > "$WORK_DIR/keystore.properties" <<EOF
storeFile=$WORK_DIR/terminownik-release.jks
storePassword=$TERMINOWNIK_STORE_PASSWORD
keyAlias=$TERMINOWNIK_KEY_ALIAS
keyPassword=$TERMINOWNIK_KEY_PASSWORD
EOF
  chmod 600 "$WORK_DIR/keystore.properties"
  export_env "$WORK_DIR/terminownik-release.jks" "$WORK_DIR/keystore.properties" "owner-secrets"
  exit 0
fi

command -v gh >/dev/null 2>&1 || fail "brak gh CLI (potrzebny do ścieżki 2/3)"
[[ -n "$REPO" ]] || fail "brak GITHUB_REPOSITORY"
[[ -n "${GH_TOKEN:-}${GITHUB_TOKEN:-}" ]] || fail "brak tokenu GitHub dla gh"

# --- 2. istniejący klucz w prywatnym Release ---------------------------------------------
if gh release view "$KEY_TAG" --repo "$REPO" >/dev/null 2>&1; then
  log "znaleziono Release '$KEY_TAG' – pobieram istniejący klucz"
  gh release download "$KEY_TAG" --repo "$REPO" --pattern "$KEY_ASSET" --dir "$WORK_DIR" \
    || fail "Release '$KEY_TAG' istnieje, ale nie udało się pobrać $KEY_ASSET. NIE tworzę nowego klucza, bo to złamałoby aktualizacje aplikacji."
  (cd "$WORK_DIR" && unzip -o -q "$KEY_ASSET")
  [[ -f "$WORK_DIR/terminownik-release.jks" ]] || fail "w archiwum nie ma terminownik-release.jks"
  [[ -f "$WORK_DIR/keystore.properties" ]] || fail "w archiwum nie ma keystore.properties"
  # Weryfikacja integralności pobranego klucza.
  if [[ -f "$WORK_DIR/SHA256SUMS.txt" ]]; then
    (cd "$WORK_DIR" && sha256sum -c SHA256SUMS.txt) || fail "suma kontrolna pobranego klucza się nie zgadza"
  fi
  export_env "$WORK_DIR/terminownik-release.jks" "$WORK_DIR/keystore.properties" "ci-release-reused"
  exit 0
fi

# --- 3. pierwsze uruchomienie: tworzymy trwały klucz i zapisujemy go prywatnie ------------
log "brak sekretów i brak Release '$KEY_TAG' – tworzę nowy trwały klucz"
KEYTOOL="${KEYTOOL:-}" bash "$ROOT/scripts/make-keystore.sh" "$WORK_DIR/keys" >/dev/null

cat > "$WORK_DIR/keys/README_ODZYSKIWANIE_PL.md" <<'EOF'
# Klucz podpisywania wydań Terminownika

Ten plik i `terminownik-release.jks` tworzą komplet potrzebny do podpisywania wydań.

## Co tu jest
* `terminownik-release.jks` – keystore z kluczem RSA 4096 (ważność 30 lat),
* `keystore.properties` – hasła i alias (plik prywatny),
* `SHA256SUMS.txt` – sumy kontrolne do weryfikacji integralności.

## Jak użyć w CI (ścieżka zalecana – sekrety właściciela)
1. Pobierz to archiwum: `gh release download signing-key-storage --pattern terminownik-signing-key.zip`
2. Rozpakuj i ustaw sekrety repozytorium:
   * `TERMINOWNIK_KEYSTORE_BASE64` = `base64 -w0 terminownik-release.jks`
   * `TERMINOWNIK_STORE_PASSWORD`, `TERMINOWNIK_KEY_ALIAS`, `TERMINOWNIK_KEY_PASSWORD` (z `keystore.properties`)
3. Kolejne buildy użyją sekretów i przestaną korzystać z tego archiwum.

## Bezpieczeństwo
* Klucza NIE WOLNO nadpisać ani stracić: bez niego nie zaktualizujesz wydanej aplikacji.
* Zrób kopię OFFLINE (menedżer haseł / szyfrowany nośnik / sejf firmowy).
* Nie publikuj tego archiwum ani haseł. Repozytorium musi pozostać prywatne.
EOF

(cd "$WORK_DIR/keys" && sha256sum terminownik-release.jks keystore.properties > SHA256SUMS.txt)
(cd "$WORK_DIR/keys" && zip -q -r "../$KEY_ASSET" terminownik-release.jks keystore.properties SHA256SUMS.txt README_ODZYSKIWANIE_PL.md)

gh release create "$KEY_TAG" "$WORK_DIR/$KEY_ASSET" \
  --repo "$REPO" \
  --title "Przechowalnia klucza podpisywania (prywatne)" \
  --notes "Trwały klucz podpisywania wydań Terminownika, utworzony automatycznie przez CI podczas pierwszego buildu.

**Ten Release jest prywatny** – widzą go tylko osoby z dostępem do repozytorium.

## Zawartość
* \`terminownik-signing-key.zip\`: keystore, \`keystore.properties\` (hasła), \`SHA256SUMS.txt\`, instrukcja odzyskiwania.

## Co zrobić (jednorazowo)
1. Pobierz archiwum i przenieś klucz do własnego sejfu (menedżer haseł, nośnik offline).
2. Ustaw sekrety repozytorium: \`TERMINOWNIK_KEYSTORE_BASE64\`, \`TERMINOWNIK_STORE_PASSWORD\`, \`TERMINOWNIK_KEY_ALIAS\`, \`TERMINOWNIK_KEY_PASSWORD\`.
3. Od tego momentu CI podpisuje z sekretów właściciela (ścieżka 1), a to archiwum staje się kopią zapasową.

Klucz utworzono automatycznie, ponieważ token sesji nie ma prawa zapisu sekretów repozytorium.
Patrz \`RELEASE_CHECKLIST.md\` – to jest udokumentowany bloker produkcyjny." \
  || fail "nie udało się utworzyć Release z kluczem"

log "klucz zapisany w prywatnym Release '$KEY_TAG'"
export_env "$WORK_DIR/keys/terminownik-release.jks" "$WORK_DIR/keys/keystore.properties" "ci-release-created"
