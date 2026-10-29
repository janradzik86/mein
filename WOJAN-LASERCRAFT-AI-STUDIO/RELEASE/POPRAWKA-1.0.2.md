# POPRAWKA 1.0.2 — Android i Windows

Data: 2026-10-29 · Dotyczy: `WOJAN-LaserCraft-AI-Android-Release.apk` i `WOJAN-LaserCraft-AI-Windows-Setup.exe`

---

## 1. Windows: „EXE nie generuje obrazu wzoru" — NAPRAWIONE

### Przyczyna (potwierdzona testem na tym samym kodzie silnika)
Akcja silnika `render` (podgląd wzoru) **nie ustawiała pola `"ok": true`** w odpowiedzi JSON.
Interfejs Windows sprawdzał:

```cpp
if (!r["ok"].boolean(false) || !r.has("pngBase64")) return;   // desktop/src/main.cpp
```

…czyli po każdej próbie narysowania podglądu **po cichu wychodził**, mimo że silnik
poprawnie wygenerował obraz. Ten sam brak `ok` dotyczył akcji `calculator`, `status`,
`catalog` i `trends` — dlatego puste były też kalkulator, katalog i diagnostyka.

Dowód z uruchomionego silnika (przed poprawką):

```
[render]   ... "bytes":57398, "height":1200, "pngBase64":"iVBORw0KGgo..."   ok=0
pngDecode=1 rozmiar=1600x1200   pikseli nie-bialych=58815 z 1920000
```

Czyli obraz **był** generowany (57 398 B PNG, 1600×1200 px, 58 815 ciemnych pikseli) —
 brakowało tylko flagi, którą UI wymagało.

### Co zmieniono
1. `core/core_api.cpp` — `Engine::handle()` jest teraz opakowaniem: bierze wynik z
   `Engine::dispatch()` i **gwarantuje** pole `ok` (`ok=false`, gdy jest `error`).
2. Jawne `ok=true` także w `renderJson()` i `calculateCosts()`.
3. `desktop/src/main.cpp` — `updatePreview()` nie wymaga już `ok`; sprawdza obecność
   obrazu i **pokazuje komunikat błędu**, jeśli podgląd się nie udał (koniec z ciszą).
4. Wersja podniesiona do 1.0.2 (interfejs, silnik, `status.version`).

### Po poprawce (ten sam test)
```
render ok=1 has png=1 bytes=57398      pngDecode=1 1600x1200
status ok=1   catalog ok=1   calculator ok=1   trends ok=1
```

---

## 2. Android: APK zbudowany od nowa standardowym łańcuchem — 1.0.2

APK 1.0.0 i 1.0.1 miały dwie niestandardowe części: manifest składany własnym
narzędziem (`tools/axmlbuild.py`) i **brak katalogu `res/` oraz `resources.arsc`**.
W 1.0.2 obie rzeczy robi oficjalne narzędzie:

| etap | 1.0.1 | 1.0.2 |
|---|---|---|
| manifest + zasoby | własny AXML, brak `res/`, brak `resources.arsc` | **aapt2** (`aapt2 link`): prawdziwy `resources.arsc`, `res/mipmap-*/ic_launcher.png`, `@style/AppTheme` |
| DEX | smali 1.4 (pisał nieistniejącą wersję 036, łatana na 037) | **apktool 2.9.3 + dexlib2 3.x** — DEX 037 z prawdziwego asemblera |
| podpis | v2 + v3 | **v1 + v2 + v3** (v1 zweryfikowany: `true`) |
| ikona | brak (domyślna systemowa) | własna ikona w 5 gęstościach |
| uprawnienia | INTERNET, RECORD_AUDIO | INTERNET, RECORD_AUDIO (bez zbędnych) |

Kontrole po zbudowaniu (wbudowane w `tools/build_android.sh`, build przerywa się przy błędzie):
```
APK: 17 wpisów, classes.dex magic=b'dex\n037\x00' -> OK
dexcheck: 566 code_item, 0 błędów
dexlink: 150 klas, 6218 wywołań metod sprawdzonych, 0 problemów
apksigner: v1=true, v2=true, v3=true
androguard: is_valid_APK=True, pl.wojan.lasercraft 1.0.2 (3), minSdk 24, targetSdk 36
            label z resources.arsc = "WOJAN LaserCraft AI", ikona res/mipmap-xxxhdpi-v4/ic_launcher.png
```

Dodatkowo w kodzie aplikacji (od 1.0.1) jest **ekran błędu startu**: każdy wyjątek
z `onCreate` i z każdej zakładki jest pokazywany na ekranie i zapisywany do `files/crash.txt`.

### Czego nadal nie da się potwierdzić bez urządzenia
W środowisku budowania nie ma Androida ani telefonu, więc APK **nie został uruchomiony
na sprzęcie**. Jeśli 1.0.2 nadal nie wystartuje, aplikacja pokaże ekran z treścią błędu —
wystarczy zdjęcie. Jeżeli instalacja zostanie odrzucona przez system, potrzebny jest
dosłowny komunikat (np. „Aplikacja nie została zainstalowana", „Wystąpił problem
z analizą pakietu", `INSTALL_FAILED_…`).

---

## 3. Wersje i sumy kontrolne

| plik | rozmiar | SHA-256 |
|---|---|---|
| `WOJAN-LaserCraft-AI-Android-Release.apk` | patrz `SHA256SUMS.txt` | patrz `SHA256SUMS.txt` |
| `WOJAN-LaserCraft-AI-Windows-Setup.exe` | patrz `SHA256SUMS.txt` | patrz `SHA256SUMS.txt` |
| `WOJAN-LaserCraft-AI.exe` | patrz `SHA256SUMS.txt` | patrz `SHA256SUMS.txt` |
