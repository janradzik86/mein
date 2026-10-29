package android.net;

/** Stub testowy. */
public class Uri {
    private final String s;

    private Uri(String s) {
        this.s = s;
    }

    public static Uri parse(String s) {
        return new Uri(s);
    }

    public static Uri fromFile(java.io.File f) {
        return new Uri("file://" + f.getAbsolutePath());
    }

    public String getLastPathSegment() {
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    public String toString() {
        return s;
    }
}
