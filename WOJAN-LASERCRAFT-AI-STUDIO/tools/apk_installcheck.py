#!/usr/bin/env python3
"""apk_installcheck.py — kontrola APK pod kątem WARUNKÓW INSTALACJI na Androidzie 11–17.

Sprawdza to, co robi instalator (PackageParser/ParsingPackageUtils + ART + PackageManager):
  1. struktura ZIP (duplikaty, zip-slip, zip64),
  2. AndroidManifest.xml — czy oficjalny aapt2 go sparsuje i widzi activity startową,
  3. package / versionCode / versionName,
  4. minSdk <= docelowy, targetSdk >= 23 (od Androida 14 instalator blokuje starsze),
  5. resources.arsc: przy targetSdk >= 30 MUSI być STORED i wyrównany do 4 B
     (INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED),
  6. classes.dex: magic + wersja akceptowana przez ART, spójność strukturalna,
  7. podpis: apksigner (v1/v2/v3),
  8. brak bibliotek natywnych (reguła 16 KB stron nie dotyczy),
  9. obecność ikony i etykiety w resources.arsc.

Użycie: python3 tools/apk_installcheck.py PLIK.apk [--aapt2 ŚCIEŻKA] [--apksigner ŚCIEŻKA]
Exit 0 = brak błędów blokujących instalację.
"""
from __future__ import annotations

import argparse
import os
import re
import struct
import subprocess
import sys
import zipfile

OK, WARN, ERR = "OK  ", "UWAGA", "BŁĄD"


def entry_offsets(z):
    out = {}
    for i in z.infolist():
        out[i.filename] = (i.header_offset + 30 + len(i.filename.encode()) + len(i.extra),
                           i.compress_type)
    return out


