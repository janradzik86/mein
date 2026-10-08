"""KADR 50:11 X feed card. Photographic plate + exact lockup, staged under .grok."""
from PIL import Image, ImageDraw, ImageFilter, ImageFont

S = 2
W, H = 1200 * S, 264 * S
BG = (18, 17, 14)  # #12110e
WARM = (42, 38, 31)
INK = (244, 239, 230)  # #f4efe6
GOLD = (240, 193, 75)  # #f0c14b
RED = (196, 69, 45)  # #c4452d
STROKE = (20, 18, 14)

NARROW_BOLD = "/usr/share/fonts/truetype/liberation/LiberationSansNarrow-Bold.ttf"
NARROW = "/usr/share/fonts/truetype/liberation/LiberationSansNarrow-Regular.ttf"

title_font = ImageFont.truetype(NARROW_BOLD, 86 * S)
tag_font = ImageFont.truetype(NARROW, 18 * S)
cap_font = ImageFont.truetype(NARROW_BOLD, 34 * S)


def tracked(draw, xy, text, font, fill, tracking, anchor_y="top", stroke_width=0, stroke_fill=None):
    x, y = xy
    widths = [font.getlength(ch) for ch in text]
    total = sum(widths) + tracking * (len(text) - 1)
    ascent, descent = font.getmetrics()
    if anchor_y == "top":
        baseline = y + ascent
    else:
        baseline = y
    cursor = x
    for ch, wch in zip(text, widths):
        draw.text(
            (cursor, baseline),
            ch,
            font=font,
            fill=fill,
            anchor="ls",
            stroke_width=stroke_width,
            stroke_fill=stroke_fill,
        )
        cursor += wch + tracking
    bottom = baseline + descent
    return (x, y, x + total, bottom, total)


