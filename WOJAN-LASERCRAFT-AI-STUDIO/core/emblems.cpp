#include "emblems.h"
#include <cmath>
#include <algorithm>
#include <map>

namespace lc {

// ---------------- narzędzia ----------------
static Path polyPath(const std::vector<Vec2>& pts, bool closed = true) {
  Path p;
  p.addPolygon(pts, closed);
  return p;
}
static Path circlePath(Vec2 c, double r) {
  Path p;
  p.addCircle(c.x, c.y, r);
  return p;
}
static Path ringPath(Vec2 c, double r0, double r1) {
  Path p;
  p.addCircle(c.x, c.y, r1);
  Path inner;
  inner.addCircle(c.x, c.y, r0);
  p.addPath(inner);
  return p;
}
static Path ellipsePath(Vec2 c, double rx, double ry, double rot = 0) {
  Path p;
  p.addEllipse(c.x, c.y, rx, ry, rot);
  return p;
}
// krzywa gładka przez punkty (Catmull-Rom → Bézier)
static Path smoothPath(const std::vector<Vec2>& pts, bool closed) {
  Path p;
  if (pts.size() < 2) return p;
  size_t n = pts.size();
  p.moveTo(pts[0]);
  for (size_t i = 0; i < (closed ? n : n - 1); i++) {
    Vec2 p0 = pts[(i + n - 1) % n], p1 = pts[i % n], p2 = pts[(i + 1) % n], p3 = pts[(i + 2) % n];
    if (!closed) {
      p0 = pts[i == 0 ? 0 : i - 1];
      p3 = pts[std::min(n - 1, i + 2)];
    }
    Vec2 c1 = p1 + (p2 - p0) / 6.0;
    Vec2 c2 = p2 - (p3 - p1) / 6.0;
    p.cubicTo(c1, c2, p2);
  }
  if (closed) p.close();
  return p;
}

// ---------------- kształty bazowe ----------------
Path fishShape(Vec2 c, double w, double h, bool pike) {
  // Kontur ryby (szczupak = wydłużone ciało, spiczasty pysk, wysoka płetwa grzbietowa)
  double x0 = c.x - w / 2, x1 = c.x + w / 2;
  std::vector<Vec2> body;
  if (pike) {
    body = {{x0, c.y}, {x0 + w * 0.12, c.y - h * 0.28}, {x0 + w * 0.34, c.y - h * 0.42},
            {x0 + w * 0.58, c.y - h * 0.36}, {x0 + w * 0.74, c.y - h * 0.20},
            {x1, c.y - h * 0.48}, {x1 - w * 0.10, c.y},
            {x1, c.y + h * 0.48}, {x0 + w * 0.74, c.y + h * 0.20},
            {x0 + w * 0.58, c.y + h * 0.36}, {x0 + w * 0.34, c.y + h * 0.42},
            {x0 + w * 0.12, c.y + h * 0.28}};
  } else {
    body = {{x0, c.y}, {x0 + w * 0.16, c.y - h * 0.34}, {x0 + w * 0.46, c.y - h * 0.44},
            {x0 + w * 0.70, c.y - h * 0.26}, {x1, c.y - h * 0.50}, {x1 - w * 0.08, c.y},
            {x1, c.y + h * 0.50}, {x0 + w * 0.70, c.y + h * 0.26},
            {x0 + w * 0.46, c.y + h * 0.44}, {x0 + w * 0.16, c.y + h * 0.34}};
  }
  return smoothPath(body, true);
}

Path wolfShape(Vec2 c, double w, double h) {
  // Głowa wilka, widok z przodu: uszy, czoło, policzki, kufa.
  double cx = c.x, cy = c.y;
  std::vector<Vec2> pts = {
      {cx - w * 0.36, cy + h * 0.02}, {cx - w * 0.34, cy - h * 0.18},
      {cx - w * 0.30, cy - h * 0.40}, {cx - w * 0.16, cy - h * 0.24},
      {cx, cy - h * 0.28}, {cx + w * 0.16, cy - h * 0.24},
      {cx + w * 0.30, cy - h * 0.40}, {cx + w * 0.34, cy - h * 0.18},
      {cx + w * 0.36, cy + h * 0.02}, {cx + w * 0.20, cy + h * 0.24},
      {cx, cy + h * 0.44}, {cx - w * 0.20, cy + h * 0.24}};
  return smoothPath(pts, true);
}

Path deerShape(Vec2 c, double w, double h) {
  // jeleń z porożem (sylwetka głowy + poroże)
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  std::vector<Vec2> head = {
      {x0 + w * 0.42, y0 + h * 0.30}, {x0 + w * 0.55, y0 + h * 0.26}, {x0 + w * 0.66, y0 + h * 0.34},
      {x0 + w * 0.72, y0 + h * 0.52}, {x0 + w * 0.64, y0 + h * 0.70}, {x0 + w * 0.52, y0 + h * 0.96},
      {x0 + w * 0.40, y0 + h * 0.74}, {x0 + w * 0.34, y0 + h * 0.56}};
  p = smoothPath(head, true);
  // poroże
  auto antler = [&](double sx, double dir) {
    Path a;
    a.moveTo({x0 + w * (0.46 + sx), y0 + h * 0.30});
    a.cubicTo({x0 + w * (0.44 + sx), y0 + h * 0.18}, {x0 + w * (0.42 + sx * 0.4 * dir), y0 + h * 0.10},
              {x0 + w * (0.40 + sx * 1.4), y0 + h * 0.02});
    Path t1;
    t1.moveTo({x0 + w * (0.44 + sx), y0 + h * 0.18});
    t1.lineTo({x0 + w * (0.30 + sx * 1.2), y0 + h * 0.12});
    Path t2;
    t2.moveTo({x0 + w * (0.43 + sx * 0.6), y0 + h * 0.24});
    t2.lineTo({x0 + w * (0.58 + sx * 1.1), y0 + h * 0.20});
    a.addPath(t1);
    a.addPath(t2);
    return a;
  };
  p.addPath(antler(0.0, 1.0));
  p.addPath(antler(0.12, -1.0));
  return p;
}

Path tractorShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  // koło tylne duże
  p.addPath(ringPath({x0 + w * 0.28, y0 + h * 0.68}, w * 0.17, w * 0.22));
  // koło przednie małe
  p.addPath(ringPath({x0 + w * 0.78, y0 + h * 0.76}, w * 0.09, w * 0.13));
  // maska + kabina
  std::vector<Vec2> body = {{x0 + w * 0.10, y0 + h * 0.42}, {x0 + w * 0.46, y0 + h * 0.36},
                           {x0 + w * 0.52, y0 + h * 0.16}, {x0 + w * 0.74, y0 + h * 0.18},
                           {x0 + w * 0.82, y0 + h * 0.40}, {x0 + w * 0.94, y0 + h * 0.44},
                           {x0 + w * 0.94, y0 + h * 0.64}, {x0 + w * 0.60, y0 + h * 0.64},
                           {x0 + w * 0.46, y0 + h * 0.70}, {x0 + w * 0.10, y0 + h * 0.66}};
  p.addPath(smoothPath(body, true));
  return p;
}

