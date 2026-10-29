package pl.wojan.lasercraft;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Zakładka 10 — Eksport: profile LightBurn i LaserGRBL, pakiet produkcyjny. */
public final class TabExport {

    public static View build(final MainActivity a) {
        final Core.Design d = a.design;
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        LinearLayout prof = Ui.card(a);
        prof.addView(Ui.section(a, "Profil eksportu"));
        List<String> profiles = new ArrayList<String>();
        profiles.add("LightBurn — SVG w mm, warstwy jako grupy");
        profiles.add("LaserGRBL — PNG 1:1 (254 dpi) + G-code (tylko GRBL)");
        final Spinner sp = Ui.spinner(a, profiles, 0);
        prof.addView(sp);
        prof.addView(Ui.text(a, "LightBurn i LaserGRBL interpretują pliki inaczej — dlatego są osobne profile. "
                + "Nie zakładamy, że ten sam SVG da ten sam efekt na obu programach.\n"
                + "Maszyna: " + d.machine.name + " (kontroler " + d.machine.controller + "), kerf "
                + Core.fmt(d.machine.kerf) + " mm, LPI " + d.machine.lpi + ".", 12, Ui.MUT, false));
        col.addView(prof);

        Core.Report rep = a.report();
        final TextView status = Ui.text(a, "Walidacja: " + (rep.errors.isEmpty() ? "OK" : rep.errors.size()
                + " błędów") + ", ostrzeżeń: " + rep.warnings.size(), 12,
                rep.errors.isEmpty() ? Ui.ACC2 : Ui.ERR, false);
        col.addView(status);

        LinearLayout row1 = Ui.row(a);
        row1.addView(TabGen.sqBtn(a, "SVG (wektor)", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                String svg = Draw.toSvg(a, d, false);
                a.saveFile(fileBase(d) + ".svg", "image/svg+xml", svg.getBytes());
            }
        }));
        row1.addView(TabGen.sqBtn(a, "DXF (mm)", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                String dxf = Draw.toDxf(a, d);
                a.saveFile(fileBase(d) + ".dxf", "application/dxf", dxf.getBytes());
            }
        }));
        row1.addView(TabGen.sqBtn(a, "PDF", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                a.saveFile(fileBase(d) + ".pdf", "application/pdf", Pdf.build(a, d));
            }
        }));
        col.addView(Ui.hscroll(a, row1));

        LinearLayout row2 = Ui.row(a);
        row2.addView(TabGen.sqBtn(a, "PNG", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                android.graphics.Bitmap b = Draw.render(a, d, 254f / 25.4f, false);
                a.saveFile(fileBase(d) + ".png", "image/png", Store.encodePng(b));
            }
        }));
        row2.addView(TabGen.sqBtn(a, "JPG (podgląd)", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                android.graphics.Bitmap b = Draw.render(a, d, 3f, true);
                a.saveFile(fileBase(d) + "_podglad.jpg", "image/jpeg", Store.encodeJpg(b));
            }
        }));
        row2.addView(TabGen.sqBtn(a, "G-CODE (GRBL)", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                String[] err = new String[1];
                String g = Draw.toGcode(a, d, err);
                if (g == null) {
                    Ui.alert(a, "G-code wyłączony", err[0]);
                    return;
                }
                Ui.confirm(a, "Eksport G-code", "Plik G-code jest przeznaczony TYLKO dla kontrolera GRBL. "
                        + "Przed uruchomieniem sprawdź pozycję zerową, moc i materiał na próbce.\n"
                        + "Kontynuować?", new Runnable() {
                    public void run() {
                        a.saveFile(fileBase(d) + ".gcode", "text/plain", Draw.toGcode(a, d, new String[1]).getBytes());
                    }
                });
            }
        }));
        col.addView(Ui.hscroll(a, row2));

        LinearLayout pack = Ui.card(a);
        pack.addView(Ui.section(a, "Pakiet produkcyjny ZIP"));
        pack.addView(Ui.text(a, "Zawartość: projekt_edytowalny (wlcproj + json), lightburn.svg, "
                + "lasergrbl.png, cut_outer.svg, cut_inner.svg, engrave_vector.svg, engrave_fill.svg, "
                + "engrave_raster.png, preview.jpg, parametry.json, raport_walidacji.txt, UWAGI.txt.", 11, Ui.MUT, false));
        pack.addView(Ui.button(a, "ZAPISZ PAKIET ZIP", new View.OnClickListener() {
            public void onClick(View v) {
                Store.Bundle b = Store.production(a, d, false);
                if (b.zip == null || b.zip.length == 0) {
                    Ui.toast(a, "Nie udało się zbudować pakietu.");
                    return;
                }
                a.saveFile("wojan_" + fileBase(d) + "_pakiet.zip", "application/zip", b.zip);
            }
        }));
        col.addView(pack);

        LinearLayout repCard = Ui.card(a);
        repCard.addView(Ui.section(a, "Raport walidacji"));
        final TextView repText = Ui.text(a, rep.toText(d), 11, Ui.MUT, false);
        repCard.addView(repText);
        repCard.addView(Ui.buttonAlt(a, "ZAPISZ RAPORT TXT", new View.OnClickListener() {
            public void onClick(View v) {
                a.saveFile("raport_walidacji.txt", "text/plain", a.report().toText(d).getBytes());
            }
        }));
        col.addView(repCard);

        LinearLayout warn = Ui.card(a);
        warn.addView(Ui.text(a, "Sterowanie laserem bezpośrednio z aplikacji nie jest dostępne w wersji 1. "
                + "Program przygotowuje pliki produkcyjne; uruchomienie zadania odbywa się w LightBurn, "
                + "LaserGRBL lub na panelu maszyny.", 11, Ui.WARN, false));
        col.addView(warn);
        return Ui.scroll(a, col);
    }

    static String fileBase(Core.Design d) {
        String n = d.name == null ? "projekt" : d.name.toLowerCase(java.util.Locale.US);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isLetterOrDigit(c)) b.append(c);
            else if (b.length() > 0 && b.charAt(b.length() - 1) != '_') b.append('_');
        }
        if (b.length() == 0) b.append("projekt");
        return b.toString();
    }
}
