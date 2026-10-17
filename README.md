# JL-Mod Modern

A modernization fork of [JL-Mod](https://github.com/woesss/JL-Mod) — the J2ME (MIDP) emulator for Android —
tuned for retro handhelds.

The emulation core is untouched. What changed is everything around it: the parts you actually touch on a
device with a D-pad, four face buttons and two shoulders.

## What's new

- **Gamepad first.** L2/R2 are analog axes (`ABS_Z` / `ABS_RZ`) on most handhelds and were invisible to the
  emulator. They are now translated into real key events, so they can be mapped like any other button —
  including inside the key mapping screen.
- **Global key mapping.** One mapping for every game. A game only overrides it if you change the mapping
  for that game.
- **Global shaders.** Load a shader file once, it applies to all games. Ships with *HD anti-alias*
  (bilinear + unsharp mask) and a *CRT slot mask* (OSSC port) — see [`shaders/`](shaders) —
  and a per-game shader still wins.
- **Global scaling.** Stretch / integer scaling / fit-to-edge, applied to every game. Integer mode is
  centered and never crops.
- **Full screen.** The in-game action bar is gone; the menu slides in from the left edge (or the MENU key).
- **Save states (10 slots).** Snapshot a game's own save data to one of ten slots from the in-game menu and
  load it back later — the game is restarted so it picks the data up. Handy for games with a single built-in
  save slot.
- **Sane defaults.** Gamepad mapping and shader defaults apply to newly added games with no configuration,
  and the resolution picker now offers the usual J2ME resolutions.

## Upstream features (kept)

- **Shaders** — PPSSPP-compatible post-processing filters in `/sdcard/JL-Mod/shaders`
- **Sound banks** — DLS / SF2 banks in `/sdcard/JL-Mod/soundbanks`
- **Skins** — background images in `/sdcard/JL-Mod/skins`
- **Mascot Capsule 3D** — if a 3D scene is missing, add `micro3d.v3.render.no-mix2D3D: true` to
  *System properties*; `micro3d.v3.texture.filter: true` enables texture filtering
- **Porting** — build an Android app from J2ME sources, see the
  [upstream wiki](https://github.com/woesss/JL-Mod/wiki/Porting-midlet-instruction)

## Install

- Android 4.0+ (minSdk 14), arm64-v8a / armeabi-v7a / x86 / x86_64
- Download the APK from the releases page
- This build is signed with a different key than upstream, so **uninstall the original JL-Mod first**.
  Games and profiles live in `/sdcard/JL-Mod` and are kept — only the app's own settings are reset.

## Quick start

1. Add games (the app itself, or launch them with their path from a frontend such as Pegasus)
2. **Settings → Global key mapping** — map the gamepad once
3. **Settings → Shader** — load `hd.ini` (copy the files first:
   `adb push shaders/ /sdcard/JL-Mod/shaders/`)
4. **Settings → Scaling mode** — integer or fit-to-edge
5. Per game: long-press the game → Settings (resolution, per-game shader/keys)
6. **In game:** MENU / left-edge swipe → *Save state* / *Load state* (10 slots)

> Save states snapshot the game's own persistent data (RMS record stores), because a J2ME VM cannot be frozen.
> If a game only writes it saves at save points, a snapshot is the state of the last in-game save. Loading a slot
> backs up the current data to a hidden slot first, so a wrong load can be undone.

## Build

```bash
./gradlew assembleEmulatorRelease
```

Native libraries are prebuilt and shipped in `app/src/main/jniLibs`, so the NDK is not needed.
`shaders/` holds the bundled filters — push them to `/sdcard/JL-Mod/shaders/` on the device.
Implementation notes are in [AGENTS.md](AGENTS.md).

## Credits

- [JL-Mod](https://github.com/woesss/JL-Mod) / [J2ME-Loader](https://github.com/nikita36078/J2ME-Loader) by
  Nikita Shakarun, Yury Kharchenko, woesss and contributors
- CRT slot mask ported from *OSSC Slot Mask simulator* by DariusG (RetroArch slang shaders)
- Apache License 2.0

## 中文简介

老模拟器的现代化改造：模拟核心不动，重做交互层。

- **手柄优先** — L2/R2 在掌机上上报为模拟轴，原版收不到；现在转成真实按键，可以像其它键一样映射
- **全局按键映射** — 一套映射对所有游戏生效，某个游戏单独改过才覆盖全局
- **全局着色器** — 加载一次到处生效，附带「高清抗锯齿」和「CRT 遮罩」两个滤镜
- **全局缩放** — 全屏拉伸 / 整数缩放 / 短边缩放（整数模式居中，不裁切）
- **全屏** — 去掉游戏内顶栏，菜单改为左侧滑出（或按 MENU 键）
- **开箱即用** — 新加的游戏零配置就有手柄映射和滤镜；分辨率选择器补齐了常用 J2ME 尺寸
