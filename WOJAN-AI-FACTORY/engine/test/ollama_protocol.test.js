// Test: PROTOKÓŁ Ollama — realny HTTP (lokalny stub /api/chat na node:http).
// To NIE jest test z żywym modelem LLM; żywego Ollama używa właściciel na PC (patrz docs/TESTY.md).
import { test } from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { OllamaProvider, OpenAICompatProvider, QuotaError, UnavailableError } from "../src/providers.js";

function stubServer(handler) {
  return new Promise((resolve) => {
    const srv = http.createServer(handler);
    srv.listen(0, "127.0.0.1", () => resolve(srv));
  });
}

test("OllamaProvider: realne żądanie /api/chat, poprawny format i parsowanie", async () => {
  const srv = await stubServer((req, res) => {
    let body = "";
    req.on("data", (c) => (body += c));
    req.on("end", () => {
      const p = JSON.parse(body);
      assert.equal(req.method, "POST");
      assert.equal(req.url, "/api/chat");
      assert.equal(p.stream, false);
      assert.equal(p.model, "llama3.1:8b");
      assert.equal(p.messages[0].role, "user");
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify({ message: { role: "assistant", content: "Witaj z modelu" }, done: true }));
    });
  });
  const port = srv.address().port;
  const o = new OllamaProvider({ base: `http://127.0.0.1:${port}`, model: "llama3.1:8b" });
  const r = await o.complete([{ role: "user", content: "cześć" }]);
  assert.equal(r.text, "Witaj z modelu");
  assert.equal(r.provider, "ollama");
  srv.close();
});

test("OllamaProvider: 429 → QuotaError (limitów nie obchodzimy), connection refused → UnavailableError", async () => {
  const srv = await stubServer((req, res) => {
    res.statusCode = 429;
    res.end("rate limit exceeded, try later");
  });
  const port = srv.address().port;
  const o = new OllamaProvider({ base: `http://127.0.0.1:${port}` });
  await assert.rejects(() => o.complete([{ role: "user", content: "x" }]), QuotaError);
  srv.close();

  const dead = new OllamaProvider({ base: "http://127.0.0.1:1" });
  await assert.rejects(() => dead.complete([{ role: "user", content: "x" }]), UnavailableError);
});

test("OpenAICompatProvider (OmniRoute/OpenRouter): /chat/completions + Bearer + quota mapping", async () => {
  const srv = await stubServer((req, res) => {
    assert.equal(req.headers.authorization, "Bearer klucz-tajny");
    let body = "";
    req.on("data", (c) => (body += c));
    req.on("end", () => {
      const p = JSON.parse(body);
      if (p.model === "drogi/model") {
        res.statusCode = 429;
        res.end(JSON.stringify({ error: { message: "You exceeded your current quota" } }));
      } else {
        res.end(JSON.stringify({ choices: [{ message: { content: "kod gotowy" } }] }));
      }
    });
  });
  const port = srv.address().port;
  const p = new OpenAICompatProvider({ id: "omniroute", base: `http://127.0.0.1:${port}/v1`, model: "tani/model", apiKey: "klucz-tajny" });
  const r = await p.complete([{ role: "user", content: "napisz kod" }]);
  assert.equal(r.text, "kod gotowy");

  const paid = new OpenAICompatProvider({ id: "openai", base: `http://127.0.0.1:${port}/v1`, model: "drogi/model", apiKey: "klucz-tajny" });
  await assert.rejects(() => paid.complete([{ role: "user", content: "x" }]), QuotaError);
  srv.close();
});
