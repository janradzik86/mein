#include "path.h"
#include <cmath>
#include <sstream>

namespace lc {

double Contour::area() const {
  double a = 0;
  for (size_t i = 0, n = pts.size(); i < n; i++) {
    const Vec2& p = pts[i];
    const Vec2& q = pts[(i + 1) % n];
    a += p.cross(q);
  }
  return a * 0.5;
}
bool Contour::contains(const Vec2& q) const { return pointInPolygon(pts, q); }
Rect Contour::bounds() const {
  Rect r;
  if (pts.empty()) return r;
  double x0 = pts[0].x, y0 = pts[0].y, x1 = x0, y1 = y0;
  for (auto& p : pts) { x0 = std::min(x0, p.x); y0 = std::min(y0, p.y); x1 = std::max(x1, p.x); y1 = std::max(y1, p.y); }
  return {x0, y0, x1 - x0, y1 - y0};
}

void Path::addRect(double x, double y, double w, double h) {
  moveTo({x, y}); lineTo({x + w, y}); lineTo({x + w, y + h}); lineTo({x, y + h}); close();
}
void Path::addRoundRect(double x, double y, double w, double h, double r) {
  r = std::min(r, std::min(w, h) / 2);
  if (r <= 0.001) { addRect(x, y, w, h); return; }
  const double k = 0.5522847498 * r;
  moveTo({x + r, y});
  lineTo({x + w - r, y});
  cubicTo({x + w - r + k, y}, {x + w, y + r - k}, {x + w, y + r});
  lineTo({x + w, y + h - r});
  cubicTo({x + w, y + h - r + k}, {x + w - r + k, y + h}, {x + w - r, y + h});
  lineTo({x + r, y + h});
  cubicTo({x + r - k, y + h}, {x, y + h - r + k}, {x, y + h - r});
  lineTo({x, y + r});
  cubicTo({x, y + r - k}, {x + r - k, y}, {x + r, y});
  close();
}
void Path::addCircle(double cx, double cy, double r) { addEllipse(cx, cy, r, r, 0); }
void Path::addEllipse(double cx, double cy, double rx, double ry, double rotDeg) {
  const double k = 0.5522847498;
  double c_ = std::cos(rotDeg * M_PI / 180), s_ = std::sin(rotDeg * M_PI / 180);
  auto P = [&](double x, double y) {
    return Vec2(cx + x * c_ - y * s_, cy + x * s_ + y * c_);
  };
  moveTo(P(rx, 0));
  cubicTo(P(rx, k * ry), P(k * rx, ry), P(0, ry));
  cubicTo(P(-k * rx, ry), P(-rx, k * ry), P(-rx, 0));
  cubicTo(P(-rx, -k * ry), P(-k * rx, -ry), P(0, -ry));
  cubicTo(P(k * rx, -ry), P(rx, -k * ry), P(rx, 0));
  close();
}
void Path::addPolygon(const std::vector<Vec2>& pts, bool closed) {
  if (pts.empty()) return;
  moveTo(pts[0]);
  for (size_t i = 1; i < pts.size(); i++) lineTo(pts[i]);
  if (closed) close();
}
void Path::addStar(double cx, double cy, double r, double rInner, int points, double rotDeg) {
  std::vector<Vec2> pts;
  for (int i = 0; i < points * 2; i++) {
    double a = (rotDeg - 90) * M_PI / 180.0 + i * M_PI / points;
    double rr = (i % 2 == 0) ? r : rInner;
    pts.push_back({cx + rr * std::cos(a), cy + rr * std::sin(a)});
  }
  addPolygon(pts, true);
}
void Path::addArc(double cx, double cy, double r, double a0Deg, double a1Deg) {
  // łuk kołowy aproksymowany krzywymi sześciennymi
  double a0 = a0Deg * M_PI / 180, a1 = a1Deg * M_PI / 180;
  double span = a1 - a0;
  int steps = std::max(1, (int)std::ceil(std::fabs(span) / (M_PI / 2)));
  double d = span / steps;
  Vec2 p0(cx + r * std::cos(a0), cy + r * std::sin(a0));
  moveTo(p0);
  for (int i = 0; i < steps; i++) {
    double b0 = a0 + i * d, b1 = b0 + d;
    double k = 4.0 / 3.0 * std::tan(d / 4.0);
    Vec2 c1(cx + r * (std::cos(b0) - k * std::sin(b0)), cy + r * (std::sin(b0) + k * std::cos(b0)));
    Vec2 c2(cx + r * (std::cos(b1) + k * std::sin(b1)), cy + r * (std::sin(b1) - k * std::cos(b1)));
    Vec2 p1(cx + r * std::cos(b1), cy + r * std::sin(b1));
    cubicTo(c1, c2, p1);
  }
}
void Path::addPath(const Path& o) { segs.insert(segs.end(), o.segs.begin(), o.segs.end()); }

Rect Path::bounds() const {
  Rect r;
  bool first = true;
  Vec2 cur;
  for (auto& s : segs) {
    auto acc = [&](const Vec2& p) {
      if (first) { r = Rect(p.x, p.y, 0, 0); first = false; }
      else {
        double x0 = std::min(r.x, p.x), y0 = std::min(r.y, p.y);
        double x1 = std::max(r.x1(), p.x), y1 = std::max(r.y1(), p.y);
        r = Rect(x0, y0, x1 - x0, y1 - y0);
      }
    };
    switch (s.t) {
      case SEG_M: case SEG_L: cur = s.p[0]; acc(cur); break;
      case SEG_C: {
        // punkty kontrolne + próbki
        for (int i = 1; i <= 12; i++) {
          double t = i / 12.0, mt = 1 - t;
          Vec2 p = cur * (mt * mt * mt) + s.p[0] * (3 * mt * mt * t) + s.p[1] * (3 * mt * t * t) + s.p[2] * (t * t * t);
          acc(p);
        }
        cur = s.p[2];
        break;
      }
      case SEG_Q: {
        for (int i = 1; i <= 10; i++) {
          double t = i / 10.0, mt = 1 - t;
          Vec2 p = cur * (mt * mt) + s.p[0] * (2 * mt * t) + s.p[1] * (t * t);
          acc(p);
        }
        cur = s.p[1];
        break;
      }
      case SEG_Z: break;
    }
  }
  return r;
}
void Path::apply(const Transform2& t) {
  for (auto& s : segs) {
    switch (s.t) {
      case SEG_M: case SEG_L: s.p[0] = t.apply(s.p[0]); break;
      case SEG_C: s.p[0] = t.apply(s.p[0]); s.p[1] = t.apply(s.p[1]); s.p[2] = t.apply(s.p[2]); break;
      case SEG_Q: s.p[0] = t.apply(s.p[0]); s.p[1] = t.apply(s.p[1]); break;
      case SEG_Z: break;
    }
  }
}

std::vector<Contour> Path::flatten(double tol) const {
  std::vector<Contour> out;
  Contour cur;
  Vec2 pos, start;
  bool have = false;
  auto push = [&](const Vec2& p) { cur.pts.push_back(p); };
  auto finish = [&](bool closed) {
    if (cur.pts.size() >= 2) { cur.closed = closed; out.push_back(cur); }
    cur = Contour();
  };
  tol = std::max(0.002, tol);
  for (auto& s : segs) {
    switch (s.t) {
      case SEG_M:
        if (!cur.pts.empty()) finish(false);
        pos = s.p[0]; start = pos; push(pos); have = true;
        break;
      case SEG_L:
        pos = s.p[0]; push(pos);
        break;
      case SEG_C: {
        Vec2 p0 = pos, p1 = s.p[0], p2 = s.p[1], p3 = s.p[2];
        double len = (p1 - p0).len() + (p2 - p1).len() + (p3 - p2).len();
        int n = std::clamp((int)std::ceil(len / std::max(0.05, tol * 2.0)), 4, 400);
        for (int i = 1; i <= n; i++) {
          double t = (double)i / n, mt = 1 - t;
          Vec2 p = p0 * (mt * mt * mt) + p1 * (3 * mt * mt * t) + p2 * (3 * mt * t * t) + p3 * (t * t * t);
          push(p);
        }
        pos = p3;
        break;
      }
      case SEG_Q: {
        Vec2 p0 = pos, p1 = s.p[0], p2 = s.p[1];
        double len = (p1 - p0).len() + (p2 - p1).len();
        int n = std::clamp((int)std::ceil(len / std::max(0.05, tol * 2.0)), 4, 300);
        for (int i = 1; i <= n; i++) {
          double t = (double)i / n, mt = 1 - t;
          push(p0 * (mt * mt) + p1 * (2 * mt * t) + p2 * (t * t));
        }
        pos = p2;
        break;
      }
      case SEG_Z:
        if (!cur.pts.empty()) {
          if ((cur.pts.back() - cur.pts.front()).len() < 1e-6) cur.pts.pop_back();
          finish(true);
        }
        pos = start;
        break;
    }
  }
  if (!cur.pts.empty()) finish(false);
  return out;
}

double Path::totalLength() const {
  double L = 0;
  auto cs = flatten(0.1);
  for (auto& c : cs)
    for (size_t i = 1; i < c.pts.size(); i++) L += (c.pts[i] - c.pts[i - 1]).len();
  return L;
}
int Path::countClosedContours() const {
  int n = 0;
  for (auto& c : flatten(0.2)) if (c.closed && c.pts.size() > 2) n++;
  return n;
}

// ---------------- SVG path danych ----------------
namespace {
struct NumScanner {
  const std::string& s;
  size_t i = 0;
  explicit NumScanner(const std::string& t) : s(t) {}
  void skip() { while (i < s.size() && (isspace((unsigned char)s[i]) || s[i] == ',')) i++; }
  bool eof() { skip(); return i >= s.size(); }
  bool num(double& v) {
    skip();
    if (i >= s.size()) return false;
    size_t st = i;
    if (s[i] == '+' || s[i] == '-') i++;
    bool digits = false;
    while (i < s.size() && isdigit((unsigned char)s[i])) { i++; digits = true; }
    if (i < s.size() && s[i] == '.') { i++; while (i < s.size() && isdigit((unsigned char)s[i])) { i++; digits = true; } }
    if (digits && i < s.size() && (s[i] == 'e' || s[i] == 'E')) {
      i++;
      if (i < s.size() && (s[i] == '+' || s[i] == '-')) i++;
      while (i < s.size() && isdigit((unsigned char)s[i])) i++;
    }
    if (!digits) return false;
    v = std::atof(s.substr(st, i - st).c_str());
    return true;
  }
  char cmd() {
    skip();
    if (i < s.size() && isalpha((unsigned char)s[i])) return s[i++];
    return 0;
  }
  char peekCmd() { skip(); if (i < s.size() && isalpha((unsigned char)s[i])) return s[i]; return 0; }
};
}  // namespace

Path Path::fromSVG(const std::string& d) {
  Path p;
  NumScanner sc(d);
  char prevCmd = 0;
  Vec2 pos, start, lastCtrl;
  bool lastWasCubic = false, lastWasQuad = false;
  while (!sc.eof()) {
    char c = sc.cmd();
    if (!c) break;
    bool rel = islower((unsigned char)c);
    char C = (char)toupper((unsigned char)c);
    auto get = [&](double& v) { return sc.num(v); };
    auto P = [&](double x, double y) { return rel ? Vec2(pos.x + x, pos.y + y) : Vec2(x, y); };
    if (C == 'M') {
      double x, y;
      if (!get(x) || !get(y)) break;
      pos = P(x, y); start = pos; p.moveTo(pos);
      while (true) {
        sc.skip();
        if (sc.peekCmd()) break;
        double x2, y2;
        if (!get(x2) || !get(y2)) break;
        Vec2 q = P(x2, y2);
        p.lineTo(q); pos = q;
      }
      lastWasCubic = lastWasQuad = false;
    } else if (C == 'L') {
      double x, y;
      while (get(x) && get(y)) { Vec2 q = P(x, y); p.lineTo(q); pos = q; sc.skip(); if (sc.peekCmd()) break; }
      lastWasCubic = lastWasQuad = false;
    } else if (C == 'H') {
      double x;
      while (get(x)) { Vec2 q(rel ? pos.x + x : x, pos.y); p.lineTo(q); pos = q; sc.skip(); if (sc.peekCmd()) break; }
      lastWasCubic = lastWasQuad = false;
    } else if (C == 'V') {
      double y;
      while (get(y)) { Vec2 q(pos.x, rel ? pos.y + y : y); p.lineTo(q); pos = q; sc.skip(); if (sc.peekCmd()) break; }
      lastWasCubic = lastWasQuad = false;
    } else if (C == 'C') {
      double x1, y1, x2, y2, x, y;
      while (get(x1) && get(y1) && get(x2) && get(y2) && get(x) && get(y)) {
        Vec2 c1 = P(x1, y1), c2 = P(x2, y2), q = P(x, y);
        p.cubicTo(c1, c2, q); lastCtrl = c2; pos = q;
        sc.skip(); if (sc.peekCmd()) break;
      }
      lastWasCubic = true; lastWasQuad = false;
    } else if (C == 'S') {
      double x2, y2, x, y;
      while (get(x2) && get(y2) && get(x) && get(y)) {
        Vec2 c1 = lastWasCubic ? Vec2(2 * pos.x - lastCtrl.x, 2 * pos.y - lastCtrl.y) : pos;
        Vec2 c2 = P(x2, y2), q = P(x, y);
        p.cubicTo(c1, c2, q); lastCtrl = c2; pos = q;
        sc.skip(); if (sc.peekCmd()) break;
      }
      lastWasCubic = true; lastWasQuad = false;
    } else if (C == 'Q') {
      double x1, y1, x, y;
      while (get(x1) && get(y1) && get(x) && get(y)) {
        Vec2 c1 = P(x1, y1), q = P(x, y);
        p.quadTo(c1, q); lastCtrl = c1; pos = q;
        sc.skip(); if (sc.peekCmd()) break;
      }
      lastWasQuad = true; lastWasCubic = false;
    } else if (C == 'T') {
      double x, y;
      while (get(x) && get(y)) {
        Vec2 c1 = lastWasQuad ? Vec2(2 * pos.x - lastCtrl.x, 2 * pos.y - lastCtrl.y) : pos;
        Vec2 q = P(x, y);
        p.quadTo(c1, q); lastCtrl = c1; pos = q;
        sc.skip(); if (sc.peekCmd()) break;
      }
      lastWasQuad = true; lastWasCubic = false;
    } else if (C == 'A') {
      double rx, ry, rot, large, sweep, x, y;
      while (get(rx) && get(ry) && get(rot) && get(large) && get(sweep) && get(x) && get(y)) {
        Vec2 end = P(x, y);
        // konwersja łuku eliptycznego na krzywe (implementacja standardowa)
        double x1 = pos.x, y1 = pos.y, x2 = end.x, y2 = end.y;
        if (std::fabs(rx) < 1e-9 || std::fabs(ry) < 1e-9) { p.lineTo(end); }
        else {
          rx = std::fabs(rx); ry = std::fabs(ry);
          double phi = rot * M_PI / 180.0;
          double cosP = std::cos(phi), sinP = std::sin(phi);
          double dx = (x1 - x2) / 2, dy = (y1 - y2) / 2;
          double x1p = cosP * dx + sinP * dy, y1p = -sinP * dx + cosP * dy;
          double lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry);
          if (lam > 1) { double s = std::sqrt(lam); rx *= s; ry *= s; }
          double sign = (large > 0) != (sweep > 0) ? 1 : -1;
          double num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p;
          double den = rx * rx * y1p * y1p + ry * ry * x1p * x1p;
          double co = den < 1e-12 ? 0 : sign * std::sqrt(std::max(0.0, num / den));
          double cxp = co * rx * y1p / ry, cyp = -co * ry * x1p / rx;
          double cx = cosP * cxp - sinP * cyp + (x1 + x2) / 2;
          double cy = sinP * cxp + cosP * cyp + (y1 + y2) / 2;
          auto ang = [&](double ux, double uy, double vx, double vy) {
            double dot = ux * vx + uy * vy, l = std::sqrt(ux * ux + uy * uy) * std::sqrt(vx * vx + vy * vy);
            double a = std::acos(std::clamp(dot / std::max(1e-12, l), -1.0, 1.0));
            return (ux * vy - uy * vx < 0) ? -a : a;
          };
          double th1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
          double dth = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry);
          if (sweep <= 0 && dth > 0) dth -= 2 * M_PI;
          if (sweep > 0 && dth < 0) dth += 2 * M_PI;
          int n = std::max(1, (int)std::ceil(std::fabs(dth) / (M_PI / 2)));
          double step = dth / n;
          for (int i = 0; i < n; i++) {
            double a0 = th1 + i * step, a1 = a0 + step;
            double k = 4.0 / 3.0 * std::tan(step / 4.0);
            auto pt = [&](double a, double off) {
              double xa = rx * std::cos(a), ya = ry * std::sin(a);
              double xb = -rx * std::sin(a) * k * off, yb = ry * std::cos(a) * k * off;
              return Vec2(cosP * (xa + xb) - sinP * (ya + yb) + cx, sinP * (xa + xb) + cosP * (ya + yb) + cy);
            };
            p.cubicTo(pt(a0, 1), pt(a1, -1), pt(a1, 0));
          }
        }
        pos = end;
        sc.skip(); if (sc.peekCmd()) break;
      }
      lastWasCubic = lastWasQuad = false;
    } else if (C == 'Z') {
      p.close(); pos = start;
      lastWasCubic = lastWasQuad = false;
    } else {
      break;
    }
    prevCmd = c;
    (void)prevCmd;
  }
  return p;
}

