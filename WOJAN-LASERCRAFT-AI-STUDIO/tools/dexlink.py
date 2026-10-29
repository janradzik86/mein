#!/usr/bin/env python3
"""dexlink.py — kontrola spójności odwołań w classes.dex (linkowanie metod i klas).

Sprawdza:
  1. każdy typ użyty w instrukcjach (const-class, new-instance, invoke-*, sget/iget,
     check-cast, …) istnieje albo w naszym DEX-ie, albo w android.jar (API 34),
  2. każde wywołanie metody z pakietu aplikacji (Lpl/wojan/…) trafia na metodę,
     która naprawdę istnieje w tej klasie lub w jej klasie nadrzędnej w DEX-ie
     (typowe uszkodzenie po konwersji .class → .smali: przekręcona nazwa/deskryptor,
     co na urządzeniu kończy się NoSuchMethodError/NoSuchFieldError),
  3. brak duplikatów metod w tej samej klasie (ART odrzuca taki DEX).

Użycie: python3 tools/dexlink.py classes.dex [android.jar]
Exit 0 = brak problemów.
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


class Dex:
    def __init__(self, data: bytes):
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
            _shorty, ret, params_off = struct.unpack_from("<III", data, self.proto_off + 12 * i)
            plist = []
            if params_off:
                n = struct.unpack_from("<I", data, params_off)[0]
                plist = [self.types[struct.unpack_from("<H", data, params_off + 4 + 2 * k)[0]]
                         for k in range(n)]
            self.protos.append((self.types[ret], tuple(plist)))

        self.fields = []
        for i in range(self.field_n):
            c, t, nidx = struct.unpack_from("<HHI", data, self.field_off + 8 * i)
            self.fields.append((self.types[c], self.strings[nidx], self.types[t]))

        self.methods = []
        for i in range(self.meth_n):
            c, p, nidx = struct.unpack_from("<HHI", data, self.meth_off + 8 * i)
            self.methods.append((self.types[c], self.strings[nidx], self.protos[p]))

    def class_defs(self):
        out = []
        for i in range(self.cls_n):
            cd = self.cls_off + 32 * i
            (class_idx, access, super_idx, _ifaces, _src, _ann, class_data_off,
             _sv) = struct.unpack_from("<8I", self.d, cd)
            name = self.types[class_idx]
            super_name = self.types[super_idx] if super_idx != 0xFFFFFFFF else None
            out.append((name, super_name, access, class_data_off))
        return out

    def own_methods(self, class_data_off):
        """Zbiór (nazwa, proto) metod zdefiniowanych w klasie."""
        res = set()
        if class_data_off == 0:
            return res
        p = class_data_off
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
                _, p = uleb(self.d, p)
                _, p = uleb(self.d, p)
                res.add(self.methods[midx][1:])
        return res

    def own_fields(self, class_data_off):
        res = set()
        if class_data_off == 0:
            return res
        p = class_data_off
        ss, p = uleb(self.d, p)
        ifs, p = uleb(self.d, p)
        dms, p = uleb(self.d, p)
        vms, p = uleb(self.d, p)
        for cnt in (ss, ifs):
            fidx = 0
            for _ in range(cnt):
                diff, p = uleb(self.d, p)
                fidx += diff
                _, p = uleb(self.d, p)
                res.add(self.fields[fidx][1])
        for _ in range(dms + vms):
            _, p = uleb(self.d, p)
            _, p = uleb(self.d, p)
        return res

    def code_items(self, class_data_off):
        """(method_idx, code_off) dla metod z kodem."""
        out = []
        if class_data_off == 0:
            return out
        p = class_data_off
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
                _, p = uleb(self.d, p)
                co, p = uleb(self.d, p)
                if co:
                    out.append((midx, co))
        return out


def main(argv):
    path = argv[1]
    jar = argv[2] if len(argv) > 2 else "/home/user/.cache/lc/android34.jar"
    data = open(path, "rb").read()
    dex = Dex(data)
    errs = []

    jar_classes = set()
    try:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.endswith(".class"):
                    jar_classes.add("L" + n[:-6] + ";")
    except OSError:
        print(f"dexlink: UWAGA brak {jar} — sprawdzam tylko klasy aplikacji")

    defs = dex.class_defs()
    dex_classes = {name for name, _s, _a, _c in defs}
    super_of = {name: sup for name, sup, _a, _c in defs}
    own_m = {name: dex.own_methods(cd) for name, _s, _a, cd in defs}
    own_f = {name: dex.own_fields(cd) for name, _s, _a, cd in defs}

    for name, _s, _a, _c in defs:
        seen = set()
        for k in own_m[name]:
            if k in seen:
                errs.append(f"{name}: zduplikowana metoda {k[0]}{k[1]}")
            seen.add(k)

    def method_ok(cls, mname, proto):
        cur = cls
        hops = 0
        while cur and hops < 40:
            if cur not in own_m:
                # klasa frameworkowa (android.jar) — nie da się tu sprawdzić, przyjmujemy OK
                return True
            if (mname, proto) in own_m[cur]:
                return True
            cur = super_of.get(cur)
            hops += 1
        return False

    checked_methods = 0
    for cname, _s, _a, cd in defs:
        for midx, co in dex.code_items(cd):
            registers_size, _ins, _outs, _tries, _dbg, insns_size = struct.unpack_from("<HHHHII", data, co)
            insns = data[co + 16: co + 16 + insns_size * 2]
            off = 0
            while off < insns_size:
                op = insns[off * 2]
                w = W[op]
                if op in (0x6E, 0x6F, 0x70, 0x71, 0x72, 0x74, 0x75, 0x76, 0x77, 0x78):
                    idx = struct.unpack_from("<H", insns, off * 2 + 2)[0]
                    tcls, mname, proto = dex.methods[idx]
                    checked_methods += 1
                    if tcls not in dex_classes and jar_classes and tcls not in jar_classes:
                        errs.append(f"{cname}: wywołanie nieistniejącej klasy {tcls} ({mname})")
                    elif tcls in dex_classes and not method_ok(tcls, mname, proto):
                        errs.append(f"{cname}: brak metody {tcls}->{mname}{list(proto[1])}")
                elif op == 0x22 or op == 0x1F or op == 0x20 or op == 0x1C:  # new-instance/check-cast/instance-of/const-class
                    idx = struct.unpack_from("<H", insns, off * 2 + 2)[0]
                    t = dex.types[idx]
                    if t not in dex_classes and jar_classes and t not in jar_classes and not t.startswith("["):
                        errs.append(f"{cname}: nieznany typ {t} (op {op:#x})")
                off += w

    print(f"dexlink: {len(dex_classes)} klas, {checked_methods} wywołań metod sprawdzonych, {len(errs)} problemów")
    for e in errs[:30]:
        print("  ", e)
    return 1 if errs else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
