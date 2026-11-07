# 重大决策（ADR 简表）

> 只记录**已确认**或明确 **Pending decision** 的项。
> 无法从仓库与基线文档确定的，**不得擅自拍板**。

图例：`Accepted` = 有代码或基线明确采纳 · `Pending decision` = 待产品确认

---

## D-001 · 采用 AgentStandards 工程公约

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 采用 AgentStandards 工程公约 1.0.0 |
| **依据** | 2026-07-25 项目迁移 |

---

## D-002 · 主入口为系统分享菜单

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 主入口为 `ACTION_SEND` / `ACTION_SEND_MULTIPLE` + `image/*`；首页选图为辅助入口 |
| **依据** | GOAL.md §A |

---

## D-003 · 分块解码架构

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 超长图必须使用 `BitmapRegionDecoder` 按区域解码；禁止整图 `BitmapFactory.decode*` 主路径；目标处理至少 1440 × 50000 不 OOM |
| **依据** | GOAL.md §G |

---

## D-004 · 禁止 AI 超分恢复

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 禁止实现或暗示「AI 超分恢复原文」；源图严重压缩时只能警告 |
| **依据** | GOAL.md §B、§H |

---

## D-005 · 禁止云端/OCR/OpenCV

| | |
|--|--|
| **状态** | Accepted |
| **决策** | MVP 及后续不做云端处理、账号、AI 修复文字、复杂 OCR、OpenCV 依赖 |
| **依据** | GOAL.md §H |

---

## D-006 · MVP 范围

| | |
|--|--|
| **状态** | Accepted |
| **决策** | MVP 仅做「给 AI 看」模式；阅读和存档模式为第二阶段 |
| **依据** | GOAL.md §C |

---

## D-007 · C2 解码生命周期与能量缓存复用

| | |
|--|--|
| **状态** | Accepted（commit `67d6512`） |
| **决策** | ①`computeEnergyMaps` 改用 `handle.use{}` 词法所有权（任何成功 handle 恰好 close 一次），移除可能跳过 `y` 递增导致死循环的裸 `continue`；②源图 raw `columnEnergy`/`rowEnergy`（source-invariant）缓存进 `SourceInfo`，由 `analyzeUri` 计算一次、`processInternal` 复用，消除重复全图能量扫描；③删除 `onProcessed` 死回调 |
| **依据** | 规格 `changjietu0.2.md` C2.1/C2.2/C2.3；构建门 80 用例/0 失败 |

---

## D-008 · C2F1 处理完成一次性导航契约（ProcessTransition）

| | |
|--|--|
| **状态** | Accepted（commit `d244f6d`） |
| **决策** | 处理完成不再用「slices 非空 且 非 processing」推断（slices 是持久业务状态，返回后旧 slices 仍在，会把用户弹回 preview）。改为 `ProcessTransition`（core）pending id + ack：仅初始 `startProcess` 成功结束时 `markCompleted()` 产生单调递增 pending id；MainActivity 用 `LaunchedEffect(pendingProcessResultId)` 观察一次导航进 preview 后 `ackProcessResult` 清空。失败不产生 pending；手动切片编辑（`reprocessFromRanges`）不产生 pending；ack 仅当 id 匹配当前 pending 才生效，旧 ack 不清除新 pending。借鉴 C1 pending/ack 思路但保持最小、独立，不泛化为全局事件系统 |
| **依据** | C2.3 六场景验收；`ProcessTransitionTest`（纯 JVM）9 例 + `C2EvidenceGateTest` 5 例；构建门 94 用例/0 失败 |

---

## D-009 · C3 移除 material-icons-extended（尺寸优化）

| | |
|--|--|
| **状态** | Accepted（C3） |
| **决策** | clean A/B 实测：移除 `material-icons-extended` 使 debug APK 从 17,710,094 → 10,828,657 bytes（-6,881,437 / -38.9%）。项目仅用 5 个图标，其中仅 Image 与 CallMerge 为 extended-only（material-icons-core=0）；新增本地 `AppIcons`（ImageVector）提供这 2 个图标，视觉语义与 AndroidX 一致。其余 ArrowBack/Delete/Share 来自 material-icons-core（material 传递依赖保留）。依赖为真正大头，故移除 |
| **依据** | C3.1 size attribution（zip 分类 + AAR 逐图标核对 + clean A/B）；94 用例/0 失败、assemble/lint PASS |