Path carShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  std::vector<Vec2> body = {{x0 + w * 0.04, y0 + h * 0.62}, {x0 + w * 0.10, y0 + h * 0.44},
                            {x0 + w * 0.26, y0 + h * 0.40}, {x0 + w * 0.38, y0 + h * 0.20},
                            {x0 + w * 0.64, y0 + h * 0.18}, {x0 + w * 0.76, y0 + h * 0.38},
                            {x0 + w * 0.92, y0 + h * 0.46}, {x0 + w * 0.96, y0 + h * 0.62}};
  p = smoothPath(body, false);
  p.lineTo({x0 + w * 0.04, y0 + h * 0.62});
  p.close();
  p.addPath(ringPath({x0 + w * 0.26, y0 + h * 0.68}, w * 0.05, w * 0.10));
  p.addPath(ringPath({x0 + w * 0.74, y0 + h * 0.68}, w * 0.05, w * 0.10));
  // szyba
  Path win;
  win.addRoundRect(x0 + w * 0.42, y0 + h * 0.26, w * 0.20, h * 0.12, 2);
  p.addPath(win);
  return p;
}

Path guitarShape(Vec2 c, double w, double h) {
  Path p;
  // korpus (ósemka) + gryf
  double cx = c.x;
  Path body;
  body.addEllipse(cx, c.y + h * 0.18, w * 0.34, h * 0.26);
  Path upper;
  upper.addEllipse(cx, c.y - h * 0.14, w * 0.26, h * 0.20);
  body.addPath(upper);
  p.addPath(body);
  Path neck;
  neck.addRoundRect(cx - w * 0.055, c.y - h * 0.48, w * 0.11, h * 0.40, 1.5);
  p.addPath(neck);
  Path head;
  head.addRoundRect(cx - w * 0.09, c.y - h * 0.56, w * 0.18, h * 0.10, 2);
  p.addPath(head);
  // otwór rezonansowy
  p.addPath(circlePath({cx, c.y + h * 0.16}, w * 0.09));
  return p;
}

Path gamepadShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  std::vector<Vec2> body = {{x0 + w * 0.06, y0 + h * 0.52}, {x0 + w * 0.14, y0 + h * 0.26},
                            {x0 + w * 0.36, y0 + h * 0.20}, {x0 + w * 0.64, y0 + h * 0.20},
                            {x0 + w * 0.86, y0 + h * 0.26}, {x0 + w * 0.94, y0 + h * 0.52},
                            {x0 + w * 0.88, y0 + h * 0.82}, {x0 + w * 0.68, y0 + h * 0.88},
                            {x0 + w * 0.50, y0 + h * 0.72}, {x0 + w * 0.32, y0 + h * 0.88},
                            {x0 + w * 0.12, y0 + h * 0.82}};
  p = smoothPath(body, true);
  p.addPath(circlePath({x0 + w * 0.30, y0 + h * 0.50}, w * 0.06));
  Path dpadH, dpadV;
  dpadH.addRect(x0 + w * 0.58, y0 + h * 0.46, w * 0.14, h * 0.09);
  dpadV.addRect(x0 + w * 0.605, y0 + h * 0.36, w * 0.07, h * 0.28);
  p.addPath(dpadH);
  p.addPath(dpadV);
  return p;
}

Path mugShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  p.addRoundRect(x0 + w * 0.10, y0 + h * 0.16, w * 0.62, h * 0.68, 3);
  Path handle;
  handle.addArc(x0 + w * 0.72, y0 + h * 0.50, w * 0.20, -80, 80);
  handle.moveTo({x0 + w * 0.72, y0 + h * 0.30});
  handle.addArc(x0 + w * 0.72, y0 + h * 0.50, w * 0.13, -80, 80);
  p.addPath(handle);
  // para
  Path steam;
  steam.moveTo({x0 + w * 0.34, y0 + h * 0.12});
  steam.cubicTo({x0 + w * 0.24, y0 + h * 0.02}, {x0 + w * 0.44, y0 - h * 0.04}, {x0 + w * 0.34, y0 - h * 0.14});
  steam.moveTo({x0 + w * 0.54, y0 + h * 0.12});
  steam.cubicTo({x0 + w * 0.44, y0 + h * 0.00}, {x0 + w * 0.64, y0 - h * 0.06}, {x0 + w * 0.54, y0 - h * 0.16});
  p.addPath(steam);
  return p;
}

Path skullShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  std::vector<Vec2> cranium = {{x0 + w * 0.16, y0 + h * 0.54}, {x0 + w * 0.22, y0 + h * 0.20},
                               {x0 + w * 0.50, y0 + h * 0.06}, {x0 + w * 0.78, y0 + h * 0.20},
                               {x0 + w * 0.84, y0 + h * 0.54}, {x0 + w * 0.76, y0 + h * 0.72},
                               {x0 + w * 0.66, y0 + h * 0.78}, {x0 + w * 0.64, y0 + h * 0.94},
                               {x0 + w * 0.36, y0 + h * 0.94}, {x0 + w * 0.34, y0 + h * 0.78},
                               {x0 + w * 0.24, y0 + h * 0.72}};
  p = smoothPath(cranium, true);
  // oczodoły
  p.addPath(ellipsePath({x0 + w * 0.36, y0 + h * 0.50}, w * 0.11, h * 0.12));
  p.addPath(ellipsePath({x0 + w * 0.64, y0 + h * 0.50}, w * 0.11, h * 0.12));
  // nos + zęby
  Path nose;
  nose.addPolygon({{x0 + w * 0.50, y0 + h * 0.60}, {x0 + w * 0.44, y0 + h * 0.70}, {x0 + w * 0.56, y0 + h * 0.70}}, true);
  p.addPath(nose);
  for (int i = 0; i < 4; i++) {
    Path t;
    t.addRect(x0 + w * (0.40 + i * 0.06), y0 + h * 0.80, w * 0.035, h * 0.10);
    p.addPath(t);
  }
  return p;
}

Path roseShape(Vec2 c, double w, double h) {
  Path p;
  for (int ring = 0; ring < 3; ring++) {
    int petals = 5 + ring;
    double rx = w * (0.46 - ring * 0.12), ry = h * (0.46 - ring * 0.12);
    for (int i = 0; i < petals; i++) {
      double a = 2 * M_PI * i / petals + ring * 0.4;
      Vec2 pc(c.x + std::cos(a) * rx * 0.55, c.y + std::sin(a) * ry * 0.55);
      Path pt;
      pt.addEllipse(pc.x, pc.y, rx * 0.42, ry * 0.30, a * 180 / M_PI);
      p.addPath(pt);
    }
  }
  p.addPath(circlePath(c, std::min(w, h) * 0.10));
  return p;
}

