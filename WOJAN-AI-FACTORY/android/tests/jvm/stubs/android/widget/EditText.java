package android.widget;
public class EditText extends TextView {
    public EditText(android.content.Context c) { super(c); }
    public void setHint(CharSequence h) { LOG.add("hint:" + h); }
    public void setHint(int resid) { }
    public void setHintTextColor(int c) { }
    public void setInputType(int t) { }
    public void setSelection(int i) { }
}
