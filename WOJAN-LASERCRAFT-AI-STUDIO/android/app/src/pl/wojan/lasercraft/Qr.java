package pl.wojan.lasercraft;

import android.graphics.Path;

/**
 * Generator kodów QR (Android) — tryb bajtowy, wersje 1–10, poziomy L/M/Q/H.
 * Ta sama implementacja algorytmu co w silniku Windows (ISO/IEC 18004):
 * kodowanie → Reed–Solomon → przeplot → wzory funkcyjne → maska → informacja formatu.
 */
public final class Qr {

    public static class Code {
        public int size, version, mask;
        public char ecc;
        public boolean[] modules;
        public boolean ok;
        public String error = "";

        public boolean at(int x, int y) {
            return x >= 0 && y >= 0 && x < size && y < size && modules[y * size + x];
        }
    }

    // totalCodewords, eccPerBlock, b1, d1, b2, d2 dla wersji 1..10 i poziomów L,M,Q,H
    private static final int[][] SPEC = {
            {26, 7, 1, 19, 0, 0, 26, 10, 1, 16, 0, 0, 26, 13, 1, 13, 0, 0, 26, 17, 1, 9, 0, 0},
            {44, 10, 1, 34, 0, 0, 44, 16, 1, 28, 0, 0, 44, 22, 1, 22, 0, 0, 44, 28, 1, 16, 0, 0},
            {70, 15, 1, 55, 0, 0, 70, 26, 1, 44, 0, 0, 70, 18, 2, 17, 0, 0, 70, 22, 2, 13, 0, 0},
            {100, 20, 1, 80, 0, 0, 100, 18, 2, 32, 0, 0, 100, 26, 2, 24, 0, 0, 100, 16, 4, 9, 0, 0},
            {134, 26, 1, 108, 0, 0, 134, 24, 2, 43, 0, 0, 134, 18, 2, 15, 2, 16, 134, 22, 2, 11, 2, 12},
            {172, 18, 2, 68, 0, 0, 172, 16, 4, 27, 0, 0, 172, 24, 4, 19, 0, 0, 172, 28, 4, 15, 0, 0},
            {196, 20, 2, 78, 0, 0, 196, 18, 4, 31, 0, 0, 196, 18, 2, 14, 4, 15, 196, 26, 4, 13, 1, 14},
            {242, 24, 2, 97, 0, 0, 242, 22, 2, 38, 2, 39, 242, 22, 4, 18, 2, 19, 242, 26, 4, 14, 2, 15},
            {292, 30, 2, 116, 0, 0, 292, 22, 3, 36, 2, 37, 292, 20, 4, 16, 4, 17, 292, 24, 4, 12, 4, 13},
            {346, 18, 2, 68, 2, 69, 346, 26, 4, 43, 1, 44, 346, 24, 6, 19, 2, 20, 346, 28, 6, 15, 2, 16}
    };

    private static int eccIndex(char ecc) {
        if (ecc == 'L') return 0;
        if (ecc == 'M') return 1;
        if (ecc == 'Q') return 2;
        return 3;
    }

    private static int specValue(int ver, char ecc, int offset) {
        int idx = eccIndex(ecc) * 6 + offset;
        return SPEC[ver - 1][idx];
    }

    static int dataCodewords(int ver, char ecc) {
        return specValue(ver, ecc, 2) * specValue(ver, ecc, 3) + specValue(ver, ecc, 4) * specValue(ver, ecc, 5);
    }

    private static int gfMul(int a, int b) {
        int r = 0;
        while (b != 0) {
            if ((b & 1) != 0) r ^= a;
            b >>= 1;
            a = ((a << 1) ^ ((a & 0x80) != 0 ? 0x1D : 0)) & 0xFF;
        }
        return r & 0xFF;
    }

