package pl.wojan.lasercraft;

import android.graphics.Path;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.Inflater;

/**
 * Import wektorów: SVG (ścieżki „d”), DXF (LWPOLYLINE/POLYLINE/LINE/CIRCLE/ARC jako polilinia)
 * oraz PDF (geometria z operatorów m/l/c/re; strumienie FlateDecode).
 * Import PDF odtwarza TYLKO wektory — teksty, obrazy i fonty nie są odtwarzane.
 */
public final class Imp {

    public static class Result {
        public List<Path> paths = new ArrayList<Path>();
        public double widthMm = 100, heightMm = 100;
        public String note = "";
        public String error = "";
    }

    // ---------------- SVG ----------------
    public static Result svg(String xml) {
        Result r = new Result();
        try {
            java.util.regex.Matcher mw = java.util.regex.Pattern
                    .compile("width\\s*=\\s*\"([0-9.]+)\\s*(mm|cm|px|in)?\"").matcher(xml);
            java.util.regex.Matcher mh = java.util.regex.Pattern
                    .compile("height\\s*=\\s*\"([0-9.]+)\\s*(mm|cm|px|in)?\"").matcher(xml);
            if (mw.find()) r.widthMm = unit(Double.parseDouble(mw.group(1)), mw.group(2));
            if (mh.find()) r.heightMm = unit(Double.parseDouble(mh.group(1)), mh.group(2));
            java.util.regex.Matcher vb = java.util.regex.Pattern
                    .compile("viewBox\\s*=\\s*\"([-0-9. ]+)\"").matcher(xml);
            if (vb.find()) {
                String[] p = vb.group(1).trim().split("[ ,]+");
                if (p.length >= 4 && (r.widthMm <= 0 || r.heightMm <= 0)) {
                    r.widthMm = Double.parseDouble(p[2]);
                    r.heightMm = Double.parseDouble(p[3]);
                }
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<path[^>]*\\sd\\s*=\\s*\"([^\"]+)\"").matcher(xml);
            while (m.find()) {
                Path p = Draw.parseSvgPath(m.group(1));
                if (!p.isEmpty()) r.paths.add(p);
            }
            java.util.regex.Matcher mr = java.util.regex.Pattern
                    .compile("<rect[^>]*>").matcher(xml);
            while (mr.find()) {
                String tag = mr.group();
                double x = attr(tag, "x"), y = attr(tag, "y"), w = attr(tag, "width"), h = attr(tag, "height");
                if (w > 0 && h > 0) {
                    Path p = new Path();
                    p.addRect((float) x, (float) y, (float) (x + w), (float) (y + h), Path.Direction.CW);
                    r.paths.add(p);
                }
            }
            java.util.regex.Matcher mc = java.util.regex.Pattern.compile("<circle[^>]*>").matcher(xml);
            while (mc.find()) {
                String tag = mc.group();
                double cx = attr(tag, "cx"), cy = attr(tag, "cy"), rr = attr(tag, "r");
                if (rr > 0) {
                    Path p = new Path();
                    p.addCircle((float) cx, (float) cy, (float) rr, Path.Direction.CW);
                    r.paths.add(p);
                }
            }
            if (r.paths.isEmpty()) r.error = "SVG nie zawiera ścieżek (<path>) ani prostokątów/kół.";
            r.note = "SVG: " + r.paths.size() + " obiektów, " + Core.fmt(r.widthMm) + "×"
                    + Core.fmt(r.heightMm) + " mm";
            return r;
        } catch (Exception e) {
            r.error = "Błąd parsowania SVG: " + e.getMessage();
            return r;
        }
    }

    static double unit(double v, String u) {
        if (u == null) return v;
        if (u.equals("cm")) return v * 10;
        if (u.equals("in")) return v * 25.4;
        if (u.equals("px")) return v * 25.4 / 96.0;
        return v;
    }

