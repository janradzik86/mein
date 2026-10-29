#include "raster.h"
#include "codec.h"
#include <cmath>
#include <cstring>

namespace lc {

Image Image::toGray() const {
  if (ch == 1) return *this;
  Image o(w, h, 1);
  for (int i = 0; i < w * h; i++) {
    const uint8_t* p = &px[(size_t)i * 3];
    o.px[i] = (uint8_t)((p[0] * 299 + p[1] * 587 + p[2] * 114) / 1000);
  }
  return o;
}
Image Image::toRGB() const {
  if (ch == 3) return *this;
  Image o(w, h, 3);
  for (int i = 0; i < w * h; i++) { o.px[i * 3] = o.px[i * 3 + 1] = o.px[i * 3 + 2] = px[i]; }
  return o;
}

// ---------------- PNG ----------------
static void putBE32(std::vector<uint8_t>& v, uint32_t x) {
  v.push_back((uint8_t)(x >> 24)); v.push_back((uint8_t)(x >> 16));
  v.push_back((uint8_t)(x >> 8)); v.push_back((uint8_t)x);
}
static void putChunk(std::vector<uint8_t>& out, const char* type, const std::vector<uint8_t>& data) {
  putBE32(out, (uint32_t)data.size());
  std::vector<uint8_t> td;
  td.insert(td.end(), type, type + 4);
  td.insert(td.end(), data.begin(), data.end());
  out.insert(out.end(), td.begin(), td.end());
  putBE32(out, crc32(td.data(), td.size()));
}
std::vector<uint8_t> pngEncode(const Image& img) {
  std::vector<uint8_t> out = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  std::vector<uint8_t> ihdr;
  putBE32(ihdr, (uint32_t)img.w);
  putBE32(ihdr, (uint32_t)img.h);
  ihdr.push_back(8);                       // bit depth
  ihdr.push_back(img.ch == 3 ? 2 : 0);     // color type
  ihdr.push_back(0); ihdr.push_back(0); ihdr.push_back(0);
  putChunk(out, "IHDR", ihdr);
  std::vector<uint8_t> raw;
  raw.reserve((size_t)(img.w * img.ch + 1) * img.h);
  for (int y = 0; y < img.h; y++) {
    raw.push_back(0);
    const uint8_t* row = &img.px[(size_t)y * img.w * img.ch];
    raw.insert(raw.end(), row, row + (size_t)img.w * img.ch);
  }
  putChunk(out, "IDAT", zlibCompress(raw, 9));
  putChunk(out, "IEND", {});
  return out;
}

static uint32_t rdBE32(const uint8_t* p) { return ((uint32_t)p[0] << 24) | (p[1] << 16) | (p[2] << 8) | p[3]; }

bool pngDecode(const std::vector<uint8_t>& data, Image& out) {
  static const uint8_t sig[8] = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  if (data.size() < 8 || std::memcmp(data.data(), sig, 8) != 0) return false;
  size_t pos = 8;
  int w = 0, h = 0, bitDepth = 8, colorType = 0, interlace = 0;
  std::vector<uint8_t> idat, plte;
  while (pos + 8 <= data.size()) {
    uint32_t len = rdBE32(&data[pos]);
    std::string type((const char*)&data[pos + 4], 4);
    if (pos + 12 + len > data.size()) return false;
    const uint8_t* d = &data[pos + 8];
    if (type == "IHDR" && len >= 13) {
      w = (int)rdBE32(d); h = (int)rdBE32(d + 4);
      bitDepth = d[8]; colorType = d[9]; interlace = d[12];
    } else if (type == "PLTE") {
      plte.assign(d, d + len);
    } else if (type == "IDAT") {
      idat.insert(idat.end(), d, d + len);
    } else if (type == "IEND") break;
    pos += 12 + len;
  }
  if (w <= 0 || h <= 0 || bitDepth != 8 || interlace != 0) return false;
  std::vector<uint8_t> raw;
  if (!zlibDecompress(idat.data(), idat.size(), raw)) return false;
  int channels = colorType == 2 ? 3 : (colorType == 6 ? 4 : (colorType == 4 ? 2 : (colorType == 3 ? 1 : 1)));
  size_t stride = (size_t)w * channels;
  if (raw.size() < (stride + 1) * h) return false;
  std::vector<uint8_t> recon(stride * h);
  std::vector<uint8_t> prev(stride, 0);
  for (int y = 0; y < h; y++) {
    uint8_t filter = raw[(stride + 1) * y];
    const uint8_t* src = &raw[(stride + 1) * y + 1];
    uint8_t* dst = &recon[stride * y];
    for (size_t x = 0; x < stride; x++) {
      int a = x >= (size_t)channels ? dst[x - channels] : 0;
      int b = prev[x];
      int c = x >= (size_t)channels ? prev[x - channels] : 0;
      int v = src[x];
      switch (filter) {
        case 0: break;
        case 1: v += a; break;
        case 2: v += b; break;
        case 3: v += (a + b) / 2; break;
        case 4: {
          int pp = a + b - c;
          int pa = std::abs(pp - a), pb = std::abs(pp - b), pc = std::abs(pp - c);
          v += (pa <= pb && pa <= pc) ? a : (pb <= pc ? b : c);
          break;
        }
        default: return false;
      }
      dst[x] = (uint8_t)v;
    }
    prev.assign(dst, dst + stride);
  }
  out = Image(w, h, channels == 1 || channels == 2 ? 1 : 3);
  for (int y = 0; y < h; y++) {
    for (int x = 0; x < w; x++) {
      const uint8_t* s = &recon[stride * y + (size_t)x * channels];
      if (channels == 1) out.px[(size_t)y * w + x] = s[0];
      else if (channels == 2) out.px[(size_t)y * w + x] = s[0];
      else if (channels == 3) { out.px[((size_t)y * w + x) * 3 + 0] = s[0]; out.px[((size_t)y * w + x) * 3 + 1] = s[1]; out.px[((size_t)y * w + x) * 3 + 2] = s[2]; }
      else {
        // RGBA → złożenie na biel
        int a = s[3];
        for (int k = 0; k < 3; k++) out.px[((size_t)y * w + x) * 3 + k] = (uint8_t)((s[k] * a + 255 * (255 - a)) / 255);
      }
    }
  }
  return true;
}

// ---------------- BMP ----------------
static void putLE16(std::vector<uint8_t>& v, uint16_t x) { v.push_back((uint8_t)x); v.push_back((uint8_t)(x >> 8)); }
static void putLE32(std::vector<uint8_t>& v, uint32_t x) {
  v.push_back((uint8_t)x); v.push_back((uint8_t)(x >> 8));
  v.push_back((uint8_t)(x >> 16)); v.push_back((uint8_t)(x >> 24));
}
std::vector<uint8_t> bmpEncode(const Image& img) {
  Image rgb = img.toRGB();
  int rowSize = (rgb.w * 3 + 3) & ~3;
  uint32_t dataSize = (uint32_t)rowSize * rgb.h;
  std::vector<uint8_t> out;
  out.push_back('B'); out.push_back('M');
  putLE32(out, 54 + dataSize);
  putLE16(out, 0); putLE16(out, 0);
  putLE32(out, 54);
  putLE32(out, 40);
  putLE32(out, (uint32_t)rgb.w);
  putLE32(out, (uint32_t)rgb.h);
  putLE16(out, 1);
  putLE16(out, 24);
  putLE32(out, 0);
  putLE32(out, dataSize);
  putLE32(out, 2835); putLE32(out, 2835);
  putLE32(out, 0); putLE32(out, 0);
  for (int y = rgb.h - 1; y >= 0; y--) {
    for (int x = 0; x < rgb.w; x++) {
      const uint8_t* p = rgb.at(x, y);
      out.push_back(p[2]); out.push_back(p[1]); out.push_back(p[0]);
    }
    for (int k = rgb.w * 3; k < rowSize; k++) out.push_back(0);
  }
  return out;
}
bool bmpDecode(const std::vector<uint8_t>& d, Image& out) {
  if (d.size() < 54 || d[0] != 'B' || d[1] != 'M') return false;
  uint32_t off = d[10] | (d[11] << 8) | (d[12] << 16) | ((uint32_t)d[13] << 24);
  int w = (int)(d[18] | (d[19] << 8) | (d[20] << 16) | ((uint32_t)d[21] << 24));
  int h = (int)(d[22] | (d[23] << 8) | (d[24] << 16) | ((uint32_t)d[25] << 24));
  int bpp = d[28] | (d[29] << 8);
  bool topDown = h < 0;
  if (topDown) h = -h;
  if (w <= 0 || h <= 0 || (bpp != 24 && bpp != 32 && bpp != 8)) return false;
  int rowSize = ((w * bpp / 8) + 3) & ~3;
  if (off + (size_t)rowSize * h > d.size()) return false;
  out = Image(w, h, 3);
  const uint8_t* pal = nullptr;
  int palCount = 0;
  if (bpp == 8) { pal = d.data() + 54; palCount = (off - 54) / 4; }
  for (int y = 0; y < h; y++) {
    int sy = topDown ? y : h - 1 - y;
    const uint8_t* row = d.data() + off + (size_t)sy * rowSize;
    for (int x = 0; x < w; x++) {
      uint8_t* o = out.at(x, y);
      if (bpp == 8) {
        int idx = row[x];
        if (pal && idx < palCount) { o[0] = pal[idx * 4 + 2]; o[1] = pal[idx * 4 + 1]; o[2] = pal[idx * 4]; }
      } else {
        const uint8_t* p = row + x * (bpp / 8);
        o[0] = p[2]; o[1] = p[1]; o[2] = p[0];
      }
    }
  }
  return true;
}

// ---------------- operacje ----------------
Image imgResize(const Image& src, int w, int h) {
  Image o(w, h, src.ch);
  if (!src.valid() || w <= 0 || h <= 0) return o;
  for (int y = 0; y < h; y++) {
    double sy = (y + 0.5) * src.h / h - 0.5;
    int y0 = (int)std::floor(sy), fy = (int)((sy - y0) * 256);
    y0 = std::clamp(y0, 0, src.h - 1);
    int y1 = std::clamp(y0 + 1, 0, src.h - 1);
    for (int x = 0; x < w; x++) {
      double sx = (x + 0.5) * src.w / w - 0.5;
      int x0 = (int)std::floor(sx), fx = (int)((sx - x0) * 256);
      x0 = std::clamp(x0, 0, src.w - 1);
      int x1 = std::clamp(x0 + 1, 0, src.w - 1);
      for (int c = 0; c < src.ch; c++) {
        int p00 = src.at(x0, y0)[c], p10 = src.at(x1, y0)[c], p01 = src.at(x0, y1)[c], p11 = src.at(x1, y1)[c];
        int top = (p00 * (256 - fx) + p10 * fx) >> 8;
        int bot = (p01 * (256 - fx) + p11 * fx) >> 8;
        o.at(x, y)[c] = (uint8_t)((top * (256 - fy) + bot * fy) >> 8);
      }
    }
  }
  return o;
}
Image imgCrop(const Image& src, int x, int y, int w, int h) {
  Image o(std::max(1, w), std::max(1, h), src.ch, 255);
  for (int yy = 0; yy < o.h; yy++)
    for (int xx = 0; xx < o.w; xx++) {
      int sx = x + xx, sy = y + yy;
      if (sx < 0 || sy < 0 || sx >= src.w || sy >= src.h) continue;
      std::memcpy(o.at(xx, yy), src.at(sx, sy), src.ch);
    }
  return o;
}
Image imgGrayscale(const Image& src) { return src.toGray(); }
Image imgThreshold(const Image& src, int thr) {
  Image g = src.toGray();
  Image o(g.w, g.h, 1);
  for (size_t i = 0; i < g.px.size(); i++) o.px[i] = g.px[i] < thr ? 0 : 255;
  return o;
}
Image imgDither(const Image& src, int thr, const std::string& algo) {
  Image g = src.toGray();
  Image o = g;
  if (algo == "none") return imgThreshold(g, thr);
  if (algo == "atkinson") {
    // Atkinson — rozrzut 6/8 błędu
    std::vector<float> buf(g.w * g.h);
    for (int i = 0; i < g.w * g.h; i++) buf[i] = (float)g.px[i];
    for (int y = 0; y < g.h; y++)
      for (int x = 0; x < g.w; x++) {
        float old = buf[(size_t)y * g.w + x];
        float nv = old < 128 ? 0.0f : 255.0f;
        o.px[(size_t)y * g.w + x] = (uint8_t)nv;
        float err = (old - nv) / 8.0f;
        auto add = [&](int dx, int dy) {
          int nx = x + dx, ny = y + dy;
          if (nx >= 0 && ny >= 0 && nx < g.w && ny < g.h) buf[(size_t)ny * g.w + nx] += err;
        };
        add(1, 0); add(2, 0); add(-1, 1); add(0, 1); add(1, 1); add(0, 2);
      }
    return o;
  }
  // Floyd–Steinberg
  std::vector<float> buf(g.w * g.h);
  for (int i = 0; i < g.w * g.h; i++) buf[i] = (float)g.px[i];
  for (int y = 0; y < g.h; y++)
    for (int x = 0; x < g.w; x++) {
      float old = buf[(size_t)y * g.w + x];
      float nv = old < 128 ? 0.0f : 255.0f;
      o.px[(size_t)y * g.w + x] = (uint8_t)nv;
      float err = old - nv;
      auto add = [&](int dx, int dy, float f) {
        int nx = x + dx, ny = y + dy;
        if (nx >= 0 && ny >= 0 && nx < g.w && ny < g.h) buf[(size_t)ny * g.w + nx] += err * f;
      };
      add(1, 0, 7.0f / 16); add(-1, 1, 3.0f / 16); add(0, 1, 5.0f / 16); add(1, 1, 1.0f / 16);
    }
  return o;
}
Image imgBrightnessContrast(const Image& src, double brightness, double contrast) {
  Image o = src;
  double c = (259.0 * (contrast + 255.0)) / (255.0 * (259.0 - contrast));
  for (size_t i = 0; i < o.px.size(); i++) {
    double v = o.px[i] + brightness;
    v = c * (v - 128) + 128;
    o.px[i] = (uint8_t)std::clamp(v, 0.0, 255.0);
  }
  return o;
}
Image imgGamma(const Image& src, double gamma) {
  Image o = src;
  for (size_t i = 0; i < o.px.size(); i++) {
    double v = std::pow(o.px[i] / 255.0, 1.0 / std::max(0.05, gamma)) * 255.0;
    o.px[i] = (uint8_t)std::clamp(v, 0.0, 255.0);
  }
  return o;
}
Image imgBlur(const Image& src, int radius) {
  if (radius <= 0) return src;
  Image g = src.toGray();
  Image tmp = g, o = g;
  // poziomo
  for (int y = 0; y < g.h; y++)
    for (int x = 0; x < g.w; x++) {
      int sum = 0, cnt = 0;
      for (int k = -radius; k <= radius; k++) {
        int xx = std::clamp(x + k, 0, g.w - 1);
        sum += g.at(xx, y)[0];
        cnt++;
      }
      tmp.at(x, y)[0] = (uint8_t)(sum / cnt);
    }
  for (int y = 0; y < g.h; y++)
    for (int x = 0; x < g.w; x++) {
      int sum = 0, cnt = 0;
      for (int k = -radius; k <= radius; k++) {
        int yy = std::clamp(y + k, 0, g.h - 1);
        sum += tmp.at(x, yy)[0];
        cnt++;
      }
      o.at(x, y)[0] = (uint8_t)(sum / cnt);
    }
  return o;
}
Image imgSharpen(const Image& src, double amount) {
  Image g = src.toGray();
  Image b = imgBlur(g, 1);
  Image o = g;
  for (size_t i = 0; i < o.px.size(); i++) {
    double v = g.px[i] + amount * (g.px[i] - b.px[i]);
    o.px[i] = (uint8_t)std::clamp(v, 0.0, 255.0);
  }
  return o;
}
Image imgSobel(const Image& src, int thr) {
  Image g = src.toGray();
  Image o(g.w, g.h, 1, 255);
  for (int y = 1; y < g.h - 1; y++)
    for (int x = 1; x < g.w - 1; x++) {
      int gx = -g.gray(x - 1, y - 1) - 2 * g.gray(x - 1, y) - g.gray(x - 1, y + 1) +
               g.gray(x + 1, y - 1) + 2 * g.gray(x + 1, y) + g.gray(x + 1, y + 1);
      int gy = -g.gray(x - 1, y - 1) - 2 * g.gray(x, y - 1) - g.gray(x + 1, y - 1) +
               g.gray(x - 1, y + 1) + 2 * g.gray(x, y + 1) + g.gray(x + 1, y + 1);
      int mag = (int)std::sqrt((double)gx * gx + (double)gy * gy);
      o.at(x, y)[0] = mag > thr ? 0 : 255;
    }
  return o;
}
Image imgInvert(const Image& src) {
  Image o = src;
  for (auto& p : o.px) p = (uint8_t)(255 - p);
  return o;
}
Image imgMedian(const Image& src) {
  Image g = src.toGray();
  Image o = g;
  int hist[256];
  for (int y = 1; y < g.h - 1; y++)
    for (int x = 1; x < g.w - 1; x++) {
      std::memset(hist, 0, sizeof hist);
      for (int dy = -1; dy <= 1; dy++)
        for (int dx = -1; dx <= 1; dx++) hist[g.gray(x + dx, y + dy)]++;
      int cum = 0, med = 0;
      for (int v = 0; v < 256; v++) { cum += hist[v]; if (cum >= 5) { med = v; break; } }
      o.at(x, y)[0] = (uint8_t)med;
    }
  return o;
}
int imgEstimateBackground(const Image& src) {
  Image g = src.toGray();
  int hist[256] = {0};
  int n = 0;
  for (int x = 0; x < g.w; x++) { hist[g.gray(x, 0)]++; hist[g.gray(x, g.h - 1)]++; n += 2; }
  for (int y = 0; y < g.h; y++) { hist[g.gray(0, y)]++; hist[g.gray(g.w - 1, y)]++; n += 2; }
  int cum = 0, med = 128;
  for (int v = 0; v < 256; v++) { cum += hist[v]; if (cum * 2 >= n) { med = v; break; } }
  return med;
}
Image imgRemoveBackground(const Image& src, int tolerance) {
  Image g = src.toGray();
  int bg = imgEstimateBackground(g);
  Image o = g;
  std::vector<char> seen((size_t)g.w * g.h, 0);
  std::vector<std::pair<int, int>> stack;
  for (int x = 0; x < g.w; x++) { stack.push_back({x, 0}); stack.push_back({x, g.h - 1}); }
  for (int y = 0; y < g.h; y++) { stack.push_back({0, y}); stack.push_back({g.w - 1, y}); }
  while (!stack.empty()) {
    auto [x, y] = stack.back();
    stack.pop_back();
    if (x < 0 || y < 0 || x >= g.w || y >= g.h) continue;
    size_t idx = (size_t)y * g.w + x;
    if (seen[idx]) continue;
    if (std::abs((int)g.px[idx] - bg) > tolerance) continue;
    seen[idx] = 1;
    o.px[idx] = 255;
    stack.push_back({x + 1, y}); stack.push_back({x - 1, y});
    stack.push_back({x, y + 1}); stack.push_back({x, y - 1});
  }
  return o;
}
Image imgFitBox(const Image& src, int tw, int th, bool keepAspect, bool padWhite) {
  int w = tw, h = th;
  if (keepAspect) {
    double s = std::min((double)tw / src.w, (double)th / src.h);
    w = std::max(1, (int)(src.w * s));
    h = std::max(1, (int)(src.h * s));
  }
  Image r = imgResize(src, w, h);
  if (!padWhite || (w == tw && h == th)) return r;
  Image o(tw, th, r.ch, 255);
  int ox = (tw - w) / 2, oy = (th - h) / 2;
  for (int y = 0; y < h; y++)
    for (int x = 0; x < w; x++)
      if (ox + x >= 0 && oy + y >= 0 && ox + x < tw && oy + y < th)
        std::memcpy(o.at(ox + x, oy + y), r.at(x, y), r.ch);
  return o;
}
Image imgFromGrayRaw(const uint8_t* data, int w, int h) {
  Image o(w, h, 1);
  std::memcpy(o.px.data(), data, (size_t)w * h);
  return o;
}
Image imgFromRGBRaw(const uint8_t* data, int w, int h) {
  Image o(w, h, 3);
  std::memcpy(o.px.data(), data, (size_t)w * h * 3);
  return o;
}

// ---------------- rasteryzator ----------------
void Canvas::fillContours(const std::vector<Contour>& cs, bool evenOdd, double value) {
  if (cs.empty()) return;
  Rect bb;
  bool first = true;
  for (auto& c : cs) bb = first ? c.bounds() : bb.unite(c.bounds()), first = false;
  int x0 = std::clamp((int)std::floor(bb.x) - 1, 0, img_.w - 1);
  int x1 = std::clamp((int)std::ceil(bb.x1()) + 1, 0, img_.w - 1);
  int y0 = std::clamp((int)std::floor(bb.y) - 1, 0, img_.h - 1);
  int y1 = std::clamp((int)std::ceil(bb.y1()) + 1, 0, img_.h - 1);
  const int SS = 4;
  std::vector<float> cov(std::max(1, x1 - x0 + 2), 0.0f);
  std::vector<std::pair<double, int>> xs;
  for (int y = y0; y <= y1; y++) {
    std::fill(cov.begin(), cov.end(), 0.0f);
    for (int s = 0; s < SS; s++) {
      double sy = y + (s + 0.5) / SS;
      xs.clear();
      for (auto& c : cs) {
        size_t n = c.pts.size();
        for (size_t i = 0; i < n; i++) {
          const Vec2& a = c.pts[i];
          const Vec2& b = c.pts[(i + 1) % n];
          if ((a.y <= sy && b.y > sy) || (b.y <= sy && a.y > sy)) {
            double t = (sy - a.y) / (b.y - a.y);
            xs.push_back({a.x + t * (b.x - a.x), (b.y > a.y) ? 1 : -1});
          }
        }
      }
      if (xs.empty()) continue;
      std::sort(xs.begin(), xs.end());
      int wind = 0;
      for (size_t i = 0; i + 1 < xs.size(); i++) {
        wind += xs[i].second;
        bool inside = evenOdd ? (wind % 2 != 0) : (wind != 0);
        if (!inside) continue;
        double xa = xs[i].first, xb = xs[i + 1].first;
        if (xb <= xa) continue;
        int ia = (int)std::floor(xa) - x0, ib = (int)std::floor(xb) - x0;
        for (int ix = ia; ix <= ib; ix++) {
          if (ix < 0 || ix >= (int)cov.size()) continue;
          double px0 = x0 + ix, px1 = px0 + 1;
          double ov = std::min(px1, xb) - std::max(px0, xa);
          if (ov > 0) cov[ix] += (float)(ov / SS);
        }
      }
    }
    uint8_t* row = nullptr;
    for (int ix = 0; ix < (int)cov.size(); ix++) {
      if (cov[ix] <= 0.001f) continue;
      int px = x0 + ix;
      if (px < 0 || px >= img_.w) continue;
      row = img_.at(px, y);
      double a = std::clamp((double)cov[ix], 0.0, 1.0);
      row[0] = (uint8_t)std::clamp(row[0] * (1 - a) + value * a, 0.0, 255.0);
    }
  }
}

void Canvas::strokePath(const Path& p, double widthMm, double pxPerMm, double value) {
  // kontury w pikselach; widthMm przeliczane przez pxPerMm
  double halfW = std::max(0.35, widthMm * pxPerMm / 2.0);
  auto contours = p.flatten(0.08);
  for (auto& c : contours) {
    size_t n = c.pts.size();
    size_t segs = c.closed ? n : (n > 1 ? n - 1 : 0);
    for (size_t i = 0; i < segs; i++) {
      Vec2 a = c.pts[i], b = c.pts[(i + 1) % n];
      int x0 = std::clamp((int)std::floor(std::min(a.x, b.x) - halfW - 1), 0, img_.w - 1);
      int x1 = std::clamp((int)std::ceil(std::max(a.x, b.x) + halfW + 1), 0, img_.w - 1);
      int y0 = std::clamp((int)std::floor(std::min(a.y, b.y) - halfW - 1), 0, img_.h - 1);
      int y1 = std::clamp((int)std::ceil(std::max(a.y, b.y) + halfW + 1), 0, img_.h - 1);
      Vec2 ab = b - a;
      double abLen2 = std::max(1e-9, ab.dot(ab));
      for (int y = y0; y <= y1; y++) {
        for (int x = x0; x <= x1; x++) {
          Vec2 pt(x + 0.5, y + 0.5);
          double t = std::clamp((pt - a).dot(ab) / abLen2, 0.0, 1.0);
          Vec2 proj = a + ab * t;
          double d = (pt - proj).len();
          double a_ = std::clamp(halfW + 0.5 - d, 0.0, 1.0);
          if (a_ <= 0.01) continue;
          uint8_t* row = img_.at(x, y);
          row[0] = (uint8_t)std::clamp(row[0] * (1 - a_) + value * a_, 0.0, 255.0);
        }
      }
    }
  }
}

void Canvas::drawImageGray(const Image& src, const Rect& dstPx, double /*pxPerMm*/, int threshold,
                           const std::string& dither, double opacity) {
  if (!src.valid() || dstPx.w <= 0 || dstPx.h <= 0) return;
  int dw = std::max(1, (int)std::lround(dstPx.w));
  int dh = std::max(1, (int)std::lround(dstPx.h));
  Image scaled = imgResize(src.toGray(), dw, dh);
  if (dither != "none") scaled = imgDither(scaled, threshold, dither);
  else if (threshold >= 0) scaled = imgThreshold(scaled, threshold);
  int ox = (int)std::lround(dstPx.x), oy = (int)std::lround(dstPx.y);
  for (int y = 0; y < dh; y++) {
    int py = oy + y;
    if (py < 0 || py >= img_.h) continue;
    for (int x = 0; x < dw; x++) {
      int px = ox + x;
      if (px < 0 || px >= img_.w) continue;
      double v = scaled.at(x, y)[0];
      double a = opacity * (1.0 - v / 255.0);
      uint8_t* row = img_.at(px, py);
      row[0] = (uint8_t)std::clamp(row[0] * (1 - a), 0.0, 255.0);
    }
  }
}

}  // namespace lc
