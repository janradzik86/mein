package pl.wojan.lasercraft;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Biblioteka projektów: zapis/odczyt JSON (wlcproj/1), wersje, ulubione,
 * pakiety produkcyjne ZIP, import z zabezpieczeniem przed zip-slip i limitami rozmiaru.
 */
public final class Store {

    public static final String EXT = ".wlcproj";
    public static final int MAX_ENTRIES = 512;
    public static final long MAX_TOTAL = 200L * 1024 * 1024;
    public static final long MAX_ENTRY = 64L * 1024 * 1024;

    public static class Meta {
        public String id, name, modified, category, product;
        public boolean favourite;
        public int objects;
    }

    public static File root(Context c) {
        File f = new File(c.getFilesDir(), "projects");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File versionsDir(Context c) {
        File f = new File(c.getFilesDir(), "versions");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File assetsDir(Context c, String designId) {
        File f = new File(c.getFilesDir(), "assets/" + designId);
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File fileFor(Context c, String id) {
        return new File(root(c), safe(id) + EXT);
    }

    static String safe(String s) {
        StringBuilder b = new StringBuilder();
        if (s == null) s = "";
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' || ch == '.') b.append(ch);
        }
        if (b.length() == 0) b.append("projekt");
        return b.toString();
    }

    public static String newId() {
        return "p" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + "_" + (System.currentTimeMillis() % 1000);
    }

    public static boolean save(Context c, Core.Design d) {
        try {
            if (d.id == null || d.id.isEmpty()) d.id = newId();
            d.modified = Gen.now();
            FileOutputStream fo = new FileOutputStream(fileFor(c, d.id));
            fo.write(d.toJson().toString(1).getBytes("UTF-8"));
            fo.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Core.Design load(Context c, String id) {
        try {
            File f = fileFor(c, id);
            if (!f.exists()) return null;
            byte[] b = readAll(new FileInputStream(f), 32L * 1024 * 1024);
            JSONObject o = new JSONObject(new String(b, "UTF-8"));
            return Core.Design.fromJson(o);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean delete(Context c, String id) {
        boolean ok = fileFor(c, id).delete();
        File v = new File(versionsDir(c), safe(id));
        if (v.isDirectory()) {
            File[] fs = v.listFiles();
            if (fs != null) for (int i = 0; i < fs.length; i++) fs[i].delete();
            v.delete();
        }
        return ok;
    }

    public static List<Meta> list(Context c) {
        List<Meta> out = new ArrayList<Meta>();
        File[] fs = root(c).listFiles();
        if (fs == null) return out;
        for (int i = 0; i < fs.length; i++) {
            if (!fs[i].getName().endsWith(EXT)) continue;
            try {
                byte[] b = readAll(new FileInputStream(fs[i]), 8L * 1024 * 1024);
                JSONObject o = new JSONObject(new String(b, "UTF-8"));
                Meta m = new Meta();
                m.id = o.optString("id", fs[i].getName().replace(EXT, ""));
                m.name = o.optString("name", m.id);
                m.modified = o.optString("modified", "");
                m.category = o.optString("category", "");
                m.favourite = o.optBoolean("favourite", false);
                JSONObject pr = o.optJSONObject("product");
                m.product = pr == null ? "" : pr.optString("name", "");
                JSONArray objs = o.optJSONArray("objects");
                m.objects = objs == null ? 0 : objs.length();
                out.add(m);
            } catch (Exception e) {
                // uszkodzony plik — pomijamy, nie blokujemy biblioteki
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<Meta>() {
            public int compare(Meta a, Meta b) {
                if (a.favourite != b.favourite) return a.favourite ? -1 : 1;
                return b.modified.compareTo(a.modified);
            }
        });
        return out;
    }

    public static void setFavourite(Context c, String id, boolean fav) {
        Core.Design d = load(c, id);
        if (d == null) return;
        try {
            File f = fileFor(c, id);
            JSONObject o = new JSONObject(new String(readAll(new FileInputStream(f), 32L * 1024 * 1024), "UTF-8"));
            o.put("favourite", fav);
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(o.toString(1).getBytes("UTF-8"));
            fo.close();
        } catch (Exception e) {
        }
    }

    public static boolean isFavourite(Context c, String id) {
        try {
            File f = fileFor(c, id);
            if (!f.exists()) return false;
            JSONObject o = new JSONObject(new String(readAll(new FileInputStream(f), 32L * 1024 * 1024), "UTF-8"));
            return o.optBoolean("favourite", false);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- wersje ----------------
    public static void snapshot(Context c, Core.Design d, String label) {
        try {
            File dir = new File(versionsDir(c), safe(d.id));
            if (!dir.exists()) dir.mkdirs();
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            File f = new File(dir, stamp + EXT);
            FileOutputStream fo = new FileOutputStream(f);
            JSONObject o = d.toJson();
            o.put("_label", label == null ? "" : label);
            fo.write(o.toString(1).getBytes("UTF-8"));
            fo.close();
            File[] all = dir.listFiles();
            if (all != null && all.length > 20) {
                java.util.Arrays.sort(all);
                for (int i = 0; i < all.length - 20; i++) all[i].delete();
            }
        } catch (Exception e) {
        }
    }

    public static List<String> versionFiles(Context c, String id) {
        List<String> out = new ArrayList<String>();
        File[] fs = new File(versionsDir(c), safe(id)).listFiles();
        if (fs == null) return out;
        java.util.Arrays.sort(fs);
        for (int i = fs.length - 1; i >= 0; i--) out.add(fs[i].getName());
        return out;
    }

    public static Core.Design loadVersion(Context c, String id, String fileName) {
        try {
            File f = new File(new File(versionsDir(c), safe(id)), safe(fileName));
            if (!f.exists()) return null;
            return Core.Design.fromJson(new JSONObject(new String(readAll(new FileInputStream(f),
                    32L * 1024 * 1024), "UTF-8")));
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- IO ----------------
    public static byte[] readAll(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > limit) {
                in.close();
                throw new IOException("Przekroczony limit rozmiaru (" + (limit / 1048576) + " MB).");
            }
            bo.write(buf, 0, n);
        }
        in.close();
        return bo.toByteArray();
    }

    /** Czyta strumień bez zamykania go (potrzebne w ZipInputStream). */
    public static byte[] readEntry(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > limit) throw new IOException("Przekroczony limit rozmiaru wpisu ZIP.");
            bo.write(buf, 0, n);
        }
        return bo.toByteArray();
    }

    public static byte[] readUri(Context c, Uri uri, long limit) throws IOException {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Nie można otworzyć pliku.");
        return readAll(in, limit);
    }

    public static void writeUri(Context c, Uri uri, byte[] data) throws IOException {
        OutputStream out = c.getContentResolver().openOutputStream(uri, "wt");
        if (out == null) throw new IOException("Nie można zapisać pliku.");
        out.write(data);
        out.flush();
        out.close();
    }

    // ---------------- pakiet produkcyjny ----------------
    public static class Bundle {
        public byte[] zip;
        public List<String> files = new ArrayList<String>();
        public long bytes;
    }

    public static Bundle production(Context c, Core.Design d, boolean includeGuides) {
        Bundle b = new Bundle();
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ZipOutputStream zo = new ZipOutputStream(bo);
            PathProvider prov = new Draw.Provider(c);

            put(zo, b, "projekt_edytowalny.wlcproj", d.toJson().toString(1).getBytes("UTF-8"));
            put(zo, b, "projekt_edytowalny.json", d.toJson().toString(1).getBytes("UTF-8"));

            // LightBurn: SVG w mm, warstwy jako grupy, napisy jako ścieżki
            put(zo, b, "lightburn.svg", Draw.toSvg(c, d, includeGuides).getBytes("UTF-8"));
            // LaserGRBL: raster 1:1 w rozdzielczości maszyny
            Bitmap png = Draw.render(c, d, 254f / 25.4f, false);
            ByteArrayOutputStream p2 = new ByteArrayOutputStream();
            png.compress(Bitmap.CompressFormat.PNG, 100, p2);
            put(zo, b, "lasergrbl.png", p2.toByteArray());

            put(zo, b, "cut_outer.svg", layerSvg(c, d, Core.L_CUT_OUTER).getBytes("UTF-8"));
            put(zo, b, "cut_inner.svg", layerSvg(c, d, Core.L_CUT_INNER).getBytes("UTF-8"));
            put(zo, b, "engrave_vector.svg", layerSvg(c, d, Core.L_ENGRAVE_VECTOR).getBytes("UTF-8"));
            put(zo, b, "engrave_fill.svg", layerSvg(c, d, Core.L_ENGRAVE_FILL).getBytes("UTF-8"));
            put(zo, b, "engrave_raster.png", rasterLayer(c, d));

            Bitmap prev = Draw.render(c, d, 3f, true);
            ByteArrayOutputStream jp = new ByteArrayOutputStream();
            prev.compress(Bitmap.CompressFormat.JPEG, 92, jp);
            put(zo, b, "preview.jpg", jp.toByteArray());

            put(zo, b, "parametry.json", params(c, d).toString(1).getBytes("UTF-8"));

            Core.Report rep = Core.validate(d, prov);
            put(zo, b, "raport_walidacji.txt", rep.toText(d).getBytes("UTF-8"));

            String gcodeInfo = "GRBL".equals(d.machine.controller)
                    ? "G-code: dostępny (eksport osobny)."
                    : "G-code: NIE eksportowany — kontroler „" + d.machine.controller
                    + "” wymaga pliku SVG/DXF. Nie zgadujemy G-code dla nieznanej maszyny.";
            put(zo, b, "UWAGI.txt", (gcodeInfo + "\n" + rep.toText(d)).getBytes("UTF-8"));

            zo.close();
            b.zip = bo.toByteArray();
            b.bytes = b.zip.length;
            b.files.add("projekt_edytowalny.wlcproj");
            return b;
        } catch (Exception e) {
            return b;
        }
    }

    static void put(ZipOutputStream zo, Bundle b, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(System.currentTimeMillis());
        zo.putNextEntry(e);
        zo.write(data);
        zo.closeEntry();
        b.files.add(name);
    }

    public static String layerSvg(Context c, Core.Design d, String layer) {
        Core.Design copy = d;
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(Core.fmt(d.product.w))
                .append("mm\" height=\"").append(Core.fmt(d.product.h)).append("mm\" viewBox=\"0 0 ")
                .append(Core.fmt(d.product.w)).append(" ").append(Core.fmt(d.product.h))
                .append("\" data-layer=\"").append(layer).append("\">\n");
        PathProvider prov = new Draw.Provider(c);
        for (int i = 0; i < d.objects.size(); i++) {
            Core.Obj o = d.objects.get(i);
            if (!o.visible || !layer.equals(o.layer)) continue;
            android.graphics.Path p = prov.pathFor(o);
            if (p == null || p.isEmpty()) continue;
            sb.append("  <path d=\"").append(Draw.pathToSvgD(p, 0.12f)).append("\" fill=\"")
                    .append("fill".equals(o.role) ? Core.layerColor(layer) : "none")
                    .append("\" stroke=\"").append(Core.layerColor(layer))
                    .append("\" stroke-width=\"").append(Core.fmt(Math.max(0.1, o.strokeW))).append("\"/>\n");
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    public static byte[] rasterLayer(Context c, Core.Design d) {
        PathProvider prov = new Draw.Provider(c);
        // rastrowy podgląd czarno-biały 1:1 (254 dpi = 10 px/mm) z obiektów grawerowanych
        int pxmm = 10;
        Bitmap bmp = Bitmap.createBitmap(Math.max(8, (int) (d.product.w * pxmm)),
                Math.max(8, (int) (d.product.h * pxmm)), Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        cv.drawColor(android.graphics.Color.WHITE);
        android.graphics.Paint pt = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        pt.setColor(android.graphics.Color.BLACK);
        pt.setStyle(android.graphics.Paint.Style.FILL);
        cv.scale(pxmm, pxmm);
        for (int i = 0; i < d.objects.size(); i++) {
            Core.Obj o = d.objects.get(i);
            if (!o.visible) continue;
            if (!Core.L_ENGRAVE_VECTOR.equals(o.layer) && !Core.L_ENGRAVE_FILL.equals(o.layer)
                    && !Core.L_ENGRAVE_RASTER.equals(o.layer)) continue;
            android.graphics.Path p = prov.pathFor(o);
            if (p == null) continue;
            pt.setStyle("fill".equals(o.role) ? android.graphics.Paint.Style.FILL : android.graphics.Paint.Style.STROKE);
            pt.setStrokeWidth((float) Math.max(0.12, o.strokeW));
            cv.drawPath(p, pt);
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bo);
        return bo.toByteArray();
    }

    public static JSONObject params(Context c, Core.Design d) {
        JSONObject o = new JSONObject();
        try {
            o.put("program", "WOJAN LaserCraft AI Studio (Android)");
            o.put("format", "wlcproj/1");
            o.put("projekt", d.name);
            o.put("jednostki", "mm");
            o.put("material", Core.material(d.product.material).name);
            o.put("grubosc_mm", Core.material(d.product.material).thickness);
            o.put("produkt", d.product.name);
            o.put("szerokosc_mm", d.product.w);
            o.put("wysokosc_mm", d.product.h);
            o.put("kerf_mm", d.machine.kerf);
            o.put("kerf_zrodlo", "Ustawienia użytkownika / pomiar na próbce — NIE zgadywane przez AI");
            o.put("maszyna", d.machine.name);
            o.put("kontroler", d.machine.controller);
            o.put("lpi", d.machine.lpi);
            o.put("wymaga_ciecia", d.product.requiresCut);
            JSONArray layers = new JSONArray();
            for (int i = 0; i < Core.LAYERS.length; i++) {
                int n = 0;
                for (int k = 0; k < d.objects.size(); k++)
                    if (Core.LAYERS[i].equals(d.objects.get(k).layer) && d.objects.get(k).visible) n++;
                JSONObject l = new JSONObject();
                l.put("warstwa", Core.LAYERS[i]);
                l.put("obiektow", n);
                l.put("kolor", Core.layerColor(Core.LAYERS[i]));
                layers.put(l);
            }
            o.put("warstwy", layers);
            o.put("uwaga_czas", "Czas pracy orientacyjny — wymaga profilu konkretnej maszyny.");
        } catch (Exception e) {
        }
        return o;
    }

    // ---------------- import ZIP ----------------
    public static class ImportResult {
        public Core.Design design;
        public int files;
        public String error = "";
        public List<String> notes = new ArrayList<String>();
    }

    /** Import ZIP projektu z zabezpieczeniem zip-slip i limitami. */
    public static ImportResult importZip(Context c, Uri uri) {
        ImportResult r = new ImportResult();
        try {
            byte[] data = readUri(c, uri, MAX_TOTAL);
            ZipInputStream zi = new ZipInputStream(new ByteArrayInputStream(data));
            ZipEntry e;
            int count = 0;
            long total = 0;
            String projectJson = null;
            List<Object[]> assets = new ArrayList<Object[]>();
            String projectId = newId();
            while ((e = zi.getNextEntry()) != null) {
                count++;
                if (count > MAX_ENTRIES) {
                    r.error = "ZIP ma więcej niż " + MAX_ENTRIES + " plików.";
                    return r;
                }
                String name = e.getName();
                if (name == null) continue;
                name = name.replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.contains("..\\")) {
                    r.error = "ZIP zawiera niebezpieczną ścieżkę: " + name;
                    return r;
                }
                if (e.isDirectory()) continue;
                byte[] content = readEntry(zi, MAX_ENTRY);
                total += content.length;
                if (total > MAX_TOTAL) {
                    r.error = "ZIP przekracza " + (MAX_TOTAL / 1048576) + " MB.";
                    return r;
                }
                String lower = name.toLowerCase(Locale.US);
                if (lower.endsWith(".wlcproj") || lower.endsWith("projekt_edytowalny.json")) {
                    projectJson = new String(content, "UTF-8");
                } else if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                        || lower.endsWith(".webp") || lower.endsWith(".bmp")) {
                    assets.add(new Object[]{new File(name).getName(), content});
                } else if (!(lower.endsWith(".svg") || lower.endsWith(".dxf") || lower.endsWith(".txt")
                        || lower.endsWith(".json") || lower.endsWith(".pdf") || lower.endsWith(".gcode"))) {
                    r.notes.add("Pominięto nieznany plik: " + name);
                }
            }
            zi.close();
            if (projectJson == null) {
                r.error = "W archiwum nie ma pliku projektu ( .wlcproj lub projekt_edytowalny.json ).";
                return r;
            }
            JSONObject o = new JSONObject(projectJson);
            Core.Design d = Core.Design.fromJson(o);
            d.id = projectId;
            // zasoby rastrowe → katalog projektu
            List<String> saved = new ArrayList<String>();
            for (int i = 0; i < assets.size(); i++) {
                Object[] a = assets.get(i);
                String base = String.valueOf(a[0]);
                int dot = base.lastIndexOf('.');
                if (dot > 0) base = base.substring(0, dot);
                String nm = saveRaster(c, projectId, base, (byte[]) a[1]);
                if (!nm.isEmpty()) saved.add(nm);
            }
            int nextAsset = 0;
            for (int i = 0; i < d.objects.size(); i++) {
                Core.Obj ob = d.objects.get(i);
                if (!Core.L_ENGRAVE_RASTER.equals(ob.layer)) continue;
                if (ob.d == null || ob.d.isEmpty() || !rasterFile(c, projectId, ob.d).exists()) {
                    if (nextAsset < saved.size()) ob.d = saved.get(nextAsset++);
                }
            }
            if (!saved.isEmpty()) r.notes.add("Wczytano obrazów rastrowych: " + saved.size() + ".");
            r.design = d;
            r.files = count;
            r.notes.add("Wczytano projekt: " + d.name + " (" + d.objects.size() + " obiektów).");
            return r;
        } catch (Exception ex) {
            r.error = "Błąd importu ZIP: " + ex.getMessage();
            return r;
        }
    }

    /** Zapis obrazu rastrowego do katalogu projektu; zwraca nazwę pliku. */
    public static String saveRaster(Context c, String designId, String baseName, byte[] png) {
        try {
            File dir = assetsDir(c, designId);
            File f = new File(dir, safe(baseName) + ".png");
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(png);
            fo.close();
            return f.getName();
        } catch (Exception e) {
            return "";
        }
    }

    public static File rasterFile(Context c, String designId, String name) {
        return new File(assetsDir(c, designId), safe(name));
    }

    public static byte[] encodePng(Bitmap b) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, bo);
        return bo.toByteArray();
    }

    public static byte[] encodeJpg(Bitmap b) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 92, bo);
        return bo.toByteArray();
    }
}
