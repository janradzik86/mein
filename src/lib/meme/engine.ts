export type CaptionId = "top" | "bottom";

export type Caption = {
  id: CaptionId;
  text: string;
  x: number;
  y: number;
  size: number;
  stroke: number;
  fill: string;
  outline: string;
};

export type PhotoId = "kot" | "pies" | "kawa" | "szczyt";
export type PlateId = "scena" | "papier" | "pop" | "podzial" | "kino";

export type Background =
  | { kind: "photo"; id: PhotoId }
  | { kind: "plate"; id: PlateId }
  | { kind: "upload"; url: string; name: string };

export type Preset = {
  id: string;
  label: string;
  detail: string;
  uppercase: boolean;
  top: Pick<Caption, "x" | "y" | "size" | "stroke"> & Partial<Pick<Caption, "fill" | "outline">>;
  bottom: Pick<Caption, "x" | "y" | "size" | "stroke"> & Partial<Pick<Caption, "fill" | "outline">>;
};

export type Block = {
  id: CaptionId;
  cx: number;
  cy: number;
  left: number;
  top: number;
  width: number;
  height: number;
  lines: string[];
  fontPx: number;
  strokePx: number;
  lineH: number;
};

export const PHOTOS: { id: PhotoId; label: string; src: string }[] = [
  { id: "kot", label: "Kot", src: "/mem-templates/kot.jpg" },
  { id: "pies", label: "Pies", src: "/mem-templates/pies.jpg" },
  { id: "kawa", label: "Kawa", src: "/mem-templates/kawa.jpg" },
  { id: "szczyt", label: "Szczyt", src: "/mem-templates/szczyt.jpg" },
];

export const PLATES: { id: PlateId; label: string }[] = [
  { id: "scena", label: "Scena" },
  { id: "kino", label: "Kino" },
  { id: "pop", label: "Pop" },
  { id: "podzial", label: "Podział" },
  { id: "papier", label: "Papier" },
];

export const FILL_SWATCHES = [
  { id: "bialy", label: "Biały", value: "#ffffff" },
  { id: "zolty", label: "Żółty", value: "#f0c14b" },
  { id: "czarny", label: "Czarny", value: "#14120e" },
  { id: "czerwony", label: "Czerwony", value: "#e23d2b" },
] as const;

export const STROKE_SWATCHES = [
  { id: "czarny", label: "Czarny", value: "#14120e" },
  { id: "bialy", label: "Biały", value: "#ffffff" },
  { id: "zolty", label: "Żółty", value: "#f0c14b" },
  { id: "czerwony", label: "Czerwony", value: "#e23d2b" },
] as const;

const CLASSIC_INK = { fill: "#ffffff", outline: "#14120e" };

export const PRESETS: Preset[] = [
  {
    id: "klasyczny",
    label: "Klasyczny",
    detail: "Białe litery, gruby czarny kontur, góra i dół",
    uppercase: true,
    top: { ...CLASSIC_INK, x: 0.5, y: 0.13, size: 0.086, stroke: 0.015 },
    bottom: { ...CLASSIC_INK, x: 0.5, y: 0.87, size: 0.086, stroke: 0.015 },
  },
  {
    id: "gruby",
    label: "Gruby kontur",
    detail: "Większa czcionka i mocna obwódka",
    uppercase: true,
    top: { x: 0.5, y: 0.14, size: 0.1, stroke: 0.028 },
    bottom: { x: 0.5, y: 0.86, size: 0.1, stroke: 0.028 },
  },
  {
    id: "cienki",
    label: "Cienki",
    detail: "Mniejsze litery, delikatny kontur",
    uppercase: true,
    top: { x: 0.5, y: 0.12, size: 0.058, stroke: 0.004 },
    bottom: { x: 0.5, y: 0.88, size: 0.058, stroke: 0.004 },
  },
  {
    id: "gora",
    label: "Góra",
    detail: "Duży napis u góry, mały podpis na dole",
    uppercase: true,
    top: { x: 0.5, y: 0.16, size: 0.118, stroke: 0.016 },
    bottom: { x: 0.5, y: 0.9, size: 0.046, stroke: 0.008 },
  },
  {
    id: "dol",
    label: "Dół",
    detail: "Duży napis na dole, krótki wstęp u góry",
    uppercase: true,
    top: { x: 0.5, y: 0.1, size: 0.046, stroke: 0.008 },
    bottom: { x: 0.5, y: 0.82, size: 0.118, stroke: 0.016 },
  },
  {
    id: "srodek",
    label: "Środek",
    detail: "Oba napisy złożone przy środku kadru",
    uppercase: true,
    top: { x: 0.5, y: 0.4, size: 0.076, stroke: 0.012 },
    bottom: { x: 0.5, y: 0.58, size: 0.076, stroke: 0.012 },
  },
];

