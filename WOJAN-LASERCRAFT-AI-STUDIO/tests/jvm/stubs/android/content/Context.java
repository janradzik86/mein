package android.content;

import android.content.res.AssetManager;

/** Stub testowy kontekstu aplikacji (na urządzeniu: android.content.Context). */
public class Context {
    private static final AssetManager AM = new AssetManager();

    public AssetManager getAssets() {
        return AM;
    }

    public String getPackageName() {
        return "pl.wojan.lasercraft";
    }

    private final ContentResolver cr = new ContentResolver();

    public ContentResolver getContentResolver() {
        return cr;
    }

    public java.io.File getFilesDir() {
        java.io.File f = new java.io.File("/tmp/lc-jvm-files");
        f.mkdirs();
        return f;
    }

    public java.io.File getExternalFilesDir(String t) {
        return getFilesDir();
    }
}
