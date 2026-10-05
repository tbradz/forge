"""Delve's town hub art, built from Forge Adventure's own mountain-town map so the
town matches the rest of the world.

- town_bg_day.png / town_bg.png (night): the mountain town map with its generic houses
  removed, colour-graded grittier, at 2x (960x540).
- One sprite per location, at 4x, placed by delve_hub.json in map pixels:
  castle (stone capital built into the wall), dungeon (skull cave), tavern (great hall),
  shop / outfitter (houses with hanging signs), house.

Run from the repo root:  python3 delve-tools/make_world_art.py
"""
import os
import random
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

sys.path.insert(0, os.path.dirname(__file__))
import render_tmx  # noqa: E402

RES = "forge-gui/res/adventure/common"
OUT = RES + "/ui/delve"
TOWN = RES + "/maps/map/towns/mountain_town.tmx"
BUILDINGS = RES + "/maps/tileset/buildings.png"
W, H = 480, 270

# Foreground-layer areas (map pixels) whose tiles we erase: the houses, the cave and its sign
ERASE = [(60, 76, 100, 116), (108, 76, 148, 116), (156, 124, 196, 164), (252, 124, 292, 164),
         (348, 124, 388, 164), (200, 74, 246, 112), (188, 90, 206, 112)]

# location -> (sprite source box in buildings.png or 'house', bottom-centre x, bottom y in map pixels)
PLACES = {
    "castle": ((0, 464, 64, 64), 344, 108),      # PlainsCapital
    "dungeon": ((64, 272, 32, 32), 224, 112),    # SkullCave
    "tavern": ((48, 0, 48, 48), 100, 116),       # MountainTown great hall
    "shop": ("house+sign:352,864", 176, 164),    # house + card sign
    "outfitter": ("house+sign:304,784", 272, 164),
    "house": ("house", 368, 164),
}
FILLER_HOUSE_AT = (304, 136)  # the one generic house we keep, for a lived-in look


