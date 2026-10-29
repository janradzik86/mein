// Magazyn obrazów rastrowych projektu (PNG base64 w JSON projektu)
#pragma once
#include "design.h"
#include <map>

namespace lc {

class AssetStore {
 public:
  void bind(const Design& d) { assets_ = &d.assets; }
  std::string putPng(const Image& img, const std::string& name = "");
  std::string putPngBytes(const std::vector<uint8_t>& png, const std::string& name = "",
                          int w = 0, int h = 0);
  bool get(const std::string& id, Image& out) const;
  std::string b64(const std::string& id) const;
  bool has(const std::string& id) const;
  size_t count() const { return assets_ ? assets_->size() : 0; }
  std::vector<std::string> ids() const;

 private:
  const Json* assets_ = nullptr;
  mutable std::map<std::string, Image> cache_;
};

}  // namespace lc
