# 长截图处理器（changjietu）

Android 本地工具：长截图质量检测 → 按屏幕分辨率智能切片 → 批量保存 / ZIP 打包 / 分享给 ChatGPT、Grok 等应用。

## 为什么需要

系统或聊天平台转发长截图时，常会把原图压成极窄图（例如 **86 × 1536 px**）。文字已在压缩环节丢失，放大或「AI 超分」不能可靠恢复正文。本工具强调：

1. **源图质量检测**，严重压缩时明确警告
2. **按手机屏幕切片**，单张不超过屏幕长边，不缩放、只裁切
3. **缝隙感知**，优先在文字行/卡片之间的空白带下刀，减少内容截断
4. **逐张选择 + ZIP 打包**，保存到 `Pictures/changjietu` 或 `Download/changjietu`
5. **系统分享入口**，相册一点分享即可处理
6. **分块解码**，避免超长图 OOM

## 状态

- 规格：[`GOAL.md`](./GOAL.md)（含 v0.2 增量）
- 版本：`0.2.0`（versionCode 2）
- 包名：`com.changjietu.app`
- 当前交付：Release APK（R8 minify + resource shrink + 本地 Release 签名）
- 验证：`testDebugUnitTest` 108 用例 / 0 失败；`assembleRelease`、`lintDebug` 通过；**无 adb 设备，运行态未实测**

## 功能

| 能力 | 说明 |
|------|------|
| 选图 | Photo Picker |
| 分享进入 | `ACTION_SEND` / `SEND_MULTIPLE` + `image/*` |
| 质量四档 | 良好 / 可用 / 偏低 / 不可恢复（86px 宽 → 不可恢复） |
| 屏幕自适应 | 自动读取设备屏幕像素，单张高度默认 = 屏幕长边，可下调到 50% |
| 不缩放 | 切片保持源图内容宽度与原始像素；超宽源图只提示、不横向裁切 |
| 缝隙感知 | 低能量空白带检测 + 缝隙宽度/距离/高度评分；尾部过短自动重排 |
| 重叠 | 100–160 px，默认 120 px |
| 页码角标 | 半透明 `01/N`，自动避开正文（可关闭） |
| 预览 | 逐张选择 / 全选 / 拖切点 / 合并 / 删除 / 重新编号 |
| 保存 | 选中切片保存到相册目录 `Pictures/changjietu` |
| ZIP | 选中切片打包到 `Download/changjietu/长截图_时间戳.zip`，可直接分享 |
| 分享 | `ACTION_SEND_MULTIPLE` + `image/*` + FileProvider URI |
| 分块解码 | `BitmapRegionDecoder`，单片处理完即释放 |

## 非目标（本版不做）

云端、账号、OCR、PDF、AI 修字、OpenCV、重复顶栏去除、滚动录屏。严重压缩源图只警告，**不会假装恢复原文**。

## 构建

> **Supported build matrix（仅列经本项目验证 / 由 wrapper 设计推导的行为）**
>
> | 环境 | 状态 | AAPT2 执行方式 |
> |------|------|----------------|
> | Linux **aarch64**（本项目开发主机） | **已验证** | `tools/aapt2` 经 `qemu-x86_64` 运行 SDK `build-tools/35.0.0/aapt2` |
> | Linux **x86-64** | 设计支持，本主机未运行时验证 | wrapper 直接执行 SDK aapt2 |
> | macOS / Windows / 其他架构 | 未验证、未声明支持 | — |

验证过的 JDK：**17**。构建前可用仓库内 preflight 检查主机契约（fail-fast）：

```bash
tools/preflight.sh
```

构建命令：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

AAPT2 override 是**仓库可控**的：`gradle.properties` 中 `android.aapt2FromMavenOverride=../tools/aapt2`，指向仓库内 `tools/aapt2` 兼容包装（相对路径，非机器绝对路径）。AAPT2 来源为 Android SDK `build-tools`（35.0.0），非第三方二进制。

**前提条件（preflight 会逐项检查）：**

1. **JDK 17**。安装后设 `JAVA_HOME` 指向 JDK 17；`java -version` 应显示 major 17。
2. **Android SDK Build Tools 35.0.0**。确认存在：`ls "$ANDROID_HOME/build-tools/35.0.0/aapt2"`。
3. **Linux ARM64 额外需要 `qemu-user`**（提供 `qemu-x86_64`）。确认：`qemu-x86_64 --version`。

项目**不要求**编辑 `~/.gradle/gradle.properties`。Gradle 的 user-level 或 `-P` 同名 property 仍可按正常 precedence 覆盖项目默认值。

### Release 签名

Release 构建从 `local.properties`（已 gitignore）读取本地签名配置，keystore **不入库**：

```properties
changjietu.release.storeFile=/absolute/path/to/changjietu-release.jks
changjietu.release.storePassword=...
changjietu.release.keyAlias=changjietu
changjietu.release.keyPassword=...
```

也可用环境变量 `CHANGJIETU_RELEASE_STORE_FILE` 等。未配置时回退 debug 签名，保证他人仍可构建安装包；正式分发前应配置自己的 Release keystore。

## 安装与交付

```bash
adb install -r download/changjietu-0.2.0-release.apk
```

- 仓库内：`download/changjietu-0.2.0-release.apk`（同名覆盖 `changjietu-release-latest.apk`），附 `.sha256`
- 设备共享存储：`/sdcard/Download/changjietu-0.2.0-release.apk`
- 构建产物：`app/build/outputs/apk/release/app-release.apk`

## 推荐使用路径

> 相册 / 任意 App → **分享** → **长截图处理器** → 看质量提示 → 开始处理 → 选择切片 → 保存相册 / 打包 ZIP / 分享

不要把已被聊天软件压扁的图当作「原图」。

## 切片规则

- **单张高度上限** = 设备屏幕长边（例如 1080 × 2400 的设备，单张最高 2400 px）；可在处理页下调到 50%
- **宽度** = 源图内容宽度（裁边后），不缩放；若源图宽于手机屏幕，界面会提示但保留完整宽度
- **重叠** = 100–160 px；切点优先落在低能量空白带，尾部过短时重排为更均衡的两片
- **格式** = JPEG 95%，原始分辨率；不做 AI 超分、不做 OCR

## 质量等级

| 等级 | 条件 |
|------|------|
| 良好 | 宽度 ≥ 1080 |
| 可用 | 720–1079 |
| 偏低 | 360–719 |
| 不可恢复 | 宽度 < 360，或估计字高过小 |

「不可恢复」会持续警告；仍可强制切片，但工具不会暗示恢复原文。

## 模块包结构

```
app/src/main/java/com/changjietu/app/
├── feature/importui|editor|export|history
├── image/analysis|processing
├── core            # DisplaySpec / 分享与处理契约
├── ui/theme
└── MainActivity.kt
```

## 协作文档

1. `AGENTS.md`
2. `docs/CURRENT_STATUS.md`
3. `docs/DECISIONS.md`
4. `GOAL.md`
