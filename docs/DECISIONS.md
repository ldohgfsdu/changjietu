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
| **状态** | Accepted（C3） |
| **决策** | 普通构建依赖用户级 `~/.gradle/gradle.properties` 的 `android.aapt2FromMavenOverride`（机器绝对路径）属外部配置，非 reproducible closure。AGP 通过 `ProviderFactory.gradleProperty` 读取该属性（`Project.extra` 无法注入），故唯一仓库可控方式是静态 `gradle.properties`。改为提交 `gradle.properties`：`android.aapt2FromMavenOverride=../tools/aapt2`（相对路径，指向仓库内 `tools/aapt2` qemu 包装）。包装脚本自检测架构：x86-64 直接运行 SDK `build-tools/35.0.0/aapt2`，ARM64 经 `qemu-user` 运行。无机器绝对路径入库、无用户级手动配置要求。代价：所有平台改用 SDK aapt2（非 AGP Maven aapt2），已文档化 |
| **依据** | C3.2 provenance + C3.3 方案评估 + C3.4 strict isolated Gate PASS（fresh GRADLE_USER_HOME、无用户级 gradle.properties，APK SHA `04ced785…` 复现） |

---

## 变更记录

| 日期 | 变更 |
|------|------|
| 2026-07-25 | 项目迁移至 AgentStandards 1.0.0；提取 GOAL.md 已有决策 |
| 2026-08-16 | 记录 D-007（C2） |
| 2026-08-16 | 记录 D-008（C2F1） |
| 2026-08-16 | 记录 D-009、D-010（C3）
