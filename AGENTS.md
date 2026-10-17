# AGENTS.md — 维护者工作笔记

**已发布**：https://github.com/sankerXD/JL-Mod-Modern · v1.0.0（2024-10-18 发）
- 分支 `modern` → 远端 `main`；上游基线 commit `724b90a0`（JL-Mod 0.87.1）
- 版本：`versionName 1.0.0` / `versionCode 49`
- Release 附件：`JL-Mod-Modern_1.0.0.apk`（用 `jlmod.keystore` 签名，密钥不入库）
- 重新发版：改版本号 → `./gradlew assembleEmulatorRelease` → `git tag/gpush` → `gh release create --repo sankerXD/JL-Mod-Modern`

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

## 七、文件清单

| 文件 | 说明 |
|---|---|
| `JL-Mod_0.87.1-trigger.apk` | 打了扳机补丁的成品 |
| `JL-Mod-trigger/` | 分支源码（commit 724b90a0 + 全部补丁），仓库根 |
| `build.sh` | 一键重编译 |
| `jl_map.py` | 批量写/清游戏配置 |
| `shaders/` | 着色器源码（hd / slotmask） |
| `jl-original.apk` | 原版 APK（回滚用） |
| `backup/sdcard-JL-Mod-backup.tar` | 改造前 `/sdcard/JL-Mod` 全量备份 |
| `backup/config-純愛.orig.json` | 该游戏原始配置 |
