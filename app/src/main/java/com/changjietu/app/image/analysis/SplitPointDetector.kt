package com.changjietu.app.image.analysis

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 智能分割：在候选高度附近用水平投影能量找最低缝，尽量避开文字行。
 *
 * [rowEnergy] 索引为图像 y（相对裁切后坐标系 0..height-1），值越大表示该行内容越密。
 *
 * 两条路径：
 * - 无能量数据：保留既有确定性回退（固定目标高度），用于纯几何场景与历史契约。
 * - 有能量数据：缝隙感知。先识别低能量「空白带」，再按「缝隙宽度 + 距离目标 + 高度合理」
 *   评分选切点；切点永远满足 `sliceHeight <= targetSliceHeight`，保证单张不超过屏幕长边。
 */
data class SplitPlan(
    val contentHeight: Int,
    val targetSliceHeight: Int,
    val overlap: Int,
    /** 每片在源图（裁切后）中的 [startY, endY) ，已含重叠 */
    val ranges: List<IntRange>,
) {
    val sliceCount: Int get() = ranges.size
}

object SplitPointDetector {

    /** 单张切片的最小前进量，避免在超小目标高度下产生零碎切片。 */
    private const val MIN_ADVANCE_FLOOR = 200

    fun plan(
        contentHeight: Int,
        targetSliceHeight: Int = 2200,
        overlap: Int = 0,
        rowEnergy: FloatArray? = null,
        searchWindow: Int = 220,
        minSlices: Int = 1,
        maxSlices: Int = 400,
        minGapHeight: Int = 6,
        protectedRows: BooleanArray? = null,
    ): SplitPlan {
        require(contentHeight > 0)
        val target = targetSliceHeight.coerceIn(400, 8000)
        val ov = overlap.coerceIn(0, 160)
        val hardMax = maxSlices.coerceAtLeast(minSlices).coerceAtLeast(1)

        if (contentHeight <= target) {
            return SplitPlan(
                contentHeight = contentHeight,
                targetSliceHeight = target,
                overlap = ov,
                ranges = listOf(0 until contentHeight),
            )
        }

        val ranges = if (rowEnergy == null) {
            legacyRanges(
                contentHeight = contentHeight,
                target = target,
                ov = ov,
                searchWindow = searchWindow,
                maxSlices = hardMax,
            )
        } else {
            require(rowEnergy.size >= contentHeight) { "rowEnergy too short" }
            require(protectedRows == null || protectedRows.size >= contentHeight) {
                "protectedRows too short"
            }
            energyAwareRanges(
                contentHeight = contentHeight,
                target = target,
                ov = ov,
                rowEnergy = rowEnergy,
                searchWindow = searchWindow,
                maxSlices = hardMax,
                minGapHeight = minGapHeight,
                protectedRows = protectedRows,
            )
        }

        return SplitPlan(
            contentHeight = contentHeight,
            targetSliceHeight = target,
            overlap = ov,
            ranges = clampRanges(ranges, contentHeight, hardMax),
        )
    }

    // ---------------------------------------------------------------------
    // Legacy path（无 rowEnergy）
    // ---------------------------------------------------------------------

