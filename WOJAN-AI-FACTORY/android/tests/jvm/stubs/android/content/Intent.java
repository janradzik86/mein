package android.content;
public class Intent {
    private final String desc;
    public Intent() { this.desc = "Intent()"; }
    public Intent(Context c, Class<?> cls) { this.desc = "Intent(" + cls.getSimpleName() + ")"; }
    public Intent(String action) { this.desc = "Intent(" + action + ")"; }
    public Intent putExtra(String k, String v) { return this; }
    public String getStringExtra(String k) { return null; }
    public String toString() { return desc; }
}
