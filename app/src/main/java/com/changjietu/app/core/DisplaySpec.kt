package com.changjietu.app.core

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 设备可用显示尺寸（物理像素，纵向约定：shortSide × longSide）。
 *
 * 产品要求：单张切片不超过手机屏幕长边；切片只裁切、不缩放。
 */
data class DisplaySpec(
    val shortSidePx: Int,
    val longSidePx: Int,
) {
    init {
        require(shortSidePx > 0) { "shortSidePx must be > 0" }
        require(longSidePx > 0) { "longSidePx must be > 0" }
    }

    /** 单张切片允许的最大高度：屏幕长边，且落在解码安全区间。 */
    val maxSliceHeightPx: Int get() = longSidePx.coerceIn(400, 8000)

    /** 切片高度占屏幕长边的百分比（用于「占屏高」展示）。 */
    fun percentOfScreenHeight(px: Int): Int =
        (px * 100f / longSidePx).roundToInt().coerceIn(1, 100)

    /** 内容宽度占屏幕短边的百分比（用于「屏宽」展示）。 */
    fun percentOfScreenWidth(px: Int): Int =
        (px * 100f / shortSidePx).roundToInt().coerceIn(1, 100)

    companion object {
        fun of(widthPx: Int, heightPx: Int): DisplaySpec =
            DisplaySpec(min(widthPx, heightPx), max(widthPx, heightPx))

        /**
         * 读取真实屏幕像素尺寸。API 30+ 用 maximumWindowMetrics（含系统装饰的显示区域），
         * 旧版本用 getRealMetrics。任何异常回退到 resources.displayMetrics。
         */
        fun from(context: Context): DisplaySpec {
            val measured = runCatching {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    ?: return@runCatching null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bounds = wm.maximumWindowMetrics.bounds
                    bounds.width() to bounds.height()
                } else {
                    val dm = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    wm.defaultDisplay.getRealMetrics(dm)
                    dm.widthPixels to dm.heightPixels
                }
            }.getOrNull()?.takeIf { (w, h) -> w > 0 && h > 0 }
            val fallback = context.resources.displayMetrics
            val (w, h) = measured ?: (fallback.widthPixels to fallback.heightPixels)
            return of(
                w.coerceAtLeast(1),
                h.coerceAtLeast(1),
            )
        }
    }
}
