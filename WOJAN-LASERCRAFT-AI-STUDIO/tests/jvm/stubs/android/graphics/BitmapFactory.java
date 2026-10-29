package android.graphics;

import java.io.InputStream;

/** Stub testowy — bez dekodowania obrazów. */
public class BitmapFactory {

    public static class Options {
        public boolean inJustDecodeBounds;
        public int inSampleSize = 1;
        public Bitmap.Config inPreferredConfig;
        public int outWidth, outHeight;
    }

    public static Bitmap decodeFile(String path) {
        return null;
    }

    public static Bitmap decodeFile(String path, Options o) {
        return null;
    }

    public static Bitmap decodeStream(InputStream in) {
        return null;
    }

    public static Bitmap decodeStream(InputStream in, RectF out, Options o) {
        return null;
    }
}
