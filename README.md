# 长截图处理器（changjietu）

Android 本地工具：长截图质量检测 → 智能切片 → 批量分享给 ChatGPT / Grok 等应用。

## 为什么需要

系统或聊天平台转发长截图时，常会把原图压成极窄图（例如 **86 × 1536 px**）。文字已在压缩环节丢失，放大或「AI 超分」不能可靠恢复正文。本工具强调：

1. **源图质量检测**，严重压缩时明确警告  
2. **智能切片 + 重叠 + 编号**，适合丢给多模态 AI  
3. **系统分享入口**，相册一点分享即可处理  
4. **分块解码**，避免超长图 OOM  

## 状态

- 规格：[`GOAL.md`](./GOAL.md)  
- 版本：`0.1.0` MVP（`assembleDebug` + 单元测试已通过）  
- 包名：`com.changjietu.app`  

## MVP 已实现

| 能力 | 说明 |
|------|------|
| 选图 | Photo Picker |
| 分享进入 | `ACTION_SEND` / `SEND_MULTIPLE` + `image/*` |
| 质量四档 | 良好 / 可用 / 偏低 / 不可恢复（86px 宽 → 不可恢复） |
| 裁空白边 | 列/行能量启发式 |
| 智能切片 | 投影寻缝 + 重叠 + 角标 `01/N` |
| 预览 | 拖分割线 / 合并 / 删除 / 重新编号 |
| 导出 | MediaStore 保存 + `ACTION_SEND_MULTIPLE` |
| 分块解码 | `BitmapRegionDecoder` |

## 非目标（本版不做）

云端、账号、OCR、PDF/ZIP、AI 修字、OpenCV、重复顶栏去除、滚动录屏。

## 构建

```bash
export ANDROID_HOME=/path/to/Android/Sdk
# arm64 主机需 qemu-x86_64；已配置 tools/aapt2 包装
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Debug APK：

```
app/build/outputs/apk/debug/app-debug.apk
```

安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 推荐使用路径

> 相册 / 任意 App → **分享** → **长截图处理器** → 看质量提示 → 开始处理 → 批量分享到 ChatGPT / Grok  

不要把已被聊天软件压扁的图当作「原图」。

## 质量等级

| 等级 | 条件 |
|------|------|
| 良好 | 宽度 ≥ 1080 |
| 可用 | 720～1079 |
| 偏低 | 360～719 |
| 不可恢复 | 宽度 &lt; 360，或估计字高过小 |

## 模块包结构

```
app/src/main/java/com/changjietu/app/
├── feature/importui|editor|export|history
├── image/analysis|processing
├── core|ui
└── MainActivity.kt
```

## 记忆与协作

1. `AGENTS.md`  
2. `.memory/CURRENT_STATE.md`  
3. `.memory/active_object.yaml`  
4. `GOAL.md`  
