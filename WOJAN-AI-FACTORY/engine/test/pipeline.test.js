// Test: pełny pipeline ZADANIE→…→EXE/APK z pętlą poprawek + trwałość po restarcie.
import { test } from "node:test";
import assert from "node:assert/strict";
import { createEngine } from "../wojan-engine.js";
import { MockProvider } from "../src/providers.js";
import { MockRunner } from "../src/pipeline.js";
import { Store } from "../src/store.js";

function mockPlan(text) {
  return JSON.stringify({
    name: "kalkulator",
    platform: "WINDOWS_EXE",
    files: [{ path: "src/main.c", content: "int main(){return 0;}" }],
    build: "cc src/main.c -o kalkulator.exe",
    tests: [{ name: "start", command: "kalkulator.exe --selftest" }],
    summary: "plan ok",
  });
}

test("pipeline: kompilacja fail → DIAGNOSTYKA → POPRAWKA → EXE/APK (realne przejścia faz)", async () => {
  const store = new Store({});
  const provider = new MockProvider({ id: "arch", script: [
    { text: mockPlan() },            // ARCHITEKT
    { text: JSON.stringify({ passed: false, failures: [{ test: "start", detail: "CS0103" }], summary: "błąd" }) }, // TESTER (nie dojdzie)
    { text: JSON.stringify({ files: [{ path: "src/main.c", content: "int main(){return 42;}" }], summary: "poprawiono" }) }, // KODER (poprawka)
    { text: JSON.stringify({ commands: ["cc src/main.c -o kalkulator.exe"], run_command: "kalkulator.exe", artifact: "dist/kalkulator.exe", summary: "build ok" }) }, // BUILD_AGENT r1
    { text: JSON.stringify({ commands: ["cc src/main.c -o kalkulator.exe"], run_command: "kalkulator.exe", artifact: "dist/kalkulator.exe", summary: "build ok" }) }, // BUILD_AGENT r2
    { text: JSON.stringify({ passed: true, failures: [], summary: "testy ok" }) }, // TESTER
    { text: JSON.stringify({ approved: true, issues: [], summary: "zgodne" }) },  // REVIEWER
    { text: JSON.stringify({ errors: [{ where: "build", message: "CS0103", fix_hint: "dodaj deklarację" }], summary: "znaleziono 1 błąd" }) }, // DIAGNOSTA
  ]});
  // kolejność wywołań zależy od fazy; ustawmy skrypt wygodniej przez pełną kontrolę:
  provider.script = [
    { text: mockPlan() },                                                                                 // 1 ARCHITEKT
    { text: JSON.stringify({ commands: ["cc"], run_command: "k", artifact: "dist/kalkulator.exe", summary: "b" }) }, // 2 BUILD_AGENT
    { text: JSON.stringify({ passed: false, failures: [{ test: "start", detail: "CS0103" }], summary: "fail" }) },   // 3 TESTER
    { text: JSON.stringify({ errors: [{ where: "build", message: "CS0103", fix_hint: "fix" }], summary: "diag" }) }, // 4 DIAGNOSTA
    { text: JSON.stringify({ files: [{ path: "src/main.c", content: "int main(){return 42;}" }], summary: "fix" }) },// 5 KODER (POPRAWKA)
    { text: JSON.stringify({ commands: ["cc"], run_command: "k", artifact: "dist/kalkulator.exe", summary: "b" }) }, // 6 BUILD_AGENT
    { text: JSON.stringify({ passed: true, failures: [], summary: "ok" }) },                                       // 7 TESTER
    { text: JSON.stringify({ approved: true, issues: [], summary: "ok" }) },                                       // 8 REVIEWER
  ];
  const runner = new MockRunner({ script: [
    { buildExit: 1, testExit: 1, log: "error CS0103" },
    { buildExit: 0, testExit: 0, log: "OK", artifact: "dist/kalkulator.exe" },
  ]});
  const engine = createEngine({ store, mock: { id: "arch", script: [] }, runner });
  // podmień provider na kontrolowany
  engine.router.all = [provider];

  const res = await engine.api.pipeline.run("Zrób kalkulator", {});
  assert.equal(res.ok, true);
  assert.equal(res.artifact.kind, "EXE");
  assert.equal(res.artifact.verified, true);

  const proj = store.get(`projects.${res.projectId}`);
  assert.equal(proj.status, "gotowy");
  const phases = proj.logs.map((l) => l.phase);
  for (const ph of ["PROJEKT", "KOD", "KOMPILACJA", "TEST", "DIAGNOSTYKA", "POPRAWKA", "EXE/APK"]) {
    assert.ok(phases.includes(ph), `brak fazy ${ph} w logach: ${phases.join(",")}`);
  }
  assert.equal(provider.calls.length, 8);
});

test("trwałość: stan projektu przeżywa restart procesu (nowy Store z tego samego źródła)", () => {
  const mem = { blob: null };
  const io = {
    readFile: () => mem.blob,
    writeFile: (_p, s) => { mem.blob = s; },
  };
  const s1 = new Store({ path: "store.json", io });
  s1.set("projects.p_1", { id: "p_1", name: "Test", status: "gotowy", phase: "EXE/APK" });
  // "restart" — nowy obiekt Store z tego samego pliku
  const s2 = new Store({ path: "store.json", io });
  assert.equal(s2.get("projects.p_1").name, "Test");
  assert.equal(s2.get("projects.p_1").status, "gotowy");
});
