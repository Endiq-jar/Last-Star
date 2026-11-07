#!/usr/bin/env python3
"""
Bakes the environment pack into the game's model set.

Two things make this more than a file-format conversion:

  * The pack is authored in centimetres and each asset is a different physical
    size, so each is scaled to a deliberate target in metres rather than to a
    shared bounding box. A tree you can walk under, a bush you can see over, a
    mountain that closes the horizon.

  * Every normal in the pack points straight up (0, 1, 0) -- the exporter wrote
    billboard/unlit normals. Through a toon shader that shades by normal, every
    surface lands in the same ramp band and trees, rock faces and grass all
    render as one flat colour mass. Normals are rebuilt from the triangles here.
"""

import os
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import fbx_import  # noqa: E402

MAGIC = 0x4D4F444C
VERSION = 2

# ModelLibrary role ids
TREE, ROCK, PLANT, GRASS, FLOWER = 0, 1, 2, 3, 4
PATH, HILL = 9, 20
WANDER = 23

SRC = 'assets_src/asset/game asset (FBX)'

# (file, model name, role, target metres, base colour, scale mode)
# 'height' for vertical props, 'width' for flat ground-cover cards,
# 'span' for the bridge, whose longest axis is the walkway.
#
# Base colours are sampled from the Mountain (Textures) pack's Colored/ ALB
# maps, not guessed -- see tools/sample_mountain_textures.py for how each one
# was derived (including the three with no texture of their own: mountain,
# rock2 and pebbles are variants of Rock01's sampled colour, and flowers is a
# hue-rotation of Flower01's, since the pack ships no "Flowers01" map).
# spell-book isn't part of this nature pack and keeps its original colour.
ASSETS = [
    ('Mountain01.fbx', 'mountain',   HILL,   260.0, (0.44, 0.48, 0.54), 'height'),
    ('Tree01.fbx',     'tree',       TREE,     8.5, (0.14, 0.25, 0.04), 'height'),
    ('Bush01.fbx',     'bush',       PLANT,    1.7, (0.10, 0.36, 0.03), 'height'),
    ('Rock01.fbx',     'rock',       ROCK,     1.9, (0.31, 0.37, 0.40), 'height'),
    ('Rock02.fbx',     'rock2',      ROCK,     2.6, (0.33, 0.39, 0.42), 'height'),
    # Ground cover is sized by height, not width. Every one of these is
    # authored as a broad, low mound of clumps -- Grass01 is 115 wide against
    # 45 tall -- so scaling by width produces a wide flat plate lying on the
    # ground, which is exactly how it looked. These are the heights each thing
    # should stand at in metres.
    ('Pebbles01.fbx',  'pebbles',    ROCK,     0.14, (0.37, 0.43, 0.47), 'height'),
    ('Grass01.fbx',    'grass',      GRASS,    0.95, (0.14, 0.36, 0.07), 'height'),
    ('Flower01.fbx',   'flower',     FLOWER,   0.52, (0.13, 0.37, 0.45), 'height'),
    ('Flowers01.fbx',  'flowers',    FLOWER,   0.44, (0.45, 0.13, 0.20), 'height'),
    ('Bridge01.fbx',   'bridge',     PATH,    14.0, (0.21, 0.17, 0.10), 'span'),
    ('spell-book.fbx', 'spell-book', WANDER,   0.42, (0.42, 0.34, 0.74), 'span'),
]

# Two decorative meshes carry most of the triangle budget and are seen from a
# distance or held at arm's length, so they get decimated.
BUDGET = {'bridge': 3200, 'spell-book': 2400}


def compute_normals(pos):
    """Rebuilds per-vertex normals from the triangles, then normalises."""
    n = len(pos) // 3
    acc = [0.0] * (n * 3)
    for t in range(0, n - 2, 3):
        a, b, c = t * 3, (t + 1) * 3, (t + 2) * 3
        e1x, e1y, e1z = pos[b] - pos[a], pos[b + 1] - pos[a + 1], pos[b + 2] - pos[a + 2]
        e2x, e2y, e2z = pos[c] - pos[a], pos[c + 1] - pos[a + 1], pos[c + 2] - pos[a + 2]
        nx = e1y * e2z - e1z * e2y
        ny = e1z * e2x - e1x * e2z
        nz = e1x * e2y - e1y * e2x
        for v in (a, b, c):
            acc[v] += nx
            acc[v + 1] += ny
            acc[v + 2] += nz
    out = []
    for i in range(0, len(acc), 3):
        x, y, z = acc[i], acc[i + 1], acc[i + 2]
        ln = (x * x + y * y + z * z) ** 0.5
        out.extend((x / ln, y / ln, z / ln) if ln > 1e-9 else (0.0, 1.0, 0.0))
    return out


