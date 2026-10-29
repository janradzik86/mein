// Obraz rastrowy: PNG/BMP, korekty fotograficzne, rasteryzator wektorów
#pragma once
#include "path.h"

namespace lc {

struct Image {
  int w = 0, h = 0, ch = 1;  // ch=1 szarość, ch=3 RGB
  std::vector<uint8_t> px;
  Image() = default;
  Image(int w_, int h_, int ch_ = 1, uint8_t fill = 255) : w(w_), h(h_), ch(ch_), px((size_t)w_ * h_ * ch_, fill) {}
  bool valid() const { return w > 0 && h > 0 && px.size() == (size_t)w * h * ch; }
  uint8_t* at(int x, int y) { return &px[((size_t)y * w + x) * ch]; }
  const uint8_t* at(int x, int y) const { return &px[((size_t)y * w + x) * ch]; }
  uint8_t gray(int x, int y) const {
    if (x < 0 || y < 0 || x >= w || y >= h) return 255;
    const uint8_t* p = at(x, y);
    if (ch == 1) return p[0];
    return (uint8_t)((p[0] * 299 + p[1] * 587 + p[2] * 114) / 1000);
  }
  Image toGray() const;
  Image toRGB() const;
};

// PNG
std::vector<uint8_t> pngEncode(const Image& img);
bool pngDecode(const std::vector<uint8_t>& data, Image& out);
// BMP (24-bit, bez kompresji)
std::vector<uint8_t> bmpEncode(const Image& img);
bool bmpDecode(const std::vector<uint8_t>& data, Image& out);

// operacje fotograficzne
Image imgResize(const Image& src, int w, int h);
Image imgCrop(const Image& src, int x, int y, int w, int h);
Image imgGrayscale(const Image& src);
Image imgThreshold(const Image& src, int thr);
Image imgDither(const Image& src, int thr, const std::string& algo);
Image imgBrightnessContrast(const Image& src, double brightness, double contrast);
Image imgGamma(const Image& src, double gamma);
Image imgBlur(const Image& src, int radius);
Image imgSharpen(const Image& src, double amount);
Image imgSobel(const Image& src, int thr);
Image imgInvert(const Image& src);
Image imgMedian(const Image& src);           // usuwanie szumu (3x3)
Image imgRemoveBackground(const Image& src, int tolerance);
Image imgFitBox(const Image& src, int tw, int th, bool keepAspect, bool padWhite);
int imgEstimateBackground(const Image& src);
Image imgFromGrayRaw(const uint8_t* data, int w, int h);
Image imgFromRGBRaw(const uint8_t* data, int w, int h);

// rasteryzacja wektorów (podgląd i eksport rastrowy)
class Canvas {
 public:
  Canvas(int w, int h, uint8_t fill = 255) : img_(w, h, 1, fill) {}
  Image& image() { return img_; }
  const Image& image() const { return img_; }
  void fillContours(const std::vector<Contour>& cs, bool evenOdd, double value);
  void strokePath(const Path& p, double widthMm, double pxPerMm, double value);
  void drawImageGray(const Image& src, const Rect& dstMm, double pxPerMm, int threshold,
                     const std::string& dither, double opacity = 1.0);
 private:
  Image img_;
};

}  // namespace lc
