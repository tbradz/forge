"""Delve's foil sleeves: card backs with a painted holographic sheen, pixel art like the rest of Delve.

Forge sleeves are 360x500; these are painted at 72x100 and scaled 5x. Delve adds them to Forge's
sleeve table at startup, so they show as card backs in duels, pack openings and the Outfitter.

Run from the repo root:  python delve-tools/make_sleeves.py [--preview DIR]
"""
import math
import os
import random
import sys
from PIL import Image, ImageDraw

OUT = "forge-gui/res/adventure/common/ui/delve/sleeves"
W, H, SCALE = 72, 100, 5


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def hue(h):
    """A bright rainbow colour for h in 0..1."""
    h = (h % 1) * 6
    i, f = int(h), h - int(h)
    c = [(255, int(255 * f), 0), (int(255 * (1 - f)), 255, 0), (0, 255, int(255 * f)),
         (0, int(255 * (1 - f)), 255), (int(255 * f), 0, 255), (255, 0, int(255 * (1 - f)))][i % 6]
    return c


def base(c1, c2):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=5, fill=c1)
    px = img.load()
    for y in range(H):
        for x in range(W):
            if px[x, y][3]:
                px[x, y] = lerp(c1, c2, y / (H - 1)) + (255,)
    return img


def sheen(img, strength, rainbow=True, angle=0.55, bands=3.0, seed=1):
    """Diagonal foil bands: rainbow (holo) or white (metallic), strongest along a soft diagonal."""
    px = img.load()
    r = random.Random(seed)
    for y in range(H):
        for x in range(W):
            p = px[x, y]
            if not p[3]:
                continue
            t = (x * angle + y) / (W * angle + H)
            band = 0.5 + 0.5 * math.sin(t * math.pi * 2 * bands)
            glint = max(0.0, 1 - abs(t - 0.45) * 3.2)  # the bright streak across the middle
            k = strength * (0.35 * band + 0.65 * glint)
            if r.random() < 0.03:
                k = min(1, k + 0.35)  # foil sparkle
            tint = hue(t * 1.6 + 0.1) if rainbow else (255, 255, 255)
            c = lerp(p[:3], tint, min(1, k))
            c = tuple(min(255, v) for v in c)
            px[x, y] = c + (255,)


def border(img, c, inner=None):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, W - 1, H - 1), radius=5, outline=c, width=2)
    if inner:
        d.rounded_rectangle((4, 4, W - 5, H - 5), radius=3, outline=inner)


def emblem_star(d, cx, cy, r1, r2, c):
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rr = r1 if i % 2 == 0 else r2
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=c)


def holo_prism():
    img = base((30, 26, 50), (14, 12, 26))
    sheen(img, 0.75, True, seed=2)
    d = ImageDraw.Draw(img)
    d.polygon([(36, 30), (52, 50), (36, 70), (20, 50)], outline=(255, 255, 255))
    d.polygon([(36, 36), (47, 50), (36, 64), (25, 50)], fill=(20, 18, 34))
    border(img, (220, 220, 240), (120, 120, 160))
    return img


def gold_leaf():
    img = base((120, 84, 24), (70, 44, 10))
    sheen(img, 0.6, False, seed=3)
    d = ImageDraw.Draw(img)
    for cx, cy in ((12, 12), (W - 13, 12), (12, H - 13), (W - 13, H - 13)):  # filigree corners
        d.arc((cx - 7, cy - 7, cx + 7, cy + 7), 0, 360, fill=(255, 230, 150))
        d.point((cx, cy), fill=(255, 245, 200))
    emblem_star(d, 36, 50, 16, 7, (255, 226, 130))
    emblem_star(d, 36, 50, 9, 4, (150, 100, 30))
    border(img, (255, 220, 120), (170, 120, 40))
    return img


