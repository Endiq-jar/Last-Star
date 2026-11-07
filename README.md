# Last Star

**Last Star is a Godot 4.7 GDScript game.** The project now lives at the repository root: open this folder directly in Godot, where `project.godot` is located. There is no extra project subfolder to enter.

The game is an original offline fantasy adventure with streamed Terrain3D ground, an alpine gravel trail and timber lodge, distant faceted mountain ridges, textured conifer and broadleaf tree assets, meadow vegetation, touch controls, shard collection, a Warden encounter, day/night lighting, and a local save. It uses no network services, ads, or analytics.

## Open and run

1. Open Godot 4.7.2 or newer.
2. Import/open this repository folder (the one containing `project.godot`).
3. Let Godot finish importing the project and the Terrain3D extension.
4. Run the project using the main scene in `scenes/last_star.tscn`.

Desktop controls: WASD to move, Space to jump, and mouse drag to orbit the camera. Touch controls are shown in-game. Save data is stored under Godot's `user://` directory.

## Terrain3D add-on

The MIT-licensed Terrain3D 1.1.0 Godot add-on is installed at `addons/terrain_3d/` and enabled as an editor plug-in. The main scene now creates a Terrain3D node, loads its region data/material/texture assets from `assets/terrain3d/`, and assigns the player as its clipmap and collision target. The bundled sample height regions are offset so the playable village and starting area sit over the map. Android ARM libraries are included. The GDScript terrain remains as a fallback if the extension cannot load.

The add-on's MIT license is retained at `addons/terrain_3d/LICENSE.txt`. Original game code is under the repository's EPL-2.0 license. See `CREDITS.md` for third-party notices.

## Project layout

- `project.godot` — root Godot project settings and main scene selection
- `scenes/` — playable scene
- `scripts/laststar/` — GDScript world, player, HUD, and gameplay code
- `addons/terrain_3d/` — Terrain3D editor/runtime extension
- `export_presets.cfg` — Godot export configuration

## Automatic Android build

GitHub Actions runs GDScript format/lint checks, imports the project with Godot 4.7.2, performs a headless startup check, and exports a universal ARMv7 plus ARM64 Android debug APK on pushes, pull requests, or manual dispatch. Download `LastStar-Android-debug` from the completed workflow run's **Artifacts** section. The APK is a debug build, not a release-signed store package.

The development workspace has no Godot executable, so the new Android export workflow has not yet been run here. Use its first GitHub Actions result to verify the extension and export on the CI runner.
