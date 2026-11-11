# 当前状态

> 更新日期：2026-11-09
> **只记录仓库可证实的事实**。

---

## 2026-11-10：v0.2.9 裁边与切线焦点修复

- 源问题：实机截图中自动裁边橙线越过左侧正文，且显示「第 1/5 条」却看不到实际蓝色切线手柄。
- 修复：新会话默认不开启自动裁边，避免误删边缘文字；用户仍可主动开启。打开精细切线编辑器后，等待滚动布局测量完成再定位当前切线，并按实际可滚动视口计算居中。
- 代码合并：PR #16，合并提交 `f7d7080`。
- 验证：Android 单测 / Debug 构建 / Lint / 有界状态机验证已在 PR #16 的 CI 通过。新版本签名/Release 仍要以正式发布工作流结果为准。
- **已知限制：此修复尚未经 Android 真机交互复验**，不能把 CI 通过等同于已证明切线拖动、裁边显示或全幅导出在所有机型上正确。原图本身已截断的文字不能通过本修复恢复。

---

## 2026-11-10：v0.2.10 自动切点与横向裁边安全补丁

- 真机反馈：v0.2.9 的精细切线页仍可能在表格/正文内部出现蓝线，且启用裁边时橙线靠近头像和边缘文字。
- 根因机制：切点算法原先仅搜索目标高度前的窄窗口；裁边检测按整个长图的平均列能量识别边缘，可能漏掉稀疏内容。
- 本轮修复（PR #18，合并提交 `04ace20`）：全合法区间优先寻找空白带；检测不到可信空白时在处理页和编辑器提示，并用红色切线标记；自动裁边一律保留左右像素，只有显式手工裁边才允许改动横向范围。隐藏边界不再渲染橙色虚假矩形。
- 补充测试：早于目标窗口的低能量间隙、无安全缝的连续表格、切片全覆盖无重叠、自动横向裁边的像素保护规则。
- **未完成的门禁：缺少完整原始长图和新版本的真机导出回读，不能断言所有文字/表格都安全切开。** 如果目标片段高度内没有空白缝，在固定单片最大高度且不允许重叠时，不存在可保证不截断内容的通用算法；界面会提示人工检查而非假装无风险。

---

## 2026-11-11：v0.2.14 撤销重型 OCR / 视觉检测

- 用户实机 v0.2.13 继续复现表格、统计图和图片截断，证据显示新增 45MB 级依赖未达到核心目标；用户明确要求回滚。
- 回滚来源：`v0.2.10` → `bec88053495149c84f187191b0fb2ffbf0c6c8ac`；运行代码和发布工作流以该标签的树为准。
- 保留版本号连续升级：`versionName=0.2.14`、`versionCode=16`，复用原有签名，可覆盖 0.2.13。
- 删去 ML Kit / OCR、后续视觉检测和配套错误防护；切点能力恢复 v0.2.10（低能量空白带+风险提示）。不能宣称图片整块自动识别成功。
- 每日回归针对原版切线、裁边和几何测试；正式发布需验证构建、签名连续性、哈希和 Release 远端回读。

---

## v0.2.15：文件生命周期与发版说明

- 为正在保存相册和打包 ZIP 的切片增加短期读者锁；分享之后为文件及其输出目录刷新 24h 保留时间。
- 自动清理跳过当前预览/输出引用的切片，并且只删除超出保留期的历史缓存。
- 新增 JUnit 生命周期测试，正式发布强制每版独立中文 Changelog，自动追加 APK 大小和哈希。
- CI 仍验证签名连续性和远端 APK 回读；真机分享兼容性需单独验收。

---

## v0.2.16：切线手势命中与无效重算修复

- 总览和精细页实际拖拽命中区从 38dp 扩大到 48dp，视觉圆点仍为 38dp。
- 总览切线的原位点击/取消手势不再触发无意义的重切与 IO；精细页维持拖动零 IO 的既有行为。
- 更新 UI 文案、静态手势验收约束、v0.2.16 中文更新日志。
- 原有图像实际像素坐标和分享链路不变；需使用真实设备进行拖拽、滚动、方向变化验收。

---

## 1. 当前版本

| 项 | 值 | 证据 |
|----|-----|------|
| versionName | **0.2.16** | `app/build.gradle.kts` |
| versionCode | **18** | 同上 |
| applicationId | `com.changjietu.app` | 同上 |
| Adopted covenant | **1.0.0** | `AGENTS.md` |

> 当前版本号用于本轮补丁发布；是否已正式交付以 GitHub Releases 和成功的 publish job 为准。下文 §2.1–2.9 是历史批次记录，不是 2026-11-09 的当前版本。

