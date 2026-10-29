# RAPORT TESTOW — WOJAN LaserCraft AI Studio 1.0.8

Data: 2026-10-29 · Wersja: 1.0.5 (versionCode 6) · Środowisko: kontener Linux
x86_64, 2 vCPU, 3 GB RAM, OpenJDK 25.0.2, Zig/clang (Windows x64), bez Androida.

Zasada tego raportu: **każdy wiersz PASS ma dowód** (polecenie + wynik albo plik w
repozytorium). Czego nie dało się uruchomić, jest oznaczone **NIEWERYFIKOWANE** —
nie jako PASS. Tabela na końcu zbiera status każdego wymaganego scenariusza.



## 0b. Przebieg 1.0.8 — silnik mowy whisper.cpp wbudowany (2026-10-29)

**Zrobione:** whisper.cpp 1.7.4 + ggml skompilowane do aplikacji Windows (`tools/build_whisper.sh`,
16 plików źródłowych, biblioteka statyczna 7,46 MB). Integracja: `core/stt.cpp` (WAV PCM →
resampling 16 kHz → mono → `whisper_full` z językiem „pl”), zakładka Mikrofon z przyciskiem
ROZPOZNAJ MOWE i widocznym statusem modelu. EXE urósł z 2,80 MB do **3,75 MB**.

**Sprawdzone wykonawczo:** kompilacja i linkowanie (Linux: `gcc/g++`, Windows: Zig/clang),
uruchomienie `stt::status()`, `stt::engine_info()` → „whisper.cpp 1.7.4 | AVX = 0 …”.
Ścieżka transkrypcji jest zaimplementowana i zwraca czytelny błąd, gdy brak wag.

**Nie sprawdzone wykonawczo:** sama transkrypcja realnego nagrania — nie ma czym.
W środowisku budowania nie da się pobrać pliku wag:

| Źródło | Wynik sprawdzenia |
|---|---|
| huggingface.co (oficjalne wagi) | host niedostępny |
| GitHub, 43 znalezione kopie modeli (`ggml-tiny/base`, `q5_1`) | wszystkie to wskaźniki Git LFS (131–134 B) |
| raw./media.githubusercontent.com | zablokowane (SSL) |
| PyPI | brak paczek z wagami Whispera |

Dlatego instalator zawiera katalog `modele` ze skryptem `pobierz_model.bat`, który pobiera
`ggml-base-q5_1.bin` (59,7 MB) na komputer użytkownika i sprawdza rozmiar. Aplikacja wykrywa
plik automatycznie; status jest pokazany wprost w interfejsie.

**Podpis APK:** build przeszedł na **oryginalny `apksig` Google** (uber-apk-signer 1.3.0 z PyPI):
`zipalign verified`, `signature verified [v2, v3]`, nowy certyfikat `5aac0b56…0e23d`.
Kontrola instalacyjna: 0 problemów blokujących; DEX 0/0/0; audyt API 0 klas nowszych.

**Nadal brak:** model językowy (LLM) — te same blokady pobierania wag; STT na Androidzie —
brak NDK/kompilatora ARM w środowisku; instalacja APK na telefonie — nieweryfikowana
(brak wirtualizacji i dostępu do obrazów systemu).

## 0a. Przebieg 1.0.6 — środowisko odtworzone od zera (2026-10-29, 06:00–06:15)

Środowisko zostało wyczyszczone (brak JDK, Ziga, aapt2, apktool, `android.jar`, ImGui,
**oraz klucza podpisywania**). Wszystko odtworzone: JDK i Zig z PyPI (`jdk4py`, `ziglang`),
aapt2 z wheelu PyPI, apktool z npm, `android.jar` (API 34) z GitHuba (blob `923bafcc…`),
ImGui 1.91.5 z codeload.github.com.

W tym środowisku **nie ma kompilatora Javy** (JDK to samo JRE; ECJ nieosiągalny —
Maven/Google zablokowane, repozytoria z `ecj.jar` zawierają wskaźniki Git LFS lub `.sha1`).
Konsekwencje, podane wprost:

| Element | 1.0.5 | 1.0.6 |
|---|---|---|
| Budowa APK | ECJ + j2smali (kompilacja źródeł Java) | **asemblacja smali** z DEX 1.0.5 (kod identyczny ze źródłami) + aapt2 |
| Podpis | `apksigner.jar` | **własna implementacja v1+v2** (`tools/apk_sign.py`), zweryfikowana androguardem |
| 41 testów JVM | wykonane (41 PASS) | **NIE WYKONANE** — brak kompilatora Javy |
| 24 kontrole E2E (CLI) | wykonane | **wykonane ponownie: 24 PASS** |

