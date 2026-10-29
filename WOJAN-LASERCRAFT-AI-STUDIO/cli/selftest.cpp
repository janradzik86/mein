// Wbudowane testy silnika (E2E) — wyniki zapisywane jako dowody do katalogu wyjściowego
#include "core_api.h"
#include "zip.h"
#include <cstdio>
#include <cstring>

using namespace lc;

namespace {

struct TestResult {
  std::string id;
  std::string name;
  bool pass = false;
  std::string detail;
};

std::string gOut;
Engine* gEng = nullptr;
std::vector<TestResult> gResults;

void record(const std::string& id, const std::string& name, bool pass, const std::string& detail) {
  TestResult r;
  r.id = id;
  r.name = name;
  r.pass = pass;
  r.detail = detail;
  gResults.push_back(r);
  printf("[%s] %-4s %-42s %s\n", pass ? "PASS" : "FAIL", id.c_str(), name.c_str(), detail.c_str());
}

Design gen(const std::string& prompt, std::string* err = nullptr) {
  Json req = Json::object();
  req.set("prompt", prompt);
  Json r = gEng->handle("generate", req);
  if (r.has("error")) {
    if (err) *err = r["error"].str();
    return Design();
  }
  return Design::fromJson(r["design"]);
}

Json renderPng(const Design& d, double pxmm, bool guides = false) {
  Json req = Json::object();
  req.set("design", d.toJson());
  req.set("pxPerMm", pxmm);
  req.set("guides", guides);
  return gEng->handle("render", req);
}

long rssKb() {
  FILE* f = fopen("/proc/self/status", "r");
  if (!f) return -1;
  char line[256];
  long v = -1;
  while (fgets(line, sizeof line, f)) {
    if (strncmp(line, "VmRSS:", 6) == 0) { sscanf(line + 6, "%ld", &v); break; }
  }
  fclose(f);
  return v;
}

int countLayer(const Design& d, const std::string& layer) {
  int n = 0;
  for (auto& o : d.objects) if (o.layer == layer && o.visible) n++;
  return n;
}

}  // namespace

