#!/usr/bin/env python3
"""
Checks tools/import_mesh_text.py: the paste-back format Claude returns must
produce a world.bin the Java loader accepts, and malformed input must fail
loudly rather than write a broken file.
"""
import os
import struct
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..'))

import import_mesh_text  # noqa: E402

checks = 0
failures = 0


def check(cond, what):
    global checks, failures
    checks += 1
    print(('  ok   ' if cond else '  FAIL ') + what)
    if not cond:
        failures += 1


GOOD = """### MESH peak_a
VERTS 3
0.0 0.0 0.0 0.0 1.0 0.0
10.0 0.0 0.0 0.0 1.0 0.0
0.0 10.0 0.0 0.0 1.0 0.0
TRIS 1
0 1 2
"""


def main():
    tmp = tempfile.mkdtemp()
    print('mesh text import')

    # 1. A well-formed block parses.
    meshes = import_mesh_text.parse(GOOD)
    check(len(meshes) == 1, 'one mesh parsed')
    name, pos, nrm, idx = meshes[0]
    check(name == 'peak_a', 'name read')
    check(len(pos) == 9, 'three vertices of three floats')
    check(len(nrm) == 9, 'three normals')
    check(idx == [0, 1, 2], 'triangle indices')

    # 2. Noise around the block is tolerated: Claude adds fences and prose.
    noisy = 'Here are the meshes:\n\n```\n' + GOOD + '```\n\nLet me know!'
    check(len(import_mesh_text.parse(noisy)) == 1, 'markdown fences ignored')

    # 3. Commas and extra whitespace are fine.
    commas = GOOD.replace('0.0 0.0 0.0', '0.0,0.0,0.0')
    check(len(import_mesh_text.parse(commas)) == 1, 'comma separators accepted')

    # 4. Bad input fails loudly.
    for label, bad in [
        ('out-of-range index', GOOD.replace('0 1 2', '0 1 99')),
        ('too few floats', GOOD.replace('0.0 1.0 0.0', '0.0 1.0')),
        ('vertex count too high', GOOD.replace('VERTS 3', 'VERTS 9')),
        ('no blocks at all', 'nothing here'),
    ]:
        try:
            got = import_mesh_text.parse(bad)
            check(len(got) == 0, '%s rejected' % label)
        except import_mesh_text.ParseError:
            check(True, '%s rejected' % label)

    # 5. End to end: parse -> normalise -> write -> check the file header.
    src = os.path.join(tmp, 'meshes.txt')
    with open(src, 'w') as f:
        f.write(GOOD)
    outdir = os.path.join(tmp, 'out')
    r = subprocess.run([sys.executable, os.path.join(HERE, '..', 'import_mesh_text.py'),
                        src, '--out', outdir, '--size', '50'],
                       capture_output=True, text=True)
    check(r.returncode == 0, 'cli exit 0')
    bin_path = os.path.join(outdir, 'world.bin')
    check(os.path.exists(bin_path), 'world.bin written')

    if os.path.exists(bin_path):
        data = open(bin_path, 'rb').read()
        magic, version, count, _ = struct.unpack_from('<IIII', data, 0)
        check(magic == 0x4D4F444C, 'magic is LDMOD')
        check(version == 2, 'bake version 2')
        check(count == 1, 'one model in the file')
        # Bounds follow the name, counts and target size.
        nl = struct.unpack_from('<I', data, 16)[0]
        off = 16 + 4 + nl + ((-nl) % 4) + 16 + 4
        bounds = struct.unpack_from('<ffffff', data, off)
        height = bounds[4] - bounds[1]
        check(abs(height - 50.0) < 0.01,
              'normalised to 50 m tall (%.2f)' % height)
        check(abs(bounds[1]) < 0.01, 'grounded at y=0')

    print()
    print('checks: %d   failures: %d' % (checks, failures))
    print('RESULT: ' + ('PASS' if failures == 0 else 'FAIL'))
    return 0 if failures == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
