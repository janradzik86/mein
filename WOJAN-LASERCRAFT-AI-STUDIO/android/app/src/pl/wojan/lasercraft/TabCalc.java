package pl.wojan.lasercraft;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Zakładka 9 — Kalkulator LaserCraft: koszt materiału, czas, energia, marża. */
public final class TabCalc {

    public static View build(final MainActivity a) {
        final Core.Design d = a.design;
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        Core.MaterialDef mat = Core.material(d.product.material);
        LinearLayout card = Ui.card(a);
        card.addView(Ui.section(a, "Dane do wyceny"));
        card.addView(Ui.text(a, "Materiał: " + mat.name + " • produkt " + Core.fmt(d.product.w) + "×"
                + Core.fmt(d.product.h) + " mm • maszyna: " + d.machine.name, 12, Ui.MUT, false));

        final EditText costM2 = Ui.num(a, "koszt materiału zł/m²", mat.costPerM2);
        final EditText area = Ui.num(a, "powierzchnia mm²", d.product.w * d.product.h);
        final EditText engArea = Ui.num(a, "powierzchnia grawerowania mm²", d.product.w * d.product.h * 0.25);
        final EditText cutLen = Ui.num(a, "długość cięcia mm", 2 * (d.product.w + d.product.h) + 200);
        final EditText power = Ui.num(a, "moc lasera W", 10);
        final EditText energy = Ui.num(a, "cena energii zł/kWh", 0.85);
        final EditText rate = Ui.num(a, "stawka zł/h", 45);
        final EditText setup = Ui.num(a, "przygotowanie min", 6);
        final EditText margin = Ui.num(a, "marża %", 60);
        final EditText waste = Ui.num(a, "odpad %", 12);
        final EditText engSpeed = Ui.num(a, "prędkość grawerowania mm/s", 120);
        final EditText cutSpeed = Ui.num(a, "prędkość cięcia mm/s", 8);

        card.addView(pair(a, "Koszt materiału (zł/m²)", costM2));
        card.addView(pair(a, "Powierzchnia materiału (mm²)", area));
        card.addView(pair(a, "Powierzchnia grawerowania (mm²)", engArea));
        card.addView(pair(a, "Długość cięcia (mm)", cutLen));
        card.addView(pair(a, "Moc lasera (W)", power));
        card.addView(pair(a, "Cena energii (zł/kWh)", energy));
        card.addView(pair(a, "Stawka pracy (zł/h)", rate));
        card.addView(pair(a, "Przygotowanie (min)", setup));
        card.addView(pair(a, "Marża (%)", margin));
        card.addView(pair(a, "Odpad (%)", waste));
        card.addView(pair(a, "Prędkość grawerowania (mm/s)", engSpeed));
        card.addView(pair(a, "Prędkość cięcia (mm/s)", cutSpeed));
        col.addView(card);

        final TextView out = Ui.text(a, "Naciśnij „PRZELICZ”, aby zobaczyć wycenę.", 12, Ui.TXT, false);
        LinearLayout btns = Ui.row(a);
        btns.addView(TabGen.sqBtn(a, "PRZELICZ", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                Core.Cost c = Core.cost(Ui.val(costM2, 45), Ui.val(area, 30000), Ui.val(engArea, 8000),
                        Ui.val(cutLen, 700), Ui.val(power, 10), Ui.val(energy, 0.85), Ui.val(rate, 45),
                        Ui.val(engSpeed, 120), Ui.val(cutSpeed, 8), Ui.val(setup, 6), 1.5, 0,
                        Ui.val(margin, 60), Ui.val(waste, 12));
                String s = "WYCENA (orientacyjna)\n"
                        + "materiał: " + zl(c.materialCost) + "\n"
                        + "grawerowanie: " + Core.fmt(c.engraveMinutes) + " min\n"
                        + "cięcie: " + Core.fmt(c.cutMinutes) + " min\n"
                        + "czas maszyny: " + Core.fmt(c.machineMinutes) + " min\n"
                        + "energia: " + zl(c.energyCost) + "\n"
                        + "praca: " + zl(c.laborCost) + "\n"
                        + "zużycie maszyny: " + zl(c.wearCost) + "\n"
                        + "koszt całkowity: " + zl(c.totalCost) + "\n"
                        + "cena z marżą: " + zl(c.price) + "\n"
                        + "zysk: " + zl(c.profit) + "\n"
                        + "cena brutto (23% VAT): " + zl(c.vatPrice) + "\n\n"
                        + c.note;
                out.setText(s);
            }
        }));
        btns.addView(TabGen.sqBtn(a, "ZAPISZ W PROJEKCIE", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                a.checkpoint();
                Core.Cost c = Core.cost(Ui.val(costM2, 45), Ui.val(area, 30000), Ui.val(engArea, 8000),
                        Ui.val(cutLen, 700), Ui.val(power, 10), Ui.val(energy, 0.85), Ui.val(rate, 45),
                        Ui.val(engSpeed, 120), Ui.val(cutSpeed, 8), Ui.val(setup, 6), 1.5, 0,
                        Ui.val(margin, 60), Ui.val(waste, 12));
                d.pushHistory("Wycena: " + zl(c.price));
                Store.save(a, d);
                Ui.toast(a, "Wycena zapisana w historii projektu: " + zl(c.price));
            }
        }));
        col.addView(Ui.hscroll(a, btns));
        col.addView(out);

        LinearLayout note = Ui.card(a);
        note.addView(Ui.text(a, "Czasy są szacunkiem opartym na podanych parametrach. Rzeczywisty czas zależy "
                + "od maszyny, ogniskowania, materiału i liczby przejść — zweryfikuj na próbce. "
                + "Produkcja wyłącznie pod potwierdzone zamówienie (bez magazynu).", 11, Ui.MUT, false));
        col.addView(note);
        return Ui.scroll(a, col);
    }

    static LinearLayout pair(MainActivity a, String label, EditText e) {
        LinearLayout r = Ui.row(a);
        r.addView(Ui.text(a, label, 12, Ui.MUT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(e, new LinearLayout.LayoutParams(Ui.dp(a, 120), ViewGroup.LayoutParams.WRAP_CONTENT));
        r.setPadding(0, Ui.dp(a, 2), 0, Ui.dp(a, 2));
        return r;
    }

    static String zl(double v) {
        return Core.fmt(v) + " zł";
    }
}