---

## 2026-11-10：v0.2.8 恢复无效页面导航修复

- 发现处理页和结果页在 Android 恢复旧导航栈而 ViewModel 新建后，显示无源/无切片的两个死胡同；根因机制来自导航状态与内存状态寿命不一致，尚无 logcat 证实实际触发事件。
- 新增 `NavigationRecovery` 纯决策模型：无数据且无活跃加载时从无效页面返回 home；若原图仍在而切片为空，则从结果页返回 process。
- 将 `openUri` 的 Loading 状态提前到启动协程之前，保护正常选图和系统分享的异步加载过程。
- `NavigationRecoveryTest` 和 `tools/verify_state_machines.py` 加入回归门禁；不声明恢复旧编辑内容。见 `docs/state-machines/NAVIGATION_RECOVERY.md`。
- Android 真机进程死亡、旋转、分享恢复仍待复验。

---

## 2026-11-09：v0.2.7 切线导航可见性修复

- 实机截图证实 v0.2.6 的上一条、下一条箭头不可见；替换成固定宽度可见文本与明确的点击区域。
- 中间切点计数增加「定位」交互，支持一键滚动至当前切点。
- 不修改裁切几何、编码及导出流程；真机复验仍须执行，不能以 CI 通过替代。

---

## 2026-11-09：v0.2.6 实机视觉补丁

- 首页选图按钮文本按按钮中心定位；切线页导航改为 48dp 图标触控区；结果页网格占比由 41% 改为 58%。
- 添加源码布局回归测试；UI 的实机复查仍未执行，不以静态测试代替真实视觉验收。
- 三张新截图不能证明裁切完整性与保序，请以输入源图和实际输出为准。

---

## 2026-11-09：v0.2.5 补丁准备

- 修复高清细节预览中的位图所有权错误（独立拷贝，再关闭原解码句柄）；拒绝取消/过期加载结果。
- 未改变切点不再重复导出整个 JPEG 集合；已补充精确像素边界单测。
- 总览分块解码失败改为显式失败并释放输出；界面明确提示预览不可用。
- 设备实测仍待完成。GitHub Actions CI/签名/Release 结果应在合并和发布后独立核对。

---

## 2. 已完成

### 2.1 MVP（0.1.0 基线）

| 能力 | 说明 |
|------|------|
| 选图 | Photo Picker |
| 分享进入 | `ACTION_SEND` / `SEND_MULTIPLE` + `image/*` |
| 质量四档 | 良好 / 可用 / 偏低 / 不可恢复（86px 宽 → 不可恢复） |
| 裁空白边 | 列/行能量启发式 |
| 智能切片 | 投影寻缝 + 重叠 + 角标 `01/N` |
| 预览 | 拖分割线 / 合并 / 删除 / 重新编号 |
| 导出 | MediaStore 保存 + `ACTION_SEND_MULTIPLE` |

### 2.2 v0.2（2026-11-06）

| 能力 | 说明 |
|------|------|
| 屏幕自适应 | `DisplaySpec` 读取真实屏幕像素；单张高度默认 = 屏幕长边，上限硬约束 |
| 缝隙感知 | 低能量空白带检测 + 缝隙宽度/距离/高度评分；尾部过短重排 |
| 不缩放 | 区域解码 sampleSize=1；保留内容宽度与原始像素 |
| 逐张选择 | 预览页单选/全选/取消全选；保存、ZIP、分享只作用于选中项 |
| 相册目录 | 选中切片保存到 `Pictures/changjietu`（API 29+ MediaStore，26–28 直接写入 + 扫描） |
| ZIP | `SliceZipper`（`java.util.zip`，无新依赖）→ `Download/changjietu/*.zip`，可分享 |
| Release | `assembleRelease` + R8 minify + shrinkResources + 本地 Release 签名 |
| UI/UX | 首页/处理页/预览页重设计；图标缩小居中；错误/空态/权限/导出反馈 |

- 构建门：`testDebugUnitTest` **108 用例 / 0 失败**、`assembleDebug`、`assembleRelease`、`lintDebug` 均 PASS（JDK 17；lint 0 errors / 9 warnings）
- 规格文件：[GOAL.md](../GOAL.md)（含 v0.2 增量）

### 2.3 v0.3（2026-11-06，未发版）

