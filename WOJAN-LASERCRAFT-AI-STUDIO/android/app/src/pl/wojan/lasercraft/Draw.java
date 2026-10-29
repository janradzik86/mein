package pl.wojan.lasercraft;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Rysowanie i eksport geometrii (Android): ścieżki w milimetrach,
 * teksty jako prawdziwe ścieżki czcionek (Paint.getTextPath),
 * eksport SVG/DXF z tych samych ścieżek co podgląd.
 */
public final class Draw {

    // ---------------- czcionki ----------------
    private static final String[] FONT_IDS = {"anton", "bebas", "cinzel", "cinzel_deco", "playfair",
            "oswald", "abril", "pirata", "barlow"};
    private static final String[] FONT_FILES = {"Anton-Regular.ttf", "BebasNeue-Regular.ttf", "Cinzel.ttf",
            "CinzelDecorative-Regular.ttf", "PlayfairDisplay.ttf", "Oswald.ttf", "AbrilFatface-Regular.ttf",
            "PirataOne-Regular.ttf", "BarlowCondensed-Regular.ttf"};
    private static final Map<String, Typeface> TYPE_CACHE = new HashMap<String, Typeface>();

    public static Typeface typeface(Context ctx, String id) {
        Typeface cached = TYPE_CACHE.get(id);
        if (cached != null) return cached;
        int idx = 0;
        for (int i = 0; i < FONT_IDS.length; i++) if (FONT_IDS[i].equals(id)) idx = i;
        Typeface tf = null;
        try {
            tf = Typeface.createFromAsset(ctx.getAssets(), "fonts/" + FONT_FILES[idx]);
        } catch (Exception e) {
            tf = Typeface.DEFAULT_BOLD;
        }
        TYPE_CACHE.put(id, tf);
        return tf;
    }

    /** Tekst → ścieżka w milimetrach (punkt (0,0) = początek linii bazowej, wyrównanie do x). */
    public static Path textPath(Context ctx, String text, String fontId, float sizeMm, float tracking,
                               String align, float baselineX, float baselineY) {
        Path p = new Path();
        if (text == null || text.isEmpty()) return p;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(typeface(ctx, fontId));
        paint.setTextSize(Math.max(0.5f, sizeMm));
        paint.setLetterSpacing(0f);
        float width = paint.measureText(text) + Math.max(0, text.length() - 1) * tracking;
        float x = baselineX;
        if ("center".equals(align)) x -= width / 2f;
        else if ("right".equals(align)) x -= width;
        if (tracking <= 0.01f) {
            paint.getTextPath(text, 0, text.length(), x, baselineY, p);
        } else {
            Path tmp = new Path();
            float cx = x;
            for (int i = 0; i < text.length(); i++) {
                String ch = text.substring(i, i + 1);
                paint.getTextPath(ch, 0, ch.length(), cx, baselineY, tmp);
                p.addPath(tmp);
                tmp.reset();
                cx += paint.measureText(ch) + tracking;
            }
        }
        return p;
    }

