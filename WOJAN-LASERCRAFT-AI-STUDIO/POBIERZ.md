# POBIERZ — WOJAN LaserCraft AI Studio 1.0.5

**Kliknij link, nie kopiuj ścieżki.** Linki są pełne, przypięte do commita
`afe099e0b9e0eba99186acbdb6e981f0ac9c62f7`, więc działają nawet po dodaniu kolejnych zmian.

## APK — aplikacja na telefon

[**WOJAN-LaserCraft-AI-Android-Release.apk** (809 kB)](https://github.com/janradzik86/mein/raw/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Android-Release.apk)

SHA-256: `cf311f3fc5568299476d5b4d20ddb7627c26b356404bffba1ca5b97edcdc50bb`

## Windows 11 (x64)

* [**Instalator: WOJAN-LaserCraft-AI-Windows-Setup.exe** (4,8 MB)](https://github.com/janradzik86/mein/raw/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI-Windows-Setup.exe)
  — SHA-256 `087eab07d8ab8ce4975bb4913c7377d19541959f5a0336061b846877daa775d2`
* [Sama aplikacja: WOJAN-LaserCraft-AI.exe (2,7 MB)](https://github.com/janradzik86/mein/raw/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/WOJAN-LaserCraft-AI.exe)
  — SHA-256 `8c8e0ee618c8896689a246a3444fd4e0d39a89ccd014531f9a6c3bb7eab30e41`

## Dokumenty i archiwa

| Plik | Link |
|---|---|
| Sumy SHA-256 | [SHA256SUMS.txt](https://github.com/janradzik86/mein/raw/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/SHA256SUMS.txt) |
| Instrukcja (PDF) | [INSTRUKCJA.pdf](https://github.com/janradzik86/mein/raw/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/INSTRUKCJA.pdf) |
| Raport testów | [RAPORT-TESTOW.md](https://github.com/janradzik86/mein/blob/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/RAPORT-TESTOW.md) |
| Modele i licencje | [MODELE-I-LICENCJE.md](https://github.com/janradzik86/mein/blob/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/MODELE-I-LICENCJE.md) |
| Kontrola instalacji APK | [KONTROLA-INSTALACJI.txt](https://github.com/janradzik86/mein/blob/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/KONTROLA-INSTALACJI.txt) |
| 26 projektów przykładowych | [PRZYKLADOWE-PROJEKTY.zip](https://github.com/janradzik86/mein/raw/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/PRZYKLADOWE-PROJEKTY.zip) |
| Kod źródłowy | [KOD-ZRODLOWY.zip](https://github.com/janradzik86/mein/raw/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/KOD-ZRODLOWY.zip) |
| Co zmieniono | [POPRAWKA-1.0.5.md](https://github.com/janradzik86/mein/blob/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7/WOJAN-LASERCRAFT-AI-STUDIO/RELEASE/POPRAWKA-1.0.5.md) |

## Jeśli link nadal nie pobiera pliku

1. **Telefon**: po kliknięciu Chrome może otworzyć podgląd zamiast pobierać — wybierz
   menu ⋮ → „Pobierz”. Plik musi mieć **809 kB**; jeśli ma kilkadziesiąt kB, przeglądarka
   zapisała stronę HTML zamiast APK.
2. **Komputer**: prawy przycisk → „Zapisz element docelowy jako…”.
3. Konto GitHub nie jest potrzebne — repozytorium jest publiczne.
4. Jeśli `raw.githubusercontent.com` jest zablokowany w Twojej sieci (niektóre sieci
   firmowe), pobierz całe repozytorium jako ZIP i wypakuj katalog `RELEASE`:
   <https://github.com/janradzik86/mein/archive/afe099e0b9e0eba99186acbdb6e981f0ac9c62f7.zip>

## Uwaga o GitHub Actions

Wydania w tym repozytorium **nie mają załączników** i nie da się ich dodać z środowiska,
w którym budujemy aplikację: host `uploads.github.com` jest tam zablokowany
(`SSL_ERROR_SYSCALL`), a GitHub Actions w tym repozytorium nie uruchamia zadań (przebieg
kończy się po 3 s, bez przydzielonego runners — blokada rozliczeniowa). Dlatego pliki są
w repozytorium, a powyższe linki prowadzą bezpośrednio do nich. Gdy blokada Actions
zostanie zdjęta, `.github/workflows/lasercraft-publikacja-wydania.yml` doda je jako
zwykłe załączniki wydania.
