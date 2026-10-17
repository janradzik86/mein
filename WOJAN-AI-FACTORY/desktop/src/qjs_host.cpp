// WOJAN AI FACTORY — host QuickJS: ładuje silnik JS, udostępnia WoJS natywne,
// serializuje żądania API (wątek UI pompuje — jeden kontekst JS).
#include "wojan_host.h"
#include <quickjs.h>
#include <condition_variable>
#include <thread>
#include <chrono>
#include <fstream>
#include <sstream>
#include <cstring>
#include <cstdio>

static JSRuntime* g_rt = nullptr;
static JSContext* g_ctx = nullptr;
static std::string g_engineDir;
static std::string g_version = "1.0.0";

// ---- kolejka żądań (httpd -> wątek UI) ----
static std::mutex g_queueM;
static std::queue<std::shared_ptr<PendingRequest>> g_queue;

std::shared_ptr<PendingRequest> qjs_submit(const std::string& method, const std::string& path,
                                           const std::string& body, const std::string& headers) {
    auto pr = std::make_shared<PendingRequest>();
    pr->method = method; pr->path = path; pr->body = body; pr->headers = headers;
    {
        std::lock_guard<std::mutex> lk(g_queueM);
        g_queue.push(pr);
    }
    return pr;
}

void qjs_pump();

// ---- natywne WoJS ----
static JSValue js_read_file(JSContext* ctx, JSValueConst, int argc, JSValueConst* argv) {
    if (argc < 1) return JS_ThrowTypeError(ctx, "readFile(path)");
    const char* p = JS_ToCString(ctx, argv[0]);
    bool ok = false;
    std::string data = wojs_read_file(p, &ok);
    JS_FreeCString(ctx, p);
    if (!ok) return JS_NULL;
    return JS_NewStringLen(ctx, data.data(), (size_t)data.size());
}

static JSValue js_write_file(JSContext* ctx, JSValueConst, int argc, JSValueConst* argv) {
    if (argc < 2) return JS_ThrowTypeError(ctx, "writeFile(path, content)");
    const char* p = JS_ToCString(ctx, argv[0]);
    const char* c = JS_ToCString(ctx, argv[1]);
    bool ok = wojs_write_file(p, c);
    JS_FreeCString(ctx, p);
    JS_FreeCString(ctx, c);
    return JS_NewBool(ctx, ok);
}

static JSValue js_run_command(JSContext* ctx, JSValueConst, int argc, JSValueConst* argv) {
    if (argc < 1) return JS_ThrowTypeError(ctx, "runCommand(cmd, cwd, timeoutMs)");
    const char* c = JS_ToCString(ctx, argv[0]);
    const char* cwd = argc > 1 ? JS_ToCString(ctx, argv[1]) : "";
    int timeout = 600000;
    if (argc > 2) { int32_t t; if (JS_ToInt32(ctx, &t, argv[2]) == 0) timeout = t; }
    int exitCode = -1;
    std::string out = wojs_run_command(c, cwd, timeout, &exitCode);
    JS_FreeCString(ctx, c);
    if (argc > 1) JS_FreeCString(ctx, cwd);
    JSValue o = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, o, "exitCode", JS_NewInt32(ctx, exitCode));
    JS_SetPropertyStr(ctx, o, "output", JS_NewStringLen(ctx, out.data(), (size_t)out.size()));
    return o;
}

static std::string json_get_string(const std::string& json, const char* key) {
    std::string k = std::string("\"") + key + "\"";
    size_t p = json.find(k);
    if (p == std::string::npos) return "";
    size_t c = json.find(':', p + k.size());
    if (c == std::string::npos) return "";
    size_t q1 = json.find_first_not_of(" \t\n\r", c + 1);
    if (q1 == std::string::npos || json[q1] != '"') return "";
    std::string raw;
    for (size_t i = q1 + 1; i < json.size(); i++) {
        if (json[i] == '\\' && i + 1 < json.size()) {
            i++;
            switch (json[i]) {
                case 'n': raw += '\n'; break;
                case 't': raw += '\t'; break;
                case 'r': raw += '\r'; break;
                case '"': raw += '"'; break;
                case '\\': raw += '\\'; break;
                case '/': raw += '/'; break;
                default: raw += json[i];
            }
        } else if (json[i] == '"') break;
        else raw += json[i];
    }
    return raw;
}

static std::string json_get_object(const std::string& json, const char* key) {
    std::string k = std::string("\"") + key + "\"";
    size_t p = json.find(k);
    if (p == std::string::npos) return "{}";
    size_t brace = json.find('{', p);
    if (brace == std::string::npos) return "{}";
    int depth = 0;
    for (size_t i = brace; i < json.size(); i++) {
        if (json[i] == '{') depth++;
        else if (json[i] == '}') { depth--; if (depth == 0) return json.substr(brace, i - brace + 1); }
    }
    return "{}";
}

