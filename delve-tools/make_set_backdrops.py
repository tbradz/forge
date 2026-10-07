"""Set-themed dungeon backdrops for Delve: one look per plane, built like the crypt backdrop
(make_world_art.py --dungeon) but from other Forge Adventure maps' own tiles, so the style matches.

Each theme: an even floor of the map's floor tiles, a scatter of its decoration in small clusters,
then the same dungeon lighting (dark edges, light pools, torch glows) tinted for the plane.
Outputs per theme (960x540, like path_bg.png / dungeon_bg.png):
  ui/delve/backdrops/<theme>_path.png  (the dungeon map)
  ui/delve/backdrops/<theme>_menu.png  (gate / talk / panels)

Run from the repo root:  python delve-tools/make_set_backdrops.py [--preview DIR] [theme ...]
"""
import os
import random
import sys
import xml.etree.ElementTree as ET
from PIL import Image, ImageEnhance

sys.path.insert(0, os.path.dirname(__file__))
import render_tmx  # noqa: E402
from make_world_art import value_noise, glow, vignette, W, H, RES  # noqa: E402

MAPS = RES + "/maps/map/"
OUT = RES + "/ui/delve/backdrops"

# theme -> tiles (from one Adventure map) and lighting.
#   floor: [(gid, weight)], clutter: gid groups placed together, tint: (r, g, b) multipliers,
#   sat: colour saturation, dark: overall light, light: torch/lamp colour
THEMES = {
    "cave": dict(map="cave/cave_dino.tmx", floor=[(2438, 1)], clusters=14,
                 clutter=[[21, 6], [3454, 90], [167, 6, 21], [3452]],
                 sat=0.55, dark=0.54, tint=(1.0, 0.95, 0.88), light=(255, 150, 70)),
    "snow": dict(map="snowabbey/snowabbey_1.tmx", floor=[(5560, 60), (4935, 25)], clusters=9,
                 clutter=[[3428, 3427], [3435, 3433], [3429, 3741]],
                 sat=0.6, dark=0.52, tint=(0.86, 0.93, 1.08), light=(170, 210, 255)),
    "mage": dict(map="magetower/magetower_11_dreamhalls.tmx", floor=[(2936, 1)],
                 clusters=10, clutter=[[2496, 2497], [2497], [2496]],
                 sat=0.7, dark=0.6, tint=(0.95, 0.88, 1.12), light=(190, 140, 255)),
    "vampire": dict(map="vampirecastle/vampirecastle_1.tmx",
                    floor=[(18464, 1)], clusters=12,
                    clutter=[[17820, 17819], [18076, 18204], [19611, 17820], [18715, 18716]],
                    sat=0.6, dark=0.6, tint=(0.9, 0.95, 1.05), light=(120, 220, 200)),
    "factory": dict(map="factory/factory_1_golems.tmx", floor=[(1829, 60), (2462, 12)], clusters=10,
                    clutter=[[94], [1880, 3459], [10448, 5230], [10476], [10725, 10726], [1878, 5230]],
                    sat=0.55, dark=0.64, tint=(0.92, 0.97, 1.08), light=(120, 200, 255)),
    "phyrexia": dict(map="phyrexia/phyrexian_b1.tmx", floor=[(11916, 40), (11908, 30), (11917, 30)],
                     clusters=11, clutter=[[11963, 11956], [11915, 11931], [11947, 11940], [11958]],
                     sat=0.7, dark=0.6, tint=(0.88, 1.06, 0.92), light=(140, 255, 120)),
    "grove": dict(map="grove/grove_1_bears.tmx", floor=[(2406, 60), (2888, 30)], clusters=12,
                  clutter=[[591, 895], [742, 896], [581, 737], [2257, 2413]],
                  sat=0.6, dark=0.56, tint=(0.95, 1.04, 0.9), light=(255, 220, 130)),
    "desert": dict(map="monastery/monastery_1.tmx", floor=[(2438, 40), (2446, 30), (2920, 6), (2382, 4)],
                   clusters=12, clutter=[[3449, 3447], [3451, 3450], [2512, 234], [233, 76, 234]],
                   sat=0.6, dark=0.56, tint=(1.06, 0.97, 0.85), light=(255, 170, 80)),
    "monastery": dict(map="monastery/monastery_1.tmx", floor=[(13395, 1)], clusters=9,
                      clutter=[[5545], [1877], [6020], [927, 5545], [234, 233]],
                      sat=0.6, dark=0.64, tint=(1.04, 0.98, 0.9), light=(255, 190, 110)),
    "palace": dict(map="djinnpalace/djinnpalace_1.tmx", floor=[(2856, 60), (2810, 8)], clusters=9,
                   clutter=[[1876], [2034], [1879, 10639], [251, 92], [5545]],
                   sat=0.6, dark=0.52, tint=(1.05, 0.98, 0.9), light=(255, 200, 110)),
}

