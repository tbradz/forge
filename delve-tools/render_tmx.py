"""Render a Tiled .tmx map from Forge Adventure to a PNG (tile layers + tile objects).

Used to build Delve backdrops from the hand-made Adventure maps so the town and
dungeon look like they belong to the same world.

    python3 delve-tools/render_tmx.py MAP.tmx OUT.png [--layers Background,Ground,...] [--skip AboveSprites]
"""
import base64
import os
import sys
import zlib
import struct
import xml.etree.ElementTree as ET
from PIL import Image

FLIP_H, FLIP_V, FLIP_D = 0x80000000, 0x40000000, 0x20000000

_cache = {}


def load_tileset(path):
    if path in _cache:
        return _cache[path]
    root = ET.parse(path).getroot()
    tw, th = int(root.get("tilewidth")), int(root.get("tileheight"))
    cols = int(root.get("columns") or 0)
    img_el = root.find("image")
    ts = {"tw": tw, "th": th, "cols": cols, "img": None, "tiles": {}}
    base = os.path.dirname(path)
    if img_el is not None:
        ts["img"] = Image.open(os.path.join(base, img_el.get("source"))).convert("RGBA")
        if not cols:
            ts["cols"] = ts["img"].width // tw
    for t in root.findall("tile"):  # image-collection tilesets
        im = t.find("image")
        if im is not None:
            ts["tiles"][int(t.get("id"))] = Image.open(os.path.join(base, im.get("source"))).convert("RGBA")
    _cache[path] = ts
    return ts


def tile_image(tilesets, gid):
    raw = gid & ~(FLIP_H | FLIP_V | FLIP_D)
    if raw == 0:
        return None
    first, ts = None, None
    for fg, t in tilesets:
        if fg <= raw:
            first, ts = fg, t
    if ts is None:
        return None
    local = raw - first
    if local in ts["tiles"]:
        im = ts["tiles"][local]
    elif ts["img"] is not None:
        cx, cy = local % ts["cols"], local // ts["cols"]
        im = ts["img"].crop((cx * ts["tw"], cy * ts["th"], (cx + 1) * ts["tw"], (cy + 1) * ts["th"]))
    else:
        return None
    if gid & FLIP_D:
        im = im.transpose(Image.TRANSPOSE)
    if gid & FLIP_H:
        im = im.transpose(Image.FLIP_LEFT_RIGHT)
    if gid & FLIP_V:
        im = im.transpose(Image.FLIP_TOP_BOTTOM)
    return im


def layer_gids(layer, w, h):
    data = layer.find("data")
    enc, comp = data.get("encoding"), data.get("compression")
    if enc == "csv":
        return [int(v) for v in data.text.replace("\n", "").split(",") if v.strip()]
    raw = base64.b64decode(data.text.strip())
    if comp == "zlib" or comp == "gzip":
        raw = zlib.decompress(raw, 15 + 32)
    return list(struct.unpack("<%dI" % (w * h), raw))


def render(tmx, skip=(), only=None):
    root = ET.parse(tmx).getroot()
    w, h = int(root.get("width")), int(root.get("height"))
    tw, th = int(root.get("tilewidth")), int(root.get("tileheight"))
    base = os.path.dirname(tmx)
    tilesets = []
    for t in root.findall("tileset"):
        tilesets.append((int(t.get("firstgid")), load_tileset(os.path.normpath(os.path.join(base, t.get("source"))))))
    tilesets.sort(key=lambda x: x[0])
    out = Image.new("RGBA", (w * tw, h * th), (0, 0, 0, 255))

    def walk(el):
        for child in el:
            if child.tag == "group":
                if child.get("visible") == "0":
                    continue
                walk(child)
            elif child.tag == "layer":
                name = child.get("name")
                if child.get("visible") == "0" or name in skip or (only and name not in only):
                    continue
                gids = layer_gids(child, w, h)
                for i, gid in enumerate(gids):
                    im = tile_image(tilesets, gid)
                    if im is None:
                        continue
                    x, y = (i % w) * tw, (i // w) * th
                    out.alpha_composite(im, (x, y + th - im.height))
            elif child.tag == "objectgroup":
                if child.get("visible") == "0" or child.get("name") in skip:
                    continue
                for o in child.findall("object"):
                    gid = o.get("gid")
                    if not gid or o.get("visible") == "0":
                        continue
                    im = tile_image(tilesets, int(gid))
                    if im is None:
                        continue
                    ow, oh = float(o.get("width", im.width)), float(o.get("height", im.height))
                    if (int(ow), int(oh)) != im.size and ow > 0 and oh > 0:
                        im = im.resize((int(ow), int(oh)), Image.NEAREST)
                    x, y = float(o.get("x")), float(o.get("y"))
                    out.alpha_composite(im, (int(x), int(y - im.height)))

    walk(root)
    return out


if __name__ == "__main__":
    args = sys.argv[1:]
    skip = []
    if "--skip" in args:
        i = args.index("--skip")
        skip = args[i + 1].split(",")
        del args[i:i + 2]
    render(args[0], skip=skip).save(args[1])
