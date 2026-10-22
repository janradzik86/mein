#!/usr/bin/env python3
"""Testy regresyjne mapowania JVM bytecode -> smali w j2smali.

Warstwa A: syntetyczne klasy .class (budowane bajt po bajcie) -> j2smali ->
           dokladne oczekiwane smali + kontrole przeplywu typow po permutacjach.
Warstwa B: realne wzorce Java -> ECJ -> j2smali -> smali.jar -> dex ->
           dexcheck + dexverify (koncowe potwierdzenie typow ART).

Uruchomienie:  ~/.cache/wojan-venv/bin/python tools/test_j2smali_map.py
Pokrywa bledy translatora znalezione 2026-10-22 (regresje):
  1. fconst_2 zle bity IEEE-754 (0x7f000000 zamiast 0x40000000)
  2. przeplyw stosu dla konwersji 0x85-0x93 (zrodla szerokie vs wyniki szerokie)
  3. przeplyw stosu dla lshl/lshr/lushr (bilans -1, nie -3)
  4. putfield wartosci szerokiej (zle rejestry value/obj)
  5. przeplyw typow po dup_x1/dup_x2/dup2_x1/dup2_x2/swap (stare typy w slotach)
  6. P (sloty parametrow) liczone jako len(params) zamiast slot_count
  7. (regresja v1.0.4) brak ACC_CONSTRUCTOR na <init>
  8. (regresja v1.0.5) zla kolejnosc operandow aput (value,array,index)
"""
import os
import struct
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "j2smali"))
import j2smali as J  # noqa: E402

TOOLCACHE = os.path.join(os.path.expanduser("~"), ".cache", "wojan-tools")
VENVPY = os.path.join(os.path.expanduser("~"), ".cache", "wojan-venv", "bin", "python")
JAVA = os.path.join(os.path.expanduser("~"), ".cache", "wojan-venv",
                    "lib", "python3.11", "site-packages", "jdk4py",
                    "java-runtime", "bin", "java")

FAILED = []
PASSED = 0


def ok(name, cond, detail=""):
    global PASSED
    if cond:
        PASSED += 1
        print(f"  PASS  {name}")
    else:
        FAILED.append(name)
        print(f"  FAIL  {name}  {detail}")


def has(text, *needles):
    return all(n in text for n in needles)


# ---------------------------------------------------------------------------
# Warstwa A — syntetyczne klasy
# ---------------------------------------------------------------------------

def u2(x):
    return struct.pack(">H", x)


def u4(x):
    return struct.pack(">I", x)


class CP:
    def __init__(self):
        self.e = [None]

    def utf8(self, s):
        self.e.append(("u", s))
        return len(self.e) - 1

    def cls(self, name):
        return self._add(("c", self.utf8(name)))

    def nat(self, n, d):
        return self._add(("n", self.utf8(n), self.utf8(d)))

    def fref(self, cls_i, nat_i):
        return self._add(("f", cls_i, nat_i))

    def _add(self, ent):
        self.e.append(ent)
        return len(self.e) - 1

    def blob(self):
        out = b""
        for e in self.e[1:]:
            if e[0] == "u":
                b = e[1].encode("utf-8")
                out += bytes([1]) + u2(len(b)) + b
            elif e[0] == "c":
                out += bytes([7]) + u2(e[1])
            elif e[0] == "n":
                out += bytes([12]) + u2(e[1]) + u2(e[2])
            elif e[0] == "f":
                out += bytes([9]) + u2(e[1]) + u2(e[2])
            else:
                raise AssertionError(e)
        return u2(len(self.e)) + out


