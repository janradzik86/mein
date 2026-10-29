// WOJAN LaserCraft AI Studio — interfejs (12 zakładek, motyw grafitowy, język polski)
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include "app.h"
#include "common.h"
#include "export.h"
#include "validate.h"

namespace {

const char* kTabs[] = {"Generator AI", "Mikrofon i prompt", "Import zdjęcia", "Kategorie", "Edytor",
                       "Napisy AI", "QR", "Moje projekty", "Kalkulator", "Eksport",
                       "Diagnostyka AI", "Ustawienia"};

GpuTex gImportPreview;
std::vector<uint8_t> gImportBytes;
lc::Json gImportImage;
int gImportSrcW = 0, gImportSrcH = 0;
float gBrightness = 0, gContrast = 0, gGamma = 1.0f;
int gThreshold = 128;
int gDitherIdx = 1;
bool gRemoveBg = false, gAutoContrast = false;
float gImportWidthMm = 60.f;
int gCropX = 0, gCropY = 0, gCropW = 0, gCropH = 0;
char gImportPathBuf[512] = {0};

char gIdeaText[512] = {0};
lc::Json gTextSuggestions;
int gTextToneIdx = 0, gTextCategoryIdx = 0, gTextCount = 6;
char gDedication[128] = {0};
char gExactText[256] = {0};
std::string gSpellingInfo;

char gSearch[128] = {0};
lc::Json gTrends;
char gValidateInfo[4096] = {0};
int gExportFormat = 0;
std::string gExportInfo;
lc::Json gPackageResult;
bool gShowSafety = false;

void tooltip(const char* text) {
  if (ImGui::IsItemHovered()) ImGui::SetTooltip("%s", text);
}

void sectionTitle(const char* text) {
  ImGui::Spacing();
  ImGui::PushStyleColor(ImGuiCol_Text, ImVec4(0.95f, 0.62f, 0.18f, 1.0f));
  ImGui::TextUnformatted(text);
  ImGui::PopStyleColor();
  ImGui::Separator();
}

bool bigButton(const char* label, ImVec2 size = ImVec2(0, 34), const char* tip = nullptr) {
  ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 6.0f);
  bool r = ImGui::Button(label, size);
  ImGui::PopStyleVar();
  if (tip) tooltip(tip);
  return r;
}

void drawPreviewPanel(AppState& app, float availH, const char* id) {
  ImGui::BeginChild(id, ImVec2(0, availH), true);
  if (!app.preview.valid()) {
    ImGui::TextDisabled("Brak podglądu — wygeneruj projekt (GENERUJ AI).");
    ImGui::EndChild();
    return;
  }
  ImVec2 avail = ImGui::GetContentRegionAvail();
  float ar = (float)app.preview.w / std::max(1, app.preview.h);
  float w = avail.x - 8, h = w / ar;
  if (h > avail.y - 8) { h = avail.y - 8; w = h * ar; }
  w *= app.zoom;
  h *= app.zoom;
  ImVec2 cur = ImGui::GetCursorScreenPos();
  ImGui::SetCursorPosX(cur.x + std::max(0.0f, (avail.x - w) / 2.0f + app.pan.x));
  ImGui::SetCursorPosY(cur.y + std::max(0.0f, (avail.y - h) / 2.0f + app.pan.y));
  ImGui::Image((ImTextureID)(intptr_t)app.preview.srv, ImVec2(w, h), ImVec2(0, 0), ImVec2(1, 1),
               ImVec4(1, 1, 1, 1), ImVec4(0.13f, 0.13f, 0.14f, 1.0f));
  if (ImGui::IsItemHovered()) {
    ImGui::BeginTooltip();
    ImGui::Text("%d × %d px  ·  %.1f × %.1f mm", app.preview.w, app.preview.h, app.design.product.w,
                app.design.product.h);
    ImGui::EndTooltip();
  }
  ImGui::EndChild();
}

void applyTheme() {
  ImGuiStyle& s = ImGui::GetStyle();
  s.WindowRounding = 6;
  s.ChildRounding = 6;
  s.FrameRounding = 5;
  s.GrabRounding = 5;
  s.PopupRounding = 6;
  s.ScrollbarRounding = 6;
  s.TabRounding = 6;
  s.WindowBorderSize = 1;
  s.FrameBorderSize = 0;
  s.ItemSpacing = ImVec2(8, 6);
  s.FramePadding = ImVec2(8, 5);
  ImVec4* c = s.Colors;
  c[ImGuiCol_WindowBg] = ImVec4(0.085f, 0.09f, 0.10f, 1.0f);
  c[ImGuiCol_ChildBg] = ImVec4(0.105f, 0.11f, 0.12f, 1.0f);
  c[ImGuiCol_PopupBg] = ImVec4(0.10f, 0.105f, 0.115f, 0.98f);
  c[ImGuiCol_Border] = ImVec4(0.24f, 0.25f, 0.27f, 1.0f);
  c[ImGuiCol_FrameBg] = ImVec4(0.16f, 0.17f, 0.19f, 1.0f);
  c[ImGuiCol_FrameBgHovered] = ImVec4(0.21f, 0.22f, 0.24f, 1.0f);
  c[ImGuiCol_FrameBgActive] = ImVec4(0.26f, 0.27f, 0.29f, 1.0f);
  c[ImGuiCol_TitleBg] = ImVec4(0.09f, 0.095f, 0.10f, 1.0f);
  c[ImGuiCol_TitleBgActive] = ImVec4(0.12f, 0.125f, 0.135f, 1.0f);
  c[ImGuiCol_MenuBarBg] = ImVec4(0.10f, 0.105f, 0.115f, 1.0f);
  c[ImGuiCol_Tab] = ImVec4(0.14f, 0.145f, 0.16f, 1.0f);
  c[ImGuiCol_TabHovered] = ImVec4(0.96f, 0.62f, 0.18f, 0.55f);
  c[ImGuiCol_TabSelected] = ImVec4(0.96f, 0.55f, 0.10f, 0.85f);
  c[ImGuiCol_Button] = ImVec4(0.20f, 0.21f, 0.23f, 1.0f);
  c[ImGuiCol_ButtonHovered] = ImVec4(0.96f, 0.62f, 0.18f, 0.85f);
  c[ImGuiCol_ButtonActive] = ImVec4(0.85f, 0.50f, 0.10f, 1.0f);
  c[ImGuiCol_Header] = ImVec4(0.22f, 0.23f, 0.25f, 1.0f);
  c[ImGuiCol_HeaderHovered] = ImVec4(0.96f, 0.62f, 0.18f, 0.55f);
  c[ImGuiCol_HeaderActive] = ImVec4(0.96f, 0.55f, 0.10f, 0.85f);
  c[ImGuiCol_CheckMark] = ImVec4(0.96f, 0.62f, 0.18f, 1.0f);
  c[ImGuiCol_SliderGrab] = ImVec4(0.96f, 0.62f, 0.18f, 1.0f);
  c[ImGuiCol_SliderGrabActive] = ImVec4(1.0f, 0.72f, 0.30f, 1.0f);
  c[ImGuiCol_Text] = ImVec4(0.90f, 0.91f, 0.92f, 1.0f);
  c[ImGuiCol_TextDisabled] = ImVec4(0.55f, 0.56f, 0.58f, 1.0f);
  c[ImGuiCol_Separator] = ImVec4(0.26f, 0.27f, 0.29f, 1.0f);
  c[ImGuiCol_TableHeaderBg] = ImVec4(0.14f, 0.15f, 0.16f, 1.0f);
  c[ImGuiCol_TableRowBg] = ImVec4(0, 0, 0, 0);
  c[ImGuiCol_TableRowBgAlt] = ImVec4(1, 1, 1, 0.03f);
}

}  // namespace

