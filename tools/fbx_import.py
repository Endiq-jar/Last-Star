"""
Reads Autodesk FBX (binary 7.x and ASCII) and hands back flat triangle soup.

Why this exists: the Mountain pack the project wanted ships as FBX. Unity's
Asset Store is not reachable from the build environment, but if the FBX files
are placed in `assets_src/mountain/` this module turns them into the same
vertex arrays the rest of the bake pipeline already understands, so the art can
drop into the world without any intermediate DCC tool or converter.

Only the parts that matter for static environment art are implemented:
geometry, global transforms, and per-vertex normals. Materials, animation,
cameras and lights are ignored on purpose -- the game bakes colour into vertex
attributes and lights everything with its own toon shader.

Binary FBX layout (little endian):
    header  "Kaydara FBX Binary  \\x00" + 2 bytes + u32 version (>= 7100 -> u32)
    nodes   u32 endOffset, u32 numProperties, u32 propertyListLen, u8 nameLen,
            name, properties..., nested nodes..., u32 zero terminator
"""

import struct
import zlib


class FbxError(Exception):
    pass


# --------------------------------------------------------------- node reading

class Node(object):
    __slots__ = ('name', 'props', 'children')

    def __init__(self, name, props=None, children=None):
        self.name = name
        self.props = props or []
        self.children = children or []

    def find(self, name):
        for c in self.children:
            if c.name == name:
                return c
        return None

    def find_all(self, name):
        return [c for c in self.children if c.name == name]

    def get(self, index, default=None):
        return self.props[index] if index < len(self.props) else default

    def __repr__(self):
        return '<%s %d props, %d children>' % (
            self.name, len(self.props), len(self.children))


def _read_property(buf, pos, wide):
    """Reads one typed property; returns (value, new position)."""
    t = buf[pos]
    pos += 1
    if t == ord('Y'):
        return struct.unpack_from('<h', buf, pos)[0], pos + 2
    if t == ord('C'):
        return bool(buf[pos]), pos + 1
    if t == ord('I'):
        return struct.unpack_from('<i', buf, pos)[0], pos + 4
    if t == ord('F'):
        return struct.unpack_from('<f', buf, pos)[0], pos + 4
    if t == ord('D'):
        return struct.unpack_from('<d', buf, pos)[0], pos + 8
    if t == ord('L'):
        return struct.unpack_from('<q', buf, pos)[0], pos + 8
    if t in (ord('S'), ord('R')):
        n = struct.unpack_from('<I', buf, pos)[0]
        pos += 4
        raw = buf[pos:pos + n]
        pos += n
        if t == ord('S'):
            return raw.decode('utf-8', 'replace'), pos
        return raw, pos
    # Typed arrays carry a length, an encoding flag and a compressed length.
    if t in (ord('f'), ord('d'), ord('l'), ord('i'), ord('b')):
        count, encoding, comp_len = struct.unpack_from('<III', buf, pos)
        pos += 12
        data = buf[pos:pos + comp_len]
        pos += comp_len
        if encoding == 1:
            data = zlib.decompress(data)
        fmt = {ord('f'): 'f', ord('d'): 'd',
               ord('l'): 'q', ord('i'): 'i', ord('b'): 'b'}[t]
        return list(struct.unpack_from('<%d%s' % (count, fmt), data, 0)), pos
    raise FbxError('unknown property type %r at %d' % (chr(t), pos))


def read_binary(data):
    if not data.startswith(b'Kaydara FBX Binary'):
        raise FbxError('not a binary FBX file')
    version = struct.unpack_from('<I', data, 23)[0]
    wide = version >= 7500
    pos = 27

    def read_nodes(pos, end):
        nodes = []
        null_size = 25 if wide else 13
        while pos < end:
            if wide:
                end_offset, num_props, prop_len = struct.unpack_from('<QQQ', data, pos)
                pos += 24
            else:
                end_offset, num_props, prop_len = struct.unpack_from('<III', data, pos)
                pos += 12
            name_len = data[pos]
            pos += 1
            if end_offset == 0:
                return nodes, pos
            name = data[pos:pos + name_len].decode('ascii', 'replace')
            pos += name_len
            props = []
            for _ in range(num_props):
                value, pos = _read_property(data, pos, wide)
                props.append(value)
            children, pos = read_nodes(pos, end_offset - null_size)
            nodes.append(Node(name, props, children))
            pos = end_offset
        return nodes, pos

    nodes, _ = read_nodes(pos, len(data))
    if not nodes or nodes[0].name != 'FBXHeaderExtension':
        # Some exporters omit the header block; the object list is what matters.
        pass
    return nodes