def make_class(code, max_stack, max_locals, desc="()V", name="T",
               method_name="m", method_acc=0x9, fields=(), code_is_raw=False):
    """Minimalny poprawny plik .class (major 52) z jedna metoda 'm'."""
    cp = CP()
    code_utf = cp.utf8("Code")
    this_i = cp.cls(name)
    super_i = cp.cls("java/lang/Object")
    m_utf = cp.utf8(method_name)
    d_utf = cp.utf8(desc)

    field_blob = b""
    fnat = {}
    for fname, fdesc in fields:
        fnat[(fname, fdesc)] = cp.nat(fname, fdesc)
    # deklaracje pol: atrybutow brak
    for fname, fdesc in fields:
        field_blob += u2(0x1) + u2(cp.utf8(fname)) + u2(cp.utf8(fdesc)) + u2(0)

    frefs = {}
    for fname, fdesc in fields:
        frefs[(fname, fdesc)] = cp.fref(this_i, fnat[(fname, fdesc)])

    body = u2(max_stack) + u2(max_locals) + u4(len(code)) + code + u2(0) + u2(0)
    code_attr = u2(code_utf) + u4(len(body)) + body
    method = u2(method_acc) + u2(m_utf) + u2(d_utf) + u2(1) + code_attr

    out = b"\xca\xfe\xba\xbe" + u2(0) + u2(52)
    out += cp.blob()
    out += u2(0x21) + u2(this_i) + u2(super_i)
    out += u2(0)  # interfaces
    out += u2(len(fields)) + field_blob
    out += u2(1) + method
    out += u2(0)  # class attrs
    return J.parse_class(out), frefs


def translate_synthetic(*a, **kw):
    cf, frefs = make_class(*a, **kw)
    return J.translate(cf), frefs, cf


def flow_types(cf, pc, slots, base=6):
    """Typy na slotach stosu (S(0)=base) w danym pc, po compute_flows."""
    mt = J.MethodTranslator(cf, cf["methods"][0][0], cf["methods"][0][1],
                            cf["methods"][0][2], cf["methods"][0][3])
    mt.compute_flows()
    d, types = mt.flows[pc]
    return [types[base + s] for s in slots]


