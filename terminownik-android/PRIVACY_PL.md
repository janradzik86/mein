# Prywatność i rzeczywisty przepływ danych — Terminownik 1.0.0

Ten dokument opisuje **faktyczne** zachowanie aplikacji, a nie deklaracje marketingowe.
Każde stwierdzenie da się zweryfikować w kodzie albo w zbudowanym APK (polecenia w nawiasach).

## 1. Najważniejsze: aplikacja nie ma dostępu do internetu

* Uprawnienia `INTERNET`, `ACCESS_NETWORK_STATE` i `ACCESS_WIFI_STATE` są **usunięte z manifestu**
  przez `tools:node="remove"` (`app/src/main/AndroidManifest.xml`).
* Bez `INTERNET` system Android blokuje ruch sieciowy aplikacji na poziomie jądra — nawet gdyby
  którakolwiek biblioteka chciała coś wysłać, **nie może**.
* CI weryfikuje to na zbudowanym APK i przerywa wydanie, gdyby uprawnienie się pojawiło
  (`aapt2 dump permissions` / `apkanalyzer manifest print`).

Konsekwencje: brak konta, brak serwera, brak chmury, brak analityki, brak reklam, brak SDK
reklamowych i trackingowych, brak aktualizacji danych produktów z sieci.

## 2. Gdzie są Twoje dane

| Dane | Miejsce | Widoczność |
| --- | --- | --- |
| Pozycje, partie, terminy, notatki, statusy | baza SQLite (Room) w katalogu prywatnym aplikacji | tylko aplikacja (sandbox Androida) |
| Słownik kod → ostatnio wpisana nazwa | ta sama baza | tylko aplikacja |
| Lista zakupów | ta sama baza | tylko aplikacja |
| Historia i plan przypomnień | ta sama baza | tylko aplikacja |
| Ustawienia (motyw, wyprzedzenie, godzina) | DataStore Preferences w katalogu prywatnym | tylko aplikacja |
| Zdjęcia produktów | pliki w katalogu prywatnym aplikacji (`files/photos`) | tylko aplikacja |
| Kopia zapasowa | plik ZIP **w miejscu wskazanym przez Ciebie** (systemowy selektor plików) | Ty decydujesz |

Nie używamy pamięci współdzielonej ani katalogów publicznych. `allowBackup="true"` oznacza jedynie,
że systemowa kopia zapasowa Androida może obejmować dane aplikacji — to funkcja systemu, którą
możesz wyłączyć w ustawieniach telefonu.

## 3. Uprawnienia — prosimy tylko w momencie użycia

| Uprawnienie | Po co | Kiedy prosimy | Co, jeśli odmówisz |
| --- | --- | --- | --- |
| `CAMERA` (`required="false"`) | skanowanie kodów i zdjęcie etykiety do OCR | dopiero po wejściu w skaner/OCR | wpis ręczny kodu i daty; wybór gotowego zdjęcia z galerii; aplikacja działa dalej |
| `POST_NOTIFICATIONS` (Android 13+) | przypomnienia o terminach | przy pierwszym planowaniu / w ustawieniach | terminy są widoczne w aplikacji, przypomnienia nie przychodzą |
| `SCHEDULE_EXACT_ALARM` | punktualne przypomnienie o wybranej godzinie | w ustawieniach, link do ekranu systemowego | planujemy okno ±15 minut i informujemy o tym wprost |
| `VIBRATE` | wibracja powiadomienia | — | brak wpływu na funkcje |
| `RECEIVE_BOOT_COMPLETED` | przywrócenie przypomnień po restarcie telefonu | — (deklarowane, bez okna dialogowego) | przypomnienia wrócą po otwarciu aplikacji |

**Nie żądamy** dostępu do pamięci, kontaktów, lokalizacji, kalendarza ani mikrofonu.
Zdjęcia wybierasz systemowym selektorem (`PickVisualMedia`), więc nie potrzebujemy szerokiego
dostępu do plików.

## 4. Przetwarzanie na urządzeniu

* **Kody kreskowe**: ML Kit Barcode Scanning z **modelem wbudowanym w APK** — klatki z aparatu są
  analizowane lokalnie i nigdzie nie wysyłane.
* **OCR dat**: ML Kit Text Recognition (model łaciński, wbudowany) — rozpoznanie tekstu lokalne.
  Zdjęcie zrobione na potrzeby OCR jest usuwane z pamięci podręcznej natychmiast po zdekodowaniu
  (`file.delete()` w `OcrScreen.captureAndRecognize`).
* **Logika dat i przypomnień**: wyłącznie kod w `domain/` i `notifications/`, bez usług zewnętrznych.

## 5. Biblioteki firm trzecich a dane

