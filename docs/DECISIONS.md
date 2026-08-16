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

## 变更记录

| 日期 | 变更 |
|------|------|
| 2026-07-25 | 项目迁移至 AgentStandards 1.0.0；提取 GOAL.md 已有决策 |
| 2026-08-16 | 记录 D-007（C2）
