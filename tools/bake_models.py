#!/usr/bin/env python3
"""
Bakes glTF-binary (.glb) models into the flat vertex buffer the Android build
reads at start-up.

Why a custom bake instead of a runtime GLB parser? The APK has no JSON parser,
no image decoder and a ~100 ms start-up budget. The baker resolves every node
transform, material colour and index buffer ahead of time and writes one
contiguous block, so at runtime a model is a single little-endian sweep with no
allocation per triangle.

Two kinds of source model exist in the Kenney kits imported here:

  * flat-coloured kits (nature) -- one `baseColorFactor` per material;
  * atlas kits (castle, survival, fantasy-town) -- every primitive samples a
    single `Textures/colormap.png` atlas. There is no image decoder on the
    device, so the baker *bakes the texture into the vertices*: it samples the
    atlas at each vertex's UV. Kenney's atlases are flat swatch grids with a
    little painted grain, so per-vertex sampling keeps the look, drops the
    texture entirely, and the result stays in the same vertex-colour pipeline
    the rest of the renderer uses.

Colours are art-directed to the game's anime palette (see PALETTE / anime())
and the per-model header carries the role and target size so the placement code
needs no name -> meaning table.

Output format (`assets/models/<set>.bin`, little-endian):

    u32   magic     0x4D4F444C  ("LDMOD")
    u32   version   2
    u32   modelCount
    u32   reserved
    per model:
        u32   nameLength        followed by nameLength ASCII bytes (padded to 4)
        u32   vertexCount
        u32   indexCount
        u32   role              ModelLibrary role id
        u32   reserved
        f32   targetSize        metres, largest axis, as authored in the manifest
        f32   minX minY minZ maxX maxY maxZ
        vertexCount * (px py pz nx ny nz r g b)    9 floats
        indexCount  * uint16

Usage:
    tools/bake_models.py --manifest <srcDir> <outDir> <setName>
    tools/bake_models.py <srcDir> <outDir> <setName> [name ...]
"""

import json
import math
import os
import struct
import sys

MAGIC = 0x4D4F444C
VERSION = 2

HERE = os.path.dirname(os.path.abspath(__file__))


# --------------------------------------------------------------------- glb io

def read_glb(path):
    data = open(path, 'rb').read()
    magic, version, length = struct.unpack('<III', data[:12])
    if magic != 0x46546C67:
        raise ValueError('%s: not a GLB' % path)
    off = 12
    js = None
    bin_chunk = b''
    while off < length:
        clen, ctype = struct.unpack('<II', data[off:off + 8])
        payload = data[off + 8:off + 8 + clen]
        if ctype == 0x4E4F534A:
            js = json.loads(payload)
        elif ctype == 0x004E4942:
            bin_chunk = payload
        off += 8 + clen
    if js is None:
        raise ValueError('%s: no JSON chunk' % path)
    return js, bin_chunk


COMP = {5120: ('b', 1), 5121: ('B', 1), 5122: ('h', 2), 5123: ('H', 2),
        5125: ('I', 4), 5126: ('f', 4)}
NCOMP = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4, 'MAT4': 16}


def read_accessor(js, bin_chunk, index):
    acc = js['accessors'][index]
    view = js['bufferViews'][acc['bufferView']]
    fmt, size = COMP[acc['componentType']]
    n = NCOMP[acc['type']]
    base = view.get('byteOffset', 0) + acc.get('byteOffset', 0)
    stride = view.get('byteStride') or size * n
    out = []
    for i in range(acc['count']):
        out.append(struct.unpack_from('<' + fmt * n, bin_chunk, base + i * stride))
    return out


# ------------------------------------------------------------ png (stdlib only)

