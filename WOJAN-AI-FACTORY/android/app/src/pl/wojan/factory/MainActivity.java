package pl.wojan.factory;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * Ekran główny: połączenie z silnikiem WOJAN na komputerze Windows.
 * Telefon steruje fabryką przez zabezpieczone API — nie kompiluje lokalnie.
 */
public class MainActivity extends Activity {
    private EditText serverInput;
    private EditText tokenInput;
    private TextView statusView;
    private TextView infoView;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ETAP 1: minimalny ekran startowy — zero zależności poza TextView/LinearLayout.
        // Zielony pasek „Start OK" = klasa MainActivity ładuje się i działa.
        try {
            showStartup();
        } catch (Throwable t) {
            try {
                TextView v = new TextView(this);
                v.setText("BŁĄD STARTU (v" + BuildInfo.VERSION + "): "
                        + t.getClass().getName() + ": " + String.valueOf(t.getMessage()));
                v.setTextColor(Color.WHITE);
                v.setBackgroundColor(Color.rgb(120, 24, 24));
                v.setTextSize(14);
                v.setPadding(48, 48, 48, 48);
                v.setTextIsSelectable(true);
                setContentView(v);
            } catch (Throwable ignored) {
                // nic więcej nie da się zrobić
            }
            return;
        }
        // ETAP 2: właściwy interfejs — dopiero po pokazaniu zielonego paska.
        // Każdy błąd zamienia się w czerwony panel z nazwą wyjątku i śladem.
        ui.post(new Runnable() {
            public void run() {
                try {
                    buildUi();
                } catch (Throwable t) {
                    showFatal(t);
                }
            }
        });
    }

    /** Ekran startowy — musi przejść nawet przy uszkodzonym Store/API. */
    private void showStartup() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 20, 28));
        root.setPadding(48, 96, 48, 48);
        TextView t = new TextView(this);
        t.setText("WOJAN AI FACTORY\nwersja " + BuildInfo.VERSION
                + "\n\nStart OK — wczytywanie interfejsu…");
        t.setTextColor(Color.rgb(96, 210, 140));
        t.setTextSize(18);
        root.addView(t);
        setContentView(root);
    }

    /** Awaria nigdy nie może być niewidoczna — dokładny błąd na ekranie. */
    private void showFatal(Throwable t) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("BŁĄD STARTU (wersja ").append(BuildInfo.VERSION).append(")\n\n")
              .append(t.getClass().getName()).append(":\n")
              .append(String.valueOf(t.getMessage())).append("\n\n");
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < st.length && i < 8; i++) {
                sb.append("  at ").append(st[i].toString()).append("\n");
            }
            sb.append("\nZrób zdjęcie tego ekranu i wyślij je do zgłoszenia.");
            TextView v = new TextView(this);
            v.setText(sb.toString());
            v.setTextColor(Color.WHITE);
            v.setBackgroundColor(Color.rgb(120, 24, 24));
            v.setTextSize(13);
            v.setPadding(48, 48, 48, 48);
            v.setTextIsSelectable(true);
            ScrollView sv = new ScrollView(this);
            sv.addView(v);
            setContentView(sv);
        } catch (Throwable ignored) {
            TextView v = new TextView(this);
            v.setText("BŁĄD STARTU: " + t.getClass().getName());
            setContentView(v);
        }
    }

    private void buildUi() {
        Store store = Store.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(18, 20, 28));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = label(BuildInfo.NAME, 26, true);
        title.setTextColor(Color.rgb(255, 184, 77));
        root.addView(title);
        TextView sub = label("Fabryka aplikacji AI — sterowanie z telefonu (wersja " + BuildInfo.VERSION + ")", 14, false);
        sub.setTextColor(Color.rgb(150, 158, 176));
        root.addView(sub);
        root.addView(spacer(dp(24)));

        root.addView(label("Adres silnika Windows", 13, true));
        serverInput = field("np. http://192.168.0.10:8777", store.getServer());
        root.addView(serverInput);
        root.addView(spacer(dp(12)));

        root.addView(label("Token dostępu", 13, true));
        tokenInput = field("token z ekranu Ustawienia silnika", store.getToken());
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(tokenInput);
        root.addView(spacer(dp(16)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button save = button("Zapisz i połącz");
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { connect(); }
        });
        row.addView(save);
        row.addView(spacer(dp(12)));
        Button settings = button("Ustawienia");
        settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        row.addView(settings);
        root.addView(row);
        root.addView(spacer(dp(16)));

        statusView = label("Nie połączono", 15, true);
        statusView.setTextColor(Color.rgb(120, 128, 148));
        root.addView(statusView);
        root.addView(spacer(dp(8)));

        infoView = label("", 13, false);
        infoView.setTextColor(Color.rgb(150, 158, 176));
        root.addView(infoView);
        root.addView(spacer(dp(24)));

        Button open = button("  Otwórz projekty  ");
        open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ProjectsActivity.class));
            }
        });
        root.addView(open);
        root.addView(spacer(dp(32)));

        TextView note = label(
            "Telefon wydaje polecenia i odczytuje wyniki. Kompilacja EXE/APK\n" +
            "odbywa się na komputerze z silnikiem WOJAN (Windows SDK, Gradle,\n" +
            "Android SDK, Git, Ollama). Telefon nie udaje kompilatora.", 12, false);
        note.setTextColor(Color.rgb(110, 118, 138));
        root.addView(note);

        setContentView(scroll);
    }

    private void connect() {
        final String server = serverInput.getText().toString().trim();
        final String token = tokenInput.getText().toString().trim();
        Store.get(this).save(server, token);
        statusView.setText("Łączenie…");
        statusView.setTextColor(Color.rgb(255, 184, 77));
        new Thread(new Runnable() {
            public void run() {
                final StringBuilder info = new StringBuilder();
                final boolean[] ok = {false};
                try {
                    ApiClient api = new ApiClient(server, token);
                    JSONObject st = api.get("/api/v1/status");
                    ok[0] = st.optBoolean("ok", false);
                    info.append("Silnik: ").append(ApiClient.optString(st, "version", "?")).append("\n");
                    info.append("Tryb AI: ").append(ApiClient.optString(st, "mode", "?")).append("\n");
                    JSONObject tasks = st.optJSONObject("tasks");
                    if (tasks != null) {
                        info.append("Zadania w toku: ").append(tasks.optInt("running", 0)).append("\n");
                    }
                    org.json.JSONArray prov = ApiClient.optArray(st, "providers");
                    for (int i = 0; i < prov.length(); i++) {
                        JSONObject p = prov.optJSONObject(i);
                        if (p != null) {
                            info.append(" • ").append(ApiClient.optString(p, "name", "?"))
                                .append(": ").append(ApiClient.optString(p, "state", "?")).append("\n");
                        }
                    }
                } catch (Exception e) {
                    info.append("Błąd: ").append(e.getMessage());
                }
                final boolean success = ok[0];
                ui.post(new Runnable() {
                    public void run() {
                        if (success) {
                            statusView.setText("Połączono z fabryką ✓");
                            statusView.setTextColor(Color.rgb(96, 210, 140));
                        } else {
                            statusView.setText("Brak połączenia");
                            statusView.setTextColor(Color.rgb(230, 100, 100));
                        }
                        infoView.setText(info.toString());
                    }
                });
            }
        }).start();
    }

    // ---- pomocnicze budowanie UI (bez zasobów XML) ----
    private TextView label(String text, int sizeSp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(90, 98, 118));
        e.setTextColor(Color.WHITE);
        e.setTextSize(15);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackgroundColor(Color.rgb(32, 36, 48));
        if (value != null && value.length() > 0) e.setText(value);
        return e;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(18, 20, 28));
        b.setBackgroundColor(Color.rgb(255, 184, 77));
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        b.setLayoutParams(lp);
        return b;
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h));
        return v;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