| 能力 | 说明 |
|------|------|
| 剪辑式编辑 | 预览页上半部为导入长图的下采样总览，叠加自动切分线；上下箭头手柄无级拖动切点（向下 = 上片多一点/下片少一点）、点击任意处新增切入点、头/尾整体裁切范围手柄；下半部保留切片网格（逐张选择/合并/删除/导出） |
| 长图总览分块解码 | 新增 `OverviewGeometry` + `OverviewRenderer`：按条带 `decodeRegion(sampleSize)` 生成 ≤ 360 × 6000 的 RGB_565 位图，不整图 decode、不交给 Coil 重解码 |
| 编辑数据模型 | `EditorState` 追加 `cropTop`/`cropBottom`；拖动只改内存状态（零 IO、`ProcessTransition` 不受影响），松手一次性重切；`SplitPointDetector` 保持不变 |
| miuix UI | 三个页面迁移 `top.yukonga.miuix.kmp:miuix:0.3.0`（兼容现有 Kotlin 2.0.21 / Compose 1.7.x）；`MiuixTheme` 叠加 `MaterialTheme`；组件 TopAppBar/Scaffold/Card/Button/Slider/SuperSwitch/TextField 等 |
| 状态栏适配 | `MainActivity` 按夜间模式设置 `WindowInsetsControllerCompat` 图标深浅；XML 主题透明系统栏；miuix `SmallTopAppBar` 自带 statusBars insets，`Scaffold(contentWindowInsets=0)` 防双重顶部 padding |
| 角标修复 | `BadgePositionSelector` 永不返回 `HIDDEN`；四角都不够空时回退到能量最低的角，角标始终绘制 |
| 裁空白边优化 | `MarginDetector` 自适应阈值 + 持续段噪点容忍 + 逐轴 clamp（取代「过裁就四边全归零」）+ 近纯空白轴单独拒绝 |
| ZIP 重命名 | 导出前对话框自定义名，`ZipNameSanitizer` 清洗；`SavedZip.file` 统一回填缓存 zip，修 API 26–28 `FileProvider` 分享隐患 |

- 构建门（合入 miuix 时）：`testDebugUnitTest` **123 用例 / 0 失败**、`assembleDebug`、`lintDebug`、`assembleRelease`（R8 + lintVital）均 PASS（JDK 17）
- 主路径静态核查：仅 `ImageSplitter.decodeBounds` 使用 `BitmapFactory.decodeStream`（`inJustDecodeBounds=true`），其余均为 `decodeRegion` 分块解码

### 2.4 v0.3 补洞（2026-11-06，仍未发版）

未重做 2.3 已有的剪辑总览、miuix 0.3.0、ZIP 重命名和「角标不再返回 HIDDEN」。

| 能力 | 说明 |
|------|------|
| 采样能量铺满 | `sampleSpan` 把降采样像素涂回源区间，空白边不再把采样空洞当空白；单侧扫描上限 40% |
| 角标入画 | 过窄切片缩小角标并夹在画布内；能量数组过短时固定右上角，绘制不再因 `HIDDEN` 跳过 |
| 切点手势 | 手柄在按下时拦住外层滚动；拖空白处预览长图；长按新增长图切点 |
| 点击预览 | 点切片图打开可上下滑动的大图；勾选圈仍负责选择 |
| 状态栏 | 关闭 API 29+ 系统栏对比度遮罩；窗口背景对齐 miuix；首页去掉不伸进状态栏的渐变头图 |

- 构建门：`testDebugUnitTest` **127 用例 / 0 失败**、`assembleDebug` PASS（JDK 17）。未重跑 `lintDebug` / `assembleRelease`
- 运行态：无 adb 设备，拖动手感、大图预览、状态栏视觉、ZIP 重命名落盘仍待真机验收

### 2.5 处理页切线预览（2026-11-07，仍未发版）

未改切片、寻缝、裁边算法，也未重做结果页的拖动编辑。

| 能力 | 说明 |
|------|------|
| 首页 | 只留「选择图片」和最近记录。去掉长介绍和常驻质量四档。记录以时间和张数为主，分辨率次之，去掉蓝色「再处理」 |
| 处理页 | 主按钮固定在底部，用 primary 色，文案为「生成 N 张切片」。页码、JPEG 说明和「给 AI 看」收进「更多选项」 |
| 切线预览 | 分析完成后分块生成整图总览；高度、重叠只改切线。裁边在预览上画框。单张时不展示重叠滑块。点击可放大 |
| 文案 | 界面称「清晰度不足」，并说明不是文件损坏。正式等级名「不可恢复」仍留在 `QualityAnalyzer`。JPEG 写明像素尺寸不变但是有损 |

- 构建门：`testDebugUnitTest` **130 用例 / 0 失败**、`assembleRelease`（含 lintVital）PASS（JDK 17）。未单独重跑 `lintDebug`
- 运行态：无 adb 设备。预览、滑块手柄、底部按钮和首页层级未在真机验收

