package com.changjietu.app.image.processing

/**
 * 角标位置：优先右上 → 右下 → 左上 → 左下。
 * [HIDDEN] 仅为兼容旧调用保留，[BadgePositionSelector.choose] 永不返回它：
 * 四角都不够空时回退到能量最低的角，保证角标始终绘制（半透明底可接受压到内容）。
 */
enum class BadgePosition { TR, BR, TL, BL, HIDDEN }

/**
 * 纯决策函数：给定切片/角标尺寸、安全 padding、已有能量数据，
 * 选择尽量不遮挡内容的角标位置。
 *
 * 规则：
 * - candidate 必须通过 safe-enough gate（数据驱动阈值，非「相对最低」）。
 * - 优先级 TR → BR → TL → BL；全部不安全 → 回退到 (列均值 + 行均值) 最小的角，平局取 TR。
 * - **永不返回 HIDDEN**：曾出现四角均有内容时角标整个消失（「角标偶见失效」）。
 * - 角区 = actual badge bounds + safety padding（无固定 50×50 假设）。
 * - 输入仅切片尺寸 / 角标尺寸 / padding / 已有能量数据；无 Android 依赖，可 JVM 单测。
 * - 不扩展 canvas、不新增设置、不做 OCR/ML。
 */
object BadgePositionSelector {

    /** safe-enough gate 系数：候选角区域平均能量必须显著低于切片整体平均。 */
    const val SAFE_FACTOR = 0.5f

    private const val EPSILON = 1e-6f

    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun choose(
        sliceWidth: Int,
        sliceHeight: Int,
        badgeWidth: Int,
        badgeHeight: Int,
        padding: Int,
        colEnergy: FloatArray,
        rowEnergy: FloatArray,
    ): BadgePosition {
        require(sliceWidth > 0 && sliceHeight > 0) { "slice dimensions must be positive" }
        require(badgeWidth > 0 && badgeHeight > 0) { "badge dimensions must be positive" }
        require(padding >= 0) { "padding must be >= 0" }
        require(colEnergy.size >= sliceWidth) { "colEnergy too short: ${colEnergy.size} < $sliceWidth" }
        require(rowEnergy.size >= sliceHeight) { "rowEnergy too short: ${rowEnergy.size} < $sliceHeight" }

        // safe-enough gate：以切片整体平均为基准，候选角区域必须明显更空。
        val colGate = average(colEnergy, 0, sliceWidth) * SAFE_FACTOR + EPSILON
        val rowGate = average(rowEnergy, 0, sliceHeight) * SAFE_FACTOR + EPSILON

        val candidates = listOf(
            BadgePosition.TR to cornerRect(sliceWidth, sliceHeight, badgeWidth, badgeHeight, padding, top = true, right = true),
            BadgePosition.BR to cornerRect(sliceWidth, sliceHeight, badgeWidth, badgeHeight, padding, top = false, right = true),
            BadgePosition.TL to cornerRect(sliceWidth, sliceHeight, badgeWidth, badgeHeight, padding, top = true, right = false),
            BadgePosition.BL to cornerRect(sliceWidth, sliceHeight, badgeWidth, badgeHeight, padding, top = false, right = false),
        )

        // 四角都不够空时也必须出一个位置：semi-transparent 角标压到内容上，
        // 远比整张切片缺号（用户以为角标失效）要好。
        var fallbackPos = BadgePosition.TR
        var fallbackScore = Float.POSITIVE_INFINITY
        for ((pos, rect) in candidates) {
            val colAvg = average(colEnergy, rect.left, rect.right)
            val rowAvg = average(rowEnergy, rect.top, rect.bottom)
            if (colAvg < colGate && rowAvg < rowGate) {
                return pos
            }
            val score = colAvg + rowAvg
            if (score < fallbackScore) {
                fallbackScore = score
                fallbackPos = pos
            }
        }
        return fallbackPos
    }

    /**
     * 角标相对字号的缩放，保证背景矩形能放进切片。
     * 返回值 ≤ 1。过窄切片（裁边后只剩一条）若不缩小，角标会画到画布外，看起来像没打上。
     */
    fun badgeScaleToFit(rectW: Float, rectH: Float, bitmapWidth: Int, bitmapHeight: Int): Float {
        if (rectW <= 0f || rectH <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) return 1f
        val sx = bitmapWidth.toFloat() / rectW
        val sy = bitmapHeight.toFloat() / rectH
        return minOf(1f, sx, sy).coerceAtLeast(0.35f)
    }

    /**
     * 角标左上角。矩形可比画布宽/高时贴边，且 left/top 不会为负，避免整段文字被裁没。
     */
    fun placeBadge(
        bitmapWidth: Int,
        bitmapHeight: Int,
        rectW: Float,
        rectH: Float,
        margin: Float,
        position: BadgePosition,
    ): Pair<Float, Float> {
        val bw = bitmapWidth.toFloat().coerceAtLeast(1f)
        val bh = bitmapHeight.toFloat().coerceAtLeast(1f)
        val w = rectW.coerceAtLeast(1f)
        val h = rectH.coerceAtLeast(1f)
        val maxLeft = (bw - w).coerceAtLeast(0f)
        val maxTop = (bh - h).coerceAtLeast(0f)
        val preferRight = position == BadgePosition.TR || position == BadgePosition.BR || position == BadgePosition.HIDDEN
        val preferTop = position == BadgePosition.TR || position == BadgePosition.TL || position == BadgePosition.HIDDEN
        val m = margin.coerceAtLeast(0f)
        val left = (if (preferRight) bw - w - m else m).coerceIn(0f, maxLeft)
        val top = (if (preferTop) m else bh - h - m).coerceIn(0f, maxTop)
        return left to top
    }

    /**
     * 角区 = actual badge bounds + padding，并 clamp 到切片内（不越界）。
     */
    internal fun cornerRect(
        sliceWidth: Int,
        sliceHeight: Int,
        badgeWidth: Int,
        badgeHeight: Int,
        padding: Int,
        top: Boolean,
        right: Boolean,
    ): Rect {
        val pad = padding
        val left = if (right) (sliceWidth - badgeWidth - pad).coerceAtLeast(0) else pad
        val rightX = if (right) sliceWidth else (badgeWidth + pad).coerceAtMost(sliceWidth)
        val topY = if (top) pad else (sliceHeight - badgeHeight - pad).coerceAtLeast(0)
        val bottom = if (top) (badgeHeight + pad).coerceAtMost(sliceHeight) else sliceHeight
        return Rect(left, topY, rightX, bottom)
    }

    private fun average(arr: FloatArray, from: Int, toExclusive: Int): Float {
        val f = from.coerceIn(0, arr.size)
        val t = toExclusive.coerceIn(f, arr.size)
        if (t <= f) return 0f
        var sum = 0f
        for (i in f until t) sum += arr[i]
        return sum / (t - f)
    }
}
