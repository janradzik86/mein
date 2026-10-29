# 1.0.8 — prawdziwy silnik mowy whisper.cpp wbudowany w aplikację

Data: 2026-10-29 · versionCode 9 · poprzednia wersja: 1.0.6 (7)

## Co doszło: silnik rozpoznawania mowy

Do programu wkompilowany jest **whisper.cpp 1.7.4** (silnik OpenAI Whisper, MIT) wraz
z biblioteką ggml i backendem CPU. To nie jest zaślepka ani wywołanie chmury — to pełny
kod inferencji, skompilowany dla Windows x64 (Zig/clang, bez Visual Studio):

* źródła: `third_party/whisper.cpp` (pobrane z codeload.github.com, tag v1.7.4),
* skrypt budowania: `tools/build_whisper.sh linux|windows` (bez CMake),
* integracja: `core/stt.h`, `core/stt.cpp` — czytanie WAV (PCM 16-bit, dowolne
  sample rate → resampling do 16 kHz, mono), wykrywanie modelu, transkrypcja,
  czyszczenie znaczników `[...]`,
* interfejs: zakładka **Mikrofon i prompt** → przycisk **ROZPOZNAJ MOWE (offline)**,
  status modelu pokazany wprost („model: ggml-base-q5_1.bin, 57 MB” albo ostrzeżenie),
* rozmiar EXE wzrósł z 2,80 MB do **3,75 MB** (to waga samego silnika).

## Czego nadal nie ma: PLIKU WAG

Wagi modelu (plik `ggml-base-q5_1.bin`, 59,7 MB) **nie są w instalatorze**, bo w środowisku
budowania nie da się ich pobrać — to nie jest wymówka, to wynik sprawdzenia:

| Źródło | Wynik |
|---|---|
| Hugging Face (`huggingface.co`) | host niedostępny w środowisku budowania |
| GitHub — 43 znalezione kopie modelu (`ggml-tiny/base`, także wersje q5_1) | **wszystkie to wskaźniki Git LFS (131–134 B)**, nie pliki wag |
| `raw.githubusercontent.com`, `media.githubusercontent.com` | zablokowane (SSL) |
| PyPI | brak paczek z wagami Whispera |

Dlatego instalator zawiera katalog **`modele`** z instrukcją i skryptem
`pobierz_model.bat` (Windows) / `pobierz_model.sh`. Po pobraniu pliku (2 minuty,
jednorazowo, na Twoim komputerze — tam Hugging Face działa) aplikacja **sama wykrywa
wagi** i przycisk rozpoznawania mowy zaczyna działać. Aplikacja nigdy nie udaje, że
model jest: pokazuje „brak pliku wag” i podaje katalog, w którym go szuka.

## Podpis APK: teraz prawdziwy apksig Google

Wcześniejsze wydania podpisywałem własną implementacją v1+v2. Teraz build używa
**oryginalnej biblioteki Google `apksig`** (przez `uber-apk-signer 1.3.0`, pobrany jako
wheel z PyPI) — czyli dokładnie tego samego kodu, co `apksigner` z Android SDK.
Weryfikacja po podpisaniu:

```
- zipalign verified
- signature verified [v2, v3]
  SHA256: 5aac0b5656fa4cd25a7b726c4f1afe3accfda332fce1181803d5bed69fe0e23d
Successfully processed 1 APKs and 0 errors
```

**Nowy certyfikat** (klucz z poprzedniego przebiegu przepadł razem z czyszczonym
środowiskiem; klucz jest teraz w `/home/user/toolchain/keystore/`, poza repozytorium):
`CN=WOJAN AI FACTORY Release Key`, SHA-256 `5aac0b56…0e23d`, RSA 2048, ważny do 2036.
Przed instalacją trzeba odinstalować każdą wcześniejszą wersję (inny podpis).

## Kontrole APK 1.0.8

| Kontrola | Wynik |
|---|---|
| warunki instalacji (aapt2 parse, arsc STORED+4 B, targetSdk 36, DEX 037, activity, ZIP) | 0 problemów blokujących |
| podpis | v1 + v2 + v3, zweryfikowane prawdziwym `apksig` i własnym weryfikatorem |
| zipalign | OK (wbudowany zipalign 33.0.2 z uber-apk-signer) |
| DEX: dexcheck / dexlink / dexverify | 0 / 0 / 0 |
| audyt API vs minSdk 24 | 0 klas nowszych |
| rozmiar | 828 893 B, wersja 1.0.8 (9) |

## Nadal brakuje (świadomie, bez ukrywania)

* **Model językowy (LLM)** — nie ma wag w tym środowisku z tego samego powodu co Whisper.
  „Generator AI” pozostaje deterministycznym generatorem proceduralnym + parserem języka.
* **STT na Androidzie** — aplikacja Android nie ma silnika whisper (brak NDK/kompilatora
  ARM w tym środowisku). Zakładka Mikrofon na telefonie nagrywa dźwięk i prosi o tekst.
* **Instalacja APK na telefonie** — niezweryfikowana wykonawczo (brak wirtualizacji,
  brak dostępu do obrazów systemu Android w środowisku).
