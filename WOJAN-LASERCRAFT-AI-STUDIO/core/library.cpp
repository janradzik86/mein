#include "library.h"
#include "projector.h"

namespace lc {

namespace {
CategoryDef C(const char* id, const char* name, const char* emblem, const char* blurb,
              std::vector<std::string> tags, const char* product, const char* style) {
  CategoryDef c;
  c.id = id; c.name = name; c.emblem = emblem; c.blurb = blurb;
  c.tags = std::move(tags); c.suggestedProduct = product; c.suggestedStyle = style;
  return c;
}
}  // namespace

const std::vector<CategoryDef>& categoryCatalog() {
  static const std::vector<CategoryDef> list = {
      C("wlasne", "Własny pomysł", "kolo", "Dowolny motyw — opisz go własnymi słowami.", {"wlasny", "custom"}, "tabliczka", "nowoczesny"),
      C("wedkarstwo", "Wędkarstwo", "szczupak", "Szczupaki, karasie, wobler — dla pasjonatów wędki.", {"ryby", "wedka", "lowisko"}, "tabliczka", "rycina"),
      C("myslistwo", "Myślistwo", "jelen", "Jelenie, wilki, sygnały łowieckie, koła łowieckie.", {"las", "lowy", "jelen"}, "tabliczka", "rycina"),
      C("wilk", "Wilki", "wilk", "Wilcza wataha, wilcze oczy, cytaty o sile.", {"wilk", "wataha"}, "tabliczka", "gotyk"),
      C("konie", "Konie", "kon", "Konie, podkowy, jeździectwo.", {"kon", "jezdziectwo", "podkowa"}, "tabliczka", "rycina"),
      C("psy", "Psy", "lapa", "Rasy, łapy, obroże, tabliczki na budę.", {"pies", "lapa", "rasa"}, "tabliczka", "retro"),
      C("koty", "Koty", "lapa", "Koty, kociaki, kocie cytaty.", {"kot", "kociak"}, "tabliczka", "komiks"),
      C("zwierzeta", "Zwierzęta", "ptak", "Ptaki, lisy, jeże i inne zwierzęta.", {"zwierze", "ptak", "las"}, "tabliczka", "rycina"),
      C("rolnictwo", "Rolnictwo", "tractor", "Traktory, gospodarstwa, plony.", {"traktor", "gospodarstwo", "rolnik"}, "tabliczka", "retro"),
      C("motoryzacja", "Motoryzacja", "samochod", "Auta, motocykle, garaże, warsztaty.", {"auto", "garaz", "warsztat"}, "tabliczka", "retro"),
      C("muzyka", "Muzyka", "gitara", "Gitary, nuty, zespoły, festiwale.", {"gitara", "nuty", "zespol"}, "tabliczka", "retro"),
      C("gaming", "Gaming", "pad", "Pady, retro konsole, nicki graczy.", {"gra", "konsola", "gracz"}, "tabliczka", "komiks"),
      C("sport", "Sport", "kolo_zebate", "Siłownia, bieganie, kluby, medale.", {"trening", "medal", "klub"}, "medal", "nowoczesny"),
      C("rower", "Rower", "kolo_zebate", "Rowery, trasy, szosy, MTB.", {"rower", "trasa"}, "zakladka", "nowoczesny"),
      C("wedrowki", "Wędrówki", "gory", "Góry, szczyty, schroniska, korony gór.", {"gory", "szczyt", "szlak"}, "tabliczka", "rycina"),
      C("przyroda", "Przyroda", "drzewo", "Lasy, drzewa, liście, natura.", {"las", "drzewo", "liscie"}, "tabliczka", "rycina"),
      C("morze", "Morze", "kotwica", "Kotwice, fale, żagle, porty.", {"morze", "zagiel", "kotwica"}, "tabliczka", "retro"),
      C("wojsko", "Wojsko", "orzel", "Orły, godła, wojskowe motto.", {"wojsko", "orzel", "godlo"}, "tabliczka", "rycina"),
      C("patriotyczne", "Patriotyczne", "orzel", "Orzeł, flaga, rocznice narodowe.", {"polska", "flaga", "orzel"}, "tabliczka", "rycina"),
      C("slask", "Śląsk", "kolo_zebate", "Godło śląskie, hałdy, gwara.", {"slask", "godlo"}, "tabliczka", "rycina"),
      C("kaszuby", "Kaszuby", "kotwica", "Kaszubskie wzory, haft, gryf.", {"kaszuby", "haft"}, "tabliczka", "rycina"),
      C("goryale", "Górale", "gory", "Góralskie ornamenty, oscypki, muzyka.", {"gorale", "zakopane"}, "deska", "rycina"),
      C("slowianskie", "Słowiańskie", "kolo_zebate", "Swarga, ręce boga, słowiańskie symbole.", {"slowianie", "swarga", "symbol"}, "tabliczka", "slowianskie"),
      C("religijne", "Religijne", "serce", "Krzyże, anioły, cytaty biblijne.", {"krzyz", "aniol", "wiara"}, "tabliczka", "rycina"),
      C("pierwsza_komunia", "Pierwsza Komunia", "serce", "Pamiątki komunijne, imiona, daty.", {"komunia", "pamiatka"}, "tabliczka", "elegancki"),
      C("chrzest", "Chrzest", "serce", "Pamiątki chrztu, aniołki, imiona.", {"chrzest", "aniolek"}, "tabliczka", "elegancki"),
      C("slub", "Ślub", "serce", "Zaproszenia, pamiątki, monogramy.", {"slub", "monogram"}, "tabliczka", "elegancki"),
      C("walentynki", "Walentynki", "serce", "Serca, wyznania, pary.", {"walentynki", "milosc", "serce"}, "magnes", "elegancki"),
      C("dzien_matki", "Dzień Matki", "roza", "Róże, podziękowania, dedykacje.", {"mama", "roza", "podziekowanie"}, "tabliczka", "elegancki"),
      C("dzien_ojca", "Dzień Ojca", "kubek", "Kubki, narzędzia, wędka, wspomnienia.", {"tata", "kubek"}, "kubek", "retro"),
      C("urodziny", "Urodziny", "serce", "Życzenia, imiona, wiek, balony.", {"urodziny", "zyczenia"}, "tabliczka", "komiks"),
      C("rocznice", "Rocznice", "serce", "Rocznice ślubu, jubileusze, daty.", {"rocznica", "jubileusz"}, "tabliczka", "elegancki"),
      C("boze_narodzenie", "Boże Narodzenie", "drzewo", "Choinki, śnieżynki, kolędy, prezenty.", {"swieta", "choinka", "sniezynka"}, "ozdoba", "rycina"),
      C("wielkanoc", "Wielkanoc", "serce", "Pisanki, zajączki, koszyczki.", {"wielkanoc", "pisanka", "zajac"}, "ozdoba", "retro"),
      C("halloween", "Halloween", "dynia", "Dynie, duchy, czaszki, nietoperze.", {"halloween", "dynia", "duch"}, "ozdoba", "gotyk"),
      C("gotyk", "Gotyk", "czaszka", "Czaszki, memento mori, gotyckie litery.", {"czaszka", "memento", "gotyk"}, "tabliczka", "gotyk"),
      C("fantastyka", "Fantastyka", "smok", "Smoki, rycerze, magia, herby.", {"smok", "rycerz", "magia"}, "tabliczka", "fantasy"),
      C("sredniowiecze", "Średniowiecze", "czaszka", "Herby, tarcze, miecze, zamki.", {"herb", "tarcza", "zamek"}, "tabliczka", "rycina"),
      C("retro", "Retro i vintage", "kubek", "Lata 50., neony, stare reklamy.", {"retro", "vintage", "neon"}, "tabliczka", "retro"),
      C("kuchnia", "Kuchnia", "kubek", "Deski, przepisy, miarki, kawy.", {"deska", "przepis", "kawa"}, "deska", "retro"),
      C("ogrod", "Ogród", "drzewo", "Tabliczki ziół, warzywnik, pszczoły.", {"ogrod", "zioła", "pszczola"}, "tabliczka", "retro"),
      C("dom_rodzina", "Dom i rodzina", "serce", "Domowe zasady, imiona, drzewo genealogiczne.", {"dom", "rodzina"}, "tabliczka", "elegancki"),
      C("firma", "Firma i logo", "kolo_zebate", "Logo, wizytówki, oznakowanie.", {"logo", "firma", "wizytowka"}, "tabliczka", "minimal"),
      C("restauracja", "Gastronomia", "kubek", "Menu, tabliczki na stoliki, kawy.", {"menu", "kawiarnia"}, "tabliczka", "retro"),
      C("fotograwer", "Fotograwer", "ptak", "Zdjęcia, portrety, fotograwer na drewnie.", {"zdjecie", "portret", "foto"}, "tabliczka", "minimal"),
      C("bizuteria", "Biżuteria", "serce", "Zawieszki, medaliony, grawer na metalu.", {"medalion", "zawieszka"}, "medalion", "elegancki"),
      C("dzieci", "Dla dzieci", "ptak", "Imiona, misie, pokoje dziecięce.", {"dziecko", "imie"}, "tabliczka", "komiks"),
      C("szkola", "Szkoła", "kolo_zebate", "Nagrody, dyplomy, tabliczki klasowe.", {"szkola", "dyplom"}, "tabliczka", "elegancki"),
      C("narzedzia", "Narzędzia i warsztat", "kolo_zebate", "Tabliczki do skrzynek, organizerów.", {"warsztat", "narzedzia"}, "tabliczka", "minimal"),
      C("qr_kontakt", "QR i kontakt", "kolo_zebate", "Wizytówki QR, linki, Wi-Fi.", {"qr", "link", "wifi"}, "zakladka", "minimal"),
  };
  return list;
}

const CategoryDef* findCategory(const std::string& id) {
  for (auto& c : categoryCatalog()) if (c.id == id) return &c;
  return nullptr;
}

const std::vector<TemplateDef>& templateCatalog() {
  static const std::vector<TemplateDef> list = {
      {"tabliczka_wedkarska", "Tabliczka wędkarska", "Tabliczka z grawerem: szczupak, napis KRÓL ŁOWISKA, na dole RYBY SIĘ MNIE BOJĄ, 200×150 mm, styl rycina", "wedkarstwo", "tabliczka"},
      {"piersiowka_wilk", "Piersiówka z wilkiem", "Piersiówka z grawerem: wilk, napis WILKI NIE ŚPIĄ, tylko grawer bez cięcia, 95×115 mm", "wilk", "piersiowka"},
      {"brelok_imie", "Brelok z imieniem", "Brelok z imieniem KASIA, serce, 40×40 mm, sklejka 3 mm, z otworem na kółko", "urodziny", "brelok"},
      {"medal_sport", "Medal sportowy", "Medal biegowy, napis BIEG PO MARZENIA, na dole 2026, 60×60 mm, akryl", "sport", "medal"},
      {"deska_kuchnia", "Deska kuchenna", "Deska kuchenna z napisem KUCHNIA BABCI, motyw ziół, 300×200 mm, grawer", "kuchnia", "deska"},
      {"zakladka_motyw", "Zakładka z cytatem", "Zakładka do książki z cytatem CZYTAM, WIĘC JESTEM, motyw lasu, 50×150 mm", "przyroda", "zakladka"},
      {"ozdoba_swieta", "Ozdoba świąteczna", "Ozdoba choinkowa: śnieżynka, napis WESOŁYCH ŚWIĄT, 80×90 mm, sklejka 3 mm", "boze_narodzenie", "ozdoba"},
      {"magnes_humor", "Magnes z humorem", "Magnes na lodówkę, napis KTO TU RZĄDZI? JA, 60×45 mm, komiks", "urodziny", "magnes"},
      {"tabliczka_qr", "Tabliczka z QR", "Tabliczka z kodem QR do linku https://wojan.pl i napis ZESKANUJ MNIE, 100×100 mm", "qr_kontakt", "tabliczka"},
      {"medalion_rocznica", "Medalion rocznicowy", "Medalion 40×40 mm, napis 10 LAT RAZEM, serce, stal, tylko grawer", "rocznice", "medalion"},
      {"kubek_dzien_ojca", "Kubek dla taty", "Kubek z grawerem: wędka, napis NAJLEPSZY TATA, tylko grawer, 210×90 mm", "dzien_ojca", "kubek"},
      {"dyplom_szkola", "Dyplom szkolny", "Dyplom dla ucznia, napis MISTRZ MATEMATYKI, ramka elegancka, 200×150 mm", "szkola", "tabliczka"},
  };
  return list;
}

const std::vector<SafetyNote>& safetyNotes() {
  static const std::vector<SafetyNote> list = {
      {"pvc", "PVC / PCW", "niebezpieczny",
       "NIGDY nie tnij ani nie graweruj PVC/PCW — wydziela chlorowodór i dioksyny. Otwórz warsztat, "
       "usuń materiał z pola roboczego."},
      {"abs", "ABS", "niebezpieczny", "ABS przy cięciu wydziela cyjanowodór — grawerować tylko przy mocnej wentylacji."},
      {"polistyren", "Polistyren (PS)", "uwaga", "Topi się i kopci; grawerować z filtracją, nie ciąć."},
      {"poliwęglan", "Poliwęglan (PC)", "uwaga", "Wydziela związki fenolowe — tylko grawer przy wyciągu."},
      {"skora_chromowa", "Skóra chromowana", "niebezpieczny",
       "Sole chromu przy spalaniu są toksyczne — używaj skóry roślinnie barwionej."},
      {"teflon", "PTFE / teflon", "niebezpieczny", "Rozkład PTFE wydziela silnie toksyczne opary — bezwzględny zakaz."},
      {"drewno_lakier", "Drewno lakierowane", "uwaga", "Lakiery i kleje mogą wydzielać toksyczne opary; testuj na małym elemencie."},
      {"md", "Płyta MDF", "uwaga", "Klej formaldehydowy — przy cięciu grubej płyty używaj wyciągu."},
      {"sklejka", "Sklejka brzozowa", "ok", "Bezpieczny materiał do cięcia i grawerowania."},
      {"akryl", "Akryl (PMMA)", "ok", "Bezpieczny; cięty akryl wydziela zapach — zalecana wentylacja."},
      {"stal", "Stal nierdzewna", "ok", "Bezpieczna przy grawerowaniu fiber lub pastą na diodzie; wymaga wyciągu."},
      {"anodowane", "Aluminium anodowane", "ok", "Grawer na anodzie jest bezpieczny; nie przecinaj cienkich, odblaskowych blach diodą."},
      {"ceramika", "Ceramika", "ok", "Bezpieczna; grawer na szkliwie wymaga pasty lub lasera CO₂."},
      {"guma", "Guma / kauczuk", "uwaga", "Dym bogaty w sadzę — mocny wyciąg i krótkie serie."},
  };
  return list;
}

Json libraryJson() {
  Json j = Json::object();
  Json cats = Json::array();
  for (auto& c : categoryCatalog()) {
    Json o = Json::object();
    o.set("id", c.id);
    o.set("name", c.name);
    o.set("emblem", c.emblem);
    o.set("blurb", c.blurb);
    Json tags = Json::array();
    for (auto& t : c.tags) tags.push(t);
    o.set("tags", tags);
    o.set("product", c.suggestedProduct);
    o.set("style", c.suggestedStyle);
    cats.push(o);
  }
  j.set("categories", cats);
  Json tpls = Json::array();
  for (auto& t : templateCatalog()) {
    Json o = Json::object();
    o.set("id", t.id);
    o.set("name", t.name);
    o.set("prompt", t.prompt);
    o.set("category", t.category);
    o.set("product", t.product);
    tpls.push(o);
  }
  j.set("templates", tpls);
  Json prods = Json::array();
  for (auto& p : productCatalog()) {
    Json o = Json::object();
    o.set("id", p.id);
    o.set("name", p.name);
    o.set("category", p.category);
    o.set("shape", p.shape);
    o.set("material", p.defaultMaterial);
    o.set("w", p.w);
    o.set("h", p.h);
    o.set("cornerR", p.cornerR);
    o.set("margin", p.margin);
    o.set("cut", p.cut);
    o.set("curved", p.curved);
    o.set("note", p.note);
    prods.push(o);
  }
  j.set("products", prods);
  Json mats = Json::array();
  for (auto& m : materialCatalog()) {
    Json o = Json::object();
    o.set("id", m.id);
    o.set("name", m.name);
    o.set("thickness", m.thickness);
    o.set("cuts", m.cuts);
    o.set("engraves", m.engraves);
    o.set("safe", m.safe);
    o.set("warning", m.warning);
    o.set("costPerM2", m.costPerM2);
    mats.push(o);
  }
  j.set("materials", mats);
  Json mach = Json::array();
  for (auto& m : machineCatalog()) {
    Json o = Json::object();
    o.set("id", m.id);
    o.set("name", m.name);
    o.set("fieldW", m.fieldW);
    o.set("fieldH", m.fieldH);
    o.set("kerf", m.kerf);
    o.set("lpi", (double)m.lpi);
    o.set("controller", m.controller);
    o.set("kind", m.kind);
    o.set("note", m.note);
    mach.push(o);
  }
  j.set("machines", mach);
  j.set("safety", safetyJson());
  j.set("categoryCount", (double)categoryCatalog().size());
  return j;
}

Json safetyJson() {
  Json arr = Json::array();
  for (auto& s : safetyNotes()) {
    Json o = Json::object();
    o.set("id", s.id);
    o.set("material", s.material);
    o.set("level", s.level);
    o.set("text", s.text);
    arr.push(o);
  }
  return arr;
}

}  // namespace lc