    private fun legacyRanges(
        contentHeight: Int,
        target: Int,
        ov: Int,
        searchWindow: Int,
        maxSlices: Int,
    ): List<IntRange> {
        val energy = FloatArray(contentHeight) { 1f }
        val cuts = mutableListOf<Int>() // exclusive end y of each slice before applying overlap fill
        var cursor = 0
        while (cursor < contentHeight) {
            val remaining = contentHeight - cursor
            if (remaining <= target) {
                cuts.add(contentHeight)
                break
            }
            val idealEnd = cursor + target
            val windowStart = (idealEnd - searchWindow).coerceAtLeast(cursor + target / 2)
            val windowEnd = (idealEnd + searchWindow).coerceAtMost(contentHeight - 1)
            val best = findLowestEnergyY(energy, windowStart, windowEnd, idealEnd)
            // 保证前进
            val cut = best.coerceIn(cursor + 200, contentHeight)
            cuts.add(cut)
            if (cuts.size >= maxSlices) {
                // 强制收尾，绝不静默丢失尾部内容
                if (cuts.last() < contentHeight) {
                    cuts[cuts.lastIndex] = contentHeight
                }
                break
            }
            // 下一片从 cut - overlap 开始
            val next = (cut - ov).coerceAtLeast(cursor + 1)
            if (next <= cursor) break
            cursor = next
        }

        // 由 cut 边界构建带重叠的 ranges
        val ranges = mutableListOf<IntRange>()
        var start = 0
        for (end in cuts) {
            if (end <= start) continue
            ranges.add(start until end)
            start = (end - ov).coerceAtLeast(0)
        }

        // 合并过短末片
        if (ranges.size >= 2 && ov > 0) {
            val last = ranges.last()
            if (last.height() < target / 3) {
                val prev = ranges[ranges.size - 2]
                ranges[ranges.size - 2] = prev.first until last.last + 1
                ranges.removeAt(ranges.lastIndex)
            }
        }

        // --- Tail Rebalance（post-process 最后两个 ranges）---
        // 原则：
        // 1. 仅当最后 range 明显过长（> target*1.15）或过短（< secondLast/2 且 < target/2）时才触发；
        //    imbalance 不存在则原样返回。
        // 2. 前 N-2 个 range 完全不变。
        // 3. combined interval 高度按 union span 计算，避免重复计算重叠区。
        // 4. 只选一个 safe/energy cutY；由 cutY 派生两张 range。
        // 5. effectiveOverlap 满足现有 domain contract 100..160，并优先保持配置 overlap。
        if (ranges.size >= 2) {
            val secondLast = ranges[ranges.size - 2]
            val last = ranges.last()
            val lastHeight = last.height()
            val secondLastHeight = secondLast.height()

            val isTooTall = lastHeight > target * 115 / 100
            val isTooShort = lastHeight < secondLastHeight / 2 && lastHeight < target / 2

            if (isTooTall || isTooShort) {
                val combinedStart = secondLast.first
                val combinedEnd = last.last
                val combinedHeight = combinedEnd - combinedStart + 1

                if (combinedHeight >= ov + 2) {
                    val idealCut = (combinedStart + combinedEnd + ov + 1) / 2
                    val windowStart = (idealCut - searchWindow)
                        .coerceAtLeast(combinedStart + ov + 1)
                    val windowEnd = (idealCut + searchWindow)
                        .coerceAtMost(combinedEnd - 1)
                    if (windowStart < windowEnd) {
                        val effectiveOverlap = ov
                        val cutY = findLowestEnergyY(energy, windowStart, windowEnd, idealCut)
                            .coerceIn(combinedStart + ov + 1, combinedEnd - 1)
                        val nextStart = cutY - effectiveOverlap

                        val preserved = ranges.subList(0, ranges.size - 2).toList()
                        ranges.clear()
                        ranges.addAll(preserved)
                        ranges.add(combinedStart until cutY)
                        ranges.add(nextStart until combinedEnd + 1)

                        val actualPrev = ranges[ranges.size - 2]
                        val actualNext = ranges[ranges.size - 1]
                        val actualOverlap = actualPrev.last - actualNext.first + 1
                        check(actualOverlap == ov) {
                            "Tail Rebalance broke overlap contract: $actualOverlap"
                        }
                        check(actualNext.first > actualPrev.first) {
                            "Tail Rebalance: no forward progress"
                        }
                        check(actualNext.last == combinedEnd) {
                            "Tail Rebalance: final range must reach combinedEnd"
                        }
                    }
                }
            }
        }

        return ranges
    }

    // ---------------------------------------------------------------------
    // Energy-aware path
    // ---------------------------------------------------------------------

