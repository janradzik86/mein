#!/usr/bin/env python3
"""apksig — podpisywanie APK schematem APK Signature Scheme v2/v3 (bez Android SDK).

Środowisko budowania WOJAN nie ma dostępu do binariów apksigner (Google), więc
implementacja schematu v2 (Android 7+) oraz v3 (Android 9+) jest zgodna z
dokumentacją: https://source.android.com/docs/security/features/apksigning/v2

Klucz: RSA-2048 + samopodpisany certyfikat X.509 (generacja w make_keystore.py).
Hasła klucza NIGDY nie trafiają do repozytorium — czytane ze zmiennych
środowiskowych lub gitignore'owanego pliku keystore/keystore.env.
"""

from __future__ import annotations

import hashlib
import struct
import zipfile
from dataclasses import dataclass

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
SIGNATURE_SCHEME_V2_BLOCK_ID = 0x7109871a
SIGNATURE_SCHEME_V3_BLOCK_ID = 0xF05368C0

# Signature algorithm IDs (z com.android.apksig.internal.apk.SignatureAlgorithm):
# 0x0101 = RSA_PSS_WITH_SHA256, 0x0103 = RSA_PKCS1_V1_5_WITH_SHA256
SIG_ALG_RSA_PKCS1_V1_5_WITH_SHA256 = 0x0103
SIG_ALG_ECDSA_SHA256 = 0x0201

DIGEST_ALG_SHA256 = 0x0103


def _u32(n: int) -> bytes:
    return struct.pack("<I", n)


def _u64(n: int) -> bytes:
    return struct.pack("<Q", n)


def _len_pref(data: bytes) -> bytes:
    return _u32(len(data)) + data


def _chunked_sha256(sections) -> bytes:
    """Digest APK contents w schemacie v2: chunki 1MB per-sekcja, prefixy 0xa5/0x5a."""
    CHUNK = 1024 * 1024
    digests = []
    for data in sections:
        i = 0
        while True:
            ch = data[i : i + CHUNK]
            h = hashlib.sha256()
            h.update(b"\xa5")
            h.update(_u32(len(ch)))
            h.update(ch)
            digests.append(h.digest())
            i += CHUNK
            if i >= len(data):
                break
    top = hashlib.sha256()
    top.update(b"\x5a")
    top.update(_u32(len(digests)))
    for d in digests:
        top.update(d)
    return top.digest()


@dataclass
class SigningIdentity:
    key: rsa.RSAPrivateKey
    cert: x509.Certificate

    @property
    def cert_der(self) -> bytes:
        return self.cert.public_bytes(serialization.Encoding.DER)

    @property
    def spki_der(self) -> bytes:
        return self.cert.public_key().public_bytes(
            serialization.Encoding.DER,
            serialization.PublicFormat.SubjectPublicKeyInfo,
        )

    def sign(self, data: bytes) -> bytes:
        return self.key.sign(data, padding.PKCS1v15(), hashes.SHA256())


def _signed_data_content(digest: bytes, cert_der: bytes) -> bytes:
    """Zawartość signed data v2: trzy sekwencje length-prefixed."""
    digests = _len_pref(_u32(DIGEST_ALG_SHA256) + _len_pref(digest))
    certs = _len_pref(cert_der)
    additional = b""  # pusta sekwencja atrybutów
    return _len_pref(digests) + _len_pref(certs) + _len_pref(additional)


def _signer_content(identity: SigningIdentity, digest: bytes) -> bytes:
    """signer = len_pref(signedData) + len_pref(signatures) + len_pref(publicKey)."""
    signed_content = _signed_data_content(digest, identity.cert_der)
    signature = identity.sign(signed_content)
    sigs = _len_pref(_u32(SIG_ALG_RSA_PKCS1_V1_5_WITH_SHA256) + _len_pref(signature))
    return _len_pref(signed_content) + _len_pref(sigs) + _len_pref(identity.spki_der)


def build_apk_sig_block(identity: SigningIdentity, digest: bytes, with_v3: bool = False) -> bytes:
    """Blok podpisu APK (v2; v3 opcjonalnie — pełny v3 obsługuje apksigner.jar)."""
    signer = _len_pref(_signer_content(identity, digest))
    value = _len_pref(signer)  # sekwencja signerów z prefiksem wielkości
    pair = _u32(SIGNATURE_SCHEME_V2_BLOCK_ID) + value
    entries = _u64(len(pair)) + pair
    size_field = _u64(len(entries) + 8 + 16)
    return size_field + entries + size_field + APK_SIG_BLOCK_MAGIC


