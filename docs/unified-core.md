# JL-Modern 核心接口（libretro 形状的 EmulatorCore）+ 按键家族自动适配

> 双目标：**①我们自己的 RA 核心计划**（libretro 化打包给 RetroArch）
> 与 **②盒子伙伴的核心嵌入需求**（RA 式自造外围）是**同一个核的
> 两个消费者**——API 面从第一天按 libretro 形状设计，一次设计两处用。
>
> 分层原则：**snapvm 保持纯核**（字节码引擎，不认识 Android/输入设备）；
> 按键映射在 lcdui 宿主层（纯数据，不进引擎）；
> `EmulatorCore` 门面按 **libretro vtable 形状**暴露——
> load/run/input/frame/audio/serialize，上面再包两个消费者：
> AAR（盒子嵌入）和 libretro .so shim（RA）。
>
> 核心能力差异点：**即时存档/读档 = retro_serialize/unserialize**，
> dex 方案做不了，是我们快照机制对外的最大卖点。

---

## 一、对外契约：libretro 形状的 EmulatorCore

核心 API 面（与 libretro vtable 一一对应，**不依赖 Android UI**）：

```java
public final class EmulatorCore {
    // === 生命周期 ===                                    // libretro 对应
    void loadGame(String jarPath);                         // retro_load_game
    void reset();                                          // retro_reset
    void pause() / resume() / destroy();                   // (核自管)
    // === 运行/输出 ===
    void runFrame();          // 可选同步步进               // retro_run
    void setVideoCallback(Vcb cb);   // cb.frame(int[] px, w, h, pitch)
                                                             // retro_video_refresh
    void setAudioMode(AudioMode m);  // INTERNAL_TRACK | PCM_CALLBACK
                                                             // retro_audio_sample_batch
    // === 输入 ===
    void sendKey(int j2meKeyCode, boolean down);           // retro_input_state
    void sendKeyAction(String semantic, int type);         // 语义键(家族表)
    void sendPointer(int action, float x, float y);        // pointer
    // === 存档 ===
    byte[] serialize();                                    // retro_serialize ★
    void unserialize(byte[] state);                        // retro_unserialize ★
    // === 配置 ===
    void setKeyFamily(String name);    // 按键家族表
    void setVariable(String k, String v);                  // retro 风格变量
}
```

**两个消费者**：

- **AAR（盒子嵌入）**：`EmulatorCore` + `EmulatorView`(SurfaceView 包装
  `setVideoCallback` 内部实现)。他也可以自己调 onFrame 拿帧做滤镜/缩放。
- **libretro .so（RA）**：薄 JNI shim——RA Android 前端已有 JavaVM，
  shim 用 JNI 实例化 EmulatorCore 并翻译 vtable 调用
  （`retro_run` → 取 latest-frame / `retro_input_state` → sendKey /
  `retro_serialize` → byte[] 直通）。

设计要点：
- **帧输出必须回调化（v1 就要）**：RA 没有 View，视频只能回调；
  LCDUI Canvas 帧本来就是 int[]/Bitmap 攒的，`onFrame` 是顺的，
  但不能只做 View 包装
- **异步泵 + latest-frame**：我们泵是时间片异步模型，别硬改成
  同步 retro_run——核内异步泵 + 最新帧缓冲，run/retro_run 只取最新帧
- **音频 v1 走 INTERNAL_TRACK**（核内 AudioTrack，Android 上盒子/RA 都通）；
  `PCM_CALLBACK` 标 v2——MIDI 我们可控，MP3 输出层要动手术
- **映射 API 收两层**：sendKey 裸码（他自己映射）/ sendKeyAction 语义名走家族表
- **VK 不进核**：虚拟键盘留 app 壳层（消费者自己做）
- **serialize/unserialize 即卖点**：堆/解释器栈/绿线程/锁/媒体/显示影射
  全序列化——byte[] 形状天然就是 libretro 要的

### 模式 B — Intent API（轻集成备选，适合只想唤起的场景）

```bash
am start -a android.intent.action.VIEW \
    -d "file:///sdcard/roms/JAVA/<游戏>.jar" \
    -t "application/java-archive" \
    io.github.sankerxd.jlmodern
```

