// ai_preview.cpp — podgląd próbek z modelu neuronowego (do oceny jakości i dokumentacji).
// Wypisuje JSON: {"prompt":..., "strokes":[[[x,y],...],...]}
// Budowanie: g++ -std=c++17 -O2 -Icore tools/ai_preview.cpp build/libwojan.a -o build/ai_preview -lpthread
#include "aimodel.h"
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

int main(int argc, char** argv) {
  const char* prompt = argc > 1 ? argv[1] : "mandala";
  const char* cat = argc > 2 ? argv[2] : "natura";
  const char* style = argc > 3 ? argv[3] : "rycina";
  unsigned seed = argc > 4 ? (unsigned)std::atoi(argv[4]) : 1;
  std::vector<std::vector<lc::Vec2>> st;
  std::string err;
  bool ok = lc::aimodel::generate(prompt, cat, style, "tabliczka", "sredni", "line", seed, st, err);
  std::printf("{\"ok\":%d,\"err\":\"%s\",\"prompt\":\"%s\",\"strokes\":[", ok ? 1 : 0, err.c_str(), prompt);
  for (size_t i = 0; i < st.size(); i++) {
    std::printf("%s[", i ? "," : "");
    for (size_t j = 0; j < st[i].size(); j++)
      std::printf("%s[%.4f,%.4f]", j ? "," : "", st[i][j].x, st[i][j].y);
    std::printf("]");
  }
  std::printf("]}\n");
  return ok ? 0 : 2;
}
