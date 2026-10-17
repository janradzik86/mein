// WOJAN AI FACTORY — runner systemowy: prawdziwa kompilacja/testy na PC.
// QuickJS: przez WoJS.runCommand (CreateProcess); Node: child_process (testy narzędziowe).

export class SysRunner {
  constructor(opts = {}) {
    this.workRoot = opts.workRoot || null; // katalog roboczy projektów
    this.timeoutMs = opts.timeoutMs || 600000;
  }

  _run(cmd, cwd) {
    if (globalThis.WoJS && globalThis.WoJS.runCommand) {
      return globalThis.WoJS.runCommand(cmd, cwd || "", this.timeoutMs);
    }
    throw new Error("WoJS.runCommand niedostępne — użyj MockRunner lub hosta QuickJS");
  }

  _projectDir(projectId) {
    const sep = globalThis.WoJS ? "\\" : "/";
    return (this.workRoot || "projects") + sep + projectId;
  }

  async writeFiles(projectId, files) {
    const dir = this._projectDir(projectId);
    for (const f of files) {
      const path = dir + (globalThis.WoJS ? "\\" : "/") + f.path.replace(/\//g, globalThis.WoJS ? "\\" : "/");
      if (globalThis.WoJS && globalThis.WoJS.writeFile) {
        globalThis.WoJS.writeFile(path, f.content);
      } else {
        throw new Error("WoJS.writeFile niedostępne");
      }
    }
    return { dir };
  }

  async build(projectId, plan) {
    const dir = this._projectDir(projectId);
    const cmds = (plan && plan.commands) || [];
    let log = "";
    for (const c of cmds) {
      const r = this._run(c, dir);
      log += `$ ${c}\n${r.output}\n`;
      if (r.exitCode !== 0) return { exitCode: r.exitCode, log, artifact: "" };
    }
    return { exitCode: 0, log, artifact: (plan && plan.artifact) ? dir + "\\" + plan.artifact : "" };
  }

  async test(projectId, plan) {
    const dir = this._projectDir(projectId);
    const tests = (plan && plan.tests) || [];
    let log = "";
    for (const t of tests) {
      const r = this._run(t.command, dir);
      log += `$ ${t.command}\n${r.output}\n`;
      if (r.exitCode !== 0) return { exitCode: r.exitCode, log };
    }
    return { exitCode: 0, log };
  }
}
