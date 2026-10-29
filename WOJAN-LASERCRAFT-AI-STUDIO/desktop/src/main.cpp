// WOJAN LaserCraft AI Studio — aplikacja desktopowa Windows 11 (Win32 + Direct3D 11 + ImGui)
// Cały silnik AI działa lokalnie (biblioteka libwojan), brak połączeń sieciowych.
#include <windows.h>
#include <commdlg.h>
#include <shlobj.h>
#include <mmsystem.h>
#include <d3d11.h>
#include <thread>
#include "imgui.h"
#include "imgui_impl_win32.h"
#include "imgui_impl_dx11.h"
#include "app.h"
#include "raster.h"
#include "common.h"

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "winmm.lib")

static AppState gApp;
static ID3D11Device* gDevice = nullptr;
static ID3D11DeviceContext* gContext = nullptr;
static ID3D11RenderTargetView* gRTV = nullptr;
static ID3D11ShaderResourceView* gLogoSrv = nullptr;
static bool gRunning = true;
static int gActiveTab = 0;

extern IMGUI_IMPL_API LRESULT ImGui_ImplWin32_WndProcHandler(HWND, UINT, WPARAM, LPARAM);
static LRESULT WINAPI WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam);

// ------------------------------- tekstury -------------------------------
void GpuTex::release() {
  if (srv) srv->Release();
  srv = nullptr;
  w = h = 0;
}

GpuTex makeTexture(ID3D11Device* dev, const std::vector<uint8_t>& png) {
  GpuTex t;
  if (!dev || png.empty()) return t;
  lc::Image img;
  if (!lc::pngDecode(png, img)) return t;
  lc::Image gray = img.ch == 1 ? img : img.toGray();
  std::vector<uint8_t> rgba((size_t)gray.w * gray.h * 4);
  for (int i = 0; i < gray.w * gray.h; i++) {
    uint8_t v = gray.px[i];
    rgba[i * 4 + 0] = v;
    rgba[i * 4 + 1] = v;
    rgba[i * 4 + 2] = v;
    rgba[i * 4 + 3] = 255;
  }
  D3D11_TEXTURE2D_DESC desc{};
  desc.Width = gray.w;
  desc.Height = gray.h;
  desc.MipLevels = 1;
  desc.ArraySize = 1;
  desc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
  desc.SampleDesc.Count = 1;
  desc.Usage = D3D11_USAGE_DEFAULT;
  desc.BindFlags = D3D11_BIND_SHADER_RESOURCE;
  D3D11_SUBRESOURCE_DATA sd{};
  sd.pSysMem = rgba.data();
  sd.SysMemPitch = gray.w * 4;
  ID3D11Texture2D* tex = nullptr;
  if (FAILED(dev->CreateTexture2D(&desc, &sd, &tex)) || !tex) return t;
  D3D11_SHADER_RESOURCE_VIEW_DESC srvDesc{};
  srvDesc.Format = desc.Format;
  srvDesc.ViewDimension = D3D11_SRV_DIMENSION_TEXTURE2D;
  srvDesc.Texture2D.MipLevels = 1;
  dev->CreateShaderResourceView(tex, &srvDesc, &t.srv);
  tex->Release();
  t.w = gray.w;
  t.h = gray.h;
  return t;
}

// ------------------------------- dialogi -------------------------------
static std::string wideToUtf8(const std::wstring& w) {
  if (w.empty()) return "";
  int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), nullptr, 0, nullptr, nullptr);
  std::string s(n, 0);
  WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int)w.size(), &s[0], n, nullptr, nullptr);
  return s;
}
static std::wstring utf8ToWide(const std::string& s) {
  if (s.empty()) return L"";
  int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), nullptr, 0);
  std::wstring w(n, 0);
  MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int)s.size(), (LPWSTR)w.data(), n);
  return w;
}

