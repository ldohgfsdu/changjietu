package com.changjietu.app.image.processing

import com.changjietu.app.core.ProcessErrorMessages
import com.changjietu.app.image.analysis.Margins
import com.changjietu.app.image.analysis.SplitPointDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * 驱动真实 [ImageSplitter.splitToFiles] 与 [SliceRenderer] 契约：
 * immutable 源、裁边、角标、约 11 片、超长不整高 decode、失败可重试。
 *
 * 说明：本机 aarch64 无 Robolectric/conscrypt 原生库，故用无图形假 [RegionSource]
 * + 契约型 [SliceWriter]（仍调用 decodeRegion / requireMutableOutputForBadge），
 * 并静态校验生产 [DefaultSliceWriter]/[SliceRenderer] 源码路径。
 */
class SliceRendererTest {

    private class FakeHandle(
        override val width: Int,
        override val height: Int,
        override val isMutable: Boolean = false,
    ) : RegionHandle {
        var closed = false
            private set
        override val bitmapOrNull get() = null
        override fun close() {
            closed = true
        }
    }

    /** 模拟 BitmapRegionDecoder：只返回 immutable handle。 */
    private class TrackingRegionSource(
        override val width: Int,
        override val height: Int,
        private val failUntilCall: Int = 0,
    ) : RegionSource {
        val decodeHeights = mutableListOf<Int>()
        val decodeRects = mutableListOf<IntArray>()
        var callCount = 0
        val closedHandles = mutableListOf<FakeHandle>()

        override fun decodeRegion(left: Int, top: Int, right: Int, bottom: Int, sampleSize: Int): RegionHandle {
            callCount++
            if (callCount <= failUntilCall) {
                error("controlled failure on call $callCount")
            }
            val w = (right - left).coerceAtLeast(1)
            val h = (bottom - top).coerceAtLeast(1)
            decodeHeights += h
            decodeRects += intArrayOf(left, top, right, bottom)
            val handle = FakeHandle(w, h, isMutable = false)
            return object : RegionHandle by handle {
                override fun close() {
                    handle.close()
                    closedHandles += handle
                }
            }
        }

        override fun close() = Unit
    }