def blend_normals(computed, original, factor):
    """Leans rebuilt normals toward the authored ones."""
    if factor <= 0.0:
        return computed
    return [computed[i] * (1.0 - factor) + original[i] * factor
            for i in range(len(computed))]


def decimate(pos, max_tris):
    """Vertex-clustering decimation, keeping the silhouette."""
    if len(pos) // 9 <= max_tris:
        return pos
    xs, ys, zs = pos[0::3], pos[1::3], pos[2::3]
    span = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))
    cells = max(8, int((max_tris * 1.6) ** 0.5))
    cell = span / cells
    minx, miny, minz = min(xs), min(ys), min(zs)

    lookup, out_pos, remap = {}, [], []
    for i in range(0, len(pos), 3):
        key = (int((pos[i] - minx) / cell), int((pos[i + 1] - miny) / cell),
               int((pos[i + 2] - minz) / cell))
        j = lookup.get(key)
        if j is None:
            j = len(out_pos) // 3
            lookup[key] = j
            out_pos.extend((pos[i], pos[i + 1], pos[i + 2]))
        remap.append(j)

    merged = []
    for t in range(0, len(remap) - 2, 3):
        a, b, c = remap[t], remap[t + 1], remap[t + 2]
        if a == b or b == c or a == c:
            continue
        for v in (a, b, c):
            merged.extend((out_pos[v * 3], out_pos[v * 3 + 1], out_pos[v * 3 + 2]))
    return merged


def normalise(pos, target, mode):
    """
    Scales and grounds the model.

    <p>Which axis to author against depends on what the asset is:</p>
    <ul>
      <li>{@code 'height'} for vertical props -- trees, rocks, mountains;</li>
      <li>{@code 'width'} for flat ground-cover cards, which are wide and low
          and would explode to metres across if scaled by height;</li>
      <li>{@code 'span'} for the bridge, whose longest axis is the walkway it
          crosses, not its height.</li>
    </ul>
    """
    xs, ys, zs = pos[0::3], pos[1::3], pos[2::3]
    miny, maxy = min(ys), max(ys)
    height = maxy - miny
    if height <= 1e-6:
        raise ValueError('zero height')
    if mode == 'width':
        span = max(max(xs) - min(xs), max(zs) - min(zs))
        if span <= 1e-6:
            raise ValueError('zero width')
        scale = target / span
    elif mode == 'span':
        span = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs))
        scale = target / span
    else:
        scale = target / height
    cx = (min(xs) + max(xs)) * 0.5
    cz = (min(zs) + max(zs)) * 0.5
    out = list(pos)
    for i in range(0, len(out), 3):
        out[i] = (out[i] - cx) * scale
        out[i + 1] = (out[i + 1] - miny) * scale
        out[i + 2] = (out[i + 2] - cz) * scale
    return out, scale


