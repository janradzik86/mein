# MODELE I LICENCJE — WOJAN LaserCraft AI Studio 1.0.5

Ten dokument mówi dokładnie, **co liczy projekty w programie**, czym to jest, na jakiej
licencji i czego w środku **nie ma**. Zasada nadrzędna: nie nazywamy „modelem AI” czegoś,
czym nie jest, i nie deklarujemy trenowania modelu, którego nie trenowaliśmy.

## 1. Co realnie pracuje w aplikacji

| Warstwa | Co to jest | Gdzie w kodzie | Licencja |
|---|---|---|---|
| Analiza polecenia (NLU) | Deterministyczny parser tekstu polskiego: słownik produktów (50 kategorii), wymiarów (cm/mm/cal), materiałów, stylów, intencji cięcia i grawerowania, obsługa polskich znaków diakrytycznych i odmiany | `core/nlu.cpp`, `android/.../Core.java` | kod własny, patrz pkt 4 |
| Generator kompozycji | Proceduralny generator wektorowy: siatki, ramki, rozety, ornamenty, herby, emblematy branżowe, układy tekstu, wyrównania i marginesy produktu | `core/design.cpp`, `core/compose.cpp`, `core/emblems.cpp` | kod własny |
| Napisy | Bank fraz i szablonów (10 propozycji na zapytanie) + korekta tekstu (spacje, wielkie litery, diakrytyki) | `core/textgen.cpp` | kod własny |
| Kształty i czcionki | Czcionki wektorowe TTF renderowane do krzywych (własny parser TTF) | `core/ttf.cpp`, `fonts/*.ttf` | czcionki: SIL OFL 1.1 (pkt 3) |
| Obraz → wektor | Progowanie, dithering, binaryzacja, wektoryzacja konturów (marching squares + uproszczenie Douglasa-Peuckera) | `core/raster.cpp`, `core/path.cpp` | kod własny |
| QR | Własny generator QR (wersje 1–40, poziomy L/M/Q/H, maski, RS) + niezależny dekoder używany jako self-test | `core/qr.cpp` | kod własny |
| Walidacja przed laserem | Reguły geometrii: obrys produktu, marginesy, kerf użytkownika, warstwy cięcia vs grawerowania, blokada PVC i nieznanych tworzyw | `core/validate.cpp` | kod własny |
| Eksport | SVG (profil LightBurn), DXF R12 (mm), PDF, PNG/JPG/BMP, G-code (tylko GRBL), ZIP/pakiet produkcyjny | `core/export.cpp` | kod własny |

Wszystko powyższe działa **lokalnie i offline**, w tym samym kodzie na Windows i Androidzie
(wspólny rdzeń `core/` + port Java dla APK — te same reguły, ten sam format projektu).

## 2. Czego w programie NIE MA (świadomie, bez ukrywania)

* **Nie ma sieci neuronowej ani wag modelu** — żadnego LLM, żadnego modelu dyfuzji obrazu.
  „Generator AI” to opisany wyżej deterministyczny generator proceduralny + parser języka.
  To znaczy: te same dane wejściowe dają zawsze ten sam projekt (test T27), nie ma
  halucynacji, ale też nie ma „rozmowy” z modelem językowym.
* **Nie ma lokalnego rozpoznawania mowy.** Zakładka „Mikrofon i prompt” nagrywa dźwięk
  (WAV 16 kHz mono) i pokazuje pole na wpisanie treści; transkrypcja nie jest wykonywana
  na urządzeniu. whisper.cpp **nie jest** dołączony — brakuje go w tym wydaniu i jest to
  jedyny sposób, w jaki moglibyśmy to zrobić uczciwie: model `ggml-base` (~150 MB) plus
  biblioteka pod ARM64 i x86_64. Rozmiar APK wzrósłby z 0,8 MB do ~110 MB.
* **Nie ma modelu do generowania zdjęć.** Import zdjęcia robi przetwarzanie obrazu
  (progowanie/wektoryzacja), nie syntezę.
