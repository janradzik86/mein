package android.graphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Stub testowy android.graphics.Path — przechowuje segmenty i udostępnia
 * computeBounds/approximate z tą samą semantyką co API Androida
 * (trójki: ułamek długości konturu, x, y; ułamek zeruje się na początku konturu).
 */
public class Path {

    public enum Direction {CCW, CW}

    public enum FillType {WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD}

    public static class Seg {
        public int type;          // 0 move, 1 line, 2 quad, 3 cubic, 4 close
        public float[] p;

        Seg(int t, float... v) {
            type = t;
            p = v;
        }
    }

    public final List<Seg> segs = new ArrayList<Seg>();
    private FillType fill = FillType.WINDING;

    public void setFillType(FillType f) {
        fill = f;
    }

    public FillType getFillType() {
        return fill;
    }

    public void moveTo(float x, float y) {
        segs.add(new Seg(0, x, y));
    }

    public void lineTo(float x, float y) {
        segs.add(new Seg(1, x, y));
    }

    public void quadTo(float x1, float y1, float x2, float y2) {
        segs.add(new Seg(2, x1, y1, x2, y2));
    }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        segs.add(new Seg(3, x1, y1, x2, y2, x3, y3));
    }

    public void close() {
        segs.add(new Seg(4));
    }

    public void reset() {
        segs.clear();
    }

    public boolean isEmpty() {
        for (int i = 0; i < segs.size(); i++)
            if (segs.get(i).type != 0) return false;
        return true;
    }

    public void addPath(Path src) {
        segs.addAll(src.segs);
    }

    public void offset(float dx, float dy) {
        for (int i = 0; i < segs.size(); i++) {
            Seg s = segs.get(i);
            for (int k = 0; k < s.p.length; k += 2) {
                s.p[k] += dx;
                s.p[k + 1] += dy;
            }
        }
    }

    public void transform(Matrix m) {
        for (int i = 0; i < segs.size(); i++) m.mapPoints(segs.get(i).p);
    }

    public void addRect(float l, float t, float r, float b, Direction d) {
        moveTo(l, t);
        lineTo(r, t);
        lineTo(r, b);
        lineTo(l, b);
        close();
    }

    public void addRect(RectF rect, Direction d) {
        addRect(rect.left, rect.top, rect.right, rect.bottom, d);
    }

    public void addOval(RectF o, Direction d) {
        float cx = o.centerX(), cy = o.centerY();
        float rx = o.width() / 2, ry = o.height() / 2;
        moveTo(cx + rx, cy);
        for (int i = 1; i <= 16; i++) {
            double a = Math.PI * 2 * i / 16;
            lineTo((float) (cx + rx * Math.cos(a)), (float) (cy + ry * Math.sin(a)));
        }
        close();
    }

    public void addCircle(float cx, float cy, float r, Direction d) {
        addOval(new RectF(cx - r, cy - r, cx + r, cy + r), d);
    }

    public void addRoundRect(RectF r, float rx, float ry, Direction d) {
        addRect(r, d);
    }

    public void computeBounds(RectF out, boolean exact) {
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, rr = -Float.MAX_VALUE, bb = -Float.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < segs.size(); i++) {
            float[] p = segs.get(i).p;
            for (int k = 0; k + 1 < p.length; k += 2) {
                any = true;
                l = Math.min(l, p[k]);
                rr = Math.max(rr, p[k]);
                t = Math.min(t, p[k + 1]);
                bb = Math.max(bb, p[k + 1]);
            }
        }
        if (!any) {
            out.set(0, 0, 0, 0);
            return;
        }
        out.set(l, t, rr, bb);
    }

    public float[] approximate(float tolerance) {
        List<List<float[]>> contours = toContours(tolerance);
        List<Float> out = new ArrayList<Float>();
        for (int c = 0; c < contours.size(); c++) {
            List<float[]> pts = contours.get(c);
            if (pts.isEmpty()) continue;
            float total = 0;
            float[] cum = new float[pts.size()];
            for (int i = 1; i < pts.size(); i++) {
                total += dist(pts.get(i - 1), pts.get(i));
                cum[i] = total;
            }
            if (total <= 0) total = 1;
            for (int i = 0; i < pts.size(); i++) {
                out.add(cum[i] / total);
                out.add(pts.get(i)[0]);
                out.add(pts.get(i)[1]);
            }
        }
        float[] res = new float[out.size()];
        for (int i = 0; i < res.length; i++) res[i] = out.get(i);
        return res;
    }

    static float dist(float[] a, float[] b) {
        return (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    /** Kontury jako listy punktów (0..1 próbkowanie krzywych). */
    public List<List<float[]>> toContours(float tol) {
        List<List<float[]>> out = new ArrayList<List<float[]>>();
        List<float[]> cur = new ArrayList<float[]>();
        float cx = 0, cy = 0, sx = 0, sy = 0;
        int steps = tol < 0.2f ? 16 : (tol < 0.6f ? 8 : 4);
        for (int i = 0; i < segs.size(); i++) {
            Seg s = segs.get(i);
            switch (s.type) {
                case 0:
                    if (cur.size() > 1) out.add(cur);
                    cur = new ArrayList<float[]>();
                    cx = s.p[0];
                    cy = s.p[1];
                    sx = cx;
                    sy = cy;
                    cur.add(new float[]{cx, cy});
                    break;
                case 1:
                    cx = s.p[0];
                    cy = s.p[1];
                    cur.add(new float[]{cx, cy});
                    break;
                case 2: {
                    float x0 = cx, y0 = cy;
                    for (int k = 1; k <= steps; k++) {
                        float t = k / (float) steps;
                        float mt = 1 - t;
                        float x = mt * mt * x0 + 2 * mt * t * s.p[0] + t * t * s.p[2];
                        float y = mt * mt * y0 + 2 * mt * t * s.p[1] + t * t * s.p[3];
                        cur.add(new float[]{x, y});
                    }
                    cx = s.p[2];
                    cy = s.p[3];
                    break;
                }
                case 3: {
                    float x0 = cx, y0 = cy;
                    for (int k = 1; k <= steps; k++) {
                        float t = k / (float) steps;
                        float mt = 1 - t;
                        float x = mt * mt * mt * x0 + 3 * mt * mt * t * s.p[0]
                                + 3 * mt * t * t * s.p[2] + t * t * t * s.p[4];
                        float y = mt * mt * mt * y0 + 3 * mt * mt * t * s.p[1]
                                + 3 * mt * t * t * s.p[3] + t * t * t * s.p[5];
                        cur.add(new float[]{x, y});
                    }
                    cx = s.p[4];
                    cy = s.p[5];
                    break;
                }
                default:
                    if (!cur.isEmpty()) {
                        cur.add(new float[]{sx, sy});
                        out.add(cur);
                        cur = new ArrayList<float[]>();
                        cur.add(new float[]{sx, sy});
                    }
                    cx = sx;
                    cy = sy;
                    break;
            }
        }
        if (cur.size() > 1) out.add(cur);
        return out;
    }
}
