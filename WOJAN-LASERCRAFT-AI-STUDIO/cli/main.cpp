// WOJAN LaserCraft AI Studio — narzędzie wiersza poleceń (silnik + serwer API + testy)
#include "core_api.h"
#include "zip.h"
#include <cstdio>
#include <cstring>
#include <string>

using namespace lc;

static void usage() {
  printf(
      "WOJAN LaserCraft AI Studio — silnik (CLI)\n"
      "Użycie: wojan-cli <polecenie> [opcje]\n\n"
      "  status                              informacje o silniku\n"
      "  catalog                             katalog produktów/materiałów/kategorii\n"
      "  analyze --prompt \"...\"              analiza promptu\n"
      "  generate --prompt \"...\" [--out DIR] [--variants N] [--dpi N]\n"
      "  edit --project PLIK --command \"...\"\n"
      "  validate --project PLIK\n"
      "  fix --project PLIK\n"
      "  render --project PLIK --out PNG [--pxmm N] [--guides]\n"
      "  export --project PLIK --out DIR [--formats svg,dxf,gcode,png]\n"
      "  package --project PLIK --out DIR\n"
      "  texts [--category KAT] [--count N]\n"
      "  qr --data \"...\" [--size MM]\n"
      "  cost [--json ...]\n"
      "  trends\n"
      "  serve [--port 8090] [--data DIR]   lokalny serwer API (LAN)\n"
      "  selftest [--out DIR]                testy wbudowane silnika\n");
}

static std::string arg(int argc, char** argv, const char* name, const char* def = "") {
  for (int i = 2; i < argc - 1; i++)
    if (std::strcmp(argv[i], name) == 0) return argv[i + 1];
  return def;
}
static bool flag(int argc, char** argv, const char* name) {
  for (int i = 2; i < argc; i++)
    if (std::strcmp(argv[i], name) == 0) return true;
  return false;
}

static Design loadDesign(Engine& eng, const std::string& path, bool& ok) {
  Json req = Json::object();
  req.set("path", path);
  Json r = eng.handle("load_project", req);
  ok = !r.has("error");
  if (!ok) return Design();
  return Design::fromJson(r["design"]);
}

int cmdSelftest(Engine& eng, const std::string& outDir);

