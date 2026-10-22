package pl.wojan.diag;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Constructor;

/**
 * Diagnostyka startu WOJAN AI FACTORY. Osobny pakiet (pl.wojan.diag) —
 * instaluje się OBOK głównej apki. Jeśli główna apka crashuje się bez
 * komunikatu, ten ekran pokaże DOKŁADNIE który krok tworzenia klasy
 * MainActivity się wywala i z jakim wyjątkiem.
 */
public class DiagActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StringBuilder log = new StringBuilder();
        log.append("DIAG 1.0.6 — pakiet pl.wojan.diag\n");
        log.append("Wersja zrodel: ").append(pl.wojan.factory.BuildInfo.VERSION).append("\n\n");
        log.append("Test tworzenia pl.wojan.factory.MainActivity:\n");
        boolean classesOk = false;
        try {
            Class<?> c = Class.forName("pl.wojan.factory.MainActivity");
            log.append("1. Class.forName     : OK\n");
            Constructor<?> ctor = c.getConstructor();
            log.append("2. getConstructor    : OK\n");
            Object o = ctor.newInstance();
            log.append("3. newInstance <init>: OK\n\n");
            log.append("Klasy laduja sie poprawnie. Jesli glowna apka\n");
            log.append("mimo to crashuje — wcisnij przycisk ponizej\n");
            log.append("i zrob zrzut ekranu.");
            classesOk = true;
        } catch (Throwable t) {
            log.append("\n*** BLAD ***\n");
            log.append(t.getClass().getName()).append("\n");
            log.append(String.valueOf(t.getMessage())).append("\n");
            Throwable c2 = t.getCause();
            int lvl = 1;
            while (c2 != null && lvl <= 3) {
                log.append("PRZYCZYNA").append(lvl).append(": ")
                  .append(c2.getClass().getName()).append("\n");
                log.append("   ").append(String.valueOf(c2.getMessage())).append("\n");
                c2 = c2.getCause();
                lvl++;
            }
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < st.length && i < 12; i++) {
                log.append("  at ").append(st[i].toString()).append("\n");
            }
            log.append("\nZrob ZRZUT EKRANU tego ekranu.");
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 20, 28));
        root.setPadding(48, 96, 48, 48);
        TextView t = new TextView(this);
        t.setText(log.toString());
        t.setTextSize(13);
        t.setTextColor(classesOk ? Color.rgb(96, 210, 140) : Color.rgb(255, 130, 130));
        t.setTextIsSelectable(true);
        root.addView(t);

        if (classesOk) {
            Button b = new Button(this);
            b.setText("URUCHOM WOJAN AI FACTORY");
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(v.getContext(),
                            pl.wojan.factory.MainActivity.class));
                }
            });
            root.addView(b);
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }
}
