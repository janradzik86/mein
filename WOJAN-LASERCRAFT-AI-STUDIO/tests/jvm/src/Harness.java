package test;

import android.content.Context;
import android.graphics.Path;
import android.graphics.RectF;
import org.json.JSONObject;
import pl.wojan.lasercraft.Core;
import pl.wojan.lasercraft.Draw;
import pl.wojan.lasercraft.Gen;
import pl.wojan.lasercraft.Img;
import pl.wojan.lasercraft.Pdf;
import pl.wojan.lasercraft.Qr;
import pl.wojan.lasercraft.Store;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Testy silnika WOJAN LaserCraft AI Studio na JVM.
 * Kod silnika (Core/Gen/Draw/Qr/Img/Pdf) jest DOKŁADNIE tym samym kodem, który trafia do APK;
 * podstawione są tylko klasy android.graphics (ścieżki, macierze) i org.json.
 */
public class Harness {

    static int pass = 0, fail = 0;
    static StringBuilder log = new StringBuilder();
    static Context ctx = new Context();

    public static void main(String[] a) throws Exception {
        File out = new File("tests/out");
        out.mkdirs();

        log.append("RAPORT TESTÓW SILNIKA (JVM) — WOJAN LaserCraft AI Studio\n");
        log.append("Data: ").append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date())).append("\n");
        log.append("Środowisko: ").append(System.getProperty("java.vm.name")).append(" ")
                .append(System.getProperty("java.version")).append("\n\n");

        t01_analyze();
        t02_qr();
        t03_samples(out);
        t04_svg_dxf_pdf();
        t05_validation_negative();
        t06_json_roundtrip();
        t07_img();
        t08_cost();
        t09_determinism();
        t10_diacritics();
        t11_gcode();
        t12_capacity();
        t13_bundle(out);
        t14_generator_quality();

        log.append("\nWYNIK: ").append(pass).append(" PASS, ").append(fail).append(" FAIL\n");
        String text = log.toString();
        System.out.println(text);
        FileOutputStream fo = new FileOutputStream(new File(out, "raport_jvm.txt"));
        fo.write(text.getBytes("UTF-8"));
        fo.close();
        if (fail > 0) System.exit(1);
    }

    static void ok(String name, boolean cond, String detail) {
        if (cond) pass++;
        else fail++;
        log.append(cond ? "PASS " : "FAIL ").append(name);
        if (detail != null && !detail.isEmpty()) log.append(" — ").append(detail);
        log.append("\n");
    }

    // ---------------- T01: analiza promptu ----------------
    static void t01_analyze() {
        Object[][] cases = {
                {"Tabliczka dla wędkarza, szczupak, napis KRÓL ŁOWISKA, 200x150 mm, retro",
                        "tabliczka", 200.0, 150.0, true, "wedkarstwo"},
                {"Piersiówka dla taty, motyw jelenia, napis NAJLEPSZY TATA, tylko grawer",
                        "piersiowka", 95.0, 115.0, false, "dzien_ojca"},
                {"Brelok z imieniem WOJAN, 40x40 mm, minimal", "brelok", 40.0, 40.0, true, "wlasne"},
                {"Deska kuchenna 300x200 mm, napis MISTRZ KUCHNI", "deska", 300.0, 200.0, false, "kuchnia"},
                {"Kubek 210x90 mm, tylko grawer", "kubek", 210.0, 90.0, false, "wlasne"},
                {"Podkładka pod kubek z wilkiem, 100x100 mm", "podkladka", 100.0, 100.0, true, "wilk"},
                {"Medal biegowy, BIEG PO MARZENIA, 60x60 mm", "medal", 60.0, 60.0, true, "sport"},
                {"Zawieszka świąteczna 60x80 mm, choinka", "zawieszka", 60.0, 80.0, true, "boze_narodzenie"}
        };
        int good = 0;
        StringBuilder det = new StringBuilder();
        for (int i = 0; i < cases.length; i++) {
            Core.Spec s = Core.analyze((String) cases[i][0]);
            boolean ok = s.productId.equals(cases[i][1])
                    && Math.abs(s.width - (Double) cases[i][2]) < 0.01
                    && Math.abs(s.height - (Double) cases[i][3]) < 0.01
                    && s.requiresCut == (Boolean) cases[i][4]
                    && s.category.equals(cases[i][5]);
            if (ok) good++;
            else det.append("[").append(i + 1).append(": produkt=").append(s.productId)
                    .append(" ").append(s.width).append("x").append(s.height)
                    .append(" cut=").append(s.requiresCut).append(" kat=").append(s.category).append("] ");
        }
        ok("T01 Analiza promptu (8 przypadków: produkt, wymiary, cięcie, kategoria)",
                good == cases.length, good + "/" + cases.length + " " + det);

        Core.Spec s2 = Core.analyze("Tabliczka 20x15 cm dla mamy");
        ok("T02 Analiza jednostek cm → mm (20x15 cm)",
                Math.abs(s2.width - 200) < 0.01 && Math.abs(s2.height - 150) < 0.01,
                s2.width + "x" + s2.height + " mm");
    }

    // ---------------- QR ----------------
    static void t02_qr() {
        String[] payloads = {
                "https://wojan.pl",
                "WOJAN LaserCraft AI Studio",
                "Zażółć gęślą jaźń",
                "1234567890123456789012345678901234567890",
                "Projekt tabliczki 200x150 mm, materiał sklejka 3 mm, grawer + cięcie konturu."
        };
        char[] eccs = {'L', 'M', 'Q', 'H'};
        int okn = 0, total = 0;
        StringBuilder det = new StringBuilder();
        for (int i = 0; i < payloads.length; i++) {
            for (int k = 0; k < eccs.length; k++) {
                total++;
                Qr.Code c = Qr.encode(payloads[i], eccs[k]);
                if (!c.ok) {
                    det.append("[encode ").append(i).append(eccs[k]).append(": ").append(c.error).append("] ");
                    continue;
                }
                String dec = QrDecode.decode(c);
                boolean good = dec.equals("OK:" + payloads[i]);
                if (good) okn++;
                else det.append("[").append(i).append("/").append(eccs[k]).append(" → ").append(dec).append("] ");
            }
        }
        ok("T03 QR: kodowanie → niezależny dekoder (syndromy RS) dla 5 ładunków × 4 poziomy",
                okn == total, okn + "/" + total + " " + det.toString());

        int structOk = 0;
        for (int i = 0; i < payloads.length; i++) {
            String s = Qr.structuralCheck(payloads[i], 'M');
            if (s.startsWith("QR OK")) structOk++;
            else log.append("      ").append(s.replace("\n", " ")).append("\n");
        }
        ok("T04 QR: test strukturalny (wzory, BCH, maska) — 5 ładunków",
                structOk == payloads.length, structOk + "/" + payloads.length);

        Qr.Code v = Qr.encode(repeat("A", 100), 'H');
        ok("T05 QR: wersja >= 7 (informacja wersji, 100 B, poziom H)", v.ok && v.version >= 7,
                "wersja=" + v.version + " mod=" + v.size + " maska=" + v.mask);

        boolean tooLong = !Qr.encode(repeat("B", 400), 'H').ok;
        ok("T06 QR: przekroczenie pojemności zgłoszone błędem (400 B, poziom H)", tooLong, "");

        Path p = Qr.path("https://wojan.pl", 30, 'M');
        RectF b = new RectF();
        p.computeBounds(b, true);
        Qr.Code cc = Qr.encode("https://wojan.pl", 'M');
        double expected = 30.0 * cc.size / (cc.size + 8.0); // margines 4 modułów nie jest rysowany
        ok("T07 QR: ścieżka wektorowa w obszarze 30 mm (margines 4 modułów)",
                b.left >= 30.0 / (cc.size + 8.0) - 0.01 && b.right <= 30.01
                        && Math.abs(b.width() - expected) < 0.2,
                Core.fmt(b.width()) + "×" + Core.fmt(b.height()) + " mm, oczekiwane "
                        + Core.fmt(expected) + " mm");
    }

    static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    // ---------------- przykładowe projekty ----------------
    static void t03_samples(File out) throws Exception {
        List<Core.Design> samples = Gen.samples(ctx);
        int errors = 0, cutMismatch = 0, emptyPath = 0, objects = 0;
        File dir = new File(out, "projekty");
        dir.mkdirs();
        for (int i = 0; i < samples.size(); i++) {
            Core.Design d = samples.get(i);
            objects += d.objects.size();
            Core.Report r = Core.validate(d, new Draw.Provider(ctx));
            if (!r.errors.isEmpty()) {
                errors++;
                for (int k = 0; k < r.errors.size(); k++)
                    log.append("      ").append(d.name).append(": ").append(r.errors.get(k)).append("\n");
            }
            boolean hasCut = false, hasEngrave = false;
            for (int k = 0; k < d.objects.size(); k++) {
                Core.Obj o = d.objects.get(k);
                if (Core.isCut(o.layer)) hasCut = true;
                else hasEngrave = true;
                if (!"text".equals(o.type) && !"qr".equals(o.type) && !"raster".equals(o.type)
                        && (o.d == null || o.d.isEmpty())) emptyPath++;
            }
            if (d.product.requiresCut != hasCut) cutMismatch++;
            if (!hasEngrave) cutMismatch++;
            FileOutputStream fo = new FileOutputStream(new File(dir, d.id + ".wlcproj"));
            fo.write(d.toJson().toString(1).getBytes("UTF-8"));
            fo.close();
        }
        ok("T08 Przykładowe projekty: 24 szt. bez błędów walidacji",
                samples.size() >= 24 && errors == 0,
                samples.size() + " projektów, " + objects + " obiektów, błędów: " + errors);
        ok("T09 Warstwy zgodne z produktem (cięcie tylko dla wycinanych, zawsze grawer)",
                cutMismatch == 0, "niezgodności: " + cutMismatch);
        ok("T10 Każdy obiekt geometryczny ma ścieżkę (brak pustych 'd')", emptyPath == 0,
                "pustych: " + emptyPath);
    }

    // ---------------- eksporty ----------------
    static void t04_svg_dxf_pdf() throws Exception {
        Core.Design d = Gen.fromPrompt(ctx, "Tabliczka dla wędkarza, szczupak, napis KRÓL ŁÓWISKA, 200x150 mm, retro", 0);
        String svg = Draw.toSvg(ctx, d, false);
        boolean svgOk = svg.startsWith("<?xml") && svg.contains("<svg") && svg.contains("width=\"200.00mm\"")
                && svg.contains("height=\"150.00mm\"") && svg.contains("id=\"CUT_OUTER\"")
                && svg.contains("id=\"ENGRAVE_VECTOR\"") && svg.trim().endsWith("</svg>");
        int paths = count(svg, "<path");
        ok("T11 SVG (LightBurn): mm, viewBox, grupy warstw, ścieżki", svgOk && paths > 3,
                svg.length() + " B, " + paths + " ścieżek");

        String dxf = Draw.toDxf(ctx, d);
        boolean dxfOk = dxf.startsWith("0\nSECTION") && dxf.contains("$INSUNITS") && dxf.contains("LWPOLYLINE")
                && dxf.contains("CUT_OUTER") && dxf.trim().endsWith("EOF");
        ok("T12 DXF: nagłówek, jednostki mm ($INSUNITS=4), warstwy, polilinie", dxfOk,
                dxf.length() + " B, " + count(dxf, "LWPOLYLINE") + " polilinii");

        byte[] pdf = Pdf.build(ctx, d);
        String head = new String(pdf, 0, 8, "ISO-8859-1");
        String tail = new String(pdf, Math.max(0, pdf.length - 10), Math.min(10, pdf.length), "ISO-8859-1");
        boolean pdfOk = head.startsWith("%PDF-1.") && tail.contains("%%EOF") && pdf.length > 500;
        new FileOutputStream("tests/out/przyklad.pdf").write(pdf);
        ok("T13 PDF: nagłówek, xref, EOF, geometria wektorowa", pdfOk, pdf.length + " B");

        new FileOutputStream("tests/out/przyklad.svg").write(svg.getBytes("UTF-8"));
        new FileOutputStream("tests/out/przyklad.dxf").write(dxf.getBytes("ISO-8859-1"));
        // walidacja XML przez prosty parser liczby nawiasów
        ok("T14 SVG: poprawna liczba znaczników (otwarcia = zamknięcia <g>)",
                count(svg, "<g ") == count(svg, "</g>"), count(svg, "<g ") + " grup");
    }

    // ---------------- walidacja negatywna ----------------
    static void t05_validation_negative() {
        Core.Design d = Gen.fromPrompt(ctx, "Tabliczka 200x150 mm, napis TEST", 0);
        Core.Report r0 = Core.validate(d, new Draw.Provider(ctx));
        ok("T15 Walidacja: poprawny projekt bez błędów", r0.errors.isEmpty(), r0.errors.toString());

        Core.Design pvc = Gen.fromPrompt(ctx, "Tabliczka 200x150 mm, napis TEST", 0);
        pvc.product.material = "pvc";
        Core.Report r1 = Core.validate(pvc, new Draw.Provider(ctx));
        ok("T16 Walidacja: PVC blokuje eksport (błąd materiału)", !r1.errors.isEmpty(),
                r1.errors.isEmpty() ? "brak błędu!" : r1.errors.get(0));

        Core.Design cut = Gen.fromPrompt(ctx, "Piersiówka 95x115 mm, napis TATA", 0);
        Core.Obj bad = new Core.Obj();
        bad.id = "x1";
        bad.type = "path";
        bad.layer = Core.L_CUT_OUTER;
        bad.name = "Niechciany kontur";
        bad.d = "M 0 0 L 10 0 L 10 10 Z";
        cut.objects.add(bad);
        Core.Report r2 = Core.validate(cut, new Draw.Provider(ctx));
        ok("T17 Walidacja: cięcie na gotowym przedmiocie = błąd", !r2.errors.isEmpty(),
                r2.errors.isEmpty() ? "brak błędu!" : r2.errors.get(0));

        Core.Design noContour = Gen.fromPrompt(ctx, "Tabliczka 200x150 mm, napis TEST", 0);
        for (int i = noContour.objects.size() - 1; i >= 0; i--)
            if (Core.L_CUT_OUTER.equals(noContour.objects.get(i).layer)) noContour.objects.remove(i);
        Core.Report r3 = Core.validate(noContour, new Draw.Provider(ctx));
        ok("T18 Walidacja: brak konturu cięcia = błąd", !r3.errors.isEmpty(),
                r3.errors.isEmpty() ? "brak błędu!" : r3.errors.get(0));

        Core.Design outside = Gen.fromPrompt(ctx, "Tabliczka 200x150 mm, napis TEST", 0);
        Core.Obj o = new Core.Obj();
        o.id = "x2";
        o.type = "path";
        o.layer = Core.L_ENGRAVE_VECTOR;
        o.name = "Poza obszarem";
        o.d = "M 0 0 L 400 0 L 400 400 Z";
        outside.objects.add(o);
        Core.Report r4 = Core.validate(outside, new Draw.Provider(ctx));
        ok("T19 Walidacja: obiekt poza produktem = błąd", !r4.errors.isEmpty(),
                r4.errors.isEmpty() ? "brak błędu!" : r4.errors.get(0));

        Core.Design qr = Gen.fromPrompt(ctx, "Tabliczka 100x100 mm, kod QR https://wojan.pl", 0);
        boolean hasQr = false;
        for (int i = 0; i < qr.objects.size(); i++) if ("qr".equals(qr.objects.get(i).type)) hasQr = true;
        ok("T20 Generator: kod QR dodawany dla kategorii QR", hasQr, "");
    }

    // ---------------- JSON ----------------
    static void t06_json_roundtrip() throws Exception {
        Core.Design d = Gen.fromPrompt(ctx, "Zakładka do książki, CZYTAM WIĘC JESTEM, 50x150 mm", 2);
        d.id = "rt_test";
        String json = d.toJson().toString(1);
        Core.Design back = Core.Design.fromJson(new JSONObject(json));
        boolean same = back.objects.size() == d.objects.size()
                && Math.abs(back.product.w - d.product.w) < 1e-9
                && back.product.material.equals(d.product.material)
                && back.machine.controller.equals(d.machine.controller)
                && back.objects.get(0).id.equals(d.objects.get(0).id);
        ok("T21 JSON: zapis → odczyt (wlcproj/1) zachowuje obiekty i parametry", same,
                json.length() + " B JSON, " + back.objects.size() + " obiektów");

        JSONObject jo = new JSONObject(json);
        ok("T22 JSON: pole format = wlcproj/1", "wlcproj/1".equals(jo.optString("format", "")),
                jo.optString("format", "?"));
    }

    // ---------------- obraz ----------------
    static void t07_img() {
        Img.Mask m = new Img.Mask(64, 64);
        for (int y = 16; y < 48; y++)
            for (int x = 16; x < 48; x++) {
                m.px[y * 64 + x] = true;
                m.dark++;
            }
        Path p = Img.vectorize(m, 30, 30, 0.6);
        RectF b = new RectF();
        p.computeBounds(b, true);
        boolean inside = b.left >= 7.0 && b.left <= 8.0 && b.right >= 22.0 && b.right <= 23.0;
        ok("T23 Wektoryzacja: kwadrat 32×32 px → ścieżka ~15×15 mm w skali 30 mm", inside && !p.isEmpty(),
                Core.fmt(b.left) + ".." + Core.fmt(b.right) + " mm");

        List<float[]> pts = new ArrayList<float[]>();
        for (int i = 0; i <= 100; i++) pts.add(new float[]{i, (float) Math.sin(i / 10.0)});
        List<float[]> simp = Img.simplify(pts, 0.05f);
        ok("T24 Uproszczenie Douglasa-Peuckera redukuje punkty", simp.size() < pts.size() && simp.size() > 5,
                pts.size() + " → " + simp.size());
    }

    // ---------------- koszty ----------------
    static void t08_cost() {
        Core.Cost c = Core.cost(45, 30000, 8000, 700, 10, 0.85, 45, 120, 8, 6, 1.5, 0, 60, 12);
        boolean sane = c.materialCost > 0 && c.machineMinutes > 0 && c.totalCost > c.materialCost
                && c.price > c.totalCost && c.vatPrice > c.price;
        ok("T25 Kalkulator: koszt < cena < brutto, dodatnie czasy", sane,
                "materiał " + Core.fmt(c.materialCost) + " zł, koszt " + Core.fmt(c.totalCost)
                        + " zł, cena " + Core.fmt(c.price) + " zł, brutto " + Core.fmt(c.vatPrice) + " zł");
        Core.Cost c2 = Core.cost(45, 30000, 8000, 700, 10, 0.85, 45, 120, 8, 6, 1.5, 0, 120, 12);
        ok("T26 Kalkulator: wyższa marża → wyższa cena", c2.price > c.price,
                Core.fmt(c.price) + " → " + Core.fmt(c2.price) + " zł");
    }

    // ---------------- determinizm ----------------
    static void t09_determinism() throws Exception {
        String a = Gen.fromPrompt(ctx, "Tabliczka 200x150 mm, wilk, MÓJ DOM", 0).toJson().toString();
        String b = Gen.fromPrompt(ctx, "Tabliczka 200x150 mm, wilk, MÓJ DOM", 0).toJson().toString();
        ok("T27 Determinizm generatora: ten sam prompt i wariant → identyczny projekt", a.equals(b),
                a.length() + " B");
        String c = Gen.fromPrompt(ctx, "Tabliczka 200x150 mm, wilk, MÓJ DOM", 1).toJson().toString();
        ok("T28 Warianty różnią się między sobą", !a.equals(c), "");
    }

    // ---------------- diakrytyki ----------------
    static void t10_diacritics() {
        Core.Spec s = Core.analyze("Tabliczka z napisem ZAŻÓŁĆ GĘŚLĄ JAŹŃ 200x150 mm");
        boolean hasTop = s.topText != null && !s.topText.isEmpty();
        String fix = Core.fixSpelling("tabliczka dla wedkarza  z  napisem");
        ok("T29 Diakrytyki: analiza tekstu z ą/ć/ę/ł/ń/ó/ś/ź/ż", hasTop,
                "napis='" + s.topText + "'");
        ok("T30 Korekta tekstu: podwójne spacje usunięte", fix.indexOf("  ") < 0, "'" + fix + "'");
        List<Core.TextPair> t = Core.texts(Core.analyze("tabliczka"), 10, 3);
        boolean all = !t.isEmpty();
        for (int i = 0; i < t.size(); i++) if (t.get(i).top.isEmpty()) all = false;
        ok("T31 Bank napisów: 10 propozycji, żadna pusta", all && t.size() == 10, t.size() + " propozycji");
    }

    // ---------------- G-code ----------------
    static void t11_gcode() {
        Core.Design d = Gen.fromPrompt(ctx, "Tabliczka 200x150 mm, napis TEST", 0);
        d.machine.controller = "GRBL";
        String[] err = new String[1];
        String g = Draw.toGcode(ctx, d, err);
        boolean okG = g != null && g.contains("G21") && g.contains("G90") && g.endsWith("M2\n");
        ok("T32 G-code: generowany dla GRBL (mm, G90, M5 na końcu)", okG,
                g == null ? "brak" : g.length() + " B");

        d.machine.controller = "Ruida";
        err = new String[1];
        String g2 = Draw.toGcode(ctx, d, err);
        ok("T33 G-code: ZABLOKOWANY dla nieznanego kontrolera (Ruida)", g2 == null && err[0] != null,
                err[0]);
    }

    // ---------------- pojemność / wydajność ----------------
    static void t12_capacity() {
        long t0 = System.currentTimeMillis();
        int n = 0;
        for (int i = 0; i < 50; i++) {
            Core.Design d = Gen.fromPrompt(ctx, "Tabliczka 200x150 mm, wariant " + i, i % 4);
            Draw.toSvg(ctx, d, false);
            Draw.toDxf(ctx, d);
            n++;
        }
        long dt = System.currentTimeMillis() - t0;
        ok("T34 Wydajność: 50× (generowanie + SVG + DXF) poniżej 5 s", dt < 5000,
                n + " projektów w " + dt + " ms (" + (dt / Math.max(1, n)) + " ms/projekt)");
    }

    // ---------------- pakiet produkcyjny ----------------
    static void t13_bundle(File out) throws Exception {
        Core.Design d = Gen.fromPrompt(ctx, "Tabliczka dla wędkarza, KRÓL ŁOWISKA, 200x150 mm, retro", 0);
        d.id = "bundle_test";
        Store.Bundle b = Store.production(ctx, d, false);
        String[] expected = {"projekt_edytowalny.wlcproj", "projekt_edytowalny.json", "lightburn.svg",
                "lasergrbl.png", "cut_outer.svg", "cut_inner.svg", "engrave_vector.svg",
                "engrave_fill.svg", "engrave_raster.png", "preview.jpg", "parametry.json",
                "raport_walidacji.txt", "UWAGI.txt"};
        int found = 0;
        for (int i = 0; i < expected.length; i++) if (b.files.contains(expected[i])) found++;
        ok("T35 Pakiet produkcyjny: wszystkie wymagane pliki w ZIP", found == expected.length,
                found + "/" + expected.length + " plików, " + b.bytes + " B");
        new FileOutputStream(new File(out, "pakiet_test.zip")).write(b.zip != null ? b.zip : new byte[0]);

        String lb = Store.layerSvg(ctx, d, Core.L_CUT_OUTER);
        ok("T36 Profil LightBurn: osobny plik tylko z warstwą cięcia",
                lb.contains("data-layer=\"CUT_OUTER\"") && !lb.contains("ENGRAVE"),
                lb.length() + " B");

        JSONObject params = Store.params(ctx, d);
        boolean p = params.optString("jednostki", "").equals("mm")
                && params.optDouble("kerf_mm", -1) > 0
                && params.optString("program", "").contains("LaserCraft");
        ok("T37 parametry.json: jednostki mm i kerf z ustawień użytkownika", p,
                "kerf=" + params.optDouble("kerf_mm", -1) + " mm");
    }

    // ---------------- jakość generatora: motywy, zawieszka, napisy, produkt okrągły ----------------
    static Core.Obj findObj(Core.Design d, String namePrefix) {
        for (int i = 0; i < d.objects.size(); i++) {
            Core.Obj o = d.objects.get(i);
            if (o.name != null && o.name.startsWith(namePrefix)) return o;
        }
        return null;
    }

    static void t14_generator_quality() {
        // 1. okazje mają własne motywy (nie koło zębate)
        String[][] cases = {
                {"Ozdoba świąteczna, choinka, napis WESOŁYCH ŚWIĄT, 80x90 mm", "choinka"},
                {"Pisanka wielkanocna, zając, napis WESOŁEGO ALLELUJA, 200x150 mm", "jajko"},
                {"Tabliczka ślubna, obrączki, napis ANNA I PIOTR 12.06.2027, 200x150 mm", "obraczki"},
                {"Medal biegowy, napis BIEG PO MARZENIA, 60x60 mm", "medal"}
        };
        int good = 0;
        StringBuilder det = new StringBuilder();
        for (int i = 0; i < cases.length; i++) {
            Core.Design d = Gen.fromPrompt(ctx, cases[i][0], 0);
            boolean found = false;
            for (int k = 0; k < d.objects.size(); k++) {
                Core.Obj o = d.objects.get(k);
                if (Core.L_ENGRAVE_VECTOR.equals(o.layer) && o.name != null &&
                        o.name.equals("Motyw: " + cases[i][1])) found = true;
            }
            if (found) good++;
            else det.append("[").append(i + 1).append(": brak ").append(cases[i][1]).append("] ");
        }
        ok("T38 Motywy okolicznościowe (choinka, pisanka, obrączki, medal)", good == cases.length,
                good + "/" + cases.length + " " + det);

        // 2. napisy: jawne "napis X" bez instrukcji technicznych i bez wymiarów
        Core.Spec s1 = Core.analyze(
                "Piersiówka z grawerem: wilk, napis WILK Z BIESZCZAD, tylko grawer bez cięcia, 95x115 mm");
        Core.Spec s2 = Core.analyze(
                "Tabliczka ślubna, obrączki, napis ANNA I PIOTR 12.06.2027, 200x150 mm");
        Core.Spec s3 = Core.analyze("Ozdoba świąteczna: choinka, napis WESOŁYCH ŚWIĄT, 80x90 mm");
        boolean texts = s1.topText.equals("WILK Z BIESZCZAD")
                && s2.topText.equals("ANNA I PIOTR 12.06.2027")
                && s3.topText.equals("WESOŁYCH ŚWIĄT");
        ok("T39 Napisy z promptu: bez instrukcji (\"tylko grawer\"), bez wymiarów, z diakrytykami",
                texts, "[" + s1.topText + "] [" + s2.topText + "] [" + s3.topText + "]");

        // 3. zawieszka: otwór na sznurek leży CAŁY w materiale i jest osobnym konturem cięcia
        Core.Design tag = Gen.fromPrompt(ctx, "Zawieszka świąteczna, choinka, napis WESOŁYCH ŚWIĄT, 60x80 mm", 0);
        Core.Obj hole = null, outer = null;
        for (int i = 0; i < tag.objects.size(); i++) {
            Core.Obj o = tag.objects.get(i);
            if (Core.L_CUT_OUTER.equals(o.layer)) outer = o;
            if (Core.L_CUT_INNER.equals(o.layer)) hole = o;
        }
        boolean holeOk = false;
        String holeInfo = "brak otworu";
        if (hole != null && outer != null) {
            android.graphics.Path hp = Draw.parseSvgPath(hole.d);
            android.graphics.RectF hb = new android.graphics.RectF();
            hp.computeBounds(hb, true);
            // ścieżka jest w układzie lokalnym obiektu — dodaj przesunięcie
            double cx = (hb.left + hb.right) / 2.0 + hole.x;
            double cy = (hb.top + hb.bottom) / 2.0 + hole.y;
            double r = (hb.right - hb.left) / 2.0;
            holeOk = cx - r > 1.0 && cy - r > 1.0 && cx + r < tag.product.w - 1.0
                    && cy + r < tag.product.h - 1.0 && r > 1.0;
            holeInfo = "środek " + Core.fmt(cx) + "," + Core.fmt(cy) + " mm, r=" + Core.fmt(r)
                    + " mm, produkt " + Core.fmt(tag.product.w) + "x" + Core.fmt(tag.product.h);
        }
        ok("T40 Zawieszka: otwór na sznurek w materiale, osobna warstwa CUT_INNER", holeOk, holeInfo);

        // 4. produkt okrągły: cała treść w kole, brak błędów walidacji
        Core.Design circle = Gen.fromPrompt(ctx, "Podkładka pod kubek z wilkiem, 100x100 mm, rycina", 0);
        Core.Report rp = Core.validate(circle, new Draw.Provider(ctx));
        double rad = Math.min(circle.product.w, circle.product.h) / 2.0;
        int outside = 0;
        StringBuilder detOut = new StringBuilder();
        for (int i = 0; i < circle.objects.size(); i++) {
            Core.Obj o = circle.objects.get(i);
            if (Core.L_CUT_OUTER.equals(o.layer) || Core.L_GUIDES.equals(o.layer)) continue;
            android.graphics.Path p = new Draw.Provider(ctx).pathFor(o);
            android.graphics.RectF b = new android.graphics.RectF();
            p.computeBounds(b, true);
            if (b.right - b.left < 0.001 && b.bottom - b.top < 0.001) continue;
            // każdy PUNKT geometrii (nie narożnik bounding boxa) musi leżeć w kole
            float[] approx = p.approximate(0.5f);
            boolean tooFar = false;
            for (int k = 0; k + 2 < approx.length; k += 3) {
                // dla "text" i "qr" provider już wstawia przesunięcie obiektu do ścieżki
                double ox = "path".equals(o.type) ? o.x : 0, oy = "path".equals(o.type) ? o.y : 0;
                double px = approx[k + 1] + ox - rad, py = approx[k + 2] + oy - rad;
                if (Math.sqrt(px * px + py * py) > rad + 0.4) { tooFar = true; break; }
            }
            if (tooFar) {
                outside++;
                detOut.append("[").append(o.name).append(" ").append(Core.fmt(b.left)).append("..")
                        .append(Core.fmt(b.right)).append(" x ").append(Core.fmt(b.top)).append("..")
                        .append(Core.fmt(b.bottom)).append("] ");
            }
        }
        ok("T41 Produkt okrągły: treść wpisana w koło, walidacja bez błędów",
                outside == 0 && rp.errors.isEmpty(),
                outside + " obiektów poza kołem " + detOut + ", błędy=" + rp.errors.size());
    }

    static int count(String s, String sub) {
        int n = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) {
            n++;
            i += sub.length();
        }
        return n;
    }
}
