# 1.0.9 — MODEL JEST W ŚRODKU. Rozpoznawanie mowy działa offline.

Data: 2026-10-29 · versionCode 10 · poprzednia wersja: 1.0.8 (9)

## Najważniejsze: prawdziwy model językowy w instalatorze

W katalogu `modele\` instalatora leży **`ggml-base-q5_1.bin` (59 707 642 B, 56,9 MiB)** —
kompletny model Whisper base: 74 mln parametrów, 99 języków (w tym polski), kwantyzacja q5_1.
Silnik `whisper.cpp 1.7.4` jest wkompilowany w `WOJAN-LaserCraft-AI.exe`.
Razem: **rozpoznawanie mowy działa od razu po instalacji, bez internetu i bez kluczy API.**

### Skąd wziął się model (szczegóły, bez ogólników)

W poprzednim wydaniu pisałem, że wag nie ma jak pobrać (Hugging Face nieosiągalny,
kopie na GitHubie to wskaźniki Git LFS po 131 B). To była prawda, ale nie cała —
wagi były dostępne w **pakietach PyPI**. Znalazłem je skryptem, który przejrzał
indeks PyPI (47 MB, 2 633 kandydatów) i sprawdził nagłówki 194 plików > 20 MB:

* `gympulse-whisper-base-part1` (66,3 MB) + `part2` (67,1 MB) — model Whisper base
  w kontenerze CTranslate2 (`WhisperSpec`), źródło: `Systran/faster-whisper-base` (MIT),
  wagi OpenAI Whisper (MIT),
* po złączeniu: `model.bin` 145 217 532 B, 212 tensorów, fp16.

Napisałem konwerter tego formatu na ggml: **`tools/convert_whisperspec.py`**.
Format kontenera nie jest opisany w internecie, więc odtworzyłem go z danych:
nagłówek `WhisperSpec`, wpisy `[u16 len][nazwa+NUL][u8 rank][u32 dims][u8 dtype][u32 pole][dane]`,
dtype 4 = fp16, 2 = int16, 1 = int8. Nazwy tensorów to oryginalna konwencja
TensorFlow/CTranslate2 (`decoder/layer_0/attention/layer_norm/gamma`), a whisper.cpp
oczekuje `decoder.blocks.0.cross_attn_ln.weight` — konwerter mapuje 212 tensorów na 263
tensory ggml, rozbijając sklejone QKV (1536 = 3×512) i KV cross-attention (1024 = 2×512).
Filtry mel (80×201) liczy `librosa` — porównane z wzorcowym plikiem whisper.cpp
`models/for-tests-ggml-base.bin`: **różnica maksymalna 0.0**.

### Dowód, że model naprawdę rozpoznaje mowę

Test wykonawczy tym samym kodem, który jest w aplikacji (`core/stt.cpp`):

```
plik: samples/jfk.wav (język en)
wynik: "And so my fellow Americans, ask not what your country can do for you,
        ask what you can do for your country."
czas: 2,68 s (model q5_1, 4 wątki CPU, AVX2)
```

Polski — nagranie syntezowane lokalnie (espeak-ng, głos `pl`, 8,6 s):

```
tekst wejściowy: "Cześć, tu Wojan Laser Craft. Projektuję tabliczkę z grawerem dla żony.
                  Piersiówka, stal nierdzewna, dwadzieścia centymetrów."
rozpoznane:      "Cześć, tu boją dla serkraft, projektuje tam liczkę trawerem dla żony,
                  nieprzówka, stalię ze mój a nieścia centymetrów."
czas: 3,70 s
```

Rozpoznanie polszczyzny działa (diakrytyki, słowa dziedzinowe), jakość jest ograniczona
głosem syntetycznym i modelem `base` — na prawdziwym nagraniu z mikrofonu będzie lepsza.
To nadal **nie jest** test na Twoim głosie; nie mam mikrofonu w środowisku budowania.

## Co jeszcze zmienione

* wnioski z 1.0.8: build APK nie wywala się już na brakującym `loguru` (krok opcjonalny),
* APK 1.0.9 / versionCode 10, podpisany `apksig` (v2+v3), zipalign OK,
* interfejs: komunikat awaryjny mówi teraz wprost, że model jest dołączony,
* instalator zawiera `modele\ggml-base-q5_1.bin` + `modele\README-MODEL.txt`,
* `dist/README-INSTALACJA.txt` i `MODELE-I-LICENCJE.md` opisują realny stan.

## Czego nadal nie ma (bez ukrywania)

* **Modelu językowego (LLM) do generowania opisów** — „Generator AI” to wciąż silnik
  proceduralny + parser polszczyzny, nie sieć neuronowa. Na PyPI są wagi GGUF
  (`gemma3-270m-q4-k-m-gguf`, 4 części, ~238 MB), ale to 4× więcej niż budżet tego
  wydania; to materiał na 1.1.0 (osobna decyzja: +238 MB instalatora).
* **STT na Androidzie** — APK nie ma silnika whisper (brak NDK/kompilatora ARM
  w środowisku), więc model w APK byłby bezużyteczny. Mikrofon na telefonie nagrywa
  dźwięk i prosi o wpisanie tekstu.
* **Uruchomienie EXE i Setup.exe na Windows** — nie zweryfikowane wykonawczo (brak
  Windows w środowisku). Sprawdzone: kompilacja, linkowanie, koperta instalatora,
  sumy kontrolne, poprawne wczytanie modelu przez ten sam kod na Linuksie.
* **Instalacja APK na telefonie** — nadal niezweryfikowana wykonawczo.
