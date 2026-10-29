#include "ttf.h"
#include <cmath>
#include <cstring>

namespace lc {

static uint16_t rd16(const uint8_t* p) { return (uint16_t)((p[0] << 8) | p[1]); }
static int16_t rds16(const uint8_t* p) { return (int16_t)rd16(p); }
static uint32_t rd32(const uint8_t* p) {
  return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3];
}

const uint8_t* Font::tbl(const char* tag, uint32_t* len) const {
  auto it = tables_.find(std::string(tag, 4));
  if (it == tables_.end()) return nullptr;
  if (len) *len = it->second.second;
  return d_.data() + it->second.first;
}

bool Font::loadFile(const std::string& path) {
  std::vector<uint8_t> b;
  if (!readFileBytes(path, b) || b.size() < 128) return false;
  return loadBytes(std::move(b));
}

bool Font::loadBytes(std::vector<uint8_t> data) {
  d_ = std::move(data);
  if (d_.size() < 12) return false;
  const uint8_t* p = d_.data();
  uint32_t ver = rd32(p);
  if (ver == 0x74746366) return false;  // 'ttcf' — kolekcja: nieobsługiwana
  uint16_t numTables = rd16(p + 4);
  for (uint16_t i = 0; i < numTables; i++) {
    const uint8_t* e = p + 12 + i * 16;
    if (12 + i * 16 + 16 > d_.size()) break;
    char tag[5] = {(char)e[0], (char)e[1], (char)e[2], (char)e[3], 0};
    uint32_t off = rd32(e + 8), len = rd32(e + 12);
    if (off + len <= d_.size()) tables_[tag] = {off, len};
  }
  const uint8_t* head = tbl("head");
  if (!head) return false;
  upem_ = rd16(head + 18);
  if (upem_ == 0) return false;
  locaFmt_ = rds16(head + 50);
  const uint8_t* maxp = tbl("maxp");
  if (!maxp) return false;
  numGlyphs_ = rd16(maxp + 4);
  const uint8_t* hhea = tbl("hhea");
  if (hhea) {
    asc_ = rds16(hhea + 4);
    desc_ = rds16(hhea + 6);
    uint16_t numH = rd16(hhea + 34);
    const uint8_t* hmtx = tbl("hmtx");
    if (hmtx) {
      for (uint16_t i = 0; i < numH; i++) {
        uint32_t off = i * 4;
        hmtx_.push_back({(uint32_t)rd16(hmtx + off), (uint32_t)rds16(hmtx + off + 2)});
      }
    }
  }
  const uint8_t* os2 = tbl("OS/2");
  if (os2) {
    uint16_t os2ver = rd16(os2);
    if (os2ver >= 2) {
      int16_t cap = rds16(os2 + 88);
      int16_t xh = rds16(os2 + 86);
      if (cap > 0) cap_ = cap;
      if (xh > 0) xh_ = xh;
    }
    if (cap_ == 0) cap_ = asc_ * 0.7;
    if (xh_ == 0) xh_ = cap_ * 0.7;
  } else {
    cap_ = asc_ * 0.7;
    xh_ = asc_ * 0.5;
  }
  const uint8_t* loca = tbl("loca");
  if (loca) {
    if (locaFmt_ == 0) {
      for (uint32_t i = 0; i <= numGlyphs_; i++) loca_.push_back((uint32_t)rd16(loca + i * 2) * 2);
    } else {
      for (uint32_t i = 0; i <= numGlyphs_; i++) loca_.push_back(rd32(loca + i * 4));
    }
  }
  // cmap
  const uint8_t* cm = tbl("cmap");
  uint32_t cmLen = 0;
  cm = tbl("cmap", &cmLen);
  if (cm) {
    uint16_t n = rd16(cm + 2);
    int best = -1;
    uint32_t bestScore = 0;
    for (uint16_t i = 0; i < n; i++) {
      const uint8_t* rec = cm + 4 + i * 8;
      uint16_t pid = rd16(rec), eid = rd16(rec + 2);
      uint32_t off = rd32(rec + 4);
      if (off + 2 > cmLen) continue;
      const uint8_t* sub = cm + off;
      uint16_t fmt = rd16(sub);
      uint32_t score = 0;
      if (fmt == 12) score = 100;
      else if (fmt == 4) score = 80;
      else if (fmt == 6) score = 40;
      if (pid == 3 && eid == 10) score += 20;
      if (pid == 3 && eid == 1) score += 10;
      if (score > bestScore) { bestScore = score; best = (int)off; }
    }
    if (best >= 0) {
      const uint8_t* sub = cm + best;
      uint16_t fmt = rd16(sub);
      if (fmt == 4) {
        uint16_t segX2 = rd16(sub + 6);
        uint16_t segs = segX2 / 2;
        const uint8_t* endArr = sub + 14;
        const uint8_t* startArr = endArr + segX2 + 2;
        const uint8_t* deltaArr = startArr + segX2;
        const uint8_t* rangeArr = deltaArr + segX2;
        for (uint16_t s = 0; s < segs; s++) {
          uint32_t end = rd16(endArr + s * 2), start = rd16(startArr + s * 2);
          int16_t delta = rds16(deltaArr + s * 2);
          uint16_t ro = rd16(rangeArr + s * 2);
          if (start == 0xFFFF) continue;
          for (uint32_t c = start; c <= end && c != 0xFFFF; c++) {
            int gi;
            if (ro == 0) gi = (int)((c + delta) & 0xFFFF);
            else {
              const uint8_t* gaddr = rangeArr + s * 2 + ro + (c - start) * 2;
              if (gaddr + 2 > (const uint8_t*)(d_.data() + d_.size())) continue;
              uint16_t g = rd16(gaddr);
              gi = g == 0 ? 0 : (int)((g + delta) & 0xFFFF);
            }
            if (gi > 0) cmap_[c] = gi;
          }
        }
      } else if (fmt == 12) {
        uint32_t ngroups = rd32(sub + 12);
        for (uint32_t g = 0; g < ngroups; g++) {
          const uint8_t* rec = sub + 16 + g * 12;
          uint32_t sc = rd32(rec), ec = rd32(rec + 4), sg = rd32(rec + 8);
          for (uint32_t c = sc; c <= ec && c < 0x110000 && (c - sc) < 200000; c++) cmap_[c] = (int)(sg + (c - sc));
        }
      }
    }
  }
  // kern (format 0)
  const uint8_t* kn = tbl("kern");
  if (kn) {
    uint16_t ntab = rd16(kn + 2);
    uint32_t off = 4;
    for (uint16_t t = 0; t < ntab; t++) {
      uint16_t version = rd16(kn + off), length = rd16(kn + off + 2), coverage = rd16(kn + off + 4);
      if ((version >> 8) == 0 && (coverage & 0xFF) == 0) {
        uint16_t npairs = rd16(kn + off + 6);
        for (uint16_t i = 0; i < npairs; i++) {
          const uint8_t* pr = kn + off + 14 + i * 6;
          uint16_t l = rd16(pr), r = rd16(pr + 2);
          kern_[((uint64_t)l << 16) | r] = rds16(pr + 4);
        }
      }
      off += length ? length : 14;
      if (off >= d_.size()) break;
    }
  }
  // nazwa
  const uint8_t* nm = tbl("name");
  if (nm) {
    uint16_t count = rd16(nm + 2), strOff = rd16(nm + 4);
    for (uint16_t i = 0; i < count; i++) {
      const uint8_t* rec = nm + 6 + i * 12;
      uint16_t pid = rd16(rec), nid = rd16(rec + 6), len = rd16(rec + 8), off2 = rd16(rec + 10);
      if (nid == 4 && pid == 3) {
        const uint8_t* s = nm + strOff + off2;
        std::string out;
        for (uint16_t k = 0; k + 1 < len; k += 2) out += utf8From(rd16(s + k));
        name_ = out;
        break;
      }
    }
  }
  if (name_.empty()) name_ = "font";
  return upem_ > 0;
}

