# 当前状态

> 更新日期：2026-08-16
> **只记录仓库可证实的事实**。

---

## 1. 当前版本

| 项 | 值 | 证据 |
|----|-----|------|
| versionName | **0.1.0** | `app/build.gradle.kts` |
| versionCode | **1** | 同上 |
| applicationId | `com.changjietu.app` | `app/build.gradle.kts` |
| Adopted covenant | **1.0.0** | `AGENTS.md` |

---

## 2. 已完成（MVP）

| 能力 | 说明 |
|------|------|
| 选图 | Photo Picker |
| 分享进入 | `ACTION_SEND` / `SEND_MULTIPLE` + `image/*` |
| 质量四档 | 良好 / 可用 / 偏低 / 不可恢复（86px 宽 → 不可恢复） |
| 裁空白边 | 列/行能量启发式 |
| 智能切片 | 投影寻缝 + 重叠 + 角标 `01/N` |
| 预览 | 拖分割线 / 合并 / 删除 / 重新编号 |
| 导出 | MediaStore 保存 + `ACTION_SEND_MULTIPLE` |

- `assembleDebug` + 单元测试已通过
- 规格文件：[GOAL.md](../GOAL.md)

---

## 3. 进行中

- **C1 / C2：CODE-CLOSED / RUNTIME-PENDING**（无 adb 设备，运行态冒烟待补）
  - C1（进图合同）commit `91a2030`；C2（解码生命周期 + 能量缓存复用）commit `67d6512`
  - 构建门：`testDebugUnitTest` 80 用例 / 0 失败、`assembleDebug`、`lintDebug` 均 PASS（JDK 17）
- **Isolated Build Reproducibility Gate：BLOCKED_EXTERNAL / BUILD_ENVIRONMENT**
  - ARM64 主机 vs AGP 8.7.3 的 Maven x86-64 AAPT2，无法复现隔离 PASS；未伪造、未修

---

## 4. 缺口

- 无 adb 设备，运行态冒烟待补（C1 / C2 最高状态为 CODE-CLOSED / RUNTIME-PENDING）
- 第二阶段功能全部延后（见 GOAL.md §第二阶段）

---

## 5. APK 基线

| 项 | 值 |
|----|-----|
| 产物路径 | `app/build/outputs/apk/debug/app-debug.apk` |

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
