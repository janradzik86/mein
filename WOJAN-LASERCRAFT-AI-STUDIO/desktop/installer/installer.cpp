// WOJAN LaserCraft AI Studio — instalator Windows (Win32, bez zewnętrznych zależności).
// Samorozpakowujące się archiwum: dane instalacyjne są dołączone na końcu tego pliku EXE.
// Instalacja do %LOCALAPPDATA%\WOJAN LaserCraft AI + skróty + wpis w aplecie "Programy i funkcje".
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shlobj.h>
#include <objbase.h>
#include <objidl.h>
#include <shellapi.h>
#include <string>
#include <vector>
#include <cstdio>
#include <cstring>
#include <cstdint>

#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "shell32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "advapi32.lib")

static const char *APP_NAME = "WOJAN LaserCraft AI Studio";
static const char *APP_EXE = "WOJAN-LaserCraft-AI.exe";
static const char *APP_VERSION = "1.0.0";
static const char *UNINST_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\WOJANLaserCraftAI";
static const char FOOTER_MAGIC[8] = {'W', 'L', 'C', 'P', 'A', 'Y', '0', '1'};

struct Entry {
    std::string name;
    std::vector<unsigned char> data;
};

static std::wstring utf8ToWide(const std::string &s) {
    if (s.empty()) return std::wstring();
    int n = MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int) s.size(), NULL, 0);
    std::wstring w(n, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, s.c_str(), (int) s.size(), &w[0], n);
    return w;
}

static std::string wideToUtf8(const std::wstring &w) {
    if (w.empty()) return std::string();
    int n = WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int) w.size(), NULL, 0, NULL, NULL);
    std::string s(n, '\0');
    WideCharToMultiByte(CP_UTF8, 0, w.c_str(), (int) w.size(), &s[0], n, NULL, NULL);
    return s;
}

static std::wstring exePath() {
    wchar_t buf[MAX_PATH * 2];
    DWORD n = GetModuleFileNameW(NULL, buf, MAX_PATH * 2);
    return std::wstring(buf, n);
}

static std::wstring dirName(const std::wstring &p) {
    size_t i = p.find_last_of(L"\\/");
    return i == std::wstring::npos ? p : p.substr(0, i);
}

static std::wstring installDir() {
    wchar_t *base = NULL;
    std::wstring dir;
    if (SUCCEEDED(SHGetKnownFolderPath(FOLDERID_LocalAppData, 0, NULL, &base))) {
        dir = std::wstring(base) + L"\\Programs\\WOJAN LaserCraft AI";
        CoTaskMemFree(base);
    } else {
        wchar_t buf[MAX_PATH * 2];
        DWORD n = GetEnvironmentVariableW(L"LOCALAPPDATA", buf, MAX_PATH * 2);
        dir = std::wstring(buf, n) + L"\\Programs\\WOJAN LaserCraft AI";
    }
    return dir;
}