Path treeShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  // pień
  Path trunk;
  trunk.addPolygon({{x0 + w * 0.45, y0 + h * 0.96}, {x0 + w * 0.55, y0 + h * 0.96},
                    {x0 + w * 0.53, y0 + h * 0.55}, {x0 + w * 0.47, y0 + h * 0.55}}, true);
  p.addPath(trunk);
  // korona z kilku okręgów
  for (int i = 0; i < 6; i++) {
    double a = 2 * M_PI * i / 6;
    p.addPath(circlePath({c.x + std::cos(a) * w * 0.18, y0 + h * 0.36 + std::sin(a) * h * 0.14}, w * 0.20));
  }
  p.addPath(circlePath({c.x, y0 + h * 0.30}, w * 0.24));
  return p;
}

Path mountainsShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  p.addPolygon({{x0 + w * 0.04, y0 + h * 0.82}, {x0 + w * 0.30, y0 + h * 0.20},
                {x0 + w * 0.46, y0 + h * 0.55}, {x0 + w * 0.62, y0 + h * 0.12},
                {x0 + w * 0.96, y0 + h * 0.82}}, true);
  // śnieg na szczytach
  p.addPolygon({{x0 + w * 0.30, y0 + h * 0.20}, {x0 + w * 0.38, y0 + h * 0.40},
                {x0 + w * 0.30, y0 + h * 0.36}, {x0 + w * 0.24, y0 + h * 0.42}}, true);
  p.addPolygon({{x0 + w * 0.62, y0 + h * 0.12}, {x0 + w * 0.70, y0 + h * 0.36},
                {x0 + w * 0.62, y0 + h * 0.32}, {x0 + w * 0.55, y0 + h * 0.38}}, true);
  return p;
}

Path crownShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  p.addPolygon({{x0 + w * 0.06, y0 + h * 0.72}, {x0 + w * 0.10, y0 + h * 0.24},
                {x0 + w * 0.30, y0 + h * 0.50}, {x0 + w * 0.50, y0 + h * 0.14},
                {x0 + w * 0.70, y0 + h * 0.50}, {x0 + w * 0.90, y0 + h * 0.24},
                {x0 + w * 0.94, y0 + h * 0.72}}, true);
  p.addPath(circlePath({x0 + w * 0.50, y0 + h * 0.30}, w * 0.05));
  return p;
}

Path eagleShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  // skrzydła
  p.addPath(smoothPath({{x0 + w * 0.50, y0 + h * 0.36}, {x0 + w * 0.30, y0 + h * 0.28},
                        {x0 + w * 0.10, y0 + h * 0.36}, {x0 + w * 0.02, y0 + h * 0.56},
                        {x0 + w * 0.16, y0 + h * 0.52}, {x0 + w * 0.10, y0 + h * 0.68},
                        {x0 + w * 0.26, y0 + h * 0.60}, {x0 + w * 0.24, y0 + h * 0.76},
                        {x0 + w * 0.40, y0 + h * 0.62}, {x0 + w * 0.50, y0 + h * 0.70}}, true));
  p.addPath(smoothPath({{x0 + w * 0.50, y0 + h * 0.36}, {x0 + w * 0.70, y0 + h * 0.28},
                        {x0 + w * 0.90, y0 + h * 0.36}, {x0 + w * 0.98, y0 + h * 0.56},
                        {x0 + w * 0.84, y0 + h * 0.52}, {x0 + w * 0.90, y0 + h * 0.68},
                        {x0 + w * 0.74, y0 + h * 0.60}, {x0 + w * 0.76, y0 + h * 0.76},
                        {x0 + w * 0.60, y0 + h * 0.62}, {x0 + w * 0.50, y0 + h * 0.70}}, true));
  // głowa + dziób
  p.addPath(smoothPath({{x0 + w * 0.44, y0 + h * 0.34}, {x0 + w * 0.50, y0 + h * 0.10},
                        {x0 + w * 0.58, y0 + h * 0.34}, {x0 + w * 0.54, y0 + h * 0.42},
                        {x0 + w * 0.46, y0 + h * 0.42}}, true));
  p.addPath(polyPath({{x0 + w * 0.52, y0 + h * 0.24}, {x0 + w * 0.62, y0 + h * 0.28},
                      {x0 + w * 0.52, y0 + h * 0.32}}, true));
  return p;
}

Path pumpkinShape(Vec2 c, double w, double h) {
  Path p;
  p.addEllipse(c.x, c.y + h * 0.08, w * 0.42, h * 0.34);
  for (int i = -2; i <= 2; i++) {
    if (i == 0) continue;
    Path rib;
    rib.addEllipse(c.x + i * w * 0.12, c.y + h * 0.08, w * 0.09, h * 0.33);
    p.addPath(rib);
  }
  // ogonek
  Path stem;
  stem.addRect(c.x - w * 0.04, c.y - h * 0.32, w * 0.08, h * 0.12);
  p.addPath(stem);
  return p;
}

Path heartShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  p.moveTo({c.x, y0 + h * 0.96});
  p.cubicTo({x0 - w * 0.06, y0 + h * 0.52}, {x0 + w * 0.16, y0 + h * 0.02}, {c.x, y0 + h * 0.26});
  p.cubicTo({x0 + w * 0.84, y0 + h * 0.02}, {x0 + w * 1.06, y0 + h * 0.52}, {c.x, y0 + h * 0.96});
  p.close();
  return p;
}

Path pawShape(Vec2 c, double w, double h) {
  Path p;
  p.addEllipse(c.x, c.y + h * 0.22, w * 0.26, h * 0.22);
  p.addPath(circlePath({c.x - w * 0.28, c.y - h * 0.06}, w * 0.11));
  p.addPath(circlePath({c.x - w * 0.10, c.y - h * 0.22}, w * 0.11));
  p.addPath(circlePath({c.x + w * 0.10, c.y - h * 0.22}, w * 0.11));
  p.addPath(circlePath({c.x + w * 0.28, c.y - h * 0.06}, w * 0.11));
  return p;
}

Path birdShape(Vec2 c, double w, double h) {
  Path p;
  p.addPath(smoothPath({{c.x - w * 0.42, c.y + h * 0.10}, {c.x - w * 0.10, c.y - h * 0.16},
                        {c.x + w * 0.24, c.y - h * 0.20}, {c.x + w * 0.44, c.y - h * 0.02},
                        {c.x + w * 0.20, c.y + h * 0.08}, {c.x - w * 0.10, c.y + h * 0.22}}, true));
  p.addPath(polyPath({{c.x + w * 0.44, c.y - h * 0.02}, {c.x + w * 0.58, c.y + h * 0.02},
                      {c.x + w * 0.42, c.y + h * 0.08}}, true));
  return p;
}

