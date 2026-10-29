package pl.wojan.lasercraft;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Pomocnicze budowanie natywnego interfejsu (ciemny grafit, bez plików zasobów). */
public final class Ui {

    public static final int BG = Color.rgb(0x12, 0x14, 0x17);
    public static final int CARD = Color.rgb(0x1B, 0x1F, 0x25);
    public static final int CARD2 = Color.rgb(0x23, 0x28, 0x30);
    public static final int TXT = Color.rgb(0xE7, 0xEA, 0xEF);
    public static final int MUT = Color.rgb(0x9A, 0xA4, 0xB1);
    public static final int ACC = Color.rgb(0xFF, 0x7A, 0x18);
    public static final int ACC2 = Color.rgb(0x25, 0xC2, 0xA4);
    public static final int WARN = Color.rgb(0xFF, 0xC1, 0x3B);
    public static final int ERR = Color.rgb(0xFF, 0x5C, 0x5C);

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(int fill, int stroke, float radiusDp, Context c, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, strokeDp), stroke);
        return g;
    }

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView text(Context c, String s, float size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s == null ? "" : s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(round(CARD, Color.rgb(0x2C, 0x32, 0x3B), 10, c, 1));
        int p = dp(c, 10);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static TextView section(Context c, String title) {
        TextView t = text(c, title, 15, ACC, true);
        int p = dp(c, 8);
        t.setPadding(0, p, 0, dp(c, 4));
        return t;
    }

    public static Button button(Context c, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(Color.rgb(0x10, 0x12, 0x14));
        b.setBackground(round(ACC, 0, 8, c, 0));
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        b.setMinHeight(0);
        b.setMinimumHeight(dp(c, 40));
        b.setOnClickListener(l);
        return b;
    }

    public static Button buttonAlt(Context c, String label, View.OnClickListener l) {
        Button b = button(c, label, l);
        b.setTextColor(TXT);
        b.setBackground(round(CARD2, Color.rgb(0x3A, 0x42, 0x4D), 8, c, 1));
        return b;
    }

    public static Button buttonAcc2(Context c, String label, View.OnClickListener l) {
        Button b = button(c, label, l);
        b.setBackground(round(ACC2, 0, 8, c, 0));
        return b;
    }

    public static EditText edit(Context c, String hint, String value, int lines) {
        EditText e = new EditText(c);
        e.setHint(hint);
        if (value != null) e.setText(value);
        e.setTextColor(TXT);
        e.setHintTextColor(MUT);
        e.setTextSize(13);
        e.setBackground(round(CARD2, Color.rgb(0x3A, 0x42, 0x4D), 8, c, 1));
        e.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        if (lines > 1) {
            e.setSingleLine(false);
            e.setMinLines(lines);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        return e;
    }

    public static EditText num(Context c, String hint, double value) {
        EditText e = edit(c, hint, Core.fmt(value), 1);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return e;
    }

    public static double val(EditText e, double dflt) {
        try {
            String s = e.getText().toString().trim().replace(',', '.');
            if (s.isEmpty()) return dflt;
            return Double.parseDouble(s);
        } catch (Exception ex) {
            return dflt;
        }
    }

    public static Spinner spinner(Context c, List<String> items, int selected) {
        Spinner s = new Spinner(c);
        ArrayAdapter<String> a = new ArrayAdapter<String>(c, android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setBackground(round(CARD2, Color.rgb(0x3A, 0x42, 0x4D), 8, c, 1));
        int p = dp(c, 6);
        s.setPadding(p, p, p, p);
        if (selected >= 0 && selected < items.size()) s.setSelection(selected);
        return s;
    }

    public static CheckBox check(Context c, String label, boolean checked, View.OnClickListener l) {
        CheckBox b = new CheckBox(c);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(TXT);
        b.setChecked(checked);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    public static View spacer(Context c, int h) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, h)));
        return v;
    }

    public static View hline(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 0.7f)));
        lp.topMargin = dp(c, 6);
        lp.bottomMargin = dp(c, 6);
        v.setLayoutParams(lp);
        v.setBackgroundColor(Color.rgb(0x33, 0x3A, 0x44));
        return v;
    }

    public static ScrollView scroll(Context c, View content) {
        ScrollView s = new ScrollView(c);
        s.setBackgroundColor(BG);
        s.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        s.setFillViewport(true);
        return s;
    }

    public static HorizontalScrollView hscroll(Context c, View content) {
        HorizontalScrollView s = new HorizontalScrollView(c);
        s.addView(content);
        s.setHorizontalScrollBarEnabled(false);
        return s;
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static void alert(Context c, String title, String msg) {
        try {
            new AlertDialog.Builder(c).setTitle(title).setMessage(msg)
                    .setPositiveButton("OK", null).show();
        } catch (Exception e) {
            toast(c, msg);
        }
    }

    public interface OnText {
        void on(String s);
    }

    public static void input(Context c, String title, String hint, String value, final OnText cb) {
        final EditText e = edit(c, hint, value, 1);
        LinearLayout box = col(c);
        int p = dp(c, 16);
        box.setPadding(p, p, p, 0);
        box.addView(e);
        new AlertDialog.Builder(c).setTitle(title).setView(box)
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        cb.on(e.getText().toString());
                    }
                })
                .setNegativeButton("Anuluj", null).show();
    }

    public static void confirm(Context c, String title, String msg, final Runnable yes) {
        new AlertDialog.Builder(c).setTitle(title).setMessage(msg)
                .setPositiveButton("Tak", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        yes.run();
                    }
                })
                .setNegativeButton("Nie", null).show();
    }

    public static AdapterView.OnItemSelectedListener onSelect(final OnText cb) {
        return new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                cb.on(String.valueOf(pos));
            }

            public void onNothingSelected(AdapterView<?> p) {
            }
        };
    }
}
