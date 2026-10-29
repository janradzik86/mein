// aimodel.cpp — wnioskowanie lokalnego modelu rysującego wzory grawerunkowe.
//
// Obsługiwane formaty wag (oba wytrenowane w tym projekcie, bez zależności zewnętrznych):
//   WOJANAI1 — warunkowany GRU + MDN (regresja delt pióra), starszy wariant
//   WOJANAI2 — warunkowany GRU nad tokenami ruchów pióra (16 kierunków × 3 długości),
//              trenowany przez tools/train_tokens.py
//
// Wejście: opis projektu (prompt + kategoria + styl + produkt + detal + typ grawerowania)
//          → wektor 128 (hashowanie słów/trigramów, identyczne jak w treningu)
// Wyjście: lista kresek (współrzędne znormalizowane do [0,1]), gotowa na warstwę ENGRAVE_VECTOR.
//
// Brak Pythona, brak PyTorcha, brak GPU — czysty C++17, kilkadziesiąt ms na rysunek.

#include "aimodel.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#ifdef _WIN32
#include <windows.h>
#else
#include <unistd.h>
#endif

namespace lc {
namespace aimodel {
namespace {

constexpr int kCondDim = 128;
constexpr int kTextBuckets = 96;
constexpr int kParamBuckets = 32;

// --- format WOJANAI1 (MDN) ---
constexpr int kInDim = 5;
constexpr float kScale = 40.0f;

// --- format WOJANAI2 (tokeny) ---
constexpr int kDirs = 16;
constexpr float kLens[3] = {0.022f, 0.05f, 0.09f};

enum class Kind { None, Mdn, Token };

struct Net {
  Kind kind = Kind::None;
  bool ok = false;
  int condDim = 0, hidden = 0, layers = 0, nMix = 0, maxlen = 0, vocab = 0, emb = 0;
  std::vector<float> condW, condB;
  std::vector<std::vector<float>> wih, whh, bih, bhh;
  std::vector<float> outW, outB;      // MDN: [K*5+3, H]; tokeny: [V, H]
  std::vector<float> embW;            // tokeny: [V, E]
  long long bytes = 0, params = 0;
  std::string path, error;
};

Net g_net;
std::once_flag g_once;

std::string exeDir() {
#ifdef _WIN32
  char buf[4096];
  DWORD n = GetModuleFileNameA(nullptr, buf, sizeof(buf));
  if (n == 0) return ".";
  std::string s(buf, n);
  size_t p = s.find_last_of("\\/");
  return p == std::string::npos ? "." : s.substr(0, p);
#else
  char buf[4096];
  ssize_t n = readlink("/proc/self/exe", buf, sizeof(buf) - 1);
  if (n <= 0) return ".";
  buf[n] = 0;
  std::string s(buf);
  size_t p = s.find_last_of('/');
  return p == std::string::npos ? "." : s.substr(0, p);
#endif
}

bool readFile(const std::string& path, std::vector<char>& out) {
  FILE* f = std::fopen(path.c_str(), "rb");
  if (!f) return false;
  std::fseek(f, 0, SEEK_END);
  long n = std::ftell(f);
  std::fseek(f, 0, SEEK_SET);
  if (n <= 0) {
    std::fclose(f);
    return false;
  }
  out.resize((size_t)n);
  size_t got = std::fread(out.data(), 1, out.size(), f);
  std::fclose(f);
  return got == out.size();
}

std::vector<std::string> candidates() {
  std::vector<std::string> v;
  std::string dir = exeDir();
  v.push_back(dir + "/modele/ai-generator.bin");
  v.push_back(dir + "/ai-generator.bin");
  const char* env = std::getenv("WOJAN_MODELS");
  if (env && *env) {
    v.push_back(std::string(env) + "/ai-generator.bin");
    v.push_back(std::string(env) + "/modele/ai-generator.bin");
  }
#ifdef _WIN32
  const char* la = std::getenv("LOCALAPPDATA");
  if (la && *la) v.push_back(std::string(la) + "\\WOJAN LaserCraft AI\\modele\\ai-generator.bin");
#else
  const char* home = std::getenv("HOME");
  if (home && *home) v.push_back(std::string(home) + "/.local/share/wojan-lasercraft/modele/ai-generator.bin");
#endif
  v.push_back("modele/ai-generator.bin");
  return v;
}

// ---------------------------------------------------------------- wektor warunkujący

uint32_t fnv1a(const std::string& s) {
  uint32_t h = 2166136261u;
  for (unsigned char c : s) {
    h ^= c;
    h *= 16777619u;
  }
  return h;
}

std::string normText(const std::string& s) {
  std::string o;
  o.reserve(s.size());
  static const char* pairs[][2] = {{"\xC4\x85", "a"}, {"\xC4\x87", "c"}, {"\xC4\x99", "e"}, {"\xC5\x82", "l"},
                                   {"\xC5\x84", "n"}, {"\xC3\xB3", "o"}, {"\xC5\x9B", "s"}, {"\xC5\xBA", "z"},
                                   {"\xC5\xBC", "z"}, {"\xC4\x84", "a"}, {"\xC4\x86", "c"}, {"\xC4\x98", "e"},
                                   {"\xC5\x81", "l"}, {"\xC5\x83", "n"}, {"\xC3\x93", "o"}, {"\xC5\x9A", "s"},
                                   {"\xC5\xB9", "z"}, {"\xC5\xBB", "z"}};
  size_t i = 0;
  while (i < s.size()) {
    bool done = false;
    if ((unsigned char)s[i] >= 0x80) {
      for (auto& pr : pairs) {
        size_t n = std::strlen(pr[0]);
        if (s.compare(i, n, pr[0]) == 0) {
          o += pr[1];
          i += n;
          done = true;
          break;
        }
      }
      if (done) continue;
    }
    char c = s[i];
    if (c >= 'A' && c <= 'Z') c = (char)(c - 'A' + 'a');
    o += c;
    i++;
  }
  return o;
}

void condVector(const std::string& prompt, const std::string& category, const std::string& style,
                const std::string& product, const std::string& detail, const std::string& engrave,
                float* v) {
  for (int i = 0; i < kCondDim; i++) v[i] = 0.f;
  std::string txt = normText(prompt);
  std::string tok;
  auto flush = [&]() {
    if (tok.empty()) return;
    v[fnv1a(tok) % kTextBuckets] += 1.0f;
    for (size_t i = 0; i + 3 <= tok.size(); i++) v[fnv1a(tok.substr(i, 3)) % kTextBuckets] += 0.5f;
    tok.clear();
  };
  for (char c : txt) {
    if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))
      tok += c;
    else
      flush();
  }
  flush();
  const std::pair<const char*, std::string> params[5] = {
      {"category", category}, {"style", style}, {"product", product}, {"detail", detail}, {"engrave", engrave}};
  for (auto& kv : params) {
    if (kv.second.empty()) continue;
    std::string key = std::string(kv.first) + "=" + normText(kv.second);
    v[kTextBuckets + fnv1a(key) % kParamBuckets] += 2.0f;
  }
  float n = 0;
  for (int i = 0; i < kCondDim; i++) n += v[i] * v[i];
  n = std::sqrt(n);
  if (n > 1e-6f)
    for (int i = 0; i < kCondDim; i++) v[i] /= n;
}

// ---------------------------------------------------------------- wczytywanie wag

bool load(Net& n) {
  std::vector<char> raw;
  std::string chosen;
  for (auto& c : candidates()) {
    if (readFile(c, raw)) {
      chosen = c;
      break;
    }
  }
  if (chosen.empty()) {
    n.error = "brak pliku modele/ai-generator.bin";
    return false;
  }
  n.path = chosen;
  n.bytes = (long long)raw.size();
  if (raw.size() < 9) {
    n.error = "plik modelu jest za krótki";
    return false;
  }
  const bool mdn = std::memcmp(raw.data(), "WOJANAI1", 8) == 0;
  const bool tok = std::memcmp(raw.data(), "WOJANAI2", 8) == 0;
  if (!mdn && !tok) {
    n.error = "nieprawidłowy nagłówek modelu (oczekiwano WOJANAI1 lub WOJANAI2)";
    return false;
  }

  const char* p = raw.data() + 8;
  const char* end = raw.data() + raw.size();
  auto rd = [&p, end]() {
    int32_t v = 0;
    if (p + 4 > end) {
      p = end;
      return 0;
    }
    std::memcpy(&v, p, 4);
    p += 4;
    return (int)v;
  };
  auto take = [&p, end](std::vector<float>& dst, size_t count) {
    dst.resize(count);
    if (p + count * sizeof(float) > end) {
      p = end;
      return false;
    }
    std::memcpy(dst.data(), p, count * sizeof(float));
    p += count * sizeof(float);
    return true;
  };

  n.condDim = rd();
  n.hidden = rd();
  n.layers = rd();
  const int fourth = rd();
  const int fifth = rd();
  n.maxlen = rd();
  const int nT = rd();
  if (n.condDim != kCondDim || n.layers != 2 || n.hidden <= 0 || n.hidden > 1024 || nT < 10 || nT > 16) {
    n.error = "nieobsługiwana konfiguracja modelu";
    return false;
  }
  const int H = n.hidden;
  n.wih.resize(2, {});
  n.whh.resize(2, {});
  n.bih.resize(2, {});
  n.bhh.resize(2, {});
  bool ok = true;
  if (mdn) {
    n.kind = Kind::Mdn;
    n.nMix = fourth;
    const int inDim = fifth;
    if (inDim != kInDim || n.nMix <= 0 || n.nMix > 32) {
      n.error = "nieobsługiwana konfiguracja modelu (MDN)";
      return false;
    }
    const int K = n.nMix;
    ok = ok && take(n.condW, (size_t)n.condDim * 2 * H);
    ok = ok && take(n.condB, (size_t)2 * H);
    ok = ok && take(n.wih[0], (size_t)3 * H * kInDim);
    ok = ok && take(n.whh[0], (size_t)3 * H * H);
    ok = ok && take(n.bih[0], (size_t)3 * H);
    ok = ok && take(n.bhh[0], (size_t)3 * H);
    ok = ok && take(n.wih[1], (size_t)3 * H * H);
    ok = ok && take(n.whh[1], (size_t)3 * H * H);
    ok = ok && take(n.bih[1], (size_t)3 * H);
    ok = ok && take(n.bhh[1], (size_t)3 * H);
    ok = ok && take(n.outW, (size_t)(K * 5 + 3) * H);
    ok = ok && take(n.outB, (size_t)(K * 5 + 3));
    n.params = 128LL * 2 * H + 2 * H + 2 * (3LL * H * kInDim + 3LL * H * H + 6LL * H) +
               (long long)(K * 5 + 3) * H + (K * 5 + 3);
  } else {
    n.kind = Kind::Token;
    n.vocab = fourth;
    n.emb = fifth;
    if (n.vocab < kDirs * 3 + 2 || n.vocab > 4096 || n.emb <= 0 || n.emb > 256) {
      n.error = "nieobsługiwana konfiguracja modelu (tokeny)";
      return false;
    }
    ok = ok && take(n.condW, (size_t)n.condDim * 2 * H);
    ok = ok && take(n.condB, (size_t)2 * H);
    ok = ok && take(n.embW, (size_t)n.vocab * n.emb);
    ok = ok && take(n.wih[0], (size_t)3 * H * (n.emb + kCondDim));
    ok = ok && take(n.whh[0], (size_t)3 * H * H);
    ok = ok && take(n.bih[0], (size_t)3 * H);
    ok = ok && take(n.bhh[0], (size_t)3 * H);
    ok = ok && take(n.wih[1], (size_t)3 * H * H);
    ok = ok && take(n.whh[1], (size_t)3 * H * H);
    ok = ok && take(n.bih[1], (size_t)3 * H);
    ok = ok && take(n.bhh[1], (size_t)3 * H);
    ok = ok && take(n.outW, (size_t)n.vocab * H);
    ok = ok && take(n.outB, (size_t)n.vocab);
    n.params = (long long)n.condDim * 2 * H + 2 * H + (long long)n.vocab * n.emb +
               (long long)3 * H * (n.emb + kCondDim) + (long long)3 * H * H + 6LL * H + (long long)3 * H * H +
               (long long)3 * H * H + 6LL * H + (long long)n.vocab * H + n.vocab;
  }
  if (!ok) {
    n.error = "plik modelu obcięty";
    n.kind = Kind::None;
    return false;
  }
  n.ok = true;
  return true;
}

Net& net() {
  std::call_once(g_once, []() { load(g_net); });
  return g_net;
}

// ---------------------------------------------------------------- matematyka

void matVec(const std::vector<float>& W, int rows, int cols, const float* x, float* y) {
  for (int r = 0; r < rows; r++) {
    const float* w = W.data() + (size_t)r * cols;
    float acc = 0.f;
    for (int c = 0; c < cols; c++) acc += w[c] * x[c];
    y[r] = acc;
  }
}

float sigmoidf2(float x) { return 1.f / (1.f + std::exp(-x)); }

// bramki w kolejności PyTorcha: r, z, n
void gruCell(const Net& n, int layer, const float* x, int inDim, const float* hPrev, float* hOut) {
  const int H = n.hidden;
  std::vector<float> gi((size_t)3 * H), gh((size_t)3 * H);
  matVec(n.wih[layer], 3 * H, inDim, x, gi.data());
  matVec(n.whh[layer], 3 * H, H, hPrev, gh.data());
  for (int i = 0; i < 3 * H; i++) gi[i] += n.bih[layer][i] + n.bhh[layer][i];
  for (int i = 0; i < H; i++) {
    float r = sigmoidf2(gi[i] + gh[i]);
    float z = sigmoidf2(gi[H + i] + gh[H + i]);
    float nn = std::tanh(gi[2 * H + i] + r * gh[2 * H + i]);
    hOut[i] = (1.f - z) * nn + z * hPrev[i];
  }
}

struct Rng {
  uint32_t s;
  explicit Rng(uint32_t seed) : s(seed ? seed : 0x9e3779b9u) {}
  uint32_t next() {
    s ^= s << 13;
    s ^= s >> 17;
    s ^= s << 5;
    return s;
  }
  float uni() { return (next() >> 8) * (1.0f / 16777216.0f); }
  float normal() {
    float u1 = std::max(uni(), 1e-7f), u2 = uni();
    return std::sqrt(-2.f * std::log(u1)) * std::cos(6.2831853f * u2);
  }
  int categorical(const float* logits, int n) {
    float mx = logits[0];
    for (int i = 1; i < n; i++) mx = std::max(mx, logits[i]);
    float sum = 0;
    std::vector<float> p((size_t)n);
    for (int i = 0; i < n; i++) {
      p[i] = std::exp(logits[i] - mx);
      sum += p[i];
    }
    float r = uni() * sum;
    for (int i = 0; i < n; i++) {
      r -= p[i];
      if (r <= 0.f) return i;
    }
    return n - 1;
  }
};

// ---------------------------------------------------------------- rysowanie: tokeny

bool sampleTokens(const Net& n, const float* cond, uint32_t seed, std::vector<std::vector<Vec2>>& out) {
  const int H = n.hidden, E = n.emb, V = n.vocab;
  const int inDim = E + kCondDim;   // embedding tokenu + wektor warunkujący (jak w treningu)
  const int penUp = V - 2, endTok = V - 1;
  std::vector<float> h((size_t)2 * H);
  {
    std::vector<float> tmp((size_t)2 * H);
    matVec(n.condW, 2 * H, kCondDim, cond, tmp.data());
    for (int i = 0; i < 2 * H; i++) h[i] = std::tanh(tmp[i] + n.condB[i]);
  }
  Rng rng(seed * 2654435761u + 7919u);
  std::vector<float> o0((size_t)H), o1((size_t)H), logits((size_t)V);
  std::vector<std::pair<float, int>> order((size_t)V);
  std::vector<Vec2> cur;
  float px = 0.5f, py = 0.5f;
  int tok = endTok;             // token startowy
  float temperature = 0.92f;
  int topK = 8;
  if (const char* e = std::getenv("WOJAN_AI_TEMP")) {
    float v = (float)std::atof(e);
    if (v > 0.1f && v < 2.0f) temperature = v;
  }
  if (const char* e = std::getenv("WOJAN_AI_TOPK")) {
    int v = std::atoi(e);
    if (v >= 1 && v <= 50) topK = v;
  }
  const int maxTok = std::min(n.maxlen, 200);

  for (int t = 0; t < maxTok; t++) {
    std::vector<float> h0(h.begin(), h.begin() + H), h1(h.begin() + H, h.end());
    std::vector<float> xin((size_t)inDim);
    std::memcpy(xin.data(), n.embW.data() + (size_t)tok * E, sizeof(float) * E);
    std::memcpy(xin.data() + E, cond, sizeof(float) * kCondDim);
    gruCell(n, 0, xin.data(), inDim, h0.data(), o0.data());
    gruCell(n, 1, o0.data(), H, h1.data(), o1.data());
    h.assign(o0.begin(), o0.end());
    h.insert(h.end(), o1.begin(), o1.end());

    matVec(n.outW, V, H, o1.data(), logits.data());
    for (int i = 0; i < V; i++) logits[i] = (logits[i] + n.outB[i]) / temperature;

    // top-k: próg odcięcia, resztę zerujemy
    for (int i = 0; i < V; i++) order[(size_t)i] = {logits[i], i};
    std::partial_sort(order.begin(), order.begin() + std::min(topK, V), order.end(),
                      [](const std::pair<float, int>& a, const std::pair<float, int>& b) { return a.first > b.first; });
    const float cut = order[(size_t)std::min(topK, V) - 1].first;
    for (int i = 0; i < V; i++)
      if (logits[i] < cut) logits[i] = -1e9f;

    tok = rng.categorical(logits.data(), V);
    if (tok == endTok) break;
    if (tok == penUp) {
      if (cur.size() > 2) out.push_back(cur);
      cur.clear();
      continue;
    }
    const int li = tok / kDirs, d = tok % kDirs;
    const float ang = (float)d / kDirs * 6.2831853f - 3.14159265f;
    px += std::cos(ang) * kLens[li];
    py += std::sin(ang) * kLens[li];
    px = std::min(1.6f, std::max(-0.6f, px));
    py = std::min(1.6f, std::max(-0.6f, py));
    cur.push_back({(double)px, (double)py});
    if (out.size() >= 24) break;
  }
  if (cur.size() > 2) out.push_back(cur);
  return !out.empty();
}

// ---------------------------------------------------------------- rysowanie: MDN

bool sampleMdn(const Net& n, const float* cond, uint32_t seed, std::vector<std::vector<Vec2>>& out) {
  const int H = n.hidden, K = n.nMix;
  std::vector<float> h((size_t)2 * H);
  {
    std::vector<float> tmp((size_t)2 * H);
    matVec(n.condW, 2 * H, kCondDim, cond, tmp.data());
    for (int i = 0; i < 2 * H; i++) h[i] = std::tanh(tmp[i] + n.condB[i]);
  }
  Rng rng(seed * 2654435761u + 12345u);
  float x[kInDim] = {0, 0, 0, 0, 0};
  std::vector<float> o0((size_t)H), o1((size_t)H), logits((size_t)(K * 5 + 3)), pi((size_t)K);
  std::vector<Vec2> cur;
  float px = 0.5f, py = 0.5f;
  const float temperature = 0.62f;
  const int maxTokens = std::min(n.maxlen, 400);

  for (int t = 0; t < maxTokens; t++) {
    std::vector<float> h0(h.begin(), h.begin() + H), h1(h.begin() + H, h.end());
    gruCell(n, 0, x, kInDim, h0.data(), o0.data());
    gruCell(n, 1, o0.data(), H, h1.data(), o1.data());
    h.assign(o0.begin(), o0.end());
    h.insert(h.end(), o1.begin(), o1.end());

    matVec(n.outW, K * 5 + 3, H, o1.data(), logits.data());
    for (int i = 0; i < K * 5 + 3; i++) logits[i] += n.outB[i];
    for (int k = 0; k < K; k++) pi[(size_t)k] = logits[4 * K + k] / temperature;
    const int k = rng.categorical(pi.data(), K);
    const float m0 = logits[2 * k], m1 = logits[2 * k + 1];
    const float sd0 = std::exp(std::min(std::max(logits[2 * K + 2 * k], -6.f), 1.f));
    const float sd1 = std::exp(std::min(std::max(logits[2 * K + 2 * k + 1], -6.f), 1.f));
    const float dxv = m0 + sd0 * rng.normal();
    const float dyv = m1 + sd1 * rng.normal();
    px += std::min(0.25f, std::max(-0.25f, dxv / kScale));
    py += std::min(0.25f, std::max(-0.25f, dyv / kScale));
    if (px < -6.f || px > 7.f || py < -6.f || py > 7.f) break;
    cur.push_back({(double)px, (double)py});

    float penLogits[3] = {logits[5 * K] / temperature, logits[5 * K + 1] / temperature,
                          logits[5 * K + 2] / temperature};
    if (out.size() < 3) penLogits[2] -= 9.0f;
    const int pen = rng.categorical(penLogits, 3);
    if (pen == 1) {
      if (cur.size() > 1) out.push_back(cur);
      cur.clear();
    } else if (pen == 2) {
      if (cur.size() > 1) out.push_back(cur);
      cur.clear();
      break;
    }
    if (out.size() >= 18) break;
    x[0] = dxv;
    x[1] = dyv;
    x[2] = x[3] = x[4] = 0;
    x[2 + (pen > 2 ? 2 : pen)] = 1;
  }
  if (cur.size() > 1) out.push_back(cur);
  return !out.empty();
}

std::vector<Vec2> smooth(const std::vector<Vec2>& s) {
  if (s.size() < 4) return s;
  std::vector<Vec2> o = s;
  for (size_t i = 1; i + 1 < s.size(); i++) {
    o[i].x = 0.25 * s[i - 1].x + 0.5 * s[i].x + 0.25 * s[i + 1].x;
    o[i].y = 0.25 * s[i - 1].y + 0.5 * s[i].y + 0.25 * s[i + 1].y;
  }
  return o;
}

}  // namespace

