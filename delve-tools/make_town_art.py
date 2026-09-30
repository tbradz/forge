"""Generate the Delve town art (placeholder until custom art exists).

- town_bg.png: a procedurally painted dusk backdrop, drawn at 480x270 and
  scaled 2x with nearest-neighbour so it stays pixel-art crisp.
- building sprites: cropped from Forge's own adventure tileset
  (res/adventure/common/maps/tileset/buildings.png) and scaled 4x.

Every building is its own PNG so any of them can be swapped for new art later
without touching code. Run from the repo root:
    python3 delve-tools/make_town_art.py
"""
import math
import random
from PIL import Image, ImageDraw

RES = "forge-gui/res/adventure/common"
OUT = RES + "/ui/delve"
TILESET = RES + "/maps/tileset/buildings.png"

W, H = 480, 270
HORIZON = 150
rng = random.Random(7)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def backdrop():
    img = Image.new("RGB", (W, H))
    px = img.load()
    top, mid, low = (22, 18, 44), (74, 44, 78), (196, 104, 74)
    bands = 14  # banded gradient reads as pixel art
    for y in range(HORIZON + 10):
        t = y / (HORIZON + 10)
        t = round(t * bands) / bands
        c = lerp(top, mid, t / 0.6) if t < 0.6 else lerp(mid, low, (t - 0.6) / 0.4)
        for x in range(W):
            px[x, y] = c
    d = ImageDraw.Draw(img)
    # stars
    for _ in range(90):
        x, y = rng.randrange(W), rng.randrange(0, 90)
        b = rng.choice([150, 190, 230])
        px[x, y] = (b, b, min(255, b + 20))
    # moon
    d.ellipse((392, 22, 420, 50), fill=(236, 226, 196))
    d.ellipse((398, 20, 426, 48), fill=lerp(top, mid, 0.3))  # crescent bite
    # far mountains
    def ridge(base, amp, color, step, seed):
        r = random.Random(seed)
        pts, y = [(0, H)], base
        for x in range(0, W + step, step):
            y = max(base - amp, min(base + amp // 3, y + r.randint(-amp // 3, amp // 3)))
            pts.append((x, y))
        pts.append((W, H))
        d.polygon(pts, fill=color)
    ridge(118, 30, (58, 40, 72), 12, 1)
    ridge(138, 22, (40, 30, 56), 8, 2)
    # castle hill
    d.ellipse((150, 96, 330, 210), fill=(34, 28, 46))
    # ground
    for y in range(160, H):
        for x in range(W):
            n = rng.random()
            base = (40, 52, 38) if n > 0.12 else (34, 44, 32)
            px[x, y] = base
    # road up to the castle and plaza
    d.polygon([(226, 150), (254, 150), (300, H), (180, H)], fill=(92, 78, 64))
    d.ellipse((120, 196, 360, 290), fill=(88, 76, 66))
    for _ in range(420):  # cobbles
        x, y = rng.randrange(126, 354), rng.randrange(200, H)
        if ((x - 240) / 120) ** 2 + ((y - 243) / 47) ** 2 < 1:
            px[x, y] = (106, 94, 80) if rng.random() > 0.5 else (72, 62, 54)
    # vignette
    for y in range(H):
        for x in range(W):
            dx, dy = (x - W / 2) / (W / 2), (y - H / 2) / (H / 2)
            f = max(0.45, 1 - 0.55 * (dx * dx + dy * dy) ** 1.2)
            r, g, b = px[x, y]
            px[x, y] = (int(r * f), int(g * f), int(b * f))
    return img.resize((W * 2, H * 2), Image.NEAREST)


# name -> crop box (x, y, w, h) in buildings.png
SPRITES = {
    "castle": (384, 133, 64, 43),
    "dungeon": (32, 113, 32, 30),
    "house": (321, 624, 31, 32),
    "shop": (352, 624, 32, 32),
    "tavern": (288, 624, 32, 32),
    "outfitter": (226, 68, 28, 26),
}


def sprites():
    sheet = Image.open(TILESET).convert("RGBA")
    for name, (x, y, w, h) in SPRITES.items():
        s = sheet.crop((x, y, x + w, y + h))
        s = s.crop(s.getbbox())  # trim empty border
        s.resize((s.width * 4, s.height * 4), Image.NEAREST).save(f"{OUT}/{name}.png")


if __name__ == "__main__":
    import os
    os.makedirs(OUT, exist_ok=True)
    backdrop().save(f"{OUT}/town_bg.png")
    sprites()
    print("wrote", sorted(os.listdir(OUT)))
