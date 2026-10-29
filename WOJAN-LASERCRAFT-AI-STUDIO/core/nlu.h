// Analiza języka naturalnego (polski): prompt → specyfikacja projektu, komendy edycji
#pragma once
#include "json.h"
#include <string>
#include <vector>

namespace lc {

struct PromptSpec {
  std::string rawPrompt;
  std::string category = "humor";
  std::string style = "retro";
  std::string detail = "sredni";      // niski | sredni | wysoki
  std::string engraveType = "fill+line";
  std::string productId = "tabliczka";
  std::string shape = "rounded";
  std::string material = "sklejka3";
  std::string machineId = "dioda10";
  double width = 200, height = 150;
  bool dimsGiven = false;
  bool requiresCut = true, cutGiven = false;
  std::string primarySubject;     // id emblematu
  std::string secondarySubject;
  std::string topText, bottomText, dedication;
  std::string tone = "zartobliwy";
  std::string occasion;
  std::string fontId;
  std::string frameStyle;
  std::vector<std::string> keywords;
  Json diagnostics = Json::object();
};

PromptSpec analyzePrompt(const std::string& prompt);

struct EditIntent {
  std::string action;     // scale_text | replace_subject | funnier | remove_background | shape_circle |
                          // add_holes | move_text_up | lighten | change_frame | keep_subject_new_text |
                          // simplify | add_dedication | undo | redo | add_qr | rotate | lock | unlock |
                          // set_text | set_product | unknown
  std::string target;     // obiekt/tekst/emblemat
  std::string value;      // nowa wartość (np. "wilk", "20%", tekst)
  double amount = 0;
  std::string raw;
};
std::vector<EditIntent> parseEditCommands(const std::string& text);
const char* actionLabel(const std::string& action);

}  // namespace lc