// ================================ menu + zakładki ================================
void uiDrawAll(AppState& app, int& activeTab, bool& wantExit) {
  static bool themed = false;
  if (!themed) { applyTheme(); themed = true; }

  if (app.previewDirty && app.hasDesign && app.device) app.updatePreview();
  if (app.qrPreviewDirty && app.device) app.refreshQr();

  if (ImGui::BeginMainMenuBar()) {
    if (ImGui::BeginMenu("Projekt")) {
      if (ImGui::MenuItem("Nowy", "Ctrl+N")) app.newProject();
      if (ImGui::MenuItem("Otwórz…", "Ctrl+O")) {
        std::string p = openFileDialog(L"Projekty (*.wlcproj;*.zip)\0*.wlcproj;*.zip\0Wszystkie pliki\0*.*\0",
                                       "Otwórz projekt");
        if (!p.empty()) app.openProject(p);
      }
      if (ImGui::MenuItem("Zapisz", "Ctrl+S")) {
        std::string p = app.projectPath;
        if (p.empty())
          p = saveFileDialog(L"Projekt WOJAN (*.wlcproj)\0*.wlcproj\0", "Zapisz projekt",
                             (app.hasDesign ? app.design.name.c_str() : "projekt"));
        if (!p.empty()) app.saveProject(p);
      }
      if (ImGui::MenuItem("Zapisz jako…")) {
        std::string p = saveFileDialog(L"Projekt WOJAN (*.wlcproj)\0*.wlcproj\0", "Zapisz projekt jako",
                                       (app.hasDesign ? app.design.name.c_str() : "projekt"));
        if (!p.empty()) app.saveProject(p);
      }
      ImGui::Separator();
      if (ImGui::MenuItem("Zamknij", "Alt+F4")) wantExit = true;
      ImGui::EndMenu();
    }
    if (ImGui::BeginMenu("Edycja")) {
      if (ImGui::MenuItem("Cofnij", "Ctrl+Z")) {
        if (app.hasDesign && app.design.undo()) { app.dirty = app.previewDirty = true; app.toast("Cofnięto zmianę", false); }
      }
      if (ImGui::MenuItem("Ponów", "Ctrl+Y")) {
        if (app.hasDesign && app.design.redo()) { app.dirty = app.previewDirty = true; app.toast("Ponowiono zmianę", false); }
      }
      ImGui::Separator();
      if (ImGui::MenuItem("Sprawdź przed laserem", "F8")) activeTab = 9;
      if (ImGui::MenuItem("Eksport produkcyjny", "Ctrl+E")) activeTab = 9;
      ImGui::EndMenu();
    }
    if (ImGui::BeginMenu("Pomoc")) {
      if (ImGui::MenuItem("Instrukcja (INSTRUKCJA.pdf w katalogu aplikacji)"))
        app.toast("Instrukcja: katalog instalacji → INSTRUKCJA.pdf", false);
      if (ImGui::MenuItem("O aplikacji"))
        app.toast("WOJAN LaserCraft AI Studio 1.0 — silnik lokalny, bez chmury i kluczy API.", false);
      ImGui::EndMenu();
    }
    ImGui::SameLine(ImGui::GetWindowWidth() - 380);
    ImGui::TextDisabled("Silnik lokalny · offline · v1.0.0");
    ImGui::EndMainMenuBar();
  }

  ImGuiIO& io = ImGui::GetIO();
  if (io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_N)) app.newProject();
  if (io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_G)) { app.generate(1); activeTab = 4; }
  if (io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_Z)) {
    if (app.hasDesign && app.design.undo()) { app.previewDirty = true; app.dirty = true; }
  }
  if (io.KeyCtrl && ImGui::IsKeyPressed(ImGuiKey_Y)) {
    if (app.hasDesign && app.design.redo()) { app.previewDirty = true; app.dirty = true; }
  }
  if (ImGui::IsKeyPressed(ImGuiKey_F5)) app.previewDirty = true;
  if (ImGui::IsKeyPressed(ImGuiKey_F8)) activeTab = 9;

  ImGui::SetNextWindowPos(ImVec2(0, ImGui::GetFrameHeight()));
  ImGui::SetNextWindowSize(ImVec2(io.DisplaySize.x, io.DisplaySize.y - ImGui::GetFrameHeight()));
  ImGui::Begin("##root", nullptr,
               ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove |
                   ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoBringToFrontOnFocus |
                   ImGuiWindowFlags_NoScrollbar);
  if (ImGui::BeginTabBar("##tabs", ImGuiTabBarFlags_None)) {
    for (int i = 0; i < 12; i++) {
      if (ImGui::BeginTabItem(kTabs[i])) {
        activeTab = i;
        switch (i) {
          case 0: uiDrawGenerator(app); break;
          case 1: uiDrawMic(app); break;
          case 2: uiDrawImport(app); break;
          case 3: uiDrawCategories(app); break;
          case 4: uiDrawEditor(app); break;
          case 5: uiDrawTexts(app); break;
          case 6: uiDrawQr(app); break;
          case 7: uiDrawProjects(app); break;
          case 8: uiDrawCalculator(app); break;
          case 9: uiDrawExport(app); break;
          case 10: uiDrawDiagnostics(app); break;
          case 11: uiDrawSettings(app); break;
        }
        ImGui::EndTabItem();
      }
    }
    ImGui::EndTabBar();
  }
  ImGui::Separator();
  ImGui::TextDisabled("%s", app.statusLine.c_str());
  ImGui::SameLine(ImGui::GetWindowWidth() - 460);
  if (app.hasDesign)
    ImGui::TextDisabled("Obiekty: %d · warstwy: %d · %.0f × %.0f mm",
                        (int)app.design.objects.size(), (int)app.design.layers.size(),
                        app.design.product.w, app.design.product.h);
  else
    ImGui::TextDisabled("Brak projektu");
  ImGui::End();
}

// ================================ 1. Generator AI ================================
void uiDrawGenerator(AppState& app) {
  static char promptBuf[4096] = {0};
  static bool promptInit = false;
  if (!promptInit) { snprintf(promptBuf, sizeof promptBuf, "%s", app.promptText.c_str()); promptInit = true; }

  float fullH = ImGui::GetContentRegionAvail().y;
  ImGui::BeginChild("##genLeft", ImVec2(ImGui::GetContentRegionAvail().x * 0.46f, fullH), false);
  ImGui::TextUnformatted("Opisz, co chcesz wygrawerować (po polsku):");
  ImGui::PushStyleColor(ImGuiCol_FrameBg, ImVec4(0.14f, 0.15f, 0.17f, 1));
  if (ImGui::InputTextMultiline("##prompt", promptBuf, sizeof promptBuf, ImVec2(-1, 110)))
    app.promptText = promptBuf;
  ImGui::PopStyleColor();

  float bw = (ImGui::GetContentRegionAvail().x - 8) / 2;
  ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.96f, 0.55f, 0.10f, 1.0f));
  ImGui::PushStyleColor(ImGuiCol_ButtonHovered, ImVec4(1.0f, 0.68f, 0.25f, 1.0f));
  ImGui::PushStyleColor(ImGuiCol_Text, ImVec4(0.08f, 0.08f, 0.09f, 1.0f));
  if (bigButton("GENERUJ AI", ImVec2(bw, 40), "Generuje projekt na podstawie opisu (Ctrl+G)"))
    app.generate(1);
  ImGui::PopStyleColor(3);
  ImGui::SameLine();
  if (bigButton("GENERUJ 4 WARIANTY", ImVec2(bw, 40), "Cztery różne style tego samego motywu"))
    app.generateVariants4();

  ImGui::Spacing();
  if (bigButton("MIKROFON", ImVec2(bw, 30), "Dyktowanie opisu (zakładka Mikrofon)"))
    app.toast("Przejdź do zakładki „Mikrofon i prompt”.", false);
  ImGui::SameLine();
  if (bigButton("DODAJ ZDJĘCIE", ImVec2(bw, 30), "Import zdjęcia do fotograwerowania")) {
    std::string p = openFileDialog(L"Obrazy (*.png;*.bmp;*.jpg;*.jpeg)\0*.png;*.bmp;*.jpg;*.jpeg\0", "Dodaj zdjęcie");
    if (!p.empty()) {
      snprintf(gImportPathBuf, sizeof gImportPathBuf, "%s", p.c_str());
      app.toast("Zdjęcie wczytane — przejdź do zakładki „Import zdjęcia”.", false);
    }
  }
  if (bigButton("DODAJ PLIK", ImVec2(bw, 30), "Otwórz projekt .wlcproj / archiwum ZIP")) {
    std::string p = openFileDialog(L"Projekty (*.wlcproj;*.zip)\0*.wlcproj;*.zip\0", "Dodaj plik projektu");
    if (!p.empty()) app.openProject(p);
  }
  ImGui::SameLine();
  if (bigButton("ZRÓB ZDJĘCIE", ImVec2(bw, 30),
                "Windows: zrób zdjęcie aplikacją Aparat i wczytaj je przez DODAJ ZDJĘCIE")) {
    app.toast("Zrób zdjęcie w aplikacji Aparat, zapisz i wczytaj przez DODAJ ZDJĘCIE.", false);
  }
  if (bigButton("LOSUJ POMYSŁ", ImVec2(bw, 30), "Losowy pomysł z biblioteki (offline)")) {
    lc::Json req = lc::Json::object();
    req.set("seed", (double)((uint32_t)(ImGui::GetTime() * 7919.0) & 0xFFFF));
    lc::Json r = app.engine.handle("ideas", req);
    if (r.has("idea")) {
      app.promptText = r["idea"].str();
      snprintf(promptBuf, sizeof promptBuf, "%s", app.promptText.c_str());
      app.toast("Pomysł: " + r["idea"].str(), false);
    }
  }
  ImGui::SameLine();
  if (bigButton("POPRAW TEKST", ImVec2(bw, 30), "Poprawia polską ortografię")) {
    lc::Json req = lc::Json::object();
    req.set("text", app.promptText);
    lc::Json r = app.engine.handle("check_spelling", req);
    app.promptText = r["fixed"].str(app.promptText);
    snprintf(promptBuf, sizeof promptBuf, "%s", app.promptText.c_str());
    app.toast("Poprawiono tekst opisu.", false);
  }

  sectionTitle("Ustawienia projektu");
  static const char* styles[] = {"rycina", "retro", "gotyk", "elegancki", "komiks", "minimal", "fantasy", "nowoczesny"};
  static const char* details[] = {"niski", "średni", "wysoki"};
  static const char* engraveTypes[] = {"grawer linii", "grawer wypełnienia", "fotograwer rastrowy"};
  static const char* cuts[] = {"automatycznie (wg produktu)", "wytnij kontur", "tylko grawer (bez cięcia)"};

  auto& cats = lc::categoryCatalog();
  if (ImGui::BeginCombo("Kategoria", cats[std::clamp(app.categoryIdx, 0, (int)cats.size() - 1)].name.c_str())) {
    for (int i = 0; i < (int)cats.size(); i++)
      if (ImGui::Selectable(cats[i].name.c_str(), i == app.categoryIdx)) app.categoryIdx = i;
    ImGui::EndCombo();
  }
  auto& prods = lc::productCatalog();
  if (ImGui::BeginCombo("Przedmiot", prods[std::clamp(app.productIdx, 0, (int)prods.size() - 1)].name.c_str())) {
    for (int i = 0; i < (int)prods.size(); i++)
      if (ImGui::Selectable(prods[i].name.c_str(), i == app.productIdx)) {
        app.productIdx = i;
        app.widthMm = (float)prods[i].w;
        app.heightMm = (float)prods[i].h;
      }
    ImGui::EndCombo();
  }
  static const char* shapes[] = {"prostokąt", "zaokrąglony", "okrąg", "owal", "tarcza", "zawieszka"};
  ImGui::Combo("Kształt", &app.shapeIdx, shapes, 6);
  ImGui::InputFloat("Szerokość (mm)", &app.widthMm, 5, 20, "%.0f");
  ImGui::InputFloat("Wysokość (mm)", &app.heightMm, 5, 20, "%.0f");
  auto& mats = lc::materialCatalog();
  if (ImGui::BeginCombo("Materiał", mats[std::clamp(app.materialIdx, 0, (int)mats.size() - 1)].name.c_str())) {
    for (int i = 0; i < (int)mats.size(); i++)
      if (ImGui::Selectable((mats[i].name + (mats[i].safe ? "" : "  ⚠")).c_str(), i == app.materialIdx))
        app.materialIdx = i;
    ImGui::EndCombo();
  }
  if (!mats.empty() && !mats[std::clamp(app.materialIdx, 0, (int)mats.size() - 1)].safe)
    ImGui::TextColored(ImVec4(1, 0.4f, 0.35f, 1), "⚠ %s",
                       mats[std::clamp(app.materialIdx, 0, (int)mats.size() - 1)].warning.c_str());
  ImGui::Combo("Styl", &app.styleIdx, styles, 8);
  ImGui::Combo("Poziom szczegółowości", &app.detailIdx, details, 3);
  ImGui::Combo("Typ grawerowania", &app.engraveIdx, engraveTypes, 3);
  ImGui::Combo("Cięcie", &app.cutMode, cuts, 3);
  auto& machines = lc::machineCatalog();
  if (ImGui::BeginCombo("Maszyna", machines[std::clamp(app.machineIdx, 0, (int)machines.size() - 1)].name.c_str())) {
    for (int i = 0; i < (int)machines.size(); i++)
      if (ImGui::Selectable((machines[i].name + "  (" + machines[i].controller + ")").c_str(), i == app.machineIdx))
        app.machineIdx = i;
    ImGui::EndCombo();
  }
  if (ImGui::Button("Ostrzeżenia materiałowe", ImVec2(240, 26))) gShowSafety = !gShowSafety;
  if (gShowSafety) {
    ImGui::BeginChild("##safety", ImVec2(0, 140), true);
    for (auto& n : lc::safetyNotes()) {
      ImVec4 col = n.level == "niebezpieczny" ? ImVec4(1, 0.35f, 0.3f, 1)
                   : n.level == "uwaga" ? ImVec4(1, 0.8f, 0.3f, 1)
                                        : ImVec4(0.6f, 0.9f, 0.6f, 1);
      ImGui::TextColored(col, "• %s", n.material.c_str());
      ImGui::SameLine();
      ImGui::TextDisabled("%s", n.text.c_str());
    }
    ImGui::EndChild();
  }
  ImGui::EndChild();

  ImGui::SameLine();
  ImGui::BeginChild("##genRight", ImVec2(0, fullH), false);
  if (!app.variants.empty()) {
    ImGui::TextUnformatted("Warianty (kliknij, aby wybrać):");
    for (int i = 0; i < (int)app.variants.size(); i++) {
      const lc::Json& v = app.variants[i];
      std::string label = "Wariant " + std::to_string(i + 1);
      if (v.has("design")) label += " - " + v["design"]["style"].str();
      if (ImGui::Button((label + "##v" + std::to_string(i)).c_str(), ImVec2(210, 26))) {
        if (v.has("design")) {
          app.setDesign(v["design"], "Wybrano wariant " + std::to_string(i + 1));
          app.texts.clear();
        }
      }
      if (i % 3 != 2) ImGui::SameLine();
    }
    ImGui::Separator();
  }
  float previewH = std::max(240.0f, fullH * (app.texts.empty() ? 0.62f : 0.52f));
  drawPreviewPanel(app, previewH, "##preview");
  ImGui::PushItemWidth(130);
  ImGui::SliderFloat("Jakosc podgladu (px/mm)", &app.previewPxPerMm, 3, 16, "%.0f");
  if (ImGui::IsItemDeactivatedAfterEdit()) app.previewDirty = true;
  ImGui::SameLine();
  if (ImGui::Checkbox("Prowadnice", &app.showGuides)) app.previewDirty = true;
  ImGui::SameLine();
  if (ImGui::Button("Odswiez (F5)")) app.previewDirty = true;
  ImGui::SameLine();
  ImGui::SliderFloat("Zoom", &app.zoom, 0.4f, 3.0f, "%.1fx");
  ImGui::PopItemWidth();
  if (!app.texts.empty()) {
    sectionTitle("Propozycje napisow z silnika");
    ImGui::BeginChild("##texts", ImVec2(0, 110), true);
    for (int i = 0; i < (int)app.texts.size(); i++) {
      const lc::Json& t = app.texts[i];
      std::string line = t["top"].str() + (t["bottom"].str().empty() ? "" : " / " + t["bottom"].str());
      if (ImGui::Selectable((line + "##t" + std::to_string(i)).c_str())) {
        if (app.hasDesign) {
          for (auto& o : app.design.objects) {
            if (o.type != "text") continue;
            if (o.name == "Napis glowny" || o.name == "Napis główny") {
              o.text = t["top"].str();
              app.design.pushHistory("Nowy napis glowny");
              app.previewDirty = app.dirty = true;
              break;
            }
          }
        }
      }
    }
    ImGui::EndChild();
  }
  if (!app.log.empty()) {
    ImGui::Spacing();
    ImGui::TextDisabled("Dziennik silnika:");
    ImGui::BeginChild("##log", ImVec2(0, 64), true);
    for (auto& l : app.log) ImGui::TextDisabled("· %s", l.c_str());
    ImGui::EndChild();
  }
  ImGui::EndChild();
}