def layer_a():
    print("== Warstwa A: syntetyczne klasy (dokladne smali) ==")

    # 1) fconst bits
    for op, bits in ((0x0B, "0x00000000"), (0x0C, "0x3f800000"), (0x0D, "0x40000000")):
        txt, _, _ = translate_synthetic(bytes([op, 0xAE]), 1, 0, "()F")
        ok(f"fconst op={op:#x} -> const v6, {bits}", f"const v6, {bits}" in txt, txt)

    # 2) i2l + arytmetyka po konwersji (po i2l sp=2 -> kolejna stala w S(2)=v8)
    txt, _, _ = translate_synthetic(
        bytes([0x1A, 0x85, 0x0A, 0x61, 0xAD]), 4, 1, "(I)J")
    ok("i2l: int-to-long v6, v6", "int-to-long v6, v6" in txt, txt)
    ok("i2l: add-long v6, v6, v8 po konwersji",
       "add-long v6, v6, v8" in txt, txt)
    ok("i2l: return-wide v6", "return-wide v6" in txt, txt)

    # 3) lshl + arytmetyka po przesunieciu
    txt, _, _ = translate_synthetic(
        bytes([0x16, 0x00, 0x1C, 0x79, 0x0A, 0x61, 0xAD]), 4, 3, "(JI)J")
    ok("lshl: shl-long v6, v6, v8", "shl-long v6, v6, v8" in txt, txt)
    ok("lshl: add-long v6, v6, v8 po przesunieciu",
       "add-long v6, v6, v8" in txt, txt)
    ok("lshl: const-wide v8, 1 (nie przesuniete)", "const-wide v8, 1" in txt, txt)

    # 4) l2i + d2f
    txt, _, _ = translate_synthetic(
        bytes([0x16, 0x00, 0x88, 0x04, 0x60, 0xAC]), 2, 2, "(J)I")
    ok("l2i: long-to-int v6, v6", "long-to-int v6, v6" in txt, txt)
    ok("l2i: add-int v6, v6, v7", "add-int v6, v6, v7" in txt, txt)

    txt, _, _ = translate_synthetic(
        bytes([0x18, 0x00, 0x90, 0x0D, 0x62, 0xAE]), 2, 2, "(D)F")
    ok("d2f: double-to-float v6, v6", "double-to-float v6, v6" in txt, txt)
    ok("d2f: const v7, 0x40000000 + add-float v6, v6, v7",
       has(txt, "const v7, 0x40000000", "add-float v6, v6, v7"), txt)

    # 5) putfield szerokiej (instancja!): value S(sp0-2), obj S(sp0-3)
    idx_holder, frefs = make_class(bytes([0x2A, 0x16, 0x01, 0xB5, 0x00, 0x00, 0xB1]),
                                   3, 3, "(J)V", method_acc=0x1,
                                   fields=(("f", "J"),))
    idx = frefs[("f", "J")]
    code = bytes([0x2A, 0x16, 0x01]) + bytes([0xB5]) + struct.pack(">H", idx) + bytes([0xB1])
    cf, frefs = make_class(code, 3, 3, "(J)V", method_acc=0x1, fields=(("f", "J"),))
    txt = J.translate(cf)
    ok("putfield-wide: iput-wide v7, v6, LT;->f:J",
       "iput-wide v7, v6, LT;->f:J" in txt, txt)
    ok("putfield-wide: move-wide v7, v10 (para wartosci)",
       "move-wide v7, v10" in txt, txt)

    # 6) dup_x1 + swap: rodzaje ruchow (REF/INT/REF) po permutacji
    code = bytes([0x19, 0x00, 0x03, 0x19, 0x00, 0x04, 0x32,
                  0x5A, 0x57, 0x5F, 0x53, 0xB1])
    txt, _, cf = translate_synthetic(code, 4, 1, "([Ljava/lang/Object;)V")
    ok("dup_x1: permutacja [.. b c]->[.. c b c] (3 ruchy)",
       has(txt,
           "move-object/from16 v9, v8",
           "move/from16 v8, v7",
           "move-object/from16 v7, v9"), txt)
    ok("swap po dup_x1: move/from16 v7, v8 (INT, nie REF)",
       "move/from16 v7, v8" in txt and "move-object/from16 v8, v0" in txt, txt)
    ok("aastore: aput-object v8, v6, v7 (value,array,index)",
       "aput-object v8, v6, v7" in txt, txt)
    t = flow_types(cf, 8, [0, 1, 2, 3])  # pc=8: zaraz za dup_x1
    ok("dup_x1: przeplyw typow [A A I A] po permutacji",
       t == ["A", "A", "I", "A"], f"types={t}")

    # 7) dup2_x1 forma szeroka [a W]->[W a W]
    code = bytes([0x1A, 0x16, 0x01, 0x5D, 0x58, 0x57, 0x58, 0xB1])
    txt, _, cf = translate_synthetic(code, 5, 3, "(IJ)V")
    ok("dup2_x1 wide: sekwencja ruchow",
       has(txt,
           "move-wide v2, v7",
           "move/from16 v8, v6",
           "move-wide v9, v2",
           "move-wide v6, v2"), txt)
    t = flow_types(cf, 4, [0, 1, 2, 3, 4])  # pc=4: zaraz za dup2_x1
    ok("dup2_x1 wide: przeplyw typow [W W I W W]",
       t == ["W", "W", "I", "W", "W"], f"types={t}")

    # 8) ACC_CONSTRUCTOR na <init> (regresja v1.0.4)
    txt, _, _ = translate_synthetic(bytes([0xB1]), 1, 1, "()V",
                                    method_name="<init>", method_acc=0x1)
    ok("<init> dostaje flage constructor",
       ".method public constructor <init>()V" in txt, txt)

    # 9) sloty parametrow P dla szerokich (regresja bug6)
    #    static (JI)I: J w slotach 0-1, I w slocie 2 -> L(0)=v8, L(2)=v10
    txt, _, _ = translate_synthetic(
        bytes([0x1C, 0x1A, 0x60, 0xAC]), 2, 3, "(JI)I")
    ok("P-sloty: iload_2 (param I po szerokim J) -> move v6, v10",
       "move v6, v10" in txt and "move v7, v8" in txt, txt)


# ---------------------------------------------------------------------------
# Warstwa B — ECJ end-to-end
# ---------------------------------------------------------------------------

FIXTURES = {
    "E1.java": """\
public class E1 {
    public static void fill(int[] a, String[] s, byte[] b) {
        a[0] = 1;
        s[0] = "x";
        b[0] = 2;
        a[1] = a[0];
    }
}
""",
    "E2.java": """\
public class E2 {
    long f;
    double d;
    public void set(long v, double x) { this.f = v; this.d = x; }
    public long get() { return f; }
    public double getd() { return d; }
}
""",
    "E3.java": """\
public class E3 {
    public static long scale(int x) { return (long) x * 3L + 1L; }
    public static float twice(float f) { return 2.0f * f; }
}
""",
}


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        raise RuntimeError(f"cmd failed: {cmd}\n{r.stdout}\n{r.stderr}")
    return r


