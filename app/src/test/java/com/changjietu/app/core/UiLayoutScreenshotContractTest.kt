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
        val nav = editor.substringAfter("// Keep legible text actions; no decorative locator outline.")
            .substringBefore("\n\n                Box(\n                    modifier = Modifier")
        assertTrue("navigation should take only 48dp", nav.contains(".height(48.dp)"))
        assertTrue("previous and next remain accessible", nav.contains(".width(72.dp)"))
        assertTrue("navigation touch targets remain at least 44dp", nav.contains(".height(44.dp)"))
        assertFalse("center locator must not look like a giant boxed input", nav.contains(".border("))
        assertTrue("center locator must show a compact counter and action", nav.contains("· 定位"))
        assertTrue("jump geometry must account for its actual height",
            editor.contains("(maxHeight - 48.dp).toPx()"))
        assertTrue(nav.contains("Text(\n                            \"上一条\""))
        assertTrue(nav.contains("Text(\n                            \"下一条\""))
        assertTrue(nav.contains("onClickLabel = \"定位当前切线\""))
        assertTrue(nav.contains("jumpTo(selectedCutIndex, viewHeightPx, viewportHeightPx)"))
        assertFalse(nav.contains("AppIcons.Chevron"))
        assertFalse(nav.contains("TextButton("))
    }

    @Test fun preciseEditorDoesNotReserveAnEmpty48dpWarningPanel() {
        val editor = read("feature/editor/PreviewCutEditor.kt")
        assertFalse(editor.contains("Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart)"))
        assertTrue(editor.contains("拖动切线调整，长按图片添加"))
        assertTrue(editor.contains("红色：未找到可靠空白"))
        assertTrue(editor.contains("蓝色：候选空白"))
        assertTrue(editor.contains(".height(48.dp).padding(horizontal = 8.dp)"))
    }

    @Test fun bothEditorsHaveLargeTouchTargetButOriginalVisualDiameter() {
        val main = read("feature/editor/OverviewEditor.kt")
        val precise = read("feature/editor/PreviewCutEditor.kt")
        for (source in listOf(main, precise)) {
            assertTrue(source.contains("private val HANDLE_SIZE = 38.dp"))
            assertTrue(source.contains("private val HANDLE_TOUCH_SIZE = 48.dp"))
            assertTrue(source.contains(".size(HANDLE_TOUCH_SIZE)"))
            assertTrue(source.contains("HANDLE_TOUCH_SIZE.toPx()"))
            assertTrue(source.contains("Modifier.size(HANDLE_SIZE)"))
            assertTrue(source.contains("val completed = verticalDrag(down.id)"))
        }
        assertTrue(main.contains("CutDragGeometry.hasChanged"))
        assertTrue(main.contains("if (!completed && accumulated != 0f) onDelta(base, 0f)"))
        assertTrue(precise.contains("if (!completed && accumulated != 0f) onPreviewCut(index, base)"))
    }

    @Test fun resultPageReservesMajorityOfHeightForSliceCards() {
        val result = read("feature/editor/PreviewScreen.kt")
        assertTrue(result.contains("modifier = Modifier.fillMaxWidth().weight(0.42f)"))
        assertTrue(result.contains("modifier = Modifier.weight(if (editingExpanded) 0.58f else 1f)"))
        assertTrue(result.contains("Text(\"保存相册\""))
        assertTrue(result.contains("Text(\"打包 ZIP\""))
    }
}