# where the scenes put their flickering torch glows (DelveMapScene / DelveGateScene): keep the art's light there
PATH_TORCHES = [(30, 140), (450, 120), (240, 250)]
MENU_TORCHES = [(60, 160), (420, 160)]


def tilesets_of(tmx):
    root = ET.parse(tmx).getroot()
    base = os.path.dirname(tmx)
    return sorted([(int(t.get("firstgid")), render_tmx.load_tileset(os.path.normpath(os.path.join(base, t.get("source")))))
                   for t in root.findall("tileset") if t.get("source")], key=lambda x: x[0])


def floor(theme, seed):
    t = THEMES[theme]
    ts = tilesets_of(MAPS + t["map"])
    r = random.Random(seed)
    cols, rows = W // 16 + 1, H // 16 + 1
    img = Image.new("RGBA", (cols * 16, rows * 16), (0, 0, 0, 255))
    pool = [g for g, n in t["floor"] for _ in range(n)]
    for y in range(rows):
        for x in range(cols):
            img.alpha_composite(render_tmx.tile_image(ts, r.choice(pool)), (x * 16, y * 16))
    # decoration in small clusters, sorted top to bottom so taller pieces overlap naturally
    pieces = []
    for _ in range(t["clusters"]):
        cx, cy = r.randrange(0, W), r.randrange(24, H)
        kind = r.choice(t["clutter"])
        for _ in range(r.randint(1, 4)):
            pieces.append((cy + r.randint(-10, 10), cx + r.randint(-16, 16), r.choice(kind)))
    for py, px, gid in sorted(pieces):
        im = render_tmx.tile_image(ts, gid)
        if im is not None:
            img.alpha_composite(im, (max(0, px), max(0, py - im.height)))
    return img.crop((0, 0, W, H)).convert("RGB")


def grade(img, theme, torches, seed):
    t = THEMES[theme]
    img = ImageEnhance.Color(img).enhance(t["sat"])
    w, h = img.size
    light = value_noise(w, h, 48, seed)
    edge = value_noise(w, h, 24, seed + 1)
    px, lp, ep = img.load(), light.load(), edge.load()
    tr, tg, tb = t["tint"]
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y]
            k = t["dark"] * (0.55 + 0.75 * lp[x, y] / 255)
            d = min(x, y, w - 1 - x, h - 1 - y) + (ep[x, y] - 128) / 6
            if d < 26:
                k *= max(0.05, d / 26)
            k = round(k * 10) / 10  # banded light reads as pixel art
            px[x, y] = (min(255, int(r * k * tr)), min(255, int(g * k * tg)), min(255, int(b * k * tb) + 3))
    img = glow(img, torches, 64, t["light"], 70)
    return vignette(img, 0.6)


def make(theme, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    path = grade(floor(theme, 11), theme, PATH_TORCHES, 31)
    path.resize((W * 2, H * 2), Image.NEAREST).save(f"{out_dir}/{theme}_path.png")
    menu = grade(floor(theme, 23), theme, MENU_TORCHES, 37)
    menu.resize((W * 2, H * 2), Image.NEAREST).save(f"{out_dir}/{theme}_menu.png")


if __name__ == "__main__":
    args = sys.argv[1:]
    out = OUT
    if "--preview" in args:
        i = args.index("--preview")
        out = args[i + 1]
        del args[i:i + 2]
    for name in args or THEMES:
        make(name, out)
        print("made", name)
