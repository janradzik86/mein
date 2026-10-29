package android.graphics;

/** Stub testowy — macierz afiniczna 2D. */
public class Matrix {
    public float a = 1, b = 0, c = 0, d = 1, tx = 0, ty = 0;

    public void setScale(float sx, float sy) {
        a = sx; d = sy;
        b = c = 0;
        tx = ty = 0;
    }

    public void postTranslate(float dx, float dy) {
        tx += dx;
        ty += dy;
    }

    public boolean postScale(float sx, float sy) {
        a *= sx; b *= sx; c *= sy; d *= sy;
        return true;
    }

    public boolean postRotate(float deg) {
        double r = Math.toRadians(deg);
        float cs = (float) Math.cos(r), sn = (float) Math.sin(r);
        float na = a * cs + c * sn, nb = b * cs + d * sn;
        float nc = -a * sn + c * cs, nd = -b * sn + d * cs;
        a = na; b = nb; c = nc; d = nd;
        return true;
    }

    public void mapPoints(float[] pts) {
        for (int i = 0; i + 1 < pts.length; i += 2) {
            float x = pts[i], y = pts[i + 1];
            pts[i] = a * x + c * y + tx;
            pts[i + 1] = b * x + d * y + ty;
        }
    }
}
