#!/usr/bin/env python3
"""build_diag.py — buduje WOJAN-DIAG.apk (osobny pakiet pl.wojan.diag).

Ten sam potok co build_apk.py (ECJ -> j2smali -> smali -> AXML -> zip -> apksigner),
ale: klasy głównej apki + DiagActivity, wersja „DIAG-1.0.6", manifest z dwiema
aktywnościami (diag = launcher). Cel: zamienić cichy crash głównej apki na
widoczny komunikat błędu na ekranie telefonu.

Użycie:
  WOJAN_KEYSTORE_PASS=... python3 build_diag.py --keystore keystore/wojan-release.p12 \
      --out dist/WOJAN-DIAG.apk
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
BUILD = os.path.join(ROOT, "android", "build", "build-diag")
APP_SRC = os.path.join(ROOT, "android", "app", "src")
DIAG_SRC = os.path.join(ROOT, "android", "diag", "src")
J2SMALI = os.path.join(ROOT, "tools", "j2smali", "j2smali.py")
PACKAGE = "pl.wojan.diag"
ACTIVITIES = [PACKAGE + ".DiagActivity", "pl.wojan.factory.MainActivity"]


def find_java() -> str:
    for p in glob.glob(os.path.expanduser(
            "~/.cache/wojan-venv/lib/python3.*/site-packages/jdk4py/java-runtime/bin/java")):
        return p
    raise SystemExit("brak java — uruchom scripts/fetch-toolchain.sh")


def run(cmd, **kw):
    print("+", " ".join(cmd)[:200])
    r = subprocess.run(cmd, **kw)
    if r.returncode != 0:
        raise SystemExit(f"NIEUDANE: {' '.join(cmd)[:200]}")
    return r


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--keystore", required=True)
    args = ap.parse_args()

    java = find_java()
    ecj = os.path.join(TOOLCACHE, "ecj.jar")
    android_jar = os.path.join(TOOLCACHE, "android34.jar")
    smali = os.path.join(TOOLCACHE, "smali.jar")
    apksigner = os.path.join(TOOLCACHE, "apksigner.jar")
    py = sys.executable

    classes = os.path.join(BUILD, "classes")
    smali_dir = os.path.join(BUILD, "smali")
    dex_dir = os.path.join(BUILD, "dex")
    for d in (BUILD, classes, smali_dir, dex_dir):
        os.makedirs(d, exist_ok=True)

    srcs = sorted(glob.glob(os.path.join(APP_SRC, "**", "*.java"), recursive=True))
    srcs += sorted(glob.glob(os.path.join(DIAG_SRC, "**", "*.java"), recursive=True))
    print(f"Kompilacja {len(srcs)} plików .java (apka + diag)")
    run([java, "-jar", ecj, "-source", "1.8", "-target", "1.8", "-nowarn",
         "-bootclasspath", android_jar, "-d", classes] + srcs)

    class_files = sorted(glob.glob(os.path.join(classes, "**", "*.class"), recursive=True))
    run([py, J2SMALI] + class_files)

    for sf in glob.glob(os.path.join(classes, "**", "*.smali"), recursive=True):
        rel = os.path.relpath(sf, classes)
        dst = os.path.join(smali_dir, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.move(sf, dst)

    dex = os.path.join(dex_dir, "classes.dex")
    run([java, "-jar", smali, smali_dir, "-o", dex])
    dc = subprocess.run([py, os.path.join(ROOT, "tools", "dexcheck.py"), dex],
                        capture_output=True, text=True)
    sys.stderr.write(dc.stdout + dc.stderr)
    if dc.returncode != 0:
        raise SystemExit("dexcheck: NIE przeszedł — build przerwany")

    sys.path.insert(0, os.path.join(ROOT, "tools"))
    from axmlbuild import build_manifest, xml_to_axml  # noqa: E402
    xml = build_manifest(PACKAGE, [{"name": a} for a in ACTIVITIES],
                         label="WOJAN DIAG", version_code=7, version_name="DIAG-1.0.6")
    manifest = xml_to_axml(xml)
    with open(os.path.join(BUILD, "AndroidManifest.xml"), "wb") as fh:
        fh.write(manifest)

    sys.path.insert(0, os.path.join(ROOT, "tools", "apksig"))
    from make_apk import AlignedZip  # noqa: E402
    unsigned = os.path.join(BUILD, "unsigned.apk")
    z = AlignedZip(unsigned)
    z.add("AndroidManifest.xml", manifest, stored=True)
    z.add("classes.dex", open(dex, "rb").read(), stored=True)
    with open(unsigned, "wb") as fh:
        fh.write(z.build())

    pw = os.environ.get("WOJAN_KEYSTORE_PASS")
    if not pw:
        raise SystemExit("brak WOJAN_KEYSTORE_PASS")
    out = os.path.abspath(args.out)
    run([java, "--enable-native-access=ALL-UNNAMED", "-cp", apksigner,
         "com.android.apksigner.ApkSignerTool", "sign",
         "--ks", args.keystore, "--ks-pass", f"pass:{pw}", "--key-pass", f"pass:{pw}",
         "--out", out, unsigned])
    run([java, "--enable-native-access=ALL-UNNAMED", "-cp", apksigner,
         "com.android.apksigner.ApkSignerTool", "verify", "--print-certs", out])
    print(f"GOTOWE: {out} ({os.path.getsize(out)} B)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
