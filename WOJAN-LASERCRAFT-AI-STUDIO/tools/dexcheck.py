#!/usr/bin/env python3
"""dexcheck.py — strukturalna walidacja classes.dex (format, code_item, szerokości instrukcji).

Tabela szerokości z oficjalnej specyfikacji Dalvik bytecode (formaty 10x/11x/12x/21x/22x/23x/31x/35x/3rc/51l).
Użycie: python3 dexcheck.py classes.dex  -> exit 0 = OK
"""
from __future__ import annotations
import struct, sys, zlib, hashlib

W = [1] * 256
def setw(ops, width):
    for o in ops:
        W[o] = width

setw([0x00], 1)                 # nop (10x)
setw([0x01, 0x04, 0x07], 1)     # move / move-wide / move-object (12x)
setw([0x02, 0x05, 0x08], 2)     # */from16 (22x)
setw([0x03, 0x06, 0x09], 3)     # */16 (32x)
setw(range(0x0a, 0x0e), 1)      # move-result*, move-exception (11x)
setw(range(0x0e, 0x12), 1)      # return* (11x)
setw([0x12], 1)                 # const/4 (11n)
setw([0x13, 0x15, 0x16, 0x19], 2)  # const/16, const/high16, const-wide/16, const-wide/high16 (21*)
setw([0x14, 0x17], 3)           # const, const-wide/32 (31i)
setw([0x18], 5)                 # const-wide (51l)
setw([0x1a, 0x1c], 2)           # const-string, const-class (21c)
setw([0x1b], 3)                 # const-string/jumbo (31c)
setw([0x1d, 0x1e], 1)           # monitor-enter/exit (11x)
setw([0x1f, 0x20, 0x22, 0x23], 2)  # check-cast, instance-of, new-instance, new-array (22c)
setw([0x21], 1)                 # array-length (12x)
setw([0x24], 3)                 # filled-new-array (35c)
setw([0x25], 3)                 # filled-new-array/range (3rc)
setw([0x26], 3)                 # fill-array-data (31t)
setw([0x27, 0x28], 1)           # throw, goto
setw([0x29], 2)                 # goto/16 (20t)
setw([0x2a], 3)                 # goto/32 (30t)
setw([0x2b, 0x2c], 3)           # packed/sparse-switch (31t)
setw(range(0x2d, 0x32), 2)      # cmp* (23x)
setw(range(0x32, 0x38), 2)      # if-eq..if-le (22t)
setw(range(0x38, 0x3e), 2)      # if-eqz..if-lez (21t)
setw(range(0x3e, 0x44), 1)      # nieużywane (zakazane — walidator wykryje)
setw(range(0x44, 0x52), 2)      # aget*/aput* (23x)
setw(range(0x52, 0x60), 2)      # iget*/iput* (22c)
setw(range(0x60, 0x6e), 2)      # sget*/sput* (21c)
setw(range(0x6e, 0x73), 3)      # invoke-* (35c)
setw([0x73], 1)                 # nieużywane
setw(range(0x74, 0x79), 3)      # invoke-*/range (3rc)
setw([0x79, 0x7a], 1)           # nieużywane
setw(range(0x7b, 0x90), 1)      # unop (12x)
setw(range(0x90, 0xb0), 2)      # binop (23x)
setw(range(0xb0, 0xd0), 1)      # binop/2addr (12x)
setw(range(0xd0, 0xd8), 2)      # binop/lit16 (22s)
setw(range(0xd8, 0xe3), 2)      # binop/lit8 (22b)
setw(range(0xe3, 0xfa), 1)      # zarezerwowane
setw([0xfa, 0xfb, 0xfc, 0xfd], 3)  # invoke-polymorphic/custom (35c/3rc)
setw([0xfe, 0xff], 2)           # const-method-handle/type (21c)

BAD_OPS = set(range(0x3e, 0x44)) | {0x73, 0x79, 0x7a} | set(range(0xe3, 0xfa))

def uleb(data, p):
    if p >= len(data):
        raise IndexError("uleb poza plikiem")
    v = 0; s = 0
    while True:
        b = data[p]; p += 1
        v |= (b & 0x7f) << s
        if not (b & 0x80):
            return v, p
        s += 7

def sleb(data, p):
    v = 0; s = 0
    while True:
        b = data[p]; p += 1
        v |= (b & 0x7f) << s
        s += 7
        if not (b & 0x80):
            if b & 0x40:
                v |= - (1 << s)
            return v, p

