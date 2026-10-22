#!/usr/bin/env python3
"""j2smali — translator JVM bytecode (.class) -> smali (dex) dla WOJAN AI FACTORY.

Łańcuch budowy APK bez Google SDK:
    ECJ (javac) -> .class -> j2smali -> .smali -> smali.jar -> classes.dex

Strategia rejestrów: okno stosu JVM mapowane 1:1 na rejetry dex
(vS[s] = slot stosu s) — brak alokacji rejestrów, translacja mechaniczna.
Parametry metody zajmują ostatnie rejetry (konwencja smali .registers).
Śledzimy głębokość stosu i typy (INT/FLOAT/REF/WIDE) w analizie dataflow,
dzięki czemu wybór move/move-object/move-wide jest zgodny z weryfikatorem ART.

Nieobsługiwane (świadomie, nie występuje w kodzie ECJ 1.8): multianewarray,
invokedynamic, JSR/RET.
"""

from __future__ import annotations

import struct
import sys

TAG_UTF8, TAG_INT, TAG_FLOAT, TAG_LONG, TAG_DOUBLE = 1, 3, 4, 5, 6
TAG_CLASS, TAG_STRING, TAG_FIELD, TAG_METHOD, TAG_IFACE_METHOD = 7, 8, 9, 10, 11
TAG_NAME_TYPE, TAG_METHOD_HANDLE, TAG_METHOD_TYPE = 12, 15, 16

T_INT, T_FLOAT, T_REF, T_WIDE, T_TOP = "I", "F", "A", "W", "?"


class ClassFormatError(Exception):
    pass


class ConstantPool:
    def __init__(self, entries):
        self.entries = entries

    def utf8(self, i):
        tag, v = self.entries[i]
        if tag != TAG_UTF8:
            raise ClassFormatError(f"#{i} not Utf8")
        return v

    def class_name(self, i):
        tag, v = self.entries[i]
        if tag != TAG_CLASS:
            raise ClassFormatError(f"#{i} not Class")
        return self.utf8(v)

    def string(self, i):
        tag, v = self.entries[i]
        return self.utf8(v)

    def name_and_type(self, i):
        tag, v = self.entries[i]
        return self.utf8(v[0]), self.utf8(v[1])

    def ref(self, i):
        tag, v = self.entries[i]
        if tag not in (TAG_FIELD, TAG_METHOD, TAG_IFACE_METHOD):
            raise ClassFormatError(f"#{i} not ref")
        cls_i, nt_i = v
        name, desc = self.name_and_type(nt_i)
        return tag, self.class_name(cls_i), name, desc

    def const(self, i):
        return self.entries[i]


class Reader:
    def __init__(self, data):
        self.d, self.p = data, 0

    def u1(self):
        v = self.d[self.p]; self.p += 1; return v

    def u2(self):
        v = struct.unpack_from(">H", self.d, self.p)[0]; self.p += 2; return v

    def u4(self):
        v = struct.unpack_from(">I", self.d, self.p)[0]; self.p += 4; return v

    def bytes(self, n):
        v = self.d[self.p : self.p + n]; self.p += n; return v


def parse_constant_pool(r):
    count = r.u2()
    entries, i = {}, 1
    while i < count:
        tag = r.u1()
        if tag == TAG_UTF8:
            entries[i] = (tag, r.bytes(r.u2()).decode("utf-8", "surrogateescape"))
        elif tag == TAG_INT:
            entries[i] = (tag, struct.unpack(">i", r.bytes(4))[0])
        elif tag == TAG_FLOAT:
            entries[i] = (tag, struct.unpack(">f", r.bytes(4))[0])
        elif tag == TAG_LONG:
            entries[i] = (tag, struct.unpack(">q", r.bytes(8))[0]); i += 1
        elif tag == TAG_DOUBLE:
            entries[i] = (tag, struct.unpack(">d", r.bytes(8))[0]); i += 1
        elif tag in (TAG_CLASS, TAG_STRING, TAG_METHOD_TYPE):
            entries[i] = (tag, r.u2())
        elif tag in (TAG_FIELD, TAG_METHOD, TAG_IFACE_METHOD, TAG_NAME_TYPE):
            entries[i] = (tag, (r.u2(), r.u2()))
        elif tag == TAG_METHOD_HANDLE:
            entries[i] = (tag, (r.u1(), r.u2()))
        else:
            raise ClassFormatError(f"bad tag {tag}")
        i += 1
    return ConstantPool(entries)


def parse_class(data):
    r = Reader(data)
    if r.u4() != 0xCAFEBABE:
        raise ClassFormatError("not a class file")
    r.u2(); r.u2()
    cp = parse_constant_pool(r)
    access = r.u2()
    name = cp.class_name(r.u2())
    super_i = r.u2()
    super_name = cp.class_name(super_i) if super_i else None
    ifaces = [cp.class_name(r.u2()) for _ in range(r.u2())]

    def attrs():
        out = []
        for _ in range(r.u2()):
            n = cp.utf8(r.u2())
            out.append((n, r.bytes(r.u4())))
        return out

    fields = []
    for _ in range(r.u2()):
        acc, nm, ds = r.u2(), cp.utf8(r.u2()), cp.utf8(r.u2())
        cv = None
        for n, d in attrs():
            if n == "ConstantValue":
                cv = struct.unpack(">H", d)[0]
        fields.append((acc, nm, ds, cv))

    methods = []
    for _ in range(r.u2()):
        acc, nm, ds = r.u2(), cp.utf8(r.u2()), cp.utf8(r.u2())
        code = None
        for n, d in attrs():
            if n == "Code":
                cr = Reader(d)
                ms, ml = cr.u2(), cr.u2()
                bc = cr.bytes(cr.u4())
                exc = [tuple(cr.u2() for _ in range(4)) for _ in range(cr.u2())]
                code = (ms, ml, bc, exc)
        methods.append((acc, nm, ds, code))

    return {
        "access": access, "name": name, "super": super_name,
        "interfaces": ifaces, "fields": fields, "methods": methods, "cp": cp,
    }


# ---------------------------------------------------------------- descriptors

def split_params(desc):
    params, i = [], 1
    while desc[i] != ")":
        start = i
        while desc[i] == "[":
            i += 1
        if desc[i] == "L":
            i = desc.index(";", i)
        i += 1
        params.append(desc[start:i])
    return params, desc[i + 1 :]


def slot_count(t):
    return 2 if t in ("J", "D") else 1


def type_of_desc(t):
    if t in ("J", "D"):
        return T_WIDE
    if t[0] in ("L", "["):
        return T_REF
    if t in ("F",):
        return T_FLOAT
    return T_INT