    private fun energyAwareRanges(
        contentHeight: Int,
        target: Int,
        ov: Int,
        rowEnergy: FloatArray,
        searchWindow: Int,
        maxSlices: Int,
        minGapHeight: Int,
        protectedRows: BooleanArray?,
    ): List<IntRange> {
        val smoothed = smooth(rowEnergy, contentHeight, radius = 2)
        val threshold = contentThreshold(smoothed, contentHeight)
        val gaps = findGaps(smoothed, contentHeight, threshold, minGapHeight, protectedRows)
        val effectiveWindow = max(searchWindow, (target * 0.12f).toInt()).coerceIn(160, 360)

        val cuts = mutableListOf<Int>()
        var cursor = 0
        var guard = 0
        while (cursor < contentHeight && guard++ < maxSlices) {
            val remaining = contentHeight - cursor
            if (remaining <= target) {
                cuts.add(contentHeight)
                break
            }
            val maxCut = (cursor + target).coerceAtMost(contentHeight)
            // 下限：既避免碎片，也保证与上一片有实质重叠空间。
            // If a table spans the ordinary 55%-to-target interval, allow an
            // earlier genuine paragraph gap rather than forcing a seam into it.
            val chartNearTarget = protectedRows != null &&
                ((cursor + (target * 0.55f).toInt())..maxCut).any { protectedRows[it] }
            val preferredFraction = if (chartNearTarget) 0.25f else 0.55f
            val minAdvance = max(MIN_ADVANCE_FLOOR, max(ov + 80, (target * preferredFraction).toInt()))
                .coerceAtMost(target - 1)
            val minCut = (cursor + minAdvance).coerceAtMost(maxCut - 1).coerceAtLeast(cursor + 1)
            val ideal = maxCut
            // Prefer a real content gap ANYWHERE in the allowed 55–100% slice
            // interval. Previously searching only the last ~360 px could force
            // a seam through text or a table despite an earlier safe gap.
            val safeStart = minCut
            val safeEnd = maxCut
            val gapCut = chooseGapCut(
                gaps = gaps,
                windowStart = safeStart,
                windowEnd = safeEnd,
                ideal = ideal,
                rangeStart = cursor,
                target = target,
            )
            // Without an identified gap, still produce a lossless full-coverage
            // geometric split; the editor will explicitly warn about uncertainty.
            val cut = (gapCut ?: lowestOutsideProtected(
                smoothed, protectedRows, safeStart, safeEnd, ideal,
            )).coerceIn(safeStart, safeEnd)

            cuts.add(cut)
            val next = (cut - ov).coerceAtLeast(cursor + 1)
            if (next <= cursor) break
            cursor = next

            if (cuts.size >= maxSlices - 1 && cursor < contentHeight) {
                // 极端长图：剩余部分按硬上限补切，绝不丢尾部。
                var rangeStart = cursor
                while (contentHeight - rangeStart > target) {
                    val forcedCut = rangeStart + target
                    cuts.add(forcedCut)
                    rangeStart = (forcedCut - ov).coerceAtLeast(0)
                    if (cuts.size >= maxSlices) break
                }
                cuts.add(contentHeight)
                break
            }
        }

        if (cuts.isEmpty()) cuts.add(contentHeight)
        if (cuts.last() != contentHeight) {
            var rangeStart = (cuts.last() - ov).coerceAtLeast(0)
            while (contentHeight - rangeStart > target) {
                val forcedCut = rangeStart + target
                cuts.add(forcedCut)
                rangeStart = (forcedCut - ov).coerceAtLeast(0)
                if (cuts.size >= maxSlices) break
            }
            cuts.add(contentHeight)
        }

        val ranges = buildRanges(cuts, ov)
        return rebalanceTinyTail(
            ranges = ranges,
            contentHeight = contentHeight,
            target = target,
            ov = ov,
            gaps = gaps,
            energy = smoothed,
            searchWindow = effectiveWindow,
        )
    }

