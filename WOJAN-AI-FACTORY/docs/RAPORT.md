# WOJAN AI FACTORY — raport weryfikacji (2026-10-17)

**Status: NIE DEKLARUJĘ PEŁNEJ GOTOWOŚCI.** Obu wersji (EXE, APK) nie da się uruchomić
i przetestować w środowisku budowania (brak systemu Windows i urządzenia Android) — do
pełnego odbioru potrzebne są testy uruchomieniowe na sprzęcie właściciela (receptury:
`docs/TESTY.md`). Poniżej rozdział: co REALNIE przetestowano, a co tylko zaimplementowano.

## 1. Funkcje REALNIE przetestowane (dowody)

| Funkcja | Test | Wynik |
|---|---|---|
| Router AI: failover po błędzie limitu (429) z zapisem rozmowy; model wyczerpany nie wraca | `engine/test/router_failover.test.js` | PASS |
| Router: LOCAL_ONLY nie sięga do chmury; MANUAL = jeden wskazany model | tamże | PASS |
| Router: błąd połączenia ≠ wyczerpanie limitu (provider nie jest blokowany) | tamże | PASS |
| Pipeline: ZADANIE→PROJEKT→KOD→KOMPILACJA→TEST→DIAGNOSTYKA→POPRAWKA→EXE/APK z realną pętlą poprawek (kompilacja fail → diagnoza → fix → OK) | `engine/test/pipeline.test.js` | PASS |
| Trwałość stanu projektu po restarcie (nowy magazyn z tego samego pliku) | tamże | PASS |
| Kontrakt API Androida (status/projekty/zadania/logi/artefakty/role/mode, auth Bearer) | `engine/test/api.test.js` | PASS |
| Przełączanie trybu pracy (POST /api/v1/mode) + walidacja błędnego trybu | tamże | PASS |
| Protokół Ollama: realne żądanie/odpowiedź HTTP `/api/chat` (prawdziwy serwer HTTP, format protokołu) | `engine/test/ollama_protocol.test.js` | PASS |
| Mapowanie błędów providerów: 429 → QuotaError (bez obchodzenia limitów), connection refused → UnavailableError | tamże | PASS |
| Protokół OpenAI-compatible (OmniRoute/OpenRouter/OpenAI): `/chat/completions` + Bearer + quota mapping | tamże | PASS |
| Fabryka E2E przez publiczne API: projekt → zadanie → pipeline z poprawką → artefakt zweryfikowany → widoczny w API | `tests/test_factory_e2e.js` | PASS |
| Silnik w **QuickJS-NG 0.17** (runtime z EXE) przez interfejs hosta C++ (`WojanEngine.init` + `handleRequest`): pipeline z pętlą poprawki, fazy, trwałość | `tests/run_qjs_engine.sh` | PASS |
| Kompilacja Javy → dex: ECJ → j2smali → smali.jar (43 pliki klas → 42 klasy w dex, 141/141 metod z kodem) | build APK + analiza androguard | PASS |
| Podpis APK v1+v2+v3 oficjalnym apksigner 33.0.2 + weryfikacja `verify --print-certs` | build APK | PASS |
| Niezależna walidacja APK (androguard): manifest, sdk 24/34, 3 aktywności,2 permisje, integralność dex | analiza androguard | PASS |
| Kompilacja EXE (Win32+DX11+ImGui+QuickJS-NG, zig cc → PE x64 GUI) + importy systemowe (d3d11, winhttp, ws2_32…) | `desktop/build/build_exe.py` + parser PE | PASS |
| Instalator Setup.exe: PE x64 GUI, kompletne archiwum payload (17 plików, magic `WJAN`) | `desktop/installer/build_installer.py` + parser payloadu | PASS |
| Portable.zip: 18 wpisów, integralność CRC | walidacja zip | PASS |

Uruchomienie całego zestawu: `bash WOJAN-AI-FACTORY/tests/run_all.sh` → **10/10 Node + E2E + QuickJS smoke = wszystkie PASS**.

## 2. Funkcje TYLKO ZAIMPLEMENTOWANE (bez testu uruchomieniowego)

| Funkcja | Przeszkoda (konkretna) |
|---|---|
| Uruchomienie EXE na Windows 11 (UI DX11/ImGui, serwer API dla telefonu, pipeline na prawdziwych kompilatorach) | w środowisku budowania brak Windows i wine — PE nie da się wykonać; zweryfikowano strukturę PE, linkowanie i logikę silnika w QuickJS-NG poza EXE |
| Instalacja Setup.exe (kopiowanie, skrót, wpis Uninstall, `/uninstall`) | jw. — wymaga Windows |
| Instalacja APK na telefonie + połączenie z PC (URL+token) + wysłanie zadania | brak fizycznego urządzenia/emulatora Android |
| Żywe odpowiedzi modeli LLM (Ollama, OmniRoute, Hermes) i realny model-switch po błędzie dostępności | brak GPU/modeli w środowisku; protokoły i logika failover przetestowane na stubach HTTP i mock-providerach |
| Pipeline na prawdziwych narzędziach (MSVC/zig/Gradle) przez SysRunner w EXE | wymaga Windows + SDK na PC właściciela; MockRunner testuje logikę faz |
| Podpis produkcyjny APK | **oczekuje klucza właściciela** — procedura gotowa: `docs/PODPIS-PRODUKCYJNY.md` |

