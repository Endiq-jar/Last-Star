# Godot migration prototype

## Decision and scope

Godot GDScript is now the active game project under `godot/`. The previous Java/OpenGL Android application remains in the repository and as a verified fallback until a Godot Android export can be built and tested; the packaged APK has not yet changed engines.

## Repository review

- Reviewed: [ProWille/Godot-Adventure-Game](https://github.com/ProWille/Godot-Adventure-Game), snapshot `774112bc3d689c2690f8b40a3955f321dfc51542` (2026-09-19), MIT.
- The complete MIT-licensed source snapshot is archived under `upstream/Godot-Adventure-Game/` for reference only; its C# code is not part of the active runtime.
- Its Godot 4.7.2 Mono project demonstrates chunk terrain, six biomes, water, shader grass, decoration, and a minimap. Its placeholder assets are not a complete RPG art set.
- The active Last Star runtime is original GDScript and uses the Godot engine directly; no other game-engine base or Genshin code/assets are part of the runtime.

## Existing repository candidates reviewed

| Repository | License / stack | Decision |
|---|---|---|
| [AnyRPG/AnyRPGCore](https://github.com/AnyRPG/AnyRPGCore) | MIT; Unity C#; source snapshot already under `upstream/AnyRPGCore/` | RPG systems are useful study material, but Unity `MonoBehaviour` code and Unity packages cannot be dropped into Godot as-is. Port only individual behavior concepts when needed. |
| [ProWille/Godot-Adventure-Game](https://github.com/ProWille/Godot-Adventure-Game) | MIT; Godot C# | Reviewed; full source preserved in `upstream/Godot-Adventure-Game/`, but not used as the Last Star runtime base. |
| [ice-wocker/GunFire](https://github.com/ice-wocker/GunFire) | MIT; Android Java/OpenGL ES 2; small shooter | Same broad platform, but it contributes no open-world RPG content and would duplicate the current renderer. Not merged. |
| [Jay22K/unity-Third-person-controller-android](https://github.com/Jay22K/unity-Third-person-controller-android) | GPL-3.0; Unity package; copied under `upstream/` | Not incorporated: incompatible engine, and distributing a derivative would impose GPL obligations on the affected work. |
| [nikunjsingh/Android_Unity3D_OpenWorld](https://github.com/nikunjsingh/Android_Unity3D_OpenWorld) | No license declared; Unity 5.6 | Not incorporated: absent a license, redistribution and adaptation rights are not granted. |

This is a focused review of repositories relevant to this game's engine and genre, not an exhaustive review of every GitHub repository. Search results containing Genshin fan games or game data were not used as assets or code.

## Migration status

**Godot GDScript is now the active scene in `godot/project.godot`; full feature parity and Android export are not complete.** The active scene generates deterministic terrain chunks around the player, with per-chunk trees and rocks, a continuous trail, river channel, ridge town, touch-controlled third-person hero, six collectible shards, a Warden encounter gated by those shards, melee/skill actions, a 360-second day/night cycle, and a local SHA-256-checked save written through a temporary file. The imported C# terrain project remains as reference content but is not the active scene. This is a compact original RPG slice, not a full Genshin-scale game.

Still to port: Last Star's full quest corpus, inventory and equipment, 101-spell system, companion/progression systems, full menu/UI, combat depth, and mobile performance tuning. Robust Android touch/layout behavior and character animation also need device verification. The exact UI palette, offline operation, and zero-permission requirement remain constraints. Keep the existing native APK available until a Godot Android APK builds and passes equivalent tests on a device.

## Validation constraints

No Godot executable or .NET SDK is installed in this build workspace. The new GDScript files pass the `gdtoolkit` parser/linter, but no Godot runtime, Android export, or device build could be run here. The existing native app's tests and APK workflow remain independent; passing those tests does not validate the Godot project.
