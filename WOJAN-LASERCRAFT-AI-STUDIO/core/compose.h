// Kompozytor projektu: specyfikacja promptu → gotowy projekt (warstwy, geometria, teksty)
#pragma once
#include "nlu.h"
#include "projector.h"
#include "textgen.h"

namespace lc {

struct GenerateOptions {
  int variants = 1;
  uint32_t seed = 0;
  bool includeFrame = true;
  bool includeOrnaments = true;
  bool includeGuides = true;
  std::string styleOverride;
  std::string variantStyle;     // "rycina" | "komiks" | "retro" | "minimal" | ""
  std::string textTop, textBottom;
  bool forceCut = false, forceNoCut = false;
  bool lockTexts = false;
};

struct GenerateResult {
  Design design;
  std::vector<TextSuggestion> texts;
  std::vector<std::string> log;
  Json metrics = Json::object();
  bool ok = false;
  std::string error;
};

GenerateResult generateDesign(const PromptSpec& spec, FontRegistry& fonts, AssetStore& assets,
                              const GenerateOptions& opt);
std::vector<GenerateResult> generateVariants(const PromptSpec& spec, FontRegistry& fonts,
                                             AssetStore& assets, const GenerateOptions& opt,
                                             const std::vector<std::string>& styles);

struct EditResult {
  bool ok = false;
  std::string message;
  std::vector<std::string> applied;
  std::vector<std::string> failed;
};
EditResult applyEditText(Design& d, const std::string& command, FontRegistry& fonts, AssetStore& assets);
EditResult applyIntent(Design& d, const EditIntent& intent, FontRegistry& fonts, AssetStore& assets);

// pomocnicze
void refreshCutContour(Design& d);
std::string autoFontForStyle(const std::string& style);
double fitTextSize(FontRegistry& fonts, const std::string& text, const std::string& font, double maxWidth,
                   double maxSize, double minSize, double tracking);
Json computeMetrics(const Design& d, FontRegistry& fonts, const AssetStore& assets);

}  // namespace lc