    public static float measureText(Context ctx, String text, String fontId, float sizeMm) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(typeface(ctx, fontId));
        paint.setTextSize(Math.max(0.5f, sizeMm));
        return paint.measureText(text == null ? "" : text);
    }

    // ---------------- dostawca ścieżek ----------------
    public static class Provider implements PathProvider {
        private final Context ctx;

        public Provider(Context ctx) {
            this.ctx = ctx;
        }

        public Path pathFor(Core.Obj o) {
            Path p = new Path();
            if (o == null) return p;
            if ("raster".equals(o.type) || Core.L_ENGRAVE_RASTER.equals(o.layer)) return p;
            if ("text".equals(o.type)) {
                p = textPath(ctx, o.text, o.font, (float) o.size, (float) o.tracking, o.align,
                        (float) o.x, (float) o.y);
                return p;
            }
            if ("qr".equals(o.type)) {
                p = Qr.path(o.qrData, o.qrSize, 'M');
                p.offset((float) o.x, (float) o.y);
                return p;
            }
            if (o.d != null && !o.d.isEmpty()) {
                p = parseSvgPath(o.d);
            }
            if (o.x != 0 || o.y != 0) p.offset((float) o.x, (float) o.y);
            if (Math.abs(o.sx - 1) > 1e-6 || Math.abs(o.sy - 1) > 1e-6 || Math.abs(o.rot) > 1e-6) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postScale((float) o.sx, (float) o.sy);
                m.postRotate((float) o.rot);
                p.transform(m);
            }
            return p;
        }
    }

    /** Parser ścieżek SVG (M, L, C, Q, Z) — zgodny z formatem .wlcproj z Windows. */
    public static Path parseSvgPath(String d) {
        Path p = new Path();
        if (d == null || d.isEmpty()) return p;
        java.util.ArrayList<Float> nums = new java.util.ArrayList<Float>();
        char cmd = 'M';
        int i = 0;
        float curX = 0, curY = 0, startX = 0, startY = 0;
        while (i < d.length()) {
            char c = d.charAt(i);
            if (Character.isLetter(c)) {
                cmd = c;
                i++;
                if (c == 'Z' || c == 'z') {
                    p.close();
                    curX = startX;
                    curY = startY;
                }
                continue;
            }
            if (c == ' ' || c == ',') { i++; continue; }
            int st = i;
            if (d.charAt(i) == '-' || d.charAt(i) == '+') i++;
            boolean dot = false;
            while (i < d.length()) {
                char ch = d.charAt(i);
                if (Character.isDigit(ch)) { i++; continue; }
                if (ch == '.' && !dot) { dot = true; i++; continue; }
                if ((ch == 'e' || ch == 'E') && i + 1 < d.length()) {
                    i++;
                    if (d.charAt(i) == '-' || d.charAt(i) == '+') i++;
                    continue;
                }
                break;
            }
            if (i == st) { i++; continue; }
            float v;
            try {
                v = Float.parseFloat(d.substring(st, i));
            } catch (Exception e) {
                continue;
            }
            nums.add(Float.valueOf(v));
            if (cmd == 'M' || cmd == 'L') {
                if (nums.size() >= 2) {
                    float x = nums.get(0).floatValue(), y = nums.get(1).floatValue();
                    if (cmd == 'M') {
                        p.moveTo(x, y);
                        startX = x;
                        startY = y;
                        cmd = 'L';
                    } else {
                        p.lineTo(x, y);
                    }
                    curX = x;
                    curY = y;
                    nums.clear();
                }
            } else if (cmd == 'C') {
                if (nums.size() >= 6) {
                    p.cubicTo(nums.get(0).floatValue(), nums.get(1).floatValue(),
                            nums.get(2).floatValue(), nums.get(3).floatValue(),
                            nums.get(4).floatValue(), nums.get(5).floatValue());
                    curX = nums.get(4).floatValue();
                    curY = nums.get(5).floatValue();
                    nums.clear();
                }
            } else if (cmd == 'Q') {
                if (nums.size() >= 4) {
                    p.quadTo(nums.get(0).floatValue(), nums.get(1).floatValue(),
                            nums.get(2).floatValue(), nums.get(3).floatValue());
                    curX = nums.get(2).floatValue();
                    curY = nums.get(3).floatValue();
                    nums.clear();
                }
            } else {
                nums.clear();
            }
        }
        p.isEmpty();
        return p;
    }

    // ---------------- kształty produktów ----------------
    public static Path productShape(String shape, double w, double h, double r) {
        Path p = new Path();
        float W = (float) w, H = (float) h, R = (float) r;
        if ("circle".equals(shape)) {
            p.addCircle(W / 2, H / 2, Math.min(W, H) / 2, Path.Direction.CW);
        } else if ("oval".equals(shape)) {
            p.addOval(new RectF(0, 0, W, H), Path.Direction.CW);
        } else if ("shield".equals(shape)) {
            p.moveTo(W * 0.5f, 0);
            p.lineTo(W, H * 0.18f);
            p.lineTo(W * 0.92f, H * 0.70f);
            p.quadTo(W * 0.5f, H, W * 0.08f, H * 0.70f);
            p.lineTo(0, H * 0.18f);
            p.close();
        } else if ("tag".equals(shape)) {
            // Zawieszka: kontur z zaokrąglonymi narożnikami.
            // Otwór na sznurek jest osobnym obiektem warstwy CUT_INNER (patrz Gen.applySpec),
            // dzięki temu w LightBurn/LaserGRBL cięcie wewnętrzne idzie przed obrysem.
            p.addRoundRect(new RectF(0, 0, W, H), R, R, Path.Direction.CW);
        } else if ("rect".equals(shape)) {
            p.addRect(0, 0, W, H, Path.Direction.CW);
        } else {
            p.addRoundRect(new RectF(0, 0, W, H), R, R, Path.Direction.CW);
        }
        return p;
    }

    // ---------------- ramki ----------------
    public static Path frame(String style, RectF a, float t) {
        Path p = new Path();
        if ("none".equals(style)) return p;
        if ("simple".equals(style) || "retro".equals(style)) {
            p.addRoundRect(new RectF(a.left + 0.6f, a.top + 0.6f, a.right - 0.6f, a.bottom - 0.6f),
                    1.5f, 1.5f, Path.Direction.CW);
            return p;
        }
        if ("double".equals(style)) {
            p.addRect(a.left, a.top, a.right, a.bottom, Path.Direction.CW);
            float k = Math.min(a.width(), a.height()) * 0.05f + 1.5f;
            p.addRect(a.left + k, a.top + k, a.right - k, a.bottom - k, Path.Direction.CW);
            return p;
        }
        // ornate / gothic: podwójna ramka + ornamenty narożne
        p.addRect(a.left, a.top, a.right, a.bottom, Path.Direction.CW);
        float k = 2.5f;
        p.addRect(a.left + k, a.top + k, a.right - k, a.bottom - k, Path.Direction.CW);
        float s = Math.min(a.width(), a.height()) * 0.12f;
        for (int corner = 0; corner < 4; corner++) {
            float cx = (corner == 0 || corner == 3) ? a.left + k : a.right - k;
            float cy = (corner < 2) ? a.top + k : a.bottom - k;
            float dx = (corner == 0 || corner == 3) ? 1 : -1;
            float dy = (corner < 2) ? 1 : -1;
            p.moveTo(cx, cy + dy * s);
            p.quadTo(cx, cy, cx + dx * s, cy);
            p.moveTo(cx + dx * s * 0.35f, cy + dy * s * 0.35f);
            p.quadTo(cx + dx * s * 0.55f, cy + dy * s * 0.15f, cx + dx * s * 0.7f, cy + dy * s * 0.35f);
        }
        return p;
    }

    // ---------------- emblematy (generator proceduralny) ----------------
    public static Path emblem(String id, RectF a) {
        Path p = new Path();
        if (a == null || a.width() <= 0 || a.height() <= 0) return p;
        float cx = a.centerX(), cy = a.centerY();
        float s = Math.min(a.width(), a.height());
        float r = s * 0.46f;
        if (id == null) id = "kolo";
        if (id.equals("serce")) {
            p.moveTo(cx, cy + r * 0.75f);
            p.cubicTo(cx - r * 1.35f, cy - r * 0.25f, cx - r * 0.55f, cy - r * 1.05f, cx, cy - r * 0.35f);
            p.cubicTo(cx + r * 0.55f, cy - r * 1.05f, cx + r * 1.35f, cy - r * 0.25f, cx, cy + r * 0.75f);
            p.close();
        } else if (id.equals("kolo")) {
            p.addCircle(cx, cy, r, Path.Direction.CW);
            p.addCircle(cx, cy, r * 0.78f, Path.Direction.CCW);
            for (int i = 0; i < 24; i++) {
                double ang = Math.PI * 2 * i / 24;
                p.addRect(cx + (float) Math.cos(ang) * r * 0.82f, cy + (float) Math.sin(ang) * r * 0.82f,
                        cx + (float) Math.cos(ang) * r - 0.6f, cy + (float) Math.sin(ang) * r + 0.6f,
                        Path.Direction.CW);
            }
        } else if (id.equals("drzewo")) {
            p.moveTo(cx - r, cy + r * 0.9f);
            p.lineTo(cx + r, cy + r * 0.9f);
            p.lineTo(cx + r * 0.55f, cy + r * 0.55f);
            p.lineTo(cx + r * 0.8f, cy + r * 0.55f);
            p.lineTo(cx + r * 0.45f, cy + r * 0.15f);
            p.lineTo(cx + r * 0.65f, cy + r * 0.15f);
            p.lineTo(cx, cy - r);
            p.lineTo(cx - r * 0.65f, cy + r * 0.15f);
            p.lineTo(cx - r * 0.45f, cy + r * 0.15f);
            p.lineTo(cx - r * 0.8f, cy + r * 0.55f);
            p.lineTo(cx - r * 0.55f, cy + r * 0.55f);
            p.close();
        } else if (id.equals("gory")) {
            p.moveTo(cx - r, cy + r * 0.7f);
            p.lineTo(cx - r * 0.35f, cy - r * 0.5f);
            p.lineTo(cx - r * 0.05f, cy + r * 0.1f);
            p.lineTo(cx + r * 0.35f, cy - r * 0.9f);
            p.lineTo(cx + r, cy + r * 0.7f);
            p.close();
        } else if (id.equals("ryba")) {
            // kontur ciała (pysk z lewej, ogon z prawej)
            p.moveTo(cx - r, cy);
            p.cubicTo(cx - r * 0.72f, cy - r * 0.62f, cx - r * 0.15f, cy - r * 0.78f, cx + r * 0.25f, cy - r * 0.62f);
            p.lineTo(cx + r * 0.62f, cy - r * 1.0f);
            p.lineTo(cx + r * 0.52f, cy);
            p.lineTo(cx + r * 0.62f, cy + r * 1.0f);
            p.lineTo(cx + r * 0.25f, cy + r * 0.62f);
            p.cubicTo(cx - r * 0.15f, cy + r * 0.78f, cx - r * 0.72f, cy + r * 0.62f, cx - r, cy);
            p.close();
            // skrzela
            p.moveTo(cx - r * 0.62f, cy - r * 0.55f);
            p.quadTo(cx - r * 0.40f, cy, cx - r * 0.62f, cy + r * 0.55f);
            // oko
            p.addCircle(cx - r * 0.78f, cy - r * 0.16f, r * 0.09f, Path.Direction.CW);
            // płetwa grzbietowa
            p.moveTo(cx - r * 0.05f, cy - r * 0.68f);
            p.quadTo(cx + r * 0.10f, cy - r * 1.18f, cx + r * 0.34f, cy - r * 0.66f);
            // płetwa piersiowa
            p.moveTo(cx - r * 0.30f, cy + r * 0.60f);
            p.quadTo(cx - r * 0.22f, cy + r * 1.02f, cx + r * 0.02f, cy + r * 0.58f);
            // łuski
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    float sx = cx + r * (-0.30f + col * 0.26f);
                    float sy = cy + r * (0.24f * (row - 1));
                    p.moveTo(sx, sy - r * 0.10f);
                    p.quadTo(sx + r * 0.09f, sy, sx, sy + r * 0.10f);
                }
            }
        } else if (id.equals("wilk")) {
            // głowa wilka z przodu: uszy, czoło, policzki, kufa
            p.moveTo(cx - r * 0.72f, cy + r * 0.04f);
            p.cubicTo(cx - r * 0.70f, cy - r * 0.36f, cx - r * 0.66f, cy - r * 0.62f, cx - r * 0.60f, cy - r * 0.80f);
            p.cubicTo(cx - r * 0.46f, cy - r * 0.56f, cx - r * 0.34f, cy - r * 0.44f, cx - r * 0.22f, cy - r * 0.46f);
            p.quadTo(cx, cy - r * 0.36f, cx + r * 0.22f, cy - r * 0.46f);
            p.cubicTo(cx + r * 0.34f, cy - r * 0.44f, cx + r * 0.46f, cy - r * 0.56f, cx + r * 0.60f, cy - r * 0.80f);
            p.cubicTo(cx + r * 0.66f, cy - r * 0.62f, cx + r * 0.70f, cy - r * 0.36f, cx + r * 0.72f, cy + r * 0.04f);
            p.cubicTo(cx + r * 0.66f, cy + r * 0.44f, cx + r * 0.30f, cy + r * 0.66f, cx, cy + r * 0.88f);
            p.cubicTo(cx - r * 0.30f, cy + r * 0.66f, cx - r * 0.66f, cy + r * 0.44f, cx - r * 0.72f, cy + r * 0.04f);
            p.close();
            // wewnętrzne krawędzie uszu
            p.moveTo(cx - r * 0.50f, cy - r * 0.60f);
            p.lineTo(cx - r * 0.52f, cy - r * 0.74f);
            p.moveTo(cx + r * 0.50f, cy - r * 0.60f);
            p.lineTo(cx + r * 0.52f, cy - r * 0.74f);
            // oczy (skośne)
            p.moveTo(cx - r * 0.38f, cy - r * 0.12f);
            p.lineTo(cx - r * 0.16f, cy - r * 0.18f);
            p.lineTo(cx - r * 0.22f, cy + r * 0.04f);
            p.close();
            p.moveTo(cx + r * 0.38f, cy - r * 0.12f);
            p.lineTo(cx + r * 0.16f, cy - r * 0.18f);
            p.lineTo(cx + r * 0.22f, cy + r * 0.04f);
            p.close();
            // nos
            p.moveTo(cx - r * 0.10f, cy + r * 0.30f);
            p.lineTo(cx + r * 0.10f, cy + r * 0.30f);
            p.lineTo(cx, cy + r * 0.42f);
            p.close();
            // kufa
            p.moveTo(cx, cy + r * 0.42f);
            p.lineTo(cx, cy + r * 0.52f);
            p.moveTo(cx - r * 0.12f, cy + r * 0.52f);
            p.quadTo(cx, cy + r * 0.62f, cx + r * 0.12f, cy + r * 0.52f);
        } else if (id.equals("jelen")) {
            p.moveTo(cx - r * 0.28f, cy - r * 0.15f);
            p.quadTo(cx - r * 0.45f, cy + r * 0.5f, cx, cy + r * 0.85f);
            p.quadTo(cx + r * 0.45f, cy + r * 0.5f, cx + r * 0.28f, cy - r * 0.15f);
            p.close();
            for (int side = -1; side <= 1; side += 2) {
                float bx = cx + side * r * 0.2f;
                p.moveTo(bx, cy - r * 0.15f);
                p.quadTo(bx + side * r * 0.2f, cy - r * 0.7f, bx + side * r * 0.05f, cy - r);
                p.moveTo(bx + side * r * 0.1f, cy - r * 0.45f);
                p.lineTo(bx + side * r * 0.45f, cy - r * 0.62f);
                p.moveTo(bx + side * r * 0.1f, cy - r * 0.7f);
                p.lineTo(bx + side * r * 0.35f, cy - r * 0.95f);
            }
        } else if (id.equals("kotwica")) {
            p.addCircle(cx, cy - r * 0.75f, r * 0.16f, Path.Direction.CW);
            p.moveTo(cx, cy - r * 0.6f);
            p.lineTo(cx, cy + r * 0.75f);
            p.moveTo(cx - r * 0.35f, cy - r * 0.25f);
            p.lineTo(cx + r * 0.35f, cy - r * 0.25f);
            p.moveTo(cx - r * 0.75f, cy + r * 0.2f);
            p.quadTo(cx, cy + r * 1.15f, cx + r * 0.75f, cy + r * 0.2f);
        } else if (id.equals("dynia")) {
            p.addOval(new RectF(cx - r, cy - r * 0.8f, cx + r, cy + r * 0.85f), Path.Direction.CW);
            for (int i = -2; i <= 2; i++) {
                p.moveTo(cx + i * r * 0.32f, cy - r * 0.78f);
                p.quadTo(cx + i * r * 0.42f, cy, cx + i * r * 0.32f, cy + r * 0.82f);
            }
            p.addRect(cx - r * 0.08f, cy - r * 1.1f, cx + r * 0.08f, cy - r * 0.8f, Path.Direction.CW);
        } else if (id.equals("czaszka")) {
            p.addOval(new RectF(cx - r * 0.8f, cy - r, cx + r * 0.8f, cy + r * 0.55f), Path.Direction.CW);
            p.addRect(cx - r * 0.35f, cy + r * 0.3f, cx + r * 0.35f, cy + r * 0.95f, Path.Direction.CW);
            Path eyes = new Path();
            eyes.addCircle(cx - r * 0.32f, cy - r * 0.15f, r * 0.2f, Path.Direction.CW);
            eyes.addCircle(cx + r * 0.32f, cy - r * 0.15f, r * 0.2f, Path.Direction.CW);
            p.addPath(eyes);
        } else if (id.equals("gitara")) {
            p.addOval(new RectF(cx - r * 0.7f, cy - r * 0.2f, cx + r * 0.7f, cy + r * 0.95f), Path.Direction.CW);
            p.addOval(new RectF(cx - r * 0.5f, cy - r * 0.7f, cx + r * 0.5f, cy + r * 0.35f), Path.Direction.CW);
            p.addRect(cx - r * 0.12f, cy - r * 1.15f, cx + r * 0.12f, cy + r * 0.1f, Path.Direction.CW);
        } else if (id.equals("choinka")) {
            // choinka: trzy poziomy gałęzi, pień, gwiazda
            p.moveTo(cx, cy - r * 0.98f);
            p.lineTo(cx + r * 0.34f, cy - r * 0.44f);
            p.lineTo(cx + r * 0.17f, cy - r * 0.44f);
            p.lineTo(cx + r * 0.46f, cy + r * 0.02f);
            p.lineTo(cx + r * 0.24f, cy + r * 0.02f);
            p.lineTo(cx + r * 0.62f, cy + r * 0.56f);
            p.lineTo(cx + r * 0.10f, cy + r * 0.56f);
            p.lineTo(cx + r * 0.10f, cy + r * 0.86f);
            p.lineTo(cx - r * 0.10f, cy + r * 0.86f);
            p.lineTo(cx - r * 0.10f, cy + r * 0.56f);
            p.lineTo(cx - r * 0.62f, cy + r * 0.56f);
            p.lineTo(cx - r * 0.24f, cy + r * 0.02f);
            p.lineTo(cx - r * 0.46f, cy + r * 0.02f);
            p.lineTo(cx - r * 0.17f, cy - r * 0.44f);
            p.lineTo(cx - r * 0.34f, cy - r * 0.44f);
            p.close();
            // gwiazda na czubku
            for (int i = 0; i < 10; i++) {
                double ang = -Math.PI / 2 + Math.PI * i / 5.0;
                float rad = (i % 2 == 0) ? r * 0.14f : r * 0.06f;
                float x = cx + (float) Math.cos(ang) * rad;
                float y = (cy - r * 0.98f) + (float) Math.sin(ang) * rad;
                if (i == 0) p.moveTo(x, y);
                else p.lineTo(x, y);
            }
            p.close();
            // bombki
            p.addCircle(cx - r * 0.26f, cy + r * 0.26f, r * 0.07f, Path.Direction.CW);
            p.addCircle(cx + r * 0.28f, cy + r * 0.14f, r * 0.07f, Path.Direction.CW);
            p.addCircle(cx + r * 0.02f, cy + r * 0.40f, r * 0.07f, Path.Direction.CW);
        } else if (id.equals("jajko")) {
            // pisanka: jajko + dwie zygzakowate wstęgi + kropki
            p.addOval(new RectF(cx - r * 0.68f, cy - r, cx + r * 0.68f, cy + r), Path.Direction.CW);
            for (int band = 0; band < 2; band++) {
                float by = cy + (band == 0 ? -r * 0.16f : r * 0.24f);
                float bw = r * 0.56f;
                float step = bw * 2 / 7f;
                for (int i = 0; i <= 7; i++) {
                    float x = cx - bw + i * step;
                    float y = by + ((i % 2 == 0) ? -r * 0.10f : r * 0.10f);
                    if (i == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
            }
            p.addCircle(cx - r * 0.22f, cy + r * 0.62f, r * 0.055f, Path.Direction.CW);
            p.addCircle(cx + r * 0.24f, cy + r * 0.62f, r * 0.055f, Path.Direction.CW);
        } else if (id.equals("obraczki")) {
            // dwie obrączki
            p.addCircle(cx - r * 0.30f, cy + r * 0.05f, r * 0.52f, Path.Direction.CW);
            p.addCircle(cx - r * 0.30f, cy + r * 0.05f, r * 0.40f, Path.Direction.CCW);
            p.addCircle(cx + r * 0.30f, cy + r * 0.05f, r * 0.52f, Path.Direction.CW);
            p.addCircle(cx + r * 0.30f, cy + r * 0.05f, r * 0.40f, Path.Direction.CCW);
        } else if (id.equals("medal")) {
            // medal: krążek + wstęga
            p.addCircle(cx, cy - r * 0.18f, r * 0.56f, Path.Direction.CW);
            p.addCircle(cx, cy - r * 0.18f, r * 0.46f, Path.Direction.CCW);
            p.moveTo(cx - r * 0.26f, cy + r * 0.24f);
            p.lineTo(cx - r * 0.40f, cy + r * 0.98f);
            p.lineTo(cx - r * 0.12f, cy + r * 0.76f);
            p.lineTo(cx - r * 0.04f, cy + r * 0.30f);
            p.close();
            p.moveTo(cx + r * 0.26f, cy + r * 0.24f);
            p.lineTo(cx + r * 0.40f, cy + r * 0.98f);
            p.lineTo(cx + r * 0.12f, cy + r * 0.76f);
            p.lineTo(cx + r * 0.04f, cy + r * 0.30f);
            p.close();
            for (int i = 0; i < 5; i++) {
                double ang = -Math.PI / 2 + Math.PI * 2 * i / 5.0;
                float x = cx + (float) Math.cos(ang) * r * 0.20f;
                float y = (cy - r * 0.18f) + (float) Math.sin(ang) * r * 0.20f;
                if (i == 0) p.moveTo(x, y);
                else p.lineTo(x, y);
            }
            p.close();
        } else if (id.equals("ornament")) {
            p.moveTo(cx - r, cy);
            for (int i = 0; i <= 12; i++) {
                float t = i / 12f;
                p.lineTo(cx - r + 2 * r * t, cy + (float) Math.sin(t * Math.PI * 4) * r * 0.18f);
            }
        } else {
            // domyślnie: gwiazda słowiańska / ornament geometryczny
            for (int i = 0; i < 16; i++) {
                double ang = Math.PI * 2 * i / 16;
                double rad = (i % 2 == 0) ? r : r * 0.62;
                float x = cx + (float) Math.cos(ang) * (float) rad;
                float y = cy + (float) Math.sin(ang) * (float) rad;
                if (i == 0) p.moveTo(x, y);
                else p.lineTo(x, y);
            }
            p.close();
            p.addCircle(cx, cy, r * 0.22f, Path.Direction.CCW);
        }
        return p;
    }

    // ---------------- render ----------------
    public static Bitmap render(Context ctx, Core.Design d, float pxPerMm, boolean guides) {
        int W = Math.max(1, Math.round((float) d.product.w * pxPerMm));
        int H = Math.max(1, Math.round((float) d.product.h * pxPerMm));
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(Color.WHITE);
        Provider provider = new Provider(ctx);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < Core.LAYERS.length; i++) {
            String layer = Core.LAYERS[i];
            if (Core.L_GUIDES.equals(layer) && !guides) continue;
            if (Core.L_ENGRAVE_RASTER.equals(layer)) {
                for (int k = 0; k < d.objects.size(); k++) {
                    Core.Obj o = d.objects.get(k);
                    if (!o.visible || !Core.L_ENGRAVE_RASTER.equals(o.layer)) continue;
                    drawRaster(cv, ctx, d, o, pxPerMm);
                }
                continue;
            }
            for (int k = 0; k < d.objects.size(); k++) {
                Core.Obj o = d.objects.get(k);
                if (!o.visible || !layer.equals(o.layer)) continue;
                Path p = provider.pathFor(o);
                if (p.isEmpty()) continue;
                boolean fill = "fill".equals(o.role);
                int col = Color.BLACK;
                if (Core.L_CUT_OUTER.equals(layer)) col = Color.RED;
                else if (Core.L_CUT_INNER.equals(layer)) col = Color.rgb(0, 140, 220);
                else if (Core.L_SCORE.equals(layer)) col = Color.MAGENTA;
                else if (Core.L_GUIDES.equals(layer)) col = Color.rgb(120, 200, 150);
                paint.setColor(col);
                paint.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, (float) (fill ? 0 : Math.max(0.08, o.strokeW) * pxPerMm)));
                if (Core.L_GUIDES.equals(layer)) {
                    paint.setPathEffect(new DashPathEffect(new float[]{6f, 4f}, 0f));
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1.2f);
                } else {
                    paint.setPathEffect(null);
                }
                cv.save();
                cv.scale(pxPerMm, pxPerMm);
                cv.drawPath(p, paint);
                cv.restore();
            }
        }
        return bmp;
    }

    static void drawRaster(Canvas cv, Context ctx, Core.Design d, Core.Obj o, float pxPerMm) {
        try {
            java.io.File f = Store.rasterFile(ctx, d.id, o.d);
            if (!f.exists()) return;
            android.graphics.BitmapFactory.Options op = new android.graphics.BitmapFactory.Options();
            op.inPreferredConfig = Bitmap.Config.ALPHA_8;
            Bitmap b = android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), op);
            if (b == null) return;
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setAlpha(230);
            android.graphics.RectF dst = new android.graphics.RectF((float) o.x * pxPerMm,
                    (float) o.y * pxPerMm, (float) (o.x + o.imgW) * pxPerMm,
                    (float) (o.y + o.imgH) * pxPerMm);
            cv.drawBitmap(b, null, dst, p);
            b.recycle();
        } catch (Exception e) {
        }
    }

    // ---------------- eksport SVG ----------------
    public static String toSvg(Context ctx, Core.Design d, boolean includeGuides) {
        Provider provider = new Provider(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
        sb.append("width=\"").append(Core.fmt(d.product.w)).append("mm\" height=\"")
                .append(Core.fmt(d.product.h)).append("mm\" viewBox=\"0 0 ")
                .append(Core.fmt(d.product.w)).append(" ").append(Core.fmt(d.product.h)).append("\" ");
        sb.append("data-units=\"mm\" data-project=\"").append(escape(d.name)).append("\">\n");
        sb.append("  <desc>WOJAN LaserCraft AI Studio (Android) — wymiary w mm, teksty jako ścieżki.</desc>\n");
        for (int i = 0; i < Core.LAYERS.length; i++) {
            String layer = Core.LAYERS[i];
            if (Core.L_GUIDES.equals(layer) && !includeGuides) continue;
            if (Core.L_ENGRAVE_RASTER.equals(layer)) continue;
            StringBuilder body = new StringBuilder();
            for (int k = 0; k < d.objects.size(); k++) {
                Core.Obj o = d.objects.get(k);
                if (!o.visible || !layer.equals(o.layer)) continue;
                Path p = provider.pathFor(o);
                if (p.isEmpty()) continue;
                boolean fill = "fill".equals(o.role);
                body.append("    <path d=\"").append(pathToSvgD(p, 0.12f)).append("\" ");
                if (fill) {
                    body.append("fill=\"").append(Core.layerColor(layer)).append("\" fill-rule=\"evenodd\" stroke=\"none\"");
                } else {
                    body.append("fill=\"none\" stroke=\"").append(Core.layerColor(layer))
                            .append("\" stroke-width=\"").append(Core.fmt(Math.max(0.1, o.strokeW))).append("\"");
                }
                body.append(" id=\"").append(escape(o.id)).append("\"");
                if (!o.name.isEmpty()) body.append(" data-name=\"").append(escape(o.name)).append("\"");
                body.append("/>\n");
            }
            if (body.length() == 0) continue;
            sb.append("  <g id=\"").append(layer).append("\" data-operation=\"")
                    .append(Core.isCut(layer) ? "Cięcie" : "Grawer").append("\" data-color=\"")
                    .append(Core.layerColor(layer)).append("\">\n").append(body).append("  </g>\n");
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Ścieżka Android → dane SVG (spłaszczenie krzywych z tolerancją mm). */
    public static String pathToSvgD(Path p, float toleranceMm) {
        StringBuilder sb = new StringBuilder();
        float[] approx = p.approximate(toleranceMm);
        if (approx.length < 6) return "";
        sb.append("M ");
        boolean open = false;
        float lastFrac = 0f;
        for (int i = 0; i + 2 < approx.length; i += 3) {
            float frac = approx[i];
            float x = approx[i + 1];
            float y = approx[i + 2];
            if (i > 0 && frac <= lastFrac && frac < 0.001f) {
                sb.append(" Z M ");
            }
            sb.append(Core.fmt(x)).append(" ").append(Core.fmt(y)).append(i + 3 < approx.length ? " L " : "");
            lastFrac = frac;
            open = true;
        }
        if (open) sb.append(" Z");
        return sb.toString();
    }

    // ---------------- eksport DXF ----------------
    public static String toDxf(Context ctx, Core.Design d) {
        Provider provider = new Provider(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1015\n");
        sb.append("9\n$INSUNITS\n70\n4\n9\n$MEASUREMENT\n70\n1\n");
        sb.append("9\n$EXTMIN\n10\n0.0\n20\n0.0\n30\n0.0\n");
        sb.append("9\n$EXTMAX\n10\n").append(Core.fmt(d.product.w)).append("\n20\n")
                .append(Core.fmt(d.product.h)).append("\n30\n0.0\n");
        sb.append("0\nENDSEC\n0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n70\n7\n");
        for (int i = 0; i < Core.LAYERS.length; i++) {
            if (Core.L_ENGRAVE_RASTER.equals(Core.LAYERS[i]) || Core.L_GUIDES.equals(Core.LAYERS[i])) continue;
            int color = Core.L_CUT_OUTER.equals(Core.LAYERS[i]) ? 1
                    : Core.L_CUT_INNER.equals(Core.LAYERS[i]) ? 5
                    : Core.L_SCORE.equals(Core.LAYERS[i]) ? 6 : 7;
            sb.append("0\nLAYER\n2\n").append(Core.LAYERS[i]).append("\n70\n0\n62\n")
                    .append(color).append("\n6\nCONTINUOUS\n");
        }
        sb.append("0\nENDTAB\n0\nENDSEC\n0\nSECTION\n2\nENTITIES\n");
        for (int i = 0; i < Core.LAYERS.length; i++) {
            String layer = Core.LAYERS[i];
            if (Core.L_ENGRAVE_RASTER.equals(layer) || Core.L_GUIDES.equals(layer)) continue;
            for (int k = 0; k < d.objects.size(); k++) {
                Core.Obj o = d.objects.get(k);
                if (!o.visible || !layer.equals(o.layer)) continue;
                Path p = provider.pathFor(o);
                if (p.isEmpty()) continue;
                float[] approx = p.approximate(0.12f);
                if (approx.length < 6) continue;
                sb.append("0\nLWPOLYLINE\n8\n").append(layer).append("\n");
                int count = approx.length / 3;
                sb.append("90\n").append(count).append("\n70\n1\n");
                for (int j = 0; j + 2 < approx.length; j += 3) {
                    sb.append("10\n").append(Core.fmt(approx[j + 1]))
                            .append("\n20\n").append(Core.fmt(approx[j + 2])).append("\n");
                }
            }
        }
        sb.append("0\nENDSEC\n0\nEOF\n");
        return sb.toString();
    }

    // ---------------- G-code (tylko GRBL) ----------------
    public static String toGcode(Context ctx, Core.Design d, String errorOut[]) {
        if (!"GRBL".equals(d.machine.controller)) {
            if (errorOut != null && errorOut.length > 0)
                errorOut[0] = "Kontroler „" + d.machine.controller + "” nie jest GRBL — eksport G-code zablokowany.";
            return null;
        }
        Provider provider = new Provider(ctx);
        StringBuilder g = new StringBuilder();
        g.append("; WOJAN LaserCraft AI Studio — G-code (GRBL)\n");
        g.append("; projekt: ").append(d.name).append("\n");
        g.append("; material: ").append(d.product.material).append(", kerf ").append(Core.fmt(d.machine.kerf)).append(" mm\n");
        g.append("; UWAGA: moce i prędkości orientacyjne — sprawdź na materiale próbnym.\n");
        g.append("G21\nG90\nG94\nG17\nM5\nG0 X0 Y0\n");
        for (int i = 0; i < Core.LAYERS.length; i++) {
            String layer = Core.LAYERS[i];
            if (Core.L_ENGRAVE_RASTER.equals(layer) || Core.L_GUIDES.equals(layer)) continue;
            boolean cut = Core.isCut(layer);
            for (int k = 0; k < d.objects.size(); k++) {
                Core.Obj o = d.objects.get(k);
                if (!o.visible || !layer.equals(o.layer)) continue;
                Path p = provider.pathFor(o);
                if (p.isEmpty()) continue;
                PathMeasure pm = new PathMeasure(p, false);
                do {
                    float len = pm.getLength();
                    if (len < 0.5f) continue;
                    int steps = Math.max(2, (int) (len / 0.5f));
                    float[] pos = new float[2];
                    pm.getPosTan(0, pos, null);
                    g.append("G0 X").append(Core.fmt(pos[0])).append(" Y").append(Core.fmt(pos[1])).append("\n");
                    g.append("M4 S").append(cut ? "1000" : "350").append(" ; ").append(cut ? "cięcie" : "grawer").append("\n");
                    g.append("G1 F").append(cut ? "300" : "2000").append("\n");
                    for (int s = 1; s <= steps; s++) {
                        pm.getPosTan(len * s / steps, pos, null);
                        g.append("G1 X").append(Core.fmt(pos[0])).append(" Y").append(Core.fmt(pos[1])).append("\n");
                    }
                    g.append("M5\n");
                } while (pm.nextContour());
            }
        }
        g.append("M5\nG0 X0 Y0\nM2\n");
        return g.toString();
    }
}
