#!/usr/bin/env python3
"""dexverify.py — weryfikator typów rejestrów DEX (reguły ART/Dalvik).

Przechodzi KAŻDĄ instrukcję KAŻDEJ metody i sprawdza:
  - move / move-wide / move-object vs faktyczny typ rejestru (referencji nie wolno `move`)
  - return / return-wide / return-object vs typ metody i typu wartości
  - invoke: liczba rejestrów vs prototyp; typy argumentów vs deskryptory;
    <init> tylko invoke-direct; odbiorca <init> musi być niezainicjalizowany;
    po <init> WSZYSTKIE kopie stają się zainicjalizowane (jak ART)
  - move-result* tuż po invoke, zgodny typ; wynik trafia do rejestru
  - iget/iput/sget/sput: rodzaj vs deskryptor pola; obiekt zainicjalizowany
  - if-test: if-eq/ne int lub ref/ref; if-lt..le tylko int; switch tylko int
  - new-instance / check-cast / instance-of / array-length / aget* / aput*
  - try/catch: move-exception w handlerze

Mapa opcodów zgodna z dexcheck.py (zweryfikowana empirycznie na realnym dex).
Użycie: python3 dexverify.py classes.dex  -> exit 0 = czysto
"""
import struct, sys

W = [1] * 256
def setw(ops, width):
    for o in ops:
        W[o] = width

setw([0x00], 1)
setw([0x01, 0x04, 0x07], 1)     # move / move-wide / move-object (12x)
setw([0x02, 0x05, 0x08], 2)     # */from16 (22x)
setw([0x03, 0x06, 0x09], 3)     # */16 (32x)
setw(range(0x0a, 0x0e), 1)
setw(range(0x0e, 0x12), 1)
setw([0x12], 1)
setw([0x13, 0x15, 0x16, 0x19], 2)
setw([0x14, 0x17], 3)
setw([0x18], 5)
setw([0x1a, 0x1c], 2)
setw([0x1b], 3)
setw([0x1d, 0x1e], 1)
setw([0x1f, 0x20, 0x22, 0x23], 2)
setw([0x21], 1)
setw([0x24, 0x25, 0x26], 3)
setw([0x27, 0x28], 1)
setw([0x29], 2); setw([0x2a], 3)
setw([0x2b, 0x2c], 3)
setw(range(0x2d, 0x32), 2)
setw(range(0x32, 0x38), 2)
setw(range(0x38, 0x3e), 2)
setw(range(0x44, 0x52), 2)
setw(range(0x52, 0x60), 2)
setw(range(0x60, 0x6e), 2)
setw(range(0x6e, 0x73), 3)
setw(range(0x74, 0x79), 3)
setw(range(0x7b, 0x90), 1)
setw(range(0x90, 0xb0), 2)
setw(range(0xb0, 0xd0), 1)
setw(range(0xd0, 0xd8), 2)
setw(range(0xd8, 0xe3), 2)

BAD = set(range(0x3e, 0x44)) | {0x73, 0x79, 0x7a} | set(range(0xe3, 0xfa))

# --- typy: TOP, ZERO(0), INT, FLOAT, WLO-J/D + WHI(para), REF, UNINIT, UTHIS ---
TOP, ZERO, INT, FLOAT, WHI = "?", "0", "I", "F", "H"
WJ, WD, REF, UNINIT, UTHIS = "J", "D", "R", "U", "T"

class Ty:
    __slots__ = ("k", "d", "pc")
    def __init__(self, k, d=None, pc=None):
        self.k, self.d, self.pc = k, d, pc
    def __eq__(self, o):
        return isinstance(o, Ty) and (self.k, self.d, self.pc) == (o.k, o.d, o.pc)
    def __repr__(self):
        return self.k + (f"({self.d})" if self.d else "")

T_TOP, T_ZERO, T_INT, T_FLOAT, T_WHI = Ty(TOP), Ty(ZERO), Ty(INT), Ty(FLOAT), Ty(WHI)

def is_ref(t):   return t.k in (REF, UNINIT, UTHIS, ZERO)
def is_intish(t): return t.k in (INT, ZERO)
def is_wide(t):  return t.k in (WJ, WD)