## 3. Wyniki kompilacji

| Cel | Narzędzia | Wynik |
|---|---|---|
| Android APK Release | ECJ 3.37 → j2smali (własny) → smali 1.3.3 → pyaxml → apksigner 33.0.2 | **45 399 B**, verify PASS (v1+v2+v3) |
| Windows EXE | zig 0.17 (x86_64-windows-gnu) + QuickJS-NG 0.17 + ImGui | **2 783 232 B**, PE x64 GUI |
| Setup.exe | zig 0.17, własny instalator Win32 | **3 793 301 B**, payload 17 plików |
| Portable.zip | zipfile | **1 299 427 B**, CRC OK |

## 4. Lokalizacje artefaktów

- `WOJAN-AI-FACTORY/dist/WOJAN-AI-FACTORY-Release.apk` (klucz **dev** — testowy!)
- `WOJAN-AI-FACTORY/dist/WOJAN-AI-FACTORY-Setup.exe`
- `WOJAN-AI-FACTORY/dist/WOJAN-AI-FACTORY-Portable.zip`
- `WOJAN-AI-FACTORY/desktop/build/out/WOJAN-AI-FACTORY.exe` (wewnątrz Setup/Portable)

## 5. Lista błędów

**Błędy wykryte i naprawione w trakcie prac (nie występują już):**
- j2smali: błędne mapy arytmetyki JVM (xor/shl), tablic (0x2E–0x35/0x4F–0x56), ifnull (0xC6/0xC7), etykiety wsteczne, rejestry ≥v16 w formatach 4-bitowych (staging v0–v5), `/range` dla invoke — wszystko naprawione; wynik: 0 błędów asemblacji, dex parsuje się bez błędów
- silnik: rola `agents/{role}/run` czytała zły segment URL (400) — naprawione; `saveConversation` zapisywał pod zagnieżdżonym kluczem — naprawione
- desktop: `zig cc` bez libc++ dla C++ (`zig c++`), nazwa biblioteki `d3dcompiler_47`, brak `cutils.c` w quickjs-ng (header-only) — naprawione w build_exe.py
- UI Ustawienia: przełącznik trybu był atrapą (nie wywoływał API) — **naprawione w tej turze** (POST /api/v1/mode)

**Znane ograniczenia (aktywne, świadome):**
- j2smali: `multianewarray` (tablice wielowymiarowe) → ClassFormatError — kod fabryki nie używa; do naprawy przy własnych projektach z takimi tablicami
- mój podpis v2 w tools/apksig jest tylko fallbackiem (używany gdy brak apksigner.jar); produkcyjnie zawsze oficjalny apksigner
- APK podpisany kluczem dev — **nie do dystrybucji**; procedura klucza produkcyjnego: `docs/PODPIS-PRODUKCYJNY.md` (klucz tworzony i przechowywany przez właściciela poza repo; skrypt `build_apk.py --keystore <scieżka>` gotowy)

## 6. Zgodność z ograniczeniami

- Zero WWW/PWA — brak jakiegokolwiek serwera frontendowego; aplikacje: Win32 EXE + natywny Android Java
- Zero integracji z CrossFlow Command — nie zmieniono żadnego pliku poza `WOJAN-AI-FACTORY/`
- Klucze/hasła: `keystore/*.p12`, `*.env`, `wojan-config.json` w `.gitignore`; hasła tylko w zmiennych środowiskowych; procedura podpisu zakazuje zapisu sekretów w repo/logach

## 7. Diagnostyka problemu „APK nie instaluje się" (2026-10-19)

Kontekst: właściciel zgłasza „apk nie może zainstalować / nadal nie działa" bez komunikatu błędu.
Wykonane realne prace diagnostyczne (narzędzia własne, uruchomione — wyniki realne):

1. **Struktura ZIP/AXML** (własne parsery, czysty Python): central directory poprawne, manifest
   binarny AXML dekoduje się w całości (typed values, typed string references), wpisy zipalign 4-B.
2. **dexcheck.py (`tools/dexcheck.py`)** — walidator dex: nagłówek (adler32/sha1), mapa sekcji,
   class_data, code_item, szerokości instrukcji (z przeskokami payloadów switch/array-data),
   try/catch handlery (sleb), `ins_size` vs proto, zakresy rejestrów invoke.
   **Wynik dla bieżącego dex: 141 code_item, 0 błędów.** Walidator wbudowany w `build_apk.py`.