---

## D-010 · C3 Build Contract：AAPT2 override 仓库可控

| | |
|--|--|
| **状态** | Accepted（C3），结论边界由 C3F1 修订 |
| **决策** | 普通构建曾依赖用户级 `~/.gradle/gradle.properties` 的 `android.aapt2FromMavenOverride`（机器绝对路径），属外部配置。AGP 通过 `ProviderFactory.gradleProperty` 读取该属性（`Project.extra` 无法注入），故仓库可控方式是静态 `gradle.properties`。改为提交 `gradle.properties`：`android.aapt2FromMavenOverride=../tools/aapt2`（相对路径，指向仓库内 `tools/aapt2` 包装）。包装脚本自检测架构：x86-64 直接运行 SDK `build-tools/35.0.0/aapt2`，ARM64 经 `qemu-user` 运行。**结论边界**：本项目不再**要求**用户在 `~/.gradle/gradle.properties` 配置 AAPT2 override（required dependency eliminated）；strict isolated build（fresh GRADLE_USER_HOME、无 user override）可成功。这**不等于**「外部 override 不可能」。Gradle 中 user-level `GRADLE_USER_HOME/gradle.properties` 与命令行 `-P` 对同名 property 优先于 project-root property，本项目不、也无法屏蔽合法的 user/`-P` override——用户若自行提供同名 property，仍按正常 Gradle precedence 覆盖项目默认值。代价：所有平台改用 SDK aapt2（非 AGP Maven aapt2），已文档化 |
| **依据** | C3.2 provenance + C3.3 方案评估 + C3.4 strict isolated Gate PASS（fresh GRADLE_USER_HOME、无用户级 gradle.properties，APK SHA `04ced785…` 复现）；C3F1 修订结论边界 |

---

## D-011 · C3F1 Build Environment Preflight & Cold-Host Contract

| | |
|--|--|
| **状态** | Accepted（C3F1） |
| **决策** | 把隐含 host prerequisites 变成明确、可验证的仓库契约，不做新构建方案。新增 `tools/preflight.sh`（独立 repo 脚本，fail-fast，缺失即报错并给 remediation；不把环境探测塞进业务 Gradle logic）；`tools/aapt2` 包装脚本做最小 fail-fast（ARM64 无 `qemu-x86_64`、或 SDK aapt2 不可执行时，给出确定错误而非 obscure daemon 失败）。契约项：Java major=17（已验证 JDK）；SDK root 可解析；`build-tools;35.0.0` 存在；SDK aapt2 存在、可执行并验证 `aapt2 version`；host arch；ARM64 下 `qemu-x86_64` 存在及其 version；wrapper 解析到的真实 AAPT2 path 与 SHA-256。Gradle property precedence 按 D-010：required dependency eliminated ≠ external override impossible。README 明示 supported build matrix，不声称未验证的平台 |
| **依据** | 真实主机 preflight PASS（qemu 10.2.1 / Build Tools 35.0.0 / aapt2 2.19-11948202 / SHA `d1096e11…` / arch aarch64）；negative 测试通过：隐藏 qemu → missing-qemu（exit 1）、SDK 无 build-tools/35.0.0 → missing-build-tools（exit 1）、错误 JDK(25) → 识别并 fail（exit 1） |

---

## D-012 · v0.2 屏幕自适应切片 + 缝隙感知

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 新增 `DisplaySpec`：读取设备真实屏幕像素（API 30+ `maximumWindowMetrics`，旧版 `getRealMetrics`），单张高度默认 = 屏幕长边，且为硬上限；处理页可下调到 50%。切片只裁切不缩放（区域解码 `sampleSize=1`），保留内容宽度；源图宽于屏幕时提示但保留完整宽度。切点选择改为能量感知：识别低能量空白带，按「缝隙宽度 + 距目标距离 + 高度合理性」评分；尾部过短时在均衡窗口内重排为两片；保留无能量数据的旧几何路径以维持历史契约。 |
| **依据** | GOAL.md v0.2 增量；`SplitPointDetectorEnergyTest` 7 例 + 既有 14 例（各几何下 `sliceHeight <= target`、重叠 100–160、切点落在空白带）；`testDebugUnitTest` 108/0。**参考**：GitHub `yunkai/long-image-slicer`（MIT）的 gap scoring 思路，仅借鉴设计，Kotlin 独立实现，未复制代码；未新增依赖。 |

