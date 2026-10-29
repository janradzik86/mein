#!/usr/bin/env python3
"""dexverify.py — wyrywkowa weryfikacja SEMANTYCZNA DEX (to, czego dexcheck nie widzi).

Sprawdza reguły, na których ART wywala klasę z błędem VerifyError. Najważniejsza:
`invoke-direct` w DEX wolno stosować WYŁĄCZNIE do `<init>` albo do metody prywatnej
tej samej klasy. Wywołanie `super.onDraw()` / `super.onCreate()` musi być `invoke-super`.
Konwerter .class → .smali mapujący każdy bajtkod `invokespecial` na `invoke-direct`
produkuje DEX, który ART odrzuca przy weryfikacji — aplikacja wtedy nie startuje
albo instalacja kończy się błędem dexopt.

Pozostałe kontrole:
  * invoke-interface → cel musi być interfejsem,
  * invoke-virtual  → cel nie może być interfejsem,
  * move-result*    → bezpośrednio po invoke, typ zgodny z typem zwracanym,
  * return*         → zgodny z deskryptorem metody,
  * new-instance    → nie może tworzyć interfejsu ani klasy abstrakcyjnej.

Użycie: python3 tools/dexverify.py classes.dex [android.jar]
Exit 0 = brak naruszeń.
"""
from __future__ import annotations

import struct
import sys
import zipfile

W = [1] * 256


def setw(ops, width):
    for o in ops:
        W[o] = width


setw([0x00], 1)
setw([0x01, 0x04, 0x07], 1)
setw([0x02, 0x05, 0x08], 2)
setw([0x03, 0x06, 0x09], 3)
setw(range(0x0a, 0x12), 1)
setw([0x12], 1)
setw([0x13, 0x15, 0x16, 0x19], 2)
setw([0x14, 0x17], 3)
setw([0x18], 5)
setw([0x1a, 0x1c], 2)
setw([0x1b], 3)
setw([0x1d, 0x1e], 1)
setw([0x1f, 0x20, 0x22, 0x23], 2)
setw([0x21], 1)
setw([0x24, 0x25], 3)
setw([0x26], 3)
setw([0x27, 0x28], 1)
setw([0x29], 2)
setw([0x2a], 3)
setw([0x2b, 0x2c], 3)
setw(range(0x2d, 0x32), 2)
setw(range(0x32, 0x3e), 2)
setw(range(0x44, 0x52), 2)
setw(range(0x52, 0x6e), 2)
setw(range(0x6e, 0x73), 3)
setw(range(0x74, 0x79), 3)
setw(range(0x7b, 0x90), 1)
setw(range(0x90, 0xb0), 2)
setw(range(0xb0, 0xd0), 1)
setw(range(0xd0, 0xd8), 2)
setw(range(0xd8, 0xe3), 2)
setw([0xfa, 0xfb, 0xfc, 0xfd], 3)
setw([0xfe, 0xff], 2)

INVOKE = {0x6e: "virtual", 0x6f: "super", 0x70: "direct", 0x71: "static", 0x72: "interface"}
INVOKE_RANGE = {0x74: "virtual", 0x75: "super", 0x76: "direct", 0x77: "static", 0x78: "interface"}
MOVE_RESULT = {0x0a: "int", 0x0b: "wide", 0x0c: "object"}
RETURN = {0x0e: "V", 0x0f: "int", 0x10: "wide", 0x11: "object"}


def uleb(d, p):
    v = 0
    s = 0
    while True:
        b = d[p]
        p += 1
        v |= (b & 0x7F) << s
        if not (b & 0x80):
            return v, p
        s += 7


def kind_of(desc: str) -> str:
    if desc == "V":
        return "V"
    if desc in ("J", "D"):
        return "wide"
    if desc.startswith("L") or desc.startswith("["):
        return "object"
    return "int"


class JarInfo:
    """leniwe odczytywanie flag dostępu klas z android.jar"""

    def __init__(self, path):
        self.z = zipfile.ZipFile(path)
        self.cache = {}

    def flags(self, desc):
        if desc in self.cache:
            return self.cache[desc]
        name = desc[1:-1] + ".class"
        try:
            data = self.z.read(name)
        except KeyError:
            self.cache[desc] = None
            return None
        f = access_flags(data)
        self.cache[desc] = f
        return f


