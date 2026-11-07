package com.changjietu.app.image.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.changjietu.app.core.OverviewGeometry

/**
 * 生成「剪辑式编辑页」用的长图总览位图。
 *
 * 严格遵守分块解码红线：不整图 decode，按 1024 输出像素高的条带逐条调用
 * [RegionSource.decodeRegion]（带 inSampleSize），每条画进一张有界的 RGB_565 输出后立即 close。
 * 输出尺寸由 [OverviewGeometry] 限定在 360 × 6000 以内（≈ 4.3 MB 峰值）。
 *
 * 返回的是已解码的 [ImageBitmap]，调用方直接用 `Image(bitmap=...)` 显示；
 * 不要交给 Coil/AsyncImage——它会按 composable 尺寸重新解码，超长图会 OOM。
 */
object OverviewRenderer {

    /** 每条带解码后的目标高度，控制峰值内存。 */
    private const val STRIP_OUTPUT_HEIGHT = 1024

    fun render(
        source: RegionSource,
        contentLeft: Int,
        contentTop: Int,
        contentWidth: Int,
        contentHeight: Int,
    ): ImageBitmap {
        val spec = OverviewGeometry.compute(contentWidth, contentHeight)
        val output = Bitmap.createBitmap(spec.outWidth, spec.outHeight, Bitmap.Config.RGB_565)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val stripSourceHeight = STRIP_OUTPUT_HEIGHT * spec.sampleSize

        var y = 0
        while (y < contentHeight) {
            val y2 = (y + stripSourceHeight).coerceAtMost(contentHeight)
            val handle = source.decodeRegion(
                contentLeft,
                contentTop + y,
                contentLeft + contentWidth,
                contentTop + y2,
                sampleSize = spec.sampleSize,
            ) ?: break
            handle.use { h ->
                val strip = h.bitmapOrNull ?: return@use
                val dstTop = y / spec.sampleSize
                val dstBottom = ((y2 + spec.sampleSize - 1) / spec.sampleSize)
                    .coerceAtMost(spec.outHeight)
                if (dstBottom > dstTop) {
                    canvas.drawBitmap(
                        strip,
                        null,
                        RectF(
                            0f,
                            dstTop.toFloat(),
                            spec.outWidth.toFloat(),
                            dstBottom.toFloat(),
                        ),
                        paint,
                    )
                }
            }
            y = y2
        }
        return output.asImageBitmap()
    }
}
