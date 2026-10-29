# MODEL AI GENERUJĄCY WZORY — co to jest, jak powstało, jakie ma granice

Dokument dla zleceniodawcy. Bez marketingowej nowomowy: co jest w środku, na czym
wytrenowane, czego nie potrafi.

## 1. Co to za model

`modele/ai-generator.bin` — **własna sieć neuronowa wytrenowana od zera w tym projekcie**,
dedykowana wyłącznie do rysowania wzorów pod grawer laserowy.

* architektura: **warunkowany autoregresyjny GRU** (2 warstwy × 160 jednostek)
  z wyjściem **MDN** (mieszanina 10 rozkładów Gaussa) i kategorią stanu pióra,
  czyli rodzina modeli „sketch-RNN”;
* wejście warunkujące: opis projektu (prompt + kategoria + styl + produkt + detal +
  typ grawerowania) → wektor 128 liczb → stan początkowy GRU;
* wyjście: sekwencja ruchów pióra (dx, dy, koniec kreski) → **kreski wektorowe**;
* parametry: **284 533** (1,09 MB w float32), RAM ~12 MB, czas rysowania
  **kilkadziesiąt milisekund** na CPU, w pełni offline, deterministyczny przy tym samym ziarnie.

Nie jest to model dyfuzyjny ani LLM. Rysuje **wektorami**, a nie pikselami — bo laser
potrzebuje ścieżek, a nie rozmytej bitmapy. Bitmapę z modelu trzeba by wektoryzować
(progowanie + kontury), co psuje jakość i dodaje warstwę zgadywania.

## 2. Jak powstał (pełna droga, do odtworzenia)

1. **Dane** — `tools/export_training.cpp` uruchamia **ten sam silnik** (`lc::Engine::generate`),
   którym aplikacja rysuje projekty, i dla 5 043 losowych zestawów (motyw × produkt × styl ×
   detal × typ grawerowania) wyciąga ścieżki wektorowe z warstw `ENGRAVE_VECTOR`,
   `ENGRAVE_FILL`, `SCORE`. Wynik: `build/train/shard*.jsonl` (31 MB, 5 043 rysunki,
   średnio 320 punktów). Nie użyto ani jednego cudzego pliku.
2. **Model** — `tools/train_generator.py` (PyTorch, CPU): inicjalizacja losowa,
   14 epok, Adam (lr 2e-3), funkcja straty = NLL mieszaniny Gausów + entropia krzyżowa
   stanu pióra, przycinanie gradientu 5.0, batch 64.
3. **Eksport** — wagi do `modele/ai-generator.bin` (format `WOJANAI1`, float32, nagłówek
   z konfiguracją) + `modele/ai-generator.json` (opis, lista tensorów).
4. **Wnioskowanie** — `core/aimodel.cpp`: wczytanie wag, wektor warunkujący, GRU,
   próbkowanie z mieszaniny, filtrowanie zdegenerowanych kresek, normalizacja do [0,1].
   Ten sam kod działa w aplikacji Windows; **bez Pythona, bez PyTorcha, bez GPU**.
5. **Wpięcie** — `core/compose.cpp`: rysunek z modelu trafia na warstwę `ENGRAVE_VECTOR`
   w obszarze emblematu, a ramka, napisy, kontur cięcia i reguły produktu (marginesy,
   „nie tnij wokół piersiówki”) zostają bez zmian — model rysuje tylko treść.

## 3. Licencja

Wagi i kod: własność zleceniodawcy (wytrenowane tutaj na danych wygenerowanych przez
własny silnik). Brak licencji zewnętrznych, brak zobowiązań wobec OpenAI/Meta/Google.
Wymagania: PyTorch (BSD) użyty wyłącznie jako narzędzie treningowe — nie jest częścią
produktu.

## 4. Czego model NIE potrafi (żeby nie było niespodzianek)

* Nie „rozumie” pojęć spoza danych treningowych — nowy, dziwny motyw da rysunek
  w stylu zbliżonym do znanych, nie dosłowną ilustrację.
* Nie pisze liter — napisy robi dotychczasowy generator tekstu (czcionki TTF).
* Rysuje maksymalnie 18 kresek na projekt (twardy limit wnioskowania).
* Przy nietypowych parametrach może narysować coś nieczytelnego — wtedy kompozycja
  wraca do emblematu z biblioteki (jawne w logu projektu), a nie udaje, że model dał radę.
* Jakość jest „szkicowa”, nie fotorealistyczna. To generator wzorów grawerskich,
  nie generator obrazów w sensie Stable Diffusion.
