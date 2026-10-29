#!/usr/bin/env python3
"""make_apk.py — buduje podpisany APK z manifestu (AXML) i classes.dex.

Użycie:
  python3 make_apk.py --manifest AndroidManifest.axml --dex classes.dex \
      --out WOJAN-AI-FACTORY-Release.apk --keystore keystore/wojan-release.p12 \
      [--keystore-pass-env WOJAN_KEYSTORE_PASS]

Wymagania wejścia:
  * AndroidManifest.axml — binarny AXML (tools/axmlbuild.py lub pyaxml)
  * classes.dex — wynik asemblacji smali
"""

from __future__ import annotations

import argparse
import os
import struct
import sys
import zipfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "apksig"))
from apksig import SigningIdentity, sign_apk_bytes, verify_apk_bytes  # noqa: E402

from cryptography.hazmat.primitives import serialization  # noqa: E402
from cryptography.hazmat.primitives.serialization import pkcs12  # noqa: E402


def load_identity(ks_path: str, pass_env: str) -> SigningIdentity:
    password = os.environ.get(pass_env)
    if not password:
        env_file = os.path.join(os.path.dirname(ks_path), "keystore.env")
        if os.path.exists(env_file):
            for line in open(env_file):
                line = line.strip()
                if line.startswith(f"{pass_env}="):
                    password = line.split("=", 1)[1].strip().strip('"').strip("'")
    if not password:
        raise SystemExit(
            f"brak hasła klucza: ustaw {pass_env} lub plik keystore/keystore.env "
            f"(hasło NIE może trafić do repozytorium)")
    with open(ks_path, "rb") as fh:
        loaded = pkcs12.load_key_and_certificates(fh.read(), password.encode())
    key, cert = loaded[0], loaded[1]
    return SigningIdentity(key=key, cert=cert)


class AlignedZip:
    """Zip z wyrównaniem 4 B dla wpisów STORED (wymóg mmap w Android)."""

    def __init__(self, path):
        self.path = path
        self.items = []  # (name, data, compress)

    def add(self, name: str, data: bytes, stored: bool = True):
        self.items.append((name, data, stored))

    def build(self) -> bytes:
        out = bytearray()
        central = bytearray()
        for name, data, stored in self.items:
            name_b = name.encode()
            method = 0 if stored else 8
            raw = data if stored else _deflate(data)
            crc = zipfile.crc32(data) & 0xFFFFFFFF
            # wyrównanie: offset danych = header_start + 30 + name + extra → 4 B
            header_off = len(out)
            extra_len = (4 - (header_off + 30 + len(name_b)) % 4) % 4
            extra = b"\x00" * extra_len
            out += struct.pack("<IHHHHHIIIHH", 0x04034B50, 20, 0, method, 0, 0,
                               crc, len(raw), len(data), len(name_b), extra_len)
            out += name_b + extra + raw
            central += struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, 20, 20, 0, method,
                                   0, 0, crc, len(raw), len(data), len(name_b),
                                   extra_len, 0, 0, 0, 0, header_off) + name_b + extra
        cd_off = len(out)
        out += central
        out += struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, len(self.items), len(self.items),
                           len(central), cd_off, 0)
        return bytes(out)


def _deflate(data: bytes) -> bytes:
    import zlib
    return zlib.compress(data, 9)[2:-4]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--dex", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--keystore", required=True)
    ap.add_argument("--keystore-pass-env", default="WOJAN_KEYSTORE_PASS")
    ap.add_argument("--extra", action="append", default=[],
                    help="dodatkowy plik do APK, format NAME=PATH")
    args = ap.parse_args()

    with open(args.manifest, "rb") as fh:
        manifest = fh.read()
    with open(args.dex, "rb") as fh:
        dex = fh.read()

    z = AlignedZip(args.out)
    z.add("AndroidManifest.xml", manifest, stored=True)
    z.add("classes.dex", dex, stored=True)
    for spec in args.extra:
        name, path = spec.split("=", 1)
        z.add(name, open(path, "rb").read(), stored=True)

    raw = z.build()
    identity = load_identity(args.keystore, args.keystore_pass_env)
    signed = sign_apk_bytes(raw, identity, with_v3=False)
    with open(args.out, "wb") as fh:
        fh.write(signed)

    res = verify_apk_bytes(signed)
    print(f"APK: {args.out} ({len(signed)} B)")
    print(f"podpis v2: {res}")
    if not res.get("v2"):
        raise SystemExit("weryfikacja podpisu nie powiodła się")
    return 0


if __name__ == "__main__":
    sys.exit(main())
