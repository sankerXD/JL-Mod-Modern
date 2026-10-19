# AGENTS.md — 维护者工作笔记

**已发布**：https://github.com/sankerXD/JL-Mod-Modern · v1.0.2
- 分支 `modern` → 远端 `main`；上游基线 commit `724b90a0`（JL-Mod 0.87.1）
- 版本：`versionName 1.0.2` / `versionCode 53`（**1.0.2 = 着色器内置进 APK，已发版**）
- v1.1.0/v1.1.1（即时存档实验版）已回滚并从 Releases 删掉，见「六点五」
- Release 附件：`JL-Mod-Modern_1.0.2.apk`（用 `jlmod.keystore` 签名，密钥不入库）
- 重新发版：改版本号 → `./gradlew assembleEmulatorRelease` → `git tag/gpush` → `gh release create --repo sankerXD/JL-Mod-Modern`

### 发布检查清单（每次发版照这个走，别跳步）

1. `app/build.gradle.kts`：`versionCode` +1、`versionName` 改掉
2. **确认安装包自带着色器还在**（从 1.0.2 起是硬要求，用户明确说过以后别丢）：
   - `app/src/main/assets/bundled_shaders/` 里 5 个文件（hd.ini/hd.fsh/slotmask.ini/slotmask.fsh/slotmask.vsh）
   - `FileUtils.initWorkDir()` 里还挂着 `BundledShaders.install(dir)` 那一行
3. `./gradlew assembleEmulatorRelease && ./gradlew verifyBundledShaders`
   （后者会打开 APK 逐个检查 `assets/bundled_shaders/*`，少一个直接编译失败）
4. 实机验一遍：`rm -rf /sdcard/JL-Mod/shaders` → 启动 App → 文件自动重建
5. `git tag vX.Y.Z` → `git push modern main --tags`
6. `gh release create vX.Y.Z --repo sankerXD/JL-Mod-Modern JL-Mod-Modern_X.Y.Z.apk --title ... --notes ...`

> 这里记录本次改造的**全过程**：为什么这么改、踩过哪些坑、怎么回滚。
> 面向维护者自己（也方便 AI 助手接手）。对外说明看 [README.md](JL-Mod-trigger/README.md)。


设备：KONKR / AYANEO Pocket BLOCK（Android 12, arm64, Helio G85）
原版：JL-Mod `0.87.1-240903-724b90a` → 现在装的是本地编译的 `0.87.1-trigger`

---

## 一、结论先看

| 需求 | 做法 | 需要改代码？ |
|---|---|---|
| A/B/X/Y/L1/R1 按键映射 | 每游戏配置 `KeyMappings` | 不需要 |
| **L2/R2** | **改代码**（扳机是模拟轴，不是按键） | 需要，已编译安装 |
| slotmask 滤镜 | 单 pass GLSL 着色器（移植 RetroArch 的） | 不需要 |

---

## 二、L2/R2 为什么必须改代码

用 `getevent` 抓到的真实数据：

```
L2 → EV_ABS  ABS_Z    0 → 255
R2 → EV_ABS  ABS_RZ   0 → 255
A  → EV_KEY  BTN_GAMEPAD
```

L2/R2 在系统层是**模拟扳机轴**（内核虽然也声明了 BTN_TL2/BTN_TR2，但固件只发轴事件），
Android 不会把它转成按键，而 JL-Mod 原版**完全没有 `onGenericMotionEvent`**，所以永远看不到。

### 改动（4 个文件，36 行）

- **新增 `javax/microedition/lcdui/keyboard/TriggerAxisHandler.java`**
  监听 `AXIS_LTRIGGER` / `AXIS_RTRIGGER`，越过阈值（按下 0.5 / 松开 0.3，带滞回）时合成
  `KEYCODE_BUTTON_L2` / `KEYCODE_BUTTON_R2` 的真实 KeyEvent，走原有按键通道。
- **`MicroActivity`**：`dispatchGenericMotionEvent` 挂上 handler；`onPause` 时松开扳机防卡键。
- **`KeyMapperActivity`**：同样挂上，**所以在按键映射界面里也能直接按 L2/R2 来绑定**。
- **`KeyMapper.getDefaultKeyMap()`**：给 8 个手柄键加了默认映射（新安装的游戏开箱即用）。

默认映射（可用「重置」恢复）：
`A→FIRE`、`B→*`、`X→#`、`Y→0`、`L1→左软键`、`R1→右软键`、`L2→1`、`R2→3`

### 编译说明

基于**原版同版本 commit `724b90a0`** 打补丁，行为保证一致：

```bash
./build.sh          # 产物 -> JL-Mod_0.87.1-trigger.apk
```

