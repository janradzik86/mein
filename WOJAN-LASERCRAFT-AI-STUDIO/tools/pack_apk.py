#!/usr/bin/env python3
"""pack_apk.py — składa APK (ZIP): manifest AXML i classes.dex bez kompresji + zasoby.

Wpisy muszą być wyrównane do 4 B (mmap), dlatego manifest i dex są STORED
z dopełnieniem extra-field, a zasoby (czcionki) mogą być deflate.
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
        extra_len = align4(off + 30 + len(nb)) if stored else 0
        extra = b"\x00" * extra_len
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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--dex", required=True)
    ap.add_argument("--assets", action="append", default=[])
    args = ap.parse_args()

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
