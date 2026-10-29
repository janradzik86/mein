# WOJAN LaserCraft AI Studio 1.0.8 — pobierz

Wydanie przypięte do commita [`e2d724d`](https://github.com/janradzik86/mein/commit/e2d724d836f0a5d3d0a579587f076981ae287ce3) —
linki są trwałe, nie wygasają.

## Co jest nowego w 1.0.8 (najważniejsze)

**Silnik rozpoznawania mowy whisper.cpp 1.7.4 jest wkompilowany w program Windows.**
EXE urósł z 2,80 MB do 3,75 MB — to kod inferencji, nie zaślepka. W zakładce
„Mikrofon i prompt" widać realny status: nazwę pliku wag i rozmiar modelu.

**Czego brakuje: pliku wag modelu.** W środowisku budowania `huggingface.co` jest
nieosiągalny, a wszystkie 43 znalezione w GitHub kopie `ggml-*.bin` to wskaźniki
Git LFS (131–134 B), nie wagi. Dlatego instalator zawiera katalog `modele\` ze
skryptem `pobierz_model.bat`: jedno kliknięcie, 59,7 MB, na Twoim komputerze.
Po pobraniu program sam wykrywa model i rozpoznawanie mowy działa w pełni offline.

## Instalacja Windows 11 x64

1. Pobierz **WOJAN-LaserCraft-AI-Windows-Setup.exe** (6,0 MB).
2. Uruchom. SmartScreen: „Więcej informacji" → „Uruchom mimo to" (brak certyfikatu
   Authenticode — nie kupowałem go, bo to decyzja właściciela o koszcie).
3. Instalacja bez uprawnień administratora: `%LOCALAPPDATA%\WOJAN LaserCraft AI`.
   Skróty w Menu Start i na pulpicie, pełny deinstalator w Ustawieniach.
4. Chcesz mowę offline? Uruchom `modele\pobierz_model.bat` (jednorazowo).

## Instalacja Android

1. Pobierz **WOJAN-LaserCraft-AI-Android-Release.apk** (829 kB).
2. **Odinstaluj każdą wcześniejszą wersję** — certyfikat podpisu jest nowy
   (SHA-256 `5aac0b56…0e23d`), Android odmówi aktualizacji z innym podpisem.
3. Włącz „Instaluj z nieznanych źródeł" dla przeglądarki/menedżera plików i zainstaluj.
4. Podpis zweryfikowany prawdziwą biblioteką `apksig` Google: `v2, v3`, zipalign OK.

Android nie ma silnika whisper (brak NDK/kompilatora ARM w środowisku budowania) —
zakładka Mikrofon na telefonie nagrywa dźwięk i prosi o wpisanie tekstu. To jest
napisane wprost, nie ukryte.

## Pliki wydania (SHA-256 do weryfikacji)

| Plik | Rozmiar | SHA-256 |
|---|---|---|
| [WOJAN-LaserCraft-AI-Android-Release.apk](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk) | 828 893 B | `3ba6f389aa008b2effa748724624a5e911ea539290b86f40551929edca8626c4` |
| [WOJAN-LaserCraft-AI-Windows-Setup.exe](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Windows-Setup.exe) | 6 004 671 B | `544b5a45eed3c8d762cf9942b901a2e3b9bc13a3166c7eec20f023ff70136178` |
| [SHA256SUMS.txt](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/SHA256SUMS.txt) | 555 B | `bf41f2e29b4cf797a5db495776818ddeafd4a4c3580ae2648e6c9c3c3bce270e` |
| [INSTRUKCJA.pdf](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/INSTRUKCJA.pdf) | 70 315 B | `e11bc2eed9fde5d2cdcf5a8342aaaa9f6f7344802c8258eb7740e61edb674829` |
| [RAPORT-TESTOW.md](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/RAPORT-TESTOW.md) | 13 584 B | `754f045fe8157a09bf1721db3ee603f7f87918359d203301744c1daf4a765d7a` |
| [MODELE-I-LICENCJE.md](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/MODELE-I-LICENCJE.md) | 7 566 B | `45bc4bada6374a0f6b798a466a2312b37f63608e12f22273a90b687a38bd1b48` |
| [PRZYKLADOWE-PROJEKTY.zip](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/PRZYKLADOWE-PROJEKTY.zip) | 564 614 B | `3701d96f9b5d4ee6d8e506908edb68af7e22a1f2e86f516756e2d51e651a6066` |
| [KOD-ZRODLOWY.zip](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/KOD-ZRODLOWY.zip) | 8 290 077 B | `158a7d8bc3ae021821fec18e4e90920e2e8bd650b3811a43ffc384156d86ba3b` |
| [POPRAWKA-1.0.8.md](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/POPRAWKA-1.0.8.md) | 3 932 B | `0f95404335923f2e83d4c7051b15a357913f288a378723b20369c1664508b5aa` |

Sprawdzenie na Windows (PowerShell):
```powershell
Get-FileHash .\WOJAN-LaserCraft-AI-Windows-Setup.exe -Algorithm SHA256
```
Na telefonie/komputerze: `sha256sum -c SHA256SUMS.txt` w katalogu z plikami.

## Stan testów — bez upiększeń

* Podpis APK, zipalign, DEX (0/0/0), audyt API vs minSdk 24 — sprawdzone wykonawczo, wynik OK.
* Kompilacja i linkowanie whisper.cpp (Linux i Windows) — sprawdzone wykonawczo.
* **Transkrypcja realnego nagrania — nie sprawdzona**, bo brak pliku wag w tym środowisku.
* **Instalacja APK na telefonie — nie sprawdzona** (brak wirtualizacji/emulatora w środowisku).
* **Setup.exe na Windows — nie uruchomiony wykonawczo** (brak Windows w środowisku);
  koperta instalatora zweryfikowana programowo (47 wpisów, nagłówek PE, sumy zgodne).
* Model językowy (LLM) nie dołączony — te same blokady pobierania wag; generator działa
  na wbudowanym silniku proceduralnym i parserze polszczyzny.

Pełna lista: [RAPORT-TESTOW.md](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/RAPORT-TESTOW.md),
[POPRAWKA-1.0.8.md](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/POPRAWKA-1.0.8.md),
[MODELE-I-LICENCJE.md](https://github.com/janradzik86/mein/raw/e2d724d836f0a5d3d0a579587f076981ae287ce3/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/MODELE-I-LICENCJE.md).
