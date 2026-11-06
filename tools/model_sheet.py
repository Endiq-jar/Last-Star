#!/usr/bin/env python3
"""Renders a contact sheet of the baked model set.

The models are already in the repository as a single binary (assets/models/
world.bin), so a reviewer can see what was imported without opening the game.
Each tile is a small isometric render: faces are flat-shaded from their own
vertex colour with a fixed key light, and the whole tile is then drawn with the
project's palette so the sheet matches the game's look.

    tools/model_sheet.py docs/ui/models.png [columns] [tile]

Requires pillow (pip install pillow).
"""

import struct
import sys

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("model_sheet.py needs pillow: pip install pillow")

MAGIC = 0x4D4F444C
VERSION = 2

# Same roles as model_manifest.py / ModelLibrary.java.
ROLE_NAMES = [
    "TREE", "ROCK", "PLANT", "GRASS", "FLOWER", "MUSHROOM", "LOG", "STUMP",
    "CLIFF", "HILL", "HOUSE", "TOWER", "WALL", "GATE", "TENT", "FIRE",
    "BARREL", "CRATE", "CHEST", "BANNER", "ROOF", "PROP", "FENCE",
]

BG = (16, 20, 30)
PANEL = (26, 31, 42)
INK = (232, 217, 181)
DIM = (139, 132, 112)
ACCENT = (201, 162, 39)


def read_models(path):
    with open(path, "rb") as fh:
        blob = fh.read()
    magic, version, count, _reserved = struct.unpack_from("<IIII", blob, 0)
    if magic != MAGIC or version != VERSION:
        raise SystemExit("not a v%d model file: %s" % (VERSION, path))
    off = 16
    out = []
    for _ in range(count):
        (name_len,) = struct.unpack_from("<I", blob, off)
        off += 4
        name = blob[off:off + name_len].decode("ascii").rstrip("\0")
        # Names are padded to a four-byte boundary so the header stays aligned.
        off += (name_len + 3) & ~3
        vcount, icount, role, _r = struct.unpack_from("<IIII", blob, off)
        off += 16
        (target,) = struct.unpack_from("<f", blob, off)
        off += 4
        bounds = struct.unpack_from("<6f", blob, off)
        off += 24
        verts = []
        for _i in range(vcount):
            px, py, pz, nx, ny, nz, cr, cg, cb = struct.unpack_from("<9f", blob, off)
            off += 36
            verts.append(((px, py, pz), (nx, ny, nz), (cr, cg, cb)))
        indices = struct.unpack_from("<%dH" % icount, blob, off)
        off += icount * 2
        out.append({
            "name": name, "role": role, "target": target, "bounds": bounds,
            "verts": verts, "indices": indices,
        })
    return out


def shade(colour, normal):
    """Lambert with a fixed key light, plus a little sky fill."""
    nx, ny, nz = normal
    ln = (nx * nx + ny * ny + nz * nz) ** 0.5 or 1.0
    nx, ny, nz = nx / ln, ny / ln, nz / ln
    key = max(0.0, nx * -0.42 + ny * 0.80 + nz * 0.43)
    fill = 0.5 + 0.5 * ny
    k = 0.42 + 0.62 * key + 0.16 * fill
    return tuple(min(255, max(0, int(c * 255.0 * k))) for c in colour)


def render_tile(model, size, ss=3):
    """Isometric render of one model into an RGB image."""
    w = h = size * ss
    img = Image.new("RGB", (w, h), PANEL)
    draw = ImageDraw.Draw(img)

    minx, miny, minz, maxx, maxy, maxz = model["bounds"]
    cx = (minx + maxx) * 0.5
    cz = (minz + maxz) * 0.5
    span = max(maxx - minx, maxy - miny, maxz - minz) or 1.0

    # Isometric basis: yaw 40 degrees, pitch 28.
    import math
    ya, pa = math.radians(40.0), math.radians(28.0)
    cy_, sy_ = math.cos(ya), math.sin(ya)
    cp, sp = math.cos(pa), math.sin(pa)

    def project(p):
        x, y, z = p[0] - cx, p[1] - miny, p[2] - cz
        xr = x * cy_ - z * sy_
        zr = x * sy_ + z * cy_
        yr = y * cp - zr * sp
        return xr, -yr

    scale = (w * 0.74) / span
    mid = (miny + maxy) * 0.5

    def screen(p):
        sx, sy = project(p)
        return (w * 0.5 + sx * scale, h * 0.55 + (sy + (mid - miny) * cp) * scale)

    tris = []
    idx = model["indices"]
    verts = model["verts"]
    for i in range(0, len(idx) - 2, 3):
        a, b, c = idx[i], idx[i + 1], idx[i + 2]
        pa_, pb, pc = verts[a], verts[b], verts[c]
        n = pa_[1]
        depth = (pa_[0][0] + pb[0][0] + pc[0][0]) * 0.17 \
            + (pa_[0][2] + pb[0][2] + pc[0][2]) * 0.29
        tris.append((depth, pa_, pb, pc, n))
    tris.sort(key=lambda t: t[0])

    for _d, pa_, pb, pc, n in tris:
        pts = [screen(pa_[0]), screen(pb[0]), screen(pc[0])]
        colour = shade(pa_[2], n)
        draw.polygon(pts, fill=colour, outline=colour)

    return img.resize((size, size), Image.LANCZOS)


def main():
    if len(sys.argv) < 2:
        sys.exit("usage: model_sheet.py <out.png> [columns] [tile]")
    out = sys.argv[1]
    cols = int(sys.argv[2]) if len(sys.argv) > 2 else 7
    tile = int(sys.argv[3]) if len(sys.argv) > 3 else 150

    models = read_models("app/src/main/assets/models/world.bin")
    rows = (len(models) + cols - 1) // cols
    pad = 10
    label = 18
    width = cols * (tile + pad) + pad
    height = rows * (tile + pad + label) + pad
    sheet = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.load_default()
    except Exception:
        font = None

    total_tris = 0
    for i, model in enumerate(models):
        col = i % cols
        row = i // cols
        x = pad + col * (tile + pad)
        y = pad + row * (tile + pad + label)
        sheet.paste(render_tile(model, tile), (x, y))
        total_tris += len(model["indices"]) // 3
        name = model["name"]
        if len(name) > 17:
            name = name[:16] + "~"
        if font:
            draw.text((x + 1, y + tile + 3), name, fill=INK, font=font)
            draw.text((x + 1, y + tile + 3 + 8), ROLE_NAMES[model["role"]]
                      if model["role"] < len(ROLE_NAMES) else "?", fill=DIM, font=font)

    sheet.save(out)
    print("wrote %s -- %d models, %d triangles" % (out, len(models), total_tris))


if __name__ == "__main__":
    main()