// ================================ 2. Mikrofon ================================
void uiDrawMic(AppState& app) {
  static char buf[2048] = {0};
  ImGui::Columns(2, "##miccols", true);
  ImGui::SetColumnWidth(0, ImGui::GetContentRegionAvail().x * 0.5f);
  sectionTitle("Dyktowanie opisu (offline)");
  ImGui::TextWrapped(
      "Nagranie zapisujemy lokalnie jako WAV 16 kHz. Windows 11 nie udostepnia darmowego, w pelni "
      "offline silnika rozpoznawania mowy dla aplikacji desktop bez instalacji pakietu jezyka. "
      "Dlatego nagrywamy i zapisujemy dzwiek, a transkrypcje wpisujesz lub wklejasz ponizej - caly "
      "pozostaly potok (analiza promptu, projekt, eksport) dziala w 100%% lokalnie.");
  if (ImGui::Button(app.micRecording ? "ZATRZYMAJ NAGRANIE" : "NAGRAJ (mikrofon)", ImVec2(240, 36))) {
    std::string err;
    if (!app.micRecording) {
      if (startMicRecording(err)) {
        app.micRecording = true;
        app.toast("Nagrywanie... mow wyraznie, 5-20 s.", false);
      } else {
        app.toast(err, true);
      }
    } else {
      std::string path = app.dataDir + "\\nagranie.wav";
      if (stopMicRecording(path, err)) {
        app.micRecording = false;
        app.micInfo = path;
        app.toast("Zapisano nagranie: " + path, false);
      } else {
        app.toast(err, true);
        app.micRecording = false;
      }
    }
  }
  if (app.micRecording) ImGui::TextColored(ImVec4(1, 0.35f, 0.3f, 1), "NAGRYWANIE...");
  if (!app.micInfo.empty()) ImGui::TextDisabled("Plik: %s", app.micInfo.c_str());
  sectionTitle("Transkrypcja / opis");
  if (buf[0] == 0) snprintf(buf, sizeof buf, "%s", app.transcript.c_str());
  if (ImGui::InputTextMultiline("##transcript", buf, sizeof buf, ImVec2(-1, 170)))
    app.transcript = buf;
  if (ImGui::Button("Wyslij do generatora", ImVec2(200, 30))) {
    app.promptText = app.transcript;
    app.generate(1);
  }
  ImGui::SameLine();
  if (ImGui::Button("Popraw polski tekst", ImVec2(180, 30))) {
    lc::Json req = lc::Json::object();
    req.set("text", app.transcript);
    lc::Json r = app.engine.handle("check_spelling", req);
    app.transcript = r["fixed"].str(app.transcript);
    snprintf(buf, sizeof buf, "%s", app.transcript.c_str());
    app.toast("Poprawiono tekst.", false);
  }
  ImGui::NextColumn();
  sectionTitle("Jak mowic, aby silnik dobrze zrozumial");
  ImGui::BulletText("Podaj przedmiot: tabliczka, piersiowka, brelok, kubek.");
  ImGui::BulletText("Podaj wymiary: 200 na 150 milimetrow.");
  ImGui::BulletText("Podaj motyw: szczupak, wilk, jelen, kolo zebate.");
  ImGui::BulletText("Napis podaj WERSALIKAMI: napis KROL LOWISKA.");
  ImGui::BulletText("Zaznacz ciecie: wytnij kontur albo tylko grawer, bez ciecia.");
  ImGui::Spacing();
  sectionTitle("Prywatnosc");
  ImGui::TextWrapped("Dzwiek nie opuszcza komputera. Aplikacja nie ma klienta HTTP ani telemetrii.");
  ImGui::Columns(1);
}

