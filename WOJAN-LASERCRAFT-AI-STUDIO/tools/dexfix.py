#!/usr/bin/env python3
"""dexfix.py — poprawia nagłówek wersji formatu DEX na wartość akceptowaną przez ART.

Dlaczego to istnieje:
    smali 1.4 (cache/lc/smali.jar) zapisuje ZAWSZE magic "dex\\n036", niezależnie od -a.
    ART (libdexfile/dex/standard_dex_file.cc) ma tablicę kDexMagicVersions:
        {'0','3','5'}, {'0','3','7'}, {'0','3','8'}, {'0','3','9'}, {'0','4','0'}, {'0','4','1'}
    z komentarzem „Dex version 036 skipped because of an old dalvik bug…".
    Wersja 036 NIE jest więc akceptowana przez żadne urządzenie: instalacja/uruchomienie
    kończy się błędem „Unknown dex version" (INSTALL_FAILED_INVALID_APK / crash przy starcie).

    Poprawka: nadpisz wersję na wynikającą z minSdk (24 → 037), przelicz adler32 i SHA-1.
    Magic leży poza zakresem obu sum kontrolnych, ale przeliczamy je i tak, żeby plik był
    zgodny ze specyfikacją.

Użycie:
    python3 tools/dexfix.py --api 24 classes.dex     # poprawia w miejscu, exit 1 jeśli nie da się
    python3 tools/dexfix.py --check classes.dex      # tylko sprawdza wersję
"""
from __future__ import annotations

import hashlib
import struct
import sys
import zlib

# Wersje akceptowane przez ART (standard_dex_file.cc, Android 15/16).
ART_VERSIONS = (b"035", b"037", b"038", b"039", b"040", b"041")

# api → wersja formatu DEX (oficjalna tabela; 036 nigdy nie była poprawna)
API_TO_VERSION = {
    23: b"035", 24: b"037", 25: b"037", 26: b"038", 27: b"038",
    28: b"039", 29: b"039", 30: b"039", 31: b"039", 32: b"039", 33: b"039", 34: b"039",
    35: b"040", 36: b"040",
}

# Opcody wymagające formatu ≥ 038 (invoke-custom / polymorphic / method handles).
OPS_NEED_038 = {0xFA, 0xFB, 0xFC, 0xFD, 0xFE, 0xFF}


def fix(path: str, api: int, check_only: bool = False) -> int:
    data = bytearray(open(path, "rb").read())
    if len(data) < 112 or bytes(data[:4]) != b"dex\n":
        print(f"dexfix: {path}: to nie jest plik DEX")
        return 1
    cur = bytes(data[4:7])
    want = API_TO_VERSION.get(api, b"037")
    print(f"dexfix: {path}: wersja w pliku = {cur.decode()}, docelowa (api {api}) = {want.decode()}")
    if cur == want:
        print("dexfix: wersja już poprawna — bez zmian")
        return 0
    if cur not in ART_VERSIONS and cur != b"036":
        print(f"dexfix: UWAGA nieznana wersja {cur!r} — zostawiam")
        return 1
    if check_only:
        print("dexfix: --check: wersja wymaga poprawki")
        return 1
    data[4:7] = want
    # SHA-1 obejmuje bajty od 32 do końca, adler32 od 12 do końca — magic nie wchodzi,
    # ale liczymy od nowa dla porządku.
    data[12:32] = hashlib.sha1(bytes(data[32:])).digest()
    struct.pack_into("<I", data, 8, zlib.adler32(bytes(data[12:])) & 0xFFFFFFFF)
    open(path, "wb").write(bytes(data))
    print(f"dexfix: poprawiono magic na dex\\n{want.decode()}\\0, przeliczono SHA-1 i adler32")
    return 0


def main(argv: list[str]) -> int:
    api = 24
    check = False
    args = list(argv[1:])
    if "--check" in args:
        check = True
        args.remove("--check")
    if "--api" in args:
        i = args.index("--api")
        api = int(args[i + 1])
        del args[i:i + 2]
    if len(args) != 1:
        print(__doc__)
        return 2
    return fix(args[0], api, check)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