# ---------------------------------------------------------------- ascii parsing

def read_ascii(text):
    """Minimal ASCII FBX parser: enough for object geometry and transforms."""
    # Replace the array literals with placeholders so a simple token walk works.
    arrays = []

    def stash(match):
        body = match.group(1)
        parts = body.split(',')
        vals = [float(p) for p in parts if p.strip()]
        arrays.append(vals)
        return ' 0 '  # placeholder, replaced below by index lookup

    import re
    text = re.sub(r'\*(\d+)\s*\{([^}]*)\}', stash, text)

    tokens = re.findall(r'[A-Za-z_][A-Za-z0-9_.:]*|"[^"]*"|[-+0-9.eE]+|[{}\[\],]', text)
    pos = [0]

    def parse_nodes(depth=0):
        nodes = []
        while pos[0] < len(tokens):
            tok = tokens[pos[0]]
            if tok == '}':
                pos[0] += 1
                return nodes
            if tok in ('{', ',', ']'):
                pos[0] += 1
                continue
            if tok == '[':
                # begin a nested model block (Models: ... { ... })
                pos[0] += 1
                continue
            name = tok
            pos[0] += 1
            if pos[0] < len(tokens) and tokens[pos[0]] == ':':
                pos[0] += 1
            props = []
            while pos[0] < len(tokens) and tokens[pos[0]] not in ('{', '}', ','):
                t = tokens[pos[0]]
                if t.startswith('"'):
                    props.append(t.strip('"'))
                else:
                    try:
                        props.append(float(t))
                    except ValueError:
                        props.append(t)
                pos[0] += 1
                if pos[0] < len(tokens) and tokens[pos[0]] == ',':
                    pos[0] += 1
            children = []
            if pos[0] < len(tokens) and tokens[pos[0]] == '{':
                pos[0] += 1
                children = parse_nodes(depth + 1)
            nodes.append(Node(name, props, children))
            if pos[0] < len(tokens) and tokens[pos[0]] == '}':
                pos[0] += 1
                return nodes
        return nodes

    return parse_nodes()


# ------------------------------------------------------------------- geometry

def _transform_props(node):
    """
    Finds Lcl Translation/Rotation/Scaling however the exporter nested them.

    <p>FBX has no single spelling for this. Both shapes below appear in the
    wild, and packs authored years apart mix them:</p>
    <ul>
      <li>{@code Model > Properties70 > P "Lcl Translation" ...}</li>
      <li>{@code Model > Lcl Translation ...} directly</li>
    </ul>
    """
    found = {}
    holders = [node] + node.find_all('Properties70')
    for holder in holders:
        for child in holder.children:
            key = child.name
            # Properties70 uses P nodes whose first payload is the name.
            if key == 'P' and child.props:
                key = str(child.get(0))
                values = child.props[4:]
            else:
                values = child.props
            if key in ('Lcl Translation', 'Lcl Rotation', 'Lcl Scaling') and key not in found:
                found[key] = values[:3]
    return found


def _matrix_from(node):
    """Lcl Translation/Rotation/Scaling composed into a 4x4, or None."""
    if node is None:
        return None
    props = _transform_props(node)
    t = props.get('Lcl Translation')
    r = props.get('Lcl Rotation')
    sc = props.get('Lcl Scaling')
    if t is None and sc is None and r is None:
        return None
    tx, ty, tz = (tuple(t) if t and len(t) >= 3 else (0.0, 0.0, 0.0))
    rx, ry, rz = (tuple(r) if r and len(r) >= 3 else (0.0, 0.0, 0.0))
    sx, sy, sz = (tuple(sc) if sc and len(sc) >= 3 else (1.0, 1.0, 1.0))
    import math
    rx, ry, rz = (math.radians(v) for v in (rx, ry, rz))
    cx, sxx = math.cos(rx), math.sin(rx)
    cy, syy = math.cos(ry), math.sin(ry)
    cz, szz = math.cos(rz), math.sin(rz)
    # FBX default rotation order is XYZ, applied as R = Rz * Ry * Rx.
    m00 = cy * cz
    m01 = sxx * syy * cz - cx * szz
    m02 = cx * syy * cz + sxx * szz
    m10 = cy * szz
    m11 = sxx * syy * szz + cx * cz
    m12 = cx * syy * szz - sxx * cz
    m20 = -syy
    m21 = sxx * cy
    m22 = cx * cy
    return [m00 * sx, m01 * sy, m02 * sz, tx,
            m10 * sx, m11 * sy, m12 * sz, ty,
            m20 * sx, m21 * sy, m22 * sz, tz,
            0.0, 0.0, 0.0, 1.0]


