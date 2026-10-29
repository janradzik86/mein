// Wspólne API silnika (JSON) używane przez CLI, serwer HTTP, GUI Windows i Android (JNI)
#pragma once
#include "compose.h"
#include "export.h"
#include "validate.h"

namespace lc {

class Engine {
 public:
  Engine();
  void setDataDir(const std::string& dir);   // katalog danych (projekty, cache)
  void setFontDir(const std::string& dir);
  const std::string& dataDir() const { return dataDir_; }
  // Główne wejście: akcja + parametry JSON → wynik JSON
  Json handle(const std::string& action, const Json& req);
  Json status();
  FontRegistry& fonts() { return fonts_; }
  AssetStore& assets() { return assets_; }

  // akcje pomocnicze (używane też przez testy)
  Json catalog();
  Json generate(const Json& req);
  Json edit(const Json& req);
  Json validateJson(const Json& req);
  Design designFromRequest(const Json& req, bool* ok = nullptr);
  Json renderJson(const Json& req);
  Json calculateCosts(const Json& req);
  // Wewnętrzny rozgałęziacz akcji — publiczne handle() normalizuje pole "ok".
  Json dispatch(const std::string& action, const Json& req);

 private:
  std::string dataDir_ = ".";
  FontRegistry fonts_;
  AssetStore assets_;
  Design current_;
};

// Kalkulator kosztów (LaserCraft)
struct CostInput {
  double materialCostPerM2 = 45;
  double areaMm2 = 200 * 150;
  double engraveAreaMm2 = 0;
  double cutLengthMm = 0;
  double machinePowerW = 20;
  double energyPriceKwh = 0.85;
  double hourlyRate = 40;
  double engraveSpeedMmS = 60;
  double cutSpeedMmS = 8;
  double setupMinutes = 3;
  int passes = 1;
  double packaging = 4.5;
  double shipping = 15;
  double marginPercent = 40;
  double wastePercent = 10;
};
struct CostResult {
  double materialCost = 0, engraveMinutes = 0, cutMinutes = 0, machineMinutes = 0;
  double energyCost = 0, laborCost = 0, wearCost = 0, totalCost = 0;
  double price = 0, profit = 0, vatPrice = 0;
  bool estimateOnly = true;
  std::string note;
};
CostResult calculateCost(const CostInput& in);

struct TrendEntry {
  std::string title, category, idea;
  std::string season;   // "cały rok" | "Q4" | "wiosna" ...
  std::string source;   // źródło (offline: biblioteka własna)
  std::string date;
  std::vector<std::string> tags;
};
const std::vector<TrendEntry>& offlineTrends();

}  // namespace lc
