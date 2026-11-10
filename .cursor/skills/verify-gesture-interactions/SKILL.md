---
name: verify-gesture-interactions
description: Audit and verify changjietu Compose drag, cut-handle, scroll and release interactions. Use when touch feels laggy, cut handles miss touches, a gesture commits unexpectedly, or a gesture overlaps scrolling.
---

# 长截图手势交互验收 Skill

本技能是本仓库的**执行路由**，参考 [Emil Kowalski Gestures Skill](https://github.com/emilkowalski/skills/blob/main/skills/gestures/SKILL.md)。原 Skill 面向 Web；本仓库是 Android Jetpack Compose，**不得**直接复制 Motion/Vaul/CSS 写法。

## 启动

1. 读 `AGENTS.md`、`GOAL.md`、`docs/GESTURE_ACCEPTANCE.md` 和 `docs/state-machines/registry.json`。
2. 先定位实际手势所有权及状态流：`OverviewEditor.kt`、`PreviewCutEditor.kt`、`LongshotViewModel.kt`、`SourceCutGeometry.kt`。
3. 定义一次真实用户流程：手指按下 → 捕获 → 逐帧位移 → 松开/取消 → 预览状态 → 重切/导出。逐状态核对不变量。
4. 用最小改动处理已证实根因；不要为了动效引入新状态机或引入 spring/fling。切线是精确定位工具，**释放后应固定在最终切点**。
5. 执行 `./gradlew :app:testDebugUnitTest`、`./gradlew :app:assembleDebug`、`python3 tools/verify_state_machines.py`、`bash formal/longshot/verify.sh`。不具备运行环境则明确标记未验证，不要伪称 PASS。
6. 用真机测试纵向滚动与切线拖动冲突、密集切线、顶部/底部裁剪、停住/快速反向/取消、48dp 命中区以及实际保存结果。建议同时覆盖 60Hz 与高刷新率设备；记录设备、录屏、APK、commit。
7. 结果分 `IMPLEMENTED`、`AUTOMATED_VERIFIED`、`DEVICE_VERIFIED`、`PENDING`，不把源码静态测试当真机验收。

## 必须守住

- TRACK 阶段跟手，不用插值或弹簧拖慢切线；仅在有确定需求时设计 RELEASE 动画。
- 触控区域 ≥44dp，视觉圆点可以更小；拖动区与纵向滚动手势所有权要明确。
- 编辑时仅更新切点，不做解码、重切、分享或存储 IO；释放一次提交，原位释放不得重切。
- 预览线与最终导出边界同源；验证导出无重叠/漏字、保序。不得为让动画好看而改变源图切点。
- 保留架构红线：不整图解码、不引入 OCR/OpenCV/云端、不破坏分享链路。
- 参考 `docs/GESTURE_ACCEPTANCE.md` 的场景矩阵逐项留证。