```bash
am start -a android.intent.action.VIEW \
    -d "file:///sdcard/roms/JAVA/<游戏>.jar" \
    -t "application/java-archive" \
    io.github.sankerxd.jlmodern
```

→ `MicroActivity` 直接进游戏画面（全屏、无模拟器 UI）。天马G 驱动 JL-Mod
用的就是同一契约，未动。首次启动自动转 res.jar（和 JL-Mod 一致）。

可选 extras（新增，均有默认值不传也行）：
- `--ez novk true`：本次启动不显示虚拟键盘（盒子有实体键时干净全屏）
- `--es profile <name>`：指定 config profile
- `--ez exit_to_front true`：游戏退出后直接回到前端（不回 app 主界面）

#### 模式 B 按键注入（KEYINJECT 转正）

```bash
am broadcast -a io.github.sankerxd.jlmodern.KEY \
    --es action SOFT_LEFT            # 语义键名（推荐）
    # 或 --ei keycode -6             # 直接发 J2ME keycode
    # --ei type down|up|press        # 事件类型，默认 press(down+up)
    # --es pkg <jar包名/游戏名>       # 可选：指定目标游戏(默认当前前台游戏)
```

语义键名表（v1）：
`SOFT_LEFT SOFT_RIGHT FIRE UP DOWN LEFT RIGHT UP_LEFT UP_RIGHT
DOWN_LEFT DOWN_RIGHT NUM0..NUM9 STAR POUND A B C D MENU CLEAR`

- 语义名 → J2ME keycode 翻译走**当前游戏的按键家族表**（Phase 2）
- `keycode` 直发保留给调试/探针
- `type` 覆盖事件编排：单发 down（治"抬起也算一步"）、down+up（默认）、
  以及后续的 repeat 参数
- 保留现有 `--es op save|load --ei slot n`（存档/读档，探针用）

**安全面**：接收器从 debug 源集移到 main，`exported=true`（盒子是第三方
进程）。可选 `--es token` 校验（SharedPreferences 里配的开关，默认关）。

#### 模式 B 触摸注入（v1.1，触摸型游戏需要）

```bash
am broadcast -a io.github.sankerxd.jlmodern.KEY \
    --es action POINTER_DOWN --ef x 120.0 --ef y 240.0
# POINTER_UP / POINTER_MOVE，坐标为游戏逻辑分辨率坐标
```

`pointerPressed/pointerReleased` 驱动的游戏不吃 keycode，这条通道补。

---

## 二、内部实现改动

### EmulatorCore 抽取（主体工作）

`MicroActivity` 里真正的"核"是：Displayable 容器 + Canvas/EventQueue +
SnapVm 泵 + SnapHandles/快照 + KeyMapper + 媒体。**壳**是：抽屉、菜单、
vkEditBar、KeyMapperActivity、fragment/Activity 依赖。

- 新建 `:emucore` Gradle module：`org.snapvm.adapter` +
  `javax.microedition.lcdui` 宿主 + `ru.woesss.j2me` 媒体层 +
  `EmulatorCore` 门面；**微 edition UI 视图层里只有 LCDUI 渲染必需的部分**
  进核，VK overlay / 抽屉相关全部留 app 壳
- 门面对内调：`SnapVm.load`/`setFrozen`、`Canvas.keyEvent`/`pointerEvent`、
  `SnapHandles` snapshot、`EventQueue`
- **帧管线回调化**：LCDUI 帧缓冲当前在 `Displayable` 容器里直接画——
  抽一个 `FrameSink` 接口，app 壳实现成现在的 onDraw，核默认给
  `latest-frame` 缓冲 + `onFrame` 回调；`EmulatorView` 是 sink 的一种实现
- **音频**：v1 `INTERNAL_TRACK`（现状 Player→Android media API 不动）；
  `PCM_CALLBACK` 列 v2（MIDI 合成输出可路由，MP3/AAC 走 MediaPlayer 的
  要另接输出层——标开放问题）
- **存档形状**：现有 `SnapHandles`/`.svm` 文件 → 包 `serialize()` 返回
  byte[]（流式写出再吐数组）+ `unserialize(byte[])`——对盒子再暴露
  `saveSnapshot(slot)/loadSnapshot(slot)` 便捷方法