Znaleziony i naprawiony **realny błąd w podpisywaniu v2**: sekwencja podpisów była owinięta
podwójnym polem długości, czyli format niezgodny ze specyfikacją — Android odrzuciłby taki
APK mimo poprawnych obliczeń kryptograficznych. Ten sam błąd zawiera `tools/apksig/apksig.py`
(plik z innego projektu w tym repozytorium, nigdy nieużyty do wydania). Po naprawie podpis
potwierdzają dwie niezależne implementacje: własny weryfikator i androguard (`v1=True v2=True`).

Nowy certyfikat podpisywania (stary klucz przepadł razem ze środowiskiem — nieodwracalnie):

```
CN=WOJAN AI FACTORY Release Key, O=WOJAN AI FACTORY, C=PL
SHA-256 F3:E5:AF:82:F8:84:D6:45:AD:C3:12:44:E9:08:A9:62:E3:57:F4:22:43:B0:48:DA:16:CB:E4:04:75:FD:D7:EE
RSA 2048, SHA256withRSA, ważny do 2036-10-26
```

Skutek: przed instalacją 1.0.6 trzeba odinstalować poprzednią wersję (inny podpis).
Kolejne wydania z tego klucza aktualizują się już normalnie.

## 0. Czego w tym środowisku nie ma (i dlaczego)

Próba uruchomienia emulatora Androida została podjęta i **nie jest możliwa** w tym
kontenerze. Sprawdzenie (wynik dosłowny):

```
$ ls /dev/kvm                      → No such file or directory
$ grep -E 'vmx|svm' /proc/cpuinfo  → brak (brak wirtualizacji sprzętowej)
$ nproc; free -g                   → 2 CPU; 3 GB RAM
$ command -v docker podman qemu-system-x86_64 sdkmanager adb → BRAK
$ curl https://dl.google.com/      → SSL_ERROR_SYSCALL (host zablokowany w sandboxie)
```

Bez KVM, bez Dockera i bez dostępu do `dl.google.com` (jedynego źródła obrazów systemu
Android i emulatora) nie da się zbootować Androida 16/17. Dlatego instalacja APK na
urzeczywistym systemie **pozostaje nieweryfikowana wykonawczo** — zamiast tego APK
przechodzi pełny zestaw kontroli statycznych opisanych w pkt 3, obejmujących wszystkie
udokumentowane warunki instalacji Androida 11–17.

Nie ma też Wine, więc instalatora Windows i EXE nie da się uruchomić w tym środowisku
(`command -v wine` → BRAK). Weryfikacja Windows jest strukturalna (pkt 5).

## 1. Testy jednostkowe silnika na JVM — 41/41 PASS (przebieg 1.0.5, patrz 0a)

Polecenie: `bash tools/test_engine.sh` · dowód: `tests/out/raport_jvm.txt`
(39 825 B, data 2026-10-29 04:39). Ten sam kod, który trafia do APK (klasy `Core`,
`Qr`, `Gen`, `Draw`, `Pdf`, `Img`, `Store`, `PathProvider`), uruchomiony na JVM
z podstawionymi klasami `android.graphics` i `org.json`.

| Zakres | Testy | Wynik |
|---|---|---|
| Analiza promptu i jednostek | T01–T02 | PASS (8/8 przypadków, 20×15 cm → 200×150 mm) |
| QR: kodowanie, dekodowanie, wersje, pojemność | T03–T07 | PASS (20/20 dekodowań, 5 ładunków × 4 poziomy) |
| Przykłady i warstwy | T08–T10 | PASS (24 projekty, 114 obiektów, 0 błędów, 0 niezgodności warstw) |
| Eksport SVG/DXF/PDF | T11–T14 | PASS (mm, viewBox, warstwy, $INSUNITS=4, xref, znaczniki zbilansowane) |
| Walidacja przed laserem | T15–T19 | PASS (PVC blokowane, cięcie na gotowym przedmiocie = błąd, brak konturu = błąd) |
| Generator, JSON, wektoryzacja | T20–T24 | PASS (wlcproj/1, Douglas-Peucker 101→19 punktów) |
| Kalkulator, determinizm, warianty | T25–T28 | PASS (koszt < cena < brutto; ten sam prompt → identyczny projekt) |
| Polskie znaki, korekta tekstu, bank napisów | T29–T31 | PASS („ZAŻÓŁĆ GĘŚLĄ JAŹŃ”, 10 propozycji) |
| G-code i blokady | T32–T33 | PASS (GRBL 61 552 B; Ruida → eksport zablokowany) |
| Wydajność, pakiet produkcyjny | T34–T35 | PASS (50 projektów w 128 ms; 13/13 plików pakietu) |
| Profile LightBurn, parametry, motywy | T36–T38 | PASS (kerf z ustawień użytkownika) |
| Napisy z promptu, zawieszka, produkt okrągły | T39–T41 | PASS (otwór w materiale jako CUT_INNER) |

