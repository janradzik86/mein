// WOJAN AI FACTORY — klient HTTP (WinHTTP) dla silnika JS: cloud API / Ollama / Kaggle.
#include "wojan_host.h"
#include <winhttp.h>
#include <sstream>
#include <vector>

#pragma comment(lib, "winhttp.lib")

static bool parse_url(const std::string& url, bool* https, std::wstring* host,
                      INTERNET_PORT* port, std::wstring* path) {
    size_t pos = url.find("://");
    if (pos == std::string::npos) return false;
    std::string scheme = url.substr(0, pos);
    *https = (scheme == "https");
    size_t hostStart = pos + 3;
    size_t pathStart = url.find('/', hostStart);
    std::string hostPort = (pathStart == std::string::npos)
        ? url.substr(hostStart) : url.substr(hostStart, pathStart - hostStart);
    *path = std::wstring(L"/");
    if (pathStart != std::string::npos) {
        std::string p = url.substr(pathStart);
        path->assign(p.begin(), p.end());
    }
    *port = *https ? INTERNET_DEFAULT_HTTPS_PORT : INTERNET_DEFAULT_HTTP_PORT;
    size_t colon = hostPort.rfind(':');
    std::string hostStr = hostPort;
    if (colon != std::string::npos) {
        hostStr = hostPort.substr(0, colon);
        *port = (INTERNET_PORT)atoi(hostPort.substr(colon + 1).c_str());
    }
    host->assign(hostStr.begin(), hostStr.end());
    return !host->empty();
}

// headersJson: {"Name":"value",...} — proste parsowanie bez pełnego JSON
static void parse_headers(const std::string& json, std::vector<std::wstring>* out) {
    size_t i = 0;
    while (i < json.size()) {
        size_t k1 = json.find('"', i);
        if (k1 == std::string::npos) break;
        size_t k2 = json.find('"', k1 + 1);
        if (k2 == std::string::npos) break;
        std::string key = json.substr(k1 + 1, k2 - k1 - 1);
        size_t c1 = json.find(':', k2);
        if (c1 == std::string::npos) break;
        size_t v1 = json.find_first_not_of(" \t\n\r", c1 + 1);
        if (v1 == std::string::npos) break;
        std::string val;
        if (json[v1] == '"') {
            size_t v2 = json.find('"', v1 + 1);
            val = json.substr(v1 + 1, v2 - v1 - 1);
            i = v2 + 1;
        } else {
            size_t v2 = json.find_first_of(",}", v1);
            val = json.substr(v1, (v2 == std::string::npos ? json.size() : v2) - v1);
            i = (v2 == std::string::npos) ? json.size() : v2 + 1;
        }
        std::wstring wkey(key.begin(), key.end()), wval(val.begin(), val.end());
        out->push_back(wkey + L": " + wval);
    }
}

bool http_request(const std::string& url, const std::string& method,
                  const std::string& headersJson, const std::string& body,
                  int* outStatus, std::string* outBody, std::string* outErr) {
    *outStatus = 0;
    outBody->clear();
    bool https = false;
    std::wstring host, path;
    INTERNET_PORT port = 0;
    if (!parse_url(url, &https, &host, &port, &path)) {
        *outErr = "zły URL: " + url;
        return false;
    }
    HINTERNET hSession = WinHttpOpen(L"WOJAN-AI-FACTORY/1.0",
        WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY, WINHTTP_NO_PROXY_NAME,
        WINHTTP_NO_PROXY_BYPASS, 0);
    if (!hSession) { *outErr = "WinHttpOpen failed"; return false; }
    WinHttpSetTimeouts(hSession, 30000, 30000, 60000, 120000);
    HINTERNET hConnect = WinHttpConnect(hSession, host.c_str(), port, 0);
    if (!hConnect) { WinHttpCloseHandle(hSession); *outErr = "WinHttpConnect failed"; return false; }
    std::wstring wmethod(method.begin(), method.end());
    DWORD flags = https ? WINHTTP_FLAG_SECURE : 0;
    HINTERNET hRequest = WinHttpOpenRequest(hConnect, wmethod.c_str(), path.c_str(),
        nullptr, WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, flags);
    if (!hRequest) {
        WinHttpCloseHandle(hConnect); WinHttpCloseHandle(hSession);
        *outErr = "WinHttpOpenRequest failed";
        return false;
    }
    std::vector<std::wstring> headers;
    parse_headers(headersJson, &headers);
    std::wstring allHeaders;
    for (auto& h : headers) { allHeaders += h; allHeaders += L"\r\n"; }
    BOOL ok = WinHttpSendRequest(hRequest,
        allHeaders.empty() ? WINHTTP_NO_ADDITIONAL_HEADERS : allHeaders.c_str(),
        allHeaders.empty() ? 0 : (DWORD)-1L,
        body.empty() ? WINHTTP_NO_REQUEST_DATA : (LPVOID)body.data(),
        (DWORD)body.size(), (DWORD)body.size(), 0);
    if (!ok || !WinHttpReceiveResponse(hRequest, nullptr)) {
        *outErr = "wysyłanie żądania nieudane (sieć/TLS)";
        WinHttpCloseHandle(hRequest); WinHttpCloseHandle(hConnect); WinHttpCloseHandle(hSession);
        return false;
    }
    DWORD status = 0, size = sizeof(status);
    WinHttpQueryHeaders(hRequest, WINHTTP_QUERY_STATUS_CODE | WINHTTP_QUERY_FLAG_NUMBER,
        WINHTTP_HEADER_NAME_BY_INDEX, &status, &size, WINHTTP_NO_HEADER_INDEX);
    *outStatus = (int)status;
    std::string data;
    DWORD avail = 0;
    while (WinHttpQueryDataAvailable(hRequest, &avail) && avail > 0) {
        std::vector<char> buf(avail);
        DWORD read = 0;
        WinHttpReadData(hRequest, buf.data(), avail, &read);
        data.append(buf.data(), read);
    }
    *outBody = data;
    WinHttpCloseHandle(hRequest); WinHttpCloseHandle(hConnect); WinHttpCloseHandle(hSession);
    return true;
}
