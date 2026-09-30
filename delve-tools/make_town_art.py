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


def backdrop(daytime=False):
    rng.seed(7)
    img = Image.new("RGB", (W, H))
    px = img.load()
    top, mid, low = ((78, 132, 196), (128, 176, 222), (214, 222, 206)) if daytime \
        else ((22, 18, 44), (74, 44, 78), (196, 104, 74))
    bands = 14  # banded gradient reads as pixel art
    for y in range(HORIZON + 10):
        t = y / (HORIZON + 10)
        t = round(t * bands) / bands
        c = lerp(top, mid, t / 0.6) if t < 0.6 else lerp(mid, low, (t - 0.6) / 0.4)
        for x in range(W):
            px[x, y] = c
    d = ImageDraw.Draw(img)
    if daytime:
        d.ellipse((396, 22, 424, 50), fill=(255, 236, 170))  # sun
        for cx, cy in ((80, 40), (170, 28), (300, 50)):  # clouds
            for dx in (0, 10, 20):
                d.ellipse((cx + dx, cy, cx + dx + 22, cy + 12), fill=(236, 242, 248))
    else:
        for _ in range(90):  # stars
            x, y = rng.randrange(W), rng.randrange(0, 90)
            b = rng.choice([150, 190, 230])
            px[x, y] = (b, b, min(255, b + 20))
        d.ellipse((392, 22, 420, 50), fill=(236, 226, 196))  # moon
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
    ridge(118, 30, (96, 120, 150) if daytime else (58, 40, 72), 12, 1)
    ridge(138, 22, (74, 100, 110) if daytime else (40, 30, 56), 8, 2)
    # castle hill
    d.ellipse((150, 96, 330, 210), fill=(62, 96, 58) if daytime else (34, 28, 46))
    # ground
    for y in range(160, H):
        for x in range(W):
            n = rng.random()
            base = ((76, 122, 60) if n > 0.12 else (66, 108, 52)) if daytime else \
                ((40, 52, 38) if n > 0.12 else (34, 44, 32))
            px[x, y] = base
    # road up to the castle and plaza
    road = (150, 128, 96) if daytime else (92, 78, 64)
    d.polygon([(226, 150), (254, 150), (300, H), (180, H)], fill=road)
    d.ellipse((120, 196, 360, 290), fill=(142, 122, 96) if daytime else (88, 76, 66))
    for _ in range(420):  # cobbles
        x, y = rng.randrange(126, 354), rng.randrange(200, H)
        if ((x - 240) / 120) ** 2 + ((y - 243) / 47) ** 2 < 1:
            px[x, y] = ((168, 148, 118) if rng.random() > 0.5 else (122, 104, 80)) if daytime else \
                ((106, 94, 80) if rng.random() > 0.5 else (72, 62, 54))
    # vignette
    for y in range(H):
        for x in range(W):
            dx, dy = (x - W / 2) / (W / 2), (y - H / 2) / (H / 2)
            f = max(0.7 if daytime else 0.45, 1 - (0.3 if daytime else 0.55) * (dx * dx + dy * dy) ** 1.2)
            r, g, b = px[x, y]
            px[x, y] = (int(r * f), int(g * f), int(b * f))
    return img.resize((W * 2, H * 2), Image.NEAREST)


