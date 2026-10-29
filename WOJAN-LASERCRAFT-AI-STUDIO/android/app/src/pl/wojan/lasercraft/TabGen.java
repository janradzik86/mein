package pl.wojan.lasercraft;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Zakładka 1 — Generator AI: prompt, przyciski, selektory, 4 warianty. */
public final class TabGen {

    static final int REQ_CAMERA = 21;

    public static View build(final MainActivity a) {
        final Core.Design d = a.design;
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        TextView hdr = Ui.text(a, "Opisz, co chcesz wypalić — po polsku, własnymi słowami", 13, Ui.MUT, false);
        col.addView(hdr);

        final EditText prompt = Ui.edit(a, "np. Tabliczka dla wędkarza, szczupak, napis KRÓL ŁOWISKA, 200x150 mm, retro",
                d.prompt, 3);
        col.addView(prompt);

        // --- rząd przycisków 1 ---
        LinearLayout r1 = Ui.row(a);
        r1.addView(sqBtn(a, "GENERUJ AI", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                generate(a, prompt.getText().toString(), 0);
            }
        }));
        r1.addView(sqBtn(a, "MIKROFON", Ui.ACC2, new View.OnClickListener() {
            public void onClick(View v) {
                a.showTab(1);
            }
        }));
        r1.addView(sqBtn(a, "DODAJ ZDJĘCIE", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                a.pickImage();
            }
        }));
        col.addView(Ui.hscroll(a, r1));

        // --- rząd przycisków 2 ---
        LinearLayout r2 = Ui.row(a);
        r2.addView(sqBtn(a, "DODAJ PLIK", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                a.pickProject();
            }
        }));
        r2.addView(sqBtn(a, "ZRÓB ZDJĘCIE", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                takePhoto(a);
            }
        }));
        r2.addView(sqBtn(a, "LOSUJ POMYSŁ", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                String[] ideas = Core.ideas();
                String pick = ideas[new Random().nextInt(ideas.length)];
                prompt.setText(pick);
                Ui.toast(a, "Wylosowano pomysł — możesz go zmienić przed generowaniem.");
            }
        }));
        col.addView(Ui.hscroll(a, r2));

        // --- rząd przycisków 3 ---
        LinearLayout r3 = Ui.row(a);
        r3.addView(sqBtn(a, "POPRAW TEKST", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                String fixed = Core.fixSpelling(prompt.getText().toString());
                prompt.setText(fixed);
                Ui.toast(a, "Poprawiono pisownię (bez AI w chmurze — reguły lokalne).");
            }
        }));
        r3.addView(sqBtn(a, "GENERUJ 4 WARIANTY", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                variants(a, prompt.getText().toString(), col);
            }
        }));
        col.addView(Ui.hscroll(a, r3));

        // --- selektory ---
        LinearLayout selCard = Ui.card(a);
        selCard.addView(Ui.section(a, "Parametry (nadpisują opis, jeśli je zmienisz)"));

        final List<Core.CategoryDef> cats = Core.categories();
        List<String> catNames = new ArrayList<String>();
        int catSel = 0;
        for (int i = 0; i < cats.size(); i++) {
            catNames.add(cats.get(i).name);
            if (cats.get(i).id.equals(d.category)) catSel = i;
        }
        catNames.add("Własna kategoria");
        if (d.category == null || d.category.isEmpty() || "wlasne".equals(d.category)) catSel = catNames.size() - 1;
        selCard.addView(Ui.text(a, "Kategoria (" + cats.size() + " + własna)", 12, Ui.MUT, false));
        final Spinner spCat = Ui.spinner(a, catNames, catSel);
        selCard.addView(spCat);

        final List<Core.ProductDef> prods = Core.products();
        List<String> prodNames = new ArrayList<String>();
        int prodSel = 0;
        for (int i = 0; i < prods.size(); i++) {
            prodNames.add(prods.get(i).name + "  (" + Core.fmt(prods.get(i).w) + "×"
                    + Core.fmt(prods.get(i).h) + " mm)");
            if (prods.get(i).id.equals(d.product.id)) prodSel = i;
        }
        selCard.addView(Ui.text(a, "Przedmiot", 12, Ui.MUT, false));
        final Spinner spProd = Ui.spinner(a, prodNames, prodSel);
        selCard.addView(spProd);

        List<String> shapes = new ArrayList<String>();
        shapes.add("Prostokąt zaokrąglony");
        shapes.add("Prostokąt");
        shapes.add("Koło");
        shapes.add("Owal");
        shapes.add("Tarcza");
        shapes.add("Zawieszka (tag)");
        String[] shapeIds = {"rounded", "rect", "circle", "oval", "shield", "tag"};
        int shapeSel = 0;
        for (int i = 0; i < shapeIds.length; i++) if (shapeIds[i].equals(d.product.shape)) shapeSel = i;
        selCard.addView(Ui.text(a, "Kształt", 12, Ui.MUT, false));
        final Spinner spShape = Ui.spinner(a, shapes, shapeSel);
        selCard.addView(spShape);

        LinearLayout dimRow = Ui.row(a);
        final EditText ew = Ui.num(a, "szer. mm", d.product.w);
        final EditText eh = Ui.num(a, "wys. mm", d.product.h);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        dimRow.addView(ew, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = Ui.dp(a, 6);
        dimRow.addView(eh, half2);
        selCard.addView(Ui.text(a, "Wymiary (mm)", 12, Ui.MUT, false));
        selCard.addView(dimRow);

        final List<Core.MaterialDef> mats = Core.materials();
        List<String> matNames = new ArrayList<String>();
        int matSel = 0;
        for (int i = 0; i < mats.size(); i++) {
            matNames.add((mats.get(i).safe ? "" : "⚠ ") + mats.get(i).name);
            if (mats.get(i).id.equals(d.product.material)) matSel = i;
        }
        selCard.addView(Ui.text(a, "Materiał", 12, Ui.MUT, false));
        final Spinner spMat = Ui.spinner(a, matNames, matSel);
        selCard.addView(spMat);
        final TextView matWarn = Ui.text(a, "", 11, Ui.WARN, false);
        selCard.addView(matWarn);
        updateWarn(a, mats, matSel, matWarn);
        spMat.setOnItemSelectedListener(Ui.onSelect(new Ui.OnText() {
            public void on(String s) {
                updateWarn(a, mats, Integer.parseInt(s), matWarn);
            }
        }));

        List<String> styles = new ArrayList<String>();
        int styleSel = 0;
        for (int i = 0; i < Gen.STYLES.length; i++) {
            styles.add(Gen.STYLE_LABELS[i]);
            if (Gen.STYLES[i].equals(d.style)) styleSel = i;
        }
        selCard.addView(Ui.text(a, "Styl", 12, Ui.MUT, false));
        final Spinner spStyle = Ui.spinner(a, styles, styleSel);
        selCard.addView(spStyle);

        List<String> details = new ArrayList<String>();
        int detSel = 1;
        for (int i = 0; i < Gen.DETAILS.length; i++) {
            details.add(Gen.DETAIL_LABELS[i]);
            if (Gen.DETAILS[i].equals(d.detail)) detSel = i;
        }
        selCard.addView(Ui.text(a, "Poziom szczegółowości", 12, Ui.MUT, false));
        final Spinner spDet = Ui.spinner(a, details, detSel);
        selCard.addView(spDet);

        List<String> eng = new ArrayList<String>();
        int engSel = 1;
        for (int i = 0; i < Gen.ENGTYPES.length; i++) {
            eng.add(Gen.ENGTYPES[i]);
            if (Gen.ENGTYPES[i].equals(d.engraveType)) engSel = i;
        }
        selCard.addView(Ui.text(a, "Typ grawerowania", 12, Ui.MUT, false));
        final Spinner spEng = Ui.spinner(a, eng, engSel);
        selCard.addView(spEng);

        final CheckBox cut = Ui.check(a, "Projekt wymaga cięcia konturu (wycięcie z arkusza)", d.product.requiresCut, null);
        selCard.addView(cut);
        col.addView(selCard);

        // --- podgląd ---
        EditorView ev = new EditorView(a, d, a.provider);
        LinearLayout.LayoutParams evp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(a, 260));
        ev.setLayoutParams(evp);
        col.addView(ev);

        LinearLayout tools = Ui.row(a);
        tools.addView(sqBtn(a, "DOPASUJ", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                ev.post(new Runnable() {
                    public void run() {
                        // fit() jest prywatny — dwukrotne dotknięcie robi to samo, więc odświeżamy widok
                    }
                });
                a.refresh();
            }
        }));
        tools.addView(sqBtn(a, "SPRAWDŹ PRZED LASEREM", Ui.ACC2, new View.OnClickListener() {
            public void onClick(View v) {
                Ui.alert(a, "Kontrola przed laserem", a.report().toText(a.design));
            }
        }));
        col.addView(Ui.hscroll(a, tools));

        ScrollView sc = Ui.scroll(a, col);
        return sc;
    }

    static Button sqBtn(final MainActivity a, String label, int color, View.OnClickListener l) {
        Button b = Ui.button(a, label, l);
        b.setBackground(Ui.round(color, 0xFF3A424D, 8, a, 1));
        b.setTextColor(color == Ui.CARD2 ? Ui.TXT : 0xFF101214);
        b.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(a, 40));
        lp.rightMargin = Ui.dp(a, 5);
        lp.topMargin = Ui.dp(a, 4);
        b.setLayoutParams(lp);
        return b;
    }

    static void updateWarn(MainActivity a, List<Core.MaterialDef> mats, int idx, TextView tv) {
        if (idx < 0 || idx >= mats.size()) return;
        Core.MaterialDef m = mats.get(idx);
        if (!m.safe) {
            tv.setText("⚠ " + m.warning);
            tv.setTextColor(Ui.ERR);
        } else if (!m.warning.isEmpty()) {
            tv.setText("• " + m.warning);
            tv.setTextColor(Ui.WARN);
        } else {
            tv.setText("Materiał bezpieczny przy prawidłowej wentylacji.");
            tv.setTextColor(Ui.MUT);
        }
    }

    public static Core.Spec specFromUi(Core.Design d, String prompt, Spinner spCat, Spinner spProd,
                                       Spinner spShape, EditText ew, EditText eh, Spinner spMat,
                                       Spinner spStyle, Spinner spDet, Spinner spEng, CheckBox cut) {
        Core.Spec sp = Core.analyze(prompt);
        if (spCat != null) {
            int i = spCat.getSelectedItemPosition();
            List<Core.CategoryDef> cats = Core.categories();
            sp.category = i >= 0 && i < cats.size() ? cats.get(i).id : "wlasne";
        }
        if (spProd != null) sp.productId = Core.products().get(Math.max(0, spProd.getSelectedItemPosition())).id;
        if (spMat != null) sp.material = Core.materials().get(Math.max(0, spMat.getSelectedItemPosition())).id;
        if (spStyle != null) sp.style = Gen.STYLES[Math.max(0, spStyle.getSelectedItemPosition())];
        if (spDet != null) sp.detail = Gen.DETAILS[Math.max(0, spDet.getSelectedItemPosition())];
        if (spEng != null) sp.engravetype = Gen.ENGTYPES[Math.max(0, spEng.getSelectedItemPosition())];
        if (cut != null) sp.requiresCut = cut.isChecked();
        double w = Ui.val(ew, d.product.w);
        double h = Ui.val(eh, d.product.h);
        if (w > 5 && h > 5) {
            sp.width = w;
            sp.height = h;
            sp.dimsGiven = true;
        }
        sp.cutGiven = true;
        if (spShape != null) {
            String[] shapeIds = {"rounded", "rect", "circle", "oval", "shield", "tag"};
            int i = Math.max(0, Math.min(shapeIds.length - 1, spShape.getSelectedItemPosition()));
            sp.shape = shapeIds[i];
        }
        return sp;
    }

    static void generate(MainActivity a, String prompt, int variant) {
        if (prompt == null || prompt.trim().isEmpty()) {
            Ui.toast(a, "Wpisz najpierw opis projektu.");
            return;
        }
        a.checkpoint();
        Core.Spec sp = Core.analyze(prompt);
        a.design.prompt = prompt;
        Gen.applySpec(a, a.design, sp, variant);
        String name = sp.topText == null || sp.topText.isEmpty() ? "Projekt AI" : sp.topText;
        a.design.name = name.length() > 28 ? name.substring(0, 28) : name;
        a.commit("GENERUJ AI (wariant " + (variant + 1) + ")");
        Core.Report r = a.report();
        String msg = "Wygenerowano: " + a.design.objects.size() + " obiektów, "
                + r.errors.size() + " błędów, " + r.warnings.size() + " ostrzeżeń.";
        Ui.toast(a, msg);
    }

    static void variants(final MainActivity a, String prompt, LinearLayout col) {
        if (prompt == null || prompt.trim().isEmpty()) {
            Ui.toast(a, "Wpisz najpierw opis projektu.");
            return;
        }
        Core.Report r0;
        List<Core.Design> list = new ArrayList<Core.Design>();
        for (int i = 0; i < 4; i++) {
            Core.Design dd = Gen.fromPrompt(a, prompt, i);
            dd.id = a.design.id;
            list.add(dd);
        }
        LinearLayout box = Ui.card(a);
        box.addView(Ui.section(a, "4 warianty — dotknij, aby wybrać"));
        LinearLayout grid = Ui.row(a);
        for (int i = 0; i < list.size(); i++) {
            final Core.Design cand = list.get(i);
            final int idx = i;
            LinearLayout cell = Ui.col(a);
            ImageView iv = new ImageView(a);
            int px = Ui.dp(a, 110);
            iv.setLayoutParams(new LinearLayout.LayoutParams(px, px));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageBitmap(Draw.render(a, cand, px / (float) Math.max(cand.product.w, cand.product.h), false));
            cell.addView(iv);
            TextView tv = Ui.text(a, "Wariant " + (i + 1), 11, Ui.TXT, false);
            cell.addView(tv);
            Button pick = Ui.button(a, "WYBIERZ", new View.OnClickListener() {
                public void onClick(View v) {
                    a.checkpoint();
                    Core.Spec sp = Core.analyze(cand.prompt);
                    Gen.applySpec(a, a.design, sp, idx);
                    a.design.name = cand.name;
                    a.commit("Wybrano wariant " + (idx + 1));
                }
            });
            pick.setTextSize(10);
            cell.addView(pick);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(px, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = Ui.dp(a, 6);
            grid.addView(cell, clp);
        }
        box.addView(Ui.hscroll(a, grid));
        String info = "";
        for (int i = 0; i < list.size(); i++) {
            Core.Report rr = Core.validate(list.get(i), a.provider);
            info += "Wariant " + (i + 1) + ": " + list.get(i).objects.size() + " obiektów, błędów: "
                    + rr.errors.size() + ", ostrzeżeń: " + rr.warnings.size() + "\n";
        }
        box.addView(Ui.text(a, info, 11, Ui.MUT, false));
        col.addView(box);
        a.refreshKeepScroll();
        Ui.toast(a, "Gotowe — 4 warianty policzone lokalnie.");
    }

    static void takePhoto(final MainActivity a) {
        try {
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            final Uri target = a.getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new android.content.ContentValues());
            a.cameraTarget = target;
            i.putExtra(MediaStore.EXTRA_OUTPUT, target);
            a.startActivityForResult(i, REQ_CAMERA);
            Ui.toast(a, "Po zrobieniu zdjęcia wróć do aplikacji — zdjęcie wczyta się do importu.");
        } catch (Exception e) {
            Ui.toast(a, "Aparat niedostępny: " + e.getMessage());
        }
    }
}