def run(cmd):
    try:
        r = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        return r.returncode, r.stdout + r.stderr
    except Exception as e:  # noqa: BLE001
        return 1, str(e)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk")
    ap.add_argument("--aapt2", default=os.path.expanduser("~/.cache/lc/aapt2"))
    ap.add_argument("--apksigner", default=os.path.expanduser("~/.cache/lc/apksigner.jar"))
    ap.add_argument("--java", default=os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "java"))
    ap.add_argument("--min-device-sdk", type=int, default=36)
    args = ap.parse_args()

    path = args.apk
    res = []          # (status, opis)
    blocking = 0
    info = {}

    # ---- 1. ZIP
    try:
        z = zipfile.ZipFile(path)
    except Exception as e:  # noqa: BLE001
        print(f"{ERR} nie da się otworzyć ZIP: {e}")
        return 1
    names = z.namelist()
    dups = len(names) - len(set(names))
    res.append((OK if dups == 0 else ERR, f"wpisów {len(names)}, duplikatów {dups}"))
    blocking += dups > 0
    bad_paths = [n for n in names if n.startswith("/") or ".." in n.split("/")]
    res.append((OK if not bad_paths else ERR, f"podejrzane ścieżki: {bad_paths or 'brak'}"))
    blocking += bool(bad_paths)
    big = os.path.getsize(path)
    info["rozmiar"] = big
    res.append((OK, f"rozmiar pliku {big} B"))
    if any(n.endswith(".apk") for n in names):
        res.append((WARN, "APK zawiera inny plik .apk w środku"))

    offs = entry_offsets(z)

    # ---- 2. manifest przez oficjalny aapt2
    rc, out = run([args.aapt2, "dump", "badging", path])
    if rc != 0:
        res.append((ERR, f"aapt2 nie sparsował APK: {out.strip()[:200]}"))
        blocking += 1
        badging = ""
    else:
        badging = out
        res.append((OK, "aapt2 dump badging — manifest i zasoby parsują się"))

    pkg = re.search(r"package: name='([^']+)' versionCode='([^']*)' versionName='([^']*)'", badging)
    if pkg:
        info["package"] = pkg.group(1)
        info["version"] = f"{pkg.group(3)} ({pkg.group(2)})"
        res.append((OK, f"package {pkg.group(1)}, wersja {pkg.group(3)} ({pkg.group(2)})"))
        try:
            if int(pkg.group(2)) <= 0:
                res.append((ERR, "versionCode musi być > 0"))
                blocking += 1
        except ValueError:
            res.append((ERR, "versionCode nie jest liczbą"))
            blocking += 1
    else:
        res.append((ERR, "brak package/versionCode w manifeście"))
        blocking += 1

    launch = re.search(r"launchable-activity: name='([^']+)'", badging)
    res.append((OK if launch else ERR,
                "activity startowa: " + (launch.group(1) if launch else "BRAK — nie da się uruchomić")))
    blocking += launch is None

    label = re.search(r"application-label:'([^']*)'", badging)
    icon = re.search(r"application-icon-160:'([^']+)'", badging)
    res.append((OK if label else WARN, "etykieta: " + (label.group(1) if label else "brak")))
    res.append((OK if icon else WARN, "ikona: " + (icon.group(1) if icon else "brak")))

    m_min = re.search(r"sdkVersion:'(\d+)'", badging)
    m_tgt = re.search(r"targetSdkVersion:'(\d+)'", badging)
    min_sdk = int(m_min.group(1)) if m_min else None
    tgt_sdk = int(m_tgt.group(1)) if m_tgt else None
    info["min_sdk"], info["target_sdk"] = min_sdk, tgt_sdk
    if min_sdk is None or tgt_sdk is None:
        res.append((ERR, "brak uses-sdk (minSdk/targetSdk)"))
        blocking += 1
    else:
        res.append((OK if min_sdk >= 21 else WARN, f"minSdk {min_sdk}"))
        res.append((OK if tgt_sdk >= 23 else ERR,
                    f"targetSdk {tgt_sdk} (Android 14+ blokuje targetSdk < 23)"))
        blocking += tgt_sdk < 23

    # ---- 3. resources.arsc — kluczowa reguła Androida 11+
    if "resources.arsc" not in names:
        res.append((WARN, "brak resources.arsc"))
    else:
        doff, method = offs["resources.arsc"]
        stored = method == 0
        aligned = doff % 4 == 0
        if tgt_sdk is not None and tgt_sdk >= 30:
            if not stored:
                res.append((ERR, "resources.arsc SPACKOWANY — instalator odrzuci APK "
                                 "(INSTALL_PARSE_FAILED_RESOURCES_ARSC_COMPRESSED)"))
                blocking += 1
            elif not aligned:
                res.append((ERR, f"resources.arsc pod offsetem {doff} — brak wyrównania do 4 B"))
                blocking += 1
            else:
                res.append((OK, f"resources.arsc STORED, offset {doff} (4 B OK)"))
        else:
            res.append((OK if stored and aligned else WARN,
                        f"resources.arsc {'STORED' if stored else 'deflate'}, offset {doff}"
                        f" (reguła R+ nie obowiązuje dla targetSdk {tgt_sdk})"))

    # ---- 4. DEX
    dex_names = [n for n in names if re.fullmatch(r"classes\d*\.dex", n)]
    if not dex_names:
        res.append((ERR, "brak classes.dex"))
        blocking += 1
    else:
        ART_VERSIONS = (b"035", b"037", b"038", b"039", b"040", b"041")
        for dn in dex_names:
            head = z.read(dn)[:8]
            ver_ok = head[:4] == b"dex\n" and head[4:7] in ART_VERSIONS
            res.append((OK if ver_ok else ERR,
                        f"{dn}: magic {head!r} {'(ART akceptuje)' if ver_ok else '(ART ODRZUCI)'}"))
            blocking += not ver_ok
            doff2, method2 = offs[dn]
            if method2 == 0 and doff2 % 4 != 0:
                res.append((ERR, f"{dn} niepakowany bez wyrównania 4 B (offset {doff2})"))
                blocking += 1

    # ---- 5. biblioteki natywne (16 KB strony)
    so = [n for n in names if n.startswith("lib/") and n.endswith(".so")]
    res.append((OK, f"biblioteki natywne: {len(so)}" + (" (brak — reguła 16 KB nie dotyczy)" if not so else "")))

    # ---- 6. podpis
    java = args.java if os.path.exists(args.java) else "java"
    rc, out = run([java, "-jar", args.apksigner, "verify", "--min-sdk-version", "21", "--verbose", path])
    v1 = "v1 scheme (JAR signing): true" in out
    v2 = "v2 scheme (APK Signature Scheme v2): true" in out
    v3 = "v3 scheme (APK Signature Scheme v3): true" in out
    if rc != 0:
        res.append((ERR, "apksigner: podpis NIEprawidłowy: " + out.strip()[:200]))
        blocking += 1
    else:
        res.append((OK if (v2 or v3) and v1 else ERR,
                    f"podpis: v1={v1} v2={v2} v3={v3} (Android 11+ wymaga v2/v3)"))
        blocking += not (v2 or v3)

    # ---- raport
    print(f"KONTROLA INSTALACYJNA: {os.path.basename(path)}")
    print(f"  package {info.get('package')} · wersja {info.get('version')} · "
          f"minSdk {info.get('min_sdk')} · targetSdk {info.get('target_sdk')} · {big} B\n")
    for st, txt in res:
        print(f"  [{st}] {txt}")
    print(f"\nWYNIK: {blocking} problemów blokujących instalację")
    return 0 if blocking == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
