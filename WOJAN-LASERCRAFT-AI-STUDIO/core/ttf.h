// Parser TrueType/OpenType (glyf) — obrysy glifów do ścieżek wektorowych
#pragma once
#include "path.h"
#include <map>
#include <memory>
#include <string>
#include <vector>

namespace lc {

class Font {
 public:
  bool loadFile(const std::string& path);
  bool loadBytes(std::vector<uint8_t> data);
  bool valid() const { return upem_ > 0; }
  const std::string& name() const { return name_; }
  int glyphIndex(uint32_t cp) const;
  Path glyphPath(int gi) const;          // w jednostkach fontu (y w górę)
  double advance(int gi) const;
  double kern(int a, int b) const;
  double measure(const std::string& text, double sizeMm, double trackingMm) const;
  // Układ tekstu: origin = pozycja bazowa pierwszego znaku
  Path layout(const std::string& text, double sizeMm, double trackingMm, Vec2 origin) const;
  double upem() const { return upem_; }
  double ascenderMm(double sizeMm) const { return asc_ * sizeMm / upem_; }
  double descenderMm(double sizeMm) const { return desc_ * sizeMm / upem_; }
  double capHeightMm(double sizeMm) const { return cap_ * sizeMm / upem_; }
  Rect glyphBoundsMm(const std::string& text, double sizeMm, double trackingMm) const;

 private:
  const uint8_t* tbl(const char* tag, uint32_t* len = nullptr) const;
  std::vector<uint8_t> d_;
  std::map<std::string, std::pair<uint32_t, uint32_t>> tables_;
  std::map<uint32_t, int> cmap_;
  std::map<uint64_t, double> kern_;
  std::string name_;
  double upem_ = 0, asc_ = 0, desc_ = 0, cap_ = 0, xh_ = 0;
  uint32_t locaFmt_ = 0, numGlyphs_ = 0;
  std::vector<uint32_t> loca_;
  std::vector<std::pair<uint32_t, uint32_t>> hmtx_;
};

class FontRegistry {
 public:
  // dir = katalog z plikami TTF (assets/fonts)
  void setDir(const std::string& dir) { dir_ = dir; }
  Font* get(const std::string& id);          // ładowanie leniwe + cache
  const std::string& dir() const { return dir_; }
  std::vector<std::string> ids() const { return {"anton", "bebas", "cinzel", "cinzel_deco", "playfair", "oswald", "abril", "pirata", "barlow"}; }
  std::string fileFor(const std::string& id) const;

 private:
  std::string dir_;
  std::map<std::string, std::unique_ptr<Font>> cache_;
};

}  // namespace lc
