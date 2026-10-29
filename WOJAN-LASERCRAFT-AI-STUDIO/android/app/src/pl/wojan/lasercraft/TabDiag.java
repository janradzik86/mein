package pl.wojan.lasercraft;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Zakładka 11 — Diagnostyka AI: co działa lokalnie, testy silnika, zasoby. */
public final class TabDiag {

    public static View build(final MainActivity a) {
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        LinearLayout info = Ui.card(a);
        info.addView(Ui.section(a, "Silnik lokalny"));
        StringBuilder sb = new StringBuilder();
        sb.append("Wersja aplikacji: ").append(MainActivity.VERSION).append("\n");
        sb.append("Silnik: analiza języka polskiego (reguły), generator wektorowy, generator napisów, "
                + "koder QR, wektoryzacja bitmap, walidacja geometrii, kalkulator.\n");
        sb.append("Model językowy (LLM): BRAK wbudowanego modelu wagowego — funkcje „AI” w tej wersji "
                + "realizuje deterministyczny silnik regułowy + generator proceduralny. "
                + "Nie jest to model neuronowy i tak jest opisane w raporcie.\n");
        sb.append("Model mowy (Whisper): brak w pakiecie — używane jest rozpoznawanie offline modelu "
                + "systemowego, jeśli producent je dostarczył.\n");
        sb.append("Czcionki: 9 rodzin TTF wbudowanych w APK (SIL OFL / Apache — patrz Ustawienia).\n");
        sb.append("Sieć: aplikacja działa bez internetu; żadne dane nie są wysyłane.\n");
        col.addView(info);

        ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        LinearLayout dev = Ui.card(a);
        dev.addView(Ui.section(a, "Urządzenie"));
        dev.addView(Ui.text(a, "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n"
                + "Model: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "ABI: " + (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?") + "\n"
                + "RAM całkowity: " + (mi.totalMem / (1024 * 1024)) + " MB, wolny: " + (mi.availMem / (1024 * 1024)) + " MB\n"
                + "Wymagania aplikacji: ok. 60–120 MB RAM przy pracy z obrazem 480 px.", 12, Ui.MUT, false));
        col.addView(dev);