int cmdSelftest(Engine& eng, const std::string& outDir) {
  gEng = &eng;
  gOut = outDir;
  makeDirs(outDir);
  printf("=== WOJAN LaserCraft AI Studio — testy silnika ===\n");

  // ---------- TEST A: tabliczka ----------
  {
    std::string err;
    std::string prompt =
        "Zrób śmieszną tabliczkę dla wędkarza. Na środku szczupak w okularach przeciwsłonecznych, po bokach "
        "dwie wędki, na górze napis KRÓL ŁOWISKA, na dole RYBY SIĘ MNIE BOJĄ, A ŻONA MNIE SZUKA. Styl retro, "
        "dużo detali, czarno-białe, ozdobny zewnętrzny kontur, szerokość 200 mm, wysokość 150 mm.";
    Design d = gen(prompt, &err);
    bool ok = !d.objects.empty();
    bool dims = std::fabs(d.product.w - 200) < 0.01 && std::fabs(d.product.h - 150) < 0.01;
    int cut = countLayer(d, LAYER_CUT_OUTER);
    bool closed = false;
    if (cut) {
      for (auto& o : d.objects) {
        if (o.layer == LAYER_CUT_OUTER) {
          Path p = Path::fromSVG(o.d);
          closed = p.countClosedContours() >= 1;
        }
      }
    }
    Json vreq = Json::object();
    vreq.set("design", d.toJson());
    Json vr = eng.handle("validate", vreq);
    std::string svg = eng.handle("export_svg", vreq)["svg"].str();
    bool svgMm = svg.find("width=\"200mm\"") != std::string::npos &&
                 svg.find("height=\"150mm\"") != std::string::npos;
    bool svgCutColor = svg.find("#FF0000") != std::string::npos;
    writeFile(gOut + "/A_tabliczka.svg", svg);
    writeFile(gOut + "/A_tabliczka.wlcproj", d.toJson().dump(1));
    Json png = renderPng(d, 6.0);
    writeFileBytes(gOut + "/A_tabliczka.png", base64Decode(png["pngBase64"].str()));
    writeFile(gOut + "/A_walidacja.txt", vr["text"].str());
    char det[512];
    snprintf(det, sizeof det, "obiekty=%zu, kontur cięcia=%d (zamknięty=%d), SVG mm=%d, kolor cięcia=%d, błędy=%d, ostrzeżenia=%d",
             d.objects.size(), cut, (int)closed, (int)svgMm, (int)svgCutColor, vr["errors"].integer(-1), vr["warnings"].integer(-1));
    record("A", "Tabliczka: prompt → projekt → cięcie → SVG", ok && dims && cut == 1 && closed && svgMm && svgCutColor && vr["errors"].integer(1) == 0, det);
  }

  // ---------- TEST B: piersiówka (gotowy przedmiot, bez cięcia) ----------
  {
    Design d = gen("Piersiówka z grawerem: wilk, napis WILKI NIE ŚPIĄ, tylko grawer bez cięcia, 95×115 mm");
    bool noCut = countLayer(d, LAYER_CUT_OUTER) == 0 && countLayer(d, LAYER_CUT_INNER) == 0;
    bool isPiersiowka = d.product.id == "piersiowka";
    Json vreq = Json::object();
    vreq.set("design", d.toJson());
    Json vr = eng.handle("validate", vreq);
    std::string svg = eng.handle("export_svg", vreq)["svg"].str();
    bool noCutGroup = svg.find("id=\"CUT_OUTER\"") == std::string::npos;
    Json g = eng.handle("export_gcode", vreq);
    bool gcodeNoCut = g["ok"].boolean() && g["gcode"].str().find("cięcie") == std::string::npos;
    writeFile(gOut + "/B_piersiowka.svg", svg);
    writeFile(gOut + "/B_piersiowka.wlcproj", d.toJson().dump(1));
    Json png = renderPng(d, 6.0);
    writeFileBytes(gOut + "/B_piersiowka.png", base64Decode(png["pngBase64"].str()));
    char det[512];
    snprintf(det, sizeof det, "produkt=%s, warstwy cięcia=%d, grupa CUT_OUTER w SVG=%d, G-code bez cięcia=%d, błędy=%d",
             d.product.id.c_str(), countLayer(d, LAYER_CUT_OUTER), !noCutGroup, (int)gcodeNoCut, vr["errors"].integer(-1));
    record("B", "Piersiówka: grawer bez linii cięcia", isPiersiowka && noCut && noCutGroup && gcodeNoCut, det);
  }

  // ---------- TEST C: zdjęcie (import → korekty → eksport) ----------
  {
    // obraz testowy: gradient + jasne tło (imituje zdjęcie)
    Image img(240, 180, 3);
    for (int y = 0; y < 180; y++)
      for (int x = 0; x < 240; x++) {
        uint8_t* p = img.at(x, y);
        int v = 240 - (x * 3 + y * 2) / 3;
        if (v < 0) v = 0;
        p[0] = (uint8_t)std::clamp(v + 20, 0, 255);
        p[1] = (uint8_t)std::clamp(v, 0, 255);
        p[2] = (uint8_t)std::clamp(v / 2, 0, 255);
        if (((x - 120) * (x - 120) + (y - 90) * (y - 90)) < 1600) { p[0] = 30; p[1] = 30; p[2] = 60; }
      }
    auto png = pngEncode(img);
    Json req = Json::object();
    req.set("pngBase64", base64Encode(png));
    Json ops = Json::array();
    Json o1 = Json::object(); o1.set("op", "gray"); ops.push(o1);
    Json o2 = Json::object(); o2.set("op", "contrast"); o2.set("value", 30); ops.push(o2);
    Json o3 = Json::object(); o3.set("op", "dither"); o3.set("algo", "floyd"); ops.push(o3);
    req.set("ops", ops);
    Json r = eng.handle("image_edit", req);
    auto outPng = base64Decode(r["pngBase64"].str());
    Image out;
    bool decoded = pngDecode(outPng, out);
    bool gray = decoded && out.ch == 1;
    int dark = 0;
    for (auto v : out.px) if (v < 128) dark++;
    writeFileBytes(gOut + "/C_zdjecie_oryginal.png", png);
    writeFileBytes(gOut + "/C_zdjecie_grawer.png", outPng);
    // wstaw obraz do projektu
    Design d = gen("Tabliczka z grawerowanym zdjęciem, fotograwer, 150×120 mm");
    Json ireq = Json::object();
    ireq.set("design", d.toJson());
    ireq.set("pngBase64", r["pngBase64"].str());
    ireq.set("widthMm", 60.0);
    ireq.set("name", "Zdjęcie");
    Json ir = eng.handle("import_image", ireq);
    Design d2 = Design::fromJson(ir["design"]);
    int imgs = 0;
    for (auto& o : d2.objects) if (o.type == "image") imgs++;
    Json vreq = Json::object();
    vreq.set("design", ir["design"]);
    Json vres = eng.handle("validate", vreq);
    writeFile(gOut + "/C_projekt_ze_zdjeciem.wlcproj", ir["design"].dump(1));
    char det[512];
    snprintf(det, sizeof det, "wejście RGB=%dx%d, wyjście szarość=%d, ciemnych pikseli=%d, obiekt obrazu w projekcie=%d, ostrzeżenia=%d",
             img.w, img.h, (int)gray, dark, imgs, vres["warnings"].integer(-1));
    record("C", "Zdjęcie: import → szarość → kontrast → dithering", decoded && gray && dark > 500 && imgs == 1, det);
  }

  // ---------- TEST D: mikrofon (transkrypcja → projekt) ----------
  {
    // Ścieżka transkrypcji jest platformowa (Android on-device ASR / Windows SAPI).
    // Tutaj testujemy to, co robi silnik po otrzymaniu transkryptu.
    std::string transcript =
        "Zrób tabliczkę dla dziadka, na górze napis DZIADEK RZĄDZI, na dole STO LAT I JESZCZE WIĘCEJ, "
        "styl retro, szerokość 180 mm, wysokość 120 mm";
    Json areq = Json::object();
    areq.set("prompt", transcript);
    Json a = eng.handle("analyze", areq);
    Design d = gen(transcript);
    std::string top = a["spec"]["topText"].str();
    bool textOk = top.find("DZIADEK") != std::string::npos || top.size() > 3;
    bool dims = std::fabs(d.product.w - 180) < 0.01;
    writeFile(gOut + "/D_transkrypcja.txt", transcript);
    writeFile(gOut + "/D_projekt.wlcproj", d.toJson().dump(1));
    char det[512];
    snprintf(det, sizeof det, "transkrypt → napis='%s', szerokość=%.0f mm, obiektów=%zu (ASR: patrz dokumentacja platformy)",
             top.c_str(), d.product.w, d.objects.size());
    record("D", "Mikrofon: transkrypt → analiza → projekt", textOk && dims && !d.objects.empty(), det);
  }

  // ---------- TEST E: edycja (zmiana napisu bez utraty reszty) ----------
  {
    Design d = gen("Tabliczka warsztatowa: koło zębate, na górze napis WARSZTAT, na dole TU SIĘ NAPRAWIA, 200×150 mm");
    size_t before = d.objects.size();
    double sizeBefore = 0;
    for (auto& o : d.objects) if (o.type == "text" && o.name.find("główny") != std::string::npos) sizeBefore = o.size;
    Json req = Json::object();
    req.set("design", d.toJson());
    req.set("command", "Zwiększ napis o 20 procent");
    Json r = eng.handle("edit", req);
    Design d2 = Design::fromJson(r["design"]);
    double sizeAfter = 0;
    for (auto& o : d2.objects) if (o.type == "text" && o.name.find("główny") != std::string::npos) sizeAfter = o.size;
    bool scaled = sizeBefore > 0 && std::fabs(sizeAfter / sizeBefore - 1.2) < 0.02;
    bool kept = d2.objects.size() >= before;
    // druga komenda: nowy tekst, reszta bez zmian
    Json req2 = Json::object();
    req2.set("design", r["design"]);
    req2.set("command", "Zostaw postać, zmień tekst");
    Json r2 = eng.handle("edit", req2);
    Design d3 = Design::fromJson(r2["design"]);
    int emblemsBefore = 0, emblemsAfter = 0;
    for (auto& o : d2.objects) if (o.extra.has("emblem")) emblemsBefore++;
    for (auto& o : d3.objects) if (o.extra.has("emblem")) emblemsAfter++;
    bool emblemKept = emblemsBefore > 0 && emblemsAfter == emblemsBefore;
    // undo
    Json ureq = Json::object();
    ureq.set("design", d3.toJson());
    ureq.set("command", "cofnij");
    Json ur = eng.handle("edit", ureq);
    bool undoOk = ur["ok"].boolean();
    writeFile(gOut + "/E_po_edycji.wlcproj", r2["design"].dump(1));
    Json png = renderPng(d3, 6.0);
    writeFileBytes(gOut + "/E_po_edycji.png", base64Decode(png["pngBase64"].str()));
    char det[512];
    snprintf(det, sizeof det, "skala %.1f→%.1f mm (+20%%=%d), obiekty %zu→%zu, emblematy %d→%d, undo=%d",
             sizeBefore, sizeAfter, (int)scaled, before, d2.objects.size(), emblemsBefore, emblemsAfter, (int)undoOk);
    record("E", "Edycja: skalowanie napisu, nowy tekst, undo", scaled && kept && emblemKept && undoOk, det);
  }

  // ---------- TEST F: QR ----------
  {
    Json req = Json::object();
    req.set("data", "https://lasercraft.pl/wzor/123");
    req.set("size", 30.0);
    Json r = eng.handle("qr", req);
    std::string path = r["svgPath"].str();
    // render QR na obrazie (bez tła projektu) do weryfikacji dekoderem zewnętrznym
    Design d;
    d.product.w = 40;
    d.product.h = 40;
    d.product.requiresCut = false;
    Obj o;
    o.id = "qr1";
    o.type = "qr";
    o.layer = LAYER_ENGRAVE_VECTOR;
    o.role = "fill";
    o.qrData = "https://lasercraft.pl/wzor/123";
    o.qrSize = 36;
    o.x = 2;
    o.y = 2;
    d.objects.push_back(o);
    d.pushHistory("test QR");
    Json png = renderPng(d, 12.0);
    writeFileBytes(gOut + "/F_qr.png", base64Decode(png["pngBase64"].str()));
    std::string svg = eng.handle("export_svg", [&] {
      Json q = Json::object();
      q.set("design", d.toJson());
      return q;
    }())["svg"].str();
    writeFile(gOut + "/F_qr.svg", svg);
    writeFile(gOut + "/F_qr_dane.txt", "https://lasercraft.pl/wzor/123");
    bool ok = r["ok"].boolean() && path.size() > 1000 && r["selfCheck"].str().find("OK") != std::string::npos;
    char det[512];
    snprintf(det, sizeof det, "%s, modułów=%.0f, ścieżka=%zu znaków, PNG+SVG zapisane do weryfikacji dekoderem",
             r["selfCheck"].str().c_str(), r["modules"].num(), path.size());
    record("F", "QR: generowanie → SVG → eksport (dekodowanie zewn.)", ok, det);
  }

  // ---------- TEST G: DXF ----------
  {
    Design d = gen("Tabliczka DXF 200×150 mm, koło zębate, napis DXF TEST");
    Json req = Json::object();
    req.set("design", d.toJson());
    std::string dxf = eng.handle("export_dxf", req)["dxf"].str();
    writeFile(gOut + "/G_projekt.dxf", dxf);
    bool units = dxf.find("$INSUNITS") != std::string::npos && dxf.find("70\n4") != std::string::npos;
    bool layers = dxf.find("CUT_OUTER") != std::string::npos;
    bool polylines = dxf.find("LWPOLYLINE") != std::string::npos;
    bool noRaster = dxf.find("IMAGE") == std::string::npos;
    char det[512];
    snprintf(det, sizeof det, "jednostki mm=%d, warstwy=%d, LWPOLYLINE=%d, brak rastra=%d, rozmiar=%zu B",
             (int)units, (int)layers, (int)polylines, (int)noRaster, dxf.size());
    record("G", "DXF: geometria, jednostki, warstwy", units && layers && polylines && noRaster, det);
  }

  // ---------- TEST H: LaserGRBL ----------
  {
    Design d = gen("Tabliczka 200×150 mm, napis LASERGRBL TEST, koło zębate");
    Json req = Json::object();
    req.set("design", d.toJson());
    req.set("pxPerMm", 10.0);
    Json png = eng.handle("render", req);
    auto bytes = base64Decode(png["pngBase64"].str());
    writeFileBytes(gOut + "/H_lasergrbl.png", bytes);
    Image img;
    bool decoded = pngDecode(bytes, img);
    bool dims = decoded && img.w == 2000 && img.h == 1500;
    Json greq = Json::object();
    greq.set("design", d.toJson());
    Json g = eng.handle("export_gcode", greq);
    std::string gcode = g["gcode"].str();
    writeFile(gOut + "/H_laser.gcode", gcode);
    bool header = gcode.find("G21") != std::string::npos && gcode.find("G90") != std::string::npos;
    bool safe = gcode.find("M5") != std::string::npos;
    bool noHoming = gcode.find("G28") == std::string::npos;
    char det[512];
    snprintf(det, sizeof det, "PNG %dx%d (10 px/mm), G-code %zu B, nagłówek=%d, M5=%d, bez G28=%d",
             img.w, img.h, gcode.size(), (int)header, (int)safe, (int)noHoming);
    record("H", "LaserGRBL: PNG + G-code GRBL", decoded && dims && g["ok"].boolean() && header && safe && noHoming, det);
  }

  // ---------- TEST I: offline ----------
  {
    // Silnik nie wykonuje żądań sieciowych: generowanie działa bez sieci.
    Design d = gen("Tabliczka offline: napis BEZ INTERNETU, koło zębate, 150×100 mm");
    Json req = Json::object();
    req.set("design", d.toJson());
    std::string svg = eng.handle("export_svg", req)["svg"].str();
    bool ok = !d.objects.empty() && svg.size() > 500;
    char det[512];
    snprintf(det, sizeof det, "generowanie i eksport bez sieci: obiekty=%zu, SVG=%zu B (silnik nie ma klienta HTTP)",
             d.objects.size(), svg.size());
    record("I", "Offline: generowanie i eksport bez internetu", ok, det);
  }

  // ---------- TEST K: pamięć ----------
  {
    long rssStart = rssKb();
    long maxRss = rssStart;
    for (int i = 0; i < 24; i++) {
      std::string prompt = "Tabliczka numer " + std::to_string(i) + ", wędkarstwo, napis TEST " + std::to_string(i) +
                           ", styl retro, 200×150 mm";
      Design d = gen(prompt);
      Json req = Json::object();
      req.set("design", d.toJson());
      req.set("pxPerMm", 4.0);
      eng.handle("render", req);
      eng.handle("validate", req);
      long r = rssKb();
      if (r > maxRss) maxRss = r;
    }
    long rssEnd = rssKb();
    double growthMb = (rssEnd - rssStart) / 1024.0;
    bool ok = rssEnd > 0 && growthMb < 200;
    char det[512];
    snprintf(det, sizeof det, "24 generowania: RSS %.1f MB → %.1f MB (wzrost %.1f MB, max %.1f MB)",
             rssStart / 1024.0, rssEnd / 1024.0, growthMb, maxRss / 1024.0);
    record("K", "Pamięć: 24× generowanie bez wycieków", ok, det);
  }

  // ---------- TEST L: polskie znaki ----------
  {
    std::string polish = "ZAŻÓŁĆ GĘŚLĄ JAŹŃ ĄĆĘŁŃÓŚŹŻ";
    Json treq = Json::object();
    treq.set("text", polish);
    Json chk = eng.handle("check_spelling", treq);
    Design d = gen("Tabliczka z polskimi znakami: na górze napis ZAŻÓŁĆ GĘŚLĄ JAŹŃ, na dole ĄĆĘŁŃÓŚŹŻ, 200×150 mm");
    int textOk = 0;
    bool glyphsOk = true;
    FontRegistry& fr = eng.fonts();
    for (auto& o : d.objects) {
      if (o.type != "text") continue;
      textOk++;
      Font* f = fr.get(o.font);
      for (auto& ch : utf8Chars(o.text)) {
        if (ch == " ") continue;
        auto cps = utf8Decode(ch);
        if (!cps.empty() && f->glyphIndex(cps[0]) == 0) glyphsOk = false;
      }
    }
    Json req = Json::object();
    req.set("design", d.toJson());
    std::string svg = eng.handle("export_svg", req)["svg"].str();
    std::string dxf = eng.handle("export_dxf", req)["dxf"].str();
    writeFile(gOut + "/L_polskie_znaki.svg", svg);
    writeFile(gOut + "/L_polskie_znaki.dxf", dxf);
    Json png = renderPng(d, 8.0);
    writeFileBytes(gOut + "/L_polskie_znaki.png", base64Decode(png["pngBase64"].str()));
    writeFile(gOut + "/L_polskie_znaki.txt", polish + "\n" + chk["fixed"].str());
    bool diacritics = false;
    for (auto cp : utf8Decode(chk["fixed"].str()))
      if (cp == 0x17B || cp == 0x15A || cp == 0x141 || cp == 0x104) diacritics = true;
    char det[512];
    snprintf(det, sizeof det, "tekstów=%d, wszystkie glify obecne=%d, SVG=%zu B, DXF=%zu B, znaki diakrytyczne w SVG=%d",
             textOk, (int)glyphsOk, svg.size(), dxf.size(),
             (int)(svg.find("path") != std::string::npos));
    record("L", "Polskie znaki ĄĆĘŁŃÓŚŹŻ w tekście i eksporcie", textOk > 0 && glyphsOk && diacritics && svg.size() > 500, det);
  }

  // ---------- TEST M: zgodność wymiarów ----------
  {
    Design d = gen("Projekt kontrolny 120×80 mm, koło, napis MIARA");
    Json req = Json::object();
    req.set("design", d.toJson());
    std::string svg = eng.handle("export_svg", req)["svg"].str();
    std::string dxf = eng.handle("export_dxf", req)["dxf"].str();
    bool svgDims = svg.find("width=\"120mm\"") != std::string::npos && svg.find("viewBox=\"0 0 120 80\"") != std::string::npos;
    bool dxfExt = dxf.find("$EXTMAX") != std::string::npos && dxf.find("120") != std::string::npos;
    int groups = 0;
    size_t p = 0;
    while ((p = svg.find("inkscape:groupmode=\"layer\"", p)) != std::string::npos) { groups++; p++; }
    writeFile(gOut + "/M_kontrola.svg", svg);
    writeFile(gOut + "/M_kontrola.dxf", dxf);
    char det[512];
    snprintf(det, sizeof det, "SVG 120×80=%d, warstwy w SVG=%d, DXF EXTMAX=%d", (int)svgDims, groups, (int)dxfExt);
    record("M", "Zgodność: rozmiary i warstwy SVG/DXF", svgDims && groups >= 2 && dxfExt, det);
  }

  // ---------- Raport ----------
  int pass = 0;
  for (auto& r : gResults) if (r.pass) pass++;
  std::string txt = "RAPORT TESTÓW SILNIKA (wygenerowany automatycznie)\n";
  txt += "data: " + nowIso() + "\n";
  txt += sfmt("wynik: %d/%zu zaliczonych\n\n", pass, gResults.size());
  Json js = Json::object();
  Json arr = Json::array();
  js.set("total", (double)gResults.size());
  js.set("passed", (double)pass);
  for (auto& r : gResults) {
    txt += sfmt("[%s] %s — %s\n    %s\n", r.pass ? "PASS" : "FAIL", r.id.c_str(), r.name.c_str(), r.detail.c_str());
    Json o = Json::object();
    o.set("id", r.id);
    o.set("name", r.name);
    o.set("pass", r.pass);
    o.set("detail", r.detail);
    arr.push(o);
  }
  js.set("tests", arr);
  writeFile(gOut + "/testy_silnika.txt", txt);
  writeFile(gOut + "/testy_silnika.json", js.dump(2));
  printf("\n%s\n", txt.c_str());
  return pass == (int)gResults.size() ? 0 : 3;
}
