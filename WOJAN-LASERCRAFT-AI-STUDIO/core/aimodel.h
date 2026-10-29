// aimodel.h — neuronowy generator wzorów (własny model wytrenowany dla tej aplikacji).
//
// Model: warunkowany autoregresyjny GRU (2 warstwy, 160 ukrytych) z wyjściem MDN
// (mieszanina 10 Gaussów) i kategorią stanu pióra. Opis projektu (prompt + kategoria,
// styl, produkt, detal, typ grawerowania) wchodzi jako stan początkowy GRU.
// Wagi: modele/ai-generator.bin (format WOJANAI1), wygenerowane przez
// tools/train_generator.py na danych z tools/export_training.cpp (ten sam silnik).
#ifndef WOJAN_AIMODEL_H
#define WOJAN_AIMODEL_H

#include <string>
#include <vector>

#include "common.h"

namespace lc {
namespace aimodel {

struct Info {
  bool available = false;    // znaleziono plik wag i udało się go wczytać
  std::string path;
  long long bytes = 0;
  int hidden = 0, layers = 0, nMix = 0, condDim = 0;
  int params = 0;            // liczba parametrów
  std::string version;       // np. "WOJANAI1"
  std::string error;
};

Info status();
std::string modelDir();                    // katalog „modele” (obok programu lub %LOCALAPPDATA%)
std::string engineInfo();                  // jednozdaniowy opis do zakładki Diagnostyka

// Generuje kreski rysunku. Współrzędne w [0,1] (y w dół), gotowe do wpisania w obszar.
bool generate(const std::string& prompt, const std::string& category, const std::string& style,
              const std::string& product, const std::string& detail, const std::string& engrave,
              uint32_t seed, std::vector<std::vector<Vec2>>& strokes, std::string& err);

}  // namespace aimodel
}  // namespace lc

#endif
