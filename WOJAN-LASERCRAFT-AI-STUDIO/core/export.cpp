#include "export.h"
#include "qr.h"
#include "validate.h"
#include "zip.h"
#include <cmath>

namespace lc {

namespace {

std::string esc(const std::string& s) {
  std::string o;
  for (char c : s) {
    switch (c) {
      case '<': o += "&lt;"; break;
      case '>': o += "&gt;"; break;
      case '&': o += "&amp;"; break;
      case '"': o += "&quot;"; break;
      default: o.push_back(c);
    }
  }
  return o;
}

std::vector<const Obj*> layerObjects(const Design& d, const std::string& layer) {
  std::vector<const Obj*> out;
  LayerDef* ld = const_cast<Design&>(d).layer(layer);
  if (ld && !ld->visible) return out;
  for (auto& o : d.objects) {
    if (o.layer != layer) continue;
    if (!o.visible) continue;
    out.push_back(&o);
  }
  return out;
}

std::string svgForObject(const Design& d, const Obj& o, FontRegistry& fonts, const AssetStore& assets,
                         bool embedRaster, const std::string& layerColor) {
  std::string out;
  std::string color = layerColor;
  std::string fillAttr = (o.role == "fill")
                          ? "fill=\"" + color + "\" fill-rule=\"evenodd\" stroke=\"none\""
                          : "fill=\"none\" stroke=\"" + color + "\"";
  if (o.role != "fill") {
    double sw = layerIsCut(o.layer) ? 0.1 : std::max(0.05, o.strokeW);
    fillAttr += " stroke-width=\"" + fmtDouble(sw, 3) + "\" stroke-linejoin=\"round\" stroke-linecap=\"round\"";
  }
  if (o.type == "image") {
    std::string b64 = assets.b64(o.asset);
    if (b64.empty()) return out;
    double w = o.imgW > 0 ? o.imgW : 50, h = o.imgH > 0 ? o.imgH : 50;
    out += "<image x=\"" + fmtDouble(o.x, 3) + "\" y=\"" + fmtDouble(o.y, 3) + "\" width=\"" +
           fmtDouble(w, 3) + "\" height=\"" + fmtDouble(h, 3) + "\"";
    if (embedRaster) out += " xlink:href=\"data:image/png;base64," + b64 + "\"";
    out += " preserveAspectRatio=\"none\" id=\"" + esc(o.id) + "\"/>\n";
    return out;
  }
  Path p;
  if (o.type == "text") {
    Font* f = fonts.get(o.font);
    p = textPath(f, o.text, o.size, o.tracking, o.align, o.warp, o.warpAmount, Vec2(o.x, o.y));
  } else if (o.type == "qr") {
    p = qrPath(o.qrData, o.qrSize);
    p.apply(Transform2::translate(o.x, o.y));
  } else {
    p = Path::fromSVG(o.d);
    p.apply(Transform2::translate(o.x, o.y));
  }
  if (p.empty()) return out;
  if (std::fabs(o.sx - 1) > 1e-9 || std::fabs(o.sy - 1) > 1e-9 || std::fabs(o.rot) > 1e-9) {
    Transform2 t = o.transform();
    p.apply(t);
  }
  out += "<path d=\"" + p.toSVG() + "\" " + fillAttr + " id=\"" + esc(o.id) + "\"";
  if (!o.name.empty()) out += " inkscape:label=\"" + esc(o.name) + "\"";
  out += "/>\n";
  return out;
}

}  // namespace

std::string designToSvg(const Design& d, FontRegistry& fonts, const AssetStore& assets,
                        bool includeGuides, bool embedRaster) {
  std::string s;
  s += "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
  s += "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"\n";
  s += "     xmlns:inkscape=\"http://www.inkscape.org/namespaces/inkscape\"\n";
  s += "     width=\"" + fmtDouble(d.product.w, 3) + "mm\" height=\"" + fmtDouble(d.product.h, 3) + "mm\"\n";
  s += "     viewBox=\"0 0 " + fmtDouble(d.product.w, 3) + " " + fmtDouble(d.product.h, 3) + "\"\n";
  s += "     id=\"wojan-lasercraft\" data-units=\"mm\"\n";
  s += "     data-project=\"" + esc(d.name) + "\" data-machine=\"" + esc(d.machine.name) + "\">\n";
  s += "  <title>" + esc(d.name) + "</title>\n";
  s += "  <desc>WOJAN LaserCraft AI Studio — wymiary w mm; kolory warstw: CUT_OUTER #FF0000, "
       "CUT_INNER #00AAFF, ENGRAVE #000000. Teksty zamienione na ścieżki.</desc>\n";
  for (auto& layerId : allLayers()) {
    if (layerId == LAYER_GUIDES && !includeGuides) continue;
    auto objs = layerObjects(d, layerId);
    if (objs.empty()) continue;
    std::string color = layerColor(layerId);
    s += "  <g id=\"" + layerId + "\" inkscape:groupmode=\"layer\" inkscape:label=\"" + layerLabel(layerId) +
         "\" data-operation=\"" + layerOperation(layerId) + "\" data-color=\"" + color + "\">\n";
    for (auto* o : objs) s += "    " + svgForObject(d, *o, fonts, assets, embedRaster, color);
    s += "  </g>\n";
  }
  s += "</svg>\n";
  return s;
}

std::string designToSvgLayer(const Design& d, FontRegistry& fonts, const AssetStore& assets,
                             const std::string& layer) {
  Design tmp = d;
  if (!layer.empty()) {
    for (auto& o : tmp.objects) if (o.layer != layer) o.visible = false;
    for (auto& l : tmp.layers) l.visible = (l.id == layer);
  }
  return designToSvg(tmp, fonts, assets, layer == LAYER_GUIDES, true);
}

namespace {

// Budowniczy poprawnego pliku DXF R2000 (AC1015): nagłówek, tabele, bloki, obiekty.
struct DxfBuilder {
  std::string s;
  int handle = 0x100;
  std::string h() {
    char b[16];
    snprintf(b, sizeof b, "%X", handle++);
    return b;
  }
  void tag(int code, const std::string& v) { s += std::to_string(code) + "\n" + v + "\n"; }
  void tag(int code, double v) { tag(code, fmtDouble(v, 6)); }
};

}  // namespace

std::string designToDxf(const Design& d, FontRegistry& fonts, const AssetStore& assets,
                        const std::string& layerOnly) {
  // DXF R2000 ASCII, jednostki mm, LWPOLYLINE; raster NIE jest osadzany (DXF nie przenosi rastra)
  (void)assets;
  struct Lay { const char* name; int color; };
  const Lay layers[] = {{"0", 7},         {"CUT_OUTER", 1},     {"CUT_INNER", 5},
                        {"ENGRAVE_VECTOR", 7}, {"ENGRAVE_FILL", 8}, {"SCORE", 6},
                        {"GUIDES", 3}};

  // 1) geometria + zakres
  struct Poly { std::string layer; bool closed; std::vector<Vec2> pts; };
  std::vector<Poly> polys;
  double minX = 1e18, minY = 1e18, maxX = -1e18, maxY = -1e18;
  for (auto& layerId : allLayers()) {
    if (!layerOnly.empty() && layerId != layerOnly) continue;
    if (layerId == LAYER_ENGRAVE_RASTER) continue;  // raster nie jest geometrią DXF
    if (layerId == LAYER_GUIDES) continue;          // prowadnice nie trafiają do produkcji
    auto objs = layerObjects(d, layerId);
    for (auto* o : objs) {
      Path p;
      if (o->type == "text") {
        Font* f = fonts.get(o->font);
        p = textPath(f, o->text, o->size, o->tracking, o->align, o->warp, o->warpAmount, Vec2(o->x, o->y));
      } else if (o->type == "image") {
        continue;
      } else if (o->type == "qr") {
        p = qrPath(o->qrData, o->qrSize);
        p.apply(Transform2::translate(o->x, o->y));
      } else {
        p = Path::fromSVG(o->d);
        p.apply(Transform2::translate(o->x, o->y));
        if (std::fabs(o->sx - 1) > 1e-9 || std::fabs(o->sy - 1) > 1e-9 || std::fabs(o->rot) > 1e-9)
          p.apply(o->transform());
      }
      if (p.empty()) continue;
      for (auto& c : p.flatten(0.05)) {
        if (c.pts.size() < 2) continue;
        Poly q;
        q.layer = layerId;
        q.closed = c.closed && c.pts.size() > 2;
        q.pts = c.pts;
        for (auto& pt : q.pts) {
          minX = std::min(minX, pt.x); minY = std::min(minY, pt.y);
          maxX = std::max(maxX, pt.x); maxY = std::max(maxY, pt.y);
        }
        polys.push_back(q);
      }
    }
  }
  if (polys.empty()) { minX = minY = 0; maxX = d.product.w; maxY = d.product.h; }

  DxfBuilder b;
  // --- HEADER ---
  b.tag(0, "SECTION"); b.tag(2, "HEADER");
  b.tag(9, "$ACADVER"); b.tag(1, "AC1015");
  b.tag(9, "$HANDSEED"); b.tag(5, "FFFFF");
  b.tag(9, "$INSBASE"); b.tag(10, 0.0); b.tag(20, 0.0); b.tag(30, 0.0);
  b.tag(9, "$EXTMIN"); b.tag(10, minX); b.tag(20, minY); b.tag(30, 0.0);
  b.tag(9, "$EXTMAX"); b.tag(10, maxX); b.tag(20, maxY); b.tag(30, 0.0);
  b.tag(9, "$LIMMIN"); b.tag(10, std::min(0.0, minX)); b.tag(20, std::min(0.0, minY));
  b.tag(9, "$LIMMAX"); b.tag(10, std::max(maxX, d.product.w)); b.tag(20, std::max(maxY, d.product.h));
  b.tag(9, "$INSUNITS"); b.tag(70, "4");   // 4 = milimetry
  b.tag(9, "$MEASUREMENT"); b.tag(70, "1");  // metryczny
  b.tag(9, "$LUNITS"); b.tag(70, "2");
  b.tag(9, "$LUPREC"); b.tag(70, "4");
  b.tag(9, "$DIMASSOC"); b.tag(280, "2");
  b.tag(0, "ENDSEC");
  // --- CLASSES (pusta, dopuszczalna) ---
  b.tag(0, "SECTION"); b.tag(2, "CLASSES"); b.tag(0, "ENDSEC");
  // --- TABLES ---
  b.tag(0, "SECTION"); b.tag(2, "TABLES");

  // VPORT (wymagany przez większość czytników)
  b.tag(0, "TABLE"); b.tag(2, "VPORT"); b.tag(5, b.h());
  b.tag(100, "AcDbSymbolTable"); b.tag(70, "1");
  b.tag(0, "VPORT"); b.tag(5, b.h()); b.tag(100, "AcDbSymbolTableRecord");
  b.tag(100, "AcDbViewportTableRecord"); b.tag(2, "*Active");
  b.tag(70, "0"); b.tag(10, 0.0); b.tag(20, 0.0); b.tag(11, 1.0); b.tag(21, 1.0);
  b.tag(12, (minX + maxX) / 2); b.tag(22, (minY + maxY) / 2);
  b.tag(13, 0.0); b.tag(23, 0.0); b.tag(14, 10.0); b.tag(24, 10.0);
  b.tag(15, 0.0); b.tag(25, 0.0); b.tag(16, 0.0); b.tag(26, 0.0); b.tag(36, 1.0);
  b.tag(17, 0.0); b.tag(27, 0.0); b.tag(37, 0.0); b.tag(40, std::max(maxY - minY, 10.0));
  b.tag(41, 1.5); b.tag(42, 50.0); b.tag(43, 0.0); b.tag(44, 0.0);
  b.tag(50, 0.0); b.tag(51, 0.0); b.tag(71, "0"); b.tag(72, "100");
  b.tag(73, "1"); b.tag(74, "3"); b.tag(75, "0"); b.tag(76, "0"); b.tag(77, "0");
  b.tag(78, "0"); b.tag(281, "0"); b.tag(65, "1"); b.tag(110, 0.0); b.tag(120, 0.0);
  b.tag(130, 0.0); b.tag(111, 1.0); b.tag(121, 0.0); b.tag(131, 0.0);
  b.tag(112, 0.0); b.tag(122, 1.0); b.tag(132, 0.0); b.tag(79, "0"); b.tag(146, "0.0");
  b.tag(0, "ENDTAB");

  // LTYPE
  b.tag(0, "TABLE"); b.tag(2, "LTYPE"); b.tag(5, b.h());
  b.tag(100, "AcDbSymbolTable"); b.tag(70, "3");
  auto ltype = [&](const char* name, const char* desc) {
    b.tag(0, "LTYPE"); b.tag(5, b.h()); b.tag(100, "AcDbSymbolTableRecord");
    b.tag(100, "AcDbLinetypeTableRecord"); b.tag(2, name); b.tag(70, "0");
    b.tag(3, desc); b.tag(72, "65"); b.tag(73, "0"); b.tag(40, 0.0);
  };
  ltype("ByBlock", "");
  ltype("ByLayer", "");
  ltype("CONTINUOUS", "Solid line");
  b.tag(0, "ENDTAB");

  // LAYER
  b.tag(0, "TABLE"); b.tag(2, "LAYER"); b.tag(5, b.h());
  b.tag(100, "AcDbSymbolTable"); b.tag(70, std::to_string((int)(sizeof layers / sizeof layers[0])));
  for (auto& l : layers) {
    b.tag(0, "LAYER"); b.tag(5, b.h()); b.tag(100, "AcDbSymbolTableRecord");
    b.tag(100, "AcDbLayerTableRecord"); b.tag(2, l.name); b.tag(70, "0");
    b.tag(62, std::to_string(l.color)); b.tag(6, "CONTINUOUS");
    b.tag(370, "25");  // grubość linii 0,25 mm (informacyjnie)
  }
  b.tag(0, "ENDTAB");

  // STYLE
  b.tag(0, "TABLE"); b.tag(2, "STYLE"); b.tag(5, b.h());
  b.tag(100, "AcDbSymbolTable"); b.tag(70, "1");
  b.tag(0, "STYLE"); b.tag(5, b.h()); b.tag(100, "AcDbSymbolTableRecord");
  b.tag(100, "AcDbTextStyleTableRecord"); b.tag(2, "Standard"); b.tag(70, "0");
  b.tag(40, 0.0); b.tag(41, 1.0); b.tag(50, 0.0); b.tag(71, "0"); b.tag(42, 2.5);
  b.tag(3, "txt"); b.tag(4, "");
  b.tag(0, "ENDTAB");

  // VIEW
  b.tag(0, "TABLE"); b.tag(2, "VIEW"); b.tag(5, b.h());
  b.tag(100, "AcDbSymbolTable"); b.tag(70, "0");
  b.tag(0, "ENDTAB");

  // UCS
  b.tag(0, "TABLE"); b.tag(2, "UCS"); b.tag(5, b.h());
  b.tag(100, "AcDbSymbolTable"); b.tag(70, "0");
  b.tag(0, "ENDTAB");

  // APPID
  b.tag(0, "TABLE"); b.tag(2, "APPID"); b.tag(5, b.h());
  b.tag(100, "AcDbSymbolTable"); b.tag(70, "1");
  b.tag(0, "APPID"); b.tag(5, b.h()); b.tag(100, "AcDbSymbolTableRecord");
  b.tag(100, "AcDbRegAppTableRecord"); b.tag(2, "ACAD"); b.tag(70, "0");
  b.tag(0, "ENDTAB");

  // DIMSTYLE
  b.tag(0, "TABLE"); b.tag(2, "DIMSTYLE"); b.tag(5, b.h());
  b.tag(100, "AcDbSymbolTable"); b.tag(70, "1"); b.tag(100, "AcDbDimStyleTable");
  b.tag(0, "DIMSTYLE"); b.tag(105, b.h()); b.tag(100, "AcDbSymbolTableRecord");
  b.tag(100, "AcDbDimStyleTableRecord"); b.tag(2, "Standard"); b.tag(70, "0");
  b.tag(0, "ENDTAB");

  // BLOCK_RECORD
  b.tag(0, "TABLE"); b.tag(2, "BLOCK_RECORD"); b.tag(5, b.h());
  b.tag(100, "AcDbSymbolTable"); b.tag(70, "2");
  std::string hModelSpace = b.h(), hPaperSpace = b.h();
  b.tag(0, "BLOCK_RECORD"); b.tag(5, hModelSpace); b.tag(100, "AcDbSymbolTableRecord");
  b.tag(100, "AcDbBlockTableRecord"); b.tag(2, "*Model_Space"); b.tag(70, "0");
  b.tag(0, "BLOCK_RECORD"); b.tag(5, hPaperSpace); b.tag(100, "AcDbSymbolTableRecord");
  b.tag(100, "AcDbBlockTableRecord"); b.tag(2, "*Paper_Space"); b.tag(70, "0");
  b.tag(0, "ENDTAB");
  b.tag(0, "ENDSEC");

  // --- BLOCKS ---
  b.tag(0, "SECTION"); b.tag(2, "BLOCKS");
  auto block = [&](const char* name, const std::string& rec, bool paper) {
    b.tag(0, "BLOCK"); b.tag(5, b.h()); b.tag(100, "AcDbEntity"); b.tag(8, "0");
    b.tag(100, "AcDbBlockBegin"); b.tag(2, name); b.tag(70, "0");
    b.tag(10, 0.0); b.tag(20, 0.0); b.tag(30, 0.0); b.tag(3, name);
    b.tag(1, ""); (void)paper;
    b.tag(0, "ENDBLK"); b.tag(5, b.h()); b.tag(100, "AcDbEntity"); b.tag(8, "0");
    b.tag(100, "AcDbBlockEnd");
    (void)rec;
  };
  block("*Model_Space", hModelSpace, false);
  block("*Paper_Space", hPaperSpace, true);
  b.tag(0, "ENDSEC");

  // --- ENTITIES ---
  b.tag(0, "SECTION"); b.tag(2, "ENTITIES");
  for (auto& q : polys) {
    b.tag(0, "LWPOLYLINE"); b.tag(5, b.h());
    b.tag(100, "AcDbEntity"); b.tag(8, q.layer);
    b.tag(100, "AcDbPolyline");
    b.tag(90, std::to_string(q.pts.size())); b.tag(70, q.closed ? "1" : "0");
    b.tag(43, 0.0);
    for (auto& pt : q.pts) { b.tag(10, pt.x); b.tag(20, pt.y); }
  }
  b.tag(0, "ENDSEC");

  // --- OBJECTS (słowniki wymagane przez R2000) ---
  b.tag(0, "SECTION"); b.tag(2, "OBJECTS");
  std::string hRoot = b.h(), hGroup = b.h(), hLayouts = b.h();
  b.tag(0, "DICTIONARY"); b.tag(5, hRoot); b.tag(100, "AcDbDictionary"); b.tag(281, "1");
  b.tag(3, "ACAD_GROUP"); b.tag(350, hGroup);
  b.tag(3, "ACAD_LAYOUT"); b.tag(350, hLayouts);
  b.tag(0, "DICTIONARY"); b.tag(5, hGroup); b.tag(330, hRoot);
  b.tag(100, "AcDbDictionary"); b.tag(281, "1");
  b.tag(0, "DICTIONARY"); b.tag(5, hLayouts); b.tag(330, hRoot);
  b.tag(100, "AcDbDictionary"); b.tag(281, "1");
  b.tag(3, "Layout1"); b.tag(350, b.h());
  b.tag(0, "ENDSEC");
  b.tag(0, "EOF");
  return b.s;
}

ExportResult designToGcode(const Design& d, FontRegistry& fonts, const AssetStore& assets,
                           const GcodeOptions& opt, std::string& out) {
  ExportResult r;
  out.clear();
  if (d.machine.controller != "GRBL") {
    r.error = "Wybrany kontroler '" + d.machine.controller + "' nie jest GRBL — eksport G-code zablokowany. "
              "Ustaw profil maszyny z kontrolerem GRBL albo użyj eksportu SVG/DXF.";
    return r;
  }
  if (d.machine.id == "custom") {
    r.warnings.push_back("Profil 'Własna maszyna' — sprawdź pole robocze i kerf przed uruchomieniem lasera.");
  }
  // sprawdź granice pola roboczego
  double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
  for (auto& o : d.objects) {
    if (!o.visible) continue;
    Path p;
    if (o.type == "text") p = textPath(fonts.get(o.font), o.text, o.size, o.tracking, o.align, o.warp, o.warpAmount, Vec2(o.x, o.y));
    else if (o.type == "qr") { p = qrPath(o.qrData, o.qrSize); p.apply(Transform2::translate(o.x, o.y)); }
    else if (o.type == "path") { p = Path::fromSVG(o.d); p.apply(Transform2::translate(o.x, o.y)); }
    if (p.empty()) continue;
    Rect b = p.bounds();
    minX = std::min(minX, b.x); minY = std::min(minY, b.y);
    maxX = std::max(maxX, b.x1()); maxY = std::max(maxY, b.y1());
  }
  if (minX < 1e8 && !opt.allowOutsideField) {
    if (minX < -0.01 || minY < -0.01 || maxX > d.machine.fieldW + 0.01 || maxY > d.machine.fieldH + 0.01) {
      r.error = sfmt("Projekt (%.1f–%.1f × %.1f–%.1f mm) wykracza poza pole robocze maszyny %.0f×%.0f mm. "
                     "Eksport G-code zablokowany.", minX, maxX, minY, maxY, d.machine.fieldW, d.machine.fieldH);
      return r;
    }
  }
  std::string g;
  g += "; WOJAN LaserCraft AI Studio — G-code (GRBL)\n";
  g += "; projekt: " + d.name + "\n";
  g += "; maszyna: " + d.machine.name + " (pole " + fmtDouble(d.machine.fieldW, 1) + "x" +
       fmtDouble(d.machine.fieldH, 1) + " mm)\n";
  g += "; materiał: " + d.product.material + ", kerf " + fmtDouble(d.machine.kerf, 2) + " mm\n";
  g += "; WYgenerowano: " + nowIso() + "\n";
  g += "; UWAGA: parametry mocy/prędkości są orientacyjne — sprawdź na materiale próbnym.\n";
  g += "G21 ; mm\nG90 ; pozycja absolutna\nG94 ; posuw na minutę\nG17 ; płaszczyzna XY\n";
  g += "M5 ; laser wyłączony\nG0 Z" + fmtDouble(opt.zSafe, 2) + "\n";
  g += "G0 X0 Y0\n";
  int line = 0;
  auto emitPath = [&](const Path& p, double feed, double power, bool cut, int passes) {
    auto contours = p.flatten(0.05);
    for (auto& c : contours) {
      if (c.pts.size() < 2) continue;
      g += "G0 Z" + fmtDouble(opt.zSafe, 2) + "\n";
      g += "G0 X" + fmtDouble(c.pts[0].x, 3) + " Y" + fmtDouble(c.pts[0].y, 3) + "\n";
      for (int pass = 0; pass < passes; pass++) {
        g += "M4 S" + fmtDouble(power * 10, 0) + " ; " + (cut ? "cięcie" : "grawer") + " moc " +
             fmtDouble(power, 0) + "%\n";
        g += "G1 F" + fmtDouble(feed, 0) + "\n";
        for (size_t i = 1; i < c.pts.size(); i++)
          g += "G1 X" + fmtDouble(c.pts[i].x, 3) + " Y" + fmtDouble(c.pts[i].y, 3) + "\n";
        if (c.closed)
          g += "G1 X" + fmtDouble(c.pts[0].x, 3) + " Y" + fmtDouble(c.pts[0].y, 3) + "\n";
        g += "M5\n";
        line++;
      }
    }
  };
  if (opt.includeEngraveVector) {
    for (auto& layerId : {LAYER_ENGRAVE_VECTOR, LAYER_ENGRAVE_FILL, LAYER_SCORE}) {
      auto objs = layerObjects(d, layerId);
      if (objs.empty()) continue;
      g += "; --- " + layerLabel(layerId) + " ---\n";
      LayerDef* ld = const_cast<Design&>(d).layer(layerId);
      double feed = ld ? ld->speed : opt.engraveFeed;
      double power = ld ? ld->power : opt.power;
      int passes = ld ? ld->passes : 1;
      for (auto* o : objs) {
        Path p;
        if (o->type == "text") p = textPath(fonts.get(o->font), o->text, o->size, o->tracking, o->align, o->warp, o->warpAmount, Vec2(o->x, o->y));
        else if (o->type == "qr") { p = qrPath(o->qrData, o->qrSize); p.apply(Transform2::translate(o->x, o->y)); }
        else if (o->type == "path") { p = Path::fromSVG(o->d); p.apply(Transform2::translate(o->x, o->y)); }
        if (!p.empty()) emitPath(p, feed, power, false, passes);
      }
      if (layerId == LAYER_ENGRAVE_FILL) r.warnings.push_back("Warstwa ENGRAVE_FILL: wypełnienia eksportowane jako kontury — raster w G-code tylko w trybie rastrowym.");
    }
  }
  if (opt.includeCut) {
    for (auto& layerId : {LAYER_CUT_INNER, LAYER_CUT_OUTER}) {  // najpierw otwory
      auto objs = layerObjects(d, layerId);
      if (objs.empty()) continue;
      g += "; --- " + layerLabel(layerId) + " ---\n";
      LayerDef* ld = const_cast<Design&>(d).layer(layerId);
      double feed = ld ? ld->speed : opt.feed;
      double power = ld ? ld->power : opt.power;
      int passes = ld ? ld->passes : opt.passes;
      for (auto* o : objs) {
        Path p = Path::fromSVG(o->d);
        p.apply(Transform2::translate(o->x, o->y));
        if (p.empty()) continue;
        // kompensacja kerf: kontur powiększony o kerf/2
        if (d.machine.kerf > 0.001) {
          Path off = p.offsetted(d.machine.kerf / 2);
          if (!off.empty()) p = off;
        }
        emitPath(p, feed, power, true, passes);
      }
    }
  }
  g += "; --- koniec ---\nM5\nG0 Z" + fmtDouble(opt.zSafe, 2) + "\nG0 X0 Y0\nM2\n";
  out = g;
  r.ok = true;
  r.warnings.push_back("G-code wygenerowany dla profilu GRBL; przed uruchomieniem sprawdź moc/prędkość na próbce.");
  return r;
}

std::string exportReportText(const Design& d, FontRegistry& fonts, const AssetStore& assets) {
  ValidationReport rep = validateDesign(d, fonts, assets);
  return rep.toText();
}

PackageResult writeProductionPackage(const Design& d, FontRegistry& fonts, const AssetStore& assets,
                                     const std::string& outRoot,
                                     const std::string& previewPngBase64) {
  PackageResult res;
  std::string dirName = slugify(d.name.empty() ? "projekt" : d.name);
  std::string dir = outRoot + "/" + dirName;
  makeDirs(dir);
  auto addFile = [&](const std::string& name, const std::string& content) {
    std::string path = dir + "/" + name;
    if (!writeFile(path, content)) { res.error = "nie można zapisać " + path; return false; }
    res.files.push_back(name);
    res.manifest.push_back({name, std::to_string(content.size())});
    return true;
  };
  std::string svgAll = designToSvg(d, fonts, assets, false, true);
  if (!addFile("lightburn.svg", svgAll)) return res;
  // PNG rastrowy do LaserGRBL: renderuje wywołujący (potrzebny raster); tu zapisujemy PNG z assets
  if (!addFile("cut_outer.svg", designToSvgLayer(d, fonts, assets, LAYER_CUT_OUTER))) return res;
  if (!addFile("engrave_vector.svg", designToSvgLayer(d, fonts, assets, LAYER_ENGRAVE_VECTOR))) return res;
  if (!addFile("design.dxf", designToDxf(d, fonts, assets, ""))) return res;
  Json params = Json::object();
  params.set("produkt", d.product.name);
  params.set("wymiary_mm", Json(sfmt("%.1f x %.1f", d.product.w, d.product.h)));
  params.set("material", d.product.material);
  params.set("maszyna", d.machine.name);
  params.set("kontroler", d.machine.controller);
  params.set("kerf_mm", d.machine.kerf);
  params.set("lpi", d.machine.lpi);
  params.set("wymaga_ciecia", d.product.requiresCut);
  params.set("data", nowIso());
  if (!addFile("parametry.json", params.dump(2))) return res;
  if (!addFile("raport_walidacji.txt", exportReportText(d, fonts, assets))) return res;
  if (!previewPngBase64.empty()) {
    auto bytes = base64Decode(previewPngBase64);
    std::string path = dir + "/preview.png";
    writeFileBytes(path, bytes);
    res.files.push_back("preview.png");
  }
  res.dir = dir;
  res.ok = true;
  return res;
}

}  // namespace lc
