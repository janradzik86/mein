#include "nlu.h"
#include "common.h"
#include "emblems.h"
#include "projector.h"
#include <cstdlib>
#include <cctype>

namespace lc {

namespace {

std::string norm(const std::string& s) { return lowerAscii(stripDiacritics(s)); }

// Normalizacja ZACHOWUJĄCA mapę offsetów: każdy bajt tekstu znormalizowanego
// wskazuje na offset bajtu w oryginale. Bez tego wycinanie fragmentów promptu
// po indeksach z wersji bez diakrytyków cięło znaki UTF-8 w połowie.
struct NormMap {
  std::string s;
  std::vector<size_t> toOrig;
  size_t origSize = 0;
};
NormMap normMap(const std::string& orig) {
  NormMap m;
  m.origSize = orig.size();
  size_t off = 0;
  for (auto& ch : utf8Chars(orig)) {
    std::string n = lowerAscii(stripDiacritics(ch));
    if (n.empty()) n = ch;
    // znaki typograficzne → odpowiedniki ASCII (offsety pozostają poprawne)
    if (n == "×" || n == "✕" || n == "⋅") n = "x";
    else if (n == "—" || n == "–" || n == "‑") n = "-";
    else if (n == "„" || n == "”" || n == "“" || n == "«" || n == "»") n = "\"";
    for (char c : n) {
      m.s.push_back(c);
      m.toOrig.push_back(off);
    }
    off += ch.size();
  }
  m.toOrig.push_back(orig.size());
  return m;
}
size_t origAt(const NormMap& m, size_t i) {
  return i < m.toOrig.size() ? m.toOrig[i] : m.origSize;
}
// obcina niepełny znak UTF-8 na końcu (zabezpieczenie przed uszkodzonym JSON)
std::string cutUtf8(const std::string& s) {
  size_t i = s.size();
  while (i > 0 && ((unsigned char)s[i - 1] & 0xC0) == 0x80) i--;
  if (i == 0) return s;
  unsigned char c = (unsigned char)s[i - 1];
  size_t need = c < 0x80 ? 1 : (c >> 5) == 0x6 ? 2 : (c >> 4) == 0xE ? 3 : (c >> 3) == 0x1E ? 4 : 1;
  if (s.size() - (i - 1) < need) return s.substr(0, i - 1);
  return s;
}
// usuwa wiodące słowa typu "napis"/"tekst"/"napis:" z wyciągniętego fragmentu
std::string stripTextLead(std::string f) {
  f = trim(f);
  for (const char* lead : {"napisem", "napisie", "napis", "tekstem", "tekst", "tresc", "trescia"}) {
    std::string n = norm(f);
    std::string l = std::string(lead);
    if (n.rfind(l, 0) == 0) {
      size_t cut = 0;
      size_t cnt = 0;
      for (auto& ch : utf8Chars(f)) {
        cut += ch.size();
        cnt += norm(ch).size();
        if (cnt >= l.size()) break;
      }
      std::string rest = f.substr(cut);
      rest = trim(rest);
      if (!rest.empty() && (rest[0] == ':' || rest[0] == '=')) rest = trim(rest.substr(1));
      f = trim(rest);
      break;
    }
  }
  return f;
}

// znajdź liczbę po słowie kluczowym (z tolerancją 20 znaków w przód)
bool numberAfter(const std::string& low, const std::vector<std::string>& keys, double& out) {
  for (auto& k : keys) {
    size_t p = 0;
    while ((p = low.find(k, p)) != std::string::npos) {
      size_t i = p + k.size();
      // pomiń spacje, dwukropek, "="
      while (i < low.size() && (low[i] == ' ' || low[i] == ':' || low[i] == '=')) i++;
      size_t st = i;
      bool dot = false;
      while (i < low.size() && ((low[i] >= '0' && low[i] <= '9') || (low[i] == '.' && !dot))) {
        if (low[i] == '.') dot = true;
        i++;
      }
      if (i > st) {
        std::string numStr = low.substr(st, i - st);
        double v = std::atof(numStr.c_str());
        // jednostka
        std::string rest = low.substr(i, 6);
        if (rest.find("cm") != std::string::npos) v *= 10;
        if (v > 0) { out = v; return true; }
      }
      p += k.size();
    }
  }
  return false;
}

std::string extractQuoted(const std::string& s, size_t& from) {
  const char* opens[] = {"\"", "„", "«", "’", "'"};
  const char* closes[] = {"\"", "”", "»", "’", "'"};
  for (int i = 0; i < 5; i++) {
    size_t a = s.find(opens[i], from);
    if (a == std::string::npos) continue;
    size_t b = s.find(closes[i], a + 1);
    if (b == std::string::npos || b == a + 1) continue;
    from = b + 1;
    return trim(s.substr(a + 1, b - a - 1));
  }
  return "";
}

// wykryj fragmenty pisane WERSALIKAMI (typowy sposób podawania napisów)
std::vector<std::string> extractCaps(const std::string& s) {
  std::vector<std::string> out;
  std::string cur;
  // "…2027 200" + "x150" w prompcie → ostatnia liczba to wymiar, nie napis
  auto stripDim = [&](std::string t) -> std::string {
    t = trim(t);
    size_t sp = t.find_last_of(' ');
    if (sp == std::string::npos) return t;
    std::string tail = t.substr(sp + 1);
    if (tail.size() < 2 || tail.size() > 5) return t;
    for (char c : tail)
      if (c < '0' || c > '9') return t;
    if (s.find(tail + "x") != std::string::npos || s.find(tail + "X") != std::string::npos ||
        s.find(tail + "\xC3\x97") != std::string::npos)
      return trim(t.substr(0, sp));
    return t;
  };
  for (auto cp : utf8Decode(s)) {
    // wielkie litery: ASCII, Latin-1 (Ó), Latin Extended-A (Ą Ć Ę Ł Ń Ś Ź Ż)
    bool upper = (cp >= 'A' && cp <= 'Z') || (cp >= 0xC0 && cp <= 0xDE && cp != 0xD7) ||
                 cp == 0x104 || cp == 0x106 || cp == 0x118 || cp == 0x139 || cp == 0x141 ||
                 cp == 0x143 || cp == 0x15A || cp == 0x179 || cp == 0x17B;
    bool digit = cp >= '0' && cp <= '9';
    if (upper || digit || cp == ' ' || cp == '!' || cp == '?' || cp == ',' || cp == '.' ||
        cp == '-' || cp == ':' || cp == '/') {
      if (upper || digit) { cur += utf8From(cp); continue; }
      // kropka/myślnik w środku (daty 12.06.2027, numery 12-06) — zachowaj znak
      if (cp == '.' || cp == '-' || cp == '/' || cp == ':') {
        if (!cur.empty() && cur.back() != ' ') cur += utf8From(cp);
        continue;
      }
      if (!cur.empty()) cur += " ";
      continue;
    }
    if (!cur.empty()) {
      std::string t = stripDim(cur);
      // co najmniej 2 znaki wersalikami
      int upperCount = 0;
      for (auto c2 : utf8Decode(t)) if (c2 >= 'A' && c2 <= 'Z') upperCount++;
      if (upperCount >= 2 && t.size() >= 3) out.push_back(t);
      cur.clear();
    }
  }
  if (!cur.empty()) {
    std::string t = stripDim(cur);
    if (t.size() >= 3) out.push_back(t);
  }
  return out;
}

// Znajdź słowo kluczowe. Krótkie słowa (<=4 znaki) muszą być osobnymi słowami,
// żeby "gra" nie łapało "grawer", a "las" nie łapało "klasy". Dłuższe traktujemy
// jak rdzenie ("tabliczk" → "tabliczka", "tabliczkę").
size_t findKeyword(const std::string& low, const std::string& kw) {
  if (kw.empty()) return std::string::npos;
  bool shortKw = kw.size() <= 4;
  size_t p = 0;
  while ((p = low.find(kw, p)) != std::string::npos) {
    bool leftOk = p == 0 || !std::isalnum((unsigned char) low[p - 1]);
    size_t e = p + kw.size();
    bool rightOk = e >= low.size() || !std::isalnum((unsigned char) low[e]);
    if (leftOk && (!shortKw || rightOk)) return p;
    p += 1;
  }
  return std::string::npos;
}

std::string matchProduct(const std::string& low, bool& cutDetected) {
  struct P { const char* keys; const char* id; };
  static const P list[] = {
      {"piersiowk|piersiówk|flask", "piersiowka"},
      {"kubek|kubka|kubki", "kubek"},
      {"brelok|breloka|breloki", "brelok"},
      {"niesmiertelnik|nieśmiertelnik", "niesmiertelnik"},
      {"medalion", "medalion"},
      {"medal|medalu", "medal"},
      {"naszywka|naszywk", "naszywka"},
      {"zawieszka|zawieszk", "zawieszka"},
      {"zakladka|zakładka", "zakladka"},
      {"magnes", "magnes"},
      {"deska|deski|deskę", "deska"},
      {"skrzynk", "skrzynka"},
      {"podkladka|podkładk", "podkladka"},
      {"ozdob", "ozdoba"},
      {"etui", "etui"},
      {"portfel", "portfel"},
      {"tarcza|tarczę|tarczy", "tarcza"},
      {"szyld", "szyld"},
      {"emblemat", "tarcza"},
      {"tabliczk|tablic|znaczek|plakiet", "tabliczka"},
      {"pisank|jajk", "ozdoba"},
      {"amulet|talizman|wisior|naszyjnik", "medalion"},
      {"dowoln|dowolny przedmiot", "dowolny"},
  };
  // decyduje NAJWCZEŚNIEJSZE wystąpienie, nie kolejność tabeli
  // ("Podkładka pod kubek" → podkładka, nie kubek)
  size_t best = std::string::npos;
  std::string bestId;
  for (auto& p : list) {
    for (auto& alt : split(p.keys, '|')) {
      size_t pos = findKeyword(low, alt);
      if (pos != std::string::npos && (best == std::string::npos || pos < best)) {
        best = pos;
        bestId = p.id;
      }
    }
  }
  (void)cutDetected;
  return bestId;
}

std::string matchMaterial(const std::string& low) {
  if (low.find("sklejk") != std::string::npos) {
    if (low.find("5 mm") != std::string::npos || low.find("5mm") != std::string::npos) return "sklejka5";
    if (low.find("2 mm") != std::string::npos || low.find("2mm") != std::string::npos) return "sklejka2";
    return "sklejka3";
  }
  if (low.find("mdf") != std::string::npos) return "mdf3";
  if (low.find("skora syntetyczna") != std::string::npos || low.find("pu ") != std::string::npos) return "skora_syn";
  if (low.find("skor") != std::string::npos) return "skora";
  if (low.find("nierdzew") != std::string::npos) return "stal";
  if (low.find("alumini") != std::string::npos) return "aluminium";
  if (low.find("akryl") != std::string::npos || low.find("plexi") != std::string::npos) return "akryl";
  if (low.find("szkl") != std::string::npos) return "szklo";
  if (low.find("drewn") != std::string::npos) return "drewno";
  if (low.find("ceramik") != std::string::npos) return "ceramika";
  return "";
}

std::string matchStyle(const std::string& low) {
  if (low.find("gotyk") != std::string::npos || low.find("gothic") != std::string::npos) return "gotyk";
  if (low.find("komiks") != std::string::npos || low.find("comic") != std::string::npos) return "komiks";
  if (low.find("minimal") != std::string::npos || low.find("prost") != std::string::npos) return "minimal";
  if (low.find("vintage") != std::string::npos) return "vintage";
  if (low.find("retro") != std::string::npos) return "retro";
  if (low.find("rycin") != std::string::npos || low.find("sztych") != std::string::npos ||
      low.find("drzeworyt") != std::string::npos)
    return "rycina";
  if (low.find("elegan") != std::string::npos || low.find("klasycz") != std::string::npos) return "elegancki";
  if (low.find("nowoczes") != std::string::npos || low.find("modern") != std::string::npos) return "nowoczesny";
  if (low.find("fantasy") != std::string::npos || low.find("bajk") != std::string::npos) return "fantasy";
  if (low.find("slub") != std::string::npos || low.find("ślub") != std::string::npos) return "elegancki";
  return "";
}

std::string matchCategory(const std::string& low) {
  struct C { const char* word; const char* cat; };
  static const C list[] = {
      {"wedk|wędk|ryb", "wedkarstwo"}, {"mysliw|myśliw|jelen|las", "myslistwo"},
      {"rolnic|traktor|gospodar", "rolnictwo"}, {"warsztat|narzędzi|mechanik", "warsztat"},
      {"garaz|garaż|auto|samochod|motoryz", "motoryzacja"},
      {"polityk|wybor|rząd|sejm|satyr politycz", "satyra_polityczna"},
      {"satyr społ|urzęd|kolejka", "satyra_spoleczna"},
      {"boże narodz|boze narodz|święta|swieta|choink", "boze_narodzenie"},
      {"wielkan|jajk", "wielkanoc"}, {"walentyn|kocham|milosc|miłość", "walentynki"},
      {"urodzin|sto lat", "urodziny"}, {"imienin", "imieniny"},
      {"ślub|slub|wesel", "sluby"}, {"rocznic", "rocznice"},
      {"dzień matki|dzien matki|dla mamy|mama", "dzien_matki"},
      {"dzień ojca|dzien ojca|dla taty|tata", "dzien_ojca"},
      {"babci", "dzien_babci"}, {"dziadk", "dzien_dziadka"},
      {"emeryt", "emerytura"}, {"halloween|dyni|strasz", "halloween"},
      {"patriot|orzeł|flaga|niepodleg", "patriotyczne"}, {"słowia|slowia", "slowianskie"},
      {"fantasy|smok|rycer|mag", "fantastyka"}, {"gotyk|czaszka|memento", "gotyk"},
      {"zwierz|pies|kot|wilk", "zwierzeta"}, {"wilk", "wilki"},
      {"przyrod|gór|las|drzewo", "przyroda"}, {"sport|trening|siłowni", "sport"},
      {"muzyk|gitara|zespół", "muzyka"}, {"gaming|gra|konsol", "gaming"},
      {"relig|bóg|bog|wiara|modlitw", "religijne"}, {"retro|vintage", "retro"},
      {"minimal", "minimalistyczne"}, {"komiks", "komiksowe"}, {"portret", "portrety"},
      {"fotograwer|zdjęc|zdjec", "fotograwer"}, {"logo|firm", "logo"},
      {"emblemat|herb|tarcza", "emblematy"}, {"dedykacj", "dedykacje"},
      {"prezent|podarun", "prezenty"}, {"pamiąt|pamiat", "pamiatki"},
      {"dom|rodzin", "dom_i_rodzina"}, {"kuchni", "dom_i_rodzina"},
      {"mandala|geometryczn|podkladk|podkładk", "minimalistyczne"},
  };
  size_t best = std::string::npos;
  std::string bestCat;
  for (auto& c : list) {
    for (auto& alt : split(c.word, '|')) {
      size_t pos = findKeyword(low, alt);
      if (pos != std::string::npos && (best == std::string::npos || pos < best)) {
        best = pos;
        bestCat = c.cat;
      }
    }
  }
  return bestCat;
}

}  // namespace

PromptSpec analyzePrompt(const std::string& prompt) {
  PromptSpec s;
  s.rawPrompt = prompt;
  NormMap nm = normMap(prompt);
  const std::string& low = nm.s;
  s.diagnostics.set("promptLength", (double)prompt.size());

  // --- wymiary ---
  double w = 0, h = 0;
  bool dims = false;
  if (numberAfter(low, {"szerokosc", "szerokość", "width", "szer."}, w)) dims = true;
  if (numberAfter(low, {"wysokosc", "wysokość", "height", "wys."}, h)) dims = true;
  if (!dims) {
    // wzorzec 200x150
    for (size_t i = 0; i + 3 < low.size(); i++) {
      if (low[i] >= '0' && low[i] <= '9') {
        size_t j = i;
        while (j < low.size() && ((low[j] >= '0' && low[j] <= '9') || low[j] == '.')) j++;
        if (j < low.size() && low[j] == 'x') {
          size_t k = j + 1;
          size_t st = k;
          while (k < low.size() && ((low[k] >= '0' && low[k] <= '9') || low[k] == '.')) k++;
          if (k > st) {
            std::string a = low.substr(i, j - i), b = low.substr(st, k - st);
            w = std::atof(a.c_str());
            h = std::atof(b.c_str());
            // jednostka: bezpośrednio po drugiej liczbie albo gdziekolwiek w prompcie
            std::string after = low.substr(k, 4);
            bool cm = after.find("cm") != std::string::npos;
            if (!cm && low.find("cm") != std::string::npos && low.find("mm") == std::string::npos) cm = true;
            if (cm) { w *= 10; h *= 10; }
            dims = true;
            break;
          }
        }
        i = j;
      }
    }
  }
  if (dims && w > 0 && h > 0) {
    s.width = w;
    s.height = h;
    s.dimsGiven = true;
  }

  // --- produkt ---
  bool cutDetected = false;
  std::string prod = matchProduct(low, cutDetected);
  const ProductDef* pdef = prod.empty() ? findProduct("tabliczka") : findProduct(prod);
  if (pdef) {
    s.productId = pdef->id;
    s.shape = pdef->shape;
    s.material = pdef->defaultMaterial;
    s.requiresCut = pdef->cut;
    if (!s.dimsGiven) { s.width = pdef->w; s.height = pdef->h; }
  }
  // --- materiał ---
  std::string mat = matchMaterial(low);
  if (!mat.empty()) s.material = mat;
  // --- cięcie / gotowy przedmiot ---
  if (low.find("bez ciecia") != std::string::npos || low.find("bez cięcia") != std::string::npos ||
      low.find("gotowym przedmiocie") != std::string::npos || low.find("tylko grawer") != std::string::npos ||
      low.find("bez wycinania") != std::string::npos) {
    s.requiresCut = false;
    s.cutGiven = true;
  } else if (low.find("wycinan") != std::string::npos || low.find("do wyciecia") != std::string::npos ||
             low.find("kontur ciecia") != std::string::npos || low.find("kontur cięcia") != std::string::npos) {
    s.requiresCut = true;
    s.cutGiven = true;
  }
  // --- styl / detal / kategoria ---
  std::string st = matchStyle(low);
  if (!st.empty()) s.style = st;
  if (low.find("duzo detal") != std::string::npos || low.find("dużo detal") != std::string::npos ||
      low.find("szczegol") != std::string::npos || low.find("szczegół") != std::string::npos ||
      low.find("bogat") != std::string::npos)
    s.detail = "wysoki";
  else if (low.find("prost") != std::string::npos || low.find("minimal") != std::string::npos ||
           low.find("oszczed") != std::string::npos)
    s.detail = "niski";
  std::string cat = matchCategory(low);
  if (!cat.empty()) s.category = cat;
  // --- ton / okazja ---
  if (low.find("smieszn") != std::string::npos || low.find("śmieszn") != std::string::npos ||
      low.find("zabawn") != std::string::npos || low.find("dowcip") != std::string::npos ||
      low.find("humor") != std::string::npos)
    s.tone = "zartobliwy";
  else if (low.find("poważn") != std::string::npos || low.find("powazn") != std::string::npos)
    s.tone = "poważny";
  else if (low.find("iron") != std::string::npos || low.find("satyr") != std::string::npos)
    s.tone = "ironiczny";
  else if (low.find("czuł") != std::string::npos || low.find("czul") != std::string::npos ||
           low.find("wzrusz") != std::string::npos)
    s.tone = "czuly";
  for (const char* occ : {"urodziny", "imieniny", "ślub", "slub", "rocznic", "święta", "swieta",
                          "wielkanoc", "walentynki", "emerytur", "halloween"})
    if (low.find(occ) != std::string::npos) { s.occasion = occ; break; }

  // --- napisy ---
  size_t pos = 0;
  std::vector<std::string> quoted;
  for (int i = 0; i < 4; i++) {
    std::string q = extractQuoted(prompt, pos);
    if (q.empty()) break;
    quoted.push_back(q);
  }
  std::vector<std::string> caps = extractCaps(prompt);
  // napis po słowie "napis"/"tekst"
  auto textAfterKeyword = [&](const std::vector<std::string>& keys) -> std::string {
    for (auto& k : {std::string("napis "), std::string("napis: "), std::string("napisem "),
                    std::string("tekst "), std::string("tekstem "), std::string("napis \""),
                    std::string("napis „")}) {
      size_t p2 = low.find(k);
      if (p2 == std::string::npos) continue;
      size_t st2 = origAt(nm, p2 + k.size());
      size_t en = prompt.find_first_of("\n.;", st2);
      std::string frag = cutUtf8(prompt.substr(st2, en == std::string::npos ? std::string::npos : en - st2));
      return stripTextLead(frag);
    }
    (void)keys;
    return "";
  };
  std::string topAfter = textAfterKeyword({"napis"});
  // "na górze X", "na dole Y"
  auto afterPhrase = [&](const char* phrase) -> std::string {
    std::string ph = norm(phrase);
    size_t p2 = low.find(ph);
    if (p2 == std::string::npos) return "";
    size_t st2 = origAt(nm, p2 + ph.size());
    while (st2 < prompt.size() &&
           (prompt[st2] == ' ' || prompt[st2] == ':' || prompt[st2] == '"' ||
            prompt.compare(st2, std::string("„").size(), "„") == 0))
      st2++;
    // do przecinka/kropki/średnika lub 60 znaków
    size_t en = prompt.find_first_of(",;.\n", st2);
    std::string frag = cutUtf8(prompt.substr(st2, en == std::string::npos ? std::string::npos : en - st2));
    if (frag.size() > 80) frag = cutUtf8(frag.substr(0, 80));
    return stripTextLead(frag);
  };
  // "ANNA I PIOTR 12.06.2027, 200x150 mm" → napis bez fragmentu wymiarów
  auto cutAtSize = [](std::string t) -> std::string {
    for (size_t i = 1; i + 1 < t.size(); i++) {
      if (!(t[i] == 'x' || t[i] == 'X')) continue;
      if (!(t[i - 1] >= '0' && t[i - 1] <= '9')) continue;
      size_t j = i + 1;
      if (!(t[j] >= '0' && t[j] <= '9')) continue;
      while (j < t.size() && t[j] >= '0' && t[j] <= '9') j++;
      std::string head = trim(t.substr(0, i));
      while (!head.empty() && (head.back() == ' ' || head.back() == ',' || head.back() == ';' ||
                               head.back() == ':' || head.back() == '-'))
        head.pop_back();
      // ostatnia cyfra przed "x" należy do wymiaru, nie do napisu
      while (!head.empty() && head.back() >= '0' && head.back() <= '9') head.pop_back();
      head = trim(head);
      return head;
    }
    return t;
  };
  std::string top = afterPhrase("na gorze");
  std::string bottom = afterPhrase("na dole");
  top = cutAtSize(top);
  bottom = cutAtSize(bottom);
  if (top.empty() && !topAfter.empty()) top = cutAtSize(topAfter);
  if (top.empty() && !quoted.empty()) top = quoted[0];
  if (bottom.empty() && quoted.size() > 1) bottom = quoted[1];
  if (top.empty() && caps.size() > 0) top = cutAtSize(caps[0]);
  if (bottom.empty() && caps.size() > 1) bottom = cutAtSize(caps[1]);
  if (bottom.empty() && caps.size() > 2) bottom = cutAtSize(caps[2]);
  // dedykacja
  size_t dp = low.find("dedykacj");
  if (dp != std::string::npos) {
    std::string frag = cutUtf8(prompt.substr(origAt(nm, dp), 90));
    size_t colon = frag.find(':');
    if (colon != std::string::npos) s.dedication = trim(frag.substr(colon + 1));
  }
  // odetnij końcowe instrukcje techniczne wciągnięte do napisu
  // ("WILK Z BIESZCZAD, tylko grawer bez cięcia", "... sklejka 3 mm")
  auto cutInstructions = [&](std::string t) -> std::string {
    static const char* stop[] = {"tylko", "bez", "grawer", "cien", "cut", "sklejk", "mdf", "skor",
                                 "stal", "nierdzew", "alumini", "akryl", "plexi", "ceramik",
                                 "drewn", "szkl", "material", "wymiar", "mm", "cm", "wariant",
                                 "styl", "ramka", "font", "czcionk"};
    std::vector<std::string> parts;
    std::string cur;
    for (size_t i = 0; i <= t.size(); i++) {
      if (i == t.size() || t[i] == ',') {
        parts.push_back(cur);
        cur.clear();
        continue;
      }
      cur.push_back(t[i]);
    }
    // zachowaj pierwszy segment zawsze, kolejne tylko jeśli nie są instrukcją
    std::vector<std::string> keep;
    for (size_t i = 0; i < parts.size(); i++) {
      std::string n = norm(parts[i]);
      bool isInstr = false;
      for (auto& w : stop) {
        size_t p2 = n.find(w);
        if (p2 == std::string::npos) continue;
        // "mm" tylko jako osobne słowo
        if (std::string(w) == "mm" || std::string(w) == "cm") {
          if (p2 > 0 && (std::isalnum((unsigned char) n[p2 - 1]) || n[p2 - 1] == '.')) continue;
          if (p2 + std::string(w).size() < n.size() &&
              std::isalnum((unsigned char) n[p2 + std::string(w).size()]))
            continue;
        }
        isInstr = true;
        break;
      }
      if (i == 0) { keep.push_back(parts[i]); continue; }
      if (isInstr) break;
      keep.push_back(parts[i]);
    }
    std::string out;
    for (size_t i = 0; i < keep.size(); i++) {
      std::string seg = trim(keep[i]);
      if (seg.empty()) continue;
      if (!out.empty()) out += ", ";
      out += seg;
    }
    return out;
  };
  top = cutInstructions(top);
  bottom = cutInstructions(bottom);

  // usuń końcową interpunkcję — na grawerze mało kto chce "ŚWIĄT,"
  auto stripTail = [](std::string t) -> std::string {
    while (!t.empty() && (t.back() == ',' || t.back() == ';' || t.back() == ':' || t.back() == '.' ||
                          t.back() == '-' || t.back() == ' '))
      t.pop_back();
    return trim(t);
  };
  top = stripTail(top);
  bottom = stripTail(bottom);
  s.topText = top;
  s.bottomText = bottom;
  s.diagnostics.set("quotedCount", (double)quoted.size());
  s.diagnostics.set("capsCount", (double)caps.size());

  // --- temat (emblemat) ---
  // najpierw szukaj w całym prompcie
  std::string subj = emblemBestMatch(prompt);
  if (subj.empty()) {
    // spróbuj po słowach kluczowych kategorii
    subj = emblemBestMatch(s.category);
  }
  s.primarySubject = subj;
  // drugi temat: kolejne słowo kluczowe
  std::string second = emblemBestMatch(stripDiacritics(low.substr(low.size() > 60 ? low.size() - 60 : 0)));
  if (!second.empty() && second != subj) s.secondarySubject = second;

  // --- maszyna ---
  if (low.find("fiber") != std::string::npos) s.machineId = "fiber20";
  else if (low.find("co2") != std::string::npos || low.find("co₂") != std::string::npos) s.machineId = "co2_600";
  else if (low.find("20 w") != std::string::npos) s.machineId = "dioda20";
  // --- font / ramka wg stylu ---
  s.fontId = (s.style == "gotyk") ? "cinzel_deco" : (s.style == "elegancki") ? "playfair" :
             (s.style == "fantasy") ? "pirata" : (s.style == "minimal") ? "oswald" :
             (s.style == "komiks") ? "barlow" : "anton";
  s.frameStyle = frameBestMatch(s.style);

  // --- słowa kluczowe ---
  for (auto& w2 : split(low, ' ')) {
    if (w2.size() < 4) continue;
    if (w2 == "oraz" || w2 == "jest" || w2 == "który" || w2 == "ktora" || w2 == "tego" || w2 == "bardzo") continue;
    s.keywords.push_back(w2);
    if (s.keywords.size() >= 24) break;
  }
  return s;
}

std::vector<EditIntent> parseEditCommands(const std::string& text) {
  std::vector<EditIntent> out;
  // dziel po kropkach/średnikach/" oraz "/" i " tylko gdy wygląda to na nowe polecenie
  std::string clean = text;
  for (auto& sep : {std::string(";"), std::string(". "), std::string("\n")}) {
    clean = replaceAll(clean, sep, "|");
  }
  for (auto& part : split(clean, '|')) {
    std::string p = trim(part);
    if (p.size() < 3) continue;
    NormMap nm = normMap(p);
    const std::string& low = nm.s;
    EditIntent e;
    e.raw = p;
    auto has = [&](std::initializer_list<const char*> words) {
      for (auto w : words) if (low.find(w) != std::string::npos) return true;
      return false;
    };
    if (has({"cofnij", "undo"})) e.action = "undo";
    else if (has({"ponów", "ponow", "redo"})) e.action = "redo";
    else if (has({"zwieksz", "zwiększ", "powieksz", "powiększ", "wiekszy napis", "większy napis"})) {
      e.action = "scale_text";
      // procent
      size_t pc = p.find('%');
      if (pc != std::string::npos) {
        size_t i = pc;
        while (i > 0 && ((p[i - 1] >= '0' && p[i - 1] <= '9') || p[i - 1] == ' ' || p[i - 1] == '.')) i--;
        std::string num = trim(p.substr(i, pc - i));
        if (!num.empty()) e.amount = std::atof(num.c_str()) / 100.0;
      }
      if (e.amount == 0) e.amount = 0.2;
      if (has({"mniej", "zmniejsz"})) e.amount = -std::fabs(e.amount);
    } else if (has({"zmniejsz", "pomniejsz"})) {
      e.action = "scale_text";
      e.amount = -0.2;
      size_t pc = p.find('%');
      if (pc != std::string::npos) {
        size_t i = pc;
        while (i > 0 && ((p[i - 1] >= '0' && p[i - 1] <= '9') || p[i - 1] == ' ')) i--;
        std::string num = trim(p.substr(i, pc - i));
        if (!num.empty()) e.amount = -std::atof(num.c_str()) / 100.0;
      }
    } else if (has({"zamien", "zamień", "wymien", "wymień", "zamiast"})) {
      e.action = "replace_subject";
      std::string subj = emblemBestMatch(p);
      // temat "na X"
      size_t on = low.find(" na ");
      if (on != std::string::npos) {
        std::string target = cutUtf8(p.substr(origAt(nm, on + 4)));
        std::string s2 = emblemBestMatch(target);
        if (!s2.empty()) subj = s2;
      }
      e.value = subj;
    } else if (has({"smieszniejsz", "śmieszniejsz", "zabawniejsz", "puent", "dowcipniejsz"})) e.action = "funnier";
    else if (has({"usun tlo", "usuń tło", "bez tla", "bez tła", "usun tło"})) e.action = "remove_background";
    else if (has({"okragl", "okrągł", "kolisty", "circle"})) e.action = "shape_circle";
    else if (has({"otwor", "otwór", "dziur", "montazow", "montażow"})) {
      e.action = "add_holes";
      size_t i = 0;
      while (i < low.size() && !(low[i] >= '0' && low[i] <= '9')) i++;
      double n = 2;
      if (i < low.size()) n = std::atof(low.substr(i, 2).c_str());
      e.amount = n > 0 ? n : 2;
      if (has({"jeden", "jedno"})) e.amount = 1;
      if (has({"dwa", "dwie"})) e.amount = 2;
      if (has({"trzy"})) e.amount = 3;
      if (has({"cztery"})) e.amount = 4;
    } else if (has({"przesun napis", "przesuń napis", "wyzej", "wyżej"})) e.action = "move_text_up";
    else if (has({"nizej", "niżej"})) e.action = "move_text_down";
    else if (has({"rozjasnij", "rozjaśnij", "jasniejsz", "jaśniejsz"})) e.action = "lighten";
    else if (has({"ramk", "border", "obramow"})) e.action = "change_frame";
    else if (has({"zostaw postac", "zostaw postać", "zostaw grafike", "zostaw grafikę", "zmien tekst", "zmień tekst", "inny tekst", "nowy tekst"}))
      e.action = "keep_subject_new_text";
    else if (has({"uprosc", "uprość", "prostsze", "szybki grawer", "mniej detal"})) e.action = "simplify";
    else if (has({"dedykacj"})) {
      e.action = "add_dedication";
      size_t colon = p.find(':');
      e.value = colon != std::string::npos ? trim(p.substr(colon + 1)) : "";
    }
    else if (has({"qr"})) {
      e.action = "add_qr";
      size_t i = low.find("http");
      if (i != std::string::npos) {
        size_t oi = origAt(nm, i);
        size_t en = p.find(' ', oi);
        e.value = p.substr(oi, en == std::string::npos ? std::string::npos : en - oi);
      }
    }
    else if (has({"obroc", "obróć", "rotate"})) {
      e.action = "rotate";
      size_t i = 0;
      while (i < low.size() && !(low[i] >= '0' && low[i] <= '9')) i++;
      e.amount = i < low.size() ? std::atof(low.substr(i, 3).c_str()) : 90;
    }
    else if (has({"zablokuj"})) e.action = "lock";
    else if (has({"odblokuj"})) e.action = "unlock";
    else if (has({"napisz", "napis powinien", "zmien napis", "zmień napis", "tekst na"})) {
      e.action = "set_text";
      size_t q = p.find('"');
      if (q == std::string::npos) q = p.find("„");
      if (q != std::string::npos) {
        size_t q2 = p.find_first_of("\"”", q + 1);
        if (q2 != std::string::npos) e.value = trim(p.substr(q + 1, q2 - q - 1));
      } else {
        size_t i = low.find("napis");
        if (i != std::string::npos) e.value = stripTextLead(trim(p.substr(origAt(nm, i))));
      }
    } else if (has({"gotowy przedmiot", "bez ciecia", "bez cięcia", "bez wycinania"})) {
      e.action = "no_cut";
    } else if (has({"dodaj ramke", "dodaj ramkę"})) {
      e.action = "change_frame";
      e.value = frameBestMatch(p);
    } else if (has({"wiecej miejsca", "więcej miejsca", "dopasuj tekst"})) {
      e.action = "fit_text";
    } else {
      e.action = "unknown";
    }
    out.push_back(e);
  }
  return out;
}

const char* actionLabel(const std::string& action) {
  if (action == "scale_text") return "skalowanie tekstu";
  if (action == "replace_subject") return "zamiana motywu";
  if (action == "funnier") return "zmiana puenty";
  if (action == "remove_background") return "usuwanie tła";
  if (action == "shape_circle") return "zmiana kształtu na okrągły";
  if (action == "add_holes") return "dodawanie otworów";
  if (action == "move_text_up") return "przesunięcie napisu w górę";
  if (action == "move_text_down") return "przesunięcie napisu w dół";
  if (action == "lighten") return "rozjaśnienie graweru";
  if (action == "change_frame") return "zmiana ramki";
  if (action == "keep_subject_new_text") return "zachowanie postaci, nowy tekst";
  if (action == "simplify") return "uproszczenie grafiki";
  if (action == "add_dedication") return "dodanie dedykacji";
  if (action == "undo") return "cofnięcie";
  if (action == "redo") return "ponowienie";
  if (action == "add_qr") return "dodanie kodu QR";
  if (action == "rotate") return "obrót";
  if (action == "lock") return "blokada obiektu";
  if (action == "unlock") return "odblokowanie obiektu";
  if (action == "set_text") return "zmiana tekstu";
  if (action == "no_cut") return "tryb gotowego przedmiotu";
  if (action == "fit_text") return "dopasowanie tekstu";
  return "nieznane polecenie";
}

}  // namespace lc
