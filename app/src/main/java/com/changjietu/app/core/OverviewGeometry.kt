package com.changjietu.app.core

/**
 * 长图总览位图的尺寸规划（纯 JVM，可单测）。
 *
 * 目标：把任意长图（含 1440 × 50000）压到一张有界位图，供剪辑式编辑页显示切点线。
 * 只决定采样率与输出尺寸，不做任何解码；实际分块解码见
 * [com.changjietu.app.image.processing.OverviewRenderer]。
 */
object OverviewGeometry {

    /** 输出位图宽度上限（像素）。 */
    const val MAX_WIDTH = 360

    /** 输出位图高度上限（像素），RGB_565 下 ≈ 4.3 MB 封顶。 */
    const val MAX_HEIGHT = 6000

    data class Spec(
        val sampleSize: Int,
        val outWidth: Int,
        val outHeight: Int,
    )

    fun compute(contentWidth: Int, contentHeight: Int): Spec {
        require(contentWidth > 0 && contentHeight > 0) { "content dimensions must be positive" }
        // BitmapRegionDecoder 的 inSampleSize 取 2 的幂最稳妥，逐步翻倍直到输出尺寸落进上限。
        var sample = 1
        while (ceilDiv(contentWidth, sample) > MAX_WIDTH ||
            ceilDiv(contentHeight, sample) > MAX_HEIGHT
        ) {
            sample *= 2
        }
        return Spec(
            sampleSize = sample,
            outWidth = ceilDiv(contentWidth, sample).coerceAtLeast(1),
            outHeight = ceilDiv(contentHeight, sample).coerceAtLeast(1),
        )
    }

    private fun ceilDiv(value: Int, divisor: Int): Int =
        (value + divisor - 1) / divisor
}
