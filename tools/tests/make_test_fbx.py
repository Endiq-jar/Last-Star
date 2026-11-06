"""Writes a binary FBX resembling an exporter's output, to test the importer."""
import struct, zlib, sys

def prop_i(v):   return b'I' + struct.pack('<i', v)
def prop_l(v):   return b'L' + struct.pack('<q', v)
def prop_d(v):   return b'D' + struct.pack('<d', v)
def prop_s(v):
    b = v.encode('ascii'); return b'S' + struct.pack('<I', len(b)) + b
def prop_arr_f(vals):
    raw = struct.pack('<%df' % len(vals), *vals)
    comp = zlib.compress(raw)
    return b'f' + struct.pack('<III', len(vals), 1, len(comp)) + comp
def prop_arr_i(vals):
    raw = struct.pack('<%di' % len(vals), *vals)
    comp = zlib.compress(raw)
    return b'i' + struct.pack('<III', len(vals), 1, len(comp)) + comp

class N:
    def __init__(self, name, props=None, children=None):
        self.name=name; self.props=props or []; self.children=children or []

def encode(nodes, offset):
    """EndOffset is an ABSOLUTE file offset, so offsets thread through here."""
    out = b''
    for n in nodes:
        nb = n.name.encode('ascii')
        props = b''.join(n.props)
        plen = len(props)                      # byte count, not a property count
        body = encode(n.children, offset + 13 + len(nb) + plen)
        end = offset + 13 + len(nb) + plen + len(body) + 13
        out += (struct.pack('<III', end, len(n.props), plen)
                + bytes([len(nb)]) + nb + props + body + b'\x00' * 13)
        offset = end
    return out

def build(path):
    V = [0,0,0, 4,0,0, 4,0,4, 0,0,4, 2,4.5,2]
    # FBX encodes each face as flat indices whose LAST index is negated (~i).
    # Every face needs its own terminator; omitting one concatenates the rest
    # into a single polygon.
    faces = [0, 1, 2, ~3,
             0, 4, ~1,
             1, 4, ~2,
             2, 4, ~3,
             3, 4, ~0]
    Nn = [0,1,0]*4 + [0.5,0.6,0.5]

    geom = N('Geometry', [prop_l(1001), prop_s('Pyramid::Geometry'), prop_s('Mesh')], [
        N('Vertices', [prop_arr_f([float(x) for x in V])]),
        N('PolygonVertexIndex', [prop_arr_i(faces)]),
        N('GeometryVersion', [prop_i(124)]),
        N('Normals', [prop_arr_f([float(x) for x in Nn])]),
    ])
    model = N('Model', [prop_l(2001), prop_s('Pyramid'), prop_s('Mesh')], [
        N('Version', [prop_i(232)]),
        N('Properties70', [], [
            N('P', [prop_s('Lcl Translation'), prop_s('Lcl Translation'), prop_s(''),
                    prop_s('A'), prop_d(10.0), prop_d(0.0), prop_d(-5.0)]),
            N('P', [prop_s('Lcl Scaling'), prop_s('Lcl Scaling'), prop_s(''),
                    prop_s('A'), prop_d(2.0), prop_d(2.0), prop_d(2.0)]),
        ]),
        N('Geometry', [prop_l(1001)]),
    ])
    root = [N('Objects', [], [geom, model])]
    header = b'Kaydara FBX Binary  \x00\x1a\x00' + struct.pack('<I', 7400)
    data = header + encode(root, len(header)) + b'\x00' * 160
    open(path, 'wb').write(data)
    print('wrote', path, len(data), 'bytes')

build(sys.argv[1] if len(sys.argv) > 1 else 'build/test.fbx')
