package com.changjietu.app.image.analysis

import kotlin.math.max
import kotlin.math.min

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

    /** 连续多少个高能量样本才认定为正文开始：容忍边缘孤立噪点，不被单像素打断。 */
    private const val CONTENT_RUN = 4

    /**
     * @param threshold 固定阈值的基准，用于推导自适应阈值的上下限
     * @param maxFraction 单侧最多裁掉的比例，防止整图被裁没
     */
    fun detect(
        width: Int,
        height: Int,
        columnEnergy: FloatArray,
        rowEnergy: FloatArray,
        threshold: Float = 2.5f,
        maxFraction: Float = 0.40f,
    ): Margins {
        require(columnEnergy.size == width) { "columnEnergy size" }
        require(rowEnergy.size == height) { "rowEnergy size" }

        val maxSideW = (width * maxFraction).toInt().coerceAtLeast(0)
        val maxSideH = (height * maxFraction).toInt().coerceAtLeast(0)

        // 平滑只用于推导阈值（中位数本身抗噪），边缘扫描用原始能量以避免模糊边界。
        val colThr = adaptiveThreshold(smooth(columnEnergy, 1), threshold)
        val rowThr = adaptiveThreshold(smooth(rowEnergy, 1), threshold)

        // 整轴近似纯空白（连中段都低于阈值）时不裁该轴，避免把整张图裁没。
        val colBlank = interiorMedian(smooth(columnEnergy, 1)) < colThr
        val rowBlank = interiorMedian(smooth(rowEnergy, 1)) < rowThr

        var left = if (colBlank) 0 else leadingBlank(columnEnergy, colThr, maxSideW)
        var right = if (colBlank) 0 else trailingBlank(columnEnergy, colThr, maxSideW)
        var top = if (rowBlank) 0 else leadingBlank(rowEnergy, rowThr, maxSideH)
        var bottom = if (rowBlank) 0 else trailingBlank(rowEnergy, rowThr, maxSideH)

        // 逐轴 clamp：只压缩超出 50% 的那一轴，取代「某轴过裁就四边全归零」的悬崖。
        val (l, r) = clampPair(left, right, width)
        val (t, b) = clampPair(top, bottom, height)
        // 保守安全内边距：检测出的空白再留一点（宁可多留白边，不裁内容）。
        // 按图尺寸缩放，小合成图（测试）不被吃完：100px宽留2、200px高留1。
        val safetyX = minOf(24, width / 50).coerceAtLeast(0)
        val safetyY = minOf(16, height / 200).coerceAtLeast(0)
        left = (l - safetyX).coerceAtLeast(0)
        right = (r - safetyX).coerceAtLeast(0)
        top = (t - safetyY).coerceAtLeast(0)
        bottom = (b - safetyY).coerceAtLeast(0)
        return Margins(left, top, right, bottom)
    }

    /** 保证该轴剩余严格超过一半；超限时按比例压缩两侧，而不是全部丢弃。 */
    private fun clampPair(a: Int, b: Int, size: Int): Pair<Int, Int> {
        val total = a + b
        val maxCrop = (size - (size / 2 + 1)).coerceAtLeast(0)
        if (total <= maxCrop) return a to b
        if (total == 0) return 0 to 0
        val na = a * maxCrop / total
        return na to (maxCrop - na)
    }

    /**
     * 自适应阈值：以中段能量中位数为基准，适配低对比度截图；
     * 下限防止纯色背景被误判为内容，上限防止高对比图把浅色边距过度裁掉。
     */
    private fun adaptiveThreshold(energy: FloatArray, threshold: Float): Float {
        val floor = threshold * 0.15f
        val cap = threshold * 2f
        return max(floor, min(interiorMedian(energy) * 0.25f, cap))
    }

    private fun interiorMedian(energy: FloatArray): Float {
        if (energy.isEmpty()) return 0f
        val from = energy.size / 4
        val to = (energy.size * 3) / 4
        if (to <= from) return energy[energy.size / 2]
        val copy = energy.copyOfRange(from, to)
        copy.sort()
        return copy[copy.size / 2]
    }

    private fun smooth(energy: FloatArray, radius: Int): FloatArray {
        if (energy.isEmpty()) return energy
        val out = FloatArray(energy.size)
        for (i in energy.indices) {
            var sum = 0f
            var n = 0
            for (j in (i - radius)..(i + radius)) {
                if (j in energy.indices) {
                    sum += energy[j]
                    n++
                }
            }
            out[i] = if (n == 0) 0f else sum / n
        }
        return out
    }

    /** 从头部数连续空白；孤立高能量噪点不会终止空白段，持续 [CONTENT_RUN] 个才认定正文开始。 */
    private fun leadingBlank(energy: FloatArray, threshold: Float, maxSide: Int): Int {
        val limit = minOf(maxSide, energy.size / 2)
        var blankEnd = 0
        var run = 0
        var i = 0
        while (i < limit) {
            if (energy[i] >= threshold) {
                run++
                if (run >= CONTENT_RUN) break
            } else {
                run = 0
                blankEnd = i + 1
            }
            i++
        }
        return blankEnd
    }

    private fun trailingBlank(energy: FloatArray, threshold: Float, maxSide: Int): Int {
        val limit = minOf(maxSide, energy.size / 2)
        var blankCount = 0
        var run = 0
        var i = energy.lastIndex
        var steps = 0
        while (steps < limit && i >= 0) {
            if (energy[i] >= threshold) {
                run++
                if (run >= CONTENT_RUN) break
            } else {
                run = 0
                blankCount = steps + 1
            }
            i--
            steps++
        }
        return blankCount
    }
}