def main():
    base = Image.new("RGB", (W, H), BG)

    # Photo plate: wide slice through the cat's face, plants falling to the right.
    src = Image.open("/workspace/public/mem-templates/kot.jpg").convert("RGB")
    sw, sh = src.size
    pw, ph = 612 * S, 214 * S
    crop_h = int(round(sw * ph / pw))
    crop_y = max(0, int(sh * 0.16))
    plate = src.crop((0, crop_y, sw, min(sh, crop_y + crop_h))).resize((pw, ph), Image.Resampling.LANCZOS)

    px, py = 548 * S, 22 * S

    glow = Image.new("L", (W, H), 0)
    gd = ImageDraw.Draw(glow)
    gd.ellipse((px - 80 * S, py - 70 * S, px + pw + 40 * S, py + ph + 90 * S), fill=210)
    glow = glow.filter(ImageFilter.GaussianBlur(36 * S))
    warm = Image.new("RGB", (W, H), WARM)
    base = Image.composite(warm, base, glow)

    # Soft ground shadow, not a UI card.
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    sd.rectangle((px + 8 * S, py + 10 * S, px + pw + 8 * S, py + ph + 12 * S), fill=(0, 0, 0, 150))
    shadow = shadow.filter(ImageFilter.GaussianBlur(10 * S))
    base_rgba = base.convert("RGBA")
    base_rgba.alpha_composite(shadow)
    base_rgba.paste(plate, (px, py))

    # Viewfinder: corner brackets, ring, cross, warm-red pip.
    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    arm = 26 * S
    stroke = 3 * S
    gold_a = GOLD + (230,)
    inset = 8 * S
    x0, y0, x1, y1 = px + inset, py + inset, px + pw - inset, py + ph - inset
    for cx, cy, dx, dy in (
        (x0, y0, 1, 1),
        (x1, y0, -1, 1),
        (x0, y1, 1, -1),
        (x1, y1, -1, -1),
    ):
        od.line([(cx, cy), (cx + dx * arm, cy)], fill=gold_a, width=stroke)
        od.line([(cx, cy), (cx, cy + dy * arm)], fill=gold_a, width=stroke)
    cx = (x0 + x1) // 2
    cy = (y0 + y1) // 2
    r = 34 * S
    od.ellipse((cx - r, cy - r, cx + r, cy + r), outline=GOLD + (170,), width=2 * S)
    od.line([(cx - r - 18 * S, cy), (cx + r + 18 * S, cy)], fill=GOLD + (150,), width=2 * S)
    od.line([(cx, cy - r - 14 * S), (cx, cy + r + 14 * S)], fill=GOLD + (150,), width=2 * S)
    pip = 4 * S
    od.ellipse((cx - pip, cy - pip, cx + pip, cy + pip), fill=RED + (235,))
    base_rgba.alpha_composite(overlay)

    draw = ImageDraw.Draw(base_rgba)
    caption = "TYLKO CHWILĘ"
    cap_size = 34 * S
    cap_font_fit = ImageFont.truetype(NARROW_BOLD, cap_size)
    cap_w = cap_font_fit.getlength(caption)
    while cap_w > pw - 48 * S and cap_size > 18 * S:
        cap_size -= 2 * S
        cap_font_fit = ImageFont.truetype(NARROW_BOLD, cap_size)
        cap_w = cap_font_fit.getlength(caption)
    cap_x = px + pw / 2
    cap_y = py + 16 * S
    draw.text(
        (cap_x, cap_y),
        caption,
        font=cap_font_fit,
        fill=(255, 255, 255, 255),
        anchor="mt",
        stroke_width=max(2, 4 * S // 2),
        stroke_fill=STROKE + (255,),
    )

    # Lockup: left half, raised above the midline, clear of the bottom fifth.
    title = "KADR"
    tag = "GENERATOR MEMÓW"
    tx, ty = 64 * S, 34 * S
    title_box = tracked(draw, (tx, ty), title, title_font, GOLD + (255,), tracking=10 * S)
    tag_y = title_box[3] + 10 * S
    tag_box = tracked(draw, (tx, tag_y), tag, tag_font, INK + (255,), tracking=3 * S)
    rule_y = tag_box[3] + 8 * S
    rule_w = int(title_box[4] * 0.42)
    draw.line([(tx, rule_y), (tx + rule_w, rule_y)], fill=GOLD + (255,), width=2 * S)

    # Film grain over the studio, not over the type (type is redrawn crisp after).
    rgb = base_rgba.convert("RGB")
    noise = Image.effect_noise((W, H), 18).convert("L")
    grain = Image.merge("RGB", (noise, noise, noise))
    rgb = Image.blend(rgb, grain, 0.045)
    # Re-stamp the lockup so grain does not chew the glyphs.
    draw = ImageDraw.Draw(rgb)
    tracked(draw, (tx, ty), title, title_font, GOLD, tracking=10 * S)
    tracked(draw, (tx, tag_y), tag, tag_font, INK, tracking=3 * S)
    draw.line([(tx, rule_y), (tx + rule_w, rule_y)], fill=GOLD, width=2 * S)

    out = "/workspace/.grok/x-banner-raw.png"
    rgb.save(out, "PNG")

    # Safe-zone report in final 1200×264 pixels.
    def box1(b):
        return tuple(round(v / S, 1) for v in b[:4])

    title_1 = box1(title_box)
    tag_1 = box1(tag_box)
    print("title_box", title_1)
    print("tag_box", tag_1)
    print("photo", px // S, py // S, (px + pw) // S, (py + ph) // S)
    print("rule_y", round(rule_y / S, 1))
    for name, b in (("title", title_1), ("tag", tag_1)):
        x0b, y0b, x1b, y1b = b
        if x1b >= 600:
            raise SystemExit(f"{name} enters the right half")
        if y1b >= 264 * 0.8:
            raise SystemExit(f"{name} enters the bottom fifth")
        if x0b < 28 or y0b < 16:
            raise SystemExit(f"{name} hugs an edge")
    print("safe-zone ok")


if __name__ == "__main__":
    main()
