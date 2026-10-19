# Instalacja na urządzeniu — kroki i diagnostyka błędów

## Windows 11 — `WOJAN-AI-FACTORY-Setup.exe`

1. Pobierz Setup.exe i **skopiuj na Windows** (sprawdź sumę SHA-256 — patrz `docs/RAPORT.md`).
2. Uruchom dwukrotnie. Jeśli SmartScreen blokuje: **„Więcej informacji" → „Uruchom mimo to"**
   (plik nie jest podpisany certyfikatem EV — to normalne przy własnych instalatorach).
3. Wybierz katalog → Instaluj. Skrót pojawi się na pulpicie.
4. Deinstalacja: Ustawienia → Aplikacje, albo `Setup.exe /uninstall`.

Zamiast instalatora: `WOJAN-AI-FACTORY-Portable.zip` → rozpakuj → `WOJAN-AI-FACTORY.exe`.

## Android — `WOJAN-AI-FACTORY-Release.apk`

1. **Jeśli masz zainstalowaną wcześniejszą wersję WOJAN — ODINSTALUJ JĄ NAJPIERW.**
   (Wcześniejsze buildy były podpisane innym kluczem testowym — Android odmówi aktualizacji:
   „Nie zainstalowano aplikacji" / signature mismatch.)
2. Pobierz APK → skopiuj na telefon.
3. (Opcjonalnie) sprawdź sumę pliku po przeniesieniu — musi się zgadzać z podaną przy pobieraniu.
4. Stuknij w APK. Gdy system pyta o **„Nieznane źródła"** — zezwól **dla tej aplikacji**
   (Przeglądarka/Menedżer plików → Instaluj nieznane aplikacje).
5. Gdy **Play Protect** zablokuje: „Szczegóły" → **„Zainstaluj mimo wszystko"**.
   (APK spoza Sklepu Play, bez reputacji — standardowe ostrzeżenie, nie wirus.)
6. Po instalacji: podaj URL komputera `http://<IP-PC>:8765` + token z zakładki Ustawienia na PC.

## Mapa błędów instalacji → przyczyna → rozwiązanie

| Komunikat na telefonie | Przyczyna | Rozwiązanie |
|---|---|---|
| „Nie zainstalowano aplikacji" / „App not installed" po instalacji WCZEŚNIEJSZEJ wersji | inny klucz podpisu (stara wersja) | **odinstaluj starą wersję** → zainstaluj nową |
| „Nie udało się przeanalizować pakietu" / „There was a problem parsing the package" | plik uszkodzony przy pobieraniu/przenoszeniu | pobierz ponownie, porównaj SHA-256, przenieś kablem/USB |
| „Zablokowano przez Play Protect" | brak reputacji w Google | Szczegóły → Zainstaluj mimo wszystko |
| „Aplikacja nie jest zgodna z telefonem" / brak w liście | Android < 5.0 (minSdk 21) | wymagany Android 5.0+ |
| „Brak miejsca" | pamięć pełna | zwolnij miejsce |
| Instalacja startuje, ale aplikacja gaśnie przy starcie | błąd wykonawczy | wyślij mi komunikat — naprawię (to NIE jest problem instalacji) |

## Diagnostyka przez ADB (gdy masz PC pod ręką)

```bash
adb install -r WOJAN-AI-FACTORY-Release.apk
# adb wypisze DOKŁADNY kod błędu, np. INSTALL_FAILED_UPDATE_INCOMPATIBLE,
# INSTALL_PARSE_FAILED_*, INSTALL_FAILED_OLDER_SDK — podaj mi ten kod.
```

## Podpis

APK jest podpisany **kluczem testowym/dev** (zgodnie z ustaleniem — do testów).
Do wydania produkcyjnego: `docs/PODPIS-PRODUKCYJNY.md` (Twój własny klucz na Twoim PC).
Po przejściu na klucz produkcyjny **ponowna instalacja wymaga odinstalowania wersji testowej**
(inny podpis = inna tożsamość aplikacji).