def access_flags(data: bytes):
    """access_flags z pliku .class (po stałej puli)."""
    p = 8
    cnt = struct.unpack_from(">H", data, p)[0]
    p += 2
    i = 1
    while i < cnt:
        tag = data[p]
        p += 1
        if tag == 1:
            n = struct.unpack_from(">H", data, p)[0]
            p += 2 + n
        elif tag in (7, 8, 16, 19, 20):
            p += 2
        elif tag == 15:
            p += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            p += 4
        elif tag in (5, 6):
            p += 8
            i += 1
        else:
            return None
        i += 1
    return struct.unpack_from(">H", data, p)[0]


class Dex:
    def __init__(self, data):
        self.d = data
        (self.str_n, self.str_off, self.type_n, self.type_off,
         self.proto_n, self.proto_off, self.field_n, self.field_off,
         self.meth_n, self.meth_off, self.cls_n, self.cls_off) = struct.unpack_from("<12I", data, 56)
        self.strings = []
        for i in range(self.str_n):
            p = struct.unpack_from("<I", data, self.str_off + 4 * i)[0]
            n, p = uleb(data, p)
            self.strings.append(data[p:p + n].decode("utf-8", "replace"))
        self.types = [self.strings[struct.unpack_from("<I", data, self.type_off + 4 * i)[0]]
                      for i in range(self.type_n)]
        self.protos = []
        for i in range(self.proto_n):
            _s, ret, poff = struct.unpack_from("<III", data, self.proto_off + 12 * i)
            params = []
            if poff:
                n = struct.unpack_from("<I", data, poff)[0]
                params = [self.types[struct.unpack_from("<H", data, poff + 4 + 2 * k)[0]] for k in range(n)]
            self.protos.append((self.types[ret], tuple(params)))
        self.methods = []
        for i in range(self.meth_n):
            c, pr, nidx = struct.unpack_from("<HHI", data, self.meth_off + 8 * i)
            self.methods.append((self.types[c], self.strings[nidx], self.protos[pr]))
        self.classes = {}
        for i in range(self.cls_n):
            cd = self.cls_off + 32 * i
            (cidx, acc, sidx, _if, _src, _ann, cdo, _sv) = struct.unpack_from("<8I", data, cd)
            self.classes[self.types[cidx]] = {
                "acc": acc,
                "super": self.types[sidx] if sidx != 0xFFFFFFFF else None,
                "data": cdo,
            }

    def class_methods(self, cdo):
        """{ (nazwa, proto): (access, code_off) }"""
        out = {}
        if cdo == 0:
            return out
        p = cdo
        ss, p = uleb(self.d, p)
        ifs, p = uleb(self.d, p)
        dms, p = uleb(self.d, p)
        vms, p = uleb(self.d, p)
        for _ in range(ss + ifs):
            _, p = uleb(self.d, p)
            _, p = uleb(self.d, p)
        for cnt in (dms, vms):
            midx = 0
            for _ in range(cnt):
                diff, p = uleb(self.d, p)
                midx += diff
                acc, p = uleb(self.d, p)
                co, p = uleb(self.d, p)
                _cls, nm, proto = self.methods[midx]
                out[(nm, proto)] = (acc, co)
        return out


