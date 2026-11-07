#!/usr/bin/env python3
"""
Samples average albedo colour from the Holotna Mountain (Textures) pack and
derives the per-model base colours bake_mountain_pack.py bakes into vertices.

The game has no per-pixel texture sampling (see docs/IMPORTING_MOUNTAINS.md) --
every model is lit by a toon shader over a flat vertex colour. "Texturing" a
model here means picking that colour from the real art instead of a guess.

Source: assets_src/mountain_textures/Mountain (Textures)/Colored/*_ALB.png --
the Colored/ variant is used throughout, not the plain ALB/_SPE set, because
it matches the anime/cel-shaded direction the project calls for (plain ALB is
a desaturated/neutral base meant to be recoloured downstream).

Not every pk_ model has a texture in this pack:
  * pk_mountain, pk_rock2, pk_pebbles are stone with no dedicated map --
    derived from Rock01's sampled colour (same material family).
  * pk_flowers (the second, yellow flower clump) has no "Flowers01" map in
    this pack (only "Flower01") -- derived by hue-rotating the sampled
    Flower01 colour, the same pink-to-yellow relationship the placeholder
    colours already encoded.
  * pk_spell-book is not part of this nature pack at all and is left alone.
"""
import colorsys
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.join(HERE, '..', 'assets_src', 'mountain_textures',
                     'Mountain (Textures)', 'Colored')


def average_colour(path):
    """
    Mean colour of the opaque texels only.

    Foliage cards (leaf/flower/grass/bush) are alpha-cutout RGBA: the RGB
    channel under a fully transparent texel is often black or garbage, since
    nothing ever samples it at render time. Averaging it in would pull every
    plant colour hard toward black, so transparent and near-transparent
    texels are excluded; opaque textures (rock, bark, bridge) have no alpha
    channel and every texel counts as before.
    """
    from PIL import Image
    im = Image.open(path).convert('RGBA').resize((16, 16), Image.LANCZOS)
    px = list(im.getdata())
    r = g = b = n = 0
    for pr, pg, pb, pa in px:
        if pa < 16:
            continue
        # Atlas sheets (e.g. the bridge) pack several UV islands with black
        # gutter between them on a texture that has no alpha channel at all,
        # so the alpha check above doesn't catch it. Near-black texels are
        # excluded here too -- no material in this pack is actually rendered
        # as pure black, so it reads as padding rather than content.
        if pr < 12 and pg < 12 and pb < 12:
            continue
        r += pr / 255.0
        g += pg / 255.0
        b += pb / 255.0
        n += 1
    if n == 0:
        return (0.5, 0.5, 0.5)
    return (r / n, g / n, b / n)


def mix(a, b, t):
    return tuple(a[i] * (1 - t) + b[i] * t for i in range(3))


def scale(c, f):
    return tuple(max(0.0, min(1.0, v * f)) for v in c)


def hue_rotate(c, delta):
    h, s, v = colorsys.rgb_to_hsv(*c)
    h = (h + delta) % 1.0
    return colorsys.hsv_to_rgb(h, s, v)


def main():
    sampled = {}
    for stem in ('Bridge01', 'Bush01', 'Flower01', 'Grass01', 'Leaf01',
                 'Rock01', 'Trunk01'):
        path = os.path.join(PACK, stem + '_ALB.png')
        sampled[stem] = average_colour(path)

    tree = mix(sampled['Trunk01'], sampled['Leaf01'], 0.7)  # canopy dominates
    cool_grey = (0.55, 0.58, 0.66)  # atmospheric-perspective tint for distant rock

    out = {
        'pk_bridge':     sampled['Bridge01'],
        'pk_bush':       sampled['Bush01'],
        'pk_rock':       sampled['Rock01'],
        'pk_grass':      sampled['Grass01'],
        'pk_flower':     sampled['Flower01'],
        'pk_tree':       tree,
        'pk_mountain':   mix(sampled['Rock01'], cool_grey, 0.55),
        'pk_rock2':      scale(sampled['Rock01'], 1.06),
        'pk_pebbles':    scale(sampled['Rock01'], 1.18),
        'pk_flowers':    hue_rotate(sampled['Flower01'], 0.42),
    }

    for name, c in out.items():
        print('%-14s (%.4f, %.4f, %.4f)' % (name, c[0], c[1], c[2]))

    return out


if __name__ == '__main__':
    main()