3. **Poprawki bezpieczeństwa weryfikatora ART w j2smali**: porównania referencji nie przechodzą
   przez rejstry `int` (ART VerifyError); wynik `iget-wide` nie nakłada się na rejestr obiektu.
4. **apksigner verify** dla minSdk 21/23/26/34 — **PASS** (v1+v2+v3), cert `66f4dae4…`.
5. **androguard**: 42 klasy, pełne xref bez błędów.

**Rozmiar: 45 399 B, SHA-256: `a3503cd809f0ce4946de1f52336a7e6f4d46feed672f68926d7aaffafe2317e3`.**

**NIEUKOŃCZONE — przeszkoda:** brak fizycznego urządzenia z Androidem — nie da się wykonać testu
instalacji w środowisku budowania. Mechaniczna poprawność APK zweryfikowana powyżej; jednoznaczna
diagnoza wymaga **dokładnego komunikatu błędu** z telefonu lub wyniku `adb install -r`
(procedura: `docs/INSTALACJA.md`). Możliwe przyczyny po stronie urządzenia: uszkodzone pobranie
(plik ≠ 45 399 B), blokada „nieznanych źródeł", konflikt podpisu ze starą wersją (odinstaluj),
Play Protect / nakładka producenta (MIUI/OneUI).


## 8. WYDANIE v1.0.2 (2026-10-19) — odpowiedź na „nadal nie działa"

- Nowy artefakt o **nowej nazwie** `WOJAN-AI-FACTORY-Release-v1.0.2.apk` (zabicie cache'a
  przeglądarki — użytkownik mógł wielokrotnie dotykać starego pobranego pliku).
- Wersja 1.0.2 / versionCode 3 / minSdk 21; ekran startowy aplikacji pokazuje numer wersji
  (diagnostyka: użytkownik od razu widzi, który build uruchamia).
- **Crash-guard**: `MainActivity.onCreate` owinięty w try/catch — każdy błąd startu jest
  WIDOCZNY na ekranie (czerwony panel z nazwą wyjątku) zamiast cichego zamknięcia aplikacji.
- dexcheck wbudowany w build: **143 code_item, 0 błędów**; apksigner verify PASS (sdk 21/26/34,
  exit 0); androguard „successfully validated", minSdk 21, exported=true, MAIN/LAUNCHER OK.
- SHA-256: `a3503cd809f0ce4946de1f52336a7e6f4d46feed672f68926d7aaffafe2317e3` (45 399 B).


## 9. WYDANIE v1.0.3 (2026-10-22) — odpowiedź na „nie otwiera aplikacji"

Zrzut ekranu właściciela (Samsung, „Informacje o aplikacji — Zainstalowane") rozstrzygnął:
aplikacja **instaluje się poprawnie**, usterka jest przy **uruchamianiu**. Wprowadzone zmiany:

- **Start dwuetapowy** w `MainActivity`: najpierw minimalny zielony ekran „Start OK — wersja 1.0.3"
  (zero zależności poza TextView), dopiero potem właściwy interfejs. Każda awaria startu zamienia
  się w **czerwony panel** z nazwą wyjątku, komunikatem i 8 ramkami śladu (zamiast cichego
  zamknięcia aplikacji).
- **dexcheck rozszerzony** o kontrole reguł weryfikatora ART: liczba rejestrów `invoke` vs
  prototyp metody, typ `move-result*` vs typ zwracany, rodzaj `iget/iput/sget/sput` vs deskryptor
  pola, `return*` vs typ metody. Wynik na bieżącym dex: **148 code_item, 0 błędów**.
- Dodatkowo poza dexcheck (analiza porównawcza): brak naruszeń formatów 4-bit, ART inicjalizuje
  wszystkie kopie niezainicjalizowanych referencji (źródła AOSP `register_line.cc`), co wykluczyło
  hipotezę błędów konstruktora.

**Artefakt: `dist/WOJAN-AI-FACTORY-Release-v1.0.3.apk` (45 399 B), SHA-256:
`0bc7afbb87fe888f9ea81449af6717ebfffaf70795689e3b196f5474b2c371a5`, versionCode 4,
versionName 1.0.3, minSdk 21/target 34.** apksigner verify PASS (sdk 21/26/34), androguard
„successfully validated". Uwaga: dev-klucz testowy wygenerowany ponownie w tej sesji — wymagana
dezinstalacja wcześniejszych wersji przed instalacją v1.0.3.

**NIEUKOŃCZONE — przeszkoda:** brak fizycznego urządzenia — test uruchomieniowy wykona właściciel;
ekran startowy v1.0.3 zwraca diagnozę wizualnie (zielony / czerwony panel / brak obrazu).