Path horseShape(Vec2 c, double w, double h) {
  Path p;
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  // koń w galopie — uproszczona sylwetka
  p.addPath(smoothPath({{x0 + w * 0.12, y0 + h * 0.66}, {x0 + w * 0.08, y0 + h * 0.44},
                        {x0 + w * 0.24, y0 + h * 0.36}, {x0 + w * 0.44, y0 + h * 0.34},
                        {x0 + w * 0.60, y0 + h * 0.26}, {x0 + w * 0.74, y0 + h * 0.12},
                        {x0 + w * 0.86, y0 + h * 0.06}, {x0 + w * 0.92, y0 + h * 0.18},
                        {x0 + w * 0.82, y0 + h * 0.30}, {x0 + w * 0.78, y0 + h * 0.44},
                        {x0 + w * 0.90, y0 + h * 0.60}, {x0 + w * 0.72, y0 + h * 0.62},
                        {x0 + w * 0.52, y0 + h * 0.70}, {x0 + w * 0.30, y0 + h * 0.74}}, true));
  // grzywa
  Path mane;
  mane.moveTo({x0 + w * 0.62, y0 + h * 0.24});
  mane.cubicTo({x0 + w * 0.56, y0 + h * 0.34}, {x0 + w * 0.64, y0 + h * 0.44}, {x0 + w * 0.58, y0 + h * 0.56});
  p.addPath(mane);
  return p;
}

Path dragonShape(Vec2 c, double w, double h) {
  Path p;
  // głowa smoka z rogami i zębami
  double x0 = c.x - w / 2, y0 = c.y - h / 2;
  p.addPath(smoothPath({{x0 + w * 0.20, y0 + h * 0.62}, {x0 + w * 0.16, y0 + h * 0.36},
                        {x0 + w * 0.34, y0 + h * 0.22}, {x0 + w * 0.56, y0 + h * 0.20},
                        {x0 + w * 0.78, y0 + h * 0.28}, {x0 + w * 0.90, y0 + h * 0.44},
                        {x0 + w * 0.82, y0 + h * 0.60}, {x0 + w * 0.58, y0 + h * 0.66},
                        {x0 + w * 0.36, y0 + h * 0.70}}, true));
  Path horns;
  horns.addPolygon({{x0 + w * 0.30, y0 + h * 0.24}, {x0 + w * 0.22, y0 + h * 0.04},
                    {x0 + w * 0.38, y0 + h * 0.18}}, true);
  horns.addPolygon({{x0 + w * 0.52, y0 + h * 0.20}, {x0 + w * 0.58, y0 + h * 0.02},
                    {x0 + w * 0.64, y0 + h * 0.22}}, true);
  p.addPath(horns);
  p.addPath(circlePath({x0 + w * 0.42, y0 + h * 0.42}, w * 0.045));
  for (int i = 0; i < 3; i++)
    p.addPath(polyPath({{x0 + w * (0.52 + i * 0.10), y0 + h * 0.60},
                        {x0 + w * (0.56 + i * 0.10), y0 + h * 0.72},
                        {x0 + w * (0.60 + i * 0.10), y0 + h * 0.60}}, true));
  return p;
}

Path anchorShape(Vec2 c, double h) {
  Path p;
  double r = h * 0.42;
  Path ring;
  ring.addCircle(c.x, c.y - h * 0.34, h * 0.06);
  p.addPath(ring);
  Path stem;
  stem.addRect(c.x - h * 0.025, c.y - h * 0.28, h * 0.05, h * 0.62);
  p.addPath(stem);
  Path cross;
  cross.addRect(c.x - h * 0.20, c.y - h * 0.16, h * 0.40, h * 0.045);
  p.addPath(cross);
  Path arms;
  arms.addArc(c.x, c.y + h * 0.08, r, 200, 340);
  p.addPath(arms);
  Path fluteL, fluteR;
  fluteL.addPolygon({{c.x - r * 0.98, c.y + h * 0.10}, {c.x - r * 0.78, c.y + h * 0.24}, {c.x - r * 0.86, c.y - h * 0.02}}, true);
  fluteR.addPolygon({{c.x + r * 0.98, c.y + h * 0.10}, {c.x + r * 0.78, c.y + h * 0.24}, {c.x + r * 0.86, c.y - h * 0.02}}, true);
  p.addPath(fluteL);
  p.addPath(fluteR);
  return p;
}

Path gearShape(Vec2 c, double r, int teeth) {
  Path p;
  std::vector<Vec2> pts;
  double inner = r * 0.78;
  for (int i = 0; i < teeth; i++) {
    double a0 = 2 * M_PI * i / teeth;
    double a1 = a0 + 2 * M_PI / teeth * 0.30;
    double a2 = a0 + 2 * M_PI / teeth * 0.50;
    double a3 = a0 + 2 * M_PI / teeth * 0.80;
    pts.push_back({c.x + std::cos(a0) * inner, c.y + std::sin(a0) * inner});
    pts.push_back({c.x + std::cos(a1) * r, c.y + std::sin(a1) * r});
    pts.push_back({c.x + std::cos(a2) * r, c.y + std::sin(a2) * r});
    pts.push_back({c.x + std::cos(a3) * inner, c.y + std::sin(a3) * inner});
  }
  p = polyPath(pts, true);
  p.addPath(circlePath(c, r * 0.30));
  return p;
}

