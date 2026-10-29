package pl.wojan.lasercraft;

import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Zakładka 12 — Ustawienia: maszyna, kerf, katalog produktów, licencje, informacje. */
public final class TabSet {

    public static View build(final MainActivity a) {
        final Core.Design d = a.design;
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        LinearLayout machine = Ui.card(a);
        machine.addView(Ui.section(a, "Maszyna i kerf"));
        List<Core.MachineDef> machines = Core.machines();
        List<String> names = new ArrayList<String>();
        int sel = 0;
        for (int i = 0; i < machines.size(); i++) {
            names.add(machines.get(i).name + " — " + machines.get(i).controller);
            if (machines.get(i).id.equals(d.machine.id)) sel = i;
        }
        final Spinner sp = Ui.spinner(a, names, sel);
        machine.addView(sp);
        final EditText kerf = Ui.num(a, "kerf mm", d.machine.kerf);
        machine.addView(Ui.text(a, "Kerf (szerokość szczeliny cięcia) — zmierz na własnej maszynie i materiale. "
                + "Aplikacja nie zgaduje tej wartości.", 11, Ui.MUT, false));
        machine.addView(kerf);
        final EditText lpi = Ui.num(a, "LPI (linie na cal)", d.machine.lpi);
        machine.addView(Ui.text(a, "LPI dla fotograwerowania", 11, Ui.MUT, false));
        machine.addView(lpi);
        machine.addView(Ui.button(a, "ZAPISZ USTAWIENIA MASZYNY", new View.OnClickListener() {
            public void onClick(View v) {
                int i = Math.max(0, Math.min(machines.size() - 1, sp.getSelectedItemPosition()));
                Core.MachineDef md = machines.get(i);
                a.checkpoint();
                d.machine.id = md.id;
                d.machine.name = md.name;
                d.machine.controller = md.controller;
                d.machine.kerf = Math.max(0.01, Ui.val(kerf, md.kerf));
                d.machine.lpi = (int) Math.max(30, Ui.val(lpi, md.lpi));
                d.machine.fieldW = md.fieldW;
                d.machine.fieldH = md.fieldH;
                a.commit("Ustawienia maszyny: " + md.name);
                Ui.toast(a, "Zapisano profil maszyny.");
            }
        }));
        col.addView(machine);

        LinearLayout behav = Ui.card(a);
        behav.addView(Ui.section(a, "Zachowanie programu"));
        final CheckBox auto = Ui.check(a, "Autozapis po każdej zmianie", a.autoSave, null);
        behav.addView(auto);
        behav.addView(Ui.buttonAlt(a, "ZASTOSUJ", new View.OnClickListener() {
            public void onClick(View v) {
                a.autoSave = auto.isChecked();
                Ui.toast(a, "Autozapis: " + (a.autoSave ? "włączony" : "wyłączony"));
            }
        }));
        behav.addView(Ui.hline(a));
        behav.addView(Ui.text(a, "Dane projektu: " + d.name + " (" + d.objects.size() + " obiektów)\n"
                + "Format zapisu: wlcproj/1 (JSON, wspólny z wersją Windows)\n"
                + "Katalog: " + Store.root(a).getAbsolutePath(), 11, Ui.MUT, false));
        col.addView(behav);

        LinearLayout prod = Ui.card(a);
        prod.addView(Ui.section(a, "Katalog produktów — zasady"));
        StringBuilder sb = new StringBuilder();
        List<Core.ProductDef> ps = Core.products();
        for (int i = 0; i < ps.size(); i++) {
            Core.ProductDef p = ps.get(i);
            sb.append("• ").append(p.name).append(": ").append(Core.fmt(p.w)).append("×").append(Core.fmt(p.h))
                    .append(" mm, margines ").append(Core.fmt(p.margin)).append(" mm, ")
                    .append(p.cut ? "wycinany" : "tylko grawer").append(p.curved ? ", powierzchnia zakrzywiona" : "")
                    .append("\n");
        }
        prod.addView(Ui.text(a, sb.toString(), 11, Ui.MUT, false));
        col.addView(prod);

        LinearLayout safe = Ui.card(a);
        safe.addView(Ui.section(a, "Bezpieczeństwo materiałów"));
        String[] notes = Core.safetyNotes();
        StringBuilder s2 = new StringBuilder();
        for (int i = 0; i < notes.length; i++) s2.append("⚠ ").append(notes[i]).append("\n");
        safe.addView(Ui.text(a, s2.toString(), 11, Ui.WARN, false));
        col.addView(safe);

        LinearLayout lic = Ui.card(a);
        lic.addView(Ui.section(a, "Licencje i źródła"));
        lic.addView(Ui.text(a, "Czcionki: Anton, Bebas Neue, Cinzel, Cinzel Decorative, Playfair Display, "
                + "Oswald, Abril Fatface, Pirata One, Barlow Condensed — SIL Open Font License 1.1 "
                + "(wbudowane w APK, używane do ścieżek tekstu).\n"
                + "Kod QR: implementacja własna wg ISO/IEC 18004 (bez bibliotek zewnętrznych).\n"
                + "Wektoryzacja: algorytm konturowy własny (bez potrace).\n"
                + "Aplikacja nie zawiera modeli neuronowych w tej wersji — patrz Diagnostyka AI.\n"
                + "Brak zależności sieciowych, brak analityki, brak reklam.", 11, Ui.MUT, false));
        col.addView(lic);

        LinearLayout about = Ui.card(a);
        about.addView(Ui.text(a, "WOJAN LaserCraft AI Studio — Android " + MainActivity.VERSION
                + "\nStudio projektowania wzorów do grawerowania i cięcia laserem."
                + "\nProjekt i wykonanie: Wojan LaserCraft.", 12, Ui.TXT, true));
        col.addView(about);
        return Ui.scroll(a, col);
    }
}
