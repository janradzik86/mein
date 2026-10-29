package android.graphics;

/**
 * Stub testowy Paint: miara tekstu liczona proporcjonalnie do rozmiaru,
 * a getTextPath tworzy prostokąty zamiast konturów TTF (na urządzeniu: prawdziwe ścieżki czcionki).
 */
public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public static final int FILTER_BITMAP_FLAG = 2;

    public enum Style {FILL, STROKE, FILL_AND_STROKE}

    public enum Cap {BUTT, ROUND, SQUARE}

    public enum Join {MITER, ROUND, BEVEL}

    private float size = 10;
    private Style style = Style.FILL;
    private int color = 0xFF000000;
    private float strokeW = 1;
    private float letterSpacing = 0;
    private int alpha = 255;

    public Paint() {
    }

    public Paint(int flags) {
    }

    public void setAntiAlias(boolean b) {
    }

    public void setFilterBitmap(boolean b) {
    }

    public void setTypeface(Typeface t) {
    }

    public void setTextSize(float s) {
        size = s;
    }

    public void setLetterSpacing(float s) {
        letterSpacing = s;
    }

    public void setStyle(Style s) {
        style = s;
    }

    public Style getStyle() {
        return style;
    }

    public void setColor(int c) {
        color = c;
    }

    public int getColor() {
        return color;
    }

    public void setStrokeWidth(float w) {
        strokeW = w;
    }

    public float getStrokeWidth() {
        return strokeW;
    }

    public void setStrokeCap(Cap c) {
    }

    public void setStrokeJoin(Join j) {
    }

    public void setPathEffect(Object e) {
    }

    public void setAlpha(int a) {
        alpha = a;
    }

    public float measureText(String s) {
        return s == null ? 0 : (float) (s.length() * size * 0.55);
    }

    public void getTextPath(String text, int start, int end, float x, float y, Path path) {
        // przybliżenie: każdy znak jako prostokąt o wysokości rozmiaru czcionki
        float w = size * 0.55f;
        float cx = x;
        for (int i = start; i < end; i++) {
            path.addRect(cx, y - size * 0.72f, cx + w * 0.86f, y, Path.Direction.CW);
            cx += w + letterSpacing;
        }
    }
}
