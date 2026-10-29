package test;

import pl.wojan.lasercraft.Qr;

/**
 * Niezależny dekoder macierzy QR (test): odczyt informacji formatu, zdjęcie maski,
 * wypakowanie kodu z przeplotem, weryfikacja syndromów Reeda-Solomona i odczyt ładunku.
 * Napisany od nowa (nie korzysta z kodu kodera poza samą macierzą), aby wykrywać
 * błędy rozmieszczenia modułów, przeplotu i masek.
 */
public final class QrDecode {

    public static String decode(Qr.Code c) {
        int size = c.size;
        // 1. informacja formatu (kopia A) → poziom i maska
        int fmt = 0;
        for (int i = 0; i <= 5; i++) fmt |= (c.at(8, i) ? 1 : 0) << i;
        fmt |= (c.at(8, 7) ? 1 : 0) << 6;
        fmt |= (c.at(8, 8) ? 1 : 0) << 7;
        fmt |= (c.at(7, 8) ? 1 : 0) << 8;
        for (int i = 9; i < 15; i++) fmt |= (c.at(14 - i, 8) ? 1 : 0) << i;
        int val = fmt ^ 0x5412;
        int eclBits = (val >> 13) & 3;
        char ecc = eclBits == 1 ? 'L' : eclBits == 0 ? 'M' : eclBits == 3 ? 'Q' : 'H';
        int mask = (val >> 10) & 7;

        // 2. mapa modułów funkcyjnych (wzory, informacja formatu, wersja)
        boolean[] fn = functionMap(size, c.version);

        // 3. odczyt bitów danych w zygzak + zdjęcie maski
        int totalBits = 0;
        for (int i = 0; i < fn.length; i++) if (!fn[i]) totalBits++;
        int[] bits = new int[totalBits];
        int bi = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (fn[y * size + x]) continue;
                    boolean bit = c.at(x, y);
                    if (maskBit(mask, x, y)) bit = !bit;
                    if (bi < bits.length) bits[bi++] = bit ? 1 : 0;
                }
            }
        }
        int nCw = totalBits / 8;
        int[] cw = new int[nCw];
        for (int i = 0; i < nCw; i++) {
            int b = 0;
            for (int k = 0; k < 8; k++) b = (b << 1) | bits[i * 8 + k];
            cw[i] = b;
        }

        // 4. parametry bloków (niezależna tabela w teście)
        int[] p = spec(c.version, ecc);
        int eccPerBlock = p[0], b1 = p[1], d1 = p[2], b2 = p[3], d2 = p[4];
        int nBlocks = b1 + b2;
        int maxData = Math.max(b1 > 0 ? d1 : 0, b2 > 0 ? d2 : 0);
        int[][] data = new int[nBlocks][];
        int[][] ec = new int[nBlocks][];
        for (int b = 0; b < nBlocks; b++) {
            data[b] = new int[b < b1 ? d1 : d2];
            ec[b] = new int[eccPerBlock];
        }
        int idx = 0;
        for (int i = 0; i < maxData; i++)
            for (int b = 0; b < nBlocks; b++)
                if (i < data[b].length) data[b][i] = cw[idx++];
        for (int i = 0; i < eccPerBlock; i++)
            for (int b = 0; b < nBlocks; b++) ec[b][i] = cw[idx++];

        // 5. syndromy RS — muszą być zerowe
        for (int b = 0; b < nBlocks; b++) {
            int[] full = new int[data[b].length + eccPerBlock];
            System.arraycopy(data[b], 0, full, 0, data[b].length);
            System.arraycopy(ec[b], 0, full, data[b].length, eccPerBlock);
            // pierwiastki generatora QR: α^0 … α^(n−1)
            for (int s = 0; s < eccPerBlock; s++) {
                int alpha = gfPow(2, s);   // punkt oceny: α^s
                int syn = 0;
                for (int k = 0; k < full.length; k++) {
                    syn ^= gfMul(full[k], gfPow(alpha, full.length - 1 - k));
                }
                if (syn != 0) return "SYNDROM s=" + (b + 1) + "/" + s + " ≠ 0";
            }
        }

        // 6. strumień danych: tryb + długość + bajty
        int[] stream = new int[nBlocks * maxData];
        int sp = 0;
        for (int b = 0; b < nBlocks; b++)
            for (int k = 0; k < data[b].length; k++) stream[sp++] = data[b][k];
        int[] bitsAll = new int[sp * 8];
        for (int i = 0; i < sp; i++)
            for (int k = 0; k < 8; k++) bitsAll[i * 8 + k] = (stream[i] >> (7 - k)) & 1;
        int mode = grab(bitsAll, 0, 4);
        if (mode != 4) return "tryb ≠ bajtowy (" + mode + ")";
        int cb = c.version >= 10 ? 16 : 8;
        int len = grab(bitsAll, 4, cb);
        byte[] out = new byte[len];
        int pos = 4 + cb;
        for (int i = 0; i < len; i++) {
            out[i] = (byte) grab(bitsAll, pos, 8);
            pos += 8;
        }
        try {
            return "OK:" + new String(out, "UTF-8");
        } catch (Exception e) {
            return "OK:<błąd kodowania>";
        }
    }

    static int grab(int[] b, int from, int n) {
        int v = 0;
        for (int i = 0; i < n; i++) v = (v << 1) | (from + i < b.length ? b[from + i] : 0);
        return v;
    }

    static int[] spec(int ver, char ecc) {
        // {ecc, b1, d1, b2, d2} dla wersji 1..10
        int[][][] t = {
                {{7, 1, 19, 0, 0}, {10, 1, 16, 0, 0}, {13, 1, 13, 0, 0}, {17, 1, 9, 0, 0}},
                {{10, 1, 34, 0, 0}, {16, 1, 28, 0, 0}, {22, 1, 22, 0, 0}, {28, 1, 16, 0, 0}},
                {{15, 1, 55, 0, 0}, {26, 1, 44, 0, 0}, {18, 2, 17, 0, 0}, {22, 2, 13, 0, 0}},
                {{20, 1, 80, 0, 0}, {18, 2, 32, 0, 0}, {26, 2, 24, 0, 0}, {16, 4, 9, 0, 0}},
                {{26, 1, 108, 0, 0}, {24, 2, 43, 0, 0}, {18, 2, 15, 2, 16}, {22, 2, 11, 2, 12}},
                {{18, 2, 68, 0, 0}, {16, 4, 27, 0, 0}, {24, 4, 19, 0, 0}, {28, 4, 15, 0, 0}},
                {{20, 2, 78, 0, 0}, {18, 4, 31, 0, 0}, {18, 2, 14, 4, 15}, {26, 4, 13, 1, 14}},
                {{24, 2, 97, 0, 0}, {22, 2, 38, 2, 39}, {22, 4, 18, 2, 19}, {26, 4, 14, 2, 15}},
                {{30, 2, 116, 0, 0}, {22, 3, 36, 2, 37}, {20, 4, 16, 4, 17}, {24, 4, 12, 4, 13}},
                {{18, 2, 68, 2, 69}, {26, 4, 43, 1, 44}, {24, 6, 19, 2, 20}, {28, 6, 15, 2, 16}}
        };
        int i = ecc == 'L' ? 0 : ecc == 'M' ? 1 : ecc == 'Q' ? 2 : 3;
        return t[ver - 1][i];
    }

    static boolean[] functionMap(int size, int ver) {
        boolean[] fn = new boolean[size * size];
        // wzory pozycjonujące + separatory
        markFinder(fn, size, 3, 3);
        markFinder(fn, size, size - 4, 3);
        markFinder(fn, size, 3, size - 4);
        // synchronizacja
        for (int i = 0; i < size; i++) {
            fn[6 * size + i] = true;
            fn[i * size + 6] = true;
        }
        // wzory dopasowania
        int[] pos = alignment(ver);
        for (int i = 0; i < pos.length; i++)
            for (int j = 0; j < pos.length; j++) {
                boolean skip = (i == 0 && j == 0) || (i == 0 && j == pos.length - 1)
                        || (i == pos.length - 1 && j == 0);
                if (skip) continue;
                for (int dy = -2; dy <= 2; dy++)
                    for (int dx = -2; dx <= 2; dx++) {
                        int x = pos[j] + dx, y = pos[i] + dy;
                        if (x >= 0 && y >= 0 && x < size && y < size) fn[y * size + x] = true;
                    }
            }
        // informacja formatu
        for (int i = 0; i <= 5; i++) fn[i * size + 8] = true;
        fn[7 * size + 8] = true;
        fn[8 * size + 8] = true;
        fn[8 * size + 7] = true;
        for (int i = 9; i < 15; i++) fn[8 * size + (14 - i)] = true;
        for (int i = 0; i < 8; i++) fn[8 * size + (size - 1 - i)] = true;
        for (int i = 8; i < 15; i++) fn[(size - 15 + i) * size + 8] = true;
        fn[(size - 8) * size + 8] = true;
        if (ver >= 7) {
            for (int i = 0; i < 18; i++) {
                int a = size - 11 + i % 3, b = i / 3;
                fn[b * size + a] = true;
                fn[a * size + b] = true;
            }
        }
        return fn;
    }

    static void markFinder(boolean[] fn, int size, int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -4; dx <= 4; dx++) {
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && y >= 0 && x < size && y < size) fn[y * size + x] = true;
            }
    }

    static int[] alignment(int ver) {
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

    static boolean maskBit(int mask, int x, int y) {
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

    static int gfMul(int a, int b) {
        int r = 0;
        while (b != 0) {
            if ((b & 1) != 0) r ^= a;
            b >>= 1;
            a = ((a << 1) ^ ((a & 0x80) != 0 ? 0x1D : 0)) & 0xFF;
        }
        return r & 0xFF;
    }

    static int gfPow(int a, int n) {
        int r = 1, base = a & 0xFF;
        while (n > 0) {
            if ((n & 1) != 0) r = gfMul(r, base);
            base = gfMul(base, base);
            n >>= 1;
        }
        return r;
    }
}
