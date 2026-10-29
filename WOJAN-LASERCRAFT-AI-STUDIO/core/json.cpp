#include "json.h"
#include "common.h"
#include <cmath>
#include <cstdlib>

namespace lc {

static const Json& nullJson() { static Json n; return n; }

const Json& Json::operator[](size_t i) const {
  if (t_ != ARR || i >= arr_.size()) return nullJson();
  return arr_[i];
}
const Json& Json::operator[](const std::string& k) const {
  if (t_ != OBJ) return nullJson();
  auto it = obj_.find(k);
  return it == obj_.end() ? nullJson() : it->second;
}
Json& Json::operator[](const std::string& k) {
  if (t_ != OBJ) { t_ = OBJ; obj_.clear(); }
  return obj_[k];
}
Json& Json::operator[](size_t i) {
  if (t_ != ARR) { t_ = ARR; arr_.clear(); }
  if (i >= arr_.size()) arr_.resize(i + 1);
  return arr_[i];
}
std::vector<std::string> Json::keys() const {
  std::vector<std::string> k;
  for (auto& kv : obj_) k.push_back(kv.first);
  return k;
}

static void escapeInto(std::string& out, const std::string& s) {
  out.push_back('"');
  for (unsigned char c : s) {
    switch (c) {
      case '"': out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      case '\t': out += "\\t"; break;
      default:
        if (c < 0x20) { out += sfmt("\\u%04x", c); }
        else out.push_back((char)c);
    }
  }
  out.push_back('"');
}

void Json::dumpTo(std::string& out, int indent, int level) const {
  auto nl = [&](int lvl) {
    if (indent < 0) return;
    out.push_back('\n');
    out.append((size_t)(lvl * indent), ' ');
  };
  switch (t_) {
    case NUL: out += "null"; break;
    case BOOL: out += b_ ? "true" : "false"; break;
    case NUM: {
      if (!std::isfinite(n_)) { out += "0"; break; }
      double r = std::round(n_);
      if (std::fabs(n_ - r) < 1e-9 && std::fabs(n_) < 1e15) {
        out += sfmt("%lld", (long long)r);
      } else {
        char buf[64];
        std::snprintf(buf, sizeof buf, "%.6g", n_);
        out += buf;
      }
      break;
    }
    case STR: escapeInto(out, s_); break;
    case ARR: {
      if (arr_.empty()) { out += "[]"; break; }
      out.push_back('[');
      for (size_t i = 0; i < arr_.size(); i++) {
        if (i) out.push_back(',');
        nl(level + 1);
        arr_[i].dumpTo(out, indent, level + 1);
      }
      nl(level);
      out.push_back(']');
      break;
    }
    case OBJ: {
      if (obj_.empty()) { out += "{}"; break; }
      out.push_back('{');
      bool first = true;
      for (auto& kv : obj_) {
        if (!first) out.push_back(',');
        first = false;
        nl(level + 1);
        escapeInto(out, kv.first);
        out.push_back(':');
        if (indent >= 0) out.push_back(' ');
        kv.second.dumpTo(out, indent, level + 1);
      }
      nl(level);
      out.push_back('}');
      break;
    }
  }
}
std::string Json::dump(int indent) const {
  std::string out;
  dumpTo(out, indent, 0);
  return out;
}

namespace {
struct Parser {
  const std::string& s;
  size_t i = 0;
  std::string err;
  explicit Parser(const std::string& t) : s(t) {}
  void ws() { while (i < s.size() && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++; }
  bool fail(const std::string& m) { if (err.empty()) err = m + " @ " + std::to_string(i); return false; }
  bool parseValue(Json& out) {
    ws();
    if (i >= s.size()) return fail("nieoczekiwany koniec");
    char c = s[i];
    if (c == '{') return parseObj(out);
    if (c == '[') return parseArr(out);
    if (c == '"') { std::string v; if (!parseStr(v)) return false; out = Json(v); return true; }
    if (c == 't') { if (s.compare(i, 4, "true") == 0) { i += 4; out = Json(true); return true; } return fail("zła wartość"); }
    if (c == 'f') { if (s.compare(i, 5, "false") == 0) { i += 5; out = Json(false); return true; } return fail("zła wartość"); }
    if (c == 'n') { if (s.compare(i, 4, "null") == 0) { i += 4; out = Json(); return true; } return fail("zła wartość"); }
    return parseNum(out);
  }
  bool parseNum(Json& out) {
    size_t st = i;
    if (i < s.size() && (s[i] == '-' || s[i] == '+')) i++;
    while (i < s.size() && ((s[i] >= '0' && s[i] <= '9') || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '-' || s[i] == '+')) i++;
    if (i == st) return fail("oczekiwano liczby");
    out = Json(std::atof(s.substr(st, i - st).c_str()));
    return true;
  }
  bool parseStr(std::string& out) {
    if (s[i] != '"') return fail("oczekiwano cudzysłowu");
    i++;
    out.clear();
    while (i < s.size()) {
      char c = s[i++];
      if (c == '"') return true;
      if (c == '\\') {
        if (i >= s.size()) return fail("zła ucieczka");
        char e = s[i++];
        switch (e) {
          case 'n': out.push_back('\n'); break;
          case 't': out.push_back('\t'); break;
          case 'r': out.push_back('\r'); break;
          case 'b': out.push_back('\b'); break;
          case 'f': out.push_back('\f'); break;
          case '/': out.push_back('/'); break;
          case '\\': out.push_back('\\'); break;
          case '"': out.push_back('"'); break;
          case 'u': {
            if (i + 4 > s.size()) return fail("zły \\u");
            uint32_t cp = (uint32_t)std::strtoul(s.substr(i, 4).c_str(), nullptr, 16);
            i += 4;
            if (cp >= 0xD800 && cp <= 0xDBFF && i + 6 <= s.size() && s[i] == '\\' && s[i + 1] == 'u') {
              uint32_t lo = (uint32_t)std::strtoul(s.substr(i + 2, 4).c_str(), nullptr, 16);
              if (lo >= 0xDC00 && lo <= 0xDFFF) { cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00); i += 6; }
            }
            out += utf8From(cp);
            break;
          }
          default: out.push_back(e);
        }
      } else out.push_back(c);
    }
    return fail("niezamknięty napis");
  }
  bool parseArr(Json& out) {
    i++;
    out = Json::array();
    ws();
    if (i < s.size() && s[i] == ']') { i++; return true; }
    while (true) {
      Json v;
      if (!parseValue(v)) return false;
      out.push(v);
      ws();
      if (i < s.size() && s[i] == ',') { i++; continue; }
      if (i < s.size() && s[i] == ']') { i++; return true; }
      return fail("oczekiwano , lub ]");
    }
  }
  bool parseObj(Json& out) {
    i++;
    out = Json::object();
    ws();
    if (i < s.size() && s[i] == '}') { i++; return true; }
    while (true) {
      ws();
      std::string k;
      if (!parseStr(k)) return false;
      ws();
      if (i >= s.size() || s[i] != ':') return fail("oczekiwano :");
      i++;
      Json v;
      if (!parseValue(v)) return false;
      out.set(k, v);
      ws();
      if (i < s.size() && s[i] == ',') { i++; continue; }
      if (i < s.size() && s[i] == '}') { i++; return true; }
      return fail("oczekiwano , lub }");
    }
  }
};
}  // namespace

Json Json::parse(const std::string& text, std::string* err) {
  Parser p(text);
  Json v;
  if (!p.parseValue(v)) {
    if (err) *err = p.err;
    return Json();
  }
  if (err) err->clear();
  return v;
}
Json Json::loadFile(const std::string& path) {
  std::string txt = readFile(path);
  if (txt.empty()) return Json();
  std::string err;
  Json j = Json::parse(txt, &err);
  if (!err.empty()) {
    // raport błędu na stderr — wywołujący sprawdza isNull()
    std::fprintf(stderr, "[json] %s: %s\n", path.c_str(), err.c_str());
  }
  return j;
}

}  // namespace lc
