# Godot migration prototype

## Decision and scope

The user selected Godot as the direction for a possible engine migration. The current shipped Android game remains the Java/OpenGL ES application under `app/`; it is preserved as a working reference and fallback. A separate, MIT-licensed Godot prototype is staged in `godot/` so migration can proceed without silently replacing a working app with an unverified rewrite.

## Selected base

- Repository: [ProWille/Godot-Adventure-Game](https://github.com/ProWille/Godot-Adventure-Game)
- Snapshot: `774112bc3d689c2690f8b40a3955f321dfc51542` (2026-09-19)
- License: MIT; original `godot/LICENSE` is retained.
- Upstream stack: Godot 4.7.2 Mono / C# and .NET 8+ (Android target currently requests .NET 9).
- Relevant systems: deterministic chunked terrain, six biomes, water, shader-driven grass, biome-aware decoration, minimap, and debug HUD.
- Important limitation: its tree/plant/stone assets are simple placeholders, not a complete Genshin-scale art set or finished RPG.

The selected project was chosen for terrain and world-streaming fit, not because it contains Genshin assets. No Genshin code, characters, names, maps, music, or other protected game content is included.

## Existing repository candidates reviewed

| Repository | License / stack | Decision |
|---|---|---|
| [AnyRPG/AnyRPGCore](https://github.com/AnyRPG/AnyRPGCore) | MIT; Unity C#; source snapshot already under `upstream/AnyRPGCore/` | RPG systems are useful study material, but Unity `MonoBehaviour` code and Unity packages cannot be dropped into Godot as-is. Port only individual behavior concepts when needed. |
| [ProWille/Godot-Adventure-Game](https://github.com/ProWille/Godot-Adventure-Game) | MIT; Godot C# | Selected migration base and copied to `godot/`. |
| [ice-wocker/GunFire](https://github.com/ice-wocker/GunFire) | MIT; Android Java/OpenGL ES 2; small shooter | Same broad platform, but it contributes no open-world RPG content and would duplicate the current renderer. Not merged. |
| [Jay22K/unity-Third-person-controller-android](https://github.com/Jay22K/unity-Third-person-controller-android) | GPL-3.0; Unity package; copied under `upstream/` | Not incorporated: incompatible engine, and distributing a derivative would impose GPL obligations on the affected work. |
| [nikunjsingh/Android_Unity3D_OpenWorld](https://github.com/nikunjsingh/Android_Unity3D_OpenWorld) | No license declared; Unity 5.6 | Not incorporated: absent a license, redistribution and adaptation rights are not granted. |

This is a focused review of repositories relevant to this game's engine and genre, not an exhaustive review of every GitHub repository. Search results containing Genshin fan games or game data were not used as assets or code.

## Migration status

**Godot GDScript vertical slice is now the active scene in `godot/project.godot`; full feature parity and Android export are not complete.** The scene builds an original stylized meadow, continuous trail, multimesh forest, ridge town, water, a controllable third-person hero, one simple enemy, touch controls, attack/skill actions, a day-light rotation, and a local position save. The imported C# terrain project remains as reference content but is not the active scene. This is a finite prototype map, not the upstream project's endless streamed terrain and not a complete Genshin-scale RPG.

Still to port: Last Star's full quest, inventory, spell, progression, companion, save-versioning, menu, and combat systems; robust Android touch/layout behavior; character animation; and mobile performance tuning. The exact UI palette, offline operation, and zero-permission requirement remain constraints. Keep the existing native APK available until a Godot Android APK builds and passes equivalent tests on a device.

## Validation constraints

No Godot executable or .NET SDK is installed in this build workspace. The new GDScript files pass the `gdtoolkit` parser/linter, but no Godot runtime, Android export, or device build could be run here. The existing native app's tests and APK workflow remain independent; passing those tests does not validate the Godot project.
