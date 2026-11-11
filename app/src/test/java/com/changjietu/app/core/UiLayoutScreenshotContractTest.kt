package com.changjietu.app.core

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对照 2026-11-09 的三张实机反馈图添加源码结构回归护栏。
 * 这是静态契约测试，不能代替不同字体比例/屏幕宽度下的设备布局截图验收。
 */
class UiLayoutScreenshotContractTest {
    private fun read(relative: String): String {
        val path = "src/main/java/com/changjietu/app/$relative"
        return listOf(File(path), File("app/$path"), File("../$path"))
            .firstOrNull { it.isFile }?.readText() ?: error("Cannot locate $path")
    }

    @Test fun homeActionCentersLabelWithoutDecorativeLeadingOffset() {
        val home = read("feature/importui/HomeScreen.kt")
        val button = home.substringAfter("picker.launch(").substringBefore("when (val state = homeState)")
        assertTrue(button.contains("contentAlignment = Alignment.Center"))
        assertTrue(button.contains("Text(\n                                \"选择图片\""))
        assertFalse(button.contains("Spacer(Modifier.width(8.dp))"))
        assertFalse(button.contains("AppIcons.Image,"))
    }

    @Test fun cutNavigatorProvidesVisibleTextAndDirectLocate() {
        val editor = read("feature/editor/PreviewCutEditor.kt")
        val nav = editor.substringAfter("// 实机 v0.2.6 证明自定义箭头图标不可见。")
            .substringBefore("\n\n                Box(\n                    modifier = Modifier")
        assertTrue(nav.contains(".height(52.dp)"))
        assertTrue(nav.contains(".width(78.dp)"))
        assertTrue(nav.contains(".height(48.dp)"))
        assertTrue(nav.contains("Text(\n                            \"上一条\""))
        assertTrue(nav.contains("Text(\n                            \"下一条\""))
        assertTrue(nav.contains("onClickLabel = \"定位当前切线\""))
        assertTrue(nav.contains("jumpTo(selectedCutIndex, viewHeightPx, viewportHeightPx)"))
        assertFalse(nav.contains("AppIcons.Chevron"))
        assertFalse(nav.contains("TextButton("))
    }

    @Test fun cutHandlesExpose48DpTouchAreaWithUnchanged38DpArtwork() {
        for (file in listOf("feature/editor/OverviewEditor.kt", "feature/editor/PreviewCutEditor.kt")) {
            val s = read(file)
            assertTrue("$file: visual grip must remain 38dp", s.contains("private val HANDLE_SIZE = 38.dp"))
            assertTrue("$file: touch area must be 48dp", s.contains("private val HANDLE_TOUCH_SIZE = 48.dp"))
            assertTrue(s.contains("HANDLE_TOUCH_SIZE.toPx()"))
            assertTrue(s.contains(".size(HANDLE_TOUCH_SIZE)"))
            assertTrue(s.contains(".size(HANDLE_SIZE)"))
        }
        val overview = read("feature/editor/OverviewEditor.kt")
        assertTrue("Cancelled gestures must not recompute", overview.contains("if (completed &&"))
        assertTrue("A tap cannot recompute slices", overview.contains(").roundToInt() != base"))
        assertTrue("Actual movement remains tracked during drag", overview.contains("onDelta(base, accumulated / viewHeightPx * contentHeight)"))
    }

    @Test fun resultPageReservesMajorityOfHeightForSliceCards() {
        val result = read("feature/editor/PreviewScreen.kt")
        assertTrue(result.contains("modifier = Modifier.fillMaxWidth().weight(0.42f)"))
        assertTrue(result.contains("modifier = Modifier.weight(if (editingExpanded) 0.58f else 1f)"))
        assertTrue(result.contains("Text(\"保存相册\""))
        assertTrue(result.contains("Text(\"打包 ZIP\""))
    }
}
