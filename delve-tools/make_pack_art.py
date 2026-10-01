"""Generate the booster-opening art: a foil booster pack, a burst flash, a
rare-card glow and the opening-table backdrop. Pixel art drawn small and
scaled up with nearest-neighbour. Run from the repo root:
    python3 delve-tools/make_pack_art.py
"""
import math
import random
from PIL import Image, ImageDraw, ImageFilter

OUT = "forge-gui/res/adventure/common/ui/delve"
rng = random.Random(11)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def pack():
    w, h = 48, 72
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(4, h - 4):
        for x in range(2, w - 2):
            # diagonal foil sheen over a deep violet-to-teal body
            t = y / h
            base = lerp((52, 24, 92), (18, 70, 98), t)
            sheen = max(0.0, 1 - abs(((x + y * 0.6) % 34) - 17) / 6.0)
            c = lerp(base, (214, 226, 255), sheen * 0.55)
            px[x, y] = c + (255,)
    d = ImageDraw.Draw(img)
    # crimped seals top and bottom
    for x in range(2, w - 2, 2):
        d.rectangle([x, 0, x, 5], fill=(170, 170, 186, 255))
        d.rectangle([x + 1, 1, x + 1, 5], fill=(120, 120, 140, 255))
        d.rectangle([x, h - 6, x, h - 1], fill=(170, 170, 186, 255))
        d.rectangle([x + 1, h - 6, x + 1, h - 2], fill=(120, 120, 140, 255))
    # outline
    d.rectangle([2, 4, w - 3, h - 5], outline=(14, 8, 26, 255))
    # center emblem: a five-pointed mana star in a gold ring
    cx, cy, r = w // 2, h // 2 - 2, 13
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(24, 14, 40, 255), outline=(232, 186, 74, 255))
    d.ellipse([cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1], outline=(150, 108, 34, 255))
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        rr = 9 if i % 2 == 0 else 4
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    d.polygon(pts, fill=(246, 214, 120, 255))
    # name band near the bottom (the set name is drawn in-game over it)
    d.rectangle([5, h - 20, w - 6, h - 11], fill=(16, 10, 30, 230), outline=(232, 186, 74, 255))
    img = img.resize((w * 4, h * 4), Image.NEAREST)
    img.save(OUT + "/booster_pack.png")


def flash():
    s = 128
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    px = img.load()
    for y in range(s):
        for x in range(s):
            dx, dy = x - s / 2, y - s / 2
            dist = math.hypot(dx, dy) / (s / 2)
            ang = math.atan2(dy, dx)
            rays = 0.5 + 0.5 * math.cos(ang * 12)
            a = max(0.0, 1 - dist) ** 1.5 * (0.55 + 0.45 * rays)
            px[x, y] = (255, 244, 210, int(255 * min(1, a * 1.4)))
    img.resize((s * 2, s * 2), Image.BILINEAR).save(OUT + "/pack_flash.png")


def glow():
    w, h = 80, 110
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([14, 14, w - 15, h - 15], radius=6, fill=(255, 196, 64, 255))
    img = img.filter(ImageFilter.GaussianBlur(7))
    img.save(OUT + "/rare_glow.png")


def table():
    w, h = 480, 270
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            # a dark felt table lit from the centre, with subtle wood-grain noise
            dx, dy = (x - w / 2) / w, (y - h * 0.55) / h
            light = max(0.0, 1 - math.hypot(dx * 1.3, dy * 1.8) * 1.6)
            grain = ((x * 7 + (y // 3) * 13) % 17) / 17.0 * 0.06
            c = lerp((14, 22, 20), (38, 66, 54), light * 0.9 + grain)
            px[x, y] = c
    d = ImageDraw.Draw(img)
    for i in range(70):  # dust motes
        x, y = rng.randrange(w), rng.randrange(h)
        d.point((x, y), fill=(70, 100, 86))
    img.resize((w * 2, h * 2), Image.NEAREST).save(OUT + "/opening_table.png")


pack()
flash()
glow()
table()
print("ok")


def shade():
    """A plain dark overlay (e.g. on sold cards)."""
    Image.new("RGBA", (8, 8), (0, 0, 0, 175)).save(OUT + "/shade.png")


shade()


def shop_bg():
    """The Card Shop backdrop: Forge's market art, blurred and darkened so cards stand out."""
    from PIL import ImageEnhance
    im = Image.open("forge-gui/res/adventure/common/ui/market.png").convert("RGB")
    im = im.filter(ImageFilter.GaussianBlur(radius=max(2, im.width // 240)))
    im = ImageEnhance.Color(ImageEnhance.Brightness(im).enhance(0.5)).enhance(0.7)
    im.save(OUT + "/shop_bg.png")


shop_bg()