- 关掉了 `externalNativeBuild`，`app/src/main/jniLibs/` 里直接放原版 APK 抽出来的 `.so`
  （68 个，和原版逐个比对完全一致），所以不需要 NDK 编译。
- 签名用的是本地生成的 `jlmod.keystore`（原版签名拿不到），因此**装之前必须卸载原版**。
- 游戏库和配置都在 `/sdcard/JL-Mod`，卸载不丢；SharedPreferences（主题等全局设置）会重置。

### 回滚

```bash
adb uninstall ru.woesss.j2meloader
adb install jl-original.apk
adb shell tar -xf sdcard-JL-Mod-backup.tar -C /sdcard   # 需要时
```

---

## 三、按键映射表（当前生效）

`/sdcard/JL-Mod/configs/<游戏>/config.json` → `KeyMappings`，格式 `Android keycode : J2ME keycode`

| 手柄键 | keycode | J2ME 键 | 屏幕虚拟键 |
|---|---|---|---|
| A | 96 | -5 | `F`（FIRE/确认）|
| B | 97 | 42 | `*` |
| X | 99 | 35 | `#` |
| Y | 100 | 48 | `0` |
| L1 | 102 | -6 | `L`（左软键）|
| R1 | 103 | -7 | `R`（右软键）|
| **L2** | 104 | 49 | `1` |
| **R2** | 105 | 51 | `3` |

`纯爱物语-湛蓝回忆` 目前用的是**内置默认映射**（配置里 `KeyMappings: null`），
默认值已经包含上面 8 条，所以效果相同。

⚠️ 一个虚拟键同时只能绑一个手柄键；在映射界面重新绑定会移除旧的那条（原版逻辑）。

---

### 全局按键映射（新增）

**入口**：主界面 ⋮ → 设置 → **全局按键映射**（打开的就是原来的按键映射界面，标题变成"全局按键映射"）

优先级（后者覆盖前者）：

```
内置默认  <  全局按键映射  <  单个游戏自己的 KeyMappings
```

- 全局映射存在 SharedPreferences（key `pref_keymap`，JSON 格式），由 `util/GlobalKeyMap.java` 读写
- 应用顺序在 `KeyMapper.setKeyMapping()`：先 `getDefaultKeyMap()`，再 merge 全局，最后 merge 游戏的
- 全局界面里的「重置」= 恢复内置默认（内置默认已经包含 8 个手柄键：A→FIRE、B→*、X→#、Y→0、
  L1/R1→左右软键、L2→1、R2→3）
- 想要给某个游戏单独设：长按游戏 → 设置 → 输入设备 → 按键映射（那一份会覆盖全局）

**已把 506 个游戏配置里的 `KeyMappings` 全部清掉**（`./jl_map.py apply --all --remove-keymap`），
所以现在所有游戏都跟随全局；以后只有你在某个游戏里改过，那个游戏才会脱离全局。

> 图标：设置页里新增的三个图标（全局按键映射 / 着色器 / 倍率缩放）都按原版图标的写法做的——
> `48dp` 画布 + `viewport 24` + `group scale 0.6 / translate 5`，所以它们和原有图标的
> 尺寸、垂直位置、中心点（x=70）完全一致。之前直接写 `24dp` 满画布会导致图标偏左偏大，
> 而用工具栏的白色 `ic_action_keyboard` 在浅色背景上等于没有图标。

> 注意原版逻辑限制：**一个虚拟键同时只能绑一个按键**。比如把虚拟键 `1` 绑到手柄 Y 上，
> 原来绑到 `1` 的 L2 就会被自动解绑。

## 四、画面滤镜（着色器）

`/sdcard/JL-Mod/shaders/` 下现在有两套，在 **设置 → 着色器（滤镜）** 里点一下加载即可切换：

| 文件 | 效果 | 适用 |
|---|---|---|
| **`hd.ini`（当前使用）** | **抗锯齿高清化**：手工双线性放大 + unsharp 锐化 | J2ME 手游、文字多的游戏 |
| `slotmask.ini` | CRT slotmask（OSSC 移植） | 想要彩监味道时 |

### 当前的 HD anti-alias（`hd.fsh`）

1. **手工双线性放大**（自己在 texel 空间算，不依赖 sampler 的过滤设置）——去掉马赛克块感，
   斜线和文字边缘变平滑；
2. **unsharp mask**：把 4 邻域的模糊版本减回去，所以是"平滑但依然锐"，不是单纯糊；
3. 可选饱和度提升。

参数（`.ini` 的 `SettingDefaultValue1..2`，游戏设置里「着色器调节」也能改）：

