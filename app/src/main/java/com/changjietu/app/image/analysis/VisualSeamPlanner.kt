package com.changjietu.app.image.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Content-first, bounded cut planner. The normal target height is a preference;
 * a detected visual block takes precedence when a safe seam can be reached
 * within a bounded 1.8x height. This path is only used when actual visual
 * geometry was detected. Existing OCR-only/legacy planning stays unchanged.
 *
 * The input regions are content-relative, inclusive Y ranges. A block longer
 * than the bounded image height is *not* silently declared safe: downstream
 * uncertainCuts() marks the forced seam through any protected region.
 */
object VisualSeamPlanner {
    private const val MAX_BITMAP_SLICE_HEIGHT = 8000

    fun plan(
        contentHeight: Int,
        targetSliceHeight: Int,
        overlap: Int,
        rowEnergy: FloatArray,
        visualRows: List<IntRange>,
        protectedRows: List<IntRange>,
    ): SplitPlan {
        if (visualRows.isEmpty()) {
            return SplitPointDetector.plan(
                contentHeight = contentHeight,
                targetSliceHeight = targetSliceHeight,
                overlap = overlap,
                rowEnergy = rowEnergy,
                protectedRows = protectedRows,
            )
        }
        require(contentHeight > 0 && rowEnergy.size >= contentHeight)
        val target = targetSliceHeight.coerceIn(400, MAX_BITMAP_SLICE_HEIGHT)
        val ov = overlap.coerceIn(0, 160)
        // Do not try to decode arbitrarily large images just to save one chart.
        val maxHeight = min(MAX_BITMAP_SLICE_HEIGHT, max(target, (target * 1.8f).toInt()))
        val visual = mask(contentHeight, visualRows)
        val protected = mask(contentHeight, protectedRows)
        val blank = detectBlankBands(rowEnergy, contentHeight)

        val ends = ArrayList<Int>()
        var cursor = 0
        while (cursor < contentHeight && ends.size < 399) {
            val remaining = contentHeight - cursor
            if (remaining <= target) {
                ends.add(contentHeight)
                break
            }
            // Remove the former 55%-of-target minimum. A whole figure can start
            // early, and the only safe seam may be *before* it.
            val minAdvance = max(180, ov + 48)
            val lo = cursor + minAdvance
            val hi = min(contentHeight - 1, cursor + maxHeight)
            if (lo > hi) {
                ends.add(contentHeight)
                break
            }
            var best = -1
            var bestScore = Double.POSITIVE_INFINITY
            for (cut in lo..hi) {
                val len = cut - cursor
                val distance = abs(len - target).toDouble() * 0.20
                val overflowPenalty = max(0, len - target) * 0.12
                val shortPenalty = when {
                    len < target / 4 -> 450.0
                    len < target / 2 -> 150.0
                    else -> 0.0
                }
                val tinyTail = contentHeight - cut
                val tailPenalty = if (tinyTail < target / 4) 220.0 else 0.0
                val contentPenalty = when {
                    visual[cut] -> 50000.0
                    protected[cut] -> 3500.0
                    else -> 0.0
                }
                val blankPenalty = if (blank[cut]) 0.0 else 240.0
                val score = distance + overflowPenalty + shortPenalty + tailPenalty +
                    contentPenalty + blankPenalty
                if (score < bestScore) {
                    bestScore = score
                    best = cut
                }
            }
            // If even the best cut crosses a protected block, prefer one
            // moderately oversized last image whenever that removes the cut.
            if (remaining <= maxHeight && (best < 0 || visual[best] || protected[best])) {
                ends.add(contentHeight)
                break
            }
            if (best < 0) {
                ends.add(contentHeight)
                break
            }
            ends.add(best)
            cursor = (best - ov).coerceAtLeast(cursor + 1)
        }
        if (ends.lastOrNull() != contentHeight) ends.add(contentHeight)
        val ranges = SplitPointDetector.rangesFromCutEnds(contentHeight, ends, ov)
        return SplitPlan(contentHeight, target, ov, ranges)
    }

    private fun mask(height: Int, rows: List<IntRange>): BooleanArray {
        val marked = BooleanArray(height)
        for (r in rows) {
            val lo = r.first.coerceAtLeast(0)
            val hi = r.last.coerceAtMost(height - 1)
            if (lo <= hi) for (y in lo..hi) marked[y] = true
        }
        return marked
    }

    /** A seam needs a band of low energy, not merely one isolated empty row. */
    private fun detectBlankBands(energy: FloatArray, height: Int): BooleanArray {
        val values = energy.copyOfRange(0, height)
        values.sort()
        val median = values[height / 2].coerceAtLeast(0f)
        val threshold = max(0.75f, median * 0.30f)
        val result = BooleanArray(height)
        for (y in 3 until height - 3) {
            var safe = true
            for (dy in -3..3) {
                if (energy[y + dy] > threshold) {
                    safe = false
                    break
                }
            }
            result[y] = safe
        }
        return result
    }
}
