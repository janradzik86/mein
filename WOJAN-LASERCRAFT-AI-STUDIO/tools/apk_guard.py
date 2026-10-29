#!/usr/bin/env python3
"""apk_guard.py — brama jakości APK: blokuje wydanie, jeśli pakiet nie jest naszą
aplikacją natywną.

Powód powstania: do telefonu trafiły pliki `wojan-lasercraft-lite-shell-*.apk` (3,0 MB)
zbudowane jako powłoka WebView/Capacitor (pakiet `pl.wojan.hobby.lasercraft`). Taka
powłoka wywala się na Androidzie 14+ wyjątkiem:

    SecurityException: One of RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED should be
    specified when a receiver isn't being registered exclusively for system broadcasts
    at com.capacitorjs.plugins.share.SharePlugin.load
    at com.getcapacitor.BridgeActivity.onCreate

To nie jest nasz kod i nie wolno go wydawać jako „WOJAN LaserCraft AI”. Skrypt sprawdza,
że plik APK:
  * ma pakiet `pl.wojan.lasercraft` (nie `pl.wojan.hobby.lasercraft`),
  * ma activity startową `pl.wojan.lasercraft.MainActivity`,
  * NIE zawiera śladów Capacitora, Cordovy ani WebView w classes.dex,
  * NIE rejestruje dynamicznie odbiorników (registerReceiver) — czyli nie może trafić
    na wyjątek RECEIVER_EXPORTED,
  * zawiera klasy własnego rdzenia (Core, Draw) — dowód, że to nasz natywny program.

Użycie: python3 tools/apk_guard.py RELEASE/….apk
Exit 0 = plik wolno wydać. Exit 1 = STOP (z wypisanym powodem).
"""
from __future__ import annotations

import re
import sys
import zipfile

PACKAGE = b"pl.wojan.lasercraft"
BAD_PACKAGE = b"pl.wojan.hobby.lasercraft"
FORBIDDEN = [
    (b"capacitor", "Capacitor (powłoka WebView)"),
    (b"Capacitor", "Capacitor (powłoka WebView)"),
    (b"getcapacitor", "Capacitor (powłoka WebView)"),
    (b"BridgeActivity", "Capacitor BridgeActivity"),
    (b"SharePlugin", "wtyczka Capacitor Share"),
    (b"cordova", "Cordova (powłoka WebView)"),
    (b"android/webkit/WebView", "WebView (aplikacja nie może być stroną WWW)"),
    (b"registerReceiver", "dynamiczna rejestracja odbiornika (ryzyko RECEIVER_EXPORTED)"),
]
REQUIRED_CLASSES = [b"Lpl/wojan/lasercraft/Core;", b"Lpl/wojan/lasercraft/Draw;",
                    b"Lpl/wojan/lasercraft/MainActivity;"]


def in_manifest(manifest: bytes, needle: bytes) -> bool:
    """Manifest w APK jest binarny (AXML) i trzyma łańcuchy jako UTF-16LE — szukamy obu postaci."""
    return needle in manifest or needle.decode("utf-8").encode("utf-16le") in manifest


def main() -> int:
    if len(sys.argv) < 2:
        print("użycie: python3 tools/apk_guard.py PLIK.apk", file=sys.stderr)
        return 2
    path = sys.argv[1]
    problems: list[str] = []
    notes: list[str] = []

    try:
        z = zipfile.ZipFile(path)
    except Exception as e:  # noqa: BLE001
        print(f"BŁĄD: nie mogę otworzyć {path}: {e}", file=sys.stderr)
        return 1

    dex_files = [n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
    if not dex_files:
        problems.append("brak classes.dex")
        dex = b""
    else:
        dex = b"".join(z.read(n) for n in dex_files)

    manifest = z.read("AndroidManifest.xml") if "AndroidManifest.xml" in z.namelist() else b""

    # 1) pakiet
    if in_manifest(manifest, BAD_PACKAGE) or BAD_PACKAGE in dex:
        problems.append(f"pakiet {BAD_PACKAGE.decode()} — to NIE jest nasza aplikacja "
                        "(powłoka WebView z innego projektu)")
    elif not in_manifest(manifest, PACKAGE):
        problems.append(f"brak pakietu {PACKAGE.decode()} w manifeście")

    # 2) activity startowa
    if not in_manifest(manifest, b"MainActivity"):
        problems.append("brak MainActivity w manifeście")

    # 3) zakazane technologie
    for needle, opis in FORBIDDEN:
        if needle in dex:
            problems.append(f"classes.dex zawiera: {opis} ({needle.decode()})")

    # 4) klasy własnego rdzenia
    for cls in REQUIRED_CLASSES:
        if cls not in dex:
            problems.append(f"brak klasy {cls.decode()} — to nie jest nasz rdzeń")

    # 5) informacyjnie
    libs = [n for n in z.namelist() if n.startswith("lib/") and n.endswith(".so")]
    notes.append(f"biblioteki natywne: {len(libs)}")
    notes.append(f"rozmiar pliku: {sum(i.file_size for i in z.infolist())} B (nieskompresowany)")

    print(f"BRAMA APK: {path}")
    for n in notes:
        print(f"  · {n}")
    if problems:
        print()
        for p in problems:
            print(f"  [STOP] {p}")
        print("\nWYNIK: NIE WOLNO WYDAWAĆ TEGO PLIKU")
        return 1
    print("\nWYNIK: plik jest naszą aplikacją natywną (bez WebView) — wolno wydać")
    return 0


if __name__ == "__main__":
    sys.exit(main())
