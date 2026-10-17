#!/usr/bin/env python3
"""WOJAN AI FACTORY — budowanie WOJAN-AI-FACTORY.exe (Windows 11, DX11/ImGui/QuickJS).
Używa zig cc (python -m ziglang) z pip-pakietu ziglang — bez zewnętrznych SDK.
Wynik: desktop/build/out/WOJAN-AI-FACTORY.exe
"""
import os, subprocess, sys, glob, shutil

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # WOJAN-AI-FACTORY
BUILD = os.path.join(ROOT, "desktop", "build")
OUT = os.path.join(BUILD, "out")
OBJ = os.path.join(BUILD, "obj")
TOOLCACHE = os.environ.get("WOJAN_TOOLCACHE", os.path.expanduser("~/.cache/wojan-tools"))
QJS_SRC = os.path.join(TOOLCACHE, "src", "quickjs-master")
IMGUI_SRC = os.path.join(TOOLCACHE, "src", "imgui-master")
PY = sys.executable

TARGET = "x86_64-windows-gnu"

def run(cmd, **kw):
    print("+", " ".join(cmd)[:220])
    r = subprocess.run(cmd, **kw)
    if r.returncode != 0:
        sys.exit(f"NIEUDANE: {' '.join(cmd)[:220]}")
    return r

def zig(*args):
    return [PY, "-m", "ziglang", "cc", "-target", TARGET, *args]

def zigxx(*args):
    return [PY, "-m", "ziglang", "c++", "-target", TARGET, *args]

os.makedirs(OUT, exist_ok=True)
os.makedirs(OBJ, exist_ok=True)

if not os.path.isdir(QJS_SRC) or not os.path.isdir(IMGUI_SRC):
    sys.exit("brak źródeł imgui/quickjs — uruchom scripts/fetch-toolchain.sh")

CFLAGS = ["-O2", "-std=gnu11", "-g0",
          f"-I{QJS_SRC}", f"-I{IMGUI_SRC}", f"-I{IMGUI_SRC}/backends",
          "-D_WIN32_WINNT=0x0A00", "-DIMGUI_IMPL_WIN32_DISABLE_GAMEPAD",
          "-Wno-unused-command-line-argument"]
CXXFLAGS = ["-O2", "-std=gnu++17", "-g0", f"-I{QJS_SRC}", f"-I{IMGUI_SRC}",
            f"-I{IMGUI_SRC}/backends", f"-I{os.path.join(ROOT, 'desktop', 'src')}",
            "-D_WIN32_WINNT=0x0A00", "-DIMGUI_IMPL_WIN32_DISABLE_GAMEPAD",
            "-fno-exceptions", "-fno-rtti",
            "-Wno-unused-command-line-argument"]

def compile_c(src, name):
    obj = os.path.join(OBJ, name + ".o")
    run(zig(*CFLAGS, "-c", src, "-o", obj))
    return obj

def compile_cpp(src, name):
    obj = os.path.join(OBJ, name + ".o")
    run(zigxx(*CXXFLAGS, "-c", src, "-o", obj))
    return obj

objs = []

# QuickJS-NG (C) — cutils jest header-only
for f, n in [("quickjs.c", "quickjs"), ("dtoa.c", "dtoa"),
             ("libregexp.c", "libregexp"), ("libunicode.c", "libunicode")]:
    objs.append(compile_c(os.path.join(QJS_SRC, f), n))

# ImGui (C++) + backends Win32/DX11
for f, n in [("imgui.cpp", "imgui"), ("imgui_draw.cpp", "imgui_draw"),
             ("imgui_tables.cpp", "imgui_tables"), ("imgui_widgets.cpp", "imgui_widgets"),
             ("backends/imgui_impl_win32.cpp", "imgui_impl_win32"),
             ("backends/imgui_impl_dx11.cpp", "imgui_impl_dx11")]:
    objs.append(compile_cpp(os.path.join(IMGUI_SRC, f), n))

# nasz kod (C++)
for f, n in [("main.cpp", "main"), ("qjs_host.cpp", "qjs_host"), ("httpd.cpp", "httpd"),
             ("http_client.cpp", "http_client"), ("sys_native.cpp", "sys_native")]:
    objs.append(compile_cpp(os.path.join(ROOT, "desktop", "src", f), n))

exe = os.path.join(OUT, "WOJAN-AI-FACTORY.exe")
run(zigxx("-o", exe, *objs,
        "-Wl,--subsystem,windows", "-municode",
        "-ld3d11", "-ldxgi", "-ld3dcompiler_47", "-lwinhttp", "-lws2_32",
        "-luser32", "-lgdi32", "-lshell32", "-lole32", "-lcomdlg32",
        "-ladvapi32", "-limm32", "-ldwmapi", "-lversion", "-luuid",
        "-static"))
print("OK:", exe, os.path.getsize(exe), "B")
