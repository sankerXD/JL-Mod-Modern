# JL-Modern

A modern J2ME emulator for Android, tuned for retro handhelds.
The UI and rendering pipeline are inherited from [JL-Mod](https://github.com/woesss/JL-Mod) — huge thanks to its authors.

The emulation core is [snapvm](https://github.com/sankerXD/snapvm): a from-scratch JVM whose entire
machine state — heap, stacks, threads, monitors — is serializable. That is what makes true save states
possible on a Java emulator.

## Features

- **Save states** — instant save & load at any moment, powered by snapvm
- **Shaders** — HD anti-alias and CRT slot-mask filters bundled in the APK, global or per-game
- **Gamepad first** — full key mapping including analog L2/R2 triggers; one global mapping covers all
  games, a game only overrides it if you remap that game
- **Modern UX** — fullscreen gameplay, slide-in menu, global scaling modes (stretch / integer /
  fit-to-edge), per-game overrides

## Install

- Android 4.0+ (minSdk 14), arm64-v8a / armeabi-v7a / x86 / x86_64
- Download the APK from the releases page
- This build is signed with a different key than upstream, so **uninstall the original JL-Mod first**.
  Games and profiles live in `/sdcard/JL-Mod` and are kept — only the app's own settings are reset.

## Build

```bash
./gradlew assembleEmulatorRelease
```

Native libraries are prebuilt in `app/src/main/jniLibs`, so the NDK is not needed. snapvm is included
as a composite build (`includeBuild("../snapvm")` in `settings.gradle.kts`) — clone
[snapvm](https://github.com/sankerXD/snapvm) next to this repo, or drop the published jar into
`mavenLocal`. Implementation notes are in [AGENTS.md](AGENTS.md).

## Credits

- [JL-Mod](https://github.com/woesss/JL-Mod) / [J2ME-Loader](https://github.com/nikita36078/J2ME-Loader)
  by Nikita Shakarun, Yury Kharchenko, woesss and contributors
- [snapvm](https://github.com/sankerXD/snapvm) — serializable JVM core
- CRT slot mask ported from *OSSC Slot Mask simulator* by DariusG (RetroArch slang shaders)
- Apache License 2.0

## 中文简介

一个现代化的 J2ME 模拟器。界面和渲染管线继承自 JL-Mod,内核是全新开发的可序列化 JVM
[snapvm](https://github.com/sankerXD/snapvm) —— 整机状态可冻结可恢复,所以能做到真·即时存档。

- **即时存档 / 即时读档** — 任意时刻冻结整机状态,随时恢复
- **着色器滤镜** — 内置高清抗锯齿 / CRT 遮罩,全局或单游戏生效
- **手柄优先** — 含模拟扳机 L2/R2 的全键映射,一套映射全局生效
- **现代化交互** — 全屏游玩、左侧滑出菜单、全局缩放(拉伸 / 整数 / 短边)
