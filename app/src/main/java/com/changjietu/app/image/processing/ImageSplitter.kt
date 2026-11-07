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
        } catch (e: Exception) {
            outFile.delete()
            throw e
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
        val total = request.plan.ranges.size
        // 自动计划/手动切线均以同一份绝对源图坐标输出；no-overlap 必须无缝连续。
        if (request.plan.overlap == 0 && request.plan.ranges.firstOrNull()?.first == 0 &&
            request.plan.ranges.lastOrNull()?.last == request.plan.contentHeight - 1
        ) {
            // 刻意删除单片后可能不连续，不能把删除操作视为需要自动补齐的区域。
            val contiguous = request.plan.ranges.zipWithNext().all { (a, b) ->
                a.last + 1 == b.first
            }
            if (contiguous) {
                SliceGeometry.requireNoOverlapCoverage(request.plan.ranges, request.plan.contentHeight)
            }
        }
        request.outputDir.mkdirs()

        val results = mutableListOf<SliceOutput>()
        try {
            request.plan.ranges.forEachIndexed { index, range ->
                val rect = SliceGeometry.regionFor(src.width, src.height, m, range)
                val absTop = rect.top
                val absBottom = rect.bottom
                val label = "%02d/%02d".format(index + 1, total)
                val name = "${request.filePrefix}_%02d_of_%02d.jpg".format(index + 1, total)
                val out = File(request.outputDir, name)
                // 为单片切出对应能量（源坐标切片），用于角标安全位置决策
                val sliceCol = request.colEnergy?.let { e ->
                    if (rect.right > rect.left && rect.right <= e.size) {
                        e.copyOfRange(rect.left, rect.right)
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
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom,
                    badgeLabel = if (request.drawBadge) label else null,
                    outFile = out,
                    jpegQuality = request.jpegQuality,
                    sliceColEnergy = sliceCol,
                    sliceRowEnergy = sliceRow,
                )
                check(w == rect.width && h == rect.height) {
                    "Output mismatch for $label: expected ${rect.width}x${rect.height}, got ${w}x${h}"
                }
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
        } catch (e: Exception) {
            request.outputDir.deleteRecursively()
            throw e
        }
    }

    fun decodeBounds(input: java.io.InputStream): Pair<Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        return opts.outWidth to opts.outHeight
    }
}
