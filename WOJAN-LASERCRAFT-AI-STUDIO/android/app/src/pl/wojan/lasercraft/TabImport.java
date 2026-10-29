package pl.wojan.lasercraft;

import android.graphics.Bitmap;
import android.graphics.Path;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Zakładka 3 — Import zdjęcia: kadrowanie, tło, progowanie, dithering, wektoryzacja, SVG/DXF/PDF. */
public final class TabImport {

    static Bitmap pending;
    static String pendingName = "";
    static Bitmap previewMask;
    static Object lastResult; // Mask lub Path
    static double lastW, lastH;

    public static void handleImage(final MainActivity a, Uri uri) {
        Bitmap b = Img.load(a, uri, 1600);
        if (b == null) {
            Ui.toast(a, "Nie można wczytać obrazu (format nieobsługiwany?).");
            return;
        }
        pending = b;
        pendingName = "obraz";
        previewMask = null;
        lastResult = null;
        a.showTab(2);
        Ui.toast(a, "Wczytano obraz " + b.getWidth() + "×" + b.getHeight() + " px.");
    }

    static boolean isVector(String name) {
        String n = name.toLowerCase(java.util.Locale.US);
        return n.endsWith(".svg") || n.endsWith(".dxf") || n.endsWith(".pdf");
    }