export function defaultCaptions(): Caption[] {
  const preset = PRESETS[0];
  return applyPreset(
    [
      {
        id: "top",
        text: "TYLKO NA CHWILĘ",
        x: 0.5,
        y: 0.13,
        size: 0.086,
        stroke: 0.015,
        fill: "#ffffff",
        outline: "#14120e",
      },
      {
        id: "bottom",
        text: "TRZY GODZINY PÓŹNIEJ",
        x: 0.5,
        y: 0.87,
        size: 0.086,
        stroke: 0.015,
        fill: "#ffffff",
        outline: "#14120e",
      },
    ],
    preset,
  );
}

export function applyPreset(captions: Caption[], preset: Preset): Caption[] {
  return captions.map((caption) => {
    const patch = caption.id === "top" ? preset.top : preset.bottom;
    return { ...caption, ...patch };
  });
}

export function adaptForPlate(captions: Caption[], plateId: PlateId): Caption[] {
  if (plateId !== "papier") return captions;
  return captions.map((caption) =>
    caption.fill === "#ffffff" && caption.outline === "#14120e"
      ? { ...caption, fill: "#14120e", outline: "#ffffff" }
      : caption,
  );
}

const plateCache = new Map<string, HTMLCanvasElement>();

export function plateCanvas(id: PlateId, size = 1080): HTMLCanvasElement {
  const key = `${id}-${size}`;
  const cached = plateCache.get(key);
  if (cached) return cached;
  const canvas = document.createElement("canvas");
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext("2d");
  if (!ctx) return canvas;
  drawPlate(ctx, id, size, size);
  plateCache.set(key, canvas);
  return canvas;
}

function clampByte(value: number): number {
  return Math.max(0, Math.min(255, value));
}

function grain(ctx: CanvasRenderingContext2D, w: number, h: number, amount: number) {
  const image = ctx.getImageData(0, 0, w, h);
  const data = image.data;
  let seed = 1337;
  const next = () => {
    seed = (seed * 16807) % 2147483647;
    return (seed & 0xffff) / 0xffff;
  };
  const step = w > 400 ? 8 : 4;
  for (let i = 0; i < data.length; i += step) {
    const n = (next() - 0.5) * amount;
    data[i] = clampByte(data[i] + n);
    data[i + 1] = clampByte(data[i + 1] + n);
    data[i + 2] = clampByte(data[i + 2] + n);
  }
  ctx.putImageData(image, 0, 0);
}