### 2.6 评审优化（2026-11-07，仍未发版）

按 7.8/10 评审继续收细节，未改切片、寻缝、裁边算法核心（`SplitPointDetector`/`ImageSplitter`/`MarginDetector` 未动），未升级 miuix。

| 能力 | 说明 |
|------|------|
| 生成前切线编辑 | 处理页全屏「调整切线」直接拖切点/长按新增，`manualCuts` 经 `buildPreviewPlan`/`processInternal` 同步预览与正式切片；改高度/重叠/裁边恢复自动；上一条/下一条跳转；`SmallTopAppBar` 统一 |
| 编号与容器 | 总览编号收小并移至左侧外缘（`CutPreview`/`PreviewCutEditor`/`OverviewEditor`），总览容器 240→190dp，文案「点击查看并调整切线」 |
| 文案修正 | 首页「保留原始画质」→「原尺寸裁切，不缩放图片」；每张高度「与当前屏幕等高」→「按当前设备屏幕像素高度裁切」；「当前预设」→「当前裁切设置」；重叠说明→「保留连续阅读上下文」；标题「裁切方式」→「裁切设置」 |
| 滑块与快捷 | `ThumbSlider` 轨道 28→6dp、手柄 22→18dp；高度新增「按屏幕高度/紧凑半屏」按钮 |
| 首页层级 | 去掉「选择一张长截图」标题，只留说明+按钮；「最近处理」16→13sp 次级标题；历史缩略图失败显示占位图标+「原图不可用」 |
| 导航栏 | 切片大图弹窗由 Row 换为 `SmallTopAppBar`，与主界面一致 |

- 构建门：`testDebugUnitTest` **130 用例 / 0 失败**、`assembleDebug`、`assembleRelease`（含 lintVital）PASS（JDK 17）。`lintDebug` 6 errors 系既有 `NewApi`（`windowLightNavigationBar`/`enforce*Contrast`，非本轮新增）未修
- 运行态：无 adb 设备。全屏拖拽手感、实时切线同步、上一条/下一条滚动、高度快捷值、历史占位均未真机验收

### 2.7 裁边/切线解耦与保守裁边（2026-11-07，仍未发版）

P0 解耦：`manualCuts` 改 source 坐标 + `SourceCutGeometry` 纯几何（JVM 4 例）；`updateSettings` 不再清空切线，切换裁边/改高度重叠均保留；越界切点仅过滤并提示冲突。保守裁边：`MarginDetector` 加安全内边距（宽/50、上限 24；高/200、上限 16）。黄色框：`manualMargins` 数值可编辑 + 恢复检测值，开关保留；拖框与撤销/持久化未做（超出本轮）。算法核心仍未动，未升级 miuix。

- 构建门：`testDebugUnitTest` **135 用例 / 0 失败**、`assembleDebug`、`assembleRelease`（含 lintVital）PASS（JDK 17）。`lintDebug` 6 errors 系既有 `NewApi`（非本轮新增）未修
- 运行态：已连设备冒烟待补；保守量与冲突提示未真机验收

### 2.8 GitHub 审查 P0 修复（2026-11-07，仍未发版）

P0-A：`previewManualCut` 用可见索引映射（`visibleSource`/`fullIndexOfVisible`），隐藏点不参与拖动。P0-B：`EditorState.appliedMargins` 快照初次有效裁边，重切片沿用该快照，不再推导 `source.margins`。P0-C：`sanitizeMargins` 统一非法矩形（L+R≤W-64，T+B≤H-100）。2D colMax 校验、黄色拖框、撤销/跨页持久化未做。

- 构建门：`testDebugUnitTest` **137 用例 / 0 失败**、`assembleDebug`、`assembleRelease`（含 lintVital）PASS（JDK 17）。`lintDebug` 6 errors 系既有 `NewApi`（非本轮新增）未修
- 运行态：隐藏切点拖动、手动裁边重切片一致性未真机验收

### 2.9 结果页打磨（2026-11-07，仍未发版）

P0 高清区域预览：`OverviewEditor` 增加 `windowTop/windowHeight` 窗口映射，`LongshotViewModel.loadDetailWindow` 按 content 窗口 `sample=1` 分块解码（不整图解码），「精细编辑」全屏 + 上/下一区域。P0 选择保护：同张数重切片保留 `selectedIndices`，张数变才全选并提示。P1 IA：标题「切片结果」、第二行「已生成 N 张·已选择 M/N」、分享主按钮「分享所选·N 张」、ZIP 文案改「导出 ZIP」、选择 26→48dp 热区、合并/删除 36→48dp、说明收至一行（顶/底=截取·蓝=切点）。

