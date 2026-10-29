package pl.wojan.lasercraft;

import android.graphics.Path;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Silnik lokalny (Android): model projektu, katalogi, analiza promptu, generator kompozycji,
 * walidacja produkcyjna, kalkulator kosztów i generator napisów.
 * Format projektu .wlcproj jest identyczny z wersją Windows (JSON).
 */
public final class Core {

    // ---------------- warstwy ----------------
    public static final String L_CUT_OUTER = "CUT_OUTER";
    public static final String L_CUT_INNER = "CUT_INNER";
    public static final String L_ENGRAVE_VECTOR = "ENGRAVE_VECTOR";
    public static final String L_ENGRAVE_FILL = "ENGRAVE_FILL";
    public static final String L_ENGRAVE_RASTER = "ENGRAVE_RASTER";
    public static final String L_SCORE = "SCORE";
    public static final String L_GUIDES = "GUIDES";

    public static final String[] LAYERS = {L_CUT_OUTER, L_CUT_INNER, L_ENGRAVE_VECTOR,
            L_ENGRAVE_FILL, L_ENGRAVE_RASTER, L_SCORE, L_GUIDES};

    public static String layerLabel(String id) {
        if (L_CUT_OUTER.equals(id)) return "Cięcie zewnętrzne";
        if (L_CUT_INNER.equals(id)) return "Cięcie wewnętrzne / otwory";
        if (L_ENGRAVE_VECTOR.equals(id)) return "Grawer wektorowy";
        if (L_ENGRAVE_FILL.equals(id)) return "Grawer — wypełnienie";
        if (L_ENGRAVE_RASTER.equals(id)) return "Grawer rastrowy";
        if (L_SCORE.equals(id)) return "Bigowanie";
        return "Prowadnice";
    }

    public static String layerColor(String id) {
        if (L_CUT_OUTER.equals(id)) return "#FF0000";
        if (L_CUT_INNER.equals(id)) return "#00AAFF";
        if (L_SCORE.equals(id)) return "#FF00FF";
        if (L_GUIDES.equals(id)) return "#00FF88";
        if (L_ENGRAVE_RASTER.equals(id)) return "#808080";
        return "#000000";
    }

    public static boolean isCut(String id) {
        return L_CUT_OUTER.equals(id) || L_CUT_INNER.equals(id);
    }

