// Biblioteka proceduralnych emblematów i ornamentów (wektor, monochrom)
#pragma once
#include "path.h"
#include <map>

namespace lc {

struct EmblemPart {
  Path path;
  bool filled = true;      // wypełnienie (sylwetka)
  bool stroke = true;      // kontur
  double hatchSpacing = 0;  // kreskowanie cieni (0 = brak)
  double hatchAngle = 45;
};

struct Emblem {
  std::string id;
  std::vector<EmblemPart> parts;
  Rect bounds;             // w mm dla skali 100x100
};

// Emblemat w skali 100×100 mm (środek 50,50). Zwraca nullptr dla nieznanego id.
const Emblem* emblem(const std::string& id);
std::vector<std::string> emblemIds();
std::string emblemBestMatch(const std::string& subjectPl);   // dopasowanie po słowie kluczowym

// Ramki / bordiury (skala: dopasowanie do podanego prostokąta)
Path framePath(const std::string& style, const Rect& area, double thickness);
std::vector<std::string> frameIds();
std::string frameBestMatch(const std::string& style);

// Elementy ozdobne
Path bannerRibbon(const Rect& r, double foldSize);
Path sunRays(Vec2 c, double r0, double r1, int count);
Path laurel(Vec2 c, double r, bool leftSide);
Path cornerFlourish(Vec2 corner, double size, int rotationQuarter);
Path dividerLine(Vec2 a, Vec2 b, double amplitude, double step);
Path starsRow(Vec2 center, double width, int n, double size);
Path moonsAndStars(const Rect& r);
Path snowflake(Vec2 c, double r, int arms);
Path crossOrnament(const Rect& r);
Path arrowBanner(const Rect& r);
Path shieldEmblem(const Rect& r);
Path gearShape(Vec2 c, double r, int teeth);
Path anchorShape(Vec2 c, double h);
Path fishShape(Vec2 c, double w, double h, bool pike);
Path wolfShape(Vec2 c, double w, double h);
Path deerShape(Vec2 c, double w, double h);
Path tractorShape(Vec2 c, double w, double h);
Path carShape(Vec2 c, double w, double h);
Path guitarShape(Vec2 c, double w, double h);
Path gamepadShape(Vec2 c, double w, double h);
Path mugShape(Vec2 c, double w, double h);
Path skullShape(Vec2 c, double w, double h);
Path roseShape(Vec2 c, double w, double h);
Path treeShape(Vec2 c, double w, double h);
Path mountainsShape(Vec2 c, double w, double h);
Path crownShape(Vec2 c, double w, double h);
Path eagleShape(Vec2 c, double w, double h);
Path pumpkinShape(Vec2 c, double w, double h);
Path heartShape(Vec2 c, double w, double h);
Path pawShape(Vec2 c, double w, double h);
Path birdShape(Vec2 c, double w, double h);
Path horseShape(Vec2 c, double w, double h);
Path dragonShape(Vec2 c, double w, double h);

}  // namespace lc