// ================================ 3. Import zdjęcia ================================
void uiDrawImport(AppState& app) {
  static char nameBuf[128] = "Zdjęcie";
  ImGui::Columns(2, "##impcols", true);
  ImGui::SetColumnWidth(0, ImGui::GetContentRegionAvail().x * 0.45f);
  sectionTitle("Plik");
  if (ImGui::Button("Wybierz zdjecie...", ImVec2(200, 32))) {
    std::string p = openFileDialog(L"Obrazy (*.png;*.bmp)\0*.png;*.bmp\0", "Wybierz zdjecie");
    if (!p.empty()) snprintf(gImportPathBuf, sizeof gImportPathBuf, "%s", p.c_str());
  }
  ImGui::InputText("Sciezka", gImportPathBuf, sizeof gImportPathBuf);
  if (ImGui::Button("Wczytaj", ImVec2(120, 28))) {
    std::string path = gImportPathBuf;
    std::vector<uint8_t> bytes;
    if (!lc::readFileBytes(path, bytes) || bytes.empty()) {
      app.toast("Nie mozna odczytac pliku: " + path, true);
    } else {
      lc::Image img;
      bool ok = lc::pngDecode(bytes, img) || lc::bmpDecode(bytes, img);
      if (!ok) {
        app.toast("Obslugiwane wewnetrznie: PNG i BMP. Plik zapisz jako PNG (Paint: Zapisz jako -> PNG).", true);
      } else {
        gImportBytes = bytes;
        gImportSrcW = img.w;
        gImportSrcH = img.h;
        gCropX = gCropY = 0;
        gCropW = img.w;
        gCropH = img.h;
        GpuTex t = makeTexture(app.device, lc::pngEncode(img));
        if (t.valid()) { gImportPreview.release(); gImportPreview = t; }
        app.toast("Wczytano obraz " + std::to_string(img.w) + "x" + std::to_string(img.h), false);
      }
    }
  }
  sectionTitle("Obrobka pod grawer");
  ImGui::SliderFloat("Jasnosc", &gBrightness, -100, 100, "%.0f");
  ImGui::SliderFloat("Kontrast", &gContrast, -100, 100, "%.0f");
  ImGui::SliderFloat("Gamma", &gGamma, 0.3f, 2.5f, "%.2f");
  ImGui::SliderInt("Progowanie", &gThreshold, 1, 254);
  static const char* dithers[] = {"brak (progowanie)", "Floyd-Steinberg", "Bayer 8x8", "Atkinson"};
  static const char* ditherIds[] = {"none", "floyd", "bayer", "atkinson"};
  ImGui::Combo("Dithering", &gDitherIdx, dithers, 4);
  ImGui::Checkbox("Usun tlo (jednolite)", &gRemoveBg);
  ImGui::InputFloat("Szerokosc na projekcie (mm)", &gImportWidthMm, 5, 20, "%.0f");
  ImGui::InputText("Nazwa obiektu", nameBuf, sizeof nameBuf);

  if (bigButton("PRZYGOTUJ GRAWER (podglad)", ImVec2(-1, 34))) {
    if (gImportBytes.empty()) {
      app.toast("Najpierw wczytaj zdjecie.", true);
    } else {
      lc::Json req = lc::Json::object();
      req.set("pngBase64", lc::base64Encode(gImportBytes));
      lc::Json ops = lc::Json::array();
      auto op = [&](const char* nm) {
        lc::Json o = lc::Json::object();
        o.set("op", nm);
        return o;
      };
      ops.push(op("gray"));
      if (gRemoveBg) { lc::Json o = op("removeBg"); o.set("tolerance", 40.0); ops.push(o); }
      if (std::fabs(gBrightness) > 0.5f) { lc::Json o = op("brightness"); o.set("value", (double)gBrightness); ops.push(o); }
      if (std::fabs(gContrast) > 0.5f) { lc::Json o = op("contrast"); o.set("value", (double)gContrast); ops.push(o); }
      if (std::fabs(gGamma - 1.0f) > 0.01f) { lc::Json o = op("gamma"); o.set("value", (double)gGamma); ops.push(o); }
      if (gCropW > 0 && gCropH > 0 && (gCropX || gCropY || gCropW != gImportSrcW || gCropH != gImportSrcH)) {
        lc::Json o = op("crop");
        o.set("x", (double)gCropX); o.set("y", (double)gCropY);
        o.set("w", (double)gCropW); o.set("h", (double)gCropH);
        ops.push(o);
      }
      if (gDitherIdx > 0) {
        lc::Json o = op("dither");
        o.set("algo", ditherIds[gDitherIdx]);
        ops.push(o);
      } else {
        lc::Json o = op("threshold");
        o.set("value", (double)gThreshold);
        ops.push(o);
      }
      req.set("ops", ops);
      lc::Json r = app.engine.handle("image_edit", req);
      if (r["ok"].boolean(false)) {
        GpuTex t = makeTexture(app.device, lc::base64Decode(r["pngBase64"].str()));
        if (t.valid()) { gImportPreview.release(); gImportPreview = t; }
        gImportImage = r;
        app.toast("Przygotowano obraz: " + std::to_string(r["width"].integer(0)) + "x" +
                  std::to_string(r["height"].integer(0)), false);
      } else {
        app.toast(r.has("error") ? r["error"].str() : "Blad obrobki obrazu", true);
      }
    }
  }
  if (bigButton("WSTAW DO PROJEKTU", ImVec2(-1, 34))) {
    if (!app.hasDesign) {
      app.toast("Najpierw wygeneruj projekt (zakladka Generator AI).", true);
    } else if (gImportImage.size() == 0) {
      app.toast("Najpierw przygotuj grawer.", true);
    } else {
      lc::Json req = lc::Json::object();
      req.set("design", app.design.toJson());
      req.set("pngBase64", gImportImage["pngBase64"]);
      req.set("widthMm", (double)gImportWidthMm);
      req.set("name", std::string(nameBuf));
      req.set("grayscale", true);
      req.set("removeBg", gRemoveBg);
      req.set("dither", std::string(ditherIds[gDitherIdx]));
      req.set("threshold", (double)gThreshold);
      lc::Json r = app.engine.handle("import_image", req);
      if (r["ok"].boolean(false)) {
        app.setDesign(r["design"], "Wstawiono zdjecie do projektu");
        app.toast("Zdjecie dodane na warstwie ENGRAVE_RASTER.", false);
      } else {
        app.toast(r.has("error") ? r["error"].str() : "Nie udalo sie wstawic zdjecia", true);
      }
    }
  }
  ImGui::NextColumn();
  sectionTitle("Podglad");
  ImGui::BeginChild("##impprev", ImVec2(0, ImGui::GetContentRegionAvail().y - 110), true);
  if (gImportPreview.valid()) {
    ImVec2 avail = ImGui::GetContentRegionAvail();
    float ar = (float)gImportPreview.w / std::max(1, gImportPreview.h);
    float w = std::min(avail.x, avail.y * ar);
    ImGui::Image((ImTextureID)(intptr_t)gImportPreview.srv, ImVec2(w, w / ar));
    ImGui::TextDisabled("%d x %d px", gImportPreview.w, gImportPreview.h);
  } else {
    ImGui::TextDisabled("Brak obrazu - wczytaj plik PNG/BMP.");
  }
  ImGui::EndChild();
  sectionTitle("Kadrowanie (px)");
  ImGui::PushItemWidth(90);
  ImGui::InputInt("X", &gCropX);
  ImGui::SameLine();
  ImGui::InputInt("Y", &gCropY);
  ImGui::SameLine();
  ImGui::InputInt("W", &gCropW);
  ImGui::SameLine();
  ImGui::InputInt("H", &gCropH);
  ImGui::PopItemWidth();
  if (ImGui::Button("Reset kadru")) { gCropX = gCropY = 0; gCropW = gImportSrcW; gCropH = gImportSrcH; }
  ImGui::SameLine();
  ImGui::TextDisabled("Wejscie: %d x %d", gImportSrcW, gImportSrcH);
  ImGui::Columns(1);
}

// ================================ 4. Kategorie ================================
void uiDrawCategories(AppState& app) {
  ImGui::TextWrapped("Kategorie biblioteki (50 + wlasny pomysl). Klikniecie ustawia kategorie i sugerowany produkt.");
  ImGui::InputText("Szukaj", gSearch, sizeof gSearch);
  std::string q = lc::lowerAscii(lc::stripDiacritics(gSearch));
  ImGui::Separator();
  ImGui::BeginChild("##cats", ImVec2(0, ImGui::GetContentRegionAvail().y * 0.52f), true);
  auto& cats = lc::categoryCatalog();
  float availW = ImGui::GetContentRegionAvail().x;
  int perRow = std::max(3, (int)(availW / 240));
  int inRow = 0;
  for (int i = 0; i < (int)cats.size(); i++) {
    std::string name = cats[i].name;
    if (!q.empty() && lc::lowerAscii(lc::stripDiacritics(name)).find(q) == std::string::npos) continue;
    bool sel = (i == app.categoryIdx);
    if (sel) ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.96f, 0.55f, 0.10f, 0.9f));
    if (ImGui::Button((name + "##c" + std::to_string(i)).c_str(), ImVec2(availW / perRow - 8, 34))) {
      app.categoryIdx = i;
      auto& prods = lc::productCatalog();
      for (int k = 0; k < (int)prods.size(); k++)
        if (prods[k].id == cats[i].suggestedProduct) {
          app.productIdx = k;
          app.widthMm = (float)prods[k].w;
          app.heightMm = (float)prods[k].h;
        }
      app.toast("Kategoria: " + name, false);
    }
    if (sel) ImGui::PopStyleColor();
    if (ImGui::IsItemHovered()) ImGui::SetTooltip("%s\nMotyw: %s", cats[i].blurb.c_str(), cats[i].emblem.c_str());
    if (++inRow % perRow) ImGui::SameLine();
  }
  ImGui::EndChild();
  sectionTitle("Szablony gotowych projektow");
  ImGui::BeginChild("##templates", ImVec2(0, 0), true);
  for (auto& t : lc::templateCatalog()) {
    if (ImGui::Button(("Uzyj: " + t.name + "##tpl" + t.id).c_str(), ImVec2(280, 30))) {
      app.promptText = t.prompt;
      app.generate(1);
    }
    ImGui::SameLine();
    ImGui::TextDisabled("%s", t.prompt.c_str());
  }
  ImGui::EndChild();
}

