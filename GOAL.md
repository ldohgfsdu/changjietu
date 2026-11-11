# /goal — 长截图处理器 Android MVP

## 后续增量：本机视觉结构检测（2026-11-11）

用户进一步要求真实识别头像、照片、表格和统计图表的结构，反复使用样本检验切点，并以签名 APK 发布为交付闭环。允许引入**无需新增模型和云端服务**的轻量图像连通域与结构检测，和已批准的 OCR 联合生成切线保护区。

此阶段视觉检测不等于语义文档理解。必须保留复杂内容误检/漏检警告、源图像素完整覆盖、手动切线调整以及 1440×50000 量级不整图解码约束；发布必须由 CI 验证签名连续性及 APK SHA-256。未有原始长图黄金样本和真机回归，不宣称所有内容块绝不被切断。

---

## 已批准的后续增量：端侧内容感知切点（2026-11-10）

用户明确批准在 MVP 之上实施：**本机 OCR 仅识别文字行位置，用于切线禁区**，并改进切线编辑体验。本增量覆盖下文 MVP §H、Non-goals、v0.2 禁用 OCR 的历史约束；原 MVP 实现记录保留，不得把旧禁止项解释成当前仍禁止端侧文字定位。

- 仅内置端侧中文 OCR（Google ML Kit bundled）；不上传截图、不保存识别文本或 Markdown、不生成任何猜测字符。
- OCR 逐条带 RegionDecoder 解码，保持 1440×50000 的内存防线。默认自动识别，界面显示进度，允许跳过/关闭；无法识别要显式提示。
- 检测出的文字框转换为全图 Y 像素保护区，与水平能量共同优化切点。**预览与最终切片必须使用相同的保护区及坐标系**。
- 表格/图像暂缺成熟可验证的端侧分块识别时，继续告知风险，不能把“文本不被截断”冒充“全部内容块绝不会截断”。
- 新增单元测试锁定文字保护、坐标映射、无可靠切线时的回退、无重叠完整覆盖和旧发布工作流不变。正式上线需真实长图/真机输出复验。
- 此轮不加入大于 GB 级的 VLM、不引入云服务与 OpenCV、不处理 PDF。

---

## 一句话目标

交付可安装的 Android 本地 App「长截图处理器」：从相册或系统分享接入长截图 → 源图质量检测与警告 → 分块解码智能切片（保留重叠）→ 预览微调 → 批量保存 / `ACTION_SEND_MULTIPLE` 一键分享到 ChatGPT、Grok 等应用。不引入云端、OCR、AI 修图。

---

## Goal kind

`code-change`（从空仓库脚手架到可 `assembleDebug` 的 MVP）

## 产品动机（不可省略的硬约束）

平台分享长图时会把原图压成极窄缩略图（真实案例：`86 × 1536 px`）。文字信息在生成/转发环节已丢失，单纯放大或「AI 超分」只能猜字，**不可作为可靠恢复**。工具必须：

1. **明确警告**严重压缩源图，禁止假装增强后恢复了原文。
2. **主入口是系统分享菜单**，而非仅 App 首页。
3. **分块解码**，禁止整图 ARGB 常驻内存（例：`1440 × 50000 × 4 ≈ 288 MB` 易 OOM）。

---

## Acceptance criteria（MVP 完成定义）

### A. 接入与入口

1. 首页可「选择图片」（Photo Picker / 系统图库）。
2. 注册 `intent-filter`：`ACTION_SEND` / `ACTION_SEND_MULTIPLE` + `image/*`，出现在系统分享菜单。
3. 从分享进入时直接进入处理流（无需先打开首页再选图）。

### B. 源图质量检测（最关键）

打开图片后**先**展示质量面板（无需等切片完成）：