static JSValue js_http_request(JSContext* ctx, JSValueConst, int argc, JSValueConst* argv) {
    if (argc < 2) return JS_ThrowTypeError(ctx, "HttpRequest(url, optsJson)");
    const char* url = JS_ToCString(ctx, argv[0]);
    const char* opts = JS_ToCString(ctx, argv[1]);
    std::string o = opts;
    std::string method = json_get_string(o, "method");
    if (method.empty()) method = "GET";
    std::string body = json_get_string(o, "body");
    std::string headersJson = json_get_object(o, "headers");
    std::string urlStr = url;
    JS_FreeCString(ctx, url);
    JS_FreeCString(ctx, opts);
    int status = 0;
    std::string outBody, err;
    bool ok = http_request(urlStr, method, headersJson, body, &status, &outBody, &err);
    JSValue r = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, r, "status", JS_NewInt32(ctx, ok ? status : 0));
    JS_SetPropertyStr(ctx, r, "body", JS_NewStringLen(ctx, outBody.data(), (size_t)outBody.size()));
    JS_SetPropertyStr(ctx, r, "error", JS_NewString(ctx, err.c_str()));
    return r;
}

// ---- loader modułów ES z dysku ----
static JSModuleDef* js_module_loader(JSContext* ctx, const char* module_name, void*) {
    std::string name = module_name;
    std::string path = name;
    // ścieżki względne modułów silnika: ./src/x.js -> <engineDir>/src/x.js
    while (path.rfind("./", 0) == 0) path = path.substr(2);
    if (path.find(':') == std::string::npos && !path.empty() && path[0] != '/' && path[0] != '\\') {
        path = g_engineDir + "/" + path;
    }
    for (size_t p; (p = path.find("/./")) != std::string::npos;) path.erase(p, 2);
    for (size_t p; (p = path.find("//")) != std::string::npos;) path.erase(p, 1);
    bool ok = false;
    std::string src = wojs_read_file(path, &ok);
    if (!ok) {
        JS_ThrowReferenceError(ctx, "nie znaleziono modułu: %s", module_name);
        return nullptr;
    }
    JSValue val = JS_Eval(ctx, src.data(), (size_t)src.size(), module_name,
                          JS_EVAL_TYPE_MODULE | JS_EVAL_FLAG_COMPILE_ONLY);
    if (JS_IsException(val)) return nullptr;
    return (JSModuleDef*)JS_VALUE_GET_PTR(val);
}

static void pump_jobs() {
    for (;;) {
        JSContext* c1;
        int r = JS_ExecutePendingJob(g_rt, &c1);
        if (r <= 0) break;
    }
}

bool qjs_init(const std::string& engineDir, const std::string& configJson, std::string* err) {
    g_engineDir = engineDir;
    g_rt = JS_NewRuntime();
    if (!g_rt) { *err = "JS_NewRuntime failed"; return false; }
    JS_SetMemoryLimit(g_rt, (size_t)256 * 1024 * 1024);
    JS_SetMaxStackSize(g_rt, 4 * 1024 * 1024);
    g_ctx = JS_NewContext(g_rt);
    if (!g_ctx) { *err = "JS_NewContext failed"; return false; }
    JS_SetModuleLoaderFunc(g_rt, nullptr, js_module_loader, nullptr);

    JSValue wojs = JS_NewObject(g_ctx);
    JS_SetPropertyStr(g_ctx, wojs, "readFile", JS_NewCFunction(g_ctx, js_read_file, "readFile", 1));
    JS_SetPropertyStr(g_ctx, wojs, "writeFile", JS_NewCFunction(g_ctx, js_write_file, "writeFile", 2));
    JS_SetPropertyStr(g_ctx, wojs, "runCommand", JS_NewCFunction(g_ctx, js_run_command, "runCommand", 3));
    JS_SetPropertyStr(g_ctx, wojs, "appVersion", JS_NewString(g_ctx, g_version.c_str()));
    JSValue g = JS_GetGlobalObject(g_ctx);
    JS_SetPropertyStr(g_ctx, g, "WoJS", wojs);
    JS_SetPropertyStr(g_ctx, g, "HttpRequest",
                      JS_NewCFunction(g_ctx, js_http_request, "HttpRequest", 2));
    JS_FreeValue(g_ctx, g);

    bool ok = false;
    std::string entry = g_engineDir + "/wojan-engine.js";
    std::string src = wojs_read_file(entry, &ok);
    if (!ok) { *err = "brak silnika: " + entry; return false; }
    JSValue mod = JS_Eval(g_ctx, src.data(), (size_t)src.size(), entry.c_str(), JS_EVAL_TYPE_MODULE);
    if (JS_IsException(mod)) {
        JSValue ex = JS_GetException(g_ctx);
        const char* s = JS_ToCString(g_ctx, ex);
        *err = std::string("błąd ładowania silnika: ") + (s ? s : "?");
        if (s) JS_FreeCString(g_ctx, s);
        return false;
    }
    JS_FreeValue(g_ctx, mod);
    pump_jobs();

    std::string call = "globalThis.WojanEngine.init(" +
        (configJson.empty() ? std::string("{}") : configJson) + ")";
    JSValue v = JS_Eval(g_ctx, call.data(), call.size(), "<init>", JS_EVAL_TYPE_GLOBAL);
    if (JS_IsException(v)) {
        JSValue ex = JS_GetException(g_ctx);
        const char* s = JS_ToCString(g_ctx, ex);
        *err = std::string("błąd init silnika: ") + (s ? s : "?");
        if (s) JS_FreeCString(g_ctx, s);
        return false;
    }
    JS_FreeValue(g_ctx, v);
    pump_jobs();
    return true;
}

