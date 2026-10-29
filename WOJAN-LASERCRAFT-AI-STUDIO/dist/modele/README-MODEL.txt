MODEL ROZPOZNAWANIA MOWY (whisper.cpp) — WOJAN LaserCraft AI Studio
==================================================================

Silnik rozpoznawania mowy jest WBUDOWANY w program (whisper.cpp 1.7.4 skompilowany
do pliku WOJAN-LaserCraft-AI.exe). Brakuje tylko pliku z wagami modelu, bo serwery
z wagami (Hugging Face) byly zablokowane w srodowisku, w ktorym budowalismy wydanie.
Nie udajemy, ze model jest w srodku — aplikacja wyraznie pokazuje, czy wagi widzi.

JAK WLACZYC ROZPOZNAWANIE MOWY (2 minuty, jednorazowo)
-----------------------------------------------------
1. Kliknij dwukrotnie:  pobierz_model.bat   (w tym katalogu)
   Skrypt pobierze plik ggml-base-q5_1.bin (59,7 MB) i sprawdzi jego rozmiar.
   Mozesz tez pobrac recznie z przegladarki:
   https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin
2. Wloz pobrany plik do TEGO katalogu („modele”).
3. Uruchom program i otworz zakladke „Mikrofon i prompt”.
   Nagraj wypowiedz i nacisnij ROZPOZNAJ MOWE (offline).

Model base (q5_1, skwantyzowany) obsluguje jezyk polski, zajmuje 59,7 MB na dysku
i ok. 400-500 MB pamieci RAM podczas pracy. Rozpoznawanie odbywa sie w calosci na
Twoim komputerze — bez internetu, bez kluczy API, dzwiek nie opuszcza komputera.

LICENCJA MODELU
---------------
Wagi Whisper (OpenAI) sa udostepnione na licencji MIT. Pliki ggml dla whisper.cpp
przygotowuje projekt ggerganov/whisper.cpp (MIT). Mozesz ich uzywac komercyjnie.

INNE MODELE (opcjonalnie)
-------------------------
- ggml-small-q5_1.bin (~182 MB) — wyzsza jakosc, wolniejszy
- ggml-tiny-q5_1.bin (~31 MB)  — najszybszy, gorsza jakosc
Program wybiera najwiekszy plik ggml-*.bin z tego katalogu.
