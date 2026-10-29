#include "compose.h"
#include <map>
#include "emblems.h"
#include "qr.h"
#include "validate.h"
#include <cmath>

namespace lc {

std::string autoFontForStyle(const std::string& style) {
  std::string s = lowerAscii(stripDiacritics(style));
  if (s == "gotyk") return "cinzel_deco";
  if (s == "elegancki") return "playfair";
  if (s == "fantasy") return "pirata";
  if (s == "minimal") return "oswald";
  if (s == "komiks") return "barlow";
  if (s == "rycina") return "playfair";
  if (s == "vintage" || s == "retro") return "abril";
  return "anton";
}

double fitTextSize(FontRegistry& fonts, const std::string& text, const std::string& font, double maxWidth,
                   double maxSize, double minSize, double tracking) {
  Font* f = fonts.get(font);
  if (!f || !f->valid() || text.empty()) return minSize;
  // miara dla 100 mm → skalowanie liniowe
  double w100 = f->measure(text, 100, tracking * 100 / 10.0);
  double s = maxWidth / std::max(0.01, w100) * 100.0;
  // std::clamp z maxSize < minSize jest niezdefiniowane — zabezpieczenie
  return std::clamp(s, minSize, std::max(minSize, maxSize));
}

namespace {

Obj makeTextObj(Design& d, const std::string& text, const std::string& font, double size, double x, double y,
                const std::string& layer, const std::string& name, const std::string& align = "center",
                const std::string& warp = "none", double warpAmount = 0) {
  Obj o;
  o.id = d.nextId("t");
  o.type = "text";
  o.layer = layer;
  o.role = (layer == LAYER_ENGRAVE_FILL) ? "fill" : "line";
  o.name = name;
  o.text = utf8Sanitize(text);
  o.font = font;
  o.size = size;
  o.tracking = size * 0.02;
  o.x = x;
  o.y = y;
  o.align = align;
  o.warp = warp;
  o.warpAmount = warpAmount;
  o.strokeW = (layer == LAYER_ENGRAVE_FILL) ? 0.0 : 0.3;
  return o;
}

Obj makePathObj(Design& d, const Path& p, const std::string& layer, const std::string& name,
                const std::string& role = "line", double strokeW = 0.25) {
  Obj o;
  o.id = d.nextId("p");
  o.type = "path";
  o.layer = layer;
  o.role = role;
  o.name = name;
  o.d = p.toSVG();
  o.strokeW = strokeW;
  return o;
}

// Dopasowanie tekstu do obszaru: skaluje w dół, jeśli wychodzi poza szerokość,
// i przesuwa w pionie, aby cała treść (łącznie z polskimi znakami) mieściła się w obszarze.
void fitTextInArea(Design& d, Obj& o, FontRegistry& fonts, const Rect& area, double pad = 0.6) {
  Font* f = fonts.get(o.font);
  if (!f || !f->valid()) return;
  Rect inner(area.x + pad, area.y + pad, std::max(1.0, area.w - 2 * pad), std::max(1.0, area.h - 2 * pad));
  for (int attempt = 0; attempt < 24; attempt++) {
    Path p = textPath(f, o.text, o.size, o.tracking, o.align, o.warp, o.warpAmount, Vec2(o.x, o.y));
    if (p.empty()) return;
    Rect b = p.bounds();
    bool tooWide = b.w > inner.w;
    bool tooTall = b.h > inner.h;
    if (tooWide || tooTall) {
      double k = std::min(tooWide ? inner.w / b.w : 1.0, tooTall ? inner.h / b.h : 1.0);
      o.size *= std::max(0.75, k * 0.98);
      o.tracking = o.size * 0.02;
      continue;
    }
    double dy = 0;
    if (b.y < inner.y) dy = inner.y - b.y;
    else if (b.y1() > inner.y1()) dy = inner.y1() - b.y1();
    o.y += dy;
    return;
  }
}

// dodaj emblemat przeskalowany do zadanego prostokąta
void addEmblem(Design& d, const std::string& emblemId, const Rect& area, const std::string& detail,
               std::vector<std::string>& log) {
  const Emblem* e = emblem(emblemId);
  if (!e) return;
  Rect b = e->bounds;
  if (b.w <= 0 || b.h <= 0) return;
  double s = std::min(area.w / b.w, area.h / b.h);
  double dx = area.cx() - b.cx() * s;
  double dy = area.cy() - b.cy() * s;
  int idx = 0;
  for (auto& part : e->parts) {
    Path p = part.path.transformed(Transform2{s, 0, 0, s, dx, dy});
    if (part.filled) {
      Obj o = makePathObj(d, p, LAYER_ENGRAVE_FILL, emblemId + "_fill" + std::to_string(idx), "fill", 0.0);
      o.extra.set("emblem", emblemId);
      o.extra.set("part", "fill");
      d.objects.push_back(o);
    }
    if (part.stroke) {
      Obj o = makePathObj(d, p, LAYER_ENGRAVE_VECTOR, emblemId + "_line" + std::to_string(idx), "line",
                          detail == "niski" ? 0.45 : 0.3);
      o.extra.set("emblem", emblemId);
      o.extra.set("part", "line");
      d.objects.push_back(o);
    }
    if (part.hatchSpacing > 0 && detail != "niski") {
      double spacing = part.hatchSpacing * (detail == "wysoki" ? 0.8 : 1.2);
      Path hatch;
      for (auto& c : p.flatten(0.2)) {
        auto lines = hatchContour(c, spacing, part.hatchAngle, 0.6);
        for (auto& ln : lines) {
          hatch.moveTo(ln.first);
          hatch.lineTo(ln.second);
        }
      }
      if (!hatch.empty()) {
        Obj o = makePathObj(d, hatch, LAYER_ENGRAVE_VECTOR, emblemId + "_hatch", "line", 0.2);
        o.extra.set("emblem", emblemId);
        o.extra.set("part", "hatch");
        d.objects.push_back(o);
      }
    }
    idx++;
  }
  log.push_back("Dodano emblemat '" + emblemId + "' w obszarze " + sfmt("%.0f×%.0f mm", area.w, area.h));
}

}  // namespace

GenerateResult generateDesign(const PromptSpec& spec, FontRegistry& fonts, AssetStore& assets,
                              const GenerateOptions& opt) {
  GenerateResult res;
  Design& d = res.design;
  d.id = "proj-" + std::to_string((uint32_t)(opt.seed ? opt.seed : 1));
  d.created = d.modified = nowIso();
  d.prompt = spec.rawPrompt;
  d.category = spec.category;
  d.style = opt.variantStyle.empty() ? (opt.styleOverride.empty() ? spec.style : opt.styleOverride)
                                     : opt.variantStyle;
  d.detail = spec.detail;
  d.engraveType = spec.engraveType;

  // produkt i maszyna
  const ProductDef* pdef = findProduct(spec.productId);
  if (!pdef) pdef = findProduct("tabliczka");
  applyProduct(d, *pdef, spec.material.empty() ? pdef->defaultMaterial : spec.material);
  if (spec.dimsGiven) {
    d.product.w = spec.width;
    d.product.h = spec.height;
  }
  if (opt.forceCut) d.product.requiresCut = true;
  if (opt.forceNoCut) d.product.requiresCut = false;
  if (!spec.cutGiven && !opt.forceCut && !opt.forceNoCut) d.product.requiresCut = pdef->cut;
  const MachineDef* mdef = findMachine(spec.machineId);
  if (mdef) applyMachine(d, *mdef);
  d.name = pdef->name + " — " + (spec.topText.empty() ? spec.category : spec.topText);
  if (d.name.size() > 60) d.name = d.name.substr(0, 60);

  double W = d.product.w, H = d.product.h;
  double margin = std::max(2.0, d.product.engraveMargin);
  double topMargin = margin;
  if (d.product.shape == "tag") {
    // strefa otworu na sznurek (patrz productShape): 2*(r+3) od góry
    double holeR = std::max(1.8, std::min(4.5, H * 0.05));
    topMargin = std::max(margin, 2 * (holeR + 3.0) + 1.5);
  }
  Rect outer(margin, topMargin, W - 2 * margin, H - margin - topMargin);
  if (outer.w < 10 || outer.h < 10) {
    res.error = "Obszar grawerowania jest zbyt mały — zwiększ wymiary lub zmniejsz margines.";
    return res;
  }
  std::string detail = d.detail;
  std::string style = d.style;
  bool minimal = style == "minimal" || detail == "niski";
  bool ornate = style == "rycina" || style == "gotyk" || detail == "wysoki" || style == "retro" || style == "vintage";

  // --- ramka ---
  Rect frameArea = outer;
  double frameT = minimal ? 0.6 : 1.0;
  if (opt.includeFrame &&
      !(d.product.shape == "circle" || d.product.shape == "oval" || d.product.shape == "shield")) {
    Path frame = framePath(style == "minimal" ? "simple" : spec.frameStyle.empty() ? frameBestMatch(style) : spec.frameStyle,
                           frameArea, frameT);
    if (!frame.empty()) {
      Obj o = makePathObj(d, frame, LAYER_ENGRAVE_VECTOR, "Ramka", "line", minimal ? 0.4 : 0.3);
      d.objects.push_back(o);
      res.log.push_back("Ramka w stylu '" + (spec.frameStyle.empty() ? frameBestMatch(style) : spec.frameStyle) + "'");
    }
    frameArea = frameArea.inflate(-(frameT + 4.0));
  } else if (d.product.shape == "circle" || d.product.shape == "oval") {
    // ramka okrągła dopasowana do kształtu
    Rect r = outer.inflate(-2.0);
    Path ring;
    ring.addEllipse(r.cx(), r.cy(), r.w / 2 * 0.96, r.h / 2 * 0.96);
    Path ring2;
    ring2.addEllipse(r.cx(), r.cy(), r.w / 2 * 0.9, r.h / 2 * 0.9);
    ring.addPath(ring2);
    d.objects.push_back(makePathObj(d, ring, LAYER_ENGRAVE_VECTOR, "Ramka okrągła", "line", 0.3));
    frameArea = Rect(r.cx() - r.w * 0.42, r.cy() - r.h * 0.42, r.w * 0.84, r.h * 0.84);
  }
  if (frameArea.w < 12 || frameArea.h < 12) frameArea = outer;

  // --- teksty ---
  TextSpec ts;
  ts.category = spec.category;
  ts.topic = spec.category;
  ts.occasion = spec.occasion;
  ts.tone = spec.tone;
  ts.keywords = spec.topText + " " + spec.bottomText;
  if (!opt.textTop.empty()) { ts.exactText = opt.textTop; if (!opt.textBottom.empty()) ts.exactText += "\n" + opt.textBottom; }
  else if (!spec.topText.empty() || !spec.bottomText.empty()) {
    ts.exactText = spec.topText;
    if (!spec.bottomText.empty()) ts.exactText += "\n" + spec.bottomText;
  }
  ts.dedication = spec.dedication;
  res.texts = generateTexts(ts, 4, opt.seed ? opt.seed : 7);
  std::string topText = !opt.textTop.empty() ? opt.textTop
                        : !spec.topText.empty() ? spec.topText
                                                : (res.texts.empty() ? "TWÓJ NAPIS" : res.texts[0].top);
  std::string bottomText = !opt.textBottom.empty() ? opt.textBottom
                           : !spec.bottomText.empty() ? spec.bottomText
                                                      : (res.texts.empty() ? "" : res.texts[0].bottom);
  topText = fixPolishSpelling(topText);
  bottomText = fixPolishSpelling(bottomText);
  if (minimal) {
    topText = shortenText(topText, 20);
    bottomText = shortenText(bottomText, 34);
  }

  std::string fontMain = spec.fontId.empty() ? autoFontForStyle(style) : spec.fontId;
  std::string fontSub = (style == "gotyk") ? "cinzel" : (style == "elegancki" ? "playfair" : "bebas");
  double contentW = frameArea.w;

  // górny napis
  // w kategoriach z dekoracją w narożnikach (śnieżynki, księżyc) zwężamy pole napisu,
  // żeby motyw nie wchodził na litery
  bool cornerDecor = (spec.category == "boze_narodzenie" || spec.category == "halloween");
  double headW = cornerDecor ? frameArea.w * 0.66 : frameArea.w * 0.90;
  double maxTop = std::min(frameArea.h * 0.24, contentW * 0.26);
  double topSize = fitTextSize(fonts, topText, fontMain, headW, maxTop, 3.0, 0.02);
  double topBaseline = frameArea.y + topSize * 1.05;
  Obj topObj = makeTextObj(d, topText, fontMain, topSize, frameArea.cx(), topBaseline, LAYER_ENGRAVE_FILL, "Napis główny");
  fitTextInArea(d, topObj, fonts,
                Rect(frameArea.cx() - headW / 2, frameArea.y, headW, std::max(4.0, frameArea.h * 0.34)));
  if (topObj.size < 2.9 && topText.size() > 14) {
    // za długi napis dla wąskiego pola — skróć zamiast pisać nieczytelnie
    topText = shortenText(topText, 18);
    topObj.text = topText;
    topObj.size = fitTextSize(fonts, topText, fontMain, headW, maxTop, 3.0, 0.02);
    fitTextInArea(d, topObj, fonts,
                  Rect(frameArea.cx() - headW / 2, frameArea.y, headW, std::max(4.0, frameArea.h * 0.34)));
    res.log.push_back("Napis główny skrócony do: '" + topText + "'");
  }
  if (spec.dedication.empty()) d.objects.push_back(topObj);
  else {
    topObj.warp = "none";
    d.objects.push_back(topObj);
  }
  res.log.push_back(sfmt("Napis główny: '%s' (%.1f mm, font %s)", topText.c_str(), topSize, fontMain.c_str()));

  // dolny napis (zawijanie na maks. 2 linie)
  double bottomSize = 0;
  std::vector<std::string> bottomLines;
  if (!bottomText.empty()) {
    // podział na 2 linie po " — " lub ", "
    std::string sep = bottomText.find(" — ") != std::string::npos ? " — " : (bottomText.find(", ") != std::string::npos ? ", " : "");
    if (!sep.empty() && bottomText.size() > 26 && frameArea.h > 40) {
      auto parts = split(bottomText, sep[0]);
      std::string l1 = parts[0];
      std::string l2;
      for (size_t i = 1; i < parts.size(); i++) l2 += (i > 1 ? std::string(1, sep[0]) : "") + parts[i];
      bottomLines = {trim(l1), trim(l2)};
    } else {
      bottomLines = {bottomText};
    }
    double maxBottom = std::max(2.8, std::min(frameArea.h * 0.12, 12.0));
    for (auto& line : bottomLines) {
      bottomSize = std::max(bottomSize, fitTextSize(fonts, line, fontSub, contentW * 0.92, maxBottom, 2.8, 0.02));
    }
    bottomSize = bottomSize > 0 ? bottomSize : 3.0;
    // nie generujemy napisu mniejszego niż 2,5 mm — byłby nieczytelny po grawerowaniu
    if (frameArea.h < 26 || frameArea.w < 34) {
      res.log.push_back("Napis dolny pominięty — za mały obszar (" +
                        std::to_string((int) std::lround(frameArea.w)) + "×" +
                        std::to_string((int) std::lround(frameArea.h)) + " mm)");
      bottomLines.clear();
    }
  }

  // --- emblemat ---
  double topReserved = topSize * 1.5;
  double bottomReserved = bottomLines.empty() ? 0 : bottomSize * 1.4 * bottomLines.size() + 3;
  Rect emblemArea(frameArea.x + frameArea.w * 0.10, frameArea.y + topReserved + 2,
                  frameArea.w * 0.80, std::max(10.0, frameArea.h - topReserved - bottomReserved - 6));
  std::string subject = spec.primarySubject;
  if (subject.empty()) subject = emblemBestMatch(spec.category);
  if (subject.empty()) {
    // jawne przypisanie motywu do kategorii — żeby na pisance nie lądowało koło zębate
    static const char* catPairs[][2] = {
        {"wedkarstwo", "szczupak"}, {"myslistwo", "jelen"}, {"rolnictwo", "tractor"},
        {"motoryzacja", "samochod"}, {"garaz", "samochod"}, {"warsztat", "kolo_zebate"},
        {"muzyka", "gitara"}, {"gaming", "pad"}, {"zwierzeta", "lapa"}, {"wilki", "wilk"},
        {"przyroda", "drzewo"}, {"patriotyczne", "orzel"}, {"slowianskie", "kolo"},
        {"gotyk", "czaszka"}, {"halloween", "dynia"}, {"walentynki", "serce"},
        {"dzien_matki", "roza"}, {"dzien_ojca", "roza"}, {"sluby", "obraczki"},
        {"rocznice", "obraczki"}, {"boze_narodzenie", "choinka"}, {"wielkanoc", "jajko"},
        {"emblematy", "korona"}, {"sport", "medal"}, {"logo", "korona"},
        {"urodziny", "medal"}, {"imieniny", "medal"}, {"pamiatki", "medal"},
        {"prezenty", "serce"}, {"dedykacje", "roza"}, {"religijne", "korona"},
        {"retro", "kolo"}, {"vintage", "kolo"}, {"minimalistyczne", "kolo"},
        {"fotograwer", "kolo"}, {"portrety", "kolo"}, {"fantastyka", "smok"},
        {"komiksowe", "pad"}, {"hobby", "kolo"}, {"emerytura", "medal"},
        {"dzien_babci", "roza"}, {"dzien_dziadka", "medal"}, {"sluby", "obraczki"},
    };
    for (auto& kv : catPairs) {
      if (spec.category == kv[0]) { subject = kv[1]; break; }
    }
  }
  if (!subject.empty()) addEmblem(d, subject, emblemArea, detail, res.log);
  else res.log.push_back("Bez emblematu — kompozycja tekstowa (kategoria: " + spec.category + ")");
  if (!spec.secondarySubject.empty() && spec.secondarySubject != subject && detail != "niski") {
    Rect side(emblemArea.x, emblemArea.y + emblemArea.h * 0.45, emblemArea.w * 0.28, emblemArea.h * 0.5);
    Rect side2(emblemArea.x1() - emblemArea.w * 0.28, emblemArea.y + emblemArea.h * 0.45,
               emblemArea.w * 0.28, emblemArea.h * 0.5);
    addEmblem(d, spec.secondarySubject, side, "niski", res.log);
    addEmblem(d, spec.secondarySubject, side2, "niski", res.log);
  } else if (detail == "wysoki" && !minimal) {
    // symetryczne motywy poboczne dla bogactwa kompozycji
    double sz = std::min(emblemArea.w * 0.2, emblemArea.h * 0.3);
    if (sz > 8) {
      Rect left(emblemArea.x, emblemArea.cy() - sz / 2, sz, sz);
      Rect right(emblemArea.x1() - sz, emblemArea.cy() - sz / 2, sz, sz);
      std::string side = (spec.category == "wedkarstwo") ? "ryba" : (spec.category == "myslistwo" ? "jelen" : "");
      if (!side.empty()) {
        addEmblem(d, side, left, "niski", res.log);
        addEmblem(d, side, right, "niski", res.log);
      }
    }
  }

  // --- dolne napisy + banner ---
  if (!bottomLines.empty()) {
    double baseY = frameArea.y1() - (bottomLines.size() > 1 ? bottomSize * 1.35 : bottomSize * 0.35);
    if (ornate) {
      Rect banner(frameArea.x + frameArea.w * 0.06, baseY - bottomSize * 1.25, frameArea.w * 0.88, bottomSize * 1.7);
      Obj b = makePathObj(d, bannerRibbon(banner, std::min(6.0, banner.h * 0.35)), LAYER_ENGRAVE_VECTOR, "Wstęga", "line", 0.3);
      d.objects.push_back(b);
    }
    for (size_t i = 0; i < bottomLines.size(); i++) {
      double y = baseY - (bottomLines.size() - 1 - i) * bottomSize * 1.35;
      Obj o = makeTextObj(d, bottomLines[i], fontSub, bottomSize, frameArea.cx(), y, LAYER_ENGRAVE_FILL, "Napis dolny " + std::to_string(i + 1));
      d.objects.push_back(o);
    }
    res.log.push_back("Napis dolny: '" + join(bottomLines, " / ") + "'");
  }

  // --- ornamenty ---
  if (opt.includeOrnaments && !minimal) {
    if (detail == "wysoki") {
      double s = std::min(frameArea.w, frameArea.h) * 0.13;
      if (s > 6) {
        d.objects.push_back(makePathObj(d, cornerFlourish({frameArea.x + 1.5, frameArea.y + 1.5}, s, 0), LAYER_ENGRAVE_VECTOR, "Ornament 1", "line", 0.25));
        d.objects.push_back(makePathObj(d, cornerFlourish({frameArea.x1() - 1.5, frameArea.y1() - 1.5}, s, 2), LAYER_ENGRAVE_VECTOR, "Ornament 2", "line", 0.25));
      }
    }
    if (style == "slowianskie" || style == "slavic" || spec.category == "slowianskie") {
      d.objects.push_back(makePathObj(d, sunRays({emblemArea.cx(), emblemArea.cy()}, emblemArea.w * 0.42, emblemArea.w * 0.5, 24),
                                      LAYER_ENGRAVE_VECTOR, "Promienie", "line", 0.22));
    }
    if (spec.category == "halloween") {
      d.objects.push_back(makePathObj(d, moonsAndStars(Rect(frameArea.x, frameArea.y, frameArea.w, frameArea.h * 0.35)),
                                      LAYER_ENGRAVE_VECTOR, "Księżyc i gwiazdy", "line", 0.22));
    }
    if (spec.category == "boze_narodzenie") {
      d.objects.push_back(makePathObj(d, snowflake({frameArea.x1() - frameArea.w * 0.10, frameArea.y + frameArea.h * 0.10}, std::min(frameArea.w, frameArea.h) * 0.06, 6),
                                      LAYER_ENGRAVE_VECTOR, "Śnieżynka 1", "line", 0.22));
      d.objects.push_back(makePathObj(d, snowflake({frameArea.x + frameArea.w * 0.10, frameArea.y + frameArea.h * 0.90}, std::min(frameArea.w, frameArea.h) * 0.05, 6),
                                      LAYER_ENGRAVE_VECTOR, "Śnieżynka 2", "line", 0.22));
    }
    // separator pod napisem głównym
    if (detail != "niski" && ornate && frameArea.h > 50) {
      Vec2 a(frameArea.cx() - frameArea.w * 0.18, topBaseline + topSize * 0.35);
      Vec2 b(frameArea.cx() + frameArea.w * 0.18, topBaseline + topSize * 0.35);
      Path div = dividerLine(a, b, 0.8, 3);
      Path stars = starsRow({frameArea.cx(), topBaseline + topSize * 0.38}, 2, 1, topSize * 0.10);
      Path all = div;
      all.addPath(stars);
      d.objects.push_back(makePathObj(d, all, LAYER_ENGRAVE_VECTOR, "Separator", "line", 0.25));
    }
  }

  // --- dedykacja ---
  if (!spec.dedication.empty()) {
    double size = std::min(4.5, frameArea.h * 0.06);
    Obj o = makeTextObj(d, spec.dedication, "playfair", size, frameArea.cx(), frameArea.y1() - 1.0,
                        LAYER_ENGRAVE_VECTOR, "Dedykacja", "center");
    d.objects.push_back(o);
  }

  // --- kontur cięcia / prowadnice ---
  ensureCutContour(d);
  if (opt.includeGuides) {
    Path g;
    g.addRect(margin, margin, W - 2 * margin, H - 2 * margin);
    Obj o = makePathObj(d, g, LAYER_GUIDES, "Obszar grawerowania", "line", 0.15);
    o.locked = true;
    d.objects.insert(d.objects.begin(), o);
  }
  d.pushHistory("Wygenerowano projekt (" + style + ")");
  d.params.set("generated", true);
  d.params.set("style", style);
  d.params.set("subject", subject);
  res.metrics = computeMetrics(d, fonts, assets);
  res.ok = true;
  return res;
}

std::vector<GenerateResult> generateVariants(const PromptSpec& spec, FontRegistry& fonts,
                                             AssetStore& assets, const GenerateOptions& opt,
                                             const std::vector<std::string>& styles) {
  std::vector<GenerateResult> out;
  for (size_t i = 0; i < styles.size(); i++) {
    GenerateOptions o = opt;
    o.variantStyle = styles[i];
    o.seed = opt.seed + (uint32_t)i * 7919u;
    GenerateResult r = generateDesign(spec, fonts, assets, o);
    if (r.ok) {
      r.design.name = r.design.name + " [" + styles[i] + "]";
      r.design.params.set("variant", styles[i]);
    }
    out.push_back(std::move(r));
  }
  return out;
}

void refreshCutContour(Design& d) { ensureCutContour(d); }

Json computeMetrics(const Design& d, FontRegistry& fonts, const AssetStore& assets) {
  Json m = Json::object();
  int textCount = 0, pathCount = 0, imageCount = 0, qrCount = 0;
  for (auto& o : d.objects) {
    if (o.type == "text") textCount++;
    else if (o.type == "image") imageCount++;
    else if (o.type == "qr") qrCount++;
    else pathCount++;
  }
  m.set("texts", textCount);
  m.set("paths", pathCount);
  m.set("images", imageCount);
  m.set("qr", qrCount);
  m.set("engraveAreaMm2", designEngraveAreaMm2(d, fonts, assets));
  m.set("cutLengthMm", designCutLengthMm(d));
  m.set("productAreaMm2", d.product.w * d.product.h);
  Json layers = Json::array();
  for (auto& l : d.layers) {
    int n = 0;
    for (auto& o : d.objects) if (o.layer == l.id) n++;
    Json lj = Json::object();
    lj.set("id", l.id);
    lj.set("objects", (double)n);
    layers.push(lj);
  }
  m.set("layers", layers);
  ValidationReport rep = validateDesign(d, fonts, assets);
  m.set("validationErrors", rep.errors);
  m.set("validationWarnings", rep.warnings);
  return m;
}

// ---------------- edycja ----------------
EditResult applyIntent(Design& d, const EditIntent& in, FontRegistry& fonts, AssetStore& assets) {
  EditResult r;
  (void)assets;
  auto fail = [&](const std::string& msg) {
    r.message = msg;
    r.failed.push_back(msg);
    return r;
  };
  auto findTextObj = [&](bool top) -> Obj* {
    Obj* best = nullptr;
    for (auto& o : d.objects) {
      if (o.type != "text") continue;
      if (o.name.find(top ? "główny" : "dolny") != std::string::npos) return &o;
      if (!best) best = &o;
    }
    return best;
  };
  if (in.action == "undo") {
    if (!d.undo()) return fail("Nie ma czego cofać.");
    r.ok = true;
    r.message = "Cofnięto zmianę.";
    r.applied.push_back("cofnięcie");
    return r;
  }
  if (in.action == "redo") {
    if (!d.redo()) return fail("Nie ma czego ponawiać.");
    r.ok = true;
    r.message = "Ponowiono zmianę.";
    r.applied.push_back("ponowienie");
    return r;
  }
  if (in.action == "scale_text") {
    Obj* t = findTextObj(true);
    if (!t) return fail("W projekcie nie ma napisu do powiększenia.");
    double f = 1.0 + in.amount;
    if (f < 0.2) f = 0.2;
    double old = t->size;
    t->size *= f;
    t->tracking *= f;
    t->y = t->y - (t->size - old) * 0.5;  // utrzymaj wizualne wyrównanie
    r.ok = true;
    r.message = sfmt("Zmieniono rozmiar napisu '%s' z %.1f na %.1f mm.", t->text.c_str(), old, t->size);
    r.applied.push_back("skalowanie tekstu");
    d.pushHistory("Skalowanie tekstu");
    return r;
  }
  if (in.action == "replace_subject") {
    if (in.value.empty()) return fail("Nie rozpoznałem nowego motywu (np. 'zamień rybę na wilka').");
    // usuń emblematy, zostaw teksty/ramkę
    bool found = false;
    for (auto it = d.objects.begin(); it != d.objects.end();) {
      if (it->extra.has("emblem")) { it = d.objects.erase(it); found = true; }
      else ++it;
    }
    double margin = d.product.engraveMargin;
    Rect area(margin + d.product.w * 0.12, margin + d.product.h * 0.22,
              d.product.w * 0.76, d.product.h * 0.50);
    std::vector<std::string> log;
    addEmblem(d, in.value, area, d.detail, log);
    r.ok = true;
    r.message = std::string(found ? "Zamieniono motyw" : "Dodano motyw") + " na '" + in.value + "'.";
    r.applied.push_back("zamiana motywu");
    d.pushHistory("Zamiana motywu na " + in.value);
    (void)fonts;
    return r;
  }
  if (in.action == "funnier" || in.action == "keep_subject_new_text" || in.action == "set_text") {
    Obj* bottom = findTextObj(false);
    Obj* top = findTextObj(true);
    if (in.action == "set_text" && !in.value.empty()) {
      if (!top) return fail("Brak napisu do zmiany.");
      top->text = in.value;
      r.ok = true;
      r.message = "Ustawiono nowy napis: '" + in.value + "'.";
      r.applied.push_back("zmiana tekstu");
      d.pushHistory("Zmiana tekstu");
      return r;
    }
    TextSpec ts;
    ts.category = d.category;
    ts.tone = d.params.has("tone") ? d.params["tone"].str("zartobliwy") : "zartobliwy";
    auto sugg = generateTexts(ts, 8, (uint32_t)(d.objects.size() * 131 + 17));
    std::string chosenTop = top ? top->text : "";
    std::string chosenBottom;
    for (auto& s : sugg) {
      if (s.top != chosenTop) { chosenTop = s.top; chosenBottom = s.bottom; break; }
    }
    if (chosenBottom.empty() && !sugg.empty()) chosenBottom = sugg[0].bottom;
    if (in.action == "funnier" && bottom) {
      bottom->text = adjustText(bottom->text, "śmieszniej");
      r.message = "Dodano śmieszniejszą puentę: '" + bottom->text + "'.";
      r.applied.push_back("zmiana puenty");
    } else {
      if (top) top->text = chosenTop;
      if (bottom) bottom->text = chosenBottom;
      else {
        Obj o;
        o.id = d.nextId("t");
        o.type = "text";
        o.layer = LAYER_ENGRAVE_FILL;
        o.role = "fill";
        o.name = "Napis dolny 1";
        o.text = chosenBottom;
        o.font = top ? top->font : "bebas";
        o.size = top ? top->size * 0.55 : 5;
        o.x = d.product.w / 2;
        o.y = d.product.h - d.product.engraveMargin - o.size;
        o.align = "center";
        d.objects.push_back(o);
      }
      r.message = sfmt("Nowy tekst: '%s' / '%s'.", chosenTop.c_str(), chosenBottom.c_str());
      r.applied.push_back("nowy tekst");
    }
    r.ok = true;
    d.pushHistory("Nowy tekst");
    (void)fonts;
    return r;
  }
  if (in.action == "remove_background") {
    int removed = 0;
    for (auto it = d.objects.begin(); it != d.objects.end();) {
      if (it->type == "image" || it->layer == LAYER_ENGRAVE_RASTER) { it = d.objects.erase(it); removed++; }
      else ++it;
    }
    r.ok = true;
    r.message = removed ? sfmt("Usunięto %d elementów tła/rastra.", removed) : "Nie znalazłem tła do usunięcia.";
    r.applied.push_back("usuwanie tła");
    d.pushHistory("Usuwanie tła");
    return r;
  }
  if (in.action == "shape_circle") {
    d.product.shape = "circle";
    refreshCutContour(d);
    r.ok = true;
    r.message = "Zmieniono kształt na okrągły (z nowym konturem cięcia).";
    r.applied.push_back("kształt okrągły");
    d.pushHistory("Kształt okrągły");
    return r;
  }
  if (in.action == "add_holes") {
    int n = std::max(1, (int)std::lround(in.amount > 0 ? in.amount : 2));
    double margin = d.product.engraveMargin;
    for (int i = 0; i < n; i++) {
      Obj o;
      o.id = d.nextId("hole");
      o.type = "path";
      o.layer = LAYER_CUT_INNER;
      o.role = "hole";
      o.name = "Otwór montażowy " + std::to_string(i + 1);
      o.strokeW = 0.1;
      Path p;
      if (n == 1) p.addCircle(d.product.w / 2, margin * 0.9, 2.5);
      else {
        double x = margin + (d.product.w - 2 * margin) * (i / (double)(n - 1));
        p.addCircle(x, margin * 0.9, 2.0);
      }
      o.d = p.toSVG();
      d.objects.insert(d.objects.begin(), o);
    }
    if (!d.product.requiresCut) r.message = "Dodano otwory, ale produkt nie jest wycinany — włącz cięcie, aby je wyeksportować.";
    else r.message = sfmt("Dodano %d otworów montażowych (warstwa CUT_INNER).", n);
    r.ok = true;
    r.applied.push_back("otwory montażowe");
    d.pushHistory("Otwory montażowe");
    return r;
  }
  if (in.action == "move_text_up" || in.action == "move_text_down") {
    double dy = d.product.h * 0.06 * (in.action == "move_text_up" ? -1 : 1);
    int moved = 0;
    for (auto& o : d.objects) {
      if (o.type == "text") { o.y += dy; moved++; }
    }
    if (!moved) return fail("Brak napisów do przesunięcia.");
    r.ok = true;
    r.message = sfmt("Przesunięto %d napisów o %.1f mm %s.", moved, std::fabs(dy),
                     in.action == "move_text_up" ? "w górę" : "w dół");
    r.applied.push_back("przesunięcie napisów");
    d.pushHistory("Przesunięcie napisów");
    return r;
  }
  if (in.action == "lighten") {
    LayerDef* l = d.layer(LAYER_ENGRAVE_FILL);
    if (l) l->power = std::max(5.0, l->power * 0.75);
    LayerDef* l2 = d.layer(LAYER_ENGRAVE_VECTOR);
    if (l2) l2->power = std::max(5.0, l2->power * 0.75);
    for (auto& o : d.objects) {
      if (o.type == "image") { o.brightness += 12; o.threshold = std::min(240, o.threshold + 10); }
    }
    r.ok = true;
    r.message = "Rozjaśniono grawer (mniejsza moc, wyższy próg dla rastra).";
    r.applied.push_back("rozjaśnienie");
    d.pushHistory("Rozjaśnienie graweru");
    return r;
  }
  if (in.action == "change_frame") {
    double margin = std::max(2.0, d.product.engraveMargin);
    Rect outer(margin, margin, d.product.w - 2 * margin, d.product.h - 2 * margin);
    std::string style = in.value.empty() ? "ornate" : in.value;
    Path frame = framePath(style, outer, 1.0);
    if (frame.empty()) return fail("Nie udało się zbudować ramki.");
    for (auto it = d.objects.begin(); it != d.objects.end();) {
      if (it->name.find("Ramka") != std::string::npos) it = d.objects.erase(it);
      else ++it;
    }
    d.objects.push_back(makePathObj(d, frame, LAYER_ENGRAVE_VECTOR, "Ramka (" + style + ")", "line", 0.3));
    r.ok = true;
    r.message = "Zmieniono ramkę na styl '" + style + "' (reszta projektu bez zmian).";
    r.applied.push_back("zmiana ramki");
    d.pushHistory("Zmiana ramki");
    return r;
  }
  if (in.action == "simplify") {
    int removed = 0;
    for (auto it = d.objects.begin(); it != d.objects.end();) {
      bool isHatch = it->extra.has("part") && it->extra["part"].str() == "hatch";
      bool isOrnament = it->name.find("Ornament") != std::string::npos || it->name.find("Separator") != std::string::npos;
      if (isHatch || isOrnament) { it = d.objects.erase(it); removed++; }
      else { if (it->strokeW < 0.3) it->strokeW = 0.3; ++it; }
    }
    d.detail = "niski";
    r.ok = true;
    r.message = sfmt("Uproszczono grafikę (usunięto %d elementów cieni/ornamentów) — szybszy grawer.", removed);
    r.applied.push_back("uproszczenie");
    d.pushHistory("Uproszczenie grafiki");
    return r;
  }
  if (in.action == "add_dedication") {
    Obj o;
    o.id = d.nextId("t");
    o.type = "text";
    o.layer = LAYER_ENGRAVE_VECTOR;
    o.role = "line";
    o.name = "Dedykacja";
    o.text = in.value.empty() ? dedicationFor("Ciebie", "") : in.value;
    o.font = "playfair";
    o.size = std::min(4.5, d.product.h * 0.05);
    o.x = d.product.w / 2;
    o.y = d.product.h - d.product.engraveMargin - 1.5;
    o.align = "center";
    o.strokeW = 0.3;
    d.objects.push_back(o);
    r.ok = true;
    r.message = "Dodano dedykację: '" + o.text + "'.";
    r.applied.push_back("dedykacja");
    d.pushHistory("Dedykacja");
    return r;
  }
  if (in.action == "add_qr") {
    Obj o;
    o.id = d.nextId("qr");
    o.type = "qr";
    o.layer = LAYER_ENGRAVE_VECTOR;
    o.role = "fill";
    o.name = "Kod QR";
    o.qrData = in.value.empty() ? "https://lasercraft.pl" : in.value;
    o.qrSize = std::min(d.product.w, d.product.h) * 0.25;
    o.x = d.product.w - d.product.engraveMargin - o.qrSize;
    o.y = d.product.h - d.product.engraveMargin - o.qrSize;
    QrCode qr = qrEncode(o.qrData);
    if (!qr.ok) return fail("Dane dla QR są za długie.");
    d.objects.push_back(o);
    r.ok = true;
    r.message = "Dodano kod QR (" + qrSelfCheck(qr) + ").";
    r.applied.push_back("kod QR");
    d.pushHistory("Kod QR");
    return r;
  }
  if (in.action == "rotate") {
    double a = in.amount > 0 ? in.amount : 90;
    int n = 0;
    for (auto& o : d.objects) {
      if (o.locked || o.layer == LAYER_GUIDES) continue;
      o.rot = std::fmod(o.rot + a, 360.0);
      n++;
    }
    r.ok = true;
    r.message = sfmt("Obrócono %d obiektów o %.0f°.", n, a);
    r.applied.push_back("obrót");
    d.pushHistory("Obrót");
    return r;
  }
  if (in.action == "lock" || in.action == "unlock") {
    int n = 0;
    for (auto& o : d.objects) {
      if (o.type == "text" || o.extra.has("emblem")) {
        o.locked = (in.action == "lock");
        n++;
      }
    }
    r.ok = true;
    r.message = std::string(in.action == "lock" ? "Zablokowano" : "Odblokowano") + sfmt(" %d obiektów.", n);
    r.applied.push_back(in.action == "lock" ? "blokada" : "odblokowanie");
    return r;
  }
  if (in.action == "no_cut") {
    d.product.requiresCut = false;
    refreshCutContour(d);
    r.ok = true;
    r.message = "Ustawiono tryb gotowego przedmiotu — usunięto linie cięcia.";
    r.applied.push_back("tryb gotowego przedmiotu");
    d.pushHistory("Tryb gotowego przedmiotu");
    return r;
  }
  if (in.action == "fit_text") {
    Obj* t = findTextObj(true);
    if (!t) return fail("Brak napisu.");
    double maxW = d.product.w - 2 * d.product.engraveMargin - 6;
    double s = fitTextSize(fonts, t->text, t->font, maxW, t->size, 2.5, 0.02);
    t->size = s;
    r.ok = true;
    r.message = sfmt("Dopasowano tekst do szerokości (%.1f mm).", s);
    r.applied.push_back("dopasowanie tekstu");
    d.pushHistory("Dopasowanie tekstu");
    return r;
  }
  return fail("Nie rozpoznałem polecenia: '" + in.raw + "'");
}

EditResult applyEditText(Design& d, const std::string& command, FontRegistry& fonts, AssetStore& assets) {
  EditResult total;
  auto intents = parseEditCommands(command);
  if (intents.empty()) {
    total.message = "Nie rozpoznałem polecenia.";
    total.failed.push_back("puste polecenie");
    return total;
  }
  for (auto& in : intents) {
    EditResult r = applyIntent(d, in, fonts, assets);
    for (auto& a : r.applied) total.applied.push_back(a);
    for (auto& f : r.failed) total.failed.push_back(f);
    if (!total.message.empty() && !r.message.empty()) total.message += " ";
    total.message += r.message;
    total.ok = total.ok || r.ok;
  }
  d.modified = nowIso();
  return total;
}

}  // namespace lc