Info status() {
  Net& n = net();
  Info i;
  i.available = n.ok;
  i.path = n.path;
  i.bytes = n.bytes;
  i.hidden = n.hidden;
  i.layers = n.layers;
  i.nMix = n.kind == Kind::Mdn ? n.nMix : 0;
  i.condDim = n.condDim;
  i.params = n.params;
  i.version = n.kind == Kind::Mdn ? "GRU-MDN" : (n.kind == Kind::Token ? "GRU-tokeny" : "");
  i.error = n.error;
  return i;
}

std::string modelDir() {
  Net& n = net();
  if (n.path.empty()) return "";
  size_t p = n.path.find_last_of("/\\");
  return p == std::string::npos ? std::string(".") : n.path.substr(0, p);
}

std::string engineInfo() {
  Net& n = net();
  if (!n.ok) return std::string("model AI niedostępny: ") + n.error;
  char buf[512];
  if (n.kind == Kind::Token)
    std::snprintf(buf, sizeof(buf),
                  "model AI: %s (GRU %d×%d, słownik %d tokenów, %lld parametrów, %.1f MB) — %s",
                  "tokenowy GRU", n.layers, n.hidden, n.vocab, (long long)n.params,
                  n.bytes / 1048576.0, n.path.c_str());
  else
    std::snprintf(buf, sizeof(buf), "model AI: GRU-MDN %d×%d, mieszanina %d, %lld parametrów, %.1f MB — %s",
                  n.layers, n.hidden, n.nMix, (long long)n.params, n.bytes / 1048576.0, n.path.c_str());
  return std::string(buf);
}

