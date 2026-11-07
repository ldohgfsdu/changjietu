package com.changjietu.app.image.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Color
import android.graphics.Rect
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class BitmapRegionSource(
    private val decoder: BitmapRegionDecoder,
) : RegionSource {
    override val width: Int get() = decoder.width
    override val height: Int get() = decoder.height

    override fun decodeRegion(left: Int, top: Int, right: Int, bottom: Int, sampleSize: Int): RegionHandle? {
        if (left >= width || top >= height || right <= left || bottom <= top) return null
        val l = left.coerceIn(0, width - 1)
        val t = top.coerceIn(0, height - 1)
        val r = right.coerceIn(l + 1, width)
        val b = bottom.coerceIn(t + 1, height)
        // 区域解码结果视为只读输入；角标/绘制一律走 SliceRenderer 的可变输出。
        // 不在此处 copy 为 mutable，避免误把「修 Canvas」做成整图拷贝。
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        return try {
            val bmp = decoder.decodeRegion(Rect(l, t, r, b), opts) ?: return null
            BitmapRegionHandle(bmp)
        } catch (_: Exception) {
            null
        }
    }

    override fun close() {
        decoder.recycle()
    }

    companion object {
        fun open(input: InputStream): BitmapRegionSource? {
            return try {
                val decoder = if (android.os.Build.VERSION.SDK_INT >= 31) {
                    BitmapRegionDecoder.newInstance(input)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(input, false)
                }
                if (decoder == null) null else BitmapRegionSource(decoder)
            } catch (_: Exception) {
                null
            }
        }
    }
}

object RegionDecoderUtil {

    /**
     * 采样计算列/行能量，用于空白边与分割寻缝。
     * 使用较大 sampleSize，避免整图加载。
     */
    fun computeEnergyMaps(
        source: RegionSource,
        targetSampleWidth: Int = 256,
    ): Pair<FloatArray, FloatArray> {
        val w = source.width
        val h = source.height
        val sampleSize = max(1, w / targetSampleWidth)
        val stripH = min(h, 4096)
        val colAcc = FloatArray(w)
        val colCount = FloatArray(w)
        val rowEnergy = FloatArray(h)

        var y = 0
        while (y < h) {
            val y2 = min(y + stripH, h)
            // 词法所有权：任何成功获得的 handle 都由 use{} 恰好 close 一次；
            // null-bitmap 跳过条带也仍 close，且不会因 continue 跳过 y 递增而死循环。
            val handle = source.decodeRegion(0, y, w, y2, sampleSize) ?: break
            handle.use { hdl ->
                val bmp = hdl.bitmapOrNull ?: return@use
                val bw = bmp.width
                val bh = bmp.height
                val scaleX = w.toFloat() / bw
                val scaleY = (y2 - y).toFloat() / bh
                val pixels = IntArray(bw * bh)
                bmp.getPixels(pixels, 0, bw, 0, 0, bw, bh)
                for (py in 0 until bh) {
                    // 一个采样像素代表一段源像素。只写一个下标会让中间列/行保持 0，
                    // 空白边检测会把正文里的采样空洞当成可裁空白。
                    val ySpan = sampleSpan(y, py, scaleY, h)
                    var rowVar = 0f
                    var prevLum = -1f
                    for (px in 0 until bw) {
                        val c = pixels[py * bw + px]
                        val lum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c))
                        if (prevLum >= 0f) {
                            rowVar += kotlin.math.abs(lum - prevLum)
                        }
                        prevLum = lum
                        if (py > 0) {
                            val above = pixels[(py - 1) * bw + px]
                            val lumA = (0.299f * Color.red(above) + 0.587f * Color.green(above) + 0.114f * Color.blue(above))
                            val delta = kotlin.math.abs(lum - lumA)
                            val xSpan = sampleSpan(0, px, scaleX, w)
                            for (sx in xSpan) {
                                colAcc[sx] += delta
                                colCount[sx] += 1f
                            }
                        }
                    }
                    if (!ySpan.isEmpty()) {
                        paintMax(rowEnergy, ySpan.first, ySpan.last + 1, rowVar / max(1, bw - 1))
                    }
                }
            }
            y = y2
        }

        val colEnergy = FloatArray(w) { i ->
            if (colCount[i] > 0) colAcc[i] / colCount[i] else 0f
        }
        return colEnergy to rowEnergy
    }

    /**
     * 采样像素 [index] 覆盖的源坐标区间（含首不含尾）。
     * [scale] 是源像素 / 采样像素。区间至少 1 像素，且不越出 [size]。
     */
    internal fun sampleSpan(origin: Int, index: Int, scale: Float, size: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val start = (origin + index * scale).toInt().coerceIn(0, size - 1)
        var end = (origin + (index + 1) * scale).toInt()
        if (end <= start) end = start + 1
        return start until end.coerceAtMost(size)
    }

    /** 把 [value] 涂进 [start, endExclusive)，只抬高、不压低，避免相邻采样互相抹掉正文。 */
    internal fun paintMax(dest: FloatArray, start: Int, endExclusive: Int, value: Float) {
        val from = start.coerceIn(0, dest.size)
        val to = endExclusive.coerceIn(from, dest.size)
        for (i in from until to) {
            if (value > dest[i]) dest[i] = value
        }
    }

    fun estimateGlyphHeight(rowEnergy: FloatArray): Float {
        if (rowEnergy.isEmpty()) return 0f
        val mean = rowEnergy.average().toFloat()
        val thr = mean * 0.85f + 1f
        val runs = mutableListOf<Int>()
        var run = 0
        for (e in rowEnergy) {
            if (e >= thr) {
                run++
            } else if (run > 0) {
                if (run in 2..80) runs.add(run)
                run = 0
            }
        }
        if (run in 2..80) runs.add(run)
        if (runs.isEmpty()) return 0f
        runs.sort()
        return runs[runs.size / 2].toFloat()
    }

    @Deprecated(
        message = "Use SliceRenderer.drawPageBadge on mutable output only",
        replaceWith = ReplaceWith(
            "SliceRenderer.drawPageBadge(bitmap, label)",
            "com.changjietu.app.image.processing.SliceRenderer",
        ),
    )
    fun drawPageBadge(bitmap: Bitmap, label: String) {
        SliceRenderer.drawPageBadge(bitmap, label)
    }
}
