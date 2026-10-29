// WOJAN LaserCraft AI Studio — wspólne typy i narzędzia (C++17, bez zależności)
#pragma once
#include <cstdint>
#include <cmath>
#include <string>
#include <vector>
#include <algorithm>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>

namespace lc {

struct Vec2 {
  double x = 0, y = 0;
  Vec2() = default;
  Vec2(double x_, double y_) : x(x_), y(y_) {}
  Vec2 operator+(const Vec2& o) const { return {x + o.x, y + o.y}; }
  Vec2 operator-(const Vec2& o) const { return {x - o.x, y - o.y}; }
  Vec2 operator*(double s) const { return {x * s, y * s}; }
  Vec2 operator/(double s) const { return {x / s, y / s}; }
  Vec2& operator+=(const Vec2& o) { x += o.x; y += o.y; return *this; }
  double len() const { return std::sqrt(x * x + y * y); }
  double dot(const Vec2& o) const { return x * o.x + y * o.y; }
  double cross(const Vec2& o) const { return x * o.y - y * o.x; }
  Vec2 norm() const { double l = len(); return l < 1e-12 ? Vec2(0, 0) : Vec2(x / l, y / l); }
};

struct Rect {
  double x = 0, y = 0, w = 0, h = 0;
  Rect() = default;
  Rect(double x_, double y_, double w_, double h_) : x(x_), y(y_), w(w_), h(h_) {}
  double x1() const { return x + w; }
  double y1() const { return y + h; }
  double cx() const { return x + w / 2; }
  double cy() const { return y + h / 2; }
  bool empty() const { return w <= 0 || h <= 0; }
  bool contains(double px, double py) const { return px >= x && py >= y && px <= x + w && py <= y + h; }
  bool contains(const Rect& r) const { return r.x >= x && r.y >= y && r.x + r.w <= x + w && r.y + r.h <= y + h; }
  bool overlaps(const Rect& r) const { return !(r.x > x + w || r.x + r.w < x || r.y > y + h || r.y + r.h < y); }
  Rect inflate(double d) const { return {x - d, y - d, w + 2 * d, h + 2 * d}; }
  Rect unite(const Rect& r) const {
    if (empty()) return r;
    if (r.empty()) return *this;
    double a = std::min(x, r.x), b = std::min(y, r.y);
    double c = std::max(x1(), r.x1()), d = std::max(y1(), r.y1());
    return {a, b, c - a, d - b};
  }
  bool valid() const { return std::isfinite(x) && std::isfinite(y) && std::isfinite(w) && std::isfinite(h); }
};

struct Transform2 {
  double a = 1, b = 0, c = 0, d = 1, e = 0, f = 0;  // [a b c d e f] jak SVG
  Vec2 apply(const Vec2& p) const { return {a * p.x + c * p.y + e, b * p.x + d * p.y + f}; }
  Vec2 applyVec(const Vec2& p) const { return {a * p.x + c * p.y, b * p.x + d * p.y}; }
  Transform2 mul(const Transform2& o) const {  // this * o
    return {a * o.a + c * o.b, b * o.a + d * o.b,
            a * o.c + c * o.d, b * o.c + d * o.d,
            a * o.e + c * o.f + e, b * o.e + d * o.f + f};
  }
  Transform2 inverse() const {
    double det = a * d - b * c;
    if (std::fabs(det) < 1e-12) return {};
    double ia = d / det, ib = -b / det, ic = -c / det, id = a / det;
    return {ia, ib, ic, id, -(ia * e + ic * f), -(ib * e + id * f)};
  }
  static Transform2 translate(double dx, double dy) { return {1, 0, 0, 1, dx, dy}; }
  static Transform2 scale(double sx, double sy) { return {sx, 0, 0, sy, 0, 0}; }
  static Transform2 rotate(double deg) {
    double r = deg * M_PI / 180.0, c_ = std::cos(r), s_ = std::sin(r);
    return {c_, s_, -s_, c_, 0, 0};
  }
  bool identity() const {
    return std::fabs(a - 1) < 1e-12 && std::fabs(b) < 1e-12 && std::fabs(c) < 1e-12 &&
           std::fabs(d - 1) < 1e-12 && std::fabs(e) < 1e-12 && std::fabs(f) < 1e-12;
  }
};

// ---- narzędzia tekstowe ----
std::string trim(const std::string& s);
std::string lowerAscii(const std::string& s);
std::string upperAscii(const std::string& s);
bool startsWith(const std::string& s, const std::string& p);
bool contains(const std::string& s, const std::string& p);
std::vector<std::string> split(const std::string& s, char sep);
std::string join(const std::vector<std::string>& v, const std::string& sep);
std::string replaceAll(std::string s, const std::string& from, const std::string& to);
std::string fmtDouble(double v, int decimals);
std::string slugify(const std::string& s);

// UTF-8
std::vector<uint32_t> utf8Decode(const std::string& s);
std::string utf8Encode(const std::vector<uint32_t>& cps);
std::string utf8From(uint32_t cp);
std::vector<std::string> utf8Chars(const std::string& s);
// usuwa niepoprawne sekwencje UTF-8 (zabezpieczenie plików projektu/JSON)
std::string utf8Sanitize(const std::string& s);
std::string stripDiacritics(const std::string& s);  // do dopasowań w NLU
std::string lowerPl(const std::string& s);           // małe litery także PL

// pliki
std::string readFile(const std::string& path);
bool writeFile(const std::string& path, const std::string& data);
bool writeFileBytes(const std::string& path, const std::vector<uint8_t>& data);
bool readFileBytes(const std::string& path, std::vector<uint8_t>& out);
bool makeDirs(const std::string& path);
std::string base64Encode(const std::vector<uint8_t>& in);
std::vector<uint8_t> base64Decode(const std::string& in);
std::string nowIso();
std::string sfmt(const char* fmt, ...);

}  // namespace lc