// ---------------- ramki ----------------
Path framePath(const std::string& style, const Rect& a, double t) {
  Path p;
  auto inset = [&](double k) {
    return Rect(a.x + k, a.y + k, std::max(1.0, a.w - 2 * k), std::max(1.0, a.h - 2 * k));
  };
  if (style == "none") return p;
  if (style == "simple" || style == "retro") {
    Rect r1 = inset(0.6);
    Path o;
    o.addRoundRect(r1.x, r1.y, r1.w, r1.h, 1.5);
    p.addPath(o);
    Rect r2 = inset(t + 2.2);
    Path i2;
    i2.addRoundRect(r2.x, r2.y, r2.w, r2.h, 1.0);
    p.addPath(i2);
  } else if (style == "double" || style == "ornate") {
    for (double k : {0.6, t + 2.0, t + 4.3}) {
      Rect r = inset(k);
      Path o;
      o.addRoundRect(r.x, r.y, r.w, r.h, style == "ornate" ? 3.0 : 1.2);
      p.addPath(o);
    }
    if (style == "ornate") {
      double s = std::min(a.w, a.h) * 0.16;
      p.addPath(cornerFlourish({a.x + t + 4.0, a.y + t + 4.0}, s, 0));
      p.addPath(cornerFlourish({a.x1() - t - 4.0, a.y + t + 4.0}, s, 1));
      p.addPath(cornerFlourish({a.x1() - t - 4.0, a.y1() - t - 4.0}, s, 2));
      p.addPath(cornerFlourish({a.x + t + 4.0, a.y1() - t - 4.0}, s, 3));
    }
  } else if (style == "rope") {
    Rect r = inset(t * 0.6);
    // plecionka z okręgów wzdłuż obwodu
    int n = std::max(24, (int)((r.w + r.h) * 2 / (t * 1.6)));
    double per = 2 * (r.w + r.h);
    for (int i = 0; i < n; i++) {
      double d = per * i / n;
      Vec2 pt;
      if (d < r.w) pt = {r.x + d, r.y};
      else if (d < r.w + r.h) pt = {r.x1(), r.y + (d - r.w)};
      else if (d < 2 * r.w + r.h) pt = {r.x1() - (d - r.w - r.h), r.y1()};
      else pt = {r.x, r.y1() - (d - 2 * r.w - r.h)};
      p.addPath(ellipsePath(pt, t * 0.75, t * 0.5, (i % 2) ? 30 : -30));
    }
    Path o1, o2;
    o1.addRoundRect(r.x, r.y, r.w, r.h, 1);
    Rect r2 = inset(t * 2.2);
    o2.addRoundRect(r2.x, r2.y, r2.w, r2.h, 1);
    p.addPath(o1);
    p.addPath(o2);
  } else if (style == "gothic") {
    Rect r = inset(t * 0.5);
    // łuk ostry u góry
    Path g;
    g.moveTo({r.x, r.y1()});
    g.lineTo({r.x, r.y + r.h * 0.28});
    g.cubicTo({r.x, r.y + r.h * 0.06}, {r.x + r.w * 0.30, r.y}, {r.x + r.w * 0.5, r.y});
    g.cubicTo({r.x + r.w * 0.70, r.y}, {r.x1(), r.y + r.h * 0.06}, {r.x1(), r.y + r.h * 0.28});
    g.lineTo({r.x1(), r.y1()});
    g.close();
    p.addPath(g);
    Rect r2 = inset(t + 2.5);
    Path g2;
    g2.moveTo({r2.x, r2.y1()});
    g2.lineTo({r2.x, r2.y + r2.h * 0.28});
    g2.cubicTo({r2.x, r2.y + r2.h * 0.06}, {r2.x + r2.w * 0.30, r2.y}, {r2.x + r2.w * 0.5, r2.y});
    g2.cubicTo({r2.x + r2.w * 0.70, r2.y}, {r2.x1(), r2.y + r2.h * 0.06}, {r2.x1(), r2.y + r2.h * 0.28});
    g2.lineTo({r2.x1(), r2.y1()});
    g2.close();
    p.addPath(g2);
  } else if (style == "oval") {
    Rect r = inset(t);
    Path o;
    o.addEllipse(r.cx(), r.cy(), r.w / 2, r.h / 2);
    p.addPath(o);
    Rect r2 = inset(t + 2.4);
    Path o2;
    o2.addEllipse(r2.cx(), r2.cy(), r2.w / 2, r2.h / 2);
    p.addPath(o2);
  } else if (style == "sunburst") {
    p.addPath(sunRays({a.cx(), a.cy()}, std::min(a.w, a.h) * 0.42, std::min(a.w, a.h) * 0.50, 36));
    Rect r = inset(t + 3.0);
    Path o;
    o.addRoundRect(r.x, r.y, r.w, r.h, 2);
    p.addPath(o);
  } else if (style == "industrial") {
    Rect r = inset(1);
    Path o;
    o.addRoundRect(r.x, r.y, r.w, r.h, 0.8);
    p.addPath(o);
    // nity w narożnikach
    for (double fx : {0.04, 0.96})
      for (double fy : {0.06, 0.94}) {
        Path riv;
        riv.addCircle(a.x + a.w * fx, a.y + a.h * fy, 2.2);
        p.addPath(riv);
      }
    Rect r2 = inset(t + 3.5);
    Path o2;
    o2.addRoundRect(r2.x, r2.y, r2.w, r2.h, 0.6);
    p.addPath(o2);
  } else if (style == "floral") {
    Rect r = inset(1.0);
    Path o;
    o.addRoundRect(r.x, r.y, r.w, r.h, 2.0);
    p.addPath(o);
    double s = std::min(a.w, a.h) * 0.22;
    p.addPath(cornerFlourish({a.x + 5, a.y + 5}, s, 0));
    p.addPath(cornerFlourish({a.x1() - 5, a.y + 5}, s, 1));
    p.addPath(cornerFlourish({a.x1() - 5, a.y1() - 5}, s, 2));
    p.addPath(cornerFlourish({a.x + 5, a.y1() - 5}, s, 3));
    p.addPath(laurel({a.x + s * 0.6, a.cy()}, std::min(a.w, a.h) * 0.30, true));
    p.addPath(laurel({a.x1() - s * 0.6, a.cy()}, std::min(a.w, a.h) * 0.30, false));
  } else {
    Rect r = inset(1);
    Path o;
    o.addRoundRect(r.x, r.y, r.w, r.h, 1);
    p.addPath(o);
  }
  // Ramka NIE MOŻE wychodzić poza obszar produktu (walidator i wycinarka).
  // Motywy (floral, ornate, sunburst) mają ozdobniki wystające poza prostokąt —
  // dopasowujemy skalę i środek, zamiast zostawiać geometrię poza materiałem.
  Rect b = p.bounds();
  if (!b.empty() && b.w > 0.01 && b.h > 0.01) {
    double kx = a.w / b.w, ky = a.h / b.h;
    double k = std::min(1.0, std::min(kx, ky));
    double tx = a.cx() - (b.x + b.w / 2.0) * k;
    double ty = a.cy() - (b.y + b.h / 2.0) * k;
    if (k < 0.999 || std::fabs(tx) > 0.02 || std::fabs(ty) > 0.02) {
      Path fitted;
      fitted.segs = p.segs;
      fitted.apply(Transform2{k, 0, 0, k, tx, ty});
      p = fitted;
    }
  }
  return p;
}

std::vector<std::string> frameIds() {
  return {"simple", "double", "ornate", "rope", "gothic", "oval", "sunburst", "industrial", "floral", "retro"};
}
std::string frameBestMatch(const std::string& style) {
  std::string s = lowerAscii(stripDiacritics(style));
  if (contains(s, "gotyk") || contains(s, "gothic") || s == "gothic") return "gothic";
  if (contains(s, "vintage") || contains(s, "retro")) return "retro";
  if (contains(s, "minimal")) return "simple";
  if (contains(s, "komiks") || contains(s, "comic")) return "industrial";
  if (contains(s, "slub") || contains(s, "elegan")) return "floral";
  if (contains(s, "swieta") || contains(s, "christmas")) return "rope";
  if (contains(s, "slavia") || contains(s, "slowia")) return "sunburst";
  if (contains(s, "fantasy") || contains(s, "bajk")) return "ornate";
  return "double";
}