def main(path):
    d = open(path, "rb").read()
    errs = []
    # Android akceptuje wersje 035–039; smali z -a 24 emituje 036
    if not (d[:4] == b"dex\n" and d[4:7] in (b"035", b"036", b"037", b"038", b"039")
            and d[7] == 0):
        errs.append(f"zły magic: {d[:8]!r}")
    if struct.unpack_from("<I", d, 40)[0] != 0x12345678:
        errs.append("zła endian tag")
    if hashlib.sha1(d[32:]).digest() != d[12:32]:
        errs.append("zły SHA-1 w nagłówku")
    if (zlib.adler32(d[12:]) & 0xFFFFFFFF) != struct.unpack_from("<I", d, 8)[0]:
        errs.append("zły adler32 w nagłówku")
    (_, _, _, _, _, map_off, str_ids, str_off, type_ids, type_off,
     proto_ids, proto_off, field_ids, field_off, meth_ids, meth_off,
     class_defs, class_off, data_size, data_off) = struct.unpack_from("<20I", d, 32)

    n_code = 0
    for ci in range(class_defs):
        cd = class_off + 32 * ci
        class_idx, access, super_idx, interfaces_off, source_idx, annotations_off, class_data_off, static_values_off = struct.unpack_from("<8I", d, cd)
        if class_data_off == 0:
            continue
        p = class_data_off
        ss, p = uleb(d, p); iss, p = uleb(d, p); dms, p = uleb(d, p); vms, p = uleb(d, p)
        for _ in range(ss + iss):
            _, p = uleb(d, p); _, p = uleb(d, p)
        for lst, cnt in (("direct", dms), ("virtual", vms)):
            midx = 0   # idx_diff liczony od 0 w KAŻDEJ liście osobno
            for _ in range(cnt):
                diff, p = uleb(d, p)
                midx += diff
                acc, p = uleb(d, p)
                code_off, p = uleb(d, p)
                if code_off == 0:
                    continue
                n_code += 1
                registers_size, ins_size, outs_size, tries_size, debug_off, insns_size = struct.unpack_from("<HHHHII", d, code_off)
                # ins_size zgodne z method_id (proto + static)?
                moff = meth_off + 8 * midx
                _mclass, proto_idx = struct.unpack_from("<HH", d, moff)
                proto_p = proto_off + 12 * proto_idx
                _, _, params_off = struct.unpack_from("<III", d, proto_p)
                n_params = 0
                if params_off:
                    cnt2 = struct.unpack_from("<I", d, params_off)[0]
                    n_params = cnt2  # każdy typ argumentu; szerokie zajmują 2 rejestry
                    for k in range(cnt2):
                        tidx = struct.unpack_from("<H", d, params_off + 4 + 2 * k)[0]
                        type_str_off = struct.unpack_from("<I", d, type_off + 4 * tidx)[0]
                        sp = struct.unpack_from("<I", d, str_off + 4 * type_str_off)[0]
                        slen, sp2 = uleb(d, sp)
                        if d[sp2:sp2+1] in (b"J", b"D"):
                            n_params += 1
                is_static = (acc & 0x8) != 0
                expected_ins = n_params + (0 if is_static else 1)
                if ins_size != expected_ins:
                    errs.append(f"method {midx}: ins_size={ins_size}, oczekiwane {expected_ins} (params={n_params}, acc={acc:#x})")
                if registers_size < ins_size:
                    errs.append(f"method {midx}: registers_size({registers_size}) < ins_size({ins_size})")
                insns = d[code_off + 16: code_off + 16 + insns_size * 2]
                off = 0
                payloads = {}
                while off < insns_size:
                    if off in payloads:
                        off = payloads[off]
                        continue
                    op = insns[off * 2]   # off w jednostkach 16-bitowych → bajty
                    wdt = W[op]
                    if op in BAD_OPS:
                        errs.append(f"method {midx}: niedozwolony opcode {op:#x} @ {off}")
                    if off + wdt > insns_size:
                        errs.append(f"method {midx}: instrukcja poza zakresem @ {off} (op={op:#x}, w={wdt}, size={insns_size})")
                        break
                    if op in (0x26, 0x2b, 0x2c):
                        rel = struct.unpack_from("<h", insns, off * 2 + 2)[0]
                        poff = off + rel
                        try:
                            if op == 0x2b:      # packed-switch
                                ident, n = struct.unpack_from("<HH", insns, poff * 2)
                                if ident != 0x0100: raise ValueError
                                end = poff + 4 + 2 * n
                            elif op == 0x2c:    # sparse-switch
                                ident, n = struct.unpack_from("<HH", insns, poff * 2)
                                if ident != 0x0300: raise ValueError
                                end = poff + 4 + 4 * n
                            else:               # fill-array-data
                                ident, width = struct.unpack_from("<HH", insns, poff * 2)
                                n = struct.unpack_from("<I", insns, poff * 2 + 4)[0]
                                if ident != 0x0200: raise ValueError
                                end = poff + (8 + width * n + 1) // 2
                            if poff < 0 or end > insns_size: raise ValueError
                            payloads[poff] = end
                        except Exception:
                            errs.append(f"method {midx}: zły payload @ {poff} dla op={op:#x}")
                    if 0x74 <= op <= 0x78 or op == 0x25:
                        # 3rc: AA=count, BBBB=method_idx, CCCC=start register
                        count = insns[off * 2 + 1]
                        start = struct.unpack_from("<H", insns, off * 2 + 4)[0]
                        if start + count > registers_size:
                            errs.append(f"method {midx}: invoke/range poza rejestrami @ {off} (start={start}, count={count}, N={registers_size})")
                    if 0x6e <= op <= 0x72 or op == 0x24:
                        count = insns[off * 2 + 1] >> 4
                        if count > 5:
                            errs.append(f"method {midx}: invoke count={count} > 5 @ {off}")
                    off += wdt
                if off != insns_size and not errs:
                    errs.append(f"method {midx}: spacer {off} != insns_size {insns_size}")
                if tries_size:
                    tbase = code_off + 16 + insns_size * 2
                    if insns_size % 2:
                        tbase += 2
                    hlist = tbase + 8 * tries_size
                    for t in range(tries_size):
                        start3, cnt4, hoff = struct.unpack_from("<IHB", d, tbase + 8 * t)
                        if start3 + cnt4 > insns_size:
                            errs.append(f"method {midx}: try poza zakresem")
                        hp = hlist + hoff
                        try:
                            sz, hp = sleb(d, hp)
                            n = abs(sz)
                            for _ in range(n):
                                _, hp = uleb(d, hp)   # type_idx
                                _, hp = uleb(d, hp)   # addr
                            if sz <= 0:
                                _, hp = uleb(d, hp)   # catch-all addr
                            if hp > len(d):
                                raise IndexError
                        except IndexError:
                            errs.append(f"method {midx}: uszkodzony catch handler")
    print(f"dexcheck: {path}: {n_code} code_item, {len(errs)} błędów")
    for e in errs[:25]:
        print("  ", e)
    return 1 if errs else 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
