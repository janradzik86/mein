package android.app;
import android.content.DialogInterface;
public class AlertDialog implements DialogInterface {
    public void dismiss() { }
    public void show() { android.view.View.LOG.add("dialog:show"); }
    public static class Builder {
        public Builder(android.content.Context c) { }
        public Builder setTitle(CharSequence t) { android.view.View.LOG.add("dialog.title:" + t); return this; }
        public Builder setTitle(int resId) { return this; }
        public Builder setMessage(CharSequence m) { android.view.View.LOG.add("dialog.message:" + m); return this; }
        public Builder setView(android.view.View v) { return this; }
        public Builder setItems(CharSequence[] items, DialogInterface.OnClickListener l) {
            android.view.View.LOG.add("dialog.items:" + (items == null ? 0 : items.length)); return this; }
        public Builder setItems(String[] items, DialogInterface.OnClickListener l) { return this; }
        public Builder setSingleChoiceItems(CharSequence[] items, int checked, DialogInterface.OnClickListener l) {
            android.view.View.LOG.add("dialog.single:" + (items == null ? 0 : items.length)); return this; }
        public Builder setSingleChoiceItems(String[] items, int checked, DialogInterface.OnClickListener l) { return this; }
        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener l) {
            android.view.View.LOG.add("dialog.pos:" + text); return this; }
        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener l) {
            android.view.View.LOG.add("dialog.neg:" + text); return this; }
        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener l) { return this; }
        public AlertDialog create() { return new AlertDialog(); }
        public AlertDialog show() { android.view.View.LOG.add("dialog:show"); return new AlertDialog(); }
    }
}
