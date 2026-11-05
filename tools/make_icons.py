#!/usr/bin/env python3
"""Generates the Last Star launcher icons and marketing art.

Pure standard library (struct + zlib), so it runs anywhere with no Pillow.
The icon is drawn with 4x supersampling for clean edges: a night-sky gradient,
a ring of light and the five-pointed shard the game is named after.

Usage:  python3 tools/make_icons.py
"""
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DOCS = os.path.join(ROOT, "docs")


def write_png(path, width, height, pixels):
    """pixels: bytearray of RGBA rows."""
    raw = bytearray()
    stride = width * 4
    for y in range(height):
        raw.append(0)  # filter type 0
        raw.extend(pixels[y * stride:(y + 1) * stride])

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        out += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return out

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", header)
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def lerp(a, b, t):
    return a + (b - a) * t


def clamp01(v):
    return 0.0 if v < 0 else (1.0 if v > 1 else v)


def smoothstep(e0, e1, x):
    t = clamp01((x - e0) / (e1 - e0 + 1e-9))
    return t * t * (3 - 2 * t)


def star_points(cx, cy, outer, inner, points=5, rotation=-math.pi / 2):
    pts = []
    for i in range(points * 2):
        ang = rotation + i * math.pi / points
        r = outer if i % 2 == 0 else inner
        pts.append((cx + math.cos(ang) * r, cy + math.sin(ang) * r))
    return pts


def point_in_polygon(x, y, poly):
    inside = False
    n = len(poly)
    j = n - 1
    for i in range(n):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi):
            inside = not inside
        j = i
    return inside


def render_icon(size):
    ss = 4  # supersampling factor
    w = h = size * ss
    buf = bytearray(w * h * 4)

    cx = w * 0.5
    cy = h * 0.5
    # Star geometry
    outer = w * 0.36
    inner = outer * 0.42
    poly = star_points(cx, cy * 0.98, outer, inner)

    # Small companions (shards being restored)
    sparks = [
        (cx - w * 0.33, cy - h * 0.30, w * 0.035),
        (cx + w * 0.32, cy - h * 0.22, w * 0.026),
        (cx + w * 0.28, cy + h * 0.31, w * 0.020),
        (cx - w * 0.30, cy + h * 0.26, w * 0.016),
    ]

    for y in range(h):
        for x in range(w):
            nx = (x - cx) / (w * 0.5)
            ny = (y - cy) / (h * 0.5)
            r = math.sqrt(nx * nx + ny * ny)

            # Background: deep night gradient, brighter toward the top.
            t = clamp01(y / h)
            bg_r = lerp(0.055, 0.020, t)
            bg_g = lerp(0.085, 0.030, t)
            bg_b = lerp(0.190, 0.075, t)

            # Radial glow behind the star.
            glow = max(0.0, 1.0 - r * 0.95)
            glow = glow ** 2.4
            bg_r += glow * 0.42
            bg_g += glow * 0.30
            bg_b += glow * 0.10

            # Ring of light.
            ring_d = abs(r - 0.62)
            ring = max(0.0, 1.0 - ring_d / 0.028)
            bg_r += ring * 0.30
            bg_g += ring * 0.26
            bg_b += ring * 0.30

            # Sparse stars.
            hsh = math.sin((x * 12.9898 + y * 78.233) * 0.001) * 43758.5453
            frac = hsh - math.floor(hsh)
            if frac > 0.9993:
                bg_r += 0.75
                bg_g += 0.78
                bg_b += 0.9

            for sx, sy, sr in sparks:
                d = math.hypot(x - sx, y - sy)
                if d < sr * 2.4:
                    k = max(0.0, 1.0 - d / (sr * 2.4)) ** 2
                    bg_r += k * 0.9
                    bg_g += k * 0.72
                    bg_b += k * 0.35

            inside = point_in_polygon(x, y, poly)
            if inside:
                # Gold gradient across the star, warm rim.
                grad = clamp01((y - (cy - outer)) / (outer * 2))
                sr_ = lerp(1.0, 0.92, grad)
                sg_ = lerp(0.90, 0.66, grad)
                sb_ = lerp(0.55, 0.20, grad)
                bg_r = sr_
                bg_g = sg_
                bg_b = sb_
            else:
                # Contact glow just outside the silhouette.
                near = 0.0
                for i in range(len(poly)):
                    x1, y1 = poly[i]
                    x2, y2 = poly[(i + 1) % len(poly)]
                    dx, dy = x2 - x1, y2 - y1
                    ll = dx * dx + dy * dy
                    tt = 0.0 if ll == 0 else max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / ll))
                    px, py = x1 + tt * dx, y1 + tt * dy
                    d = math.hypot(x - px, y - py)
                    near = max(near, max(0.0, 1.0 - d / (w * 0.045)))
                bg_r += near * 0.35
                bg_g += near * 0.26
                bg_b += near * 0.08

            i = (y * w + x) * 4
            buf[i] = int(clamp01(bg_r) * 255)
            buf[i + 1] = int(clamp01(bg_g) * 255)
            buf[i + 2] = int(clamp01(bg_b) * 255)
            buf[i + 3] = 255

    # Downsample.
    out = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            ar = ag = ab = 0
            for dy in range(ss):
                for dx in range(ss):
                    i = ((y * ss + dy) * w + (x * ss + dx)) * 4
                    ar += buf[i]
                    ag += buf[i + 1]
                    ab += buf[i + 2]
            n = ss * ss
            o = (y * size + x) * 4
            out[o] = ar // n
            out[o + 1] = ag // n
            out[o + 2] = ab // n
            out[o + 3] = 255
    return out


