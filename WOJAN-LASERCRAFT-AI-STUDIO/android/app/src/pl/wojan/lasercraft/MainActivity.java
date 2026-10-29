package pl.wojan.lasercraft;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * WOJAN LaserCraft AI Studio — Android.
 * Natywny interfejs (bez WebView), 12 zakładek, silnik lokalny, brak kont i chmury.
 */
public class MainActivity extends Activity {

    public static final String VERSION = "1.0.2";
    public static final String[] TABS = {"Generator AI", "Mikrofon i prompt", "Import zdjęcia",
            "Kategorie", "Edytor", "Napisy AI", "QR", "Moje projekty", "Kalkulator", "Eksport",
            "Diagnostyka AI", "Ustawienia"};

    public Core.Design design;
    public Draw.Provider provider;
    private LinearLayout root;
    private HorizontalScrollView tabBar;
    private LinearLayout tabRow;
    private FrameLayout host;
    private int current = -1;
    private final List<String> undoStack = new ArrayList<String>();
    private final List<String> redoStack = new ArrayList<String>();
    private byte[] pendingBytes;
    private String pendingName;
    public int lastTab = 0;
    public boolean autoSave = true;
    public Uri cameraTarget;

    public static final int REQ_IMAGE = 11;
    public static final int REQ_PROJECT = 12;
    public static final int REQ_ZIP = 13;
    public static final int REQ_CREATE = 14;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        installCrashHandler();
        try {
            initUi();
        } catch (Throwable t) {
            showFatal(t);
        }
    }

    /** Zapisuje nieobsłużony wyjątek do pliku i pokazuje go na ekranie (diagnostyka zdalna). */
    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread th, Throwable e) {
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(sw));
                    writeCrash(sw.toString());
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(th, e);
            }
        });
    }

    private void writeCrash(String trace) {
        try {
            java.io.File f = new java.io.File(getFilesDir(), "crash.txt");
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(trace.getBytes("UTF-8"));
            fo.close();
        } catch (Throwable ignored) {
        }
    }

    /** Ekran błędu startu — zamiast anonimowego zamknięcia aplikacji. */
    private void showFatal(Throwable t) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            String trace = sw.toString();
            writeCrash(trace);
            ScrollView sc = new ScrollView(this);
            sc.setBackgroundColor(0xFF0E1013);
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(Ui.dp(this, 14), Ui.dp(this, 24), Ui.dp(this, 14), Ui.dp(this, 14));
            box.addView(Ui.text(this, "WOJAN LaserCraft AI — błąd uruchamiania", 16, 0xFFFF6B6B, true));
            box.addView(Ui.text(this, "Wersja " + VERSION + " · Android " + android.os.Build.VERSION.RELEASE
                    + " · " + android.os.Build.MODEL, 12, Ui.TXT, false));
            TextView tv = Ui.text(this, trace, 11, 0xFFD0D4DA, false);
            tv.setTypeface(android.graphics.Typeface.MONOSPACE);
            box.addView(tv);
            sc.addView(box);
            setContentView(sc);
        } catch (Throwable ignored) {
        }
    }

    private void initUi() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(0xFF0E1013);
        getWindow().setNavigationBarColor(0xFF0E1013);
        provider = new Draw.Provider(this);

        design = Gen.blank("");
        Core.Spec sp = new Core.Spec();
        sp.productId = "tabliczka";
        sp.topText = "WOJAN LASERCRAFT";
        sp.bottomText = "AI STUDIO";
        Gen.applySpec(this, design, sp, 0);
        design.name = "Projekt startowy";

        root = Ui.col(this);
        root.setBackgroundColor(Ui.BG);

        // nagłówek
        LinearLayout head = Ui.row(this);
        head.setBackgroundColor(0xFF15181C);
        int hp = Ui.dp(this, 10);
        head.setPadding(hp, hp, hp, hp);
        TextView title = Ui.text(this, "WOJAN LaserCraft AI", 16, Ui.ACC, true);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button save = Ui.button(this, "ZAPISZ", new View.OnClickListener() {
            public void onClick(View v) {
                saveProject();
            }
        });
        head.addView(save);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(MainActivity.this, 36));
        save.setLayoutParams(blp);
        root.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // pasek zakładek
        tabRow = Ui.row(this);
        tabRow.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        tabBar = Ui.hscroll(this, tabRow);
        tabBar.setBackgroundColor(0xFF101317);
        root.addView(tabBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        host = new FrameLayout(this);
        root.addView(host, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        applyInsets(root);
        showTab(0);
    }

    private void applyInsets(View v) {
        final int top = Ui.dp(this, 4);
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public android.view.WindowInsets onApplyWindowInsets(View view, android.view.WindowInsets insets) {
                int t = 0, bt = 0, l = 0, r = 0;
                try {
                    t = insets.getSystemWindowInsetTop();
                    bt = insets.getSystemWindowInsetBottom();
                    l = insets.getSystemWindowInsetLeft();
                    r = insets.getSystemWindowInsetRight();
                } catch (Exception e) {
                }
                view.setPadding(l, t + top, r, bt);
                return insets;
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (current != 0) {
            showTab(0);
            return;
        }
        super.onBackPressed();
    }

    // ---------------- zakładki ----------------
    public void showTab(int i) {
        if (i < 0 || i >= TABS.length) return;
        current = i;
        lastTab = i;
        rebuildTabs();
        host.removeAllViews();
        View v;
        try {
            v = buildTab(i);
        } catch (Throwable t) {
            v = tabError(i, t);
        }
        host.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View tabError(int i, Throwable t) {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        box.addView(Ui.text(this, "Zakładka „" + TABS[i] + "” zgłosiła błąd", 15, 0xFFFF6B6B, true));
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        writeCrash(sw.toString());
        TextView tv = Ui.text(this, sw.toString(), 11, 0xFFD0D4DA, false);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        box.addView(tv);
        sc.addView(box);
        return sc;
    }

    private View buildTab(int i) {
        View v;
        switch (i) {
            case 0: v = TabGen.build(this); break;
            case 1: v = TabMic.build(this); break;
            case 2: v = TabImport.build(this); break;
            case 3: v = TabCat.build(this); break;
            case 4: v = TabEdit.build(this); break;
            case 5: v = TabText.build(this); break;
            case 6: v = TabQr.build(this); break;
            case 7: v = TabProj.build(this); break;
            case 8: v = TabCalc.build(this); break;
            case 9: v = TabExport.build(this); break;
            case 10: v = TabDiag.build(this); break;
            default: v = TabSet.build(this); break;
        }
        return v;
    }

    private void rebuildTabs() {
        tabRow.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button b = Ui.button(this, TABS[i], new View.OnClickListener() {
                public void onClick(View v) {
                    showTab(idx);
                }
            });
            boolean active = i == current;
            b.setTextColor(active ? 0xFF101214 : Ui.TXT);
            b.setBackground(Ui.round(active ? Ui.ACC : Ui.CARD2, 0xFF3A424D, 8, this, 1));
            b.setTextSize(12);
            b.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 34));
            lp.rightMargin = Ui.dp(this, 4);
            tabRow.addView(b, lp);
        }
    }

    public void refresh() {
        showTab(current < 0 ? 0 : current);
    }

    public void refreshKeepScroll() {
        rebuildTabs();
        host.removeAllViews();
        showTab(current < 0 ? 0 : current);
    }

    // ---------------- historia ----------------
    public void checkpoint() {
        try {
            undoStack.add(design.toJson().toString());
            if (undoStack.size() > 60) undoStack.remove(0);
            redoStack.clear();
        } catch (Exception e) {
        }
    }

    public void commit(String label) {
        design.pushHistory(label);
        if (autoSave) Store.save(this, design);
        refresh();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public String historyLabel() {
        if (design.history.isEmpty()) return "—";
        int p = Math.max(0, Math.min(design.historyPos, design.history.size() - 1));
        return (p + 1) + "/" + design.history.size() + ": " + design.history.get(p);
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            Ui.toast(this, "Brak zmian do cofnięcia.");
            return;
        }
        try {
            redoStack.add(design.toJson().toString());
            String prev = undoStack.remove(undoStack.size() - 1);
            Core.Design d = Core.Design.fromJson(new org.json.JSONObject(prev));
            String id = design.id;
            d.id = id;
            design = d;
            refresh();
            Ui.toast(this, "Cofnięto.");
        } catch (Exception e) {
            Ui.toast(this, "Błąd cofania: " + e.getMessage());
        }
    }

    public void redo() {
        if (redoStack.isEmpty()) {
            Ui.toast(this, "Brak zmian do ponowienia.");
            return;
        }
        try {
            undoStack.add(design.toJson().toString());
            String nx = redoStack.remove(redoStack.size() - 1);
            Core.Design d = Core.Design.fromJson(new org.json.JSONObject(nx));
            String id = design.id;
            d.id = id;
            design = d;
            refresh();
            Ui.toast(this, "Ponowiono.");
        } catch (Exception e) {
            Ui.toast(this, "Błąd ponowienia: " + e.getMessage());
        }
    }

    public void setDesign(Core.Design d) {
        design = d;
        undoStack.clear();
        redoStack.clear();
    }

    public boolean saveProject() {
        if (design.id == null || design.id.isEmpty()) design.id = Store.newId();
        boolean ok = Store.save(this, design);
        Store.snapshot(this, design, "autozapis");
        Ui.toast(this, ok ? "Zapisano: " + design.name : "Nie udało się zapisać projektu.");
        return ok;
    }

    public Core.Report report() {
        return Core.validate(design, provider);
    }

    // ---------------- pliki ----------------
    public void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try {
            startActivityForResult(i, REQ_IMAGE);
        } catch (Exception e) {
            Ui.toast(this, "Brak menedżera plików.");
        }
    }

    public void pickProject() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream",
                "application/json", "text/plain", "*/*"});
        try {
            startActivityForResult(i, REQ_ZIP);
        } catch (Exception e) {
            Ui.toast(this, "Brak menedżera plików.");
        }
    }

    public void saveFile(String name, String mime, byte[] data) {
        pendingBytes = data;
        pendingName = name;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        try {
            startActivityForResult(i, REQ_CREATE);
        } catch (Exception e) {
            Ui.toast(this, "Brak menedżera plików — zapisano do katalogu aplikacji.");
            writeLocal(name, data);
        }
    }

    public File writeLocal(String name, byte[] data) {
        try {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "");
            if (dir == null || !dir.exists()) dir = new File(getFilesDir(), "export");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, name);
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(data);
            fo.close();
            Ui.toast(this, "Zapisano: " + f.getAbsolutePath());
            return f;
        } catch (Exception e) {
            Ui.toast(this, "Błąd zapisu: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) {
            if (req == REQ_CREATE) {
                if (pendingBytes != null && pendingName != null) {
                    writeLocal(pendingName, pendingBytes);
                    pendingBytes = null;
                    pendingName = null;
                }
            }
            return;
        }
        Uri uri = data.getData();
        if (req == REQ_CREATE) {
            try {
                Store.writeUri(this, uri, pendingBytes);
                Ui.toast(this, "Zapisano plik: " + pendingName);
            } catch (Exception e) {
                Ui.toast(this, "Błąd zapisu: " + e.getMessage());
            }
            pendingBytes = null;
            pendingName = null;
        } else if (req == 21) {
            TabImport.handleImage(this, cameraTarget != null ? cameraTarget : uri);
        } else if (req == REQ_IMAGE) {
            TabImport.handleImage(this, uri);
        } else if (req == REQ_ZIP) {
            String nm = nameOf(uri);
            String low = nm.toLowerCase(java.util.Locale.US);
            if (low.endsWith(".svg") || low.endsWith(".dxf") || low.endsWith(".pdf")) {
                TabImport.handleVector(this, uri, nm);
            } else if (low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg")
                    || low.endsWith(".webp") || low.endsWith(".bmp")) {
                TabImport.handleImage(this, uri);
            } else {
                TabProj.handleImport(this, uri);
            }
        } else if (req == REQ_PROJECT) {
            TabProj.handleImport(this, uri);
        }
    }

    public String nameOf(Uri uri) {
        try {
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (c.moveToFirst() && idx >= 0) {
                    String n = c.getString(idx);
                    c.close();
                    if (n != null) return n;
                }
                c.close();
            }
        } catch (Exception e) {
        }
        String p = uri.getLastPathSegment();
        return p == null ? "plik" : p;
    }

    public void openProject(String id) {
        Core.Design d = Store.load(this, id);
        if (d == null) {
            Ui.toast(this, "Nie można otworzyć projektu.");
            return;
        }
        setDesign(d);
        showTab(4);
        Ui.toast(this, "Otwarto: " + d.name);
    }

    public View scrollOf(View content) {
        ScrollView s = Ui.scroll(this, content);
        return s;
    }
}
