// Walidator produkcyjny „SPRAWDŹ PRZED LASEREM"
#pragma once
#include "assets.h"
#include "projector.h"

namespace lc {

enum class Severity { Info, Warning, Error };

struct Issue {
  Severity severity = Severity::Warning;
  std::string code;
  std::string message;
  std::string objectId;
  std::string fixHint;
  bool fixable = false;
};

struct ValidationReport {
  std::vector<Issue> issues;
  int errors = 0, warnings = 0, infos = 0;
  bool ok() const { return errors == 0; }
  void add(Severity s, const std::string& code, const std::string& msg,
           const std::string& objectId = "", const std::string& hint = "", bool fixable = false);
  std::string toText() const;
  std::string toJson() const;
  bool has(const std::string& code) const;
};

ValidationReport validateDesign(const Design& d, FontRegistry& fonts, const AssetStore& assets);
// automatyczne, bezpieczne poprawki (zwraca listę wykonanych)
std::vector<std::string> applySafeFixes(Design& d, FontRegistry& fonts, const AssetStore& assets);
double designEngraveAreaMm2(const Design& d, FontRegistry& fonts, const AssetStore& assets);
double designCutLengthMm(const Design& d);

}  // namespace lc