    private static int[] rsGenerator(int degree) {
        int[] g = new int[degree];
        g[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                g[j] = gfMul(g[j], root);
                if (j + 1 < degree) g[j] ^= g[j + 1];
            }
            root = gfMul(root, 2);
        }
        return g;
    }

    private static int[] rsRemainder(int[] data, int[] gen) {
        int[] res = new int[gen.length];
        for (int i = 0; i < data.length; i++) {
            int factor = data[i] ^ res[0];
            for (int k = 0; k + 1 < res.length; k++) res[k] = res[k + 1];
            res[res.length - 1] = 0;
            for (int k = 0; k < gen.length; k++) res[k] ^= gfMul(gen[k], factor);
        }
        return res;
    }

    private static int[] alignmentPositions(int ver) {
        if (ver == 1) return new int[0];
        int num = ver / 7 + 2;
        int step = (ver == 32) ? 26 : ((ver * 4 + num * 2 + 1) / (num * 2 - 2)) * 2;
        int[] res = new int[num];
        res[0] = 6;
        int pos = ver * 4 + 10;
        for (int i = num - 1; i >= 1; i--) {
            res[i] = pos;
            pos -= step;
        }
        return res;
    }

    private static boolean maskBit(int mask, int x, int y) {
        switch (mask) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (x / 3 + y / 2) % 2 == 0;
            case 5: return (x * y) % 2 + (x * y) % 3 == 0;
            case 6: return ((x * y) % 2 + (x * y) % 3) % 2 == 0;
            case 7: return ((x + y) % 2 + (x * y) % 3) % 2 == 0;
        }
        return false;
    }

    public static Code encode(String data, char ecc) {
        Code out = new Code();
        out.ecc = ecc;
        if (data == null || data.isEmpty()) {
            out.error = "Brak danych do zakodowania.";
            return out;
        }
        byte[] bytes = toUtf8Bytes(data);
        int ver = 0;
        for (int v = 1; v <= 10; v++) {
            int countBits = v >= 10 ? 16 : 8;
            long need = 4 + countBits + (long) bytes.length * 8;
            if (need <= (long) dataCodewords(v, ecc) * 8) { ver = v; break; }
        }
        if (ver == 0) {
            out.error = "Dane za długie na wersję 10.";
            return out;
        }
        out.version = ver;
        int dataCw = dataCodewords(ver, ecc);
        int countBits = ver >= 10 ? 16 : 8;
        int capacity = dataCw * 8;
        int[] bits = new int[capacity];
        int bp = 0;
        bp = pushBits(bits, bp, 0x4, 4);
        bp = pushBits(bits, bp, bytes.length, countBits);
        for (int i = 0; i < bytes.length; i++) bp = pushBits(bits, bp, bytes[i] & 0xFF, 8);
        int term = Math.min(4, capacity - bp);
        for (int i = 0; i < term; i++) bits[bp++] = 0;
        while (bp % 8 != 0) bits[bp++] = 0;
        int[] dataCws = new int[dataCw];
        int cwi = 0;
        for (int i = 0; i + 8 <= bp; i += 8) {
            int b = 0;
            for (int k = 0; k < 8; k++) b = (b << 1) | bits[i + k];
            dataCws[cwi++] = b;
        }
        int pad = 0xEC;
        while (cwi < dataCw) {
            dataCws[cwi++] = pad;
            pad = pad == 0xEC ? 0x11 : 0xEC;
        }

        int eccPerBlock = specValue(ver, ecc, 1);
        int b1 = specValue(ver, ecc, 2), d1 = specValue(ver, ecc, 3);
        int b2 = specValue(ver, ecc, 4), d2 = specValue(ver, ecc, 5);
        int numBlocks = b1 + b2;
        int[] gen = rsGenerator(eccPerBlock);
        int[][] dataBlocks = new int[numBlocks][];
        int[][] eccBlocks = new int[numBlocks][];
        int pos = 0;
        int maxData = 0;
        for (int b = 0; b < numBlocks; b++) {
            int len = b < b1 ? d1 : d2;
            int[] blk = new int[len];
            for (int i = 0; i < len; i++) blk[i] = dataCws[pos++];
            dataBlocks[b] = blk;
            eccBlocks[b] = rsRemainder(blk, gen);
            if (len > maxData) maxData = len;
        }
        int[] finalCw = new int[specValue(ver, ecc, 0)];
        int fi = 0;
        for (int i = 0; i < maxData; i++)
            for (int b = 0; b < numBlocks; b++)
                if (i < dataBlocks[b].length) finalCw[fi++] = dataBlocks[b][i];
        for (int i = 0; i < eccPerBlock; i++)
            for (int b = 0; b < numBlocks; b++) finalCw[fi++] = eccBlocks[b][i];

        int size = ver * 4 + 17;
        int bestScore = Integer.MAX_VALUE;
        boolean[] best = null;
        int bestMask = 0;
        for (int mask = 0; mask <= 7; mask++) {
            boolean[] m = new boolean[size * size];
            boolean[] fn = new boolean[size * size];
            drawFunctionPatterns(m, fn, size, ver);
            drawFormat(m, fn, size, ecc, mask);
            placeData(m, fn, size, finalCw);
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                    if (!fn[y * size + x] && maskBit(mask, x, y)) m[y * size + x] = !m[y * size + x];
            int score = penalty(m, size);
            if (score < bestScore) {
                bestScore = score;
                best = m;
                bestMask = mask;
            }
        }
        if (best == null) {
            out.error = "Błąd wewnętrzny kodera QR.";
            return out;
        }
        out.size = size;
        out.modules = best;
        out.mask = bestMask;
        out.ok = true;
        return out;
    }

    private static int pushBits(int[] bits, int pos, int val, int count) {
        for (int i = count - 1; i >= 0; i--) bits[pos++] = (val >> i) & 1;
        return pos;
    }

    private static void set(boolean[] m, boolean[] fn, int size, int x, int y, boolean dark, boolean func) {
        if (x < 0 || y < 0 || x >= size || y >= size) return;
        m[y * size + x] = dark;
        if (func) fn[y * size + x] = true;
    }

    private static void drawFunctionPatterns(boolean[] m, boolean[] fn, int size, int ver) {
        for (int i = 0; i < size; i++) {
            set(m, fn, size, 6, i, i % 2 == 0, true);
            set(m, fn, size, i, 6, i % 2 == 0, true);
        }
        finder(m, fn, size, 3, 3);
        finder(m, fn, size, size - 4, 3);
        finder(m, fn, size, 3, size - 4);
        int[] pos = alignmentPositions(ver);
        for (int i = 0; i < pos.length; i++)
            for (int j = 0; j < pos.length; j++) {
                boolean skip = (i == 0 && j == 0) || (i == 0 && j == pos.length - 1)
                        || (i == pos.length - 1 && j == 0);
                if (skip) continue;
                for (int dy = -2; dy <= 2; dy++)
                    for (int dx = -2; dx <= 2; dx++)
                        set(m, fn, size, pos[j] + dx, pos[i] + dy,
                                Math.max(Math.abs(dx), Math.abs(dy)) != 1, true);
            }
        if (ver >= 7) {
            int rem = ver;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >> 11) * 0x1F25);
            int bits = (ver << 12) | rem;
            for (int i = 0; i < 18; i++) {
                boolean bit = ((bits >> i) & 1) != 0;
                int a = size - 11 + i % 3, b = i / 3;
                set(m, fn, size, a, b, bit, true);
                set(m, fn, size, b, a, bit, true);
            }
        }
    }

    private static void finder(boolean[] m, boolean[] fn, int size, int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                set(m, fn, size, cx + dx, cy + dy, dist != 2 && dist != 4, true);
            }
    }

    private static void drawFormat(boolean[] m, boolean[] fn, int size, char ecc, int mask) {
        int ecl = ecc == 'L' ? 1 : ecc == 'M' ? 0 : ecc == 'Q' ? 3 : 2;
        int data = (ecl << 3) | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(m, fn, size, 8, i, bitAt(bits, i), true);
        set(m, fn, size, 8, 7, bitAt(bits, 6), true);
        set(m, fn, size, 8, 8, bitAt(bits, 7), true);
        set(m, fn, size, 7, 8, bitAt(bits, 8), true);
        for (int i = 9; i < 15; i++) set(m, fn, size, 14 - i, 8, bitAt(bits, i), true);
        for (int i = 0; i < 8; i++) set(m, fn, size, size - 1 - i, 8, bitAt(bits, i), true);
        for (int i = 8; i < 15; i++) set(m, fn, size, 8, size - 15 + i, bitAt(bits, i), true);
        set(m, fn, size, 8, size - 8, true, true);
    }

    private static boolean bitAt(int v, int i) {
        return ((v >> i) & 1) != 0;
    }

    private static void placeData(boolean[] m, boolean[] fn, int size, int[] codewords) {
        int bitIdx = 0;
        int total = codewords.length * 8;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (fn[y * size + x]) continue;
                    boolean bit = false;
                    if (bitIdx < total) {
                        int cw = codewords[bitIdx >> 3];
                        bit = ((cw >> (7 - (bitIdx & 7))) & 1) != 0;
                    }
                    m[y * size + x] = bit;
                    bitIdx++;
                }
            }
        }
    }

    private static int penalty(boolean[] m, int size) {
        int score = 0;
        for (int y = 0; y < size; y++) {
            int run = 1;
            for (int x = 1; x < size; x++) {
                if (m[y * size + x] == m[y * size + x - 1]) run++;
                else { if (run >= 5) score += 3 + (run - 5); run = 1; }
            }
            if (run >= 5) score += 3 + (run - 5);
        }
        for (int x = 0; x < size; x++) {
            int run = 1;
            for (int y = 1; y < size; y++) {
                if (m[y * size + x] == m[(y - 1) * size + x]) run++;
                else { if (run >= 5) score += 3 + (run - 5); run = 1; }
            }
            if (run >= 5) score += 3 + (run - 5);
        }
        for (int y = 0; y + 1 < size; y++)
            for (int x = 0; x + 1 < size; x++) {
                boolean v = m[y * size + x];
                if (v == m[y * size + x + 1] && v == m[(y + 1) * size + x] && v == m[(y + 1) * size + x + 1])
                    score += 3;
            }
        int dark = 0;
        for (int i = 0; i < m.length; i++) if (m[i]) dark++;
        int pct = dark * 100 / (size * size);
        score += (Math.abs(pct - 50) / 5) * 10;
        return score;
    }

    private static byte[] toUtf8Bytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (Exception e) {
            return s.getBytes();
        }
    }

    public static int modulesFor(String data, char ecc) {
        Code c = encode(data, ecc);
        return c.ok ? c.size : 0;
    }

    public static String selfCheckText(String data, char ecc) {
        Code c = encode(data, ecc);
        if (!c.ok) return "QR: błąd — " + c.error;
        if (!c.at(8, c.size - 8)) return "QR: brak modułu ciemnego";
        int cd = 0, total = c.size * c.size;
        for (int i = 0; i < c.modules.length; i++) if (c.modules[i]) cd++;
        double ratio = (double) cd / total;
        if (ratio < 0.3 || ratio > 0.7) return "QR: podejrzany stosunek ciemnych modułów";
        return "QR: OK (wersja " + c.version + ", poziom " + c.ecc + ", modułów " + c.size
                + ", maska " + c.mask + ")";
    }


    /** Test strukturalny: wzory pozycjonujące, synchronizacja, informacja formatu (BCH) i wersji. */
    public static String structuralCheck(String data, char ecc) {
        Code c = encode(data, ecc);
        if (!c.ok) return "BŁĄD: " + c.error;
        int size = c.size;
        StringBuilder sb = new StringBuilder();
        int fails = 0;
        int[][] centers = {{3, 3}, {size - 4, 3}, {3, size - 4}};
        for (int k = 0; k < centers.length; k++) {
            boolean ok = true;
            for (int dy = -3; dy <= 3; dy++)
                for (int dx = -3; dx <= 3; dx++) {
                    int dist = Math.max(Math.abs(dx), Math.abs(dy));
                    boolean expect = dist != 2;
                    if (c.at(centers[k][0] + dx, centers[k][1] + dy) != expect) ok = false;
                }
            if (!ok) {
                sb.append("wzór pozycjonujący ").append(k + 1).append(": BŁĄD\n");
                fails++;
            }
        }
        boolean timing = true;
        for (int i = 8; i < size - 8; i++) {
            if (c.at(i, 6) != (i % 2 == 0)) timing = false;
            if (c.at(6, i) != (i % 2 == 0)) timing = false;
        }
        if (!timing) {
            sb.append("wzory synchronizacji: BŁĄD\n");
            fails++;
        }
        // informacja formatu — kopie A i B muszą być zgodne i przechodzić BCH
        int a = 0, b = 0;
        for (int i = 0; i <= 5; i++) a |= (c.at(8, i) ? 1 : 0) << i;
        a |= (c.at(8, 7) ? 1 : 0) << 6;
        a |= (c.at(8, 8) ? 1 : 0) << 7;
        a |= (c.at(7, 8) ? 1 : 0) << 8;
        for (int i = 9; i < 15; i++) a |= (c.at(14 - i, 8) ? 1 : 0) << i;
        for (int i = 0; i < 8; i++) b |= (c.at(size - 1 - i, 8) ? 1 : 0) << i;
        for (int i = 8; i < 15; i++) b |= (c.at(8, size - 15 + i) ? 1 : 0) << i;
        if (a != b) {
            sb.append("informacja formatu: kopie różne (A=").append(a).append(", B=").append(b).append(")\n");
            fails++;
        }
        int val = a ^ 0x5412;
        int data5 = val >> 10;
        int rem = data5;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >> 9) * 0x537);
        if ((data5 << 10 | (rem & 0x3FF)) != val) {
            sb.append("informacja formatu: BCH nie zgadza się\n");
            fails++;
        }
        int maskBits = data5 & 7;
        if (maskBits != c.mask) {
            sb.append("maska w nagłówku (").append(maskBits).append(") ≠ maska macierzy (")
                    .append(c.mask).append(")\n");
            fails++;
        }
        int dark = 0;
        for (int i = 0; i < c.modules.length; i++) if (c.modules[i]) dark++;
        double ratio = 100.0 * dark / (size * size);
        if (ratio < 30 || ratio > 70) {
            sb.append("udział modułów ciemnych ").append(fmt(ratio)).append("% poza zakresem\n");
            fails++;
        }
        String head = "QR " + (fails == 0 ? "OK" : "BŁĄD") + " — wersja " + c.version + ", poziom " + c.ecc
                + ", " + size + "×" + size + " modułów, maska " + c.mask + ", ciemnych "
                + fmt(ratio) + "%\n";
        return head + sb.toString();
    }

    static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    /** Ścieżka wektorowa QR w milimetrach (jeden prostokąt na moduł, scalane poziomo). */
    public static Path path(String data, double sizeMm, char ecc) {
        Path p = new Path();
        Code c = encode(data, ecc);
        if (!c.ok) return p;
        int quiet = 4;
        int total = c.size + quiet * 2;
        float mm = (float) (sizeMm / total);
        for (int y = 0; y < c.size; y++) {
            int x = 0;
            while (x < c.size) {
                if (!c.at(x, y)) { x++; continue; }
                int start = x;
                while (x < c.size && c.at(x, y)) x++;
                p.addRect((start + quiet) * mm, (y + quiet) * mm, (x + quiet) * mm, (y + quiet + 1) * mm,
                        Path.Direction.CW);
            }
        }
        return p;
    }
}
