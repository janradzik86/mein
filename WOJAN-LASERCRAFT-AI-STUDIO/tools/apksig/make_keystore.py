#!/usr/bin/env python3
"""make_keystore.py — generuje klucz podpisujący APK (RSA-2048, X.509) w formacie PKCS#12.

Hasło czytane ze zmiennej środowiskowej WOJAN_KEYSTORE_PASS (nigdy z repozytorium).
Właściciel fabryki decyduje, czy użyć tego klucza, czy własnego (patrz
docs/BEZPIECZENSTWO.md — decyzja wymagana).
"""

import datetime
import os
import sys

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "keystore/wojan-release.p12"
    password = os.environ.get("WOJAN_KEYSTORE_PASS")
    if not password:
        raise SystemExit("ustaw WOJAN_KEYSTORE_PASS (hasło klucza) w środowisku")
    os.makedirs(os.path.dirname(out), exist_ok=True)

    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "WOJAN AI FACTORY"),
        x509.NameAttribute(NameOID.COMMON_NAME, "WOJAN AI FACTORY Release Key"),
    ])
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(datetime.datetime.utcnow() - datetime.timedelta(days=1))
        .not_valid_after(datetime.datetime.utcnow() + datetime.timedelta(days=3650))
        .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
        .sign(key, hashes.SHA256())
    )

    p12 = pkcs12.serialize_key_and_certificates(
        b"wojan", key, cert, None,
        serialization.BestAvailableEncryption(password.encode()),
    )
    with open(out, "wb") as fh:
        fh.write(p12)

    # publiczny certyfikat do wglądu (można w repo — to NIE jest sekret)
    pem_path = out.rsplit(".", 1)[0] + "-cert.pem"
    with open(pem_path, "wb") as fh:
        fh.write(cert.public_bytes(serialization.Encoding.PEM))
    print(f"keystore: {out}")
    print(f"certyfikat (publiczny): {pem_path}")
    print("hasło: [ze środowiska — nie zapisano]")
    return 0


if __name__ == "__main__":
    sys.exit(main())
