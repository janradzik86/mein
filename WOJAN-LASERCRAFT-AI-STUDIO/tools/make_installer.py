#!/usr/bin/env python3
"""make_installer.py — buduje WOJAN-LaserCraft-AI-Windows-Setup.exe.

Instalator to mały program Win32 (desktop/installer/installer.cpp) z doczepionym
na końcu archiwum: [wpis]* + stopka(8 B magic, rozmiar u64, liczba wpisów u32, 4 B).
Wpis: nazwa u16 + UTF-8 nazwa + rozmiar u32 + dane.

Użycie:
  python3 tools/make_installer.py --build      # kompiluje instalator i pakuje
  python3 tools/make_installer.py --verify     # sprawdza archiwum w gotowym EXE
  python3 tools/make_installer.py --extract DIR
"""

from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import struct
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ZIG_PY = os.environ.get("LC_PY", "/tmp/v1/bin/python")
OUT_EXE = os.path.join(ROOT, "build", "WOJAN-LaserCraft-AI-Windows-Setup.exe")
STUB_EXE = os.path.join(ROOT, "build", "win", "setup_stub.exe")
MAGIC = b"WLCPAY01"


def payload_files():
    """Lista (źródło, nazwa w instalacji) — wszystko, co dostaje użytkownik."""
    items = []
    app = os.path.join(ROOT, "build", "WOJAN-LaserCraft-AI.exe")
    if not os.path.exists(app):
        sys.exit("BŁĄD: brak build/WOJAN-LaserCraft-AI.exe (uruchom tools/build_windows.sh)")
    items.append((app, "WOJAN-LaserCraft-AI.exe"))

    fonts = os.path.join(ROOT, "fonts")
    for name in sorted(os.listdir(fonts)):
        if name.lower().endswith(".ttf"):
            items.append((os.path.join(fonts, name), "fonts\\" + name))

    proj = os.path.join(ROOT, "dist", "przyklady")
    if os.path.isdir(proj):
        for name in sorted(os.listdir(proj)):
            if name.lower().endswith(".wlcproj"):
                items.append((os.path.join(proj, name), "projekty\\" + name))

    for extra, dest in (
        (os.path.join(ROOT, "RELEASE", "INSTRUKCJA.pdf"), "dokumentacja\\INSTRUKCJA.pdf"),
        (os.path.join(ROOT, "RELEASE", "MODELE-I-LICENCJE.md"), "dokumentacja\\MODELE-I-LICENCJE.md"),
        (os.path.join(ROOT, "RELEASE", "RAPORT-TESTOW.md"), "dokumentacja\\RAPORT-TESTOW.md"),
        (os.path.join(ROOT, "RELEASE", "POPRAWKA-1.0.2.md"), "dokumentacja\\ZMIANY-1.0.2.md"),
        (os.path.join(ROOT, "dist", "README-INSTALACJA.txt"), "README.txt"),
        (os.path.join(ROOT, "dist", "LICENCJE.txt"), "LICENCJE.txt"),
    ):
        if os.path.exists(extra):
            items.append((extra, dest))
    return items


def build_container(items):
    body = bytearray()
    for src, name in items:
        data = open(src, "rb").read()
        nb = name.encode("utf-8")
        if len(nb) > 0xFFFF:
            sys.exit("nazwa wpisu za długa: " + name)
        body += struct.pack("<H", len(nb)) + nb + struct.pack("<I", len(data)) + data
    footer = MAGIC + struct.pack("<QI", len(body), len(items)) + b"\x00\x00\x00\x00"
    return bytes(body) + footer, len(body)


def compile_stub():
    os.makedirs(os.path.dirname(STUB_EXE), exist_ok=True)
    src = os.path.join(ROOT, "desktop", "installer", "installer.cpp")
    zig = [ZIG_PY, "-m", "ziglang", "c++"]
    cmd = zig + [
        "-target", "x86_64-windows-gnu", "-O2", "-std=gnu++17",
        "-D_WIN32_WINNT=0x0A00", "-DUNICODE", "-D_UNICODE",
        "-Wl,--subsystem,windows",
        "-static", "-static-libgcc", "-static-libstdc++",
        "-o", STUB_EXE, src,
        "-lole32", "-luuid", "-lshell32", "-luser32", "-lgdi32",
        "-ladvapi32", "-lcomdlg32", "-lcomctl32",
    ]
    print("  cc", os.path.relpath(src, ROOT))
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write(r.stdout + "\n" + r.stderr + "\n")
        sys.exit("BŁĄD: kompilacja instalatora nie powiodła się")
    for line in (r.stderr or "").strip().splitlines()[:12]:
        if "warning" in line and "_WIN32_WINNT" in line:
            continue
        print("   ", line)
    return STUB_EXE