std::string openFileDialog(const wchar_t* filter, const char* title) {
  wchar_t buf[MAX_PATH] = {0};
  OPENFILENAMEW ofn{};
  ofn.lStructSize = sizeof ofn;
  ofn.hwndOwner = GetActiveWindow();
  ofn.lpstrFilter = filter;
  ofn.lpstrFile = buf;
  ofn.nMaxFile = MAX_PATH;
  std::wstring wtitle = utf8ToWide(title);
  ofn.lpstrTitle = wtitle.c_str();
  ofn.Flags = OFN_FILEMUSTEXIST | OFN_PATHMUSTEXIST | OFN_NOCHANGEDIR;
  if (!GetOpenFileNameW(&ofn)) return "";
  return wideToUtf8(buf);
}

std::string saveFileDialog(const wchar_t* filter, const char* title, const char* defaultName) {
  wchar_t buf[MAX_PATH] = {0};
  std::wstring def = utf8ToWide(defaultName ? defaultName : "projekt");
  wcsncpy(buf, def.c_str(), def.size() < MAX_PATH ? def.size() : MAX_PATH - 1);
  OPENFILENAMEW ofn{};
  ofn.lStructSize = sizeof ofn;
  ofn.hwndOwner = GetActiveWindow();
  ofn.lpstrFilter = filter;
  ofn.lpstrFile = buf;
  ofn.nMaxFile = MAX_PATH;
  std::wstring wtitle = utf8ToWide(title);
  ofn.lpstrTitle = wtitle.c_str();
  ofn.Flags = OFN_OVERWRITEPROMPT | OFN_PATHMUSTEXIST | OFN_NOCHANGEDIR;
  if (!GetSaveFileNameW(&ofn)) return "";
  return wideToUtf8(buf);
}

std::string pickFolderDialog() {
  wchar_t buf[MAX_PATH] = {0};
  BROWSEINFOW bi{};
  bi.hwndOwner = GetActiveWindow();
  bi.lpszTitle = L"Wybierz katalog";
  bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE;
  LPITEMIDLIST idl = SHBrowseForFolderW(&bi);
  if (!idl) return "";
  SHGetPathFromIDListW(idl, buf);
  CoTaskMemFree(idl);
  return wideToUtf8(buf);
}

// ------------------------------- mikrofon -------------------------------
namespace {
HWAVEIN gWaveIn = nullptr;
std::vector<short> gPcm;
WAVEHDR gHdr{};
std::vector<short> gChunk;

void CALLBACK waveProc(HWAVEIN, UINT msg, DWORD_PTR, DWORD_PTR p1, DWORD_PTR) {
  if (msg != WIM_DATA) return;
  WAVEHDR* h = (WAVEHDR*)p1;
  size_t n = h->dwBytesRecorded / sizeof(short);
  gPcm.insert(gPcm.end(), gChunk.begin(), gChunk.begin() + n);
  waveInAddBuffer(gWaveIn, h, sizeof(WAVEHDR));
}
}  // namespace

bool startMicRecording(std::string& err) {
  if (gWaveIn) { err = "Nagrywanie już trwa."; return false; }
  WAVEFORMATEX wf{};
  wf.wFormatTag = WAVE_FORMAT_PCM;
  wf.nChannels = 1;
  wf.nSamplesPerSec = 16000;
  wf.wBitsPerSample = 16;
  wf.nBlockAlign = 2;
  wf.nAvgBytesPerSec = 32000;
  MMRESULT r = waveInOpen(&gWaveIn, WAVE_MAPPER, &wf, (DWORD_PTR)waveProc, 0, CALLBACK_FUNCTION);
  if (r != MMSYSERR_NOERROR) {
    gWaveIn = nullptr;
    err = "Brak mikrofonu lub dostęp zablokowany (kod " + std::to_string(r) + ").";
    return false;
  }
  gPcm.clear();
  gChunk.assign(16000, 0);  // 1 s bufora
  gHdr = WAVEHDR{};
  gHdr.lpData = (LPSTR)gChunk.data();
  gHdr.dwBufferLength = (DWORD)(gChunk.size() * sizeof(short));
  waveInPrepareHeader(gWaveIn, &gHdr, sizeof(WAVEHDR));
  waveInAddBuffer(gWaveIn, &gHdr, sizeof(WAVEHDR));
  waveInStart(gWaveIn);
  return true;
}

