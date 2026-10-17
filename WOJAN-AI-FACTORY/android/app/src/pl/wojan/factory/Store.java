package pl.wojan.factory;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

/** Magazyn ustawień aplikacji (połączenie z silnikiem WOJAN). */
public final class Store {
    private static Store instance;
    private final SharedPreferences prefs;

    private Store(Context c) {
        prefs = c.getSharedPreferences("wojan", Context.MODE_PRIVATE);
    }

    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    public String getServer() { return prefs.getString("server", ""); }
    public String getToken() { return prefs.getString("token", ""); }
    public String getAlias() { return prefs.getString("alias", "Telefon"); }
    public int getRefresh() { return prefs.getInt("refresh", 5); }

    public void save(String server, String token) {
        prefs.edit().putString("server", server).putString("token", token).apply();
    }
    public void saveAlias(String alias) {
        prefs.edit().putString("alias", alias).apply();
    }
    public void saveRefresh(int sec) {
        prefs.edit().putInt("refresh", sec).apply();
    }
}