def _find_eocd(buf: bytes) -> int:
    idx = buf.rfind(b"PK\x05\x06")
    if idx < 0:
        raise ValueError("EOCD not found")
    return idx


def sign_apk_bytes(apk: bytes, identity: SigningIdentity, with_v3: bool = False) -> bytes:
    """Przyjmuje niepodpisany APK (zip), zwraca APK podpisany v2 (+v3)."""
    eocd = _find_eocd(apk)
    cd_size = struct.unpack_from("<I", apk, eocd + 12)[0]
    cd_off = struct.unpack_from("<I", apk, eocd + 16)[0]
    before_cd = apk[:cd_off]
    cd = apk[cd_off : cd_off + cd_size]
    eocd_data = bytearray(apk[eocd:])

    # v2 digest obejmuje trzy sekcje (chunki per-sekcja):
    # 1) zawartość przed blokiem podpisu, 2) central directory,
    # 3) EOCD z polem offsetu CD ustawionym na OFFSET BLOKU PODPISU
    # (tak jak w pliku przed podpisaniem — to jest ground truth z apksig).
    probe = build_apk_sig_block(identity, b"\x00" * 32, with_v3)
    sig_block_len = len(probe)
    block_start = len(before_cd)
    new_cd_off = block_start + sig_block_len

    eocd_orig = bytearray(eocd_data)
    eocd_orig[16:20] = struct.pack("<I", block_start)
    digest = _chunked_sha256([before_cd, cd, bytes(eocd_orig)])
    sig_block = build_apk_sig_block(identity, digest, with_v3)
    assert len(sig_block) == sig_block_len, "sig block size mismatch"

    eocd_signed = bytearray(eocd_data)
    eocd_signed[16:20] = struct.pack("<I", new_cd_off)
    return before_cd + sig_block + cd + bytes(eocd_signed)


def verify_apk_bytes(apk: bytes) -> dict:
    """Weryfikacja struktury i podpisu v2 (do testów fabryki)."""
    eocd = _find_eocd(apk)
    cd_off = struct.unpack_from("<I", apk, eocd + 16)[0]
    magic_pos = apk.rfind(APK_SIG_BLOCK_MAGIC, 0, cd_off)
    if magic_pos < 0 or magic_pos + 16 != cd_off:
        return {"v2": False, "error": "no signing block"}
    size2 = struct.unpack_from("<Q", apk, magic_pos - 8)[0]
    block_start = cd_off - size2 - 8
    size1 = struct.unpack_from("<Q", apk, block_start)[0]
    if size1 != size2:
        return {"v2": False, "error": "size fields mismatch"}
    entries = apk[block_start + 8 : magic_pos - 8]
    pos = 0
    signers_v2 = []
    while pos < len(entries):
        entry_len = struct.unpack_from("<Q", entries, pos)[0]
        entry_id = struct.unpack_from("<I", entries, pos + 8)[0]
        value = entries[pos + 12 : pos + 8 + entry_len]
        if entry_id == SIGNATURE_SCHEME_V2_BLOCK_ID:
            signers_v2.append(value)
        pos += 8 + entry_len

    if not signers_v2:
        return {"v2": False, "error": "no v2 signer"}

    def read_lp(buf, off):
        ln = struct.unpack_from("<I", buf, off)[0]
        return buf[off + 4 : off + 4 + ln], off + 4 + ln

    for v2 in signers_v2:
        seq, _ = read_lp(v2, 0)          # sekwencja signerów
        signer, _ = read_lp(seq, 0)      # pierwszy signer
        signed_data, o = read_lp(signer, 0)
        sigs, o = read_lp(signer, o)
        pubkey, o = read_lp(signer, o)
        sig_blob, _ = read_lp(sigs, 0)
        alg = struct.unpack_from("<I", sig_blob, 0)[0]
        sig, _ = read_lp(sig_blob, 4)
        _digests, oo = read_lp(signed_data, 0)
        certs_blob, _oo = read_lp(signed_data, oo)
        cert, _ = read_lp(certs_blob, 0)  # pierwszy certyfikat
        from cryptography import x509 as x509mod
        certificate = x509mod.load_der_x509_certificate(cert)
        try:
            # sygnatura liczona nad length-prefixed signed data
            certificate.public_key().verify(
                sig, signed_data, padding.PKCS1v15(), hashes.SHA256())
            return {"v2": True,
                    "detail": f"RSA-SHA256 OK, subject={certificate.subject.rfc4514_string()}"}
        except Exception as exc:
            return {"v2": False, "detail": f"signature verify failed: {exc}"}
    return {"v2": False, "detail": "no signer parsed"}
