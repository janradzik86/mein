// WOJAN AI FACTORY — serwer HTTP dla telefonu (winsock, wątek akceptacji).
// Kontrakt API = silnik JS (ta sama implementacja, co testy Node).
#include "wojan_host.h"
#include <winsock2.h>
#include <ws2tcpip.h>
#include <thread>
#include <atomic>
#include <cstdio>

#pragma comment(lib, "ws2_32.lib")

static std::atomic<bool> g_running{false};
static std::thread g_thread;
static unsigned short g_port = 0;
static std::string g_token;

static std::string trim(const std::string& s) {
    size_t a = s.find_first_not_of(" \t\r\n");
    size_t b = s.find_last_not_of(" \t\r\n");
    return a == std::string::npos ? "" : s.substr(a, b - a + 1);
}

static void handle_client(SOCKET client) {
    // odczyt nagłówków
    std::string data;
    char buf[4096];
    size_t headerEnd = std::string::npos;
    while (headerEnd == std::string::npos) {
        int n = recv(client, buf, sizeof(buf), 0);
        if (n <= 0) { closesocket(client); return; }
        data.append(buf, n);
        headerEnd = data.find("\r\n\r\n");
        if (data.size() > 1 << 20) { closesocket(client); return; }
    }
    std::string headers = data.substr(0, headerEnd);
    std::string body = data.substr(headerEnd + 4);

    // request line
    size_t lineEnd = headers.find("\r\n");
    std::string reqLine = headers.substr(0, lineEnd == std::string::npos ? headers.size() : lineEnd);
    std::string method, path;
    {
        size_t s1 = reqLine.find(' ');
        size_t s2 = reqLine.find(' ', s1 + 1);
        method = reqLine.substr(0, s1);
        path = (s1 == std::string::npos) ? "/" : reqLine.substr(s1 + 1, (s2 == std::string::npos ? reqLine.size() : s2) - s1 - 1);
    }

    // Content-Length + Authorization
    size_t contentLength = 0;
    std::string auth;
    size_t pos = 0;
    while (pos < headers.size()) {
        size_t e = headers.find("\r\n", pos);
        std::string line = headers.substr(pos, (e == std::string::npos ? headers.size() : e) - pos);
        pos = (e == std::string::npos) ? headers.size() : e + 2;
        size_t c = line.find(':');
        if (c == std::string::npos) continue;
        std::string k = trim(line.substr(0, c));
        std::string v = trim(line.substr(c + 1));
        if (_stricmp(k.c_str(), "Content-Length") == 0) contentLength = (size_t)atoi(v.c_str());
        if (_stricmp(k.c_str(), "Authorization") == 0) auth = v;
    }
    while (body.size() < contentLength) {
        int n = recv(client, buf, sizeof(buf), 0);
        if (n <= 0) break;
        body.append(buf, n);
    }

    // auth Bearer (jeśli token skonfigurowany)
    if (!g_token.empty()) {
        std::string expect = "Bearer " + g_token;
        if (auth != expect) {
            std::string r = "HTTP/1.1 401 Unauthorized\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: 27\r\n\r\n{\"error\":\"unauthorized\"}\n";
            send(client, r.data(), (int)r.size(), 0);
            closesocket(client);
            return;
        }
    }

    auto pr = qjs_submit(method, path, body, "{\"Authorization\":\"" + auth + "\"}");
    {
        std::unique_lock<std::mutex> lk(pr->m);
        pr->cv.wait_for(lk, std::chrono::seconds(1200), [&] { return pr->done; });
    }
    std::string respBody = pr->done ? pr->resp.body : "{\"error\":\"timeout\"}";
    int status = pr->done ? pr->resp.status : 504;
    const char* statusText =
        status == 200 ? "OK" :
        status == 201 ? "Created" :
        status == 401 ? "Unauthorized" :
        status == 404 ? "Not Found" :
        status == 504 ? "Gateway Timeout" : "Error";
    char head[256];
    snprintf(head, sizeof(head),
        "HTTP/1.1 %d %s\r\nContent-Type: application/json; charset=utf-8\r\nConnection: close\r\nContent-Length: %d\r\n\r\n",
        status, statusText, (int)respBody.size());
    send(client, head, (int)strlen(head), 0);
    send(client, respBody.data(), (int)respBody.size(), 0);
    closesocket(client);
}

static void server_thread(unsigned short port) {
    SOCKET srv = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (srv == INVALID_SOCKET) return;
    BOOL yes = 1;
    setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, (const char*)&yes, sizeof(yes));
    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;   // cały LAN — telefon łączy się z PC
    addr.sin_port = htons(port);
    if (bind(srv, (sockaddr*)&addr, sizeof(addr)) == SOCKET_ERROR) {
        closesocket(srv);
        return;
    }
    listen(srv, 8);
    g_port = port;
    u_long nb = 1;
    ioctlsocket(srv, FIONBIO, &nb);
    while (g_running) {
        SOCKET c = accept(srv, nullptr, nullptr);
        if (c == INVALID_SOCKET) {
            Sleep(100);
            continue;
        }
        u_long b = 0;
        ioctlsocket(c, FIONBIO, &b);
        std::thread(handle_client, c).detach();
    }
    closesocket(srv);
}

bool httpd_start(unsigned short port, const std::string& token, std::string* err) {
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        *err = "WSAStartup failed";
        return false;
    }
    g_token = token;
    g_running = true;
    g_thread = std::thread(server_thread, port);
    // chwila na bind
    for (int i = 0; i < 50 && g_port == 0; i++) Sleep(20);
    return true;
}

void httpd_stop() {
    g_running = false;
    if (g_thread.joinable()) g_thread.join();
    WSACleanup();
}

unsigned short httpd_port() { return g_port; }
