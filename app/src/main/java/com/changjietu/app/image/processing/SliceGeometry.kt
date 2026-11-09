package com.changjietu.app.image.processing

import com.changjietu.app.image.analysis.Margins

/**
 * 唯一的输出像素坐标映射：切线是 [endExclusive]，绝不增加隐藏 padding。
 * Android BitmapRegionDecoder 的 right / bottom 都为 exclusive。
 */
data class SlicePixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object SliceGeometry {
    /**
     * 不变的像素 ranges 无需重新解码和写盘。
     * 与展示的实际 [startY, endY) 比较，而非只比较切线数量。
     */
    fun matchesRenderedRanges(ranges: List<IntRange>, slices: List<SliceOutput>): Boolean =
        ranges.size == slices.size && ranges.zip(slices).all { (range, slice) ->
            range.first == slice.startY && range.last + 1 == slice.endY
        }

    fun regionFor(
        sourceWidth: Int,
        sourceHeight: Int,
        margins: Margins,
        range: IntRange,
    ): SlicePixelRect {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(margins.left >= 0 && margins.right >= 0 &&
            margins.top >= 0 && margins.bottom >= 0)
        val right = sourceWidth - margins.right
        val contentHeight = sourceHeight - margins.top - margins.bottom
        require(margins.left < right && contentHeight > 0)
        require(range.first >= 0 && range.first <= range.last &&
            range.last < contentHeight) {
            "Invalid source range: $range for contentHeight=$contentHeight"
        }
        return SlicePixelRect(
            left = margins.left,
            top = margins.top + range.first,
            right = right,
            bottom = margins.top + range.last + 1,
        )
    }

    /** 0 overlap 下，所有中间边界必须满足上张末端等于下张起点，且完整覆盖原内容。 */
    fun requireNoOverlapCoverage(ranges: List<IntRange>, contentHeight: Int) {
        require(contentHeight > 0 && ranges.isNotEmpty())
        require(ranges.first().first == 0)
        require(ranges.last().last + 1 == contentHeight)
        for (i in ranges.indices) {
            val r = ranges[i]
            require(r.first <= r.last && r.last < contentHeight)
            if (i > 0) {
                require(r.first == ranges[i - 1].last + 1) {
                    "Non-exact seam at index=$i: previous=${ranges[i - 1]}, next=$r"
                }
            }
        }
    }
}
