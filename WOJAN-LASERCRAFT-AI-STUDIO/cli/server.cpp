// Lokalny serwer HTTP API (LAN: telefon ↔ laptop, podgląd w przeglądarce)
// Bez chmury — działa wyłącznie w sieci lokalnej, brak jakichkolwiek wywołań zewnętrznych.
#include "core_api.h"
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>
#include <cstring>
#include <string>
#include <thread>

using namespace lc;

namespace {
std::string readAll(int fd, size_t maxBytes = 64 * 1024 * 1024) {
  std::string data;
  char buf[16384];
  size_t headerEnd = std::string::npos;
  size_t contentLength = 0;
  while (true) {
    ssize_t n = recv(fd, buf, sizeof buf, 0);
    if (n <= 0) break;
    data.append(buf, (size_t)n);
    if (headerEnd == std::string::npos) {
      headerEnd = data.find("\r\n\r\n");
      if (headerEnd != std::string::npos) {
        auto head = lowerAscii(data.substr(0, headerEnd));
        size_t p = head.find("content-length:");
        if (p != std::string::npos) contentLength = (size_t)std::atol(head.c_str() + p + 15);
      }
    }
    if (headerEnd != std::string::npos && data.size() >= headerEnd + 4 + contentLength) break;
    if (data.size() > maxBytes) break;
  }
  return data;
}

std::string mimeFor(const std::string& path) {
  auto ends = [&](const char* s) { return path.size() >= strlen(s) && path.substr(path.size() - strlen(s)) == s; };
  if (ends(".html")) return "text/html; charset=utf-8";
  if (ends(".js")) return "application/javascript; charset=utf-8";
  if (ends(".css")) return "text/css; charset=utf-8";
  if (ends(".png")) return "image/png";
  if (ends(".svg")) return "image/svg+xml";
  if (ends(".json")) return "application/json; charset=utf-8";
  if (ends(".ttf")) return "font/ttf";
  return "application/octet-stream";
}

void sendAll(int fd, const std::string& data) {
  size_t sent = 0;
  while (sent < data.size()) {
    ssize_t n = send(fd, data.data() + sent, data.size() - sent, 0);
    if (n <= 0) break;
    sent += (size_t)n;
  }
}

void handleClient(int fd, Engine* eng, std::string wwwRoot) {
  std::string raw = readAll(fd);
  if (raw.empty()) { close(fd); return; }
  size_t lineEnd = raw.find("\r\n");
  std::string reqLine = raw.substr(0, lineEnd);
  auto parts = split(reqLine, ' ');
  std::string method = parts.size() > 0 ? parts[0] : "GET";
  std::string path = parts.size() > 1 ? parts[1] : "/";
  size_t qpos = path.find('?');
  std::string query = qpos == std::string::npos ? "" : path.substr(qpos + 1);
  if (qpos != std::string::npos) path = path.substr(0, qpos);
  size_t headerEnd = raw.find("\r\n\r\n");
  std::string body = headerEnd == std::string::npos ? "" : raw.substr(headerEnd + 4);

  std::string response;
  if (path == "/api" || path == "/api/") {
    std::string action = "status";
    for (auto& kv : split(query, '&')) {
      auto kvp = split(kv, '=');
      if (kvp.size() == 2 && kvp[0] == "action") action = kvp[1];
    }
    Json req = Json::object();
    if (!body.empty()) {
      std::string err;
      Json parsed = Json::parse(body, &err);
      if (!err.empty()) {
        response = "HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nAccess-Control-Allow-Origin: *\r\n\r\n";
        Json e = Json::object();
        e.set("error", "Błąd JSON: " + err);
        response += e.dump();
        sendAll(fd, response);
        close(fd);
        return;
      }
      req = parsed;
    }
    Json result = eng->handle(action, req);
    response = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n"
               "Access-Control-Allow-Origin: *\r\nCache-Control: no-store\r\n\r\n" + result.dump();
  } else if (method == "OPTIONS") {
    response = "HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\n"
               "Access-Control-Allow-Headers: *\r\nAccess-Control-Allow-Methods: GET,POST,OPTIONS\r\n\r\n";
  } else {
    std::string file = path == "/" ? "/index.html" : path;
    if (file.find("..") != std::string::npos) {
      response = "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\n\r\n";
    } else {
      std::string full = wwwRoot + file;
      std::string content = readFile(full);
      if (content.empty() && file != "/index.html") {
        // brak pliku → prosty interfejs testowy
        content = "<!doctype html><meta charset=utf-8><title>WOJAN API</title><body style='font-family:sans-serif'>"
                  "<h1>WOJAN LaserCraft AI Studio — API lokalne</h1>"
                  "<p>Endpoint: <code>POST /api?action=generate</code> z JSON <code>{\"prompt\":\"...\"}</code>.</p></body>";
        response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n" + content;
      } else {
        response = "HTTP/1.1 200 OK\r\nContent-Type: " + mimeFor(full) +
                   "\r\nCache-Control: no-store\r\n\r\n" + content;
      }
    }
  }
  sendAll(fd, response);
  close(fd);
}
}  // namespace

int runHttpServer(Engine& eng, int port, const std::string& wwwRoot) {
  int srv = socket(AF_INET, SOCK_STREAM, 0);
  if (srv < 0) { perror("socket"); return 1; }
  int on = 1;
  setsockopt(srv, SOL_SOCKET, SO_REUSEADDR, &on, sizeof on);
  sockaddr_in addr{};
  addr.sin_family = AF_INET;
  addr.sin_addr.s_addr = INADDR_ANY;
  addr.sin_port = htons((uint16_t)port);
  if (bind(srv, (sockaddr*)&addr, sizeof addr) < 0) { perror("bind"); return 1; }
  listen(srv, 16);
  printf("API lokalne: http://0.0.0.0:%d/api?action=status\n", port);
  fflush(stdout);
  while (true) {
    sockaddr_in cli{};
    socklen_t len = sizeof cli;
    int fd = accept(srv, (sockaddr*)&cli, &len);
    if (fd < 0) continue;
    std::thread([fd, &eng, wwwRoot] { handleClient(fd, &eng, wwwRoot); }).detach();
  }
  return 0;
}
