#!/usr/bin/env python3
"""svgpreview.py — rasteryzuje wyeksportowane SVG (tylko ścieżki M/L/C/Q/Z) do PNG.

Narzędzie pomocnicze do wizualnej kontroli geometrii (nie jest częścią aplikacji).
Użycie: python3 tools/svgpreview.py PLIK.svg WYJSCIE.png [px_per_mm]
"""
from __future__ import annotations

import re
import sys

from PIL import Image, ImageDraw

NUM = re.compile(r"[MLCZmlcz]|-?\d*\.?\d+(?:[eE][-+]?\d+)?")


def parse_path(d):
    toks = NUM.findall(d)
    i = 0
    cmd = None
    cur = (0.0, 0.0)
    start = (0.0, 0.0)
    contours = []
    pts = []

    def num():
        nonlocal i
        if i >= len(toks):
            raise EOFError
        tv = toks[i]
        if tv and tv[0] in "MLCZmlcz":
            raise EOFError
        v = float(tv)
        i += 1
        return v

    def flush():
        nonlocal pts
        if len(pts) >= 3:
            contours.append(list(pts))
        pts = []

    def add(p):
        pts.append(p)

    while i < len(toks):
        t = toks[i]
        if t and t[0] in "MLCZmlcz" and not re.match(r"-?\d", t):
            cmd = t
            i += 1
            if cmd in "Zz":
                if pts and (abs(pts[-1][0] - start[0]) > 1e-6 or abs(pts[-1][1] - start[1]) > 1e-6):
                    add(start)
                flush()
                cur = start
                continue
            if cmd in "Mm":
                try:
                    cur = (num(), num())
                except EOFError:
                    break
                start = cur
                flush()
                add(cur)
                cmd = "L" if cmd == "M" else "l"
            continue
        try:
            if cmd in ("M", "L", "m", "l"):
                x, y = num(), num()
                if cmd.islower():
                    x += cur[0]
                    y += cur[1]
                cur = (x, y)
                add(cur)
            elif cmd in ("C", "c"):
                x1, y1, x2, y2, x3, y3 = num(), num(), num(), num(), num(), num()
                if cmd == "c":
                    x1 += cur[0]; y1 += cur[1]
                    x2 += cur[0]; y2 += cur[1]
                    x3 += cur[0]; y3 += cur[1]
                for k in range(1, 13):
                    s = k / 12.0
                    mt = 1 - s
                    bx = (mt ** 3) * cur[0] + 3 * (mt ** 2) * s * x1 + 3 * mt * s * s * x2 + s ** 3 * x3
                    by = (mt ** 3) * cur[1] + 3 * (mt ** 2) * s * y1 + 3 * mt * s * s * y2 + s ** 3 * y3
                    add((bx, by))
                cur = (x3, y3)
            elif cmd in ("Q", "q"):
                x1, y1, x2, y2 = num(), num(), num(), num()
                if cmd == "q":
                    x1 += cur[0]; y1 += cur[1]
                    x2 += cur[0]; y2 += cur[1]
                for k in range(1, 13):
                    s = k / 12.0
                    mt = 1 - s
                    bx = mt * mt * cur[0] + 2 * mt * s * x1 + s * s * x2
                    by = mt * mt * cur[1] + 2 * mt * s * y1 + s * s * y2
                    add((bx, by))
                cur = (x2, y2)
            else:
                i += 1
        except EOFError:
            flush()
            break
    flush()
    return contours


def render(svg_path, png_path, scale=6.0):
    svg = open(svg_path, encoding="utf-8").read()
    m = re.search(r'width="([\d.]+)mm"\s+height="([\d.]+)mm"', svg)
    w = float(m.group(1))
    h = float(m.group(2))
    img = Image.new("RGB", (max(1, int(w * scale)), max(1, int(h * scale))), "white")
    dr = ImageDraw.Draw(img)
    paths = re.findall(r"<path ([^>]*)/>", svg)
    for attrs in paths:
        dm = re.search(r'd="([^"]*)"', attrs)
        if not dm:
            continue
        d = dm.group(1)
        fill = re.search(r'fill="([^"]*)"', attrs)
        fill = fill.group(1) if fill else "none"
        stroke = re.search(r'stroke="([^"]*)"', attrs)
        stroke = stroke.group(1) if stroke else "none"
        swm = re.search(r'stroke-width="([\d.]+)"', attrs)
        sw = float(swm.group(1)) if swm else 0.2
        contours = parse_path(d)
        for c in contours:
            pts = [(x * scale, y * scale) for x, y in c]
            if fill not in ("none", "None"):
                dr.polygon(pts, fill=fill if isinstance(fill, str) else "black", outline=None)
            if stroke not in ("none", "None") and sw > 0:
                dr.line(pts + [pts[0]], fill=stroke, width=max(1, int(sw * scale)), joint="curve")
    img.save(png_path)
    return png_path


if __name__ == "__main__":
    a = sys.argv
    if len(a) < 3:
        print(__doc__)
        sys.exit(2)
    print(render(a[1], a[2], float(a[3]) if len(a) > 3 else 6.0))
