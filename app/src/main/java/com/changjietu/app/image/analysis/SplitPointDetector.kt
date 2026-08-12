package com.changjietu.app.image.analysis

/**
 * 智能分割：在候选高度附近用水平投影能量找最低缝，尽量避开文字行。
 *
 * [rowEnergy] 索引为图像 y（相对裁切后坐标系 0..height-1），值越大表示该行内容越密。
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

    fun plan(
        contentHeight: Int,
        targetSliceHeight: Int = 2200,
        overlap: Int = 120,
        rowEnergy: FloatArray? = null,
        searchWindow: Int = 220,
        minSlices: Int = 1,
        maxSlices: Int = 48,
    ): SplitPlan {
        require(contentHeight > 0)
        val target = targetSliceHeight.coerceIn(400, 8000)
        val ov = overlap.coerceIn(100, 160)

        if (contentHeight <= target) {
            return SplitPlan(
                contentHeight = contentHeight,
                targetSliceHeight = target,
                overlap = ov,
                ranges = listOf(0 until contentHeight),
            )
        }

        val energy = rowEnergy ?: FloatArray(contentHeight) { 1f }
        require(energy.size >= contentHeight) { "rowEnergy too short" }

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
                // 强制收尾
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
        val starts = mutableListOf(0)
        val ends = cuts.toMutableList()
        // 重建 starts：第 i 片 start = max(0, ends[i-1] - ov) for i>0，但需与算法 cursor 一致
        val ranges = mutableListOf<IntRange>()
        var start = 0
        for (i in ends.indices) {
            val end = ends[i]
            ranges.add(start until end)
            if (i < ends.lastIndex) {
                start = (end - ov).coerceAtLeast(0)
            }
        }

        // 合并过短末片
        if (ranges.size >= 2) {
            val last = ranges.last()
            if (last.last - last.first + 1 < target / 3) {
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
        // 3. combined interval 高度按 union span 计算：combinedStart = R[n-1].start, combinedEnd = R[n].end,
        //    combinedHeight = combinedEnd - combinedStart。禁止直接 R[n-1].height + R[n].height，
        //    因为原 ranges 已含 overlap，直接求和会重复计算重叠区。
        // 4. 只选一个 safe/energy cutY（ideal boundary 只作搜索中心）；由 cutY 派生两张 range：
        //    previous = [combinedStart, cutY); next = [cutY - effectiveOverlap, combinedEnd)。
        //    禁止对 previous.end 与 next.start 分别独立偏移，避免两侧偏移叠加造成异常 overlap。
        // 5. effectiveOverlap 满足现有 domain contract 100..160，并优先保持配置 overlap。
        if (ranges.size >= 2) {
            val secondLast = ranges[ranges.size - 2]
            val last = ranges.last()
            val lastHeight = last.last - last.first + 1
            val secondLastHeight = secondLast.last - secondLast.first + 1

            val isTooTall = lastHeight > target * 115 / 100
            val isTooShort = lastHeight < secondLastHeight / 2 && lastHeight < target / 2

            if (isTooTall || isTooShort) {
                // union span（inclusive 坐标）
                val combinedStart = secondLast.first
                val combinedEnd = last.last
                val combinedHeight = combinedEnd - combinedStart + 1

                // 需要足够空间分成两片且保持最小前进与 overlap contract
                if (combinedHeight >= ov + 2) {
                    // 单一 ideal boundary：分两片后高度大致相等（考虑 overlap）
                    val idealCut = (combinedStart + combinedEnd + ov + 1) / 2
                    val windowStart = (idealCut - searchWindow)
                        .coerceAtLeast(combinedStart + ov + 1)
                    val windowEnd = (idealCut + searchWindow)
                        .coerceAtMost(combinedEnd - 1)
                    if (windowStart < windowEnd) {
                        // safe/energy cut 只调整 cutY
                        val effectiveOverlap = ov
                        val cutY = findLowestEnergyY(energy, windowStart, windowEnd, idealCut)
                            .coerceIn(combinedStart + ov + 1, combinedEnd - 1)
                        val nextStart = cutY - effectiveOverlap

                        val preserved = ranges.subList(0, ranges.size - 2).toList()
                        ranges.clear()
                        ranges.addAll(preserved)
                        ranges.add(combinedStart until cutY)
                        ranges.add(nextStart until combinedEnd + 1)

                        // 显式验证 invariant（实际 overlap、前进、覆盖）
                        val actualPrev = ranges[ranges.size - 2]
                        val actualNext = ranges[ranges.size - 1]
                        val actualOverlap = actualPrev.last - actualNext.first + 1
                        check(actualOverlap in 100..160) {
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

        val clamped = ranges
            .map { it.first.coerceAtLeast(0) until (it.last + 1).coerceAtMost(contentHeight) }
            .filter { it.first < it.last + 1 && it.last >= it.first }
            .let { list -> if (list.isEmpty()) listOf(0 until contentHeight) else list }

        return SplitPlan(
            contentHeight = contentHeight,
            targetSliceHeight = target,
            overlap = ov,
            ranges = clamped.take(maxSlices.coerceAtLeast(minSlices)),
        )
    }

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
            val dist = kotlin.math.abs(y - idealEnd).toFloat()
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