| 参数 | 默认 | 说明 |
|---|---|---|
| Sharpness | 0.4 | 0 = 纯双线性（最柔）；0.4 默认；>1 会出现锐化光晕 |
| Saturation | 1.0 | 1.0 原色，1.1~1.2 更艳 |

> 性能：默认 sharpness>0 时每像素约 20 次纹理采样；如果某个 3D 游戏掉帧，
> 把 Sharpness 设成 0 就降到 4 次（纯双线性）。

### 备用的 slotmask（`slotmask.fsh`）

OSSC Slot Mask simulator (DariusG @2023) 移植版，gamma2 空间 + 按亮度阈值混合
（高光处不压遮罩，所以不会发白）。参数：Mask size（1 细 … 6 粗，默认 3）、
Mask brightness、Scanline、Threshold。

> 结论：CRT 像素滤镜在小尺寸手游上不讨好——低分辨率 + 非整数放大，遮罩要么细到像噪点、
> 要么粗到像彩色棋盘，所以默认改成 HD 抗锯齿。

### 着色器已内置进 APK（1.0.2）

以前换台机器要先 `adb push shaders/ /sdcard/JL-Mod/shaders/`，现在**装完就有**：

- 文件放在 `app/src/main/assets/bundled_shaders/`（hd.ini/hd.fsh/slotmask.ini/slotmask.fsh/slotmask.vsh）
- 启动时由 `util/BundledShaders.install()` 释放到 `<工作目录>/shaders/`，
  挂钩点是 `FileUtils.initWorkDir()`（MainActivity 建目录、改工作目录、授权后重试都会走到，天然幂等）
- **不会覆盖你改过的文件**：书签文件 `shaders/.bundled_shaders` 里记着 `version` + 每个文件的 md5；
  升级 APK 时只刷新"还是我上次发的那份"的文件，你自己编辑过的原样保留
- 书签是 `.bundled_shaders` 而不是 `.ini`，所以文件选择器里看不到它

实测（vivo/ Pocket Block、Android 12）：`rm -rf /sdcard/JL-Mod/shaders` 后启动 App，
五个文件 + 书签（`version=53`）自动重建；设置 → 着色器 → 选文件，选择器里直接能看到
`hd.ini` 和 `slotmask.ini`。

> 注意 `assets/shaders/` 是**引擎内部**的着色器（`Program.java` / `ShaderProgram.java` 通过
> `getAssets().open("shaders/simple.fsh")` 读），别把用户滤镜混进去，所以另开了 `bundled_shaders/`。

## 五、批量铺开（已执行：506/506）

已把全部 506 个游戏配置统一（手柄 8 键 + 开启 slotmask），校验结果：
`手柄键不一致 0 / 滤镜参数不一致 0 / JSON 损坏 0`。

```bash
./jl_map.py list                          # 列出所有游戏配置
./jl_map.py apply --game "游戏名"         # 单个
./jl_map.py apply --all                   # 全部：统一手柄键 + 开启 slotmask
./jl_map.py apply --all --no-shader       # 只统一按键，不动滤镜
./jl_map.py apply --all --remove-shader   # 去掉所有游戏的 slotmask
```

（脚本会先 force-stop，避免 App 退出时把内存里的配置写回覆盖修改；
改之前已整包备份 `backup/sdcard-JL-Mod-before-all.tar`。）

### 游戏是怎么启动的（重要）

游戏本体在 `/sdcard/roms/JAVA/`（**523 个 jar**），由**天马G / PegasusG 传参启动**：

```
天马G --(jar 路径)--> JL-Mod MainActivity --(自动转码)--> /sdcard/JL-Mod/converted/<游戏名>/
                                                     └--> /sdcard/JL-Mod/configs/<游戏名>/config.json
```

- 配置目录按游戏名匹配，卖家预置了 **506** 个
- 剩下 **21 个没有预置配置**的游戏（轩辕剑-天之痕1~5、苍神录系列、昆仑奇侠传1~2、欢乐三国_BT版）
  走的是代码里的默认值

### 没有配置的游戏怎么办（已解决）

因为这类游戏没有 config.json，光改配置文件覆盖不到。所以又在代码默认值里补了两处：

- `KeyMapper.getDefaultKeyMap()` —— 8 个手柄键进默认映射
- `ProfileModel` 构造函数 —— 新 profile 默认就带 `Slotmask CRT`（scale 3 / brightness 0.2 / size 1 / threshold 0.3）

实测：把一个游戏的配置目录整个移走，直接启动 → 新建的默认 profile 里「着色器」已显示
`Slotmask CRT`，画面也带滤镜。**所以以后新加的游戏零配置就有映射 + 滤镜。**

唯一需要留意的是这 21 个游戏没有卖家调过的分辨率（默认 240x320），
如果某个游戏画面比例不对，在它的设置里改一下「屏幕选项」宽高即可。

