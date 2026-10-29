package pl.wojan.lasercraft;

import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Zakładka 8 — Moje projekty: biblioteka, ulubione, wersje, kopie, import/eksport. */
public final class TabProj {

    public static View build(final MainActivity a) {
        final LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));

        LinearLayout bar = Ui.row(a);
        bar.addView(TabGen.sqBtn(a, "NOWY PROJEKT", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                Core.Design d = Gen.blank("");
                Core.Spec sp = Core.analyze("tabliczka 150x100 mm, napis MÓJ PROJEKT");
                Gen.applySpec(a, d, sp, 0);
                d.id = Store.newId();
                d.name = "Nowy projekt " + d.id.substring(d.id.length() - 4);
                a.setDesign(d);
                Store.save(a, d);
                a.showTab(4);
            }
        }));
        bar.addView(TabGen.sqBtn(a, "IMPORT (ZIP / PROJEKT)", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                a.pickProject();
            }
        }));
        bar.addView(TabGen.sqBtn(a, "KOPIA ZAPASOWA", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                byte[] zip = backup(a);
                if (zip == null || zip.length == 0) {
                    Ui.toast(a, "Brak projektów do kopii.");
                    return;
                }
                a.saveFile("wojan-lasercraft-backup.zip", "application/zip", zip);
            }
        }));
        bar.addView(TabGen.sqBtn(a, "24 PRZYKŁADY", Ui.ACC2, new View.OnClickListener() {
            public void onClick(View v) {
                final List<Core.Design> samples = Gen.samples(a);
                Ui.confirm(a, "Wygenerować przykłady?", "Zostanie utworzonych " + samples.size()
                        + " projektów demonstracyjnych (generowanych przez silnik aplikacji, "
                        + "nie przez sztuczną inteligencję w chmurze).", new Runnable() {
                    public void run() {
                        int n = 0;
                        for (int i = 0; i < samples.size(); i++) {
                            Core.Design d = samples.get(i);
                            if (Store.fileFor(a, d.id).exists()) continue;
                            if (Store.save(a, d)) n++;
                        }
                        Ui.toast(a, "Utworzono " + n + " przykładowych projektów.");
                        a.refresh();
                    }
                });
            }
        }));
        col.addView(Ui.hscroll(a, bar));

        final List<Store.Meta> list = Store.list(a);
        col.addView(Ui.text(a, "Projektów w bibliotece: " + list.size()
                + ". Zapisywane lokalnie w pamięci aplikacji (bez konta i chmury).", 12, Ui.MUT, false));

        if (list.isEmpty()) {
            LinearLayout empty = Ui.card(a);
            empty.addView(Ui.text(a, "Biblioteka jest pusta. Wygeneruj projekt w zakładce Generator AI "
                    + "albo wczytaj archiwum ZIP.", 13, Ui.TXT, false));
            col.addView(empty);
        }

        for (int i = 0; i < list.size(); i++) {
            final Store.Meta m = list.get(i);
            LinearLayout card = Ui.col(a);
            card.setBackground(Ui.round(Ui.CARD, 0xFF2C323B, 8, a, 1));
            int p = Ui.dp(a, 8);
            card.setPadding(p, p, p, p);
            card.addView(Ui.text(a, (m.favourite ? "★ " : "") + m.name, 14, Ui.TXT, true));
            card.addView(Ui.text(a, m.product + "  •  " + m.category + "  •  " + m.objects
                    + " obiektów  •  " + m.modified, 11, Ui.MUT, false));
            LinearLayout r = Ui.row(a);
            r.addView(TabGen.sqBtn(a, "OTWÓRZ", Ui.ACC, new View.OnClickListener() {
                public void onClick(View v) {
                    a.openProject(m.id);
                }
            }));
            r.addView(TabGen.sqBtn(a, m.favourite ? "★ USUŃ" : "☆ ULUB", Ui.CARD2, new View.OnClickListener() {
                public void onClick(View v) {
                    Store.setFavourite(a, m.id, !m.favourite);
                    a.refresh();
                }
            }));
            r.addView(TabGen.sqBtn(a, "KOPIUJ", Ui.CARD2, new View.OnClickListener() {
                public void onClick(View v) {
                    Core.Design d = Store.load(a, m.id);
                    if (d == null) return;
                    d.id = Store.newId();
                    d.name = d.name + " (kopia)";
                    Store.save(a, d);
                    a.refresh();
                }
            }));
            r.addView(TabGen.sqBtn(a, "WERSJE", Ui.CARD2, new View.OnClickListener() {
                public void onClick(View v) {
                    showVersions(a, m.id);
                }
            }));
            r.addView(TabGen.sqBtn(a, "ZIP", Ui.CARD2, new View.OnClickListener() {
                public void onClick(View v) {
                    Core.Design d = Store.load(a, m.id);
                    if (d == null) return;
                    Store.Bundle b = Store.production(a, d, false);
                    a.saveFile("wojan_" + m.id + ".zip", "application/zip", b.zip);
                }
            }));
            r.addView(TabGen.sqBtn(a, "USUŃ", Ui.CARD2, new View.OnClickListener() {
                public void onClick(View v) {
                    Ui.confirm(a, "Usunąć projekt?", m.name, new Runnable() {
                        public void run() {
                            Store.delete(a, m.id);
                            a.refresh();
                        }
                    });
                }
            }));
            card.addView(Ui.hscroll(a, r));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ui.dp(a, 6);
            col.addView(card, lp);
        }

        LinearLayout t = Ui.card(a);
        t.addView(Ui.section(a, "Przenoszenie telefon ↔ laptop"));
        t.addView(Ui.text(a, "1. Zapisz projekt jako ZIP (przycisk ZIP) i wyślij przez USB, e-mail lub Bluetooth.\n"
                + "2. Na laptopie otwórz ZIP w wersji Windows — format projektu jest wspólny (wlcproj/1).\n"
                + "3. W drugą stronę: „IMPORT (ZIP / PROJEKT)” wczytuje archiwum z Windows.\n"
                + "Transfer przez sieć LAN nie jest w tej wersji zaimplementowany — używamy plików.", 12, Ui.MUT, false));
        col.addView(t);
        return Ui.scroll(a, col);
    }

    static void showVersions(final MainActivity a, final String id) {
        final List<String> files = Store.versionFiles(a, id);
        if (files.isEmpty()) {
            Ui.toast(a, "Brak zapisanych wersji tego projektu.");
            return;
        }
        StringBuilder sb = new StringBuilder("Zapisane wersje (najnowsze pierwsze):\n");
        for (int i = 0; i < Math.min(files.size(), 12); i++) sb.append("• ").append(files.get(i)).append("\n");
        sb.append("\nCzy przywrócić najnowszą wersję?");
        Ui.confirm(a, "Wersje projektu", sb.toString(), new Runnable() {
            public void run() {
                Core.Design d = Store.loadVersion(a, id, files.get(0));
                if (d == null) {
                    Ui.toast(a, "Nie można wczytać wersji.");
                    return;
                }
                a.checkpoint();
                d.id = id;
                a.setDesign(d);
                a.commit("Przywrócono wersję " + files.get(0));
            }
        });
    }

    static byte[] backup(MainActivity a) {
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ZipOutputStream zo = new ZipOutputStream(bo);
            File[] fs = Store.root(a).listFiles();
            if (fs == null) return null;
            int n = 0;
            for (int i = 0; i < fs.length; i++) {
                if (!fs[i].getName().endsWith(Store.EXT)) continue;
                byte[] data = Store.readAll(new java.io.FileInputStream(fs[i]), 32L * 1024 * 1024);
                zo.putNextEntry(new ZipEntry("projekty/" + fs[i].getName()));
                zo.write(data);
                zo.closeEntry();
                n++;
            }
            zo.putNextEntry(new ZipEntry("README.txt"));
            zo.write(("Kopia zapasowa WOJAN LaserCraft AI Studio (Android)\n"
                    + "projektów: " + n + "\nFormat: wlcproj/1 — otwiera się także w wersji Windows.\n")
                    .getBytes("UTF-8"));
            zo.closeEntry();
            zo.close();
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public static void handleImport(final MainActivity a, Uri uri) {
        String name = a.nameOf(uri);
        String low = name.toLowerCase(java.util.Locale.US);
        if (low.endsWith(Store.EXT) || low.endsWith(".json")) {
            try {
                byte[] data = Store.readUri(a, uri, Store.MAX_ENTRY);
                Core.Design d = Core.Design.fromJson(new org.json.JSONObject(new String(data, "UTF-8")));
                d.id = Store.newId();
                Store.save(a, d);
                Ui.toast(a, "Wczytano projekt: " + d.name);
                a.refresh();
            } catch (Exception e) {
                Ui.toast(a, "Błąd wczytywania projektu: " + e.getMessage());
            }
            return;
        }
        Store.ImportResult r = Store.importZip(a, uri);
        if (r.error != null && !r.error.isEmpty()) {
            Ui.alert(a, "Import ZIP", r.error);
            return;
        }
        Store.save(a, r.design);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.notes.size(); i++) sb.append("• ").append(r.notes.get(i)).append("\n");
        Ui.alert(a, "Zaimportowano ZIP", "Plików w archiwum: " + r.files + "\nProjekt: " + r.design.name
                + "\n" + sb.toString());
        a.refresh();
    }
}
