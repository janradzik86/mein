# RAPORT TESTÓW — WOJAN AI FACTORY APK v1.0.6 (2026-10-22)

Żądanie użytkownika: **„Sprawdź kod od pierwszej linii aż do ostatniej, napraw błędy
i zrób szczegółowy test z raportem. APK Release daj do pobrania jak będzie naprawdę
działać — mam mało miejsca na telefonie, nie potrzebne śmieci."**

Poniżej: co zostało sprawdzone linia po linii, co naprawione, co REALNIE
przetestowane (z wynikiem), a co tylko zaimplementowane.

---

## 1. Zakres audytu „od pierwszej do ostatniej linii"

### 1.1 Tłumacz j2smali (warstwa, w której były WSZYSTKIE dotychczasowe bugi APK)
Przejrzane linia po linii i porównane ze specyfikacją JVM (§6.5) + dokumentacją Dalvik:
**każda rodzina rozkazów** w `step()` (przepływ stosu/typów) i `emit_insn()` (emisja smali):
stałe, load/store, tablice (aget/aput), dup/permutacje, arytmetyka int/float/long/double,
przesunięcia, konwersje 0x85–0x93, cmp, skoki, switch (packed/sparse), zwroty, pola
(iget/iput/sget/sput), wywołania (35c/3rc), new/check-cast/instance-of/throw/monitor,
ldc/ldc2_w, iinc, deklaracje klas/pól/metod, flagi ACC (w tym ACC_CONSTRUCTOR).

Histogram opcode'ów 6 klas produkcyjnych (43 pliki .class, 148 metod) wyznaczył,
które rozkazy faktycznie wchodzą w skład APK — pełna lista w sekcji 3.

### 1.2 Manifest i pakiet APK
- binarny AXML rozbity bajt po bajcie: **0 referencji zasobów** (brak `@mipmap`,
  `@style` → brak ryzyka `Resources.NotFoundException` przy starcie),
- etykiety to stringi dosłowne; `label="WOJAN AI FACTORY"`, `exported=true` tylko
  dla MainActivity z MAIN/LAUNCHER; usesCleartextTraffic=true (LAN HTTP, świadome),
- zawartość ZIP: `AndroidManifest.xml + classes.dex + META-INF` — nic więcej
  (zero śmieci w APK).

### 1.3 Kod aplikacji (Java)
23 testy uruchomieniowe na JVM (realne wykonanie `onCreate`, `showStartup`,
`showFatal`, `buildUi`, `connect`, `Store`, `SettingsActivity` na atrapach Androida).

---

## 2. Znalezione i naprawione bugi

### 2.1 Bugi, które ŁAMAŁY APK (poprawione w v1.0.4 / v1.0.5 — patrz raport 1.0.5)
| # | Bug | Skutek na telefonie | Naprawiony w |
|---|-----|---------------------|--------------|
| A | brak `ACC_CONSTRUCTOR` (0x10000) na wszystkich `<init>/<clinit>` | Android nie mógł utworzyć Activity — apka „nie otwierała się" | v1.0.4 |
| B | `aput` złą kolejnością operandów + zły bilans stosu dla `lastore/dastore` | każda metoda zapisująca do tablic pisała po złych rejestrach | v1.0.5 |

