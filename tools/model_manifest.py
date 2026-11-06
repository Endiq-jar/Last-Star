"""
Single source of truth for the world's imported models.

Each entry is (name, pack, role, targetSizeMetres).

`targetSize` is the size the model should be scaled to in game, measured on the
model's largest axis, so every asset arrives as a metric prop the placement code
can treat uniformly. It is written into the baked buffer's per-model header.
"""

# Role ids, mirrored by com.laststar.game.gl.ModelLibrary.
ROLES = {
    'TREE': 0, 'ROCK': 1, 'PLANT': 2, 'GRASS': 3, 'FLOWER': 4, 'MUSHROOM': 5,
    'LOG': 6, 'STUMP': 7, 'CLIFF': 8, 'PATH': 9, 'WALL': 10, 'TOWER': 11,
    'ROOF': 12, 'GATE': 13, 'FLAG': 14, 'TENT': 15, 'BARREL': 16,
    'CRATE': 17, 'FIRE': 18, 'BRIDGE': 19, 'HILL': 20, 'DOOR': 21, 'FENCE': 22,
}

MODELS = [
    # ------------------------------------------------------------- forest
    ('tree_pineTallA_detailed', 'nature',   'TREE',  9.0),
    ('tree_pineTallB_detailed', 'nature',   'TREE', 10.5),
    ('tree_pineRoundC',         'nature',   'TREE',  7.5),
    ('tree_oak',                'nature',   'TREE',  7.0),
    ('tree_detailed',           'nature',   'TREE',  7.2),
    ('tree_default',            'nature',   'TREE',  8.4),
    ('tree_simple',             'nature',   'TREE',  7.8),
    ('tree_thin',               'nature',   'TREE',  7.2),
    ('tree_fat',                'nature',   'TREE',  5.8),
    ('tree_blocks',             'nature',   'TREE',  6.4),
    # ------------------------------------------------------------- ground
    ('rock_largeA',             'nature',   'ROCK',  1.7),
    ('rock_largeC',             'nature',   'ROCK',  1.9),
    ('rock_largeE',             'nature',   'ROCK',  2.1),
    ('rock_tallC',              'nature',   'ROCK',  2.2),
    ('rock_tallF',              'nature',   'ROCK',  1.8),
    ('stone_largeA',            'nature',   'ROCK',  1.6),
    ('stone_largeD',            'nature',   'ROCK',  1.8),
    ('stone_smallA',            'nature',   'ROCK',  0.9),
    ('stone_tallB',             'nature',   'ROCK',  2.0),
    ('rocks-large',             'castle',   'ROCK',  2.8),
    ('rocks-small',             'castle',   'ROCK',  1.6),
    ('ground-hills',            'castle',   'HILL',  7.0),
    # ------------------------------------------------------------- cliffs
    ('cliff_block_stone',       'nature',   'CLIFF', 5.0),
    ('cliff_top_stone',         'nature',   'CLIFF', 5.0),
    ('cliff_half_stone',        'nature',   'CLIFF', 4.4),
    ('cliff_waterfall_stone',   'nature',   'CLIFF', 5.0),
    ('cliff_waterfallTop_stone', 'nature',  'CLIFF', 5.0),
    # ------------------------------------------------------------- plants
    ('plant_bush',              'nature',   'PLANT', 1.0),
    ('plant_bushLarge',         'nature',   'PLANT', 1.2),
    ('plant_bushTriangle',      'nature',   'PLANT', 1.1),
    ('plant_flatTall',          'nature',   'PLANT', 0.9),
    ('mushroom_red',            'nature',   'MUSHROOM', 0.5),
    ('mushroom_redGroup',       'nature',   'MUSHROOM', 0.7),
    ('flower_purpleA',          'nature',   'FLOWER', 0.6),
    ('flower_redB',             'nature',   'FLOWER', 0.65),
    ('flower_yellowB',          'nature',   'FLOWER', 0.6),
    ('grass',                   'nature',   'GRASS',  0.9),
    ('grass_large',             'nature',   'GRASS',  1.0),
    ('grass_leafsLarge',        'nature',   'GRASS',  0.9),
    # ------------------------------------------------------------- timber
    ('log',                     'nature',   'LOG',    1.4),
    ('log_large',               'nature',   'LOG',    1.8),
    ('log_stack',               'nature',   'LOG',    1.6),
    ('stump_round',             'nature',   'STUMP',  0.9),
    ('tree-log',                'survival', 'LOG',    3.0),
    ('tree-log-small',          'survival', 'LOG',    2.4),
    # ------------------------------------------------------------- paths
    ('bridge_stone',            'nature',   'BRIDGE', 5.0),
    ('path_stoneCircle',        'nature',   'PATH',   3.0),
    ('campfire_stones',         'nature',   'FIRE',   2.0),
    ('campfire_logs',           'nature',   'FIRE',   1.2),
    # ------------------------------------------------------------- castle
    ('tower-square',            'castle',   'TOWER',  7.0),
    ('tower-square-mid',        'castle',   'TOWER',  6.0),
    ('tower-square-top-roof',   'castle',   'ROOF',   6.0),
    ('tower-square-roof',       'castle',   'ROOF',   9.0),
    ('tower-hexagon-base',      'castle',   'TOWER',  7.0),
    ('tower-hexagon-roof',      'castle',   'ROOF',   5.0),
    ('wall',                    'castle',   'WALL',   4.0),
    ('wall-half',               'castle',   'WALL',   4.0),
    ('wall-corner',             'castle',   'WALL',   4.0),
    ('wall-corner-half',        'castle',   'WALL',   4.0),
    ('wall-doorway',            'castle',   'WALL',   4.0),
    ('wall-pillar',             'castle',   'WALL',   4.0),
    ('gate',                    'castle',   'GATE',   5.0),
    ('metal-gate',              'castle',   'GATE',   4.5),
    ('door',                    'castle',   'DOOR',   2.4),
    ('flag',                    'castle',   'FLAG',   5.0),
    ('flag-banner-long',        'castle',   'FLAG',   6.0),
    # ------------------------------------------------------------- camp
    ('tent',                    'survival', 'TENT',   2.8),
    ('tent-canvas',             'survival', 'TENT',   2.8),
    ('barrel',                  'survival', 'BARREL', 1.2),
    ('barrel-open',             'survival', 'BARREL', 1.2),
    ('box',                     'survival', 'CRATE',  0.9),
    ('chest',                   'survival', 'CRATE',  1.1),
    ('campfire-pit',            'survival', 'FIRE',   1.6),
    ('bucket',                  'survival', 'BARREL', 0.55),
    ('bottle',                  'survival', 'CRATE',  0.38),
    ('bottle-large',            'survival', 'CRATE',  0.48),
    # ------------------------------------------------------- fantasy town
    ('wall-wood',               'fantasy-town', 'WALL', 3.2),
    ('wall-wood-door',          'fantasy-town', 'WALL', 3.2),
    ('wall-window-glass',       'fantasy-town', 'WALL', 3.2),
    ('wall-doorway-square',     'fantasy-town', 'DOOR', 3.2),
    ('roof-point',              'fantasy-town', 'ROOF', 4.4),
    ('roof-window',             'fantasy-town', 'ROOF', 4.4),
    ('fence-gate',              'fantasy-town', 'FENCE', 2.0),
    ('hedge-gate',              'fantasy-town', 'FENCE', 1.8),
]

# Packs whose GLBs reference an external colormap atlas (Kenney's textured
# kits). Everything else carries flat baseColorFactor materials.
COLORMAP_PACKS = ('castle', 'survival', 'fantasy-town')

NAMES = [m[0] for m in MODELS]
