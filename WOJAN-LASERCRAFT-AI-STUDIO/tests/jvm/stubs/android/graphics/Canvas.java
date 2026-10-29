package android.graphics;

/** Stub testowy kanwy — liczy wywołania rysowania (bez rasteryzacji). */
public class Canvas {
    public static int drawCalls = 0;

    public Canvas(Bitmap b) {
    }

    public void drawColor(int c) {
        drawCalls++;
    }

    public void drawPath(Path p, Paint paint) {
        drawCalls++;
    }

    public void drawRect(RectF r, Paint p) {
        drawCalls++;
    }

    public void drawRect(float l, float t, float r, float b, Paint p) {
        drawCalls++;
    }

    public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        drawCalls++;
    }

    public void drawText(String s, float x, float y, Paint p) {
        drawCalls++;
    }

    public void drawBitmap(Bitmap b, RectF src, RectF dst, Paint p) {
        drawCalls++;
    }

    public void drawBitmap(Bitmap b, float x, float y, Paint p) {
        drawCalls++;
    }

    public int save() {
        return 0;
    }

    public void restore() {
    }

    public void translate(float x, float y) {
    }

    public void scale(float sx, float sy) {
    }
}