---

## D-013 · v0.2 逐张选择 / ZIP / changjietu 相册

| | |
|--|--|
| **状态** | Accepted |
| **决策** | `EditorState.selectedIndices` 默认全选，预览页支持逐张切换与全选/取消全选；保存图片、打包 ZIP、分享只作用于选中切片。图片保存到 `Pictures/changjietu`：API 29+ 走 MediaStore（`IS_PENDING` 失败清理孤儿行），API 26–28 直接写公共目录并请求 `WRITE_EXTERNAL_STORAGE` + MediaScanner。ZIP 使用平台 `java.util.zip`（无新依赖），先写 `.part` 再替换，保存到 `Download/changjietu`；分享 ZIP/多图统一 FileProvider + ClipData。 |
| **依据** | GOAL.md v0.2 增量；`SliceZipperTest` 3 例（内容完整、重名去重、失败不留半截 ZIP）；`assembleRelease`、`lintDebug` PASS。 |

---

## D-014 · v0.2 Release 构建与本地签名

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 版本升至 `0.2.0` / versionCode 2；Release 启用 R8 minify + resource shrink。签名配置从 gitignored `local.properties` 或环境变量读取，keystore 存放于仓库外；缺失时回退 debug 签名以保证可构建。交付 APK 固定命名到 `download/` 与 `/sdcard/Download/`，并附 `.sha256`。 |
| **依据** | `assembleRelease` PASS；`apksigner verify` 证书 `CN=changjietu`；APK 1,400,598 bytes / SHA-256 `e71c304c…`；lintVitalRelease PASS。运行时未在真机验证。 |

---

## D-015 · v0.3 采用 miuix 0.3.0（不升级工具链）

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 三个页面改用 `top.yukonga.miuix.kmp:miuix:0.3.0`（Maven Central），不改 Kotlin 2.0.21 / Compose BOM 2024.10.01 / AGP 8.7.3。主题为 `MaterialTheme` 外层 + `MiuixTheme` 内层（残留 M3 的 SnackbarHost/ProgressIndicator 仍需 MaterialTheme 取色）。 |
| **依据** | 最新版 miuix 0.9.4 的 POM 要求 Kotlin 2.4.20 / Compose Multiplatform 1.12，与已验证的构建契约（D-010/D-011）冲突；0.3.0 用 Kotlin 2.0.21 / CMP 1.7.1，其 Android 变体经 `available-at` 落到 `androidx.compose.*` 1.7.5，与现有 BOM 兼容。已实测 `assembleDebug` / `assembleRelease` 通过。用户确认「兼容版、不动工具链」。 |

---

## D-016 · v0.3 剪辑式编辑与长图总览

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 预览页不再用折叠滑块 `CutAdjustCard`，改为在长图下采样总览上叠加切点线。总览由 `OverviewRenderer` 按 1024 输出像素高的条带 `decodeRegion(sampleSize)` 生成 ≤ 360 × 6000 的 RGB_565 `ImageBitmap`（ViewModel 持有，直接 `Image(bitmap=...)` 显示，不走 Coil 避免按 composable 尺寸重解码 OOM）。坐标统一线性映射 `contentY = pointerY / viewHeightPx * contentHeight`。`EditorState` 追加 `cropTop`/`cropBottom`；拖动只更新内存状态（零 IO、不触发 `ProcessTransition.markCompleted()`），松手由 `commitCuts()` 一次性重切；重建 ranges 时把 `rangesFromCutEnds` 的 `contentHeight` 传为 `cropBottom`，避免末尾补回全高。切片网格保留用于选择/合并/删除/导出。 |
| **依据** | 用户要求「像剪辑一样」直接在导入长图上体现自动切分位置、上下箭头手柄无级调节、点击新增切入点、头尾自定义截取范围。架构红线 D-003（禁止整图 decode）约束总览也必须分块解码。 |

---

## D-017 · 角标永不隐藏

