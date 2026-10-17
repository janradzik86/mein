package pl.wojan.factory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Lista projektów fabryki + wydawanie zadań agentom + logi + pliki. */
public class ProjectsActivity extends Activity {
    private LinearLayout listWrap;
    private TextView statusView;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(18, 20, 28));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Projekty fabryki");
        title.setTextSize(22);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setTextColor(Color.rgb(255, 184, 77));
        root.addView(title);

        statusView = new TextView(this);
        statusView.setText("Ładowanie…");
        statusView.setTextColor(Color.rgb(150, 158, 176));
        statusView.setTextSize(13);
        root.addView(statusView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = btn("Odśwież");
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { load(); }
        });
        row.addView(refresh);
        row.addView(space(10, 0));
        Button add = btn("Nowy projekt");
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { newProject(); }
        });
        row.addView(add);
        row.addView(space(10, 0));
        Button settings = btn("Ustawienia");
        settings.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(ProjectsActivity.this, SettingsActivity.class));
            }
        });
        row.addView(settings);
        root.addView(row);

        listWrap = new LinearLayout(this);
        listWrap.setOrientation(LinearLayout.VERTICAL);
        root.addView(listWrap);

        setContentView(scroll);
        load();
    }

    private ApiClient api() {
        Store s = Store.get(this);
        return new ApiClient(s.getServer(), s.getToken());
    }

    private void load() {
        statusView.setText("Ładowanie projektów…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    final JSONObject resp = api().get("/api/v1/projects");
                    final JSONArray projects = ApiClient.optArray(resp, "projects");
                    final List<String> lines = new ArrayList<String>();
                    final List<JSONObject> objs = new ArrayList<JSONObject>();
                    for (int i = 0; i < projects.length(); i++) {
                        JSONObject p = projects.optJSONObject(i);
                        if (p != null) {
                            objs.add(p);
                            lines.add(ApiClient.optString(p, "name", "?")
                                + "  [" + ApiClient.optString(p, "target", "?") + "]  —  "
                                + ApiClient.optString(p, "status", "?"));
                        }
                    }
                    ui.post(new Runnable() {
                        public void run() {
                            statusView.setText("Projekty: " + objs.size());
                            render(objs, lines);
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() {
                            statusView.setText("Błąd: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void render(final List<JSONObject> objs, List<String> lines) {
        listWrap.removeAllViews();
        for (int i = 0; i < objs.size(); i++) {
            final JSONObject p = objs.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Color.rgb(32, 36, 48));
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(8), 0, 0);
            card.setLayoutParams(lp);

            TextView name = new TextView(this);
            name.setText(lines.get(i));
            name.setTextColor(Color.WHITE);
            name.setTextSize(15);
            card.addView(name);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button task = btn("Zadanie");
            task.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { newTask(p); }
            });
            actions.addView(task);
            actions.addView(space(8, 0));
            Button logs = btn("Logi");
            logs.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showLogs(p); }
            });
            actions.addView(logs);
            actions.addView(space(8, 0));
            Button files = btn("Pliki");
            files.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showFiles(p); }
            });
            actions.addView(files);
            card.addView(actions);
            listWrap.addView(card);
        }
    }

    private void newProject() {
        final EditText name = input("Nazwa projektu");
        final EditText desc = input("Opis aplikacji");
        final String[] targets = {"windows-exe", "android-apk"};
        final int[] chosen = {0};
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Nowy projekt");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), 0);
        box.addView(name);
        box.addView(desc);
        b.setView(box);
        b.setSingleChoiceItems(targets, 0, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) { chosen[0] = which; }
        });
        b.setPositiveButton("Utwórz", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                final String n = name.getText().toString().trim();
                final String ds = desc.getText().toString().trim();
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("name", n);
                            body.put("target", targets[chosen[0]]);
                            body.put("description", ds);
                            api().post("/api/v1/projects", body);
                            ui.post(new Runnable() { public void run() { load(); } });
                        } catch (final Exception e) {
                            ui.post(new Runnable() {
                                public void run() { statusView.setText("Błąd: " + e.getMessage()); }
                            });
                        }
                    }
                }).start();
            }
        });
        b.setNegativeButton("Anuluj", null);
        b.show();
    }

    private void newTask(final JSONObject project) {
        final String[] roles = {"ARCHITEKT", "KODER", "TESTER", "DIAGNOSTA", "BUILD AGENT", "REVIEWER"};
        final int[] chosen = {1};
        final EditText prompt = input("Polecenie dla agenta");
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Zadanie dla: " + ApiClient.optString(project, "name", "?"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), 0);
        box.addView(prompt);
        b.setView(box);
        b.setSingleChoiceItems(roles, 1, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) { chosen[0] = which; }
        });
        b.setPositiveButton("Uruchom", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                final String pr = prompt.getText().toString().trim();
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            JSONObject body = new JSONObject();
                            body.put("role", roles[chosen[0]]);
                            body.put("prompt", pr);
                            String path = "/api/v1/projects/" + project.optString("id") + "/tasks";
                            api().post(path, body);
                            ui.post(new Runnable() {
                                public void run() {
                                    statusView.setText("Zadanie wysłane ✓");
                                    showLogs(project);
                                }
                            });
                        } catch (final Exception e) {
                            ui.post(new Runnable() {
                                public void run() { statusView.setText("Błąd: " + e.getMessage()); }
                            });
                        }
                    }
                }).start();
            }
        });
        b.setNegativeButton("Anuluj", null);
        b.show();
    }

    private void showLogs(final JSONObject project) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String path = "/api/v1/projects/" + project.optString("id") + "/logs";
                    JSONObject resp = api().get(path);
                    final JSONArray lines = ApiClient.optArray(resp, "lines");
                    final StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < lines.length(); i++) sb.append(lines.optString(i)).append("\n");
                    if (sb.length() == 0) sb.append("(brak logów)");
                    ui.post(new Runnable() {
                        public void run() { dialog("Logi — " + ApiClient.optString(project, "name", "?"), sb.toString()); }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() { statusView.setText("Błąd: " + e.getMessage()); }
                    });
                }
            }
        }).start();
    }

    private void showFiles(final JSONObject project) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String path = "/api/v1/projects/" + project.optString("id") + "/artifacts";
                    JSONObject resp = api().get(path);
                    final JSONArray files = ApiClient.optArray(resp, "artifacts");
                    final List<String> names = new ArrayList<String>();
                    for (int i = 0; i < files.length(); i++) {
                        JSONObject f = files.optJSONObject(i);
                        if (f != null) {
                            names.add(ApiClient.optString(f, "name", "?")
                                + "  (" + f.optLong("size", 0) + " B)");
                        }
                    }
                    final String[] arr = names.toArray(new String[0]);
                    ui.post(new Runnable() {
                        public void run() {
                            AlertDialog.Builder b = new AlertDialog.Builder(ProjectsActivity.this);
                            b.setTitle("Pliki wynikowe (EXE/APK)");
                            if (arr.length == 0) {
                                b.setMessage("Brak plików — uruchom BUILD AGENT.");
                            } else {
                                b.setItems(arr, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int which) {
                                        downloadFile(project, files.optJSONObject(which));
                                    }
                                });
                            }
                            b.setNegativeButton("Zamknij", null);
                            b.show();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() { statusView.setText("Błąd: " + e.getMessage()); }
                    });
                }
            }
        }).start();
    }

    private void downloadFile(final JSONObject project, final JSONObject file) {
        if (file == null) return;
        statusView.setText("Pobieranie " + ApiClient.optString(file, "name", "?") + "…");
        new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] data = api().download("/api/v1/artifacts/" + file.optString("id"));
                    java.io.File dir = getExternalFilesDir(null);
                    if (dir == null) dir = getFilesDir();
                    java.io.File out = new java.io.File(dir, ApiClient.optString(file, "name", "plik.bin"));
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
                    fos.write(data);
                    fos.close();
                    final String p = out.getAbsolutePath();
                    ui.post(new Runnable() {
                        public void run() {
                            dialog("Pobrano ✓", "Zapisano do:\n" + p + "\n\n(" + data.length + " B)");
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        public void run() { statusView.setText("Błąd pobierania: " + e.getMessage()); }
                    });
                }
            }
        }).start();
    }

    private void dialog(String title, String message) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(title);
        b.setMessage(message);
        b.setPositiveButton("OK", null);
        b.show();
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(90, 98, 118));
        e.setTextColor(Color.rgb(220, 224, 235));
        return e;
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(Color.rgb(18, 20, 28));
        b.setBackgroundColor(Color.rgb(255, 184, 77));
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), dp(8), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private View space(int w, int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return v;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}