---

## 六、界面改造（全局倍率缩放 / 去掉顶栏 / 左侧抽屉）

### 1. 倍率缩放（全局）

**入口**：主界面 ⋮ → 设置 → **倍率缩放**

| 选项 | 行为 | 内部值 |
|---|---|---|
| 跟随游戏设置 | 用每个游戏 config.json 里自己的缩放类型 | 0（默认值）|
| 全屏拉伸 | 画面铺满整屏，不保持比例 | screenScaleType=2 |
| 整数缩放 | 取能放下的最大整数倍（2x/3x…），画面居中 | 新增 screenScaleType=3 |
| 短边缩放 | 等比放大到某条边贴住屏幕边框，另一边同倍率 | screenScaleType=1 |

- 实现在 `MicroLoader.applyGlobalScaleMode()`：启动游戏时读全局偏好，非 0 就覆盖 `params.screenScaleType`，
  对**所有游戏**生效（优先于每个游戏自己的设置）。
- 整数缩放在 `Canvas.updateSize()` 新增 `case 3`：`factor = min(屏宽/画布宽, 屏高/画布高)`；
  `factor >= 1` 用整数倍，画面比屏幕还大时退回等比适应避免裁切；整数模式同时把对齐强制为居中。
- 实测（240×275 游戏 / 960×720 屏）：整数缩放 = 2 倍（480×550 居中）；短边缩放 = 2.618 倍（628×720 满高）。

### 2. 游戏内顶栏已移除

`activity_micro.xml` 的 Toolbar 改成 `visibility=gone` + 高度 0；`SetCurrentEvent` 不再
`actionBar.show()/setTitle()`，`overlay.setLocation(0, 0)`。原来那条吃掉约 56px 高度的黑色标题栏没了，画面从 y=0 开始。

另加 `getWindow().addFlags(FLAG_FULLSCREEN)`（`!statusBarEnabled` 时）兜底，避免手势把状态栏带出来。

### 3. 菜单改成左侧滑动抽屉

- 根布局换成 `androidx.drawerlayout.widget.DrawerLayout`（新增依赖 `androidx.drawerlayout:drawerlayout:1.2.0`），左侧 280dp 抽屉。
- 抽屉内容**由同一份菜单资源** `res/menu/midlet_displayable.xml` 动态生成（`setupMenuDrawer()`），
  「退出 / 存储日志 / 锁定屏幕旋转 / 键盘类型(IME) / 屏幕截图 / 限制 FPS / 虚拟键盘子菜单」全在，
  点击走原来的 `onOptionsItemSelected()`，行为一致（含可勾选项状态切换）。
- 打开方式：**屏幕左边缘向右滑**（DrawerLayout 原生手势），或按 **MENU / BACK 键**（`openOptionsMenu()` 已改为打开抽屉）。
- 抽屉标题显示当前游戏名。


### 4. 着色器（滤镜）：全局加载文件 + 单游戏覆盖

**规则**：单游戏如果自己加载了着色器 → 用它的；否则用全局的；全局「关闭」就什么也不加。

**全局入口**：主界面 ⋮ → 设置 → 着色器（滤镜）→ 弹窗只有两项：

| 选项 | 行为 |
|---|---|
| 关闭（无滤镜）| 所有游戏都不加载滤镜 |
| 全局：加载着色器文件… | 打开文件选择器（默认停在 `/sdcard/JL-Mod/shaders`，只显示 `.ini`），选中即加载 |

加载后会**校验**（`ShaderList.validate()`）：
文件可读 → 能解析出带 `Fragment=`/`Vertex=` 的段 → 两个源文件存在且非空 → 都含 `main()`。
不通过就弹窗说明原因、不保存；通过则 Toast「已加载着色器：xxx」，设置页摘要变成「全局：xxx」。

**单游戏覆盖**：长按游戏 → 设置 → 屏幕选项 → **着色器**（这一行是点击式的，不是下拉框），点一下弹窗：

- 跟随全局着色器（默认）
- *（如果这个游戏自己加载过，会多一项它的名字）*
- 加载着色器文件… ← 选文件后这个游戏就用它自己的，覆盖全局

> 这一行踩了两个排版坑：
> 1. 原来是个 Spinner，在 48dp 的行里会把文字压到底部再裁掉 → 换成可点击 TextView
> 2. 换完还是跟上一行对不齐：其它行的值列是靠 `@id/barrierScreenConfig` 这个 Barrier 对齐的，
>    而这行原来是 `LinearLayout`（不参与 Barrier）。现在拆成三个直接子 View
>    （标签 / 值 / 调节按钮），标签用 `end_toStartOf=barrierScreenConfig`、值用
>    `start_toEndOf=barrierScreenConfig`，并把标签加进 Barrier 的 referenced_ids —— 现在
>    「图形模式」「着色器」两行的值列都从 x=186 开始，完全对齐。