std::string Path::toSVG() const {
  std::string out;
  int n = 0;
  for (auto& s : segs) {
    if (n++) out += " ";
    switch (s.t) {
      case SEG_M: out += "M " + fmtDouble(s.p[0].x, 3) + " " + fmtDouble(s.p[0].y, 3); break;
      case SEG_L: out += "L " + fmtDouble(s.p[0].x, 3) + " " + fmtDouble(s.p[0].y, 3); break;
      case SEG_C: out += "C " + fmtDouble(s.p[0].x, 3) + " " + fmtDouble(s.p[0].y, 3) + " " +
                          fmtDouble(s.p[1].x, 3) + " " + fmtDouble(s.p[1].y, 3) + " " +
                          fmtDouble(s.p[2].x, 3) + " " + fmtDouble(s.p[2].y, 3); break;
      case SEG_Q: out += "Q " + fmtDouble(s.p[0].x, 3) + " " + fmtDouble(s.p[0].y, 3) + " " +
                          fmtDouble(s.p[1].x, 3) + " " + fmtDouble(s.p[1].y, 3); break;
      case SEG_Z: out += "Z"; break;
    }
  }
  return out;
}
Path Path::fromPolyline(const std::vector<Vec2>& pts, bool closed) {
  Path p;
  p.addPolygon(pts, closed);
  return p;
}
Path Path::fitInto(const Rect& r, double alignX, double alignY) const {
  Rect b = bounds();
  if (b.w <= 1e-9 || b.h <= 1e-9) return *this;
  double s = std::min(r.w / b.w, r.h / b.h);
  double dx = r.x + (r.w - b.w * s) * alignX - b.x * s;
  double dy = r.y + (r.h - b.h * s) * alignY - b.y * s;
  return transformed(Transform2{s, 0, 0, s, dx, dy});
}
Path Path::scaledBox(double cx, double cy, double s) const {
  return transformed(Transform2{s, 0, 0, s, cx - cx * s, cy - cy * s});
}
Path Path::offsetted(double d) const {
  // odsunięcie wielokątowe (miter/round uproszczone) — wystarczające dla obrysów
  auto cs = flatten(0.1);
  Path out;
  for (auto& c : cs) {
    if (c.pts.size() < 3) continue;
    bool ccw = c.area() > 0;
    double sign = ccw ? 1 : -1;
    std::vector<Vec2> res;
    size_t n = c.pts.size();
    for (size_t i = 0; i < n; i++) {
      Vec2 prev = c.pts[(i + n - 1) % n], cur = c.pts[i], next = c.pts[(i + 1) % n];
      Vec2 e1 = (cur - prev).norm(), e2 = (next - cur).norm();
      Vec2 n1(-e1.y * sign, e1.x * sign), n2(-e2.y * sign, e2.x * sign);
      Vec2 bis = (n1 + n2).norm();
      double denom = std::max(0.35, 1.0 + n1.dot(n2));
      res.push_back(cur + bis * (d / denom));
    }
    out.addPolygon(res, true);
  }
  return out;
}

