package com.changjietu.app.image.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 屏幕分辨率切片 + 缝隙感知切点的不变量。
 *
 * 产品约束：
 * - 单张高度不超过目标（手机屏幕长边）
 * - 相邻片重叠保持在 100..160
 * - 尽量把切点落在低能量空白带，而不是文字行
 * - 尾部过短时重排，而不是留下碎片
 */
class SplitPointDetectorEnergyTest {

    private fun textWithGaps(
        height: Int,
        textHeight: Int = 40,
        gapHeight: Int = 20,
        textEnergy: Float = 6f,
        gapEnergy: Float = 0.2f,
    ): FloatArray = FloatArray(height) { y ->
        if (y % (textHeight + gapHeight) < textHeight) textEnergy else gapEnergy
    }

    private fun assertCoverageAndMaxHeight(plan: SplitPlan, height: Int, target: Int) {
        assertTrue("plan must not be empty", plan.ranges.isNotEmpty())
        assertEquals(0, plan.ranges.first().first)
        assertEquals(height - 1, plan.ranges.last().last)
        plan.ranges.forEach { r ->
            val h = r.last - r.first + 1
            assertTrue("slice height $h must be <= target $target for $r", h <= target)
        }
        for (i in 0 until plan.ranges.size - 1) {
            val a = plan.ranges[i]
            val b = plan.ranges[i + 1]
            assertTrue("must advance: ${b.first} > ${a.first}", b.first > a.first)
            val overlap = a.last - b.first + 1
            assertTrue("overlap=$overlap must be in 100..160", overlap in 100..160)
        }
    }

    @Test
    fun max_height_never_exceeds_screen_target() {
        val targets = listOf(400, 800, 1200, 2000, 2400, 3200)
        val heights = listOf(401, 1200, 2401, 5000, 12000, 50000)
        for (target in targets) {
            for (height in heights) {
                if (height <= target) continue
                val energy = textWithGaps(height)
                val plan = SplitPointDetector.plan(
                    contentHeight = height,
                    targetSliceHeight = target,
                    overlap = 120,
                    rowEnergy = energy,
                )
                assertCoverageAndMaxHeight(plan, height, target)
            }
        }
    }

    @Test
    fun cuts_land_in_blank_gaps_not_text_lines() {
        val height = 12000
        val energy = textWithGaps(height)
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2200,
            overlap = 120,
            rowEnergy = energy,
        )
        assertCoverageAndMaxHeight(plan, height, 2200)
        // 除最后一片的图片末尾外，每个切点前后都应落在空白带里。
        plan.ranges.dropLast(1).forEach { r ->
            val cut = r.last + 1
            assertTrue("cut=$cut should not end inside text", energy[cut - 1] < 1f)
            assertTrue("cut=$cut should not start inside text", energy[cut] < 1f)
        }
    }

    @Test
    fun no_gaps_falls_back_to_fixed_target_height() {
        val height = 10000
        val energy = FloatArray(height) { 5f }
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2200,
            overlap = 120,
            rowEnergy = energy,
        )
        assertCoverageAndMaxHeight(plan, height, 2200)
        assertEquals(5, plan.sliceCount)
        plan.ranges.dropLast(1).forEach { assertEquals(2200, it.last - it.first + 1) }
    }

    @Test
    fun tiny_tail_is_rebalanced_without_losing_content() {
        val height = 2500
        val energy = textWithGaps(height, textHeight = 80, gapHeight = 20)
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2400,
            overlap = 120,
            rowEnergy = energy,
        )
        assertCoverageAndMaxHeight(plan, height, 2400)
        assertEquals(2, plan.sliceCount)
        plan.ranges.forEach { r ->
            val h = r.last - r.first + 1
            assertTrue("tail slice too short: $h", h >= 1000)
        }
    }

    @Test
    fun screen_height_geometry_produces_reasonable_count() {
        val height = 18420
        val energy = FloatArray(height) { y -> if (y % 400 in 0..7) 0.1f else 5f }
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 2400,
            overlap = 120,
            rowEnergy = energy,
        )
        assertCoverageAndMaxHeight(plan, height, 2400)
        assertTrue("count=${plan.sliceCount}", plan.sliceCount in 8..10)
    }

    @Test
    fun long_50000px_image_splits_within_screen_height() {
        val height = 50000
        val energy = textWithGaps(height)
        val plan = SplitPointDetector.plan(
            contentHeight = height,
            targetSliceHeight = 3200,
            overlap = 120,
            rowEnergy = energy,
        )
        assertCoverageAndMaxHeight(plan, height, 3200)
        assertTrue("count=${plan.sliceCount}", plan.sliceCount in 16..18)
    }

    @Test
    fun existing_legacy_contract_still_works_without_energy() {
        val plan = SplitPointDetector.plan(
            contentHeight = 9000,
            targetSliceHeight = 2200,
            overlap = 120,
        )
        assertEquals(4, plan.sliceCount)
        assertEquals(0 until 2200, plan.ranges.first())
        assertEquals(8999, plan.ranges.last().last)
        assertFalse(plan.ranges.any { it.first > it.last })
    }
}
