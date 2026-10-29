#!/usr/bin/env python3
"""pack_apk.py — składa APK (ZIP).

Dwa tryby:
  * --base BASE.apk  — bierze APK zbudowany przez **aapt2 link** (poprawny
    AndroidManifest.xml, res/, resources.arsc, assets/) i dokłada classes.dex.
    To jest tryb używany w tools/build_android.sh.
  * --manifest/--assets — tryb awaryjny (własny AXML z tools/axmlbuild.py).

classes.dex jest zapisywany bez kompresji i wyrównany do 4 B (mmap w ART),
pozostałe wpisy zachowują sposób pakowania z aapt2 (stored/deflate).
"""
from __future__ import annotations

import argparse
import os
import struct
import sys
import zipfile
import zlib


def align4(n: int) -> int:
    return (4 - n % 4) % 4


def _align_extra(base_off: int) -> bytes:
    """Poprawne pole extra wyrównujące dane wpisu do 4 B.

    Android wymaga, aby NIEPAKOWANE wpisy (resources.arsc, classes.dex) zaczynały się
    pod adresem podzielnym przez 4 — inaczej system nie może zmapować resources.arsc
    w pamięć i instalacja kończy się błędem
    INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED (dla targetSdk >= 30).
    Pole extra to sekwencja rekordów (id u16, rozmiar u16, dane); używamy id 0xD935,
    tak jak robi to zipalign.
    """
    need = (4 - base_off % 4) % 4
    if need == 0:
        return b""
    size = need + 4          # nagłówek rekordu (4 B) + dane rekord uzupełniające
    return struct.pack("<HH", 0xD935, size - 4) + b"\x00" * (size - 4)


def build(entries, out_path):
    """entries: lista (name, data, stored)."""
    buf = bytearray()
    central = bytearray()
    for name, data, stored in entries:
        nb = name.encode("utf-8")
        method = 0 if stored else 8
        raw = data if stored else zlib.compress(data, 9)[2:-4]
        crc = zlib.crc32(data) & 0xFFFFFFFF
        off = len(buf)
        extra = _align_extra(off + 30 + len(nb)) if stored else b""
        extra_len = len(extra)
        buf += struct.pack("<IHHHHHIIIHH", 0x04034B50, 20, 0, method, 0, 0,
                           crc, len(raw), len(data), len(nb), extra_len)
        buf += nb + extra + raw
        central += struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, 20, 20, 0, method,
                               0, 0, crc, len(raw), len(data), len(nb),
                               extra_len, 0, 0, 0, 0, off) + nb + extra
    cd_off = len(buf)
    buf += central
    buf += struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, len(entries), len(entries),
                       len(central), cd_off, 0)
    with open(out_path, "wb") as fh:
        fh.write(buf)
    return len(buf)


def from_base(base_path, dex_path):
    """Wpisy z APK aapt2 + classes.dex zaraz po manifeście."""
    dex = open(dex_path, "rb").read()
    entries = []
    with zipfile.ZipFile(base_path) as z:
        names = z.namelist()
        if "AndroidManifest.xml" not in names:
            sys.exit("BŁĄD: base.apk bez AndroidManifest.xml")
        for n in names:
            if n == "classes.dex":
                continue  # podmieniamy na własny (bez kompresji, wyrównany do 4 B)
            info = z.getinfo(n)
            data = z.read(n)
            stored = info.compress_type == 0
            if n == "resources.arsc":
                # obowiązkowe: targetSdk >= 30 odrzuca APK ze spakowanym resources.arsc
                stored = True
            entries.append((n, data, stored))
            if n == "AndroidManifest.xml":
                entries.append(("classes.dex", dex, True))
    if not any(n == "classes.dex" for n, _d, _s in entries):
        entries.append(("classes.dex", dex, True))
    return entries


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--manifest")
    ap.add_argument("--dex", required=True)
    ap.add_argument("--base")
    ap.add_argument("--assets", action="append", default=[])
    args = ap.parse_args()

    if args.base:
        entries = from_base(args.base, args.dex)
        size = build(entries, args.out)
        print(f"{args.out}: {size} B, wpisów z aapt2 + classes.dex: {len(entries)}")
        return 0

    if not args.manifest:
        ap.error("podaj --base albo --manifest")
    entries = []
    entries.append(("AndroidManifest.xml", open(args.manifest, "rb").read(), True))
    entries.append(("classes.dex", open(args.dex, "rb").read(), True))

    count = 0
    total = 0
    for adir in args.assets:
        base = os.path.basename(adir.rstrip("/"))
        for root, _dirs, files in os.walk(adir):
            for f in sorted(files):
                if f.startswith("."):
                    continue
                p = os.path.join(root, f)
                rel = os.path.relpath(p, adir).replace(os.sep, "/")
                data = open(p, "rb").read()
                entries.append((f"assets/{rel}", data, False))
                count += 1
                total += len(data)

    size = build(entries, args.out)
    print(f"{args.out}: {size} B, zasobów: {count} ({total} B)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