bool stopMicRecording(const std::string& wavPath, std::string& err) {
  if (!gWaveIn) { err = "Brak aktywnego nagrania."; return false; }
  waveInStop(gWaveIn);
  waveInReset(gWaveIn);
  waveInUnprepareHeader(gWaveIn, &gHdr, sizeof(WAVEHDR));
  waveInClose(gWaveIn);
  gWaveIn = nullptr;
  // zapis WAV 16 kHz mono PCM
  uint32_t dataBytes = (uint32_t)(gPcm.size() * 2);
  std::vector<uint8_t> wav;
  auto put32 = [&](uint32_t v) { for (int i = 0; i < 4; i++) wav.push_back((uint8_t)((v >> (8 * i)) & 0xFF)); };
  auto put16 = [&](uint16_t v) { wav.push_back((uint8_t)(v & 0xFF)); wav.push_back((uint8_t)(v >> 8)); };
  auto putStr = [&](const char* s) { for (int i = 0; i < 4; i++) wav.push_back((uint8_t)s[i]); };
  putStr("RIFF");
  put32(36 + dataBytes);
  putStr("WAVEfmt ");
  put32(16);
  put16(1);
  put16(1);
  put32(16000);
  put32(32000);
  put16(2);
  put16(16);
  putStr("data");
  put32(dataBytes);
  for (short s : gPcm) { wav.push_back((uint8_t)(s & 0xFF)); wav.push_back((uint8_t)((s >> 8) & 0xFF)); }
  if (!lc::writeFileBytes(wavPath, wav)) { err = "Nie można zapisać pliku WAV."; return false; }
  return true;
}

// ------------------------------- AppState -------------------------------
void AppState::toast(const std::string& msg, bool error) {
  Toast t;
  t.text = msg;
  t.error = error;
  t.until = ImGui::GetTime() + 5.0;
  toasts.push_back(t);
  statusLine = msg;
  if (toasts.size() > 6) toasts.erase(toasts.begin());
}

bool AppState::setDesign(const lc::Json& d, const std::string& why) {
  if (d.isNull() || d.size() == 0) {
    toast("Silnik nie zwrócił projektu: " + why, true);
    return false;
  }
  design = lc::Design::fromJson(d);
  hasDesign = true;
  dirty = true;
  previewDirty = true;
  selectedObj = -1;
  selectedNode = -1;
  log.push_back(why);
  if (log.size() > 40) log.erase(log.begin());
  lc::Json j = design.toJson();
  textTopBuf[0] = 0;
  textBottomBuf[0] = 0;
  for (auto& o : design.objects) {
    if (o.type != "text") continue;
    if (o.name == "Napis główny") snprintf(textTopBuf, sizeof textTopBuf, "%s", o.text.c_str());
    if (o.name.rfind("Napis dolny", 0) == 0 && textBottomBuf[0] == 0)
      snprintf(textBottomBuf, sizeof textBottomBuf, "%s", o.text.c_str());
  }
  return true;
}

void AppState::updatePreview() {
  if (!hasDesign || !device) return;
  lc::Json req = lc::Json::object();
  req.set("design", design.toJson());
  req.set("pxPerMm", (double)previewPxPerMm);
  req.set("guides", showGuides);
  lc::Json r = engine.handle("render", req);
  if (!r["ok"].boolean(false) || !r.has("pngBase64")) return;
  auto bytes = lc::base64Decode(r["pngBase64"].str());
  GpuTex t = makeTexture(device, bytes);
  if (t.valid()) {
    preview.release();
    preview = t;
  }
  previewDirty = false;
}

void AppState::newProject() {
  hasDesign = false;
  design = lc::Design();
  projectPath.clear();
  preview.release();
  variants.clear();
  texts.clear();
  chat.clear();
  selectedObj = -1;
  dirty = false;
  previewDirty = true;
  toast("Nowy, pusty projekt.");
}