两个已修的坑：
- 从文件选择器返回时 `onResume()` 会重新从磁盘加载配置，把刚选好的着色器覆盖掉 →
  用 `selectedShader` + `shaderModified` 在本次会话内保住选择
- 原来的点击监听只在"图形模式"切到硬件加速时才挂上，已经是 GL 模式下永远不执行 →
  移到 `onCreate` 里无条件挂

**实现要点**

- 新增 `util/PickFileResultContract`：文件选择器支持自定义扩展名
  （`FilteredFilePickerActivity.EXTRA_EXTENSIONS`，原来只认 jad/jar/kjx）
- `ShaderList`：解析 ini、校验、并把 `SettingDefaultValue1..4` 填进 `ShaderInfo.values`
  （引擎只在 `values != null` 时下发 `u_setting`，否则参数全是 0）
- `ShaderInfo.dir` 改为持久化字段，所以着色器文件可以放在任意目录
- `MicroLoader.applyShader()`：`params.shader != null`（游戏自己加载了）直接用；否则读全局偏好加载
- 删掉了 `ProfileModel` 里的默认着色器，并把 506 个配置里的 `Shader` 字段全部清掉
  （`./jl_map.py apply --all --remove-shader`），这样所有游戏默认「跟随全局」

### 已知小现象

用「边缘滑动」拉抽屉的瞬间，左上角状态栏会闪一下再自动消失——这是 Android `IMMERSIVE_STICKY`
对边缘手势的固有反应；用 MENU/BACK 键打开则完全不闪。


### 补充：画面"错乱"的真正原因是每游戏分辨率配错了（不是拉伸）

现象：宠物王国6-烈火 画面在 x=720 处有一条硬接缝，右边一条不同内容的竖带。

排查结论：
- 这条接缝在 **全屏拉伸 / 短边缩放 / 跟随游戏设置 三种模式下位置完全一样** → 与缩放模式无关
- 接缝精确落在 x=720 = **240 × 3**，说明游戏自己只画了 240 像素宽的画面
- 该游戏 canvas 被卖家配成 **320×240**（横屏），但游戏 manifest 里写着"适用于N73"，N73 是 **240×320** 竖屏

修复：把该游戏的 `ScreenWidth/ScreenHeight` 改成 240×320 → 画面完美铺满、无接缝。
原配置备份在 `backup/config-pet.orig.json`。

**506 个配置的分辨率分布很不一致**（卖家逐个猜的）：

| 分辨率 | 数量 |
|---|---|
| 240×320 | 266 |
| 320×240 | 183 |
| 320×320 | 24 |
| 605×317 | 11 |
| 744×390 | 7 |
| 240×275 | 6 |
| 176×208 / 176×220 | 4 |
| 其他 | 5 |

所以**任何游戏画面"错位/有条竖带/只填一部分"，都是它的 canvas 分辨率不对**，与倍率缩放无关。

修法：长按游戏 → 设置 → 屏幕选项 → 宽/高（点右边图标有预设列表，这次补进了
128×160、176×208、240×320、240×400、320×240、352×416、360×640 等常用尺寸）。

---

## 六点五、为什么不做「即时存档」（曾实现，已整个回滚）

1.1.0/1.1.1 做过一版 10 槽位的即时存档（快照 `/sdcard/JL-Mod/data/<游戏>/` 里的 RMS 文件 + 原地重启），
最后**整个回滚**，原因：

- J2ME 是直接跑在 Android 进程里的 Java 字节码，**没有可序列化的模拟机器**，JVM 堆不能快照 →
  真正的"任意帧存档"不可能实现
- 能做的只有"游戏自身存档文件"的快照，而 J2ME 游戏**只在自己存档时写盘**。
  实测证据：某次 slot1 (00:51:40) 与读档前的 backup (00:53:04) **md5 完全一致**，
  且期间整个 `/sdcard/JL-Mod` 没有任何文件被修改 → 玩家这 1 分半的"进度"从未落盘
- 于是"即时存档"这个名字会误导：用户以为存的是当前进度，其实只能存到上一次游戏内存档点

排查过程中确认的一些机制（以后可能有用）：

- RMS 写入是 open→write→close，**即时落盘**（`AndroidRecordStoreManager.saveToDisk`）
- `javax.microedition.rms.RecordStore` 属于 APK 自身的类，在进程内是单例；
  换 MIDlet 不会清缓存，需要 `AndroidRecordStoreManager.reset()`
