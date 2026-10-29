#include "assets.h"

namespace lc {

std::string AssetStore::putPng(const Image& img, const std::string& name) {
  auto png = pngEncode(img);
  return putPngBytes(png, name, img.w, img.h);
}
std::string AssetStore::putPngBytes(const std::vector<uint8_t>& png, const std::string& name, int w, int h) {
  if (!assets_) return "";
  Json& store = const_cast<Json&>(*assets_);
  std::string id = "img" + std::to_string(store.size() + 1);
  while (store.has(id)) id += "x";
  store.set(id, Json(base64Encode(png)));
  (void)name;
  (void)w;
  (void)h;
  return id;
}
bool AssetStore::get(const std::string& id, Image& out) const {
  auto it = cache_.find(id);
  if (it != cache_.end()) { out = it->second; return out.valid(); }
  if (!assets_ || !assets_->has(id)) return false;
  auto bytes = base64Decode((*assets_)[id].str());
  Image img;
  if (!pngDecode(bytes, img)) {
    if (!bmpDecode(bytes, img)) return false;
  }
  cache_[id] = img;
  out = img;
  return img.valid();
}
std::string AssetStore::b64(const std::string& id) const {
  if (!assets_ || !assets_->has(id)) return "";
  return (*assets_)[id].str();
}
bool AssetStore::has(const std::string& id) const { return assets_ && assets_->has(id); }
std::vector<std::string> AssetStore::ids() const { return assets_ ? assets_->keys() : std::vector<std::string>{}; }

}  // namespace lc
