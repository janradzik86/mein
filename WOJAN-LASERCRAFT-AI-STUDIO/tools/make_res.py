#!/usr/bin/env python3
"""make_res.py — generuje zasoby Androida (ikony + values) i manifest źródłowy.

Dlaczego: APK 1.0.0/1.0.1 nie miały katalogu `res/` ani `resources.arsc` — manifest był
składany własnym narzędziem (tools/axmlbuild.py), a ikony brakowało. Ten skrypt tworzy
pełny, zwykły zestaw zasobów, który kompiluje **oficjalny aapt2**, dzięki czemu
AndroidManifest.xml i resources.arsc są w formacie dokładnie takim, jaki robi Gradle.

Użycie:
    python3 tools/make_res.py --res android/res --manifest build/android/AndroidManifest.src.xml \
        --package pl.wojan.lasercraft --version-code 3 --version-name 1.0.2 \
        --min-sdk 24 --target-sdk 36
"""
from __future__ import annotations

import argparse
import os
import struct
import zlib

# ---------------------------------------------------------------- PNG (bez PIL)
def _chunk(tag: bytes, data: bytes) -> bytes:
    return (struct.pack(">I", len(data)) + tag + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))


def write_png(path: str, w: int, h: int, rgba: bytearray) -> None:
    raw = bytearray()
    stride = w * 4
    for y in range(h):
        raw.append(0)  # filtr 0
        raw += rgba[y * stride:(y + 1) * stride]
    png = (b"\x89PNG\r\n\x1a\n"
           + _chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + _chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + _chunk(b"IEND", b""))
    with open(path, "wb") as fh:
        fh.write(png)


# ---------------------------------------------------------------- rysowanie
def _in_round_rect(x, y, w, h, r):
    if x < 0 or y < 0 or x > w or y > h:
        return False
    cx = min(max(x, r), w - r)
    cy = min(max(y, r), h - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r


def _in_w(x, y, w, h):
    """Litera W jako cztery ukośne belki (współrzędne znormalizowane 0..1)."""
    u, v = x / w, y / h
    if v < 0.18 or v > 0.84:
        return False
    t = 0.085  # grubość belki
    # trzy dolne wierzchołki i dwa górne wypusty
    segs = [((0.12, 0.18), (0.30, 0.84)), ((0.30, 0.84), (0.50, 0.36)),
            ((0.50, 0.36), (0.70, 0.84)), ((0.70, 0.84), (0.88, 0.18))]
    for (x1, y1), (x2, y2) in segs:
        dx, dy = x2 - x1, y2 - y1
        L2 = dx * dx + dy * dy
        tt = ((u - x1) * dx + (v - y1) * dy) / L2
        tt = max(0.0, min(1.0, tt))
        px, py = x1 + tt * dx, y1 + tt * dy
        if (u - px) ** 2 + (v - py) ** 2 <= t * t:
            return True
    return False


def render_icon(size: int) -> bytearray:
    """Ikona: grafitowa zaokrąglona płytka + bursztynowa litera W + smuga lasera."""
    SS = 3  # supersampling
    W = size * SS
    buf = bytearray(W * W * 4)
    r = W * 0.22
    for y in range(W):
        for x in range(W):
            inside = _in_round_rect(x + 0.5, y + 0.5, W, W, r)
            if not inside:
                continue
            t = y / W
            # tło: gradient grafitowy
            br = int(0x18 + (0x0E - 0x18) * t)
            bg = int(0x1C + (0x10 - 0x1C) * t)
            bb = int(0x21 + (0x13 - 0x21) * t)
            cr, cg, cb = br, bg, bb
            if _in_w(x, y, W, W):
                cr, cg, cb = 0xFF, 0xB3, 0x00
            # cienka smuga lasera pod literą
            if 0.885 < y / W < 0.915 and 0.10 < x / W < 0.90 and not _in_w(x, y, W, W):
                cr, cg, cb = 0xFF, 0x8A, 0x00
            i = (y * W + x) * 4
            buf[i] = cr
            buf[i + 1] = cg
            buf[i + 2] = cb
            buf[i + 3] = 255
    # downscale (box filter)
    out = bytearray(size * size * 4)
    n = SS * SS
    for y in range(size):
        for x in range(size):
            ar = ag = ab = aa = 0
            for dy in range(SS):
                for dx in range(SS):
                    i = ((y * SS + dy) * W + (x * SS + dx)) * 4
                    a = buf[i + 3]
                    ar += buf[i] * a
                    ag += buf[i + 1] * a
                    ab += buf[i + 2] * a
                    aa += a
            o = (y * size + x) * 4
            if aa:
                out[o] = ar // aa
                out[o + 1] = ag // aa
                out[o + 2] = ab // aa
            out[o + 3] = aa // n
    return out


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

STRINGS = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">WOJAN LaserCraft AI</string>
    <string name="tab_generator">Generator AI</string>
</resources>
"""

STYLES = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="AppTheme" parent="@android:style/Theme.Material.NoActionBar">
        <item name="android:windowBackground">#FF0E1013</item>
        <item name="android:colorBackground">#FF0E1013</item>
        <item name="android:statusBarColor">#FF0E1013</item>
        <item name="android:navigationBarColor">#FF0E1013</item>
        <item name="android:colorAccent">#FFFFB300</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
"""

MANIFEST = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{package}"
    android:versionCode="{version_code}"
    android:versionName="{version_name}">

    <uses-sdk android:minSdkVersion="{min_sdk}" android:targetSdkVersion="{target_sdk}" />

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <uses-feature android:name="android.hardware.microphone" android:required="false" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />

    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:theme="@style/AppTheme"
        android:allowBackup="true"
        android:hardwareAccelerated="true"
        android:usesCleartextTraffic="true"
        android:supportsRtl="false">

        <activity
            android:name="{package}.MainActivity"
            android:label="@string/app_name"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden|screenLayout|smallestScreenSize|uiMode"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
"""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--res", default="android/res")
    ap.add_argument("--manifest", default="build/android/AndroidManifest.src.xml")
    ap.add_argument("--package", default="pl.wojan.lasercraft")
    ap.add_argument("--version-code", default="3")
    ap.add_argument("--version-name", default="1.0.2")
    ap.add_argument("--min-sdk", default="24")
    ap.add_argument("--target-sdk", default="36")
    args = ap.parse_args()

    os.makedirs(os.path.join(args.res, "values"), exist_ok=True)
    with open(os.path.join(args.res, "values", "strings.xml"), "w", encoding="utf-8") as fh:
        fh.write(STRINGS)
    with open(os.path.join(args.res, "values", "styles.xml"), "w", encoding="utf-8") as fh:
        fh.write(STYLES)

    for dens, px in DENSITIES.items():
        d = os.path.join(args.res, "mipmap-" + dens)
        os.makedirs(d, exist_ok=True)
        p = os.path.join(d, "ic_launcher.png")
        write_png(p, px, px, render_icon(px))
        print(f"ikona {p}: {px}×{px}")

    os.makedirs(os.path.dirname(os.path.abspath(args.manifest)), exist_ok=True)
    xml = MANIFEST.format(package=args.package, version_code=args.version_code,
                          version_name=args.version_name, min_sdk=args.min_sdk,
                          target_sdk=args.target_sdk)
    with open(args.manifest, "w", encoding="utf-8") as fh:
        fh.write(xml)
    print(f"manifest źródłowy: {args.manifest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
