#!/usr/bin/env python3
"""
Rewrites the colour field of the already-baked pk_* models in world.bin using
sample_mountain_textures.main(), leaving every vertex position/normal/index
and every non-pack model untouched.

Needed because assets_src/ (the pack's source FBX) is git-ignored and not
present in a fresh checkout, so bake_mountain_pack.py can't re-run its FBX
import. The geometry already in the committed world.bin is reused as-is;
only the colour triplet written per model changes.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import bake_mountain_pack as bake  # noqa: E402
import sample_mountain_textures as sample  # noqa: E402


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/assets/models/world.bin'
    colours = sample.main()

    models = bake.read_set(path)
    changed = 0
    out = []
    for name, pos, nrm, idx, role, target, colour in models:
        new_colour = colours.get(name)
        if new_colour is not None and new_colour != colour:
            print('%-14s (%.2f, %.2f, %.2f) -> (%.2f, %.2f, %.2f)'
                  % (name, colour[0], colour[1], colour[2],
                     new_colour[0], new_colour[1], new_colour[2]))
            colour = new_colour
            changed += 1
        out.append((name, pos, nrm, idx, role, target, colour))

    with open(path, 'wb') as f:
        import struct
        f.write(struct.pack('<IIII', bake.MAGIC, bake.VERSION, len(out), 0))
        for item in out:
            bake.write_model(f, *item)

    print()
    print('%d of %d models recoloured, %s unchanged' % (changed, len(out), path))


if __name__ == '__main__':
    main()
