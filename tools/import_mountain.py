#!/usr/bin/env python3
"""
Imports a mountain asset pack (FBX) into the game's baked model set.

Usage:
    tools/import_mountain.py <path-to-zip-or-directory> [--role ROLE]

Accepts:
  * "Mountain (FBX).zip"     -- the meshes; this is the one that works
  * a directory of .fbx files
  * "Mountain (Blender 4.2.2).zip" -- .blend only, which cannot be read here
    (see below)
  * "Mountain (Textures).zip" -- images; sampled into vertex colours

The .blend file cannot be parsed by this project: it is Blender's internal
format, and pulling a mesh out of it needs Blender installed. If only the
Blender zip is available, export FBX from Blender (File > Export > FBX) and run
this on that instead.

Textures are optional. The game bakes colour into vertex attributes and lights
everything with its own toon shader, so a mountain without its texture atlas
still renders -- it just gets a flat rock colour. When a texture is present its
average colour is sampled and used as the base.
"""

import argparse
import os
import struct
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import fbx_import  # noqa: E402

MAGIC = 0x4D4F444C
VERSION = 2

ROLES = {
    'cliff': 8, 'rock': 1, 'hill': 20, 'tree': 0, 'plant': 2,
}


def find_meshes(root):
    """Returns [(name, path)] for every FBX or .blend under root."""
    meshes = []
    for dirpath, _dirnames, filenames in os.walk(root):
        for fn in sorted(filenames):
            ext = fn.lower().rsplit('.', 1)[-1]
            if ext in ('fbx', 'blend'):
                meshes.append((os.path.splitext(fn)[0], os.path.join(dirpath, fn)))
    return meshes


def average_texture_colour(root):
    """Mean colour of every image in a textures folder, or a rock default."""
    try:
        from PIL import Image
    except ImportError:
        return (0.50, 0.52, 0.58)
    total = [0.0, 0.0, 0.0]
    n = 0
    for dirpath, _d, filenames in os.walk(root):
        for fn in filenames:
            if fn.lower().rsplit('.', 1)[-1] not in ('png', 'jpg', 'jpeg', 'tga'):
                continue
            try:
                im = Image.open(os.path.join(dirpath, fn)).convert('RGB')
            except Exception:
                continue
            im = im.resize((16, 16))
            px = list(im.getdata())
            if not px:
                continue
            for r, g, b in px:
                total[0] += r / 255.0
                total[1] += g / 255.0
                total[2] += b / 255.0
                n += 1
    if n == 0:
        return (0.50, 0.52, 0.58)
    return (total[0] / n, total[1] / n, total[2] / n)


def normalise(pos, target_size):
    """
    Scales a mesh so its largest axis is target_size metres and it sits on
    y = 0, centred in x/z. The game places props by footprint, so a model that
    arrives in centimetres and offset from the origin would float or sink.
    """
    xs = pos[0::3]
    ys = pos[1::3]
    zs = pos[2::3]
    minx, maxx = min(xs), max(xs)
    miny, maxy = min(ys), max(ys)
    minz, maxz = min(zs), max(zs)
    span = max(maxx - minx, maxy - miny, maxz - minz)
    if span <= 1e-6:
        raise ValueError('degenerate mesh')
    scale = target_size / span
    cx = (minx + maxx) * 0.5
    cz = (minz + maxz) * 0.5
    out = list(pos)
    for i in range(0, len(out), 3):
        out[i] = (out[i] - cx) * scale
        out[i + 1] = (out[i + 1] - miny) * scale
        out[i + 2] = (out[i + 2] - cz) * scale
    return out, scale