// ================================ 5. Edytor ================================
void uiDrawEditor(AppState& app) {
  if (!app.hasDesign) {
    ImGui::TextDisabled("Brak projektu - wygeneruj go w zakladce Generator AI (Ctrl+G).");
    return;
  }
  float fullH = ImGui::GetContentRegionAvail().y;
  if (ImGui::Button("Cofnij (Ctrl+Z)")) { if (app.design.undo()) { app.previewDirty = app.dirty = true; } }
  ImGui::SameLine();
  if (ImGui::Button("Ponow (Ctrl+Y)")) { if (app.design.redo()) { app.previewDirty = app.dirty = true; } }
  ImGui::SameLine();
  if (ImGui::Button("Zablokuj / odblokuj")) {
    if (app.selectedObj >= 0 && app.selectedObj < (int)app.design.objects.size()) {
      auto& o = app.design.objects[app.selectedObj];
      o.locked = !o.locked;
      app.design.pushHistory(o.locked ? "Zablokowano obiekt" : "Odblokowano obiekt");
      app.toast(o.locked ? "Obiekt zablokowany" : "Obiekt odblokowany", false);
      app.dirty = true;
    }
  }
  ImGui::SameLine();
  ImGui::TextDisabled("Tryb: %s", app.objectMode ? "obiekty" : "wezly");
  ImGui::SameLine();
  if (ImGui::Checkbox("Tryb PRO", &app.proMode)) {}
  ImGui::SameLine();
  ImGui::Checkbox("Siatka mm", &app.gridSnap);
  ImGui::SameLine();
  ImGui::SetNextItemWidth(100);
  ImGui::DragFloat("Siatka", &app.gridMm, 0.5f, 1.0f, 50.0f, "%.1f mm");

  ImGui::Columns(3, "##edcols", true);
  ImGui::SetColumnWidth(0, 260);
  ImGui::SetColumnWidth(1, ImGui::GetContentRegionAvail().x - 330);

  ImGui::TextUnformatted("Warstwy");
  for (auto& l : app.design.layers) {
    ImGui::PushID(l.id.c_str());
    if (ImGui::Checkbox("##vis", &l.visible)) app.previewDirty = true;
    ImGui::SameLine();
    ImGui::TextUnformatted(l.label.c_str());
    ImGui::PopID();
  }
  ImGui::Separator();
  ImGui::TextUnformatted("Obiekty");
  ImGui::BeginChild("##objs", ImVec2(0, fullH - 330), true);
  for (int i = 0; i < (int)app.design.objects.size(); i++) {
    auto& o = app.design.objects[i];
    std::string label = o.name.empty() ? o.id : o.name;
    label = "[" + o.layer.substr(0, 4) + "] " + label;
    if (o.locked) label += " [zablok.]";
    if (ImGui::Selectable((label + "##o" + std::to_string(i)).c_str(), i == app.selectedObj))
      app.selectedObj = i;
  }
  ImGui::EndChild();
  if (ImGui::Button("Usun obiekt", ImVec2(-1, 26))) {
    if (app.selectedObj >= 0 && app.selectedObj < (int)app.design.objects.size()) {
      if (app.design.objects[app.selectedObj].locked) {
        app.toast("Obiekt jest zablokowany.", true);
      } else {
        app.design.objects.erase(app.design.objects.begin() + app.selectedObj);
        app.design.pushHistory("Usunieto obiekt");
        app.selectedObj = -1;
        app.previewDirty = app.dirty = true;
      }
    }
  }
  if (ImGui::Button("Duplikuj obiekt", ImVec2(-1, 26))) {
    if (app.selectedObj >= 0 && app.selectedObj < (int)app.design.objects.size()) {
      lc::Obj c = app.design.objects[app.selectedObj];
      c.id = app.design.nextId("dup");
      c.x += 3;
      c.y += 3;
      app.design.objects.push_back(c);
      app.design.pushHistory("Duplikat obiektu");
      app.previewDirty = app.dirty = true;
    }
  }
  if (ImGui::Button("Polacz sciezki (boolean)", ImVec2(-1, 26))) {
    if (app.selectedObj >= 0 && app.selectedObj + 1 < (int)app.design.objects.size()) {
      auto& a = app.design.objects[app.selectedObj];
      auto& b = app.design.objects[app.selectedObj + 1];
      if (a.type == "path" && b.type == "path") {
        a.d = a.d + " " + b.d;
        app.design.objects.erase(app.design.objects.begin() + app.selectedObj + 1);
        app.design.pushHistory("Operacja boolean (polaczone sciezki)");
        app.previewDirty = app.dirty = true;
        app.toast("Polaczono sciezki (regula even-odd).", false);
      } else {
        app.toast("Operacje boolean wymagaja dwoch obiektow sciezkowych.", true);
      }
    }
  }

  ImGui::NextColumn();
  drawPreviewPanel(app, fullH - 60, "##canvas");
  ImGui::TextDisabled("Zaznacz obiekt z listy, aby zmienic jego parametry w inspektorze.");

  ImGui::NextColumn();
  sectionTitle("Wlasciwosci");
  if (app.selectedObj >= 0 && app.selectedObj < (int)app.design.objects.size()) {
    auto& o = app.design.objects[app.selectedObj];
    ImGui::TextUnformatted(o.name.c_str());
    ImGui::TextDisabled("id: %s | typ: %s | warstwa: %s", o.id.c_str(), o.type.c_str(), o.layer.c_str());
    if (o.locked) ImGui::TextColored(ImVec4(1, 0.7f, 0.3f, 1), "Obiekt zablokowany");
    bool changed = false;
    ImGui::PushItemWidth(120);
    if (o.type == "text") {
      static char tb[256];
      snprintf(tb, sizeof tb, "%s", o.text.c_str());
      if (ImGui::InputText("Tresc", tb, sizeof tb)) { o.text = tb; changed = true; }
      static const char* fonts[] = {"anton", "bebas", "cinzel", "cinzel_deco", "playfair", "oswald", "abril", "pirata", "barlow"};
      int cur = 0;
      for (int i = 0; i < 9; i++) if (o.font == fonts[i]) cur = i;
      if (ImGui::Combo("Font", &cur, fonts, 9)) { o.font = fonts[cur]; changed = true; }
      float fsz = (float)o.size, ftrk = (float)o.tracking;
      if (ImGui::DragFloat("Rozmiar (mm)", &fsz, 0.2f, 1.0f, 200.0f, "%.1f")) { o.size = fsz; changed = true; }
      if (ImGui::DragFloat("Tracking", &ftrk, 0.01f, -2.0f, 20.0f, "%.2f")) { o.tracking = ftrk; changed = true; }
      static const char* warps[] = {"none", "arc", "bridge", "wave"};
      int w = 0;
      for (int i = 0; i < 4; i++) if (o.warp == warps[i]) w = i;
      if (ImGui::Combo("Zgiecie", &w, warps, 4)) { o.warp = warps[w]; changed = true; }
    }
    float fx = (float)o.x, fy = (float)o.y, fsx = (float)o.sx, fsy = (float)o.sy, frot = (float)o.rot,
          fsw = (float)o.strokeW, fsize = (float)o.size, ftr = (float)o.tracking;
    if (ImGui::DragFloat("X (mm)", &fx, 0.1f, -500.0f, 500.0f, "%.2f")) { o.x = fx; changed = true; }
    if (ImGui::DragFloat("Y (mm)", &fy, 0.1f, -500.0f, 500.0f, "%.2f")) { o.y = fy; changed = true; }
    if (ImGui::DragFloat("Skala X", &fsx, 0.01f, 0.05f, 10.0f, "%.2f")) { o.sx = fsx; changed = true; }
    if (ImGui::DragFloat("Skala Y", &fsy, 0.01f, 0.05f, 10.0f, "%.2f")) { o.sy = fsy; changed = true; }
    if (ImGui::DragFloat("Obrót (st.)", &frot, 0.5f, -360.0f, 360.0f, "%.1f")) { o.rot = frot; changed = true; }
    if (ImGui::DragFloat("Grubosc linii", &fsw, 0.01f, 0.0f, 3.0f, "%.2f")) { o.strokeW = fsw; changed = true; }
    (void)fsize; (void)ftr;
    if (o.type == "image") {
      float fiw = (float)o.imgW, fih = (float)o.imgH;
      if (ImGui::DragFloat("Szerokosc obrazu", &fiw, 0.5f, 1.0f, 500.0f, "%.1f mm")) { o.imgW = fiw; changed = true; }
      if (ImGui::DragFloat("Wysokosc obrazu", &fih, 0.5f, 1.0f, 500.0f, "%.1f mm")) { o.imgH = fih; changed = true; }
      int thr = o.threshold;
      if (ImGui::SliderInt("Progowanie", &thr, 1, 254)) { o.threshold = thr; changed = true; }
    }
    ImGui::PopItemWidth();
    if (ImGui::Button("Przyciagnij do siatki", ImVec2(-1, 26))) {
      o.x = std::round(o.x / app.gridMm) * app.gridMm;
      o.y = std::round(o.y / app.gridMm) * app.gridMm;
      changed = true;
    }
    if (ImGui::Button("Wysrodkuj w produkcie", ImVec2(-1, 26))) {
      if (o.type == "text") o.x = app.design.product.w / 2;
      else { o.x = 0; o.y = 0; }
      changed = true;
    }
    if (changed) { app.previewDirty = app.dirty = true; }
    if (ImGui::Button("Zapisz stan (historia)", ImVec2(-1, 26)))
      app.design.pushHistory("Edycja obiektu " + o.id);
  } else {
    ImGui::TextDisabled("Wybierz obiekt z listy.");
  }
  sectionTitle("Projekt");
  ImGui::PushItemWidth(120);
  float pw = (float)app.design.product.w, ph = (float)app.design.product.h;
  if (ImGui::DragFloat("Szerokosc produktu", &pw, 1.0f, 10.0f, 1000.0f, "%.0f mm")) {
    app.design.product.w = pw;
    app.previewDirty = app.dirty = true;
  }
  if (ImGui::DragFloat("Wysokosc produktu", &ph, 1.0f, 10.0f, 1000.0f, "%.0f mm")) {
    app.design.product.h = ph;
    app.previewDirty = app.dirty = true;
  }
  ImGui::PopItemWidth();
  if (ImGui::Button("Sprawdz projekt (walidacja)", ImVec2(-1, 30))) {
    lc::Json req = lc::Json::object();
    req.set("design", app.design.toJson());
    lc::Json r = app.engine.handle("validate", req);
    snprintf(gValidateInfo, sizeof gValidateInfo, "%s", r["text"].str().c_str());
    app.toast(lc::sfmt("Walidacja: %d bledow, %d ostrzezen", r["errors"].integer(0), r["warnings"].integer(0)),
              r["errors"].integer(0) > 0);
  }
  ImGui::Columns(1);
}