// pomocnik — poza klasą, żeby uniknąć zagnieżdżonej lambdy

int Font::glyphIndex(uint32_t cp) const {
  auto it = cmap_.find(cp);
  return it == cmap_.end() ? 0 : it->second;
}
double Font::advance(int gi) const {
  if (hmtx_.empty()) return upem_ * 0.5;
  if (gi < 0) gi = 0;
  if ((size_t)gi < hmtx_.size()) return hmtx_[gi].first;
  return hmtx_.back().first;
}
double Font::kern(int a, int b) const {
  auto it = kern_.find(((uint64_t)(uint16_t)a << 16) | (uint16_t)b);
  return it == kern_.end() ? 0 : it->second;
}

namespace {
struct GlyfReader {
  const uint8_t* base;
  uint32_t len;
  std::vector<Vec2> pts;
  std::vector<uint8_t> flags;
  bool ok = true;
  void readContour(int32_t n) {
    std::vector<uint8_t> f;
    std::vector<Vec2> quiet;
    int cx = 0, cy = 0;
    for (int i = 0; i < n; i++) {
      if ((size_t)(base ? 0 : 0) + 0) {}
      uint8_t fl = *base++;
      uint8_t rep = 1;
      if (fl & 8) { rep = (uint8_t)(*base++ + 1); }
      flags.insert(flags.end(), rep, fl);
      for (int r = 0; r < rep; r++) {
        int dx = 0, dy = 0;
        if (fl & 2) { uint8_t v = *base++; dx = (fl & 16) ? v : -v; }
        else if (!(fl & 16)) { dx = (int16_t)((base[0] << 8) | base[1]); base += 2; }
        if (fl & 4) { uint8_t v = *base++; dy = (fl & 32) ? v : -v; }
        else if (!(fl & 32)) { dy = (int16_t)((base[0] << 8) | base[1]); base += 2; }
        cx += dx; cy += dy;
        // jeśli repeat, kolejne punkty nie powtarzają przesunięć (spec: repeat obejmuje flagę,
        // ale dx/dy są per punkt) — obsługujemy to przez pętlę z jedną flagą na punkt
        quiet.push_back(Vec2(cx, cy));
      }
    }
    pts = quiet;
  }
};
}  // namespace

