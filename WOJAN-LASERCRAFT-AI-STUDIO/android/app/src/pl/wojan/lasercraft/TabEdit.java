package pl.wojan.lasercraft;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Zakładka 5 — Edytor: warstwy, geometria, teksty, operacje, historia. */
public final class TabEdit {

    public static View build(final MainActivity a) {
        final Core.Design d = a.design;
        LinearLayout col = Ui.col(a);
        col.setPadding(Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));

        final EditorView ev = new EditorView(a, d, a.provider);
        ev.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 250)));
        col.addView(ev);

        final TextView selInfo = Ui.text(a, "Zaznacz obiekt na podglądzie lub z listy.", 11, Ui.MUT, false);
        ev.setListener(new EditorView.Listener() {
            public void onChanged(String label) {
                a.checkpoint();
                a.commit(label);
            }

            public void onSelected(Core.Obj o) {
                selInfo.setText(o == null ? "Brak zaznaczenia." :
                        "Zaznaczono: " + o.name + " [" + o.layer + "]  x=" + Core.fmt(o.x) + " y=" + Core.fmt(o.y));
            }
        });
        col.addView(selInfo);

        LinearLayout tools = Ui.row(a);
        tools.addView(TabGen.sqBtn(a, "COFNIJ", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                a.undo();
            }
        }));
        tools.addView(TabGen.sqBtn(a, "PONÓW", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                a.redo();
            }
        }));
        tools.addView(TabGen.sqBtn(a, "ZAPISZ", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                a.saveProject();
            }
        }));
        tools.addView(TabGen.sqBtn(a, "SPRAWDŹ", Ui.ACC2, new View.OnClickListener() {
            public void onClick(View v) {
                Ui.alert(a, "Kontrola przed laserem", a.report().toText(a.design));
            }
        }));
        col.addView(Ui.hscroll(a, tools));

        LinearLayout views = Ui.row(a);
        final android.widget.CheckBox cbGrid = Ui.check(a, "Siatka mm", true, new View.OnClickListener() {
            public void onClick(View v) {
                ev.setGrid(((android.widget.CheckBox) v).isChecked());
            }
        });
        final android.widget.CheckBox cbSnap = Ui.check(a, "Przyciąganie", true, new View.OnClickListener() {
            public void onClick(View v) {
                ev.setSnap(((android.widget.CheckBox) v).isChecked());
            }
        });
        final android.widget.CheckBox cbGuide = Ui.check(a, "Prowadnice", false, new View.OnClickListener() {
            public void onClick(View v) {
                ev.setGuides(((android.widget.CheckBox) v).isChecked());
            }
        });
        views.addView(cbGrid);
        views.addView(cbSnap);
        views.addView(cbGuide);
        col.addView(Ui.hscroll(a, views));

        // lista obiektów
        LinearLayout objRow = Ui.row(a);
        objRow.addView(Ui.text(a, "Obiekty:", 12, Ui.MUT, false));
        for (int i = 0; i < d.objects.size(); i++) {
            final int idx = i;
            Core.Obj o = d.objects.get(i);
            final String label = (o.visible ? "" : "○ ") + o.name;
            objRow.addView(TabGen.sqBtn(a, label, i % 2 == 0 ? Ui.CARD2 : Ui.CARD2, new View.OnClickListener() {
                public void onClick(View v) {
                    ev.selectIndex(idx);
                    Ui.toast(a, label);
                }
            }));
        }
        col.addView(Ui.hscroll(a, objRow));

        // właściwości
        LinearLayout props = Ui.card(a);
        props.addView(Ui.section(a, "Właściwości zaznaczonego obiektu"));

        List<String> layerNames = new ArrayList<String>();
        for (int i = 0; i < Core.LAYERS.length; i++) layerNames.add(Core.layerLabel(Core.LAYERS[i]));
        final Spinner spLayer = Ui.spinner(a, layerNames, 0);
        props.addView(Ui.text(a, "Warstwa produkcyjna", 11, Ui.MUT, false));
        props.addView(spLayer);

        LinearLayout geo = Ui.row(a);
        final EditText ex = Ui.num(a, "x mm", 0);
        final EditText ey = Ui.num(a, "y mm", 0);
        final EditText erot = Ui.num(a, "rot °", 0);
        final EditText escale = Ui.num(a, "skala", 1);
        geo.addView(ex, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        geo.addView(ey, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        geo.addView(erot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        geo.addView(escale, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        props.addView(geo);

        final EditText eText = Ui.edit(a, "treść napisu", "", 1);
        final EditText eSize = Ui.num(a, "wys. mm", 10);
        final EditText eTrack = Ui.num(a, "odstęp", 0.3);
        List<String> fonts = new ArrayList<String>();
        for (int i = 0; i < TabText.FONT_LABELS.length; i++) fonts.add(TabText.FONT_LABELS[i]);
        final Spinner spFont = Ui.spinner(a, fonts, 0);
        props.addView(Ui.text(a, "Napis (tylko obiekty tekstowe)", 11, Ui.MUT, false));
        props.addView(eText);
        props.addView(spFont);
        LinearLayout trow = Ui.row(a);
        trow.addView(eSize, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        trow.addView(eTrack, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        props.addView(trow);

        final LinearLayout holder = props;
        props.addView(Ui.button(a, "ZASTOSUJ ZMIANY", new View.OnClickListener() {
            public void onClick(View v) {
                Core.Obj o = ev.selected();
                if (o == null) {
                    Ui.toast(a, "Najpierw zaznacz obiekt.");
                    return;
                }
                a.checkpoint();
                o.x = Ui.val(ex, o.x);
                o.y = Ui.val(ey, o.y);
                o.rot = Ui.val(erot, o.rot);
                double sc = Ui.val(escale, 1);
                if (sc > 0.01) {
                    o.sx = sc;
                    o.sy = sc;
                }
                int li = spLayer.getSelectedItemPosition();
                if (li >= 0 && li < Core.LAYERS.length) o.layer = Core.LAYERS[li];
                if ("text".equals(o.type)) {
                    if (!eText.getText().toString().isEmpty()) o.text = eText.getText().toString();
                    o.size = Math.max(0.8, Ui.val(eSize, o.size));
                    o.tracking = Ui.val(eTrack, o.tracking);
                    o.font = TabText.FONTS[Math.max(0, spFont.getSelectedItemPosition())];
                }
                if (!d.product.requiresCut && Core.isCut(o.layer)) {
                    Ui.alert(a, "Uwaga", "Produkt nie jest wycinany (np. piersiówka, kubek). "
                            + "Warstwa cięcia została ustawiona ręcznie — sprawdź, czy to celowe.");
                }
                a.commit("Edycja obiektu: " + o.name);
            }
        }));
        col.addView(props);

        // operacje
        LinearLayout ops = Ui.row(a);
        ops.addView(TabGen.sqBtn(a, "DUPLIKUJ", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                Core.Obj o = ev.selected();
                if (o == null) {
                    Ui.toast(a, "Zaznacz obiekt.");
                    return;
                }
                a.checkpoint();
                Core.Obj copy = Core.Obj.fromJson(o.toJson());
                copy.id = d.newId("copy");
                copy.name = o.name + " (kopia)";
                copy.x += 5;
                copy.y += 5;
                d.objects.add(copy);
                a.commit("Duplikat: " + o.name);
            }
        }));
        ops.addView(TabGen.sqBtn(a, "USUŃ", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                final Core.Obj o = ev.selected();
                if (o == null) return;
                Ui.confirm(a, "Usunąć obiekt?", o.name, new Runnable() {
                    public void run() {
                        a.checkpoint();
                        d.objects.remove(o);
                        a.commit("Usunięto: " + o.name);
                    }
                });
            }
        }));
        ops.addView(TabGen.sqBtn(a, "ZABLOKUJ", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                Core.Obj o = ev.selected();
                if (o == null) {
                    Ui.toast(a, "Zaznacz obiekt.");
                    return;
                }
                a.checkpoint();
                o.locked = !o.locked;
                a.commit((o.locked ? "Zablokowano: " : "Odblokowano: ") + o.name);
            }
        }));
        ops.addView(TabGen.sqBtn(a, "UKRYJ/POKAŻ", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                Core.Obj o = ev.selected();
                if (o == null) return;
                a.checkpoint();
                o.visible = !o.visible;
                a.commit((o.visible ? "Pokazano: " : "Ukryto: ") + o.name);
            }
        }));
        col.addView(Ui.hscroll(a, ops));

        LinearLayout ops2 = Ui.row(a);
        ops2.addView(TabGen.sqBtn(a, "W GÓRĘ", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                move(a, ev, true);
            }
        }));
        ops2.addView(TabGen.sqBtn(a, "W DÓŁ", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                move(a, ev, false);
            }
        }));
        ops2.addView(TabGen.sqBtn(a, "WYŚRODKUJ H", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                Core.Obj o = ev.selected();
                if (o == null) return;
                a.checkpoint();
                android.graphics.RectF b = ev.boundsOf(o);
                if (b != null) o.x += (d.product.w - (b.left + b.right)) / 2.0;
                a.commit("Wyśrodkowano poziomo");
            }
        }));
        ops2.addView(TabGen.sqBtn(a, "WYŚRODKUJ V", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                Core.Obj o = ev.selected();
                if (o == null) return;
                a.checkpoint();
                android.graphics.RectF b = ev.boundsOf(o);
                if (b != null) o.y += (d.product.h - (b.top + b.bottom)) / 2.0;
                a.commit("Wyśrodkowano pionowo");
            }
        }));
        ops2.addView(TabGen.sqBtn(a, "OBRÓĆ 90°", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                Core.Obj o = ev.selected();
                if (o == null) return;
                a.checkpoint();
                o.rot = (o.rot + 90) % 360;
                a.commit("Obrót 90°");
            }
        }));
        ops2.addView(TabGen.sqBtn(a, "ODBIJ LUSTRZANIE", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                Core.Obj o = ev.selected();
                if (o == null) return;
                a.checkpoint();
                o.sx = -o.sx;
                a.commit("Odbicie lustrzane");
            }
        }));
        col.addView(Ui.hscroll(a, ops2));

        // dodawanie
        LinearLayout add = Ui.row(a);
        add.addView(TabGen.sqBtn(a, "+ TEKST", Ui.ACC, new View.OnClickListener() {
            public void onClick(View v) {
                a.checkpoint();
                Core.Obj o = TabText.newText(a, "NOWY NAPIS", "anton", d.product.w / 2, d.product.h / 2,
                        d.product.w - 12, Math.min(18, d.product.h * 0.14), "Napis");
                o.layer = Core.L_ENGRAVE_VECTOR;
                d.objects.add(o);
                a.commit("Dodano napis");
            }
        }));
        add.addView(TabGen.sqBtn(a, "+ PROSTOKĄT", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                shape(a, "rect");
            }
        }));
        add.addView(TabGen.sqBtn(a, "+ KOŁO", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                shape(a, "circle");
            }
        }));
        add.addView(TabGen.sqBtn(a, "+ RAMKA", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                a.checkpoint();
                double m = Math.max(3, d.product.engraveMargin);
                android.graphics.Path p = Draw.frame("double", new android.graphics.RectF(
                        (float) m, (float) m, (float) (d.product.w - m), (float) (d.product.h - m)), 0.4f);
                Core.Obj o = new Core.Obj();
                o.id = d.newId("frame");
                o.type = "path";
                o.layer = Core.L_ENGRAVE_VECTOR;
                o.role = "line";
                o.name = "Ramka";
                o.d = Draw.pathToSvgD(p, 0.1f);
                o.strokeW = 0.3;
                d.objects.add(o);
                a.commit("Dodano ramkę");
            }
        }));
        add.addView(TabGen.sqBtn(a, "+ SZCZEGÓŁY", Ui.CARD2, new View.OnClickListener() {
            public void onClick(View v) {
                a.checkpoint();
                android.graphics.Path p = Gen.ornamentLine(d.product.w, d.product.h,
                        Math.max(3, d.product.engraveMargin) + 2, d.objects.size());
                Core.Obj o = new Core.Obj();
                o.id = d.newId("orn");
                o.type = "path";
                o.layer = Core.L_ENGRAVE_VECTOR;
                o.role = "line";
                o.name = "Ornament";
                o.d = Draw.pathToSvgD(p, 0.1f);
                o.strokeW = 0.25;
                d.objects.add(o);
                a.commit("Dodano ornament");
            }
        }));
        col.addView(Ui.hscroll(a, add));

        LinearLayout foot = Ui.card(a);
        foot.addView(Ui.text(a, "Historia: " + a.historyLabel(), 11, Ui.MUT, false));
        foot.addView(Ui.text(a, "Warstwy: CUT_OUTER (czerwony), CUT_INNER (niebieski), ENGRAVE_VECTOR, "
                + "ENGRAVE_FILL, ENGRAVE_RASTER, SCORE (magenta), GUIDES (zielony). "
                + "Obiekty zablokowane nie dają się zaznaczyć na podglądzie.", 11, Ui.MUT, false));
        col.addView(foot);

        return Ui.scroll(a, col);
    }

    static void move(MainActivity a, EditorView ev, boolean up) {
        Core.Obj o = ev.selected();
        if (o == null) return;
        int i = a.design.objects.indexOf(o);
        int j = up ? i + 1 : i - 1;
        if (j < 0 || j >= a.design.objects.size()) return;
        a.checkpoint();
        a.design.objects.remove(i);
        a.design.objects.add(j, o);
        a.commit(up ? "W górę" : "W dół");
    }

    static void shape(MainActivity a, String kind) {
        a.checkpoint();
        double w = Math.min(60, a.design.product.w * 0.4);
        double h = Math.min(40, a.design.product.h * 0.4);
        android.graphics.Path p = new android.graphics.Path();
        if ("circle".equals(kind)) p.addCircle(0f, 0f, (float) (w / 2), android.graphics.Path.Direction.CW);
        else p.addRect(0f, 0f, (float) w, (float) h, android.graphics.Path.Direction.CW);
        Core.Obj o = new Core.Obj();
        o.id = a.design.newId("shape");
        o.type = "path";
        o.layer = Core.L_ENGRAVE_VECTOR;
        o.role = "line";
        o.name = "circle".equals(kind) ? "Koło" : "Prostokąt";
        o.d = Draw.pathToSvgD(p, 0.1f);
        o.strokeW = 0.3;
        o.x = (a.design.product.w - w) / 2;
        o.y = (a.design.product.h - h) / 2;
        a.design.objects.add(o);
        a.commit("Dodano " + o.name);
    }
}
