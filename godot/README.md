# Last Star — Godot runtime

This folder contains the active GDScript implementation of Last Star's Godot
migration. The active scene is `scenes/last_star.tscn`; the project does not use
C# or another game-engine runtime.

## Current gameplay slice

- Deterministic noise-based terrain streamed in a 7-by-7 chunk window around the
  player, with chunks regenerated from world coordinates as the player travels.
- Per-chunk MultiMesh trees and rocks, a winding footpath, river channel, and a
  ridge settlement near the starting area.
- Third-person movement, jump, sprint, orbit camera, mobile joystick/buttons,
  and desktop keyboard/mouse input.
- Six collectible shards, a simple melee/skill combat loop, and one Warden
  encounter unlocked after collecting the shards.
- Day/night lighting and a local JSON save with SHA-256 validation and
  temporary-file replacement.

This is not yet a feature-complete port of the existing Last Star game. The full
spell corpus, equipment/inventory, companion and quest systems, menu screens,
region variety, character animation, and production Android export are still
unfinished. The finite settlement/river dressing currently only covers the
starting region; terrain itself streams beyond it.

## Run locally

Install the standard Godot 4.7 or newer editor/runtime, then open this folder or
run:

```sh
godot --path godot
```

Controls: WASD to move, Space to jump, left mouse drag to orbit the camera; on
touchscreens, touch and drag anywhere on the left half to move, drag the right
half to look, and use the RUN, SKILL, JUMP, and ATK buttons. Saves are kept in Godot's per-user
`user://last_star_godot.json` location. No network calls or analytics are used.

## Validation status

The GDScript files pass `gdtoolkit` syntax/style checks. This workspace does not
have the Godot executable or Android export templates, so runtime startup,
Android export, performance, and physical-device touch behavior remain
unverified. The Java/OpenGL Android build is still retained separately as the
last tested fallback; its APK has not been replaced by a Godot export.

The MIT-licensed Godot C# exploration project reviewed during migration is
archived under `../upstream/Godot-Adventure-Game/` and is not part of this
runtime. The active Last Star project is licensed under the repository's
[EPL-2.0 license](../LICENSE).
