"""Delve's town hub: the side-view diorama (the art direction Tyler chose), rebuilt.

Everything is painted at the buildings' own pixel size (one canvas pixel = one sprite
pixel; the game shows the building sprites at 2 layout units per pixel), so the
backdrop and buildings match. 240x135 canvas, scaled 4x to 960x540.

Writes town_bg_day.png, town_bg.png (night) and the six building sprites
(castle, dungeon, house, shop, tavern, outfitter) cropped whole from buildings.png.

Run from the repo root:  python3 delve-tools/make_town_diorama.py
"""
import os
import random
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
import render_tmx  # noqa: E402

RES = "forge-gui/res/adventure/common"
OUT = RES + "/ui/delve"
TILESET = RES + "/maps/tileset/buildings.png"
MAIN_TSX = RES + "/maps/tileset/main.tsx"
CW, CH = 240, 135          # canvas (sprite pixels)
SCALE = 4                  # -> 960x540

# whole-building crops in buildings.png (x, y, w, h), checked with margins
SPRITES = {
    "castle": (384, 128, 64, 48),
    "dungeon": (32, 113, 32, 30),
    "house": (321, 624, 31, 32),
    "shop": (352, 624, 32, 32),
    "tavern": (288, 624, 32, 32),
    "outfitter": (226, 68, 28, 26),
}
# where each building stands: bottom-centre x and ground y on the canvas
PLACE = {
    "castle": (120, 66),
    "house": (31, 112),
    "shop": (72, 104),
    "tavern": (168, 104),
    "outfitter": (209, 110),
    "dungeon": (120, 121),
}
HORIZON = 62


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def main_tile(local_id):
    ts = render_tmx.load_tileset(MAIN_TSX)
    cols = ts["cols"]
    x, y = (local_id % cols) * 16, (local_id // cols) * 16
    return ts["img"].crop((x, y, x + 16, y + 16))


def sky(img, night, r):
    d = ImageDraw.Draw(img)
    px = img.load()
    top, mid, low = ((22, 18, 44), (60, 40, 80), (170, 92, 76)) if night else ((70, 122, 188), (122, 170, 216), (206, 218, 210))
    for y in range(HORIZON + 6):
        t = round(y / (HORIZON + 6) * 9) / 9
        c = lerp(top, mid, t / 0.6) if t < 0.6 else lerp(mid, low, (t - 0.6) / 0.4)
        for x in range(CW):
            px[x, y] = c
    if night:
        for _ in range(70):
            x, y = r.randrange(CW), r.randrange(0, 44)
            b = r.choice([150, 190, 235])
            px[x, y] = (b, b, min(255, b + 20))
        d.ellipse((196, 9, 210, 23), fill=(238, 228, 198))
        d.ellipse((199, 8, 213, 22), fill=lerp(top, mid, 0.25))
    else:
        d.ellipse((198, 10, 212, 24), fill=(255, 238, 176))
        d.ellipse((200, 12, 210, 22), fill=(255, 248, 210))


def ridge(img, base, amp, step, seed, light, dark, snow):
    """A mountain range with lit and shaded faces, snow on the high peaks."""
    r = random.Random(seed)
    pts = []
    y = base
    for x in range(-step, CW + 2 * step, step):
        y = max(base - amp, min(base + amp // 4, y + r.randint(-amp // 2, amp // 2)))
        pts.append((x, y))
    px = img.load()
    for i in range(len(pts) - 1):
        (x0, y0), (x1, y1) = pts[i], pts[i + 1]
        for x in range(max(0, x0), min(CW, x1)):
            t = (x - x0) / (x1 - x0)
            ytop = int(y0 + (y1 - y0) * t)
            face = light if y1 > y0 else dark  # slopes facing the sun are lighter
            for yy in range(max(0, ytop), HORIZON + 8):
                c = face
                if snow and yy < ytop + 2 + (base - amp * 0.6 - ytop) // 2 and ytop < base - amp * 0.6:
                    c = snow
                px[x, yy] = c


def ground(img, night, r):
    px = img.load()
    g1, g2, g3 = ((44, 56, 42), (38, 48, 36), (52, 64, 48)) if night else ((84, 128, 64), (74, 114, 56), (98, 142, 72))
    for y in range(HORIZON, CH):
        for x in range(CW):
            n = r.random()
            px[x, y] = g2 if n < 0.18 else g3 if n > 0.93 else g1
    d = ImageDraw.Draw(img)
    # castle hill
    hill, hill_hi = ((36, 46, 36), (42, 54, 40)) if night else ((70, 108, 54), (82, 122, 62))
    d.ellipse((70, 50, 170, 100), fill=hill)
    d.ellipse((80, 52, 150, 76), fill=hill_hi)
    # grass tufts and flowers
    for _ in range(160):
        x, y = r.randrange(CW), r.randrange(HORIZON + 2, CH)
        c = (30, 40, 28) if night else (60, 98, 46)
        d.point((x, y), fill=c)
        d.point((x + 1, y - 1), fill=c)
    if not night:
        for _ in range(26):
            x, y = r.randrange(CW), r.randrange(HORIZON + 6, CH)
            d.point((x, y), fill=r.choice([(236, 220, 120), (230, 150, 170), (240, 240, 240)]))


def roads(img, night, r):
    d = ImageDraw.Draw(img)
    dirt, dirt_dk = ((80, 70, 58), (64, 56, 46)) if night else ((150, 126, 92), (128, 106, 76))
    # winding road up to the castle
    for y in range(66, 112):
        t = (y - 66) / 46
        cx = 120 + int(6 * (1 - t) * (1 if (y // 9) % 2 else -1))
        half = 4 + int(10 * t)
        d.line((cx - half, y, cx + half, y), fill=dirt)
        d.point((cx - half, y), fill=dirt_dk)
        d.point((cx + half, y), fill=dirt_dk)
    # paths to each building
    for name, (x, y) in PLACE.items():
        if name in ("castle", "dungeon"):
            continue
        d.line((x, y + 1, 120, 118), fill=dirt, width=3)
    # cobbled plaza in front of the gate
    stone, stone_dk, stone_hi = ((92, 86, 84), (64, 60, 60), (108, 102, 98)) if night else ((158, 146, 128), (118, 108, 94), (178, 168, 150))
    d.ellipse((76, 108, 164, 136), fill=stone_dk)
    px = img.load()
    for y in range(108, CH):
        for x in range(76, 164):
            if ((x - 120) / 44) ** 2 + ((y - 122) / 14) ** 2 < 1:
                row = y // 3
                brick = (x + (row % 2) * 2) // 4
                edge = (x + (row % 2) * 2) % 4 == 0 or y % 3 == 0
                px[x, y] = stone_dk if edge else (stone_hi if (brick * 7 + row * 3) % 5 == 0 else stone)


def trees(img, night, r):
    """A line of pines along the horizon and a few in front, from the Adventure tiles."""
    pine = main_tile(1527).convert("RGBA")  # gid 1528 in the town map = local id 1527
    if night:
        r_, g_, b_, a_ = pine.split()
        pine = Image.merge("RGBA", (r_.point(lambda v: int(v * 0.5)), g_.point(lambda v: int(v * 0.55)),
                                    b_.point(lambda v: int(v * 0.8)), a_))
    spots = [(x, HORIZON - 12 + r.randint(-2, 2)) for x in range(-6, CW, 9) if not (60 < x < 176)]
    spots += [(4, 116), (226, 118), (52, 72), (186, 72)]
    for x, y in sorted(spots, key=lambda s: s[1]):
        img.alpha_composite(pine, (x, y))


def lamps(img, night):
    d = ImageDraw.Draw(img)
    posts = [(96, 112), (144, 112), (52, 108), (188, 108)]
    for x, y in posts:
        d.line((x, y - 12, x, y), fill=(40, 34, 30))
        d.rectangle((x - 1, y - 15, x + 1, y - 12), fill=(255, 210, 120) if night else (90, 80, 60))
        d.point((x, y - 16), fill=(40, 34, 30))
    return posts


def fences(img, night):
    d = ImageDraw.Draw(img)
    wood = (70, 54, 40) if night else (128, 96, 64)
    for x0, x1, y in ((4, 22, 120), (218, 238, 124)):
        d.line((x0, y - 3, x1, y - 3), fill=wood)
        d.line((x0, y - 1, x1, y - 1), fill=wood)
        for x in range(x0, x1 + 1, 4):
            d.line((x, y - 5, x, y), fill=wood)


def shadows(img, sizes):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for name, (x, y) in PLACE.items():
        w = sizes[name][0]
        d.ellipse((x - w * 0.55, y - 3, x + w * 0.55, y + 3), fill=(0, 0, 0, 70))
    img.alpha_composite(layer)


def night_grade(img):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if y > HORIZON - 14:  # the sky is already painted for night
                r, g, b = int(r * 0.78), int(g * 0.8), int(b * 0.92 + 6)
            px[x, y] = (min(255, r), min(255, g), min(255, b), a)


def glows(img, spots, radius, color, alpha):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for x, y in spots:
        for r in range(radius, 0, -1):
            a = int(alpha * (1 - r / radius) ** 1.5)
            d.ellipse((x - r, y - r * 0.6, x + r, y + r * 0.6), fill=color + (a,))
    img.alpha_composite(layer)


def vignette(img, strength):
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            dx, dy = (x - w / 2) / (w / 2), (y - h / 2) / (h / 2)
            f = max(1 - strength, 1 - strength * (dx * dx + dy * dy) ** 1.3)
            r, g, b, a = px[x, y]
            px[x, y] = (int(r * f), int(g * f), int(b * f), a)


def scene(night, sizes):
    r = random.Random(7)
    img = Image.new("RGBA", (CW, CH), (0, 0, 0, 255))
    sky(img, night, r)
    if night:
        ridge(img, 46, 18, 10, 1, (64, 52, 88), (48, 40, 70), (150, 150, 180))
        ridge(img, 56, 12, 7, 2, (44, 38, 62), (36, 30, 52), None)
    else:
        ridge(img, 46, 18, 10, 1, (132, 150, 182), (104, 122, 156), (236, 240, 248))
        ridge(img, 56, 12, 7, 2, (96, 124, 128), (80, 106, 112), None)
    ground(img, night, r)
    trees(img, night, r)
    roads(img, night, r)
    fences(img, night)
    shadows(img, sizes)
    posts = lamps(img, night)
    if night:
        night_grade(img)
        glows(img, [(x, y - 13) for x, y in posts], 14, (255, 190, 110), 140)
        glows(img, [(x, y - 4) for (x, y) in PLACE.values()], 16, (255, 170, 80), 110)
        vignette(img, 0.5)
    else:
        vignette(img, 0.25)
    return img.convert("RGB").resize((CW * SCALE, CH * SCALE), Image.NEAREST)


def main():
    os.makedirs(OUT, exist_ok=True)
    sheet = Image.open(TILESET).convert("RGBA")
    sizes, layout = {}, {}
    for name, (x, y, w, h) in SPRITES.items():
        s = sheet.crop((x, y, x + w, y + h))
        s = s.crop(s.getbbox())
        s.resize((s.width * 4, s.height * 4), Image.NEAREST).save(f"{OUT}/{name}.png")
        sizes[name] = s.size
        cx, gy = PLACE[name]
        # layout units: 2 per sprite pixel; canvas pixel = 2 layout units too
        layout[name] = (int((cx - s.width / 2) * 2), int((gy - s.height) * 2), s.width * 2, s.height * 2)
    scene(False, sizes).save(f"{OUT}/town_bg_day.png")
    scene(True, sizes).save(f"{OUT}/town_bg.png")
    for k, v in layout.items():
        print(k, v)
    return layout


if __name__ == "__main__":
    main()
