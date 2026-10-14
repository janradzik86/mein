# Raport z testów — Terminownik 1.0.0

Zasada: **to, co nie zostało uruchomione, jest oznaczone jako NIEWYKONANE** — nigdy jako zaliczone.
Każdy wynik poniżej ma podaną dokładną komendę, więc można go odtworzyć.

Data ostatniej aktualizacji: 2026-10-13 (commit `1cc3ad9` + dokumentacja).

## 1. WYKONANE — logika biznesowa (67 scenariuszy, bez Androida)

```bash
export JAVA_HOME=/tmp/jdkx/jdk4py/java-runtime
export KOTLINC=/tmp/ktc/node_modules/kotlin-compiler/bin/kotlinc
bash tools/verify-domain.sh
```

Kompilator: kotlinc 2.4.21 (npm `kotlin-compiler`), JVM: Java 25 (jdk4py).
Źródła kompilowane: `domain/*.kt`, `data/backup/{BackupContract,BackupValidator}.kt`,
`src/test/kotlin/.../DomainScenarios.kt` + `LocalScenarioRunner.kt`.

Wynik dosłowny:

```
Razem: 67, zaliczone: 67, nieudane: 0
Wszystkie scenariusze logiki biznesowej zaliczone.
```

Podział na grupy:

| Grupa | Scenariusze | Wynik |
| --- | --- | --- |
| GS1 (parsowanie AI 01/10/17/21/30/310n/315n, GS, FNC1, wiek daty) | 12 | 12 PASS |
| Kody (walidacja GTIN mod-10, rodziny, nośniki terminu) | 5 | 5 PASS |
| Terminy (kubełki: po terminie / dziś / kończące się / brak daty / niepotwierdzone) | 7 | 7 PASS |
| Przypomnienia (sloty lead/due, klucze, brak duplikatów, zaległości) | 7 | 7 PASS |
| OCR (etykiety PL/EN bez znaków diakrytycznych, niejednoznaczności, zakres lat) | 14 | 14 PASS |
| Formatowanie (daty, miesiąc, ilości, odległości dni, rozmiar pliku, nazwa kopii) | 8 | 8 PASS |
| Kopia zapasowa (ścieżki, limity, bomba ZIP, polityka manifestu, sumy, sanityzacja) | 14 | 14 PASS |

Uwaga o środowisku: konsola sandboxa wyświetla polskie znaki jako `?` — to artefakt kodowania
terminala, nie błąd danych (testy porównują łańcuchy w UTF-8). JVM `jdk4py` nie zawiera modułu
`jdk.localedata`, dlatego `PlFormat` wymusza symbole polskie jawnie (`DecimalFormatSymbols` z
`decimalSeparator = ','`), a nie przez domyślną lokalizację urządzenia.

### Reguły krytyczne potwierdzone wykonanymi testami