| 等级 | 条件 | UI 文案要点 |
|------|------|-------------|
| 良好 | 宽度 ≥ 1080 px | 可继续处理 |
| 可用 | 宽度 720～1079 px | 可用，建议原始截图 |
| 偏低 | 宽度 360～719 px | 文字可能不清 |
| 不可恢复 | 宽度 < 360 px，**或**正文估计字高过小 | **无法可靠识别正文**；建议从原始截图直接分享到本工具 |

- 必须显示：`原图：W × H px`、质量等级、结论、建议。
- 「不可恢复」不得隐藏；用户仍可强制切片，但每次预览/导出页保留醒目警告。
- **禁止**实现或暗示「AI 超分恢复原文」。

字高启发式（MVP 可简化）：在若干采样行上估计非背景笔画高度；若估计字高 < 8 px 且宽度 < 720，可升为「不可恢复」或至少「偏低」并附说明。

### C. 模式（MVP 只完整做「给 AI 看」）

| 模式 | MVP |
|------|-----|
| **给 AI 看** | 必做：默认 4～12 张；保持原始（裁边后）宽度；单张约 1～2 屏高；相邻重叠；自动编号 `01/N`…；`ACTION_SEND_MULTIPLE` |
| 阅读 | 可有入口占位或最小裁边；分割线拖动在预览页统一支持即可 |
| 存档 | MVP：多张 PNG/JPEG 批量保存；PDF/ZIP/长图拼接/OCR **第二阶段** |

处理页可配置（有合理默认）：

- 单张目标高度：默认约 `2200` px（可按屏幕高度 × 1.5～2 估算）
- 相邻重叠：默认 `120` px（范围约 100～160）
- 裁剪纯色/空白边：默认开
- 去重固定顶栏：MVP **不做算法**，开关可隐藏或 disabled「即将推出」

### D. 智能分割（非固定高度生切）

1. 读取尺寸；**不**一次性加载整张超长 Bitmap。
2. 去除纯色边缘与无效空白（左右优先；上下可选）。
3. 按目标高度生成候选分割点。
4. 在候选点上下约 **150～300 px** 窗口内寻找：空白带、段落/卡片间距、分割线、水平投影能量最低处。
5. 尽量避免切过文字行主体（用水平投影/行密度启发式，**不**引入 OpenCV）。
6. 相邻切片重叠约 **100～160 px**。
7. 在重叠区或角标加入轻量页码：`01/09` 样式，不遮挡正文核心区（半透明底即可）。

示例验收参数（单元测试可用合成图）：

```
原图：1080 × 18420
目标高度：2200 px
重叠：120 px
输出：约 9 张（允许 ±1 取决于边界搜索）
格式：JPEG 质量 95% 或 PNG（可配置，默认 JPEG 95%）
```

### E. 预览与手动调整

- 缩略图网格/列表展示全部切片。
- 支持：拖动分割线、合并相邻片、删除某一片、查看原尺寸信息。
- 操作后重新编号。
- 「批量分享」「保存到相册」。

### F. 导出与分享

- MediaStore 保存到相册/应用导出目录，文件名含序号：`longshot_01_of_09.jpg`。
- `ACTION_SEND_MULTIPLE` + `image/*` + `FileProvider` URI，一次分享全部切片。
- 导出过程不 ANR；超长图用后台协程；失败可重试与错误提示。

### G. 内存与工程

- 使用 `BitmapRegionDecoder`（或等价分块 API）按区域解码；单片处理完及时 recycle。
- 目标可处理至少 `1440 × 50000` 量级而不因整图解码 OOM（在合理 heap 下；可用仪器/单元测模拟 region 路径）。
- Kotlin + Jetpack Compose + Material 3。
- minSdk 建议 26+，targetSdk 当前稳定版。
- 模块边界清晰（可单 module 起步，但包结构按下列划分）。
- `./gradlew :app:assembleDebug` 成功；核心算法有 JVM 单元测试。

### H. 明确不做（本 goal 失败条件若做了也不算加分）