    /**
     * 契约 Writer：走与生产相同的 decode→校验→写盘→close 顺序；
     * 不 Canvas 源图；角标仅在确认「输出可变」策略后记录。
     */
    private class ContractSliceWriter(
        private val expectImmutableSource: Boolean = true,
    ) : SliceWriter {
        val badgeLabels = mutableListOf<String?>()
        var maxConcurrentOpen = 0
        private var openCount = 0

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
            val region = source.decodeRegion(left, top, right, bottom, 1)
                ?: error("region decode failed")
            openCount++
            maxConcurrentOpen = maxOf(maxConcurrentOpen, openCount)
            try {
                if (expectImmutableSource) {
                    assertFalse("source region must stay read-only/immutable", region.isMutable)
                }
                // 生产路径：新建可变输出再画角标；此处校验同一规则
                SliceRenderer.requireMutableOutputForBadge(isMutable = true, isRecycled = false)
                if (badgeLabel != null) {
                    // 若误对 immutable 源画角标应失败
                    try {
                        SliceRenderer.requireMutableOutputForBadge(
                            isMutable = region.isMutable,
                            isRecycled = false,
                        )
                        fail("badge on immutable source should be rejected")
                    } catch (e: IllegalArgumentException) {
                        assertTrue(e.message!!.contains("mutable"))
                    }
                }
                badgeLabels += badgeLabel
                outFile.writeBytes(
                    "slice:$badgeLabel:${region.width}x${region.height}:q=$jpegQuality".toByteArray(),
                )
                return region.width to region.height
            } finally {
                region.close()
                openCount--
            }
        }
    }

    @Test
    fun requireMutableOutputForBadge_rejects_immutable() {
        try {
            SliceRenderer.requireMutableOutputForBadge(isMutable = false)
            fail("expected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("mutable"))
        }
        // 可变输出可通过
        SliceRenderer.requireMutableOutputForBadge(isMutable = true, isRecycled = false)
    }

    @Test
    fun splitToFiles_immutable_source_crop_margins_badge_about_eleven() {
        val fullW = 200
        val fullH = 5000
        val src = TrackingRegionSource(fullW, fullH)
        val margins = Margins(left = 10, top = 20, right = 10, bottom = 20)
        val contentH = fullH - margins.top - margins.bottom
        val plan = SplitPointDetector.plan(
            contentHeight = contentH,
            targetSliceHeight = 500,
            overlap = 40,
        )
        assertTrue("plan slices=${plan.sliceCount}", plan.sliceCount in 9..13)

        val writer = ContractSliceWriter()
        val dir = Files.createTempDirectory("slices").toFile()
        try {
            val outputs = ImageSplitter.splitToFiles(
                SplitRequest(
                    source = src,
                    margins = margins,
                    plan = plan,
                    outputDir = dir,
                    drawBadge = true,
                    jpegQuality = 90,
                ),
                writer = writer,
            )
            assertEquals(plan.sliceCount, outputs.size)
            assertTrue(outputs.size >= 9)
            outputs.forEachIndexed { i, o ->
                assertTrue(o.file.exists() && o.file.length() > 0)
                assertEquals("%02d/%02d".format(i + 1, outputs.size), o.label)
                assertTrue(writer.badgeLabels[i] == o.label)
            }
            src.decodeRects.forEach { r ->
                assertEquals(margins.left, r[0])
                assertEquals(fullW - margins.right, r[2])
            }
            assertTrue(src.decodeHeights.all { it < contentH })
            assertEquals(src.callCount, src.closedHandles.size)
            assertEquals(1, writer.maxConcurrentOpen) // 一片处理完再下一片
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun tall_21696_never_decodes_full_height_about_eleven_slices() {
        val w = 1220
        val h = 21696
        val src = TrackingRegionSource(w, h)
        val plan = SplitPointDetector.plan(
            contentHeight = h,
            targetSliceHeight = 2200,
            overlap = 120,
        )
        assertTrue("expected ~11 slices, got ${plan.sliceCount}", plan.sliceCount in 10..12)

        val writer = ContractSliceWriter()
        val dir = Files.createTempDirectory("tall").toFile()
        try {
            val outs = ImageSplitter.splitToFiles(
                SplitRequest(
                    source = src,
                    margins = Margins(0, 0, 0, 0),
                    plan = plan,
                    outputDir = dir,
                    drawBadge = true,
                ),
                writer = writer,
            )
            assertEquals(plan.sliceCount, outs.size)
            assertTrue(outs.size >= 10)
            assertTrue("must not decode full height", src.decodeHeights.none { it >= h })
            assertTrue(src.decodeHeights.maxOrNull()!! <= 2200 + 500)
            assertTrue(writer.badgeLabels.all { it != null && it.contains("/") })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun failure_then_retry_succeeds() {
        val w = 100
        val h = 3000
        val plan = SplitPointDetector.plan(h, targetSliceHeight = 1000, overlap = 50)
        val failing = TrackingRegionSource(w, h, failUntilCall = 1)
        val dir1 = Files.createTempDirectory("fail").toFile()
        try {
            try {
                ImageSplitter.splitToFiles(
                    SplitRequest(failing, Margins(0, 0, 0, 0), plan, dir1, drawBadge = true),
                    writer = ContractSliceWriter(),
                )
                fail("expected controlled failure")
            } catch (e: Exception) {
                assertTrue(e.message!!.contains("controlled failure"))
            }
        } finally {
            dir1.deleteRecursively()
        }

        val ok = TrackingRegionSource(w, h)
        val dir2 = Files.createTempDirectory("ok").toFile()
        try {
            val outs = ImageSplitter.splitToFiles(
                SplitRequest(ok, Margins(0, 0, 0, 0), plan, dir2, drawBadge = true),
                writer = ContractSliceWriter(),
            )
            assertTrue(outs.isNotEmpty())
            assertEquals(plan.sliceCount, outs.size)
        } finally {
            dir2.deleteRecursively()
        }
    }

    @Test
    fun shipped_SliceRenderer_source_uses_mutable_output_only() {
        val root = resolveProcessingSources()
        val renderer = File(root, "SliceRenderer.kt").readText()
        val splitter = File(root, "ImageSplitter.kt").readText()
        assertTrue(renderer.contains("Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)"))
        assertTrue(renderer.contains("Canvas(output)"))
        assertTrue(renderer.contains("requireMutableOutputForBadge"))
        // 只检查可执行代码行，忽略注释中的说明文字
        val codeLines = renderer.lines().filterNot { it.trimStart().startsWith("//") }
        assertFalse(
            "must not Canvas(region/source) in code",
            codeLines.any { Regex("""Canvas\(\s*region""").containsMatchIn(it) },
        )
        assertTrue(codeLines.any { it.contains("Canvas(output)") })
        assertTrue(splitter.contains("SliceRenderer.renderSlice"))
        assertTrue(splitter.contains("DefaultSliceWriter"))
        // 旧崩溃路径：对 decode 结果直接 drawPageBadge
        assertFalse(splitter.contains("drawPageBadge(bmp"))
    }

    private fun resolveProcessingSources(): File {
        val candidates = listOf(
            File("src/main/java/com/changjietu/app/image/processing"),
            File("app/src/main/java/com/changjietu/app/image/processing"),
            File("../src/main/java/com/changjietu/app/image/processing"),
        )
        return candidates.firstOrNull { File(it, "SliceRenderer.kt").isFile }
            ?: error("SliceRenderer.kt not found from cwd=${File(".").absolutePath}")
    }

    @Test
    fun processErrorMessages_sanitize_technical() {
        val s = ProcessErrorMessages.sanitizeForUi(
            "java.lang.IllegalStateException: Immutable bitmap passed to Canvas constructor",
            ProcessErrorMessages.PROCESS_FAILED,
        )
        assertEquals(ProcessErrorMessages.PROCESS_FAILED, s)
        assertFalse(s.contains("Immutable"))
    }
}
