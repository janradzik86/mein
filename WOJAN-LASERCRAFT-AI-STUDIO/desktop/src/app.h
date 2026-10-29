// Stan aplikacji Windows (ImGui/DX11) — wspólny dla wszystkich paneli UI
#pragma once
#include <d3d11.h>
#include <string>
#include <vector>
#include "imgui.h"
#include "core_api.h"
#include "library.h"

struct GpuTex {
  ID3D11ShaderResourceView* srv = nullptr;
  int w = 0, h = 0;
  void release();
  bool valid() const { return srv != nullptr; }
};

struct Toast {
  std::string text;
  bool error = false;
  double until = 0;
};

struct AppState {
  // --- silnik ---
  lc::Engine engine;
  ID3D11Device* device = nullptr;
  std::string exeDir, dataDir, fontDir;

  // --- czcionki interfejsu (polskie znaki) ---
  float uiScale = 1.0f;
  ImFont* fontHead = nullptr;
  ImFont* fontSmall = nullptr;

  // --- projekt ---
  lc::Design design;
  bool hasDesign = false;
  std::string promptText = "Tabliczka z grawerem: szczupak, napis KRÓL ŁOWISKA, na dole RYBY SIĘ MNIE BOJĄ, 200×150 mm";
  std::string projectPath;
  bool dirty = false;

  // --- formularz generatora ---
  int categoryIdx = 0, productIdx = 0, shapeIdx = 0, materialIdx = 0, styleIdx = 0, detailIdx = 1,
      engraveIdx = 0, machineIdx = 0;
  float widthMm = 200, heightMm = 150;
  int cutMode = 0;  // 0=auto, 1=tnie, 2=tylko grawer

  // --- podgląd ---
  GpuTex preview;
  bool previewDirty = true;
  float previewPxPerMm = 8.0f;
  float zoom = 1.0f;
  ImVec2 pan{0, 0};
  bool showGuides = true, showRulers = true, proMode = true, gridSnap = true;
  float gridMm = 10.0f;
  bool objectMode = true;  // true = zaznaczanie obiektów, false = tryb węzłów

  // --- warianty / teksty / historia ---
  std::vector<lc::Json> variants;
  std::vector<lc::Json> texts;
  std::vector<std::string> log;
  std::vector<std::string> chat;
  std::string editCommand;
  std::string transcript;
  char textTopBuf[256] = {0};
  char textBottomBuf[256] = {0};

  // --- edytor ---
  int selectedObj = -1;
  int selectedNode = -1;

  // --- moduły ---
  lc::Json projectList;
  int selectedProject = -1;
  std::string qrData = "https://wojan.pl";
  int qrEccIdx = 1;
  float qrSizeMm = 40;
  std::string qrCheck;
  GpuTex qrPreview;
  bool qrPreviewDirty = true;

  float calcMaterialCost = 45, calcArea = 300, calcEngrave = 180, calcCut = 60, calcPower = 20,
        calcEnergy = 0.85f, calcRate = 40, calcEngraveSpeed = 60, calcCutSpeed = 8, calcSetup = 3,
        calcPackaging = 4.5f, calcShipping = 15, calcMargin = 40, calcWaste = 10;
  lc::Json calcResult;

  lc::Json engineStatus;
  std::string diagText;

  bool settingAutoPreview = true;
  std::string exportDir;
  std::string statusLine = "Gotowe";

  bool micRecording = false;
  bool micAvailable = false;
  std::string micInfo;
  std::vector<Toast> toasts;

  // --- operacje ---
  void toast(const std::string& msg, bool error = false);
  bool setDesign(const lc::Json& d, const std::string& why);
  void updatePreview();
  void generate(int variantCount);
  void generateVariants4();
  void applyEdit(const std::string& cmd);
  void openProject(const std::string& path);
  void saveProject(const std::string& path);
  void refreshProjects();
  void refreshQr();
  void newProject();
};

// --- ui.cpp ---
void uiDrawAll(AppState& app, int& activeTab, bool& wantExit);
void uiDrawGenerator(AppState& app);
void uiDrawMic(AppState& app);
void uiDrawImport(AppState& app);
void uiDrawCategories(AppState& app);
void uiDrawEditor(AppState& app);
void uiDrawTexts(AppState& app);
void uiDrawQr(AppState& app);
void uiDrawProjects(AppState& app);
void uiDrawCalculator(AppState& app);
void uiDrawExport(AppState& app);
void uiDrawDiagnostics(AppState& app);
void uiDrawSettings(AppState& app);

// --- platforma (main.cpp) ---
std::string openFileDialog(const wchar_t* filter, const char* title);
std::string saveFileDialog(const wchar_t* filter, const char* title, const char* defaultName);
std::string pickFolderDialog();
GpuTex makeTexture(ID3D11Device* dev, const std::vector<uint8_t>& png);
bool startMicRecording(std::string& err);
bool stopMicRecording(const std::string& wavPath, std::string& err);