* **Nie ma sterowania laserem** ani G-code dla kontrolerów innych niż GRBL — pliki wgrywa
  użytkownik w LightBurn/LaserGRBL.

## 3. Czcionki (jedyne zewnętrzne pliki binarne w wydaniu)

Dołączone TTF: Anton, Abril Fatface, Barlow Condensed, Barlow SemiCondensed, Bebas Neue,
Cinzel, Cinzel Decorative, Oswald, Pirata One, Playfair Display.

* Wszystkie pochodzą z Google Fonts i są wydane na **SIL Open Font License 1.1**, która
  pozwala używać ich w aplikacjach komercyjnych, dołączać do instalatora i osadzać
  w dokumentach. Nie modyfikujemy plików czcionek.
* Ograniczenie, o którym trzeba wiedzieć: w repozytorium **nie ma plików tekstowych licencji
  OFL** dla poszczególnych czcionek. Ten dokument jest oświadczeniem o pochodzeniu i licencji;
  przed publikacją sklepową należy dołączyć `OFL.txt` z każdego źródła (Google Fonts).

## 4. Licencja kodu

Kod programu (rdzeń C++, interfejs ImGui, port Android, narzędzia budowania) jest
**własnością zleceniodawcy** — WOJAN LaserCraft AI Studio, przekazany wraz z wydaniem.
Biblioteki zewnętrzne w kodzie źródłowym:

| Biblioteka | Wersja | Licencja | Użycie |
|---|---|---|---|
| Dear ImGui | 1.90.x | MIT | interfejs Windows |
| stb_image / stb_image_write | public domain | MIT/PD | dekodowanie i zapis obrazów |
| Zig (kompilator) | 0.13/0.14 | MIT | wyłącznie narzędzie budowania, nie trafia do programu |

Aplikacja Android nie zawiera żadnych bibliotek firm trzecich — wszystkie klasy to kod
własny + platforma Android.

## 5. Dlaczego nie dołączyliśmy „prawdziwego” LLM

Wymagania: model miał działać offline, po polsku, w rozmiarze mieszczącym się w instalatorze
i w pamięci telefonu. Kandydaci i powody odrzucenia:

| Model | Rozmiar (Q4) | Dlaczego nie |
|---|---|---|
| Qwen2.5-1.5B-Instruct | ~1,0 GB | jakość polskiego dostateczna, ale 1 GB w APK + ~1,5 GB RAM w telefonie — poza budżetem tego wydania |
| Llama-3.2-1B-Instruct | ~0,8 GB | licencja Llama (ograniczenia komercyjne, wymóg nazwy „Built with Llama”), słabszy polski |
| Bielik-1.5B ( SpeakLeash) | ~1,0 GB | najlepszy polski w tej klasie, licencja Apache-2.0 — **kandydat nr 1** na kolejne wydanie |
| Phi-3-mini | ~2,3 GB | za duży na telefon |
| whisper.cpp base | ~150 MB | jedyny realny wybór na STT offline — poza tym wydaniem, patrz pkt 2 |

Decyzja na to wydanie: generator proceduralny (0 MB modeli, natychmiastowy start, brak
halucynacji, pełna kontrola geometrii). Kolejny krok, jeśli zleceniodawca zaakceptuje
rozmiar instalatora: Bielik-1.5B-Instruct (Apache-2.0) jako opcjonalny dodatek
„AI rozmowne”, pobierany wyłącznie na życzenie — nigdy jako warunek działania programu.

## 6. Odtwarzalność przykładów

Wszystkie 26 projektów w `dist/przyklady` i w instalatorze powstało **z tego samego silnika**
(`tools/make_samples.py` → CLI aplikacji). Nie ma w nich grafiki wygenerowanej przez model
obrazu ani cudzych wzorów. Każdy plik ma obok siebie plik `_walidacja.txt` z wynikiem
„SPRAWDŹ PRZED LASEREM”.

---

Wersja dokumentu: 1.0.5, 2026-10-29.
