#!/usr/bin/env python3
"""WOJAN AI FACTORY — budowanie Setup.exe (własny instalator Win32) + ZIP portable.
Wyniki: dist/WOJAN-AI-FACTORY-Setup.exe, dist/WOJAN-AI-FACTORY-Portable.zip
"""
import os, subprocess, sys, struct, zipfile, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DIST = os.path.join(ROOT, "dist")
PY = os.environ.get("WOJAN_PY", "/home/user/.cache/wojan-venv/bin/python")
EXE = os.path.join(ROOT, "desktop", "build", "out", "WOJAN-AI-FACTORY.exe")
TARGET = "x86_64-windows-gnu"

def run(cmd):
    print("+", " ".join(cmd)[:200])
    r = subprocess.run(cmd)
    if r.returncode != 0:
        sys.exit(f"NIEUDANE: {' '.join(cmd)[:200]}")

os.makedirs(DIST, exist_ok=True)

# 1) Setup.exe (bazowy instalator)
setup_src = os.path.join(ROOT, "desktop", "installer", "src", "setup.cpp")
setup_base = os.path.join(DIST, "setup-base.exe")
run([PY, "-m", "ziglang", "c++", "-target", TARGET, "-O2", "-std=gnu++17", "-fno-exceptions",
     "-D_WIN32_WINNT=0x0A00", "-Wl,--subsystem,windows",
     "-o", setup_base, setup_src,
     "-lshell32", "-lole32", "-lcomdlg32", "-ladvapi32", "-luser32", "-lgdi32", "-static"])

# 2) payload: pliki aplikacji (ścieżki względne w archiwum)
files = [
    (EXE, "WOJAN-AI-FACTORY.exe"),
]
engine_dir = os.path.join(ROOT, "engine")
for base, _, names in os.walk(engine_dir):
    for n in names:
        if n.endswith((".js", ".json")):
            full = os.path.join(base, n)
            rel = os.path.relpath(full, engine_dir).replace("\\", "/")
            files.append((full, "engine/" + rel))
readme = os.path.join(ROOT, "README.md")
if os.path.exists(readme):
    files.append((readme, "README.md"))

# format: [u32 count][u16 nlen][name][u32 size][data]... + [u32 payload_size][u32 'WJAN']
blob = struct.pack("<I", len(files))
for full, rel in files:
    data = open(full, "rb").read()
    nb = rel.encode("utf-8")
    blob += struct.pack("<H", len(nb)) + nb + struct.pack("<I", len(data)) + data
blob += struct.pack("<II", len(blob), 0x4E414A57)  # 'WJAN'

setup_final = os.path.join(DIST, "WOJAN-AI-FACTORY-Setup.exe")
with open(setup_final, "wb") as f:
    f.write(open(setup_base, "rb").read())
    f.write(blob)
print("OK:", setup_final, os.path.getsize(setup_final), "B (", len(files), "plików )")

# 3) portable ZIP
portable = os.path.join(DIST, "WOJAN-AI-FACTORY-Portable.zip")
with zipfile.ZipFile(portable, "w", zipfile.ZIP_DEFLATED) as z:
    for full, rel in files:
        z.write(full, "WOJAN-AI-FACTORY/" + rel)
    z.writestr("WOJAN-AI-FACTORY/wojan-config.json",
               '{"ollama":{"base":"http://127.0.0.1:11434"},"mode":"AUTO"}')
print("OK:", portable, os.path.getsize(portable), "B")