def read_png(path):
    """Decodes an 8-bit PNG to (width, height, rows of (r, g, b) tuples)."""
    import zlib
    d = open(path, 'rb').read()
    if d[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError('%s: not a PNG' % path)
    off = 8
    idat = b''
    w = h = bd = ct = None
    pal = None
    while off < len(d):
        ln = struct.unpack('>I', d[off:off + 4])[0]
        typ = d[off + 4:off + 8]
        payload = d[off + 8:off + 8 + ln]
        off += 12 + ln
        if typ == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', payload[:10])
        elif typ == b'PLTE':
            pal = payload
        elif typ == b'IDAT':
            idat += payload
        elif typ == b'IEND':
            break
    if bd != 8:
        raise ValueError('%s: only 8-bit PNGs are supported' % path)
    raw = zlib.decompress(idat)
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ct]
    stride = w * bpp
    rows = []
    prev = bytearray(stride)
    p = 0
    for _ in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pp = a + b - c
                pa, pb, pc = abs(pp - a), abs(pp - b), abs(pp - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        rows.append(line)
        prev = line

    out = []
    for y in range(h):
        line = rows[y]
        row = []
        for x in range(w):
            o = x * bpp
            if ct == 3:
                i = line[o]
                row.append((pal[i * 3], pal[i * 3 + 1], pal[i * 3 + 2]))
            elif ct in (2, 6):
                row.append((line[o], line[o + 1], line[o + 2]))
            else:
                row.append((line[o], line[o], line[o]))
        out.append(row)
    return w, h, out


class Atlas(object):
    """Nearest-neighbour sampler for a Kenney colormap atlas."""

    def __init__(self, path):
        self.w, self.h, self.rows = read_png(path)
        self.swatches = self._swatches()

    def _swatches(self):
        """
        The atlas's flat swatch colours. Kenney paints a little vertical grain
        into timber and stone swatches; snapping each sampled colour to the
        nearest swatch removes that grain without losing the hue, which keeps
        the vertices clean and lets the renderer tint a material per instance.
        """
        counts = {}
        for row in self.rows:
            for rgb in row:
                if rgb == (0, 0, 0):
                    continue
                counts[rgb] = counts.get(rgb, 0) + 1
        return [rgb for rgb, n in counts.items() if n >= 300]

    def sample(self, u, v):
        x = int(u * self.w)
        y = int(v * self.h)
        x = 0 if x < 0 else (self.w - 1 if x >= self.w else x)
        y = 0 if y < 0 else (self.h - 1 if y >= self.h else y)
        rgb = self.rows[y][x]
        if not self.swatches:
            return rgb
        best, bestd = self.swatches[0], 1e9
        for s in self.swatches:
            d = (s[0] - rgb[0]) ** 2 + (s[1] - rgb[1]) ** 2 + (s[2] - rgb[2]) ** 2
            if d < bestd:
                bestd, best = d, s
        return best


# ------------------------------------------------------- anime palette remap

def _hex(s):
    return (int(s[1:3], 16) / 255.0, int(s[3:5], 16) / 255.0, int(s[5:7], 16) / 255.0)


# Kenney's generic material names say *what* a surface is; these say what it
# looks like here. Reference mood: Genshin's Mondstadt/Liyue outdoors --
# warm sunlit timber, pale sandstone masonry, teal-green foliage with a blue
# shadow side, cool grey-violet rock.
PALETTE = {
    'grass':       _hex('#5FBF6A'),
    'leafsGreen':  _hex('#49B36B'),
    'leafsDark':   _hex('#2E8C63'),
    'colorPurple': _hex('#9B7BE0'),
    'colorRed':    _hex('#E0574F'),
    'colorYellow': _hex('#F2B84B'),
    'dirt':        _hex('#C08A5E'),
    'dirtDark':    _hex('#9C6B47'),
    'wood':        _hex('#C07244'),
    'woodBark':    _hex('#A2653F'),
    'woodBarkDark': _hex('#7E4E33'),
    'woodDark':    _hex('#8A5533'),
    'woodInner':   _hex('#E8C79B'),
    'stone':       _hex('#C2CBDA'),
    'stoneDark':   _hex('#8E97B4'),
    'water':       _hex('#8FE0F0'),
    '_defaultMat': _hex('#D8DEEA'),
}


def _srgb_to_linear(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _linear_to_srgb(c):
    c = 0.0 if c < 0.0 else (1.0 if c > 1.0 else c)
    return c * 12.92 if c <= 0.0031308 else 1.055 * (c ** (1.0 / 2.4)) - 0.055


def anime(colour, sat=1.14, lift=0.05, cool=0.05):
    """
    Pushes a baked vertex colour towards the game's anime palette: clean
    saturation, a cool shadow ramp (shadows go teal/blue, never grey) and
    high-key mid-tones. Applied on top of PALETTE, which does the heavy lifting
    for Kenney's generic material names; for atlas swatches this is the only
    colour pass.
    """
    r, g, b = colour
    lr, lg, lb = _srgb_to_linear(r), _srgb_to_linear(g), _srgb_to_linear(b)
    lum = 0.2126 * lr + 0.7152 * lg + 0.0722 * lb

    lr = lum + (lr - lum) * sat
    lg = lum + (lg - lum) * sat
    lb = lum + (lb - lum) * sat

    shadow = max(0.0, 1.0 - lum * 2.2)
    lr = lr * (1.0 - 0.55 * cool * shadow)
    lb = lb + cool * shadow
    lg = lg + cool * 0.22 * shadow

    lr = lr + (1.0 - lr) * lift * (1.0 - lum)
    lg = lg + (1.0 - lg) * lift * (1.0 - lum)
    lb = lb + (1.0 - lb) * lift * (1.0 - lum)

    return (_linear_to_srgb(lr), _linear_to_srgb(lg), _linear_to_srgb(lb))


# ------------------------------------------------------------- scene walking

def mat_mul(a, b):
    """Column-major 4x4 multiply, glTF convention (a then b applied)."""
    out = [0.0] * 16
    for c in range(4):
        for r in range(4):
            s = 0.0
            for k in range(4):
                s += a[k * 4 + r] * b[c * 4 + k]
            out[c * 4 + r] = s
    return out


def trs_matrix(node):
    if 'matrix' in node:
        return list(node['matrix'])
    t = node.get('translation', [0, 0, 0])
    r = node.get('rotation', [0, 0, 0, 1])
    s = node.get('scale', [1, 1, 1])
    x, y, z, w = r
    xx, yy, zz = x * x, y * y, z * z
    xy, xz, yz = x * y, x * z, y * z
    wx, wy, wz = w * x, w * y, w * z
    return [
        (1 - 2 * (yy + zz)) * s[0], (2 * (xy + wz)) * s[0], (2 * (xz - wy)) * s[0], 0.0,
        (2 * (xy - wz)) * s[1], (1 - 2 * (xx + zz)) * s[1], (2 * (yz + wx)) * s[1], 0.0,
        (2 * (xz + wy)) * s[2], (2 * (yz - wx)) * s[2], (1 - 2 * (xx + yy)) * s[2], 0.0,
        t[0], t[1], t[2], 1.0,
    ]


def transform_point(m, p):
    x, y, z = p
    return (m[0] * x + m[4] * y + m[8] * z + m[12],
            m[1] * x + m[5] * y + m[9] * z + m[13],
            m[2] * x + m[6] * y + m[10] * z + m[14])


def transform_dir(m, p):
    x, y, z = p
    return (m[0] * x + m[4] * y + m[8] * z,
            m[1] * x + m[5] * y + m[9] * z,
            m[2] * x + m[6] * y + m[10] * z)


def texture_uv(mat, uv):
    """Applies KHR_texture_transform so an atlas sub-rect samples correctly."""
    tr = (mat.get('pbrMetallicRoughness', {}).get('baseColorTexture', {})
          .get('extensions', {}).get('KHR_texture_transform'))
    if not tr:
        return uv
    u, v = uv
    su, sv = tr.get('scale', [1, 1])
    ou, ov = tr.get('offset', [0, 0])
    return (u * su + ou, v * sv + ov)


def flatten(js, bin_chunk, atlas):
    """Walks the scene graph and returns [(verts, indices)]."""
    out = []
    scene = js.get('scenes', [{}])[js.get('scene', 0)]
    nodes = js['nodes']

    def walk(index, parent):
        node = nodes[index]
        world = mat_mul(parent, trs_matrix(node))
        if 'mesh' in node:
            for prim in js['meshes'][node['mesh']]['primitives']:
                if prim.get('mode', 4) != 4:
                    continue
                attrs = prim['attributes']
                pos = read_accessor(js, bin_chunk, attrs['POSITION'])
                nrm = (read_accessor(js, bin_chunk, attrs['NORMAL'])
                       if 'NORMAL' in attrs else None)
                uv = (read_accessor(js, bin_chunk, attrs['TEXCOORD_0'])
                      if 'TEXCOORD_0' in attrs else None)
                if 'indices' in prim:
                    flat_idx = [v[0] for v in read_accessor(js, bin_chunk, prim['indices'])]
                else:
                    flat_idx = list(range(len(pos)))
                mat = js['materials'][prim['material']] if 'material' in prim else {}
                pbr = mat.get('pbrMetallicRoughness', {})
                base = pbr.get('baseColorFactor', [0.8, 0.8, 0.8, 1.0])
                textured = 'baseColorTexture' in pbr and atlas is not None
                if not textured:
                    colour = tuple(PALETTE.get(mat.get('name', ''), base)[:3])

                verts = []
                for i, p in enumerate(pos):
                    wp = transform_point(world, p)
                    wn = transform_dir(world, nrm[i]) if nrm is not None else (0.0, 1.0, 0.0)
                    ln = math.sqrt(wn[0] ** 2 + wn[1] ** 2 + wn[2] ** 2) or 1.0
                    if textured:
                        r, g, b = atlas.sample(*texture_uv(mat, uv[i] if uv else (0.0, 0.0)))
                        c = anime((r / 255.0, g / 255.0, b / 255.0))
                    else:
                        c = anime(colour)
                    verts.append((wp, (wn[0] / ln, wn[1] / ln, wn[2] / ln), c))
                out.append((verts, flat_idx))
        for child in node.get('children', []):
            walk(child, world)

    identity = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
    for root in scene.get('nodes', []):
        walk(root, identity)
    return out


# --------------------------------------------------------------------- baking

def bake(src, name, role, target_size, atlas):
    js, bin_chunk = read_glb(src)
    parts = flatten(js, bin_chunk, atlas)
    if not parts:
        raise ValueError('%s: no triangles' % src)

    positions, normals, colours, indices = [], [], [], []
    base = 0
    for verts, idx in parts:
        for (p, n, c) in verts:
            positions.append(p)
            normals.append(n)
            colours.append(c)
        for i in idx:
            indices.append(base + i)
        base += len(verts)

    if base > 65535:
        raise ValueError('%s: too many vertices for uint16 indices (%d)' % (src, base))

    minx = min(p[0] for p in positions)
    maxx = max(p[0] for p in positions)
    miny = min(p[1] for p in positions)
    maxy = max(p[1] for p in positions)
    minz = min(p[2] for p in positions)
    maxz = max(p[2] for p in positions)
    cx = (minx + maxx) * 0.5
    cz = (minz + maxz) * 0.5

    # Centre on X/Z and drop onto y = 0 so placement maths is identical for
    # every asset.
    fixed = [(p[0] - cx, p[1] - miny, p[2] - cz) for p in positions]

    return {
        'name': name, 'role': role, 'targetSize': target_size,
        'positions': fixed, 'normals': normals, 'colours': colours,
        'indices': indices,
        'min': (minx - cx, 0.0, minz - cz),
        'max': (maxx - cx, maxy - miny, maxz - cz),
    }


def write_set(out_dir, set_name, models):
    out = bytearray()
    out += struct.pack('<IIII', MAGIC, VERSION, len(models), 0)
    for m in models:
        nm = m['name'].encode('ascii')
        out += struct.pack('<I', len(nm))
        out += bytearray(nm).ljust((len(nm) + 3) // 4 * 4, b'\0')
        out += struct.pack('<IIII', len(m['positions']), len(m['indices']),
                           m['role'], 0)
        out += struct.pack('<f', m['targetSize'])
        out += struct.pack('<ffffff', m['min'][0], m['min'][1], m['min'][2],
                           m['max'][0], m['max'][1], m['max'][2])
        for (p, n, c) in zip(m['positions'], m['normals'], m['colours']):
            out += struct.pack('<9f', p[0], p[1], p[2], n[0], n[1], n[2], c[0], c[1], c[2])
        out += struct.pack('<%dH' % len(m['indices']), *m['indices'])

    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, set_name + '.bin')
    with open(path, 'wb') as f:
        f.write(out)

    verts = sum(len(m['positions']) for m in models)
    tris = sum(len(m['indices']) // 3 for m in models)
    biggest = max(models, key=lambda m: len(m['indices']))
    print('%s: %d models, %d verts, %d tris, %d bytes'
          % (path, len(models), verts, tris, len(out)))
    print('   largest: %s (%d tris)' % (biggest['name'], len(biggest['indices']) // 3))
    return len(out)


def main(argv):
    args = list(argv[1:])
    use_manifest = False
    if args and args[0] == '--manifest':
        use_manifest = True
        args = args[1:]
    if len(args) < 3:
        print(__doc__)
        return 2

    src_dir, out_dir, set_name = args[0], args[1], args[2]

    sys.path.insert(0, HERE)
    import model_manifest as manifest

    if use_manifest:
        entries = list(manifest.MODELS)
    else:
        by_name = dict((m[0], m) for m in manifest.MODELS)
        entries = [by_name.get(n, (n, '?', 'ROCK', 1.0)) for n in args[3:]]
        if not entries:
            print('error: give model names, or use --manifest')
            return 2

    atlases = {}
    for pack in manifest.COLORMAP_PACKS:
        path = os.path.join(src_dir, 'Textures', pack + '.png')
        if os.path.exists(path):
            atlases[pack] = Atlas(path)

    models = []
    for (name, pack, role, size) in entries:
        src = os.path.join(src_dir, pack, name + '.glb')
        if not os.path.exists(src):
            alt = os.path.join(src_dir, name + '.glb')
            if os.path.exists(alt):
                src = alt
            else:
                print('   missing, skipped: %s' % src)
                continue
        models.append(bake(src, name, manifest.ROLES[role], size,
                           atlases.get(pack) if pack in atlases else None))

    if not models:
        print('error: nothing baked')
        return 1
    write_set(out_dir, set_name, models)
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