Suma: **41 PASS, 0 FAIL.**

## 2. Scenariusze E2E A–M na prawdziwym silniku (CLI) — 24/24 PASS

Polecenie: `./build/wojan-cli selftest` · dowód: `tests/out/raport_selftest.txt`
oraz 28 plików wynikowych w `data/selftest/` (SVG, DXF, PNG, G-code, projekty, raporty
walidacji — każdy plik wygenerowany w tym przebiegu, nie kopiowany).

| Scenariusz | Wynik (dosłownie z przebiegu) | Status |
|---|---|---|
| A. Tabliczka | obiekty=19, kontur cięcia=1 (zamknięty), SVG mm=1, błędy=0 | PASS |
| B. Piersiówka (tylko grawer) | warstwy cięcia=0, grupa CUT_OUTER w SVG=0, G-code bez cięcia | PASS |
| C. Zdjęcie | 240×180 RGB → szarość → dithering, 32 953 ciemnych pikseli, obiekt obrazu w projekcie | PASS |
| D. Mikrofon (transkrypt) | napis „DZIADEK RZĄDZI”, 180 mm, 10 obiektów (ASR: patrz pkt 6) | PASS (bez ASR) |
| E. Edycja + undo | skala 30,7→36,9 mm (+20%), obiekty 9→9, undo=1 | PASS |
| F. QR | wersja 3, poziom M, 29 modułów, maska 6, ścieżka 13 729 znaków | PASS |
| G. DXF | $INSUNITS mm, 1 warstwa, 1 LWPOLYLINE, brak rastra, 98 411 B | PASS |
| H. LaserGRBL | PNG 2000×1500 (10 px/mm), G-code 465 619 B, M5 na końcu, bez G28 | PASS |
| I. Offline | generowanie i eksport bez sieci (silnik nie ma klienta HTTP) | PASS |
| K. Pamięć | 24 generowania: RSS 16,6 MB → 16,6 MB (wzrost 0,0 MB) | PASS |
| L. Polskie znaki | wszystkie glify ĄĆĘŁŃÓŚŹŻ obecne w SVG i DXF | PASS |
| M. Zgodność SVG/DXF | SVG 120×80 mm=1, 3 warstwy, DXF EXTMAX=1 | PASS |

## 3. APK — warunki instalacji (Android 11–17)

Narzędzie: `tools/apk_installcheck.py` (nowe w 1.0.5, uruchamiane automatycznie na końcu
builda). Pełny wynik: `RELEASE/KONTROLA-INSTALACJI.txt`.

```
package pl.wojan.lasercraft · wersja 1.0.5 (6) · minSdk 24 · targetSdk 36
[OK] 20 wpisów ZIP, 0 duplikatów, brak ścieżek zip-slip
[OK] aapt2 dump badging — manifest i zasoby parsują się (oficjalny parser AOSP)
[OK] activity startowa: pl.wojan.lasercraft.MainActivity
[OK] etykieta: WOJAN LaserCraft AI · ikona rozwiązywana z zasobów
[OK] minSdk 24 · targetSdk 36 (Android 14+ blokuje targetSdk < 23)
[OK] resources.arsc STORED, offset 44 (4 B OK)   ← warunek Androida 11+
[OK] classes.dex: magic dex\n037 — wersja akceptowana przez ART
[OK] brak bibliotek natywnych (reguła 16 KB stron nie dotyczy)
[OK] podpis: v1=True v2=True v3=True (certyfikat SHA-256 f60aa26d…, ważny do 2036)
WYNIK: 0 problemów blokujących instalację
```

Dowód, że manifest jest czytelny dla AOSP (zrzut AXML z 1.0.4, struktura identyczna):
`<manifest>` → `package`, `versionCode`, `versionName`; `<uses-sdk>` 24/36;
`<application>` z `label`/`icon` z `resources.arsc`; `<activity android:name="…MainActivity" android:exported="true">` z `MAIN`/`LAUNCHER`; wszystkie atrybuty mają poprawne
`attrId` (np. `name` = `0x01010003`).

