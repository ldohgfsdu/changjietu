package com.changjietu.app.image.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * C2.1：RegionDecoderUtil.computeEnergyMaps 的资源生命周期（close-once、无泄漏、无死循环）。
 *
 * 用 null-bitmap 假 handle 走「跳过条带」路径，在无 Android 图形栈的 JVM 上验证所有权：
 * 每个成功获得的 handle 恰好 close 一次；normal / null-bitmap / break / exception 均不漏。
 */
class RegionDecoderUtilTest {

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
        private val returnNullFromCall: Int = -1,
        private val throwFromCall: Int = Int.MAX_VALUE,
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
        ): RegionHandle? {
            decodeCalls++
            if (decodeCalls == returnNullFromCall) return null
            if (decodeCalls >= throwFromCall) error("controlled decode failure")
            val h = CountingHandle()
            handles += h
            return h
        }

        override fun close() = Unit
    }

    // 9000 高、stripH=4096 → 3 个条带解码调用
    private val w = 4
    private val h = 9000

    @Test
    fun computeEnergyMaps_nullBitmap_skipsStripsAndClosesEachOnce_noInfiniteLoop() {
        val src = FakeRegionSource(w, h)
        val (colE, rowE) = RegionDecoderUtil.computeEnergyMaps(src)
        // 无死循环：函数返回；条带全部被跳过（bitmap null）
        assertEquals(3, src.decodeCalls)
        assertEquals(3, src.handles.size)
        assertTrue(src.handles.all { it.closedCount == 1 })
        assertEquals(w, colE.size)
        assertEquals(h, rowE.size)
    }

    @Test
    fun computeEnergyMaps_multipleRegions_eachHandleClosedOnce() {
        val src = FakeRegionSource(w, h)
        RegionDecoderUtil.computeEnergyMaps(src)
        assertTrue(src.handles.size >= 3)
        assertTrue("every handle closed exactly once", src.handles.all { it.closedCount == 1 })
    }

    @Test
    fun computeEnergyMaps_decodeReturnsNull_breaks_priorHandlesClosed() {
        val src = FakeRegionSource(w, h, returnNullFromCall = 2)
        RegionDecoderUtil.computeEnergyMaps(src)
        // 第二次解码返回 null → break；此前唯一成功 handle 已 close
        assertEquals(2, src.decodeCalls)
        assertEquals(1, src.handles.size)
        assertEquals(1, src.handles[0].closedCount)
    }

    @Test
    fun computeEnergyMaps_decodeThrows_closesCreatedHandles_andPropagates() {
        val src = FakeRegionSource(w, h, throwFromCall = 2)
        try {
            RegionDecoderUtil.computeEnergyMaps(src)
            fail("expected controlled decode failure")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("controlled decode failure"))
        }
        // 失败前创建并已进入 use 的 handle 已 close；抛出时未产生新 handle
        assertEquals(2, src.decodeCalls)
        assertEquals(1, src.handles.size)
        assertEquals(1, src.handles[0].closedCount)
        assertNull("no handle created after throw", src.handles.getOrNull(1))
    }

    @Test
    fun computeEnergyMaps_isDeterministic_sameSourceSameEnergy() {
        val a = FakeRegionSource(w, h)
        val b = FakeRegionSource(w, h)
        val (colA, rowA) = RegionDecoderUtil.computeEnergyMaps(a)
        val (colB, rowB) = RegionDecoderUtil.computeEnergyMaps(b)
        assertTrue(colA.contentEquals(colB))
        assertTrue(rowA.contentEquals(rowB))
    }

    @Test
    fun shipped_computeEnergyMaps_usesLexicalClose_noBareContinue() {
        val file = File(resolveRegionDecoderDir(), "RegionDecoder.kt")
        val code = file.readText().lines().filterNot { it.trimStart().startsWith("//") }
        assertTrue("computeEnergyMaps must use handle.use", code.any { it.contains("handle.use") })
        assertTrue(
            "must not have bare ?: continue skipping close/advance",
            code.none { Regex("""\?:\s*continue""").containsMatchIn(it) },
        )
        assertTrue(
            "must not rely on try/finally close pattern that old code used",
            code.none { it.contains("finally {") && it.contains("handle.close()") },
        )
    }

    private fun resolveRegionDecoderDir(): File {
        val candidates = listOf(
            File("src/main/java/com/changjietu/app/image/processing"),
            File("app/src/main/java/com/changjietu/app/image/processing"),
            File("../src/main/java/com/changjietu/app/image/processing"),
        )
        return candidates.firstOrNull { File(it, "RegionDecoder.kt").isFile }
            ?: error("RegionDecoder.kt not found from cwd=${File(".").absolutePath}")
    }
}