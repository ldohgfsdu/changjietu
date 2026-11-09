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

    @Test fun resultPageReservesMajorityOfHeightForSliceCards() {
        val result = read("feature/editor/PreviewScreen.kt")
        assertTrue(result.contains("modifier = Modifier.fillMaxWidth().weight(0.42f)"))
        assertTrue(result.contains("modifier = Modifier.weight(if (editingExpanded) 0.58f else 1f)"))
        assertTrue(result.contains("Text(\"保存相册\""))
        assertTrue(result.contains("Text(\"打包 ZIP\""))
    }
}
