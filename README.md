# 长截图处理器（changjietu）

Android 本地工具：长截图质量检测 → 按屏幕分辨率智能切片 → 批量保存 / ZIP 打包 / 分享给 ChatGPT、Grok 等应用。

## 为什么需要

系统或聊天平台转发长截图时，常会把原图压成极窄图（例如 **86 × 1536 px**）。文字已在压缩环节丢失，放大或「AI 超分」不能可靠恢复正文。本工具强调：

1. **源图质量检测**，严重压缩时明确警告
2. **按手机屏幕切片**，单张不超过屏幕长边，不缩放、只裁切
3. **缝隙感知**，优先在文字行/卡片之间的空白带下刀，减少内容截断
4. **逐张选择 + ZIP 打包**，保存到 `Pictures/changjietu` 或 `Download/changjietu`
5. **系统分享入口**，相册一点分享即可处理
6. **分块解码**，避免超长图 OOM

## 状态

- 规格：[`GOAL.md`](./GOAL.md)（含 v0.2 增量）
- 版本：`0.2.13`（versionCode 15；发布状态以 GitHub Releases 为准）
- 包名：`com.changjietu.app`
- 当前交付：Release APK（R8 minify + resource shrink + 本地 Release 签名）
- 验证入口：GitHub Actions 执行 `testDebugUnitTest`、`assembleDebug`、`lintDebug`、`lintVitalRelease`；发布流程另验证 Release 构建、签名连续性与 SHA-256。**未完成 Android 真机交互验收**。

## 反馈渠道（酷安集中帖试点）

计划通过**一条酷安长期反馈帖**集中收集 Bug 与功能建议。用户在原帖评论，内部自动化负责按评论去重归档为私有 GitHub Issue；该入口尚未公布，只有正式发帖后才会填写链接。

- [可直接发布的酷安主帖与回复文案](docs/COOLAPK_FEEDBACK_POST.md)
- [GitHub 反馈接收器与授权操作](docs/COOLAPK_FEEDBACK_OPERATIONS.md)
- [接收器技术说明](docs/COOLAPK_FEEDBACK_PILOT.md)

**当前状态**：GitHub 接收器及受控 Agent 工作流准备中；酷安自动读取、酷安自动回复和外部发布均未启用。不要在 Issue 中放入用户 Cookie、Token 或未脱敏截图。仓库是私有仓库，不应把内部 Release/Issue 链接当作公开下载或反馈入口。

## 功能

| 能力 | 说明 |
|------|------|
| 选图 | Photo Picker |
| 分享进入 | `ACTION_SEND` / `SEND_MULTIPLE` + `image/*` |
| 质量四档 | 良好 / 可用 / 偏低 / 不可恢复（86px 宽 → 不可恢复） |
| 屏幕自适应 | 自动读取设备屏幕像素，单张高度默认 = 屏幕长边，可下调到 50% |
| 不缩放 | 切片保持源图内容宽度与原始像素；超宽源图只提示、不横向裁切 |
| 缝隙感知 | 低能量空白带检测 + 缝隙宽度/距离/高度评分；尾部过短自动重排 |
| 重叠 | **默认 0 px**（严格按切线分割），可选 1–160 px，用于主动保留上下文 |
| 页码角标 | 半透明 `01/N`，自动避开正文（可关闭） |
| 预览 | 逐张选择 / 全选 / 拖切点 / 合并 / 删除 / 重新编号 |
| 保存 | 选中切片保存到相册目录 `Pictures/changjietu` |
| ZIP | 选中切片打包到 `Download/changjietu/长截图_时间戳.zip`，可直接分享 |
| 分享 | `ACTION_SEND_MULTIPLE` + `image/*` + FileProvider URI |
| 分块解码 | `BitmapRegionDecoder`，单片处理完即释放 |

## v0.2.13 真机回归入口

安装新版本后，优先使用此前发生误切的**原始长图**检查“调整切线”：分别观察折线图、整张表格及图片是否被蓝线穿过，并确认生成后的切片与预览一致。若内容跨越安全的最大单片高度，界面仍可能标示风险；该类情况需要人工调整。只上传编辑器截图不足以复核源图实际切点像素坐标。

## v0.2.13 内容优先切线修复

- 用户实测 v0.2.12 仍有切线横穿折线图和整张数据表；原因不只是 OCR，而是旧规划器要求切点落在目标高度的后 45% 区间，目标高度又是硬上限。
- PR #24：保留视觉区域与 OCR 文字行的独立坐标，预览与导出改用同一个 `VisualSeamPlanner`。
- 识别到视觉区域时允许提前在图片前切开；如需保护完整图表，可将单片延长到目标高度的 1.8×，最高 8000 像素，仍保证原图像素覆盖。
- 内容大于安全硬限、识别器漏检或密集连续图文没有合适空隙时，仍可能被拆开，必须检查风险提示。
- 算法层图表/表格/照片回归测试和完整 Android CI 是发版门禁；**缺少原始长图，尚未通过实机逐像素验证**。该版本仍包含 45MB 量级的 bundled OCR。
- 回归说明：[docs/VISUAL_SEAM_REAL_DEVICE_FAILURE_20261111.md](docs/VISUAL_SEAM_REAL_DEVICE_FAILURE_20261111.md)。

