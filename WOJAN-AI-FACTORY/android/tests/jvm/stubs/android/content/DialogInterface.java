package android.content;
public interface DialogInterface {
    int BUTTON_POSITIVE = -1, BUTTON_NEGATIVE = -2, BUTTON_NEUTRAL = -3;
    void dismiss();
    interface OnClickListener { void onClick(DialogInterface dialog, int which); }
    interface OnMultiChoiceClickListener { void onClick(DialogInterface dialog, int which, boolean isChecked); }
}
