"""Delve's interiors, painted to match the side-view town (make_town_diorama.py).

Same rules as the town: one canvas pixel = one sprite pixel, 240x135 canvas scaled 4x to
960x540, flat shaded colours, warm light pools and a vignette. No photo or painted source
art: every room is drawn here from simple shapes so it reads as one hand-made pixel world.

Screens are mostly covered by dark panels (about 80% opaque) in menus, but the talk scenes
show the top half in full, so each room keeps its centrepiece up top and its clutter at the
edges and along the floor.

Writes (into ui/delve/):
  shop_bg.png       Card Shop (day): plank walls, pack shelves, window, counter
  tavern_bg.png     Tavern (evening): hearth, beams, lanterns, barrels, tables
  castle_bg.png     Castle (evening): stone hall, tall windows, banners, dais
  outfitter_bg.png  Outfitter (day): fabric bolts, sleeves on a line, mirror, dress form
  opening_table.png pack opening: a lamplit wooden table seen from above
  plate.png / plate_glow.png / dot.png / dot_gold.png  dungeon map: stone slabs, flagstones
  playmat_*.png     playmats under your side of the battlefield (Outfitter / Castle titles)

Run from the repo root:  python delve-tools/make_interiors.py [--preview DIR]
"""
import os
import random
import sys
from PIL import Image, ImageDraw

OUT = "forge-gui/res/adventure/common/ui/delve"
CW, CH = 240, 135
SCALE = 4


