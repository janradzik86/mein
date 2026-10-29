#include "textgen.h"
#include "common.h"
#include <random>

namespace lc {

namespace {

struct Pair {
  const char* top;
  const char* bottom;
  const char* tone;
};

// Bazowe pary napisów (temat: wędkarstwo)
const std::vector<Pair>& fishingPairs() {
  static const std::vector<Pair> v = {
      {"KRÓL ŁOWISKA", "RYBY SIĘ MNIE BOJĄ, A ŻONA MNIE SZUKA", "zartobliwy"},
      {"WĘDKARZ", "CISZA, SPOKÓJ I JEDNA RYBA NA CAŁY DZIEŃ", "zartobliwy"},
      {"TU ŁOWIĘ", "TU ODPOCZYWAM, TU NIC NIE ROBIĘ", "suchy"},
      {"NAJLEPSZY POŁÓW", "BYŁ WTEDY, GDY NIKT NIE WIDZIAŁ", "ironiczny"},
      {"RYBY BIORĄ", "PODOBNO WCZORAJ", "suchy"},
      {"MISTRZ SPOKOJU", "Z WĘDKĄ W RĘKU I RYBĄ W OPOWIEŚCIACH", "czuly"},
      {"SZCZUPAK 12 KG", "PROSZĘ NIE PYTAĆ O ZDJĘCIE", "ironiczny"},
      {"DOM WĘDKARZA", "TU RYBY MAJĄ GŁOS DECYDUJĄCY", "zartobliwy"},
      {"WĘDKA, KAWKA, SPOKÓJ", "TAKI PLAN NA CAŁY DZIEŃ", "czuly"},
      {"ZŁOWIĆ RYBĘ", "TO DOPIERO POCZĄTEK, POTEM TRZEBA JĄ OPOWIEDZIEĆ", "absurdalny"},
  };
  return v;
}
const std::vector<Pair>& huntingPairs() {
  static const std::vector<Pair> v = {
      {"MYŚLIWY", "WSTAJE PRZED WSZYSTKIMI, WIDZI NAJWIĘCEJ", "poważny"},
      {"TU MIESZKA MYŚLIWY", "SPOKOJNIE, ON JEST W LESIE", "zartobliwy"},
      {"LEŚNA DUSZA", "Z TRADYCJĄ I SZACUNKIEM DO ZWIERZĄT", "czuly"},
      {"NAJLEPSZY TROP", "PROWADZI DO DOMU", "czuly"},
      {"GWARA MYŚLIWSKA", "STĄD WIADOMO, ŻE JESTEŚMY STĄD", "ironiczny"},
  };
  return v;
}
const std::vector<Pair>& workshopPairs() {
  static const std::vector<Pair> v = {
      {"WARSZTAT", "TU SIĘ NIC NIE PSuje, TU SIĘ NAPRAWIA", "zartobliwy"},
      {"MAJSTER", "ZGADNIE, ZANIM ZOBACZY", "zartobliwy"},
      {"NARZĘDZIA", "POŻYCZANE WRACAJĄ ZAWSZE — CZASEM PÓŹNIEJ", "ironiczny"},
      {"GARAŻ", "NAJWAŻNIEJSZE POMIESZCZENIE W DOMU", "suchy"},
      {"ZROBIĘ TO SAM", "I ZAJMIE MI TO TYLKO TRZY WEEKENDY", "ironiczny"},
      {"TU RZĄDZI ŚRUBOKRĘT", "KLUCZ FRANCUSKI JEST TYLKO DORADCĄ", "absurdalny"},
      {"MECHANIK", "WIE, JAK BRZMI DOBRY SILNIK", "poważny"},
      {"WARSZTATOWE ZASADY", "1. NIE MA ZASAD. 2. ZOBACZ PUNKT 1", "suchy"},
  };
  return v;
}
const std::vector<Pair>& autoPairs() {
  static const std::vector<Pair> v = {
      {"MIŁOŚNIK MOTORYZACJI", "CZWÓREK KÓŁ I DŁUGICH TRAS", "zartobliwy"},
      {"GARAŻOWY MECHANIK", "DIAGNOZA ZAWODOWA, CENA PRZYJACIELSKA", "zartobliwy"},
      {"MOJE AUTO", "WIĘCEJ NIŻ ŚRODEK TRANSPORTU", "czuly"},
      {"PARKUJĘ TU", "NAJLEPIEJ TAM, GDZIE NIE MOŻNA", "ironiczny"},
      {"SILNIK PRACUJE", "KIEROWCA TEŻ, ALE TYLKO NA KAWIE", "suchy"},
  };
  return v;
}
const std::vector<Pair>& homePairs() {
  static const std::vector<Pair> v = {
      {"NASZ DOM", "TU ZAWSZE JEST MIEJSCE DLA GOŚCI", "czuly"},
      {"DOM", "GDZIE SERCE, TAM DOM", "czuly"},
      {"RODZINA", "RAZEM NA DOBRE I NA ZŁE", "czuly"},
      {"TU MIESZKA SZCZĘŚCIE", "I STERTA NIEPOSKŁADANYCH SKARPETEK", "zartobliwy"},
      {"KUCHNIA", "SERCE DOMU I GŁÓWNE ŹRÓDŁO ZAPACHÓW", "zartobliwy"},
      {"WITAJ W DOMU", "ZDEJMUJ BUTY I ZOSTAŃ NA OBIAD", "czuly"},
  };
  return v;
}
const std::vector<Pair>& humorPairs() {
  static const std::vector<Pair> v = {
      {"JESTEM NA DIECIE", "OD PONIEDZIAŁKU, KTÓRY PRZYJDZIE KIEDYŚ", "zartobliwy"},
      {"NIE JESTEM ZMĘCZONY", "JESTEM W TRYBIE OSZCZĘDZANIA ENERGII", "absurdalny"},
      {"WSZYSTKO GRA", "PRZYNAJMNIEJ DO PIERWSZEJ AWARII", "suchy"},
      {"MAM PLAN", "I MAM TEŻ DRUGI PLAN, GDY PIERWSZY NIE WYJDZIE", "absurdalny"},
      {"SPOKOJNIE", "JA TEŻ NIE WIEM, CO ROBIĘ", "suchy"},
      {"NIE KRZYCZ", "JA SIĘ URODZIŁEM ZMĘCZONY", "ironiczny"},
      {"ZROBIĘ TO JUTRO", "ZAUFAJ MI, MÓWIĘ TAK OD ROKU", "ironiczny"},
      {"DOBRY CZŁOWIEK", "ALE Z TYMI PORANKAMI TO PRZESADA", "suchy"},
      {"ŻYCIE JEST PIĘKNE", "A JA JESTEM DOWODEM NA WYJĄTEK", "ironiczny"},
      {"PERFEKCJONISTA", "POPRAWIA COŚ, CO JUŻ DZIAŁA", "suchy"},
  };
  return v;
}
const std::vector<Pair>& satirePairs() {
  static const std::vector<Pair> v = {
      {"TU BYŁ POLITYK", "OBIECAŁ, ŻE WRÓCI PRZED WYBORAMI", "ironiczny"},
      {"DEBATA", "KAŻDY MÓWI, NIKT NIE SŁUCHA", "ironiczny"},
      {"NOWA USTAWA", "STARA ZMIANA, NOWY TYTUŁ", "ironiczny"},
      {"GŁOS LUDU", "WYRAŹNIE SŁYSZALNY W PIĄTEK PO POŁUDNIU", "ironiczny"},
      {"KOALICJA", "ZGODNI W NIEPOROZUMIENIU", "ironiczny"},
      {"KAMPANIA", "PLAKATY OBIECUJĄ, RZECZYWISTOŚĆ WERYFIKUJE", "ironiczny"},
  };
  return v;
}
const std::vector<Pair>& christmasPairs() {
  static const std::vector<Pair> v = {
      {"WESOŁYCH ŚWIĄT", "SPOKOJU, CIEPŁA I DUŻO PIERNICZKÓW", "czuly"},
      {"BOŻE NARODZENIE", "CZAS, KTÓRY ZATRZYMUJE SIĘ NA CHWILĘ", "czuly"},
      {"ŚWIĘTA", "BIAŁY OBRUS, CZERWONY BARSZCZ, ZŁOTE WSPOMNIENIA", "czuly"},
      {"DLA CIEBIE", "Z CAŁEGO SERCA I Z CAŁEJ CHOINKI", "czuly"},
  };
  return v;
}
const std::vector<Pair>& easterPairs() {
  static const std::vector<Pair> v = {
      {"WESOŁEGO ALLELUJA", "ZDROWIA, WIOSNY I MOKREGO PONIEDZIAŁKU", "czuly"},
      {"WIELKANOC", "NADZIEJA, KTÓRA WRACA KAŻDEGO ROKU", "czuly"},
      {"JAJKO WZORZYSTE", "DOWÓD, ŻE MAMY ZDOLNOŚCI ARTYSTYCZNE", "zartobliwy"},
  };
  return v;
}
const std::vector<Pair>& lovePairs() {
  static const std::vector<Pair> v = {
      {"KOCHAM CIĘ", "DZIŚ, JUTRO I ZAWSZE", "czuly"},
      {"TY I JA", "NAJLEPSZY SKŁAD OD LAT", "czuly"},
      {"DLA CIEBIE", "BO NIC NIE MUSISZ ROBIĆ, ŻEBY BYĆ WAŻNY", "czuly"},
      {"RAZEM", "LEPIEJ NIŻ OSOBNO, NAWET W KOLEJCE", "czuly"},
  };
  return v;
}
const std::vector<Pair>& patrioticPairs() {
  static const std::vector<Pair> v = {
      {"PAMIĘTAM", "O TYCH, KTÓRZY WALCZYLI O WOLNOŚĆ", "poważny"},
      {"BÓG, HONOR, OJCZYZNA", "WART JESTEM, BY O TYM PAMIĘTAĆ", "poważny"},
      {"TU JESTEM", "TU MIESZKAM, TU WRACAM", "poważny"},
      {"WOLNOŚĆ", "NIE JEST DANA RAZ NA ZAWSZE", "poważny"},
  };
  return v;
}
const std::vector<Pair>& slavicPairs() {
  static const std::vector<Pair> v = {
      {"SŁOWIAŃSKA DUSZA", "ŻYJE ZGODNIE Z NATURĄ", "poważny"},
      {"STARE ZNAKI", "NOWE ZNACZENIA", "poważny"},
      {"TU MIESZKA TRADYCJA", "PRZEKAZYWANA Z POKOLENIA NA POKOLENIE", "poważny"},
  };
  return v;
}
const std::vector<Pair>& fantasyPairs() {
  static const std::vector<Pair> v = {
      {"NIE WCHODŹ", "SMOK MA PRZESZKODY, ALE NIE MA HUMORU", "zartobliwy"},
      {"TU MIESZKA SMOK", "OSTRZEGAM, WOLI CZYTAĆ", "absurdalny"},
      {"GDZIE JEST PRZYGODA", "TAM ZAWSZE ZA PÓŹNO WSTAŁEM", "ironiczny"},
  };
  return v;
}
const std::vector<Pair>& gothicPairs() {
  static const std::vector<Pair> v = {
      {"CARPE NOCTEM", "NOC JEST NASZA", "poważny"},
      {"MEMENTO MORI", "PAMIĘTAJ, ŻE ŻYJESZ", "poważny"},
      {"CISZA I CIEMNOŚĆ", "NAJLEPSI TOWARZYSZE MYŚLI", "poważny"},
  };
  return v;
}
const std::vector<Pair>& retirementPairs() {
  static const std::vector<Pair> v = {
      {"EMERYT", "WOLNY CZŁOWIEK Z PEŁNYM KALENDARZEM", "zartobliwy"},
      {"NA EMERYTURZE", "PONIEDZIAŁEK TEŻ JEST WEEKENDEM", "zartobliwy"},
      {"ZASŁUŻONY ODPOCZYNEK", "ZACZYNA SIĘ W PONIEDZIAŁEK O 6:00", "ironiczny"},
      {"TERAZ MOJE CZASY", "I MÓJ PLAN DNIA", "czuly"},
  };
  return v;
}
const std::vector<Pair>& birthdayPairs() {
  static const std::vector<Pair> v = {
      {"STO LAT", "I JESZCZE WIĘCEJ POWODÓW DO RADOŚCI", "czuly"},
      {"WESOŁYCH URODZIN", "ŚMIECHU, ZDROWIA I DOBRYCH LUDZI WOKÓŁ", "czuly"},
      {"KOLEJNY ROK", "A TY CIĄGLE MŁODY DUCHOM", "zartobliwy"},
      {"URODZINY", "OFICJALNIE DZIEŃ, W KTÓRYM MASZ RACJĘ", "zartobliwy"},
  };
  return v;
}
const std::vector<Pair>& nameDayPairs() {
  static const std::vector<Pair> v = {
      {"W DNIU IMIENIN", "ŻYCZĘ SAMYCH DOBRYCH WIADOMOŚCI", "czuly"},
      {"IMIENINY", "DZIEŃ, W KTÓRYM WSZYSCY PAMIĘTAJĄ O TOBIE", "zartobliwy"},
  };
  return v;
}
const std::vector<Pair>& weddingPairs() {
  static const std::vector<Pair> v = {
      {"NA ZAWSZE RAZEM", "PRZEZ DOBRE I LEPSZE CHWILE", "czuly"},
      {"ŚLUB", "POCZĄTEK CZEGOŚ WSPANIAŁEGO", "czuly"},
      {"DWOJE LUDZI", "JEDNA HISTORIA", "czuly"},
  };
  return v;
}
const std::vector<Pair>& motherPairs() {
  static const std::vector<Pair> v = {
      {"NAJLEPSZA MAMA", "WIE, GDY TRZEBA ZADZWONIĆ", "czuly"},
      {"DLA MAMY", "BO NIKT NIE KOCHA TAK JAK ONA", "czuly"},
      {"MAMO", "DZIĘKUJĘ ZA WSZYSTKO, NAWET ZA TE PORADY", "czuly"},
  };
  return v;
}
const std::vector<Pair>& fatherPairs() {
  static const std::vector<Pair> v = {
      {"NAJLEPSZY TATA", "NAUCZYŁ MNIE WIĘCEJ, NIŻ MYŚLAŁ", "czuly"},
      {"DLA TATY", "MISTRZA NAPRAWIANIA ŚWIATA", "zartobliwy"},
  };
  return v;
}
const std::vector<Pair>& grandparentPairs() {
  static const std::vector<Pair> v = {
      {"DLA BABCI", "NAJLEPSZEJ KUCHNI I NAJWIĘKSZEGO SERCA", "czuly"},
      {"DLA DZIADKA", "OPOWIEŚCI, KTÓRE SIĘ NIGDY NIE KOŃCZĄ", "czuly"},
      {"BABCIA", "PRAWDZIWA SZEFOWA RODZINY", "zartobliwy"},
  };
  return v;
}
const std::vector<Pair>& petPairs() {
  static const std::vector<Pair> v = {
      {"TU MIESZKA PIES", "WŁAŚCICIEL DOMU, A TY JESTEŚ GOŚCIEM", "zartobliwy"},
      {"KOT", "MÓWIĘ, KIEDY CHCĘ JEDZENIE, NIE KIEDY TY CHCESZ", "zartobliwy"},
      {"PRZYJACIEL", "Z OGONEM I NAJLEPSZYM SERCEM", "czuly"},
  };
  return v;
}
const std::vector<Pair>& sportPairs() {
  static const std::vector<Pair> v = {
      {"TRENING", "CIAŁO WYSIŁEK, GŁOWA SPOKÓJ", "poważny"},
      {"NIE MA TEGO ZŁEGO", "CO NA DOBRE NIE WYJDZIE NA BOISKU", "zartobliwy"},
      {"SPORT", "JEDYNA RZECZ, KTÓRA NIE OSZUKUJE", "poważny"},
  };
  return v;
}
const std::vector<Pair>& musicPairs() {
  static const std::vector<Pair> v = {
      {"MUZYKA", "NAJLEPSZY LEK BEZ RECEPTY", "czuly"},
      {"GITARA", "SZEŚĆ STRUN I NIESKOŃCZONE MOŻLIWOŚCI", "poważny"},
      {"GŁOŚNIEJ", "ZAWSZE GŁOŚNIEJ", "zartobliwy"},
  };
  return v;
}
const std::vector<Pair>& gamingPairs() {
  static const std::vector<Pair> v = {
      {"GRACZ", "JESZCZE JEDNA RUNDA I IDĘ SPAĆ", "zartobliwy"},
      {"GAMING", "GDZIE RZECZYWISTOŚĆ MA GORSZĄ GRAFIKĘ", "absurdalny"},
      {"POZIOM TRUDNOŚCI", "ŻYCIE — BEZ ZAPISU STANU", "ironiczny"},
  };
  return v;
}
const std::vector<Pair>& religionPairs() {
  static const std::vector<Pair> v = {
      {"Z WIARĄ", "PRZEZ KAŻDY DZIEŃ", "poważny"},
      {"BÓG JEST MIŁOŚCIĄ", "A MIŁOŚĆ JEST W DOMU", "poważny"},
      {"POKÓJ WAM", "I SPOKÓJ W RODZINIE", "poważny"},
  };
  return v;
}
const std::vector<Pair>& halloweenPairs() {
  static const std::vector<Pair> v = {
      {"STRASZNY DOM", "NAJSTRASZNIEJSZA JEST TU RACHUNKOWOŚĆ", "zartobliwy"},
      {"HALLOWEEN", "CUKIEREK ALBO PSIKUS, ALE GŁÓWNIE CUKIEREK", "zartobliwy"},
      {"TU STRASZY", "GŁÓWNIE NIEUMYTE NACZYNIA", "absurdalny"},
  };
  return v;
}
const std::vector<Pair>& minimalistPairs() {
  static const std::vector<Pair> v = {
      {"MNIEJ", "ZNACZY WIĘCEJ", "poważny"},
      {"PROSTO", "I SKUTECZNIE", "poważny"},
      {"CISZA", "JEST FORMĄ LUKSUSU", "suchy"},
      {"JEDNO SŁOWO", "WYSTARCZY", "poważny"},
  };
  return v;
}
const std::vector<Pair>& logoPairs() {
  static const std::vector<Pair> v = {
      {"JAKOŚĆ", "DOSTRZEGANA W SZCZEGÓŁACH", "poważny"},
      {"RĘKODZIEŁO", "Z PATYNĄ CZASU", "poważny"},
      {"TWÓJ ZNAK", "TWOJA HISTORIA", "poważny"},
  };
  return v;
}

const std::vector<Pair>& pairsFor(const std::string& category) {
  std::string c = lowerAscii(stripDiacritics(category));
  if (contains(c, "wedk") || contains(c, "fish")) return fishingPairs();
  if (contains(c, "mysliw") || contains(c, "hunt")) return huntingPairs();
  if (contains(c, "warsztat") || contains(c, "workshop")) return workshopPairs();
  if (contains(c, "garaz") || contains(c, "motoryz") || contains(c, "auto")) return autoPairs();
  if (contains(c, "dom") || contains(c, "rodzin") || contains(c, "home")) return homePairs();
  if (contains(c, "polityk") || contains(c, "satyr")) return satirePairs();
  if (contains(c, "boze") || contains(c, "christmas") || contains(c, "swieta")) return christmasPairs();
  if (contains(c, "wielkan") || contains(c, "easter")) return easterPairs();
  if (contains(c, "walent") || contains(c, "milosc") || contains(c, "slub")) return weddingPairs();
  if (contains(c, "patriot")) return patrioticPairs();
  if (contains(c, "slowian")) return slavicPairs();
  if (contains(c, "fantasy") || contains(c, "gothic") || contains(c, "gotyk")) return fantasyPairs();
  if (contains(c, "emeryt")) return retirementPairs();
  if (contains(c, "urodzin") || contains(c, "birthday")) return birthdayPairs();
  if (contains(c, "imienin")) return nameDayPairs();
  if (contains(c, "dzien matki") || contains(c, "mama")) return motherPairs();
  if (contains(c, "dzien ojca") || contains(c, "tata")) return fatherPairs();
  if (contains(c, "babci") || contains(c, "dziadk")) return grandparentPairs();
  if (contains(c, "zwierz") || contains(c, "pies") || contains(c, "kot")) return petPairs();
  if (contains(c, "sport")) return sportPairs();
  if (contains(c, "muzyk")) return musicPairs();
  if (contains(c, "gaming") || c == "gra" || c == "gry") return gamingPairs();
  if (contains(c, "relig")) return religionPairs();
  if (contains(c, "halloween")) return halloweenPairs();
  if (contains(c, "minimal")) return minimalistPairs();
  if (contains(c, "logo") || contains(c, "emblemat")) return logoPairs();
  if (contains(c, "dla mamy")) return motherPairs();
  if (contains(c, "dla taty")) return fatherPairs();
  return humorPairs();
}

const char* kIdeas[] = {
    "Zrób śmieszną tabliczkę dla wędkarza: szczupak w okularach, dwie wędki, napis KRÓL ŁOWISKA / RYBY SIĘ MNIE BOJĄ, A ŻONA MNIE SZUKA, styl retro, 200×150 mm.",
    "Zaprojektuj brelok dla mechanika: klucz i koło zębate, napis MISTRZ KLUCZA, minimalistyczny, 50×30 mm, sklejka 3 mm.",
    "Tabliczka do warsztatu: imadło, śrubokręt, napis TU SIĘ NIE PSuje, TU SIĘ NAPRAWIA, styl vintage, 250×180 mm.",
    "Dla taty na Dzień Ojca: wędka i ryba, napis NAJLEPSZY TATA, WĘDKARSKI MISTRZ, ramka ozdobna, 180×120 mm.",
    "Halloween: dynia, nietoperze, napis STRASZNY DOM, TU STRASZY GŁÓWNIE RACHUNKOWOŚĆ, 200×200 mm, sklejka.",
    "Boże Narodzenie: choinka i śnieżynki, napis WESOŁYCH ŚWIĄT, SPOKOJU I CIEPŁA, ramka z plecionki, 180×140 mm.",
    "Dla gracza: pad do gier, kostki, napis JESZCZE JEDNA RUNDA, styl komiksowy, 200×150 mm.",
    "Naszywka skórzana dla myśliwego: jeleń z porożem, napis LEŚNA DUSZA, 80×40 mm, skóra naturalna.",
    "Zawieszka do wina: kieliszek i kiść winogron, napis WYTRWAŁOŚĆ, 60×80 mm.",
    "Medal dla dziadka: koło zębate i orzeł, napis ZASŁUŻONY ODPOCZYNEK, 60×60 mm, akryl.",
    "Zakładka do książki: pióro i ornament, napis CZYTAM, WIĘC JESTEM, 50×150 mm.",
    "Magnes na lodówkę: kot i ryba, napis SZEF KUCHNI, 60×45 mm, styl komiksowy.",
    "Tabliczka do garażu: samochód, klucz, napis GARAŻ, NAJWAŻNIEJSZE POMIESZCZENIE W DOMU, 250×150 mm.",
    "Szyld dla rolnika: traktor i pole, napis GOSPODARZ ROKU, 300×150 mm, sklejka 5 mm.",
    "Dla mamy: kwiaty i serce, napis NAJLEPSZA MAMA, Z CAŁEGO SERCA, ramka floralna, 200×150 mm.",
    "Piersiówka z grawerem: wilk, napis WILKI NIE ŚPIĄ, BEZ CIĘCIA, 95×115 mm.",
    "Tabliczka patriotyczna: orzeł i flaga, napis PAMIĘTAM, 200×150 mm.",
    "Emblemat fantasy: smok, napis TUTAJ SMOK, NIE WCHODŹ, 200×200 mm, gotyk.",
    "Sport: hantle i bieżnia, napis TRENING, CIAŁO WYSIŁEK GŁOWA SPOKÓJ, 200×120 mm.",
    "Muzyka: gitara, nuty, napis MUZYKA, NAJLEPSZY LEK BEZ RECEPTY, 180×220 mm.",
};
std::vector<std::string> ideaListVec() {
  std::vector<std::string> v;
  for (auto& i : kIdeas) v.push_back(i);
  return v;
}

std::mt19937& rngFor(uint32_t seed) {
  static std::mt19937 rng(seed ? seed : 1234567u);
  return rng;
}
std::string pickRecipient(const TextSpec& spec) {
  if (!spec.recipient.empty()) return spec.recipient;
  static const char* list[] = {"taty", "mamy", "dziadka", "babci", "szefa", "wędkarza", "mechanika", "kolegi", "przyjaciela"};
  return list[rngFor(0)() % 9];
}

}  // namespace

std::vector<TextSuggestion> generateTexts(const TextSpec& spec, int count, uint32_t seed) {
  std::vector<TextSuggestion> out;
  auto& rng = rngFor(seed ? seed : 987654321u);
  const auto& base = pairsFor(spec.category.empty() ? spec.topic : spec.category);
  // 1) jeśli użytkownik podał konkretny tekst — użyj go
  if (!spec.exactText.empty()) {
    auto lines = split(spec.exactText, '\n');
    TextSuggestion s;
    s.top = trim(lines[0]);
    s.bottom = lines.size() > 1 ? trim(lines[1]) : "";
    s.dedication = spec.dedication;
    s.tone = spec.tone;
    s.source = "użytkownik";
    out.push_back(s);
  }
  // 2) warianty z bazy
  std::vector<size_t> order(base.size());
  for (size_t i = 0; i < order.size(); i++) order[i] = i;
  for (size_t i = order.size(); i > 1; i--) std::swap(order[i - 1], order[rng() % i]);
  for (size_t idx : order) {
    if ((int)out.size() >= count) break;
    const Pair& p = base[idx];
    TextSuggestion s;
    s.top = p.top;
    s.bottom = p.bottom;
    s.tone = p.tone;
    s.source = "szablon";
    if (spec.recipient.find("mam") != std::string::npos || spec.category == "dzien_matki") {
      s.bottom = std::string(p.bottom);
    }
    if (!spec.dedication.empty()) s.dedication = spec.dedication;
    out.push_back(s);
  }
  // 3) kombinacja z odbiorcą (personalizacja)
  if ((int)out.size() < count) {
    TextSuggestion s;
    std::string r = pickRecipient(spec);
    s.top = "DLA " + upperAscii(stripDiacritics(r));
    s.bottom = "Z NAJLEPSZYMI ŻYCZENIAMI";
    s.tone = "czuly";
    s.source = "personalizacja";
    out.push_back(s);
  }
  // 4) dostosowanie długości i tonu
  for (auto& s : out) {
    if (spec.length == "krotki") {
      if (s.top.size() > 22) s.top = shortenText(s.top, 22);
      if (s.bottom.size() > 40) s.bottom = shortenText(s.bottom, 40);
    } else if (spec.length == "dlugi") {
      if (!s.dedication.empty()) s.dedication = s.dedication + " • " + dedicationFor(pickRecipient(spec), spec.occasion);
    }
    if (spec.tone == "poważny") s.tone = "poważny";
    if (spec.profanity == "brak") {
      s.top = replaceAll(s.top, "cholera", "kurcze");
      s.bottom = replaceAll(s.bottom, "cholera", "kurcze");
    }
  }
  while ((int)out.size() < count && !out.empty()) out.push_back(out[out.size() % out.size()]);
  if ((int)out.size() > count) out.resize(count);
  return out;
}

std::string adjustText(const std::string& text, const std::string& action) {
  std::string a = lowerAscii(stripDiacritics(action));
  std::string t = text;
  if (contains(a, "smieszn") || contains(a, "smieszniej") || contains(a, "zabawni")) {
    static const char* tails[] = {" (podobno)", ", ale tylko w teorii", " — tak twierdzi sąsiad",
                                  ", według najnowszych badań", " i to mnie najbardziej martwi"};
    static int i = 0;
    t += tails[i++ % 5];
    return t;
  }
  if (contains(a, "skroc") || contains(a, "krotk")) return shortenText(t, 24);
  if (contains(a, "eleganc") || contains(a, "poważn") || contains(a, "powazn")) {
    return "Z SZACUNKIEM I UZNANIEM — " + t;
  }
  if (contains(a, "dosadn") || contains(a, "mocni")) {
    return t + " BEZ OWIJANIA W BAWEŁNĘ";
  }
  if (contains(a, "rym")) return makeRhyme(t);
  if (contains(a, "dedykacj")) return t + " • " + dedicationFor("obdarowanego", "");
  if (contains(a, "puent")) {
    if (t.find("—") != std::string::npos) return t;
    return t + " — I TO BY BYŁO NA TYLE";
  }
  if (contains(a, "pisowni") || contains(a, "ortograf")) return fixPolishSpelling(t);
  return t;
}

std::string fixPolishSpelling(const std::string& text) {
  // poprawa typowych błędów (bez zamiany poprawnych form)
  std::string t = text;
  struct Fix { const char* wrong; const char* right; };
  static const Fix fixes[] = {
      {"wziąść", "wziąć"}, {"włanczać", "włączać"}, {"włączać się", "włączać się"},
      {"poszłem", "poszedłem"}, {"poszłam", "poszłam"}, {"w ogóle", "w ogóle"},
      {"napewno", "na pewno"}, {"napweno", "na pewno"}, {"wogole", "w ogóle"},
      {"wogóle", "w ogóle"}, {"poniewasz", "ponieważ"}, {"dlaczgo", "dlaczego"},
      {"jutrz", "jutro"}, {"terazniejszy", "teraźniejszy"}, {"włącza", "włącza"},
      {"żeczywistość", "rzeczywistość"}, {"żeka", "rzeka"}, {"morze być", "może być"},
      {"możeż", "możesz"}, {"kurde", "kurczę"}, {"psiakrew", "psiakość"},
      {"w każdym bądz razie", "w każdym razie"}, {"bądz", "bądź"}, {"rąz", "raz"},
      {"hazard", "hazard"}, {"wziąść udział", "wziąć udział"}, {"najleprzy", "najlepszy"},
      {"szcześliwy", "szczęśliwy"}, {"szczęsliwy", "szczęśliwy"}, {"wesołych świąt", "Wesołych Świąt"},
  };
  for (auto& f : fixes) t = replaceAll(t, f.wrong, f.right);
  // usunięcie podwójnych spacji
  while (t.find("  ") != std::string::npos) t = replaceAll(t, "  ", " ");
  return trim(t);
}

std::string makeRhyme(const std::string& line) {
  // dobierz zakończenie rymujące się z ostatnim słowem
  auto words = split(line, ' ');
  std::string last = words.empty() ? "" : lowerAscii(stripDiacritics(words.back()));
  while (!last.empty() && !isalpha((unsigned char)last.back())) last.pop_back();
  if (last.size() >= 3) {
    std::string tail = last.substr(last.size() - 3);
    if (tail == "raz") return line + "\nI TO NIE PIERWSZY RAZ";
    if (tail == "dom") return line + "\nGDZIE SERCE, TAM DOM";
    if (tail == "ryb") return line + "\nA RYBA BYŁA TUŻ TUŻ";
    if (tail == "sen") return line + "\nI TAK MIAŁ BYĆ TEN SEN";
    if (tail == "los") return line + "\nTAKI JEST NASZ LOS";
  }
  return line + "\nI TYLE W TEMACIE";
}

std::string shortenText(const std::string& text, size_t maxChars) {
  if (text.size() <= maxChars) return text;
  auto words = split(text, ' ');
  std::string out;
  for (auto& w : words) {
    if (out.size() + w.size() + 1 > maxChars) break;
    if (!out.empty()) out += " ";
    out += w;
  }
  return out.empty() ? text.substr(0, maxChars) : out;
}

std::string dedicationFor(const std::string& recipient, const std::string& occasion) {
  std::string r = recipient.empty() ? "dla Ciebie" : "dla " + recipient;
  if (contains(lowerAscii(stripDiacritics(occasion)), "urodzin")) return r + " z okazji urodzin";
  if (contains(lowerAscii(stripDiacritics(occasion)), "slub")) return r + " na nowej drodze życia";
  if (contains(lowerAscii(stripDiacritics(occasion)), "swieta")) return r + " z okazji świąt";
  return r + ", z najlepszymi życzeniami";
}

std::string randomIdea(uint32_t seed) {
  auto v = ideaListVec();
  auto& rng = rngFor(seed ? seed : (uint32_t)std::random_device{}());
  return v[rng() % v.size()];
}
std::vector<std::string> ideaList() { return ideaListVec(); }

std::vector<SpellIssue> checkSpelling(const std::string& text) {
  std::vector<SpellIssue> issues;
  static const char* suspects[][2] = {
      {"wziąść", "wziąć"}, {"włanczać", "włączać"}, {"poszłem", "poszedłem"},
      {"napewno", "na pewno"}, {"wogole", "w ogóle"}, {"poniewasz", "ponieważ"},
      {"morze", "może"}, {"bądz", "bądź"}, {"najleprzy", "najlepszy"},
      {"szczęsliwy", "szczęśliwy"}, {"dlaczgo", "dlaczego"}, {"jutrz", "jutro"},
  };
  std::string low = lowerPl(text);
  for (auto& s : suspects) {
    if (contains(low, utf8From(0)) ) {}
    if (contains(low, s[0])) {
      SpellIssue i;
      i.word = s[0];
      i.suggestion = s[1];
      i.reason = "typowy błąd językowy w języku polskim";
      issues.push_back(i);
    }
  }
  // brak polskich znaków w długim tekście
  bool hasAsciiOnly = true;
  for (auto cp : utf8Decode(text)) if (cp > 127) hasAsciiOnly = false;
  if (hasAsciiOnly && text.size() > 12) {
    SpellIssue i;
    i.word = "brak znaków diakrytycznych";
    i.suggestion = "dodaj Ą, Ć, Ę, Ł, Ń, Ó, Ś, Ź, Ż tam, gdzie są wymagane";
    i.reason = "tekst nie zawiera polskich znaków — możliwy błąd zapisu";
    issues.push_back(i);
  }
  return issues;
}

}  // namespace lc