- 云端处理 / 账号 / 网络必需逻辑
- AI 修复文字 / 超分辨率「恢复原文」
- 自动总结、复杂 OCR
- 滚动截图录制
- OpenCV 依赖
- PDF / ZIP / 重新拼成长图 / 重复顶栏去除（第二阶段）

---

## 建议工程结构

```
app/
├── feature/import/          # Photo Picker、Share Intent
├── feature/editor/          # 处理页、预览、分割线
├── feature/export/          # 保存、SEND_MULTIPLE、FileProvider
├── feature/history/         # 可选：最近处理（Room，MVP 可简化为内存/DataStore）
├── image/analysis/
│   ├── QualityAnalyzer
│   ├── MarginDetector
│   ├── SplitPointDetector
│   └── RepeatedRegionDetector  # stub / 第二阶段
├── image/processing/
│   ├── RegionDecoder
│   ├── ImageSplitter
│   ├── ImageMerger            # stub / 第二阶段
│   └── PdfExporter            # stub / 第二阶段
└── data/local/                # 可选 Room 历史
```

单 `:app` module 可接受，但 **包名与类职责** 必须按上表可定位。

---

## UI 信息架构（MVP）

### 首页

- 标题：长截图处理
- `[ 选择图片 ]`
- 最近处理（有则列表；无则空态一句说明）
- 副文案强调：也可从相册「分享」到本应用

### 处理页

```
原图质量：良好 | 可用 | 偏低 | 不可恢复
原图：1080 × 18420 px
预计输出：8 张

模式：● 给 AI 看  ○ 阅读(可简化)  ○ 存档(保存多图)

单张高度    [ 2200 ]
相邻重叠    [  120 ]
裁剪空白边  [开]
去重顶栏    [即将推出/关]

[ 开始处理 ]
```

### 预览页

- 切片缩略图 + 序号
- 拖动分割 / 合并 / 删除
- `[ 批量分享 ]` `[ 保存全部 ]`

---

## Verification plan（独立可证伪）

1. **gating**：`./gradlew :app:assembleDebug`（及存在的 unit test task）成功；日志可复现。
2. **gating**：`QualityAnalyzer` 单测覆盖四档宽度边界（1080 / 720 / 360 / 86）与「不可恢复」文案条件。
3. **gating**：`SplitPointDetector` / `ImageSplitter` 对合成长图（固定条带+空白缝）验证：片数合理、重叠像素、不整图 decode。
4. **gating**：`AndroidManifest` 含 SEND/SEND_MULTIPLE image；`FileProvider` 配置存在。
5. **evidence**：核心类存在 `BitmapRegionDecoder` 或封装 `RegionDecoder` 的区域解码路径；无「整图 `BitmapFactory.decode*` 超长图」主路径。
6. **evidence**（若无模拟器）：静态走查 + 单测即可；若有 emulator，手动：分享一张图 → 切片 → 分享到任意支持多图应用。
7. **禁止项检查**：依赖清单无 OpenCV/云 OCR SDK；无「超分恢复」功能入口。

---

## Non-goals（本轮）

- OCR / Markdown 导出 / PDF / ZIP / 单张长图重拼
- 重复顶栏感知哈希去除（可留接口 stub）
- 多张截图自动拼接
- Play 上架物料、多语言完整本地化（中文 UI 即可）
- iOS
- WorkManager 复杂调度（协程 + 前台进度足够；导出可先主流程内完成）

---

## Implementation approach

1. 用 Android Studio 标准空 Compose 工程脚手架（Gradle Version Catalog 可选）。
2. 先实现 `RegionDecoder` + 质量检测 + 假分割（固定高度），打通选图→预览→保存→分享。
3. 再替换为投影能量智能分割 + 页码角标。
4. 接线 Share Intent 与 FileProvider。
5. 单测锁住质量阈值与分割不变量；`assembleDebug`。
6. 更新 `.memory/CURRENT_STATE.md` 与 `active_object.yaml` 反映完成状态。

---

## Task checklist