// ================================ 6. Napisy AI ================================
void uiDrawTexts(AppState& app) {
  auto& cats = lc::categoryCatalog();
  static const char* tones[] = {"zartobliwy", "powazny", "wzruszajacy", "sarkastyczny", "motywacyjny"};
  ImGui::Columns(2, "##txtcols", true);
  ImGui::SetColumnWidth(0, ImGui::GetContentRegionAvail().x * 0.45f);
  sectionTitle("Generator napisow (lokalny)");
  if (ImGui::BeginCombo("Kategoria", cats[std::clamp(gTextCategoryIdx, 0, (int)cats.size() - 1)].name.c_str())) {
    for (int i = 0; i < (int)cats.size(); i++)
      if (ImGui::Selectable(cats[i].name.c_str(), i == gTextCategoryIdx)) gTextCategoryIdx = i;
    ImGui::EndCombo();
  }
  ImGui::Combo("Ton", &gTextToneIdx, tones, 5);
  ImGui::SliderInt("Liczba propozycji", &gTextCount, 3, 8);
  ImGui::InputText("Dedykacja (opcjonalnie)", gDedication, sizeof gDedication);
  ImGui::InputText("Dokladny tekst (opcjonalnie)", gExactText, sizeof gExactText);
  if (bigButton("GENERUJ NAPISY", ImVec2(-1, 34))) {
    lc::Json req = lc::Json::object();
    req.set("category", cats[gTextCategoryIdx].id);
    req.set("tone", std::string(tones[gTextToneIdx]));
    req.set("count", (double)gTextCount);
    req.set("dedication", std::string(gDedication));
    req.set("exactText", std::string(gExactText));
    gTextSuggestions = app.engine.handle("texts", req);
    app.toast("Wygenerowano propozycje napisow.", false);
  }
  if (bigButton("LOSUJ POMYSL", ImVec2(-1, 30))) {
    lc::Json req = lc::Json::object();
    req.set("seed", (double)((uint32_t)(ImGui::GetTime() * 3571.0) & 0xFFFF));
    lc::Json r = app.engine.handle("ideas", req);
    snprintf(gIdeaText, sizeof gIdeaText, "%s", r["idea"].str().c_str());
  }
  if (gIdeaText[0]) {
    ImGui::TextWrapped("Pomysl: %s", gIdeaText);
    if (ImGui::Button("Uzyj jako opisu projektu")) { app.promptText = gIdeaText; app.generate(1); }
  }
  sectionTitle("Poprawnosc jezykowa");
  static char checkBuf[256] = {0};
  ImGui::InputText("Tekst do sprawdzenia", checkBuf, sizeof checkBuf);
  if (ImGui::Button("Sprawdz i popraw", ImVec2(-1, 28))) {
    lc::Json req = lc::Json::object();
    req.set("text", std::string(checkBuf));
    lc::Json r = app.engine.handle("check_spelling", req);
    gSpellingInfo = "Poprawiona wersja: " + r["fixed"].str();
    snprintf(checkBuf, sizeof checkBuf, "%s", r["fixed"].str().c_str());
  }
  if (!gSpellingInfo.empty()) ImGui::TextWrapped("%s", gSpellingInfo.c_str());

  ImGui::NextColumn();
  sectionTitle("Propozycje");
  ImGui::BeginChild("##sugg", ImVec2(0, ImGui::GetContentRegionAvail().y - 120), true);
  int shown = 0;
  if (gTextSuggestions.has("texts")) {
    int i = 0;
    for (auto& t : gTextSuggestions["texts"].items()) {
      ImGui::PushID(i);
      ImGui::TextWrapped("%s", t["top"].str().c_str());
      ImGui::TextDisabled("%s", t["bottom"].str().c_str());
      if (ImGui::SmallButton("Wstaw jako napis glowny")) {
        if (app.hasDesign) {
          bool done = false;
          for (auto& o : app.design.objects) {
            if (o.type == "text" && (o.name == "Napis glowny" || o.name == "Napis główny")) {
              o.text = t["top"].str();
              done = true;
              break;
            }
          }
          if (done) {
            app.design.pushHistory("Napis glowny z generatora");
            app.previewDirty = app.dirty = true;
            app.toast("Ustawiono napis: " + t["top"].str(), false);
          } else {
            app.toast("Projekt nie ma napisu glownego - wygeneruj projekt od nowa.", true);
          }
        } else {
          app.toast("Brak projektu.", true);
        }
      }
      ImGui::SameLine();
      if (ImGui::SmallButton("Ustaw oba napisy")) {
        if (app.hasDesign) {
          for (auto& o : app.design.objects) {
            if (o.type != "text") continue;
            if (o.name == "Napis glowny" || o.name == "Napis główny") o.text = t["top"].str();
            else if (o.name.rfind("Napis dolny", 0) == 0) o.text = t["bottom"].str();
          }
          app.design.pushHistory("Napis z generatora");
          app.previewDirty = app.dirty = true;
          app.toast("Ustawiono oba napisy.", false);
        }
      }
      ImGui::SameLine();
      if (ImGui::SmallButton("Kopiuj")) ImGui::SetClipboardText(t["top"].str().c_str());
      ImGui::Separator();
      ImGui::PopID();
      i++;
      shown++;
    }
  }
  if (shown == 0) ImGui::TextDisabled("Brak propozycji - kliknij GENERUJ NAPISY.");
  ImGui::EndChild();
  sectionTitle("Aktualne napisy w projekcie");
  ImGui::InputText("Gorny", app.textTopBuf, sizeof app.textTopBuf);
  ImGui::InputText("Dolny", app.textBottomBuf, sizeof app.textBottomBuf);
  if (ImGui::Button("Zastosuj do projektu", ImVec2(200, 30)) && app.hasDesign) {
    for (auto& o : app.design.objects) {
      if (o.type != "text") continue;
      if (o.name == "Napis glowny" || o.name == "Napis główny") o.text = app.textTopBuf;
      else if (o.name.rfind("Napis dolny", 0) == 0) o.text = app.textBottomBuf;
    }
    app.design.pushHistory("Reczna zmiana napisow");
    app.previewDirty = app.dirty = true;
    app.toast("Zastosowano napisy.", false);
  }
  ImGui::Columns(1);
}

// ================================ 7. QR ================================
void uiDrawQr(AppState& app) {
  static char dataBuf[1024] = "https://wojan.pl";
  ImGui::Columns(2, "##qrcols", true);
  ImGui::SetColumnWidth(0, ImGui::GetContentRegionAvail().x * 0.5f);
  sectionTitle("Generator QR (wektorowy)");
  if (ImGui::InputTextMultiline("Dane (URL / tekst / Wi-Fi)", dataBuf, sizeof dataBuf, ImVec2(-1, 80)))
    app.qrData = dataBuf;
  static const char* ecc[] = {"L (7%)", "M (15%)", "Q (25%)", "H (30%)"};
  ImGui::Combo("Korekcja bledow", &app.qrEccIdx, ecc, 4);
  ImGui::SliderFloat("Rozmiar (mm)", &app.qrSizeMm, 10, 150, "%.0f");
  if (ImGui::Button("GENERUJ QR", ImVec2(180, 32))) {
    app.qrData = dataBuf;
    app.refreshQr();
  }
  ImGui::SameLine();
  if (ImGui::Button("Wstaw do projektu", ImVec2(180, 32))) {
    if (!app.hasDesign) {
      app.toast("Brak projektu.", true);
    } else {
      app.qrData = dataBuf;
      lc::Json req = lc::Json::object();
      req.set("data", app.qrData);
      req.set("size", (double)app.qrSizeMm);
      lc::Json r = app.engine.handle("qr", req);
      if (r["ok"].boolean(false)) {
        lc::Obj o;
        o.id = app.design.nextId("qr");
        o.type = "qr";
        o.layer = lc::LAYER_ENGRAVE_FILL;
        o.role = "fill";
        o.name = "Kod QR";
        o.qrData = app.qrData;
        o.qrSize = app.qrSizeMm;
        o.x = (app.design.product.w - o.qrSize) / 2;
        o.y = (app.design.product.h - o.qrSize) / 2;
        app.design.objects.push_back(o);
        app.design.pushHistory("Dodano kod QR");
        app.previewDirty = app.dirty = true;
        app.toast("Dodano QR (" + r["selfCheck"].str() + ").", false);
      } else {
        app.toast("Nie udalo sie zakodowac danych QR.", true);
      }
    }
  }
  if (!app.qrCheck.empty()) {
    ImGui::Spacing();
    ImGui::TextWrapped("%s", app.qrCheck.c_str());
  }
  ImGui::Spacing();
  ImGui::TextWrapped("Test: macierz QR sprawdzana wewnetrznie (wzorce, informacja formatu, odczyt zwrotny) "
                     "oraz dekoderem zewnetrznym (ZXing / OpenCV) w testach wydania.");
  ImGui::NextColumn();
  sectionTitle("Podglad");
  if (app.qrPreview.valid()) {
    ImGui::Image((ImTextureID)(intptr_t)app.qrPreview.srv, ImVec2(320, 320));
    ImGui::TextDisabled("%d x %d px (8 px / modul + cicha strefa)", app.qrPreview.w, app.qrPreview.h);
  } else {
    ImGui::TextDisabled("Brak podgladu - kliknij GENERUJ QR.");
  }
  ImGui::Columns(1);
}

// ================================ 8. Moje projekty ================================
void uiDrawProjects(AppState& app) {
  if (ImGui::Button("Odswiez liste", ImVec2(150, 28))) app.refreshProjects();
  ImGui::SameLine();
  if (ImGui::Button("Otworz plik...", ImVec2(150, 28))) {
    std::string p = openFileDialog(L"Projekty (*.wlcproj;*.zip)\0*.wlcproj;*.zip\0", "Otworz projekt");
    if (!p.empty()) app.openProject(p);
  }
  ImGui::SameLine();
  if (ImGui::Button("Zapisz biezacy", ImVec2(150, 28))) {
    if (!app.hasDesign) {
      app.toast("Brak projektu.", true);
    } else {
      lc::Json req = lc::Json::object();
      req.set("design", app.design.toJson());
      lc::Json r = app.engine.handle("save_project", req);
      if (r["ok"].boolean(false)) {
        app.toast("Zapisano w bibliotece: " + r["path"].str(), false);
        app.refreshProjects();
      } else {
        app.toast(r.has("error") ? r["error"].str() : "Blad zapisu", true);
      }
    }
  }
  ImGui::SameLine();
  if (ImGui::Button("Kopia zapasowa ZIP...", ImVec2(190, 28))) {
    if (!app.hasDesign) {
      app.toast("Brak projektu.", true);
    } else {
      std::string p = saveFileDialog(L"Archiwum ZIP (*.zip)\0*.zip\0", "Kopia zapasowa projektu", "kopia-projektu.zip");
      if (!p.empty()) {
        lc::Json req = lc::Json::object();
        req.set("design", app.design.toJson());
        lc::Json r = app.engine.handle("export_zip", req);
        auto bytes = lc::base64Decode(r["zipBase64"].str());
        if (!bytes.empty() && lc::writeFileBytes(p, bytes)) app.toast("Zapisano kopie: " + p, false);
        else app.toast("Nie udalo sie zapisac archiwum", true);
      }
    }
  }
  ImGui::Separator();
  if (app.projectList.size() == 0) app.refreshProjects();
  ImGui::TextDisabled("Katalog: %s", app.projectList.has("dir") ? app.projectList["dir"].str().c_str() : "(domyslny)");
  ImGui::BeginChild("##projlist", ImVec2(0, ImGui::GetContentRegionAvail().y), true);
  int idx = 0;
  if (app.projectList.has("projects")) {
    for (auto& p : app.projectList["projects"].items()) {
      ImGui::PushID(idx);
      bool fav = p["favorite"].boolean(false);
      ImGui::TextColored(fav ? ImVec4(1, 0.85f, 0.3f, 1) : ImVec4(0.9f, 0.9f, 0.9f, 1), "%s %s",
                         fav ? "*" : "-", p["name"].str().c_str());
      ImGui::SameLine(380);
      ImGui::TextDisabled("%s | %s | %s", p["product"].str().c_str(), p["size"].str().c_str(),
                          p["modified"].str().c_str());
      ImGui::SameLine(860);
      if (ImGui::SmallButton("Otworz")) app.openProject(p["path"].str());
      ImGui::SameLine();
      if (ImGui::SmallButton(fav ? "Odznacz" : "Ulubione")) {
        lc::Json req = lc::Json::object();
        req.set("path", p["path"]);
        req.set("favorite", !fav);
        app.engine.handle("favorite_project", req);
        app.refreshProjects();
      }
      ImGui::SameLine();
      if (ImGui::SmallButton("Duplikuj")) {
        lc::Json req = lc::Json::object();
        req.set("path", p["path"]);
        lc::Json r = app.engine.handle("duplicate_project", req);
        app.toast(r["ok"].boolean(false) ? "Utworzono kopie" : "Blad kopiowania", !r["ok"].boolean(false));
        app.refreshProjects();
      }
      ImGui::SameLine();
      if (ImGui::SmallButton("Usun")) {
        lc::Json req = lc::Json::object();
        req.set("path", p["path"]);
        lc::Json r = app.engine.handle("delete_project", req);
        app.toast(r["ok"].boolean(false) ? "Usunieto projekt" : "Nie udalo sie usunac", !r["ok"].boolean(false));
        app.refreshProjects();
      }
      ImGui::PopID();
      idx++;
    }
  }
  if (idx == 0) ImGui::TextDisabled("Biblioteka jest pusta - zapisz pierwszy projekt.");
  ImGui::EndChild();
}