def build():
    from_samples = payload_files()
    stub = compile_stub()
    base = open(stub, "rb").read()
    container, body_len = build_container(from_samples)
    out = base + container
    os.makedirs(os.path.dirname(OUT_EXE), exist_ok=True)
    with open(OUT_EXE, "wb") as fh:
        fh.write(out)
    print("wpisów w archiwum: %d, archiwum %d B, całość %d B (%.1f MB)"
          % (len(from_samples), body_len, len(out), len(out) / 1048576.0))
    verify(OUT_EXE)
    return 0


def parse(path):
    blob = open(path, "rb").read()
    if len(blob) < 24:
        sys.exit("BŁĄD: plik za mały")
    foot = blob[-24:]
    if foot[:8] != MAGIC:
        sys.exit("BŁĄD: brak stopki archiwum (magic)")
    size, count = struct.unpack("<QI", foot[8:20])
    start = len(blob) - 24 - size
    if start < 0:
        sys.exit("BŁĄD: rozmiar archiwum większy niż plik")
    entries = []
    pos = start
    end = start + size
    for _ in range(count):
        if pos + 2 > end:
            sys.exit("BŁĄD: urwany nagłówek wpisu")
        (nlen,) = struct.unpack("<H", blob[pos:pos + 2])
        pos += 2
        name = blob[pos:pos + nlen].decode("utf-8")
        pos += nlen
        if pos + 4 > end:
            sys.exit("BŁĄD: urwany rozmiar wpisu")
        (sz,) = struct.unpack("<I", blob[pos:pos + 4])
        pos += 4
        data = blob[pos:pos + sz]
        if len(data) != sz:
            sys.exit("BŁĄD: urwany wpis " + name)
        pos += sz
        entries.append((name, data))
    if pos != end:
        sys.exit("BŁĄD: nadmiarowe bajty w archiwum (%d != %d)" % (pos, end))
    return blob, entries


def verify(path=OUT_EXE):
    blob, entries = parse(path)
    print("WERYFIKACJA archiwum w %s" % os.path.relpath(path, ROOT))
    total = 0
    for name, data in entries:
        total += len(data)
        print("  %-46s %9d B  sha256 %s" % (name, len(data), hashlib.sha256(data).hexdigest()[:16]))
    # porównanie z plikami źródłowymi
    mism = 0
    src_by_name = {dest: src for src, dest in payload_files()}
    for name, data in entries:
        src = src_by_name.get(name)
        if src and open(src, "rb").read() != data:
            print("  ROZBIEŻNOŚĆ:", name)
            mism += 1
    pe_ok = blob[:2] == b"MZ" and b"PE\x00\x00" in blob[:4096]
    print("  wpisów: %d, danych: %d B, koperta: %.1f MB, nagłówek PE: %s, rozbieżności: %d"
          % (len(entries), total, len(blob) / 1048576.0, "OK" if pe_ok else "BRAK", mism))
    return 0 if mism == 0 and pe_ok else 1


def extract(dest):
    _blob, entries = parse(OUT_EXE)
    for name, data in entries:
        p = os.path.join(dest, name.replace("\\", os.sep))
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "wb") as fh:
            fh.write(data)
    print("wypakowano %d plików do %s" % (len(entries), dest))
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--build", action="store_true")
    ap.add_argument("--verify", action="store_true")
    ap.add_argument("--extract", default="")
    a = ap.parse_args()
    if a.extract:
        return extract(a.extract)
    if a.verify:
        return verify()
    if a.build:
        return build()
    ap.print_help()
    return 0


if __name__ == "__main__":
    sys.exit(main())
