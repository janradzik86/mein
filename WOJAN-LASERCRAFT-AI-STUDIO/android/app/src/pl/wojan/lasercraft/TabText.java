package pl.wojan.lasercraft;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Zakładka 6 — Napisy AI: generator polskich napisów z diakrytykami. */
public final class TabText {

    public static final String[] FONTS = {"anton", "bebas", "cinzel", "cinzel_deco", "playfair",
            "oswald", "abril", "pirata", "barlow"};
    public static final String[] FONT_LABELS = {"Anton (mocny)", "Bebas Neue (wąski)", "Cinzel (klasyczny)",
            "Cinzel Decorative", "Playfair Display", "Oswald", "Abril Fatface", "Pirata One (gotyk)",
            "Barlow Condensed"};

    public static View build(final MainActivity a) {
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        LinearLayout top = Ui.card(a);
        top.addView(Ui.section(a, "Generator napisów"));
        top.addView(Ui.text(a, "Napis górny / dolny — wpisz własny albo wygeneruj propozycje. "
                + "Polskie znaki ą ć ę ł ń ó ś ź ż są obsługiwane w podglądzie, SVG i eksporcie.", 12, Ui.MUT, false));
        final EditText eTop = Ui.edit(a, "napis górny", firstText(a, true), 1);
        final EditText eBot = Ui.edit(a, "napis dolny", firstText(a, false), 1);
        top.addView(eTop);
        top.addView(eBot);
        List<String> fl = new ArrayList<String>();
        int fsel = 0;
        for (int i = 0; i < FONT_LABELS.length; i++) {
            fl.add(FONT_LABELS[i]);
            if (FONTS[i].equals(currentFont(a))) fsel = i;
        }
        final Spinner spFont = Ui.spinner(a, fl, fsel);
        top.addView(Ui.text(a, "Czcionka (wbudowane TTF, bez pobierania)", 12, Ui.MUT, false));
        top.addView(spFont);
        col.addView(top);

        LinearLayout btns = Ui.row(a);
        btns.addView(TabGen.sqBtn(a, "ZASTOSUJ DO PROJEKTU", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                applyText(a, eTop.getText().toString(), eBot.getText().toString(), spFont.getSelectedItemPosition());
            }
        }));
        btns.addView(TabGen.sqBtn(a, "LOSUJ 10 PROPOZYCJI", Ui.ACC2, new View.OnClickListener() {
            public void onClick(View v) {
                a.refresh();
                Ui.toast(a, "Nowe propozycje poniżej.");
            }
        }));
        btns.addView(TabGen.sqBtn(a, "TEST DIAKRYTYKÓW", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                eTop.setText("ZAŻÓŁĆ GĘŚLĄ JAŹŃ");
                eBot.setText("ĆWICZ ĆWICZ ĆWICZ");
                String t = "Zażółć gęślą jaźń — ąćęłńóśźż ĄĆĘŁŃÓŚŹŻ";
                Core.Report r = a.report();
                Ui.alert(a, "Test polskich znaków", "Podgląd: " + t + "\n\n"
                        + "Czcionka: " + FONT_LABELS[spFont.getSelectedItemPosition()] + "\n"
                        + "Projekt: " + a.design.objects.size() + " obiektów, błędy: " + r.errors.size()
                        + "\n\nZnaki są renderowane jako ścieżki czcionki TTF — eksport SVG/DXF nie traci ogonków.");
            }
        }));
        col.addView(Ui.hscroll(a, btns));

        // propozycje
        Core.Spec spec = Core.analyze(a.design.prompt);
        spec.topText = eTop.getText().toString();
        spec.bottomText = eBot.getText().toString();
        List<Core.TextPair> pairs = Core.texts(spec, 10, new Random().nextInt(1000) + a.design.objects.size());
        LinearLayout list = Ui.col(a);
        list.addView(Ui.section(a, "Propozycje napisów (bank lokalny, bez chmury)"));
        for (int i = 0; i < pairs.size(); i++) {
            final Core.TextPair tp = pairs.get(i);
            LinearLayout row = Ui.row(a);
            row.setBackground(Ui.round(Ui.CARD, 0xFF2C323B, 8, a, 1));
            int p = Ui.dp(a, 8);
            row.setPadding(p, p, p, p);
            LinearLayout txt = Ui.col(a);
            txt.addView(Ui.text(a, tp.top, 13, Ui.TXT, true));
            txt.addView(Ui.text(a, tp.bottom, 11, Ui.MUT, false));
            row.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button use = Ui.buttonAlt(a, "UŻYJ", new View.OnClickListener() {
                public void onClick(View v) {
                    eTop.setText(tp.top);
                    eBot.setText(tp.bottom);
                    applyText(a, tp.top, tp.bottom, 0);
                }
            });
            use.setTextSize(11);
            row.addView(use);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(a, 4);
            list.addView(row, lp);
        }
        col.addView(list);
        return Ui.scroll(a, col);
    }

    static String firstText(MainActivity a, boolean top) {
        for (int i = 0; i < a.design.objects.size(); i++) {
            Core.Obj o = a.design.objects.get(i);
            if (!"text".equals(o.type)) continue;
            if (top && o.name.contains("górny")) return o.text;
            if (!top && o.name.contains("dolny")) return o.text;
        }
        return "";
    }

    static String currentFont(MainActivity a) {
        for (int i = 0; i < a.design.objects.size(); i++) {
            Core.Obj o = a.design.objects.get(i);
            if ("text".equals(o.type) && o.font != null && !o.font.isEmpty()) return o.font;
        }
        return "anton";
    }

    static void applyText(MainActivity a, String top, String bot, int fontIdx) {
        String font = FONTS[Math.max(0, Math.min(FONTS.length - 1, fontIdx))];
        a.checkpoint();
        int updated = 0;
        double W = a.design.product.w, H = a.design.product.h;
        double margin = Math.max(3, a.design.product.engraveMargin);
        for (int i = 0; i < a.design.objects.size(); i++) {
            Core.Obj o = a.design.objects.get(i);
            if (!"text".equals(o.type)) continue;
            if (o.name.contains("górny") && !top.isEmpty()) {
                o.text = top;
                o.font = font;
                o.size = fitted(a, top, font, Math.min(H * 0.16, 20), W - 2 * margin - 6);
                o.y = margin + o.size * 0.92;
                updated++;
            } else if (o.name.contains("dolny") && !bot.isEmpty()) {
                o.text = bot;
                o.font = font;
                o.size = fitted(a, bot, font, Math.min(H * 0.12, 15), W - 2 * margin - 8);
                o.y = H - margin - o.size * 0.35;
                updated++;
            }
        }
        if (updated == 0) {
            // brak napisów w projekcie — dodaj nowe obiekty
            if (!top.isEmpty()) a.design.objects.add(newText(a, top, font, W / 2, margin + 14, W - 2 * margin - 6, 16, "Napis górny"));
            if (!bot.isEmpty()) a.design.objects.add(newText(a, bot, font, W / 2, H - margin - 4, W - 2 * margin - 8, 12, "Napis dolny"));
            updated = a.design.objects.size();
        }
        a.commit("Napis: " + (top.isEmpty() ? bot : top));
        Ui.toast(a, "Zaktualizowano napisy (" + updated + ").");
    }

    static double fitted(MainActivity a, String txt, String font, double base, double maxW) {
        double size = base;
        float m = Draw.measureText(a, txt, font, (float) size);
        if (m > maxW && maxW > 2) size = Math.max(1.6, size * maxW / m);
        return size;
    }

    static Core.Obj newText(MainActivity a, String txt, String font, double x, double y,
                            double maxW, double base, String name) {
        Core.Obj o = new Core.Obj();
        o.id = a.design.newId("txt");
        o.type = "text";
        o.layer = Core.L_ENGRAVE_VECTOR;
        o.role = "line";
        o.name = name;
        o.text = txt;
        o.font = font;
        o.align = "center";
        o.tracking = 0.3;
        o.strokeW = 0.25;
        o.size = fitted(a, txt, font, base, maxW);
        o.x = x;
        o.y = y;
        return o;
    }
}
