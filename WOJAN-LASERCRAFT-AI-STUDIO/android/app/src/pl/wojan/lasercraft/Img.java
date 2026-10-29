package pl.wojan.lasercraft;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.net.Uri;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Import i obróbka bitmap: skalowanie, kadrowanie, usuwanie tła,
 * progowanie, dithering (Floyd–Steinberg / uporządkowany) oraz wektoryzacja konturowa.
 * Wszystko lokalnie, bez bibliotek zewnętrznych.
 */
public final class Img {

    public static final int MAX_WORK = 480; // px — rozdzielczość robocza wektoryzacji

    public static class Mask {
        public int w, h;
        public boolean[] px;
        public int dark; // liczba ciemnych pikseli

        public Mask(int w, int h) {
            this.w = w;
            this.h = h;
            this.px = new boolean[Math.max(1, w * h)];
        }

        public boolean at(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h && px[y * w + x];
        }

        public void set(int x, int y, boolean v) {
            if (x < 0 || y < 0 || x >= w || y >= h) return;
            px[y * w + x] = v;
        }
    }

    public static Bitmap load(Context ctx, Uri uri, int maxSide) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            InputStream in = ctx.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, o);
            if (in != null) in.close();
            int sample = 1;
            int big = Math.max(o.outWidth, o.outHeight);
            while (big / sample > maxSide * 2) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
            in = ctx.getContentResolver().openInputStream(uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, o2);
            if (in != null) in.close();
            if (b == null) return null;
            if (Math.max(b.getWidth(), b.getHeight()) > maxSide) {
                float k = maxSide / (float) Math.max(b.getWidth(), b.getHeight());
                b = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)),
                        Math.max(1, Math.round(b.getHeight() * k)), true);
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** Obraz w skali szarości 0..255 (płaska tablica — bez tablic dwuwymiarowych). */
    public static class Gray {
        public final int w, h;
        public final int[] px;

        Gray(int w, int h) {
            this.w = w;
            this.h = h;
            this.px = new int[Math.max(1, w * h)];
        }

        public int at(int x, int y) {
            return px[y * w + x];
        }

        public void set(int x, int y, int v) {
            px[y * w + x] = v;
        }

        public Gray copy() {
            Gray g = new Gray(w, h);
            System.arraycopy(px, 0, g.px, 0, px.length);
            return g;
        }
    }

    /** Skala szarości 0..255 (z uwzględnieniem alfa — przezroczyste tło = białe). */
    public static Gray gray(Bitmap b, int outW) {
        int w = Math.max(4, outW);
        int h = Math.max(4, Math.round(b.getHeight() * (w / (float) b.getWidth())));
        Bitmap s = Bitmap.createScaledBitmap(b, w, h, true);
        Gray g = new Gray(w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = s.getPixel(x, y);
                int a = (c >>> 24) & 0xFF;
                int r = (c >> 16) & 0xFF, gg = (c >> 8) & 0xFF, bb = c & 0xFF;
                int lum = (r * 299 + gg * 587 + bb * 114) / 1000;
                // przezroczystość → tło (białe)
                lum = (lum * a + 255 * (255 - a)) / 255;
                g.set(x, y, lum);
            }
        if (s != b) s.recycle();
        return g;
    }

    /** Usuwa tło: zalewanie od krawędzi pikseli jaśniejszych niż próg → biel. */
    public static void removeBackground(Gray g, int thr) {
        int h = g.h, w = g.w;
        boolean[] seen = new boolean[w * h];
        ArrayQueue q = new ArrayQueue(w * h);
        for (int x = 0; x < w; x++) {
            if (g.at(x, 0) >= thr && !seen[x]) { seen[x] = true; q.add(x, 0); }
            if (g.at(x, h - 1) >= thr && !seen[(h - 1) * w + x]) { seen[(h - 1) * w + x] = true; q.add(x, h - 1); }
        }
        for (int y = 0; y < h; y++) {
            if (g.at(0, y) >= thr && !seen[y * w]) { seen[y * w] = true; q.add(0, y); }
            if (g.at(w - 1, y) >= thr && !seen[y * w + w - 1]) { seen[y * w + w - 1] = true; q.add(w - 1, y); }
        }
        while (!q.empty()) {
            int x = q.x(), y = q.y();
            q.pop();
            g.set(x, y, 255);
            int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
            for (int k = 0; k < 4; k++) {
                int nx = x + dx[k], ny = y + dy[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                if (seen[ny * w + nx] || g.at(nx, ny) < thr) continue;
                seen[ny * w + nx] = true;
                q.add(nx, ny);
            }
        }
    }

    public static int[] levels() {
        return new int[]{2, 3, 4, 6, 8};
    }

    /** Progowanie z podaną liczbą poziomów (1 = czarno-białe). */
    public static Mask threshold(Gray g, int level, int levelsCount) {
        int h = g.h, w = g.w;
        Mask m = new Mask(w, h);
        int n = Math.max(1, levelsCount);
        int step = 255 / n;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int v = g.at(x, y);
                int quant = Math.min(n - 1, v / Math.max(1, step));
                int qv = quant * step;
                boolean dark = (n == 1) ? v < level : qv < 255 / 2;
                m.px[y * w + x] = dark;
                if (dark) m.dark++;
            }
        return m;
    }

    public static Mask dither(Gray g, boolean ordered) {
        int h = g.h, w = g.w;
        Mask m = new Mask(w, h);
        if (ordered) {
            int[][] bayer = {
                    {0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int t = bayer[y % 4][x % 4] * 16 + 8;
                    boolean dark = g.at(x, y) < t;
                    m.px[y * w + x] = dark;
                    if (dark) m.dark++;
                }
            return m;
        }
        Gray buf = g.copy();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int old = buf.at(x, y);
                boolean dark = old < 128;
                m.px[y * w + x] = dark;
                if (dark) m.dark++;
                int err = old - (dark ? 0 : 255);
                if (x + 1 < w) buf.set(x + 1, y, buf.at(x + 1, y) + err * 7 / 16);
                if (y + 1 < h) {
                    if (x > 0) buf.set(x - 1, y + 1, buf.at(x - 1, y + 1) + err * 3 / 16);
                    buf.set(x, y + 1, buf.at(x, y + 1) + err * 5 / 16);
                    if (x + 1 < w) buf.set(x + 1, y + 1, buf.at(x + 1, y + 1) + err * 1 / 16);
                }
            }
        return m;
    }

    public static Bitmap maskToBitmap(Mask m) {
        int[] px = new int[m.w * m.h];
        for (int i = 0; i < px.length; i++) px[i] = m.px[i] ? Color.BLACK : Color.WHITE;
        return Bitmap.createBitmap(px, m.w, m.h, Bitmap.Config.ARGB_8888);
    }

    /**
     * Wektoryzacja konturowa: krawędzie między pikselami ciemnymi i jasnymi
     * łańcuchowane w zamknięte pętle, uproszczone algorytmem Douglasa–Peuckera.
     * Zwraca ścieżkę w milimetrach (oś Y w dół, tak jak w SVG).
     */
    public static Path vectorize(Mask m, double mmW, double mmH, double tolerancePx) {
        int w = m.w, h = m.h;
        double sx = mmW / w, sy = mmH / h;
        Path p = new Path();
        Map<Long, List<long[]>> edges = new HashMap<Long, List<long[]>>();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if (!m.at(x, y)) continue;
                if (!m.at(x, y - 1)) addEdge(edges, x, y, x + 1, y);
                if (!m.at(x + 1, y)) addEdge(edges, x + 1, y, x + 1, y + 1);
                if (!m.at(x, y + 1)) addEdge(edges, x + 1, y + 1, x, y + 1);
                if (!m.at(x - 1, y)) addEdge(edges, x, y + 1, x, y);
            }
        int used = 0;
        for (List<long[]> l : edges.values()) used += l.size();
        int guard = used + 4;
        while (!edges.isEmpty() && guard-- > 0) {
            long[] first = null;
            Long firstKey = null;
            for (Map.Entry<Long, List<long[]>> e : edges.entrySet()) {
                if (!e.getValue().isEmpty()) { first = e.getValue().remove(0); firstKey = e.getKey(); break; }
            }
            if (first == null) break;
            List<float[]> pts = new ArrayList<float[]>();
            pts.add(new float[]{(float) first[0], (float) first[1]});
            long cx = first[2], cy = first[3];
            int steps = 0;
            while (steps++ < used + 8) {
                List<long[]> next = edges.get(key(cx, cy));
                if (next == null || next.isEmpty()) break;
                long[] e = next.remove(0);
                pts.add(new float[]{(float) cx, (float) cy});
                cx = e[2];
                cy = e[3];
                if (cx == first[0] && cy == first[1]) break;
            }
            if (pts.size() < 3) continue;
            List<float[]> simp = simplify(pts, (float) tolerancePx);
            if (simp.size() < 3) continue;
            p.moveTo(simp.get(0)[0] * (float) sx, simp.get(0)[1] * (float) sy);
            for (int i = 1; i < simp.size(); i++)
                p.lineTo(simp.get(i)[0] * (float) sx, simp.get(i)[1] * (float) sy);
            p.close();
        }
        return p;
    }

    private static long key(long x, long y) {
        return (y << 20) | x;
    }

    private static void addEdge(Map<Long, List<long[]>> map, long x0, long y0, long x1, long y1) {
        Long k = key(x0, y0);
        List<long[]> l = map.get(k);
        if (l == null) {
            l = new ArrayList<long[]>();
            map.put(k, l);
        }
        l.add(new long[]{x0, y0, x1, y1});
    }

    /** Douglas–Peucker na otwartej polilinii (zamknięcie traktowane jako pętla). */
    public static List<float[]> simplify(List<float[]> pts, float tol) {
        boolean[] keep = new boolean[pts.size()];
        keep[0] = true;
        keep[pts.size() - 1] = true;
        dp(pts, 0, pts.size() - 1, tol, keep);
        List<float[]> out = new ArrayList<float[]>();
        for (int i = 0; i < pts.size(); i++) if (keep[i]) out.add(pts.get(i));
        return out;
    }

    private static void dp(List<float[]> p, int a, int b, float tol, boolean[] keep) {
        float maxD = -1;
        int idx = -1;
        float ax = p.get(a)[0], ay = p.get(a)[1], bx = p.get(b)[0], by = p.get(b)[1];
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        for (int i = a + 1; i < b; i++) {
            float px = p.get(i)[0], py = p.get(i)[1];
            float d;
            if (len < 1e-6f) d = (float) Math.hypot(px - ax, py - ay);
            else d = Math.abs(dy * px - dx * py + bx * ay - by * ax) / len;
            if (d > maxD) { maxD = d; idx = i; }
        }
        if (maxD > tol && idx > 0) {
            keep[idx] = true;
            dp(p, a, idx, tol, keep);
            dp(p, idx, b, tol, keep);
        }
    }

    /** Prosta kolejka FIFO na int[] (bez autoboxingu, szybsza niż LinkedList). */
    static final class ArrayQueue {
        private final int[] bx, by;
        private int head = 0, tail = 0;

        ArrayQueue(int cap) {
            bx = new int[cap];
            by = new int[cap];
        }

        boolean empty() {
            return head >= tail;
        }

        void add(int x, int y) {
            if (tail < bx.length) {
                bx[tail] = x;
                by[tail] = y;
                tail++;
            }
        }

        int x() { return bx[head]; }

        int y() { return by[head]; }

        void pop() { head++; }
    }
}
