// Eksport produkcyjny: SVG (LightBurn), DXF, G-code GRBL, paczka produkcyjna
#pragma once
#include "assets.h"
#include "projector.h"

namespace lc {

struct ExportResult {
  bool ok = false;
  std::string error;
  std::vector<std::string> warnings;
  std::string primaryFile;
};

struct GcodeOptions {
  bool includeEngraveVector = true;
  bool includeCut = true;
  bool includeRaster = false;
  double power = 60;         // % (0-100)
  double feed = 400;         // mm/min dla cięcia
  double engraveFeed = 1200;
  double zSafe = 5;
  int passes = 1;
  bool allowOutsideField = false;
};

// SVG — pełny projekt z warstwami (kolory techniczne, teksty jako ścieżki)
std::string designToSvg(const Design& d, FontRegistry& fonts, const AssetStore& assets,
                        bool includeGuides = false, bool embedRaster = true);
// SVG pojedynczej warstwy ("" = wszystkie widoczne)
std::string designToSvgLayer(const Design& d, FontRegistry& fonts, const AssetStore& assets,
                             const std::string& layer);
// DXF R2000 (LWPOLYLINE), jednostki mm
std::string designToDxf(const Design& d, FontRegistry& fonts, const AssetStore& assets,
                        const std::string& layer = "");
// G-code GRBL (tylko gdy profil maszyny jest jawnie wybrany)
ExportResult designToGcode(const Design& d, FontRegistry& fonts, const AssetStore& assets,
                           const GcodeOptions& opt, std::string& out);
// raport walidacji jako tekst
std::string exportReportText(const Design& d, FontRegistry& fonts, const AssetStore& assets);

// Paczka produkcyjna: katalog + ZIP
struct PackageResult {
  bool ok = false;
  std::string error;
  std::string dir;
  std::vector<std::string> files;
  std::vector<std::pair<std::string, std::string>> manifest;  // nazwa → sha256/rozmiar
};
PackageResult writeProductionPackage(const Design& d, FontRegistry& fonts, const AssetStore& assets,
                                     const std::string& outRoot,
                                     const std::string& previewPngBase64OrPath = "");

}  // namespace lc
