# JL-Modern

A modernized J2ME/Java ME emulator for Android, built around a brand-new Java
interpreter — [snapvm](https://github.com/sankerXD/snapvm). SnapVM is a fully
serializable JVM: the whole machine state (heap, stacks, threads, monitors) can
be frozen and restored, which is what makes true instant save states possible.

JL-Modern brings the features people expect from a modern emulator to classic
Java mobile games: instant save/load, real-time shader filters, gamepad-first
input including analog triggers, and scaling modes tuned for today's screens.

> **Status: destructive development.** File formats, save states and behavior may
> break between releases. Compatibility and performance work is ongoing — expect
> rough edges.

**Roadmap:** RetroArch core · Linux support.

## Features

- **Instant save states** — freeze at any frame, resume exactly where you left
  off (20 slots per game), powered by snapvm
- **Shaders** — HD anti-alias, LCD3x, Dither-LCD and CRT slot-mask bundled in the
  APK; switchable in-game, global or per-game
- **Virtual keyboard editing that doesn't fight you** — enter layout/scale edit
  and the game freezes in place; on-screen Done / Discard, quick "hide all keys"
  toggle
- **Gamepad first** — full key mapping including analog L2/R2 triggers; one
  global mapping covers all games, a game only overrides it if you remap it
- **Modern UX** — fullscreen gameplay, slide-in menu, per-game resolution and
  scaling (fit / stretch / integer), FPS limiter, screenshots

## Install

- Android 4.0+ (minSdk 14), arm64-v8a / armeabi-v7a / x86 / x86_64
- Download the APK from the releases page
- This build is signed with a different key than upstream, so **uninstall the
  original JL-Mod first**. Games and profiles live in `/sdcard/JL-Modern` and are
  kept — only the app's own settings are reset.

## Build

```bash
./gradlew assembleEmulatorRelease
```

Native libraries are prebuilt in `app/src/main/jniLibs`, so the NDK is not
needed. snapvm is included as a composite build (`includeBuild("../snapvm")` in
`settings.gradle.kts`) — clone [snapvm](https://github.com/sankerXD/snapvm) next
to this repo, or drop the published jar into `mavenLocal`. Implementation notes
are in [AGENTS.md](AGENTS.md).

## 中文简介

一个现代化的 J2ME 模拟器。核心不是陈旧的dex，而是全新开发的
Java 解释器 [snapvm](https://github.com/sankerXD/snapvm) —— 可序列化 JVM，
整机状态可冻结可恢复，所以能做到真·即时存档。

> **破坏性开发阶段**：存档格式、行为随时可能变，兼容性/性能仍在迭代。

- **即时存档 / 即时读档** — 任意时刻冻结整机状态，随时恢复（每游戏 20 槽）
- **着色器滤镜** — 内置高清抗锯齿 / LCD3x / Dither 单色屏 / CRT 遮罩，
  游戏内实时切换，全局或单游戏生效
- **虚拟键盘编辑** — 编辑时游戏原地冻结，画面上直接「完成/放弃」
- **手柄优先** — 含模拟扳机 L2/R2 的全键映射，一套映射全局生效
- **现代化交互** — 全屏游玩、左侧滑出菜单、逐游戏分辨率与缩放（适应 / 拉伸 / 整数）

后续计划：RetroArch 核心支持、Linux 支持。

## Credits / 致谢

- The UI, host APIs and rendering pipeline are inherited from
  [JL-Mod](https://github.com/woesss/JL-Mod) /
  [J2ME-Loader](https://github.com/nikita36078/J2ME-Loader) by Nikita Shakarun,
  Yury Kharchenko, woesss and contributors — huge thanks. JL-Modern 的界面与
  宿主层实现源自他们。
- [snapvm](https://github.com/sankerXD/snapvm) — serializable JVM core
- CRT slot mask ported from *OSSC Slot Mask simulator* by DariusG (RetroArch
  slang shaders); LCD3x adapted from the RetroArch shader of the same name
- Apache License 2.0
