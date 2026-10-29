// Generator kodów QR (tryb bajtowy, wersje 1–10, poziomy korekcji L/M/Q/H)
// Implementacja wg ISO/IEC 18004: kodowanie danych → Reed–Solomon → przeplot →
// macierz funkcyjna → wzór masek (kara) → informacja formatu. Odczyt zwrotny
// (qrDecode) służy do wewnętrznego testu round-trip; niezależna weryfikacja
// zewnętrzna: dekodery ZXing / OpenCV.
#include "qr.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <cstdio>
#include <cstdlib>

namespace lc {
namespace {

struct VersionEcc {
  int totalCodewords;
  int eccPerBlock;
  int b1, d1;  // bloki grupy 1: liczba i długość danych
  int b2, d2;  // bloki grupy 2 (może być 0)
};

// Tabela ISO 18004 (wersje 1–10, poziomy L M Q H)
VersionEcc eccSpec(int ver, char ecc) {
  static const VersionEcc tbl[10][4] = {
      {{26, 7, 1, 19, 0, 0}, {26, 10, 1, 16, 0, 0}, {26, 13, 1, 13, 0, 0}, {26, 17, 1, 9, 0, 0}},
      {{44, 10, 1, 34, 0, 0}, {44, 16, 1, 28, 0, 0}, {44, 22, 1, 22, 0, 0}, {44, 28, 1, 16, 0, 0}},
      {{70, 15, 1, 55, 0, 0}, {70, 26, 1, 44, 0, 0}, {70, 18, 2, 17, 0, 0}, {70, 22, 2, 13, 0, 0}},
      {{100, 20, 1, 80, 0, 0}, {100, 18, 2, 32, 0, 0}, {100, 26, 2, 24, 0, 0}, {100, 16, 4, 9, 0, 0}},
      {{134, 26, 1, 108, 0, 0}, {134, 24, 2, 43, 0, 0}, {134, 18, 2, 15, 2, 16}, {134, 22, 2, 11, 2, 12}},
      {{172, 18, 2, 68, 0, 0}, {172, 16, 4, 27, 0, 0}, {172, 24, 4, 19, 0, 0}, {172, 28, 4, 15, 0, 0}},
      {{196, 20, 2, 78, 0, 0}, {196, 18, 4, 31, 0, 0}, {196, 18, 2, 14, 4, 15}, {196, 26, 4, 13, 1, 14}},
      {{242, 24, 2, 97, 0, 0}, {242, 22, 2, 38, 2, 39}, {242, 22, 4, 18, 2, 19}, {242, 26, 4, 14, 2, 15}},
      {{292, 30, 2, 116, 0, 0}, {292, 22, 3, 36, 2, 37}, {292, 20, 4, 16, 4, 17}, {292, 24, 4, 12, 4, 13}},
      {{346, 18, 2, 68, 2, 69}, {346, 26, 4, 43, 1, 44}, {346, 24, 6, 19, 2, 20}, {346, 28, 6, 15, 2, 16}},
  };
  int idx = (ecc == 'L') ? 0 : (ecc == 'M') ? 1 : (ecc == 'Q') ? 2 : 3;
  return tbl[std::clamp(ver, 1, 10) - 1][idx];
}

int dataCodewords(int ver, char ecc) {
  VersionEcc s = eccSpec(ver, ecc);
  return s.b1 * s.d1 + s.b2 * s.d2;
}

// ---- GF(256) ----
uint8_t gfMul(uint8_t a, uint8_t b) {
  int r = 0;
  while (b) {
    if (b & 1) r ^= a;
    b >>= 1;
    a = (uint8_t)((a << 1) ^ ((a & 0x80) ? 0x1D : 0));
  }
  return (uint8_t)r;
}

std::vector<uint8_t> rsGenerator(int degree) {
  std::vector<uint8_t> g(degree, 0);
  g[degree - 1] = 1;
  uint8_t root = 1;
  for (int i = 0; i < degree; i++) {
    for (int j = 0; j < degree; j++) {
      g[j] = gfMul(g[j], root);
      if (j + 1 < degree) g[j] ^= g[j + 1];
    }
    root = gfMul(root, 2);
  }
  return g;
}

std::vector<uint8_t> rsRemainder(const std::vector<uint8_t>& data, const std::vector<uint8_t>& gen) {
  std::vector<uint8_t> res(gen.size(), 0);
  for (uint8_t b : data) {
    uint8_t factor = b ^ res[0];
    res.erase(res.begin());
    res.push_back(0);
    for (size_t i = 0; i < gen.size(); i++) res[i] ^= gfMul(gen[i], factor);
  }
  return res;
}

// ---- macierz ----
struct Mat {
  int n = 0;
  std::vector<uint8_t> m, fn;
  void init(int size) {
    n = size;
    m.assign((size_t)size * size, 0);
    fn.assign((size_t)size * size, 0);
  }
  bool get(int x, int y) const { return x >= 0 && y >= 0 && x < n && y < n && m[(size_t)y * n + x]; }
  bool isFunc(int x, int y) const { return fn[(size_t)y * n + x] != 0; }
  void set(int x, int y, bool dark) {
    if (x < 0 || y < 0 || x >= n || y >= n) return;
    m[(size_t)y * n + x] = dark ? 1 : 0;
  }
  void setFunc(int x, int y, bool dark) {
    set(x, y, dark);
    fn[(size_t)y * n + x] = 1;
  }
};

int alignmentStep(int ver) {
  if (ver <= 1) return 0;
  return (ver == 32) ? 26 : (ver * 4 + 4) / 2 / 2 * 2;
}

std::vector<int> alignmentPositions(int ver) {
  if (ver == 1) return {};
  int num = ver / 7 + 2;
  int step = (ver == 32) ? 26 : ((ver * 4 + num * 2 + 1) / (num * 2 - 2)) * 2;
  std::vector<int> res(num);
  res[0] = 6;
  for (int i = num - 1, pos = ver * 4 + 10; i >= 1; i--, pos -= step) res[i] = pos;
  return res;
}

void drawFunctionPatterns(Mat& qr, int ver, int mask) {
  int size = qr.n;
  // wzorce pozycjonujące + separatory
  for (int i = 0; i < size; i++) {
    qr.setFunc(6, i, i % 2 == 0);
    qr.setFunc(i, 6, i % 2 == 0);
  }
  auto finder = [&](int cx, int cy) {
    for (int dy = -4; dy <= 4; dy++)
      for (int dx = -4; dx <= 4; dx++) {
        int dist = std::max(std::abs(dx), std::abs(dy));
        int x = cx + dx, y = cy + dy;
        if (x < 0 || y < 0 || x >= size || y >= size) continue;
        qr.setFunc(x, y, dist != 2 && dist != 4);
      }
  };
  finder(3, 3);
  finder(size - 4, 3);
  finder(3, size - 4);
  // wzorce dopasowania (alignment)
  auto pos = alignmentPositions(ver);
  for (size_t i = 0; i < pos.size(); i++)
    for (size_t j = 0; j < pos.size(); j++) {
      bool skip = (i == 0 && j == 0) || (i == 0 && j == pos.size() - 1) || (i == pos.size() - 1 && j == 0);
      if (skip) continue;
      for (int dy = -2; dy <= 2; dy++)
        for (int dx = -2; dx <= 2; dx++)
          qr.setFunc(pos[j] + dx, pos[i] + dy, std::max(std::abs(dx), std::abs(dy)) != 1);
    }
  // informacja formatu (wzory rezerwujemy z aktualną maską)
  int data = ((ver >= 0) ? 0 : 0);
  (void)data;
  // informacja wersji (>=7)
  if (ver >= 7) {
    int rem = ver;
    for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
    int bits = (ver << 12) | rem;
    for (int i = 0; i < 18; i++) {
      bool bit = ((bits >> i) & 1) != 0;
      int a = size - 11 + i % 3, b = i / 3;
      qr.setFunc(a, b, bit);
      qr.setFunc(b, a, bit);
    }
  }
  (void)mask;
}

void drawFormat(Mat& qr, char ecc, int mask) {
  int size = qr.n;
  int ecl = (ecc == 'L') ? 1 : (ecc == 'M') ? 0 : (ecc == 'Q') ? 3 : 2;
  int data = (ecl << 3) | mask;
  int rem = data;
  for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
  int bits = ((data << 10) | rem) ^ 0x5412;
  auto bit = [&](int i) { return ((bits >> i) & 1) != 0; };
  for (int i = 0; i <= 5; i++) qr.setFunc(8, i, bit(i));
  qr.setFunc(8, 7, bit(6));
  qr.setFunc(8, 8, bit(7));
  qr.setFunc(7, 8, bit(8));
  for (int i = 9; i < 15; i++) qr.setFunc(14 - i, 8, bit(i));
  for (int i = 0; i < 8; i++) qr.setFunc(size - 1 - i, 8, bit(i));
  for (int i = 8; i < 15; i++) qr.setFunc(8, size - 15 + i, bit(i));
  qr.setFunc(8, size - 8, true);  // moduł ciemny
}

bool maskBit(int mask, int x, int y) {
  switch (mask) {
    case 0: return (x + y) % 2 == 0;
    case 1: return y % 2 == 0;
    case 2: return x % 3 == 0;
    case 3: return (x + y) % 3 == 0;
    case 4: return (x / 3 + y / 2) % 2 == 0;
    case 5: return (x * y) % 2 + (x * y) % 3 == 0;
    case 6: return ((x * y) % 2 + (x * y) % 3) % 2 == 0;
    case 7: return ((x + y) % 2 + (x * y) % 3) % 2 == 0;
  }
  return false;
}

void placeData(Mat& qr, const std::vector<uint8_t>& codewords) {
  int size = qr.n;
  size_t bitIdx = 0;
  size_t totalBits = codewords.size() * 8;
  for (int right = size - 1; right >= 1; right -= 2) {
    if (right == 6) right = 5;
    for (int vert = 0; vert < size; vert++) {
      for (int j = 0; j < 2; j++) {
        int x = right - j;
        bool upward = ((right + 1) & 2) == 0;
        int y = upward ? size - 1 - vert : vert;
        if (qr.isFunc(x, y)) continue;
        bool bit = false;
        if (bitIdx < totalBits) bit = ((codewords[bitIdx >> 3] >> (7 - (bitIdx & 7))) & 1) != 0;
        qr.set(x, y, bit);
        bitIdx++;
      }
    }
  }
}

int penalty(const Mat& qr) {
  int n = qr.n, score = 0;
  // reguła 1: ciągi >= 5
  for (int y = 0; y < n; y++) {
    int run = 1;
    for (int x = 1; x < n; x++) {
      if (qr.get(x, y) == qr.get(x - 1, y)) run++;
      else { if (run >= 5) score += 3 + (run - 5); run = 1; }
    }
    if (run >= 5) score += 3 + (run - 5);
  }
  for (int x = 0; x < n; x++) {
    int run = 1;
    for (int y = 1; y < n; y++) {
      if (qr.get(x, y) == qr.get(x, y - 1)) run++;
      else { if (run >= 5) score += 3 + (run - 5); run = 1; }
    }
    if (run >= 5) score += 3 + (run - 5);
  }
  // reguła 2: bloki 2x2
  for (int y = 0; y + 1 < n; y++)
    for (int x = 0; x + 1 < n; x++) {
      bool v = qr.get(x, y);
      if (v == qr.get(x + 1, y) && v == qr.get(x, y + 1) && v == qr.get(x + 1, y + 1)) score += 3;
    }
  // reguła 3: wzór podobny do wzorca pozycjonującego (1:1:3:1:1 z 4 modułami tła)
  auto hasFinder = [&](int x, int y, int dx, int dy) {
    static const int pat[7] = {1, 0, 1, 1, 1, 0, 1};
    for (int i = 0; i < 7; i++) {
      int xx = x + dx * i, yy = y + dy * i;
      if (xx < 0 || yy < 0 || xx >= n || yy >= n) return false;
      if ((int)qr.get(xx, yy) != pat[i]) return false;
    }
    for (int i = 1; i <= 4; i++) {
      int bx = x - dx * i, by = y - dy * i;
      if (bx >= 0 && by >= 0 && bx < n && by < n && qr.get(bx, by)) return false;
    }
    return true;
  };
  for (int y = 0; y < n; y++)
    for (int x = 0; x < n; x++) {
      if (hasFinder(x, y, 1, 0)) score += 40;
      if (hasFinder(x, y, 0, 1)) score += 40;
      if (hasFinder(x, y + 6, 0, -1)) score += 40;
      if (hasFinder(x + 6, y, -1, 0)) score += 40;
    }
  // reguła 4: balans ciemnych modułów
  int dark = 0;
  for (auto v : qr.m) dark += v;
  int total = n * n;
  int pct = dark * 100 / total;
  score += (std::abs(pct - 50) / 5) * 10;
  return score;
}

}  // namespace

QrCode qrEncode(const std::string& data, char ecc, int minVersion, int forceMask) {
  QrCode out;
  if (ecc != 'L' && ecc != 'M' && ecc != 'Q' && ecc != 'H') ecc = 'M';
  out.ecc = ecc;
  if (data.empty()) {
    out.error = "Brak danych do zakodowania.";
    return out;
  }
  int ver = 0;
  for (int v = std::max(1, minVersion); v <= 10; v++) {
    int countBits = v >= 10 ? 16 : 8;
    long needBits = 4 + countBits + (long)data.size() * 8;
    if (needBits <= (long)dataCodewords(v, ecc) * 8) { ver = v; break; }
  }
  if (ver == 0) {
    out.error = "Dane za długie na wersję 10 (maks. ~" +
                std::to_string((dataCodewords(10, ecc) * 8 - 4 - 16) / 8) + " bajtów).";
    return out;
  }
  out.version = ver;
  int dataCw = dataCodewords(ver, ecc);
  int countBits = ver >= 10 ? 16 : 8;

  // --- strumień bitów danych ---
  std::vector<uint8_t> bits;
  auto pushBits = [&](uint32_t val, int count) {
    for (int i = count - 1; i >= 0; i--) bits.push_back((val >> i) & 1);
  };
  pushBits(0x4, 4);  // tryb bajtowy
  pushBits((uint32_t)data.size(), countBits);
  for (unsigned char c : data) pushBits(c, 8);
  int capacityBits = dataCw * 8;
  int term = std::min(4, capacityBits - (int)bits.size());
  for (int i = 0; i < term; i++) bits.push_back(0);
  while (bits.size() % 8 != 0) bits.push_back(0);
  std::vector<uint8_t> dataCws;
  for (size_t i = 0; i < bits.size(); i += 8) {
    uint8_t b = 0;
    for (int k = 0; k < 8; k++) b = (uint8_t)((b << 1) | bits[i + k]);
    dataCws.push_back(b);
  }
  for (uint8_t pad = 0xEC; dataCws.size() < (size_t)dataCw; pad ^= 0xEC ^ 0x11) dataCws.push_back(pad);

  // --- Reed–Solomon + przeplot ---
  VersionEcc spec = eccSpec(ver, ecc);
  int numBlocks = spec.b1 + spec.b2;
  std::vector<std::vector<uint8_t>> blocks(numBlocks);
  int pos = 0;
  for (int b = 0; b < numBlocks; b++) {
    int len = (b < spec.b1) ? spec.d1 : spec.d2;
    blocks[b].assign(dataCws.begin() + pos, dataCws.begin() + pos + len);
    pos += len;
  }
  auto gen = rsGenerator(spec.eccPerBlock);
  std::vector<std::vector<uint8_t>> eccBlocks(numBlocks);
  int maxData = 0;
  for (int b = 0; b < numBlocks; b++) {
    eccBlocks[b] = rsRemainder(blocks[b], gen);
    maxData = std::max(maxData, (int)blocks[b].size());
  }
  std::vector<uint8_t> finalCw;
  for (int i = 0; i < maxData; i++)
    for (int b = 0; b < numBlocks; b++)
      if (i < (int)blocks[b].size()) finalCw.push_back(blocks[b][i]);
  for (int i = 0; i < spec.eccPerBlock; i++)
    for (int b = 0; b < numBlocks; b++) finalCw.push_back(eccBlocks[b][i]);
  if ((int)finalCw.size() != spec.totalCodewords) {
    out.error = "Niespójna liczba kodów (błąd wewnętrzny).";
    return out;
  }

  // --- macierz + wybór maski ---
  int size = ver * 4 + 17;
  int bestMask = 0;
  int bestScore = 1 << 30;
  std::vector<uint8_t> best;
  int from = forceMask >= 0 ? forceMask : 0;
  int to = forceMask >= 0 ? forceMask : 7;
  for (int mask = from; mask <= to; mask++) {
    Mat qr;
    qr.init(size);
    drawFunctionPatterns(qr, ver, mask);
    drawFormat(qr, ecc, mask);
    placeData(qr, finalCw);
    for (int y = 0; y < size; y++)
      for (int x = 0; x < size; x++)
        if (!qr.isFunc(x, y) && maskBit(mask, x, y)) qr.set(x, y, !qr.get(x, y));
    int sc = penalty(qr);
    if (sc < bestScore) {
      bestScore = sc;
      bestMask = mask;
      best = qr.m;
    }
  }
  out.size = size;
  out.modules = best;
  out.ok = true;
  out.mask = bestMask;
  return out;
}

Path qrPath(const std::string& data, double sizeMm, char ecc) {
  Path p;
  QrCode qr = qrEncode(data, ecc);
  if (!qr.ok || qr.size <= 0) return p;
  const int quiet = 4;
  int total = qr.size + quiet * 2;
  double mm = sizeMm / total;
  for (int y = 0; y < qr.size; y++) {
    int x = 0;
    while (x < qr.size) {
      if (!qr.at(x, y)) { x++; continue; }
      int start = x;
      while (x < qr.size && qr.at(x, y)) x++;
      p.addRect((start + quiet) * mm, (y + quiet) * mm, (x - start) * mm, mm);
    }
  }
  return p;
}

std::string qrSelfCheck(const QrCode& qr) {
  if (!qr.ok) return "QR: błąd — " + qr.error;
  if (qr.size < 21 || qr.size > 57 || (qr.size - 17) % 4 != 0)
    return "QR: nieprawidłowy rozmiar matrycy";
  int ver = (qr.size - 17) / 4;
  int size = qr.size;
  // trzy wzorce pozycjonujące 7x7 + separator
  const int cx[3] = {3, size - 4, 3};
  const int cy[3] = {3, 3, size - 4};
  for (int p = 0; p < 3; p++)
    for (int dy = -3; dy <= 3; dy++)
      for (int dx = -3; dx <= 3; dx++) {
        int d = std::max(std::abs(dx), std::abs(dy));
        int expected = (d == 2) ? 0 : 1;
        if (qr.at(cx[p] + dx, cy[p] + dy) != expected) return "QR: uszkodzony wzorzec pozycjonujący";
      }
  // wzór synchronizacji
  for (int i = 8; i < size - 8; i++) {
    int exp = (i % 2 == 0) ? 1 : 0;
    if (qr.at(i, 6) != exp || qr.at(6, i) != exp) return "QR: uszkodzony wzór synchronizacji";
  }
  // moduł ciemny
  if (!qr.at(8, size - 8)) return "QR: brak modułu ciemnego";
  // informacja formatu: odczyt + kontrola BCH
  int bits = 0;
  for (int i = 0; i <= 5; i++) bits |= (qr.at(8, i) ? 1 : 0) << i;
  bits |= (qr.at(8, 7) ? 1 : 0) << 6;
  bits |= (qr.at(8, 8) ? 1 : 0) << 7;
  bits |= (qr.at(7, 8) ? 1 : 0) << 8;
  for (int i = 9; i < 15; i++) bits |= (qr.at(14 - i, 8) ? 1 : 0) << i;
  bits ^= 0x5412;
  int dataBits = bits >> 10;
  int rem = dataBits;
  for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
  if (((dataBits << 10) | rem) != bits) return "QR: uszkodzona informacja formatu";
  int ecl = dataBits >> 3;
  int eccBits = (ecl == 1) ? 'L' : (ecl == 0) ? 'M' : (ecl == 3) ? 'Q' : 'H';
  if (eccBits != qr.ecc) return "QR: poziom korekcji niezgodny z informacją formatu";
  return "QR: OK (wersja " + std::to_string(ver) + ", poziom " + qr.ecc + ", modów " +
         std::to_string(size) + ", maska " + std::to_string(bits & 7) + ")";
}

bool qrDecode(const QrCode& qr, std::string& out) {
  out.clear();
  if (!qr.ok) return false;
  int ver = (qr.size - 17) / 4;
  if (ver < 1 || ver > 10) return false;
  int size = qr.size;
  // odczyt maski z informacji formatu
  int bits = 0;
  for (int i = 0; i <= 5; i++) bits |= (qr.at(8, i) ? 1 : 0) << i;
  bits |= (qr.at(8, 7) ? 1 : 0) << 6;
  bits |= (qr.at(8, 8) ? 1 : 0) << 7;
  bits |= (qr.at(7, 8) ? 1 : 0) << 8;
  for (int i = 9; i < 15; i++) bits |= (qr.at(14 - i, 8) ? 1 : 0) << i;
  int mask = ((bits ^ 0x5412) >> 10) & 7;
  if (getenv("LC_QR_DEBUG")) fprintf(stderr, "raw bits=%d -> data=%d mask=%d\n", bits, bits ^ 0x5412, mask);

  // odtwórz mapę modułów funkcyjnych i zdejmij maskę
  Mat qr2;
  qr2.init(size);
  qr2.m = qr.modules;
  drawFunctionPatterns(qr2, ver, mask);
  drawFormat(qr2, qr.ecc, mask);  // rezerwacja pól informacji formatu
  std::vector<uint8_t> blockBits;
  for (int right = size - 1; right >= 1; right -= 2) {
    if (right == 6) right = 5;
    for (int vert = 0; vert < size; vert++) {
      for (int j = 0; j < 2; j++) {
        int x = right - j;
        bool upward = ((right + 1) & 2) == 0;
        int y = upward ? size - 1 - vert : vert;
        if (qr2.isFunc(x, y)) continue;
        bool v = qr.at(x, y);
        if (maskBit(mask, x, y)) v = !v;
        blockBits.push_back(v ? 1 : 0);
      }
    }
  }
  std::vector<uint8_t> cw;
  for (size_t i = 0; i + 8 <= blockBits.size(); i += 8) {
    uint8_t b = 0;
    for (int k = 0; k < 8; k++) b = (uint8_t)((b << 1) | blockBits[i + k]);
    cw.push_back(b);
  }
  VersionEcc spec = eccSpec(ver, qr.ecc);
  int numBlocks = spec.b1 + spec.b2;
  if ((int)cw.size() < spec.totalCodewords) return false;
  // rozplot
  int maxData = std::max(spec.b1 ? spec.d1 : 0, spec.b2 ? spec.d2 : 0);
  std::vector<std::vector<uint8_t>> blocks(numBlocks);
  size_t idx = 0;
  for (int i = 0; i < maxData; i++)
    for (int b = 0; b < numBlocks; b++)
      if (i < ((b < spec.b1) ? spec.d1 : spec.d2)) blocks[b].push_back(cw[idx++]);
  std::vector<uint8_t> dataCws;
  for (auto& b : blocks) dataCws.insert(dataCws.end(), b.begin(), b.end());
  // parsowanie nagłówka trybu bajtowego
  size_t bitPos = 0;
  auto readBits = [&](int count) -> uint32_t {
    uint32_t v = 0;
    for (int i = 0; i < count; i++) {
      if (bitPos / 8 >= dataCws.size()) return 0xFFFFFFFF;
      v = (v << 1) | ((dataCws[bitPos >> 3] >> (7 - (bitPos & 7))) & 1);
      bitPos++;
    }
    return v;
  };
  uint32_t mode = readBits(4);
  if (mode != 4) { if (getenv("LC_QR_DEBUG")) fprintf(stderr, "mode=%u mask=%d\n", mode, mask); return false; }
  uint32_t len = readBits(ver >= 10 ? 16 : 8);
  if (len == 0xFFFFFFFF || len > dataCws.size()) return false;
  out.reserve(len);
  for (uint32_t i = 0; i < len; i++) out.push_back((char)readBits(8));
  return true;
}

}  // namespace lc
