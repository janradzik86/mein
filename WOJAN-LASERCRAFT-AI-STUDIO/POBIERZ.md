# WOJAN LaserCraft AI Studio — pobierz (wersja 1.0.9)

## UWAGA: TRZY różne programy o podobnej nazwie

Do telefonu trafiły **dwa inne programy** o zbliżonej nazwie — żaden nie jest tym produktem:

1. **`wojan-lasercraft-lite-shell-*.apk` (3,0 MB)** — powłoka WebView/Capacitor, pakiet
   `pl.wojan.hobby.lasercraft`; wywala się na Androidzie 14+.
2. **„Wojan Studio”** — strona WWW w WebView otwierająca `wojan-studio-ai-e4wd.onrender.com`;
   pokazuje „Strona nie wyświetliła się” (serwer zwraca HTML zamiast plików JS/CSS).

Pełne rozróżnienie (rozmiar, pakiet, nazwa, zachowanie):
[`RELEASE/JAK-ROZPOZNAC-APLIKACJE.md`](RELEASE/JAK-ROZPOZNAC-APLIKACJE.md).

Do telefonu trafiły pliki **`wojan-lasercraft-lite-shell-*.apk` (3,0 MB)** — to **nie jest**
ten program, tylko powłoka WebView/Capacitor z innego projektu (pakiet
`pl.wojan.hobby.lasercraft`). Wywala się na Androidzie 14+ wyjątkiem
`SecurityException: One of RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED…`
w `com.capacitorjs.plugins.share.SharePlugin`. Pełna diagnoza:
[`RELEASE/DIAGNOZA-URUCHOMIENIA.md`](RELEASE/DIAGNOZA-URUCHOMIENIA.md).

**Nasza aplikacja** (rozpoznasz po tym):

| | nasza | tamta powłoka |
|---|---|---|
| plik | `WOJAN-LaserCraft-AI-Android-Release.apk` | `wojan-lasercraft-lite-shell-*.apk` |
| rozmiar | **832 990 B (0,83 MB)** | 3,0 MB |
| pakiet | `pl.wojan.lasercraft` | `pl.wojan.hobby.lasercraft` |
| nazwa na liście aplikacji | **WOJAN LaserCraft AI** | WOJAN LaserCraft |
| WebView / Capacitor | **brak** (własny interfejs) | cała aplikacja to WebView |
| ładuje adres internetowy | **nigdy** | tak |

Strony wydań na GitHubie nie mają załączników (serwer, z którego buduję, nie ma dostępu
do `uploads.github.com`), dlatego plik leży **w repozytorium**, a link jest przypięty do
commita — działa i nie wygasa:

* **APK (0,83 MB, Android 8–17, arm64 i x86):**
  https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk
  SHA-256: `417cbb8fd8b2e60645e2a84882e88e14f64a9f297c0fb329013da83948c818a8`
* Instalator Windows (65,7 MB) — link i instrukcja niżej.

## Windows 11 x64

1. Pobierz **WOJAN-LaserCraft-AI-Windows-Setup.exe** (65,7 MB — w środku jest model).
2. Uruchom. SmartScreen: „Więcej informacji” → „Uruchom mimo to” (brak certyfikatu
   Authenticode — to decyzja o koszcie, nie błąd).
3. Instalacja bez administratora: `%LOCALAPPDATA%\WOJAN LaserCraft AI`.
4. Zakładka **Mikrofon i prompt** → NAGRAJ → **ROZPOZNAJ MOWE (offline)**.
   Na górze powinno być zielone: „model: ggml-base-q5_1.bin (56.9 MB)”.

## Test 30-sekundowy (jeśli aplikacja nadal się zamyka)

* **WOJAN-PROBE-test.apk (29 KB)** — https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-PROBE-test.apk
  pokazuje jeden napis „DZIAŁA” (pakiet `pl.wojan.probe`, ikona „WOJAN PROBE”).
  Jeśli ten plik działa, a LaserCraft nie — problem jest w kodzie aplikacji, nie w podpisie.
  Szczegóły i instrukcja: `RELEASE/TEST-URUCHOMIENIA.md`.

## Android — instalacja krok po kroku

1. Odinstaluj **starą powłokę**: Ustawienia → Aplikacje → znajdź „WOJAN LaserCraft”
   (bez „AI”) → Odinstaluj. Jeśli zostanie, ikony nadal będą się mylić.
2. Pobierz plik z linku wyżej (0,83 MB). Po pobraniu sprawdź rozmiar w Menedżerze plików:
   **musi mieć mniej niż 1 MB (0,83 MB)**. Plik 3,0 MB to nie my.
3. **Odinstaluj starą wersję** (nowy klucz podpisujący — inaczej instalacja odmówi),
   potem otwórz plik → „Zainstaluj mimo ostrzeżenia” (Android ostrzega o instalacji spoza sklepu;
   podpis jest nasz, SHA-256 certyfikatu `5aac0b56…0e23d`).
