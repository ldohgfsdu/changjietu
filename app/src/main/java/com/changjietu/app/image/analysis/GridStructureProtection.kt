package com.changjietu.app.image.analysis

import kotlin.math.abs

/**
 * Detects sustained *parallel* vertical rules in a downsampled image.
 *
 * A 1D row-energy projection mistakes blank table cells for page separators.
 * Parallel long lines reveal that those rows still belong to one table/chart.
 * This is a conservative seam-exclusion heuristic, not generic object recognition.
 *
 * Input is a sampled bitmap row and its corresponding [top, bottomExclusive)
 * SOURCE pixel interval, so no full-size Bitmap is allocated.
 */
class GridStructureProtection(
    private val height: Int,
    sampledWidth: Int,
    private val minRuleLengthPx: Int = 72,
    private val minParallelRules: Int = 4,
) {
    init { require(height > 0 && sampledWidth > 1) }
    private val binSize = 3
    private val bins = (sampledWidth + binSize - 1) / binSize
    private val activeStart = IntArray(bins) { -1 }
    private val activeEnd = IntArray(bins)
    private val support = IntArray(height)
    private val visible = BooleanArray(bins)
    private var finished = false

    fun acceptRow(pixels: IntArray, offset: Int, top: Int, bottomExclusive: Int) {
        check(!finished) { "Cannot append rows after collecting protected spans" }
        if (top < 0 || bottomExclusive <= top || bottomExclusive > height) return
        visible.fill(false)
        val width = bins * binSize
        val count = (pixels.size - offset).coerceAtMost(width)
        if (count <= 1) return

        var lastLuma = luminance(pixels[offset])
        for (x in 1 until count) {
            val luma = luminance(pixels[offset + x])
            // Downsampling weakens 1–2 px grey borders. Requiring a long run and
            // four distinct x-bins reduces false positives from ordinary text.
            if (abs(luma - lastLuma) >= 8) visible[x / binSize] = true
            lastLuma = luma
        }
        for (bin in 0 until bins) {
            if (visible[bin]) {
                if (activeStart[bin] == -1) activeStart[bin] = top
                activeEnd[bin] = bottomExclusive
            } else {
                finishRun(bin)
            }
        }
    }

    private fun finishRun(bin: Int) {
        val start = activeStart[bin]
        if (start >= 0) {
            val end = activeEnd[bin]
            if (end - start >= minRuleLengthPx) {
                for (y in start until end) support[y]++
            }
        }
        activeStart[bin] = -1
    }

    fun protectedRows(): BooleanArray {
        if (!finished) {
            for (bin in 0 until bins) finishRun(bin)
            finished = true
        }
        val raw = BooleanArray(height) { support[it] >= minParallelRules }
        // Fill tiny gaps caused by JPEG anti-aliasing or crossing horizontal rules.
        var from = 0
        while (from < height) {
            if (raw[from]) { from++; continue }
            val start = from
            while (from < height && !raw[from]) from++
            if (start > 0 && from < height && from - start <= 24) {
                for (y in start until from) raw[y] = true
            }
        }
        // Do not place a seam directly on a visible table border.
        val result = BooleanArray(height)
        for (y in raw.indices) {
            if (!raw[y]) continue
            val lo = (y - 12).coerceAtLeast(0)
            val hi = (y + 12).coerceAtMost(height - 1)
            for (i in lo..hi) result[i] = true
        }
        return result
    }

    private fun luminance(color: Int): Int {
        val r = color ushr 16 and 0xFF
        val g = color ushr 8 and 0xFF
        val b = color and 0xFF
        return (r * 77 + g * 150 + b * 29) ushr 8
    }
}
