# Jak zbudować podpisane APK (jedna komenda)

Kod aplikacji jest w katalogu **`terminownik-android/`**. Skrypty poniżej same instalują JDK 21
i Android SDK (bez Android Studio, bez roota, bez konta Google), tworzą trwały klucz podpisywania,
kompilują, podpisują i weryfikują APK.

## Linux / macOS / WSL / Git Bash

```bash
git clone https://github.com/janradzik86/mein.git
cd mein
git checkout arena/3c74200e-mein
cd terminownik-android
bash scripts/one-shot-build.sh
```

## Windows (PowerShell)

```powershell
git clone https://github.com/janradzik86/mein.git
cd mein
git checkout arena/3c74200e-mein
cd terminownik-android
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\one-shot-build.ps1
```

Najszybsza droga do samego pliku (bez lintu i testów): flaga `--skip-tests` / `-SkipTests`.

## Wynik

| Plik | Zawartość |
| --- | --- |
| `dist/terminownik-release.apk` | podpisany plik instalacyjny (release, `debuggable=false`) |
| `dist/SHA256SUMS.txt` | suma kontrolna SHA-256 |
| `dist/VERIFY_REPORT.md` | wynik `apksigner verify --verbose --print-certs`, rozmiar, wersja, uprawnienia, testy |
| `dist/BUILD_FAILURE_LOG.txt` | tylko przy błędzie — pełny log kompilatora do wklejenia w zgłoszeniu |

Instalacja na telefonie:

```bash
adb install -r dist/terminownik-release.apk
```

albo skopiuj plik na telefon i otwórz go (zezwól na instalację z nieznanego źródła).

Klucz podpisywania powstaje przy pierwszym uruchomieniu w `~/keys/terminownik` — **zrób jego
kopię OFFLINE**, bez niej nie zaktualizujesz raz wydanej aplikacji. Skrypt nigdy nie nadpisuje
istniejącego klucza i nie drukuje haseł.

## Dlaczego nie ma gotowego pliku w repozytorium

GitHub Actions na koncie `janradzik86` nie uruchamia jobów — adnotacja z runu
[38496245640](https://github.com/janradzik86/mein/actions/runs/38496245640):

> The job was not started because your account is locked due to a billing issue.

Po odblokowaniu rozliczeń (https://github.com/settings/billing) ten sam workflow
(`.github/workflows/terminownik-release.yml`) zbuduje, zweryfikuje i opublikuje APK automatycznie
w Release `terminownik-v1.0.0`. Build lokalny opisany wyżej daje identyczny plik i nie zależy
od stanu konta GitHub.
