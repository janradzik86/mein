#include "codec.h"
#include <cstring>

namespace lc {

static uint32_t crcTable[256];
static bool crcInit = false;
static void initCrc() {
  if (crcInit) return;
  for (uint32_t i = 0; i < 256; i++) {
    uint32_t c = i;
    for (int k = 0; k < 8; k++) c = (c & 1) ? 0xEDB88320u ^ (c >> 1) : c >> 1;
    crcTable[i] = c;
  }
  crcInit = true;
}
uint32_t crc32(const uint8_t* data, size_t len, uint32_t crc) {
  initCrc();
  uint32_t c = crc ^ 0xFFFFFFFFu;
  for (size_t i = 0; i < len; i++) c = crcTable[(c ^ data[i]) & 0xFF] ^ (c >> 8);
  return c ^ 0xFFFFFFFFu;
}
static uint32_t adler32(const uint8_t* d, size_t n) {
  uint32_t a = 1, b = 0;
  for (size_t i = 0; i < n; i++) {
    a = (a + d[i]) % 65521;
    b = (b + a) % 65521;
  }
  return (b << 16) | a;
}

// ---------------- DEFLATE (stałe kody Huffmana + LZ77) ----------------
namespace {
struct BitWriter {
  std::vector<uint8_t>& out;
  uint32_t bitbuf = 0;
  int bitcnt = 0;
  explicit BitWriter(std::vector<uint8_t>& o) : out(o) {}
  void put(uint32_t v, int n) {  // LSB-first
    bitbuf |= (v & ((1u << n) - 1)) << bitcnt;
    bitcnt += n;
    while (bitcnt >= 8) {
      out.push_back((uint8_t)(bitbuf & 0xFF));
      bitbuf >>= 8;
      bitcnt -= 8;
    }
  }
  void putRev(uint32_t code, int n) {  // kody Huffmana — MSB-first
    for (int i = n - 1; i >= 0; i--) put((code >> i) & 1, 1);
  }
  void flush() {
    if (bitcnt > 0) { out.push_back((uint8_t)(bitbuf & 0xFF)); bitbuf = 0; bitcnt = 0; }
  }
};

static const int LEN_BASE[] = {3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
                              35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258};
static const int LEN_EXTRA[] = {0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
                                3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0};
static const int DIST_BASE[] = {1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
                                257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145,
                                8193, 12289, 16385, 24577};
static const int DIST_EXTRA[] = {0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
                                 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13};

void putLiteral(BitWriter& bw, int sym) {
  if (sym < 144) bw.putRev(0x30 + sym, 8);
  else if (sym < 256) bw.putRev(0x190 + (sym - 144), 9);
  else if (sym < 280) bw.putRev(sym - 256, 7);
  else bw.putRev(0xC0 + (sym - 280), 8);
}
void putLength(BitWriter& bw, int len) {
  int i = 28;
  while (i > 0 && LEN_BASE[i] > len) i--;
  putLiteral(bw, 257 + i);
  if (LEN_EXTRA[i]) bw.put(len - LEN_BASE[i], LEN_EXTRA[i]);
}
void putDistance(BitWriter& bw, int dist) {
  int i = 29;
  while (i > 0 && DIST_BASE[i] > dist) i--;
  bw.putRev(i, 5);
  if (DIST_EXTRA[i]) bw.put(dist - DIST_BASE[i], DIST_EXTRA[i]);
}
}  // namespace

std::vector<uint8_t> deflateRaw(const std::vector<uint8_t>& raw, int level) {
  std::vector<uint8_t> out;
  const int WIN = 32768, MINM = 3, MAXM = 258;
  BitWriter bw(out);
  bw.put(1, 1);  // BFINAL
  bw.put(1, 2);  // BTYPE = 01 (stałe Huffmana)
  int n = (int)raw.size();
  const int HBITS = level >= 6 ? 15 : 13;
  const int HSIZE = 1 << HBITS;
  std::vector<int> head(HSIZE, -1), prev(n > 0 ? n : 1, -1);
  int i = 0;
  while (i < n) {
    int bestLen = 0, bestDist = 0;
    if (i + MINM <= n) {
      uint32_t h = ((uint32_t)raw[i] << 10 ^ (uint32_t)raw[i + 1] << 5 ^ raw[i + 2]) & (HSIZE - 1);
      int cand = head[h];
      int chain = 0;
      while (cand >= 0 && i - cand <= WIN && chain < 48) {
        int maxl = std::min(MAXM, n - i);
        int l = 0;
        while (l < maxl && raw[cand + l] == raw[i + l]) l++;
        if (l > bestLen) { bestLen = l; bestDist = i - cand; if (l >= maxl) break; }
        cand = prev[cand];
        chain++;
      }
      prev[i] = head[h];
      head[h] = i;
    }
    if (bestLen >= MINM) {
      putLength(bw, bestLen);
      putDistance(bw, bestDist);
      // wstaw pominięte pozycje do łańcucha
      for (int k = 1; k < bestLen; k++) {
        int j = i + k;
        if (j + MINM <= n) {
          uint32_t h = ((uint32_t)raw[j] << 10 ^ (uint32_t)raw[j + 1] << 5 ^ raw[j + 2]) & (HSIZE - 1);
          prev[j] = head[h];
          head[h] = j;
        }
      }
      i += bestLen;
    } else {
      putLiteral(bw, raw[i]);
      i++;
    }
  }
  putLiteral(bw, 256);
  bw.flush();
  return out;
}

std::vector<uint8_t> zlibCompress(const std::vector<uint8_t>& raw, int level) {
  std::vector<uint8_t> body = deflateRaw(raw, level);
  std::vector<uint8_t> out;
  out.push_back(0x78);
  out.push_back(level >= 6 ? 0xDA : 0x9C);
  out.insert(out.end(), body.begin(), body.end());
  uint32_t ad = adler32(raw.data(), raw.size());
  out.push_back((uint8_t)(ad >> 24));
  out.push_back((uint8_t)(ad >> 16));
  out.push_back((uint8_t)(ad >> 8));
  out.push_back((uint8_t)ad);
  return out;
}

// ---------------- INFLATE ----------------
namespace {
struct BitReader {
  const uint8_t* d;
  size_t len, pos = 0;
  uint32_t bitbuf = 0;
  int bitcnt = 0;
  bool err = false;
  BitReader(const uint8_t* data, size_t l) : d(data), len(l) {}
  int bit() {
    if (bitcnt == 0) {
      if (pos >= len) { err = true; return 0; }
      bitbuf = d[pos++];
      bitcnt = 8;
    }
    int b = bitbuf & 1;
    bitbuf >>= 1;
    bitcnt--;
    return b;
  }
  uint32_t bits(int n) {
    uint32_t v = 0;
    for (int i = 0; i < n; i++) v |= (uint32_t)bit() << i;
    return v;
  }
};

struct Huffman {
  std::vector<int> count, symbol;
  bool build(const uint8_t* lengths, int n) {
    count.assign(16, 0);
    symbol.assign(n, 0);
    for (int i = 0; i < n; i++) count[lengths[i]]++;
    if (count[0] == n) return true;
    int left = 1;
    for (int l = 1; l < 16; l++) {
      left <<= 1;
      left -= count[l];
      if (left < 0) return false;
    }
    std::vector<int> offs(16, 0);
    for (int l = 1; l < 15; l++) offs[l + 1] = offs[l] + count[l];
    for (int i = 0; i < n; i++) if (lengths[i]) symbol[offs[lengths[i]]++] = i;
    return true;
  }
  int decode(BitReader& br) const {
    int code = 0, first = 0, index = 0;
    for (int l = 1; l < 16; l++) {
      code |= br.bit();
      int cnt = count[l];
      if (code - first < cnt) return symbol[index + (code - first)];
      index += cnt;
      first = (first + cnt) << 1;
      code <<= 1;
    }
    return -1;
  }
};

bool inflateBlock(BitReader& br, std::vector<uint8_t>& out) {
  int last = 0;
  do {
    last = (int)br.bits(1);
    int type = (int)br.bits(2);
    if (type == 0) {
      br.bitcnt = 0;
      if (br.pos + 4 > br.len) return false;
      uint32_t len = br.d[br.pos] | (br.d[br.pos + 1] << 8);
      br.pos += 4;
      if (br.pos + len > br.len) return false;
      out.insert(out.end(), br.d + br.pos, br.d + br.pos + len);
      br.pos += len;
    } else if (type == 1 || type == 2) {
      static Huffman fixedLit, fixedDist;
      static bool fixedBuilt = false;
      Huffman lit, dist;
      if (type == 1) {
        if (!fixedBuilt) {
          uint8_t l[288];
          for (int i = 0; i < 144; i++) l[i] = 8;
          for (int i = 144; i < 256; i++) l[i] = 9;
          for (int i = 256; i < 280; i++) l[i] = 7;
          for (int i = 280; i < 288; i++) l[i] = 8;
          fixedLit.build(l, 288);
          uint8_t d[30];
          for (int i = 0; i < 30; i++) d[i] = 5;
          fixedDist.build(d, 30);
          fixedBuilt = true;
        }
        lit = fixedLit;
        dist = fixedDist;
      } else {
        int hlit = (int)br.bits(5) + 257, hdist = (int)br.bits(5) + 1, hclen = (int)br.bits(4) + 4;
        static const int ord[19] = {16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15};
        uint8_t cl[19];
        std::memset(cl, 0, sizeof cl);
        for (int i = 0; i < hclen; i++) cl[ord[i]] = (uint8_t)br.bits(3);
        Huffman clh;
        if (!clh.build(cl, 19)) return false;
        std::vector<uint8_t> lengths(hlit + hdist, 0);
        int i = 0;
        while (i < hlit + hdist) {
          int sym = clh.decode(br);
          if (sym < 0 || br.err) return false;
          if (sym < 16) lengths[i++] = (uint8_t)sym;
          else if (sym == 16) {
            if (i == 0) return false;
            uint8_t prev = lengths[i - 1];
            int rep = 3 + (int)br.bits(2);
            while (rep-- && i < hlit + hdist) lengths[i++] = prev;
          } else if (sym == 17) {
            int rep = 3 + (int)br.bits(3);
            while (rep-- && i < hlit + hdist) lengths[i++] = 0;
          } else {
            int rep = 11 + (int)br.bits(7);
            while (rep-- && i < hlit + hdist) lengths[i++] = 0;
          }
        }
        if (!lit.build(lengths.data(), hlit)) return false;
        if (!dist.build(lengths.data() + hlit, hdist)) return false;
      }
      while (true) {
        int sym = lit.decode(br);
        if (sym < 0 || br.err) return false;
        if (sym < 256) out.push_back((uint8_t)sym);
        else if (sym == 256) break;
        else {
          int li = sym - 257;
          if (li >= 29) return false;
          int len = LEN_BASE[li] + (int)br.bits(LEN_EXTRA[li]);
          int dsym = dist.decode(br);
          if (dsym < 0 || dsym >= 30) return false;
          int d = DIST_BASE[dsym] + (int)br.bits(DIST_EXTRA[dsym]);
          if ((size_t)d > out.size()) return false;
          size_t start = out.size() - d;
          for (int k = 0; k < len; k++) out.push_back(out[start + k]);
        }
      }
    } else return false;
  } while (!last && !br.err);
  return !br.err;
}
}  // namespace

bool inflateRaw(const uint8_t* data, size_t len, std::vector<uint8_t>& out) {
  out.clear();
  BitReader br(data, len);
  return inflateBlock(br, out);
}
bool zlibDecompress(const uint8_t* data, size_t len, std::vector<uint8_t>& out) {
  if (len < 6) return false;
  if ((data[0] & 0x0F) != 8) return false;
  return inflateRaw(data + 2, len - 6, out);
}

}  // namespace lc
