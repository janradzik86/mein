// WOJAN AI FACTORY — providerzy AI. Każdy complete() zwraca {text, model, provider}.
// Błędy: QuotaError (limit/429 — NIE obchodzimy!), UnavailableError (brak połączenia/modelu).

import { request, HttpError } from "./http.js";

export class QuotaError extends Error {
  constructor(provider, model, detail) {
    super(`Limit modelu (${provider}/${model}): ${detail}`);
    this.provider = provider;
    this.model = model;
    this.detail = detail;
    this.isQuota = true;
  }
}

export class UnavailableError extends Error {
  constructor(provider, model, detail) {
    super(`Model niedostępny (${provider}/${model}): ${detail}`);
    this.provider = provider;
    this.model = model;
    this.isUnavailable = true;
  }
}

function looksLikeQuota(status, body) {
  if (status === 429) return true;
  const s = String(body).toLowerCase();
  return s.includes("quota") || s.includes("rate limit") || s.includes("429") ||
         s.includes("exceeded") || s.includes("limit exceeded") || s.includes("przekroczono limit");
}

async function chatHttp(url, headers, payload, provider, model) {
  let r;
  try {
    r = await request(url, { method: "POST", headers: { "Content-Type": "application/json", ...headers }, body: JSON.stringify(payload) });
  } catch (e) {
    throw new UnavailableError(provider, model, String(e));
  }
  const text = await r.text();
  if (r.status >= 400) {
    if (looksLikeQuota(r.status, text)) throw new QuotaError(provider, model, text.slice(0, 200));
    throw new UnavailableError(provider, model, `HTTP ${r.status}: ${text.slice(0, 200)}`);
  }
  return text;
}

// ---- Ollama (PC lokalnie / Kaggle przez bezpieczny tunel — nigdy goły port!) ----
export class OllamaProvider {
  constructor(opts = {}) {
    this.id = "ollama";
    this.base = (opts.base || "http://127.0.0.1:11434").replace(/\/$/, "");
    this.model = opts.model || "llama3.1:8b";
    this.free = true;
    this.local = true;
  }
  async complete(messages, _opts = {}) {
    const text = await chatHttp(`${this.base}/api/chat`, {}, { model: this.model, messages, stream: false }, this.id, this.model);
    let out;
    try { out = JSON.parse(text); } catch { throw new UnavailableError(this.id, this.model, "zła odpowiedź JSON"); }
    return { text: (out.message && out.message.content) || "", model: this.model, provider: this.id };
  }
}

// ---- OpenAI-compatible: OmniRoute / OpenRouter / OpenAI / lokalny router ----
export class OpenAICompatProvider {
  constructor(opts = {}) {
    this.id = opts.id || "openai-compat";
    this.base = (opts.base || "https://openrouter.ai/api/v1").replace(/\/$/, "");
    this.model = opts.model || "openai/gpt-4o-mini";
    this.apiKey = opts.apiKey || "";
    this.free = !!opts.free;
    this.local = !!opts.local;
  }
  async complete(messages, _opts = {}) {
    const headers = this.apiKey ? { Authorization: `Bearer ${this.apiKey}` } : {};
    const text = await chatHttp(`${this.base}/chat/completions`, headers, { model: this.model, messages }, this.id, this.model);
    let out;
    try { out = JSON.parse(text); } catch { throw new UnavailableError(this.id, this.model, "zła odpowiedź JSON"); }
    return { text: out.choices?.[0]?.message?.content || "", model: this.model, provider: this.id };
  }
}

// ---- Gemini (darmowy tier API) ----
export class GeminiProvider {
  constructor(opts = {}) {
    this.id = "gemini";
    this.model = opts.model || "gemini-2.0-flash";
    this.apiKey = opts.apiKey || "";
    this.free = true;
    this.local = false;
  }
  async complete(messages, _opts = {}) {
    const contents = messages.map((m) => ({
      role: m.role === "assistant" ? "model" : "user",
      parts: [{ text: m.content }],
    }));
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${this.model}:generateContent?key=${encodeURIComponent(this.apiKey)}`;
    const text = await chatHttp(url, {}, { contents }, this.id, this.model);
    let out;
    try { out = JSON.parse(text); } catch { throw new UnavailableError(this.id, this.model, "zła odpowiedź JSON"); }
    return { text: out.candidates?.[0]?.content?.parts?.[0]?.text || "", model: this.model, provider: this.id };
  }
}

// ---- Mock: do testów automatycznych i trybu demonstracyjnego silnika ----
export class MockProvider {
  constructor(opts = {}) {
    this.id = opts.id || "mock";
    this.model = opts.model || "mock-1";
    this.free = true;
    this.local = true;
    this.script = opts.script || []; // kolejne odpowiedzi / błędy
    this.calls = [];
  }
  async complete(messages) {
    this.calls.push(messages);
    const next = this.script.length ? this.script.shift() : { text: `mock:${this.model}` };
    if (next.error === "quota") throw new QuotaError(this.id, this.model, next.detail || "quota");
    if (next.error === "down") throw new UnavailableError(this.id, this.model, next.detail || "down");
    return { text: next.text, model: this.model, provider: this.id };
  }
}
