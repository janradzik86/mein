// Minimalny, samodzielny JSON (parse + emit) — bez zależności
#pragma once
#include <map>
#include <cmath>
#include <memory>
#include <string>
#include <vector>

namespace lc {

class Json {
 public:
  enum Type { NUL, BOOL, NUM, STR, ARR, OBJ };
  Json() : t_(NUL) {}
  Json(bool b) : t_(BOOL), b_(b) {}
  Json(double d) : t_(NUM), n_(d) {}
  Json(int i) : t_(NUM), n_(i) {}
  Json(const char* s) : t_(STR), s_(s) {}
  Json(const std::string& s) : t_(STR), s_(s) {}
  static Json array() { Json j; j.t_ = ARR; return j; }
  static Json object() { Json j; j.t_ = OBJ; return j; }

  Type type() const { return t_; }
  bool isNull() const { return t_ == NUL; }
  bool isArray() const { return t_ == ARR; }
  bool isObject() const { return t_ == OBJ; }
  bool isNumber() const { return t_ == NUM; }
  bool isString() const { return t_ == STR; }

  // odczyt z domyślnymi wartościami
  double num(double d = 0) const { return t_ == NUM ? n_ : (t_ == BOOL ? (b_ ? 1 : 0) : d); }
  int integer(int d = 0) const { return (int)std::lround(num(d)); }
  bool boolean(bool d = false) const { return t_ == BOOL ? b_ : (t_ == NUM ? n_ != 0 : d); }
  std::string str(const std::string& d = "") const { return t_ == STR ? s_ : d; }
  size_t size() const { return t_ == ARR ? arr_.size() : (t_ == OBJ ? obj_.size() : 0); }

  const Json& operator[](size_t i) const;
  const Json& operator[](const std::string& k) const;
  Json& operator[](const std::string& k);
  Json& operator[](size_t i);
  bool has(const std::string& k) const { return t_ == OBJ && obj_.count(k) > 0; }
  std::vector<std::string> keys() const;
  const std::vector<Json>& items() const { return arr_; }
  std::vector<Json>& items() { return arr_; }
  const std::map<std::string, Json>& fields() const { return obj_; }

  void push(const Json& v) { if (t_ != ARR) { t_ = ARR; arr_.clear(); } arr_.push_back(v); }
  void set(const std::string& k, const Json& v) { if (t_ != OBJ) { t_ = OBJ; obj_.clear(); } obj_[k] = v; }

  std::string dump(int indent = -1) const;
  static Json parse(const std::string& text, std::string* err = nullptr);
  static Json loadFile(const std::string& path);

 private:
  void dumpTo(std::string& out, int indent, int level) const;
  Type t_;
  bool b_ = false;
  double n_ = 0;
  std::string s_;
  std::vector<Json> arr_;
  std::map<std::string, Json> obj_;
};

}  // namespace lc
