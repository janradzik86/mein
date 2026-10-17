package pl.wojan.factory;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Ustawienia aplikacji mobilnej. */
public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Store store = Store.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(18, 20, 28));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Ustawienia");
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setTextColor(Color.rgb(255, 184, 77));
        root.addView(title);

        root.addView(label("Alias urządzenia (widoczny w silniku)"));
        final EditText alias = field(store.getAlias());
        root.addView(alias);

        root.addView(label("Odświeżanie statusu (sekundy)"));
        final EditText refresh = field(String.valueOf(store.getRefresh()));
        root.addView(refresh);

        root.addView(label("Adres silnika"));
        final EditText server = field(store.getServer());
        root.addView(server);

        root.addView(label("Token dostępu"));
        final EditText token = field(store.getToken());
        root.addView(token);

        Button save = new Button(this);
        save.setText("Zapisz");
        save.setTextColor(Color.rgb(18, 20, 28));
        save.setBackgroundColor(Color.rgb(255, 184, 77));
        save.setPadding(dp(16), dp(10), dp(16), dp(10));
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                store.saveAlias(alias.getText().toString().trim());
                try {
                    int r = Integer.parseInt(refresh.getText().toString().trim());
                    store.saveRefresh(r > 0 ? r : 5);
                } catch (NumberFormatException e) {
                    store.saveRefresh(5);
                }
                store.save(server.getText().toString().trim(), token.getText().toString().trim());
                finish();
            }
        });
        root.addView(save);

        TextView note = new TextView(this);
        note.setText("\nToken jest generowany w silniku na komputerze i tam też\n" +
            "przechowywany. Nie zapisuj go w niezaufanych miejscach.\n" +
            "Połączenie działa wyłącznie w Twojej sieci (LAN) lub przez\n" +
            "bezpieczny tunel wskazany przez właściciela fabryki.");
        note.setTextColor(Color.rgb(110, 118, 138));
        note.setTextSize(12);
        root.addView(note);

        setContentView(scroll);
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(Color.rgb(150, 158, 176));
        t.setPadding(0, dp(14), 0, dp(4));
        return t;
    }

    private EditText field(String value) {
        EditText e = new EditText(this);
        e.setTextColor(Color.WHITE);
        e.setTextSize(15);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackgroundColor(Color.rgb(32, 36, 48));
        if (value != null) e.setText(value);
        return e;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