bool generate(const std::string& prompt, const std::string& category, const std::string& style,
              const std::string& product, const std::string& detail, const std::string& engrave,
              uint32_t seed, std::vector<std::vector<Vec2>>& strokes, std::string& err) {
  Net& n = net();
  strokes.clear();
  if (!n.ok) {
    err = n.error;
    return false;
  }
  float cond[kCondDim];
  condVector(prompt, category, style, product, detail, engrave, cond);

  // kilka losowań — wybieramy najbogatszy rysunek (deterministycznie dla danego ziarna)
  std::vector<std::vector<Vec2>> best;
  size_t bestPts = 0;
  for (uint32_t attempt = 0; attempt < 4; attempt++) {
    std::vector<std::vector<Vec2>> cand;
    const uint32_t s = seed + attempt * 7919u;
    bool ok = (n.kind == Kind::Token) ? sampleTokens(n, cond, s, cand) : sampleMdn(n, cond, s, cand);
    if (!ok) continue;
    size_t pts = 0;
    for (auto& c : cand) pts += c.size();
    if (pts > bestPts) {
      bestPts = pts;
      best = cand;
    }
    if (bestPts > 260) break;
  }
  if (best.empty()) {
    err = "model nie narysował sensownego wzoru (spróbuj innego ziarna)";
    return false;
  }

  // wygładzenie + normalizacja do [0,1] + odrzucenie zdegenerowanych kresek
  double minx = 1e9, miny = 1e9, maxx = -1e9, maxy = -1e9;
  std::vector<std::vector<Vec2>> kept;
  for (auto& raw : best) {
    std::vector<Vec2> s = smooth(raw);
    double len = 0;
    for (size_t i = 1; i < s.size(); i++)
      len += std::hypot(s[i].x - s[i - 1].x, s[i].y - s[i - 1].y);
    if (len < 0.04) continue;  // kreska-drobinka, nic nie wnosi
    for (auto& p : s) {
      minx = std::min(minx, p.x);
      miny = std::min(miny, p.y);
      maxx = std::max(maxx, p.x);
      maxy = std::max(maxy, p.y);
    }
    kept.push_back(s);
  }
  if (kept.size() < 2) {
    err = "model narysował zbyt prosty wzór (spróbuj innego ziarna)";
    return false;
  }
  const double w = std::max(maxx - minx, 1e-9), h = std::max(maxy - miny, 1e-9), ext = std::max(w, h);
  for (auto& s : kept)
    for (auto& p : s) {
      p.x = (p.x - minx) / ext + (1.0 - w / ext) * 0.5;
      p.y = (p.y - miny) / ext + (1.0 - h / ext) * 0.5;
    }
  strokes = kept;
  return true;
}

}  // namespace aimodel
}  // namespace lc