void qjs_shutdown() {
    if (g_ctx) JS_FreeContext(g_ctx);
    if (g_rt) JS_FreeRuntime(g_rt);
    g_ctx = nullptr;
    g_rt = nullptr;
}

// Wywołanie w kontekście JS; obsługuje Promise (async handle).
static HttpResponse call_engine(const std::string& method, const std::string& path,
                                const std::string& bodyJson, const std::string& headersJson) {
    HttpResponse resp;
    if (!g_ctx) { resp.status = 500; resp.body = "{\"error\":\"engine nie działa\"}"; return resp; }
    JSValue g = JS_GetGlobalObject(g_ctx);
    JS_SetPropertyStr(g_ctx, g, "__wojan_m", JS_NewString(g_ctx, method.c_str()));
    JS_SetPropertyStr(g_ctx, g, "__wojan_p", JS_NewString(g_ctx, path.c_str()));
    JS_SetPropertyStr(g_ctx, g, "__wojan_b", JS_NewString(g_ctx, bodyJson.c_str()));
    JS_SetPropertyStr(g_ctx, g, "__wojan_h", JS_NewString(g_ctx, headersJson.c_str()));
    const char* src =
        "(()=>{const b=__wojan_b?JSON.parse(__wojan_b):null;"
        "const h=__wojan_h?JSON.parse(__wojan_h):{};"
        "return globalThis.WojanEngine.handleRequest(__wojan_m,__wojan_p,"
        "b?JSON.stringify(b):null,JSON.stringify(h));})()";
    JSValue result = JS_Eval(g_ctx, src, 0, "<call>", JS_EVAL_TYPE_GLOBAL);
    if (JS_IsException(result)) {
        JSValue ex = JS_GetException(g_ctx);
        const char* s = JS_ToCString(g_ctx, ex);
        resp.status = 500;
        resp.body = std::string("{\"error\":\"") + (s ? s : "?") + "\"}";
        if (s) JS_FreeCString(g_ctx, s);
        JS_FreeValue(g_ctx, g);
        return resp;
    }
    if (JS_IsPromise(result)) {
        for (int i = 0; i < 600000; i++) {
            int state = JS_PromiseState(g_ctx, result);
            if (state == JS_PROMISE_FULFILLED || state == JS_PROMISE_REJECTED) break;
            JSContext* c1;
            if (JS_ExecutePendingJob(g_rt, &c1) <= 0)
                std::this_thread::sleep_for(std::chrono::milliseconds(2));
        }
        int state = JS_PromiseState(g_ctx, result);
        if (state == JS_PROMISE_FULFILLED) {
            JSValue v = JS_PromiseResult(g_ctx, result);
            JS_FreeValue(g_ctx, result);
            result = v;
        } else {
            resp.status = 500;
            resp.body = "{\"error\":\"promise nierozwiązany\"}";
            JS_FreeValue(g_ctx, result);
            JS_FreeValue(g_ctx, g);
            return resp;
        }
    }
    const char* s = JS_ToCString(g_ctx, result);
    std::string json = s ? s : "{}";
    if (s) JS_FreeCString(g_ctx, s);
    JS_FreeValue(g_ctx, result);
    JS_FreeValue(g_ctx, g);

    size_t st = json.find("\"status\"");
    if (st != std::string::npos) {
        size_t c = json.find(':', st);
        resp.status = atoi(json.c_str() + c + 1);
    }
    size_t b = json.find("\"body\"");
    if (b != std::string::npos) {
        size_t c = json.find(':', b);
        size_t start = json.find_first_not_of(" \t\n\r", c + 1);
        if (start != std::string::npos && json[start] == '{') {
            int depth = 0;
            size_t i = start;
            for (; i < json.size(); i++) {
                if (json[i] == '{') depth++;
                else if (json[i] == '}') { depth--; if (depth == 0) break; }
            }
            resp.body = json.substr(start, i - start + 1);
        } else if (start != std::string::npos && json.compare(start, 4, "null") == 0) {
            resp.body = "null";
        }
    }
    if (resp.body.empty()) resp.body = json;
    return resp;
}

void qjs_pump() {
    pump_jobs();
    for (;;) {
        std::shared_ptr<PendingRequest> pr;
        {
            std::lock_guard<std::mutex> lk(g_queueM);
            if (g_queue.empty()) break;
            pr = g_queue.front();
            g_queue.pop();
        }
        pr->resp = call_engine(pr->method, pr->path, pr->body, pr->headers);
        {
            std::lock_guard<std::mutex> lk(pr->m);
            pr->done = true;
        }
        pr->cv.notify_all();
    }
}

HttpResponse qjs_handle_request(const std::string& method, const std::string& path,
                                const std::string& bodyJson, const std::string& headersJson) {
    return call_engine(method, path, bodyJson, headersJson);
}

std::string qjs_version() { return g_version; }
