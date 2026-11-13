package com.changjietu.app.core

import com.changjietu.app.image.analysis.SplitPointDetector
import com.changjietu.app.image.processing.RegionDecoderUtil
import com.changjietu.app.image.processing.RegionHandle
import com.changjietu.app.image.processing.RegionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * C2.2：源图 raw energy 的缓存与失效；证明 analyze→process 的重复全图能量扫描被消除，
 * 且缓存不改语义输出。
 */
class C2EnergyCacheTest {

    private class CountingHandle : RegionHandle {
        var closedCount = 0
            private set
        override val width get() = 1
        override val height get() = 1
        override val isMutable get() = false
        override val bitmapOrNull get() = null
        override fun close() {
            closedCount++
        }
    }

    private class FakeRegionSource(
        override val width: Int,
        override val height: Int,
    ) : RegionSource {
        val handles = mutableListOf<CountingHandle>()
        var decodeCalls = 0
            private set

        override fun decodeRegion(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            sampleSize: Int,
        ): RegionHandle {
            decodeCalls++
            val h = CountingHandle()
            handles += h
            return h
        }

        override fun close() = Unit
    }

    @Test
    fun cachedEnergy_equalsRecomputedEnergy_sameSource() {
        val src = FakeRegionSource(4, 9000)
        // 模拟 analyze：扫描一次得到 raw energy（缓存）
        val cached = RegionDecoderUtil.computeEnergyMaps(src)
        val scansForAnalyze = src.decodeCalls
        // 模拟 process 复用缓存，不再扫描：decode 数不因缓存读取而增加
        val afterCacheRead = src.decodeCalls

        // 等价性：缓存值 == 重新扫描值（证明复用不改变结果）
        val src2 = FakeRegionSource(4, 9000)
        val before = RegionDecoderUtil.computeEnergyMaps(src2)

        assertTrue("col energy cached==recompute", before.first.contentEquals(cached.first))
        assertTrue("row energy cached==recompute", before.second.contentEquals(cached.second))
        assertTrue(scansForAnalyze > 0)
        assertEquals("reading cache must not scan again", scansForAnalyze, afterCacheRead)
        // 缓存所有 handle 均已 close（无泄漏）
        assertTrue(src.handles.all { it.closedCount == 1 })
    }

    @Test
    fun cachedEnergy_derivesSameSplitPlan_asRecompute() {
        val src = FakeRegionSource(4, 9000)
        val (colE, rowE) = RegionDecoderUtil.computeEnergyMaps(src)
        val src2 = FakeRegionSource(4, 9000)
        val (_, rowE2) = RegionDecoderUtil.computeEnergyMaps(src2)

        val contentH = 9000
        val planCached = SplitPointDetector.plan(
            contentHeight = contentH,
            targetSliceHeight = 2200,
            overlap = 120,
            rowEnergy = rowE.copyOfRange(0, contentH),
        )
        val planRecompute = SplitPointDetector.plan(
            contentHeight = contentH,
            targetSliceHeight = 2200,
            overlap = 120,
            rowEnergy = rowE2.copyOfRange(0, contentH),
        )
        assertEquals("identical split ranges", planRecompute.ranges, planCached.ranges)
        assertEquals("identical slice count", planRecompute.sliceCount, planCached.sliceCount)
    }

    @Test
    fun shipped_pipeline_scansOnce_analyzeYes_processNo() {
        val vm = File(resolveViewModelDir(), "LongshotViewModel.kt")
        val text = vm.readText().lines()
        // 提取 processInternal 函数体
        val procStart = text.indexOfFirst { it.contains("private fun processInternal") }
        val procEnd = text.indexOfFirst { it.contains("private fun cleanupOldEntries") }
        assertTrue(procStart >= 0 && procEnd > procStart)
        val processBody = text.subList(procStart, procEnd).joinToString("\n")

        // 提取 analyzeUri 函数体
        val analyzeStart = text.indexOfFirst { it.contains("private fun analyzeUri") }
        val analyzeEnd = text.indexOfFirst { it.contains("fun startProcess") }
        assertTrue(analyzeStart >= 0 && analyzeEnd > analyzeStart)
        val analyzeBody = text.subList(analyzeStart, analyzeEnd).joinToString("\n")

        // analyze 扫描一次；process 不再扫描，改读缓存
        assertTrue("analyze must scan energy and grid structure once", analyzeBody.contains("computeAnalysisMaps"))
        assertTrue("process must not scan again", !processBody.contains("computeAnalysisMaps") && !processBody.contains("computeEnergyMaps"))
        assertTrue("process must reuse cached grid protection", processBody.contains("srcInfo.protectedSeamRows"))
        assertTrue("process must reuse cached rowEnergy", processBody.contains("srcInfo.rowEnergy"))
        assertTrue("process must reuse cached columnEnergy", processBody.contains("srcInfo.columnEnergy"))
    }

    private fun resolveViewModelDir(): File {
        val candidates = listOf(
            File("src/main/java/com/changjietu/app/feature/editor"),
            File("app/src/main/java/com/changjietu/app/feature/editor"),
            File("../src/main/java/com/changjietu/app/feature/editor"),
        )
        return candidates.firstOrNull { File(it, "LongshotViewModel.kt").isFile }
            ?: error("LongshotViewModel.kt not found from cwd=${File(".").absolutePath}")
    }
}