int main(int argc, char** argv) {
  if (argc < 2) { usage(); return 1; }
  std::string cmd = argv[1];
  Engine eng;
  std::string dataDir = arg(argc, argv, "--data", "data");
  makeDirs(dataDir);
  eng.setDataDir(dataDir);
  eng.setFontDir(arg(argc, argv, "--fonts", "fonts"));

  if (cmd == "status") {
    printf("%s\n", eng.status().dump(2).c_str());
    return 0;
  }
  if (cmd == "catalog") {
    printf("%s\n", eng.catalog().dump(1).c_str());
    return 0;
  }
  if (cmd == "analyze") {
    Json req = Json::object();
    req.set("prompt", arg(argc, argv, "--prompt", ""));
    printf("%s\n", eng.handle("analyze", req).dump(2).c_str());
    return 0;
  }
  if (cmd == "generate") {
    Json req = Json::object();
    req.set("prompt", arg(argc, argv, "--prompt", ""));
    int variants = std::atoi(arg(argc, argv, "--variants", "1").c_str());
    req.set("variants", (double)variants);
    if (variants > 1) {
      Json st = Json::array();
      for (auto& s : {"rycina", "komiks", "retro", "minimal"}) st.push(s);
      req.set("variantStyles", st);
    }
    if (flag(argc, argv, "--style")) req.set("style", arg(argc, argv, "--style"));
    if (flag(argc, argv, "--product")) req.set("product", arg(argc, argv, "--product"));
    if (flag(argc, argv, "--top")) req.set("topText", arg(argc, argv, "--top"));
    if (flag(argc, argv, "--bottom")) req.set("bottomText", arg(argc, argv, "--bottom"));
    Json r = eng.handle("generate", req);
    std::string out = arg(argc, argv, "--out", "");
    if (r.has("error")) { printf("BŁĄD: %s\n", r["error"].str().c_str()); return 1; }
    Json designs = Json::array();
    if (r.has("variants")) {
      for (auto& v : r["variants"].items()) if (v.has("design")) designs.push(v["design"]);
    } else if (r.has("design")) {
      designs.push(r["design"]);
    }
    printf("Wygenerowano %zu wariant(ów)\n", designs.size());
    int idx = 0;
    for (auto& dj : designs.items()) {
      Design d = Design::fromJson(dj);
      Json rq = Json::object();
      rq.set("design", dj);
      rq.set("pxPerMm", 6.0);
      Json rj = eng.handle("render", rq);
      auto png = base64Decode(rj["pngBase64"].str());
      std::string base = out.empty() ? ("data/exports/" + slugify(d.name)) : (out + "/" + slugify(d.name));
      makeDirs(base);
      writeFileBytes(base + ".png", png);
      writeFile(base + ".wlcproj", dj.dump(1));
      writeFile(base + ".svg", eng.handle("export_svg", rq)["svg"].str());
      Json vr = eng.handle("validate", rq);
      writeFile(base + "_walidacja.txt", vr["text"].str());
      printf("  [%d] %s — %s (błędów: %d, ostrzeżeń: %d)\n", ++idx, d.name.c_str(), (base + ".png").c_str(),
             vr["errors"].integer(0), vr["warnings"].integer(0));
    }
    return 0;
  }
  if (cmd == "edit") {
    bool ok = false;
    Design d = loadDesign(eng, arg(argc, argv, "--project", ""), ok);
    if (!ok) { printf("BŁĄD: nie można wczytać projektu\n"); return 1; }
    Json req = Json::object();
    req.set("design", d.toJson());
    req.set("command", arg(argc, argv, "--command", ""));
    Json r = eng.handle("edit", req);
    printf("%s\n", r["message"].str().c_str());
    std::string savePath = arg(argc, argv, "--save", "");
    if (!savePath.empty()) writeFile(savePath, r["design"].dump(1));
    return r["ok"].boolean() ? 0 : 2;
  }
  if (cmd == "validate" || cmd == "fix") {
    bool ok = false;
    Design d = loadDesign(eng, arg(argc, argv, "--project", ""), ok);
    if (!ok) { printf("BŁĄD: nie można wczytać projektu\n"); return 1; }
    Json req = Json::object();
    req.set("design", d.toJson());
    Json r = eng.handle(cmd, req);
    printf("%s\n", r["text"].isString() ? r["text"].str().c_str() : r.dump(2).c_str());
    if (cmd == "fix") {
      std::string savePath = arg(argc, argv, "--save", "");
      if (!savePath.empty()) writeFile(savePath, r["design"].dump(1));
    }
    return r["ok"].boolean() ? 0 : 2;
  }
  if (cmd == "render") {
    bool ok = false;
    Design d = loadDesign(eng, arg(argc, argv, "--project", ""), ok);
    if (!ok) { printf("BŁĄD: nie można wczytać projektu\n"); return 1; }
    Json req = Json::object();
    req.set("design", d.toJson());
    req.set("pxPerMm", std::atof(arg(argc, argv, "--pxmm", "6").c_str()));
    req.set("guides", flag(argc, argv, "--guides"));
    Json r = eng.handle("render", req);
    auto png = base64Decode(r["pngBase64"].str());
    std::string out = arg(argc, argv, "--out", "render.png");
    writeFileBytes(out, png);
    printf("Zapisano %s (%dx%d, %zu B)\n", out.c_str(), r["width"].integer(), r["height"].integer(), png.size());
    return 0;
  }
  if (cmd == "export") {
    bool ok = false;
    Design d = loadDesign(eng, arg(argc, argv, "--project", ""), ok);
    if (!ok) { printf("BŁĄD: nie można wczytać projektu\n"); return 1; }
    std::string out = arg(argc, argv, "--out", "data/exports");
    makeDirs(out);
    Json req = Json::object();
    req.set("design", d.toJson());
    std::string formats = arg(argc, argv, "--formats", "svg,dxf,gcode,png");
    for (auto& f : split(formats, ',')) {
      if (f == "svg") {
        Json r = eng.handle("export_svg", req);
        writeFile(out + "/lightburn.svg", r["svg"].str());
        printf("SVG: %zu B\n", r["svg"].str().size());
        Json lr = Json::object();
        lr.set("design", d.toJson());
        lr.set("layer", "CUT_OUTER");
        writeFile(out + "/cut_outer.svg", eng.handle("export_svg", lr)["svg"].str());
        Json er = Json::object();
        er.set("design", d.toJson());
        er.set("layer", "ENGRAVE_VECTOR");
        writeFile(out + "/engrave_vector.svg", eng.handle("export_svg", er)["svg"].str());
      } else if (f == "dxf") {
        writeFile(out + "/design.dxf", eng.handle("export_dxf", req)["dxf"].str());
        printf("DXF zapisany\n");
      } else if (f == "gcode") {
        Json r = eng.handle("export_gcode", req);
        if (r["ok"].boolean()) {
          writeFile(out + "/laser.gcode", r["gcode"].str());
          printf("G-code: %zu B\n", r["gcode"].str().size());
        } else {
          printf("G-code: BŁĄD — %s\n", r["error"].str().c_str());
        }
      } else if (f == "png") {
        req.set("pxPerMm", 10.0);
        Json r = eng.handle("render", req);
        auto png = base64Decode(r["pngBase64"].str());
        writeFileBytes(out + "/lasergrbl.png", png);
        printf("PNG: %zu B\n", png.size());
      }
    }
    return 0;
  }
  if (cmd == "package") {
    bool ok = false;
    Design d = loadDesign(eng, arg(argc, argv, "--project", ""), ok);
    if (!ok) { printf("BŁĄD: nie można wczytać projektu\n"); return 1; }
    Json req = Json::object();
    req.set("design", d.toJson());
    req.set("dir", arg(argc, argv, "--out", "data/exports"));
    Json pngReq = Json::object();
    pngReq.set("design", d.toJson());
    pngReq.set("pxPerMm", 8.0);
    req.set("previewPng", eng.handle("render", pngReq)["pngBase64"].str());
    Json r = eng.handle("export_package", req);
    printf("%s\n", r.dump(2).c_str());
    return r["ok"].boolean() ? 0 : 1;
  }
  if (cmd == "texts") {
    Json req = Json::object();
    req.set("category", arg(argc, argv, "--category", "humor"));
    req.set("count", (double)std::atoi(arg(argc, argv, "--count", "6").c_str()));
    Json r = eng.handle("texts", req);
    for (auto& t : r["texts"].items())
      printf("• %s\n  %s\n", t["top"].str().c_str(), t["bottom"].str().c_str());
    return 0;
  }
  if (cmd == "qr") {
    Json req = Json::object();
    req.set("data", arg(argc, argv, "--data", "https://lasercraft.pl"));
    req.set("size", std::atof(arg(argc, argv, "--size", "30").c_str()));
    Json r = eng.handle("qr", req);
    printf("%s\n", r.dump(2).c_str());
    return r["ok"].boolean() ? 0 : 1;
  }
  if (cmd == "cost") {
    Json req = Json::object();
    req.set("areaMm2", 200.0 * 150.0);
    req.set("engraveAreaMm2", 12000.0);
    req.set("cutLengthMm", 700.0);
    printf("%s\n", eng.handle("calculator", req).dump(2).c_str());
    return 0;
  }
  if (cmd == "trends") {
    printf("%s\n", eng.handle("trends", Json::object()).dump(1).c_str());
    return 0;
  }
  if (cmd == "selftest") {
    return cmdSelftest(eng, arg(argc, argv, "--out", "data/selftest"));
  }
  if (cmd == "serve") {
    extern int runHttpServer(Engine& eng, int port, const std::string& wwwRoot);
    return runHttpServer(eng, std::atoi(arg(argc, argv, "--port", "8090").c_str()),
                         arg(argc, argv, "--www", "www"));
  }
  usage();
  return 1;
}
