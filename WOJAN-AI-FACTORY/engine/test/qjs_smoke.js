// WOJAN AI FACTORY — test silnika w QuickJS-NG (runtime z EXE), NIE w Node.
// Uruchom: tests/run_qjs_engine.sh  (build/qjs --std --module engine/test/qjs_smoke.js)
// Testuje DOKŁADNIE interfejs hosta C++ z EXE: WojanEngine.init(cfg) + handleRequest(...).
import * as std from "qjs:std";
import * as os from "qjs:os";

// shim WoJS (w EXE implementuje to C++: sys_native.cpp)
globalThis.WoJS = {
  readFile: (p) => {
    try { return std.loadFile(p); } catch { return null; }
  },
  writeFile: (p, s) => {
    const f = std.open(p, "w");
    if (!f) return false;
    f.puts(s);
    f.close();
    return true;
  },
};

function assert(cond, msg) {
  if (!cond) {
    std.err.puts("FAIL: " + msg + "\n");
    std.exit(1);
  }
}

const TMP = "/tmp/wojan-qjs-test";
os.mkdir(TMP, 0o755);

// konfiguracja jak w wojan-config.json EXE + kontrolowany MockProvider
const cfg = {
  token: "qjs-token",
  storePath: TMP + "/store.json",
  mock: {
    id: "qjs",
    script: [
      { text: JSON.stringify({ name: "proj-qjs", platform: "WINDOWS_EXE",
          files: [{ path: "src/main.c", content: "int main(){return 0;}" }],
          build: "cc src/main.c -o p.exe", tests: [], summary: "plan" }) },
      { text: JSON.stringify({ commands: ["cc src/main.c -o p.exe"], run_command: "p.exe",
          artifact: "p.exe", summary: "build" }) },
      { text: JSON.stringify({ passed: false, failures: [{ test: "t", detail: "exit=1" }], summary: "fail" }) },
      { text: JSON.stringify({ errors: [{ where: "main.c", message: "błąd", fix_hint: "fix" }], summary: "diag" }) },
      { text: JSON.stringify({ files: [{ path: "src/main.c", content: "int main(){return 42;}" }], summary: "fix" }) },
      { text: JSON.stringify({ commands: ["cc src/main.c -o p.exe"], run_command: "p.exe",
          artifact: "p.exe", summary: "build" }) },
      { text: JSON.stringify({ passed: true, failures: [], summary: "ok" }) },
      { text: JSON.stringify({ approved: true, issues: [], summary: "ok" }) },
    ],
  },
};

const mod = await import("../wojan-engine.js");
const initRes = JSON.parse(globalThis.WojanEngine.init(JSON.stringify(cfg)));
assert(initRes.ok === true, "WojanEngine.init: " + JSON.stringify(initRes));
std.out.puts("[0/4] QuickJS: WojanEngine.init OK (tryb " + initRes.mode + ")\n");

const H = { Authorization: "Bearer qjs-token" };

const r1 = JSON.parse(await globalThis.WojanEngine.handleRequest(
  "POST", "/api/v1/projects", JSON.stringify({ name: "proj-qjs" }), JSON.stringify(H)));
assert(r1.status === 201, "POST /projects status=" + r1.status + " body=" + JSON.stringify(r1.body));
const id = r1.body.id;
std.out.puts("[1/4] QuickJS: POST /projects -> 201 " + id + "\n");

// MockRunner domyślny: 1. kompilacja FAIL, 2. OK -> pełna pętla DIAGNOSTYKA->POPRAWKA
const r2 = JSON.parse(await globalThis.WojanEngine.handleRequest(
  "POST", "/api/v1/projects/" + id + "/tasks",
  JSON.stringify({ task: "zrób EXE" }), JSON.stringify(H)));
assert(r2.status === 200 && r2.body.ok === true, "pipeline: " + JSON.stringify(r2.body).slice(0, 400));
assert(r2.body.artifact.kind === "EXE" && r2.body.artifact.verified === true, "artifact verified");
std.out.puts("[2/4] QuickJS: pipeline ZADANIE->EXE/APK z pętlą POPRAWKI PASS (artefakt verified)\n");

const r3 = JSON.parse(await globalThis.WojanEngine.handleRequest(
  "GET", "/api/v1/projects/" + id + "/logs?after=0", null, JSON.stringify(H)));
const phases = r3.body.logs.map((l) => l.phase);
for (const ph of ["PROJEKT", "KOD", "KOMPILACJA", "TEST", "DIAGNOSTYKA", "POPRAWKA", "EXE/APK"]) {
  assert(phases.indexOf(ph) >= 0, "brak fazy " + ph + " w: " + phases.join(","));
}
std.out.puts("[3/4] QuickJS: wszystkie fazy pipeline w logach (" + r3.body.logs.length + " wpisów)\n");

// trwałość: nowy odczyt tego samego pliku (tak jak restart aplikacji)
const raw = std.loadFile(TMP + "/store.json");
const data = JSON.parse(raw);
assert(data.projects[id].status === "gotowy", "trwałość stanu po restarcie");
std.out.puts("[4/4] QuickJS: trwałość stanu po restarcie PASS\n");

std.out.puts("=== QJS ENGINE SMOKE: PASS (QuickJS-NG — ten sam runtime co w EXE) ===\n");
std.exit(0);