def silver_etched():
    img = base((150, 156, 170), (90, 96, 112))
    sheen(img, 0.55, False, angle=0.9, bands=4, seed=4)
    d = ImageDraw.Draw(img)
    for i in range(6, W - 6, 6):  # etched lines
        d.line((i, 8, i - 4, H - 8), fill=(120, 126, 140))
    d.ellipse((22, 36, 50, 64), fill=(70, 76, 92), outline=(230, 234, 245))
    d.line((36, 40, 36, 60), fill=(230, 234, 245), width=2)
    d.line((26, 50, 46, 50), fill=(230, 234, 245), width=2)
    border(img, (235, 238, 248), (120, 126, 142))
    return img


def mana_swirl():
    img = base((24, 22, 30), (10, 10, 14))
    px = img.load()
    cols = [(250, 240, 200), (80, 140, 230), (110, 70, 130), (220, 70, 50), (70, 170, 90)]  # W U B R G
    for y in range(H):
        for x in range(W):
            if not px[x, y][3]:
                continue
            a = math.atan2(y - H / 2, x - W / 2)
            dist = math.hypot(x - W / 2, (y - H / 2) * 0.72)
            seg = ((a + math.pi) / (2 * math.pi) * 5 + dist / 18) % 5
            c = cols[int(seg)]
            k = 0.55 * max(0.0, 1 - dist / 46)
            px[x, y] = lerp(px[x, y][:3], c, k) + (255,)
    sheen(img, 0.35, True, seed=5)
    d = ImageDraw.Draw(img)
    d.ellipse((30, 44, 42, 56), fill=(240, 236, 220))
    border(img, (200, 190, 160), (90, 86, 80))
    return img


def starfoil():
    img = base((18, 22, 58), (8, 10, 30))
    r = random.Random(6)
    d = ImageDraw.Draw(img)
    for _ in range(90):
        x, y = r.randrange(4, W - 4), r.randrange(4, H - 4)
        d.point((x, y), fill=hue(r.random()))
    for _ in range(7):
        x, y = r.randrange(10, W - 10), r.randrange(10, H - 10)
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            d.point((x + dx, y + dy), fill=(255, 255, 255) if (dx, dy) == (0, 0) else (200, 210, 255))
    sheen(img, 0.4, True, angle=0.4, seed=7)
    sky = img.getpixel((50, 47))[:3]  # cut the crescent with the sky's own colour, not a flat dark disc
    d = ImageDraw.Draw(img)
    d.ellipse((24, 38, 48, 62), fill=(236, 232, 210))
    d.ellipse((30, 35, 54, 59), fill=sky)
    border(img, (190, 200, 255), (70, 80, 150))
    return img


def dragonscale():
    img = base((40, 70, 50), (16, 34, 24))
    d = ImageDraw.Draw(img)
    for row, y in enumerate(range(2, H, 6)):  # overlapping scales
        for x in range(-6 + (row % 2) * 4, W + 6, 8):
            d.arc((x - 5, y - 5, x + 5, y + 5), 200, 340, fill=(90, 140, 100))
    sheen(img, 0.55, True, angle=0.7, bands=2.5, seed=8)
    d = ImageDraw.Draw(img)
    d.polygon([(36, 32), (44, 50), (36, 68), (28, 50)], fill=(230, 200, 90))
    d.line((36, 36, 36, 64), fill=(120, 80, 20))
    border(img, (220, 200, 120), (60, 100, 70))
    return img


SLEEVES = {
    "holo_prism": holo_prism, "gold_leaf": gold_leaf, "silver_etched": silver_etched,
    "mana_swirl": mana_swirl, "starfoil": starfoil, "dragonscale": dragonscale,
}


def main():
    out = OUT
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
    os.makedirs(out, exist_ok=True)
    for name, fn in SLEEVES.items():
        fn().resize((W * SCALE, H * SCALE), Image.NEAREST).save(os.path.join(out, f"{name}.png"))
        print(name)


if __name__ == "__main__":
    main()