void AppState::generate(int variantCount) {
  lc::Json req = lc::Json::object();
  req.set("prompt", promptText);
  if (categoryIdx > 0 && categoryIdx < (int)lc::categoryCatalog().size())
    req.set("category", lc::categoryCatalog()[categoryIdx].id);
  if (productIdx >= 0 && productIdx < (int)lc::productCatalog().size())
    req.set("product", lc::productCatalog()[productIdx].id);
  req.set("width", (double)widthMm);
  req.set("height", (double)heightMm);
  if (materialIdx >= 0 && materialIdx < (int)lc::materialCatalog().size())
    req.set("material", lc::materialCatalog()[materialIdx].id);
  static const char* styles[] = {"rycina", "retro", "gotyk", "elegancki", "komiks", "minimal", "fantasy", "nowoczesny"};
  if (styleIdx >= 0 && styleIdx < 8) req.set("style", styles[styleIdx]);
  static const char* details[] = {"niski", "sredni", "wysoki"};
  if (detailIdx >= 0 && detailIdx < 3) req.set("detail", details[detailIdx]);
  if (machineIdx >= 0 && machineIdx < (int)lc::machineCatalog().size())
    req.set("machine", lc::machineCatalog()[machineIdx].id);
  if (cutMode == 1) req.set("requiresCut", true);
  if (cutMode == 2) req.set("requiresCut", false);
  req.set("variants", (double)variantCount);
  lc::Json r = engine.handle(variantCount > 1 ? "variants" : "generate", req);
  if (r.has("error") && !r["error"].str().empty()) {
    toast("Błąd generowania: " + r["error"].str(), true);
    return;
  }
  if (r.has("variants")) {
    variants.clear();
    texts.clear();
    int i = 0;
    for (auto& v : r["variants"].items()) {
      variants.push_back(v);
      lc::Json tl = v.has("texts") ? v["texts"] : lc::Json::array();
      (void)tl;
      i++;
    }
    if (!variants.empty() && variants[0].has("design")) setDesign(variants[0]["design"], "Wygenerowano wariant 1 z " + std::to_string(variants.size()));
    if (r.has("texts")) {
      for (auto& t : r["texts"].items()) texts.push_back(t);
    }
    return;
  }
  if (setDesign(r["design"], "Wygenerowano: " + design.name)) {
    texts.clear();
    if (r.has("texts"))
      for (auto& t : r["texts"].items()) texts.push_back(t);
    variants.clear();
  }
}

void AppState::generateVariants4() { generate(4); }

void AppState::applyEdit(const std::string& cmd) {
  if (!hasDesign) { toast("Najpierw wygeneruj projekt.", true); return; }
  lc::Json req = lc::Json::object();
  req.set("design", design.toJson());
  req.set("command", cmd);
  lc::Json r = engine.handle("edit", req);
  if (!r["ok"].boolean(false)) {
    toast(r.has("message") ? "Nie udało się: " + r["message"].str() : "Nie zrozumiano polecenia.", true);
    chat.push_back("✗ " + cmd);
    return;
  }
  if (r.has("design")) setDesign(r["design"], "Edycja: " + cmd);
  chat.push_back("→ " + cmd + (r.has("message") ? ("  (" + r["message"].str() + ")") : ""));
}

void AppState::openProject(const std::string& path) {
  std::string text = lc::readFile(path);
  if (text.empty()) { toast("Nie można odczytać pliku projektu: " + path, true); return; }
  std::string err;
  lc::Json j = lc::Json::parse(text, &err);
  if (!err.empty()) { toast("Uszkodzony plik projektu: " + err, true); return; }
  if (!setDesign(j, "Otwarto: " + path)) return;
  projectPath = path;
  dirty = false;
}

void AppState::saveProject(const std::string& path) {
  if (!hasDesign) { toast("Brak projektu do zapisania.", true); return; }
  std::string text = design.toJson().dump(1);
  if (!lc::writeFile(path, text)) { toast("Nie można zapisać: " + path, true); return; }
  projectPath = path;
  dirty = false;
  toast("Zapisano projekt: " + path);
}

