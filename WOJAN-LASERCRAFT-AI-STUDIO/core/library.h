// Biblioteka kategorii, szablonów i ostrzeżeń bezpieczeństwa.
// Jedno źródło prawdy dla UI Windows/Android i CLI — eksport do JSON (akcja "library").
#pragma once
#include "json.h"

namespace lc {

struct CategoryDef {
  std::string id, name, emblem, blurb;
  std::vector<std::string> tags;
  std::string suggestedProduct, suggestedStyle;
};

struct TemplateDef {
  std::string id, name, prompt;
  std::string category, product;
};

struct SafetyNote {
  std::string id, material, level;  // poziom: "niebezpieczny" | "uwaga" | "ok"
  std::string text;
};

const std::vector<CategoryDef>& categoryCatalog();   // 50 kategorii + "wlasne"
const CategoryDef* findCategory(const std::string& id);
const std::vector<TemplateDef>& templateCatalog();   // gotowe szablony projektów
const std::vector<SafetyNote>& safetyNotes();        // PVC, ABS, PCV, skóra chromowa itd.
Json libraryJson();
Json safetyJson();

}  // namespace lc
