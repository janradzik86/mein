#include "common.h"
#include <cstdarg>
#include <ctime>
#include <sys/stat.h>

namespace lc {

std::string trim(const std::string& s) {
  size_t a = s.find_first_not_of(" \t\r\n");
  if (a == std::string::npos) return "";
  size_t b = s.find_last_not_of(" \t\r\n");
  return s.substr(a, b - a + 1);
}
std::string lowerAscii(const std::string& s) {
  std::string r = s;
  for (char& c : r) if (c >= 'A' && c <= 'Z') c = char(c - 'A' + 'a');
  return r;
}
std::string upperAscii(const std::string& s) {
  std::string r = s;
  for (char& c : r) if (c >= 'a' && c <= 'z') c = char(c - 'a' + 'A');
  return r;
}
bool startsWith(const std::string& s, const std::string& p) {
  return s.size() >= p.size() && s.compare(0, p.size(), p) == 0;
}
bool contains(const std::string& s, const std::string& p) { return s.find(p) != std::string::npos; }
std::vector<std::string> split(const std::string& s, char sep) {
  std::vector<std::string> out;
  std::string cur;
  for (char c : s) {
    if (c == sep) { out.push_back(cur); cur.clear(); }
    else cur.push_back(c);
  }
  out.push_back(cur);
  return out;
}
std::string join(const std::vector<std::string>& v, const std::string& sep) {
  std::string r;
  for (size_t i = 0; i < v.size(); i++) { if (i) r += sep; r += v[i]; }
  return r;
}
std::string replaceAll(std::string s, const std::string& from, const std::string& to) {
  if (from.empty()) return s;
  size_t p = 0;
  while ((p = s.find(from, p)) != std::string::npos) { s.replace(p, from.size(), to); p += to.size(); }
  return s;
}
std::string fmtDouble(double v, int decimals) {
  char buf[64];
  std::snprintf(buf, sizeof buf, "%.*f", decimals, v);
  std::string s = buf;
  if (s.find('.') != std::string::npos) {
    while (!s.empty() && s.back() == '0') s.pop_back();
    if (!s.empty() && s.back() == '.') s.pop_back();
  }
  if (s == "-0") s = "0";
  return s;
}
std::string slugify(const std::string& s) {
  std::string d = lowerAscii(stripDiacritics(s)), out;
  for (char c : d) {
    if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) out.push_back(c);
    else if (c == ' ' || c == '_' || c == '-' || c == '/') { if (!out.empty() && out.back() != '-') out.push_back('-'); }
  }
  while (!out.empty() && out.back() == '-') out.pop_back();
  return out.empty() ? "projekt" : out;
}

std::vector<uint32_t> utf8Decode(const std::string& s) {
  std::vector<uint32_t> out;
  size_t i = 0;
  while (i < s.size()) {
    unsigned char c = (unsigned char)s[i];
    uint32_t cp = 0xFFFD; int n = 0;
    if (c < 0x80) { cp = c; n = 0; }
    else if ((c & 0xE0) == 0xC0) { cp = c & 0x1F; n = 1; }
    else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; n = 2; }
    else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; n = 3; }
    else { i++; out.push_back(0xFFFD); continue; }
    if (i + n >= s.size() + 0 && n > 0) { out.push_back(0xFFFD); break; }
    for (int k = 1; k <= n; k++) {
      unsigned char cc = (unsigned char)s[i + k];
      if ((cc & 0xC0) != 0x80) { cp = 0xFFFD; n = k - 1; break; }
      cp = (cp << 6) | (cc & 0x3F);
    }
    i += n + 1;
    out.push_back(cp);
  }
  return out;
}
std::string utf8From(uint32_t cp) {
  std::string r;
  if (cp < 0x80) r.push_back(char(cp));
  else if (cp < 0x800) { r.push_back(char(0xC0 | (cp >> 6))); r.push_back(char(0x80 | (cp & 0x3F))); }
  else if (cp < 0x10000) { r.push_back(char(0xE0 | (cp >> 12))); r.push_back(char(0x80 | ((cp >> 6) & 0x3F))); r.push_back(char(0x80 | (cp & 0x3F))); }
  else { r.push_back(char(0xF0 | (cp >> 18))); r.push_back(char(0x80 | ((cp >> 12) & 0x3F))); r.push_back(char(0x80 | ((cp >> 6) & 0x3F))); r.push_back(char(0x80 | (cp & 0x3F))); }
  return r;
}
std::string utf8Encode(const std::vector<uint32_t>& cps) {
  std::string r;
  for (auto cp : cps) r += utf8From(cp);
  return r;
}
std::vector<std::string> utf8Chars(const std::string& s) {
  std::vector<std::string> out;
  for (auto cp : utf8Decode(s)) out.push_back(utf8From(cp));
  return out;
}
std::string utf8Sanitize(const std::string& s) {
  std::string out;
  out.reserve(s.size());
  size_t i = 0;
  while (i < s.size()) {
    unsigned char c = (unsigned char)s[i];
    size_t need = 0;
    if (c < 0x80) need = 1;
    else if ((c >> 5) == 0x6) need = 2;
    else if ((c >> 4) == 0xE) need = 3;
    else if ((c >> 3) == 0x1E) need = 4;
    if (need == 0 || i + need > s.size()) { i++; continue; }
    bool ok = true;
    for (size_t k = 1; k < need; k++)
      if (((unsigned char)s[i + k] >> 6) != 0x2) { ok = false; break; }
    if (!ok) { i++; continue; }
    out.append(s, i, need);
    i += need;
  }
  return out;
}