// ================================ 9. Kalkulator ================================
void uiDrawCalculator(AppState& app) {
  ImGui::Columns(2, "##calccols", true);
  ImGui::SetColumnWidth(0, ImGui::GetContentRegionAvail().x * 0.45f);
  sectionTitle("Dane wejsciowe");
  ImGui::InputFloat("Koszt materialu (zl/m2)", &app.calcMaterialCost, 5, 20, "%.2f");
  ImGui::InputFloat("Powierzchnia projektu (cm2)", &app.calcArea, 10, 50, "%.0f");
  ImGui::InputFloat("Powierzchnia grawerowania (cm2)", &app.calcEngrave, 10, 50, "%.0f");
  ImGui::InputFloat("Dlugosc ciecia (cm)", &app.calcCut, 10, 50, "%.0f");
  ImGui::InputFloat("Moc lasera (W)", &app.calcPower, 1, 5, "%.0f");
  ImGui::InputFloat("Cena energii (zl/kWh)", &app.calcEnergy, 0.05f, 0.2f, "%.2f");
  ImGui::InputFloat("Stawka godzinowa (zl/h)", &app.calcRate, 5, 10, "%.0f");
  ImGui::InputFloat("Predkosc grawerowania (mm/s)", &app.calcEngraveSpeed, 5, 20, "%.0f");
  ImGui::InputFloat("Predkosc ciecia (mm/s)", &app.calcCutSpeed, 1, 5, "%.1f");
  ImGui::InputFloat("Przygotowanie (min)", &app.calcSetup, 0.5f, 2, "%.1f");
  ImGui::InputFloat("Opakowanie (zl)", &app.calcPackaging, 1, 5, "%.2f");
  ImGui::InputFloat("Wysylka (zl)", &app.calcShipping, 1, 5, "%.2f");
  ImGui::InputFloat("Marza (%)", &app.calcMargin, 5, 10, "%.0f");
  ImGui::InputFloat("Odpad (%)", &app.calcWaste, 1, 5, "%.0f");
  if (bigButton("PRZELICZ", ImVec2(-1, 34))) {
    lc::Json req = lc::Json::object();
    req.set("materialCostPerM2", (double)app.calcMaterialCost);
    req.set("areaMm2", (double)app.calcArea * 100.0);
    req.set("engraveAreaMm2", (double)app.calcEngrave * 100.0);
    req.set("cutLengthMm", (double)app.calcCut * 10.0);
    req.set("machinePowerW", (double)app.calcPower);
    req.set("energyPriceKwh", (double)app.calcEnergy);
    req.set("hourlyRate", (double)app.calcRate);
    req.set("engraveSpeedMmS", (double)app.calcEngraveSpeed);
    req.set("cutSpeedMmS", (double)app.calcCutSpeed);
    req.set("setupMinutes", (double)app.calcSetup);
    req.set("packaging", (double)app.calcPackaging);
    req.set("shipping", (double)app.calcShipping);
    req.set("marginPercent", (double)app.calcMargin);
    req.set("wastePercent", (double)app.calcWaste);
    app.calcResult = app.engine.handle("calculator", req);
  }
  ImGui::NextColumn();
  sectionTitle("Wynik");
  if (app.calcResult.size() > 0) {
    auto money = [](const char* label, const lc::Json& j, const char* key) {
      ImGui::Text("%-28s %.2f zl", label, j[key].num(0));
    };
    money("Material:", app.calcResult, "materialCost");
    money("Energia:", app.calcResult, "energyCost");
    money("Robocizna:", app.calcResult, "laborCost");
    money("Zuzycie maszyny:", app.calcResult, "wearCost");
    ImGui::Separator();
    money("Koszt calkowity:", app.calcResult, "totalCost");
    money("Cena netto:", app.calcResult, "price");
    money("Cena brutto (23% VAT):", app.calcResult, "vatPrice");
    money("Zysk:", app.calcResult, "profit");
    ImGui::Separator();
    ImGui::Text("Grawer: %.1f min   Ciecie: %.1f min   Razem maszyna: %.1f min",
                app.calcResult["engraveMinutes"].num(0), app.calcResult["cutMinutes"].num(0),
                app.calcResult["machineMinutes"].num(0));
    if (app.calcResult["estimateOnly"].boolean(true))
      ImGui::TextColored(ImVec4(1, 0.75f, 0.3f, 1),
                         "Wartosci orientacyjne - czas pracy zalezy od realnych parametrow maszyny.");
  } else {
    ImGui::TextDisabled("Kliknij PRZELICZ, aby zobaczyc koszt i cene.");
  }
  ImGui::Columns(1);
}

// ================================ 10. Eksport ================================
void uiDrawExport(AppState& app) {
  if (!app.hasDesign) {
    ImGui::TextDisabled("Brak projektu do eksportu.");
    return;
  }
  sectionTitle("Walidacja przed laserem");
  if (ImGui::Button("SPRAWDŹ PRZED LASEREM (F8)", ImVec2(280, 34))) {
    lc::Json req = lc::Json::object();
    req.set("design", app.design.toJson());
    lc::Json r = app.engine.handle("validate", req);
    snprintf(gValidateInfo, sizeof gValidateInfo, "%s", r["text"].str().c_str());
    app.toast(lc::sfmt("Walidacja: %d bledow, %d ostrzezen", r["errors"].integer(0), r["warnings"].integer(0)),
              r["errors"].integer(0) > 0);
  }
  ImGui::SameLine();
  if (ImGui::Button("Napraw automatycznie", ImVec2(200, 34))) {
    lc::Json req = lc::Json::object();
    req.set("design", app.design.toJson());
    lc::Json r = app.engine.handle("fix", req);
    if (r["ok"].boolean(false) && r.has("design")) {
      app.setDesign(r["design"], "Automatyczne poprawki");
      app.toast("Zastosowano bezpieczne poprawki.", false);
    } else {
      app.toast("Brak poprawek do zastosowania.", false);
    }
  }
  ImGui::BeginChild("##valinfo", ImVec2(0, 160), true);
  ImGui::TextUnformatted(gValidateInfo[0] ? gValidateInfo
                                          : "Kliknij SPRAWDŹ PRZED LASEREM, aby zobaczyc raport.");
  ImGui::EndChild();

  sectionTitle("Eksport plikow");
  static const char* formats[] = {"SVG (LightBurn)", "SVG (LaserGRBL)", "DXF", "PNG", "SVG warstwowy",
                                  "G-code (GRBL)", "Paczka produkcyjna (ZIP)"};
  ImGui::Combo("Format", &gExportFormat, formats, 7);
  ImGui::TextDisabled("Pole robocze maszyny: %.0f x %.0f mm (profil: %s)", app.design.machine.fieldW,
                      app.design.machine.fieldH, app.design.machine.name.c_str());
  if (ImGui::Button("EKSPORTUJ...", ImVec2(220, 34))) {
    lc::Json req = lc::Json::object();
    req.set("design", app.design.toJson());
    if (gExportFormat == 6) {
      req.set("dir", app.exportDir);
      lc::Json r = app.engine.handle("export_package", req);
      if (r["ok"].boolean(false)) {
        auto bytes = lc::base64Decode(r["zipBase64"].str());
        std::string out = app.exportDir + "\\" + r["zipName"].str("paczka.zip");
        if (lc::writeFileBytes(out, bytes)) {
          gExportInfo = "Paczka produkcyjna: " + out + " (" + std::to_string(r["files"].size()) + " plikow)";
          app.toast("Zapisano paczke: " + out, false);
          gPackageResult = r;
        }
      } else {
        app.toast(r.has("error") ? r["error"].str() : "Blad paczki", true);
      }
    } else if (gExportFormat == 5) {
      lc::Json r = app.engine.handle("export_gcode", req);
      if (r["ok"].boolean(false)) {
        std::string p = saveFileDialog(L"G-code (*.nc;*.gcode)\0*.nc;*.gcode\0", "Zapisz G-code",
                                       (app.design.name + ".nc").c_str());
        if (!p.empty() && lc::writeFile(p, r["gcode"].str())) {
          gExportInfo = "G-code: " + p;
          app.toast("Zapisano G-code", false);
        }
      } else {
        app.toast(r.has("error") ? r["error"].str() : "Eksport G-code zablokowany", true);
      }
    } else if (gExportFormat == 2) {
      lc::Json r = app.engine.handle("export_dxf", req);
      std::string p = saveFileDialog(L"DXF (*.dxf)\0*.dxf\0", "Zapisz DXF", (app.design.name + ".dxf").c_str());
      if (!p.empty() && lc::writeFile(p, r["dxf"].str())) {
        gExportInfo = "DXF: " + p;
        app.toast("Zapisano DXF", false);
      }
    } else if (gExportFormat == 3) {
      lc::Json r = app.engine.handle("render", req);
      std::string p = saveFileDialog(L"PNG (*.png)\0*.png\0", "Zapisz PNG", (app.design.name + ".png").c_str());
      if (!p.empty() && lc::writeFileBytes(p, lc::base64Decode(r["pngBase64"].str()))) {
        gExportInfo = "PNG: " + p;
        app.toast("Zapisano PNG", false);
      }
    } else {
      if (gExportFormat == 4) req.set("layer", std::string("CUT_OUTER"));
      lc::Json r = app.engine.handle("export_svg", req);
      std::string p = saveFileDialog(L"SVG (*.svg)\0*.svg\0", "Zapisz SVG", (app.design.name + ".svg").c_str());
      if (!p.empty() && lc::writeFile(p, r["svg"].str())) {
        gExportInfo = "SVG: " + p;
        app.toast("Zapisano SVG", false);
      }
    }
  }
  ImGui::SameLine();
  if (ImGui::Button("Paczka produkcyjna do katalogu eksportu", ImVec2(340, 34))) {
    lc::Json req = lc::Json::object();
    req.set("design", app.design.toJson());
    req.set("dir", app.exportDir);
    lc::Json r = app.engine.handle("export_package", req);
    if (r["ok"].boolean(false)) {
      gExportInfo = "Paczka: " + r["dir"].str();
      app.toast("Paczka produkcyjna zapisana: " + r["dir"].str(), false);
      gPackageResult = r;
    } else {
      app.toast(r.has("error") ? r["error"].str() : "Blad paczki", true);
    }
  }
  if (!gExportInfo.empty()) ImGui::TextWrapped("%s", gExportInfo.c_str());
  if (gPackageResult.has("files")) {
    ImGui::BeginChild("##pkgfiles", ImVec2(0, 130), true);
    for (auto& f : gPackageResult["files"].items()) ImGui::BulletText("%s", f.str().c_str());
    ImGui::EndChild();
  }
  sectionTitle("Podpowiedzi zgodnosci");
  ImGui::BulletText("LightBurn: SVG w mm, warstwy jako grupy (CUT_OUTER = czerwony).");
  ImGui::BulletText("LaserGRBL: importuj PNG (1 px = 0,1 mm) lub SVG; G-code tylko dla GRBL.");
  ImGui::BulletText("DXF: LWPOLYLINE, jednostki mm, bez rastra (fotograwer -> PNG).");
  ImGui::BulletText("G-code nie jest generowany dla nieznanego kontrolera - uzyj SVG/DXF.");
}