- [ ] 创建 Android/Kotlin/Compose 工程与基础主题（中文字符串）
- [ ] `RegionDecoder` 分块解码 + 内存安全切片写入
- [ ] `QualityAnalyzer` + 质量警告 UI
- [ ] `MarginDetector` 裁纯色/空白边
- [ ] `SplitPointDetector` + 重叠 + 页码角标
- [ ] 首页选图 + 处理页参数 + 预览编辑（拖分割/合并/删）
- [ ] MediaStore 保存 + `ACTION_SEND_MULTIPLE`
- [ ] Share intent-filter 入口
- [ ] 单元测试 + `assembleDebug`
- [ ] 文档：`README.md` 说明如何安装 debug APK、分享路径、质量等级含义
- [ ] 同步 `.memory/*`

---

## Risks

- 无 Android SDK/模拟器的 CI 环境：以 `assembleDebug` + JVM 单测为 gating；真机手测标为后续。
- 智能分割对纯图/渐变背景可能不准：允许手动拖分割线兜底。
- 部分厂商相册分享只给压缩 Uri：质量检测正是为此存在，必须诚实警告。
- 超长图文件解码器限制：若 `BitmapRegionDecoder` 对某格式失败，明确错误提示，勿静默全图 decode。

---

## 第二阶段（本 goal 不验收，仅备案）

- 重复顶栏/底栏 phash 去除
- PDF / ZIP / 重拼长图
- OCR → 文本/Markdown
- 阅读模式完整状态栏/导航栏裁剪
- Room 处理历史
- WorkManager 大批量导出

---

## 给 Grok Build 的启动句（可直接粘贴）

```
/goal 在 /root/code/changjietu 从零实现 Android 本地 App「长截图处理器」MVP。严格按仓库根目录 GOAL.md 验收：Kotlin+Compose；相册/系统分享接入；QualityAnalyzer 四档质量警告（86px 宽必须判不可恢复且禁止 AI 超分暗示）；BitmapRegionDecoder 分块解码避免 OOM；智能分割（投影寻缝+重叠 100–160px+页码）；预览可拖分割/合并/删；MediaStore 保存与 ACTION_SEND_MULTIPLE；assembleDebug+核心单测通过。不做云端/OCR/PDF/OpenCV/重复顶栏算法。完成后更新 README 与 .memory。
```

---

## v0.2 增量（2026-11-06 用户确认）

在 MVP 之上追加，仍受 §H 禁止项约束：

1. **按手机屏幕自动切片**：读取设备真实屏幕像素，单张高度默认 = 屏幕长边，不得超过；每个设备自动不同。
2. **只裁切、不缩放**：输出保持源图内容宽度与原始像素；源图宽度超过屏幕时保留完整宽度并提示，不横向裁切内容。
3. **减少内容截断**：切点优先落在低能量空白带（文字行/卡片间隙），尾部过短时重排为均衡两片；相邻重叠保持 100–160 px。
4. **逐张选择 / 全选**：预览页可选择任意切片；保存、ZIP、分享只作用于选中的切片。
5. **相册目录**：选中图片保存到 `Pictures/changjietu`。
6. **ZIP**：选中切片打包为 ZIP，保存到 `Download/changjietu/`，并可经 `ACTION_SEND` 分享。
7. **Release 交付**：产出可安装的 Release APK（R8 + 资源压缩 + 本地 Release 签名），放到设备 `Download` 目录。
8. **UI/UX**：重新设计首页/处理页/预览页；启动图标缩小并居中；补充空态、错误、权限与导出反馈。

> 以上为已确认增量。未列出的第二阶段能力（PDF、OCR、重复顶栏等）仍不进入本轮。

---

## 成功时的用户路径（最终价值）

> 任意 App 或相册 → 分享 → **长截图处理器** → 质量检测 → 自动切片 → 分享给 ChatGPT / Grok  

从此不再把两万像素高的长图直接塞进聊天，被平台压成几十像素宽。
