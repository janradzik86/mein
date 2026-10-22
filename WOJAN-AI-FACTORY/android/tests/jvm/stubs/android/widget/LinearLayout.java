package android.widget;
import android.view.ViewGroup;
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0, VERTICAL = 1;
    public LinearLayout(android.content.Context c) { super(c); }
    public void setOrientation(int o) { }
    public void setGravity(int g) { }
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1, WRAP_CONTENT = -2;
        public int width, height;
        public LayoutParams(int w, int h) { width = w; height = h; }
        public LayoutParams(Object source) { }
        public void setMargins(int a, int b, int c, int d) { }
    }
}
