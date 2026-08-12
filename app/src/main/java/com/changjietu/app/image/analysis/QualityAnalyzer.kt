package com.changjietu.app.image.analysis

/**
 * 源图质量检测。宽度与字高启发式均在此判定；禁止暗示 AI 超分可恢复正文。
 */
enum class QualityLevel {
    GOOD,       // ≥ 1080
    USABLE,     // 720–1079
    LOW,        // 360–719
    UNRECOVERABLE // < 360 或字高过小
}

data class QualityReport(
    val width: Int,
    val height: Int,
    val level: QualityLevel,
    val estimatedGlyphHeightPx: Float? = null,
    val summary: String,
    val conclusion: String,
    val suggestion: String,
) {
    val isCritical: Boolean get() = level == QualityLevel.UNRECOVERABLE
}

object QualityAnalyzer {

    fun analyze(
        width: Int,
        height: Int,
        estimatedGlyphHeightPx: Float? = null,
    ): QualityReport {
        require(width > 0 && height > 0) { "invalid size $width x $height" }

        val glyphTooSmall =
            estimatedGlyphHeightPx != null &&
                estimatedGlyphHeightPx < 8f &&
                width < 720

        val level = when {
            width < 360 || glyphTooSmall -> QualityLevel.UNRECOVERABLE
            width < 720 -> QualityLevel.LOW
            width < 1080 -> QualityLevel.USABLE
            else -> QualityLevel.GOOD
        }

        return when (level) {
            QualityLevel.GOOD -> QualityReport(
                width = width,
                height = height,
                level = level,
                estimatedGlyphHeightPx = estimatedGlyphHeightPx,
                summary = "良好",
                conclusion = "分辨率足够，可继续处理。",
                suggestion = "建议始终从原始截图分享到本工具，避免经聊天平台二次压缩。",
            )
            QualityLevel.USABLE -> QualityReport(
                width = width,
                height = height,
                level = level,
                estimatedGlyphHeightPx = estimatedGlyphHeightPx,
                summary = "可用",
                conclusion = "宽度偏低，多数场景仍可切片分享。",
                suggestion = "若正文较小，建议改用设备原始截图（宽度 ≥ 1080）。",
            )
            QualityLevel.LOW -> QualityReport(
                width = width,
                height = height,
                level = level,
                estimatedGlyphHeightPx = estimatedGlyphHeightPx,
                summary = "偏低",
                conclusion = "文字可能不清晰，AI 识别效果会明显下降。",
                suggestion = "请尽量从相册选择原始长截图，不要使用聊天里已压缩的预览图。",
            )
            QualityLevel.UNRECOVERABLE -> {
                val reason = when {
                    width < 360 && glyphTooSmall ->
                        "宽度仅 ${width}px，且估计字高约 ${"%.1f".format(estimatedGlyphHeightPx)}px。"
                    width < 360 ->
                        "宽度仅 ${width}px，远低于可读阈值。"
                    else ->
                        "估计正文字高约 ${"%.1f".format(estimatedGlyphHeightPx)}px，过小无法可靠识别。"
                }
                QualityReport(
                    width = width,
                    height = height,
                    level = level,
                    estimatedGlyphHeightPx = estimatedGlyphHeightPx,
                    summary = "不可恢复",
                    conclusion = "无法可靠识别正文。$reason 放大或「AI 超分」只能猜字，不能恢复原文。",
                    suggestion = "请从原始截图直接分享到本工具（相册 → 分享 → 长截图处理器），不要转发已被平台压扁的图。",
                )
            }
        }
    }
}
