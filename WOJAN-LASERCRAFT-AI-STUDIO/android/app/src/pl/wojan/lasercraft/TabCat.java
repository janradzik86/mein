package pl.wojan.lasercraft;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Zakładka 4 — Kategorie — 50 gotowych kategorii + własna. */
public final class TabCat {

    public static View build(final MainActivity a) {
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        final List<Core.CategoryDef> cats = Core.categories();
        col.addView(Ui.text(a, "Kategoria ustawia motyw, emblemat, styl i dobór napisów. "
                + "Katalog: " + cats.size() + " kategorii + własna.", 12, Ui.MUT, false));

        LinearLayout own = Ui.card(a);
        own.addView(Ui.section(a, "Własna kategoria"));
        final EditText ownName = Ui.edit(a, "np. Koło Łowieckie „Ryś”", "", 1);
        own.addView(ownName);
        own.addView(Ui.button(a, "UTWÓRZ PROJEKT Z WŁASNEJ KATEGORII", new View.OnClickListener() {
            public void onClick(View v) {
                String n = ownName.getText().toString().trim();
                if (n.isEmpty()) {
                    Ui.toast(a, "Podaj nazwę kategorii.");
                    return;
                }
                a.checkpoint();
                Core.Spec sp = Core.analyze(a.design.prompt);
                sp.category = "wlasne";
                sp.raw = n;
                sp.subject = n;
                Gen.applySpec(a, a.design, sp, 1);
                a.design.category = "wlasne";
                a.design.name = n.length() > 26 ? n.substring(0, 26) : n;
                a.commit("Kategoria własna: " + n);
            }
        }));
        col.addView(own);

        LinearLayout grid1 = Ui.row(a);
        LinearLayout grid2 = Ui.row(a);
        for (int i = 0; i < cats.size(); i++) {
            final Core.CategoryDef cd = cats.get(i);
            LinearLayout cell = Ui.col(a);
            cell.setBackground(Ui.round(Ui.CARD, 0xFF2C323B, 8, a, 1));
            int p = Ui.dp(a, 6);
            cell.setPadding(p, p, p, p);
            cell.addView(Ui.text(a, cd.name, 12, Ui.ACC, true));
            cell.addView(Ui.text(a, "motyw: " + cd.emblem, 10, Ui.MUT, false));
            cell.addView(Ui.text(a, cd.blurb, 10, Ui.MUT, false));
            Button b = Ui.buttonAlt(a, "GENERUJ", new View.OnClickListener() {
                public void onClick(View v) {
                    a.checkpoint();
                    Core.Spec sp = Core.analyze(a.design.prompt);
                    sp.category = cd.id;
                    sp.raw = a.design.prompt.isEmpty() ? cd.name : a.design.prompt;
                    Gen.applySpec(a, a.design, sp, cd.id.length() % 4);
                    a.design.category = cd.id;
                    a.design.name = cd.name;
                    a.commit("Kategoria: " + cd.name);
                    Ui.toast(a, "Wygenerowano wzór z kategorii: " + cd.name);
                }
            });
            b.setTextSize(11);
            cell.addView(b);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = Ui.dp(a, 5);
            lp.bottomMargin = Ui.dp(a, 5);
            if (i % 2 == 0) grid1.addView(cell, lp);
            else grid2.addView(cell, lp);
        }
        col.addView(grid1);
        col.addView(grid2);
        return Ui.scroll(a, col);
    }
}
