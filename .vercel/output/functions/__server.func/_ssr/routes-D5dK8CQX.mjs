import { i as __toESM } from "../_runtime.mjs";
import { K as require_react, b as require_jsx_runtime } from "../_libs/@tanstack/react-router+[...].mjs";
import { a as Crosshair, i as Download, n as RotateCcw, r as ImagePlus } from "../_libs/lucide-react.mjs";
//#region node_modules/.nitro/vite/services/ssr/assets/routes-D5dK8CQX.js
var import_react = /* @__PURE__ */ __toESM(require_react());
var import_jsx_runtime = require_jsx_runtime();
var PHOTOS = [
	{
		id: "kot",
		label: "Kot",
		src: "/mem-templates/kot.jpg"
	},
	{
		id: "pies",
		label: "Pies",
		src: "/mem-templates/pies.jpg"
	},
	{
		id: "kawa",
		label: "Kawa",
		src: "/mem-templates/kawa.jpg"
	},
	{
		id: "szczyt",
		label: "Szczyt",
		src: "/mem-templates/szczyt.jpg"
	}
];
var PLATES = [
	{
		id: "scena",
		label: "Scena"
	},
	{
		id: "kino",
		label: "Kino"
	},
	{
		id: "pop",
		label: "Pop"
	},
	{
		id: "podzial",
		label: "Podział"
	},
	{
		id: "papier",
		label: "Papier"
	}
];
var FILL_SWATCHES = [
	{
		id: "bialy",
		label: "Biały",
		value: "#ffffff"
	},
	{
		id: "zolty",
		label: "Żółty",
		value: "#f0c14b"
	},
	{
		id: "czarny",
		label: "Czarny",
		value: "#14120e"
	},
	{
		id: "czerwony",
		label: "Czerwony",
		value: "#e23d2b"
	}
];
var STROKE_SWATCHES = [
	{
		id: "czarny",
		label: "Czarny",
		value: "#14120e"
	},
	{
		id: "bialy",
		label: "Biały",
		value: "#ffffff"
	},
	{
		id: "zolty",
		label: "Żółty",
		value: "#f0c14b"
	},
	{
		id: "czerwony",
		label: "Czerwony",
		value: "#e23d2b"
	}
];
var CLASSIC_INK = {
	fill: "#ffffff",
	outline: "#14120e"
};
var PRESETS = [
	{
		id: "klasyczny",
		label: "Klasyczny",
		detail: "Białe litery, gruby czarny kontur, góra i dół",
		uppercase: true,
		top: {
			...CLASSIC_INK,
			x: .5,
			y: .13,
			size: .086,
			stroke: .015
		},
		bottom: {
			...CLASSIC_INK,
			x: .5,
			y: .87,
			size: .086,
			stroke: .015
		}
	},
	{
		id: "gruby",
		label: "Gruby kontur",
		detail: "Większa czcionka i mocna obwódka",
		uppercase: true,
		top: {
			x: .5,
			y: .14,
			size: .1,
			stroke: .028
		},
		bottom: {
			x: .5,
			y: .86,
			size: .1,
			stroke: .028
		}
	},
	{
		id: "cienki",
		label: "Cienki",
		detail: "Mniejsze litery, delikatny kontur",
		uppercase: true,
		top: {
			x: .5,
			y: .12,
			size: .058,
			stroke: .004
		},
		bottom: {
			x: .5,
			y: .88,
			size: .058,
			stroke: .004
		}
	},
	{
		id: "gora",
		label: "Góra",
		detail: "Duży napis u góry, mały podpis na dole",
		uppercase: true,
		top: {
			x: .5,
			y: .16,
			size: .118,
			stroke: .016
		},
		bottom: {
			x: .5,
			y: .9,
			size: .046,
			stroke: .008
		}
	},
	{
		id: "dol",
		label: "Dół",
		detail: "Duży napis na dole, krótki wstęp u góry",
		uppercase: true,
		top: {
			x: .5,
			y: .1,
			size: .046,
			stroke: .008
		},
		bottom: {
			x: .5,
			y: .82,
			size: .118,
			stroke: .016
		}
	},
	{
		id: "srodek",
		label: "Środek",
		detail: "Oba napisy złożone przy środku kadru",
		uppercase: true,
		top: {
			x: .5,
			y: .4,
			size: .076,
			stroke: .012
		},
		bottom: {
			x: .5,
			y: .58,
			size: .076,
			stroke: .012
		}
	}
];
function defaultCaptions() {
	const preset = PRESETS[0];
	return applyPreset([{
		id: "top",
		text: "TYLKO NA CHWILĘ",
		x: .5,
		y: .13,
		size: .086,
		stroke: .015,
		fill: "#ffffff",
		outline: "#14120e"
	}, {
		id: "bottom",
		text: "TRZY GODZINY PÓŹNIEJ",
		x: .5,
		y: .87,
		size: .086,
		stroke: .015,
		fill: "#ffffff",
		outline: "#14120e"
	}], preset);
}
function applyPreset(captions, preset) {
	return captions.map((caption) => {
		const patch = caption.id === "top" ? preset.top : preset.bottom;
		return {
			...caption,
			...patch
		};
	});
}
function adaptForPlate(captions, plateId) {
	if (plateId !== "papier") return captions;
	return captions.map((caption) => caption.fill === "#ffffff" && caption.outline === "#14120e" ? {
		...caption,
		fill: "#14120e",
		outline: "#ffffff"
	} : caption);
}
var plateCache = /* @__PURE__ */ new Map();
function plateCanvas(id, size = 1080) {
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
function clampByte(value) {
	return Math.max(0, Math.min(255, value));
}
function grain(ctx, w, h, amount) {
	const image = ctx.getImageData(0, 0, w, h);
	const data = image.data;
	let seed = 1337;
	const next = () => {
		seed = seed * 16807 % 2147483647;
		return (seed & 65535) / 65535;
	};
	const step = w > 400 ? 8 : 4;
	for (let i = 0; i < data.length; i += step) {
		const n = (next() - .5) * amount;
		data[i] = clampByte(data[i] + n);
		data[i + 1] = clampByte(data[i + 1] + n);
		data[i + 2] = clampByte(data[i + 2] + n);
	}
	ctx.putImageData(image, 0, 0);
}
function drawPlate(ctx, id, w, h) {
	if (id === "scena") {
		const glow = ctx.createRadialGradient(w * .5, h * .46, w * .04, w * .5, h * .5, w * .72);
		glow.addColorStop(0, "#5c5346");
		glow.addColorStop(.42, "#2a261f");
		glow.addColorStop(1, "#12110e");
		ctx.fillStyle = glow;
		ctx.fillRect(0, 0, w, h);
		ctx.strokeStyle = "rgba(240, 193, 75, 0.7)";
		ctx.lineWidth = Math.max(2, w * .004);
		ctx.beginPath();
		ctx.arc(w / 2, h * .46, w * .25, 0, Math.PI * 2);
		ctx.stroke();
		grain(ctx, w, h, 16);
		return;
	}
	if (id === "papier") {
		ctx.fillStyle = "#e7dcc6";
		ctx.fillRect(0, 0, w, h);
		const vignette = ctx.createRadialGradient(w / 2, h / 2, w * .15, w / 2, h / 2, w * .72);
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
		const disc = ctx.createRadialGradient(w / 2, h * .5, w * .02, w / 2, h * .5, w * .34);
		disc.addColorStop(0, "#ffe7a3");
		disc.addColorStop(.72, "#f0c14b");
		disc.addColorStop(1, "#c4922a");
		ctx.fillStyle = disc;
		ctx.beginPath();
		ctx.arc(w / 2, h * .5, w * .3, 0, Math.PI * 2);
		ctx.fill();
		ctx.fillStyle = "#f0c14b";
		const gap = Math.max(7, Math.round(w / 42));
		for (let y = gap / 2; y < h; y += gap) for (let x = gap / 2; x < w; x += gap) {
			const dist = Math.hypot(x - w / 2, y - h * .5) / (w * .5);
			if (dist < .64 || dist > .96) continue;
			const radius = (1 - dist) * gap * .42;
			ctx.beginPath();
			ctx.arc(x, y, radius, 0, Math.PI * 2);
			ctx.fill();
		}
		return;
	}
	if (id === "podzial") {
		ctx.fillStyle = "#16140f";
		ctx.fillRect(0, 0, w / 2, h);
		ctx.fillStyle = "#e7dcc6";
		ctx.fillRect(w / 2, 0, w / 2, h);
		ctx.fillStyle = "#f0c14b";
		const bar = Math.max(3, w * .012);
		ctx.fillRect(w / 2 - bar / 2, 0, bar, h);
		grain(ctx, w, h, 12);
		return;
	}
	ctx.fillStyle = "#100e0c";
	ctx.fillRect(0, 0, w, h);
	const band = ctx.createLinearGradient(0, h * .2, 0, h * .8);
	band.addColorStop(0, "#3a2e22");
	band.addColorStop(.48, "#8a6844");
	band.addColorStop(1, "#241c16");
	ctx.fillStyle = band;
	ctx.fillRect(0, h * .2, w, h * .6);
	ctx.fillStyle = "#f0c14b";
	const hair = Math.max(2, h * .004);
	ctx.fillRect(0, h * .2, w, hair);
	ctx.fillRect(0, h * .8 - hair, w, hair);
	grain(ctx, w, h, 14);
}
function scaleSource(source, sw, sh, max = 1280) {
	const scale = Math.min(1, max / Math.max(sw, sh));
	const canvas = document.createElement("canvas");
	canvas.width = Math.max(1, Math.round(sw * scale));
	canvas.height = Math.max(1, Math.round(sh * scale));
	const ctx = canvas.getContext("2d");
	if (!ctx) return canvas;
	ctx.drawImage(source, 0, 0, canvas.width, canvas.height);
	return canvas;
}
function displayText(text, uppercase) {
	const clean = text.replace(/\r/g, "");
	return uppercase ? clean.toLocaleUpperCase("pl") : clean;
}
function wrapLines(ctx, text, maxWidth) {
	const lines = [];
	for (const paragraph of text.split("\n")) {
		const words = paragraph.split(/\s+/).filter(Boolean);
		if (words.length === 0) {
			lines.push("");
			continue;
		}
		let current = "";
		const pushWord = (word) => {
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
				} else chunk = trial;
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
function layoutCaption(ctx, caption, width, height, uppercase) {
	const text = displayText(caption.text, uppercase);
	if (!text.trim()) return null;
	const fontPx = Math.max(14, caption.size * width);
	const strokePx = Math.max(0, caption.stroke * width);
	ctx.font = `400 ${fontPx}px Anton, "Arial Narrow", Impact, sans-serif`;
	const lines = wrapLines(ctx, text, width * .9);
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
		lineH
	};
}
function drawBlock(ctx, caption, block) {
	ctx.save();
	ctx.font = `400 ${block.fontPx}px Anton, "Arial Narrow", Impact, sans-serif`;
	ctx.textAlign = "center";
	ctx.textBaseline = "middle";
	ctx.lineJoin = "round";
	ctx.miterLimit = 2;
	ctx.fillStyle = caption.fill;
	ctx.strokeStyle = caption.outline;
	ctx.lineWidth = block.strokePx;
	let y = block.cy - block.lines.length * block.lineH / 2 + block.lineH / 2;
	for (const line of block.lines) {
		if (block.strokePx > .4) ctx.strokeText(line, block.cx, y);
		ctx.fillText(line, block.cx, y);
		y += block.lineH;
	}
	ctx.restore();
}
function guideColor() {
	if (typeof document === "undefined") return "#f0c14b";
	return getComputedStyle(document.documentElement).getPropertyValue("--color-primary").trim() || "#f0c14b";
}
function drawGuide(ctx, block, canvasWidth, cssWidth) {
	const scale = canvasWidth / Math.max(cssWidth, 1);
	const pad = 8 * scale;
	ctx.save();
	ctx.strokeStyle = guideColor();
	ctx.lineWidth = Math.max(2, 1.5 * scale);
	ctx.setLineDash([7 * scale, 5 * scale]);
	ctx.strokeRect(block.left - pad, block.top - pad, block.width + pad * 2, block.height + pad * 2);
	ctx.restore();
}
function renderMeme(canvas, bitmap, captions, uppercase, activeId, showGuide) {
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
	const blocks = [];
	for (const caption of captions) {
		const block = layoutCaption(ctx, caption, width, height, uppercase);
		if (!block) continue;
		drawBlock(ctx, caption, block);
		blocks.push(block);
		if (showGuide && caption.id === activeId) drawGuide(ctx, block, width, cssWidth);
	}
	return blocks;
}
function hitTest(blocks, x, y, pad) {
	for (let index = blocks.length - 1; index >= 0; index -= 1) {
		const block = blocks[index];
		if (x >= block.left - pad && x <= block.left + block.width + pad && y >= block.top - pad && y <= block.top + block.height + pad) return block.id;
	}
	return null;
}
function clampUnit(value) {
	return Math.min(.94, Math.max(.06, value));
}
var MAX_UPLOAD_BYTES = 20971520;
function MemeStudio() {
	const [background, setBackground] = (0, import_react.useState)({
		kind: "photo",
		id: "kot"
	});
	const [captions, setCaptions] = (0, import_react.useState)(defaultCaptions);
	const [uppercase, setUppercase] = (0, import_react.useState)(true);
	const [activeId, setActiveId] = (0, import_react.useState)("top");
	const [presetId, setPresetId] = (0, import_react.useState)("klasyczny");
	const [status, setStatus] = (0, import_react.useState)(null);
	const [error, setError] = (0, import_react.useState)(null);
	const [dropping, setDropping] = (0, import_react.useState)(false);
	const [exportSize, setExportSize] = (0, import_react.useState)({
		w: 1080,
		h: 1080
	});
	const canvasRef = (0, import_react.useRef)(null);
	const bitmapRef = (0, import_react.useRef)(null);
	const blocksRef = (0, import_react.useRef)([]);
	const fileRef = (0, import_react.useRef)(null);
	const uploadUrlRef = (0, import_react.useRef)(null);
	const dragRef = (0, import_react.useRef)(null);
	const [bitmapTick, setBitmapTick] = (0, import_react.useState)(0);
	const active = captions.find((caption) => caption.id === activeId) ?? captions[0];
	(0, import_react.useEffect)(() => {
		let cancel = false;
		const fontReady = document.fonts.load("400 96px Anton", "ĄĆĘŁŃÓŚŹŻTYLKO CHWILĘ").catch(() => void 0);
		function show(bitmap) {
			if (cancel) return;
			bitmapRef.current = bitmap;
			setExportSize({
				w: bitmap.width,
				h: bitmap.height
			});
			setError(null);
			setBitmapTick((tick) => tick + 1);
		}
		if (background.kind === "plate") {
			show(plateCanvas(background.id, 1080));
			fontReady.then(() => {
				if (!cancel) setBitmapTick((tick) => tick + 1);
			});
			return () => {
				cancel = true;
			};
		}
		const src = background.kind === "photo" ? PHOTOS.find((photo) => photo.id === background.id)?.src ?? PHOTOS[0].src : background.url;
		const image = new Image();
		image.decoding = "async";
		image.crossOrigin = "anonymous";
		image.onload = () => {
			show(scaleSource(image, image.naturalWidth, image.naturalHeight));
			fontReady.then(() => {
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
	(0, import_react.useLayoutEffect)(() => {
		const canvas = canvasRef.current;
		if (!canvas) return;
		blocksRef.current = renderMeme(canvas, bitmapRef.current, captions, uppercase, activeId, true);
	}, [
		bitmapTick,
		captions,
		uppercase,
		activeId
	]);
	(0, import_react.useEffect)(() => {
		const canvas = canvasRef.current;
		if (!canvas) return;
		const observer = new ResizeObserver(() => {
			const node = canvasRef.current;
			if (!node) return;
			blocksRef.current = renderMeme(node, bitmapRef.current, captions, uppercase, activeId, true);
		});
		observer.observe(canvas);
		return () => observer.disconnect();
	}, [
		bitmapTick,
		captions,
		uppercase,
		activeId
	]);
	(0, import_react.useEffect)(() => {
		if (!status) return;
		const timer = window.setTimeout(() => setStatus(null), 2400);
		return () => window.clearTimeout(timer);
	}, [status]);
	(0, import_react.useEffect)(() => {
		return () => {
			if (uploadUrlRef.current) URL.revokeObjectURL(uploadUrlRef.current);
		};
	}, []);
	(0, import_react.useEffect)(() => {
		function onKey(event) {
			if (event.altKey || event.metaKey || event.ctrlKey) return;
			const target = event.target;
			if (target instanceof HTMLElement && (target.tagName === "TEXTAREA" || target.tagName === "INPUT")) return;
			const step = event.shiftKey ? .02 : .008;
			const delta = event.key === "ArrowLeft" ? [-step, 0] : event.key === "ArrowRight" ? [step, 0] : event.key === "ArrowUp" ? [0, -step] : event.key === "ArrowDown" ? [0, step] : null;
			if (!delta) return;
			event.preventDefault();
			setCaptions((prev) => prev.map((caption) => caption.id === activeId ? {
				...caption,
				x: clampUnit(caption.x + delta[0]),
				y: clampUnit(caption.y + delta[1])
			} : caption));
			setPresetId("");
		}
		window.addEventListener("keydown", onKey);
		return () => window.removeEventListener("keydown", onKey);
	}, [activeId]);
	function updateActive(patch) {
		setCaptions((prev) => prev.map((caption) => caption.id === activeId ? {
			...caption,
			...patch
		} : caption));
		setPresetId("");
	}
	function updateText(id, text) {
		setCaptions((prev) => prev.map((caption) => caption.id === id ? {
			...caption,
			text
		} : caption));
	}
	function usePreset(preset) {
		setCaptions((prev) => applyPreset(prev, preset));
		setUppercase(preset.uppercase);
		setPresetId(preset.id);
	}
	function choosePhoto(id) {
		setBackground({
			kind: "photo",
			id
		});
		setError(null);
	}
	function choosePlate(id) {
		setBackground({
			kind: "plate",
			id
		});
		setCaptions((prev) => adaptForPlate(prev, id));
		setError(null);
	}
	function takeFile(file) {
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
		setBackground({
			kind: "upload",
			url,
			name: file.name
		});
		setError(null);
		setStatus("Dodano twoje zdjęcie");
	}
	function reset() {
		if (uploadUrlRef.current) {
			URL.revokeObjectURL(uploadUrlRef.current);
			uploadUrlRef.current = null;
		}
		setBackground({
			kind: "photo",
			id: "kot"
		});
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
	function toPoint(event) {
		const canvas = event.currentTarget;
		const rect = canvas.getBoundingClientRect();
		return {
			x: (event.clientX - rect.left) / Math.max(rect.width, 1) * canvas.width,
			y: (event.clientY - rect.top) / Math.max(rect.height, 1) * canvas.height
		};
	}
	function padFor(canvas) {
		const rect = canvas.getBoundingClientRect();
		return 44 / Math.max(rect.width, 1) * canvas.width;
	}
	function onPointerDown(event) {
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
			py: event.clientY
		};
		setActiveId(hit);
		canvas.style.cursor = "grabbing";
	}
	function onPointerMove(event) {
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
		setCaptions((prev) => prev.map((caption) => caption.id === drag.id ? {
			...caption,
			x,
			y
		} : caption));
	}
	function onPointerUp(event) {
		const drag = dragRef.current;
		dragRef.current = null;
		event.currentTarget.style.cursor = "grab";
		if (drag && !drag.moved) {
			const field = document.getElementById(drag.id === "top" ? "caption-top" : "caption-bottom");
			if (field instanceof HTMLTextAreaElement) field.focus();
		}
	}
	function onDragOver(event) {
		if (![...event.dataTransfer.types].includes("Files")) return;
		event.preventDefault();
		setDropping(true);
	}
	function onDragLeave(event) {
		if (event.currentTarget.contains(event.relatedTarget)) return;
		setDropping(false);
	}
	function onDrop(event) {
		event.preventDefault();
		setDropping(false);
		takeFile(event.dataTransfer.files?.[0]);
	}
	const previewSrc = background.kind === "photo" ? PHOTOS.find((photo) => photo.id === background.id)?.src : background.kind === "upload" ? background.url : null;
	const sizePx = Math.round(active.size * exportSize.w);
	const strokePx = Math.round(active.stroke * exportSize.w);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
		className: "flex h-dvh flex-col overflow-hidden bg-bg text-fg",
		onDragOver,
		onDragLeave,
		onDrop,
		children: [
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("header", {
				className: "flex shrink-0 items-center justify-between gap-3 border-b border-line px-4 py-3",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "flex items-center gap-3",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
						className: "grid size-11 place-items-center rounded-md border border-line bg-surface text-primary",
						children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Crosshair, {
							className: "size-5",
							"aria-hidden": true
						})
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h1", {
						className: "font-display text-lg leading-none font-extrabold tracking-wide",
						children: "KADR"
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
						className: "mt-1 text-xs text-muted",
						children: "Generator memów"
					})] })]
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
					className: "hidden items-center gap-2 lg:flex",
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
						type: "button",
						className: "btn btn-ghost",
						onClick: reset,
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(RotateCcw, {
							className: "size-4",
							"aria-hidden": true
						}), "Resetuj"]
					}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
						type: "button",
						className: "btn btn-primary",
						onClick: download,
						children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Download, {
							className: "size-4",
							"aria-hidden": true
						}), "Pobierz mem"]
					})]
				})]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "flex min-h-0 flex-1 flex-col lg:flex-row",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
					className: "stage",
					children: [
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
							className: dropping ? "mat is-dropping max-w-full" : "mat max-w-full",
							children: [
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
									className: "mark mark-tl",
									"aria-hidden": true
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
									className: "mark mark-tr",
									"aria-hidden": true
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
									className: "mark mark-bl",
									"aria-hidden": true
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
									className: "mark mark-br",
									"aria-hidden": true
								}),
								/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
									className: "stack",
									children: [previewSrc ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
										src: previewSrc,
										alt: "",
										className: "meme-photo"
									}) : null, /* @__PURE__ */ (0, import_jsx_runtime.jsx)("canvas", {
										ref: canvasRef,
										width: 1080,
										height: 1080,
										className: previewSrc ? "meme-canvas is-overlay" : "meme-canvas",
										"aria-label": "Podgląd mema. Przeciągnij napis, aby zmienić położenie.",
										onPointerDown,
										onPointerMove,
										onPointerUp,
										onPointerCancel: onPointerUp
									})]
								}),
								dropping ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
									className: "pointer-events-none absolute inset-3 grid place-items-center rounded-sm bg-bg/85 text-sm font-semibold text-primary",
									children: "Upuść zdjęcie"
								}) : null
							]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("p", {
							className: "mt-3 text-center text-xs text-muted",
							children: ["Złap napis i przesuń", /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
								className: "hidden sm:inline",
								children: " · strzałki na klawiaturze"
							})]
						}),
						/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
							className: "h-5 text-center text-sm text-primary",
							role: "status",
							children: error ?? status ?? ""
						})
					]
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("aside", {
					className: "min-h-0 flex-1 overflow-y-auto border-t border-line px-4 py-4 lg:w-96 lg:shrink-0 lg:flex-none lg:border-t-0 lg:border-l",
					children: /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
						className: "space-y-7",
						children: [
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
								className: "space-y-3",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
										className: "text-xs font-semibold tracking-widest text-muted uppercase",
										children: "Zdjęcie"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
										type: "button",
										className: "btn btn-ghost btn-block",
										onClick: () => fileRef.current?.click(),
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(ImagePlus, {
											className: "size-4",
											"aria-hidden": true
										}), "Wgraj zdjęcie"]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
										ref: fileRef,
										type: "file",
										accept: "image/jpeg,image/png,image/webp,image/gif",
										className: "sr-only",
										onChange: (event) => {
											takeFile(event.target.files?.[0]);
											event.target.value = "";
										}
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
										className: "text-xs text-muted",
										children: "Albo upuść plik na kadr. JPG, PNG, WEBP."
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
										className: "-mx-1 flex gap-3 overflow-x-auto px-1 pb-1",
										children: [
											background.kind === "upload" ? /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Thumb, {
												label: background.name,
												selected: true,
												onClick: () => {},
												children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
													src: background.url,
													alt: "",
													className: "h-full w-full object-cover"
												})
											}) : null,
											PHOTOS.map((photo) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Thumb, {
												label: photo.label,
												selected: background.kind === "photo" && background.id === photo.id,
												onClick: () => choosePhoto(photo.id),
												children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)("img", {
													src: photo.src,
													alt: "",
													className: "h-full w-full object-cover"
												})
											}, photo.id)),
											PLATES.map((plate) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)(Thumb, {
												label: plate.label,
												selected: background.kind === "plate" && background.id === plate.id,
												onClick: () => choosePlate(plate.id),
												children: /* @__PURE__ */ (0, import_jsx_runtime.jsx)(PlateThumb, { id: plate.id })
											}, plate.id))
										]
									})
								]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
								className: "space-y-3",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
										className: "text-xs font-semibold tracking-widest text-muted uppercase",
										children: "Napis"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
										id: "caption-top",
										label: "Góra",
										active: activeId === "top",
										caps: uppercase,
										value: captions[0].text,
										onFocus: () => setActiveId("top"),
										onChange: (text) => updateText("top", text)
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Field, {
										id: "caption-bottom",
										label: "Dół",
										active: activeId === "bottom",
										caps: uppercase,
										value: captions[1].text,
										onFocus: () => setActiveId("bottom"),
										onChange: (text) => updateText("bottom", text)
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
										type: "button",
										className: uppercase ? "btn btn-primary btn-block" : "btn btn-ghost btn-block",
										"aria-pressed": uppercase,
										onClick: () => {
											setUppercase((value) => !value);
											setPresetId("");
										},
										children: "Wielkie litery"
									})
								]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
								className: "space-y-4",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("h2", {
										className: "text-xs font-semibold tracking-widest text-muted uppercase",
										children: ["Wygląd — ", activeId === "top" ? "góra" : "dół"]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
										className: "flex items-baseline justify-between",
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("label", {
											htmlFor: "font-size",
											className: "text-sm font-medium",
											children: "Rozmiar"
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
											className: "text-sm text-muted tabular-nums",
											children: [sizePx, " px"]
										})]
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
										id: "font-size",
										className: "slider",
										type: "range",
										min: .04,
										max: .16,
										step: .002,
										value: active.size,
										onChange: (event) => updateActive({ size: Number(event.target.value) })
									})] }),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", { children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
										className: "flex items-baseline justify-between",
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("label", {
											htmlFor: "stroke",
											className: "text-sm font-medium",
											children: "Kontur"
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("span", {
											className: "text-sm text-muted tabular-nums",
											children: [strokePx, " px"]
										})]
									}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("input", {
										id: "stroke",
										className: "slider",
										type: "range",
										min: 0,
										max: .03,
										step: .001,
										value: active.stroke,
										onChange: (event) => updateActive({ stroke: Number(event.target.value) })
									})] }),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
										className: "space-y-2",
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
											className: "text-sm font-medium",
											children: "Kolor liter"
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
											className: "flex gap-2",
											children: FILL_SWATCHES.map((swatch) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
												type: "button",
												className: swatch.value === active.fill ? "swatch is-on" : "swatch",
												style: { backgroundColor: swatch.value },
												"aria-label": `Kolor liter: ${swatch.label}`,
												"aria-pressed": swatch.value === active.fill,
												onClick: () => updateActive({ fill: swatch.value })
											}, swatch.id))
										})]
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
										className: "space-y-2",
										children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
											className: "text-sm font-medium",
											children: "Kolor konturu"
										}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
											className: "flex gap-2",
											children: STROKE_SWATCHES.map((swatch) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
												type: "button",
												className: swatch.value === active.outline ? "swatch is-on" : "swatch",
												style: { backgroundColor: swatch.value },
												"aria-label": `Kolor konturu: ${swatch.label}`,
												"aria-pressed": swatch.value === active.outline,
												onClick: () => updateActive({ outline: swatch.value })
											}, swatch.id))
										})]
									})
								]
							}),
							/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("section", {
								className: "space-y-3 pb-2",
								children: [
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("h2", {
										className: "text-xs font-semibold tracking-widest text-muted uppercase",
										children: "Układ"
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("div", {
										className: "flex flex-wrap gap-2",
										children: PRESETS.map((preset) => /* @__PURE__ */ (0, import_jsx_runtime.jsx)("button", {
											type: "button",
											className: presetId === preset.id ? "btn btn-primary" : "btn btn-ghost",
											"aria-pressed": presetId === preset.id,
											title: preset.detail,
											onClick: () => usePreset(preset),
											children: preset.label
										}, preset.id))
									}),
									/* @__PURE__ */ (0, import_jsx_runtime.jsx)("p", {
										className: "text-xs text-pretty text-muted",
										children: PRESETS.find((preset) => preset.id === presetId)?.detail ?? "Własne ustawienie. Wybierz układ, żeby wrócić do klasyki."
									})
								]
							})
						]
					})
				})]
			}),
			/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("div", {
				className: "dock flex shrink-0 gap-2 border-t border-line bg-bg px-3 pt-3 lg:hidden",
				children: [/* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
					type: "button",
					className: "btn btn-ghost flex-1",
					onClick: reset,
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(RotateCcw, {
						className: "size-4",
						"aria-hidden": true
					}), "Resetuj"]
				}), /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
					type: "button",
					className: "btn btn-primary flex-1",
					onClick: download,
					children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)(Download, {
						className: "size-4",
						"aria-hidden": true
					}), "Pobierz"]
				})]
			})
		]
	});
}
function Field({ id, label, active, caps, value, onFocus, onChange }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("label", {
		className: "block space-y-1.5",
		htmlFor: id,
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: active ? "text-sm font-semibold text-primary" : "text-sm font-semibold",
			children: label
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("textarea", {
			id,
			className: caps ? active ? "field is-caps is-active" : "field is-caps" : active ? "field is-active" : "field",
			rows: 2,
			maxLength: 180,
			spellCheck: false,
			autoCorrect: "off",
			value,
			onFocus,
			onChange: (event) => onChange(event.target.value)
		})]
	});
}
function Thumb({ label, selected, onClick, children }) {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsxs)("button", {
		type: "button",
		onClick,
		"aria-pressed": selected,
		className: "flex w-16 shrink-0 flex-col items-center gap-1",
		children: [/* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: selected ? "block size-16 overflow-hidden rounded-md border-2 border-primary" : "block size-16 overflow-hidden rounded-md border border-line",
			children
		}), /* @__PURE__ */ (0, import_jsx_runtime.jsx)("span", {
			className: selected ? "max-w-16 truncate text-xs font-semibold text-primary" : "max-w-16 truncate text-xs text-muted",
			children: label
		})]
	});
}
function PlateThumb({ id }) {
	const ref = (0, import_react.useRef)(null);
	(0, import_react.useEffect)(() => {
		const canvas = ref.current;
		if (!canvas) return;
		const ctx = canvas.getContext("2d");
		if (!ctx) return;
		drawPlate(ctx, id, canvas.width, canvas.height);
	}, [id]);
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)("canvas", {
		ref,
		width: 96,
		height: 96,
		className: "h-full w-full",
		"aria-hidden": true
	});
}
function Home() {
	return /* @__PURE__ */ (0, import_jsx_runtime.jsx)(MemeStudio, {});
}
//#endregion
export { Home as component };