4. Po instalacji na liście aplikacji pojawi się **WOJAN LaserCraft AI** — uruchom tę ikonę.
5. Sprawdzenie tożsamości: Ustawienia → Aplikacje → WOJAN LaserCraft AI → O aplikacji:
   pakiet musi brzmieć `pl.wojan.lasercraft`.

Jeśli aplikacja **nie uruchomi się**, wyślij zrzut ekranu z pełnym logiem — w nim widać
pakiet i numer linii, a to wystarcza do wskazania przyczyny bez zgadywania.

**Czego na telefonie nie ma (napisane wprost):** silnika whisper — zakładka Mikrofon
nagrywa dźwięk i prosi o wpisanie tekstu; model neuronowy rysowania jest na razie
w wersji Windows (port na Androida jest osobnym zadaniem, patrz `RAPORT-TESTOW.md`).

## Dowody, nie obietnice

| Test | Wynik |
|---|---|
| transkrypcja EN (`jfk.wav`) | „And so my fellow Americans, ask not what your country can do for you, ask what you can do for your country.” — 2,68 s |
| transkrypcja PL (espeak-ng, 8,6 s) | polski tekst z diakrytykami rozpoznany, 3,70 s |
| filtry mel vs wzorzec whisper.cpp | różnica maksymalna 0.0 |
| model po kwantyzacji q5_1 | transkrypcja identyczna, 59 707 642 B |
| podpis APK | apksig Google: v2+v3, zipalign verified |

## Pliki

| Plik | Rozmiar | SHA-256 |
|---|---|---|
| [WOJAN-LaserCraft-AI-Windows-Setup.exe](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Windows-Setup.exe) | 65715235 B | `6745f7789709ef4c16d346b2eb88197d6319ad2ec247541f86297ccc959a844f` |
| [WOJAN-LaserCraft-AI-Android-Release.apk](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk) | 832990 B | `417cbb8fd8b2e60645e2a84882e88e14f64a9f297c0fb329013da83948c818a8` |
| [SHA256SUMS.txt](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/SHA256SUMS.txt) | 210 B | `342e6b1c5b19f303a78fa566bb42fcf24650db8d7531a7fd6e97e1f0b4f51f0b` |
| [INSTRUKCJA.pdf](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/INSTRUKCJA.pdf) | 70313 B | `0e37e1192cfe4427fb1ea4e331cf772d96d2763d1644c6e95951ba0d0180a8dc` |
| [RAPORT-TESTOW.md](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/RAPORT-TESTOW.md) | 15342 B | `d552f868d839059b2ecca88ec6827b4b373b649ddf158ff7ebc81822137f48ed` |
| [MODELE-I-LICENCJE.md](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/MODELE-I-LICENCJE.md) | 8479 B | `5d775ac9c3c82168f3d150f61e999d7d431a3241850f29bed9c52e76809fa74d` |
| [PRZYKLADOWE-PROJEKTY.zip](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/PRZYKLADOWE-PROJEKTY.zip) | 564614 B | `3701d96f9b5d4ee6d8e506908edb68af7e22a1f2e86f516756e2d51e651a6066` |
| [KOD-ZRODLOWY.zip](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/KOD-ZRODLOWY.zip) | 8401684 B | `31a37850f5d621d34cb0e6486ee954f99d7591a46fa16b4d46ab455b0b34a7a9` |
| [POPRAWKA-1.1.0-ANDROID.md](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/POPRAWKA-1.1.0-ANDROID.md) | 3657 B | `12e87fed0a77d23cc673d0dba61b62abadeb8cbfa7cf40ec226a0921b1e8af4a` |
| [DIAGNOZA-URUCHOMIENIA.md](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/DIAGNOZA-URUCHOMIENIA.md) | 7416 B | `d2a34ef12981ba9c12101b0fc9b1eec133c6985050d0c4561173d5d01c844494` |
| [JAK-ROZPOZNAC-APLIKACJE.md](https://github.com/janradzik86/mein/raw/6a30bd1/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/JAK-ROZPOZNAC-APLIKACJE.md) | 4199 B | `298ab496b6573fec5b7ff37ee2598caee46750d6c34ce5e45ead841ae914f8b4` |

## Czego nadal nie ma (mówię wprost)

* **LLM do generowania opisów** — „Generator AI” to nadal silnik proceduralny + parser
  polszczyzny. Wagi GGUF (`gemma3-270m`, ~238 MB) są dostępne na PyPI, ale to osobna
  decyzja o rozmiarze instalatora (materiał na 1.1.0).
* **Nie uruchomiłem EXE ani Setup.exe na Windows** — brak Windows w środowisku budowania.
  Sprawdzone: kompilacja, linkowanie, koperta instalatora (48 wpisów, sumy zgodne),
  wczytanie modelu tym samym kodem na Linuksie.
* **Nie zainstalowałem APK na telefonie** — brak wirtualizacji/emulatora.
* **STT na Androidzie** — brak silnika whisper dla ARM (brak NDK w środowisku).
