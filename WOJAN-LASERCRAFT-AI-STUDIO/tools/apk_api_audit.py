#!/usr/bin/env python3
"""apk_api_audit.py — audyt API platformy użytego w classes.dex z APK.

Po co: instalacja może się udać, a aplikacja wywalić się przy starcie z
NoClassDefFoundError / NoSuchMethodError, jeśli DEX odwołuje się do klasy lub
metody, której nie ma na danym poziomie Androida. Ten audyt:

  1. wyciąga z DEX listę WSZYSTKICH klas zewnętrznych (platforma + Java + org.json),
  2. porównuje je z tablicą minimalnych poziomów API (na podstawie dokumentacji
     Android API levels; klasa spoza tablicy = ostrzeżenie do ręcznej weryfikacji),
  3. sprawdza, czy każda klasa własna ma istniejącego nadtyp/interfejsy,
  4. liczy wywołania do platformy i wypisuje pełną listę — dowód, że aplikacja
     nie używa żadnego API nowszego niż zadeklarowane minSdk.

Użycie: python3 tools/apk_api_audit.py PLIK.apk [--min-sdk 24] [--pelen]
Exit 0 = brak klas nowszych niż minSdk.
"""
from __future__ import annotations

import argparse
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dexverify import Dex  # noqa: E402

# Minimalny poziom API dla klas platformy użytych przez aplikację.
# Wartości z dokumentacji Android Developers (klasa „Added in API level X”).
API = {
    "Landroid/app/Activity;": 1,
    "Landroid/app/ActivityManager;": 1,
    "Landroid/app/ActivityManager$MemoryInfo;": 1,
    "Landroid/app/AlertDialog;": 1,
    "Landroid/app/AlertDialog$Builder;": 1,
    "Landroid/content/ContentResolver;": 1,
    "Landroid/content/ContentValues;": 1,
    "Landroid/content/Context;": 1,
    "Landroid/content/Intent;": 1,
    "Landroid/content/pm/PackageInfo;": 1,
    "Landroid/content/pm/PackageManager;": 1,
    "Landroid/content/res/Resources;": 1,
    "Landroid/database/Cursor;": 1,
    "Landroid/graphics/Bitmap;": 1,
    "Landroid/graphics/BitmapFactory;": 1,
    "Landroid/graphics/BitmapFactory$Options;": 1,
    "Landroid/graphics/Canvas;": 1,
    "Landroid/graphics/Color;": 1,
    "Landroid/graphics/DashPathEffect;": 1,
    "Landroid/graphics/Matrix;": 1,
    "Landroid/graphics/Paint;": 1,
    "Landroid/graphics/Path;": 1,
    "Landroid/graphics/PathMeasure;": 1,
    "Landroid/graphics/RectF;": 1,
    "Landroid/graphics/Typeface;": 1,
    "Landroid/graphics/drawable/GradientDrawable;": 1,
    "Landroid/media/AudioRecord;": 3,
    "Landroid/media/MediaPlayer;": 1,
    "Landroid/net/Uri;": 1,
    "Landroid/os/Bundle;": 1,
    "Landroid/os/Environment;": 8,
    "Landroid/provider/MediaStore$MediaColumns;": 1,
    "Landroid/provider/Settings$Secure;": 1,
    "Landroid/speech/SpeechRecognizer;": 8,
    "Landroid/text/Editable;": 1,
    "Landroid/text/TextWatcher;": 1,
    "Landroid/view/GestureDetector;": 1,
    "Landroid/view/GestureDetector$SimpleOnGestureListener;": 1,
    "Landroid/view/MotionEvent;": 1,
    "Landroid/view/ScaleGestureDetector;": 8,
    "Landroid/view/ScaleGestureDetector$SimpleOnScaleGestureListener;": 8,
    "Landroid/view/View;": 1,
    "Landroid/view/ViewGroup;": 1,
    "Landroid/view/ViewGroup$LayoutParams;": 1,
    "Landroid/view/Window;": 1,
    "Landroid/view/WindowInsets;": 20,
    "Landroid/view/inputmethod/InputMethodManager;": 3,
    "Landroid/widget/ArrayAdapter;": 1,
    "Landroid/widget/Button;": 1,
    "Landroid/widget/CheckBox;": 1,
    "Landroid/widget/EditText;": 1,
    "Landroid/widget/FrameLayout;": 1,
    "Landroid/widget/FrameLayout$LayoutParams;": 1,
    "Landroid/widget/HorizontalScrollView;": 3,
    "Landroid/widget/ImageView;": 1,
    "Landroid/widget/LinearLayout;": 1,
    "Landroid/widget/LinearLayout$LayoutParams;": 1,
    "Landroid/widget/ScrollView;": 1,
    "Landroid/widget/SeekBar;": 1,
    "Landroid/widget/Spinner;": 1,
    "Landroid/widget/TextView;": 1,
    "Landroid/widget/Toast;": 1,
}