    // ---------------- model ----------------
    public static class Product {
        public String id = "tabliczka", name = "Tabliczka", shape = "rounded", material = "sklejka3";
        public double w = 200, h = 150, cornerR = 6, engraveMargin = 5;
        public boolean requiresCut = true, curved = false;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("shape", shape);
                o.put("material", material);
                o.put("w", w);
                o.put("h", h);
                o.put("cornerR", cornerR);
                o.put("engraveMargin", engraveMargin);
                o.put("requiresCut", requiresCut);
                o.put("curved", curved);
            } catch (JSONException e) { }
            return o;
        }

        public static Product fromJson(JSONObject o) {
            Product p = new Product();
            if (o == null) return p;
            p.id = o.optString("id", p.id);
            p.name = o.optString("name", p.name);
            p.shape = o.optString("shape", p.shape);
            p.material = o.optString("material", p.material);
            p.w = o.optDouble("w", p.w);
            p.h = o.optDouble("h", p.h);
            p.cornerR = o.optDouble("cornerR", p.cornerR);
            p.engraveMargin = o.optDouble("engraveMargin", p.engraveMargin);
            p.requiresCut = o.optBoolean("requiresCut", p.requiresCut);
            p.curved = o.optBoolean("curved", p.curved);
            return p;
        }
    }

    public static class Machine {
        public String id = "dioda10", name = "Dioda 10 W (GRBL)", controller = "GRBL";
        public double fieldW = 400, fieldH = 400, kerf = 0.15;
        public int lpi = 254;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("controller", controller);
                o.put("fieldW", fieldW);
                o.put("fieldH", fieldH);
                o.put("kerf", kerf);
                o.put("lpi", lpi);
            } catch (JSONException e) { }
            return o;
        }

        public static Machine fromJson(JSONObject o) {
            Machine m = new Machine();
            if (o == null) return m;
            m.id = o.optString("id", m.id);
            m.name = o.optString("name", m.name);
            m.controller = o.optString("controller", m.controller);
            m.fieldW = o.optDouble("fieldW", m.fieldW);
            m.fieldH = o.optDouble("fieldH", m.fieldH);
            m.kerf = o.optDouble("kerf", m.kerf);
            m.lpi = o.optInt("lpi", m.lpi);
            return m;
        }
    }

    public static class Obj {
        public String id = "", type = "path", layer = L_ENGRAVE_VECTOR, role = "line", name = "";
        public String text = "", font = "anton", align = "center", warp = "none";
        public String d = "", qrData = "";
        public double x = 0, y = 0, sx = 1, sy = 1, rot = 0;
        public double size = 10, tracking = 0, strokeW = 0.2;
        public double qrSize = 30, imgW = 0, imgH = 0;
        public int threshold = 128;
        public String dither = "floyd";
        public boolean visible = true, locked = false;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("type", type);
                o.put("layer", layer);
                o.put("role", role);
                o.put("name", name);
                o.put("x", round(x));
                o.put("y", round(y));
                o.put("sx", sx);
                o.put("sy", sy);
                o.put("rot", rot);
                o.put("visible", visible);
                o.put("locked", locked);
                if ("text".equals(type)) {
                    o.put("text", text);
                    o.put("font", font);
                    o.put("size", round(size));
                    o.put("tracking", round(tracking));
                    o.put("align", align);
                    o.put("warp", warp);
                } else if ("qr".equals(type)) {
                    o.put("qrData", qrData);
                    o.put("qrSize", round(qrSize));
                } else if ("image".equals(type)) {
                    o.put("imgW", round(imgW));
                    o.put("imgH", round(imgH));
                    o.put("threshold", threshold);
                    o.put("dither", dither);
                }
                if (!d.isEmpty()) o.put("d", d);
                o.put("strokeW", round(strokeW));
            } catch (JSONException e) { }
            return o;
        }

        public static Obj fromJson(JSONObject o) {
            Obj b = new Obj();
            if (o == null) return b;
            b.id = o.optString("id", "");
            b.type = o.optString("type", "path");
            b.layer = o.optString("layer", L_ENGRAVE_VECTOR);
            b.role = o.optString("role", "line");
            b.name = o.optString("name", "");
            b.x = o.optDouble("x", 0);
            b.y = o.optDouble("y", 0);
            b.sx = o.optDouble("sx", 1);
            b.sy = o.optDouble("sy", 1);
            b.rot = o.optDouble("rot", 0);
            b.visible = o.optBoolean("visible", true);
            b.locked = o.optBoolean("locked", false);
            b.text = o.optString("text", "");
            b.font = o.optString("font", "anton");
            b.size = o.optDouble("size", 10);
            b.tracking = o.optDouble("tracking", 0);
            b.align = o.optString("align", "center");
            b.warp = o.optString("warp", "none");
            b.qrData = o.optString("qrData", "");
            b.qrSize = o.optDouble("qrSize", 30);
            b.imgW = o.optDouble("imgW", 0);
            b.imgH = o.optDouble("imgH", 0);
            b.threshold = o.optInt("threshold", 128);
            b.dither = o.optString("dither", "floyd");
            b.d = o.optString("d", "");
            b.strokeW = o.optDouble("strokeW", 0.2);
            return b;
        }
    }

    public static class Design {
        public String id = "", name = "Nowy projekt", prompt = "", category = "wlasne";
        public String style = "rycina", detail = "sredni", engraveType = "grawer wypełnienia";
        public String created = "", modified = "";
        public Product product = new Product();
        public Machine machine = new Machine();
        public List<Obj> objects = new ArrayList<Obj>();
        public int historyPos = -1;
        public List<String> history = new ArrayList<String>();

        public int nextId = 1;

        public String newId(String prefix) {
            return prefix + (nextId++);
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("format", "wlcproj/1");
                o.put("id", id);
                o.put("name", name);
                o.put("prompt", prompt);
                o.put("category", category);
                o.put("style", style);
                o.put("detail", detail);
                o.put("engraveType", engraveType);
                o.put("created", created);
                o.put("modified", modified);
                o.put("nextId", nextId);
                o.put("product", product.toJson());
                o.put("machine", machine.toJson());
                JSONArray arr = new JSONArray();
                for (int i = 0; i < objects.size(); i++) arr.put(objects.get(i).toJson());
                o.put("objects", arr);
                JSONArray hist = new JSONArray();
                for (int i = 0; i < history.size(); i++) hist.put(history.get(i));
                o.put("history", hist);
            } catch (JSONException e) { }
            return o;
        }

        public static Design fromJson(JSONObject o) {
            Design d = new Design();
            if (o == null) return d;
            d.id = o.optString("id", "");
            d.name = o.optString("name", "Projekt");
            d.prompt = o.optString("prompt", "");
            d.category = o.optString("category", "wlasne");
            d.style = o.optString("style", "rycina");
            d.detail = o.optString("detail", "sredni");
            d.engraveType = o.optString("engraveType", "");
            d.created = o.optString("created", "");
            d.modified = o.optString("modified", "");
            d.product = Product.fromJson(o.optJSONObject("product"));
            d.machine = Machine.fromJson(o.optJSONObject("machine"));
            d.nextId = o.optInt("nextId", 1);
            JSONArray arr = o.optJSONArray("objects");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    Obj b = Obj.fromJson(arr.optJSONObject(i));
                    d.objects.add(b);
                    if (b.id.matches("[a-z]+\\d+")) {
                        try {
                            int n = Integer.parseInt(b.id.replaceAll("[^0-9]", ""));
                            if (n >= d.nextId) d.nextId = n + 1;
                        } catch (Exception e) { }
                    }
                }
            }
            JSONArray hist = o.optJSONArray("history");
            if (hist != null) for (int i = 0; i < hist.length(); i++) d.history.add(hist.optString(i, ""));
            d.historyPos = d.history.size() - 1;
            return d;
        }

        public void pushHistory(String label) {
            while (history.size() > historyPos + 1) history.remove(history.size() - 1);
            history.add(label);
            historyPos = history.size() - 1;
        }

        public Obj findByName(String n) {
            for (int i = 0; i < objects.size(); i++) if (n.equals(objects.get(i).name)) return objects.get(i);
            return null;
        }
    }

    static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // ---------------- katalogi ----------------
    public static class ProductDef {
        public String id, name, shape, material;
        public double w, h, cornerR, margin;
        public boolean cut, curved;

        ProductDef(String id, String name, String shape, String material, double w, double h,
                   double cornerR, double margin, boolean cut, boolean curved) {
            this.id = id; this.name = name; this.shape = shape; this.material = material;
            this.w = w; this.h = h; this.cornerR = cornerR; this.margin = margin;
            this.cut = cut; this.curved = curved;
        }
    }

    public static List<ProductDef> products() {
        List<ProductDef> l = new ArrayList<ProductDef>();
        l.add(new ProductDef("tabliczka", "Tabliczka", "rounded", "sklejka3", 200, 150, 6, 5, true, false));
        l.add(new ProductDef("brelok", "Brelok", "rounded", "sklejka3", 40, 40, 6, 4, true, false));
        l.add(new ProductDef("zawieszka", "Zawieszka", "tag", "sklejka3", 60, 80, 4, 4, true, false));
        l.add(new ProductDef("medal", "Medal", "circle", "akryl", 60, 60, 0, 4, true, false));
        l.add(new ProductDef("podkladka", "Podkładka", "circle", "sklejka3", 100, 100, 0, 5, true, false));
        l.add(new ProductDef("ozdoba", "Ozdoba świąteczna", "tag", "sklejka3", 80, 90, 4, 4, true, false));
        l.add(new ProductDef("zakladka", "Zakładka", "rounded", "sklejka2", 50, 150, 5, 4, true, false));
        l.add(new ProductDef("magnes", "Magnes", "rounded", "sklejka3", 60, 45, 6, 4, true, false));
        l.add(new ProductDef("skrzynka", "Skrzynka", "rect", "sklejka5", 250, 160, 4, 8, true, false));
        l.add(new ProductDef("piersiowka", "Piersiówka", "rect", "stal", 95, 115, 0, 5, false, true));
        l.add(new ProductDef("kubek", "Kubek", "rect", "ceramika", 210, 90, 0, 5, false, true));
        l.add(new ProductDef("deska", "Deska kuchenna", "rounded", "drewno", 300, 200, 20, 10, false, false));
        l.add(new ProductDef("etui", "Etui skórzane", "rounded", "skora", 110, 80, 10, 6, false, false));
        l.add(new ProductDef("portfel", "Portfel", "rect", "skora", 120, 95, 8, 6, false, false));
        l.add(new ProductDef("medalion", "Medalion", "circle", "stal", 40, 40, 0, 3, false, false));
        l.add(new ProductDef("szyld", "Szyld", "rect", "sklejka5", 300, 150, 4, 8, true, false));
        l.add(new ProductDef("tarcza", "Tarcza / emblemat", "shield", "sklejka3", 150, 170, 4, 6, true, false));
        l.add(new ProductDef("naszywka", "Naszywka skórzana", "rounded", "skora", 80, 40, 8, 4, true, false));
        l.add(new ProductDef("niesmiertelnik", "Nieśmiertelnik", "rounded", "stal", 50, 28, 5, 2.5, true, false));
        l.add(new ProductDef("dowolny", "Dowolny przedmiot", "rect", "sklejka3", 150, 100, 3, 5, false, false));
        return l;
    }

    public static ProductDef product(String id) {
        List<ProductDef> l = products();
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(id)) return l.get(i);
        return l.get(0);
    }

    public static class MaterialDef {
        public String id, name, warning;
        public double thickness, costPerM2;
        public boolean cuts, engraves, safe;

        MaterialDef(String id, String name, double thickness, boolean cuts, boolean engraves,
                    boolean safe, String warning, double costPerM2) {
            this.id = id; this.name = name; this.thickness = thickness; this.cuts = cuts;
            this.engraves = engraves; this.safe = safe; this.warning = warning;
            this.costPerM2 = costPerM2;
        }
    }

    public static List<MaterialDef> materials() {
        List<MaterialDef> l = new ArrayList<MaterialDef>();
        l.add(new MaterialDef("sklejka3", "Sklejka brzozowa 3 mm", 3, true, true, true, "", 45));
        l.add(new MaterialDef("sklejka5", "Sklejka brzozowa 5 mm", 5, true, true, true, "", 52));
        l.add(new MaterialDef("sklejka2", "Sklejka brzozowa 2 mm", 2, true, true, true, "", 40));
        l.add(new MaterialDef("mdf3", "MDF 3 mm", 3, true, true, false, "Klej formaldehydowy — używaj wyciągu.", 32));
        l.add(new MaterialDef("akryl", "Akryl (PMMA) 3 mm", 3, true, true, true, "Cięcie akrylu — zalecana wentylacja.", 120));
        l.add(new MaterialDef("drewno", "Drewno lite", 20, false, true, true, "", 90));
        l.add(new MaterialDef("stal", "Stal nierdzewna", 1, false, true, true,
                "Grawer wymaga lasera fiber lub pasty do znakowania na diodzie.", 0));
        l.add(new MaterialDef("ceramika", "Ceramika", 5, false, true, true, "", 0));
        l.add(new MaterialDef("skora", "Skóra roślinna", 2, true, true, true, "Nie używaj skóry chromowanej.", 200));
        l.add(new MaterialDef("filc", "Filc", 3, true, true, true, "", 60));
        l.add(new MaterialDef("guma", "Guma", 2, true, true, false, "Dym bogaty w sadzę — mocny wyciąg.", 80));
        l.add(new MaterialDef("papier", "Papier / karton", 0.4, true, true, true, "", 8));
        l.add(new MaterialDef("pvc", "PVC / PCW", 3, false, false, false,
                "NIGDY nie tnij ani nie graweruj PVC — wydziela chlorowodór.", 0));
        return l;
    }

    public static MaterialDef material(String id) {
        List<MaterialDef> l = materials();
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(id)) return l.get(i);
        return l.get(0);
    }

    public static class MachineDef {
        public String id, name, controller, note;
        public double fieldW, fieldH, kerf;
        public int lpi;

        MachineDef(String id, String name, double fw, double fh, double kerf, int lpi, String controller, String note) {
            this.id = id; this.name = name; this.fieldW = fw; this.fieldH = fh;
            this.kerf = kerf; this.lpi = lpi; this.controller = controller; this.note = note;
        }
    }

    public static List<MachineDef> machines() {
        List<MachineDef> l = new ArrayList<MachineDef>();
        l.add(new MachineDef("dioda10", "Dioda 10 W (GRBL)", 400, 400, 0.15, 254, "GRBL", "Typowy ploter diodowy."));
        l.add(new MachineDef("dioda20", "Dioda 20 W (GRBL)", 400, 400, 0.20, 254, "GRBL", "Mocniejsza dioda."));
        l.add(new MachineDef("co2_600", "CO₂ 60 W (Ruida)", 600, 400, 0.25, 318, "Ruida", "Eksport DXF/SVG (bez G-code)."));
        l.add(new MachineDef("fiber20", "Fiber 20 W", 110, 110, 0.05, 500, "Fiber", "Metale — eksport DXF/SVG."));
        l.add(new MachineDef("custom", "Własna maszyna", 300, 300, 0.15, 254, "Custom",
                "Sprawdź pole robocze i kerf przed uruchomieniem lasera."));
        return l;
    }

    public static MachineDef machine(String id) {
        List<MachineDef> l = machines();
        for (int i = 0; i < l.size(); i++) if (l.get(i).id.equals(id)) return l.get(i);
        return l.get(0);
    }

    public static class CategoryDef {
        public String id, name, emblem, blurb;

        CategoryDef(String id, String name, String emblem, String blurb) {
            this.id = id; this.name = name; this.emblem = emblem; this.blurb = blurb;
        }
    }

    public static List<CategoryDef> categories() {
        List<CategoryDef> l = new ArrayList<CategoryDef>();
        l.add(new CategoryDef("wlasne", "Własny pomysł", "kolo", "Dowolny motyw — opisz go słowami."));
        l.add(new CategoryDef("wedkarstwo", "Wędkarstwo", "ryba", "Szczupaki, karasie, łowiska."));
        l.add(new CategoryDef("myslistwo", "Myślistwo", "jelen", "Jelenie, wilki, koła łowieckie."));
        l.add(new CategoryDef("wilk", "Wilki", "wilk", "Wilcza wataha, wilcze oczy."));
        l.add(new CategoryDef("konie", "Konie", "kon", "Konie, podkowy, jeździectwo."));
        l.add(new CategoryDef("psy", "Psy", "lapa", "Rasy, łapy, tabliczki."));
        l.add(new CategoryDef("koty", "Koty", "kot", "Koty i kocie cytaty."));
        l.add(new CategoryDef("zwierzeta", "Zwierzęta", "ptak", "Ptaki, lisy, jeże."));
        l.add(new CategoryDef("rolnictwo", "Rolnictwo", "traktor", "Traktory, gospodarstwa."));
        l.add(new CategoryDef("motoryzacja", "Motoryzacja", "auto", "Auta, motocykle, garaże."));
        l.add(new CategoryDef("muzyka", "Muzyka", "gitara", "Gitary, nuty, zespoły."));
        l.add(new CategoryDef("gaming", "Gaming", "pad", "Pady, konsole, nicki."));
        l.add(new CategoryDef("sport", "Sport", "kolo", "Siłownia, bieganie, medale."));
        l.add(new CategoryDef("rower", "Rower", "kolo", "Rowery, trasy, MTB."));
        l.add(new CategoryDef("wedrowki", "Wędrówki", "gory", "Góry, szczyty, szlaki."));
        l.add(new CategoryDef("przyroda", "Przyroda", "drzewo", "Lasy, drzewa, liście."));
        l.add(new CategoryDef("morze", "Morze", "kotwica", "Kotwice, fale, żagle."));
        l.add(new CategoryDef("wojsko", "Wojsko", "orzel", "Orły, godła, motto."));
        l.add(new CategoryDef("patriotyczne", "Patriotyczne", "orzel", "Orzeł, flaga, rocznice."));
        l.add(new CategoryDef("slask", "Śląsk", "kolo", "Godło śląskie, gwara."));
        l.add(new CategoryDef("kaszuby", "Kaszuby", "kotwica", "Kaszubskie wzory."));
        l.add(new CategoryDef("gorale", "Górale", "gory", "Ornamenty góralskie."));
        l.add(new CategoryDef("slowianskie", "Słowiańskie", "kolo", "Swarga, symbole słowiańskie."));
        l.add(new CategoryDef("religijne", "Religijne", "serce", "Krzyże, anioły, cytaty."));
        l.add(new CategoryDef("pierwsza_komunia", "Pierwsza Komunia", "serce", "Pamiątki komunijne."));
        l.add(new CategoryDef("chrzest", "Chrzest", "serce", "Pamiątki chrztu."));
        l.add(new CategoryDef("slub", "Ślub", "serce", "Zaproszenia, monogramy."));
        l.add(new CategoryDef("walentynki", "Walentynki", "serce", "Serca i wyznania."));
        l.add(new CategoryDef("dzien_matki", "Dzień Matki", "roza", "Róże i podziękowania."));
        l.add(new CategoryDef("dzien_ojca", "Dzień Ojca", "kubek", "Kubki i narzędzia."));
        l.add(new CategoryDef("urodziny", "Urodziny", "serce", "Życzenia i imiona."));
        l.add(new CategoryDef("rocznice", "Rocznice", "serce", "Jubileusze i daty."));
        l.add(new CategoryDef("boze_narodzenie", "Boże Narodzenie", "drzewo", "Choinki, śnieżynki."));
        l.add(new CategoryDef("wielkanoc", "Wielkanoc", "serce", "Pisanki, zajączki."));
        l.add(new CategoryDef("halloween", "Halloween", "dynia", "Dynie, duchy, czaszki."));
        l.add(new CategoryDef("gotyk", "Gotyk", "czaszka", "Czaszki, memento mori."));
        l.add(new CategoryDef("fantastyka", "Fantastyka", "smok", "Smoki, rycerze, herby."));
        l.add(new CategoryDef("sredniowiecze", "Średniowiecze", "czaszka", "Herby, tarcze, zamki."));
        l.add(new CategoryDef("retro", "Retro i vintage", "kubek", "Lata 50., neony."));
        l.add(new CategoryDef("kuchnia", "Kuchnia", "kubek", "Deski, przepisy, kawy."));
        l.add(new CategoryDef("ogrod", "Ogród", "drzewo", "Tabliczki ziół."));
        l.add(new CategoryDef("dom_rodzina", "Dom i rodzina", "serce", "Domowe zasady, imiona."));
        l.add(new CategoryDef("firma", "Firma i logo", "kolo", "Logo, wizytówki."));
        l.add(new CategoryDef("restauracja", "Gastronomia", "kubek", "Menu, tabliczki."));
        l.add(new CategoryDef("fotograwer", "Fotograwer", "ptak", "Zdjęcia i portrety."));
        l.add(new CategoryDef("bizuteria", "Biżuteria", "serce", "Zawieszki, medaliony."));
        l.add(new CategoryDef("dzieci", "Dla dzieci", "ptak", "Imiona, misie."));
        l.add(new CategoryDef("szkola", "Szkoła", "kolo", "Nagrody, dyplomy."));
        l.add(new CategoryDef("narzedzia", "Narzędzia", "kolo", "Tabliczki do skrzynek."));
        l.add(new CategoryDef("qr_kontakt", "QR i kontakt", "kolo", "Wizytówki QR, Wi-Fi."));
        return l;
    }

    public static String[] safetyNotes() {
        return new String[]{
                "PVC / PCW — NIGDY nie tnij ani nie graweruj: wydziela chlorowodór i dioksyny.",
                "ABS — przy cięciu wydziela cyjanowodór; tylko grawer z mocnym wyciągiem.",
                "Skóra chromowana — sole chromu są toksyczne; używaj skóry roślinnej.",
                "PTFE / teflon — zakaz obróbki laserowej (silnie toksyczne opary).",
                "MDF — klej formaldehydowy; przy grubszych cięciach używaj wyciągu.",
                "Guma — dym bogaty w sadzę; krótkie serie i wyciąg."
        };
    }

    public static String[] ideas() {
        return new String[]{
                "Tabliczka wędkarska: szczupak, napis KRÓL ŁOWISKA, na dole RYBY SIĘ MNIE BOJĄ, 200×150 mm",
                "Brelok z imieniem KASIA, serce, 40×40 mm, sklejka 3 mm, z otworem na kółko",
                "Medal biegowy, napis BIEG PO MARZENIA, na dole 2026, 60×60 mm, akryl",
                "Piersiówka z grawerem: wilk, napis WILKI NIE ŚPIĄ, tylko grawer bez cięcia, 95×115 mm",
                "Deska kuchenna z napisem KUCHNIA BABCI, motyw ziół, 300×200 mm, grawer",
                "Zakładka do książki z cytatem CZYTAM, WIĘC JESTEM, motyw lasu, 50×150 mm",
                "Ozdoba choinkowa: śnieżynka, napis WESOŁYCH ŚWIĄT, 80×90 mm, sklejka 3 mm",
                "Magnes na lodówkę, napis KTO TU RZĄDZI? JA, 60×45 mm, komiks",
                "Tabliczka z kodem QR do linku https://wojan.pl i napis ZESKANUJ MNIE, 100×100 mm",
                "Medalion 40×40 mm, napis 10 LAT RAZEM, serce, stal, tylko grawer",
                "Kubek z grawerem: wędka, napis NAJLEPSZY TATA, tylko grawer, 210×90 mm",
                "Dyplom szkolny, napis MISTRZ MATEMATYKI, ramka elegancka, 200×150 mm",
                "Tabliczka dla psa: łapa, napis BUREK, na dole MAM DOM, 120×80 mm",
                "Tabliczka ogrodowa: motyw ziół, napis BAZYLIA, 80×40 mm, sklejka 3 mm",
                "Herb rodzinny, orzeł, napis SEMPER FIDELIS, 200×150 mm, rycina",
                "Podkładka pod kubek: koło zębate, napis WOJAN, 100×100 mm, sklejka 3 mm",
                "Tabliczka motoryzacyjna: auto, napis MÓJ GARAŻ, MOJE KRÓLESTWO, 200×150 mm",
                "Zawieszka na choinkę: dynia, napis HALLOWEEN, 60×80 mm",
                "Tabliczka dla wędkarza: szczupak, napis WĘDKUJĘ, WIĘC JESTEM, 200×150 mm",
                "Dyplom rocznicowy: serce, napis 25 LAT RAZEM, 200×150 mm, elegancki"
        };
    }

    // ---------------- analiza promptu ----------------
    public static class Spec {
        public String raw = "", category = "wlasne", productId = "tabliczka", material = "sklejka3";
        public String style = "rycina", detail = "sredni", engravetype = "grawer wypełnienia";
        public String topText = "", bottomText = "", subject = "", frame = "retro", font = "anton";
        public String shape = "";
        public String machineId = "dioda10";
        public double width = 200, height = 150;
        public boolean dimsGiven = false, cutGiven = false, requiresCut = true;
    }

    static String norm(String s) {
        if (s == null) return "";
        String low = s.toLowerCase(new Locale("pl", "PL"));
        low = low.replace('ą', 'a').replace('ć', 'c').replace('ę', 'e').replace('ł', 'l')
                .replace('ń', 'n').replace('ó', 'o').replace('ś', 's').replace('ź', 'z').replace('ż', 'z');
        low = low.replace('×', 'x').replace('—', '-').replace('–', '-');
        return low;
    }

    static boolean has(String hay, String needle) {
        return hay.indexOf(needle) >= 0;
    }

    /** Pozycja słowa kluczowego z kontrolą granic słowa (−1 gdy brak).
     *  Bardzo krótkie hasła (≤3 znaki) muszą być całymi wyrazami: "gra" nie złapie "grawer",
     *  ale dłuższe rdzenie ("wilk", "desk", "tabliczk") łapią odmianę ("wilkiem", "deska"). */
    static int findKeyword(String hay, String kw) {
        if (hay == null || kw == null || kw.isEmpty()) return -1;
        boolean shortKw = kw.length() <= 3;
        int from = 0;
        while (true) {
            int p = hay.indexOf(kw, from);
            if (p < 0) return -1;
            boolean leftOk = p == 0 || !Character.isLetterOrDigit(hay.charAt(p - 1));
            int e = p + kw.length();
            boolean rightOk = e >= hay.length() || !Character.isLetterOrDigit(hay.charAt(e));
            if (leftOk && (!shortKw || rightOk)) return p;
            from = p + 1;
        }
    }

    /** Pozycja słowa kluczowego rozpoczynającego się na granicy słowa (−1 gdy brak). */
    static int wordStart(String hay, String kw) {
        int from = 0;
        while (true) {
            int p = hay.indexOf(kw, from);
            if (p < 0) return -1;
            boolean boundary = p == 0 || !Character.isLetter(hay.charAt(p - 1));
            if (boundary) return p;
            from = p + 1;
        }
    }

    public static Spec analyze(String prompt) {
        Spec s = new Spec();
        s.raw = prompt == null ? "" : prompt;
        String low = norm(prompt);

        // wymiary: 200x150, 200 x 150 mm, 20x15 cm
        for (int i = 0; i + 2 < low.length(); i++) {
            if (!Character.isDigit(low.charAt(i))) continue;
            int j = i;
            while (j < low.length() && (Character.isDigit(low.charAt(j)) || low.charAt(j) == '.')) j++;
            if (j < low.length() && low.charAt(j) == 'x') {
                int k = j + 1;
                while (k < low.length() && low.charAt(k) == ' ') k++;
                int st = k;
                while (k < low.length() && (Character.isDigit(low.charAt(k)) || low.charAt(k) == '.')) k++;
                if (k > st) {
                    try {
                        double w = Double.parseDouble(low.substring(i, j));
                        double h = Double.parseDouble(low.substring(st, k));
                        String tail = low.substring(k, Math.min(low.length(), k + 4));
                        if (has(tail, "cm")) { w *= 10; h *= 10; }
                        if (w > 5 && h > 5 && w < 2000 && h < 2000) {
                            s.width = w; s.height = h; s.dimsGiven = true;
                            break;
                        }
                    } catch (Exception e) { }
                }
            }
            i = j;
        }

        // produkt
        String[][] prods = {
                {"piersiówk|piersiowk", "piersiowka"}, {"kubek", "kubek"},
                {"deska|deski|deske|desku|deseczka", "deska"},
                {"brelok", "brelok"}, {"zawiesz", "zawieszka"}, {"medal", "medal"},
                {"podkladk", "podkladka"}, {"ozdob", "ozdoba"}, {"zakladk", "zakladka"},
                {"magnes", "magnes"}, {"skrzynk", "skrzynka"}, {"medalion", "medalion"},
                {"portfel", "portfel"}, {"etui", "etui"}, {"tabliczk", "tabliczka"},
                {"naszywk", "naszywka"}, {"niesmiertelnik", "niesmiertelnik"},
                {"szyld", "szyld"}, {"tarcz", "tarcza"}, {"emblemat", "tarcza"},
                {"pisank|pisanka", "ozdoba"}, {"jajko|jajka|jajek", "ozdoba"}, {"amulet|talizman", "medalion"},
                {"dowoln", "dowolny"}
        };
        // decyduje NAJWCZEŚNIEJSZE wystąpienie, nie kolejność tabeli
        // ("Podkładka pod kubek" → podkładka, nie kubek)
        int bestPos = Integer.MAX_VALUE;
        for (int i = 0; i < prods.length; i++) {
            String[] alts = prods[i][0].split("\\|");
            for (int k = 0; k < alts.length; k++) {
                int pos = findKeyword(low, alts[k]);
                if (pos >= 0 && pos < bestPos) {
                    bestPos = pos;
                    s.productId = prods[i][1];
                }
            }
        }
        if (has(low, "tylko grawer") || has(low, "bez ciecia") || has(low, "bez wycinania")) {
            s.cutGiven = true;
            s.requiresCut = false;
        } else if (has(low, "wytnij") || has(low, "wycieni") || has(low, "cięcie") || has(low, "ciecie")) {
            s.cutGiven = true;
            s.requiresCut = true;
        }
        ProductDef pd = product(s.productId);
        if (!s.dimsGiven) { s.width = pd.w; s.height = pd.h; }
        if (!s.cutGiven) s.requiresCut = pd.cut;
        s.material = pd.material;

        // kategoria
        String[][] cats = {
                {"wedk", "wedkarstwo"}, {"ryb", "wedkarstwo"}, {"szczupak", "wedkarstwo"},
                {"mysliw", "myslistwo"}, {"jelen", "myslistwo"}, {"wilk", "wilk"},
                {"kon", "konie"}, {"pies", "psy"}, {"kot", "koty"}, {"zwierz", "zwierzeta"},
                {"rolnik", "rolnictwo"}, {"traktor", "rolnictwo"}, {"auto", "motoryzacja"},
                {"motocykl", "motoryzacja"}, {"gitar", "muzyka"}, {"muzyk", "muzyka"},
                {"gaming", "gaming"}, {"gry", "gaming"}, {"graj", "gaming"}, {"sport", "sport"},
                {"bieg", "sport"}, {"rower", "rower"},
                {"gory", "wedrowki"}, {"gor", "wedrowki"}, {"las", "przyroda"}, {"drzew", "przyroda"},
                {"morze", "morze"}, {"zagiel", "morze"}, {"wojsk", "wojsko"},
                {"polsk", "patriotyczne"}, {"orzel", "patriotyczne"}, {"slask", "slask"},
                {"slowian", "slowianskie"}, {"religi", "religijne"}, {"komuni", "pierwsza_komunia"},
                {"chrzest", "chrzest"}, {"slub", "slub"}, {"walentynk", "walentynki"},
                {"mama", "dzien_matki"}, {"matk", "dzien_matki"}, {"tata", "dzien_ojca"},
                {"ojc", "dzien_ojca"}, {"urodzin", "urodziny"}, {"rocznic", "rocznice"},
                {"swieta", "boze_narodzenie"}, {"choink", "boze_narodzenie"},
                {"wielkanoc", "wielkanoc"}, {"halloween", "halloween"}, {"dynia", "halloween"},
                {"gotyk", "gotyk"}, {"czask", "gotyk"}, {"smok", "fantastyka"},
                {"herb", "sredniowiecze"}, {"retro", "retro"}, {"kuchni", "kuchnia"},
                {"ogrod", "ogrod"}, {"dom", "dom_rodzina"}, {"rodzin", "dom_rodzina"},
                {"logo", "firma"}, {"firm", "firma"}, {"menu", "restauracja"},
                {"zdjeci", "fotograwer"}, {"foto", "fotograwer"}, {"zawieszk", "bizuteria"},
                {"dzieci", "dzieci"}, {"szkol", "szkola"}, {"dyplom", "szkola"},
                {"narzedzi", "narzedzia"}, {"qr", "qr_kontakt"}
        };
        int bestCat = Integer.MAX_VALUE;
        for (int i = 0; i < cats.length; i++) {
            String[] alts = cats[i][0].split("\\|");
            for (int k = 0; k < alts.length; k++) {
                int pos = findKeyword(low, alts[k]);
                if (pos >= 0 && pos < bestCat) {
                    bestCat = pos;
                    s.category = cats[i][1];
                }
            }
        }

        // wybrane okazje mają pierwszeństwo nad motywem (np. "dla taty" > "jeleń")
        if (has(low, "choink") || has(low, "swieta") || has(low, "gwiazdk") || has(low, "boze narodzenie"))
            s.category = "boze_narodzenie";
        else if (has(low, "wielkanoc") || has(low, "pisank") || has(low, "zajac"))
            s.category = "wielkanoc";
        else if (has(low, "halloween") || has(low, "dyni")) s.category = "halloween";
        else if (has(low, "walentynk") || has(low, "milosc")) s.category = "walentynki";
        else if (has(low, "urodzin")) s.category = "urodziny";
        else if (has(low, "rocznic")) s.category = "rocznice";
        else if (has(low, "dla taty") || has(low, "tata") || has(low, "ojciec") || has(low, "ojca")) s.category = "dzien_ojca";
        else if (has(low, "dla mamy") || has(low, "mama") || has(low, "matk")) s.category = "dzien_matki";
        else if (has(low, "komuni")) s.category = "pierwsza_komunia";
        else if (has(low, "chrzest") || has(low, "chrztu")) s.category = "chrzest";
        else if (has(low, "slub") || has(low, "ślub")) s.category = "slub";

        // styl
        if (has(low, "rycin") || has(low, "engrav")) s.style = "rycina";
        if (has(low, "retro") || has(low, "vintage")) s.style = "retro";
        if (has(low, "gotyk")) s.style = "gotyk";
        if (has(low, "eleganck")) s.style = "elegancki";
        if (has(low, "komiks")) s.style = "komiks";
        if (has(low, "minimal")) s.style = "minimal";
        if (has(low, "fantasy")) s.style = "fantasy";
        if (has(low, "slowian")) s.style = "slowianskie";
        if (has(low, "wysoki") || has(low, "bogaty")) s.detail = "wysoki";
        if (has(low, "niski") || has(low, "prosty")) s.detail = "niski";
        if (has(low, "fotograwer") || has(low, "rastr")) s.engravetype = "fotograwer rastrowy";

        // napisy: WERSALIKI
        List<String> caps = capsFragments(s.raw);
        // "napis X"
        int np = low.indexOf("napis");
        String afterNapis = "";
        if (np >= 0) {
            int from = Math.min(s.raw.length(), np + 5);
            int end = from;
            while (end < s.raw.length()) {
                char c = s.raw.charAt(end);
                if (c == ',' || c == ';' || c == '\n') break;
                // kropka kończy napis tylko wtedy, gdy po niej jest spacja lub koniec
                if (c == '.' && (end + 1 >= s.raw.length() || s.raw.charAt(end + 1) == ' ')) break;
                end++;
            }
            afterNapis = s.raw.substring(from, end).trim();
            while (afterNapis.startsWith(":") || afterNapis.startsWith(" ")) afterNapis = afterNapis.substring(1).trim();
            afterNapis = stripLeadWord(afterNapis);
        }
        // kolejność: jawne "napis X" → cudzysłowy → WERSALIKI
        List<String> quoted = new ArrayList<String>();
        for (int i = 0; i + 1 < s.raw.length(); i++) {
            char c = s.raw.charAt(i);
            if (c != '"' && c != '\u201E' && c != '\u201C') continue;
            int e = s.raw.indexOf(c == '\u201E' ? '\u201D' : '"', i + 1);
            if (e > i + 1) {
                quoted.add(s.raw.substring(i + 1, e).trim());
                i = e;
            }
        }
        if (!afterNapis.isEmpty()) s.topText = afterNapis;
        else if (!quoted.isEmpty()) s.topText = quoted.get(0);
        else if (!caps.isEmpty()) s.topText = caps.get(0);
        if (s.bottomText.isEmpty()) {
            if (quoted.size() > 1) s.bottomText = quoted.get(1);
            else if (caps.size() > 1) s.bottomText = caps.get(1);
        }
        s.topText = stripTail(cutInstructions(cutAtSize(s.topText)));
        s.bottomText = stripTail(cutInstructions(cutAtSize(s.bottomText)));
        if (s.topText.isEmpty()) s.topText = "";
        s.subject = subjectFromPrompt(low);
        if (s.subject.isEmpty()) s.subject = subjectFor(s.category);
        s.frame = frameFor(s.style);
        s.font = fontFor(s.style);
        if (has(low, "fiber")) s.machineId = "fiber20";
        else if (has(low, "co2") || has(low, "co₂")) s.machineId = "co2_600";
        else if (has(low, "20 w")) s.machineId = "dioda20";
        return s;
    }

    /** Skraca napis do maxLen znaków, najlepiej na granicy słowa. */
    static String shorten(String t, int maxLen) {
        if (t == null) return "";
        String v = t.trim();
        if (v.length() <= maxLen) return v;
        int cut = v.lastIndexOf(' ', maxLen);
        if (cut < maxLen / 2) cut = maxLen;
        return v.substring(0, cut).trim();
    }

    static String stripLeadWord(String s) {
        String low = norm(s);
        String[] leads = {"napis", "tekst", "tresc"};
        for (int i = 0; i < leads.length; i++) {
            if (low.startsWith(leads[i])) {
                String r = s.substring(Math.min(s.length(), leads[i].length())).trim();
                if (r.startsWith(":") || r.startsWith("=")) r = r.substring(1).trim();
                return r;
            }
        }
        return s;
    }

    /** Fragmenty napisane WERSALIKAMI — typowy sposób podawania napisu w prompcie.
     *  Dopuszczamy cyfry, spacje i interpunkcję (daty 12.06.2027, numery 12-06),
     *  a na końcu odcinamy doklejone wymiary ("… 2027 200" + "x150"). */
    static List<String> capsFragments(String raw) {
        List<String> out = new ArrayList<String>();
        if (raw == null) return out;
        StringBuilder cur = new StringBuilder();
        String pl = "ĄĆĘŁŃÓŚŹŻ";
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean upper = (c >= 'A' && c <= 'Z') || pl.indexOf(c) >= 0;
            boolean digit = c >= '0' && c <= '9';
            boolean sep = c == ' ' || c == '!' || c == '?' || c == ',' || c == '.' ||
                    c == '-' || c == ':' || c == '/';
            if (upper || digit) {
                cur.append(c);
            } else if (sep) {
                if ((c == '.' || c == '-' || c == '/' || c == ':') && cur.length() > 0 &&
                        cur.charAt(cur.length() - 1) != ' ') {
                    cur.append(c);
                } else if (cur.length() > 0) {
                    cur.append(' ');
                }
            } else {
                flushCaps(out, cur.toString(), raw);
                cur.setLength(0);
            }
        }
        flushCaps(out, cur.toString(), raw);
        return out;
    }

    static void flushCaps(List<String> out, String frag, String raw) {
        String t = stripDimToken(frag.trim(), raw);
        if (t.length() < 3) return;
        int upper = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= 'A' && c <= 'Z') || "ĄĆĘŁŃÓŚŹŻ".indexOf(c) >= 0) upper++;
        }
        if (upper >= 2) out.add(t);
    }

    /** Usuwa końcowy token będący wymiarem ("... 2027 200" przy "200x150"). */
    static String stripDimToken(String t, String raw) {
        int sp = t.lastIndexOf(' ');
        if (sp < 0) return t;
        String tail = t.substring(sp + 1);
        if (tail.length() < 2 || tail.length() > 5) return t;
        for (int i = 0; i < tail.length(); i++) if (!Character.isDigit(tail.charAt(i))) return t;
        String rawLow = raw == null ? "" : norm(raw);
        if (rawLow.contains(tail + "x") || rawLow.contains(tail + " x")) return t.substring(0, sp).trim();
        return t;
    }

    /** Odcina fragment wymiarów doklejony do napisu: "ANNA 200x150 mm" → "ANNA". */
    static String cutAtSize(String t) {
        if (t == null) return "";
        for (int i = 1; i + 1 < t.length(); i++) {
            char c = t.charAt(i);
            if (c != 'x' && c != 'X') continue;
            if (!Character.isDigit(t.charAt(i - 1))) continue;
            if (!Character.isDigit(t.charAt(i + 1))) continue;
            String head = t.substring(0, i).trim();
            while (head.length() > 0 && (head.charAt(head.length() - 1) == ' ' ||
                    head.charAt(head.length() - 1) == ',' || head.charAt(head.length() - 1) == ';' ||
                    head.charAt(head.length() - 1) == ':' || head.charAt(head.length() - 1) == '-'))
                head = head.substring(0, head.length() - 1).trim();
            while (head.length() > 0 && Character.isDigit(head.charAt(head.length() - 1)))
                head = head.substring(0, head.length() - 1);
            return head.trim();
        }
        return t;
    }

    /** Odcina końcowe instrukcje techniczne wciągnięte do napisu
     *  ("WILK Z BIESZCZAD, tylko grawer bez cięcia", "... sklejka 3 mm"). */
    static String cutInstructions(String t) {
        if (t == null) return "";
        String[] stop = {"tylko", "bez", "grawer", "cien", "cut", "sklejk", "mdf", "skor",
                "stal", "nierdzew", "alumini", "akryl", "plexi", "ceramik", "drewn", "szkl",
                "material", "wymiar", "wariant", "styl", "ramka", "font", "czcionk"};
        String[] parts = t.split(",");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String seg = parts[i].trim();
            if (seg.isEmpty()) continue;
            if (i > 0) {
                String n = norm(seg);
                boolean instr = false;
                for (int k = 0; k < stop.length; k++) if (n.contains(stop[k])) { instr = true; break; }
                if (!instr) {
                    String n2 = n;
                    if (n2.matches(".*\\bmm\\b.*") || n2.matches(".*\\bcm\\b.*")) instr = true;
                }
                if (instr) break;
            }
            if (out.length() > 0) out.append(", ");
            out.append(seg);
        }
        return out.toString().trim();
    }

    /** Usuwa końcową interpunkcję z napisu. */
    static String stripTail(String t) {
        if (t == null) return "";
        int e = t.length();
        while (e > 0 && (t.charAt(e - 1) == ',' || t.charAt(e - 1) == ';' || t.charAt(e - 1) == ':' ||
                t.charAt(e - 1) == '.' || t.charAt(e - 1) == '-' || t.charAt(e - 1) == ' ')) e--;
        return t.substring(0, e).trim();
    }

    /** Motyw wprost z promptu ("motyw jelenia", "z wilkiem", "szczupak") — ma pierwszeństwo. */
    static String subjectFromPrompt(String low) {
        String[][] map = {
                {"szczupak", "ryba"}, {"ryb", "ryba"}, {"karp", "ryba"}, {"okon", "ryba"},
                {"wilk", "wilk"}, {"jelen", "jelen"}, {"rogacz", "jelen"}, {"los", "jelen"},
                {"traktor", "traktor"}, {"rolnik", "traktor"},
                {"samochod", "auto"}, {"auto", "auto"}, {"motocykl", "auto"}, {"motor", "auto"},
                {"gitar", "gitara"}, {"muzyk", "gitara"},
                {"pad", "pad"}, {"kontroler", "pad"},
                {"czask", "czaszka"}, {"czaszka", "czaszka"}, {"trupia", "czaszka"},
                {"roz", "roza"}, {"kwiat", "roza"}, {"kwiatek", "roza"},
                {"drzew", "drzewo"}, {"las", "drzewo"},
                {"gor", "gory"}, {"szczyt", "gory"},
                {"koron", "korona"}, {"krol", "korona"},
                {"orzel", "orzel"}, {"godlo", "orzel"},
                {"dyni", "dynia"}, {"halloween", "dynia"},
                {"serc", "serce"}, {"milosc", "serce"}, {"milosc", "serce"},
                {"lapa", "lapa"}, {"pies", "lapa"}, {"kot", "lapa"},
                {"ptak", "ptak"}, {"golab", "ptak"}, {"orzel", "orzel"},
                {"kon", "kon"}, {"konia", "kon"},
                {"smok", "smok"}, {"smoka", "smok"},
                {"kotwic", "kotwica"}, {"zagiel", "kotwica"},
                {"kolo zebate", "kolo_zebate"}, {"zebat", "kolo_zebate"}, {"mechanik", "kolo_zebate"},
                {"choink", "choinka"}, {"choinka", "choinka"},
                {"jajk", "jajko"}, {"pisank", "jajko"}, {"zajac", "jajko"},
                {"obraczk", "obraczki"}, {"obraczki", "obraczki"},
                {"medal", "medal"}, {"puchar", "medal"},
                {"mandala", "kolo"}, {"kolo", "kolo"}, {"okrag", "kolo"}
        };
        for (int i = 0; i < map.length; i++) {
            if (findKeyword(low, map[i][0]) >= 0) return map[i][1];
        }
        return "";
    }

    static String subjectFor(String cat) {
        if (cat.equals("boze_narodzenie")) return "choinka";
        if (cat.equals("wielkanoc")) return "jajko";
        if (cat.equals("slub") || cat.equals("rocznice")) return "obraczki";
        if (cat.equals("urodziny") || cat.equals("sport") || cat.equals("szkola")) return "medal";
        if (cat.equals("wilk")) return "wilk";
        if (cat.equals("wedkarstwo")) return "ryba";
        if (cat.equals("myslistwo")) return "jelen";
        if (cat.equals("wilk")) return "wilk";
        if (cat.equals("konie")) return "kon";
        if (cat.equals("psy") || cat.equals("koty")) return "lapa";
        if (cat.equals("zwierzeta") || cat.equals("dzieci") || cat.equals("fotograwer")) return "ptak";
        if (cat.equals("rolnictwo")) return "traktor";
        if (cat.equals("motoryzacja")) return "auto";
        if (cat.equals("muzyka")) return "gitara";
        if (cat.equals("gaming")) return "pad";
        if (cat.equals("wedrowki") || cat.equals("gorale")) return "gory";
        if (cat.equals("przyroda") || cat.equals("ogrod")) return "drzewo";
        if (cat.equals("morze") || cat.equals("kaszuby")) return "kotwica";
        if (cat.equals("wojsko") || cat.equals("patriotyczne")) return "orzel";
        if (cat.equals("halloween")) return "dynia";
        if (cat.equals("gotyk") || cat.equals("sredniowiecze")) return "czaszka";
        if (cat.equals("fantastyka")) return "smok";
        if (cat.equals("bizuteria") || cat.equals("walentynki") || cat.equals("slub") ||
                cat.equals("rocznice") || cat.equals("dzien_matki") || cat.equals("pierwsza_komunia") ||
                cat.equals("chrzest") || cat.equals("religijne") || cat.equals("urodziny") ||
                cat.equals("dom_rodzina")) return "serce";
        if (cat.equals("kuchnia") || cat.equals("restauracja") || cat.equals("retro")) return "kubek";
        if (cat.equals("dzien_ojca")) return "serce";
        if (cat.equals("dzien_matki")) return "roza";
        return "kolo";
    }

    static String frameFor(String style) {
        if (style.equals("minimal")) return "simple";
        if (style.equals("gotyk")) return "gothic";
        if (style.equals("elegancki")) return "double";
        if (style.equals("fantasy")) return "ornate";
        return "retro";
    }

    static String fontFor(String style) {
        if (style.equals("gotyk")) return "cinzel_deco";
        if (style.equals("elegancki")) return "playfair";
        if (style.equals("fantasy")) return "pirata";
        if (style.equals("minimal")) return "oswald";
        if (style.equals("komiks")) return "barlow";
        return "anton";
    }

    // ---------------- generowanie napisów ----------------
    public static class TextPair {
        public String top = "", bottom = "";

        TextPair(String t, String b) { top = t; bottom = b; }
    }

    public static List<TextPair> texts(Spec spec, int count, int seed) {
        List<TextPair> out = new ArrayList<TextPair>();
        String top = spec.topText;
        String bottom = spec.bottomText;
        if (!top.isEmpty()) {
            out.add(new TextPair(top, bottom));
            out.add(new TextPair(top.toUpperCase(new Locale("pl", "PL")), bottom));
        }
        String[][] banks = {
                {"KRÓL ŁOWISKA", "RYBY SIĘ MNIE BOJĄ"},
                {"WILKI NIE ŚPIĄ", "WATAHA TRZYMA SIĘ RAZEM"},
                {"MÓJ DOM, MOJE KRÓLESTWO", "TU SIĘ ZACZYNA SPOKÓJ"},
                {"NAJLEPSZY TATA", "WĘDKĄ I SERCE"},
                {"MISTRZ KUCHNI", "PRZEPISY BABCI"},
                {"BIEG PO MARZENIA", "2026"},
                {"CZYTAM, WIĘC JESTEM", "ZAKŁADKA WŁASNA"},
                {"DLA MAMY", "Z MIŁOŚCIĄ"},
                {"WESOŁYCH ŚWIĄT", "SPOKOJNYCH ŚWIĄT"},
                {"HALLOWEEN", "STRACHY W NOCY"},
                {"TU MIESZKA SZCZĘŚCIE", "I DOBRY PIES"},
                {"MÓJ GARAŻ", "MOJE KRÓLESTWO"},
                {"WĘDKUJĘ, WIĘC JESTEM", "CISZA NAD WODĄ"},
                {"SMOK STRZEŻE TEGO MIEJSCA", "WEJDŹ, JEŚLI ŚMIESZ"},
                {"25 LAT RAZEM", "I WCIĄŻ ZASKOCZENI"},
                {"PRACA, PASJA, LASER", "WOJAN LASCERCRAFT"},
                {"PIERWSZA KOMUNIA", "PAMIĄTKA DNIA"},
                {"KOCHAM LAS", "I ZAPACH DREWNA"},
                {"MÓJ OGRÓD", "MOJA DUMĄ"},
                {"DOBRY PIES", "NAJLEPSZY PRZYJACIEL"}
        };
        int idx = Math.abs(seed) % banks.length;
        while (out.size() < count) {
            if (top.isEmpty() || out.size() > 1) {
                String[] p = banks[idx % banks.length];
                out.add(new TextPair(p[0], p[1]));
            }
            idx++;
        }
        return out;
    }

    public static String fixSpelling(String text) {
        if (text == null) return "";
        String t = text;
        String low = norm(t);
        // typowe błędy: "rzadzi" -> "rządzi", "zolc" itd. — znormalizowane słowa kluczowe
        String[][] fixes = {
                {"rzadzi", "rządzi"}, {"zolc", "żółć"}, {"gesla", "gęślą"}, {"jazn", "jaźń"},
                {"krul", "król"}, {"napys", "napis"}, {"piersiowka", "piersiówka"},
                {"tabliczka", "tabliczka"}, {"zuc", "żółć"}
        };
        for (int i = 0; i < fixes.length; i++) {
            int p = low.indexOf(fixes[i][0]);
            if (p < 0) continue;
            boolean isolated = (p == 0 || !Character.isLetter(low.charAt(p - 1)));
            if (!isolated) continue;
            // zachowaj wielkość liter
            String rep = fixes[i][1];
            if (p < t.length() && Character.isUpperCase(t.charAt(p))) {
                rep = rep.substring(0, 1).toUpperCase(new Locale("pl", "PL")) + rep.substring(1);
            }
            t = t.substring(0, p) + rep + t.substring(Math.min(t.length(), p + fixes[i][0].length()));
            low = norm(t);
        }
        // porządkowanie spacji: wielokrotne → jedna, bez spacji przed przecinkiem
        t = t.replaceAll("[ \\t]{2,}", " ").replaceAll(" +([,.;:])", "$1").trim();
        return t;
    }

    // ---------------- kalkulator kosztów ----------------
    public static class Cost {
        public double materialCost, engraveMinutes, cutMinutes, machineMinutes, energyCost, laborCost,
                wearCost, totalCost, price, profit, vatPrice;
        public boolean estimateOnly = true;
        public String note = "Wartości orientacyjne — czas zależy od parametrów maszyny i materiału.";
    }

    public static Cost cost(double materialCostPerM2, double areaMm2, double engraveAreaMm2,
                            double cutLengthMm, double powerW, double energyPerKwh, double hourlyRate,
                            double engraveSpeed, double cutSpeed, double setupMin, double packaging,
                            double shipping, double marginPercent, double wastePercent) {
        Cost c = new Cost();
        double areaM2 = areaMm2 / 1000000.0;
        c.materialCost = areaM2 * materialCostPerM2 * (1.0 + wastePercent / 100.0);
        c.engraveMinutes = engraveSpeed > 0.1 ? (engraveAreaMm2 / 100.0) / engraveSpeed * 60.0 / 60.0 : 0;
        // czas grawerowania: powierzchnia / (prędkość * szerokość wiązki 0,1 mm) — szacunek
        c.engraveMinutes = engraveAreaMm2 / Math.max(1.0, engraveSpeed * 0.1) / 60.0;
        c.cutMinutes = cutSpeed > 0.1 ? cutLengthMm / cutSpeed / 60.0 : 0;
        c.machineMinutes = c.engraveMinutes + c.cutMinutes + setupMin;
        c.energyCost = (powerW / 1000.0) * (c.machineMinutes / 60.0) * 0.35 * energyPerKwh;
        c.laborCost = (c.machineMinutes / 60.0) * hourlyRate;
        c.wearCost = (c.machineMinutes / 60.0) * 3.0;
        c.totalCost = c.materialCost + c.energyCost + c.laborCost + c.wearCost + packaging + shipping;
        c.price = c.totalCost * (1.0 + marginPercent / 100.0);
        c.profit = c.price - c.totalCost;
        c.vatPrice = c.price * 1.23;
        return c;
    }

    // ---------------- walidacja ----------------
    public static class Report {
        public List<String> errors = new ArrayList<String>();
        public List<String> warnings = new ArrayList<String>();
        public List<String> infos = new ArrayList<String>();

        public String toText(Design d) {
            StringBuilder sb = new StringBuilder();
            sb.append("RAPORT WALIDACJI PROJEKTU (WOJAN LaserCraft AI Studio — Android)\n");
            sb.append("projekt: ").append(d.name).append("\n");
            sb.append("produkt: ").append(d.product.name).append(" ")
                    .append(fmt(d.product.w)).append(" x ").append(fmt(d.product.h)).append(" mm\n");
            sb.append("materiał: ").append(d.product.material)
                    .append(", kerf: ").append(fmt(d.machine.kerf)).append(" mm\n");
            sb.append("wynik: ").append(errors.isEmpty() ? "OK" : "BŁĘDY — popraw przed eksportem").append("\n");
            sb.append("błędy: ").append(errors.size()).append(", ostrzeżenia: ").append(warnings.size())
                    .append(", informacje: ").append(infos.size()).append("\n\n");
            for (int i = 0; i < errors.size(); i++) sb.append("[BŁĄD ] ").append(errors.get(i)).append("\n");
            for (int i = 0; i < warnings.size(); i++) sb.append("[UWAGA] ").append(warnings.get(i)).append("\n");
            for (int i = 0; i < infos.size(); i++) sb.append("[INFO ] ").append(infos.get(i)).append("\n");
            return sb.toString();
        }
    }

    public static String fmt(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    // Walidacja geometrii: używa ścieżek obiektów (Path) — realne pomiary, nie heurystyka.
    public static Report validate(Design d, PathProvider provider) {
        Report r = new Report();
        MaterialDef mat = material(d.product.material);
        if (!mat.safe) r.errors.add("MATERIAŁ: " + mat.warning);
        double margin = Math.max(2.0, d.product.engraveMargin);
        RectF engrave = new RectF((float) margin, (float) margin,
                (float) (d.product.w - margin), (float) (d.product.h - margin));
        int cutOuter = 0, cutInner = 0;
        for (int i = 0; i < d.objects.size(); i++) {
            Obj o = d.objects.get(i);
            if (!o.visible) continue;
            if (L_CUT_OUTER.equals(o.layer)) cutOuter++;
            if (L_CUT_INNER.equals(o.layer)) cutInner++;
            if (L_ENGRAVE_RASTER.equals(o.layer)) {
                if (!o.d.isEmpty()) {
                    RectF rr = new RectF((float) o.x, (float) o.y,
                            (float) (o.x + o.imgW), (float) (o.y + o.imgH));
                    if (!engrave.contains(rr))
                        r.warnings.add("OBRAZ_POZA: obraz „" + o.name + "” wykracza poza obszar grawerowania.");
                }
                continue;
            }
            if ("qr".equals(o.type)) {
                int modules = Qr.modulesFor(o.qrData, 'M');
                if (modules > 0) {
                    double mmPerModule = o.qrSize / (modules + 8.0);
                    if (mmPerModule < 0.4)
                        r.warnings.add("QR_MODUL: moduł QR " + fmt(mmPerModule) + " mm — może być nieczytelny.");
                    String chk = Qr.selfCheckText(o.qrData, 'M');
                    if (chk != null && chk.startsWith("QR: błąd"))
                        r.errors.add("QR: " + chk);
                }
                continue;
            }
            Path p = provider.pathFor(o);
            if (p == null || p.isEmpty()) {
                r.warnings.add("PUSTY_OBIEKT: „" + o.name + "” nie ma geometrii.");
                continue;
            }
            RectF b = new RectF();
            p.computeBounds(b, true);
            if (!isCut(o.layer)) {
                if (b.left < -0.01 || b.top < -0.01 || b.right > d.product.w + 0.01 || b.bottom > d.product.h + 0.01)
                    r.errors.add("POZA_PRODUKTEM: „" + o.name + "” wykracza poza obrys produktu.");
                else if (!engrave.contains(b))
                    r.warnings.add("MARGINES: „" + o.name + "” wchodzi w margines bezpieczeństwa.");
                if (o.strokeW > 0 && o.strokeW < 0.2)
                    r.warnings.add("CIENKA_LINIA: „" + o.name + "” ma linię " + fmt(o.strokeW) + " mm.");
            }
        }
        if (!d.product.requiresCut && (cutOuter > 0 || cutInner > 0))
            r.errors.add("CIECIE_NIEOCZEKANE: produkt jest gotowym przedmiotem, a projekt zawiera warstwy cięcia.");
        if (d.product.requiresCut && cutOuter == 0)
            r.errors.add("BRAK_KONTURU: brak zewnętrznego konturu cięcia.");
        if (d.product.curved)
            r.infos.add("Produkt zakrzywiony — wymagany uchwyt rotacyjny lub rozwinięcie powierzchni.");
        if (matIdWarn(d.product.material) != null) r.infos.add(matIdWarn(d.product.material));
        r.infos.add("Kompensacja kerf: " + fmt(d.machine.kerf) + " mm (kontur powiększony o "
                + fmt(d.machine.kerf / 2.0) + " mm na stronę).");
        r.infos.add("Obiekty: " + d.objects.size() + ", warstwy: " + LAYERS.length);
        return r;
    }

    static String matIdWarn(String id) {
        if ("stal".equals(id)) return "Stal wymaga lasera fiber lub pasty do znakowania.";
        if ("ceramika".equals(id)) return "Ceramika: grawer na szkliwie wymaga pasty lub CO₂.";
        return null;
    }
}