// ---------------- operacje ----------------
double polygonArea(const std::vector<Vec2>& pts) {
  double a = 0;
  for (size_t i = 0, n = pts.size(); i < n; i++) a += pts[i].cross(pts[(i + 1) % n]);
  return a * 0.5;
}
bool pointInPolygon(const std::vector<Vec2>& poly, const Vec2& p) {
  bool in = false;
  for (size_t i = 0, n = poly.size(); i < n; i++) {
    const Vec2& a = poly[i];
    const Vec2& b = poly[(i + 1) % n];
    if (((a.y > p.y) != (b.y > p.y)) &&
        (p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x))
      in = !in;
  }
  return in;
}
bool pointInContours(const std::vector<Contour>& cs, const Vec2& p, bool evenOdd) {
  if (evenOdd) {
    int cnt = 0;
    for (auto& c : cs) if (pointInPolygon(c.pts, p)) cnt++;
    return cnt % 2 == 1;
  }
  int wind = 0;
  for (auto& c : cs) {
    if (!pointInPolygon(c.pts, p)) continue;
    wind += c.area() > 0 ? 1 : -1;
  }
  return wind != 0;
}
std::vector<Vec2> simplifyPolyline(const std::vector<Vec2>& pts, double tol) {
  if (pts.size() < 3) return pts;
  std::vector<Vec2> out;
  // Douglas–Peucker (iteracyjnie)
  std::vector<char> keep(pts.size(), 0);
  keep.front() = keep.back() = 1;
  std::vector<std::pair<size_t, size_t>> stack{{0, pts.size() - 1}};
  while (!stack.empty()) {
    auto [a, b] = stack.back();
    stack.pop_back();
    double maxd = 0;
    size_t idx = 0;
    Vec2 A = pts[a], B = pts[b];
    Vec2 ab = B - A;
    double ablen = ab.len();
    for (size_t i = a + 1; i < b; i++) {
      double d;
      if (ablen < 1e-12) d = (pts[i] - A).len();
      else d = std::fabs(ab.cross(pts[i] - A)) / ablen;
      if (d > maxd) { maxd = d; idx = i; }
    }
    if (maxd > tol && idx > a) {
      keep[idx] = 1;
      stack.push_back({a, idx});
      stack.push_back({idx, b});
    }
  }
  for (size_t i = 0; i < pts.size(); i++) if (keep[i]) out.push_back(pts[i]);
  return out;
}

