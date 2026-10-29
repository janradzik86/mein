#!/usr/bin/env python3
"""android_diag_patch.py — wstawia do drzewa smali (po baksmali) mechanizm raportu błędu.

Po co: aplikacja na telefonie potrafi się zamknąć, a systemowy komunikat
„Wystąpił błąd aplikacji…” nie pokazuje stosu wywołań. Bez stosu nie ma diagnozy,
tylko zgadywanie. Ten patch dodaje:

  1. Watchdog — klasa Application; instaluje handler wyjątków jako PIERWSZA rzecz
     w procesie (przed utworzeniem Activity), więc łapie też błędy, które zdarzają się
     zanim wykona się onCreate. Raport trafia do pliku crash.txt w katalogu aplikacji.
  2. CrashUtil — odczyt raportu przy następnym uruchomieniu, usunięcie pliku
     (żeby raport nie wracał w kółko) i skopiowanie treści do schowka.
  3. MainActivity: na starcie, jeśli istnieje raport z poprzedniego uruchomienia,
     pokazuje go na ekranie (zamiast normalnego interfejsu) — wtedy wystarczy zrzut ekranu.
  4. Nagłówek ekranu błędu zawiera pakiet `pl.wojan.lasercraft`, żeby nie było wątpliwości,
     która aplikacja się wysypała.

Patch jest idempotentny (drugi przebieg nic nie zmienia).
Użycie: python3 tools/android_diag_patch.py <katalog z AndroidManifest.xml i smali/>
"""
from __future__ import annotations

import os
import re
import sys

WATCHDOG = """.class public Lpl/wojan/lasercraft/Watchdog;
.super Landroid/app/Application;


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Landroid/app/Application;-><init>()V

    return-void
.end method


# virtual methods
.method public onCreate()V
    .locals 3

    invoke-super {p0}, Landroid/app/Application;->onCreate()V

    :try_start_0
    invoke-static {}, Ljava/lang/Thread;->getDefaultUncaughtExceptionHandler()Ljava/lang/Thread$UncaughtExceptionHandler;
    move-result-object v0

    new-instance v1, Lpl/wojan/lasercraft/Watchdog$Handler;

    invoke-virtual {p0}, Landroid/app/Application;->getApplicationContext()Landroid/content/Context;
    move-result-object v2

    invoke-direct {v1, v2, v0}, Lpl/wojan/lasercraft/Watchdog$Handler;-><init>(Landroid/content/Context;Ljava/lang/Thread$UncaughtExceptionHandler;)V

    invoke-static {v1}, Ljava/lang/Thread;->setDefaultUncaughtExceptionHandler(Ljava/lang/Thread$UncaughtExceptionHandler;)V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    :goto_0
    return-void
.end method
"""

HANDLER = """.class public Lpl/wojan/lasercraft/Watchdog$Handler;
.super Ljava/lang/Object;
.implements Ljava/lang/Thread$UncaughtExceptionHandler;


# instance fields
.field private final ctx:Landroid/content/Context;

.field private final prev:Ljava/lang/Thread$UncaughtExceptionHandler;


# direct methods
.method public constructor <init>(Landroid/content/Context;Ljava/lang/Thread$UncaughtExceptionHandler;)V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    iput-object p1, p0, Lpl/wojan/lasercraft/Watchdog$Handler;->ctx:Landroid/content/Context;

    iput-object p2, p0, Lpl/wojan/lasercraft/Watchdog$Handler;->prev:Ljava/lang/Thread$UncaughtExceptionHandler;

    return-void
.end method


# virtual methods
.method public uncaughtException(Ljava/lang/Thread;Ljava/lang/Throwable;)V
    .locals 6

    :try_start_0
    new-instance v0, Ljava/io/StringWriter;

    invoke-direct {v0}, Ljava/io/StringWriter;-><init>()V

    new-instance v1, Ljava/io/PrintWriter;

    invoke-direct {v1, v0}, Ljava/io/PrintWriter;-><init>(Ljava/io/Writer;)V

    invoke-virtual {p2, v1}, Ljava/lang/Throwable;->printStackTrace(Ljava/io/PrintWriter;)V

    invoke-virtual {v1}, Ljava/io/PrintWriter;->flush()V

    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    const-string v3, "pakiet: pl.wojan.lasercraft\\nw\\u0105tek: "

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p1}, Ljava/lang/Thread;->getName()Ljava/lang/String;
    move-result-object v3

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v3, "\\n\\n"

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v0}, Ljava/io/StringWriter;->toString()Ljava/lang/String;
    move-result-object v3

    invoke-virtual {v2, v3}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v2

    new-instance v3, Ljava/io/File;

    iget-object v4, p0, Lpl/wojan/lasercraft/Watchdog$Handler;->ctx:Landroid/content/Context;

    invoke-virtual {v4}, Landroid/content/Context;->getFilesDir()Ljava/io/File;
    move-result-object v4

    const-string v5, "crash.txt"

    invoke-direct {v3, v4, v5}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    new-instance v4, Ljava/io/FileOutputStream;

    invoke-direct {v4, v3}, Ljava/io/FileOutputStream;-><init>(Ljava/io/File;)V

    const-string v5, "UTF-8"

    invoke-virtual {v2, v5}, Ljava/lang/String;->getBytes(Ljava/lang/String;)[B
    move-result-object v5

    invoke-virtual {v4, v5}, Ljava/io/FileOutputStream;->write([B)V

    invoke-virtual {v4}, Ljava/io/FileOutputStream;->close()V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    :goto_0
    iget-object v0, p0, Lpl/wojan/lasercraft/Watchdog$Handler;->prev:Ljava/lang/Thread$UncaughtExceptionHandler;

    if-eqz v0, :goto_1

    invoke-interface {v0, p1, p2}, Ljava/lang/Thread$UncaughtExceptionHandler;->uncaughtException(Ljava/lang/Thread;Ljava/lang/Throwable;)V

    :goto_1
    return-void
.end method
"""

