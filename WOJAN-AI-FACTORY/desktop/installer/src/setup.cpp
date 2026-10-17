// WOJAN AI FACTORY — instalator Windows (Setup.exe) + deinstalator (/uninstall).
// Payload: archiwum doklejone do Setup.exe (patrz build_installer.py):
//   [u32 count] powtarza: [u16 name_len][name][u32 size][data] ... [u32 MAGIC 'WJAN']
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shlobj.h>
#include <shobjidl.h>
#include <string>
#include <vector>
#include <fstream>
#include <cstdio>

#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "comdlg32.lib")
#pragma comment(lib, "advapi32.lib")

static const char* APP_NAME = "WOJAN AI FACTORY";
static const char* DEFAULT_DIR = "C:\\Program Files\\WOJAN AI FACTORY";
static char g_dir[MAX_PATH] = "C:\\Program Files\\WOJAN AI FACTORY";
static bool g_shortcut = true;

struct Entry { std::string name; std::vector<char> data; };
static std::vector<Entry> g_payload;
static bool g_payloadOk = false;

static bool read_payload(const char* exePath) {
    std::ifstream f(exePath, std::ios::binary);
    if (!f) return false;
    f.seekg(0, std::ios::end);
    size_t total = (size_t)f.tellg();
    f.seekg(0);
    std::vector<char> all(total);
    f.read(all.data(), total);
    // szukaj MAGIC 'WJAN' pod koniec
    if (total < 8) return false;
    unsigned int magic = 0;
    memcpy(&magic, &all[total - 4], 4);
    if (magic != 0x4E414A57) return false; // 'WJAN' LE
    unsigned int payloadSize = 0;
    memcpy(&payloadSize, &all[total - 8], 4);
    if (payloadSize + 8 > total) return false;
    size_t pos = total - 8 - payloadSize;
    unsigned int count = 0;
    memcpy(&count, &all[pos], 4);
    pos += 4;
    for (unsigned int i = 0; i < count && pos + 6 <= total - 8; i++) {
        unsigned short nlen = 0;
        memcpy(&nlen, &all[pos], 2); pos += 2;
        Entry e;
        e.name.assign(&all[pos], nlen); pos += nlen;
        unsigned int sz = 0;
        memcpy(&sz, &all[pos], 4); pos += 4;
        e.data.assign(all.begin() + pos, all.begin() + pos + sz);
        pos += sz;
        g_payload.push_back(std::move(e));
    }
    g_payloadOk = true;
    return true;
}

static bool write_file(const std::string& path, const std::vector<char>& data) {
    // katalogi nadrzędne
    std::string d = path.substr(0, path.find_last_of("\\/"));
    std::string cur;
    for (size_t i = 0; i < d.size(); i++) {
        char c = d[i];
        if (c == '\\' || c == '/') { if (!cur.empty()) CreateDirectoryA(cur.c_str(), nullptr); }
        cur += c;
    }
    if (!cur.empty()) CreateDirectoryA(cur.c_str(), nullptr);
    std::ofstream f(path, std::ios::binary | std::ios::trunc);
    if (!f) return false;
    f.write(data.data(), (std::streamsize)data.size());
    return f.good();
}

static void create_shortcut(const std::string& target, const std::string& lnkPath) {
    CoInitialize(nullptr);
    IShellLinkA* psl = nullptr;
    if (SUCCEEDED(CoCreateInstance(CLSID_ShellLink, nullptr, CLSCTX_INPROC_SERVER,
            IID_IShellLinkA, (void**)&psl))) {
        psl->SetPath(target.c_str());
        psl->SetWorkingDirectory(target.substr(0, target.find_last_of("\\/")).c_str());
        psl->SetDescription(APP_NAME);
        IPersistFile* ppf = nullptr;
        if (SUCCEEDED(psl->QueryInterface(IID_IPersistFile, (void**)&ppf))) {
            WCHAR w[MAX_PATH];
            MultiByteToWideChar(CP_ACP, 0, lnkPath.c_str(), -1, w, MAX_PATH);
            ppf->Save(w, TRUE);
            ppf->Release();
        }
        psl->Release();
    }
    CoUninitialize();
}

