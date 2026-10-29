// Model projektu: warstwy, obiekty, produkt, maszyna, historia
#pragma once
#include "json.h"
#include "raster.h"
#include "ttf.h"

namespace lc {

// Warstwy produkcyjne (stała kolejność)
extern const char* LAYER_CUT_OUTER;
extern const char* LAYER_CUT_INNER;
extern const char* LAYER_ENGRAVE_VECTOR;
extern const char* LAYER_ENGRAVE_FILL;
extern const char* LAYER_ENGRAVE_RASTER;
extern const char* LAYER_SCORE;
extern const char* LAYER_GUIDES;
std::vector<std::string> allLayers();
std::string layerLabel(const std::string& id);
std::string layerColor(const std::string& id);
std::string layerOperation(const std::string& id);
bool layerIsCut(const std::string& id);

struct Obj {
  std::string id;
  std::string type = "path";    // path | text | image | qr
  std::string layer = LAYER_ENGRAVE_VECTOR;
  std::string name;
  std::string role = "line";    // line | fill
  double strokeW = 0.25;
  bool locked = false, visible = true;
  double x = 0, y = 0, rot = 0, sx = 1, sy = 1;
  std::string d;                // ścieżka (mm, przed transformacją)
  std::string text, font = "anton";
  double size = 10, tracking = 0.2;
  std::string align = "center", warp = "none";
  double warpAmount = 0;
  std::string asset;            // id obrazu w assets
  double imgW = 0, imgH = 0;
  int threshold = 128;
  std::string dither = "floyd";
  double brightness = 0, contrast = 0;
  std::string qrData;
  double qrSize = 25;
  Json extra;                   // dowolne dodatkowe pola
  Json toJson() const;
  static Obj fromJson(const Json& j);
  Transform2 transform() const;
};

struct Product {
  std::string id = "tabliczka", name = "Tabliczka";
  double w = 200, h = 150;
  std::string material = "sklejka3";
  double engraveMargin = 5;   // margines bezpieczeństwa od krawędzi
  bool requiresCut = true;    // czy element jest wycinany
  double cornerR = 5;
  std::string shape = "rect"; // rect | rounded | circle | oval | shield | tag
  bool curved = false;
};

struct Machine {
  std::string id = "dioda10", name = "Dioda 10 W";
  double fieldW = 400, fieldH = 400;
  double kerf = 0.15;
  int lpi = 254;
  std::string controller = "GRBL";
  std::string kind = "dioda";  // dioda | co2 | fiber
};

struct LayerDef {
  std::string id, label, color, operation;
  bool visible = true;
  double speed = 400, power = 60;
  int passes = 1;
  bool isCut = false;
};

struct Design {
  std::string format = "wojan-lasercraft/1";
  std::string id, name = "Nowy projekt";
  std::string created, modified;
  std::string prompt, category = "humor", style = "retro", detail = "wysoki", engraveType = "fill+line";
  Product product;
  Machine machine;
  std::vector<Obj> objects;
  std::vector<LayerDef> layers;
  Json assets = Json::object();   // id -> base64 PNG
  Json assetMeta = Json::object();
  Json params = Json::object();
  std::vector<Json> history;
  int historyPos = -1;
  Json extra;

  Design();
  Json toJson() const;
  static Design fromJson(const Json& j);
  Obj* find(const std::string& id);
  void pushHistory(const std::string& label);
  bool undo();
  bool redo();
  std::string nextId(const std::string& prefix = "o");
  Rect boundsMm() const;
  LayerDef* layer(const std::string& id);
  std::string activeLayer = LAYER_ENGRAVE_VECTOR;
};

// Rozwiązana geometria obiektu (po transformacji)
struct Resolved {
  std::vector<Path> fills;
  std::vector<Path> strokes;
  bool hasImage = false;
  Image image;
  Rect imageRect;
  int threshold = 128;
  std::string dither = "none";
  bool ok = false;
  std::string error;
};

class AssetStore;
Resolved resolveObject(const Design& d, const Obj& o, FontRegistry& fonts, const AssetStore& assets,
                       bool includeGuides = false);
Path textPath(const Font* f, const std::string& text, double size, double tracking,
              const std::string& align, const std::string& warp, double warpAmount, Vec2 baseline,
              double maxWidth = 0, double* outWidth = nullptr);
Image renderDesign(const Design& d, FontRegistry& fonts, const AssetStore& assets, double pxPerMm,
                   bool includeGuides = false, bool includeRaster = true, bool monochrome = true);

}  // namespace lc
