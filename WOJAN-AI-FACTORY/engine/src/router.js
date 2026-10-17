// WOJAN AI FACTORY — router AI: tryby pracy, failover z zapisem stanu,
// NIGDY nie obchodzi limitów providerów (429 → oznaczenie wyczerpania).

import { QuotaError, UnavailableError } from "./providers.js";

export const MODES = ["LOCAL_ONLY", "FREE_FIRST", "AUTO", "CODING", "MANUAL"];

export class Router {
  // providers: [{id, complete(), free, local, model, kind}]
  constructor(providers, opts = {}) {
    this.all = providers;
    this.mode = opts.mode || "AUTO";
    this.store = opts.store || null; // {get(k,d), set(k,v)} — trwałość stanu limitów
    this.lastConversation = null;
    this.events = opts.events || (() => {});
  }

  setMode(mode) {
    if (!MODES.includes(mode)) throw new Error(`Nieznany tryb: ${mode}`);
    this.mode = mode;
    if (this.store) this.store.set("router.mode", mode);
  }

  _exhaustedKey(p) {
    return `router.exhausted.${p.id}/${p.model}`;
  }

  _isExhausted(p) {
    if (!this.store) return false;
    const until = this.store.get(this._exhaustedKey(p), 0);
    return typeof until === "number" && until > Date.now();
  }

  _markExhausted(p, hours = 1) {
    if (!this.store) return;
    this.store.set(this._exhaustedKey(p), Date.now() + hours * 3600 * 1000);
    this.events({ type: "exhausted", provider: p.id, model: p.model, hours });
  }

  clearExhausted(p) {
    if (this.store) this.store.set(this._exhaustedKey(p), 0);
  }

  chain() {
    const usable = this.all.filter((p) => !this._isExhausted(p));
    const byKind = (k) => usable.filter((p) => (p.kind || p.id) === k || p.id === k);
    switch (this.mode) {
      case "LOCAL_ONLY":
        return usable.filter((p) => p.local);
      case "FREE_FIRST":
        return [...usable.filter((p) => p.free && p.local), ...usable.filter((p) => p.free && !p.local), ...usable.filter((p) => !p.free)];
      case "CODING": {
        // preferuj modele oznaczone jako coding; reszta jako zapas
        const coding = usable.filter((p) => p.coding);
        return [...coding, ...usable.filter((p) => !p.coding)];
      }
      case "MANUAL": {
        const one = this.manualProviderId ? byKind(this.manualProviderId) : [usable[0]].filter(Boolean);
        return one;
      }
      case "AUTO":
      default:
        return [...usable.filter((p) => p.local), ...usable.filter((p) => !p.local)];
    }
  }

  // Zapis rozmowy PRZED failover (save-and-failover) — never lose context.
  saveConversation(label, messages) {
    if (!this.store) return;
    const key = `router.saved.${label}_${Date.now()}`;
    this.store.set(key, { label, messages, savedAt: new Date().toISOString() });
    this.events({ type: "saved", key, label });
    return key;
  }

  async complete(messages, opts = {}) {
    const chain = this.chain();
    if (chain.length === 0) throw new Error("Brak dostępnych modeli (wszystkie wyczerpane lub tryb bez providerów)");
    const label = opts.label || "chat";
    let lastErr = null;
    for (let i = 0; i < chain.length; i++) {
      const p = chain[i];
      try {
        this.events({ type: "try", provider: p.id, model: p.model, index: i });
        const res = await p.complete(messages, opts);
        this.events({ type: "ok", provider: p.id, model: p.model });
        return { ...res, attempts: i + 1, mode: this.mode };
      } catch (e) {
        lastErr = e;
        this.events({ type: "fail", provider: p.id, model: p.model, error: String(e) });
        // zapis stanu rozmowy przed przełączeniem
        if (i < chain.length - 1) this.saveConversation(label, messages);
        if (e instanceof QuotaError || e.isQuota) {
          this._markExhausted(p);
          continue; // NIE ponawiamy w tym samym providerze
        }
        if (e instanceof UnavailableError || e.isUnavailable) {
          continue;
        }
        throw e; // błąd programistyczny — nie maskujemy
      }
    }
    throw new Error(`Wszystkie modele niedostępne w trybie ${this.mode}: ${lastErr ? lastErr.message : "?"}`);
  }
}