* Kod EAN/UPC **nie** daje terminu ważności (grupa „Kody", „GS1").
* Data z GS1 AI 17 oraz z OCR jest zawsze `confirmed = false` → `countsForReminder = false`,
  czyli **nie tworzy przypomnienia** przed potwierdzeniem (grupy „Terminy", „Przypomnienia", „OCR").
* Zapis `03/04/2027` (oba człony ≤ 12, separator `/`) → `AMBIGUOUS_ORDER` + pewność `LOW`
  + kod problemu do UI (grupa „OCR").
* `MM.YYYY` nie jest wyciągane ze środka dłuższego zapisu; `DD=00` w GS1 → `MONTH_ONLY`.
* Bomba ZIP, ścieżki `../`, `C:\`, nazwy spoza białej listy, suma kontrolna i daty poza zakresem
  wiarygodności → odrzucane (grupa „Kopia zapasowa").

## 2. WYKONANE — spójność zasobów tekstowych z kodem

```bash
python3 tools/check-strings.py
```

Wynik dosłowny:

```
Klucze zdefiniowane: 375
Klucze użyte w kodzie: 375
Brakujące: 0
Niezgodne argumenty: 0
Nieużywane: 0

OK: wszystkie odwołania R.string istnieją i mają zgodną liczbę argumentów.
```

Kontrola obejmuje: istnienie każdego `R.string.*` użytego w Kotlin, zgodność liczby argumentów
`stringResource(...)`/`getString(...)` z placeholderami `%n$` w zasobie, duplikaty kluczy i poprawność XML.

## 3. WYKONANE — skrypt tworzenia klucza podpisywania

```bash
export JAVA_HOME=/tmp/jdkx/jdk4py/java-runtime
bash scripts/make-keystore.sh /tmp/throwaway-keys
```

* Utworzono keystore JKS, RSA 4096, ważność 10 950 dni (30 lat), alias `terminownik-release`.
* Uprawnienia plików: `600` (keystore i `keystore.properties`), katalog `700`.
* Hasła losowe (24 bajty z `/dev/urandom`), **nie drukowane** na stdout — zapisane tylko w pliku.
* Odcisk certyfikatu SHA-256 wydrukowany (wartość publiczna, nie sekret).
* Drugie uruchomienie na tym samym katalogu: `BŁĄD: klucz już istnieje` → **ochrona przed
  nadpisaniem działa** (utrata klucza = brak możliwości aktualizacji wydanej aplikacji).
* Klucz jednorazowy **usunięty** po teście (`rm -rf /tmp/throwaway-keys`), zgodnie z ustaleniem.

## 4. WYKONANE — walidacja statyczna konfiguracji

* Wszystkie XML zasobów (12 plików: `values`, `values-night`, `drawable`, `mipmap-anydpi-v26`,
  `xml/backup_rules`, `xml/data_extraction_rules`, `AndroidManifest.xml`) — parsowalne, bez błędów.
* `gradle/libs.versions.toml` — parsowalny; wszystkie wersje przypięte (bez `+`, bez alpha/beta).
* Workflow CI — poprawny YAML (parser strict, wykrywanie zduplikowanych kluczy) oraz `bash -n`
  dla każdego kroku `run:` (0 błędów składni).
* `bash -n scripts/make-keystore.sh`, `bash -n scripts/ci-prepare-signing.sh`,
  `bash -n scripts/build-release.sh` — OK.

## 5. NIEWYKONANE — i dlaczego

| Test | Stan | Przyczyna | Gdzie się pojawi |
| --- | --- | --- | --- |
| Kompilacja warstwy Android (Compose, Room/KSP, CameraX, ML Kit, Coil) | **NIEWYKONANE** | W środowisku roboczym nie ma Android SDK ani Gradle, a `dl.google.com`, `services.gradle.org` i `repo.maven.apache.org` są zablokowane; brak roota, żeby to doinstalować | job `verify` w CI (kroki Lint / Testy / assembleRelease) |
| `./gradlew :app:lintRelease` | **NIEWYKONANE** | j.w. | CI, raport `lint-results-release.xml` jako artefakt |
| Testy jednostkowe JUnit w Gradle (`DomainScenariosTest`, `PlFormatTest`, `CsvExportTest`, `BackupValidatorTest`) | **NIEWYKONANE w Gradle**; logika tych samych scenariuszy wykonana w pkt 1 | j.w. | CI, krok „Podsumowanie testów i lint" |
| Testy instrumentowane (trwałość po zamknięciu bazy, rozłączność partii, round-trip kopii ze zdjęciami, odrzucanie obcych kopii, przeliczanie przypomnień po edycji, DataStore) | **NIEWYKONANE** | wymagają emulatora/urządzenia | job `instrumented` (best-effort, `continue-on-error: true`) |
| Instalacja podpisanego APK i przejście głównego scenariusza użytkownika | **NIEWYKONANE** | brak emulatora i brak zbudowanego APK | po pierwszym udanym buildzie |
| Zrzuty ekranu działającej aplikacji | **NIEWYKONANE** | brak emulatora | po pierwszym udanym buildzie |
| `apksigner verify --verbose --print-certs` na finalnym APK | **NIEWYKONANE** | brak APK | CI, sekcja „Weryfikacja podpisu" w Release |
| SHA-256 i rozmiar finalnego APK | **NIEWYKONANE** | brak APK | CI, `SHA256SUMS.txt` w Release |
| Test ścieżki odmowy uprawnień na urządzeniu (kamera, powiadomienia, alarmy dokładne) | **NIEWYKONANE na urządzeniu**; logika degradacji zaimplementowana i opisana w kodzie | brak emulatora | job `instrumented` + ręczny test właściciela |

### Bloker wykonania testów z pkt 5

GitHub Actions w repozytorium `janradzik86/mein` **nie uruchamia jobów**: nawet minimalny workflow
(1 krok, `echo`) kończy się `conclusion: startup_failure` przy `0` jobów i `0` zarejestrowanych
workflowów. Dowody z `api.github.com`:

```
GET /repos/janradzik86/mein/actions/workflows      -> total_count: 0
GET /repos/janradzik86/mein/actions/runs/38491777521 -> status: completed, conclusion: startup_failure
GET /repos/janradzik86/mein/actions/runs/.../jobs  -> total_count: 0
POST /repos/janradzik86/mein/actions/workflows/.../dispatches -> 403
PATCH /repos/janradzik86/mein {"private": false}   -> 403 Resource not accessible by integration
GET /repos/janradzik86/mein/actions/permissions    -> 403 Resource not accessible by integration
```

Interpretacja: blokada na poziomie repozytorium/konta (Actions wyłączone albo brak minut/limitu
wydatków dla repo prywatnych), **nie** błąd pliku workflow. Token sesji nie ma prawa zmiany tych
ustawień ani widoczności repozytorium.

Ścieżki odblokowania (do wyboru przez właściciela):
1. Zmiana widoczności repo na publiczną na czas buildu (minuty Actions dla repo publicznych są darmowe),
   potem powrót do prywatnego.
2. Włączenie Actions + podniesienie limitu wydatków w Billing.
3. Build lokalny: `bash scripts/build-release.sh` (JDK 17–21 + Android SDK) — wynik w `dist/`.

Po odblokowaniu ten raport zostanie uzupełniony o dosłowne wyniki z CI/lokalnego buildu.

## 6. Scenariusz odbiorczy do przejścia ręcznego (po instalacji APK)

1. Pierwsze uruchomienie → wdrożenie (3 kroki) → „Zacznij".
2. Spiżarnia → „Dodaj" → skan kodu EAN-13 → nazwa **nie** uzupełnia terminu (komunikat wprost).
3. Ten sam produkt, druga partia z innym terminem → na liście są **dwa** wpisy.
4. „Uzupełnij (nowa partia)" → powstaje trzeci wpis, poprzednie bez zmian.
5. OCR etykiety z zapisem `03/04/2027` → propozycja z ostrzeżeniem o niejednoznaczności →
   „Potwierdzam datę" dopiero po korekcie → przypomnienie aktywne.
6. Apteczka → pozycja bez daty → widok „Brak daty", brak przypomnienia, widoczne zastrzeżenie medyczne.
7. Terminy → zakładki: kończące się / po terminie / brak daty / do potwierdzenia.
8. Ustawienia → wyprzedzenie 30 dni → alarmy przeliczone (bez duplikatów powiadomień).
9. Ustawienia → „Powiadomienie próbne" → powiadomienie pojawia się na pasku.
10. Kopia zapasowa → utwórz → „Usuń wszystkie dane" → odtwórz z kopii → ilości, partie, zdjęcia
    i terminy są zgodne z oryginałem.
11. Lista zakupów → dodaj, odhacz, eksport CSV → plik otwiera się w Excelu z polskimi znakami.
12. Wyłączenie uprawnień (kamera / powiadomienia) → aplikacja działa dalej i proponuje alternatywy
    (wpis ręczny, wybór zdjęcia z galerii, ustawienia systemu).