// Prosta, poprawna implementacja czytania glifów (simple + composite)
struct GlyphOutlineBuilder {
  const uint8_t* glyf = nullptr;
  uint32_t glyfLen = 0;
  static void emitContour(const std::vector<Vec2>& pts, const std::vector<uint8_t>& onCurve,
                          bool contoursClosed, Path& out) {
    if (pts.empty()) return;
    size_t n = pts.size();
    // znajdź punkt startowy (on-curve lub interpolacja)
    size_t start = 0;
    bool found = false;
    for (size_t i = 0; i < n; i++) if (onCurve[i]) { start = i; found = true; break; }
    auto pt = [&](size_t i) { return pts[i % n]; };
    auto isOn = [&](size_t i) { return onCurve[i % n] != 0; };
    if (!found) {
      Vec2 mid = (pt(0) + pt(1)) / 2.0;
      out.moveTo(mid);
      start = 1;
    } else {
      out.moveTo(pt(start));
    }
    size_t i = start;
    size_t count = 0;
    while (count < n + 2) {
      i = (i + 1) % n;
      count++;
      if (i == start && count > 1) break;
      if (isOn(i)) {
        out.lineTo(pt(i));
      } else {
        Vec2 c = pt(i);
        size_t j = (i + 1) % n;
        if (isOn(j)) out.quadTo(c, pt(j));
        else out.quadTo(c, (pt(j) + pt(i)) / 2.0);
        i = j;
        count++;
        if (i == start) break;
      }
    }
    if (contoursClosed) out.close();
  }
};

