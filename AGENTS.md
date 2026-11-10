# changjietu（长截图处理器）

Android 本地工具：长截图质量检测 → 智能切片 → 批量分享给多模态 AI。

## 全局公约

本项目遵循：

/root/code/AgentStandards/ENGINEERING_COVENANT.md

Adopted covenant: 1.0.0

若该文件不存在，仍须遵守本文件中列出的最低原则，并报告全局公约不可用。

## 权威顺序（冲突时）

1. **用户当前指令**
2. **真实代码** + [GOAL.md](GOAL.md) + [docs/CURRENT_STATUS.md](docs/CURRENT_STATUS.md) + [docs/DECISIONS.md](docs/DECISIONS.md)
3. 本文件（路由与禁令）
4. `.memory/*` 仅为辅助会话状态，**不得**覆盖 1–3

## 默认启动（轻量）

每个任务**默认只读本文件**。需要状态时再读 `GOAL.md` 和 `CURRENT_STATUS`；有决策歧义时再读 `DECISIONS`。
**禁止**机械通读 `docs/**`。

## 按需文档矩阵

| 任务类型 | 再读（仅相关项） |
|----------|------------------|
| 产品规格 / 验收标准 | [GOAL.md](GOAL.md)（MVP 完整定义） |
| 产品说明 / 安装 | [README.md](README.md) |
| 当前状态 | [docs/CURRENT_STATUS.md](docs/CURRENT_STATUS.md) |
| 已确认决策 | [docs/DECISIONS.md](docs/DECISIONS.md) |

> GOAL.md 是本项目的产品规格权威文件，含完整的 MVP 定义、验收标准、工程结构和 Non-goals。

兼容入口：[CLAUDE.md](CLAUDE.md)（仅转发本文件，无第二套规则）。

## 会话目标

`CURRENT_STATUS` 中记录的当前阶段仅描述项目路线位置，**不等于**本次会话的执行目标。
只有用户当前请求或显式 `/goal` 才能建立 **Session Objective**；
**禁止**因看到路线图下一阶段而自动开始开发。

## 技术栈（摘要）

Kotlin · Jetpack Compose · Material 3 · `BitmapRegionDecoder` 分块解码

- applicationId：`com.changjietu.app`
- 当前开发版本：**0.2.10**（versionCode 12；正式发布状态见 Releases）
- 模块：`:app`（单 module，包结构按 GOAL.md 建议工程结构）

## 构建与 APK

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest   # 核心算法有 JVM 单元测试
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

## 产品定位

**主入口**：系统分享菜单（`ACTION_SEND` / `ACTION_SEND_MULTIPLE` + `image/*`）。

平台分享长图时常把原图压成极窄缩略图（真实案例：86 × 1536 px）。文字信息在压缩环节已丢失。

工具职责：
1. **源图质量检测**，四档警告（良好/可用/偏低/不可恢复），严重压缩时明确警告
2. **智能切片**，分块解码 + 投影寻缝 + 重叠 + 页码角标
3. **预览微调**，拖分割线/合并/删除/重新编号
4. **批量导出**，MediaStore 保存 + `ACTION_SEND_MULTIPLE`

## 架构红线（不可修改）

以下三条来自 GOAL.md，任何 Agent 不得违反：

1. **禁止 AI 超分恢复**：禁止实现或暗示「AI 超分恢复原文」。源图严重压缩时只能警告，不能假装增强后恢复了文字（GOAL.md §B、§H）。
2. **禁止整图 BitmapFactory.decode 主路径**：超长图必须使用 `BitmapRegionDecoder`（或等价分块 API）按区域解码；单片处理完及时 recycle。目标可处理至少 1440 × 50000 量级而不因整图解码 OOM（GOAL.md §G）。
3. **禁止引入云端/OCR/OpenCV**：不做云端处理、账号、网络必需逻辑、AI 修复文字、复杂 OCR、OpenCV 依赖（GOAL.md §H）。

## 隐式状态机与回归门禁

涉及导航、协程、切线状态、保存、分享、缓存与发布时，先按需阅读 [状态机注册表](docs/state-machines/registry.json)，使用 [.cursor Skill](.cursor/skills/audit-longshot-state-machines/SKILL.md) 定位风险和验证范围。运行 `python3 tools/verify_state_machines.py`，再运行受影响的 JVM/Android 测试与 `bash formal/longshot/verify.sh`。只有四个子系统有有界 TLC 模型；不可把全部 29 个条目描述为已经形式化证明。状态机目录是 AI Agent 的导航资料，不覆盖真实代码和 GOAL.md。

## 禁止事项

- 密钥 / token 入库；破坏性 git（除非用户明确要求）
- 不擅自扩大范围；先跟 GOAL.md 和用户指令
- **禁止 AI 超分恢复**（见架构红线 1）
- **禁止整图 BitmapFactory.decode 主路径**（见架构红线 2）
- **禁止引入云端/OCR/OpenCV**（见架构红线 3）
- 改完要验证：`./gradlew :app:assembleDebug` 与相关 unit test
