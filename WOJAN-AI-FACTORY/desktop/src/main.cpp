// WOJAN AI FACTORY — aplikacja desktopowa Windows 11 (DX11 + ImGui).
// Panel: Projekty / Zadania / Logi / Ustawienia / Status. Serwer dla telefonu w tle.
#include "wojan_host.h"
#include <d3d11.h>
#include <tchar.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <cstdio>

#include "imgui.h"
#include "imgui_impl_win32.h"
#include "imgui_impl_dx11.h"

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "dxgi.lib")

static ID3D11Device*           g_device = nullptr;
static ID3D11DeviceContext*    g_ctx = nullptr;
static IDXGISwapChain*         g_swap = nullptr;
static ID3D11RenderTargetView* g_rtv = nullptr;

static void CreateRenderTarget() {
    ID3D11Texture2D* back = nullptr;
    g_swap->GetBuffer(0, IID_PPV_ARGS(&back));
    g_device->CreateRenderTargetView(back, nullptr, &g_rtv);
    back->Release();
}
static void CleanupRenderTarget() {
    if (g_rtv) { g_rtv->Release(); g_rtv = nullptr; }
}
static bool CreateDeviceD3D(HWND h) {
    DXGI_SWAP_CHAIN_DESC sd{};
    sd.BufferCount = 2;
    sd.BufferDesc.Width = 0;
    sd.BufferDesc.Height = 0;
    sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    sd.BufferDesc.RefreshRate.Numerator = 60;
    sd.BufferDesc.RefreshRate.Denominator = 1;
    sd.Flags = DXGI_SWAP_CHAIN_FLAG_ALLOW_MODE_SWITCH;
    sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    sd.OutputWindow = h;
    sd.SampleDesc.Count = 1;
    sd.Windowed = TRUE;
    sd.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;
    UINT flags = 0;
    D3D_FEATURE_LEVEL lvl = D3D_FEATURE_LEVEL_11_0;
    if (D3D11CreateDeviceAndSwapChain(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, flags,
            &lvl, 1, D3D11_SDK_VERSION, &sd, &g_swap, &g_device, nullptr, &g_ctx) != S_OK)
        return false;
    CreateRenderTarget();
    return true;
}
static void CleanupDeviceD3D() {
    CleanupRenderTarget();
    if (g_swap) { g_swap->Release(); g_swap = nullptr; }
    if (g_ctx) { g_ctx->Release(); g_ctx = nullptr; }
    if (g_device) { g_device->Release(); g_device = nullptr; }
}