def main(argv):
    path = argv[1]
    jar_path = argv[2] if len(argv) > 2 else "/home/user/.cache/lc/android34.jar"
    dex = Dex(open(path, "rb").read())
    try:
        jar = JarInfo(jar_path)
    except OSError:
        jar = None

    def is_interface(desc):
        c = dex.classes.get(desc)
        if c is not None:
            return bool(c["acc"] & 0x200)
        if jar:
            f = jar.flags(desc)
            if f is not None:
                return bool(f & 0x200)
        return None

    def is_abstract(desc):
        c = dex.classes.get(desc)
        if c is not None:
            return bool(c["acc"] & 0x400)
        if jar:
            f = jar.flags(desc)
            if f is not None:
                return bool(f & 0x400)
        return None

    def is_private_here(cls_desc, nm, proto):
        c = dex.classes.get(cls_desc)
        if c is None:
            return False
        m = dex.class_methods(c["data"]).get((nm, proto))
        return bool(m and (m[0] & 0x2))

    errs = []
    n_inv = 0
    for cname, c in dex.classes.items():
        methods = dex.class_methods(c["data"])
        for (nm, proto), (acc, co) in methods.items():
            if not co:
                continue
            _regs, _ins, _outs, _tries, _dbg, isize = struct.unpack_from("<HHHHII", dex.d, co)
            insns = dex.d[co + 16: co + 16 + isize * 2]
            prev_invoke = None  # (kind, return-desc)
            off = 0
            payloads = {}
            while off < isize:
                if off in payloads:
                    off = payloads[off]
                    continue
                op = insns[off * 2]
                w = W[op]
                if op in (0x26, 0x2b, 0x2c):
                    rel = struct.unpack_from("<h", insns, off * 2 + 2)[0]
                    poff = off + rel
                    try:
                        ident = struct.unpack_from("<H", insns, poff * 2)[0]
                        if op == 0x2b:      # packed-switch
                            n = struct.unpack_from("<H", insns, poff * 2 + 2)[0]
                            end = poff + 4 + 2 * n
                        elif op == 0x2c:    # sparse-switch
                            n = struct.unpack_from("<H", insns, poff * 2 + 2)[0]
                            end = poff + 4 + 4 * n
                        else:               # fill-array-data
                            width = struct.unpack_from("<H", insns, poff * 2 + 2)[0]
                            n = struct.unpack_from("<I", insns, poff * 2 + 4)[0]
                            end = poff + (8 + width * n + 1) // 2
                        if 0 <= poff < isize and end <= isize:
                            payloads[poff] = end
                    except Exception:
                        pass
                if op in INVOKE or op in INVOKE_RANGE:
                    kind = INVOKE.get(op) or INVOKE_RANGE.get(op)
                    idx = struct.unpack_from("<H", insns, off * 2 + 2)[0]
                    tcls, tnm, (tret, _tp) = dex.methods[idx]
                    n_inv += 1
                    if kind == "direct" and tnm != "<init>":
                        if not (tcls == cname and is_private_here(cname, tnm, (tret, _tp))):
                            errs.append(f"{cname}.{nm}: invoke-direct do {tcls}->{tnm}"
                                        f" (dozwolone tylko <init> lub metoda prywatna tej klasy)")
                    elif kind == "interface":
                        ii = is_interface(tcls)
                        if ii is False:
                            errs.append(f"{cname}.{nm}: invoke-interface do klasy {tcls} (to nie interfejs)")
                    elif kind == "virtual":
                        ii = is_interface(tcls)
                        if ii is True:
                            errs.append(f"{cname}.{nm}: invoke-virtual do interfejsu {tcls}")
                    prev_invoke = (kind, tret)
                elif op in MOVE_RESULT:
                    want = MOVE_RESULT[op]
                    if prev_invoke is None:
                        errs.append(f"{cname}.{nm}: move-result bez invoke (offset {off})")
                    else:
                        got = kind_of(prev_invoke[1])
                        exp = "int" if want == "int" else want
                        if got != exp:
                            errs.append(f"{cname}.{nm}: move-result-{want}, a metoda zwraca {prev_invoke[1]}")
                    prev_invoke = None
                elif op in RETURN:
                    got = RETURN[op]
                    exp = kind_of(proto[0])
                    if got != exp:
                        errs.append(f"{cname}.{nm}: {op:#x} zwraca {got}, a deskryptor mówi {proto[0]}")
                    prev_invoke = None
                elif op == 0x22:
                    idx = struct.unpack_from("<H", insns, off * 2 + 2)[0]
                    t = dex.types[idx]
                    if is_interface(t) or is_abstract(t):
                        errs.append(f"{cname}.{nm}: new-instance abstrakcyjnego/interfejsu {t}")
                    prev_invoke = None
                else:
                    if op not in (0x00,):
                        prev_invoke = None
                off += w

    print(f"dexverify: {len(dex.classes)} klas, {n_inv} wywołań, {len(errs)} naruszeń weryfikacji ART")
    for e in errs[:40]:
        print("  ", e)
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
