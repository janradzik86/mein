package test;

import android.content.Context;
import pl.wojan.lasercraft.Core;
import pl.wojan.lasercraft.Draw;
import pl.wojan.lasercraft.Gen;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/** Eksport przykładowych projektów silnika Android do SVG — do wizualnej kontroli. */
public final class ExportSvg {
    static String findText(Core.Design d, String name) {
        for (int i = 0; i < d.objects.size(); i++) {
            Core.Obj o = d.objects.get(i);
            if (name.equals(o.name) || (name.equals("Napis górny") && o.name.startsWith("Napis g")))
                return o.text;
        }
        return "";
    }

    static String findEmblem(Core.Design d) {
        for (int i = 0; i < d.objects.size(); i++) {
            Core.Obj o = d.objects.get(i);
            if (o.name != null && o.name.startsWith("Motyw: ")) return o.name.substring(7);
        }
        return "-";
    }

    public static void main(String[] args) throws Exception {
        String dir = args.length > 0 ? args[0] : "tests/out/android";
        new File(dir).mkdirs();
        Context ctx = new Context();
        String[] prompts = {
                "Tabliczka dla wędkarza, szczupak, napis KRÓL ŁOWISKA, 200x150 mm, retro",
                "Piersiówka dla taty, motyw jelenia, napis NAJLEPSZY TATA, tylko grawer",
                "Ozdoba świąteczna, choinka, napis WESOŁYCH ŚWIĄT, 80x90 mm",
                "Pisanka wielkanocna, zając, napis WESOŁEGO ALLELUJA, 200x150 mm",
                "Tabliczka ślubna, obrączki, napis ANNA I PIOTR 12.06.2027, 200x150 mm",
                "Medal biegowy, napis BIEG PO MARZENIA, 60x60 mm",
                "Podkładka pod kubek z wilkiem, 100x100 mm, rycina",
                "Zakładka do książki, napis CZYTAM WIĘC JESTEM, 50x150 mm"
        };
        for (int i = 0; i < prompts.length; i++) {
            Core.Design d = Gen.fromPrompt(ctx, prompts[i], 0);
            String svg = Draw.toSvg(ctx, d, false);
            File f = new File(dir, String.format("a%02d.svg", i + 1));
            Writer w = new OutputStreamWriter(new FileOutputStream(f), "UTF-8");
            w.write(svg);
            w.close();
            Core.Report r = Core.validate(d, new Draw.Provider(ctx));
            System.out.println("   napis górny: '" + (d.objects.size() > 0 ? findText(d, "Napis górny") : "") +
                    "'  dolny: '" + findText(d, "Napis dolny") + "'  motyw: " + findEmblem(d));
            System.out.println(f.getPath() + "  produkt=" + d.product.name
                    + " " + Core.fmt(d.product.w) + "x" + Core.fmt(d.product.h)
                    + "  obiektów=" + d.objects.size()
                    + "  błędy=" + r.errors.size() + " ostrz=" + r.warnings.size()
                    + "  napis='" + d.name + "'");
        }
    }
}
