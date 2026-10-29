#include "validate.h"
#include "qr.h"
#include <cmath>
#include <set>

namespace lc {

void ValidationReport::add(Severity s, const std::string& code, const std::string& msg,
                           const std::string& objectId, const std::string& hint, bool fixable) {
  Issue i{s, code, msg, objectId, hint, fixable};
  issues.push_back(i);
  if (s == Severity::Error) errors++;
  else if (s == Severity::Warning) warnings++;
  else infos++;
}
std::string ValidationReport::toText() const {
  std::string s = "RAPORT WALIDACJI PROJEKTU (WOJAN LaserCraft AI Studio)\n";
  s += "data: " + nowIso() + "\n";
  s += "wynik: " + std::string(ok() ? "GOTOWY DO EKSPORTU (bez błędów krytycznych)" : "BŁĘDY — popraw przed eksportem") + "\n";
  s += sfmt("błędy: %d, ostrzeżenia: %d, informacje: %d\n\n", errors, warnings, infos);
  auto label = [](Severity s) {
    return s == Severity::Error ? "BŁĄD " : (s == Severity::Warning ? "UWAGA" : "INFO ");
  };
  for (auto& i : issues) {
    s += std::string("[") + label(i.severity) + "] " + i.code + ": " + i.message;
    if (!i.objectId.empty()) s += " (obiekt: " + i.objectId + ")";
    s += "\n";
    if (!i.fixHint.empty()) s += "         poprawka: " + i.fixHint + "\n";
  }
  if (issues.empty()) s += "Brak uwag.\n";
  return s;
}
std::string ValidationReport::toJson() const {
  Json j = Json::object();
  j.set("ok", ok());
  j.set("errors", errors);
  j.set("warnings", warnings);
  j.set("infos", infos);
  Json arr = Json::array();
  for (auto& i : issues) {
    Json o = Json::object();
    o.set("severity", i.severity == Severity::Error ? "error" : (i.severity == Severity::Warning ? "warning" : "info"));
    o.set("code", i.code);
    o.set("message", i.message);
    if (!i.objectId.empty()) o.set("object", i.objectId);
    if (!i.fixHint.empty()) o.set("fix", i.fixHint);
    o.set("fixable", i.fixable);
    arr.push(o);
  }
  j.set("issues", arr);
  return j.dump(2);
}
bool ValidationReport::has(const std::string& code) const {
  for (auto& i : issues) if (i.code == code) return true;
  return false;
}

namespace {
struct PathData {
  const Obj* obj;
  Path path;
  std::vector<Contour> contours;
  Rect bounds;
};
}

ValidationReport validateDesign(const Design& d, FontRegistry& fonts, const AssetStore& assets) {
  ValidationReport rep;
  const double kerf = std::max(0.0, d.machine.kerf);
  const double minStroke = 0.2;
  const double minTextCap = 2.5;      // mm — minimalna wysokość liter czytelna po grawerze
  const double minGap = std::max(0.2, kerf * 1.5);

  // 1) wymiary i pole robocze
  if (d.product.w <= 0 || d.product.h <= 0) {
    rep.add(Severity::Error, "WYMIARY", "Produkt ma nieprawidłowe wymiary.", "", "Ustaw szerokość i wysokość większe od zera.");
  }
  if (d.product.w > d.machine.fieldW || d.product.h > d.machine.fieldH) {
    rep.add(Severity::Error, "POLE_ROBOCZE",
            sfmt("Projekt %.0f×%.0f mm nie mieści się w polu roboczym %.0f×%.0f mm.",
                 d.product.w, d.product.h, d.machine.fieldW, d.machine.fieldH),
            "", "Zmniejsz wymiary albo wybierz maszynę z większym polem.");
  }
  if (d.product.engraveMargin < 0) {
    rep.add(Severity::Error, "MARGINES", "Ujemny margines bezpieczeństwa.", "", "Ustaw margines ≥ 0 mm.");
  }
  if (d.product.engraveMargin < 1.0 && d.product.engraveMargin >= 0) {
    rep.add(Severity::Warning, "MARGINES", "Margines bezpieczeństwa poniżej 1 mm — ryzyko uszkodzenia krawędzi.", "",
            "Zalecane 3–6 mm.");
  }
  Rect engrave = Rect(d.product.engraveMargin, d.product.engraveMargin,
                      std::max(0.0, d.product.w - 2 * d.product.engraveMargin),
                      std::max(0.0, d.product.h - 2 * d.product.engraveMargin));

  // 2) materiał
  const MaterialDef* mat = findMaterial(d.product.material);
  if (!mat) {
    rep.add(Severity::Warning, "MATERIAL", "Nieznany profil materiału: " + d.product.material, "",
            "Wybierz materiał z listy — parametry mocy zależą od materiału.");
  } else {
    if (!mat->safe) rep.add(Severity::Error, "MATERIAL_BHP", mat->warning, "",
                            "Nie tnij nieznanych tworzyw; sprawdź kartę bezpieczeństwa.");
    else if (!mat->warning.empty()) rep.add(Severity::Warning, "MATERIAL_INFO", mat->warning);
  }
  if (d.product.requiresCut && mat && !mat->cuts)
    rep.add(Severity::Warning, "MATERIAL_CIEBIE",
            "Wybrany materiał (" + mat->name + ") zwykle nie jest cięty tą technologią.", "",
            "Rozważ wyłączenie cięcia (projekt na gotowym przedmiocie).");

  // 3) zbierz geometrię
  std::vector<PathData> datas;
  for (auto& o : d.objects) {
    if (!o.visible) continue;
    if (o.layer == LAYER_GUIDES) continue;
    Path p;
    if (o.type == "text") {
      Font* f = fonts.get(o.font);
      if (!f || !f->valid()) {
        rep.add(Severity::Error, "FONT", "Brak fontu dla tekstu: " + o.text, o.id, "Wybierz inny font.");
        continue;
      }
      if (o.size < minTextCap)
        rep.add(Severity::Error, "TEKST_MALY",
                sfmt("Wysokość liter %.1f mm jest za mała (minimum %.1f mm).", o.size, minTextCap), o.id,
                "Zwiększ rozmiar tekstu.", true);
      // brakujące znaki
      std::string missing;
      for (auto& ch : utf8Chars(o.text)) {
        if (ch == " ") continue;
        auto cps = utf8Decode(ch);
        if (!cps.empty() && f->glyphIndex(cps[0]) == 0) missing += ch;
      }
      if (!missing.empty())
        rep.add(Severity::Error, "BRAK_ZNAKOW", "Font nie zawiera znaków: " + missing, o.id,
                "Zmień font lub usuń znaki.", false);
      p = textPath(f, o.text, o.size, o.tracking, o.align, o.warp, o.warpAmount, Vec2(o.x, o.y));
    } else if (o.type == "qr") {
      QrCode qr = qrEncode(o.qrData);
      std::string chk = qrSelfCheck(qr);
      if (!qr.ok) rep.add(Severity::Error, "QR", chk, o.id, "Skróć dane QR.", false);
      else {
        double module = o.qrSize / (qr.size + 8);
        if (module < 0.4)
          rep.add(Severity::Warning, "QR_MODUL", sfmt("Moduł QR %.2f mm — może być nieczytelny po grawerowaniu.", module), o.id,
                  "Zwiększ rozmiar QR do min. 20 mm lub skróć dane.");
      }
      p = qrPath(o.qrData, o.qrSize);
      p.apply(Transform2::translate(o.x, o.y));
    } else if (o.type == "image") {
      if (!assets.has(o.asset)) {
        rep.add(Severity::Error, "OBRAZ", "Brak danych obrazu w projekcie.", o.id, "Usuń obiekt lub wgraj obraz ponownie.");
        continue;
      }
      Image img;
      assets.get(o.asset, img);
      Rect r(o.x, o.y, o.imgW, o.imgH);
      if (!engrave.contains(r) && d.product.engraveMargin > 0)
        rep.add(Severity::Warning, "OBRAZ_POZA", "Obraz wykracza poza obszar grawerowania.", o.id,
                "Przesuń lub zmniejsz obraz.", true);
      continue;
    } else {
      p = Path::fromSVG(o.d);
      p.apply(Transform2::translate(o.x, o.y));
      if (std::fabs(o.sx - 1) > 1e-9 || std::fabs(o.sy - 1) > 1e-9 || std::fabs(o.rot) > 1e-9)
        p.apply(o.transform());
    }
    if (p.empty()) {
      rep.add(Severity::Warning, "PUSTY_OBIEKT", "Obiekt nie zawiera geometrii.", o.id, "Usuń obiekt.", false);
      continue;
    }
    PathData pd;
    pd.obj = &o;
    pd.path = p;
    pd.contours = p.flatten(0.1);
    pd.bounds = p.bounds();
    datas.push_back(pd);
  }

  // 4) warstwy cięcia: zamknięcie, zdublowane ścieżki, cienkie elementy
  int cutOuterCount = 0;
  for (auto& pd : datas) {
    const Obj& o = *pd.obj;
    bool isCut = layerIsCut(o.layer);
    if (isCut) {
      if (o.layer == LAYER_CUT_OUTER) cutOuterCount++;
      for (auto& c : pd.contours) {
        if (!c.closed || c.pts.size() < 3) {
          rep.add(Severity::Error, "KONTUR_OTWARTY",
                  "Kontur cięcia nie jest zamknięty — laser nie wytnie elementu.", o.id,
                  "Zamknij ścieżkę (połącz końce).", false);
        }
        if (std::fabs(c.area()) < 0.05)
          rep.add(Severity::Warning, "KONTUR_MALY", "Bardzo mały kontur cięcia (<0,05 mm²) — może się przepalić.", o.id, "", false);
      }
      if (o.strokeW > 0 && o.strokeW < 0.1)
        rep.add(Severity::Warning, "CIENKA_LINIA", "Linia cięcia cieńsza niż 0,1 mm.", o.id,
                "Linie cięcia powinny mieć 0,1–0,2 mm (grubość nie ma znaczenia dla lasera, ale ułatwia pracę).", false);
    } else {
      if (o.strokeW > 0 && o.strokeW < minStroke)
        rep.add(Severity::Warning, "CIENKA_LINIA", sfmt("Grawer liniowy %.2f mm może być niewidoczny.", o.strokeW), o.id,
                "Zwiększ grubość linii do min. 0,2 mm.", true);
    }
    // marginesy / poza przedmiotem — kontur cięcia jest celowo powiększony o kerf,
    // więc warstw cięcia nie traktujemy jak "element poza produktem"
    if (!isCut && !engrave.contains(pd.bounds)) {
      bool outsideProduct = pd.bounds.x < -0.01 || pd.bounds.y < -0.01 ||
                            pd.bounds.x1() > d.product.w + 0.01 || pd.bounds.y1() > d.product.h + 0.01;
      if (outsideProduct)
        rep.add(Severity::Error, "POZA_PRODUKTEM", "Element wykracza poza obrys produktu.", o.id,
                "Przesuń element do obszaru roboczego.", true);
      else
        rep.add(Severity::Warning, "MARGINES_ELEMENT",
                "Element wchodzi w margines bezpieczeństwa od krawędzi.", o.id,
                "Zachowaj odstęp od krawędzi.", true);
    }
  }

  // 5) liczba zewnętrznych konturów cięcia
  if (!d.product.requiresCut) {
    if (cutOuterCount > 0)
      rep.add(Severity::Error, "CIECIE_NIEOCZEKIWANE",
              "Produkt jest gotowym przedmiotem, a projekt zawiera warstwę CUT_OUTER — eksport mógłby uruchomić cięcie przedmiotu.",
              "cut_outer", "Usuń warstwę cięcia (opcja 'napraw' usuwa ją automatycznie).", true);
  } else {
    if (cutOuterCount == 0)
      rep.add(Severity::Error, "BRAK_KONTURU", "Brak zewnętrznego konturu cięcia dla elementu wycinanego.", "",
              "Dodaj kontur cięcia (naprawa automatyczna).", true);
    else if (cutOuterCount > 1)
      rep.add(Severity::Warning, "WIELE_KONTUROW",
              sfmt("Wykryto %d konturów zewnętrznych — upewnij się, że to zamierzone (np. wiele sztuk).", cutOuterCount), "",
              "Dla jednego elementu powinien być jeden kontur.", false);
  }

  // 6) zdublowane ścieżki (te same punkty startowe i ten sam kształt)
  for (size_t i = 0; i < datas.size(); i++) {
    for (size_t j = i + 1; j < datas.size(); j++) {
      if (datas[i].obj->layer != datas[j].obj->layer) continue;
      Rect a = datas[i].bounds, b = datas[j].bounds;
      if (std::fabs(a.x - b.x) < 0.02 && std::fabs(a.y - b.y) < 0.02 &&
          std::fabs(a.w - b.w) < 0.02 && std::fabs(a.h - b.h) < 0.02) {
        rep.add(Severity::Warning, "DUPLIKAT",
                "Dwa obiekty mają identyczny obrys w tej samej warstwie (możliwe zdublowane cięcie).",
                datas[j].obj->id, "Usuń duplikat — podwójne cięcie zwiększa ryzyko zapłonu materiału.", true);
      }
    }
  }

  // 7) minimalne odstępy między elementami cięcia
  std::vector<Rect> cutRects;
  for (auto& pd : datas) if (layerIsCut(pd.obj->layer)) cutRects.push_back(pd.bounds);
  for (size_t i = 0; i < cutRects.size(); i++)
    for (size_t j = i + 1; j < cutRects.size(); j++) {
      Rect a = cutRects[i].inflate(minGap), b = cutRects[j];
      if (a.overlaps(b) && !cutRects[i].overlaps(cutRects[j]))
        rep.add(Severity::Warning, "ODSTEP",
                sfmt("Odstęp między elementami cięcia mniejszy niż %.1f mm (kerf %.2f mm) — materiał może pęknąć.", minGap, kerf));
    }

  // 8) raster: rozdzielczość
  for (auto& pd : datas) {
    (void)pd;
  }
  for (auto& o : d.objects) {
    if (o.type != "image" || !o.visible) continue;
    Image img;
    if (!assets.get(o.asset, img)) continue;
    if (o.imgW > 0) {
      double dpi = img.w / (o.imgW / 25.4);
      if (dpi < 150)
        rep.add(Severity::Warning, "RASTER_DPI", sfmt("Rozdzielczość obrazu %.0f DPI — poniżej 150 DPI grawer będzie rozmyty.", dpi), o.id,
                "Zwiększ rozdzielczość lub zmniejsz rozmiar obrazu.", false);
      if (dpi > 1200)
        rep.add(Severity::Info, "RASTER_DPI_HI", sfmt("Obraz ma %.0f DPI — czas grawerowania będzie długi.", dpi), o.id, "", false);
    }
    if (o.dither == "none" && o.threshold < 0)
      rep.add(Severity::Info, "RASTER_TRYB", "Obraz bez progowania i ditheringu — eksport rastrowy użyje skali szarości.", o.id);
  }

  // 9) operacje/kolory warstw
  std::set<std::string> seen;
  const std::vector<std::string> valid = allLayers();
  for (auto& l : d.layers) {
    if (std::find(valid.begin(), valid.end(), l.id) == valid.end()) {
      rep.add(Severity::Warning, "WARSTWA_NIEZNANA", "Nieznana warstwa: " + l.id);
    }
    if (!seen.insert(l.id).second)
      rep.add(Severity::Warning, "WARSTWA_DUPLIKAT", "Zduplikowana definicja warstwy: " + l.id);
    if (l.color.empty() || l.color[0] != '#')
      rep.add(Severity::Warning, "KOLOR", "Nieprawidłowy kolor operacji w warstwie " + l.label);
  }

  // 10) puste warstwy / brak treści
  if (d.objects.empty())
    rep.add(Severity::Error, "BRAK_TESCI", "Projekt nie zawiera żadnych obiektów.", "", "Wygeneruj wzór lub dodaj elementy.");

  // 11) kerf — informacja o kompensacji
  if (kerf <= 0)
    rep.add(Severity::Warning, "KERF", "Kerf = 0 mm — elementy będą ciaśniejsze od projektu.", "",
            "Zmierz kerf i wpisz wartość (typowo 0,1–0,3 mm).");
  else
    rep.add(Severity::Info, "KERF_OK", sfmt("Kompensacja kerf: %.2f mm (kontur cięcia powiększony o %.2f mm na stronę).", kerf, kerf / 2));

  // 12) podsumowanie
  rep.add(Severity::Info, "PODSUMOWANIE",
          sfmt("Obiekty: %zu, powierzchnia grawerowania: %.0f mm², długość cięcia: %.0f mm",
               d.objects.size(), designEngraveAreaMm2(d, fonts, assets), designCutLengthMm(d)));
  return rep;
}

double designEngraveAreaMm2(const Design& d, FontRegistry& fonts, const AssetStore& assets) {
  double total = 0;
  for (auto& o : d.objects) {
    if (!o.visible) continue;
    if (o.layer == LAYER_CUT_OUTER || o.layer == LAYER_CUT_INNER || o.layer == LAYER_GUIDES) continue;
    if (o.type == "image") { total += o.imgW * o.imgH; continue; }
    Path p;
    if (o.type == "text") p = textPath(fonts.get(o.font), o.text, o.size, o.tracking, o.align, o.warp, o.warpAmount, Vec2(o.x, o.y));
    else if (o.type == "qr") { p = qrPath(o.qrData, o.qrSize); p.apply(Transform2::translate(o.x, o.y)); }
    else { p = Path::fromSVG(o.d); p.apply(Transform2::translate(o.x, o.y)); }
    Rect b = p.bounds();
    total += b.w * b.h;
  }
  (void)assets;
  return total;
}
double designCutLengthMm(const Design& d) {
  double total = 0;
  for (auto& o : d.objects) {
    if (!o.visible || !layerIsCut(o.layer)) continue;
    Path p = Path::fromSVG(o.d);
    total += p.totalLength();
  }
  return total;
}

std::vector<std::string> applySafeFixes(Design& d, FontRegistry& fonts, const AssetStore& assets) {
  std::vector<std::string> done;
  // 1) gotowy przedmiot → usuń warstwy cięcia
  if (!d.product.requiresCut) {
    size_t before = d.objects.size();
    for (auto it = d.objects.begin(); it != d.objects.end();) {
      if (layerIsCut(it->layer)) it = d.objects.erase(it);
      else ++it;
    }
    if (d.objects.size() != before)
      done.push_back("Usunięto " + std::to_string(before - d.objects.size()) + " elementów cięcia (produkt nie jest wycinany).");
  } else {
    // brak konturu cięcia → dodaj
    bool has = false;
    for (auto& o : d.objects) if (o.layer == LAYER_CUT_OUTER) has = true;
    if (!has) {
      ensureCutContour(d);
      done.push_back("Dodano kontur cięcia zgodny z kształtem produktu.");
    }
  }
  // 2) zbyt małe litery → powiększ
  for (auto& o : d.objects) {
    if (o.type == "text" && o.size < 2.5) {
      done.push_back("Powiększono tekst '" + o.text + "' z " + fmtDouble(o.size, 1) + " mm do 2,5 mm.");
      o.size = 2.5;
    }
    if (o.type != "text" && o.strokeW > 0 && o.strokeW < 0.2) {
      o.strokeW = 0.2;
      done.push_back("Zwiększono grubość linii obiektu '" + (o.name.empty() ? o.id : o.name) + "' do 0,2 mm.");
    }
  }
  // 3) elementy poza obszarem → wsuń do środka obszaru grawerowania
  Rect engrave(d.product.engraveMargin, d.product.engraveMargin,
               std::max(1.0, d.product.w - 2 * d.product.engraveMargin),
               std::max(1.0, d.product.h - 2 * d.product.engraveMargin));
  for (auto& o : d.objects) {
    if (!o.visible || layerIsCut(o.layer)) continue;
    Rect b;
    if (o.type == "image") b = Rect(o.x, o.y, o.imgW, o.imgH);
    else if (o.type == "path") { b = Path::fromSVG(o.d).bounds(); b = Rect(o.x + b.x, o.y + b.y, b.w, b.h); }
    else continue;
    if (b.w > engrave.w || b.h > engrave.h) continue;
    double dx = 0, dy = 0;
    if (b.x < engrave.x) dx = engrave.x - b.x;
    else if (b.x1() > engrave.x1()) dx = engrave.x1() - b.x1();
    if (b.y < engrave.y) dy = engrave.y - b.y;
    else if (b.y1() > engrave.y1()) dy = engrave.y1() - b.y1();
    if (std::fabs(dx) > 1e-6 || std::fabs(dy) > 1e-6) {
      o.x += dx;
      o.y += dy;
      done.push_back("Przesunięto obiekt '" + (o.name.empty() ? o.id : o.name) + "' do obszaru grawerowania.");
    }
  }
  // 4) duplikaty
  for (size_t i = 0; i < d.objects.size(); i++) {
    for (size_t j = i + 1; j < d.objects.size(); j++) {
      Obj& a = d.objects[i];
      Obj& b = d.objects[j];
      if (a.type != "path" || b.type != "path") continue;
      if (a.layer != b.layer) continue;
      if (a.d == b.d && std::fabs(a.x - b.x) < 0.01 && std::fabs(a.y - b.y) < 0.01) {
        d.objects.erase(d.objects.begin() + j);
        done.push_back("Usunięto zdublowany obiekt '" + b.id + "'.");
        j--;
      }
    }
  }
  (void)fonts;
  (void)assets;
  return done;
}

}  // namespace lc
