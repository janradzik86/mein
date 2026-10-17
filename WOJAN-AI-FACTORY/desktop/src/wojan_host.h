// WOJAN AI FACTORY — nagłówki hosta desktopowego (Win32 + QuickJS + DX11/ImGui).
#pragma once
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <string>
#include <vector>
#include <mutex>
#include <memory>
#include <queue>
#include <condition_variable>
#include <functional>

struct HttpResponse {
    int status = 200;
    std::string body;      // JSON
};

struct PendingRequest {
    std::string method, path, body, headers;
    HttpResponse resp;
    bool done = false;
    std::mutex m;
    std::condition_variable cv;
};

std::shared_ptr<PendingRequest> qjs_submit(const std::string& method, const std::string& path,
                                           const std::string& body, const std::string& headers);

// http_client.cpp — WinHTTP, dla silnika JS (globalThis.HttpRequest)
bool http_request(const std::string& url, const std::string& method,
                  const std::string& headersJson, const std::string& body,
                  int* outStatus, std::string* outBody, std::string* outErr);

// qjs_host.cpp — kontekst QuickJS + natywne WoJS
bool qjs_init(const std::string& engineDir, const std::string& configJson, std::string* err);
void qjs_shutdown();
// Wykonuje żądanie API w sposób synchroniczny (kolejka; wątek UI pompuje)
HttpResponse qjs_handle_request(const std::string& method, const std::string& path,
                                const std::string& bodyJson, const std::string& headersJson);
// Pompuje zadania JS (wywoływane z pętli komunikatów)
void qjs_pump();
std::string qjs_version();

// httpd.cpp — serwer HTTP dla telefonu (winsock)
bool httpd_start(unsigned short port, const std::string& token, std::string* err);
void httpd_stop();
unsigned short httpd_port();

// sys_native.cpp — WoJS.runCommand / fs
std::string wojs_read_file(const std::string& path, bool* ok);
bool wojs_write_file(const std::string& path, const std::string& content);
std::string wojs_run_command(const std::string& cmd, const std::string& cwd, int timeoutMs,
                             int* exitCode);
