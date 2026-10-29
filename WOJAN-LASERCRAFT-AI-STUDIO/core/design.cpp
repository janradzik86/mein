#include "design.h"
#include "assets.h"
#include "qr.h"
#include <cmath>

namespace lc {

const char* LAYER_CUT_OUTER = "CUT_OUTER";
const char* LAYER_CUT_INNER = "CUT_INNER";
const char* LAYER_ENGRAVE_VECTOR = "ENGRAVE_VECTOR";
const char* LAYER_ENGRAVE_FILL = "ENGRAVE_FILL";
const char* LAYER_ENGRAVE_RASTER = "ENGRAVE_RASTER";
const char* LAYER_SCORE = "SCORE";
const char* LAYER_GUIDES = "GUIDES";

std::vector<std::string> allLayers() {
  return {LAYER_CUT_OUTER, LAYER_CUT_INNER, LAYER_ENGRAVE_VECTOR, LAYER_ENGRAVE_FILL,
          LAYER_ENGRAVE_RASTER, LAYER_SCORE, LAYER_GUIDES};
}
std::string layerLabel(const std::string& id) {
  if (id == LAYER_CUT_OUTER) return "Cięcie zewnętrzne";
  if (id == LAYER_CUT_INNER) return "Cięcie wewnętrzne";
  if (id == LAYER_ENGRAVE_VECTOR) return "Grawer wektorowy";
  if (id == LAYER_ENGRAVE_FILL) return "Grawer — wypełnienie";
  if (id == LAYER_ENGRAVE_RASTER) return "Grawer rastrowy";
  if (id == LAYER_SCORE) return "Znakowanie";
  if (id == LAYER_GUIDES) return "Linie pomocnicze";
  return id;
}
std::string layerColor(const std::string& id) { return colorForLayer(id); }
std::string layerOperation(const std::string& id) {
  if (id == LAYER_CUT_OUTER || id == LAYER_CUT_INNER) return "Cięcie";
  if (id == LAYER_SCORE) return "Znakowanie";
  if (id == LAYER_GUIDES) return "Pomocnicza";
  if (id == LAYER_ENGRAVE_RASTER) return "Raster";
  return "Grawer";
}
bool layerIsCut(const std::string& id) { return id == LAYER_CUT_OUTER || id == LAYER_CUT_INNER; }

Json Obj::toJson() const {
  Json j = Json::object();
  j.set("id", id); j.set("type", type); j.set("layer", layer); j.set("name", name);
  j.set("role", role); j.set("strokeW", strokeW);
  j.set("locked", locked); j.set("visible", visible);
  j.set("x", x); j.set("y", y); j.set("rot", rot); j.set("sx", sx); j.set("sy", sy);
  if (!d.empty()) j.set("d", d);
  if (type == "text") {
    j.set("text", text); j.set("font", font); j.set("size", size); j.set("tracking", tracking);
    j.set("align", align); j.set("warp", warp); j.set("warpAmount", warpAmount);
  }
  if (type == "image") {
    j.set("asset", asset); j.set("imgW", imgW); j.set("imgH", imgH);
    j.set("threshold", threshold); j.set("dither", dither);
    j.set("brightness", brightness); j.set("contrast", contrast);
  }
  if (type == "qr") { j.set("qrData", qrData); j.set("qrSize", qrSize); }
  if (!extra.isNull()) j.set("extra", extra);
  return j;
}
Obj Obj::fromJson(const Json& j) {
  Obj o;
  o.id = j["id"].str();
  o.type = j["type"].str("path");
  o.layer = j["layer"].str(LAYER_ENGRAVE_VECTOR);
  o.name = j["name"].str();
  o.role = j["role"].str("line");
  o.strokeW = j["strokeW"].num(0.25);
  o.locked = j["locked"].boolean(false);
  o.visible = j["visible"].boolean(true);
  o.x = j["x"].num(0); o.y = j["y"].num(0);
  o.rot = j["rot"].num(0); o.sx = j["sx"].num(1); o.sy = j["sy"].num(1);
  o.d = j["d"].str();
  o.text = j["text"].str(); o.font = j["font"].str("anton");
  o.size = j["size"].num(10); o.tracking = j["tracking"].num(0.2);
  o.align = j["align"].str("center"); o.warp = j["warp"].str("none");
  o.warpAmount = j["warpAmount"].num(0);
  o.asset = j["asset"].str(); o.imgW = j["imgW"].num(0); o.imgH = j["imgH"].num(0);
  o.threshold = j["threshold"].integer(128); o.dither = j["dither"].str("floyd");
  o.brightness = j["brightness"].num(0); o.contrast = j["contrast"].num(0);
  o.qrData = j["qrData"].str(); o.qrSize = j["qrSize"].num(25);
  o.extra = j["extra"];
  return o;
}
Transform2 Obj::transform() const {
  return Transform2::translate(x, y).mul(Transform2::rotate(rot)).mul(Transform2::scale(sx, sy));
}

Design::Design() {
  created = modified = nowIso();
  layers = {
      {LAYER_CUT_OUTER, layerLabel(LAYER_CUT_OUTER), "#FF0000", "Cięcie", true, 400, 80, 1, true},
      {LAYER_CUT_INNER, layerLabel(LAYER_CUT_INNER), "#00AAFF", "Cięcie", true, 400, 75, 1, true},
      {LAYER_ENGRAVE_VECTOR, layerLabel(LAYER_ENGRAVE_VECTOR), "#000000", "Grawer", true, 1200, 30, 1, false},
      {LAYER_ENGRAVE_FILL, layerLabel(LAYER_ENGRAVE_FILL), "#000000", "Grawer", true, 900, 25, 1, false},
      {LAYER_ENGRAVE_RASTER, layerLabel(LAYER_ENGRAVE_RASTER), "#808080", "Raster", true, 2000, 20, 1, false},
      {LAYER_SCORE, layerLabel(LAYER_SCORE), "#FF00FF", "Znakowanie", true, 1500, 12, 1, false},
      {LAYER_GUIDES, layerLabel(LAYER_GUIDES), "#00FF88", "Pomocnicza", true, 3000, 5, 1, false},
  };
}
Json Design::toJson() const {
  Json j = Json::object();
  j.set("format", format); j.set("id", id); j.set("name", name);
  j.set("created", created); j.set("modified", modified);
  j.set("prompt", prompt); j.set("category", category); j.set("style", style);
  j.set("detail", detail); j.set("engraveType", engraveType); j.set("activeLayer", activeLayer);
  Json p = Json::object();
  p.set("id", product.id); p.set("name", product.name);
  p.set("w", product.w); p.set("h", product.h); p.set("material", product.material);
  p.set("engraveMargin", product.engraveMargin); p.set("requiresCut", product.requiresCut);
  p.set("cornerR", product.cornerR); p.set("shape", product.shape); p.set("curved", product.curved);
  j.set("product", p);
  Json m = Json::object();
  m.set("id", machine.id); m.set("name", machine.name);
  m.set("fieldW", machine.fieldW); m.set("fieldH", machine.fieldH);
  m.set("kerf", machine.kerf); m.set("lpi", machine.lpi);
  m.set("controller", machine.controller); m.set("kind", machine.kind);
  j.set("machine", m);
  Json ar = Json::array();
  for (auto& o : objects) ar.push(o.toJson());
  j.set("objects", ar);
  Json lr = Json::array();
  for (auto& l : layers) {
    Json lj = Json::object();
    lj.set("id", l.id); lj.set("label", l.label); lj.set("color", l.color);
    lj.set("operation", l.operation); lj.set("visible", l.visible);
    lj.set("speed", l.speed); lj.set("power", l.power); lj.set("passes", l.passes);
    lj.set("isCut", l.isCut);
    lr.push(lj);
  }
  j.set("layers", lr);
  j.set("assets", assets);
  j.set("assetMeta", assetMeta);
  j.set("params", params);
  j.set("extra", extra);
  Json hist = Json::array();
  for (auto& h : history) hist.push(h);
  j.set("history", hist);
  j.set("historyPos", historyPos);
  return j;
}
Design Design::fromJson(const Json& j) {
  Design d;
  if (j.isNull() || !j.isObject()) return d;
  d.format = j["format"].str("wojan-lasercraft/1");
  d.id = j["id"].str();
  d.name = j["name"].str("Projekt");
  d.created = j["created"].str();
  d.modified = j["modified"].str();
  d.prompt = j["prompt"].str();
  d.category = j["category"].str();
  d.style = j["style"].str();
  d.detail = j["detail"].str();
  d.engraveType = j["engraveType"].str();
  d.activeLayer = j["activeLayer"].str(LAYER_ENGRAVE_VECTOR);
  const Json& p = j["product"];
  if (p.isObject()) {
    d.product.id = p["id"].str("tabliczka");
    d.product.name = p["name"].str("Tabliczka");
    d.product.w = p["w"].num(200);
    d.product.h = p["h"].num(150);
    d.product.material = p["material"].str("sklejka3");
    d.product.engraveMargin = p["engraveMargin"].num(5);
    d.product.requiresCut = p["requiresCut"].boolean(true);
    d.product.cornerR = p["cornerR"].num(5);
    d.product.shape = p["shape"].str("rect");
    d.product.curved = p["curved"].boolean(false);
  }
  const Json& m = j["machine"];
  if (m.isObject()) {
    d.machine.id = m["id"].str("dioda10");
    d.machine.name = m["name"].str("Dioda 10 W");
    d.machine.fieldW = m["fieldW"].num(400);
    d.machine.fieldH = m["fieldH"].num(400);
    d.machine.kerf = m["kerf"].num(0.15);
    d.machine.lpi = m["lpi"].integer(254);
    d.machine.controller = m["controller"].str("GRBL");
    d.machine.kind = m["kind"].str("dioda");
  }
  for (auto& oj : j["objects"].items()) d.objects.push_back(Obj::fromJson(oj));
  if (j["layers"].isArray() && j["layers"].size() > 0) {
    d.layers.clear();
    for (auto& lj : j["layers"].items()) {
      LayerDef l;
      l.id = lj["id"].str();
      l.label = lj["label"].str(layerLabel(l.id));
      l.color = lj["color"].str(colorForLayer(l.id));
      l.operation = lj["operation"].str(layerOperation(l.id));
      l.visible = lj["visible"].boolean(true);
      l.speed = lj["speed"].num(500);
      l.power = lj["power"].num(50);
      l.passes = lj["passes"].integer(1);
      l.isCut = lj["isCut"].boolean(layerIsCut(l.id));
      d.layers.push_back(l);
    }
  }
  d.assets = j["assets"];
  if (!d.assets.isObject()) d.assets = Json::object();
  d.assetMeta = j["assetMeta"];
  d.params = j["params"];
  d.extra = j["extra"];
  for (auto& h : j["history"].items()) d.history.push_back(h);
  d.historyPos = j["historyPos"].integer(-1);
  return d;
}
Obj* Design::find(const std::string& oid) {
  for (auto& o : objects) if (o.id == oid) return &o;
  return nullptr;
}
LayerDef* Design::layer(const std::string& id) {
  for (auto& l : layers) if (l.id == id) return &l;
  return nullptr;
}
std::string Design::nextId(const std::string& prefix) {
  int n = 1;
  while (true) {
    std::string cand = prefix + std::to_string(n++);
    bool used = false;
    for (auto& o : objects) if (o.id == cand) { used = true; break; }
    if (!used) return cand;
  }
}
void Design::pushHistory(const std::string& label) {
  Json snap = Json::object();
  snap.set("label", label);
  snap.set("time", nowIso());
  Json ar = Json::array();
  for (auto& o : objects) ar.push(o.toJson());
  snap.set("objects", ar);
  snap.set("name", name);
  snap.set("category", category);
  snap.set("style", style);
  if (historyPos >= 0 && historyPos + 1 < (int)history.size())
    history.erase(history.begin() + historyPos + 1, history.end());
  history.push_back(snap);
  if (history.size() > 120) history.erase(history.begin());
  historyPos = (int)history.size() - 1;
}
bool Design::undo() {
  if (historyPos <= 0) return false;
  historyPos--;
  const Json& snap = history[historyPos];
  objects.clear();
  for (auto& oj : snap["objects"].items()) objects.push_back(Obj::fromJson(oj));
  return true;
}
bool Design::redo() {
  if (historyPos < 0 || historyPos + 1 >= (int)history.size()) return false;
  historyPos++;
  const Json& snap = history[historyPos];
  objects.clear();
  for (auto& oj : snap["objects"].items()) objects.push_back(Obj::fromJson(oj));
  return true;
}
Rect Design::boundsMm() const {
  Rect r;
  bool first = true;
  for (auto& o : objects) {
    if (!o.visible) continue;
    Rect b;
    if (o.type == "text") {
      b = Rect(o.x, o.y - o.size, o.size * std::max(1, (int)o.text.size()) * 0.6, o.size * 1.3);
    } else if (o.type == "image") {
      b = Rect(o.x, o.y, o.imgW, o.imgH);
    } else if (o.type == "qr") {
      b = Rect(o.x, o.y, o.qrSize, o.qrSize);
    } else {
      b = Path::fromSVG(o.d).bounds();
      b = Rect(o.x + b.x * o.sx, o.y + b.y * o.sy, b.w * o.sx, b.h * o.sy);
    }
    r = first ? b : r.unite(b);
    first = false;
  }
  return r;
}

// dopasowanie tekstu do dostępnej szerokości
Path textPath(const Font* f, const std::string& text, double size, double tracking,
              const std::string& align, const std::string& warp, double warpAmount, Vec2 baseline,
              double maxWidth, double* outWidth) {
  Path out;
  if (!f || !f->valid()) return out;
  std::string t = text;
  double s = size;
  double w = f->measure(t, s, tracking);
  if (maxWidth > 0 && w > maxWidth) {
    double k = maxWidth / w;
    s *= k;
    tracking *= k;
    w = f->measure(t, s, tracking);
  }
  double ox = 0;
  if (align == "center") ox = -w / 2;
  else if (align == "right") ox = -w;
  Path p = f->layout(t, s, tracking, Vec2(baseline.x + ox, baseline.y));
  if (warp == "arc" || warp == "bridge") {
    Rect b = p.bounds();
    double cx = baseline.x, baseY = baseline.y;
    double radius = b.w * 0.9 + 12;
    if (warpAmount > 0) radius = std::max(20.0, radius / std::max(0.1, warpAmount));
    std::vector<Seg> segs;
    auto bend = [&](Vec2 q) {
      double dx = q.x - cx;
      double dy = q.y - baseY;
      if (warp == "arc") {
        double ang = dx / radius;
        return Vec2(cx + std::sin(ang) * (radius + dy * -1.0), baseY + radius - std::cos(ang) * (radius + dy * -1.0));
      }
      // bridge: parabola
      double k = radius * 6.0;
      return Vec2(q.x, q.y + dx * dx / k);
    };
    for (auto& sg : p.segs) {
      Seg n = sg;
      switch (sg.t) {
        case SEG_M: case SEG_L: n.p[0] = bend(sg.p[0]); break;
        case SEG_C: n.p[0] = bend(sg.p[0]); n.p[1] = bend(sg.p[1]); n.p[2] = bend(sg.p[2]); break;
        case SEG_Q: n.p[0] = bend(sg.p[0]); n.p[1] = bend(sg.p[1]); break;
        case SEG_Z: break;
      }
      segs.push_back(n);
    }
    out.segs = segs;
  } else {
    out = p;
  }
  if (outWidth) *outWidth = w;
  return out;
}

Resolved resolveObject(const Design& d, const Obj& o, FontRegistry& fonts, const AssetStore& assets,
                       bool includeGuides) {
  Resolved r;
  if (!o.visible) return r;
  if (o.layer == LAYER_GUIDES && !includeGuides) return r;
  Path base;
  if (o.type == "text") {
    Font* f = fonts.get(o.font);
    double w = 0;
    base = textPath(f, o.text, o.size, o.tracking, o.align, o.warp, o.warpAmount, Vec2(0, 0), 0, &w);
    base.apply(Transform2::translate(o.x, o.y));
  } else if (o.type == "qr") {
    base = qrPath(o.qrData, o.qrSize);
    base.apply(Transform2::translate(o.x, o.y));
  } else if (o.type == "image") {
    std::string b64 = d.assets.has(o.asset) ? d.assets[o.asset].str() : std::string();
    if (b64.empty()) { r.error = "brak danych obrazu"; return r; }
    auto bytes = base64Decode(b64);
    Image img;
    if (!pngDecode(bytes, img)) { r.error = "nie można zdekodować obrazu"; return r; }
    img = img.toGray();
    if (std::fabs(o.brightness) > 0.01 || std::fabs(o.contrast) > 0.01)
      img = imgBrightnessContrast(img, o.brightness, o.contrast);
    r.hasImage = true;
    r.image = img;
    r.imageRect = Rect(o.x, o.y, o.imgW > 0 ? o.imgW : 50, o.imgH > 0 ? o.imgH : 50);
    r.threshold = o.threshold;
    r.dither = o.dither;
    r.ok = true;
    return r;
  } else {
    base = Path::fromSVG(o.d);
    base.apply(Transform2::translate(o.x, o.y));
  }
  if (base.empty()) return r;
  Rect bb = base.bounds();
  double cx = d.product.w / 2, cy = d.product.h / 2;
  Transform2 sc = Transform2::translate(cx, cy).mul(Transform2::scale(o.sx, o.sy)).mul(Transform2::translate(-cx, -cy));
  Transform2 rt = Transform2::translate(cx, cy).mul(Transform2::rotate(o.rot)).mul(Transform2::translate(-cx, -cy));
  base.apply(sc);
  base.apply(rt);
  (void)bb;
  if (o.role == "fill") r.fills.push_back(base);
  else r.strokes.push_back(base);
  r.ok = true;
  return r;
}

Image renderDesign(const Design& d, FontRegistry& fonts, const AssetStore& assets, double pxPerMm,
                   bool includeGuides, bool includeRaster, bool monochrome) {
  int W = std::max(1, (int)std::lround(d.product.w * pxPerMm));
  int H = std::max(1, (int)std::lround(d.product.h * pxPerMm));
  if ((long long)W * H > 40LL * 1000 * 1000) {
    double k = std::sqrt(40.0e6 / ((double)W * H));
    W = std::max(1, (int)(W * k));
    H = std::max(1, (int)(H * k));
    pxPerMm = pxPerMm * k;
  }
  Canvas cv(W, H, 255);
  // kolejność: raster → wypełnienia → linie → cięcie → prowadnice
  std::vector<std::string> order = {LAYER_ENGRAVE_RASTER, LAYER_ENGRAVE_FILL, LAYER_ENGRAVE_VECTOR,
                                    LAYER_SCORE, LAYER_CUT_OUTER, LAYER_CUT_INNER, LAYER_GUIDES};
  for (auto& layerId : order) {
    LayerDef* ld = const_cast<Design&>(d).layer(layerId);
    if (ld && !ld->visible) continue;
    if (layerId == LAYER_ENGRAVE_RASTER && !includeRaster) continue;
    for (auto& o : d.objects) {
      if (o.layer != layerId) continue;
      Resolved res = resolveObject(d, o, fonts, assets, includeGuides);
      if (!res.ok) continue;
      double val = 0;
      if (layerId == LAYER_GUIDES) val = 190;
      else if (layerId == LAYER_ENGRAVE_RASTER) val = 0;
      else if (monochrome) val = layerId == LAYER_CUT_INNER ? 40 : 0;
      // geometria obiektów jest w mm — Canvas pracuje w pikselach
      Transform2 toPx = Transform2::scale(pxPerMm, pxPerMm);
      for (auto& f : res.fills) {
        Path q = f;
        q.apply(toPx);
        auto cs = q.flatten(0.3);
        // reguła even-odd: otwory (ring, zęby koła, okienka liter) są wycinane
        cv.fillContours(cs, true, val);
      }
      for (auto& s : res.strokes) {
        double w = std::max(0.08, o.strokeW);
        Path q = s;
        q.apply(toPx);
        cv.strokePath(q, w, pxPerMm, val);
      }
      if (res.hasImage) {
        Rect pr(res.imageRect.x * pxPerMm, res.imageRect.y * pxPerMm,
                res.imageRect.w * pxPerMm, res.imageRect.h * pxPerMm);
        cv.drawImageGray(res.image, pr, pxPerMm, res.threshold, res.dither);
      }
    }
  }
  return cv.image();
}

}  // namespace lc