def weld_and_index(pos, nrm):
    """
    The bake stores indexed triangles, so identical vertices are merged here.
    Without this an FBX worth ~2k triangles would carry ~6k vertices.
    """
    lookup = {}
    out_pos = []
    out_nrm = []
    indices = []
    for i in range(0, len(pos), 3):
        key = (round(pos[i], 4), round(pos[i + 1], 4), round(pos[i + 2], 4),
               round(nrm[i], 3), round(nrm[i + 1], 3), round(nrm[i + 2], 3))
        idx = lookup.get(key)
        if idx is None:
            idx = len(out_pos) // 3
            lookup[key] = idx
            out_pos.extend(key[:3])
            out_nrm.extend(key[3:])
        indices.append(idx)
    if len(out_pos) // 3 > 65535:
        raise ValueError('mesh exceeds 65535 vertices; split it first')
    return out_pos, out_nrm, indices


def write_model(f, name, pos, nrm, indices, role, target_size, colour):
    nb = name.encode('ascii', 'replace')
    f.write(struct.pack('<I', len(nb)))
    pad = (-len(nb)) % 4
    f.write(nb + b'\x00' * pad)
    f.write(struct.pack('<IIiI', len(pos) // 3, len(indices), role, 0))
    f.write(struct.pack('<f', target_size))
    xs = pos[0::3]
    ys = pos[1::3]
    zs = pos[2::3]
    f.write(struct.pack('<ffffff', min(xs), min(ys), min(zs),
                        max(xs), max(ys), max(zs)))
    for i in range(0, len(pos), 3):
        f.write(struct.pack('<9f',
                            pos[i], pos[i + 1], pos[i + 2],
                            nrm[i], nrm[i + 1], nrm[i + 2],
                            colour[0], colour[1], colour[2]))
    for idx in indices:
        f.write(struct.pack('<H', idx))


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('source', help='zip file or directory')
    ap.add_argument('--role', default='cliff', choices=sorted(ROLES))
    ap.add_argument('--out', default='build/mountain_set')
    ap.add_argument('--name', default='mountain_set')
    ap.add_argument('--size', type=float, default=90.0,
                    help='largest axis in metres (default 90)')
    args = ap.parse_args()

    workdir = args.source
    tmp = None
    if os.path.isfile(args.source) and args.source.lower().endswith('.zip'):
        import tempfile
        tmp = tempfile.mkdtemp()
        with zipfile.ZipFile(args.source) as z:
            z.extractall(tmp)
        workdir = tmp
        print('extracted %s -> %s' % (args.source, tmp))

    colour = average_texture_colour(workdir)
    print('base colour from textures: (%.2f, %.2f, %.2f)' % colour)

    meshes = find_meshes(workdir)
    fbx = [m for m in meshes if m[1].lower().endswith('.fbx')]
    blend = [m for m in meshes if m[1].lower().endswith('.blend')]

    if not fbx:
        if blend:
            print()
            print('Only .blend files were found:')
            for nm, path in blend:
                print('  ' + path)
            print()
            print('.blend is Blender\'s internal format and needs Blender to read.')
            print('Open it in Blender and File > Export > FBX, then run this on that.')
            return 2
        print('No .fbx files found in %s' % workdir)
        if tmp:
            print('(the archive had %d files total)' % len(meshes))
        return 1

    role = ROLES[args.role]
    models = []
    for name, path in fbx:
        try:
            pos, nrm = fbx_import.read_fbx(path)
        except Exception as e:
            print('  skip %s: %s' % (name, e))
            continue
        pos, scale = normalise(pos, args.size)
        pos, nrm, indices = weld_and_index(pos, nrm)
        print('  %-40s %6d tris  %6d verts  scale %.4f'
              % (name[:40], len(indices) // 3, len(pos) // 3, scale))
        models.append((name, pos, nrm, indices))

    if not models:
        print('Nothing importable.')
        return 1

    os.makedirs(args.out, exist_ok=True)
    out = os.path.join(args.out, 'world.bin')
    with open(out, 'wb') as f:
        f.write(struct.pack('<IIII', MAGIC, VERSION, len(models), 0))
        for name, pos, nrm, indices in models:
            write_model(f, name, pos, nrm, indices, role, args.size, colour)
    print()
    print('wrote %s (%d models, %.1f KiB)'
          % (out, len(models), os.path.getsize(out) / 1024.0))
    print('Copy it over app/src/main/assets/models/world.bin, then rebuild.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
