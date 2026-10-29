// Generator napisów (język polski): teksty, rymy, dedykacje, korekta
#pragma once
#include <string>
#include <vector>

namespace lc {

struct TextSpec {
  std::string topic;        // temat (np. "wędkarstwo")
  std::string occasion;     // okazja (urodziny, ślub, Boże Narodzenie…)
  std::string recipient;    // odbiorca (tata, dziadek, szef…)
  std::string tone = "zartobliwy";   // poważny | zartobliwy | ironiczny | czuly | dosadny
  std::string humor = "suchy";       // suchy | absurdalny | ironiczny | rubaszny
  std::string length = "sredni";     // krotki | sredni | dlugi
  std::string profanity = "brak";    // brak | lagodny | mocny
  std::string dedication;            // np. "dla taty od córki"
  std::string category = "humor";
  std::string keywords;
  std::string exactText;             // jeśli użytkownik podał własny tekst
};

struct TextSuggestion {
  std::string top;        // napis górny (główny)
  std::string bottom;     // napis dolny (puenta)
  std::string dedication;
  std::string tone;
  std::string source;     // skąd (szablon / kombinacja / użytkownik)
  double score = 1.0;
};
std::vector<TextSuggestion> generateTexts(const TextSpec& spec, int count = 4, uint32_t seed = 0);

// Przekształcenia
std::string adjustText(const std::string& text, const std::string& action);
std::string fixPolishSpelling(const std::string& text);
std::string makeRhyme(const std::string& line);
std::string shortenText(const std::string& text, size_t maxChars);
std::string dedicationFor(const std::string& recipient, const std::string& occasion);

// Losowe pomysły (tryb offline, "LOSUJ POMYSŁ")
std::string randomIdea(uint32_t seed);
std::vector<std::string> ideaList();

// Weryfikacja polskich znaków i typowych błędów
struct SpellIssue { std::string word; std::string suggestion; std::string reason; };
std::vector<SpellIssue> checkSpelling(const std::string& text);

}  // namespace lc
