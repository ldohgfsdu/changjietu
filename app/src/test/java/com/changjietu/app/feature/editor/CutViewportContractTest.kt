package com.changjietu.app.feature.editor

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutViewportContractTest {
    @Test fun statusChangesDoNotInsertOrRemoveViewportSiblings() {
        val relative = "src/main/java/com/changjietu/app/feature/editor/PreviewCutEditor.kt"
        val source = listOf(File(relative), File("app", relative)).first { it.isFile }.readText()
        assertFalse(source.contains("if (cuts.getOrNull(selectedCutIndex) in uncertainCutEnds) {"))
        assertFalse(source.contains("if (manualActive) {"))
        // Status is always one Text node (content changes, no conditional siblings).
        assertTrue(source.contains("Text(\n            if (cuts.getOrNull(selectedCutIndex) in uncertainCutEnds)"))
        assertFalse(source.contains("Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart)"))
        // The bottom actions and navigation remain stable while reclaiming viewport space.
        assertTrue(source.contains("Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)"))
        assertTrue(source.contains("(maxHeight - 48.dp).toPx()"))
        assertTrue(source.contains("LaunchedEffect(sourceHeight, contentTop, cuts.size, viewHeightPx)"))
        assertFalse(source.contains("LaunchedEffect(sourceHeight, contentTop, cuts.size, viewHeightPx, viewportHeightPx)"))
    }
}
