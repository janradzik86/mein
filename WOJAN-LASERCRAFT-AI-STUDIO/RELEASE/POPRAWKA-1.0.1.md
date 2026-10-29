# POPRAWKA 1.0.1 — dlaczego APK 1.0.0 nie działał na telefonie

Data: 2026-10-29 · Dotyczy: `WOJAN-LaserCraft-AI-Android-Release.apk`

## Przyczyna (potwierdzona, nie domysł)

Plik `classes.dex` w APK 1.0.0 miał w nagłówku wersję formatu **036**:

```
magic: b'dex\n036\x00'
```

ART (środowisko uruchomieniowe Androida) **nie zna wersji 036 i celowo ją odrzuca**.
W źródłach AOSP, `libdexfile/dex/standard_dex_file.cc`, tablica dozwolonych wersji to:

```cpp
const uint8_t StandardDexFile::kDexMagicVersions[...] = {
    {'0','3','5','\0'},
    // Dex version 036 skipped because of an old dalvik bug on some versions of android where
    // dex files with that version number would erroneously be accepted and run.
    {'0','3','7','\0'},
    {'0','3','8','\0'},
    {'0','3','9','\0'},
    {'0','4','0','\0'},
    {'0','4','1','\0'},
};
```

Skutek: system nie potrafi otworzyć DEX-a, więc instalacja kończy się błędem
(`INSTALL_FAILED_DEXOPT` / „Aplikacja nie została zainstalowana”), a gdyby plik
znalazł się w systemie — aplikacja nie ma z czego uruchomić kodu.

Źródło błędu: używane w toolchainie `smali` (wersja 1.x, `smali.jar`, Built-Jdk 1.6.0_26)
wpisuje magic `036` **niezależnie od parametru `-a`** — sprawdzone eksperymentalnie
dla `-a 19 … -a 34`, zawsze `036`.

## Poprawka

1. Nowy krok budowania `tools/dexfix.py` — po asemblacji nadpisuje wersję na wynikającą
   z `minSdk` (24 → **037**, wersja wprowadzona w Androidzie 7.0 i akceptowana przez
   wszystkie nowsze wydania, w tym Androida 16) i przelicza `adler32` + `SHA-1`.
2. `tools/dexcheck.py` — lista dozwolonych wersji zgodna z ART (035/037/038/039/040/041),
   wersja 036 jest teraz **błędem blokującym build**, plus kontrola, czy plik nie używa
   instrukcji wymagających formatu ≥ 038 (`invoke-custom`, `const-method-handle`).
3. `tools/dexlink.py` (nowe) — sprawdza spójność odwołań: 150 klas, 6218 wywołań metod,
   każde wywołanie do klas aplikacji rozwiązuje się do istniejącej metody, brak duplikatów.
4. `tools/build_android.sh` — po podpisaniu odczytuje `classes.dex` **z gotowego APK**
   i przerywa build, jeśli wersja DEX jest nieobsługiwana.
5. `MainActivity` — ekran błędu startu: każdy wyjątek podczas `onCreate` oraz każdej
   zakładki jest przechwytywany, zapisywany do `files/crash.txt` i **pokazywany na ekranie**
   (z wersją systemu i modelem telefonu). Koniec z „aplikacja się zamyka i nic nie wiadomo”.
6. `versionName 1.0.1`, `versionCode 2` — instaluje się jako aktualizacja nad 1.0.0.

## Weryfikacja po poprawce

```
dexfix: wersja w pliku = 036, docelowa (api 24) = 037 → poprawiono
dexcheck: 566 code_item, 0 błędów
dexlink: 150 klas, 6218 wywołań metod sprawdzonych, 0 problemów
weryfikacja APK: classes.dex magic=b'dex\n037\x00' -> OK
apksigner verify: v2 = true, v3 = true, cert SHA-256 f60aa26d…
androguard: is_valid_APK() = True, pl.wojan.lasercraft, 1.0.1 (2), minSdk 24, targetSdk 36
```

## Czego nadal nie da się potwierdzić bez telefonu

Build nie był uruchamiany na fizycznym urządzeniu ani emulatorze (w środowisku budowania
nie ma Androida). Poprawka usuwa **udowodnioną** przyczynę (DEX 036) i dodaje ekran błędu,
który pokaże każdą dalszą usterkę. Jeśli po instalacji 1.0.1 pojawi się ekran
„błąd uruchamiania”, wystarczy zdjęcie ekranu albo plik `crash.txt` — to wystarczy do
naprawy.

## Instalacja

1. Odinstaluj 1.0.0 (albo zainstaluj nakładkowo — podpis jest ten sam).
2. Otwórz `WOJAN-LaserCraft-AI-Android-Release.apk`, zezwól na instalację z nieznanych źródeł.
3. Suma kontrolna: patrz `SHA256SUMS.txt`.