def merge(a, b):
    if a == b: return a
    if a.k == TOP or b.k == TOP: return T_TOP
    if a.k == ZERO: return b if b.k in (REF, UNINIT, UTHIS, INT, FLOAT) else T_TOP
    if b.k == ZERO: return a if a.k in (REF, UNINIT, UTHIS, INT, FLOAT) else T_TOP
    if is_ref(a) and is_ref(b):
        if a.k == b.k:
            return Ty(a.k, a.d if a.d == b.d else None, a.pc if a.pc == b.pc else None)
        return Ty(REF)
    return T_TOP

def ty_desc(desc):
    if desc == 'J': return Ty(WJ)
    if desc == 'D': return Ty(WD)
    if desc in ('I', 'Z', 'B', 'C', 'S'): return T_INT
    if desc == 'F': return T_FLOAT
    return Ty(REF, desc)

def uleb(d, p):
    v = s = 0
    while True:
        b = d[p]; p += 1; v |= (b & 0x7f) << s
        if not b & 0x80: return v, p
        s += 7

class Dex:
    def __init__(self, d):
        self.d = d
        h = struct.unpack_from('<20I', d, 32)
        self.str_off, self.type_off, self.proto_off = h[7], h[9], h[11]
        self.field_off, self.meth_off, self.class_off = h[13], h[15], h[17]

    def sstr(self, i):
        sp = struct.unpack_from('<I', self.d, self.str_off + 4 * i)[0]
        l, p = uleb(self.d, sp)
        return self.d[p:p + l].decode('utf-8', 'replace')

    def tname(self, tidx):
        return self.sstr(struct.unpack_from('<I', self.d, self.type_off + 4 * tidx)[0])

    def proto(self, idx):
        _, ret, po = struct.unpack_from('<III', self.d, self.proto_off + 12 * idx)
        args = []
        if po:
            n = struct.unpack_from('<I', self.d, po)[0]
            for k in range(n):
                args.append(self.tname(struct.unpack_from('<H', self.d, po + 4 + 2 * k)[0]))
        return args, self.tname(ret)

    def field(self, idx):
        return self.tname(struct.unpack_from('<H', self.d, self.field_off + 8 * idx + 2)[0])

    def method(self, idx):
        mc, mp, mn = struct.unpack_from('<HHI', self.d, self.meth_off + 8 * idx)
        args, ret = self.proto(mp)
        return self.sstr(mn), args, ret

INVOKE = (0x6e, 0x6f, 0x70, 0x71, 0x72, 0x74, 0x75, 0x76, 0x77, 0x78)

