package com.changjietu.app.image.analysis

/**
 * 第二阶段：重复顶栏/底栏检测（感知哈希）。
 * MVP 仅 stub，UI 可显示「即将推出」。
 */
data class RepeatedRegionHint(
    val topPx: Int = 0,
    val bottomPx: Int = 0,
    val detected: Boolean = false,
)

object RepeatedRegionDetector {
    fun detectStub(
        @Suppress("UNUSED_PARAMETER") contentHeight: Int,
    ): RepeatedRegionHint = RepeatedRegionHint(detected = false)
}