def weld(pos, nrm):
    """Merges duplicate vertices; the bake format indexes triangles."""
    lookup, op, on, idx = {}, [], [], []
    for i in range(0, len(pos), 3):
        key = (round(pos[i], 2), round(pos[i + 1], 2), round(pos[i + 2], 2),
               round(nrm[i], 1), round(nrm[i + 1], 1), round(nrm[i + 2], 1))
        j = lookup.get(key)
        if j is None:
            j = len(op) // 3
            lookup[key] = j
            op.extend(key[:3])
            on.extend(key[3:])
        idx.append(j)
    if len(op) // 3 > 65535:
        raise ValueError('over 65535 verts (%d)' % (len(op) // 3))
    return op, on, idx


def write_model(f, name, pos, nrm, idx, role, target, colour):
    nb = name.encode('ascii')
    f.write(struct.pack('<I', len(nb)))
    f.write(nb + b'\x00' * ((-len(nb)) % 4))
    f.write(struct.pack('<IIiI', len(pos) // 3, len(idx), role, 0))
    f.write(struct.pack('<f', target))
    xs, ys, zs = pos[0::3], pos[1::3], pos[2::3]
    f.write(struct.pack('<ffffff', min(xs), min(ys), min(zs),
                        max(xs), max(ys), max(zs)))
    for i in range(0, len(pos), 3):
        f.write(struct.pack('<9f', pos[i], pos[i + 1], pos[i + 2],
                            nrm[i], nrm[i + 1], nrm[i + 2],
                            colour[0], colour[1], colour[2]))
    for v in idx:
        f.write(struct.pack('<H', v))


def read_set(path):
    """
    Reads an existing baked set back into the tuples this script writes.

    <p>The built-in library -- trees, rocks, walls, towers, campfires -- is baked
    from glTF sources that are not part of the shipped repository, so the only
    way to keep it is to merge into the baked file rather than replace it.
    Losing those 84 models to gain 11 would throw away the whole settlement
    kit, and every wall, tent and tower in the world with it.</p>
    """
    data = open(path, 'rb').read()
    magic, version, count, _ = struct.unpack('<IIII', data[:16])
    if magic != MAGIC or version != VERSION:
        raise ValueError('%s is not a v%d set' % (path, version))
    off = 16
    out = []
    for _ in range(count):
        name_len = struct.unpack('<I', data[off:off + 4])[0]
        off += 4
        name = data[off:off + name_len].decode('ascii')
        off += (name_len + 3) & ~3
        verts, idxs, role, _res = struct.unpack('<IIII', data[off:off + 16])
        off += 16
        target = struct.unpack('<f', data[off:off + 4])[0]
        off += 4 + 24

        pos, nrm, colour = [], [], [0.0, 0.0, 0.0]
        for _v in range(verts):
            px, py, pz, nx, ny, nz, cr, cg, cb = struct.unpack(
                '<9f', data[off:off + 36])
            off += 36
            pos.extend((px, py, pz))
            nrm.extend((nx, ny, nz))
            colour = [cr, cg, cb]
        idx = list(struct.unpack('<%dH' % idxs, data[off:off + idxs * 2]))
        off += idxs * 2
        out.append((name, pos, nrm, idx, role, target, tuple(colour)))
    return out


def main():
    plain = [a for a in sys.argv[1:] if not a.startswith('--')]
    out_path = plain[0] if plain else 'build/pack/world.bin'
    base = None
    argv = sys.argv[1:]
    for i, a in enumerate(argv):
        if a == '--merge' and i + 1 < len(argv):
            base = argv[i + 1]
    os.makedirs(os.path.dirname(out_path) or '.', exist_ok=True)

    existing = []
    if base and os.path.exists(base):
        loaded = read_set(base)
        # Re-running the bake must be safe. The baked set is committed and the
        # built-in models cannot be regenerated (their glTF sources are not in
        # the repository), so the base is whatever is committed -- which may
        # already contain the pack. Dropping those entries first makes this
        # idempotent instead of failing on a name collision.
        existing = [m for m in loaded if not m[0].startswith('pk_')]
        dropped = len(loaded) - len(existing)
        print('merging into %d built-in models from %s%s'
              % (len(existing), base,
                 '' if dropped == 0 else ' (replacing %d pack models)' % dropped))

    prepared = []
    for fn, name, role, target, colour, mode in ASSETS:
        path = os.path.join(SRC, fn)
        if not os.path.exists(path):
            print('  missing %s' % path)
            continue
        pos, nrm = fbx_import.read_fbx(path)
        pos, scale = normalise(pos, target, mode)

        budget = BUDGET.get(name)
        if budget:
            before = len(pos) // 9
            pos = decimate(pos, budget)
            print('  %-12s decimated %d -> %d triangles' % (name, before, len(pos) // 9))

        blending = 0.0 if name in ('grass', 'flower', 'flowers') else 0.12
        nrm = blend_normals(compute_normals(pos), nrm[:len(pos)], blending)
        pos, nrm, idx = weld(pos, nrm)

        xs, ys, zs = pos[0::3], pos[1::3], pos[2::3]
        print('  %-12s %6d tris  %5d verts  %.2fw x %.2fh m'
              % (name, len(idx) // 3, len(pos) // 3,
                 max(max(xs) - min(xs), max(zs) - min(zs)), max(ys) - min(ys)))
        prepared.append(('pk_' + name, pos, nrm, idx, role, target, colour))

    if not prepared:
        print('nothing baked')
        return 1

    taken = set(m[0] for m in existing)
    for item in prepared:
        if item[0] in taken:
            raise ValueError('name collision on %s' % item[0])
    everything = existing + prepared

    with open(out_path, 'wb') as f:
        f.write(struct.pack('<IIII', MAGIC, VERSION, len(everything), 0))
        for item in everything:
            write_model(f, item[0], item[1], item[2], item[3], item[4],
                        item[5], item[6])

    tris = sum(len(p[3]) // 3 for p in prepared)
    print()
    print('wrote %s' % out_path)
    print('  %d pack models, %d pack triangles' % (len(prepared), tris))
    print('  %d models in set, %.0f KiB'
          % (len(everything), os.path.getsize(out_path) / 1024.0))
    return 0


if __name__ == '__main__':
    sys.exit(main())
