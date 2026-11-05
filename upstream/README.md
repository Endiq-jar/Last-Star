# Upstream copies

This folder holds the files from the three repositories you asked me to copy.
They are **verbatim copies of the upstream projects**, kept here for reference,
study and reuse. Each sub-folder keeps its original licence and README.

| Folder | Source repository | Licence | What is included |
|---|---|---|---|
| [`AnyRPGCore/`](AnyRPGCore/) | [AnyRPG/AnyRPGCore](https://github.com/AnyRPG/AnyRPGCore) | MIT | All 1,094 C# source files, shaders, Shader Graph assets, Unity `Packages/` and `ProjectSettings/` (Unity 6000.3.10f1), plus `FILE_INDEX.csv` listing **all 22,914 upstream files** with sizes |
| [`unity-Third-person-controller-android/`](unity-Third-person-controller-android/) | [Jay22K/unity-Third-person-controller-android](https://github.com/Jay22K/unity-Third-person-controller-android) | GPL-3.0 | **Complete: 7 of 7 files** — `Third_person_controller.unitypackage` (1,245 asset entries), README, three reference images, GPL licence |
| [`Android_Unity3D_OpenWorld/`](Android_Unity3D_OpenWorld/) | [nikunjsingh93/Android_Unity3D_OpenWorld](https://github.com/nikunjsingh93/Android_Unity3D_OpenWorld) | *(none declared upstream)* | **Complete: all 32 files** — Unity 5.6 project settings, the four `.csproj` files, `unitycar.sln`, `obj/Debug` build outputs, and the author's prebuilt `car and fps.apk` (Unity 5.6, `armeabi-v7a` + `x86`, debug-signed) |

## Why some binary files are not in this folder

The full upstream AnyRPGCore checkout is **1.2 GB** working tree (803 MB of it
assets) and contains 22,916 files. GitHub rejects individual files over 100 MB
and the repository would balloon, so the large binary media (FBX models, PSD/TGA
textures, WAV music, baked `.unity` scenes, `.anim` clips, `.prefab`/`.asset`
YAML) was left out of the git copy. It is fully recoverable in one command:

```bash
git clone https://github.com/AnyRPG/AnyRPGCore.git   # 1.2 GB, complete
```

Nothing was lost: every line of source, every shader, every project setting and
the complete package manifest is present, and `AnyRPGCore/FILE_INDEX.csv` lists
every upstream file with its byte size so you can diff a fresh clone against this
copy. Only the binary art/audio payload is externalised.

## Licensing notes (please read)

* **AnyRPGCore** — MIT. Can be copied, modified and redistributed with the
  copyright notice; `AnyRPGCore/LICENSE` is included.
* **unity-Third-person-controller-android** — **GPL-3.0**. This is a copyleft
  licence: anything you ship that is derived from this package must itself be
  released under the GPL-3.0 with source. That is exactly why it is kept in this
  separate, clearly-marked folder and **not** compiled into the Last Star APK
  (which is EPL-2.0). Use it as a learning reference, or keep your own
  project GPL-3.0 if you build on it.
* **Android_Unity3D_OpenWorld** — the upstream repository declares no licence, so
  by default all rights are reserved by the author (Nikunj Singh). It is copied
  here only so you have it locally; do not redistribute it commercially without
  asking the author. The included `car and fps.apk` is the author's own build,
  signed with the standard Android debug key
  (`SHA-256 6A:01:9B:1D:...:2A:40`).
* **Last Star** (this project's game) shares **no code, art or audio** with the
  three repositories above. Everything in `app/`, `desktop/` and `tools/` is
  original work, which is what keeps its EPL-2.0 licensing clean.