// ================================ 11. Diagnostyka AI ================================
void uiDrawDiagnostics(AppState& app) {
  if (app.engineStatus.size() == 0) app.engineStatus = app.engine.status();
  sectionTitle("Silnik");
  ImGui::Text("Aplikacja: %s %s (build %d)", app.engineStatus["app"].str().c_str(),
              app.engineStatus["version"].str().c_str(), app.engineStatus["build"].integer(0));
  ImGui::Text("Silnik: %s", app.engineStatus["engine"].str().c_str());
  ImGui::Text("Tryb AI: %s", app.engineStatus["ai"].str().c_str());
  ImGui::Text("Offline: %s | chmura: %s | klucze API: %s",
              app.engineStatus["offline"].boolean(false) ? "TAK" : "nie",
              app.engineStatus["cloud"].boolean(false) ? "TAK" : "nie",
              app.engineStatus["apiKeys"].boolean(false) ? "TAK" : "nie");
  ImGui::Text("Katalog danych: %s", app.dataDir.c_str());
  ImGui::Text("Fonty: %d | emblematy: %d", app.engineStatus["fonts"].integer(0),
              app.engineStatus["emblems"].integer(0));
  sectionTitle("Moduly lokalne");
  for (auto& m : app.engineStatus["modules"].items()) ImGui::BulletText("%s", m.str().c_str());
  if (ImGui::Button("Test silnika (walidacja + eksporty + QR)", ImVec2(380, 32))) {
    std::string out;
    if (app.hasDesign) {
      lc::Json req = lc::Json::object();
      req.set("design", app.design.toJson());
      lc::Json v = app.engine.handle("validate", req);
      std::string svg = app.engine.handle("export_svg", req)["svg"].str();
      std::string dxf = app.engine.handle("export_dxf", req)["dxf"].str();
      lc::Json qreq = lc::Json::object();
      qreq.set("data", std::string("https://wojan.pl/test"));
      qreq.set("size", 40.0);
      lc::Json q = app.engine.handle("qr", qreq);
      out = lc::sfmt("Walidacja: %d bledow, %d ostrzezen\nSVG: %d B\nDXF: %d B\nQR: %s | round-trip: %s\n",
                     v["errors"].integer(0), v["warnings"].integer(0), (int)svg.size(), (int)dxf.size(),
                     q["selfCheck"].str().c_str(), q["roundTrip"].boolean(false) ? "OK" : "BLAD");
    } else {
      out = "Brak projektu - wygeneruj projekt, aby wykonac pelny test.";
    }
    app.diagText = out;
  }
  if (!app.diagText.empty()) {
    ImGui::BeginChild("##diag", ImVec2(0, 110), true);
    ImGui::TextUnformatted(app.diagText.c_str());
    ImGui::EndChild();
  }
  sectionTitle("Biblioteka trendow (offline, ze zrodlem i data)");
  if (ImGui::Button("Pokaz trendy", ImVec2(180, 28))) {
    lc::Json req = lc::Json::object();
    gTrends = app.engine.handle("trends", req);
  }
  ImGui::BeginChild("##trends", ImVec2(0, 210), true);
  if (gTrends.has("trends")) {
    for (auto& t : gTrends["trends"].items()) {
      ImGui::TextColored(ImVec4(1, 0.75f, 0.3f, 1), "%s (%s, %s)", t["title"].str().c_str(),
                         t["season"].str().c_str(), t["date"].str().c_str());
      ImGui::TextWrapped("%s", t["idea"].str().c_str());
      ImGui::TextDisabled("Zrodlo: %s", t["source"].str().c_str());
      ImGui::Separator();
    }
  } else {
    ImGui::TextDisabled("Kliknij 'Pokaz trendy'. Dane pochodza z wbudowanej biblioteki (bez sieci).");
  }
  ImGui::EndChild();
}

// ================================ 12. Ustawienia ================================
void uiDrawSettings(AppState& app) {
  ImGui::Columns(2, "##setcols", true);
  sectionTitle("Katalogi");
  static char dataBuf[512] = {0};
  if (dataBuf[0] == 0) snprintf(dataBuf, sizeof dataBuf, "%s", app.dataDir.c_str());
  ImGui::InputText("Katalog danych", dataBuf, sizeof dataBuf, ImGuiInputTextFlags_ReadOnly);
  ImGui::SameLine();
  if (ImGui::Button("Zmien...")) {
    std::string p = pickFolderDialog();
    if (!p.empty()) {
      app.dataDir = p;
      app.engine.setDataDir(p);
      snprintf(dataBuf, sizeof dataBuf, "%s", p.c_str());
      app.toast("Katalog danych: " + p, false);
    }
  }
  static char expBuf[512] = {0};
  if (expBuf[0] == 0) snprintf(expBuf, sizeof expBuf, "%s", app.exportDir.c_str());
  ImGui::InputText("Katalog eksportu", expBuf, sizeof expBuf, ImGuiInputTextFlags_ReadOnly);
  ImGui::SameLine();
  if (ImGui::Button("Zmien##exp")) {
    std::string p = pickFolderDialog();
    if (!p.empty()) {
      app.exportDir = p;
      snprintf(expBuf, sizeof expBuf, "%s", p.c_str());
    }
  }
  ImGui::TextDisabled("Fonty (wbudowane): %s", app.fontDir.c_str());
  sectionTitle("Maszyna i kerf");
  auto& machines = lc::machineCatalog();
  int midx = 0;
  for (int i = 0; i < (int)machines.size(); i++)
    if (machines[i].id == app.design.machine.id) midx = i;
  if (ImGui::BeginCombo("Profil maszyny", machines[midx].name.c_str())) {
    for (int i = 0; i < (int)machines.size(); i++)
      if (ImGui::Selectable(machines[i].name.c_str(), i == midx)) {
        app.design.machine.id = machines[i].id;
        app.design.machine.name = machines[i].name;
        app.design.machine.fieldW = machines[i].fieldW;
        app.design.machine.fieldH = machines[i].fieldH;
        app.design.machine.kerf = machines[i].kerf;
        app.design.machine.controller = machines[i].controller;
        app.design.machine.lpi = machines[i].lpi;
        app.dirty = true;
      }
    ImGui::EndCombo();
  }
  float fkerf = (float)app.design.machine.kerf;
  if (ImGui::DragFloat("Kerf (mm)", &fkerf, 0.01f, 0.0f, 1.0f, "%.2f")) {
    app.design.machine.kerf = fkerf;
    app.dirty = true;
  }
  ImGui::TextDisabled("Kerf to zmierzona szczelina ciecia - nie jest zgadywana przez AI.");
  sectionTitle("Interfejs");
  ImGui::Checkbox("Automatyczny podglad po zmianie", &app.settingAutoPreview);
  if (ImGui::DragFloat("Domyslne px/mm podgladu", &app.previewPxPerMm, 0.5f, 3, 16, "%.0f"))
    app.previewDirty = true;
  ImGui::NextColumn();
  sectionTitle("Bezpieczenstwo i prywatnosc");
  ImGui::BulletText("Wszystkie dane pozostaja na dysku lokalnym.");
  ImGui::BulletText("Brak kont, logowania i telemetrii.");
  ImGui::BulletText("Import plikow: limity rozmiaru, ochrona przed zip-slip.");
  ImGui::BulletText("Walidacja SVG/DXF/ZIP przed uzyciem w produkcji.");
  sectionTitle("Materialy - ostrzezenia");
  ImGui::BeginChild("##safety2", ImVec2(0, 280), true);
  for (auto& n : lc::safetyNotes()) {
    ImVec4 col = n.level == "niebezpieczny" ? ImVec4(1, 0.35f, 0.3f, 1)
                 : n.level == "uwaga" ? ImVec4(1, 0.8f, 0.3f, 1)
                                      : ImVec4(0.6f, 0.9f, 0.6f, 1);
    ImGui::TextColored(col, "%s", n.material.c_str());
    ImGui::TextWrapped("%s", n.text.c_str());
    ImGui::Separator();
  }
  ImGui::EndChild();
  sectionTitle("O aplikacji");
  ImGui::TextWrapped("WOJAN LaserCraft AI Studio 1.0.0 - lokalny generator wzorow do grawerowania "
                     "i ciecia laserem. Silnik regulowy i proceduralny wbudowany w program, bez chmury, "
                     "bez kluczy API, bez pobierania modeli po instalacji.");
  ImGui::Columns(1);
}
