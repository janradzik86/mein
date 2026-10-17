#!/usr/bin/env python3
"""build_apk.py — pełny potok budowania APK Release WOJAN AI FACTORY.

Potok (wszystkie kroki realne, bez skrótów):
  ECJ (javac-like) -> j2smali (JVM bytecode -> smali) -> smali.jar (dex 035)
  -> pyaxml (binarny AndroidManifest.xml) -> zip 4B-aligned
  -> oficjalny apksigner.jar v1+v2+v3 (fallback: tools/apksig v2)
  -> ApkSignerTool verify (MUSI przejść, inaczej błąd)

Użycie:
  WOJAN_KEYSTORE_PASS=... python3 build_apk.py --dev --out dist/WOJAN-AI-FACTORY-Release.apk

  --dev  : podpisz kluczem dev keystore/wojan-dev.p12 (domyślnie: wojan-release.p12)
Hasło klucza: z WOJAN_KEYSTORE_PASS lub keystore/keystore.env — NIGDY z kodu/repo.
"""
from __future__ import annotations

import argparse
import glob
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOOLCACHE = os.environ.get("WOJAN_TOOLCACHE", os.path.expanduser("~/.cache/wojan-tools"))
BUILD = os.path.join(ROOT, "android", "build", "build")
SRC = os.path.join(ROOT, "android", "app", "src")
J2SMALI = os.path.join(ROOT, "tools", "j2smali", "j2smali.py")
AXMLBUILD = os.path.join(ROOT, "tools", "axmlbuild.py")
MAKE_APK = os.path.join(ROOT, "tools", "apksig", "make_apk.py")
PACKAGE = "pl.wojan.factory"
ACTIVITIES = [PACKAGE + ".MainActivity", PACKAGE + ".ProjectsActivity", PACKAGE + ".SettingsActivity"]


def find_java() -> str:
    jh = os.environ.get("JAVA_HOME")
    if jh and os.path.exists(os.path.join(jh, "bin", "java")):
        return os.path.join(jh, "bin", "java")
    cand = os.path.join(os.path.dirname(sys.executable), "..", "lib")
    for p in glob.glob(os.path.join(cand, "python3.*", "site-packages", "jdk4py", "java-runtime", "bin", "java")):
        return os.path.abspath(p)
    for p in glob.glob(os.path.expanduser("~/.cache/wojan-venv/lib/python3.*/site-packages/jdk4py/java-runtime/bin/java")):
        return p
    raise SystemExit("brak java (JRE) — ustaw JAVA_HOME albo zainstaluj jdk4py w venv")


def run(cmd, **kw):
    print("+", " ".join(cmd)[:220])
    r = subprocess.run(cmd, **kw)
    if r.returncode != 0:
        raise SystemExit(f"NIEUDANE: {' '.join(cmd)[:220]}")
    return r


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--dev", action="store_true", help="podpisz kluczem dev")
    args = ap.parse_args()

    java = find_java()
    ecj = os.path.join(TOOLCACHE, "ecj.jar")
    android_jar = os.path.join(TOOLCACHE, "android34.jar")
    smali = os.path.join(TOOLCACHE, "smali.jar")
    apksigner = os.path.join(TOOLCACHE, "apksigner.jar")
    for f in (ecj, android_jar, smali):
        if not os.path.exists(f):
            raise SystemExit(f"brak narzędzia {f} — uruchom scripts/fetch-toolchain.sh")

    classes = os.path.join(BUILD, "classes")
    smali_dir = os.path.join(BUILD, "smali")
    dex_dir = os.path.join(BUILD, "dex")
    for d in (BUILD, classes, smali_dir, dex_dir):
        os.makedirs(d, exist_ok=True)
    shutil.rmtree(classes, ignore_errors=True)
    shutil.rmtree(smali_dir, ignore_errors=True)
    os.makedirs(classes)
    os.makedirs(smali_dir)

    # 1) ECJ — kompilacja Javy (api 24-34 z android34.jar)
    java_files = sorted(glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True))
    if not java_files:
        raise SystemExit("brak źródeł Java w android/app/src")
    run([java, "-jar", ecj, "-source", "1.8", "-target", "1.8",
         "-bootclasspath", android_jar, "-d", classes] + java_files)

    # 2) j2smali — JVM bytecode -> smali (własny tłumacz)
    class_files = sorted(glob.glob(os.path.join(classes, "**", "*.class"), recursive=True))
    run([sys.executable, J2SMALI] + class_files)
    # przenieś .smali do build/smali ze strukturą pakietów
    for sf in glob.glob(os.path.join(classes, "**", "*.smali"), recursive=True):
        rel = os.path.relpath(sf, classes)
        dst = os.path.join(smali_dir, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.move(sf, dst)

    # 3) smali.jar — asemblacja dex 035
    dex = os.path.join(dex_dir, "classes.dex")
    run([java, "-jar", smali, smali_dir, "-o", dex])

    # 4) binarny manifest AXML (pyaxml)
    manifest = os.path.join(BUILD, "AndroidManifest.xml")
    run([sys.executable, AXMLBUILD, manifest, PACKAGE] + ACTIVITIES)

    # 5+6) spakowanie + podpis + weryfikacja
    sys.path.insert(0, os.path.join(ROOT, "tools", "apksig"))
    from make_apk import AlignedZip, load_identity  # noqa: E402

    unsigned = os.path.join(BUILD, "unsigned.apk")
    z = AlignedZip(unsigned)
    z.add("AndroidManifest.xml", open(manifest, "rb").read(), stored=True)
    z.add("classes.dex", open(dex, "rb").read(), stored=True)
    raw = z.build()
    with open(unsigned, "wb") as fh:
        fh.write(raw)

    keystore = os.path.join(ROOT, "keystore", "wojan-dev.p12" if args.dev else "wojan-release.p12")
    if not os.path.exists(keystore):
        raise SystemExit(f"brak keystore {keystore} — tools/apksig/make_keystore.py (hasło w env)")
    out = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)

    if os.path.exists(apksigner):
        # oficjalny apksigner 33.0.2 — v1+v2+v3
        pw = os.environ.get("WOJAN_KEYSTORE_PASS")
        if not pw:
            env_file = os.path.join(os.path.dirname(keystore), "keystore.env")
            if os.path.exists(env_file):
                for line in open(env_file):
                    if line.strip().startswith("WOJAN_KEYSTORE_PASS="):
                        pw = line.split("=", 1)[1].strip().strip('"').strip("'")
        if not pw:
            raise SystemExit("brak WOJAN_KEYSTORE_PASS (env lub keystore/keystore.env)")
        run([java, "--enable-native-access=ALL-UNNAMED", "-cp", apksigner,
             "com.android.apksigner.ApkSignerTool", "sign",
             "--ks", keystore, "--ks-pass", f"pass:{pw}", "--key-pass", f"pass:{pw}",
             "--out", out, unsigned])
        run([java, "--enable-native-access=ALL-UNNAMED", "-cp", apksigner,
             "com.android.apksigner.ApkSignerTool", "verify", "--print-certs", out])
    else:
        # fallback: własny podpis v2 (tools/apksig) — też weryfikowany
        run([sys.executable, MAKE_APK, "--manifest", manifest, "--dex", dex,
             "--out", out, "--keystore", keystore])

    size = os.path.getsize(out)
    print(f"GOTOWE: {out} ({size} B)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
