# POBIERZ — WOJAN LaserCraft AI Studio 1.0.6

**Wersja 1.0.6 (29.10.2026).** Linki przypięte do commita `fc7e7b35e48f933ec55da74a76751ea9599a4815` — działają bez logowania.

## ⬇️ POBIERANIE — kliknij link

| Co | Plik | Rozmiar | SHA-256 (skrót) |
|---|---|---|---|
| Android 7.0+ (nowy podpis 1.0.6) | [WOJAN-LaserCraft-AI-Android-Release.apk](https://github.com/janradzik86/mein/raw/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk) | 805 kB | `000aee269e4c4ecd…` |
| Windows 11 x64 — instalator | [WOJAN-LaserCraft-AI-Windows-Setup.exe](https://github.com/janradzik86/mein/raw/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Windows-Setup.exe) | 4,8 MB | `b1d999d686c30e43…` |
| Windows 11 x64 — sam program | [WOJAN-LaserCraft-AI.exe](https://github.com/janradzik86/mein/raw/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI.exe) | 2,7 MB | `964736d18640db68…` |
| instrukcja (PDF) | [INSTRUKCJA.pdf](https://github.com/janradzik86/mein/raw/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/INSTRUKCJA.pdf) | 69 kB | `a225fefd5613f1fe…` |
| raport testów — co sprawdzone, co nie | [RAPORT-TESTOW.md](https://github.com/janradzik86/mein/blob/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/RAPORT-TESTOW.md) | 11 kB | |
| co realnie liczy projekty | [MODELE-I-LICENCJE.md](https://github.com/janradzik86/mein/blob/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/MODELE-I-LICENCJE.md) | 6,1 kB | |
| wynik kontroli instalacyjnej APK | [KONTROLA-INSTALACJI.txt](https://github.com/janradzik86/mein/blob/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/KONTROLA-INSTALACJI.txt) | 1,7 kB | |
| co zmieniono w 1.0.6 | [POPRAWKA-1.0.6.md](https://github.com/janradzik86/mein/blob/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/POPRAWKA-1.0.6.md) | 6,0 kB | |
| 26 projektów przykładowych | [PRZYKLADOWE-PROJEKTY.zip](https://github.com/janradzik86/mein/raw/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/PRZYKLADOWE-PROJEKTY.zip) | 551 kB | `3701d96f9b5d4ee6…` |
| pełny kod źródłowy | [KOD-ZRODLOWY.zip](https://github.com/janradzik86/mein/raw/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/KOD-ZRODLOWY.zip) | 2,5 MB | `dc01c970f416fb69…` |
| sumy SHA-256 | [SHA256SUMS.txt](https://github.com/janradzik86/mein/raw/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/SHA256SUMS.txt) | 555 B | |

Pełna lista plików: [SHA256SUMS.txt](https://github.com/janradzik86/mein/raw/fc7e7b35e48f933ec55da74a76751ea9599a4815/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/SHA256SUMS.txt)

## ⚠️ Najpierw odinstaluj starą wersję

1.0.6 jest podpisane **nowym certyfikatem** (stary klucz przepadł razem ze środowiskiem
budowania). Jeśli na telefonie jest 1.0.0–1.0.5, system odmówi instalacji
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Odinstaluj starą wersję, potem zainstaluj 1.0.6.
Kolejne wydania z tego klucza będą się już aktualizowały normalnie.

## Co jest sprawdzone (dowody w RAPORT-TESTOW.md)

* warunki instalacji APK: **0 problemów blokujących** (`KONTROLA-INSTALACJI.txt`),
* podpis v1 + v2: **własny weryfikator i androguard — oba OK**,
* DEX: dexcheck 0 błędów, dexlink 0 problemów, dexverify 0 naruszeń weryfikatora ART,
* scenariusze E2E A–M na silniku: **24 PASS**,
* Windows: EXE i instalator zbudowane ze źródeł 1.0.6.

## Czego NIE sprawdzono — wprost

* **instalacja na telefonie / emulatorze**: środowisko nie ma wirtualizacji (`/dev/kvm`),
  Dockera ani dostępu do `dl.google.com`,
* **uruchomienie EXE/instalatora**: brak Wine/Windows,
* **41 testów JVM**: w tym środowisku nie ma kompilatora Javy (szczegóły w POPRAWKA-1.0.6.md),
* **STT offline i model językowy**: brak możliwości pobrania wag (Hugging Face zablokowany);
  „Generator AI” to deterministyczny generator proceduralny — opis w MODELE-I-LICENCJE.md.

## Jeśli instalacja nadal się nie uda

Potrzebny dokładny komunikat, nie domysł:

```
adb install -r WOJAN-LaserCraft-AI-Android-Release.apk
# → Success  albo  Failure [INSTALL_FAILED_*]
adb logcat -d | findstr /i "PackageManager PackageInstaller installd dex2oat"
```

Albo zdjęcie komunikatu Androida i informacja, czy po próbie instalacji pozycja
„WOJAN LaserCraft AI” pojawia się w Ustawienia → Aplikacje.