extern IMGUI_IMPL_API LRESULT ImGui_ImplWin32_WndProcHandler(HWND, UINT, WPARAM, LPARAM);
static LRESULT WINAPI WndProc(HWND h, UINT msg, WPARAM wp, LPARAM lp) {
    if (ImGui_ImplWin32_WndProcHandler(h, msg, wp, lp)) return true;
    switch (msg) {
        case WM_SIZE:
            if (g_device && wp != SIZE_MINIMIZED) {
                CleanupRenderTarget();
                g_swap->ResizeBuffers(0, (UINT)LOWORD(lp), (UINT)HIWORD(lp), DXGI_FORMAT_UNKNOWN, 0);
                CreateRenderTarget();
            }
            return 0;
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProc(h, msg, wp, lp);
}

// ---- stan aplikacji ----
struct AppState {
    std::string engineDir;
    std::string configPath;
    std::string token;
    unsigned short apiPort = 8765;
    char taskBuf[2048] = "";
    char projName[256] = "Nowy projekt";
    int modeIdx = 2; // AUTO
    std::vector<std::string> logLines;
    std::string projectsJson = "{\"projects\":[]}";
    std::string statusJson = "{}";
    std::string lastError;
    bool busy = false;
};
static AppState g_app;

static const char* MODES[] = { "LOCAL ONLY", "FREE FIRST", "AUTO", "CODING", "MANUAL" };

static void app_log(const std::string& s) {
    g_app.logLines.push_back(s);
    if (g_app.logLines.size() > 5000) g_app.logLines.erase(g_app.logLines.begin());
}

static std::string api_call(const char* method, const char* path, const std::string& body) {
    std::string headers = "{\"Authorization\":\"Bearer " + g_app.token + "\"}";
    HttpResponse r = qjs_handle_request(method, path, body, headers);
    if (r.status >= 400) {
        g_app.lastError = "HTTP " + std::to_string(r.status) + ": " + r.body;
        app_log("[HTTP " + std::to_string(r.status) + "] " + path);
    }
    return r.body;
}

static void refresh_projects() {
    g_app.projectsJson = api_call("GET", "/api/v1/projects", "");
    g_app.statusJson = api_call("GET", "/api/v1/status", "");
}

static std::string json_escape(const std::string& s) {
    std::string o;
    for (char c : s) {
        switch (c) {
            case '"': o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\n': o += "\\n"; break;
            case '\r': o += "\\r"; break;
            case '\t': o += "\\t"; break;
            default:
                if ((unsigned char)c < 0x20) {
                    char b[8];
                    snprintf(b, sizeof(b), "\\u%04x", c);
                    o += b;
                } else o += c;
        }
    }
    return o;
}

// proste wyświetlanie listy projektów z JSON-a
static void draw_projects_list(const std::string& json) {
    size_t pos = 0;
    int idx = 0;
    while ((pos = json.find("\"id\"", pos)) != std::string::npos) {
        auto get = [&](const char* key, size_t from) -> std::string {
            size_t p = json.find(std::string("\"") + key + "\"", from);
            if (p == std::string::npos || p > from + 400) return "";
            size_t c = json.find(':', p);
            size_t q1 = json.find('"', c);
            size_t q2 = json.find('"', q1 + 1);
            return q1 == std::string::npos ? "" : json.substr(q1 + 1, q2 - q1 - 1);
        };
        std::string id = get("id", pos);
        std::string name = get("name", pos);
        std::string status = get("status", pos);
        std::string phase = get("phase", pos);
        ImGui::Text("[%d] %s", idx + 1, name.c_str());
        ImGui::SameLine(220);
        ImGui::TextColored(ImVec4(1.0f, 0.85f, 0.2f, 1.0f), "%s", phase.c_str());
        ImGui::SameLine(380);
        ImGui::TextDisabled("%s  (%s)", status.c_str(), id.c_str());
        pos += 4;
        idx++;
    }
    if (idx == 0) ImGui::TextDisabled("Brak projektów — utwórz zadanie poniżej.");
}

int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE, PWSTR cmdLine, int) {
    // katalog aplikacji
    char exePath[MAX_PATH];
    GetModuleFileNameA(nullptr, exePath, MAX_PATH);
    std::string exeDir = exePath;
    exeDir = exeDir.substr(0, exeDir.find_last_of("\\/"));
    g_app.engineDir = exeDir + "\\engine";
    g_app.configPath = exeDir + "\\wojan-config.json";

    // konfiguracja (token, port, providerzy)
    std::string configJson = "{}";
    {
        bool ok = false;
        std::string raw = wojs_read_file(g_app.configPath, &ok);
        if (ok) configJson = raw;
        // token/port z configu (prosty odczyt)
        size_t t = configJson.find("\"token\"");
        if (t != std::string::npos) {
            size_t q1 = configJson.find('"', configJson.find(':', t));
            size_t q2 = configJson.find('"', q1 + 1);
            g_app.token = configJson.substr(q1 + 1, q2 - q1 - 1);
        }
        size_t p = configJson.find("\"apiPort\"");
        if (p != std::string::npos) {
            g_app.apiPort = (unsigned short)atoi(configJson.c_str() + configJson.find(':', p) + 1);
        }
    }
    if (g_app.token.empty()) {
        // wygeneruj token przy pierwszym starcie
        char hex[33];
        srand((unsigned)GetTickCount64());
        for (int i = 0; i < 32; i++) sprintf(hex + i, "%01x", rand() % 16);
        g_app.token = hex;
        configJson = configJson.substr(0, configJson.size() > 1 ? configJson.size() - 1 : 1);
        if (configJson.empty() || configJson[0] != '{') configJson = "{";
        configJson += ",\"token\":\"" + g_app.token + "\",\"apiPort\":" + std::to_string(g_app.apiPort) + "}";
        wojs_write_file(g_app.configPath, configJson);
    }
    // store + praca pod katalogiem danych użytkownika
    std::string dataDir = exeDir + "\\data";
    CreateDirectoryA(dataDir.c_str(), nullptr);
    {
        // dodaj storePath do configu silnika (bez zapisu — runtime)
        std::string extra = ",\"storePath\":\"" + json_escape(dataDir + "\\store.json") + "\",\"workRoot\":\"" + json_escape(dataDir + "\\projects") + "\",\"token\":\"" + json_escape(g_app.token) + "\"";
        if (!configJson.empty() && configJson.back() == '}') {
            configJson = configJson.substr(0, configJson.size() - 1) + extra + "}";
        } else configJson = "{" + extra.substr(1) + "}";
    }

    // okno
    WNDCLASSEXA wc = { sizeof(wc), CS_CLASSDC, WndProc, 0, 0, hInst, nullptr, nullptr, nullptr, nullptr, "WOJAN_AI_FACTORY", nullptr };
    RegisterClassExA(&wc);
    HWND hwnd = CreateWindowA(wc.lpszClassName, "WOJAN AI FACTORY", WS_OVERLAPPEDWINDOW,
        100, 100, 1180, 720, nullptr, nullptr, wc.hInstance, nullptr);
    if (!CreateDeviceD3D(hwnd)) {
        CleanupDeviceD3D();
        UnregisterClassA(wc.lpszClassName, wc.hInstance);
        MessageBoxA(nullptr, "Błąd inicjalizacji DirectX 11.", "WOJAN AI FACTORY", MB_ICONERROR);
        return 1;
    }
    ShowWindow(hwnd, SW_SHOWDEFAULT);
    UpdateWindow(hwnd);

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.ConfigFlags |= ImGuiConfigFlags_NavEnableKeyboard;
    io.Fonts->AddFontDefault();
    ImGui::StyleColorsDark();
    ImGui_ImplWin32_Init(hwnd);
    ImGui_ImplDX11_Init(g_device, g_ctx);

    // silnik JS
    std::string err;
    if (!qjs_init(g_app.engineDir, configJson, &err)) {
        MessageBoxA(nullptr, err.c_str(), "WOJAN AI FACTORY — błąd silnika", MB_ICONERROR);
        return 1;
    }
    // serwer dla telefonu
    if (!httpd_start(g_app.apiPort, g_app.token, &err)) {
        app_log("UWAGA: serwer API nie wystartował: " + err);
    } else {
        app_log("Serwer API nasłuchuje na porcie " + std::to_string(g_app.apiPort) + " (token w ustawieniach)");
    }
    refresh_projects();
    app_log("WOJAN AI FACTORY " + qjs_version() + " — gotowy.");

    bool done = false;
    while (!done) {
        MSG msg;
        while (PeekMessage(&msg, nullptr, 0U, 0U, PM_REMOVE)) {
            TranslateMessage(&msg);
            DispatchMessage(&msg);
            if (msg.message == WM_QUIT) done = true;
        }
        if (done) break;

        qjs_pump();

        ImGui_ImplDX11_NewFrame();
        ImGui_ImplWin32_NewFrame();
        ImGui::NewFrame();

        ImGui::SetNextWindowPos(ImVec2(0, 0));
        ImGui::SetNextWindowSize(ImVec2(io.DisplaySize.x, io.DisplaySize.y));
        ImGui::Begin("WOJAN AI FACTORY", nullptr,
            ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize | ImGuiWindowFlags_NoMove);

        ImGui::TextColored(ImVec4(1.0f, 0.75f, 0.1f, 1.0f), "WOJAN AI FACTORY");
        ImGui::SameLine();
        ImGui::TextDisabled("v%s | port API: %d | tryb: %s", qjs_version().c_str(), g_app.apiPort, MODES[g_app.modeIdx]);
        ImGui::Separator();

        if (ImGui::BeginTabBar("main")) {
            if (ImGui::BeginTabItem("Projekty")) {
                if (ImGui::Button("Odśwież")) refresh_projects();
                ImGui::SameLine();
                if (ImGui::Button("Nowe zadanie")) {
                    ImGui::OpenPopup("nowe_zadanie");
                }
                ImGui::Separator();
                draw_projects_list(g_app.projectsJson);

                if (ImGui::BeginPopupModal("nowe_zadanie", nullptr, ImGuiWindowFlags_AlwaysAutoResize)) {
                    ImGui::InputText("Nazwa projektu", g_app.projName, sizeof(g_app.projName));
                    ImGui::InputTextMultiline("Opis zadania (ZADANIE)", g_app.taskBuf, sizeof(g_app.taskBuf),
                        ImVec2(560, 160));
                    if (ImGui::Button("Uruchom") && g_app.taskBuf[0]) {
                        std::string body = std::string("{\"name\":\"") + json_escape(g_app.projName) +
                            "\",\"task\":\"" + json_escape(g_app.taskBuf) + "\"}";
                        g_app.busy = true;
                        app_log(">>> ZADANIE: " + std::string(g_app.taskBuf).substr(0, 120));
                        // utwórz projekt, potem wyślij zadanie (pipeline startuje od razu)
                        std::string created = api_call("POST", "/api/v1/projects", body);
                        std::string pid;
                        size_t p = created.find("\"id\"");
                        if (p != std::string::npos) {
                            size_t q1 = created.find('"', created.find(':', p));
                            size_t q2 = created.find('"', q1 + 1);
                            pid = created.substr(q1 + 1, q2 - q1 - 1);
                        }
                        if (!pid.empty()) {
                            app_log("Projekt " + pid + " — pipeline ZADANIE→…→EXE/APK rozpoczęty.");
                            std::string r = api_call("POST", ("/api/v1/projects/" + pid + "/tasks").c_str(),
                                "{\"task\":\"" + json_escape(g_app.taskBuf) + "\"}");
                            app_log("Wynik: " + r.substr(0, 300));
                        }
                        g_app.busy = false;
                        refresh_projects();
                        ImGui::CloseCurrentPopup();
                    }
                    ImGui::SameLine();
                    if (ImGui::Button("Anuluj")) ImGui::CloseCurrentPopup();
                    ImGui::EndPopup();
                }
                ImGui::EndTabItem();
            }
            if (ImGui::BeginTabItem("Logi")) {
                if (ImGui::Button("Wyczyść")) g_app.logLines.clear();
                ImGui::BeginChild("logscroll", ImVec2(0, 0), false, ImGuiWindowFlags_HorizontalScrollbar);
                for (auto& l : g_app.logLines) ImGui::TextUnformatted(l.c_str());
                if (ImGui::GetScrollY() >= ImGui::GetScrollMaxY() - 4)
                    ImGui::SetScrollHereY(1.0f);
                ImGui::EndChild();
                ImGui::EndTabItem();
            }
            if (ImGui::BeginTabItem("Ustawienia")) {
                if (ImGui::Combo("Tryb pracy", &g_app.modeIdx, MODES, IM_ARRAYSIZE(MODES))) {
                    std::string mode = MODES[g_app.modeIdx];
                    for (auto& c : mode) if (c == ' ') c = '_';   // LOCAL ONLY -> LOCAL_ONLY
                    std::string body = "{\"mode\":\"" + mode + "\"}";
                    std::string r = api_call("POST", "/api/v1/mode", body);
                    app_log("Tryb pracy: " + std::string(MODES[g_app.modeIdx]) + " -> " + r);
                }
                ImGui::Text("Token API dla telefonu:");
                ImGui::InputText("##token", (char*)g_app.token.c_str(), 0, ImGuiInputTextFlags_ReadOnly);
                ImGui::TextWrapped("Konfiguracja providerów: plik wojan-config.json obok programu "
                                   "(ollama, omniroute, openrouter, openai, gemini). "
                                   "Klucze API trzymaj TYLKO lokalnie — nie udostępniaj repozytorium.");
                ImGui::TextDisabled("Serwer: 0.0.0.0:%d — wystawiony na LAN; używaj silnego tokena "
                                    "i zapory systemowej (tylko Twoja sieć).", g_app.apiPort);
                ImGui::EndTabItem();
            }
            if (ImGui::BeginTabItem("Status")) {
                ImGui::TextWrapped("Produkt: WOJAN AI FACTORY — fabryka aplikacji AI.");
                ImGui::Text("Status silnika:");
                ImGui::TextWrapped("%s", g_app.statusJson.c_str());
                if (!g_app.lastError.empty()) {
                    ImGui::TextColored(ImVec4(1, 0.4f, 0.4f, 1), "Ostatni błąd:");
                    ImGui::TextWrapped("%s", g_app.lastError.c_str());
                }
                ImGui::Separator();
                ImGui::TextWrapped("Telefon (Android) łączy się z tym komputerem przez IP LAN i port %d, "
                                   "używając tokena z ustawień. Kompilacja EXE/APK odbywa się NA TYM KOMPUTERZE.",
                                   g_app.apiPort);
                ImGui::EndTabItem();
            }
            ImGui::EndTabBar();
        }
        ImGui::End();

        ImGui::Render();
        const float clear[4] = { 0.08f, 0.08f, 0.10f, 1.0f };
        g_ctx->OMSetRenderTargets(1, &g_rtv, nullptr);
        g_ctx->ClearRenderTargetView(g_rtv, clear);
        ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());
        g_swap->Present(1, 0);
    }

    httpd_stop();
    qjs_shutdown();
    ImGui_ImplDX11_Shutdown();
    ImGui_ImplWin32_Shutdown();
    ImGui::DestroyContext();
    CleanupDeviceD3D();
    DestroyWindow(hwnd);
    UnregisterClassA(wc.lpszClassName, wc.hInstance);
    return 0;
}
