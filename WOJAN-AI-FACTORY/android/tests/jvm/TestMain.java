import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Testy uruchomieniowe logiki aplikacji WOJAN na JVM (atrapy Androida).
 * Obejmują REALNE wykonanie: MainActivity.onCreate (start), showFatal (diagnostyka),
 * Store (zapis/odczyt), SettingsActivity.onCreate, oraz Runnable buildUi.
 */
public class TestMain {
    static int failures = 0;

    static void check(String name, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + name);
        if (!ok) failures++;
    }

    static String log() {
        StringBuilder sb = new StringBuilder();
        for (String s : View.LOG) sb.append(s).append('\n');
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        // ---------- 1) pełny start MainActivity ----------
        View.LOG.clear();
        Class<?> mc = Class.forName("pl.wojan.factory.MainActivity");
        Constructor<?> ctor = mc.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object act = ctor.newInstance();
        Method onCreate = mc.getDeclaredMethod("onCreate", Bundle.class);
        onCreate.setAccessible(true);
        onCreate.invoke(act, new Bundle());
        String all = log();
        check("start: zielony ekran 'Start OK' pokazany", all.contains("Start OK"));
        check("start: numer wersji 1.0.5 na ekranie startowym", all.contains("1.0.5"));
        check("start: setContentView wywolane", all.contains("setContentView"));
        check("start: tytul glowny 'WOJAN AI FACTORY'", all.contains("WOJAN AI FACTORY"));
        check("start: pole 'Adres silnika Windows'", all.contains("Adres silnika Windows"));
        check("start: pole 'Token dostepu'", all.contains("Token dostępu"));
        check("start: przycisk 'Zapisz i polacz'", all.contains("Zapisz i połącz"));
        check("start: przycisk 'Otworz projekty'", all.contains("Otwórz projekty"));

        // ---------- 2) buildUi po uruchomieniu (Runnable) ----------
        // Handler.post wykonal sie synchronicznie w onCreate — jesli doszlismy tutaj,
        // buildUi nie rzucil wyjatkiem (bylby zlapany przez showFatal).
        check("start: buildUi bez wyjatku (brak panelu bledu)", !all.contains("BŁĄD STARTU"));

        // ---------- 3) showFatal — czerwony panel diagnostyczny ----------
        View.LOG.clear();
        Method sf = mc.getDeclaredMethod("showFatal", Throwable.class);
        sf.setAccessible(true);
        sf.invoke(act, new RuntimeException("TEST-EXC-123"));
        all = log();
        check("showFatal: tytul BŁĄD STARTU", all.contains("BŁĄD STARTU"));
        check("showFatal: nazwa wyjatku na ekranie", all.contains("TEST-EXC-123"));
        check("showFatal: klasa wyjatku na ekranie", all.contains("java.lang.RuntimeException"));

        // ---------- 4) showStartup — zielony ekran ----------
        View.LOG.clear();
        Method ss = mc.getDeclaredMethod("showStartup");
        ss.setAccessible(true);
        ss.invoke(act);
        all = log();
        check("showStartup: zielony 'Start OK'", all.contains("Start OK"));
        check("showStartup: wersja 1.0.5", all.contains("1.0.5"));

        // ---------- 5) Store — zapis i odczyt ustawien ----------
        Class<?> sc = Class.forName("pl.wojan.factory.Store");
        Method getStore = sc.getMethod("get", Context.class);
        Object store = getStore.invoke(null, act);
        sc.getMethod("save", String.class, String.class).invoke(store, "http://192.168.0.10:8777", "token123");
        sc.getMethod("saveAlias", String.class).invoke(store, "Telefon-Test");
        sc.getMethod("saveRefresh", int.class).invoke(store, 7);
        check("Store: getServer po save", "http://192.168.0.10:8777".equals(sc.getMethod("getServer").invoke(store)));
        check("Store: getToken po save", "token123".equals(sc.getMethod("getToken").invoke(store)));
        check("Store: getAlias po saveAlias", "Telefon-Test".equals(sc.getMethod("getAlias").invoke(store)));
        check("Store: getRefresh po saveRefresh", Integer.valueOf(7).equals(sc.getMethod("getRefresh").invoke(store)));

        // ---------- 6) SettingsActivity.onCreate ----------
        View.LOG.clear();
        Class<?> stc = Class.forName("pl.wojan.factory.SettingsActivity");
        Constructor<?> stCtor = stc.getDeclaredConstructor();
        stCtor.setAccessible(true);
        Object st = stCtor.newInstance();
        Method stOn = stc.getDeclaredMethod("onCreate", Bundle.class);
        stOn.setAccessible(true);
        stOn.invoke(st, new Bundle());
        all = log();
        check("SettingsActivity: setContentView wywolane", all.contains("setContentView"));
        check("SettingsActivity: bez wyjatku (brak BŁĄD STARTU)", !all.contains("BŁĄD STARTU"));

        // ---------- 7) MainActivity$1.run() (Runnable buildUi) ----------
        View.LOG.clear();
        Class<?> r1 = Class.forName("pl.wojan.factory.MainActivity$1");
        Constructor<?> r1c = r1.getDeclaredConstructor(mc);
        r1c.setAccessible(true);
        Object runnable = r1c.newInstance(act);
        ((Runnable) runnable).run();
        all = log();
        check("Runnable buildUi: tytul pojawil sie", all.contains("WOJAN AI FACTORY"));
        check("Runnable buildUi: bez panelu bledu", !all.contains("BŁĄD STARTU"));

        // ---------- 8) dp() — konwersja px ----------
        Method dp = mc.getDeclaredMethod("dp", int.class);
        dp.setAccessible(true);
        check("dp(24) = 48 przy density 2.0", Integer.valueOf(48).equals(dp.invoke(act, 24)));

        System.out.println();
        if (failures == 0) {
            System.out.println("=== JVM TESTS: ALL PASS (8 sekcji) ===");
        } else {
            System.out.println("=== JVM TESTS: " + failures + " FAILURES ===");
            System.exit(1);
        }
    }
}
