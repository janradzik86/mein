# Lista kontrolna wydania — Terminownik

## 1. Artefakty wydania

| Artefakt | Ścieżka / miejsce | Stan |
| --- | --- | --- |
| Podpisany APK release | GitHub Release `terminownik-v1.0.0` → `terminownik-release.apk`; lokalnie `dist/terminownik-release.apk` | **nie zbudowane** (bloker: patrz §6) |
| Sumy kontrolne | `SHA256SUMS.txt` (Release / `dist/`) | j.w. |
| Raport weryfikacji | Release body + `dist/VERIFY_REPORT.md` | j.w. |
| Kod źródłowy | gałąź `arena/3c74200e-mein` w `janradzik86/mein` (kompletna, bez sekretów) | gotowe |
| Dokumentacja | `README_PL.md`, `PLAN.md`, `TEST_REPORT.md`, `PRIVACY_PL.md`, `LICENCJE_PL.md` | gotowe |
| Zrzuty ekranu | — | **brak** (wymaga emulatora) |

Format: **APK** (nie AAB, nie zestaw splitów, nie debug). Wariant release ma
`minifyEnabled = true`, `shrinkResources = true`, `debuggable = false`,
`dependenciesInfo { includeInApk = false; includeInBundle = false }`.

## 2. Podpisywanie

Konfiguracja w `app/build.gradle.kts`: V1 + V2 + V3 włączone, V4 wyłączone.
Źródło danych podpisu (w kolejności priorytetu):

1. Zmienne środowiskowe: `TERMINOWNIK_STORE_FILE`, `TERMINOWNIK_STORE_PASSWORD`,
   `TERMINOWNIK_KEY_ALIAS`, `TERMINOWNIK_KEY_PASSWORD`.
2. Plik `keystore.properties` w katalogu głównym modułu (w `.gitignore`).
3. CI: `scripts/ci-prepare-signing.sh` — sekrety → istniejący klucz w prywatnym Release
   `signing-key-storage` → utworzenie nowego klucza i zapisanie go tam.

Zadanie zabezpieczające `requireReleaseSigning` jest podpięte do `assembleRelease`:
build release bez klucza przerywa się zamiast tworzyć niepodpisany plik.

### Zasady bezpieczeństwa klucza

* Klucz tworzony **raz**; skrypt `make-keystore.sh` odmawia nadpisania istniejącego pliku (zweryfikowane).
* Hasła losowe (24 bajty `/dev/urandom`), nigdy nie drukowane, nigdy nie commitowane.
* Pliki klucza poza repozytorium: domyślnie `~/keys/terminownik` (uprawnienia `700`/`600`).
* `.gitignore` wyklucza: `*.jks`, `*.keystore`, `*.pk12`, `keystore.properties`, `signing.properties`,
  `keys/`, `.signing/`, `dist/`, `*.apk`.
* Kopia zapasowa OFFLINE obowiązkowa: menedżer haseł albo szyfrowany nośnik; utrata klucza
  uniemożliwia aktualizację raz wydanej aplikacji.

## 3. Sekrety do ustawienia przez właściciela

Settings → Secrets and variables → Actions → New repository secret:

| Sekret | Jak uzyskać |
| --- | --- |
| `TERMINOWNIK_KEYSTORE_BASE64` | `base64 -w0 ~/keys/terminownik/terminownik-release.jks` |
| `TERMINOWNIK_STORE_PASSWORD` | z `keystore.properties` (pole `storePassword`) |
| `TERMINOWNIK_KEY_ALIAS` | `terminownik-release` |
| `TERMINOWNIK_KEY_PASSWORD` | z `keystore.properties` (pole `keyPassword`) |

Uwaga: token sesji agenta **nie ma prawa** zapisu sekretów (`gh secret set` → 403), dlatego
konfiguracja jest po stronie właściciela. Do czasu jej wykonania CI korzysta z klucza
w prywatnym Release — to **udokumentowany bloker produkcyjny** (§6).

## 4. Weryfikacja po zbudowaniu (wykonywana automatycznie w CI)