### 2.2 Bugi znalezione w TYM audycie (6 sztuk — wszystkie w nieużywanych rozkazach)
Każdy **realny** w tłumaczu, ale **żaden nie występował w ścieżce kodu tej apki**
(histogram opcode'ów: 0 wystąpień). Naprawione + testy regresyjne:

| # | Bug | Gdyby był użyty | Dowód (stary tłumacz) |
|---|-----|-----------------|----------------------|
| 1 | `fconst_2` emitował bity `0x7f000000` zamiast `0x40000000` | `2.0f` = cicha zła wartość | `const v6, 0x7f000000` |
| 2 | przepływ stosu konwersji 0x85–0x93 mylił źródła szerokie z wynikami szerokimi (8 złych rozkazów: i2l/i2d/l2i/l2f/f2d/d2i/d2l/d2f) | po konwersji **wszystkie dalsze rejestry w bloku przesunięte** — wartości śmieciowe | `add-long v5, v5` (rejestry w oknie scratch!) |
| 3 | `lshl/lshr/lushr`: bilans stosu −3 zamiast −1 | jw., po każdym przesunięciu long | `const-wide v7` nadpisywał wynik `shl-long` |
| 4 | `putfield` wartości szerokiej: value/obiekt z złych slotów | zapis `long/double` do pola pisał obiekt=wskaznik na wartość | `iput-wide v9, v8` (value-hi jako value, value-lo jako obiekt) |
| 5 | przepływ typów po `dup_x1/dup_x2/dup2_x1/dup2_x2/swap` zostawiał STARE typy w slotach | błędne `move` vs `move-object` po permutacji → VerifyError lub zła ścieżka | testy typów: `[A A I A]` |
| 6 | `P` (sloty parametrów metody) liczone jako `len(params)` zamiast sumy slotów | metody z parametrami `long/double` przypisywały parametry do złych rejestrów | `move v7, v6` (kolizja z oknem stosu) |

**Regresja potwierdzona testem na starym tłumaczu** (wygenerowane śmieci powyżej) —
testy te bugi faktycznie wykrywają.

**Dowód braku wpływu na APK:** stary (v1.0.5) i nowy (v1.0.6) tłumacz przetłumaczyły
43 pliki .class do **identycznego bajt w bajt smali** (`diff -rq` = brak różnic).
Jedyna różnica w APK vs v1.0.5: napis wersji `1.0.5` → `1.0.6` (żeby App-info
na telefonie jednoznacznie wskazało zainstalowany build).

---

## 3. Wyniki testów (2026-10-22, build v1.0.6, versionCode 7)

### REALNIE wykonane (z wynikiem):
| Test | Zakres | Wynik |
|------|--------|-------|
| **testy tłumacza** `tools/test_j2smali_map.py` (NOWE) | 23 testy złotego smali na syntetycznych klasach + 13 end-to-end ECJ→smali→dex→dexcheck/dexverify; pokrywają m.in. i2l/l2i/d2f/lshl/putfield-wide/dup_x1/dup2_x1/swap/fconst/aput/ACC_CONSTRUCTOR/sloty parametrów | **36/36 PASS** |
| testy JVM `android/tests/jvm/run_jvm_test.sh` | realne wykonanie logiki aplikacji (start, UI, Store, Settings, dp) | **23/23 PASS (8 sekcji)** |
| **dexverify.py** (własny weryfikator typów wg reguł ART) | wszystkie 148 metod classes.dex: rodzaje move vs typy, invoke vs prototypy, uninit/`<init>`, pola/zwroty/unopy | **148 metod, 0 błędów** |
| **dexcheck.py** | struktura dex + flagi ACC_CONSTRUCTOR + invoke/move-result/pola/zwroty | **148 code_item, 0 błędów** |
| apksigner (v1+v2+v3) | weryfikacja podpisu przy min-sdk 21, 26, 34 | **PASS ×3** |
| androguard | pełny pars APK/manifestu | **„successfully validated"**; vName 1.0.6, vCode 7, MAIN/LAUNCHER OK |
| diff starego i nowego tłumacza | 43 klasy produkcyjne | **identyczne** (fixy ≠ zmiana APK) |

### Tylko zaimplementowane / NIEUKOŃCZONE (realne przeszkody):
| Element | Status | Konkretna przeszkoda |
|---------|--------|---------------------|
| **Uruchomienie APK na fizycznym telefonie** | **NIEUKOŃCZONE** | brak dostępu do urządzenia w środowisku budowania (zero emulatora/Androida) — to JEDYNY nieodhaczony test; bez niego nie deklaruję „działa" |
| Uruchomienie Setup.exe / Portable na Windows 11 | **NIEUKOŃCZONE** | brak Windows/wine w środowisku (ten sam stan co wcześniej) |
| Testy UI na urządzeniu (dotyk, klawiatura, sieć LAN) | nie wykonalne tutaj | jw. |

---

## 4. Artefakt do pobrania (JEDEN plik — bez śmieci)

| Plik | Rozmiar | SHA-256 |
|------|---------|---------|
| **`WOJAN-106.apk`** (= `WOJAN-AI-FACTORY-Release.apk`, identyczne bajty) | 45 399 B | `2aec4a5ca43262f6b6ece68dd84aab047d2d3293f0861051364fde9bb4a13dfe` |

- wersja **1.0.6**, versionCode **7**, minSdk 21, targetSdk 34, podpis v1+v2+v3,
- pobranie: `https://github.com/janradzik86/mein/raw/arena/6fc88dc6-mein/WOJAN-AI-FACTORY/dist/WOJAN-106.apk`
- Setup.exe (3 793 301 B) i Portable.zip (1 299 427 B) — bez zmian względem v1.0.5.

## 5. Procedura testu na telefonie (krok po kroku)

1. **Odinstaluj** WOJAN AI FACTORY (nowy klucz dev = nowy podpis; aktualizacja „na"
   starą wersję nie przejdzie).
2. **Usuń WSZYSTKIE pobrane pliki APK** z menedżera pobierania (tam są śmieci
   i stare wersje — mylą się przez przycinane nazwy).
3. Pobierz **tylko** `WOJAN-106.apk` z linku powyżej.
4. Zainstaluj. Wejdź w **Info o aplikacji** — musi być **„Wersja 1.0.6"**.
   (Jeśli jest inna liczba — masz stary plik; usuń i pobierz ponownie.)
5. Uruchom. Oczekiwania:
   - **zielony pasek „Start OK — wersja 1.0.6"** → potem główne UI → **SUKCES**;
   - **czerwony panel** z nazwą wyjątku i śladem → **zrób zdjęcie** (to pełna diagnoza);
   - **okienko „przestaje działać"** → zdjęcie + napisz;
   - **cisza** (nic po tapnięciu) → napisz „cisza" → buduję izolowany APK
     diagnostyczny z samym paskiem startowym.
6. Po tecie **usuń pobrany plik APK** (miejsce na telefonie).

## 6. Status

Kod przeszedł audyt linia po linii na poziomie tłumacza, bajtkodu i pakietu;
wszystkie testy wykonalne poza telefonem są **zielone**. Zgodnie z zasadą
„nie twierdzę, że działa, dopóki nie sprawdzę" — **gotowość do użycia deklaruję
dopiero po Twoim teście na telefonie** (sekcja 5). To jedyny pozostały krok.
