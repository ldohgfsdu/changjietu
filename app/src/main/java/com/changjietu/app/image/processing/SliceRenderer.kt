package com.changjietu.app.image.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.max

/**
 * 单片渲染：区域解码（只读）→ 独立可变 ARGB_8888 输出 → 绘制源区域 + 可选角标。
 * 禁止对输入/区域 Bitmap 创建 Canvas 或改像素；禁止为角标整图 copy 超长原图。
 */
object SliceRenderer {

    /** 角标安全检测默认 padding（像素）。 */
    const val DEFAULT_BADGE_PADDING = 16

    /** 逐条解码的条带高度：控制峰值内存，避免整片源区域与输出位图同时常驻。 */
    private const val STRIP_HEIGHT = 1024

    /**
     * 角标绘制前的纯校验（可在无 Android 图形栈的 JVM 上单测）。
     */
    fun requireMutableOutputForBadge(isMutable: Boolean, isRecycled: Boolean = false) {
        require(!isRecycled) { "output bitmap recycled" }
        require(isMutable) {
            "page badge must draw on mutable output slice, not source/region bitmap"
        }
    }

    /**
     * 角标字号：仅依赖输出切片宽度，保证 measure 与 draw 一致。
     */
    fun badgeTextSize(bitmapWidth: Int): Float = max(28f, bitmapWidth / 28f)

    /**
     * 测量角标（含背景圆角矩形的 padding）尺寸。返回 (width, height)。
     */
    fun measureBadge(label: String, textSize: Float): Pair<Float, Float> {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val pad = textSize * 0.35f
        val tw = textPaint.measureText(label)
        val th = textSize
        return (tw + pad * 2f) to (th + pad * 1.2f)
    }

    /**
     * @return 可变输出 Bitmap（调用方负责 recycle）；区域源在返回前已 close。
     *
     * 当提供 [sliceColEnergy]/[sliceRowEnergy]（长度分别 ≥ 输出宽/高）时，
     * 由 [BadgePositionSelector] 选择尽量不遮挡内容的角标位置；四角均不安全时
     * 回退到能量最低的角，保证角标始终绘制（不再出现 HIDDEN 缺号）。
     * 未提供能量数据时固定绘制在右上角（TR）。
     */
    fun renderSlice(
        source: RegionSource,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        badgeLabel: String? = null,
        sliceColEnergy: FloatArray? = null,
        sliceRowEnergy: FloatArray? = null,
        badgePadding: Int = DEFAULT_BADGE_PADDING,
    ): Bitmap {
        val outW = (right - left).coerceAtLeast(1)
        val outH = (bottom - top).coerceAtLeast(1)
        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(output)
            canvas.drawColor(Color.WHITE)
            // 分条解码后逐条绘制到输出位图：峰值内存 ≈ output + 一条源带，
            // 而不是 output + 整片源区域（屏幕高度切片时差异明显）。
            var y = top
            while (y < bottom) {
                val stripBottom = (y + STRIP_HEIGHT).coerceAtMost(bottom)
                val region = source.decodeRegion(left, y, right, stripBottom, sampleSize = 1)
                    ?: error("region decode failed for ($left,$y)-($right,$stripBottom)")
                try {
                    val srcBmp = region.bitmapOrNull
                        ?: error("region has no Android bitmap")
                    canvas.drawBitmap(srcBmp, 0f, (y - top).toFloat(), null)
                } finally {
                    region.close()
                }
                y = stripBottom
            }
            if (badgeLabel != null) {
                val textSize = badgeTextSize(outW)
                val (badgeW, badgeH) = measureBadge(badgeLabel, textSize)
                val position = if (
                    sliceColEnergy != null &&
                    sliceRowEnergy != null &&
                    sliceColEnergy.size >= outW &&
                    sliceRowEnergy.size >= outH
                ) {
                    BadgePositionSelector.choose(
                        sliceWidth = outW,
                        sliceHeight = outH,
                        badgeWidth = badgeW.toInt().coerceAtLeast(1),
                        badgeHeight = badgeH.toInt().coerceAtLeast(1),
                        padding = badgePadding,
                        colEnergy = sliceColEnergy,
                        rowEnergy = sliceRowEnergy,
                    )
                } else {
                    BadgePosition.TR
                }
                // HIDDEN 只是旧枚举。choose() 不再返回它；这里仍绘制，避免任何路径缺号。
                drawPageBadge(output, badgeLabel, position)
            }
            return output
        } catch (e: Exception) {
            if (!output.isRecycled) output.recycle()
            throw e
        }
    }

    /**
     * 仅允许在可变输出切片上绘制角标。
     */
    fun drawPageBadge(
        bitmap: Bitmap,
        label: String,
        position: BadgePosition = BadgePosition.TR,
    ) {
        requireMutableOutputForBadge(isMutable = bitmap.isMutable, isRecycled = bitmap.isRecycled)
        val canvas = Canvas(bitmap)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = badgeTextSize(bitmap.width)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var textSize = textPaint.textSize
        var pad = textSize * 0.35f
        var tw = textPaint.measureText(label)
        var th = textSize
        var rectW = tw + pad * 2f
        var rectH = th + pad * 1.2f
        val scale = BadgePositionSelector.badgeScaleToFit(rectW, rectH, bitmap.width, bitmap.height)
        if (scale < 0.999f) {
            textSize *= scale
            textPaint.textSize = textSize
            pad = textSize * 0.35f
            tw = textPaint.measureText(label)
            th = textSize
            rectW = tw + pad * 2f
            rectH = th + pad * 1.2f
        }
        val margin = pad
        val (left, top) = BadgePositionSelector.placeBadge(
            bitmapWidth = bitmap.width,
            bitmapHeight = bitmap.height,
            rectW = rectW,
            rectH = rectH,
            margin = margin,
            position = position,
        )
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(160, 0, 0, 0)
        }
        canvas.drawRoundRect(
            left,
            top,
            left + rectW,
            top + rectH,
            12f,
            12f,
            bg,
        )
        canvas.drawText(label, left + pad, top + th, textPaint)
    }
}