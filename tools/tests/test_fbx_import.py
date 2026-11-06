#!/usr/bin/env python3
"""
Checks tools/fbx_import.py against a binary FBX written by make_test_fbx.py.

The fixture mimics what an exporter emits: a compressing typed-array property,
PolygonVertexIndex with negated face terminators, and a Model with nested
Properties70 transforms. If the importer regresses on any of those the numbers
below stop matching.
"""
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..'))

import fbx_import  # noqa: E402

checks = 0
failures = 0


def check(cond, what):
    global checks, failures
    checks += 1
    if cond:
        print('  ok   ' + what)
    else:
        failures += 1
        print('  FAIL ' + what)


def main():
    tmp = tempfile.mkdtemp()
    path = os.path.join(tmp, 'pyramid.fbx')
    subprocess.check_call([sys.executable, os.path.join(HERE, 'make_test_fbx.py'), path])

    pos, nrm = fbx_import.read_fbx(path)
    verts = len(pos) // 3

    print('fbx import')
    check(len(pos) % 9 == 0, 'vertex data is whole triangles')
    check(verts // 3 == 6, 'six triangles from quad + four faces')
    check(len(nrm) == len(pos), 'one normal per position')

    xs = [pos[i * 3] for i in range(verts)]
    ys = [pos[i * 3 + 1] for i in range(verts)]
    zs = [pos[i * 3 + 2] for i in range(verts)]

    # Lcl Scaling (2,2,2) then Lcl Translation (10,0,-5) must both apply.
    check(abs(min(xs) - 10.0) < 1e-4, 'translation x applied (%.2f)' % min(xs))
    check(abs(max(xs) - 18.0) < 1e-4, 'scale x applied (%.2f)' % max(xs))
    check(abs(min(ys) - 0.0) < 1e-4, 'base stays on the ground (%.2f)' % min(ys))
    check(abs(max(ys) - 9.0) < 1e-4, 'height scaled (%.2f)' % max(ys))
    check(abs(min(zs) - (-5.0)) < 1e-4, 'translation z applied (%.2f)' % min(zs))

    # A file with no geometry must fail loudly rather than return nonsense.
    bad = os.path.join(tmp, 'empty.fbx')
    with open(bad, 'wb') as f:
        f.write(b'Kaydara FBX Binary  \x00\x1a\x00' + b'\x00' * 64)
    try:
        fbx_import.read_fbx(bad)
        check(False, 'empty file raises')
    except Exception:
        check(True, 'empty file raises instead of returning garbage')

    print()
    print('checks: %d   failures: %d' % (checks, failures))
    print('RESULT: ' + ('PASS' if failures == 0 else 'FAIL'))
    return 0 if failures == 0 else 1


if __name__ == '__main__':
    sys.exit(main())