// ---------------- ornamenty ----------------
Path bannerRibbon(const Rect& r, double fold) {
  // Wstęga z wcięciami po bokach (jaskółczy ogon) — jeden zamknięty kontur,
  // bez "ogonków", które wcześniej rysowały się jako poszarpane trójkąty.
  Path p;
  double f = std::min(fold, std::min(r.w * 0.18, r.h * 0.7));
  p.addPolygon({{r.x, r.y}, {r.x1(), r.y}, {r.x1() - f, r.cy()}, {r.x1(), r.y1()},
                {r.x, r.y1()}, {r.x + f, r.cy()}}, true);
  return p;
}
Path sunRays(Vec2 c, double r0, double r1, int count) {
  Path p;
  for (int i = 0; i < count; i++) {
    double a = 2 * M_PI * i / count;
    double spread = (2 * M_PI / count) * 0.28;
    Path ray;
    ray.addPolygon({{c.x + std::cos(a) * r0, c.y + std::sin(a) * r0},
                    {c.x + std::cos(a - spread) * r1, c.y + std::sin(a - spread) * r1},
                    {c.x + std::cos(a + spread) * r1, c.y + std::sin(a + spread) * r1}}, true);
    p.addPath(ray);
  }
  return p;
}
Path laurel(Vec2 c, double r, bool leftSide) {
  Path p;
  double dir = leftSide ? -1 : 1;
  int leaves = 9;
  for (int i = 0; i < leaves; i++) {
    double t = (double)i / (leaves - 1);
    double a = (leftSide ? M_PI * 0.55 : M_PI * 0.45) + dir * t * M_PI * 0.55;
    Vec2 pt(c.x + std::cos(a) * r * 0.9, c.y - std::sin(a) * r * 0.9 + r * 0.5);
    Path leaf;
    leaf.addEllipse(pt.x, pt.y, r * 0.16, r * 0.07, (a * 180 / M_PI) + 40 * dir);
    p.addPath(leaf);
  }
  Path stem;
  stem.addArc(c.x, c.y + r * 0.5, r * 0.9, leftSide ? 160 : 200, leftSide ? 20 : -20);
  p.addPath(stem);
  return p;
}
Path cornerFlourish(Vec2 corner, double size, int rot) {
  Path p;
  // ornament narożny: spirala + listki (rysowany w orientacji 0 = lewy górny)
  p.moveTo({corner.x, corner.y + size});
  p.cubicTo({corner.x, corner.y + size * 0.30}, {corner.x + size * 0.30, corner.y},
            {corner.x + size, corner.y});
  Path leaf1;
  leaf1.addEllipse(corner.x + size * 0.34, corner.y + size * 0.34, size * 0.20, size * 0.09, 45);
  Path leaf2;
  leaf2.addEllipse(corner.x + size * 0.62, corner.y + size * 0.16, size * 0.16, size * 0.07, 20);
  Path curl;
  curl.moveTo({corner.x + size * 0.30, corner.y + size * 0.30});
  curl.cubicTo({corner.x + size * 0.46, corner.y + size * 0.42}, {corner.x + size * 0.52, corner.y + size * 0.24},
               {corner.x + size * 0.40, corner.y + size * 0.20});
  p.addPath(leaf1);
  p.addPath(leaf2);
  p.addPath(curl);
  if (rot != 0) {
    Vec2 c(corner.x, corner.y);
    Transform2 t = Transform2::translate(c.x, c.y).mul(Transform2::rotate(rot * 90)).mul(Transform2::translate(-c.x, -c.y));
    p.apply(t);
  }
  return p;
}
Path dividerLine(Vec2 a, Vec2 b, double amplitude, double step) {
  Path p;
  double len = (b - a).len();
  if (len < 1) return p;
  Vec2 dir = (b - a) / len, nrm(-dir.y, dir.x);
  int n = std::max(2, (int)(len / step));
  std::vector<Vec2> pts;
  for (int i = 0; i <= n; i++) {
    double t = (double)i / n;
    double s = std::sin(t * M_PI * 6) * amplitude;
    pts.push_back(a + dir * (len * t) + nrm * s);
  }
  return smoothPath(pts, false);
}
Path starsRow(Vec2 center, double width, int n, double size) {
  Path p;
  for (int i = 0; i < n; i++) {
    double x = n == 1 ? center.x : center.x - width / 2 + width * i / (n - 1);
    Path s;
    s.addStar(x, center.y, size, size * 0.42, 5);
    p.addPath(s);
  }
  return p;
}
Path moonsAndStars(const Rect& r) {
  Path p;
  Path moon;
  moon.addCircle(r.cx(), r.cy() - r.h * 0.2, r.h * 0.22);
  Path cut;
  cut.addCircle(r.cx() + r.h * 0.09, r.cy() - r.h * 0.26, r.h * 0.19);
  moon.addPath(cut);
  p.addPath(moon);
  for (int i = 0; i < 6; i++) {
    double x = r.x + r.w * (0.12 + 0.76 * ((i * 37) % 100) / 100.0);
    double y = r.y + r.h * (0.12 + 0.76 * ((i * 61) % 100) / 100.0);
    if (std::fabs(x - r.cx()) < r.w * 0.18 && std::fabs(y - (r.cy() - r.h * 0.2)) < r.h * 0.18) continue;
    Path s;
    s.addStar(x, y, r.w * 0.028, r.w * 0.012, 5);
    p.addPath(s);
  }
  return p;
}
Path snowflake(Vec2 c, double r, int arms) {
  Path p;
  for (int i = 0; i < arms; i++) {
    double a = 2 * M_PI * i / arms;
    Path arm;
    arm.moveTo(c);
    arm.lineTo({c.x + std::cos(a) * r, c.y + std::sin(a) * r});
    for (double f : {0.45, 0.72}) {
      Path br;
      br.moveTo({c.x + std::cos(a) * r * f, c.y + std::sin(a) * r * f});
      br.lineTo({c.x + std::cos(a + 0.5) * r * f * 1.05, c.y + std::sin(a + 0.5) * r * f * 1.05});
      Path br2;
      br2.moveTo({c.x + std::cos(a) * r * f, c.y + std::sin(a) * r * f});
      br2.lineTo({c.x + std::cos(a - 0.5) * r * f * 1.05, c.y + std::sin(a - 0.5) * r * f * 1.05});
      arm.addPath(br);
      arm.addPath(br2);
    }
    p.addPath(arm);
  }
  return p;
}
Path crossOrnament(const Rect& r) {
  Path p;
  p.addRect(r.cx() - r.w * 0.08, r.y, r.w * 0.16, r.h);
  p.addRect(r.x, r.cy() - r.h * 0.16, r.w, r.h * 0.16);
  Path glow;
  glow.addCircle(r.cx(), r.cy() - r.h * 0.16, r.w * 0.26);
  p.addPath(glow);
  return p;
}
Path arrowBanner(const Rect& r) {
  Path p;
  p.addPolygon({{r.x, r.y}, {r.x1() - r.h * 0.5, r.y}, {r.x1(), r.cy()},
                {r.x1() - r.h * 0.5, r.y1()}, {r.x, r.y1()}, {r.x + r.h * 0.35, r.cy()}}, true);
  return p;
}
Path shieldEmblem(const Rect& r) {
  Path p;
  p.moveTo({r.x + r.w * 0.06, r.y});
  p.lineTo({r.x1() - r.w * 0.06, r.y});
  p.cubicTo({r.x1(), r.y + r.h * 0.35}, {r.x1() - r.w * 0.12, r.y + r.h * 0.72}, {r.cx(), r.y1()});
  p.cubicTo({r.x + r.w * 0.12, r.y + r.h * 0.72}, {r.x, r.y + r.h * 0.35}, {r.x + r.w * 0.06, r.y});
  p.close();
  return p;
}

