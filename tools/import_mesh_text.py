#!/usr/bin/env python3
"""
Imports meshes from the plain-text format in docs/CLAUDE_PROMPT_MOUNTAIN.md.

Why: archives cannot be attached to the conversation, so geometry has to arrive
as text. This reads that text, validates it, and writes the same baked
world.bin that tools/import_mountain.py produces from an FBX.

Format:
    ### MESH <name>
    VERTS <vertexCount>
    X Y Z NX NY NZ        # one line per vertex
    ...
    TRIS <triangleCount>
    A B C                 # one line per triangle
    ...

Anything before the first ### MESH is ignored, so markdown fences and chatter
around the block do no harm.
"""

import argparse
import os
import struct
import sys

MAGIC = 0x4D4F444C
VERSION = 2

ROLES = {'cliff': 8, 'rock': 1, 'hill': 20, 'tree': 0, 'plant': 2}


class ParseError(Exception):
    pass


def parse(text):
    """Returns [(name, positions, normals, indices)] from the text format."""
    meshes = []
    lines = text.splitlines()
    i = 0
    n = len(lines)
    while i < n:
        line = lines[i].strip()
        if not line.startswith('### MESH'):
            i += 1
            continue
        name = line[len('### MESH'):].strip() or ('mesh%d' % len(meshes))
        i += 1

        # VERTS
        while i < n and not lines[i].strip():
            i += 1
        if i >= n or not lines[i].strip().upper().startswith('VERTS'):
            raise ParseError('%s: expected VERTS, got %r'
                             % (name, lines[i].strip() if i < n else 'EOF'))
        try:
            vcount = int(lines[i].strip().split()[1])
        except (IndexError, ValueError):
            raise ParseError('%s: VERTS needs a count' % name)
        i += 1

        pos = []
        nrm = []
        for v in range(vcount):
            while i < n and not lines[i].strip():
                i += 1
            if i >= n:
                raise ParseError('%s: ran out of vertices at %d/%d'
                                 % (name, v, vcount))
            parts = lines[i].strip().replace(',', ' ').split()
            if len(parts) < 6:
                raise ParseError('%s: vertex %d has %d floats, needs 6'
                                 % (name, v, len(parts)))
            try:
                vals = [float(p) for p in parts[:6]]
            except ValueError:
                raise ParseError('%s: vertex %d is not numeric: %r'
                                 % (name, v, lines[i].strip()))
            pos.extend(vals[:3])
            nrm.extend(vals[3:6])
            i += 1

        # TRIS
        while i < n and not lines[i].strip():
            i += 1
        if i >= n or not lines[i].strip().upper().startswith('TRIS'):
            raise ParseError('%s: expected TRIS, got %r'
                             % (name, lines[i].strip() if i < n else 'EOF'))
        try:
            tcount = int(lines[i].strip().split()[1])
        except (IndexError, ValueError):
            raise ParseError('%s: TRIS needs a count' % name)
        i += 1

        idx = []
        for t in range(tcount):
            while i < n and not lines[i].strip():
                i += 1
            if i >= n:
                raise ParseError('%s: ran out of triangles at %d/%d'
                                 % (name, t, tcount))
            parts = lines[i].strip().replace(',', ' ').split()
            if len(parts) < 3:
                raise ParseError('%s: triangle %d needs 3 indices' % (name, t))
            try:
                tri = [int(float(p)) for p in parts[:3]]
            except ValueError:
                raise ParseError('%s: triangle %d is not numeric' % (name, t))
            for vi in tri:
                if vi < 0 or vi >= vcount:
                    raise ParseError('%s: triangle %d references vertex %d of %d'
                                     % (name, t, vi, vcount))
            idx.extend(tri)
            i += 1

        meshes.append((name, pos, nrm, idx))
    return meshes


