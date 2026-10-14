# Terminownik — plan i kryteria odbioru

Dokument spisany przed implementacją i uzupełniony o stan faktyczny (kolumna „Stan").
Zakres: w pełni lokalna aplikacja Android do pilnowania terminów ważności w spiżarni i apteczce.

## 1. Cel

Natatywna aplikacja (Kotlin + Jetpack Compose + Material 3), która działa **bez konta i bez internetu**,
pilnuje terminów ważności, rozdziela partie tego samego produktu i nigdy nie tworzy przypomnienia
z daty odczytanej automatycznie bez potwierdzenia użytkownika.

## 2. Architektura (warstwy)

| Warstwa | Zawartość | Zależność od Androida |
| --- | --- | --- |
| `domain/` | modele, reguły terminów, walidacja GTIN, parser GS1, parser dat OCR, planer przypomnień, CSV, formatowanie PL | brak (czysty JVM — testowalny lokalnie bez SDK) |
| `data/local/` | Room: pozycje/partie, słownik kod→nazwa, zakupy, log przypomnień, zaplanowane alarmy | tak |
| `data/settings/` | DataStore Preferences | tak |
| `data/photo/` | import zdjęć przez systemowy selektor, przechowywanie w katalogu aplikacji | tak |
| `data/backup/` | format kopii (ZIP + manifest + JSON + zdjęcia), walidator niezaufanego wejścia, eksport/import | tak |
| `notifications/` | kanały, alarmy dokładne z fallbackiem, odbiorniki systemowe, worker porządkujący | tak |
| `scan/` | ML Kit barcode + text recognition (modele wbudowane), interpretacja domenowa | tak |
| `ui/` | Compose, Material 3, motyw jasny/ciemny/systemowy, ekran wdrożenia, listy, formularz, skaner, OCR, zakupy, ustawienia | tak |

DI: ręczny kontener (`AppContainer`) — bez frameworka, bez generowania kodu.

## 3. Kryteria odbioru i stan

| # | Kryterium (ze specyfikacji) | Stan | Gdzie zweryfikowane |
| --- | --- | --- | --- |
| 1 | Osobne sekcje Spiżarnia i Apteczka | zaimplementowane | `ItemListScreen`, filtr `Section` |
| 2 | Pozycja = nazwa, ilość, jednostka, miejsce, zdjęcie, partia, termin, notatka | zaimplementowane | `PantryItem`, `ItemEditScreen` |
| 3 | Skanowanie kodów; EAN nigdy nie daje terminu | zaimplementowane + **wykonane testy** | `BarcodeInterpreter`, scenariusze „Kody" (5/5) |
| 4 | Lokalny słownik kod → ostatnio wpisana nazwa | zaimplementowane | `BarcodeNameDao.remember`, `lookupBarcodeName` |
| 5 | GS1 DataMatrix z AI, gdy faktycznie występuje | zaimplementowane + **wykonane testy** | `Gs1Parser`, scenariusze „GS1" (12/12) |
| 6 | OCR daty na urządzeniu | zaimplementowane + **wykonane testy logiki** | `OcrDateParser`, scenariusze „OCR" (14/14) |
| 7 | Każda data automatyczna wymaga potwierdzenia | zaimplementowane + **wykonane testy** | `ExpiryDate.confirmed`, `ExpiryRules.countsForReminder` |
| 8 | Rozróżnienie „należy spożyć do" / „najlepiej spożyć przed" | zaimplementowane | `DateType`, dialog daty, CSV |
| 9 | Widoki: kończące się / po terminie / brak daty | zaimplementowane | `DeadlinesScreen`, filtry listy |
| 10 | Przypomnienia z konfigurowalnym wyprzedzeniem | zaimplementowane + **wykonane testy logiki** | `ReminderPlanner` (7/7), `ReminderScheduler` |
| 11 | Zużyte / wyrzucone / uzupełnione (nowa partia) | zaimplementowane | `restock()` tworzy nowy wiersz |
| 12 | Lista zakupów + eksport CSV | zaimplementowane + **wykonane testy** | `ShoppingScreen`, `CsvExport` |
| 13 | Pełna kopia zapasowa z walidacją | zaimplementowane + **wykonane testy** | `BackupRepository`, `BackupValidator` (14/14 scenariuszy + testy JUnit) |
| 14 | Partie tego samego kodu pozostają rozdzielone | zaimplementowane | brak klucza unikalnego po kodzie; test instrumentowany `PantryPersistenceTest` |
| 15 | Błędny OCR prowadzi do poprawki, nie do fałszywego przypomnienia | zaimplementowane + **wykonane testy** | `OcrIssue`, `countsForReminder=false` |
| 16 | Skanowanie i OCR offline od pierwszego uruchomienia | zaimplementowane | ML Kit bundled; `INTERNET` usunięty z manifestu (`tools:node="remove"`) |
| 17 | Brak porad medycznych i dawkowania | zaimplementowane | `disclaimer_medicine` w apteczce i formularzu |
| 18 | Nie oceniamy bezpieczeństwa żywności po samej dacie | zaimplementowane | `disclaimer_food` |
| 19 | Usuwanie danych przez użytkownika | zaimplementowane | „Usuń wszystkie dane" w ustawieniach + `eraseAllData()` |
| 20 | minSdk 26, wersje przypięte, wrapper w repo | zaimplementowane | `libs.versions.toml` (bez `+`, bez alpha/beta), `gradle/wrapper` + SHA-256 dystrybucji |
| 21 | Polski interfejs, wszystkie teksty w zasobach | zaimplementowane + **wykonana kontrola** | 375 stringów; `tools/check-strings.py`: 0 brakujących, 0 złych argumentów, 0 nieużywanych |
| 22 | Jasny i ciemny motyw, duże fonty, podstawowy TalkBack | zaimplementowane | `Theme.kt`, typografia w `sp`, `contentDescription`, semantyka kart |
| 23 | Stany: pusta lista, ładowanie, brak internetu, błędne dane, odmowa uprawnień | zaimplementowane | `EmptyState`/`LoadingState`/`ErrorState`, ekrany odmowy kamery i powiadomień |
| 24 | Ikona, splash, krótkie wdrożenie | zaimplementowane | ikona adaptacyjna (wektor), `Theme.Terminownik.Splash`, `OnboardingScreen` (3 kroki) |
| 25 | Podpisane APK release do pobrania | **NIEZREALIZOWANE — bloker środowiskowy** | patrz `RELEASE_CHECKLIST.md` |

## 4. Kolejność prac (zrealizowana)

1. Rozpoznanie środowiska i wersji bibliotek (API GitHub, tarball'e referencyjne).
2. Konfiguracja buildu: wrapper, katalog wersji, moduł, manifest, proguard, reguły backupu.
3. Domena (czysty JVM) + lokalny runner scenariuszy.
4. Room, DataStore, zdjęcia, repozytorium.
5. Kopia zapasowa: kontrakt, walidator, eksport/import.
6. Powiadomienia i alarmy.
7. Skanowanie i OCR.
8. UI: motyw, komponenty, nawigacja, ekrany.
9. Zasoby tekstowe + statyczna kontrola spójności.
10. Testy jednostkowe i instrumentowane.
11. CI: build, podpisywanie, weryfikacja, publikacja Release.
12. Dokumentacja (ten katalog).

## 5. Ryzyka i ograniczenia

* Brak Android SDK w środowisku roboczym → warstwa Android (Compose/Room/CameraX/ML Kit)
  nie została lokalnie skompilowana. Pierwszy build w CI lub lokalnie może wymagać poprawek.
* Emulatory na runnerach GitHub bywają niestabilne → job instrumentowany jest best-effort
  (`continue-on-error: true`), a jego wynik raportujemy dosłownie (nigdy „zaliczone" bez wykonania).
* Android nie gwarantuje przypomnienia co do sekundy (Doze, optymalizacja baterii) — aplikacja
  używa alarmów dokładnych, gdy są dozwolone, a w przeciwnym razie okna ±15 minut i informuje o tym
  użytkownika (`settings_reminder_limitations`).