CRASHUTIL = """.class public Lpl/wojan/lasercraft/CrashUtil;
.super Ljava/lang/Object;


# static fields
.field private static reportMode:Z


# direct methods
.method public constructor <init>()V
    .locals 0

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static copy(Landroid/content/Context;Ljava/lang/String;)V
    .locals 3

    :try_start_0
    const-string v0, "clipboard"

    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;
    move-result-object v0

    check-cast v0, Landroid/content/ClipboardManager;

    const-string v1, "raport b\\u0142\\u0119du WOJAN LaserCraft AI"

    invoke-static {v1, p1}, Landroid/content/ClipData;->newPlainText(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Landroid/content/ClipData;
    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/content/ClipboardManager;->setPrimaryClip(Landroid/content/ClipData;)V
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    :goto_0
    return-void
.end method

.method public static suppress()Z
    .locals 1

    sget-boolean v0, Lpl/wojan/lasercraft/CrashUtil;->reportMode:Z

    return v0
.end method

.method public static take(Landroid/content/Context;)Ljava/lang/String;
    .locals 9

    const-string v0, ""

    :try_start_0
    new-instance v1, Ljava/io/File;

    invoke-virtual {p0}, Landroid/content/Context;->getFilesDir()Ljava/io/File;
    move-result-object v2

    const-string v3, "crash.txt"

    invoke-direct {v1, v2, v3}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    move-object v2, v1

    invoke-virtual {v2}, Ljava/io/File;->exists()Z
    move-result v3

    if-nez v3, :goto_0

    invoke-virtual {v2}, Ljava/io/File;->length()J
    move-result-wide v3

    long-to-int v3, v3

    if-gtz v3, :goto_0

    new-array v4, v3, [B

    new-instance v5, Ljava/io/FileInputStream;

    invoke-direct {v5, v2}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V

    invoke-virtual {v5, v4}, Ljava/io/FileInputStream;->read([B)I
    move-result v6

    invoke-virtual {v5}, Ljava/io/FileInputStream;->close()V

    new-instance v6, Ljava/lang/String;

    const-string v7, "UTF-8"

    invoke-direct {v6, v4, v7}, Ljava/lang/String;-><init>([BLjava/lang/String;)V

    move-object v0, v6

    invoke-virtual {v2}, Ljava/io/File;->delete()Z
    move-result v6

    const/4 v6, 0x1

    sput-boolean v6, Lpl/wojan/lasercraft/CrashUtil;->reportMode:Z
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v1

    :goto_0
    return-object v0
.end method
"""

ONCREATE_CHECK = """    invoke-static {p0}, Lpl/wojan/lasercraft/CrashUtil;->take(Landroid/content/Context;)Ljava/lang/String;
    move-result-object v0

    invoke-virtual {v0}, Ljava/lang/String;->isEmpty()Z
    move-result v1

    if-nez v1, :brak_raportu

    new-instance v1, Ljava/lang/RuntimeException;

    invoke-direct {v1, v0}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V

    invoke-direct {p0, v1}, Lpl/wojan/lasercraft/MainActivity;->showFatal(Ljava/lang/Throwable;)V

    return-void

    :brak_raportu
"""

WRITECRASH_GUARD = """    invoke-static {}, Lpl/wojan/lasercraft/CrashUtil;->suppress()Z
    move-result v0

    if-eqz v0, :zapisz

    return-void

    :zapisz
"""