def layer_b():
    print("== Warstwa B: ECJ -> j2smali -> smali.jar -> dexcheck/dexverify ==")
    for tool, path in (("java", JAVA), ("ecj", os.path.join(TOOLCACHE, "ecj.jar")),
                       ("smali", os.path.join(TOOLCACHE, "smali.jar"))):
        if not os.path.exists(path):
            print(f"  BRAK {tool}: {path} — uruchom scripts/fetch-toolchain.sh")
            sys.exit(2)

    with tempfile.TemporaryDirectory(prefix="j2smali-map-") as td:
        src = os.path.join(td, "src")
        classes = os.path.join(td, "classes")
        smali_d = os.path.join(td, "smali")
        os.makedirs(src)
        os.makedirs(classes)
        os.makedirs(smali_d)
        for fn, body in FIXTURES.items():
            with open(os.path.join(src, fn), "w") as f:
                f.write(body)

        run([JAVA, "-jar", os.path.join(TOOLCACHE, "ecj.jar"),
             "-source", "1.8", "-target", "1.8", "-nowarn", "-d", classes]
            + [os.path.join(src, fn) for fn in FIXTURES])

        texts = {}
        for fn in sorted(FIXTURES):
            cls = fn[:-5]
            cf = J.parse_class(open(os.path.join(classes, cls + ".class"), "rb").read())
            txt = J.translate(cf)
            texts[cls] = txt
            outp = os.path.join(smali_d, cls + ".smali")
            with open(outp, "w") as f:
                f.write(txt)

        t1 = texts["E1"]
        ok("E1: iastore -> aput v8, v6, v7 (value,array,index)",
           "aput v8, v6, v7" in t1, t1)
        ok("E1: aastore -> aput-object v8, v6, v7",
           "aput-object v8, v6, v7" in t1, t1)
        ok("E1: bastore -> aput-byte v8, v6, v7",
           "aput-byte v8, v6, v7" in t1, t1)
        ok("E1: aaload -> aget v8, v8, v9", "aget v8, v8, v9" in t1, t1)

        t2 = texts["E2"]
        ok("E2: iput-wide wartosc,obiekt",
           "iput-wide v" in t2 and "LE2;->f:J" in t2
           and "LE2;->d:D" in t2, t2)
        ok("E2: iget-wide", "iget-wide v" in t2, t2)
        ok("E2: brak zlamanych iput-wide vHI, vLO",
           "iput-wide v8, v7" not in t2, t2)

        t3 = texts["E3"]
        ok("E3: int-to-long v6, v6", "int-to-long v6, v6" in t3, t3)
        ok("E3: mul-long/add-long po konwersji",
           has(t3, "mul-long v6, v6, v8", "add-long v6, v6, v8"), t3)
        ok("E3: fconst_2 bity 0x40000000", "0x40000000" in t3
           and "0x7f000000" not in t3, t3)

        # asemblacja + weryfikatory na malym dexie z fixture'ami
        dex = os.path.join(td, "fixtures.dex")
        run([JAVA, "-jar", os.path.join(TOOLCACHE, "smali.jar"),
             smali_d, "-o", dex])
        ok("smali.jar: asemblacja fixture'ow", os.path.exists(dex))

        r = subprocess.run([VENVPY, os.path.join(HERE, "dexcheck.py"), dex],
                           capture_output=True, text=True)
        ok("dexcheck: fixture E1-E3", r.returncode == 0,
           (r.stdout + r.stderr)[-400:])
        r = subprocess.run([VENVPY, os.path.join(HERE, "dexverify.py"), dex],
                           capture_output=True, text=True)
        ok("dexverify: fixture E1-E3", r.returncode == 0,
           (r.stdout + r.stderr)[-400:])


def main():
    layer_a()
    layer_b()
    print(f"\n== WYNIK: {PASSED} PASS, {len(FAILED)} FAIL ==")
    if FAILED:
        for f in FAILED:
            print(f"  ! {f}")
        sys.exit(1)


if __name__ == "__main__":
    main()
