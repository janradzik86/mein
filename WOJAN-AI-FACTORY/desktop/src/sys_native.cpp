// WOJAN AI FACTORY — natywne WoJS: fs + runCommand (CreateProcess) dla silnika.
#include "wojan_host.h"
#include <fstream>
#include <sstream>
#include <cstdio>

std::string wojs_read_file(const std::string& path, bool* ok) {
    *ok = false;
    std::ifstream f(path, std::ios::binary);
    if (!f) return "";
    std::stringstream ss;
    ss << f.rdbuf();
    *ok = true;
    return ss.str();
}

bool wojs_write_file(const std::string& path, const std::string& content) {
    // twórz katalogi nadrzędne
    std::string dir = path;
    size_t slash = dir.find_last_of("\\/");
    if (slash != std::string::npos) {
        std::string d = dir.substr(0, slash);
        std::string cur;
        for (size_t i = 0; i < d.size(); i++) {
            char c = d[i];
            if (c == '\\' || c == '/') {
                if (!cur.empty()) CreateDirectoryA(cur.c_str(), nullptr);
            }
            cur += c;
        }
        if (!cur.empty()) CreateDirectoryA(cur.c_str(), nullptr);
    }
    std::ofstream f(path, std::ios::binary | std::ios::trunc);
    if (!f) return false;
    f.write(content.data(), (std::streamsize)content.size());
    return f.good();
}

std::string wojs_run_command(const std::string& cmd, const std::string& cwd, int timeoutMs,
                             int* exitCode) {
    *exitCode = -1;
    SECURITY_ATTRIBUTES sa{ sizeof(sa), nullptr, TRUE };
    HANDLE hRead = nullptr, hWrite = nullptr;
    if (!CreatePipe(&hRead, &hWrite, &sa, 0)) return "(CreatePipe failed)";
    SetHandleInformation(hRead, HANDLE_FLAG_INHERIT, 0);

    STARTUPINFOA si{};
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    si.hStdOutput = hWrite;
    si.hStdError = hWrite;
    si.hStdInput = GetStdHandle(STD_INPUT_HANDLE);

    PROCESS_INFORMATION pi{};
    std::string full = "cmd.exe /S /C \"" + cmd + "\"";
    std::vector<char> cmdline(full.begin(), full.end());
    cmdline.push_back('\0');
    BOOL ok = CreateProcessA(nullptr, cmdline.data(), nullptr, nullptr, TRUE,
        CREATE_NO_WINDOW, nullptr, cwd.empty() ? nullptr : cwd.c_str(), &si, &pi);
    CloseHandle(hWrite);
    if (!ok) {
        CloseHandle(hRead);
        return "(CreateProcess failed, exit=-1)";
    }
    std::string output;
    char buf[4096];
    DWORD read = 0, start = GetTickCount();
    while (true) {
        if (timeoutMs > 0 && (int)(GetTickCount() - start) > timeoutMs) {
            TerminateProcess(pi.hProcess, 124);
            output += "\n(przekroczono limit czasu)";
            break;
        }
        DWORD avail = 0;
        if (!PeekNamedPipe(hRead, nullptr, 0, nullptr, &avail, nullptr)) break;
        if (avail > 0) {
            if (ReadFile(hRead, buf, sizeof(buf), &read, nullptr) && read > 0)
                output.append(buf, read);
        } else {
            if (WaitForSingleObject(pi.hProcess, 50) == WAIT_OBJECT_0) {
                while (ReadFile(hRead, buf, sizeof(buf), &read, nullptr) && read > 0)
                    output.append(buf, read);
                break;
            }
        }
    }
    DWORD code = 0;
    GetExitCodeProcess(pi.hProcess, &code);
    *exitCode = (int)code;
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    CloseHandle(hRead);
    return output;
}