def _apply(m, x, y, z):
    return (m[0] * x + m[1] * y + m[2] * z + m[3],
            m[4] * x + m[5] * y + m[6] * z + m[7],
            m[8] * x + m[9] * y + m[10] * z + m[11])


def _apply_dir(m, x, y, z):
    return (m[0] * x + m[1] * y + m[2] * z,
            m[4] * x + m[5] * y + m[6] * z,
            m[8] * x + m[9] * y + m[10] * z)


def _geometry_from(geom_node, transform):
    """Pulls positions/normals/polygons out of one Geometry node."""
    verts = geom_node.find('Vertices')
    if verts is None or not verts.props:
        return None
    raw = verts.props[0]
    positions = [(raw[i], raw[i + 1], raw[i + 2]) for i in range(0, len(raw) - 2, 3)]

    idx_node = geom_node.find('PolygonVertexIndex')
    if idx_node is None or not idx_node.props:
        return None
    raw_idx = idx_node.props[0]

    nrm_node = geom_node.find('Normals')
    normals = None
    if nrm_node is not None and nrm_node.props:
        rn = nrm_node.props[0]
        normals = [(rn[i], rn[i + 1], rn[i + 2]) for i in range(0, len(rn) - 2, 3)]

    # FBX polygons are stored as a flat list where the last index of each face
    # is negated (one's complement). Triangulate fans on the way out.
    faces = []
    current = []
    for v in raw_idx:
        if v < 0:
            current.append(~v)
            faces.append(current)
            current = []
        else:
            current.append(v)
    if len(current) >= 3:
        faces.append(current)

    out_pos = []
    out_nrm = []
    m = transform or [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
    for face in faces:
        for k in range(1, len(face) - 1):
            for vi in (face[0], face[k], face[k + 1]):
                if vi < 0 or vi >= len(positions):
                    continue
                px, py, pz = positions[vi]
                out_pos.append(_apply(m, px, py, pz))
                if normals and vi < len(normals):
                    nx, ny, nz = normals[vi]
                    out_nrm.append(_apply_dir(m, nx, ny, nz))
                else:
                    out_nrm.append((0.0, 1.0, 0.0))
    return out_pos, out_nrm


def _walk(nodes, out):
    for n in nodes:
        if n.name in ('Objects',):
            out.extend(n.children)
        else:
            _walk(n.children, out)


def read_fbx(path):
    """
    Returns (positions, normals) as flat float lists for the whole file.

    Every mesh in the file is merged. A "Mountain" pack usually ships as one
    mountain per FBX, so merging is the right default; separate files stay
    separate because the bake takes one model per file.
    """
    with open(path, 'rb') as f:
        data = f.read()
    if data.startswith(b'Kaydara FBX Binary'):
        roots = read_binary(data)
    else:
        roots = read_ascii(data.decode('utf-8', 'replace'))

    objects = []
    _walk(roots, objects)

    # Map geometry id -> transform, so instances placed with Lcl Translation
    # land in the right spot instead of all stacking at the origin.
    model_by_geom = {}
    transforms = {}
    for n in objects:
        if n.name == 'Model':
            uid = n.get(0)
            t = _matrix_from(n)
            transforms[uid] = t
            for c in n.children:
                if c.name == 'Geometry':
                    gid = c.get(0)
                    if gid is not None:
                        model_by_geom[gid] = uid

    all_pos = []
    all_nrm = []
    for n in objects:
        if n.name != 'Geometry':
            continue
        gid = n.get(0)
        transform = transforms.get(model_by_geom.get(gid))
        got = _geometry_from(n, transform)
        if got is None:
            continue
        all_pos.extend(got[0])
        all_nrm.extend(got[1])

    if not all_pos:
        raise FbxError('no triangle geometry found in %s' % path)
    # Flatten: the bake pipeline wants one float list for positions and one for
    # normals, not a list of triples.
    out_p = []
    out_n = []
    for p in all_pos:
        out_p.extend(p)
    for n in all_nrm:
        out_n.extend(n)
    return out_p, out_n


if __name__ == '__main__':
    import sys
    for p in sys.argv[1:]:
        pos, nrm = read_fbx(p)
        print('%s: %d triangles, %d verts' % (p, len(pos) // 3, len(pos)))