- J2ME 游戏在 `destroyApp()` 里基本都会调 `notifyDestroyed()` → `Process.killProcess(myPid())`，
  所以想在同一进程里重启游戏**不能调 destroyApp**，只能 `quitSafely()` 掉 MidletThread 再
  `new MicroLoader(...)`（新 AppClassLoader）拉起新实例
- 存档文件由旧 App（不同 uid）创建时，新 App 可能**删不掉**（能覆盖不能 unlink）→ 需要重命名兜底

---

## 六点六、换核心：snapvm 可快照解释器（进行中）

既然 ART 模型下即时存档物理无解，决定**直接替换执行核心**（不是双核）：

- **新核心是独立项目 `snapvm`**：https://github.com/sankerXD/snapvm
  纯 Java（`org.snapvm` 包、零依赖、Java 8 源码级）字节码解释器，guest 堆/栈帧/线程全是
  普通 Java 对象 + green thread 协作调度 → 整机状态可序列化。JL-Mod 之后以依赖方式引用它。
  硬规矩：**VM 任何改动必须带冒烟测试**，跑 `./test.sh`（= `gradlew test`）全绿才提交。
- 架构：`游戏字节码 → snapvm 解释器 → host 桥接 → JL-Mod 现有 javax.microedition.* 实现`。
  MIDP/渲染/音频/RMS 层一行不动，还是 ART 原生速度；需要快照的只有解释器自己的对象图。
- 全库 API 扫描（523 jar / 22547 class）确认 JL-Mod 现有实现覆盖引用面 ~100%，
  `java.*` 引用不超出 lang/io/util → VM 只需 CLDC 级类库。
- SquirrelJME 只当参考实现（它证明了"VM 状态=普通对象图"可行），不直接采用。
- 里程碑：M1 解释器核心+12 冒烟测试 ✅ → M2 补语义 → M3 接进本仓库跑真 midlet →
  M4 线程/monitor → M5 快照序列化 → M6 523 游戏 burn-in + 与 ART 路径差分对拍 → M7 摘 dexlib。
- 开发期**保留 ART/dex 路径做差分对拍**，稳定后再删；最终发布形态是新核心唯一执行。

### M3 真机修复记录（斗地主 / 魔塔 / 合金弹头，全绿）

- **`System.getProperty` 丢属性**：`SnapVm.copyProps` 原来只复制 `microedition.*/fileconn.*/user.home`
  白名单，`supports.mixing` 这类 profile `SystemProperties` 到不了 `j2meProps`
  → `12星座斗地主1` 在 `u.<init>@95` NPE。
  修复：去掉白名单全量复制。此时 host 属性表已被 `MicroLoader.applyConfiguration()`
  （`System.setProperty` 逐条注入）填满，guest 看到的和 ART 路径完全一致。
- **`Class.getResourceAsStream` 漏路**：`HostBridge.classBuiltin` 只认 `GuestClass` 接收者。
  游戏用 `Runtime.getRuntime().getClass().getResourceAsStream("/atb/...")`（host `Class` 接收者）
  → 落到 host 反射 → 查不到 jar 资源 → `c.l.<clinit>@1109` arraylength NPE（魔塔/合金弹头同型）。
  修复：host `Class` 接收者先走 `vm.getResource`（相对路径按包名归一化），查不到再回退反射。
  冒烟测试 `hostClassResourceAsStream`。
- 验证：斗地主到「是否开启音乐」、魔塔过 c.l 到开场动画、合金弹头-鏖战过 c.l 到标题。
- **`SnapVm already running` 卡死**：`MicroActivity` 在独立 `:midlet` 进程，正常退出靠
  `notifyDestroyed` 的 `killProcess`；HOME 退到后台时老 `SnapVm.instance` 随进程存活，
  再拉新游戏 → `load` 抛 `IllegalStateException` → ACRA「Init midlet failed」卡死。
  修复：`SnapVm.load` 发现旧实例就 `stop()` 换成新 VM（与 dex 路径"旧 midlet 随进程死"
  等价；pump 是 daemon，`running=false` 自然退出）。验证：同进程 HOME 后再拉新游戏正常进标题。

---

## 六点七、更名 JL-Modern + 数据目录迁移

- **显示名**：`rootProject.name = "JL-Modern"` → release `app_name`；
  debug 变体 `app_name = "JL-Modern-Debug"`。应用内可见字符串全部清掉 `JL-Mod`。
- **包名**：`applicationId = "io.github.sankerxd.jlmodern"`（debug `.debug`）。
  新包名 = 新应用身份，旧 `ru.woesss.j2meloader(.debug)` 并存，数据互不覆盖。
  Java namespace `ru.playsoftware.j2meloader` 不动；代码里全是 `getPackageName()` 自适应。
