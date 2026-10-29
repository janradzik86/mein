#!/usr/bin/env bash
# Buduje WOJAN-LaserCraft-AI.exe (Windows 11 x64) przez zig cc/clang — bez Visual Studio.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
ZC="${ZIG:-/tmp/v1/bin/python -m ziglang}"
TOOLCACHE="${LC_TOOLS:-$HOME/.cache/lc}"
IMGUI="$TOOLCACHE/src/imgui-1.91.5"
DEF="$(/tmp/v1/bin/python -c "import ziglang,os;print(os.path.join(os.path.dirname(ziglang.__file__),\"lib\",\"libc\",\"mingw\",\"lib-common\"))")"
B=build/win
mkdir -p "$B/obj"
TARGET=x86_64-windows-gnu
CXXFLAGS="-target $TARGET -O2 -std=gnu++17 -D_WIN32_WINNT=0x0A00 -DIMGUI_IMPL_WIN32_DISABLE_GAMEPAD -Icore -Idesktop/src -I$IMGUI -I$IMGUI/backends -Wno-unused-command-line-argument"

compile() { # $1 = plik źródłowy, $2 = nazwa obiektu
  local src="$1" obj="$B/obj/$2.o"
  if [ -f "$obj" ] && [ "$obj" -nt "$src" ]; then return 0; fi
  echo "  cc $src"
  $ZC c++ $CXXFLAGS -c "$src" -o "$obj"
}

echo "== biblioteka silnika (core) =="
for f in core/*.cpp; do compile "$f" "core_$(basename "${f%.cpp}")"; done
echo "== interfejs =="
compile desktop/src/main.cpp win_main
compile desktop/src/ui.cpp win_ui
LIBS=""
for f in imgui imgui_draw imgui_tables imgui_widgets backends/imgui_impl_win32 backends/imgui_impl_dx11; do
  name="imgui_$(basename "$f")"
  src="$IMGUI/$f.cpp"
  if [ ! -f "$B/obj/$name.o" ] || [ "$src" -nt "$B/obj/$name.o" ]; then
    echo "  cc $src"
    $ZC c++ $CXXFLAGS -c "$src" -o "$B/obj/$name.o"
  fi
done
echo "== linkowanie =="
$ZC c++ -target $TARGET -O2 -Wl,--subsystem,windows -o build/WOJAN-LaserCraft-AI.exe \
  "$B"/obj/*.o -L"$DEF" -ld3dcompiler_47 -ld3d11 -ldxgi -ldwmapi -lcomdlg32 -lshell32 -lole32 \
  -luser32 -lgdi32 -lwinmm -ladvapi32 -lshlwapi -static -static-libgcc -static-libstdc++ \
  -Wl,/Brepro
SIZE=$(stat -c%s build/WOJAN-LaserCraft-AI.exe)
echo "OK build/WOJAN-LaserCraft-AI.exe $SIZE B"