    public static View build(final MainActivity a) {
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        LinearLayout pick = Ui.card(a);
        pick.addView(Ui.section(a, "Wczytaj materiał"));
        LinearLayout r = Ui.row(a);
        r.addView(TabGen.sqBtn(a, "ZDJĘCIE / GRAFIKA", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                a.pickImage();
            }
        }));
        r.addView(TabGen.sqBtn(a, "SVG / DXF / PDF / ZIP", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                a.pickProject();
            }
        }));
        pick.addView(Ui.hscroll(a, r));
        pick.addView(Ui.text(a, "Obsługiwane: JPG, PNG, WEBP, BMP, SVG, DXF, PDF (geometria), ZIP (projekt). "
                + "Limit importu: 64 MB na plik, 200 MB na archiwum, maks. 512 plików w ZIP.", 11, Ui.MUT, false));
        col.addView(pick);

        if (pending == null) {
            col.addView(Ui.text(a, "Nie wczytano jeszcze obrazu.", 13, Ui.MUT, false));
            return Ui.scroll(a, col);
        }

        ImageView iv = new ImageView(a);
        iv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 200)));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(previewMask != null ? previewMask : pending);
        col.addView(iv);

        final TextView stats = Ui.text(a, previewMask == null ? "Obraz źródłowy: " + pending.getWidth()
                + "×" + pending.getHeight() + " px." : lastStats, 12, Ui.MUT, false);
        col.addView(stats);

        LinearLayout opt = Ui.card(a);
        opt.addView(Ui.section(a, "Obróbka"));

        final SeekBar cropL = seek(a, opt, "Kadrowanie lewe (%)", 0, 40, 0);
        final SeekBar cropT = seek(a, opt, "Kadrowanie górne (%)", 0, 40, 0);
        final SeekBar cropR = seek(a, opt, "Kadrowanie prawe (%)", 0, 40, 0);
        final SeekBar cropB = seek(a, opt, "Kadrowanie dolne (%)", 0, 40, 0);
        final SeekBar bg = seek(a, opt, "Usuwanie tła — próg jasności (0 = wyłączone)", 0, 255, 0);
        final SeekBar level = seek(a, opt, "Próg binaryzacji", 0, 255, 128);

        List<String> modes = new ArrayList<String>();
        modes.add("Fotograwer rastrowy (dithering)");
        modes.add("Fotograwer rastrowy (progowanie)");
        modes.add("Wektoryzacja konturowa (SVG/DXF)");
        opt.addView(Ui.text(a, "Tryb wyjścia", 12, Ui.MUT, false));
        final Spinner spMode = Ui.spinner(a, modes, 0);
        opt.addView(spMode);

        List<String> dith = new ArrayList<String>();
        dith.add("Floyd–Steinberg");
        dith.add("Uporządkowany (Bayer 4×4)");
        opt.addView(Ui.text(a, "Dithering", 12, Ui.MUT, false));
        final Spinner spDith = Ui.spinner(a, dith, 0);

        LinearLayout dims = Ui.row(a);
        final EditText ew = Ui.num(a, "szerokość mm", Math.min(120, a.design.product.w - 2 * a.design.product.engraveMargin));
        dims.addView(ew, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        opt.addView(Ui.text(a, "Szerokość grafiki na produkcie (mm) — wysokość liczona z proporcji", 12, Ui.MUT, false));
        opt.addView(dims);
        opt.addView(spDith);
        col.addView(opt);

        LinearLayout actions = Ui.row(a);
        actions.addView(TabGen.sqBtn(a, "PRZETWÓRZ", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                process(a, iv, stats, cropL, cropT, cropR, cropB, bg, level, spMode, spDith, ew);
            }
        }));
        actions.addView(TabGen.sqBtn(a, "DODAJ DO PROJEKTU", Ui.ACC2, new View.OnClickListener() {
            public void onClick(View v) {
                if (lastResult == null) {
                    process(a, iv, stats, cropL, cropT, cropR, cropB, bg, level, spMode, spDith, ew);
                }
                addToDesign(a, spMode.getSelectedItemPosition(), spDith.getSelectedItemPosition(),
                        level.getProgress(), ew);
            }
        }));
        actions.addView(TabGen.sqBtn(a, "ANULUJ", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                pending = null;
                previewMask = null;
                lastResult = null;
                a.refresh();
            }
        }));
        col.addView(Ui.hscroll(a, actions));

        LinearLayout warn = Ui.card(a);
        warn.addView(Ui.text(a, "Uwaga: wektoryzacja konturowa odtwarza kształt, nie „pełny wektor” z "
                + "krzywymi jak w ilustracji wektorowej. Dla fotograwerowania lepszy jest raster "
                + "(warstwa ENGRAVE_RASTER), który zachowuje półtony.", 11, Ui.WARN, false));
        col.addView(warn);

        return Ui.scroll(a, col);
    }

    static String lastStats = "";

    static SeekBar seek(MainActivity a, LinearLayout parent, String label, int max, int maxVal, int init) {
        parent.addView(Ui.text(a, label, 12, Ui.MUT, false));
        SeekBar s = new SeekBar(a);
        s.setMax(maxVal);
        s.setProgress(init);
        parent.addView(s);
        return s;
    }

    static void process(MainActivity a, ImageView iv, TextView stats, SeekBar cl, SeekBar ct, SeekBar cr,
                        SeekBar cb, SeekBar bg, SeekBar level, Spinner mode, Spinner dith, EditText ew) {
        try {
            int w = pending.getWidth(), h = pending.getHeight();
            int x0 = w * cl.getProgress() / 100, y0 = h * ct.getProgress() / 100;
            int x1 = w - w * cr.getProgress() / 100, y1 = h - h * cb.getProgress() / 100;
            if (x1 - x0 < 8 || y1 - y0 < 8) {
                Ui.toast(a, "Kadrowanie zbyt agresywne — zostaw więcej obrazu.");
                return;
            }
            Bitmap crop = Bitmap.createBitmap(pending, x0, y0, x1 - x0, y1 - y0);
            double widthMm = Math.max(5, Math.min(a.design.product.w, Ui.val(ew, 80)));
            double heightMm = widthMm * crop.getHeight() / (double) crop.getWidth();
            lastW = widthMm;
            lastH = heightMm;
            int workW = Math.min(Img.MAX_WORK, crop.getWidth());
            Img.Gray g = Img.gray(crop, workW);
            if (bg.getProgress() > 0) Img.removeBackground(g, bg.getProgress());
            int m = mode.getSelectedItemPosition();
            if (m == 2) {
                Img.Mask mask = Img.threshold(g, level.getProgress(), 1);
                Path p = Img.vectorize(mask, widthMm, heightMm, 0.75);
                lastResult = p;
                previewMask = Img.maskToBitmap(mask);
                int contours = countSubPaths(p);
                lastStats = "Wektoryzacja: " + g.w + "×" + g.h + " px, konturów: " + contours
                        + ", wynik " + Core.fmt(widthMm) + "×" + Core.fmt(heightMm) + " mm.";
            } else {
                Img.Mask mask = (m == 0) ? Img.dither(g, dith.getSelectedItemPosition() == 1)
                        : Img.threshold(g, level.getProgress(), 1);
                lastResult = mask;
                previewMask = Img.maskToBitmap(mask);
                double pct = 100.0 * mask.dark / (mask.w * mask.h);
                double areaMm2 = widthMm * heightMm * pct / 100.0;
                double minutes = areaMm2 / (a.design.machine.lpi * 0.9) / 60.0 * 1.6;
                lastStats = "Raster: " + mask.w + "×" + mask.h + " px, ciemnych " + Core.fmt(pct)
                        + "%, czas orientacyjny ok. " + Core.fmt(Math.max(0.2, minutes)) + " min (szacunek).";
            }
            iv.setImageBitmap(previewMask);
            stats.setText(lastStats);
        } catch (Exception e) {
            Ui.toast(a, "Błąd obróbki: " + e.getMessage());
        }
    }

    static int countSubPaths(Path p) {
        float[] ap = p.approximate(0.5f);
        int n = 1;
        for (int i = 3; i < ap.length; i += 3) if (ap[i] < ap[i - 3]) n++;
        return n;
    }

    static void addToDesign(MainActivity a, int mode, int dither, int level, EditText ew) {
        if (lastResult == null) {
            Ui.toast(a, "Najpierw przetwórz obraz.");
            return;
        }
        a.checkpoint();
        double margin = Math.max(2, a.design.product.engraveMargin);
        double x = (a.design.product.w - lastW) / 2.0;
        double y = (a.design.product.h - lastH) / 2.0;
        Core.Obj o = new Core.Obj();
        o.id = a.design.newId("img");
        o.strokeW = 0.1;
        if (mode == 2) {
            Path p = (Path) lastResult;
            o.type = "path";
            o.layer = Core.L_ENGRAVE_VECTOR;
            o.role = "fill";
            o.name = "Wektoryzacja: " + pendingName;
            o.d = Draw.pathToSvgD(p, 0.15f);
            o.x = x;
            o.y = y;
        } else {
            Img.Mask mask = (Img.Mask) lastResult;
            String file = Store.saveRaster(a, a.design.id, "img" + a.design.nextId,
                    Store.encodePng(Img.maskToBitmap(mask)));
            o.type = "raster";
            o.layer = Core.L_ENGRAVE_RASTER;
            o.role = "fill";
            o.name = "Fotograwer: " + pendingName;
            o.d = file;
            o.imgW = lastW;
            o.imgH = lastH;
            o.x = x;
            o.y = y;
            o.threshold = level;
            o.dither = dither == 1 ? "ordered" : "floyd";
        }
        a.design.objects.add(o);
        a.commit("Import obrazu (" + (mode == 2 ? "wektoryzacja" : "raster") + ")");
        previewMask = null;
        lastResult = null;
        pending = null;
        Ui.toast(a, "Dodano do projektu: " + o.name);
    }

    /** Import SVG/DXF/PDF/ZIP — wywoływane po wyborze pliku. */
    public static void handleVector(MainActivity a, Uri uri, String name) {
        try {
            String lower = name.toLowerCase(java.util.Locale.US);
            if (lower.endsWith(".zip") || lower.endsWith(".wlcproj") || lower.endsWith(".json")) {
                TabProj.handleImport(a, uri);
                return;
            }
            byte[] data = Store.readUri(a, uri, Store.MAX_ENTRY);
            Imp.Result r;
            if (lower.endsWith(".svg")) {
                r = Imp.svg(new String(data, "UTF-8"));
            } else if (lower.endsWith(".dxf")) {
                r = Imp.dxf(new String(data, "ISO-8859-1"));
            } else if (lower.endsWith(".pdf")) {
                r = Imp.pdf(data);
            } else {
                Ui.toast(a, "Nieobsługiwany format: " + name);
                return;
            }
            if (r.error != null && !r.error.isEmpty()) {
                Ui.alert(a, "Import", r.error);
                return;
            }
            a.checkpoint();
            int added = 0;
            for (int i = 0; i < r.paths.size(); i++) {
                Path p = r.paths.get(i);
                if (p.isEmpty()) continue;
                android.graphics.RectF b = new android.graphics.RectF();
                p.computeBounds(b, true);
                Core.Obj o = new Core.Obj();
                o.id = a.design.newId("imp");
                o.type = "path";
                o.layer = a.design.product.requiresCut ? Core.L_CUT_OUTER : Core.L_ENGRAVE_VECTOR;
                o.role = "line";
                o.name = name + " #" + (i + 1);
                o.d = Draw.pathToSvgD(p, 0.12f);
                o.strokeW = 0.2;
                a.design.objects.add(o);
                added++;
            }
            a.commit("Import " + name + " (" + added + " obiektów)");
            Ui.alert(a, "Import zakończony", r.note + "\nDodano obiektów: " + added
                    + "\n\nSprawdź warstwy i uruchom „SPRAWDŹ PRZED LASEREM”.");
        } catch (Exception e) {
            Ui.toast(a, "Błąd importu: " + e.getMessage());
        }
    }
}