void AppState::refreshProjects() {
  lc::Json req = lc::Json::object();
  projectList = engine.handle("projects_list", req);
  if (projectList.has("projects") && projectList["projects"].isArray()) selectedProject = -1;
  int idx = 0;
  for (auto& p : projectList["projects"].items()) { (void)p; idx++; }
  if (idx == 0) projectList = lc::Json::object();
}

void AppState::refreshQr() {
  if (!device) return;
  lc::Json req = lc::Json::object();
  req.set("data", qrData);
  static const char* ecc[] = {"L", "M", "Q", "H"};
  req.set("ecc", ecc[std::clamp(qrEccIdx, 0, 3)]);
  req.set("sizeMm", (double)qrSizeMm);
  lc::Json r = engine.handle("qr", req);
  if (r["ok"].boolean(false)) {
    qrCheck = r.has("check") ? r["check"].str() : "QR: OK";
    if (r.has("pngBase64")) {
      GpuTex t = makeTexture(device, lc::base64Decode(r["pngBase64"].str()));
      if (t.valid()) { qrPreview.release(); qrPreview = t; }
    }
  } else {
    qrCheck = r.has("error") ? r["error"].str() : "QR: błąd";
    qrPreview.release();
  }
  qrPreviewDirty = false;
}

// ------------------------------- DX11 -------------------------------
static bool createDevice(HWND hwnd) {
  DXGI_SWAP_CHAIN_DESC sd{};
  sd.BufferCount = 2;
  sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
  sd.BufferDesc.RefreshRate.Numerator = 60;
  sd.BufferDesc.RefreshRate.Denominator = 1;
  sd.Flags = DXGI_SWAP_CHAIN_FLAG_ALLOW_MODE_SWITCH;
  sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
  sd.OutputWindow = hwnd;
  sd.SampleDesc.Count = 1;
  sd.Windowed = TRUE;
  sd.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;
  UINT flags = 0;
  D3D_FEATURE_LEVEL level;
  const D3D_FEATURE_LEVEL levels[] = {D3D_FEATURE_LEVEL_11_0, D3D_FEATURE_LEVEL_10_0};
  IDXGISwapChain* swap = nullptr;
  HRESULT hr = D3D11CreateDeviceAndSwapChain(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, flags, levels, 2,
                                             D3D11_SDK_VERSION, &sd, &swap, &gDevice, &level, &gContext);
  if (FAILED(hr))
    hr = D3D11CreateDeviceAndSwapChain(nullptr, D3D_DRIVER_TYPE_WARP, nullptr, flags, levels, 2,
                                       D3D11_SDK_VERSION, &sd, &swap, &gDevice, &level, &gContext);
  if (FAILED(hr)) return false;
  static IDXGISwapChain* gSwap = nullptr;
  gSwap = swap;
  ID3D11Texture2D* back = nullptr;
  swap->GetBuffer(0, IID_PPV_ARGS(&back));
  if (back) {
    gDevice->CreateRenderTargetView(back, nullptr, &gRTV);
    back->Release();
  }
  extern IDXGISwapChain* gSwapChainRef;
  gSwapChainRef = swap;
  return true;
}

IDXGISwapChain* gSwapChainRef = nullptr;