static void register_uninstall(const std::string& dir) {
    HKEY k;
    std::string key = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\WOJAN-AI-FACTORY";
    if (RegCreateKeyExA(HKEY_CURRENT_USER, key.c_str(), 0, nullptr, 0, KEY_WRITE, nullptr, &k, nullptr) == ERROR_SUCCESS) {
        std::string uninst = "\"" + dir + "\\Setup.exe\" /uninstall";
        std::string disp = std::string(APP_NAME) + " 1.0.0";
        RegSetValueExA(k, "DisplayName", 0, REG_SZ, (BYTE*)disp.c_str(), (DWORD)disp.size() + 1);
        RegSetValueExA(k, "UninstallString", 0, REG_SZ, (BYTE*)uninst.c_str(), (DWORD)uninst.size() + 1);
        RegSetValueExA(k, "Publisher", 0, REG_SZ, (BYTE*)"WOJAN", 6);
        RegSetValueExA(k, "DisplayVersion", 0, REG_SZ, (BYTE*)"1.0.0", 6);
        RegCloseKey(k);
    }
}

static void do_uninstall(const char* exePath) {
    std::string dir = exePath;
    dir = dir.substr(0, dir.find_last_of("\\/"));
    if (MessageBoxA(nullptr, "Odinstalować WOJAN AI FACTORY?", APP_NAME, MB_YESNO | MB_ICONQUESTION) != IDYES) return;
    // usuń pliki (bez Setup.exe — zostaje do usunięcia na końcu)
    std::string cmd = "cmd.exe /C ping -n 3 127.0.0.1 >nul & rmdir /S /Q \"" + dir + "\"";
    STARTUPINFOA si{ sizeof(si) };
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    PROCESS_INFORMATION pi{};
    CreateProcessA(nullptr, (char*)cmd.c_str(), nullptr, nullptr, FALSE, CREATE_NO_WINDOW, nullptr, nullptr, &si, &pi);
    HKEY k;
    std::string key = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\WOJAN-AI-FACTORY";
    RegDeleteKeyA(HKEY_CURRENT_USER, key.c_str());
    MessageBoxA(nullptr, "Odinstalowano.", APP_NAME, MB_OK | MB_ICONINFORMATION);
}

// ---- okno ----
static HWND g_hwnd, g_hDir, g_hProgress, g_hChk;

static void do_install() {
    GetWindowTextA(g_hDir, g_dir, MAX_PATH);
    if (g_dir[0] == 0) { MessageBoxA(nullptr, "Podaj katalog docelowy.", APP_NAME, MB_ICONWARNING); return; }
    g_shortcut = (SendMessage(g_hChk, BM_GETCHECK, 0, 0) == BST_CHECKED);
    if (!g_payloadOk) { MessageBoxA(nullptr, "Brak danych instalacyjnych.", APP_NAME, MB_ICONERROR); return; }
    SetWindowTextA(g_hProgress, "Instalowanie...");
    int okCount = 0;
    for (auto& e : g_payload) {
        std::string path = std::string(g_dir) + "\\" + e.name;
        for (auto& c : path) if (c == '/') c = '\\';
        if (write_file(path, e.data)) okCount++;
    }
    // Setup.exe do katalogu (dla deinstalacji)
    char self[MAX_PATH];
    GetModuleFileNameA(nullptr, self, MAX_PATH);
    std::ifstream sf(self, std::ios::binary);
    std::vector<char> selfData((std::istreambuf_iterator<char>(sf)), std::istreambuf_iterator<char>());
    write_file(std::string(g_dir) + "\\Setup.exe", selfData);
    // konfiguracja startowa (jeśli brak)
    std::string cfg = std::string(g_dir) + "\\wojan-config.json";
    if (GetFileAttributesA(cfg.c_str()) == INVALID_FILE_ATTRIBUTES) {
        std::string defCfg = R"({"ollama":{"base":"http://127.0.0.1:11434"},"mode":"AUTO"})";
        write_file(cfg, std::vector<char>(defCfg.begin(), defCfg.end()));
    }
    if (g_shortcut) {
        char desk[MAX_PATH];
        if (SUCCEEDED(SHGetFolderPathA(nullptr, CSIDL_DESKTOPDIRECTORY, nullptr, 0, desk))) {
            create_shortcut(std::string(g_dir) + "\\WOJAN-AI-FACTORY.exe",
                            std::string(desk) + "\\" + APP_NAME + ".lnk");
        }
    }
    register_uninstall(g_dir);
    char msg[512];
    snprintf(msg, sizeof(msg), "Zainstalowano %d plików w:\n%s\n\nSkrót na pulpicie: %s",
             okCount, g_dir, g_shortcut ? "tak" : "nie");
    SetWindowTextA(g_hProgress, "Gotowe.");
    MessageBoxA(nullptr, msg, APP_NAME, MB_OK | MB_ICONINFORMATION);
}

