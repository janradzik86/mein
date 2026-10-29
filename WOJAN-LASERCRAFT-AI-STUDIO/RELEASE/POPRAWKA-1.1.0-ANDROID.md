# POPRAWKA 1.1.0 (Android) — aplikacja nie umiera już po cichu

Data: 2026-10-29 · versionCode 11 · plik: `WOJAN-LaserCraft-AI-Android-Release.apk` (832 990 B)
SHA-256: `417cbb8fd8b2e60645e2a84882e88e14f64a9f297c0fb329013da83948c818a8`

## Co jest nowe

1. **Watchdog** (`pl.wojan.lasercraft.Watchdog`, klasa `Application`): przechwytuje błąd
   jako pierwsza rzecz w procesie — także taki, który zdarza się **przed** utworzeniem okna.
   Pełny stos wywołań zapisuje do `crash.txt` w katalogu aplikacji.
2. **Ekran raportu przy następnym uruchomieniu**: jeśli poprzedni start się wysypał,
   program pokazuje **od razu raport** (nie interfejs) — z nazwą pakietu, wersją, modelem
   telefonu i całym stosem wywołań. Raport jest **automatycznie kopiowany do schowka**,
   więc wystarczy wkleić go w WhatsApp. Po pokazaniu raportu plik jest usuwany, żeby
   ekran nie wracał w kółko.
3. **Nagłówek ekranu błędu** zawiera teraz `pl.wojan.lasercraft` — od razu widać, która
   aplikacja się wysypała (wcześniej był tam tylko numer wersji 1.0.6, co myliło).
4. **Odbiornik insetów** łapie `Throwable`, nie tylko `Exception` — błąd w tym miejscu
   nie kończy już procesu.
5. Kontrola zgodności z **Androidem 16 (API 36) i 17 (API 37)**: każde z 6 263 wywołań
   metod sprawdzone względem `android.jar` tych wersji — 0 brakujących metod.

## Ważne: NOWY PODPIS (trzeba odinstalować starą wersję)

Klucz podpisujący poprzedniego wydania przepadł razem ze środowiskiem budowania.
To wydanie jest podpisane nowym kluczem:

* właściciel: `CN=WOJAN LaserCraft AI, O=WOJAN, C=PL`
* klucz: RSA 4096, ważny 30 lat
* SHA-256 certyfikatu: `52:53:AC:1B:C2:AD:4C:F7:B7:1A:86:85:B3:32:46:4C:E7:4B:8F:F2:A9:0A:94:0D:27:65:3E:A7:54:2C:DD:CB`

Skutek: **telefon nie zainstaluje 1.1.0 „na wierzch” starej wersji** (różny podpis).
Najpierw odinstaluj starą aplikację (Ustawienia → Aplikacje → WOJAN LaserCraft AI →
Odinstaluj), potem zainstaluj nowy plik. Od tego wydania podpis jest stały i kolejne
wersje będą się instalować jako aktualizacja.

Klucz i hasło są w `/home/user/toolchain/keystore/` (poza repozytorium) oraz
w `keystore/keystore.env` w repozytorium — do przekazania właścicielowi.

## Czego to wydanie NIE robi

* **Nie naprawia jeszcze nieznanego błędu startu** — nie znam jego treści, a bez stosu
  wywołań każda „poprawka” byłaby zgadywaniem. To wydanie ma ten stos **wydobyć**.
* Ekran raportu jest jedyną nową funkcją; reszta programu bez zmian.

## Co zrobić na telefonie (dwie minuty)

1. Odinstaluj starą wersję (różny podpis, patrz wyżej) i oba programy o podobnej nazwie.
2. Zainstaluj `WOJAN-LaserCraft-AI-Android-Release.apk` (0,83 MB).
3. Otwórz **WOJAN LaserCraft AI**.
   * **Jeśli pokaże się interfejs z 12 zakładkami** — działa; napisz mi to, bo wtedy
     problem leżał w tamtych dwóch programach, nie w naszym.
   * **Jeśli się zamknie** — otwórz aplikację **drugi raz**. Pojawi się ekran
     „błąd uruchamiania” z pełnym stosem (i ten sam tekst w schowku).
     Zrób zrzut ekranu (albo wklej tekst) i przyślij. Z tym stosem naprawa jest
     kwestią jednej poprawki, bo będzie w nim konkretna linia kodu.

## Kontrola techniczna tego pliku (wykonana)

```
apk_installcheck: 0 problemów blokujących (podpis v1/v2/v3 — apksig Google)
apk_guard:        brak Capacitora, brak WebView, brak registerReceiver, własny rdzeń
dexcheck:         574 code_item, 0 błędów
dexlink:          153 klas, 6 263 wywołań — 0 problemów (API 34, 36 i 37)
dexverify:        0 naruszeń weryfikacji ART
```