// ---------------- katalog emblematów ----------------
static Emblem makeEmblem(const std::string& id, std::vector<EmblemPart> parts) {
  Emblem e;
  e.id = id;
  e.parts = std::move(parts);
  Rect b;
  bool first = true;
  for (auto& p : e.parts) {
    Rect pb = p.path.bounds();
    b = first ? pb : b.unite(pb);
    first = false;
  }
  e.bounds = b;
  return e;
}

const Emblem* emblem(const std::string& id) {
  static std::map<std::string, Emblem> lib;
  if (lib.empty()) {
    Vec2 c(50, 50);
    auto add = [&](const std::string& key, std::vector<EmblemPart> parts) {
      lib[key] = makeEmblem(key, std::move(parts));
    };
    // --- ryby: rysunek liniowy (kontur + skrzela + oko + płetwy) ---
    auto fishDetail = [&](double w, double h, bool pike) {
      std::vector<EmblemPart> v;
      v.push_back({fishShape(c, w, h, pike), false, true, 0, 0});
      Path gill;
      double x0 = c.x - w / 2;
      gill.moveTo({x0 + w * 0.17, c.y - h * 0.30});
      gill.cubicTo({x0 + w * 0.24, c.y - h * 0.10}, {x0 + w * 0.24, c.y + h * 0.10},
                   {x0 + w * 0.17, c.y + h * 0.30});
      v.push_back({gill, false, true, 0, 0});
      Path eye;
      eye.addCircle(x0 + w * 0.10, c.y - h * 0.10, std::max(1.2, h * 0.055));
      v.push_back({eye, true, false, 0, 0});
      Path dorsal;
      dorsal.moveTo({c.x + w * 0.04, c.y - h * 0.36});
      dorsal.cubicTo({c.x + w * 0.12, c.y - h * 0.72}, {c.x + w * 0.24, c.y - h * 0.70},
                     {c.x + w * 0.30, c.y - h * 0.34});
      v.push_back({dorsal, false, true, 0, 0});
      Path pec;
      pec.moveTo({x0 + w * 0.30, c.y + h * 0.32});
      pec.cubicTo({x0 + w * 0.34, c.y + h * 0.58}, {x0 + w * 0.46, c.y + h * 0.56},
                  {x0 + w * 0.48, c.y + h * 0.30});
      v.push_back({pec, false, true, 0, 0});
      // łuski: trzy rzędy krótkich łuków
      Path scales;
      for (int row = 0; row < 3; row++) {
        for (int col = 0; col < 4; col++) {
          double sx = c.x + w * (0.02 + col * 0.11);
          double sy = c.y + h * (0.10 * (row - 1) + (col % 2 ? 0.06 : 0.0));
          scales.moveTo({sx, sy - h * 0.05});
          scales.cubicTo({sx + w * 0.03, sy}, {sx + w * 0.03, sy}, {sx, sy + h * 0.05});
        }
      }
      v.push_back({scales, false, true, 0, 0});
      return v;
    };
    add("szczupak", fishDetail(92, 44, true));
    add("ryba", fishDetail(88, 52, false));
    // --- wilk: kontur głowy + oczy + nos + linia kufy + wewnętrzne uszy ---
    {
      std::vector<EmblemPart> v;
      v.push_back({wolfShape(c, 74, 84), false, true, 0, 0});
      Path ears;
      ears.moveTo({c.x - 74 * 0.26, c.y - 84 * 0.34});
      ears.lineTo({c.x - 74 * 0.27, c.y - 84 * 0.44});
      ears.moveTo({c.x + 74 * 0.26, c.y - 84 * 0.34});
      ears.lineTo({c.x + 74 * 0.27, c.y - 84 * 0.44});
      v.push_back({ears, false, true, 0, 0});
      Path eyes;
      eyes.addPolygon({{c.x - 19, c.y - 6}, {c.x - 8, c.y - 9}, {c.x - 11, c.y + 2}}, true);
      eyes.addPolygon({{c.x + 19, c.y - 6}, {c.x + 8, c.y - 9}, {c.x + 11, c.y + 2}}, true);
      v.push_back({eyes, true, true, 0, 0});
      Path nose;
      nose.addPolygon({{c.x - 5, c.y + 15}, {c.x + 5, c.y + 15}, {c.x, c.y + 21}}, true);
      v.push_back({nose, true, true, 0, 0});
      Path muzzle;
      muzzle.moveTo({c.x, c.y + 21});
      muzzle.lineTo({c.x, c.y + 26});
      muzzle.moveTo({c.x - 6, c.y + 26});
      muzzle.quadTo({c.x, c.y + 31}, {c.x + 6, c.y + 26});
      v.push_back({muzzle, false, true, 0, 0});
      add("wilk", v);
    }
    add("jelen", {{deerShape(c, 78, 88)}});
    add("tractor", {{tractorShape(c, 90, 80)}});
    add("samochod", {{carShape(c, 90, 60)}});
    add("gitara", {{guitarShape(c, 60, 92)}});
    add("pad", {{gamepadShape(c, 88, 56)}});
    add("kubek", {{mugShape(c, 80, 80)}});
    add("czaszka", {{skullShape(c, 74, 84)}});
    add("roza", {{roseShape(c, 84, 84)}});
    add("drzewo", {{treeShape(c, 80, 88)}});
    add("gory", {{mountainsShape(c, 90, 60)}});
    add("korona", {{crownShape(c, 84, 62)}});
    add("orzel", {{eagleShape(c, 92, 80)}});
    add("dynia", {{pumpkinShape(c, 80, 80)}});
    add("serce", {{heartShape(c, 78, 72)}});
    add("lapa", {{pawShape(c, 72, 72)}});
    add("ptak", {{birdShape(c, 86, 60)}});
    add("kon", {{horseShape(c, 92, 72)}});
    add("smok", {{dragonShape(c, 86, 70)}});
    add("kotwica", {{anchorShape(c, 72)}});
    add("kolo_zebate", {{gearShape(c, 44, 14)}});
    add("kolo", {{ringPath(c, 34, 44)}});
    // --- motywy okolicznościowe (dodane w wersji 1.0) ---
    {
      // choinka: trzy poziomy gałęzi + pień + gwiazda
      Path tree;
      tree.addPolygon({{50, 4}, {66, 30}, {57, 30}, {70, 48}, {59, 48}, {78, 72}, {50, 66}}, true);
      tree.addPolygon({{50, 4}, {34, 30}, {43, 30}, {30, 48}, {41, 48}, {22, 72}, {50, 66}}, true);
      Path trunk;
      trunk.addRect(45, 70, 10, 16);
      Path star = starsRow({50, 8}, 10, 1, 9);
      add("choinka", {{tree, true, true, 2.2, 45}, {trunk, true, true, 0, 0}, {star, false, true, 0, 0}});
    }
    {
      // pisanka: jajko z zygzakowatą wstęgą i kropkami
      Path egg;
      egg.addEllipse(50, 52, 30, 40);
      Path band;
      for (int i = 0; i < 9; i++) {
        double x0 = 24 + i * 6.5;
        if (i == 0) band.moveTo({x0, 50});
        band.lineTo({x0 + 3.2, 42});
        band.lineTo({x0 + 6.5, 50});
      }
      Path band2;
      for (int i = 0; i < 9; i++) {
        double x0 = 24 + i * 6.5;
        if (i == 0) band2.moveTo({x0, 64});
        band2.lineTo({x0 + 3.2, 56});
        band2.lineTo({x0 + 6.5, 64});
      }
      add("jajko", {{egg, false, true, 0, 0}, {band, false, true, 0, 0}, {band2, false, true, 0, 0}});
    }
    {
      // obrączki: dwa pierścienie
      add("obraczki", {{ringPath({41, 56}, 22, 27)}, {ringPath({60, 56}, 22, 27)}});
    }
    {
      // medal: tarcza + wstęga
      Path disc = ringPath({50, 42}, 26, 32);
      Path ribbon;
      ribbon.addPolygon({{34, 68}, {44, 68}, {50, 92}, {40, 92}}, true);
      ribbon.addPolygon({{66, 68}, {56, 68}, {50, 92}, {60, 92}}, true);
      add("medal", {{ribbon, true, true, 0, 0}, {disc, false, true, 0, 0}});
    }
    lib["kolo"].parts[0].hatchSpacing = 0;
  }
  auto it = lib.find(id);
  return it == lib.end() ? nullptr : &it->second;
}
std::vector<std::string> emblemIds() {
  emblem("szczupak");
  static std::map<std::string, Emblem>* lib = nullptr;
  (void)lib;
  return {"szczupak", "ryba", "wilk", "jelen", "tractor", "samochod", "gitara", "pad", "kubek",
          "czaszka", "roza", "drzewo", "gory", "korona", "orzel", "dynia", "serce", "lapa", "ptak",
          "kon", "smok", "kotwica", "kolo_zebate", "choinka", "jajko", "obraczki", "medal"};
}