| | |
|--|--|
| **状态** | Accepted |
| **决策** | `BadgePositionSelector.choose` 保留 TR→BR→TL→BL safe-enough gate，但四角都不够空时不再返回 `HIDDEN`，改为回退到 `(列均值 + 行均值)` 最小的角（平局取 TR）；`HIDDEN` 枚举仅保留兼容。角标半透明底，压到内容上可接受，但不可整张切片缺号。 |
| **依据** | 「角标偶见失效」根因：满版内容/深色高能量切片四角均无法通过 `SAFE_FACTOR=0.5` gate，`SliceRenderer` 遇到 `HIDDEN` 直接跳过绘制。`BadgePositionSelectorTest` 中 2 个断言 HIDDEN 的用例改为断言 TR，并新增「回退取能量最低角」用例。 |

---

## D-018 · v0.3 裁空白边 / ZIP 重命名

| | |
|--|--|
| **状态** | Accepted |
| **决策** | ① `MarginDetector` 保持 `detect(...)` 签名，改自适应阈值（中段中位数推导，带上下限）、持续段噪点容忍（连续 4 个高能量样本才认定正文开始）、逐轴 clamp（只压缩超限轴，取代四边全归零）、近纯空白轴单独拒绝。② ZIP 导出前用标准 `Dialog`（miuix 0.3.0 的 `SuperDialog` 不会自动 dismiss）输入自定义名，经 `ZipNameSanitizer` 清洗后传给 `saveZipToDownloads`；缓存 ZIP 文件名不变。③ `SavedZip.file` 统一回填缓存 zip，避免 API 26–28 用公共 Downloads 副本走 `FileProvider` 抛异常。 |
| **依据** | 用户要求「裁剪空白边缘再优化」「导出压缩包重命名」；`MarginDetectorTest` 保留 2 个既有用例 + 4 个回归用例，`ZipNameSanitizerTest` 6 例，全部 123 用例通过。 |

---

## 变更记录

| 日期 | 变更 |
|------|------|
| 2026-07-25 | 项目迁移至 AgentStandards 1.0.0；提取 GOAL.md 已有决策 |
| 2026-08-16 | 记录 D-007（C2） |
| 2026-08-16 | 记录 D-008（C2F1） |
| 2026-08-16 | 记录 D-009、D-010（C3） |
| 2026-08-16 | D-010 结论边界修订 + 记录 D-011（C3F1） |
| 2026-11-06 | v0.2：记录 D-012（屏幕自适应 + 缝隙感知）、D-013（选择/ZIP/相册）、D-014（Release 构建） |
| 2026-11-06 | v0.3：记录 D-015（miuix 0.3.0 选版）、D-016（剪辑式编辑与总览分块解码）、D-017（角标永不隐藏）、D-018（裁边优化/ZIP 重命名） |
| 2026-11-06 | v0.3 补洞：记录 D-019（采样能量铺满、角标缩放、切点手势、点击预览、状态栏对比度） |
| 2026-11-07 | 记录 D-020（首页减法、处理页重组、切线预览；不改裁切算法） |
| 2026-11-07 | 记录 D-021（生成前全屏切线编辑、文案/滑块/首页层级收口） |
| 2026-11-07 | 记录 D-022（裁边/切线 source 坐标解耦、保守裁边、黄色框数值编辑） |

---

## D-019 · v0.3 补洞：采样能量、角标入画、切点手势、点击预览、状态栏

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 不重做已落地的剪辑总览、miuix 0.3.0、ZIP 重命名和「角标不再返回 HIDDEN」。补四件事：①`computeEnergyMaps` 把每个采样像素涂满它代表的源区间（`sampleSpan` + 行能量 `paintMax`），空白边单侧扫描上限从 25% 提到 40%，避免采样空洞被当成可裁空白或把正文裁进一半；②角标按切片尺寸缩小并夹在画布内，能量数组过短时回退右上角，绘制路径不再因 `HIDDEN` 跳过；③切点/裁切手柄在指针 Initial 阶段消费按下，外层纵向滚动不再抢走拖动；长按长图新增切点；点切片图打开可滚动大图，勾选圈仍负责选择；④关闭 API 29+ 状态栏/导航栏对比度遮罩，窗口背景与 miuix 背景对齐，首页去掉不伸进状态栏的渐变头图。不升级 miuix（D-015），不新增依赖。 |
| **依据** | 对照未提交的 v0.3 代码后只补缺口。miuix 0.3.0 源码（Apache-2.0）里 `SmallTopAppBar` 已对内容做 `statusBars` padding，双重 padding 会把标题顶下去，因此页面仍把 Scaffold `contentWindowInsets` 设为 0。参考 Android targetSdk 35 边到边的对比度遮罩行为，只关遮罩、不自绘一套顶栏。空白边算法仍是本仓库能量启发式，未复制 GPL 项目代码。`testDebugUnitTest` 127 例 / 0 失败，`assembleDebug` PASS。无 adb 设备，拖动手感与状态栏视觉未在真机验收。 |

