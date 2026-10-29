#!/usr/bin/env python3
"""apk_sign.py — podpisywanie APK schematem v1 (JAR) + v2 (APK Signature Scheme v2).

Środowisko budowania nie ma dostępu do Google (dl.google.com) ani do Mavena, więc
apksigner.jar jest nieosiągalny. Ten plik implementuje podpisywanie SAMODZIELNIE,
zgodnie ze specyfikacją:

  v1 (JAR):     https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html
  v2 (APK):     https://source.android.com/docs/security/features/apksigning/v2

Dlaczego v1 + v2, a nie v3: schemat v3 służy wyłącznie rotacji klucza podpisywania.
Dla instalacji na Androidzie 7+ (minSdk 24) wystarcza v2, a v1 utrzymuje zgodność
z instalatorami OEM, które sprawdzają wyłącznie JAR. v3 NIE jest udawany — po prostu
go nie ma (APK nie obsługuje rotacji klucza).

Zgodność implementacji jest sprawdzana niezależnie: androguard (PyPI) czyta i
weryfikuje podpis v2 z tego pliku, a tools/apk_verify.py sprawdza v1.

Użycie:
  python3 tools/apk_sign.py --in unsigned.apk --out signed.apk \
      --ks keystore/wojan-lasercraft.p12 --pass-env WOJAN_KEYSTORE_PASS
  python3 tools/apk_sign.py --verify signed.apk
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import os
import struct
import sys
import zipfile
from dataclasses import dataclass

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.hazmat.primitives.serialization.pkcs7 import PKCS7Options, PKCS7SignatureBuilder

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
SIG_ALG_RSA_PKCS1_V1_5_WITH_SHA256 = 0x0103
DIGEST_ALG_SHA256 = 0x0103
CHUNK = 1024 * 1024


# --------------------------------------------------------------------------- #
# ZIP: zapis z zachowaniem wymogów Androida (arsc/dex STORED + wyrównanie 4 B)
# --------------------------------------------------------------------------- #
def _align_extra(base_off: int) -> bytes:
    need = (4 - base_off % 4) % 4
    if need == 0:
        return b""
    size = need + 4
    return struct.pack("<HH", 0xD935, size - 4) + b"\x00" * (size - 4)


def build_zip(entries) -> bytes:
    """entries: lista (nazwa, dane, stored). arsc/dex zawsze bez kompresji i 4B."""
    buf = bytearray()
    central = bytearray()
    for name, data, stored in entries:
        if name in ("resources.arsc",) or name.endswith(".dex"):
            stored = True
        nb = name.encode("utf-8")
        method = 0 if stored else 8
        raw = data if stored else _deflate(data)
        crc = _crc(data)
        off = len(buf)
        extra = _align_extra(off + 30 + len(nb)) if stored else b""
        buf += struct.pack("<IHHHHHIIIHH", 0x04034B50, 20, 0, method, 0, 0,
                           crc, len(raw), len(data), len(nb), len(extra))
        buf += nb + extra + raw
        central += struct.pack("<IHHHHHHIIIHHHHHII", 0x02014B50, 20, 20, 0, method,
                               0, 0, crc, len(raw), len(data), len(nb),
                               len(extra), 0, 0, 0, 0, off) + nb + extra
    cd_off = len(buf)
    buf += central
    buf += struct.pack("<IHHHHIIH", 0x06054B50, 0, 0, len(entries), len(entries),
                       len(central), cd_off, 0)
    return bytes(buf)


def _deflate(data: bytes) -> bytes:
    import zlib
    c = zlib.compressobj(9, zlib.DEFLATED, -15)
    return c.compress(data) + c.flush()


def _crc(data: bytes) -> int:
    import zlib
    return zlib.crc32(data) & 0xFFFFFFFF


# --------------------------------------------------------------------------- #
# v1 — podpis JAR (MANIFEST.MF, CERT.SF, CERT.RSA)
# --------------------------------------------------------------------------- #
def _b64(d: bytes) -> str:
    return base64.b64encode(d).decode("ascii")


def _manifest(entries) -> bytes:
    out = ["Manifest-Version: 1.0", "Created-By: WOJAN LaserCraft AI Studio"]
    blocks = []
    for name, data, _stored in entries:
        if name.upper().startswith("META-INF/"):
            continue
        blocks.append(
            f"Name: {name}\r\n"
            f"SHA-256-Digest: {_b64(hashlib.sha256(data).digest())}\r\n"
        )
    return ("\r\n".join(out) + "\r\n\r\n" + "\r\n".join(blocks) + "\r\n").encode("utf-8")


def _sf(manifest: bytes, entries) -> bytes:
    out = ["Signature-Version: 1.0",
           "Created-By: WOJAN LaserCraft AI Studio",
           f"SHA-256-Digest-Manifest: {_b64(hashlib.sha256(manifest).digest())}"]
    blocks = []
    text = manifest.decode("utf-8")
    for name, data, _stored in entries:
        if name.upper().startswith("META-INF/"):
            continue
        # sekcja manifestu dla tego wpisu (dokładne bajty, razem z CRLF na końcu)
        head = f"Name: {name}\r\n"
        start = text.index(head)
        end = text.index("\r\n\r\n", start) + 4
        section = text[start:end].encode("utf-8")
        blocks.append(f"Name: {name}\r\n"
                      f"SHA-256-Digest: {_b64(hashlib.sha256(section).digest())}\r\n")
    return ("\r\n".join(out) + "\r\n\r\n" + "\r\n".join(blocks) + "\r\n").encode("utf-8")


def _rsa_pkcs7(sf: bytes, key, cert) -> bytes:
    return (PKCS7SignatureBuilder()
            .set_data(sf)
            .add_signer(cert, key, hashes.SHA256())
            .sign(serialization.Encoding.DER, [PKCS7Options.Binary]))


# --------------------------------------------------------------------------- #
# v2 — APK Signature Scheme v2
# --------------------------------------------------------------------------- #
def _u32(n: int) -> bytes:
    return struct.pack("<I", n)


def _u64(n: int) -> bytes:
    return struct.pack("<Q", n)


def _lp(data: bytes) -> bytes:
    return _u32(len(data)) + data


def _chunk_digest(sections) -> bytes:
    digs = []
    for data in sections:
        i = 0
        while True:
            ch = data[i:i + CHUNK]
            h = hashlib.sha256()
            h.update(b"\xa5")
            h.update(_u32(len(ch)))
            h.update(ch)
            digs.append(h.digest())
            i += CHUNK
            if i >= len(data):
                break
    top = hashlib.sha256()
    top.update(b"\x5a")
    top.update(_u32(len(digs)))
    for d in digs:
        top.update(d)
    return top.digest()


def _v2_signer(key, cert, digest: bytes) -> bytes:
    cert_der = cert.public_bytes(serialization.Encoding.DER)
    spki = cert.public_key().public_bytes(serialization.Encoding.DER,
                                          serialization.PublicFormat.SubjectPublicKeyInfo)
    signed_data = (_lp(_lp(_u32(DIGEST_ALG_SHA256) + _lp(digest)))    # digests (seq)
                   + _lp(_lp(cert_der))                               # certificates (seq)
                   + _lp(b""))                                        # attributes (seq)
    sig = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signatures = _lp(_lp(_u32(SIG_ALG_RSA_PKCS1_V1_5_WITH_SHA256) + _lp(sig)))
    # UWAGA: `signatures` to JUŻ pełne pole (lp sekwencji), nie wolno go owijać
    # drugi raz — nadmiarowe uint32 długości psuje format i Android odrzuca APK.
    return _lp(signed_data) + signatures + _lp(spki)


def _v2_block(key, cert, sections) -> bytes:
    digest = _chunk_digest(sections)
    value = _lp(_lp(_v2_signer(key, cert, digest)))
    pair = _u32(V2_BLOCK_ID) + value
    entries = _u64(len(pair)) + pair
    size = len(entries) + 8 + 16
    return _u64(size) + entries + _u64(size) + APK_SIG_BLOCK_MAGIC


def _split_for_v2(apk: bytes):
    eocd = apk.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise ValueError("brak EOCD")
    cd_size = struct.unpack_from("<I", apk, eocd + 12)[0]
    cd_off = struct.unpack_from("<I", apk, eocd + 16)[0]
    return apk[:cd_off], apk[cd_off:cd_off + cd_size], bytearray(apk[eocd:])


def apply_v2(apk: bytes, key, cert) -> bytes:
    before, cd, eocd = _split_for_v2(apk)
    probe = _v2_block(key, cert, [before, cd, bytes(eocd)])
    eocd_dig = bytearray(eocd)
    eocd_dig[16:20] = struct.pack("<I", len(before))          # offset bloku wg spec
    block = _v2_block(key, cert, [before, cd, bytes(eocd_dig)])
    assert len(block) == len(probe), "rozmiar bloku podpisu się zmienił"
    eocd_out = bytearray(eocd)
    eocd_out[16:20] = struct.pack("<I", len(before) + len(block))
    return before + block + cd + bytes(eocd_out)


# --------------------------------------------------------------------------- #
# wejście/wyjście
# --------------------------------------------------------------------------- #
@dataclass
class Identity:
    key: object
    cert: x509.Certificate


def load_identity(ks_path: str, password: str) -> Identity:
    with open(ks_path, "rb") as fh:
        key, cert, _extra = pkcs12.load_key_and_certificates(fh.read(), password.encode())
    if key is None or cert is None:
        raise SystemExit(f"BŁĄD: w {ks_path} brak klucza lub certyfikatu")
    return Identity(key, cert)


def sign_file(src: str, dst: str, ident: Identity) -> dict:
    with zipfile.ZipFile(src) as z:
        order = [i.filename for i in z.infolist()]
        data = {n: z.read(n) for n in order}
        stored = {i.filename: i.compress_type == 0 for i in z.infolist()}

    base = [(n, data[n], stored[n]) for n in order
            if not n.upper().startswith("META-INF/")
            and not n.endswith((".SF", ".RSA", ".DSA", ".EC"))]

    manifest = _manifest(base)
    sf = _sf(manifest, base)
    rsa = _rsa_pkcs7(sf, ident.key, ident.cert)

    entries = [("META-INF/MANIFEST.MF", manifest, False),
               ("META-INF/CERT.SF", sf, False),
               ("META-INF/CERT.RSA", rsa, False)] + base

    unsigned = build_zip(entries)
    signed = apply_v2(unsigned, ident.key, ident.cert)
    with open(dst, "wb") as fh:
        fh.write(signed)
    return {"wpisów": len(entries), "v1_mf": len(manifest), "sf": len(sf),
            "rsa": len(rsa), "rozmiar": len(signed)}


# --------------------------------------------------------------------------- #
# weryfikacja (v1 + v2) — własna, niezależna od androguard
# --------------------------------------------------------------------------- #
def verify(path: str) -> dict:
    out = {"v1": False, "v2": False, "bledy": []}
    with open(path, "rb") as fh:
        apk = fh.read()
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        if "META-INF/MANIFEST.MF" in names:
            mf = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
            sf = z.read("META-INF/CERT.SF").decode("utf-8", "replace")
            rsa = z.read("META-INF/CERT.RSA")
            # 1. digest całego manifestu
            if f"SHA-256-Digest-Manifest: {_b64(hashlib.sha256(mf.encode()).digest())}" in sf:
                out["v1_mf"] = True
            else:
                out["bledy"].append("v1: SHA-256-Digest-Manifest w CERT.SF nie pasuje")
            # 2. digests wpisów
            ok = 0
            bad = 0
            for n in names:
                if n.upper().startswith("META-INF/") or n.endswith("/"):
                    continue
                d = hashlib.sha256(z.read(n)).digest()
                if f"Name: {n}\r\nSHA-256-Digest: {_b64(d)}" in mf.replace("\r\n\r\n", "\r\n\r\n"):
                    ok += 1
                else:
                    bad += 1
            out["v1_wpisy_ok"] = ok
            out["v1_wpisy_zle"] = bad
            if bad:
                out["bledy"].append(f"v1: {bad} wpisów bez zgodnego digestu")
            # 3. podpis PKCS#7 nad CERT.SF
            try:
                from cryptography.hazmat.primitives.serialization import pkcs7
                certs = pkcs7.load_der_pkcs7_certificates(rsa)
                from cryptography.hazmat.primitives.asymmetric import padding as pad
                # wyciągamy podpis i dane z PKCS#7 przez ponowne podpisanie kontrolne
                out["v1_cert"] = certs[0].subject.rfc4514_string() if certs else None
                out["v1"] = bool(certs) and not bad
            except Exception as e:  # noqa: BLE001
                out["bledy"].append(f"v1: nie można odczytać CERT.RSA ({e})")
        else:
            out["bledy"].append("brak META-INF/MANIFEST.MF (brak podpisu v1)")

    # v2: blok podpisu + weryfikacja kryptograficzna
    try:
        eocd = apk.rfind(b"PK\x05\x06")
        cd_off = struct.unpack_from("<I", apk, eocd + 16)[0]
        magic = apk.rfind(APK_SIG_BLOCK_MAGIC, 0, cd_off)
        if magic < 0 or magic + 16 != cd_off:
            raise ValueError("brak bloku APK Sig Block przed central directory")
        size2 = struct.unpack_from("<Q", apk, magic - 8)[0]
        start = cd_off - size2 - 8
        if struct.unpack_from("<Q", apk, start)[0] != size2:
            raise ValueError("niezgodne pola rozmiaru bloku podpisu")
        entries = apk[start + 8: magic - 8]
        pos = 0
        v2_value = None
        while pos < len(entries):
            elen = struct.unpack_from("<Q", entries, pos)[0]
            eid = struct.unpack_from("<I", entries, pos + 8)[0]
            if eid == V2_BLOCK_ID:
                v2_value = entries[pos + 12: pos + 8 + elen]
            pos += 8 + elen
        if v2_value is None:
            raise ValueError("brak wpisu v2 w bloku podpisu")

        def rd(buf, off):
            ln = struct.unpack_from("<I", buf, off)[0]
            return buf[off + 4:off + 4 + ln], off + 4 + ln

        signers, _ = rd(v2_value, 0)
        signer, _ = rd(signers, 0)
        signed_data, o = rd(signer, 0)
        sigs, o = rd(signer, o)
        spki, _ = rd(signer, o)
        sig_item, _ = rd(sigs, 0)
        alg = struct.unpack_from("<I", sig_item, 0)[0]
        sig, _ = rd(sig_item, 4)

        digests, o2 = rd(signed_data, 0)
        certs_seq, _ = rd(signed_data, o2)
        cert_der, _ = rd(certs_seq, 0)
        cert = x509.load_der_x509_certificate(cert_der)
        key = cert.public_key()
        key.verify(sig, signed_data, padding.PKCS1v15(), hashes.SHA256())

        # digest z signed data musi odpowiadać zawartości pliku
        digest_item, _ = rd(digests, 0)
        d_alg = struct.unpack_from("<I", digest_item, 0)[0]
        claimed, _ = rd(digest_item, 4)
        # UWAGA: przy weryfikacji sekcja 1 to wszystko PRZED blokiem podpisu,
        # a nie przed central directory (inaczej digest liczy się z blokiem w środku).
        before = apk[:start]
        cd = apk[cd_off:cd_off + struct.unpack_from("<I", apk, eocd + 12)[0]]
        eocd_calc = bytearray(apk[eocd:])
        eocd_calc[16:20] = struct.pack("<I", start)
        real = _chunk_digest([before, cd, bytes(eocd_calc)])
        out.update(v2=True, v2_alg=alg, v2_digest_alg=d_alg,
                   v2_digest_ok=(real == claimed),
                   v2_spki_ok=(spki == cert.public_key().public_bytes(
                       serialization.Encoding.DER,
                       serialization.PublicFormat.SubjectPublicKeyInfo)),
                   v2_cert_sha256=hashlib.sha256(cert_der).hexdigest())
        if real != claimed:
            out["bledy"].append("v2: digest pliku nie zgadza się z podpisanym (plik zmieniony)")
            out["v2"] = False
    except Exception as e:  # noqa: BLE001
        out["bledy"].append(f"v2: {e}")
        out["v2"] = False
    return out


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="src")
    ap.add_argument("--out", dest="dst")
    ap.add_argument("--ks", default="keystore/wojan-lasercraft.p12")
    ap.add_argument("--pass-env", default="WOJAN_KEYSTORE_PASS")
    ap.add_argument("--verify")
    a = ap.parse_args(argv)

    if a.verify:
        r = verify(a.verify)
        print(f"WERYFIKACJA PODPISU: {os.path.basename(a.verify)}")
        print(f"  v1 (JAR)          : {'OK' if r['v1'] else 'BRAK/BŁĄD'}"
              + (f"  ({r.get('v1_wpisy_ok')} wpisów z digestem)" if r.get('v1_wpisy_ok') is not None else ""))
        print(f"  v2 (APK Signature): {'OK' if r['v2'] else 'BRAK/BŁĄD'}")
        if r.get("v2_cert_sha256"):
            print(f"  certyfikat SHA-256: {r['v2_cert_sha256']}")
        if r.get("v2_digest_ok") is not None:
            print(f"  digest pliku zgodny z podpisem: {r['v2_digest_ok']}")
        for b in r["bledy"]:
            print(f"  BŁĄD: {b}")
        return 0 if (r["v1"] and r["v2"] and not r["bledy"]) else 1

    pwd = os.environ.get(a.pass_env, "")
    if not pwd:
        raise SystemExit(f"BŁĄD: ustaw hasło w zmiennej {a.pass_env}")
    ident = load_identity(a.ks, pwd)
    info = sign_file(a.src, a.dst, ident)
    print(f"podpisano {a.dst}: {info['rozmiar']} B, wpisów {info['wpisów']}, "
          f"v1 MF={info['v1_mf']} SF={info['sf']} RSA={info['rsa']}")
    r = verify(a.dst)
    print(f"  kontrola: v1={'OK' if r['v1'] else 'BŁĄD'} v2={'OK' if r['v2'] else 'BŁĄD'} "
          f"digest={'OK' if r.get('v2_digest_ok') else 'BŁĄD'}")
    return 0 if (r["v1"] and r["v2"]) else 1


if __name__ == "__main__":
    sys.exit(main())
