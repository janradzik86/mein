package android.graphics;

import java.io.ByteArrayOutputStream;

/** Stub testowy bitmapy (bez prawdziwego rasteryzowania). */
public class Bitmap {

    public enum Config {ALPHA_8, ARGB_8888, RGB_565}

    public enum CompressFormat {PNG, JPEG, WEBP}

    private final int w, h;

    private Bitmap(int w, int h) {
        this.w = w;
        this.h = h;
    }

    public static Bitmap createBitmap(int w, int h, Config c) {
        return new Bitmap(Math.max(1, w), Math.max(1, h));
    }

    public static Bitmap createBitmap(int[] px, int w, int h, Config c) {
        return new Bitmap(Math.max(1, w), Math.max(1, h));
    }

    public static Bitmap createScaledBitmap(Bitmap src, int w, int h, boolean filter) {
        return new Bitmap(Math.max(1, w), Math.max(1, h));
    }

    public int getWidth() {
        return w;
    }

    public int getHeight() {
        return h;
    }

    public int getPixel(int x, int y) {
        return 0xFFFFFFFF;
    }

    public void recycle() {
    }

    public boolean compress(CompressFormat f, int q, ByteArrayOutputStream out) {
        out.write(new byte[]{1, 2, 3, 4}, 0, 4); // znacznik testowy
        return true;
    }
}
