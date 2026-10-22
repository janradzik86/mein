package android.app;
import android.content.Context;
public class Activity extends Context {
    public static java.util.List<String> CONTENT = new java.util.ArrayList<String>();
    protected void onCreate(android.os.Bundle savedInstanceState) { }
    protected void onDestroy() { }
    protected void onResume() { }
    protected void onPause() { }
    public void setContentView(android.view.View v) {
        android.view.View.LOG.add("setContentView:" + (v == null ? "null" : v.getClass().getSimpleName()));
    }
    public void setContentView(int layout) { android.view.View.LOG.add("setContentView:layout"); }
    public void setTitle(CharSequence t) { android.view.View.LOG.add("setTitle:" + t); }
    public void finish() { android.view.View.LOG.add("finish"); }
    public android.view.View findViewById(int id) { return null; }
    public final void runOnUiThread(Runnable r) { r.run(); }
}