// ------------------------------- main -------------------------------
int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE, LPSTR, int) {
  SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
  CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
  timeBeginPeriod(1);

  WNDCLASSEXW wc{};
  wc.cbSize = sizeof wc;
  wc.style = CS_OWNDC;
  wc.lpfnWndProc = WndProc;
  wc.hInstance = hInstance;
  wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
  wc.hbrBackground = (HBRUSH)GetStockObject(BLACK_BRUSH);
  wc.lpszClassName = L"WOJANLaserCraft";
  RegisterClassExW(&wc);
  HWND hwnd = CreateWindowW(wc.lpszClassName, L"WOJAN LaserCraft AI Studio — lokalny generator wzorów laserowych",
                            WS_OVERLAPPEDWINDOW, CW_USEDEFAULT, CW_USEDEFAULT, 1500, 950, nullptr, nullptr,
                            hInstance, nullptr);
  if (!hwnd) return 1;
  if (!createDevice(hwnd)) {
    MessageBoxW(hwnd, L"Nie można uruchomić Direct3D 11 — sprawdź sterowniki karty graficznej.", L"Błąd", MB_ICONERROR);
    return 1;
  }

  ShowWindow(hwnd, SW_SHOWMAXIMIZED);
  UpdateWindow(hwnd);

  IMGUI_CHECKVERSION();
  ImGui::CreateContext();
  ImGuiIO& io = ImGui::GetIO();
  io.ConfigFlags |= ImGuiConfigFlags_NavEnableKeyboard;
  io.IniFilename = nullptr;

  // katalog aplikacji + zasoby
  wchar_t exePath[MAX_PATH];
  GetModuleFileNameW(nullptr, exePath, MAX_PATH);
  std::wstring wdir(exePath);
  size_t slash = wdir.find_last_of(L"\\/");
  gApp.exeDir = wideToUtf8(wdir.substr(0, slash));
  gApp.dataDir = gApp.exeDir + "\\data";
  gApp.fontDir = gApp.exeDir + "\\fonts";
  gApp.exportDir = gApp.exeDir + "\\eksport";
  CreateDirectoryA(gApp.dataDir.c_str(), nullptr);
  CreateDirectoryA(gApp.exportDir.c_str(), nullptr);
  gApp.engine.setDataDir(gApp.dataDir);
  gApp.engine.setFontDir(gApp.fontDir);
  gApp.device = gDevice;
  gApp.engineStatus = gApp.engine.status();

  ImGui_ImplWin32_Init(hwnd);
  ImGui_ImplDX11_Init(gDevice, gContext);

  // --- czcionki: polskie znaki diakrytyczne + skalowanie DPI ---
  {
    ImGuiIO& io = ImGui::GetIO();
    io.Fonts->Clear();
    // zakresy: podstawowy łaciński, łaciński rozszerzony A (ą ć ę ł ń ó ś ź ż),
    // typografia (myślniki, cudzysłowy, wielokropek, kropka, stopnie, euro)
    static const ImWchar polishRanges[] = {
        0x0020, 0x00FF,  // Basic Latin + Latin-1 Supplement (ó, °, ×, ą? nie)
        0x0100, 0x017F,  // Latin Extended-A (Ą ą Ć ć Ę ę Ł ł Ń ń Ś ś Ź ź Ż ż)
        0x2010, 0x2027,  // myślniki, cudzysłowy, wielokropek, bullet
        0x2030, 0x203A,
        0x20AC, 0x20AC,  // €
        0x2122, 0x2122,  // ™
        0
    };
    float dpi = 1.0f;
    {
      UINT d = GetDpiForWindow(hwnd);
      if (d >= 96) dpi = (float)d / 96.0f;
    }
    if (dpi > 2.5f) dpi = 2.5f;
    gApp.uiScale = dpi;
    std::string uiFont = gApp.fontDir + "\\Oswald.ttf";
    std::string headFont = gApp.fontDir + "\\BarlowSemiCondensed-Bold.ttf";
    ImFontConfig cfg;
    cfg.OversampleH = 2;
    cfg.OversampleV = 2;
    cfg.PixelSnapH = false;
    ImFont* f = nullptr;
    if (GetFileAttributesA(uiFont.c_str()) != INVALID_FILE_ATTRIBUTES)
      f = io.Fonts->AddFontFromFileTTF(uiFont.c_str(), 16.0f * dpi, &cfg, polishRanges);
    if (!f) f = io.Fonts->AddFontDefault();
    cfg.MergeMode = false;
    if (GetFileAttributesA(headFont.c_str()) != INVALID_FILE_ATTRIBUTES)
      gApp.fontHead = io.Fonts->AddFontFromFileTTF(headFont.c_str(), 21.0f * dpi, &cfg, polishRanges);
    gApp.fontSmall = nullptr;
    if (GetFileAttributesA(uiFont.c_str()) != INVALID_FILE_ATTRIBUTES)
      gApp.fontSmall = io.Fonts->AddFontFromFileTTF(uiFont.c_str(), 13.0f * dpi, &cfg, polishRanges);
    io.FontDefault = f;
    ImGui::GetStyle().ScaleAllSizes(dpi > 1.0f ? (dpi * 0.92f) : 1.0f);
  }
  ImVec4 clear(0.08f, 0.09f, 0.10f, 1.0f);

  while (gRunning) {
    MSG msg;
    while (PeekMessageW(&msg, nullptr, 0, 0, PM_REMOVE)) {
      TranslateMessage(&msg);
      DispatchMessageW(&msg);
      if (msg.message == WM_QUIT) gRunning = false;
    }
    if (!gRunning) break;

    ImGui_ImplDX11_NewFrame();
    ImGui_ImplWin32_NewFrame();
    ImGui::NewFrame();

    char title[256];
    snprintf(title, sizeof title, "WOJAN LaserCraft AI Studio%s — %s",
             gApp.dirty ? " *" : "",
             gApp.hasDesign ? gApp.design.name.c_str() : "nowy projekt");
    SetWindowTextW(hwnd, utf8ToWide(title).c_str());

    static bool wantExit = false;
    uiDrawAll(gApp, gActiveTab, wantExit);
    if (wantExit) {
      if (!gApp.dirty || MessageBoxW(hwnd, L"Niezapisane zmiany. Zamknąć aplikację?", L"Wyjście",
                                     MB_YESNO | MB_ICONQUESTION) == IDYES)
        gRunning = false;
      wantExit = false;
    }

    ImGui::Render();
    if (gRTV) {
      gContext->OMSetRenderTargets(1, &gRTV, nullptr);
      gContext->ClearRenderTargetView(gRTV, (float*)&clear);
    }
    ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());
    if (gSwapChainRef) gSwapChainRef->Present(1, 0);
  }

  ImGui_ImplDX11_Shutdown();
  ImGui_ImplWin32_Shutdown();
  ImGui::DestroyContext();
  gApp.preview.release();
  gApp.qrPreview.release();
  if (gRTV) gRTV->Release();
  if (gContext) gContext->Release();
  if (gDevice) gDevice->Release();
  timeEndPeriod(1);
  CoUninitialize();
  return 0;
}

