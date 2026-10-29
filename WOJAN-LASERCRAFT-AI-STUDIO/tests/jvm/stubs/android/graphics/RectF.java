package android.graphics;

/** Stub testowy (JVM) — odwzorowanie semantyki android.graphics.RectF. */
public class RectF {
    public float left, top, right, bottom;

    public RectF() {
    }

    public RectF(float l, float t, float r, float b) {
        left = l; top = t; right = r; bottom = b;
    }

    public void set(float l, float t, float r, float b) {
        left = l; top = t; right = r; bottom = b;
    }

    public float width() {
        return right - left;
    }

    public float height() {
        return bottom - top;
    }

    public float centerX() {
        return (left + right) / 2;
    }

    public float centerY() {
        return (top + bottom) / 2;
    }

    public boolean contains(float x, float y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public boolean contains(RectF r) {
        return r.left >= left && r.right <= right && r.top >= top && r.bottom <= bottom;
    }

    public void union(float x, float y) {
        if (x < left) left = x;
        if (x > right) right = x;
        if (y < top) top = y;
        if (y > bottom) bottom = y;
    }
}
