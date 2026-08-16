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
        val l = left.coerceIn(0, width)
        val t = top.coerceIn(0, height)
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
        targetSampleWidth: Int = 64,
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
                    val srcY = (y + py * scaleY).toInt().coerceIn(0, h - 1)
                    var rowVar = 0f
                    var prevLum = -1f
                    for (px in 0 until bw) {
                        val c = pixels[py * bw + px]
                        val lum = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c))
                        if (prevLum >= 0f) {
                            rowVar += kotlin.math.abs(lum - prevLum)
                        }
                        prevLum = lum
                        val srcX = (px * scaleX).toInt().coerceIn(0, w - 1)
                        if (py > 0) {
                            val above = pixels[(py - 1) * bw + px]
                            val lumA = (0.299f * Color.red(above) + 0.587f * Color.green(above) + 0.114f * Color.blue(above))
                            colAcc[srcX] += kotlin.math.abs(lum - lumA)
                            colCount[srcX] += 1f
                        }
                    }
                    rowEnergy[srcY] = rowVar / max(1, bw - 1)
                }
            }
            y = y2
        }

        val colEnergy = FloatArray(w) { i ->
            if (colCount[i] > 0) colAcc[i] / colCount[i] else 0f
        }
        return colEnergy to rowEnergy
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
