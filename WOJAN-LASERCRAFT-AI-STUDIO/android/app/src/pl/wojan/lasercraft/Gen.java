package pl.wojan.lasercraft;

import android.content.Context;
import android.graphics.Path;
import android.graphics.RectF;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Generator projektów (silnik proceduralny, w pełni lokalny):
 * specyfikacja z języka naturalnego → obiekty wektorowe z warstwami produkcyjnymi.
 * Ten sam algorytm co wersja Windows (Core.analyze + generator kształtów).
 */
public final class Gen {

    public static final String[] STYLES = {"rycina", "retro", "gotyk", "elegancki", "minimal",
            "komiks", "fantasy", "slowianskie", "nowoczesny"};
    public static final String[] STYLE_LABELS = {"Rycina", "Retro", "Gotyk", "Elegancki", "Minimal",
            "Komiks", "Fantasy", "Słowiański", "Nowoczesny"};
    public static final String[] DETAILS = {"niski", "sredni", "wysoki"};
    public static final String[] DETAIL_LABELS = {"Niski (szybki)", "Średni", "Wysoki (bogaty)"};
    public static final String[] ENGTYPES = {"grawer liniowy", "grawer wypełnienia",
            "grawer wypełnienia + linia", "fotograwer rastrowy"};

    static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
    }

    public static Core.Design blank(String prompt) {
        Core.Design d = new Core.Design();
        d.prompt = prompt == null ? "" : prompt;
        d.created = now();
        d.modified = now();
        d.name = "Projekt " + new SimpleDateFormat("dd.MM HH:mm", Locale.US).format(new Date());
        return d;
    }

    /** Buduje kompletny projekt z promptu (deterministycznie wg wariantu). */
    public static Core.Design fromPrompt(Context ctx, String prompt, int variant) {
        Core.Spec spec = Core.analyze(prompt);
        return fromSpec(ctx, spec, variant);
    }

    public static Core.Design fromSpec(Context ctx, Core.Spec spec, int variant) {
        Core.Design d = blank(spec.raw);
        applySpec(ctx, d, spec, variant);
        return d;
    }

    public static void applySpec(Context ctx, Core.Design d, Core.Spec spec, int variant) {
        Core.ProductDef pd = Core.product(spec.productId);
        Core.MaterialDef md = Core.material(spec.material);
        Core.MachineDef mdef = Core.machine(spec.machineId);
        d.category = spec.category;
        d.style = spec.style;
        d.detail = spec.detail;
        d.engraveType = spec.engravetype;
        d.prompt = spec.raw;
        d.product.id = pd.id;
        d.product.name = pd.name;
        d.product.shape = spec.shape != null && !spec.shape.isEmpty() ? spec.shape : pd.shape;
        d.product.material = md.id;
        d.product.w = spec.width > 5 ? spec.width : pd.w;
        d.product.h = spec.height > 5 ? spec.height : pd.h;
        d.product.cornerR = pd.cornerR;
        d.product.engraveMargin = pd.margin;
        d.product.requiresCut = spec.requiresCut;
        d.product.curved = pd.curved;
        d.machine.id = mdef.id;
        d.machine.name = mdef.name;
        d.machine.controller = mdef.controller;
        d.machine.kerf = mdef.kerf;
        d.machine.lpi = mdef.lpi;
        d.objects.clear();
        d.nextId = 1;

        double W = d.product.w, H = d.product.h;
        double margin = Math.max(2.0, d.product.engraveMargin);
        // zawieszka/ozdoba ma otwór na sznurek — strefa grawerowania zaczyna się pod nim
        double marginTop = margin;
        if ("tag".equals(d.product.shape)) {
            double holeR = Math.max(1.8, Math.min(4.5, H * 0.05));
            marginTop = Math.max(margin, 2 * (holeR + 3.0) + 1.5);
        }
        // produkt okrągły/owalny: prostokąt wpisany w koło (inaczej treść wyjdzie za obrys)
        boolean round = "circle".equals(d.product.shape) || "oval".equals(d.product.shape);
        double insetX = margin, insetTop = marginTop, insetBottom = margin;
        if (round) {
            double r = Math.min(W, H) / 2.0;
            double in = r * (1.0 - Math.sqrt(0.5)) + margin * 0.5;   // 0.293·r
            insetX = Math.max(margin + in * 0.55, margin);
            insetTop = Math.max(marginTop + in * 0.35, marginTop);
            insetBottom = Math.max(margin + in * 0.35, margin);
        }

        // 1. kontur cięcia (z kompensacją kerf, jeżeli produkt jest wycinany)
        if (d.product.requiresCut) {
            double k = d.machine.kerf;
            Path cut = Draw.productShape(d.product.shape, W + k, H + k, d.product.cornerR + k / 2.0);
            Core.Obj o = new Core.Obj();
            o.id = d.newId("cut");
            o.type = "path";
            o.layer = Core.L_CUT_OUTER;
            o.role = "line";
            o.name = "Obrys produktu (kompensacja kerf " + Core.fmt(k) + " mm)";
            o.d = Draw.pathToSvgD(cut, 0.05f);
            o.x = -k / 2.0;
            o.y = -k / 2.0;
            o.strokeW = 0.15;
            d.objects.add(o);
            // otwór na zawieszkę / sznurek
            // otwór na sznurek/kółko — zawsze osobna warstwa CUT_INNER
            if ("tag".equals(d.product.shape) || "brelok".equals(pd.id) || "medal".equals(pd.id)
                    || "magnes".equals(pd.id) || "niesmiertelnik".equals(pd.id)) {
                double r = "tag".equals(d.product.shape)
                        ? Math.max(1.8, Math.min(4.5, H * 0.05))
                        : Math.max(1.6, Math.min(3.0, Math.min(W, H) * 0.06));
                Path hole = new Path();
                hole.addCircle(0f, 0f, (float) (r + k / 2.0), Path.Direction.CW);
                Core.Obj h = new Core.Obj();
                h.id = d.newId("hole");
                h.type = "path";
                h.layer = Core.L_CUT_INNER;
                h.role = "line";
                h.name = "Otwór na zawieszkę";
                h.d = Draw.pathToSvgD(hole, 0.05f);
                h.x = W / 2.0;
                h.y = "tag".equals(d.product.shape) ? r + 3.0 : margin + r + 2.0;
                h.strokeW = 0.15;
                d.objects.add(h);
            }
        }

        boolean ornate = !"minimal".equals(spec.style) && !"niski".equals(spec.detail);
        boolean minimal = "minimal".equals(spec.style) || "niski".equals(spec.detail);

        // 2. ramka grawerowana
        if (ornate) {
            String fstyle = spec.frame;
            if (fstyle == null || fstyle.isEmpty()) {
                if ("retro".equals(spec.style) || "slowianskie".equals(spec.style)) fstyle = "retro";
                else if ("gotyk".equals(spec.style)) fstyle = "gothic";
                else if ("elegancki".equals(spec.style)) fstyle = "double";
                else fstyle = "simple";
            }
            RectF area = new RectF((float) insetX, (float) insetTop,
                    (float) (W - insetX), (float) (H - insetBottom));
            Path frame;
            if (round) {
                // ramka okrągła dopasowana do kształtu produktu
                float rr = (float) (Math.min(W, H) / 2.0 - margin * 0.6);
                frame = new Path();
                frame.addCircle((float) (W / 2.0), (float) (H / 2.0), rr, Path.Direction.CW);
                frame.addCircle((float) (W / 2.0), (float) (H / 2.0), rr - Math.max(1.2f, rr * 0.05f),
                        Path.Direction.CCW);
            } else {
                frame = Draw.frame(fstyle, area, 0.4f);
            }
            if (!frame.isEmpty()) {
                Core.Obj o = new Core.Obj();
                o.id = d.newId("frame");
                o.type = "path";
                o.layer = Core.L_ENGRAVE_VECTOR;
                o.role = "line";
                o.name = "Ramka (" + fstyle + ")";
                o.d = Draw.pathToSvgD(frame, 0.08f);
                o.strokeW = 0.35;
                d.objects.add(o);
            }
        }

        // 3. układ stref: napis górny, motyw, napis dolny (bez nachodzenia)
        String emblem = emblemFor(spec, variant);
        double innerW = W - 2 * insetX - 4;
        boolean hasTop = spec.topText != null && !spec.topText.isEmpty();
        boolean hasBottom = spec.bottomText != null && !spec.bottomText.isEmpty();
        double topZone = hasTop ? Math.max(6.0, Math.min(H * 0.20, 24.0)) : 0;
        double bottomZone = hasBottom ? Math.max(5.0, Math.min(H * 0.15, 18.0)) : 0;
        double embTop = insetTop + topZone + (hasTop ? 2 : 0);
        double embBottom = H - insetBottom - bottomZone - (hasBottom ? 2 : 0);
        double embW = Math.min(innerW * 1.0, Math.min(W * 0.80, (embBottom - embTop) * 1.6));
        double embH = Math.max(0, embBottom - embTop);
        if (embW > 6 && embH > 6) {
            RectF ea = new RectF(0, 0, (float) embW, (float) embH);
            Path em = Draw.emblem(emblem, ea);
            if (!em.isEmpty()) {
                Core.Obj o = new Core.Obj();
                o.id = d.newId("emblem");
                o.type = "path";
                o.layer = Core.L_ENGRAVE_VECTOR;
                o.role = "line";
                o.name = "Motyw: " + emblem;
                o.d = Draw.pathToSvgD(em, 0.10f);
                o.x = (W - embW) / 2.0;
                o.y = embTop;
                o.strokeW = 0.3;
                d.objects.add(o);
            }
        }

        // 4. napisy
        String font = fontFor(spec, variant);
        String top = spec.topText == null || spec.topText.isEmpty() ? null : spec.topText;
        String bottom = spec.bottomText == null || spec.bottomText.isEmpty() ? null : spec.bottomText;
        List<Core.TextPair> pairs = Core.texts(spec, 1, variant);
        if (top == null && !pairs.isEmpty()) top = pairs.get(0).top;
        if (bottom == null && !pairs.isEmpty()) bottom = pairs.get(0).bottom;
        boolean filled = spec.engravetype != null && spec.engravetype.contains("wypełn");

        boolean cornerDecor = "boze_narodzenie".equals(spec.category) || "halloween".equals(spec.category);
        if (top != null && !top.isEmpty()) {
            double headW = innerW * (cornerDecor ? 0.72 : 0.94);
            double baseSize = Math.max(5.0, Math.min(H * 0.16, 20));
            Core.Obj o = textObj(ctx, d, top, font, headW, baseSize);
            if (o.size < 2.9 && top.length() > 14) {
                // za długi napis — skracamy zamiast pisać nieczytelnie
                String shortTop = Core.shorten(top, 18);
                o = textObj(ctx, d, shortTop, font, headW, baseSize);
                o.name = "Napis górny";
            }
            o.name = "Napis górny";
            o.x = W / 2.0;
            o.y = insetTop + o.size * 0.95;
            o.layer = filled ? Core.L_ENGRAVE_FILL : Core.L_ENGRAVE_VECTOR;
            o.role = filled ? "fill" : "line";
            d.objects.add(o);
        }
        if (bottom != null && !bottom.isEmpty() && H - insetBottom - insetTop > 30) {
            double baseSize = Math.max(5.0, Math.min(H * 0.12, 15));
            Core.Obj o = textObj(ctx, d, bottom, font, innerW * 0.86, baseSize);
            if (o.size >= 2.8) {
                o.name = "Napis dolny";
                o.x = W / 2.0;
                o.y = H - insetBottom - o.size * 0.35;
                o.layer = filled ? Core.L_ENGRAVE_FILL : Core.L_ENGRAVE_VECTOR;
                o.role = filled ? "fill" : "line";
                d.objects.add(o);
            }
        }

        // 5. detal: ornamenty narożne / kropki (poziom wysoki)
        if ("wysoki".equals(spec.detail) && !minimal) {
            Core.Obj o = new Core.Obj();
            o.id = d.newId("orn");
            o.type = "path";
            o.layer = Core.L_ENGRAVE_VECTOR;
            o.role = "line";
            o.name = "Ornament liniowy";
            o.d = Draw.pathToSvgD(ornamentLine(W, H, margin, variant), 0.08f);
            o.strokeW = 0.25;
            d.objects.add(o);
        }

        // 6. QR — tylko gdy kategoria to kod QR
        if ("qr_kontakt".equals(spec.category) || Core.norm(spec.raw).contains("qr")) {
            Core.Obj o = new Core.Obj();
            o.id = d.newId("qr");
            o.type = "qr";
            o.layer = Core.L_ENGRAVE_VECTOR;
            o.role = "fill";
            o.name = "Kod QR";
            o.qrData = qrDataFrom(spec.raw);
            o.qrSize = Math.min(W, H) * 0.42;
            o.x = (W - o.qrSize) / 2.0;
            o.y = H / 2.0 - o.qrSize / 2.0 + 3;
            d.objects.add(o);
        }

        d.modified = now();
        d.pushHistory("Wygenerowano: wariant " + (variant + 1));
    }

    public static String qrDataFrom(String raw) {
        if (raw != null) {
            String[] parts = raw.split("\\s+");
            for (int i = 0; i < parts.length; i++)
                if (parts[i].startsWith("http") || parts[i].contains("www.")) return parts[i];
        }
        return "WOJAN LaserCraft AI Studio";
    }

    static Core.Obj textObj(Context ctx, Core.Design d, String txt, String font, double maxW, double baseSize) {
        Core.Obj o = new Core.Obj();
        o.type = "text";
        o.text = txt;
        o.font = font;
        o.align = "center";
        o.tracking = 0.4;
        double size = baseSize;
        float measured = Draw.measureText(ctx, txt, font, (float) size);
        double avail = maxW - Math.max(0, txt.length() - 1) * o.tracking;
        if (measured > avail && avail > 1) size = Math.max(1.6, size * avail / measured);
        o.size = size;
        o.strokeW = 0.25;
        o.id = d.newId("txt");
        return o;
    }

    static Path ornamentLine(double W, double H, double margin, int variant) {
        Path p = new Path();
        double y = H * 0.5;
        double len = Math.min(W * 0.22, 40);
        int mode = Math.abs(variant) % 3;
        if (mode == 0) {
            for (int side = -1; side <= 1; side += 2) {
                double x0 = side < 0 ? margin + 2 : W - margin - 2;
                p.moveTo((float) x0, (float) y);
                p.lineTo((float) (x0 - side * len), (float) y);
                p.addCircle((float) (x0 - side * len), (float) y, 0.9f, Path.Direction.CW);
            }
        } else if (mode == 1) {
            for (int i = 0; i < 7; i++) {
                double x = margin + 4 + i * ((W - 2 * margin - 8) / 6.0);
                p.addCircle((float) x, (float) (margin + 1.6), 0.7f, Path.Direction.CW);
                p.addCircle((float) x, (float) (H - margin - 1.6), 0.7f, Path.Direction.CW);
            }
        } else {
            double cx = W / 2, cy = H - margin - 2.2;
            p.moveTo((float) (cx - 12), (float) cy);
            p.quadTo((float) cx, (float) (cy - 3.4), (float) (cx + 12), (float) cy);
        }
        return p;
    }

    static String emblemFor(Core.Spec spec, int variant) {
        String category = spec == null ? null : spec.category;
        // motyw z analizy promptu (okazje: choinka, jajko, obrączki, medal) ma pierwszeństwo
        String em = spec == null ? null : spec.subject;
        if (em == null || em.isEmpty()) {
            List<Core.CategoryDef> cats = Core.categories();
            for (int i = 0; i < cats.size(); i++) {
                if (cats.get(i).id.equals(category)) em = cats.get(i).emblem;
            }
        }
        if (em == null || em.isEmpty()) em = "kolo";
        boolean occasion = category != null && (category.equals("boze_narodzenie") || category.equals("wielkanoc")
                || category.equals("slub") || category.equals("rocznice") || category.equals("urodziny")
                || category.equals("walentynki") || category.equals("halloween"));
        if (!occasion && Math.abs(variant) % 4 == 3) {
            String[] alt = {"wilk", "jelen", "serce", "drzewo", "gory", "kotwica", "gitara", "czaszka"};
            em = alt[Math.abs(variant + category.length()) % alt.length];
        }
        return em;
    }

    static String fontFor(Core.Spec spec, int variant) {
        if (spec.font != null && !spec.font.isEmpty() && !"anton".equals(spec.font)) return spec.font;
        if ("gotyk".equals(spec.style)) return "pirata";
        if ("elegancki".equals(spec.style)) return "cinzel";
        if ("minimal".equals(spec.style) || "nowoczesny".equals(spec.style)) return "bebas";
        if ("komiks".equals(spec.style)) return "abril";
        if ("retro".equals(spec.style)) return "playfair";
        return variant % 3 == 0 ? "anton" : (variant % 3 == 1 ? "bebas" : "oswald");
    }

    /** 24 przykładowe projekty generowane deterministycznie przez ten sam silnik. */
    public static List<Core.Design> samples(Context ctx) {
        String[][] specs = {
                {"Tabliczka dla wędkarza, szczupak, napis KRÓL ŁOWISKA, 200x150 mm, retro"},
                {"Piersiówka dla taty, motyw jelenia, napis NAJLEPSZY TATA, tylko grawer"},
                {"Brelok z imieniem WOJAN, 40x40 mm, minimal"},
                {"Zawieszka świąteczna, choinka, napis WESOŁYCH ŚWIĄT, 60x80 mm"},
                {"Medal biegowy, napis BIEG PO MARZENIA, 60x60 mm, nowoczesny"},
                {"Podkładka pod kubek z wilkiem, 100x100 mm, rycina"},
                {"Ozdoba świąteczna z dynią, HALLOWEEN, 80x90 mm"},
                {"Zakładka do książki, napis CZYTAM WIĘC JESTEM, 50x150 mm"},
                {"Magnes na lodówkę z kotem, 60x45 mm"},
                {"Deska kuchenna z napisem MISTRZ KUCHNI, 300x200 mm, tylko grawer"},
                {"Zakładka z ornamentem słowiańskim, 50x150 mm, słowiański"},
                {"Tabliczka herb Śląska, 200x150 mm, gotyk"},
                {"Dyplom rocznicowy, serce, napis 25 LAT RAZEM, 200x150 mm, elegancki"},
                {"Pamiątka komunijna z rybą, 120x90 mm, elegancki"},
                {"Zawieszka na chrzest, anioł, 60x80 mm"},
                {"Brelok z QR do wizytówki, https://wojan.pl, 50x50 mm"},
                {"Tabliczka MÓJ OGRÓD, motyw drzewa, 180x120 mm"},
                {"Medal dla myśliwego, jelenia głowa, 60x60 mm"},
                {"Podkładka z motywem górskim, GÓRY CZEKAJĄ, 100x100 mm"},
                {"Zakładka z gitarą, ROCK AND ROLL, 50x150 mm"},
                {"Magnes z kotwicą, MORZE, 60x45 mm"},
                {"Tabliczka z czterolistną koniczyną, SZCZĘŚCIE, 200x150 mm"},
                {"Zawieszka z motywem słowiańskim, 70x90 mm, wysoki detal"},
                {"Tabliczka firmowa, logo, 250x160 mm, minimalistyczny"}
        };
        List<Core.Design> out = new ArrayList<Core.Design>();
        for (int i = 0; i < specs.length; i++) {
            Core.Design d = fromPrompt(ctx, specs[i][0], i);
            d.id = String.format(Locale.US, "sample_%02d", i + 1);
            d.name = "Przykład " + (i + 1) + " — " + Core.product(d.product.id).name;
            out.add(d);
        }
        return out;
    }
}