## 4. APK — audyt API i DEX

* `tools/apk_api_audit.py`: 150 klas własnych, 53 klas `android.*` + 50 klas Java/JSON,
  1032 wywołania. **0 klas nowszych niż minSdk 24**, 0 niedostępnych nadtypów,
  0 klas do ręcznej weryfikacji.
* `tools/dexcheck.py`: 566 `code_item`, 0 błędów strukturalnych.
* `tools/dexlink.py`: 150 klas, 6218 wywołań sprawdzonych wobec `android.jar`,
  0 problemów (żadna metoda platformy nie jest nieistniejąca).
* `tools/dexverify.py`: 150 klas, 6218 wywołań, 0 naruszeń reguł weryfikatora ART
  (m.in. `invoke-direct` tylko do `<init>`/metod prywatnych, `invoke-super` dla `super.*`).

## 5. Windows — budowa i instalator

* `bash tools/build_core.sh && bash tools/build_windows.sh` → `WOJAN-LaserCraft-AI.exe`
  2 804 224 B, PE32+ x86-64, konsola Windows. Po dodaniu `-Wl,/Brepro` przebudowa
  od zera dała **bit w bit ten sam plik** (SHA-256 zgodny).
* `python3 tools/make_installer.py --build` → Setup 4,7 MB: 38 wpisów (program, 10 czcionek,
  26 projektów `.wlcproj`, dokumentacja), nagłówek PE w kopercie poprawiony, 0 rozbieżności
  między archiwum w Setup.exe a plikami źródłowymi.
* **NIEWERYFIKOWANE**: uruchomienie EXE i kreatora instalacji (brak Wine/Windows w kontenerze).

## 6. Świadomie nieweryfikowane / znane braki

| Element | Status | Dlaczego |
|---|---|---|
| Instalacja APK na fizycznym telefonie / emulatorze | **NIEWERYFIKOWANE** | brak KVM, Dockera i dostępu do obrazów systemu (pkt 0) |
| Uruchomienie GUI Android (12 zakładek) | **NIEWERYFIKOWANE** | to samo |
| Uruchomienie EXE / instalatora Windows | **NIEWERYFIKOWANE** | brak Wine/Windows |
| Lokalne rozpoznawanie mowy (STT) | **NIE ZAIMPLEMENTOWANE** | nagranie WAV + wpisany tekst; whisper.cpp poza wydaniem (pkt 2 dokumentu MODELE-I-LICENCJE.md) |
| Ikona aplikacji Android | ograniczenie | APK nie zawiera `resources.arsc` z ikoną — używana jest ikona domyślna systemu |
| Sterowanie laserem / G-code poza GRBL | celowo wyłączone | wymóg: żadnego G-code dla nieznanej maszyny |
| Podpis Authenticode instalatora | brak | brak certyfikatu komercyjnego (decyzja właściciela) |

## 7. Historia blokad instalacji (co konkretnie było nie tak)

| Wersja | Defekt wykryty | Dowód | Naprawa |
|---|---|---|---|
| 1.0.0/1.0.1 | DEX w wersji `dex\n036`, której ART nie akceptuje (dozwolone: 035, 037–041) | tabela `kDexMagicVersions` w ART (`standard_dex_file.cc`) | wersja DEX 037 |
| 1.0.2/1.0.3 | `resources.arsc` spakowany deflate i bez wyrównania do 4 B → `INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED` | zrzut ZIP: `resources.arsc` method=8, offset 114100 | 1.0.4: STORED + wyrównanie 4 B (`tools/pack_apk.py`) |
| 1.0.5 | brak nowego defektu instalacyjnego; dodano automatyczną kontrolę instalacyjną i audyt API do builda, wymuszono i potwierdzono podpis v1+v2+v3 | `RELEASE/KONTROLA-INSTALACJI.txt` | — |

## 8. Jak powtórzyć te testy

```bash
bash tools/test_engine.sh                     # 41 testów JVM
bash tools/build_cli.sh && ./build/wojan-cli selftest   # E2E A–M
bash tools/build_android.sh                   # APK + kontrole instalacyjne (wymaga WOJAN_KEYSTORE_PASS)
bash tools/build_core.sh && bash tools/build_windows.sh && python3 tools/make_installer.py --build
python3 tools/apk_installcheck.py RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk
python3 tools/apk_api_audit.py RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk --min-sdk 24 --pelen
```

Wersja raportu: 1.0.8, 2026-10-29.