def normalise(pos, target_size):
    """Scales the largest axis to target_size and grounds the mesh at y = 0."""
    xs = pos[0::3]
    ys = pos[1::3]
    zs = pos[2::3]
    minx, maxx = min(xs), max(xs)
    miny, maxy = min(ys), max(ys)
    minz, maxz = min(zs), max(zs)
    span = max(maxx - minx, maxy - miny, maxz - minz)
    if span <= 1e-6:
        raise ParseError('degenerate mesh (zero size)')
    scale = target_size / span
    cx = (minx + maxx) * 0.5
    cz = (minz + maxz) * 0.5
    out = list(pos)
    for i in range(0, len(out), 3):
        out[i] = (out[i] - cx) * scale
        out[i + 1] = (out[i + 1] - miny) * scale
        out[i + 2] = (out[i + 2] - cz) * scale
    return out, scale


def write_model(f, name, pos, nrm, indices, role, target_size, colour):
    nb = name.encode('ascii', 'replace')
    f.write(struct.pack('<I', len(nb)))
    f.write(nb + b'\x00' * ((-len(nb)) % 4))
    f.write(struct.pack('<IIiI', len(pos) // 3, len(indices), role, 0))
    f.write(struct.pack('<f', target_size))
    xs, ys, zs = pos[0::3], pos[1::3], pos[2::3]
    f.write(struct.pack('<ffffff', min(xs), min(ys), min(zs),
                        max(xs), max(ys), max(zs)))
    for i in range(0, len(pos), 3):
        f.write(struct.pack('<9f', pos[i], pos[i + 1], pos[i + 2],
                            nrm[i], nrm[i + 1], nrm[i + 2],
                            colour[0], colour[1], colour[2]))
    for vi in indices:
        f.write(struct.pack('<H', vi))


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('source', help='text file, or - for stdin')
    ap.add_argument('--role', default='cliff', choices=sorted(ROLES))
    ap.add_argument('--out', default='build/mountain_set')
    ap.add_argument('--size', type=float, default=90.0)
    ap.add_argument('--colour', default='0.55,0.57,0.62',
                    help='base RGB, 0-1')
    args = ap.parse_args()

    if args.source == '-':
        text = sys.stdin.read()
    else:
        with open(args.source, 'r', encoding='utf-8', errors='replace') as f:
            text = f.read()

    try:
        meshes = parse(text)
    except ParseError as e:
        print('parse error: %s' % e)
        return 1

    if not meshes:
        print('no "### MESH" blocks found -- is this the right text?')
        return 1

    colour = tuple(float(c) for c in args.colour.split(','))
    if len(colour) != 3:
        print('--colour needs three values')
        return 1

    role = ROLES[args.role]
    os.makedirs(args.out, exist_ok=True)
    out = os.path.join(args.out, 'world.bin')
    written = 0
    with open(out, 'wb') as f:
        # Header is written after the count is known, so build models first.
        prepared = []
        for name, pos, nrm, idx in meshes:
            try:
                pos, scale = normalise(pos, args.size)
            except ParseError as e:
                print('  skip %s: %s' % (name, e))
                continue
            if len(pos) // 3 > 65535:
                print('  skip %s: %d vertices exceeds the 65535 limit'
                      % (name, len(pos) // 3))
                continue
            prepared.append((name, pos, nrm, idx, scale))

        if not prepared:
            print('nothing importable')
            return 1

        f.write(struct.pack('<IIII', MAGIC, VERSION, len(prepared), 0))
        for name, pos, nrm, idx, scale in prepared:
            print('  %-30s %6d tris  %6d verts  scale %.4f'
                  % (name[:30], len(idx) // 3, len(pos) // 3, scale))
            write_model(f, name, pos, nrm, idx, role, args.size, colour)
            written += 1

    print()
    print('wrote %s (%d models, %.1f KiB)'
          % (out, written, os.path.getsize(out) / 1024.0))
    print('Copy it over app/src/main/assets/models/world.bin, then rebuild.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