        final TextView out = Ui.text(a, "Naciśnij „URUCHOM TESTY”, aby sprawdzić silnik na tym urządzeniu.", 12, Ui.TXT, false);
        LinearLayout btns = Ui.row(a);
        btns.addView(TabGen.sqBtn(a, "URUCHOM TESTY", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                String r = runTests(a);
                out.setText(r);
            }
        }));
        btns.addView(TabGen.sqBtn(a, "ZAPISZ RAPORT", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                a.saveFile("raport_testow_android.txt", "text/plain", runTests(a).getBytes());
            }
        }));
        col.addView(Ui.hscroll(a, btns));
        col.addView(out);

        LinearLayout sec = Ui.card(a);
        sec.addView(Ui.section(a, "Bezpieczeństwo danych"));
        sec.addView(Ui.text(a, "• brak konta i logowania\n"
                + "• brak wysyłania projektów do chmury\n"
                + "• import ZIP zabezpieczony przed zip-slip, limit 512 plików / 200 MB\n"
                + "• walidacja SVG/PDF przed wczytaniem geometrii\n"
                + "• pliki projektów w prywatnym katalogu aplikacji", 12, Ui.MUT, false));
        col.addView(sec);
        return Ui.scroll(a, col);
    }

    static String runTests(MainActivity a) {
        StringBuilder sb = new StringBuilder();
        int pass = 0, fail = 0;
        sb.append("RAPORT TESTÓW — WOJAN LaserCraft AI Studio Android ").append(MainActivity.VERSION).append("\n");
        sb.append("Data: ").append(Gen.now()).append("\n");
        sb.append("Urządzenie: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append("\n\n");

        // 1. QR
        String qr = Qr.structuralCheck("https://wojan.pl", 'M');
        boolean ok = qr.startsWith("QR OK");
        sb.append(ok ? "PASS " : "FAIL ").append("T1 QR struktura (https://wojan.pl): ")
                .append(qr.replace("\n", " ")).append("\n");
        if (ok) pass++; else fail++;

        String qr2 = Qr.structuralCheck("Zażółć gęślą jaźń", 'Q');
        boolean ok2 = qr2.startsWith("QR OK");
        sb.append(ok2 ? "PASS " : "FAIL ").append("T2 QR polskie znaki UTF-8: ").append(qr2.replace("\n", " ")).append("\n");
        if (ok2) pass++; else fail++;

        // 2. diakrytyki
        String[] words = {"Zażółć", "gęślą", "jaźń", "ĆWICZ", "ŹDŹBŁO"};
        boolean dia = true;
        for (int i = 0; i < words.length; i++) {
            if (Draw.measureText(a, words[i], "anton", 20) <= 0) dia = false;
        }
        sb.append(dia ? "PASS " : "FAIL ").append("T3 Pomiar tekstu z diakrytykami (5 słów)\n");
        if (dia) pass++; else fail++;

        // 3. generator
        Core.Design d = Gen.fromPrompt(a, "Tabliczka dla wędkarza, szczupak, napis KRÓL ŁOWISKA, 200x150 mm, retro", 0);
        boolean gen = d.objects.size() >= 3;
        sb.append(gen ? "PASS " : "FAIL ").append("T4 Generator projektu: ").append(d.objects.size()).append(" obiektów\n");
        if (gen) pass++; else fail++;

        // 4. walidacja
        Core.Report r = Core.validate(d, new Draw.Provider(a));
        boolean val = r.errors.isEmpty();
        sb.append(val ? "PASS " : "FAIL ").append("T5 Walidacja wygenerowanego projektu: błędy=")
                .append(r.errors.size()).append(", ostrzeżenia=").append(r.warnings.size()).append("\n");
        if (val) pass++; else fail++;
        for (int i = 0; i < r.errors.size() && i < 5; i++) sb.append("       BŁĄD: ").append(r.errors.get(i)).append("\n");

        // 5. SVG
        String svg = Draw.toSvg(a, d, false);
        boolean svgOk = svg.contains("<svg") && svg.contains("mm\"") && svg.contains("<path") && svg.contains("</svg>");
        sb.append(svgOk ? "PASS " : "FAIL ").append("T6 SVG: ").append(svg.length()).append(" B, ścieżek: ")
                .append(count(svg, "<path")).append("\n");
        if (svgOk) pass++; else fail++;

        // 6. DXF
        String dxf = Draw.toDxf(a, d);
        boolean dxfOk = dxf.startsWith("0\nSECTION") && dxf.contains("LWPOLYLINE") && dxf.endsWith("EOF\n");
        sb.append(dxfOk ? "PASS " : "FAIL ").append("T7 DXF: ").append(dxf.length()).append(" B, polilinii: ")
                .append(count(dxf, "LWPOLYLINE")).append("\n");
        if (dxfOk) pass++; else fail++;

        // 7. PDF
        byte[] pdf = Pdf.build(a, d);
        boolean pdfOk = pdf.length > 200 && pdf[0] == '%' && new String(pdf, 0, 5).equals("%PDF-")
                && new String(pdf, Math.max(0, pdf.length - 8), Math.min(8, pdf.length)).contains("EOF");
        sb.append(pdfOk ? "PASS " : "FAIL ").append("T8 PDF: ").append(pdf.length).append(" B\n");
        if (pdfOk) pass++; else fail++;

        // 8. pakiet ZIP
        Store.Bundle b = Store.production(a, d, false);
        boolean zipOk = b.zip != null && b.zip.length > 1000 && b.files.size() >= 9;
        sb.append(zipOk ? "PASS " : "FAIL ").append("T9 Pakiet produkcyjny: ").append(b.files.size())
                .append(" plików, ").append(b.zip == null ? 0 : b.zip.length).append(" B\n");
        if (zipOk) pass++; else fail++;

        // 9. zapis/odczyt projektu
        d.id = "test_" + System.currentTimeMillis();
        boolean saved = Store.save(a, d);
        Core.Design back = Store.load(a, d.id);
        boolean rt = saved && back != null && back.objects.size() == d.objects.size();
        sb.append(rt ? "PASS " : "FAIL ").append("T10 Zapis/odczyt projektu (round-trip JSON)\n");
        if (rt) pass++; else fail++;
        Store.delete(a, d.id);

        // 10. wektoryzacja
        Img.Mask mask = new Img.Mask(40, 40);
        for (int y = 10; y < 30; y++) for (int x = 10; x < 30; x++) {
            mask.px[y * 40 + x] = true;
            mask.dark++;
        }
        android.graphics.Path vp = Img.vectorize(mask, 20, 20, 0.6);
        boolean vec = !vp.isEmpty();
        sb.append(vec ? "PASS " : "FAIL ").append("T11 Wektoryzacja kwadratu 20×20 mm\n");
        if (vec) pass++; else fail++;

        // 11. 24 przykłady
        List<Core.Design> samples = Gen.samples(a);
        int bad = 0;
        for (int i = 0; i < samples.size(); i++) {
            Core.Report rr = Core.validate(samples.get(i), new Draw.Provider(a));
            if (!rr.errors.isEmpty()) bad++;
        }
        sb.append(bad == 0 ? "PASS " : "FAIL ").append("T12 Przykładowe projekty: ").append(samples.size())
                .append(", z błędami: ").append(bad).append("\n");
        if (bad == 0) pass++; else fail++;

        // 12. import/eksport ścieżek
        android.graphics.Path parsed = Draw.parseSvgPath("M 0 0 L 10 0 L 10 10 Z");
        android.graphics.RectF bounds = new android.graphics.RectF();
        parsed.computeBounds(bounds, true);
        boolean pOk = Math.abs(bounds.right - 10) < 0.01 && Math.abs(bounds.bottom - 10) < 0.01;
        sb.append(pOk ? "PASS " : "FAIL ").append("T13 Parser ścieżek SVG (10×10 mm)\n");
        if (pOk) pass++; else fail++;

        // 13. zoom/undo
        int before = a.design.objects.size();
        sb.append("INFO  Historia projektu: ").append(a.historyLabel())
                .append(", obiektów teraz: ").append(before).append("\n");

        sb.append("\nWYNIK: ").append(pass).append(" PASS, ").append(fail).append(" FAIL\n");
        return sb.toString();
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
