#!/usr/bin/env bash
# Tworzy trwałe, prywatne klucze podpisywania wydań Terminownika.
#
# Zasady (zgodne z wymaganiami właściciela):
#   * klucz tworzony JEDEN raz – skrypt nigdy nie nadpisuje istniejącego pliku,
#   * hasła są losowe i nigdy nie są drukowane na standardowym wyjściu,
#   * wynik trafia do katalogu poza repozytorium (domyślnie ~/keys/terminownik),
#   * skrypt nie wysyła niczego do sieci.
#
# Użycie:
#   scripts/make-keystore.sh [KATALOG_WYJŚCIOWY]
#
# Po uruchomieniu:
#   1. skopiuj `keystore.properties` i `terminownik-release.jks` do menedżera haseł / sejfu,
#   2. zrób kopię zapasową OFFLINE (nośnik szyfrowany, menedżer haseł, sejf firmowy),
#   3. w CI ustaw sekrety: TERMINOWNIK_KEYSTORE_BASE64, TERMINOWNIK_STORE_PASSWORD,
#      TERMINOWNIK_KEY_ALIAS, TERMINOWNIK_KEY_PASSWORD (patrz RELEASE_CHECKLIST.md).
set -euo pipefail

OUT_DIR="${1:-$HOME/keys/terminownik}"
STORE_FILE="$OUT_DIR/terminownik-release.jks"
PROPS_FILE="$OUT_DIR/keystore.properties"
ALIAS="terminownik-release"
VALIDITY_DAYS=10950   # 30 lat – klucz musi przeżyć cały cykl życia aplikacji
DNAME="CN=Terminownik, OU=Release, O=Wlasciciel aplikacji, L=Gliwice, ST=Slaskie, C=PL"

if [[ -f "$STORE_FILE" ]]; then
  echo "BŁĄD: klucz już istnieje: $STORE_FILE" >&2
  echo "Istniejącego klucza NIE WOLNO nadpisać – stracisz możliwość aktualizacji wydanej aplikacji." >&2
  exit 1
fi

# keytool: z JDK, z PATH albo ze wskazanego JAVA_HOME.
KEYTOOL="${KEYTOOL:-}"
if [[ -z "$KEYTOOL" ]]; then
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/keytool" ]]; then
    KEYTOOL="$JAVA_HOME/bin/keytool"
  elif command -v keytool >/dev/null 2>&1; then
    KEYTOOL="$(command -v keytool)"
  else
    echo "BŁĄD: nie znaleziono keytool. Ustaw JAVA_HOME albo KEYTOOL=/ścieżka/keytool." >&2
    exit 2
  fi
fi

random_password() {
  # 24 bajty losowe → base64 bez znaków specjalnych problematycznych w properties.
  head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 28
}

mkdir -p "$OUT_DIR"
chmod 700 "$OUT_DIR"

STORE_PASSWORD="$(random_password)"
KEY_PASSWORD="$(random_password)"

echo "Tworzę keystore: $STORE_FILE (alias: $ALIAS, ważność: $VALIDITY_DAYS dni)"
"$KEYTOOL" -genkeypair \
  -keystore "$STORE_FILE" \
  -storetype JKS \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity "$VALIDITY_DAYS" \
  -storepass "$STORE_PASSWORD" \
  -keypass "$KEY_PASSWORD" \
  -dname "$DNAME" >/dev/null

umask 077
cat > "$PROPS_FILE" <<EOF
# Plik prywatny – NIE commituj go (jest w .gitignore).
storeFile=$STORE_FILE
storePassword=$STORE_PASSWORD
keyAlias=$ALIAS
keyPassword=$KEY_PASSWORD
EOF
chmod 600 "$PROPS_FILE" "$STORE_FILE"

echo "Gotowe. Hasła zapisano w: $PROPS_FILE (nie są wyświetlane)."
echo "Odcisk SHA-256 certyfikatu (możesz go pokazywać publicznie):"
"$KEYTOOL" -list -v -keystore "$STORE_FILE" -storepass "$STORE_PASSWORD" -alias "$ALIAS" 2>/dev/null \
  | grep -A1 "SHA256:" | head -2 || true
echo
echo "Następne kroki:"
echo "  1. Zrób kopię zapasową OFFLINE: $STORE_FILE oraz $PROPS_FILE"
echo "  2. Sekrety CI: TERMINOWNIK_KEYSTORE_BASE64=\$(base64 -w0 $STORE_FILE)"
echo "     TERMINOWNIK_STORE_PASSWORD / TERMINOWNIK_KEY_ALIAS=$ALIAS / TERMINOWNIK_KEY_PASSWORD"
echo "  3. Sprawdź podpis: apksigner verify --verbose --print-certs <apk>"
