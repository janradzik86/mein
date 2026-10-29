#include "projector.h"
#include "assets.h"
#include <cmath>

namespace lc {

Path productShape(const std::string& shape, double w, double h, double r) {
  Path p;
  if (shape == "circle") {
    p.addCircle(w / 2, h / 2, std::min(w, h) / 2);
  } else if (shape == "oval" || shape == "ellipse") {
    p.addEllipse(w / 2, h / 2, w / 2, h / 2);
  } else if (shape == "rounded") {
    p.addRoundRect(0, 0, w, h, r);
  } else if (shape == "shield") {
    // tarcza heraldyczna: górna krawędź prosta, boki łukowate, dół w szpic
    p.moveTo({w * 0.06, 0});
    p.lineTo({w * 0.94, 0});
    p.cubicTo({w * 0.99, h * 0.30}, {w * 0.92, h * 0.62}, {w * 0.5, h});
    p.cubicTo({w * 0.08, h * 0.62}, {w * 0.01, h * 0.30}, {w * 0.06, 0});
    p.close();
  } else if (shape == "tag") {
    // Zawieszka: kontur z zaokrąglonymi narożnikami. Otwór na sznurek jest osobnym
    // obiektem warstwy CUT_INNER (compose.cpp), więc cięcie wewnętrzne idzie pierwsze.
    p.addRoundRect(0, 0, w, h, std::min(6.0, r));
  } else if (shape == "dogtag") {
    p.addRoundRect(0, 0, w, h, std::min(w, h) * 0.18);
  } else {
    p.addRect(0, 0, w, h);
  }
  return p;
}
std::string productShapeSvgPath(const std::string& shape, double w, double h, double r) {
  return productShape(shape, w, h, r).toSVG();
}

const std::vector<ProductDef>& productCatalog() {
  static const std::vector<ProductDef> list = {
      {"tabliczka", "Tabliczka", "dekoracje", "rounded", "sklejka3", 200, 150, 6, 6, true, false,
       "Wycinana tabliczka ze sklejki — pełny kontur cięcia."},
      {"szyld", "Szyld", "dekoracje", "rect", "sklejka5", 300, 150, 4, 8, true, false, "Duży szyld wycinany."},
      {"tarcza", "Tarcza / emblemat", "emblematy", "shield", "sklejka3", 150, 170, 4, 6, true, false,
       "Tarcza z konturem cięcia."},
      {"naszywka", "Naszywka skórzana", "odziez", "rounded", "skora", 80, 40, 8, 4, true, false,
       "Naszywka wycinana ze skóry; otwory na szwy opcjonalne."},
      {"niesmiertelnik", "Nieśmiertelnik", "breloki", "dogtag", "stal", 50, 28, 5, 2.5, true, false,
       "Nieśmiertelnik metalowy — grawer na gotowym blanku."},
      {"brelok", "Brelok", "breloki", "rounded", "sklejka3", 50, 30, 6, 3, true, false,
       "Brelok z otworem na kółko."},
      {"zawieszka", "Zawieszka", "breloki", "tag", "sklejka3", 60, 80, 5, 4, true, false, "Zawieszka z uszkiem."},
      {"medalion", "Medalion", "bizuteria", "circle", "stal", 40, 40, 0, 3, false, false,
       "Grawer na gotowym medalionie — bez linii cięcia."},
      {"medal", "Medal", "bizuteria", "circle", "akryl", 60, 60, 0, 4, true, false, "Medal wycinany z akrylu."},
      {"piersiowka", "Piersiówka", "akcesoria", "rect", "stal", 95, 115, 0, 5, false, true,
       "GOTOWY przedmiot — grawer bez cięcia. Powierzchnia zakrzywiona: wymaga rotacyjnego uchwytu."},
      {"kubek", "Kubek", "akcesoria", "rect", "ceramika", 210, 90, 0, 5, false, true,
       "GOTOWY kubek — grawer na powierzchni walcowej (rozwinięcie 210×90 mm)."},
      {"deska", "Deska kuchenna", "kuchnia", "rounded", "drewno", 300, 200, 20, 10, false, false,
       "Grawer na gotowej desce — bez cięcia."},
      {"skrzynka", "Skrzynka", "kuchnia", "rect", "sklejka5", 250, 160, 4, 8, true, false, "Skrzynka z płyt."},
      {"podkladka", "Podkładka", "kuchnia", "circle", "sklejka3", 100, 100, 0, 5, true, false, "Okrągła podkładka."},
      {"ozdoba", "Ozdoba świąteczna", "swieta", "tag", "sklejka3", 80, 90, 4, 4, true, false, "Ozdoba z uszkiem."},
      {"magnes", "Magnes", "pamiatki", "rounded", "sklejka3", 60, 45, 6, 4, true, false, "Magnes na lodówkę."},
      {"zakladka", "Zakładka do książki", "pamiatki", "rounded", "sklejka2", 50, 150, 5, 4, true, false, "Zakładka."},
      {"etui", "Etui skórzane", "odziez", "rounded", "skora", 110, 80, 10, 6, false, false,
       "GOTOWE etui — tylko grawer."},
      {"portfel", "Portfel", "odziez", "rect", "skora", 120, 95, 8, 6, false, false, "GOTOWY portfel — tylko grawer."},
      {"dowolny", "Dowolny przedmiot", "wlasne", "rect", "sklejka3", 150, 100, 3, 5, false, false,
       "Własny przedmiot — podaj wymiary i obszar grawerowania."},
  };
  return list;
}
const ProductDef* findProduct(const std::string& id) {
  for (auto& p : productCatalog()) if (p.id == id) return &p;
  return nullptr;
}

const std::vector<MaterialDef>& materialCatalog() {
  static const std::vector<MaterialDef> list = {
      {"sklejka3", "Sklejka brzozowa 3 mm", 3, true, true, true, "", 45, false},
      {"sklejka5", "Sklejka brzozowa 5 mm", 5, true, true, true, "", 60, false},
      {"sklejka2", "Sklejka brzozowa 2 mm", 2, true, true, true, "", 40, false},
      {"drewno", "Drewno lite (dąb/buk)", 20, false, true, true, "", 120, false},
      {"mdf3", "MDF 3 mm", 3, true, true, true, "MDF tnie się dobrze, ale pył drażni drogi oddechowe — używaj odciągu.", 30, false},
      {"skora", "Skóra naturalna", 2, true, true, true, "Skóra naturalna: przy cięciu wydziela zapach spalenizny, używaj odciągu.", 180, false},
      {"skora_syn", "Skóra syntetyczna (PU)", 1.5, true, true, false,
       "OSTRZEŻENIE: skóra syntetyczna/PU może zawierać PVC — przy cięciu wydziela chlorowodór i dioksyny. Cięcie tylko po sprawdzeniu karty bezpieczeństwa materiału.", 90, false},
      {"stal", "Stal nierdzewna", 1, false, true, true, "Grawer wymaga lasera fiber lub pasty do znakowania na diodzie.", 250, false},
      {"aluminium", "Aluminium anodowane", 2, false, true, true, "Do grawerowania aluminium wymagany laser fiber.", 220, false},
      {"akryl", "Akryl (PMMA)", 3, true, true, true,
       "UWAGA: akryl to PMMA — NIE mylić z PVC. Cięcie akrylu wydziela drażniące opary, wymagana wentylacja.", 110, true},
      {"ceramika", "Ceramika / kubek", 0, false, true, true, "Grawer na gotowym kubku.", 0, false},
      {"szklo", "Szkło", 3, false, true, false,
       "Szkło: grawer laserem diodowym może pękać; wymagana pasta/folia do znakowania lub laser CO2 z ostrożnymi parametrami.", 0, true},
      {"inne", "Inny materiał", 0, false, false, false,
       "Nieznany materiał — NIE tnij bez karty bezpieczeństwa (PVC, PTFE, poliwęglan wydzielają toksyny).", 0, false},
  };
  return list;
}
const MaterialDef* findMaterial(const std::string& id) {
  for (auto& m : materialCatalog()) if (m.id == id) return &m;
  return nullptr;
}

const std::vector<MachineDef>& machineCatalog() {
  static const std::vector<MachineDef> list = {
      {"dioda10", "Dioda 10 W (GRBL)", 400, 400, 0.15, 254, "GRBL", "dioda", "Pole 400×400 mm, kerf 0,15 mm (pomiar własny)."},
      {"dioda20", "Dioda 20 W (GRBL)", 400, 400, 0.20, 254, "GRBL", "dioda", "Pole 400×400 mm."},
      {"co2_600", "CO2 60 W (Ruida)", 600, 400, 0.25, 318, "Ruida", "co2", "Pole 600×400 mm."},
      {"fiber20", "Fiber 20 W", 110, 110, 0.05, 500, "BJJCZ", "fiber", "Pole 110×110 mm."},
      {"custom", "Własna maszyna", 300, 300, 0.15, 254, "GRBL", "dioda", "Wpisz parametry z dokumentacji maszyny."},
  };
  return list;
}
const MachineDef* findMachine(const std::string& id) {
  for (auto& m : machineCatalog()) if (m.id == id) return &m;
  return nullptr;
}

void applyProduct(Design& d, const ProductDef& p, const std::string& material) {
  d.product.id = p.id;
  d.product.name = p.name;
  d.product.w = p.w;
  d.product.h = p.h;
  d.product.shape = p.shape;
  d.product.cornerR = p.cornerR;
  d.product.engraveMargin = p.margin;
  d.product.requiresCut = p.cut;
  d.product.curved = p.curved;
  if (!material.empty()) d.product.material = material;
  else d.product.material = p.defaultMaterial;
}
void applyMachine(Design& d, const MachineDef& m) {
  d.machine.id = m.id;
  d.machine.name = m.name;
  d.machine.fieldW = m.fieldW;
  d.machine.fieldH = m.fieldH;
  d.machine.kerf = m.kerf;
  d.machine.lpi = m.lpi;
  d.machine.controller = m.controller;
  d.machine.kind = m.kind;
}
void ensureCutContour(Design& d) {
  // usuń stary kontur (obiekt oznaczony role=cut lub id=cut_outer)
  for (auto it = d.objects.begin(); it != d.objects.end();) {
    if (it->role == "cut" || it->id == "cut_outer") it = d.objects.erase(it);
    else ++it;
  }
  if (!d.product.requiresCut) return;
  Obj o;
  o.id = "cut_outer";
  o.type = "path";
  o.layer = LAYER_CUT_OUTER;
  o.role = "cut";
  o.name = "Kontur cięcia";
  o.strokeW = 0.1;
  o.x = 0;
  o.y = 0;
  Path p = productShape(d.product.shape, d.product.w, d.product.h, d.product.cornerR);
  o.d = p.toSVG();
  // otwór na kółko/sznurek — osobna warstwa cięcia wewnętrznego (CUT_INNER)
  bool needsHole = d.product.id == "brelok" || d.product.id == "zawieszka" ||
                   d.product.id == "ozdoba" || d.product.id == "medal" || d.product.shape == "tag";
  if (needsHole) {
    double holeR = d.product.shape == "tag" ? std::max(1.8, std::min(4.5, d.product.h * 0.05)) : 2.5;
    double hy = d.product.shape == "tag" ? holeR + 3.0
                : (d.product.id == "brelok" ? 6.0 : d.product.h * 0.10);
    Obj hole;
    hole.id = "cut_hole1";
    hole.type = "path";
    hole.layer = LAYER_CUT_INNER;
    hole.role = "hole";
    hole.name = "Otwór na kółko";
    hole.strokeW = 0.1;
    Path hp;
    hp.addCircle(d.product.w / 2, hy, holeR);
    hole.d = hp.toSVG();
    d.objects.insert(d.objects.begin(), hole);
  }
  d.objects.insert(d.objects.begin(), o);
}

SheetPlan computePlan(const Design& d, FontRegistry& fonts, const AssetStore& assets) {
  SheetPlan plan;
  plan.productW = d.product.w;
  plan.productH = d.product.h;
  plan.productRect = Rect(0, 0, d.product.w, d.product.h);
  plan.shape = d.product.shape;
  double m = std::max(0.0, d.product.engraveMargin);
  plan.engraveArea = Rect(m, m, std::max(0.0, d.product.w - 2 * m), std::max(0.0, d.product.h - 2 * m));
  plan.engraveAreaMm2 = plan.engraveArea.w * plan.engraveArea.h;
  for (auto& o : d.objects) {
    if (o.layer == LAYER_CUT_OUTER) {
      plan.hasCutContour = true;
      Path p = Path::fromSVG(o.d);
      p.apply(Transform2::translate(o.x, o.y));
      plan.cutBounds = plan.cutBounds.unite(p.bounds());
      plan.cutLengthMm += p.totalLength();
    } else if (o.layer == LAYER_CUT_INNER) {
      Path p = Path::fromSVG(o.d);
      p.apply(Transform2::translate(o.x, o.y));
      plan.cutLengthMm += p.totalLength();
    }
  }
  return plan;
}
std::string planReport(const SheetPlan& plan) {
  return sfmt("Produkt %.1f × %.1f mm, kształt %s, obszar grawerowania %.1f × %.1f mm (%.0f mm²), "
              "kontur cięcia: %s (długość %.1f mm)",
              plan.productW, plan.productH, plan.shape.c_str(), plan.engraveArea.w,
              plan.engraveArea.h, plan.engraveAreaMm2,
              plan.hasCutContour ? "TAK" : "NIE (gotowy przedmiot)", plan.cutLengthMm);
}

}  // namespace lc