    /**
     * Identify automatic seams that do not land inside a confirmed low-energy
     * blank band. This is a *heuristic*: even a low-energy band is not proof
     * that all visual contents (e.g. table borders) are safe to cut.
     *
     * The returned cut ends are relative to the same content Y axis as energy.
     * The final image end is not a seam and should not be included in cutEnds.
     */
    fun uncertainCuts(
        cutEnds: List<Int>,
        rowEnergy: FloatArray,
        minGapHeight: Int = 6,
        protectedRows: BooleanArray? = null,
    ): List<Int> {
        if (cutEnds.isEmpty()) return emptyList()
        if (rowEnergy.isEmpty()) return cutEnds
        val smoothed = smooth(rowEnergy, rowEnergy.size, radius = 2)
        val gaps = findGaps(
            smoothed,
            rowEnergy.size,
            contentThreshold(smoothed, rowEnergy.size),
            minGapHeight,
            protectedRows,
        )
        return cutEnds.filter { cut ->
            cut !in 1 until rowEnergy.size ||
                gaps.none { gap -> cut >= gap.first + 2 && cut <= gap.last - 1 }
        }
    }

    /**
     * When every low-energy row is inside an image grid, prefer an outside
     * location even if not proven blank. The caller still flags it uncertain.
     */
    private fun lowestOutsideProtected(
        energy: FloatArray,
        protectedRows: BooleanArray?,
        lo: Int,
        hi: Int,
        ideal: Int,
    ): Int {
        if (protectedRows == null) return findLowestEnergyY(energy, lo, hi, ideal)
        var bestY = -1
        var bestScore = Float.MAX_VALUE
        for (y in lo..hi) {
            if (protectedRows[y]) continue
            val dist = abs(y - ideal).toFloat()
            val score = energy[y] + dist * 0.01f
            if (score < bestScore) {
                bestScore = score
                bestY = y
            }
        }
        return if (bestY >= 0) bestY else findLowestEnergyY(energy, lo, hi, ideal)
    }

    /** 在窗口内选一个位于空白带中的切点；没有合格空白带则返回 null。 */
    private fun chooseGapCut(
        gaps: List<IntRange>,
        windowStart: Int,
        windowEnd: Int,
        ideal: Int,
        rangeStart: Int,
        target: Int,
    ): Int? {
        var best: Int? = null
        var bestScore = Float.NEGATIVE_INFINITY
        val span = (windowEnd - windowStart).coerceAtLeast(1).toFloat()
        for (gap in gaps) {
            val lo = max(gap.first, windowStart)
            val hi = min(gap.last, windowEnd)
            if (hi < lo) continue
            if (hi - lo + 1 < 3) continue
            val cut = (lo + hi) / 2
            val gapLen = gap.last - gap.first + 1
            val gapScore = gapLen.coerceAtMost(150).toFloat() / 150f * 50f
            val distScore = (1f - abs(cut - ideal) / span).coerceIn(0f, 1f) * 30f
            val height = cut - rangeStart
            val heightScore = when {
                height >= target * 0.85f -> 20f
                height >= target * 0.65f -> 12f
                height >= target * 0.50f -> 6f
                else -> 0f
            }
            val score = gapScore + distScore + heightScore
            if (score > bestScore) {
                bestScore = score
                best = cut
            }
        }
        return best
    }

