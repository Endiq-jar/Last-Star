#!/usr/bin/env python3
"""Validate a DCC-neutral weighted-skin JSON export and compile it to LSSKIN v1.

Input is intended to be produced by a Blender/exporter adapter. It carries a
parent-first skeleton, inverse-bind matrices, indexed triangle geometry with up
to four joint influences per vertex, and sampled local-matrix clip frames.
"""
import json
import math
import os
import sys


def finite_number(value, label):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError(f"{label} must be a finite number")
    return float(value)


def vector(value, count, label):
    if not isinstance(value, list) or len(value) != count:
        raise ValueError(f"{label} must contain {count} numbers")
    return [finite_number(v, label) for v in value]


def compile_asset(source):
    if not isinstance(source, dict):
        raise ValueError("root must be a JSON object")
    if source.get("format") != "last-star-skinned" or source.get("version") != 1:
        raise ValueError("expected last-star-skinned JSON version 1")
    bones = source.get("bones")
    if not isinstance(bones, list) or not 1 <= len(bones) <= 128:
        raise ValueError("bone count must be 1..128")
    for i, bone in enumerate(bones):
        if not isinstance(bone, dict) or not isinstance(bone.get("name"), str) \
                or not bone["name"] or any(ch.isspace() for ch in bone["name"]):
            raise ValueError(f"bone {i} needs a single-token name")
        parent = bone.get("parent")
        if isinstance(parent, bool) or not isinstance(parent, int) or parent < -1 or parent >= i:
            raise ValueError(f"bone {i} parent must be -1 or precede the child")
        vector(bone.get("inverse_bind"), 16, f"bone {i} inverse_bind")

    vertices = source.get("vertices")
    if not isinstance(vertices, list) or not 1 <= len(vertices) <= 65535:
        raise ValueError("vertex count must be 1..65535")
    cooked_vertices = []
    for vi, vertex in enumerate(vertices):
        p = vector(vertex.get("position"), 3, f"vertex {vi} position")
        n = vector(vertex.get("normal"), 3, f"vertex {vi} normal")
        c = vector(vertex.get("color", [1, 1, 1, 1]), 4, f"vertex {vi} color")
        influences = vertex.get("influences")
        if not isinstance(influences, list) or not 1 <= len(influences) <= 4:
            raise ValueError(f"vertex {vi} must have 1..4 influences")
        pairs = []
        for joint, weight in influences:
            if isinstance(joint, bool) or not isinstance(joint, int) or not 0 <= joint < len(bones):
                raise ValueError(f"vertex {vi} references invalid joint {joint}")
            weight = finite_number(weight, f"vertex {vi} weight")
            if weight < 0:
                raise ValueError(f"vertex {vi} has a negative weight")
            pairs.append((joint, weight))
        total = sum(weight for _, weight in pairs)
        if total <= 1e-8:
            raise ValueError(f"vertex {vi} has zero total weight")
        pairs = [(joint, weight / total) for joint, weight in pairs]
        pairs.extend([(0, 0.0)] * (4 - len(pairs)))
        cooked_vertices.append(p + n + c + [v for pair in pairs for v in pair])

    indices = source.get("indices")
    if not isinstance(indices, list) or len(indices) < 3 or len(indices) % 3:
        raise ValueError("indices must describe one or more complete triangles")
    if any(isinstance(i, bool) or not isinstance(i, int) or i < 0 or i >= len(vertices) for i in indices):
        raise ValueError("index out of range")

    clips = source.get("clips", [])
    if not isinstance(clips, list) or len(clips) > 256:
        raise ValueError("clip count must be 0..256")
    out = ["LSSKIN 1", f"bones {len(bones)}"]
    for bone in bones:
        out.append("bone " + bone["name"] + " " + str(bone["parent"]) + " "
                   + " ".join(str(v) for v in vector(bone["inverse_bind"], 16, "inverse_bind")))
    out.append(f"vertices {len(cooked_vertices)}")
    out.extend(" ".join(str(v) for v in vertex) for vertex in cooked_vertices)
    out.append(f"indices {len(indices)}")
    out.append(" ".join(str(i) for i in indices))
    out.append(f"clips {len(clips)}")
    for clip in clips:
        if not isinstance(clip, dict):
            raise ValueError("clip entries must be objects")
        name = clip.get("name")
        duration = finite_number(clip.get("duration"), f"clip {name} duration")
        frames = clip.get("frames")
        if not isinstance(name, str) or not name or any(ch.isspace() for ch in name) or duration <= 0:
            raise ValueError("clip needs a single-token name and positive duration")
        if not isinstance(frames, list) or not 1 <= len(frames) <= 10000:
            raise ValueError(f"clip {name} must have 1..10000 frames")
        out.append(f"clip {name} {duration} {len(frames)}")
        last = -1.0
        for frame in frames:
            if not isinstance(frame, dict):
                raise ValueError(f"clip {name} frames must be objects")
            t = finite_number(frame.get("time"), f"clip {name} frame time")
            mats = frame.get("local_matrices")
            if t < 0 or t > duration or t <= last or not isinstance(mats, list) or len(mats) != len(bones):
                raise ValueError(f"clip {name} frame keys must increase and contain one matrix per bone")
            last = t
            out.append("frame " + str(t))
            for matrix in mats:
                out.append(" ".join(str(v) for v in vector(matrix, 16, f"clip {name} local matrix")))
    return "\n".join(out) + "\n"


def main(argv):
    if len(argv) != 3:
        print("usage: import_skinned.py input.json output.lsskin", file=sys.stderr)
        return 2
    try:
        with open(argv[1], "r", encoding="utf-8") as f:
            data = json.load(f)
        output = compile_asset(data)
        os.makedirs(os.path.dirname(os.path.abspath(argv[2])), exist_ok=True)
        with open(argv[2], "w", encoding="utf-8", newline="\n") as f:
            f.write(output)
        print(f"compiled {len(data['bones'])} bones, {len(data['vertices'])} vertices, "
              f"{len(data['indices']) // 3} triangles, {len(data.get('clips', []))} clips")
        return 0
    except (OSError, ValueError, TypeError, KeyError, json.JSONDecodeError) as e:
        print(f"import_skinned: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