export function drawPlate(ctx: CanvasRenderingContext2D, id: PlateId, w: number, h: number) {
  if (id === "scena") {
    const glow = ctx.createRadialGradient(w * 0.5, h * 0.46, w * 0.04, w * 0.5, h * 0.5, w * 0.72);
    glow.addColorStop(0, "#5c5346");
    glow.addColorStop(0.42, "#2a261f");
    glow.addColorStop(1, "#12110e");
    ctx.fillStyle = glow;
    ctx.fillRect(0, 0, w, h);
    ctx.strokeStyle = "rgba(240, 193, 75, 0.7)";
    ctx.lineWidth = Math.max(2, w * 0.004);
    ctx.beginPath();
    ctx.arc(w / 2, h * 0.46, w * 0.25, 0, Math.PI * 2);
    ctx.stroke();
    grain(ctx, w, h, 16);
    return;
  }

  if (id === "papier") {
    ctx.fillStyle = "#e7dcc6";
    ctx.fillRect(0, 0, w, h);
    const vignette = ctx.createRadialGradient(w / 2, h / 2, w * 0.15, w / 2, h / 2, w * 0.72);
    vignette.addColorStop(0, "rgba(255, 250, 240, 0.2)");
    vignette.addColorStop(1, "rgba(92, 58, 28, 0.32)");
    ctx.fillStyle = vignette;
    ctx.fillRect(0, 0, w, h);
    grain(ctx, w, h, 18);
    return;
  }

  if (id === "pop") {
    ctx.fillStyle = "#14120e";
    ctx.fillRect(0, 0, w, h);
    const disc = ctx.createRadialGradient(w / 2, h * 0.5, w * 0.02, w / 2, h * 0.5, w * 0.34);
    disc.addColorStop(0, "#ffe7a3");
    disc.addColorStop(0.72, "#f0c14b");
    disc.addColorStop(1, "#c4922a");
    ctx.fillStyle = disc;
    ctx.beginPath();
    ctx.arc(w / 2, h * 0.5, w * 0.3, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "#f0c14b";
    const gap = Math.max(7, Math.round(w / 42));
    for (let y = gap / 2; y < h; y += gap) {
      for (let x = gap / 2; x < w; x += gap) {
        const dist = Math.hypot(x - w / 2, y - h * 0.5) / (w * 0.5);
        if (dist < 0.64 || dist > 0.96) continue;
        const radius = (1 - dist) * gap * 0.42;
        ctx.beginPath();
        ctx.arc(x, y, radius, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    return;
  }

  if (id === "podzial") {
    ctx.fillStyle = "#16140f";
    ctx.fillRect(0, 0, w / 2, h);
    ctx.fillStyle = "#e7dcc6";
    ctx.fillRect(w / 2, 0, w / 2, h);
    ctx.fillStyle = "#f0c14b";
    const bar = Math.max(3, w * 0.012);
    ctx.fillRect(w / 2 - bar / 2, 0, bar, h);
    grain(ctx, w, h, 12);
    return;
  }

  ctx.fillStyle = "#100e0c";
  ctx.fillRect(0, 0, w, h);
  const band = ctx.createLinearGradient(0, h * 0.2, 0, h * 0.8);
  band.addColorStop(0, "#3a2e22");
  band.addColorStop(0.48, "#8a6844");
  band.addColorStop(1, "#241c16");
  ctx.fillStyle = band;
  ctx.fillRect(0, h * 0.2, w, h * 0.6);
  ctx.fillStyle = "#f0c14b";
  const hair = Math.max(2, h * 0.004);
  ctx.fillRect(0, h * 0.2, w, hair);
  ctx.fillRect(0, h * 0.8 - hair, w, hair);
  grain(ctx, w, h, 14);
}

export function scaleSource(source: CanvasImageSource, sw: number, sh: number, max = 1280) {
  const scale = Math.min(1, max / Math.max(sw, sh));
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(sw * scale));
  canvas.height = Math.max(1, Math.round(sh * scale));
  const ctx = canvas.getContext("2d");
  if (!ctx) return canvas;
  ctx.drawImage(source, 0, 0, canvas.width, canvas.height);
  return canvas;
}

function displayText(text: string, uppercase: boolean): string {
  const clean = text.replace(/\r/g, "");
  return uppercase ? clean.toLocaleUpperCase("pl") : clean;
}

function wrapLines(ctx: CanvasRenderingContext2D, text: string, maxWidth: number): string[] {
  const lines: string[] = [];
  for (const paragraph of text.split("\n")) {
    const words = paragraph.split(/\s+/).filter(Boolean);
    if (words.length === 0) {
      lines.push("");
      continue;
    }
    let current = "";
    const pushWord = (word: string) => {
      if (ctx.measureText(word).width <= maxWidth) {
        current = word;
        return;
      }
      let chunk = "";
      for (const char of word) {
        const trial = chunk + char;
        if (ctx.measureText(trial).width > maxWidth && chunk) {
          lines.push(chunk);
          chunk = char;
        } else {
          chunk = trial;
        }
      }
      current = chunk;
    };
    for (const word of words) {
      const next = current ? `${current} ${word}` : word;
      if (ctx.measureText(next).width <= maxWidth) current = next;
      else {
        if (current) lines.push(current);
        pushWord(word);
      }
    }
    if (current) lines.push(current);
  }
  return lines.length > 0 ? lines : [""];
}

function layoutCaption(
  ctx: CanvasRenderingContext2D,
  caption: Caption,
  width: number,
  height: number,
  uppercase: boolean,
): Block | null {
  const text = displayText(caption.text, uppercase);
  if (!text.trim()) return null;
  const fontPx = Math.max(14, caption.size * width);
  const strokePx = Math.max(0, caption.stroke * width);
  ctx.font = `400 ${fontPx}px Anton, "Arial Narrow", Impact, sans-serif`;
  const lines = wrapLines(ctx, text, width * 0.9);
  const lineH = fontPx * 1.08;
  const measured = lines.map((line) => ctx.measureText(line).width);
  const blockWidth = Math.max(...measured, fontPx) + strokePx;
  const blockHeight = Math.max(lineH, lines.length * lineH) + strokePx;
  const cx = caption.x * width;
  const cy = caption.y * height;
  return {
    id: caption.id,
    cx,
    cy,
    left: cx - blockWidth / 2,
    top: cy - blockHeight / 2,
    width: blockWidth,
    height: blockHeight,
    lines,
    fontPx,
    strokePx,
    lineH,
  };
}

function drawBlock(ctx: CanvasRenderingContext2D, caption: Caption, block: Block) {
  ctx.save();
  ctx.font = `400 ${block.fontPx}px Anton, "Arial Narrow", Impact, sans-serif`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.lineJoin = "round";
  ctx.miterLimit = 2;
  ctx.fillStyle = caption.fill;
  ctx.strokeStyle = caption.outline;
  ctx.lineWidth = block.strokePx;
  let y = block.cy - (block.lines.length * block.lineH) / 2 + block.lineH / 2;
  for (const line of block.lines) {
    if (block.strokePx > 0.4) ctx.strokeText(line, block.cx, y);
    ctx.fillText(line, block.cx, y);
    y += block.lineH;
  }
  ctx.restore();
}

function guideColor(): string {
  if (typeof document === "undefined") return "#f0c14b";
  const value = getComputedStyle(document.documentElement).getPropertyValue("--color-primary").trim();
  return value || "#f0c14b";
}

function drawGuide(ctx: CanvasRenderingContext2D, block: Block, canvasWidth: number, cssWidth: number) {
  const scale = canvasWidth / Math.max(cssWidth, 1);
  const pad = 8 * scale;
  ctx.save();
  ctx.strokeStyle = guideColor();
  ctx.lineWidth = Math.max(2, 1.5 * scale);
  ctx.setLineDash([7 * scale, 5 * scale]);
  ctx.strokeRect(block.left - pad, block.top - pad, block.width + pad * 2, block.height + pad * 2);
  ctx.restore();
}

export function renderMeme(
  canvas: HTMLCanvasElement,
  bitmap: HTMLCanvasElement | null,
  captions: Caption[],
  uppercase: boolean,
  activeId: CaptionId | null,
  showGuide: boolean,
): Block[] {
  const width = bitmap?.width ?? 1080;
  const height = bitmap?.height ?? 1080;
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }
  const ctx = canvas.getContext("2d");
  if (!ctx) return [];
  if (bitmap) ctx.drawImage(bitmap, 0, 0);
  else {
    ctx.fillStyle = "#12110e";
    ctx.fillRect(0, 0, width, height);
  }
  const cssWidth = canvas.getBoundingClientRect().width || width;
  const blocks: Block[] = [];
  for (const caption of captions) {
    const block = layoutCaption(ctx, caption, width, height, uppercase);
    if (!block) continue;
    drawBlock(ctx, caption, block);
    blocks.push(block);
    if (showGuide && caption.id === activeId) drawGuide(ctx, block, width, cssWidth);
  }
  return blocks;
}

export function hitTest(blocks: Block[], x: number, y: number, pad: number): CaptionId | null {
  for (let index = blocks.length - 1; index >= 0; index -= 1) {
    const block = blocks[index];
    if (
      x >= block.left - pad &&
      x <= block.left + block.width + pad &&
      y >= block.top - pad &&
      y <= block.top + block.height + pad
    ) {
      return block.id;
    }
  }
  return null;
}

export function clampUnit(value: number): number {
  return Math.min(0.94, Math.max(0.06, value));
}
