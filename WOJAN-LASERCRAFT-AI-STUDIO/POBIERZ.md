# WOJAN LaserCraft AI Studio 1.0.9 — pobierz

**MODEL JEST W ŚRODKU.** Instalator zawiera wagi Whisper base (`modele\ggml-base-q5_1.bin`,
59,7 MB) i wkompilowany silnik whisper.cpp 1.7.4 — rozpoznawanie mowy po polsku działa
od razu, offline, bez kluczy API. Sprawdzone wykonawczo (szczegóły niżej).

Linki przypięte do commita [`bf323c0`](https://github.com/janradzik86/mein/commit/bf323c0ca7f25de05a5b5aac60f3bb5ca446a14c) —
trwałe, nie wygasają.

## Windows 11 x64

1. Pobierz **WOJAN-LaserCraft-AI-Windows-Setup.exe** (65,7 MB — w środku jest model).
2. Uruchom. SmartScreen: „Więcej informacji” → „Uruchom mimo to” (brak certyfikatu
   Authenticode — to decyzja o koszcie, nie błąd).
3. Instalacja bez administratora: `%LOCALAPPDATA%\WOJAN LaserCraft AI`.
4. Zakładka **Mikrofon i prompt** → NAGRAJ → **ROZPOZNAJ MOWE (offline)**.
   Na górze powinno być zielone: „model: ggml-base-q5_1.bin (56.9 MB)”.

## Android

**WOJAN-LaserCraft-AI-Android-Release.apk** (829 kB). Odinstaluj poprzednią wersję
(certyfikat podpisu: SHA-256 `5aac0b56…0e23d`). APK nie ma silnika whisper — na telefonie
zakładka Mikrofon nagrywa dźwięk i prosi o wpisanie tekstu; to jest napisane wprost.

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
| [WOJAN-LaserCraft-AI-Windows-Setup.exe](https://github.com/janradzik86/mein/raw/bf323c0ca7f25de05a5b5aac60f3bb5ca446a14c/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Windows-Setup.exe) | 65 715 235 B | `6745f7789709ef4c16d346b2eb88197d6319ad2ec247541f86297ccc959a844f` |
| [WOJAN-LaserCraft-AI-Android-Release.apk](https://github.com/janradzik86/mein/raw/bf323c0ca7f25de05a5b5aac60f3bb5ca446a14c/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk) | 828 894 B | `e793bf65faf8b7fe7a85a327cdab95491504a661fafb46ebeebe495d9bdf2821` |
| [SHA256SUMS.txt](https://github.com/janradzik86/mein/raw/bf323c0ca7f25de05a5b5aac60f3bb5ca446a14c/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/SHA256SUMS.txt) | 555 B | `ca3da0febd4b0fcdee05ba4592c62d065cc7d0bc59c5e68e347f57a84f50cd0b` |
| [INSTRUKCJA.pdf](https://github.com/janradzik86/mein/raw/bf323c0ca7f25de05a5b5aac60f3bb5ca446a14c/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/INSTRUKCJA.pdf) | 70 313 B | `0e37e1192cfe4427fb1ea4e331cf772d96d2763d1644c6e95951ba0d0180a8dc` |
| [RAPORT-TESTOW.md](https://github.com/janradzik86/mein/raw/bf323c0ca7f25de05a5b5aac60f3bb5ca446a14c/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/RAPORT-TESTOW.md) | 15 342 B | `d552f868d839059b2ecca88ec6827b4b373b649ddf158ff7ebc81822137f48ed` |
| [MODELE-I-LICENCJE.md](https://github.com/janradzik86/mein/raw/bf323c0ca7f25de05a5b5aac60f3bb5ca446a14c/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/MODELE-I-LICENCJE.md) | 8 479 B | `5d775ac9c3c82168f3d150f61e999d7d431a3241850f29bed9c52e76809fa74d` |
| [PRZYKLADOWE-PROJEKTY.zip](https://github.com/janradzik86/mein/raw/bf323c0ca7f25de05a5b5aac60f3bb5ca446a14c/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/PRZYKLADOWE-PROJEKTY.zip) | 564 614 B | `3701d96f9b5d4ee6d8e506908edb68af7e22a1f2e86f516756e2d51e651a6066` |
| [KOD-ZRODLOWY.zip](https://github.com/janradzik86/mein/raw/bf323c0ca7f25de05a5b5aac60f3bb5ca446a14c/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/KOD-ZRODLOWY.zip) | 8 401 684 B | `31a37850f5d621d34cb0e6486ee954f99d7591a46fa16b4d46ab455b0b34a7a9` |
| [POPRAWKA-1.0.9.md](https://github.com/janradzik86/mein/raw/bf323c0ca7f25de05a5b5aac60f3bb5ca446a14c/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/POPRAWKA-1.0.9.md) | 4 361 B | `e5ee55de45efc66d891ac9b7aa1a7d660d7ef99ce3ca3af870db737f57154c88` |

## Czego nadal nie ma (mówię wprost)

* **LLM do generowania opisów** — „Generator AI” to nadal silnik proceduralny + parser
  polszczyzny. Wagi GGUF (`gemma3-270m`, ~238 MB) są dostępne na PyPI, ale to osobna
  decyzja o rozmiarze instalatora (materiał na 1.1.0).
* **Nie uruchomiłem EXE ani Setup.exe na Windows** — brak Windows w środowisku budowania.
  Sprawdzone: kompilacja, linkowanie, koperta instalatora (48 wpisów, sumy zgodne),
  wczytanie modelu tym samym kodem na Linuksie.
* **Nie zainstalowałem APK na telefonie** — brak wirtualizacji/emulatora.
* **STT na Androidzie** — brak silnika whisper dla ARM (brak NDK w środowisku).
