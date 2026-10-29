package android.graphics;

import java.util.ArrayList;
import java.util.List;

/** Stub testowy PathMeasure — mierzy długość konturów ścieżki. */
public class PathMeasure {
    private final List<float[]> pts;
    private int contour = 0;

    public PathMeasure(Path path, boolean forceClosed) {
        List<List<float[]>> cs = path.toContours(0.12f);
        pts = new ArrayList<float[]>();
        if (!cs.isEmpty()) pts.addAll(cs.get(0));
    }

    public float getLength() {
        float total = 0;
        for (int i = 1; i < pts.size(); i++)
            total += (float) Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[1] - pts.get(i - 1)[1]);
        return total;
    }

    public boolean getPosTan(float distance, float[] pos, float[] tan) {
        if (pts.isEmpty()) return false;
        if (distance <= 0) {
            pos[0] = pts.get(0)[0];
            pos[1] = pts.get(0)[1];
            return true;
        }
        float acc = 0;
        for (int i = 1; i < pts.size(); i++) {
            float dx = pts.get(i)[0] - pts.get(i - 1)[0];
            float dy = pts.get(i)[1] - pts.get(i - 1)[1];
            float d = (float) Math.hypot(dx, dy);
            if (acc + d >= distance) {
                float t = d <= 0 ? 0 : (distance - acc) / d;
                pos[0] = pts.get(i - 1)[0] + dx * t;
                pos[1] = pts.get(i - 1)[1] + dy * t;
                if (tan != null) {
                    tan[0] = dx;
                    tan[1] = dy;
                }
                return true;
            }
            acc += d;
        }
        pos[0] = pts.get(pts.size() - 1)[0];
        pos[1] = pts.get(pts.size() - 1)[1];
        return true;
    }

    public boolean nextContour() {
        return false;
    }
}
