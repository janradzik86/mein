#!/usr/bin/env python3
"""make_instrukcja.py — generuje RELEASE/INSTRUKCJA.pdf (instrukcja instalacji i obsługi).

PDF powstaje z tego skryptu (reportlab + DejaVuSans, pełne polskie znaki), a nie
z zewnętrznego edytora — dzięki temu treść jest w repozytorium i zawsze zgodna
z aktualną wersją plików. Rozmiary i sumy SHA-256 są wstawiane z RELEASE/.
"""
from __future__ import annotations

import hashlib
import os

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (KeepTogether, ListFlowable, ListItem, PageBreak,
                                Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REL = os.path.join(ROOT, "RELEASE")
OUT = os.path.join(REL, "INSTRUKCJA.pdf")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FONT_B = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_M = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"


def sha256(p):
    h = hashlib.sha256()
    with open(p, "rb") as fh:
        for c in iter(lambda: fh.read(1 << 20), b""):
            h.update(c)
    return h.hexdigest()


def size(n):
    return f"{n / 1048576.0:.1f} MB" if n >= 1048576 else f"{n / 1024.0:.0f} kB"


def files_table():
    rows = [["Plik", "Rozmiar", "Przeznaczenie"]]
    cel = {
        "WOJAN-LaserCraft-AI-Android-Release.apk": "telefon/tablet z Androidem 7.0+",
        "WOJAN-LaserCraft-AI-Windows-Setup.exe": "Windows 11 x64 — instalator (program, czcionki, 26 projektów, dokumentacja)",
        "WOJAN-LaserCraft-AI.exe": "Windows 11 x64 — sam program, bez instalacji",
    }
    for n in cel:
        p = os.path.join(REL, n)
        if os.path.exists(p):
            rows.append([n, size(os.path.getsize(p)), cel[n]])
    return rows


def build():
    pdfmetrics.registerFont(TTFont("DejaVu", FONT))
    pdfmetrics.registerFont(TTFont("DejaVu-Bold", FONT_B))
    pdfmetrics.registerFont(TTFont("DejaVuMono", FONT_M))
    st = getSampleStyleSheet()
    base = ParagraphStyle("b", parent=st["BodyText"], fontName="DejaVu", fontSize=9.5,
                          leading=13.5, alignment=TA_LEFT, spaceAfter=5)
    h1 = ParagraphStyle("h1", parent=base, fontName="DejaVu-Bold", fontSize=17, leading=21,
                        textColor=colors.HexColor("#111111"), spaceAfter=10)
    h2 = ParagraphStyle("h2", parent=base, fontName="DejaVu-Bold", fontSize=12, leading=15,
                        textColor=colors.HexColor("#2b2b2b"), spaceBefore=10, spaceAfter=5)
    small = ParagraphStyle("s", parent=base, fontSize=8, leading=11,
                           textColor=colors.HexColor("#555555"))
    mono = ParagraphStyle("m", parent=base, fontName="DejaVuMono", fontSize=8, leading=11)

    def bullets(items, style=base):
        return ListFlowable([ListItem(Paragraph(t, style), leftIndent=12) for t in items],
                            bulletType="bullet", start="•", leftIndent=12, bulletFontSize=7)

    S = []
    S.append(Paragraph("WOJAN LaserCraft AI Studio — instrukcja", h1))
    S.append(Paragraph("Wersja 1.1.0 · wydanie offline · Windows 11 x64 i Android 7.0+", base))
    S.append(Spacer(1, 6))
    S.append(Paragraph(
        "Program generuje wzory do grawerowania i cięcia laserowego: wektorowe projekty, "
        "napisy z polskimi znakami, kody QR, import zdjęć, walidację geometrii oraz eksport "
        "do LightBurn i LaserGRBL. Silnik działa lokalnie — nie wymaga konta, chmury ani "
        "kluczy API. Internet jest potrzebny wyłącznie wtedy, gdy sam go włączysz "
        "(biblioteka trendów).", base))

    S.append(Paragraph("1. Co jest w wydaniu", h2))
    t = Table(files_table(), colWidths=[66 * mm, 16 * mm, 98 * mm], hAlign="LEFT")
    t.setStyle(TableStyle([
        ("FONTNAME", (0, 0), (-1, 0), "DejaVu-Bold"),
        ("FONTNAME", (0, 1), (-1, -1), "DejaVu"),
        ("FONTSIZE", (0, 0), (-1, -1), 7.5),
        ("GRID", (0, 0), (-1, -1), 0.3, colors.HexColor("#bbbbbb")),
        ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#eeeeee")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
    ]))
    S.append(t)
    S.append(Paragraph(
        "Sumy kontrolne SHA-256 wszystkich plików są w SHA256SUMS.txt. Sprawdzenie na Windows:",
        small))
    S.append(Paragraph("Get-FileHash .\\WOJAN-LaserCraft-AI-Windows-Setup.exe -Algorithm SHA256",
                       mono))

    S.append(Paragraph("2. Instalacja na Androidzie", h2))
    S.append(bullets([
        "Wymagania: Android 7.0 (API 24) lub nowszy, dowolny procesor — aplikacja nie zawiera "
        "bibliotek natywnych, więc działa na ARM64 i x86_64.",
        "Skopiuj plik <b>WOJAN-LaserCraft-AI-Android-Release.apk</b> na telefon (kabel USB, "
        "Dysk Google, e-mail) albo otwórz link do wydania w przeglądarce telefonu.",
        "Jeśli masz zainstalowaną starszą wersję (1.0.0–1.0.5) — <b>najpierw ją odinstaluj</b> (1.1.0 ma nowy certyfikat podpisywania, więc aktualizacja „na wierzch” jest niemożliwa). "
        "Odinstalowanie usuwa zapisane projekty, więc przed nim zrób kopię "
        "(Moje projekty → Kopia ZIP).",
        "Otwórz plik z aplikacji „Pliki”/„Pobrane”. Android zapyta o zgodę na instalację "
        "z tego źródła — zezwól (Ustawienia → Aplikacje → Specjalny dostęp → Instalowanie "
        "nieznanych aplikacji → wybrana aplikacja).",
        "Po instalacji ikona „WOJAN LaserCraft AI” pojawi się w szufladzie aplikacji. "
        "Aplikacja używa domyślnej ikony systemowej — to nie jest błąd instalacji.",
        "Jeśli system napisze „Aplikacja nie została zainstalowana”, sprawdź, czy plik ma "
        "809 kB (a nie np. 40 kB strony HTML), i spróbuj ponownie po odinstalowaniu starej wersji.",
    ]))

    S.append(Paragraph("3. Instalacja na Windows 11", h2))
    S.append(bullets([
        "Uruchom <b>WOJAN-LaserCraft-AI-Windows-Setup.exe</b> (dwuklik).",
        "Program nie ma płatnego podpisu Authenticode, więc SmartScreen może pokazać "
        "„Windows ochronił Twój komputer”. Wybierz <b>Więcej informacji → Uruchom mimo to</b>. "
        "To normalne dla instalatorów bez certyfikatu komercyjnego.",
        "Instalator nie wymaga uprawnień administratora: domyślnie instaluje do "
        "<i>%LOCALAPPDATA%\\WOJAN LaserCraft AI</i> i tworzy skrót w menu Start oraz na pulpicie.",
        "W katalogu instalacji znajdziesz program, komplet czcionek, 26 przykładowych projektów "
        "oraz dokumentację.",
        "Odinstalowanie: Ustawienia → Aplikacje → WOJAN LaserCraft AI Studio → Odinstaluj "
        "albo plik Odinstaluj.exe w katalogu programu.",
    ]))

    S.append(Paragraph("4. Pierwsze uruchomienie", h2))
    S.append(Paragraph(
        "Po starcie widzisz 12 zakładek. Najkrótsza droga do gotowego wzoru:", base))
    S.append(bullets([
        "<b>Generator AI</b> — wpisz polecenie po polsku („tabliczka dla wędkarza, napis "
        "Wędkarz z pasją, 20×15 cm, tylko grawer”) i naciśnij GENERUJ AI. Przyciski obok "
        "dodają zdjęcie, plik, mikrofon albo losowy pomysł; GENERUJ 4 WARIANTY tworzy "
        "cztery różne kompozycje.",
        "<b>Kategorie</b> — 50 gotowych kategorii z podpowiedziami; można dodać własną.",
        "<b>Edytor</b> — przesuwanie, skalowanie, obrót, punkty, warstwy, siatka w mm, "
        "cofanie/ponawianie.",
        "<b>Napisy AI</b> — 10 propozycji napisu do wybranej okazji, z polskimi znakami.",
        "<b>QR</b> — kod QR jako wektor, z testem odczytu wykonywanym lokalnie.",
        "<b>SPRAWDŹ PRZED LASEREM</b> (Eksport/Diagnostyka) — walidacja geometrii, "
        "marginesów, kerfa i materiału; PVC jest blokowane.",
    ]))

    S.append(Paragraph("5. Offline i prywatność", h2))
    S.append(bullets([
        "Wszystkie obliczenia, generator i walidacja działają bez internetu.",
        "Projekty są zapisywane lokalnie: Android — katalog prywatny aplikacji "
        "(widoczny przez eksport/kopię ZIP); Windows — <i>%LOCALAPPDATA%\\WOJAN LaserCraft AI</i>.",
        "Brak kont, logowania, telemetrii i wysyłania projektów.",
        "Tryb online (biblioteka trendów) jest wyłącznie ręcznie włączany przez użytkownika "
        "i zawsze pokazuje źródło oraz datę.",
        "Część funkcji AI jest proceduralna (deterministyczny generator wektorowy + biblioteki "
        "kształtów + czcionki wektorowe). Program nie udaje sieci neuronowej: szczegóły "
        "w pliku MODELE-I-LICENCJE.md.",
        "Dyktowanie głosem: zakładka Mikrofon nagrywa dźwięk i pokazuje pole na wpisanie "
        "treści — pełne lokalne rozpoznawanie mowy (whisper.cpp) nie jest jeszcze w tej "
        "wersji, co jest świadomie zgłoszone jako brak.",
    ]))

    S.append(Paragraph("6. Eksport", h2))
    S.append(bullets([
        "SVG (profil LightBurn), DXF (R12, mm), PDF, PNG/JPG/BMP, G-code (tylko dla GRBL), ZIP.",
        "Pakiet produkcyjny zawiera: projekt_edytowalny.json, lightburn.svg, lasergrbl.png, "
        "cut_outer.svg, engrave_vector.svg, engrave_raster.png, preview.jpg, parametry.json, "
        "raport_walidacji.txt.",
        "Program nigdy nie generuje G-code dla nieznanego kontrolera i nie steruje laserem "
        "bezpośrednio — pliki wgrywasz w LightBurn/LaserGRBL.",
    ]))

    S.append(Paragraph("7. Rozwiązywanie problemów", h2))
    S.append(bullets([
        "Android, biały/pusty ekran: aplikacja zapisuje błąd startu w pliku crash.txt w swoim "
        "katalogu i pokazuje go na ekranie — zrób zdjęcie ekranu i wyślij je do nas.",
        "Android, instalacja odrzucona: odinstaluj starszą wersję, sprawdź rozmiar pliku, "
        "upewnij się, że instalujesz z „Plików”, a nie z podglądu przeglądarki.",
        "Windows, SmartScreen: Więcej informacji → Uruchom mimo to.",
        "Windows, brak czcionek w projekcie: użyj przycisku „Czcionki” w Ustawieniach — "
        "program wskazuje katalog fonts w instalacji.",
        "Zawsze możesz sprawdzić stan silnika w zakładce Diagnostyka AI (wersja, tryb offline, "
        "pamięć, liczba kategorii).",
    ]))
    S.append(PageBreak())
    S.append(Paragraph("8. Znane ograniczenia tej wersji", h2))
    S.append(bullets([
        "Brak lokalnego rozpoznawania mowy (STT) — nagranie + wpisany tekst.",
        "Brak sterowania laserem i brak generowania G-code dla kontrolerów innych niż GRBL.",
        "Ikona aplikacji Android jest domyślną ikoną systemową (brak zasobów graficznych w APK).",
        "Podpis APK jest własny (self-signed) — to normalne dla instalacji spoza sklepu; "
        "pozwala instalować aktualizacje zachowujące dane.",
        "Instalator Windows nie jest podpisany certyfikatem komercyjnym.",
        "Czas pracy lasera jest szacowany — zawsze potwierdź go w LightBurn/LaserGRBL.",
    ]))
    S.append(Paragraph(
        "Wersja 1.1.0. Data wydania: 2026-10-29. Kontakt i zgłaszanie błędów: przez repozytorium "
        "wydania (GitHub) lub bezpośrednio u wykonawcy.", small))

    doc = SimpleDocTemplate(OUT, pagesize=A4, leftMargin=18 * mm, rightMargin=18 * mm,
                            topMargin=16 * mm, bottomMargin=16 * mm,
                            title="WOJAN LaserCraft AI Studio — instrukcja 1.1.0",
                            author="WOJAN LaserCraft AI Studio", subject="Instrukcja instalacji")
    doc.build(S)
    print(f"OK {os.path.relpath(OUT, ROOT)} {os.path.getsize(OUT)} B")


if __name__ == "__main__":
    build()
