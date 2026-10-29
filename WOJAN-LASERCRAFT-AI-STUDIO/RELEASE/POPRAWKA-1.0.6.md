# 1.0.6 — pełna przebudowa od zera, nowy podpis, naprawiony błąd w podpisywaniu v2

Data: 2026-10-29 · versionCode 7 · poprzednia wersja: 1.0.5 (6)

## Co się stało z poprzednim wydaniem

Środowisko budowania zostało wyczyszczone: zniknęły JDK, Zig, aapt2, apktool, `android.jar`,
a **także klucz podpisywania** (`keystore/*.p12`). Wszystko odtworzyłem od zera — poniżej
skąd, żeby to było sprawdzalne:

| Narzędzie | Źródło w tym środowisku |
|---|---|
| JDK 25 (samo JRE, bez `javac`) | PyPI, pakiet `jdk4py` |
| Kompilator Zig (Windows x64) | PyPI, pakiet `ziglang` |
| aapt2 2.19 (oficjalny AAPT2) | PyPI, wheel `aapt2` → `aapt2/bin/Linux/aapt2` |
| apktool 2.9.3 (w środku smali/dexlib2 3.x) | npm, pakiet `@postar/apktool-node` |
| `android.jar` API 34 | GitHub, `Sable/android-platforms`, blob `923bafcc…` (ten sam commit co wcześniej) |
| ImGui 1.91.5 (Windows) | codeload.github.com, tag `v1.91.5` |

## Realny błąd znaleziony i naprawiony: podpisywanie v2

W środowisku **nie ma `apksigner.jar`** (Google/Maven zablokowane), więc podpisywanie
musiało być zaimplementowane od zera: `tools/apk_sign.py` (v1 JAR + v2 APK Signature).

Przy pierwszym przebiegu weryfikacja wykryła błąd: **sekwencja podpisów w bloku v2 miała
jedno zbędne pole długości (uint32)** — `signer.signatures` było owinięte dwa razy zamiast
raz. Taki APK ma poprawny podpis kryptograficzny, ale **niezgodny format** — Android
odrzuciłby go przy instalacji (`INSTALL_PARSE_FAILED_NO_CERTIFICATES` / błąd weryfikacji
podpisu). Ten sam błąd siedział wcześniej w `tools/apksig/apksig.py` (napisanym w innym
projekcie w tym repozytorium) i nigdy nie był użyty do wydania — dlatego go nie widzieliśmy.

Po poprawce podpis jest sprawdzany **dwoma niezależnymi implementacjami**:

* `tools/apk_sign.py --verify` — własny weryfikator: podpis RSA nad `signed data`,
  zgodność `SPKI`, zgodność digestu pliku z podpisanym digestem (`True`),
* **androguard** (PyPI, niezależna implementacja spoza tego projektu):
  `v1=True v2=True`, pakiet i wersja odczytane poprawnie.

## Nowy klucz podpisywania

Stary klucz przepadł razem ze środowiskiem — **nie da się go odzyskać**. Wydanie 1.0.6 jest
podpisane **nowym** certyfikatem:

```
CN=WOJAN AI FACTORY Release Key, O=WOJAN AI FACTORY, C=PL
SHA-256: F3:E5:AF:82:F8:84:D6:45:AD:C3:12:44:E9:08:A9:62:E3:57:F4:22:43:B0:48:DA:16:CB:E4:04:75:FD:D7:EE
ważny: 2026-10-29 → 2036-10-26, RSA 2048, SHA256withRSA
```

