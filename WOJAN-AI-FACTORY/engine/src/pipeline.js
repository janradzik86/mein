// WOJAN AI FACTORY — pipeline: ZADANIE→PROJEKT→KOD→KOMPILACJA→TEST→DIAGNOSTYKA→POPRAWKA→EXE/APK

import { rolePrompt, parseRoleJson, ROLES } from "./roles.js";

export const PHASES = ["ZADANIE", "PROJEKT", "KOD", "KOMPILACJA", "TEST", "DIAGNOSTYKA", "POPRAWKA", "EXE/APK"];

export class Pipeline {
  // router: Router, store: Store, runner: {writeFiles(dir,files), build(dir,plan), test(dir,plan)}
  constructor(opts) {
    this.router = opts.router;
    this.store = opts.store;
    this.runner = opts.runner;
    this.log = opts.log || (() => {});
    this.maxFixRounds = opts.maxFixRounds || 3;
  }

  _log(projectId, phase, msg) {
    const entry = { ts: new Date().toISOString(), phase, msg };
    const logs = this.store.get(`projects.${projectId}.logs`, []);
    logs.push(entry);
    this.store.set(`projects.${projectId}.logs`, logs);
    this.log(entry);
  }

  _setPhase(projectId, phase) {
    this.store.set(`projects.${projectId}.phase`, phase);
    this._log(projectId, phase, `Faza: ${phase}`);
  }

  async _ask(role, taskText, state) {
    const messages = rolePrompt(role, taskText, state);
    const res = await this.router.complete(messages, { label: role });
    return { raw: res.text, data: parseRoleJson(res.text), provider: res.provider, model: res.model };
  }

  async run(taskText, opts = {}) {
    const projectId = opts.projectId || `p_${Date.now()}`;
    const proj = this.store.get(`projects.${projectId}`, null) || {
      id: projectId,
      name: opts.name || "projekt",
      platform: opts.platform || "WINDOWS_EXE",
      createdAt: new Date().toISOString(),
      status: "w trakcie",
      phase: "ZADANIE",
      logs: [],
      artifacts: [],
    };
    proj.taskText = taskText;
    this.store.set(`projects.${projectId}`, proj);

    // PROJEKT (ARCHITEKT)
    this._setPhase(projectId, "PROJEKT");
    const arch = await this._ask("ARCHITEKT", taskText, { projectId, phase: "PROJEKT" });
    proj.name = arch.data.name || proj.name;
    proj.platform = arch.data.platform || proj.platform;
    proj.plan = arch.data;
    this.store.set(`projects.${projectId}`, proj);

    let fixRound = 0;
    let lastErrors = [];
    while (fixRound <= this.maxFixRounds) {
      // KOD (KODER) — pierwszy raz z planu, później POPRAWKA
      this._setPhase(projectId, fixRound === 0 ? "KOD" : "POPRAWKA");
      let files = arch.data.files || [];
      if (fixRound > 0) {
        const fix = await this._ask("KODER", taskText, { plan: arch.data, errors: lastErrors, round: fixRound });
        if (fix.data.files && fix.data.files.length) files = fix.data.files;
        this._log(projectId, "POPRAWKA", `Poprawka runda ${fixRound}: ${fix.data.summary || ""}`);
      }
      proj.files = files;
      this.store.set(`projects.${projectId}`, proj);

      // BUILD_AGENT — plan kompilacji
      const build = await this._ask("BUILD_AGENT", taskText, { files: files.map((f) => f.path), plan: arch.data });
      proj.buildPlan = build.data;
      this.store.set(`projects.${projectId}`, proj);

      // KOMPILACJA (prawdziwy runner: MSVC/zig na PC, Gradle/SDK na PC — NIE na telefonie)
      this._setPhase(projectId, "KOMPILACJA");
      await this.runner.writeFiles(projectId, files);
      const buildResult = await this.runner.build(projectId, build.data);
      this._log(projectId, "KOMPILACJA", `build exit=${buildResult.exitCode}`);

      // TEST
      this._setPhase(projectId, "TEST");
      const testResult = await this.runner.test(projectId, build.data);
      this._log(projectId, "TEST", `test exit=${testResult.exitCode}`);
      const tester = await this._ask("TESTER", taskText, {
        buildLog: buildResult.log || "", testLog: testResult.log || "",
        exitCodes: { build: buildResult.exitCode, test: testResult.exitCode },
      });
      this._log(projectId, "TEST", `TESTER: passed=${tester.data.passed} — ${tester.data.summary || ""}`);

      if (buildResult.exitCode === 0 && testResult.exitCode === 0) {
        this._setPhase(projectId, "EXE/APK");
        const review = await this._ask("REVIEWER", taskText, { files: files.map((f) => f.path), build: build.data, tests: testResult });
        const artifact = {
          id: `a_${Date.now()}`,
          kind: proj.platform === "ANDROID_APK" ? "APK" : "EXE",
          path: buildResult.artifact || build.data.artifact || "",
          projectId,
          createdAt: new Date().toISOString(),
          verified: buildResult.exitCode === 0 && testResult.exitCode === 0,
        };
        proj.artifacts.push(artifact);
        proj.status = "gotowy";
        proj.review = review.data;
        this.store.set(`projects.${projectId}`, proj);
        this._log(projectId, "EXE/APK", `Artefakt ${artifact.kind}: ${artifact.path} (reviewer approved=${review.data.approved})`);
        return { projectId, ok: true, artifact, review: review.data };
      }

      // DIAGNOSTYKA
      this._setPhase(projectId, "DIAGNOSTYKA");
      const diag = await this._ask("DIAGNOSTA", taskText, {
        buildLog: buildResult.log || "",
        testLog: testResult.log || "",
        exitCodes: { build: buildResult.exitCode, test: testResult.exitCode },
      });
      lastErrors = diag.data.errors || [];
      this._log(projectId, "DIAGNOSTYKA", `Błędów: ${lastErrors.length} — ${diag.data.summary || ""}`);
      if (lastErrors.length === 0) {
        proj.status = "błąd";
        this.store.set(`projects.${projectId}`, proj);
        return { projectId, ok: false, error: "Kompilacja/testy nieudane, diagnosta nie znalazł błędów" };
      }
      fixRound++;
    }
    proj.status = "błąd — przekroczono limit poprawek";
    this.store.set(`projects.${projectId}`, proj);
    return { projectId, ok: false, error: `Przekroczono ${this.maxFixRounds} rund poprawek` };
  }
}

// Runner do testów: skryptowalny, ale symulacja jest JAWNIE oznaczona (mock).
export class MockRunner {
  constructor(opts = {}) {
    // [{buildExit, testExit, log}] kolejne wywołania; domyślnie: fail, fail, ok
    this.script = opts.script || [
      { buildExit: 1, testExit: 1, log: "error CS0103: nazwa 'x' nie istnieje" },
      { buildExit: 0, testExit: 0, log: "OK", artifact: "dist/projekt.exe" },
    ];
    this.files = {};
    this.calls = 0;
  }
  async writeFiles(projectId, files) {
    for (const f of files) this.files[`${projectId}/${f.path}`] = f.content;
  }
  async build(projectId, plan) {
    const s = this.script[Math.min(this.calls, this.script.length - 1)];
    return { exitCode: s.buildExit, log: s.log, artifact: s.artifact || "" };
  }
  async test(projectId, plan) {
    const s = this.script[Math.min(this.calls, this.script.length - 1)];
    this.calls++;
    return { exitCode: s.testExit, log: s.testExit === 0 ? "testy OK" : "FAIL: compile" };
  }
}
