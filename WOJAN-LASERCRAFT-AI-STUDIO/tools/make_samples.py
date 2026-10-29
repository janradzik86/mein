#!/usr/bin/env python3
"""make_samples.py — generuje przykładowe projekty WYŁĄCZNIE silnikiem aplikacji.

Każdy projekt powstaje w lokalnym silniku (proceduralny generator wektorowy +
czcionki wektorowe). Nie ma tu żadnej grafiki wygenerowanej przez model obrazu —
zgodnie z wymaganiem: przykłady muszą być odtwarzalne z silnika.

Wynik: dist/przyklady/*.wlcproj (+ .png, _walidacja.txt) oraz dist/przyklady/SPIS.txt
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CLI = os.path.join(ROOT, "build", "wojan-cli")
OUT = os.path.join(ROOT, "dist", "przyklady")
RAW = os.path.join(OUT, "_raw")

PROMPTS = [
    ("Tabliczka z grawerem: szczupak, napis KRÓL ŁOWISKA, na dole RYBY SIĘ MNIE BOJĄ, 200x150 mm, sklejka 3 mm", "wedkarstwo", "sklejka3"),
    ("Piersiówka z grawerem: wilk w lesie, napis WILK Z BIESZCZAD, tylko grawer bez cięcia, 95x115 mm", "wilki", "stal"),
    ("Kubek z grawerem: napis KOCHAM KAWĘ, serce, bez cięcia, 210x90 mm", "dom_i_rodzina", "ceramika"),
    ("Deska kuchenna do krojenia: napis KUCHNIA BABCI, ornament, 300x200 mm, drewno", "dzien_babci", "drewno"),
    ("Medal dożynkowy: snop zboża, napis DOŻYNKI 2026, 60x60 mm, sklejka 3 mm", "rolnictwo", "sklejka3"),
    ("Brelok: litera W, napis WOJAN, 50x30 mm, skóra", "logo", "skora"),
    ("Zakładka do książki: napis CZYTAJ CODZIENNIE, liście, 50x150 mm, sklejka 2 mm", "hobby", "sklejka2"),
    ("Ozdoba świąteczna: choinka, napis WESOŁYCH ŚWIĄT, 80x90 mm, sklejka 3 mm", "boze_narodzenie", "sklejka3"),
    ("Medalion z grawerem: róża, napis DLA MAMY, 40x40 mm, aluminium, tylko grawer", "dzien_matki", "aluminium"),
    ("Naszywka skórzana: emblemat motocyklowy, napis WOLNOŚĆ, 80x40 mm, skóra", "motoryzacja", "skora"),
    ("Szyld warsztatowy: napis MAJSTER KOWALSKI, kowadło, 300x150 mm, sklejka 5 mm", "warsztat", "sklejka5"),
    ("Tarcza/emblemat: herb, napis RODZINA KOWALSKICH, 150x170 mm, sklejka 3 mm", "emblematy", "sklejka3"),
    ("Magnes na lodówkę: kot, napis KOT RZĄDZI, 60x45 mm, sklejka 3 mm", "zwierzeta", "sklejka3"),
    ("Etui skórzane: monogram JK, 110x80 mm, skóra", "prezenty", "skora"),
    ("Podkładka pod kubek: koło, mandala, napis KAWKA, 100x100 mm, mdf 3 mm", "minimalistyczne", "mdf3"),
    ("Skrzynka na narzędzia: napis NARZĘDZIA, ramka, 250x160 mm, sklejka 5 mm", "garaz", "sklejka5"),
    ("Zawieszka na choinkę: gwiazda, napis 2026, 60x80 mm, sklejka 3 mm", "boze_narodzenie", "sklejka3"),
    ("Portfel: grawer na skórze, napis INITIALS, ornament, 120x95 mm", "vintage", "skora"),
    ("Nieśmiertelnik: dane kontaktowe, kod QR, 50x28 mm, stal, tylko grawer", "pamiatki", "stal"),
    ("Dedykacja na drewnie: napis WSZYSTKIEGO NAJLEPSZEGO Z OKAZJI URODZIN, 200x150 mm, sklejka 3 mm", "urodziny", "sklejka3"),
    ("Pisanka wielkanocna: zając, napis WESOŁEGO ALLELUJA, 80x90 mm, sklejka 3 mm", "wielkanoc", "sklejka3"),
    ("Satyryczna tabliczka do garażu: napis NIE DOTYKAĆ, 200x150 mm, sklejka 3 mm", "satyra_spoleczna", "sklejka3"),
    ("Slubna tabliczka: obrączki, napis ANNA I PIOTR 12.06.2027, 200x150 mm, sklejka 3 mm", "sluby", "sklejka3"),
    ("Słowiański amulet: kołowrót, napis SWAROŻYC, 60x60 mm, sklejka 3 mm", "slowianskie", "sklejka3"),
    ("Portret fotograwerowy: twarz, napis PAMIĄTKA, 200x150 mm, sklejka 3 mm", "portrety", "sklejka3"),
    ("Tabliczka gamingowa: pada, napis LEVEL 99, 200x150 mm, sklejka 3 mm", "gaming", "sklejka3"),
]


# dodatkowe operacje edycyjne (wykonywane przez silnik, nie ręcznie)
EDITS = {
    19: ["dodaj kod QR"],
}


def slug(s):
    s = s.lower()
    rep = {"ą": "a", "ć": "c", "ę": "e", "ł": "l", "ń": "n", "ó": "o", "ś": "s", "ź": "z", "ż": "z"}
    for k, v in rep.items():
        s = s.replace(k, v)
    s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")
    return s[:60]


def run(args):
    r = subprocess.run(args, capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write(r.stdout + r.stderr)
        sys.exit("BŁĄD: %s" % " ".join(args))
    return r.stdout


def main():
    if not os.path.exists(CLI):
        sys.exit("BŁĄD: brak build/wojan-cli")
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(RAW, exist_ok=True)

    rows = []
    for i, (prompt, cat, mat) in enumerate(PROMPTS, 1):
        work = os.path.join(RAW, "p%02d" % i)
        os.makedirs(work, exist_ok=True)
        out = run([CLI, "generate", "--prompt", prompt, "--out", work])
        files = os.listdir(work)
        proj = [f for f in files if f.endswith(".wlcproj")]
        png = [f for f in files if f.endswith(".png")]
        val = [f for f in files if f.endswith("_walidacja.txt")]
        if not proj:
            sys.exit("BŁĄD: brak projektu dla: " + prompt)
        base = "p%02d-%s" % (i, slug(cat))
        dest_proj = os.path.join(OUT, base + ".wlcproj")
        shutil.copyfile(os.path.join(work, proj[0]), dest_proj)
        for cmd in EDITS.get(i, []):
            tmp = dest_proj + ".tmp"
            run([CLI, "edit", "--project", dest_proj, "--command", cmd, "--save", tmp])
            shutil.move(tmp, dest_proj)
            print("      edycja:", cmd)
        if png:
            shutil.copyfile(os.path.join(work, png[0]), os.path.join(OUT, base + ".png"))
        # raport walidacji zawsze liczony od gotowego pliku (po edycjach)
        rep = run([CLI, "validate", "--project", dest_proj])
        with open(os.path.join(OUT, base + "_walidacja.txt"), "w", encoding="utf-8") as fh:
            fh.write(rep)
        data = open(dest_proj, "rb").read()
        j = json.loads(data.decode("utf-8"))
        layers = sorted({o.get("layer", "") for o in j.get("objects", [])})
        err, warn = 0, 0
        if True:
            vt = rep
            m = re.search(r"błęd\w*:\s*(\d+)", vt)
            if m:
                err = int(m.group(1))
            m = re.search(r"ostrzeże\w*:\s*(\d+)", vt)
            if m:
                warn = int(m.group(1))
        rows.append({
            "nr": i, "plik": base + ".wlcproj", "kategoria": cat,
            "material": j.get("product", {}).get("material", ""),
            "produkt": j.get("product", {}).get("name", ""),
            "mm": "%gx%g" % (j.get("product", {}).get("w", 0), j.get("product", {}).get("h", 0)),
            "obiekty": len(j.get("objects", [])), "warstwy": ",".join(layers),
            "bledy": err, "ostrzezenia": warn,
            "sha256": hashlib.sha256(data).hexdigest()[:16],
            "prompt": prompt, "stdout": out.strip().splitlines()[-1] if out.strip() else "",
        })
        print("  [%02d] %-28s %-14s %-4s obj=%-3d err=%d warn=%d"
              % (i, base, rows[-1]["produkt"], rows[-1]["mm"], rows[-1]["obiekty"], err, warn))

    with open(os.path.join(OUT, "SPIS.txt"), "w", encoding="utf-8") as fh:
        fh.write("WOJAN LaserCraft AI Studio — przykładowe projekty\n")
        fh.write("Wszystkie projekty wygenerowane przez lokalny silnik aplikacji "
                 "(deterministycznie, bez grafiki z modeli obrazu).\n")
        fh.write("Format: .wlcproj (JSON) — otwierany w wersji Android i Windows.\n\n")
        fh.write("%-34s %-14s %-14s %-9s %-4s %-6s %s\n" % ("plik", "produkt", "kategoria", "mm", "obj", "bł/warn", "warstwy"))
        for r in rows:
            fh.write("%-34s %-14s %-14s %-9s %-4d %d/%-4d %s\n" % (
                r["plik"], r["produkt"], r["kategoria"], r["mm"], r["obiekty"],
                r["bledy"], r["ostrzezenia"], r["warstwy"]))
        fh.write("\nsuma obiektów: %d\n" % sum(r["obiekty"] for r in rows))
        fh.write("projektów: %d\n" % len(rows))

    with open(os.path.join(OUT, "spis.json"), "w", encoding="utf-8") as fh:
        json.dump(rows, fh, ensure_ascii=False, indent=1)

    bad = [r for r in rows if r["bledy"] > 0]
    print("projekty: %d, z błędami walidacji: %d" % (len(rows), len(bad)))
    shutil.rmtree(RAW, ignore_errors=True)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