def dungeon_backdrop():
    """Dark stone-brick wall with two torches, for the dungeon/menu screens."""
    img = Image.new("RGB", (W, H), (20, 18, 24))
    d = ImageDraw.Draw(img)
    r = random.Random(11)
    bh, bw = 12, 28
    for row in range(0, H // bh + 1):
        off = (row % 2) * (bw // 2)
        for col in range(-1, W // bw + 2):
            x0, y0 = col * bw + off, row * bh
            shade = r.randint(30, 44)
            d.rectangle((x0 + 1, y0 + 1, x0 + bw - 1, y0 + bh - 1),
                        fill=(shade, shade - 3, shade + 4))
    px = img.load()
    # torch glow
    for tx in (60, W - 60):
        for y in range(H):
            for x in range(W):
                dist = ((x - tx) ** 2 + (y - 70) ** 2) ** 0.5
                if dist < 110:
                    f = (1 - dist / 110) ** 2 * 0.9
                    rr, gg, bb = px[x, y]
                    px[x, y] = (min(255, int(rr + 120 * f)), min(255, int(gg + 60 * f)), min(255, int(bb + 10 * f)))
        d.rectangle((tx - 2, 70, tx + 2, 92), fill=(70, 50, 34))
        d.polygon([(tx - 5, 70), (tx, 56), (tx + 5, 70)], fill=(255, 170, 60))
        d.polygon([(tx - 2, 70), (tx, 62), (tx + 2, 70)], fill=(255, 236, 150))
    # darken for readable text
    for y in range(H):
        for x in range(W):
            dx, dy = (x - W / 2) / (W / 2), (y - H / 2) / (H / 2)
            f = max(0.35, 0.8 - 0.45 * (dx * dx + dy * dy))
            rr, gg, bb = px[x, y]
            px[x, y] = (int(rr * f), int(gg * f), int(bb * f))
    return img.resize((W * 2, H * 2), Image.NEAREST)


def path_backdrop():
    """Cave floor for the dungeon trail map: rough stone, scattered rocks, moss, torchlight."""
    r = random.Random(23)
    img = Image.new("RGB", (W, H), (30, 27, 30))
    px = img.load()
    for y in range(H):
        for x in range(W):
            n = r.random()
            base = 34 if n > 0.5 else 30
            if n > 0.97: base = 44
            px[x, y] = (base, base - 3, base - 2)
    d = ImageDraw.Draw(img)
    for _ in range(140):  # rocks
        x, y, s_ = r.randrange(W), r.randrange(40, H), r.choice([2, 3, 4, 5])
        c = r.randint(40, 58)
        d.ellipse((x, y, x + s_ + 2, y + s_), fill=(c, c - 4, c - 6))
        d.line((x + 1, y + s_, x + s_ + 1, y + s_), fill=(18, 16, 18))
    for _ in range(40):  # moss patches
        x, y = r.randrange(W), r.randrange(40, H)
        for _k in range(12):
            px[min(W - 1, x + r.randint(-4, 4)), min(H - 1, max(0, y + r.randint(-2, 2)))] = (38, 52, 34)
    for tx, ty in ((40, 60), (240, 50), (440, 60), (140, 250), (340, 250)):  # torch pools of light
        for y in range(max(0, ty - 120), min(H, ty + 120)):
            for x in range(max(0, tx - 120), min(W, tx + 120)):
                dist = ((x - tx) ** 2 + (y - ty) ** 2) ** 0.5
                if dist < 120:
                    f = (1 - dist / 120) ** 2 * 0.55
                    rr, gg, bb = px[x, y]
                    px[x, y] = (min(255, int(rr + 110 * f)), min(255, int(gg + 60 * f)), min(255, int(bb + 15 * f)))
    for y in range(H):
        for x in range(W):
            dx, dy = (x - W / 2) / (W / 2), (y - H / 2) / (H / 2)
            f = max(0.4, 1 - 0.5 * (dx * dx + dy * dy))
            rr, gg, bb = px[x, y]
            px[x, y] = (int(rr * f), int(gg * f), int(bb * f))
    return img.resize((W * 2, H * 2), Image.NEAREST)


def plate(glow):
    """Round stone platform a room stands on (24x12 base, scaled 4x)."""
    img = Image.new("RGBA", (26, 14), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if glow:
        d.ellipse((0, 0, 25, 13), fill=(255, 200, 90, 110))
    d.ellipse((1, 3, 24, 13), fill=(20, 18, 22, 255))
    d.ellipse((1, 1, 24, 11), fill=(88, 82, 90, 255))
    d.ellipse((3, 2, 22, 9), fill=(112, 106, 114, 255))
    d.arc((1, 1, 24, 11), 200, 340, fill=(140, 134, 142, 255))
    return img.resize((img.width * 4, img.height * 4), Image.NEAREST)


def dot(color):
    img = Image.new("RGBA", (3, 3), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 2, 2), fill=(0, 0, 0, 160))
    d.point((1, 1), fill=color)
    d.point((1, 0), fill=color)
    return img.resize((12, 12), Image.NEAREST)


def hud_bar():
    img = Image.new("RGBA", (W, 26), (12, 10, 14, 215))
    d = ImageDraw.Draw(img)
    d.line((0, 25, W, 25), fill=(120, 96, 60, 255))
    d.line((0, 24, W, 24), fill=(60, 48, 30, 255))
    return img.resize((W * 2, 52), Image.NEAREST)


def stairs():
    """Dungeon entrance: stone steps descending into darkness (16x16)."""
    img = Image.new("RGBA", (18, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([(1, 15), (4, 2), (13, 2), (16, 15)], fill=(10, 8, 12, 255))
    for i, y in enumerate((12, 9, 6, 3)):
        c = 110 - i * 22
        d.rectangle((3 + i, y, 14 - i, y + 2), fill=(c, c - 6, c - 4, 255))
    d.line((1, 15, 4, 2), fill=(70, 64, 72, 255))
    d.line((16, 15, 13, 2), fill=(70, 64, 72, 255))
    return img.resize((img.width * 4, img.height * 4), Image.NEAREST)


def panel():
    """Dark translucent panel with a thin bronze border, for text over busy art."""
    img = Image.new("RGBA", (40, 40), (12, 10, 14, 205))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, 39, 39), outline=(120, 96, 60, 255))
    return img.resize((160, 160), Image.NEAREST)


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
    backdrop(daytime=True).save(f"{OUT}/town_bg_day.png")
    dungeon_backdrop().save(f"{OUT}/dungeon_bg.png")
    path_backdrop().save(f"{OUT}/path_bg.png")
    plate(False).save(f"{OUT}/plate.png")
    plate(True).save(f"{OUT}/plate_glow.png")
    dot((150, 140, 150, 255)).save(f"{OUT}/dot.png")
    dot((255, 200, 80, 255)).save(f"{OUT}/dot_gold.png")
    hud_bar().save(f"{OUT}/hud_bar.png")
    stairs().save(f"{OUT}/stairs.png")
    panel().save(f"{OUT}/panel.png")
    sprites()
    print("wrote", sorted(os.listdir(OUT)))