Path Font::glyphPath(int gi) const {
  Path out;
  const uint8_t* glyf = tbl("glyf");
  uint32_t glyfLen = 0;
  glyf = tbl("glyf", &glyfLen);
  if (!glyf || gi < 0 || (size_t)gi + 1 >= loca_.size()) return out;
  uint32_t off = loca_[gi], end = loca_[gi + 1];
  if (off >= end || end > glyfLen) return out;
  const uint8_t* g = glyf + off;
  int16_t nc = rds16(g);
  if (nc >= 0) {
    // simple glyph
    const uint8_t* p = g + 10;
    std::vector<uint16_t> endPts;
    for (int i = 0; i < nc; i++) { endPts.push_back(rd16(p)); p += 2; }
    uint16_t iLen = rd16(p);
    p += 2 + iLen;
    int numPts = nc ? endPts.back() + 1 : 0;
    std::vector<uint8_t> flags;
    flags.reserve(numPts);
    while ((int)flags.size() < numPts) {
      uint8_t fl = *p++;
      flags.push_back(fl);
      if (fl & 8) {
        uint8_t rep = *p++;
        for (int k = 0; k < rep; k++) flags.push_back(fl);
      }
    }
    // UWAGA: w formacie TrueType wszystkie delty X są zapisane przed deltami Y
    std::vector<int> dxs(numPts, 0), dys(numPts, 0);
    for (int i = 0; i < numPts; i++) {
      uint8_t fl = flags[i];
      if (fl & 2) { uint8_t v = *p++; dxs[i] = (fl & 16) ? (int)v : -(int)v; }
      else if (!(fl & 16)) { dxs[i] = rds16(p); p += 2; }
    }
    for (int i = 0; i < numPts; i++) {
      uint8_t fl = flags[i];
      if (fl & 4) { uint8_t v = *p++; dys[i] = (fl & 32) ? (int)v : -(int)v; }
      else if (!(fl & 32)) { dys[i] = rds16(p); p += 2; }
    }
    std::vector<Vec2> pts;
    pts.reserve(numPts);
    int cx = 0, cy = 0;
    for (int i = 0; i < numPts; i++) {
      cx += dxs[i]; cy += dys[i];
      pts.push_back(Vec2(cx, cy));
    }
    std::vector<uint8_t> on;
    for (int i = 0; i < numPts; i++) on.push_back((flags[i] & 1) ? 1 : 0);
    size_t startIdx = 0;
    for (int ci = 0; ci < nc; ci++) {
      size_t e = endPts[ci];
      if (e < startIdx || e >= (size_t)numPts) { startIdx = e + 1; continue; }
      std::vector<Vec2> cp(pts.begin() + startIdx, pts.begin() + e + 1);
      std::vector<uint8_t> co(on.begin() + startIdx, on.begin() + e + 1);
      GlyphOutlineBuilder::emitContour(cp, co, true, out);
      startIdx = e + 1;
    }
  } else {
    // composite glyph
    const uint8_t* p = g + 10;
    while (true) {
      uint16_t flags = rd16(p);
      uint16_t gi2 = rd16(p + 2);
      p += 4;
      double arg1, arg2;
      if (flags & 1) {
        if (flags & 2) { arg1 = rds16(p); arg2 = rds16(p + 2); p += 4; }
        else { arg1 = (double)rd16(p); arg2 = (double)rd16(p + 2); p += 4; }
      } else {
        if (flags & 2) { arg1 = (int8_t)p[0]; arg2 = (int8_t)p[1]; p += 2; }
        else { arg1 = p[0]; arg2 = p[1]; p += 2; }
      }
      Transform2 t;
      if (flags & 8) { double s = rds16(p) / 16384.0; p += 2; t = {s, 0, 0, s, 0, 0}; }
      else if (flags & 0x40) { double sx = rds16(p) / 16384.0, sy = rds16(p + 2) / 16384.0; p += 4; t = {sx, 0, 0, sy, 0, 0}; }
      else if (flags & 0x80) {
        double a = rds16(p) / 16384.0, b = rds16(p + 2) / 16384.0;
        double c = rds16(p + 4) / 16384.0, d = rds16(p + 6) / 16384.0;
        p += 8;
        t = {a, b, c, d, 0, 0};
      }
      // ARGS_ARE_XY_VALUES = 0x2 → przesunięcie
      if (flags & 2) { t.e = arg1; t.f = arg2; }
      Path sub = glyphPath(gi2);
      sub.apply(t);
      out.addPath(sub);
      if (!(flags & 0x20)) break;
    }
  }
  return out;
}

