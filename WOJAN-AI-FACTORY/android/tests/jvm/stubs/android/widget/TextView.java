package android.widget;
import android.view.View;
public class TextView extends View {
    protected CharSequence text = "";
    public TextView(android.content.Context c) { super(c); }
    public void setText(CharSequence t) { this.text = t; LOG.add("setText:" + t); }
    public void setText(int resid) { LOG.add("setText:res"); }
    public CharSequence getText() { return text; }
    public void setTextColor(int color) { }
    public void setTextSize(float size) { }
    public void setTextSize(int unit, float size) { }
    public void setTypeface(android.graphics.Typeface tf) { }
    public void setTextIsSelectable(boolean b) { }
    public void setGravity(int g) { }
    public void setSingleLine(boolean b) { }
    public void setAllCaps(boolean b) { }
}