- **数据目录与 app_name 解耦**：新增 `resValue("string","emulator_dir_name","JL-Modern")`，
  release/debug 共用 `/sdcard/JL-Modern`（debug 包以前用 `/sdcard/JL-Debug`，
  没有卖家布局文件才导致虚拟键"左右反"——见下）。
- **一次性迁移**：`Config.migrateLegacyDir(File)`，仅当目标是默认目录、目标不存在且
  `/sdcard/JL-Mod` 存在时整目录 `renameTo`；`MainActivity.checkAndCreateDirs` /
  `applyWorkDir` 各调一次覆盖权限时序。用户自定义过工作目录则不迁移。
- **虚拟键"左右反"真因**：不是 `RectSnap` 或映射 bug——debug 数据目录里缺
  `VirtualKeyboardLayout` 文件，走了 NUM_ARR 兜底（数字左方向右）。卖家的 506 份布局
  本来就是方向左数字右，数据迁移过来即正确。若新装无配置想要同款，得在游戏设置里
  把键盘类型切成 Custom/方向+数字。

---

## 七、坑点清单（血泪版）

每条格式：**现象 → 原因 → 结论**。都是实机踩出来的，以后改这块先扫一眼。

### 模拟器内核

**1. 手柄 L2/R2 按了没反应**
`getevent` 实测：`L2 = EV_ABS ABS_Z`、`R2 = EV_ABS ABS_RZ`（模拟轴）、`A = EV_KEY BTN_GAMEPAD`（按键）。
原版 JL-Mod 里**一处 `onGenericMotionEvent` 都没有**，轴事件永远收不到。
→ 新增 `TriggerAxisHandler`，把轴翻成 `KEYCODE_BUTTON_L2/R2` 再走原有按键通道（0.5 按下 / 0.3 松开，带滞回）。

**2. 一个虚拟键只能绑一个按键**
原版 `KeyMapperActivity.dispatchKeyEvent` 会先删掉"value 相同"的旧条目。
把虚拟键 `1` 绑到手柄 Y，原本绑在 `1` 上的 L2 就会被自动解绑。
→ 这是原版行为，不是 bug；改映射前先想清楚。

**3. 着色器参数全是 0 / 画面直接黑屏**
`Canvas` 只在 `settings.shader.values != null` 时才 `glUniform4fv(u_setting)`。
不填 values → `u_setting` 保持 `(0,0,0,0)`；slotmask 里 `msk = 0` 就整屏黑。
→ `ShaderList` 解析 ini 时必须把 `SettingDefaultValue1..4` 填进 `values`。

**4. 从 config.json 读回来的着色器找不到文件**
`ShaderInfo.dir` 原本是 `transient`，反序列化后为 null，`dir + fragment` 就是个非法路径。
→ 改成持久化字段 `@SerializedName("dir")`，并在 `MicroLoader` 里对空值兜底到工作目录的 `shaders/`。

### UI / 资源

**5. 刚选的着色器"自己变回去了"**
从文件选择器返回时 `ConfigActivity.onResume()` → `loadParams(true)` → `loadConfig()` 从磁盘重读，把内存里的选择覆盖掉。
→ 用 `selectedShader` + `shaderModified` 在本次会话内保住用户的选择。

**6. 点着色器那一行完全没反应**
点击监听原本挂在"图形模式切到硬件加速"的分支里 —— GL 模式下那个分支永远不执行。
→ 监听移到 `onCreate` 里无条件挂。

**7. 配置页的值列对不齐 / 文字被裁掉**
- 其它行靠 `@id/barrierScreenConfig`（Barrier）对齐，`LinearLayout` 不参与约束 → 新行拆成直接子 View，
  用 `end_toStartOf` / `start_toEndOf=barrier`，并把标签加进 `constraint_referenced_ids`
- 48dp 的行里塞 Spinner，选中项文字会被压到底部再裁掉 → 换成可点击 TextView

**8. 设置图标偏左、偏大、甚至看不见**
原版图标规格是 `48dp` 画布 + `viewport 24` + `group scale 0.6 / translate 5`。
直接写 `24dp` 满画布 → 偏左偏大；用工具栏的白色 `ic_action_keyboard` → 浅色背景上等于隐形。
→ 新图标全按原版规格重写（可见内容中心 x=70 与原有图标完全一致）。

**9. 边缘滑出抽屉时状态栏闪一下**
`IMMERSIVE_STICKY` 对边缘手势的固有反应，不是布局 bug。
→ 可接受；用 MENU/BACK 键打开则完全不闪。

### 构建 / 发布

