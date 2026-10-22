package android.view;
import java.util.ArrayList;
import java.util.List;
public class View {
    public static final List<String> LOG = new ArrayList<String>();
    public static final int VISIBLE = 0, INVISIBLE = 4, GONE = 8;
    protected ContextRef ctx;
    public View(android.content.Context c) { }
    public void setLayoutParams(Object lp) { }
    public void setPadding(int a, int b, int c, int d) { }
    public void setBackgroundColor(int color) { }
    public void setOnClickListener(OnClickListener l) { }
    public void setOnLongClickListener(OnLongClickListener l) { }
    public void setVisibility(int v) { }
    public void setEnabled(boolean b) { }
    public interface OnClickListener { void onClick(View v); }
    public interface OnLongClickListener { boolean onLongClick(View v); }
    static class ContextRef { }
}
