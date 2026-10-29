#!/usr/bin/env python3
"""serwer_pobierania.py — lokalny serwer plików wydania (tylko do pobrania, bez logowania).

Serwuje RELEASE/ z poprawnymi nagłówkami:
- .apk  → application/vnd.android.package-archive, Content-Disposition: attachment
- .exe  → application/vnd.microsoft.portable-executable (wymuszone pobranie)
- index.html → strona z przyciskami pobierania i sumami SHA-256

Nasłuchuje na 0.0.0.0 (wymagane, żeby podgląd z przeglądarki działał).
"""
from __future__ import annotations

import hashlib
import html
import mimetypes
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "RELEASE")
CUSTOM_TYPES = {
    ".apk": "application/vnd.android.package-archive",
    ".exe": "application/vnd.microsoft.portable-executable",
    ".wlcproj": "application/json",
}
FILES = [
    ("WOJAN-LaserCraft-AI-Android-Release.apk", "Aplikacja Android (APK, podpisany Release)"),
    ("WOJAN-LaserCraft-AI-Windows-Setup.exe", "Instalator Windows 11 x64 (zawiera silnik, czcionki i 26 projektów)"),
    ("WOJAN-LaserCraft-AI.exe", "Sama aplikacja Windows (bez instalatora)"),
    ("SHA256SUMS.txt", "Sumy kontrolne SHA-256"),
]


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def human(n):
    return "%.1f MB" % (n / 1048576.0) if n >= 1048576 else "%.0f kB" % (n / 1024.0)


def build_index():
    rows = []
    for name, desc in FILES:
        p = os.path.join(ROOT, name)
        if not os.path.exists(p):
            rows.append('<tr><td colspan="4">brak pliku: %s</td></tr>' % html.escape(name))
            continue
        size = os.path.getsize(p)
        digest = sha256(p)
        rows.append(
            '<tr><td class="n"><a href="/%s" download>%s</a><div class="d">%s</div></td>'
            '<td class="s">%s</td><td class="h"><code>%s</code></td>'
            '<td><a class="btn" href="/%s" download>Pobierz</a></td></tr>'
            % (html.escape(name), html.escape(name), html.escape(desc),
               human(size), digest, html.escape(name))
        )
    return """<!DOCTYPE html>
<html lang="pl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>WOJAN LaserCraft AI Studio — pobieranie</title>
<style>
 :root{color-scheme:dark}
 body{margin:0;background:#0e1013;color:#e6e8ea;font:16px/1.5 system-ui,Segoe UI,Roboto,sans-serif}
 main{max-width:1080px;margin:0 auto;padding:32px 20px 64px}
 h1{font-size:26px;margin:0 0 4px;color:#ffb300;letter-spacing:.3px}
 .sub{color:#9aa3ad;margin:0 0 26px}
 table{width:100%%;border-collapse:collapse;background:#15181c;border-radius:10px;overflow:hidden}
 td,th{padding:14px 16px;border-bottom:1px solid #232830;vertical-align:top;text-align:left}
 th{background:#1b1f24;color:#9aa3ad;font-weight:600;font-size:13px;text-transform:uppercase;letter-spacing:.5px}
 tr:last-child td{border-bottom:none}
 .n a{color:#e6e8ea;text-decoration:none;font-weight:600}
 .n a:hover{color:#ffb300}
 .d{color:#8b949e;font-size:13px;margin-top:3px}
 .s{white-space:nowrap;color:#c9d1d9}
 code{font-size:12px;color:#8bd5a0;word-break:break-all}
 .btn{display:inline-block;background:#ffb300;color:#15181c;font-weight:700;padding:9px 18px;
      border-radius:8px;text-decoration:none;white-space:nowrap}
 .btn:hover{background:#ffc233}
 .note{margin-top:26px;padding:16px 18px;background:#15181c;border-left:3px solid #ffb300;border-radius:6px;
       color:#c9d1d9;font-size:14px}
 .note b{color:#ffb300}
 ul{margin:8px 0 0 18px;padding:0}
 li{margin:3px 0}
 @media(max-width:720px){ .h{display:none} th:nth-child(3){display:none} td{padding:12px} }
</style></head><body><main>
<h1>WOJAN LaserCraft AI Studio 1.0.4</h1>
<p class="sub">Programy offline do projektowania wzorów laserowych — bez konta, bez kluczy API, bez pobierania modeli.</p>
<table>
<tr><th>Plik</th><th>Rozmiar</th><th>SHA-256</th><th></th></tr>
%s
</table>
<div class="note">
<b>Instalacja</b>
<ul>
<li><b>Android:</b> pobierz APK, otwórz na telefonie, zezwól na instalację z tego źródła
(Android 8–16, ARM64). Podpisany kluczem wydania — kolejne wersje zainstalują się jako aktualizacja.</li>
<li><b>Windows 11 x64:</b> uruchom <code>WOJAN-LaserCraft-AI-Windows-Setup.exe</code>.
Instaluje do <code>%%LOCALAPPDATA%%\\Programs\\WOJAN LaserCraft AI</code>, tworzy skróty i wpis
w „Programy i funkcje”. Bez uprawnień administratora.</li>
<li><b>Ostrzeżenie Windows:</b> plik nie jest podpisany certyfikatem komercyjnym (SmartScreen może
pokazać „Windows chronił Twój komputer”). To nie jest wirus — sprawdź sumę SHA-256 powyżej.</li>
</ul>
</div>
</main></body></html>""" % "\n".join(rows)


class Handler(BaseHTTPRequestHandler):
    server_version = "WOJAN-LaserCraft/1.0"

    def _send(self, code, body, ctype, extra=None):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("X-Content-Type-Options", "nosniff")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def do_HEAD(self):
        self.do_GET(head=True)

    def do_GET(self, head=False):
        path = self.path.split("?")[0]
        if path in ("/", "/index.html"):
            body = build_index().encode("utf-8")
            self._send(200, body, "text/html; charset=utf-8")
            return
        name = os.path.basename(path)
        full = os.path.join(ROOT, name)
        if not os.path.isfile(full) or not os.path.abspath(full).startswith(ROOT):
            self._send(404, b"Nie ma takiego pliku", "text/plain; charset=utf-8")
            return
        ext = os.path.splitext(name)[1].lower()
        ctype = CUSTOM_TYPES.get(ext) or mimetypes.guess_type(name)[0] or "application/octet-stream"
        size = os.path.getsize(full)
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(size))
        self.send_header("Content-Disposition", 'attachment; filename="%s"' % name)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if head:
            return
        with open(full, "rb") as fh:
            while True:
                chunk = fh.read(1 << 20)
                if not chunk:
                    break
                try:
                    self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):
                    return

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    srv = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print("Serwer pobierania: http://0.0.0.0:%d/  (katalog: %s)" % (port, ROOT), flush=True)
    for name, _ in FILES:
        p = os.path.join(ROOT, name)
        if os.path.exists(p):
            print("  /%s  %d B  %s" % (name, os.path.getsize(p), sha256(p)[:16]), flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    main()
