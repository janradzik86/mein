package android.graphics;
public class Typeface {
    public static final int NORMAL = 0, BOLD = 1, ITALIC = 2, BOLD_ITALIC = 3;
    private static final Typeface INSTANCE = new Typeface();
    public static Typeface create(String family, int style) { return INSTANCE; }
}