def escape_str(s):
    out = []
    for ch in s:
        o = ord(ch)
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        elif o < 0x20 or o == 0x7F:
            out.append(f"\\u{o:04x}")
        else:
            out.append(ch)
    return '"' + "".join(out) + '"'


# instruction lengths (default 1); opcode -> length
FIXED = {}
for op, ln in [
    *[(o, 1) for o in range(0x00, 0x10)],
    (0x10, 2), (0x11, 3), (0x12, 2), (0x13, 3), (0x14, 3),
    (0x15, 2), (0x16, 2), (0x17, 2), (0x18, 2), (0x19, 2),
    *[(o, 1) for o in range(0x1A, 0x36)],
    (0x36, 2), (0x37, 2), (0x38, 2), (0x39, 2), (0x3A, 2),
    *[(o, 1) for o in range(0x3B, 0x84)],
    (0x84, 3),
    *[(o, 1) for o in range(0x85, 0x99)],
    *[(o, 3) for o in range(0x99, 0xA8)],
    *[(o, 1) for o in range(0xA8, 0xB2)],
    *[(o, 3) for o in range(0xB2, 0xB9)],
    (0xB9, 5), (0xBA, 5), (0xBB, 3), (0xBC, 2), (0xBD, 3),
    (0xBE, 1), (0xBF, 1), (0xC0, 3), (0xC1, 3), (0xC2, 1), (0xC3, 1),
    (0xC6, 3), (0xC7, 3), (0xC8, 5), (0xC9, 5),
]:
    FIXED[op] = ln


def insn_len(cb, i):
    op = cb[i]
    if op == 0xC4:  # wide
        return 6 if cb[i + 1] == 0x84 else 4
    if op == 0xAA:  # tableswitch
        p = (i + 4) & ~3
        low = struct.unpack_from(">i", cb, p + 4)[0]
        high = struct.unpack_from(">i", cb, p + 8)[0]
        return (p + 12 + 4 * (high - low + 1)) - i
    if op == 0xAB:  # lookupswitch
        p = (i + 4) & ~3
        npairs = struct.unpack_from(">i", cb, p + 4)[0]
        return (p + 8 + 8 * npairs) - i
    return FIXED.get(op, 1)


def branch_targets(cb):
    """Zwraca (pctargets, switch_payloads)."""
    targets, payloads = set(), {}
    i = 0
    while i < len(cb):
        op = cb[i]
        if 0x99 <= op <= 0xA7 or op in (0xC6, 0xC7):
            targets.add(i + struct.unpack_from(">h", cb, i + 1)[0])
        elif op in (0xC8, 0xC9):
            targets.add(i + struct.unpack_from(">i", cb, i + 1)[0])
        elif op == 0xAA:
            p = (i + 4) & ~3
            default = struct.unpack_from(">i", cb, p)[0]
            low = struct.unpack_from(">i", cb, p + 4)[0]
            high = struct.unpack_from(">i", cb, p + 8)[0]
            targets.add(i + default)
            for k in range(high - low + 1):
                targets.add(i + struct.unpack_from(">i", cb, p + 12 + 4 * k)[0])
            payloads[i] = ("packed", p, low, high)
        elif op == 0xAB:
            p = (i + 4) & ~3
            default = struct.unpack_from(">i", cb, p)[0]
            npairs = struct.unpack_from(">i", cb, p + 4)[0]
            targets.add(i + default)
            for k in range(npairs):
                targets.add(i + struct.unpack_from(">i", cb, p + 8 + 8 * k + 4)[0])
            payloads[i] = ("lookup", p, npairs, None)
        i += insn_len(cb, i)
    return targets, payloads


# Mapy JVM (zgodne z sekcją 6.5 specyfikacji — UWAGA na 0x78-0x83):
INT_OPS = {0x60: "add-int", 0x64: "sub-int", 0x68: "mul-int", 0x6C: "div-int",
           0x70: "rem-int", 0x7E: "and-int", 0x80: "or-int", 0x82: "xor-int"}
LONG_OPS = {0x61: "add-long", 0x65: "sub-long", 0x69: "mul-long", 0x6D: "div-long",
            0x71: "rem-long", 0x7F: "and-long", 0x81: "or-long", 0x83: "xor-long"}
FLOAT_OPS = {0x62: "add-float", 0x66: "sub-float", 0x6A: "mul-float",
             0x6E: "div-float", 0x72: "rem-float"}
DOUBLE_OPS = {0x63: "add-double", 0x67: "sub-double", 0x6B: "mul-double",
              0x6F: "div-double", 0x73: "rem-double"}
INT_SHIFTS = {0x78: "shl-int", 0x7A: "shr-int", 0x7C: "ushr-int"}
LONG_SHIFTS = {0x79: "shl-long", 0x7B: "shr-long", 0x7D: "ushr-long"}
ARRAY_LOAD = {0x2E: ("aget", T_INT), 0x2F: ("aget-wide", T_WIDE),
              0x30: ("aget", T_FLOAT), 0x31: ("aget-wide", T_WIDE),
              0x32: ("aget-object", T_REF), 0x33: ("aget-byte", T_INT),
              0x34: ("aget-char", T_INT), 0x35: ("aget-short", T_INT)}
ARRAY_STORE = {0x4F: "aput", 0x50: "aput-wide", 0x51: "aput", 0x52: "aput-wide",
               0x53: "aput-object", 0x54: "aput-byte", 0x55: "aput-char",
               0x56: "aput-short"}
ATYPE = {4: "[Z", 5: "[C", 6: "[F", 7: "[D", 8: "[B", 9: "[S", 10: "[I", 11: "[J"}
CMP_BRANCH = {0x9F: "if-eq", 0xA0: "if-ne", 0xA1: "if-lt", 0xA2: "if-ge",
              0xA3: "if-gt", 0xA4: "if-le"}
Z_BRANCH = {0x99: "if-eqz", 0x9A: "if-nez", 0x9B: "if-ltz", 0x9C: "if-gez",
            0x9D: "if-gtz", 0x9E: "if-lez"}

ACC_CLASS = [(0x1, "public"), (0x10, "final"), (0x200, "interface"),
             (0x400, "abstract"), (0x1000, "synthetic"), (0x2000, "annotation"),
             (0x4000, "enum")]
ACC_METHOD = [(0x1, "public"), (0x2, "private"), (0x4, "protected"),
              (0x8, "static"), (0x10, "final"), (0x20, "synchronized"),
              (0x40, "bridge"), (0x80, "varargs"), (0x100, "native"),
              (0x400, "abstract"), (0x800, "strict"), (0x1000, "synthetic"),
              (0x10000, "constructor")]
ACC_FIELD = [(0x1, "public"), (0x2, "private"), (0x4, "protected"),
             (0x8, "static"), (0x10, "final"), (0x40, "volatile"),
             (0x80, "transient"), (0x1000, "synthetic"), (0x4000, "enum")]