std::string stripDiacritics(const std::string& s) {
  static const char* pairs[] = {
    "ą","a","ć","c","ę","e","ł","l","ń","n","ó","o","ś","s","ź","z","ż","z",
    "Ą","A","Ć","C","Ę","E","Ł","L","Ń","N","Ó","O","Ś","S","Ź","Z","Ż","Z",
    "á","a","é","e","í","i","ú","u","ü","u","ö","o","ä","a","ß","ss","ń","n"};
  std::string out;
  for (auto& ch : utf8Chars(s)) {
    bool done = false;
    for (size_t i = 0; i + 1 < sizeof(pairs) / sizeof(pairs[0]); i += 2)
      if (ch == pairs[i]) { out += pairs[i + 1]; done = true; break; }
    if (!done) out += ch;
  }
  return out;
}
std::string lowerPl(const std::string& s) {
  auto cps = utf8Decode(s);
  std::vector<uint32_t> o;
  for (auto cp : cps) {
    if (cp >= 'A' && cp <= 'Z') o.push_back(cp + 32);
    else if (cp >= 0xC0 && cp <= 0xDD && cp != 0xD7) o.push_back(cp + 32);
    else if (cp == 0x141) o.push_back(0x142);  // Ł -> ł
    else if (cp == 0x15A) o.push_back(0x15B);  // Ś
    else if (cp == 0x179) o.push_back(0x17A);  // Ź
    else if (cp == 0x17B) o.push_back(0x17C);  // Ż
    else o.push_back(cp);
  }
  return utf8Encode(o);
}

std::string readFile(const std::string& path) {
  std::ifstream f(path, std::ios::binary);
  if (!f) return "";
  std::string s((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
  return s;
}
bool writeFile(const std::string& path, const std::string& data) {
  makeDirs(std::filesystem::path(path).parent_path().string());
  std::ofstream f(path, std::ios::binary);
  if (!f) return false;
  f.write(data.data(), (std::streamsize)data.size());
  return bool(f);
}
bool writeFileBytes(const std::string& path, const std::vector<uint8_t>& data) {
  makeDirs(std::filesystem::path(path).parent_path().string());
  std::ofstream f(path, std::ios::binary);
  if (!f) return false;
  if (!data.empty()) f.write((const char*)data.data(), (std::streamsize)data.size());
  return bool(f);
}
bool readFileBytes(const std::string& path, std::vector<uint8_t>& out) {
  std::ifstream f(path, std::ios::binary);
  if (!f) return false;
  out.assign((std::istreambuf_iterator<char>(f)), std::istreambuf_iterator<char>());
  return true;
}
bool makeDirs(const std::string& path) {
  if (path.empty()) return true;
  std::error_code ec;
  std::filesystem::create_directories(path, ec);
  return !ec;
}

static const char* B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
std::string base64Encode(const std::vector<uint8_t>& in) {
  std::string out;
  size_t i = 0;
  while (i + 2 < in.size()) {
    uint32_t v = (in[i] << 16) | (in[i + 1] << 8) | in[i + 2];
    out.push_back(B64[(v >> 18) & 63]); out.push_back(B64[(v >> 12) & 63]);
    out.push_back(B64[(v >> 6) & 63]); out.push_back(B64[v & 63]);
    i += 3;
  }
  if (i + 1 == in.size()) {
    uint32_t v = in[i] << 16;
    out.push_back(B64[(v >> 18) & 63]); out.push_back(B64[(v >> 12) & 63]); out += "==";
  } else if (i + 2 == in.size()) {
    uint32_t v = (in[i] << 16) | (in[i + 1] << 8);
    out.push_back(B64[(v >> 18) & 63]); out.push_back(B64[(v >> 12) & 63]);
    out.push_back(B64[(v >> 6) & 63]); out += "=";
  }
  return out;
}
std::vector<uint8_t> base64Decode(const std::string& in) {
  std::vector<uint8_t> out;
  int val = 0, bits = -8;
  for (unsigned char c : in) {
    int idx = -1;
    if (c >= 'A' && c <= 'Z') idx = c - 'A';
    else if (c >= 'a' && c <= 'z') idx = c - 'a' + 26;
    else if (c >= '0' && c <= '9') idx = c - '0' + 52;
    else if (c == '+') idx = 62;
    else if (c == '/') idx = 63;
    else continue;
    val = (val << 6) | idx; bits += 6;
    if (bits >= 0) { out.push_back(uint8_t((val >> bits) & 0xFF)); bits -= 8; }
  }
  return out;
}
std::string nowIso() {
  std::time_t t = std::time(nullptr);
  std::tm tmv{};
#ifdef _WIN32
  gmtime_s(&tmv, &t);
#else
  gmtime_r(&t, &tmv);
#endif
  char buf[32];
  std::strftime(buf, sizeof buf, "%Y-%m-%dT%H:%M:%SZ", &tmv);
  return buf;
}
std::string sfmt(const char* fmt, ...) {
  char buf[1024];
  va_list ap;
  va_start(ap, fmt);
  std::vsnprintf(buf, sizeof buf, fmt, ap);
  va_end(ap);
  return buf;
}

}  // namespace lc