| Biblioteka | Czy ma dostęp do sieci w tej aplikacji | Uwagi |
| --- | --- | --- |
| ML Kit (barcode-scanning, text-recognition) | **nie** — brak `INTERNET` w manifeście | używamy wariantów bundled, więc modele są w APK i nie są pobierane |
| CameraX | nie | tylko podgląd i zdjęcie |
| Room, DataStore, WorkManager, Navigation, Lifecycle | nie | biblioteki lokalne |
| Coil | nie | wczytuje wyłącznie pliki lokalne (zdjęcia z katalogu aplikacji) |
| Kotlin Coroutines / Serialization | nie | — |
| Desugar JDK libs | nie | — |

Nie dodajemy własnej analityki ani crash-reportingu. Nie ma SDK reklamowych.

## 6. Powiadomienia

Treść powiadomienia jest generowana lokalnie i zawiera nazwę pozycji oraz jej termin
(`notification_title_lead`, `notification_body`). Nie wysyłamy nic na zewnątrz.
Android nie gwarantuje przypomnienia co do sekundy — gdy alarmy dokładne są zablokowane,
stosujemy okno ±15 minut i pokazujemy to w ustawieniach.

## 7. Kopia zapasowa — co zawiera i jak jest chroniona

Plik ZIP wskazywany przez Ciebie zawiera:

```
manifest.json          wersja formatu, identyfikator aplikacji, data utworzenia, strefa czasowa,
                       liczby rekordów, lista plików z sumami SHA-256 i rozmiarami
items.json             pozycje z partiami, ilościami, terminami, typem daty i źródłem daty
barcode_names.json     słownik kod → nazwa
shopping.json          lista zakupów
settings.json          ustawienia
reminder_log.json      historia wysłanych przypomnień
photos/<uid>.jpg       zdjęcia produktów
```

Import traktuje plik jako **niezaufany** (`BackupValidator`, `BackupRepository`):

* biała lista ścieżek — odrzucamy `../`, ścieżki absolutne, separatory Windows, litery dysku, bajt NUL,
  nazwy spoza listy (ochrona przed zip-slip),
* limity: 400 MB archiwum, 20 MB na plik JSON, 15 MB na zdjęcie, 20 000 wpisów, 50 000 pozycji,
* ochrona przed bombą ZIP (współczynnik kompresji ≤ 200),
* porównanie sum SHA-256 z manifestem,
* odrzucenie kopii z nowszego formatu i kopii innej aplikacji,
* sanityzacja tekstu (długość, znaki sterujące) i liczb (ujemne/NaN/absurdalne odrzucane),
* daty poza wiarygodnym zakresem (dziś −20 lat … +60 lat) są pomijane z ostrzeżeniem,
* przed importem widzisz podgląd zawartości i wybierasz tryb: **zastąp wszystko** albo **dołącz**.

## 8. Usuwanie danych

* **„Usuń wszystkie dane"** w ustawieniach: kasuje pozycje, partie, zdjęcia, słownik kodów, listę
  zakupów, historię i plan przypomnień oraz ustawienia; odwołuje też wszystkie alarmy i powiadomienia.
* **Odinstalowanie aplikacji** usuwa wszystko, co przechowujemy (dane są wyłącznie w katalogu prywatnym).
* Kopię zapasową usuwasz sam — to zwykły plik we wskazanym przez Ciebie miejscu.

## 9. Dane wrażliwe

Apteczka zawiera informacje, które mogą zdradzać stan zdrowia. Dlatego:

* zostają wyłącznie na urządzeniu (brak sieci, brak konta, brak serwera),
* aplikacja **nie udziela porad medycznych ani nie sugeruje dawkowania** (zastrzeżenie widoczne
  w sekcji Apteczka i w formularzu),
* nie łączymy danych leków z żadną zewnętrzną bazą.

Podobnie z żywnością: data to wskazówka organizacyjna — przydatność do spożycia oceniasz samodzielnie
(zapach, wygląd, smak), a aplikacja tego nie robi za Ciebie.

## 10. Jak to sprawdzić samodzielnie

```bash
# uprawnienia w zbudowanym APK (musi brakować android.permission.INTERNET)
"$ANDROID_HOME/build-tools/<wersja>/aapt2" dump permissions terminownik-release.apk

# manifest i flaga debuggable
apkanalyzer manifest print terminownik-release.apk | grep -E "uses-permission|debuggable"

# kod: usunięcie uprawnień sieciowych
grep -n "tools:node=\"remove\"" app/src/main/AndroidManifest.xml

# kod: usuwanie zdjęcia po OCR
grep -n "file.delete()" app/src/main/kotlin/pl/terminownik/app/ui/screens/OcrScreen.kt
```