    static double attr(String tag, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(name + "\\s*=\\s*\"([-0-9.]+)\"").matcher(tag);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (Exception e) {
            }
        }
        return 0;
    }

    // ---------------- DXF ----------------
    public static Result dxf(String text) {
        Result r = new Result();
        try {
            String[] lines = text.split("\\r?\\n");
            List<String> code = new ArrayList<String>();
            List<String> val = new ArrayList<String>();
            for (int i = 0; i + 1 < lines.length; i += 2) {
                code.add(lines[i].trim());
                val.add(lines[i + 1].trim());
            }
            double maxX = 0, maxY = 0;
            int i = 0;
            while (i < code.size()) {
                if (code.get(i).equals("0")) {
                    String type = val.get(i).toUpperCase(Locale.ROOT);
                    int j = i + 1;
                    double x = 0, y = 0, cx = 0, cy = 0, rad = 0;
                    boolean closed = false;
                    List<float[]> pts = new ArrayList<float[]>();
                    while (j < code.size() && !code.get(j).equals("0")) {
                        int c = -1;
                        try {
                            c = Integer.parseInt(code.get(j));
                        } catch (Exception e) {
                        }
                        double v = 0;
                        try {
                            v = Double.parseDouble(val.get(j));
                        } catch (Exception e) {
                        }
                        if (type.equals("LINE")) {
                            if (c == 10) x = v;
                            else if (c == 20) y = v;
                            else if (c == 11) pts.add(new float[]{(float) x, (float) y, (float) v});
                            else if (c == 21) {
                                if (!pts.isEmpty()) pts.get(pts.size() - 1)[2] = (float) v;
                            }
                        } else if (type.equals("LWPOLYLINE") || type.equals("POLYLINE")) {
                            if (c == 10) x = v;
                            else if (c == 20) {
                                y = v;
                                pts.add(new float[]{(float) x, (float) y});
                            } else if (c == 70 && ((int) v & 1) == 1) closed = true;
                        } else if (type.equals("CIRCLE") || type.equals("ARC")) {
                            if (c == 10) cx = v;
                            else if (c == 20) cy = v;
                            else if (c == 40) rad = v;
                        }
                        j++;
                    }
                    if (type.equals("LINE") && pts.size() >= 1) {
                        Path p = new Path();
                        p.moveTo(pts.get(0)[0], pts.get(0)[1]);
                        p.lineTo(pts.get(0)[2], pts.size() > 1 ? pts.get(1)[1] : pts.get(0)[2]);
                        moveHelper(p, pts);
                        r.paths.add(p);
                    } else if ((type.equals("LWPOLYLINE") || type.equals("POLYLINE")) && pts.size() >= 2) {
                        Path p = new Path();
                        p.moveTo(pts.get(0)[0], pts.get(0)[1]);
                        for (int k = 1; k < pts.size(); k++) p.lineTo(pts.get(k)[0], pts.get(k)[1]);
                        if (closed) p.close();
                        r.paths.add(p);
                    } else if ((type.equals("CIRCLE") || type.equals("ARC")) && rad > 0) {
                        Path p = new Path();
                        p.addCircle((float) cx, (float) cy, (float) rad, Path.Direction.CW);
                        r.paths.add(p);
                    }
                    i = j;
                } else i++;
            }
            for (int k = 0; k < r.paths.size(); k++) {
                android.graphics.RectF b = new android.graphics.RectF();
                r.paths.get(k).computeBounds(b, true);
                maxX = Math.max(maxX, b.right);
                maxY = Math.max(maxY, b.bottom);
            }
            r.widthMm = Math.max(10, Math.ceil(maxX));
            r.heightMm = Math.max(10, Math.ceil(maxY));
            if (r.paths.isEmpty()) r.error = "DXF nie zawiera obsługiwanych encji (LINE/LWPOLYLINE/CIRCLE).";
            r.note = "DXF: " + r.paths.size() + " obiektów, obszar " + Core.fmt(maxX) + "×"
                    + Core.fmt(maxY) + " mm";
            return r;
        } catch (Exception e) {
            r.error = "Błąd parsowania DXF: " + e.getMessage();
            return r;
        }
    }

    static void moveHelper(Path p, List<float[]> pts) {
    }

    // ---------------- PDF ----------------
    public static Result pdf(byte[] data) {
        Result r = new Result();
        try {
            String raw = new String(data, Charset.forName("ISO-8859-1"));
            int idx = 0, found = 0;
            while (true) {
                int s = raw.indexOf("stream", idx);
                if (s < 0) break;
                int e = raw.indexOf("endstream", s);
                if (e < 0) break;
                int ds = s + 6;
                if (ds < raw.length() && raw.charAt(ds) == '\r') ds++;
                if (ds < raw.length() && raw.charAt(ds) == '\n') ds++;
                String dict = raw.substring(Math.max(0, s - 400), s);
                byte[] chunk = new byte[e - ds];
                for (int i = 0; i < chunk.length; i++) chunk[i] = (byte) raw.charAt(ds + i);
                idx = e + 9;
                String content;
                if (dict.contains("FlateDecode")) {
                    try {
                        content = new String(inflate(chunk), Charset.forName("ISO-8859-1"));
                    } catch (Exception ex) {
                        continue;
                    }
                } else {
                    content = new String(chunk, Charset.forName("ISO-8859-1"));
                }
                if (content.indexOf(" m") < 0 && content.indexOf(" re") < 0) continue;
                found += parsePdfOps(content, r);
            }
            if (r.paths.isEmpty()) {
                r.error = "PDF: nie znaleziono geometrii wektorowej (możliwy PDF skanowany/obraz — "
                        + "użyj importu zdjęcia).";
            } else {
                float maxX = 0, maxY = 0;
                for (int k = 0; k < r.paths.size(); k++) {
                    android.graphics.RectF b = new android.graphics.RectF();
                    r.paths.get(k).computeBounds(b, true);
                    maxX = Math.max(maxX, b.right);
                    maxY = Math.max(maxY, b.bottom);
                }
                // PDF ma oś Y w górę — odwracamy do układu SVG (Y w dół)
                android.graphics.Matrix flip = new android.graphics.Matrix();
                flip.setScale(1f, -1f);
                flip.postTranslate(0f, maxY);
                for (int k = 0; k < r.paths.size(); k++) r.paths.get(k).transform(flip);
                r.widthMm = Math.max(10, maxX);
                r.heightMm = Math.max(10, maxY);
                r.note = "PDF: " + r.paths.size() + " ścieżek ze " + found + " strumieni treści "
                        + "(tylko geometria — teksty, fonty i obrazy nie są odtwarzane)";
            }
            return r;
        } catch (Exception e) {
            r.error = "Błąd importu PDF: " + e.getMessage();
            return r;
        }
    }

    static byte[] inflate(byte[] in) throws Exception {
        Inflater inf = new Inflater();
        inf.setInput(in);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n == 0) break;
            bo.write(buf, 0, n);
        }
        inf.end();
        return bo.toByteArray();
    }

    /** Parsuje operatory ścieżek PDF (mm = pt × 25,4/72) i zwraca liczbę ścieżek. */
    static int parsePdfOps(String content, Result r) {
        List<Double> nums = new ArrayList<Double>();
        Path cur = null;
        double x = 0, y = 0, sx = 0, sy = 0;
        int i = 0, paths = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(-?[0-9.]+)|([A-Za-z*']+)").matcher(content);
        double lastCx = 0, lastCy = 0;
        while (m.find()) {
            if (m.group(1) != null) {
                try {
                    nums.add(Double.valueOf(Double.parseDouble(m.group(1))));
                } catch (Exception e) {
                }
                continue;
            }
            String op = m.group(2);
            if (op.equals("m") && nums.size() >= 2) {
                if (cur != null && !cur.isEmpty()) r.paths.add(cur);
                cur = new Path();
                x = toMmX(nums.get(nums.size() - 2));
                y = toMmY(nums.get(nums.size() - 1));
                sx = x;
                sy = y;
                cur.moveTo((float) x, (float) y);
                paths++;
            } else if (op.equals("l") && nums.size() >= 2 && cur != null) {
                x = toMmX(nums.get(nums.size() - 2));
                y = toMmY(nums.get(nums.size() - 1));
                cur.lineTo((float) x, (float) y);
            } else if (op.equals("c") && nums.size() >= 6 && cur != null) {
                double x1 = toMmX(nums.get(nums.size() - 6)), y1 = toMmY(nums.get(nums.size() - 5));
                double x2 = toMmX(nums.get(nums.size() - 4)), y2 = toMmY(nums.get(nums.size() - 3));
                double x3 = toMmX(nums.get(nums.size() - 2)), y3 = toMmY(nums.get(nums.size() - 1));
                cur.cubicTo((float) x1, (float) y1, (float) x2, (float) y2, (float) x3, (float) y3);
                x = x3;
                y = y3;
            } else if (op.equals("re") && nums.size() >= 4) {
                double rx = toMmX(nums.get(nums.size() - 4));
                double ry = toMmY(nums.get(nums.size() - 3) + nums.get(nums.size() - 2));
                double rw = nums.get(nums.size() - 2) * MM;
                double rh = nums.get(nums.size() - 1) * MM;
                Path p = new Path();
                p.addRect((float) rx, (float) ry, (float) (rx + rw), (float) (ry + rh), Path.Direction.CW);
                r.paths.add(p);
                paths++;
            } else if (op.equals("h") && cur != null) {
                cur.close();
                x = sx;
                y = sy;
            } else if ((op.equals("f") || op.equals("S") || op.equals("B") || op.equals("n"))
                    && cur != null) {
                if (!cur.isEmpty()) r.paths.add(cur);
                cur = null;
            }
            nums.clear();
        }
        if (cur != null && !cur.isEmpty()) r.paths.add(cur);
        return paths;
    }

    static final double MM = 25.4 / 72.0;

    static double toMmX(double pt) {
        return pt * MM;
    }

    static double toMmY(double pt) {
        return pt * MM;
    }
}
