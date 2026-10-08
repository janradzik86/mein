import { useEffect, useLayoutEffect, useRef, useState, type DragEvent, type PointerEvent, type ReactNode } from "react";
import { Crosshair, Download, ImagePlus, RotateCcw } from "lucide-react";
import {
  FILL_SWATCHES,
  PHOTOS,
  PLATES,
  PRESETS,
  STROKE_SWATCHES,
  adaptForPlate,
  applyPreset,
  clampUnit,
  defaultCaptions,
  drawPlate,
  hitTest,
  plateCanvas,
  renderMeme,
  scaleSource,
  type Background,
  type Block,
  type Caption,
  type CaptionId,
  type PhotoId,
  type PlateId,
  type Preset,
} from "@/lib/meme/engine";

const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

export function MemeStudio() {
  const [background, setBackground] = useState<Background>({ kind: "photo", id: "kot" });
  const [captions, setCaptions] = useState<Caption[]>(defaultCaptions);
  const [uppercase, setUppercase] = useState(true);
  const [activeId, setActiveId] = useState<CaptionId>("top");
  const [presetId, setPresetId] = useState("klasyczny");
  const [status, setStatus] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dropping, setDropping] = useState(false);
  const [exportSize, setExportSize] = useState({ w: 1080, h: 1080 });

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const bitmapRef = useRef<HTMLCanvasElement | null>(null);
  const blocksRef = useRef<Block[]>([]);
  const fileRef = useRef<HTMLInputElement>(null);
  const uploadUrlRef = useRef<string | null>(null);
  const dragRef = useRef<{
    id: CaptionId;
    dx: number;
    dy: number;
    moved: boolean;
    px: number;
    py: number;
  } | null>(null);
  const [bitmapTick, setBitmapTick] = useState(0);

  const active = captions.find((caption) => caption.id === activeId) ?? captions[0];

  useEffect(() => {
    let cancel = false;
    const fontReady = document.fonts
      .load("400 96px Anton", "ĄĆĘŁŃÓŚŹŻTYLKO CHWILĘ")
      .catch(() => undefined);

    function show(bitmap: HTMLCanvasElement) {
      if (cancel) return;
      bitmapRef.current = bitmap;
      setExportSize({ w: bitmap.width, h: bitmap.height });
      setError(null);
      setBitmapTick((tick) => tick + 1);
    }

    if (background.kind === "plate") {
      show(plateCanvas(background.id, 1080));
      void fontReady.then(() => {
        if (!cancel) setBitmapTick((tick) => tick + 1);
      });
      return () => {
        cancel = true;
      };
    }

    const src =
      background.kind === "photo"
        ? (PHOTOS.find((photo) => photo.id === background.id)?.src ?? PHOTOS[0].src)
        : background.url;
    const image = new Image();
    image.decoding = "async";
    image.crossOrigin = "anonymous";
    image.onload = () => {
      show(scaleSource(image, image.naturalWidth, image.naturalHeight));
      void fontReady.then(() => {
        if (!cancel) setBitmapTick((tick) => tick + 1);
      });
    };
    image.onerror = () => {
      if (!cancel) setError("Nie udało się wczytać obrazu. Użyj JPG, PNG albo WEBP.");
    };
    image.src = src;
    return () => {
      cancel = true;
    };
  }, [background]);

  useLayoutEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    blocksRef.current = renderMeme(canvas, bitmapRef.current, captions, uppercase, activeId, true);
  }, [bitmapTick, captions, uppercase, activeId]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const observer = new ResizeObserver(() => {
      const node = canvasRef.current;
      if (!node) return;
      blocksRef.current = renderMeme(node, bitmapRef.current, captions, uppercase, activeId, true);
    });
    observer.observe(canvas);
    return () => observer.disconnect();
  }, [bitmapTick, captions, uppercase, activeId]);

  useEffect(() => {
    if (!status) return;
    const timer = window.setTimeout(() => setStatus(null), 2400);
    return () => window.clearTimeout(timer);
  }, [status]);

  useEffect(() => {
    return () => {
      if (uploadUrlRef.current) URL.revokeObjectURL(uploadUrlRef.current);
    };
  }, []);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.altKey || event.metaKey || event.ctrlKey) return;
      const target = event.target;
      if (target instanceof HTMLElement && (target.tagName === "TEXTAREA" || target.tagName === "INPUT")) return;
      const step = event.shiftKey ? 0.02 : 0.008;
      const delta =
        event.key === "ArrowLeft"
          ? [-step, 0]
          : event.key === "ArrowRight"
            ? [step, 0]
            : event.key === "ArrowUp"
              ? [0, -step]
              : event.key === "ArrowDown"
                ? [0, step]
                : null;
      if (!delta) return;
      event.preventDefault();
      setCaptions((prev) =>
        prev.map((caption) =>
          caption.id === activeId
            ? { ...caption, x: clampUnit(caption.x + delta[0]), y: clampUnit(caption.y + delta[1]) }
            : caption,
        ),
      );
      setPresetId("");
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [activeId]);

  function updateActive(patch: Partial<Caption>) {
    setCaptions((prev) => prev.map((caption) => (caption.id === activeId ? { ...caption, ...patch } : caption)));
    setPresetId("");
  }

  function updateText(id: CaptionId, text: string) {
    setCaptions((prev) => prev.map((caption) => (caption.id === id ? { ...caption, text } : caption)));
  }

  function usePreset(preset: Preset) {
    setCaptions((prev) => applyPreset(prev, preset));
    setUppercase(preset.uppercase);
    setPresetId(preset.id);
  }

  function choosePhoto(id: PhotoId) {
    setBackground({ kind: "photo", id });
    setError(null);
  }

  function choosePlate(id: PlateId) {
    setBackground({ kind: "plate", id });
    setCaptions((prev) => adaptForPlate(prev, id));
    setError(null);
  }

  function takeFile(file: File | undefined) {
    if (!file) return;
    if (!file.type.startsWith("image/")) {
      setError("To nie jest obraz. Wybierz JPG, PNG albo WEBP.");
      return;
    }
    if (file.size > MAX_UPLOAD_BYTES) {
      setError("Plik jest za duży. Maksimum to 20 MB.");
      return;
    }
    const url = URL.createObjectURL(file);
    if (uploadUrlRef.current) URL.revokeObjectURL(uploadUrlRef.current);
    uploadUrlRef.current = url;
    setBackground({ kind: "upload", url, name: file.name });
    setError(null);
    setStatus("Dodano twoje zdjęcie");
  }

  function reset() {
    if (uploadUrlRef.current) {
      URL.revokeObjectURL(uploadUrlRef.current);
      uploadUrlRef.current = null;
    }
    setBackground({ kind: "photo", id: "kot" });
    setCaptions(defaultCaptions());
    setUppercase(true);
    setActiveId("top");
    setPresetId("klasyczny");
    setError(null);
    setStatus("Przywrócono klasyczny układ");
  }

  function download() {
    const exportCanvas = document.createElement("canvas");
    renderMeme(exportCanvas, bitmapRef.current, captions, uppercase, null, false);
    exportCanvas.toBlob((blob) => {
      if (!blob) {
        setStatus("Nie udało się zapisać obrazu");
        return;
      }
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "kadr-mem.png";
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      setStatus("Zapisano kadr-mem.png");
    }, "image/png");
  }

  function toPoint(event: PointerEvent<HTMLCanvasElement>) {
    const canvas = event.currentTarget;
    const rect = canvas.getBoundingClientRect();
    return {
      x: ((event.clientX - rect.left) / Math.max(rect.width, 1)) * canvas.width,
      y: ((event.clientY - rect.top) / Math.max(rect.height, 1)) * canvas.height,
    };
  }

  function padFor(canvas: HTMLCanvasElement) {
    const rect = canvas.getBoundingClientRect();
    return (44 / Math.max(rect.width, 1)) * canvas.width;
  }

  function onPointerDown(event: PointerEvent<HTMLCanvasElement>) {
    const canvas = event.currentTarget;
    const point = toPoint(event);
    const hit = hitTest(blocksRef.current, point.x, point.y, padFor(canvas));
    if (!hit) return;
    const block = blocksRef.current.find((item) => item.id === hit);
    if (!block) return;
    canvas.setPointerCapture(event.pointerId);
    dragRef.current = {
      id: hit,
      dx: point.x - block.cx,
      dy: point.y - block.cy,
      moved: false,
      px: event.clientX,
      py: event.clientY,
    };
    setActiveId(hit);
    canvas.style.cursor = "grabbing";
  }

  function onPointerMove(event: PointerEvent<HTMLCanvasElement>) {
    const canvas = event.currentTarget;
    const point = toPoint(event);
    const drag = dragRef.current;
    if (!drag) {
      const hit = hitTest(blocksRef.current, point.x, point.y, padFor(canvas));
      canvas.style.cursor = hit ? "grab" : "default";
      return;
    }
    if (!drag.moved) {
      if (Math.hypot(event.clientX - drag.px, event.clientY - drag.py) <= 4) return;
      drag.moved = true;
      setPresetId("");
    }
    const x = clampUnit((point.x - drag.dx) / canvas.width);
    const y = clampUnit((point.y - drag.dy) / canvas.height);
    setCaptions((prev) => prev.map((caption) => (caption.id === drag.id ? { ...caption, x, y } : caption)));
  }

  function onPointerUp(event: PointerEvent<HTMLCanvasElement>) {
    const drag = dragRef.current;
    dragRef.current = null;
    event.currentTarget.style.cursor = "grab";
    if (drag && !drag.moved) {
      const field = document.getElementById(drag.id === "top" ? "caption-top" : "caption-bottom");
      if (field instanceof HTMLTextAreaElement) field.focus();
    }
  }

  function onDragOver(event: DragEvent<HTMLDivElement>) {
    if (![...event.dataTransfer.types].includes("Files")) return;
    event.preventDefault();
    setDropping(true);
  }

  function onDragLeave(event: DragEvent<HTMLDivElement>) {
    if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
    setDropping(false);
  }

  function onDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDropping(false);
    takeFile(event.dataTransfer.files?.[0]);
  }

  const previewSrc =
    background.kind === "photo"
      ? PHOTOS.find((photo) => photo.id === background.id)?.src
      : background.kind === "upload"
        ? background.url
        : null;

  const sizePx = Math.round(active.size * exportSize.w);
  const strokePx = Math.round(active.stroke * exportSize.w);

  return (
    <div
      className="flex h-dvh flex-col overflow-hidden bg-bg text-fg"
      onDragOver={onDragOver}
      onDragLeave={onDragLeave}
      onDrop={onDrop}
    >
      <header className="flex shrink-0 items-center justify-between gap-3 border-b border-line px-4 py-3">
        <div className="flex items-center gap-3">
          <span className="grid size-11 place-items-center rounded-md border border-line bg-surface text-primary">
            <Crosshair className="size-5" aria-hidden />
          </span>
          <div>
            <h1 className="font-display text-lg leading-none font-extrabold tracking-wide">KADR</h1>
            <p className="mt-1 text-xs text-muted">Generator memów</p>
          </div>
        </div>
        <div className="hidden items-center gap-2 lg:flex">
          <button type="button" className="btn btn-ghost" onClick={reset}>
            <RotateCcw className="size-4" aria-hidden />
            Resetuj
          </button>
          <button type="button" className="btn btn-primary" onClick={download}>
            <Download className="size-4" aria-hidden />
            Pobierz mem
          </button>
        </div>
      </header>

      <div className="flex min-h-0 flex-1 flex-col lg:flex-row">
        <section className="stage">
          <div className={dropping ? "mat is-dropping max-w-full" : "mat max-w-full"}>
            <span className="mark mark-tl" aria-hidden />
            <span className="mark mark-tr" aria-hidden />
            <span className="mark mark-bl" aria-hidden />
            <span className="mark mark-br" aria-hidden />
            <div className="stack">
              {previewSrc ? <img src={previewSrc} alt="" className="meme-photo" /> : null}
              <canvas
                ref={canvasRef}
                width={1080}
                height={1080}
                className={previewSrc ? "meme-canvas is-overlay" : "meme-canvas"}
                aria-label="Podgląd mema. Przeciągnij napis, aby zmienić położenie."
                onPointerDown={onPointerDown}
                onPointerMove={onPointerMove}
                onPointerUp={onPointerUp}
                onPointerCancel={onPointerUp}
              />
            </div>
            {dropping ? (
              <div className="pointer-events-none absolute inset-3 grid place-items-center rounded-sm bg-bg/85 text-sm font-semibold text-primary">
                Upuść zdjęcie
              </div>
            ) : null}
          </div>
          <p className="mt-3 text-center text-xs text-muted">
            Złap napis i przesuń
            <span className="hidden sm:inline"> · strzałki na klawiaturze</span>
          </p>
          <p className="h-5 text-center text-sm text-primary" role="status">
            {error ?? status ?? ""}
          </p>
        </section>

        <aside className="min-h-0 flex-1 overflow-y-auto border-t border-line px-4 py-4 lg:w-96 lg:shrink-0 lg:flex-none lg:border-t-0 lg:border-l">
          <div className="space-y-7">
            <section className="space-y-3">
              <h2 className="text-xs font-semibold tracking-widest text-muted uppercase">Zdjęcie</h2>
              <button type="button" className="btn btn-ghost btn-block" onClick={() => fileRef.current?.click()}>
                <ImagePlus className="size-4" aria-hidden />
                Wgraj zdjęcie
              </button>
              <input
                ref={fileRef}
                type="file"
                accept="image/jpeg,image/png,image/webp,image/gif"
                className="sr-only"
                onChange={(event) => {
                  takeFile(event.target.files?.[0]);
                  event.target.value = "";
                }}
              />
              <p className="text-xs text-muted">Albo upuść plik na kadr. JPG, PNG, WEBP.</p>
              <div className="-mx-1 flex gap-3 overflow-x-auto px-1 pb-1">
                {background.kind === "upload" ? (
                  <Thumb
                    label={background.name}
                    selected
                    onClick={() => {
                      /* already selected */
                    }}
                  >
                    <img src={background.url} alt="" className="h-full w-full object-cover" />
                  </Thumb>
                ) : null}
                {PHOTOS.map((photo) => (
                  <Thumb
                    key={photo.id}
                    label={photo.label}
                    selected={background.kind === "photo" && background.id === photo.id}
                    onClick={() => choosePhoto(photo.id)}
                  >
                    <img src={photo.src} alt="" className="h-full w-full object-cover" />
                  </Thumb>
                ))}
                {PLATES.map((plate) => (
                  <Thumb
                    key={plate.id}
                    label={plate.label}
                    selected={background.kind === "plate" && background.id === plate.id}
                    onClick={() => choosePlate(plate.id)}
                  >
                    <PlateThumb id={plate.id} />
                  </Thumb>
                ))}
              </div>
            </section>

            <section className="space-y-3">
              <h2 className="text-xs font-semibold tracking-widest text-muted uppercase">Napis</h2>
              <Field
                id="caption-top"
                label="Góra"
                active={activeId === "top"}
                caps={uppercase}
                value={captions[0].text}
                onFocus={() => setActiveId("top")}
                onChange={(text) => updateText("top", text)}
              />
              <Field
                id="caption-bottom"
                label="Dół"
                active={activeId === "bottom"}
                caps={uppercase}
                value={captions[1].text}
                onFocus={() => setActiveId("bottom")}
                onChange={(text) => updateText("bottom", text)}
              />
              <button
                type="button"
                className={uppercase ? "btn btn-primary btn-block" : "btn btn-ghost btn-block"}
                aria-pressed={uppercase}
                onClick={() => {
                  setUppercase((value) => !value);
                  setPresetId("");
                }}
              >
                Wielkie litery
              </button>
            </section>

            <section className="space-y-4">
              <h2 className="text-xs font-semibold tracking-widest text-muted uppercase">
                Wygląd — {activeId === "top" ? "góra" : "dół"}
              </h2>
              <div>
                <div className="flex items-baseline justify-between">
                  <label htmlFor="font-size" className="text-sm font-medium">
                    Rozmiar
                  </label>
                  <span className="text-sm text-muted tabular-nums">{sizePx} px</span>
                </div>
                <input
                  id="font-size"
                  className="slider"
                  type="range"
                  min={0.04}
                  max={0.16}
                  step={0.002}
                  value={active.size}
                  onChange={(event) => updateActive({ size: Number(event.target.value) })}
                />
              </div>
              <div>
                <div className="flex items-baseline justify-between">
                  <label htmlFor="stroke" className="text-sm font-medium">
                    Kontur
                  </label>
                  <span className="text-sm text-muted tabular-nums">{strokePx} px</span>
                </div>
                <input
                  id="stroke"
                  className="slider"
                  type="range"
                  min={0}
                  max={0.03}
                  step={0.001}
                  value={active.stroke}
                  onChange={(event) => updateActive({ stroke: Number(event.target.value) })}
                />
              </div>
              <div className="space-y-2">
                <p className="text-sm font-medium">Kolor liter</p>
                <div className="flex gap-2">
                  {FILL_SWATCHES.map((swatch) => (
                    <button
                      key={swatch.id}
                      type="button"
                      className={swatch.value === active.fill ? "swatch is-on" : "swatch"}
                      style={{ backgroundColor: swatch.value }}
                      aria-label={`Kolor liter: ${swatch.label}`}
                      aria-pressed={swatch.value === active.fill}
                      onClick={() => updateActive({ fill: swatch.value })}
                    />
                  ))}
                </div>
              </div>
              <div className="space-y-2">
                <p className="text-sm font-medium">Kolor konturu</p>
                <div className="flex gap-2">
                  {STROKE_SWATCHES.map((swatch) => (
                    <button
                      key={swatch.id}
                      type="button"
                      className={swatch.value === active.outline ? "swatch is-on" : "swatch"}
                      style={{ backgroundColor: swatch.value }}
                      aria-label={`Kolor konturu: ${swatch.label}`}
                      aria-pressed={swatch.value === active.outline}
                      onClick={() => updateActive({ outline: swatch.value })}
                    />
                  ))}
                </div>
              </div>
            </section>

            <section className="space-y-3 pb-2">
              <h2 className="text-xs font-semibold tracking-widest text-muted uppercase">Układ</h2>
              <div className="flex flex-wrap gap-2">
                {PRESETS.map((preset) => (
                  <button
                    key={preset.id}
                    type="button"
                    className={presetId === preset.id ? "btn btn-primary" : "btn btn-ghost"}
                    aria-pressed={presetId === preset.id}
                    title={preset.detail}
                    onClick={() => usePreset(preset)}
                  >
                    {preset.label}
                  </button>
                ))}
              </div>
              <p className="text-xs text-pretty text-muted">
                {PRESETS.find((preset) => preset.id === presetId)?.detail ??
                  "Własne ustawienie. Wybierz układ, żeby wrócić do klasyki."}
              </p>
            </section>
          </div>
        </aside>
      </div>

      <div className="dock flex shrink-0 gap-2 border-t border-line bg-bg px-3 pt-3 lg:hidden">
        <button type="button" className="btn btn-ghost flex-1" onClick={reset}>
          <RotateCcw className="size-4" aria-hidden />
          Resetuj
        </button>
        <button type="button" className="btn btn-primary flex-1" onClick={download}>
          <Download className="size-4" aria-hidden />
          Pobierz
        </button>
      </div>
    </div>
  );
}

