// WOJAN AI FACTORY — punkt wejścia silnika (QuickJS i Node).
// QuickJS: global WojanEngine.init(cfg) / handleRequest(method, path, body, headers) -> JSON.

import { Store } from "./src/store.js";
import { Router } from "./src/router.js";
import { OllamaProvider, OpenAICompatProvider, GeminiProvider, MockProvider } from "./src/providers.js";
import { Api } from "./src/api.js";
import { MockRunner } from "./src/pipeline.js";
import { SysRunner } from "./src/sys_runner.js";
import { ROLES } from "./src/roles.js";
import { MODES } from "./src/router.js";

export { Store, Router, Api, MockRunner, SysRunner, ROLES, MODES };
export * from "./src/providers.js";

export function buildProviders(cfg = {}) {
  const list = [];
  if (cfg.ollama !== false) {
    list.push(new OllamaProvider(cfg.ollama || {}));
  }
  if (cfg.omniRoute) {
    list.push(new OpenAICompatProvider({ id: "omniroute", local: true, free: true, coding: true, ...cfg.omniRoute }));
  }
  if (cfg.openRouter) {
    list.push(new OpenAICompatProvider({ id: "openrouter", ...cfg.openRouter }));
  }
  if (cfg.openai) {
    list.push(new OpenAICompatProvider({ id: "openai", base: "https://api.openai.com/v1", ...cfg.openai }));
  }
  if (cfg.gemini && cfg.gemini.apiKey) {
    list.push(new GeminiProvider(cfg.gemini));
  }
  if (cfg.mock) {
    list.push(new MockProvider(cfg.mock));
  }
  return list;
}

export function createEngine(cfg = {}) {
  const store = cfg.store || new Store({ path: cfg.storePath || null, io: cfg.io });
  const router = new Router(buildProviders(cfg), {
    mode: cfg.mode || store.get("router.mode") || "AUTO",
    store,
    events: cfg.events,
  });
  const api = new Api({
    store, router,
    runner: cfg.runner || (globalThis.WoJS && globalThis.WoJS.runCommand
      ? new SysRunner({ workRoot: cfg.workRoot })
      : new MockRunner()),
    token: cfg.token || "",
    buildName: cfg.buildName || "WOJAN AI FACTORY",
    version: cfg.version || "1.0.0",
  });
  return { store, router, api, handle: (m, p, b, h) => api.handle(m, p, b, h) };
}

// ---- interfejs dla QuickJS ----
let _engine = null;
globalThis.WojanEngine = {
  init(cfgJson) {
    const cfg = typeof cfgJson === "string" ? JSON.parse(cfgJson) : (cfgJson || {});
    _engine = createEngine(cfg);
    return JSON.stringify({ ok: true, mode: _engine.router.mode });
  },
  handleRequest(method, path, bodyJson, headersJson) {
    if (!_engine) return JSON.stringify({ status: 500, body: { error: "engine nie zainicjalizowany" } });
    let body = null;
    try { body = bodyJson ? JSON.parse(bodyJson) : null; } catch { body = null; }
    let headers = {};
    try { headers = headersJson ? JSON.parse(headersJson) : {}; } catch { headers = {}; }
    return _engine.handle(method, path, body, headers).then((r) => JSON.stringify(r));
  },
  version() { return "1.0.0"; },
};