- **依赖切断**：lcdui 层对 `MicroActivity`/`ContextHolder`/`binding` 的
  反向引用是主要工作量——`ContextHolder` 给 headless 模式
  （无 Activity 时用门面注入的替代物）
- **异步泵不动**：SnapVm 泵保持时间片模型；`runFrame()`/`retro_run`
  只取 latest-frame，RA 的同步调用模型用缓冲适配

### RA shim（第二阶段，同核另一消费者）

- `jlmodern_libretro.so`：JNI 层 `System.loadLibrary` + 持有
  `EmulatorCore` jobject；翻译 vtable 调用进 Java 方法
- RA Android 前端的 JavaVM 可直接 `JNI_OnLoad` 拿 env——
  核类从随 so 分发的 dex 加载（dx/d8 一次转）
- 输入：RA 轮询 → shim 里维持按键位图 → 变化边沿 sendKey
- 存档：serialize/unserialize 与 RA 的 savestate 天然同形状

### 模式 B / 数据层改动

| 改动 | 位置 | 说明 |
|---|---|---|
| 接收器转正 | `MicroActivity` + manifest | debug 守卫去掉，action 名定 `io.github.sankerxd.jlmodern.KEY`（新 action，旧的 KEYINJECT 保留兼容） |
| 语义名→码 | `KeyMapper` | 新一层"语义键表"：按当前游戏家族查码；查不到回落 J2ME 标准码 |
| 事件编排 | `KeyMapper`/`Canvas.keyEvent` 调用侧 | `type` 参数控制发 down/up 序列；家族表可声明"该家族抬起不计步"等 |
| novk extra | `MicroActivity.onCreate` 读 intent | `vk.hide()` 或初始化时跳过 VK |

---

## 三、按键家族表（数据层）

在现有 KeyMappings（Android码→J2ME码）旁加**家族层**：

- `assets/keyfamilies/<name>.json` 预置家族表：语义键 → J2ME keycode +
  事件编排参数（press 发不发 up、repeat 节奏）
- 游戏 `config.json` 加 `keyFamily: "<name>"` 字段；缺省 = J2ME 标准表
- 覆盖优先级不变：玩家手改 > 家族表 > 标准
- 家族表内容**由扫描器批量生成**，不靠人肉

已知家族种子（先建这几个，扫描结果再并/拆）：
- `s40`：标准 `-1..-5` + 软键 `-6/-7`
- `se`：软键 `-202/-203`（索爱）
- `moto`：`-21/-22`
- `siemens`：`-59/-61`
- `digits`：纯数字键/`0..9` 硬编码
- `event-up-step`：按下+抬起都算一步的特殊家族（Phase 4 探针识别）

## 四、静态键值扫描器（我们做，供家族表）

工具形态：snapvm 侧 jar 扫描器，`ClassFile` 解析器现成。

```
输入: jar 文件目录
对每 jar:
  1. 遍历类，找 keyPressed/keyReleased/keyRepeated (I)V
     （MIDP 覆写方法名混淆器不能改——override 库方法必须保名，
      个别深度混淆漏网的走 Phase 4）
  2. 解析方法字节码:
     - lookupswitch/tableswitch 对参数1的 case 常量
     - if_icmpeq/if 链中"参数1 vs 常量"的常量(bipush/sipush/ldc)
     - 一层内联: keyPressed 直接调用的同参数方法继续扫
  3. 输出 {监听 keycode 集} → 与家族表相似度匹配归族
产物: 每游戏一行 {game, family|new, keycodes[]} → 批量写进 configs/<>/config.json
```

工作量评估：**小工具，一天量级**——ClassFile/常量池/方法字节码解析
snapvm 已有，主要就是方法体扫常量。间接分发（先存字段再比较）
v1 不追，先统计覆盖率。

## 五、快照探针（动态兜底，验证用）

静态扫不准/归不了族的语义槽位：

```
KEYINJECT op save → 注候选码(type=press 和 down 各试)
→ 观测: repaint 计数 / Displayable 内容变化 / 菜单光标位移
→ op load → 换码
```