// ---------------- odczyt dołączonego archiwum ----------------
static bool readSelf(std::vector<unsigned char> &out) {
    HANDLE h = CreateFileW(exePath().c_str(), GENERIC_READ, FILE_SHARE_READ, NULL,
                           OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return false;
    LARGE_INTEGER size;
    if (!GetFileSizeEx(h, &size)) {
        CloseHandle(h);
        return false;
    }
    out.resize((size_t) size.QuadPart);
    DWORD read = 0;
    BOOL ok = ReadFile(h, out.data(), (DWORD) out.size(), &read, NULL);
    CloseHandle(h);
    return ok && read == (DWORD) out.size();
}

static uint64_t rd64(const unsigned char *p) {
    uint64_t v = 0;
    for (int i = 7; i >= 0; i--) v = (v << 8) | p[i];
    return v;
}

static uint32_t rd32(const unsigned char *p) {
    return (uint32_t) p[0] | ((uint32_t) p[1] << 8) | ((uint32_t) p[2] << 16) | ((uint32_t) p[3] << 24);
}

static uint32_t rd16(const unsigned char *p) {
    return (uint32_t) p[0] | ((uint32_t) p[1] << 8);
}

static bool loadPayload(std::vector<Entry> &entries, std::string &err) {
    std::vector<unsigned char> self;
    if (!readSelf(self)) {
        err = "Nie można odczytać własnego pliku EXE.";
        return false;
    }
    if (self.size() < 24) {
        err = "Plik instalatora jest uszkodzony (za mały).";
        return false;
    }
    const unsigned char *foot = self.data() + self.size() - 24;
    if (memcmp(foot, FOOTER_MAGIC, 8) != 0) {
        err = "Brak danych instalacyjnych na końcu pliku (uszkodzone pobranie?).";
        return false;
    }
    uint64_t payloadSize = rd64(foot + 8);
    uint32_t count = rd32(foot + 16);
    if (payloadSize == 0 || payloadSize + 24 > self.size()) {
        err = "Nieprawidłowy rozmiar danych instalacyjnych.";
        return false;
    }
    size_t start = self.size() - 24 - (size_t) payloadSize;
    size_t pos = start;
    for (uint32_t i = 0; i < count; i++) {
        if (pos + 2 > start + payloadSize) {
            err = "Nieprawidłowy nagłówek archiwum.";
            return false;
        }
        uint32_t nameLen = rd16(self.data() + pos);
        pos += 2;
        if (pos + nameLen + 4 > start + payloadSize) {
            err = "Nieprawidłowy wpis archiwum.";
            return false;
        }
        Entry e;
        e.name.assign((const char *) self.data() + pos, nameLen);
        pos += nameLen;
        uint32_t sz = rd32(self.data() + pos);
        pos += 4;
        if (pos + sz > start + payloadSize) {
            err = "Nieprawidłowy rozmiar pliku w archiwum.";
            return false;
        }
        e.data.assign(self.data() + pos, self.data() + pos + sz);
        pos += sz;
        entries.push_back(e);
    }
    return true;
}

// ---------------- skróty i rejestr ----------------
static void createShortcut(const std::wstring &lnkPath, const std::wstring &target,
                           const std::wstring &workDir, const std::wstring &icon) {
    IShellLinkW *psl = NULL;
    if (FAILED(CoCreateInstance(CLSID_ShellLink, NULL, CLSCTX_INPROC_SERVER,
                                IID_IShellLinkW, (void **) &psl)))
        return;
    psl->SetPath(target.c_str());
    psl->SetWorkingDirectory(workDir.c_str());
    psl->SetDescription(L"WOJAN LaserCraft AI Studio — projekty do grawerowania i cięcia laserem");
    psl->SetIconLocation(icon.c_str(), 0);
    IPersistFile *pf = NULL;
    if (SUCCEEDED(psl->QueryInterface(IID_IPersistFile, (void **) &pf))) {
        pf->Save(lnkPath.c_str(), TRUE);
        pf->Release();
    }
    psl->Release();
}

static std::wstring knownFolder(REFKNOWNFOLDERID id) {
    wchar_t *p = NULL;
    if (SUCCEEDED(SHGetKnownFolderPath(id, 0, NULL, &p))) {
        std::wstring s(p);
        CoTaskMemFree(p);
        return s;
    }
    return std::wstring();
}

static void writeRegistry(const std::wstring &dir, const std::wstring &uninstaller) {
    HKEY hk;
    if (RegCreateKeyExW(HKEY_CURRENT_USER, utf8ToWide(UNINST_KEY).c_str(), 0, NULL, 0,
                        KEY_WRITE, NULL, &hk, NULL) != ERROR_SUCCESS)
        return;
    RegSetValueExW(hk, L"DisplayName", 0, REG_SZ, (const BYTE *) L"WOJAN LaserCraft AI Studio",
                   (DWORD) (wcslen(L"WOJAN LaserCraft AI Studio") + 1) * 2);
    std::wstring ver = L"1.0.0";
    RegSetValueExW(hk, L"DisplayVersion", 0, REG_SZ, (const BYTE *) ver.c_str(),
                   (DWORD) (ver.size() + 1) * 2);
    RegSetValueExW(hk, L"InstallLocation", 0, REG_SZ, (const BYTE *) dir.c_str(),
                   (DWORD) (dir.size() + 1) * 2);
    std::wstring un = L"\"" + uninstaller + L"\" /uninstall";
    RegSetValueExW(hk, L"UninstallString", 0, REG_SZ, (const BYTE *) un.c_str(),
                   (DWORD) (un.size() + 1) * 2);
    RegSetValueExW(hk, L"Publisher", 0, REG_SZ, (const BYTE *) L"WOJAN LaserCraft", (DWORD) (wcslen(L"WOJAN LaserCraft") + 1) * 2);
    RegSetValueExW(hk, L"DisplayIcon", 0, REG_SZ, (const BYTE *) uninstaller.c_str(),
                   (DWORD) (uninstaller.size() + 1) * 2);
    DWORD one = 1;
    RegSetValueExW(hk, L"NoModify", 0, REG_DWORD, (const BYTE *) &one, 4);
    RegSetValueExW(hk, L"NoRepair", 0, REG_DWORD, (const BYTE *) &one, 4);
    DWORD estKb = 30 * 1024;
    RegSetValueExW(hk, L"EstimatedSize", 0, REG_DWORD, (const BYTE *) &estKb, 4);
    RegCloseKey(hk);
}

// ---------------- okno ----------------
static HWND g_hwnd, g_status, g_btnInstall, g_btnClose, g_progress;
static bool g_installed = false;

static void setStatus(const std::wstring &s) {
    SetWindowTextW(g_status, s.c_str());
    UpdateWindow(g_status);
}

static void ensureParentDirs(const std::wstring &path) {
    size_t pos = 0;
    while ((pos = path.find(L'\\', pos + 1)) != std::wstring::npos) {
        std::wstring part = path.substr(0, pos);
        if (part.size() > 3) CreateDirectoryW(part.c_str(), NULL);
    }
}

static bool writeFileTo(const std::wstring &path, const std::vector<unsigned char> &data) {
    ensureParentDirs(path);
    HANDLE h = CreateFileW(path.c_str(), GENERIC_WRITE, 0, NULL, CREATE_ALWAYS,
                           FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return false;
    DWORD written = 0;
    BOOL ok = WriteFile(h, data.data(), (DWORD) data.size(), &written, NULL);
    CloseHandle(h);
    return ok && written == (DWORD) data.size();
}

static void doInstall() {
    EnableWindow(g_btnInstall, FALSE);
    setStatus(L"Przygotowanie...");
    std::vector<Entry> entries;
    std::string err;
    if (!loadPayload(entries, err)) {
        setStatus(L"Błąd: " + utf8ToWide(err));
        EnableWindow(g_btnInstall, TRUE);
        return;
    }
    std::wstring dir = installDir();
    if (!CreateDirectoryW(dir.c_str(), NULL) && GetLastError() != ERROR_ALREADY_EXISTS) {
        std::wstring parent = dirName(dir);
        CreateDirectoryW(dirName(parent).c_str(), NULL);
        CreateDirectoryW(parent.c_str(), NULL);
        CreateDirectoryW(dir.c_str(), NULL);
    }
    int done = 0;
    for (size_t i = 0; i < entries.size(); i++) {
        std::wstring name = utf8ToWide(entries[i].name);
        std::wstring full = dir + L"\\" + name;
        setStatus(L"Kopiowanie: " + name);
        if (!writeFileTo(full, entries[i].data)) {
            setStatus(L"Błąd zapisu pliku: " + name);
            EnableWindow(g_btnInstall, TRUE);
            return;
        }
        done++;
        SendMessageW(g_progress, PBM_SETPOS, (WPARAM) (100.0 * done / entries.size()), 0);
    }
    std::wstring target = dir + L"\\" + utf8ToWide(APP_EXE);
    std::wstring uninstaller = dir + L"\\Uninstall.exe";
    {
        std::vector<unsigned char> self;
        if (readSelf(self)) writeFileTo(uninstaller, self);
    }
    setStatus(L"Tworzenie skrótów...");
    std::wstring menu = knownFolder(FOLDERID_Programs);
    if (!menu.empty()) {
        CreateDirectoryW(menu.c_str(), NULL);
        createShortcut(menu + L"\\WOJAN LaserCraft AI Studio.lnk", target, dir, target);
    }
    std::wstring desktop = knownFolder(FOLDERID_Desktop);
    if (!desktop.empty())
        createShortcut(desktop + L"\\WOJAN LaserCraft AI Studio.lnk", target, dir, target);
    writeRegistry(dir, uninstaller);
    SendMessageW(g_progress, PBM_SETPOS, 100, 0);
    setStatus(L"Gotowe. Program zainstalowany w:\n" + dir);
    g_installed = true;
    SetWindowTextW(g_btnInstall, L"Uruchom program");
}

static void doUninstall() {
    std::wstring dir = installDir();
    std::wstring target = dir + L"\\" + utf8ToWide(APP_EXE);
    std::wstring menu = knownFolder(FOLDERID_Programs);
    std::wstring desktop = knownFolder(FOLDERID_Desktop);
    if (!menu.empty()) DeleteFileW((menu + L"\\WOJAN LaserCraft AI Studio.lnk").c_str());
    if (!desktop.empty()) DeleteFileW((desktop + L"\\WOJAN LaserCraft AI Studio.lnk").c_str());
    RegDeleteKeyW(HKEY_CURRENT_USER, utf8ToWide(UNINST_KEY).c_str());
    std::wstring pattern = dir + L"\\*";
    WIN32_FIND_DATAW fd;
    HANDLE h = FindFirstFileW(pattern.c_str(), &fd);
    if (h != INVALID_HANDLE_VALUE) {
        do {
            std::wstring n(fd.cFileName);
            if (n == L"." || n == L"..") continue;
            std::wstring full = dir + L"\\" + n;
            if (n == L"Uninstall.exe") {
                MoveFileExW(full.c_str(), NULL, MOVEFILE_DELAY_UNTIL_REBOOT);
            } else {
                DeleteFileW(full.c_str());
            }
        } while (FindNextFileW(h, &fd));
        FindClose(h);
    }
    MoveFileExW(dir.c_str(), NULL, MOVEFILE_DELAY_UNTIL_REBOOT);
    (void) target;
}

static LRESULT CALLBACK wndProc(HWND h, UINT m, WPARAM w, LPARAM l) {
    switch (m) {
        case WM_CREATE:
            return 0;
        case WM_COMMAND:
            if (LOWORD(w) == 1001) {
                if (g_installed) {
                    std::wstring target = installDir() + L"\\" + utf8ToWide(APP_EXE);
                    ShellExecuteW(NULL, L"open", target.c_str(), NULL,
                                  installDir().c_str(), SW_SHOWNORMAL);
                } else {
                    doInstall();
                }
                return 0;
            }
            if (LOWORD(w) == 1002) {
                DestroyWindow(h);
                return 0;
            }
            break;
        case WM_DESTROY:
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProcW(h, m, w, l);
}

static int runUninstallerSilent() {
    doUninstall();
    return 0;
}

int WINAPI WinMain(HINSTANCE hInst, HINSTANCE, LPSTR lpCmd, int) {
    CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    std::string cmd = lpCmd ? lpCmd : "";
    if (cmd.find("/uninstall") != std::string::npos || cmd.find("/U") != std::string::npos) {
        int r = runUninstallerSilent();
        CoUninitialize();
        return r;
    }

    WNDCLASSEXW wc = {0};
    wc.cbSize = sizeof(wc);
    wc.lpfnWndProc = wndProc;
    wc.hInstance = hInst;
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH) (COLOR_WINDOW + 1);
    wc.lpszClassName = L"WOJANLCInstaller";
    RegisterClassExW(&wc);

    g_hwnd = CreateWindowExW(0, wc.lpszClassName, L"WOJAN LaserCraft AI Studio — instalator",
                             WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX,
                             CW_USEDEFAULT, CW_USEDEFAULT, 620, 400, NULL, NULL, hInst, NULL);
    HFONT font = (HFONT) GetStockObject(DEFAULT_GUI_FONT);
    std::wstring dir = installDir();
    std::wstring hello =
        L"WOJAN LaserCraft AI Studio 1.0.0\n"
        L"Projektowanie wzorów do grawerowania i cięcia laserem\n\n"
        L"Program działa w pełni offline: silnik, czcionki i wzory są w tym instalatorze.\n"
        L"Bez konta, bez kluczy API, bez pobierania modeli.\n\n"
        L"Katalog instalacji:";
    HWND title = CreateWindowW(L"STATIC", hello.c_str(),
        WS_CHILD | WS_VISIBLE | SS_LEFT, 20, 15, 570, 110, g_hwnd, NULL, hInst, NULL);
    SendMessageW(title, WM_SETFONT, (WPARAM) font, TRUE);

    HWND path = CreateWindowW(L"STATIC", dir.c_str(), WS_CHILD | WS_VISIBLE | SS_LEFT,
                              20, 130, 570, 40, g_hwnd, NULL, hInst, NULL);
    SendMessageW(path, WM_SETFONT, (WPARAM) font, TRUE);

    g_progress = CreateWindowW(PROGRESS_CLASSW, NULL, WS_CHILD | WS_VISIBLE,
                               20, 180, 570, 22, g_hwnd, NULL, hInst, NULL);
    SendMessageW(g_progress, PBM_SETRANGE, 0, MAKELPARAM(0, 100));

    g_status = CreateWindowW(L"STATIC", L"Gotowy do instalacji.", WS_CHILD | WS_VISIBLE | SS_LEFT,
                             20, 215, 570, 90, g_hwnd, NULL, hInst, NULL);
    SendMessageW(g_status, WM_SETFONT, (WPARAM) font, TRUE);

    g_btnInstall = CreateWindowW(L"BUTTON", L"Zainstaluj", WS_CHILD | WS_VISIBLE | BS_DEFPUSHBUTTON,
                                 300, 315, 140, 34, g_hwnd, (HMENU) 1001, hInst, NULL);
    SendMessageW(g_btnInstall, WM_SETFONT, (WPARAM) font, TRUE);
    g_btnClose = CreateWindowW(L"BUTTON", L"Zamknij", WS_CHILD | WS_VISIBLE,
                               450, 315, 140, 34, g_hwnd, (HMENU) 1002, hInst, NULL);
    SendMessageW(g_btnClose, WM_SETFONT, (WPARAM) font, TRUE);

    ShowWindow(g_hwnd, SW_SHOW);
    UpdateWindow(g_hwnd);

    MSG msg;
    while (GetMessageW(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
    CoUninitialize();
    return 0;
}