static LRESULT CALLBACK WndProc(HWND h, UINT m, WPARAM w, LPARAM l) {
    switch (m) {
        case WM_CREATE: {
            CreateWindowA("STATIC", "Katalog docelowy:", WS_CHILD | WS_VISIBLE,
                20, 20, 380, 20, h, nullptr, nullptr, nullptr);
            g_hDir = CreateWindowA("EDIT", g_dir, WS_CHILD | WS_VISIBLE | WS_BORDER | ES_AUTOHSCROLL,
                20, 44, 380, 26, h, nullptr, nullptr, nullptr);
            CreateWindowA("BUTTON", "Przeglądaj...", WS_CHILD | WS_VISIBLE,
                410, 44, 100, 26, h, (HMENU)1, nullptr, nullptr);
            g_hChk = CreateWindowA("BUTTON", "Utwórz skrót na pulpicie",
                WS_CHILD | WS_VISIBLE | BS_AUTOCHECKBOX, 20, 84, 380, 22, h, nullptr, nullptr, nullptr);
            SendMessage(g_hChk, BM_SETCHECK, BST_CHECKED, 0);
            CreateWindowA("BUTTON", "Instaluj", WS_CHILD | WS_VISIBLE | BS_DEFPUSHBUTTON,
                20, 120, 140, 34, h, (HMENU)2, nullptr, nullptr);
            g_hProgress = CreateWindowA("STATIC", "Gotowy do instalacji.",
                WS_CHILD | WS_VISIBLE, 20, 168, 490, 22, h, nullptr, nullptr, nullptr);
            return 0;
        }
        case WM_COMMAND:
            if (LOWORD(w) == 1) {
                BROWSEINFOA bi{};
                bi.hwndOwner = h;
                bi.lpszTitle = "Wybierz katalog instalacji";
                bi.ulFlags = BIF_RETURNONLYFSDIRS | BIF_NEWDIALOGSTYLE;
                LPITEMIDLIST pidl = SHBrowseForFolderA(&bi);
                if (pidl) {
                    char p[MAX_PATH];
                    if (SHGetPathFromIDListA(pidl, p)) SetWindowTextA(g_hDir, p);
                    CoTaskMemFree(pidl);
                }
            }
            if (LOWORD(w) == 2) do_install();
            return 0;
        case WM_CLOSE:
            DestroyWindow(h);
            return 0;
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProc(h, m, w, l);
}

int WINAPI WinMain(HINSTANCE hInst, HINSTANCE, LPSTR cmdLine, int) {
    char self[MAX_PATH];
    GetModuleFileNameA(nullptr, self, MAX_PATH);
    if (strstr(cmdLine, "/uninstall")) {
        do_uninstall(self);
        return 0;
    }
    read_payload(self);
    WNDCLASSEXA wc{ sizeof(wc), CS_CLASSDC, WndProc, 0, 0, hInst,
        LoadIcon(nullptr, IDI_APPLICATION), LoadCursor(nullptr, IDC_ARROW),
        (HBRUSH)(COLOR_BTNFACE + 1), nullptr, "WOJAN_SETUP", nullptr };
    RegisterClassExA(&wc);
    g_hwnd = CreateWindowA("WOJAN_SETUP", "WOJAN AI FACTORY — instalator",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
        CW_USEDEFAULT, CW_USEDEFAULT, 560, 250, nullptr, nullptr, hInst, nullptr);
    ShowWindow(g_hwnd, SW_SHOWDEFAULT);
    MSG msg;
    while (GetMessage(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    return 0;
}