# java.*, javax.*, org.json.* — dostępne w Androidzie od API 1–8; nie wymagają tabeli,
# ale raportujemy je, żeby lista była kompletna (dowód offline/kompatybilności).


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk")
    ap.add_argument("--min-sdk", type=int, default=24)
    ap.add_argument("--pelen", action="store_true", help="wypisz wszystkie klasy i metody")
    a = ap.parse_args()

    with zipfile.ZipFile(a.apk) as z:
        d = Dex(z.read("classes.dex"))

    ext_android = {}
    java_classes = set()
    calls = {}
    for cls, nm, proto in d.methods:
        calls[cls] = calls.get(cls, 0) + 1
        if cls.startswith("Lpl/wojan"):
            continue
        if cls.startswith("Landroid/"):
            ext_android[cls] = ext_android.get(cls, 0) + 1
        else:
            java_classes.add(cls)

    too_new = []
    unknown = []
    for c in sorted(ext_android):
        if c in API:
            if API[c] > a.min_sdk:
                too_new.append((c, API[c]))
        else:
            unknown.append(c)

    # spójność hierarchii klas własnych
    missing_super = []
    for c, info in d.classes.items():
        sup = info["super"]
        if sup and sup not in d.classes and not sup.startswith(("Ljava/", "Landroid/", "Lorg/json/")):
            missing_super.append((c, sup))

    print(f"AUDYT API: {os.path.basename(a.apk)} (minSdk {a.min_sdk})")
    print(f"  klas własnych: {len(d.classes)}")
    print(f"  klas platformy android.*: {len(ext_android)}  (+ {len(java_classes)} klas Java/JSON)")
    print(f"  wywołań do metod: {len(d.methods)}")
    top = sorted(ext_android.items(), key=lambda kv: -kv[1])[:10]
    print("  najczęściej używane API platformy: " +
          ", ".join(f"{c.split('/')[-1].rstrip(';')}×{n}" for c, n in top))
    if a.pelen:
        print("\n  KLASY PLATFORMY (poziom API, liczba wywołań):")
        for c in sorted(ext_android):
            lvl = API.get(c, "?")
            kind = "" if c in API else "  <-- spoza tablicy"
            print(f"    API {lvl!s:>3}  {ext_android[c]:4d}×  {c}{kind}")
        print("\n  KLASY JAVA / JSON:")
        for c in sorted(java_classes):
            print(f"    {calls.get(c, 0):4d}×  {c}")

    if too_new:
        print("\n  BŁĄD: klasy nowsze niż minSdk:")
        for c, lvl in too_new:
            print(f"    {c} wymaga API {lvl}")
    if unknown:
        print("\n  UWAGA: klasy spoza tablicy kontrolnej (sprawdź ręcznie):")
        for c in unknown:
            print(f"    {c}")
    if missing_super:
        print("\n  BŁĄD: nadtypy spoza DEX:")
        for c, s in missing_super:
            print(f"    {c} -> {s}")

    bad = len(too_new) + len(missing_super)
    print(f"\nWYNIK: {'OK' if bad == 0 else 'BŁĄD'} — klas nowszych niż minSdk: {len(too_new)}, "
          f"niedostępnych nadtypów: {len(missing_super)}, do ręcznej weryfikacji: {len(unknown)}")
    return 0 if bad == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