```bash
APK=app/build/outputs/apk/release/app-release.apk
"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$APK"
sha256sum "$APK"
"$BUILD_TOOLS/aapt2" dump badging "$APK" | grep -E "package:|sdkVersion|targetSdkVersion"
"$BUILD_TOOLS/aapt2" dump permissions "$APK"        # musi NIE zawierać android.permission.INTERNET
apkanalyzer manifest print "$APK" | grep -i debuggable   # musi NIE być android:debuggable="true"
unzip -l "$APK" | grep lib/ | awk '{print $4}' | cut -d/ -f2 | sort -u   # ABI
```

CI przerywa wydanie, jeśli w APK znajdzie `INTERNET` albo `debuggable="true"`.
Wyniki (dosłowne wyjście poleceń) trafiają do treści Release i do `dist/VERIFY_REPORT.md`.

## 5. Kolejność czynności wydania

1. Ustawić sekrety (§3) **albo** zaakceptować klucz z prywatnego Release i przenieść go do sejfu.
2. Uruchomić build: push na `arena/3c74200e-mein` (workflow reaguje na zmiany w `terminownik-android/**`).
3. Poczekać na job `verify`: lint → testy jednostkowe → `assembleRelease` → weryfikacja → Release.
4. Sprawdzić w Release: wersję, rozmiar, SHA-256, wyjście `apksigner`, brak `INTERNET`, ABI.
5. Pobrać `terminownik-release.apk` i zainstalować: `adb install -r terminownik-release.apk`.
6. Przejść scenariusz odbiorczy z `TEST_REPORT.md` §6 (12 kroków).
7. Job `instrumented` (emulator) traktować jako informacyjny — jego wynik raportujemy dosłownie.
8. Uzupełnić `TEST_REPORT.md` o realne wyniki buildu i testów na urządzeniu.

## 6. Blokery produkcyjne (uczciwie, bez kamuflowania)

| # | Bloker | Wpływ | Co odblokowuje |
| --- | --- | --- | --- |
| 1 | **GitHub Actions nie startuje jobów** w `janradzik86/mein` (`startup_failure`, 0 jobów, 0 zarejestrowanych workflowów; nawet dla workflow hello-world) | brak zbudowanego APK, brak wyników lint/testów w Gradle | zmiana widoczności repo na publiczną (minuty darmowe) albo włączenie Actions / limit wydatków w Billing |
| 2 | **Token sesji bez praw**: `gh repo create` 403, `gh secret set` 403, `PATCH /repos` 403, odczyt ustawień Actions 403 | nie można utworzyć osobnego repo ani wgrać sekretów; aplikacja żyje jako osobny katalog Gradle w repo `mein` na gałęzi sesji | token z `admin:repo` albo ręczne kliknięcia właściciela |
| 3 | **Brak Android SDK w środowisku roboczym** (blokady `dl.google.com`, `services.gradle.org`, Maven; brak roota) | warstwa Android nie została lokalnie skompilowana; pierwszy build może wymagać poprawek, które agent zrobi w tej samej sesji | CI albo build lokalny `scripts/build-release.sh` |
| 4 | **Klucz podpisywania utworzony przez CI** (jeśli sekrety właściciela nie zostaną ustawione) | klucz leży w prywatnym Release, a nie w sejfie właściciela — ryzyko operacyjne, nie techniczne | ustawienie sekretów z §3; Release z kluczem zostaje jako kopia zapasowa |
| 5 | **Brak zrzutów ekranu i testu na urządzeniu** | niespełniony punkt briefu „zainstaluj finalny APK i przejdź główny scenariusz" | po odblokowaniu buildu: emulator w CI albo telefon właściciela |
| 6 | **Brak integracji płatności/sklepu** | zgodnie z założeniami: pełna wersja jako płatny APK poza sklepami; pobranie ze sklepu zewnętrznego poza MVP | decyzja biznesowa właściciela (nie jest to defekt kodu) |

Koszt infrastruktury: **0 zł** przy repo publicznym (minuty Actions darmowe) albo w ramach darmowego
limitu 2000 min/mies. dla repo prywatnych (build zużywa ok. 15–25 min). Brak serwerów, brak backendu,
brak usług płatnych — aplikacja jest w pełni lokalna.