    /** 末片过短时，把最后两片重排为更均衡的两片（仍满足最大高度与重叠契约）。 */
    private fun rebalanceTinyTail(
        ranges: List<IntRange>,
        contentHeight: Int,
        target: Int,
        ov: Int,
        gaps: List<IntRange>,
        energy: FloatArray,
        searchWindow: Int,
    ): List<IntRange> {
        if (ranges.size < 2) return ranges
        val prev = ranges[ranges.size - 2]
        val last = ranges.last()
        val tailFloor = max(ov + 80, target / 2).coerceAtMost(target)
        if (last.height() >= tailFloor) return ranges

        val combinedStart = prev.first
        val combinedEndExclusive = last.last + 1

        // 合并后仍不超过屏幕高度时，直接合并成一张更自然的切片。
        if (combinedEndExclusive - combinedStart <= target) {
            return ranges.dropLast(2) + listOf(combinedStart until combinedEndExclusive)
        }

        val lo = max(combinedStart + ov + 1, combinedEndExclusive - target + ov)
        val hi = min(combinedStart + target, combinedEndExclusive - ov - 1)
        if (lo > hi) return ranges

        val ideal = (combinedStart + combinedEndExclusive + ov) / 2
        // 尾部重排优先「均衡」，只在 ideal 附近找缝隙，避免为了贴缝隙制造过短末片。
        val balanceWindow = max(80, searchWindow)
        val windowStart = max(lo, ideal - balanceWindow)
        val windowEnd = min(hi, ideal + balanceWindow)
        if (windowStart > windowEnd) return ranges
        val cut = (chooseGapCut(
            gaps = gaps,
            windowStart = windowStart,
            windowEnd = windowEnd,
            ideal = ideal,
            rangeStart = combinedStart,
            target = target,
        ) ?: findLowestEnergyY(energy, windowStart, windowEnd, ideal)).coerceIn(lo, hi)

        val nextStart = (cut - ov).coerceAtLeast(0)
        val newPrev = combinedStart until cut
        val newLast = nextStart until combinedEndExclusive
        if (newPrev.height() > target || newLast.height() > target) return ranges
        if (newLast.first <= newPrev.first) return ranges

        val result = ranges.dropLast(2) + listOf(newPrev, newLast)
        if (result.last().last + 1 != contentHeight) return ranges
        return result
    }

