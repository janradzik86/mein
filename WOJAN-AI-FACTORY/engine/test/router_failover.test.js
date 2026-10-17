// Test: failover z zapisem stanu po błędzie limitu — realna logika routera.
import { test } from "node:test";
import assert from "node:assert/strict";
import { Router } from "../src/router.js";
import { MockProvider, QuotaError } from "../src/providers.js";
import { Store } from "../src/store.js";

test("quota → zapis rozmowy → przełączenie na zapasowy model; wyczerpany nie wraca", async () => {
  const store = new Store({});
  const events = [];
  const a = new MockProvider({ id: "model-a", script: [{ error: "quota", detail: "429 limit" }] });
  const b = new MockProvider({ id: "model-b", script: [{ text: "odpowiedź B" }] });
  const router = new Router([a, b], { mode: "FREE_FIRST", store, events: (e) => events.push(e) });

  const msgs = [{ role: "user", content: "napisz funkcję" }];
  const res = await router.complete(msgs, { label: "KODER" });
  assert.equal(res.text, "odpowiedź B");
  assert.equal(res.provider, "model-b");
  assert.equal(res.attempts, 2);

  // rozmowa zapisana przed failover
  const savedKeys = Object.keys(store.get("router.saved", {}));
  assert.equal(savedKeys.length, 1);
  assert.deepEqual(store.get(`router.saved.${savedKeys[0]}`).messages, msgs);

  // model-a oznaczony wyczerpany — kolejne complete nie próbuje go
  a.script.push({ text: "nie powinienem być wywołany" });
  const res2 = await router.complete(msgs);
  assert.equal(res2.provider, "model-b");
  assert.equal(a.calls.length, 1); // tylko jedno wywołanie A
  assert.ok(events.some((e) => e.type === "exhausted" && e.provider === "model-a"));
});

test("tryb LOCAL_ONLY nie sięga do chmury; MANUAL używa jednego providera", async () => {
  const local = new MockProvider({ id: "ollama-local", script: [{ text: "lokalnie" }] });
  const cloud = new MockProvider({ id: "gemini", script: [{ text: "chmura" }] });
  local.local = true; cloud.local = false;

  const r1 = new Router([local, cloud], { mode: "LOCAL_ONLY" });
  const res = await r1.complete([{ role: "user", content: "cześć" }]);
  assert.equal(res.provider, "ollama-local");

  const r2 = new Router([local, cloud], { mode: "MANUAL" });
  r2.manualProviderId = "gemini";
  const res2 = await r2.complete([{ role: "user", content: "cześć" }]);
  assert.equal(res2.provider, "gemini");
});

test("błąd niedostępności też przechodzi dalej, ale nie wyczerpuje providera", async () => {
  const store = new Store({});
  const a = new MockProvider({ id: "a", script: [{ error: "down", detail: "connection refused" }] });
  const b = new MockProvider({ id: "b", script: [{ text: "ok" }] });
  const router = new Router([a, b], { mode: "AUTO", store });
  const res = await router.complete([{ role: "user", content: "x" }]);
  assert.equal(res.provider, "b");
  assert.equal(store.get("router.exhausted.a/mock-1", 0), 0); // down != quota
});
