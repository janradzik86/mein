# Terminownik — spiżarnia i apteczka pod kontrolą terminów

W pełni **lokalna**, natywna aplikacja na Androida: spis produktów z terminami ważności,
przypomnienia, skanowanie kodów, odczyt daty z etykiety (OCR), lista zakupów i pełna kopia zapasowa.
Bez konta, bez internetu, bez reklam, bez analityki.

* Package: `pl.terminownik.app`
* Wersja: `1.0.0` (kod 1)
* minSdk 26 (Android 8.0), compileSdk/targetSdk 36
* Kotlin + Jetpack Compose + Material 3, Room, DataStore, CameraX, ML Kit (modele wbudowane)

## Co aplikacja robi

| Obszar | Możliwości |
| --- | --- |
| Spis | Osobne sekcje **Spiżarnia** i **Apteczka**; pozycja = nazwa, ilość, jednostka, miejsce, zdjęcie, partia, termin, notatka |
| Partie | Ten sam produkt może mieć wiele partii z różnymi terminami — **nie są łączone**; „Uzupełnij" tworzy nową partię |
| Terminy | Widoki: kończące się / po terminie / brak daty / do potwierdzenia; rozróżnienie **„należy spożyć do"** (bezpieczeństwo) i **„najlepiej spożyć przed"** (jakość) |
| Kody | Skaner ML Kit (EAN/UPC/GTIN-14/DataMatrix/GS1-128), walidacja sumy kontrolnej, lokalny słownik **kod → ostatnio wpisana nazwa** |
| GS1 | Gdy kod faktycznie zawiera elementy GS1: AI 01 (GTIN), 10 (partia), 21 (numer seryjny), 17 (termin), 11 (data produkcji), 30 (sztuki), 310n/315n (masa/objętość) |
| OCR | Zdjęcie etykiety albo plik z galerii → propozycje dat z poziomem pewności i ostrzeżeniami (np. `03/04/2027` = zapis niejednoznaczny) |
| Potwierdzanie | **Każda** data odczytana automatycznie trafia do formularza jako niepotwierdzona i nie tworzy przypomnienia, dopóki jej nie zatwierdzisz |
| Przypomnienia | Konfigurowalne wyprzedzenie (0–60 dni), godzina, przypomnienie w dniu terminu; alarmy dokładne, gdy system na nie pozwala |
| Zakupy | Lista zakupów, „kup ponownie" ze spisu, odhaczanie, eksport CSV |
| Dane | Eksport CSV spisu, **pełna kopia zapasowa** (ZIP: manifest + JSON + zdjęcia) z podglądem przed importem i trybami *zastąp* / *dołącz*, kasowanie wszystkich danych |

## Czego aplikacja NIE robi (świadomie)

* Nie łączy się z internetem — uprawnienie `INTERNET` jest **usunięte z manifestu** (`tools:node="remove"`),
  a w buildzie release kontroluje to CI.
* Nie pobiera danych produktów z płatnych ani zewnętrznych API. Z kodu EAN **nigdy** nie zgaduje terminu ważności.
* Nie udziela porad medycznych ani nie sugeruje dawkowania leków.
* Nie ocenia bezpieczeństwa żywności po samej dacie — data jest wskazówką organizacyjną.
* Nie obiecuje przypomnienia co do sekundy: Android ogranicza pracę w tle (Doze, optymalizacja baterii).
  Gdy alarmy dokładne są dozwolone, używamy ich; inaczej planujemy okno ±15 minut i mówimy o tym wprost w ustawieniach.

## Model sprzedaży

* Brak subskrypcji, brak reklam, brak Google Play Billing.
* Pełna wersja jest dystrybuowana jako **płatny plik APK poza sklepami**; wszystkie funkcje są w niej odblokowane.
* Pobranie ze sklepu zewnętrznego i obsługa płatności sklepowej **nie wchodzą w zakres MVP**.
* Aplikacja nie zawiera mechanizmu licencji ani „zabezpieczenia antypirackiego" i nie jest tak przedstawiana.

## Budowanie

### A. CI (GitHub Actions) — zalecane

Workflow: `.github/workflows/terminownik-release.yml` (w katalogu nadrzędnym repozytorium).
Wykonuje: kontrolę spójności zasobów tekstowych → `:app:lintRelease` → testy jednostkowe →
`:app:assembleRelease` → `apksigner verify --verbose --print-certs` → SHA-256 → kontrolę braku
`INTERNET` i `debuggable=true` → publikację GitHub Release z plikiem `terminownik-release.apk`.

Sekrety (Settings → Secrets and variables → Actions):