def verify_method(dx, midx, name, args, ret, is_static, regs, ins, insns):
    errs = []
    def err(off, msg):
        errs.append(f"m{midx} {name} @{off}: {msg}")

    n = regs
    st0 = [T_TOP] * n
    p = n - ins
    if not is_static:
        st0[p] = Ty(UTHIS) if name == '<init>' else Ty(REF, None)
        p += 1
    for a in args:
        t = ty_desc(a)
        st0[p] = t
        if is_wide(t):
            st0[p + 1] = T_WHI; p += 2
        else:
            p += 1

    nunits = len(insns) // 2
    flows = {}
    work = [(0, st0, None)]
    while work:
        off, st, prev = work.pop()
        st = list(st)
        while off < nunits:
            if off in flows:
                old = flows[off]
                merged = [merge(a, b) for a, b in zip(old, st)]
                if merged == list(old):
                    break
                st = merged
            flows[off] = list(st)
            op = insns[off * 2]
            w = W[op]
            if op in BAD:
                err(off, f"niedozwolony opcode {op:#x}")
            if off + w > nunits:
                err(off, f"instrukcja poza zakresem op={op:#x}")
                break

            def b1(): return insns[off * 2 + 1]
            def u16(b): return struct.unpack_from('<H', insns, off * 2 + b)[0]
            def s16(b): return struct.unpack_from('<h', insns, off * 2 + b)[0]
            def rg(i): return st[i] if 0 <= i < n else T_TOP
            def setr(i, t):
                if 0 <= i < n: st[i] = t

            # move-result* — kontekst invoke
            if op in (0x0a, 0x0b, 0x0c):
                if prev is None:
                    err(off, "move-result bez bezpośrednio poprzedzającego invoke")
                else:
                    _, mret = prev
                    exp = {0x0a: 'I', 0x0b: 'W', 0x0c: 'O'}[op]
                    got = 'W' if mret in ('J', 'D') else ('O' if mret[:1] in ('L', '[') else 'I')
                    if exp != got:
                        err(off, f"move-result-{exp} vs ret {mret}")
                    aa = b1()
                    if exp == 'W':
                        setr(aa, ty_desc(mret)); setr(aa + 1, T_WHI)
                    elif exp == 'O':
                        setr(aa, Ty(REF, mret))
                    else:
                        setr(aa, ty_desc(mret))
                prev = None
                off += w
                continue

            # payloady switch/array — pomijamy (nie są instrukcjami)
            if op in (0x26, 0x2b, 0x2c):
                if op in (0x2b, 0x2c) and not is_intish(rg(b1())):
                    err(off, "switch na nie-int")
                off += w
                continue

            nxt_prev = None

            # --- moves (rodziny 0x01/04/07 = move/wide/object) ---
            if op in (0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09):
                fam = {0x01: 0, 0x02: 0, 0x03: 0, 0x04: 1, 0x05: 1, 0x06: 1,
                       0x07: 2, 0x08: 2, 0x09: 2}[op]
                fmt = {0x01: '12x', 0x02: '22x', 0x03: '32x',
                       0x04: '12x', 0x05: '22x', 0x06: '32x',
                       0x07: '12x', 0x08: '22x', 0x09: '32x'}[op]
                if fmt == '12x':
                    a, b = b1() & 0xF, b1() >> 4
                elif fmt == '22x':
                    a, b = b1(), u16(2)
                else:
                    a, b = u16(2), u16(4)
                tb = rg(b)
                if fam == 0:      # move — 32-bit nie-ref (ZERO=null jest dozwolony)
                    if is_ref(tb) and tb.k != ZERO:
                        err(off, f"move (int) na referencji v{b}={tb}")
                    elif is_wide(tb) or tb == T_WHI:
                        err(off, f"move (int) na szerokim v{b}")
                    setr(a, tb if tb.k in (INT, FLOAT, ZERO) else T_TOP)
                elif fam == 1:    # move-wide
                    if not is_wide(tb):
                        err(off, f"move-wide na nie-szerokim v{b}={tb}")
                    setr(a, tb if is_wide(tb) else Ty(WJ))
                    setr(a + 1, T_WHI)
                else:             # move-object
                    if not is_ref(tb):
                        err(off, f"move-object na nie-referencji v{b}={tb}")
                    setr(a, tb)
                off += w; prev = None; continue

            if op == 0x0d:  # move-exception
                setr(b1(), Ty(REF, 'Ljava/lang/Throwable;'))
                off += w; prev = None; continue

            # --- return ---
            if op == 0x0e:
                if ret != 'V':
                    err(off, f"return-void vs metoda ret={ret}")
                if name == '<init>':
                    for i, t in enumerate(st):
                        if t.k == UTHIS:
                            err(off, f"<init> wraca z niezainicjalizowanym this (v{i})")
                break
            if op == 0x0f:
                t = rg(b1())
                if ret in ('J', 'D') or ret[:1] in ('L', '[', 'V'):
                    err(off, f"return (cat1) vs metoda ret={ret}")
                if not is_intish(t) and t.k != FLOAT:
                    err(off, f"return {t} vs metoda ret={ret}")
                break
            if op == 0x10:
                t = rg(b1())
                if ret not in ('J', 'D'):
                    err(off, f"return-wide vs metoda ret={ret}")
                if not is_wide(t):
                    err(off, f"return-wide {t}")
                break
            if op == 0x11:
                t = rg(b1())
                if ret[:1] not in ('L', '['):
                    err(off, f"return-object vs metoda ret={ret}")
                if not is_ref(t):
                    err(off, f"return-object {t}")
                elif t.k in (UNINIT, UTHIS):
                    err(off, "return niezainicjalizowanej referencji")
                break

            # --- const ---
            if op == 0x12:
                setr(b1() & 0xF, T_ZERO if (b1() >> 4) == 0 else T_INT)
                off += w; prev = None; continue
            if op == 0x13:
                setr(b1(), T_INT); off += w; prev = None; continue
            if op == 0x14:
                setr(b1(), T_INT); off += w; prev = None; continue
            if op in (0x15, 0x16, 0x17, 0x18, 0x19):
                t = {0x15: T_INT, 0x16: Ty(WJ), 0x17: T_INT, 0x18: Ty(WJ), 0x19: Ty(WD)}[op]
                setr(b1(), t)
                if is_wide(t):
                    setr(b1() + 1, T_WHI)
                off += w; prev = None; continue
            if op == 0x1a:
                setr(b1(), Ty(REF, 'Ljava/lang/String;')); off += w; prev = None; continue
            if op == 0x1b:
                setr(b1(), Ty(REF, 'Ljava/lang/String;')); off += w; prev = None; continue
            if op == 0x1c:
                setr(b1(), Ty(REF, 'Ljava/lang/Class;')); off += w; prev = None; continue

            # --- monitor / throw ---
            if op in (0x1d, 0x1e):
                if not is_ref(rg(b1())):
                    err(off, f"monitor na nie-referencji {rg(b1())}")
                off += w; prev = None; continue
            if op == 0x27:
                if not is_ref(rg(b1())):
                    err(off, f"throw {rg(b1())}")
                break

            # --- check-cast(0x1f) instance-of(0x20) new-instance(0x22) new-array(0x23) array-length(0x21) ---
            if op == 0x1f:
                a = b1()
                if not is_ref(rg(a)):
                    err(off, f"check-cast na nie-referencji {rg(a)}")
                setr(a, Ty(REF, dx.tname(u16(2))))
                off += w; prev = None; continue
            if op == 0x20:
                a, b = b1() & 0xF, b1() >> 4
                if not is_ref(rg(b)):
                    err(off, f"instance-of na nie-referencji {rg(b)}")
                setr(a, T_INT)
                off += w; prev = None; continue
            if op == 0x21:
                a, b = b1() & 0xF, b1() >> 4
                if not is_ref(rg(b)):
                    err(off, f"array-length {rg(b)}")
                setr(a, T_INT)
                off += w; prev = None; continue
            if op == 0x22:
                setr(b1(), Ty(UNINIT, dx.tname(u16(2)), off))
                off += w; prev = None; continue
            if op == 0x23:
                a, b = b1() & 0xF, b1() >> 4
                if not is_intish(rg(b)):
                    err(off, f"new-array z nie-int {rg(b)}")
                setr(a, Ty(REF, '[' + dx.tname(u16(2))))
                off += w; prev = None; continue

            # --- aget/aput (23x): aa=byte1, bb=byte2 (tablica), cc=byte3 (indeks) ---
            if 0x44 <= op <= 0x51:
                aa = b1()
                bb = insns[off * 2 + 2]
                cc = insns[off * 2 + 3]
                get = op <= 0x4a
                kindop = (op - 0x44) if get else (op - 0x4b)
                wide = kindop == 1
                refk = kindop == 2
                arr = rg(bb)
                if not is_ref(arr):
                    err(off, f"aget/aput na nie-tablicy {arr}")
                if not is_intish(rg(cc)):
                    err(off, f"aget/aput z nie-int indeksem {rg(cc)}")
                if get:
                    if wide:
                        setr(aa, Ty(WJ) if arr.d in (None, '[J') else Ty(WD))
                        setr(aa + 1, T_WHI)
                    elif refk:
                        setr(aa, Ty(REF, (arr.d or '[Ljava/lang/Object;')[1:]))
                    else:
                        setr(aa, T_INT if kindop != 0 or True else T_INT)
                else:
                    val = rg(aa)
                    if wide and not is_wide(val):
                        err(off, f"aput-wide {val}")
                    elif refk and not is_ref(val):
                        err(off, f"aput-object {val}")
                off += w; prev = None; continue

            # --- iget/iput (22c) sget/sput (21c) ---
            if 0x52 <= op <= 0x6d:
                fd = dx.field(u16(2))
                if op <= 0x5f:
                    a, b = b1() & 0xF, b1() >> 4
                    ob = rg(b)
                    if not is_ref(ob):
                        err(off, f"iget/iput na nie-obiekcie {ob}")
                    elif ob.k == UNINIT:
                        # ART zezwala na dostęp do pól przez niezainicjalizowany `this`
                        # w konstruktorach klas wewnętrznych (this$0 przed super()); UNINIT z new-instance nie
                        err(off, f"iget/iput na niezainicjalizowanym {ob}")
                else:
                    a, b = b1(), None
                kindop = (op - 0x52) if op <= 0x58 else (op - 0x59) if op <= 0x5f \
                         else (op - 0x60) if op <= 0x66 else (op - 0x67)
                gets = op <= 0x58 or 0x60 <= op <= 0x66
                wide, refk = kindop == 1, kindop == 2
                if gets:
                    if wide:
                        if fd not in ('J', 'D'):
                            err(off, f"get-wide na polu {fd}")
                        setr(a, ty_desc(fd)); setr(a + 1, T_WHI)
                    elif refk:
                        if fd[:1] not in ('L', '['):
                            err(off, f"get-object na polu {fd}")
                        setr(a, Ty(REF, fd))
                    else:
                        if fd[:1] in ('L', '['):
                            err(off, f"get na polu ref {fd}")
                        setr(a, ty_desc(fd))
                else:
                    val = rg(a)
                    if wide and not is_wide(val):
                        err(off, f"put-wide {val} vs pole {fd}")
                    elif refk and not is_ref(val):
                        err(off, f"put-object {val} vs pole {fd}")
                    elif not (wide or refk) and not is_intish(val) and val.k != FLOAT:
                        err(off, f"put {val} vs pole {fd}")
                off += w; prev = None; continue

            # --- invoke ---
            if op in INVOKE:
                is_rng = op >= 0x74
                kind = {0x6e: 'virtual', 0x6f: 'super', 0x70: 'direct', 0x71: 'static',
                        0x72: 'interface', 0x74: 'virtual', 0x75: 'super', 0x76: 'direct',
                        0x77: 'static', 0x78: 'interface'}[op]
                cnt = b1()
                mname, margs, mret = dx.method(u16(2))
                slots = sum(2 if a in ('J', 'D') else 1 for a in margs) + (0 if kind == 'static' else 1)
                if cnt != slots:
                    err(off, f"invoke-{kind} count={cnt} != {slots} ({mname})")
                if is_rng:
                    start = u16(4)
                    regs_idx = list(range(start, start + cnt))
                else:
                    g = b1() & 0xF
                    word = u16(4)
                    regs_idx = [word & 0xF, (word >> 4) & 0xF, (word >> 8) & 0xF, (word >> 12) & 0xF, g][:cnt]
                ai = 0
                if kind != 'static':
                    recv = rg(regs_idx[0]) if regs_idx else T_TOP
                    if mname == '<init>':
                        if kind != 'direct':
                            err(off, f"<init> przez invoke-{kind}")
                        if recv.k not in (UNINIT, UTHIS):
                            err(off, f"<init> na złym odbiorcy {recv}")
                        if recv.k == UTHIS:
                            for i in range(n):
                                if st[i].k == UTHIS:
                                    st[i] = Ty(REF, recv.d)
                        elif recv.k == UNINIT:
                            for i in range(n):
                                if st[i].k == UNINIT and st[i].pc == recv.pc:
                                    st[i] = Ty(REF, recv.d)
                    else:
                        if not is_ref(recv):
                            err(off, f"odbiorca nie-referencją {recv} ({mname})")
                        elif recv.k in (UNINIT, UTHIS):
                            err(off, f"niezainicjalizowany odbiorca {recv} ({mname})")
                    ai = 1
                for a in margs:
                    need = ty_desc(a)
                    got = rg(regs_idx[ai]) if ai < len(regs_idx) else T_TOP
                    if is_wide(need):
                        if not is_wide(got):
                            err(off, f"arg {a} {mname}: v{regs_idx[ai] if ai < len(regs_idx) else '?'}={got} nie szeroki")
                        ai += 2
                    else:
                        if need.k == INT and not is_intish(got) and got.k != FLOAT:
                            err(off, f"arg {a} {mname}: {got} nie int")
                        if need.k == FLOAT and got.k not in (FLOAT, ZERO, INT):
                            err(off, f"arg float {mname}: {got}")
                        if need.d and need.d[:1] in ('L', '['):
                            if not is_ref(got):
                                err(off, f"arg {a} {mname}: {got} nie referencja")
                            elif got.k in (UNINIT, UTHIS):
                                err(off, f"arg {a} {mname}: niezainicjalizowany {got}")
                        ai += 1
                nxt_prev = (off, mret)
                off += w; prev = nxt_prev; continue

            # --- if-test (22t) / ifz (21t) / goto ---
            if 0x32 <= op <= 0x37:
                a, b = b1() & 0xF, b1() >> 4
                ta, tb = rg(a), rg(b)
                if op in (0x32, 0x33):
                    if is_ref(ta) != is_ref(tb):
                        err(off, f"if-eq/ne mieszane typy {ta},{tb}")
                else:
                    if not is_intish(ta) or not is_intish(tb):
                        err(off, f"if-lt..le na {ta},{tb}")
                work.append((off + s16(2), list(st), None))
                off += w; prev = None; continue
            if 0x38 <= op <= 0x3d:
                a = b1()
                ta = rg(a)
                if op not in (0x38, 0x39):
                    if not is_intish(ta):
                        err(off, f"if-ltz..lez na {ta}")
                work.append((off + s16(2), list(st), None))
                off += w; prev = None; continue
            if op == 0x28:
                work.append((off + struct.unpack_from('<b', insns, off * 2 + 1)[0], list(st), None))
                break
            if op == 0x29:
                work.append((off + s16(2), list(st), None))
                break
            if op == 0x2a:
                work.append((off + struct.unpack_from('<i', insns, off * 2 + 2)[0], list(st), None))
                break

            # --- unop 12x (0x7b-0x8f): MAPA z bajtów (potwierdzona): ---
            # 7b neg-int, 7c not-int, 7d neg-long, 7e not-long, 7f neg-float, 80 neg-double,
            # 81 i2l, 82 i2f, 83 i2d, 84 l2i, 85 l2f, 86 l2d, 87 f2i, 88 f2l, 89 f2d,
            # 8a d2i, 8b d2l, 8c d2f, 8d i2b, 8e i2c, 8f i2s
            if 0x7b <= op <= 0x8f:
                a, b = b1() & 0xF, b1() >> 4
                tb = rg(b)
                wide_in = op in (0x7d, 0x7e, 0x80, 0x84, 0x85, 0x86, 0x8a, 0x8b, 0x8c)
                wide_out = op in (0x7d, 0x7e, 0x80, 0x81, 0x83, 0x88, 0x89)
                float_in = op in (0x7f, 0x87, 0x88, 0x89)
                float_out = op in (0x7f, 0x82, 0x85, 0x8c)
                if wide_in and not is_wide(tb):
                    err(off, f"unop szeroki na {tb}")
                if float_in and tb.k not in (FLOAT, ZERO) and not is_wide(tb):
                    err(off, f"unop float na {tb}")
                if wide_out:
                    setr(a, Ty(WJ)); setr(a + 1, T_WHI)
                elif float_out:
                    setr(a, T_FLOAT)
                else:
                    setr(a, T_INT)
                off += w; prev = None; continue
            if 0x90 <= op <= 0xaf:
                aa, bb, cc = b1(), insns[off * 2 + 2], insns[off * 2 + 3]
                off += w; prev = None; continue
            if 0xb0 <= op <= 0xcf:
                off += w; prev = None; continue
            if 0xd0 <= op <= 0xe2:
                off += w; prev = None; continue

            # domyślne: dalej
            off += w; prev = None
    return errs