def render_without_houses():
    import xml.etree.ElementTree as ET
    orig_layer_gids = render_tmx.layer_gids

    def masked(layer, w, h):
        gids = orig_layer_gids(layer, w, h)
        if layer.get("name") not in ("Foreground", "AboveSprites"):
            return gids
        out = list(gids)
        for i in range(len(out)):
            x, y = (i % w) * 16 + 8, (i // w) * 16 + 8
            for x0, y0, x1, y1 in ERASE:
                if x0 <= x < x1 and y0 <= y < y1:
                    out[i] = 0
        return out

    render_tmx.layer_gids = masked
    try:
        img = render_tmx.render(TOWN, skip=["Objects"])
    finally:
        render_tmx.layer_gids = orig_layer_gids
    return img


def house_sprite():
    """The generic house, cut from the map's Foreground layer on transparency."""
    fg = render_tmx.render(TOWN, only=["Foreground"])
    # the Foreground render has an opaque black base; rebuild with transparency
    import xml.etree.ElementTree as ET
    root = ET.parse(TOWN).getroot()
    w, h = int(root.get("width")), int(root.get("height"))
    base = os.path.dirname(TOWN)
    tilesets = sorted([(int(t.get("firstgid")), render_tmx.load_tileset(os.path.normpath(os.path.join(base, t.get("source")))))
                       for t in root.findall("tileset")], key=lambda x: x[0])
    layer = [l for l in root.findall("layer") if l.get("name") == "Foreground"][0]
    gids = render_tmx.layer_gids(layer, w, h)
    img = Image.new("RGBA", (w * 16, h * 16), (0, 0, 0, 0))
    for i, gid in enumerate(gids):
        t = render_tmx.tile_image(tilesets, gid)
        if t is not None:
            img.alpha_composite(t, ((i % w) * 16, (i // w) * 16 + 16 - t.height))
    x, y = FILLER_HOUSE_AT
    return img.crop((x - 4, y - 4, x + 36, y + 30)).crop(img.crop((x - 4, y - 4, x + 36, y + 30)).getbbox())


def grade(img, night=False):
    img = img.convert("RGB")
    img = ImageEnhance.Color(img).enhance(0.72)       # less saturated: grittier
    img = ImageEnhance.Contrast(img).enhance(1.08)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b = px[x, y]
            if night:
                r, g, b = int(r * 0.30), int(g * 0.34), int(b * 0.52 + 12)
            else:
                r, g, b = int(r * 0.92), int(g * 0.90), int(b * 0.88)
            px[x, y] = (min(255, r), min(255, g), min(255, b))
    return img


def vignette(img, strength):
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((-w * 0.25, -h * 0.35, w * 1.25, h * 1.35), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(min(w, h) // 5))
    dark = Image.new("RGB", (w, h), (8, 6, 10))
    return Image.composite(img, Image.blend(img, dark, strength), mask)


def glow(img, spots, radius, color, alpha):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for x, y in spots:
        for r in range(radius, 0, -2):
            a = int(alpha * (1 - r / radius) ** 1.6)
            d.ellipse((x - r, y - r * 0.7, x + r, y + r * 0.7), fill=color + (a,))
    out = img.convert("RGBA")
    out.alpha_composite(layer)
    return out.convert("RGB")


def sprite(spec, buildings, house):
    if spec == "house":
        return house
    if isinstance(spec, str) and spec.startswith("house+sign:"):
        sx, sy = map(int, spec.split(":")[1].split(","))
        sign = buildings.crop((sx, sy, sx + 16, sy + 16))
        out = Image.new("RGBA", (house.width + 10, house.height + 2), (0, 0, 0, 0))
        out.alpha_composite(house, (0, 2))
        out.alpha_composite(sign, (out.width - 16, out.height - 16))
        return out
    x, y, w, h = spec
    return buildings.crop((x, y, x + w, y + h))


def main():
    os.makedirs(OUT, exist_ok=True)
    town = render_without_houses().crop((0, 0, W, H))
    buildings = Image.open(BUILDINGS).convert("RGBA")
    house = house_sprite()

    layout = {}
    for name, (spec, cx, by) in PLACES.items():
        s = sprite(spec, buildings, house)
        s.resize((s.width * 4, s.height * 4), Image.NEAREST).save(f"{OUT}/hub_{name}.png")
        layout[name] = (cx - s.width // 2, by - s.height, s.width, s.height)

    day = vignette(grade(town), 0.35)
    day.resize((W * 2, H * 2), Image.NEAREST).save(f"{OUT}/town_bg_day.png")

    night = grade(town, night=True)
    # warm light spilling from each building's door, and lamps along the road
    doors = [(x + w / 2, y + h - 2) for (x, y, w, h) in layout.values()]
    night = glow(night, doors, 30, (255, 170, 80), 170)
    night = glow(night, [(224, 150), (224, 236)], 20, (255, 190, 110), 110)
    night = vignette(night, 0.55)
    night.resize((W * 2, H * 2), Image.NEAREST).save(f"{OUT}/town_bg.png")

    for k, v in layout.items():
        print(k, v)


if __name__ == "__main__" and "--dungeon" not in sys.argv:
    # The top-down town this made was replaced by make_town_diorama.py (Tyler prefers the side view);
    # running it would overwrite town_bg*.png, so it needs an explicit flag now.
    if "--old-top-down-town" in sys.argv:
        main()
    else:
        print("Nothing to do: the town comes from make_town_diorama.py. Use --dungeon for the dungeon art.")


# ---- dungeon: a crypt floor built from the Adventure crypt map's own tiles ----------
CRYPT = RES + "/maps/map/graveyard_crypt/crypt_3.tmx"
FLOOR = [(2454, 60), (2462, 25), (5219, 5), (5062, 5), (1829, 5)]
CLUTTER = [3454, 3452, 235, 247, 64, 221, 3416, 3416]


def crypt_floor(seed, clutter=0.06, wall=True):
    import xml.etree.ElementTree as ET
    root = ET.parse(CRYPT).getroot()
    base = os.path.dirname(CRYPT)
    tilesets = sorted([(int(t.get("firstgid")), render_tmx.load_tileset(os.path.normpath(os.path.join(base, t.get("source")))))
                       for t in root.findall("tileset")], key=lambda x: x[0])
    r = random.Random(seed)
    cols, rows = W // 16 + 1, H // 16 + 1
    img = Image.new("RGBA", (cols * 16, rows * 16), (0, 0, 0, 255))
    pool = [g for g, n in FLOOR for _ in range(n)]
    for y in range(rows):
        for x in range(cols):
            img.alpha_composite(render_tmx.tile_image(tilesets, r.choice(pool)), (x * 16, y * 16))
            if r.random() < clutter and y > 1:
                t = render_tmx.tile_image(tilesets, r.choice(CLUTTER))
                img.alpha_composite(t, (x * 16, y * 16))
    if wall:  # the crypt's brick wall along the top
        t = render_tmx.tile_image(tilesets, 3258)
        for x in range(cols):
            img.alpha_composite(t, (x * 16, 0))
    return img.crop((0, 0, W, H)).convert("RGB")


def value_noise(w, h, cell, seed):
    """Smooth random field 0..1 (bilinear-upsampled random grid)."""
    r = random.Random(seed)
    gw, gh = w // cell + 2, h // cell + 2
    small = Image.new("L", (gw, gh))
    small.putdata([r.randrange(256) for _ in range(gw * gh)])
    return small.resize((gw * cell, gh * cell), Image.BICUBIC).crop((0, 0, w, h))


def dungeon_grade(img, darkness, torches, seed):
    img = ImageEnhance.Color(img).enhance(0.5)
    w, h = img.size
    light = value_noise(w, h, 48, seed)           # uneven light: pools and shadows
    edge = value_noise(w, h, 24, seed + 1)        # crumbling dark edges
    px, lp, ep = img.load(), light.load(), edge.load()
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            k = darkness * (0.55 + 0.75 * lp[x, y] / 255)
            # distance to the nearest screen edge, roughened by noise
            d = min(x, y, w - 1 - x, h - 1 - y) + (ep[x, y] - 128) / 6
            if d < 26:
                k *= max(0.05, d / 26)
            k = round(k * 10) / 10  # banded light reads as pixel art
            px[x, y] = (int(r * k * 0.92), int(g * k * 0.96), min(255, int(b * k * 1.08) + 3))
    img = glow(img, torches, 64, (255, 140, 60), 80)
    return vignette(img, 0.6)


def scatter_clusters(img, seed, n):
    """Rubble and moss in small clusters (reads less like a grid than single tiles)."""
    import xml.etree.ElementTree as ET
    root = ET.parse(CRYPT).getroot()
    base = os.path.dirname(CRYPT)
    tilesets = sorted([(int(t.get("firstgid")), render_tmx.load_tileset(os.path.normpath(os.path.join(base, t.get("source")))))
                       for t in root.findall("tileset")], key=lambda x: x[0])
    r = random.Random(seed)
    out = img.convert("RGBA")
    for _ in range(n):
        cx, cy = r.randrange(0, W), r.randrange(30, H)
        kind = r.choice([[221, 64, 221], [3416, 3416], [235, 247, 3454], [3452, 235]])
        for _ in range(r.randint(2, 5)):
            t = render_tmx.tile_image(tilesets, r.choice(kind))
            out.alpha_composite(t, (cx + r.randint(-14, 14), cy + r.randint(-10, 10)))
    return out.convert("RGB")


def torch_glow():
    """A soft warm light pool; the map scene flickers its alpha."""
    w, h = 128, 90
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for r in range(64, 0, -2):
        a = int(150 * (1 - r / 64) ** 1.8)
        d.ellipse((w / 2 - r, h / 2 - r * 0.7, w / 2 + r, h / 2 + r * 0.7), fill=(255, 150, 70, a))
    img.save(f"{OUT}/torch_glow.png")


def fog():
    """Wispy fog that tiles horizontally; the map scene drifts it slowly."""
    w, h = W, H
    n1, n2 = value_noise(w, h, 60, 91), value_noise(w, h, 22, 92)
    img = Image.new("RGBA", (w, h))
    p1, p2, out = n1.load(), n2.load(), img.load()
    for y in range(h):
        for x in range(w):
            # blend in the wrapped column so the left and right edges meet seamlessly
            t = x / w
            v = (p1[x, y] * 0.7 + p2[x, y] * 0.3) * (1 - t) + (p1[w - 1 - x, y] * 0.7 + p2[w - 1 - x, y] * 0.3) * t
            a = max(0, int((v - 135) * 0.6))
            out[x, y] = (120, 130, 145, min(40, a))
    img.save(f"{OUT}/fog.png")


def make_dungeon():
    path = dungeon_grade(scatter_clusters(crypt_floor(11, clutter=0.03), 5, 14), 0.66,
                         [(30, 140), (450, 120), (240, 250)], 31)
    torch_glow()
    fog()
    path.resize((W * 2, H * 2), Image.NEAREST).save(f"{OUT}/path_bg.png")
    menu = dungeon_grade(scatter_clusters(crypt_floor(23, clutter=0.03), 6, 10), 0.62,
                         [(60, 160), (420, 160)], 37)
    menu.resize((W * 2, H * 2), Image.NEAREST).save(f"{OUT}/dungeon_bg.png")


if __name__ == "__main__" and "--dungeon" in sys.argv:
    make_dungeon()