std::vector<std::pair<Vec2, Vec2>> hatchContour(const Contour& c, double spacing, double angleDeg,
                                                double inset) {
  std::vector<std::pair<Vec2, Vec2>> lines;
  if (c.pts.size() < 3 || spacing <= 0.01) return lines;
  Rect b = c.bounds();
  double a = angleDeg * M_PI / 180.0;
  Vec2 dir(std::cos(a), std::sin(a)), nrm(-dir.y, dir.x);
  double diag = std::sqrt(b.w * b.w + b.h * b.h);
  Vec2 ctr = {b.cx(), b.cy()};
  for (double t = -diag / 2; t <= diag / 2; t += spacing) {
    Vec2 base = ctr + nrm * t;
    Vec2 p0 = base - dir * diag, p1 = base + dir * diag;
    // przecięcia z konturem
    std::vector<double> ts;
    size_t n = c.pts.size();
    for (size_t i = 0; i < n; i++) {
      Vec2 A = c.pts[i], B = c.pts[(i + 1) % n];
      Vec2 r = B - A, s = dir;
      double den = r.cross(s);
      if (std::fabs(den) < 1e-12) continue;
      Vec2 qp = A - p0;
      // rozwiązanie p0 + t*s = A + u*r  →  t = (qp×r)/(s×r), u = (qp×s)/(r×s)
      double u = -qp.cross(s) / den;
      double t = -qp.cross(r) / den;
      if (u >= 0 && u <= 1 && t >= 0 && t <= 2 * diag) ts.push_back(t);
    }
    if (ts.size() < 2) continue;
    std::sort(ts.begin(), ts.end());
    for (size_t i = 0; i + 1 < ts.size(); i += 2) {
      double v0 = ts[i] + (inset > 0 ? inset : 0);
      double v1 = ts[i + 1] - (inset > 0 ? inset : 0);
      if (v1 - v0 < 0.05) continue;
      lines.push_back({base + dir * (v0 - diag), base + dir * (v1 - diag)});
    }
  }
  return lines;
}

std::string colorForLayer(const std::string& layer) {
  if (layer == "CUT_OUTER") return "#FF0000";
  if (layer == "CUT_INNER") return "#00AAFF";
  if (layer == "ENGRAVE_VECTOR") return "#000000";
  if (layer == "ENGRAVE_FILL") return "#000000";
  if (layer == "ENGRAVE_RASTER") return "#808080";
  if (layer == "SCORE") return "#FF00FF";
  if (layer == "GUIDES") return "#00FF88";
  return "#000000";
}

}  // namespace lc
