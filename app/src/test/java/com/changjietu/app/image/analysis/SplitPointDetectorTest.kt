package com.changjietu.app.image.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SplitPointDetectorTest {

    @Test
    fun single_slice_when_shorter_than_target() {
        val plan = SplitPointDetector.plan(contentHeight = 1000, targetSliceHeight = 2200, overlap = 120)
        assertEquals(1, plan.sliceCount)
        assertEquals(0 until 1000, plan.ranges.single())
    }

    @Test
    fun example_1080x18420_about_nine_slices() {
        // 18420 / (2200-120) ≈ 8.86 → 约 9 张
        val h = 18420
        val target = 2200
        val overlap = 120
        val energy = FloatArray(h) { y ->
            // 每 400px 一条空白缝
            if (y % 400 in 0..8) 0.1f else 5f
        }
        val plan = SplitPointDetector.plan(
            contentHeight = h,
            targetSliceHeight = target,
            overlap = overlap,
            rowEnergy = energy,
        )
        assertTrue("sliceCount=${plan.sliceCount}", plan.sliceCount in 8..10)
        // 每片高度大致合理
        plan.ranges.forEach { r ->
            val height = r.last - r.first + 1
            assertTrue("height=$height for $r", height in 400..target + overlap + 400)
        }
        // 首尾覆盖
        assertEquals(0, plan.ranges.first().first)
        assertEquals(h - 1, plan.ranges.last().last)
        // 相邻重叠
        for (i in 0 until plan.ranges.size - 1) {
            val a = plan.ranges[i]
            val b = plan.ranges[i + 1]
            assertTrue(b.first < a.last)
            val ov = a.last - b.first + 1
            assertTrue("overlap=$ov", ov in 1..(overlap + 50))
        }
    }

    @Test
    fun prefers_low_energy_near_ideal() {
        val energy = FloatArray(5000) { 10f }
        // 在 ideal 附近挖一个低谷
        for (y in 2180..2220) energy[y] = 0.1f
        val y = SplitPointDetector.findLowestEnergyY(energy, 2000, 2400, 2200)
        assertTrue(y in 2180..2220)
    }

    @Test
    fun rangesFromCutEnds_rebuilds_with_overlap() {
        val ranges = SplitPointDetector.rangesFromCutEnds(1000, listOf(400, 800, 1000), overlap = 50)
        assertEquals(3, ranges.size)
        assertEquals(0 until 400, ranges[0])
        assertEquals(350 until 800, ranges[1])
        assertEquals(750 until 1000, ranges[2])
    }

    // ===== Tail Rebalance 单元测试 =====

    private fun assertNoDuplicateNoZeroProgress(ranges: List<IntRange>) {
        for (i in 0 until ranges.size - 1) {
            val a = ranges[i]
            val b = ranges[i + 1]
            assertTrue("prev non-empty $a", a.last > a.first)
            assertTrue("next non-empty $b", b.last > b.first)
            assertTrue("next.first=${b.first} must be > prev.first=${a.first}", b.first > a.first)
            assertTrue("next.first=${b.first} must be < prev.last=${a.last} (overlap)", b.first < a.last)
        }
    }

    private fun assertAllOverlapsInContract(ranges: List<IntRange>) {
        for (i in 0 until ranges.size - 1) {
            val a = ranges[i]
            val b = ranges[i + 1]
            val ov = a.last - b.first + 1
            assertTrue("actualOverlap=$ov must be in 100..160 for $a / $b", ov in 100..160)
        }
    }

    @Test
    fun tail_rebalance_balanced_tail_unchanged() {
        // 3000 / 1500 / overlap100：末两片 1500/1600，无失衡 → 原样返回
        val plan = SplitPointDetector.plan(contentHeight = 3000, targetSliceHeight = 1500, overlap = 100)
        assertEquals(listOf(0 until 1500, 1400 until 3000), plan.ranges)
    }

    @Test
    fun tail_rebalance_long_tail_rebalances() {
        // 9000 / 2200 / overlap120：末片 680 < target/3 被合并成 2760（>1.15*target）→ 重平衡
        val h = 9000
        val plan = SplitPointDetector.plan(contentHeight = h, targetSliceHeight = 2200, overlap = 120)
        assertEquals(4, plan.ranges.size)
        // 前 N-2 完全不变
        assertEquals(listOf(0 until 2200, 2080 until 4280), plan.ranges.subList(0, plan.ranges.size - 2))
        // 末两片来自单一 cutY = 6640
        assertEquals(4160 until 6640, plan.ranges[2])
        assertEquals(6520 until h, plan.ranges[3])
        assertEquals(120, plan.ranges[2].last - plan.ranges[3].first + 1)
        assertNoDuplicateNoZeroProgress(plan.ranges)
    }

    @Test
    fun tail_rebalance_short_tail_replans_combined_interval() {
        // energy 迫使切点落在 1800 / 3650 附近 → 末片 964（< secondLast/2 且 < target/2）
        // → combined interval 重规划（非 brutal merge）
        val h = 4500
        val target = 2000
        val overlap = 120
        val energy = FloatArray(h) { y -> if (y in 1800..1808 || y in 3650..3658) 0.1f else 5f }
        val plan = SplitPointDetector.plan(
            contentHeight = h, targetSliceHeight = target, overlap = overlap, rowEnergy = energy,
        )
        assertEquals(3, plan.ranges.size)
        // 前 N-2 不变（第一片）
        assertEquals(0, plan.ranges[0].first)
        // 末两片重平衡：高度大致相等（未重平衡时约为 1970/964，明显失衡）
        val h1 = plan.ranges[1].last - plan.ranges[1].first + 1
        val h2 = plan.ranges[2].last - plan.ranges[2].first + 1
        assertTrue("h1=$h1 h2=$h2 not balanced", abs(h1 - h2) <= h1 / 5 + h2 / 5)
        // overlap 保持配置值
        assertEquals(overlap, plan.ranges[1].last - plan.ranges[2].first + 1)
        // final range reaches content end
        assertEquals(h - 1, plan.ranges[2].last)
        assertNoDuplicateNoZeroProgress(plan.ranges)
    }

    @Test
    fun tail_rebalance_configured_overlap_120_preserved() {
        // 11097 / crop T57 B19 → contentHeight 11021；target 2200；overlap 120
        val h = 11097 - 57 - 19
        val plan = SplitPointDetector.plan(contentHeight = h, targetSliceHeight = 2200, overlap = 120)
        val actualOverlap = plan.ranges[plan.ranges.size - 2].last - plan.ranges.last().first + 1
        assertEquals(120, actualOverlap)
    }

    @Test
    fun tail_rebalance_overlap_160_boundary() {
        val h = 11021
        val plan = SplitPointDetector.plan(contentHeight = h, targetSliceHeight = 2200, overlap = 160)
        assertEquals(6, plan.ranges.size)
        assertEquals(8160 until 9670, plan.ranges[4])
        assertEquals(9510 until h, plan.ranges[5])
        assertEquals(160, plan.ranges[4].last - plan.ranges[5].first + 1)
        assertAllOverlapsInContract(plan.ranges)
    }

    @Test
    fun tail_rebalance_actual_overlap_never_exceeds_160() {
        val geometries = listOf(
            Triple(11021, 2200, 120),
            Triple(9000, 2200, 120),
            Triple(4500, 2000, 120),
            Triple(18420, 2200, 120),
            Triple(11021, 2200, 160),
            Triple(3000, 1500, 100),
        )
        for ((h, target, overlap) in geometries) {
            val energy = FloatArray(h) { y -> if (y in 1800..1808 || y in 3650..3658) 0.1f else 5f }
            val plan = SplitPointDetector.plan(
                contentHeight = h, targetSliceHeight = target, overlap = overlap, rowEnergy = energy,
            )
            assertAllOverlapsInContract(plan.ranges)
        }
    }

    @Test
    fun tail_rebalance_first_N_minus_2_unchanged() {
        val h = 9000
        val plan = SplitPointDetector.plan(contentHeight = h, targetSliceHeight = 2200, overlap = 120)
        val unchanged = listOf(0 until 2200, 2080 until 4280)
        assertEquals(unchanged, plan.ranges.subList(0, plan.ranges.size - 2))
    }

    @Test
    fun tail_rebalance_final_end_reaches_content_end() {
        val h = 11021
        val plan = SplitPointDetector.plan(contentHeight = h, targetSliceHeight = 2200, overlap = 120)
        assertEquals(h - 1, plan.ranges.last().last)
        assertNoDuplicateNoZeroProgress(plan.ranges)
    }

    @Test
    fun tail_rebalance_no_duplicate_and_no_zero_progress() {
        val h = 11021
        val plan = SplitPointDetector.plan(contentHeight = h, targetSliceHeight = 2200, overlap = 120)
        assertNoDuplicateNoZeroProgress(plan.ranges)
    }

    @Test
    fun tail_rebalance_regression_11097_crop_geometry() {
        // 原始 11097，top crop 57，bottom crop 19 → contentHeight 11021；target 2200；overlap 120
        val original = 11097
        val topCrop = 57
        val bottomCrop = 19
        val h = original - topCrop - bottomCrop
        val plan = SplitPointDetector.plan(contentHeight = h, targetSliceHeight = 2200, overlap = 120)
        assertEquals(5, plan.ranges.size)
        // 前 N-2 完全不变
        assertEquals(listOf(0 until 2200, 2080 until 4280, 4160 until 6360), plan.ranges.subList(0, 3))
        // 重平衡后末两片
        assertEquals(6240 until 8690, plan.ranges[3])
        assertEquals(8570 until h, plan.ranges[4])
        assertEquals(120, plan.ranges[3].last - plan.ranges[4].first + 1)
        assertEquals(h - 1, plan.ranges[4].last)
        assertNoDuplicateNoZeroProgress(plan.ranges)
    }
}