---

## D-020 · 处理页以切线预览为主，首页只留导入和记录

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 不改 `ImageSplitter` / `SplitPointDetector` / `MarginDetector`。首页去掉长介绍和常驻「源图质量四档」，首屏是「选择图片」和最近记录；记录行不再放蓝色「再处理」，点整行仍是重新打开这张图（历史里只存 URI，没有可回放的结果页）。处理页：分析完成后用 `OverviewRenderer` 做整图分块总览，高度和重叠只移动切线；裁边开关在预览上画框，不重新解码。主按钮用 primary 色固定在底部，文案是「生成 N 张切片」。单张时不展示重叠滑块。页码、JPEG 说明和「给 AI 看」收到「更多选项」，不做成可切换的未实现模式。质量枚举和 summary「不可恢复」保持不变（测试与 GOAL 锁定）；界面说「清晰度不足」，并写明不是文件损坏、放大不能恢复已丢失的文字。JPEG 文案改为「像素尺寸不变，但是有损」。滑块补上手柄，高度和重叠可以输入数字。不增加 PNG 导出。 |
| **依据** | 2026-11-07 对首页截图和设置页录屏的启发式评审。阅读/存档模式仍未实现（D-006），所以不展示可选入口。`testDebugUnitTest` 130 例 / 0 失败，`assembleRelease` PASS。无 adb 设备，预览和按钮层级未在真机验收。 |

---

## D-021 · 生成前全屏切线编辑与评审收口

| | |
|--|--|
| **状态** | Accepted |
| **决策** | 仍不改 `ImageSplitter`/`SplitPointDetector`/`MarginDetector` 算法核心，也不升级 miuix。新增 `manualCuts`（`LongshotViewModel` 状态，content 坐标，不含末端）：`buildPreviewPlan` 与 `processInternal` 共用 `rangesFromCutEnds` 生成 ranges，保证预览↔生成同源；改 target/overlap/crop 或换图时清空回自动；全屏 `PreviewCutEditor` 拖 ⇅/长按新增/上一条下一条跳转；总览容器 240→190dp，编号 32×18→22×14 并移左侧外缘；滑块轨道 28→6dp、手柄 18dp；高度加「按屏幕高度/紧凑半屏」；文案按评审修正（「原尺寸裁切，不缩放图片」「按当前设备屏幕像素高度裁切」「当前裁切设置：保留重叠 · 自动编号」「保留连续阅读上下文」「裁切设置」）；首页去掉中间标题、最近标题降至 13sp、历史图失败占位；切片大图与全屏切线统一 `SmallTopAppBar`。 |
| **依据** | 2026-11-07 第二轮启发式评审（7.8/10），P0 生成前编辑、P0 文案、P1 滑块与首页层级、P2 导航栏统一。`testDebugUnitTest` 130 例 / 0 失败，`assembleDebug`、`assembleRelease`（lintVital）PASS。无 adb 设备，手感与滚动定位未真机验收。 |

---

## D-022 · 裁边/切线解耦与保守裁边

| | |
|--|--|
| **状态** | Accepted |
| **决策** | `manualCuts` 改为 source-Y 并抽 `SourceCutGeometry` 纯几何；`updateSettings` 不再清空切线（裁边开关/高度/重叠均保留，越界仅过滤+冲突提示）；新增 `manualMargins` 数值可编辑黄色框（开关保留、恢复检测值清空）；`MarginDetector` 加保守安全内边距（宽/50≤24、高/200≤16）。范围外：拖框、撤销重做、跨页面持久化、colMax 检测未做。 |
| **依据** | 2026-11-07 第三轮评审：裁边开关重置切线记 P0，右 191px 误裁疑虑。`testDebugUnitTest` 135/0、`assembleDebug`/`Release` PASS；`lintDebug` 6 NewApi 既有。 |