- 构建门：`testDebugUnitTest` **137 用例 / 0 失败**、`assembleDebug`、`assembleRelease`（含 lintVital）PASS（JDK 17）。`lintDebug` 6 errors 系既有 `NewApi`（非本轮新增）未修
- 运行态：精细编辑高清手感、窗口切换、选择保留未真机验收

---

## 3. 进行中 / 历史修复

- **C1 / C2：CODE-CLOSED / RUNTIME-PENDING**（无 adb 设备，运行态冒烟待补）
  - C1（进图合同）commit `91a2030`；C2（解码生命周期 + 能量缓存复用）commit `67d6512`
  - C2F1（处理完成一次性导航契约 + C2.2 Evidence Gate）commit `d244f6d`
- **C3（Artifact Size & Build Environment Closure）**：SIZE-CLOSED / BUILD-CLOSED / RUNTIME-PENDING
  - 移除 `material-icons-extended`（~6.9MB / ~39%），本地 `AppIcons` 保留 Image/CallMerge
  - Build contract：AAPT2 override 为仓库可控 `gradle.properties` → `tools/aapt2`；结论边界见 D-010
  - C3F1：`tools/preflight.sh` + wrapper fail-fast + README supported build matrix（D-011）
  - Isolated Gradle-home reproducibility：PASS（非完全 hermetic/cold-network）

---

## 4. 缺口

- 无 adb 设备，运行态冒烟待补；视觉/交互最终由用户安装 APK 验收
- v0.3 剪辑式编辑的拖动手感、总览大图渲染、切片点击预览、ZIP 重命名对话框、状态栏图标深浅均未在真机验证
- 2.5/2.6 的首页、处理页切线预览、生成前全屏编辑、底部主按钮未在真机验收。PNG 无损导出未做
- Release（R8）仅通过构建与 lint，未在真机验证运行时
- `manualCuts` 生成前编辑的预览↔生成一致性仅静态同源（`buildPreviewPlan`/`processInternal` 共用 `rangesFromCutEnds`），未在真机对比
- `ProcessTransition` 导航 wiring（Compose/Activity 层）为 NOT AUTOMATED，仅纯 JVM 契约已测
- 第二阶段功能全部延后（见 GOAL.md §第二阶段）
- Cold-network 全量重下依赖未执行（见 §5.1）

---

## 5. APK 基线

| 项 | 值 |
|----|-----|
| Release 产物 | `app/build/outputs/apk/release/app-release.apk` |
| Release 交付 | `download/changjietu-0.2.0-release.apk`、`/sdcard/Download/changjietu-0.2.0-release.apk` |
| Release 体积 | **1,571,355** bytes（2026-11-07 结果页打磨轮） |
| Release SHA-256 | `b096d11c59f980234983f0cb5ea8bc50087cdf05a88ed521b1e217949b251ec0` |
| Debug 体积 | **14,776,293** bytes（2026-11-07 结果页打磨轮） |
| Debug SHA-256 | `c476c1f6a143c37b3e35047dd9d61e2af89fb3c0f42f02bf59c800fbdec87d51` |
| 测量日 | 2026-11-07 |
| 环境 | JDK 17 · Gradle 8.11.1 · AGP 8.7.3 · Kotlin 2.0.21 |

> APK 字节在不同构建间存在 ZIP 对齐填充非确定性（约 ±11 KB）；以 clean 构建为权威。
> Release 签名 keystore 位于仓库外（`/root/.android/changjietu-release.jks`），`local.properties` 不入库。

---

## 5.1 Cold-Network（cold dependency-resolution）Gate

| 项 | 值 |
|----|-----|
| 含义 | 全新 GRADLE_USER_HOME、无预置 dependency cache，从网络解析全部依赖的 smoke |
| 术语 | **isolated Gradle-home reproducibility**（非完全 hermetic/cold-network reproducibility） |
| 结果 | COLD_NETWORK_FETCH = **NOT VERIFIED**（未执行，未伪造） |
| 影响 | 不阻止 build-contract closure（C3F1 BUILD-CONTRACT-CLOSED） |

---

## 6. 架构红线（不可修改）

1. **禁止 AI 超分恢复**（GOAL.md §B、§H）
2. **禁止整图 BitmapFactory.decode 主路径**（GOAL.md §G）
3. **禁止引入云端/OCR/OpenCV**（GOAL.md §H）

---

## 7. 下一阶段

<!-- 待用户指定 -->

> **注意**：此处记录的阶段仅描述项目路线位置，**不等于**本次会话的执行目标。
> 只有用户当前请求或显式 `/goal` 才能建立 Session Objective。