static LRESULT WINAPI WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
  extern IMGUI_IMPL_API LRESULT ImGui_ImplWin32_WndProcHandler(HWND, UINT, WPARAM, LPARAM);
  if (ImGui_ImplWin32_WndProcHandler(hWnd, msg, wParam, lParam)) return true;
  switch (msg) {
    case WM_SIZE:
      if (gDevice && wParam != SIZE_MINIMIZED && gSwapChainRef) {
        if (gRTV) { gRTV->Release(); gRTV = nullptr; }
        gSwapChainRef->ResizeBuffers(0, (UINT)LOWORD(lParam), (UINT)HIWORD(lParam), DXGI_FORMAT_UNKNOWN, 0);
        ID3D11Texture2D* back = nullptr;
        gSwapChainRef->GetBuffer(0, IID_PPV_ARGS(&back));
        if (back) {
          gDevice->CreateRenderTargetView(back, nullptr, &gRTV);
          back->Release();
        }
      }
      return 0;
    case WM_SYSCOMMAND:
      if ((wParam & 0xfff0) == SC_KEYMENU) return 0;  // bez menu Alt
      break;
    case WM_DROPFILES: {
      HDROP drop = (HDROP)wParam;
      UINT count = DragQueryFileW(drop, 0xFFFFFFFF, nullptr, 0);
      for (UINT i = 0; i < count; i++) {
        wchar_t path[MAX_PATH];
        DragQueryFileW(drop, i, path, MAX_PATH);
        std::string p = wideToUtf8(path);
        if (p.size() > 8 && (p.substr(p.size() - 8) == ".wlcproj")) gApp.openProject(p);
        else gApp.openProject(p);
      }
      DragFinish(drop);
      return 0;
    }
    case WM_DESTROY:
      PostQuitMessage(0);
      return 0;
  }
  return DefWindowProcW(hWnd, msg, wParam, lParam);
}
