package android.content;
public class Context {
    public static final int MODE_PRIVATE = 0;
    private final java.util.Map<String, SharedPreferences> prefs = new java.util.HashMap<String, SharedPreferences>();
    public SharedPreferences getSharedPreferences(String name, int mode) {
        SharedPreferences p = prefs.get(name);
        if (p == null) { p = new InMemoryPrefs(); prefs.put(name, p); }
        return p;
    }
    public Context getApplicationContext() { return this; }
    public android.content.res.Resources getResources() { return new android.content.res.Resources(); }
    public void startActivity(Intent i) { android.view.View.LOG.add("startActivity:" + i); }
    public String getPackageName() { return "pl.wojan.factory"; }
    public java.io.File getExternalFilesDir(String type) { return new java.io.File(System.getProperty("java.io.tmpdir"), "wojan-test"); }
    public java.io.File getFilesDir() { return new java.io.File(System.getProperty("java.io.tmpdir"), "wojan-test"); }
    public java.io.File getCacheDir() { return getFilesDir(); }
    public java.io.File getExternalCacheDir() { return getFilesDir(); }
    public Object getSystemService(String name) { return null; }
    static class InMemoryPrefs implements SharedPreferences {
        private final java.util.Map<String, Object> map = new java.util.HashMap<String, Object>();
        public String getString(String k, String d) { Object o = map.get(k); return o == null ? d : (String) o; }
        public int getInt(String k, int d) { Object o = map.get(k); return o == null ? d : (Integer) o; }
        public long getLong(String k, long d) { Object o = map.get(k); return o == null ? d : (Long) o; }
        public boolean getBoolean(String k, boolean d) { Object o = map.get(k); return o == null ? d : (Boolean) o; }
        public Editor edit() {
            return new Editor() {
                private final java.util.Map<String, Object> pending = new java.util.HashMap<String, Object>();
                public Editor putString(String k, String v) { pending.put(k, v); return this; }
                public Editor putInt(String k, int v) { pending.put(k, v); return this; }
                public Editor putLong(String k, long v) { pending.put(k, v); return this; }
                public Editor putBoolean(String k, boolean v) { pending.put(k, v); return this; }
                public Editor remove(String k) { pending.put(k, null); return this; }
                public void apply() { for (java.util.Map.Entry<String, Object> e : pending.entrySet()) {
                    if (e.getValue() == null) map.remove(e.getKey()); else map.put(e.getKey(), e.getValue()); } }
                public boolean commit() { apply(); return true; }
            };
        }
    }
}
