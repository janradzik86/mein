package pl.wojan.lasercraft;

import android.content.Context;
import android.graphics.Path;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimalny generator PDF (1.4) bez zewnętrznych bibliotek:
 * ścieżki wektorowe w punktach (mm × 72/25,4), warstwy jako zestawy ścieżek.
 * PDF nie zawiera rastra — tylko geometrię, więc jest zgodny z drukiem i wektorem.
 */
public final class Pdf {

    static final double MM2PT = 72.0 / 25.4;

    public static byte[] build(Context c, Core.Design d) {
        PathProvider prov = new Draw.Provider(c);
        StringBuilder content = new StringBuilder();
        content.append("q\n1 j 1 J\n");
        for (int i = 0; i < Core.LAYERS.length; i++) {
            String layer = Core.LAYERS[i];
            if (Core.L_ENGRAVE_RASTER.equals(layer) || Core.L_GUIDES.equals(layer)) continue;
            boolean cut = Core.isCut(layer);
            for (int k = 0; k < d.objects.size(); k++) {
                Core.Obj o = d.objects.get(k);
                if (!o.visible || !layer.equals(o.layer)) continue;
                Path p = prov.pathFor(o);
                if (p == null || p.isEmpty()) continue;
                float[] ap = p.approximate(0.12f);
                if (ap.length < 6) continue;
                boolean fill = "fill".equals(o.role);
                if (cut) content.append("0.85 0.10 0.10 RG\n");
                else if (Core.L_SCORE.equals(layer)) content.append("0.80 0.10 0.80 RG\n");
                else content.append("0.05 0.05 0.05 RG\n");
                double wpt = Math.max(0.1, o.strokeW) * MM2PT;
                content.append(fmt(wpt)).append(" w\n");
                boolean started = false;
                float prevFrac = -1f;
                for (int j = 0; j + 2 < ap.length; j += 3) {
                    float frac = ap[j];
                    double x = ap[j + 1] * MM2PT;
                    double y = (d.product.h - ap[j + 2]) * MM2PT;
                    if (!started || (frac <= prevFrac && frac < 0.001f)) {
                        content.append(fmt(x)).append(" ").append(fmt(y)).append(" m\n");
                        started = true;
                    } else {
                        content.append(fmt(x)).append(" ").append(fmt(y)).append(" l\n");
                    }
                    prevFrac = frac;
                }
                if (fill) content.append("f\n");
                else content.append("S\n");
            }
        }
        content.append("Q\n");

        double wPt = d.product.w * MM2PT;
        double hPt = d.product.h * MM2PT;
        String stream = content.toString();
        byte[] streamBytes = latin(stream);

        List<byte[]> objs = new ArrayList<byte[]>();
        objs.add(latin("<< /Type /Catalog /Pages 2 0 R >>"));
        objs.add(latin("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objs.add(latin("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + fmt(wPt) + " " + fmt(hPt)
                + "] /Contents 4 0 R /Resources << >> >>"));
        objs.add(concat(latin("<< /Length " + streamBytes.length + " >>\nstream\n"), streamBytes,
                latin("\nendstream")));
        objs.add(latin("<< /Title (" + esc(d.name) + ") /Producer (WOJAN LaserCraft AI Studio - Android)"
                + " /Creator (WOJAN LaserCraft AI Studio) /CreationDate (D:"
                + new java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US)
                .format(new java.util.Date()) + ") >>"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] header = new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', '%', (byte) 0xE2,
                (byte) 0xE3, (byte) 0xCF, (byte) 0xD3, '\n'};
        out.write(header, 0, header.length);
        int[] offsets = new int[objs.size()];
        for (int i = 0; i < objs.size(); i++) {
            offsets[i] = out.size();
            byte[] o1 = latin((i + 1) + " 0 obj\n");
            out.write(o1, 0, o1.length);
            out.write(objs.get(i), 0, objs.get(i).length);
            byte[] o3 = latin("\nendobj\n");
            out.write(o3, 0, o3.length);
        }
        int xref = out.size();
        StringBuilder x = new StringBuilder();
        x.append("xref\n0 ").append(objs.size() + 1).append("\n");
        x.append("0000000000 65535 f \n");
        for (int i = 0; i < objs.size(); i++)
            x.append(String.format(java.util.Locale.US, "%010d 00000 n \n", offsets[i]));
        x.append("trailer\n<< /Size ").append(objs.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        byte[] tail = latin(x.toString());
        out.write(tail, 0, tail.length);
        return out.toByteArray();
    }

    static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.3f", v);
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    static byte[] latin(String s) {
        try {
            return s.getBytes("ISO-8859-1");
        } catch (Exception e) {
            return s.getBytes();
        }
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        for (int i = 0; i < parts.length; i++) bo.write(parts[i], 0, parts[i].length);
        return bo.toByteArray();
    }
}
