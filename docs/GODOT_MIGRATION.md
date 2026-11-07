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

**Staged, not migrated or Android-verified.** The Godot base's display name and export filename have been made Last Star-specific. The project still runs the upstream terrain-exploration scene; the original Last Star gameplay systems are not yet ported. The current app's offline/no-permission constraint and exact UI palette remain requirements for any future mobile port.

Recommended port order: Android touch movement and camera; original player/combat loop; persistent save/day-night/world state; quests and UI; then replace placeholder flora and settlement models with assets whose redistribution terms are explicit. Keep the native APK available until the Godot APK builds and passes equivalent playthrough tests.

## Validation constraints

No Godot or .NET SDK is installed in this build workspace, so the staged C# project has not been compiled or exported. Do not describe it as a working Android build until that toolchain is available and the Godot Android export has been tested on a device. The existing native app's tests and APK workflow are independent of this prototype.