def render_icon_round(size):
    """Circular variant (adaptive icon foreground safe zone)."""
    ss = 4
    w = h = size * ss
    square = render_icon_supersampled(w)
    out = bytearray(size * size * 4)
    cx = cy = w * 0.5
    rad = w * 0.5 - ss
    for y in range(size):
        for x in range(size):
            ar = ag = ab = aa = 0
            for dy in range(ss):
                for dx in range(ss):
                    px = x * ss + dx
                    py = y * ss + dy
                    d = math.hypot(px - cx, py - cy)
                    cov = 1.0 - smoothstep(rad - ss, rad, d)
                    i = (py * w + px) * 4
                    ar += square[i] * cov
                    ag += square[i + 1] * cov
                    ab += square[i + 2] * cov
                    aa += 255 * cov
            n = ss * ss
            o = (y * size + x) * 4
            out[o] = int(ar / n)
            out[o + 1] = int(ag / n)
            out[o + 2] = int(ab / n)
            out[o + 3] = int(aa / n)
    return out


def render_icon_supersampled(w):
    """Same as render_icon but returns the raw supersampled buffer."""
    size = w // 4
    return _render_supersampled(w)


def _render_supersampled(w):
    h = w
    buf = bytearray(w * h * 4)
    cx = w * 0.5
    cy = h * 0.5
    outer = w * 0.36
    inner = outer * 0.42
    poly = star_points(cx, cy, outer, inner)
    for y in range(h):
        for x in range(w):
            nx = (x - cx) / (w * 0.5)
            ny = (y - cy) / (h * 0.5)
            r = math.sqrt(nx * nx + ny * ny)
            t = clamp01(y / h)
            bg_r = lerp(0.055, 0.020, t)
            bg_g = lerp(0.085, 0.030, t)
            bg_b = lerp(0.190, 0.075, t)
            glow = max(0.0, 1.0 - r * 0.95) ** 2.4
            bg_r += glow * 0.42
            bg_g += glow * 0.30
            bg_b += glow * 0.10
            ring = max(0.0, 1.0 - abs(r - 0.62) / 0.028)
            bg_r += ring * 0.30
            bg_g += ring * 0.26
            bg_b += ring * 0.30
            if point_in_polygon(x, y, poly):
                grad = clamp01((y - (cy - outer)) / (outer * 2))
                bg_r = lerp(1.0, 0.92, grad)
                bg_g = lerp(0.90, 0.66, grad)
                bg_b = lerp(0.55, 0.20, grad)
            i = (y * w + x) * 4
            buf[i] = int(clamp01(bg_r) * 255)
            buf[i + 1] = int(clamp01(bg_g) * 255)
            buf[i + 2] = int(clamp01(bg_b) * 255)
            buf[i + 3] = 255
    return buf


DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def main():
    for folder, size in DENSITIES.items():
        path = os.path.join(RES, folder, "ic_launcher.png")
        write_png(path, size, size, render_icon(size))
        print("wrote", os.path.relpath(path, ROOT), f"{size}x{size}")

    # Adaptive icon foreground (108dp canvas) for API 26+.
    for folder, size in DENSITIES.items():
        fg = int(size * 108 / 48)
        path = os.path.join(RES, folder, "ic_launcher_foreground.png")
        write_png(path, fg, fg, render_icon_round(fg))
        print("wrote", os.path.relpath(path, ROOT), f"{fg}x{fg}")

    # Store badge + screenshots folder placeholder art.
    write_png(os.path.join(DOCS, "icon-512.png"), 512, 512, render_icon(512))


if __name__ == "__main__":
    main()
