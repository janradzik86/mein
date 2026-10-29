MODEL ROZPOZNAWANIA MOWY — WOJAN LaserCraft AI Studio 1.0.9
============================================================

W TYM KATALOGU JEST PELNY MODEL JEZYKOWY DLA ROZPOZNAWANIA MOWY:

    ggml-base-q5_1.bin   59,7 MB   (Whisper base, skwantyzowany q5_1, 99 jezykow, w tym polski)

Silnik whisper.cpp 1.7.4 jest wkompilowany w plik WOJAN-LaserCraft-AI.exe.
Model lezy obok programu, wiec rozpoznawanie mowy dziala od razu — bez internetu,
bez kluczy API i bez pobierania czegokolwiek.

JAK SPRAWDZIC
-------------
1. Uruchom WOJAN-LaserCraft-AI.exe.
2. Zakladka „Mikrofon i prompt” — na gorze powinno byc zielone:
   „Silnik mowy: whisper.cpp 1.7.4 (wbudowany) - model: ggml-base-q5_1.bin (56.9 MB)”.
3. NAGRAJ wypowiedz, potem ROZPOZNAJ MOWE (offline).

JAKOSC I CZAS
-------------
* Model base (74 mln parametrow) — dobra jakosc dla polskiego, ~3-8 s na 10 s nagrania
  na typowym laptopie (CPU, bez karty graficznej).
* Wymaga ok. 400-500 MB wolnej pamieci RAM podczas pracy.
* Zamiana na mocniejszy model: wloz inny plik ggml-*.bin (np. ggml-small-q5_1.bin, 182 MB)
  i usun obecny — program wybiera najwiekszy plik ggml-*.bin z tego katalogu.

LICENCJE
--------
* Wagi: Whisper (OpenAI) — licencja MIT, uzycie komercyjne dozwolone.
* Konwersja do ggml: whisper.cpp (ggerganov) — MIT.
* Plik powstal przez konwersje wag CTranslate2 (Systran/faster-whisper-base, MIT)
  do formatu ggml narzedziem tools/convert_whisperspec.py z tego repozytorium.
