package com.changjietu.app.image.analysis

/**
 * 纯色/近似纯色边缘检测。输入为按行/列采样的平均亮度或主色差异分数。
 * [columnVariance] 长度 = width 的采样，值越小越接近空白/纯色。
 * [rowVariance] 长度 = height 的采样。
 */
data class Margins(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun applyToWidth(width: Int): Int = (width - left - right).coerceAtLeast(1)
    fun applyToHeight(height: Int): Int = (height - top - bottom).coerceAtLeast(1)

    fun isEmpty(): Boolean = left == 0 && top == 0 && right == 0 && bottom == 0
}

object MarginDetector {

    /**
     * @param threshold 方差/能量低于此值视为空白边
     * @param maxFraction 单侧最多裁掉的比例，防止整图被裁没
     */
    fun detect(
        width: Int,
        height: Int,
        columnEnergy: FloatArray,
        rowEnergy: FloatArray,
        threshold: Float = 2.5f,
        maxFraction: Float = 0.25f,
    ): Margins {
        require(columnEnergy.size == width) { "columnEnergy size" }
        require(rowEnergy.size == height) { "rowEnergy size" }

        val maxSideW = (width * maxFraction).toInt().coerceAtLeast(0)
        val maxSideH = (height * maxFraction).toInt().coerceAtLeast(0)

        val left = countLeadingBelow(columnEnergy, threshold, maxSideW)
        val right = countTrailingBelow(columnEnergy, threshold, maxSideW)
        val top = countLeadingBelow(rowEnergy, threshold, maxSideH)
        val bottom = countTrailingBelow(rowEnergy, threshold, maxSideH)

        // 保证严格超过 50% 区域，避免整图近似纯色时被过度裁切
        val remainW = width - left - right
        val remainH = height - top - bottom
        if (remainW <= width / 2 || remainH <= height / 2) {
            return Margins(0, 0, 0, 0)
        }
        return Margins(left, top, right, bottom)
    }

    private fun countLeadingBelow(energy: FloatArray, threshold: Float, max: Int): Int {
        var n = 0
        val limit = minOf(max, energy.size / 2)
        while (n < limit && energy[n] < threshold) n++
        return n
    }

    private fun countTrailingBelow(energy: FloatArray, threshold: Float, max: Int): Int {
        var n = 0
        val limit = minOf(max, energy.size / 2)
        var i = energy.lastIndex
        while (n < limit && i >= 0 && energy[i] < threshold) {
            n++
            i--
        }
        return n
    }
}
