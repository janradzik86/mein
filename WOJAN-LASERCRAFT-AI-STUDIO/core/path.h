// Geometria wektorowa: ścieżki SVG-owe, flattening, kształty, kreskowanie
#pragma once
#include "common.h"

namespace lc {

enum SegType { SEG_M, SEG_L, SEG_C, SEG_Q, SEG_Z };

struct Seg {
  SegType t;
  Vec2 p[3];
  Seg(SegType t_, Vec2 a = {}, Vec2 b = {}, Vec2 c = {}) : t(t_) { p[0] = a; p[1] = b; p[2] = c; }
};

struct Contour {
  std::vector<Vec2> pts;
  bool closed = true;
  double area() const;
  bool contains(const Vec2& q) const;
  Rect bounds() const;
};

struct Path {
  std::vector<Seg> segs;
  bool empty() const { return segs.empty(); }
  void moveTo(Vec2 p) { segs.emplace_back(SEG_M, p); }
  void lineTo(Vec2 p) { segs.emplace_back(SEG_L, p); }
  void cubicTo(Vec2 c1, Vec2 c2, Vec2 p) { segs.emplace_back(SEG_C, c1, c2, p); }
  void quadTo(Vec2 c, Vec2 p) { segs.emplace_back(SEG_Q, c, p); }
  void close() { segs.emplace_back(SEG_Z); }
  void addRect(double x, double y, double w, double h);
  void addRoundRect(double x, double y, double w, double h, double r);
  void addCircle(double cx, double cy, double r);
  void addEllipse(double cx, double cy, double rx, double ry, double rotDeg = 0);
  void addPolygon(const std::vector<Vec2>& pts, bool closed = true);
  void addStar(double cx, double cy, double r, double rInner, int points, double rotDeg = 0);
  void addLine(Vec2 a, Vec2 b) { moveTo(a); lineTo(b); }
  void addArc(double cx, double cy, double r, double a0Deg, double a1Deg);
  void addPath(const Path& o);
  Rect bounds() const;
  Transform2 boundsTransform() const { return {}; }
  void apply(const Transform2& t);
  void apply(double sx, double sy, double dx, double dy) { apply(Transform2{sx, 0, 0, sy, dx, dy}); }
  Path transformed(const Transform2& t) const { Path p = *this; p.apply(t); return p; }

  std::vector<Contour> flatten(double tol = 0.05) const;   // kontury (flattening Béziera)
  double totalLength() const;
  int countClosedContours() const;

  std::string toSVG() const;                 // tylko "d"
  static Path fromSVG(const std::string& d);
  static Path fromPolyline(const std::vector<Vec2>& pts, bool closed);
  // dopasowanie ścieżki do ramki (skalowanie z zachowaniem proporcji, wyrównanie 0..1)
  Path fitInto(const Rect& r, double alignX = 0.5, double alignY = 0.5) const;
  Path scaledBox(double cx, double cy, double s) const;
  Path offsetted(double d) const;  // przybliżone odsunięcie (dla obrysu)
};

std::vector<std::pair<Vec2, Vec2>> hatchContour(const Contour& c, double spacing, double angleDeg,
                                                double inset = 0.0);
std::vector<Vec2> simplifyPolyline(const std::vector<Vec2>& pts, double tol);
double polygonArea(const std::vector<Vec2>& pts);
bool pointInPolygon(const std::vector<Vec2>& poly, const Vec2& p);
bool pointInContours(const std::vector<Contour>& cs, const Vec2& p, bool evenOdd = false);
std::string colorForLayer(const std::string& layer);

}  // namespace lc