std::string emblemBestMatch(const std::string& subject) {
  std::string s = lowerAscii(stripDiacritics(subject));
  struct K { const char* word; const char* id; };
  static const K keys[] = {
      {"choink", "choinka"}, {"christmas", "choinka"}, {"swieta", "choinka"}, {"boze", "choinka"},
      {"jajk", "jajko"}, {"pisank", "jajko"}, {"wielkan", "jajko"}, {"easter", "jajko"}, {"zajac", "jajko"},
      {"obraczk", "obraczki"}, {"slub", "obraczki"}, {"wedding", "obraczki"}, {"rocznic", "obraczki"},
      {"medal", "medal"}, {"dozynk", "medal"}, {"nagrod", "medal"}, {"zasluz", "medal"},
      {"szczupak", "szczupak"}, {"pike", "szczupak"}, {"ryb", "ryba"}, {"fish", "ryba"},
      {"karp", "ryba"}, {"okon", "ryba"}, {"wedk", "szczupak"},
      {"wilk", "wilk"}, {"wolf", "wilk"}, {"wilcz", "wilk"},
      {"jelen", "jelen"}, {"deer", "jelen"}, {"rogacz", "jelen"}, {"los", "jelen"},
      {"mysliw", "jelen"}, {"hunt", "jelen"},
      {"traktor", "tractor"}, {"tractor", "tractor"}, {"rolnik", "tractor"}, {"rolnictw", "tractor"},
      {"samochod", "samochod"}, {"car", "samochod"}, {"auto", "samochod"}, {"garaz", "samochod"},
      {"motor", "samochod"}, {"motocykl", "samochod"},
      {"gitara", "gitara"}, {"guitar", "gitara"}, {"muzyk", "gitara"},
      {"pad", "pad"}, {"gamepad", "pad"}, {"gaming", "pad"}, {"kontroler", "pad"}, {"gra", "pad"},
      {"kubek", "kubek"}, {"mug", "kubek"}, {"kaw", "kubek"},
      {"czaszka", "czaszka"}, {"skull", "czaszka"}, {"pirat", "czaszka"},
      {"roza", "roza"}, {"rose", "roza"}, {"kwiat", "roza"},
      {"drzewo", "drzewo"}, {"tree", "drzewo"}, {"las", "drzewo"},
      {"gory", "gory"}, {"mountain", "gory"}, {"gór", "gory"},
      {"korona", "korona"}, {"crown", "korona"}, {"king", "korona"}, {"krol", "korona"},
      {"orzel", "orzel"}, {"eagle", "orzel"}, {"godlo", "orzel"},
      {"dynia", "dynia"}, {"pumpkin", "dynia"}, {"halloween", "dynia"},
      {"serce", "serce"}, {"heart", "serce"}, {"milosc", "serce"},
      {"lapa", "lapa"}, {"paw", "lapa"}, {"pies", "lapa"}, {"kot", "lapa"},
      {"ptak", "ptak"}, {"bird", "ptak"}, {"golab", "ptak"},
      {"kon", "kon"}, {"horse", "kon"}, {"jezdz", "kon"},
      {"smok", "smok"}, {"dragon", "smok"},
      {"kotwica", "kotwica"}, {"anchor", "kotwica"}, {"zegl", "kotwica"},
      {"zebat", "kolo_zebate"}, {"gear", "kolo_zebate"}, {"warsztat", "kolo_zebate"}, {"mechanik", "kolo_zebate"},
      {"kolo", "kolo"}, {"circle", "kolo"},
  };
  for (auto& k : keys) if (contains(s, k.word)) return k.id;
  return "";
}

}  // namespace lc
