package pl.wojan.lasercraft;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Zakładka 7 — QR: generator wektorowy z testem strukturalnym (bez bibliotek zewnętrznych). */
public final class TabQr {

    public static View build(final MainActivity a) {
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        LinearLayout card = Ui.card(a);
        card.addView(Ui.section(a, "Kod QR (wektor)"));
        card.addView(Ui.text(a, "Tryb bajtowy UTF-8, wersje 1–10, korekcja L/M/Q/H, "
                + "wzory i maski wg ISO/IEC 18004. Kod jest generowany lokalnie — bez internetu.", 12, Ui.MUT, false));
        final EditText data = Ui.edit(a, "adres, tekst lub numer", "https://wojan.pl", 1);
        card.addView(data);
        List<String> ecc = new ArrayList<String>();
        ecc.add("L (7%)");
        ecc.add("M (15%)");
        ecc.add("Q (25%)");
        ecc.add("H (30%)");
        final Spinner spEcc = Ui.spinner(a, ecc, 1);
        card.addView(Ui.text(a, "Poziom korekcji błędów", 12, Ui.MUT, false));
        card.addView(spEcc);
        final EditText sizeField = Ui.num(a, "rozmiar mm", 40);
        card.addView(Ui.text(a, "Rozmiar kodu na produkcie (mm, z marginesem 4 moduły)", 12, Ui.MUT, false));
        card.addView(sizeField);
        col.addView(card);

        final TextView result = Ui.text(a, "Wpisz dane i naciśnij „GENERUJ I SPRAWDŹ”.", 12, Ui.MUT, false);
        final ImageView preview = new ImageView(a);
        preview.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 240)));
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);

        final Path[] current = new Path[1];
        final String[] currentData = new String[]{""};

        LinearLayout btns = Ui.row(a);
        btns.addView(TabGen.sqBtn(a, "GENERUJ I SPRAWDŹ", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                String txt = data.getText().toString();
                char level = "LMQH".charAt(Math.max(0, spEcc.getSelectedItemPosition()));
                Qr.Code code = Qr.encode(txt, level);
                if (!code.ok) {
                    result.setText("Błąd: " + code.error);
                    result.setTextColor(Ui.ERR);
                    return;
                }
                double mm = Math.max(10, Ui.val(sizeField, 40));
                Path p = Qr.path(txt, mm, level);
                current[0] = p;
                currentData[0] = txt;
                double moduleMm = mm / (code.size + 8.0);
                String chk = Qr.structuralCheck(txt, level);
                result.setText(chk + "moduł: " + Core.fmt(moduleMm) + " mm (minimum czytelności 0,4 mm)\n"
                        + "rozmiar: " + Core.fmt(mm) + "×" + Core.fmt(mm) + " mm\n"
                        + (moduleMm < 0.4 ? "UWAGA: moduł zbyt mały — zwiększ rozmiar!" : "Moduł w normie."));
                result.setTextColor(chk.startsWith("QR OK") ? Ui.ACC2 : Ui.ERR);
                preview.setImageBitmap(render(p, mm, 560));
            }
        }));
        btns.addView(TabGen.sqBtn(a, "DODAJ DO PROJEKTU", Ui.ACC2, new View.OnClickListener() {
            public void onClick(View v) {
                if (current[0] == null) {
                    Ui.toast(a, "Najpierw wygeneruj kod.");
                    return;
                }
                a.checkpoint();
                double mm = Math.max(10, Ui.val(sizeField, 40));
                Core.Obj o = new Core.Obj();
                o.id = a.design.newId("qr");
                o.type = "qr";
                o.layer = Core.L_ENGRAVE_VECTOR;
                o.role = "fill";
                o.name = "Kod QR";
                o.qrData = currentData[0];
                o.qrSize = mm;
                o.x = (a.design.product.w - mm) / 2.0;
                o.y = (a.design.product.h - mm) / 2.0;
                a.design.objects.add(o);
                a.commit("Dodano kod QR");
                Ui.toast(a, "Kod QR dodany do projektu (wektor).");
            }
        }));
        col.addView(Ui.hscroll(a, btns));

        LinearLayout btns2 = Ui.row(a);
        btns2.addView(TabGen.sqBtn(a, "ZAPISZ SVG", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                if (current[0] == null) {
                    Ui.toast(a, "Najpierw wygeneruj kod.");
                    return;
                }
                double mm = Math.max(10, Ui.val(sizeField, 40));
                String svg = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" "
                        + "width=\"" + Core.fmt(mm) + "mm\" height=\"" + Core.fmt(mm) + "mm\" viewBox=\"0 0 "
                        + Core.fmt(mm) + " " + Core.fmt(mm) + "\">\n  <path d=\""
                        + Draw.pathToSvgD(current[0], 0.02f) + "\" fill=\"#000000\"/>\n</svg>\n";
                a.saveFile("qr.svg", "image/svg+xml", svg.getBytes());
            }
        }));
        btns2.addView(TabGen.sqBtn(a, "ZAPISZ PNG", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                if (current[0] == null) {
                    Ui.toast(a, "Najpierw wygeneruj kod.");
                    return;
                }
                double mm = Math.max(10, Ui.val(sizeField, 40));
                a.saveFile("qr.png", "image/png", Store.encodePng(render(current[0], mm, 1024)));
            }
        }));
        btns2.addView(TabGen.sqBtn(a, "TEST POLSKICH ZNAKÓW", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                data.setText("Zażółć gęślą jaźń");
                Ui.toast(a, "Wpisz testowy tekst i sprawdź — kodowanie UTF-8, tryb bajtowy.");
            }
        }));
        col.addView(Ui.hscroll(a, btns2));

        col.addView(result);
        col.addView(preview);

        LinearLayout note = Ui.card(a);
        note.addView(Ui.text(a, "Test w aplikacji sprawdza strukturę kodu (wzory pozycjonujące, "
                + "synchronizację, informację formatu BCH, zgodność maski). Pełny odczyt kodu "
                + "wykonano w testach silnika (dekoder zxing na wyeksportowanym pliku) — wynik opisany "
                + "w RAPORCIE TESTOW.", 11, Ui.MUT, false));
        col.addView(note);
        return Ui.scroll(a, col);
    }

    static Bitmap render(Path p, double mm, int px) {
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(Color.WHITE);
        float k = px / (float) mm;
        cv.save();
        cv.scale(k, k);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        cv.drawPath(p, paint);
        cv.restore();
        return b;
    }
}
