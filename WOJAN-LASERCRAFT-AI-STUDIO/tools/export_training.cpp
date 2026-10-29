// export_training.cpp — zrzuca dane treningowe dla neuronowego generatora wzorów.
//
// Wykorzystuje TEN SAM silnik (lc::Engine::generate), który rysuje projekty w aplikacji:
// dla każdego przebiegu generuje projekt, wyciąga z niego ścieżki wektorowe (warstwy
// ENGRAVE_VECTOR / ENGRAVE_FILL / SCORE / CUT_*) i zapisuje je jako polilinie w JSONL.
//
// Użycie: build/export_training <liczba_projektów> <wyjście.jsonl> [seed0]
#include "core_api.h"

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

using namespace lc;

namespace {

struct Rng {
  uint64_t s;
  explicit Rng(uint64_t seed) : s(seed * 6364136223846793005ULL + 1442695040888963407ULL) {}
  uint32_t next() {
    s = s * 6364136223846793005ULL + 1442695040888963407ULL;
    return (uint32_t)(s >> 33);
  }
  int range(int n) { return n <= 0 ? 0 : (int)(next() % (uint32_t)n); }
  const std::string& pick(const std::vector<std::string>& v) { return v[range((int)v.size())]; }
};

const std::vector<std::string> kMotifs = {
    "wilk",      "kot",        "lis",       "sowa",       "jeleń",     "koń",      "ryba",      "ośmiornica",
    "smok",      "mandala",    "różа",      "drzewo",     "góry",      "latarnia", "statek",    "samolot",
    "motocykl",  "rower",      "gitarа",    "kubek",      "młot",      "topór",    "kompas",    "kotwica",
    "słońce",    "księżyc",    "gwiazdy",   "fala",       "kwiat",     "liść",     "czaszkа",   "serce",
    "piersiówka", "tabliczka", "medal",     "herb",       "monogram",  "labirynt", "skrzydła",  "piorun",
    "wędka",     "grzyb",      "las",       "wulkan",     "rakieta",   "zamek",    "tarcza",    "wąż",
    "pająk",     "nietoperz",  "kości",     "anioł",      "syrena",    "pegaz",    "feniks",    "tygrys",
    "niedźwiedź", "orzeł",     "żubr",      "bocian",     "karp",      "pszczoła", "motyl",     "ważka"};
const std::vector<std::string> kProducts = {"tabliczka", "piersiwka", "kubek",    "deska",
                                            "medal",     "brelok",    "zakladka", "szyld"};
const std::vector<std::string> kStyles = {"rycina", "retro", "minimal", "komiks", "gotyk", "geometryczny"};
const std::vector<std::string> kDetails = {"niski", "sredni", "wysoki"};
const std::vector<std::string> kEngrave = {"line", "fill", "fill+line"};
const std::vector<std::string> kCategories = {"hobby", "rodzina", "praca", "sport", "zwierzeta",
                                              "natura", "motoryzacja", "kuchnia", "podroze", "humor"};
const std::vector<std::string> kExtras = {"dla żony", "dla męża", "na urodziny", "na święta",
                                          "z imieniem", "ręcznie robione", "prezent", "na warsztat"};

// zamiana nazwy produktu z listy na id używane przez silnik
std::string productId(const std::string& p) {
  if (p == "piersiwka") return "piersiwka";
  if (p == "kubek") return "kubek";
  if (p == "deska") return "deska";
  if (p == "medal") return "medal";
  if (p == "brelok") return "brelok";
  if (p == "zakladka") return "zakladka";
  if (p == "szyld") return "szyld";
  return "tabliczka";
}

struct Stroke {
  std::vector<Vec2> pts;
};

std::string jsonStr(const std::string& s) {
  std::string o = "\"";
  for (char c : s) {
    if (c == '"' || c == '\\') { o += '\\'; o += c; }
    else if ((unsigned char)c < 32) { char b[8]; std::snprintf(b, sizeof b, "\\u%04x", c); o += b; }
    else o += c;
  }
  return o + "\"";
}

}  // namespace