function Field({
  id,
  label,
  active,
  caps,
  value,
  onFocus,
  onChange,
}: {
  id: string;
  label: string;
  active: boolean;
  caps: boolean;
  value: string;
  onFocus: () => void;
  onChange: (text: string) => void;
}) {
  return (
    <label className="block space-y-1.5" htmlFor={id}>
      <span className={active ? "text-sm font-semibold text-primary" : "text-sm font-semibold"}>{label}</span>
      <textarea
        id={id}
        className={caps ? (active ? "field is-caps is-active" : "field is-caps") : active ? "field is-active" : "field"}
        rows={2}
        maxLength={180}
        spellCheck={false}
        autoCorrect="off"
        value={value}
        onFocus={onFocus}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function Thumb({
  label,
  selected,
  onClick,
  children,
}: {
  label: string;
  selected: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button type="button" onClick={onClick} aria-pressed={selected} className="flex w-16 shrink-0 flex-col items-center gap-1">
      <span
        className={
          selected
            ? "block size-16 overflow-hidden rounded-md border-2 border-primary"
            : "block size-16 overflow-hidden rounded-md border border-line"
        }
      >
        {children}
      </span>
      <span className={selected ? "max-w-16 truncate text-xs font-semibold text-primary" : "max-w-16 truncate text-xs text-muted"}>
        {label}
      </span>
    </button>
  );
}

function PlateThumb({ id }: { id: PlateId }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;
    drawPlate(ctx, id, canvas.width, canvas.height);
  }, [id]);
  return <canvas ref={ref} width={96} height={96} className="h-full w-full" aria-hidden />;
}
