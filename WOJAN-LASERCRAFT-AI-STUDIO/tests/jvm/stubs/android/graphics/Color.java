package android.graphics;

/** Stub testowy. */
public class Color {
    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int RED = 0xFFFF0000;
    public static final int MAGENTA = 0xFFFF00FF;
    public static final int TRANSPARENT = 0;

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int parseColor(String s) {
        if (s == null) return BLACK;
        String t = s.trim();
        if (t.startsWith("#")) {
            String h = t.substring(1);
            if (h.length() == 6) return (int) (0xFF000000L | Long.parseLong(h, 16));
            if (h.length() == 8) return (int) Long.parseLong(h, 16);
        }
        return BLACK;
    }

    public static int alpha(int c) {
        return (c >>> 24) & 0xFF;
    }

    public static int red(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int green(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }
}
