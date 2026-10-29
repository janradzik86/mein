# 1.0.5 — przebudowa od zera, kontrole instalacyjne w buildzie, komplet dokumentów

Data: 2026-10-29 · versionCode 6 · poprzednia wersja: 1.0.4 (5)

## Co dokładnie zmieniono

1. **Kontrole instalacyjne weszły do builda.** `tools/apk_installcheck.py` sprawdza na
   *gotowym, podpisanym* APK wszystkie warunki instalacji Androida 11–17 i przerywa build,
   gdy którykolwiek nie jest spełniony. Wynik trafia do `RELEASE/KONTROLA-INSTALACJI.txt`.
   Wcześniej te warunki sprawdzaliśmy ręcznie, osobnymi poleceniami.
2. **Audyt API** (`tools/apk_api_audit.py`) — 53 klasy platformy `android.*` użyte przez
   aplikację, każda z minimalnym poziomem API; build przerywa się, gdy pojawi się klasa
   nowsza niż `minSdk 24`.
3. **Potwierdzony podpis v1 + v2 + v3** w jednym pliku (`META-INF/WOJAN.SF`, `.RSA`,
   `MANIFEST.MF` + bloki v2/v3). Wcześniejsze raporty pokazywały „v1 = false”, bo
   `apksigner verify` domyślnie nie sprawdza JAR dla `minSdk ≥ 24`; podpis v1 **był**
   w pliku. Teraz jest to jawnie weryfikowane i raportowane.
4. **Powtarzalna budowa Windows.** Dodano `-Wl,/Brepro`; przebudowa od zera daje plik
   bit w bit identyczny (SHA-256 zgodny). Wersja Androida: treść identyczna (smali
   150 klas, 0 różnic po deasemblacji), ale plik nie jest bit-w-bit powtarzalny —
   apktool/smali zmienia kilka bajtów w strukturach pomocniczych DEX. Napisane wprost,
   żeby nikt nie porównywał sum między przebiegami i nie sądził, że coś podmieniono.
5. **Komplet dokumentów wydania**, których brakowało: `RAPORT-TESTOW.md`,
   `MODELE-I-LICENCJE.md`, `INSTRUKCJA.pdf` (3 strony, polskie znaki), `KOD-ZRODLOWY.zip`,
   `PRZYKLADOWE-PROJEKTY.zip`, `KONTROLA-INSTALACJI.txt`. Instalator Windows ładuje je
   teraz do katalogu `dokumentacja`.
6. Numer wersji podniesiony w silniku, UI Windows i APK (`1.0.5`, kod 6), żeby aktualizacja
   na telefonie była czysta i żeby nie było dwóch różnych plików z tym samym numerem.

## Czego NIE zmieniono, bo nie było czego naprawiać

W 1.0.5 **nie znaleziono nowego defektu instalacyjnego**. Wszystkie udokumentowane warunki
są spełnione (patrz `KONTROLA-INSTALACJI.txt`). Defekty z wcześniejszych wersji:

| Wersja | Defekt | Naprawiony w |
|---|---|---|
| 1.0.0–1.0.1 | DEX `dex\n036` — ART akceptuje tylko 035, 037–041 | 1.0.1 |
| 1.0.2–1.0.3 | `resources.arsc` spakowany bez wyrównania → `INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED` | 1.0.4 |
| 1.0.4 | `invoke-direct` zamiast `invoke-super` (j2smali) — `VerifyError` przy starcie | 1.0.3/1.0.4 |

## Czego nadal nie da się tu sprawdzić

Kontener, w którym budujemy, **nie ma wirtualizacji (brak `/dev/kvm`), Dockera ani dostępu
do `dl.google.com`**, więc nie da się uruchomić emulatora Androida ani żadnego systemu
Android 16/17. Fizycznej instalacji na telefonie i uruchomienia GUI nie wykonano —
i nie twierdzimy, że wykonano. Jeśli instalacja nadal się nie udaje, potrzebujemy
**dokładnego komunikatu lub kodu błędu**, żeby nie zgadywać:

```
adb install -r WOJAN-LaserCraft-AI-Android-Release.apk
# oczekiwany komunikat: Success  albo  Failure [INSTALL_FAILED_*]
adb logcat -d | findstr /i "PackageManager PackageInstaller installd dex2oat"
```

Jeśli nie ma dostępu do ADB: w telefonie **Ustawienia → Aplikacje → zobacz, czy pozycja
„WOJAN LaserCraft AI” istnieje** (jeśli istnieje, to nie jest problem instalacji, tylko
uruchamiania — wtedy aplikacja sama pokaże ekran błędu i zapisze `crash.txt`).

## Weryfikacja tego wydania

| Kontrola | Wynik | Dowód |
|---|---|---|
| Testy silnika na JVM | 41 PASS / 0 FAIL | `tests/out/raport_jvm.txt` |
| Scenariusze E2E A–M (CLI) | 24 PASS / 0 FAIL | `tests/out/raport_selftest.txt`, `data/selftest/` |
| Warunki instalacji APK | 0 problemów blokujących | `RELEASE/KONTROLA-INSTALACJI.txt` |
| Audyt API (DEX) | 0 klas nowszych niż minSdk 24 | tamże |
| DEX: struktura / linkowanie / weryfikator ART | 0 błędów / 0 / 0 | log builda |
| Instalator Windows | 38 wpisów, PE OK, 0 rozbieżności | `tools/make_installer.py --build` |
| Instalacja na telefonie, GUI Android | **NIEWERYFIKOWANE** (brak emulatora/wirtualizacji) | pkt 0 raportu testów |
| Uruchomienie EXE/instalatora | **NIEWERYFIKOWANE** (brak Wine) | tamże |
