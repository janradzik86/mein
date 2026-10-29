// stt.h — lokalne rozpoznawanie mowy (whisper.cpp), model z katalogu „modele”.
//
// Silnik jest wkompilowany w aplikację (biblioteka whisper.cpp + ggml). Plik wag
// (ggml-*.bin) NIE jest wbudowany w instalator, bo w środowisku budowania nie ma
// dostępu do serwerów z wagami (wszystkie znalezione kopie w repozytoriach to
// wskaźniki Git LFS). Aplikacja wykrywa wagę w katalogu „modele” obok programu
// i dopiero wtedy udostępnia transkrypcję — nie udajemy, że model istnieje.
#ifndef WOJAN_STT_H
#define WOJAN_STT_H

#include <string>
#include <vector>

namespace stt {

struct Status {
  bool engine = true;      // silnik whisper.cpp wkompilowany (zawsze true)
  bool model = false;      // znaleziono plik wag
  std::string model_path;  // ścieżka do wag
  std::string model_name;  // np. "ggml-base-q5_1.bin"
  long long model_bytes = 0;
  std::string katalog;     // katalog przeszukiwany
};

// Katalog modeli: <katalog programu>/modele, potem %LOCALAPPDATA%\WOJAN LaserCraft AI\modele
std::string model_dir();

// Szuka pliku ggml-*.bin w katalogu modeli (największy, jeśli jest kilka).
Status status();

// Wczytuje WAV 16-bit PCM mono/stereo, konwertuje do 16 kHz mono float.
// Zwraca false i ustawia err, jeśli plik nie jest czytelnym WAV PCM.
bool read_wav_16k(const std::string& path, std::vector<float>& pcm, std::string& err);

// Transkrypcja pliku WAV. language: "pl", "en" albo "auto".
// Zwraca tekst (może być pusty); ok=false + err przy błędzie (brak modelu, zły plik).
bool transcribe_wav(const std::string& wav_path, const std::string& language,
                    std::string& text, std::string& err, double* sekundy = nullptr);

// Informacja diagnostyczna: wersja silnika i backendy (do zakładki Diagnostyka).
std::string engine_info();

}  // namespace stt

#endif