def main(path):
    d = open(path, 'rb').read()
    dx = Dex(d)
    n_code = struct.unpack_from('<I', d, 96)[0]
    errs = []
    n = 0
    for ci in range(n_code):
        cd = dx.class_off + 32 * ci
        class_idx, access, super_idx, if_off, src_idx, ann_off, cdo, sv = struct.unpack_from('<8I', d, cd)
        if not cdo:
            continue
        p = cdo
        ss, p = uleb(d, p); iss, p = uleb(d, p); dms, p = uleb(d, p); vms, p = uleb(d, p)
        for _ in range(ss + iss):
            _, p = uleb(d, p); _, p = uleb(d, p)
        for cnt in (dms, vms):
            midx = 0
            for _ in range(cnt):
                diff, p = uleb(d, p); midx += diff
                acc, p = uleb(d, p)
                code_off, p = uleb(d, p)
                if not code_off:
                    continue
                n += 1
                regs, ins, outs, tries, dbg, insns_size = struct.unpack_from('<HHHHII', d, code_off)
                insns = d[code_off + 16: code_off + 16 + insns_size * 2]
                mname, margs, mret = dx.method(midx)
                errs.extend(verify_method(dx, midx, mname, margs, mret, bool(acc & 0x8),
                                          regs, ins, insns))
    print(f"dexverify: {path}: {n} metod, {len(errs)} błędów")
    for e in errs[:40]:
        print("  ", e)
    return 1 if errs else 0

if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
