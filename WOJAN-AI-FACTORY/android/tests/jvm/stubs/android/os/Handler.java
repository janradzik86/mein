package android.os;
public class Handler {
    public Handler() { }
    public Handler(Looper looper) { }
    public boolean post(Runnable r) { r.run(); return true; }
    public boolean postDelayed(Runnable r, long ms) { r.run(); return true; }
    public void removeCallbacks(Runnable r) { }
}