| Sekret | Znaczenie |
| --- | --- |
| `TERMINOWNIK_KEYSTORE_BASE64` | `base64 -w0 terminownik-release.jks` |
| `TERMINOWNIK_STORE_PASSWORD` | hasło keystore'a |
| `TERMINOWNIK_KEY_ALIAS` | alias klucza (domyślnie `terminownik-release`) |
| `TERMINOWNIK_KEY_PASSWORD` | hasło klucza |

Bez tych sekretów CI używa klucza przechowywanego w prywatnym Release `signing-key-storage`,
a przy pierwszym uruchomieniu tworzy go i tam zapisuje (szczegóły i ścieżka migracji:
`RELEASE_CHECKLIST.md`).

### B. Lokalnie — jedna komenda, bez żadnych przygotowań

`scripts/one-shot-build.sh` (Linux / macOS / WSL / Git Bash) oraz `scripts/one-shot-build.ps1`
(Windows) **same instalują** JDK 21 i Android SDK (bez Android Studio), tworzą trwały klucz
podpisywania, kompilują, podpisują i weryfikują APK.

```bash
cd terminownik-android
bash scripts/one-shot-build.sh            # Linux / macOS / WSL / Git Bash
```

```powershell
cd terminownik-android
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\one-shot-build.ps1             # Windows
```

Flagi: `--skip-tests` / `-SkipTests` (najszybciej do pliku APK), `--clean` / `-Clean`,
`--tools-dir` / `-ToolsDir <katalog>`. Narzędzia (~800 MB) i zależności Gradle są buforowane
w `~/.terminownik-tools` i `~/.gradle`, więc kolejne buildy trwają minuty.

### C. Lokalnie — gdy JDK i Android SDK już są zainstalowane

```bash
cd terminownik-android
bash scripts/build-release.sh --assemble-first   # NAJPIERW APK, potem lint i testy informacyjnie
bash scripts/build-release.sh                    # lint + testy + APK w jednym przebiegu
bash scripts/build-release.sh --skip-tests       # bez testów
```

Wymagania: JDK 17–21, Android SDK (platform 36 + build-tools), `ANDROID_HOME` albo
`local.properties` z `sdk.dir=...`. Tryb `--assemble-first` jest zalecany przy pierwszym buildzie:
ewentualny problem z lintem nie blokuje powstania pliku instalacyjnego.

### Gdy kompilacja się nie powiedzie

Skrypt zapisuje pełny log do `dist/BUILD_FAILURE_LOG.txt` i drukuje jego ostatnie 60 linii.
Wystarczy wkleić ten plik w zgłoszeniu — zawiera dokładne błędy kompilatora z numerami linii.

### Instalacja

```bash
adb install -r dist/terminownik-release.apk
```

albo skopiuj plik APK na telefon i otwórz go (zezwól na instalację z tego źródła).

## Weryfikacja lokalna (bez SDK)

```bash
# logika biznesowa: 67 scenariuszy domenowych + walidator kopii
export JAVA_HOME=/ścieżka/jdk KOTLINC=/ścieżka/kotlinc
bash tools/verify-domain.sh

# spójność zasobów tekstowych z kodem (R.string.*, liczba argumentów, klucze nieużywane)
python3 tools/check-strings.py
```

## Testy

* Jednostkowe: `app/src/test/` — `DomainScenariosTest` (67 scenariuszy), `PlFormatTest`, `CsvExportTest`,
  `BackupValidatorTest`.
* Instrumentowane: `app/src/androidTest/` — trwałość danych po zamknięciu/ponownym otwarciu bazy,
  rozłączność partii, round-trip kopii zapasowej (ilości, partie, zdjęcia, terminy), odrzucanie obcych
  i uszkodzonych kopii, przeliczanie przypomnień po edycji bez duplikatów, nadganianie zaległości, DataStore.

Stan wykonania i dosłowne wyniki: **`TEST_REPORT.md`** (to, czego nie uruchomiono, jest tam oznaczone
jako NIEWYKONANE — nigdy jako zaliczone).

## Dokumentacja

| Plik | Zawartość |
| --- | --- |
| `PLAN.md` | plan, architektura, kryteria odbioru i ich stan |
| `TEST_REPORT.md` | co realnie uruchomiono, z jakimi wynikami; co pozostało niewykonane |
| `RELEASE_CHECKLIST.md` | podpisywanie, sekrety, weryfikacja wydania, blokery produkcyjne |
| `PRIVACY_PL.md` | prywatność i rzeczywisty przepływ danych |
| `LICENCJE_PL.md` | biblioteki, licencje i źródła danych |
