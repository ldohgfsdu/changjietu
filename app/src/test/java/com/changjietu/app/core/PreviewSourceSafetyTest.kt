package com.changjietu.app.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 静态回归门禁：Android Bitmap 的渲染/内存契约仍需真机测试。
 * 这里只防止重新引入「包装后即 recycle」与「半张白图当成成功」的代码路径。
 */
class PreviewSourceSafetyTest {
    private fun source(name: String, folder: String): String {
        val relative = "src/main/java/com/changjietu/app/$folder/$name.kt"
        val candidates = listOf(File(relative), File("app/$relative"), File("../$relative"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("$name.kt source missing")
    }

    @Test fun detailPreviewOwnsPixelsBeforeClosingDecoderHandle() {
        val editor = source("LongshotViewModel", "feature/editor")
        val block = editor.substringAfter("fun loadDetailWindow(").substringBefore("private val _previewBitmap")
        assertTrue(block.contains("region.use { src ->"))
        assertTrue(block.contains("handle.use { hdl ->"))
        assertTrue(block.contains(".copy(android.graphics.Bitmap.Config.ARGB_8888, false)"))
        assertTrue(block.contains("?.asImageBitmap()"))
        assertFalse(block.contains("handle?.close()"))
        assertTrue(block.contains("generation == sourceGeneration"))
    }

    @Test fun overviewDecodeFailureCannotSilentlyReturnPartialImage() {
        val renderer = source("OverviewRenderer", "image/processing")
        assertTrue(renderer.contains("?: error(\"Overview decode failed"))
        assertTrue(renderer.contains("?: error(\"Overview decode returned no bitmap"))
        assertTrue(renderer.contains("if (!output.isRecycled) output.recycle()"))
        assertFalse(renderer.contains(") ?: break"))
    }
}
