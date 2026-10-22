# RAPORT TESTÓW — APK WOJAN AI FACTORY v1.0.5 (2026-10-22)

Artefakt: `dist/WOJAN-AI-FACTORY-Release.apk` (kopia do pobrania na telefon: `dist/WOJAN-105.apk`)
Rozmiar: **45 399 B** · SHA-256: `c5705d9da9a1decf8a92edca5d9e5e719579a5f94488cd9e4ea5c59953ccaa7a`
Wersja: **1.0.5** (versionCode 6) · minSdk 21 / targetSdk 34

## 1. Znalezione i naprawione błędy (przegląd kodu od pierwszej do ostatniej linii)

| # | Błąd | Skutek na telefonie | Naprawa |
|---|------|---------------------|---------|
| 1 | **`ACC_CONSTRUCTOR` (0x10000) pomijany dla `<init>`/`<clinit>`** (brak w tabeli flag j2smali) | Android nie mógł utworzyć instancji Activity → aplikacja „nie otwierała się" bez komunikatu (WSZYSTKIE wersje v1.0.0–v1.0.3) | flaga `constructor` w tabeli + zabezpieczenie nazwowe + kontrola w dexcheck (v1.0.4) |
| 2 | **`aput` z kolejnością operandów `(tablica, indeks, wartość)` zamiast smali `(wartość, tablica, indeks)`** | każda metoda zapisująca do tablicy (m.in. `newProject`, `newTask`) robiła `VerifyError`/błędne dane | poprawna kolejność + szerokie wartości (v1.0.5) |
| 3 | `step()` zdejmował 3 sloty zamiast 4 przy `lastore/dastore` | rozjechanie modelu stosu przy tablicach szerokich | korekta (v1.0.5) |
| 4 | Wcześniej (v1.0.1–1.0.2): staging `if_acmp` przez rejstry int, nakładanie `iget-wide`, minSdk 24→21 | możliwe odrzucenia weryfikatora ART | naprawione, potwierdzone dexverify |

## 2. Testy REALNIE wykonane w tej turze (wyniki)

### A. JVM — realne wykonanie logiki aplikacji (`android/tests/jvm/run_jvm_test.sh`) — **23/23 PASS**
Kod aplikacji skompilowany i **uruchomiony** na JVM z atrapami Androida:

| Test | Wynik |
|------|-------|
| start: zielony ekran „Start OK" pokazany | PASS |
| start: numer wersji 1.0.5 na ekranie | PASS |
| start: setContentView wywołane | PASS |
| start: tytuł główny „WOJAN AI FACTORY" | PASS |
| start: pole „Adres silnika Windows" | PASS |
| start: pole „Token dostępu" | PASS |
| start: przycisk „Zapisz i połącz" | PASS |
| start: przycisk „Otwórz projekty" | PASS |
| start: buildUi bez wyjątku (brak panelu błędu) | PASS |
| showFatal: tytuł BŁĄD STARTU | PASS |
| showFatal: nazwa wyjątku na ekranie | PASS |
| showFatal: klasa wyjątku na ekranie | PASS |
| showStartup: zielony „Start OK" | PASS |
| showStartup: wersja 1.0.5 | PASS |
| Store: getServer po save | PASS |
| Store: getToken po save | PASS |
| Store: getAlias po saveAlias | PASS |
| Store: getRefresh po saveRefresh | PASS |
| SettingsActivity: setContentView | PASS |
| SettingsActivity: bez wyjątku | PASS |
| Runnable buildUi: tytuł się pojawił | PASS |
| Runnable buildUi: bez panelu błędu | PASS |
| dp(24) = 48 przy density 2.0 | PASS |

### B. dexverify.py — weryfikator typów rejestrów (reguły ART), **każda instrukcja każdej metody** — **0 błędów**
148 metod: move/move-object/move-wide vs typy, return* vs prototyp, invoke vs sygnatury,
`<init>` na niezainicjalizowanym odbiorcy + inicjalizacja wszystkich kopii (jak ART),
move-result po invoke, iget/iput/sget/sput vs deskryptory pól, if/switch/unop operand types.

### C. dexcheck.py (struktura DEX) — **148 code_item, 0 błędów**
nagłówki (adler32/sha1), szerokości instrukcji z payloadami, try/catch, `ins_size` vs prototyp,
**ACC_CONSTRUCTOR dla każdego `<init>`/`<clinit>`**, liczby rejestrów invoke, typy move-result,
rodzaje operacji na polach, return vs typ metody.

### D. apksigner (oficjalne narzędzie Google) — **PASS** dla minSdk 21, 26, 34 (v1+v2+v3)

### E. androguard (zewnętrzny parser) — **„successfully validated"**, manifest: vName 1.0.5,
vCode 6, minSdk 21, exported=true, MAIN/LAUNCHER poprawne.

### F. Testy silnika (node 10/10 + factory E2E + QuickJS smoke) — PASS (bez zmian w silniku).

## 3. Czego NIE da się przetestować bez fizycznego urządzenia (NIEUKOŃCZONE / przeszkoda)

- Instalacja i uruchomienie na realnym ART/Samsungu — środowisko budowania nie ma sprzętu
  ani emulatora Androida. **Pełne „działa" wymaga jednego testu na telefonie.**
- Faktyczne zachowanie nakładek producenta (OneUI/MIUI), Play Protect, wydajność, sieć LAN.

## 4. Ekran startowy = diagnostyka na telefonie

- 🟢 „WOJAN AI FACTORY / wersja 1.0.5 / Start OK" → startuje; potem główny ekran.
- 🔴 panel „BŁĄD STARTU" z nazwą wyjątku i śladem → zdjęcie = jednoznaczna diagnoza.
- ⚫ cisza → awaria ładowania klasy (zgłoszenie → build izolujący).

## 5. Test na telefonie (procedura dla właściciela)

1. Odinstaluj WOJAN (Ustawienia → Aplikacje).
2. **Usuń wszystkie pliki WOJAN z folderu Pobrane** (stare i nowe).
3. Pobierz **TYLKO** `WOJAN-105.apk` (45 399 B) → zainstaluj → Otwórz.
4. W Ustawieniach aplikacji na dole musi być **Wersja 1.0.5** — inny numer = zły plik.