    private fun buildRanges(cuts: List<Int>, ov: Int): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = 0
        for (cut in cuts) {
            if (cut <= start) continue
            ranges.add(start until cut)
            start = (cut - ov).coerceAtLeast(0)
        }
        return if (ranges.isEmpty()) listOf(0 until max(1, cuts.lastOrNull() ?: 1)) else ranges
    }

    private fun smooth(energy: FloatArray, height: Int, radius: Int): FloatArray {
        val out = FloatArray(height)
        for (y in 0 until height) {
            var sum = 0f
            var n = 0
            for (i in (y - radius)..(y + radius)) {
                if (i in 0 until height) {
                    sum += energy[i]
                    n++
                }
            }
            out[y] = if (n == 0) 0f else sum / n
        }
        return out
    }

    /**
     * 内容/空白阈值：结合整体均值、高分位与低分位，避免把整屏密集文字都判成空白，
     * 也避免把稀疏长图里的细小缝隙全部忽略。
     */
    private fun contentThreshold(energy: FloatArray, height: Int): Float {
        val sorted = FloatArray(height) { energy[it] }
        sorted.sort()
        val p25 = sorted[(height * 0.25f).toInt().coerceIn(0, height - 1)]
        val p90 = sorted[(height * 0.90f).toInt().coerceIn(0, height - 1)]
        val mean = energy.sum() / height
        val byMean = mean * 0.35f
        val byPercentile = p90 * 0.25f + p25 * 0.5f
        return max(0.75f, min(byMean, byPercentile))
    }

    private fun findGaps(
        energy: FloatArray,
        height: Int,
        threshold: Float,
        minGapHeight: Int,
        protectedRows: BooleanArray? = null,
    ): List<IntRange> {
        val gaps = mutableListOf<IntRange>()
        var runStart = -1
        for (y in 0 until height) {
            if (energy[y] <= threshold && protectedRows?.getOrNull(y) != true) {
                if (runStart < 0) runStart = y
            } else if (runStart >= 0) {
                if (y - runStart >= minGapHeight) gaps.add(runStart until y)
                runStart = -1
            }
        }
        if (runStart >= 0 && height - runStart >= minGapHeight) {
            gaps.add(runStart until height)
        }
        return gaps
    }

    private fun clampRanges(
        ranges: List<IntRange>,
        contentHeight: Int,
        maxSlices: Int,
    ): List<IntRange> {
        var clamped = ranges
            .map { it.first.coerceAtLeast(0) until (it.last + 1).coerceAtMost(contentHeight) }
            .filter { it.first <= it.last }
        if (clamped.isEmpty()) return listOf(0 until contentHeight)

        // 保证覆盖到尾部，绝不因为取整/裁剪丢内容。
        if (clamped.last().last != contentHeight - 1) {
            val last = clamped.last()
            clamped = clamped.dropLast(1) + listOf(last.first until contentHeight)
        }
        if (clamped.size <= maxSlices) return clamped

        // 兜底：超过上限时保留前 maxSlices-1 片，并把剩余内容并入最后一片（不静默丢弃）。
        val kept = clamped.take(maxSlices - 1).toMutableList()
        if (kept.isEmpty()) return listOf(0 until contentHeight)
        kept.add(kept.last().first until contentHeight)
        return kept
    }

    private fun IntRange.height(): Int = last - first + 1

    // ---------------------------------------------------------------------
    // Shared primitives
    // ---------------------------------------------------------------------

    /**
     * 在 [windowStart, windowEnd] 找能量最低的 y；同等能量偏好靠近 [idealEnd]。
     */
    fun findLowestEnergyY(
        rowEnergy: FloatArray,
        windowStart: Int,
        windowEnd: Int,
        idealEnd: Int,
    ): Int {
        val start = windowStart.coerceIn(0, rowEnergy.lastIndex)
        val end = windowEnd.coerceIn(start, rowEnergy.lastIndex)
        var bestY = idealEnd.coerceIn(start, end)
        var bestScore = Float.MAX_VALUE
        for (y in start..end) {
            // 平滑：看邻域均值，避免单像素噪声
            val e = localEnergy(rowEnergy, y, radius = 2)
            val dist = abs(y - idealEnd).toFloat()
            val score = e + dist * 0.01f
            if (score < bestScore) {
                bestScore = score
                bestY = y
            }
        }
        return bestY
    }

    private fun localEnergy(rowEnergy: FloatArray, y: Int, radius: Int): Float {
        var sum = 0f
        var n = 0
        for (i in (y - radius)..(y + radius)) {
            if (i in rowEnergy.indices) {
                sum += rowEnergy[i]
                n++
            }
        }
        return if (n == 0) 0f else sum / n
    }

    /**
     * 从候选 cut 列表（升序 exclusive ends）与 overlap 生成 ranges。
     * 供预览手动调整后重建。
     */
    fun rangesFromCutEnds(
        contentHeight: Int,
        cutEnds: List<Int>,
        overlap: Int,
    ): List<IntRange> {
        if (cutEnds.isEmpty()) return listOf(0 until contentHeight)
        val ends = cutEnds.map { it.coerceIn(1, contentHeight) }.distinct().sorted()
            .toMutableList()
        if (ends.last() != contentHeight) ends.add(contentHeight)
        val ranges = mutableListOf<IntRange>()
        var start = 0
        for (end in ends) {
            if (end <= start) continue
            ranges.add(start until end)
            start = (end - overlap).coerceAtLeast(0)
        }
        if (ranges.isEmpty()) return listOf(0 until contentHeight)
        return ranges
    }

    /** 将 ranges 转为非重叠 cut ends（每片 exclusive end，末片 = height） */
    fun cutEndsFromRanges(ranges: List<IntRange>, contentHeight: Int): List<Int> {
        if (ranges.isEmpty()) return listOf(contentHeight)
        return ranges.map { (it.last + 1).coerceIn(1, contentHeight) }
            .distinct()
            .sorted()
            .let { if (it.lastOrNull() != contentHeight) it + contentHeight else it }
    }
}