Konsekwencja: jeśli na telefonie została **jakakolwiek** wcześniejsza wersja (1.0.0–1.0.5),
trzeba ją **odinstalować** przed instalacją 1.0.6 — inaczej system odmówi
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`: inny podpis). Kolejne wydania z tego klucza będą się
już aktualizowały „na wierzch”.

Hasło i plik `keystore/wojan-lasercraft.p12` **nie są w repozytorium** (`.gitignore`) —
klucz trzeba odebrać od wykonawcy osobnym, kontrolowanym kanałem.

## Jak zbudowano APK bez kompilatora Javy

W tym środowisku **nie ma żadnego kompilatora Javy** (JDK z PyPI to samo JRE;
ECJ niedostępny — Maven i Google zablokowane; repozytoria z `ecj.jar` zawierają albo
wskaźniki Git LFS, albo pliki `.sha1`). Dlatego:

1. `apktool d` (baksmali) wyciąga z wydanego DEX **smali — 150 klas**, 1:1 z źródłami
   w `android/app/src`,
2. numer wersji podbijany w smali, `apktool.yml` i manifeście,
3. `apktool b --use-aapt2` składa zasoby i manifest oficjalnym aapt2,
4. `tools/pack_apk.py` przepakowuje: `resources.arsc` i `classes.dex` **STORED + 4 B**,
5. kontrole DEX: `dexcheck` (566 code_item, 0 błędów), `dexlink` (6218 wywołań, 0 problemów),
   `dexverify` (6218 wywołań, 0 naruszeń weryfikatora ART),
6. `tools/apk_sign.py` podpisuje v1 + v2.

Skrypt: `tools/build_android_smali.sh`. **Ograniczenie, o którym trzeba wiedzieć:** bez
kompilatora Javy nie da się w tym środowisku zmienić kodu aplikacji Android — 1.0.6 ma
kod identyczny z 1.0.5 (zmieniony numer wersji, nowy podpis, świeżo zbudowane zasoby).
Zmiany funkcjonalne wymagają środowiska z `javac`/ECJ.

## Stan testów

| Zakres | Wynik | Dowód |
|---|---|---|
| Kontrola instalacji APK (aapt2 parse, arsc STORED/4B, targetSdk 36, DEX 037, activity, ZIP) | 0 problemów blokujących | `RELEASE/KONTROLA-INSTALACJI.txt` |
| Podpis v1+v2: własny weryfikator + androguard | OK / OK | tamże |
| Audyt API: klasy `android.*` vs minSdk 24 | 0 klas nowszych, 0 brakujących nadtypów | tamże |
| DEX: dexcheck / dexlink / dexverify | 0 / 0 / 0 | log builda |
| Scenariusze E2E A–M na silniku (CLI) | 24 PASS / 0 FAIL | `tests/out/raport_selftest.txt` |
| Windows EXE + instalator | zbudowane ze źródeł 1.0.6 | `build/WOJAN-LaserCraft-AI.exe`, Setup |
| **41 testów JVM** | **NIE URUCHOMIONE** — brak kompilatora Javy w środowisku | — |
| **Instalacja na telefonie / emulatorze** | **NIEWERYFIKOWANE** — brak KVM, Dockera i dostępu do dl.google.com | — |
| **Uruchomienie EXE/instalatora** | **NIEWERYFIKOWANE** — brak Wine/Windows | — |

## Czego nadal nie ma (bez zmian, świadomie)

* **Lokalne rozpoznawanie mowy (STT)** — nie ma whisper.cpp ani wag modelu. Pobranie wag
  jest w tym środowisku niemożliwe (Hugging Face zablokowany). Zakładka Mikrofon nagrywa
  dźwięk i prosi o wpisanie treści.
* **Sieciowy model językowy (LLM)** — z tego samego powodu: brak możliwości pobrania wag
  (Bielik 1.5B ≈ 1 GB, Llama 3.2 1B ≈ 0,8 GB). „Generator AI” to deterministyczny generator
  proceduralny + parser języka polskiego, opisany w `MODELE-I-LICENCJE.md`.
* **Podpis v3** — nieobsługiwany (służy tylko rotacji klucza; dla minSdk 24 wystarcza v2).

## Instalacja 1.0.6

1. Odinstaluj starą wersję (inny podpis — inaczej `INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
2. Zainstaluj `WOJAN-LaserCraft-AI-Android-Release.apk` (805 kB).
3. Jeśli system odmówi: `adb install -r …apk` i prześlij dokładny kod `INSTALL_FAILED_*`.
