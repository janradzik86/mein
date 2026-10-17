// WOJAN AI FACTORY — trwały magazyn stanu (projekty, zadania, logi, artefakty).
// IO zależne od środowiska: Node podaje io={readFile,writeFile}, QuickJS używa WoJS.fs.

function defaultIO() {
  if (globalThis.WoJS && globalThis.WoJS.readFile) {
    return {
      readFile: (p) => globalThis.WoJS.readFile(p),
      writeFile: (p, s) => globalThis.WoJS.writeFile(p, s),
    };
  }
  return null;
}

export class Store {
  constructor(opts = {}) {
    this.data = opts.initial || {};
    this.path = opts.path || null;
    this.io = opts.io || defaultIO();
    if (this.path && this.io) {
      try {
        const raw = this.io.readFile(this.path);
        if (raw) this.data = JSON.parse(raw);
      } catch { /* pierwszy start — pusty magazyn */ }
    }
  }

  get(key, dflt = null) {
    const parts = key.split(".");
    let cur = this.data;
    for (const p of parts) {
      if (cur == null || typeof cur !== "object" || !(p in cur)) return dflt;
      cur = cur[p];
    }
    return cur === undefined ? dflt : cur;
  }

  set(key, value) {
    const parts = key.split(".");
    let cur = this.data;
    for (let i = 0; i < parts.length - 1; i++) {
      if (typeof cur[parts[i]] !== "object" || cur[parts[i]] == null) cur[parts[i]] = {};
      cur = cur[parts[i]];
    }
    cur[parts[parts.length - 1]] = value;
    this.flush();
    return value;
  }

  delete(key) {
    const parts = key.split(".");
    let cur = this.data;
    for (let i = 0; i < parts.length - 1; i++) {
      if (typeof cur[parts[i]] !== "object") return;
      cur = cur[parts[i]];
    }
    delete cur[parts[parts.length - 1]];
    this.flush();
  }

  flush() {
    if (!this.path || !this.io) return;
    this.io.writeFile(this.path, JSON.stringify(this.data));
  }
}