def flags(acc, table):
    return [n for b, n in table if acc & b]


class MethodTranslator:
    def __init__(self, cf, acc, name, desc, code):
        self.cf, self.acc, self.name, self.desc, self.code = cf, acc, name, desc, code
        self.cp = cf["cp"]
        self.lines = []
        self.params, self.ret = split_params(desc)
        self.is_static = bool(acc & 0x8)
        self.P = len(self.params) + (0 if self.is_static else 1)
        self.np_locals = code[1] - self.P          # nie-parametrowe lokale
        self.max_stack = code[0]
        self.WIN = 6                               # v0..v5 zarezerwowane na staging
        self.N = self.WIN + self.np_locals + self.max_stack + self.P
        self.scr = 0                               # v0..v5
        self.label_names = {}

    # --- rejestry ---------------------------------------------------------
    def S(self, s):
        return f"v{self.WIN + self.np_locals + s}"

    def L(self, slot):
        if slot < self.P:
            return f"v{self.N - self.P + slot}"
        return f"v{self.WIN + slot - self.P}"

    def _regnum(self, name):
        return int(name[1:])

    def SCR(self, k=0):
        return f"v{self.scr + k}"

    def SSCR(self, k=0):
        return f"v{self.scr + k}"

    def label(self, pc):
        if pc not in self.label_names:
            self.label_names[pc] = f":L{pc}"
        return self.label_names[pc]

    @staticmethod
    def lref(cn):
        """Referencja typu dex: tablice zostają jak w deskryptorze."""
        return cn if cn.startswith("[") else f"L{cn};"

    def _mov(self, w, t, dst, src):
        base = {T_REF: "move-object", T_WIDE: "move-wide"}.get(t, "move")
        if self._regnum(dst) < 16 and self._regnum(src) < 16:
            w(f"    {base} {dst}, {src}")
        else:
            w(f"    {base}/from16 {dst}, {src}")

    def _iget(self, w, t, dest, objreg, fref):
        if self._regnum(dest) < 16 and self._regnum(objreg) < 16:
            kind = "iget-object" if t == T_REF else ("iget-wide" if t == T_WIDE else "iget")
            w(f"    {kind} {dest}, {objreg}, {fref}")
            return
        kind = "iget-object" if t == T_REF else ("iget-wide" if t == T_WIDE else "iget")
        if t == T_WIDE:
            # obiekt w v0, wynik w parze v2,v3 — bez nakładania się rejestrów (weryfikator ART)
            w(f"    move-object/from16 v0, {objreg}")
            w(f"    {kind} v2, v0, {fref}")
            self._mov(w, t, dest, "v2")
        else:
            w(f"    move-object/from16 v1, {objreg}")
            w(f"    {kind} v0, v1, {fref}")
            self._mov(w, t, dest, "v0")

    def _iput(self, w, t, valreg, objreg, fref):
        kind = "iput-object" if t == T_REF else ("iput-wide" if t == T_WIDE else "iput")
        if self._regnum(valreg) < 16 and self._regnum(objreg) < 16:
            w(f"    {kind} {valreg}, {objreg}, {fref}")
            return
        self._mov(w, t, "v0", valreg)
        w(f"    move-object/from16 v2, {objreg}")
        w(f"    {kind} v0, v2, {fref}")

    def _sget(self, w, t, dest, fref):
        kind = "sget-object" if t == T_REF else ("sget-wide" if t == T_WIDE else "sget")
        w(f"    {kind} {dest}, {fref}")

    def _sput(self, w, t, valreg, fref):
        kind = "sput-object" if t == T_REF else ("sput-wide" if t == T_WIDE else "sput")
        w(f"    {kind} {valreg}, {fref}")

    # --- typy -------------------------------------------------------------
    def move_op(self, t):
        return {T_REF: "move-object", T_WIDE: "move-wide"}.get(t, "move")

    def arg_type(self, i):
        return type_of_desc(self.params[i]) if self.P else T_TOP

    # --- dataflow: (depth, types) per pc ----------------------------------
    def compute_flows(self):
        cb = self.code[2]
        exc = self.code[3]
        types0 = [T_TOP] * self.N
        if not self.is_static:
            types0[self.N - self.P] = T_REF
        for k, p in enumerate(self.params):
            types0[self.N - self.P + (0 if self.is_static else 1) + k] = type_of_desc(p)

        work = [(0, 0, types0)]
        for s, e, h, t in exc:
            ht = [T_TOP] * self.N
            ht[self.WIN + self.np_locals] = T_REF  # wyjątek na S(0)
            work.append((h, 1, ht))

        def push(pc, depth, types):
            work.append((pc, depth, list(types)))

        flows = {}
        while work:
            pc, sp, types = work.pop()
            types = list(types)
            i = pc
            while i < len(cb):
                if i in flows:
                    old_d, old_t = flows[i]
                    if old_d != sp:
                        raise ClassFormatError(
                            f"stack depth mismatch @ {i}: {old_d} vs {sp}")
                    merged = [a if a == b else T_TOP for a, b in zip(old_t, types)]
                    if merged == list(old_t):
                        break
                    types = merged
                flows[i] = (sp, tuple(types))
                ns, nt = self.step(cb, i, sp, types, push)
                if ns is None:
                    break
                sp, types = ns, nt
                i += insn_len(cb, i)
        self.flows = {pc: (d, list(t)) for pc, (d, t) in flows.items()}

    def step(self, cb, i, sp, types, push):
        """Jedna instrukcja: aktualizuje sp/typy; wywoluje push na skokach."""
        op = cb[i]
        cp = self.cp

        def setS(s, t):
            idx = self.WIN + self.np_locals + s
            types[idx] = t
            if t == T_WIDE:
                types[idx + 1] = T_WIDE

        def getS(s):
            return types[self.WIN + self.np_locals + s]

        def setL(slot, t):
            if slot < self.P:
                return
            types[self.WIN + slot - self.P] = t
            if t == T_WIDE:
                types[self.WIN + slot - self.P + 1] = T_WIDE

        wide = lambda t: 2 if t == T_WIDE else 1

        if op == 0x00:
            pass
        elif op == 0x01:
            setS(sp, T_REF); sp += 1
        elif 0x02 <= op <= 0x08:
            setS(sp, T_INT); sp += 1
        elif op in (0x09, 0x0A):
            setS(sp, T_WIDE); sp += 2
        elif 0x0B <= op <= 0x0D:
            setS(sp, T_FLOAT); sp += 1
        elif op in (0x0E, 0x0F):
            setS(sp, T_WIDE); sp += 2
        elif op in (0x10, 0x11):
            setS(sp, T_INT); sp += 1
        elif op in (0x12, 0x13):
            idx = cb[i + 1] if op == 0x12 else struct.unpack_from(">H", cb, i + 1)[0]
            tag, _ = cp.const(idx)
            t = {TAG_INT: T_INT, TAG_FLOAT: T_FLOAT, TAG_STRING: T_REF,
                 TAG_CLASS: T_REF}.get(tag, T_INT)
            setS(sp, t); sp += 1
        elif op == 0x14:
            setS(sp, T_WIDE); sp += 2
        elif op in (0x15, 0x16, 0x17, 0x18, 0x19):
            t = {0x15: T_INT, 0x16: T_WIDE, 0x17: T_FLOAT, 0x18: T_WIDE,
                 0x19: T_REF}[op]
            setS(sp, t); sp += wide(t)
        elif 0x1A <= op <= 0x2D:
            t = (T_INT, T_WIDE, T_FLOAT, T_WIDE, T_REF)[(op - 0x1A) // 4]
            setS(sp, t); sp += wide(t)
        elif op in (0x36, 0x37, 0x38, 0x39, 0x3A):
            t = {0x36: T_INT, 0x37: T_WIDE, 0x38: T_FLOAT, 0x39: T_WIDE,
                 0x3A: T_REF}[op]
            sp -= wide(t); setL(cb[i + 1], t)
        elif 0x3B <= op <= 0x4E:
            t = (T_INT, T_WIDE, T_FLOAT, T_WIDE, T_REF)[(op - 0x3B) // 4]
            sp -= wide(t); setL((op - 0x3B) % 4, t)
        elif op == 0x84:
            pass
        elif op in ARRAY_LOAD:
            _, rt = ARRAY_LOAD[op]
            sp -= 2; setS(sp, rt); sp += wide(rt)
        elif op in ARRAY_STORE:
            sp -= 3
        elif op == 0xBE:
            pass  # array-length: typ bez zmian (int)
        elif op in (0xBC, 0xBD):
            sp -= 1; setS(sp, T_REF); sp += 1
        elif op == 0xC5:
            raise ClassFormatError("multianewarray")
        elif op == 0x57:
            sp -= 1
        elif op == 0x58:
            sp -= 2
        elif op == 0x59:
            setS(sp, getS(sp - 1)); sp += 1
        elif op in (0x5A, 0x5B):
            setS(sp, getS(sp - 1)); sp += 1
        elif op in (0x5C, 0x5D, 0x5E):
            if op == 0x5C and getS(sp - 1) == T_WIDE:
                setS(sp, T_WIDE); sp += 2
            elif op == 0x5D and getS(sp - 1) == T_WIDE:
                setS(sp, T_WIDE); sp += 2
            elif op == 0x5E and getS(sp - 1) == T_WIDE and getS(sp - 3) != T_WIDE:
                setS(sp, T_WIDE); sp += 2
            else:
                setS(sp, getS(sp - 2)); setS(sp + 1, getS(sp - 1)); sp += 2
        elif op == 0x5F:
            pass
        elif op in INT_OPS or op in FLOAT_OPS or op in INT_SHIFTS:
            sp -= 1
        elif op in LONG_OPS or op in DOUBLE_OPS:
            sp -= 2
        elif op in LONG_SHIFTS:
            sp -= 3
        elif op in (0x74, 0x75, 0x76, 0x77):
            pass
        elif 0x85 <= op <= 0x93:
            t = {0x85: T_WIDE, 0x86: T_FLOAT, 0x87: T_WIDE, 0x88: T_INT,
                 0x89: T_FLOAT, 0x8A: T_WIDE, 0x8B: T_INT, 0x8C: T_WIDE,
                 0x8D: T_WIDE, 0x8E: T_INT, 0x8F: T_WIDE, 0x90: T_FLOAT,
                 0x91: T_INT, 0x92: T_INT, 0x93: T_INT}[op]
            sp -= 1
            if op in (0x85, 0x87, 0x8A, 0x8D):
                sp -= 1
            setS(sp, t); sp += wide(t)
        elif op == 0x94:
            sp -= 3; setS(sp, T_INT); sp += 1
        elif op in (0x95, 0x96):
            sp -= 1
        elif op in (0x97, 0x98):
            sp -= 3; setS(sp, T_INT); sp += 1
        elif op in Z_BRANCH or op in (0xC6, 0xC7):
            sp -= 1
            push(i + struct.unpack_from(">h", cb, i + 1)[0], sp, types)
        elif op in CMP_BRANCH or op in (0xA5, 0xA6):
            sp -= 2
            push(i + struct.unpack_from(">h", cb, i + 1)[0], sp, types)
        elif op == 0xA7:
            push(i + struct.unpack_from(">h", cb, i + 1)[0], sp, types)
            return None, None
        elif op in (0xC8, 0xC9):
            push(i + struct.unpack_from(">i", cb, i + 1)[0], sp, types)
            return None, None
        elif op == 0xAA:
            sp -= 1
            p = (i + 4) & ~3
            default = struct.unpack_from(">i", cb, p)[0]
            low = struct.unpack_from(">i", cb, p + 4)[0]
            high = struct.unpack_from(">i", cb, p + 8)[0]
            push(i + default, sp, types)
            for k in range(high - low + 1):
                push(i + struct.unpack_from(">i", cb, p + 12 + 4 * k)[0], sp, types)
            return None, None
        elif op == 0xAB:
            sp -= 1
            p = (i + 4) & ~3
            default = struct.unpack_from(">i", cb, p)[0]
            npairs = struct.unpack_from(">i", cb, p + 4)[0]
            push(i + default, sp, types)
            for k in range(npairs):
                push(i + struct.unpack_from(">i", cb, p + 8 + 8 * k + 4)[0], sp, types)
            return None, None
        elif op in (0xAC, 0xAD, 0xAE, 0xAF, 0xB0, 0xB1):
            return None, None
        elif op == 0xB2:
            idx = struct.unpack_from(">H", cb, i + 1)[0]
            _, _, _, d = cp.ref(idx)
            t = type_of_desc(d)
            setS(sp, t); sp += wide(t)
        elif op == 0xB3:
            idx = struct.unpack_from(">H", cb, i + 1)[0]
            _, _, _, d = cp.ref(idx)
            sp -= wide(type_of_desc(d))
        elif op == 0xB4:
            idx = struct.unpack_from(">H", cb, i + 1)[0]
            _, _, _, d = cp.ref(idx)
            t = type_of_desc(d)
            sp -= 1; setS(sp, t); sp += wide(t)
        elif op == 0xB5:
            idx = struct.unpack_from(">H", cb, i + 1)[0]
            _, _, _, d = cp.ref(idx)
            sp -= 1 + wide(type_of_desc(d))
        elif op in (0xB6, 0xB7, 0xB8, 0xB9):
            idx = struct.unpack_from(">H", cb, i + 1)[0]
            _, _, _, d = cp.ref(idx)
            args, ret = split_params(d)
            arg_slots = sum(slot_count(a) for a in args) + (0 if op == 0xB8 else 1)
            sp -= arg_slots
            if ret != "V":
                t = type_of_desc(ret)
                setS(sp, t); sp += wide(t)
        elif op == 0xBB:
            setS(sp, T_REF); sp += 1
        elif op == 0xBF:
            return None, None
        elif op == 0xC0:
            setS(sp - 1, T_REF)
        elif op == 0xC1:
            sp -= 1; setS(sp, T_INT); sp += 1
        elif op in (0xC2, 0xC3):
            sp -= 1
        else:
            raise ClassFormatError(f"step: opcode {op:#x} @ {i}")
        return sp, types

    # --- emisja ------------------------------------------------------------
    def emit_all(self):
        cb, exc = self.code[2], self.code[3]
        targets, payloads = branch_targets(cb)
        for s, e, h, t in exc:
            targets.update((s, e, h))

        # wszystkie etykiety muszą powstać PRZED emisją (dla odwołań wstecznych)
        for pc in targets:
            self.label(pc)
        for s, e, h, t in exc:
            self.label(s); self.label(e); self.label(h)
        names = self.name
        extra = "constructor " if self.name in ("<init>", "<clinit>") and "constructor" not in flags(self.acc, ACC_METHOD) else ""
        self.lines.append(f".method {' '.join(flags(self.acc, ACC_METHOD))} {extra}{self.name}{self.desc}")
        self.lines.append(f"    .registers {self.N}")
        for s, e, h, t in exc:
            if t == 0:
                self.lines.append(f"    .catchall {{{self.label(s)} .. {self.label(e)}}} {self.label(h)}")
            else:
                cn = self.cp.class_name(t)
                self.lines.append(f"    .catch L{cn}; {{{self.label(s)} .. {self.label(e)}}} {self.label(h)}")

        handler_pcs = {h for s, e, h, t in exc}
        i = 0
        while i < len(cb):
            if i in self.label_names:
                self.lines.append(f"{self.label_names[i]}")
            if i in handler_pcs:
                self.lines.append(f"    move-exception {self.S(0)}")
            self.emit_insn(cb, i)
            i += insn_len(cb, i)
        if len(cb) in self.label_names:
            self.lines.append(f"{self.label_names[len(cb)]}")

        for i, (kind, p, a, b) in payloads.items():
            if kind == "packed":
                self.lines.append(f"    :pswitch_{i}")
                self.lines.append(f"    .packed-switch {a}")
                low, high = a, b
                for k in range(high - low + 1):
                    off = struct.unpack_from(">i", cb, p + 12 + 4 * k)[0]
                    self.lines.append(f"        {self.label(i + off)}")
                self.lines.append("    .end packed-switch")
            else:
                self.lines.append(f"    :sswitch_{i}")
                self.lines.append("    .sparse-switch")
                for k in range(a):
                    key = struct.unpack_from(">i", cb, p + 8 + 8 * k)[0]
                    off = struct.unpack_from(">i", cb, p + 8 + 8 * k + 4)[0]
                    self.lines.append(f"        {key} -> {self.label(i + off)}")
                self.lines.append("    .end sparse-switch")
        self.lines.append(".end method")
        self.lines.append("")

    def emit_insn(self, cb, i):
        op = cb[i]
        w = self.lines.append
        sp0, types = self.flows.get(i, (0, [T_TOP] * self.N))
        sp = sp0

        def tS(s):
            return types[self.WIN + self.np_locals + s]

        def tL(slot):
            return types[self.N - self.P + slot] if slot < self.P else types[self.WIN + slot - self.P]

        def mv(t):
            # wariant /from16 jest legalny dla wszystkich rejestrów < 256
            return {T_REF: "move-object/from16", T_WIDE: "move-wide/from16"}.get(t, "move/from16")

        cp = self.cp

        # stałe / stos
        if op == 0x00:
            w("    nop"); return
        if op == 0x01:
            dst = self.S(sp)
            if self._regnum(dst) < 16:
                w(f"    const/4 {dst}, 0x0")
            else:
                w(f"    const/16 {dst}, 0x0")
            return
        if 0x02 <= op <= 0x08:
            dst = self.S(sp)
            if self._regnum(dst) < 16:
                w(f"    const/4 {dst}, {op - 0x03}")
            else:
                w(f"    const/16 {dst}, {op - 0x03}")
            return
        if op in (0x09, 0x0A):
            w(f"    const-wide {self.S(sp)}, {op - 0x09}"); return
        if 0x0B <= op <= 0x0D:
            w(f"    const {self.S(sp)}, 0x{(op - 0x0B) * 0x3f800000:08x}"); return
        if op in (0x0E, 0x0F):
            hi = 0x3FF00000 if op == 0x0F else 0
            w(f"    const-wide {self.S(sp)}, 0x{hi:08x}00000000"); return
        if op == 0x10:
            w(f"    const/16 {self.S(sp)}, {struct.unpack('b', cb[i+1:i+2])[0]}"); return
        if op == 0x11:
            w(f"    const/16 {self.S(sp)}, {struct.unpack_from('>h', cb, i+1)[0]}"); return
        if op in (0x12, 0x13):
            idx = cb[i + 1] if op == 0x12 else struct.unpack_from(">H", cb, i + 1)[0]
            self.emit_ldc(idx, self.S(sp), False); return
        if op == 0x14:
            self.emit_ldc(struct.unpack_from(">H", cb, i + 1)[0], self.S(sp), True); return

        # locals
        if op in (0x15, 0x16, 0x17, 0x18, 0x19):
            t = {0x15: T_INT, 0x16: T_WIDE, 0x17: T_FLOAT, 0x18: T_WIDE, 0x19: T_REF}[op]
            self._mov(w, t, self.S(sp), self.L(cb[i+1])); return
        if 0x1A <= op <= 0x2D:
            slot = (op - 0x1A) % 4
            t = (T_INT, T_WIDE, T_FLOAT, T_WIDE, T_REF)[(op - 0x1A) // 4]
            self._mov(w, t, self.S(sp), self.L(slot)); return
        if op in (0x36, 0x37, 0x38, 0x39, 0x3A):
            t = {0x36: T_INT, 0x37: T_WIDE, 0x38: T_FLOAT, 0x39: T_WIDE, 0x3A: T_REF}[op]
            depth = sp0 - (2 if t == T_WIDE else 1)
            self._mov(w, t, self.L(cb[i+1]), self.S(depth)); return
        if 0x3B <= op <= 0x4E:
            slot = (op - 0x3B) % 4
            t = (T_INT, T_WIDE, T_FLOAT, T_WIDE, T_REF)[(op - 0x3B) // 4]
            depth = sp0 - (2 if t == T_WIDE else 1)
            self._mov(w, t, self.L(slot), self.S(depth)); return
        if op == 0x84:
            w(f"    add-int/lit8 {self.L(cb[i+1])}, {self.L(cb[i+1])}, {struct.unpack('b', cb[i+2:i+3])[0]}"); return

        # arrays
        if op in ARRAY_LOAD:
            kind, rt = ARRAY_LOAD[op]
            w(f"    {kind} {self.S(sp0-2)}, {self.S(sp0-2)}, {self.S(sp0-1)}"); return
        if op in ARRAY_STORE:
            kind = ARRAY_STORE[op]
            w(f"    {kind} {self.S(sp0-3)}, {self.S(sp0-2)}, {self.S(sp0-1)}"); return
        if op == 0xBE:
            reg = self.S(sp0-1)
            if self._regnum(reg) < 16:
                w(f"    array-length {reg}, {reg}")
            else:
                self._mov(w, T_REF, "v0", reg)
                w(f"    array-length v0, v0")
                self._mov(w, T_INT, reg, "v0")
            return
        if op == 0xBC:
            dst = self.S(sp0-1)
            if self._regnum(dst) < 16:
                w(f"    new-array {dst}, {dst}, {ATYPE[cb[i+1]]}")
            else:
                self._mov(w, T_INT, "v0", dst)
                w(f"    new-array v0, v0, {ATYPE[cb[i+1]]}")
                self._mov(w, T_REF, dst, "v0")
            return
        if op == 0xBD:
            cn = cp.class_name(struct.unpack_from(">H", cb, i + 1)[0])
            dst = self.S(sp0-1)
            typ = self.lref(cn)
            if typ[0] == "[":
                arr = typ
            else:
                arr = "[" + typ
            if self._regnum(dst) < 16:
                w(f"    new-array {dst}, {dst}, {arr}")
            else:
                self._mov(w, T_INT, "v0", dst)
                w(f"    new-array v0, v0, {arr}")
                self._mov(w, T_REF, dst, "v0")
            return

        # dup/permutacje — ogólne przez scratch
        if op in (0x57, 0x58):
            return  # pop: nic
        if op == 0x59:  # dup
            t = tS(sp0 - 1)
            self._mov(w, t, self.S(sp0), self.S(sp0-1)); return
        if op == 0x5A:  # dup_x1  [a b] -> [b a b]
            ta, tb = tS(sp0 - 2), tS(sp0 - 1)
            w(f"    {mv(tb)} {self.S(sp0)}, {self.S(sp0-1)}")
            w(f"    {mv(ta)} {self.S(sp0-1)}, {self.S(sp0-2)}")
            w(f"    {mv(tb)} {self.S(sp0-2)}, {self.S(sp0)}"); return
        if op == 0x5B:  # dup_x2
            if tS(sp0 - 3) != T_WIDE:
                # forma 1 (3x cat1) [a b c] -> [c a b c]
                ta, tb, tc = tS(sp0 - 3), tS(sp0 - 2), tS(sp0 - 1)
                w(f"    {mv(tc)} {self.S(sp0)}, {self.S(sp0-1)}")
                w(f"    {mv(tb)} {self.S(sp0-1)}, {self.S(sp0-2)}")
                w(f"    {mv(ta)} {self.S(sp0-2)}, {self.S(sp0-3)}")
                w(f"    {mv(tc)} {self.S(sp0-3)}, {self.S(sp0)}"); return
            # forma 2: [W c] -> [c W c]
            tc = tS(sp0 - 1)
            w(f"    {mv(tc)} {self.SCR(0)}, {self.S(sp0-1)}")      # SCR0 = c
            w(f"    move-wide {self.SCR(2)}, {self.S(sp0-3)}")     # SCR2 = W
            w(f"    move-wide {self.S(sp0-2)}, {self.SSCR(2)}")    # W -> srodek
            w(f"    {mv(tc)} {self.S(sp0-3)}, {self.SSCR(0)}")     # c -> dol
            w(f"    {mv(tc)} {self.S(sp0)}, {self.SSCR(0)}"); return
        if op == 0x5C:  # dup2
            if tS(sp0 - 1) == T_WIDE:
                w(f"    move-wide {self.S(sp0)}, {self.S(sp0-2)}")
            else:
                t1, t2 = tS(sp0 - 2), tS(sp0 - 1)
                w(f"    {mv(t1)} {self.S(sp0)}, {self.S(sp0-2)}")
                w(f"    {mv(t2)} {self.S(sp0+1)}, {self.S(sp0-1)}")
            return
        if op == 0x5D:  # dup2_x1
            if tS(sp0 - 1) == T_WIDE:
                # [a W] -> [W a W]
                ta = tS(sp0 - 3)
                w(f"    move-wide {self.SSCR(2)}, {self.S(sp0-2)}")   # SCR2 = W
                w(f"    {mv(ta)} {self.S(sp0-1)}, {self.S(sp0-3)}")   # a -> blizej szczytu
                w(f"    move-wide {self.S(sp0)}, {self.SSCR(2)}")     # W -> szczyt
                w(f"    move-wide {self.S(sp0-3)}, {self.SSCR(2)}")   # W -> dol
                return
            # 3x cat1: [a b c] -> [b c a b c]
            ta, tb, tc = tS(sp0 - 3), tS(sp0 - 2), tS(sp0 - 1)
            w(f"    {mv(tc)} {self.S(sp0)}, {self.S(sp0-1)}")
            w(f"    {mv(tb)} {self.S(sp0+1)}, {self.S(sp0-2)}")
            w(f"    {mv(ta)} {self.S(sp0+2)}, {self.S(sp0-3)}")
            w(f"    {mv(tb)} {self.S(sp0-3)}, {self.S(sp0+1)}")
            w(f"    {mv(tc)} {self.S(sp0-2)}, {self.S(sp0)}")
            w(f"    {mv(ta)} {self.S(sp0-1)}, {self.S(sp0+2)}"); return
        if op == 0x5E:  # dup2_x2 — najczęstsza forma: [a b W] -> [W a b W] / wide-top forms
            if tS(sp0 - 1) == T_WIDE and tS(sp0 - 3) != T_WIDE:
                # [a b W] -> [W a b W]  (forma: value2 cat1 x2, value1 cat2)
                ta, tb = tS(sp0 - 3), tS(sp0 - 2)
                w(f"    move-wide {self.SCR(0)}, {self.S(sp0-2)}")
                w(f"    move-wide {self.S(sp0)}, {self.SCR(0)}")
                w(f"    {mv(tb)} {self.S(sp0-1)}, {self.S(sp0-3)}")
                w(f"    {mv(ta)} {self.S(sp0-2)}, {self.S(sp0-4)}")
                w(f"    move-wide {self.S(sp0-4)}, {self.SCR(0)}"); return
            raise ClassFormatError(f"dup2_x2 form not supported @ {i}")
        if op == 0x5F:  # swap
            ta, tb = tS(sp0 - 2), tS(sp0 - 1)
            if ta == T_WIDE or tb == T_WIDE:
                raise ClassFormatError("swap of wide")
            w(f"    {mv(ta)} {self.SCR(0)}, {self.S(sp0-2)}")
            w(f"    {mv(tb)} {self.S(sp0-2)}, {self.S(sp0-1)}")
            w(f"    {mv(ta)} {self.S(sp0-1)}, {self.SCR(0)}"); return

        # arytmetyka
        if op in INT_OPS:
            w(f"    {INT_OPS[op]} {self.S(sp0-2)}, {self.S(sp0-2)}, {self.S(sp0-1)}"); return
        if op in FLOAT_OPS:
            w(f"    {FLOAT_OPS[op]} {self.S(sp0-2)}, {self.S(sp0-2)}, {self.S(sp0-1)}"); return
        if op in LONG_OPS:
            w(f"    {LONG_OPS[op]} {self.S(sp0-4)}, {self.S(sp0-4)}, {self.S(sp0-2)}"); return
        if op in DOUBLE_OPS:
            w(f"    {DOUBLE_OPS[op]} {self.S(sp0-4)}, {self.S(sp0-4)}, {self.S(sp0-2)}"); return
        if op in INT_SHIFTS:
            w(f"    {INT_SHIFTS[op]} {self.S(sp0-2)}, {self.S(sp0-2)}, {self.S(sp0-1)}"); return
        if op in LONG_SHIFTS:
            w(f"    {LONG_SHIFTS[op]} {self.S(sp0-3)}, {self.S(sp0-3)}, {self.S(sp0-1)}"); return
        if op in (0x74, 0x75, 0x76, 0x77):
            name = {0x74: "neg-int", 0x75: "neg-long", 0x76: "neg-float",
                    0x77: "neg-double"}[op]
            t = T_WIDE if op in (0x75, 0x77) else (T_FLOAT if op == 0x76 else T_INT)
            reg = self.S(sp0-(2 if op in (0x75,0x77) else 1))
            if self._regnum(reg) < 16:
                w(f"    {name} {reg}, {reg}")
            else:
                self._mov(w, t, "v0", reg)
                w(f"    {name} v0, v0")
                self._mov(w, t, reg, "v0")
            return
        if 0x85 <= op <= 0x93:
            name = {0x85: "int-to-long", 0x86: "int-to-float", 0x87: "int-to-double",
                    0x88: "long-to-int", 0x89: "long-to-float", 0x8A: "long-to-double",
                    0x8B: "float-to-int", 0x8C: "float-to-long", 0x8D: "float-to-double",
                    0x8E: "double-to-int", 0x8F: "double-to-long", 0x90: "double-to-float",
                    0x91: "int-to-byte", 0x92: "int-to-char", 0x93: "int-to-short"}[op]
            src = sp0 - (2 if op in (0x88, 0x89, 0x8A, 0x8E, 0x8F, 0x90) else 1)
            w(f"    {name} {self.S(src)}, {self.S(src)}"); return
        if op == 0x94:
            w(f"    cmp-long {self.S(sp0-4)}, {self.S(sp0-4)}, {self.S(sp0-2)}"); return
        if op in (0x95, 0x96):
            w(f"    {'cmpl-float' if op == 0x95 else 'cmpg-float'} {self.S(sp0-2)}, {self.S(sp0-2)}, {self.S(sp0-1)}"); return
        if op in (0x97, 0x98):
            w(f"    {'cmpl-double' if op == 0x97 else 'cmpg-double'} {self.S(sp0-4)}, {self.S(sp0-4)}, {self.S(sp0-2)}"); return

        # skoki
        if op in Z_BRANCH:
            tgt = self.label(i + struct.unpack_from('>h', cb, i+1)[0])
            reg = self.S(sp0-1)
            if self._regnum(reg) < 16:
                w(f"    {Z_BRANCH[op]} {reg}, {tgt}")
            else:
                tt = tS(sp0-1)
                self._mov(w, T_REF if tt == T_REF else T_INT, "v0", reg)
                w(f"    {Z_BRANCH[op]} v0, {tgt}")
            return
        if op in (0xC6, 0xC7):
            name = "if-eqz" if op == 0xC6 else "if-nez"
            tgt = self.label(i + struct.unpack_from('>h', cb, i+1)[0])
            reg = self.S(sp0-1)
            if self._regnum(reg) < 16:
                w(f"    {name} {reg}, {tgt}")
            else:
                self._mov(w, T_REF, "v0", reg)
                w(f"    {name} v0, {tgt}")
            return
        if op in CMP_BRANCH or op in (0xA5, 0xA6):
            if op in (0xA5, 0xA6):
                name = "if-eq" if op == 0xA5 else "if-ne"
            else:
                name = CMP_BRANCH[op]
            tgt = self.label(i + struct.unpack_from('>h', cb, i+1)[0])
            ra, rb = self.S(sp0-2), self.S(sp0-1)
            if self._regnum(ra) < 16 and self._regnum(rb) < 16:
                w(f"    {name} {ra}, {rb}, {tgt}")
            else:
                # typy z analizy przepływu — acmp porównuje REFERENCJE (move-object!)
                if op in (0xA5, 0xA6):
                    ta, tb = T_REF, T_REF
                else:
                    ta, tb = T_INT, T_INT
                self._mov(w, ta, "v0", ra)
                self._mov(w, tb, "v1", rb)
                w(f"    {name} v0, v1, {tgt}")
            return
        if op == 0xA7:
            w(f"    goto {self.label(i + struct.unpack_from('>h', cb, i+1)[0])}"); return
        if op in (0xC8, 0xC9):
            w(f"    goto {self.label(i + struct.unpack_from('>i', cb, i+1)[0])}"); return
        if op == 0xAA:
            w(f"    packed-switch {self.S(sp0-1)}, :pswitch_{i}"); return
        if op == 0xAB:
            w(f"    sparse-switch {self.S(sp0-1)}, :sswitch_{i}"); return

        # powroty
        if op in (0xAC, 0xAE):
            w(f"    return {self.S(sp0-1)}"); return
        if op in (0xAD, 0xAF):
            w(f"    return-wide {self.S(sp0-2)}"); return
        if op == 0xB0:
            w(f"    return-object {self.S(sp0-1)}"); return
        if op == 0xB1:
            w("    return-void"); return

        # pola
        if op == 0xB2:
            idx = struct.unpack_from(">H", cb, i + 1)[0]
            _, cls, nm, d = cp.ref(idx)
            self._sget(w, type_of_desc(d), self.S(sp0), f"{self.lref(cls)}->{nm}:{d}"); return
        if op == 0xB3:
            idx = struct.unpack_from(">H", cb, i + 1)[0]
            _, cls, nm, d = cp.ref(idx)
            t = type_of_desc(d)
            self._sput(w, t, self.S(sp0 - (2 if t == T_WIDE else 1)), f"{self.lref(cls)}->{nm}:{d}"); return
        if op == 0xB4:
            idx = struct.unpack_from(">H", cb, i + 1)[0]
            _, cls, nm, d = cp.ref(idx)
            self._iget(w, type_of_desc(d), self.S(sp0-1), self.S(sp0-1), f"{self.lref(cls)}->{nm}:{d}"); return
        if op == 0xB5:
            idx = struct.unpack_from(">H", cb, i + 1)[0]
            _, cls, nm, d = cp.ref(idx)
            t = type_of_desc(d)
            self._iput(w, t, self.S(sp0-1), self.S(sp0-2), f"{self.lref(cls)}->{nm}:{d}"); return

        # wywołania
        if op in (0xB6, 0xB7, 0xB8, 0xB9):
            idx = struct.unpack_from(">H", cb, i + 1)[0]
            _, cls, nm, d = cp.ref(idx)
            kind = {0xB6: "invoke-virtual", 0xB7: "invoke-direct",
                    0xB8: "invoke-static", 0xB9: "invoke-interface"}[op]
            args, ret = split_params(d)
            arg_slots = sum(slot_count(a) for a in args) + (0 if op == 0xB8 else 1)
            base = sp0 - arg_slots
            if arg_slots == 0:
                w(f"    {kind} {{}}, {self.lref(cls)}->{nm}{d}")
            else:
                w(f"    {kind}/range {{{self.S(base)} .. {self.S(base+arg_slots-1)}}}, {self.lref(cls)}->{nm}{d}")
            if ret != "V":
                t = type_of_desc(ret)
                w(f"    {'move-result-wide' if t == T_WIDE else ('move-result-object' if t == T_REF else 'move-result')} {self.S(base)}")
            return

        # obiekty
        if op == 0xBB:
            cn = cp.class_name(struct.unpack_from(">H", cb, i + 1)[0])
            dst = self.S(sp0)
            if self._regnum(dst) < 16:
                w(f"    new-instance {dst}, {self.lref(cn)}")
            else:
                w(f"    new-instance v0, {self.lref(cn)}")
                self._mov(w, T_REF, dst, "v0")
            return
        if op == 0xBF:
            w(f"    throw {self.S(sp0-1)}"); return
        if op == 0xC0:
            cn = cp.class_name(struct.unpack_from(">H", cb, i + 1)[0])
            dst = self.S(sp0-1)
            if self._regnum(dst) < 16:
                w(f"    check-cast {dst}, {self.lref(cn)}")
            else:
                self._mov(w, T_REF, "v0", dst)
                w(f"    check-cast v0, {self.lref(cn)}")
                self._mov(w, T_REF, dst, "v0")
            return
        if op == 0xC1:
            cn = cp.class_name(struct.unpack_from(">H", cb, i + 1)[0])
            src2, dst = self.S(sp0-1), self.S(sp0-1)
            if self._regnum(src2) < 16 and self._regnum(dst) < 16:
                w(f"    instance-of {dst}, {src2}, {self.lref(cn)}")
            else:
                self._mov(w, T_REF, "v1", src2)
                w(f"    instance-of v0, v1, {self.lref(cn)}")
                self._mov(w, T_INT, dst, "v0")
            return
        if op == 0xC2:
            w(f"    monitor-enter {self.S(sp0-1)}"); return
        if op == 0xC3:
            w(f"    monitor-exit {self.S(sp0-1)}"); return
        raise ClassFormatError(f"emit: opcode {op:#x} @ {i}")

    def emit_ldc(self, idx, dst, wide):
        tag, v = self.cp.const(idx)
        if wide:
            if tag == TAG_LONG:
                self.lines.append(f"    const-wide {dst}, {v}")
            elif tag == TAG_DOUBLE:
                bits = struct.unpack(">Q", struct.pack(">d", v))[0]
                self.lines.append(f"    const-wide {dst}, 0x{bits:016x}")
            else:
                raise ClassFormatError("ldc2_w non-wide")
        else:
            if tag == TAG_INT:
                self.lines.append(f"    const {dst}, {v}")
            elif tag == TAG_FLOAT:
                bits = struct.unpack(">I", struct.pack(">f", v))[0]
                self.lines.append(f"    const {dst}, 0x{bits:08x}")
            elif tag == TAG_STRING:
                self.lines.append(f"    const-string {dst}, {escape_str(self.cp.string(idx))}")
            elif tag == TAG_CLASS:
                self.lines.append(f"    const-class {dst}, {self.lref(self.cp.class_name(idx))}")
            else:
                raise ClassFormatError(f"ldc tag {tag}")


def translate(cf) -> str:
    out = [".class " + " ".join(flags(cf["access"], ACC_CLASS)) + f" L{cf['name']};"]
    if cf["super"]:
        out.append(f".super L{cf['super']};")
    for i in cf["interfaces"]:
        out.append(f".implements L{i};")
    out.append("")
    for acc, nm, ds, cv in cf["fields"]:
        out.append(f".field {' '.join(flags(acc, ACC_FIELD))} {nm}:{ds}")
        if cv is not None:
            out.append(f"    # ConstantValue: {cv}")
        out.append("")
    for acc, nm, ds, code in cf["methods"]:
        mt = MethodTranslator(cf, acc, nm, ds, code if code else (0, 0, b"", []))
        if code is None:
            extra = "constructor " if nm in ("<init>", "<clinit>") and not (acc & 0x10000) else ""
            out.append(f".method {' '.join(flags(acc, ACC_METHOD))} {extra}{nm}{ds}")
            out.append(".end method")
            out.append("")
            continue
        mt.compute_flows()
        mt.emit_all()
        out.extend(mt.lines)
    return "\n".join(out) + "\n"


def main(argv):
    for path in argv[1:]:
        cf = parse_class(open(path, "rb").read())
        out = path.rsplit(".", 1)[0] + ".smali"
        open(out, "w", encoding="utf-8").write(translate(cf))
        print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
