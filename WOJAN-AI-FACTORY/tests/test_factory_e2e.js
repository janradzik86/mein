// WOJAN AI FACTORY — test end-to-end fabryki: ZADANIE → … → EXE/APK przez publiczne API.
// Uruchom: node tests/test_factory_e2e.js  (albo: bash tests/run_all.sh)
import { createEngine } from "../engine/wojan-engine.js";
import { MockProvider } from "../engine/src/providers.js";
import { MockRunner } from "../engine/src/pipeline.js";
import { Store } from "../engine/src/store.js";
import assert from "node:assert/strict";

const store = new Store({});
const provider = new MockProvider({ id: "e2e", script: [
  { text: JSON.stringify({ name: "notatnik-ai", platform: "WINDOWS_EXE",
      files: [{ path: "src/main.c", content: "int main(){return 0;}" }],
      build: "gcc src/main.c -o notatnik.exe",
      tests: [{ name: "selftest", command: "notatnik.exe --selftest" }],
      summary: "plan" }) },
  { text: JSON.stringify({ commands: ["gcc src/main.c -o notatnik.exe"], run_command: "notatnik.exe",
      artifact: "notatnik.exe", summary: "build" }) },
  { text: JSON.stringify({ passed: false, failures: [{ test: "selftest", detail: "exit=1" }], summary: "fail" }) },
  { text: JSON.stringify({ errors: [{ where: "main.c", message: "zły kod powrotu", fix_hint: "return 0" }], summary: "diag" }) },
  { text: JSON.stringify({ files: [{ path: "src/main.c", content: "int main(){return 42;}" }], summary: "fix" }) },
  { text: JSON.stringify({ commands: ["gcc src/main.c -o notatnik.exe"], run_command: "notatnik.exe",
      artifact: "notatnik.exe", summary: "build" }) },
  { text: JSON.stringify({ passed: true, failures: [], summary: "ok" }) },
  { text: JSON.stringify({ approved: true, issues: [], summary: "zgodne z zadaniem" }) },
]});
const runner = new MockRunner({ script: [
  { buildExit: 1, testExit: 1, log: "main.c:1: error: cos" },
  { buildExit: 0, testExit: 0, log: "OK", artifact: "notatnik.exe" },
]});
const engine = createEngine({ store, token: "e2e-token", mock: { script: [] }, runner });
engine.router.all = [provider];

const H = { Authorization: "Bearer e2e-token" };
const t0 = Date.now();
const created = await engine.handle("POST", "/api/v1/projects", { name: "notatnik-ai" }, H);
assert.equal(created.status, 201);
const id = created.body.id;
console.log(`[1/5] PROJEKT utworzony: ${id}`);

const task = await engine.handle("POST", `/api/v1/projects/${id}/tasks`,
  { task: "Zbuduj notatnik konsolowy C dla Windows EXE" }, H);
assert.equal(task.status, 200);
assert.equal(task.body.ok, true, JSON.stringify(task.body));
assert.equal(task.body.artifact.kind, "EXE");
assert.equal(task.body.artifact.verified, true);
console.log(`[2/5] PIPELINE zakończony: artefakt ${task.body.artifact.path} (EXE, verified)`);

const detail = await engine.handle("GET", `/api/v1/projects/${id}`, null, H);
const phases = detail.body.logs.map((l) => l.phase);
for (const ph of ["PROJEKT", "KOD", "KOMPILACJA", "TEST", "DIAGNOSTYKA", "POPRAWKA", "EXE/APK"]) {
  assert.ok(phases.includes(ph), `brak fazy ${ph}`);
}
console.log("[3/5] FAZY kompletne: ZADANIE→PROJEKT→KOD→KOMPILACJA→TEST→DIAGNOSTYKA→POPRAWKA→EXE/APK");

const restart = new Store({ path: null, initial: JSON.parse(JSON.stringify(store.data)) });
assert.equal(restart.get(`projects.${id}`).status, "gotowy");
console.log("[4/5] TRWAŁOŚĆ: stan projektu po restarcie magazynu — OK");

const arts = await engine.handle("GET", "/api/v1/artifacts", null, H);
assert.equal(arts.body.artifacts.length, 1);
console.log(`[5/5] API: artefakt widoczny w /api/v1/artifacts (${Date.now() - t0} ms)`);

console.log("\n=== WOJAN AI FACTORY E2E: PASS ===");
