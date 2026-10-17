// WOJAN AI FACTORY — implementacja kontraktu API (zgodna z ApiClient.java).
// GET  /api/v1/status | projects | projects/{id} | projects/{id}/logs?after=N | artifacts | artifacts/{id}
// POST /api/v1/projects | projects/{id}/tasks | agents/{role}/run

import { ROLES, rolePrompt, parseRoleJson } from "./roles.js";
import { Pipeline, PHASES } from "./pipeline.js";

export class Api {
  constructor(opts) {
    this.store = opts.store;
    this.router = opts.router;
    this.runner = opts.runner;
    this.token = opts.token || "";
    this.pipeline = new Pipeline({ router: this.router, store: this.store, runner: this.runner, log: opts.log });
    this.buildName = opts.buildName || "WOJAN AI FACTORY";
    this.version = opts.version || "1.0.0";
  }

  _auth(headers) {
    if (!this.token) return true;
    const h = headers || {};
    const auth = h.Authorization || h.authorization || "";
    return auth === `Bearer ${this.token}`;
  }

  async handle(method, path, body, headers) {
    if (!this._auth(headers)) return { status: 401, body: { error: "unauthorized" } };
    method = method.toUpperCase();
    const [p0, query] = path.split("?");
    const parts = p0.replace(/^\/+|\/+$/g, "").split("/");
    // parts[0]=api, parts[1]=v1
    const seg = parts.slice(2);

    try {
      if (method === "GET" && seg[0] === "status") {
        return { status: 200, body: { status: "ok", version: this.version, name: this.buildName, mode: this.router.mode, time: Date.now() } };
      }
      if (method === "GET" && seg[0] === "projects" && seg.length === 1) {
        return { status: 200, body: { projects: this._listProjects() } };
      }
      if (method === "GET" && seg[0] === "projects" && seg.length === 2) {
        const p = this.store.get(`projects.${seg[1]}`);
        return p ? { status: 200, body: p } : { status: 404, body: { error: "brak projektu" } };
      }
      if (method === "GET" && seg[0] === "projects" && seg[2] === "logs") {
        const after = Number((query || "").match(/after=(\d+)/)?.[1] || 0);
        const logs = (this.store.get(`projects.${seg[1]}.logs`) || []).slice(after);
        return { status: 200, body: { logs, total: (this.store.get(`projects.${seg[1]}.logs`) || []).length } };
      }
      if (method === "GET" && seg[0] === "artifacts" && seg.length === 1) {
        return { status: 200, body: { artifacts: this._listArtifacts() } };
      }
      if (method === "GET" && seg[0] === "artifacts" && seg.length === 2) {
        const a = this._listArtifacts().find((x) => x.id === seg[1]);
        return a ? { status: 200, body: a } : { status: 404, body: { error: "brak artefaktu" } };
      }
      if (method === "POST" && seg[0] === "projects" && seg.length === 1) {
        const id = `p_${Date.now()}`;
        const name = (body && body.name) || "projekt";
        this.store.set(`projects.${id}`, { id, name, status: "nowy", phase: "ZADANIE", logs: [], artifacts: [], createdAt: new Date().toISOString() });
        return { status: 201, body: this.store.get(`projects.${id}`) };
      }
      if (method === "POST" && seg[0] === "projects" && seg[2] === "tasks") {
        const taskText = (body && (body.task || body.text)) || "";
        const result = await this.pipeline.run(taskText, { projectId: seg[1] });
        return { status: 200, body: result };
      }
      if (method === "POST" && seg[0] === "agents" && seg.length === 3 && seg[2] === "run") {
        const role = decodeURIComponent(seg[1]).toUpperCase().replace(/-/g, "_");
        const mapped = role === "BUILD" ? "BUILD_AGENT" : role;
        if (!ROLES.includes(mapped)) return { status: 400, body: { error: `nieznana rola: ${role}` } };
        const taskText = (body && (body.task || body.text)) || "";
        const res = await this.router.complete(rolePrompt(mapped, taskText, {}), { label: mapped });
        let data = null;
        try { data = parseRoleJson(res.text); } catch { data = { raw: res.text }; }
        return { status: 200, body: { role: mapped, provider: res.provider, model: res.model, data } };
      }
      return { status: 404, body: { error: `brak endpointu ${method} ${path}` } };
    } catch (e) {
      return { status: 500, body: { error: String(e && e.message || e) } };
    }
  }

  _listProjects() {
    const all = this.store.get("projects", {}) || {};
    return Object.values(all).map((p) => ({
      id: p.id, name: p.name, status: p.status, phase: p.phase,
      platform: p.platform, createdAt: p.createdAt,
    }));
  }

  _listArtifacts() {
    const all = this.store.get("projects", {}) || {};
    return Object.values(all).flatMap((p) => p.artifacts || []);
  }
}