# ---------------------------------------------------------------- helpers

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def mul(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def canvas(c=(0, 0, 0)):
    return Image.new("RGBA", (CW, CH), c + (255,))


def planks(img, box, base, seed, vertical=True, width=6):
    """Wooden boards with seams, grain flecks and slightly different tones per board."""
    r = random.Random(seed)
    x0, y0, x1, y1 = box
    px = img.load()
    n = (x1 - x0) if vertical else (y1 - y0)
    tones = [mul(base, r.choice([0.9, 0.96, 1.0, 1.04, 1.08])) for _ in range(n // width + 2)]
    seam = mul(base, 0.62)
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = (x - x0) if vertical else (y - y0)
            board = k // width
            c = tones[board]
            if k % width == 0:
                c = seam
            elif r.random() < 0.05:
                c = mul(c, 0.86)
            px[x, y] = c + (255,)
    # board ends (short horizontal joints) so the wall doesn't look like stripes
    d = ImageDraw.Draw(img)
    if vertical:
        for b in range(n // width + 1):
            yy = r.randrange(y0 + 4, max(y0 + 5, y1 - 4))
            xa = x0 + b * width + 1
            d.line((xa, yy, min(x1 - 1, xa + width - 2), yy), fill=seam)


def bricks(img, box, base, seed, bw=8, bh=4):
    """Stone blocks in running bond with mortar and per-block tint."""
    r = random.Random(seed)
    x0, y0, x1, y1 = box
    px = img.load()
    mortar = mul(base, 0.6)
    tint = {}
    for y in range(y0, y1):
        row = (y - y0) // bh
        off = (row % 2) * (bw // 2)
        for x in range(x0, x1):
            col = (x - x0 + off) // bw
            if (row, col) not in tint:
                tint[(row, col)] = r.choice([0.86, 0.92, 0.97, 1.0, 1.04, 1.1])
            top = (y - y0) % bh == 0
            side = (x - x0 + off) % bw == 0
            if top or side:
                c = mortar
            else:
                c = mul(base, tint[(row, col)])
                if (y - y0) % bh == 1:  # lit top edge of each block
                    c = mul(c, 1.12)
            px[x, y] = c + (255,)


def floorboards(img, y0, base, seed):
    """Floor boards running across the room, rows getting deeper toward the viewer."""
    r = random.Random(seed)
    px = img.load()
    y, h, rows = y0, 2, []
    while y < CH:
        rows.append((y, h))
        y += h
        h = min(h + 1, 7)
    seam = mul(base, 0.6)
    for i, (ry, rh) in enumerate(rows):
        f = 0.8 + 0.25 * (i / max(1, len(rows) - 1))
        tone = mul(base, f * r.choice([0.94, 1.0, 1.05]))
        joint = r.randrange(0, 30)
        for yy in range(ry, min(CH, ry + rh)):
            for x in range(CW):
                c = seam if yy == ry or (x + joint + i * 13) % 37 == 0 else tone
                px[x, yy] = c + (255,)


def glow(img, spots, radius, color, alpha, squash=0.65):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for x, y in spots:
        for rr in range(radius, 0, -1):
            a = int(alpha * (1 - rr / radius) ** 1.6)
            d.ellipse((x - rr, y - rr * squash, x + rr, y + rr * squash), fill=color + (a,))
    img.alpha_composite(layer)


def vignette(img, strength):
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            dx, dy = (x - w / 2) / (w / 2), (y - h / 2) / (h / 2)
            f = max(1 - strength, 1 - strength * (dx * dx + dy * dy) ** 1.3)
            r_, g_, b_, a_ = px[x, y]
            px[x, y] = (int(r_ * f), int(g_ * f), int(b_ * f), a_)


def grade(img, rm, gm, bm, add=0):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r_, g_, b_, a_ = px[x, y]
            px[x, y] = (min(255, int(r_ * rm)), min(255, int(g_ * gm)), min(255, int(b_ * bm) + add), a_)


def shadow(img, box, alpha=70):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse(box, fill=(0, 0, 0, alpha))
    img.alpha_composite(layer)


def finish(img):
    return img.convert("RGB").resize((CW * SCALE, CH * SCALE), Image.NEAREST)


# ---------------------------------------------------------------- props

def beam(d, y, x0=0, x1=CW, h=5, wood=(74, 50, 34)):
    d.rectangle((x0, y, x1, y + h), fill=wood)
    d.line((x0, y, x1, y), fill=mul(wood, 1.3))
    d.line((x0, y + h, x1, y + h), fill=mul(wood, 0.55))


def post(d, x, y0, y1, w=5, wood=(74, 50, 34)):
    d.rectangle((x, y0, x + w, y1), fill=wood)
    d.line((x, y0, x, y1), fill=mul(wood, 1.3))
    d.line((x + w, y0, x + w, y1), fill=mul(wood, 0.55))


def window(d, x, y, w, h, sky_top, sky_low, frame=(70, 48, 32), arch=False, panes=(2, 3)):
    for yy in range(y, y + h):
        c = lerp(sky_top, sky_low, (yy - y) / max(1, h - 1))
        d.line((x, yy, x + w, yy), fill=c)
    if arch:  # round the top with the wall colour drawn by the caller afterwards
        pass
    d.rectangle((x - 2, y - 2, x + w + 2, y + h + 2), outline=frame, width=2)
    cols, rows = panes
    for i in range(1, cols):
        xx = x + w * i // cols
        d.line((xx, y, xx, y + h), fill=frame)
    for j in range(1, rows):
        yy = y + h * j // rows
        d.line((x, yy, x + w, yy), fill=frame)
    d.rectangle((x - 3, y + h + 2, x + w + 3, y + h + 4), fill=mul(frame, 1.2))  # sill


def barrel(d, x, y, h=14, w=11, wood=(120, 80, 48)):
    """x = left, y = bottom."""
    top = y - h
    d.rounded_rectangle((x, top, x + w, y), radius=3, fill=wood)
    d.line((x + 2, top + 1, x + 2, y - 1), fill=mul(wood, 1.25))
    d.line((x + w - 2, top + 1, x + w - 2, y - 1), fill=mul(wood, 0.7))
    hoop = (64, 60, 58)
    for hy in (top + 2, y - 3):
        d.line((x, hy, x + w, hy), fill=hoop)
    d.ellipse((x + 1, top - 1, x + w - 1, top + 2), fill=mul(wood, 0.8), outline=mul(wood, 0.55))


def crate(d, x, y, s=10, wood=(132, 96, 58)):
    d.rectangle((x, y - s, x + s, y), fill=wood, outline=mul(wood, 0.55))
    d.line((x + 1, y - s + 1, x + s - 1, y - 1), fill=mul(wood, 0.75))
    d.line((x + 1, y - s + 2, x + s - 1, y - s + 2), fill=mul(wood, 1.2))


def mug(d, x, y, c=(150, 112, 70)):
    d.rectangle((x, y - 3, x + 2, y), fill=c)
    d.point((x + 3, y - 2), fill=mul(c, 0.7))
    d.line((x, y - 4, x + 2, y - 4), fill=(236, 228, 210))  # foam


def table(d, x, y, w=30, wood=(110, 72, 44)):
    """Round tavern table, x = centre, y = floor."""
    top = y - 9
    d.ellipse((x - w // 2, top - 3, x + w // 2, top + 3), fill=mul(wood, 1.15), outline=mul(wood, 0.6))
    d.rectangle((x - 1, top + 3, x + 1, y), fill=mul(wood, 0.7))
    d.line((x - 5, y, x + 5, y), fill=mul(wood, 0.6))


def stool(d, x, y, wood=(104, 70, 42)):
    d.ellipse((x - 3, y - 6, x + 3, y - 4), fill=mul(wood, 1.1))
    d.line((x - 2, y - 4, x - 3, y), fill=mul(wood, 0.7))
    d.line((x + 2, y - 4, x + 3, y), fill=mul(wood, 0.7))


def lantern(d, x, y, lit=True, chain=0):
    if chain:
        d.line((x, y - chain, x, y - 3), fill=(50, 44, 40))
    d.rectangle((x - 2, y - 3, x + 2, y + 3), fill=(52, 44, 38))
    d.rectangle((x - 1, y - 2, x + 1, y + 2), fill=(255, 214, 120) if lit else (120, 110, 90))
    d.line((x - 2, y - 4, x + 2, y - 4), fill=(52, 44, 38))


def candle(d, x, y):
    d.rectangle((x, y - 4, x + 1, y), fill=(232, 222, 196))
    d.point((x, y - 5), fill=(255, 210, 110))
    d.point((x, y - 6), fill=(255, 240, 190))


def bottle(d, x, y, c):
    d.rectangle((x, y - 4, x + 2, y), fill=c)
    d.point((x + 1, y - 5), fill=mul(c, 0.8))
    d.point((x + 1, y - 6), fill=(90, 70, 50))
    d.point((x, y - 3), fill=mul(c, 1.5))


PACK_COLOURS = [(96, 52, 140), (40, 92, 150), (150, 48, 48), (46, 120, 72), (180, 140, 50), (30, 30, 36), (210, 210, 200)]


def pack_box(d, x, y, c, w=5, h=8):
    """A booster pack standing on a shelf (x left, y = shelf top)."""
    d.rectangle((x, y - h, x + w - 1, y - 1), fill=c)
    d.line((x, y - h, x + w - 1, y - h), fill=(200, 200, 210))           # crimp
    d.line((x + 1, y - h + 1, x + 1, y - 2), fill=mul(c, 1.45))           # foil sheen
    d.point((x + w // 2, y - h // 2), fill=(240, 200, 90))                # emblem


def shelf(d, x0, x1, y, wood=(96, 64, 40)):
    d.rectangle((x0, y, x1, y + 2), fill=wood)
    d.line((x0, y, x1, y), fill=mul(wood, 1.35))
    d.line((x0, y + 3, x1, y + 3), fill=(0, 0, 0))
    for bx in (x0 + 2, x1 - 3):
        d.line((bx, y + 3, bx + 1, y + 5), fill=mul(wood, 0.7))


def banner(d, x, y, w, h, cloth, trim=(212, 170, 70), emblem="tower"):
    d.line((x - 2, y, x + w + 2, y), fill=(70, 56, 40), width=2)
    pts = [(x, y + 1), (x + w, y + 1), (x + w, y + h), (x + w // 2, y + h - 5), (x, y + h)]
    d.polygon(pts, fill=cloth)
    d.line((x + 1, y + 2, x + 1, y + h - 1), fill=mul(cloth, 1.3))
    d.line((x + w - 1, y + 2, x + w - 1, y + h - 1), fill=mul(cloth, 0.7))
    d.line((x + 1, y + 4, x + w - 1, y + 4), fill=trim)
    cx, cy = x + w // 2, y + h // 2
    if emblem == "tower":
        d.rectangle((cx - 2, cy - 3, cx + 2, cy + 4), fill=trim)
        for i in (-2, 0, 2):
            d.point((cx + i, cy - 4), fill=trim)
    else:  # sun
        d.ellipse((cx - 3, cy - 3, cx + 3, cy + 3), fill=trim)
        d.point((cx, cy - 5), fill=trim)
        d.point((cx, cy + 5), fill=trim)
        d.point((cx - 5, cy), fill=trim)
        d.point((cx + 5, cy), fill=trim)


def sconce(d, x, y):
    d.rectangle((x - 1, y, x + 1, y + 5), fill=(70, 60, 52))
    d.line((x - 2, y, x + 2, y), fill=(90, 80, 70))
    d.point((x, y - 1), fill=(255, 170, 60))
    d.point((x, y - 2), fill=(255, 220, 140))
    d.point((x - 1, y - 1), fill=(240, 120, 40))
    d.point((x + 1, y - 1), fill=(240, 120, 40))


def fire(d, x, y, w, r):
    """Flames on a log pile, x = left, y = hearth floor."""
    d.rectangle((x + 1, y - 2, x + w - 1, y), fill=(84, 52, 30))
    d.line((x + 2, y - 3, x + w - 3, y - 1), fill=(110, 70, 40))
    for i in range(w):
        h = r.randint(3, 8) if 1 < i < w - 2 else r.randint(1, 3)
        for j in range(h):
            t = j / max(1, h)
            c = (255, 240, 170) if t < 0.25 else (255, 180, 60) if t < 0.6 else (220, 90, 30)
            if j > 0 and r.random() < 0.15:
                continue
            d.point((x + i, y - 3 - j), fill=c)


# ---------------------------------------------------------------- rooms

def tavern():
    r = random.Random(5)
    img = canvas()
    d = ImageDraw.Draw(img)
    wall = (104, 72, 48)
    planks(img, (0, 0, CW, 74), wall, 1, vertical=True, width=7)
    # stone hearth, centre back
    hx0, hx1 = 92, 148
    bricks(img, (hx0, 12, hx1, 74), (118, 112, 106), 2, bw=7, bh=4)
    d.rectangle((hx0 - 3, 10, hx1 + 3, 14), fill=(90, 62, 40))  # mantel
    d.line((hx0 - 3, 10, hx1 + 3, 10), fill=(130, 92, 60))
    d.rectangle((104, 42, 136, 74), fill=(20, 14, 12))              # firebox
    d.ellipse((104, 36, 136, 48), fill=(20, 14, 12))
    fire(d, 108, 69, 25, r)
    # mantel clutter
    for i, x in enumerate((96, 101, 140, 144)):
        bottle(d, x, 9, [(70, 110, 70), (120, 60, 50), (150, 120, 60), (70, 80, 120)][i])
    candle(d, 119, 9)
    candle(d, 123, 9)
    # beams and posts
    beam(d, 0, h=6)
    post(d, 34, 6, 74)
    post(d, 202, 6, 74)
    # shelves of bottles left; mugs on hooks right
    shelf(d, 6, 30, 30)
    shelf(d, 6, 30, 48)
    for k, sy in enumerate((30, 48)):
        for x in range(8, 29, 4):
            bottle(d, x, sy, r.choice([(60, 100, 60), (120, 60, 50), (150, 120, 60), (80, 90, 130)]))
    d.line((210, 28, 236, 28), fill=(70, 50, 34))
    for x in range(212, 236, 5):
        d.line((x, 28, x, 30), fill=(60, 56, 50))
        d.rectangle((x - 1, 31, x + 2, 35), fill=(140, 104, 66))
    # windows with a night sky either side of the hearth
    window(d, 52, 20, 22, 26, (24, 22, 54), (60, 46, 86), panes=(2, 2))
    window(d, 166, 20, 22, 26, (24, 22, 54), (60, 46, 86), panes=(2, 2))
    for x, y in ((56, 23), (70, 28), (171, 25), (183, 22)):
        d.point((x, y), fill=(220, 220, 240))
    # dart board-ish target as a tavern touch
    d.ellipse((212, 42, 226, 56), fill=(60, 44, 32))
    d.ellipse((214, 44, 224, 54), fill=(200, 190, 160))
    d.ellipse((217, 47, 221, 51), fill=(170, 50, 40))
    # floor
    floorboards(img, 74, (112, 76, 48), 3)
    d = ImageDraw.Draw(img)
    d.line((0, 74, CW, 74), fill=(50, 34, 24))
    # hanging lanterns
    lamps = [(64, 14), (176, 14), (120, 8)]
    for x, y in lamps[:2]:
        lantern(d, x, y, chain=8)
    # furniture along the floor
    for tx in (36, 120, 204):
        shadow(img, (tx - 18, 125, tx + 18, 131))
    d = ImageDraw.Draw(img)
    for tx in (36, 120, 204):
        table(d, tx, 128, 30, wood=(150, 100, 60))
        stool(d, tx - 20, 130, wood=(140, 94, 56))
        stool(d, tx + 20, 130, wood=(140, 94, 56))
    mug(d, 30, 116)
    mug(d, 40, 116)
    candle(d, 120, 116)
    mug(d, 198, 116)
    mug(d, 210, 116)
    # cards on the middle table
    d.rectangle((110, 116, 114, 117), fill=(220, 214, 196))
    d.rectangle((126, 116, 130, 117), fill=(220, 214, 196))
    barrel(d, 2, 104, 16, 12)
    barrel(d, 14, 106, 14, 11)
    barrel(d, 226, 104, 16, 12)
    crate(d, 216, 106, 9)
    # evening: warm, dim; light from hearth, lanterns and candles
    grade(img, 0.72, 0.62, 0.62)
    glow(img, [(120, 60)], 70, (255, 150, 60), 150, 0.8)
    glow(img, [(64, 15), (176, 15)], 26, (255, 190, 110), 120)
    glow(img, [(120, 112)], 18, (255, 190, 110), 100)
    glow(img, [(119, 4), (123, 4)], 10, (255, 200, 120), 90)
    vignette(img, 0.55)
    return img


def shop():
    r = random.Random(8)
    img = canvas()
    d = ImageDraw.Draw(img)
    planks(img, (0, 0, CW, 100), (150, 112, 74), 4, vertical=True, width=8)
    beam(d, 0, h=5, wood=(96, 66, 42))
    # a window letting daylight in, top right
    window(d, 196, 16, 30, 34, (120, 170, 220), (200, 220, 230), frame=(96, 66, 42), panes=(2, 2))
    d.rectangle((197, 42, 225, 49), fill=(126, 160, 110))  # hills through the glass
    # shelves of packs across the back wall
    for sy in (30, 52, 74):
        shelf(d, 8, 182, sy)
        x = 11
        while x < 178:
            c = r.choice(PACK_COLOURS)
            run = r.randint(2, 5)
            for _ in range(run):
                if x > 177:
                    break
                pack_box(d, x, sy, c)
                x += 6
            x += r.choice([2, 3, 6])
    # card binders / boxes on the top shelf ends
    d.rectangle((186, 70, 192, 74), fill=(70, 40, 40))
    d.rectangle((193, 68, 199, 74), fill=(40, 60, 90))
    shelf(d, 184, 232, 74)
    for x in (204, 212, 220):
        d.rectangle((x, 66, x + 6, 73), fill=(230, 222, 200))  # deck boxes
        d.line((x, 66, x + 6, 66), fill=(180, 60, 50))
    # hanging sign
    d.line((150, 5, 150, 9), fill=(60, 50, 40))
    d.line((172, 5, 172, 9), fill=(60, 50, 40))
    d.rectangle((146, 9, 176, 18), fill=(80, 54, 34), outline=(50, 34, 22))
    for i, x in enumerate(range(152, 172, 5)):  # little card glyphs instead of letters
        d.rectangle((x, 11, x + 3, 16), fill=(230, 214, 170))
        d.point((x + 1, 13), fill=PACK_COLOURS[i][:3])
    # counter along the bottom
    floorboards(img, 100, (120, 84, 54), 6)
    d = ImageDraw.Draw(img)
    d.rectangle((0, 108, CW, 135), fill=(110, 72, 44))
    d.rectangle((0, 106, CW, 109), fill=(150, 104, 64))
    d.line((0, 106, CW, 106), fill=(190, 140, 90))
    for x in range(0, CW, 24):
        d.rectangle((x + 3, 113, x + 20, 131), outline=(84, 54, 32))
    # on the counter: a till, a lamp, a jar of tokens, a stack of packs
    d.rectangle((20, 98, 34, 105), fill=(90, 70, 50), outline=(60, 44, 30))
    d.rectangle((22, 99, 32, 101), fill=(210, 180, 90))
    d.rectangle((214, 96, 220, 105), fill=(170, 200, 210), outline=(110, 130, 140))
    for i in range(4):
        d.point((215 + i % 3 * 2, 101 + i // 3 * 2), fill=(230, 190, 70))
    for i in range(3):
        pack_box(d, 190 + i * 6, 106, PACK_COLOURS[i])
    candle(d, 120, 105)
    # Tyler asked for a calm shop: the singles sit right on this backdrop, so keep it dim and soft
    grade(img, 0.66, 0.62, 0.6)
    # daylight: a shaft from the window onto the counter
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).polygon([(196, 16), (226, 16), (200, 108), (150, 108)], fill=(255, 240, 200, 34))
    img.alpha_composite(layer)
    glow(img, [(212, 32)], 40, (255, 240, 200), 70)
    vignette(img, 0.35)
    return img


def outfitter():
    r = random.Random(12)
    img = canvas()
    d = ImageDraw.Draw(img)
    planks(img, (0, 0, CW, 96), (126, 104, 120), 9, vertical=True, width=7)  # painted boards, plum
    beam(d, 0, h=5, wood=(84, 58, 44))
    # rolls of cloth in a cubby rack, seen end-on (spirals read as fabric, not books)
    d.rectangle((6, 22, 66, 90), fill=(60, 42, 30))
    for row, y in enumerate((38, 54, 70, 86)):
        d.rectangle((6, y, 66, y + 1), fill=(96, 66, 44))
        for i in range(5):
            c = r.choice([(170, 50, 60), (50, 90, 160), (60, 130, 80), (200, 170, 80), (120, 70, 150), (220, 214, 200), (64, 64, 72)])
            cx, cy = 12 + i * 12, y - 6
            d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=c, outline=mul(c, 0.6))
            d.arc((cx - 3, cy - 3, cx + 3, cy + 3), 200, 520, fill=mul(c, 0.7))
            d.arc((cx - 1, cy - 1, cx + 1, cy + 1), 0, 360, fill=mul(c, 0.55))
            d.arc((cx - 5, cy - 5, cx + 5, cy + 5), 200, 290, fill=mul(c, 1.35))
    # card sleeves pinned on a line across the top, the shop's speciality
    d.line((76, 20, 228, 24), fill=(70, 60, 50))
    x = 80
    while x < 224:
        c = r.choice(PACK_COLOURS + [(200, 90, 140), (90, 200, 200)])
        yy = 21 + (x - 76) * 4 // 152
        d.rectangle((x, yy + 1, x + 7, yy + 12), fill=c, outline=mul(c, 0.6))
        d.line((x + 1, yy + 2, x + 1, yy + 11), fill=mul(c, 1.4))
        d.point((x + 3, yy), fill=(200, 190, 160))
        x += 11
    # a tall mirror, centre right
    d.rectangle((150, 36, 176, 92), fill=(90, 64, 40))
    for yy in range(39, 90):
        d.line((153, yy, 173, yy), fill=lerp((170, 190, 210), (110, 130, 150), (yy - 39) / 51))
    d.line((156, 42, 162, 52), fill=(220, 230, 240))
    d.line((158, 42, 164, 52), fill=(200, 214, 230))
    # dress form wearing a cloak, centre left
    d.line((104, 92, 104, 76), fill=(70, 50, 36))
    d.line((98, 92, 110, 92), fill=(70, 50, 36))
    d.polygon([(96, 48), (112, 48), (116, 78), (92, 78)], fill=(120, 40, 50))
    d.line((97, 49, 93, 77), fill=(150, 60, 70))
    d.ellipse((99, 40, 109, 50), fill=(200, 180, 150))
    d.line((96, 50, 112, 50), fill=(212, 170, 70))
    # playmats rolled in a barrel and a stack of dice in a dish, right
    barrel(d, 196, 92, 14, 12, wood=(110, 76, 50))
    for i, c in enumerate([(40, 90, 150), (150, 48, 48), (46, 120, 72)]):
        d.rectangle((199 + i * 3, 66 + i, 200 + i * 3, 79), fill=c)
    d.rectangle((214, 86, 234, 92), fill=(84, 58, 44))
    for i, c in enumerate([(220, 60, 60), (240, 240, 240), (60, 120, 220), (230, 190, 60)]):
        d.rectangle((216 + i * 4, 83, 218 + i * 4, 85), fill=c)
    # floor with a rug
    floorboards(img, 96, (124, 92, 66), 13)
    d = ImageDraw.Draw(img)
    d.line((0, 96, CW, 96), fill=(60, 44, 34))
    d.rectangle((60, 108, 180, 130), fill=(120, 44, 52))
    d.rectangle((63, 111, 177, 127), outline=(212, 170, 70))
    d.rectangle((70, 116, 170, 122), outline=(80, 30, 40))
    for x in range(60, 181, 4):
        d.point((x, 107), fill=(212, 170, 70))
        d.point((x, 131), fill=(212, 170, 70))
    # counter with a measuring tape and shears, bottom right
    d.rectangle((192, 110, CW, 135), fill=(100, 70, 46))
    d.line((192, 110, CW, 110), fill=(150, 108, 70))
    d.line((200, 108, 230, 108), fill=(230, 210, 120))
    d.line((214, 105, 220, 109), fill=(170, 170, 180))
    d.line((214, 109, 220, 105), fill=(170, 170, 180))
    glow(img, [(120, 30)], 80, (255, 236, 200), 50)
    vignette(img, 0.35)
    return img


def castle():
    r = random.Random(21)
    img = canvas()
    d = ImageDraw.Draw(img)
    stone = (112, 110, 122)
    bricks(img, (0, 0, CW, 98), stone, 31, bw=10, bh=5)
    # vaulted ceiling falling into shadow
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    for y in range(0, 18):
        ld.line((0, y, CW, y), fill=(14, 12, 20, int(200 * (1 - y / 18) ** 1.4)))
    img.alpha_composite(layer)
    d = ImageDraw.Draw(img)
    # tall arched windows with moonlight
    for wx in (28, 196):
        d.rectangle((wx - 1, 26, wx + 17, 80), fill=(64, 62, 72))
        for yy in range(30, 78):
            d.line((wx + 2, yy, wx + 14, yy), fill=lerp((22, 24, 58), (54, 50, 96), (yy - 30) / 48))
        d.ellipse((wx + 2, 22, wx + 14, 38), fill=(22, 24, 58))
        d.arc((wx - 1, 19, wx + 17, 41), 180, 360, fill=(64, 62, 72), width=3)
        d.line((wx + 8, 26, wx + 8, 78), fill=(50, 48, 58))
        d.line((wx + 2, 52, wx + 14, 52), fill=(50, 48, 58))
        d.rectangle((wx - 2, 78, wx + 18, 81), fill=(90, 88, 100))
    d.ellipse((202, 34, 207, 39), fill=(230, 226, 200))  # moon in the right window
    for x, y in ((32, 40), (38, 60), (210, 56), (34, 33)):
        d.point((x, y), fill=(210, 210, 230))
    # banners between the windows
    banner(d, 62, 18, 16, 46, (140, 30, 36))
    banner(d, 162, 18, 16, 46, (140, 30, 36))
    banner(d, 92, 14, 14, 36, (36, 54, 120), emblem="sun")
    banner(d, 134, 14, 14, 36, (36, 54, 120), emblem="sun")
    # dais and throne, centre back
    d.rectangle((96, 84, 144, 98), fill=(96, 94, 106))
    d.rectangle((100, 80, 140, 84), fill=(120, 118, 130))
    d.line((96, 84, 144, 84), fill=(150, 148, 160))
    d.rectangle((112, 52, 128, 80), fill=(100, 70, 40))
    d.rectangle((114, 54, 126, 72), fill=(140, 30, 36))
    d.rectangle((110, 72, 130, 80), fill=(110, 78, 46))
    for x in (112, 120, 127):
        d.point((x, 50), fill=(220, 180, 70))
    d.line((112, 51, 128, 51), fill=(200, 160, 60))
    # torch sconces on the pillars
    for x in (54, 86, 154, 186):
        sconce(d, x, 54)
    # pillars
    for px_ in (0, 82, 150, 226):
        if px_ in (82, 150):
            continue
        d.rectangle((px_, 14, px_ + 13, 98), fill=(124, 122, 134))
        d.line((px_ + 1, 14, px_ + 1, 98), fill=(150, 148, 160))
        d.line((px_ + 12, 14, px_ + 12, 98), fill=(80, 78, 90))
    # flagstone floor with a red carpet running to the throne
    fy = 98
    px = img.load()
    for y in range(fy, CH):
        depth = (y - fy) / (CH - fy)
        th = 3 + int(depth * 5)
        row = (y - fy) // th if th else 0
        for x in range(CW):
            w = 10 + int(depth * 8)
            edge = (y - fy) % th == 0 or (x + row * 5) % w == 0
            c = (70, 68, 80) if edge else mul((118, 116, 128), 0.9 + 0.2 * depth)
            px[x, y] = c + (255,)
    d = ImageDraw.Draw(img)
    d.polygon([(110, 98), (130, 98), (150, CH), (90, CH)], fill=(130, 28, 34))
    d.line((110, 98, 90, CH), fill=(212, 170, 70))
    d.line((130, 98, 150, CH), fill=(212, 170, 70))
    # the tournament tables either side
    for tx in (30, 186):
        d.rectangle((tx, 112, tx + 30, 115), fill=(120, 82, 50))
        d.line((tx, 112, tx + 30, 112), fill=(160, 116, 72))
        d.rectangle((tx + 2, 116, tx + 4, 128), fill=(90, 60, 36))
        d.rectangle((tx + 26, 116, tx + 28, 128), fill=(90, 60, 36))
        for i in range(3):
            d.rectangle((tx + 6 + i * 7, 110, tx + 10 + i * 7, 111), fill=(225, 218, 200))
        candle(d, tx + 27, 111)
    # night: cool moonlight, warm torches
    grade(img, 0.62, 0.62, 0.8, 4)
    glow(img, [(36, 52), (204, 52)], 30, (150, 170, 255), 60)
    glow(img, [(54, 52), (86, 52), (154, 52), (186, 52)], 20, (255, 170, 80), 140)
    glow(img, [(120, 70)], 40, (255, 200, 130), 60)
    glow(img, [(57, 111), (213, 111)], 12, (255, 190, 110), 90)
    vignette(img, 0.55)
    return img


def opening_table():
    """Seen from above: wide oak boards under a lamp, a candle and a few loose items at the rims."""
    r = random.Random(17)
    img = canvas()
    d = ImageDraw.Draw(img)
    # boards run across the table
    y, i = 0, 0
    base = (120, 82, 52)
    px = img.load()
    while y < CH:
        h = 11 + r.randint(-1, 1)
        tone = mul(base, r.choice([0.9, 0.95, 1.0, 1.05]))
        joint = r.randrange(40, 200)
        for yy in range(y, min(CH, y + h)):
            for x in range(CW):
                c = tone
                if yy == y:
                    c = mul(base, 0.55)
                elif x == joint and yy > y:
                    c = mul(base, 0.6)
                elif ((x * 3 + yy * 17 + i * 31) % 23 == 0) or (yy - y == h // 2 and (x // 7 + i) % 5 == 0):
                    c = mul(tone, 0.86)  # grain
                px[x, yy] = c + (255,)
        y += h
        i += 1
    d = ImageDraw.Draw(img)
    # knots
    for _ in range(5):
        kx, ky = r.randrange(10, CW - 10), r.randrange(10, CH - 10)
        d.ellipse((kx - 2, ky - 1, kx + 2, ky + 1), fill=mul(base, 0.6))
    # a playmat in the middle where the packs are opened: dark cloth, stitched edge, faint mana star
    mx0, my0, mx1, my1 = 36, 18, 204, 118
    d.rounded_rectangle((mx0 + 2, my0 + 2, mx1 + 2, my1 + 2), radius=5, fill=(40, 26, 18))   # shadow
    d.rounded_rectangle((mx0, my0, mx1, my1), radius=5, fill=(34, 52, 62))
    d.rounded_rectangle((mx0 + 3, my0 + 3, mx1 - 3, my1 - 3), radius=4, outline=(70, 96, 108))
    for x in range(mx0 + 6, mx1 - 5, 3):
        d.point((x, my0 + 3), fill=(150, 130, 80))
        d.point((x, my1 - 3), fill=(150, 130, 80))
    for y in range(my0 + 6, my1 - 5, 3):
        d.point((mx0 + 3, y), fill=(150, 130, 80))
        d.point((mx1 - 3, y), fill=(150, 130, 80))
    import math
    cx, cy = (mx0 + mx1) // 2, (my0 + my1) // 2
    star = [(cx + (30 if i % 2 == 0 else 12) * math.cos(-math.pi / 2 + i * math.pi / 5),
             cy + (30 if i % 2 == 0 else 12) * math.sin(-math.pi / 2 + i * math.pi / 5)) for i in range(10)]
    d.polygon(star, outline=(46, 68, 80))
    d.ellipse((cx - 36, cy - 36, cx + 36, cy + 36), outline=(44, 64, 76))
    # at the rims, out of the way of the cards: candle, coins, an empty wrapper, dice
    # candle in a brass dish, seen from above: dish, wax, wick flame
    d.ellipse((9, 9, 23, 23), fill=(150, 112, 50), outline=(100, 72, 30))
    d.ellipse((12, 12, 20, 20), fill=(214, 202, 176))
    d.ellipse((14, 14, 18, 18), fill=(236, 228, 206))
    d.point((16, 16), fill=(60, 40, 30))
    d.point((16, 15), fill=(255, 200, 90))
    for cx, cy in ((222, 18), (226, 21), (219, 22), (224, 15)):
        d.ellipse((cx - 2, cy - 2, cx + 2, cy + 2), fill=(214, 172, 60), outline=(150, 112, 30))
    d.polygon([(206, 112), (226, 106), (230, 118), (212, 126)], fill=(90, 60, 130))
    d.line((206, 112, 226, 106), fill=(200, 200, 214))
    d.rectangle((18, 116, 21, 119), fill=(230, 60, 60))
    d.rectangle((24, 118, 27, 121), fill=(240, 240, 240))
    d.point((19, 117), fill=(255, 255, 255))
    d.point((25, 119), fill=(30, 30, 30))
    # lamp light from above: bright centre, dark rim
    grade(img, 0.72, 0.66, 0.62)
    glow(img, [(120, 70)], 140, (255, 200, 130), 120, 0.6)
    glow(img, [(16, 16)], 26, (255, 200, 120), 110)
    vignette(img, 0.5)
    return img


# ---------------------------------------------------------------- dungeon map pieces

def slab(lit):
    """A raised stone slab for a map room (26x14 px -> 104x56 at 4x), optional gold rim."""
    w, h = 26, 14
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    side, top, hi, lo = (58, 56, 64), (112, 108, 118), (146, 142, 150), (84, 80, 90)
    d.ellipse((0, 4, w - 1, h - 1), fill=(20, 18, 24))            # shadow/base
    d.ellipse((0, 3, w - 1, h - 2), fill=side)                      # side face
    d.ellipse((0, 0, w - 1, h - 5), fill=top)                       # top face
    px = img.load()
    r = random.Random(3)
    for y in range(h):
        for x in range(w):
            p = px[x, y]
            if p[3] and p[:3] == top and r.random() < 0.14:
                px[x, y] = lo + (255,)
    d.arc((0, 0, w - 1, h - 5), 190, 350, fill=hi)                  # lit upper rim
    d.line((7, 4, 12, 6), fill=lo)                                  # cracks
    d.line((12, 6, 14, 5), fill=lo)
    d.line((17, 3, 19, 6), fill=lo)
    if lit:
        d.arc((0, 0, w - 1, h - 5), 0, 360, fill=(255, 196, 80))
        d.arc((1, 1, w - 2, h - 6), 200, 340, fill=(255, 230, 150))
    return img.resize((w * 4, h * 4), Image.NEAREST)


def flagstone(gold):
    """One stepping stone of a trail (3x3 px -> 12x12)."""
    img = Image.new("RGBA", (3, 3), (0, 0, 0, 0))
    px = img.load()
    if gold:
        c, hi, lo = (232, 180, 70), (255, 226, 140), (150, 108, 40)
    else:
        c, hi, lo = (128, 122, 132), (164, 158, 168), (70, 66, 76)
    for (x, y), col in {(1, 0): hi, (0, 1): hi, (1, 1): c, (2, 1): c, (1, 2): lo, (0, 0): None,
                        (2, 0): c, (0, 2): c, (2, 2): lo}.items():
        if col:
            px[x, y] = col + (255,)
    return img.resize((12, 12), Image.NEAREST)


# ---------------------------------------------------------------- playmats

MAT_W, MAT_H = 240, 80   # 3:1, stretched over your side of the battlefield; 4x -> 960x320

MATS = {
    # id: (cloth, darker cloth, stitch/trim, emblem colour, emblem)
    "forest": ((34, 70, 44), (26, 56, 36), (120, 150, 90), (48, 92, 58), "leaf"),
    "midnight": ((24, 30, 62), (18, 22, 48), (150, 150, 190), (40, 48, 92), "moon"),
    "ember": ((70, 30, 24), (54, 22, 18), (200, 120, 60), (96, 44, 30), "flame"),
    "knight": ((40, 58, 96), (30, 44, 76), (190, 196, 210), (62, 84, 130), "shield"),
    "baron": ((84, 26, 34), (66, 20, 26), (212, 170, 70), (112, 40, 48), "tower"),
    "duke": ((58, 32, 88), (44, 24, 68), (230, 190, 80), (84, 50, 120), "crown"),
}


def playmat(mat_id):
    """A cloth playmat: woven texture, darker rim, stitched edge, a quiet emblem (cards sit on top)."""
    cloth, dark, trim, mark, emblem = MATS[mat_id]
    r = random.Random(sum(map(ord, mat_id)))
    img = Image.new("RGBA", (MAT_W, MAT_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, MAT_W - 1, MAT_H - 1), radius=6, fill=cloth)
    px = img.load()
    for y in range(MAT_H):
        for x in range(MAT_W):
            if px[x, y][3] == 0:
                continue
            # woven cloth: a faint diagonal twill plus a darker rim
            edge = min(x, y, MAT_W - 1 - x, MAT_H - 1 - y)
            c = lerp(dark, cloth, min(1, edge / 10))
            if (x + y * 2) % 5 == 0 or r.random() < 0.04:
                c = mul(c, 0.92)
            px[x, y] = c + (255,)
    # stitched border
    for x in range(8, MAT_W - 8, 3):
        d.point((x, 4), fill=trim)
        d.point((x, MAT_H - 5), fill=trim)
    for y in range(8, MAT_H - 8, 3):
        d.point((4, y), fill=trim)
        d.point((MAT_W - 5, y), fill=trim)
    if mat_id in ("baron", "duke", "knight"):  # title mats: a solid inner trim line too
        d.rounded_rectangle((8, 8, MAT_W - 9, MAT_H - 9), radius=4, outline=mul(trim, 0.7))
    if mat_id == "duke":
        d.rounded_rectangle((11, 11, MAT_W - 12, MAT_H - 12), radius=3, outline=mul(trim, 0.5))
    # the emblem, centred and low-contrast so cards on top stay readable
    cx, cy = MAT_W // 2, MAT_H // 2
    if emblem == "leaf":
        d.ellipse((cx - 14, cy - 8, cx + 14, cy + 8), outline=mark, width=2)
        d.line((cx - 14, cy, cx + 14, cy), fill=mark)
        for i in range(-10, 11, 5):
            d.line((cx + i, cy, cx + i + 3, cy - 5), fill=mark)
            d.line((cx + i, cy, cx + i + 3, cy + 5), fill=mark)
    elif emblem == "moon":
        d.ellipse((cx - 13, cy - 13, cx + 13, cy + 13), fill=mark)
        d.ellipse((cx - 7, cy - 15, cx + 17, cy + 9), fill=cloth)
        for _ in range(40):
            x, y = r.randrange(12, MAT_W - 12), r.randrange(10, MAT_H - 10)
            if abs(x - cx) > 20:
                d.point((x, y), fill=mul(trim, r.choice([0.45, 0.6, 0.8])))
    elif emblem == "flame":
        d.polygon([(cx, cy - 16), (cx + 10, cy + 2), (cx + 6, cy + 12), (cx - 6, cy + 12), (cx - 10, cy + 2)], fill=mark)
        d.polygon([(cx, cy - 6), (cx + 5, cy + 4), (cx, cy + 10), (cx - 5, cy + 4)], fill=mul(mark, 1.25))
        for _ in range(30):
            x, y = r.randrange(12, MAT_W - 12), r.randrange(10, MAT_H - 10)
            if abs(x - cx) > 18:
                d.point((x, y), fill=mul(trim, r.choice([0.4, 0.55])))
    elif emblem == "shield":
        d.polygon([(cx - 12, cy - 14), (cx + 12, cy - 14), (cx + 12, cy + 2), (cx, cy + 15), (cx - 12, cy + 2)], outline=mark, fill=mul(cloth, 1.1))
        d.line((cx, cy - 12, cx, cy + 11), fill=mark, width=3)
        d.line((cx - 10, cy - 4, cx + 10, cy - 4), fill=mark, width=3)
    elif emblem == "tower":
        d.rectangle((cx - 8, cy - 8, cx + 8, cy + 14), fill=mark)
        for i in (-8, -2, 4):
            d.rectangle((cx + i, cy - 13, cx + i + 3, cy - 9), fill=mark)
        d.rectangle((cx - 2, cy + 6, cx + 2, cy + 14), fill=cloth)
    elif emblem == "crown":
        d.polygon([(cx - 16, cy + 8), (cx - 16, cy - 8), (cx - 8, cy), (cx, cy - 14), (cx + 8, cy), (cx + 16, cy - 8), (cx + 16, cy + 8)], fill=mark)
        d.rectangle((cx - 16, cy + 8, cx + 16, cy + 12), fill=mul(mark, 1.2))
        for x in (cx - 16, cx, cx + 16):
            d.point((x, cy - 9 if x != cx else cy - 15), fill=trim)
    return img.resize((MAT_W * 4, MAT_H * 4), Image.NEAREST)


# ---------------------------------------------------------------- main

def build():
    return {
        "tavern_bg.png": finish(tavern()),
        "shop_bg.png": finish(shop()),
        "outfitter_bg.png": finish(outfitter()),
        "castle_bg.png": finish(castle()),
        "opening_table.png": finish(opening_table()),
        "plate.png": slab(False),
        "plate_glow.png": slab(True),
        "dot.png": flagstone(False),
        "dot_gold.png": flagstone(True),
        **{f"playmat_{m}.png": playmat(m) for m in MATS},
    }


def main():
    out = OUT
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
    os.makedirs(out, exist_ok=True)
    for name, im in build().items():
        im.save(os.path.join(out, name))
        print(name, im.size)


if __name__ == "__main__":
    main()
