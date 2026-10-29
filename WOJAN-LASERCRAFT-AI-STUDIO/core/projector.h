// Rzutowanie projektu na produkt: kształt, obszar grawerowania, marginesy
#pragma once
#include "design.h"

namespace lc {

struct SheetPlan {
  double productW = 200, productH = 150;
  Rect productRect;
  Rect engraveArea;     // obszar bezpieczny (z marginesem)
  bool hasCutContour = false;
  Rect cutBounds;
  double cutLengthMm = 0;
  double engraveAreaMm2 = 0;
  std::string shape = "rect";
};

// Kształty produktów: zwraca kontur w mm (0,0)-(w,h)
Path productShape(const std::string& shape, double w, double h, double cornerR);
std::string productShapeSvgPath(const std::string& shape, double w, double h, double cornerR);

// Produkty gotowe (bez cięcia) i wycinane
struct ProductDef {
  std::string id, name, category, shape, defaultMaterial;
  double w, h, cornerR, margin;
  bool cut, curved;
  std::string note;
};
const std::vector<ProductDef>& productCatalog();
const ProductDef* findProduct(const std::string& id);

struct MaterialDef {
  std::string id, name;
  double thickness;
  bool cuts, engraves, safe;
  std::string warning;
  double costPerM2;     // orientacyjny koszt materiału (PLN/m²)
  bool transparent;
};
const std::vector<MaterialDef>& materialCatalog();
const MaterialDef* findMaterial(const std::string& id);

struct MachineDef {
  std::string id, name;
  double fieldW, fieldH, kerf;
  int lpi;
  std::string controller, kind;
  std::string note;
};
const std::vector<MachineDef>& machineCatalog();
const MachineDef* findMachine(const std::string& id);

// ustawia produkt i (jeśli trzeba) dokłada warstwę cięcia
void applyProduct(Design& d, const ProductDef& p, const std::string& material);
void applyMachine(Design& d, const MachineDef& m);
void ensureCutContour(Design& d);     // tworzy CUT_OUTER wg kształtu produktu
SheetPlan computePlan(const Design& d, FontRegistry& fonts, const AssetStore& assets);
std::string planReport(const SheetPlan& plan);

}  // namespace lc