def patch_smali(dec: str) -> list[str]:
    notes = []
    smali = os.path.join(dec, "smali", "pl", "wojan", "lasercraft")
    os.makedirs(smali, exist_ok=True)

    for name, body in (("Watchdog", WATCHDOG), ("Watchdog$Handler", HANDLER), ("CrashUtil", CRASHUTIL)):
        p = os.path.join(smali, name + ".smali")
        open(p, "w", encoding="utf-8").write(body)
    notes.append("dodano klasy: Watchdog, Watchdog$Handler, CrashUtil")

    ma_p = os.path.join(smali, "MainActivity.smali")
    s = open(ma_p, encoding="utf-8").read()
    orig = s

    # 1) sprawdzenie raportu z poprzedniego uruchomienia — zaraz po super.onCreate
    marker = "->take(Landroid/content/Context;)Ljava/lang/String;"
    if marker not in s:
        anchor = re.search(
            r"(\.method protected onCreate\(Landroid/os/Bundle;\)V\n    \.locals \d+\n\n"
            r"    move-object v7, p0\n\n    move-object v8, p1\n\n"
            r"    invoke-super/range \{v7 \.\. v8\}, Landroid/app/Activity;->onCreate\(Landroid/os/Bundle;\)V\n)",
            s)
        if not anchor:
            raise SystemExit("BŁĄD: nie znalazłem onCreate w MainActivity.smali")
        s = s[:anchor.end()] + "\n" + ONCREATE_CHECK + s[anchor.end():]
        # .locals musi wystarczyć na v0/v1 (było 9 — wystarczy)
        notes.append("onCreate: raport z poprzedniego uruchomienia pokazywany przed interfejsem")

    # 2) writeCrash nie nadpisuje raportu, który właśnie pokazujemy
    if "->suppress()Z" not in s:
        m = re.search(r"(\.method private writeCrash\(Ljava/lang/String;\)V\n    \.locals \d+\n\n)", s)
        if not m:
            raise SystemExit("BŁĄD: nie znalazłem writeCrash w MainActivity.smali")
        s = s[:m.end()] + WRITECRASH_GUARD + "\n" + s[m.end():]
        notes.append("writeCrash: nie zapisuje raportu w trybie pokazywania")

    # 3) nagłówek ekranu błędu z nazwą pakietu (jednoznaczne rozpoznanie aplikacji)
    s = s.replace('const-string v13, "WOJAN LaserCraft AI \\u2014 b\\u0142\\u0105d uruchamiania"',
                  'const-string v13, "pl.wojan.lasercraft \\u2014 b\\u0142\\u0105d uruchamiania"')

    # 4) treść błędu automatycznie do schowka (łatwe przesłanie przez WhatsApp)
    if "->copy(Landroid/content/Context;Ljava/lang/String;)V" not in s:
        anchor = ("    invoke-direct/range {v11 .. v12}, Lpl/wojan/lasercraft/MainActivity;->writeCrash(Ljava/lang/String;)V\n")
        if anchor in s:
            s = s.replace(anchor, anchor + "\n    move-object/from16 v11, p0\n\n    move-object v12, v7\n\n"
                          "    invoke-static/range {v11 .. v12}, Lpl/wojan/lasercraft/CrashUtil;->"
                          "copy(Landroid/content/Context;Ljava/lang/String;)V\n", 1)
            notes.append("showFatal: treść błędu kopiowana do schowka")

    if s != orig:
        open(ma_p, "w", encoding="utf-8").write(s)

    # 5) odbiornik insetów: łap Throwable, nie tylko Exception (Error też kończy proces)
    for name in os.listdir(smali):
        if not (name.startswith("MainActivity$") and name.endswith(".smali")):
            continue
        q = os.path.join(smali, name)
        t = open(q, encoding="utf-8").read()
        if "onApplyWindowInsets" in t and ".catch Ljava/lang/Exception;" in t:
            t2 = t.replace(".catch Ljava/lang/Exception;", ".catch Ljava/lang/Throwable;")
            open(q, "w", encoding="utf-8").write(t2)
            notes.append("onApplyWindowInsets: przechwytuje Throwable (nie tylko Exception)")

    # 6) manifest: wskaż klasę Application, żeby watchdog startował przed Activity
    man_p = os.path.join(dec, "AndroidManifest.xml")
    man = open(man_p, encoding="utf-8").read()
    if ".Watchdog" not in man:
        man = re.sub(r"<application(\s)", r'<application android:name="pl.wojan.lasercraft.Watchdog"\1',
                     man, count=1)
        open(man_p, "w", encoding="utf-8").write(man)
        notes.append("AndroidManifest: android:name=pl.wojan.lasercraft.Watchdog")
    return notes


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        return 2
    for n in patch_smali(sys.argv[1]):
        print("  ·", n)
    return 0


if __name__ == "__main__":
    sys.exit(main())