double Font::measure(const std::string& text, double sizeMm, double trackingMm) const {
  double s = sizeMm / upem_;
  double w = 0;
  int prev = -1;
  auto cps = utf8Decode(text);
  for (size_t i = 0; i < cps.size(); i++) {
    int gi = glyphIndex(cps[i]);
    if (prev >= 0) w += kern(prev, gi) * s;
    w += advance(gi) * s;
    if (i + 1 < cps.size()) w += trackingMm;
    prev = gi;
  }
  return w;
}

Path Font::layout(const std::string& text, double sizeMm, double trackingMm, Vec2 origin) const {
  double s = sizeMm / upem_;
  double x = origin.x;
  int prev = -1;
  Path out;
  auto cps = utf8Decode(text);
  for (size_t i = 0; i < cps.size(); i++) {
    int gi = glyphIndex(cps[i]);
    if (gi == 0 && cps[i] != ' ') {
      // brak glifu — spróbuj bez diakrytyku
      std::string sng = utf8From(cps[i]);
      std::string base = stripDiacritics(sng);
      if (base != sng && !base.empty()) gi = glyphIndex(utf8Decode(base)[0]);
    }
    if (prev >= 0) x += kern(prev, gi) * s;
    if (gi > 0 && cps[i] != ' ') {
      Path gp = glyphPath(gi);
      // y w fontcie rośnie w górę → odwracamy, skalujemy do mm
      gp.apply(Transform2{s, 0, 0, -s, x, origin.y});
      out.addPath(gp);
    }
    x += advance(gi) * s;
    if (i + 1 < cps.size()) x += trackingMm;
    prev = gi;
  }
  return out;
}

Rect Font::glyphBoundsMm(const std::string& text, double sizeMm, double trackingMm) const {
  Path p = layout(text, sizeMm, trackingMm, {0, 0});
  return p.bounds();
}

Font* FontRegistry::get(const std::string& id) {
  auto it = cache_.find(id);
  if (it != cache_.end()) return it->second.get();
  auto f = std::make_unique<Font>();
  std::string path = fileFor(id);
  if (!f->loadFile(path)) {
    // awaryjnie: pierwszy dostępny font
    for (auto& alt : ids()) {
      std::string p2 = fileFor(alt);
      if (p2 != path && f->loadFile(p2)) { path = p2; break; }
    }
  }
  Font* raw = f.get();
  cache_[id] = std::move(f);
  return raw;
}
std::string FontRegistry::fileFor(const std::string& id) const {
  std::string d = dir_.empty() ? "fonts" : dir_;
  if (id == "anton") return d + "/Anton-Regular.ttf";
  if (id == "bebas") return d + "/BebasNeue-Regular.ttf";
  if (id == "cinzel") return d + "/Cinzel.ttf";
  if (id == "cinzel_deco") return d + "/CinzelDecorative-Regular.ttf";
  if (id == "playfair") return d + "/PlayfairDisplay.ttf";
  if (id == "oswald") return d + "/Oswald.ttf";
  if (id == "abril") return d + "/AbrilFatface-Regular.ttf";
  if (id == "pirata") return d + "/PirataOne-Regular.ttf";
  if (id == "barlow") return d + "/BarlowSemiCondensed-Bold.ttf";
  if (id == "barlow_r") return d + "/BarlowCondensed-Regular.ttf";
  return d + "/" + id;
}

}  // namespace lc
