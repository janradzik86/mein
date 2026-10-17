// WOJAN AI FACTORY — warstwa HTTP wspólna dla Node i QuickJS.
// QuickJS udostępnia globalThis.HttpRequest(url, optsJson) -> {status, body}.
// Node używa fetch. Kod silnika zawsze korzysta z request().

export class HttpError extends Error {
  constructor(status, body) {
    super(`HTTP ${status}`);
    this.status = status;
    this.body = body;
  }
}

export async function request(url, opts = {}) {
  const method = (opts.method || "GET").toUpperCase();
  const headers = opts.headers || {};
  const body = opts.body || null;
  if (globalThis.HttpRequest) {
    const raw = globalThis.HttpRequest(url, JSON.stringify({ method, headers, body }));
    return {
      status: raw.status,
      headers: raw.headers || {},
      text: async () => raw.body,
      json: async () => JSON.parse(raw.body),
    };
  }
  const r = await fetch(url, { method, headers, body });
  const text = await r.text();
  return {
    status: r.status,
    headers: Object.fromEntries(r.headers.entries()),
    text: async () => text,
    json: async () => JSON.parse(text),
  };
}

export async function requestJson(url, opts = {}) {
  const r = await request(url, opts);
  const t = await r.text();
  if (r.status >= 400) throw new HttpError(r.status, t);
  try {
    return { status: r.status, json: JSON.parse(t), text: t };
  } catch {
    return { status: r.status, json: null, text: t };
  }
}
