// Test: kontrakt API (zgodny z ApiClient.java) + auth Bearer.
import { test } from "node:test";
import assert from "node:assert/strict";
import { createEngine } from "../wojan-engine.js";
import { MockProvider } from "../src/providers.js";
import { MockRunner } from "../src/pipeline.js";
import { Store } from "../src/store.js";

function makeEngine() {
  const store = new Store({});
  const provider = new MockProvider({ id: "m", script: [
    { text: JSON.stringify({ name: "proj-x", platform: "ANDROID_APK", files: [{ path: "A.java", content: "class A{}" }], build: "gradle", tests: [], summary: "s" }) },
    { text: JSON.stringify({ commands: ["gradle"], run_command: "adb shell", artifact: "app-release.apk", summary: "s" }) },
    { text: JSON.stringify({ passed: true, failures: [], summary: "s" }) },
    { text: JSON.stringify({ approved: true, issues: [], summary: "s" }) },
  ]});
  const runner = new MockRunner({ script: [{ buildExit: 0, testExit: 0, log: "OK", artifact: "app-release.apk" }] });
  const engine = createEngine({ store, token: "sekret-123", mock: { script: [] }, runner });
  engine.router.all = [provider];
  return engine;
}

test("status i auth", async () => {
  const e = makeEngine();
  const bad = await e.handle("GET", "/api/v1/status", null, {});
  assert.equal(bad.status, 401);
  const ok = await e.handle("GET", "/api/v1/status", null, { Authorization: "Bearer sekret-123" });
  assert.equal(ok.status, 200);
  assert.equal(ok.body.name, "WOJAN AI FACTORY");
});

test("projekty + zadanie + logi + artefakty", async () => {
  const e = makeEngine();
  const H = { Authorization: "Bearer sekret-123" };
  const created = await e.handle("POST", "/api/v1/projects", { name: "demo" }, H);
  assert.equal(created.status, 201);
  const id = created.body.id;

  const task = await e.handle("POST", `/api/v1/projects/${id}/tasks`, { task: "zrób apkę" }, H);
  assert.equal(task.status, 200);
  assert.equal(task.body.ok, true);
  assert.equal(task.body.artifact.kind, "APK");

  const list = await e.handle("GET", "/api/v1/projects", null, H);
  assert.equal(list.body.projects.length, 1);
  assert.equal(list.body.projects[0].status, "gotowy");

  const logs = await e.handle("GET", `/api/v1/projects/${id}/logs?after=0`, null, H);
  assert.ok(logs.body.logs.length > 0);
  const logs2 = await e.handle("GET", `/api/v1/projects/${id}/logs?after=${logs.body.total}`, null, H);
  assert.equal(logs2.body.logs.length, 0);

  const arts = await e.handle("GET", "/api/v1/artifacts", null, H);
  assert.equal(arts.body.artifacts.length, 1);
  const a = await e.handle("GET", `/api/v1/artifacts/${arts.body.artifacts[0].id}`, null, H);
  assert.equal(a.status, 200);

  const role = await e.handle("POST", "/api/v1/agents/ARCHITEKT/run", { task: "coś" }, H);
  assert.equal(role.status, 200);
  assert.equal(role.body.role, "ARCHITEKT");

  const nope = await e.handle("GET", "/api/v1/nie-ma", null, H);
  assert.equal(nope.status, 404);
});