**10. R8 会把 `Log.d/v/i` 全部删掉**
AGP 默认的 `proguard-android-optimize.txt` 带 `-assumenosideeffects`，只有 `w/e` 保留。
排查时加了一堆 `Log.d`，结果一条都不出现，白折腾半天以为代码没执行。
→ 调试信息一律用 `Log.e`（或 Toast）。

**11. 不需要 NDK 也能编译**
关掉 `externalNativeBuild`，把原版 APK 里那 68 个 `.so` 放进 `app/src/main/jniLibs/`。
→ 仓库多 8.5MB，换"装个 JDK17 + SDK 就能编"。

**12. 签名密钥绝对不能提交**
`.gitignore` 原本只挡 `*.jks`，`jlmod.keystore` / `keystore.properties` 会被 commit 上去。
→ 已补 `*.keystore` 和 `keystore.properties`。密钥丢了，以后发的更新用户必须卸载重装。

**13. 卸载重装会换 uid**
新 App 是 `u0_a97`，老数据是 `u0_a84` 建的。`/sdcard` 上**能覆盖、但可能删不掉**（unlink 被拒）。
→ 真要删就加"重命名成 `.stale`"兜底。SharedPreferences（主题等）会重置，游戏库和配置不受影响。

**14. 改 `config.json` 前必须先 force-stop**
App 退出时会把内存里的配置写回，覆盖外部修改。
→ `jl_map.py` 每次先 `am force-stop`。

### 存档（已回滚的功能，留档防重做）

**15. J2ME 的 JVM 没法冻结**
没有可序列化的"模拟机器"，ART 也不提供堆快照 → **真正的任意帧存档物理上做不到**。

**16. RMS 是即时落盘的，但游戏只在自己存档时才写**
`AndroidRecordStoreManager.saveToDisk` 是 open→write→close，所以磁盘一直是最新的 ——
问题是"最新"只到上一次游戏内存档。实测：slot1 写于 00:51:40，读档前的 backup 写于 00:53:04，
两者 **md5 完全一致**，期间 `/sdcard/JL-Mod` 没有任何文件被修改。
→ 所以"即时存档"实际等于"上一次游戏内存档"，名字误导，最终整个回滚。

**17. `RecordStore` 是进程内单例，换 MIDlet 不清缓存**
`javax.microedition.rms.RecordStore` 属于 APK 自身的类，`AndroidRecordStoreManager.recordStores`
在进程里只有一份，换 MIDlet 不会失效 → 需要 `RecordStore.clearCaches()` 触发 `reset()`。

**18. J2ME 游戏在 `destroyApp()` 里基本都会杀进程**
它们通常调 `notifyDestroyed()` → `Process.killProcess(myPid())`（logcat 实测 `Sending signal. PID: SIG 9`）。
想在同一进程里重启游戏**不能调 destroyApp**，只能 `quitSafely()` 掉 MidletThread 再
`new MicroLoader(...)`（新 AppClassLoader）拉起新实例。

### 其它已知问题

**19. 每个游戏的 canvas 分辨率是卖家猜的**
画面"错位 / 有条竖带 / 只填一部分" = 那个游戏宽高配错了（例：宠物王国被配成 320×240，实际是 240×320）。
分布：240×320 266 个、320×240 183 个、320×320 24 个、605×317 11 个、744×390 7 个…
→ 长按游戏 → 设置 → 屏幕选项 → 宽/高（预设里已补常用尺寸）。

**20. 游戏由天马G 传参启动**
jar 在 `/sdcard/roms/JAVA/`，按文件名生成 `converted/<名>/` 和 `configs/<名>/`。
没有预置配置的 21 个游戏走代码默认值（映射和滤镜靠内置默认兜底）。

### 想要回滚进度时的土办法

功能上做不了，但手动能做：把 `/sdcard/JL-Mod/data/<游戏名>/` 整个文件夹拷走就是一份完整存档，
要回滚再拷回去（拷之前先退出游戏）。用 MT管理器 之类的文件管理器就能干。

---

## 八、文件清单

| 文件 | 说明 |
|---|---|
| `JL-Mod_0.87.1-trigger.apk` | 打了扳机补丁的成品 |
| `JL-Mod-trigger/` | 分支源码（commit 724b90a0 + 全部补丁），仓库根 |
| `build.sh` | 一键重编译 |
| `jl_map.py` | 批量写/清游戏配置 |
| `shaders/` | 着色器源码（hd / slotmask），内容与 APK 内 `assets/bundled_shaders/` 一致 |
| `jl-original.apk` | 原版 APK（回滚用） |
| `backup/sdcard-JL-Mod-backup.tar` | 改造前 `/sdcard/JL-Mod` 全量备份 |
| `backup/config-純愛.orig.json` | 该游戏原始配置 |