## v0.2.12 本机视觉内容避让

- PR #23：新增基于实际像素的轻量视觉区域检测（头像、照片、连接的表格/图表线框），与原有中文 OCR 保护区合并，自动切点优先避开这些结构。
- 使用 BitmapRegionDecoder 逐块检测；无云端上传或新增大模型依赖；视觉与 OCR 任意一个失败可独立降级。
- 改进跨帖竖向时间线误连接和深浅色背景取色；处理页显示视觉区域/文字行数量与降级提示。
- 真实长截图黄金样本与真机视觉验收尚未完成：**不保证所有头像、表格和图片永不截断**。高度超过单张上限的完整内容块，仍可能需要调整。
- 算法级合成 ARGB 像素测试与 Android CI 是发布门禁，不可替代原始截图的真机输出对比。

## v0.2.11 OCR 文字行避让（历史）

[PR #20](https://github.com/ldohgfsdu/changjietu/pull/20) 已增加端侧中文 OCR **文字行位置**避让，不存 OCR 文本；识别进度/可跳过、风险切点直达及 48dp 手柄。预览和正式切片使用相同的文字位置保护区。本版本将通过全量 CI、签名校验和远端发布回读；原始长图与真机性能验收仍需完成。

- 技术与验收：[docs/CONTENT_AWARE_SEAMS_V1.md](docs/CONTENT_AWARE_SEAMS_V1.md)
- 尚未保证表格或图片完整性，输出仍按源像素不缩放；自动横向裁边继续采用谨慎保护策略。
- 使用内置 OCR 会增加 APK 体积，APK 体积和耗时以正式构建、真机实测为准。

## 非目标

云端识别、账号、PDF、AI 修字、OpenCV、重复顶栏去除、滚动录屏。严重压缩源图只警告，**不会假装恢复原文**。

## 构建

> **Supported build matrix（仅列经本项目验证 / 由 wrapper 设计推导的行为）**
>
> | 环境 | 状态 | AAPT2 执行方式 |
> |------|------|----------------|
> | Linux **aarch64**（本项目开发主机） | **已验证** | `tools/aapt2` 经 `qemu-x86_64` 运行 SDK `build-tools/35.0.0/aapt2` |
> | Linux **x86-64** | 设计支持，本主机未运行时验证 | wrapper 直接执行 SDK aapt2 |
> | macOS / Windows / 其他架构 | 未验证、未声明支持 | — |

验证过的 JDK：**17**。构建前可用仓库内 preflight 检查主机契约（fail-fast）：

```bash
tools/preflight.sh
```

构建命令：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

AAPT2 override 是**仓库可控**的：`gradle.properties` 中 `android.aapt2FromMavenOverride=../tools/aapt2`，指向仓库内 `tools/aapt2` 兼容包装（相对路径，非机器绝对路径）。AAPT2 来源为 Android SDK `build-tools`（35.0.0），非第三方二进制。

**前提条件（preflight 会逐项检查）：**

1. **JDK 17**。安装后设 `JAVA_HOME` 指向 JDK 17；`java -version` 应显示 major 17。
2. **Android SDK Build Tools 35.0.0**。确认存在：`ls "$ANDROID_HOME/build-tools/35.0.0/aapt2"`。
3. **Linux ARM64 额外需要 `qemu-user`**（提供 `qemu-x86_64`）。确认：`qemu-x86_64 --version`。

项目**不要求**编辑 `~/.gradle/gradle.properties`。Gradle 的 user-level 或 `-P` 同名 property 仍可按正常 precedence 覆盖项目默认值。

### Release 签名

Release 构建从 `local.properties`（已 gitignore）读取本地签名配置，keystore **不入库**：

```properties
changjietu.release.storeFile=/absolute/path/to/changjietu-release.jks
changjietu.release.storePassword=...
changjietu.release.keyAlias=changjietu
changjietu.release.keyPassword=...
```

也可用环境变量 `CHANGJIETU_RELEASE_STORE_FILE` 等。未配置时回退 debug 签名，保证他人仍可构建安装包；正式分发前应配置自己的 Release keystore。

## 安装与交付

```bash
# 先在 Releases 页面下载对应版本的 APK 到当前目录
adb install -r changjietu-0.2.13-release.apk
```

- 正式分发：GitHub Releases（APK 和同名 `.sha256` 两个资产，不将 APK 写入源码仓库）
- 设备安装：从 GitHub Releases 下载 APK，保持 Release 签名连续性
- 构建产物：`app/build/outputs/apk/release/app-release.apk`

## 推荐使用路径

> 相册 / 任意 App → **分享** → **长截图处理器** → 看质量提示 → 开始处理 → 选择切片 → 保存相册 / 打包 ZIP / 分享

不要把已被聊天软件压扁的图当作「原图」。

## 切片规则

- **单张高度上限** = 设备屏幕长边（例如 1080 × 2400 的设备，单张最高 2400 px）；可在处理页下调到 50%
- **宽度** = 源图内容宽度（裁边后），不缩放；若源图宽于手机屏幕，界面会提示但保留完整宽度
- **重叠** = 默认 0 px（无重复）；需要上下文才主动设为 1–160 px；切点优先落在低能量空白带，尾部过短时重排为更均衡的两片
- **格式** = JPEG 95%，原始分辨率；不做 AI 超分、不做 OCR

## 质量等级

| 等级 | 条件 |
|------|------|
| 良好 | 宽度 ≥ 1080 |
| 可用 | 720–1079 |
| 偏低 | 360–719 |
| 不可恢复 | 宽度 < 360，或估计字高过小 |

「不可恢复」会持续警告；仍可强制切片，但工具不会暗示恢复原文。

## 模块包结构

```
app/src/main/java/com/changjietu/app/
├── feature/importui|editor|export|history
├── image/analysis|processing
├── core            # DisplaySpec / 分享与处理契约
├── ui/theme
└── MainActivity.kt
```

## 协作文档

1. `AGENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/DECISIONS.md`
4. `GOAL.md`

## v0.2.10 自动切点与横向裁边安全补丁

- 在片段允许的更大范围查找空白带，减少表格或文字被迫切开的情况。
- 没找到可确认空白带时，处理页提醒人工检查，编辑器把对应切线标红。
- 自动裁边默认只裁上下，始终保留左右像素；手动横向裁边仍可用。
- 新增切线与裁边回归测试，源码 CI 通过后才可发布；**仍需要用原始长图复测最终导出**。

## v0.2.9 裁边/切线可见性安全补丁

- 默认保留整张源图，不自动裁去可能包含文字的边缘；用户可主动启用自动裁边。
- 进入精细切线编辑器时自动定位蓝色切线/手柄，并修正顶部导航高度导致的滚动位置偏差。
- CI 自动验证通过；Android 真机拖动、图片导出和不同设备 UI 尚需复验。

## v0.2.8 进程重建导航恢复

- 修复 Android 恢复旧页面、但原图与切片内存状态已经丢失时，结果页和裁切页连环空白提示：自动返回可用的首页或处理页。
- 保留合法的选图/外部分享加载状态，避免新图尚未解析就被误判为数据丢失。
- 添加导航恢复单元测试与源码状态顺序回归门禁；**不会恢复系统回收前未持久化的手动切线或切片**。
- 实机进程死亡/旋转/分享复验仍需执行。

## v0.2.7 调整切线导航可见性修复

- v0.2.6 实机截图确认「上一条」「下一条」图标未正常显示，改为固定 78dp 宽的文字入口和 48dp 高触控区。
- 中间「第 X/N 条 · 定位」可直接滚动到当前切线，首个切点不再必须靠手动滚动查找。
- 保留原图裁切算法、相册保存、ZIP 和分享行为；单元测试仅能阻止源码结构回退，仍需真机视觉/交互复验。

## v0.2.6 实机截图 UI 补丁

- 首页选图按钮去掉造成文字偏右的前置装饰图标；文本在整个按钮宽度内居中。
- 切线调整页上一条/下一条改为 48dp 图标热区，保持 52dp 紧凑导航行。
- 结果页总览与网格比例调整为 42%/58%，为切片选择与管理留出更多可视面积。
- 添加静态布局回归测试；**实际手机视觉、字体缩放、触控手感仍需实机确认**。

## v0.2.5 正确性修复

- 高清精细编辑复制独立位图快照后再释放区域解码句柄，防止持有 recycled Bitmap；切换区域时清除旧图，并拒绝过期任务回写。
- 切线像素边界不变时跳过整批 JPEG 重编码。
- 总览图分块解码失败时停止并报告预览不可用，不再把残缺白图当成完整结果。
- 本轮只保证代码和 CI 验收；真实 Android 图像渲染、拖拽手感、相册和跨应用分享仍需设备验收。

## 裁切与分享顺序（v0.2.4）

- 默认 **0px 重叠**：每条蓝色切线是上片的 **exclusive end**，下一片从同一 Y 坐标开始；两张图片不会因为隐藏的默认重叠重复正文。
- 可主动输入非零重叠（1–160px）用于 AI 上下文；这时重复区域是**明确选择的行为**，不是额外偷偷裁进来的区域。
- 内容分析优先在低能量空白带中选择切线；如果原图没有安全空白带，仍可能需要人工精细编辑，应用不能保证任意截图完全不截字。
- 分享、相册、ZIP 均按源图从上到下排序，ZIP 内的切片文件名含 01/N 等序号。Android 的 `ACTION_SEND_MULTIPLE` 不能约束**接收应用**是否重新排序；严格保持顺序请使用 ZIP 或按文件名顺序逐张导入。
- 预览低分辨率不代表输出被缩放；最终输出按原图像素矩形解码，逐片校验结果尺寸。
