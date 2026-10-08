"""KADR share card — letterpress cutting-room lockup, 2400×1260 (2× 1200×630)."""
import math

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 2400, 1260
INK = (18, 17, 14)
INK_WARM = (36, 32, 26)
PAPER = (244, 239, 230)
GOLD = (240, 193, 75)
STAMP = (196, 69, 45)
HAIR = (40, 37, 32)

serif = ImageFont.truetype(
    "/usr/share/fonts/truetype/liberation/LiberationSerif-Bold.ttf", 430
)
sans = ImageFont.truetype(
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf", 72
)

img = Image.new("RGB", (W, H), INK)
rad = Image.new("L", (W, H), 0)
rd = ImageDraw.Draw(rad)
rd.ellipse((W * 0.08, H * -0.25, W * 0.92, H * 1.2), fill=255)
rad = rad.filter(ImageFilter.GaussianBlur(110))
wash = Image.new("RGB", (W, H), INK_WARM)
img = Image.composite(wash, img, rad)

draw = ImageDraw.Draw(img)

for x in range(140, W, 140):
    if 640 < x < 1760:
        continue
    draw.line([(x, 0), (x, H)], fill=HAIR, width=2)
for y in range(140, H, 140):
    if 240 < y < 1020:
        continue
    draw.line([(0, y), (W, y)], fill=HAIR, width=2)

noise = Image.effect_noise((W, H), 16).convert("L")
grain = Image.merge("RGB", (noise, noise, noise))
img = Image.blend(img, grain, 0.04)
draw = ImageDraw.Draw(img)


def crop_mark(x, y, dx, dy, arm=100, stroke=8):
    draw.line([(x, y), (x + dx * arm, y)], fill=PAPER, width=stroke)
    draw.line([(x, y), (x, y + dy * arm)], fill=PAPER, width=stroke)


inset = 108
crop_mark(inset, inset, 1, 1)
crop_mark(W - inset, inset, -1, 1)
crop_mark(inset, H - inset, 1, -1)
crop_mark(W - inset, H - inset, -1, -1)

word = "KADR"
tracking = 36
widths = [serif.getlength(ch) for ch in word]
total = sum(widths) + tracking * (len(word) - 1)
# Cap height from the font, so the registration target sits on the letter body.
ascent = serif.getmetrics()[0]
cap = int(ascent * 0.72)
baseline = 690
top = baseline - cap
cx = W // 2
cy = top + cap // 2

# Registration target, drawn before type so the letters knock it out.
draw.ellipse((cx - 188, cy - 188, cx + 188, cy + 188), outline=GOLD, width=7)
draw.ellipse((cx - 34, cy - 34, cx + 34, cy + 34), outline=GOLD, width=6)
draw.line([(cx - 280, cy), (cx + 280, cy)], fill=GOLD, width=6)
draw.line([(cx, cy - 280), (cx, cy + 280)], fill=GOLD, width=6)

x = (W - total) / 2
for ch, wch in zip(word, widths):
    draw.text((x, baseline), ch, font=serif, fill=PAPER, anchor="ls")
    x += wch + tracking

rule_w = int(total * 0.36)
rule_y = baseline + 52
draw.line([(cx - rule_w, rule_y), (cx + rule_w, rule_y)], fill=GOLD, width=5)

tag = "generator memów"
tag_track = 12
tag_widths = [sans.getlength(ch) for ch in tag]
tag_total = sum(tag_widths) + tag_track * (len(tag) - 1)
tx = (W - tag_total) / 2
tag_y = rule_y + 86
for ch, wch in zip(tag, tag_widths):
    draw.text((tx, tag_y), ch, font=sans, fill=PAPER, anchor="ls")
    tx += wch + tag_track

# Printer's stamp: red ring, gold center. Clear of the slug and the crop.
sx, sy = W - 250, H - 210
draw.ellipse((sx - 46, sy - 46, sx + 46, sy + 46), outline=STAMP, width=8)
draw.ellipse((sx - 14, sy - 14, sx + 14, sy + 14), fill=GOLD)

img.save("/workspace/.grok/og-raw.png", "PNG")
print("word_width", round(total), f"{total / W:.2%}")
print("tag_width", round(tag_total))
print("lockup_top", top, "tag_y", tag_y, "stamp", sx, sy)
print("margins L/R", round((W - total) / 2), "bottom", H - tag_y)
