package com.changjietu.app.image.processing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.changjietu.app.image.analysis.Margins
import com.changjietu.app.image.analysis.SplitPlan
import java.io.File
import java.io.FileOutputStream

data class SliceOutput(
    val index: Int,
    val total: Int,
    val startY: Int,
    val endY: Int,
    val width: Int,
    val height: Int,
    val file: File,
    val label: String,
)

data class SplitRequest(
    val source: RegionSource,
    val margins: Margins,
    val plan: SplitPlan,
    val outputDir: File,
    val jpegQuality: Int = 95,
    val drawBadge: Boolean = true,
    val filePrefix: String = "longshot",
    /** 全图列能量（源坐标），用于单片角标安全位置决策。 */
    val colEnergy: FloatArray? = null,
    /** 全图行能量（源坐标），用于单片角标安全位置决策。 */
    val rowEnergy: FloatArray? = null,
)

/**
 * 单片写出。默认走 [SliceRenderer]（可变输出 + 角标）。
 * 测试可注入假实现以在无图形栈 JVM 上验证分区/重试契约。
 */
fun interface SliceWriter {
    /**
     * @return width to height of written slice
     */
    fun writeSlice(
        source: RegionSource,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        badgeLabel: String?,
        outFile: File,
        jpegQuality: Int,
        sliceColEnergy: FloatArray?,
        sliceRowEnergy: FloatArray?,
    ): Pair<Int, Int>
}

object DefaultSliceWriter : SliceWriter {
    override fun writeSlice(
        source: RegionSource,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        badgeLabel: String?,
        outFile: File,
        jpegQuality: Int,
        sliceColEnergy: FloatArray?,
        sliceRowEnergy: FloatArray?,
    ): Pair<Int, Int> {
        val output = SliceRenderer.renderSlice(
            source = source,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            badgeLabel = badgeLabel,
            sliceColEnergy = sliceColEnergy,
            sliceRowEnergy = sliceRowEnergy,
        )
        try {
            FileOutputStream(outFile).use { fos ->
                output.compress(
                    Bitmap.CompressFormat.JPEG,
                    jpegQuality.coerceIn(70, 100),
                    fos,
                )
            }
            return output.width to output.height
        } finally {
            if (!output.isRecycled) output.recycle()
        }
    }
}

object ImageSplitter {

    /**
     * 按 plan.ranges 分片写出 JPEG。
     * 每片：区域解码（只读）→ 可变输出 → 角标 → 压缩写盘 → 立即释放。
     * 不缓存全部全尺寸切片，不整图 mutable copy。
     */
    fun splitToFiles(
        request: SplitRequest,
        writer: SliceWriter = DefaultSliceWriter,
    ): List<SliceOutput> {
        val src = request.source
        val m = request.margins
        val contentLeft = m.left
        val contentTop = m.top
        val contentRight = src.width - m.right
        val total = request.plan.ranges.size
        request.outputDir.mkdirs()

        val results = mutableListOf<SliceOutput>()
        request.plan.ranges.forEachIndexed { index, range ->
            val absTop = contentTop + range.first
            val absBottom = contentTop + range.last + 1
            val label = "%02d/%02d".format(index + 1, total)
            val name = "${request.filePrefix}_%02d_of_%02d.jpg".format(index + 1, total)
            val out = File(request.outputDir, name)
            // 为单片切出对应能量（源坐标切片），用于角标安全位置决策
            val sliceCol = request.colEnergy?.let { e ->
                if (contentRight > contentLeft && contentRight <= e.size) {
                    e.copyOfRange(contentLeft, contentRight)
                } else {
                    null
                }
            }
            val sliceRow = request.rowEnergy?.let { e ->
                if (absBottom > absTop && absBottom <= e.size) {
                    e.copyOfRange(absTop, absBottom)
                } else {
                    null
                }
            }
            val (w, h) = writer.writeSlice(
                source = src,
                left = contentLeft,
                top = absTop,
                right = contentRight,
                bottom = absBottom,
                badgeLabel = if (request.drawBadge) label else null,
                outFile = out,
                jpegQuality = request.jpegQuality,
                sliceColEnergy = sliceCol,
                sliceRowEnergy = sliceRow,
            )
            results.add(
                SliceOutput(
                    index = index,
                    total = total,
                    startY = range.first,
                    endY = range.last + 1,
                    width = w,
                    height = h,
                    file = out,
                    label = label,
                ),
            )
        }
        return results
    }

    fun decodeBounds(input: java.io.InputStream): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        return opts.outWidth to opts.outHeight
    }
}
