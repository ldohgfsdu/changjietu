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
  - C2F1（处理完成一次性导航契约 + C2.2 Evidence Gate）commit `d244f6d`
  - 构建门：`testDebugUnitTest` 94 用例 / 0 失败、`assembleDebug`、`lintDebug` 均 PASS（JDK 17）
- **C3（Artifact Size & Build Environment Closure）：SIZE-CLOSED / BUILD-CLOSED / RUNTIME-PENDING**
  - 尺寸 A/B：移除 `material-icons-extended`（~6.9MB / ~39%），本地 `AppIcons` 保留 Image/CallMerge
  - Build contract：AAPT2 override 改为仓库可控 `gradle.properties`（`android.aapt2FromMavenOverride=../tools/aapt2` → `tools/aapt2` 包装），消除用户级 Gradle 配置依赖
  - **Isolated Build Reproducibility Gate：PASS**（fresh GRADLE_USER_HOME、无用户级 gradle.properties，复用已声明 wrapper/dependency cache；test/assemble/lint 全过，APK SHA 复现）

---

## 4. 缺口

- 无 adb 设备，运行态冒烟待补（C1 / C2 最高状态为 CODE-CLOSED / RUNTIME-PENDING）
- `ProcessTransition` 导航 wiring（Compose/Activity 层）为 NOT AUTOMATED，仅纯 JVM 契约已测
- README 为既有 dirty 文件，已排除于 C1/C2/C2F1 提交；是否提交留待 C3 单独裁决（非"永不入库"）
- 第二阶段功能全部延后（见 GOAL.md §第二阶段）

---

## 5. APK 基线

| 项 | 值 |
|----|-----|
| 产物路径 | `app/build/outputs/apk/debug/app-debug.apk` |
| variant | debug |
| 测量日 | 2026-08-16 |
| 环境 | JDK 17 · Gradle 8.11.1 · AGP 8.7.3 · Kotlin 2.0.21 |
| 体积（clean A/B） | **17,710,094** bytes（含 material-icons-extended，SHA `50404db0…`）→ **10,828,657** bytes（移除后，SHA `04ced785…`），-6,881,437 bytes / -38.9% |
| APK 成分 | classes.dex ≈ 10.25 MB（97%）、resources.arsc ≈ 418 KB、lib ≈ 37 KB、res ≈ 14 KB、其余少量 |

> 注：APK 文件字节在不同构建间存在 ZIP 对齐填充非确定性（约 ±11 KB），A/B 以 clean 构建为权威。

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