int main(int argc, char** argv) {
  int count = argc > 1 ? std::atoi(argv[1]) : 1000;
  std::string outPath = argc > 2 ? argv[2] : "build/train/strokes.jsonl";
  int seed0 = argc > 3 ? std::atoi(argv[3]) : 1;

  Engine eng;
  makeDirs("build/train/data");
  eng.setDataDir("build/train/data");
  eng.setFontDir("fonts");

  FILE* f = std::fopen(outPath.c_str(), "wb");
  if (!f) { std::fprintf(stderr, "nie mogę zapisać %s\n", outPath.c_str()); return 1; }

  int written = 0, skipped = 0;
  long long totalPts = 0;
  for (int i = 0; i < count; i++) {
    Rng rng((uint64_t)(seed0 + i * 7919));
    std::string motif = rng.pick(kMotifs);
    std::string product = rng.pick(kProducts);
    std::string style = rng.pick(kStyles);
    std::string detail = rng.pick(kDetails);
    std::string engrave = rng.pick(kEngrave);
    std::string cat = rng.pick(kCategories);
    std::string extra = rng.pick(kExtras);
    std::string prompt = motif + " " + extra + " " + cat;

    Json req = Json::object();
    req.set("prompt", prompt);
    req.set("product", productId(product));
    req.set("style", style);
    req.set("detail", detail);
    req.set("engraveType", engrave);
    req.set("seed", (double)(seed0 * 100000 + i));
    Json r = eng.handle("generate", req);
    if (!r.has("design") || r.has("error")) { skipped++; continue; }
    Design d = Design::fromJson(r["design"]);

    // zbierz ścieżki rysunku (bez tekstów i bez obrazów rastrowych)
    std::vector<Stroke> strokes;
    double minx = 1e9, miny = 1e9, maxx = -1e9, maxy = -1e9;
    for (const Obj& o : d.objects) {
      if (o.type != "path" || !o.visible) continue;
      if (o.layer != LAYER_ENGRAVE_VECTOR && o.layer != LAYER_ENGRAVE_FILL && o.layer != LAYER_SCORE)
        continue;
      if (o.role == "guide") continue;
      // Ozdobniki kompozycji (ramki, wstęgi, separatory) pomijamy: są powtarzalne i w danych
      // dominowały nad treścią — model uczył się rysować prostokąt/wstęgę zamiast motywu.
      // Zostają wyłącznie ścieżki emblematu (nazwy typu "<motyw>_line0", "<motyw>_fill0").
      {
        static const char* kOzdobniki[] = {"Ramka", "ramka", "Wstęga", "Separator", "Kontur",
                                           "Obszar", "Napis", "Tło", "Tarcza tła"};
        bool ozdobnik = false;
        for (const char* k : kOzdobniki)
          if (o.name.find(k) != std::string::npos) { ozdobnik = true; break; }
        if (ozdobnik) continue;
      }
      Path p = Path::fromSVG(o.d);
      if (p.empty()) continue;
      p.apply(o.transform());
      for (const Contour& c : p.flatten(0.08)) {
        if (c.pts.size() < 2) continue;
        Stroke s;
        for (const Vec2& v : c.pts) {
          s.pts.push_back(v);
          if (v.x < minx) minx = v.x;
          if (v.y < miny) miny = v.y;
          if (v.x > maxx) maxx = v.x;
          if (v.y > maxy) maxy = v.y;
        }
        if (c.closed) s.pts.push_back(c.pts.front());
        strokes.push_back(std::move(s));
      }
    }
    double bw = maxx - minx, bh = maxy - miny;
    if (strokes.size() < 3 || bw < 5 || bh < 5) { skipped++; continue; }

    // normalizacja do [0,1]^2 (y w dół), ograniczenie liczby punktów
    int maxStrokes = 14;
    int stride = 1;
    int total = 0;
    for (auto& s : strokes) total += (int)s.pts.size();
    while (total / stride > 520) stride++;
    std::string line = "{\"prompt\":" + jsonStr(prompt) + ",\"product\":" + jsonStr(product) +
                       ",\"style\":" + jsonStr(style) + ",\"detail\":" + jsonStr(detail) +
                       ",\"engrave\":" + jsonStr(engrave) + ",\"category\":" + jsonStr(cat) +
                       ",\"strokes\":[";
    int used = 0;
    bool firstStroke = true;
    for (auto& s : strokes) {
      if (used >= maxStrokes) break;
      std::string body;
      int k = 0, n = 0;
      for (size_t j = 0; j < s.pts.size(); j += stride, k++) {
        double x = (s.pts[j].x - minx) / bw;
        double y = (s.pts[j].y - miny) / bh;
        if (x < -0.05 || x > 1.05 || y < -0.05 || y > 1.05) continue;
        char b[48];
        std::snprintf(b, sizeof b, "%s[%.4f,%.4f]", n ? "," : "", x, y);
        body += b;
        n++;
      }
      if (n < 2) continue;
      line += (firstStroke ? "" : ",") + std::string("[") + body + "]";
      firstStroke = false;
      used++;
      totalPts += n;
    }
    line += "]}";
    if (used < 3) { skipped++; continue; }
    std::fprintf(f, "%s\n", line.c_str());
    written++;
    if (written % 250 == 0) std::fprintf(stderr, "  %d projektów...\n", written);
  }
  std::fclose(f);
  std::printf("OK %s: %d projektów, pominięto %d, punktów %lld\n", outPath.c_str(), written, skipped, totalPts);
  return 0;
}
