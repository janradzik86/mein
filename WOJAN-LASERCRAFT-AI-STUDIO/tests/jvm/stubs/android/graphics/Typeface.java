package android.graphics;

import android.content.res.AssetManager;

/** Stub testowy czcionki — na urządzeniu ładowany jest prawdziwy TTF z assets. */
public class Typeface {
    public static final Typeface DEFAULT = new Typeface();
    public static final Typeface DEFAULT_BOLD = new Typeface();

    public static Typeface createFromAsset(AssetManager mgr, String path) {
        return new Typeface();
    }
}
