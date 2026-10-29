#include "core_api.h"
#include "emblems.h"
#include "qr.h"
#include "library.h"
#include "zip.h"
#include <cmath>
#include <filesystem>
#include <set>

namespace lc {

static Json specToJson(const PromptSpec& s);

CostResult calculateCost(const CostInput& in) {
  CostResult r;
  double areaM2 = std::max(0.0, in.areaMm2) / 1e6;
  double waste = 1.0 + in.wastePercent / 100.0;
  r.materialCost = in.materialCostPerM2 * areaM2 * waste;
  // czas: pole grawerowania / prędkość + długość cięcia / prędkość
  double engraveMm2 = in.engraveAreaMm2 > 0 ? in.engraveAreaMm2 : in.areaMm2 * 0.45;
  r.engraveMinutes = (engraveMm2 / std::max(1.0, in.engraveSpeedMmS * 60.0)) * in.passes;
  r.cutMinutes = (in.cutLengthMm / std::max(0.5, in.cutSpeedMmS * 60.0)) * in.passes;
  r.machineMinutes = in.setupMinutes + r.engraveMinutes + r.cutMinutes;
  double kWh = (in.machinePowerW / 1000.0) * (r.machineMinutes / 60.0) * 0.35;  // 35% średnie obciążenie
  r.energyCost = kWh * in.energyPriceKwh;
  r.laborCost = (r.machineMinutes / 60.0) * in.hourlyRate;
  r.wearCost = (r.machineMinutes / 60.0) * 3.0;  // orientacyjny koszt eksploatacji (soczewki, dysze, prowadnice)
  r.totalCost = r.materialCost + r.energyCost + r.laborCost + r.wearCost + in.packaging;
  r.price = r.totalCost * (1.0 + in.marginPercent / 100.0) + in.shipping;
  r.profit = r.totalCost * (in.marginPercent / 100.0);
  r.vatPrice = r.price * 1.23;
  r.estimateOnly = true;
  r.note = "Czasy są orientacyjne — opierają się na prędkościach z profilu i nie uwzględniają hamowania, "
           "przejazdów jałowych i rzeczywistej charakterystyki maszyny. Dla wiążącej wyceny zmierz czas na próbce.";
  return r;
}

const std::vector<TrendEntry>& offlineTrends() {
  static const std::vector<TrendEntry> list = {
      {"Personalizowane tabliczki dla wędkarzy", "wedkarstwo",
       "Szczupak + napis z imieniem + wymiary połowu; sezon wiosna–jesień.", "wiosna-lato",
       "biblioteka własna WOJAN", "2026-01-15", {"wedkarstwo", "retro", "prezent"}},
      {"Szyldy warsztatowe z łacińskimi sentencjami", "warsztat",
       "Hasła typu 'Memento mori' i 'Festina lente' jako ozdoba garażu.", "cały rok",
       "biblioteka własna WOJAN", "2026-01-15", {"warsztat", "gotyk", "szyld"}},
      {"Naszywki skórzane z inicjałami", "odziez",
       "Małe naszywki 80×40 mm, monogram + data, popularne jako dodatek do prezentu.", "cały rok",
       "biblioteka własna WOJAN", "2026-02-02", {"skora", "naszywka", "prezent"}},
      {"Ozdoby choinkowe z imieniem", "boze_narodzenie",
       "Zawieszki 80×90 mm z imieniem i rokiem — sprzedaż od października.", "Q4",
       "biblioteka własna WOJAN", "2026-02-02", {"swieta", "ozdoba", "imie"}},
      {"Zakładki do książek z motywem literackim", "pamiatki",
       "Cienka sklejka 2 mm, motyw kwiatowy lub pióro, sentencja.", "cały rok",
       "biblioteka własna WOJAN", "2026-03-01", {"zakladka", "prezent", "minimal"}},
      {"Medale i nagrody dla klubów sportowych", "sport",
       "Medal 60 mm z nazwą klubu i rokiem — zamówienia grupowe.", "cały rok",
       "biblioteka własna WOJAN", "2026-03-01", {"medal", "sport", "klub"}},
      {"Grawer na piersiówkach z motywem wilka", "prezenty",
       "Grawer bez cięcia, popularny prezent męski; wymaga sprawdzenia krzywizny.", "Q4",
       "biblioteka własna WOJAN", "2026-03-15", {"piersiowka", "wilk", "prezent"}},
      {"Emblematy herbowe i tarcze dla stowarzyszeń", "emblematy",
       "Tarcza 150×170 mm, herb lub godło lokalne, ornament rycinowy.", "cały rok",
       "biblioteka własna WOJAN", "2026-04-01", {"tarcza", "herb", "rycina"}},
      {"Dekoracje halloweenowe z dynią", "halloween",
       "Dynia + napis, zawieszki na drzwi; sezon wrzesień–październik.", "Q4",
       "biblioteka własna WOJAN", "2026-04-01", {"halloween", "dynia", "dekoracja"}},
      {"Minimalistyczne tabliczki z QR do oferty", "logo",
       "QR + krótki napis, idealne dla małych firm i rękodzielników.", "cały rok",
       "biblioteka własna WOJAN", "2026-05-01", {"qr", "firma", "minimal"}},
  };
  return list;
}

Engine::Engine() { fonts_.setDir("fonts"); }

void Engine::setDataDir(const std::string& dir) {
  dataDir_ = dir;
  makeDirs(dir);
  makeDirs(dir + "/projects");
  makeDirs(dir + "/exports");
  makeDirs(dir + "/thumbs");
}
void Engine::setFontDir(const std::string& dir) { fonts_.setDir(dir); }

Json Engine::status() {
  Json j = Json::object();
  j.set("app", "WOJAN LaserCraft AI Studio");
  j.set("engine", "wojan-lasercraft-core");
  j.set("version", "1.0.0");
  j.set("build", 20261029);
  j.set("offline", true);
  j.set("cloud", false);
  j.set("apiKeys", false);
  j.set("fonts", (double)fonts_.ids().size());
  j.set("emblems", (double)emblemIds().size());
  j.set("dataDir", dataDir_);
  Json mods = Json::array();
  mods.push("analiza promptu (PL) — regułowy NLU");
  mods.push("generator kompozycji + 24 emblematy proceduralne + 10 ramek");
  mods.push("generator napisów PL (ponad 120 par napisów, rymy, dedykacje)");
  mods.push("silnik wektorowy (TTF→ścieżki, SVG, DXF, QR)");
  mods.push("silnik rastra (PNG/BMP, dithering, progowanie, wektoryzacja)");
  mods.push("walidator produkcyjny + kerf");
  mods.push("eksport LightBurn/LaserGRBL + G-code GRBL");
  j.set("modules", mods);
  j.set("ai", "lokalny silnik regułowy + modele proceduralne (offline, bez chmury i kluczy API)");
  return j;
}

Json Engine::catalog() {
  Json j = Json::object();
  Json products = Json::array();
  for (auto& p : productCatalog()) {
    Json o = Json::object();
    o.set("id", p.id); o.set("name", p.name); o.set("shape", p.shape);
    o.set("w", p.w); o.set("h", p.h); o.set("cut", p.cut); o.set("curved", p.curved);
    o.set("material", p.defaultMaterial); o.set("margin", p.margin); o.set("note", p.note);
    o.set("category", p.category);
    products.push(o);
  }
  j.set("products", products);
  Json mats = Json::array();
  for (auto& m : materialCatalog()) {
    Json o = Json::object();
    o.set("id", m.id); o.set("name", m.name); o.set("thickness", m.thickness);
    o.set("cuts", m.cuts); o.set("engraves", m.engraves); o.set("safe", m.safe);
    o.set("warning", m.warning); o.set("costPerM2", m.costPerM2);
    mats.push(o);
  }
  j.set("materials", mats);
  Json machines = Json::array();
  for (auto& m : machineCatalog()) {
    Json o = Json::object();
    o.set("id", m.id); o.set("name", m.name); o.set("fieldW", m.fieldW); o.set("fieldH", m.fieldH);
    o.set("kerf", m.kerf); o.set("lpi", m.lpi); o.set("controller", m.controller);
    o.set("kind", m.kind); o.set("note", m.note);
    machines.push(o);
  }
  j.set("machines", machines);
  Json cats = Json::array();
  struct C { const char* id; const char* name; const char* sub; };
  static const C categories[] = {
      {"humor", "Humor", "suchar,absurd,ironia"}, {"smieszne_teksty", "Śmieszne teksty", "krótkie,mocne,do tabliczki"},
      {"satyra_polityczna", "Satyra polityczna", "wybory,obietnice,sejm"}, {"satyra_spoleczna", "Satyra społeczna", "urzędnicy,kolejki,internet"},
      {"dom_i_rodzina", "Dom i rodzina", "kuchnia,salon,ogrod"}, {"warsztat", "Warsztat", "narzędzia,majster,diy"},
      {"garaz", "Garaż", "mechanik,auto,naprawa"}, {"motoryzacja", "Motoryzacja", "auto,moto,trasa"},
      {"wedkarstwo", "Wędkarstwo", "szczupak,spinning,polow"}, {"myslistwo", "Myślistwo", "jelen,las,tradycja"},
      {"rolnictwo", "Rolnictwo", "traktor,pole,gospodarstwo"}, {"zwierzeta", "Zwierzęta", "pies,kot,wilk"},
      {"wilki", "Wilki", "wataha,las,noc"}, {"przyroda", "Przyroda", "gory,drzewa,las"},
      {"patriotyczne", "Patriotyczne", "orzel,flaga,pamiec"}, {"slowianskie", "Słowiańskie", "znaki,tradycja,natura"},
      {"fantastyka", "Fantastyka", "smok,rycerz,magia"}, {"gotyk", "Gotyk", "czaszka,memento,ciemnosc"},
      {"halloween", "Halloween", "dynia,duch,straszny dom"}, {"boze_narodzenie", "Boże Narodzenie", "choinka,swieta,zima"},
      {"wielkanoc", "Wielkanoc", "jajko,alleluja,wiosna"}, {"walentynki", "Walentynki", "serce,milosc,para"},
      {"urodziny", "Urodziny", "sto lat,tort,zyczenia"}, {"imieniny", "Imieniny", "imie,zyczenia"},
      {"sluby", "Śluby", "para,mlodzi,nowa droga"}, {"rocznice", "Rocznice", "razem,lata"},
      {"dzien_matki", "Dzień Matki", "mama,serce,kwiaty"}, {"dzien_ojca", "Dzień Ojca", "tata,narzedzia,wedka"},
      {"dzien_babci", "Dzień Babci", "babcia,rodzina"}, {"dzien_dziadka", "Dzień Dziadka", "dziadek,historie"},
      {"emerytura", "Emerytura", "wolne,odpoczynek"}, {"zawody", "Zawody", "mechanik,stolarz,elektryk"},
      {"hobby", "Hobby", "kolekcje,pasje"}, {"sport", "Sport", "silownia,klub,trening"},
      {"muzyka", "Muzyka", "gitara,zespol,nuty"}, {"gaming", "Gaming", "pad,retro,gra"},
      {"religijne", "Religijne", "wiara,modlitwa"}, {"retro", "Retro", "vintage,stare"}, {"vintage", "Vintage", "patyna,klasyka"},
      {"minimalistyczne", "Minimalistyczne", "prosto,czysto"}, {"komiksowe", "Komiksowe", "komiks,ramka,ekspresja"},
      {"portrety", "Portrety", "twarz,fotograwer"}, {"fotograwer", "Fotograwer", "zdjecie,portret,rastr"},
      {"logo", "Logo", "firma,znak,brand"}, {"emblematy", "Emblematy", "herb,tarcza,godlo"},
      {"dedykacje", "Dedykacje", "napis,imie,okazja"}, {"prezenty", "Prezenty", "podarunek,okazja"},
      {"pamiatki", "Pamiątki", "pamiatka,regionalne"}, {"napisy", "Napisy na zamówienie", "wlasny tekst"},
      {"wlasna", "Własna kategoria", "twoja,nazwa,wlasna"},
  };
  for (auto& c : categories) {
    Json o = Json::object();
    o.set("id", c.id); o.set("name", c.name);
    Json subs = Json::array();
    for (auto& s : split(c.sub, ',')) subs.push(s);
    o.set("tags", subs);
    cats.push(o);
  }
  j.set("categories", cats);
  Json frames = Json::array();
  for (auto& f : frameIds()) frames.push(f);
  j.set("frames", frames);
  Json emblems = Json::array();
  for (auto& e : emblemIds()) emblems.push(e);
  j.set("emblems", emblems);
  Json styles = Json::array();
  for (auto& s : {"retro", "rycina", "vintage", "gotyk", "elegancki", "nowoczesny", "minimal", "komiks", "fantasy"}) styles.push(s);
  j.set("styles", styles);
  Json fonts = Json::array();
  for (auto& f : fonts_.ids()) fonts.push(f);
  j.set("fonts", fonts);
  Json layers = Json::array();
  for (auto& l : allLayers()) {
    Json o = Json::object();
    o.set("id", l); o.set("label", layerLabel(l)); o.set("color", layerColor(l));
    o.set("operation", layerOperation(l));
    layers.push(o);
  }
  j.set("layers", layers);
  Json ideas = Json::array();
  for (auto& i : ideaList()) ideas.push(i);
  j.set("ideas", ideas);
  return j;
}

Design Engine::designFromRequest(const Json& req, bool* ok) {
  const Json& dj = req.has("design") ? req["design"] : req;
  Design d = Design::fromJson(dj);
  if (ok) *ok = !dj.isNull() && dj.isObject();
  return d;
}

Json Engine::generate(const Json& req) {
  Json out = Json::object();
  std::string prompt = req["prompt"].str();
  if (prompt.empty()) {
    out.set("error", "Brak opisu projektu (pole 'prompt').");
    return out;
  }
  PromptSpec spec = analyzePrompt(prompt);
  // nadpisania z formularza UI
  if (req.has("category") && !req["category"].str().empty()) spec.category = req["category"].str();
  if (req.has("product") && !req["product"].str().empty()) {
    const ProductDef* p = findProduct(req["product"].str());
    if (p) {
      spec.productId = p->id;
      spec.shape = p->shape;
      spec.requiresCut = p->cut;
      spec.cutGiven = true;
      if (!spec.dimsGiven) { spec.width = p->w; spec.height = p->h; }
    }
  }
  if (req.has("width")) { spec.width = req["width"].num(spec.width); spec.dimsGiven = true; }
  if (req.has("height")) { spec.height = req["height"].num(spec.height); spec.dimsGiven = true; }
  if (req.has("material") && !req["material"].str().empty()) spec.material = req["material"].str();
  if (req.has("style") && !req["style"].str().empty()) spec.style = req["style"].str();
  if (req.has("detail") && !req["detail"].str().empty()) spec.detail = req["detail"].str();
  if (req.has("machine") && !req["machine"].str().empty()) spec.machineId = req["machine"].str();
  if (req.has("shape") && !req["shape"].str().empty()) spec.shape = req["shape"].str();
  if (req.has("requiresCut")) { spec.requiresCut = req["requiresCut"].boolean(true); spec.cutGiven = true; }
  if (req.has("topText")) spec.topText = req["topText"].str();
  if (req.has("bottomText")) spec.bottomText = req["bottomText"].str();
  if (req.has("font") && !req["font"].str().empty()) spec.fontId = req["font"].str();

  GenerateOptions opt;
  opt.seed = (uint32_t)req["seed"].integer(0);
  if (opt.seed == 0) opt.seed = (uint32_t)(std::hash<std::string>{}(prompt) & 0x7FFFFFFF);
  bool variantsRequested = req["variants"].integer(1) > 1;
  int variantCount = std::clamp(req["variants"].integer(1), 1, 4);
  if (req.has("variantStyles") && req["variantStyles"].isArray()) {
    std::vector<std::string> styles;
    for (auto& s : req["variantStyles"].items()) styles.push_back(s.str());
    if (!styles.empty()) {
      auto results = generateVariants(spec, fonts_, assets_, opt, styles);
      Json arr = Json::array();
      for (auto& r : results) {
        Json o = Json::object();
        o.set("ok", r.ok);
        if (!r.ok) { o.set("error", r.error); arr.push(o); continue; }
        Json dd = r.design.toJson();
        o.set("design", dd);
        Json texts = Json::array();
        for (auto& t : r.texts) {
          Json tj = Json::object();
          tj.set("top", t.top); tj.set("bottom", t.bottom); tj.set("dedication", t.dedication);
          tj.set("tone", t.tone); tj.set("source", t.source);
          texts.push(tj);
        }
        o.set("texts", texts);
        o.set("metrics", r.metrics);
        Json lg = Json::array();
        for (auto& s : r.log) lg.push(s);
        o.set("log", lg);
        o.set("style", styles[&r - &results[0]]);
        arr.push(o);
      }
      out.set("variants", arr);
      out.set("promptSpec", specToJson(spec));
      return out;
    }
  }
  GenerateResult r = generateDesign(spec, fonts_, assets_, opt);
  if (!r.ok) {
    out.set("error", r.error);
    return out;
  }
  current_ = r.design;
  assets_.bind(current_);
  out.set("ok", true);
  out.set("design", current_.toJson());
  Json texts = Json::array();
  for (auto& t : r.texts) {
    Json tj = Json::object();
    tj.set("top", t.top); tj.set("bottom", t.bottom); tj.set("dedication", t.dedication);
    tj.set("tone", t.tone); tj.set("source", t.source);
    texts.push(tj);
  }
  out.set("texts", texts);
  out.set("metrics", r.metrics);
  out.set("promptSpec", specToJson(spec));
  Json lg = Json::array();
  for (auto& s : r.log) lg.push(s);
  out.set("log", lg);
  (void)variantCount;
  (void)variantsRequested;
  return out;
}

Json Engine::edit(const Json& req) {
  Json out = Json::object();
  Design d = designFromRequest(req);
  assets_.bind(d);
  std::string command = req["command"].str();
  EditResult r = applyEditText(d, command, fonts_, assets_);
  out.set("ok", r.ok);
  out.set("message", r.message);
  Json ap = Json::array();
  for (auto& a : r.applied) ap.push(a);
  out.set("applied", ap);
  Json fl = Json::array();
  for (auto& f : r.failed) fl.push(f);
  out.set("failed", fl);
  out.set("design", d.toJson());
  out.set("metrics", computeMetrics(d, fonts_, assets_));
  current_ = d;
  return out;
}

Json Engine::validateJson(const Json& req) {
  Design d = designFromRequest(req);
  assets_.bind(d);
  ValidationReport rep = validateDesign(d, fonts_, assets_);
  Json out = Json::object();
  out.set("ok", rep.ok());
  out.set("errors", rep.errors);
  out.set("warnings", rep.warnings);
  out.set("infos", rep.infos);
  Json arr = Json::array();
  rep.issues.clear();
  rep = validateDesign(d, fonts_, assets_);
  for (auto& i : rep.issues) {
    Json o = Json::object();
    o.set("severity", i.severity == Severity::Error ? "error" : (i.severity == Severity::Warning ? "warning" : "info"));
    o.set("code", i.code);
    o.set("message", i.message);
    o.set("object", i.objectId);
    o.set("fix", i.fixHint);
    arr.push(o);
  }
  out.set("issues", arr);
  out.set("text", rep.toText());
  return out;
}

Json Engine::renderJson(const Json& req) {
  Design d = designFromRequest(req);
  assets_.bind(d);
  double pxPerMm = req["pxPerMm"].num(6.0);
  bool guides = req["guides"].boolean(false);
  bool raster = req["raster"].boolean(true);
  Image img = renderDesign(d, fonts_, assets_, pxPerMm, guides, raster);
  Json out = Json::object();
  out.set("width", img.w);
  out.set("height", img.h);
  auto png = pngEncode(img);
  out.set("pngBase64", base64Encode(png));
  out.set("bytes", (double)png.size());
  return out;
}

Json Engine::calculateCosts(const Json& req) {
  CostInput in;
  in.materialCostPerM2 = req["materialCostPerM2"].num(in.materialCostPerM2);
  in.areaMm2 = req["areaMm2"].num(in.areaMm2);
  in.engraveAreaMm2 = req["engraveAreaMm2"].num(0);
  in.cutLengthMm = req["cutLengthMm"].num(0);
  in.machinePowerW = req["machinePowerW"].num(in.machinePowerW);
  in.energyPriceKwh = req["energyPriceKwh"].num(in.energyPriceKwh);
  in.hourlyRate = req["hourlyRate"].num(in.hourlyRate);
  in.engraveSpeedMmS = req["engraveSpeedMmS"].num(in.engraveSpeedMmS);
  in.cutSpeedMmS = req["cutSpeedMmS"].num(in.cutSpeedMmS);
  in.setupMinutes = req["setupMinutes"].num(in.setupMinutes);
  in.passes = std::max(1, req["passes"].integer(1));
  in.packaging = req["packaging"].num(in.packaging);
  in.shipping = req["shipping"].num(in.shipping);
  in.marginPercent = req["marginPercent"].num(in.marginPercent);
  in.wastePercent = req["wastePercent"].num(in.wastePercent);
  CostResult r = calculateCost(in);
  Json o = Json::object();
  o.set("materialCost", r.materialCost);
  o.set("engraveMinutes", r.engraveMinutes);
  o.set("cutMinutes", r.cutMinutes);
  o.set("machineMinutes", r.machineMinutes);
  o.set("energyCost", r.energyCost);
  o.set("laborCost", r.laborCost);
  o.set("wearCost", r.wearCost);
  o.set("totalCost", r.totalCost);
  o.set("price", r.price);
  o.set("profit", r.profit);
  o.set("vatPrice", r.vatPrice);
  o.set("estimateOnly", r.estimateOnly);
  o.set("note", r.note);
  return o;
}

Json specToJson(const PromptSpec& s) {
  Json o = Json::object();
  o.set("category", s.category);
  o.set("style", s.style);
  o.set("detail", s.detail);
  o.set("product", s.productId);
  o.set("shape", s.shape);
  o.set("material", s.material);
  o.set("machine", s.machineId);
  o.set("width", s.width);
  o.set("height", s.height);
  o.set("dimsGiven", s.dimsGiven);
  o.set("requiresCut", s.requiresCut);
  o.set("subject", s.primarySubject);
  o.set("secondary", s.secondarySubject);
  o.set("topText", s.topText);
  o.set("bottomText", s.bottomText);
  o.set("tone", s.tone);
  o.set("occasion", s.occasion);
  o.set("font", s.fontId);
  o.set("frame", s.frameStyle);
  Json kw = Json::array();
  for (auto& k : s.keywords) kw.push(k);
  o.set("keywords", kw);
  return o;
}

Json Engine::handle(const std::string& action, const Json& req) {
  Json out = Json::object();
  try {
    if (action == "ping" || action == "status" || action == "ai_status") return status();
    if (action == "catalog") return catalog();
    if (action == "library") {
      Json lib = libraryJson();
      lib.set("ok", true);
      return lib;
    }
    if (action == "safety") {
      out.set("notes", safetyJson());
      out.set("ok", true);
      return out;
    }
    if (action == "analyze") {
      PromptSpec s = analyzePrompt(req["prompt"].str());
      out.set("spec", specToJson(s));
      out.set("ok", true);
      return out;
    }
    if (action == "generate" || action == "variants") return generate(req);
    if (action == "edit") return edit(req);
    if (action == "validate") return validateJson(req);
    if (action == "fix") {
      Design d = designFromRequest(req);
      assets_.bind(d);
      auto fixes = applySafeFixes(d, fonts_, assets_);
      out.set("design", d.toJson());
      Json arr = Json::array();
      for (auto& f : fixes) arr.push(f);
      out.set("applied", arr);
      out.set("ok", true);
      out.set("validation", validateJson(req.has("design") ? req : Json::object()));
      return out;
    }
    if (action == "render" || action == "preview") return renderJson(req);
    if (action == "texts") {
      TextSpec ts;
      ts.category = req["category"].str("humor");
      ts.occasion = req["occasion"].str();
      ts.recipient = req["recipient"].str();
      ts.tone = req["tone"].str("zartobliwy");
      ts.length = req["length"].str("sredni");
      ts.profanity = req["profanity"].str("brak");
      ts.exactText = req["exactText"].str();
      ts.dedication = req["dedication"].str();
      auto list = generateTexts(ts, std::clamp(req["count"].integer(4), 1, 8),
                                (uint32_t)req["seed"].integer(0));
      Json arr = Json::array();
      for (auto& t : list) {
        Json o = Json::object();
        o.set("top", t.top); o.set("bottom", t.bottom);
        o.set("dedication", t.dedication); o.set("tone", t.tone); o.set("source", t.source);
        arr.push(o);
      }
      out.set("texts", arr);
      out.set("ok", true);
      return out;
    }
    if (action == "adjust_text") {
      out.set("text", adjustText(req["text"].str(), req["action"].str()));
      out.set("ok", true);
      return out;
    }
    if (action == "check_spelling") {
      auto issues = checkSpelling(req["text"].str());
      Json arr = Json::array();
      for (auto& i : issues) {
        Json o = Json::object();
        o.set("word", i.word); o.set("suggestion", i.suggestion); o.set("reason", i.reason);
        arr.push(o);
      }
      out.set("issues", arr);
      out.set("fixed", fixPolishSpelling(req["text"].str()));
      return out;
    }
    if (action == "ideas") {
      out.set("idea", randomIdea((uint32_t)req["seed"].integer(0)));
      Json all = Json::array();
      for (auto& i : ideaList()) all.push(i);
      out.set("all", all);
      return out;
    }
    if (action == "trends") {
      Json arr = Json::array();
      for (auto& t : offlineTrends()) {
        Json o = Json::object();
        o.set("title", t.title); o.set("category", t.category); o.set("idea", t.idea);
        o.set("season", t.season); o.set("source", t.source); o.set("date", t.date);
        Json tags = Json::array();
        for (auto& g : t.tags) tags.push(g);
        o.set("tags", tags);
        o.set("mode", "offline");
        arr.push(o);
      }
      out.set("trends", arr);
      out.set("note", "Tryb offline: biblioteka własna WOJAN z datami. Tryb online (świadomie uruchamiany) "
                      "wymaga źródła z sieci — aplikacja nie łączy się z internetem automatycznie.");
      return out;
    }
    if (action == "export_svg") {
      Design d = designFromRequest(req);
      assets_.bind(d);
      std::string layer = req["layer"].str();
      out.set("svg", layer.empty() ? designToSvg(d, fonts_, assets_, req["guides"].boolean(false))
                                   : designToSvgLayer(d, fonts_, assets_, layer));
      out.set("ok", true);
      out.set("filename", slugify(d.name) + (layer.empty() ? ".svg" : "_" + slugify(layer) + ".svg"));
      return out;
    }
    if (action == "export_dxf") {
      Design d = designFromRequest(req);
      assets_.bind(d);
      out.set("dxf", designToDxf(d, fonts_, assets_, req["layer"].str()));
      out.set("ok", true);
      out.set("filename", slugify(d.name) + ".dxf");
      out.set("note", "DXF zawiera wyłącznie geometrię wektorową (bez rastra). Jednostki: mm.");
      return out;
    }
    if (action == "export_gcode") {
      Design d = designFromRequest(req);
      assets_.bind(d);
      GcodeOptions o;
      o.includeCut = req["includeCut"].boolean(true);
      o.includeEngraveVector = req["includeEngrave"].boolean(true);
      o.power = req["power"].num(o.power);
      o.feed = req["feed"].num(o.feed);
      o.passes = std::max(1, req["passes"].integer(1));
      std::string g;
      ExportResult r = designToGcode(d, fonts_, assets_, o, g);
      out.set("ok", r.ok);
      out.set("error", r.error);
      out.set("gcode", g);
      Json w = Json::array();
      for (auto& x : r.warnings) w.push(x);
      out.set("warnings", w);
      return out;
    }
    if (action == "export_package") {
      Design d = designFromRequest(req);
      assets_.bind(d);
      std::string root = req["dir"].str(dataDir_ + "/exports");
      PackageResult pr = writeProductionPackage(d, fonts_, assets_, root, req["previewPng"].str());
      out.set("ok", pr.ok);
      out.set("error", pr.error);
      out.set("dir", pr.dir);
      Json files = Json::array();
      for (auto& f : pr.files) files.push(f);
      out.set("files", files);
      // dodatkowo ZIP z paczką
      if (pr.ok) {
        ZipWriter zw;
        for (auto& f : pr.files) {
          std::vector<uint8_t> bytes;
          if (readFileBytes(pr.dir + "/" + f, bytes)) zw.add(f, bytes);
        }
        auto zip = zw.finish();
        out.set("zipBase64", base64Encode(zip));
        out.set("zipName", slugify(d.name) + "-paczka.zip");
      }
      return out;
    }
    if (action == "export_zip") {  // projekt + paczka
      Design d = designFromRequest(req);
      assets_.bind(d);
      Json proj = d.toJson();
      ZipWriter zw;
      zw.add("projekt.wlcproj", proj.dump(1));
      zw.add("lightburn.svg", designToSvg(d, fonts_, assets_, false, true));
      zw.add("design.dxf", designToDxf(d, fonts_, assets_, ""));
      zw.add("cut_outer.svg", designToSvgLayer(d, fonts_, assets_, LAYER_CUT_OUTER));
      zw.add("engrave_vector.svg", designToSvgLayer(d, fonts_, assets_, LAYER_ENGRAVE_VECTOR));
      zw.add("raport_walidacji.txt", exportReportText(d, fonts_, assets_));
      out.set("zipBase64", base64Encode(zw.finish()));
      out.set("zipName", slugify(d.name) + ".zip");
      out.set("ok", true);
      return out;
    }
    if (action == "save_project") {
      Design d = designFromRequest(req);
      std::string path = req["path"].str();
      if (path.empty()) path = dataDir_ + "/projects/" + slugify(d.name) + ".wlcproj";
      d.modified = nowIso();
      if (d.id.empty()) d.id = "proj-" + std::to_string((long long)std::hash<std::string>{}(d.name + d.modified) % 1000000);
      if (!writeFile(path, d.toJson().dump(1))) {
        out.set("error", "Nie można zapisać pliku: " + path);
        return out;
      }
      out.set("ok", true);
      out.set("path", path);
      out.set("bytes", (double)readFile(path).size());
      return out;
    }
    if (action == "load_project") {
      std::string path = req["path"].str();
      // obsługa archiwum ZIP z projektem
      if (path.size() > 4 && (path.substr(path.size() - 4) == ".zip")) {
        ZipReader zr;
        std::string err;
        if (!zr.openFile(path, &err)) { out.set("error", err); return out; }
        for (auto& n : zr.names()) {
          if (n.size() > 8 && n.substr(n.size() - 8) == ".wlcproj") {
            std::string txt;
            if (zr.readText(n, txt)) {
              Design d = Design::fromJson(Json::parse(txt));
              out.set("ok", true);
              out.set("design", d.toJson());
              return out;
            }
          }
        }
        out.set("error", "Archiwum nie zawiera projektu .wlcproj");
        return out;
      }
      std::string txt = readFile(path);
      if (txt.empty()) { out.set("error", "Nie można odczytać pliku: " + path); return out; }
      std::string err;
      Json j = Json::parse(txt, &err);
      if (!err.empty()) { out.set("error", "Błąd JSON: " + err); return out; }
      Design d = Design::fromJson(j);
      out.set("ok", true);
      out.set("design", d.toJson());
      return out;
    }
    if (action == "projects_list") {
      std::string dir = req["dir"].str(dataDir_ + "/projects");
      Json arr = Json::array();
      std::error_code ec;
      for (auto& e : std::filesystem::directory_iterator(dir, ec)) {
        if (!e.is_regular_file()) continue;
        std::string p = e.path().string();
        if (p.size() < 8 || p.substr(p.size() - 8) != ".wlcproj") continue;
        std::string txt = readFile(p);
        Json proj = Json::parse(txt);
        Json o = Json::object();
        o.set("path", p);
        o.set("name", proj["name"].str(e.path().stem().string()));
        o.set("modified", proj["modified"].str(""));
        o.set("category", proj["category"].str(""));
        o.set("favorite", proj["extra"]["favorite"].boolean(false));
        const Json& prod = proj["product"];
        o.set("product", prod["name"].str(""));
        o.set("size", sfmt("%.0f×%.0f mm", proj["product"]["w"].num(0), proj["product"]["h"].num(0)));
        o.set("bytes", (double)txt.size());
        arr.push(o);
      }
      out.set("projects", arr);
      out.set("dir", dir);
      return out;
    }
    if (action == "duplicate_project") {
      std::string path = req["path"].str();
      std::string txt = readFile(path);
      if (txt.empty()) { out.set("error", "Brak pliku"); return out; }
      Json j = Json::parse(txt);
      Design d = Design::fromJson(j);
      d.name = d.name + " (kopia)";
      d.modified = nowIso();
      std::string np = dataDir_ + "/projects/" + slugify(d.name) + ".wlcproj";
      writeFile(np, d.toJson().dump(1));
      out.set("ok", true);
      out.set("path", np);
      return out;
    }
    if (action == "delete_project") {
      std::string path = req["path"].str();
      std::error_code ec;
      bool ok = std::filesystem::remove(path, ec);
      out.set("ok", ok);
      if (!ok) out.set("error", "Nie można usunąć pliku");
      return out;
    }
    if (action == "favorite_project") {
      std::string path = req["path"].str();
      std::string txt = readFile(path);
      Json j = Json::parse(txt);
      j["extra"]["favorite"] = Json(req["favorite"].boolean(true));
      writeFile(path, j.dump(1));
      out.set("ok", true);
      return out;
    }
    if (action == "qr") {
      std::string data = req["data"].str();
      double size = req["size"].num(30);
      QrCode qr = qrEncode(data, req["ecc"].str("M").empty() ? 'M' : req["ecc"].str("M")[0]);
      out.set("ok", qr.ok);
      std::string chk = qrSelfCheck(qr);
      out.set("selfCheck", chk);
      out.set("check", chk);
      out.set("version", (double)qr.version);
      out.set("modules", (double)qr.size);
      out.set("mask", (double)qr.mask);
      out.set("svgPath", qrPath(data, size).toSVG());
      out.set("error", qr.error);
      // test odczytu zwrotnego (wewnętrzny) — dowód spójności macierzy
      std::string decoded;
      bool rt = qrDecode(qr, decoded);
      out.set("roundTrip", rt && decoded == data);
      if (qr.ok) {
        // rysunek QR do podglądu w UI (8 px na moduł + cicha strefa)
        int quiet = 4, px = 8;
        int total = qr.size + quiet * 2;
        Image img(total * px, total * px, 1, 255);
        for (int y = 0; y < qr.size; y++)
          for (int xx = 0; xx < qr.size; xx++)
            if (qr.at(xx, y))
              for (int dy = 0; dy < px; dy++)
                for (int dx = 0; dx < px; dx++) {
                  uint8_t* p = img.at((xx + quiet) * px + dx, (y + quiet) * px + dy);
                  p[0] = 0;
                }
        out.set("pngBase64", base64Encode(pngEncode(img)));
        out.set("width", img.w);
        out.set("height", img.h);
      }
      return out;
    }
    if (action == "image_info") {
      auto bytes = base64Decode(req["pngBase64"].str());
      Image img;
      bool ok = pngDecode(bytes, img) || bmpDecode(bytes, img);
      out.set("ok", ok);
      if (ok) {
        out.set("width", img.w);
        out.set("height", img.h);
        out.set("channels", img.ch);
        out.set("background", (double)imgEstimateBackground(img));
      }
      return out;
    }
    if (action == "image_edit") {
      // pipeline: grayscale, brightness, contrast, gamma, blur, sharpen, median, sobel,
      // threshold, dither, removeBg, resize, crop
      auto bytes = base64Decode(req["pngBase64"].str());
      Image img;
      if (!(pngDecode(bytes, img) || bmpDecode(bytes, img))) {
        out.set("error", "Nie można zdekodować obrazu (obsługiwane: PNG, BMP; JPG konwertuje warstwa platformy).");
        return out;
      }
      for (auto& op : req["ops"].items()) {
        std::string o = op["op"].str();
        if (o == "gray") img = imgGrayscale(img);
        else if (o == "brightness") img = imgBrightnessContrast(img, op["value"].num(0), 0);
        else if (o == "contrast") img = imgBrightnessContrast(img, 0, op["value"].num(0));
        else if (o == "gamma") img = imgGamma(img, op["value"].num(1));
        else if (o == "blur") img = imgBlur(img, op["radius"].integer(1));
        else if (o == "sharpen") img = imgSharpen(img, op["amount"].num(1));
        else if (o == "median") img = imgMedian(img);
        else if (o == "sobel") img = imgSobel(img, op["threshold"].integer(80));
        else if (o == "threshold") img = imgThreshold(img, op["value"].integer(128));
        else if (o == "dither") img = imgDither(img, 128, op["algo"].str("floyd"));
        else if (o == "removeBg") img = imgRemoveBackground(img, op["tolerance"].integer(32));
        else if (o == "invert") img = imgInvert(img);
        else if (o == "resize") img = imgResize(img, std::max(1, op["w"].integer(img.w)), std::max(1, op["h"].integer(img.h)));
        else if (o == "crop") img = imgCrop(img, op["x"].integer(0), op["y"].integer(0), op["w"].integer(img.w), op["h"].integer(img.h));
        else if (o == "fit") img = imgFitBox(img, op["w"].integer(img.w), op["h"].integer(img.h),
                                             op["keepAspect"].boolean(true), true);
      }
      auto png = pngEncode(img);
      out.set("pngBase64", base64Encode(png));
      out.set("width", img.w);
      out.set("height", img.h);
      out.set("ok", true);
      return out;
    }
    if (action == "import_image") {
      auto bytes = base64Decode(req["pngBase64"].str());
      Image img;
      if (!(pngDecode(bytes, img) || bmpDecode(bytes, img))) {
        out.set("error", "Nie można zdekodować obrazu.");
        return out;
      }
      // przygotowanie do grawerowania
      if (req["grayscale"].boolean(true)) img = imgGrayscale(img);
      if (req["removeBg"].boolean(false)) img = imgRemoveBackground(img, req["bgTolerance"].integer(32));
      Design d = designFromRequest(req);
      assets_.bind(d);
      std::string id = assets_.putPng(img, req["name"].str("obraz"));
      double targetW = req["widthMm"].num(std::min(d.product.w * 0.4, 60.0));
      double targetH = targetW * img.h / std::max(1, img.w);
      Obj o;
      o.id = d.nextId("img");
      o.type = "image";
      o.layer = LAYER_ENGRAVE_RASTER;
      o.role = "raster";
      o.name = req["name"].str("Obraz");
      o.asset = id;
      o.imgW = targetW;
      o.imgH = targetH;
      o.x = (d.product.w - targetW) / 2;
      o.y = (d.product.h - targetH) / 2;
      o.dither = req["dither"].str("floyd");
      o.threshold = req["threshold"].integer(128);
      if (d.product.w > 0) d.objects.push_back(o);
      out.set("ok", true);
      out.set("assetId", id);
      out.set("design", d.toJson());
      out.set("pngBase64", base64Encode(pngEncode(img)));
      return out;
    }
    if (action == "calculator") return calculateCosts(req);
    if (action == "report") {
      Design d = designFromRequest(req);
      assets_.bind(d);
      out.set("text", exportReportText(d, fonts_, assets_));
      out.set("ok", true);
      return out;
    }
    if (action == "package_manifest") {
      out.set("note", "Lista plików paczki produkcyjnej");
      Json arr = Json::array();
      for (auto& f : {"projekt_edytowalny.wlcproj", "lightburn.svg", "lasergrbl.png", "cut_outer.svg",
                      "engrave_vector.svg", "engrave_raster.png", "preview.png", "parametry.json",
                      "raport_walidacji.txt", "design.dxf"})
        arr.push(f);
      out.set("files", arr);
      return out;
    }
    out.set("error", "Nieznana akcja: " + action);
    return out;
  } catch (const std::exception& e) {
    out = Json::object();
    out.set("error", std::string("Wyjątek w silniku: ") + e.what());
    return out;
  }
}

}  // namespace lc