- 候选响应检测信号：Canvas repaint 次数、屏幕像素 hash、
  guest 侧菜单索引字段（从静态扫描顺带抠）
- "按一下走几格"用 `type=down` vs `type=press` 对比光标位移即可识别
  ——正好治他说的"点一下走两格"家族
- 自动跑完把确认表写回家族库

## 六、两个消费者的最终集成面

```java
// 消费者①：盒子 AAR（他自己 Activity 内嵌）
EmulatorCore core = new EmulatorCore(context);
core.setVideoCallback((px,w,h,pitch) -> blitToSurface(px,w,h,pitch)); // 或用 EmulatorView
core.loadGame(jarPath);
core.sendKey(SOFT_LEFT_CODE, true);        // 或 sendKeyAction("SOFT_LEFT", PRESS)
core.saveSnapshot(0); core.loadSnapshot(0);
```

```c
// 消费者②：RA libretro .so shim（我们出）
retro_load_game(info)  -> JNIEnv 实例化 EmulatorCore, loadGame(path)
retro_run()            -> 取 latest-frame -> video_refresh_cb(px,w,h,pitch)
retro_input_state()    -> 按键位图边沿 -> sendKey
retro_serialize()      -> byte[] 直通 RA savestate
```

```bash
# 模式 B：第三方轻集成（可选保留）
am start -a VIEW -d jar路径 -t application/java-archive io.github.sankerxd.jlmodern
sendBroadcast(io.github.sankerxd.jlmodern.KEY, action="SOFT_LEFT", type="press")
```

## 七、实施顺序建议

1. **Phase 1（libretro 形 API 面，先交付）**：抽 `EmulatorCore`——
   `loadGame/sendKey/sendPointer/serialize/unserialize/setVideoCallback/pause/resume`
   直接调现有内部件；`onFrame` 回调第一天就在（RA 命门）。
   目标：本仓库内编译出最小核（还不是独立 AAR 也行，先证明 API 面齐）。
   Intent extras/KEYINJECT 转正顺带做。
2. **Phase 2（家族数据层 + sendKeyAction）**：keyFamily 字段 + 预置表 +
   KeyMapper 家族查询 + 事件编排参数。
3. **Phase 3（扫描器）**：snapvm jar 扫描器跑游戏库 → 归族统计 + 家族表种子。
4. **Phase 4（探针）**：自动验证 + 兜底补表（含"点一下走两格"族识别）。
5. **Phase 5（打包）**：AAR 定型（盒子）；libretro .so shim（RA，同核另包）。
   音频 PCM_CALLBACK、触摸注入按需后补。

## 八、验收

- EmulatorCore：demo 壳 Activity 调 API → 时空旅者等游戏运行、
  sendKey/sendPointer 响应正确、serialize/unserialize 往返无损
- onFrame 回调：demo 壳自己拿帧贴图（不依赖 EmulatorView）
- RA shim（后期）：RetroArch 加载 so → 游戏运行 + savestate 往返
- Intent 集成脚本：am start + sendBroadcast 语义键 → 不同家族游戏响应正确
- 扫描器报告：测试 jar 集归族率、每族 keycode 集人工抽查正确
- "点一下走两格"类游戏用 type=down 单发后菜单步进正常
- 玩家手动改键不破坏家族表；现有游戏按键行为零回归

## 九、开放问题

- **音频 v2**：`PCM_CALLBACK` 需要 MIDI 合成输出路由 + MP3/AAC 解码
  输出层改造；v1 用 INTERNAL_TRACK（核内 AudioTrack）在 Android 上够用
- **运行模型**：RA `retro_run` 同步 vs 我们异步泵——latest-frame 缓冲
  方案牺牲的是输入到显示的 ~1 帧延迟，J2ME 游戏可接受
- **帧格式**：libretro 要 RGB565/XRGB8888——LCDUI 帧是 ARGB，shim 里
  做一层转换还是核内直接产 RGB565（GL 输出的话看 shader 链末端）
- **dex 加载**：RA shim 里我们的 Java 类怎么进 RA 进程的 classloader
  ——随 so 发 dex + PathClassLoader，或打包进 RA 的 assets 约定路径
